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

package cn.edu.seu.cat

import akka.actor.{Actor, ActorSystem, Props}
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.job.CatJob
import cn.edu.seu.cat.operation.{JobSubmissionFailed, JobSubmissionSuccess, JobSubmission}
import com.typesafe.config.ConfigFactory

/**
 * Created by JunxueZhang on 2/2/15.
 */

class CatJobSubmit(val catJob: CatJob) extends Actor with Logging {

  val masterAkkaUrl = catJob.conf.submitAkkaPath
  val master = context.actorSelection(masterAkkaUrl)

  override def preStart = {
    super.preStart
    master ! JobSubmission(job = catJob)
  }

  override def receive: Receive = {
    case JobSubmissionSuccess(message: String) => {
      catLog.info(message)
    }
    case JobSubmissionFailed(message: String) => {
      catLog.error(message)
    }
  }
}

object CatJobSubmit {
  def submitJob(job: CatJob): Unit = {
    val akkaConf = ConfigFactory.parseString(
      s"""
         |akka.actor.provider = "akka.remote.RemoteActorRefProvider"
         |akka.remote.netty.tcp.transport-class = "akka.remote.transport.netty.NettyTransport"
         |akka.remote.netty.tcp.hostname = "127.0.0.1"
         |akka.remote.netty.tcp.port = 0
         |akka.loglevel = "ERROR"
         |akka.remote.netty.tcp.maximum-frame-size = 1280000b
     """.stripMargin)
    val actorSystem = ActorSystem("Job-Submit", akkaConf)
    actorSystem.actorOf(Props(new CatJobSubmit(job)))
  }
}
