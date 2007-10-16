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

package com.sun.jini.tool;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.security.policy.DynamicPolicy;
import net.jini.security.policy.DynamicPolicyProvider;
import net.jini.security.policy.PolicyInitializationException;

/**
 * Defines a {@link DynamicPolicy} that logs information about missing
 * permissions, and optionally grants all permissions, which is <b>FOR
 * DEBUGGING ONLY</b>. Do not use this security policy provider to grant
 * all permissions in a production environment. <p>
 *
 * This class is intended to simplify the process of deciding what security
 * permissions to grant to run an application.  While it is generally
 * acceptable to grant all permissions to local, trusted code, downloaded
 * code should typically be granted the least permission possible. <p>
 *
 * The usual approach to choosing which permissions to grant is to start by
 * running the application with a security policy file that grants all
 * permissions to local, trusted code.  When the application fails with an
 * exception message that identifies a missing permission, add that
 * permission to the security policy file, and repeat the process. Although
 * straight forward, this process can be time consuming if the application
 * requires many permission grants. <p>
 *
 * Another approach is to set the value of the
 * <code>"java.security.debug"</code> system property to
 * <code>"access,failure"</code>, which produces debugging output that
 * describes permission grants and failures. Unfortunately, this approach
 * produces voluminous output, making it difficult to determine which
 * permission grants are needed. <p>
 *
 * This security policy provider permits another, hopefully more
 * convenient, approach. When this class is specified as the security
 * policy provider, and granting all permissions is enabled, it uses the
 * standard dynamic security policy to determine what permissions are
 * granted. If a permission is not granted by the standard policy, though,
 * then rather than denying permission, this class logs the missing
 * permission in the form required by the security policy file, and grants
 * the permission, allowing the program to continue. In this way,
 * developers can determine the complete set of security permissions
 * required by the application. <p>
 *
 * Note that the information printed by this security policy provider may
 * not be in the form you wish to use in your policy file. In particular,
 * using system property substitutions and <code>KeyStore</code> aliases
 * may produce a more portable file than one containing the exact entries
 * logged. Note, too, that the information printed for
 * <code>signedBy</code> fields specifies the principal name for
 * <code>X.509</code> certificates, rather than the <code>KeyStore</code>
 * alias, which is not a valid security policy file format. <p>
 *
 * Using this security policy provider without granting all permissions is
 * also useful since it prints information about security exceptions that
 * were caught, but that might have an affect on program behavior. <p>
 *
 * This class uses uses the {@link Logger} named
 * <code>net.jini.security.policy</code> to log information at the following
 * levels: <ul>
 *
 * <li> {@link Level#WARNING WARNING} - Permissions that were needed but not
 * granted by the policy file.
 *
 * <li> {@link Level#FINE FINE} - Also include stack traces.
 *
 * <li> {@link Level#FINER FINER} - All permissions granted, with stack traces
 * for ones not granted by the policy file, and dynamic grants.
 *
 * <li> {@link Level#FINEST FINEST} - All permissions granted, with all stack
 * traces, and dynamic grants. </ul>
 *
 * To use this security policy provider, do the following: <ul>
 *
 * <li> Copy the <code>jsk-policy.jar</code> file from the <code>lib-ext</code>
 * subdirectory of the Apache River release
 * installation to the extensions directory of the Java(TM) 2 SDK (or JRE)
 * installation, and copy the <code>jsk-debug-policy.jar</code> file
 * from the <code>lib</code> subdirectory of the Apache River release installation to
 * the extensions directory of the Java 2 SDK (or JRE) installation.
 *
 * <li> Specify this class as the security policy provider. Create a copy of
 * the file <code>jre/lib/security/security/java.security</code>, modify the
 * file to contain the line:
 *
 * <blockquote>
 * <pre>
 * policy.provider=com.sun.jini.tool.DebugDynamicPolicyProvider
 * </pre>
 * </blockquote>
 *
 * and then specify this new file as the value of the
 * <code>java.security.properties</code> system property.  
 *
 * <li> Specify whether all permissions should be granted by setting the
 * <code>com.sun.jini.tool.DebugDynamicPolicyProvider.grantAll</code> security
 * property to <code>true</code> by adding the following line to the security
 * properties file:
 * 
 * <blockquote>
 * <pre>
 * com.sun.jini.tool.DebugDynamicPolicyProvider.grantAll=true
 * </pre>
 * </blockquote> </ul> <p>
 *
 * Granting all permissions is disabled by default. <p>
 *
 * Make sure to specify a security manager, either by setting the
 * <code>java.security.manager</code> system property, or putting the following
 * code in the main method of the application:
 *
 * <blockquote>
 * <pre>
 * if (System.getSecurityManager() == null) {
 *     System.setSecurityManager(new SecurityManager());
 * }
 * </pre>
 * </blockquote>
 *
 * <p>This provider can be used in conjunction with the provider
 * <code>com.sun.jini.start.AggregatePolicyProvider</code> by setting the
 * <code>com.sun.jini.start.AggregatePolicyProvider.mainPolicyClass</code> 
 * system property to the fully qualified name of this class.  If this
 * provider is used with the <code>AggregatePolicyProvider</code>, then the
 * JAR file <code>jsk-debug-policy.jar</code> needs to be in the
 * application's class path, and this class needs to be granted all
 * permissions. 
 *
 *
 * @author Sun Microsystems, Inc.
 **/
public class DebugDynamicPolicyProvider extends DynamicPolicyProvider {

    /* Logger to use */
    private static final Logger logger =
	Logger.getLogger("net.jini.security.policy");

    /* If true, always grant permission */
    private static boolean grantAll =
	((Boolean) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    return Boolean.valueOf(
			Security.getProperty(
			    "com.sun.jini.tool." +
			    "DebugDynamicPolicyProvider.grantAll"));
		}
	    })).booleanValue();

    /* Cache of permission requests already made */
    private static final Set requests = new HashSet();

    /** The empty codesource. */
    private static final CodeSource emptyCS =
	new CodeSource(null, (Certificate[]) null);

    /**
     * Creates an instance of this class that wraps a default underlying
     * policy, as specified by {@link
     * DynamicPolicyProvider#DynamicPolicyProvider() DynamicPolicyProvider()}.
     *
     * @throws PolicyInitializationException if unable to construct the base
     *	       policy
     * @throws SecurityException if there is a security manager and the calling
     *	       context does not have adequate permissions to read the <code>
     *	       net.jini.security.policy.DynamicPolicyProvider.basePolicyClass
     *	       </code> security property, or if the calling context does not
     *	       have adequate permissions to access the base policy class
     */
    public DebugDynamicPolicyProvider() throws PolicyInitializationException { }

    /**
     * Creates an instance of this class that wraps around the given
     * non-<code>null</code> base policy object.
     *
     * @param basePolicy base policy object containing information about
     *        non-dynamic grants
     * @throws NullPointerException if <code>basePolicy</code> is
     * 	       <code>null</code>
     */
    public DebugDynamicPolicyProvider(Policy basePolicy) {
	super(basePolicy);
    }

    /** Log calls. */
    public void grant(Class cl,
		      Principal[] principals,
		      Permission[] permissions)
    {
	try {
	    super.grant(cl, principals, permissions);
	    if (permissions == null
		|| permissions.length == 0
		|| !logger.isLoggable(Level.FINER))
	    {
		return;
	    }
	    Request req = new Request(cl, principals, permissions);
	    if (cl == null) {
		logger.log(Level.FINER,
			   "Granting permissions for all classes:\n{0}",
			   req.toString());
	    } else {
		logger.log(Level.FINER,
			   "Granting permissions for {0}:\n{1}",
			   new Object[] { cl, req.toString() });
	    }
	} catch (SecurityException e) {
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "Granting permissions failed", e);
	    }
	    throw e;
	}
    }

    /** Always returns true, but logs unique requests */
    public boolean implies(ProtectionDomain pd, Permission perm) {
	boolean implies = super.implies(pd, perm);
	boolean result = implies ? true : grantAll;
	if (!(logger.isLoggable(Level.FINER)
	      || (!implies && logger.isLoggable(Level.WARNING))))
	{
	    return result;
	}
	Request request = new Request(pd, perm);
	synchronized (requests) {
	    if (requests.contains(request)) {
		return result;
	    }
	    requests.add(request);
	}
	String stackTrace = null;
	if (logger.isLoggable(Level.FINEST)
	    || (!implies && logger.isLoggable(Level.FINE)))
	{
	    StringWriter sw = new StringWriter();
	    PrintWriter pw = new PrintWriter(sw);
	    new Exception("Stack trace:").printStackTrace(pw);
	    pw.close();
	    stackTrace = sw.toString();
	}
	if (implies) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "Permission granted:\n{0}\n{1}",
			   new Object[] { request.toString(), stackTrace });
	    } else {
		logger.log(Level.FINER, "Permission granted:\n{0}",
			   request.toString());
	    }
	} else if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.WARNING,
		       (grantAll
			? "Permission not granted by base policy:\n{0}\n{1}"
			: "Permission not granted:\n{0}\n{1}"),
		       new Object[] { request.toString(), stackTrace });
	} else {
	    logger.log(Level.WARNING,
		       (grantAll
			? "Permission not granted by base policy:\n{0}"
			: "Permission not granted:\n{0}"),
		       request.toString());
	}   
	return result;
    }

    /** Returns the name of the certificate. */
    private static String getCertName(Certificate cert) {
	if (cert instanceof X509Certificate) {
	    return ((X509Certificate) cert).getSubjectDN().getName();
	} else {
	    return cert.toString();
	}
    }

    /**
     * Returns a quoted version of the argument, such that it would result in
     * the argument if read from a file with the standard String syntax.
     */
    private static String quoteString(String s) {
	if (s == null) {
	    return "";
	}
	int len = s.length();
	StringBuffer buf = new StringBuffer(len + 2);
	buf.append('"');
	for (int off = 0; off < len; ) {
	    int quote = s.indexOf('"', off);
	    int slash = s.indexOf('\\', off);
	    if (quote >= 0 && (slash < 0 || slash > quote)) {
		buf.append(s.substring(off, quote));
		buf.append("\\\"");
		off = quote + 1;
	    } else if (slash >= 0) {
		buf.append(s.substring(off, slash));
		buf.append("\\\\");
		off = slash + 1;
	    } else {
		buf.append(s.substring(off));
		break;
	    }
	}
	buf.append('"');
	return buf.toString();
    }

    /* Keeps track of an individual permission request */
    private static class Request {
	final CodeSource codeSource;
	final Certificate[] certs;
	final Principal[] principals;
	final Permission[] perms;

	Request(ProtectionDomain pd, Permission perm) {
	    codeSource = pd.getCodeSource();
	    certs = codeSource == null ? null : codeSource.getCertificates();
	    principals = pd.getPrincipals();
	    this.perms = new Permission[] { perm };
	}

	Request(final Class cl, Principal[] principals, Permission[] perms) {
	    codeSource = (cl == null) ? emptyCS :
		(CodeSource) AccessController.doPrivileged(
		    new PrivilegedAction() {
			public Object run() {
			    return cl.getProtectionDomain().getCodeSource();
			}
		    });
	    certs = null;
	    this.principals = principals;
	    this.perms = perms;
	}

	public boolean equals(Object o) {
	    if (o instanceof Request) {
		Request other = (Request) o;
		return (codeSource == null
			? other.codeSource == null
			: equals(codeSource.getLocation(),
				 other.codeSource.getLocation()))
		    && equals(principals, other.principals)
		    && equals(perms, other.perms);
	    } else {
		return false; 
	    }
	}

	private static boolean equals(Object x, Object y) {
	    return (x == null) ? y == null : x.equals(y);
	}

	private static boolean equals(Object[] x, Object[] y) {
	    if (x == null) {
		return y == null;
	    } else if (y == null) {
		return false;
	    } else {
		return Arrays.equals(x, y);
	    }
	}

	public int hashCode() {
	    return hash(codeSource == null ? null : codeSource.getLocation())
		^ hash(certs) ^ hash(principals) ^ hash(perms);
	}

	private static int hash(Object obj) {
	    return (obj == null) ? 0 : obj.hashCode();
	}

	private static int hash(Object[] array) {
	    int result = 0;
	    if (array != null) {
		for (int i = array.length; --i >= 0; ) {
		    result ^= hash(array[i]);
		}
	    }
	    return result;
	}

	public String toString() {
	    StringBuffer buf = new StringBuffer();
	    buf.append("grant\n");
	    if (codeSource == null) {
		buf.append("    /* bootstrap codebase */\n");
	    } else {
		URL location = codeSource.getLocation();
		if (location != null) {
		    buf.append("    codeBase ");
		    buf.append(quoteString(location.toString()));
		    buf.append('\n');
		}
		if (certs != null) {
		    for (int i = 0; i < certs.length; i++) {
			buf.append("    signedby ");
			buf.append(quoteString(getCertName(certs[i])));
			buf.append('\n');
		    }
		}
	    }
	    if (principals != null) {
		for (int i = 0; i < principals.length; i++) {
		    buf.append("    principal ");
		    buf.append(principals[i].getClass().getName());
		    buf.append(' ');
		    buf.append(quoteString(principals[i].getName()));
		    buf.append('\n');
		}
	    }
	    buf.append("{\n");
	    for (int i = 0; i < perms.length; i++) {
		Permission perm = perms[i];
		buf.append("    permission ");
		buf.append(perm.getClass().getName());
		buf.append('\n');
		buf.append("        ");
		buf.append(quoteString(perm.getName()));
		String actions = perm.getActions();
		if (actions != null && actions.length() != 0) {
		    buf.append(",\n");
		    buf.append("        ");
		    buf.append(quoteString(perm.getActions()));
		}
		buf.append(";\n");
	    }
	    buf.append("};");
	    return buf.toString();
	}
    }
}
