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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import javax.security.auth.Subject;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.io.context.ServerSubject;
import net.jini.loader.pref.PreferredProxyCodebaseProvider.SmartProxyIsolationRouter;
import net.jini.loader.pref.PreferredProxyCodebaseProvider.UnsupportedIsolationRouter;
import org.apache.river.api.net.Uri;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Adversarial tests for the T1 smart-proxy OS-process isolation routing
 * branch inserted into {@link PreferredProxyCodebaseProvider#resolve}.
 *
 * <p>These probes verify the highest-risk properties of the branch and,
 * crucially, its <em>placement</em>.  The branch sits AFTER the two
 * "already local, first-party" fast paths ({@code SERVICES_EXP} lookup and
 * the self-unmarshal-via-parent special case) and BEFORE the {@code CACHE}
 * lookup / new-loader-creation block.  Consequently:
 * <ol>
 *   <li>a {@code SERVICES_EXP} hit (an object THIS process exported) MUST
 *       bypass isolation entirely even when the flag is ON -- it is
 *       first-party code, not another party's downloaded mobile bytecode
 *       (see {@link #flagOn_servicesExpHit_bypassesIsolation_usesExportedLoader});</li>
 *   <li>the self-unmarshal-via-parent case (proxy already resolvable by the
 *       parent stream loader) MUST likewise bypass isolation even when the
 *       flag is ON (see {@link #flagOn_selfUnmarshalViaParent_bypassesIsolation});</li>
 *   <li>with the flag ON, a principal present, and NO first-party loader
 *       found (the CACHE-miss / new-loader path), the router IS invoked and
 *       neither {@code CACHE} nor {@code SERVICES_EXP} gains an entry
 *       (see {@link #flagOn_cacheMissNewLoaderPath_routesAndBypassesLegacy});</li>
 *   <li>the fail-closed refusal (flag ON, no principal, no first-party
 *       loader) never leaves a loader cached in {@code CACHE} or
 *       {@code SERVICES_EXP} and never invokes the router;</li>
 *   <li>with the flag OFF (default and explicit) the router is never
 *       consulted;</li>
 *   <li>the null / empty / single / multiple principal boundaries are each
 *       handled as intended.</li>
 * </ol>
 *
 * @since 3.1.1
 */
public class PreferredProxyCodebaseProviderIsolationTest {

    private static final String FLAG =
            PreferredProxyCodebaseProvider.SMART_PROXY_ISOLATION_ENABLED_PROPERTY;

    /** A single non-directory codebase URL so pathToURIs / asURL succeed. */
    private static final String PATH = "http://localhost/app.jar";

    private SmartProxyIsolationRouter savedRouter;
    private String savedFlag;

    @Before
    public void setUp() {
        savedRouter = PreferredProxyCodebaseProvider.getIsolationRouter();
        savedFlag = System.getProperty(FLAG);
        System.clearProperty(FLAG);
    }

    @After
    public void tearDown() {
        PreferredProxyCodebaseProvider.setIsolationRouter(savedRouter);
        if (savedFlag == null) System.clearProperty(FLAG);
        else System.setProperty(FLAG, savedFlag);
    }

    // ------------------------------------------------------------------
    // Flag helper: default OFF, explicit false OFF, true ON.
    // ------------------------------------------------------------------

    @Test
    public void flagDefaultsOff() {
        System.clearProperty(FLAG);
        assertFalse("isolation routing must default to off",
                PreferredProxyCodebaseProvider.isolationRoutingEnabled());
    }

    @Test
    public void flagExplicitFalseIsOff() {
        System.setProperty(FLAG, "false");
        assertFalse(PreferredProxyCodebaseProvider.isolationRoutingEnabled());
    }

    @Test
    public void flagTrueIsOn() {
        System.setProperty(FLAG, "true");
        assertTrue(PreferredProxyCodebaseProvider.isolationRoutingEnabled());
    }

    @Test
    public void defaultRouterIsTheUnsupportedPlaceholder() {
        assertTrue(PreferredProxyCodebaseProvider.getIsolationRouter()
                instanceof UnsupportedIsolationRouter);
    }

    // ------------------------------------------------------------------
    // (1) SERVICES_EXP hit -> isolation bypassed EVEN WHEN THE FLAG IS ON.
    //
    // A SERVICES_EXP hit means this very process exported the object
    // (record(...) is the only writer, keyed by (handler, codebase)); it is
    // first-party code, definitionally outside the isolation threat model.
    // The isolation branch must NOT intercept it: the exported loader must be
    // used and the router must never be consulted.
    // ------------------------------------------------------------------

    @Test
    public void flagOn_servicesExpHit_bypassesIsolation_usesExportedLoader()
            throws Exception {
        System.setProperty(FLAG, "true");
        ProbeRouter probe = new ProbeRouter(new Object());
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);

        CodebaseAccessor accessor = accessor(PATH);
        // The loader this process "exported" for the object.
        ClassLoader exportedLoader = new URLClassLoader(new URL[0], null);
        Object exportKey = makeExportKey(accessor);
        ConcurrentMap<Object, Object> servicesExp = mapField("SERVICES_EXP");

        Object sentinel = new Object();
        RecordingMarshalledInstance serviceProxy =
                new RecordingMarshalledInstance(sentinel);

        int cacheBefore = cacheSize();
        servicesExp.put(exportKey, exportedLoader);
        try {
            Object result = new PreferredProxyCodebaseProvider().resolve(
                    accessor, serviceProxy, parentLoader(), null,
                    context(subject("spiffe://example/a")));

            assertFalse("router must NOT be consulted on a SERVICES_EXP hit"
                    + " (first-party, exported-from-here) even with the flag on",
                    probe.invoked);
            assertTrue("serviceProxy.get must have been driven with the"
                    + " exported loader (legacy resolution proceeded)",
                    serviceProxy.getCalled);
            assertSame("the SERVICES_EXP-exported loader must be used, not an"
                    + " isolation subprocess", exportedLoader,
                    serviceProxy.loaderSeen);
            assertSame("resolve must return the unmarshalled proxy verbatim",
                    sentinel, result);
            // No NEW loader was created; CACHE untouched.
            assertEquals("CACHE must not have grown", cacheBefore, cacheSize());
        } finally {
            servicesExp.remove(exportKey);
        }
    }

    // ------------------------------------------------------------------
    // (2) self-unmarshal-via-parent -> isolation bypassed EVEN WHEN ON.
    //
    // When the codebase annotation equals the parent (stream) loader's own
    // annotation, resolve() reuses the parent loader (loader = parent): the
    // proxy is already resolvable locally, not a fresh download.  The
    // isolation branch must not intercept this either.
    // ------------------------------------------------------------------

    @Test
    public void flagOn_selfUnmarshalViaParent_bypassesIsolation()
            throws Exception {
        System.setProperty(FLAG, "true");
        ProbeRouter probe = new ProbeRouter(new Object());
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);

        // A URLClassLoader whose single URL is PATH: getLoaderAnnotation()
        // returns urlsToPath(urls) == PATH, so path.equals(loaderPath) and the
        // self-unmarshal-via-parent special case fires (loader = parent).
        URLClassLoader parent =
                new URLClassLoader(new URL[]{ new URL(PATH) }, null);

        Object sentinel = new Object();
        RecordingMarshalledInstance serviceProxy =
                new RecordingMarshalledInstance(sentinel);

        int cacheBefore = cacheSize();
        int expBefore = servicesExpSize();

        Object result = new PreferredProxyCodebaseProvider().resolve(
                accessor(PATH), serviceProxy, parent, null,
                context(subject("spiffe://example/a")));

        assertFalse("router must NOT be consulted on the self-unmarshal-via-"
                + "parent path (already-local proxy) even with the flag on",
                probe.invoked);
        assertTrue("serviceProxy.get must have been driven with the parent"
                + " loader (legacy resolution proceeded)", serviceProxy.getCalled);
        assertSame("the parent loader itself must be used, not an isolation"
                + " subprocess", parent, serviceProxy.loaderSeen);
        assertSame("resolve must return the unmarshalled proxy verbatim",
                sentinel, result);
        assertEquals("CACHE must not have grown", cacheBefore, cacheSize());
        assertEquals("SERVICES_EXP must not have grown", expBefore,
                servicesExpSize());
    }

    // ------------------------------------------------------------------
    // (3) Flag ON + principal + NO first-party loader (CACHE-miss /
    //     new-loader path) -> router invoked, legacy path bypassed, and
    //     neither map gains an entry.  This is the case isolation is meant
    //     to intercept: bytecode this process would otherwise download and
    //     classload in-process.
    // ------------------------------------------------------------------

    @Test
    public void flagOn_cacheMissNewLoaderPath_routesAndBypassesLegacy()
            throws Exception {
        System.setProperty(FLAG, "true");
        Object sentinel = new Object();
        ProbeRouter probe = new ProbeRouter(sentinel);
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);

        int cacheBefore = cacheSize();
        int expBefore = servicesExpSize();

        // parentLoader() is the test's own loader; its annotation is not PATH,
        // so the self-unmarshal special case does NOT fire, SERVICES_EXP is a
        // miss, and control reaches the isolation branch with loader == null.
        Object result = new PreferredProxyCodebaseProvider().resolve(
                accessor(PATH), null, parentLoader(), null,
                context(subject("spiffe://example/a")));

        assertSame("router result must be returned verbatim", sentinel, result);
        assertTrue("router must have been invoked on the CACHE-miss/new-loader"
                + " path", probe.invoked);
        assertEquals("exactly one principal expected", 1,
                probe.principals.length);
        // Legacy new-loader path was NOT taken: no fresh CACHE entry, and the
        // SERVICES_EXP map was not mutated either.
        assertEquals("CACHE must not have gained a new-loader entry",
                cacheBefore, cacheSize());
        assertEquals("SERVICES_EXP must not have grown", expBefore,
                servicesExpSize());
    }

    @Test
    public void flagOn_withMultiplePrincipals_routes() throws Exception {
        System.setProperty(FLAG, "true");
        Object sentinel = new Object();
        ProbeRouter probe = new ProbeRouter(sentinel);
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);

        Object result = new PreferredProxyCodebaseProvider().resolve(
                accessor(PATH), null, parentLoader(), null,
                context(subject("spiffe://example/a", "spiffe://example/b")));

        assertSame(sentinel, result);
        assertTrue(probe.invoked);
        assertEquals(2, probe.principals.length);
    }

    // ------------------------------------------------------------------
    // (4) Fail-closed: flag ON, no principal, no first-party loader ->
    //     refuse, no caching, router never called.  Covers null and empty
    //     boundaries.  Placement note: the fail-closed check now sits after
    //     SERVICES_EXP / self-unmarshal, so it is only reached when neither
    //     first-party fast path found a loader (loader == null here).
    // ------------------------------------------------------------------

    @Test
    public void flagOn_noServerSubject_failsClosed_noCaching() throws Exception {
        System.setProperty(FLAG, "true");
        ProbeRouter probe = new ProbeRouter(new Object());
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);

        int cacheBefore = cacheSize();
        int expBefore = servicesExpSize();

        try {
            new PreferredProxyCodebaseProvider().resolve(
                    accessor(PATH), null, parentLoader(), null,
                    context(/* no ServerSubject -> serverPrincipals null */));
            fail("expected fail-closed IOException");
        } catch (IOException expected) {
            assertTrue("message must state fail-closed refusal",
                    expected.getMessage().contains("fail-closed"));
        }
        assertFalse("router must NOT be invoked on the refusal path",
                probe.invoked);
        assertEquals("CACHE must not have grown", cacheBefore, cacheSize());
        assertEquals("SERVICES_EXP must not have grown", expBefore,
                servicesExpSize());
    }

    @Test
    public void flagOn_emptyPrincipalSet_failsClosed() throws Exception {
        System.setProperty(FLAG, "true");
        ProbeRouter probe = new ProbeRouter(new Object());
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);

        int cacheBefore = cacheSize();
        int expBefore = servicesExpSize();

        try {
            new PreferredProxyCodebaseProvider().resolve(
                    accessor(PATH), null, parentLoader(), null,
                    context(emptySubject()));
            fail("expected fail-closed IOException for empty principal set");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("fail-closed"));
        }
        assertFalse(probe.invoked);
        assertEquals("CACHE must not have grown", cacheBefore, cacheSize());
        assertEquals("SERVICES_EXP must not have grown", expBefore,
                servicesExpSize());
    }

    // ------------------------------------------------------------------
    // (5) Flag OFF -> router never consulted (legacy path taken).
    // ------------------------------------------------------------------

    @Test
    public void flagOff_neverRoutes() {
        System.clearProperty(FLAG); // default off
        ProbeRouter probe = new ProbeRouter(new Object());
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);
        try {
            // With the flag off, resolve() proceeds into the legacy new-loader
            // path, which tries to actually download PATH and throws.  The
            // specific throwable is irrelevant -- what matters is the router
            // was never consulted.
            new PreferredProxyCodebaseProvider().resolve(
                    accessor(PATH), null, parentLoader(), null,
                    context(subject("spiffe://example/a")));
        } catch (Throwable ignored) {
            // expected: legacy path fails trying to download the codebase
        }
        assertFalse("router must never be consulted when the flag is off",
                probe.invoked);
    }

    @Test
    public void flagExplicitFalse_neverRoutes() {
        System.setProperty(FLAG, "false");
        ProbeRouter probe = new ProbeRouter(new Object());
        PreferredProxyCodebaseProvider.setIsolationRouter(probe);
        try {
            new PreferredProxyCodebaseProvider().resolve(
                    accessor(PATH), null, parentLoader(), null,
                    context(subject("spiffe://example/a")));
        } catch (Throwable ignored) {
        }
        assertFalse(probe.invoked);
    }

    // ------------------------------------------------------------------
    // Default (placeholder) router: throws UnsupportedOperationException
    // naming T2/T4, both directly and through resolve() (CACHE-miss path).
    // ------------------------------------------------------------------

    @Test
    public void defaultRouterThrowsUnsupportedNamingT2AndT4() {
        try {
            new UnsupportedIsolationRouter().route(
                    new Principal[]{namePrincipal("spiffe://example/a")},
                    accessor(PATH), null, new URL[0], PATH,
                    parentLoader(), null, context());
            fail("placeholder router must throw");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("T2"));
            assertTrue(e.getMessage().contains("T4"));
            assertTrue(e.getMessage().contains(FLAG));
        }
    }

    @Test
    public void resolveWithDefaultRouter_flagOn_throwsUnsupported() throws Exception {
        System.setProperty(FLAG, "true");
        // leave the default UnsupportedIsolationRouter installed
        try {
            new PreferredProxyCodebaseProvider().resolve(
                    accessor(PATH), null, parentLoader(), null,
                    context(subject("spiffe://example/a")));
            fail("expected UnsupportedOperationException from placeholder");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("T2"));
            assertTrue(e.getMessage().contains("T4"));
        }
    }

    // ==================================================================
    // Test support
    // ==================================================================

    /** Records whether it was invoked and returns a fixed sentinel. */
    private static final class ProbeRouter implements SmartProxyIsolationRouter {
        final Object result;
        volatile boolean invoked;
        volatile Principal[] principals;
        ProbeRouter(Object result) { this.result = result; }
        @Override
        public Object route(Principal[] serverPrincipals,
                            CodebaseAccessor bootstrapProxy,
                            MarshalledInstance serviceProxy,
                            URL[] codebase, String path,
                            ClassLoader parent, ClassLoader verifier,
                            Collection context) {
            this.invoked = true;
            this.principals = serverPrincipals;
            return result;
        }
    }

    /**
     * A {@link MarshalledInstance} stub that records the loader
     * {@code resolve()} drives the unmarshal with and returns a fixed
     * sentinel, so a test can prove WHICH loader (exported / parent) the
     * legacy path used when isolation is correctly bypassed.
     */
    private static final class RecordingMarshalledInstance
            extends MarshalledInstance {
        final Object result;
        volatile boolean getCalled;
        volatile ClassLoader loaderSeen;
        RecordingMarshalledInstance(Object result) throws IOException {
            super("dummy");
            this.result = result;
        }
        @Override
        public Object get(ClassLoader defaultLoader,
                          boolean verifyCodebaseIntegrity,
                          ClassLoader verifierLoader,
                          Collection context) {
            this.getCalled = true;
            this.loaderSeen = defaultLoader;
            return result;
        }
    }

    private static ClassLoader parentLoader() {
        return PreferredProxyCodebaseProviderIsolationTest.class.getClassLoader();
    }

    private static Collection context(Object... elements) {
        Collection c = new ArrayList();
        Collections.addAll(c, elements);
        return c;
    }

    private static ServerSubject subject(String... principalNames) {
        Set<Principal> principals = new LinkedHashSet<>();
        for (String n : principalNames) principals.add(namePrincipal(n));
        final Subject s = new Subject(true, principals,
                Collections.emptySet(), Collections.emptySet());
        return () -> s;
    }

    private static ServerSubject emptySubject() {
        final Subject s = new Subject(true, Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet());
        return () -> s;
    }

    private static Principal namePrincipal(final String name) {
        return new Principal() {
            @Override public String getName() { return name; }
            @Override public boolean equals(Object o) {
                return o instanceof Principal
                        && name.equals(((Principal) o).getName());
            }
            @Override public int hashCode() { return name.hashCode(); }
            @Override public String toString() { return name; }
        };
    }

    /**
     * Minimal {@link CodebaseAccessor} whose annotation is {@code path},
     * realised as a dynamic {@link Proxy} so that
     * {@code Proxy.getInvocationHandler(bootstrapProxy)} (used to build the
     * SERVICES_EXP / CACHE keys in {@code resolve()}) succeeds.
     */
    private static CodebaseAccessor accessor(final String path) {
        InvocationHandler h = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "getClassAnnotation": return path;
                    case "getCertFactoryType": return null;
                    case "getCertPathEncoding": return null;
                    case "getEncodedCerts": return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == (args == null ? null : args[0]);
                    case "toString": return "accessorProxy[" + path + "]";
                    default: return null;
                }
            }
        };
        return (CodebaseAccessor) Proxy.newProxyInstance(
                CodebaseAccessor.class.getClassLoader(),
                new Class<?>[]{ CodebaseAccessor.class },
                h);
    }

    /**
     * Builds the exact {@code SERVICES_EXP} key {@code resolve()} constructs
     * for {@code bootstrapProxy} at {@link #PATH}: {@code Key(handler,
     * Arrays.asList(pathToURIs(PATH)), null)}.  Reflective because {@code Key}
     * is a private nested class.
     */
    private static Object makeExportKey(CodebaseAccessor bootstrapProxy)
            throws Exception {
        Class<?> keyClass = Class.forName(
                "net.jini.loader.pref.PreferredProxyCodebaseProvider$Key");
        Constructor<?> ctor = keyClass.getDeclaredConstructor(
                InvocationHandler.class, List.class, ClassLoader.class);
        ctor.setAccessible(true);
        Uri[] uris = PreferredClassProvider.pathToURIs(PATH);
        return ctor.newInstance(
                Proxy.getInvocationHandler(bootstrapProxy),
                Arrays.asList(uris), null);
    }

    @SuppressWarnings("unchecked")
    private static int cacheSize() throws Exception {
        return mapField("CACHE").size();
    }

    @SuppressWarnings("unchecked")
    private static int servicesExpSize() throws Exception {
        return mapField("SERVICES_EXP").size();
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<Object, Object> mapField(String name)
            throws Exception {
        Field f = PreferredProxyCodebaseProvider.class.getDeclaredField(name);
        f.setAccessible(true);
        return (ConcurrentMap<Object, Object>) f.get(null);
    }
}
