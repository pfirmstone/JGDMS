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

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Admission proves a stream admissible; it must not copy the stream's content to do it. A large {@code byte[]} field used to be copied
 * into the walk's output buffer (and again as that buffer grew), only to be thrown away. This pins the allocation of the full stage to
 * a small fraction of the content for an opaque 4 MiB field.
 */
class DerStreamAdmissionAllocationTest {

    @AtomicSerial
    public static final class Bytes {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("b", byte[].class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Bytes x) throws IOException {
            arg.put("b", x.b);
            arg.writeArgs();
        }
        final byte[] b;
        Bytes(byte[] b) { this.b = b; }
        public Bytes(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            b = arg.get("b", new byte[0], byte[].class);
        }
    }

    public interface Greeter { String greet(); }

    /** A proxy handler that carries a large opaque field. */
    @AtomicSerial
    public static final class BigHandler implements InvocationHandler {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("data", byte[].class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, BigHandler h) throws IOException {
            arg.put("data", h.data);
            arg.writeArgs();
        }
        final byte[] data;
        BigHandler(byte[] data) { this.data = data; }
        public BigHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            data = arg.get("data", new byte[0], byte[].class);
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) { return null; }
    }

    /** A record whose one field holds a proxy (a nested [8] region inside a record's payload). */
    @AtomicSerial
    public static final class HoldsProxy {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("g", Greeter.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, HoldsProxy h) throws IOException {
            arg.put("g", h.g);
            arg.writeArgs();
        }
        final Greeter g;
        HoldsProxy(Greeter g) { this.g = g; }
        public HoldsProxy(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            g = (Greeter) arg.get("g", null);
        }
    }

    private static final int SIZE = 4 * 1024 * 1024;

    private static long allocated() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
                .getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    private static Greeter proxyWithBigHandler() {
        return (Greeter) Proxy.newProxyInstance(DerStreamAdmissionAllocationTest.class.getClassLoader(),
                new Class<?>[] {Greeter.class}, new BigHandler(new byte[SIZE]));
    }

    private static byte[] streamOf(Object... items) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        for (Object o : items) c.writeObject(o);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    /** Bytes the full stage allocates admitting {@code stream}, measured on a second reader after one warm-up. */
    private static long admissionAllocation(byte[] stream) throws IOException {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean, "per-thread allocation counters");
        ClassLoader cl = DerStreamAdmissionAllocationTest.class.getClassLoader();
        DerMarshalInputStream warm = DerMarshalInputStream.forDispatcher(new ByteArrayInputStream(stream),
                new ResolutionContext(cl, false, cl), DerInputLimits.DEFAULT, DerStreamPolicy.BUILT_IN);
        warm.admitStream();
        DerMarshalInputStream in = DerMarshalInputStream.forDispatcher(new ByteArrayInputStream(stream),
                new ResolutionContext(cl, false, cl), DerInputLimits.DEFAULT, DerStreamPolicy.BUILT_IN);
        long before = allocated();
        in.admitStream();
        return allocated() - before;
    }

    @Test
    void theFullStageDoesNotCopyAnOpaqueFieldsContent() throws Exception {
        byte[] stream = streamOf(new Bytes(new byte[SIZE]));
        long used = admissionAllocation(stream);
        assertTrue(used < SIZE / 4, "admitting a " + SIZE + " byte field allocated " + used + " bytes: its content was copied");
    }

    // The region shapes below are cut out of the input before the walk (one copy; a collection and a proxy are cut twice, once for the item and once
    // for the collection or handler record inside it), and the elements are few and large because the value check of each element allocates a
    // little (a reader, a BigInteger for an integer; interpreted code does not remove it), which is not the copy being pinned here. A walk that also wrote the content to a growing buffer would cost several more.

    @Test
    void aTopLevelArrayOfRecordsIsNotCopiedBeyondTheItemItself() throws Exception {
        Bytes[] four = new Bytes[4];
        for (int i = 0; i < four.length; i++) four[i] = new Bytes(new byte[SIZE / 4]);
        byte[] stream = streamOf((Object) four);
        long used = admissionAllocation(stream);
        assertTrue(used < stream.length * 3 / 2, "[9] array of " + stream.length + " bytes: admission allocated " + used);
    }

    @Test
    void aTopLevelCollectionIsNotCopiedBeyondTheItemItself() throws Exception {
        // few large elements: the value check of each element costs a little garbage, which is not the copy being pinned
        List<byte[]> list = new ArrayList<>();
        for (int k = 0; k < 16; k++) list.add(new byte[256 * 1024]);
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeCollection(list, "list:byte[]");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        byte[] stream = bos.toByteArray();
        long used = admissionAllocation(stream);
        assertTrue(used < stream.length * 5 / 2, "[16] collection of " + stream.length + " bytes: admission allocated " + used);
    }

    @Test
    void aTopLevelProxyHandlerIsNotCopiedBeyondTheItemItself() throws Exception {
        byte[] stream = streamOf(proxyWithBigHandler());
        long used = admissionAllocation(stream);
        assertTrue(used < stream.length * 5 / 2, "[8] proxy of " + stream.length + " bytes: admission allocated " + used);
    }

    @Test
    void aProxyNestedInARecordIsNotCopied() throws Exception {
        byte[] stream = streamOf(new HoldsProxy(proxyWithBigHandler()));
        long used = admissionAllocation(stream);
        assertTrue(used < SIZE / 4, "nested [8] proxy in a " + stream.length + " byte stream: admission allocated " + used);
    }
}
