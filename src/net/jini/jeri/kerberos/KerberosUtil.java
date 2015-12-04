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
package net.jini.jeri.kerberos;

import org.apache.river.logging.Levels;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.ref.ReferenceQueue;
import java.net.Socket;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogRecord;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.security.auth.AuthPermission;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.Subject;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.io.UnsupportedConstraintException;
import net.jini.security.AuthenticationPermission;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.MessageProp;
import org.ietf.jgss.Oid;

/**
 * Utility class for the Kerberos provider.
 *
 * 
 * @since 2.0
 */
class KerberosUtil {

    /**
     * Oid used to represent the Kerberos v5 GSS-API mechanism,
     * defined as in RFC 1964.
     */
    static final Oid krb5MechOid;
    
    /**
     * Oid used to represent the name syntax in Kerberos v5 GSS-API
     * mechanism. Examples: "joe@KERBEROSREALM" or
     * "ftp/myhost.foo.com@KERBEROSREALM".
     */
    static final Oid krb5NameType;

    static {
	try {
	    krb5MechOid = new Oid("1.2.840.113554.1.2.2");
	    krb5NameType = new Oid("1.2.840.113554.1.2.2.1");
	} catch (GSSException e) {
	    throw new ExceptionInInitializerError(e);
	}
    }

    static final InvocationConstraints INTEGRITY_REQUIRED_CONSTRAINTS =
        new InvocationConstraints(Integrity.YES, null);

    static final InvocationConstraints INTEGRITY_PREFERRED_CONSTRAINTS =
        new InvocationConstraints(null, Integrity.YES);

    /** Field used by ConfigIter to generate configs */
    private static final boolean BOOL_TABLE[] = new boolean[] {false, true};

    /** Map constraints to other constraints they depend on */
    private static final Map depends = new HashMap();

    static {
	InvocationConstraint[] deps = new InvocationConstraint[0];
	depends.put(ConnectionAbsoluteTime.class, deps);
	depends.put(ConnectionRelativeTime.class, deps);
	depends.put(Integrity.class, deps);
	depends.put(Confidentiality.class, deps);
	depends.put(ClientAuthentication.class, deps);
	depends.put(ServerAuthentication.class, deps);
	deps = new InvocationConstraint[]{ClientAuthentication.YES};
	depends.put(ClientMinPrincipal.class, deps);
	depends.put(ClientMinPrincipalType.class, deps);
	depends.put(ClientMaxPrincipal.class, deps);
	depends.put(ClientMaxPrincipalType.class, deps);
	depends.put(Delegation.class, deps);
	deps = new InvocationConstraint[]{ServerAuthentication.YES};
	depends.put(ServerMinPrincipal.class, deps);
    }

    /**
     * make the null constructor private, so this class is
     * non-instantiable
     */
    private KerberosUtil() {}

    //-----------------------------------
    //     package-private methods
    //-----------------------------------

    /**
     * Test whether the caller has AuthPermission("getSubject").
     * 
     * @return true if the caller has AuthPermission("getSubject"),
     *         false otherwise.
     */
    static boolean canGetSubject() {
	try {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null)
		sm.checkPermission(new AuthPermission("getSubject"));
	    return true;
	} catch (SecurityException e) {
	    return false;
	}
    }

    /**
     * Check whether the type of the specified constraint is supported
     * by this provider.
     *
     * @param c the constraint to be tested
     * @return true if the specified constraints has a known type,
     *         false otherwise.
     */
    static boolean isSupportedConstraintType(InvocationConstraint c) {
	return depends.get(c.getClass()) != null;
    }

    /**
     * Test whether the specified constraint can possibly be supported
     * by this provider.
     *
     * @param c the constraint to be tested
     * @return true if the specified constraints can possibly be
     *         supported, false otherwise.
     */
    static boolean isSupportableConstraint(InvocationConstraint c) {

	if (c instanceof ConstraintAlternatives) {
	    Set alts = ((ConstraintAlternatives) c).elements();
	    Class type = null;
	    for (Iterator iter = alts.iterator(); iter.hasNext(); ) {
		InvocationConstraint alt= (InvocationConstraint) iter.next();
		if (type == null) {
		    type = alt.getClass();
		} else if (type != alt.getClass()) {
		    return false; // does not support heterogenous alternatives
		}
		if (isSupportableConstraint(alt))
		    return true;
	    }
	    return false;
	}

	if (!isSupportedConstraintType(c))
	    return false; // unsupported constraint type

	if (c instanceof Integrity) {
	    return c == Integrity.YES;
	} else if (c instanceof ClientAuthentication) {
	    return c == ClientAuthentication.YES;
	} else if (c instanceof ServerAuthentication) {
	    return c == ServerAuthentication.YES;
	} else if (c instanceof ClientMinPrincipal) {
	    Set elems = ((ClientMinPrincipal) c).elements();
	    if (elems.size() > 1) {
		return false; // can only authenticate as one principal
	    } else { // elems contains at least one element
		return elems.iterator().next() instanceof KerberosPrincipal;
	    }
	} else if (c instanceof ClientMinPrincipalType) {
	    Set elems = ((ClientMinPrincipalType) c).elements();
	    if (elems.size() > 1) {
		return false; // can only support one type
	    } else { // elems contains at least one element
		return elems.contains(KerberosPrincipal.class);
	    }
	} else if (c instanceof ClientMaxPrincipal) {
	    Set elems = ((ClientMaxPrincipal) c).elements();
	    for (Iterator iter = elems.iterator(); iter.hasNext(); ) {
		if (iter.next() instanceof KerberosPrincipal)
		    return true;
	    }
	    return false;
	} else if (c instanceof ClientMaxPrincipalType) {
	    Set elems = ((ClientMaxPrincipalType) c).elements();
	    return elems.contains(KerberosPrincipal.class);
	} else if (c instanceof ServerMinPrincipal) {
	    Set elems = ((ServerMinPrincipal) c).elements();
	    if (elems.size() > 1) {
		return false; // can only authenticate as one principal
	    } else { // elems contains at least one element
		return elems.iterator().next() instanceof KerberosPrincipal;
	    }
	}

	return true;
    }

    /**
     * Test whether the specified configuration is satisfiable by the
     * given constraint.
     *
     * @param config configuration to be tested
     * @param c the constraint to be tested
     * @return true if the specified configuration is allowed by
     *         the given constraint, false otherwise.
     */
    static boolean isSatisfiable(Config config, InvocationConstraint c) {

	/* Note that though some of the checks done here have already
	   been done in isSupportedConstraint(c), they have to be
	   repeated here to support ConstraintAlternatives. */

	if (c instanceof ConstraintAlternatives) {
	    Set elems = ((ConstraintAlternatives) c).elements();
	    for (Iterator iter = elems.iterator(); iter.hasNext(); ) {
		InvocationConstraint elem =
		    (InvocationConstraint) iter.next();
		if (isSatisfiable(config, elem))
		    return true;
	    }
	    return false;
	}

	if (!isSupportedConstraintType(c))
	    return false; // unsupported constraint type

	if (c instanceof Integrity) {
	    return c == Integrity.YES;
	} else if (c instanceof Confidentiality) {
	    return config.encry == (c == Confidentiality.YES);
	} else if (c instanceof ClientAuthentication) {
	    return c == ClientAuthentication.YES;
	} else if (c instanceof ServerAuthentication) {
	    return c == ServerAuthentication.YES;
	} else if (c instanceof Delegation) {
	    return config.deleg == (c == Delegation.YES);
	} else if (c instanceof ClientMinPrincipal) {
	    Set elems = ((ClientMinPrincipal) c).elements();
	    if (elems.size() > 1) {
		return false; // can only authenticate as one principal
	    } else { // elems contains at least one element
		return elems.contains(config.clientPrincipal);
	    }
	} else if (c instanceof ClientMinPrincipalType) {
	    Set elems = ((ClientMinPrincipalType) c).elements();
	    if (elems.size() > 1) {
		return false; // can only support one type
	    } else { // elems contains at least one element
		return elems.contains(KerberosPrincipal.class);
	    }
	} else if (c instanceof ClientMaxPrincipal) {
	    Set elems = ((ClientMaxPrincipal) c).elements();
	    return elems.contains(config.clientPrincipal);
	} else if (c instanceof ClientMaxPrincipalType) {
	    Set elems = ((ClientMaxPrincipalType) c).elements();
	    return elems.contains(KerberosPrincipal.class);
	} else if (c instanceof ServerMinPrincipal) {
	    Set elems = ((ServerMinPrincipal) c).elements();
	    if (elems.size() > 1) {
		return false; // can only authenticate as one principal
	    } else { // elems contains at least one element
		return elems.contains(config.serverPrincipal);
	    }
	}
	return true;
    }

    /**

     * Collect all client principal candidates from the given
     * constraint.  This method assumes homogeneous alternatives.
     *
     * @param c the given constraint
     * @param cpCandidates the set of candidates satisfiable by the
     * constraints previously checked, which new principals should be
     * added to.  This set contains no principals if no client
     * principal constraint has been checked yet.
     * @return false if the passed in constraint is {@link
     * ClientMinPrincipal} or {@link ClientMaxPrincipal}, or {@link
     * ConstraintAlternatives} whose elements are of those types, and
     * is not satisfiable regarding to the given set of candidates,
     * true other wise.
     */
    static boolean collectCpCandidates(
	InvocationConstraint c, Set cpCandidates)
    {
	boolean isPrincipalConstraint = false;
	HashSet cpset = new HashSet();
	if (c instanceof ConstraintAlternatives) {
	    Set alts = ((ConstraintAlternatives) c).elements();
	    for (Iterator iter = alts.iterator(); iter.hasNext(); ) {
		c = (InvocationConstraint) iter.next();
		if (c instanceof ClientMinPrincipal) {
		    isPrincipalConstraint = true;
		    Set elems = ((ClientMinPrincipal) c).elements();
		    Object cp = elems.iterator().next();
		    if (elems.size() > 1 || !(cp instanceof KerberosPrincipal))
			continue; // constraint unsupportable
		    cpset.add(cp);
		} else if (c instanceof ClientMaxPrincipal) {
		    isPrincipalConstraint = true;
		    Set elems = ((ClientMaxPrincipal) c).elements();
		    for (Iterator jter = elems.iterator(); jter.hasNext(); ) {
			Object elem = jter.next();
			if (elem instanceof KerberosPrincipal)
			    cpset.add(elem);
		    }
		}
	    }	    
	} else if (c instanceof ClientMinPrincipal) {
	    isPrincipalConstraint = true;
	    Set elems = ((ClientMinPrincipal) c).elements();
	    Object cp = elems.iterator().next();
	    if (elems.size() > 1 || !(cp instanceof KerberosPrincipal))
		return false; // constraint unsupportable
	    cpset.add(cp);
	} else if (c instanceof ClientMaxPrincipal) {
	    isPrincipalConstraint = true;
	    Set elems = ((ClientMaxPrincipal) c).elements();
	    for (Iterator iter = elems.iterator(); iter.hasNext(); ) {
		Object elem = iter.next();
		if (elem instanceof KerberosPrincipal)
		    cpset.add(elem);
	    }
	}

	if (isPrincipalConstraint) {
	    if (cpCandidates.size() == 0) {
		// this constraint is the 1st principal constraint checked
		if (cpset.size() > 0) {
		    cpCandidates.addAll(cpset);
		    return true;
		} else {
		    return false;
		}
	    } else { // seen other principal constraints before
		cpCandidates.retainAll(cpset);
		return cpCandidates.size() > 0;
	    }
	} else {
	    return true; // no say if not principal constraint
	}
    }

    /**
     * Check whether the caller has the AuthenticationPermission with
     * the specified principals and action.
     *
     * @param local local principal of the
     *        <code>AuthenticationPermission</code>, cannot be *
     *        <code>null<code>.
     * @param peer peer principal of the
     *        <code>AuthenticationPermission</code>.
     * @param action action of the
     *        <code>AuthenticationPermission</code>, valid values
     *        include: * "connect", "delegate", "listen", and
     *        "accept".
     * @throws SecurityException if the caller does not have the
     *         checked permission
     */
    static void checkAuthPermission(KerberosPrincipal local,
				    KerberosPrincipal peer,
				    String action)
    {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    Set localps = Collections.singleton(local);
	    Set peerps = null;
	    if (peer != null)
		peerps = Collections.singleton(peer);
	    AuthenticationPermission perm = 
		new AuthenticationPermission(localps, peerps, action);
	    sm.checkPermission(perm);
	}
    }

    /**
     * Check whether the caller has the specified
     * AuthenticationPermission.
     *
     * @param perm the AuthenticationPermission to be checked
     * @throws SecurityException if the caller does not have the
     *         checked permission
     */
    static void checkAuthPermission(AuthenticationPermission perm) {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null)
	    sm.checkPermission(perm);
    }

    /**
     * Check whether the given set of constraints contains the
     * candidate constraint.
     *
     * @param constraints the constraints to be checked
     * @param candidate candidate constraint
     * @return true if the candidate constraint is found in the give
     *         set of constraints, false otherwise.
     */
    static boolean containsConstraint(
	Set constraints, InvocationConstraint candidate)
    {
	for (Iterator iter = constraints.iterator(); iter.hasNext(); ) {
	    InvocationConstraint c = (InvocationConstraint) iter.next();
	    if (c instanceof ConstraintAlternatives) {
		Set elems = ((ConstraintAlternatives) c).elements();
		return elems.contains(candidate);
	    } else if (c.equals(candidate)) {
		return true;
	    }
	}
	return false;
    }

    /**
     * Get the GSSCredential corresponding to the given principal from
     * the given <code>Subject</code>, whose usage type is governed by
     * the usage parameter.
     * 
     * @param subj the subject from which the TGT or
     *        <code>KerberosKey</code> will be extracted to construct
     *        the GSSCredential, can not be null
     * @param principal the principal whose name will be used to
     *        construct the GSSCredential. If <code>null</code>, then
     *        a <code>null</code> name will be passed to the
     *        <code>manager</code> to allow it to choose a default.
     * @param manager the GSSManager instance that will be used to
     *        construct the GSSCredential, can not be null
     * @param usage intended usage for the GSScredential. The value of
     *        this parameter must be one of: {@link
     *        GSSCredential#INITIATE_AND_ACCEPT}, {@link
     *        GSSCredential#ACCEPT_ONLY}, and {@link
     *        GSSCredential#INITIATE_ONLY}.
     * @return the requested GSSCredential
     * @throws UnsupportedConstraintException if failed to get the
     *         requested <code>GSSCredential</code>
     */
    static GSSCredential getGSSCredential (
	final Subject subj, final KerberosPrincipal principal,
	final GSSManager manager, final int usage)
	throws GSSException
    {
	try {
	    return (GSSCredential) Subject.doAs(
                subj, new PrivilegedExceptionAction() {
		    public Object run() throws GSSException {
		        GSSName name = manager.createName(principal.getName(),
							  krb5NameType);
			return manager.createCredential(
		            name, GSSCredential.INDEFINITE_LIFETIME,
			    krb5MechOid, usage);
		    }
	        });
	} catch (PrivilegedActionException pe) {
	    throw (GSSException) pe.getException();
	}
    }

    /**
     * Only throw non-generic exception if caller has getSubject
     * permission.
     *
     * @param detailedException the real
     *        <code>UnsupportedConstraintException</code> or
     *        <code>SecurityException</code> to be thrown if caller
     *        has the "getSubject" <code>AuthPermission</code>.
     * @param genericException the generic
     *        <code>UnsupportedConstraintException</code> to be thrown
     *        if caller does not have the "getSubject"
     *        <code>AuthPermission</code>.
     */
    static void secureThrow(Exception detailedException,
			    UnsupportedConstraintException genericException)
	throws UnsupportedConstraintException
    {
	if (KerberosUtil.canGetSubject()) { // has "getSubject" permission
	    if (detailedException instanceof SecurityException) {
		throw (SecurityException) detailedException;
	    } else {
		throw (UnsupportedConstraintException) detailedException;
	    }
	} else {
	    throw genericException;
	}
    }

    /**
     * Logs a throw. Use this method to log a throw when the log
     * message needs parameters.
     *
     * @param logger logger to log to
     * @param level the log level
     * @param sourceClass class where throw occurred
     * @param sourceMethod name of the method where throw occurred
     * @param msg log message
     * @param params log message parameters
     * @param e exception thrown
     */
    static void logThrow(Logger logger, Level level, Class sourceClass,
			 String sourceMethod, String msg, Object[] params,
			 Throwable e)
    {
	LogRecord r = new LogRecord(level, msg);
	r.setLoggerName(logger.getName());
	r.setSourceClassName(sourceClass.getName());
	r.setSourceMethodName(sourceMethod);
	r.setParameters(params);
	r.setThrown(e);
	logger.log(r);
    }

    //-----------------------------------
    //    package-private sub-classes
    //-----------------------------------

    /**
     * An instances of this class records one configuration possibly
     * satisfiable by this provider.
     */
    static final class Config {
	
	/** client principal of the connection */
	KerberosPrincipal clientPrincipal;
	
	/** server principal of the connection */
	KerberosPrincipal serverPrincipal;
	
	/** whether the channel should be encrypted */
	boolean encry;
	
	/** whether client credential should be delegated to server */
	boolean deleg;

	/** number of preferences this config can satisfy */
	int prefCount;
	
	Config(KerberosPrincipal clientPrincipal,
	       KerberosPrincipal serverPrincipal,
	       boolean encry, boolean deleg)
	{
	    this.clientPrincipal = clientPrincipal;
	    this.serverPrincipal = serverPrincipal;
	    this.encry = encry;
	    this.deleg = deleg;
	}
	
	/** Returns a string representation of this configuration. */
	public String toString() {
	    return "Config[clientPrincipal=" + clientPrincipal +
		" serverPrincipal=" + serverPrincipal +
		" encry=" + encry + " deleg=" + deleg +
		" prefCount=" + prefCount + "]";
	}
    }

    /** An iterator returns all possible configs */
    static final class ConfigIter {

	private final Set clientPrincipals;
	private final KerberosPrincipal serverPrincipal;
	private Iterator cpIter;
	private final boolean canDeleg; // true if delegation allowed
	private int configId;
	private int numConfigs;

	ConfigIter(Set clientPrincipals, KerberosPrincipal serverPrincipal,
		   boolean canDeleg)
	{
	    this.clientPrincipals = clientPrincipals;
	    this.serverPrincipal = serverPrincipal;
	    this.canDeleg = canDeleg;
	    configId = 0;
	    numConfigs = clientPrincipals.size() * 2;
	    if (canDeleg)
		numConfigs *= 2;
	}

	boolean hasNext() {
	    return configId < numConfigs;
	}

	Config next() {
	    if (configId >= numConfigs)
		throw new java.util.NoSuchElementException();

	    if (configId % clientPrincipals.size() == 0)
		cpIter = clientPrincipals.iterator();

	    KerberosPrincipal cp = (KerberosPrincipal) cpIter.next();
	    int encryId = (configId / clientPrincipals.size()) % 2;
	    Config config;
	    if (canDeleg) {
		int delegId = configId / clientPrincipals.size() / 2;
		config = new Config(cp, serverPrincipal, BOOL_TABLE[encryId],
				    BOOL_TABLE[delegId]);
	    } else {
		config = new Config(cp, serverPrincipal, BOOL_TABLE[encryId],
				    false);
	    }
	    ++configId;
	    return config;
	}
    }

    /**
     * Connection class serves as the parent of connection classes
     * defined in both client and server end point classes.
     */
    static class Connection {

	/* 2 means "integrity using DES MAC of MD5 of plaintext" */
	protected static final int INTEGRITY_QOP = 2;

	/* for privacy only the default (0) is supported */
	protected static final int PRIVACY_QOP = 0;

	/** TCP socket used by this connection */
	protected final Socket sock;

	/** Input stream provided by the underlying socket */
	protected DataInputStream dis;

	/** Output stream provided by the underlying socket */
	protected DataOutputStream dos;

	/** client principal of this connection */
	KerberosPrincipal clientPrincipal; // serverPrincipal is in endpoint

	/**
	 * GSSContext instance used by this connection, it is
	 * initialized in child class
	 */
	protected GSSContext gssContext;

	/** Boolean to indicate whether traffic will be encrypted */
	protected boolean doEncryption;

	/**
	 * If this field is set to true, the initiator's credentials
	 * will be delegated to the acceptor during GSS context
	 * establishment.
	 */
	protected boolean doDelegation;

	/** logger of the connection */
	protected Logger connectionLogger;

	/** 
	 * Construct a connection object.
	 *
	 * @param sock underlying socket used by this connection
	 */
	Connection(Socket sock) throws IOException {
	    this.sock = sock;
	    dis = new DataInputStream(sock.getInputStream());
	    dos = new DataOutputStream(sock.getOutputStream());
	}

	/** Close the connection */
	public void close() {
	    connectionLogger.log(Level.FINE, "closing {0}", this);
	    try {
		sock.close();
	    } catch (IOException e) {}
	}

	/**
	 * Wrap the content of the buffer into a GSS token and write
	 * it out to the underlying socket.
	 * 
	 * @param buf the buffer whose content will be send out
	 * @param offset offset marks the start of the content to be
	 *        sent out
	 * @param len number of bytes to be sent out
	 * @throws IOException if problems encountered
	 */
	void write(byte[] buf, int offset, int len) throws IOException {
	    MessageProp prop;
	    if (doEncryption) {
		prop = new MessageProp(PRIVACY_QOP, true);
	    } else { // 2 means "integrity using DES MAC of MD5 of plaintext"
		prop = new MessageProp(INTEGRITY_QOP, false);
	    }
	    byte[] token = null;
	    try {
		try {
		    synchronized (gssContext) {
			token = gssContext.wrap(buf, offset, len, prop);
		    }
		} catch (GSSException ge) {
		    IOException ioe = new IOException(
			"Failed to wrap buf into GSS token.");
		    ioe.initCause(ge);
		    throw ioe;
		}

		if (doEncryption != prop.getPrivacy()) {
		    throw new IOException(
			"Returned token encryption property is: " +
			prop.getPrivacy() + ",\nwhile connection " +
			"encryption requirement is: " + doEncryption);
		}
	    
		if (connectionLogger.isLoggable(Level.FINEST)) {
		    connectionLogger.log(
			Level.FINEST, "wrapped " + len + " bytes (" +
			(doEncryption ? "" : "not ") + "encrypted) " +
			"into a " + token.length + " bytes token and " +
			"sending it over the network");
		}
		
		dos.writeInt(token.length);
		dos.write(token);
	    } catch (IOException ioe) {
		if (connectionLogger.isLoggable(Levels.FAILED)) {
		    logThrow(connectionLogger, Levels.FAILED,
			     this.getClass(), "write",
			     "failed to wrap buf of size {0} into a GSS " +
			     "token,\nconnection is {1},\nthrows ",
			     new Object[] {Integer.valueOf(len), this}, ioe);
		}
		throw ioe;
	    }
	}

	/** Flush the output stream used for send. */
	void flush() throws IOException {
	    dos.flush();
	}	    
	
	/**
	 * Block until a complete GSS token has been received, unwrap
	 * it, and return its content.
	 * 
	 * @return byte array of the unwrapped GSS token
	 * @throws IOException if problems encountered
	 */
	byte[] read() throws IOException {
	    try {
		MessageProp prop = new MessageProp(0, false);
		byte[] token = new byte[dis.readInt()];
		dis.readFully(token);
		
		byte[] bytes;
		try {
		    synchronized (gssContext) {
			bytes = gssContext.unwrap(
			    token, 0, token.length, prop);
		    }
		} catch (GSSException e) {
		    IOException ioe = new IOException(
			"Failed to unwrap a GSS token of length " +
			token.length);
		    ioe.initCause(e);
		    throw ioe;
		}
		
		/* this state of the connection can changed by
		   every incoming token */
		doEncryption = prop.getPrivacy();
		
		if (connectionLogger.isLoggable(Level.FINEST)) {
		    connectionLogger.log(
			Level.FINEST,  "received a " + token.length +
			" bytes token (" + (doEncryption ? "" : "not ") +
			"encrypted), " + bytes.length + " bytes when " +
			"unwrapped");
		}
		return bytes;
	    } catch (IOException ioe) {
		if (connectionLogger.isLoggable(Levels.FAILED)) {
		    logThrow(connectionLogger, Levels.FAILED,
			     this.getClass(), "read",
			     "read fails on connection {0}, throws",
			     new Object[] {this}, ioe);
		}
		throw ioe;
	    }
	}
    }

    /**
     * Input stream returned by getInputStream() of client or server
     * connection
     */
    static class ConnectionInputStream extends InputStream {
	private byte[] buf;
	private int offset; // point to the byte for next read
	private final Connection connection;

	/** Construct the input stream */
	ConnectionInputStream(Connection connection) {
	    buf = new byte[0]; // indicate no buffered data available
	    offset = 0;
	    this.connection = connection;
	}

	// This method's javadoc is inherited from InputStream
	public synchronized int read() throws IOException {
	    if (offset == buf.length) {
		do {
		    buf = connection.read();
		} while (buf.length == 0);
		offset = 0;
	    }
	    return buf[offset++];
	}

	// This method's javadoc is inherited from InputStream
	public synchronized int read(byte b[], int off, int len)
	    throws IOException
	{
	    if (b == null) {
		throw new NullPointerException();
	    } else if (off < 0 || len < 0 || (off + len) > b.length) {
		throw new IndexOutOfBoundsException();
	    }

	    if (offset == buf.length) {
		do {
		    buf = connection.read();
		} while (buf.length == 0);
		offset = 0;
	    }

	    int bytes = Math.min(buf.length - offset, len);
	    System.arraycopy(buf, offset, b, off, bytes);
	    offset += bytes;
	    return bytes;
	}

	// This method's javadoc is inherited from InputStream
	public synchronized int available() throws IOException {
	    return buf.length - offset;
	}

	/** Close the DataInputStream of the enclosed connection */
	public void close() throws IOException {
	    connection.dis.close();
	}
    }

    /**
     * Output stream returned by getOutputStream() of client or server
     * connection
     */
    static class ConnectionOutputStream extends OutputStream {
	// 8k is chosen because it might be close to a page size
	private static final int bufSize = 8000; // buf + overhead < 8192 ?
	private final byte[] buf;
	private int curLen; // current content length of the internal buffer
	private final Connection connection;

	/** Construct an instance of ConnectionOutputStream */
	ConnectionOutputStream(Connection connection) {
	    buf = new byte[bufSize];
	    curLen = 0; // init buf as empty
	    this.connection = connection;
	}

	// This method's javadoc is inherited from OutStream
	public synchronized void write(int b) throws IOException {
	    if (curLen == bufSize) {
		connection.write(buf, 0, curLen);
		curLen = 0;
	    }
	    buf[curLen++] = (byte) b;
	}

	// This method's javadoc is inherited from OutStream
	public synchronized void write(byte[] b, int off, int len)
	    throws IOException
	{
	    if (b == null) {
		throw new NullPointerException();
	    } else if (off < 0 || len < 0 || (off + len) > b.length) {
		throw new IndexOutOfBoundsException();
	    }

	    if ((curLen + len) >= bufSize) {
		int count = bufSize - curLen;
		System.arraycopy(b, off, buf, curLen, count);
		off += count;
		len -= count;
		connection.write(buf, 0, bufSize);
		curLen = 0;
	    }

	    while (len > bufSize) {
		connection.write(b, off, bufSize);
		off += bufSize;
		len -= bufSize;
	    }

	    System.arraycopy(b, off, buf, curLen, len);
	    curLen += len;
	}

	// This method's javadoc is inherited from OutStream
	public synchronized void flush() throws IOException {
	    if (curLen > 0) {
		connection.write(buf, 0, curLen);
		curLen = 0;
	    }
	    connection.flush();
	}

	/**
	 * Flush this stream, then close the DataOutputStream of the
	 * enclosed connection.
	 */
	public void close() throws IOException {
	    try {
		flush();
	    } finally {
		connection.dis.close();
	    }
	}
    }

    /**
     * A synchronized hash map that only maintains soft reference to
     * its value objects. It can be configured to have a limited
     * capacity. LRU replacement policy is used when capacity limit is
     * reached.
     */
    static class SoftCache {

	/** Internal hash map used to store the actual key value pairs */
	private final LRUHashMap hash;

	/** Reference queue for cleared ValueCells */
	private ReferenceQueue queue = new ReferenceQueue();

	/**
	 * Construct an instance of the SoftCache, using a default
	 * capacity of 8.
	 */
	SoftCache() {
	    this(Integer.MAX_VALUE, 8); // init cache size as unlimited
	}

	/**
	 * Construct an instance of the SoftCache with the given
	 * size limit.
	 *
	 * @param maxCacheSize maximum number of entries allowed in
	 *        this cache
	 */
	SoftCache(int maxCacheSize) {
	    this(maxCacheSize, 8);
	}

	/**
	 * Construct an instance of the SoftCache with the given
	 * size limit and initial capacity.
	 *
	 * @param maxCacheSize maximum number of entries allowed in
	 *        this cache
	 * @param initialCapacity initial capacity of the cache
	 */
	SoftCache(int maxCacheSize, int initialCapacity) {
	    hash = new LRUHashMap(maxCacheSize, initialCapacity);
	}

	/**
	 * Associates the specified value with the specified key in
	 * this cache. Only a soft reference is maintained to each
	 * value object stored in the cache. If the map previously
	 * contained a mapping for this key, and the old value object
	 * has not been garbage collected, the old value will be
	 * returned to the caller.  This method is synchronized.
         *
         * @param key key with which the specified value is to be
         *        associated.
         * @param value - value to be associated with the specified
         *        key, only a soft reference is maintained to value in
         *        this cache.
         * @return previous value associated with specified key, if an
         *         old mapping exists and the old value has not been
         *         garbage collected, or null if there was no
         *         mapping for key. A null return can also indicate
         *         that the HashMap previously associated null with
         *         the specified key.
	 */
	public synchronized Object put(Object key, Object value) {
	    processQueue();
	    ValueCell vc = ValueCell.create(key, value, queue);
	    return ValueCell.strip(hash.put(key, vc), true);
	}

	/**
	 * If there is a mapping in this cache for the specified key,
	 * and the softly referenced value of the mapping has not been
	 * garbage collected, return the value, other wise, return
	 * null.  This method is synchronized.
	 *
	 * @param key - key whose associated value is to be returned.
	 * @return the value to which this map maps the specified key.
	 */
	public synchronized Object get(Object key) {
	    processQueue();
	    return ValueCell.strip(hash.get(key), false);
	}

	/**
	 * Removes the mapping for this key from this cache if it
	 * still exists.  This method is synchronized.
	 *
	 * @param key key whose mapping is to be removed from the cache.
	 * @return previous value associated with the specified key
	 *         that has not been garbage collected, or null. A
	 *         null return can also indicate that the HashMap
	 *         previously associated null with the specified key.
 	 */
	public synchronized Object remove(Object key) {
	    processQueue();
	    return ValueCell.strip(hash.remove(key), true);
	}
	
	/**
	 * Removes all entries in this cache.  This method is
	 * synchronized.
	 */
	public synchronized void clear() {
	    processQueue();
	    hash.clear();
	}

	/** Process the internal ReferenceQueue */
	private void processQueue() {
	    ValueCell vc;
	    while ((vc = (ValueCell) queue.poll()) != null) {
		/*
		 * vc.isValid() is false, then the vc has been
		 * dropped, and the value cell currently in hash
		 * corresponding to vc.key, if exists, must be one
		 * that has been newly inserted using the same key =>
		 * can not do hash.remove(vc.key)
		 */
		if (vc.isValid())
		    hash.remove(vc.key);
	    }
	}

	/** A linked hash map that implements LRU replacement policy */
	private static class LRUHashMap extends LinkedHashMap {
            private static final long serialVersionUID = 1L;

	    private int maxCacheSize;

	    /**
	     * Construct an instance of the hash map.
	     *
	     * @param maxCacheSize maximum number of entries allowed
	     *        in this map
	     * @param initialCapacity initial capacity of the map
	     * @throws IllegalArgumentException if maxCacheSize is
	     *         negative
	     */
	    LRUHashMap(int maxCacheSize, int initialCapacity) {
		super(initialCapacity, 0.75f, true); // using access-order
		if (maxCacheSize < 0)
		    throw new IllegalArgumentException("negative cache size");
		this.maxCacheSize = maxCacheSize;
	    }

	    // This method's javadoc is inherited from LinkedHashMap
	    protected boolean removeEldestEntry(Map.Entry eldest) {
		if (size() > maxCacheSize) {
		    /*
		     * clear the soft ref and mark internal key as
		     * invalid, LRUHashMap will take care of the
		     * removing part
		     */
		    ValueCell.strip(eldest.getValue(), true);
		    return true;
		}
		return false;
	    }
	}

	/**
	 * An instance of this class maintains a reference to a key,
	 * and a soft reference to the value the key maps to.
	 */
	private static class ValueCell extends SoftReference {

	    static private Object INVALID_KEY = new Object();
	    private Object key;

	    private ValueCell(Object key, Object value, ReferenceQueue queue) {
		super(value, queue);
		this.key = key;
	    }

	    private static ValueCell create(Object key, Object value,
					    ReferenceQueue queue)
	    {
		if (value == null) return null;
		return new ValueCell(key, value, queue);
	    }

	    /**
	     * Extract the encapsulated value if the passed in object
	     * is an instance of ValueCell, clear the soft reference
	     * and mark the cell as invalid if drop is true.
	     */
	    private static Object strip(Object val, boolean drop) {
		if (val == null)
		    return null;
		ValueCell vc = (ValueCell)val;
		Object o = vc.get();
		if (drop)
		    vc.drop();
		return o;
	    }

	    /** 
	     * Return true if this cell has not been dropped, false
	     * otherwise
	     */
	    private boolean isValid() {
		return (key != INVALID_KEY);
	    }

	    /** Clear the soft reference, and mark the cell as invalid */
	    private void drop() {
		clear();
		key = INVALID_KEY;
	    }
	}
    }	
}
