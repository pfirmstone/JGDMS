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
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.security.AccessControlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.jeri.internal.runtime.Util;
import org.junit.Assert;
import org.junit.Test;

/**
 * B2 through the REAL {@code BasicInvocationDispatcher.dispatch(...)}: where the full admission of a request stream runs.
 * <ul>
 *   <li>An UNAUTHORIZED request ({@code checkAccess} throws) must not pay for it: the stream it carries (an unknown-digest
 *       item, which only the full walk can see) is never walked, so it is still un-admitted afterwards.</li>
 *   <li>An AUTHORIZED request is admitted between {@code checkAccess} and {@code unmarshalArguments}: a bad stream is
 *       rejected whole, the remote method is never invoked and no argument constructor runs; a good one is dispatched.</li>
 * </ul>
 */
public class AtomicDerAdmissionDispatchTest {

    @AtomicSerial
    public static final class Arg {
        static final AtomicInteger CONSTRUCTED = new AtomicInteger();
        public static SerialForm[] serialForm() {
            return new SerialForm[] { new SerialForm("n", int.class) };
        }
        public static void serialize(PutArg a, Arg v) throws IOException {
            a.put("n", v.n);
            a.writeArgs();
        }
        final int n;
        public Arg(int n) { this.n = n; }
        public Arg(GetArg a) throws IOException, ClassNotFoundException {
            CONSTRUCTED.incrementAndGet();
            this.n = a.get("n", 0);
        }
    }

    public interface Service extends Remote {
        void take(Arg a) throws Exception;
    }

    static final AtomicInteger INVOKED = new AtomicInteger();

    private static class Impl implements Service {
        public void take(Arg a) { INVOKED.incrementAndGet(); }
    }

    private static final class FakeCaps implements ServerCapabilities {
        public InvocationConstraints checkConstraints(InvocationConstraints c) { return InvocationConstraints.EMPTY; }
    }

    private static final class FakeInbound implements InboundRequest {
        private final InputStream in;
        final ByteArrayOutputStream response = new ByteArrayOutputStream();
        FakeInbound(byte[] request) { this.in = new ByteArrayInputStream(request); }
        public InputStream getRequestInputStream() { return in; }
        public OutputStream getResponseOutputStream() { return response; }
        public void checkPermissions() { }
        public InvocationConstraints checkConstraints(InvocationConstraints c) throws UnsupportedConstraintException {
            return InvocationConstraints.EMPTY;
        }
        public void populateContext(Collection ctx) { }
        public void abort() { }
    }

    /** A dispatcher that records the stream it created and whether access was granted. */
    private static final class Probe extends AtomicDerInvocationDispatcher {
        volatile ObjectInput stream;
        final AtomicInteger accessChecks = new AtomicInteger();
        final boolean allow;
        Probe(Collection methods, boolean allow) throws Exception {
            super(methods, new FakeCaps(), null, null, AtomicDerAdmissionDispatchTest.class.getClassLoader());
            this.allow = allow;
        }
        @Override
        protected ObjectInput createMarshalInputStream(Object impl, InboundRequest request, boolean integrity, Collection context)
                throws IOException {
            stream = super.createMarshalInputStream(impl, request, integrity, context);
            return stream;
        }
        @Override
        protected void checkAccess(Remote impl, Method method, InvocationConstraints constraints, Collection context,
                                   AccessControlContext remoteContext) {
            accessChecks.incrementAndGet();
            if (!allow) throw new SecurityException("not authorized");
        }
    }

    private static Collection<Method> methods() throws Exception {
        Collection<Method> m = new ArrayList<>();
        m.add(Service.class.getMethod("take", Arg.class));
        return m;
    }

    /** Request: version 1, no integrity, no atomic validation, then the DER stream [method hash, items...]. */
    private static byte[] request(boolean bad) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x01); bos.write(0x00); bos.write(0x00);
        DerMarshalOutputStream out = new DerMarshalOutputStream(bos);
        out.writeLong(Util.getMethodHash(Service.class.getMethod("take", Arg.class)));
        out.writeObject(new Arg(7));
        out.flush();
        if (bad) {
            // an item whose chainRef names a digest this stream never sent: only the full walk can tell
            byte[] emptyHierarchy = {0x30, 0x00};
            byte[] fmt = "JGDMS-STD-006/ATOMIC-DER".getBytes("US-ASCII");
            ByteArrayOutputStream rec = new ByteArrayOutputStream();
            rec.write(0x04); rec.write(emptyHierarchy.length); rec.write(emptyHierarchy);
            rec.write(0x81); rec.write(32); rec.write(new byte[32]);
            rec.write(0x0C); rec.write(fmt.length); rec.write(fmt);
            byte[] r = rec.toByteArray();
            bos.write(0xA1); bos.write(2 + r.length); bos.write(0x30); bos.write(r.length); bos.write(r);
        }
        return bos.toByteArray();
    }

    @Test
    public void anUnauthorizedRequestDoesNotPayForTheFullWalk() throws Exception {
        Arg.CONSTRUCTED.set(0);
        INVOKED.set(0);
        Probe d = new Probe(methods(), false);
        d.dispatch(new Impl(), new FakeInbound(request(true)), new ArrayList());
        Assert.assertEquals("checkAccess ran", 1, d.accessChecks.get());
        Assert.assertEquals(0, INVOKED.get());
        Assert.assertEquals(0, Arg.CONSTRUCTED.get());
        // the stream was created and its method hash read, but its (bad) items were never walked: admitting it NOW is the
        // first time the unknown digest is seen, so the failure is the original one, not a "rejected earlier" rethrow
        DerMarshalInputStream in = (DerMarshalInputStream) d.stream;
        try {
            in.admitStream();
            Assert.fail("the stream is bad");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("unknown-digest"));
            Assert.assertFalse("it was not rejected earlier by a walk the unauthorized caller triggered: " + e.getMessage(),
                    e.getMessage().contains("rejected by admission"));
        }
    }

    @Test
    public void anAuthorizedBadRequestIsRejectedWholeBeforeAnArgumentIsBuiltOrTheMethodRuns() throws Exception {
        Arg.CONSTRUCTED.set(0);
        INVOKED.set(0);
        Probe d = new Probe(methods(), true);
        d.dispatch(new Impl(), new FakeInbound(request(true)), new ArrayList());
        Assert.assertEquals(1, d.accessChecks.get());
        Assert.assertEquals("the remote method must not run", 0, INVOKED.get());
        Assert.assertEquals("no argument may be constructed from a stream that is not admissible", 0, Arg.CONSTRUCTED.get());
    }

    @Test
    public void anAuthorizedGoodRequestIsAdmittedAndDispatched() throws Exception {
        Arg.CONSTRUCTED.set(0);
        INVOKED.set(0);
        Probe d = new Probe(methods(), true);
        d.dispatch(new Impl(), new FakeInbound(request(false)), new ArrayList());
        Assert.assertEquals(1, d.accessChecks.get());
        Assert.assertEquals(1, Arg.CONSTRUCTED.get());
        Assert.assertEquals(1, INVOKED.get());
    }
}
