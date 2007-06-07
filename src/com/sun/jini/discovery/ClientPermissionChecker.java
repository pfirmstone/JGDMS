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

package com.sun.jini.discovery;

import com.sun.jini.collection.SoftCache;
import java.security.AccessControlException;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Set;
import javax.security.auth.Subject;

/**
 * Implementation of {@link ClientSubjectChecker} that approves or rejects
 * client subjects based on whether or not they have been granted a particular
 * permission.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class ClientPermissionChecker implements ClientSubjectChecker {

    private static final CodeSource emptyCS =
	new CodeSource(null, (Certificate[]) null);
    private static final ProtectionDomain emptyPD = 
	new ProtectionDomain(emptyCS, null, null, null);

    private final SoftCache domains = new SoftCache();
    private final Permission permission;

    /**
     * Creates instance that checks if client subjects have been granted the
     * specified permission.
     *
     * @param permission the permission to use in client subject checks
     * @throws NullPointerException if <code>permission</code> is
     * <code>null</code>
     */
    public ClientPermissionChecker(Permission permission) {
	if (permission == null) {
	    throw new NullPointerException();
	}
	this.permission = permission;
    }

    /**
     * Checks whether or not to permit exchanging or accepting data with/from a
     * client authenticated as the given subject, by testing if the subject has
     * been granted the permission that this instance was constructed with.  If
     * a security manager is installed, a {@link ProtectionDomain} is
     * constructed with an empty {@link CodeSource} (<code>null</code> location
     * and certificates), <code>null</code> permissions, <code>null</code>
     * class loader, and the principals from the given client subject (if any),
     * and the {@link ProtectionDomain#implies implies} method of that
     * protection domain is invoked with the specified permission.  If
     * <code>true</code> is returned, this method returns normally, otherwise a
     * <code>SecurityException</code> is thrown.  If no security manager is
     * installed, this method returns normally.  The given client subject must
     * be read-only if non-<code>null</code>.
     *
     * @throws SecurityException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public void checkClientSubject(Subject subject) {
	if (subject != null && !subject.isReadOnly()) {
	    throw new IllegalArgumentException("subject is not read-only");
	}
	if (System.getSecurityManager() == null) {
	    return;
	}
	ProtectionDomain pd;
	if (subject == null) {
	    pd = emptyPD;
	} else {
	    synchronized (domains) {
		pd = (ProtectionDomain) domains.get(subject);
	    }
	    if (pd == null) {
		Set s = subject.getPrincipals();
		Principal[] prins =
		    (Principal[]) s.toArray(new Principal[s.size()]);
		pd = new ProtectionDomain(emptyCS, null, null, prins);
		synchronized (domains) {
		    domains.put(subject, pd);
		}
	    }
	}
	if (!pd.implies(permission)) {
	    throw new AccessControlException("access denied " + permission);
	}
    }
}
