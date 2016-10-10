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

import javax.ws.rs.core.MediaType
import javax.ws.rs.{GET, Produces}

import akka.util.Timeout
import cn.edu.seu.cat.common.util.AkkaUtils
import cn.edu.seu.cat.metrics.operation.{QueryMetrics, QueryMetricsSuccess}

import scala.concurrent.duration._


/**
 * Created by JunxueZhang on 5/19/15.
 */

@Produces(Array(MediaType.APPLICATION_JSON))
private[v1] class MetricsResource(uIRoot: UIRoot) {

  val masterActorRef = uIRoot.getMasterActorRef

  implicit val timeout = Timeout(5 seconds)

  @GET
  def getMetrics(): QueryMetricsSuccess = {
    masterActorRef match {
      case Some(master) =>
        AkkaUtils.askWithReply(QueryMetrics, Left(master), timeout.duration)
      case _ =>
        throw new NotFoundException("Master actor not found")

    }
  }
}
