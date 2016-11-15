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

package org.apache.river.discovery.kerberos;

import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import javax.net.ServerSocketFactory;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.kerberos.KerberosServerEndpoint;
import org.apache.river.discovery.internal.EndpointBasedServer;
import org.apache.river.discovery.internal.EndpointInternals;
import org.apache.river.discovery.internal.KerberosEndpointInternalsAccess;
import org.apache.river.discovery.internal.UnicastServer;

/**
 * Implements the server side of the <code>net.jini.discovery.kerberos</code>
 * unicast discovery format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Server extends UnicastServer {
    
    /**
     * Creates a new instance.
     */
    public Server() {
	super(new ServerImpl());
    }

    private static final class ServerImpl extends EndpointBasedServer {
	
	private static EndpointInternals epi = 
	    AccessController.doPrivileged(new PrivilegedAction<EndpointInternals>() {
		public EndpointInternals run() {
		    return KerberosEndpointInternalsAccess.get();
		}
	    });
	
	ServerImpl() {
	    super("net.jini.discovery.kerberos", epi);
	}

	// documentation inherited from EndpointBasedServer
	protected ServerEndpoint getServerEndpoint(ServerSocketFactory factory)
	    throws UnsupportedConstraintException
	{
	    return KerberosServerEndpoint.getInstance("ignored", 0, null, factory);
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