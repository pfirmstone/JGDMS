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

import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.security.Security;

/**
 * The <em>mechanism</em> that decides whether the current caller may reach
 * the subprocess policy admin surface (task&nbsp;T2, requirement&nbsp;#3).
 * This is the fail-closed gate that guards every access to the subprocess
 * policy admin surface; it never consults what interfaces a caller's stub
 * declares.
 *
 * <h2>2026-07-21 ratified redesign: authorization by
 * {@link SubProcessAdminPermission}, not principal-name matching</h2>
 * Previous revisions of this class authorized by looping over the caller's
 * principals and comparing canonical names against a single configured
 * "admin principal".  That conflated <em>authentication</em> (which
 * principals the caller proved) with <em>authorization</em> (what those
 * principals are allowed to do), hard-wired a single all-or-nothing admin
 * identity, and could not distinguish the dangerous {@code grant} operation
 * from the read-only {@code getGrants}.  Authorization is now a standard
 * JGDMS permission check:
 * <ol>
 *   <li>The caller's authenticated {@link Subject} is obtained from the
 *       injectable {@link CallerIdentity}.  The default,
 *       {@link #CURRENT_SUBJECT}, resolves the <em>ambient</em>
 *       ({@code Subject.callAs}/{@code doAs}-bound) subject only &mdash; see
 *       below, including the <strong>normative deployment requirement</strong>
 *       that remote TLS+SPIFFE dispatch inject a connection-derived
 *       {@code CallerIdentity} instead.  This step is <em>only</em> how the
 *       caller's principals are obtained; it is no longer the authorization
 *       decision itself.</li>
 *   <li>A synthetic {@link ProtectionDomain} is built over an empty
 *       {@link CodeSource} carrying exactly those principals ({@code null}
 *       permissions, {@code null} class loader), mirroring
 *       {@code net.jini.jeri.BasicInvocationDispatcher#checkClientPermission}.
 *       A bare {@code AccessController.checkPermission()} would <strong>not
 *       </strong> work here: a remote TLS/SPIFFE caller's principals are
 *       never on the ambient thread stack &mdash; the useful "call stack"
 *       exists only on the client side of the remote call &mdash; so the
 *       grant that satisfies this check must be to the caller's principals
 *       alone, never qualified by executing code.
 *       ({@code jgdms-pref-class-loader} does not depend on
 *       {@code jgdms-jeri}, so the small idiom is duplicated locally, just
 *       as this class already duplicates the {@code Subject.current()}
 *       reflective lookup.)</li>
 *   <li>An {@link AccessControlContext} over exactly that domain is obtained
 *       via the {@link Security#create(ProtectionDomain[])} <em>factory</em>
 *       (never the {@code AccessControlContext} constructor): on DirtyChai
 *       the factory is virtual-thread-safe and interning, and it performs no
 *       call-stack merge, so the context contains the caller's domain alone.
 *       </li>
 *   <li>{@link SecurityManager#checkPermission(java.security.Permission,
 *       Object) SecurityManager.checkPermission(perm, ctx)} is invoked with
 *       a {@link SubProcessAdminPermission} whose target is the
 *       <em>operation name</em> ({@code getSubProcessPolicyAdmin},
 *       {@code grant}, {@code refresh}, {@code getGrants}), keeping the
 *       installed {@code SecurityManager} (on DirtyChai, the
 *       {@code CombinerSecurityManager}) the single authorization
 *       chokepoint.  The permission check runs inside
 *       {@link AccessController#doPrivileged(PrivilegedAction)}, and the
 *       preceding identity read inside
 *       {@code AccessController.doPrivilegedWithCombiner} (see
 *       {@link #isAdmin(String)} for why the combiner-preserving form is
 *       required), so this library's own privilege performs both and
 *       neither the operation's permission requirement nor the
 *       {@code AuthPermission("getSubject")} the identity tiers need goes
 *       viral to constrained caller stacks.</li>
 * </ol>
 * The deployed policy names the admin: it grants
 * {@code SubProcessAdminPermission} targets to the orchestrating admin
 * principal(s) (e.g. via a {@code PrincipalGrant}).  Per-operation targets
 * allow least privilege &mdash; {@code grant} installs new authority into
 * the subprocess policy and can be granted more narrowly than the read-only
 * {@code getGrants}.  Grant BARE operation names only: wildcard/prefix
 * targets ({@code "*"}, {@code "get*"}) really do imply the operation
 * targets and defeat per-operation least privilege, while qualified
 * targets ({@code "some.Interface.grant"}) silently never match &mdash;
 * see {@link SubProcessAdminPermission}'s javadoc for the verified
 * matching semantics.  Because {@link net.jini.security.AccessPermission}
 * matching is class-gated, a grant of a plain {@code AccessPermission} (or
 * any other subclass) never satisfies this gate.  In the live transport
 * (task&nbsp;T4) the stricter endpoint {@code MethodConstraints}
 * additionally enforce authentication at the wire before dispatch ever
 * reaches here; this gate is the in-process floor that holds even if a
 * proxy reference is captured and re-invoked.
 *
 * <h2>Why an installed {@code SecurityManager} is required
 * (2026-07-20 board finding, retained under the redesign)</h2>
 * Without an installed {@link SecurityManager} there is nothing to submit
 * the permission check to, and the ambient {@link Subject} this class reads
 * cannot be trusted to be genuinely authenticated: any code in this JVM can
 * execute {@code new Subject(true, Set.of(forgedAdminPrincipal), ...)} and
 * bind it via {@code Subject.doAs}/{@code Subject.callAs} &mdash; the JDK's
 * own {@code AuthPermission("callAs")/("doAs")} check inside those methods
 * runs <strong>only when a SecurityManager is installed</strong>.  Both
 * {@link #isAdmin} and {@link #requireAdmin} therefore refuse
 * <em>every</em> caller when no {@code SecurityManager} is installed
 * (loud, safe failure rather than silent bypass).
 *
 * <h2>"A SecurityManager is installed" is NECESSARY, not SUFFICIENT
 * (2026-07-20 two-seat board re-review, retained under the redesign)</h2>
 * The guarantee this class depends on is a deployment/policy prerequisite
 * it cannot verify from inside the JVM:
 * <ul>
 *   <li>the installed {@code SecurityManager}'s policy must grant
 *       {@code SubProcessAdminPermission} targets <em>only</em> to the
 *       orchestrating admin principal(s) &mdash; a permissive
 *       {@code SecurityManager} (or a policy granting these targets to
 *       everyone) authorizes every caller while satisfying the
 *       "installed" check;</li>
 *   <li>{@code AuthPermission("callAs")} and {@code AuthPermission("doAs")}
 *       must be denied to hosted/business protection domains, or hosted
 *       code can bind a forged {@link Subject} carrying the admin
 *       principals and this gate's synthetic domain will genuinely carry
 *       them (the redesign moves the decision to policy, but the
 *       principals it decides over still come from the ambient
 *       {@code Subject});</li>
 *   <li>{@code RuntimePermission("setSecurityManager")} must be denied to
 *       hosted code, or it can replace the restrictive installation with a
 *       permissive one and replay either attack.  Both attack shapes were
 *       reproduced end-to-end and remain permanent regressions in
 *       {@code IsolationSecurityCriticalTest}.</li>
 * </ul>
 * <h2>Caller identity: ambient tiers only, plus a NORMATIVE
 * connection-derived requirement for remote callers (2026-07-21 board
 * round&nbsp;2, finding&nbsp;F1)</h2>
 * The "current caller identity" is obtained from an injectable
 * {@link CallerIdentity} so the gate is unit-testable without live TLS; the
 * default, {@link #CURRENT_SUBJECT}, resolves the <em>ambient</em>
 * {@link Subject} in two tiers: {@code Subject.current()} (JDK&nbsp;18+),
 * bound via {@code Subject.callAs} (always prefer {@code callAs};
 * {@code Subject.doAs} also binds it but is deprecated); else the legacy
 * {@code Subject.getSubject(AccessController.getContext())} read.  These
 * tiers are legitimate for JAAS-login, JWT-derived and same-JVM callers,
 * whose identity IS bound to the calling thread.
 *
 * <p><strong>Normative deployment requirement (T4 wiring):</strong> a
 * remote TLS+SPIFFE caller's principals are <em>never</em> on the
 * receiving thread's ambient subject &mdash; they are established by the
 * server connection.  A deployment that dispatches remote callers to this
 * gate MUST inject a <em>connection-derived</em> {@link CallerIdentity}
 * (the equivalent of
 * {@code org.apache.river.jeri.internal.runtime.Util.getClientSubject()},
 * as consumed by {@code net.jini.jeri.BasicInvocationDispatcher}) through
 * the {@link #AdminPrincipalAuthenticator(CallerIdentity) constructor
 * seam} at T4 wiring time.  With the default {@link #CURRENT_SUBJECT} a
 * remote caller resolves to "unauthenticated" and is refused (fail
 * closed) &mdash; never resolved to some other identity.
 *
 * <p>The DirtyChai {@code Subject.processWorker()} lookup is deliberately
 * <strong>not</strong> an identity tier (board round&nbsp;2 removed it):
 * it returns THIS process's own SPIRE workload identity from the
 * {@code SpiffeCredentialManager} singleton, so by construction it can
 * never deliver a remote caller's principals, and on a configured
 * deployment it always returns non-null &mdash; consulted first it would
 * shadow the genuine ambient tiers and evaluate the subprocess's OWN
 * principals: a bricked admin surface (own SVID not granted), or a
 * confused-deputy authorization of every caller (own SVID granted).
 *
 * <p>An unauthenticated caller (no resolvable {@code Subject}) is checked
 * against an empty synthetic domain carrying no principals, exactly as
 * {@code BasicInvocationDispatcher.checkClientPermission} does &mdash; only
 * a policy that grants {@code SubProcessAdminPermission} to everyone would
 * authorize it.
 *
 * @since 3.1.1
 */
public final class AdminPrincipalAuthenticator {

    /** Supplies the authenticated identity of the current caller. */
    public interface CallerIdentity {
        /**
         * @return the {@link Subject} the current caller is authenticated as,
         *         or {@code null} if the caller is unauthenticated.
         */
        Subject current();
    }

    /**
     * {@code Subject.current()} (JDK&nbsp;18+, and the DirtyChai JDK), resolved
     * reflectively so this class stays {@code -release 8} source-compatible
     * (matching JGDMS's {@code SubjectAwareExecutor} bridging).  {@code null}
     * on a JDK too old to provide it, in which case the legacy
     * {@code Subject.getSubject(AccessControlContext)} path is used.
     */
    private static final Method CURRENT_METHOD;
    static {
        Method m = null;
        try {
            m = Subject.class.getMethod("current");
        } catch (NoSuchMethodException | SecurityException ignored) {
            // Pre-18 JDK: fall back to the legacy access-control-context path.
        }
        CURRENT_METHOD = m;
    }

    /**
     * The empty {@link CodeSource} ({@code null} location and certificates)
     * the synthetic caller domain is built over &mdash; the grant that
     * satisfies this gate must be to the caller's principals alone, never
     * qualified by what code is executing (mirrors
     * {@code BasicInvocationDispatcher}'s {@code emptyCS}).
     */
    private static final CodeSource EMPTY_CODE_SOURCE =
            new CodeSource(null, (Certificate[]) null);

    /**
     * The synthetic domain used for an unauthenticated caller: empty
     * {@link CodeSource}, no permissions, no class loader, no principals
     * (mirrors {@code BasicInvocationDispatcher}'s {@code emptyPD}).
     */
    private static final ProtectionDomain EMPTY_PROTECTION_DOMAIN =
            new ProtectionDomain(EMPTY_CODE_SOURCE, null, null, null);

    /**
     * The current caller's <em>ambient</em> authenticated {@link Subject},
     * read in two tiers:
     * <ol>
     *   <li>{@code Subject.current()} (JDK&nbsp;18+, and the DirtyChai JDK),
     *       bound via {@code Subject.callAs} (preferred; the deprecated
     *       {@code Subject.doAs} also binds it) &mdash; a caller
     *       authenticated in-thread (a real JAAS login, a JWT-derived
     *       subject, a same-JVM {@code callAs} caller);</li>
     *   <li>else the legacy
     *       {@code Subject.getSubject(AccessController.getContext())}.</li>
     * </ol>
     * The reflective lookup resolves to {@code null} on a JDK lacking
     * {@code Subject.current()}, in which case that tier is silently
     * skipped. On a modern vanilla JDK with the SecurityManager disabled
     * the legacy call throws {@link UnsupportedOperationException}; that is
     * caught and treated as "no authenticated caller" (fail closed).
     *
     * <p><strong>This default can never see a remote TLS+SPIFFE
     * caller</strong>: such a caller's principals are connection-derived,
     * not thread-bound, so the deployment MUST inject a connection-derived
     * {@link CallerIdentity} at T4 wiring time instead &mdash; see the
     * class javadoc's normative deployment requirement.  A remote caller
     * reaching this default resolves to {@code null} (unauthenticated,
     * refused fail-closed).  {@code Subject.processWorker()} is
     * deliberately not consulted: it returns this process's own workload
     * identity, never a caller's (see the class javadoc).
     *
     * <p>Under the 2026-07-21 redesign (see the class javadoc) the resolved
     * {@code Subject} is no longer itself the authorization decision: its
     * principals populate the synthetic {@link ProtectionDomain} the
     * {@link SubProcessAdminPermission} check is evaluated against.
     */
    public static final CallerIdentity CURRENT_SUBJECT = new CallerIdentity() {
        @Override
        public Subject current() {
            if (CURRENT_METHOD != null) {
                try {
                    return (Subject) CURRENT_METHOD.invoke(null);
                } catch (Exception ignored) {
                    // Fall through to the legacy path below.
                }
            }
            try {
                return Subject.getSubject(AccessController.getContext());
            } catch (UnsupportedOperationException noSm) {
                // Modern JDK, SecurityManager disabled: no ambient Subject
                // resolvable this way -> fail closed (no admin).
                return null;
            }
        }
    };

    private final CallerIdentity callerIdentity;

    /**
     * @param callerIdentity source of the current caller's authenticated
     *        identity; must not be null
     */
    public AdminPrincipalAuthenticator(CallerIdentity callerIdentity) {
        if (callerIdentity == null) {
            throw new NullPointerException("callerIdentity");
        }
        this.callerIdentity = callerIdentity;
    }

    /**
     * Checks whether the current caller is authorized for the named admin
     * operation: builds a synthetic {@link ProtectionDomain} (empty
     * {@link CodeSource}, {@code null} permissions, {@code null} class
     * loader) carrying the principals of the caller's authenticated
     * {@link Subject} (or no principals if the caller is unauthenticated),
     * obtains an {@link AccessControlContext} over exactly that domain via
     * the {@link Security#create(ProtectionDomain[])} factory, and submits
     * {@code SubProcessAdminPermission(operation)} to the installed
     * {@link SecurityManager}'s two-argument
     * {@code checkPermission(perm, ctx)} &mdash; keeping the
     * {@code SecurityManager} the single authorization chokepoint.  The
     * permission check runs inside
     * {@link AccessController#doPrivileged(PrivilegedAction)}, and the
     * preceding identity read inside
     * {@code AccessController.doPrivilegedWithCombiner} (which preserves
     * the subject's {@code DomainCombiner} &mdash; see the inline comment),
     * so this library's own privilege performs both and neither the
     * permission requirement nor {@code AuthPermission("getSubject")} goes
     * viral to constrained caller stacks.
     *
     * @param operation the guarded operation name, used as the
     *        {@link SubProcessAdminPermission} target
     *        ({@code getSubProcessPolicyAdmin}, {@code grant},
     *        {@code refresh} or {@code getGrants}); must not be null
     * @return {@code true} iff a {@link SecurityManager} is installed AND
     *         the deployed policy grants
     *         {@code SubProcessAdminPermission(operation)} to the current
     *         caller's authenticated principals. Always {@code false} when
     *         no {@code SecurityManager} is installed &mdash; see the class
     *         javadoc: without one there is nothing to submit the check to,
     *         and the ambient {@link Subject} this method reads cannot be
     *         trusted to be genuinely authenticated rather than forged
     *         in-process. <strong>An installed {@code SecurityManager} is
     *         necessary but not sufficient</strong> &mdash; see the class
     *         javadoc's "NECESSARY, not SUFFICIENT" section for the
     *         deployment prerequisites this class cannot itself verify.
     * @throws NullPointerException if {@code operation} is null &mdash;
     *         thrown in preference to the no-{@code SecurityManager}
     *         {@code false}: the permission is deliberately constructed and
     *         validated before that guard, so a caller bug (null/invalid
     *         operation name) is never masked as a mere refusal
     * @throws IllegalArgumentException if {@code operation} does not match
     *         {@link net.jini.security.AccessPermission}'s target syntax
     *         (same precedence as {@code NullPointerException})
     */
    public boolean isAdmin(String operation) {
        final SecurityManager sm = System.getSecurityManager();
        // Constructed BEFORE the no-SecurityManager guard on purpose: a null
        // or syntactically invalid operation name is a caller bug and throws
        // NPE/IAE even when the guard below would otherwise return false.
        final SubProcessAdminPermission perm =
                new SubProcessAdminPermission(operation);
        if (sm == null) {
            return false;
        }
        Subject s;
        try {
            /*
             * Identity acquisition runs under this library's own privilege:
             * every CURRENT_SUBJECT tier (Subject.current() and the legacy
             * getSubject(ACC) read) requires AuthPermission("getSubject")
             * when a SecurityManager is installed, and performed on the
             * ambient stack that requirement would go viral to constrained
             * caller domains. doPrivilegedWithCombiner -- deliberately NOT
             * plain doPrivileged -- preserves the current DomainCombiner:
             * on a vanilla SM-enabled JDK (18..23 with
             * -Djava.security.manager=allow), Subject.current() reads the
             * SubjectDomainCombiner off the AccessControlContext, so plain
             * doPrivileged would drop the combiner and blind that tier; on
             * DirtyChai, Subject.current() reads a ScopedValue that
             * privilege frames do not affect, so preserving the combiner is
             * harmless there (both implementations read and verified,
             * 2026-07-21 board round 2, F2).
             */
            s = AccessController.doPrivilegedWithCombiner(
                    new PrivilegedAction<Subject>() {
                @Override
                public Subject run() {
                    return callerIdentity.current();
                }
            });
        } catch (SecurityException identityDenied) {
            // Fail-closed skip: even this library's own domain was refused
            // AuthPermission("getSubject") (or an injected CallerIdentity
            // threw). Treat the caller as unauthenticated -- the check below
            // then runs against an empty-principal domain -- rather than let
            // a SecurityException from identity ACQUISITION escape this
            // method's boolean contract as if it were the authorization
            // verdict.
            s = null;
        }
        final ProtectionDomain pd;
        if (s == null) {
            pd = EMPTY_PROTECTION_DOMAIN;
        } else {
            // Copy the (synchronized-but-mutable) principal set BEFORE
            // toArray: sizing the array from principals.size() and then
            // filling it races with concurrent Subject modification. Same
            // canonical shape as BasicInvocationDispatcher
            // .checkClientPermission's HashSet copy + toArray(new
            // Principal[0]).
            Set<Principal> principals =
                    new LinkedHashSet<Principal>(s.getPrincipals());
            pd = new ProtectionDomain(EMPTY_CODE_SOURCE, null, null,
                    principals.toArray(new Principal[0]));
        }
        // Deliberate divergence from BasicInvocationDispatcher, which caches
        // these synthetic domains in a weak map (domains.computeIfAbsent)
        // because it sits on the per-call dispatch hot path: this admin gate
        // is a low-frequency administrative path, and the fresh domain is
        // only identity-compared within the single check below, so a cache
        // would add shared state for no measurable win.
        try {
            /*
             * The single caller domain is built into an AccessControlContext
             * via the interning factory (Security.create) and submitted to
             * the installed SecurityManager, all inside doPrivileged: this
             * trusted frame's own privilege (e.g. createAccessControlContext)
             * applies, the factory returns a context of exactly {pd} -- no
             * call-stack merge -- and the permission requirement does not
             * leak to constrained caller stacks. Calling
             * ProtectionDomain.implies directly would bypass the
             * SecurityManager chokepoint; using the AccessControlContext
             * constructor instead of the factory would forgo DirtyChai's
             * virtual-thread-safe interning.
             */
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    AccessControlContext ctx =
                            Security.create(new ProtectionDomain[]{ pd });
                    sm.checkPermission(perm, ctx);
                    return null;
                }
            });
            return true;
        } catch (SecurityException denied) {
            return false;
        }
    }

    /**
     * Fail-closed assertion: throws unless {@link #isAdmin(String)} holds
     * for {@code operation}.
     *
     * @param operation the guarded operation name, used as the
     *        {@link SubProcessAdminPermission} target and in the exception
     *        message
     * @throws NullPointerException if {@code operation} is null &mdash;
     *         thrown in preference to either {@code SecurityException}
     *         below, aligning with {@link #isAdmin(String)}: a caller bug
     *         is never reported as a security refusal
     * @throws IllegalArgumentException if {@code operation} does not match
     *         {@link net.jini.security.AccessPermission}'s target syntax
     *         (same precedence as {@code NullPointerException})
     * @throws SecurityException if no {@link SecurityManager} is installed
     *         (there is nothing to submit the permission check to, and this
     *         class cannot verify the ambient {@link Subject} it would read
     *         is genuine rather than forged; see the class javadoc), or if
     *         the deployed policy does not grant
     *         {@code SubProcessAdminPermission(operation)} to the current
     *         caller's authenticated principals
     */
    public void requireAdmin(String operation) {
        // Validate eagerly, aligning with isAdmin's construct-before-guard
        // contract: a null/invalid operation name throws NPE/IAE here, and
        // is never reported as a SecurityException naming "null".
        new SubProcessAdminPermission(operation);
        if (!securityManagerInstalled()) {
            throw new SecurityException(
                "Refused: no SecurityManager is installed in this JVM ("
                + operation + " denied, fail-closed). This gate authorizes by"
                + " submitting SubProcessAdminPermission(\"" + operation
                + "\") against the caller's authenticated principals to the"
                + " installed SecurityManager; with none installed there is"
                + " nothing to submit the check to, and the ambient Subject"
                + " the principals are read from cannot be trusted either:"
                + " without an installed SecurityManager enforcing"
                + " AuthPermission(\"callAs\")/AuthPermission(\"doAs\"), any"
                + " code in this JVM can construct new Subject(true, {forged"
                + " admin principal}, ...) and bind it via"
                + " Subject.callAs/Subject.doAs with no permission check at"
                + " all. So it refuses EVERY caller -- including a genuine"
                + " admin -- rather than silently trust an unverifiable"
                + " Subject. NOTE: installing a SecurityManager is NECESSARY"
                + " but NOT SUFFICIENT -- its policy must grant"
                + " SubProcessAdminPermission targets only to the"
                + " orchestrating admin principal(s), and must deny"
                + " AuthPermission(\"callAs\"), AuthPermission(\"doAs\") AND"
                + " RuntimePermission(\"setSecurityManager\") to"
                + " untrusted/hosted business-proxy code; see this class's"
                + " javadoc for why all of these are required.");
        }
        if (!isAdmin(operation)) {
            throw new SecurityException(
                "Refused: the deployed policy does not grant"
                + " SubProcessAdminPermission(\"" + operation + "\") to the"
                + " current caller's authenticated principals; " + operation
                + " denied (fail-closed). Interface declaration is not"
                + " authority; an authenticated, policy-granted identity is.");
        }
    }

    /**
     * @return {@code true} iff a {@link SecurityManager} is currently
     *         installed. This is the same idiom used throughout JGDMS (e.g.
     *         {@code PreferredClassProvider},
     *         {@code PreferredProxyCodebaseProvider}) to detect whether the
     *         JDK will actually enforce permission checks. <strong>This
     *         alone does not verify the installed instance's policy grants
     *         {@code SubProcessAdminPermission} only to the admin
     *         principal(s), nor that it denies
     *         {@code AuthPermission("callAs")/("doAs")} and
     *         {@code RuntimePermission("setSecurityManager")} to hosted
     *         code</strong> &mdash; see the class javadoc's "NECESSARY, not
     *         SUFFICIENT" section.
     */
    private static boolean securityManagerInstalled() {
        return System.getSecurityManager() != null;
    }
}
