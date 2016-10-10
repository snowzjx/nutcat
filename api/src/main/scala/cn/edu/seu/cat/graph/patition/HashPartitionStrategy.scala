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


class HashPartitionStrategy[VERTEX] extends PartitionStrategy[VERTEX] {

  var avaibleWorkerIds: List[String] = List.empty

  override def partition(vertex: VERTEX): String = {
    val index = vertex.hashCode % avaibleWorkerIds.size
    avaibleWorkerIds(index)
  }

  override def setAvaibleExecutorIds(ids: List[String]): Unit = {
    avaibleWorkerIds = ids
  }
}
