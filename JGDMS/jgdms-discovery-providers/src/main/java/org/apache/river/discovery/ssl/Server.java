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
import javax.net.ServerSocketFactory;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;
import org.apache.river.discovery.internal.EndpointBasedServer;
import org.apache.river.jeri.internal.EndpointInternals;
import org.apache.river.jeri.internal.SslEndpointInternalsAccess;
import org.apache.river.discovery.internal.UnicastServer;
import aQute.bnd.annotation.headers.RequireCapability;
import aQute.bnd.annotation.headers.ProvideCapability;
import java.util.Iterator;
import java.util.Set;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.ssl.ConfidentialityStrength;

/**
 * Implements the server side of the <code>net.jini.discovery.ssl</code>
 * unicast discovery format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@RequireCapability(
	ns="osgi.extender",
	filter="(osgi.extender=osgi.serviceloader.registrar)")
@ProvideCapability(
	ns="osgi.serviceloader",
	name="org.apache.river.discovery.DiscoveryFormatProvider")
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
	    super("net.jini.discovery.ssl", epi);
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
		return MessageDigest.getInstance("SHA-1");
	    } catch (NoSuchAlgorithmException ex) {
		throw new AssertionError(ex);
	    }
	}
	
	@Override
	public void checkUnicastDiscoveryConstraints(
					    InvocationConstraints constraints) 
		throws UnsupportedConstraintException
	{
	    Set<InvocationConstraint> required = constraints.requirements();
	    Iterator<InvocationConstraint> itReq = required.iterator();
	    while (itReq.hasNext()){
		InvocationConstraint c = itReq.next();
		if (c == ConfidentialityStrength.STRONG) 
		    throw new UnsupportedConstraintException("SHA-1 is considered weak " + c);
	    }
	    Set<InvocationConstraint> pref = constraints.preferences();
	    Iterator<InvocationConstraint> itPref = pref.iterator();
	    while (itPref.hasNext()){
		InvocationConstraint c = itPref.next();
		if (c == ConfidentialityStrength.STRONG) 
		    throw new UnsupportedConstraintException("SHA-1 is considered weak " + c);
	    }
	    super.checkUnicastDiscoveryConstraints(constraints);
	}
    }
}
