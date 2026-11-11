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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.export.CodebaseAccessor;
import net.jini.export.Exporter;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.SslEngineSvidFactory.Svid;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.loader.pref.ServerIdentityPinAccess;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The codebase provider's {@code ServerIdentityPin}: the probe of a bootstrap
 * proxy's own connection, and the pin, over real SSL and TCP JERI with
 * {@link AtomicILFactory} exports (the AtomicInvocationHandler).  Moved from
 * jgdms-jeri's {@code AuthenticateServerTest}, which cannot have the provider on
 * its test classpath.
 */
public class ServerIdentityPinProbeTest {

    private static final ClassLoader LOADER = ServerIdentityPinProbeTest.class.getClassLoader();
    private static final Collection<Object> KEEP = new ArrayList<Object>();
    private static String[] previousTrustStore;
    private static Svid server, client, other;
    private static CodebaseAccessor accessor, otherAccessor, tcpAccessor;

    /** A bootstrap proxy's server side. */
    public static final class Accessor implements CodebaseAccessor {
        public String getClassAnnotation() { return "http://localhost/probe.jar"; }
        public String getCertFactoryType() { return null; }
        public String getCertPathEncoding() { return null; }
        public byte[] getEncodedCerts() { return null; }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        Path dir = Files.createTempDirectory("pin-probe");
        server = SslEngineSvidFactory.generate(dir, "server", "CN=Reggie", "spiffe://example.org/reggie");
        client = SslEngineSvidFactory.generate(dir, "client", "CN=Tester", "spiffe://example.org/tester");
        other = SslEngineSvidFactory.generate(dir, "other", "CN=Other", "spiffe://example.org/other");
        previousTrustStore = SslEngineSvidFactory.installTrustStore(dir, server.leaf, client.leaf, other.leaf);
        resetSslTrustManagerCache();
        accessor = exportAccessor(server);
        otherAccessor = exportAccessor(other);
        Exporter tax = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
            new AtomicILFactory(null, null, LOADER), false, true);
        Accessor ta = new Accessor();
        KEEP.add(ta);
        KEEP.add(tax);
        tcpAccessor = (CodebaseAccessor) tax.export(ta);
    }

    private static CodebaseAccessor exportAccessor(Svid s) throws Exception {
        ServerEndpoint se = SslServerEndpoint.getInstance(s.subject, new X500Principal[]{s.x500}, "localhost", 0);
        Exporter ex = new BasicJeriExporter(se, new AtomicILFactory(
            new BasicMethodConstraints(AuthenticateServerTest.MUTUAL), null, LOADER), false, true);
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

    /** FilterX509TrustManager caches its trust manager per JVM: reset at set-up and tear-down. */
    private static void resetSslTrustManagerCache() throws Exception {
        java.lang.reflect.Field tm = FilterX509TrustManager.class.getDeclaredField("tm");
        tm.setAccessible(true);
        tm.set(null, null);
    }

    private static boolean spiffeAvailable() {
        try {
            Class.forName("au.net.zeus.jgdms.spiffe.SpiffePrincipal", false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
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

    @Test
    public void thePinProbeReturnsTheAuthenticatedIdentity() throws Exception {
        Principal p = asClient(() -> ServerIdentityPinAccess.probe(accessor));
        if (spiffeAvailable()) {
            assertEquals("spiffe://example.org/reggie", p.getName());
        } else {
            assertEquals("without the SPIFFE class the identity is the X.500 name", server.x500, p);
        }
    }

    @Test
    public void thePinProbeRefusesAClientThatIsNotLoggedOnInsteadOfTreatingItAsAnonymous() throws Exception {
        try {
            ServerIdentityPinAccess.probe(accessor);
            fail("an authenticating transport whose handshake cannot be made must refuse");
        } catch (UnsupportedConstraintException expected) {
        }
    }

    @Test
    public void thePinProbeGivesNoIdentityForATransportThatCannotAuthenticate() throws Exception {
        assertNull(asClient(() -> ServerIdentityPinAccess.probe(tcpAccessor)));
    }

    @Test
    public void aPinnedProxyStillWorksAndAnotherServerCannotAnswerForIt() throws Exception {
        Principal p = asClient(() -> ServerIdentityPinAccess.probe(accessor));
        CodebaseAccessor pinned = ServerIdentityPinAccess.pin(accessor, p);
        assertEquals("http://localhost/probe.jar", asClient(() -> pinned.getClassAnnotation()));
        CodebaseAccessor wrong = ServerIdentityPinAccess.pin(otherAccessor, p);
        try {
            asClient(() -> wrong.getClassAnnotation());
            fail("a server other than the pinned one must not answer");
        } catch (IOException expected) {
        }
    }

    @Test
    public void aProxyThatAlreadyRequiresTheServerPrincipalCanBeProbedAndPinned() throws Exception {
        CodebaseAccessor constrained = (CodebaseAccessor) ((RemoteMethodControl) accessor).setConstraints(
            new BasicMethodConstraints(new InvocationConstraints(
                new InvocationConstraint[]{ClientAuthentication.YES, ServerAuthentication.YES,
                    Integrity.YES, Confidentiality.YES, new ServerMinPrincipal(server.x500)}, null)));
        Principal p = asClient(() -> ServerIdentityPinAccess.probe(constrained));
        CodebaseAccessor pinned = ServerIdentityPinAccess.pin(constrained, p);
        // With the SPIFFE class present the pin is the SPIFFE ID and replaces the X.500
        // requirement: one connection cannot satisfy two ServerMinPrincipal requirements.
        assertEquals("http://localhost/probe.jar", asClient(() -> pinned.getClassAnnotation()));
    }
}
