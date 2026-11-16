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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.x500.X500Principal;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.export.CodebaseAccessor;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The identity rule of {@link ServerIdentityPin}: which principal a bootstrap
 * proxy is pinned to, and which handlers are asked at all.
 * <p>
 * The SPIFFE principal is recognised by its exact class name
 * {@code au.net.zeus.jgdms.spiffe.SpiffePrincipal}, which lives in a module that
 * depends on the JERI module and is not on this module's test classpath, so a
 * class of that name is compiled and loaded here.  A probe of a real SSL
 * endpoint is covered by {@code AuthenticateServerTest} in the JERI module.
 */
public class ServerIdentityPinTest {

    private static Class<?> spiffeClass;

    @BeforeClass
    public static void defineSpiffePrincipal() throws Exception {
        Path dir = Files.createTempDirectory("spiffe-stand-in");
        Path src = dir.resolve("au/net/zeus/jgdms/spiffe/SpiffePrincipal.java");
        Files.createDirectories(src.getParent());
        Files.write(src, ("package au.net.zeus.jgdms.spiffe;\n"
                + "public final class SpiffePrincipal implements java.security.Principal {\n"
                + "  private final String name;\n"
                + "  public SpiffePrincipal(String name) { this.name = name; }\n"
                + "  public String getName() { return name; }\n"
                + "  public boolean equals(Object o) { return o instanceof SpiffePrincipal"
                + " && ((SpiffePrincipal) o).name.equals(name); }\n"
                + "  public int hashCode() { return name.hashCode(); }\n"
                + "  public String toString() { return name; }\n"
                + "}\n").getBytes("UTF-8"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, "-proc:none", "-d", dir.toString(), src.toString()));
        spiffeClass = new URLClassLoader(new URL[]{dir.toUri().toURL()},
                ServerIdentityPinTest.class.getClassLoader())
                .loadClass("au.net.zeus.jgdms.spiffe.SpiffePrincipal");
    }

    private static Principal spiffe(String uri) throws Exception {
        return (Principal) spiffeClass.getConstructor(String.class).newInstance(uri);
    }

    private static Principal kerberos(String name) {
        return new KerberosPrincipal(name);
    }

    private static Set<Principal> set(Principal... ps) {
        return new LinkedHashSet<Principal>(Arrays.asList(ps));
    }

    // ---- which principal --------------------------------------------------

    @Test
    public void oneSpiffePrincipalIsPinned() throws Exception {
        Principal s = spiffe("spiffe://example/reggie");
        assertSame(s, ServerIdentityPin.select(set(new X500Principal("CN=reggie"), s)));
    }

    @Test
    public void twoSpiffePrincipalsAreAnAmbiguousIdentityAndRefused() throws Exception {
        try {
            ServerIdentityPin.select(set(spiffe("spiffe://example/a"), spiffe("spiffe://example/b")));
            fail("a pin on either would admit the other");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("more than one SPIFFE"));
        }
    }

    @Test
    public void oneKerberosPrincipalIsPinnedWhenThereIsNoSpiffe() {
        Principal k = kerberos("svc/host@EXAMPLE.COM");
        try {
            assertSame(k, ServerIdentityPin.select(set(k)));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void spiffeWinsOverKerberos() throws Exception {
        Principal s = spiffe("spiffe://example/a");
        assertSame(s, ServerIdentityPin.select(set(kerberos("svc/host@EXAMPLE.COM"), s)));
    }

    @Test
    public void twoKerberosPrincipalsAreRefused() {
        try {
            ServerIdentityPin.select(set(kerberos("a@EXAMPLE.COM"), kerberos("b@EXAMPLE.COM")));
            fail("ambiguous");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("more than one Kerberos"));
        }
    }

    @Test
    public void aDistinguishedNameAloneIsPinnedWhenNothingBetterExists() throws Exception {
        // A DN names no workload (another certificate may carry it), but it is what the stream's
        // subject carried before, so such servers keep principal-scoped grants.
        X500Principal dn = new X500Principal("CN=reggie");
        assertSame(dn, ServerIdentityPin.select(set(dn)));
    }

    @Test
    public void twoDistinguishedNamesAreRefused() {
        try {
            ServerIdentityPin.select(set(new X500Principal("CN=a"), new X500Principal("CN=b")));
            fail("ambiguous");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("more than one X.500"));
        }
    }

    @Test
    public void nothingRecognisableIsRefusedNotTreatedAsAnonymous() {
        try {
            ServerIdentityPin.select(set());
            fail("an authenticated server with no recognisable principal");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no SPIFFE, Kerberos or X.500"));
        }
    }

    @Test
    public void aPrincipalOnlyNamedLikeSpiffeIsNotTaken() throws Exception {
        Principal imitation = new Principal() {
            @Override public String getName() { return "spiffe://example/a"; }
        };
        try {
            ServerIdentityPin.select(set(imitation));
            fail("recognised by class, not by name");
        } catch (IOException expected) {
        }
    }

    // ---- which handlers are asked ------------------------------------------

    @Test
    public void aProxyWithAForeignHandlerIsNeverAsked() throws Exception {
        ForeignHandler h = new ForeignHandler();
        Object proxy = Proxy.newProxyInstance(CodebaseAccessor.class.getClassLoader(),
                new Class<?>[]{CodebaseAccessor.class}, h);
        assertNull("a handler of another class could answer whatever it liked",
                ServerIdentityPin.probe(proxy));
        assertFalse("its authenticateServer must not run", h.asked);
    }

    @Test
    public void somethingThatIsNotAProxyHasNoIdentity() throws Exception {
        assertNull(ServerIdentityPin.probe(new Object()));
        assertNull(ServerIdentityPin.probe(null));
    }

    /** Public so that a careless reflective call would succeed: only the class check stops it. */
    public static final class ForeignHandler implements InvocationHandler {
        volatile boolean asked;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }

        public Set<Principal> authenticateServer(Method m) {
            asked = true;
            return set(kerberos("evil/host@EXAMPLE.COM"));
        }
    }

    // ---- the pin ------------------------------------------------------------

    @Test
    public void everyCodebaseCallRequiresTheServerAndKeepsTheExistingConstraints() throws Exception {
        MethodConstraints existing = new BasicMethodConstraints(
                new InvocationConstraints(Integrity.YES, null));
        Recorder r = new Recorder(existing);
        CodebaseAccessor proxy = r.proxy();
        Principal pin = spiffe("spiffe://example/reggie");

        CodebaseAccessor pinned = ServerIdentityPin.pin(proxy, pin);
        assertNotNull(pinned);
        assertNotNull("setConstraints was called", r.set);

        Set<InvocationConstraint> expected = new LinkedHashSet<InvocationConstraint>(Arrays.<InvocationConstraint>asList(
                ServerAuthentication.YES, new ServerMinPrincipal(pin)));
        for (Method m : CodebaseAccessor.class.getMethods()) {
            Set<?> reqs = r.set.getConstraints(m).requirements();
            assertTrue(m.getName() + " requires server authentication", reqs.containsAll(expected));
            assertTrue(m.getName() + " keeps the integrity requirement", reqs.contains(Integrity.YES));
        }
        // A method the table does not name still requires the pin.
        Set<?> other = r.set.getConstraints(Object.class.getMethod("toString")).requirements();
        assertTrue(other.containsAll(expected));
    }

    @Test
    public void aServerMinPrincipalTheProxyAlreadyCarriesIsReplacedByThePin() throws Exception {
        // Two different ServerMinPrincipal requirements cannot both be met on one connection.
        MethodConstraints existing = new BasicMethodConstraints(new InvocationConstraints(
                new InvocationConstraint[]{Integrity.YES,
                    new ServerMinPrincipal(new X500Principal("CN=reggie"))}, null));
        Recorder r = new Recorder(existing);
        Principal pin = spiffe("spiffe://example/reggie");
        ServerIdentityPin.pin(r.proxy(), pin);
        for (Method m : CodebaseAccessor.class.getMethods()) {
            int count = 0;
            for (Object c : r.set.getConstraints(m).requirements()) {
                if (c instanceof ServerMinPrincipal) {
                    count++;
                    assertEquals(new ServerMinPrincipal(pin), c);
                }
            }
            assertEquals(m.getName() + ": exactly one ServerMinPrincipal", 1, count);
            assertTrue(r.set.getConstraints(m).requirements().contains(Integrity.YES));
        }
    }

    @Test
    public void theRouterContextCarriesThePinnedIdentityNotTheStreams() throws Exception {
        // A ServerSubject in the unmarshalling stream is the delivering server's.
        final javax.security.auth.Subject streams = new javax.security.auth.Subject(true,
                Collections.<Principal>singleton(spiffe("spiffe://example/delivering-server")),
                Collections.emptySet(), Collections.emptySet());
        net.jini.io.context.IntegrityEnforcement integrity = new net.jini.io.context.IntegrityEnforcement() {
            public boolean integrityEnforced() { return true; }
        };
        java.util.List<Object> context = new java.util.ArrayList<Object>();
        context.add(integrity);
        context.add(new net.jini.io.context.ServerSubject() {
            public javax.security.auth.Subject getServerSubject() { return streams; }
        });
        Principal pin = spiffe("spiffe://example/the-proxys-server");

        java.util.Collection routed = PreferredProxyCodebaseProvider.contextWithPinnedIdentity(
                context, new Principal[]{pin});

        assertTrue("other elements are kept", routed.contains(integrity));
        int subjects = 0;
        for (Object o : routed) {
            if (o instanceof net.jini.io.context.ServerSubject) {
                subjects++;
                assertEquals(Collections.singleton(pin),
                        ((net.jini.io.context.ServerSubject) o).getServerSubject().getPrincipals());
            }
        }
        assertEquals("exactly one ServerSubject, the pinned one", 1, subjects);
        assertEquals("the stream's own context is untouched", 2, context.size());
    }

    @Test
    public void aProxyThatCannotCarryConstraintsCannotBePinned() throws Exception {
        CodebaseAccessor plain = (CodebaseAccessor) Proxy.newProxyInstance(
                CodebaseAccessor.class.getClassLoader(), new Class<?>[]{CodebaseAccessor.class},
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) { return null; }
                });
        try {
            ServerIdentityPin.pin(plain, spiffe("spiffe://example/a"));
            fail("refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("RemoteMethodControl"));
        }
    }

    /** A bootstrap proxy that records the constraints it is given. */
    private static final class Recorder implements InvocationHandler {
        private final MethodConstraints existing;
        volatile MethodConstraints set;

        Recorder(MethodConstraints existing) {
            this.existing = existing;
        }

        CodebaseAccessor proxy() {
            return (CodebaseAccessor) Proxy.newProxyInstance(
                    CodebaseAccessor.class.getClassLoader(),
                    new Class<?>[]{CodebaseAccessor.class, RemoteMethodControl.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getConstraints":
                    return existing;
                case "setConstraints":
                    set = (MethodConstraints) args[0];
                    return proxy;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return null;
            }
        }
    }
}
