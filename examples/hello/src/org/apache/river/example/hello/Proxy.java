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

package org.apache.river.example.hello;

import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import net.jini.security.proxytrust.TrustEquivalence;

/** Define a smart proxy for the server. */
class Proxy implements Serializable, Hello {

    private static final long serialVersionUID = 2L;

    /** The server proxy */
    final Hello serverProxy;

    /**
     * Create a smart proxy, using an implementation that supports constraints
     * if the server proxy does.
     */
    static Proxy create(Hello serverProxy) {
	return (serverProxy instanceof RemoteMethodControl)
	    ? new ConstrainableProxy(serverProxy)
	    : new Proxy(serverProxy);
    }

    Proxy(Hello serverProxy) {
	this.serverProxy = serverProxy;
    }

    public boolean equals(Object o) {
	return getClass() == o.getClass()
	    && serverProxy.equals(((Proxy) o).serverProxy);
    }

    public int hashCode() {
	return serverProxy.hashCode();
    }

    /** Implement Hello. */
    public String sayHello() throws RemoteException {
	System.out.println("Calling sayHello in smart proxy");
	return serverProxy.sayHello();
    }

    /** A constrainable implementation of the smart proxy. */
    private static final class ConstrainableProxy extends Proxy
	implements RemoteMethodControl
    {
        private static final long serialVersionUID = 2L;

	ConstrainableProxy(Hello serverProxy) {
	    super(serverProxy);
	}

	/** Implement RemoteMethodControl */

	public MethodConstraints getConstraints() {
	    return ((RemoteMethodControl) serverProxy).getConstraints();
	}

	public RemoteMethodControl setConstraints(MethodConstraints mc) {
	    return new ConstrainableProxy(
		(Hello) ((RemoteMethodControl) serverProxy).setConstraints(
		    mc));
	}

	/*
	 * Provide access to the underlying server proxy to permit the
	 * ProxyTrustVerifier class to verify the proxy.
	 */
	private ProxyTrustIterator getProxyTrustIterator() {
	    return new SingletonProxyTrustIterator(serverProxy);
	}
    }

    /** A trust verifier for secure smart proxies. */
    final static class Verifier implements TrustVerifier, Serializable {

        private static final long serialVersionUID = 2L;

	private final RemoteMethodControl serverProxy;
    
	/**
	 * Create the verifier, throwing UnsupportedOperationException if the
	 * server proxy does not implement both RemoteMethodControl and
	 * TrustEquivalence.
	 */
	Verifier(Hello serverProxy) {
	    if (serverProxy instanceof RemoteMethodControl &&
		serverProxy instanceof TrustEquivalence)
	    {
		this.serverProxy = (RemoteMethodControl) serverProxy;
	    } else {
		throw new UnsupportedOperationException();
	    }
	}

	/** Implement TrustVerifier */
	public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	    throws RemoteException
	{
	    if (obj == null || ctx == null) {
		throw new NullPointerException();
	    } else if (!(obj instanceof ConstrainableProxy)) {
		return false;
	    }
	    RemoteMethodControl otherServerProxy =
		(RemoteMethodControl) ((ConstrainableProxy) obj).serverProxy;
	    MethodConstraints mc = otherServerProxy.getConstraints();
	    TrustEquivalence trusted =
		(TrustEquivalence) serverProxy.setConstraints(mc);
	    return trusted.checkTrustEquivalence(otherServerProxy);
	}
    }
}
