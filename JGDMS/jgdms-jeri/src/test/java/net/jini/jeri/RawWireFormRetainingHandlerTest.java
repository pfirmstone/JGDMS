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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
 * Tests for deserialization-time raw-wire-form retention on the JERI invocation handlers
 * ({@link BasicInvocationHandler} and subclasses implementing {@link RawWireFormRetaining}).
 *
 * <p>Same package as the classes under test so the {@code protected}
 * {@code createMarshalInputStream}/{@code setClientConstraints} seams are reachable.
 *
 * <p>Background: a DER {@code [8]} proxy that drops an interface at decode used to install a
 * decode-local wrapper handler on the narrowed proxy; the wrapped real handler's
 * {@code createMarshalInputStream} then failed its {@code Proxy.getInvocationHandler(proxy) != this}
 * self-check on the first live call. The current mechanism captures the retained bytes DURING the
 * handler's own deserialization -- the {@code (GetArg)} constructor reads them from the framework's
 * {@link GetArg#getInjected(String) injection channel} (under {@link RawWireFormRetaining#RAW_FORM_KEY})
 * -- so the handler installed on the narrowed proxy IS the real handler (no wrapper), and there is
 * no copy constructor and no {@code withRawForm}.
 *
 * <p>The bytes are injected only by trusted der decode code; these unit tests drive the same
 * {@code (GetArg)} construction path through a minimal {@link InjectingGetArg} that supplies both a
 * wire-field map (for {@code arg.get}) and a disjoint injected map (for {@code arg.getInjected}).
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

    // ---- a minimal GetArg with a disjoint wire-field map and injected map -------------------

    /**
     * A {@link GetArg} that backs {@code arg.get(name, ...)} with a flat {@code fields} map and
     * {@code arg.getInjected(name)} with a SEPARATE {@code injected} map -- exactly the disjoint
     * two-channel shape the real decoders provide. Field names across the handler hierarchy are
     * unique ({@code oe}, {@code clientConstraints}, {@code serverConstraints},
     * {@code useCodebaseAnnotations}, {@code compression}), so a flat lookup ignoring the resolved
     * caller class is sufficient here.
     */
    static final class InjectingGetArg extends GetArg {
        private final Map<String, Object> fields;
        private final Map<String, Object> injected;
        private final Class<?>[] classes;
        InjectingGetArg(Map<String, Object> fields, Map<String, Object> injected, Class<?>... classes) {
            super();
            this.fields = fields;
            this.injected = injected;
            this.classes = classes;
        }
        @Override protected Object lookup(Class<?> caller, String name) {
            return fields.containsKey(name) ? fields.get(name) : ABSENT;
        }
        @Override protected boolean isDefaulted(Class<?> caller, String name) {
            return !fields.containsKey(name);
        }
        @Override public Object getInjected(String name) { return injected.get(name); }
        @Override public Class[] serialClasses() { return classes.clone(); }
        @Override public Collection getObjectStreamContext() { return Collections.emptyList(); }
    }

    private static Map<String, Object> baseFields() {
        Map<String, Object> f = new HashMap<>();
        f.put("oe", new SerializableEndpoint(1));
        // clientConstraints / serverConstraints absent -> default null (valid unconstrained proxy).
        return f;
    }

    private static BasicInvocationHandler basicFrom(byte[] injectedRawForm)
            throws IOException, ClassNotFoundException {
        Map<String, Object> inj = injectedRawForm == null ? Collections.emptyMap()
                : Collections.singletonMap(RawWireFormRetaining.RAW_FORM_KEY, (Object) injectedRawForm);
        return new BasicInvocationHandler(
                new InjectingGetArg(baseFields(), inj, BasicInvocationHandler.class));
    }

    private static AtomicInvocationHandler atomicFrom(byte[] injectedRawForm)
            throws IOException, ClassNotFoundException {
        Map<String, Object> f = baseFields();
        f.put("useCodebaseAnnotations", Boolean.TRUE);
        f.put("compression", Compression.DEFLATE);
        Map<String, Object> inj = injectedRawForm == null ? Collections.emptyMap()
                : Collections.singletonMap(RawWireFormRetaining.RAW_FORM_KEY, (Object) injectedRawForm);
        return new AtomicInvocationHandler(new InjectingGetArg(
                f, inj, BasicInvocationHandler.class, AtomicInvocationHandler.class));
    }

    private static AtomicDerInvocationHandler derFrom(byte[] injectedRawForm)
            throws IOException, ClassNotFoundException {
        Map<String, Object> inj = injectedRawForm == null ? Collections.emptyMap()
                : Collections.singletonMap(RawWireFormRetaining.RAW_FORM_KEY, (Object) injectedRawForm);
        return new AtomicDerInvocationHandler(new InjectingGetArg(
                baseFields(), inj, BasicInvocationHandler.class, AtomicDerInvocationHandler.class));
    }

    // ---- 1. injected rawForm is captured, per concrete type -------------------------------

    @Test
    public void injectedRawForm_capturedBy_eachConcreteType() throws Exception {
        byte[] raw = { 10, 20, 30 };
        BasicInvocationHandler basic = basicFrom(raw);
        AtomicInvocationHandler atomic = atomicFrom(raw);
        AtomicDerInvocationHandler der = derFrom(raw);

        Assert.assertSame(BasicInvocationHandler.class, basic.getClass());
        Assert.assertSame(AtomicInvocationHandler.class, atomic.getClass());
        Assert.assertSame(AtomicDerInvocationHandler.class, der.getClass());

        for (RawWireFormRetaining r : new RawWireFormRetaining[]{ basic, atomic, der }) {
            Assert.assertArrayEquals("injected rawForm must be captured by the (GetArg) ctor",
                    raw, r.rawForm());
        }
    }

    @Test
    public void rawForm_isDefensivelyCopied_bothDirections() throws Exception {
        byte[] raw = { 1, 2, 3 };
        RawWireFormRetaining r = atomicFrom(raw);
        raw[0] = 99; // mutate caller's array AFTER construction
        Assert.assertArrayEquals("injected array must be defensively copied on capture",
                new byte[]{ 1, 2, 3 }, r.rawForm());
        r.rawForm()[0] = 77; // mutate the returned array
        Assert.assertArrayEquals("returned array must be defensively copied",
                new byte[]{ 1, 2, 3 }, r.rawForm());
    }

    @Test
    public void noInjection_handler_hasNullRawForm() throws Exception {
        Assert.assertNull(basicFrom(null).rawForm());
        Assert.assertNull(atomicFrom(null).rawForm());
        Assert.assertNull(derFrom(null).rawForm());
        // The plain (non-deserialization) constructors also carry no retained form.
        SerializableEndpoint oe = new SerializableEndpoint(1);
        Assert.assertNull(new BasicInvocationHandler(oe, null).rawForm());
        Assert.assertNull(new AtomicInvocationHandler(oe, null, false).rawForm());
        Assert.assertNull(new AtomicDerInvocationHandler(oe, null).rawForm());
    }

    // ---- DC-1 (WIRE-DISJOINT): a hostile "rawForm" WIRE field is ignored --------------------

    @Test
    public void dc1_wireRawFormField_isIgnored_onlyInjectedChannelCounts() throws Exception {
        // Attacker also transmits a wire field literally named "rawForm" carrying attacker bytes,
        // BUT the injected channel is empty. The handler reads getInjected(RAW_FORM_KEY), never
        // get("rawForm"), so the wire field lands in the field store and is IGNORED.
        Map<String, Object> hostileFields = baseFields();
        hostileFields.put("rawForm", new byte[]{ (byte) 0xDE, (byte) 0xAD });        // attacker wire bytes
        hostileFields.put(RawWireFormRetaining.RAW_FORM_KEY, new byte[]{ (byte) 0xBE }); // even keyed as the injection key
        BasicInvocationHandler h = new BasicInvocationHandler(
                new InjectingGetArg(hostileFields, Collections.emptyMap(), BasicInvocationHandler.class));
        Assert.assertNull("a wire-declared rawForm field must NEVER become the retained form",
                h.rawForm());

        // And with a genuine trusted injection, that value (not any wire field) is what is retained.
        BasicInvocationHandler h2 = new BasicInvocationHandler(new InjectingGetArg(
                hostileFields, Collections.singletonMap(RawWireFormRetaining.RAW_FORM_KEY,
                        (Object) new byte[]{ 7 }), BasicInvocationHandler.class));
        Assert.assertArrayEquals("only the injected value is retained, never a wire field",
                new byte[]{ 7 }, h2.rawForm());
    }

    // ---- 2. REGRESSION: self-check passes for a proxy carrying its own retaining handler ----

    @Test
    public void selfCheck_passesForProxyCarryingRetainingHandler() throws Exception {
        AtomicInvocationHandler retaining = atomicFrom(new byte[]{ 5 });
        Object proxy = proxyFor(retaining);
        // The KEY property: the handler installed on the proxy IS this handler (no wrapper).
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
        AtomicInvocationHandler h1 = atomicFrom(new byte[]{ 1 });
        AtomicInvocationHandler h2 = atomicFrom(new byte[]{ 2 });
        Object proxy2 = proxyFor(h2); // proxy2's handler is h2, NOT h1

        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () ->
                h1.createMarshalInputStream(
                        proxy2, echoMethod(), new FakeOutboundRequest(), false, Collections.emptyList()));
        Assert.assertEquals("not proxy for this", e.getMessage());
    }

    // ---- 4. Object methods behave as a plain handler (rawForm not wire-visible) ----

    @Test
    public void objectMethods_areNativeToTheHandler_rawFormIgnored() throws Exception {
        AtomicInvocationHandler plain = atomicFrom(null);
        AtomicInvocationHandler retaining = atomicFrom(new byte[]{ 7, 7, 7 });

        Assert.assertEquals("retaining must equal an otherwise-identical non-retaining handler",
                plain, retaining);
        Assert.assertEquals("and the reverse", retaining, plain);
        Assert.assertEquals("hashCode must not depend on rawForm",
                plain.hashCode(), retaining.hashCode());
    }

    // ---- 5. setConstraints on the narrowed proxy passes the self-check and drops rawForm ----

    @Test
    public void setConstraints_onNarrowedProxy_passesSelfCheck_andDropsRawForm() throws Exception {
        AtomicInvocationHandler retaining = atomicFrom(new byte[]{ 9 });
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
        AtomicInvocationHandler plain = atomicFrom(null);
        AtomicInvocationHandler retaining = atomicFrom(new byte[]{ 1, 2, 3, 4, 5 });

        byte[] plainBytes = atomicSerialize(plain);
        byte[] retainingBytes = atomicSerialize(retaining);

        Assert.assertTrue("retaining handler must serialize to non-empty bytes", retainingBytes.length > 0);
        Assert.assertArrayEquals(
                "a retaining handler must serialize byte-identically to an otherwise-equal "
                + "non-retaining one (rawForm is transient and absent from the serial form)",
                plainBytes, retainingBytes);
    }

    // ---- 7. Source-ambiguity regression: (handler, null) binds to the constraints copy ctor ----

    @Test
    public void constructorAmbiguity_gone_handlerNullBindsToConstraintsCopyCtor() {
        // With the (BasicInvocationHandler, byte[]) copy ctor removed, `new
        // BasicInvocationHandler(handler, null)` is unambiguous: it binds to
        // (BasicInvocationHandler, MethodConstraints) -- the copy ctor behind setConstraints(null).
        // This compiling at all is the assertion (it was ambiguous under the copy-ctor design and
        // broke qa ConstructorAccessorTest). We also confirm it behaves as the constraints copy.
        BasicInvocationHandler base = new BasicInvocationHandler(new SerializableEndpoint(1), null);
        // No cast: this bare `null` literal is UNAMBIGUOUS now (only (BasicInvocationHandler,
        // MethodConstraints) matches). Under the removed (BasicInvocationHandler, byte[]) copy ctor
        // it matched both and failed to compile -- the qa ConstructorAccessorTest regression.
        BasicInvocationHandler copy = new BasicInvocationHandler(base, null);
        Assert.assertNull("null-constraints copy is an unconstrained proxy handler",
                copy.getClientConstraints());
        Assert.assertNull("a constraints copy carries no retained wire form", copy.rawForm());
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
