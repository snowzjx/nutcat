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

package cn.edu.seu.cat.service.util

import java.util.concurrent.{ConcurrentHashMap, Executors}

import akka.actor.ActorSelection
import akka.pattern.ask
import akka.util.Timeout
import cn.edu.seu.cat.operation.{Message, Operation}

import scala.collection.convert.decorateAsScala._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Created by JunxueZhang on 15/7/2.
 */
class MessageBuffer[K, V](val bufferSize: Int,
                          val askTimeoutSeconds: Int,
                          val actorSelectFunc: (K) => ActorSelection,
                          val messageFunc: (List[V]) => Operation) {

  implicit val timeout = Timeout(askTimeoutSeconds seconds)
  implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  val messageBuffer = new ConcurrentHashMap[K, MessageBufferEntry[V]].asScala
  val askFutureList = mutable.ListBuffer.empty[Future[Message]]

  def sendMessage(destination: K, message: V) = {
    val messageEntry = messageBuffer.get(destination) match {
      case None =>
        val entryToInsert = new MessageBufferEntry[V]
        messageBuffer.putIfAbsent(destination, entryToInsert) match {
          case None => entryToInsert
          case Some(entry) => entry
        }
      case Some(entry) =>
        entry
    }
    val count = messageEntry.insertMessage(message)
    if (count == bufferSize) {
      /* Can be true only in one thread */
      val future: Future[Message] =
        actorSelectFunc(destination).ask(messageFunc(messageEntry.getMessages(bufferSize))).mapTo[Message]
      askFutureList += future
    }
  }

  def flush(): List[Message] = {
    messageBuffer.foreach { case (destination, entry) =>
      val future: Future[Message] =
        actorSelectFunc(destination).ask(messageFunc(entry.getAllMessages())).mapTo[Message]
      askFutureList += future
    }
    Await.result(Future.sequence(askFutureList), timeout.duration).toList
  }
}
