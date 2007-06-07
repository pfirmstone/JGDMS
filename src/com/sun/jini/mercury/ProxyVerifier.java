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
package com.sun.jini.mercury;

import com.sun.jini.landlord.ConstrainableLandlordLease;
import com.sun.jini.landlord.Landlord;
import com.sun.jini.landlord.LandlordProxyVerifier;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.ReferentUuid;
import net.jini.id.Uuid;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Defines a trust verifier for the smart proxies of a Mercury server. */
final class ProxyVerifier implements TrustVerifier, Serializable {

    private static final long serialVersionUID = 1L;

    /** Logger for logging information about this instance */
    private static final Logger logger = 
	Logger.getLogger("net.jini.security.trust");

    private static final String proxyVerifierSourceClass = 
	ProxyVerifier.class.getName();

    /** The Mercury server proxy. */
    private final RemoteMethodControl serverProxy;
    
    /**
     * The unique identifier associated with the backend server referenced
     * by the <code>serverProxy</code>, used for comparison with the IDs
     * extracted from the smart proxies being verified.
     *
     * @serial
     */
    private final Uuid proxyID;

    /**
     * Returns a verifier for the smart proxies of the specified Mercury server
     * proxy.
     *
     * @param serverProxy the Mercury server proxy
     * @throws UnsupportedOperationException if <code>serverProxy</code> does
     *	       not implement both {@link RemoteMethodControl} and {@link
     *	       TrustEquivalence}
     */
    ProxyVerifier(MailboxBackEnd serverProxy, Uuid proxyID) {
	if (!(serverProxy instanceof RemoteMethodControl)) {
	    throw new UnsupportedOperationException(
		"No verifier available for non-constrainable service");
	} else if (!(serverProxy instanceof TrustEquivalence)) {
	    throw new UnsupportedOperationException(
		"Verifier requires service proxy to implement " +
		"TrustEquivalence");
	} else if (proxyID == null) {
	    throw new IllegalArgumentException(
	        "Proxy id cannot be null");
	}
	this.serverProxy = (RemoteMethodControl) serverProxy;
        this.proxyID = proxyID;
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	if (logger.isLoggable(Level.FINER)) {
	    logger.entering(proxyVerifierSourceClass, "isTrustedObject",
	        new Object[] { obj, ctx });
	}
	if (obj == null || ctx == null) {
	    throw new NullPointerException("Arguments must not be null");
	}
	RemoteMethodControl otherServerProxy;
	Uuid inputProxyID = null;
	if (obj instanceof Registration.ConstrainableRegistration) {
	    Registration reg = (Registration) obj;
	    // verify sub-components
	    if (!isTrustedObject(reg.lease, ctx) ||
		!isTrustedObject(reg.listener, ctx)) {
		return false;
	    }
	    otherServerProxy = (RemoteMethodControl) reg.mailbox;
	} else if (obj instanceof MailboxBackEnd && 
	           obj instanceof RemoteMethodControl) {
            /* Inner proxy verification case. To simplify logic, below,
	     * just assume the same Uuid that we have in hand.
	     */ 
	    otherServerProxy = (RemoteMethodControl)obj;
	    inputProxyID = proxyID;
	} else if (obj instanceof MailboxProxy.ConstrainableMailboxProxy) {
	    otherServerProxy = (RemoteMethodControl) ((MailboxProxy)obj).mailbox;
	    inputProxyID = ((ReferentUuid)obj).getReferentUuid();
	} else if (obj instanceof MailboxAdminProxy.ConstrainableMailboxAdminProxy) {
	    otherServerProxy = (RemoteMethodControl) ((MailboxAdminProxy)obj).server;
	    inputProxyID = ((ReferentUuid)obj).getReferentUuid();
	} else if (obj instanceof ListenerProxy.ConstrainableListenerProxy) {
	    otherServerProxy = (RemoteMethodControl) ((ListenerProxy)obj).server;
	} else if (obj instanceof ConstrainableLandlordLease) {
	    final LandlordProxyVerifier lpv =
		new LandlordProxyVerifier((Landlord)serverProxy, proxyID);
	    return lpv.isTrustedObject(obj, ctx);
	} else {
	    logger.log(Level.FINEST, "Object {0} is not a supported type",
		obj);
	    return false;
	}
	
	// For top-level proxies, quickly verify proxy Uuid
	if ((inputProxyID != null) &&
	    !(proxyID.equals(inputProxyID))) {
	    return false;
	}

	MethodConstraints mc = otherServerProxy.getConstraints();
	TrustEquivalence trusted =
	    (TrustEquivalence) serverProxy.setConstraints(mc);
	boolean result = trusted.checkTrustEquivalence(otherServerProxy);
	if (logger.isLoggable(Level.FINER)) {
	    logger.exiting(proxyVerifierSourceClass, "isTrustedObject", 
	        Boolean.valueOf(result));
	}
	return result;
    }
}
