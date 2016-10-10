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

package cn.edu.seu.cat.common

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Created by JunxueZhang on 1/16/15.
 */

class CatConf(confFile: Option[File] = None) extends Cloneable with Serializable {

  val defaultConfig = ConfigFactory.load

  val config: Config = confFile.map { value =>
    if (value.exists) {
      ConfigFactory.parseFile(value).withFallback(defaultConfig)
    } else {
      defaultConfig
    }
  }.getOrElse {
    defaultConfig
  }

  //Properties from config file
  lazy val masterActorSystemName = config.getString("cat.master.actorsystem")
  lazy val masterActorName = config.getString("cat.master.actor")
  lazy val masterAddress = config.getString("cat.master.address")
  lazy val masterPort = config.getInt("cat.master.port")

  lazy val workerActorSystemName = config.getString("cat.worker.actorsystem")
  lazy val workerActorName = config.getString("cat.worker.actor")
  lazy val workerAddress = config.getString("cat.worker.address")
  lazy val workerPort = config.getInt("cat.worker.port")

  lazy val workerTimeout = config.getInt("cat.worker.timeout")

  lazy val workerPrepareTimeout = config.getInt("cat.worker.prepare.timeout")

  lazy val workerRegistrationRetries = config.getInt("cat.worker.registration.retries")
  lazy val workerRegistrationInterval = config.getInt("cat.worker.registration.interval")

  lazy val executorThreadNumber = config.getInt("cat.executor.thread.number")

  lazy val executorPortOffset = config.getInt("cat.executor.port.offset")

  lazy val executorBufferSize = config.getInt("cat.executor.buffersize")
  lazy val executorCommunicationTimeout = config.getInt("cat.executor.communication.timeout")

  lazy val executorLoadgraphTimeout = config.getInt("cat.executor.loadgraph.timeout")
  lazy val executorVertexComputerTimeout = config.getInt("cat.executor.vertexcomputer.timeout")
  lazy val executorAggregatorComputerTimeout = config.getInt("cat.executor.aggregatorcomputation.timeout")

  lazy val executorTerminationTimeout = config.getInt("cat.executor.termination.timeout")

  lazy val uiRoot = config.getString("cat.ui.root")
  lazy val uiPort = config.getInt("cat.ui.port")
  lazy val uiProxyPath = config.getString("cat.ui.proxy.path")

  lazy val tmpFolder = config.getString("cat.tmp.folder")

  lazy val akkaLogLevel = config.getString("akka.log.level")
}