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

import java.util.ArrayList;
import java.util.List;

/**
 * Reject-on-load guard against privileged-interface impersonation (task&nbsp;T2,
 * requirement&nbsp;#3, third layer).
 *
 * <p>Before a subprocess exports or dispatches a hosted smart proxy, it must
 * refuse to host any proxy whose <em>resolved</em> interface closure includes
 * the subprocess's own management interfaces ({@link SubProcessAdministrable}
 * or {@link PolicyAdmin}).  No legitimate business proxy declares the
 * subprocess's management interface; one that does is a TOCTOU /
 * dispatch-confusion escalation attempt trying to be routed to, or mistaken
 * for, the trusted management object.
 *
 * <p><strong>Identity, never name.</strong> The check is on <em>resolved type
 * identity</em> ({@code ==} / {@link Class#isAssignableFrom}), never on a
 * wire-name / simple-name string match.  In the subprocess these management
 * interfaces resolve from the trusted isolation runtime's own class loader, so
 * their {@code Class} identity is fixed and unforgeable; a decoy interface
 * merely <em>named</em> {@code SubProcessAdministrable} but loaded from
 * downloaded codebase is a different {@code Class} identity, cannot be cast to
 * or dispatched as the real management interface, and is therefore harmless
 * &mdash; matching it by name would be a bug (both a false positive on benign
 * proxies and, worse, an invitation to write name-based logic that a real
 * attacker could evade). This guard is intentionally identity-based on both
 * counts.
 *
 * <p>This is a genuinely separate check from wire-content selection: that asks
 * "which bytes/interfaces do we choose to load"; this asks "does the object we
 * actually resolved carry a management-plane type identity". Both are required.
 *
 * @since 3.1.1
 */
public final class HostedProxyGuard {

    /** The management-plane types no hosted business proxy may carry. */
    private static final Class<?>[] FORBIDDEN = new Class<?>[] {
        SubProcessAdministrable.class,
        PolicyAdmin.class
    };

    private HostedProxyGuard() { }

    /**
     * Verifies that a proxy about to be hosted does not carry any
     * management-plane type identity.  Call at reconstruction time, before the
     * proxy is exported or any method is dispatched.
     *
     * @param resolvedInterfaces the proxy's <em>resolved</em> interface
     *        {@code Class} objects (as loaded in the subprocess); may be null
     * @throws SecurityException if any resolved interface is, extends, or is a
     *         supertype-assignable of a management-plane interface &mdash;
     *         i.e. the closure shares type identity with the subprocess's own
     *         management surface. Fail closed: refuse to host.
     */
    public static void checkHostable(Class<?>[] resolvedInterfaces) {
        if (resolvedInterfaces == null) return;
        for (int i = 0; i < resolvedInterfaces.length; i++) {
            Class<?> iface = resolvedInterfaces[i];
            if (iface == null) continue;
            for (int j = 0; j < FORBIDDEN.length; j++) {
                Class<?> forbidden = FORBIDDEN[j];
                // Identity or subtype relationship, both directions of the
                // "shares management type identity" question:
                //  - iface IS / EXTENDS a management interface, or
                //  - a management interface is assignable from iface.
                // All resolved-Class comparisons; never a String name compare.
                if (iface == forbidden || forbidden.isAssignableFrom(iface)) {
                    throw new SecurityException(
                        "Refusing to host smart proxy: its resolved interface"
                        + " closure carries the subprocess management-plane type "
                        + forbidden.getName() + " (via " + iface.getName() + ")."
                        + " No legitimate business proxy declares the subprocess'"
                        + " own management interface; this is a privileged-"
                        + "interface impersonation / dispatch-confusion attempt"
                        + " (fail-closed).");
                }
            }
        }
    }

    /**
     * Convenience overload that resolves the full inherited interface closure
     * of {@code type} (its own interfaces, their superinterfaces, and, if
     * {@code type} is itself an interface, {@code type}) before checking.  Use
     * when only the leaf type is known and the transitive closure must be
     * examined.
     *
     * @param type the resolved leaf type of the hosted object; may be null
     * @throws SecurityException per {@link #checkHostable(Class[])}
     */
    public static void checkHostableClosure(Class<?> type) {
        if (type == null) return;
        List<Class<?>> closure = new ArrayList<Class<?>>();
        collect(type, closure);
        checkHostable(closure.toArray(new Class<?>[0]));
    }

    private static void collect(Class<?> t, List<Class<?>> out) {
        if (t == null || out.contains(t)) return;
        if (t.isInterface()) out.add(t);
        Class<?>[] ifaces = t.getInterfaces();
        for (int i = 0; i < ifaces.length; i++) {
            collect(ifaces[i], out);
        }
        collect(t.getSuperclass(), out);
    }
}
