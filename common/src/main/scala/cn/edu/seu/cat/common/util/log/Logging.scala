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

package cn.edu.seu.cat.common.util.log

/**
 * Created by JunxueZhang on 1/16/15.
 */

import org.slf4j.{Logger, LoggerFactory}

trait Logging {

  @transient private var log_ : Logger = null

  protected def logName: String = {
    this.getClass.getName.stripSuffix("$")
  }

  protected def catLog: Logger = {
    if (log_ == null) {
      log_ = LoggerFactory.getLogger(logName)
    }
    log_
  }

  protected def logInfo(msg: => String): Unit = {
    if (catLog.isInfoEnabled) {
      catLog.info(msg)
    }
  }

  protected def logInfo(msg: => String, throwable: Throwable): Unit = {
    if (catLog.isInfoEnabled) {
      catLog.info(msg, throwable)
    }
  }

  protected def logDebug(msg: => String): Unit = {
    if (catLog.isDebugEnabled) {
      catLog.debug(msg)
    }
  }

  protected def logDebug(msg: => String, throwable: Throwable): Unit = {
    if (catLog.isDebugEnabled) {
      catLog.debug(msg, throwable)
    }
  }

  protected def logTrace(msg: => String): Unit = {
    if (catLog.isTraceEnabled) {
      catLog.trace(msg)
    }
  }

  protected def logTrace(msg: => String, throwable: Throwable): Unit = {
    if(catLog.isTraceEnabled) {
      catLog.trace(msg, throwable)
    }
  }

  protected def logWarning(msg: => String): Unit = {
    if (catLog.isWarnEnabled) {
      catLog.warn(msg)
    }
  }

  protected def logWarning(msg: => String, throwable: Throwable): Unit = {
    if (catLog.isWarnEnabled) {
      catLog.warn(msg, throwable)
    }
  }

  protected def logError(msg: => String): Unit = {
    if (catLog.isErrorEnabled) {
      catLog.error(msg)
    }
  }

  protected def logError(msg: => String, throwable: Throwable): Unit = {
    if (catLog.isErrorEnabled) {
      catLog.error(msg, throwable)
    }
  }
}