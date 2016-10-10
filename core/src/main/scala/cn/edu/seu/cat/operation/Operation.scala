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

package cn.edu.seu.cat.operation

import cn.edu.seu.cat.job.CatJob
import cn.edu.seu.cat.metrics.Metrics

/**
 * Created by JunxueZhang on 15/6/11.
 */

sealed trait Operation extends Serializable
sealed trait Message extends Serializable {
  val message: String
}

/**
 * Register Worker
 * Worker -> Master
 */
case class RegisterWorker(id: String, host: String, port: Int) extends Operation
case class RegisterWorkerSuccess(message: String) extends Message
case class RegisterWorkerFailed(message: String) extends Message

/**
 * Heart Beat
 * Worker -> Master
 * We don't need to send success or failed message for heartbeat
 * The metric information is also contained in heartbeat
 */
case class Heartbeat(id: String, metrics: Metrics) extends Operation

/**
 * Ask the workers to prepare for the job
 * Master -> Worker
 */
case class PrepareJob(job:CatJob,
                      executorSystem: String,
                      assignments: Map[String, String],
                      driverPath: String,
                      globalExecutorPath: String,
                      executorPathMap: Map[String, String])
case class PrepareJobSuccess(workerId: String, message: String) extends Message
case class PrepareJobFailed(workerId: String, message: String) extends Message

/**
 * If error occurs or computation finishes
 * The worker should clean its state
 * Master -> Worker
 */
case class CleanState() extends Operation
case class CleanStateAck(workerId: String, message: String) extends Message

/**
 * Inform the driver to start computation
 * Master -> Driver
 */
case class StartComputation() extends Operation
case class StartComputationSuccess(message: String) extends Message
case class StartComputationFailed(message: String) extends Message

/**
 * Inform the workers to load data
 * Driver -> Executor
 */
case class StartLoadingData() extends Operation
case class StartLoadingDataSuccess(executorId: String, message: String) extends Message
case class StartLoadingDataFailed(executorId: String, message: String) extends Message

/**
 * Inform the workers to start supersteps
 * Driver -> Executor
 */
case class StartSuperstep(superstepId: Long) extends Operation
case class StartSuperstepSuccess(executorId: String, message: String, activeVertices: Long) extends Message
case class StartSuperstepFailed(executorId: String, message: String) extends Message

/**
 * Inform the workers to shift computer
 * Driver -> Executor
 */
case class StartShiftComputer() extends Operation
case class StartShiftComputerSuccess(executorId: String, message: String) extends Message
case class StartShiftComputerFailed(executorId: String, message: String) extends Message

/**
 * Inform the worker to start global computation
 * Driver -> Executor
 */
case class StartGlobalComputation(superstepId: Long) extends Operation
case class StartGlobalComputationSuccsss(message: String) extends Message
case class StartGlobalComputationFailed(message: String) extends Message

/**
 * Deliver the communication messages between executors
 * Executor <-> Executor
 */
case class BufferedSuperstepCommunication(superstepId: Long, messages: List[(Any, Any, Any)]) extends Operation
case class BufferedSuperstepCommunicationSuccess(message: String) extends Message
case class BufferedSuperstepCommunicationFailed(message: String) extends Message

/**
 * Deliver the communication messages to global executor
 * The glocal executor will also deliver the result back
 * Executor <-> Executor
 */
case class GlobalCommunication(superstepId: Long, message: Option[(Any, Any)]) extends Operation
case class GlobalCommunicationSuccess(message: String) extends Message
case class GlobalCommunicationFailed(message: String) extends Message