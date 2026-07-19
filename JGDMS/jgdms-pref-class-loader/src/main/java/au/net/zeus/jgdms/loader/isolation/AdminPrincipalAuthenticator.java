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
 * this gate is the in-process floor that holds even if a proxy reference is
 * captured and re-invoked.
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
     * @return {@code true} iff the current caller is authenticated as the
     *         orchestrating admin principal.
     */
    public boolean isAdmin() {
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
     * @throws SecurityException if the current caller is not the admin
     */
    public void requireAdmin(String operation) {
        if (!isAdmin()) {
            throw new SecurityException(
                "Refused: caller is not authenticated as the orchestrating"
                + " admin principal; " + operation + " denied (fail-closed)."
                + " Interface declaration is not authority; authentication is.");
        }
    }
}
