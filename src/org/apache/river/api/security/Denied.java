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
 * The implementation may deny a ProtectionDomain on any grounds, but it must be
 * consistent and return the same result on every occassion.
 * 
 * Denied will be used in combination with a PermissionGrant based on
 * Code signer Certificate's to deny individual CodeSource's or URL's with
 * known vulnerabilities.
 * 
 * @author Peter Firmstone
 */
public interface Denied {
    /**
     * Denied is used by a PermissionGrantBuilder, to filter out unwanted
     * ProtectionDomain implies based on any set of conditions.  This is useful
     * for a PermissionGrant that generically grant's for one 
     * 
     * @param pd
     * @param perm may be null;
     * @return
     */
    public boolean allow(ProtectionDomain pd);
}
