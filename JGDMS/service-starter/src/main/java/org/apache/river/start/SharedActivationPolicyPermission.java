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

package org.apache.river.start;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;
import org.apache.river.api.net.Uri;

/**
 * {@link Permission} class used by the 
 * {@linkplain org.apache.river.start service starter} 
 * package. This class takes a policy string argument that follows the 
 * matching semantics defined by {@link FilePermission}. Note that after 
 * <a link="http://mail.openjdk.java.net/pipermail/jdk9-dev/2016-October/005062.html">
 * FilePermission changes in JDK9 140</a> the following property must be set
 * for this Permission to function correctly. -Djdk.io.permissionsUseCanonicalPath=true
 * <p>
 * The {@link ActivateWrapper} class explicitly checks to see if the service's
 * import codebase has been granted access to service's associated policy
 * file in the shared VM's policy.
 *<P>
 * An example grant is:
 * <blockquote><pre>
 * grant codebase "file:<VAR><B>install_dir</B></VAR>/lib/fiddler.jar" {
 *     permission org.apache.river.start.SharedActivationPolicyPermission 
 *         "<VAR><B>policy_dir</B></VAR>${/}policy.fiddler";
 * };
 * </pre></blockquote>
 * This grant allows services using 
 * <code><VAR><B>install_dir</B></VAR>/lib/fiddler.jar</code> for their
 * import codebase to use 
 * <code><VAR><B>policy_dir</B></VAR>${/}policy.fiddler</code> for their
 * policy file, where <VAR><B>install_dir</B></VAR> is the installation
 * directory of the JGDMS release and <VAR><B>policy_dir</B></VAR> is the
 * pathname to the directory containing the policy file.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public final class SharedActivationPolicyPermission extends Permission
                                                    implements Serializable
{
    private static final long serialVersionUID = 1L;
    
    /**
     * In earlier versions the extra fields will duplicate those of Permission,
     * so only ref id's will be sent, so the objects these fields refer to will
     * only be sent once.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("policyPermission", Permission.class),
	    new ObjectStreamField("policy", String.class)
	}; 

    /*
     * Debug flag.
     */
    private static final boolean DEBUG = false;

    /**
     * <code>FilePermission</code> object that is the delegation
     * target of the <code>implies()</code> checks.
     * @serial
     */
    private final Permission policyPermission;

    /**
     * Constructor that creates a 
     * <code>SharedActivationPolicyPermission</code> with the specified name.
     * Delegates <code>policy</code> to supertype.
     * @param policy
     */
    public SharedActivationPolicyPermission(String policy) {
	this(policy, init(policy));
    }

    /**
     * Constructor that creates a 
     * <code>SharedActivationPolicyPermission</code> with the specified name.
     * This constructor exists for use by the <code>Policy</code> object
     * to instantiate new Permission objects. The <code>action</code>
     * argument is currently ignored.
     */
    public SharedActivationPolicyPermission(String policy, String action) {
	this(policy, init(policy));
    }
   
    private SharedActivationPolicyPermission(String policy, Permission policyPermission){
	super(policy);
	this.policyPermission = policyPermission;
    }
    
    public SharedActivationPolicyPermission(GetArg arg) 
	    throws IOException, ClassNotFoundException{
	this(
	    Valid.notNull(
		arg.get("policy", null, String.class), "policy cannot be null"
	    ),
	    (String) null
	);
    }
    
    private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
	in.defaultReadObject();
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
	PutField pf = out.putFields();
	pf.put("policyPermission", policyPermission);
	pf.put("policy", getName());
	out.writeFields();
    }

//    /**
//     * Contains common code to all constructors.
//     */
//    private Permission init(final String policy) {
//	/*
//	 * In order to leverage the <code>FilePermission</code> logic
//	 * we need to make sure that forward slashes ("/"), in 
//	 * <code>URLs</code>, are converted to
//	 * the appropriate system dependent <code>File.separatorChar</code>. 
//	 * For example,
//	 * http://host:port/* matches http://host:port/bogus.jar under
//	 * UNIX, but not under Windows since "\*" is the wildcard there.
//	 */
//        if (policy == null) throw new NullPointerException("Null policy string not allowed");
//        String uncanonicalPath = null;
//        try {
//            URL url = new URL(policy);
//	    uncanonicalPath = url.toExternalForm();
//	    uncanonicalPath = uncanonicalPath.replace('/', File.separatorChar);
//	    if (DEBUG) {
//   	        System.out.println("SharedActivationPolicyPermission::init() - "
//	        + policy + " => " + uncanonicalPath);
//	    }
//	} catch (MalformedURLException me) {
//	    uncanonicalPath = policy;
//	}
//
//        return new FilePermission(uncanonicalPath, "read");
//    }
    
    /**
     * Contains common code to all constructors.
     */
    private static Permission init(final String policy) {
	/*
	 * In order to leverage the <code>FilePermission</code> logic
	 * we need to make sure that forward slashes ("/"), in 
	 * <code>URLs</code>, are converted to
	 * the appropriate system dependent <code>File.separatorChar</code>. 
	 * For example,
	 * http://host:port/* matches http://host:port/bogus.jar under
	 * UNIX, but not under Windows since "\*" is the wildcard there.
	 */
        if (policy == null) throw new NullPointerException("Null policy string not allowed");
        String uncanonicalPath;
        try {
            URL url = new URL(policy);
            uncanonicalPath = url.toExternalForm();
            if (policy.startsWith("file:") || policy.startsWith("FILE:")){
                String path;
                try {
                    uncanonicalPath = Uri.fixWindowsURI(uncanonicalPath);
//                    uncanonicalPath = Uri.escapeIllegalCharacters(uncanonicalPath);
                    path = Uri.uriToFile(Uri.escapeAndCreate(uncanonicalPath)).getPath();
//                    path = new File(new URI(uncanonicalPath)).getPath();
                } catch (URISyntaxException ex) {
                    path = uncanonicalPath.replace('/', File.separatorChar);
                } catch (IllegalArgumentException ex){
                    path = uncanonicalPath.replace('/', File.separatorChar);
                }
                uncanonicalPath = path;
            } else {
                uncanonicalPath = uncanonicalPath.replace('/', File.separatorChar);
            }
	    if (DEBUG) {
   	        System.out.println("SharedActivationPolicyPermission::init() - "
	        + policy + " => " + uncanonicalPath);
	    }
	} catch (MalformedURLException me) {
	    uncanonicalPath = policy;
	}

        return new FilePermission(uncanonicalPath, "read");
    }

    // javadoc inherited from superclass
    @Override
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
    @Override
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
    @Override
    public int hashCode() {
	return getName().hashCode();
    }

    // javadoc inherited from superclass
    @Override
    public String getActions() {
	return "";
    }

    // javadoc inherited from superclass
    @Override
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
        @Override
	public synchronized void add(Permission p) {
	    if (isReadOnly())
		throw new SecurityException("Collection cannot be modified.");

	    if (perms.indexOf(p) < 0)
		perms.add(p);
	}

        // javadoc inherited from superclass
        @Override
	public synchronized boolean implies(Permission p) {
	    for (int i = perms.size(); --i >= 0; ) {
		if (((Permission)perms.get(i)).implies(p))
		    return true;
	    }
	    return false;
	}

	// javadoc inherited from superclass
        @Override
	public Enumeration elements() {
	    return Collections.enumeration(perms);
	}
    }
}
