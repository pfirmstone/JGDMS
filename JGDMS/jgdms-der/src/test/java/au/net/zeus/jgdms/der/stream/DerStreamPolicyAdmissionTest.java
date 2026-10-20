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
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static au.net.zeus.jgdms.der.stream.DerStreamAdmissionCharacterisationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B5: the whole-stream policy ({@link DerStreamPolicy}) as admission applies it.
 *
 * <ul>
 *   <li>every way a class name can reach the loader from the wire, placed as the LAST item of a stream, is refused by a deny
 *       entry and by an allow-list that omits it, with no class loaded, no initialiser run and no constructor run;</li>
 *   <li>each budget's inclusive boundary pair, including the shipped defaults;</li>
 *   <li>rebinding of a class name to a second schema;</li>
 *   <li>the shipped defaults admit the whole golden corpus with wide headroom.</li>
 * </ul>
 */
class DerStreamPolicyAdmissionTest {

    /** A class whose static initialiser records that it was loaded: the policy must reject BEFORE anything touches it. */
    static final AtomicInteger TRIPWIRE = new AtomicInteger();
    public static final class Tripwire {
        static { TRIPWIRE.incrementAndGet(); }
    }
    static final String TRIPWIRE_NAME = Tripwire.class.getName();

    static final Tag CTX_ENUM_TAG = new Tag(Tag.CLASS_CONTEXT, true, 7);
    static final Tag CTX_ARRAY_TAG = new Tag(Tag.CLASS_CONTEXT, true, 9);
    static final Tag CTX_NULL_TAG = new Tag(Tag.CLASS_CONTEXT, false, 0);

    /** Counts every class the resolution context is asked to load. */
    static final class CountingLoader extends ClassLoader {
        final List<String> loads = new ArrayList<>();
        CountingLoader() { super(DerStreamPolicyAdmissionTest.class.getClassLoader()); }
        @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
            loads.add(name);
            return super.loadClass(name);
        }
    }

    @BeforeEach
    void reset() {
        CONSTRUCTED.set(0);
        TRIPWIRE.set(0);
    }

    // ------------------------------------------------------------------ fixtures: records whose names and values we control

    static AtomicSerialSchemaRecord rec(String cls, AtomicSerialFieldDef... fields) {
        return new AtomicSerialSchemaRecord(cls, List.of(fields));
    }

    static AtomicSerialFieldDef field(String name, String wireType) {
        return new AtomicSerialFieldDef(name, wireType);
    }

    /** The concatenated, linked chain for records given leaf-first. */
    static byte[] chainOf(AtomicSerialSchemaRecord... leafFirst) {
        SchemaChain.Result linked = SchemaChain.linkAndGetLeafDigest(List.of(leafFirst));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord r : linked.chain()) out.writeBytes(r.encode());
        return out.toByteArray();
    }

    static byte[] leafDigest(AtomicSerialSchemaRecord... leafFirst) {
        return SchemaChain.linkAndGetLeafDigest(List.of(leafFirst)).leafDigest();
    }

    /** Payload shell for one class: {@code SEQUENCE { SEQUENCE { field TLVs } }}. */
    static byte[] shell(byte[]... fieldTlvs) {
        return DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of(fieldTlvs))));
    }

    static final byte[] NULL_TLV = {0x05, 0x00};

    /** A stream-level {@code [1]} item carrying its chain in full. */
    static byte[] item(byte[] chain, byte[] shell) {
        return DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(DerWriter.writeOctetString(shell),
                DerWriter.writeTlv(CTX_NULL_TAG, chain), DerWriter.writeUtf8String(FORMAT))));
    }

    /** The canonical four-field record a {@code [8]} proxy's handler is. */
    static byte[] canonicalRecord(AtomicSerialSchemaRecord leaf, byte[] shell) {
        return DerWriter.writeSequence(List.of(DerWriter.writeOctetString(shell), DerWriter.writeOctetString(chainOf(leaf)),
                DerWriter.writeOctetString(leafDigest(leaf)), DerWriter.writeUtf8String(FORMAT)));
    }

    /** A canonical nested site {@code SEQUENCE { OCTET STRING chain, OCTET STRING payload }}. */
    static byte[] site(AtomicSerialSchemaRecord leaf, byte[] shell) {
        return DerWriter.writeSequence(List.of(DerWriter.writeOctetString(chainOf(leaf)), DerWriter.writeOctetString(shell)));
    }

    static byte[] enumTlv(String cls) {
        return DerWriter.writeTlv(CTX_ENUM_TAG, cat(DerWriter.writeUtf8String(cls), DerWriter.writeUtf8String("X")));
    }

    static byte[] proxyOf(String iface, byte[] handler) {
        return DerWriter.writeTlv(CTX_PROXY, cat(DerWriter.writeInteger(1), DerWriter.writeUtf8String(iface), handler));
    }

    static byte[] utf8(String s) {
        return DerWriter.writeUtf8String(s);
    }

    /** A stream-level record with ONE field of the given wire type holding {@code value}; the record's own class is {@code Rec}. */
    static byte[] oneField(String wireType, byte[] value) {
        return item(chainOf(rec("fx.Rec", field("f", wireType))), shell(value));
    }

    /** A holder record, one nested-site field, as a stream-level item. */
    static byte[] holding(byte[] nestedValue) {
        return item(chainOf(rec("fx.Holder", field("p", "@AtomicSerial"))), shell(nestedValue));
    }

    static final AtomicSerialSchemaRecord LEAF = rec("fx.Leaf");

    /** Each way a class name {@code c} can reach the loader or the constructors from the wire. */
    static Map<String, Function<String, byte[]>> channels() {
        Map<String, Function<String, byte[]>> m = new LinkedHashMap<>();
        m.put("stream-level chain record", c -> item(chainOf(rec(c)), shell()));
        m.put("ancestor record of a chain", c -> item(chainOf(rec("fx.Child"), rec(c)),
                DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of()), DerWriter.writeSequence(List.of())))));
        m.put("top-level [7] enum", c -> enumTlv(c));
        m.put("[7] enum nested in a record", c -> holding(enumTlv(c)));
        m.put("top-level [8] interface", c -> proxyOf(c, DerWriter.writeTlv(CTX_ATOMIC, canonicalRecord(LEAF, shell()))));
        m.put("[8] interface nested in a record", c -> holding(proxyOf(c, site(LEAF, shell()))));
        m.put("[9] array component", c -> DerWriter.writeTlv(CTX_ARRAY_TAG,
                cat(utf8("array:@AtomicSerial:" + c), DerWriter.writeSequence(List.of()))));
        m.put("[16] collection token", c -> DerWriter.writeTlv(CTX_COLLECTION,
                cat(utf8("list:@AtomicSerial:" + c), DerWriter.writeSequence(List.of()))));
        m.put("schema: enum field type", c -> oneField("enum:" + c, NULL_TLV));
        m.put("schema: nested field type", c -> oneField("@AtomicSerial:" + c, NULL_TLV));
        m.put("schema: array of nested", c -> oneField("array:@AtomicSerial:" + c, NULL_TLV));
        m.put("schema: list of enums", c -> oneField("list:enum:" + c, NULL_TLV));
        m.put("schema: map value type", c -> oneField("map:{java.lang.String}{@AtomicSerial:" + c + "}", NULL_TLV));
        m.put("schema: array-class enum type", c -> oneField("enum:[L" + c + ";", NULL_TLV));
        m.put("schema: array of array-class enums", c -> oneField("array:enum:[[L" + c + ";", NULL_TLV));
        m.put("schema: array-class component", c -> oneField("array:@AtomicSerial:[L" + c + ";", NULL_TLV));
        m.put("schema: array-class map value", c -> oneField("map:{java.lang.String}{@AtomicSerial:[L" + c + ";}", NULL_TLV));
        m.put("[9] array-class component", c -> DerWriter.writeTlv(CTX_ARRAY_TAG,
                cat(utf8("array:@AtomicSerial:[L" + c + ";"), DerWriter.writeSequence(List.of()))));
        m.put("[16] array-class token", c -> DerWriter.writeTlv(CTX_COLLECTION,
                cat(utf8("list:enum:[L" + c + ";"), DerWriter.writeSequence(List.of()))));
        m.put("Class field value", c -> oneField("java.lang.Class", utf8(c)));
        m.put("array-class name in a Class field value", c -> oneField("java.lang.Class", utf8("[[L" + c + ";")));
        m.put("Class array element", c -> oneField("array:java.lang.Class", DerWriter.writeSequence(List.of(utf8(c)))));
        m.put("Class list element", c -> oneField("list:java.lang.Class", DerWriter.writeSequence(List.of(utf8(c)))));
        m.put("Class map key", c -> oneField("map:{java.lang.Class}{int}",   // map: is a canonicalise token: an outer SET
                DerWriter.writeSet(List.of(DerWriter.writeSequence(List.of(utf8(c), DerWriter.writeInteger(1)))))));
        m.put("chain record inside a [16] element", c -> DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(site(rec(c), shell()))))));
        m.put("chain record inside a [8] handler", c -> proxyOf("java.lang.Runnable",
                DerWriter.writeTlv(CTX_ATOMIC, canonicalRecord(rec(c), shell()))));
        m.put("chain record inside a [8] nested in a record", c -> holding(proxyOf("java.lang.Runnable", site(rec(c), shell()))));
        m.put("[7] enum inside a [16] element", c -> DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(enumTlv(c))))));
        return m;
    }

    private static final String EVIL = "com.evil.Gadget";

    private static DerMarshalInputStream open(byte[] stream, CountingLoader loader, DerStreamPolicy policy) throws IOException {
        return new DerMarshalInputStream(new ByteArrayInputStream(stream), new ResolutionContext(loader, false, loader),
                DerInputLimits.DEFAULT, policy);
    }

    /** A few good objects, then the item under test as the LAST item. */
    private static byte[] lastItem(byte[] item) throws IOException {
        return cat(goodStream(1, 2, 3), item);
    }

    private static String msg(Throwable t) {
        return DerStreamAdmissionCharacterisationTest.chain(t);
    }

    // ------------------------------------------------------------------ class-name policy: every channel

    @TestFactory
    Stream<DynamicTest> everyRouteToTheLoaderIsNoticedAndNothingIsLoadedOrBuilt() {
        List<DynamicTest> tests = new ArrayList<>();
        channels().forEach((name, build) -> tests.add(DynamicTest.dynamicTest(name, () -> {
            byte[] stream = lastItem(build.apply(EVIL));
            CountingLoader loader = new CountingLoader();
            DerMarshalInputStream in = open(stream, loader, DerStreamPolicy.BUILT_IN);
            assertTrue(in.admissionTally().classNames().contains(EVIL), name + ": " + in.admissionTally().classNames());
            in.close();
            assertTrue(loader.loads.isEmpty(), "admission loads no class: " + loader.loads);
            assertEquals(0, CONSTRUCTED.get(), "no constructor may run");
            // the class cap counts it: a cap that leaves room for the good prefix's class only refuses the stream
            int known = in.admissionTally().distinctClasses();
            assertThrows(IOException.class, () -> open(stream, new CountingLoader(),
                    DerStreamPolicy.BUILT_IN.withMaxDistinctClasses(known - 1)), name);
        })));
        return tests.stream();
    }

    @Test
    void aTripwireClassNamedInTheLastItemIsNeverLoadedOrInitialisedByAdmission() throws Exception {
        for (Map.Entry<String, Function<String, byte[]>> e : channels().entrySet()) {
            TRIPWIRE.set(0);
            byte[] stream = lastItem(e.getValue().apply(TRIPWIRE_NAME));
            CountingLoader loader = new CountingLoader();
            open(stream, loader, DerStreamPolicy.BUILT_IN).close();
            assertEquals(0, TRIPWIRE.get(), e.getKey() + ": the class initialiser ran");
            assertTrue(loader.loads.isEmpty(), e.getKey());
        }
        // control: the tripwire does trip when the delivery path is asked for the class (a record naming it)
        byte[] stream = lastItem(channels().get("stream-level chain record").apply(TRIPWIRE_NAME));
        CountingLoader loader = new CountingLoader();
        DerMarshalInputStream in = open(stream, loader, DerStreamPolicy.BUILT_IN);
        for (int i = 0; i < 3; i++) in.readObject();
        assertThrows(Exception.class, in::readObject);
        assertTrue(loader.loads.contains(TRIPWIRE_NAME), "the fixture really does make delivery load the class: " + loader.loads);
    }

    // ------------------------------------------------------------------ budgets: each limit's inclusive boundary pair

    /** {@code n} top-level records (n objects, one distinct class). */
    private static byte[] records(int n) throws IOException {
        int[] ns = new int[n];
        for (int i = 0; i < n; i++) ns[i] = i + 1;
        return goodStream(ns);
    }

    @Test
    void theObjectBudgetIsInclusive() throws Exception {
        byte[] stream = records(7);
        open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxObjects(7)).close();
        IOException e = assertThrows(IOException.class,
                () -> open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxObjects(6)));
        assertTrue(msg(e).contains("maxObjects (6)"), msg(e));
        assertEquals(0, CONSTRUCTED.get());
    }

    @Test
    void theObjectBudgetCountsEverythingThatIsBuilt() throws Exception {
        // one of each: a record, an enum, a proxy (and its handler record), an array, a collection => 6 objects
        byte[] items = cat(
                item(chainOf(rec("fx.One")), shell()),
                enumTlv("fx.E"),
                proxyOf("fx.I", DerWriter.writeTlv(CTX_ATOMIC, canonicalRecord(LEAF, shell()))),
                DerWriter.writeTlv(CTX_ARRAY_TAG, cat(utf8("array:int"), DerWriter.writeSequence(List.of()))),
                DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:int"), DerWriter.writeSequence(List.of()))));
        byte[] stream = cat(new byte[] {(byte) 0x8F, 1, 1}, items);
        DerMarshalInputStream in = open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxObjects(6));
        assertEquals(6, in.admissionTally().objects());
        in.close();
        assertThrows(IOException.class, () -> open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxObjects(5)));
    }

    @Test
    void theElementBudgetIsInclusiveAndCountsCollectionsMapsAndArrays() throws Exception {
        // a set of 5, a map of 3 entries, a primitive array of 4, a nested-record array of 2 (records are objects too)
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeCollection(new java.util.TreeSet<>(List.of("a", "b", "c", "d", "e")), "set:java.lang.String");
        java.util.TreeMap<String, Integer> map = new java.util.TreeMap<>(Map.of("a", 1, "b", 2, "c", 3));
        c.writeCollection(map, "map:{java.lang.String}{int}");
        c.writeObject(new String[] {"w", "x", "y", "z"});
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        byte[] stream = bos.toByteArray();
        DerMarshalInputStream in = open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxElements(12));
        assertEquals(12, in.admissionTally().elements());
        in.close();
        IOException e = assertThrows(IOException.class,
                () -> open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxElements(11)));
        assertTrue(msg(e).contains("maxElements (11)"), msg(e));
    }

    @Test
    void theElementBudgetCountsElementsInsideRecordFieldsAndNestedCollections() throws Exception {
        // a list of 3 lists of 2 ints: 3 + 3*2 = 9 elements, as a record FIELD value (so the shell walk meets it)
        byte[] inner = DerWriter.writeSequence(List.of(DerWriter.writeInteger(1), DerWriter.writeInteger(2)));
        byte[] value = DerWriter.writeSequence(List.of(inner, inner, inner));
        byte[] stream = streamOf(oneField("list:list:int", value));
        DerMarshalInputStream in = open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxElements(9));
        assertEquals(9, in.admissionTally().elements());
        in.close();
        assertThrows(IOException.class, () -> open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxElements(8)));
    }

    /** A nested site in STREAM form, first occurrence: {@code SEQUENCE { [0] fullChain, OCTET STRING payload }}. */
    static byte[] streamSiteFull(AtomicSerialSchemaRecord leaf, byte[] shell) {
        return DerWriter.writeSequence(List.of(DerWriter.writeTlv(CTX_NULL_TAG, chainOf(leaf)), DerWriter.writeOctetString(shell)));
    }

    /** A nested site in STREAM form, repeat occurrence: {@code SEQUENCE { [1] chainRef, OCTET STRING payload }}. */
    static byte[] streamSiteRef(AtomicSerialSchemaRecord leaf, byte[] shell) {
        return DerWriter.writeSequence(List.of(DerWriter.writeTlv(REF, leafDigest(leaf)), DerWriter.writeOctetString(shell)));
    }

    private static StreamPolicyMeter tally(byte[] stream) throws IOException {
        DerMarshalInputStream in = open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN);
        StreamPolicyMeter t = in.admissionTally();
        in.close();
        return t;
    }

    @Test
    void theTalliesCoverEveryShapeThatIsBuilt() throws Exception {
        AtomicSerialSchemaRecord leaf = rec("fx.Leaf");
        byte[] emptyShell = shell();
        // a record holding a proxy whose handler is a nested site: the record, the proxy and the handler site are 3 objects
        StreamPolicyMeter proxied = tally(streamOf(holding(proxyOf("fx.I", site(leaf, emptyShell)))));
        assertEquals(3, proxied.objects());
        assertEquals(0, proxied.elements());
        // a record whose field is an array of two nested records: the record, the array and two sites are 4 objects, 2 elements
        byte[] arr = DerWriter.writeSequence(List.of(streamSiteFull(leaf, emptyShell), streamSiteRef(leaf, emptyShell)));
        StreamPolicyMeter nested = tally(streamOf(oneField("array:@AtomicSerial:fx.Leaf", arr)));
        assertEquals(4, nested.objects());
        assertEquals(2, nested.elements());
        // a top-level [9] array of two nested records: the array and two sites are 3 objects, 2 elements
        byte[] top = DerWriter.writeTlv(CTX_ARRAY_TAG, cat(utf8("array:@AtomicSerial:fx.Leaf"), arr));
        StreamPolicyMeter topLevel = tally(streamOf(top));
        assertEquals(3, topLevel.objects());
        assertEquals(2, topLevel.elements());
        // a primitive array field: the record and the array are 2 objects; its elements are bounded by the input size and
        // are NOT part of the element budget
        StreamPolicyMeter prim = tally(streamOf(oneField("array:int",
                DerWriter.writeSequence(List.of(DerWriter.writeInteger(1), DerWriter.writeInteger(2), DerWriter.writeInteger(3))))));
        assertEquals(2, prim.objects());
        assertEquals(0, prim.elements());
        // the same as a top-level [9] item
        StreamPolicyMeter primTop = tally(streamOf(DerWriter.writeTlv(CTX_ARRAY_TAG, cat(utf8("array:int"),
                DerWriter.writeSequence(List.of(DerWriter.writeInteger(1), DerWriter.writeInteger(2)))))));
        assertEquals(1, primTop.objects());
        assertEquals(0, primTop.elements());
        StreamPolicyMeter refTop = tally(streamOf(DerWriter.writeTlv(CTX_ARRAY_TAG, cat(utf8("array:java.lang.String"),
                DerWriter.writeSequence(List.of(utf8("a"), utf8("b")))))));
        assertEquals(2, refTop.elements());
        // a reference-component array field (Strings) counts its elements
        StreamPolicyMeter refs = tally(streamOf(oneField("array:java.lang.String",
                DerWriter.writeSequence(List.of(utf8("a"), utf8("b"), utf8("c"))))));
        assertEquals(2, refs.objects());
        assertEquals(3, refs.elements());
        // a null array field is no array
        assertEquals(1, tally(streamOf(oneField("array:int", NULL_TLV))).objects());
        // an Any field holding a canonical collection of two ints: the record and the collection are 2 objects, 2 elements
        byte[] anyColl = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 30), DerWriter.writeTlv(Tag.SET, cat(
                DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 3), new byte[] {5}),
                DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 3), new byte[] {6}))));
        StreamPolicyMeter any = tally(streamOf(oneField("any", anyColl)));
        assertEquals(2, any.objects());
        assertEquals(2, any.elements());
    }

    @Test
    void aBigPrimitiveArrayIsNotHeldToTheElementBudgetButABigCollectionIs() throws Exception {
        // delivery puts no cap on a primitive array (it is bounded by the input size) and a cap of 65 536 on a collection
        List<byte[]> ints = new ArrayList<>();
        for (int i = 0; i < 1000; i++) ints.add(DerWriter.writeInteger(i));
        byte[] bigArray = streamOf(oneField("array:int", DerWriter.writeSequence(ints)));
        DerMarshalInputStream in = open(bigArray, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxElements(10));
        assertEquals(0, in.admissionTally().elements());
        in.close();
        byte[] bigList = streamOf(oneField("list:int", DerWriter.writeSequence(ints)));
        assertThrows(IOException.class, () -> open(bigList, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxElements(10)));
    }

    @Test
    void aChainAlreadyVerifiedInAnEarlierRegionStillCountsInTheNextRegionsScope() throws Exception {
        // region 1 verifies chain C (class Twin v1); region 2 reuses C (a byte-identical cache hit) and then binds Twin to v2.
        // The hit must still bind Twin in region 2's scope, or the second schema goes unnoticed.
        AtomicSerialSchemaRecord v1 = rec("fx.Twin");
        AtomicSerialSchemaRecord v2 = rec("fx.Twin", field("x", "int"));
        byte[] region1 = DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(site(v1, shell())))));
        byte[] region2 = DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(site(v1, shell()), site(v2, shell(DerWriter.writeInteger(1)))))));
        IOException e = assertThrows(IOException.class, () -> open(streamOf(region1, region2), new CountingLoader(), DerStreamPolicy.BUILT_IN));
        assertTrue(msg(e).contains("bound to two different schemas"), msg(e));
    }

    @Test
    void theStreamLevelChainCeilingsDoNotApplyAcrossRegions() throws Exception {
        // 3 regions x 100 distinct chains: delivery of a region has no table ceiling, so 300 chains across regions are fine
        byte[][] regions = new byte[3][];
        for (int r = 0; r < 3; r++) {
            List<byte[]> sites = new ArrayList<>();
            for (int i = 0; i < 100; i++) sites.add(site(rec("fx.R" + r + "x" + i), shell()));
            regions[r] = DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"), DerWriter.writeSequence(sites)));
        }
        open(streamOf(regions), new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxDistinctClasses(400)).close();
    }

    @Test
    void aRepeatedChainIsCheckedOnceNotOncePerSite() throws Exception {
        // 5 000 sites of one 40-field chain: the names and wire types are scanned for the chain, not for each site
        AtomicSerialFieldDef[] fields = new AtomicSerialFieldDef[40];
        for (int i = 0; i < fields.length; i++) fields[i] = field("f" + i, "enum:fx.E" + i);
        AtomicSerialSchemaRecord big = rec("fx.Big", fields);
        byte[][] values = new byte[fields.length][];
        java.util.Arrays.fill(values, NULL_TLV);
        List<byte[]> sites = new ArrayList<>();
        for (int i = 0; i < 5000; i++) sites.add(site(big, shell(values)));
        byte[] region = DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"), DerWriter.writeSequence(sites)));
        DerMarshalInputStream in = open(streamOf(region), new CountingLoader(), DerStreamPolicy.BUILT_IN);
        assertEquals(5001, in.admissionTally().objects(), "5 000 sites and the collection");
        assertEquals(41, in.admissionTally().distinctClasses(), "Big and its 40 enums");
        in.close();
    }

    @Test
    void theDistinctClassCapIsInclusive() throws Exception {
        byte[][] items = new byte[5][];
        for (int i = 0; i < 5; i++) items[i] = item(chainOf(rec("fx.C" + i)), shell());
        byte[] stream = streamOf(items);
        open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxDistinctClasses(5)).close();
        IOException e = assertThrows(IOException.class,
                () -> open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxDistinctClasses(4)));
        assertTrue(msg(e).contains("maxDistinctClasses (4)"), msg(e));
        // the same class repeated is one class
        byte[] repeated = records(50);
        open(repeated, new CountingLoader(), DerStreamPolicy.BUILT_IN.withMaxDistinctClasses(1)).close();
    }

    @Test
    void theDefaultBudgetsHoldTheirDocumentedBoundary() throws Exception {
        // 262 144 tiny records are admitted, one more is not. The stream is larger than the default 16 MiB cap, so the cap is raised
        // for this test; the shipped defaults are the policy's.
        DerInputLimits limits = DerInputLimits.maxBytes(64 * 1024 * 1024);
        int max = DerStreamPolicy.DEFAULT_MAX_OBJECTS;
        byte[] first = goodStream(1);
        byte[] ref = itemsOfRefs(goodStream(1, 2));
        for (int extra : new int[] {0, 1}) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.writeBytes(first);
            for (int i = 1; i < max + extra; i++) bos.writeBytes(ref);
            byte[] stream = bos.toByteArray();
            CONSTRUCTED.set(0);
            if (extra == 0) {
                DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(stream), ResolutionContext.NONE,
                        limits, DerStreamPolicy.BUILT_IN);
                assertEquals(max, in.admissionTally().objects());
                in.close();
            } else {
                IOException e = assertThrows(IOException.class, () -> new DerMarshalInputStream(new ByteArrayInputStream(stream),
                        ResolutionContext.NONE, limits, DerStreamPolicy.BUILT_IN));
                assertTrue(msg(e).contains("maxObjects (" + max + ")"), msg(e));
            }
            assertEquals(0, CONSTRUCTED.get(), "a construction bomb is refused before the first constructor");
        }
    }

    /** The second item of {@code stream} (a chainRef record), as one item's bytes. */
    private static byte[] itemsOfRefs(byte[] twoItemStream) throws Exception {
        byte[] one = goodStream(1);
        return java.util.Arrays.copyOfRange(twoItemStream, one.length, twoItemStream.length);
    }

    // ------------------------------------------------------------------ cross-item consistency

    @Test
    void oneClassNameBoundToTwoSchemasIsARebinding() throws Exception {
        byte[] a = item(chainOf(rec("fx.Twin")), shell());
        byte[] b = item(chainOf(rec("fx.Twin", field("x", "int"))), shell(DerWriter.writeInteger(1)));
        byte[] stream = streamOf(a, b);
        IOException e = assertThrows(IOException.class,
                () -> open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN));
        assertTrue(msg(e).contains("bound to two different schemas"), msg(e));
        assertEquals(0, CONSTRUCTED.get());
        // an explicit switch accepts it (the lazy path always did)
        open(stream, new CountingLoader(), DerStreamPolicy.BUILT_IN.withRejectRebinding(false)).close();
        // the same schema twice is not a rebinding (it is a chainRef in a real stream)
        open(records(5), new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
    }

    @Test
    void anAncestorRecordIsHeldToTheSameRule() throws Exception {
        // class P is the parent of Child in one chain and a root with a DIFFERENT schema in another
        byte[] pA = item(chainOf(rec("fx.Child"), rec("fx.P")),
                DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of()), DerWriter.writeSequence(List.of()))));
        byte[] pB = item(chainOf(rec("fx.P", field("x", "int"))), shell(DerWriter.writeInteger(1)));
        assertThrows(IOException.class, () -> open(streamOf(pA, pB), new CountingLoader(), DerStreamPolicy.BUILT_IN));
        open(streamOf(pA, pB), new CountingLoader(), DerStreamPolicy.BUILT_IN.withRejectRebinding(false)).close();
        // and consistent use of P (as an ancestor, then as the root it is) is fine
        byte[] pC = item(chainOf(rec("fx.P")), shell());
        open(streamOf(pA, pC), new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
    }

    @Test
    void aForwardedRegionKeepsItsOriginsVersionOfAClass() throws Exception {
        // A narrowed proxy is re-forwarded with its sender's own bytes, so a region may carry another version of a class that the
        // stream around it carries locally. That is not a rebinding: each region is its own scope.
        AtomicSerialSchemaRecord v1 = rec("fx.Twin");
        AtomicSerialSchemaRecord v2 = rec("fx.Twin", field("x", "int"));
        byte[] v2Shell = shell(DerWriter.writeInteger(1));
        byte[] main = item(chainOf(v1), shell());
        byte[] collection = DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(site(v2, v2Shell)))));
        open(streamOf(main, collection), new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
        // two forwarded proxies, each from its own origin
        byte[] proxyV1 = proxyOf("fx.I", DerWriter.writeTlv(CTX_ATOMIC, canonicalRecord(v1, shell())));
        byte[] proxyV2 = proxyOf("fx.I", DerWriter.writeTlv(CTX_ATOMIC, canonicalRecord(v2, v2Shell)));
        open(streamOf(proxyV1, proxyV2), new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
        // a proxy nested in a record, and a proxy nested in that proxy's handler record, are scopes of their own too
        open(streamOf(item(chainOf(v1), shell()), holding(proxyOf("fx.I", site(v2, v2Shell)))),
                new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
        // and leaving a region puts the enclosing scope back: a stream-level record AFTER the region is judged against the stream
        byte[] regionFirst = proxyOf("fx.I", DerWriter.writeTlv(CTX_ATOMIC, canonicalRecord(v2, v2Shell)));
        open(streamOf(regionFirst, item(chainOf(v1), shell())), new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
        open(streamOf(holding(proxyOf("fx.I", site(v2, v2Shell))), item(chainOf(v1), shell())),
                new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
        // two [16] regions, each from its own origin
        byte[] regionA = DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"), DerWriter.writeSequence(List.of(site(v1, shell())))));
        byte[] regionB = DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"), DerWriter.writeSequence(List.of(site(v2, v2Shell)))));
        open(streamOf(regionA, regionB), new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
        // inside ONE region record: a proxy (its own scope) between two sites of the region's scope. The region's binding survives
        // the proxy and the proxy's does not leak out.
        AtomicSerialSchemaRecord outer = rec("fx.Outer", field("a", "@AtomicSerial"),
                field("p", "@AtomicSerial"), field("b", "@AtomicSerial"));
        byte[] outerShell = shell(site(v1, shell()), proxyOf("fx.I", site(v2, v2Shell)), site(v1, shell()));
        open(streamOf(DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(site(outer, outerShell)))))), new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
        // ... and a site AFTER the proxy that agrees with the region's version, while the proxy held the other one
        byte[] after = shell(proxyOf("fx.I", site(v2, v2Shell)), site(v1, shell()));
        AtomicSerialSchemaRecord outer2 = rec("fx.Outer2", field("p", "@AtomicSerial"),
                field("b", "@AtomicSerial"));
        open(streamOf(DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(site(outer2, after)))))), new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
        // a site BEFORE the proxy in the region with the proxy's class at the OTHER version: the region binds v1, the proxy v2
        AtomicSerialSchemaRecord outer3 = rec("fx.Outer3", field("a", "@AtomicSerial"),
                field("p", "@AtomicSerial"));
        byte[] before = shell(site(v1, shell()), proxyOf("fx.I", site(v2, v2Shell)));
        open(streamOf(DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(site(outer3, before)))))), new CountingLoader(), DerStreamPolicy.BUILT_IN).close();
    }

    @Test
    void oneRegionBindingAClassToTwoSchemasIsARebinding() throws Exception {
        AtomicSerialSchemaRecord v1 = rec("fx.Twin");
        AtomicSerialSchemaRecord v2 = rec("fx.Twin", field("x", "int"));
        byte[] twoInOneRegion = DerWriter.writeTlv(CTX_COLLECTION, cat(utf8("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(site(v1, shell()), site(v2, shell(DerWriter.writeInteger(1)))))));
        IOException e = assertThrows(IOException.class,
                () -> open(streamOf(twoInOneRegion), new CountingLoader(), DerStreamPolicy.BUILT_IN));
        assertTrue(msg(e).contains("bound to two different schemas"), msg(e));
        open(streamOf(twoInOneRegion), new CountingLoader(), DerStreamPolicy.BUILT_IN.withRejectRebinding(false)).close();
    }

    @Test
    void aClassListedTwiceInOneChainIsARebinding() throws Exception {
        // the same class name at two levels of a hierarchy: one of the two schemas is not the class's own
        byte[] dup = item(chainOf(rec("fx.Dup", field("a", "int")), rec("fx.Dup")),
                DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of()),
                        DerWriter.writeSequence(List.of(DerWriter.writeInteger(1))))));
        IOException e = assertThrows(IOException.class, () -> open(streamOf(dup), new CountingLoader(), DerStreamPolicy.BUILT_IN));
        assertTrue(msg(e).contains("bound to two different schemas"), msg(e));
    }

    // ------------------------------------------------------------------ the shipped defaults and the golden corpus

    @Test
    void theShippedDefaultsAdmitTheWholeGoldenCorpusWithWideHeadroom() throws Exception {
        long maxObjects = 0, maxElements = 0;
        int maxClasses = 0;
        for (Map.Entry<String, Supplier<Object>> e : DerGoldenBytesTest.corpus().entrySet()) {
            if (DerGoldenBytesTest.SINGLE_ONLY.contains(e.getKey())) continue;
            byte[] stream = encodeCorpus(e.getValue());
            if (stream == null) continue;
            DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(stream), ResolutionContext.NONE,
                    DerInputLimits.DEFAULT, DerStreamPolicy.BUILT_IN);
            StreamPolicyMeter t = in.admissionTally();
            maxObjects = Math.max(maxObjects, t.objects());
            maxElements = Math.max(maxElements, t.elements());
            maxClasses = Math.max(maxClasses, t.distinctClasses());
            in.close();
        }
        // the corpus includes the benchmark's 100-record batch; the defaults must stay well clear of every maximum
        assertTrue(maxObjects >= 100 && maxObjects * 100 <= DerStreamPolicy.DEFAULT_MAX_OBJECTS,
                "objects " + maxObjects + " vs " + DerStreamPolicy.DEFAULT_MAX_OBJECTS);
        assertTrue(maxElements >= 100 && maxElements * 100 <= DerStreamPolicy.DEFAULT_MAX_ELEMENTS,
                "elements " + maxElements + " vs " + DerStreamPolicy.DEFAULT_MAX_ELEMENTS);
        assertTrue(maxClasses >= 1 && maxClasses * 100 <= DerStreamPolicy.DEFAULT_MAX_DISTINCT_CLASSES,
                "classes " + maxClasses + " vs " + DerStreamPolicy.DEFAULT_MAX_DISTINCT_CLASSES);
    }

    @Test
    void thePolicyIsPartOfFullAdmissionNotTheCheapStage() throws Exception {
        // a dispatcher stream constructs fine with a stream the policy refuses; admitStream() is what applies it, so an
        // unauthorized peer never pays for (or is told by) the policy walk
        byte[] stream = records(5);
        DerMarshalInputStream in = DerMarshalInputStream.forDispatcher(new ByteArrayInputStream(stream), ResolutionContext.NONE,
                DerInputLimits.DEFAULT, DerStreamPolicy.BUILT_IN.withMaxObjects(2));
        IOException e = assertThrows(IOException.class, in::admitStream);
        assertTrue(msg(e).contains("maxObjects (2)"), msg(e));
        assertThrows(IOException.class, in::readLong, "and the stream is dead");
    }

    // ------------------------------------------------------------------ helpers copied from the whole-stream test

    private static byte[] encodeCorpus(Supplier<Object> s) {
        try {
            DerObjectStreamCodec c = new DerObjectStreamCodec();
            Object o = s.get();
            if (o instanceof DerGoldenBytesTest.MixedStream m) {
                for (Object item : m.items) writeItem(c, item);
            } else {
                writeItem(c, o);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            c.drainTo(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeItem(DerObjectStreamCodec c, Object o) throws IOException {
        if (o instanceof DerGoldenBytesTest.TopLevelCollection t) c.writeCollection(t.coll, t.token); else c.writeObject(o);
    }

    private static byte[] streamOf(byte[]... items) {
        return cat(new byte[] {(byte) 0x8F, 1, 1}, cat(items));
    }
}
