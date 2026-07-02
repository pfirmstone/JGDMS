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
package org.apache.river.reggie.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Trust verifier for smart proxies used by Reggie.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public final class ProxyVerifier implements TrustVerifier {

    private static final String SERVER = "server";
    private static final String REGISTRAR_ID_MOST_SIG = "registrarIDMostSig";
    private static final String REGISTRAR_ID_LEAST_SIG = "registrarIDLeastSig";

    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            /** @serialField Canonical service reference. */
            new SerialForm(SERVER, RemoteMethodControl.class),
            /** @serialField Most significant bits of the registrar's service ID. */
            new SerialForm(REGISTRAR_ID_MOST_SIG, Long.TYPE),
            /** @serialField Least significant bits of the registrar's service ID. */
            new SerialForm(REGISTRAR_ID_LEAST_SIG, Long.TYPE)
        };
    }

    public static void serialize(PutArg arg, ProxyVerifier pv) throws IOException{
        arg.put(SERVER, pv.server);
        arg.put(REGISTRAR_ID_MOST_SIG, pv.registrarID.getMostSignificantBits());
        arg.put(REGISTRAR_ID_LEAST_SIG, pv.registrarID.getLeastSignificantBits());
        arg.writeArgs();
    }

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
    private final ServiceID registrarID;

    /**
     * Constructs proxy verifier which compares server references extracted
     * from smart proxies with the given canonical server reference, which must
     * implement both RemoteMethodControl and TrustEquivalence.  For proxies
     * which contain a copy of the registrar's service ID, that copy is
     * compared against the given service ID to ensure consistency.
     */
    public ProxyVerifier(Registrar server, ServiceID registrarID) {
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
     * Verifies that the deserialized server reference implements both
     * RemoteMethodControl and TrustEquivalence, mirroring the validation
     * formerly performed in readObject.
     */
    private static GetArg check(GetArg arg) throws IOException, ClassNotFoundException {
	Object server = arg.get(SERVER, null);
	if (!(server instanceof RemoteMethodControl)) {
	    throw new InvalidObjectException(
		"server does not implement RemoteMethodControl");
	} else if (!(server instanceof TrustEquivalence)) {
	    throw new InvalidObjectException(
		"server does not implement TrustEquivalence");
	}
	return arg;
    }

    public ProxyVerifier(GetArg arg) throws IOException, ClassNotFoundException {
	this(check(arg), true);
    }

    private ProxyVerifier(GetArg arg, boolean check)
	throws IOException, ClassNotFoundException
    {
	server = (RemoteMethodControl) arg.get(SERVER, null);
	registrarID = new ServiceID(
		arg.get(REGISTRAR_ID_MOST_SIG, 0L),
		arg.get(REGISTRAR_ID_LEAST_SIG, 0L));
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
}
