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

package org.apache.river.discovery;

import org.apache.river.collection.SoftCache;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.security.Security;

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

    /**
     * Context for a <code>null</code> (unauthenticated) client subject, created
     * lazily so that it is only built when a security manager is installed.
     */
    private static volatile AccessControlContext emptyContext;

    /*
     * Contexts, not domains, are cached: CombinerSecurityManager caches its
     * permission results per execution context, so a fresh context per packet
     * would force a full policy evaluation every time.
     */
    private final SoftCache<Subject,AccessControlContext> domains
	    = new SoftCache<Subject,AccessControlContext>();
    private final Permission permission;

    /**
     * Returns an <code>AccessControlContext</code> containing exactly the given
     * domain.  The context is created in a privileged block because
     * <code>AccessControlContext.create</code> requires
     * <code>SecurityPermission("createAccessControlContext")</code>, and when
     * unauthorized it silently degrades rather than throwing; scoping that check
     * to this library's domain avoids a degraded context being built when a less
     * privileged frame is on the stack.
     */
    private static AccessControlContext contextFor(final ProtectionDomain pd) {
	return AccessController.doPrivileged(
	    new PrivilegedAction<AccessControlContext>() {
		public AccessControlContext run() {
		    return Security.create(new ProtectionDomain[]{ pd });
		}
	    });
    }

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
     * and the permission is checked against an
     * {@link java.security.AccessControlContext} containing only that protection
     * domain, by invoking {@link SecurityManager#checkPermission(Permission,
     * Object)}.  If the check succeeds this method returns normally, otherwise a
     * <code>SecurityException</code> is thrown.  If no security manager is
     * installed, this method returns normally.  The given client subject must
     * be read-only if non-<code>null</code>.
     *
     * <p>The check is routed through the security manager rather than calling
     * {@link ProtectionDomain#implies implies} directly so that audit security
     * managers (polpAudit) observe it and can record the required grant.  On a
     * stock JVM the two are equivalent, as the security manager delegates to
     * {@link java.security.AccessControlContext#checkPermission}.
     *
     * @throws SecurityException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public void checkClientSubject(Subject subject) {
	if (subject != null && !subject.isReadOnly()) {
	    throw new IllegalArgumentException("subject is not read-only");
	}
	SecurityManager sm = System.getSecurityManager();
	if (sm == null) {
	    return;
	}
	AccessControlContext acc;
	if (subject == null) {
	    acc = emptyContext;
	    if (acc == null) {
		acc = contextFor(emptyPD);
		emptyContext = acc;
	    }
	} else {
	    synchronized (domains) {
		acc = domains.get(subject);
	    }
	    if (acc == null) {
		Set<Principal> s = subject.getPrincipals();
		Principal[] prins = s.toArray(new Principal[s.size()]);
		acc = contextFor(
			new ProtectionDomain(emptyCS, null, null, prins));
		synchronized (domains) {
		    domains.put(subject, acc);
		}
	    }
	}
	sm.checkPermission(permission, acc);
    }
}
