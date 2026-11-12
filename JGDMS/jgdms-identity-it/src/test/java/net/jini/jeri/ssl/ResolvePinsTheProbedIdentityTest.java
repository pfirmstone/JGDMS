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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.export.CodebaseAccessor;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.ServerSubject;
import net.jini.loader.pref.PreferredProxyCodebaseProvider;
import net.jini.loader.pref.ServerIdentityPinAccess;
import net.jini.loader.pref.ServerIdentityPinAccess.Route;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * {@link PreferredProxyCodebaseProvider#resolve} with smart-proxy isolation on,
 * over real SSL JERI, with a router that records what it is handed.
 * <p>
 * Invariants defended (handover section 3, Option B; section 18):
 * <ol>
 * <li>The server identity is the one the bootstrap proxy's OWN connection
 *     proves, never the {@code ServerSubject} of the stream that delivered it.</li>
 * <li>The proxy handed on is pinned to that identity, and the pin is enforced:
 *     another server cannot answer for it.</li>
 * <li>The context the router reads carries the pinned identity, not the stream's.</li>
 * <li>Fail closed: a probe that fails, an ambiguous identity, or a transport that
 *     cannot authenticate is refused, and nothing is routed.</li>
 * </ol>
 * The client's identity is the ambient SPIFFE worker, as deployed, with no
 * Subject in scope ({@link AmbientIdentityFixture}); the servers run in their own
 * JVM ({@link IdentityServers}).  Without the SPIFFE principal class the identity
 * is the X.500 name, which isolation refuses; the tests that need a SPIFFE
 * identity are then skipped.  The bootstrap proxies are BasicILFactory and
 * AtomicDerILFactory exports.
 */
public class ResolvePinsTheProbedIdentityTest {

    private static final String SPIFFE_CLASS = "au.net.zeus.jgdms.spiffe.SpiffePrincipal";
    private static final ClassLoader LOADER = ResolvePinsTheProbedIdentityTest.class.getClassLoader();

    private static CodebaseAccessor reggie, reggieDer, sameDn, twoUris, tcp;

    private final List<Route> routes = Collections.synchronizedList(new ArrayList<Route>());
    private AutoCloseable router;
    private String previousIsolation;

    @BeforeClass
    public static void setUp() throws Exception {
        AmbientIdentityFixture.start();
        reggie = (CodebaseAccessor) AmbientIdentityFixture.proxy("reggie");
        reggieDer = (CodebaseAccessor) AmbientIdentityFixture.proxy("reggieDer");
        sameDn = (CodebaseAccessor) AmbientIdentityFixture.proxy("sameDn");
        twoUris = (CodebaseAccessor) AmbientIdentityFixture.proxy("twoUris");
        tcp = (CodebaseAccessor) AmbientIdentityFixture.proxy("tcp");
    }

    @Before
    public void isolate() {
        assertNull("the workload identity is ambient: no Subject is in scope", Subject.current());
        previousIsolation = System.getProperty(ServerIdentityPinAccess.ISOLATION_PROPERTY);
        System.setProperty(ServerIdentityPinAccess.ISOLATION_PROPERTY, "true");
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

    private static boolean spiffeAvailable() {
        try {
            Class.forName(SPIFFE_CLASS, false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static Principal spiffe(String uri) throws Exception {
        return (Principal) Class.forName(SPIFFE_CLASS, true, ClassLoader.getSystemClassLoader())
            .getConstructor(String.class).newInstance(uri);
    }

    /**
     * The context of a stream delivered by ANOTHER server: its ServerSubject is
     * the delivering server's, and the stream's method constraints.
     */
    private static List<Object> deliveringStreamContext() throws Exception {
        return deliveringStreamContext(true);
    }

    private static List<Object> deliveringStreamContext(boolean mutualAuth) throws Exception {
        return deliveringStreamContext(mutualAuth ? AuthenticateServerTest.MUTUAL : null);
    }

    /** As above, with a constraint the client cannot meet: a client principal the ambient worker does not hold. */
    private static List<Object> demandingStreamContext() throws Exception {
        return deliveringStreamContext(InvocationConstraints.combine(AuthenticateServerTest.MUTUAL,
            new InvocationConstraints(new ClientMinPrincipal(new X500Principal("CN=Nobody")), null)));
    }

    private static List<Object> deliveringStreamContext(InvocationConstraints constraints) throws Exception {
        final Set<Principal> delivering = new LinkedHashSet<Principal>();
        delivering.add(new X500Principal("CN=Delivering"));
        if (spiffeAvailable()) delivering.add(spiffe("spiffe://example.org/delivering"));
        final Subject subject = new Subject(true, delivering, Collections.emptySet(), Collections.emptySet());
        List<Object> context = new ArrayList<Object>();
        if (constraints != null) context.add(new BasicMethodConstraints(constraints));
        context.add(new ServerSubject() {
            public Subject getServerSubject() { return subject; }
        });
        return context;
    }

    /** A plain call: the client's identity is ambient, no Subject is bound. */
    private static <T> T asClient(Callable<T> c) throws Exception {
        return c.call();
    }

    private static Object resolveAsClient(final CodebaseAccessor proxy) throws Exception {
        return resolveAsClient(proxy, deliveringStreamContext());
    }

    private static Object resolveAsClient(final CodebaseAccessor proxy, final List<Object> context)
            throws Exception {
        return asClient(() -> new PreferredProxyCodebaseProvider().resolve(proxy, null, LOADER, LOADER, context));
    }

    private static void assertPinnedTo(CodebaseAccessor proxy, Principal pin) {
        MethodConstraints mc = ((RemoteMethodControl) proxy).getConstraints();
        for (Method m : CodebaseAccessor.class.getMethods()) {
            Set<?> required = mc.getConstraints(m).requirements();
            assertTrue(m.getName() + " requires server authentication", required.contains(ServerAuthentication.YES));
            assertTrue(m.getName() + " requires the pinned server", required.contains(new ServerMinPrincipal(pin)));
        }
    }

    private Route onlyRoute() {
        assertEquals("routed exactly once", 1, routes.size());
        return routes.get(0);
    }

    // ---- 1 and 3: the identity is the proxy's own, also in the router's context ----

    @Test
    public void theRouterIsGivenTheIdentityTheProxysOwnServerProved() throws Exception {
        Assume.assumeTrue("needs " + SPIFFE_CLASS + " (jgdms-lib-dl) on the classpath", spiffeAvailable());
        assertEquals("routed", resolveAsClient(reggie));
        Route route = onlyRoute();
        assertEquals(1, route.serverPrincipals.length);
        assertEquals(spiffe("spiffe://example.org/reggie"), route.serverPrincipals[0]);
        int subjects = 0;
        for (Object o : route.context) {
            if (o instanceof ServerSubject) {
                subjects++;
                assertEquals("the router reads the pinned identity, not the delivering stream's",
                    Collections.singleton(route.serverPrincipals[0]),
                    ((ServerSubject) o).getServerSubject().getPrincipals());
            }
        }
        assertEquals("exactly one ServerSubject in the routed context", 1, subjects);
    }

    @Test
    public void withoutSpiffeTheProbedDistinguishedNameIsRefusedByIsolationNotTheStreams() throws Exception {
        Assume.assumeFalse("covered by the SPIFFE tests when the SPIFFE class is present", spiffeAvailable());
        try {
            resolveAsClient(reggie);
            fail("isolation needs a SPIFFE identity");
        } catch (IOException expected) {
            String m = expected.getMessage();
            assertTrue(m, m.contains("needs a SPIFFE server identity"));
            assertTrue("the identity is the one the proxy's server proved: " + m, m.contains("CN=Reggie"));
            assertFalse("never the delivering stream's: " + m, m.contains("Delivering"));
        }
        assertTrue(routes.isEmpty());
    }

    // ---- 2: the proxy handed on is pinned, and the pin is enforced ------------

    @Test
    public void theRoutedProxyIsPinnedAndAnotherServerCannotAnswerForIt() throws Exception {
        Assume.assumeTrue("needs " + SPIFFE_CLASS + " (jgdms-lib-dl) on the classpath", spiffeAvailable());
        resolveAsClient(reggie);
        Route route = onlyRoute();
        Principal pin = route.serverPrincipals[0];
        assertPinnedTo(route.bootstrapProxy, pin);
        MethodConstraints mc = ((RemoteMethodControl) route.bootstrapProxy).getConstraints();
        assertEquals("the pinned proxy still answers", IdentityServers.RESOLVE_CODEBASE,
            asClient(() -> route.bootstrapProxy.getClassAnnotation()));
        CodebaseAccessor unpinnedOther = (CodebaseAccessor) ((RemoteMethodControl) sameDn).setConstraints(
            new BasicMethodConstraints(AuthenticateServerTest.MUTUAL));
        assertEquals("without the pin the other server answers (control)", IdentityServers.RESOLVE_CODEBASE,
            asClient(() -> unpinnedOther.getClassAnnotation()));
        CodebaseAccessor otherServer = (CodebaseAccessor) ((RemoteMethodControl) sameDn).setConstraints(mc);
        try {
            asClient(() -> otherServer.getClassAnnotation());
            fail("a server with the same DN but another SPIFFE ID must not satisfy the pin");
        } catch (IOException expected) {
        }
    }

    @Test
    public void anAssertedIdentityIsPinnedAndAWrongServerIsRefusedBeforeRouting() throws Exception {
        Assume.assumeTrue("needs " + SPIFFE_CLASS + " (jgdms-lib-dl) on the classpath", spiffeAvailable());
        final Principal reggieId = asClient(() -> ServerIdentityPinAccess.probe(reggie));
        final List<Object> context = deliveringStreamContext();
        try {
            asClient(() -> new PreferredProxyCodebaseProvider().resolvePinned(
                sameDn, null, LOADER, LOADER, context, reggieId));
            fail("the proxy is pinned to the asserted identity, which its server does not hold");
        } catch (IOException expected) {
        }
        assertTrue("nothing routed", routes.isEmpty());
        assertEquals("routed", asClient(() -> new PreferredProxyCodebaseProvider().resolvePinned(
            reggie, null, LOADER, LOADER, deliveringStreamContext(), reggieId)));
        Route route = onlyRoute();
        assertEquals(reggieId, route.serverPrincipals[0]);
        assertPinnedTo(route.bootstrapProxy, reggieId);
    }

    @Test
    public void anAssertedIdentityMustBeARecognisedPrincipal() throws Exception {
        final Principal custom = new Principal() {
            public String getName() { return "spiffe://example.org/reggie"; }
        };
        final List<Object> context = deliveringStreamContext();
        try {
            asClient(() -> new PreferredProxyCodebaseProvider().resolvePinned(
                reggie, null, LOADER, LOADER, context, custom));
            fail("a principal of an unrecognised class names nothing the pin can enforce");
        } catch (IOException expected) {
            // Refused by ServerIdentityPin.select, whose message speaks of an authenticated server.
            assertTrue(expected.getMessage(),
                expected.getMessage().contains("no SPIFFE, Kerberos or X.500 principal")
                || expected.getMessage().contains("not a recognised principal"));
        }
        assertTrue(routes.isEmpty());
    }

    // ---- the AtomicDer handler, and the DER path's empty stream context -----

    @Test
    public void anAtomicDerBootstrapProxyIsProbedAndPinnedLikeTheOthers() throws Exception {
        Assume.assumeTrue("needs " + SPIFFE_CLASS + " (jgdms-lib-dl) on the classpath", spiffeAvailable());
        resolveAsClient(reggieDer);
        Route route = onlyRoute();
        assertEquals(spiffe("spiffe://example.org/reggie"), route.serverPrincipals[0]);
        assertPinnedTo(route.bootstrapProxy, route.serverPrincipals[0]);
    }

    /**
     * The DER stream's context carries no method constraints and no
     * ServerSubject: the identity must still come from the proxy's own server.
     */
    @Test
    public void anEmptyStreamContextStillTakesTheIdentityFromTheProxysOwnServer() throws Exception {
        final List<Object> empty = new ArrayList<Object>();
        if (spiffeAvailable()) {
            resolveAsClient(reggieDer, empty);
            Route route = onlyRoute();
            assertEquals(spiffe("spiffe://example.org/reggie"), route.serverPrincipals[0]);
            assertPinnedTo(route.bootstrapProxy, route.serverPrincipals[0]);
        } else {
            try {
                resolveAsClient(reggieDer, empty);
                fail("isolation needs a SPIFFE identity");
            } catch (IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("CN=Reggie"));
            }
            assertTrue(routes.isEmpty());
        }
    }

    // ---- 4: fail closed ------------------------------------------------------

    @Test
    public void aConstraintTheClientCannotMeetIsRefusedAndNothingIsRouted() throws Exception {
        try {
            new PreferredProxyCodebaseProvider().resolve(reggie, null, LOADER, LOADER, demandingStreamContext());
            fail("the handshake cannot be made; the stream's subject must not stand in for it");
        } catch (UnsupportedConstraintException expected) {
        }
        assertTrue(routes.isEmpty());
    }

    @Test
    public void aProbeThatCannotBeMadeRefusesAlsoWhenNotIsolating() throws Exception {
        System.setProperty(ServerIdentityPinAccess.ISOLATION_PROPERTY, "false");
        try {
            new PreferredProxyCodebaseProvider().resolve(reggie, null, LOADER, LOADER, demandingStreamContext());
            fail("a probe that cannot be made is an error, never \"no identity\" and the unverified path");
        } catch (UnsupportedConstraintException expected) {
        }
    }

    @Test
    public void aTransportThatCannotAuthenticateGivesNoIdentityEvenWithAStreamSubject() throws Exception {
        // A TCP deployment's stream carries no authentication constraints (with them,
        // the first call already fails: ClientAuthentication cannot be met on TCP).
        final List<Object> context = deliveringStreamContext(false);
        try {
            asClient(() -> new PreferredProxyCodebaseProvider().resolve(tcp, null, LOADER, LOADER, context));
            fail("no identity: isolation must refuse");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(),
                expected.getMessage().contains("no authenticated server principal"));
        }
        assertTrue(routes.isEmpty());
    }

    @Test
    public void aCertificateWithTwoSpiffeIdsIsAnAmbiguousIdentityAndIsRefused() throws Exception {
        Assume.assumeTrue("needs " + SPIFFE_CLASS + " (jgdms-lib-dl) on the classpath", spiffeAvailable());
        try {
            resolveAsClient(twoUris);
            fail("a pin on either SPIFFE ID would admit the other");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("more than one SPIFFE"));
        }
        assertTrue(routes.isEmpty());
    }
}
