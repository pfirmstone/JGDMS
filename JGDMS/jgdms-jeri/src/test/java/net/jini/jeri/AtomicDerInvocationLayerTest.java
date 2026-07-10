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

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.io.MarshalledInstance;
import net.jini.io.UnsupportedConstraintException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Assert;
import org.junit.Test;

/**
 * B2 loopback tests for the DER invocation layer (JGDMS-STD-008 sec.14, A0+B2).
 *
 * <p>Same package as the classes under test so protected createMarshal*Stream
 * methods are accessible directly.
 *
 * <h2>Test inventory</h2>
 * <ol>
 *   <li>Configuration wiring: AtomicDerILFactory creates AtomicDerInvocationHandler and
 *       AtomicDerInvocationDispatcher.</li>
 *   <li>Round-trip: handler writes a {@code Point @AtomicSerial} object via
 *       DerMarshalOutputStream; dispatcher reads it back via DerMarshalInputStream;
 *       result equals original but is not the same instance (copy semantics).</li>
 * </ol>
 */
public class AtomicDerInvocationLayerTest {

    // =========================================================================
    // @AtomicSerial test fixture -- a simple 2-int Point
    // =========================================================================

    /**
     * Minimal {@code @AtomicSerial} fixture. Used only in this test class; NOT
     * borrowed from jgdms-der (its fixtures are not on this classpath).
     */
    @AtomicSerial
    public static final class Point {

        public static SerialForm[] serialForm() {
            return new SerialForm[]{
                new SerialForm("x", int.class),
                new SerialForm("y", int.class)
            };
        }

        public static void serialize(PutArg arg, Point p) throws IOException {
            arg.put("x", p.x);
            arg.put("y", p.y);
            arg.writeArgs();
        }

        public final int x;
        public final int y;

        /** Value constructor. */
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        /** @AtomicSerial deserialization constructor. */
        public Point(GetArg arg) throws IOException, ClassNotFoundException {
            this.x = arg.get("x", 0);
            this.y = arg.get("y", 0);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Point that)) return false;
            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "Point(" + x + "," + y + ")";
        }
    }

    // =========================================================================
    // Remote interface with a method that uses Point
    // =========================================================================

    /** Minimal remote interface so we have a methods collection and a Method. */
    public interface PointService extends Remote {
        Point echo(Point p) throws Exception;
    }

    // =========================================================================
    // Fakes
    // =========================================================================

    /** Fake Remote impl for factory / dispatcher construction. */
    private static class FakeRemote implements PointService, Remote {
        @Override public Point echo(Point p) { return p; }
    }

    /** Fake ObjectEndpoint -- only needed for handler construction. */
    private static class FakeObjectEndpoint implements ObjectEndpoint {
        @Override
        public OutboundRequestIterator newCall(InvocationConstraints constraints) {
            throw new UnsupportedOperationException("test stub");
        }

        @Override
        public java.rmi.RemoteException executeCall(OutboundRequest call)
                throws IOException {
            throw new UnsupportedOperationException("test stub");
        }

        @Override
        public boolean equals(Object o) { return o instanceof FakeObjectEndpoint; }

        @Override
        public int hashCode() { return 1; }
    }

    /** Fake ServerCapabilities -- always returns empty constraints. */
    private static class FakeServerCapabilities implements ServerCapabilities {
        @Override
        public InvocationConstraints checkConstraints(InvocationConstraints constraints)
                throws UnsupportedConstraintException {
            return InvocationConstraints.EMPTY;
        }
    }

    /**
     * Fake OutboundRequest backed by a ByteArrayOutputStream for the request
     * (write side) and a ByteArrayInputStream for the response (read side).
     * Only getRequestOutputStream() and getResponseInputStream() are implemented.
     */
    private static class FakeOutboundRequest implements OutboundRequest {
        private final ByteArrayOutputStream requestBaos;
        private final ByteArrayInputStream responseBais;

        FakeOutboundRequest(ByteArrayOutputStream requestBaos,
                            ByteArrayInputStream responseBais) {
            this.requestBaos  = requestBaos;
            this.responseBais = responseBais;
        }

        @Override public OutputStream getRequestOutputStream()  { return requestBaos; }
        @Override public InputStream  getResponseInputStream()  { return responseBais; }

        @Override public void populateContext(Collection ctx) {}
        @Override public InvocationConstraints getUnfulfilledConstraints() { return InvocationConstraints.EMPTY; }
        @Override public boolean getDeliveryStatus()                        { return false; }
        @Override public void abort()                                        {}
    }

    /**
     * Fake InboundRequest backed by a ByteArrayInputStream for the request
     * (read side) and a ByteArrayOutputStream for the response (write side).
     * Only getRequestInputStream() and getResponseOutputStream() are implemented.
     */
    private static class FakeInboundRequest implements InboundRequest {
        private final ByteArrayInputStream  requestBais;
        private final ByteArrayOutputStream responseBaos;

        FakeInboundRequest(ByteArrayInputStream requestBais,
                           ByteArrayOutputStream responseBaos) {
            this.requestBais  = requestBais;
            this.responseBaos = responseBaos;
        }

        @Override public InputStream  getRequestInputStream()   { return requestBais; }
        @Override public OutputStream getResponseOutputStream() { return responseBaos; }

        @Override public void checkPermissions()                            {}
        @Override public InvocationConstraints checkConstraints(InvocationConstraints c)
                throws UnsupportedConstraintException                      { return InvocationConstraints.EMPTY; }
        @Override public void populateContext(Collection ctx)              {}
        @Override public void abort()                                       {}
    }

    // =========================================================================
    // Helper: build a Collection<Method> for PointService
    // =========================================================================

    private static Collection<Method> pointServiceMethods() throws NoSuchMethodException {
        Collection<Method> methods = new ArrayList<>();
        methods.add(PointService.class.getMethod("echo", Point.class));
        return methods;
    }

    // =========================================================================
    // Test 1: Configuration wiring
    // =========================================================================

    /**
     * Verifies that {@link AtomicDerILFactory} produces a {@link AtomicDerInvocationHandler}
     * and a {@link AtomicDerInvocationDispatcher}.
     */
    @Test
    public void test1_configurationWiring() throws Exception {
        FakeRemote fakeImpl = new FakeRemote();
        FakeObjectEndpoint fakeOE = new FakeObjectEndpoint();
        FakeServerCapabilities fakeCaps = new FakeServerCapabilities();
        Collection<Method> methods = pointServiceMethods();

        // Use the (serverConstraints, permissionClass, ClassLoader) constructor.
        ClassLoader loader = AtomicDerInvocationLayerTest.class.getClassLoader();
        AtomicDerILFactory factory = new AtomicDerILFactory(null, null, loader);

        // Call the protected factory methods directly (same-package access).
        InvocationHandler handler = factory.createInvocationHandler(
                new Class[]{PointService.class}, fakeImpl, fakeOE);
        InvocationDispatcher dispatcher = factory.createInvocationDispatcher(
                methods, fakeImpl, fakeCaps);

        Assert.assertTrue("handler must be AtomicDerInvocationHandler",
                handler instanceof AtomicDerInvocationHandler);
        Assert.assertTrue("dispatcher must be AtomicDerInvocationDispatcher",
                dispatcher instanceof AtomicDerInvocationDispatcher);
    }

    // =========================================================================
    // Test 2: Round-trip through the handler/dispatcher stream seam
    // =========================================================================

    /**
     * Encodes a {@code Point} via the handler's {@code createMarshalOutputStream}
     * (which must return a {@link DerMarshalOutputStream}), then decodes it via
     * the dispatcher's {@code createMarshalInputStream} (which must return a
     * {@link DerMarshalInputStream}).  Asserts the decoded point equals the
     * original but is a distinct instance (copy semantics -- no back-references
     * per STD-008 sec.15.3).
     */
    @Test
    public void test2_roundTripThroughHandlerDispatcherSeam() throws Exception {
        Point original = new Point(42, -7);

        // --- ENCODE side: handler writeObject -> DerMarshalOutputStream ---
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FakeOutboundRequest outReq = new FakeOutboundRequest(baos, new ByteArrayInputStream(new byte[0]));

        AtomicDerInvocationHandler handler = new AtomicDerInvocationHandler(new FakeObjectEndpoint(), null);

        // Build a minimal dynamic proxy that has handler as its invocation handler,
        // so that Proxy.getInvocationHandler(proxy) == handler passes inside createMarshalInputStream.
        Object proxy = Proxy.newProxyInstance(
                AtomicDerInvocationLayerTest.class.getClassLoader(),
                new Class[]{PointService.class, RemoteMethodControl.class, TrustEquivalence.class},
                handler);

        Method m = PointService.class.getMethod("echo", Point.class);
        Collection<Object> ctx = Collections.emptyList();

        ObjectOutput out = handler.createMarshalOutputStream(proxy, m, outReq, ctx);

        Assert.assertTrue("createMarshalOutputStream must return DerMarshalOutputStream",
                out instanceof DerMarshalOutputStream);

        out.writeObject(original);
        out.flush();
        // Note: DerMarshalOutputStream.flush() ships bytes to underlying stream.
        // We close after flush to complete.
        out.close();

        byte[] encoded = baos.toByteArray();
        Assert.assertTrue("encoded bytes must be non-empty", encoded.length > 0);

        // --- DECODE side: dispatcher readObject <- DerMarshalInputStream ---
        ByteArrayInputStream requestBais = new ByteArrayInputStream(encoded);
        ByteArrayOutputStream responseBaos = new ByteArrayOutputStream();
        FakeInboundRequest inReq = new FakeInboundRequest(requestBais, responseBaos);

        Collection<Method> methods = pointServiceMethods();
        AtomicDerInvocationDispatcher dispatcher = new AtomicDerInvocationDispatcher(
                methods, new FakeServerCapabilities(), null, null,
                AtomicDerInvocationLayerTest.class.getClassLoader());

        FakeRemote fakeImpl = new FakeRemote();
        ObjectInput in = dispatcher.createMarshalInputStream(fakeImpl, inReq, false, ctx);

        Assert.assertTrue("createMarshalInputStream must return DerMarshalInputStream",
                in instanceof DerMarshalInputStream);

        // A1a (STD-008 sec.18.2): the DER in-band input stream is an AtomicObjectInput,
        // so a DER-exported service satisfies AtomicInputValidation.YES (atomic per-object
        // validation) -- the JERI atomic gate recognises it via this marker.
        Assert.assertTrue("DER input stream must be an AtomicObjectInput (satisfies "
                + "AtomicInputValidation)",
                in instanceof org.apache.river.api.io.AtomicObjectInput);

        Object decoded = in.readObject();
        Assert.assertTrue("decoded object must be a Point", decoded instanceof Point);

        Point decodedPoint = (Point) decoded;

        Assert.assertEquals("decoded point must equal original", original, decodedPoint);
        Assert.assertNotSame("decoded point must be a copy, not the same instance",
                original, decodedPoint);
    }

    // =========================================================================
    // Test 3: AtomicDerILFactory equals / hashCode
    // =========================================================================

    @Test
    public void test3_factoryEquality() {
        ClassLoader loader = AtomicDerInvocationLayerTest.class.getClassLoader();
        AtomicDerILFactory f1 = new AtomicDerILFactory(null, null, loader);
        AtomicDerILFactory f2 = new AtomicDerILFactory(null, null, loader);
        Assert.assertEquals("two AtomicDerILFactory instances with same args must be equal", f1, f2);
        Assert.assertEquals("equal factories must have equal hashCodes", f1.hashCode(), f2.hashCode());
    }

    // =========================================================================
    // A1b: MarshallingFormat in-band enforcement (STD-008 sec.18.3)
    // =========================================================================

    /** marshallingFormat() reports JOSS for the base handler, DER for AtomicDer. */
    @Test
    public void a1b_handlerMarshallingFormats() {
        FakeObjectEndpoint oe = new FakeObjectEndpoint();
        Assert.assertEquals(MarshalledInstance.FORMAT_JOSS,
                new BasicInvocationHandler(oe, null).marshallingFormat());
        Assert.assertEquals(MarshallingFormat.ATOMIC_DER.getFormat(),
                new AtomicDerInvocationHandler(oe, null).marshallingFormat());
    }

    /** marshallingFormat() reports JOSS for the base dispatcher, DER for AtomicDer. */
    @Test
    public void a1b_dispatcherMarshallingFormats() throws Exception {
        Collection<Method> methods = pointServiceMethods();
        ClassLoader loader = AtomicDerInvocationLayerTest.class.getClassLoader();
        Assert.assertEquals(MarshalledInstance.FORMAT_JOSS,
                new BasicInvocationDispatcher(methods, new FakeServerCapabilities(), null, null, loader)
                        .marshallingFormat());
        Assert.assertEquals(MarshallingFormat.ATOMIC_DER.getFormat(),
                new AtomicDerInvocationDispatcher(methods, new FakeServerCapabilities(), null, null, loader)
                        .marshallingFormat());
    }

    /**
     * A DER service may DECLARE a MarshallingFormat.ATOMIC_DER server constraint and export
     * successfully: the DER dispatcher satisfies it (verified, then stripped before the
     * transport check). Pre-A1b this would have thrown (unknown constraint at the transport).
     */
    @Test
    public void a1b_derDispatcherExportsWithDerRequirement() throws Exception {
        Collection<Method> methods = pointServiceMethods();
        ClassLoader loader = AtomicDerInvocationLayerTest.class.getClassLoader();
        MethodConstraints mc = new BasicMethodConstraints(
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        AtomicDerInvocationDispatcher d = new AtomicDerInvocationDispatcher(
                methods, new FakeServerCapabilities(), mc, null, loader);
        Assert.assertNotNull(d);
    }

    /**
     * A JOSS service declaring a MarshallingFormat.ATOMIC_DER server constraint FAILS to export
     * (its codec format does not match the required format) -- fail-fast, discriminating
     * against the DER case above.
     */
    @Test(expected = ExportException.class)
    public void a1b_jossDispatcherRejectsDerRequirement() throws Exception {
        Collection<Method> methods = pointServiceMethods();
        ClassLoader loader = AtomicDerInvocationLayerTest.class.getClassLoader();
        MethodConstraints mc = new BasicMethodConstraints(
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        new BasicInvocationDispatcher(methods, new FakeServerCapabilities(), mc, null, loader);
    }

}
