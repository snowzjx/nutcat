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

import java.net.InetSocketAddress
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import cn.edu.seu.cat.common.util.Utils
import cn.edu.seu.cat.common.util.log.Logging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.{ContextHandlerCollection, ErrorHandler}
import org.eclipse.jetty.servlet.{DefaultServlet, ServletContextHandler, ServletHolder}
import org.eclipse.jetty.util.thread.QueuedThreadPool

import scala.xml.Node

/**
 * Created by JunxueZhang on 5/14/15.
 */

private[cat] object JettyUtils extends Logging {

  type Responder[T] = HttpServletRequest => T

  class ServletParams[T <% AnyRef](val responder: Responder[T],
                                   val contentType: String,
                                   val extractFun: T => String = (in: Any) => in.toString)

  implicit def htmlResponderToServlet(responder: Responder[Seq[Node]]): ServletParams[Seq[Node]] =
    new ServletParams(responder, "text/html", (in: Seq[Node]) => "<!DOCTYPE html>" + in.toString)

  implicit def textResponderToServlet(responder: Responder[String]): ServletParams[String] =
    new ServletParams(responder, "text/plain")

  def createServlet[T <% AnyRef](servletParams: ServletParams[T]): HttpServlet = {
    new HttpServlet {
      override def doGet(request: HttpServletRequest, response: HttpServletResponse):Unit = {
        try {
          response.setContentType("%s;charset=utf-8".format(servletParams.contentType))
          response.setStatus(HttpServletResponse.SC_OK)
          val result = servletParams.responder(request)
          response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
          response.getWriter.println(servletParams.extractFun(result))
        } catch {
          case e: IllegalArgumentException =>
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage)
          case e: Exception =>
            logWarning(s"GET ${request.getRequestURI} failed: $e", e)
            throw e
        }
      }
    }
  }

  def createServletHandler[T <% AnyRef](path: String,
                                        servletParams: ServletParams[T],
                                        basePath: String = ""): ServletContextHandler = {
    createServletHandler(path, createServlet(servletParams), basePath)
  }

  def createServletHandler(path: String,
                           servlet: HttpServlet,
                           basePath: String): ServletContextHandler = {
    val prefixedPath = attachPrefix(basePath, path)
    val contextHandler = new ServletContextHandler
    val holder = new ServletHolder(servlet)
    contextHandler.setContextPath(prefixedPath)
    contextHandler.addServlet(holder, "/")
    contextHandler
  }

  def createStaticHandler(resourceBase: String, path: String): ServletContextHandler = {
    val contextHandler = new ServletContextHandler
    contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.gzip", "false")
    val staticHandler = new DefaultServlet
    val holder = new ServletHolder(staticHandler)
    Option(Utils.getResource(resourceBase)) match {
      case Some(res) =>
        holder.setInitParameter("resourceBase", res.toString)
      case None =>
        throw new Exception("Could not find resource path for Web UI: " + resourceBase)
    }
    contextHandler.setContextPath(path)
    contextHandler.addServlet(holder, "/")
    contextHandler
  }

  private def attachPrefix(basePath: String, relativePath: String): String = {
    if (basePath == "") relativePath else (basePath + relativePath).stripSuffix("/")
  }

  def startJettyServer(hostName: String,
                       port: Int,
                       handlers: Seq[ServletContextHandler],
                       serverName: String = ""): ServerInfo = {
    val collection = new ContextHandlerCollection
    collection.setHandlers(handlers.toArray)

    def connect(currentPort: Int): (Server, Int) = {
      val server = new Server(new InetSocketAddress(hostName, currentPort))
      val pool = new QueuedThreadPool
      pool.setDaemon(true)
      server.setThreadPool(pool)
      val errorHandler = new ErrorHandler()
      errorHandler.setShowStacks(true)
      server.addBean(errorHandler)
      server.setHandler(collection)
      try {
        server.start()
        (server, server.getConnectors.head.getLocalPort)
      } catch {
        case e: Exception =>
          server.stop()
          pool.stop()
          throw e
      }
    }
    val (server, boundPort) = Utils.startServiceOnPort[Server](port, connect, serverName)
    ServerInfo(server, boundPort, collection)
  }

  private[cat] case class ServerInfo(server: Server,
                                     boundPort: Int,
                                     rootHandler: ContextHandlerCollection)
}