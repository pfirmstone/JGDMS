/*
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
package net.jini.loader.pref;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Invariant: an identity can be asserted to
 * {@link PreferredProxyCodebaseProvider#resolvePinned} only when every caller on
 * the stack holds {@link PinnedIdentityPermission}, and the check is made
 * before the bootstrap proxy is touched.  An asserted identity decides which
 * principal {@code BootstrapPermission} and the digest grants are issued for,
 * so a caller without the grant must not get that far, and the provider must
 * not vouch for its caller by checking in a privileged block of its own.
 * <p>
 * The policy grants everything to every code source; the caller is confined
 * to a domain whose permissions are fixed (it never consults the policy), and
 * the SecurityManager checks the permission against the real access control
 * context.  A SecurityManager must be installable: this class runs in its own
 * JVM (see the module's pom) with {@code -Djava.security.manager=allow}; it is
 * skipped where one cannot be installed.
 */
public class ResolvePinnedPermissionTest {

    private static final ClassLoader LOADER = ResolvePinnedPermissionTest.class.getClassLoader();

    @Test
    public void aCallerWithoutThePermissionIsRefusedBeforeTheProxyIsTouched() throws Exception {
        Recorder recorder = new Recorder();
        Throwable outcome = resolvePinnedAs(new Permissions(), recorder);
        assertTrue("an identity asserted by a caller without PinnedIdentityPermission must be refused, got "
                + outcome, outcome instanceof SecurityException);
        assertEquals("the bootstrap proxy was never called", Collections.emptyList(), recorder.calls);
    }

    /** The control: the refusal above is the gate's, not an incidental denial further on. */
    @Test
    public void aCallerWithThePermissionGetsPastTheGate() throws Exception {
        Permissions granted = new Permissions();
        granted.add(new PinnedIdentityPermission());
        Recorder recorder = new Recorder();
        Throwable outcome = resolvePinnedAs(granted, recorder);
        assertTrue("past the gate, a proxy whose handler is not a platform JERI handler is refused, got "
                + outcome, outcome instanceof IOException
                && outcome.getMessage().contains("not a platform JERI handler"));
        assertEquals("past the gate the stream's constraints are applied",
                Collections.singletonList("setConstraints"), recorder.calls);
    }

    /**
     * Calls resolvePinned from a caller confined to a domain holding exactly
     * {@code callerPermissions}, under a SecurityManager and a policy granting
     * everything to every code source.  Returns what it threw.
     */
    private static Throwable resolvePinnedAs(PermissionCollection callerPermissions, final Recorder recorder)
            throws Exception {
        final AccessControlContext caller = new AccessControlContext(new ProtectionDomain[]{
            new ProtectionDomain(new CodeSource(null, (java.security.cert.Certificate[]) null),
                    callerPermissions)});
        final Throwable[] thrown = new Throwable[1];
        withSecurityManager(new Body() {
            public void run() {
                try {
                    AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                        public Object run() throws Exception {
                            return new PreferredProxyCodebaseProvider().resolvePinned(recorder.proxy(), null,
                                    LOADER, LOADER, streamContext(), new X500Principal("CN=asserted"));
                        }
                    }, caller);
                } catch (PrivilegedActionException e) {
                    thrown[0] = e.getException();
                } catch (Throwable t) {
                    thrown[0] = t;
                }
            }
        });
        assertNotNull("resolvePinned cannot succeed with a proxy of this handler", thrown[0]);
        return thrown[0];
    }

    /** A stream context with method constraints, which resolve applies to the proxy first. */
    private static List<Object> streamContext() {
        List<Object> context = new ArrayList<Object>();
        context.add(new BasicMethodConstraints(InvocationConstraints.EMPTY));
        return context;
    }

    /** Grants everything to every domain that consults the policy. */
    private static final class AllowAllPolicy extends Policy {
        @Override
        public PermissionCollection getPermissions(CodeSource codesource) {
            Permissions p = new Permissions();
            p.add(new AllPermission());
            return p;
        }

        @Override
        public PermissionCollection getPermissions(ProtectionDomain domain) {
            return getPermissions((CodeSource) null);
        }

        @Override
        public boolean implies(ProtectionDomain domain, Permission permission) {
            return true;
        }
    }

    /**
     * Checks PinnedIdentityPermission against the access control context;
     * allows everything else, and everything once disarmed.
     */
    private static final class PinSecurityManager extends SecurityManager {
        private volatile boolean armed = true;

        PinSecurityManager() {
            // Loaded now: loading a class inside checkPermission checks a read and recurses.
            new PinnedIdentityPermission().implies(new PinnedIdentityPermission());
            AccessController.getContext();
        }

        @Override
        public void checkPermission(Permission perm) {
            if (armed && perm instanceof PinnedIdentityPermission) {
                AccessController.checkPermission(perm);
            }
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            if (armed && perm instanceof PinnedIdentityPermission) {
                ((AccessControlContext) context).checkPermission(perm);
            }
        }

        void disarm() {
            armed = false;
        }
    }

    /** A bootstrap proxy that records every call made on it. */
    private static final class Recorder implements InvocationHandler {
        final List<String> calls = Collections.synchronizedList(new ArrayList<String>());

        CodebaseAccessor proxy() {
            return (CodebaseAccessor) Proxy.newProxyInstance(LOADER,
                    new Class<?>[]{CodebaseAccessor.class, RemoteMethodControl.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "Recorder";
                default:
                    calls.add(method.getName());
                    return "setConstraints".equals(method.getName()) ? proxy : null;
            }
        }
    }

    private interface Body {
        void run();
    }

    private static void withSecurityManager(Body body) {
        Policy previousPolicy = Policy.getPolicy();
        SecurityManager previous = System.getSecurityManager();
        Assume.assumeTrue("another SecurityManager is installed", previous == null);
        PinSecurityManager sm = new PinSecurityManager();
        Policy.setPolicy(new AllowAllPolicy());
        try {
            try {
                System.setSecurityManager(sm);
            } catch (UnsupportedOperationException noAllowFlag) {
                Assume.assumeNoException("needs -Djava.security.manager=allow", noAllowFlag);
                return;
            }
            try {
                body.run();
            } finally {
                sm.disarm();
                try {
                    System.setSecurityManager(null);
                } catch (RuntimeException cannotRemove) {
                    // A JDK may refuse to remove a SecurityManager; the disarmed one allows
                    // everything, and this class runs in its own JVM.
                }
            }
        } finally {
            Policy.setPolicy(previousPolicy);
        }
    }
}
