/*
 * Copyright 2026 The Apache Software Foundation.
 *
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
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.io.context.IntegrityEnforcement;
import net.jini.io.context.ServerSubject;
import net.jini.loader.pref.PreferredProxyCodebaseProvider.SmartProxyIsolationRouter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pins down where {@code PreferredProxyCodebaseProvider.resolve} takes the
 * server identity from, which keys the isolation pool and (with a verified
 * digest) the grants.
 * <p>
 * Finding under test: the identity is the {@link ServerSubject} found in the
 * <em>unmarshalling stream's context</em>, not something learned from the
 * bootstrap proxy's own connection.  That stream context is populated by the
 * transport that delivered the bytes ({@code AtomicInvocationHandler} calls
 * {@code request.populateContext(context)} on the response, and
 * {@code SslConnection} adds the delivering server's {@code ServerSubject}).
 * So a bootstrap proxy that is nested in a response from service A is
 * attributed to A, whoever its own server is.  A proxy taken from a lookup
 * result is unmarshalled by {@code MarshalledWrapper.get()} with no context
 * (only an {@code IntegrityEnforcement}), so it has no identity at all.
 * <p>
 * These tests record the behaviour as it is; they are not an endorsement.
 */
public class PreferredProxyServerIdentityTest {

    private static final String FLAG =
            PreferredProxyCodebaseProvider.SMART_PROXY_ISOLATION_ENABLED_PROPERTY;
    private static final String PATH_A = "http://localhost/service-a.jar";
    private static final String PATH_B = "http://localhost/service-b.jar";

    private SmartProxyIsolationRouter savedRouter;
    private String savedFlag;

    @Before
    public void setUp() {
        savedRouter = PreferredProxyCodebaseProvider.getIsolationRouter();
        savedFlag = System.getProperty(FLAG);
        System.setProperty(FLAG, "true");
    }

    @After
    public void tearDown() {
        PreferredProxyCodebaseProvider.setIsolationRouter(savedRouter);
        if (savedFlag == null) System.clearProperty(FLAG);
        else System.setProperty(FLAG, savedFlag);
    }

    @Test
    public void identityIsTheDeliveringStreamsNotTheBootstrapProxysOwn() throws Exception {
        Probe probe = new Probe();
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);

        // Service B's bootstrap proxy, found inside a response delivered by
        // service A: the stream context carries A's ServerSubject.
        new PreferredProxyCodebaseProvider().resolve(accessor(PATH_B), null, 
                loader(), null, context(subject("spiffe://example/A")));

        assertTrue(probe.invoked);
        assertEquals("service B's proxy is attributed to the delivering server",
                "spiffe://example/A", probe.principals[0].getName());
    }

    @Test
    public void aLookupResultCarriesNoServerSubjectAndIsRefusedWhenIsolating() throws Exception {
        Probe probe = new Probe();
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);

        // MarshalledInstance.get with a null context builds its own, holding
        // only an IntegrityEnforcement: no ServerSubject.
        Collection lookupContext = context(new IntegrityEnforcement() {
            public boolean integrityEnforced() { return true; }
        });
        try {
            new PreferredProxyCodebaseProvider().resolve(accessor(PATH_A), null, 
                    loader(), null, lookupContext);
            fail("no identity: fail closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("fail-closed"));
        }
        assertFalse(probe.invoked);
    }

    @Test
    public void sameStreamSameIdentityForDifferentServers() throws Exception {
        Probe probe = new Probe();
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);
        Collection ctx = context(subject("spiffe://example/A"));
        new PreferredProxyCodebaseProvider().resolve(accessor(PATH_A), null, loader(), null, ctx);
        Principal first = probe.principals[0];
        new PreferredProxyCodebaseProvider().resolve(accessor(PATH_B), null, loader(), null, ctx);
        assertEquals("two different servers' proxies, one pool key", 
                first.getName(), probe.principals[0].getName());
    }

    // ---- helpers (as in PreferredProxyCodebaseProviderIsolationTest) ----------

    private static ClassLoader loader() {
        return PreferredProxyServerIdentityTest.class.getClassLoader();
    }

    private static Collection context(Object... elements) {
        Collection c = new ArrayList();
        Collections.addAll(c, elements);
        return c;
    }

    private static ServerSubject subject(String name) {
        Set<Principal> principals = new LinkedHashSet<>();
        principals.add(new Principal() {
            public String getName() { return name; }
            public boolean equals(Object o) {
                return o instanceof Principal && name.equals(((Principal) o).getName());
            }
            public int hashCode() { return name.hashCode(); }
            public String toString() { return name; }
        });
        final Subject s = new Subject(true, principals, Collections.emptySet(), 
                Collections.emptySet());
        return () -> s;
    }

    private static CodebaseAccessor accessor(final String path) {
        InvocationHandler h = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "getClassAnnotation": return path;
                    case "setConstraints": return proxy; // a real bootstrap proxy is RemoteMethodControl
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == (args == null ? null : args[0]);
                    case "toString": return "accessorProxy[" + path + "]";
                    default: return null;
                }
            }
        };
        return (CodebaseAccessor) Proxy.newProxyInstance(
                CodebaseAccessor.class.getClassLoader(),
                new Class<?>[]{ CodebaseAccessor.class, RemoteMethodControl.class }, h);
    }

    private static final class Probe implements SmartProxyIsolationRouter {
        volatile boolean invoked;
        volatile Principal[] principals;
        public Object route(Principal[] serverPrincipals, CodebaseAccessor bootstrapProxy,
                            MarshalledInstance serviceProxy, URL[] codebase, String path,
                            ClassLoader parent, ClassLoader verifier, Collection context) {
            invoked = true;
            principals = serverPrincipals;
            return new Object();
        }
    }
}
