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
 * Created by JunxueZhang on 1/17/15.
 */

/**
 * The trait is used for vertex computation.
 * The compute function will be called in each superstep.
 * @tparam VERTEX The type of vertexId.
 * @tparam DATA The type of user defined data.
 * @tparam MSG  The type of message sent to other vertices.
 * @tparam GMSG The type of message used for global computation.
 */
trait VertexComputer[VERTEX, DATA, MSG, GMSG] {

  /**
   * The compute function will be called in each superstep.
   * @param superStepId The current superstepId, it starts from 1.
   * @param vertexData Current vertex to compute in the graph.
   * @param inMessageList The messages sent from other vertices.
   * @param globalMessage The message sent from the master.
   * @param context The context object.
   */
  def compute(superStepId: Long,
              vertexData: VertexData[VERTEX, DATA],
              inMessageList: List[(VERTEX, MSG)],
              globalMessage: Option[GMSG],
              context: VertexContext[VERTEX, DATA, MSG, GMSG])
}