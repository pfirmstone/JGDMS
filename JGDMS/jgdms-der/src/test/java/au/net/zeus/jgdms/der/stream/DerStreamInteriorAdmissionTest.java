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
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static au.net.zeus.jgdms.der.stream.DerStreamAdmissionCharacterisationTest.CONSTRUCTED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B3: whole-stream admission applies the record decoder's own value rules to the interior of a record: every scalar, string,
 * byte array, enum and Class name, value array and collection (outer tag, element count, element values, order). A stream
 * with a bad interior is refused whole BEFORE the first object of the stream is built; without admission, delivery builds
 * the objects before the bad one and then fails (the control in every case), and for a field the class never reads it never
 * fails at all.
 *
 * <p>Each case takes a valid stream of three {@link Wide} records from the real codec and replaces ONE field of the LAST
 * record by a bad value TLV, so that anything admission lets through is visible as a constructor that ran.
 */
class DerStreamInteriorAdmissionTest {

    enum Color { RED, GREEN }

    /** Every value kind the interior check covers; the constructor reads every field, so delivery decodes every one. */
    @AtomicSerial
    public static final class Wide {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("z", boolean.class), new AtomicSerial.SerialForm("b", byte.class),
                new AtomicSerial.SerialForm("s", short.class), new AtomicSerial.SerialForm("i", int.class),
                new AtomicSerial.SerialForm("l", long.class), new AtomicSerial.SerialForm("f", float.class),
                new AtomicSerial.SerialForm("d", double.class), new AtomicSerial.SerialForm("c", char.class),
                new AtomicSerial.SerialForm("str", String.class), new AtomicSerial.SerialForm("bytes", byte[].class),
                new AtomicSerial.SerialForm("color", Color.class), new AtomicSerial.SerialForm("cls", Class.class),
                new AtomicSerial.SerialForm("ints", int[].class), new AtomicSerial.SerialForm("strs", String[].class),
                new AtomicSerial.SerialForm("set", Set.class), new AtomicSerial.SerialForm("list", List.class),
                new AtomicSerial.SerialForm("map", Map.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, Wide w) throws IOException {
            arg.put("z", w.z); arg.put("b", w.b); arg.put("s", w.s); arg.put("i", w.i); arg.put("l", w.l);
            arg.put("f", w.f); arg.put("d", w.d); arg.put("c", w.c); arg.put("str", w.str); arg.put("bytes", w.bytes);
            arg.put("color", w.color); arg.put("cls", w.cls); arg.put("ints", w.ints); arg.put("strs", w.strs);
            arg.put("set", w.set); arg.put("list", w.list); arg.put("map", w.map);
            arg.writeArgs();
        }
        final boolean z; final byte b; final short s; final int i; final long l; final float f; final double d; final char c;
        final String str; final byte[] bytes; final Color color; final Class<?> cls; final int[] ints; final String[] strs;
        final Set<String> set; final List<Integer> list; final Map<String, Integer> map;

        Wide(int n) {
            z = true; b = 7; s = 300; i = n; l = 1L << 40; f = 1.5f; d = -2.25; c = 'x'; str = "wide-" + n;
            bytes = new byte[] {1, 2, 3}; color = Color.GREEN; cls = String.class; ints = new int[] {1, 2, 3};
            strs = new String[] {"a", "b"};
            set = new TreeSet<>(List.of("apple", "fig", "kiwi")); list = List.of(3, 1, 2);
            map = new TreeMap<>(Map.of("one", 1, "two", 2));
        }

        public Wide(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            CONSTRUCTED.incrementAndGet();
            z = arg.get("z", false); b = arg.get("b", (byte) 0); s = arg.get("s", (short) 0); i = arg.get("i", 0);
            l = arg.get("l", 0L); f = arg.get("f", 0f); d = arg.get("d", 0d); c = arg.get("c", ' ');
            str = (String) arg.get("str", null); bytes = arg.get("bytes", new byte[0], byte[].class);
            color = arg.get("color", null, Color.class); cls = arg.get("cls", null, Class.class);
            ints = arg.get("ints", new int[0], int[].class); strs = arg.get("strs", new String[0], String[].class);
            set = arg.get("set", Collections.emptySet(), Set.class);
            list = arg.get("list", Collections.emptyList(), List.class);
            map = arg.get("map", Collections.emptyMap(), Map.class);
        }
    }

    /** A class that reads only its first field: the collection beside it is never decoded by anything but admission. */
    @AtomicSerial
    public static final class Lazy {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("n", int.class), new AtomicSerial.SerialForm("set", Set.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, Lazy w) throws IOException {
            arg.put("n", w.n); arg.put("set", w.set);
            arg.writeArgs();
        }
        final int n; final Set<String> set;
        Lazy(int n) { this.n = n; this.set = new TreeSet<>(List.of("a", "b")); }
        public Lazy(AtomicSerial.GetArg arg) throws IOException {
            CONSTRUCTED.incrementAndGet();
            n = arg.get("n", 0);
            set = null;                                       // never asks for it
        }
    }

    @BeforeEach
    void reset() {
        CONSTRUCTED.set(0);
    }

    // ------------------------------------------------------------------ plumbing

    static final Tag CTX_ATOMIC = new Tag(Tag.CLASS_CONTEXT, true, 1);

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }

    private static byte[] streamOf(Object... items) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        for (Object o : items) c.writeObject(o);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    /** Opens through a public constructor (full admission before it returns), with the shipped policy. */
    private static DerMarshalInputStream open(byte[] stream) throws IOException {
        ClassLoader cl = DerStreamInteriorAdmissionTest.class.getClassLoader();
        return new DerMarshalInputStream(new ByteArrayInputStream(stream), new ResolutionContext(cl, false, cl),
                DerInputLimits.DEFAULT, DerStreamPolicy.BUILT_IN);
    }

    /** The lazy per-item path (no admission): reads every item, returns the failure or null. */
    private static Throwable lazyFailure(byte[] stream) {
        try {
            DerObjectStreamCodec c = new DerObjectStreamCodec();
            c.initReader(stream);
            while (c.available() > 0) {
                c.readObject();
            }
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    /** The byte ranges {@code [start, end)} of the top-level items after the 3-byte version octet. */
    private static List<int[]> itemRanges(byte[] stream) throws Exception {
        DerReader r = new DerReader(stream, 3, stream.length);
        List<int[]> out = new ArrayList<>();
        while (r.hasMore()) {
            int s = r.position();
            out.add(new int[] {s, r.skipTlv()});
        }
        return out;
    }

    /** The field names of item 0's class (single-level hierarchy), in wire order, from the chain the first item carries. */
    private static List<String> fieldNames(byte[] stream) throws Exception {
        int[] first = itemRanges(stream).get(0);
        DerReader seq = itemSeq(stream, first);
        seq.skipTlv();                                              // the payload OCTET STRING
        DerReader.TlvHeader hdr = seq.readTlvHeader();              // the full chain, [0]
        List<AtomicSerialSchemaRecord> chain = SchemaChain.decodeChain(seq.readRawContent(hdr.contentLength()), "test");
        List<String> names = new ArrayList<>();
        chain.get(0).fields().forEach(f -> names.add(f.wireName()));
        return names;
    }

    /** A copy of {@code stream} whose item {@code itemIdx} has its field {@code field} replaced by the TLV {@code replacement}. */
    private static byte[] withField(byte[] stream, int itemIdx, String field, byte[] replacement) throws Exception {
        int fieldIdx = fieldNames(stream).indexOf(field);
        assertTrue(fieldIdx >= 0, "no field " + field + " in " + fieldNames(stream));
        List<int[]> items = itemRanges(stream);
        int[] range = items.get(itemIdx);
        DerReader seq = itemSeq(stream, range);                     // SEQUENCE { OCTET payload, chain-or-ref, format }
        byte[] payload = seq.readOctetString();
        int restStart = seq.position();
        while (seq.hasMore()) {
            seq.skipTlv();
        }
        byte[] rest = java.util.Arrays.copyOfRange(stream, restStart, seq.position());
        DerReader hier = new DerReader(payload).readSequence();     // hierarchy: one SEQUENCE per class
        DerReader cls = hier.readSequence();
        List<byte[]> tlvs = new ArrayList<>();
        while (cls.hasMore()) {
            int s = cls.position();
            int e = cls.skipTlv();
            tlvs.add(java.util.Arrays.copyOfRange(payload, s, e));
        }
        tlvs.set(fieldIdx, replacement);
        byte[] newPayload = DerWriter.writeSequence(List.of(DerWriter.writeSequence(tlvs)));
        byte[] newItem = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(cat(DerWriter.writeOctetString(newPayload), rest)));
        return cat(java.util.Arrays.copyOfRange(stream, 0, range[0]), newItem,
                java.util.Arrays.copyOfRange(stream, range[1], stream.length));
    }

    /** The field TLVs of item {@code itemIdx}'s class (single-level hierarchy), in wire order. */
    private static List<byte[]> fieldTlvs(byte[] stream, int itemIdx) throws Exception {
        DerReader seq = itemSeq(stream, itemRanges(stream).get(itemIdx));
        byte[] payload = seq.readOctetString();
        DerReader cls = new DerReader(payload).readSequence().readSequence();
        List<byte[]> tlvs = new ArrayList<>();
        while (cls.hasMore()) {
            int s = cls.position();
            int e = cls.skipTlv();
            tlvs.add(java.util.Arrays.copyOfRange(payload, s, e));
        }
        return tlvs;
    }

    /** The children of a constructed TLV (a SET or SEQUENCE): each child's complete bytes. */
    private static List<byte[]> children(byte[] tlv) throws Exception {
        DerReader r = new DerReader(tlv);
        r.readTlvHeader();
        List<byte[]> kids = new ArrayList<>();
        while (r.hasMore()) {
            int s = r.position();
            int e = r.skipTlv();
            kids.add(java.util.Arrays.copyOfRange(tlv, s, e));
        }
        return kids;
    }

    /** A reader over the SEQUENCE inside a stream-level {@code [1]} item. */
    private static DerReader itemSeq(byte[] stream, int[] range) throws Exception {
        DerReader item = new DerReader(stream, range[0], range[1]);
        item.readTlvHeader();
        return item.readSequence();
    }

    // ------------------------------------------------------------------ the cases: field, bad TLV

    private static byte[] utf8Bytes(int... bytes) {
        byte[] content = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) content[i] = (byte) bytes[i];
        return DerWriter.writeTlv(new Tag(Tag.CLASS_UNIVERSAL, false, 12), content);
    }

    private static byte[] octets(int... bytes) {
        byte[] content = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) content[i] = (byte) bytes[i];
        return DerWriter.writeOctetString(content);
    }

    private static byte[] ints(long... vs) {
        List<byte[]> l = new ArrayList<>();
        for (long v : vs) l.add(DerWriter.writeInteger(v));
        return DerWriter.writeSequence(l);
    }

    private static byte[] strs(String... vs) {
        List<byte[]> l = new ArrayList<>();
        for (String v : vs) l.add(DerWriter.writeUtf8String(v));
        return l.isEmpty() ? DerWriter.writeSequence(l) : DerWriter.writeSequence(l);
    }

    private static byte[] setOf(byte[]... elems) { return DerWriter.writeSet(List.of(elems)); }
    private static byte[] seqOf(byte[]... elems) { return DerWriter.writeSequence(List.of(elems)); }
    private static byte[] entry(byte[] k, byte[] v) { return DerWriter.writeSequence(List.of(k, v)); }
    private static byte[] str(String s) { return DerWriter.writeUtf8String(s); }
    private static byte[] i(long v) { return DerWriter.writeInteger(v); }
    private static final byte[] NULL_TLV = {0x05, 0x00};

    /** field -> bad value TLVs (description, bytes). */
    private static Map<String, byte[]> badValues() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        // scalars
        m.put("z|boolean not DER TRUE", new byte[] {0x01, 0x01, 0x01});
        m.put("z|boolean as INTEGER", i(1));
        m.put("b|byte out of range", i(200));
        m.put("b|byte non-minimal INTEGER", new byte[] {0x02, 0x02, 0x00, 0x05});
        m.put("b|byte as UTF8String", str("x"));
        m.put("s|short out of range", i(40000));
        m.put("i|int out of range", i(1L << 31));
        m.put("l|long out of range", DerWriter.writeInteger(new java.math.BigInteger("9223372036854775808")));
        m.put("f|float negative zero", octets(0x80, 0, 0, 0));
        m.put("f|float non-canonical NaN", octets(0x7F, 0xC0, 0, 1));
        m.put("f|float wrong length", octets(1, 2, 3));
        m.put("d|double negative zero", octets(0x80, 0, 0, 0, 0, 0, 0, 0));
        m.put("d|double non-canonical NaN", octets(0x7F, 0xF8, 0, 0, 0, 0, 0, 1));
        m.put("c|char surrogate", i(0xD800));
        m.put("c|char beyond the BMP", i(0x10000));
        m.put("str|String invalid UTF-8", utf8Bytes(0xC3, 0x28));
        m.put("str|String overlong UTF-8", utf8Bytes(0xC0, 0x80));
        m.put("str|String as INTEGER", i(3));
        m.put("bytes|byte[] as UTF8String", str("x"));
        m.put("color|enum name as INTEGER", i(1));
        m.put("color|enum name invalid UTF-8", utf8Bytes(0xFF));
        m.put("cls|Class name invalid UTF-8", utf8Bytes(0xFF));
        // value arrays
        m.put("ints|int[] element out of range", ints(1, 1L << 31));
        m.put("ints|int[] NULL element", seqOf(i(1), NULL_TLV));
        m.put("ints|int[] outer SET", setOf(i(1)));
        m.put("strs|String[] element invalid UTF-8", seqOf(str("a"), utf8Bytes(0xC3, 0x28)));
        // collections
        m.put("set|set outer SEQUENCE", seqOf(str("a")));
        m.put("set|set unsorted", setOf(str("b"), str("a")));
        m.put("set|set duplicate", setOf(str("a"), str("a")));
        m.put("set|set element invalid UTF-8", setOf(str("a"), utf8Bytes(0xC3, 0x28)));
        m.put("set|set element as INTEGER", setOf(i(1)));
        m.put("list|list outer SET", setOf(i(1)));
        m.put("list|list element out of range", seqOf(i(1), i(1L << 31)));
        m.put("map|map outer SEQUENCE", seqOf(entry(str("a"), i(1))));
        m.put("map|map keys unsorted", setOf(entry(str("b"), i(1)), entry(str("a"), i(2))));
        m.put("map|map duplicate keys", setOf(entry(str("a"), i(1)), entry(str("a"), i(2))));
        m.put("map|map entry not a SEQUENCE", setOf(str("a")));
        m.put("map|map entry with three parts", setOf(DerWriter.writeSequence(List.of(str("a"), i(1), i(2)))));
        m.put("map|map value out of range", setOf(entry(str("a"), i(1L << 31))));
        m.put("map|map key invalid UTF-8", setOf(entry(utf8Bytes(0xC3, 0x28), i(1))));
        return m;
    }

    @Test
    void theValidStreamIsAdmittedAndDelivered() throws Exception {
        byte[] stream = streamOf(new Wide(1), new Wide(2), new Wide(3));
        assertNull(lazyFailure(stream), "the fixture must read lazily");
        open(stream).close();
        assertEquals(3, CONSTRUCTED.get(), "(only the lazy read above built anything: admission built nothing)");
    }

    @TestFactory
    Stream<DynamicTest> aBadInteriorIsRefusedWholeBeforeAnythingIsBuiltAndWouldHaveReachedConstruction() throws Exception {
        byte[] good = streamOf(new Wide(1), new Wide(2), new Wide(3));
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : badValues().entrySet()) {
            String field = e.getKey().substring(0, e.getKey().indexOf('|'));
            tests.add(DynamicTest.dynamicTest(e.getKey(), () -> {
                byte[] bad = withField(good, 2, field, e.getValue());
                // control: without admission delivery builds the two good objects, then fails inside the third
                CONSTRUCTED.set(0);
                Throwable lazy = lazyFailure(bad);
                assertNotNull(lazy, "delivery must reject this value too (else it is not malformed)");
                assertTrue(CONSTRUCTED.get() >= 2, "the two objects before the bad one were built: " + CONSTRUCTED.get());
                // admission refuses the stream whole, with nothing built
                CONSTRUCTED.set(0);
                IOException rejected = assertThrows(IOException.class, () -> open(bad));
                assertTrue(DerStreamAdmissionCharacterisationTest.chain(rejected).contains("DER object stream admission"),
                        "refused by the admission walk: " + DerStreamAdmissionCharacterisationTest.chain(rejected));
                assertEquals(0, CONSTRUCTED.get(), "admission builds nothing");
            }));
        }
        return tests.stream();
    }

    @Test
    void aMalformedFieldNobodyReadsIsStillRefused() throws Exception {
        byte[] good = streamOf(new Lazy(1), new Lazy(2));
        byte[] bad = withField(good, 1, "set", setOf(str("b"), str("a")));          // unsorted SET OF
        CONSTRUCTED.set(0);
        assertNull(lazyFailure(bad), "delivery never decodes the field: it accepts the non-canonical encoding");
        assertEquals(2, CONSTRUCTED.get());
        CONSTRUCTED.set(0);
        assertThrows(IOException.class, () -> open(bad), "admission is the only place that ever sees it");
        assertEquals(0, CONSTRUCTED.get());
        open(good).close();
    }

    @Test
    void theElementCountCapIsInclusive() throws Exception {
        byte[] good = streamOf(new Wide(1), new Wide(2));
        List<byte[]> max = new ArrayList<>();
        for (int n = 0; n < 65536; n++) max.add(i(n));
        open(withField(good, 1, "list", DerWriter.writeSequence(max))).close();
        max.add(i(65536));
        byte[] over = withField(good, 1, "list", DerWriter.writeSequence(max));
        IOException e = assertThrows(IOException.class, () -> open(over));
        assertTrue(DerStreamAdmissionCharacterisationTest.chain(e).contains("maxCollection"), DerStreamAdmissionCharacterisationTest.chain(e));
        assertNotNull(lazyFailure(over), "delivery refuses it as well");
    }

    @Test
    void anAdjacentOrderCheckUsesTheDecodersOrderIncludingPaddedEquality() throws Exception {
        byte[] good = streamOf(new Wide(1), new Wide(2));
        // the order is of the whole TLV: the length octet comes before the text, so "a" < "b" < "ab"
        byte[] ascending = setOf(str("a"), str("b"), str("ab"));
        open(withField(good, 1, "set", ascending)).close();
        assertNull(lazyFailure(withField(good, 1, "set", ascending)));
        byte[] descending = setOf(str("ab"), str("a"));
        assertThrows(IOException.class, () -> open(withField(good, 1, "set", descending)));
        assertNotNull(lazyFailure(withField(good, 1, "set", descending)));
    }

    @Test
    void theRangedComparisonIsTheArrayComparisonOnARangeIncludingZeroPadding() {
        java.util.Random r = new java.util.Random(0xB3);
        for (int n = 0; n < 5000; n++) {
            byte[] a = new byte[r.nextInt(6)];
            byte[] b = new byte[r.nextInt(6)];
            for (int i = 0; i < a.length; i++) a[i] = (byte) (r.nextInt(3) == 0 ? 0 : r.nextInt(4));
            for (int i = 0; i < b.length; i++) b[i] = (byte) (r.nextInt(3) == 0 ? 0 : r.nextInt(4));
            int ao = r.nextInt(4), bo = r.nextInt(4);
            byte[] bigA = new byte[ao + a.length + 3], bigB = new byte[bo + b.length + 3];
            r.nextBytes(bigA);
            r.nextBytes(bigB);
            System.arraycopy(a, 0, bigA, ao, a.length);
            System.arraycopy(b, 0, bigB, bo, b.length);
            int expected = Integer.signum(au.net.zeus.jgdms.der.getarg.CollectionWireTypes.compareOctets(a, b));
            int actual = Integer.signum(au.net.zeus.jgdms.der.getarg.CollectionWireTypes.compareOctets(
                    bigA, ao, a.length, bigB, bo, b.length));
            assertEquals(expected, actual, java.util.Arrays.toString(a) + " vs " + java.util.Arrays.toString(b));
        }
    }

    @Test
    void nullsAreAcceptedWhereTheDecoderAcceptsThem() throws Exception {
        byte[] good = streamOf(new Wide(1), new Wide(2));
        for (String f : List.of("str", "bytes", "color", "cls", "ints", "strs", "set", "list", "map")) {
            byte[] nulled = withField(good, 1, f, NULL_TLV);
            assertNull(lazyFailure(nulled), f + ": delivery accepts NULL");
            open(nulled).close();
        }
    }

    @Test
    void aCollectionWhoseElementsAreRecordsIsOrderedByTheEncodingDeliverySees() throws Exception {
        byte[] good = streamOf(new Holder(new java.util.LinkedHashSet<>(List.of(new Wide(9), new Wide(5), new Wide(7)))));
        // delivery's view is the reconstituted bytes; the stream carries references after the first element's full chain,
        // so a comparison of the stream's own bytes would be a different (wrong) order. The valid stream must be admitted.
        assertNull(lazyFailure(good));
        open(good).close();
        // the same elements with two swapped are out of order in the reconstituted bytes: refused, and delivery refuses too
        List<byte[]> elems = new ArrayList<>(children(fieldTlvs(good, 0).get(0)));
        assertEquals(3, elems.size());
        java.util.Collections.swap(elems, 1, 2);   // keep the first (it carries the full chain): only the order changes
        byte[] swapped = withField(good, 0, "items", DerWriter.writeSet(elems));
        assertNotNull(lazyFailure(swapped), "delivery refuses the swapped set");
        CONSTRUCTED.set(0);
        IOException e = assertThrows(IOException.class, () -> open(swapped));
        assertTrue(DerStreamAdmissionCharacterisationTest.chain(e).contains("octet order"), DerStreamAdmissionCharacterisationTest.chain(e));
        assertEquals(0, CONSTRUCTED.get());
    }

    @Test
    void aMapWhoseKeysAreRecordsIsOrderedByTheKeyEncodingDeliverySees() throws Exception {
        Map<Wide, Integer> m = new java.util.LinkedHashMap<>();
        m.put(new Wide(9), 1); m.put(new Wide(5), 2); m.put(new Wide(7), 3);
        byte[] good = streamOf(new MapHolder(m));
        assertNull(lazyFailure(good));
        open(good).close();
        List<byte[]> entries = new ArrayList<>(children(fieldTlvs(good, 0).get(0)));
        assertEquals(3, entries.size());
        java.util.Collections.swap(entries, 1, 2);
        byte[] swapped = withField(good, 0, "items", DerWriter.writeSet(entries));
        assertNotNull(lazyFailure(swapped));
        assertThrows(IOException.class, () -> open(swapped));
    }

    // ------------------------------------------------------------------ hand-built streams (admission only; the class need not exist)

    private static final Tag CTX_ARRAY = new Tag(Tag.CLASS_CONTEXT, true, 9);
    private static final Tag CTX_COLLECTION = new Tag(Tag.CLASS_CONTEXT, true, 16);

    private static final byte[] VERSION = {(byte) 0x8F, 1, 1};

    /** Admission of a hand-built item (the version octet is added). */
    private static boolean admitted(byte[] item) {
        try {
            open(cat(VERSION, item)).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void assertAdmitted(byte[] stream, String what) { assertTrue(admitted(stream), what + " must be admitted"); }
    private static void assertRefused(byte[] stream, String what) { assertFalse(admitted(stream), what + " must be refused"); }

    private static byte[] field(String wireType, byte[] value) {
        return DerStreamPolicyAdmissionTest.oneField(wireType, value);
    }

    @Test
    void aBagMayRepeatButMustNotDescend() {
        assertAdmitted(field("bag:int", seqToSet(i(1), i(1), i(2))), "bag 1,1,2");
        assertRefused(field("bag:int", seqToSet(i(2), i(1))), "bag 2,1");
        assertRefused(field("set:int", seqToSet(i(1), i(1))), "set 1,1");
        assertAdmitted(field("set:int", seqToSet(i(1), i(2))), "set 1,2");
    }

    private static byte[] seqToSet(byte[]... elems) { return DerWriter.writeSet(List.of(elems)); }

    @Test
    void nestedCollectionsAreCheckedAtEveryLevel() {
        assertAdmitted(field("list:list:int", seqOf(seqOf(i(1)), seqOf(i(2)))), "list of lists");
        assertRefused(field("list:list:int", seqOf(seqOf(i(1)), seqOf(i(1L << 31)))), "inner element out of range");
        assertRefused(field("list:list:int", seqOf(setOf(i(1)))), "inner list as a SET");
        assertRefused(field("list:set:int", seqOf(seqOf(i(1)))), "inner set as a SEQUENCE");
        assertRefused(field("list:set:int", seqOf(setOf(i(2), i(1)))), "inner set unsorted");
        assertAdmitted(field("list:set:int", seqOf(setOf(i(1), i(2)), setOf(i(1)))), "inner sets sorted");
        assertRefused(field("map:{java.lang.String}{list:int}", setOf(entry(str("a"), seqOf(i(1L << 31))))), "map value list element");
    }

    @Test
    void aTopLevelCollectionItemIsCheckedLikeAField() {
        assertAdmitted(DerWriter.writeTlv(CTX_COLLECTION, cat(str("set:int"), setOf(i(1), i(2)))), "[16] set");
        assertRefused(DerWriter.writeTlv(CTX_COLLECTION, cat(str("set:int"), setOf(i(2), i(1)))), "[16] unsorted set");
        assertRefused(DerWriter.writeTlv(CTX_COLLECTION, cat(str("set:int"), seqOf(i(1)))), "[16] set as a SEQUENCE");
        assertRefused(DerWriter.writeTlv(CTX_COLLECTION, cat(str("list:int"), seqOf(i(1L << 31)))), "[16] list element out of range");
        assertRefused(DerWriter.writeTlv(CTX_COLLECTION, cat(str("map:{java.lang.String}{int}"),
                setOf(entry(str("b"), i(1)), entry(str("a"), i(2))))), "[16] map keys unsorted");
    }

    @Test
    void aTopLevelValueArrayIsCheckedLikeAField() {
        assertAdmitted(DerWriter.writeTlv(CTX_ARRAY, cat(str("array:int"), ints(1, 2))), "[9] int[]");
        assertRefused(DerWriter.writeTlv(CTX_ARRAY, cat(str("array:int"), ints(1, 1L << 31))), "[9] element out of range");
        assertRefused(DerWriter.writeTlv(CTX_ARRAY, cat(str("array:java.lang.String"), seqOf(utf8Bytes(0xC3, 0x28)))), "[9] bad UTF-8");
        assertRefused(DerWriter.writeTlv(CTX_ARRAY, cat(str("array:float"), seqOf(octets(1, 2, 3, 4)))), "[9] array:float is not built by delivery");
        assertRefused(DerWriter.writeTlv(CTX_ARRAY, cat(str("array:@AtomicSerial"), seqOf())), "[9] array:@AtomicSerial with no class");
        assertRefused(DerWriter.writeTlv(CTX_ARRAY, cat(str("array:array:int"), seqOf())), "[9] multi-dimensional");
    }

    @Test
    void aWireTypeDeliveryDoesNotDecodeIsRefusedUnlessTheValueIsNull() {
        assertRefused(field("@AtomicSerial:fx.Other", i(1)), "an @AtomicSerial token with a class");
        // a nested record in stream form: SEQUENCE { [0] full chain, OCTET STRING payload }
        byte[] nested = DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(DerStreamPolicyAdmissionTest.CTX_NULL_TAG, DerStreamPolicyAdmissionTest.chainOf(DerStreamPolicyAdmissionTest.LEAF)),
                DerWriter.writeOctetString(DerStreamPolicyAdmissionTest.shell())));
        assertAdmitted(field("@AtomicSerial", nested), "a well-formed nested record under the exact token");
        assertRefused(field("@AtomicSerial:fx.Other", nested), "the same nested record under a token delivery does not decode");
        assertAdmitted(field("@AtomicSerial:fx.Other", NULL_TLV), "the same token holding NULL (delivery accepts NULL under any token)");
        assertRefused(field("array:@AtomicSerial", seqOf()), "array:@AtomicSerial without a class");
        assertAdmitted(field("array:@AtomicSerial", NULL_TLV), "NULL under it");
        assertRefused(field("list:array:@AtomicSerial:fx.Other", seqOf(seqOf())), "an array of records inside a collection");
        assertAdmitted(field("list:array:@AtomicSerial:fx.Other", seqOf(NULL_TLV)), "a NULL one");
        assertRefused(field("frobnicate", i(1)), "an unknown wire type");
        assertRefused(field("array:float", seqOf(octets(1, 2, 3, 4))), "array:float field");
    }

    // ------------------------------------------------------------------ Any

    private static byte[] anyScalar(int tag, byte[] content) {
        return DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, tag), content);
    }

    private static byte[] anyColl(int tag, byte[] inner) {
        return DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, tag), inner);
    }

    private static byte[] anyInt(long v) {
        return anyScalar(3, java.math.BigInteger.valueOf(v).toByteArray());
    }

    @Test
    void anAnyValueIsCheckedLikeADeclaredOne() {
        assertAdmitted(field("any", anyInt(5)), "Any int");
        assertRefused(field("any", anyScalar(5, new byte[] {(byte) 0x80, 0, 0, 0})), "Any float -0.0");
        assertRefused(field("any", anyScalar(8, new byte[] {(byte) 0xC3, 0x28})), "Any String bad UTF-8");
        assertRefused(field("any", anyScalar(3, new byte[] {0, 5})), "Any int non-minimal");
        // [30] canonicalise collection: a SET, strictly ascending
        assertAdmitted(field("any", anyColl(30, setOf(anyInt(1), anyInt(2)))), "[30] ascending");
        assertRefused(field("any", anyColl(30, setOf(anyInt(2), anyInt(1)))), "[30] descending");
        assertRefused(field("any", anyColl(30, setOf(anyInt(1), anyInt(1)))), "[30] duplicate");
        assertRefused(field("any", anyColl(30, seqOf(anyInt(1)))), "[30] over a SEQUENCE");
        assertRefused(field("any", anyColl(31, setOf(anyInt(1)))), "[31] over a SET");
        assertAdmitted(field("any", anyColl(31, seqOf(anyInt(2), anyInt(1)))), "[31] keeps its order");
        assertRefused(field("any", anyColl(31, seqOf(anyInt(1), anyScalar(8, new byte[] {(byte) 0xC3, 0x28})))), "[31] bad element");
        // maps: entries are SEQUENCE{key,value}; keys strictly ascending
        assertAdmitted(field("any", anyColl(30, setOf(entry(anyInt(1), anyInt(9)), entry(anyInt(2), anyInt(8))))), "Any map");
        assertRefused(field("any", anyColl(30, setOf(entry(anyInt(2), anyInt(9)), entry(anyInt(1), anyInt(8))))), "Any map keys descending");
        assertRefused(field("any", anyColl(30, setOf(entry(anyInt(1), anyInt(9)), anyInt(2)))), "Any collection mixing entries and elements");
        assertRefused(field("any", anyColl(30, setOf(entry(anyInt(1), anyScalar(5, new byte[] {1}))))), "Any map value bad float");
    }

    @Test
    void aCollectionOfMoreThanMaxCollectionElementsInTheWalkedPathIsRefused() {
        // list:any holds an Any element, so the walked (not raw) path counts it
        List<byte[]> max = new ArrayList<>();
        for (int n = 0; n < 65536; n++) max.add(anyInt(n));
        assertAdmitted(field("list:any", DerWriter.writeSequence(max)), "65536 Any elements");
        max.add(anyInt(65536));
        assertRefused(field("list:any", DerWriter.writeSequence(max)), "65537 Any elements");
    }

    /** A holder with a canonicalise map keyed by records (sorted by key encoding when written). */
    @AtomicSerial
    public static final class MapHolder {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", Map.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, MapHolder h) throws IOException {
            arg.put("items", h.items);
            arg.writeArgs();
        }
        final Map<Wide, Integer> items;
        MapHolder(Map<Wide, Integer> items) { this.items = items; }
        public MapHolder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            CONSTRUCTED.incrementAndGet();
            items = arg.get("items", Collections.emptyMap(), Map.class);
        }
    }

    /** A holder with a canonicalise set of records (sorted by encoding when written). */
    @AtomicSerial
    public static final class Holder {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("items", Set.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Holder h) throws IOException {
            arg.put("items", h.items);
            arg.writeArgs();
        }
        final Set<Wide> items;
        Holder(Set<Wide> items) { this.items = items; }
        public Holder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            CONSTRUCTED.incrementAndGet();
            items = arg.get("items", Collections.emptySet(), Set.class);
        }
    }
}
