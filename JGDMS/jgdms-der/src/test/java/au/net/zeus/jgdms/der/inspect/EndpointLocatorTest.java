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


package au.net.zeus.jgdms.der.inspect;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.Enclosing;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.Form;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.Kind;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.Location;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.MarshalledInstanceHash;
import au.net.zeus.jgdms.der.inspect.LocatorFixtures.FxCarrier;
import au.net.zeus.jgdms.der.inspect.LocatorFixtures.FxEndpoint;
import au.net.zeus.jgdms.der.inspect.LocatorFixtures.FxEndpointType;
import au.net.zeus.jgdms.der.inspect.LocatorFixtures.FxHandler;
import au.net.zeus.jgdms.der.inspect.LocatorFixtures.FxHolder;
import au.net.zeus.jgdms.der.inspect.LocatorFixtures.FxObjectEndpoint;
import au.net.zeus.jgdms.der.inspect.LocatorFixtures.FxService;
import au.net.zeus.jgdms.der.inspect.LocatorFixtures.FxSocketFactoryImpl;
import au.net.zeus.jgdms.der.inspect.LocatorFixtures.FxUnrecognised;
import au.net.zeus.jgdms.der.marshal.DerMarshalledInstance;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import net.jini.io.MarshalledInstance;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EndpointLocator} against fixture classes shaped like a JERI proxy.
 *
 * <p><b>Invariants defended.</b> A trusted process must find every endpoint inside a marshalled
 * proxy without loading any class the data names (JGDMS-STD-006 S3.11, data independence), and
 * must refuse what it cannot fully account for: an unrecognised value in a slot that must hold
 * an endpoint, a payload it cannot read (JOSS), a socket factory that is not local code, nesting
 * beyond the codec's limits, malformed bytes. The location it reports must be exact enough to
 * replace the endpoint and repair every enclosing length and {@code MarshalledInstance} hash.
 */
class EndpointLocatorTest {

    private static final String FX = LocatorFixtures.class.getName() + "$";
    private static final String EP = FX + "FxEndpoint";
    private static final String OE = FX + "FxObjectEndpoint";
    private static final String SF = FX + "FxSocketFactoryImpl";

    private static final EndpointLocatorConfig CONFIG = EndpointLocatorConfig.builder()
            .endpoint(new EndpointLocatorConfig.EndpointClass(EP, "host", "port", "sf"))
            .requiredEndpointSlot(OE, "ep")
            .build();

    private static final EndpointLocator LOCATOR = new EndpointLocator(CONFIG);

    // -------------------------------------------------------------------------
    // Building inputs
    // -------------------------------------------------------------------------

    private static MarshalledInstanceRecord record(Object o) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(o.getClass());
        return MarshalledInstanceRecord.fromChain(chain, ObjectCodec.encodeHierarchy(o, chain));
    }

    /** A nested-record TLV for {@code o}, as the codec writes a nested field value. */
    private static byte[] nestedRecord(Object o) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(o.getClass());
        return DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(SchemaGenerator.generateChainBytes(o.getClass())),
                DerWriter.writeOctetString(ObjectCodec.encodeHierarchy(o, chain))));
    }

    private static FxService proxy(FxHandler h) {
        return (FxService) Proxy.newProxyInstance(EndpointLocatorTest.class.getClassLoader(),
                new Class<?>[] {FxService.class}, h);
    }

    private static FxHandler handler(FxEndpointType ep) {
        return new FxHandler(new FxObjectEndpoint(ep, "id"));
    }

    private static FxEndpoint ep(String host, int port) {
        return new FxEndpoint(host, port, null);
    }

    private static List<String> paths(List<EndpointFinding> fs) {
        List<String> l = new ArrayList<>();
        for (EndpointFinding f : fs) {
            l.add(f.path());
        }
        return l;
    }

    private static List<Kind> kinds(Location loc) {
        List<Kind> l = new ArrayList<>();
        for (Enclosing e : loc.enclosing()) {
            l.add(e.kind());
        }
        return l;
    }

    /** The serialized fields of a MarshalledInstance, read class-free from its own encoding. */
    private static Map<String, Object> miFields(MarshalledInstance mi) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(mi.getClass());
        byte[] payload = ObjectCodec.encodeHierarchy(mi, chain);
        return ObjectCodec.decodeToFieldMap(chain, payload).get(MarshalledInstance.class.getName());
    }

    // -------------------------------------------------------------------------
    // Finding endpoints
    // -------------------------------------------------------------------------

    @Test
    void endpointBehindProxyHandlerAndObjectEndpoint() throws Exception {
        FxHolder h = new FxHolder("t");
        h.service = proxy(handler(ep("h1", 1)));

        List<EndpointFinding> fs = LOCATOR.locate(record(h));

        assertEquals(1, fs.size());
        EndpointFinding f = fs.get(0);
        assertEquals("$.service.<handler>.oe.ep", f.path());
        assertEquals(EP, f.className());
        assertEquals(List.of(EP), f.classChain());
        assertEquals("h1", f.host());
        assertEquals(Integer.valueOf(1), f.port());
        assertNull(f.socketFactory());
        assertEquals("h1", f.fields().get("host"));
        assertEquals(1, f.fields().get("port"));
        assertEquals(Form.NESTED_RECORD, f.location().form());
        assertEquals(List.of(Kind.HIERARCHY, Kind.CLASS_FIELDS, Kind.PROXY,
                Kind.NESTED_RECORD, Kind.RECORD_PAYLOAD, Kind.HIERARCHY, Kind.CLASS_FIELDS,
                Kind.NESTED_RECORD, Kind.RECORD_PAYLOAD, Kind.HIERARCHY, Kind.CLASS_FIELDS),
                kinds(f.location()));
    }

    @Test
    void twoEndpointsInDocumentOrder() throws Exception {
        FxHolder h = new FxHolder("t");
        h.service = proxy(handler(ep("h1", 1)));
        h.direct = ep("h2", 2);

        List<EndpointFinding> fs = LOCATOR.locate(record(h));

        assertEquals(List.of("$.service.<handler>.oe.ep", "$.direct"), paths(fs));
        assertEquals("h1", fs.get(0).host());
        assertEquals("h2", fs.get(1).host());
        assertEquals(Integer.valueOf(2), fs.get(1).port());
    }

    @Test
    void endpointsInArrayListMapSetAndAny() throws Exception {
        FxHolder h = new FxHolder("t");
        h.array = new FxEndpointType[] {ep("a", 1)};
        h.list = List.of(ep("b", 2));
        Map<String, FxEndpointType> m = new LinkedHashMap<>();
        m.put("k", ep("c", 3));
        h.map = m;
        h.set = Set.of(ep("d", 4));
        h.any = List.of("not an endpoint", ep("e", 5));

        List<EndpointFinding> fs = LOCATOR.locate(record(h));

        assertEquals(List.of("$.array[0]", "$.list[0]", "$.map[value 0]", "$.set[0]", "$.any[1]"),
                paths(fs));
        assertEquals(List.of("a", "b", "c", "d", "e"),
                List.of(fs.get(0).host(), fs.get(1).host(), fs.get(2).host(), fs.get(3).host(),
                        fs.get(4).host()));
        List<Enclosing> mapEnc = fs.get(2).location().enclosing();
        Enclosing mapColl = mapEnc.get(mapEnc.size() - 2);
        assertEquals(Kind.COLLECTION, mapColl.kind());
        assertFalse(mapColl.reorderOnChange(), "a map value does not decide the entry order");
        List<Enclosing> setEnc = fs.get(3).location().enclosing();
        Enclosing setColl = setEnc.get(setEnc.size() - 1);
        assertEquals(Kind.COLLECTION, setColl.kind());
        assertTrue(setColl.reorderOnChange(), "a canonical set element decides the element order");
        assertTrue(kinds(fs.get(4).location()).contains(Kind.ANY));
        assertFalse(fs.get(1).location().enclosing().get(fs.get(1).location().enclosing().size() - 1)
                .reorderOnChange());
    }

    @Test
    void endpointInsideNestedDerMarshalledInstance() throws Exception {
        FxHolder h = new FxHolder("t");
        h.mi = new DerMarshalledInstance(ep("m", 9));

        List<EndpointFinding> fs = LOCATOR.locate(record(h));

        assertEquals(1, fs.size());
        EndpointFinding f = fs.get(0);
        assertEquals("$.mi.<payload>", f.path());
        assertEquals("m", f.host());
        assertEquals(Form.PAYLOAD, f.location().form());
        Enclosing last = f.location().enclosing().get(f.location().enclosing().size() - 1);
        assertEquals(Kind.MARSHALLED_INSTANCE_PAYLOAD, last.kind());
        assertEquals(last.contentOffset(), f.location().offset());
        assertEquals(last.contentLength(), f.location().length());
        assertNotNull(last.marshalledInstanceHash());
    }

    @Test
    void endpointsInsideCarrierAndItsBareProxyServiceProxy() throws Exception {
        MarshalledInstance smart = new DerMarshalledInstance(proxy(handler(ep("smart", 7))),
                Collections.emptyList(), false);
        FxHolder h = new FxHolder("t");
        h.mi = new DerMarshalledInstance(new FxCarrier(handler(ep("boot", 6)), smart));

        List<EndpointFinding> fs = LOCATOR.locate(record(h));

        assertEquals(List.of("$.mi.<payload>.handler.oe.ep",
                "$.mi.<payload>.serviceProxy.<payload>.<handler>.oe.ep"), paths(fs));
        assertEquals("boot", fs.get(0).host());
        assertEquals("smart", fs.get(1).host());
        List<Kind> k = kinds(fs.get(1).location());
        assertEquals(2, Collections.frequency(k, Kind.MARSHALLED_INSTANCE_PAYLOAD));
        assertTrue(k.contains(Kind.STREAM_ATOMIC));
        assertTrue(k.contains(Kind.PROXY));
    }

    @Test
    void marshalledInstanceFieldsOfABareProxyBothSubstituteModes() throws Exception {
        for (boolean substitute : new boolean[] {true, false}) {
            MarshalledInstance mi = new DerMarshalledInstance(proxy(handler(ep("p", 3))),
                    Collections.emptyList(), substitute);
            Map<String, Object> fields = miFields(mi);
            byte[] schema = (byte[]) fields.get("schemaBytes");
            assertEquals(0, schema.length, "a bare proxy has no separable schema");

            List<EndpointFinding> fs = LOCATOR.locateMarshalledPayload(
                    (byte[]) fields.get("payloadBytes"), schema, (String) fields.get("payloadFormat"));

            assertEquals(List.of("$.<handler>.oe.ep"), paths(fs));
            assertEquals("p", fs.get(0).host());
        }
    }

    @Test
    void marshalledInstanceFieldsOfAnEndpoint() throws Exception {
        Map<String, Object> fields = miFields(new DerMarshalledInstance(ep("x", 4)));
        byte[] payload = (byte[]) fields.get("payloadBytes");

        List<EndpointFinding> fs = LOCATOR.locateMarshalledPayload(payload,
                (byte[]) fields.get("schemaBytes"), (String) fields.get("payloadFormat"));

        assertEquals(1, fs.size());
        assertEquals("$", fs.get(0).path());
        assertEquals(Form.PAYLOAD, fs.get(0).location().form());
        assertEquals(0, fs.get(0).location().offset());
        assertEquals(payload.length, fs.get(0).location().length());
        assertTrue(fs.get(0).location().enclosing().isEmpty());
    }

    @Test
    void endpointInAnUnrequiredSlotOfAnyClassIsReported() throws Exception {
        FxHolder h = new FxHolder("t");
        h.direct = ep("loose", 11);
        assertEquals(List.of("$.direct"), paths(LOCATOR.locate(record(h))));
    }

    @Test
    void acceptedSocketFactoryIsDescribed() throws Exception {
        FxHolder h = new FxHolder("t");
        h.direct = new FxEndpoint("s", 1, new FxSocketFactoryImpl("local"));
        EndpointLocator accepting = new EndpointLocator(CONFIG.toBuilder()
                .socketFactoryPolicy(sf -> sf.kind() == EndpointFinding.SocketFactory.Kind.RECORD
                        && sf.classNames().equals(List.of(SF)))
                .build());

        EndpointFinding f = accepting.locate(record(h)).get(0);

        assertEquals(EndpointFinding.SocketFactory.Kind.RECORD, f.socketFactory().kind());
        assertEquals(List.of(SF), f.socketFactory().classNames());
    }

    // -------------------------------------------------------------------------
    // Refusals
    // -------------------------------------------------------------------------

    @Test
    void refusesUnrecognisedRecordInRequiredSlot() throws Exception {
        FxHolder h = new FxHolder("t");
        h.service = proxy(new FxHandler(new FxObjectEndpoint(new FxUnrecognised("x"), "id")));
        MarshalledInstanceRecord rec = record(h);

        DerException e = assertThrows(DerException.class, () -> LOCATOR.locate(rec));

        assertTrue(e.getMessage().contains("$.service.<handler>.oe.ep"), e.getMessage());
        assertTrue(e.getMessage().contains("unrecognised class"), e.getMessage());
        // Without the required slot the same bytes are accepted and nothing is found.
        EndpointLocator lax = new EndpointLocator(CONFIG.toBuilder().clearRequiredEndpointSlots().build());
        assertTrue(lax.locate(rec).isEmpty());
    }

    @Test
    void refusesNullInRequiredSlot() throws Exception {
        FxHolder h = new FxHolder("t");
        h.service = proxy(new FxHandler(new FxObjectEndpoint(null, "id")));

        DerException e = assertThrows(DerException.class, () -> LOCATOR.locate(record(h)));

        assertTrue(e.getMessage().contains("$.service.<handler>.oe.ep"), e.getMessage());
    }

    @Test
    void refusesJossMarshalledInstance() throws Exception {
        FxHolder h = new FxHolder("t");
        h.mi = new MarshalledInstance("carried by JOSS");
        assertEquals(MarshalledInstance.FORMAT_JOSS, h.mi.getPayloadFormat());

        DerException e = assertThrows(DerException.class, () -> LOCATOR.locate(record(h)));

        assertTrue(e.getMessage().contains("$.mi"), e.getMessage());
        assertTrue(e.getMessage().contains("JOSS"), e.getMessage());
    }

    @Test
    void refusesSocketFactoryNotAccepted() throws Exception {
        FxHolder h = new FxHolder("t");
        h.direct = new FxEndpoint("s", 1, new FxSocketFactoryImpl("remote"));

        DerException e = assertThrows(DerException.class, () -> LOCATOR.locate(record(h)));

        assertTrue(e.getMessage().contains("$.direct.sf"), e.getMessage());
        assertTrue(e.getMessage().contains(SF), e.getMessage());
    }

    @Test
    void refusesNestingBeyondMaxNesting() throws Exception {
        assertTrue(LOCATOR_FOR_LINKS.locate(links(ObjectCodec.MAX_NESTING)).isEmpty());
        DerException e = assertThrows(DerException.class,
                () -> LOCATOR_FOR_LINKS.locate(links(ObjectCodec.MAX_NESTING + 1)));
        assertTrue(e.getMessage().contains("MAX_NESTING"), e.getMessage());
    }

    @Test
    void refusesMarshalledInstanceNestingBeyondLimit() throws Exception {
        int limit = DerInputLimits.DEFAULT.maxMarshalledInstanceNesting();
        assertEquals(1, LOCATOR.locate(record(nestedMis(limit - 1))).size());
        DerException e = assertThrows(DerException.class, () -> LOCATOR.locate(record(nestedMis(limit))));
        assertTrue(e.getMessage().contains("MarshalledInstance nesting"), e.getMessage());
    }

    @Test
    void refusesTrailingBytes() throws Exception {
        FxHolder h = new FxHolder("t");
        h.direct = ep("h", 1);
        MarshalledInstanceRecord rec = record(h);
        byte[] p = rec.payloadBytes();
        byte[] longer = Arrays.copyOf(p, p.length + 2);
        MarshalledInstanceRecord bad = new MarshalledInstanceRecord(longer, rec.schemaBytes(),
                rec.schemaDigest(), rec.payloadFormat());

        assertThrows(DerException.class, () -> LOCATOR.locate(bad));
    }

    @Test
    void refusesMalformedNestedTlv() throws Exception {
        FxHolder h = new FxHolder("t");
        h.direct = ep("h", 1);
        MarshalledInstanceRecord rec = record(h);
        Location loc = LOCATOR.locate(rec).get(0).location();
        byte[] p = rec.payloadBytes();
        p[loc.offset()] = 0x31; // the endpoint record's SEQUENCE becomes a SET
        MarshalledInstanceRecord bad = new MarshalledInstanceRecord(p, rec.schemaBytes(),
                rec.schemaDigest(), rec.payloadFormat());

        DerException e = assertThrows(DerException.class, () -> LOCATOR.locate(bad));
        assertTrue(e.getMessage().contains("$.direct"), e.getMessage());
    }

    @Test
    void refusesMarshalledInstanceWhoseHashDoesNotMatchItsPayload() throws Exception {
        FxHolder h = new FxHolder("t");
        h.mi = new DerMarshalledInstance(ep("hostA", 1));
        MarshalledInstanceRecord rec = record(h);
        byte[] p = rec.payloadBytes();
        int at = indexOf(p, "hostA".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        p[at + 4] = 'B'; // same length, still a valid record, but the hash is now stale
        MarshalledInstanceRecord bad = new MarshalledInstanceRecord(p, rec.schemaBytes(),
                rec.schemaDigest(), rec.payloadFormat());

        DerException e = assertThrows(DerException.class, () -> LOCATOR.locate(bad));
        assertTrue(e.getMessage().contains("hash"), e.getMessage());
    }

    @Test
    void refusesObjectStreamArrayPayload() throws Exception {
        Map<String, Object> fields = miFields(new DerMarshalledInstance(
                new FxEndpointType[] {ep("arr", 1)}, Collections.emptyList(), false));
        DerException e = assertThrows(DerException.class, () -> LOCATOR.locateMarshalledPayload(
                (byte[]) fields.get("payloadBytes"), (byte[]) fields.get("schemaBytes"),
                (String) fields.get("payloadFormat")));
        assertTrue(e.getMessage().contains("not walked"), e.getMessage());
    }

    // -------------------------------------------------------------------------
    // Byte locations
    // -------------------------------------------------------------------------

    private static FxHolder everything() throws Exception {
        FxHolder h = new FxHolder("t");
        h.service = proxy(handler(ep("h1", 1)));
        h.direct = ep("h2", 2);
        h.array = new FxEndpointType[] {ep("a", 3)};
        h.list = List.of(ep("b", 4));
        Map<String, FxEndpointType> m = new LinkedHashMap<>();
        m.put("k", ep("c", 5));
        h.map = m;
        h.set = Set.of(ep("d", 6));
        h.any = List.of(ep("e", 7));
        MarshalledInstance smart = new DerMarshalledInstance(proxy(handler(ep("smart", 8))),
                Collections.emptyList(), false);
        h.mi = new DerMarshalledInstance(new FxCarrier(handler(ep("boot", 9)), smart));
        return h;
    }

    @Test
    void reportedRangesAreExactTlvs() throws Exception {
        MarshalledInstanceRecord rec = record(everything());
        byte[] p = rec.payloadBytes();
        List<EndpointFinding> fs = LOCATOR.locate(rec);
        assertEquals(9, fs.size());
        for (EndpointFinding f : fs) {
            Location loc = f.location();
            assertEquals(Form.NESTED_RECORD, loc.form(), f.path());
            // The cut-out range is one nested record that decodes, class-free, to the same endpoint.
            byte[] cut = Arrays.copyOfRange(p, loc.offset(), loc.offset() + loc.length());
            List<EndpointFinding> again = LOCATOR.locateNestedField(cut, 0, cut.length, "$cut");
            assertEquals(1, again.size(), f.path());
            assertEquals("$cut", again.get(0).path());
            assertEquals(f.className(), again.get(0).className());
            assertEquals(f.host(), again.get(0).host());
            assertEquals(f.port(), again.get(0).port());
            assertEquals(f.fields(), again.get(0).fields());
            // Every enclosing TLV is where it is said to be, and each contains the next.
            int lo = 0;
            int hi = p.length;
            for (Enclosing e : loc.enclosing()) {
                DerReader r = new DerReader(p, e.offset(), p.length);
                DerReader.TlvHeader hdr = r.readTlvHeader();
                assertEquals(e.headerLength(), r.position() - e.offset(), f.path() + " " + e);
                assertEquals(e.contentLength(), hdr.contentLength(), f.path() + " " + e);
                assertTrue(e.offset() >= lo && e.end() <= hi, f.path() + " nesting " + e);
                lo = e.contentOffset();
                hi = e.end();
                MarshalledInstanceHash mih = e.marshalledInstanceHash();
                if (e.kind() == Kind.MARSHALLED_INSTANCE_PAYLOAD) {
                    assertNotNull(mih);
                    DerReader hr = new DerReader(p, mih.offset(), mih.offset() + mih.length());
                    assertEquals(mih.value(), hr.readInteger().intValueExact());
                    assertFalse(hr.hasMore());
                    assertEquals(mih.value(), hash(p, e.contentOffset(), e.end()));
                } else {
                    assertNull(mih);
                }
            }
            assertTrue(loc.offset() >= lo && loc.offset() + loc.length() <= hi, f.path());
        }
    }

    /**
     * The location is sufficient: replacing each endpoint by another and repairing only the
     * reported enclosing lengths and hashes yields bytes the locator accepts, with the new
     * endpoint at the same path and every other endpoint unchanged.
     */
    @Test
    void replacingAtReportedLocationsYieldsValidBytes() throws Exception {
        MarshalledInstanceRecord rec = record(everything());
        List<EndpointFinding> before = LOCATOR.locate(rec);
        byte[] replacement = nestedRecord(new FxEndpoint("relay.example", 4242, null));
        int rewritten = 0;
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i).location().enclosing().stream().anyMatch(Enclosing::reorderOnChange)) {
                continue; // a canonical set must also be re-sorted; out of scope here
            }
            byte[] p = rewrite(rec.payloadBytes(), before.get(i).location(), replacement);
            MarshalledInstanceRecord out = new MarshalledInstanceRecord(p, rec.schemaBytes(),
                    rec.schemaDigest(), rec.payloadFormat());

            List<EndpointFinding> after = LOCATOR.locate(out);

            assertEquals(paths(before), paths(after));
            for (int j = 0; j < after.size(); j++) {
                if (j == i) {
                    assertEquals("relay.example", after.get(j).host(), before.get(i).path());
                    assertEquals(Integer.valueOf(4242), after.get(j).port());
                } else {
                    assertEquals(before.get(j).host(), after.get(j).host());
                }
            }
            rewritten++;
        }
        assertEquals(before.size() - 1, rewritten, "only the canonical-set element is skipped");
    }

    // -------------------------------------------------------------------------
    // No class named by the data is loaded
    // -------------------------------------------------------------------------

    /** Set by the static initialisers of the tripwire classes, and by nothing else. */
    static volatile boolean tripwireInitialised;

    /** Never referred to except by name. */
    static final class TripwireEndpoint {
        static {
            tripwireInitialised = true;
        }
    }

    /** Never referred to except by name. */
    static final class TripwireHandler {
        static {
            tripwireInitialised = true;
        }
    }

    /** Never referred to except by name. */
    static final class TripwireFactory {
        static {
            tripwireInitialised = true;
        }
    }

    /** Never referred to except by name. */
    interface TripwireService {
        Object SIDE_EFFECT = mark();

        private static Object mark() {
            tripwireInitialised = true;
            return null;
        }
    }

    private static final String T = EndpointLocatorTest.class.getName() + "$";

    @Test
    void locatingInitialisesNoClassNamedByTheData() throws Exception {
        handBuiltProxyGraph(T + "TripwireEndpoint", T + "TripwireHandler", T + "TripwireFactory",
                T + "TripwireService");
        assertFalse(tripwireInitialised, "locating initialised a class named by the data");
        // The names were resolvable all along: a class-loading locator could have reached them.
        Class.forName(T + "TripwireEndpoint", false, EndpointLocatorTest.class.getClassLoader());
        assertFalse(tripwireInitialised);
    }

    @Test
    void locatingWorksWhenNoClassNamedByTheDataExists() throws Exception {
        handBuiltProxyGraph("com.example.absent.Endpoint", "com.example.absent.Handler",
                "com.example.absent.Factory", "com.example.absent.Service");
    }

    private static void handBuiltProxyGraph(String epClass, String handlerClass, String factoryClass,
                                            String serviceIface) throws Exception {
        String holderClass = "com.example.absent.Holder";
        String oeClass = "com.example.absent.ObjectEndpoint";
        byte[] factory = handBuilt(factoryClass, List.of(field("name", "java.lang.String")),
                List.of(DerWriter.writeUtf8String("f")));
        byte[] endpoint = handBuilt(epClass,
                List.of(field("host", "java.lang.String"), field("port", "int"), field("sf", "@AtomicSerial")),
                List.of(DerWriter.writeUtf8String("th"), DerWriter.writeInteger(7), factory));
        byte[] oe = handBuilt(oeClass,
                List.of(field("ep", "@AtomicSerial"), field("id", "java.lang.String")),
                List.of(endpoint, DerWriter.writeUtf8String("id")));
        byte[] handler = handBuilt(handlerClass, List.of(field("oe", "@AtomicSerial")), List.of(oe));
        byte[] proxy = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 8),
                concat(DerWriter.writeInteger(1), DerWriter.writeUtf8String(serviceIface), handler));
        byte[] second = handBuilt(epClass,
                List.of(field("host", "java.lang.String"), field("port", "int"), field("sf", "@AtomicSerial")),
                List.of(DerWriter.writeUtf8String("any"), DerWriter.writeInteger(8), new byte[] {0x05, 0x00}));
        byte[] anyList = DerWriter.writeSequence(List.of(
                DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 20), second)));
        SchemaChain.Result chain = SchemaChain.linkAndGetLeafDigest(List.of(new AtomicSerialSchemaRecord(
                holderClass, List.of(field("svc", "@AtomicSerial"),
                        field("things", CollectionWireTypes.listToken("any"))))));
        byte[] payload = DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of(proxy, anyList))));
        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);
        EndpointLocator locator = new EndpointLocator(EndpointLocatorConfig.builder()
                .endpoint(new EndpointLocatorConfig.EndpointClass(epClass, "host", "port", "sf"))
                .requiredEndpointSlot(oeClass, "ep")
                .socketFactoryPolicy(sf -> sf.classNames().equals(List.of(factoryClass)))
                .build());

        List<EndpointFinding> fs = locator.locate(rec);

        assertEquals(List.of("$.svc.<handler>.oe.ep", "$.things[0]"), paths(fs));
        assertEquals("th", fs.get(0).host());
        assertEquals(List.of(factoryClass), fs.get(0).socketFactory().classNames());
        assertEquals("any", fs.get(1).host());
        assertEquals(Integer.valueOf(8), fs.get(1).port());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AtomicSerialFieldDef field(String name, String wireType) {
        return new AtomicSerialFieldDef(name, wireType);
    }

    /** A single-class nested record built by hand, naming {@code className} only as a string. */
    private static byte[] handBuilt(String className, List<AtomicSerialFieldDef> fields, List<byte[]> values) {
        SchemaChain.Result chain = SchemaChain.linkAndGetLeafDigest(
                List.of(new AtomicSerialSchemaRecord(className, fields)));
        byte[] hierarchy = DerWriter.writeSequence(List.of(DerWriter.writeSequence(values)));
        return DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(chain.chain().get(0).encode()),
                DerWriter.writeOctetString(hierarchy)));
    }

    private static final String LINK = "com.example.absent.Link";

    private static final EndpointLocator LOCATOR_FOR_LINKS = new EndpointLocator(
            EndpointLocatorConfig.builder().build());

    /** A top-level record with {@code nested} nested records below it, each in field {@code next}. */
    private static MarshalledInstanceRecord links(int nested) {
        List<AtomicSerialFieldDef> fields = List.of(field("next", "@AtomicSerial"));
        byte[] next = new byte[] {0x05, 0x00};
        for (int i = 0; i < nested; i++) {
            next = handBuilt(LINK, fields, List.of(next));
        }
        SchemaChain.Result chain = SchemaChain.linkAndGetLeafDigest(
                List.of(new AtomicSerialSchemaRecord(LINK, fields)));
        byte[] payload = DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of(next))));
        return MarshalledInstanceRecord.fromChain(chain, payload);
    }

    /** {@code levels} DER MarshalledInstances, each the payload of the previous, around an endpoint. */
    private static MarshalledInstance nestedMis(int levels) throws Exception {
        Object o = ep("deep", 1);
        for (int i = 0; i < levels; i++) {
            o = new DerMarshalledInstance(o);
        }
        return (MarshalledInstance) o;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] b : parts) {
            out.writeBytes(b);
        }
        return out.toByteArray();
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = hay.length - needle.length; i >= 0; i--) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new AssertionError("not found");
    }

    private static int hash(byte[] b, int from, int to) {
        int h = 0;
        for (int i = from; i < to; i++) {
            h = 31 * h + b[i];
        }
        return h;
    }

    /**
     * Replaces the located range and repairs every enclosing length, innermost first, and the
     * hash of every enclosing MarshalledInstance. Uses nothing but the reported location.
     */
    private static byte[] rewrite(byte[] buf, Location loc, byte[] replacement) throws DerException {
        int innerStart = loc.offset();
        int innerEnd = loc.offset() + loc.length();
        byte[] inner = replacement;
        MarshalledInstanceHash pendingAt = null;
        int pendingValue = 0;
        List<Enclosing> enc = loc.enclosing();
        for (int i = enc.size() - 1; i >= 0; i--) {
            Enclosing e = enc.get(i);
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            content.write(buf, e.contentOffset(), innerStart - e.contentOffset());
            content.writeBytes(inner);
            int tailStart = innerEnd;
            if (pendingAt != null && pendingAt.offset() >= innerEnd && pendingAt.offset() < e.end()) {
                content.write(buf, tailStart, pendingAt.offset() - tailStart);
                content.writeBytes(DerWriter.writeInteger(pendingValue));
                tailStart = pendingAt.offset() + pendingAt.length();
                pendingAt = null;
            }
            content.write(buf, tailStart, e.end() - tailStart);
            byte[] c = content.toByteArray();
            if (e.kind() == Kind.MARSHALLED_INSTANCE_PAYLOAD) {
                pendingAt = e.marshalledInstanceHash();
                pendingValue = hash(c, 0, c.length);
            }
            int tagLen = Tag.decode(buf, e.offset()).bytesRead();
            inner = concat(Arrays.copyOfRange(buf, e.offset(), e.offset() + tagLen),
                    DerWriter.encodeLength(c.length), c);
            innerStart = e.offset();
            innerEnd = e.end();
        }
        assertNull(pendingAt, "every hash was repaired");
        return concat(Arrays.copyOfRange(buf, 0, innerStart), inner,
                Arrays.copyOfRange(buf, innerEnd, buf.length));
    }
}
