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
package org.apache.river.norm;

import org.apache.river.landlord.ConstrainableLandlordLease;
import org.apache.river.landlord.Landlord;
import org.apache.river.landlord.LandlordProxyVerifier;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/** Defines a trust verifier for the smart proxies of a Norm server. */
@AtomicSerial
final class ProxyVerifier implements Serializable, TrustVerifier {
    private static final long serialVersionUID = 2;

    /**
     * The Norm server proxy.
     *
     * @serial
     */
    private final RemoteMethodControl serverProxy;

    /**
     * The unique ID for the Norm server.
     *
     * @serial
     */
    private final Uuid serverUuid;

    /**
     * Returns a verifier for the smart proxies of a Norm server with the
     * specified proxy and unique ID.
     *
     * @param serverProxy the Norm server proxy
     * @param serverUuid the unique ID for the Norm server
     * @throws UnsupportedOperationException if <code>serverProxy</code> does
     *	       not implement both {@link RemoteMethodControl} and {@link
     *	       TrustEquivalence}
     */
    ProxyVerifier(NormServer serverProxy, Uuid serverUuid) {
	this(serverProxy, serverUuid, check(serverProxy, serverUuid));
    }
    
    private static boolean check(NormServer serverProxy, Uuid serverUuid){
	if (!(serverProxy instanceof RemoteMethodControl)) {
	    throw new UnsupportedOperationException(
		"Verifier requires service proxy to implement " +
		"RemoteMethodControl");
	} else if (!(serverProxy instanceof TrustEquivalence)) {
	    throw new UnsupportedOperationException(
		"Verifier requires service proxy to implement " +
		"TrustEquivalence");
	}
	return true;
    }
    
    ProxyVerifier(GetArg arg) throws IOException {
	this((NormServer) arg.get("serverProxy", null),
	    (Uuid) arg.get("serverUuid", null),
	    check(arg));
    }
    
    private static boolean check(GetArg arg) throws IOException {
	NormServer serverProxy = (NormServer) arg.get("serverProxy", null);
	Uuid serverUuid = (Uuid) arg.get("serverUuid", null);
	try {
	    return check(serverProxy, serverUuid);
	} catch(UnsupportedOperationException e){
	    InvalidObjectException ex = new InvalidObjectException("invariants unsatisfied");
	    ex.initCause(e);
	    throw ex;
	}
    }
    
    private ProxyVerifier(NormServer serverProxy, Uuid serverUuid, boolean check){
	this.serverProxy = (RemoteMethodControl) serverProxy;
	this.serverUuid = serverUuid;
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	if (obj == null || ctx == null) {
	    throw new NullPointerException("Arguments must not be null");
	} else if (obj instanceof ConstrainableLandlordLease) {
	    return new LandlordProxyVerifier(
		(Landlord) serverProxy, serverUuid).isTrustedObject(obj, ctx);
	}
	RemoteMethodControl otherServerProxy;
	if (obj instanceof SetProxy.ConstrainableSetProxy) {
	    if (!isTrustedObject(((SetProxy) obj).getRenewalSetLease(), ctx)) {
		return false;
	    }
	    otherServerProxy =
		(RemoteMethodControl) ((AbstractProxy) obj).server;
	} else if (obj instanceof AdminProxy.ConstrainableAdminProxy ||
		   obj instanceof NormProxy.ConstrainableNormProxy)
	{
	    if (!serverUuid.equals(((AbstractProxy) obj).uuid)) {
		return false;
	    }
	    otherServerProxy =
		(RemoteMethodControl) ((AbstractProxy) obj).server;
	} else if (obj instanceof RemoteMethodControl) {
	    otherServerProxy = (RemoteMethodControl) obj;
	} else {
	    return false;
	}
	MethodConstraints mc = otherServerProxy.getConstraints();
	TrustEquivalence trusted =
	    (TrustEquivalence) serverProxy.setConstraints(mc);
	return trusted.checkTrustEquivalence(otherServerProxy);
    }

    /** Require server proxy to implement TrustEquivalence. */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (!(serverProxy instanceof TrustEquivalence)) {
	    throw new InvalidObjectException(
		"serverProxy must implement TrustEquivalence");
	}
    }
}
