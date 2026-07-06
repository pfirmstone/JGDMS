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

/**
 * Marker for a <em>one-shot</em> {@link PermissionGrant}: authority that must be reported
 * <strong>only</strong> through {@code Policy.impliesOnce}, never through {@code Policy.implies}.
 *
 * <p>A policy that honours this marker (e.g. {@code DynamicPolicyProvider}) must:
 * <ul>
 *   <li><b>exclude</b> one-shot grants from {@code implies(ProtectionDomain, Permission)} &mdash;
 *       so a one-shot decision is never cached by a {@code CachingSecurityManager} and never
 *       recorded into generated policy by {@code SecurityPolicyWriter} (polpAudit); and</li>
 *   <li><b>consult only</b> one-shot grants in {@code impliesOnce(ProtectionDomain, Permission)}
 *       &mdash; the after-{@code implies} probe a one-shot-aware SecurityManager runs.</li>
 * </ul>
 *
 * <p>This is a distinct <em>type</em>, not a flag: a renewable {@link LeasedDelegation} installs a
 * plain {@link LeasedPermissionGrant} (not {@code OneShot}) and is cached/recorded normally; only a
 * human-gated escalation installs a {@link OneShotLeasedPermissionGrant}, so a renewable lease can
 * never be mistaken for one-shot. One-shot grants remain time-bounded by their lease exactly like
 * any {@link LeasedPermissionGrant}; the marker governs only how the policy reports them.
 *
 * @since 3.1.1
 * @see OneShotLeasedPermissionGrant
 * @see LeasedDelegation
 */
public interface OneShot {
}
