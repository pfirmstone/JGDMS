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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.InboundRequest;
import org.apache.river.api.io.AtomicObjectInput;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Assert;
import org.junit.Test;

/**
 * B0 at the dispatcher seam, flipped by B2: a request stream {@code [good, good, item whose chainRef names a digest never
 * sent]}, read through the stream the real {@link AtomicDerInvocationDispatcher} creates. B0 pinned that the two good items were
 * delivered before the reject; B2 admits or rejects the whole stream at {@code admitStream()} (which
 * {@code BasicInvocationDispatcher.dispatch} calls between {@code checkAccess} and {@code unmarshalArguments}), so nothing is
 * delivered and no constructor runs. The full {@code dispatch(...)} call is driven by {@code AtomicDerAdmissionDispatchTest}.
 */
public class AtomicDerAdmissionSeamTest {

    @AtomicSerial
    public static final class Item {
        static final AtomicInteger CONSTRUCTED = new AtomicInteger();
        public static SerialForm[] serialForm() {
            return new SerialForm[] { new SerialForm("n", int.class) };
        }
        public static void serialize(PutArg arg, Item i) throws IOException {
            arg.put("n", i.n);
            arg.writeArgs();
        }
        final int n;
        public Item(int n) { this.n = n; }
        public Item(GetArg arg) throws IOException, ClassNotFoundException {
            CONSTRUCTED.incrementAndGet();
            this.n = arg.get("n", 0);
        }
    }

    public interface Service extends Remote {
        Item echo(Item i) throws Exception;
    }

    private static class Impl implements Service {
        public Item echo(Item i) { return i; }
    }

    private static final class FakeServerCaps implements ServerCapabilities {
        public InvocationConstraints checkConstraints(InvocationConstraints c) { return InvocationConstraints.EMPTY; }
    }

    private static final class FakeInbound implements InboundRequest {
        private final InputStream in;
        FakeInbound(InputStream in) { this.in = in; }
        public InputStream getRequestInputStream() { return in; }
        public OutputStream getResponseOutputStream() { return new ByteArrayOutputStream(); }
        public void checkPermissions() { }
        public InvocationConstraints checkConstraints(InvocationConstraints c) throws UnsupportedConstraintException {
            return InvocationConstraints.EMPTY;
        }
        public void populateContext(Collection ctx) { }
        public void abort() { }
    }

    /** [Item(1), Item(2)] followed by an item whose chainRef (32 zero octets) names no digest sent in this stream. */
    private static byte[] goodGoodBad() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DerMarshalOutputStream out = new DerMarshalOutputStream(bos);
        out.writeObject(new Item(1));
        out.writeObject(new Item(2));
        out.flush();
        byte[] emptyHierarchy = {0x30, 0x00};
        byte[] chainRef = new byte[2 + 32];
        chainRef[0] = (byte) 0x81;
        chainRef[1] = 32;
        byte[] fmt = "JGDMS-STD-006/ATOMIC-DER".getBytes("US-ASCII");
        ByteArrayOutputStream rec = new ByteArrayOutputStream();
        rec.write(0x04); rec.write(emptyHierarchy.length); rec.write(emptyHierarchy);
        rec.write(chainRef);
        rec.write(0x0C); rec.write(fmt.length); rec.write(fmt);
        byte[] recBytes = rec.toByteArray();
        bos.write(0x30); bos.write(recBytes.length); bos.write(recBytes);   // SEQUENCE { payload, chainRef, format }
        byte[] seq = bos.toByteArray();
        // wrap the last record in [1]: rebuild by hand to keep the arithmetic obvious
        int recStart = seq.length - (2 + recBytes.length);
        ByteArrayOutputStream fixed = new ByteArrayOutputStream();
        fixed.write(seq, 0, recStart);
        fixed.write(0xA1); fixed.write(2 + recBytes.length);
        fixed.write(seq, recStart, 2 + recBytes.length);
        return fixed.toByteArray();
    }

    @Test
    public void aBadRequestStreamDeliversNothingAndRunsNoConstructorAndNoDgcCallback() throws Exception {
        Item.CONSTRUCTED.set(0);
        Collection<Method> methods = new ArrayList<>();
        methods.add(Service.class.getMethod("echo", Item.class));
        AtomicDerInvocationDispatcher dispatcher = new AtomicDerInvocationDispatcher(
                methods, new FakeServerCaps(), null, null, AtomicDerAdmissionSeamTest.class.getClassLoader());
        // B0 pinned: read two good items, reject at the third. B2: the cheap stage accepts the stream at creation (nothing
        // in it is malformed at the item-header level), an object read before admitStream() is refused (and the refusal ends the
        // stream), and admitStream() rejects the WHOLE stream, so no object is ever built.
        ObjectInput refused = dispatcher.createMarshalInputStream(
                new Impl(), new FakeInbound(new ByteArrayInputStream(goodGoodBad())), false, Collections.emptyList());
        Assert.assertTrue(refused instanceof DerMarshalInputStream);
        try {
            refused.readObject();
            Assert.fail("an object read before admitStream() must be refused");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("admitStream()"));
        }
        try {
            ((AtomicObjectInput) refused).admitStream();
            Assert.fail("a refused read ends the stream: admitting afterwards cannot revive it");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("delivers nothing"));
        }
        refused.close();

        ObjectInput in = dispatcher.createMarshalInputStream(
                new Impl(), new FakeInbound(new ByteArrayInputStream(goodGoodBad())), false, Collections.emptyList());
        AtomicInteger fired = new AtomicInteger();
        ((DerMarshalInputStream) in).registerValidation(fired::incrementAndGet, 0);
        try {
            ((AtomicObjectInput) in).admitStream();
            Assert.fail("the third item names an unknown chain digest: the whole stream must be rejected");
        } catch (IOException expected) {
            StringBuilder all = new StringBuilder();
            for (Throwable t = expected; t != null; t = t.getCause()) all.append(t.getMessage()).append(" | ");
            Assert.assertTrue("rejected for the unknown digest: " + all, all.toString().contains("unknown-digest"));
        }
        try {
            in.readObject();
            Assert.fail("a rejected stream delivers nothing");
        } catch (IOException expected) {
            // permanent
        }
        in.close();
        Assert.assertEquals("B0 pinned 2; B2 builds nothing", 0, Item.CONSTRUCTED.get());
        Assert.assertEquals("and registers nothing for the DGC", 0, fired.get());
    }
}
