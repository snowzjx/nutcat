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

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by JunxueZhang on 15/7/2.
 */
class MessageBufferEntry[V] {

  val messageCount = new AtomicLong(0L)
  val messageQueue = new ConcurrentLinkedQueue[V]()

  def insertMessage(message: V): Long = {
    messageQueue.add(message)
    messageCount.incrementAndGet()
  }

  def getMessages(number: Long): List[V] = {
    messageCount.addAndGet(-number)
    (for (i <- 1L to number) yield messageQueue.poll).toList
  }

  def getAllMessages(): List[V] =  {
    Iterator.continually(messageQueue).takeWhile(!_.isEmpty).map(_.poll()).toList
  }
}
