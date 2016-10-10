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

package cn.edu.seu.cat.deploy.master

import java.io.{File, FileOutputStream}
import java.util.Date

import akka.actor._
import akka.pattern.gracefulStop
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.common.util.{AkkaUtils, RandomUtils, ReflectUtils, Utils}
import cn.edu.seu.cat.executor.DriverExecutor
import cn.edu.seu.cat.job.CatJob
import cn.edu.seu.cat.metrics.operation.{QueryMetrics, QueryMetricsSuccess}
import cn.edu.seu.cat.metrics.{Metrics, Monitor}
import cn.edu.seu.cat.operation._
import cn.edu.seu.cat.ui.CatUI
import scopt.OptionParser

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
 * Created by JunxueZhang on 15/6/13.
 */

sealed trait MState
case object MIdle extends MState
case object MReady extends MState
case object MPendingWork extends MState
case object MWorking extends MState
case object MCleaning extends MState

sealed trait MData
case object MUnInitialized extends MData
final case class MWorkingData(workerMap: Map[String, WorkerInfo],
                              workingWorkerMap: Map[String, (WorkerInfo, Boolean)],
                              jobInformation: Option[JobInformation],
                              executionConfig: Option[ExecutionConfig],
                              executionInformation: Option[ExecutionInformation]) extends MData

final case class MCleaningData(workerMap: Map[String, WorkerInfo],
                               toCleanWorkerMap: Map[String, (WorkerInfo, Boolean)],
                               executionInformation: Option[ExecutionInformation]) extends MData

final case class JobInformation(currentClient: ActorRef,
                                currentJob: CatJob)

final case class ExecutionConfig(executorSystem: String,
                                 globalExecutorPath: String,
                                 executorPathMap: Map[String, String])

final case class ExecutionInformation(executorActorSystem: ActorSystem,
                                      driverExecutor: ActorRef)
case object CheckHeartBeat


final case class MasterSucceeded(actorSystem: ActorSystem, driverExecutor: ActorRef)
final case class MasterFailed(message: String)

case object CleanFutureACK

class Master(val conf: CatConf,
             host: String,
             port: Int) extends FSM[MState, MData] with Logging {
  /*
    Used to fetch the metrics information
   */
  val metricsMonitor = new Monitor

  /*
    This timer is used to check whether some workers are dead
   */
  val CheckHeartBeatTimer = "CheckHeartBeatTimer"

  /*
    This is the cat ui to show cat status
   */
  val catUI = new CatUI(conf, self)


  override def preStart(): Unit = {
    super.preStart()
    catUI.bind()
    logInfo(s"Master started...")
  }

  import context.dispatcher

  startWith(MIdle, MUnInitialized)

  when(MIdle) {
    case Event(RegisterWorker(id: String, host: String, port: Int), _) =>
      val workerInfo = WorkerInfo(id, host, port, sender)
      logInfo(s"Worker: $id registered")
      goto(MReady) using MWorkingData(Map(id -> workerInfo), Map.empty, None, None, None) replying RegisterWorkerSuccess(s"Worker: $id has registered")
  }

  when(MReady) {
    case Event(RegisterWorker(id: String, host: String, port: Int), data@MWorkingData(currentWorkerMap, _, _, _, _)) =>
      if (currentWorkerMap.contains(id)) {
        stay() replying RegisterWorkerFailed(s"Register worker failed, due to duplicated id: $id")
      } else {
        logInfo(s"Worker: $id registered")
        val workerInfo = new WorkerInfo(id, host, port, sender)
        stay() using MWorkingData(currentWorkerMap + (id -> workerInfo), Map.empty, None, None, None) replying RegisterWorkerSuccess(s"Worker: $id has registered")
      }

    case Event(JobSubmission(job), data @ MWorkingData(workerMap, _, _, _, _)) =>
      logInfo(s"Received job: ${job.jobDescription}")
      val workingWorkerMap = workerMap.filter { case (id, workerInfo) =>
        workerInfo.state != WorkerState.DEAD
      }.map { case (id, workerInfo) =>
        id -> (workerInfo, false)
      }
      if (workingWorkerMap.size < 2) {
        logError("Submission failed. Not enough available workers")
        sender ! JobSubmissionFailed("Not enough available workers")
        stay()
      } else {
        logInfo(s"Accepted job: ${job.jobDescription}")
        // Each execution system will have a random suffix
        val currentExecutorSystemSuffix = RandomUtils.randomAlphanumericString(6)
        // Driver: driver will be launched on master
        val driverExecutorId = "driver"
        val driverPath = Utils.getExecutorActorPath(currentExecutorSystemSuffix, host, port + conf.executorPortOffset, driverExecutorId)
        logInfo(s"Setting Driver path to: $driverPath")

        // GlobalComputationExecutor: it will be launched on one worker
        val globalComputationWorkerId = workingWorkerMap.head._1
        val globalComputationWorkerInfo = workingWorkerMap.head._2._1
        val globalExecutorId = "global"
        val globalExecutorPath = Utils.getExecutorActorPath(currentExecutorSystemSuffix, globalComputationWorkerInfo.host, globalComputationWorkerInfo.port + conf.executorPortOffset, globalExecutorId)
        logInfo(s"Setting Global executor path to: $globalExecutorPath")

        // VertexExecutor: it will be launched on the workers
        val executorPathMap = (workingWorkerMap - globalComputationWorkerId).map { case(workerId, (workerInfo, _)) =>
          val executorId =  s"executor-${workerId.hashCode}"
          executorId -> Utils.getExecutorActorPath(currentExecutorSystemSuffix, workerInfo.host, workerInfo.port + conf.executorPortOffset, executorId)
        }
        logInfo(s"Set Vertex executor paths to: ${executorPathMap.values.mkString("[", ",", "]")}")

        // The overall assignment strategy
        val assignmentMap = Map(globalComputationWorkerId -> globalExecutorId) ++
          (workingWorkerMap - globalComputationWorkerId).map { case (workerId, workerInfo) =>
            workerId -> s"executor-${workerId.hashCode}"
          }

        // Executor system name
        val executorSystem = Utils.getExecutorAkkaSystemName(currentExecutorSystemSuffix)
        logInfo(s"Set execution system name to: $executorSystem")

        logInfo(s"Asking workers to prepare the job...")
        workingWorkerMap.foreach { case (_, (workerInfo, _)) =>
          workerInfo.actor ! PrepareJob(job, executorSystem, assignmentMap, driverPath, globalExecutorPath, executorPathMap)
        }
        goto(MPendingWork) using data.copy(workingWorkerMap = workingWorkerMap,
          executionConfig = Some(ExecutionConfig(executorSystem, globalExecutorPath, executorPathMap)),
          jobInformation = Some(JobInformation(sender, job)))
      }

  }

  when(MPendingWork, stateTimeout = conf.workerPrepareTimeout seconds) {
    case Event(PrepareJobSuccess(workerId, message), data @ MWorkingData(_, currentWorkingWorkerMap, Some(jobInformation), Some(executorConfig), _)) =>
      logInfo(s"Worker: $workerId has been prepared")
      currentWorkingWorkerMap.get(workerId) match {
        case Some(result) =>
          val newWorkingWorkerMap = currentWorkingWorkerMap.updated(workerId, (result._1, true))
          val unReadyWorkers = newWorkingWorkerMap.filter { case (_, (_ , isReady)) =>
            !isReady
          }
          if (unReadyWorkers.isEmpty) {
            logInfo(s"All workers have been prepared")
            val job = jobInformation.currentJob
            logInfo(s"Begin to copy jar files...")
            val future: Future[(ActorSystem, ActorRef)] = Future {
              val jarPath = s"${conf.tmpFolder}/nutcat_job_${RandomUtils.randomAlphanumericString(6)}.jar"
              val out = new FileOutputStream(jarPath)
              out.write(job.conf.jarFile)
              out.close()
              val classLoader = ReflectUtils.getClassLoader(new File(jarPath).toURI.toURL)
              val (actorSystem, _) = AkkaUtils.createActorSystem(executorConfig.executorSystem, host, port + conf.executorPortOffset, conf, classLoader)
              val driverExecutor = actorSystem.actorOf(Props(new DriverExecutor(conf, self, executorConfig.globalExecutorPath, executorConfig.executorPathMap, job.computationQueue.size)), "driver")
              (actorSystem, driverExecutor)
            }
            future.onComplete {
              case Success(futureResult) =>
                logInfo("Copy jar files succeeded")
                self ! MasterSucceeded(futureResult._1, futureResult._2)
              case Failure(t) =>
                logError("Exception in copy jar files", t)
                self ! MasterFailed("Exception in copy jar files")
            }
          }
          stay() using data.copy(workingWorkerMap = newWorkingWorkerMap)
        case None =>
          logWarning(s"Received unknown job prepare success message from: $workerId. Ignore. ..")
          stay()
      }

    case Event(PrepareJobFailed(workerId, message), data @ MWorkingData(_, currentWorkingWorkerMap, _, _, _)) =>
      logError(s"Worker: $workerId has failed to prepare, detailed message: $message")
      stay()

    case Event(StateTimeout, data @ MWorkingData(workerMap, currentWorkingWorkerMap, _, _, _)) =>
      logError("Time out in waiting workers to prepare")
      val workersToClean = currentWorkingWorkerMap.filter { case(_, (_, hasReplied)) =>
          hasReplied
      }.map { case(workerId, (workerInfo, _)) =>
        workerId -> (workerInfo, false)
      }
      workersToClean.foreach { case(_, (workerInfo, _)) =>
          workerInfo.actor ! CleanState
      }
      goto(MCleaning) using MCleaningData(workerMap, workersToClean, None)

    case Event(MasterSucceeded(actorSystem, driverExecutor), data @ MWorkingData(_, _, _, _, _)) =>
      logInfo("Start computation...")
      driverExecutor ! StartComputation
      goto(MWorking) using data.copy(executionInformation = Some(ExecutionInformation(actorSystem, driverExecutor)))

    case Event(MasterFailed(message), data @ MWorkingData(workerMap, currentWorkingWorkerMap, _, _, _)) =>
      logError(s"Master has failed to prepare, detailed message: $message")
      val workersToClean = currentWorkingWorkerMap.filter { case(_, (_, hasReplied)) =>
        hasReplied
      }.map { case(workerId, (workerInfo, _)) =>
        workerId -> (workerInfo, false)
      }
      workersToClean.foreach { case(_, (workerInfo, _)) =>
        workerInfo.actor ! CleanState
      }
      goto(MCleaning) using MCleaningData(workerMap, workersToClean, None)
  }

  when(MWorking) {
    case Event(StartComputationSuccess(message),
    data @ MWorkingData(workerMap, currentWorkingWorkerMap, Some(jobInformation), _, Some(executionInformation))) =>
      logInfo(s"Computation succeed, detail message: $message")
      jobInformation.currentClient ! JobSubmissionSuccess(s"Computation succeed, detail message: $message")
      val workersToClean = currentWorkingWorkerMap.filter { case(_, (_, hasReplied)) =>
        hasReplied
      }.map { case(workerId, (workerInfo, _)) =>
        workerId -> (workerInfo, false)
      }
      workersToClean.foreach { case(_, (workerInfo, _)) =>
        workerInfo.actor ! CleanState
      }
      goto(MCleaning) using MCleaningData(workerMap, workersToClean, Some(executionInformation))

    case Event(StartComputationFailed(message),
    data @ MWorkingData(workerMap, currentWorkingWorkerMap, Some(jobInformation), _, Some(executionInformation))) =>
      logError(s"Computation failed, detail message: $message")
      jobInformation.currentClient ! JobSubmissionFailed(s"Computation failed, detail message: $message")
      val workersToClean = currentWorkingWorkerMap.filter { case(_, (_, hasReplied)) =>
        hasReplied
      }.map { case(workerId, (workerInfo, _)) =>
        workerId -> (workerInfo, false)
      }
      workersToClean.foreach { case(_, (workerInfo, _)) =>
        workerInfo.actor ! CleanState
      }
      goto(MCleaning) using MCleaningData(workerMap, workersToClean, Some(executionInformation))
  }

  when(MCleaning, stateTimeout = conf.executorTerminationTimeout seconds) {
    case Event(CleanStateAck(workerId, message), data@MCleaningData(workerMap, workersToClean, executionInformation)) =>
      logInfo(s"Worker: $workerId has cleaned the state")
      workersToClean.get(workerId) match {
        case None =>
          logWarning(s"Received unknown clean state ack message from: $workerId. Ignore. ..")
          stay()
        case Some(result) =>
          val newWorkersToCleanMap = workersToClean.updated(workerId, (result._1, true))
          val unReadyWorkers = newWorkersToCleanMap.filter { case (_, (_, hasCleaned)) =>
            !hasCleaned
          }
          if (unReadyWorkers.isEmpty) {
            executionInformation match {
              case None =>
                goto(MReady) using MWorkingData(workerMap, Map.empty, None, None, None)
              case Some(information) =>
                Future {
                  Await.result(gracefulStop(information.driverExecutor, timeout = conf.executorTerminationTimeout seconds), conf.executorTerminationTimeout seconds)
                  information.executorActorSystem.shutdown()
                  information.executorActorSystem.awaitTermination(conf.executorTerminationTimeout seconds)
                }.onComplete {
                  case Success(value) =>
                    self ! CleanFutureACK
                  case Failure(t) =>
                    logError("Exception in cleaning the execution system, ex: ", t)
                    context.stop(self) //If it cannot clean the execution state, it cannot launch another execution, so the worker will shutdown itself                }
                }
            }
          }
          stay() using data.copy(toCleanWorkerMap = newWorkersToCleanMap)
      }
    case Event(CleanFutureACK | StateTimeout, data@MCleaningData(currentWorkerMap, _, _)) => // We don't deal with time out now
      logInfo("Master succeed to clean the current execution state")
      goto(MReady) using MWorkingData(currentWorkerMap, Map.empty, None, None, None)
  }



  whenUnhandled {
    case Event(CheckHeartBeat, data @ MWorkingData(currentWorkerMap, _, _, _, _)) => {
      val currentTime = System.currentTimeMillis()
      val workersToRemove = currentWorkerMap.filter { case (_, workerInfo) =>
        currentTime - workerInfo.lastHeartbeat > conf.workerTimeout * 5000
      }
      workersToRemove.foreach { case (id, _) =>
        logInfo(s"Worker: $id has been dead for a long time and been removed")
      }
      val newWorkerMap = (currentWorkerMap -- workersToRemove.keys).map { case (id, workerInfo) =>
        if(currentTime - workerInfo.lastHeartbeat > conf.workerTimeout * 1000) {
          logInfo(s"Worker: $id has been dead")
          id -> workerInfo.copy(state = WorkerState.DEAD)
        } else {
          id -> workerInfo
        }
      }
      if (newWorkerMap.isEmpty) {
        goto(MIdle) using MUnInitialized
      } else {
        stay() using data.copy(workerMap = newWorkerMap)
      }
    }

    case Event(Heartbeat(id, metrics), data @ MWorkingData(currentWorkerMap, _, _, _, _)) => {
      currentWorkerMap.get(id) match {
        case Some(workerInfo) =>
          val currentTime = System.currentTimeMillis()
          val newWorkerMap = currentWorkerMap.updated(id, workerInfo.copy(lastHeartbeat = currentTime, lastMetrics = Some(metrics)))
          stay() using data.copy(workerMap = newWorkerMap)
        case None =>
          logWarning(s"Received unknown heartbeat from worker: $id. Ignore. ..")
          stay()
      }
    }
    case Event(QueryMetrics, MUnInitialized) =>
      sender ! QueryMetricsSuccess(Map("master" -> metricsMonitor.getMetrics))
      stay()

    case Event(QueryMetrics, data @ MWorkingData(workerMap, _, _, _, _)) =>
      val metricsMap = workerMap.map { case (workerId, workerInfo) =>
          val metrics = workerInfo.lastMetrics match {
            case None => Metrics("0", "0", "0", "0", "0", new Date())
            case Some(value) => value
          }
          workerId -> metrics
      } + ("master" -> metricsMonitor.getMetrics)
      sender ! QueryMetricsSuccess(metricsMap)
      stay()
  }

  onTransition {
    case x -> MReady => setTimer(CheckHeartBeatTimer, CheckHeartBeat, conf.workerTimeout seconds, true)
    case x -> MIdle => cancelTimer(CheckHeartBeatTimer)
  }

}

object Master extends Logging {

  case class Config(conf: Option[File] = None)

  val parser = new OptionParser[Config]("nutcat master") {
    head("nutcat master")
    opt[File]('c', "conf") valueName "<file>" action { (x, c) =>
      c.copy(conf = Some(x))
    } text "optional file to merge with the default config file"
    help("help") text "prints this usage text"
  }

  def main(args: Array[String]) {
    parser.parse(args, Config()) match {
      case Some(config: Config) =>
        val (actorSystem, _) = startMaster(new CatConf(config.conf))
        actorSystem.awaitTermination()
      case None =>
        logError("Wrong arguments")
    }
  }

  def startMaster(conf: CatConf) = {
    val masterHost = conf.masterAddress
    val masterPort = conf.masterPort
    val masterActorSystem = conf.masterActorSystemName
    val masterActorName = conf.masterActorName
    val (actorSystem, boundPort) = AkkaUtils.createActorSystem(masterActorSystem, masterHost, masterPort, conf = conf)
    actorSystem.actorOf(Props(new Master(conf, masterHost, boundPort)), masterActorName)
    (actorSystem, boundPort)
  }
}
