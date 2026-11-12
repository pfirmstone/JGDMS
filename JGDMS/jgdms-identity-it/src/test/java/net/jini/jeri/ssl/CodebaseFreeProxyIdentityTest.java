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
import java.util.List;
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
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.SslEngineSvidFactory.Svid;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.loader.pref.PreferredProxyCodebaseProvider;
import net.jini.loader.pref.ServerIdentityPinAccess;
import net.jini.loader.pref.ServerIdentityPinAccess.Route;
import org.junit.After;
import org.junit.AfterClass;
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
 */
public class CodebaseFreeProxyIdentityTest {

    private static final ClassLoader LOADER = CodebaseFreeProxyIdentityTest.class.getClassLoader();
    private static final Collection<Object> KEEP = new ArrayList<Object>();
    private static Svid server, impostor, client;
    private static CodebaseAccessor reggie, sameDn, tcp;
    private static String[] previousTrustStore;

    private final List<Route> routes = Collections.synchronizedList(new ArrayList<Route>());
    private AutoCloseable router;
    private String previousIsolation;

    /** A codebase-free service; getCertFactoryType says which server answered. */
    public static final class Service implements CodebaseAccessor {
        private final String who;
        Service(String who) { this.who = who; }
        public String getClassAnnotation() { return null; }
        public String getCertFactoryType() { return who; }
        public String getCertPathEncoding() { return null; }
        public byte[] getEncodedCerts() { return null; }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        Path dir = Files.createTempDirectory("codebase-free");
        server = SslEngineSvidFactory.generate(dir, "server", "CN=Reggie", "spiffe://example.org/reggie");
        impostor = SslEngineSvidFactory.generate(dir, "impostor", "CN=Reggie", "spiffe://example.org/impostor");
        client = SslEngineSvidFactory.generate(dir, "client", "CN=Tester", "spiffe://example.org/tester");
        previousTrustStore = SslEngineSvidFactory.installTrustStore(dir, server.leaf, impostor.leaf, client.leaf);
        resetSslTrustManagerCache();
        reggie = exportSsl(server, "reggie");
        sameDn = exportSsl(impostor, "impostor");
        Exporter tex = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
            new BasicILFactory(null, null, LOADER), false, true);
        Service ts = new Service("tcp");
        KEEP.add(ts);
        KEEP.add(tex);
        tcp = (CodebaseAccessor) tex.export(ts);
    }

    private static CodebaseAccessor exportSsl(Svid s, String who) throws Exception {
        ServerEndpoint se = SslServerEndpoint.getInstance(s.subject, new X500Principal[]{s.x500}, "localhost", 0);
        Exporter ex = new BasicJeriExporter(se,
            new BasicILFactory(new BasicMethodConstraints(AuthenticateServerTest.MUTUAL), null, LOADER),
            false, true);
        Service impl = new Service(who);
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

    /** FilterX509TrustManager caches its trust manager per JVM: reset at set-up and tear-down. */
    private static void resetSslTrustManagerCache() throws Exception {
        java.lang.reflect.Field tm = FilterX509TrustManager.class.getDeclaredField("tm");
        tm.setAccessible(true);
        tm.set(null, null);
    }

    @Before
    public void captureRoutes() {
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

    private static <T> T asClient(Callable<T> c) throws Exception {
        try {
            return Subject.callAs(client.subject, c);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
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
