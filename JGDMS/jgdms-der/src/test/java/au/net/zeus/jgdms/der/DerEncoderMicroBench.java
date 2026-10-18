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

package au.net.zeus.jgdms.der;

import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * A1 micro-benchmark (SOW-DER-Streaming-Encoder): today's {@link DerWriter} composition vs
 * {@link DerEncoder} (the tree variant it was chosen against was deleted after this comparison). Plain loops with warm-up and the median of several runs; it reports
 * time AND allocated bytes per operation (the encoder's other goal). Not a test: run
 * {@code java -cp target/classes:target/test-classes:<deps> au.net.zeus.jgdms.der.DerEncoderMicroBench}.
 */
public final class DerEncoderMicroBench {

    interface Work { byte[] run(); }

    static final byte[] BLOB = new byte[64 * 1024];
    static final Tag CTX = new Tag(Tag.CLASS_CONTEXT, true, 3);
    static { Arrays.fill(BLOB, (byte) 0x5A); }

    // ---- workload 1: a 64 KiB blob nested 7 constructs deep ----
    static byte[] blobFacade() {
        byte[] x = DerWriter.writeOctetString(BLOB);
        for (int i = 0; i < 7; i++) x = DerWriter.writeTlv(i % 2 == 0 ? Tag.SEQUENCE : CTX, x);
        return x;
    }
    static byte[] blobWith(DerEncoder e) {
        for (int i = 0; i < 7; i++) e.begin(i % 2 == 0 ? Tag.SEQUENCE : CTX);   // outermost first
        e.leaf(Tag.OCTET_STRING, BLOB);
        for (int i = 0; i < 7; i++) e.end();
        return e.toByteArray();
    }

    // ---- workload 2: batch100-like: SEQUENCE of 100 x SEQUENCE{INTEGER, 3 UTF8Strings} ----
    static final String[] S = {"record-123456", "A short description of record number 123456 for the benchmark", "owner3@example.org"};
    static byte[] batchFacade() {
        List<byte[]> items = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            List<byte[]> f = new ArrayList<>(4);
            f.add(DerWriter.writeInteger(i));
            for (String s : S) f.add(DerWriter.writeUtf8String(s));
            items.add(DerWriter.writeSequence(f));
        }
        return DerWriter.writeSequence(items);
    }
    static byte[] batchWith(DerEncoder e) {
        e.begin(Tag.SEQUENCE);
        for (int i = 0; i < 100; i++) {
            e.begin(Tag.SEQUENCE);
            e.leaf(Tag.INTEGER, new byte[] {(byte) i});
            for (String s : S) e.leaf(Tag.UTF8STRING, s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            e.end();
        }
        e.end();
        return e.toByteArray();
    }

    // ---- workload 3: a small record ----
    static byte[] smallFacade() {
        List<byte[]> f = new ArrayList<>(4);
        f.add(DerWriter.writeInteger(42));
        for (String s : S) f.add(DerWriter.writeUtf8String(s));
        return DerWriter.writeSequence(f);
    }
    static byte[] smallWith(DerEncoder e) {
        e.begin(Tag.SEQUENCE).leaf(Tag.INTEGER, new byte[] {42});
        for (String s : S) e.leaf(Tag.UTF8STRING, s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return e.end().toByteArray();
    }

    // ---- workload 4: a sorted set of 100 small elements ----
    static byte[] setFacade() {
        List<byte[]> els = new ArrayList<>(100);
        for (int i = 99; i >= 0; i--) els.add(DerWriter.writeUtf8String("element-" + (i * 7919 % 1000)));
        CollectionWireTypes.octetSort(els);
        return DerWriter.writeSet(els);
    }
    static byte[] setWith(DerEncoder e) {
        e.beginSortedSet(CollectionWireTypes.OCTET_SORT);
        for (int i = 99; i >= 0; i--) e.leaf(Tag.UTF8STRING, ("element-" + (i * 7919 % 1000)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return e.end().toByteArray();
    }

    // ---- harness ----
    static final com.sun.management.ThreadMXBean TMX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    static void measure(String name, Work w, int iters) {
        byte[] ref = w.run();
        for (int i = 0; i < iters * 3; i++) w.run();                       // warm-up
        double[] ns = new double[9];
        long alloc = 0;
        for (int r = 0; r < ns.length; r++) {
            long a0 = TMX.getCurrentThreadAllocatedBytes();
            long t0 = System.nanoTime();
            for (int i = 0; i < iters; i++) w.run();
            long t1 = System.nanoTime();
            alloc = (TMX.getCurrentThreadAllocatedBytes() - a0) / iters;
            ns[r] = (t1 - t0) / (double) iters;
        }
        Arrays.sort(ns);
        System.out.printf("  %-22s %10.0f ns/op (median of 9)  %10d bytes allocated/op  [output %d bytes]%n",
                name, ns[ns.length / 2], alloc, ref.length);
    }

    static void workload(String title, int iters, Supplier<byte[]> facade, java.util.function.Function<DerEncoder, byte[]> enc) {
        System.out.println(title);
        byte[] expected = facade.get();
        for (String n : new String[] {"compact"}) {
            byte[] got = enc.apply(new CompactDerEncoder());
            if (!Arrays.equals(expected, got)) throw new AssertionError(title + ": " + n + " differs from the façade");
        }
        measure("DerWriter facade", facade::get, iters);
        measure("DerEncoder compact", () -> enc.apply(new CompactDerEncoder()), iters);
        // steady state: one encoder reused across operations, as a stream codec would
        CompactDerEncoder reused = new CompactDerEncoder();
        measure("compact, reused", () -> { reused.reset(); return enc.apply(reused); }, iters);
    }

    public static void main(String[] args) {
        workload("64 KiB blob nested 7 deep", 3000, DerEncoderMicroBench::blobFacade, DerEncoderMicroBench::blobWith);
        workload("batch100-like (100 x 4 fields)", 3000, DerEncoderMicroBench::batchFacade, DerEncoderMicroBench::batchWith);
        workload("small record (4 fields)", 200000, DerEncoderMicroBench::smallFacade, DerEncoderMicroBench::smallWith);
        workload("sorted set of 100 strings", 5000, DerEncoderMicroBench::setFacade, DerEncoderMicroBench::setWith);
    }
}
