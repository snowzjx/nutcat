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

package cn.edu.seu.cat.tool

import java.io.File
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.job.JobConf
import scopt.OptionParser

import scala.io.Source

/**
 * Created by JunxueZhang on 2/2/15.
 */

object GenericOptionsParser extends Logging {

  case class Config(jar: File = new File("."), submitAkkaPath:String = "", kwargs: Map[String, String] = Map())

  val parser = new OptionParser[Config]("nutcat") {
    opt[File]('j', "jar") required() valueName "<jar file path>" action { (x, c) =>
      c.copy(jar = x)
    } text "main class is a required property"
    opt[String]('p', "path") required() valueName "<submit akka path>" action { (x, c) =>
      c.copy(submitAkkaPath = x)
    }
    opt[Map[String, String]]("kwargs") valueName "k1=v1, k2=v2 ..." action { (x, c) =>
      c.copy(kwargs = x)
    } text "other arguments"
    help("help") text "prints this usage text"
  }

  def parse(args: Array[String]): Option[(JobConf, Map[String, String])] = {
    parser.parse(args, Config()) match {
      case Some(config: Config) =>
        val jarFileBytes = Source.fromFile(config.jar, "ISO-8859-1").map(_.toByte).toArray
        val jobConf = new JobConf(jarFileBytes, config.submitAkkaPath)
        Some((jobConf, config.kwargs))
      case None =>
        logError("Wrong args")
        None
    }
  }
}
