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

import java.net.{BindException, URL}

import cn.edu.seu.cat.common.util.log.Logging
;

/**
 * Created by JunxueZhang on 1/16/15.
 */

object Utils extends Logging {
  def startServiceOnPort[T](port: Int,
                            startService: Int => (T, Int),
                            serviceName: String = ""): (T, Int) = {
    val serviceString = if(serviceName == null) {""} else {s" $serviceName"}
    try {
      val (service, actualPort) = startService(port)
      logInfo(s"Success start service$serviceString on port $port.")
      (service, actualPort)
    } catch {
      case e: BindException =>
        val exceptionMessage = s"Start service$serviceString failed."
        val exception = new BindException(exceptionMessage)
        exception.setStackTrace(e.getStackTrace)
        throw exception
    }
  }

  def getFormattedClassName(obj: AnyRef): String = {
    obj.getClass.getSimpleName.replace("$", "")
  }

  def getResource(resourceName: String): URL = {
    Thread.currentThread.getContextClassLoader.getResource(resourceName)
  }


  def getExecutorActorPath(suffix: String,
                           host: String,
                           port: Int,
                           id: String): String = {
    getAkkaActorPath(getExecutorAkkaSystemName(suffix), host, port, id)
  }

  def getExecutorAkkaSystemName(suffix: String): String = {
    s"executor-$suffix"
  }

  def getAkkaActorPath(systemName: String,
                       host: String,
                       port: Int,
                       actorName: String)  = {
    s"akka.tcp://$systemName@$host:$port/user/$actorName"
  }
}
