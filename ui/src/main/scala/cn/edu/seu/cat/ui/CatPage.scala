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

import scala.xml.Node

/**
 * Created by JunxueZhang on 5/17/15.
 */

private[cat] class CatPage(parent: CatUI) extends WebUIPage("") {
  override def render(request: HttpServletRequest): Seq[Node] = {
    val content =
    <div class="hero">
      <div class="hero-titles">
        <img class="logo" src="/static/img/logo.png" alt="NutCat" />
        <h2 class="hero-tagline">A Pregel-like Graph Processing Framework</h2>
      </div>
      <div class="hero-cta">
        <div class="is-code-full">
          <pre class="code code-wrap" data-language="shell">git clone git@cloud.seu.edu.cn:nutcat/cat.git</pre>
        </div>
        <p>
          <a class="button-cta pure-button" href="#">Get Started</a>
          <a class="button-secondary pure-button" href="https://cloud.seu.edu.cn/gitlab/">View on GitLab</a>
        </p>
      </div>
    </div>
    UIUtils.basicCatPage(content, "NutCat", UIUtils.getStartedCatMenu)
  }
}
