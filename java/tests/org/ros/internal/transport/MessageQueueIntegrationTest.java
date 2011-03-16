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

import static org.junit.Assert.assertEquals;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.junit.Before;
import org.junit.Test;
import org.ros.internal.topic.SubscriberMessageQueue;
import org.ros.internal.transport.tcp.TcpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public class MessageQueueIntegrationTest {

  private OutgoingMessageQueue out;

  private class ServerHandler extends SimpleChannelHandler {
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
      Channel channel = e.getChannel();
      out.addChannel(channel);
    }
  }

  @Before
  public void setup() {
    out = new OutgoingMessageQueue();
    out.start();
  }

  @Test
  public void testSendAndReceiveMessage() throws IOException, InterruptedException {
    SimplePipelineFactory factory = new SimplePipelineFactory();
    factory.getPipeline().addLast("Server Handler", new ServerHandler());
    TcpServer server = new TcpServer(factory);
    server.start(new InetSocketAddress(0));

    Socket client = new Socket(server.getAddress().getHostName(), server.getAddress().getPort());
    SubscriberMessageQueue<org.ros.message.std.String> in =
        new SubscriberMessageQueue<org.ros.message.std.String>(org.ros.message.std.String.class);
    in.setSocket(client);
    in.start();

    // TODO(damonkohler): There is a race here that makes this test flaky. Once
    // the IncomingMessageQueue is ported to Netty, we can wait for a successful
    // connection before putting the message on the outgoing queue. That should
    // fix the problem.
    org.ros.message.std.String hello = new org.ros.message.std.String();
    hello.data = "Would you like to play a game?";
    out.put(hello);
    assertEquals(in.take(), hello);
  }
}
