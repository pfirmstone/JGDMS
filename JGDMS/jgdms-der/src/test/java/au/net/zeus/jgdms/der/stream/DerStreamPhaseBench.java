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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * A4 pre-step (SOW-DER-Streaming-Encoder): time the phases of one stream round trip SEPARATELY, so the question
 * "is {@code drainTo} / input buffering a material part of a JERI call?" has a measured answer. Phases per
 * operation: (1) encode = construct codec + writeObject, (2) drain = {@code drainTo} into a ByteArrayOutputStream (what
 * flush() does), (3) buffer = {@code readAllBytesBounded} over the bytes, (4) decode = initReader + readObject.
 * Not a test: run {@code java -cp target/classes:target/test-classes:<deps> au.net.zeus.jgdms.der.stream.DerStreamPhaseBench}.
 */
public final class DerStreamPhaseBench {

    interface Work { void run() throws Exception; }

    private static final com.sun.management.ThreadMXBean MX =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    static double[] measure(Work w, int n) throws Exception {
        for (int i = 0; i < n; i++) w.run();
        double[] ns = new double[9], alloc = new double[9];
        for (int r = 0; r < ns.length; r++) {
            long a0 = MX.getCurrentThreadAllocatedBytes();
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) w.run();
            ns[r] = (double) (System.nanoTime() - t0) / n;
            alloc[r] = (double) (MX.getCurrentThreadAllocatedBytes() - a0) / n;
        }
        Arrays.sort(ns);
        Arrays.sort(alloc);
        return new double[] {ns[4], alloc[4]};
    }

    public static void main(String[] args) throws Exception {
        java.util.Map<String, Supplier<Object>> corpus = DerGoldenBytesTest.corpus();
        DerInputLimits limits = DerInputLimits.DEFAULT;
        System.out.printf("%-16s %-8s %10s %10s%n", "payload", "phase", "ns/op", "B/op");
        for (String name : new String[] {"bench-record", "bench-batch100", "bench-blob64k"}) {
            Object o = corpus.get(name).get();
            int n = name.equals("bench-record") ? 20000 : 3000;
            // a codec with the object written but not yet drained, rebuilt per op for the drain measurement
            byte[][] wire = new byte[1][];
            double[] enc = measure(() -> { DerObjectStreamCodec c = new DerObjectStreamCodec(); c.writeObject(o); }, n);
            double[] encDrain = measure(() -> {
                DerObjectStreamCodec c = new DerObjectStreamCodec();
                c.writeObject(o);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                c.drainTo(bos);
                wire[0] = bos.toByteArray();
            }, n);
            byte[] bytes = wire[0];
            double[] buf = measure(() -> limits.readAllBytesBounded(new ByteArrayInputStream(bytes)), n);
            double[] dec = measure(() -> {
                DerObjectStreamCodec c = new DerObjectStreamCodec();
                c.initReader(bytes);
                c.readObject();
            }, n);
            System.out.printf("%-16s %-8s %10.0f %10.0f   (%d wire bytes)%n", name, "encode", enc[0], enc[1], bytes.length);
            System.out.printf("%-16s %-8s %10.0f %10.0f%n", name, "drain", Math.max(0, encDrain[0] - enc[0]), Math.max(0, encDrain[1] - enc[1]));
            System.out.printf("%-16s %-8s %10.0f %10.0f%n", name, "buffer", buf[0], buf[1]);
            System.out.printf("%-16s %-8s %10.0f %10.0f%n", name, "decode", dec[0], dec[1]);
        }
    }
}
