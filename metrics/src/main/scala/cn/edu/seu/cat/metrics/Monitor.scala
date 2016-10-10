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

package cn.edu.seu.cat.metrics

import java.util.Date

import cn.edu.seu.cat.common.util.log.Logging

/**
 * Created by JunxueZhang on 5/13/15.
 */

class Monitor extends Logging {
  val sigar = SigarUtil.getSigar

  def getMetrics: Metrics = {
    val cpuUser = sigar.getCpuPerc.getUser.toString
    val memoryUsed = sigar.getMem.getActualUsed.toString
    val memoryTotal = sigar.getMem.getTotal.toString
    val totalNetworkUp = sigar.getNetStat.getAllOutboundTotal.toString
    val totalNetworkDown = sigar.getNetStat.getAllInboundTotal.toString
    Metrics(cpuUser, memoryUsed, memoryTotal, totalNetworkUp, totalNetworkDown, new Date())
  }
}
