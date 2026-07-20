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
package net.jini.jeri;

import au.net.zeus.jgdms.der.object.RawWireFormRetaining;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the "immutable-C" raw-wire-form retention on the JERI invocation handlers
 * ({@link BasicInvocationHandler} and subclasses implementing {@link RawWireFormRetaining}).
 *
 * <p>Same package as the classes under test so the {@code protected}
 * {@code createMarshalInputStream}/{@code setClientConstraints} seams are reachable.
 *
 * <p>Background: a DER {@code [8]} proxy that drops an interface at decode used to install a
 * decode-local wrapper handler on the narrowed proxy; the wrapped real handler's
 * {@code createMarshalInputStream} then failed its {@code Proxy.getInvocationHandler(proxy) != this}
 * self-check on the very first live call ({@code IllegalArgumentException: not proxy for this}),
 * because {@code getInvocationHandler} returned the wrapper, not the real handler. Immutable-C
 * eliminates the wrapper by having the real handler carry the retained bytes itself, so the
 * handler installed on the proxy IS the handler that runs the self-check.
 */
public class RawWireFormRetainingHandlerTest {

    // ---- fixtures ---------------------------------------------------------

    public interface Echo extends Remote {
        Object echo(Object o) throws Exception;
    }

    /** An {@code @AtomicSerial} ObjectEndpoint so a handler carrying it can be serialized. */
    @AtomicSerial
    public static final class SerializableEndpoint implements ObjectEndpoint {
        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm("id", int.class) };
        }
        public static void serialize(PutArg arg, SerializableEndpoint oe) throws IOException {
            arg.put("id", oe.id);
            arg.writeArgs();
        }
        private final int id;
        public SerializableEndpoint(int id) { this.id = id; }
        public SerializableEndpoint(GetArg arg) throws IOException, ClassNotFoundException {
            this.id = arg.get("id", 0);
        }
        @Override
        public OutboundRequestIterator newCall(InvocationConstraints c) {
            throw new UnsupportedOperationException("test stub");
        }
        @Override
        public java.rmi.RemoteException executeCall(OutboundRequest call) {
            throw new UnsupportedOperationException("test stub");
        }
        @Override public boolean equals(Object o) {
            return o instanceof SerializableEndpoint se && se.id == id;
        }
        @Override public int hashCode() { return id; }
    }

    /** Minimal OutboundRequest: an empty response stream is enough to prove we passed the self-check. */
    private static final class FakeOutboundRequest implements OutboundRequest {
        @Override public OutputStream getRequestOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getResponseInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public void populateContext(Collection ctx) {}
        @Override public InvocationConstraints getUnfulfilledConstraints() { return InvocationConstraints.EMPTY; }
        @Override public boolean getDeliveryStatus() { return false; }
        @Override public void abort() {}
    }

    private static final Class<?>[] IFACES =
            { Echo.class, RemoteMethodControl.class, TrustEquivalence.class };

    private static Object proxyFor(InvocationHandler h) {
        return Proxy.newProxyInstance(
                RawWireFormRetainingHandlerTest.class.getClassLoader(), IFACES, h);
    }

    private static Method echoMethod() throws NoSuchMethodException {
        return Echo.class.getMethod("echo", Object.class);
    }

    // ---- 1. withRawForm preserves concrete type + rawForm round-trips ------

    @Test
    public void withRawForm_preservesConcreteType_andRoundTripsBytes() {
        SerializableEndpoint oe = new SerializableEndpoint(1);
        byte[] raw = { 10, 20, 30 };

        BasicInvocationHandler basic = new BasicInvocationHandler(oe, null);
        InvocationHandler basicR = basic.withRawForm(raw);
        Assert.assertSame("base handler stays BasicInvocationHandler",
                BasicInvocationHandler.class, basicR.getClass());

        AtomicInvocationHandler atomic = new AtomicInvocationHandler(oe, null, true, Compression.DEFLATE);
        InvocationHandler atomicR = atomic.withRawForm(raw);
        Assert.assertSame("AtomicInvocationHandler must NOT downgrade to Basic",
                AtomicInvocationHandler.class, atomicR.getClass());

        AtomicDerInvocationHandler der = new AtomicDerInvocationHandler(oe, null);
        InvocationHandler derR = der.withRawForm(raw);
        Assert.assertSame("AtomicDerInvocationHandler must NOT downgrade to Basic",
                AtomicDerInvocationHandler.class, derR.getClass());

        for (InvocationHandler r : new InvocationHandler[]{ basicR, atomicR, derR }) {
            Assert.assertTrue(r instanceof RawWireFormRetaining);
            Assert.assertArrayEquals("rawForm must round-trip",
                    raw, ((RawWireFormRetaining) r).rawForm());
        }
    }

    @Test
    public void rawForm_isDefensivelyCopied_bothDirections() {
        SerializableEndpoint oe = new SerializableEndpoint(1);
        byte[] raw = { 1, 2, 3 };
        RawWireFormRetaining r = (RawWireFormRetaining) new AtomicInvocationHandler(oe, null, false).withRawForm(raw);
        raw[0] = 99; // mutate caller's array
        Assert.assertArrayEquals("input array must be defensively copied",
                new byte[]{ 1, 2, 3 }, r.rawForm());
        r.rawForm()[0] = 77; // mutate returned array
        Assert.assertArrayEquals("returned array must be defensively copied",
                new byte[]{ 1, 2, 3 }, r.rawForm());
    }

    @Test
    public void nonRetaining_handler_hasNullRawForm() {
        SerializableEndpoint oe = new SerializableEndpoint(1);
        Assert.assertNull(new BasicInvocationHandler(oe, null).rawForm());
        Assert.assertNull(new AtomicInvocationHandler(oe, null, false).rawForm());
        Assert.assertNull(new AtomicDerInvocationHandler(oe, null).rawForm());
    }

    // ---- 2. REGRESSION: self-check passes for a proxy carrying its own retaining handler ----

    @Test
    public void selfCheck_passesForProxyCarryingRetainingHandler() throws Exception {
        AtomicInvocationHandler h = new AtomicInvocationHandler(
                new SerializableEndpoint(1), null, false);
        AtomicInvocationHandler retaining = (AtomicInvocationHandler) h.withRawForm(new byte[]{ 5 });
        Object proxy = proxyFor(retaining);
        // The KEY property immutable-C restores: the handler installed on the proxy IS this handler.
        Assert.assertSame(retaining, Proxy.getInvocationHandler(proxy));

        try {
            retaining.createMarshalInputStream(
                    proxy, echoMethod(), new FakeOutboundRequest(), false, Collections.emptyList());
            // If it returns, it clearly got past the self-check.
        } catch (IllegalArgumentException e) {
            Assert.fail("self-check wrongly rejected a proxy carrying its own retaining handler: "
                    + e.getMessage());
        } catch (Exception expectedFromEmptyStream) {
            // Reaching the response-stream read proves the self-check was passed; an empty stream
            // failing to decode a header here is fine and expected.
        }
    }

    // ---- 3. SECURITY: self-check STILL rejects a genuinely-wrong proxy ----

    @Test
    public void selfCheck_stillRejectsWrongProxy() throws Exception {
        SerializableEndpoint oe = new SerializableEndpoint(1);
        AtomicInvocationHandler h1 = (AtomicInvocationHandler)
                new AtomicInvocationHandler(oe, null, false).withRawForm(new byte[]{ 1 });
        AtomicInvocationHandler h2 = (AtomicInvocationHandler)
                new AtomicInvocationHandler(oe, null, false).withRawForm(new byte[]{ 2 });
        Object proxy2 = proxyFor(h2); // proxy2's handler is h2, NOT h1

        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () ->
                h1.createMarshalInputStream(
                        proxy2, echoMethod(), new FakeOutboundRequest(), false, Collections.emptyList()));
        Assert.assertEquals("not proxy for this", e.getMessage());
    }

    // ---- 4. Object methods behave as a plain handler (rawForm not wire-visible) ----

    @Test
    public void objectMethods_areNativeToTheHandler_rawFormIgnored() {
        SerializableEndpoint oe = new SerializableEndpoint(1);
        AtomicInvocationHandler plain = new AtomicInvocationHandler(oe, null, false, Compression.NONE);
        AtomicInvocationHandler retaining =
                (AtomicInvocationHandler) plain.withRawForm(new byte[]{ 7, 7, 7 });

        Assert.assertEquals("retaining must equal an otherwise-identical non-retaining handler",
                plain, retaining);
        Assert.assertEquals("and the reverse", retaining, plain);
        Assert.assertEquals("hashCode must not depend on rawForm",
                plain.hashCode(), retaining.hashCode());
    }

    // ---- 5. setConstraints on the narrowed proxy passes the self-check ----

    @Test
    public void setConstraints_onNarrowedProxy_passesSelfCheck() {
        AtomicInvocationHandler retaining = (AtomicInvocationHandler)
                new AtomicInvocationHandler(new SerializableEndpoint(1), null, false)
                        .withRawForm(new byte[]{ 9 });
        Object proxy = proxyFor(retaining);
        MethodConstraints mc = new BasicMethodConstraints(InvocationConstraints.EMPTY);

        Object newProxy = ((RemoteMethodControl) proxy).setConstraints(mc);
        Assert.assertNotNull(newProxy);
        Assert.assertTrue(Proxy.isProxyClass(newProxy.getClass()));
        // The constraint-changed handler is a distinct proxy and no longer the verbatim decoded
        // object, so it carries no retained wire form.
        Assert.assertNull(((RawWireFormRetaining) Proxy.getInvocationHandler(newProxy)).rawForm());
    }

    // ---- 6. Wire-neutrality: a retaining handler serializes identically to a non-retaining one ----

    @Test
    public void wireNeutrality_retainingSerializesIdenticalToNonRetaining() throws Exception {
        SerializableEndpoint oe = new SerializableEndpoint(42);
        AtomicInvocationHandler plain = new AtomicInvocationHandler(oe, null, true, Compression.DEFLATE);
        AtomicInvocationHandler retaining =
                (AtomicInvocationHandler) plain.withRawForm(new byte[]{ 1, 2, 3, 4, 5 });

        byte[] plainBytes = atomicSerialize(plain);
        byte[] retainingBytes = atomicSerialize(retaining);

        Assert.assertTrue("retaining handler must serialize to non-empty bytes", retainingBytes.length > 0);
        Assert.assertArrayEquals(
                "a retaining handler must serialize byte-identically to an otherwise-equal "
                + "non-retaining one (rawForm is transient and absent from the serial form)",
                plainBytes, retainingBytes);
    }

    private static byte[] atomicSerialize(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (AtomicMarshalOutputStream out = new AtomicMarshalOutputStream(
                baos, RawWireFormRetainingHandlerTest.class.getClassLoader(),
                Collections.emptyList(), false)) {
            out.writeObject(o);
            out.flush();
        }
        return baos.toByteArray();
    }
}
