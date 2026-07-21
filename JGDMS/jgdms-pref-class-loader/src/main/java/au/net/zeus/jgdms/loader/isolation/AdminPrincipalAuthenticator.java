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
 *       injectable {@link CallerIdentity} (the default,
 *       {@link #CURRENT_SUBJECT}, resolves it in three tiers &mdash; see
 *       below).  This step is <em>only</em> how the caller's principals are
 *       obtained; it is no longer the authorization decision itself.</li>
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
 *       as this class already duplicates the {@code Subject.processWorker()}
 *       / {@code Subject.current()} reflective lookups.)</li>
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
 *       chokepoint.  The whole check runs inside
 *       {@link AccessController#doPrivileged(PrivilegedAction)} so this
 *       library's own privilege performs it and the operation's permission
 *       requirements do not go viral to constrained caller stacks.</li>
 * </ol>
 * The deployed policy names the admin: it grants
 * {@code SubProcessAdminPermission} targets to the orchestrating admin
 * principal(s) (e.g. via a {@code PrincipalGrant}).  Per-operation targets
 * allow least privilege &mdash; {@code grant} installs new authority into
 * the subprocess policy and can be granted more narrowly than the read-only
 * {@code getGrants}.  Because {@link net.jini.security.AccessPermission}
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
 * The highest-priority identity tier &mdash; the DirtyChai
 * {@code Subject.processWorker()} ambient {@code WorkerSubject} &mdash; is
 * deliberately outside the {@code AuthPermission} list: it takes no
 * caller-supplied {@link Subject} to bind, so there is nothing for hosted
 * code to forge by calling it.
 *
 * <h2>The three-tier caller-identity lookup (2026-07-21, retained: it now
 * only OBTAINS the principals)</h2>
 * The "current caller identity" is obtained from an injectable
 * {@link CallerIdentity} so the gate is unit-testable without live TLS; the
 * default, {@link #CURRENT_SUBJECT}, resolves the ambient {@link Subject}
 * in three tiers, most-specific first: the DirtyChai
 * {@code Subject.processWorker()} ambient {@code WorkerSubject} minted by
 * the SSLEngine/TLS+SPIFFE keystone for a TLS-authenticated remote caller
 * (structurally invisible to the other two tiers &mdash; see
 * {@link #CURRENT_SUBJECT}'s javadoc); else {@code Subject.current()}
 * (JDK&nbsp;18+), bound via {@code Subject.callAs}/{@code doAs}; else the
 * legacy access-control-context read.  An unauthenticated caller (no
 * resolvable {@code Subject}) is checked against an empty synthetic domain
 * carrying no principals, exactly as
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
     * {@code Subject.processWorker()} on the DirtyChai JDK &mdash; the
     * ambient, stamped-not-logged-in {@code WorkerSubject} that the
     * SSLEngine/TLS+SPIFFE keystone mints for a TLS-authenticated remote
     * caller (see {@code net.jini.jeri.ssl.Utilities#spiffeWorkerSubject()},
     * the canonical instance of this exact lookup idiom, which this field and
     * {@link #findProcessWorker()} mirror). Looked up reflectively so this
     * class stays buildable on the vanilla build JDK, where the method is
     * absent; {@code jgdms-pref-class-loader} does not depend on
     * {@code jgdms-jeri}, so the lookup is duplicated locally rather than
     * shared. {@code null} on a JDK too old/vanilla to provide it, in which
     * case the {@link #CURRENT_METHOD} tier below is tried next.
     */
    private static final Method PROCESS_WORKER = findProcessWorker();

    private static Method findProcessWorker() {
        try {
            return Subject.class.getMethod("processWorker");
        } catch (NoSuchMethodException e) {
            return null;
        }
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
     * The current caller's authenticated {@link Subject}, read in the same
     * three-tier priority order the rest of JGDMS's SSLEngine/TLS+SPIFFE
     * keystone uses:
     * <ol>
     *   <li>{@code Subject.processWorker()} (DirtyChai JDK only) &mdash; the
     *       ambient {@code WorkerSubject} minted for a TLS-authenticated
     *       remote caller. This identity is, by the {@code WorkerSubject}
     *       contract, never bound via {@code Subject.doAs}/{@code callAs}
     *       (see {@code net.jini.jeri.ssl.Utilities}'s comment on
     *       {@code isAmbientWorkerSubject}, right after
     *       {@code findWorkerSubject()}) and so is <strong>structurally
     *       invisible</strong> to {@code Subject.current()} and to the
     *       legacy {@code getSubject(AccessControlContext)} path below;
     *       {@code Subject.processWorker()} is the only way to observe it;</li>
     *   <li>else {@code Subject.current()} (JDK&nbsp;18+), bound via
     *       {@code Subject.callAs}/{@code Subject.doAs} &mdash; the caller
     *       authenticated some other way (e.g. a real JAAS login) that IS
     *       captured that way;</li>
     *   <li>else the legacy
     *       {@code Subject.getSubject(AccessController.getContext())}.</li>
     * </ol>
     * Both reflective lookups resolve to {@code null} on a JDK lacking the
     * corresponding method, in which case that tier is silently skipped. On a
     * modern JDK with the SecurityManager disabled the legacy call throws
     * {@link UnsupportedOperationException}; that is caught and treated as
     * "no authenticated caller" (fail closed) &mdash; unchanged from before.
     *
     * <p>Under the 2026-07-21 redesign (see the class javadoc) the resolved
     * {@code Subject} is no longer itself the authorization decision: its
     * principals populate the synthetic {@link ProtectionDomain} the
     * {@link SubProcessAdminPermission} check is evaluated against.
     */
    public static final CallerIdentity CURRENT_SUBJECT = new CallerIdentity() {
        @Override
        public Subject current() {
            if (PROCESS_WORKER != null) {
                try {
                    Subject worker = (Subject) PROCESS_WORKER.invoke(new Subject());
                    if (worker != null) {
                        return worker;
                    }
                } catch (Exception ignored) {
                    // Fall through to Subject.current() below.
                }
            }
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
     * check runs inside {@link AccessController#doPrivileged(PrivilegedAction)}
     * so this library's own privilege performs it and the permission
     * requirement does not go viral to constrained caller stacks.
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
     * @throws NullPointerException if {@code operation} is null
     * @throws IllegalArgumentException if {@code operation} does not match
     *         {@link net.jini.security.AccessPermission}'s target syntax
     */
    public boolean isAdmin(String operation) {
        final SecurityManager sm = System.getSecurityManager();
        final SubProcessAdminPermission perm =
                new SubProcessAdminPermission(operation);
        if (sm == null) {
            return false;
        }
        Subject s = callerIdentity.current();
        final ProtectionDomain pd;
        if (s == null) {
            pd = EMPTY_PROTECTION_DOMAIN;
        } else {
            Set<Principal> principals = s.getPrincipals();
            pd = new ProtectionDomain(EMPTY_CODE_SOURCE, null, null,
                    principals.toArray(new Principal[principals.size()]));
        }
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
     * @throws SecurityException if no {@link SecurityManager} is installed
     *         (there is nothing to submit the permission check to, and this
     *         class cannot verify the ambient {@link Subject} it would read
     *         is genuine rather than forged; see the class javadoc), or if
     *         the deployed policy does not grant
     *         {@code SubProcessAdminPermission(operation)} to the current
     *         caller's authenticated principals
     */
    public void requireAdmin(String operation) {
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
