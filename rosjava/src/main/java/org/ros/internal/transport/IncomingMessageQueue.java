/*
 * Copyright (C) 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.internal.transport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.ros.concurrent.CancellableLoop;
import org.ros.concurrent.ListenerCollection;
import org.ros.concurrent.ListenerCollection.SignalRunnable;
import org.ros.message.MessageDeserializer;
import org.ros.message.MessageListener;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public class IncomingMessageQueue<T> {

  private static final boolean DEBUG = false;
  private static final Log log = LogFactory.getLog(IncomingMessageQueue.class);

  private static final int MESSAGE_BUFFER_CAPACITY = 8192;

  private final MessageDeserializer<T> deserializer;
  private final ScheduledExecutorService executorService;
  private final CircularBlockingQueue<T> messages;
  private final ListenerCollection<MessageListener<T>> listeners;
  private final Dispatcher dispatcher;

  private boolean latchMode;
  private T latchedMessage;

  private final class Receiver extends SimpleChannelHandler {
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
      T message = deserializer.deserialize(buffer.toByteBuffer());
      messages.put(message);
      if (DEBUG) {
        log.info("Received message: " + message);
      }
      super.messageReceived(ctx, e);
    }
  }

  private final class Dispatcher extends CancellableLoop {
    @Override
    public void loop() throws InterruptedException {
      final T message = messages.take();
      latchedMessage = message;
      if (DEBUG) {
        log.info("Dispatched message: " + message);
      }
      listeners.signal(new SignalRunnable<MessageListener<T>>() {
        @Override
        public void run(MessageListener<T> listener) {
          listener.onNewMessage(message);
        }
      });
    }
  }

  public IncomingMessageQueue(MessageDeserializer<T> deserializer,
      ScheduledExecutorService executorService) {
    this.deserializer = deserializer;
    this.executorService = executorService;
    messages = new CircularBlockingQueue<T>(MESSAGE_BUFFER_CAPACITY);
    listeners = new ListenerCollection<MessageListener<T>>(executorService);
    dispatcher = new Dispatcher();
    latchMode = false;
    latchedMessage = null;
    executorService.execute(dispatcher);
  }

  public void setLatchMode(boolean enabled) {
    latchMode = enabled;
  }

  public boolean getLatchMode() {
    return latchMode;
  }

  public void addListener(final MessageListener<T> listener) {
    if (DEBUG) {
      log.info("Adding listener.");
    }
    listeners.add(listener);
    if (latchMode && latchedMessage != null) {
      if (DEBUG) {
        log.info("Dispatching latched message: " + latchedMessage);
      }
      executorService.execute(new Runnable() {
        @Override
        public void run() {
          listener.onNewMessage(latchedMessage);
        }
      });
    }
  }

  public void removeListener(MessageListener<T> listener) {
    listeners.remove(listener);
  }

  public void shutdown() {
    dispatcher.cancel();
  }

  /**
   * @see CircularBlockingQueue#setLimit(int)
   */
  public void setLimit(int limit) {
    messages.setLimit(limit);
  }

  /**
   * @see CircularBlockingQueue#getLimit()
   */
  public int getLimit() {
    return messages.getLimit();
  }

  /**
   * @return a new {@link ChannelHandler} that will receive messages and add
   *         them to the queue
   */
  public ChannelHandler newChannelHandler() {
    return new Receiver();
  }
}
