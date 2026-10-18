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
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.fixtures.CollectionRecord;
import au.net.zeus.jgdms.der.object.fixtures.Foo;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A3 oracle: the offset-based, single-encoder {@link StreamSchemaDedup} walker against the frozen
 * byte[]-composition walker it replaced ({@link StreamSchemaDedupReference}), in both directions.
 *
 * <p>Two properties, both required of a refactor of the security spine ("the change is how bytes
 * move, not what is decided"):
 * <ol>
 *   <li><b>Same bytes</b> on every corpus input: the dedup transform, the reconstitution, the verified chain
 *       handed back, and the table / ceiling counters after each item.</li>
 *   <li><b>Same verdict</b> on mutated inputs: for thousands of structure-aware and byte-level mutations of
 *       valid records (chain-site tags changed, fields dropped/duplicated/swapped, sub-trees grafted, lengths
 *       perturbed, content truncated), both walkers accept with identical output, or both reject with the same
 *       message. The only normalisation is the digits in a message: positions and lengths in a reader's
 *       diagnostics are offsets into the buffer it was handed, and the old walker handed every sub-TLV a
 *       fresh copy.</li>
 * </ol>
 */
class StreamSchemaDedupEquivalenceTest {

    // ------------------------------------------------------------------ inputs

    /** One walker input: a canonical P1 record, or the content of a [9] top-level array. */
    private record Item(String name, boolean array, byte[] canonical, Class<?> cls) {}

    private static byte[] canonicalRecordOf(Object obj) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(obj.getClass());
        return MarshalledInstanceRecord.fromChain(chain, ObjectCodec.encodeHierarchy(obj, chain)).encode();
    }

    private static void collect(String name, Object sample, List<Item> out) throws Exception {
        if (sample instanceof DerGoldenBytesTest.MixedStream m) {
            int i = 0;
            for (Object o : m.items) collect(name + "#" + i++, o, out);
        } else if (sample instanceof DerGoldenBytesTest.TopLevelCollection) {
            // [16] is outside the dedup layer
        } else if (sample != null && sample.getClass().isArray() && !(sample instanceof byte[])
                && SchemaGenerator.toWireType(sample.getClass(), sample.getClass()).startsWith("array:")) {
            out.add(new Item(name, true, ObjectCodec.encodeTopLevelArray(sample), null));
        } else if (sample != null && sample.getClass().isAnnotationPresent(AtomicSerial.class)) {
            out.add(new Item(name, false, canonicalRecordOf(sample), sample.getClass()));
        }
        // scalars, enums, bare proxies, byte[]: not dedup inputs
    }

    /** A record whose single field is an {@code any}-typed collection (the [20]/[30]/[31] arms). */
    private static Item anyItem(String name, String token, Object value) throws Exception {
        AtomicSerialSchemaRecord rec = new AtomicSerialSchemaRecord(
                CollectionRecord.class.getName(), (byte[]) null, List.of(new AtomicSerialFieldDef("coll", token)));
        SchemaChain.Result chain = SchemaChain.linkAndGetLeafDigest(List.of(rec));
        AtomicSerialSchemaRecord linked = chain.chain().get(0);
        byte[] classSeq = ObjectCodec.encode(new CollectionRecord(value), CollectionRecord.class, linked);
        byte[] payload = DerWriter.writeSequence(List.of(classSeq));
        byte[] canonical = MarshalledInstanceRecord.fromChain(chain, payload).encode();
        return new Item(name, false, canonical, CollectionRecord.class);
    }

    // ------------------------------------------------------------------ a hierarchy with chain sites at TWO levels

    /** Root class of a two-level hierarchy; its fields hold chain sites (a record and a list of records). */
    @AtomicSerial
    public static class SiteBase {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("baseFoo", Foo.class),
                new AtomicSerial.SerialForm("baseList", List.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, SiteBase o) throws java.io.IOException {
            arg.put("baseFoo", o.baseFoo);
            arg.put("baseList", o.baseList);
            arg.writeArgs();
        }
        final Foo baseFoo;
        final List<Foo> baseList;
        public SiteBase(Foo baseFoo, List<Foo> baseList) { this.baseFoo = baseFoo; this.baseList = baseList; }
        @SuppressWarnings("unchecked")
        public SiteBase(AtomicSerial.GetArg arg) throws java.io.IOException, ClassNotFoundException {
            this.baseFoo = (Foo) arg.get("baseFoo", null);
            this.baseList = new ArrayList<>((List<Foo>) arg.get("baseList", null));
        }
    }

    /** Subclass adding its own chain sites (a record and a map of records). */
    @AtomicSerial
    public static class SiteDerived extends SiteBase {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("derivedFoo", Foo.class),
                new AtomicSerial.SerialForm("derivedMap", Map.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, SiteDerived o) throws java.io.IOException {
            arg.put("derivedFoo", o.derivedFoo);
            arg.put("derivedMap", o.derivedMap);
            arg.writeArgs();
        }
        final Foo derivedFoo;
        final Map<String, Foo> derivedMap;
        public SiteDerived(Foo bf, List<Foo> bl, Foo df, Map<String, Foo> dm) {
            super(bf, bl);
            this.derivedFoo = df;
            this.derivedMap = dm;
        }
        @SuppressWarnings("unchecked")
        public SiteDerived(AtomicSerial.GetArg arg) throws java.io.IOException, ClassNotFoundException {
            super(arg);
            this.derivedFoo = (Foo) arg.get("derivedFoo", null);
            this.derivedMap = new LinkedHashMap<>((Map<String, Foo>) arg.get("derivedMap", null));
        }
    }

    private static List<Item> items() throws Exception {
        List<Item> out = new ArrayList<>();
        for (Map.Entry<String, Supplier<Object>> e : DerGoldenBytesTest.corpus().entrySet()) {
            if (DerGoldenBytesTest.SINGLE_ONLY.contains(e.getKey())) continue;
            collect(e.getKey(), e.getValue().get(), out);
        }
        Map<String, Foo> dm = new LinkedHashMap<>();
        dm.put("m1", new Foo(7, "m"));
        dm.put("m2", new Foo(8, "n"));
        collect("two-level-sites", new SiteDerived(new Foo(1, "a"), new ArrayList<>(List.of(new Foo(2, "b"), new Foo(1, "a"))),
                new Foo(3, "c"), dm), out);
        String listAny = CollectionWireTypes.listToken("any");
        String setAny = CollectionWireTypes.setToken("any");
        String mapAny = CollectionWireTypes.mapToken("any", "any");
        List<Object> mixed = new ArrayList<>(List.of(new Foo(1, "a"), true, 7, "s", new Foo(2, "b")));
        mixed.add(null);
        mixed.add(new ArrayList<>(List.of(new Foo(3, "c"), new Foo(1, "a"))));
        out.add(anyItem("any-list-mixed", listAny, mixed));
        // [30]/[31] arms whose content lengths straddle the 127/128 and 255/256 length-octet boundaries
        for (int len : new int[] {120, 126, 127, 128, 250, 255, 256, 300}) {
            out.add(anyItem("any-list-len-" + len, listAny,
                    new ArrayList<Object>(List.of("x".repeat(len), new Foo(len, "f")))));
            out.add(anyItem("any-set-len-" + len, setAny,
                    new java.util.LinkedHashSet<Object>(List.of(new byte[len], new Foo(len, "g")))));
        }
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put("k1", new Foo(1, "a"));
        m.put("k2", new Foo(1, "a"));
        m.put(5, new ArrayList<>(List.of(new Foo(9, "z"))));
        out.add(anyItem("any-map", mapAny, m));
        out.add(anyItem("any-set", setAny, new java.util.LinkedHashSet<Object>(List.of(new Foo(1, "q"), new Foo(2, "r"), "x"))));
        return out;
    }

    // ------------------------------------------------------------------ the two walkers behind one face

    private record Decoded(byte[] record, byte[] leafDigest, List<AtomicSerialSchemaRecord> chain) {}

    private interface Walker {
        byte[] encodeP1(byte[] canonical, Class<?> cls) throws DerException;
        byte[] encodeArray(byte[] content) throws DerException;
        Decoded decodeP1(byte[] streamRecord) throws DerException;
        byte[] decodeArray(byte[] content) throws DerException;
        long[] state();
    }

    private static Walker reference(boolean encoding) {
        StreamSchemaDedupReference w = new StreamSchemaDedupReference(encoding);
        return new Walker() {
            public byte[] encodeP1(byte[] c, Class<?> k) throws DerException { return w.dedupTopLevelAtomic(c, k); }
            public byte[] encodeArray(byte[] c) throws DerException { return w.dedupTopLevelArray(c); }
            public Decoded decodeP1(byte[] s) throws DerException {
                StreamSchemaDedupReference.Reconstituted r = w.reconstituteTopLevelAtomicVerified(s);
                return new Decoded(r.recordBytes(), r.topChain().leafDigest(), r.topChain().chain());
            }
            public byte[] decodeArray(byte[] c) throws DerException { return w.reconstituteTopLevelArray(c); }
            public long[] state() {
                return new long[] {w.distinctChains(), w.reconstitutedChainBytes(), w.streamReconstitutedChainBytes()};
            }
        };
    }

    private static Walker migrated(boolean encoding) {
        StreamSchemaDedup w = new StreamSchemaDedup(encoding);
        return new Walker() {
            public byte[] encodeP1(byte[] c, Class<?> k) throws DerException { return w.dedupTopLevelAtomic(c, k); }
            public byte[] encodeArray(byte[] c) throws DerException { return w.dedupTopLevelArray(c); }
            public Decoded decodeP1(byte[] s) throws DerException {
                StreamSchemaDedup.Reconstituted r = w.reconstituteTopLevelAtomicVerified(s);
                assertTrue(w.materialisedChainBytes() <= w.streamReconstitutedChainBytes(),
                        "appended chain bytes must never exceed the metered total");
                return new Decoded(r.recordBytes(), r.topChain().leafDigest(), r.topChain().chain());
            }
            public byte[] decodeArray(byte[] c) throws DerException { return w.reconstituteTopLevelArray(c); }
            public long[] state() {
                return new long[] {w.distinctChains(), w.reconstitutedChainBytes(), w.streamReconstitutedChainBytes()};
            }
        };
    }

    /** What a walker did with one input. */
    private record Outcome(byte[] bytes, Decoded decoded, String error, long[] state) {
        static Outcome of(Walker w, java.util.concurrent.Callable<Object> op) {
            try {
                Object r = op.call();
                if (r instanceof Decoded d) return new Outcome(d.record(), d, null, w.state());
                return new Outcome((byte[]) r, null, null, w.state());
            } catch (DerException e) {
                return new Outcome(null, null, "DerException: " + e.getMessage(), null);
            } catch (Throwable t) {
                return new Outcome(null, null, t.getClass().getName() + ": " + t.getMessage(), null);
            }
        }
    }

    private static String normalise(String message) {
        return message == null ? null : message.replaceAll("[0-9]+", "#");
    }

    /** Distinct reject messages (digits normalised) seen across the mutation runs, with counts. */
    private static final Map<String, Integer> REJECTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger ACCEPTS = new java.util.concurrent.atomic.AtomicInteger();

    private static void assertSame(Outcome ref, Outcome mig, String what) {
        if (ref.error != null) {
            REJECTS.merge(normalise(ref.error), 1, Integer::sum);
        } else {
            ACCEPTS.incrementAndGet();
        }
        if (ref.error != null || mig.error != null) {
            assertNotNull(ref.error, what + ": reference accepted but migrated rejected: " + mig.error);
            assertNotNull(mig.error, what + ": reference rejected (" + ref.error + ") but migrated accepted");
            assertEquals(normalise(ref.error), normalise(mig.error), what + ": different reject");
            return;
        }
        assertArrayEquals(ref.bytes, mig.bytes, what + ": output bytes differ");
        if (ref.decoded != null) {
            assertArrayEquals(ref.decoded.leafDigest, mig.decoded.leafDigest, what + ": verified chain digest");
            assertEquals(ref.decoded.chain, mig.decoded.chain, what + ": verified chain records");
        }
        assertArrayEquals(ref.state, mig.state, what + ": table / ceiling counters differ");
    }

    private static Outcome encodeOne(Walker w, Item it, byte[] input) {
        return Outcome.of(w, () -> it.array ? w.encodeArray(input) : w.encodeP1(input, it.cls));
    }

    private static Outcome decodeOne(Walker w, Item it, byte[] input) {
        return Outcome.of(w, () -> it.array ? (Object) w.decodeArray(input) : w.decodeP1(input));
    }

    // ------------------------------------------------------------------ 1. same bytes on the corpus

    @TestFactory
    Stream<DynamicTest> corpusEncodesAndReconstitutesIdentically() throws Exception {
        List<Item> all = items();
        assertTrue(all.size() >= 30, "corpus too small to mean anything: " + all.size());
        Map<String, List<Item>> byName = new LinkedHashMap<>();
        for (Item it : all) byName.computeIfAbsent(it.name.split("#")[0], k -> new ArrayList<>()).add(it);
        return byName.entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> {
            for (int copies : new int[] {1, 3}) {
                List<Item> seq = new ArrayList<>();
                for (int c = 0; c < copies; c++) seq.addAll(e.getValue());
                Walker refEnc = reference(true), migEnc = migrated(true);
                Walker refDec = reference(false), migDec = migrated(false);
                int n = 0;
                for (Item it : seq) {
                    String what = e.getKey() + " x" + copies + " item " + n++;
                    Outcome re = encodeOne(refEnc, it, it.canonical);
                    Outcome me = encodeOne(migEnc, it, it.canonical);
                    assertNull(re.error, what + ": the corpus must encode: " + re.error);
                    assertSame(re, me, what + " [encode]");
                    Outcome rd = decodeOne(refDec, it, re.bytes);
                    Outcome md = decodeOne(migDec, it, re.bytes);
                    assertNull(rd.error, what + ": the corpus must reconstitute: " + rd.error);
                    assertSame(rd, md, what + " [decode]");
                    assertArrayEquals(it.canonical, md.bytes, what + ": reconstitution is not the canonical record");
                }
            }
        }));
    }

    // ------------------------------------------------------------------ mutation machinery

    /** A parsed TLV tree node (definite lengths only). */
    private static final class Node {
        byte[] tag;
        byte[] content;      // leaves
        List<Node> kids;     // constructed, or an OCTET STRING / context primitive whose content parsed as TLVs
        boolean viaPrimitive;

        Node copy() {
            Node n = new Node();
            n.tag = tag.clone();
            n.content = content == null ? null : content.clone();
            n.viaPrimitive = viaPrimitive;
            if (kids != null) {
                n.kids = new ArrayList<>();
                for (Node k : kids) n.kids.add(k.copy());
            }
            return n;
        }

        byte[] encode() {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (kids != null) {
                for (Node k : kids) body.writeBytes(k.encode());
            } else {
                body.writeBytes(content);
            }
            byte[] b = body.toByteArray();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(tag);
            writeLength(out, b.length);
            out.writeBytes(b);
            return out.toByteArray();
        }
    }

    private static void writeLength(ByteArrayOutputStream o, int len) {
        if (len < 128) { o.write(len); return; }
        int n = len > 0xFFFFFF ? 4 : len > 0xFFFF ? 3 : len > 0xFF ? 2 : 1;
        o.write(0x80 | n);
        for (int i = n - 1; i >= 0; i--) o.write(len >>> (8 * i));
    }

    /** Parses {@code b[off,end)} as a sequence of TLVs covering it exactly, or returns null. */
    private static List<Node> parse(byte[] b, int off, int end, int depth) {
        if (depth > 40 || off >= end) return null;
        List<Node> out = new ArrayList<>();
        int p = off;
        while (p < end) {
            int start = p;
            int first = b[p++] & 0xFF;
            if ((first & 0x1F) == 0x1F) {
                while (true) {
                    if (p >= end) return null;
                    if ((b[p++] & 0x80) == 0) break;
                }
            }
            if (p >= end) return null;
            int l = b[p++] & 0xFF;
            long len;
            if (l < 0x80) {
                len = l;
            } else {
                int n = l & 0x7F;
                if (n == 0 || n > 4 || p + n > end) return null;
                len = 0;
                for (int i = 0; i < n; i++) len = (len << 8) | (b[p++] & 0xFF);
            }
            if (len > end - p) return null;
            Node node = new Node();
            int tagEnd = p;
            // tag bytes = everything before the length octets
            int lenOctets = l < 0x80 ? 1 : 1 + (l & 0x7F);
            node.tag = Arrays.copyOfRange(b, start, tagEnd - lenOctets);
            int contentEnd = p + (int) len;
            boolean constructed = (first & 0x20) != 0;
            List<Node> kids = null;
            if (constructed) {
                kids = len == 0 ? new ArrayList<>() : parse(b, p, contentEnd, depth + 1);
                if (kids == null) return null;
            } else if (len > 1) {
                kids = parse(b, p, contentEnd, depth + 1);    // OCTET STRING / [0] fullChain holding TLVs
                node.viaPrimitive = kids != null;
            }
            if (kids != null) {
                node.kids = kids;
            } else {
                node.content = Arrays.copyOfRange(b, p, contentEnd);
            }
            out.add(node);
            p = contentEnd;
        }
        return out;
    }

    private static void flatten(Node n, List<Node> acc, List<Node> parents) {
        acc.add(n);
        if (n.kids != null) {
            for (Node k : n.kids) { parents.add(n); flatten(k, acc, parents); }
        }
    }

    private static final int[] TAGS = {0x30, 0x31, 0x04, 0x0C, 0x05, 0x02, 0x01, 0x80, 0x81, 0x82, 0xA0, 0xA1,
            0xA7, 0xA8, 0x87, 0x88, 0x89, 0xB4, 0xBE, 0x9E, 0x8F, 0x00, 0xFF};

    /** A structure-aware mutation of a valid record; returns null if the input does not parse. */
    static byte[] mutateTree(byte[] valid, Random r, StringBuilder how) {
        List<Node> top = parse(valid, 0, valid.length, 0);
        if (top == null) return null;
        // a [9] array item is two consecutive TLVs: mutate inside a virtual wrapper that is never itself encoded
        boolean wrapped = top.size() != 1;
        Node root;
        if (wrapped) {
            root = new Node();
            root.tag = new byte[] {0x30};
            root.kids = new ArrayList<>();
            for (Node n : top) root.kids.add(n.copy());
        } else {
            root = top.get(0).copy();
        }
        List<Node> all = new ArrayList<>();
        List<Node> parentOfEach = new ArrayList<>();
        flatten(root, all, parentOfEach);
        if (wrapped) all.remove(0);
        if (all.isEmpty()) return null;
        int idx = r.nextInt(all.size());
        Node target = all.get(idx);
        Node parent = null;
        for (Node cand : all) {
            if (cand.kids != null && cand.kids.contains(target) && cand.kids.stream().anyMatch(k -> k == target)) {
                parent = cand;
                break;
            }
        }
        int op = r.nextInt(9);
        how.append("tree-op ").append(op).append(" at node ").append(idx).append('/').append(all.size());
        switch (op) {
            case 0 -> {   // change the tag byte
                int t = TAGS[r.nextInt(TAGS.length)];
                target.tag = new byte[] {(byte) t};
                if (target.kids == null && (t & 0x20) != 0 && target.content != null) target.content = new byte[0];
            }
            case 1 -> {   // delete the node
                if (parent == null) return null;
                parent.kids.removeIf(k -> k == target);
            }
            case 2 -> {   // duplicate the node
                if (parent == null) return null;
                parent.kids.add(parent.kids.indexOf(target), target.copy());
            }
            case 3 -> {   // swap with the next sibling
                if (parent == null) return null;
                int i = parent.kids.indexOf(target);
                if (i + 1 >= parent.kids.size()) return null;
                java.util.Collections.swap(parent.kids, i, i + 1);
            }
            case 4 -> {   // replace leaf content with random bytes
                if (target.kids != null) { target.kids = null; target.viaPrimitive = false; }
                byte[] c = new byte[r.nextInt(40)];
                r.nextBytes(c);
                target.content = c;
            }
            case 5 -> {   // add a stray random child
                if (target.kids == null) return null;
                Node extra = new Node();
                extra.tag = new byte[] {(byte) TAGS[r.nextInt(TAGS.length)]};
                extra.content = new byte[r.nextInt(8)];
                r.nextBytes(extra.content);
                if ((extra.tag[0] & 0x20) != 0) extra.content = new byte[0];
                target.kids.add(r.nextInt(target.kids.size() + 1), extra);
            }
            case 6 -> {   // graft a copy of another node here
                Node other = all.get(r.nextInt(all.size())).copy();
                target.tag = other.tag;
                target.content = other.content;
                target.kids = other.kids;
                target.viaPrimitive = other.viaPrimitive;
            }
            case 7 -> {   // flip a byte inside leaf content
                if (target.kids != null || target.content.length == 0) return null;
                target.content[r.nextInt(target.content.length)] ^= (byte) (1 << r.nextInt(8));
            }
            default -> {  // truncate or extend leaf content by a byte
                if (target.kids != null) return null;
                if (r.nextBoolean() && target.content.length > 0) {
                    target.content = Arrays.copyOf(target.content, target.content.length - 1);
                } else {
                    target.content = Arrays.copyOf(target.content, target.content.length + 1);
                }
            }
        }
        if (wrapped) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            for (Node k : root.kids) o.writeBytes(k.encode());
            return o.toByteArray();
        }
        return root.encode();
    }

    /** A byte-level mutation (lengths and framing are NOT repaired). */
    static byte[] mutateBytes(byte[] valid, Random r, StringBuilder how) {
        byte[] b = valid.clone();
        int op = r.nextInt(7);
        how.append("byte-op ").append(op);
        int p = r.nextInt(b.length);
        switch (op) {
            case 0 -> b[p] ^= (byte) (1 << r.nextInt(8));
            case 1 -> b[p] = (byte) r.nextInt(256);
            case 2 -> b[p] = new byte[] {0x00, (byte) 0xFF, (byte) 0x80, (byte) 0x81, 0x30, 0x04, 0x05, 0x1F}[r.nextInt(8)];
            case 3 -> { int n = 1 + r.nextInt(8); int q = Math.min(b.length, p + n); b = concat(Arrays.copyOf(b, p), Arrays.copyOfRange(b, q, b.length)); }
            case 4 -> { byte[] ins = new byte[1 + r.nextInt(6)]; r.nextBytes(ins); b = concat(Arrays.copyOf(b, p), ins, Arrays.copyOfRange(b, p, b.length)); }
            case 5 -> b = Arrays.copyOf(b, p);
            default -> { int n = 1 + r.nextInt(16); int q = Math.min(b.length, p + n); b = concat(Arrays.copyOf(b, q), Arrays.copyOfRange(b, p, q), Arrays.copyOfRange(b, q, b.length)); }
        }
        return b;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }

    // ------------------------------------------------------------------ 2. same verdict on mutated inputs

    private static final int ITERATIONS = Integer.getInteger("dedup.equivalence.iterations", 150);

    /**
     * Encode direction: each corpus item is preceded (in the same stream) by a valid copy of itself, so the mutated
     * record exercises the second-occurrence ({@code chainRef}) path as well as the first.
     */
    @TestFactory
    Stream<DynamicTest> mutatedCanonicalInputsGetTheSameVerdictWhenEncoding() throws Exception {
        return items().stream().map(it -> DynamicTest.dynamicTest(it.name, () -> {
            Random r = new Random(0xDED0 ^ it.name.hashCode());
            int compared = 0, accepted = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                StringBuilder how = new StringBuilder();
                byte[] mutated = (i % 3 == 2) ? mutateBytes(it.canonical, r, how) : mutateTree(it.canonical, r, how);
                if (mutated == null) continue;
                boolean primed = r.nextBoolean();
                Walker ref = reference(true), mig = migrated(true);
                if (primed) {
                    assertSame(encodeOne(ref, it, it.canonical), encodeOne(mig, it, it.canonical), it.name + " priming");
                }
                Outcome ro = encodeOne(ref, it, mutated), mo = encodeOne(mig, it, mutated);
                assertSame(ro, mo, it.name + " #" + i + " [" + how + (primed ? ", primed" : "") + "] " + hex(mutated, 160));
                compared++;
                if (ro.error == null) accepted++;
            }
            assertTrue(compared > ITERATIONS / 3, it.name + ": too few parseable mutations (" + compared + ")");
            assertTrue(accepted < compared || compared < 5, it.name + ": every mutation was accepted; the mutator is not biting");
        }));
    }

    /**
     * Decode direction: stream-form records produced by the reference encoder; the mutated record is preceded by
     * the valid first occurrence so a {@code chainRef} can resolve (or be made not to).
     */
    @TestFactory
    Stream<DynamicTest> mutatedStreamInputsGetTheSameVerdictWhenReconstituting() throws Exception {
        return items().stream().map(it -> DynamicTest.dynamicTest(it.name, () -> {
            Walker enc = reference(true);
            Outcome first = encodeOne(enc, it, it.canonical);
            Outcome second = encodeOne(enc, it, it.canonical);
            assertNull(first.error);
            Random r = new Random(0xDEC0 ^ it.name.hashCode());
            int compared = 0, rejected = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                StringBuilder how = new StringBuilder();
                boolean useSecond = r.nextBoolean();
                byte[] valid = useSecond ? second.bytes : first.bytes;
                byte[] mutated = (i % 3 == 2) ? mutateBytes(valid, r, how) : mutateTree(valid, r, how);
                if (mutated == null) continue;
                Walker ref = reference(false), mig = migrated(false);
                if (useSecond) {
                    assertSame(decodeOne(ref, it, first.bytes), decodeOne(mig, it, first.bytes), it.name + " priming");
                }
                Outcome ro = decodeOne(ref, it, mutated), mo = decodeOne(mig, it, mutated);
                assertSame(ro, mo, it.name + " #" + i + " [" + how + (useSecond ? ", second" : ", first") + "] " + hex(mutated, 160));
                compared++;
                if (ro.error != null) rejected++;
            }
            assertTrue(compared > ITERATIONS / 3, it.name + ": too few parseable mutations (" + compared + ")");
            assertTrue(rejected > 0 || compared < 5, it.name + ": no mutation was ever rejected; the mutator is not biting");
        }));
    }

    private static String hex(byte[] b, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(b.length, max); i++) sb.append(String.format("%02x", b[i] & 0xFF));
        return sb + (b.length > max ? "..(" + b.length + ")" : "");
    }

    // ------------------------------------------------------------------ 3. the interior fence still holds

    @Test
    void referenceAndMigratedAgreeOnTheCounterSeamsAfterAWholeStream() throws Exception {
        Walker refEnc = reference(true), migEnc = migrated(true), refDec = reference(false), migDec = migrated(false);
        List<Item> all = items();
        for (Item it : all) {
            Outcome re = encodeOne(refEnc, it, it.canonical);
            Outcome me = encodeOne(migEnc, it, it.canonical);
            if (re.error != null) continue;   // the cumulative state may pass the table ceilings; both must agree
            assertSame(re, me, it.name);
            assertSame(decodeOne(refDec, it, re.bytes), decodeOne(migDec, it, re.bytes), it.name + " [decode]");
        }
        assertArrayEquals(refDec.state(), migDec.state());
        assertTrue(refDec.state()[2] > 0, "the stream must have reconstituted some chains");
    }

    // ------------------------------------------------------------------ 3b. depth: the MAX_NESTING boundary

    private static boolean isOctet(Node n) {
        return n.tag.length == 1 && n.tag[0] == 0x04;
    }

    /** A nested @AtomicSerial site: SEQUENCE { OCTET chain | [0] full | [1] ref , OCTET payload }. */
    private static Node findNestedSite(Node n) {
        if (n.kids != null) {
            if (n.tag.length == 1 && n.tag[0] == 0x30 && n.kids.size() == 2 && isOctet(n.kids.get(1))
                    && n.kids.get(1).kids != null
                    && (isOctet(n.kids.get(0)) || (n.kids.get(0).tag.length == 1
                        && (n.kids.get(0).tag[0] == (byte) 0x80 || n.kids.get(0).tag[0] == (byte) 0x81)))) {
                return n;
            }
            for (Node k : n.kids) {
                Node f = findNestedSite(k);
                if (f != null) return f;
            }
        }
        return null;
    }

    /** The deepest DER NULL below {@code n} (preorder, last one wins), with its parent. */
    private static Node[] deepestNull(Node n, Node parent, int depth, int[] best, Node[] found) {
        if (n.kids == null && n.tag.length == 1 && n.tag[0] == 0x05 && n.content.length == 0 && depth >= best[0]) {
            best[0] = depth;
            found[0] = n;
            found[1] = parent;
        }
        if (n.kids != null) for (Node k : n.kids) deepestNull(k, n, depth + 1, best, found);
        return found;
    }

    /**
     * Splices copies of an existing nested site into the innermost null of a self-recursive record, growing its depth
     * one site at a time, and checks that both walkers agree at every depth either side of {@code MAX_NESTING}.
     */
    @Test
    void deepeningAcrossMaxNestingGetsTheSameVerdict() throws Exception {
        int rejectedDeep = 0;
        for (String name : List.of("chain-3", "node-recursion")) {
            Item it = items().stream().filter(x -> x.name.equals(name)).findFirst().orElseThrow();
            for (boolean streamForm : new boolean[] {false, true}) {
                byte[] base = it.canonical;
                if (streamForm) {
                    base = encodeOne(reference(true), it, it.canonical).bytes;
                }
                for (int extra = 0; extra <= ObjectCodec.MAX_NESTING + 4; extra++) {
                    Node root = parse(base, 0, base.length, 0).get(0).copy();
                    Node site = findNestedSite(root);
                    assertNotNull(site, name + ": no nested site to copy");
                    Node template = site.copy();    // spliced unchanged each time: depth grows linearly, size too
                    for (int i = 0; i < extra; i++) {
                        Node[] f = deepestNull(root, null, 0, new int[] {-1}, new Node[2]);
                        Node nullNode = f[0], parent = f[1];
                        assertNotNull(nullNode);
                        Node copy = template.copy();
                        int at = parent.kids.indexOf(nullNode);
                        if (parent.viaPrimitive || parent.kids != null) parent.kids.set(at, copy);
                    }
                    byte[] input = root.encode();
                    String what = name + (streamForm ? " [stream]" : " [canonical]") + " +" + extra;
                    Outcome ro, mo;
                    if (streamForm) {
                        // prime both decoders with a valid first occurrence so chainRefs resolve
                        Walker ref = reference(false), mig = migrated(false);
                        Outcome seed = encodeOne(reference(true), it, it.canonical);
                        assertSame(decodeOne(ref, it, seed.bytes), decodeOne(mig, it, seed.bytes), what + " seed");
                        ro = decodeOne(ref, it, input);
                        mo = decodeOne(mig, it, input);
                    } else {
                        ro = encodeOne(reference(true), it, input);
                        mo = encodeOne(migrated(true), it, input);
                    }
                    assertSame(ro, mo, what);
                    if (ro.error != null && ro.error.contains("MAX_NESTING")) rejectedDeep++;
                }
            }
        }
        assertTrue(rejectedDeep > 0, "no deepened record ever hit the MAX_NESTING guard; the splice is not working");
    }

    // ------------------------------------------------------------------ 3c. the reconstitution fence (G10) on the new walker

    private static byte[] bigChain() {
        List<AtomicSerialFieldDef> fields = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            fields.add(new AtomicSerialFieldDef("f" + String.format("%03d", i) + "x".repeat(196), "int"));
        }
        byte[] c = new AtomicSerialSchemaRecord("Bomb", fields).encode();
        assertTrue(c.length < SchemaChain.MAX_CHAIN_BYTES && c.length > 60_000, "chain size " + c.length);
        return c;
    }

    private static byte[] bombArray(byte[] chain, int refs) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(chain);
        byte[] payload = DerWriter.writeOctetString(
                DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of()))));
        List<byte[]> elems = new ArrayList<>();
        elems.add(DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(new au.net.zeus.jgdms.der.Tag(au.net.zeus.jgdms.der.Tag.CLASS_CONTEXT, false, 0), chain), payload)));
        for (int i = 0; i < refs; i++) {
            elems.add(DerWriter.writeSequence(List.of(
                    DerWriter.writeTlv(new au.net.zeus.jgdms.der.Tag(au.net.zeus.jgdms.der.Tag.CLASS_CONTEXT, false, 1), digest), payload)));
        }
        byte[] wt = DerWriter.writeUtf8String("array:@AtomicSerial:Bomb");
        byte[] seq = DerWriter.writeSequence(elems);
        return concat(wt, seq);
    }

    @Test
    void fanOutBombIsRejectedBeforeTheAppendThatWouldBreachTheCeiling() throws Exception {
        byte[] chain = bigChain();
        int smallMaxInput = 256 * 1024;   // per-item ceiling 2 MiB: about 32 re-materialisations of a 64 KB chain
        for (int refs : new int[] {1, 10, 30, 31, 32, 33, 40, 1000}) {
            byte[] input = bombArray(chain, refs);
            StreamSchemaDedupReference ref = new StreamSchemaDedupReference(false, smallMaxInput);
            StreamSchemaDedup mig = new StreamSchemaDedup(false, smallMaxInput);
            String refErr = null, migErr = null;
            byte[] refOut = null, migOut = null;
            try { refOut = ref.reconstituteTopLevelArray(input); } catch (DerException e) { refErr = e.getMessage(); }
            au.net.zeus.jgdms.der.DerEncoder enc = au.net.zeus.jgdms.der.DerEncoder.create();
            try { mig.reconstituteTopLevelArrayTo(input, enc); migOut = enc.toByteArray(); } catch (DerException e) { migErr = e.getMessage(); }
            assertEquals(refErr, migErr, "refs=" + refs + ": same verdict and same message (own messages carry exact counts)");
            if (refErr == null) {
                assertArrayEquals(refOut, migOut, "refs=" + refs);
            }
            assertEquals(ref.reconstitutedChainBytes(), mig.reconstitutedChainBytes(), "refs=" + refs);
            assertEquals(ref.streamReconstitutedChainBytes(), mig.streamReconstitutedChainBytes(), "refs=" + refs);
            // meter-before-append: whatever was appended was metered first, and the ceiling was never passed
            assertTrue(mig.materialisedChainBytes() <= mig.streamReconstitutedChainBytes(), "refs=" + refs);
            assertTrue(mig.materialisedChainBytes() <= mig.maxReconstitutedChainBytes(), "refs=" + refs);
            if (refs >= 40) {
                assertNotNull(migErr, "refs=" + refs + " must breach the 2 MiB ceiling");
                assertTrue(migErr.contains("maxReconstitutedBytes"), migErr);
            }
        }
    }

    // ------------------------------------------------------------------ edge cases from the security review

    /** A zero-length payload and an empty hierarchy: both walkers must agree in both directions. */
    @Test
    void zeroLengthPayloadGetsTheSameVerdict() throws Exception {
        Item zero = items().stream().filter(x -> x.name.equals("zero-field-record")).findFirst().orElseThrow();
        SchemaChain.Result chain = SchemaGenerator.generateChain(zero.cls);
        for (byte[] payload : new byte[][] {new byte[0], DerWriter.writeSequence(List.of()),
                DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of())))}) {
            Item it = new Item("zero-payload", false, MarshalledInstanceRecord.fromChain(chain, payload).encode(), zero.cls);
            Outcome re = encodeOne(reference(true), it, it.canonical), me = encodeOne(migrated(true), it, it.canonical);
            assertSame(re, me, "zero payload [encode] " + hex(payload, 20));
            if (re.error == null) {
                assertSame(decodeOne(reference(false), it, re.bytes), decodeOne(migrated(false), it, re.bytes),
                        "zero payload [decode] " + hex(payload, 20));
            }
        }
    }

    /**
     * The encode table must not alias the caller's input: a repeat chain site is looked up through a window onto the
     * input, but a first occurrence is COPIED. Mutating the first input after the call must not change what the second
     * occurrence encodes to.
     */
    @Test
    void mutatingTheInputAfterEncodingDoesNotChangeLaterOutput() throws Exception {
        for (Item it : items()) {
            if (it.array) continue;
            StreamSchemaDedup w = new StreamSchemaDedup(true);
            byte[] first = it.canonical.clone();
            byte[] out1 = w.dedupTopLevelAtomic(first, it.cls);
            Arrays.fill(first, (byte) 0x5A);                    // scribble over the input the table might alias
            byte[] out2 = w.dedupTopLevelAtomic(it.canonical.clone(), it.cls);
            StreamSchemaDedupReference r = new StreamSchemaDedupReference(true);
            r.dedupTopLevelAtomic(it.canonical.clone(), it.cls);
            assertArrayEquals(r.dedupTopLevelAtomic(it.canonical.clone(), it.cls), out2, it.name);
            assertNotNull(out1);
        }
    }

    /**
     * Runs last (see the method name ordering is not relied on: it only asserts on what the other tests
     * accumulated if they ran in this JVM) and guards that the mutators reach MANY distinct reject paths.
     */
    @org.junit.jupiter.api.AfterAll
    static void rejectPathCoverage() {
        if (REJECTS.isEmpty()) return;   // run in isolation without the mutation tests
        if (Boolean.getBoolean("dedup.equivalence.report")) {
            REJECTS.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> System.out.println("REJECT " + e.getValue() + "x " + e.getKey()));
            System.out.println("ACCEPT " + ACCEPTS.get());
        }
        assertTrue(REJECTS.size() >= 25, "the mutators only reached " + REJECTS.size() + " distinct reject messages");
        assertTrue(ACCEPTS.get() > 100, "too few accepted mutations to test accept-equivalence: " + ACCEPTS.get());
    }

    /** Silences the unused-import warning for HashMap if mutation helpers change. */
    @SuppressWarnings("unused")
    private static final Map<String, String> UNUSED = new HashMap<>();
}
