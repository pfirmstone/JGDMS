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
package net.jini.jeri;

import java.security.AccessController;
import java.security.CodeSource;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.security.AdminAuthorizationContext;

/**
 * Seam-side assembly of the {@link AdminAuthorizationContext} handed from the
 * JERI dispatch seam to a pure downstream permission gate (2026-07-21
 * board-ratified "Option B").  This is the single place that implements steps
 * 1&ndash;3 of the ratified authorization recipe; the gate does steps 4&ndash;7.
 *
 * <ol>
 *   <li><b>Capture the caller's code sources.</b> The reducing domains already
 *       reconstructed by {@link RemoteContextCodec#unmarshal} (from the
 *       {@code CodeSource}/{@code DigestCodeSource} block, never the wire
 *       principals) supply the caller's code sources.  When none were
 *       transmitted, a single empty {@code CodeSource} stands in, mirroring
 *       {@code BasicInvocationDispatcher.checkClientPermission}'s
 *       {@code emptyPD}.</li>
 *   <li><b>Capture the authenticated worker principal.</b> Taken from the
 *       connection-derived worker {@link Subject}
 *       ({@code BasicInvocationDispatcher.getClientSubject()} &mdash; the
 *       verified mTLS/SPIFFE identity), never a live re-read of
 *       {@code Subject.processWorker()} downstream.</li>
 *   <li><b>Validate USER tokens / derive principals.</b> Already done upstream
 *       by {@code readUserSubjects}, which reconstructs each user
 *       {@link Subject}'s principals from its verified JWT claims alone
 *       (dropping every unverified wire principal).  This assembler consumes
 *       exactly those {@code userSubjects}; it never re-verifies or re-derives.</li>
 * </ol>
 *
 * <p>The assembled domains each carry the caller's captured code source stamped
 * with the <em>union</em> of the authenticated worker principals and the
 * verified user principals, built as value-equality
 * {@code java.security.DomainIdentity}s (via
 * {@link RemoteContextCodec#reducingDomain}) so the reconstructed context
 * interns in the {@code AccessControlContext} cache.  A principal-only policy
 * grant to any of those principals is therefore implied by every domain; a
 * downstream {@code AccessControlContext.create(...)} over the array ANDs them.
 */
final class AdminAuthorizationAssembler {

    /** The empty {@code CodeSource} used when no reducing context was sent. */
    private static final CodeSource EMPTY_CODE_SOURCE =
            new CodeSource(null, (Certificate[]) null);

    private AdminAuthorizationAssembler() {}

    /**
     * Assembles the caller's admin authorization identity.
     *
     * @param remoteDomains the reducing domains reconstructed from the ACC
     *        block (each already carrying the worker principals), or
     *        {@code null}/empty if none were transmitted
     * @param workerSubject the connection-derived authenticated worker
     *        {@link Subject}, or {@code null}
     * @param userSubjects  the user {@link Subject}s whose principals were
     *        reconstructed from verified JWTs, or {@code null}/empty
     * @return the value-only carrier for the pure gate; never {@code null}
     */
    static AdminAuthorizationContext assemble(ProtectionDomain[] remoteDomains,
                                              Subject workerSubject,
                                              List<Subject> userSubjects) {
        // Steps 2 + 3: union of authenticated worker principals and verified
        // user principals.  These -- and only these -- become the authorization
        // identity; no wire principals reach here (readUserSubjects dropped them).
        Set<Principal> all = new LinkedHashSet<Principal>();
        if (workerSubject != null) {
            all.addAll(workerSubject.getPrincipals());
        }
        if (userSubjects != null) {
            for (Subject s : userSubjects) {
                if (s != null) all.addAll(s.getPrincipals());
            }
        }
        Principal[] principals = all.toArray(new Principal[0]);

        // Step 1: stamp those principals onto each captured code source.  An
        // empty CodeSource stands in when no reducing context was transmitted,
        // so the gate still evaluates the principals alone (never granting
        // code-scoped authority).
        List<ProtectionDomain> domains = new ArrayList<ProtectionDomain>();
        if (remoteDomains != null && remoteDomains.length > 0) {
            for (ProtectionDomain d : remoteDomains) {
                if (d == null) continue;
                domains.add(RemoteContextCodec.reducingDomain(
                        d.getCodeSource(), principals));
            }
        }
        if (domains.isEmpty()) {
            domains.add(RemoteContextCodec.reducingDomain(
                    EMPTY_CODE_SOURCE, principals));
        }

        Subject[] users = (userSubjects == null)
                ? new Subject[0]
                : userSubjects.toArray(new Subject[0]);
        final ProtectionDomain[] pds = domains.toArray(new ProtectionDomain[0]);
        final Subject[] finalUsers = users;
        // Mint an AUTHENTICATED carrier: seamMint checks
        // AdminAuthorizationMintPermission, which the deployed policy grants only
        // to this (jgdms-jeri) code source.  A carrier the downstream gate will
        // honor can be produced ONLY here.  Run under this trusted seam's own
        // privilege (doPrivileged) so the mint permission is evaluated against
        // THIS code source alone and does not go viral to any less-privileged
        // frame that may be on the dispatch stack.
        return AccessController.doPrivileged(
                new PrivilegedAction<AdminAuthorizationContext>() {
            @Override
            public AdminAuthorizationContext run() {
                return AdminAuthorizationContext.seamMint(pds, finalUsers);
            }
        });
    }
}
