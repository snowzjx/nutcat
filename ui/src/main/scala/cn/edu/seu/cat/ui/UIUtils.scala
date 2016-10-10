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

import scala.xml.Node

/**
 * Created by JunxueZhang on 5/17/15.
 */

object UIUtils {

  var proxyPath: Option[String] = None

  lazy val getStartedCatMenu = CatMenu("Get Started", link = preparePath("/"))
  lazy val statusCatMenu = CatMenu("Status", link = preparePath("/status"), isDivided = true)
  lazy val aboutCatMenu =  CatMenu("About", link = preparePath("/about"), isDivided = true)

  lazy val catMenuList = List(getStartedCatMenu, statusCatMenu, aboutCatMenu)

  def preparePath(pathToPrepare:String): String = {
    proxyPath match {
      case None => pathToPrepare
      case Some(path) => path + pathToPrepare
    }
  }

  def commonHeaderNodes: Seq[Node] = {
      <meta http-equiv="Content-type" content="text/html; charset=utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <link rel="stylesheet" href="http://fonts.googleapis.com/css?family=Raleway:200" />
      <link rel="stylesheet" href="http://yui.yahooapis.com/pure/0.6.0/pure-min.css" />
      <link rel="stylesheet" href="/static/css/main.css" />
      <script src="http://cdn.hcharts.cn/jquery/jquery-1.8.3.min.js" type="text/javascript"></script>
      <script src="http://cdn.hcharts.cn/highcharts/highcharts.js" type="text/javascript"></script>
      <script src={preparePath("/static/js/main.js")}></script>
  }

  def basicCatPage(content: => Seq[Node], title: String, selectedMenu: CatMenu): Seq[Node] = {
    <html>
      <head>
        {commonHeaderNodes}
      <title>{title}</title>
      </head>
      <body>
        <div id="layout">
        <a href="#menu" id="menuLink" class="menu-link">
          <span></span>
        </a>
        <div id="menu">
          <div class="pure-menu">
            <a class="pure-menu-heading" href="#">NutCat</a>
            <ul class="pure-menu-list">
              {
                catMenuList.map { catMenu =>
                  if (catMenu == selectedMenu) {
                    if (!catMenu.isDivided) {
                      <li class="pure-menu-item pure-menu-selected">
                        <a href={catMenu.link} class="pure-menu-link">
                          {catMenu.displayName}
                        </a>
                      </li>
                    } else {
                      <li class="pure-menu-item pure-menu-selected menu-item-divided">
                        <a href={catMenu.link} class="pure-menu-link">
                          {catMenu.displayName}
                        </a>
                      </li>
                    }
                  } else {
                    if (!catMenu.isDivided) {
                      <li class="pure-menu-item">
                        <a href={catMenu.link} class="pure-menu-link">
                          {catMenu.displayName}
                        </a>
                      </li>
                    } else {
                      <li class="pure-menu-item menu-item-divided">
                        <a href={catMenu.link} class="pure-menu-link">
                          {catMenu.displayName}
                        </a>
                      </li>
                    }
                  }
                }
              }
            </ul>
          </div>
        </div>
        <div id="main">
          {content}
        </div>
      </div>
      </body>
    </html>
  }
}
