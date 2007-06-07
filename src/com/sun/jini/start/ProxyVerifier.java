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
package com.sun.jini.start;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Defines a trust verifier for the smart proxies of a SharedGroup server. */
final class ProxyVerifier implements TrustVerifier, Serializable {

    private static final long serialVersionUID = 1L;

    /** Logger and configuration component name for SharedGroup */
    protected static final String START_PROXY = "com.sun.jini.start.proxy";

    /** Logger for logging information about this instance */
    private static final Logger logger = Logger.getLogger(START_PROXY);

    /** The SharedGroup server proxy. */
    private final RemoteMethodControl serverProxy;

    /**
     * Returns a verifier for the smart proxies of the specified SharedGroup 
     * server proxy.
     *
     * @param serverProxy the SharedGroup server proxy
     * @throws UnsupportedOperationException if <code>serverProxy</code> does
     *	       not implement both {@link RemoteMethodControl} and {@link
     *	       TrustEquivalence}
     */
    ProxyVerifier(SharedGroupBackEnd serverProxy) {
	if (!(serverProxy instanceof RemoteMethodControl)) {
	    throw new UnsupportedOperationException(
		"No verifier available for non-secure service");
	} else if (!(serverProxy instanceof TrustEquivalence)) {
	    throw new UnsupportedOperationException(
		"Verifier requires service proxy to implement " +
		"TrustEquivalence");
	}
	this.serverProxy = (RemoteMethodControl) serverProxy;
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean isTrustedObject(
	Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	logger.entering(ProxyVerifier.class.getName(), "isTrustedObject",
	    new Object[] { obj, ctx });
	if (obj == null || ctx == null) {
	    throw new NullPointerException("Arguments must not be null");
	} 

	RemoteMethodControl otherServerProxy;
	if (obj instanceof SharedGroupBackEnd) {
	    otherServerProxy = (RemoteMethodControl) obj;
	} else {
	    logger.log(Level.FINEST, "Object {0} is not a supported type",
		obj);
	    return false;
	}

	MethodConstraints mc = otherServerProxy.getConstraints();
	TrustEquivalence trusted =
	    (TrustEquivalence) serverProxy.setConstraints(mc);
	boolean result = trusted.checkTrustEquivalence(otherServerProxy);
	logger.exiting(ProxyVerifier.class.getName(), "isTrustedObject", 
	    Boolean.valueOf(result));
	return result;
    }
}
