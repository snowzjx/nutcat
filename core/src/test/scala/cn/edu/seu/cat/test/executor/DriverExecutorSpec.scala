package cn.edu.seu.cat.test.executor

import akka.actor.{Props, Actor, ActorSystem}
import akka.testkit.{TestFSMRef, ImplicitSender, TestKit}
import cn.edu.seu.cat.common.CatConf
import cn.edu.seu.cat.executor.{DInitialized, DriverExecutor}
import cn.edu.seu.cat.operation._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
 * Created by snow on 15/6/13.
 */
class DriverExecutorSpec (_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("DriverExecutorSpec"))
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  class AckActor extends Actor {

    var activeV = 10

    override def receive: Receive = {
      case StartLoadingData =>
        println(s"-- Start load data.")
        sender ! StartLoadingDataSuccess("1", "Success")

      case StartSuperstep(superstepId) =>
        println(s"-- Start superstep: $superstepId.")
        activeV = activeV - 1
        sender ! StartSuperstepSuccess("1", "Success", activeV)

      case StartGlobalComputation(superstepId) =>
        println(s"-- Start global computation in superstep: $superstepId.")
        sender ! StartGlobalComputationSuccsss("Success")

      case StartShiftComputer =>
        println(s"-- Start shift computer.")
        activeV = 10
        sender ! StartShiftComputerSuccess("1", "Success")
    }
  }

  val ackActor = system.actorOf(Props(new AckActor))

  val conf = new CatConf
  val masterRef = testActor
  val globalExecutorPath = ackActor.path.toString
  val vertexExecutorPathMap = Map("1" -> globalExecutorPath)
  val computationSize = 3

  val driverExecutor = system.actorOf(Props(new DriverExecutor(conf, masterRef, globalExecutorPath, vertexExecutorPathMap, computationSize)))

  "The DriverExecutor" when {
    "Received StartComputation message" should {
      "return StartComputationSuccess message" in {
        driverExecutor ! StartComputation
        expectMsgType[StartComputationSuccess](1000 seconds)
      }
    }
  }
}
