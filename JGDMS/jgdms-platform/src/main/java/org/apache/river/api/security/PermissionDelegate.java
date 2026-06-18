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

import java.security.Permission;
import java.security.ProtectionDomain;

/**
 * Implemented by a {@link Permission} that, when it is not directly implied by
 * a {@link ProtectionDomain}, can nominate a substitute permission to be
 * checked in its place.
 *
 * <p>This is the decoupling seam for delegated-permission support.  A
 * {@code SecurityManager} that evaluates permissions against protection
 * domains can honour delegation generically &mdash; by recognising this
 * interface rather than any concrete permission type &mdash; so that the
 * security manager need not depend on {@link DelegatePermission} or any other
 * delegating-permission implementation.  A domain satisfies a delegating
 * permission if it implies either the delegating permission itself or the
 * permission returned by {@link #getPermissionToCheck()}.
 *
 * <p>Recognising this interface introduces no privilege escalation: the
 * substitute permission is bound to the specific permission instance asserted
 * at the guard site, and the protection domain must still imply that
 * substitute, so it can only admit a domain that already holds it.
 *
 * @see DelegatePermission
 * @author Peter Firmstone
 * @since 3.1.1
 */
public interface PermissionDelegate {

    /**
     * Returns the permission to check against a {@link ProtectionDomain} when
     * this permission is not itself directly implied by that domain.
     *
     * @return the substitute permission to check; never {@code null}.
     */
    Permission getPermissionToCheck();
}
