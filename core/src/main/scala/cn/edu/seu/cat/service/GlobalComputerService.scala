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

import akka.actor.ActorSelection
import akka.util.Timeout
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.AkkaUtils
import cn.edu.seu.cat.graph.AggregatorComputer
import cn.edu.seu.cat.operation.{GlobalCommunication, Message}

import scala.concurrent.duration._

/**
 * Created by JunxueZhang on 15/6/12.
 */
class GlobalComputerService(val conf: CatConf,
                            val superstepId: Long,
                            val aggregatorComputer: AggregatorComputer[Any, Any],
                            val messageBuffer: List[(Any, Any)],
                            val vertexExecutorMap: Map[String, ActorSelection]) {

  implicit val timeout = Timeout(conf.executorCommunicationTimeout seconds)

  def compute(): Unit = {
    val result = aggregatorComputer.compute(superstepId, messageBuffer)
    vertexExecutorMap.foreach { case (_, executor) =>
      AkkaUtils.askWithReply[Message](GlobalCommunication(superstepId, result), Right(executor), timeout.duration)
    }
  }
}
