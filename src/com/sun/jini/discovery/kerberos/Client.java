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

package com.sun.jini.discovery.kerberos;

import com.sun.jini.discovery.UnicastDiscoveryClient;
import com.sun.jini.discovery.UnicastResponse;
import com.sun.jini.discovery.internal.EndpointBasedClient;
import com.sun.jini.discovery.internal.EndpointInternals;
import com.sun.jini.discovery.internal.KerberosEndpointInternalsAccess;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import javax.net.SocketFactory;
import javax.security.auth.kerberos.KerberosPrincipal;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.kerberos.KerberosEndpoint;

/**
 * Implements the client side of the <code>net.jini.discovery.kerberos</code>
 * unicast discovery format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Client implements UnicastDiscoveryClient {
    
    // Internal implementation. We dont want to expose the internal base
    // classes to the outside.
    private final ClientImpl impl;
    /**
     * Creates a new instance.
     */
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
		    return KerberosEndpointInternalsAccess.get();
		}
	    });
	
	ClientImpl() {
	    super("net.jini.discovery.kerberos", epi);
	}

	// documentation inherited from EndpointBasedClient
	protected Endpoint getEndpoint(SocketFactory factory,
				       InvocationConstraints constraints)
	    throws UnsupportedConstraintException
	{
	    return KerberosEndpoint.getInstance(
		"ignored", 1, getKerberosPrincipal(constraints), factory);
	}

	/**
	 * Returns the kerberos principal specified in the ServerMinPrincipal
	 * requirements or preferences of the given constraints.  Throws
	 * UnsupportedConstraintException if no kerberos principal is specified, a
	 * non-kerberos server principal is required, or multiple server principals
	 * are required.
	 */
	private static KerberosPrincipal getKerberosPrincipal(
						InvocationConstraints constraints)
	    throws UnsupportedConstraintException
	{
	    KerberosPrincipal principal = null;
	    for (Iterator i = constraints.requirements().iterator(); i.hasNext(); )
	    {
		InvocationConstraint c = (InvocationConstraint) i.next();
		if (c instanceof ServerMinPrincipal) {
		    Set s = ((ServerMinPrincipal) c).elements();
		    if (s.size() > 1) {
			throw new UnsupportedConstraintException(
			    "multiple server principals");
		    }
		    Principal p = (Principal) s.iterator().next();
		    if (!(p instanceof KerberosPrincipal)) {
			throw new UnsupportedConstraintException(
			    "non-kerberos server principal");
		    }
		    if (principal == null) {
			principal = (KerberosPrincipal) p;
		    } else if (!principal.equals(p)) {
			throw new UnsupportedConstraintException(
			    "multiple server principals");
		    }
		}
		// NYI: support ConstraintAlternatives with ServerMinPrincipals
	    }
	    if (principal != null) {
		return principal;
	    }
	    for (Iterator i = constraints.preferences().iterator(); i.hasNext(); )
	    {
		InvocationConstraint c = (InvocationConstraint) i.next();
		if (c instanceof ServerMinPrincipal) {
		    Set s = ((ServerMinPrincipal) c).elements();
		    for (Iterator j = s.iterator(); j.hasNext(); ) {
			Principal p = (Principal) j.next();
			if (p instanceof KerberosPrincipal) {
			    return (KerberosPrincipal) p;
			}
		    }
		}
		// NYI: support ConstraintAlternatives with ServerMinPrincipals
	    }
	    throw new UnsupportedConstraintException("no kerberos principal");
	}	
    }
}
