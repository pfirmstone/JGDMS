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
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
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
 * The class runs in its own JVM (see the module's pom) under
 * {@code -Djava.security.manager=default}: the CombinerSecurityManager checks
 * the permission against the real access control context.  The caller runs in
 * a domain of its own {@code http:} code source holding exactly what the test
 * intends; the test policy grants that code source nothing, and grants local
 * code (the provider, the test, JUnit) everything.
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
     * {@code callerPermissions}, under the SecurityManager.  Returns what it threw.
     */
    private static Throwable resolvePinnedAs(PermissionCollection callerPermissions, final Recorder recorder)
            throws Exception {
        final AccessControlContext caller = new AccessControlContext(new ProtectionDomain[]{
            new ProtectionDomain(CALLER, callerPermissions)});
        Assume.assumeNotNull(System.getSecurityManager());
        // Made, and their classes initialized, before the confined call: only
        // resolvePinned runs as the caller.
        final PreferredProxyCodebaseProvider provider = new PreferredProxyCodebaseProvider();
        final CodebaseAccessor bootstrap = recorder.proxy();
        final List<Object> context = streamContext();
        final X500Principal asserted = new X500Principal("CN=asserted");
        final Throwable[] thrown = new Throwable[1];
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                public Object run() throws Exception {
                    return provider.resolvePinned(bootstrap, null, LOADER, LOADER, context, asserted);
                }
            }, caller);
        } catch (PrivilegedActionException e) {
            thrown[0] = e.getException();
        } catch (Throwable t) {
            thrown[0] = t;
        }
        assertNotNull("resolvePinned cannot succeed with a proxy of this handler", thrown[0]);
        return thrown[0];
    }

    /** A stream context with method constraints, which resolve applies to the proxy first. */
    private static List<Object> streamContext() {
        List<Object> context = new ArrayList<Object>();
        context.add(new BasicMethodConstraints(InvocationConstraints.EMPTY));
        return context;
    }

    /** The confined caller's code source. */
    private static final CodeSource CALLER;
    static {
        try {
            CALLER = new CodeSource(new java.net.URL("http://confined-caller.invalid/caller.jar"),
                    (java.security.cert.Certificate[]) null);
        } catch (java.net.MalformedURLException e) {
            throw new ExceptionInInitializerError(e);
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
}
