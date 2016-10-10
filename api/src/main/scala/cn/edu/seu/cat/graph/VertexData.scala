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

import cn.edu.seu.cat.graph.VertexStatus.VertexStatus

/**
 * Created by JunxueZhang on 1/19/15.
 */

/**
 * The VertexData is the API for one vertex with the Graph.
 * It consists of the vertex itself and the connected vertices.
 * It also contains the user defined data.
 * @tparam VERTEX The type of vertexId.
 * @tparam DATA The type of user defined data.
 * @param vertexId The vertexId.
 * @param vertexStatus The status of a vertex, if Active it will paricipate in the superstep.
 * @param data The user defined data.
 * @param connectedVertices The connected vertices.
 */

final case class VertexData[VERTEX, DATA](vertexId: VERTEX,
                                          vertexStatus: VertexStatus,
                                          data: DATA,
                                          connectedVertices: Set[VERTEX]) {

  /**
   * The function will change the state of the current vertex and return the changed one.
   * @param newStatus The new status.
   * @return The changed one.
   */
  def changeStatus(newStatus: VertexStatus) = {
    new VertexData[VERTEX, DATA](vertexId, newStatus, data, connectedVertices)
  }

  /**
   * The function will chanage the status of current vertex into InActive.
   * @return The changed one.
   */
  def voltToHalt() = {
    changeStatus(VertexStatus.INACTIVE)
  }
}