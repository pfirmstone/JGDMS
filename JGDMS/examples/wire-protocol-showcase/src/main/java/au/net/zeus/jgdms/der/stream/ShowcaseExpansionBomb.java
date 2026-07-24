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

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Showcase bridge to the library's reconstitution-bomb defence. Lives in
 * {@code au.net.zeus.jgdms.der.stream} so it can drive the package-private
 * {@link StreamSchemaDedup} with a deliberately SMALL input budget -- the "bounded
 * reproduction" discipline: a small budget makes the ceiling small, so the reader's
 * refusal fires after a few dozen re-materialisations (having buffered well under the
 * budget) instead of after the gigabytes the default budget would permit. This is the
 * same construction the library's own adversarial tests use
 * ({@code StreamSchemaDedupReconstitutionBombTest},
 * {@code StreamSchemaDedupCumulativeWorkBoundTest}); it is reproduced here, not imported,
 * because those test classes are not on the showcase's classpath.
 *
 * <p>Everything this class exposes to the demonstration and its automated check is public;
 * only the input construction reaches into library internals, and it constructs the
 * hostile input BY BYTES -- it never asks the library to build a bomb for it.
 */
public final class ShowcaseExpansionBomb {

    private ShowcaseExpansionBomb() {}

    /** The library's per-item expansion factor (ceiling = input budget x this). */
    public static final int PER_ITEM_FACTOR = StreamSchemaDedup.RECONSTITUTION_EXPANSION_FACTOR;
    /** The library's cumulative expansion factor (ceiling = input budget x this). */
    public static final int CUMULATIVE_FACTOR = StreamSchemaDedup.STREAM_RECONSTITUTION_EXPANSION_FACTOR;
    /** The library's default input budget (the normal deployment posture), in bytes. */
    public static final int DEFAULT_INPUT_BUDGET = 16 * 1024 * 1024;

    private static final Tag FULL = new Tag(Tag.CLASS_CONTEXT, false, 0);  // first, full shape
    private static final Tag REF  = new Tag(Tag.CLASS_CONTEXT, false, 1);  // later, a back-reference

    // ---- Result of the single-message (peak-memory) bomb -------------------------------

    /** Outcome of the single hostile message that re-expands one big shape at every reference. */
    public static final class SingleMessageResult {
        public final boolean refused;
        public final String refusalDetail;
        public final int wireInputBytes;        // the whole hostile message, on the wire
        public final int perReferenceWireBytes; // one tiny back-reference, on the wire
        public final int perReferenceExpandedBytes; // what that one reference re-materialises to
        public final double amplification;      // expanded / wire, per reference
        public final long memoryCeilingBytes;   // the reader's per-message ceiling
        public final long expandedAtRefusalBytes; // how far expansion had reached when refused
        public final long projectedAtDefaultBudgetBytes; // same trick at the normal input limit

        SingleMessageResult(boolean refused, String refusalDetail, int wireInputBytes,
                            int perReferenceWireBytes, int perReferenceExpandedBytes,
                            long memoryCeilingBytes, long expandedAtRefusalBytes) {
            this.refused = refused;
            this.refusalDetail = refusalDetail;
            this.wireInputBytes = wireInputBytes;
            this.perReferenceWireBytes = perReferenceWireBytes;
            this.perReferenceExpandedBytes = perReferenceExpandedBytes;
            this.amplification = (double) perReferenceExpandedBytes / perReferenceWireBytes;
            this.memoryCeilingBytes = memoryCeilingBytes;
            this.expandedAtRefusalBytes = expandedAtRefusalBytes;
            this.projectedAtDefaultBudgetBytes = (long) (amplification * DEFAULT_INPUT_BUDGET);
        }
    }

    /** Outcome of the many-message stream that keeps each message small but never stops. */
    public static final class ManyMessageResult {
        public final boolean refused;
        public final String refusalDetail;
        public final int messagesAccepted;      // how many small messages went through first
        public final long perMessagePeakBytes;  // the reader's memory use for the LAST message
        public final long cumulativeCeilingBytes;
        public final long cumulativeAtRefusalBytes;

        ManyMessageResult(boolean refused, String refusalDetail, int messagesAccepted,
                          long perMessagePeakBytes, long cumulativeCeilingBytes,
                          long cumulativeAtRefusalBytes) {
            this.refused = refused;
            this.refusalDetail = refusalDetail;
            this.messagesAccepted = messagesAccepted;
            this.perMessagePeakBytes = perMessagePeakBytes;
            this.cumulativeCeilingBytes = cumulativeCeilingBytes;
            this.cumulativeAtRefusalBytes = cumulativeAtRefusalBytes;
        }
    }

    /**
     * Drives one hostile message: a single big shape, then a run of tiny back-references,
     * each of which would re-materialise the whole shape. The reader stops at its
     * per-message memory ceiling; nothing near the full expansion is ever buffered.
     *
     * @param inputBudget the (small, for a bounded demonstration) input budget in bytes
     * @param shapeBytes  the size of the one shared shape
     * @param references  how many back-references to pack after it
     */
    public static SingleMessageResult runSingleMessageBomb(int inputBudget, int shapeBytes,
                                                           int references) throws Exception {
        byte[] shape = shapeOfExactSize("BalloonShape", shapeBytes);
        byte[] digest = sha256(shape);
        byte[] message = valueArray(withReferences(shape, digest, references));

        int refWire = referenceElement(digest).length;
        int refExpanded = DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(shape),
                DerWriter.writeOctetString(emptyPayload()))).length;

        StreamSchemaDedup reader = new StreamSchemaDedup(false, inputBudget);
        long ceiling = (long) inputBudget * PER_ITEM_FACTOR;
        boolean refused = false;
        String detail = "";
        try {
            reader.reconstituteTopLevelArray(message);
        } catch (DerException e) {
            refused = e.getMessage() != null && e.getMessage().contains("maxReconstitutedBytes");
            detail = firstSentence(e.getMessage());
        }
        long expandedAtRefusal = reader.reconstitutedChainBytes();
        return new SingleMessageResult(refused, detail, message.length,
                refWire, refExpanded, ceiling, expandedAtRefusal);
    }

    /**
     * Drives a never-ending stream: each message re-materialises just one shape (small, well
     * under the per-message ceiling), but the messages keep coming. Peak memory stays flat --
     * the reader releases each message's buffer before the next -- yet the reader still stops
     * the flood, at a separate cumulative ceiling, before the accumulated work runs away.
     *
     * @param inputBudget the (small, for a bounded demonstration) input budget in bytes
     * @param shapeBytes  the size of the one shared shape re-materialised each message
     */
    public static ManyMessageResult runManyMessageBomb(int inputBudget, int shapeBytes) throws Exception {
        byte[] shape = shapeOfExactSize("FloodShape", shapeBytes);
        byte[] digest = sha256(shape);
        StreamSchemaDedup reader = new StreamSchemaDedup(false, inputBudget);
        // First message seeds the shared shape.
        reader.reconstituteTopLevelArray(valueArray(List.of(fullElement(shape))));

        byte[] oneReferenceMessage = valueArray(List.of(referenceElement(digest)));
        int accepted = 0;
        boolean refused = false;
        String detail = "";
        long lastPeak = 0;
        for (int i = 0; i < 1_000_000 && !refused; i++) {
            try {
                reader.reconstituteTopLevelArray(oneReferenceMessage);
                accepted++;
                lastPeak = reader.reconstitutedChainBytes(); // resets each message -> stays flat
            } catch (DerException e) {
                refused = e.getMessage() != null
                        && e.getMessage().contains("maxStreamReconstitutedBytes");
                detail = firstSentence(e.getMessage());
            }
        }
        return new ManyMessageResult(refused, detail, accepted, lastPeak,
                reader.maxStreamReconstitutedChainBytes(),
                reader.streamReconstitutedChainBytes());
    }

    // ---- Input construction (by bytes; mirrors the library's own adversarial tests) ------

    private static List<byte[]> withReferences(byte[] shape, byte[] digest, int references) {
        List<byte[]> elements = new ArrayList<>(references + 1);
        elements.add(fullElement(shape));                 // first occurrence carries the full shape
        for (int i = 0; i < references; i++) {
            elements.add(referenceElement(digest));       // each is a tiny back-reference
        }
        return elements;
    }

    private static byte[] valueArray(List<byte[]> elements) {
        byte[] wireType = DerWriter.writeUtf8String("array:@AtomicSerial:Foo");
        byte[] sequence = DerWriter.writeSequence(elements);
        byte[] out = new byte[wireType.length + sequence.length];
        System.arraycopy(wireType, 0, out, 0, wireType.length);
        System.arraycopy(sequence, 0, out, wireType.length, sequence.length);
        return out;
    }

    private static byte[] fullElement(byte[] shape) {
        return DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(FULL, shape),
                DerWriter.writeOctetString(emptyPayload())));
    }

    private static byte[] referenceElement(byte[] digest) {
        return DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(REF, digest),
                DerWriter.writeOctetString(emptyPayload())));
    }

    private static byte[] emptyPayload() {
        return DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of())));
    }

    private static byte[] shape(String className, int fieldCount, int classPad) {
        List<AtomicSerialFieldDef> fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new AtomicSerialFieldDef(
                    "f" + String.format("%03d", i) + "x".repeat(196), "int"));
        }
        return new AtomicSerialSchemaRecord(className + "z".repeat(classPad), fields).encode();
    }

    /** Builds one shape whose encoded length is EXACTLY {@code target} bytes. */
    private static byte[] shapeOfExactSize(String className, int target) {
        int maxPad = 1000 - className.length();
        int k = 0;
        for (int guard = 0; guard < 4096; guard++) {
            int probe = shape(className, k, 0).length;
            int need = target - probe;
            if (need < 0) break;
            if (need <= maxPad + 4) {
                for (int adj = Math.max(0, need - 4); adj <= Math.min(maxPad, need + 4); adj++) {
                    byte[] out = shape(className, k, adj);
                    if (out.length == target) return out;
                }
                break;
            }
            k += Math.max(1, (need - 900) / 211);
        }
        throw new AssertionError("cannot size the shape to " + target + " bytes");
    }

    private static byte[] sha256(byte[] b) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(b);
    }

    private static String firstSentence(String message) {
        if (message == null) return "(no detail)";
        int dash = message.indexOf(" — ");
        String head = dash > 0 ? message.substring(0, dash) : message;
        return head.length() > 140 ? head.substring(0, 140) + "..." : head;
    }
}
