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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.export.CodebaseAccessor;
import net.jini.export.Exporter;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.ServerSubject;
import net.jini.jeri.AtomicDerILFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.SslEngineSvidFactory.Svid;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.loader.pref.PreferredProxyCodebaseProvider;
import net.jini.loader.pref.ServerIdentityPinAccess;
import net.jini.loader.pref.ServerIdentityPinAccess.Route;
import org.junit.After;
import org.junit.AfterClass;
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
 * The SPIFFE principal class lives in jgdms-lib-dl, which depends on this
 * module, so it is absent from this module's own test classpath.  Without it
 * the identity is the X.500 name, which isolation refuses; the tests that need
 * a SPIFFE identity are then skipped (they run when jgdms-lib-dl's classes are
 * on the classpath).  Exports use {@link BasicILFactory} and
 * {@link AtomicDerILFactory}: an {@code AtomicILFactory} export registers with
 * the provider as first party, and resolve would never reach the router.
 */
public class ResolvePinsTheProbedIdentityTest {

    private static final String SPIFFE_CLASS = "au.net.zeus.jgdms.spiffe.SpiffePrincipal";
    private static final ClassLoader LOADER = ResolvePinsTheProbedIdentityTest.class.getClassLoader();

    private static final Collection<Object> KEEP = new ArrayList<Object>();
    private static Svid server, impostor, twoUri, client;
    private static CodebaseAccessor reggie, reggieDer, sameDn, twoUris, tcp;
    private static String[] previousTrustStore;

    private final List<Route> routes = Collections.synchronizedList(new ArrayList<Route>());
    private AutoCloseable router;
    private String previousIsolation;

    /** A bootstrap proxy's server side. */
    public static final class Accessor implements CodebaseAccessor {
        public String getClassAnnotation() { return "http://localhost/resolve-pin.jar"; }
        public String getCertFactoryType() { return null; }
        public String getCertPathEncoding() { return null; }
        public byte[] getEncodedCerts() { return null; }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        Path dir = Files.createTempDirectory("resolve-pin");
        server = SslEngineSvidFactory.generate(dir, "server", "CN=Reggie", "spiffe://example.org/reggie");
        impostor = SslEngineSvidFactory.generate(dir, "impostor", "CN=Reggie", "spiffe://example.org/impostor");
        twoUri = SslEngineSvidFactory.generate(dir, "twouri", "CN=Reggie",
            "spiffe://example.org/alpha,uri:spiffe://example.org/beta");
        client = SslEngineSvidFactory.generate(dir, "client", "CN=Tester", "spiffe://example.org/tester");
        previousTrustStore = SslEngineSvidFactory.installTrustStore(dir,
            server.leaf, impostor.leaf, twoUri.leaf, client.leaf);
        resetSslTrustManagerCache();
        reggie = exportSsl(server);
        reggieDer = exportSsl(server,
            new AtomicDerILFactory(new BasicMethodConstraints(AuthenticateServerTest.MUTUAL), null, LOADER));
        sameDn = exportSsl(impostor);
        twoUris = exportSsl(twoUri);
        Exporter tex = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
            new BasicILFactory(null, null, LOADER), false, true);
        Accessor ti = new Accessor();
        KEEP.add(ti);
        KEEP.add(tex);
        tcp = (CodebaseAccessor) tex.export(ti);
    }

    private static CodebaseAccessor exportSsl(Svid s) throws Exception {
        return exportSsl(s,
            new BasicILFactory(new BasicMethodConstraints(AuthenticateServerTest.MUTUAL), null, LOADER));
    }

    private static CodebaseAccessor exportSsl(Svid s, InvocationLayerFactory ilf) throws Exception {
        ServerEndpoint se = SslServerEndpoint.getInstance(s.subject, new X500Principal[]{s.x500}, "localhost", 0);
        Exporter ex = new BasicJeriExporter(se, ilf, false, true);
        Accessor impl = new Accessor();
        KEEP.add(impl);
        KEEP.add(ex);
        return (CodebaseAccessor) Subject.callAs(s.subject, (Callable<Object>) () -> ex.export(impl));
    }

    @AfterClass
    public static void tearDown() throws Exception {
        for (Object o : KEEP) {
            if (o instanceof Exporter) ((Exporter) o).unexport(true);
        }
        if (previousTrustStore != null) SslEngineSvidFactory.restoreTrustStore(previousTrustStore);
        resetSslTrustManagerCache();
    }

    /**
     * FilterX509TrustManager caches its trust manager per JVM, built from the
     * trust store installed first; other SSL test classes in the same JVM install
     * their own.  Reset at set-up and tear-down (as MarshallingFormatDeferralTest).
     */
    private static void resetSslTrustManagerCache() throws Exception {
        java.lang.reflect.Field tm = FilterX509TrustManager.class.getDeclaredField("tm");
        tm.setAccessible(true);
        tm.set(null, null);
    }

    @Before
    public void isolate() {
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
        final Set<Principal> delivering = new LinkedHashSet<Principal>();
        delivering.add(new X500Principal("CN=Delivering"));
        if (spiffeAvailable()) delivering.add(spiffe("spiffe://example.org/delivering"));
        final Subject subject = new Subject(true, delivering, Collections.emptySet(), Collections.emptySet());
        List<Object> context = new ArrayList<Object>();
        if (mutualAuth) context.add(new BasicMethodConstraints(AuthenticateServerTest.MUTUAL));
        context.add(new ServerSubject() {
            public Subject getServerSubject() { return subject; }
        });
        return context;
    }

    private static <T> T asClient(Callable<T> c) throws Exception {
        try {
            return Subject.callAs(client.subject, c);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
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
        assertEquals("the pinned proxy still answers", "http://localhost/resolve-pin.jar",
            asClient(() -> route.bootstrapProxy.getClassAnnotation()));
        CodebaseAccessor unpinnedOther = (CodebaseAccessor) ((RemoteMethodControl) sameDn).setConstraints(
            new BasicMethodConstraints(AuthenticateServerTest.MUTUAL));
        assertEquals("without the pin the other server answers (control)", "http://localhost/resolve-pin.jar",
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
    public void aClientThatIsNotLoggedOnIsRefusedAndNothingIsRouted() throws Exception {
        try {
            new PreferredProxyCodebaseProvider().resolve(reggie, null, LOADER, LOADER, deliveringStreamContext());
            fail("the handshake cannot be made; the stream's subject must not stand in for it");
        } catch (UnsupportedConstraintException expected) {
        }
        assertTrue(routes.isEmpty());
    }

    @Test
    public void aFailedProbeRefusesAlsoWhenNotIsolating() throws Exception {
        System.setProperty(ServerIdentityPinAccess.ISOLATION_PROPERTY, "false");
        try {
            new PreferredProxyCodebaseProvider().resolve(reggie, null, LOADER, LOADER, deliveringStreamContext());
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
