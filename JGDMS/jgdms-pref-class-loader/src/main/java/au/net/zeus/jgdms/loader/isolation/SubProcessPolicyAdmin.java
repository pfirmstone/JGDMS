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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.security.Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerAuthentication;

/**
 * The subprocess-side {@link SubProcessAdministrable} implementation: a
 * separately-exported trusted management object, <strong>genuinely distinct
 * from the hosted business proxy</strong> (task&nbsp;T2, requirement&nbsp;#3,
 * criterion&nbsp;S1).
 *
 * <p>Three enforcement layers combine here so that authority rests on
 * authentication, never on interface declaration:
 * <ol>
 *   <li><b>Fail-closed accessor.</b> {@link #getSubProcessPolicyAdmin()}
 *       returns nothing usable unless the current caller is authenticated as
 *       the orchestrating admin principal (via
 *       {@link AdminPrincipalAuthenticator}).  An unauthenticated / wrongly-
 *       authenticated caller gets a {@link SecurityException}, whatever
 *       interfaces its stub declares.</li>
 *   <li><b>Per-operation re-gate.</b> Even a captured {@link PolicyAdmin}
 *       reference is useless to a non-admin: the returned proxy re-checks the
 *       admin identity on <em>every</em> operation before dispatching to the
 *       backing management object.</li>
 *   <li><b>Stricter endpoint constraints.</b> The returned proxy is a
 *       {@link RemoteMethodControl} whose {@link #adminConstraints
 *       MethodConstraints} require {@code Integrity.YES},
 *       {@code ClientAuthentication.YES}, {@code ClientMinPrincipal(admin)} and
 *       {@code ServerAuthentication.YES}.  In the live transport (task&nbsp;T4)
 *       this is the wire boundary; the constraints are fixed by the subprocess
 *       and cannot be weakened by a client
 *       ({@link #setConstraints(MethodConstraints)} does not relax them).</li>
 * </ol>
 *
 * <p>The backing {@link PolicyAdmin} passed to the constructor is the trusted
 * management object; it must never be a hosted business-proxy instance.
 *
 * @since 3.1.1
 */
public final class SubProcessPolicyAdmin implements SubProcessAdministrable {

    private final AdminPrincipalAuthenticator authenticator;
    private final PolicyAdmin backing;
    private final MethodConstraints adminConstraints;

    /**
     * @param adminPrincipal the orchestrating admin principal callers must
     *        prove; must not be null
     * @param backing the trusted, separately-exported management object;
     *        must not be null and must not be a hosted business proxy
     * @param callerIdentity source of the current caller's authenticated
     *        identity; must not be null
     */
    public SubProcessPolicyAdmin(Principal adminPrincipal,
                                 PolicyAdmin backing,
                                 AdminPrincipalAuthenticator.CallerIdentity callerIdentity) {
        if (backing == null) throw new NullPointerException("backing");
        this.authenticator =
                new AdminPrincipalAuthenticator(adminPrincipal, callerIdentity);
        this.backing = backing;
        this.adminConstraints = buildAdminConstraints(adminPrincipal);
    }

    /**
     * Builds the stricter admin {@link MethodConstraints}: integrity required,
     * client authenticated as the admin principal, server authenticated.
     */
    static MethodConstraints buildAdminConstraints(Principal adminPrincipal) {
        InvocationConstraints ic = new InvocationConstraints(
            new InvocationConstraint[] {
                Integrity.YES,
                ClientAuthentication.YES,
                new ClientMinPrincipal(adminPrincipal),
                ServerAuthentication.YES
            },
            null);
        return new BasicMethodConstraints(ic);
    }

    /** The stricter constraints the admin endpoint enforces. */
    public MethodConstraints adminConstraints() {
        return adminConstraints;
    }

    @Override
    public PolicyAdmin getSubProcessPolicyAdmin() throws RemoteException {
        // Layer 1: fail closed before handing back anything usable.
        authenticator.requireAdmin("getSubProcessPolicyAdmin");
        // Layers 2 & 3: return a re-gating, constraint-bearing proxy.
        return (PolicyAdmin) Proxy.newProxyInstance(
                SubProcessPolicyAdmin.class.getClassLoader(),
                new Class<?>[] { PolicyAdmin.class, RemoteMethodControl.class },
                new GuardedPolicyAdminHandler(
                        authenticator, backing, adminConstraints));
    }

    /**
     * Re-gates every {@link PolicyAdmin} operation on the admin identity and
     * answers {@link RemoteMethodControl} with the fixed admin constraints.
     */
    private static final class GuardedPolicyAdminHandler
            implements InvocationHandler {

        private final AdminPrincipalAuthenticator authenticator;
        private final PolicyAdmin backing;
        private final MethodConstraints constraints;

        GuardedPolicyAdminHandler(AdminPrincipalAuthenticator authenticator,
                                  PolicyAdmin backing,
                                  MethodConstraints constraints) {
            this.authenticator = authenticator;
            this.backing = backing;
            this.constraints = constraints;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String name = method.getName();
            Class<?> decl = method.getDeclaringClass();

            // RemoteMethodControl surface: expose the fixed constraints; never
            // let a client weaken them below the admin floor.
            if (decl == RemoteMethodControl.class) {
                if ("getConstraints".equals(name)) {
                    return constraints;
                }
                if ("setConstraints".equals(name)) {
                    // Constraints are fixed by the subprocess; setConstraints
                    // cannot relax them, so return the same guarded proxy.
                    return proxy;
                }
            }
            // Object methods handled locally, no auth needed.
            if (decl == Object.class) {
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(name)) {
                    return proxy == (args == null ? null : args[0]);
                }
                if ("toString".equals(name)) {
                    return "SubProcessPolicyAdmin$guarded";
                }
            }

            // Layer 2: re-gate every PolicyAdmin operation.
            authenticator.requireAdmin(name);
            try {
                return method.invoke(backing, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
