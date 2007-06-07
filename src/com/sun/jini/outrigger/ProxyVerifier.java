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
package com.sun.jini.outrigger;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.id.Uuid;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;

import com.sun.jini.landlord.Landlord;
import com.sun.jini.landlord.LandlordProxyVerifier;

/** 
 * This class defines a trust verifier for the proxies related to the 
 * Outrigger implementation of JavaSpaces technology. Uses 
 * {@link LandlordProxyVerifier} to verify Leases.
 *
 * @see net.jini.security.TrustVerifier
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
final class ProxyVerifier implements Serializable, TrustVerifier {
    private static final long serialVersionUID = 1L;

    /** 
     * The canonical instance of the server reference. This
     * instance will be used by the <code>isTrusted</code> method 
     * as the known trusted object used to determine whether or not a
     * given proxy is equivalent in trust, content, and function.
     *
     * @serial
     */
    private final RemoteMethodControl server;

    /**
     * The top level <code>Uuid</code> that has been assigned to
     * the Outrigger server this verifier is for.
     */
    private final Uuid uuid;

    /**
     * Returns a verifier for the smart proxies of an Outrigger server with
     * the specified server reference.
     *
     * @param server the reference to the <code>OutriggerServer</code>.
     * @param uuid the <code>Uuid</code> assigned to the Outrigger
     *             server <code>server</code> is a reference to.
     * @throws UnsupportedOperationException if <code>server</code> does
     *	       not implement both {@link RemoteMethodControl} and {@link
     *	       TrustEquivalence}
     * @throws NullPointerException if either argument is 
     *         <code>null</code>.
     */
    ProxyVerifier(OutriggerServer server, Uuid uuid) {
	if (server == null)
	    throw new NullPointerException("server can not be null");

	if (uuid == null)
	    throw new NullPointerException("uuid can not be null");

        if(!(server instanceof RemoteMethodControl))
            throw new UnsupportedOperationException
		("cannot construct verifier - server reference does not " +
		 "implement RemoteMethodControl");

	if (!(server instanceof TrustEquivalence)) 
            throw new UnsupportedOperationException
		("cannot construct verifier - server reference does not " +
		 "implement TrustEquivalence");

	this.uuid = uuid;
        this.server = (RemoteMethodControl)server;
    }

    /** 
     * Returns <code>true</code> if the specified proxy object (that is
     * not yet known to be trusted) is equivalent in trust, content, and
     * function to the canonical server reference this object was 
     * constructed with; otherwise returns <code>false</code>.
     *
     * @param obj proxy object that will be compared to this class' stored
     *            canonical proxy to determine whether or not the given
     *            proxy object is equivalent in trust, content, and function.
     *            
     * @return <code>true</code> if the specified object (that is not yet
     *                           known to be trusted) is equivalent in trust,
     *                           content, and function to the canonical inner
     *                           proxy object referenced in this class;
     *                           otherwise returns <code>false</code>.
     *
     * @throws NullPointerException if any argument is <code>null</code>
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
        /* Validate the arguments */
        if (obj == null || ctx == null) {
            throw new NullPointerException("arguments must not be null");
	}

        /* Prepare the input proxy object for trust verification. The types
         * of proxies, specific to the service, that this method will
         * handle are:
         *  - ConstrainableSpaceProxy2
	 *  - ConstrainableAdminProxy
	 *  - ConstrainableParticipantProxy
	 *  - ConstrainableLandlordLease (via LandlordProxyVerifier)
         */

	// Server reference from input proxy
        final RemoteMethodControl inputProxyServer;
	final Uuid inputProxyUuid; 
        if (obj instanceof ConstrainableSpaceProxy2) {
	    final ConstrainableSpaceProxy2 csp = (ConstrainableSpaceProxy2)obj;

	    inputProxyUuid = csp.getReferentUuid();
            inputProxyServer = (RemoteMethodControl)csp.space;
        } else if (obj instanceof ConstrainableAdminProxy) {
	    final ConstrainableAdminProxy cap = (ConstrainableAdminProxy)obj;

	    inputProxyUuid = cap.getReferentUuid();
            inputProxyServer = (RemoteMethodControl)cap.admin;
	} else if (obj instanceof ConstrainableParticipantProxy) {
	    final ConstrainableParticipantProxy cpp = 
		(ConstrainableParticipantProxy)obj;

	    inputProxyUuid = cpp.getReferentUuid();
	    inputProxyServer = (RemoteMethodControl)cpp.space;
	} else if (obj instanceof OutriggerServer &&
		   obj instanceof RemoteMethodControl) 
	{
	    // obj may be our inner proxy, which does not hold
	    // a ref to uuid, to simplify the logic just make
	    // inputProxyUuid = uuid.
	    inputProxyUuid = uuid;
	    inputProxyServer = (RemoteMethodControl)obj;
        } else {
	    final LandlordProxyVerifier lpv = 
		new LandlordProxyVerifier((Landlord)server, uuid);
	    return lpv.isTrustedObject(obj, ctx);
        }

	// Check Uuid first
	if (!uuid.equals(inputProxyUuid))
	    return false;

        /* Get the client constraints currently set on the server reference
	 * of contained in the input proxy 
	 */
        final MethodConstraints mConstraints = 
	    inputProxyServer.getConstraints();

        /* Create a copy of the canonical server reference with its method
	 * constraints replaced with the method constraints on
	 * server reference of the input proxy.
         */
        final TrustEquivalence constrainedServer =
             (TrustEquivalence)server.setConstraints(mConstraints);
 
       /* With respect to trust, content, and function, test whether
	* the server reference from the input proxy is equivalent to
	* the canonical server reference we have (once method
	* constraints have been normalized.)
	*/
        return constrainedServer.checkTrustEquivalence(inputProxyServer);
    }

    /**
     * Verifies that the server reference implements
     * <code>TrustEquivalence</code>.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	if (server == null)
	    throw new InvalidObjectException("null server reference");

	if (uuid == null)
	    throw new InvalidObjectException("null uuid reference");

	if (!(server instanceof TrustEquivalence))
	    throw new InvalidObjectException(
		"server does not implement TrustEquivalence");

	if (!(server instanceof Landlord))
	    throw new InvalidObjectException(
		 "server does not implement landlord");
    }
}
