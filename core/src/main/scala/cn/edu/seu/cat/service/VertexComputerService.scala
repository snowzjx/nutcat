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

package cn.edu.seu.cat.service

import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSelection
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.graph._
import cn.edu.seu.cat.graph.patition.PartitionStrategy
import cn.edu.seu.cat.operation.{BufferedSuperstepCommunication, GlobalCommunication}
import cn.edu.seu.cat.service.util.MessageBuffer

import scala.collection.parallel.immutable.ParSeq


/**
 * Created by JunxueZhang on 15/6/12.
 */
class VertexComputerService(val conf: CatConf,
                            val superstepId: Long,
                            val graphData: ParSeq[VertexData[Any, Any]],
                            val vertexComputer: VertexComputer[Any, Any, Any, Any],
                            val aggregatorComputer: Option[AggregatorComputer[Any, Any]],
                            val messageBuffer: Map[Any, List[(Any, Any)]],
                            val globalMessage: Option[Any],
                            val globalExecutor: ActorSelection,
                            val vertexExecutorMap: Map[String, ActorSelection],
                            val partitionStrategy: PartitionStrategy[Any]) extends Logging {
  val activeVerticesNum: AtomicLong = new AtomicLong(0L)

  val superstepCommunicationQueue =
    new MessageBuffer[String, (Any, Any, Any)](conf.executorBufferSize,
      conf.executorCommunicationTimeout,
      (executorId: String) => {
        vertexExecutorMap(executorId)
      },
      (messages: List[((Any, Any, Any))]) => {
        BufferedSuperstepCommunication(superstepId, messages)
      })

  val globalCommunicationMapQueue =
    new MessageBuffer[Any, (Any, Any)](Int.MaxValue,
      conf.executorCommunicationTimeout,
      (to: Any) => globalExecutor,
      (messages: List[(Any, Any)]) => {
        val result = aggregatorComputer match {
          case Some(computer) =>
            computer.compute(superstepId, messages)
          case None =>
            None
        }
        GlobalCommunication(superstepId, result)
      })

  def compute(): (Long, ParSeq[VertexData[Any, Any]]) = {
    logInfo(s"Received ${messageBuffer.size} to process")
    val startTimeMillis = System.currentTimeMillis()
    val newGraphData = graphData.map { vertexData =>
      val vertexId = vertexData.vertexId
      val vertexContext = new VertexContext[Any, Any, Any, Any]
      val vertexStatus = if (messageBuffer.contains(vertexId) || vertexData.vertexStatus == VertexStatus.ACTIVE) {
        VertexStatus.ACTIVE
      }
      else {
        VertexStatus.INACTIVE
      }
      val vertexDataToComputer = if (vertexStatus == VertexStatus.ACTIVE) {
        vertexData.changeStatus(VertexStatus.ACTIVE)
      } else {
        vertexData
      }
      if (vertexStatus == VertexStatus.ACTIVE) {
        val inMessageMapParam = messageBuffer.getOrElse(vertexId, List.empty[(Any, Any)])
        vertexComputer.compute(superstepId, vertexDataToComputer, inMessageMapParam, globalMessage, vertexContext)
      }
      val newVertexData = vertexContext.newVertexData match {
        case Some(newData) => newData
        case None => vertexData
      }

      if (vertexStatus == VertexStatus.ACTIVE) {
        activeVerticesNum.addAndGet(1)
        vertexContext.globalMessage match {
          case Some(message) =>
            globalCommunicationMapQueue.sendMessage("global", (vertexId, message))
          case _ =>
        }
        vertexContext.outMessageList.foreach { case (toVertex: Any, message: Any) =>
          val executorId = partitionStrategy.partition(toVertex)
          superstepCommunicationQueue.sendMessage(executorId, (vertexId, toVertex, message))
        }
      }
      newVertexData
    }
    superstepCommunicationQueue.flush()
    globalCommunicationMapQueue.flush()
    val endTimeMillis = System.currentTimeMillis()
    logInfo(s"Execution Time Millis: ${endTimeMillis - startTimeMillis}")
    (activeVerticesNum.get, newGraphData)
  }
}
