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

package net.jini.jeri;

import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.logging.Levels;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.ProtocolException;
import java.rmi.ConnectIOException;
import java.rmi.MarshalException;
import java.rmi.RemoteException;
import java.rmi.UnexpectedException;
import java.rmi.UnmarshalException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.IntegrityEnforcement;
import net.jini.security.proxytrust.TrustEquivalence;


/**
 * A basic implementation of the <code>InvocationHandler</code> interface.
 * This invocation handler implements Java(TM) Remote Method Invocation
 * (Java RMI) call semantics when handling
 * a remote invocation to a remote object.
 *
 * <p><code>BasicInvocationHandler</code> instances contain an
 * <code>ObjectEndpoint</code>, optional client constraints, and
 * optional server constraints.  The client and server constraints
 * control the handling of remote methods, and they are represented as
 * {@link MethodConstraints} objects that map remote methods to
 * corresponding per-method constraints.
 *
 * <p>Invocation requests sent via the {@link #invoke invoke} method use
 * the protocol defined in that method.  This invocation handler also
 * assumes that the return value conforms to the protocol outlined in the
 * {@link BasicInvocationDispatcher#dispatch
 * BasicInvocationDispatcher.dispatch} method.
 *
 * @author	Sun Microsystems, Inc.
 * @see		BasicInvocationDispatcher
 * @since 2.0
 *
 * @org.apache.river.impl
 *
 * This implementation's {@link #invoke invoke} method throws {@link
 * IllegalArgumentException} if a remote invocation is to be made and
 * the <code>proxy</code> argument is an instance of an interface
 * whose binary name is
 * <code>javax.management.MBeanServerConnection</code> or any of the
 * names produced by the following procedure:
 *
 * <blockquote>
 *
 * For each resource named
 * <code>org/apache/river/proxy/resources/InvocationHandler.moreProhibitedProxyInterfaces</code>
 * that is visible to the system class loader, the contents of the
 * resource are parsed as UTF-8 text to produce a list of interface
 * names.  The resource must contain a list of binary names of
 * interfaces, one per line.  Space and tab characters surrounding
 * each name, as well as blank lines, are ignored.  The comment
 * character is <tt>'#'</tt>; on each line, all characters starting
 * with the first comment character are ignored.
 *
 * </blockquote>
 *
 * <p>This implementation uses the {@link Logger} named
 * <code>net.jini.jeri.BasicInvocationHandler</code> to log
 * information at the following levels:
 *
 * <table summary="Describes what is logged by BasicInvocationHandler at
 *        various logging levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Levels#FAILED FAILED} <td> exception thrown from final
 * attempt to communicate a remote call
 * 
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td> exception caught in
 * attempt to communicate a remote call
 *
 * <tr> <td> {@link Level#FINE FINE} <td> remote method being invoked
 *
 * <tr> <td> {@link Level#FINE FINE} <td> successful return of remote call
 *
 * <tr> <td> {@link Level#FINEST FINEST} <td> more detailed information on
 * the above (for example, actual argument and return values) 
 *
 * </table>
 **/
public class BasicInvocationHandler
    implements InvocationHandler, TrustEquivalence, Serializable
{
    private static final long serialVersionUID = -783920361025791412L;

    /**
     * invoke logger
     **/
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicInvocationHandler");

    /** size of the method constraint cache (per instance) */
    private static final int CACHE_SIZE = 3;

    /**
     * The object endpoint for communicating with the remote object.
     *
     * @serial
     **/
    private final ObjectEndpoint oe;

    /**
     * The client constraints, or <code>null</code>.
     *
     * @serial
     **/
    private final MethodConstraints clientConstraints;

    /**
     * The server constraints, or <code>null</code>.
     *
     * @serial
     **/
    private final MethodConstraints serverConstraints;

    /*
     * The method constraint cache maps remote methods to their
     * combined client and server constraints:
     */

    /** lock guarding cacheIndex, methodCache, and constraintCache */
    private transient Object cacheLock = new Object();

    /** next index to (over)write in the method constraint cache */
    private transient int cacheIndex;

    /** method constraint cache keys (unused entries are null) */
    private transient Method[] methodCache;

    /** method constraint cache values (unused entries are null) */
    private transient InvocationConstraints[] constraintCache;

    /**
     * Creates a new <code>BasicInvocationHandler</code> with the
     * specified <code>ObjectEndpoint</code> and server constraints.
     *
     * <p>The client constraints of the created
     * <code>BasicInvocationHandler</code> will be <code>null</code>.
     *
     * @param	oe the <code>ObjectEndpoint</code> for this invocation handler
     * @param	serverConstraints the server constraints, or <code>null</code>
     *
     * @throws	NullPointerException if <code>oe</code> is <code>null</code>
     **/
    public BasicInvocationHandler(ObjectEndpoint oe,
				  MethodConstraints serverConstraints)
    {
	if (oe == null) {
	    throw new NullPointerException();
	}
	this.oe = oe;
	this.clientConstraints = null;
	this.serverConstraints = serverConstraints;
    }

    /**
     * Creates a new <code>BasicInvocationHandler</code> with the
     * specified client constraints and with the same
     * <code>ObjectEndpoint</code> and server constraints as the given
     * other <code>BasicInvocationHandler</code>.
     *
     * <p>This constructor is intended for use by the
     * <code>BasicInvocationHandler</code> implementation of the
     * {@link #setClientConstraints} method.  To create a copy of a
     * given <code>BasicInvocationHandler</code> with new client
     * constraints, use the {@link RemoteMethodControl#setConstraints
     * RemoteMethodControl.setConstraints} method on the containing
     * proxy.
     *
     * @param	other the <code>BasicInvocationHandler</code> to obtain the
     *		<code>ObjectEndpoint</code> and server constraints from
     * @param	clientConstraints the client constraints, or
     *		<code>null</code>
     *
     * @throws	NullPointerException if <code>other</code> is
     *		<code>null</code>
     **/
    public BasicInvocationHandler(BasicInvocationHandler other,
				  MethodConstraints clientConstraints)
    {
	this.oe = other.oe;
	this.clientConstraints = clientConstraints;
	this.serverConstraints = other.serverConstraints;
    }

    /**
     * Processes a method invocation made on the encapsulating
     * proxy instance, <code>proxy</code>, and returns the result.
     * This method is invoked when a method is invoked on a proxy
     * instance that this handler is associated with.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * as follows:
     *
     * <p>If <code>method</code> is one of the following methods, it
     * is processed as described below:
     *
     * <ul>
     *
     * <li>{@link Object#hashCode Object.hashCode}: Returns the hash
     * code value for the proxy.
     *
     * <li>{@link Object#equals Object.equals}: Returns
     * <code>true</code> if the argument (<code>args[0]</code>) is an
     * instance of a dynamic proxy class that implements the same
     * ordered set of interfaces as <code>proxy</code> and this
     * invocation handler is equal to the invocation handler of that
     * argument, and returns <code>false</code> otherwise.
     *
     * <li>{@link Object#toString Object.toString}: Returns a string
     * representation of the proxy.
     *
     * <li>{@link RemoteMethodControl#setConstraints
     * RemoteMethodControl.setConstraints}: Returns a new proxy
     * instance of the same class as <code>proxy</code> containing a
     * new invocation handler with the specified new client
     * constraints.  The new invocation handler is created by invoking
     * the {@link #setClientConstraints setClientConstraints} method
     * of this object with the specified client constraints
     * (<code>args[0]</code>).  An exception is thrown if
     * <code>proxy</code> is not an instance of a dynamic proxy class
     * containing this invocation handler.
     *
     * <li>{@link RemoteMethodControl#getConstraints
     * RemoteMethodControl.getConstraints}: Returns this
     * <code>BasicInvocationHandler</code>'s client constraints.
     *
     * <li>{@link TrustEquivalence#checkTrustEquivalence
     * TrustEquivalence.checkTrustEquivalence}: Returns
     * <code>true</code> if the argument (<code>args[0]</code>) is an
     * instance of a dynamic proxy class that implements the same
     * ordered set of interfaces as <code>proxy</code> and invoking
     * the {@link #checkTrustEquivalence checkTrustEquivalence} method
     * of this object with the invocation handler of that argument
     * returns <code>true</code>, and returns <code>false</code>
     * otherwise.
     *
     * </ul>
     *
     * <p>Otherwise, a remote call is made as follows:
     *
     * <p>The object endpoint's {@link ObjectEndpoint#newCall newCall}
     * method is invoked to obtain an {@link OutboundRequestIterator},
     * passing constraints obtained by combining the client and server
     * constraints for the specified remote method and making them
     * absolute.  If the returned iterator's {@link
     * OutboundRequestIterator#hasNext hasNext} method returns
     * <code>false</code>, then this method throws a {@link
     * ConnectIOException}.  Otherwise, the iterator is used to make
     * one or more attempts to communicate the remote call.  Each
     * attempt proceeds as follows:
     *
     * <blockquote>
     *
     * The iterator's {@link OutboundRequestIterator#next next} method
     * is invoked to obtain an {@link OutboundRequest OutboundRequest}
     * for the current attempt.  If <code>next</code> returns
     * normally, the request's {@link
     * OutboundRequest#getUnfulfilledConstraints
     * getUnfulfilledConstraints} method is invoked, and if the
     * returned requirements or preferences include {@link
     * Integrity#YES Integrity.YES}, object integrity is enforced for
     * the current remote call attempt.  If the returned requirements
     * include any constraint other than an {@link Integrity}
     * constraint, an {@link UnsupportedConstraintException} is
     * generated and, as described below, wrapped and handled like any
     * other <code>Exception</code> thrown from a remote call attempt.
     * Otherwise, the marshalling of the remote call proceeds with the
     * following steps in order:
     *
     * <ul>
     *
     * <li>A byte of value <code>0x00</code> is written to the request
     * output stream of the <code>OutboundRequest</code> to indicate
     * the version of the marshalling protocol being used.
     *
     * <li>A byte is written to specify object integrity enforcement:
     * <code>0x01</code> if object integrity is being enforced for
     * this remote call attempt, and <code>0x00</code> otherwise.
     *
     * <li>A client context collection is created containing an {@link
     * IntegrityEnforcement} element that reflects whether or not
     * object integrity is being enforced for this remote call
     * attempt.
     *
     * <li>The {@link #createMarshalOutputStream
     * createMarshalOutputStream} method is invoked, passing
     * <code>proxy</code>, <code>method</code>, the
     * <code>OutboundRequest</code>, and the client context, to create
     * the marshal output stream for marshalling the remote call.
     *
     * <li>The {@link #marshalMethod marshalMethod} method of this
     * invocation handler is invoked with <code>proxy</code>,
     * <code>method</code>, the marshal output stream, and the client
     * context.
     *
     * <li>The {@link #marshalArguments marshalArguments} method of
     * this invocation handler is invoked with <code>proxy</code>,
     * <code>method</code>, <code>args</code>, the marshal output
     * stream, and the client context.
     *
     * <li>The marshal output stream is closed.
     *
     * </ul>
     *
     * <p>Then the object endpoint's {@link ObjectEndpoint#executeCall
     * executeCall} method is invoked with the
     * <code>OutboundRequest</code>.  If <code>executeCall</code>
     * returns a <code>RemoteException</code>, then this method throws
     * that exception (and thus the remote call attempt iteration
     * terminates).  If <code>executeCall</code> returns
     * <code>null</code>, then the unmarshalling of the call response
     * proceeds as follows:
     *
     * <p>A byte is read from the response input stream of the
     * <code>OutboundRequest</code>:
     *
     * <ul>
     *
     * <li>If the byte is <code>0x00</code>, indicating a marshalling
     * protocol version mismatch, a {@link ProtocolException} is
     * generated and, as described below, wrapped and handled like any
     * other <code>Exception</code> thrown from a remote call attempt.
     *
     * <li>If the byte is <code>0x01</code>, indicating a normal
     * return, the {@link #createMarshalInputStream
     * createMarshalInputStream} method is invoked, passing
     * <code>proxy</code>, <code>method</code>, the
     * <code>OutboundRequest</code>, a <code>boolean</code> indicating
     * whether or not object integrity is being enforced, and the
     * client context, to create the marshal input stream for
     * unmarshalling the response, and the {@link #unmarshalReturn
     * unmarshalReturn} method of this invocation handler is invoked
     * with <code>proxy</code>, <code>method</code>, the marshal input
     * stream, and the client context.  This method returns the value
     * returned by <code>unmarshalReturn</code> (and thus the remote
     * call attempt iteration terminates).
     *
     * <li>If the byte is <code>0x02</code>, indicating an exceptional
     * return, a marshal input stream is created by calling the
     * <code>createMarshalInputStream</code> method as described for
     * the previous case, and the {@link #unmarshalThrow
     * unmarshalThrow} method of this invocation handler is invoked
     * with <code>proxy</code>, <code>method</code>, the marshal input
     * stream, and the client context.  This method throws the
     * exception returned by <code>unmarshalThrow</code> (and thus the
     * remote call attempt iteration terminates).
     *
     * <li>If the byte is any other value, a
     * <code>ProtocolException</code> is generated and, as described
     * below, wrapped and handled like any other
     * <code>Exception</code> thrown from a remote call attempt.
     *
     * </ul>
     *
     * <p>If an <code>IOException</code> is thrown during the attempt
     * to communicate the remote call, then it is wrapped in a
     * <code>RemoteException</code> as follows:
     *
     * <ul>
     *
     * <li>If <code>marshalMethod</code> was not invoked for this
     * attempt, or if an invocation of {@link
     * OutboundRequest#getDeliveryStatus getDeliveryStatus} on the
     * <code>OutboundRequest</code> returns <code>false</code>, or if
     * a marshalling protocol version mismatch was detected, then
     *
     * <ul>
     *
     * <li>if the <code>IOException</code> is a {@link
     * java.net.UnknownHostException java.net.UnknownHostException},
     * it is wrapped in a {@link java.rmi.UnknownHostException
     * java.rmi.UnknownHostException};
     *
     * <li>if it is a {@link java.net.ConnectException
     * java.net.ConnectException}, it is wrapped in a {@link
     * java.rmi.ConnectException java.rmi.ConnectException};
     *
     * <li>if it is any other <code>IOException</code>, it is wrapped
     * in a {@link ConnectIOException}.
     *
     * </ul>
     *
     * <li>Otherwise, if <code>executeCall</code> was not invoked for
     * this attempt, the <code>IOException</code> is wrapped in a
     * {@link MarshalException}, and if <code>executeCall</code> was
     * invoked, it is wrapped in an {@link UnmarshalException}.
     *
     * </ul>
     *
     * <p>If a {@link ClassNotFoundException} is thrown
     * during the unmarshalling, then it is wrapped in an {@link
     * UnmarshalException}.
     *
     * <p>In all cases, either the request output stream and the
     * response input stream will be closed or the
     * <code>OutboundRequest</code> will be aborted before this
     * attempt completes.
     *
     * </blockquote>
     *
     * <p>If an attempt to communicate the remote call throws an
     * <code>Exception</code> (other than an exception returned by
     * <code>executeCall</code> or <code>unmarshalThrow</code>, which
     * terminates the remote call attempt iteration), then if
     * <code>marshalMethod</code> was not invoked for the attempt or
     * if an invocation of <code>getDeliveryStatus</code> on the
     * <code>OutboundRequest</code> returns <code>false</code>, then
     * if the iterator's <code>hasNext</code> method returns
     * <code>true</code>, then another attempt is made.  Otherwise,
     * this method throws the <code>Exception</code> thrown by the
     * last attempt (possibly wrapped as described above).
     *
     * <p>Note that invoking a remote method on a remote object via this
     * invoke method preserves "at-most-once" call semantics.  At-most-once
     * call semantics guarantees that the remote call will either a) not
     * execute, b) partially execute, or c) execute exactly once at the remote
     * site.  With Java RMI's at-most-once call semantics, arguments may be
     * marshalled more than once for a given remote call.
     *
     * <p>A subclass can override this method to handle the methods of
     * any additional non-remote interfaces implemented by the proxy
     * or to otherwise control invocation handling behavior.
     *
     * <p>The semantics of this method are unspecified if the
     * arguments could not have been produced by an instance of some
     * valid dynamic proxy class containing this invocation handler.
     * This method throws {@link IllegalArgumentException} if
     * <code>proxy</code> is an instance of
     * <code>InvocationHandler</code> or, if a remote call is to be
     * made, any of the superinterfaces of <code>proxy</code>'s class
     * have a method with the same name and parameter types as
     * <code>method</code> but that does not declare
     * <code>RemoteException</code> or a superclass of
     * <code>RemoteException</code> in its <code>throws</code> clause
     * (even if such a method is not a member of any of the direct
     * superinterfaces of <code>proxy</code>'s class because of
     * overriding).
     *
     * @throws	Throwable {@inheritDoc}
     *
     * @see	java.lang.reflect.UndeclaredThrowableException
     **/
    public Object invoke(Object proxy, Method method, Object[] args)
	throws Throwable
    {
	if (proxy instanceof InvocationHandler) {
	    throw new IllegalArgumentException(
				    "proxy cannot be an invocation handler");
	} else if (method.getDeclaringClass() == Object.class) {
	    return invokeObjectMethod(proxy, method, args);
	} else if (method.getDeclaringClass() == RemoteMethodControl.class) {
	    /*
	     * REMIND: This optimization (testing the identity of the
	     * method's declaring class) fails if the proxy's class
	     * implements, instead of RemoteMethodControl directly, a
	     * subinterface of RemoteMethodControl that overrides the
	     * setConstraints method.  This problem could be fixed by
	     * using a more expensive Class.isAssignableFrom check,
	     * but even that would fail if the proxy class implements,
	     * prior to a subinterface of RemoteMethodControl, another
	     * interface that also declares a method with the same
	     * signature as RemoteMethodControl.setConstraints (this
	     * case does seem less unlikely).  It seems that to cover
	     * all cases, we should we testing the signature (name and
	     * parameter types) of the method directly.
	     */
	    return invokeRemoteMethodControlMethod(proxy, method, args);
	} else if (method.getDeclaringClass() == TrustEquivalence.class) {
	    /*
	     * REMIND: Ditto.
	     */
	    return invokeTrustEquivalenceMethod(proxy, method, args);
	} else {
	    return invokeRemoteMethod(proxy, method, args);
	}
    }

    /**
     * Handles java.lang.Object methods.
     **/
    private Object invokeObjectMethod(Object proxy,
				      Method method,
				      Object[] args)
    {
	String name = method.getName();

	if (name.equals("hashCode")) {
	    return Integer.valueOf(hashCode());

	} else if (name.equals("equals")) {
	    Object obj = args[0];
	    boolean b =
		proxy == obj ||
		(obj != null &&
		 Util.sameProxyClass(proxy, obj) &&
		 equals(Proxy.getInvocationHandler(obj)));
	    return Boolean.valueOf(b);

	} else if (name.equals("toString")) {
	    return proxyToString(proxy);

	} else {
	    throw new IllegalArgumentException(
		"unexpected Object method: " + method);
	}
    }

    /**
     * Handles RemoteMethodControl methods.
     **/
    private Object invokeRemoteMethodControlMethod(Object proxy,
						   Method method,
						   Object[] args)
    {
	String name = method.getName();

	if (name.equals("setConstraints")) {
	    if (Proxy.getInvocationHandler(proxy) != this) {
		throw new IllegalArgumentException("not proxy for this");
	    }
	    Class proxyClass = proxy.getClass();
	    return Proxy.newProxyInstance(
		getProxyLoader(proxyClass),
		proxyClass.getInterfaces(),
		setClientConstraints((MethodConstraints) args[0]));
	    // IllegalArgumentException means proxy argument was bogus

	} else if (name.equals("getConstraints")) {
	    return clientConstraints;

	} else {
	    throw new AssertionError(method);
	}
    }

    /**
     * Handles TrustEquivalence methods.
     **/
    private Object invokeTrustEquivalenceMethod(Object proxy,
						Method method,
						Object[] args)
    {
	String name = method.getName();
	if (name.equals("checkTrustEquivalence")) {
	    Object obj = args[0];
	    boolean b =
		proxy == obj ||
		(obj != null &&
		 Util.sameProxyClass(proxy, obj) &&
		 checkTrustEquivalence(Proxy.getInvocationHandler(obj)));
	    return Boolean.valueOf(b);

	} else {
	    throw new AssertionError(method);
	}
    }

    /**
     * Holds information about the communication failure of a remote
     * call attempt.
     **/
    private static class Failure {

	/** exception representing the communication failure */
	final Throwable exception;

	/** failure is safe to retry (and possibly worth retrying) after */
	final boolean retry;

	Failure(Throwable exception, boolean retry) {
	    this.exception = exception;
	    this.retry = retry;
	}
    }

    /**
     * Handles remote methods.
     **/
    private Object invokeRemoteMethod(Object proxy,
				      Method method,
				      Object[] args)
	throws Throwable
    {
	Util.checkProxyRemoteMethod(proxy.getClass(), method);
	InvocationConstraints constraints = getConstraints(method);

	if (logger.isLoggable(Level.FINE)) {
	    logCall(method, args, constraints);
	}

	OutboundRequestIterator iter = oe.newCall(constraints);
            if (!iter.hasNext()) {
                throw new ConnectIOException("iterator produced no requests",
                    new IOException("iterator produced no requests"));
            }

	Failure failure = null;
	do {
	    if (logger.isLoggable(Levels.HANDLED)) {
		if (failure != null) {
		    logThrow(Levels.HANDLED, method, failure.exception, false);
		}
	    }

	    Object result = invokeRemoteMethodOnce(proxy, method, args,
						   iter, constraints);
	    if (result instanceof Failure) {
		failure = (Failure) result;
	    } else {
		return result;
	    }
	} while (failure.retry && iter.hasNext());

	/*
	 * If all attempts failed with communication failures, then
	 * throw the exception representing the last communication
	 * failure.
	 */
	if (logger.isLoggable(Levels.FAILED)) {
	    if (failure != null) {
		logThrow(Levels.FAILED, method, failure.exception, false);
	    }
	}
	throw failure.exception;
    }

    /**
     * Make one attempt to invoke a remote method.
     *
     * If this method returns an object that is not a Failure
     * instance, then the overall remote invocation should return that
     * object.  If this method throws an exception, then the overall
     * remote invocation should throw that exception (no further
     * retry).
     *
     * If this method returns a Failure instance, then this attempt to
     * invoke the remote method failed due a communication failure.
     * If the retry flag of the Failure instance is true, then the
     * failure is safe to retry (in light of "at most once" execution
     * semantics) and possibly worth retrying.
     **/
    private Object invokeRemoteMethodOnce(Object proxy,
					  Method method,
					  Object[] args,
					  OutboundRequestIterator iter,
					  InvocationConstraints constraints)
	throws Throwable
    {
	/*
	 * Initiate remote call request.
	 */
	OutboundRequest request;
	try {
	    request = iter.next();
	} catch (Exception e) {
	    if (e instanceof IOException) {
		e = wrapSafeIOException((IOException) e, oe);
	    }
	    return new Failure(e, true);
	}

	/*
	 * Marshal method and arguments.
	 */
	boolean ok = false;
	boolean integrity = false;
	boolean wroteMethod = false;
	Collection context;
	try {
	    /*
	     * Check the unfulfilled constraints.  If the unfulfilled
	     * requirements include Integrity.YES, then we must verify
	     * codebase integrity at this level.  If there are any
	     * non-Integrity unfulfilled requirements, we cannot
	     * satisfy them, so this request attempt must fail.
	     */
	    InvocationConstraints unfulfilled =
		request.getUnfulfilledConstraints();
	    for (Iterator i = unfulfilled.requirements().iterator();
		 i.hasNext();)
	    {
		InvocationConstraint c = (InvocationConstraint) i.next();
		if (c == Integrity.YES) {
		    integrity = true;
		} else if (!(c instanceof Integrity)) {
		    throw new UnsupportedConstraintException(
			"cannot satisfy unfulfilled constraint: " + c);
		}
		// REMIND: support ConstraintAlternatives containing Integrity?
	    }

	    /*
	     * Even if Integrity.YES wasn't a requirement, we will
	     * satisfy a preference for it.
	     */
	    if (!integrity) {
		for (Iterator i = unfulfilled.preferences().iterator();
		     i.hasNext();)
		{
		    InvocationConstraint c = (InvocationConstraint) i.next();
		    if (c == Integrity.YES) {
			integrity = true;
			break;	// no need to examine preferences further
		    }
		    // NYI: support ConstraintAlternatives containing Integrity
		}
	    }

	    OutputStream ros = request.getRequestOutputStream();

	    ros.write(0x00);			// marshalling protocol version
	    ros.write(integrity ? 0x01 : 0x00);	// integrity

	    context = new ArrayList(1);
	    Util.populateContext(context, integrity);

	    ObjectOutputStream out =
		createMarshalOutputStream(proxy, method, request, context);

	    wroteMethod = true;
	    marshalMethod(proxy, method, out, context);

	    args = (args == null) ? new Object[0] : args;
	    marshalArguments(proxy, method, args, out, context);

	    out.close();
	    ok = true;
	} catch (Exception e) {
	    if (e instanceof IOException) {
		if (wroteMethod && request.getDeliveryStatus()) {
		    e = new MarshalException("error marshalling arguments", e);
		} else {
		    e = wrapSafeIOException((IOException) e, oe);
		}
	    }
	    return new Failure(e,
			       !wroteMethod || !request.getDeliveryStatus());
	} finally {
	    if (!ok) {
		request.abort();
	    }
	}

	/*
	 * Execute call and unmarshal return value or exception.
	 */
	ok = false;
	boolean versionMismatch = false;
	Object returnValue = null;
	Throwable throwable = null;
	try {
	    throwable = oe.executeCall(request);
	    if (throwable == null) {
		InputStream ris = request.getResponseInputStream();
		
		int responseCode = ris.read();
		if (responseCode == -1) {
		    throw new EOFException("connection closed by server");
		} else if (responseCode == 0x00) {
		    versionMismatch = true;
		    throw new ProtocolException(
			"marshalling protocol version mismatch");
		}
		
		ObjectInputStream in =
		    createMarshalInputStream(proxy, method, request,
					     integrity, context);
		
		switch (responseCode) {
		case 0x01:
		    returnValue = unmarshalReturn(proxy, method, in, context);
		    if (logger.isLoggable(Level.FINE)) {
			logReturn(method, returnValue);
		    }
		    break;

		case 0x02:
		    throwable = unmarshalThrow(proxy, method, in, context);
		    break;

		default:
		    throw new ProtocolException(
			"invalid response code " + responseCode);
		}

		in.close();
	    }
	    ok = true;
	} catch (Exception e) {
	    boolean retry = false;
	    if (e instanceof IOException) {
		if (!versionMismatch && request.getDeliveryStatus()) {
		    e = new UnmarshalException(
			 "exception unmarshalling response", e);
		} else {
		    e = wrapSafeIOException((IOException) e, oe);
		    retry = !versionMismatch;
		}
	    } else if (e instanceof ClassNotFoundException) {
		e = new UnmarshalException("error unmarshalling response", e);
	    }
	    return new Failure(e,
		!versionMismatch || !request.getDeliveryStatus());
	} finally {
	    if (!ok) {
		request.abort();
	    }
	}

	/*
	 * Throw exception or return the return value.
	 */
	if (throwable != null) {
	    if (logger.isLoggable(Levels.FAILED)) {
		logThrow(Levels.FAILED, method, throwable, true);
	    }
	    throw throwable;
	} else {
	    return returnValue;
	}
    }

    /**
     * Wraps an IOException that occurred while attempting to
     * communicate a remote call in a RemoteException that will
     * indicate that the failed remote invocation is safe to retry
     * without violating "at most once" execution semantics.
     **/
    private static RemoteException wrapSafeIOException(IOException ioe,
						       ObjectEndpoint oe)
    {
	if (ioe instanceof java.net.UnknownHostException) {
	    return new java.rmi.UnknownHostException(
		"unknown host in " + oe, ioe);
	} else if (ioe instanceof java.net.ConnectException) {
	    return new java.rmi.ConnectException(
		"connection refused or timed out to " + oe, ioe);
	} else {
	    return new ConnectIOException(
		"I/O exception connecting to " + oe, ioe);
	}
    }

    /**
     * Returns the combined absolute client and server constraints for
     * the specified method, getting the constraints from the cache if
     * possible, and creating and updating the cache if necessary.
     **/
    private InvocationConstraints getConstraints(Method method) {
	if (clientConstraints == null && serverConstraints == null) {
	    return InvocationConstraints.EMPTY;
	}

	synchronized (cacheLock) {
	    if (methodCache == null) {
		methodCache = new Method[CACHE_SIZE];
		constraintCache = new InvocationConstraints[CACHE_SIZE];
		cacheIndex = CACHE_SIZE - 1;
	    } else {
		for (int i = CACHE_SIZE; --i >= 0; ) {
		    if (methodCache[i] == method) {
			return constraintCache[i].makeAbsolute();
		    }
		}
	    }

	    InvocationConstraints constraints = InvocationConstraints.combine(
		clientConstraints == null ?
		    null : clientConstraints.getConstraints(method),
		serverConstraints == null ?
		    null : serverConstraints.getConstraints(method));

	    methodCache[cacheIndex] = method;
	    constraintCache[cacheIndex] = constraints;
	    cacheIndex = (cacheIndex == 0) ? CACHE_SIZE - 1 : cacheIndex - 1;
	    return constraints.makeAbsolute();
	}
    }

    /**
     * Returns a copy of this invocation handler with the specified
     * constraints as its new client constraints.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * as follows:
     *
     * <p>This method looks for a public constructor declared by the
     * class of this object with two parameters, the first parameter
     * type being the class of this object and the second parameter
     * type being {@link MethodConstraints}.  If found, the
     * constructor is invoked with this instance and the specified
     * constraints, and the resulting object is returned.  If the
     * constructor could not be found or was not accessible, or if the
     * constructor invocation throws an exception, an
     * <code>UndeclaredThrowableException</code> is thrown.
     *
     * <p>A subclass can override this method to control how the
     * invocation handler is copied.
     *
     * @param	constraints the new client constraints, or
     *		<code>null</code>
     *
     * @return	a copy of this invocation handler with the specified
     *		constraints as its client constraints
     **/
    protected InvocationHandler setClientConstraints(
	MethodConstraints constraints)
    {
	Class c = getClass();
	try {
	    Constructor constructor =
		c.getConstructor(new Class[] { c, MethodConstraints.class });
	    return (BasicInvocationHandler)
		constructor.newInstance(new Object[] { this, constraints });
	} catch (RuntimeException e) {
	    throw e;
	} catch (Exception e) {
	    throw new UndeclaredThrowableException(
		e, "exception constructing invocation handler");
	}
    }

    /**
     * Returns a new {@link ObjectOutputStream} instance to use to write
     * objects to the request output stream obtained by invoking the {@link
     * OutboundRequest#getRequestOutputStream getRequestOutputStream} method
     * on the given <code>request</code>.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * to return a new {@link MarshalOutputStream} instance
     * constructed with the output stream obtained from
     * <code>request</code> as specified above and an unmodifiable
     * view of the supplied <code>context</code> collection.
     *
     * <p>A subclass can override this method to control how the
     * marshal input stream is created or implemented.
     *
     * @param	proxy the proxy instance
     * @param	method the remote method invoked
     * @param	request the outbound request
     * @param	context the client context
     * @return	a new {@link ObjectOutputStream} instance for marshalling
     *		a call request
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    protected ObjectOutputStream
        createMarshalOutputStream(Object proxy,
				  Method method,
				  OutboundRequest request,
				  Collection context)
	throws IOException
    {
	if (proxy == null || method == null) {
	    throw new NullPointerException();
	}
	OutputStream out = request.getRequestOutputStream();
	Collection unmodContext = Collections.unmodifiableCollection(context);
	return new MarshalOutputStream(out, unmodContext);
    }
							  
    /**
     * Returns a new {@link ObjectInputStream} instance to use to read
     * objects from the response input stream obtained by invoking the {@link
     * OutboundRequest#getResponseInputStream getResponseInputStream} method
     * on the given <code>request</code>.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * to return a new {@link MarshalInputStream} instance constructed
     * with the input stream obtained from <code>request</code> as
     * specified above for the input stream <code>in</code>, the class
     * loader of <code>proxy</code>'s class for
     * <code>defaultLoader</code> and <code>verifierLoader</code>,
     * this method's <code>integrity</code> argument for
     * <code>verifyCodebaseIntegrity</code>, and an unmodifiable view
     * of <code>context</code> for the <code>context</code>
     * collection.  The {@link
     * MarshalInputStream#useCodebaseAnnotations
     * useCodebaseAnnotations} method is invoked on the created stream
     * before it is returned.
     *
     * <p>An exception is thrown if <code>proxy</code> is not an instance
     * of a dynamic proxy class containing this invocation handler.
     *
     * <p>A subclass can override this method to control how the
     * marshal input stream is created or implemented.
     *
     * @param	proxy the proxy instance
     * @param	method the remote method invoked
     * @param	request the outbound request
     * @param	integrity whether or not to verify codebase integrity
     * @param	context the client context
     * @return	a new {@link ObjectInputStream} instance for unmarshalling
     *		a call response
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    protected ObjectInputStream
        createMarshalInputStream(Object proxy,
				 Method method,
				 OutboundRequest request,
				 boolean integrity,
				 Collection context)
	throws IOException
    {
	if (method == null) {
	    throw new NullPointerException();
	}
	if (Proxy.getInvocationHandler(proxy) != this) {
	    throw new IllegalArgumentException("not proxy for this");
	}
	ClassLoader proxyLoader = getProxyLoader(proxy.getClass());
	Collection unmodContext = Collections.unmodifiableCollection(context);
	MarshalInputStream in =
	    new MarshalInputStream(request.getResponseInputStream(),
				   proxyLoader, integrity, proxyLoader,
				   unmodContext);
	in.useCodebaseAnnotations();
	return in;
    }

    /**
     * Returns the class loader for the specified proxy class.
     */
    private static ClassLoader getProxyLoader(final Class proxyClass) {
	return (ClassLoader)
	    AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    return proxyClass.getClassLoader();
		}
	    });
    }

    /**
     * Marshals a representation of the given <code>method</code> to
     * the outgoing request stream, <code>out</code>.  For each remote
     * call, the <code>invoke</code> method calls this method to
     * marshal a representation of the method.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * to write the JRMP method hash (defined in section 8.3 of the
     * Java RMI specification) for the given method to the output stream
     * using the {@link ObjectOutputStream#writeLong writeLong}
     * method.
     *
     * <p>A subclass can override this method to control how the remote
     * method is marshalled.
     *
     * @param	proxy the proxy instance that the method was invoked on
     * @param   method the <code>Method</code> instance corresponding
     *          to the interface method invoked on the proxy
     *          instance.  The declaring class of the
     *          <code>Method</code> object will be the interface that
     *          the method was declared in, which may be a
     *          superinterface of the proxy interface that the proxy
     *          class inherits the method through.
     * @param	out outgoing request stream for the remote call
     * @param	context the client context
     *
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    protected void marshalMethod(Object proxy,
				 Method method,
				 ObjectOutputStream out,
				 Collection context)
	throws IOException
    {
	if (proxy == null || method == null || context == null) {
	    throw new NullPointerException();
	}
        out.writeLong(Util.getMethodHash(method));
    }

    /**
     * Marshals the arguments for the specified remote method to the outgoing
     * request stream, <code>out</code>.  For each remote call, the
     * <code>invoke</code> method calls this method to marshal arguments.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * marshal each argument as follows:
     *
     * <p>If the corresponding declared parameter type is primitive, then the 
     * the primitive value is written to the stream (for example, if the type
     * is <code>int.class</code>, then the primitive <code>int</code> value
     * is written to the stream using the <code>writeInt</code> method).
     * Otherwise, the argument is written to the stream using the
     * <code>writeObject</code> method.
     *
     * <p>A subclass can override this method to marshal the arguments
     * in an alternative context, perform pre- or post-processing on
     * the arguments, marshal additional implicit data, or otherwise
     * control how the arguments are marshalled.
     *
     * @param	proxy the proxy instance that the method was invoked on
     * @param   method the <code>Method</code> instance corresponding
     *          to the interface method invoked on the proxy
     *          instance.  The declaring class of the
     *          <code>Method</code> object will be the interface that
     *          the method was declared in, which may be a
     *          superinterface of the proxy interface that the proxy
     *          class inherits the method through.
     * @param   args an array of objects containing the values of the
     *          arguments passed in the method invocation on the
     *          proxy instance.  If an argument's corresponding declared
     *          parameter type is primitive, then its value is
     *          represented with an instance of the corresponding primitive
     *          wrapper class, such as <code>java.lang.Integer</code> or 
     *          <code>java.lang.Boolean</code>.
     * @param	out outgoing request stream for the remote call
     * @param	context the client context
     *
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    protected void marshalArguments(Object proxy,
				    Method method,
				    Object[] args,
				    ObjectOutputStream out,
				    Collection context)
	throws IOException
    {
	if (proxy == null || args == null || out == null || context == null) {
	    throw new NullPointerException();
	}
	Class[] types = method.getParameterTypes();
	for (int i = 0; i < types.length; i++) {	
	    Util.marshalValue(types[i], args[i], out);
	}
    }

    /**
     * Unmarshals the return value for the specified remote method from the
     * incoming response stream, <code>in</code>.  In the case that a value is
     * returned from the invocation on the remote object, this method is
     * called to unmarshal that return value.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * as follows:
     *
     * <p>If the return type of the method is void, then no return value is
     * read from the stream and <code>null</code> is returned.  If the return
     * type is a primitive type, then the primitive value is read from the
     * stream (for example, if the type is <code>int.class</code>, then the
     * primitive <code>int</code> value is read from the stream using the
     * <code>readInt</code> method).  Otherwise, the return value is read from
     * the stream using the <code>readObject</code> method.
     *
     * <p>A subclass can override this method to unmarshal the return
     * value in an alternative context, perform post-processing on the
     * return value, unmarshal additional implicit data, or otherwise
     * control how the return value is unmarshalled.
     *
     * @param	proxy the proxy instance that the method was invoked on
     * @param   method the <code>Method</code> instance corresponding
     *          to the interface method invoked on the proxy
     *          instance.  The declaring class of the
     *          <code>Method</code> object will be the interface that
     *          the method was declared in, which may be a
     *          superinterface of the proxy interface that the proxy
     *          class inherits the method through.
     * @param	in the incoming result stream for the remote call
     * @param	context the client context
     * @return  the unmarshalled return value of the method invocation on 
     *		the proxy instance.  If the declared return value of the
     *          interface method is a primitive type, then the
     *          value returned by <code>invoke</code> will be an
     *          instance of the corresponding primitive wrapper
     *          class; otherwise, it will be a type assignable to
     *		the declared return type.
     * @throws	IOException if an I/O exception occurs
     * @throws  ClassNotFoundException if a class could not be found during
     *          unmarshalling
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    protected Object unmarshalReturn(Object proxy,
				     Method method,
				     ObjectInputStream in,
				     Collection context)
	throws IOException, ClassNotFoundException
    {
	if (proxy == null || in == null || context == null) {
	    throw new NullPointerException();
	}
	Class returnType = method.getReturnType();
	Object returnValue = null;
	if (returnType != void.class) {
	    returnValue = Util.unmarshalValue(returnType, in);
	}
	return returnValue;
    }

    /**
     * Unmarshals the throwable for the specified remote method from the
     * incoming response stream, <code>in</code>, and returns the result.  In
     * the case that an exception was thrown as a result of the
     * invocation on the remote object, this method is called to unmarshal
     * that throwable.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * to return the throwable unmarshalled from the stream using the
     * <code>readObject</code> method.  If the unmarshalled throwable
     * is a checked exception that is not assignable to any exception
     * in the <code>throws</code> clause of the method implemented by the
     * <code>proxy</code>'s class, then: if there is no public member
     * method of <code>proxy</code>'s class with the same name and
     * parameter types as <code>method</code> an {@link
     * IllegalArgumentException} is thrown, otherwise that exception
     * is wrapped in an {@link UnexpectedException} and the wrapped
     * exception is returned.
     *
     * <p>A subclass can override this method to unmarshal the return
     * value in an alternative context, perform post-processing on the
     * return value, unmarshal additional implicit data, or otherwise
     * control how the return value is unmarshalled.
     *
     * @param	proxy the proxy instance that the method was invoked on
     * @param   method the <code>Method</code> instance corresponding
     *          to the interface method invoked on the proxy
     *          instance.  The declaring class of the
     *          <code>Method</code> object will be the interface that
     *          the method was declared in, which may be a
     *          superinterface of the proxy interface that the proxy
     *          class inherits the method through.
     * @param	context the client context
     * @param	in the incoming result stream for the remote call
     * @return	the unmarshalled exception to throw from the method
     *		invocation on the proxy instance.  The exception's type
     *		must be assignable either to any of the exception types
     *		declared in the <code>throws</code> clause of the interface
     *		method or to the unchecked exception types
     *		<code>java.lang.RuntimeException</code> or
     *		<code>java.lang.Error</code>.
     * @throws	IOException if an I/O exception occurs
     * @throws  ClassNotFoundException if a class could not be found during
     *          unmarshalling
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    protected Throwable unmarshalThrow(Object proxy,
				       Method method,
				       ObjectInputStream in,
				       Collection context)
	throws IOException, ClassNotFoundException
    {
	if (proxy == null || method == null || context == null) {
	    throw new NullPointerException();
	}
	Throwable t = (Throwable) in.readObject();
	Util.exceptionReceivedFromServer(t);
	if (!(t instanceof RuntimeException || t instanceof Error)) {
	    Class cl = proxy.getClass();
	    try {
		method = cl.getMethod(method.getName(),
				      method.getParameterTypes());
	    } catch (NoSuchMethodException e) {
		throw (IllegalArgumentException)
		    new IllegalArgumentException().initCause(e);
	    }
	    Class[] exTypes = method.getExceptionTypes();
	    Class thrownType = t.getClass();
	    for (int i = 0; i < exTypes.length; i++) {
		if (exTypes[i].isAssignableFrom(thrownType)) {
		    return t;
		}
	    }
	    UnexpectedException wrapper =
		new UnexpectedException("unexpected exception");
	    wrapper.detail = t;
	    t = wrapper;
	}
	return t;
    }
    
    /**
     * Log the start of an outbound remote call.
     **/
    private void logCall(Method method,
			 Object[] args,
			 InvocationConstraints constraints)
    {
	String msg = "outbound call {0}.{1} to {2}\n{3}";
	if (logger.isLoggable(Level.FINEST)) {
	    msg = "outbound call {0}.{1} to {2}\nargs {4}\n{3}";
	}
	Object xargs = (args == null) ?
	    Collections.EMPTY_LIST : Arrays.asList(args);
	logger.logp(Level.FINE, this.getClass().getName(), "invoke", msg,
		    new Object[] { method.getDeclaringClass().getName(),
				   method.getName(), oe, constraints, xargs});
    }

    /**
     * Log the return of an outbound remote call.
     **/
    private void logReturn(Method method, Object res) {
	String msg = "outbound call {0}.{1} returns";
	if (logger.isLoggable(Level.FINEST) &&
	    method.getReturnType() != void.class)
	{
	    msg = "outbound call {0}.{1} returns {2}";
	}
	logger.logp(Level.FINE, this.getClass().getName(), "invoke", msg,
		    new Object[]{method.getDeclaringClass().getName(),
				 method.getName(), res});
    }

    /**
     * Log the throw of an outbound remote call.
     **/
    private void logThrow(Level level,
			  Method method,
			  Throwable t,
			  boolean isRemote)
    {
	LogRecord lr = new LogRecord(level,
				     isRemote ?
				     "outbound call {0}.{1} remotely throws" :
				     "outbound call {0}.{1} locally throws");
	lr.setLoggerName(logger.getName());
	lr.setSourceClassName(this.getClass().getName());
	lr.setSourceMethodName("invoke");
	lr.setParameters(new Object[]{method.getDeclaringClass().getName(),
				      method.getName()});
	lr.setThrown(t);
	logger.log(lr);
    }

    /**
     * Returns this <code>BasicInvocationHandler</code>'s
     * <code>ObjectEndpoint</code>.
     *
     * @return the <code>ObjectEndpoint</code>
     **/
    public final ObjectEndpoint getObjectEndpoint() {
	return oe;
    }

    /**
     * Returns this <code>BasicInvocationHandler</code>'s client
     * constraints.
     *
     * @return the client constraints
     **/
    public final MethodConstraints getClientConstraints() {
	return clientConstraints;
    }

    /**
     * Returns this <code>BasicInvocationHandler</code>'s server
     * constraints.
     *
     * @return the server constraints
     **/
    public final MethodConstraints getServerConstraints() {
	return serverConstraints;
    }

    /**
     * Returns the hash code value for this invocation handler.
     *
     * @return the hash code value for this invocation handler
     **/
    public int hashCode() {
	return oe.hashCode();
    }

    /**
     * Compares the specified object with this
     * <code>BasicInvocationHandler</code> for equality.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * to return <code>true</code> if and only if
     *
     * <ul>
     *
     * <li>the specified object has the same class as this object,
     *
     * <li>the <code>ObjectEndpoint</code> in the specified object has the
     * same class and is equal to the object endpoint in this object, and
     *
     * <li>the client constraints and server constraints in the specified
     * object are equal to the ones in this object.
     *
     * </ul>
     *
     * <p>A subclass should override this method if adds instance
     * state that affects equality.
     *
     * @param	obj the object to compare with
     *
     * @return	<code>true</code> if <code>obj</code> is equivalent to
     *		this object; <code>false</code> otherwise
     **/
    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	} else if (obj == null || getClass() != obj.getClass()) {
	    return false;
	}
	BasicInvocationHandler other = (BasicInvocationHandler) obj;
	return
	    Util.sameClassAndEquals(oe, other.oe) &&
	    Util.equals(clientConstraints, other.clientConstraints) &&
	    Util.equals(serverConstraints, other.serverConstraints);
    }

    /**
     * Returns <code>true</code> if the specified object (which is not
     * yet known to be trusted) is equivalent in trust, content, and
     * function to this known trusted object, and <code>false</code>
     * otherwise.
     *
     * <p><code>BasicInvocationHandler</code> implements this method
     * to return <code>true</code> if and only if
     *
     * <ul>
     *
     * <li>the specified object has the same class as this object,
     *
     * <li>this object's <code>ObjectEndpoint</code> is an instance of
     * {@link TrustEquivalence} and invoking its
     * <code>checkTrustEquivalence</code> method with the specified
     * object's <code>ObjectEndpoint</code> returns <code>true</code>,
     * and
     *
     * <li>the client constraints and server constraints in the
     * specified object are equal to the ones in this object.
     *
     * </ul>
     *
     * <p>A subclass should override this method to perform any
     * additional checks that are necessary.
     **/
    public boolean checkTrustEquivalence(Object obj) {
	if (obj == this) {
	    return true;
	} else if (obj == null || getClass() != obj.getClass()) {
	    return false;
	}
	BasicInvocationHandler other = (BasicInvocationHandler) obj;
	return
	    Util.checkTrustEquivalence(oe, other.oe) &&
	    Util.equals(clientConstraints, other.clientConstraints) &&
	    Util.equals(serverConstraints, other.serverConstraints);
    }

    /**
     * Returns a string representation of this
     * <code>BasicInvocationHandler</code>.
     *
     * @return a string representation of this
     * <code>BasicInvocationHandler</code>
     **/
    public String toString() {
	return Util.getUnqualifiedName(getClass()) + "[" + oe + "]";
    }

    /**
     * Returns a string representation for a proxy that uses this invocation
     * handler.
     **/
    private String proxyToString(Object proxy) {
	Class[] interfaces = proxy.getClass().getInterfaces();
	if (interfaces.length == 0) {
	    return "Proxy[" + this + "]";
	}
	String iface = interfaces[0].getName();
	int dot = iface.lastIndexOf('.');
	if (dot >= 0) {
	    iface = iface.substring(dot + 1);
	}
	return "Proxy[" + iface + "," + this + "]";
    }

    /**
     * @throws	InvalidObjectException if the object endpoint is
     * <code>null</code>
     **/
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (oe == null) {
	    throw new InvalidObjectException("null object endpoint");
	}
	cacheLock = new Object();
    }

    /**
     * @throws	InvalidObjectException unconditionally
     **/
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException("no data in stream; class: " +
					 this.getClass().getName());
    }
}
