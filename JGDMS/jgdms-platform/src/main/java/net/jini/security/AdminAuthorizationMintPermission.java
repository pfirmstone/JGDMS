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
package net.jini.security;

import java.security.BasicPermission;

/**
 * The permission the trusted JERI dispatch seam must hold to mint an
 * <em>authenticated</em> {@link AdminAuthorizationContext} carrier &mdash; the
 * unforgeable provenance guard behind the 2026-07-21 "Option&nbsp;B" admin gate.
 *
 * <h2>Why this exists</h2>
 * {@link AdminAuthorizationContext} has a <strong>public</strong> constructor so
 * it remains a value type any code may build, but a carrier built that way is
 * <em>unauthenticated</em>: it carries no provenance token and the downstream
 * admin gate ({@code au.net.zeus.jgdms.loader.isolation.AdminPrincipalAuthenticator})
 * refuses it (fail closed).  The <em>only</em> way to obtain an authenticated
 * carrier is {@link AdminAuthorizationContext#seamMint(java.security.ProtectionDomain[],
 * javax.security.auth.Subject[])}, which performs
 * {@code SecurityManager.checkPermission(new AdminAuthorizationMintPermission("mint"))}
 * before stamping the carrier's private provenance token.
 *
 * <h2>Deployment requirement (load-bearing)</h2>
 * The deployed policy MUST grant {@code AdminAuthorizationMintPermission "mint"}
 * <strong>only</strong> to the trusted {@code jgdms-jeri} code source (the module
 * that owns {@code net.jini.jeri.BasicInvocationDispatcher} /
 * {@code AdminAuthorizationAssembler}).  Hosted / untrusted code must never be
 * granted it: with the grant it could mint a carrier naming any principal and
 * defeat the admin gate; without it, {@code seamMint} throws
 * {@link SecurityException} and hosted code cannot produce an authenticated
 * carrier.
 *
 * <p>This is a <em>distinct</em> {@link java.security.Permission} class, so a
 * broad {@code SecurityPermission "*"} (or any other permission) grant does not
 * imply it &mdash; only an explicit {@code AdminAuthorizationMintPermission}
 * grant (or {@code AllPermission}) does.  Grant it to the seam module and to no
 * one else.
 *
 * @since 3.1.1
 */
public final class AdminAuthorizationMintPermission extends BasicPermission {

    private static final long serialVersionUID = 1L;

    /**
     * @param name the target; the seam uses {@code "mint"}.  A policy grant of
     *        {@code "mint"} (or {@code "*"}) to the seam code source authorizes
     *        minting.
     */
    public AdminAuthorizationMintPermission(String name) {
        super(name);
    }

    /**
     * @param name    the target (as {@link #AdminAuthorizationMintPermission(String)})
     * @param actions ignored; accepted for {@code Permission} deserialization
     *        symmetry only
     */
    public AdminAuthorizationMintPermission(String name, String actions) {
        super(name, actions);
    }
}
