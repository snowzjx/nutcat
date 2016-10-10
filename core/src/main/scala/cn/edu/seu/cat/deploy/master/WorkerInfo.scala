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
package cn.edu.seu.cat.deploy.master

import akka.actor.ActorRef
import cn.edu.seu.cat.deploy.master.WorkerState.WorkerState
import cn.edu.seu.cat.metrics.Metrics

/**
 * Created by JunxueZhang on 1/18/15.
 */

case class WorkerInfo(id: String,
                      host: String,
                      port: Int,
                      actor: ActorRef,
                      @transient state: WorkerState = WorkerState.ALIVE,
                      @transient lastHeartbeat: Long = System.currentTimeMillis,
                      @transient lastMetrics: Option[Metrics] = None) extends Serializable
