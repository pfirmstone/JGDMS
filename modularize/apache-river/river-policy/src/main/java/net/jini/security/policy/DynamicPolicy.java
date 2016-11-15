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

package net.jini.security.policy;

import java.security.Permission;
import java.security.Principal;

/**
 * Interface implemented by security policy providers that may support dynamic
 * granting of permissions at run-time.  The <code>grant</code> methods of the
 * {@link net.jini.security.Security} class delegate to the
 * methods declared by this interface when this interface is implemented by the
 * installed security policy provider.  Permissions are granted on the
 * granularity of class loader; granting a permission requires (of the calling
 * context) {@link net.jini.security.GrantPermission} for that
 * permission.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public interface DynamicPolicy {
    /**
     * Returns <code>true</code> if this policy provider supports dynamic
     * permission grants; returns <code>false</code> otherwise.  Note that this
     * method may return different values for a given
     * <code>DynamicPolicy</code> instance, depending on context.  For example,
     * a policy provider that delegates to different underlying policy
     * implementations depending on thread state would return <code>true</code>
     * from this method when the current delegate supports dynamic permission
     * grants, but return <code>false</code> when another delegate lacking such
     * support is in effect.
     *
     * @return  <code>true</code> if policy supports dynamic permission grants
     * 		under current context, <code>false</code> otherwise
     */
    boolean grantSupported();

    /**
     * If this security policy provider supports dynamic permission grants,
     * grants the specified permissions to all protection domains (including
     * ones not yet created) that are associated with the class loader of the
     * given class and possess at least the given set of principals.  If the
     * given class is <code>null</code>, then the grant applies across all
     * protection domains that possess at least the specified principals.  If
     * the list of principals is <code>null</code> or empty, then principals
     * are effectively ignored in determining the protection domains to which
     * the grant applies.  If this policy provider does not support dynamic
     * permission grants, then no permissions are granted and an
     * <code>UnsupportedOperationException</code> is thrown.
     * <p>
     * The given class, if non-<code>null</code>, must belong to either the
     * system domain or a protection domain whose associated class loader is
     * non-<code>null</code>.  If the class does not belong to such a
     * protection domain, then no permissions are granted and an
     * <code>UnsupportedOperationException</code> is thrown.
     * <p>
     * If a security manager is installed, its <code>checkPermission</code>
     * method is called with a <code>GrantPermission</code> containing the
     * permissions to grant; if the permission check fails, then no permissions
     * are granted and the resulting <code>SecurityException</code> is thrown.
     * The principals and permissions arrays passed in are neither modified nor
     * retained; subsequent changes to the arrays have no effect on the grant
     * operation.
     *
     * @param   cl class to grant permissions to the class loader of, or
     * 		<code>null</code> if granting across all class loaders
     * @param	principals if non-<code>null</code>, minimum set of principals
     * 		to which grants apply
     * @param	permissions if non-<code>null</code>, permissions to grant
     * @throws	UnsupportedOperationException if policy does not support
     *          dynamic grants, or if <code>cl</code> is non-<code>null</code>
     *          and belongs to a protection domain with a <code>null</code>
     *          class loader other than the system domain
     * @throws	SecurityException if a security manager is installed and the
     *          calling context does not have sufficient permissions to grant
     *          the given permissions
     * @throws	NullPointerException if any element of the principals or
     *          permissions arrays is <code>null</code>
     */
    void grant(Class cl, Principal[] principals, Permission[] permissions);

    /**
     * If this security policy provider supports dynamic permission grants,
     * returns a new array containing the cumulative set of permissions
     * dynamically granted to protection domains (including ones not yet
     * created) that are associated with the class loader of the given class
     * and possess at least the given set of principals.  If the given class is
     * <code>null</code>, then this method returns the cumulative set of
     * permissions dynamically granted across all protection domains that
     * possess at least the specified principals (i.e., through calls to the
     * grant method where the specified class was <code>null</code>).  If the
     * list of principals is <code>null</code> or empty, then the permissions
     * returned reflect only grants not qualified by principals (i.e., those
     * performed through calls to the grant method where the specified
     * principals array was <code>null</code> or empty).  If this policy
     * provider does not support dynamic permission grants, then an
     * <code>UnsupportedOperationException</code> is thrown.
     * <p>
     * The given class, if non-<code>null</code>, must belong to either the
     * system domain or a protection domain whose associated class loader is
     * non-<code>null</code>.  If the class does not belong to such a
     * protection domain, then an <code>UnsupportedOperationException</code> is
     * thrown.
     *
     * @param   cl class to query the permissions dynamically granted to the
     *          class loader of, or <code>null</code> if querying permissions
     *          granted across all class loaders
     * @param   principals if non-<code>null</code>, principals to query
     * 		dynamic grants for
     * @return  new array containing the permissions dynamically granted to the
     *          indicated class loader (if any) and principals
     * @throws	UnsupportedOperationException if policy does not support
     *          dynamic grants, or if <code>cl</code> is non-<code>null</code>
     *          and belongs to a protection domain with a <code>null</code>
     *          class loader other than the system domain
     * @throws	NullPointerException if any element of the principals array is
     *          <code>null</code>
     */
    Permission[] getGrants(Class cl, Principal[] principals);
}
