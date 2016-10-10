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
 * Created by JunxueZhang on 1/31/15.
 */

/**
 * The trait is used for aggregations.
 * It will first aggregate the messages with workers, then the master will aggregate the messages sent from workers.
 * @tparam VERTEX The type of vertexId.
 * @tparam GMSG The type of message used for global computation.
 */
trait AggregatorComputer[VERTEX, GMSG] {

  /**
   * The computation function in aggregation computations
   * @param superStepId current superstepId
   * @param inMessageList the received global messages
   * @return aggregator value. If no result, return None
   */
  def compute(superStepId: Long,
              inMessageList: List[(VERTEX, GMSG)]): Option[(VERTEX, GMSG)]
}
