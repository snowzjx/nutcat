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

package cn.edu.seu.cat.job

import cn.edu.seu.cat.CatJobSubmit
import cn.edu.seu.cat.graph.{AggregatorComputer, VertexComputer, GraphLoader}
import cn.edu.seu.cat.graph.patition.PartitionStrategy

import scala.collection.immutable.Queue

/**
 * Created by JunxueZhang on 2/2/15.
 */

class CatJob(val conf: JobConf,
             val jobDescription: String,
             partitionStrategyClass: Class[_ <: PartitionStrategy[_]],
             graphLoaderClass: Class[_ <: GraphLoader[_, _]],
             computationClassQueue: Queue[(Class[_ <: VertexComputer[_, _, _, _]], Option[Class[_ <: AggregatorComputer[_, _]]])]) extends Serializable {

  val partitionStrategy: String = partitionStrategyClass.getName

  val graphLoader: String = graphLoaderClass.getName

  val computationQueue: Queue[(String, Option[String])] = computationClassQueue.map { case (vertexComputerClass, aggregatorComputerClass) =>
      val aggregatorComputer = aggregatorComputerClass match {
        case Some(value) => Some(value.getName)
        case None => None
      }
    (vertexComputerClass.getCanonicalName, aggregatorComputer)
  }
}

object CatJob {
  def createJob(conf: JobConf,
                jobDescription: String,
                partitionStrategyClass: Class[_ <: PartitionStrategy[_]],
                graphLoaderClass: Class[_ <: GraphLoader[_, _]],
                computationClassQueue: Queue[(Class[_ <: VertexComputer[_, _, _, _]], Option[Class[_ <: AggregatorComputer[_, _]]])]): CatJob = {
    new CatJob(conf, jobDescription, partitionStrategyClass, graphLoaderClass, computationClassQueue)
  }

  def submitJob(job: CatJob): Unit = {
    CatJobSubmit.submitJob(job)
  }
}
