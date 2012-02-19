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

import java.security.CodeSource;
import java.security.ProtectionDomain;
import org.apache.river.api.security.PermissionGrant;

/**
 *
 * @author peter
 */
public interface ConcurrentPolicy {
    
    public boolean isConcurrent();
    
    /**
     * Returns a new array containing immutable PermissionGrant's, the array
     * returned is not shared.
     * 
     * Only those PermissionGrant's that imply the domain will be returned.
     * 
     * This allows the top level policy to gather all PermissionGrant's,
     * retrieve all relevant permissions, then sort them using PermissionComparator
     * or any other Comparator, so Permission's are added to a PermissionCollection
     * in the most efficient order.
     * 
     * @param domain 
     * @return PermissionGrant []
     */
    public PermissionGrant[] getPermissionGrants(ProtectionDomain domain);
    
    /**
     * Retrieves all PermissionGrant's from the underlying policy.
     * @return
     */
    public PermissionGrant[] getPermissionGrants();
}
