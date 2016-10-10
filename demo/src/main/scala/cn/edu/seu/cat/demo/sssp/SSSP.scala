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

package cn.edu.seu.cat.demo.sssp

import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.graph._
import cn.edu.seu.cat.graph.patition.HashPartitionStrategy
import cn.edu.seu.cat.job.{JobConf, CatJob}
import cn.edu.seu.cat.tool.GenericOptionsParser

import scala.collection.immutable.Queue

/**
 * Created by JunxueZhang on 15/6/4.
 */

class SSSPGraphLoader extends GraphLoader[Long, Unit] {
  /**
   * The function to load the graph.
   * @return The graph data.
   */
  override def loadGraph(): List[VertexData[Long, Unit]] = {
    List(
      VertexData(1L, VertexStatus.ACTIVE, None, Set(2L, 3L)),
      VertexData(2L, VertexStatus.INACTIVE, None, Set(3L, 4L)),
      VertexData(3L, VertexStatus.INACTIVE, None, Set(6L)),
      VertexData(4L, VertexStatus.INACTIVE, None, Set(5L, 6L)),
      VertexData(5L, VertexStatus.INACTIVE, None, Set(7L)),
      VertexData(6L, VertexStatus.INACTIVE, None, Set(7L, 8L)),
      VertexData(7L, VertexStatus.INACTIVE, None, Set(8L)),
      VertexData(8L, VertexStatus.INACTIVE, None, Set.empty[Long])
    )
  }
}

case class SSSPMessage(startsFrom: Long,
                       visited: List[Long],
                       length: Int) extends Serializable

class SSSPVertexComputer extends VertexComputer[Long, Unit, SSSPMessage, String] with Logging {
  /**
   * The compute function will be called in each superstep.
   * @param superStepId The current superstepId, it starts from 1.
   * @param vertexData Current vertex to compute in the graph.
   * @param inMessageList The messages sent from other vertices.
   * @param globalMessage The message sent from the master.
   * @param context The context object.
   */
  override def compute(superStepId: Long,
                       vertexData: VertexData[Long, Unit],
                       inMessageList: List[(Long, SSSPMessage)],
                       globalMessage: Option[String],
                       context: VertexContext[Long, Unit, SSSPMessage, String]): Unit = {
    globalMessage match {
      case Some(message) =>
        context.writeNewVertexData(vertexData.voltToHalt)
        logInfo(message)
        return
      case _ =>
    }
    if (vertexData.vertexId == 1L) {
      val message = SSSPMessage(1L, List(1L), 1)
      vertexData.connectedVertices.foreach { vertex =>
        context.writeMessage(message, vertex)
      }
    }
    else if (vertexData.vertexId == 8L) {
      val message = inMessageList.head._2
      val information = s"SSSP path: ${message.visited.mkString("->")}->8, with the length ${message.length + 1}"
      context.writeGlobalMessage(information)
    } else {
      if(!inMessageList.isEmpty) {
        val theMessage = inMessageList.reduce((m1, m2) => if (m1._2.length < m2._2.length) m1 else m2)
        vertexData.connectedVertices.foreach { connectedVertex =>
          context.writeMessage(SSSPMessage(theMessage._2.startsFrom,
            theMessage._2.visited :+ vertexData.vertexId,
            theMessage._2.length + 1), connectedVertex)
        }
      }
    }
    context.writeNewVertexData(vertexData.voltToHalt)
  }
}


class SSSPAggregatorComputer extends AggregatorComputer[Long, String] {
  /**
   * The computation function in aggregation computations
   * @param superStepId current superstepId
   * @param inMessageList the received global messages
   * @return aggregator value. If no result, return None
   */
  override def compute(superStepId: Long,
                       inMessageList: List[(Long, String)]): Option[(Long, String)] = {
    if (inMessageList.isEmpty) {
      return None
    } else {
      return Some(inMessageList.head)
    }
  }}

object SSSP extends App with Logging {

  GenericOptionsParser.parse(args) match {
    case None => {
      logError("Error")
    }
    case Some((conf: JobConf, otherArgs: Map[String, String])) =>
      println(otherArgs.mkString("-"))

      val job = CatJob.createJob(conf,
        "SSSP Job",
        classOf[HashPartitionStrategy[Long]],
        classOf[SSSPGraphLoader],
        Queue((classOf[SSSPVertexComputer], Some(classOf[SSSPAggregatorComputer]))))
      CatJob.submitJob(job)
  }
}
