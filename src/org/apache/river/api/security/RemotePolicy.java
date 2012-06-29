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

import java.io.IOException;
import net.jini.security.GrantPermission;
import net.jini.security.policy.UmbrellaGrantPermission;

/**
 * <p>
 * RemotePolicy is a service api that can be implemented by a distributed Policy service, 
 * allowing local Policy providers to be updated remotely by a djinn group administrator.
 * </p><p>
 * No service implementation has been provided, DynamicPolicyProvider does
 * implement this interface to simplify creation of such a service.
 * </p>
 * <h2>Notes for implementors:</h2>
 * <p>
 * For security purposes, only secure jeri Endpoint's should be used and must
 * require client and server authentication, in addition the proxy must be a 
 * reflective proxy only, as DownloadPermission should not be granted, which is 
 * also beneficial to reduced network load at the administrator client.  
 * RemotePolicy may be submitted to a lookup service, where an administrator 
 * client will look it up and replace PermissionGrant's periodically.
 * </p><p>
 * To reduce network load, the administrator client may delay updates by
 * lazily processing updates in a serial manner.  New RemotePolicy services
 * obtained by the administrator client's via RemoteEvent notification should
 * be processed as a priority over policy updates.  Eventually a djinn group
 * policy should reach equilibrium where all nodes have had their policy's 
 * updated.
 * </p><p>
 * This policy, in addition to any local policy provider, allows a network djinn
 * administrator to provide a list of PermissionGrant's, from a single or 
 * replicated remote location,  distributed to all nodes in a djinn that 
 * administrator is responsible for.  Every time the administrator updates
 * his network policy, he can use a RemoteEvent notification system to be
 * notified for any new RemotePolicy registrations.
 * </p><p>
 * In addition, replicating administrator clients may register a pseudo RemotePolicy
 * in order to track the primary administrator client and take over in the
 * event it fails.  Failure may be failure to authenticate or failure to renew
 * a Lease.
 * </p><p>
 * RemotePolicy, if it encapsulates an underlying RemotePolicy, does not
 * delegate updates to the base RemotePolicy, this is in case an
 * implementer wants a number of different layers of RemotePolicy, where
 * each layer represents a different administrator role or responsibility.  
 * The administrator's subject must hold the necessary permissions in order
 * to grant them, including RuntimePermission("getProtectionDomain").
 * </p><p>
 * A node may join more than one djinn group, in this case RemotePolicy's may
 * be used as nested basePolicy's.
 * </p><p>
 * The intent of RemotePolicy is for granting of DowloadPermission to
 * new signer Certificates and adding new Principals and Permission's to
 * distributed policy providers.
 * </p><p>
 * Local policy files should be used to restrict the Permissions grantable
 * via a RemotePolicy.
 * </p><p>
 * PermissionGrant's that are replaced and no longer exist in the RemotePolicy
 * will no longer be implied by the policy.
 * </p><p>
 * DefaultPolicyParser has been provided for an administrator client to
 * parse standard java format policy file's, to create PermissionGrant's.
 * </p>
 * @author Peter Firmstone
 * @since 2.2.1
 * @see GrantPermission
 * @see UmbrellaGrantPermission
 * @see PolicyParser
 * @see DefaultPolicyParser
 * @see DefaultPolicyScanner
 */
public interface RemotePolicy {
    /**
     * Replaces the existing RemotePolicy's PermissionGrant's.
     * 
     * The array is defensively copied, the caller, must have 
     * RuntimePermission("getProtectionDomain")
     * as well as GrantPermission or UmbrellaGrantPermission for every
     * Permission granted by each PermissionGrant.
     * 
     * If the calling Subject doesn't have sufficient permission, the 
     * first permission that fails will include the SecurityException as the
     * cause of the thrown IOException.
     * 
     * Permissions required by the callers Subject should be set in the 
     * local policy files at the RemotePolicy server.
     * 
     * Where an IOException is thrown, no update to the
     * RemotePolicy has occurred.
     * 
     * @param policyPermissions
     * @throws java.io.IOException 
     */
    public void replace(PermissionGrant[] policyPermissions) throws IOException;
}
