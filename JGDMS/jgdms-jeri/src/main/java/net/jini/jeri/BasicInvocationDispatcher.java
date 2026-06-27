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

import org.apache.river.action.GetBooleanAction;
import org.apache.river.jeri.internal.runtime.Util;
import org.apache.river.concurrent.RC;
import org.apache.river.concurrent.Ref;
import org.apache.river.concurrent.Referrer;
import org.apache.river.logging.Levels;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import net.jini.core.constraint.AtomicInputValidation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.export.CodebaseAccessor;
import net.jini.export.ServerContext;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;
import net.jini.io.MarshalledInstance;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.AtomicValidationEnforcement;
import net.jini.io.context.ClientSubject;
import net.jini.security.AccessPermission;
import net.jini.security.Security;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.ProxyTrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.io.context.ClientUserSubject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import net.jini.security.jwt.DefaultJwtVerifier;
import net.jini.security.jwt.JwtVerificationException;
import net.jini.security.jwt.JwtVerifier;
import org.apache.river.api.io.AccessControlContextSerializer;
import org.apache.river.api.io.AtomicObjectInput;

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
 * 
 *
 * This implementation uses the following system property:
 * <dl>
 * <dt><code>org.apache.river.jeri.server.suppressStackTrace</code>
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
    static final byte VERSION = 0x01;
    
    static final byte PREVIOUS_VERSION = 0x0;
    
    /** Marshal stream protocol version with user principals and remote ACC. */
    static final byte VERSION_WITH_PRINCIPALS_AND_ACC = 0x02;

    /**
     * Maximum number of user Subjects accepted from the wire in a single
     * request (protocol version 0x02).  A real multi-party transaction rarely
     * carries more than a handful of Subjects; this cap prevents a malicious
     * peer from forcing unbounded allocation.
     */
    private static final int MAX_USER_SUBJECTS = 16;
    /**
     * Maximum number of user principals accepted per Subject from the wire in
     * a single request (protocol version 0x02).  A real Subject rarely carries
     * more than a handful of principals; this cap prevents a malicious peer
     * from forcing unbounded allocation.
     */
    private static final int MAX_USER_PRINCIPALS = 64;
    private static final int MAX_ACC_BLOCK_BYTES = 1024 * 1024;

    /**
     * Maximum byte length of a single UTF-8–encoded string field (class name
     * or principal name) accepted from the wire.  Prevents memory exhaustion
     * from a crafted oversized field.
     */
    private static final int MAX_STRING_BYTES = 8192;

    /**
     * Pre-resolved constructors for the fixed set of known {@link Principal}
     * implementations accepted from the wire.  Populated once at class-load
     * time so that {@link #instantiatePrincipal} never calls
     * {@code Class.forName} on a remote-supplied class name, eliminating the
     * class-loading CPU DoS vector.
     */
    private static final Map<String, Constructor<? extends Principal>> PRINCIPAL_CTORS;
    static {
        Set<String> allowed = Set.of(
            "javax.security.auth.x500.X500Principal",
            "javax.security.auth.kerberos.KerberosPrincipal",
            "net.jini.jeri.ssl.SpiffePrincipal",
            "net.jini.security.jwt.JwtPrincipal"
        );
        Map<String, Constructor<? extends Principal>> ctorMap = new HashMap<>();
        for (String cname : allowed) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Principal> cls =
                    (Class<? extends Principal>) Class.forName(cname, false,
                        // System classloader is required: SpiffePrincipal and JwtPrincipal are
                        // JGDMS application-classpath classes (jgdms-jeri / jgdms-platform),
                        // not JDK built-ins.  The allowlist
                        // (not the classloader) is the security boundary — unknown names return
                        // RemotePrincipal without any classloading.
                        ClassLoader.getSystemClassLoader());
                ctorMap.put(cname, cls.getConstructor(String.class));
            } catch (Exception ignored) { /* class not present on this JDK/classpath */ }
        }
        PRINCIPAL_CTORS = Collections.unmodifiableMap(ctorMap);
    }

    /**
     * Pluggable JWT verifier (Option D, Work Item 44).  When {@code null} the
     * {@link #DEFAULT_JWT_VERIFIER} is used, which performs structural claim
     * checks ({@code exp}, {@code iat}) without JWKS signature verification.
     * Operators who need full OIDC signature verification should install a
     * custom verifier via {@link #setJwtVerifier(JwtVerifier)}.
     *
     * <p>Set via {@link #setJwtVerifier(JwtVerifier)} before exporting remote
     * objects.
     */
    private static volatile JwtVerifier jwtVerifier = null;

    /**
     * Fallback verifier applied when no custom {@link JwtVerifier} has been
     * installed.  Performs structural claim checks ({@code exp}, {@code iat})
     * without JWKS network calls — zero operational cost, closes the default-
     * path gap described in Work Item 43 / §2.3 of the security assessment.
     */
    private static final JwtVerifier DEFAULT_JWT_VERIFIER = new DefaultJwtVerifier();

    /**
     * Maximum number of raw JWT tokens accepted per Subject from the wire.
     * Prevents unbounded allocation from a malicious peer.
     */
    private static final int MAX_JWT_PER_SUBJECT = 4;

    /**
     * Maximum byte length of a single raw JWT token accepted from the wire.
     * Matches {@code DefaultJwtVerifier.MAX_TOKEN_LENGTH}.
     */
    private static final int MAX_JWT_BYTES = 65_536;

    /**
     * Connection-level JWT verification cache (Option D, Work Item 44).
     *
     * <p>Maps raw JWT compact-serialization strings to their expiry
     * {@link Instant} (extracted from the {@code exp} claim).  An entry is
     * considered valid while {@code Instant.now().isBefore(exp)}.  The
     * {@link JwtVerifier} is called at most once per token per validity window,
     * amortising any expensive JWKS HTTP lookup over the full token lifetime.
     *
     * <p>Size is bounded at {@value #JWT_CACHE_MAX_SIZE} entries.  When the
     * limit is reached, all expired entries are purged; if the map is still
     * full after pruning, it is cleared entirely (simple, safe, rare).
     */
    private static final ConcurrentHashMap<String, Instant> JWT_VERIFICATION_CACHE =
            new ConcurrentHashMap<>();
    private static final int JWT_CACHE_MAX_SIZE = 1024;

    
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

    /** Wire marshalling-format identifier of this dispatcher's codec (STD-008 sec.18.3). */
    private final String marshallingFormat;

    /** Map from Subject (weak identity) to ProtectionDomain. */
    private static final ConcurrentMap<Subject, ProtectionDomain> domains =
	RC.concurrentMap(
	    new ConcurrentHashMap<Referrer<Subject>, Referrer<ProtectionDomain>>(),
	    Ref.WEAK_IDENTITY,
	    Ref.STRONG,
	    1000L, 0L
	);

    /** dispatch logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicInvocationDispatcher");

    /**
     * Flag to remove server-side stack traces before marshalling
     * exceptions thrown by remote invocations to this VM
     */
    private static final boolean suppressStackTraces =
	((Boolean) AccessController.doPrivileged(new GetBooleanAction(
	    "org.apache.river.jeri.server.suppressStackTraces")))
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
     * DirtyChai JDK extension: {@code Subject.callAs(Callable, Subject...)}
     * varargs method, or {@code null} on a standard JDK.  Cached once at
     * class-load time via reflection.
     */
    private static final Method CALL_AS_MULTI_SUBJECT;
    static {
	Method m = null;
	try {
	    m = Subject.class.getMethod("callAs", Callable.class, Subject[].class);
	} catch (NoSuchMethodException ignored) {
	    // Standard JDK — multi-Subject callAs not available
	} catch (SecurityException ignored) {
	    // Security manager denied reflective access — treat as not available
	}
	CALL_AS_MULTI_SUBJECT = m;
    }

    /**
     * Registers a {@link JwtVerifier} to be called when a raw JWT token is
     * received as part of the user-Subject wire block (protocol version
     * {@code 0x02}, Work Item 44 Option D).
     *
     * <p>Setting {@code null} (the default) disables JWT verification; tokens
     * received on the wire are silently discarded and the principals in the
     * Subject are accepted solely on the authority of the presenting SPIFFE
     * SVID.  This preserves backward compatibility with deployments that rely
     * on SVID-scoped trust.
     *
     * <p>The verifier is a global JVM-wide setting; register it once at
     * application startup, before any remote objects are exported.
     *
     * @param verifier the verifier to use, or {@code null} to disable
     */
    public static void setJwtVerifier(JwtVerifier verifier) {
        jwtVerifier = verifier;
    }

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
	this(methods, serverCapabilities, serverConstraints, permissionClass, loader,
		MarshalledInstance.FORMAT_JOSS);
    }

    /**
     * Format-aware constructor (STD-008 sec.18.3): subclasses whose codec uses a non-JOSS
     * wire format (e.g. {@code AtomicDerInvocationDispatcher}) pass their
     * {@link MarshallingFormat} payload identifier so an in-band {@code MarshallingFormat}
     * requirement can be verified at export and stripped before the transport check.
     *
     * @param marshallingFormat the codec's payload-format identifier (must not be null)
     * @throws ExportException if a server constraint cannot be satisfied
     */
    protected BasicInvocationDispatcher(Collection methods,
				     ServerCapabilities serverCapabilities,
				     MethodConstraints serverConstraints,
				     Class permissionClass,
				     ClassLoader loader,
				     String marshallingFormat)
	throws ExportException
    {
	this(check(
		methods,
		serverCapabilities,
		serverConstraints,
		permissionClass,
		loader,
		marshallingFormat
	    )
	);
    }

    /*
    * Protects against finalizer attacks.
    */
    private static Builder check(Collection methods,
		 ServerCapabilities serverCapabilities,
		 MethodConstraints serverConstraints,
		 Class permissionClass,
		 ClassLoader loader,
		 String marshallingFormat) throws ExportException
    {
	return new Builder(methods,
		serverCapabilities,
		serverConstraints,
		permissionClass,
		loader,
		marshallingFormat
	);
    }
    
    BasicInvocationDispatcher(Builder builder){
	this.methods = builder.methods;
	this.loader = builder.loader;
	this.serverConstraints = builder.serverConstraints;
	this.permConstructor = builder.permConstructor;
	this.permUsesMethod = builder.permUsesMethod;
	this.permissions = builder.permissions;
	this.marshallingFormat = builder.marshallingFormat;
    }
    
    private static class Builder {
	Map methods;
	ClassLoader loader;
	MethodConstraints serverConstraints;
	Constructor permConstructor;
	boolean permUsesMethod;
	Map permissions;
	String marshallingFormat;

	Builder(Collection methods,
		 ServerCapabilities serverCapabilities,
		 MethodConstraints serverConstraints,
		 Class permissionClass,
		 ClassLoader loader,
		 String marshallingFormat)
	throws ExportException
	{
	    if (serverCapabilities == null) {
		throw new NullPointerException();
	    }
	    if (marshallingFormat == null) {
		throw new NullPointerException("marshallingFormat");
	    }
	    this.marshallingFormat = marshallingFormat;
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
		this.methods.put(Long.valueOf(Util.getMethodHash((Method) m)), m);
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
				     InvocationConstraints.EMPTY, marshallingFormat);
		} else {
		    Iterator iter = serverConstraints.possibleConstraints();
		    while (iter.hasNext()) {
			checkConstraints(serverCapabilities,
					 (InvocationConstraints) iter.next(), marshallingFormat);
		    }
		}
	    } catch (UnsupportedConstraintException e) {
		throw new ExportException(
		    "server does not support some constraints", e);
	    }
	}
    }

    /**
     * Check that the only unfulfilled requirements are Integrity and atomicity.
     *
     * <p>STD-008 sec.18.3: {@link MarshallingFormat} is an invocation-layer constraint
     * that the transport does not implement (and rejects as a requirement); it is
     * verified against this dispatcher's configured format and stripped before the
     * transport check.
     */
    private static void checkConstraints(ServerCapabilities serverCapabilities,
				  InvocationConstraints constraints,
				  String marshallingFormat)
	throws UnsupportedConstraintException
    {
	constraints = verifyAndStripMarshallingFormat(constraints, marshallingFormat);
	InvocationConstraints unfulfilled =
	    serverCapabilities.checkConstraints(constraints);
	for (InvocationConstraint c : unfulfilled.requirements()) {
	    if (!(c instanceof Integrity) && !(c instanceof AtomicInputValidation)) {
		throw new UnsupportedConstraintException(
			"cannot satisfy unfulfilled constraint: " + c);
	    }
	    // REMIND: support ConstraintAlternatives containing Integrity?
	}
    }

    /**
     * Returns the wire marshalling-format identifier this dispatcher's codec produces and
     * consumes (a {@link MarshallingFormat} payload id, STD-008 sec.18.3), supplied at
     * construction. The base/JOSS dispatcher uses {@link MarshalledInstance#FORMAT_JOSS};
     * {@code AtomicDerInvocationDispatcher} passes the JGDMS-STD-006/DER format via the
     * format-aware superclass constructor. (Supplied via the constructor rather than
     * overridden, because the export-time constraint check runs in a static Builder before
     * the instance exists.)
     *
     * @return the payload-format identifier; never {@code null}.
     */
    protected final String marshallingFormat() {
	return marshallingFormat;
    }

    /**
     * Verifies every required {@link MarshallingFormat} in {@code sc} matches
     * {@code marshallingFormat} -- throwing {@link UnsupportedConstraintException} on a
     * mismatch -- and returns {@code sc} with all {@code MarshallingFormat} entries removed
     * (requirements and preferences), so they are not passed to the transport (which does
     * not implement them). A matching required format is satisfied by configuration; a
     * preferred format that does not match is simply not applied (the codec is fixed at
     * export time).
     */
    private static InvocationConstraints verifyAndStripMarshallingFormat(
	    InvocationConstraints sc, String marshallingFormat)
	throws UnsupportedConstraintException
    {
	boolean hasFormat = false;
	for (InvocationConstraint c : sc.requirements()) {
	    if (c instanceof MarshallingFormat) {
		hasFormat = true;
		if (!marshallingFormat.equals(((MarshallingFormat) c).getFormat())) {
		    throw new UnsupportedConstraintException(
			"cannot satisfy required " + c + "; this service's marshalling format is "
			+ marshallingFormat);
		}
	    }
	}
	for (InvocationConstraint c : sc.preferences()) {
	    if (c instanceof MarshallingFormat) { hasFormat = true; break; }
	}
	if (!hasFormat) return sc;
	Collection<InvocationConstraint> reqs = new LinkedList<InvocationConstraint>();
	for (InvocationConstraint c : sc.requirements()) {
	    if (!(c instanceof MarshallingFormat)) reqs.add(c);
	}
	Collection<InvocationConstraint> prefs = new LinkedList<InvocationConstraint>();
	for (InvocationConstraint c : sc.preferences()) {
	    if (!(c instanceof MarshallingFormat)) prefs.add(c);
	}
	return new InvocationConstraints(reqs, prefs);
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
     * <code>0x00</code>, <code>0x01</code>, or <code>0x02</code>, two byte values of <code>0x00</code> (indicating
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
     * <li>If the version byte is <code>0x01</code>, a second byte
     * specifying object integrity and a third byte
     * specifying input validation are read from the same stream.  If any
     * exception is thrown when reading these bytes, the inbound request is
     * aborted and this method returns.  Object integrity will be enforced
     * if the second value read is not <code>0x00</code>, but will not be enforced
     * if the second value is <code>0x00</code>. An {@link
     * net.jini.io.context.IntegrityEnforcement} element is then added to
     * the server context, reflecting whether or not object integrity is
     * being enforced.  Input validation will be enforced if the third value
     * read is not <code>0x00</code>, but will not be enforced if the second
     * value is <code>0x00</code>.  An {@link AtomicInputValidation} element is
     * then added to the server context, reflecting whether or not input validation
     * is being enforced.
     *
     * <li>If the version byte is <code>0x02</code>, integrity, atomicValidation,
     * one or more user Subject blocks, and a serialized
     * {@link java.security.AccessControlContext} are read in addition to the
     * above.  The user Subjects are reconstructed into read-only
     * {@link javax.security.auth.Subject} instances stored together in the
     * server context as a single {@link net.jini.jeri.ClientUserSubject}
     * element; all Subjects are accessible via
     * {@link net.jini.io.context.ClientUserSubject#getUserSubjects()} and
     * the outermost (first) Subject via
     * {@link net.jini.io.context.ClientUserSubject#getUserSubject()}.
     * The wire format of the user-Subject block is:
     * <pre>
     *   subjectCount    : unsigned 16-bit big-endian
     *   for each Subject:
     *     principalCount  : unsigned 16-bit big-endian
     *     for each principal:
     *       classNameLength : unsigned 16-bit big-endian
     *       classNameBytes  : UTF-8
     *       nameLength      : unsigned 16-bit big-endian
     *       nameBytes       : UTF-8
     * </pre>
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
    @Override
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
	InputStream rin;
	boolean integrity;
	boolean supportsAtomicValidation;
	boolean atomicValidation = false;
	boolean hasUserSubjects = false;
        boolean hasSerializedAcc = false;
        AccessControlContext remoteIdentityContext = null;
	try {
	    rin = request.getRequestInputStream();
	    int versionByte = rin.read();
	    switch (versionByte) {
		case VERSION_WITH_PRINCIPALS_AND_ACC:
		    supportsAtomicValidation = true;
		    hasUserSubjects = true;
                    hasSerializedAcc = true;
		    break;
		case VERSION:
		    supportsAtomicValidation = true;
		    break;
		case PREVIOUS_VERSION:
		    supportsAtomicValidation = false;
		    break;
		case -1:
		    throw new EOFException();
		default:
		    rin.close();
		    OutputStream ros = request.getResponseOutputStream();
		    ros.write(MISMATCH);
		    /* TODO: Confirm if version was to be written in spec, or just 0x00 
		     * Currently test just checks for 0x00  */		    
		    ros.write(PREVIOUS_VERSION); 
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
	    if (supportsAtomicValidation){
		switch (rin.read()) {
		case 0:
		    atomicValidation = false;
		    break;
		case -1:
		    throw new EOFException();
		default:
		    atomicValidation = true;
		}
	    }
	    if (hasUserSubjects) {
		List<Subject> userSubjects = readUserSubjects(rin);
		if (!userSubjects.isEmpty()) {
		    addUserSubjectsToContext(context, userSubjects);
		}
	    }
            if (hasSerializedAcc) {
                byte[] accBytes = readByteArrayBlock(rin);
                if (accBytes.length > 0) {
                    remoteIdentityContext =
                        AccessControlContextSerializer.unmarshalForTransport(
                            accBytes, getClientSubject());
                }
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
	Util.populateContext(context, integrity, atomicValidation);
	context.add(serverConstraints);
	ObjectInput in = null;
	
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
		Collection requirements = new LinkedList(sc.requirements());
		requirements.add(Integrity.YES);
		sc = new InvocationConstraints(requirements, sc.preferences());
	    }
	    if (atomicValidation && !sc.requirements().contains(AtomicInputValidation.YES)){
		Collection requirements = new LinkedList(sc.requirements());
		requirements.add(AtomicInputValidation.YES);
		sc = new InvocationConstraints(requirements, sc.preferences());
	    }
	    // STD-008 sec.18.3: MarshallingFormat is satisfied by this dispatcher's configured
	    // codec (verified at export); strip it before the transport check, which does not
	    // implement it.
	    sc = verifyAndStripMarshallingFormat(sc, marshallingFormat());
	    InvocationConstraints unfulfilled = request.checkConstraints(sc);
	    for (Iterator<InvocationConstraint> i = unfulfilled.requirements().iterator();
		 i.hasNext();)
	    {
		InvocationConstraint c = i.next();
		if (c instanceof Integrity) {
		    if (!integrity && c == Integrity.YES) 
			unsupportedConstraint(c);
		} else if (c instanceof AtomicInputValidation) {
		    if (!atomicValidation && c == AtomicInputValidation.YES) 
			unsupportedConstraint(c);
		} else {
		    unsupportedConstraint(c);
		}
		// REMIND: support ConstraintAlternatives containing Integrity?
	    }
	    
	    checkAccess(impl, method, sc, context);
	    
	    /*
	     * Unmarshal arguments.
	     */
	    Object[] args = unmarshalArguments(impl, method, in, context);
	    // Fire end-of-decode-unit completion callbacks (e.g. the client DGC batched
	    // dirty for live refs received as arguments) after the whole argument sequence
	    // is read and BEFORE the reply (the argument ack) is written; no-op on the
	    // JOSS/atomic path (SRC RR-116 dirty-before-ack).
	    if (in instanceof AtomicObjectInput) ((AtomicObjectInput) in).endDecodeUnit();
	    if (logger.isLoggable(Level.FINE)) {
		logCall(impl, method, args);
	    }
	
	    /*
	     * Invoke method on remote object under both the server's worker
	     * Subject and (where present) all of the client's user Subjects.
	     *
	     * Subject.doAs(workerSubject, ...)           ← sets ACC; virtual threads inherit
	     *   Subject.callAs(userSubjects[0], ...)     ← outermost user Subject; dispatch thread only
	     *     Subject.callAs(userSubjects[1], ...)   ← next user Subject, if present
	     *       ...
	     *         invoke(...)
	     *
	     * Virtual threads spawned during the invocation therefore inherit the
	     * SERVER's worker identity (from the ACC), not the client's user
	     * identity.  Server code that needs to propagate user Subjects
	     * across a thread boundary must capture Subject.currentAll() and
	     * re-establish them with nested Subject.callAs calls in the new thread.
	     */
	    try {
		returnValue = invokeWithClientSubject(impl, method, args, context, remoteIdentityContext);
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
	    ObjectOutput out =
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

    private void unsupportedConstraint(InvocationConstraint c) 
	    throws UnsupportedConstraintException 
    {
	throw new UnsupportedConstraintException(
			    "cannot satisfy unfulfilled constraint: " + c);
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
    protected ObjectInput
        createMarshalInputStream(Object impl,
				 final InboundRequest request,
				 final boolean integrity,
				 Collection context)
	throws IOException
    {
	final ClassLoader streamLoader = getStreamLoader(impl);
	
	final Collection unmodContext = Collections.unmodifiableCollection(context);
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction<ObjectInputStream>(){
		
		@Override
		public ObjectInputStream run() throws Exception {
		    ObjectInputStream in;
		    for (Object o : unmodContext){
			if (o instanceof AtomicValidationEnforcement &&
				((AtomicValidationEnforcement) o).enforced())
			{
			    unsupportedConstraint(AtomicInputValidation.YES);
			}
		    }
		    in = new MarshalInputStream(request.getRequestInputStream(),
					       streamLoader, integrity,
					       streamLoader, unmodContext);
		    ((MarshalInputStream)in).useCodebaseAnnotations();
		    return in;
		}
    
	    });
	} catch (PrivilegedActionException ex) {
	    Exception cause = ex.getException();
	    if (cause instanceof IOException) throw (IOException) cause;
	    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
	    throw new IOException ("Unable to create ObjectOutputStream ",ex);
	}
    }
	
    ClassLoader getStreamLoader(Object impl){
	final ClassLoader streamLoader;
	if (loader != null) {
	    streamLoader = getClassLoader();
	} else {
	    SecurityManager security = System.getSecurityManager();
	    if (security != null) {
		security.checkPermission(getClassLoaderPermission);
	    }
	    streamLoader = impl.getClass().getClassLoader();
	}
	return streamLoader;
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
    protected ObjectOutput
        createMarshalOutputStream(final Object impl,
				  Method method,
				  final InboundRequest request,
				  final Collection context)
	throws IOException
    {
	if (impl == null) {
	    throw new NullPointerException();
	}
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction<ObjectOutputStream>(){
		
		@Override
		public ObjectOutputStream run() throws IOException {
		    OutputStream out = request.getResponseOutputStream();
		    Collection unmodContext = Collections.unmodifiableCollection(context);
		    for (Object o : unmodContext){
			if (o instanceof AtomicValidationEnforcement &&
				((AtomicValidationEnforcement) o).enforced())
			{
			    unsupportedConstraint(AtomicInputValidation.YES);
			}
		    }
		    return new MarshalOutputStream(out, unmodContext);
		}
							  
	    });
	} catch (PrivilegedActionException ex) {
	    Exception cause = ex.getException();
	    if (cause instanceof IOException) throw (IOException) cause;
	    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
	    throw new IOException ("Unable to create ObjectOutputStream ",ex);
	}
	
	
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
	// CodebaseAccessor is the bootstrap protocol used to fetch and verify a
	// proxy's codebase before trust is established.  It is governed by
	// BootstrapPermission (client-side, in PreferredProxyCodebaseProvider),
	// codebase digest verification, and server authentication -- not by the
	// service's per-method AccessPermission.  Exempt it so bootstrapping needs
	// no per-principal AccessPermission grant.
	if (CodebaseAccessor.class.equals(method.getDeclaringClass())) {
	    return;
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
        SecurityManager sm = System.getSecurityManager();
	if (sm == null) {
	    return;
	}
	ProtectionDomain pd;
	if (client == null) {
	    pd = emptyPD;
	} else {
	    pd = domains.computeIfAbsent(client, s -> {
		Set<Principal> set = s.getPrincipals();
		Principal[] prins = set.toArray(new Principal[0]);
		return new ProtectionDomain(emptyCS, null, null, prins);
	    });
	}
	// XXX what about logging
	if (logger.isLoggable(Level.FINEST)){
            Policy p = Policy.getPolicy();
            logger.log(Level.FINEST, "SecurityManager: " + sm + "\nPolicy: " + p +
                    "\nProtectionDomain: " + pd);
        }
	/*
	 * Gate 1 (workload): evaluate the permission against an ACC of the client
	 * ProtectionDomain ALONE.  Route through Security.checkPermission, which
	 * builds that ACC inside a doPrivileged so the "createAccessControlContext"
	 * authorization is satisfied by this library's frame only.  Calling
	 * AccessControlContext.create() off the bare dispatch stack instead would
	 * make createAccessControlContext go viral -- every caller up the stack
	 * would need it, forcing a global grant -- and would merge the dispatch
	 * stack domains into the context, which is what previously denied a
	 * legitimately-authorized client (e.g. getAdmin) because an unrelated stack
	 * domain lacked the grant.
	 */
	Security.checkPermission(permission, pd);
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
				     ObjectInput in,
				     Collection context)
        throws IOException, NoSuchMethodException, ClassNotFoundException
    {
	if (impl == null || context == null) {
	    throw new NullPointerException();
	}
	long hash = in.readLong();
	Method method = (Method) methods.get(Long.valueOf(hash));
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
					  ObjectInput in,
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
		"method not public or set accessible: " + method.toString());
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
				 ObjectOutput out,
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
				ObjectOutput out,
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
     * Return the current worker (TLS-authenticated) client subject, or
     * {@code null} if not currently executing a remote call.
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

    /**
     * Returns all user Subjects assembled from the principals transmitted in
     * the wire-protocol header, or an empty array if no user Subjects were
     * present in the request.
     */
    private static Subject[] getUserSubjects() {
	return AccessController.doPrivileged((PrivilegedAction<Subject[]>) () -> {
	    try {
		ClientUserSubject cus = (ClientUserSubject)
		    ServerContext.getServerContextElement(ClientUserSubject.class);
		return cus != null ? cus.getUserSubjects() : new Subject[0];
	    } catch (ServerNotActiveException e) {
		return new Subject[0];
	    }
	});
    }

    /**
     * Invokes the specified method under the server's worker Subject and, where
     * present, the client's user Subject(s).
     *
     * <p>On <b>DirtyChai</b> JDK (where {@code Subject.callAs(Callable, Subject...)}
     * exists), all user Subjects are passed in a single call:
     * <pre>
     *   Subject.doAs(workerSubject, () -&gt; {              // ACC; inherited by virtual threads
     *       Subject.callAs(action, userSubjects[0..n]);  // all user Subjects at once
     *   });
     * </pre>
     *
     * <p>On a <b>standard JDK</b> (no varargs {@code callAs}), only the first
     * user Subject is used.  Nesting multiple single-Subject {@code callAs}
     * calls is incorrect because each inner call shadows the outer one,
     * leaving only the innermost Subject visible via {@code Subject.current()}:
     * <pre>
     *   Subject.doAs(workerSubject, () -&gt; {         // ACC; inherited by virtual threads
     *       Subject.callAs(userSubjects[0], () -&gt; { // first (only) user Subject
     *           invoke(...)
     *       });
     *   });
     * </pre>
     *
     * <p>The outer {@code Subject.doAs} places the server's TLS-verified
     * worker identity into the {@code AccessControlContext}.  Any virtual
     * threads spawned during {@link #invoke} inherit that ACC and therefore
     * observe the <em>server's</em> worker identity, not the client's.
     *
     * <p>When no subjects are present, {@link #invoke} is called directly.
     *
     * <p>All exceptions thrown by {@link #invoke} are faithfully re-thrown.</p>
     *
     * @param impl the remote object
     * @param method the method to invoke
     * @param args the method arguments
     * @param context the server context
     * @return the result of the method invocation
     * @throws Throwable any exception thrown by the method
     */
    private Object invokeWithClientSubject(final Remote impl,
					   final Method method,
					   final Object[] args,
					   final Collection context,
                                           final AccessControlContext remoteIdentityContext)
	throws Throwable
    {
	final Subject workerSubject = getClientSubject();
	final Subject[] userSubjects = getUserSubjects();

	if (workerSubject == null && userSubjects.length == 0) {
	    return invoke(impl, method, args, context);
	}

	// Capture throwable and return value from inside the lambda nesting.
	final Object[]    result = { null };
	final Throwable[] thrown = { null };

	// The innermost action: invoke() → capture result or exception.
	final Callable<Void> dispatchAction = () -> {
	    try {
		result[0] = invoke(impl, method, args, context);
	    } catch (Throwable th) {
		thrown[0] = th;
	    }
	    return null;
	};
        
        final Callable<Void> dispatchWithContext;
        if (remoteIdentityContext != null) {
            dispatchWithContext = () -> {
                AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                    try {
                        dispatchAction.call();
                    } catch (Exception e) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "Exception dispatching with reconstructed remote ACC", e);
                        }
                        if (thrown[0] == null) thrown[0] = e;
                    }
                    return null;
                }, remoteIdentityContext);
                return null;
            };
        } else {
            dispatchWithContext = dispatchAction;
        }

	// Build the Subject.callAs wrapper around dispatchWithContext.
	//
	// On DirtyChai the varargs Subject.callAs(Callable, Subject...) method
	// is available: invoke it once with all user Subjects so the JVM can
	// establish them together rather than nesting single-Subject calls.
	//
	// On a standard JDK only Subject.callAs(Callable, Subject) exists.
	// Nesting multiple callAs calls is incorrect because each inner call
	// shadows the outer one; only the first (outermost) Subject is visible
	// via Subject.current().  We therefore use only userSubjects[0].
	final Callable<Void> dispatchWithUsers;
	if (userSubjects.length == 0) {
	    dispatchWithUsers = dispatchWithContext;
	} else if (CALL_AS_MULTI_SUBJECT != null && userSubjects.length > 1) {
	    // DirtyChai path: single varargs call with all subjects.
	    // The single-Subject case (length == 1) is handled by the else branch
	    // below, which is identical in effect whether or not DirtyChai is present.
	    dispatchWithUsers = () -> {
		try {
		    CALL_AS_MULTI_SUBJECT.invoke(null, dispatchWithContext,
						(Object) userSubjects);
		} catch (InvocationTargetException ite) {
		    Throwable cause = ite.getCause();
		    if (cause instanceof Exception) throw (Exception) cause;
		    if (cause instanceof Error)     throw (Error)     cause;
		    // Rare: cause is a raw Throwable (neither Exception nor Error).
		    // Wrap in InvocationTargetException (itself an Exception) so the
		    // caller still receives a meaningful stack trace.
		    throw ite;
		} catch (IllegalAccessException iae) {
		    // Should never happen: the method is public.
		    // Re-wrap so the Callable's Exception contract is honoured.
		    throw new IllegalStateException(
			"Unexpected access denial invoking Subject.callAs", iae);
		}
		return null;
	    };
	} else {
	    // Standard JDK path: use only the first Subject.
	    final Subject first = userSubjects[0];
	    dispatchWithUsers = () -> Subject.callAs(first, dispatchWithContext);
	}

	if (workerSubject != null) {
	    /*
	     * Worker subject is present: Subject.doAs places the server's TLS
	     * worker identity into the AccessControlContext so that any virtual
	     * threads spawned during invoke() inherit it (not the client's).
	     * This ACC-inheritance semantic is precisely why doAs is used here;
	     * Subject.callAs (ScopedValue-based) does not propagate to new threads.
	     *
	     * When user subjects are also present, wrap dispatchWithContext in
	     * the chain of Subject.callAs calls built above.
	     */
	    Subject.doAs(workerSubject, (PrivilegedAction<Void>) () -> {
		try {
		    dispatchWithUsers.call();
		} catch (Exception e) {
		    if (thrown[0] == null) thrown[0] = e;
		}
		return null;
	    });
	} else {
	    // User subjects only: callAs chain establishes Subject.current()
	    // via ScopedValue for the dispatch thread.
	    try {
		dispatchWithUsers.call();
	    } catch (Exception e) {
		if (thrown[0] == null) thrown[0] = e;
	    }
	}

	if (thrown[0] != null) {
	    throw thrown[0];
	}
	return result[0];
    }

    /* ---------------------------------------------------------------------- */
    /* User-Subject helpers for protocol version 0x02                          */
    /* ---------------------------------------------------------------------- */

    /**
     * Reads the multi-Subject block written by
     * {@link BasicInvocationHandler#writeUserSubjects} from the request
     * input stream.  The format is:
     * <pre>
     *   subjectCount      : unsigned 16-bit big-endian
     *   for each Subject:
     *     principalCount  : unsigned 16-bit big-endian
     *     for each principal:
     *       classNameLength : unsigned 16-bit big-endian
     *       classNameBytes  : UTF-8
     *       nameLength      : unsigned 16-bit big-endian
     *       nameBytes       : UTF-8
     *     jwtCount        : unsigned 8-bit (0 = no raw JWTs for this Subject)
     *     for each JWT:
     *       jwtLength     : unsigned 32-bit big-endian
     *       jwtBytes      : UTF-8 (raw JWT compact serialization)
     * </pre>
     *
     * <p>Each principal is reconstructed by calling
     * {@code new ClassName(name)} via reflection.  Only classes that are
     * reachable from the platform (bootstrap/extension) class loader are
     * accepted; unknown class names result in a
     * {@link RemotePrincipal} placeholder that preserves the wire data
     * without loading untrusted code.
     *
     * <p>If a {@link JwtVerifier} is registered via
     * {@link #setJwtVerifier(JwtVerifier)} and the Subject carries one or more
     * JWT tokens, each token is verified (with connection-level caching).  A
     * verification failure throws {@link IOException}, aborting the request.
     *
     * <p>To guard against malicious or malformed input, at most
     * {@value #MAX_USER_SUBJECTS} Subjects and at most
     * {@value #MAX_USER_PRINCIPALS} principals per Subject are accepted; each
     * UTF-8 string field is limited to {@value #MAX_STRING_BYTES} bytes; at
     * most {@value #MAX_JWT_PER_SUBJECT} JWT tokens are accepted per Subject,
     * each capped at {@value #MAX_JWT_BYTES} bytes.
     * Insertion order is preserved via {@link java.util.LinkedHashSet}.
     *
     * @throws IOException if any count or string length exceeds the
     *         respective limit, if the stream ends prematurely, or if JWT
     *         verification fails
     */
    private static List<Subject> readUserSubjects(InputStream in)
	throws IOException
    {
	int subjectCount = readUnsignedShort(in);
	if (subjectCount == 0) {
	    return Collections.emptyList();
	}
	if (subjectCount > MAX_USER_SUBJECTS) {
	    throw new IOException(
		"User-Subject count " + subjectCount
		+ " exceeds limit of " + MAX_USER_SUBJECTS);
	}
	List<Subject> subjects = new ArrayList<>(subjectCount);
	for (int si = 0; si < subjectCount; si++) {
	    int principalCount = readUnsignedShort(in);
	    if (principalCount > MAX_USER_PRINCIPALS) {
		throw new IOException(
		    "User-principal count " + principalCount
		    + " in Subject " + si + " exceeds limit of " + MAX_USER_PRINCIPALS);
	    }
	    Set<Principal> principals = new LinkedHashSet<>((int)(principalCount / 0.75) + 1);
	    for (int i = 0; i < principalCount; i++) {
		String className = readUtf8Prefixed(in, MAX_STRING_BYTES);
		String name      = readUtf8Prefixed(in, MAX_STRING_BYTES);
		Principal p = instantiatePrincipal(className, name);
		principals.add(p);
	    }
	    // JWT block (Option D, Work Item 44): read jwtCount raw tokens.
	    int jwtCount = in.read();
	    if (jwtCount < 0) throw new EOFException();
	    if (jwtCount > MAX_JWT_PER_SUBJECT) {
		throw new IOException(
		    "JWT token count " + jwtCount
		    + " in Subject " + si + " exceeds limit of " + MAX_JWT_PER_SUBJECT);
	    }
	    for (int ji = 0; ji < jwtCount; ji++) {
		String rawJwt = readJwtBytes(in);
		verifyJwtWithCache(rawJwt, si, ji);
	    }
	    subjects.add(new Subject(true, principals,
				     Collections.emptySet(), Collections.emptySet()));
	}
	return subjects;
    }

    /**
     * Reads a 4-byte big-endian length-prefixed JWT bytes block and returns
     * the raw JWT compact-serialization string.
     */
    private static String readJwtBytes(InputStream in) throws IOException {
	int b1 = in.read();
	int b2 = in.read();
	int b3 = in.read();
	int b4 = in.read();
	if ((b1 | b2 | b3 | b4) < 0) throw new EOFException();
	int len = ((b1 & 0xFF) << 24)
		| ((b2 & 0xFF) << 16)
		| ((b3 & 0xFF) << 8)
		| (b4 & 0xFF);
	if (len > MAX_JWT_BYTES) {
	    throw new IOException("JWT token length " + len
		    + " exceeds maximum of " + MAX_JWT_BYTES + " bytes");
	}
	if (len == 0) return "";
	byte[] bytes = new byte[len];
	int remaining = len;
	int offset = 0;
	while (remaining > 0) {
	    int read = in.read(bytes, offset, remaining);
	    if (read < 0) throw new EOFException();
	    offset += read;
	    remaining -= read;
	}
	return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Verifies a raw JWT token using the registered {@link JwtVerifier}, with
     * connection-level caching keyed on the raw token string.
     *
     * <p>If no custom verifier is registered ({@link #jwtVerifier} is
     * {@code null}) the {@link #DEFAULT_JWT_VERIFIER} is used, which performs
     * structural claim checks ({@code exp}, {@code iat}) without JWKS network
     * calls.  This closes the default-path gap (§2.3): a peer that presents a
     * JWT whose {@code exp} claim has elapsed is rejected even without an
     * explicit verifier registration.
     *
     * <p>Cache entries expire when the token's own {@code exp} claim is
     * passed; an {@link Instant#MIN} sentinel is stored when the {@code exp}
     * claim cannot be parsed, causing re-verification on each call.
     *
     * @throws IOException wrapping the {@link JwtVerificationException} if
     *         the verifier rejects the token
     */
    private static void verifyJwtWithCache(String rawJwt, int subjectIdx, int jwtIdx)
	    throws IOException
    {
	JwtVerifier verifier = jwtVerifier;
	if (verifier == null) verifier = DEFAULT_JWT_VERIFIER; // fallback: structural claims only

	Instant cachedExp = JWT_VERIFICATION_CACHE.get(rawJwt);
	if (cachedExp != null && Instant.now().isBefore(cachedExp)) {
	    return; // Still within cached validity window — skip re-verification.
	}

	try {
	    verifier.verify(rawJwt);
	} catch (JwtVerificationException e) {
	    throw new IOException(
		"JWT verification failed for Subject[" + subjectIdx
		+ "] JWT[" + jwtIdx + "]: " + e.getMessage(), e);
	}

	// Cache the result using the token's exp claim as the TTL.
	Instant exp = extractJwtExp(rawJwt);
	if (exp == null) {
	    // No parseable exp → do not cache; re-verify on every call.
	    return;
	}
	if (JWT_VERIFICATION_CACHE.size() >= JWT_CACHE_MAX_SIZE) {
	    // Prune expired entries; clear entirely if still full.
	    Instant now = Instant.now();
	    JWT_VERIFICATION_CACHE.entrySet().removeIf(e -> !now.isBefore(e.getValue()));
	    if (JWT_VERIFICATION_CACHE.size() >= JWT_CACHE_MAX_SIZE) {
		JWT_VERIFICATION_CACHE.clear();
	    }
	}
	JWT_VERIFICATION_CACHE.put(rawJwt, exp);
    }

    /**
     * Extracts the {@code exp} epoch-seconds claim from a JWT compact string
     * using minimal Base64url decode + JSON scan.  Returns {@code null} if the
     * claim is absent or cannot be parsed.
     */
    private static Instant extractJwtExp(String rawJwt) {
	if (rawJwt == null || rawJwt.isEmpty()) return null;
	int firstDot  = rawJwt.indexOf('.');
	int secondDot = rawJwt.indexOf('.', firstDot + 1);
	if (firstDot < 0 || secondDot <= firstDot) return null;
	try {
	    byte[] payloadBytes = Base64.getUrlDecoder()
		    .decode(rawJwt.substring(firstDot + 1, secondDot));
	    String json = new String(payloadBytes, StandardCharsets.UTF_8);
	    int idx = json.indexOf("\"exp\"");
	    if (idx < 0) return null;
	    int colon = json.indexOf(':', idx + 5);
	    if (colon < 0) return null;
	    int start = colon + 1;
	    while (start < json.length() && json.charAt(start) == ' ') start++;
	    int end = start;
	    while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
	    if (end == start) return null;
	    return Instant.ofEpochSecond(Long.parseLong(json.substring(start, end)));
	} catch (Exception e) {
	    return null;
	}
    }

    /**
     * Instantiates a {@link Principal} for the supplied wire-supplied class name
     * and name string.  Only class names present in the pre-built
     * {@link #PRINCIPAL_CTORS} allowlist are resolved; any other class name
     * returns a {@link RemotePrincipal} placeholder without performing any
     * class loading, preventing remote-controlled CPU DoS via class-loading
     * lock acquisition.
     */
    private static Principal instantiatePrincipal(String className, String name) {
	Constructor<? extends Principal> ctor = PRINCIPAL_CTORS.get(className);
	if (ctor == null) return new RemotePrincipal(className, name);
	try { return ctor.newInstance(name); }
	catch (Exception e) { return new RemotePrincipal(className, name); }
    }

    /**
     * Server context element carrying all user Subjects assembled from the
     * principals transmitted in the JERI request header.
     */
    private static final class UserSubjectImpl implements ClientUserSubject {
	private final Subject[] userSubjects;
	UserSubjectImpl(List<Subject> userSubjects) {
	    this.userSubjects = userSubjects.toArray(new Subject[0]);
	}
	public Subject[] getUserSubjects() { return userSubjects.clone(); }
    }

    /**
     * Creates read-only user Subjects from {@code userSubjectList} and adds a
     * {@link ClientUserSubject} element to {@code context}.
     *
     * <p>Each Subject in the list is already read-only and principal-only;
     * they are kept completely separate from the worker Subject held in the
     * existing {@link ClientSubject} context element.  The dispatcher later
     * wraps the invocation in a chain of {@code Subject.callAs} calls,
     * ensuring virtual threads inherit only the server's worker identity.
     */
    @SuppressWarnings("unchecked")
    private static void addUserSubjectsToContext(Collection context,
						 List<Subject> userSubjectList)
    {
	context.add(new UserSubjectImpl(userSubjectList));
    }

    private static int readUnsignedShort(InputStream in) throws IOException {
	int hi = in.read();
	int lo = in.read();
	if ((hi | lo) < 0) throw new EOFException();
	return (hi << 8) | lo;
    }

    private static String readUtf8Prefixed(InputStream in, int maxBytes)
	throws IOException
    {
	int len = readUnsignedShort(in);
	if (len == 0) return "";
	if (len > maxBytes) {
	    throw new IOException(
		"String field length " + len
		+ " exceeds limit of " + maxBytes + " bytes");
	}
	byte[] bytes = new byte[len];
	int remaining = len;
	int offset = 0;
	while (remaining > 0) {
	    int read = in.read(bytes, offset, remaining);
	    if (read < 0) throw new EOFException();
	    offset += read;
	    remaining -= read;
	}
	return new String(bytes, StandardCharsets.UTF_8);
    }
    
    private static byte[] readByteArrayBlock(InputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        if ((b1 | b2 | b3 | b4) < 0) throw new EOFException();
        int len = ((b1 & 0xFF) << 24)
                | ((b2 & 0xFF) << 16)
                | ((b3 & 0xFF) << 8)
                | (b4 & 0xFF);
        if (len < 0 || len > MAX_ACC_BLOCK_BYTES) {
            throw new IOException("invalid ACC block length " + len
                    + "; maximum allowed is " + MAX_ACC_BLOCK_BYTES);
        }
        if (len == 0) return new byte[0];
        byte[] out = new byte[len];
        int remaining = len;
        int offset = 0;
        while (remaining > 0) {
            int read = in.read(out, offset, remaining);
            if (read < 0) throw new EOFException();
            offset += read;
            remaining -= read;
        }
        return out;
    }

    /**
     * Placeholder Principal used when the class named on the wire is not
     * available from the platform class loader.  The class name and name are
     * preserved so that policy rules that match on
     * {@code RemotePrincipal} can still inspect the raw data.
     */
    static final class RemotePrincipal implements Principal {
	private final String className;
	private final String principalName;

	RemotePrincipal(String className, String principalName) {
	    this.className      = className;
	    this.principalName  = principalName;
	}

	/** Returns {@code "className:name"} for display and policy matching. */
	public String getName() {
	    return className + ":" + principalName;
	}

	/** Returns the original class name as transmitted on the wire. */
	public String getPrincipalClassName() {
	    return className;
	}

	/** Returns the principal name as transmitted on the wire. */
	public String getPrincipalName() {
	    return principalName;
	}

	public boolean equals(Object o) {
	    if (o == this) return true;
	    if (!(o instanceof RemotePrincipal)) return false;
	    RemotePrincipal other = (RemotePrincipal) o;
	    return className.equals(other.className)
		&& principalName.equals(other.principalName);
	}

	public int hashCode() {
	    return className.hashCode() * 31 + principalName.hashCode();
	}

	public String toString() {
	    return "RemotePrincipal[" + className + ":" + principalName + "]";
	}
    }
}
