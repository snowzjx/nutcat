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

package cn.edu.seu.cat.api.v1

import javax.ws.rs.core.Response
import javax.ws.rs.{Path, WebApplicationException}

import com.sun.jersey.spi.container.servlet.ServletContainer
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}

/**
 * Created by JunxueZhang on 5/19/15.
 */

@Path("/v1")
private[v1] class JsonRootResource extends UIRootFromServletContext {

  @Path("metrics")
  def getMetrics(): MetricsResource = {
    new MetricsResource(uiRoot)
  }
}

private[cat] object JsonRootResource {
  def getJsonServlet(uiRoot: UIRoot): ServletContextHandler = {
    val jerseyContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    jerseyContext.setContextPath("/json")
    val holder: ServletHolder = new ServletHolder(classOf[ServletContainer])
    holder.setInitParameter("com.sun.jersey.config.property.resourceConfigClass",
      "com.sun.jersey.api.core.PackagesResourceConfig")
    holder.setInitParameter("com.sun.jersey.config.property.packages",
      "cn.edu.seu.cat.api.v1")
    UIRootFromServletContext.setUiRoot(jerseyContext, uiRoot)
    jerseyContext.addServlet(holder, "/*")
    jerseyContext
  }
}

private[v1] class NotFoundException(msg: String) extends WebApplicationException(
  new NoSuchElementException(msg),
  Response
    .status(Response.Status.NOT_FOUND)
    .entity(ErrorWrapper(msg))
    .build()
)

private[v1] class BadParameterException(msg: String) extends WebApplicationException(
  new IllegalArgumentException(msg),
  Response
    .status(Response.Status.BAD_REQUEST)
    .entity(ErrorWrapper(msg))
    .build()
) {
  def this(param: String, exp: String, actual: String) = {
    this(raw"""Bad value for parameter "$param".  Expected a $exp, got "$actual"""")
  }
}

private[v1] case class ErrorWrapper(s: String)
