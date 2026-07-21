/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import net.jini.security.AdminAuthorizationContext;
import net.jini.security.AdminAuthorizationScope;
import net.jini.security.Security;

/**
 * The <em>mechanism</em> that decides whether a remote caller may reach the
 * subprocess policy admin surface (task&nbsp;T2, requirement&nbsp;#3).  This is
 * the fail-closed gate that guards every access to the subprocess policy admin
 * surface; it never consults what interfaces a caller's stub declares.
 *
 * <h2>2026-07-21 board-ratified "Option B": assemble at the seam, gate is
 * pure</h2>
 * The gate is a pure
 * <em>identity&nbsp;&rarr;&nbsp;permit/deny</em> function.  It does <strong>not
 * </strong> determine the caller's identity itself &mdash; it neither reads the
 * ambient {@code Subject.current()}/{@code Subject.currentAll()} nor
 * {@code Subject.processWorker()}.  Instead the identity is assembled once, at
 * the trusted JERI dispatch seam ({@code net.jini.jeri.BasicInvocationDispatcher}),
 * which alone can trustworthily determine it: the seam holds the verified mTLS
 * connection (the authenticated worker SPIFFE principal) and is the single point
 * that validates the caller's JWT USER tokens and reconstructs their principals
 * from the <em>verified claims alone</em> (dropping every attacker-controlled
 * wire principal).  The seam packages that identity into a value-only
 * {@link AdminAuthorizationContext} carrier that it <em>mints</em> (stamping an
 * unforgeable provenance token &mdash; see
 * {@link net.jini.security.AdminAuthorizationMintPermission}) and conveys via a
 * dedicated {@link net.jini.security.AdminAuthorizationScope} {@code ScopedValue}
 * bound around the dispatch; this gate consumes exactly that carrier and honors
 * it <strong>only</strong> when it is
 * {@linkplain AdminAuthorizationContext#isSeamMinted() seam-minted}.
 *
 * <p>Given the carrier, authorization is a standard JGDMS permission check
 * (recipe steps&nbsp;4&ndash;7):
 * <ol>
 *   <li><b>{@code Subject.callAs} the carrier's user Subjects</b> &mdash;
 *       re-establishes the acting user identity for the granted operation
 *       (belt-and-braces; the seam already established them, and the decision
 *       below rests on the carrier's domain array, not the ambient subject).</li>
 *   <li><b>{@code AccessController.doPrivileged}</b> inside the {@code callAs}
 *       &mdash; stops the permission requirement (and the
 *       {@code AuthPermission("callAs")} the step above needs) going viral to
 *       constrained caller stacks.</li>
 *   <li><b>{@link Security#create(ProtectionDomain[])}</b> over the carrier's
 *       {@code DomainIdentity[]} &mdash; the interning, virtual-thread-safe
 *       factory (never the plain {@code AccessControlContext} constructor, never
 *       {@code ProtectionDomain.implies} directly, which would bypass the
 *       {@code SecurityManager} chokepoint).  Each domain carries the caller's
 *       captured code source stamped with the union of the authenticated worker
 *       principal and the verified user principals, so a principal-only policy
 *       grant to any of them is implied by every domain.</li>
 *   <li><b>{@code SecurityManager.checkPermission(perm, ctx)}</b> with a
 *       {@link SubProcessAdminPermission} whose target is the <em>operation
 *       name</em> ({@code getSubProcessPolicyAdmin}, {@code grant},
 *       {@code refresh}, {@code getGrants}), keeping the installed
 *       {@code SecurityManager} (on DirtyChai, the {@code CombinerSecurityManager})
 *       the single authorization chokepoint.</li>
 * </ol>
 * The deployed policy names the admin: it grants {@code SubProcessAdminPermission}
 * targets to the orchestrating admin principal(s) (e.g. via a
 * {@code PrincipalGrant}).  Per-operation targets allow least privilege &mdash;
 * {@code grant} installs new authority into the subprocess policy and can be
 * granted more narrowly than the read-only {@code getGrants}.  Grant BARE
 * operation names only: wildcard/prefix targets ({@code "*"}, {@code "get*"})
 * really do imply the operation targets and defeat per-operation least
 * privilege, while qualified targets ({@code "some.Interface.grant"}) silently
 * never match &mdash; see {@link SubProcessAdminPermission}'s javadoc for the
 * verified matching semantics.  Because {@link net.jini.security.AccessPermission}
 * matching is class-gated, a grant of a plain {@code AccessPermission} (or any
 * other subclass) never satisfies this gate.
 *
 * <h2>Why an installed {@code SecurityManager} is required</h2>
 * Without an installed {@link SecurityManager} there is nothing to submit the
 * permission check to.  Both {@link #isAdmin} and {@link #requireAdmin}
 * therefore refuse <em>every</em> caller when no {@code SecurityManager} is
 * installed (loud, safe failure rather than silent bypass).
 *
 * <h2>"A SecurityManager is installed" is NECESSARY, not SUFFICIENT</h2>
 * The guarantees this class depends on are deployment/policy prerequisites it
 * cannot verify from inside the JVM:
 * <ul>
 *   <li>the installed {@code SecurityManager}'s policy must grant
 *       {@code SubProcessAdminPermission} targets <em>only</em> to the
 *       orchestrating admin principal(s) &mdash; a permissive
 *       {@code SecurityManager} (or a policy granting these targets to everyone)
 *       authorizes every caller while satisfying the "installed" check;</li>
 *   <li>{@code RuntimePermission("setSecurityManager")} must be denied to
 *       hosted code, or it can replace the restrictive installation with a
 *       permissive one and authorize every caller;</li>
 *   <li><strong>a signature-verifying {@code JwtVerifier} must be registered at
 *       the seam</strong> ({@code BasicInvocationDispatcher.setJwtVerifier}).
 *       Under Option&nbsp;B the caller's USER principals come from the seam's
 *       verified JWT claims; the built-in {@code DefaultJwtVerifier} performs a
 *       structural claims check but <em>signs nothing</em>, so a deployment that
 *       must cryptographically bind a JWT-derived admin identity MUST register a
 *       JWKS-backed verifier &mdash; otherwise a peer can present a self-minted
 *       token bearing the admin's {@code sub} and this gate will authorize over
 *       principals no trust anchor vouched for.  The worker SPIFFE principal is
 *       independently bound by the verified mTLS connection.</li>
 *   <li><strong>carrier provenance and bind</strong> (2026-07-21 forgery +
 *       replay fix): {@code AdminAuthorizationMintPermission("mint")} must be
 *       granted <em>only</em> to the trusted seam code sources &mdash;
 *       <em>both</em> {@code jgdms-jeri} and {@code jgdms-platform}, since the
 *       seam frames above its {@code doPrivileged} span both modules at mint and
 *       at bind time.  The gate honors a carrier only when it is
 *       {@linkplain AdminAuthorizationContext#isSeamMinted() seam-minted}; a
 *       carrier hosted code forges via the public
 *       {@code AdminAuthorizationContext} constructor carries no provenance token
 *       and is refused (fail closed).  The <em>same</em> permission also guards
 *       <em>binding</em> a carrier into the
 *       {@link net.jini.security.AdminAuthorizationScope} {@code ScopedValue}
 *       ({@code bindCarrier}/{@code runWithCarrier}): the provenance token proves
 *       a seam minted the carrier for <em>some</em> caller, not that it belongs
 *       to <em>this</em> call, so without the bind guard hosted code could
 *       capture a live seam-minted carrier via the public {@code currentCarrier()}
 *       and <em>replay</em> it around a directly-held admin reference.  If the
 *       permission were mis-granted to hosted code, that code could mint a carrier
 *       naming any principal (forgery) or bind a captured one (replay) and defeat
 *       this gate, so the grant belongs to the seam and no one else.  The
 *       {@code ScopedValue} conveyance itself is additional defence-in-depth.</li>
 *   <li><strong>seam ClassLoader isolation</strong>:
 *       {@code net.jini.jeri.AdminAuthorizationAssembler.assemble} is
 *       package-private and mints under its own {@code doPrivileged}, so any class
 *       loaded into the <em>same runtime package</em> {@code net.jini.jeri} could
 *       mint an arbitrary-principal carrier without holding the mint permission.
 *       The barrier is runtime-package access control: hosted/business code MUST
 *       be loaded by a <em>different</em> {@code ClassLoader} than the seam (as the
 *       preferred-class-loader isolation ensures), so it is in a different runtime
 *       package and cannot reach {@code assemble}.  A deployment must not flatten
 *       hosted code onto the {@code jgdms-jeri} loader.</li>
 * </ul>
 * These attack shapes are exercised as permanent regressions in
 * {@code IsolationSecurityCriticalTest}.
 *
 * <h2>Fail-closed identity</h2>
 * When no carrier is available (no {@code ScopedValue} binding, e.g. a same-JVM
 * caller that never dispatched through the seam, or an injected source that
 * yields {@code null}), the gate authorizes against a synthetic domain carrying
 * <em>no</em> principals &mdash; only a policy that grants
 * {@code SubProcessAdminPermission} to everyone would authorize it.  A carrier
 * whose principal set is empty, <em>and a carrier that is not seam-minted</em>,
 * are treated identically (refused unless the policy grants to everyone).
 *
 * @since 3.1.1
 */
public final class AdminPrincipalAuthenticator {

    /**
     * Supplies the {@link AdminAuthorizationContext} the seam assembled for the
     * current remote call.  Injectable so the gate is unit-testable without a
     * live transport; the default {@link #CURRENT_CONTEXT} resolves it from the
     * {@link net.jini.security.AdminAuthorizationScope} {@code ScopedValue} the
     * seam binds.
     */
    public interface AdminContextSource {
        /**
         * @return the carrier the JERI dispatch seam assembled for the current
         *         call, or {@code null} if none is available (the caller is then
         *         treated as unauthenticated and refused, fail-closed).
         */
        AdminAuthorizationContext current();
    }

    /**
     * {@code Subject.callAs(Callable, Subject...)} (DirtyChai varargs form),
     * resolved reflectively so this class stays {@code -release 8}
     * source-compatible.  {@code null} on a JDK lacking it.
     */
    private static final Method CALL_AS_MULTI;

    /**
     * {@code Subject.callAs(Subject, Callable)} (JDK&nbsp;18+ form), resolved
     * reflectively for the same reason.  {@code null} on a pre-18 JDK, in which
     * case the user Subjects are not re-established (the decision rests on the
     * carrier's domain array regardless).
     */
    private static final Method CALL_AS_SINGLE;
    static {
        Method multi = null;
        Method single = null;
        try {
            multi = Subject.class.getMethod("callAs", Callable.class, Subject[].class);
        } catch (NoSuchMethodException | SecurityException ignored) {
            // Pre-DirtyChai / pre-18 JDK: varargs callAs unavailable.
        }
        try {
            single = Subject.class.getMethod("callAs", Subject.class, Callable.class);
        } catch (NoSuchMethodException | SecurityException ignored) {
            // Pre-18 JDK: single-Subject callAs unavailable.
        }
        CALL_AS_MULTI = multi;
        CALL_AS_SINGLE = single;
    }

    private static final Subject[] NO_SUBJECTS = new Subject[0];

    /**
     * The empty {@link CodeSource} the synthetic no-principal domain is built
     * over &mdash; used only for the fail-closed case where no carrier is
     * available.
     */
    private static final CodeSource EMPTY_CODE_SOURCE =
            new CodeSource(null, (Certificate[]) null);

    /**
     * The synthetic domain used when no carrier is available: empty
     * {@link CodeSource}, no permissions, no class loader, no principals.
     */
    private static final ProtectionDomain EMPTY_PROTECTION_DOMAIN =
            new ProtectionDomain(EMPTY_CODE_SOURCE, null, null, null);

    /**
     * The default {@link AdminContextSource}: resolves the carrier the JERI
     * dispatch seam bound for the current remote call via the dedicated
     * {@link AdminAuthorizationScope} {@code ScopedValue} (NOT the public
     * {@code ServerContext} ThreadLocal &mdash; which any code could publish and
     * which is unsafe under virtual threads).  Resolves to {@code null} outside a
     * seam dispatch (no binding) or when the seam bound none &mdash; the caller
     * is then refused (fail closed).  The provenance check on the returned
     * carrier is applied centrally in {@link #authorize}; a carrier that is not
     * {@linkplain AdminAuthorizationContext#isSeamMinted() seam-minted} is
     * refused there.
     */
    public static final AdminContextSource CURRENT_CONTEXT = new AdminContextSource() {
        @Override
        public AdminAuthorizationContext current() {
            // Never throws; unbound / absent ScopedValue -> null (fail closed).
            return AdminAuthorizationScope.currentCarrier();
        }
    };

    private final AdminContextSource contextSource;

    /**
     * @param contextSource source of the current call's seam-assembled
     *        {@link AdminAuthorizationContext}; must not be null
     */
    public AdminPrincipalAuthenticator(AdminContextSource contextSource) {
        if (contextSource == null) {
            throw new NullPointerException("contextSource");
        }
        this.contextSource = contextSource;
    }

    /**
     * Checks whether the caller identified by this authenticator's
     * {@link AdminContextSource} is authorized for the named admin operation.
     * Obtains the seam-assembled {@link AdminAuthorizationContext} and delegates
     * to the pure {@link #isAdmin(AdminAuthorizationContext, String)} function.
     *
     * @param operation the guarded operation name; must not be null
     * @return {@code true} iff a {@link SecurityManager} is installed AND the
     *         deployed policy grants {@code SubProcessAdminPermission(operation)}
     *         to the seam-supplied identity.  Always {@code false} when no
     *         {@code SecurityManager} is installed.
     * @throws NullPointerException if {@code operation} is null
     * @throws IllegalArgumentException if {@code operation} does not match
     *         {@link net.jini.security.AccessPermission}'s target syntax
     */
    public boolean isAdmin(String operation) {
        // Construct/validate BEFORE fetching the carrier so a null/invalid
        // operation name is a caller bug (NPE/IAE), never masked as a refusal.
        final SubProcessAdminPermission perm =
                new SubProcessAdminPermission(operation);
        AdminAuthorizationContext carrier;
        try {
            carrier = AccessController.doPrivileged(
                    new PrivilegedAction<AdminAuthorizationContext>() {
                @Override
                public AdminAuthorizationContext run() {
                    return contextSource.current();
                }
            });
        } catch (SecurityException identityDenied) {
            // The source itself was refused (or an injected source threw):
            // treat as unauthenticated rather than let a SecurityException from
            // identity ACQUISITION escape as if it were the authorization verdict.
            carrier = null;
        }
        return authorize(carrier, perm);
    }

    /**
     * The pure gate: {@code (identity, operation) -> permit/deny}.  Authorizes
     * the named admin operation over exactly the principals the seam supplied in
     * {@code carrier}, doing recipe steps&nbsp;4&ndash;7 (see the class javadoc).
     * Reads no ambient subject and no worker state.
     *
     * @param carrier   the seam-assembled identity, or {@code null} for an
     *        unauthenticated caller (authorized against a no-principal domain,
     *        i.e. refused unless the policy grants to everyone)
     * @param operation the guarded operation name; must not be null
     * @return {@code true} iff a {@link SecurityManager} is installed AND the
     *         policy grants {@code SubProcessAdminPermission(operation)} to the
     *         carrier's principals
     * @throws NullPointerException if {@code operation} is null
     * @throws IllegalArgumentException if {@code operation} is syntactically
     *         invalid
     */
    public static boolean isAdmin(AdminAuthorizationContext carrier,
                                  String operation) {
        return authorize(carrier, new SubProcessAdminPermission(operation));
    }

    private static boolean authorize(final AdminAuthorizationContext carrier0,
                                     final SubProcessAdminPermission perm) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return false;
        }
        // PROVENANCE GATE (load-bearing forgery defence).  Honor ONLY a carrier
        // the trusted JERI seam minted under AdminAuthorizationMintPermission.  A
        // carrier hosted code fabricated via the public AdminAuthorizationContext
        // constructor is NOT seam-minted; treat it exactly like "no carrier"
        // (authorized against the no-principal EMPTY_PROTECTION_DOMAIN, i.e.
        // refused unless the policy grants to everyone).  This holds no matter
        // HOW the forged carrier reached us -- a forged ScopedValue binding, a
        // legacy ServerContext element, or an injected AdminContextSource --
        // because the check is on the token the seam alone can mint, not on the
        // conveyance.
        final AdminAuthorizationContext carrier =
                (carrier0 != null && carrier0.isSeamMinted()) ? carrier0 : null;
        final ProtectionDomain[] domains;
        final Subject[] users;
        if (carrier == null) {
            domains = new ProtectionDomain[] { EMPTY_PROTECTION_DOMAIN };
            users = NO_SUBJECTS;
        } else {
            ProtectionDomain[] d = carrier.domains();
            domains = (d.length == 0)
                    ? new ProtectionDomain[] { EMPTY_PROTECTION_DOMAIN }
                    : d;
            users = carrier.userSubjects();
        }
        final boolean[] permitted = { false };
        // Step 4 (callAs users) wraps step 5 (doPrivileged) wraps steps 6-7
        // (create + checkPermission).  The whole thing runs under this library's
        // own privilege so neither AuthPermission("callAs") nor the operation's
        // permission requirement goes viral to constrained caller stacks.
        final Runnable check = new Runnable() {
            @Override
            public void run() {
                try {
                    AccessController.doPrivileged(new PrivilegedAction<Void>() {
                        @Override
                        public Void run() {
                            AccessControlContext ctx = Security.create(domains);
                            sm.checkPermission(perm, ctx);
                            return null;
                        }
                    });
                    permitted[0] = true;
                } catch (SecurityException denied) {
                    // permitted stays false; swallow here so it does not have to
                    // cross Subject.callAs (which would wrap it in a
                    // CompletionException).
                }
            }
        };
        try {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    runAsUsers(users, check);
                    return null;
                }
            });
        } catch (SecurityException callAsDenied) {
            // The user identity could not even be established
            // (AuthPermission("callAs") denied to this library) -> not admin.
            return false;
        }
        return permitted[0];
    }

    /**
     * Establishes {@code users} via {@code Subject.callAs} (reflectively, for
     * {@code -release 8} source compatibility) and runs {@code check} inside.
     * When {@code users} is empty, or no {@code callAs} is available (pre-18
     * JDK), {@code check} is run directly &mdash; the authorization decision
     * rests on the carrier's domain array regardless of the ambient subject.
     */
    private static void runAsUsers(Subject[] users, final Runnable check) {
        if (users == null || users.length == 0) {
            check.run();
            return;
        }
        final Callable<Void> action = new Callable<Void>() {
            @Override
            public Void call() {
                check.run();
                return null;
            }
        };
        try {
            if (CALL_AS_MULTI != null && users.length > 1) {
                CALL_AS_MULTI.invoke(null, action, (Object) users);
            } else if (CALL_AS_SINGLE != null) {
                CALL_AS_SINGLE.invoke(null, users[0], action);
            } else {
                check.run();
            }
        } catch (InvocationTargetException ite) {
            Throwable c = ite.getCause();
            // Unwrap a CompletionException the callAs wrapper may add.
            if (c != null
                    && "java.util.concurrent.CompletionException".equals(
                            c.getClass().getName())
                    && c.getCause() != null) {
                c = c.getCause();
            }
            if (c instanceof SecurityException) throw (SecurityException) c;
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            if (c instanceof Error) throw (Error) c;
            throw new IllegalStateException("admin gate callAs failed", c);
        } catch (IllegalAccessException iae) {
            // Should never happen: Subject.callAs is public.
            throw new IllegalStateException(
                    "Subject.callAs not accessible", iae);
        }
    }

    /**
     * Fail-closed assertion: throws unless {@link #isAdmin(String)} holds for
     * {@code operation}.
     *
     * @param operation the guarded operation name, used as the
     *        {@link SubProcessAdminPermission} target and in the exception
     *        message
     * @throws NullPointerException if {@code operation} is null
     * @throws IllegalArgumentException if {@code operation} does not match
     *         {@link net.jini.security.AccessPermission}'s target syntax
     * @throws SecurityException if no {@link SecurityManager} is installed, or
     *         if the deployed policy does not grant
     *         {@code SubProcessAdminPermission(operation)} to the seam-supplied
     *         identity
     */
    public void requireAdmin(String operation) {
        // Validate eagerly: a null/invalid operation name throws NPE/IAE here,
        // never reported as a SecurityException naming "null".
        new SubProcessAdminPermission(operation);
        if (System.getSecurityManager() == null) {
            throw new SecurityException(
                "Refused: no SecurityManager is installed in this JVM ("
                + operation + " denied, fail-closed). This gate authorizes by"
                + " submitting SubProcessAdminPermission(\"" + operation
                + "\") against the seam-assembled caller identity to the"
                + " installed SecurityManager; with none installed there is"
                + " nothing to submit the check to. NOTE: installing a"
                + " SecurityManager is NECESSARY but NOT SUFFICIENT -- its"
                + " policy must grant SubProcessAdminPermission targets only to"
                + " the orchestrating admin principal(s), must deny"
                + " RuntimePermission(\"setSecurityManager\") to untrusted/hosted"
                + " code, and a signature-verifying JwtVerifier must be"
                + " registered at the JERI seam; see this class's javadoc.");
        }
        if (!isAdmin(operation)) {
            throw new SecurityException(
                "Refused: the deployed policy does not grant"
                + " SubProcessAdminPermission(\"" + operation + "\") to the"
                + " seam-assembled caller identity; " + operation
                + " denied (fail-closed). Interface declaration is not"
                + " authority; an authenticated, policy-granted identity is.");
        }
    }
}
