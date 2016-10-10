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

package cn.edu.seu.cat.demo.pagerank

import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.graph._
import cn.edu.seu.cat.graph.patition.HashPartitionStrategy
import cn.edu.seu.cat.job.{JobConf, CatJob}
import cn.edu.seu.cat.tool.GenericOptionsParser

import scala.collection.immutable.{Queue, Set}

/**
 * Created by JunxueZhang on 1/16/15.
 */

class PageRankGraphLoader extends GraphLoader[Long, Double] {
  /**
   * The function to load the graph.
   * @return The graph data.
   */
  override def loadGraph(): List[VertexData[Long, Double]] = {
    io.Source.fromFile("demo_data/PageRank/BigPageRankData").getLines.map { line =>
      val parts: Array[String] = line.toString.split(" ")
      val vertexId = parts(0).toLong
      val connectedVertexSet = if (parts.length < 2) {
        Set.empty[Long]
      } else {
        parts(1).split("-").map(_.toLong).toSet[Long]
      }
      VertexData(vertexId, VertexStatus.ACTIVE, 1.0, connectedVertexSet)
    }.toList
  }
}

class PageRankVertexComputer extends VertexComputer[Long, Double, Double, Nothing] with Logging {
  /**
   * The compute function will be called in each superstep.
   * @param superStepId The current superstepId, it starts from 1.
   * @param vertexData Current vertex to compute in the graph.
   * @param inMessageList The messages sent from other vertices.
   * @param globalMessage The message sent from the master.
   * @param context The context object.
   */
  override def compute(superStepId: Long,
                       vertexData: VertexData[Long, Double],
                       inMessageList: List[(Long, Double)],
                       globalMessage: Option[Nothing],
                       context: VertexContext[Long, Double, Double, Nothing]): Unit = {
    val pageRankValue = if (inMessageList.isEmpty) 0 else {
      inMessageList.foldLeft(0.0) { case (pRVL, (vId, pRVR)) =>
          pRVL + pRVR
      } + vertexData.data
    }
    if (superStepId == 10) {
      context.writeNewVertexData(vertexData.voltToHalt)
    } else {
      val size = vertexData.connectedVertices.size
      vertexData.connectedVertices.foreach { vertexId =>
        context.writeMessage(pageRankValue / size, vertexId)
      }
    }

  }
}

object PageRank extends Logging {

  def main(args: Array[String]): Unit = {

    GenericOptionsParser.parse(args) match {
      case None => {
        logError("Error")
      }
      case Some((conf: JobConf, otherArgs: Map[String, String])) =>
        println(otherArgs.mkString("-"))
        val job = CatJob.createJob(conf,
          "Page Rank Job",
          classOf[HashPartitionStrategy[Long]],
          classOf[PageRankGraphLoader],
          Queue((classOf[PageRankVertexComputer], None)))
        CatJob.submitJob(job)
    }
  }
}