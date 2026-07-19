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
package au.net.zeus.jgdms.api.policy;

import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jini.security.GrantPermission;
import org.apache.river.api.security.AdvisoryPermissionParser;

/**
 * Runtime-callable analogue of the offline {@code
 * org.apache.river.tool.ProxyPolicyGenerator}: given an authoritative
 * {@link RegistryVerdict} for a specific proxy JAR, the JAR's own declared
 * needs (its {@code META-INF/PERMISSIONS.LIST}), and the {@link GrantPermission}
 * authority the orchestrating party itself holds, this computes the
 * <em>permission ceiling</em> that an isolated subprocess hosting that proxy
 * may be granted.
 *
 * <p>This is the {@code f(RegistryVerdict, contentHash, declaredNeeds) ->
 * Permission[]} function called by the {@code SubProcessDynamicPolicy}
 * machinery (SOW-SubProcessDynamicPolicy T2) at the moment a subprocess needs
 * its authority ceiling established.  It performs no I/O beyond the optional
 * {@link #parseDeclaredNeeds(InputStream, ClassLoader) PERMISSIONS.LIST parse}
 * helper, and holds no ambient state — it is a deterministic, side-effect-free
 * mapping so that its security behaviour is unit-testable and auditable.
 *
 * <h2>Preconditions (enforced upstream, trusted here)</h2>
 * <ul>
 *   <li>The {@code verdict}'s inline signature has <b>already been verified</b>
 *       against the known-good registry public key (see
 *       {@code PreferredProxyCodebaseProvider.checkVerdictForJar} /
 *       {@link RegistryVerdict#verifySignature}).  This function does
 *       <em>not</em> re-verify the signature; a forged verdict must be rejected
 *       before it reaches here.</li>
 *   <li>A {@code DANGEROUS} verdict has <b>already been refused</b> upstream —
 *       the codebase never loads, so this function is never legitimately called
 *       for one.  Invoking it with a {@code DANGEROUS} verdict is a caller bug
 *       and fails fast (see {@link #computeSubProcessCeiling}).</li>
 * </ul>
 *
 * <h2>Mapping policy (the security decision)</h2>
 * <dl>
 *   <dt>{@link VerdictType#SAFE}</dt>
 *   <dd>The ceiling is the JAR's own declared needs (from {@code
 *       PERMISSIONS.LIST}), <b>bounded by the {@link GrantPermission} the
 *       orchestrating party itself holds</b>.  A declared permission is admitted
 *       <em>only if</em> the caller's own {@code GrantPermission} authorises
 *       granting it (i.e. {@code callerGrantCeiling.implies(new
 *       GrantPermission(p))}); anything the caller could not itself grant is
 *       silently dropped.  A SAFE verdict therefore never lets a proxy obtain
 *       more authority than the granting party already holds, regardless of what
 *       the verdict or a crafted {@code PERMISSIONS.LIST} claims.  Grant-meta and
 *       all-authority permissions ({@link GrantPermission},
 *       {@code UmbrellaGrantPermission}, {@link java.security.AllPermission}) are
 *       never treated as a proxy's declared needs and are always excluded (they
 *       are policy-grant capabilities, not declared needs) — mirroring
 *       {@code ProxyPolicyGenerator}.</dd>
 *
 *   <dt>{@link VerdictType#INCONCLUSIVE}</dt>
 *   <dd>A strictly narrower ceiling than SAFE, gated exactly as the existing
 *       {@code net.jini.loader.pref.INCONCLUSIVEPermit} concept gates
 *       <em>loading</em> an INCONCLUSIVE JAR: an explicit, per-content-hash,
 *       auditable tolerance must be present, and absent it the result is
 *       <b>fail-closed</b>.  When the caller does not hold the tolerance for
 *       this {@code contentHash} ({@code inconclusiveToleranceGranted == false}),
 *       the ceiling is <b>empty</b> — the proxy may load (INCONCLUSIVE is not
 *       refused like DANGEROUS) but receives <em>zero</em> dynamically-granted
 *       authority.  When the tolerance <em>is</em> held, the ceiling is the same
 *       caller-bounded, meta-excluded computation as SAFE.  The extra
 *       per-hash tolerance gate <em>is</em> the narrowing, mirroring
 *       INCONCLUSIVEPermit's philosophy rather than inventing a separate
 *       permission-filtering scheme.  Because platform code cannot depend on the
 *       pref-class-loader module where {@code INCONCLUSIVEPermit} lives, the
 *       tolerance is passed in as a boolean; the caller <b>must</b> source it
 *       from an {@code AccessController.checkPermission(new
 *       INCONCLUSIVEPermit(contentHash))} check (or the wildcard {@code "*"}
 *       form) and pass {@code false} on any {@code SecurityException} — never a
 *       hard-coded {@code true}.</dd>
 *
 *   <dt>{@link VerdictType#DANGEROUS}</dt>
 *   <dd>Out of scope: refused upstream before any ceiling is computed.  Passing
 *       one here throws {@link IllegalStateException} (defensive fail-fast, not a
 *       real path).</dd>
 * </dl>
 *
 * <p><b>Trust boundary.</b> The {@code callerGrantCeiling} and {@code
 * inconclusiveToleranceGranted} arguments describe the <em>orchestrating
 * party's own</em> authority; this function trusts the (authenticated,
 * more-trusted) orchestrator to state its own authority honestly, and only
 * guarantees that the computed ceiling never exceeds what those arguments
 * describe.  Authenticating the orchestrator is SOW-SubProcessDynamicPolicy
 * T3's job, not this function's.
 *
 * @since 4.0.0
 * @author Peter Firmstone
 */
public final class VerdictPermissionMapper {

    /**
     * Permission classes that are policy-grant meta-capabilities or blanket
     * all-authority, never a proxy's legitimate declared needs, so always
     * excluded from a computed ceiling (matching {@code
     * ProxyPolicyGenerator}'s exclusion set).
     */
    private static final Set<String> META_PERMISSION_CLASSES;
    static {
        Set<String> s = new java.util.HashSet<String>();
        s.add("net.jini.security.GrantPermission");
        s.add("net.jini.security.policy.UmbrellaGrantPermission");
        s.add("java.security.AllPermission");
        META_PERMISSION_CLASSES = Collections.unmodifiableSet(s);
    }

    private static final Permission[] EMPTY = new Permission[0];

    private VerdictPermissionMapper() {
        throw new AssertionError("no instances");
    }

    /**
     * Computes the permission ceiling an isolated subprocess hosting the
     * verdict's proxy JAR may be granted.  See the class documentation for the
     * full per-verdict mapping policy.
     *
     * @param verdict                     the (already signature-verified)
     *        authoritative verdict for the JAR; must be non-null and must be
     *        {@link VerdictType#SAFE} or {@link VerdictType#INCONCLUSIVE}
     * @param contentHash                 lower-case SHA-256 hex digest of the
     *        JAR the ceiling is being computed for; must be non-null and
     *        non-empty (used for the INCONCLUSIVE tolerance semantics and for
     *        audit)
     * @param declaredNeeds               the proxy's own declared needs, e.g.
     *        from {@link #parseDeclaredNeeds}; may be null or empty (treated as
     *        no declared needs)
     * @param callerGrantCeiling          the {@link GrantPermission} the
     *        orchestrating party itself holds; the computed ceiling never
     *        exceeds this.  {@code null} means the orchestrator holds no grant
     *        authority, so the ceiling is empty
     * @param inconclusiveToleranceGranted whether the orchestrator holds an
     *        {@code INCONCLUSIVEPermit} for this {@code contentHash} (or the
     *        wildcard form).  Ignored for a SAFE verdict.  For an INCONCLUSIVE
     *        verdict, {@code false} yields an empty ceiling (fail-closed).  MUST
     *        be sourced from a real permit check, never hard-coded {@code true}
     * @return a fresh, de-duplicated array of the permissions the subprocess may
     *         be granted; never null, possibly empty
     * @throws IllegalArgumentException if {@code verdict} is null, {@code
     *         contentHash} is null/empty
     * @throws IllegalStateException    if the verdict is {@link
     *         VerdictType#DANGEROUS} (must have been refused upstream) or is an
     *         unrecognised verdict type (fail-closed)
     */
    public static Permission[] computeSubProcessCeiling(
            RegistryVerdict verdict,
            String contentHash,
            Permission[] declaredNeeds,
            GrantPermission callerGrantCeiling,
            boolean inconclusiveToleranceGranted) {
        if (verdict == null) {
            throw new IllegalArgumentException("verdict must not be null");
        }
        if (contentHash == null || contentHash.isEmpty()) {
            throw new IllegalArgumentException("contentHash must not be null or empty");
        }
        VerdictType type = verdict.getVerdict();
        if (type == null) {
            // A well-formed RegistryVerdict cannot carry a null verdict, but do
            // not assume it: fail closed rather than fall through to a default.
            throw new IllegalStateException("verdict type must not be null");
        }
        switch (type) {
            case SAFE:
                return boundedCeiling(declaredNeeds, callerGrantCeiling);
            case INCONCLUSIVE:
                if (!inconclusiveToleranceGranted) {
                    // No INCONCLUSIVEPermit for this hash: zero ceiling.  The
                    // proxy may load, but receives no dynamically-granted
                    // authority.  This is the strict narrowing relative to SAFE.
                    return EMPTY;
                }
                return boundedCeiling(declaredNeeds, callerGrantCeiling);
            case DANGEROUS:
                // Must never reach here: a DANGEROUS verdict is refused upstream
                // before any codebase loads.  Fail fast rather than silently
                // compute a ceiling for a codebase that should not run at all.
                throw new IllegalStateException(
                        "DANGEROUS verdict must be refused upstream; "
                        + "computeSubProcessCeiling must not be called for it "
                        + "(contentHash=" + contentHash + ")");
            default:
                // A transitional/unexpected VerdictType value (e.g. a future
                // enum constant): fail closed rather than default to a broad
                // ceiling.
                throw new IllegalStateException(
                        "Unrecognised verdict type: " + type + " (fail-closed)");
        }
    }

    /**
     * The SAFE / INCONCLUSIVE-tolerated computation: declared needs, minus
     * grant-meta / all-authority permissions, bounded by what the caller's own
     * {@link GrantPermission} authorises granting.
     */
    private static Permission[] boundedCeiling(Permission[] declaredNeeds,
                                               GrantPermission callerGrantCeiling) {
        if (declaredNeeds == null || declaredNeeds.length == 0) {
            return EMPTY;
        }
        if (callerGrantCeiling == null) {
            // Orchestrator holds no grant authority: it can grant nothing,
            // regardless of what the proxy declares.
            return EMPTY;
        }
        // LinkedHashMap: stable order, de-duplicated by equality.
        Map<Permission, Permission> admitted =
                new LinkedHashMap<Permission, Permission>();
        for (Permission p : declaredNeeds) {
            if (p == null) {
                continue;
            }
            if (isMetaPermission(p)) {
                // Never let a proxy's declared needs widen its own ceiling with
                // a grant-meta or all-authority permission, even if the caller
                // happens to hold it.
                continue;
            }
            if (callerAuthorises(callerGrantCeiling, p)) {
                admitted.put(p, p);
            }
            // else: caller cannot itself grant p, so the ceiling excludes it.
        }
        return admitted.values().toArray(new Permission[admitted.size()]);
    }

    /**
     * @return {@code true} if {@code callerGrantCeiling} authorises granting
     *         {@code p} — i.e. the caller's own {@code GrantPermission} implies
     *         {@code GrantPermission(p)}.  Any error constructing or evaluating
     *         the wrapper fails closed (returns {@code false}).
     */
    private static boolean callerAuthorises(GrantPermission callerGrantCeiling,
                                            Permission p) {
        try {
            return callerGrantCeiling.implies(new GrantPermission(p));
        } catch (RuntimeException | LinkageError e) {
            // A permission that cannot be wrapped/evaluated is not something we
            // can prove the caller may grant: exclude it.
            return false;
        }
    }

    /**
     * @return {@code true} if {@code p} is a grant-meta or all-authority
     *         permission that must never appear in a proxy's declared-needs
     *         ceiling.  Handles {@link UnresolvedPermission} by inspecting its
     *         unresolved target type, so an unresolved {@code AllPermission} /
     *         {@code GrantPermission} cannot slip past by class name.
     */
    private static boolean isMetaPermission(Permission p) {
        if (META_PERMISSION_CLASSES.contains(p.getClass().getName())) {
            return true;
        }
        if (p instanceof UnresolvedPermission) {
            String unresolvedType = ((UnresolvedPermission) p).getUnresolvedType();
            return unresolvedType != null
                    && META_PERMISSION_CLASSES.contains(unresolvedType);
        }
        return false;
    }

    /**
     * Parses a proxy JAR's {@code META-INF/PERMISSIONS.LIST} content into its
     * declared-needs permission array, using the same line-based grammar and
     * {@link AdvisoryPermissionParser} the {@code PreferredClassLoader} uses at
     * class-load time.  Blank lines and {@code #} / {@code //} comments are
     * ignored.  A permission whose class cannot be resolved by {@code loader}
     * becomes an {@link UnresolvedPermission} (as elsewhere in JGDMS), which the
     * ceiling computation treats fail-closed.
     *
     * <p>This is a convenience so callers need not re-implement the parse; the
     * returned array is suitable to pass as {@code declaredNeeds} to
     * {@link #computeSubProcessCeiling}.
     *
     * @param permissionsList UTF-8 {@code PERMISSIONS.LIST} content; must be
     *        non-null.  The stream is fully read but not closed (the caller owns
     *        it)
     * @param loader          the class loader used to resolve permission
     *        classes; may be null (the bootstrap loader)
     * @return a fresh array of declared permissions; never null, possibly empty
     * @throws IOException if reading the stream fails
     */
    public static Permission[] parseDeclaredNeeds(InputStream permissionsList,
                                                  ClassLoader loader)
            throws IOException {
        if (permissionsList == null) {
            throw new IllegalArgumentException("permissionsList must not be null");
        }
        List<Permission> perms = new ArrayList<Permission>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(permissionsList, StandardCharsets.UTF_8));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            String trim = line.trim();
            if (trim.isEmpty() || trim.startsWith("#") || trim.startsWith("//")) {
                continue;
            }
            perms.add(AdvisoryPermissionParser.parse(line, loader));
        }
        return perms.toArray(new Permission[perms.size()]);
    }
}
