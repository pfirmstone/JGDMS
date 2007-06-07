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
package com.sun.jini.reggie;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * Trust verifier for smart proxies used by Reggie.
 *
 * @author Sun Microsystems, Inc.
 *
 */
final class ProxyVerifier implements TrustVerifier, Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * Canonical service reference, used for comparison with inner server
     * references extracted from smart proxies to verify.
     *
     * @serial
     */
    private final RemoteMethodControl server;
    /**
     * The registrar's service ID, used for comparison with registrar service
     * IDs extracted from smart proxies to verify.
     */
    private transient ServiceID registrarID;

    /**
     * Constructs proxy verifier which compares server references extracted
     * from smart proxies with the given canonical server reference, which must
     * implement both RemoteMethodControl and TrustEquivalence.  For proxies
     * which contain a copy of the registrar's service ID, that copy is
     * compared against the given service ID to ensure consistency.
     */
    ProxyVerifier(Registrar server, ServiceID registrarID) {
	if (!(server instanceof RemoteMethodControl)) {
	    throw new UnsupportedOperationException(
		"server does not implement RemoteMethodControl");
	} else if (!(server instanceof TrustEquivalence)) {
	    throw new UnsupportedOperationException(
		"server does not implement TrustEquivalence");
	}
	this.server = (RemoteMethodControl) server;
	this.registrarID = registrarID;
    }

    /**
     * Returns true if the given object is a trusted proxy, or false otherwise.
     * The given object is trusted if it is trust equivalent to the canonical
     * server reference carried by this trust verifier, or if it is an instance
     * of one of Reggie's constrainable smart proxy classes, and all component
     * proxies it contains are trusted, and its inner server reference is trust
     * equivalent to the canonical server reference, and its inner copy of the
     * registrar's service ID (if it has one) is equal to the service ID
     * carried by this verifier.
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	if (obj == null || ctx == null) {
	    throw new NullPointerException();
	}
	RemoteMethodControl inputServer;
	ServiceID inputRegistrarID;
	if (obj instanceof ConstrainableRegistrarProxy) {
	    RegistrarProxy proxy = (RegistrarProxy) obj;
	    inputServer = (RemoteMethodControl) proxy.server;
	    inputRegistrarID = proxy.registrarID;
	} else if (obj instanceof ConstrainableAdminProxy) {
	    AdminProxy proxy = (AdminProxy) obj;
	    inputServer = (RemoteMethodControl) proxy.server;
	    inputRegistrarID = proxy.registrarID;
	} else if (obj instanceof ConstrainableRegistration) {
	    Registration reg = (Registration) obj;
	    if (!isTrustedObject(reg.lease, ctx)) {
		return false;
	    }
	    inputServer = (RemoteMethodControl) reg.server;
	    inputRegistrarID = registrarID;
	} else if (obj instanceof ConstrainableEventLease ||
		   obj instanceof ConstrainableServiceLease)
	{
	    RegistrarLease lease = (RegistrarLease) obj;
	    inputServer = (RemoteMethodControl) lease.server;
	    inputRegistrarID = lease.registrarID;
	} else if (obj instanceof RemoteMethodControl) {
	    inputServer = (RemoteMethodControl) obj;
	    inputRegistrarID = registrarID;
	} else {
	    return false;
	}

	TrustEquivalence trustEquiv = (TrustEquivalence)
	    server.setConstraints(inputServer.getConstraints());
	return trustEquiv.checkTrustEquivalence(inputServer) &&
	       registrarID.equals(inputRegistrarID);
    }

    /**
     * Writes the default serializable field value for this instance, followed
     * by the registrar's service ID encoded as specified by the
     * ServiceID.writeBytes method.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
	registrarID.writeBytes(out);
    }

    /**
     * Reads the default serializable field value for this instance, followed
     * by the registrar's service ID encoded as specified by the
     * ServiceID.writeBytes method.  Verifies that the deserialized registrar
     * reference implements both RemoteMethodControl and TrustEquivalence.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	registrarID = new ServiceID(in);
	if (!(server instanceof RemoteMethodControl)) {
	    throw new InvalidObjectException(
		"server does not implement RemoteMethodControl");
	} else if (!(server instanceof TrustEquivalence)) {
	    throw new InvalidObjectException(
		"server does not implement TrustEquivalence");
	}
    }
}
