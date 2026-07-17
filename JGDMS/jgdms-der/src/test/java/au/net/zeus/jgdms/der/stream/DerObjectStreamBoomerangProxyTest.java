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
import au.net.zeus.jgdms.der.object.BoomerangProxyHandler;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tolerant per-name resolution and {@link BoomerangProxyHandler} wrap/re-forward behaviour for the
 * top-level object-stream bare {@code [8]} {@code java.lang.reflect.Proxy} item (STD-008
 * sec.15.2), the {@code DerObjectStreamCodec.readObject}/{@code writeObject} half of the fix.
 *
 * <p>Uses the same "endpoint {@code ClassLoader} overrides {@code loadClass(name, resolve)}"
 * technique as {@code DerProxyMarshalledInstanceTest.resolvesAgainstEndpointLoaderNotThreadContext},
 * but selectively throwing {@link ClassNotFoundException} for a chosen blacklist of interface
 * names (delegating everything else to the real classloader) so individual interfaces can be made
 * "unresolvable" at a given hop while the rest resolve normally.
 *
 * <p>In package {@code au.net.zeus.jgdms.der.stream} to reach the package-private
 * {@link DerObjectStreamCodec}.
 */
class DerObjectStreamBoomerangProxyTest {

    interface Greeter { String greet(); }
    interface Marker  { String mark(); }

    /** A minimal @AtomicSerial InvocationHandler whose answer must survive the round-trip. */
    @AtomicSerial
    public static final class AnswerHandler implements InvocationHandler {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[]{ new AtomicSerial.SerialForm("answer", String.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, AnswerHandler h) throws IOException {
            arg.put("answer", h.answer);
            arg.writeArgs();
        }
        private final String answer;
        public AnswerHandler(String answer) { this.answer = answer; }
        public AnswerHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.answer = (String) arg.get("answer", null);
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "greet": case "mark": case "extra": return answer;
                case "toString": return "Proxy[" + answer + "]";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals":   return proxy == (args == null ? null : args[0]);
                default:         return null;
            }
        }
    }

    /**
     * A {@code ClassLoader} that throws {@link ClassNotFoundException} for a chosen blacklist of
     * (simple-name-suffix-matched) class names and delegates everything else to the real parent
     * loader -- so a specific interface can be made "locally unresolvable" at this hop while the
     * rest resolve normally, without disturbing actual class identity for the ones that do.
     */
    static final class SelectivelyBlockingLoader extends ClassLoader {
        private final Set<String> blocked;
        final List<String> asked = new CopyOnWriteArrayList<>();

        SelectivelyBlockingLoader(ClassLoader parent, String... blockedSimpleNames) {
            super(parent);
            this.blocked = new HashSet<>(Arrays.asList(blockedSimpleNames));
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            asked.add(name);
            for (String b : blocked) {
                if (name.endsWith("$" + b) || name.endsWith("." + b) || name.equals(b)) {
                    throw new ClassNotFoundException(
                            "SelectivelyBlockingLoader: " + name + " is deliberately unresolvable");
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

    /**
     * Reads {@code b} with {@code loader} as the endpoint {@code ResolutionContext} loader.
     *
     * <p>Also swaps the calling thread's context class loader to {@code loader} for the duration
     * of the call: {@code PreferredClassProvider} (the registered {@code RMIClassLoaderSpi} in
     * this test environment) falls back to the thread context class loader when the
     * endpoint-assigned {@code defaultLoader} fails to resolve a name AND no
     * {@code SecurityManager} is installed (true for a plain unit-test JVM) -- so a blacklisted
     * name must also be blocked via the context loader, or that fallback would silently resolve
     * it anyway and the test would not actually be exercising the "unresolvable at this hop" case.
     */
    private static Object read(byte[] b, ClassLoader loader) throws IOException, ClassNotFoundException {
        Thread thread = Thread.currentThread();
        ClassLoader prevTccl = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            DerObjectStreamCodec c = new DerObjectStreamCodec();
            c.initReader(b, null, ResolutionContext.of(loader));
            return c.readObject();
        } finally {
            thread.setContextClassLoader(prevTccl);
        }
    }

    /** Parses the [8] item's leading interface-name list directly off the wire (white-box check). */
    private static String[] rawInterfaceNames(byte[] wireBytes) throws DerException {
        DerReader r = new DerReader(wireBytes);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        assertEquals((byte) 0xA8, wireBytes[0], "must be the [8] proxy tag");
        byte[] content = r.readRawContent(hdr.contentLength());
        return rawInterfaceNamesFromContent(content);
    }

    /**
     * As {@link #rawInterfaceNames(byte[])}, but parses [8] TLV CONTENT bytes directly (no outer
     * tag+length header) -- the shape {@link BoomerangProxyHandler#originalWireBytes()} returns.
     */
    private static String[] rawInterfaceNamesFromContent(byte[] content) throws DerException {
        DerReader pr = new DerReader(content);
        int count = pr.readInteger().intValueExact();
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = pr.readUtf8String();
        }
        return names;
    }

    private Proxy newProxy(Class<?>[] ifaces, InvocationHandler h) {
        return (Proxy) Proxy.newProxyInstance(getClass().getClassLoader(), ifaces, h);
    }

    // Captures WARNING log records from ProxyWireSupport's logger for the duration of a test.
    private final List<LogRecord> captured = new CopyOnWriteArrayList<>();
    private Handler captureHandler;
    private Logger proxyLogger;

    private void startCapturingLogs() {
        proxyLogger = Logger.getLogger("au.net.zeus.jgdms.der.object");
        captureHandler = new Handler() {
            @Override public void publish(LogRecord record) { captured.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        proxyLogger.addHandler(captureHandler);
        proxyLogger.setLevel(Level.ALL);
    }

    @AfterEach
    void removeLogCapture() {
        if (proxyLogger != null && captureHandler != null) {
            proxyLogger.removeHandler(captureHandler);
        }
    }

    /** Single interface missing, rest resolve: proxy builds over the resolved subset, wrapped, and the drop is logged. */
    @Test
    void singleInterfaceMissing_buildsOverResolvedSubset_wrappedAndLogged() throws Exception {
        startCapturingLogs();
        Proxy g = newProxy(new Class<?>[]{ Greeter.class, Marker.class }, new AnswerHandler("hi"));
        byte[] bytes = write(g);

        SelectivelyBlockingLoader blocking = new SelectivelyBlockingLoader(getClass().getClassLoader(), "Marker");
        Object back = read(bytes, blocking);

        assertNotNull(back);
        assertTrue(Proxy.isProxyClass(back.getClass()));
        assertTrue(back instanceof Greeter, "the resolvable interface must be present");
        assertFalse(back instanceof Marker, "the unresolvable interface must be dropped, not fail the whole item");
        assertEquals("hi", ((Greeter) back).greet(), "the real handler must still work for the resolved interface");

        InvocationHandler h = Proxy.getInvocationHandler(back);
        assertTrue(h instanceof BoomerangProxyHandler, "a drop must wrap the handler");
        BoomerangProxyHandler bph = (BoomerangProxyHandler) h;
        assertArrayEquals(
                new String[]{ Greeter.class.getName(), Marker.class.getName() },
                rawInterfaceNamesFromContent(bph.originalWireBytes()),
                "the wrapper must retain the FULL original wire-declared interface list, byte-for-byte");

        boolean logged = captured.stream().anyMatch(r ->
                r.getLevel() == Level.WARNING
                && r.getParameters() != null
                && r.getParameters().length > 0
                && Marker.class.getName().equals(r.getParameters()[0]));
        assertTrue(logged, "the dropped interface must be logged (name + reason), not silently dropped");
    }

    /** All interfaces missing: must still fail, not silently build an empty/degenerate proxy. */
    @Test
    void allInterfacesMissing_stillFails() throws Exception {
        Proxy g = newProxy(new Class<?>[]{ Greeter.class, Marker.class }, new AnswerHandler("hi"));
        byte[] bytes = write(g);

        SelectivelyBlockingLoader blockAll =
                new SelectivelyBlockingLoader(getClass().getClassLoader(), "Greeter", "Marker");
        assertThrows(ClassNotFoundException.class, () -> read(bytes, blockAll),
                "a proxy with ZERO resolvable interfaces must still fail fast, not silently build an empty proxy");
    }

    /** Nothing missing: zero behaviour change -- plain handler, no wrapper. */
    @Test
    void nothingMissing_noWrapper_plainHandler() throws Exception {
        Proxy g = newProxy(new Class<?>[]{ Greeter.class, Marker.class }, new AnswerHandler("hi"));
        byte[] bytes = write(g);

        Object back = read(bytes, getClass().getClassLoader());
        assertTrue(back instanceof Greeter && back instanceof Marker);
        InvocationHandler h = Proxy.getInvocationHandler(back);
        assertFalse(h instanceof BoomerangProxyHandler,
                "when nothing is dropped, the real handler must be used directly, unwrapped");
        assertTrue(h instanceof AnswerHandler, "the plain real handler class must round-trip unmodified");
    }

    /**
     * THE CORE LANDMINE-FIX TEST: two-hop forwarding. Hop 1 decodes with Marker missing (Greeter
     * resolves) -- this builds a BoomerangProxyHandler-wrapped proxy. Hop 1 then re-encodes
     * (forwards) that proxy. The re-encoded wire bytes must list the ORIGINAL full interface set
     * (Greeter AND Marker), not just the narrowed set, AND must be byte-identical to the original
     * sender's bytes (not merely structurally equivalent) -- this must fail if the write-side fix
     * (ProxyWireSupport.wireContentForBoomerang) is reverted to a naive
     * {@code obj.getClass().getInterfaces()} reflection or to re-deriving fresh bytes from parsed
     * state instead of relaying the retained bytes verbatim.
     */
    @Test
    void twoHopForward_reEncodesFullOriginalInterfaceSet_notNarrowedSet() throws Exception {
        Proxy original = newProxy(new Class<?>[]{ Greeter.class, Marker.class }, new AnswerHandler("hop0"));
        byte[] wireFromSender = write(original);

        // Hop 1 cannot resolve Marker.
        SelectivelyBlockingLoader hop1Loader = new SelectivelyBlockingLoader(getClass().getClassLoader(), "Marker");
        Object atHop1 = read(wireFromSender, hop1Loader);
        assertTrue(atHop1 instanceof Greeter);
        assertFalse(atHop1 instanceof Marker);
        assertTrue(Proxy.getInvocationHandler(atHop1) instanceof BoomerangProxyHandler,
                "hop1 must have built a BoomerangProxyHandler-wrapped (narrowed) proxy");

        // Hop 1 forwards (re-encodes) the narrowed proxy it holds.
        byte[] wireForwardedByHop1 = write(atHop1);

        // White-box: the re-encoded wire item's interface-name list must be the FULL original
        // pair, in original order -- NOT narrowed to just Greeter. This is a structural regression
        // check: a reverted write-side fix would re-derive names from
        // atHop1.getClass().getInterfaces(), which is Greeter-only, and this assertion would fail.
        assertArrayEquals(new String[]{ Greeter.class.getName(), Marker.class.getName() },
                rawInterfaceNames(wireForwardedByHop1),
                "forwarded wire item must carry the FULL original interface list, not the narrowed runtime set");

        // THE ACTUAL POINT OF THIS FIX: the forwarded wire bytes must be byte-identical to the
        // original sender's bytes, not merely structurally/semantically equivalent -- a freshly
        // re-derived encoding (even one listing the same names in the same order) would be this
        // node's OWN new encoding, forfeiting the sender's @AtomicSerial-validated integrity
        // guarantee that byte-for-byte relay preserves. See BoomerangProxyHandler.
        assertArrayEquals(wireFromSender, wireForwardedByHop1,
                "forwarded wire bytes must be byte-identical to the original sender's bytes, not a fresh re-encoding");

        // Black-box corroboration: a downstream hop that CAN resolve both must get a proxy
        // implementing BOTH interfaces from the forwarded bytes -- impossible if hop1's forward
        // had narrowed the wire list to Greeter-only.
        Object atDownstream = read(wireForwardedByHop1, getClass().getClassLoader());
        assertTrue(atDownstream instanceof Greeter);
        assertTrue(atDownstream instanceof Marker,
                "a downstream hop that CAN resolve Marker must still get it from the forwarded proxy");
        assertEquals("hop0", ((Marker) atDownstream).mark(), "the real handler's state must have round-tripped intact");
    }
}
