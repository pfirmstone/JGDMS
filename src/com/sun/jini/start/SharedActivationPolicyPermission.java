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

package com.sun.jini.start;

import java.io.File;
import java.io.FilePermission;
import java.io.Serializable;
import java.net.URL;
import java.net.MalformedURLException;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

/**
 * {@link Permission} class used by the 
 * {@linkplain com.sun.jini.start service starter} 
 * package. This class takes a policy string argument that follows the 
 * matching semantics defined by {@link FilePermission}. The 
 * {@link ActivateWrapper} class explicitly checks to see if the service's
 * import codebase has been granted access to service's associated policy
 * file in the shared VM's policy.
 *<P>
 * An example grant is:
 * <blockquote><pre>
 * grant codebase "file:<VAR><B>install_dir</B></VAR>/lib/fiddler.jar" {
 *     permission com.sun.jini.start.SharedActivationPolicyPermission 
 *         "<VAR><B>policy_dir</B></VAR>${/}policy.fiddler";
 * };
 * </pre></blockquote>
 * This grant allows services using 
 * <code><VAR><B>install_dir</B></VAR>/lib/fiddler.jar</code> for their
 * import codebase to use 
 * <code><VAR><B>policy_dir</B></VAR>${/}policy.fiddler</code> for their
 * policy file, where <VAR><B>install_dir</B></VAR> is the installation
 * directory of the Apache River release and <VAR><B>policy_dir</B></VAR> is the
 * pathname to the directory containing the policy file.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public final class SharedActivationPolicyPermission extends Permission
                                                    implements Serializable
{
    private static final long serialVersionUID = 1L;

    /*
     * Debug flag.
     */
    private static final boolean DEBUG = false;

    /**
     * <code>FilePermission</code> object that is the delegation
     * target of the <code>implies()</code> checks.
     * @serial
     */
    private /*final*/ FilePermission policyPermission;

    /**
     * Constructor that creates a 
     * <code>SharedActivationPolicyPermission</code> with the specified name.
     * Delegates <code>policy</code> to supertype.
     */
    public SharedActivationPolicyPermission(String policy) {
	//TBD - check for null args
	super(policy);
	init(policy);
    }

    /**
     * Constructor that creates a 
     * <code>SharedActivationPolicyPermission</code> with the specified name.
     * This constructor exists for use by the <code>Policy</code> object
     * to instantiate new Permission objects. The <code>action</code>
     * argument is currently ignored.
     */
    public SharedActivationPolicyPermission(String policy, String action) {
	//TBD - check for null args
	super(policy);
	init(policy);
    }

    /**
     * Contains common code to all constructors.
     */
    private void init(final String policy) {
	/*
	 * In order to leverage the <code>FilePermission</code> logic
	 * we need to make sure that forward slashes ("/"), in 
	 * <code>URLs</code>, are converted to
	 * the appropriate system dependent <code>File.separatorChar</code>. 
	 * For example,
	 * http://host:port/* matches http://host:port/bogus.jar under
	 * UNIX, but not under Windows since "\*" is the wildcard there.
	 */
        String uncanonicalPath = null;
        try {
            URL url = new URL(policy);
	    uncanonicalPath = url.toExternalForm();
	    uncanonicalPath = uncanonicalPath.replace('/', File.separatorChar);
	    if (DEBUG) {
   	        System.out.println("SharedActivationPolicyPermission::init() - "
	        + policy + " => " + uncanonicalPath);
	    }
	} catch (MalformedURLException me) {
	    uncanonicalPath = policy;
	}

        policyPermission = new FilePermission(uncanonicalPath, "read");
    }

    // javadoc inherited from superclass
    public boolean implies(Permission p) {

	// Quick reject tests
	if (p == null)
	   return false;
	if (!(p instanceof SharedActivationPolicyPermission))
	    return false;

        SharedActivationPolicyPermission other = 
            (SharedActivationPolicyPermission)p; 

        // Delegate to FilePermission logic 
        boolean	answer = policyPermission.implies(other.policyPermission);

	if (DEBUG) {
   	    System.out.println("SharedActivationPolicyPermission::implies() - " 
	        + "checking " + policyPermission + " vs. " 
	        + other.policyPermission + ": " + answer);
	}

	return answer;
    }

    /** Two instances are equal if they have the same name. */
    public boolean equals(Object obj) {
	// Quick reject tests
        if (obj == null) 
            return false;
	if (this == obj)
           return true;
        if (!(obj instanceof SharedActivationPolicyPermission))
           return false;

        SharedActivationPolicyPermission other = 
            (SharedActivationPolicyPermission)obj; 

	boolean answer = policyPermission.equals(other.policyPermission);
	if (DEBUG) {
	    System.out.println("SharedActivationPolicyPermission::equals() - " 
	        + "checking " + policyPermission + " vs. " 
	        + other.policyPermission + ": " + answer);
	}

	return answer; 
    }

    // javadoc inherited from superclass
    public int hashCode() {
	return getName().hashCode();
    }

    // javadoc inherited from superclass
    public String getActions() {
	return "";
    }

    // javadoc inherited from superclass
    public PermissionCollection newPermissionCollection() {
	/* bug 4158302 fix */
	return new Collection();
    }

    /** Simple permission collection. See Bug 4158302 */
    private static class Collection extends PermissionCollection {
	private static final long serialVersionUID = 1L;

	/**
	 * Permissions
	 *
	 * @serial
	 **/
	private final ArrayList perms = new ArrayList(3);

        // javadoc inherited from superclass
	public synchronized void add(Permission p) {
	    if (isReadOnly())
		throw new SecurityException("Collection cannot be modified.");

	    if (perms.indexOf(p) < 0)
		perms.add(p);
	}

        // javadoc inherited from superclass
	public synchronized boolean implies(Permission p) {
	    for (int i = perms.size(); --i >= 0; ) {
		if (((Permission)perms.get(i)).implies(p))
		    return true;
	    }
	    return false;
	}

	// javadoc inherited from superclass
	public Enumeration elements() {
	    return Collections.enumeration(perms);
	}
    }
}
