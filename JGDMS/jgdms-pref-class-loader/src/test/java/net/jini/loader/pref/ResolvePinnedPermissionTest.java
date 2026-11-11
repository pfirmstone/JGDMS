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
import java.security.Permission;
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
 * {@link PreferredProxyCodebaseProvider#resolvePinned} only by a caller holding
 * {@link PinnedIdentityPermission}, and the check is made before the bootstrap
 * proxy is touched.  An asserted identity decides which principal
 * {@code BootstrapPermission} and the digest grants are issued for, so a caller
 * without the grant must not get that far.
 * <p>
 * Needs a SecurityManager: on Java 18 to 23 run with
 * {@code -Djava.security.manager=allow}, otherwise the tests are skipped.
 */
public class ResolvePinnedPermissionTest {

    private static final ClassLoader LOADER = ResolvePinnedPermissionTest.class.getClassLoader();

    @Test
    public void aCallerWithoutThePermissionIsRefusedBeforeTheProxyIsTouched() throws Exception {
        final PinSecurityManager sm = new PinSecurityManager(true);
        final Recorder recorder = new Recorder();
        withSecurityManager(sm, new Body() {
            public void run() throws Exception {
                try {
                    new PreferredProxyCodebaseProvider().resolvePinned(recorder.proxy(), null,
                            LOADER, LOADER, streamContext(), new X500Principal("CN=asserted"));
                    fail("an identity asserted without PinnedIdentityPermission must be refused");
                } catch (SecurityException expected) {
                    assertTrue(expected.getMessage(), expected.getMessage().contains("denied by test SM"));
                }
            }
        });
        assertEquals("the gate checked exactly the pinned-identity permission",
                Collections.singletonList(new PinnedIdentityPermission()), sm.pinChecks);
        assertEquals("the bootstrap proxy was never called", Collections.emptyList(), recorder.calls);
    }

    /** The control: the refusal above is the gate's, not an incidental denial further on. */
    @Test
    public void aCallerWithThePermissionGetsPastTheGate() throws Exception {
        final PinSecurityManager sm = new PinSecurityManager(false);
        final Recorder recorder = new Recorder();
        withSecurityManager(sm, new Body() {
            public void run() throws Exception {
                try {
                    new PreferredProxyCodebaseProvider().resolvePinned(recorder.proxy(), null,
                            LOADER, LOADER, streamContext(), new X500Principal("CN=asserted"));
                    fail("a proxy whose handler is not a platform JERI handler cannot be pinned");
                } catch (IOException expected) {
                    assertTrue(expected.getMessage(),
                            expected.getMessage().contains("not a platform JERI handler"));
                }
            }
        });
        assertEquals(Collections.singletonList(new PinnedIdentityPermission()), sm.pinChecks);
        assertEquals("past the gate the stream's constraints are applied",
                Collections.singletonList("setConstraints"), recorder.calls);
    }

    /** A stream context with method constraints, which resolve applies to the proxy first. */
    private static List<Object> streamContext() {
        List<Object> context = new ArrayList<Object>();
        context.add(new BasicMethodConstraints(InvocationConstraints.EMPTY));
        return context;
    }

    /** Denies, or only records, PinnedIdentityPermission; allows everything else. */
    private static final class PinSecurityManager extends SecurityManager {
        private final boolean deny;
        final List<Permission> pinChecks = Collections.synchronizedList(new ArrayList<Permission>());

        PinSecurityManager(boolean deny) {
            this.deny = deny;
            // Loaded now: loading it inside checkPermission would check a read and recurse.
            new PinnedIdentityPermission().implies(new PinnedIdentityPermission());
        }

        @Override
        public void checkPermission(Permission perm) {
            if (perm instanceof PinnedIdentityPermission) {
                pinChecks.add(perm);
                if (deny) {
                    throw new SecurityException("denied by test SM: " + perm);
                }
            }
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            checkPermission(perm);
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
        void run() throws Exception;
    }

    private static void withSecurityManager(SecurityManager sm, Body body) throws Exception {
        SecurityManager previous = System.getSecurityManager();
        try {
            System.setSecurityManager(sm);
        } catch (UnsupportedOperationException noAllowFlag) {
            Assume.assumeNoException("needs -Djava.security.manager=allow", noAllowFlag);
            return;
        }
        try {
            body.run();
        } finally {
            System.setSecurityManager(previous);
        }
    }
}
