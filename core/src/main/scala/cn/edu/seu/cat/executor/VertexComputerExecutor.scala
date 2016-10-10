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
import cn.edu.seu.cat.graph._
import cn.edu.seu.cat.graph.patition.PartitionStrategy
import cn.edu.seu.cat.operation._
import cn.edu.seu.cat.service.VertexComputerService

import scala.collection.immutable.Queue
import scala.collection.parallel.immutable.ParSeq
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import scalaz.Scalaz._

/**
 * Created by JunxueZhang on 15/6/12.
 */

sealed trait VCState
case object VCNoData extends VCState
case object VCWaitingForLoadingData extends VCState

case object VCPendingSuperstep extends VCState
case object VCWaitingForSuperstep extends VCState

case object VCExceptionState extends VCState

sealed trait VCData
case object VCUninitialized extends VCData
final case class VCSuperstepData(superstepId: Long,
                                 graphData: ParSeq[VertexData[Any, Any]],
                                 messageBuffer: Map[Any, List[(Any, Any)]],
                                 quickMessageBuffer: Map[Any, List[(Any, Any)]], /* Sometimes, when the StartSuperstep travels slow, other executors may have already sent messages. */
                                 globalMessage: Option[Any],
                                 queue: Queue[(VertexComputer[Any, Any, Any, Any], Option[AggregatorComputer[Any, Any]])]) extends VCData

private final case class DataLoadSuccess(data: ParSeq[VertexData[Any, Any]])
private case object DataLoadFailed

private final case class SuperstepSuccess(activeVertices: Long, data: ParSeq[VertexData[Any, Any]])
private case object SuperstepFailed

class VertexComputerExecutor(val conf: CatConf,
                             val driverPath: String,
                             val executorId: String,
                             val globalExecutorPath: String,
                             val vertexExecutorPathMap: Map[String, String],
                             val partitionStrategy: PartitionStrategy[Any],
                             val graphLoader: GraphLoader[Any, Any],
                             val vertexComputerQueue: Queue[(VertexComputer[Any, Any, Any, Any], Option[AggregatorComputer[Any, Any]])])
  extends FSM[VCState, VCData] with Logging {

  val driver = context.actorSelection(driverPath)
  val globalExecutor = context.actorSelection(globalExecutorPath)
  val vertexExecutorMap = vertexExecutorPathMap. map { case (executorId, path) =>
    (executorId, context.actorSelection(path))
  }

  implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(conf.executorThreadNumber))

  startWith(VCNoData, VCUninitialized)

  when (VCNoData) {
    case Event(StartLoadingData, VCUninitialized) =>
      Future {
        blocking {
          graphLoader.loadGraph().filter { vertex =>
            partitionStrategy.partition(vertex.vertexId) == executorId
          }.par
        }
      }.onComplete {
        case Success(data) =>
          self ! DataLoadSuccess(data)
        case Failure(t) =>
          logError("Exception in VertexComputerExecutor's loading data, ex: ", t)
          self ! DataLoadFailed
      }
      goto(VCWaitingForLoadingData)
  }

  when (VCWaitingForLoadingData, stateTimeout = conf.executorLoadgraphTimeout seconds) {
    case Event(StateTimeout, _) =>
      logError("Loading data time out in VertexComputerExecutor")
      driver ! StartLoadingDataFailed(executorId, s"Loading data time out in VertexComputerExecutor: $executorId")
      goto(VCExceptionState) using VCUninitialized

    case Event(DataLoadFailed, _) =>
      logError("Loading data failed in VertexComputerExecutor")
      driver ! StartLoadingDataFailed(executorId, s"Loading data failed in VertexComputerExecutor: $executorId")
      goto(VCExceptionState) using VCUninitialized

    case Event(DataLoadSuccess(data), _) =>
      logInfo("Loading data succeeded in VertexComputerExecutor")
      driver ! StartLoadingDataSuccess(executorId, s"Loading data succeeded in VertexComputerExecutor: $executorId")
      goto(VCPendingSuperstep) using
        VCSuperstepData(0, data, Map.empty, Map.empty, None, vertexComputerQueue)
  }

  when (VCPendingSuperstep) {
    case Event(StartSuperstep(currentSuperstepId),
    data @ VCSuperstepData(_, graphData, messageBuffer, currentQuickMessageMap, globalMessage, queue)) =>
      if(queue.isEmpty) {
        logError("The computation queue cannot be empty")
        self ! SuperstepFailed
      }
      val vertexComputer = queue.head._1
      val aggregatorComputer = queue.head._2
      Future {
        val computerService = new VertexComputerService(conf,
          currentSuperstepId,
          graphData,
          vertexComputer,
          aggregatorComputer,
          messageBuffer,
          globalMessage,
          globalExecutor,
          vertexExecutorMap,
          partitionStrategy)
        computerService.compute()
      }.onComplete {
        case Success(result) =>
          self ! SuperstepSuccess(result._1, result._2)
        case Failure(t) =>
          logError("Exception in VertexComputerExecutor's processing superstep, ex: ", t)
          self ! SuperstepFailed
      }
      goto(VCWaitingForSuperstep) using data.copy(superstepId = currentSuperstepId,
        messageBuffer = currentQuickMessageMap, /* Quick messages are used for next computation */
        quickMessageBuffer = Map.empty,
        globalMessage = None)

    case Event(StartShiftComputer, data @ VCSuperstepData(_, _, _, _,  _, queue)) =>
      val newComputationQueue = queue.dequeue._2
      if (newComputationQueue.isEmpty) {
        driver ! StartShiftComputerFailed(executorId, "Shift computer failed, no available vertex computer")
        goto(VCExceptionState) using VCUninitialized
      } else {
        driver ! StartShiftComputerSuccess(executorId, "Shift computer succeeded")
        stay() using data.copy(superstepId = 0, messageBuffer = Map.empty, quickMessageBuffer = Map.empty, globalMessage = None, queue = newComputationQueue)
      }
  }

  when(VCWaitingForSuperstep, stateTimeout = conf.executorVertexComputerTimeout seconds) {
    case Event(StateTimeout, _) =>
      logError("Superstep computation time out in VertexComputerExecutor")
      driver ! StartSuperstepFailed(executorId, s"Superstep computation time out in VertexComputerExecutor: $executorId")
      goto(VCExceptionState) using VCUninitialized

    case Event(SuperstepFailed, _) =>
      logError("Superstep computation failed in VertexComputerExecutor")
      driver ! StartSuperstepFailed(executorId, s"Superstep computation failed in VertexComputerExecutor: $executorId")
      goto(VCExceptionState) using VCUninitialized

    case Event(SuperstepSuccess(activeVertices, data), currentData : VCSuperstepData) =>
      logInfo("Superstep computation succeeded in VertexComputerExecutor")
      driver ! StartSuperstepSuccess(executorId, s"Superstep computation succeeded in VertexComputerExecutor: $executorId", activeVertices)
      goto(VCPendingSuperstep) using currentData.copy(graphData = data)
  }

  when(VCExceptionState) {
    case Event(s, d) =>
      logError("The vertex computer has encountered an exception state")
      stay()
  }

  whenUnhandled {
    case Event(BufferedSuperstepCommunication(superstepId, messages),
      data @ VCSuperstepData(currentSuperstepId, _, messageBuffer, quickMessageBuffer, _, _)) =>
      if (superstepId - currentSuperstepId > 1 || superstepId - currentSuperstepId < 0) {
        logWarning(s"Received unknown BufferedSuperstepCommunication from superstep: $superstepId, ignore ...")
        stay()
      } else if (superstepId - currentSuperstepId == 1) {
        logInfo(s"Received early BufferedSuperstepCommunication from superstep: $superstepId, ignore ...")
        val messagesMap = messages.map { case (fromVertex, toVerterx, message) =>
          toVerterx ->(fromVertex, message)
        }.groupBy(_._1).map { case (k, v) =>
          (k,v.map(_._2))
        }
        val quickMessageMap = quickMessageBuffer |+| messagesMap
        stay() using data.copy(quickMessageBuffer = quickMessageMap) replying BufferedSuperstepCommunicationSuccess("Buffered superstep communication succeeded.")
      } else {
        val messagesMap = messages.map { case (fromVertex, toVerterx, message) =>
          toVerterx ->(fromVertex, message)
        }.groupBy(_._1).map { case (k, v) =>
          (k,v.map(_._2))
        }
        val newMessageBuffer = messageBuffer |+| messagesMap
        stay() using data.copy(messageBuffer = newMessageBuffer) replying BufferedSuperstepCommunicationSuccess("Buffered superstep communication succeeded")
      }

    case Event(GlobalCommunication(superstepId, value),
    data@VCSuperstepData(currentSuperstepId, _, _, _, _,  _)) =>
      if (superstepId != currentSuperstepId) {
        logWarning(s"Received outdated GlobalCommunication from superstep: $superstepId, ignore ...")
        stay()
      } else {
        val newValue = value match {
          case None => None
          case Some(result) => Some(result._2)
        }
        stay() using data.copy(globalMessage = newValue) replying GlobalCommunicationSuccess("Global communication succeeded")
      }
  }

  override def postStop() {
    super.postStop()
    executionContext.shutdown()
  }
}
