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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.PrivilegedAction;
import java.util.Collection;
import javax.net.ServerSocketFactory;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.HttpsServerEndpoint;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.internal.EndpointBasedServer;
import org.apache.river.jeri.internal.EndpointInternals;
import org.apache.river.jeri.internal.HttpsEndpointInternalsAccess;
import org.apache.river.discovery.internal.UnicastServer;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Requirement;
import org.apache.river.discovery.Plaintext;

/**
 * Implements the server side of the <code>net.jini.discovery.https</code>
 * unicast discovery format.
 *
 */
@Requirement(
	namespace="osgi.extender",
	filter="(osgi.extender=osgi.serviceloader.registrar)")
@Capability(
	namespace="osgi.serviceloader",
	name="org.apache.river.discovery.DiscoveryFormatProvider")
class Server extends UnicastServer {
    
    /**
     * Creates a new instance.
     */
    public Server() {
	super(new ServerImpl());
    }

    private static final class ServerImpl extends EndpointBasedServer {
	
	private static final EndpointInternals epi =
	    AccessController.doPrivilegedWithCombiner(new PrivilegedAction<EndpointInternals>() {
		@Override
		public EndpointInternals run() {
		    return HttpsEndpointInternalsAccess.get();
		}
	    });
	
	/**
	 * Creates a new instance.
	 */
	ServerImpl() {
	    super("net.jini.discovery.https", epi);
	}

	// documentation inherited from EndpointBasedServer
	@Override
	protected ServerEndpoint getServerEndpoint(ServerSocketFactory factory)
	    throws UnsupportedConstraintException
	{
	    return HttpsServerEndpoint.getInstance("ignored", 0, null, factory);
	}
	
	@Override
	protected void writeUnicastResponse(OutputStream out,
					UnicastResponse response,
					Collection context)
	throws IOException
	{
	    super.writeClassAnnotationCerts(out, response);
	    Plaintext.writeV2UnicastResponse(out, response, context);
	    out.flush();
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
