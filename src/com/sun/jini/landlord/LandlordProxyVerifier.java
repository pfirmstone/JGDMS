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
package com.sun.jini.landlord;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.id.Uuid;
import net.jini.id.ReferentUuid;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;

/** 
 * This class defines a trust verifier for the proxies defined
 * in the landlord package.
 *
 * @see net.jini.security.TrustVerifier
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
final public class LandlordProxyVerifier 
    implements Serializable, TrustVerifier 
{
    private static final long serialVersionUID = 1L;

    /** 
     * The canonical instance of the server reference. This
     * instance will be used by the <code>isTrusted</code> method 
     * as the known trusted object used to determine whether or not a
     * given proxy is equivalent in trust, content, and function.
     *
     * @serial
     */
    private final RemoteMethodControl landlord;

    /**
     * The <code>Uuid</code> associated <code>landlord</code>.
     * @serial
     */
    private final Uuid landlordUuid;

    /**
     * Returns a verifier for the proxies defined in the landlord
     * package with the specified server reference and server
     * <code>Uuid</code>.
     *
     * @param landlord the reference to the <code>Landlord</code>
     *                 being used by the leases for communication
     *                 back to the server.
     * @param landlordUuid a universally unique id that has been
     *                 assigned to the server granting of the lease.
     *                 Ideally the <code>Uuid</code> {@link
     *                 ReferentUuid#getReferentUuid landlord.getUuid} would
     *                 return if <code>landlord</code> implemented
     *                 {@link ReferentUuid}. Used to determine when
     *                 two leases can be batched together.  
     * @throws UnsupportedOperationException if <code>landlord</code> does
     *	       not implement both {@link RemoteMethodControl} and {@link
     *         TrustEquivalence} 
     * @throws NullPointerException if either argument is 
     *         <code>null</code>.
     */
    public LandlordProxyVerifier(Landlord landlord, Uuid landlordUuid) {
	if (landlord == null)
	    throw new NullPointerException("landlord must not be null");

	if (landlordUuid == null)
	    throw new NullPointerException("landlordUuid must not be null");

        if(!(landlord instanceof RemoteMethodControl)) {
            throw new UnsupportedOperationException
		("cannot construct verifier - server reference does not " +
		 "implement RemoteMethodControl");
	}
	
	if (!(landlord instanceof TrustEquivalence)) {
            throw new UnsupportedOperationException
		("cannot construct verifier - server reference does not " +
		 "implement TrustEquivalence");
        }

        this.landlord = (RemoteMethodControl)landlord;
	this.landlordUuid = landlordUuid;
    }

    /** 
     * Returns <code>true</code> if the specified proxy object (that is
     * not yet known to be trusted) is equivalent in trust, content, and
     * function to the canonical server reference this object was 
     * constructed with; otherwise returns <code>false</code>.
     *
     * @param obj proxy object that will be compared to this class's stored
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
         *  - ConstrainableLandlordLease
         */

	// Server reference from input proxy
        final RemoteMethodControl inputProxyServer;
        if (obj instanceof ConstrainableLandlordLease) {
	    final ConstrainableLandlordLease cll = 
		(ConstrainableLandlordLease)obj;

	    // Check the landlordUuid of obj
	    if (!landlordUuid.equals(cll.landlordUuid()))
		return false;

	    // Extract the landlord ref
            inputProxyServer = (RemoteMethodControl)cll.landlord();
        } else {
	    // A proxy type we don't know about
            return false;
        }

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
             (TrustEquivalence)landlord.setConstraints(mConstraints);
 
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

	if (landlord == null)
	    throw new InvalidObjectException("null landlord reference");

	if (landlordUuid == null)
	    throw new InvalidObjectException("null landlordUuid reference");

	if (!(landlord instanceof TrustEquivalence)) {
	    throw new InvalidObjectException(
		"server does not implement TrustEquivalence");
	}
    }
}
