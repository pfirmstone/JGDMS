/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;
import org.osgi.framework.ServiceReference;

/**
 * Exercises the OSGi provider-discovery fix in {@link Service} /
 * {@link OSGiServiceIterator} without a live OSGi runtime, using small
 * {@link Proxy}-based fakes. Covers: the two-relationship union
 * ({@code ChainedIterator}); the registry lookup returning real service
 * <em>instances</em> (the bug that returned {@code ServiceReference}s / nothing);
 * and the loader-to-bundle scoping with the platform fallback.
 */
public class ServiceOsgiTest {

    public interface Greeter { String greet(); }

    private static Greeter greeter(final String s) {
        return new Greeter() { public String greet() { return s; } };
    }

    // ---- the two-relationship union --------------------------------------

    @Test
    public void chainedIteratorYieldsFirstThenSecond() {
        Iterator<String> a = Arrays.asList("co-loaded-1", "co-loaded-2").iterator();
        Iterator<String> b = Arrays.asList("registry-1", "registry-2").iterator();
        List<String> out = new ArrayList<String>();
        for (Iterator<String> it = new Service.ChainedIterator<String>(a, b); it.hasNext();) {
            out.add(it.next());
        }
        assertEquals(Arrays.asList("co-loaded-1", "co-loaded-2", "registry-1", "registry-2"), out);
    }

    @Test
    public void chainedIteratorHandlesEmptySides() {
        List<String> empty = new ArrayList<String>();
        Iterator<String> only = Arrays.asList("x").iterator();
        Iterator<String> it = new Service.ChainedIterator<String>(empty.iterator(), only);
        assertTrue(it.hasNext());
        assertEquals("x", it.next());
        assertFalse(it.hasNext());
    }

    // ---- registry lookup returns INSTANCES (the fixed bug) ---------------

    @Test
    public void registryProvidersReturnsServiceInstancesNotReferences() {
        Greeter g1 = greeter("hello");
        Greeter g2 = greeter("g'day");
        BundleContext bc = registry(Greeter.class, g1, g2);
        List<Greeter> found = OSGiServiceIterator.registryProviders(Greeter.class, bc);
        assertEquals(2, found.size());          // not zero, the pre-fix behaviour
        assertTrue(found.contains(g1));         // the actual services, via getService
        assertTrue(found.contains(g2));
    }

    @Test
    public void registryProvidersFiltersByType() {
        // A non-Greeter registered under the same name must be skipped by the
        // isInstance guard (rather than ClassCastException-ing).
        BundleContext bc = registryRaw(Greeter.class.getName(),
                greeter("real"), new Object());
        List<Greeter> found = OSGiServiceIterator.registryProviders(Greeter.class, bc);
        assertEquals(1, found.size());
        assertEquals("real", found.get(0).greet());
    }

    @Test
    public void registryProvidersEmptyWhenNoneRegistered() {
        BundleContext bc = registry(Greeter.class);   // none
        assertTrue(OSGiServiceIterator.registryProviders(Greeter.class, bc).isEmpty());
    }

    // ---- loader -> bundle scoping, with platform fallback ----------------

    @Test
    public void contextForUsesTheRequestingLoadersBundle() {
        BundleContext platform = registry(Greeter.class);
        BundleContext bundleCtx = registry(Greeter.class);
        ClassLoader bundleLoader = new FakeBundleLoader(null, bundleCtx);
        assertSame(bundleCtx, OSGiServiceIterator.contextFor(bundleLoader, platform));
    }

    @Test
    public void contextForWalksParentChainToTheBundle() {
        // proxy-codebase model: a plain loader whose PARENT is the (client) bundle.
        BundleContext platform = registry(Greeter.class);
        BundleContext clientBundleCtx = registry(Greeter.class);
        ClassLoader clientBundle = new FakeBundleLoader(null, clientBundleCtx);
        ClassLoader proxyCodebase = new URLClassLoader(new URL[0], clientBundle);
        assertSame(clientBundleCtx, OSGiServiceIterator.contextFor(proxyCodebase, platform));
    }

    @Test
    public void contextForFallsBackToPlatformWhenNotInABundle() {
        BundleContext platform = registry(Greeter.class);
        ClassLoader plain = new URLClassLoader(new URL[0], null);
        assertSame(platform, OSGiServiceIterator.contextFor(plain, platform));
    }

    // ---- fakes -----------------------------------------------------------

    /** A BundleContext fake whose registry contains {@code services} under {@code type}'s name. */
    private static BundleContext registry(Class<?> type, Object... services) {
        return registryRaw(type.getName(), services);
    }

    private static BundleContext registryRaw(String className, Object... services) {
        Map<String, List<Object>> byName = new HashMap<String, List<Object>>();
        byName.put(className, new ArrayList<Object>(Arrays.asList(services)));
        return new FakeRegistry(byName).asContext();
    }

    /** Implements just BundleContext.getServiceReferences(String,String) + getService(ref). */
    private static final class FakeRegistry implements InvocationHandler {
        private final Map<String, List<Object>> byName;
        private final Map<ServiceReference<?>, Object> refToService =
                new IdentityHashMap<ServiceReference<?>, Object>();

        FakeRegistry(Map<String, List<Object>> byName) { this.byName = byName; }

        BundleContext asContext() {
            return (BundleContext) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{ BundleContext.class }, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("getServiceReferences")
                    && args != null && args.length == 2 && args[0] instanceof String) {
                List<Object> svcs = byName.get((String) args[0]);
                if (svcs == null || svcs.isEmpty()) return null;
                ServiceReference<?>[] refs = new ServiceReference<?>[svcs.size()];
                for (int i = 0; i < svcs.size(); i++) {
                    ServiceReference<?> ref = newRef();
                    refToService.put(ref, svcs.get(i));
                    refs[i] = ref;
                }
                return refs;
            }
            if (name.equals("getService") && args != null && args.length == 1) {
                return refToService.get(args[0]);
            }
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("equals")) return proxy == (args == null ? null : args[0]);
            if (name.equals("toString")) return "FakeBundleContext";
            throw new UnsupportedOperationException("FakeBundleContext." + name);
        }

        private static ServiceReference<?> newRef() {
            return (ServiceReference<?>) Proxy.newProxyInstance(
                    FakeRegistry.class.getClassLoader(),
                    new Class<?>[]{ ServiceReference.class },
                    new InvocationHandler() {
                        public Object invoke(Object p, Method m, Object[] a) {
                            String n = m.getName();
                            if (n.equals("hashCode")) return System.identityHashCode(p);
                            if (n.equals("equals")) return p == (a == null ? null : a[0]);
                            if (n.equals("toString")) return "FakeServiceReference";
                            throw new UnsupportedOperationException("ref." + n);
                        }
                    });
        }
    }

    /** A class loader that is a BundleReference to a (faked) bundle with a given context. */
    private static final class FakeBundleLoader extends ClassLoader implements BundleReference {
        private final Bundle bundle;

        FakeBundleLoader(ClassLoader parent, final BundleContext ctx) {
            super(parent);
            this.bundle = (Bundle) Proxy.newProxyInstance(
                    FakeBundleLoader.class.getClassLoader(),
                    new Class<?>[]{ Bundle.class },
                    new InvocationHandler() {
                        public Object invoke(Object p, Method m, Object[] a) {
                            String n = m.getName();
                            if (n.equals("getBundleContext")) return ctx;
                            if (n.equals("hashCode")) return System.identityHashCode(p);
                            if (n.equals("equals")) return p == (a == null ? null : a[0]);
                            if (n.equals("toString")) return "FakeBundle";
                            throw new UnsupportedOperationException("bundle." + n);
                        }
                    });
        }

        @Override
        public Bundle getBundle() { return bundle; }
    }
}
