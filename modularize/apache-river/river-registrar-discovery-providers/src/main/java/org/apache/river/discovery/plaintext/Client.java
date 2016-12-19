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

package org.apache.river.discovery.plaintext;

import org.apache.river.discovery.DatagramBufferFactory;
import org.apache.river.discovery.MulticastAnnouncement;
import org.apache.river.discovery.MulticastAnnouncementDecoder;
import org.apache.river.discovery.MulticastRequest;
import org.apache.river.discovery.MulticastRequestEncoder;
import org.apache.river.discovery.UnicastDiscoveryClient;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.internal.Plaintext;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;

/**
 * Implements the client side of the <code>net.jini.discovery.plaintext</code>
 * discovery format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Client
    implements MulticastRequestEncoder,
	       MulticastAnnouncementDecoder,
	       UnicastDiscoveryClient
{
    /**
     * Constructs a new instance.
     */
    public Client() {
    }

    // documentation inherited from DiscoveryFormatProvider
    public String getFormatName() {
	return "net.jini.discovery.plaintext";
    }
    
    // documentation inherited from MulticastRequestEncoder
    public void encodeMulticastRequest(MulticastRequest request,
				       DatagramBufferFactory bufs,
				       InvocationConstraints constraints)
	throws IOException
    {
	Plaintext.checkConstraints(constraints);
	Plaintext.encodeMulticastRequest(request, bufs);
    }

    // documentation inherited from MulticastAnnouncementDecoder
    public MulticastAnnouncement decodeMulticastAnnouncement(
					    ByteBuffer buf,
					    InvocationConstraints constraints)
	throws IOException
    {
	Plaintext.checkConstraints(constraints);
	return Plaintext.decodeMulticastAnnouncement(buf);
    }

    // documentation inherited from UnicastDiscoveryClient
    public void checkUnicastDiscoveryConstraints(
					InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	Plaintext.checkConstraints(constraints);
    }

    // documentation inherited from UnicastDiscoveryClient
    public UnicastResponse doUnicastDiscovery(
					Socket socket,
					InvocationConstraints constraints,
					ClassLoader defaultLoader,
					ClassLoader verifierLoader,
					Collection context,
					ByteBuffer sent,
					ByteBuffer received)
	throws IOException, ClassNotFoundException
    {
	Plaintext.checkConstraints(constraints);
	return Plaintext.readUnicastResponse(
		   new BufferedInputStream(socket.getInputStream()),
		   defaultLoader,
		   false,
		   null,
		   context);
    }
}
