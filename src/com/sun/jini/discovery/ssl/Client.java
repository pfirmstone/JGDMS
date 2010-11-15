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

package com.sun.jini.discovery.ssl;

import com.sun.jini.discovery.UnicastDiscoveryClient;
import com.sun.jini.discovery.UnicastResponse;
import com.sun.jini.discovery.internal.EndpointBasedClient;
import com.sun.jini.discovery.internal.EndpointInternals;
import com.sun.jini.discovery.internal.SslEndpointInternalsAccess;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ssl.SslEndpoint;
import javax.net.SocketFactory;

/**
 * Implements the client side of the <code>net.jini.discovery.ssl</code>
 * unicast discovery format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Client implements UnicastDiscoveryClient {
    
    // Internal implementation. We dont want to expose the internal base
    // classes to the outside.    
    private final ClientImpl impl; 
    
    public Client() {
	impl = new ClientImpl();
    }

    // javadoc inherited from DiscoveryFormatProvider
    public String getFormatName() {
	return impl.getFormatName();
    }

    // javadoc inherited from UnicastDiscoveryClient
    public void checkUnicastDiscoveryConstraints(
		    InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	impl.checkUnicastDiscoveryConstraints(constraints);
    }
    
    // javadoc inherited from UnicastDiscoveryClient
    public UnicastResponse doUnicastDiscovery(Socket socket,
					      InvocationConstraints constraints,
					      ClassLoader defaultLoader,
					      ClassLoader verifierLoader,
					      Collection context,
					      ByteBuffer sent,
					      ByteBuffer received)
	throws IOException, ClassNotFoundException
    {
	return impl.doUnicastDiscovery(socket, constraints, defaultLoader,
				       verifierLoader, context, sent, received);
    }

    private static final class ClientImpl extends EndpointBasedClient {

	private static EndpointInternals epi = (EndpointInternals)
	    AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
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
    }
}
