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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.PrivilegedAction;
import java.util.Collection;
import javax.net.SocketFactory;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ssl.HttpsEndpoint;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.internal.EndpointBasedClient;
import org.apache.river.discovery.internal.EndpointInternals;
import org.apache.river.discovery.internal.HttpsEndpointInternalsAccess;
import org.apache.river.discovery.internal.UnicastClient;

/**
 * Implements the client side of the <code>net.jini.discovery.https</code>
 * unicast discovery format.
 *
 */
class Client extends UnicastClient {
    
    public Client() {
	super(new ClientImpl());
    }

    private static final class ClientImpl extends EndpointBasedClient {

	private static EndpointInternals epi = 
	    AccessController.doPrivileged(new PrivilegedAction<EndpointInternals>() {
		public EndpointInternals run() {
		    return HttpsEndpointInternalsAccess.get();
		}
	    });
	
	/**
	 * Creates a new instance.
	 */
	ClientImpl() {
	    super("net.jini.discovery.https", epi);
	}

	// documentation inherited from EndpointBasedClient
	protected Endpoint getEndpoint(SocketFactory factory,
				       InvocationConstraints constraints)
	    throws UnsupportedConstraintException
	{
	    return HttpsEndpoint.getInstance("ignored", 1, factory);
	}
	
	@Override
	protected UnicastResponse readUnicastResponse(
					    InputStream in,
					    ClassLoader defaultLoader,
					    boolean verifyCodebaseIntegrity,
					    ClassLoader verifierLoader,
					    Collection context)
	throws IOException, ClassNotFoundException
	{
	    return super.readUnicastResponse(
		in,
		defaultLoader,
		readAnnotationCertsGrantPerm(in,verifyCodebaseIntegrity),
		verifierLoader,
		context
	    );
	}

        @Override
        protected MessageDigest handshakeHashAlgorithm() {
            return null;
        }
        
        // Ensures no handshake bytes are written.
        @Override
	protected byte[] calcHandshakeHash(ByteBuffer request, ByteBuffer response) {
	    return new byte[0];
        }
    }
}
