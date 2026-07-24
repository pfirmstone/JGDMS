/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.outrigger.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.zip.Deflater;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.entry.AbstractEntry;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The U1c <b>bulk-response wire-size probe</b>
 * ({@code SOW-Outrigger-DER-Only-JOSS-Rejection.md} sec.3 item 8 / sec.7,
 * informing sec.9.4): measures the bytes-on-the-wire of an N-entry MatchSet
 * batch (the {@code OutriggerServer.nextBatch}/{@code contents} response
 * payload, an {@code EntryRep[]}) and prints a comparison table -- turning
 * board seat 3's estimated regression numbers into measured ones.
 *
 * <p>Columns measured (per entry fixture, N = 100 -- the dedup SOW's
 * representative batch shape):
 * <ol>
 * <li><b>JOSS</b> -- the pre-U1a wire: {@link AtomicMarshalOutputStream}
 *     (the atomic-JOSS stream {@code AtomicILFactory} writes) carrying
 *     {@code EntryRep}s whose per-field {@code MarshalledInstance}s hold
 *     JOSS payloads. Codebase annotations are NOT written (conservative:
 *     favors the JOSS baseline).</li>
 * <li><b>JOSS+DEFLATE</b> -- column 1 deflated at the default level: the
 *     historical {@code jsse}/{@code spiffe} config wire
 *     ({@code Compression.DEFLATE}, withdrawn on encrypted transports by
 *     ratified decision 6).</li>
 * <li><b>DER per-occurrence</b> -- N independent single-entry DER streams
 *     summed: a proxy for the superseded pre-dedup DER wire, where every
 *     stream (and so every occurrence) re-carries the full schema chains
 *     (each stream also re-pays the 3-byte version octet; negligible at
 *     these sizes).</li>
 * <li><b>DER deduped (current)</b> -- one {@code DerMarshalOutputStream}
 *     carrying the whole {@code EntryRep[]}: the wire
 *     {@code AtomicDerILFactory} actually writes on this trunk. Since the
 *     STD-006 Appendix C stream schema dedup (T2) is merged, this number
 *     is the <b>de-facto dedup-era measurement</b> and satisfies the
 *     Outrigger-batch portion of {@code SOW-DER-Stream-Schema-Dedup.md}
 *     T4's three-way comparison.</li>
 * </ol>
 *
 * <p>Fixtures mirror the U0 baseline document's
 * ({@code U0-Outrigger-JOSS-Persistence-Baselines-2026-07-21.md}):
 * {@code simple} (scalar/String fields only -- the empty-schema path) and
 * {@code custom} (a field holding a custom {@code @AtomicSerial} value --
 * the schema-carrying case the dedup exists for).
 *
 * <p>The DER writer is reached reflectively
 * ({@code au.net.zeus.jgdms.der.stream.DerMarshalOutputStream}, cast to
 * {@link ObjectOutput}): jgdms-der is a release-25 module on this module's
 * <em>test runtime</em> classpath only, and this source tree compiles at
 * release 8 (see pom.xml).
 */
public class MatchSetWireSizeProbeTest {

    private static final int N = 100;

    /** Simple fixture: scalar/String fields only (empty-schema DER path). */
    public static class OrderEntry extends AbstractEntry {
        public String customer;
        public String product;
        public Integer quantity;
        public Integer priority;
        public OrderEntry() { }
        public OrderEntry(String customer, String product,
                          Integer quantity, Integer priority) {
            this.customer = customer;
            this.product = product;
            this.quantity = quantity;
            this.priority = priority;
        }
    }

    /** Custom {@code @AtomicSerial} field value: the schema-carrying case. */
    @AtomicSerial
    public static class Stamp implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String origin;
        private final long time;

        public Stamp(String origin, long time) {
            this.origin = origin;
            this.time = time;
        }

        public Stamp(GetArg arg) throws IOException, ClassNotFoundException {
            this.origin = (String) arg.get("origin", null);
            this.time = arg.get("time", 0L);
        }

        public static SerialForm[] serialForm() {
            return new SerialForm[] {
                new SerialForm("origin", String.class),
                new SerialForm("time", long.class),
            };
        }

        public static void serialize(PutArg arg, Stamp s) throws IOException {
            arg.put("origin", s.origin);
            arg.put("time", s.time);
            arg.writeArgs();
        }

        public boolean equals(Object o) {
            if (!(o instanceof Stamp)) return false;
            Stamp other = (Stamp) o;
            return time == other.time
                && (origin == null ? other.origin == null
                                   : origin.equals(other.origin));
        }

        public int hashCode() {
            return (origin == null ? 0 : origin.hashCode()) * 31
                 + (int) (time ^ (time >>> 32));
        }
    }

    /** Custom fixture: one field is a custom {@code @AtomicSerial} value. */
    public static class StampedOrderEntry extends AbstractEntry {
        public String customer;
        public Integer quantity;
        public Stamp stamp;
        public StampedOrderEntry() { }
        public StampedOrderEntry(String customer, Integer quantity,
                                 Stamp stamp) {
            this.customer = customer;
            this.quantity = quantity;
            this.stamp = stamp;
        }
    }

    // ---------------------------------------------------------------------
    // Population builders (values cycled, mirroring U0)
    // ---------------------------------------------------------------------

    private static EntryRep[] simpleBatch(MarshallingFormat format)
            throws IOException {
        EntryRep[] batch = new EntryRep[N];
        for (int i = 0; i < N; i++) {
            batch[i] = new EntryRep(new OrderEntry(
                "customer-" + (i % 7), "product-" + (i % 13),
                Integer.valueOf(i % 90), Integer.valueOf(i % 5)), format);
        }
        return batch;
    }

    private static EntryRep[] customBatch(MarshallingFormat format)
            throws IOException {
        EntryRep[] batch = new EntryRep[N];
        for (int i = 0; i < N; i++) {
            batch[i] = new EntryRep(new StampedOrderEntry(
                "customer-" + (i % 7), Integer.valueOf(i % 90),
                new Stamp("origin-" + (i % 3), 1700000000000L + i)), format);
        }
        return batch;
    }

    // ---------------------------------------------------------------------
    // Stream writers
    // ---------------------------------------------------------------------

    /** The current DER wire: one stream, whole batch (dedup-era). */
    private static int derStreamBytes(Object... items) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = newDerMarshalOutputStream(bos);
        for (Object item : items) {
            out.writeObject(item);
        }
        out.flush();
        out.close();
        return bos.size();
    }

    private static ObjectOutput newDerMarshalOutputStream(OutputStream bos)
            throws Exception {
        Class<?> cls = Class.forName(
            "au.net.zeus.jgdms.der.stream.DerMarshalOutputStream");
        Constructor<?> ctor = cls.getConstructor(OutputStream.class);
        return (ObjectOutput) ctor.newInstance(bos);
    }

    /** Default-level DEFLATE of a JOSS stream (historical jsse/spiffe wire). */
    private static int deflatedSize(byte[] data) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(data);
            deflater.finish();
            byte[] buf = new byte[8192];
            int total = 0;
            while (!deflater.finished()) {
                total += deflater.deflate(buf);
            }
            return total;
        } finally {
            deflater.end();
        }
    }

    private static byte[] jossStream(Object item) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        AtomicMarshalOutputStream out =
            new AtomicMarshalOutputStream(bos, Collections.EMPTY_LIST);
        out.writeObject(item);
        out.flush();
        out.close();
        return bos.toByteArray();
    }

    // ---------------------------------------------------------------------
    // The probe
    // ---------------------------------------------------------------------

    private void probe(String label, EntryRep[] derBatch, EntryRep[] jossBatch)
            throws Exception {
        // Column 4: current wire -- one DER stream, whole batch.
        int derDeduped = derStreamBytes((Object) derBatch);

        // Column 3: per-occurrence proxy -- N independent one-entry streams.
        long derPerOccurrence = 0;
        for (EntryRep rep : derBatch) {
            derPerOccurrence += derStreamBytes(rep);
        }

        // Columns 1+2: atomic-JOSS wire, plain and deflated. Soft-fail:
        // if this trunk can no longer produce the superseded wire shape,
        // report it and keep the DER measurements (the deliverable).
        // The extra per-occurrence JOSS column separates java
        // serialization's IN-STREAM back-reference sharing (repeated
        // String values and class descriptors written once per stream,
        // then handle-referenced) from per-entry encoding cost: the
        // one-stream JOSS number benefits from sharing DER canonical
        // encoding deliberately does not have.
        long jossPlain = -1, jossDeflate = -1, jossPerOccurrence = -1;
        String jossNote = "";
        try {
            byte[] joss = jossStream(jossBatch);
            jossPlain = joss.length;
            jossDeflate = deflatedSize(joss);
            jossPerOccurrence = 0;
            for (EntryRep rep : jossBatch) {
                jossPerOccurrence += jossStream(rep).length;
            }
        } catch (Throwable t) {
            jossNote = " [JOSS leg unmeasurable on this trunk: " + t + "]";
        }

        System.out.printf(
            "[wire-size] %s N=%d MatchSet batch: JOSS one-stream=%d B "
            + "(%.1f B/entry), JOSS per-occurrence=%d B (%.1f B/entry), "
            + "JOSS+DEFLATE=%d B (%.1f B/entry), DER per-occurrence=%d B "
            + "(%.1f B/entry), DER deduped (current wire)=%d B "
            + "(%.1f B/entry)%s%n",
            label, N,
            jossPlain, jossPlain / (double) N,
            jossPerOccurrence, jossPerOccurrence / (double) N,
            jossDeflate, jossDeflate / (double) N,
            derPerOccurrence, derPerOccurrence / (double) N,
            derDeduped, derDeduped / (double) N,
            jossNote);

        assertTrue("the DER batch stream must be non-empty", derDeduped > 0);
        assertTrue(
            "the deduped batch stream must beat the per-occurrence proxy "
            + "(all " + N + " EntryReps share schema chains)",
            derDeduped < derPerOccurrence);
    }

    @Test
    public void simpleEntryBatchWireSizes() throws Exception {
        probe("simple",
              simpleBatch(MarshallingFormat.ATOMIC_DER),
              simpleBatch(MarshallingFormat.JOSS));
    }

    @Test
    public void customAtomicSerialEntryBatchWireSizes() throws Exception {
        probe("custom",
              customBatch(MarshallingFormat.ATOMIC_DER),
              customBatch(MarshallingFormat.JOSS));
    }
}
