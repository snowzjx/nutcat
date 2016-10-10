/*
 * Copyright 2015 Junxue Zhang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.edu.seu.cat.deploy.worker

import java.io.{File, FileOutputStream}

import akka.actor.{ActorRef, ActorSystem, FSM, Props}
import akka.pattern.gracefulStop
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.common.util.{AkkaUtils, RandomUtils, ReflectUtils, Utils}
import cn.edu.seu.cat.executor.{GlobalComputerExecutor, VertexComputerExecutor}
import cn.edu.seu.cat.graph.patition.PartitionStrategy
import cn.edu.seu.cat.graph.{AggregatorComputer, GraphLoader, VertexComputer}
import cn.edu.seu.cat.metrics.Monitor
import cn.edu.seu.cat.operation._
import scopt.OptionParser

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
 * Created by JunxueZhang on 15/6/13.
 */

sealed trait WState
case object WIdle extends WState
case object WReady extends WState
case object WPendingWork extends WState
case object WWorking extends WState
case object WCleaning extends WState

sealed trait WData
final case class WUnInitialized(registerAttempts: Int) extends WData
case object WReadyData extends WData
final case class WWorkingData(executorActorSystem: ActorSystem, executor: ActorRef) extends WData

case object TryRegisterWorker
case object TrySendHeartBeat

sealed trait PendingPrepareWorkFutureResult
final case class PendingPrepareWorkFutureSuccess(executorActorSystem: ActorSystem,
                                                 executor: ActorRef) extends PendingPrepareWorkFutureResult
final case class PendingPrepareWorkFutureFailed(message: String) extends PendingPrepareWorkFutureResult

case object CleanFutureACK

class Worker (val conf: CatConf,
              host: String,
              port: Int) extends FSM[WState, WData] with Logging {
  /*
    Worker Id
   */
  val workerId = s"Worker-$host-$port"

  /*
    Used to fetch the metrics information
   */
  val metricsMonitor = new Monitor

  /*
    Master ActorSelection
   */
  val master = context.actorSelection(Utils.getAkkaActorPath(conf.masterActorSystemName, conf.masterAddress, conf.masterPort, conf.masterActorName))

  val workerRegistrationTimer = "workerRegistrationTimer"

  val heartBeatTimer = "heartBeatTimer"

  import context.dispatcher

  startWith(WIdle, WUnInitialized(0))

  setTimer(workerRegistrationTimer, TryRegisterWorker, conf.workerRegistrationInterval seconds, true)

  when(WIdle) {
    case Event(TryRegisterWorker, _) =>
      logInfo(s"Registering worker: $workerId")
      master ! RegisterWorker(workerId, host, port)
      stay()
    case Event(RegisterWorkerSuccess(message), _) =>
      logInfo(s"Register worker succeed, detailed message: $message")
      goto(WReady) using WReadyData
    case Event(RegisterWorkerFailed(message), WUnInitialized(attempts)) =>
      logError(s"Register worker failed, detailed message: $message")
      if (attempts > conf.workerRegistrationRetries) {
        stop()
      }
      stay() using WUnInitialized(attempts + 1)
  }

  when(WReady) {
    case Event(PrepareJob(job, executorSystem, assignments, driverPath, globalExecutorPath, executorPathMap), _) =>
      logInfo("Start to prepare for the job...")
      val future: Future[(ActorSystem, ActorRef)] = Future {
        val jarPath = s"${conf.tmpFolder}/nutcat_job_${RandomUtils.randomAlphanumericString(6)}.jar"
        val out = new FileOutputStream(jarPath)
        out.write(job.conf.jarFile)
        out.close()
        val classLoader = ReflectUtils.getClassLoader(new File(jarPath).toURI.toURL)
        val (actorSystem, _) = AkkaUtils.createActorSystem(executorSystem, host, port + conf.executorPortOffset, conf, classLoader)
        val executorActor = (assignments.get(workerId): @unchecked) match {
          case Some("global") =>
            logInfo("Launch global executor")
            // To launch global executor
            val computationQueue = job.computationQueue.map { case (_, value) =>
              value match {
                case None =>
                  None
                case Some(className) =>
                  Some {
                    ReflectUtils.getInstance(classLoader, className).asInstanceOf[AggregatorComputer[Any, Any]]
                  }
              }
            }
            val executorId = "global"
            actorSystem.actorOf(Props(new GlobalComputerExecutor(conf, driverPath, executorId, executorPathMap, computationQueue)), executorId)
          case Some(executorId: String) =>
            logInfo(s"Launch exector: $executorId")
            // To launch vertex executor
            val computationQueue = job.computationQueue.map { case (className1, className2Op) =>
              val class1 = ReflectUtils.getInstance(classLoader, className1).asInstanceOf[VertexComputer[Any, Any, Any, Any]]
              val class2 = className2Op match {
                case Some(className2) =>
                  Some {
                    ReflectUtils.getInstance(classLoader, className2).asInstanceOf[AggregatorComputer[Any, Any]]
                  }
                case None => None
              }
              (class1, class2)
            }
            val graphLoader = ReflectUtils.getInstance(classLoader, job.graphLoader).asInstanceOf[GraphLoader[Any, Any]]
            val partitionStrategy = ReflectUtils.getInstance(classLoader, job.partitionStrategy).asInstanceOf[PartitionStrategy[Any]]
            partitionStrategy.setAvaibleExecutorIds(executorPathMap.keys.toList)
            actorSystem.actorOf(Props(new VertexComputerExecutor(conf, driverPath, executorId, globalExecutorPath, executorPathMap, partitionStrategy, graphLoader, computationQueue)), executorId)
        }
        (actorSystem, executorActor)
      }
      future.onComplete {
        case Success(result) =>
          logInfo("Prepare job succeed")
          self ! PendingPrepareWorkFutureSuccess(result._1, result._2)
        case Failure(t) =>
          log.error("Exception in preparing the job", t)
          self ! PendingPrepareWorkFutureFailed("Exception in in preparing the job")
      }
      goto(WPendingWork)
  }


  when(WPendingWork) {
    case Event(PendingPrepareWorkFutureSuccess(actorSystem, executor), _) =>
      logInfo("The worker has succeeded to prepare the job")
      master ! PrepareJobSuccess(workerId, "The worker has succeeded to prepare the job")
      logInfo("Start computation...")
      goto(WWorking) using WWorkingData(actorSystem, executor)

    case Event(PendingPrepareWorkFutureFailed(message), _) =>
      logError(s"The worker has succeeded to prepare the job, detailed message: $message")
      master ! PrepareJobFailed(workerId, s"The worker has succeeded to prepare the job, detailed message: $message")
      goto(WReady) using WReadyData
  }

  when(WWorking) {
    case Event(CleanState, data@WWorkingData(executorActorSystem: ActorSystem, executor: ActorRef)) =>
      Future {
        Await.result(gracefulStop(executor, timeout = conf.executorTerminationTimeout seconds), conf.executorTerminationTimeout seconds)
        executorActorSystem.shutdown()
        executorActorSystem.awaitTermination(conf.executorTerminationTimeout seconds)
      }.onComplete {
        case Success(value) =>
          self ! CleanFutureACK
        case Failure(t) =>
          logError("Exception in cleaning the execution system, ex: ", t)
          context.stop(self) //If it cannot clean the execution state, it cannot launch another execution, so the worker will shutdown itself
      }
      goto(WCleaning)
  }

  when(WCleaning, stateTimeout = conf.executorTerminationTimeout seconds) {
    case Event(CleanFutureACK | StateTimeout,  _) => // We don't deal with time out now
      logInfo("Workers succeed to clean the current execution state")
      master ! CleanStateAck(workerId, "Workers succeed to clean the current execution state")
      goto(WReady) using WReadyData
  }


  whenUnhandled {
    case Event(TrySendHeartBeat, _) =>
      master ! Heartbeat(workerId, metricsMonitor.getMetrics)
      stay()
  }

  onTransition {
    case x -> WIdle =>
      cancelTimer(heartBeatTimer)
      setTimer(workerRegistrationTimer, TryRegisterWorker, conf.workerRegistrationInterval seconds, true)
    case WIdle -> WReady =>
      cancelTimer(workerRegistrationTimer)
      setTimer(heartBeatTimer, TrySendHeartBeat, conf.workerTimeout / 4 seconds, true)
  }
}

private[cat] object Worker extends Logging {

  case class Config(conf: Option[File] = None)

  val parser = new OptionParser[Config]("nutcat worker") {
    head("nutcat worker")
    opt[File]('c', "conf") valueName "<file>" action { (x, c) =>
      c.copy(conf = Some(x))
    } text "optional file to merge with the default config file"
    help("help") text "prints this usage text"
  }

  def main(args: Array[String]) {
    parser.parse(args, Config()) match {
      case Some(config: Config) =>
        val (actorSystem, _) = startWorker(new CatConf(config.conf))
        actorSystem.awaitTermination()
      case None =>
        logError("Wrong arguments")
    }
  }

  def startWorker(conf: CatConf) = {
    val workerHost = conf.workerAddress
    val workerPort = conf.workerPort
    val workerActorSystem = conf.workerActorSystemName
    val workerActorName = conf.workerActorName
    val (actorSystem, boundPort) = AkkaUtils.createActorSystem(workerActorSystem, workerHost, workerPort, conf = conf)
    actorSystem.actorOf(Props(new Worker(conf, workerHost, boundPort)), workerActorName)
    (actorSystem, boundPort)
  }
}


