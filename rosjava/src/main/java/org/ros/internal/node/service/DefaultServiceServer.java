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

package org.ros.internal.node.service;

import com.google.common.base.Preconditions;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandler;
import org.ros.address.AdvertiseAddress;
import org.ros.concurrent.ListenerCollection;
import org.ros.concurrent.ListenerCollection.SignalRunnable;
import org.ros.internal.message.service.ServiceDescription;
import org.ros.internal.node.topic.DefaultPublisher;
import org.ros.internal.transport.ConnectionHeader;
import org.ros.internal.transport.ConnectionHeaderFields;
import org.ros.message.MessageDeserializer;
import org.ros.message.MessageFactory;
import org.ros.message.MessageSerializer;
import org.ros.namespace.GraphName;
import org.ros.node.service.DefaultServiceServerListener;
import org.ros.node.service.ServiceResponseBuilder;
import org.ros.node.service.ServiceServer;
import org.ros.node.service.ServiceServerListener;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Default implementation of a {@link ServiceServer}.
 * 
 * @author damonkohler@google.com (Damon Kohler)
 */
public class DefaultServiceServer<T, S> implements ServiceServer<T, S> {

  private static final boolean DEBUG = false;
  private static final Log log = LogFactory.getLog(DefaultPublisher.class);

  private final ServiceDeclaration serviceDeclaration;
  private final ServiceResponseBuilder<T, S> serviceResponseBuilder;
  private final AdvertiseAddress advertiseAddress;
  private final MessageDeserializer<T> messageDeserializer;
  private final MessageSerializer<S> messageSerializer;
  private final MessageFactory messageFactory;
  private final ScheduledExecutorService scheduledExecutorService;
  private final ListenerCollection<ServiceServerListener<T, S>> listenerCollection;

  public DefaultServiceServer(ServiceDeclaration serviceDeclaration,
      ServiceResponseBuilder<T, S> serviceResponseBuilder, AdvertiseAddress advertiseAddress,
      MessageDeserializer<T> messageDeserializer, MessageSerializer<S> messageSerializer,
      MessageFactory messageFactory, ScheduledExecutorService scheduledExecutorService) {
    this.serviceDeclaration = serviceDeclaration;
    this.serviceResponseBuilder = serviceResponseBuilder;
    this.advertiseAddress = advertiseAddress;
    this.messageDeserializer = messageDeserializer;
    this.messageSerializer = messageSerializer;
    this.messageFactory = messageFactory;
    this.scheduledExecutorService = scheduledExecutorService;
    listenerCollection =
        new ListenerCollection<ServiceServerListener<T, S>>(scheduledExecutorService);
    listenerCollection.add(new DefaultServiceServerListener<T, S>() {
      @Override
      public void onMasterRegistrationSuccess(ServiceServer<T, S> registrant) {
        log.info("Service registered: " + DefaultServiceServer.this);
      }

      @Override
      public void onMasterRegistrationFailure(ServiceServer<T, S> registrant) {
        log.info("Service registration failed: " + DefaultServiceServer.this);
      }

      @Override
      public void onMasterUnregistrationSuccess(ServiceServer<T, S> registrant) {
        log.info("Service unregistered: " + DefaultServiceServer.this);
      }

      @Override
      public void onMasterUnregistrationFailure(ServiceServer<T, S> registrant) {
        log.info("Service unregistration failed: " + DefaultServiceServer.this);
      }
    });
  }

  public ChannelBuffer finishHandshake(Map<String, String> incomingHeader) {
    if (DEBUG) {
      log.info("Client handshake header: " + incomingHeader);
    }
    Map<String, String> header = getDeclaration().toConnectionHeader();
    String expectedChecksum = header.get(ConnectionHeaderFields.MD5_CHECKSUM);
    String incomingChecksum = incomingHeader.get(ConnectionHeaderFields.MD5_CHECKSUM);
    // TODO(damonkohler): Pull out header field comparison logic.
    Preconditions.checkState(incomingChecksum.equals(expectedChecksum)
        || incomingChecksum.equals("*"));
    if (DEBUG) {
      log.info("Server handshake header: " + header);
    }
    return ConnectionHeader.encode(header);
  }

  @Override
  public URI getUri() {
    return advertiseAddress.toUri("rosrpc");
  }

  @Override
  public GraphName getName() {
    return serviceDeclaration.getName();
  }

  /**
   * @return a new {@link ServiceDeclaration} with this
   *         {@link DefaultServiceServer}'s {@link URI}
   */
  ServiceDeclaration getDeclaration() {
    ServiceIdentifier identifier = new ServiceIdentifier(serviceDeclaration.getName(), getUri());
    return new ServiceDeclaration(identifier, new ServiceDescription(serviceDeclaration.getType(),
        serviceDeclaration.getDefinition(), serviceDeclaration.getMd5Checksum()));
  }

  public ChannelHandler newRequestHandler() {
    return new ServiceRequestHandler<T, S>(serviceDeclaration, serviceResponseBuilder,
        messageDeserializer, messageSerializer, messageFactory, scheduledExecutorService);
  }

  /**
   * Signal all {@link ServiceServerListener}s that the {@link ServiceServer}
   * has been successfully registered with the master.
   * 
   * <p>
   * Each listener is called in a separate thread.
   */
  public void signalOnMasterRegistrationSuccess() {
    final ServiceServer<T, S> serviceServer = this;
    listenerCollection.signal(new SignalRunnable<ServiceServerListener<T, S>>() {
      @Override
      public void run(ServiceServerListener<T, S> listener) {
        listener.onMasterRegistrationSuccess(serviceServer);
      }
    });
  }

  /**
   * Signal all {@link ServiceServerListener}s that the {@link ServiceServer}
   * has failed to register with the master.
   * 
   * <p>
   * Each listener is called in a separate thread.
   */
  public void signalOnMasterRegistrationFailure() {
    final ServiceServer<T, S> serviceServer = this;
    listenerCollection.signal(new SignalRunnable<ServiceServerListener<T, S>>() {
      @Override
      public void run(ServiceServerListener<T, S> listener) {
        listener.onMasterRegistrationFailure(serviceServer);
      }
    });
  }

  /**
   * Signal all {@link ServiceServerListener}s that the {@link ServiceServer}
   * has been successfully unregistered with the master.
   * 
   * <p>
   * Each listener is called in a separate thread.
   */
  public void signalOnMasterUnregistrationSuccess() {
    final ServiceServer<T, S> serviceServer = this;
    listenerCollection.signal(new SignalRunnable<ServiceServerListener<T, S>>() {
      @Override
      public void run(ServiceServerListener<T, S> listener) {
        listener.onMasterUnregistrationSuccess(serviceServer);
      }
    });
  }

  /**
   * Signal all {@link ServiceServerListener}s that the {@link ServiceServer}
   * has failed to unregister with the master.
   * 
   * <p>
   * Each listener is called in a separate thread.
   */
  public void signalOnMasterUnregistrationFailure() {
    final ServiceServer<T, S> serviceServer = this;
    listenerCollection.signal(new SignalRunnable<ServiceServerListener<T, S>>() {
      @Override
      public void run(ServiceServerListener<T, S> listener) {
        listener.onMasterUnregistrationFailure(serviceServer);
      }
    });
  }

  @Override
  public void shutdown() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(ServiceServerListener<T, S> listener) {
    listenerCollection.add(listener);
  }

  @Override
  public void removeListener(ServiceServerListener<T, S> listener) {
    listenerCollection.remove(listener);
  }

  @Override
  public String toString() {
    return "ServiceServer<" + getDeclaration() + ">";
  }
}
