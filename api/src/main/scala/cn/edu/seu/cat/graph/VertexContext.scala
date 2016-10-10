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

package cn.edu.seu.cat.graph

/**
 * Created by JunxueZhang on 1/18/15.
 */

import scala.collection.{mutable => m}

/**
 * The context object used in computation.
 * @tparam VERTEX The type of vertexId.
 * @tparam DATA The type of user defined data.
 * @tparam MSG The type of message sent to other vertices.
 * @tparam GMSG The type of message used for global computation.
 */
class VertexContext[VERTEX, DATA, MSG, GMSG] {

  val outMessageList = new m.ListBuffer[(VERTEX, MSG)]

  var globalMessage: Option[GMSG] = None

  var newVertexData: Option[VertexData[VERTEX, DATA]] = None

  /**
   * The function is used to send message to other vertices.
   * @param message The message to send.
   * @param toVertex The target vertexId.
   * @return
   */
  def writeMessage(message: MSG, toVertex: VERTEX) = {
    val messageToWrite = (toVertex, message)
    outMessageList += messageToWrite
  }

  /**
   * The function is used to send global message.
   * @param message The message to send.
   */
  def writeGlobalMessage(message: GMSG) = {
    globalMessage = Some(message)
  }

  /**
   * The function is used to save the new vertex.
   * When you changed some data of the current vertex, you should call this method to save it.
   * @param newVertex The vertexData to save.
   */
  def writeNewVertexData(newVertex: VertexData[VERTEX, DATA]) = {
    newVertexData = Some(newVertex)
  }
}
