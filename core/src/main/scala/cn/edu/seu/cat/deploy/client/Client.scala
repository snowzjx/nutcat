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

package cn.edu.seu.cat.deploy.client

import java.io.File
import java.net.URLClassLoader

import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.Utils
import cn.edu.seu.cat.common.util.log.Logging
import scopt.OptionParser

import scala.collection.{mutable => m}

/**
 * Created by JunxueZhang on 2/2/15.
 */

object Client extends Logging {

  case class Config(conf: Option[File] = None, jar: File = new File("."), mainClass: String = "", kwargs: Map[String, String] = Map())

  val parser = new OptionParser[Config]("nutcat client") {
    head("nutcat client")
    opt[File]('c', "conf") valueName "<file>" action { (x, c) =>
      c.copy(conf = Some(x))
    } text "optional file to merge with the default config file"
    opt[File]('j', "jar") required() valueName "<file>" action { (x, c) =>
      c.copy(jar = x)
    } text "jar is a required file property"
    opt[String]('m', "mainClass") required() valueName "<class name>" action { (x, c) =>
      c.copy(mainClass = x)
    } text "main class is a required property"
    opt[Map[String, String]]("kwargs") valueName "k1=v1, k2=v2 ..." action { (x, c) =>
      c.copy(kwargs = x)
    } text "other arguments"
    help("help") text "prints this usage text"
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, Config()) match {
      case Some(config: Config) => {
        if (config.jar.exists) {
          val catConf = new CatConf(config.conf)
          val url = config.jar.toURI.toURL
          val classLoader = new URLClassLoader(Array(url) , Thread.currentThread.getContextClassLoader)
          val mainClass = Class.forName(config.mainClass, true, classLoader)
          val mainMethod = mainClass.getMethod("main", Array("").getClass)
          val configList = m.ArrayBuffer("--jar", config.jar.getCanonicalPath)
          configList ++= m.ArrayBuffer("--path", Utils.getAkkaActorPath(catConf.masterActorSystemName, catConf.masterAddress, catConf.masterPort, catConf.masterActorName))
          if(config.kwargs.nonEmpty) {
            configList += ("--kwargs", config.kwargs.map{case(key, value) => s"$key=$value"}.mkString(","))
          }
          try {
            mainMethod.invoke(None, configList.toArray)
          } catch {
            case e: Exception => logError("Error in running the new jar", e)
          }
        } else {
          logError("Jar file doesn't exist")
        }
      }
      case None => logError("Wrong args for cat client")
    }
  }
}
