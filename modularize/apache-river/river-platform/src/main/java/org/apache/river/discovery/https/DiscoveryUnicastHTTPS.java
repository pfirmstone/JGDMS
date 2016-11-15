/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.discovery.https;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import org.apache.river.discovery.ClientSubjectChecker;
import org.apache.river.discovery.Discovery;
import org.apache.river.discovery.EncodeIterator;
import org.apache.river.discovery.MulticastAnnouncement;
import org.apache.river.discovery.MulticastRequest;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.internal.UnicastClient;
import org.apache.river.discovery.internal.UnicastServer;

/**
 * This exists as a separate https protocol because the jini discovery protocol
 * cannot perform the required handshake negotiation to select a provider 
 * as specified in DJ.2.6.6 and DJ.2.6.7 through firewalls and https proxy servers.
 * 
 * As such this is not an implementation of the
 * jini discovery protocol.  It is simply https unicast discovery.
 * 
 * There are no providers of https unicast discovery as there is with
 * Discovery v2.
 * 
 * It can be used by passing a https://host:port string to LookupLocator's 
 * constructor.
 * 
 * @author Peter Firmstone
 */
public class DiscoveryUnicastHTTPS extends Discovery {
    
    private final UnicastServer server = new Server();
    private final UnicastClient client = new Client();

    /**
     * Unsupported.
     * 
     * @throws UnsupportedOperationException
     */
    @Override
    public EncodeIterator encodeMulticastRequest(
            MulticastRequest request,
            int maxPacketSize,
            InvocationConstraints constraints) 
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * Unsupported.
     * 
     * @throws UnsupportedOperationException
     */
    @Override
    public MulticastRequest decodeMulticastRequest(
            DatagramPacket packet,
            InvocationConstraints constraints,
            ClientSubjectChecker checker) throws IOException 
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * Unsupported.
     * 
     * @throws UnsupportedOperationException
     */
    @Override
    public EncodeIterator encodeMulticastAnnouncement(
            MulticastAnnouncement announcement,
            int maxPacketSize,
            InvocationConstraints constraints) 
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    /**
     * Unsupported.
     * 
     * @throws UnsupportedOperationException
     */
    @Override
    public MulticastAnnouncement decodeMulticastAnnouncement(
            DatagramPacket packet,
            InvocationConstraints constraints) throws IOException 
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public UnicastResponse doUnicastDiscovery(
            Socket socket,
            InvocationConstraints constraints,
            ClassLoader defaultLoader,
            ClassLoader verifierLoader,
            Collection context) throws IOException, ClassNotFoundException 
    {
        ByteBuffer sent = ByteBuffer.allocate(256);
        ByteBuffer received = ByteBuffer.allocate(256);
        return client.doUnicastDiscovery(socket, constraints, defaultLoader,
                verifierLoader, context, sent, received);
    }

    @Override
    public void handleUnicastDiscovery(
            UnicastResponse response,
            Socket socket, 
            InvocationConstraints constraints,
            ClientSubjectChecker checker,
            Collection context) throws IOException 
    {
        ByteBuffer sent = ByteBuffer.allocate(256);
        ByteBuffer received = ByteBuffer.allocate(256);
        server.handleUnicastDiscovery(response, socket, constraints, checker,
                context, received, sent);
    }
    
}
