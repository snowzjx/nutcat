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

package cn.edu.seu.cat.executor

import java.util.concurrent.Executors

import akka.actor.FSM
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.graph.AggregatorComputer
import cn.edu.seu.cat.operation._
import cn.edu.seu.cat.service.GlobalComputerService

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Created by JunxueZhang on 15/6/12.
 */

sealed trait GCState
case object GCPendingGlobalComputation extends GCState
case object GCWaitingGlobalComputation extends GCState
case object GCExceptionState extends GCState

sealed trait GCData
case object GCUninitialized extends GCData
final case class GCGlobalComputerData(superstepId: Long,
                                      messageBuffer: List[(Any, Any)],
                                      queue: Queue[Option[AggregatorComputer[Any, Any]]]) extends GCData

case object GlobalComputationSuccess
case object GlobalComputationFailed

class GlobalComputerExecutor (val conf: CatConf,
                              val driverPath: String,
                              val executorId: String,
                              val vertexExecutorPathMap: Map[String, String],
                              val aggregatorComputerQueue: Queue[Option[AggregatorComputer[Any, Any]]])
  extends FSM[GCState, GCData] with Logging {

  val driver = context.actorSelection(driverPath)
  val vertexExecutorMap = vertexExecutorPathMap. map { case (executorId, path) =>
    (executorId, context.actorSelection(path))
  }

  implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(conf.executorThreadNumber))

  startWith(GCPendingGlobalComputation, GCGlobalComputerData(0, List.empty, aggregatorComputerQueue))

  when(GCPendingGlobalComputation) {
    case Event(StartShiftComputer, data @ GCGlobalComputerData(_, _, queue)) =>
      val newComputationQueue = queue.dequeue._2
      if (newComputationQueue.isEmpty) {
        driver ! StartShiftComputerFailed(executorId, "Shift computer failed, no available global computer")
        goto(GCExceptionState) using GCUninitialized
      } else {
        driver ! StartShiftComputerSuccess(executorId, "Shift computer succeeded")
        stay() using data.copy(superstepId = 0, messageBuffer = List.empty, queue = newComputationQueue)
      }

    case Event(StartGlobalComputation(currentSuperstepId), data @ GCGlobalComputerData(_, messageBuffer, queue)) =>
      if(queue.isEmpty) {
        logError("The computation queue cannot be empty")
        self ! GlobalComputationFailed
      }
      queue.head match {
        case Some(aggregatorComputer) =>
          Future {
            val computerService = new GlobalComputerService(conf, currentSuperstepId, aggregatorComputer, messageBuffer, vertexExecutorMap)
            computerService.compute()
          }.onComplete {
            case Success(_) =>
              self ! GlobalComputationSuccess
            case Failure(t) =>
              logError("Exception in GlobalComputerExecutor's processing global computation, ex: ", t)
              self ! SuperstepFailed
          }
          goto(GCWaitingGlobalComputation) using data.copy(superstepId = currentSuperstepId)
        case None =>
          driver ! StartGlobalComputationSuccsss("No need for global computation")
          goto(GCPendingGlobalComputation) using  GCGlobalComputerData(0, List.empty, aggregatorComputerQueue)
      }
  }

  when(GCWaitingGlobalComputation, stateTimeout = conf.executorAggregatorComputerTimeout seconds) {
    case Event(StateTimeout, _) =>
      logError("Global computation time out in GlobalComputerExecutor")
      driver ! StartGlobalComputationFailed("Global computation time out in GlobalComputerExecutor")
      goto(GCExceptionState) using GCUninitialized

    case Event(GlobalComputationFailed, _) =>
      logError("Global computation failed in GlobalComputerExecutor")
      driver ! StartGlobalComputationFailed("Global computation failed in GlobalComputerExecutor")
      goto(GCExceptionState) using GCUninitialized

    case Event(GlobalComputationSuccess, _) =>
      logInfo("Global computation succeeded in GlobalComputerExecutor.")
      driver ! StartGlobalComputationSuccsss("Global computation succeeded in GlobalComputerExecutor")
      goto(GCPendingGlobalComputation)
  }

  when(GCExceptionState) {
    case Event(s, d) =>
      logError("The vertex computer has been in an exception state")
      stay()
  }

  whenUnhandled {
    case Event(GlobalCommunication(superstepId, message), data @ GCGlobalComputerData(currentSuperstepId, currentMessageBuffer, _)) =>
      if (superstepId - currentSuperstepId != 1)  {
        logWarning(s"Received outdated GlobalCommunication from superstep: $superstepId, ignore ...")
        stay()
      } else {
        message match {
          case None =>
            stay() replying GlobalCommunicationSuccess("Global communication succeeded")
          case Some(value) =>
            stay() using data.copy(messageBuffer = currentMessageBuffer :+ value) replying GlobalCommunicationSuccess("Global communication succeeded")
        }
      }
  }

  override def postStop() {
    super.postStop()
    executionContext.shutdown()
  }
}
