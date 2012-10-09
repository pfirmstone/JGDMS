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
package com.sun.jini.test.share;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.core.transaction.server.*;

/**
 * Trust verifier for smart proxies used by Reggie.
 *
 * 
 *
 */
final class TesterTransactionManagerProxyVerifier implements TrustVerifier, Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * Canonical service reference, used for comparison with inner server
     * references extracted from smart proxies to verify.
     *
     * @serial
     */
    private final RemoteMethodControl server;

    /**
     * Constructs proxy verifier which compares server references extracted
     * from smart proxies with the given canonical server reference, which must
     * implement both RemoteMethodControl and TrustEquivalence.
     */
    TesterTransactionManagerProxyVerifier(TransactionManager server) {
	if (!(server instanceof RemoteMethodControl)) {
	    throw new UnsupportedOperationException(
		"server does not implement RemoteMethodControl");
	} else if (!(server instanceof TrustEquivalence)) {
	    throw new UnsupportedOperationException(
		"server does not implement TrustEquivalence");
	}
	this.server = (RemoteMethodControl) server;
    }

    /**
     * Returns true if the given object is a trusted proxy, or false otherwise.
     * The given object is trusted if it is an instance of one of Reggie's
     * constrainable smart proxy classes, and all component proxies it contains
     * are trusted, and its inner server reference is trust equivalent to the
     * canonical server reference carried by this trust verifier.
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	if (obj == null || ctx == null) {
	    throw new NullPointerException();
	}
	RemoteMethodControl inputServer;
	if (obj instanceof TesterTransactionManagerConstrainableProxy) {
	    inputServer = (RemoteMethodControl) ((TesterTransactionManagerProxy) obj).server;
	} else {
	    return false;
	}

	TrustEquivalence trustEquiv = (TrustEquivalence)
	    server.setConstraints(inputServer.getConstraints());
	return trustEquiv.checkTrustEquivalence(inputServer);
    }

    /**
     * Verifies that the server reference implements both RemoteMethodControl
     * and TrustEquivalence.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (!(server instanceof RemoteMethodControl)) {
	    throw new InvalidObjectException(
		"server does not implement RemoteMethodControl");
	} else if (!(server instanceof TrustEquivalence)) {
	    throw new InvalidObjectException(
		"server does not implement TrustEquivalence");
	}
    }
}
