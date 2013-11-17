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

package org.apache.river.api.security;

import java.security.ProtectionDomain;
import javax.security.auth.Subject;

/**
 * A Marker interface used by {@link net.jini.security.Security} to mark
 * a {@link java.security.ProtectionDomain} added to the stack that represents an
 * authenticated {@link javax.security.auth.Subject} with 
 * {@link java.security.Principal}'s.
 * <p>
 * Typically in non-distributed systems, a <code>Subject</code> is represented
 * by a <code>Thread</code> and all {@link java.security.Permission} granted 
 * to that <code>Subject</code> is injected into every <code>ProtectionDomain</code> 
 * present on the call stack. Code is always trusted, or at least it must be at the time the 
 * <code>Subject</code> is authenticated.
 * <p>
 * However in a distributed system, because code trust and Subject's are 
 * separate concerns, methods have been provided in 
 * {@link net.jini.security.Security} to push a <code>SubjectDomain</code>
 * onto the stack instead of injecting Principal's into all ProtectionDomain's on 
 * the stack, this avoids mistakenly elevating privileges of less trusted
 * code, if present on a call stack at the time the doAs subject method is called.
 * <p>
 * When using these methods, the security Policy needs to be written to grant
 * Permission to code signer certificates and Principals separately.
 * <p>
 * For example, a RemotePolicy service is updated by an Administrator client
 * using PermissionGrant's, however one of these grants is not signed by a 
 * trusted Certificate.  Ordinarily this PermissionGrant would run with the
 * privileges of the administrator, but because code trust and user trust
 * should be considered separate concerns in a distributed system,
 * the administrator is unable to make these PermissionGrant's in the 
 * presence of untrusted code.
 * <p>
 * These methods apply the principle of least privilege to Subject's as well
 * as code.  The privileges allowed will be the intersection of Permission 
 * granted to each ProtectionDomain on the call stack.
 * <p>
 * These methods require a River or Jini Policy provider to be installed.  The
 * SubjectDomain is a dynamic ProtectionDomain, it contains no Permission,
 * it always consults the current {@link java.security.Policy}.
 * 
 * @see net.jini.security.Security#doAs(javax.security.auth.Subject, java.security.PrivilegedAction) 
 * @see net.jini.security.Security#doAs(javax.security.auth.Subject, java.security.PrivilegedExceptionAction) 
 * @see net.jini.security.Security#doAsPrivileged(javax.security.auth.Subject, java.security.PrivilegedAction, net.jini.security.SecurityContext) 
 * @see net.jini.security.Security#doAsPrivileged(javax.security.auth.Subject, java.security.PrivilegedExceptionAction, net.jini.security.SecurityContext) 
 * @see net.jini.security.policy.DynamicPolicyProvider
 * @see org.apache.river.api.security.ConcurrentPolicyFile
 * 
 * @author Peter Firmstone
 * @since 3.0.0
 */
public interface SubjectDomain {
    
    public Subject getSubject();
}
