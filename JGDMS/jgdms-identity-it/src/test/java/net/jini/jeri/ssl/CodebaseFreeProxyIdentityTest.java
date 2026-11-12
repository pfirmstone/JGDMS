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

package net.jini.jeri.ssl;

import java.io.IOException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.loader.pref.PreferredProxyCodebaseProvider;
import net.jini.loader.pref.ServerIdentityPinAccess;
import net.jini.loader.pref.ServerIdentityPinAccess.Route;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A bootstrap proxy without a codebase annotation, over real SSL JERI.
 * <p>
 * Invariants defended: no code of the service runs locally, so permissions
 * have nothing to restrict and the service is represented by its identity.
 * <ol>
 * <li>The service proxy, when it is a platform JERI proxy for the bootstrap
 *     proxy's own server, is pinned to the identity that server proved, and the
 *     pin is enforced: another server cannot answer for it.</li>
 * <li>A service proxy for another server cannot be pinned to that identity:
 *     refused while isolating, returned unpinned otherwise.</li>
 * <li>Nothing is routed to a sub-process: there is no downloaded code.</li>
 * </ol>
 * The client's identity is the ambient SPIFFE worker, as deployed, with no
 * Subject in scope ({@link AmbientIdentityFixture}); the servers run in their own
 * JVM ({@link IdentityServers}).
 */
public class CodebaseFreeProxyIdentityTest {

    private static final ClassLoader LOADER = CodebaseFreeProxyIdentityTest.class.getClassLoader();
    private static CodebaseAccessor reggie, sameDn, tcp;

    private final List<Route> routes = Collections.synchronizedList(new ArrayList<Route>());
    private AutoCloseable router;
    private String previousIsolation;

    @BeforeClass
    public static void setUp() throws Exception {
        AmbientIdentityFixture.start();
        reggie = (CodebaseAccessor) AmbientIdentityFixture.proxy("freeReggie");
        sameDn = (CodebaseAccessor) AmbientIdentityFixture.proxy("freeImpostor");
        tcp = (CodebaseAccessor) AmbientIdentityFixture.proxy("freeTcp");
    }

    @Before
    public void captureRoutes() {
        assertNull("the workload identity is ambient: no Subject is in scope", Subject.current());
        previousIsolation = System.getProperty(ServerIdentityPinAccess.ISOLATION_PROPERTY);
        router = ServerIdentityPinAccess.captureRoutes(routes, "routed");
    }

    @After
    public void restore() throws Exception {
        try {
            if (router != null) router.close();
        } finally {
            if (previousIsolation == null) {
                System.clearProperty(ServerIdentityPinAccess.ISOLATION_PROPERTY);
            } else {
                System.setProperty(ServerIdentityPinAccess.ISOLATION_PROPERTY, previousIsolation);
            }
        }
    }

    private static void isolating(boolean on) {
        System.setProperty(ServerIdentityPinAccess.ISOLATION_PROPERTY, Boolean.toString(on));
    }

    /** A plain call: the client's identity is ambient, no Subject is bound. */
    private static <T> T asClient(Callable<T> c) throws Exception {
        return c.call();
    }

    /** Resolves {@code service} delivered with the bootstrap proxy {@code bootstrap}, as the client. */
    private static CodebaseAccessor resolve(final CodebaseAccessor bootstrap, CodebaseAccessor service,
            boolean mutual) throws Exception {
        final MarshalledInstance mi = new MarshalledInstance(service);
        final List<Object> context = new ArrayList<Object>();
        if (mutual) context.add(new BasicMethodConstraints(AuthenticateServerTest.MUTUAL));
        return (CodebaseAccessor) asClient(() -> new PreferredProxyCodebaseProvider().resolve(
            bootstrap, mi, LOADER, LOADER, context));
    }

    private static Principal reggieId() throws Exception {
        return asClient(() -> ServerIdentityPinAccess.probe(reggie));
    }

    private static boolean pinnedTo(Object proxy, Principal pin) throws Exception {
        MethodConstraints mc = ((RemoteMethodControl) proxy).getConstraints();
        for (Method m : CodebaseAccessor.class.getMethods()) {
            Collection<?> required = mc.getConstraints(m).requirements();
            if (!required.contains(ServerAuthentication.YES) || !required.contains(new ServerMinPrincipal(pin))) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void theServiceProxyOfItsOwnServerIsPinnedAndNotRouted() throws Exception {
        isolating(true);
        CodebaseAccessor sp = resolve(reggie, reggie, true);
        assertTrue("nothing is downloaded, so nothing is routed", routes.isEmpty());
        assertEquals("the identity is the SPIFFE ID the server proved",
            "spiffe://example.org/reggie", reggieId().getName());
        assertTrue("every method requires the server the bootstrap proxy's server proved to be",
            pinnedTo(sp, reggieId()));
        assertEquals("reggie", asClient(() -> sp.getCertFactoryType()));
    }

    @Test
    public void thePinOnTheServiceProxyIsEnforced() throws Exception {
        isolating(false);
        CodebaseAccessor sp = resolve(reggie, reggie, true);
        MethodConstraints pinned = ((RemoteMethodControl) sp).getConstraints();
        CodebaseAccessor control = (CodebaseAccessor) ((RemoteMethodControl) sameDn).setConstraints(
            new BasicMethodConstraints(AuthenticateServerTest.MUTUAL));
        assertEquals("without the pin the other server answers (control)", "impostor",
            asClient(() -> control.getCertFactoryType()));
        CodebaseAccessor other = (CodebaseAccessor) ((RemoteMethodControl) sameDn).setConstraints(pinned);
        try {
            asClient(() -> other.getCertFactoryType());
            fail("a server with the same DN but another SPIFFE ID must not satisfy the pin");
        } catch (IOException expected) {
        }
    }

    @Test
    public void aServiceProxyForAnotherServerIsRefusedWhileIsolating() throws Exception {
        isolating(true);
        try {
            resolve(reggie, sameDn, true);
            fail("it cannot be pinned to the bootstrap proxy's server");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("cannot be pinned"));
        }
        assertTrue(routes.isEmpty());
    }

    @Test
    public void aServiceProxyForAnotherServerIsReturnedUnpinnedWhenNotIsolating() throws Exception {
        isolating(false);
        CodebaseAccessor sp = resolve(reggie, sameDn, true);
        assertFalse("never pinned to an identity its server does not hold", pinnedTo(sp, reggieId()));
        assertEquals("impostor", asClient(() -> sp.getCertFactoryType()));
    }

    @Test
    public void aTransportThatCannotAuthenticateGivesAnUnpinnedProxyThatIsNotRouted() throws Exception {
        isolating(true);
        CodebaseAccessor sp = resolve(tcp, tcp, false);
        assertTrue(routes.isEmpty());
        assertEquals("tcp", asClient(() -> sp.getCertFactoryType()));
    }
}
