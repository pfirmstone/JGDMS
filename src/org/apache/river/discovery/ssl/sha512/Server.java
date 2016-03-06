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

package org.apache.river.discovery.ssl.sha512;

import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.Collection;
import javax.net.ServerSocketFactory;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.internal.EndpointBasedServer;
import org.apache.river.discovery.internal.EndpointInternals;
import org.apache.river.discovery.internal.SslEndpointInternalsAccess;
import org.apache.river.discovery.internal.UnicastServer;

/**
 * Implements the server side of the <code>net.jini.discovery.ssl.sha512</code>
 * unicast discovery format.
 *
 */
public class Server extends UnicastServer {
    
    /**
     * Creates a new instance.
     */
    public Server() {
	super(new ServerImpl());
    }

    private static final class ServerImpl extends EndpointBasedServer {
	
	private static final EndpointInternals epi =
	    AccessController.doPrivileged(new PrivilegedAction<EndpointInternals>() {
		@Override
		public EndpointInternals run() {
		    return SslEndpointInternalsAccess.get();
		}
	    });
	
	/**
	 * Creates a new instance.
	 */
	ServerImpl() {
	    super("net.jini.discovery.ssl.sha512", epi);
	}

	// documentation inherited from EndpointBasedServer
	@Override
	protected ServerEndpoint getServerEndpoint(ServerSocketFactory factory)
	    throws UnsupportedConstraintException
	{
	    return SslServerEndpoint.getInstance("ignored", 0, null, factory);
	}

	@Override
	protected MessageDigest handshakeHashAlgorithm() {
	    try {
		return MessageDigest.getInstance("SHA-512");
	    } catch (NoSuchAlgorithmException ex) {
		throw new AssertionError(ex);
	    }
	}
	
	@Override
	protected void writeUnicastResponse(OutputStream out,
					UnicastResponse response,
					Collection context)
	throws IOException
	{
	    super.writeClassAnnotationCerts(out, response);
	    super.writeUnicastResponse(out, response, context);
	}
    }
}
