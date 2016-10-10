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

package cn.edu.seu.cat.common.util

import akka.actor.{ActorRef, ActorSelection, ActorSystem, ExtendedActorSystem}
import akka.pattern.ask
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.exception.CatException
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

/**
 * Created by JunxueZhang on 1/16/15.
 */

private[cat] object AkkaUtils extends Logging {
  def createActorSystem(name: String,
                        host: String,
                        port: Int,
                        conf: CatConf): (ActorSystem, Int) = {
    val startService: Int => (ActorSystem, Int) = {actualPort:Int =>
      _createActorSystem(name, host, actualPort, conf, None)
    }
    Utils.startServiceOnPort(port, startService, name)
  }

  def createActorSystem(name: String,
                        host: String,
                        port: Int,
                        conf: CatConf,
                        classLoader: ClassLoader ): (ActorSystem, Int) = {
    val startService: Int => (ActorSystem, Int) = {actualPort:Int =>
      _createActorSystem(name, host, actualPort, conf, Some(classLoader))
    }
    Utils.startServiceOnPort(port, startService, name)
  }

  private def _createActorSystem(name: String,
                             host: String,
                             port: Int,
                             conf: CatConf,
                             classLoader: Option[ClassLoader]): (ActorSystem, Int) = {

    val akkaConf = ConfigFactory.parseString(
    s"""
       |akka.actor.provider = "akka.remote.RemoteActorRefProvider"
       |akka.remote.netty.tcp.transport-class = "akka.remote.transport.netty.NettyTransport"
       |akka.remote.netty.tcp.hostname = "$host"
       |akka.remote.netty.tcp.port = $port
       |akka.loglevel = "${conf.akkaLogLevel}"
       |akka.remote.netty.tcp.maximum-frame-size = 1280000b
     """.stripMargin)
    val actorSystem = classLoader match {
      case Some(loader)=> ActorSystem(name, akkaConf, loader)
      case None => ActorSystem(name, akkaConf)
    }
    val provider = actorSystem.asInstanceOf[ExtendedActorSystem].provider
    val boundPort = provider.getDefaultAddress.port.get
    (actorSystem, boundPort)
  }

  def askWithReply[T](message: Any,
                      actor: Either[ActorRef, ActorSelection],
                      timeout: FiniteDuration): T = {
    askWithReply[T](message, actor, maxAttempts = 1, retryInterval = 0, timeout)
  }

  def askWithReply[T](message: Any,
                      actor: Either[ActorRef, ActorSelection],
                      maxAttempts: Int,
                      retryInterval: Int,
                      timeout: FiniteDuration): T = {
    if (actor == null) {
      throw new CatException(s"Error sending message [message = $message], as actor is null.")
    }
    var lastException: Exception = null
    for (attempt <- 1 to maxAttempts) {
      try {
        val future = actor.fold(_.ask(message)(timeout), _.ask(message)(timeout))
        val result = Await.result(future, timeout)
        if (result == null) {
          throw new CatException("Actor returns null.")
        }
        return result.asInstanceOf[T]
      } catch {
        case ie: InterruptedException => throw ie
        case e: Exception =>
          lastException = e
          logWarning(s"Error sending message [message = $message] in $attempt attempts", e)
      }
      Thread.sleep(retryInterval)
    }
    throw new CatException(s"Error sending message [message = $message]", lastException)
  }
}
