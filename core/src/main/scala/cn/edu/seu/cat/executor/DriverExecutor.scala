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

import akka.actor.{ActorRef, FSM}
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.operation._

import scala.concurrent.duration._

/**
 * Created by JunxueZhang on 15/6/13.
 */

sealed trait DState
case object DInitialized extends DState
case object DPendingLoadingData extends DState
case object DPendingSuperstepComputation extends DState
case object DPendingGlobalComputation extends DState
case object DPendingShiftComputation extends DState
case object DExceptionState extends DState

sealed trait DData
case object DUnInitialized extends DData
final case class DDataLoadData(successExecutorIds: List[String]) extends DData
final case class DComputationData(successExecutorIds: List[String],
                                  activeVertices: Long,
                                  computationId: Int,
                                  superstepId: Long) extends DData

private case object ExecutorLoadingDataSuccess
private final case class ExecutorLoadingDataFailed(message: String)

private final case class ExecutorSuperstepSuccess(activeVertices: Long)
private final case class ExecutorSuperstepFailed(message: String)

private case object ExecutorGlobalComputationSuccess
private final case class ExecutorGlobalComputationFailed(message: String)

private case object ExecutorShiftComputationSuccess
private final case class ExecutorShiftComputationFailed(message: String)




class DriverExecutor(val conf: CatConf,
                     val masterRef: ActorRef,
                     val globalExecutorPath: String,
                     val vertexExecutorPathMap: Map[String, String],
                     val computationQueueSize: Int)
  extends FSM[DState, DData] with Logging  {

  val globalExecutor = context.actorSelection(globalExecutorPath)
  val vertexExecutorMap = vertexExecutorPathMap.map { case (executorId, path) =>
    (executorId, context.actorSelection(path))
  }

  startWith(DInitialized, DUnInitialized)

  when(DInitialized) {
    case Event(StartComputation, _) =>
      startLoadData()
      goto(DPendingLoadingData) using DDataLoadData(List.empty)
  }

  when(DPendingLoadingData, stateTimeout = conf.executorLoadgraphTimeout seconds) {
    case Event(StartLoadingDataSuccess(executorId, message), data @ DDataLoadData(currentSuccessIds)) => {
      logInfo(s"Executor: $executorId succeeded in lading data")
      val newList = currentSuccessIds :+ executorId
      if (newList.size == vertexExecutorMap.size) {
        val newSuperstepId = 1
        startSuperstep(newSuperstepId)
        goto(DPendingSuperstepComputation) using DComputationData(List.empty, 0, computationQueueSize, newSuperstepId)
      } else {
        stay() using DDataLoadData(newList)
      }
    }

    case Event(StartLoadingDataFailed(executorId, message), _) => {
      logInfo(s"Executor: $executorId failed to load data, detailed message: $message")
      masterRef ! StartComputationFailed(s"Executor: $executorId failed to load data, detailed message: $message")
      goto(DExceptionState) using DUnInitialized
    }

    case Event(StateTimeout, _) =>
      catLog.error("Time out in waiting executors to load data")
      masterRef ! StartComputationFailed("Time out in waiting executors to load data")
      goto(DExceptionState) using DUnInitialized
  }

  when(DPendingSuperstepComputation, stateTimeout = conf.executorVertexComputerTimeout seconds) {
    case Event(StartSuperstepSuccess(executorId, message, newActiveVertices),
    data @ DComputationData(currentSuccessIds, currentActiveVertices, currentComputationId, currentSuperstepId)) =>
      logInfo(s"Executor: $executorId succeeded in executing superstep: $currentSuperstepId with $newActiveVertices active vertices")
      val newList = currentSuccessIds :+ executorId
      if (newList.size == vertexExecutorMap.size) {
        if (currentActiveVertices + newActiveVertices == 0) {
          if (currentComputationId == 1) {
            /*
              If active vertices = 0
              &&
              No further vertex & aggregator computers
              Computation successfully
              TODO write graph
             */
            logInfo("Successfully finished computations")
            masterRef ! StartComputationSuccess("Successfully finished computations")
            goto(DInitialized) using DUnInitialized
          } else {
            startShifComputation()
            goto(DPendingShiftComputation) using data.copy(successExecutorIds = List.empty, activeVertices = 0)
          }
        } else {
          startGlobalComputation(currentSuperstepId)
          goto(DPendingGlobalComputation) using data.copy(successExecutorIds = List.empty, activeVertices = 0)
        }
      } else {
        stay() using data.copy(successExecutorIds = newList, activeVertices = newActiveVertices + currentActiveVertices)
      }

    case Event(StartSuperstepFailed(executorId, message), data @ DComputationData(_, _, _, currentSuperstepId)) =>
      logError(s"Executor: $executorId failed in executing superstep: $currentSuperstepId, detailed message: $message")
      masterRef ! StartComputationFailed(s"Executor: $executorId failed in executing superstep: $currentSuperstepId, detailed message: $message")
      goto(DExceptionState) using DUnInitialized

    case Event(StateTimeout, data @ DComputationData(_, _, _, currentSuperstepId)) =>
      logError(s"Time out in waiting executors to execute the superstep: $currentSuperstepId")
      masterRef ! StartComputationFailed(s"Time out in waiting executors to execute the superstep: $currentSuperstepId")
      goto(DExceptionState) using DUnInitialized
  }

  when(DPendingGlobalComputation, stateTimeout = conf.executorAggregatorComputerTimeout seconds) {
    case Event(StartGlobalComputationSuccsss(message), data @ DComputationData(_, _, _, currentSuperstepId)) =>
      logInfo(s"Global executor succeeded in executing superstep: $currentSuperstepId")
      val newSuperstepId = currentSuperstepId + 1
      startSuperstep(newSuperstepId)
      goto(DPendingSuperstepComputation) using data.copy(superstepId = newSuperstepId)

    case Event(StartGlobalComputationFailed(message), data @ DComputationData(_, _, _, currentSuperstepId)) =>
      logError(s"Global executor succeeded in executing superstep: $currentSuperstepId, detailed message: $message")
      masterRef ! StartComputationFailed(s"Global executor succeeded in executing superstep: $currentSuperstepId, detailed message: $message")
      goto(DExceptionState) using DUnInitialized

    case Event(StateTimeout,  data @ DComputationData(_, _, _, currentSuperstepId)) =>
      logError(s"Time out in waiting globall executor to execute the superstep: $currentSuperstepId")
      masterRef ! StartComputationFailed(s"Time out in wating glbal executor to execute the superstep: $currentSuperstepId")
      goto(DExceptionState) using DUnInitialized
  }

  when(DPendingShiftComputation) {
    case Event(StartShiftComputerSuccess(executorId, message),
    data @ DComputationData(currentSuccessIds, _, currentComputationId, _)) =>
      logInfo(s"Executor: $executorId succeeded in shifting the computer")
      val newList = currentSuccessIds :+ executorId
      if (newList.size == vertexExecutorMap.size) {
        val newSuperstepId = 1
        val newComputationId = currentComputationId - 1
        startSuperstep(newSuperstepId)
        goto(DPendingSuperstepComputation) using DComputationData(List.empty, 0, newComputationId, newSuperstepId)
      } else {
        stay() using data.copy(successExecutorIds = newList)
      }

    case Event(StartShiftComputerFailed(executorId, message),
    data @ DComputationData(_, _, currentComputationId, _)) =>
      logError(s"Executor: $executorId failed to shift the computer, detailed message: $message")
      masterRef ! JobSubmissionFailed(s"Executor: $executorId failed to shift the computer, detailed message: $message")
      goto(DExceptionState) using DUnInitialized
  }

  when(DExceptionState) {
    case Event(s, d) =>
      logError("The driver has been in an exception state")
      stay()
  }

  def startLoadData(): Unit = {
    logInfo("Ask executors to load data...")
    vertexExecutorMap.foreach { case (_, executor) =>
      executor ! StartLoadingData
    }
  }

  def startSuperstep(superstepId: Long): Unit = {
    logInfo("Ask executors to start superstep...")
    vertexExecutorMap.foreach { case(_, executor) =>
      executor ! StartSuperstep(superstepId)
    }
  }

  def startGlobalComputation(superstepId: Long): Unit = {
    logInfo("Ask executors to start global computation...")
    globalExecutor ! StartGlobalComputation(superstepId)
  }

  def startShifComputation(): Unit = {
    logInfo("Ask executors to shift computation...")
    vertexExecutorMap.foreach { case(_, executor) =>
      executor ! StartShiftComputer
    }
  }
}
