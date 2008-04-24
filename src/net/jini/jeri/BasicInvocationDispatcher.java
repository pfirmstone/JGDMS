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

import com.sun.jini.action.GetBooleanAction;
import com.sun.jini.jeri.internal.runtime.Util;
import com.sun.jini.jeri.internal.runtime.WeakKey;
import com.sun.jini.logging.Levels;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.ServerError;
import java.rmi.ServerException;
import java.rmi.UnmarshalException;
import java.rmi.server.ExportException;
import java.rmi.server.ServerNotActiveException;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.export.ServerContext;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.ClientSubject;
import net.jini.security.AccessPermission;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

/**
 * A basic implementation of the {@link InvocationDispatcher} interface,
 * providing preinvocation access control for
 * remote objects exported using {@link BasicJeriExporter}.
 *
 * <p>This invocation dispatcher handles incoming remote method invocations
 * initiated by proxies using {@link BasicInvocationHandler}, and expects
 * that a dispatched request, encapsulated in the {@link InboundRequest}
 * object passed to the {@link #dispatch dispatch} method, was sent using
 * the protocol implemented by <code>BasicInvocationHandler</code>.
 *
 * <p>A basic permission-based preinvocation access control mechanism is
 * provided. A permission class can be specified when an invocation
 * dispatcher is constructed; instances of that class are constructed using
 * either a {@link Method} instance or a <code>String</code> representing
 * the remote method being invoked. The class can have a constructor with a
 * <code>Method</code> parameter to permit an arbitrary mapping to the
 * actual permission target name and actions; otherwise, the class must
 * have a constructor taking the fully qualified name of the remote method
 * as a <code>String</code>. For each incoming call on a remote object, the
 * client subject must be granted the associated permission for that remote
 * method.  (Access control for an individual remote method can effectively
 * be disabled by granting the associated permission to all protection
 * domains.) A simple subclass of {@link AccessPermission} is typically
 * used as the permission class.
 *
 * <p>Other access control mechanisms can be implemented by subclassing this
 * class and overriding the various protected methods.
 * 
 * <p>This class is designed to support dispatching remote calls to the
 * {@link ProxyTrust#getProxyVerifier ProxyTrust.getProxyVerifier} method
 * to the local {@link ServerProxyTrust#getProxyVerifier
 * ServerProxyTrust.getProxyVerifier} method of a remote object, to allow a
 * remote object to be exported in such a way that its proxy can be
 * directly trusted by clients as well as in such a way that its proxy can
 * be trusted by clients using {@link ProxyTrustVerifier}.
 *
 * @author	Sun Microsystems, Inc.
 * @see		BasicInvocationHandler
 * @since 2.0
 *
 * @com.sun.jini.impl
 *
 * This implementation uses the following system property:
 * <dl>
 * <dt><code>com.sun.jini.jeri.server.suppressStackTrace</code>
 * <dd>If <code>true</code>, removes server-side stack traces before
 * marshalling an exception thrown as a result of a remote call.  The
 * default value is <code>false</code>.
 * </dl>
 * 
 * <p>This implementation uses the {@link Logger} named
 * <code>net.jini.jeri.BasicInvocationDispatcher</code> to log
 * information at the following levels:
 *
 * <table summary="Describes what is logged by BasicInvocationDispatcher at
 *        various logging levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Levels#FAILED FAILED} <td> exception that caused a request
 * to be aborted 
 *
 * <tr> <td> {@link Levels#FAILED FAILED} <td> exceptional result of a
 * remote call 
 *
 * <tr> <td> {@link Level#FINE FINE} <td> incoming remote call
 * 
 * <tr> <td> {@link Level#FINE FINE} <td> successful return of remote call
 *
 * <tr> <td> {@link Level#FINEST FINEST} <td> more detailed information on
 * the above (for example, actual argument and return values)
 *
 * </table>
 **/
public class BasicInvocationDispatcher implements InvocationDispatcher {

    /** Marshal stream protocol version. */
    static final byte VERSION = 0x0;
    
    /** Marshal stream protocol version mismatch. */
    static final byte MISMATCH = 0x0;
    
    /** Normal return (with or without return value). */
    static final byte RETURN = 0x01;
    
    /** Exceptional return. */
    static final byte THROW = 0x02;

    /** The class loader used by createMarshalInputStream */
    private final ClassLoader loader;
    
    /** The server constraints. */
    private final MethodConstraints serverConstraints;
    
    /**
     * Constructor for the Permission class, that has either one String
     * or one Method parameter, or null.
     */
    private final Constructor permConstructor;
    
    /** True if permConstructor has a Method parameter. */
    private final boolean permUsesMethod;
    
    /** Map from Method to Permission. */
    private final Map permissions;
    
    /** Map from Long method hash to Method, for all remote methods. */
    private final Map methods;

    /** Map from WeakKey(Subject) to ProtectionDomain. */
    private static final Map domains = new HashMap();
    
    /** Reference queue for the weak keys in the domains map. */
    private static final ReferenceQueue queue = new ReferenceQueue();

    /** dispatch logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicInvocationDispatcher");

    /**
     * Flag to remove server-side stack traces before marshalling
     * exceptions thrown by remote invocations to this VM
     */
    private static final boolean suppressStackTraces =
	((Boolean) AccessController.doPrivileged(new GetBooleanAction(
	    "com.sun.jini.jeri.server.suppressStackTraces")))
	    .booleanValue();

    /** Empty codesource. */
    private static final CodeSource emptyCS =
	new CodeSource(null, (Certificate[]) null);
    
    /** ProtectionDomain containing the empty codesource. */
    private static final ProtectionDomain emptyPD =
	new ProtectionDomain(emptyCS, null, null, null);

    /** Cached getClassLoader permission */
    private static final Permission getClassLoaderPermission =
	new RuntimePermission("getClassLoader");
    
    /**
     * Creates an invocation dispatcher to receive incoming remote calls
     * for the specified methods, for a server and transport with the
     * specified capabilities, enforcing the specified constraints,
     * performing preinvocation access control using the specified
     * permission class (if any).  The specified class loader is used by
     * the {@link #createMarshalInputStream createMarshalInputStream}
     * method.
     *
     * <p>For each combination of constraints that might need to be
     * enforced (obtained by calling the {@link
     * MethodConstraints#possibleConstraints possibleConstraints} method on
     * the specified server constraints, or using an empty constraints
     * instance if the specified server constraints instance is
     * <code>null</code>), calling the {@link
     * ServerCapabilities#checkConstraints checkConstraints} method of the
     * specified capabilities object with those constraints must return
     * constraints containing at most an {@link Integrity} constraint as a
     * requirement, or an <code>ExportException</code> is thrown.
     *
     * @param	methods a collection of {@link Method} instances for the
     *		remote methods
     * @param	serverCapabilities the transport capabilities of the server
     * @param	serverConstraints the server constraints, or <code>null</code>
     * @param	permissionClass the permission class, or <code>null</code>
     * @param	loader the class loader, or <code>null</code>
     *
     * @throws	SecurityException if the permission class is not
     *		<code>null</code> and is in a named package and a
     *		security manager exists and invoking its
     *		<code>checkPackageAccess</code> method with the package
     *		name of the permission class throws a
     *		<code>SecurityException</code>
     * @throws	IllegalArgumentException if the permission class
     *		is abstract, is not <code>public</code>, is not a subclass
     *		of {@link Permission}, or does not have a public
     *		constructor that has either one <code>String</code>
     *		parameter or one {@link Method} parameter and has no
     *		declared exceptions, or if any element of
     *		<code>methods</code> is not a {@link Method} instance
     * @throws	NullPointerException if <code>methods</code> or
     *		<code>serverCapabilities</code> is <code>null</code>, or if
     *		<code>methods</code> contains a <code>null</code> element
     * @throws	ExportException if any of the possible server constraints
     * 		cannot be satisfied according to the specified server
     *		capabilities 
     **/
    public BasicInvocationDispatcher(Collection methods,
				     ServerCapabilities serverCapabilities,
				     MethodConstraints serverConstraints,
				     Class permissionClass,
				     ClassLoader loader)
	throws ExportException
    {
	if (serverCapabilities == null) {
	    throw new NullPointerException();
	}
	this.methods = new HashMap();
	this.loader = loader;
	for (Iterator iter = methods.iterator(); iter.hasNext(); ) {
	    Object m = iter.next();
	    if (m == null) {
		throw new NullPointerException("methods contains null");
	    } else if (!(m instanceof Method)) {
		throw new IllegalArgumentException(
		    "methods must contain only Methods");
	    }
	    this.methods.put(new Long(Util.getMethodHash((Method) m)), m);
	}
	this.serverConstraints = serverConstraints;
	if (permissionClass != null) {
	    Util.checkPackageAccess(permissionClass);
	}
	permConstructor = getConstructor(permissionClass);
	
	permUsesMethod =
	    (permConstructor != null &&
	     permConstructor.getParameterTypes()[0] == Method.class);
	permissions = (permConstructor == null ?
		       null :
		       new IdentityHashMap(methods.size() + 2));
	try {
	    if (serverConstraints == null) {
		checkConstraints(serverCapabilities,
				 InvocationConstraints.EMPTY);
	    } else {
		Iterator iter = serverConstraints.possibleConstraints();
		while (iter.hasNext()) {
		    checkConstraints(serverCapabilities,
				     (InvocationConstraints) iter.next());
		}
	    }
	} catch (UnsupportedConstraintException e) {
	    throw new ExportException(
		"server does not support some constraints", e);
	}
    }

    /**
     * Check that the only unfulfilled requirement is Integrity.
     */
    private static void checkConstraints(ServerCapabilities serverCapabilities,
					 InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	InvocationConstraints unfulfilled =
	    serverCapabilities.checkConstraints(constraints);
	for (Iterator i = unfulfilled.requirements().iterator(); i.hasNext();)
	{
	    InvocationConstraint c = (InvocationConstraint) i.next();
	    if (!(c instanceof Integrity)) {
		throw new UnsupportedConstraintException(
		    "cannot satisfy unfulfilled constraint: " + c);
	    }
	    // REMIND: support ConstraintAlternatives containing Integrity?
	}
    }

    /**
     * Returns the class loader specified during construction.
     *
     * @return the class loader
     */
    protected final ClassLoader getClassLoader() {
	return loader;
    }
    
    /**
     * Checks that the specified class is a valid permission class for use in
     * preinvocation access control.
     *
     * @param	permissionClass the permission class, or <code>null</code>
     * @throws IllegalArgumentException if the permission class is abstract,
     * is not a subclass of {@link Permission}, or does not have a public
     * constructor that has either one <code>String</code> parameter or one
     * {@link Method} parameter and has no declared exceptions
     **/
    public static void checkPermissionClass(Class permissionClass) {
	getConstructor(permissionClass);
    }

    /**
     * Checks that the specified class is a subclass of Permission, is
     * public, is not abstract, and has the right one-parameter Method or
     * String constructor, and returns that constructor, otherwise throws
     * IllegalArgumentException.
     **/
    private static Constructor getConstructor(Class permissionClass) {
	if (permissionClass == null) {
	    return null;
	} else {
	    int mods = permissionClass.getModifiers();
	    if (!Permission.class.isAssignableFrom(permissionClass) ||
		Modifier.isAbstract(mods) || !Modifier.isPublic(mods))
	    {
		throw new IllegalArgumentException("bad permission class");
	    }
	}
	try {
	    Constructor permConstructor =
		permissionClass.getConstructor(new Class[]{Method.class});
	    if (permConstructor.getExceptionTypes().length == 0) {
		return permConstructor;
	    }
	} catch (NoSuchMethodException e) {
	}
	try {
	    Constructor permConstructor =
		permissionClass.getConstructor(new Class[]{String.class});
	    if (permConstructor.getExceptionTypes().length == 0) {
		return permConstructor;
	    }
	} catch (NoSuchMethodException ee) {
	}
	throw new IllegalArgumentException("bad permission class");
    }

    /**
     * Dispatches the specified inbound request to the specified remote object.
     * When used in conjunction with {@link BasicJeriExporter}, this
     * method is called in a context that has the security context and
     * context class loader specified by
     * {@link BasicJeriExporter#export BasicJeriExporter.export}.
     * 
     * <p><code>BasicInvocationDispatcher</code> implements this method to
     * execute the following actions in order:
     *
     * <ul>
     * <li>A byte specifying the marshal stream protocol version is read
     * from the request input stream of the inbound request. If any
     * exception is thrown when reading this byte, the inbound request is
     * aborted and this method returns. If the byte is not
     * <code>0x00</code>, two byte values of <code>0x00</code> (indicating
     * a marshal stream protocol version mismatch) are written to the
     * response output stream of the inbound request, the output stream is
     * closed, and this method returns.
     * 
     * <li>If the version byte is <code>0x00</code>, a second byte
     * specifying object integrity is read from the same stream.  If any
     * exception is thrown when reading this byte, the inbound request is
     * aborted and this method returns.  Object integrity will be enforced
     * if the value read is not <code>0x00</code>, but will not be enforced
     * if the value is <code>0x00</code>. An {@link
     * net.jini.io.context.IntegrityEnforcement} element is then added to
     * the server context, reflecting whether or not object integrity is
     * being enforced.
     *
     * <li>The {@link #createMarshalInputStream createMarshalInputStream}
     * method of this invocation dispatcher is called, passing the remote
     * object, the inbound request, a boolean indicating if object
     * integrity is being enforced, and the server context, to create the
     * marshal input stream for unmarshalling the request.
     *
     * <li>The {@link #unmarshalMethod unmarshalMethod} of this
     * invocation dispatcher is called with the remote object, the marshal
     * input stream, and the server context to obtain the remote method.
     *
     * <li> The {@link InboundRequest#checkConstraints checkConstraints}
     * method of the inbound request is called with the constraints that
     * must be enforced for that remote method, obtained by passing the
     * remote method to the {@link MethodConstraints#getConstraints
     * getConstraints} method of this invocation dispatcher's server
     * constraints, and adding {@link Integrity#YES Integrity.YES} as a
     * requirement if object integrity is being enforced. If the
     * unfulfilled requirements returned by <code>checkConstraints</code>
     * contains a constraint that is not an instance of {@link Integrity}
     * or if integrity is not being enforced and the returned requirements
     * contains the element <code>Integrity.YES</code>, an
     * <code>UnsupportedConstraintException</code> is sent back to the
     * caller as described further below. Otherwise, the {@link
     * #checkAccess checkAccess} method of this invocation dispatcher is
     * called with the remote object, the remote method, the enforced
     * constraints, and the server context.
     *
     * <li>The method arguments are obtained by calling the {@link
     * #unmarshalArguments unmarshalArguments} method of this invocation
     * dispatcher with the remote object, the remote method, the marshal
     * input stream, and the server context.
     * 
     * <li>If any exception is thrown during this unmarshalling, that exception
     * is sent back to the caller as described further below; however, if the
     * exception is a checked exception ({@link IOException},
     * {@link ClassNotFoundException}, or {@link NoSuchMethodException}), the
     * exception is first wrapped in an {@link UnmarshalException} and the
     * wrapped exception is sent back.
     *
     * <li>Otherwise, if unmarshalling is successful, the {@link #invoke
     * invoke} method of this invocation dispatcher is then called with the
     * remote object, the remote method, the arguments returned by
     * <code>unmarshalArguments</code>, and the server context. If
     * <code>invoke</code> throws an exception, that exception is sent back
     * to the caller as described further below.
     *
     * <li>The input stream is closed whether or not an exception was
     * thrown unmarshalling the arguments or invoking the method.
     *
     * <li>If <code>invoke</code> returns normally, a byte value of
     * <code>0x01</code> is written to the response output stream of the
     * inbound request. Then the {@link #createMarshalOutputStream
     * createMarshalOutputStream} method of this invocation dispatcher is
     * called, passing the remote object, the remote method, the inbound
     * request, and the server context, to create the marshal output stream
     * for marshalling the response. Then the {@link #marshalReturn
     * marshalReturn} method of this invocation dispatcher is called with
     * the remote object, the remote method, the value returned by
     * <code>invoke</code>, the marshal output stream, and the server
     * context. Then the marshal output stream is closed. Any exception
     * thrown during this marshalling is ignored.
     * 
     * <li>When an exception is sent back to the caller, a byte value of
     * <code>0x02</code> is written to the response output stream of the
     * inbound request. Then a marshal output stream is created by calling
     * the <code>createMarshalOutputStream</code> method as described above
     * (but with a <code>null</code> remote method if one was not
     * successfully unmarshalled). Then the {@link #marshalThrow
     * marshalThrow} method of this invocation dispatcher is called with
     * the remote object, the remote method (or <code>null</code> if one
     * was not successfully unmarshalled), the exception, the marshal
     * output stream, and the server context. Then the marshal output
     * stream is closed. Any exception thrown during this marshalling is
     * ignored. If the exception being sent back is a
     * <code>RemoteException</code>, it is wrapped in a {@link
     * ServerException} and the wrapped exception is passed to
     * <code>marshalThrow</code>. If the exception being sent back is an
     * <code>Error</code>, it is wrapped in a {@link ServerError} and the
     * wrapped exception is passed to <code>marshalThrow</code>. If the
     * exception being sent back occurred before or during the call to
     * <code>unmarshalMethod</code>, then the remote method passed to
     * <code>marshalThrow</code> is <code>null</code>.
     * </ul>
     *
     * @throws	NullPointerException {@inheritDoc}
     **/
    public void dispatch(Remote impl,
			 InboundRequest request,
			 Collection context)
    {
	if (impl == null || context == null) {
	    throw new NullPointerException();
	}

	/*
	 * Read (and check) version number and integrity flag.
	 */
	InputStream rin = null;
	boolean integrity;
	try {
	    rin = request.getRequestInputStream();
	    switch (rin.read()) {
		case VERSION:
		    break;
		case -1:
		    throw new EOFException();
		default:
		    rin.close();
		    OutputStream ros = request.getResponseOutputStream();
		    ros.write(MISMATCH);
		    ros.write(VERSION);
		    ros.close();
		    return;
	    }
	    switch (rin.read()) {
	    case 0:
		integrity = false;
		break;
	    case -1:
		throw new EOFException();
	    default:
		integrity = true;
	    }
	} catch (Throwable t) {
	    if (logger.isLoggable(Levels.FAILED)) {
		logLocalThrow(impl, null, t);
	    }
	    request.abort();
	    return;
	}

	Method method = null;
	Object returnValue = null;
	Throwable t = null;
	boolean fromImpl = false;
	Util.populateContext(context, integrity);
	ObjectInputStream in = null;
	
	try {
	    /*
	     * Unmarshal method and check security constraints.
	     */
	    in = createMarshalInputStream(impl, request, integrity, context);
	    method = unmarshalMethod(impl, in, context);
	    InvocationConstraints sc =
		(serverConstraints == null ?
		 InvocationConstraints.EMPTY :
		 serverConstraints.getConstraints(method));
	    if (integrity && !sc.requirements().contains(Integrity.YES)) {
		Collection requirements = new ArrayList(sc.requirements());
		requirements.add(Integrity.YES);
		sc = new InvocationConstraints(requirements, sc.preferences());
	    }
	    
	    InvocationConstraints unfulfilled = request.checkConstraints(sc);
	    for (Iterator i = unfulfilled.requirements().iterator();
		 i.hasNext();)
	    {
		InvocationConstraint c = (InvocationConstraint) i.next();
		if (!(c instanceof Integrity) ||
		    (!integrity && c == Integrity.YES))
		{
		    throw new UnsupportedConstraintException(
		        "cannot satisfy unfulfilled constraint: " + c);
		}
		// REMIND: support ConstraintAlternatives containing Integrity?
	    }
	    
	    checkAccess(impl, method, sc, context);

	    /*
	     * Unmarshal arguments.
	     */
	    Object[] args = unmarshalArguments(impl, method, in, context);
	    if (logger.isLoggable(Level.FINE)) {
		logCall(impl, method, args);
	    }
	
	    /*
	     * Invoke method on remote object.
	     */
	    try {
		returnValue = invoke(impl, method, args, context);
		if (logger.isLoggable(Level.FINE)) {
		    logReturn(impl, method, returnValue);
		}
	    } catch (Throwable tt) {
		t = tt;
		fromImpl = true;
	    }
	} catch (RuntimeException e) {
	    t = e;
	} catch (Exception e) {
	    t = new UnmarshalException("unmarshalling method/arguments", e);
	} catch (Throwable tt) {
	    t = tt;
	} finally {
	    if (in != null) {
		try {
		    in.close();
		} catch (IOException ignore) {
		}
	    }
	}

	/*
	 * Marshal return value or exception.
	 */
	try {
	    request.getResponseOutputStream().write(t == null ?
						    RETURN : THROW);
	    ObjectOutputStream out =
		createMarshalOutputStream(impl, method, request, context);
	    if (t != null) {
		if (logger.isLoggable(Levels.FAILED)) {
		    logRemoteThrow(impl, method, t, fromImpl);
		}
		if (t instanceof RemoteException) {
		    t = new ServerException("RemoteException in server thread",
					    (Exception) t);
		} else if (t instanceof Error) {
		    t = new ServerError("Error in server thread", (Error) t);
		}
		if (suppressStackTraces) {
		    Util.clearStackTraces(t);
		}
		marshalThrow(impl, method, t, out, context);
	    } else {
		marshalReturn(impl, method, returnValue, out, context);
	    }
	    out.close();
	    
	} catch (Throwable tt) {
	    /*
	     * All exceptions are fatal at this point.  There is no
	     * recovery if a problem occurs writing the result, so
	     * abort the call and return.  But first try to close the
	     * response output stream, in case the IOException was
	     * able to be serialized for the client successfully.
	     */
	    try {
		request.getResponseOutputStream().close();
	    } catch (IOException ignore) {
	    }
	    request.abort();
	    if (logger.isLoggable(Levels.FAILED)) {
		logLocalThrow(impl, method, tt);
	    }
	}
    }

    /**
     * Returns a new marshal input stream to use to read objects from the
     * request input stream obtained by invoking the {@link
     * InboundRequest#getRequestInputStream getRequestInputStream} method
     * on the given <code>request</code>.
     *
     * <p><code>BasicInvocationDispatcher</code> implements this method as
     * follows:
     *
     * <p>First, a class loader is selected to use as the
     * <code>defaultLoader</code> and the <code>verifierLoader</code> for
     * the marshal input stream instance.  If the class loader specified at
     * construction is not <code>null</code>, the selected loader is that
     * loader.  Otherwise, if a security manager exists, its {@link
     * SecurityManager#checkPermission checkPermission} method is invoked
     * with the permission <code>{@link
     * RuntimePermission}("getClassLoader")</code>; this invocation may
     * throw a <code>SecurityException</code>.  If the above security check
     * succeeds, the selected loader is the class loader of
     * <code>impl</code>'s class.
     *
     * <p>This method returns a new {@link MarshalInputStream} instance
     * constructed with the input stream (obtained from the
     * <code>request</code> as specified above) for the input stream
     * <code>in</code>, the selected loader for <code>defaultLoader</code>
     * and <code>verifierLoader</code>, the boolean <code>integrity</code>
     * for <code>verifyCodebaseIntegrity</code>, and an unmodifiable view
     * of <code>context</code> for the <code>context</code> collection.
     * The {@link MarshalInputStream#useCodebaseAnnotations
     * useCodebaseAnnotations} method is invoked on the created stream
     * before it is returned.
     *
     * <p>A subclass can override this method to control how the marshal input
     * stream is created or implemented.
     *
     * @param	impl the remote object
     * @param	request the inbound request
     * @param	integrity <code>true</code> if object integrity is being
     * 		enforced for the remote call, and <code>false</code> otherwise
     * @param	context the server context
     * @return	a new marshal input stream for unmarshalling a call request
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    protected ObjectInputStream
        createMarshalInputStream(Object impl,
				 InboundRequest request,
				 boolean integrity,
				 Collection context)
	throws IOException
    {
	ClassLoader streamLoader;
	if (loader != null) {
	    streamLoader = getClassLoader();
	} else {
	    SecurityManager security = System.getSecurityManager();
	    if (security != null) {
		security.checkPermission(getClassLoaderPermission);
	    }
	    streamLoader = impl.getClass().getClassLoader();
	}
	
	Collection unmodContext = Collections.unmodifiableCollection(context);
	MarshalInputStream in =
	    new MarshalInputStream(request.getRequestInputStream(),
				   streamLoader, integrity,
				   streamLoader, unmodContext);
	in.useCodebaseAnnotations();
	return in;
    }
    
    /**
     * Returns a new marshal output stream to use to write objects to the
     * response output stream obtained by invoking the {@link
     * InboundRequest#getResponseOutputStream getResponseOutputStream}
     * method on the given <code>request</code>.
     *
     * <p>This method will be called with a <code>null</code>
     * <code>method</code> argument if an <code>IOException</code> occurred
     * when reading method information from the incoming call stream.
     *
     * <p><code>BasicInvocationDispatcher</code> implements this method to
     * return a new {@link MarshalOutputStream} instance constructed with
     * the output stream obtained from the <code>request</code> as
     * specified above and an unmodifiable view of the given
     * <code>context</code> collection.
     *
     * <p>A subclass can override this method to control how the marshal output
     * stream is created or implemented.
     *
     * @param	impl the remote object
     * @param   method the possibly-<code>null</code> <code>Method</code>
     *		instance corresponding to the interface method invoked on
     *		the remote object
     * @param	request the inbound request
     * @param	context the server context
     * @return	a new marshal output stream for marshalling a call response
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if <code>impl</code>,
     *		<code>request</code>, or <code>context</code> is
     *		<code>null</code>
     **/
    protected ObjectOutputStream
        createMarshalOutputStream(Object impl,
				  Method method,
				  InboundRequest request,
				  Collection context)
	throws IOException
    {
	if (impl == null) {
	    throw new NullPointerException();
	}
	OutputStream out = request.getResponseOutputStream();
	Collection unmodContext = Collections.unmodifiableCollection(context);
	return new MarshalOutputStream(out, unmodContext);
    }
							  
    /**
     * Checks that the client has permission to invoke the specified method on
     * the specified remote object.
     *
     * <p><code>BasicInvocationDispatcher</code> implements this method as
     * follows:
     *
     * <p>If a permission class was specified when this invocation
     * dispatcher was constructed, {@link #checkClientPermission
     * checkClientPermission} is called with a permission constructed from
     * the permission class. If the permission class has a constructor with
     * a <code>Method</code> parameter, the permission is constructed by
     * passing the specified method to that constructor. Otherwise the
     * permission is constructed by passing the fully qualified name of the
     * method to the constructor with a <code>String</code> parameter,
     * where the argument is formed by concatenating the name of the
     * declaring class of the specified method and the name of the method,
     * separated by ".".
     *
     * <p>A subclass can override this method to implement other preinvocation
     * access control mechanisms.
     *
     * @param	impl the remote object
     * @param	method the remote method
     * @param	constraints the enforced constraints for the specified
     *		method, or <code>null</code>
     * @param	context the server context
     * @throws	SecurityException if the current client subject does not
     *		have permission to invoke the method
     * @throws	IllegalStateException if the current thread is not executing an
     *		incoming remote call for a remote object
     * @throws	NullPointerException if <code>impl</code>,
     *		<code>method</code>, or <code>context</code> is
     *		<code>null</code> 
     **/
    protected void checkAccess(Remote impl,
			       Method method,
			       InvocationConstraints constraints,
			       Collection context)
    {
	if (impl == null || method == null || context == null) {
	    throw new NullPointerException();
	}
	if (permConstructor == null) {
	    return;
	}
	Permission perm;
	synchronized (permissions) {
	    perm = (Permission) permissions.get(method);
	}
	if (perm == null) {
	    try {
		perm = (Permission) permConstructor.newInstance(new Object[]{
		    permUsesMethod ?
			(Object) method :
			method.getDeclaringClass().getName() + "." +
			method.getName()});
	    } catch (InvocationTargetException e) {
		Throwable t = e.getTargetException();
		if (t instanceof Error) {
		    throw (Error) t;
		}
		throw (RuntimeException) t;
	    } catch (Exception e) {
		throw new RuntimeException("unexpected exception", e);
	    }
	    synchronized (permissions) {
		permissions.put(method, perm);
	    }
	}
	checkClientPermission(perm);
    }
    
    /**
     * Checks that the client subject for the current remote call has the
     * specified permission. The client subject is obtained by calling {@link
     * ServerContext#getServerContextElement
     * ServerContext.getServerContextElement}, passing the class {@link
     * ClientSubject}, and then calling the {@link
     * ClientSubject#getClientSubject getClientSubject} method of the returned
     * element (if any). If a security manager is installed, a {@link
     * ProtectionDomain} is constructed with an empty {@link CodeSource}
     * (<code>null</code> location and certificates), <code>null</code>
     * permissions, <code>null</code> class loader, and the principals from
     * the client subject (if any), and the <code>implies</code> method of
     * that protection domain is invoked with the specified permission. If
     * <code>true</code> is returned, this method returns normally, otherwise
     * a <code>SecurityException</code> is thrown. If no security
     * manager is installed, this method returns normally.
     *
     * <p>Note that the permission grant required to satisfy this check must
     * be to the client's principals alone (or a subset thereof); it cannot be
     * qualified by what code is being executed. At the point in a remote call
     * where this method is intended to be used, the useful "call stack" only
     * exists at the other end of the remote call (on the client side), and so
     * cannot meaningfully enter into the access control decision.
     *
     * @param	permission the requested permission
     * @throws	SecurityException if the current client subject has not 
     *		been granted the specified permission
     * @throws	IllegalStateException if the current thread is not executing
     *		an incoming remote method for a remote object
     * @throws	NullPointerException if <code>permission</code> is
     *		<code>null</code> 
     **/
    public static void checkClientPermission(final Permission permission) {
	if (permission == null) {
	    throw new NullPointerException();
	}
	Subject client =
	    (Subject) AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    try {
			return Util.getClientSubject();
		    } catch (ServerNotActiveException e) {
			throw new IllegalStateException("server not active");
		    }
		}
	    });
	if (System.getSecurityManager() == null) {
	    return;
	}
	ProtectionDomain pd;
	if (client == null) {
	    pd = emptyPD;
	} else {
	    synchronized (domains) {
		WeakKey k;
		while ((k = (WeakKey) queue.poll()) != null) {
		    domains.remove(k);
		}
		pd = (ProtectionDomain) domains.get(new WeakKey(client));
		if (pd == null) {
		    Set set = client.getPrincipals();
		    Principal[] prins =
			(Principal[]) set.toArray(new Principal[set.size()]);
		    pd = new ProtectionDomain(emptyCS, null, null, prins);
		    domains.put(new WeakKey(client, queue), pd);
		}
	    }
	}
	boolean ok = pd.implies(permission);
	// XXX what about logging
	if (!ok) {
	    throw new AccessControlException("access denied " + permission);
	}
    }

    /**
     * Unmarshals a method representation from the marshal input stream,
     * <code>in</code>, and returns the <code>Method</code> object
     * corresponding to that representation.  For each remote call, the
     * <code>dispatch</code> method calls this method to unmarshal the
     * method representation.
     *
     * <p><code>BasicInvocationDispatcher</code> implements this method to
     * call the <code>readLong</code> method on the marshal input stream to
     * read the method's representation encoded as a JRMP method hash
     * (defined in section 8.3 of the Java(TM) Remote Method Invocation
     * (Java RMI) specification) and return its
     * corresponding <code>Method</code> object chosen from the collection
     * of methods passed to the constructor of this invocation dispatcher.
     * If more than one method has the same hash, it is arbitrary as to
     * which one is returned.
     *
     * <p>A subclass can override this method to control how the remote
     * method is unmarshalled.
     *
     * @param	impl the remote object
     * @param	in the marshal input stream for the remote call
     * @param	context the server context passed to the {@link #dispatch
     *		dispatch} method for the remote call being processed
     * @return	a <code>Method</code> object corresponding to the method
     *		representation
     * @throws	IOException if an I/O exception occurs 
     * @throws	NoSuchMethodException if the method representation does not
     * 		correspond to a valid method
     * @throws  ClassNotFoundException if a class could not be found during
     *          unmarshalling
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    protected Method unmarshalMethod(Remote impl,
				     ObjectInputStream in,
				     Collection context)
        throws IOException, NoSuchMethodException, ClassNotFoundException
    {
	if (impl == null || context == null) {
	    throw new NullPointerException();
	}
	long hash = in.readLong();
	Method method = (Method) methods.get(new Long(hash));
	if (method == null) {
	    throw new NoSuchMethodException(
	     "unrecognized method hash: method not supported by remote object");
	}
	return method;
    }

    /**
     * Unmarshals the arguments for the specified remote <code>method</code>
     * from the specified marshal input stream, <code>in</code>, and returns an
     * <code>Object</code> array containing the arguments read.  For each
     * remote call, the <code>dispatch</code> method calls this method to
     * unmarshal arguments.
     *
     * <p><code>BasicInvocationDispatcher</code> implements this method to
     * unmarshal each argument as follows:
     *
     * <p>If the corresponding declared parameter type is primitive, then
     * the primitive value is read from the stream using the
     * corresponding <code>read</code> method for that primitive type (for
     * example, if the type is <code>int.class</code>, then the primitive
     * <code>int</code> value is read to the stream using the
     * <code>readInt</code> method) and the value is wrapped in the
     * corresponding primitive wrapper class for that type (e.g.,
     * <code>Integer</code> for <code>int</code>, etc.).  Otherwise, the
     * argument is read from the stream using the <code>readObject</code>
     * method and returned as is.
     *
     * <p>A subclass can override this method to unmarshal the arguments in an
     * alternative context, perform post-processing on the arguments,
     * unmarshal additional implicit data, or otherwise control how the
     * arguments are unmarshalled. In general, the context used should mirror
     * the context in which the arguments are manipulated in the
     * implementation of the remote object.
     *
     * @param	impl the remote object
     * @param   method the <code>Method</code> instance corresponding
     *          to the interface method invoked on the remote object
     * @param	in the incoming request stream for the remote call
     * @param	context the server context passed to the {@link #dispatch
     *		dispatch} method for the remote call being processed
     * @return	an <code>Object</code> array containing
     *		the unmarshalled arguments.  If an argument's corresponding
     *		declared parameter type is primitive, then its value is
     *		represented with an instance of the corresponding primitive
     *		wrapper class; otherwise, the value for that argument is an
     *		object of a class assignable to the declared parameter type.
     * @throws	IOException if an I/O exception occurs
     * @throws  ClassNotFoundException if a class could not be found during
     *          unmarshalling
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    protected Object[] unmarshalArguments(Remote impl,
					  Method method,
					  ObjectInputStream in,
					  Collection context)
	throws IOException, ClassNotFoundException
    {
	if (impl == null || in == null || context == null) {
	    throw new NullPointerException();
	}
	Class[] types = method.getParameterTypes();
	Object[] args = new Object[types.length];
	for (int i = 0; i < types.length; i++) {
	    args[i] = Util.unmarshalValue(types[i], in);
	}
	return args;
    }

    /**
     * Invokes the specified <code>method</code> on the specified remote
     * object <code>impl</code>, with the specified arguments.
     * If the invocation completes normally, the return value will be
     * returned by this method.  If the invocation throws an exception,
     * this method will throw the same exception.
     *
     * <p><code>BasicInvocationDispatcher</code> implements this method as
     * follows: 
     *
     * <p>If the specified method is not set accessible or is not a
     * <code>public</code> method of a <code>public</code> class an
     * <code>IllegalArgumentException</code> is thrown.
     *
     * <p>If the specified method is {@link ProxyTrust#getProxyVerifier
     * ProxyTrust.getProxyVerifier} and the remote object is an instance of
     * {@link ServerProxyTrust}, the {@link ServerProxyTrust#getProxyVerifier
     * getProxyVerifier} method of the remote object is called and the result
     * is returned.
     * 
     * <p>Otherwise, the specified method's <code>invoke</code> method is
     * called with the specified remote object and the specified arguments,
     * and the result is returned. If <code>invoke</code> throws an {@link
     * InvocationTargetException}, that exception is caught and the target
     * exception inside it is thrown to the caller. Any other exception
     * thrown during any of this computation is thrown to the caller.
     *
     * <p>A subclass can override this method to invoke the method in an
     * alternative context, perform pre- or post-processing, or otherwise
     * control how the method is invoked.
     *
     * @param	impl the remote object
     * @param	method the <code>Method</code> instance corresponding
     *		to the interface method invoked on the remote object
     * @param	args the method arguments
     * @param	context the server context passed to the {@link #dispatch
     *		dispatch} method for the remote call being processed
     * @return	the result of the method invocation on <code>impl</code>
     * @throws	NullPointerException if any argument is <code>null</code>
     * @throws	Throwable the exception thrown from the method invocation
     *		on <code>impl</code>
     **/
    protected Object invoke(Remote impl,
			    Method method,
			    Object[] args,
			    Collection context)
	throws Throwable
    {
	if (impl == null || args == null || context == null) {
	    throw new NullPointerException();
	}

	if (!method.isAccessible() &&
	    !(Modifier.isPublic(method.getDeclaringClass().getModifiers()) &&
	      Modifier.isPublic(method.getModifiers())))
	{
	    throw new IllegalArgumentException(
		"method not public or set accessible");
	}
	
	Class decl = method.getDeclaringClass();
	if (decl == ProxyTrust.class &&
	    method.getName().equals("getProxyVerifier") &&
	    impl instanceof ServerProxyTrust)
	{
	    if (args.length != 0) {
		throw new IllegalArgumentException("incorrect arguments");
	    }
	    return ((ServerProxyTrust) impl).getProxyVerifier();
	}
	
	try {
	    return method.invoke(impl, args);
	} catch (InvocationTargetException e) {
	    throw e.getTargetException();
	}
    }

    /**
     * Marshals the specified return value for the specified remote method
     * to the marshal output stream, <code>out</code>.  After invoking
     * the method on the remote object <code>impl</code>, the
     * <code>dispatch</code> method calls this method to marshal the value
     * returned from the invocation on that remote object.
     *
     * <p><code>BasicInvocationDispatcher</code> implements this method as
     * follows: 
     *
     * <p>If the declared return type of the method is void, then no return
     * value is written to the stream.  If the return type is a primitive
     * type, then the primitive value is written to the stream (for
     * example, if the type is <code>int.class</code>, then the primitive
     * <code>int</code> value is written to the stream using the
     * <code>writeInt</code> method).  Otherwise, the return value is
     * written to the stream using the <code>writeObject</code> method.
     *
     * <p>A subclass can override this method to marshal the return value in an
     * alternative context, perform pre- or post-processing on the return
     * value, marshal additional implicit data, or otherwise control how the
     * return value is marshalled. In general, the context used should mirror
     * the context in which the result is computed in the implementation of
     * the remote object.
     *
     * @param	impl the remote object
     * @param   method the <code>Method</code> instance corresponding
     *          to the interface method invoked on the remote object
     * @param   returnValue the return value to marshal to the stream
     * @param	out the marshal output stream
     * @param	context the server context passed to the {@link #dispatch
     *		dispatch} method for the remote call being processed
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if <code>impl</code>,
     *		<code>method</code>, <code>out</code>, or
     *		<code>context</code> is <code>null</code>
     **/
    protected void marshalReturn(Remote impl,
				 Method method,
				 Object returnValue,
				 ObjectOutputStream out,
				 Collection context)
	throws IOException
    {
	if (impl == null || out == null || context == null) {
	    throw new NullPointerException();
	}
	Class returnType = method.getReturnType();
	if (returnType != void.class) {
	    Util.marshalValue(returnType, returnValue, out);
	}
    }

    /**
     * Marshals the <code>throwable</code> for the specified remote method
     * to the marshal output stream, <code>out</code>.  For each method
     * invocation on <code>impl</code> that throws an exception, this
     * method is called to marshal the throwable.  This method is also
     * called if an exception occurs reading the method information from
     * the incoming call stream, as a result of calling {@link
     * #unmarshalMethod unmarshalMethod}; in this case, the
     * <code>Method</code> instance will be <code>null</code>.
     *
     * <p><code>BasicInvocationDispatcher</code> implements this method to
     * marshal the throwable to the stream using the
     * <code>writeObject</code> method.
     *
     * <p>A subclass can override this method to marshal the throwable in an
     * alternative context, perform pre- or post-processing on the throwable,
     * marshal additional implicit data, or otherwise control how the throwable
     * is marshalled. In general, the context used should mirror the context
     * in which the exception is generated in the implementation of the
     * remote object.
     *
     * @param	impl the remote object
     * @param   method the possibly-<code>null</code> <code>Method</code>
     *		instance corresponding to the interface method invoked on
     *		the remote object
     * @param   throwable a throwable to marshal to the stream
     * @param	out the marshal output stream
     * @param 	context the server context
     * @throws	IOException if an I/O exception occurs
     * @throws	NullPointerException if <code>impl</code>,
     *		<code>throwable</code>, <code>out</code>, or
     *		<code>context</code> is <code>null</code>
     **/
    protected void marshalThrow(Remote impl,
				Method method,
				Throwable throwable,
				ObjectOutputStream out,
				Collection context)
	throws IOException
    {
	if (impl == null || throwable == null || context == null) {
	    throw new NullPointerException();
	}
	out.writeObject(throwable);
    }

    /**
     * Logs the start of an inbound call.
     **/
    private void logCall(Remote impl, Method method, Object[] args) {
	String msg = "inbound call {0}.{1} to {2} from {3}\nclient {4}";
	if (logger.isLoggable(Level.FINEST)) {
	    msg = "inbound call {0}.{1} to {2} from {3}\nargs {5}\nclient {4}";
	}
	Subject client = getClientSubject();
	Set prins = (client != null) ? client.getPrincipals() : null;
	String host = null;
	try {
	    host = Util.getClientHostString();
	} catch (ServerNotActiveException e) {
	}
	logger.logp(Level.FINE, this.getClass().getName(), "dispatch", msg,
		    new Object[]{method.getDeclaringClass().getName(),
				 method.getName(), impl, host,
				 prins, Arrays.asList(args)});
    }

    /**
     * Logs the return of an inbound call.
     **/
    private void logReturn(Remote impl, Method method, Object res) {
	String msg = "inbound call {0}.{1} to {2} returns";
	if (logger.isLoggable(Level.FINEST) &&
	    method.getReturnType() != void.class)
	{
	    msg = "inbound call {0}.{1} to {2} returns {3}";
	}
	logger.logp(Level.FINE, this.getClass().getName(), "dispatch", msg,
		    new Object[]{method.getDeclaringClass().getName(),
				 method.getName(), impl, res});
    }

    /**
     * Logs the remote throw of an inbound call.
     **/
    private void logRemoteThrow(Remote impl,
				Method method,
				Throwable t,
				boolean fromImpl)
    {
	String msg;
	if (fromImpl) {
	    msg = "inbound call {0}.{1} to {2} remotely throws";
	} else {
	    msg = "inbound call {0}.{1} to {2} dispatch remotely throws";
	    if (logger.isLoggable(Level.FINEST)) {
		msg = "inbound call {0}.{1} to {2} dispatch remotely throws" +
		      "\nclient {3}";
	    }
	}
	logThrow(msg, impl, method, t);
    }

    /**
     * Logs the local throw an an inbound call.
     **/
    private void logLocalThrow(Remote impl, Method method, Throwable t) {
	String msg = "inbound call {0}.{1} to {2} dispatch locally throws";
	if (logger.isLoggable(Level.FINEST)) {
	    msg = "inbound call {0}.{1} to {2} dispatch locally throws" +
		  "\nclient {3}";
	}
	logThrow(msg, impl, method, t);
    }

    /**
     * Logs the throw of an inbound call using the specified message,
     * whose format elements are mapped to string representations of
     * the following items: {0} for the method's declaring class, {1}
     * for the method's name, {2} for the target remote object, and
     * {3} for the client subject.
     **/
    private void logThrow(String msg, Remote impl, Method method, Throwable t)
    {
	LogRecord lr = new LogRecord(Levels.FAILED, msg);
	lr.setLoggerName(logger.getName());
	lr.setSourceClassName(this.getClass().getName());
	lr.setSourceMethodName("dispatch");
	lr.setParameters(new Object[]{(method == null ?
				       "<unknown>" :
				       method.getDeclaringClass().getName()),
				      (method == null ?
				       "<unknown>" : method.getName()),
				      impl, getClientSubject()});
	lr.setThrown(t);
	logger.log(lr);
    }

    /**
     * Return the current client subject or <code>null</code> if not
     * currently executing a remote call.
     */
    private static Subject getClientSubject() {
	return (Subject) AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		try {
		    return Util.getClientSubject();
		} catch (ServerNotActiveException e) {
		    return null;
		}
	    }
	});
    }
}
