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

import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A3 micro-benchmark: the frozen byte[]-composition dedup walker ({@link StreamSchemaDedupReference}) vs the migrated
 * walker, both directions, time AND allocation per operation. Not a test; run
 * {@code java -cp target/classes:target/test-classes:<deps> au.net.zeus.jgdms.der.stream.StreamSchemaDedupMicroBench}.
 * One operation = a fresh dedup instance processing one canonical (or stream-form) record, which is what a one-object
 * stream costs.
 */
public final class StreamSchemaDedupMicroBench {

    interface Work { void run() throws Exception; }

    private static final com.sun.management.ThreadMXBean MX =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    static void measure(String label, Work w) throws Exception {
        for (int i = 0; i < 20_000; i++) w.run();
        double[] ns = new double[9];
        double[] alloc = new double[9];
        int n = 20_000;
        for (int r = 0; r < ns.length; r++) {
            long a0 = MX.getCurrentThreadAllocatedBytes();
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) w.run();
            long t1 = System.nanoTime();
            ns[r] = (double) (t1 - t0) / n;
            alloc[r] = (double) (MX.getCurrentThreadAllocatedBytes() - a0) / n;
        }
        Arrays.sort(ns);
        Arrays.sort(alloc);
        System.out.printf("%-34s %9.0f ns/op %10.0f B/op%n", label, ns[ns.length / 2], alloc[alloc.length / 2]);
    }

    public static void main(String[] args) throws Exception {
        Map<String, Supplier<Object>> corpus = DerGoldenBytesTest.corpus();
        List<String> names = List.of("simple-record", "nested-array", "rec-set", "rec-map", "deep-chain-max-nesting",
                "chain-array-holder", "bench-batch100");
        List<Object[]> work = new ArrayList<>();
        for (String name : names) {
            Object o = corpus.get(name).get();
            SchemaChain.Result chain = SchemaGenerator.generateChain(o.getClass());
            byte[] canonical = MarshalledInstanceRecord.fromChain(chain, ObjectCodec.encodeHierarchy(o, chain)).encode();
            work.add(new Object[] {name, canonical, o.getClass()});
        }
        for (Object[] w : work) {
            String name = (String) w[0];
            byte[] canonical = (byte[]) w[1];
            Class<?> cls = (Class<?>) w[2];
            byte[] stream = new StreamSchemaDedupReference(true).dedupTopLevelAtomic(canonical, cls);
            System.out.printf("-- %s: canonical %d B, stream %d B%n", name, canonical.length, stream.length);
            measure("  encode reference", () -> new StreamSchemaDedupReference(true).dedupTopLevelAtomic(canonical, cls));
            measure("  encode migrated", () -> new StreamSchemaDedup(true).dedupTopLevelAtomic(canonical, cls));
            measure("  decode reference", () -> new StreamSchemaDedupReference(false).reconstituteTopLevelAtomicVerified(stream));
            measure("  decode migrated", () -> new StreamSchemaDedup(false).reconstituteTopLevelAtomicVerified(stream));
        }
    }
}
