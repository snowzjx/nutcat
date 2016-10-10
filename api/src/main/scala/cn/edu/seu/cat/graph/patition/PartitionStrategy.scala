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

package cn.edu.seu.cat.graph.patition

/**
 * Created by snow on 15/6/10.
 */

/**
 * The trait is used as the graph partition strategy.
 * @tparam VERTEX The type of vertexId.
 */
trait PartitionStrategy[VERTEX] {

  /**
   * Set all the executor' id.
   * @param ids The executor'id as a list.
   */
  def setAvaibleExecutorIds(ids: List[String])

  /**
   * The partition function
   * @param vertex Given a vertex, let's choose which worker to select.
   * @return The selected worker's id.
   */
  def partition(vertex: VERTEX) : String
}