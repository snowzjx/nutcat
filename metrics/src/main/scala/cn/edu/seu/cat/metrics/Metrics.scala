package cn.edu.seu.cat.metrics

import java.util.Date

/**
 * Created by snow on 15/6/15.
 */
case class Metrics(cpuUser: String,
                   memoryUsed: String,
                   memoryTotal: String,
                   totalNetworkUp: String,
                   totalNetworkDown: String,
                   time: Date) extends Serializable
