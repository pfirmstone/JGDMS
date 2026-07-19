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
import java.security.AccessController;
import java.security.Principal;
import java.util.Set;
import javax.security.auth.Subject;

/**
 * The <em>mechanism</em> that decides whether the current caller is the
 * orchestrating admin principal (task&nbsp;T2, requirement&nbsp;#3).  This is
 * the fail-closed gate that guards every access to the subprocess policy
 * admin surface; it never consults what interfaces a caller's stub declares.
 *
 * <p>Enforcement is by <em>proven authenticated identity</em>: the caller must
 * be executing within a {@link Subject} that carries the orchestrating admin
 * principal.  Matching is by canonical name (via
 * {@link IsolationPoolingKey#canonicalName(Principal)}), consistent with
 * JGDMS's {@code PrincipalGrant} precedent of identifying principals by
 * canonical name rather than object identity.
 *
 * <p>The "current caller identity" is obtained from an injectable
 * {@link CallerIdentity} so the gate is unit-testable without live TLS; the
 * default reads the {@link Subject} from the current access-control context,
 * i.e. the identity the caller is authenticated and executing as. In the live
 * transport (task&nbsp;T4) the stricter endpoint {@code MethodConstraints}
 * additionally enforce this at the wire before dispatch ever reaches here;
 * this gate is intended as the in-process floor that holds even if a proxy
 * reference is captured and re-invoked.
 *
 * <h2>Why that claim requires an installed {@code SecurityManager}
 * (2026-07-20 board finding, fixed)</h2>
 * Name-matching against {@code Subject.current()}'s principals is, by
 * itself, <strong>not</strong> proof of authenticated identity: any code
 * running in this JVM can execute
 * {@code new Subject(true, Set.of(forgedAdminPrincipal), Set.of(), Set.of())}
 * and bind it as the ambient subject via {@code Subject.doAs}/
 * {@code Subject.callAs} &mdash; construction and binding are both
 * unprivileged operations. The <em>only</em> thing that makes that binding
 * require authority is the JDK's own {@code AuthPermission("callAs")/AuthPermission("doAs")}
 * check inside {@code Subject.doAs}/{@code callAs} &mdash; and the JDK
 * performs that check <strong>only when a {@link SecurityManager} is
 * installed</strong>; with none installed it is skipped entirely, so any
 * hosted/business code (e.g. inside {@code SubProcessReconstructionServer
 * #dispatchInvoke}, which does not itself wrap hosted-method execution in a
 * {@code doAs} boundary) can forge the ambient {@code Subject} at will and
 * this gate would otherwise trust it verbatim. This is exactly the same
 * "NO-OP without an installed SecurityManager" shape as
 * {@code DeSerializationPermission("ATOMIC")} elsewhere in JGDMS &mdash; the
 * difference is that class's javadoc did not previously say so.
 *
 * <p><strong>Fix:</strong> {@link #isAdmin()} and {@link #requireAdmin} both
 * check {@link System#getSecurityManager()} first and refuse
 * <em>every</em> caller &mdash; including a genuinely authenticated admin
 * &mdash; when no {@code SecurityManager} is installed, rather than trust an
 * ambient {@code Subject} this class has no way to verify was legitimately
 * bound. This turns the previous silent bypass into a loud, safe failure:
 * the "holds even if a reference is captured and re-invoked" guarantee is
 * now actually true whenever a {@code SecurityManager} is installed with a
 * policy that does not grant {@code AuthPermission("callAs")/AuthPermission("doAs")} to
 * untrusted/hosted code (the fail-closed default for any protection domain
 * that isn't explicitly granted it) &mdash; and when no such
 * {@code SecurityManager} is present, the gate no longer pretends to enforce
 * a guarantee it cannot back.
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
     * The current caller's authenticated {@link Subject}, read the same way
     * the rest of JGDMS reads it: {@code Subject.current()} where available
     * (bound via {@code Subject.callAs} / {@code Subject.doAs}), else the
     * legacy {@code Subject.getSubject(AccessController.getContext())}.  On a
     * modern JDK with the SecurityManager disabled the legacy call throws
     * {@link UnsupportedOperationException}; that is caught and treated as
     * "no authenticated caller" (fail closed).
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

    private final String requiredAdminCanonicalName;
    private final CallerIdentity callerIdentity;

    /**
     * @param adminPrincipal the orchestrating admin principal that a caller
     *        must prove to reach the policy admin surface; must not be null
     * @param callerIdentity source of the current caller's authenticated
     *        identity; must not be null
     */
    public AdminPrincipalAuthenticator(Principal adminPrincipal,
                                       CallerIdentity callerIdentity) {
        if (adminPrincipal == null) {
            throw new NullPointerException("adminPrincipal");
        }
        if (callerIdentity == null) {
            throw new NullPointerException("callerIdentity");
        }
        this.requiredAdminCanonicalName =
                IsolationPoolingKey.canonicalName(adminPrincipal);
        this.callerIdentity = callerIdentity;
    }

    /**
     * @return {@code true} iff a {@link SecurityManager} is installed AND
     *         the current caller is authenticated as the orchestrating admin
     *         principal. Always {@code false} when no {@code SecurityManager}
     *         is installed &mdash; see the class javadoc: without one, the
     *         ambient {@link Subject} this method reads cannot be trusted to
     *         be genuinely authenticated rather than forged in-process, so
     *         every caller is refused rather than any being trusted on an
     *         unverifiable basis.
     */
    public boolean isAdmin() {
        if (!securityManagerInstalled()) {
            return false;
        }
        Subject s = callerIdentity.current();
        if (s == null) return false;
        Set<Principal> principals = s.getPrincipals();
        if (principals == null || principals.isEmpty()) return false;
        for (Principal p : principals) {
            if (requiredAdminCanonicalName.equals(
                    IsolationPoolingKey.canonicalName(p))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fail-closed assertion: throws unless {@link #isAdmin()} holds.
     *
     * @param operation short description of the guarded operation, for the
     *        exception message
     * @throws SecurityException if no {@link SecurityManager} is installed
     *         (this class cannot verify the ambient {@link Subject} it would
     *         read is genuine rather than forged; see the class javadoc), or
     *         if the current caller is not the admin
     */
    public void requireAdmin(String operation) {
        if (!securityManagerInstalled()) {
            throw new SecurityException(
                "Refused: no SecurityManager is installed in this JVM ("
                + operation + " denied, fail-closed). Subject-name matching"
                + " alone is not proof of identity: without an installed"
                + " SecurityManager enforcing AuthPermission(\"doAs\"), any"
                + " code in this JVM can construct new Subject(true,"
                + " {forged admin principal}, ...) and bind it via"
                + " Subject.doAs/Subject.callAs with no permission check at"
                + " all, defeating the principal-name match this gate"
                + " otherwise performs. This gate cannot distinguish that"
                + " forged Subject from a genuinely authenticated one, so it"
                + " refuses EVERY caller -- including a genuine admin --"
                + " rather than silently trust an unverifiable Subject."
                + " Install a SecurityManager, with a policy that does not"
                + " grant AuthPermission(\"doAs\") to untrusted/hosted"
                + " business-proxy code, to restore admin functionality.");
        }
        if (!isAdmin()) {
            throw new SecurityException(
                "Refused: caller is not authenticated as the orchestrating"
                + " admin principal; " + operation + " denied (fail-closed)."
                + " Interface declaration is not authority; authentication is.");
        }
    }

    /**
     * @return {@code true} iff a {@link SecurityManager} is currently
     *         installed. This is the same idiom used throughout JGDMS (e.g.
     *         {@code PreferredClassProvider},
     *         {@code PreferredProxyCodebaseProvider}) to detect whether the
     *         JDK will actually enforce permission checks -- in particular,
     *         whether {@code Subject.doAs}/{@code callAs} will enforce
     *         {@code AuthPermission("callAs")/AuthPermission("doAs")} against the calling code before
     *         allowing it to rebind the ambient {@link Subject}.
     */
    private static boolean securityManagerInstalled() {
        return System.getSecurityManager() != null;
    }
}
