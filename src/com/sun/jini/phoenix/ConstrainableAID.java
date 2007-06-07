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

package com.sun.jini.phoenix;

import com.sun.jini.proxy.ConstrainableProxyUtil;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationID;
import java.rmi.server.UID;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * A subclass of <code>AID</code> that implements the {@link
 * RemoteMethodControl} interface by delegating to the contained activator.
 * 
 * <p>This class exists as a convenience for activation system daemon
 * implementations supporting <code>RemoteMethodControl</code> on proxies,
 * to avoid requiring all such implementations to make code available for
 * dynamic download to clients.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 **/
final class ConstrainableAID extends AID
	implements RemoteMethodControl, TrustEquivalence
{
    private static final long serialVersionUID = 2625527831091986783L;
    private static final Method[] methodMapping = new Method[2];

    static {
	try {
	    methodMapping[0] =
		ActivationID.class.getMethod("activate",
					     new Class[]{boolean.class});
	    methodMapping[1] =
		Activator.class.getMethod("activate",
					  new Class[]{ActivationID.class,
						      boolean.class});
	} catch (NoSuchMethodException e) {
	    throw new AssertionError(e);
	}
    }

    /** the client constraints */
    private final MethodConstraints constraints;

    static final class State implements Serializable {
	private static final long serialVersionUID = 1673734348880788487L;
	private final Activator activator;
	private final UID uid;
	private final MethodConstraints constraints;

	State(Activator activator, UID uid, MethodConstraints constraints) {
	    this.activator = activator;
	    this.uid = uid;
	    this.constraints = constraints;
	}

	private Object readResolve() throws InvalidObjectException {
	    ConstrainableProxyUtil.verifyConsistentConstraints(
		constraints, activator, methodMapping);
	    return new ConstrainableAID(activator, uid, constraints);
	}
    }

    /**
     * A <code>ProxyTrust</code> trust verifier for
     * <code>ConstrainableAID</code> instances.
     *
     * @since 2.0
     **/
    static final class Verifier implements TrustVerifier, Serializable {
	private static final long serialVersionUID = 570158651966790233L;

	/**
	 * The expected activator.
	 *
	 * @serial
	 */
	private final RemoteMethodControl activator;

	/**
	 * Creates a verifier for the specified activator.
	 *
	 * @param activator the activator
	 * @throws IllegalArgumentException if the specified activator is
	 * not an instance of <code>RemoteMethodControl</code> or
	 * <code>TrustEquivalence</code>
	 */
	Verifier(Activator activator) {
	    if (!(activator instanceof RemoteMethodControl)) {
		throw new IllegalArgumentException(
		    "activator not a RemoteMethodControl instance");
	    } else if (!(activator instanceof TrustEquivalence)) {
		throw new IllegalArgumentException(
				"activator must implement TrustEquivalence");
	    }
	    this.activator = (RemoteMethodControl) activator;
	}

	/**
	 * Verifies trust in a proxy. Returns <code>true</code> if the
	 * proxy passed as an argument is an instance of
	 * <code>ConstrainableAID</code> and that proxy's internal
	 * activator instance is trust equivalent to the activator of this
	 * verifier; returns <code>false</code> otherwise.
	 **/
	public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	    throws RemoteException
	{
	    if (obj == null || ctx == null) {
		throw new NullPointerException();
	    } else if (!(obj instanceof ConstrainableAID)) {
		return false;
	    }
	    RemoteMethodControl act =
		(RemoteMethodControl) ((ConstrainableAID) obj).activator;
	    MethodConstraints mc = act.getConstraints();
	    TrustEquivalence trusted =
		(TrustEquivalence) activator.setConstraints(mc);
	    return trusted.checkTrustEquivalence(act);
	}
    }

    /**
     * Creates an activation identifier containing the specified
     * remote object activator, a new unique identifier, and
     * <code>null</code> client constraints.
     *
     * @param activator the activator
     * @param uid the unique identifier
     * @throws IllegalArgumentException if the specified activator is not
     * an instance of <code>RemoteMethodControl</code> or
     * <code>TrustEquivalence</code>
     */
    public ConstrainableAID(Activator activator, UID uid) {
	this(activator, uid, null);
    }

    /**
     * Creates an activation identifier containing the specified
     * remote object activator and a new unique identifier.
     *
     * @param activator the activator
     * @param uid the unique identifier
     * @param constraints the client constraints, or <code>null</code>
     * @throws IllegalArgumentException if the specified activator is not
     * an instance of <code>RemoteMethodControl</code> or
     * <code>TrustEquivalence</code>
     */
    private ConstrainableAID(Activator activator,
			     UID uid,
			     MethodConstraints constraints)
    {
	super(activator, uid);
	if (!(activator instanceof RemoteMethodControl)) {
	    throw new IllegalArgumentException(
		"activator not RemoteMethodControl instance");
	} else if (!(activator instanceof TrustEquivalence)) {
	    throw new IllegalArgumentException(
		"activator not TrustEquivalence instance");
	}
	this.constraints = constraints;
    }

    /** Returns an iterator that yields the activator. */
    private ProxyTrustIterator getProxyTrustIterator() {
	return new SingletonProxyTrustIterator(activator);
    }

    /**
     * Returns a new copy of this activation identifier containing the same
     * unique identifier and a copy of the activator with the new specified
     * constraints.
     *
     * @param constraints the client constraints, or <code>null</code>
     * @return a new copy of this activation identifier containing the same
     * unique identifier and a copy of the activator with the new specified
     * client constraints
     * @see #getConstraints
     */
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	MethodConstraints actConstraints =
	    ConstrainableProxyUtil.translateConstraints(constraints,
							methodMapping);
	RemoteMethodControl act =
	    ((RemoteMethodControl) activator).setConstraints(actConstraints);
	return new ConstrainableAID((Activator) act, uid, constraints);
    }

    /**
     * Returns the client constraints.
     *
     * @return the client constraints, or <code>null</code>
     * @see #setConstraints
     */
    public MethodConstraints getConstraints() {
	return constraints;
    }

    /**
     * Returns true if the object is an instance of this class with the same
     * UID and a trust equivalent activator.
     */
    public boolean checkTrustEquivalence(Object obj) {
	if (!(obj instanceof ConstrainableAID)) {
	    return false;
	}
	ConstrainableAID aid = (ConstrainableAID) obj;
	return (uid.equals(aid.uid) &&
		((TrustEquivalence) activator).checkTrustEquivalence(
							   aid.activator));
    }

    private Object writeReplace() {
	return new State(activator, uid, constraints);
    }

}
