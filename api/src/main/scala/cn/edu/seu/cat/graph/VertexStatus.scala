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
 * Created by JunxueZhang on 1/21/15.
 */

/**
 * The vertex status.
 * ACTIVE means it will participate in the supersteps.
 * INACTIVE means it will not participate in the computation.
 * When an INACTIVE vertex receives messages, it will turn ACTIVE automatically.
 */
object VertexStatus extends Enumeration {
  type VertexStatus = Value
  val ACTIVE, INACTIVE = Value
}
