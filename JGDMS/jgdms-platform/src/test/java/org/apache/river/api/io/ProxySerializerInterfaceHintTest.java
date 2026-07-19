/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.io;

import java.io.InvalidObjectException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Adversarial tests for T3: the {@code proxyInterfaces} interface-name hint on
 * {@link ProxySerializer}.
 *
 * <p>The hint is carried on the wire as a {@code String[]} of binary class
 * names so that an isolated reading process can build a thin {@link Proxy} stub
 * without unmarshalling or classloading the proxy's real implementation. These
 * tests prove the field:
 * <ul>
 *   <li>captures the FULL sender-side interface closure, inherited super-interfaces included;</li>
 *   <li>round-trips the exact name set through the {@code @AtomicSerial} decode constructor;</li>
 *   <li>is DECODE-BOUNDED: an oversized count or an oversized individual name is rejected;</li>
 *   <li>NEVER resolves a name to a Class as a side effect of decode (no {@code Class.forName});</li>
 *   <li>decodes an absent field cleanly as the empty "no advance hint" array (backward compat).</li>
 * </ul>
 */
public class ProxySerializerInterfaceHintTest {

    // --- test interface hierarchy (for the inherited-closure test) --------

    public interface Base {}
    public interface Mid extends Base {}
    public interface Leaf extends Mid {}

    private static final InvocationHandler NULL_HANDLER = new InvocationHandler() {
        public Object invoke(Object p, Method m, Object[] a) { return null; }
    };

    /**
     * A minimal {@link GetArg} that feeds the {@link ProxySerializer} decode
     * constructor fixed field values -- mirrors the fake-GetArg idiom in
     * {@code GetArgIdempotencyTest}. {@code serialClasses()} names
     * {@link ProxySerializer} so the base's caller resolution resolves the
     * decode constructor's frame.
     */
    private static final class FakeGetArg extends GetArg {
        private final CodebaseAccessor bootstrap;
        private final MarshalledInstance serviceProxy;
        private final Object proxyInterfaces; // String[] or ABSENT

        FakeGetArg(CodebaseAccessor bootstrap, MarshalledInstance serviceProxy,
                   Object proxyInterfaces) {
            super(); // no-op since the 4.0.0 guard drop
            this.bootstrap = bootstrap;
            this.serviceProxy = serviceProxy;
            this.proxyInterfaces = proxyInterfaces;
        }

        @Override
        protected Object lookup(Class<?> callerClass, String name) {
            if ("bootstrapProxy".equals(name)) return bootstrap;
            if ("serviceProxy".equals(name)) return serviceProxy;
            if ("proxyInterfaces".equals(name)) return proxyInterfaces;
            return ABSENT;
        }

        @Override
        protected boolean isDefaulted(Class<?> callerClass, String name) {
            return lookup(callerClass, name) == ABSENT;
        }

        @Override
        public Class[] serialClasses() {
            return new Class[]{ ProxySerializer.class };
        }

        @Override
        public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }

    private static CodebaseAccessor newBootstrap() {
        return (CodebaseAccessor) Proxy.newProxyInstance(
                ProxySerializerInterfaceHintTest.class.getClassLoader(),
                new Class[]{ CodebaseAccessor.class, RemoteMethodControl.class },
                NULL_HANDLER);
    }

    private static MarshalledInstance dummyServiceProxy() throws Exception {
        // Eagerly-marshalled placeholder; content is irrelevant, only its type
        // (MarshalledInstance) matters to the decode constructor.
        return new AtomicMarshalledInstance("placeholder");
    }

    private static ProxySerializer decode(Object proxyInterfaces) throws Exception {
        return new ProxySerializer(
                new FakeGetArg(newBootstrap(), dummyServiceProxy(), proxyInterfaces));
    }

    private static Set<String> setOf(String[] a) {
        return new HashSet<String>(Arrays.asList(a));
    }

    // --- requirement #2: full closure incl. inherited interfaces ----------

    @Test
    public void closureIncludesInheritedSuperInterfaces() {
        Object proxy = Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{ Leaf.class }, NULL_HANDLER);
        Set<String> names = setOf(ProxySerializer.interfaceClosureNames(proxy.getClass()));
        assertTrue("directly-declared Leaf must be present", names.contains(Leaf.class.getName()));
        assertTrue("inherited Mid must be present", names.contains(Mid.class.getName()));
        assertTrue("inherited Base must be present", names.contains(Base.class.getName()));
    }

    @Test
    public void closureOfNullOrNoInterfacesIsEmpty() {
        assertEquals(0, ProxySerializer.interfaceClosureNames(null).length);
        assertEquals(0, ProxySerializer.interfaceClosureNames(Object.class).length);
    }

    // --- requirement: faithful round-trip through decode ------------------

    @Test
    public void decodeRoundTripsExactNameSet() throws Exception {
        String[] names = { "com.example.Foo", "com.example.Bar", "com.example.Baz" };
        ProxySerializer ps = decode(names.clone());
        assertEquals("decode must return exactly the names given, no more no less",
                setOf(names), setOf(ps.proxyInterfaces()));
        assertEquals(names.length, ps.proxyInterfaces().length);
    }

    @Test
    public void proxyInterfacesAccessorReturnsDefensiveCopy() throws Exception {
        String[] names = { "com.example.Foo" };
        ProxySerializer ps = decode(names.clone());
        String[] first = ps.proxyInterfaces();
        first[0] = "mutated";
        assertEquals("accessor must not expose internal array to mutation",
                "com.example.Foo", ps.proxyInterfaces()[0]);
    }

    // --- requirement #6: absent field decodes as empty --------------------

    @Test
    public void absentFieldDecodesToEmptyHint() throws Exception {
        ProxySerializer ps = decode(GetArg.ABSENT);
        assertEquals("an absent (older-stream) field must decode as no-hint",
                0, ps.proxyInterfaces().length);
    }

    @Test
    public void nullAndEmptyFieldDecodeToEmptyHint() throws Exception {
        assertEquals(0, decode(null).proxyInterfaces().length);
        assertEquals(0, decode(new String[0]).proxyInterfaces().length);
    }

    // --- requirement #4: decode bounds actually reject --------------------

    @Test
    public void decodeRejectsOversizeCount() throws Exception {
        String[] tooMany = new String[65]; // cap is 64
        Arrays.fill(tooMany, "com.example.I");
        try {
            decode(tooMany);
            fail("decode must reject a name list beyond the count cap");
        } catch (InvalidObjectException expected) {
            assertTrue(expected.getMessage().contains("64"));
        }
    }

    @Test
    public void decodeAcceptsExactlyTheCount() throws Exception {
        String[] atCap = new String[64];
        for (int i = 0; i < atCap.length; i++) atCap[i] = "com.example.I" + i;
        assertEquals("exactly the cap must be accepted", 64, decode(atCap).proxyInterfaces().length);
    }

    @Test
    public void decodeRejectsOversizeName() throws Exception {
        char[] big = new char[1025]; // cap is 1024
        Arrays.fill(big, 'x');
        String[] names = { "com.example.Ok", new String(big) };
        try {
            decode(names);
            fail("decode must reject an individual name beyond the length cap");
        } catch (InvalidObjectException expected) {
            assertTrue(expected.getMessage().contains("1024"));
        }
    }

    @Test
    public void decodeRejectsNullElement() throws Exception {
        String[] names = { "com.example.Ok", null };
        try {
            decode(names);
            fail("decode must reject a null name element");
        } catch (InvalidObjectException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("null"));
        }
    }

    // --- requirement #5/#7: THE resolution-boundary proof -----------------

    /**
     * The crux security test: decode must store the names verbatim WITHOUT ever
     * resolving them. The list mixes a name that WOULD load if resolved
     * ({@code java.lang.Runnable}) with one that CANNOT be resolved on any
     * classpath. If {@code ProxySerializer} decode called
     * {@code Class.forName}/{@code loadClass} on the contents, the unresolvable
     * name would raise {@link ClassNotFoundException}; the absence of any such
     * throw -- and the verbatim carry of BOTH names, including the bogus one --
     * proves the resolution boundary that makes a later privileged-interface
     * gate (against {@code SubProcessAdministrable}) meaningful.
     */
    @Test
    public void decodeNeverResolvesNamesToClasses() throws Exception {
        String resolvable = "java.lang.Runnable";
        String bogus = "no.such.pkg.$Definitely$Not$Loadable$Interface$T3";
        String privileged = "au.net.zeus.jgdms.subprocess.SubProcessAdministrable";
        String[] names = { resolvable, bogus, privileged };

        ProxySerializer ps = decode(names); // must NOT throw ClassNotFoundException

        Set<String> out = setOf(ps.proxyInterfaces());
        assertTrue("resolvable name carried verbatim", out.contains(resolvable));
        assertTrue("unresolvable name carried verbatim (proves no forName on decode)",
                out.contains(bogus));
        assertTrue("a privileged interface NAME is carried as-is; T3 does not filter or "
                + "resolve it -- that is a later consumer's gate", out.contains(privileged));
        assertEquals(3, ps.proxyInterfaces().length);
    }

    /**
     * Static guard against regression: assert that {@code ProxySerializer}'s
     * source never references a class-resolution API on the hint's contents.
     * Complements the behavioural test above (a diff-time check that the field's
     * handling stays a pure String carry).
     */
    @Test
    public void sourceHasNoClassResolutionOnHint() throws Exception {
        java.nio.file.Path src = java.nio.file.Paths.get(
                "src/main/java/org/apache/river/api/io/ProxySerializer.java");
        if (!java.nio.file.Files.exists(src)) return; // only runs from module dir
        String text = new String(java.nio.file.Files.readAllBytes(src),
                java.nio.charset.StandardCharsets.UTF_8);
        // Match the actual CALL syntax (trailing paren), so the javadoc that
        // documents this very boundary ("no Class.forName ...") is not a false hit.
        assertFalse("ProxySerializer must not call Class.forName(...)", text.contains("Class.forName("));
        assertFalse("ProxySerializer must not call loadClass(...)", text.contains(".loadClass("));
    }
}
