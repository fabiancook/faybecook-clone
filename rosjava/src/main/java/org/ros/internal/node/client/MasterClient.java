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

package org.ros.internal.node.client;

import java.net.URI;
import java.util.List;

import org.ros.internal.node.response.IntegerResultFactory;
import org.ros.internal.node.response.Response;
import org.ros.internal.node.response.SystemStateResultFactory;
import org.ros.internal.node.response.TopicDefinitionListResultFactory;
import org.ros.internal.node.response.TopicTypeListResultFactory;
import org.ros.internal.node.response.UriListResultFactory;
import org.ros.internal.node.response.UriResultFactory;
import org.ros.internal.node.response.VoidResultFactory;
import org.ros.internal.node.server.NodeIdentifier;
import org.ros.internal.node.server.SlaveServer;
import org.ros.internal.node.server.master.MasterServer;
import org.ros.internal.node.topic.PublisherDefinition;
import org.ros.internal.node.topic.PublisherIdentifier;
import org.ros.internal.node.topic.TopicDefinition;
import org.ros.internal.node.xmlrpc.MasterXmlRpcEndpoint;
import org.ros.master.client.SystemState;
import org.ros.master.client.TopicType;
import org.ros.master.client.TopicSystemState;
import org.ros.namespace.GraphName;
import org.ros.node.service.ServiceServer;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

/**
 * Provides access to the XML-RPC API exposed by a {@link MasterServer}.
 * 
 * @author damonkohler@google.com (Damon Kohler)
 */
public class MasterClient extends Client<MasterXmlRpcEndpoint> {

	/**
	 * Create a new {@link MasterClient} connected to the specified
	 * {@link MasterServer} URI.
	 * 
	 * @param uri
	 *            the {@link URI} of the {@link MasterServer} to connect to
	 */
	public MasterClient(URI uri) {
		super(uri, MasterXmlRpcEndpoint.class);
	}

	/**
	 * Registers the given {@link ServiceServer}.
	 * 
	 * @param slave
	 *            the {@link NodeIdentifier} where the {@link ServiceServer} is
	 *            running
	 * @param service
	 *            the {@link ServiceServer} to register
	 * @return a {@link Response} with a void result
	 */
	public Response<Void> registerService(NodeIdentifier slave,
			ServiceServer<?, ?> service) {
		return Response.fromListChecked(xmlRpcEndpoint.registerService(slave
				.getNodeName().toString(), service.getName().toString(),
				service.getUri().toString(), slave.getUri().toString()),
				new VoidResultFactory());
	}

	/**
	 * Unregisters the specified {@link ServiceServer}.
	 * 
	 * @param slave
	 *            the {@link NodeIdentifier} where the {@link ServiceServer} is
	 *            running
	 * @param service
	 *            the {@link ServiceServer} to unregister
	 * @return a {@link Response} with the number of unregistered services as
	 *         the result
	 */
	public Response<Integer> unregisterService(NodeIdentifier slave,
			ServiceServer<?, ?> service) {
		return Response.fromListChecked(xmlRpcEndpoint.unregisterService(slave
				.getNodeName().toString(), service.getName().toString(),
				service.getUri().toString()), new IntegerResultFactory());
	}

	/**
	 * Registers the given {@link Subscriber}. In addition to receiving a list
	 * of current {@link Publisher}s, the {@link Subscriber}s
	 * {@link SlaveServer} will also receive notifications of new
	 * {@link Publisher}s via the publisherUpdate API.
	 * 
	 * @param slave
	 *            the {@link NodeIdentifier} that the {@link Subscriber} is
	 *            running on
	 * @param subscriber
	 *            the {@link Subscriber} to register
	 * @return a {@link Response} with a {@link List} or {@link SlaveServer}
	 *         XML-RPC API URIs for nodes currently publishing the specified
	 *         topic as the result
	 */
	public Response<List<URI>> registerSubscriber(NodeIdentifier slave,
			Subscriber<?> subscriber) {
		return Response.fromListChecked(xmlRpcEndpoint.registerSubscriber(slave
				.getNodeName().toString(),
				subscriber.getTopicName().toString(), subscriber
						.getTopicMessageType(), slave.getUri().toString()),
				new UriListResultFactory());
	}

	/**
	 * Unregisters the specified {@link Subscriber}.
	 * 
	 * @param slave
	 *            the {@link NodeIdentifier} where the subscriber is running
	 * @param subscriber
	 *            the {@link Subscriber} to unregister
	 * @return a {@link Response} with the number of unregistered subscribers as
	 *         the result
	 */
	public Response<Integer> unregisterSubscriber(NodeIdentifier slave,
			Subscriber<?> subscriber) {
		return Response.fromListChecked(xmlRpcEndpoint.unregisterSubscriber(
				slave.getNodeName().toString(), subscriber.getTopicName()
						.toString(), slave.getUri().toString()),
				new IntegerResultFactory());
	}

	/**
	 * Registers the specified {@link PublisherDefinition}.
	 * 
	 * @param publisherDefinition
	 *            the {@link PublisherDefinition} of the {@link Publisher} to
	 *            register
	 * @return a {@link Response} with a {@link List} of the current
	 *         {@link SlaveServer} URIs which have {@link Subscriber}s for the
	 *         published {@link TopicSystemState}.
	 */
	public Response<List<URI>> registerPublisher(
			PublisherDefinition publisherDefinition) {
		String slaveName = publisherDefinition.getSlaveName().toString();
		String slaveUri = publisherDefinition.getSlaveUri().toString();
		String topicName = publisherDefinition.getTopicName().toString();
		String messageType = publisherDefinition.getTopicMessageType();
		return Response.fromListChecked(xmlRpcEndpoint.registerPublisher(
				slaveName, topicName, messageType, slaveUri),
				new UriListResultFactory());
	}

	/**
	 * Unregisters the specified {@link PublisherDefinition}.
	 * 
	 * @param publisherIdentifierr
	 *            the {@link PublisherIdentifier} of the {@link Publisher} to
	 *            unregister
	 * @return a {@link Response} with the number of unregistered
	 *         {@link Publisher}s as the result
	 */
	public Response<Integer> unregisterPublisher(
			PublisherIdentifier publisherIdentifier) {
		String slaveName = publisherIdentifier.getNodeSlaveName().toString();
		String slaveUri = publisherIdentifier.getNodeSlaveUri().toString();
		String topicName = publisherIdentifier.getTopicName().toString();
		return Response.fromListChecked(xmlRpcEndpoint.unregisterPublisher(
				slaveName, topicName, slaveUri), new IntegerResultFactory());
	}

	/**
	 * Returns the {@link URI} of the {@link SlaveServer} with the given name.
	 * 
	 * @param slaveName
	 *            the {@link GraphName} of the caller
	 * @param nodeName
	 *            the name of the {@link SlaveServer} to lookup
	 * @return a {@link Response} with the {@link URI} of the
	 *         {@link SlaveServer} as a result
	 */
	public Response<URI> lookupNode(GraphName slaveName, String nodeName) {
		return Response.fromListChecked(
				xmlRpcEndpoint.lookupNode(slaveName.toString(), nodeName),
				new UriResultFactory());
	}

	/**
	 * Returns the {@link URI} of the {@link MasterServer}.
	 * 
	 * @param slaveName
	 *            the {@link NodeIdentifier} of the caller
	 * @return the {@link URI} of the {@link MasterServer}
	 */
	public Response<URI> getUri(GraphName slaveName) {
		return Response.fromListChecked(
				xmlRpcEndpoint.getUri(slaveName.toString()),
				new UriResultFactory());
	}

	/**
	 * Returns the {@link URI} of the {@link ServiceServer} with the given name.
	 * 
	 * @param callerName
	 *            the {@link GraphName} of the caller
	 * @param serviceName
	 *            the name of the {@link ServiceServer} to look up
	 * @return a {@link Response} with the {@link URI} of the
	 *         {@link ServiceServer} as a result
	 */
	public Response<URI> lookupService(GraphName callerName, String serviceName) {
		return Response.fromListCheckedFailure(xmlRpcEndpoint.lookupService(
				callerName.toString(), serviceName), new UriResultFactory());
	}

	/**
	 * Get a list of published topics from the master.
	 * 
	 * @param callerName
	 *            the {@link GraphName} of the caller
	 * @param subgraph
	 *            the subgraph of the topics
	 * 
	 * @return the list of topic definitions
	 */
	public Response<List<TopicDefinition>> getPublishedTopics(
			GraphName callerName, String subgraph) {
		return Response.fromListChecked(xmlRpcEndpoint.getPublishedTopics(
				callerName.toString(), subgraph),
				new TopicDefinitionListResultFactory());
	}

	/**
	 * Get a {@link List} of all {@link TopicSystemState} message types.
	 * 
	 * @param callerName
	 *            the {@link GraphName} of the caller
	 * @return a list of information about the topics
	 */
	public Response<List<TopicType>> getTopicTypes(GraphName callerName) {
		return Response.fromListChecked(
				xmlRpcEndpoint.getTopicTypes(callerName.toString()),
				new TopicTypeListResultFactory());
	}

	/**
	 * Get the system state contained in the master
	 * 
	 * @param callerName
	 *            the {@link GraphName} of the caller
	 * 
	 * @return
	 */
	public Response<SystemState> getSystemState(GraphName callerName) {
		return Response.fromListChecked(
				xmlRpcEndpoint.getSystemState(callerName.toString()),
				new SystemStateResultFactory());
	}
}
