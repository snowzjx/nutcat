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

package cn.edu.seu.cat.ui

import javax.servlet.http.HttpServletRequest

import cn.edu.seu.cat.common.util.Utils
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.ui.JettyUtils.ServerInfo
import org.eclipse.jetty.servlet.ServletContextHandler

import scala.collection.{mutable => m}
import scala.xml.Node

/**
 * Created by JunxueZhang on 5/16/15.
 */

private[cat] abstract class WebUI(rootUrl: String,
                                  port: Int,
                                  basePath: String = "",
                                  name: String = "") extends Logging {
  protected val tabs = m.ArrayBuffer[WebUITab]()
  protected val handlers = m.ArrayBuffer[ServletContextHandler]()
  protected val pageToHandlers = new m.HashMap[WebUIPage, m.ArrayBuffer[ServletContextHandler]]
  protected var serverInfo: Option[ServerInfo] = None

  private val className = Utils.getFormattedClassName(this)

  def getBasePath: String = basePath
  def getTabs: Seq[WebUITab] = tabs.toSeq
  def getHandlers: Seq[ServletContextHandler] = handlers.toSeq

  def boundPort: Int = serverInfo.map(_.boundPort).getOrElse(-1)

  def attachTab(tab: WebUITab): Unit = {
    tab.pages.foreach(attachPage)
    tabs += tab
  }

  def detachTab(tab: WebUITab): Unit = {
    tab.pages.foreach(detachPage)
    tabs -= tab
  }

  def attachPage(page: WebUIPage): Unit = {
    val pagePath = "/" + page.prefix
    val renderHandler = JettyUtils.createServletHandler(pagePath,
      (request: HttpServletRequest) => page.render(request), basePath)
    attachHandler(renderHandler)
    pageToHandlers.getOrElseUpdate(page, m.ArrayBuffer[ServletContextHandler]())
      .append(renderHandler)
  }

  def detachPage(page: WebUIPage): Unit = {
    pageToHandlers.remove(page).foreach(_.foreach(detachHandler))
  }

  def attachHandler(handler: ServletContextHandler): Unit = {
    handlers += handler
    serverInfo.foreach { info =>
      info.rootHandler.addHandler(handler)
      if (!handler.isStarted) {
        handler.start
      }
    }
  }

  def detachHandler(handler: ServletContextHandler): Unit = {
    handlers -= handler
    serverInfo.foreach { info =>
      info.rootHandler.removeHandler(handler)
      if (handler.isStarted) {
        handler.stop
      }
    }
  }

  def initialize()

  def bind(): Unit = {
    assert(!serverInfo.isDefined, s"Attempted to bind $className more than once!")
    try {
      initialize()
      serverInfo = Some(JettyUtils.startJettyServer(rootUrl, port, handlers, name))
      logInfo(s"Started $className at $rootUrl:$port")
    } catch {
      case ex: Exception =>
        logError(s"Failed to bind $className", ex)
        System.exit(1)
    }
  }

}

private[cat] abstract class WebUITab(parent: WebUI, val prefix: String) {
  val pages = m.ArrayBuffer[WebUIPage]()
  val name = prefix.capitalize

  def attachPage(page: WebUIPage): Unit = {
    page.prefix = (prefix + "/" + page.prefix).stripSuffix("/")
    pages += page
  }

}

private[cat] abstract class WebUIPage(var prefix: String) {
  def render(request: HttpServletRequest): Seq[Node]
}
