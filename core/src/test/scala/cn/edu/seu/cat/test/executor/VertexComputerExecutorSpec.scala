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

package cn.edu.seu.cat.test.executor

import akka.actor.{Props, Actor, ActorSystem}
import akka.testkit.{TestKit, TestFSMRef, ImplicitSender}
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.executor._
import cn.edu.seu.cat.graph._
import cn.edu.seu.cat.graph.patition.PartitionStrategy
import cn.edu.seu.cat.operation.{BufferedSuperstepCommunication, BufferedSuperstepCommunicationSuccess, StartSuperstep, StartLoadingData}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.immutable.Queue

/**
 * Created by JunxueZhang on 15/6/12.
 */

class VertexComputerExecutorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("VertexComputerExecutorSpec"))

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  class TestGraphLoader extends GraphLoader[Long, Unit] {
    /**
     * The function to load the graph.
     * @return The graph data.
     */
    override def loadGraph(): List[VertexData[Long, Unit]] = {
      List(
        VertexData(1L, VertexStatus.ACTIVE, None, Set(2L, 3L)),
        VertexData(2L, VertexStatus.INACTIVE, None, Set(3L, 4L)),
        VertexData(3L, VertexStatus.INACTIVE, None, Set(6L)),
        VertexData(4L, VertexStatus.INACTIVE, None, Set(5L, 6L)),
        VertexData(5L, VertexStatus.INACTIVE, None, Set(7L)),
        VertexData(6L, VertexStatus.INACTIVE, None, Set(7L, 8L)),
        VertexData(7L, VertexStatus.INACTIVE, None, Set(8L)),
        VertexData(8L, VertexStatus.INACTIVE, None, Set.empty[Long])
      )
    }
  }

  class TestVertexComputer extends VertexComputer[Long, Unit, Long, String] with Logging {
    /**
     * The compute function will be called in each superstep.
     * @param superStepId The current superstepId, it starts from 1.
     * @param vertexData Current vertex to compute in the graph.
     * @param inMessageList The messages sent from other vertices.
     * @param globalMessage The message sent from the master.
     * @param context The context object.
     */
    override def compute(superStepId: Long,
                         vertexData: VertexData[Long, Unit],
                         inMessageList: List[(Long, Long)],
                         globalMessage: Option[String],
                         context: VertexContext[Long, Unit, Long, String]): Unit = {
      inMessageList.map {println _}
      vertexData.connectedVertices foreach { vertexId =>
        context.writeMessage(vertexData.vertexId, vertexId)
      }
    }
  }

  class AckActor extends Actor {
    override def receive: Receive = {
      case BufferedSuperstepCommunication(superstepId, messages) =>
        println(s"In $superstepId, received $messages.")
        Thread.sleep(1000)
        sender ! BufferedSuperstepCommunicationSuccess
    }
  }

  val ackActor = system.actorOf(Props(new AckActor))

  val catConf = new CatConf()
  val master = testActor.path.toString
  val globalExecutor = ackActor.path.toString
  val executorId = "1"
  val executorMap = Map(executorId -> ackActor.path.toString)
  val partitionStrategy = new PartitionStrategy[Long] {
    /**
     * The partition function
     * @param vertex Given a vertex, let's choose which worker to select.
     * @return The selected worker's id.
     */
    override def partition(vertex: Long): String = executorId

    /**
     * Set all the executor' id.
     * @param ids The executor'id as a list.
     */
    override def setAvaibleExecutorIds(ids: List[String]): Unit = {}
  }.asInstanceOf[PartitionStrategy[Any]]
  val graphLoader = new TestGraphLoader().asInstanceOf[GraphLoader[Any, Any]]
  val vertexComputer = new TestVertexComputer().asInstanceOf[VertexComputer[Any, Any, Any, Any]]
  val aggregatorComputer = None


  "The VertexComputerExecutor" when {
    "Initialized" should {
      val vertexComputerExecutor = TestFSMRef(new VertexComputerExecutor(catConf, master, executorId, globalExecutor, executorMap, partitionStrategy, graphLoader, Queue((vertexComputer, aggregatorComputer))))
      "be in state NoData" in {
        assert(vertexComputerExecutor.stateName == VCNoData)
      }
      "has UnInitialized as its data" in {
        assert(vertexComputerExecutor.stateData == VCUninitialized)
      }
    }
    "Received StartLoadingData message" should {
      val vertexComputerExecutor = TestFSMRef(new VertexComputerExecutor(catConf, master, executorId, globalExecutor, executorMap, partitionStrategy, graphLoader, Queue((vertexComputer, aggregatorComputer))))
      vertexComputerExecutor ! StartLoadingData
      "be in state PendingSuperstep" in {
        assert(vertexComputerExecutor.stateName == VCPendingSuperstep)
      }
      "and loads the data" in {
        println(vertexComputerExecutor.stateData)
        assert(vertexComputerExecutor.stateData.isInstanceOf[VCSuperstepData])
      }
    }
    "Received StateComputation message" should {
      val vertexComputerExecutor = TestFSMRef(new VertexComputerExecutor(catConf, master, executorId, globalExecutor, executorMap, partitionStrategy, graphLoader, Queue((vertexComputer, aggregatorComputer))))
      vertexComputerExecutor ! StartLoadingData
      vertexComputerExecutor ! StartSuperstep(1)
      vertexComputerExecutor ! BufferedSuperstepCommunication(0, List((0, 1, 2), (2, 5, 7)))
      vertexComputerExecutor ! BufferedSuperstepCommunication(1, List((0, 1, 2), (2, 5, 7)))
      vertexComputerExecutor ! BufferedSuperstepCommunication(1, List((0, 5, 2), (2, 3, 7)))
      "be in state PendingSuperstep" in {
        assert(vertexComputerExecutor.stateName == VCPendingSuperstep)
      }
      "and computes the data" in {
        println(vertexComputerExecutor.stateData)
        assert(vertexComputerExecutor.stateData.isInstanceOf[VCSuperstepData])
      }
    }
  }
}