/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;
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
import net.jini.export.Exporter;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.SslEngineSvidFactory.Svid;
import net.jini.jeri.tcp.TcpServerEndpoint;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * {@link BasicInvocationHandler#authenticateServer}: the server principals
 * authenticated on the connection a call would use, read without invoking a
 * method.  Real SSL JERI with SPIFFE-style test credentials.
 */
public class AuthenticateServerTest {

    public interface Echo extends Remote { String echo(String s) throws RemoteException; }
    public static final class EchoImpl implements Echo { 
        public String echo(String s) { return "echo:" + s; } 
    }

    static final InvocationConstraints MUTUAL = new InvocationConstraints(
        new InvocationConstraint[]{ClientAuthentication.YES, ServerAuthentication.YES,
                                   Integrity.YES, Confidentiality.YES}, null);

    private static final Collection<Object> KEEP = new ArrayList<Object>();
    private static Svid server, sameDn, twoUri, client;
    private static Echo reggie, impostor, twoUris, tcp;
    private static Method echo;

    @BeforeClass
    public static void setUp() throws Exception {
        echo = Echo.class.getMethod("echo", String.class);
        Path dir = Files.createTempDirectory("auth-server");
        server = SslEngineSvidFactory.generate(dir, "server", "CN=Reggie", "spiffe://example.org/reggie");
        sameDn = SslEngineSvidFactory.generate(dir, "samedn", "CN=Reggie", "spiffe://example.org/impostor");
        twoUri = SslEngineSvidFactory.generate(dir, "twouri", "CN=Reggie",
            "spiffe://example.org/alpha,uri:spiffe://example.org/beta");
        client = SslEngineSvidFactory.generate(dir, "client", "CN=Tester", "spiffe://example.org/tester");
        SslEngineSvidFactory.installTrustStore(dir, server.leaf, sameDn.leaf, twoUri.leaf, client.leaf);
        reggie = export(server);
        impostor = export(sameDn);
        twoUris = export(twoUri);
        Exporter tex = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
            new AtomicILFactory(null, null, AuthenticateServerTest.class.getClassLoader()), false, true);
        EchoImpl ti = new EchoImpl();
        KEEP.add(ti);
        KEEP.add(tex);
        tcp = (Echo) tex.export(ti);
    }

    @AfterClass
    public static void tearDown() {
        for (Object o : KEEP) {
            if (o instanceof Exporter) ((Exporter) o).unexport(true);
        }
    }

    private static Echo export(Svid s) throws Exception {
        ServerEndpoint se = SslServerEndpoint.getInstance(s.subject, new X500Principal[]{s.x500}, "localhost", 0);
        Exporter ex = new BasicJeriExporter(se, new AtomicILFactory(new BasicMethodConstraints(MUTUAL), null,
            AuthenticateServerTest.class.getClassLoader()), false, true);
        EchoImpl impl = new EchoImpl();
        KEEP.add(impl);
        KEEP.add(ex);
        return (Echo) Subject.callAs(s.subject, (Callable<Echo>) () -> (Echo) ex.export(impl));
    }

    private static <T> T asClient(Callable<T> c) throws Exception {
        try {
            return Subject.callAs(client.subject, c);
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }

    private static BasicInvocationHandler handler(Echo proxy) {
        return (BasicInvocationHandler) Proxy.getInvocationHandler(proxy);
    }

    private static Principal spiffeNamed(Set<Principal> ps, String fragment) {
        for (Principal p : ps) if (p.getName().startsWith("spiffe://") && p.getName().contains(fragment)) return p;
        throw new AssertionError("no SPIFFE principal containing " + fragment + " in " + ps);
    }

    private static int spiffeCount(Set<Principal> ps) {
        int n = 0;
        for (Principal p : ps) if (p.getName().startsWith("spiffe://")) n++;
        return n;
    }

    private static Echo pinned(Echo proxy, Principal pin) {
        InvocationConstraints c = new InvocationConstraints(
            new InvocationConstraint[]{ClientAuthentication.YES, ServerAuthentication.YES,
                new ServerMinPrincipal(pin), Integrity.YES, Confidentiality.YES}, null);
        return (Echo) ((RemoteMethodControl) proxy).setConstraints(new BasicMethodConstraints(c));
    }

    @Test
    public void returnsTheAuthenticatedServerPrincipals() throws Exception {
        Set<Principal> ps = asClient(() -> handler(reggie).authenticateServer(echo));
        assertEquals(1, spiffeCount(ps));
        spiffeNamed(ps, "/reggie");
        assertTrue("and the X500 name", ps.contains(server.x500));
        try {
            ps.add(server.x500);
            fail("unmodifiable");
        } catch (UnsupportedOperationException expected) { }
        // The connection is usable afterwards, and repeated probes do not leak.
        assertEquals("echo:hi", asClient(() -> reggie.echo("hi")));
        asClient(() -> {
            for (int i = 0; i < 300; i++) handler(reggie).authenticateServer(echo);
            return reggie.echo("still works");
        });
    }

    @Test
    public void aTransportThatCannotAuthenticateTheServerIsRefused() throws Exception {
        try {
            asClient(() -> handler(tcp).authenticateServer(echo));
            fail("TCP authenticates no server");
        } catch (UnsupportedConstraintException expected) { }
    }

    @Test(expected = NullPointerException.class)
    public void nullMethodIsRefused() throws Exception {
        asClient(() -> handler(reggie).authenticateServer(null));
    }

    /** A hostile object endpoint could return a forged connection subject. */
    @Test
    public void aForeignObjectEndpointIsRefusedBeforeAnyIo() throws Exception {
        ObjectEndpoint evil = new ObjectEndpoint() {
            public OutboundRequestIterator newCall(InvocationConstraints c) {
                throw new AssertionError("no I/O may happen");
            }
            public RemoteException executeCall(net.jini.jeri.OutboundRequest r) { 
                throw new AssertionError(); 
            }
        };
        try {
            new BasicInvocationHandler(evil, null).authenticateServer(echo);
            fail("foreign object endpoint");
        } catch (SecurityException expected) { }
    }

    @Test
    public void aForeignHandlerSubclassIsRefused() throws Exception {
        BasicInvocationHandler real = handler(reggie);
        BasicInvocationHandler sub = new BasicInvocationHandler(real.getObjectEndpoint(), null) { };
        try {
            sub.authenticateServer(echo);
            fail("subclass of the handler");
        } catch (SecurityException expected) { }
    }

    // ---- what the pin does with the identity the probe returned -------------

    @Test
    public void pinOnTheProbedSpiffeIdentity() throws Exception {
        Set<Principal> ps = asClient(() -> handler(reggie).authenticateServer(echo));
        Principal pin = spiffeNamed(ps, "/reggie");
        assertEquals("right SVID", "echo:x", asClient(() -> pinned(reggie, pin).echo("x")));
        try {
            asClient(() -> pinned(impostor, pin).echo("x"));
            fail("another SVID with the same DN must fail the pin");
        } catch (Exception expected) { }
    }

    @Test
    public void aDnOnlyPinDoesNotPinTheWorkload() throws Exception {
        // Characterisation: the impostor has the same DN, so a pin on the X500 name admits it.
        assertEquals("echo:x", asClient(() -> pinned(impostor, server.x500).echo("x")));
    }

    @Test
    public void aCertificateWithTwoSpiffeIdsYieldsTwoPrincipals() throws Exception {
        Set<Principal> ps = asClient(() -> handler(twoUris).authenticateServer(echo));
        assertEquals("the caller must refuse an ambiguous identity", 2, spiffeCount(ps));
        Principal alpha = spiffeNamed(ps, "alpha");
        assertEquals("a pin on one present URI is satisfied", "echo:x",
                asClient(() -> pinned(twoUris, alpha).echo("x")));
        try {
            asClient(() -> pinned(twoUris, spiffeNamed(
                    asClient(() -> handler(reggie).authenticateServer(echo)), "/reggie")).echo("x"));
            fail("an absent URI must fail");
        } catch (Exception expected) { }
    }
}
