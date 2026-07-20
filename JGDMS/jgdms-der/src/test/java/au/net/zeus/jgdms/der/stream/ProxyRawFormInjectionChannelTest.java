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

package au.net.zeus.jgdms.der.stream;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.object.RawWireFormRetaining;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security tests for the {@code [8]} proxy raw-wire-form INJECTION CHANNEL (the mechanism that
 * replaced the copy-constructor design): a narrowed {@code [8]}-decoded proxy's handler captures the
 * original wire bytes at deserialization time from
 * {@link AtomicSerial.GetArg#getInjected(String)}, NOT from any wire field.
 *
 * <ul>
 *   <li><b>DC-1 (WIRE-DISJOINT).</b> An attacker who transmits a real wire field named identically
 *       to the injection key cannot influence what the handler retains -- the wire field lands in
 *       the positional field store ({@code arg.get}) and is ignored; the retained form comes only
 *       from the trusted-decoder injected map ({@code arg.getInjected}).</li>
 *   <li><b>DC-3 (SCOPED).</b> The injected value reaches the target handler's TOP-level GetArg only;
 *       a nested {@code @AtomicSerial} field decoded within the handler sees an EMPTY injected map.</li>
 * </ul>
 */
class ProxyRawFormInjectionChannelTest {

    public interface A { String a(); }
    public interface B { String b(); }

    // ---- DC-1 fixture: a handler with a WIRE field literally named "rawForm" ----------------

    /**
     * A handler whose {@code @AtomicSerial} wire form declares a field literally named
     * {@code "rawForm"} (attacker-controllable). Its {@link RawWireFormRetaining#rawForm()} returns
     * the DESERIALIZATION-INJECTED bytes ({@code retainedForm}), NEVER the wire {@code "rawForm"}
     * field -- exactly the disjointness DC-1 requires. The wire field is separately exposed via
     * {@link #wireField()} to prove it WAS decoded (present on the wire) yet inert.
     */
    @AtomicSerial
    public static final class WireDecoyHandler implements InvocationHandler, RawWireFormRetaining {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[]{ new AtomicSerial.SerialForm("rawForm", byte[].class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, WireDecoyHandler h) throws IOException {
            arg.put("rawForm", h.wireRawForm); // the WIRE decoy bytes
            arg.writeArgs();
        }
        private final byte[] wireRawForm;             // WIRE field "rawForm" (attacker bytes)
        private final transient byte[] retainedForm;  // decode-injected [8] bytes
        public WireDecoyHandler(byte[] wireRawForm) { this(wireRawForm, null); }
        private WireDecoyHandler(byte[] wireRawForm, byte[] retainedForm) {
            this.wireRawForm = wireRawForm == null ? null : wireRawForm.clone();
            this.retainedForm = retainedForm == null ? null : retainedForm.clone();
        }
        public WireDecoyHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this((byte[]) arg.get("rawForm", null),
                 arg.getInjected(RawWireFormRetaining.RAW_FORM_KEY) instanceof byte[] b ? b : null);
        }
        /** DC-1: retained form is the INJECTED value, never the wire "rawForm" field. */
        @Override public byte[] rawForm() { return retainedForm == null ? null : retainedForm.clone(); }
        public byte[] wireField() { return wireRawForm == null ? null : wireRawForm.clone(); }
        @Override public Object invoke(Object proxy, Method m, Object[] args) {
            switch (m.getName()) {
                case "a": case "b": return "ok";
                case "toString": return "WireDecoyProxy";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == (args == null ? null : args[0]);
                default: return null;
            }
        }
    }

    // ---- DC-3 fixtures: a handler with a nested @AtomicSerial probe -------------------------

    /** A nested {@code @AtomicSerial} value that records whether IT saw an injected value. */
    @AtomicSerial
    public static final class NestedProbe {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[]{ new AtomicSerial.SerialForm("tag", String.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, NestedProbe p) throws IOException {
            arg.put("tag", p.tag);
            arg.writeArgs();
        }
        private final String tag;
        private final transient boolean sawInjection;
        public NestedProbe(String tag) { this.tag = tag; this.sawInjection = false; }
        public NestedProbe(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.tag = (String) arg.get("tag", null);
            // DC-3: a nested frame's GetArg must carry an EMPTY injected map.
            this.sawInjection = arg.getInjected(RawWireFormRetaining.RAW_FORM_KEY) != null;
        }
        public boolean sawInjection() { return sawInjection; }
        public String tag() { return tag; }
    }

    /** A handler whose only wire field is a nested {@code @AtomicSerial} {@link NestedProbe}. */
    @AtomicSerial
    public static final class ProbeHandler implements InvocationHandler, RawWireFormRetaining {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[]{ new AtomicSerial.SerialForm("probe", NestedProbe.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, ProbeHandler h) throws IOException {
            arg.put("probe", h.probe);
            arg.writeArgs();
        }
        private final NestedProbe probe;
        private final transient byte[] retainedForm;
        public ProbeHandler(NestedProbe probe) { this(probe, null); }
        private ProbeHandler(NestedProbe probe, byte[] retainedForm) {
            this.probe = probe;
            this.retainedForm = retainedForm == null ? null : retainedForm.clone();
        }
        public ProbeHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this((NestedProbe) arg.get("probe", null),
                 arg.getInjected(RawWireFormRetaining.RAW_FORM_KEY) instanceof byte[] b ? b : null);
        }
        @Override public byte[] rawForm() { return retainedForm == null ? null : retainedForm.clone(); }
        public NestedProbe probe() { return probe; }
        @Override public Object invoke(Object proxy, Method m, Object[] args) {
            switch (m.getName()) {
                case "a": case "b": return "ok";
                case "toString": return "ProbeProxy";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == (args == null ? null : args[0]);
                default: return null;
            }
        }
    }

    // ---- harness (top-level [8] via DerObjectStreamCodec, selective narrowing) --------------

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
                    throw new ClassNotFoundException(name + " deliberately unresolvable");
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    private static byte[] write(Object o) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject(o);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    private static Object read(byte[] b, ClassLoader loader) throws IOException, ClassNotFoundException {
        Thread t = Thread.currentThread();
        ClassLoader prev = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            DerObjectStreamCodec c = new DerObjectStreamCodec();
            c.initReader(b, null, ResolutionContext.of(loader));
            return c.readObject();
        } finally {
            t.setContextClassLoader(prev);
        }
    }

    /** Parses interface names out of the [8] TLV CONTENT bytes (the shape rawForm() returns). */
    private static String[] ifaceNamesFromContent(byte[] content) throws DerException {
        DerReader pr = new DerReader(content);
        int count = pr.readInteger().intValueExact();
        String[] names = new String[count];
        for (int i = 0; i < count; i++) names[i] = pr.readUtf8String();
        return names;
    }

    private static Proxy proxyOver(Class<?>[] ifaces, InvocationHandler h) {
        return (Proxy) Proxy.newProxyInstance(ProxyRawFormInjectionChannelTest.class.getClassLoader(), ifaces, h);
    }

    // =====================================================================================
    // DC-1: a wire field named "rawForm" is IGNORED; only the injected [8] bytes are retained.
    // =====================================================================================

    @Test
    void dc1_attackerWireRawFormField_isIgnored_injectedBytesRetained() throws Exception {
        byte[] attackerBytes = { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF };
        Proxy p = proxyOver(new Class<?>[]{ A.class, B.class }, new WireDecoyHandler(attackerBytes));
        byte[] wire = write(p);

        // Decode with B unresolvable -> narrowing -> the decoder injects the FULL [8] content bytes.
        Object back = read(wire, new SelectivelyBlockingLoader(getClass().getClassLoader(), "B"));
        assertTrue(back instanceof A);
        assertFalse(back instanceof B, "B must be dropped (narrowing must occur to trigger injection)");

        InvocationHandler h = Proxy.getInvocationHandler(back);
        assertTrue(h instanceof WireDecoyHandler);
        WireDecoyHandler wdh = (WireDecoyHandler) h;

        // KEY DC-1 ASSERTION: the retained form is the trusted-injected [8] content, NOT the
        // attacker's wire "rawForm" field. It parses as the FULL original interface list {A,B}.
        byte[] retained = wdh.rawForm();
        assertNotNull(retained, "narrowing must inject the [8] bytes into the handler");
        assertFalse(Arrays.equals(attackerBytes, retained),
                "the retained form must NEVER be the attacker's wire 'rawForm' field bytes");
        assertArrayEquals(new String[]{ A.class.getName(), B.class.getName() },
                ifaceNamesFromContent(retained),
                "retained form is the injected [8] content (interface list), not the wire field");

        // Corroboration: the attacker wire field WAS on the wire and decoded into the field store
        // (proving the collision is real) -- it is simply inert, read by arg.get, never as rawForm.
        assertArrayEquals(attackerBytes, wdh.wireField(),
                "the wire 'rawForm' field is decoded normally (into the field store) but ignored for retention");
    }

    @Test
    void dc1_noNarrowing_wireRawFormField_stillIgnored_nothingRetained() throws Exception {
        byte[] attackerBytes = { 1, 2, 3 };
        Proxy p = proxyOver(new Class<?>[]{ A.class, B.class }, new WireDecoyHandler(attackerBytes));
        byte[] wire = write(p);

        // Nothing dropped -> no injection at all. The wire "rawForm" field must NOT leak into rawForm().
        Object back = read(wire, getClass().getClassLoader());
        WireDecoyHandler wdh = (WireDecoyHandler) Proxy.getInvocationHandler(back);
        assertNull(wdh.rawForm(),
                "with no injection, a wire 'rawForm' field must never become the retained form");
        assertArrayEquals(attackerBytes, wdh.wireField(), "the wire field still round-trips as an ordinary field");
    }

    // =====================================================================================
    // DC-3: the injected value reaches the handler's TOP frame only, never a nested frame.
    // =====================================================================================

    @Test
    void dc3_injectedValue_doesNotPropagateIntoNestedFrame() throws Exception {
        Proxy p = proxyOver(new Class<?>[]{ A.class, B.class }, new ProbeHandler(new NestedProbe("n")));
        byte[] wire = write(p);

        Object back = read(wire, new SelectivelyBlockingLoader(getClass().getClassLoader(), "B"));
        assertFalse(back instanceof B, "B must be dropped (narrowing must occur to trigger injection)");
        ProbeHandler h = (ProbeHandler) Proxy.getInvocationHandler(back);

        // Top frame DID see the injection (retained the [8] bytes).
        assertNotNull(h.rawForm(), "the handler's TOP-level GetArg must receive the injected [8] bytes");
        assertArrayEquals(new String[]{ A.class.getName(), B.class.getName() }, ifaceNamesFromContent(h.rawForm()));

        // DC-3: the nested @AtomicSerial field's own GetArg must NOT have seen the injected value.
        assertNotNull(h.probe());
        assertEquals("n", h.probe().tag());
        assertFalse(h.probe().sawInjection(),
                "DC-3: a nested @AtomicSerial decode frame MUST get an EMPTY injected map -- the "
                + "injected value must not propagate out of the handler's top-level frame");
    }
}
