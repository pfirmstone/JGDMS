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
import java.util.Collection;

/**
 * Policy providers can implement this interface to provide nested policies
 * a common interface to allow delayed creation of PermissionCollection
 * instances until all after all Permission objects are collected, allowing
 * the implementer to add Permission objects to a PermissionCollection in
 * an order that avoids unnecessary reverse DNS calls for example.
 * 
 * @author Peter Firmstone
 */
public interface ScalableNestedPolicy {
    
    /**
     * Returns a new Collection containing immutable PermissionGrant's, the
     * Collection returned is not synchronised and must not be shared with policy 
     * internal state.
     * 
     * Only those PermissionGrant's that imply the domain will be returned.
     * 
     * This allows the top level policy to gather all PermissionGrant's,
     * retrieve all relevant permissions, then sort them using PermissionComparator
     * or any other Comparator, so Permission objects are added to a PermissionCollection
     * in the most efficient order.
     * 
     * If a nested base policy doesn't support ScalableNestedPolicy, then the
     * implementer should create a PermissionGrant for the domain, containing
     * the Permission objects returned from the policy.
     * 
     * The first nested base policy (usually a file policy provider) is
     * responsible for merging any static Permission objects from the
     * ProtectionDomain.
     * 
     * @param domain 
     * @return Collection<PermissionGrant>  
     */
    public Collection<PermissionGrant> getPermissionGrants(
                                                ProtectionDomain domain);
}
