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
import org.apache.river.api.common.Beta;

/**
 * <p>
 * RemotePolicy is a service api that can be implemented by a distributed Policy service, 
 * allowing local Policy providers to be updated remotely by a djinn group administrator.
 * </p>
 * <h2>Notes for implementors:</h2>
 * <p>
 * For security purposes, only secure jeri Endpoint's should be used and must
 * require client and server authentication, in addition the proxy must be a 
 * reflective proxy only, as DownloadPermission should not be granted, which is 
 * also beneficial to reduced network load at the administrator client.  
 * RemotePolicy may be submitted to a lookup service, where a group administrator 
 * will replace PermissionGrant's periodically.
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
 * event it fails.  Failure may be failure to authenticate or Lease expiry.
 * </p><p>
 * RemotePolicy, if it encapsulates another nested RemotePolicy, does not
 * delegate updates to the base RemotePolicy, this is in case an
 * implementer wants a number of different layers of RemotePolicy, where
 * each layer represents a different administrator group role or responsibility.  
 * The administrator's subject must hold the necessary permissions in order
 * to grant them, including GrantPermission and PolicyPermission("REMOTE").
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
 * </p><p>
 * If a node participates in more than one djinn group and registers with more
 * than one lookup service, RemotePolicy's may be nested.
 * </p>
 * 
 * @since 2.2.1
 * @see GrantPermission
 * @see UmbrellaGrantPermission
 * @see PolicyParser
 * @see DefaultPolicyParser
 * @see DefaultPolicyScanner
 * @see PolicyPermission
 */
@Beta
public interface RemotePolicy {
    /**
     * Replaces the existing RemotePolicy's PermissionGrant's.
     * 
     * The array is defensively copied, the caller, must have 
     * RuntimePermission("getProtectionDomain") and PolicyPermission("Remote"),
     * as well as GrantPermission or UmbrellaGrantPermission for every
     * Permission granted by each PermissionGrant.
     * 
     * If the calling Subject doesn't have sufficient permission, the 
     * first permission that fails will be logged locally and the PermissionGrant
     * will not be included in the policy update. SecurityException's are
     * logged as level WARNING, NullPointerException as SEVERE.
     * 
     * No security policy information will be returned directly or by way of exception
     * to avoid providing an attacker with information that could lead to 
     * privilege escalation.
     * 
     * Permissions required by the callers Subject should be set in the 
     * local policy files at the RemotePolicy service server.
     * 
     * Where an IOException is thrown, it should be assumed no update to the
     * RemotePolicy has occurred.  The policy is idempotent and the update may
     * be retried.
     * 
     * PermissionGrant's included in the policy will be the intersection
     * of the Set of PermissionGrant's delivered by the caller and the
     * those authorised by the local policy.  No attempt should
     * be made by the RemotePolicy implementation to grant a subset of Permissions
     * contained in a single PermissionGrant, each individual PermissionGrant should be 
     * either allowed or denied atomically.
     * 
     * The djinn group administrator needn't be concerned if the RemotePolicy
     * node doesn't accept all grants, it is up to the node administrator participating
     * in the djinn to determine trust.
     * 
     * Administrators should group Permissions into PermissionGrant's based
     * on component functionality, if any of the Permissions are not allowed
     * then none of the permissions required for functionality of that component
     * or service will be granted, this is preferred to partial functionality, 
     * which is harder to debug.
     * 
     * Each node participating in a djinn may have up to one RemotePolicy
     * service per group.
     * 
     * @param policyPermissions
     * @throws java.io.IOException 
     */
    public void replace(PermissionGrant[] policyPermissions) throws IOException;
}
