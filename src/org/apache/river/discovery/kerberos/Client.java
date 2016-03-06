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
import java.security.Principal;
import java.security.PrivilegedAction;
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
import org.apache.river.discovery.internal.EndpointBasedClient;
import org.apache.river.discovery.internal.EndpointInternals;
import org.apache.river.discovery.internal.KerberosEndpointInternalsAccess;
import org.apache.river.discovery.internal.UnicastClient;

/**
 * Implements the client side of the <code>net.jini.discovery.kerberos</code>
 * unicast discovery format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Client extends UnicastClient {
    
    /**
     * Creates a new instance.
     */
    public Client() {
	super(new ClientImpl());
    }

    private static final class ClientImpl extends EndpointBasedClient {
	private static EndpointInternals epi = 
	    AccessController.doPrivileged(new PrivilegedAction<EndpointInternals>() {
		public EndpointInternals run() {
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
