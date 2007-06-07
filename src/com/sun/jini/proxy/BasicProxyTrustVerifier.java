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

package com.sun.jini.proxy;


import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * A basic trust verifier for proxies.  This trust verifier is used to
 * verify that object passed to its {@link #isTrustedObject
 * isTrustedObject} method is equivalent in trust, content, and function to
 * the known trusted object that the trust verifier is constructed with.
 * This trust verifier is typically returned by an implementation of the
 * {@link ServerProxyTrust#getProxyVerifier
 * ServerProxyTrust.getProxyVerifier} method.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public final class BasicProxyTrustVerifier
    implements TrustVerifier, Serializable
{
    private static final long serialVersionUID = 2L;
    
    /**
     * The trusted proxy.
     *
     * @serial
     */
    private final RemoteMethodControl proxy;

    /**
     * Creates a trust verifier containing the specified trusted proxy. 
     *
     * @param proxy the trusted proxy
     * @throws IllegalArgumentException if the specified proxy is
     * not an instance of {@link RemoteMethodControl} or
     * {@link TrustEquivalence}
     */
    public BasicProxyTrustVerifier(Object proxy) {
	if (!(proxy instanceof RemoteMethodControl)) {
	    throw new IllegalArgumentException(
		"proxy not a RemoteMethodControl instance");
	} else if  (!(proxy instanceof TrustEquivalence)) {
	    throw new IllegalArgumentException(
		"proxy not a TrustEquivalence instance");
	}
	this.proxy = (RemoteMethodControl) proxy;
    }
	
    /**
     * Verifies trust in a proxy. Returns <code>true</code> if and only if
     * the specified object is an instance of {@link RemoteMethodControl}
     * and invoking the {@link TrustEquivalence#checkTrustEquivalence
     * checkTrustEquivalence} method on a proxy that is this verifier's
     * proxy with the same method constraints of the specified object,
     * passing the specified object returns <code>true</code>.
     **/
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx) {
	if (obj == null || ctx == null) {
	    throw new NullPointerException();
	} else if (!(obj instanceof RemoteMethodControl)) {
	    return false;
	}
	RemoteMethodControl unverifiedProxy = (RemoteMethodControl) obj;
	MethodConstraints constraints = unverifiedProxy.getConstraints();
	TrustEquivalence trustedProxy =
	    (TrustEquivalence) proxy.setConstraints(constraints);
	return trustedProxy.checkTrustEquivalence(unverifiedProxy);
    }

    /**
     * @throws InvalidObjectException if the proxy is not an instance of
     * {@link RemoteMethodControl} and {@link TrustEquivalence}
     **/
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (!(proxy instanceof RemoteMethodControl)) {
	    throw new InvalidObjectException(
		"proxy not a RemoteMethodControl instance");
	} else if  (!(proxy instanceof TrustEquivalence)) {
	    throw new InvalidObjectException(
		"proxy not a TrustEquivalence instance");
	}
    }
}
    
