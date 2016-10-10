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

import akka.actor.ActorRef
import cn.edu.seu.cat.api.v1.{JsonRootResource, UIRoot}
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.ui.about.AboutPage
import cn.edu.seu.cat.ui.status.StatusPage

/**
 * Created by JunxueZhang on 5/17/15.
 */

private[cat] class CatUI(val catConf: CatConf,
                         val masterActorRef: ActorRef)
  extends WebUI(rootUrl = catConf.uiRoot, port = catConf.uiPort ,name = "NutCat") with UIRoot with Logging {

  UIUtils.proxyPath = Some(catConf.uiProxyPath)

  override def initialize(): Unit = {
    attachHandler(JettyUtils.createStaticHandler("static","/static"))
    attachHandler(JsonRootResource.getJsonServlet(this))
    attachPage(new CatPage(this))
    attachPage(new StatusPage(this))
    attachPage(new AboutPage(this))
  }

  override def getMasterActorRef(): Option[ActorRef] = {
    Some(masterActorRef)
  }
}
