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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.object.fixtures.Greeter;
import au.net.zeus.jgdms.der.object.fixtures.GreeterHandler;
import au.net.zeus.jgdms.der.object.fixtures.OuterWithProxy;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tolerant per-name resolution and {@link TolerantProxyHandler} wrap/re-forward behaviour for the
 * NESTED (field-level) bare {@code [8]} CTX_PROXY record (STD-008 sec.15.2), the
 * {@code ObjectCodec.decodeProxy}/{@code encodeProxy} half of the fix -- proves the shared
 * {@link ProxyWireSupport} helper behaves identically at both {@code [8]} sites, not just the
 * top-level object-stream one covered by {@code DerObjectStreamTolerantProxyTest}.
 *
 * <p>{@link OuterWithProxy#getGreeter()} is declared as the {@link Greeter} interface, but the
 * runtime value here is a {@code Proxy} implementing BOTH {@link Greeter} and the extra
 * {@link Marker} interface (a proxy may implement more interfaces than the declared field type
 * names -- see {@code ObjectCodec.encodeProxy}'s javadoc), which is what makes it possible to
 * "drop" {@code Marker} specifically while {@code Greeter} (the declared/needed type) still
 * resolves.
 */
class ObjectCodecTolerantProxyTest {

    interface Marker { String mark(); }

    /**
     * As {@code DerObjectStreamTolerantProxyTest.SelectivelyBlockingLoader}: blocks a chosen
     * blacklist of class names, delegating everything else to the real parent loader.
     */
    static final class SelectivelyBlockingLoader extends ClassLoader {
        private final Set<String> blocked;

        SelectivelyBlockingLoader(ClassLoader parent, String... blockedSimpleNames) {
            super(parent);
            this.blocked = new HashSet<>(Arrays.asList(blockedSimpleNames));
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            for (String b : blocked) {
                if (name.endsWith("$" + b) || name.endsWith("." + b) || name.equals(b)) {
                    throw new ClassNotFoundException(
                            "SelectivelyBlockingLoader: " + name + " is deliberately unresolvable");
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    private static Greeter newProxy(String greeting, ClassLoader cl) {
        return (Greeter) Proxy.newProxyInstance(
                cl, new Class<?>[]{ Greeter.class, Marker.class }, new GreeterHandler(greeting));
    }

    private static byte[] encode(OuterWithProxy outer) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(OuterWithProxy.class);
        return ObjectCodec.encodeHierarchy(outer, chain);
    }

    /**
     * Decodes with {@code loader} as both the {@code ResolutionContext} loader AND (for the
     * duration of the call) the thread context class loader: as with the object-stream site,
     * {@code PreferredClassProvider} falls back to the thread context loader when the
     * endpoint-assigned loader fails and no {@code SecurityManager} is installed, so a
     * blacklisted name must be blocked at both to actually simulate "unresolvable at this hop".
     */
    private static OuterWithProxy decode(byte[] encoded, ClassLoader loader) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader prevTccl = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            SchemaChain.Result chain = SchemaGenerator.generateChain(OuterWithProxy.class);
            return ObjectCodec.decodeHierarchy(
                    OuterWithProxy.class, chain, encoded, null, ResolutionContext.of(loader));
        } finally {
            thread.setContextClassLoader(prevTccl);
        }
    }

    /** Nested-field counterpart of the top-level single-interface-missing case. */
    @Test
    void nestedProxy_singleInterfaceMissing_buildsOverResolvedSubset_wrapped() throws Exception {
        ClassLoader real = getClass().getClassLoader();
        Greeter proxy = newProxy("nested-hi", real);
        OuterWithProxy outer = new OuterWithProxy("tag1", proxy);
        byte[] encoded = encode(outer);

        SelectivelyBlockingLoader blockMarker = new SelectivelyBlockingLoader(real, "Marker");
        OuterWithProxy decoded = decode(encoded, blockMarker);

        Greeter g = decoded.getGreeter();
        assertTrue(g instanceof Greeter);
        assertFalse(g instanceof Marker, "Marker must be dropped, not fail the whole nested field");
        assertEquals("nested-hi: world", g.greet("world"), "the real handler must still work");

        InvocationHandler h = Proxy.getInvocationHandler(g);
        assertTrue(h instanceof TolerantProxyHandler);
        assertArrayEquals(new String[]{ Greeter.class.getName(), Marker.class.getName() },
                ((TolerantProxyHandler) h).originalInterfaceNames());
    }

    /**
     * Nested-field counterpart of the all-interfaces-missing-must-fail case. {@code DerGetArg}
     * wraps ANY nested-field decode failure (pre-existing convention, not specific to this fix)
     * in an {@link java.io.InvalidObjectException}; the important assertion is that the
     * underlying cause is the {@link ClassNotFoundException} from
     * {@code ProxyWireSupport.resolveTolerant}'s zero-resolved rejection -- i.e. it still fails
     * fast, not a silently-built empty/degenerate proxy.
     */
    @Test
    void nestedProxy_allInterfacesMissing_stillFails() throws Exception {
        ClassLoader real = getClass().getClassLoader();
        Greeter proxy = newProxy("nested-hi", real);
        OuterWithProxy outer = new OuterWithProxy("tag2", proxy);
        byte[] encoded = encode(outer);

        SelectivelyBlockingLoader blockAll = new SelectivelyBlockingLoader(real, "Greeter", "Marker");
        java.io.InvalidObjectException ioe = assertThrows(java.io.InvalidObjectException.class,
                () -> decode(encoded, blockAll));
        assertTrue(ioe.getCause() instanceof ClassNotFoundException,
                "must fail fast because of the zero-resolved-interfaces rejection, not silently"
                + " build an empty proxy; cause was " + ioe.getCause());
    }

    /** Nested-field counterpart of the nothing-missing / zero-behaviour-change case. */
    @Test
    void nestedProxy_nothingMissing_noWrapper() throws Exception {
        ClassLoader real = getClass().getClassLoader();
        Greeter proxy = newProxy("nested-hi", real);
        OuterWithProxy outer = new OuterWithProxy("tag3", proxy);
        byte[] encoded = encode(outer);

        OuterWithProxy decoded = decode(encoded, real);
        Greeter g = decoded.getGreeter();
        assertTrue(g instanceof Greeter && g instanceof Marker);
        InvocationHandler h = Proxy.getInvocationHandler(g);
        assertFalse(h instanceof TolerantProxyHandler, "nothing dropped -> plain handler, unwrapped");
        assertTrue(h instanceof GreeterHandler);
    }

    /**
     * Nested-field counterpart of the two-hop forwarding landmine-fix test: hop1 decodes with
     * Marker missing, re-encodes (forwards) the resulting OuterWithProxy, and the re-encoded
     * nested [8] record must carry the FULL original interface set, not the narrowed one.
     */
    @Test
    void nestedProxy_twoHopForward_reEncodesFullOriginalInterfaceSet() throws Exception {
        ClassLoader real = getClass().getClassLoader();
        Greeter proxy = newProxy("nested-hop0", real);
        OuterWithProxy sent = new OuterWithProxy("tag4", proxy);
        byte[] wireFromSender = encode(sent);

        SelectivelyBlockingLoader hop1Loader = new SelectivelyBlockingLoader(real, "Marker");
        OuterWithProxy atHop1 = decode(wireFromSender, hop1Loader);
        assertTrue(Proxy.getInvocationHandler(atHop1.getGreeter()) instanceof TolerantProxyHandler);

        byte[] wireForwardedByHop1 = encode(atHop1);
        OuterWithProxy atDownstream = decode(wireForwardedByHop1, real);

        Greeter g = atDownstream.getGreeter();
        assertTrue(g instanceof Greeter);
        assertTrue(g instanceof Marker,
                "a downstream hop that CAN resolve Marker must still get it from the forwarded proxy"
                + " -- fails if the write-side fix reverted to a naive getClass().getInterfaces()");
        assertEquals("nested-hop0: world", g.greet("world"));
    }
}
