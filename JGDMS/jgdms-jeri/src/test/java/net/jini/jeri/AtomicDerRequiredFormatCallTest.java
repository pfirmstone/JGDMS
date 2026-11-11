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

import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Collections;
import java.util.NoSuchElementException;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.io.MarshalledInstance;
import net.jini.io.UnsupportedConstraintException;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Assert;
import org.junit.Test;

/**
 * Invariant: when a DER call requires {@code MarshallingFormat.ATOMIC_DER}, a smart proxy returned
 * in the response must itself be a DER payload. The format a nested {@link MarshalledInstance}
 * names is chosen by the sender, so the receiver's requirement has to reach the nested decode:
 * {@code BasicInvocationHandler.invokeRemoteMethodOnce} -> {@code AtomicDerInvocationHandler.
 * createMarshalInputStream} -> {@code ResolutionContext} -> {@code DerProxySerializer}.
 *
 * <p>Drives the real {@code invoke} on a real proxy over a fake endpoint, so every hop is on the
 * path. Unit coverage of the last hop alone is in {@code DerProxyRequiredFormatTest} (jgdms-der).
 */
public class AtomicDerRequiredFormatCallTest {

    /** The remote interface the proxy implements; one method returning whatever was decoded. */
    public interface Fetcher extends Remote {
        Object fetch() throws RemoteException;
    }

    /** The {@code @AtomicSerial} handler a {@code DerProxySerializer} carrier must hold. */
    @AtomicSerial
    public static class CarrierHandler implements InvocationHandler {
        public static SerialForm[] serialForm() { return new SerialForm[0]; }

        public static void serialize(PutArg arg, CarrierHandler h) throws IOException {
            arg.writeArgs();
        }

        public CarrierHandler() {}

        public CarrierHandler(GetArg arg) throws IOException, ClassNotFoundException {}

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    private static final class OneShotIterator implements OutboundRequestIterator {
        private final OutboundRequest request;
        private boolean used;

        OneShotIterator(OutboundRequest request) { this.request = request; }

        @Override public boolean hasNext() { return !used; }

        @Override public OutboundRequest next() {
            if (used) throw new NoSuchElementException();
            used = true;
            return request;
        }
    }

    private static final class FakeEndpoint implements ObjectEndpoint {
        private final OutboundRequest request;

        FakeEndpoint(OutboundRequest request) { this.request = request; }

        @Override public OutboundRequestIterator newCall(InvocationConstraints constraints) {
            return new OneShotIterator(request);
        }

        @Override public RemoteException executeCall(OutboundRequest call) { return null; }

        @Override public boolean equals(Object o) { return o instanceof FakeEndpoint; }

        @Override public int hashCode() { return 1; }
    }

    private static final class FakeRequest implements OutboundRequest {
        private final InvocationConstraints unfulfilled;
        // one stream, as a real request has: the handler reads the response code from it, then
        // the DER decoder continues from the next octet
        private final InputStream response;

        FakeRequest(InvocationConstraints unfulfilled, byte[] response) {
            this.unfulfilled = unfulfilled;
            this.response = new ByteArrayInputStream(response);
        }

        @Override public OutputStream getRequestOutputStream() { return new ByteArrayOutputStream(); }

        @Override public InputStream getResponseInputStream() { return response; }

        @Override public void populateContext(Collection ctx) {}

        @Override public InvocationConstraints getUnfulfilledConstraints() { return unfulfilled; }

        // true => an unmarshalling failure surfaces as UnmarshalException, not a silent retry
        @Override public boolean getDeliveryStatus() { return true; }

        @Override public void abort() {}
    }

    /**
     * A response (success code 0x01, then a DER {@code DerProxySerializer} carrier) whose smart
     * proxy is a JOSS {@link MarshalledInstance} -- the sender-chosen format the call must refuse.
     * The carrier's constructor is package-private in another module, hence reflection.
     */
    private static byte[] jossCarrierResponse() throws Exception {
        byte[] body = jossCarrierBytes();
        byte[] withCode = new byte[body.length + 1];
        withCode[0] = 0x01;
        System.arraycopy(body, 0, withCode, 1, body.length);
        return withCode;
    }

    private static byte[] jossCarrierBytes() throws Exception {
        MarshalledInstance joss = new MarshalledInstance("joss-payload", Collections.emptyList());
        Class<?> c = Class.forName("au.net.zeus.jgdms.der.object.DerProxySerializer");
        Constructor<?> ctor = c.getDeclaredConstructor(InvocationHandler.class,
                MarshalledInstance.class, Collection.class, ClassLoader.class, ClassLoader.class);
        ctor.setAccessible(true);
        Object carrier = ctor.newInstance(new CarrierHandler(), joss, Collections.emptyList(), null, null);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DerMarshalOutputStream out = new DerMarshalOutputStream(bos, Collections.emptyList(),
                AtomicDerRequiredFormatCallTest.class.getClassLoader());
        out.writeObject(carrier);
        out.flush();
        return bos.toByteArray();
    }

    private static Fetcher proxyOver(InvocationConstraints unfulfilled, byte[] response) throws Exception {
        AtomicDerInvocationHandler handler = new AtomicDerInvocationHandler(
                new FakeEndpoint(new FakeRequest(unfulfilled, response)), null);
        return (Fetcher) Proxy.newProxyInstance(
                AtomicDerRequiredFormatCallTest.class.getClassLoader(),
                new Class<?>[]{Fetcher.class, RemoteMethodControl.class, TrustEquivalence.class},
                handler);
    }

    @Test
    public void jossSmartProxyIsRefusedWhenCallRequiresDer() throws Exception {
        Fetcher f = proxyOver(new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null),
                jossCarrierResponse());
        try {
            Object got = f.fetch();
            Assert.fail("JOSS smart proxy was decoded in a call requiring ATOMIC_DER: " + got);
        } catch (RemoteException expected) {
            Throwable t = expected;
            while (t != null && !(t instanceof UnsupportedConstraintException)) t = t.getCause();
            Assert.assertNotNull("expected an UnsupportedConstraintException in the cause chain of "
                    + expected, t);
            Assert.assertTrue(t.getMessage(), t.getMessage().contains("payloadFormat"));
        }
    }

    /** Server side: a DER dispatcher reading call arguments must refuse a JOSS smart proxy. */
    @Test
    public void dispatcherRefusesJossSmartProxyArgument() throws Exception {
        byte[] request = jossCarrierBytes();
        InboundRequest in = new InboundRequest() {
            @Override public InputStream getRequestInputStream() { return new ByteArrayInputStream(request); }
            @Override public OutputStream getResponseOutputStream() { return new ByteArrayOutputStream(); }
            @Override public void checkPermissions() {}
            @Override public InvocationConstraints checkConstraints(InvocationConstraints c) {
                return InvocationConstraints.EMPTY;
            }
            @Override public void populateContext(Collection ctx) {}
            @Override public void abort() {}
        };
        ClassLoader loader = AtomicDerRequiredFormatCallTest.class.getClassLoader();
        AtomicDerInvocationDispatcher d = new AtomicDerInvocationDispatcher(
                Collections.singletonList(Fetcher.class.getMethod("fetch")),
                c -> InvocationConstraints.EMPTY, null, null, loader);
        org.apache.river.api.io.AtomicObjectInput ois = (org.apache.river.api.io.AtomicObjectInput)
                d.createMarshalInputStream(new Fetcher() { public Object fetch() { return null; } },
                        in, false, Collections.emptyList());
        ois.admitStream();
        try {
            Object got = ((java.io.ObjectInput) ois).readObject();
            Assert.fail("JOSS smart proxy was decoded by a DER dispatcher: " + got);
        } catch (IOException expected) {
            Throwable t = expected;
            while (t != null && !(t instanceof UnsupportedConstraintException)) t = t.getCause();
            Assert.assertNotNull("expected UnsupportedConstraintException in chain of " + expected, t);
        }
    }

    @Test
    public void jossSmartProxyIsDecodedWhenNoFormatRequired() throws Exception {
        Fetcher f = proxyOver(InvocationConstraints.EMPTY, jossCarrierResponse());
        Assert.assertEquals("joss-payload", f.fetch());
    }
}
