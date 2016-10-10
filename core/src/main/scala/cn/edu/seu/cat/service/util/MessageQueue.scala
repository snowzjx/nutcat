package cn.edu.seu.cat.service.util

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentLinkedQueue, Executors}

import akka.actor.ActorSelection
import akka.pattern.ask
import akka.util.Timeout
import cn.edu.seu.cat.operation.{Message, Operation}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Created by JunxueZhang on 15/6/30.
 */
@Deprecated
class MessageQueue[K, V](val bufferSize: Int,
                         val askTimeoutSeconds: Int,
                         val actorSelectFunc: (K) => ActorSelection,
                         val messageFunc: (List[V]) => Operation) {


  implicit val timeout = Timeout(askTimeoutSeconds seconds)

  implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  val cachedMessage: AtomicLong = new AtomicLong(0L)

  val concurrentQueue = new ConcurrentLinkedQueue[(K, V)]
  val messageMap = mutable.Map.empty[K, List[V]]

  val askFutureList = mutable.ListBuffer.empty[Future[Message]]


  def sendMessage(destination: K, message: V) = {
    concurrentQueue.add((destination, message))
    if(cachedMessage.addAndGet(1) == bufferSize) {
      //Only one thread can execute in the block
      this.synchronized {
        println(s"-----${cachedMessage.get()}")
        cachedMessage.addAndGet(-bufferSize)
        for (i <- 1 to bufferSize) {
          val message = concurrentQueue.poll()
          prepareMessageMap(message)
        }
        flushMessageMap()
      }
    }
  }

  def flush() = {
    while (!concurrentQueue.isEmpty) {
      val message = concurrentQueue.poll()
      prepareMessageMap(message)
    }
    flushMessageMap()
    Await.ready(Future.sequence(askFutureList), timeout.duration)
  }

  private def prepareMessageMap(message: (K, V)): Unit = {
    messageMap.get(message._1) match {
      case None =>
        messageMap.update(message._1, List(message._2))
      case Some(list) =>
        messageMap.update(message._1, list :+ message._2)
    }
  }

  private def flushMessageMap(): Unit = {
    messageMap.foreach { case (destination, list) =>
      val future: Future[Message] =
        actorSelectFunc(destination).ask(messageFunc(list)).mapTo[Message]
      askFutureList += future
    }
    messageMap.clear
  }
}
