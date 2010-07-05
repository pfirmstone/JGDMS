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
 * A Denied implementation must be immutable, it will be accessed by concurrent
 * code.  To be utilised the implementation must have the RuntimePermission
 * "getProtectionDomain"
 * 
 * The implementation may deny a Permission on any grounds, but it must be
 * consistent and return the same result on every occassion.
 * 
 * Denied must implement a robust implementation of equals() and hashCode()
 * 
 * Denied can only deny Permissions that are Revokeable, Permission's granted
 * by underlying Policy's cannot be denied, as the Permission's will become
 * merged within a ProtectionDomain's own Permissions collection.
 * 
 * Under certain circumstances it may be possible to deny any and all Permission
 * to ProtectionDomains if we have control of the creation of those
 * ProtectionDomain's, passing in a null PermissionCollection.
 * The underlying Policy also must not grant that ProtectionDomain any Permissions,
 * only under these circumstances is it possible to Denied any or all Permission.
 * 
 * Denied code must be kept to a minimum, as every implies() will be checked.
 * 
 * Denied may also be used in combination with a PermissionGrant based on
 * Code signer Certificate's to deny individual CodeSource's or URL's with
 * known vulnerabilities.
 * 
 * @author Peter Firmstone
 */
public interface Denied {
    /**
     * Denied is used by a RevokeableDynamicPolicy, to cut short permission checks
     * or allow the Policy.implies method to continue to execute to its
     * normal conclusion.
     * 
     * @param pd
     * @param perm may be null;
     * @return
     */
    public boolean allow(ProtectionDomain pd, Permission perm);
}
