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

package org.apache.river.discovery.ssl;

import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import javax.net.SocketFactory;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ssl.SslEndpoint;
import org.apache.river.discovery.internal.EndpointBasedClient;
import org.apache.river.discovery.internal.EndpointInternals;
import org.apache.river.discovery.internal.SslEndpointInternalsAccess;
import org.apache.river.discovery.internal.UnicastClient;

/**
 * Implements the client side of the <code>net.jini.discovery.ssl</code>
 * unicast discovery format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Client extends UnicastClient {
    
    public Client() {
	super(new ClientImpl());
    }

    private static final class ClientImpl extends EndpointBasedClient {

	private static EndpointInternals epi = 
	    AccessController.doPrivileged(new PrivilegedAction<EndpointInternals>() {
		public EndpointInternals run() {
		    return SslEndpointInternalsAccess.get();
		}
	    });
	
	/**
	 * Creates a new instance.
	 */
	ClientImpl() {
	    super("net.jini.discovery.ssl", epi);
	}

	// documentation inherited from EndpointBasedClient
	protected Endpoint getEndpoint(SocketFactory factory,
				       InvocationConstraints constraints)
	    throws UnsupportedConstraintException
	{
	    return SslEndpoint.getInstance("ignored", 1, factory);
	}

	@Override
	protected MessageDigest handshakeHashAlgorithm() {
	    try {
		return MessageDigest.getInstance("SHA-1");
	    } catch (NoSuchAlgorithmException ex) {
		throw new AssertionError(ex);
	    }
	}
    }
}
