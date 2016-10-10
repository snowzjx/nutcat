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

package cn.edu.seu.cat.ui.status

import javax.servlet.http.HttpServletRequest

import cn.edu.seu.cat.ui.{CatUI, UIUtils, WebUIPage}

import scala.xml.Node

/**
 * Created by JunxueZhang on 5/18/15.
 */

class StatusPage(parent: CatUI) extends WebUIPage("status") {
  override def render(request: HttpServletRequest): Seq[Node] = {
    val content =
      <div class="header">
        <h1>Cat Status</h1>
      </div>
        <div style="margin: 20px">
          <div class="pure-g">
            <div class="pure-u-1-2">
              <div id="cpu" style="min-width:400px;height:400px"></div>
            </div>
            <div class="pure-u-1-2">
              <div id="memory" style="min-width:400px;height:400px"></div>
            </div>
            <div class="pure-u-1-2">
              <div id="net-up" style="min-width:400px;height:400px"></div>
            </div>
            <div class="pure-u-1-2">
              <div id="net-down" style="min-width:400px;height:400px"></div>
            </div>
          </div>
        </div>
        <script>
          {
            s"""
              |ajaxAddress = '${UIUtils.preparePath("/json/v1/metrics")}'
            """.stripMargin}
        </script>
        <script src="/static/js/status.js"></script>
    UIUtils.basicCatPage(content,"NutCat Status", UIUtils.statusCatMenu)
  }
}
