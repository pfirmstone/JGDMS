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

import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.marshal.DerMarshalInstanceOutput;
import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end wire vectors for the STD-006 Appendix C stream schema dedup (the
 * sec.C.11 core set that rides T2; T3 carries the full adversarial corpus):
 * version-octet vectors, the mandatory-dedup enforcement vector (duplicate full
 * form), unknown-digest and cross-stream rejects, the {@code Node} self-recursion
 * two-leg pre-order vector (sec.C.6.2 / sec.C.11.3(12)), format-mixing rejects,
 * the value-boundary vector (sec.C.11.3(9)), the {@code [8]} exclusion-transitivity
 * vector and the boomerang negative leg (sec.C.11.3(14)-(15)), round-trip
 * byte-exactness (sec.C.5.4), P2-first/P1-first mixed occurrence ordering, the
 * capture-context boundary (sec.C.11.3(13)), and the representative bulk shape
 * with a wire-shrink measurement.
 */
class DerStreamSchemaDedupTest {

    private static final byte[] VERSION = { (byte) 0x8F, 0x01, 0x01 };
    private static final Tag CTX_ATOMIC = new Tag(Tag.CLASS_CONTEXT, true, 1);
    private static final Tag CTX_PROXY  = new Tag(Tag.CLASS_CONTEXT, true, 8);
    private static final Tag FULL = new Tag(Tag.CLASS_CONTEXT, false, 0);   // 0x80
    private static final Tag REF  = new Tag(Tag.CLASS_CONTEXT, false, 1);   // 0x81

    // =========================================================================
    // Fixtures
    // =========================================================================

    /** Self-recursive type (sec.C.6.2's traversal-order forcing case). */
    @AtomicSerial
    public static final class Node {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("next", Node.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, Node n) throws IOException {
            arg.put("next", n.next);
            arg.writeArgs();
        }
        private final Node next;
        public Node(Node next) { this.next = next; }
        public Node(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.next = (Node) arg.get("next", null);
        }
        public Node next() { return next; }
    }

    /** A byte[] field carrier for the value-boundary vector (sec.C.4.3/C.11.3(9)). */
    @AtomicSerial
    public static final class BytesHolder {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("blob", byte[].class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, BytesHolder h) throws IOException {
            arg.put("blob", h.blob);
            arg.writeArgs();
        }
        private final byte[] blob;
        public BytesHolder(byte[] blob) { this.blob = blob; }
        public BytesHolder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.blob = (byte[]) arg.get("blob", null);
        }
        public byte[] blob() { return blob; }
    }

    /** Auto-wired collection field: {@code List<VersionedRecord>} → {@code list:@AtomicSerial}. */
    @AtomicSerial
    public static final class RecordListHolder {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("items", List.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, RecordListHolder h) throws IOException {
            arg.put("items", h.items);
            arg.writeArgs();
        }
        private final List<VersionedRecord> items;
        public RecordListHolder(List<VersionedRecord> items) { this.items = items; }
        @SuppressWarnings("unchecked")
        public RecordListHolder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            Object v = arg.get("items", null);
            this.items = (v == null) ? null : new ArrayList<>((List<VersionedRecord>) v);
        }
        public List<VersionedRecord> items() { return items; }
    }

    public interface Greet {
        String greet();
    }

    /** {@code InvocationHandler} with an {@code @AtomicSerial} field (transitivity vector). */
    @AtomicSerial
    public static final class GreetHandler implements InvocationHandler {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("rec", VersionedRecord.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, GreetHandler h) throws IOException {
            arg.put("rec", h.rec);
            arg.writeArgs();
        }
        private final VersionedRecord rec;
        public GreetHandler(VersionedRecord rec) { this.rec = rec; }
        public GreetHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.rec = (VersionedRecord) arg.get("rec", null);
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "greet"    -> rec == null ? "?" : rec.getLabel();
                case "toString" -> "GreetProxy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals"   -> proxy == (args == null ? null : args[0]);
                default         -> null;
            };
        }
    }

    // =========================================================================
    // Stream helpers
    // =========================================================================

    private static byte[] encodeStream(Object... objs) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        for (Object o : objs) c.writeObject(o);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    private static List<Object> decodeStream(byte[] bytes, int count)
            throws IOException, ClassNotFoundException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.initReader(bytes);
        List<Object> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(c.readObject());
        return out;
    }

    private static IOException decodeReject(byte[] bytes, int itemsToRead) {
        return assertThrows(IOException.class, () -> decodeStream(bytes, itemsToRead));
    }

    /** Reads one complete TLV's bytes from {@code r}. */
    private static byte[] tlv(DerReader r) throws Exception {
        int start = r.position();
        DerReader.TlvHeader hdr = r.readTlvHeader();
        r.readRawContent(hdr.contentLength());
        return r.slice(start, r.position());
    }

    /** Splits a stream into its item TLVs, asserting the version-octet prefix. */
    private static List<byte[]> items(byte[] stream) throws Exception {
        assertEquals((byte) 0x8F, stream[0], "stream must begin with the version octet");
        assertEquals((byte) 0x01, stream[1]);
        assertEquals((byte) 0x01, stream[2]);
        DerReader r = new DerReader(stream);
        tlv(r);                                     // consume version TLV
        List<byte[]> out = new ArrayList<>();
        while (r.hasMore()) out.add(tlv(r));
        return out;
    }

    /** A stream assembled from raw item TLVs (crafting seam). */
    private static byte[] stream(byte[]... itemTlvs) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.writeBytes(VERSION);
        for (byte[] i : itemTlvs) bos.writeBytes(i);
        return bos.toByteArray();
    }

    /** Parsed stream-form P1 record: {@code SEQ{payload OCTET, schema, format UTF8}}. */
    private record P1(byte[] payload, Tag schemaTag, byte[] schemaContent) {}

    private static P1 parseP1Item(byte[] itemTlv) throws Exception {
        DerReader r = new DerReader(itemTlv);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        assertEquals(CTX_ATOMIC, hdr.tag(), "expected a [1] item");
        DerReader seq = new DerReader(r.readRawContent(hdr.contentLength())).readSequence();
        byte[] payload = seq.readOctetString();
        DerReader.TlvHeader schema = seq.readTlvHeader();
        byte[] schemaContent = seq.readRawContent(schema.contentLength());
        String format = seq.readUtf8String();
        assertEquals(MarshalledInstanceRecord.PAYLOAD_FORMAT, format);
        assertFalse(seq.hasMore(), "stream P1 record is exactly three fields");
        return new P1(payload, schema.tag(), schemaContent);
    }

    /** The nested-record field TLVs of a single-class hierarchy payload. */
    private static List<byte[]> classFieldTlvs(byte[] hierarchyPayload) throws Exception {
        DerReader outer = new DerReader(hierarchyPayload);
        DerReader hier = outer.readSequence();
        DerReader classSeq = hier.readSequence();
        assertFalse(hier.hasMore(), "single-class hierarchy expected");
        List<byte[]> out = new ArrayList<>();
        while (classSeq.hasMore()) out.add(tlv(classSeq));
        return out;
    }

    /** Schema-arm tag of a stream-form nested record {@code SEQ{schema, payload OCTET}}. */
    private static Tag nestedSchemaTag(byte[] nestedTlv) throws Exception {
        DerReader seq = new DerReader(nestedTlv).readSequence();
        return seq.readTlvHeader().tag();
    }

    private static byte[] chainBytesOf(Class<?> cls) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(cls);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        chain.chain().forEach(rec -> buf.writeBytes(rec.encode()));
        return buf.toByteArray();
    }

    private static byte[] canonicalRecordOf(Object obj) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(obj.getClass());
        return MarshalledInstanceRecord.fromChain(
                chain, ObjectCodec.encodeHierarchy(obj, chain)).encode();
    }

    // =========================================================================
    // 1. Version-octet vectors (sec.C.5.2 / sec.C.11.3(5))
    // =========================================================================

    @Test
    void versionOctet_emptyStreamIsExactlyTheVersionTlv() throws Exception {
        byte[] stream = encodeStream();          // zero items (degenerate, C.11.3(11))
        assertArrayEquals(VERSION, stream,
                "an empty stream is exactly the version TLV 8F 01 01");
        // Constructing a reader over it succeeds (valid empty stream, empty table).
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.initReader(stream);
        assertEquals(0, c.available());
    }

    @Test
    void versionOctet_missing_supersededTrunkShape_rejectedAtFirstTlv() throws Exception {
        // A stream whose first TLV is an ITEM (the superseded trunk format's shape).
        byte[] deduped = encodeStream(new VersionedRecord(1, "a", "x"));
        byte[] item = items(deduped).get(0);
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        IOException e = assertThrows(IOException.class, () -> c.initReader(item));
        assertTrue(e.getMessage().contains("version octet"),
                "must be rejected at the first TLV: " + e.getMessage());
    }

    @Test
    void versionOctet_unknownVersions_rejected() {
        for (int v : new int[] { 0x00, 0x02, 0xFF }) {
            byte[] stream = { (byte) 0x8F, 0x01, (byte) v };
            DerObjectStreamCodec c = new DerObjectStreamCodec();
            IOException e = assertThrows(IOException.class, () -> c.initReader(stream));
            assertTrue(e.getMessage().contains("unknown stream-format version"),
                    "version 0x" + Integer.toHexString(v) + ": " + e.getMessage());
        }
    }

    @Test
    void versionOctet_wrongContentLength_rejected() {
        for (byte[] stream : new byte[][] {
                { (byte) 0x8F, 0x00 },                       // length 0
                { (byte) 0x8F, 0x02, 0x00, 0x01 } }) {       // length 2
            DerObjectStreamCodec c = new DerObjectStreamCodec();
            IOException e = assertThrows(IOException.class, () -> c.initReader(stream));
            assertTrue(e.getMessage().contains("version octet"), e.getMessage());
        }
    }

    @Test
    void versionOctet_emptyBuffer_rejected() {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        assertThrows(IOException.class, () -> c.initReader(new byte[0]));
    }

    @Test
    void versionOctet_secondMidStream_rejected() throws Exception {
        byte[] stream = stream(VERSION);         // ver + ver
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.initReader(stream);
        IOException e = assertThrows(IOException.class, c::readObject);
        assertTrue(e.getMessage().contains("unexpected context tag"),
                "a second [15] is not an item: " + e.getMessage());
    }

    // =========================================================================
    // 2. Dedup wire shape + mandatory-dedup enforcement (sec.C.6 / C.9.2)
    // =========================================================================

    @Test
    void dedup_firstOccurrenceFull_subsequentRef_andWireShrinks() throws Exception {
        VersionedRecord a = new VersionedRecord(1, "a", "x");
        VersionedRecord b = new VersionedRecord(2, "b", "y");
        byte[] stream = encodeStream(a, b);
        List<byte[]> items = items(stream);
        assertEquals(2, items.size());
        P1 p1 = parseP1Item(items.get(0));
        P1 p2 = parseP1Item(items.get(1));
        assertEquals(FULL, p1.schemaTag(), "first occurrence must be fullChain [0]");
        assertEquals(REF, p2.schemaTag(), "subsequent occurrence must be chainRef [1]");
        assertEquals(32, p2.schemaContent().length);
        assertArrayEquals(chainBytesOf(VersionedRecord.class), p1.schemaContent(),
                "fullChain content is the canonical chain bytes");
        assertTrue(items.get(1).length < items.get(0).length,
                "the reference form must shrink the wire");
        // Round trip.
        List<Object> back = decodeStream(stream, 2);
        assertEquals(a, back.get(0));
        assertEquals(b, back.get(1));
    }

    @Test
    void mandatoryDedup_duplicateFullForm_rejected() throws Exception {
        // Precisely the stream a NON-deduping (per-occurrence) encoder would produce:
        // two byte-identical full-form items. Every conformant decoder rejects it —
        // the mandatory-dedup enforcement vector (sec.C.9.2 / C.11.3(2)).
        byte[] one = encodeStream(new VersionedRecord(1, "a", "x"));
        byte[] item = items(one).get(0);
        byte[] nonDeduped = stream(item, item);
        IOException e = decodeReject(nonDeduped, 2);
        assertTrue(e.getMessage().contains("duplicate fullChain"),
                "non-deduped output is a malformed stream: " + e.getMessage());
    }

    @Test
    void unknownDigest_and_crossStreamIsolation_rejected() throws Exception {
        // Stream A: full-then-ref. Stream B: A's REF item alone — same digest,
        // fresh table — must reject as unknown-digest (sec.C.7.5 / C.11.3(1),(10)).
        byte[] a = encodeStream(new VersionedRecord(1, "a", "x"),
                                new VersionedRecord(2, "b", "y"));
        byte[] refItem = items(a).get(1);
        IOException e = decodeReject(stream(refItem), 1);
        assertTrue(e.getMessage().contains("unknown-digest"),
                "cross-stream references must be meaningless: " + e.getMessage());
    }

    @Test
    void interleavedStreams_maintainIndependentTables() throws Exception {
        // Two encoders interleaved: each stream's first occurrence is FULL — no
        // cross-stream table sharing on the encode side either.
        DerObjectStreamCodec c1 = new DerObjectStreamCodec();
        DerObjectStreamCodec c2 = new DerObjectStreamCodec();
        c1.writeObject(new VersionedRecord(1, "a", "x"));
        c2.writeObject(new VersionedRecord(2, "b", "y"));
        ByteArrayOutputStream b1 = new ByteArrayOutputStream();
        ByteArrayOutputStream b2 = new ByteArrayOutputStream();
        c1.drainTo(b1);
        c2.drainTo(b2);
        assertEquals(FULL, parseP1Item(items(b1.toByteArray()).get(0)).schemaTag());
        assertEquals(FULL, parseP1Item(items(b2.toByteArray()).get(0)).schemaTag());
    }

    // =========================================================================
    // 3. Format mixing (sec.C.11.3(6),(7))
    // =========================================================================

    @Test
    void canonicalFourFieldRecordAtStreamSite_rejected() throws Exception {
        // The sec.7.8 record (schemaBytes as OCTET STRING at position 2) is not a
        // stream production.
        byte[] canonical = canonicalRecordOf(new VersionedRecord(1, "a", "x"));
        byte[] item = DerWriter.writeTlv(CTX_ATOMIC, canonical);
        IOException e = decodeReject(stream(item), 1);
        assertTrue(e.getMessage().contains("SchemaChainRef"),
                "canonical record-level form at a stream site: " + e.getMessage());
    }

    @Test
    void schemaDigestReinserted_fourFieldStreamRecord_rejected() throws Exception {
        // A stream P1 record with the dropped schemaDigest field re-inserted
        // (sec.C.11.3(7)): payload, fullChain, digest, format.
        byte[] chain = chainBytesOf(VersionedRecord.class);
        SchemaChain.Result cr = SchemaGenerator.generateChain(VersionedRecord.class);
        byte[] payload = ObjectCodec.encodeHierarchy(new VersionedRecord(1, "a", "x"), cr);
        List<byte[]> children = new ArrayList<>(4);
        children.add(DerWriter.writeOctetString(payload));
        children.add(DerWriter.writeTlv(FULL, chain));
        children.add(DerWriter.writeOctetString(cr.leafDigest()));
        children.add(DerWriter.writeUtf8String(MarshalledInstanceRecord.PAYLOAD_FORMAT));
        byte[] item = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(children));
        assertThrows(IOException.class, () -> decodeStream(stream(item), 1),
                "the schemaDigest field is unrepresentable in the stream form");
    }

    // =========================================================================
    // 4. Node self-recursion: pre-order two-leg vector (sec.C.6.2 / C.11.3(12))
    // =========================================================================

    @Test
    void node_outerSiteFull_nestedSiteRef_evenThoughNestedBytesComeFirst() throws Exception {
        Node node = new Node(new Node((Node) null));
        byte[] stream = encodeStream(node);
        P1 p1 = parseP1Item(items(stream).get(0));
        // Outer site (processed first in record-entry pre-order): fullChain.
        assertEquals(FULL, p1.schemaTag());
        // The nested site's bytes appear EARLIER in the stream (P1 payload precedes
        // the record's own chain field) yet carry the REFERENCE — the byte-order vs
        // traversal-order distinction the spec pins.
        List<byte[]> fields = classFieldTlvs(p1.payload());
        assertEquals(1, fields.size());
        assertEquals(REF, nestedSchemaTag(fields.get(0)),
                "nested occurrence must be the reference form");
        // Round trip.
        Node back = (Node) decodeStream(stream, 1).get(0);
        assertNotNull(back.next());
        assertNull(back.next().next());
    }

    @Test
    void node_inverseLegs_rejected() throws Exception {
        // Craft both byte-order-tempting inverses; a conformant (traversal-order)
        // decoder rejects each.
        byte[] chain = chainBytesOf(Node.class);
        byte[] digest = SchemaGenerator.generateChain(Node.class).leafDigest();
        SchemaChain.Result cr = SchemaGenerator.generateChain(Node.class);
        byte[] innerHier = ObjectCodec.encodeHierarchy(new Node((Node) null), cr);

        byte[] nestedFull = DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(FULL, chain),
                DerWriter.writeOctetString(innerHier)));
        byte[] outerHierWithNestedFull = DerWriter.writeSequence(List.of(
                DerWriter.writeSequence(List.of(nestedFull))));

        // Leg a: outer REF + nested FULL — the outer site is processed first and its
        // digest has no prior fullChain: unknown-digest reject.
        byte[] legA = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(outerHierWithNestedFull),
                DerWriter.writeTlv(REF, digest),
                DerWriter.writeUtf8String(MarshalledInstanceRecord.PAYLOAD_FORMAT))));
        IOException ea = decodeReject(stream(legA), 1);
        assertTrue(ea.getMessage().contains("unknown-digest"), ea.getMessage());

        // Leg b: outer FULL + nested FULL — the nested site is a duplicate full form.
        byte[] legB = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(outerHierWithNestedFull),
                DerWriter.writeTlv(FULL, chain),
                DerWriter.writeUtf8String(MarshalledInstanceRecord.PAYLOAD_FORMAT))));
        IOException eb = decodeReject(stream(legB), 1);
        assertTrue(eb.getMessage().contains("duplicate fullChain"), eb.getMessage());

        // Positive control: outer FULL + nested REF (the conformant shape) decodes.
        byte[] nestedRef = DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(REF, digest),
                DerWriter.writeOctetString(innerHier)));
        byte[] good = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(DerWriter.writeSequence(List.of(
                        DerWriter.writeSequence(List.of(nestedRef))))),
                DerWriter.writeTlv(FULL, chain),
                DerWriter.writeUtf8String(MarshalledInstanceRecord.PAYLOAD_FORMAT))));
        Node back = (Node) decodeStream(stream(good), 1).get(0);
        assertNotNull(back.next());
    }

    // =========================================================================
    // 5. Value boundary: chain bytes inside a byte[] VALUE never dedup (sec.C.4.3)
    // =========================================================================

    @Test
    void valueBoundary_chainBytesInsideByteArrayValue_travelVerbatim_neverTabled()
            throws Exception {
        byte[] nodeChain = chainBytesOf(Node.class);
        byte[] nodeDigest = SchemaGenerator.generateChain(Node.class).leafDigest();
        BytesHolder holder = new BytesHolder(nodeChain);
        byte[] stream = encodeStream(holder);
        // The blob's octets (recognisably chain bytes) travel verbatim inside the
        // payload value.
        P1 p1 = parseP1Item(items(stream).get(0));
        assertTrue(indexOf(p1.payload(), nodeChain) >= 0,
                "value octets must travel verbatim (rule 2)");
        // A reference that could only resolve against those value-interior bytes
        // rejects as unknown-digest: values never populate the table.
        byte[] refItem = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(DerWriter.writeSequence(List.of(
                        DerWriter.writeSequence(List.of(new byte[] { 0x05, 0x00 }))))),
                DerWriter.writeTlv(REF, nodeDigest),
                DerWriter.writeUtf8String(MarshalledInstanceRecord.PAYLOAD_FORMAT))));
        byte[] crafted = stream(items(stream).get(0), refItem);
        IOException e = decodeReject(crafted, 2);
        assertTrue(e.getMessage().contains("unknown-digest"), e.getMessage());
        // And the holder itself round-trips byte-preserving.
        BytesHolder back = (BytesHolder) decodeStream(stream, 1).get(0);
        assertArrayEquals(nodeChain, back.blob());
    }

    // =========================================================================
    // 6. [8] exclusion: transitivity + boomerang negative leg (sec.C.6.5, C.11.3(14),(15))
    // =========================================================================

    @Test
    void proxyInterior_neverPopulatesTable_outsideSiteAfterProxyIsStillFull()
            throws Exception {
        // Proxy FIRST (its handler carries a VersionedRecord interior record), then a
        // top-level VersionedRecord: if the interior had populated the table the
        // outside site would be a REF; it must be FULL (interior never counts).
        Greet proxy = (Greet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { Greet.class },
                new GreetHandler(new VersionedRecord(7, "inner", "i")));
        byte[] stream = encodeStream(proxy, new VersionedRecord(8, "outer", "o"));
        List<byte[]> items = items(stream);
        assertEquals((byte) 0xA8, items.get(0)[0], "[8] item expected");
        assertEquals(FULL, parseP1Item(items.get(1)).schemaTag(),
                "an outside site AFTER the [8] follows the outside-only sequence");
        // Interior is canonical record-level form: the handler's [1] record inside the
        // [8] content has OCTET STRING (not a SchemaChainRef arm) at field 2.
        assertProxyInteriorCanonical(items.get(0));
        // Round trip: the proxy still works and the table sequencing decodes.
        List<Object> back = decodeStream(stream, 2);
        assertEquals("inner", ((Greet) back.get(0)).greet());
        assertEquals(new VersionedRecord(8, "outer", "o"), back.get(1));
    }

    @Test
    void proxyInterior_neverConsultsTable_outsideFirstThenProxyStaysCanonical()
            throws Exception {
        // Outside site FIRST, then the proxy: the interior VersionedRecord record must
        // STILL be canonical full form (never a reference), because no chainRef may
        // appear inside a [8] region.
        Greet proxy = (Greet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { Greet.class },
                new GreetHandler(new VersionedRecord(7, "inner", "i")));
        byte[] stream = encodeStream(new VersionedRecord(8, "outer", "o"), proxy);
        List<byte[]> items = items(stream);
        assertEquals(FULL, parseP1Item(items.get(0)).schemaTag());
        assertProxyInteriorCanonical(items.get(1));
        List<Object> back = decodeStream(stream, 2);
        assertEquals("inner", ((Greet) back.get(1)).greet());
    }

    /** Asserts the [8] item's handler record is the canonical sec.7.8 four-field form. */
    private static void assertProxyInteriorCanonical(byte[] proxyItemTlv) throws Exception {
        DerReader r = new DerReader(proxyItemTlv);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        assertEquals(CTX_PROXY, hdr.tag());
        DerReader content = new DerReader(r.readRawContent(hdr.contentLength()));
        int count = content.readInteger().intValueExact();
        for (int i = 0; i < count; i++) content.readUtf8String();
        DerReader.TlvHeader handler = content.readTlvHeader();
        assertEquals(CTX_ATOMIC, handler.tag());
        DerReader rec = new DerReader(content.readRawContent(handler.contentLength()))
                .readSequence();
        rec.readOctetString();                             // payloadBytes
        byte[] schemaBytes = rec.readOctetString();        // OCTET STRING, not [0]/[1]
        assertTrue(schemaBytes.length > 0, "canonical schemaBytes expected");
        assertEquals(32, rec.readOctetString().length,     // schemaDigest present
                "canonical record retains its schemaDigest field");
        // Transitively: the handler's own @AtomicSerial field record (inside its
        // payload) is also canonical — verified by the round-trip decodes above and
        // by the negative leg below (any SchemaChainRef inside [8] rejects).
    }

    @Test
    void boomerang_negativeLeg_streamProductionInsideProxyInterior_rejected()
            throws Exception {
        // A synthetic retained-form [8] whose handler record uses the STREAM form
        // (SchemaChainRef arm) — both arms probed — must reject on decode: stream
        // productions are unrepresentable inside a [8] region (sec.C.6.5, C.11.3(14)
        // negative leg / C.11.3(6)).
        byte[] chain = chainBytesOf(GreetHandler.class);
        byte[] digest = SchemaGenerator.generateChain(GreetHandler.class).leafDigest();
        SchemaChain.Result cr = SchemaGenerator.generateChain(GreetHandler.class);
        byte[] payload = ObjectCodec.encodeHierarchy(
                new GreetHandler(new VersionedRecord(1, "a", "x")), cr);
        for (byte[] arm : new byte[][] {
                DerWriter.writeTlv(FULL, chain),
                DerWriter.writeTlv(REF, digest) }) {
            byte[] streamFormRecord = DerWriter.writeSequence(List.of(
                    DerWriter.writeOctetString(payload),
                    arm,
                    DerWriter.writeUtf8String(MarshalledInstanceRecord.PAYLOAD_FORMAT)));
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            content.writeBytes(DerWriter.writeInteger(BigInteger.ONE));
            content.writeBytes(DerWriter.writeUtf8String(Greet.class.getName()));
            content.writeBytes(DerWriter.writeTlv(CTX_ATOMIC, streamFormRecord));
            byte[] proxyItem = DerWriter.writeTlv(CTX_PROXY, content.toByteArray());
            assertThrows(IOException.class, () -> decodeStream(stream(proxyItem), 1),
                    "a SchemaChainRef inside a [8] interior must reject");
        }
    }

    @Test
    void boomerangRelay_retainedInteriorDecodesInLaterDistinctStreams() throws Exception {
        // sec.C.11.3(14) positive leg at the T2 level: a [8] proxy decoded from stream
        // A re-forwards into later, DISTINCT streams byte-verbatim — possible exactly
        // because the interior was never dedup-encoded. (Full narrowing-based relay
        // coverage lives in DerObjectStreamBoomerangProxyTest, running green with
        // dedup live.)
        Greet proxy = (Greet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { Greet.class },
                new GreetHandler(new VersionedRecord(7, "relay", "r")));
        byte[] a = encodeStream(proxy);
        Greet hop1 = (Greet) decodeStream(a, 1).get(0);
        byte[] b = encodeStream(hop1);              // later, distinct stream
        Greet hop2 = (Greet) decodeStream(b, 1).get(0);
        assertEquals("relay", hop2.greet());
        // The [8] interiors of both streams are canonical and identical.
        assertArrayEquals(items(a).get(0), items(b).get(0),
                "re-forwarded [8] item must be byte-identical across streams");
    }

    // =========================================================================
    // 7. P2 sites: [9] array elements and collection fields; mixed P1/P2 ordering
    // =========================================================================

    @Test
    void arrayElements_dedup_andP1AfterP2IsRef() throws Exception {
        VersionedRecord a = new VersionedRecord(1, "a", "x");
        VersionedRecord b = new VersionedRecord(2, "b", "y");
        VersionedRecord c = new VersionedRecord(3, "c", "z");
        byte[] stream = encodeStream(new VersionedRecord[] { a, b }, c);
        List<byte[]> items = items(stream);
        // [9] item: UTF8(arrayWireType) ++ SEQUENCE(elements) — elem 1 full, elem 2 ref.
        DerReader r = new DerReader(items.get(0));
        DerReader.TlvHeader hdr = r.readTlvHeader();
        assertEquals(new Tag(Tag.CLASS_CONTEXT, true, 9), hdr.tag());
        DerReader content = new DerReader(r.readRawContent(hdr.contentLength()));
        assertTrue(content.readUtf8String().startsWith("array:@AtomicSerial:"));
        DerReader elems = content.readSequence();
        assertEquals(FULL, nestedSchemaTag(tlv(elems)), "element 1: first occurrence");
        assertEquals(REF, nestedSchemaTag(tlv(elems)), "element 2: reference");
        // The [1] item AFTER the P2 first occurrence is a reference (mixed-site
        // ordering, sec.C.11.2).
        assertEquals(REF, parseP1Item(items.get(1)).schemaTag());
        // Round trip.
        List<Object> back = decodeStream(stream, 2);
        assertArrayEquals(new VersionedRecord[] { a, b }, (VersionedRecord[]) back.get(0));
        assertEquals(c, back.get(1));
    }

    @Test
    void p1First_thenArrayElements_bothRefs() throws Exception {
        VersionedRecord a = new VersionedRecord(1, "a", "x");
        byte[] stream = encodeStream(a, new VersionedRecord[] { a,
                new VersionedRecord(2, "b", "y") });
        List<byte[]> items = items(stream);
        assertEquals(FULL, parseP1Item(items.get(0)).schemaTag());
        DerReader r = new DerReader(items.get(1));
        DerReader.TlvHeader hdr = r.readTlvHeader();
        DerReader content = new DerReader(r.readRawContent(hdr.contentLength()));
        content.readUtf8String();
        DerReader elems = content.readSequence();
        assertEquals(REF, nestedSchemaTag(tlv(elems)));
        assertEquals(REF, nestedSchemaTag(tlv(elems)));
        decodeStream(stream, 2);
    }

    @Test
    void collectionField_elementsDedup_inWireOrder() throws Exception {
        VersionedRecord a = new VersionedRecord(1, "a", "x");
        VersionedRecord b = new VersionedRecord(2, "b", "y");
        RecordListHolder holder = new RecordListHolder(new ArrayList<>(List.of(a, b)));
        byte[] stream = encodeStream(holder, new VersionedRecord(3, "c", "z"));
        List<byte[]> items = items(stream);
        P1 p1 = parseP1Item(items.get(0));
        assertEquals(FULL, p1.schemaTag(), "holder chain: first occurrence");
        // Field 0 is the list SEQUENCE; its element records dedup in wire order.
        List<byte[]> fields = classFieldTlvs(p1.payload());
        DerReader list = new DerReader(fields.get(0)).readSequence();
        assertEquals(FULL, nestedSchemaTag(tlv(list)), "list element 1: first VR occurrence");
        assertEquals(REF, nestedSchemaTag(tlv(list)), "list element 2: reference");
        // The top-level VR after the holder is a reference (its identity was tabled
        // at the P2 collection site).
        assertEquals(REF, parseP1Item(items.get(1)).schemaTag());
        // Round trip.
        List<Object> back = decodeStream(stream, 2);
        assertEquals(List.of(a, b), ((RecordListHolder) back.get(0)).items());
        assertEquals(new VersionedRecord(3, "c", "z"), back.get(1));
    }

    // =========================================================================
    // 8. Round-trip byte-exactness (sec.C.5.4 / C.11.1(4))
    // =========================================================================

    @Test
    void roundTrip_reEncodeOfDecodedObjects_reproducesCanonicalFullForm() throws Exception {
        Object[] values = {
            new VersionedRecord(1, "a", "x"),
            new Node(new Node((Node) null)),
            new RecordListHolder(new ArrayList<>(List.of(
                    new VersionedRecord(1, "a", "x"), new VersionedRecord(2, "b", "y")))),
        };
        // One stream carrying all three (chains dedup across items), decoded, then
        // each decoded object re-encoded in a NON-STREAM (canonical record-level)
        // context: byte-identical to the canonical encoding of the original.
        byte[] stream = encodeStream(values);
        List<Object> back = decodeStream(stream, values.length);
        for (int i = 0; i < values.length; i++) {
            assertArrayEquals(canonicalRecordOf(values[i]), canonicalRecordOf(back.get(i)),
                    "re-encode of decoded value " + i + " must reproduce canonical bytes");
        }
    }

    // =========================================================================
    // 9. Capture-context boundary (sec.C.1.2 item 3 / C.11.3(13))
    // =========================================================================

    @Test
    void captureContext_noVersionOctet_noStreamProductions() throws Exception {
        // The MarshalledInstance capture path's empty-schema sentinel (a bare proxy)
        // produces record-level bytes: no [15] version octet, canonical form only.
        Greet proxy = (Greet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { Greet.class },
                new GreetHandler(new VersionedRecord(7, "cap", "c")));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DerMarshalInstanceOutput out = new DerMarshalInstanceOutput(
                bos, new ArrayList<>(), false);
        out.writeObject(proxy);
        byte[] captured = bos.toByteArray();
        assertEquals((byte) 0xA8, captured[0],
                "capture bytes must begin with the [8] item, not a version octet");
        assertEquals(0, out.getSchemaBytes().length, "empty-schema sentinel expected");
        // And the capture-mode reader decodes them (no version octet expected).
        DerMarshalInputStream in = DerMarshalInputStream.recordLevelCapture(
                new ByteArrayInputStream(captured),
                au.net.zeus.jgdms.der.getarg.ResolutionContext.NONE);
        Greet back = (Greet) in.readObject();
        assertEquals("cap", back.greet());
    }

    // =========================================================================
    // 10. Representative bulk shape: the wire actually shrinks (measured)
    // =========================================================================

    @Test
    void bulkShape_oneFullChain_thenRefs_wireShrinkMeasured() throws Exception {
        int n = 100;
        Object[] records = new Object[n];
        for (int i = 0; i < n; i++) {
            records[i] = new VersionedRecord(i, "label-" + i, "extra-" + i);
        }
        byte[] stream = encodeStream(records);
        List<byte[]> items = items(stream);
        assertEquals(n, items.size());
        int fulls = 0, refs = 0;
        for (byte[] item : items) {
            P1 p = parseP1Item(item);
            if (FULL.equals(p.schemaTag())) fulls++;
            else if (REF.equals(p.schemaTag())) refs++;
        }
        assertEquals(1, fulls, "N same-class entries: exactly one fullChain");
        assertEquals(n - 1, refs, "N same-class entries: N-1 references");

        // Baseline: the superseded per-occurrence format = one canonical full record
        // per item (no version octet). Measured, not assumed.
        long baseline = 0;
        for (Object r : records) {
            byte[] canonical = canonicalRecordOf(r);
            baseline += DerWriter.writeTlv(CTX_ATOMIC, canonical).length;
        }
        long deduped = stream.length;
        long chainLen = chainBytesOf(VersionedRecord.class).length;
        System.out.printf(
                "[wire-shrink] N=%d VersionedRecord items: superseded=%d B, deduped=%d B"
                + " (%.1f%% of baseline; chain=%d B, 1 full + %d refs of 34 B)%n",
                n, baseline, deduped, 100.0 * deduped / baseline, chainLen, n - 1);
        assertTrue(deduped < baseline,
                "the dedup stream must be smaller than the per-occurrence baseline");
        // The shrink must be roughly (N-1) x (chainLen + digestField) minus the
        // reference cost — assert a conservative floor: at least (N-1) x (chainLen - 40).
        assertTrue(baseline - deduped >= (long) (n - 1) * (chainLen - 40),
                "shrink must scale with the removed chain repetitions");
        // All N round-trip.
        List<Object> back = decodeStream(stream, n);
        assertEquals(records[n - 1], back.get(n - 1));
        assertEquals(records[0], back.get(0));
    }

    // =========================================================================
    // 11. Degenerates (sec.C.11.3(11))
    // =========================================================================

    @Test
    void degenerate_primitivesOnlyStream_valid() throws Exception {
        DerObjectStreamCodec w = new DerObjectStreamCodec();
        w.writeInt(42);
        w.writeUTF("plain");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        w.drainTo(bos);
        byte[] stream = bos.toByteArray();
        assertEquals((byte) 0x8F, stream[0]);
        DerObjectStreamCodec r = new DerObjectStreamCodec();
        r.initReader(stream);
        assertEquals(42, r.readInt());
        assertEquals("plain", r.readUTF());
    }

    @Test
    void degenerate_singleChainSite_oneFullZeroRefs() throws Exception {
        byte[] stream = encodeStream(new VersionedRecord(1, "solo", "s"));
        assertEquals(FULL, parseP1Item(items(stream).get(0)).schemaTag());
        assertEquals(new VersionedRecord(1, "solo", "s"), decodeStream(stream, 1).get(0));
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private static int indexOf(byte[] haystack, byte[] needle) {
        Objects.requireNonNull(needle);
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
