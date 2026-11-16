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
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.Enclosing;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.Form;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.Kind;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.Location;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.MarshalledInstanceHash;
import au.net.zeus.jgdms.der.inspect.EndpointFinding.SocketFactory;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.ProxyWireSupport;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finds every recognised endpoint record inside DER (JGDMS-STD-006 Atomic DER) bytes without
 * loading, resolving or initialising any class named by the data.
 *
 * <p>The locator is read-only. It walks the bytes against the schema chains they carry, using
 * the codec's own class-free checks ({@link SchemaChain#decodeChain},
 * {@link DerFieldStore#classFree}, {@link DerFieldStore#validateValueClassFree},
 * {@link ObjectCodec#validateAnyScalarArm}) and its ceilings ({@link ObjectCodec#MAX_NESTING},
 * {@link ObjectCodec#MAX_COLLECTION}, {@link ProxyWireSupport#MAX_PROXY_INTERFACES},
 * {@link DerInputLimits#maxMarshalledInstanceNesting()}). It descends into nested records,
 * {@code [8]} proxies (interface names and handler), {@code @AtomicSerial} arrays, collections
 * and maps (keys and values), {@code Any} slots, {@code [1]} object-stream records and the
 * payload of every DER-format {@code net.jini.io.MarshalledInstance} (at any level of a record's
 * class chain, so a subclass is handled too).
 *
 * <p>A record whose leaf class is a configured endpoint class is reported wherever it is
 * found. It refuses, with a {@link DerException} naming the path, when:
 * <ul>
 *   <li>a configured required endpoint slot holds anything but a recognised endpoint record;</li>
 *   <li>a {@code MarshalledInstance} carries a non-null payload in a format other than
 *       {@link MarshalledInstanceRecord#PAYLOAD_FORMAT}, or its {@code hash} does not match its
 *       payload;</li>
 *   <li>a recognised endpoint has a non-null socket factory that the configured policy does
 *       not accept;</li>
 *   <li>nesting exceeds the codec's limits;</li>
 *   <li>any TLV is malformed, any value is invalid for its wire type, or bytes are left over;</li>
 *   <li>an object-stream item is a {@code [9]} array or {@code [16]} collection, which this
 *       locator does not walk.</li>
 * </ul>
 *
 * <p>Depth is counted as the decoder counts it: structural nesting restarts at zero inside a
 * {@code MarshalledInstance} payload and inside an object-stream item (each is a fresh decode),
 * and the number of nested {@code MarshalledInstance} payloads is bounded separately. The input to
 * {@link #locate} and {@link #locateMarshalledPayload} counts as one such payload, which makes the
 * bound one level stricter than a decoder that starts outside any payload.
 *
 * <p>Only a class named exactly {@code net.jini.io.MarshalledInstance} is descended into. A
 * payload carried in a {@code byte[]} field of any other class is opaque to the locator.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class EndpointLocator {

    /** The class whose records carry a nested payload. */
    static final String MARSHALLED_INSTANCE = "net.jini.io.MarshalledInstance";

    private static final String JOSS = "JOSS";
    private static final String ANY = "any";
    private static final String NESTED = "@AtomicSerial";

    private static final Tag CTX_ATOMIC = new Tag(Tag.CLASS_CONTEXT, true, 1);
    private static final Tag CTX_ENUM = new Tag(Tag.CLASS_CONTEXT, true, 7);
    private static final Tag CTX_PROXY = new Tag(Tag.CLASS_CONTEXT, true, 8);
    private static final Tag NULL = new Tag(Tag.CLASS_UNIVERSAL, false, 5);

    private final EndpointLocatorConfig config;

    /**
     * Creates a locator with {@link EndpointLocatorConfig#defaults()}.
     */
    public EndpointLocator() {
        this(EndpointLocatorConfig.defaults());
    }

    /**
     * Creates a locator.
     *
     * @param config what to recognise and refuse
     */
    public EndpointLocator(EndpointLocatorConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * The configuration.
     *
     * @return the configuration
     */
    public EndpointLocatorConfig config() {
        return config;
    }

    /**
     * Locates endpoints in a top-level schema-separated record. Offsets are into
     * {@code record.payloadBytes()}. If the record itself is an endpoint it is reported with
     * {@link Form#PAYLOAD} and no enclosing TLVs.
     *
     * @param record the record
     * @return the findings, in document order
     * @throws DerException if the record is refused
     */
    public List<EndpointFinding> locate(MarshalledInstanceRecord record) throws DerException {
        Objects.requireNonNull(record, "record");
        byte[] payload = record.payloadBytes();
        Walker w = new Walker(payload);
        Ctx root = Ctx.root("$", 1);
        try {
            if (!MarshalledInstanceRecord.PAYLOAD_FORMAT.equals(record.payloadFormat())) {
                throw fail(root, "record payloadFormat '" + record.payloadFormat()
                        + "' is not " + MarshalledInstanceRecord.PAYLOAD_FORMAT);
            }
            List<AtomicSerialSchemaRecord> chain = w.chain(root, record.schemaBytes());
            if (!Arrays.equals(chain.get(0).schemaDigest(), record.schemaDigest())) {
                throw fail(root, "schemaDigest does not match the leaf schema record");
            }
            w.walkHierarchy(0, payload.length, chain, root,
                    new Target(Form.PAYLOAD, 0, payload.length, root.frames));
        } catch (RuntimeException e) {
            throw internal(root, e);
        }
        return w.result();
    }

    /**
     * Locates endpoints in the serialized state of a {@code MarshalledInstance}: its
     * {@code payloadBytes}, {@code schemaBytes} and {@code payloadFormat} fields. An empty
     * {@code schemaBytes} means the payload is a single record-level object-stream item (the form
     * {@code DerMarshalInstanceOutput} writes for a value with no {@code @AtomicSerial} class,
     * such as a bare proxy); otherwise it is a hierarchy payload described by
     * {@code schemaBytes}. Offsets are into {@code payloadBytes}.
     *
     * @param payloadBytes  the payload, or {@code null} for a marshalled {@code null}
     * @param schemaBytes   the schema chain, possibly empty or {@code null}
     * @param payloadFormat the payload format ({@code null} is read as {@code "JOSS"})
     * @return the findings, in document order
     * @throws DerException if the payload is refused
     */
    public List<EndpointFinding> locateMarshalledPayload(byte[] payloadBytes, byte[] schemaBytes,
                                                         String payloadFormat) throws DerException {
        if (payloadBytes == null) {
            return List.of();
        }
        Walker w = new Walker(payloadBytes.clone());
        Ctx root = Ctx.root("$", 1);
        try {
            String fmt = payloadFormat == null ? JOSS : payloadFormat;
            if (!MarshalledInstanceRecord.PAYLOAD_FORMAT.equals(fmt)) {
                throw fail(root, "payloadFormat '" + fmt + "' is not "
                        + MarshalledInstanceRecord.PAYLOAD_FORMAT);
            }
            int len = payloadBytes.length;
            if (schemaBytes == null || schemaBytes.length == 0) {
                w.walkStreamItem(0, len, root);
            } else {
                List<AtomicSerialSchemaRecord> chain = w.chain(root, schemaBytes);
                w.walkHierarchy(0, len, chain, root, new Target(Form.PAYLOAD, 0, len, root.frames));
            }
        } catch (RuntimeException e) {
            throw internal(root, e);
        }
        return w.result();
    }

    /**
     * Locates endpoints in one polymorphic field value: a nested-record {@code SEQUENCE}, a
     * field-form {@code [8]} proxy, a {@code [7]} enum or DER {@code NULL}, occupying exactly
     * {@code buf[offset, offset + length)}. Offsets in the findings are into {@code buf}.
     *
     * @param buf    the bytes; not modified, and must not be modified during the call
     * @param offset start of the value's TLV
     * @param length length of the value's TLV
     * @param path   the path to report for the value
     * @return the findings, in document order
     * @throws DerException if the value is refused
     */
    public List<EndpointFinding> locateNestedField(byte[] buf, int offset, int length, String path)
            throws DerException {
        Objects.checkFromIndexSize(offset, length, buf.length);
        Walker w = new Walker(buf);
        Ctx root = Ctx.root(Objects.requireNonNull(path, "path"), 0);
        try {
            w.walkSlot(offset, offset + length, root, false);
        } catch (RuntimeException e) {
            throw internal(root, e);
        }
        return w.result();
    }

    /**
     * Locates endpoints in one record-level object-stream item occupying exactly
     * {@code buf[offset, offset + length)}: a {@code [1]} record, a {@code [8]} proxy with a
     * {@code [1]} handler, a {@code [7]} enum, {@code [0]} null or a scalar item. Items using
     * stream schema-dedup productions are not in record-level form and are refused as
     * malformed. Offsets in the findings are into {@code buf}.
     *
     * @param buf    the bytes; not modified, and must not be modified during the call
     * @param offset start of the item's TLV
     * @param length length of the item's TLV
     * @param path   the path to report for the item
     * @return the findings, in document order
     * @throws DerException if the item is refused
     */
    public List<EndpointFinding> locateStreamItem(byte[] buf, int offset, int length, String path)
            throws DerException {
        Objects.checkFromIndexSize(offset, length, buf.length);
        Walker w = new Walker(buf);
        Ctx root = Ctx.root(Objects.requireNonNull(path, "path"), 0);
        try {
            w.walkStreamItem(offset, offset + length, root);
        } catch (RuntimeException e) {
            throw internal(root, e);
        }
        return w.result();
    }

    // =========================================================================
    // Walk state
    // =========================================================================

    /** A refusal that already names its path; never re-wrapped. */
    private static final class PathException extends DerException {
        private static final long serialVersionUID = 1L;

        PathException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static DerException fail(Ctx c, String message) {
        return new PathException("EndpointLocator: at " + c.path + ": " + message, null);
    }

    private static DerException at(Ctx c, DerException e) {
        if (e instanceof PathException) {
            return e;
        }
        return new PathException("EndpointLocator: at " + c.path + ": " + e.getMessage(), e);
    }

    private static DerException internal(Ctx c, RuntimeException e) {
        return new PathException("EndpointLocator: at " + c.path + ": refused, unexpected "
                + e.getClass().getName() + ": " + e.getMessage(), e);
    }

    /**
     * Where the walk is: the value's path, the TLVs that enclose it (outermost first), the
     * structural depth as the decoder counts it, and how many MarshalledInstance payloads deep.
     */
    private record Ctx(String path, List<Enclosing> frames, int depth, int miLevel) {

        static Ctx root(String path, int miLevel) {
            return new Ctx(path, List.of(), 0, miLevel);
        }

        Ctx child(String suffix) {
            return new Ctx(path + suffix, frames, depth, miLevel);
        }

        Ctx push(Enclosing e) {
            List<Enclosing> l = new ArrayList<>(frames.size() + 1);
            l.addAll(frames);
            l.add(e);
            return new Ctx(path, Collections.unmodifiableList(l), depth, miLevel);
        }

        Ctx depth(int d) {
            return new Ctx(path, frames, d, miLevel);
        }

        Ctx miLevel(int m) {
            return new Ctx(path, frames, depth, m);
        }
    }

    /** The range a finding reports, decided by whoever opened the hierarchy. */
    private record Target(Form form, int offset, int length, List<Enclosing> frames) {
    }

    /** One parsed TLV: absolute start, content start and end. */
    private record Tlv(Tag tag, int start, int contentStart, int end) {
        int contentLength() {
            return end - contentStart;
        }
    }

    /** One class of a hierarchy, with the field TLV ranges walked. */
    private record ClassPart(AtomicSerialSchemaRecord schema, DerFieldStore store,
                             Map<String, int[]> ranges) {
    }

    private final class Walker {

        private final byte[] buf;
        private final List<EndpointFinding> findings = new ArrayList<>();

        Walker(byte[] buf) {
            this.buf = buf;
        }

        List<EndpointFinding> result() {
            return List.copyOf(findings);
        }

        // ---------------------------------------------------------------------
        // TLV helpers
        // ---------------------------------------------------------------------

        private Tlv parse(int start, int limit) throws DerException {
            DerReader r = new DerReader(buf, start, limit);
            DerReader.TlvHeader h = r.readTlvHeader();
            int cs = r.position();
            return new Tlv(h.tag(), start, cs, cs + h.contentLength());
        }

        /** Parses the single TLV that must fill {@code [start, end)} exactly. */
        private Tlv exact(int start, int end, Ctx c) throws DerException {
            if (start >= end) {
                throw fail(c, "empty value");
            }
            Tlv t = parse(start, end);
            if (t.end != end) {
                throw fail(c, "trailing bytes after " + t.tag);
            }
            return t;
        }

        private Enclosing frame(Kind kind, Ctx c, Tlv t) {
            return frame(kind, c, t, false, null);
        }

        private Enclosing frame(Kind kind, Ctx c, Tlv t, boolean reorder, MarshalledInstanceHash h) {
            return new Enclosing(kind, c.path, t.start, t.contentStart - t.start, t.contentLength(),
                    reorder, h);
        }

        private byte[] slice(int start, int end) {
            return Arrays.copyOfRange(buf, start, end);
        }

        List<AtomicSerialSchemaRecord> chain(Ctx c, byte[] chainBytes) throws DerException {
            try {
                return SchemaChain.decodeChain(chainBytes, "EndpointLocator");
            } catch (DerException e) {
                throw at(c, e);
            }
        }

        private void checkDepth(Ctx c, int depth) throws DerException {
            if (depth > ObjectCodec.MAX_NESTING) {
                throw fail(c, "nesting depth " + depth + " exceeds MAX_NESTING ("
                        + ObjectCodec.MAX_NESTING + ")");
            }
        }

        // ---------------------------------------------------------------------
        // Field-form values
        // ---------------------------------------------------------------------

        /** A polymorphic {@code @AtomicSerial} slot value: NULL, [7], [8] or a nested record. */
        void walkSlot(int start, int end, Ctx c, boolean required) throws DerException {
            try {
                checkDepth(c, c.depth);
                Tlv t = exact(start, end, c);
                if (NULL.equals(t.tag)) {
                    if (t.contentLength() != 0) {
                        throw fail(c, "malformed NULL");
                    }
                    if (required) {
                        throw fail(c, "required endpoint slot holds null");
                    }
                    return;
                }
                if (CTX_PROXY.equals(t.tag)) {
                    if (required) {
                        throw fail(c, "required endpoint slot holds a proxy, not an endpoint record");
                    }
                    walkFieldProxy(t, c);
                    return;
                }
                if (CTX_ENUM.equals(t.tag)) {
                    if (required) {
                        throw fail(c, "required endpoint slot holds an enum, not an endpoint record");
                    }
                    enumLeaf(t);
                    return;
                }
                if (Tag.SEQUENCE.equals(t.tag)) {
                    walkNestedRecord(t, c, required);
                    return;
                }
                throw fail(c, "unexpected tag " + t.tag
                        + " in an object slot (expected NULL, [7], [8] or SEQUENCE)");
            } catch (DerException e) {
                throw at(c, e);
            }
        }

        private String[] enumLeaf(Tlv t) throws DerException {
            DerReader r = new DerReader(buf, t.contentStart, t.end);
            String className = r.readUtf8String();
            String constant = r.readUtf8String();
            if (r.hasMore()) {
                throw new DerException("trailing bytes in [7] enum content");
            }
            return new String[] {className, constant};
        }

        /** {@code SEQUENCE { OCTET STRING schemaChain, OCTET STRING payload }}. */
        private void walkNestedRecord(Tlv t, Ctx c, boolean required) throws DerException {
            checkDepth(c, c.depth + 1);
            DerReader seq = new DerReader(buf, t.contentStart, t.end);
            int chainLen = seq.readOctetStringHeader();
            int chainStart = seq.position();
            seq.skip(chainLen);
            if (!seq.hasMore()) {
                throw fail(c, "nested record has no payload");
            }
            Tlv p = parse(seq.position(), t.end);
            if (!Tag.OCTET_STRING.equals(p.tag)) {
                throw fail(c, "nested record payload is " + p.tag + ", not OCTET STRING");
            }
            if (p.end != t.end) {
                throw fail(c, "nested record has trailing content after its payload");
            }
            List<AtomicSerialSchemaRecord> records = chain(c, slice(chainStart, chainStart + chainLen));
            String leaf = records.get(0).className();
            if (required && !config.endpoints().containsKey(leaf)) {
                throw fail(c, "required endpoint slot holds a record of unrecognised class '"
                        + leaf + "'");
            }
            Ctx inner = c.push(frame(Kind.NESTED_RECORD, c, t))
                    .push(frame(Kind.RECORD_PAYLOAD, c, p))
                    .depth(c.depth + 1);
            walkHierarchy(p.contentStart, p.end, records, inner,
                    new Target(Form.NESTED_RECORD, t.start, t.end - t.start, c.frames));
        }

        /** Field-form {@code [8]}: INTEGER count, names, then the handler as a nested record. */
        private void walkFieldProxy(Tlv t, Ctx c) throws DerException {
            DerReader pr = new DerReader(buf, t.contentStart, t.end);
            proxyNames(pr, c);
            if (!pr.hasMore()) {
                throw fail(c, "[8] proxy has no handler");
            }
            int hs = pr.position();
            int he = pr.skipTlv();
            if (pr.hasMore()) {
                throw fail(c, "trailing bytes after the handler in [8] proxy");
            }
            if (!Tag.SEQUENCE.equals(parse(hs, he).tag)) {
                throw fail(c, "[8] proxy handler is not a nested record");
            }
            Ctx hc = c.push(frame(Kind.PROXY, c, t)).child(".<handler>").depth(c.depth + 1);
            walkSlot(hs, he, hc, false);
        }

        private List<String> proxyNames(DerReader pr, Ctx c) throws DerException {
            int count;
            try {
                count = pr.readInteger().intValueExact();
            } catch (ArithmeticException e) {
                throw fail(c, "[8] proxy interface count overflow");
            }
            if (count <= 0 || count > ProxyWireSupport.MAX_PROXY_INTERFACES) {
                throw fail(c, "[8] proxy interface count " + count + " out of range (1.."
                        + ProxyWireSupport.MAX_PROXY_INTERFACES + ")");
            }
            List<String> names = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                names.add(pr.readUtf8String());
            }
            return names;
        }

        /**
         * A hierarchy payload: {@code SEQUENCE} of per-class {@code SEQUENCE}s, root first,
         * walked field by field against {@code leafFirst}.
         */
        void walkHierarchy(int start, int end, List<AtomicSerialSchemaRecord> leafFirst, Ctx c,
                           Target target) throws DerException {
            try {
                checkDepth(c, c.depth);
                Tlv h = exact(start, end, c);
                if (!Tag.SEQUENCE.equals(h.tag)) {
                    throw fail(c, "hierarchy payload is " + h.tag + ", not SEQUENCE");
                }
                Ctx hc = c.push(frame(Kind.HIERARCHY, c, h));
                int findingIndex = findings.size();
                EndpointLocatorConfig.EndpointClass spec =
                        config.endpoints().get(leafFirst.get(0).className());
                List<ClassPart> parts = new ArrayList<>(leafFirst.size());
                DerReader hr = new DerReader(buf, h.contentStart, h.end);
                for (int i = leafFirst.size() - 1; i >= 0; i--) {
                    AtomicSerialSchemaRecord rec = leafFirst.get(i);
                    if (!hr.hasMore()) {
                        throw fail(c, "hierarchy has no SEQUENCE for class '" + rec.className() + "'");
                    }
                    int cs = hr.position();
                    int ce = hr.skipTlv();
                    Tlv cls = parse(cs, ce);
                    if (!Tag.SEQUENCE.equals(cls.tag)) {
                        throw fail(c, "fields of class '" + rec.className() + "' are " + cls.tag
                                + ", not SEQUENCE");
                    }
                    boolean isMi = MARSHALLED_INSTANCE.equals(rec.className());
                    DerFieldStore store = null;
                    if (spec != null || isMi) {
                        store = DerFieldStore.classFree(rec, new DerReader(buf, cs, ce));
                    }
                    Ctx cc = hc.push(frame(Kind.CLASS_FIELDS, c, cls));
                    Map<String, int[]> ranges = walkClassFields(rec, cls, cc);
                    parts.add(new ClassPart(rec, store, ranges));
                    if (isMi) {
                        walkMarshalledInstance(store, ranges, cc);
                    }
                }
                if (hr.hasMore()) {
                    throw fail(c, "hierarchy has more class SEQUENCEs than its schema chain");
                }
                if (spec != null) {
                    findings.add(findingIndex, finding(spec, leafFirst, parts, c, target));
                }
            } catch (DerException e) {
                throw at(c, e);
            }
        }

        private Map<String, int[]> walkClassFields(AtomicSerialSchemaRecord rec, Tlv cls, Ctx cc)
                throws DerException {
            Map<String, int[]> ranges = new LinkedHashMap<>();
            DerReader fr = new DerReader(buf, cls.contentStart, cls.end);
            for (AtomicSerialFieldDef def : rec.fields()) {
                if (!fr.hasMore()) {
                    throw fail(cc, "class '" + rec.className() + "' has no TLV for field '"
                            + def.wireName() + "'");
                }
                int fs = fr.position();
                int fe = fr.skipTlv();
                ranges.put(def.wireName(), new int[] {fs, fe});
                boolean required = config.isRequiredSlot(rec.className(), def.wireName());
                walkField(fs, fe, def.wireType(), cc.child("." + def.wireName()), required);
            }
            if (fr.hasMore()) {
                throw fail(cc, "class '" + rec.className() + "' has more TLVs than schema fields");
            }
            return ranges;
        }

        private void walkField(int fs, int fe, String wireType, Ctx c, boolean required)
                throws DerException {
            try {
                if (required && !NESTED.equals(wireType)) {
                    throw fail(c, "required endpoint slot has wire type '" + wireType
                            + "', not '" + NESTED + "'");
                }
                if (NESTED.equals(wireType)) {
                    walkSlot(fs, fe, c, required);
                } else if (wireType.startsWith("array:" + NESTED + ":")) {
                    walkNestedArray(fs, fe, c);
                } else if (CollectionWireTypes.isCollection(wireType)) {
                    walkCollection(fs, fe, wireType, c);
                } else if (ANY.equals(wireType)) {
                    walkAny(fs, fe, c);
                } else {
                    validateValue(fs, fe, wireType, c);
                }
            } catch (DerException e) {
                throw at(c, e);
            }
        }

        private void validateValue(int s, int e, String wireType, Ctx c) throws DerException {
            DerReader r = new DerReader(buf, s, e);
            DerFieldStore.validateValueClassFree(r, wireType);
            if (r.hasMore()) {
                throw fail(c, "trailing bytes after value of wire type '" + wireType + "'");
            }
        }

        private void walkNestedArray(int s, int e, Ctx c) throws DerException {
            Tlv t = exact(s, e, c);
            if (NULL.equals(t.tag) && t.contentLength() == 0) {
                return;
            }
            if (!Tag.SEQUENCE.equals(t.tag)) {
                throw fail(c, "object array is " + t.tag + ", not SEQUENCE or NULL");
            }
            Ctx ac = c.push(frame(Kind.ARRAY, c, t));
            DerReader r = new DerReader(buf, t.contentStart, t.end);
            int i = 0;
            while (r.hasMore()) {
                int es = r.position();
                int ee = r.skipTlv();
                walkSlot(es, ee, ac.child("[" + i + "]"), false);
                i++;
            }
        }

        private void walkCollection(int s, int e, String token, Ctx c) throws DerException {
            checkDepth(c, c.depth);
            Tlv t = exact(s, e, c);
            if (NULL.equals(t.tag) && t.contentLength() == 0) {
                return;
            }
            boolean canonical = CollectionWireTypes.isCanonicalise(token);
            Tag expected = canonical ? Tag.SET : Tag.SEQUENCE;
            if (!expected.equals(t.tag)) {
                throw fail(c, "collection '" + token + "' is " + t.tag + ", not " + expected);
            }
            Enclosing plain = frame(Kind.COLLECTION, c, t, false, null);
            Enclosing ordered = frame(Kind.COLLECTION, c, t, canonical, null);
            DerReader body = new DerReader(buf, t.contentStart, t.end);
            int count = 0;
            int prevStart = -1;
            int prevEnd = -1;
            if (CollectionWireTypes.isMap(token)) {
                String[] kv = CollectionWireTypes.mapKeyValueWireTypes(token);
                while (body.hasMore()) {
                    if (++count > ObjectCodec.MAX_COLLECTION) {
                        throw fail(c, "entry count exceeds MAX_COLLECTION ("
                                + ObjectCodec.MAX_COLLECTION + ")");
                    }
                    int i = count - 1;
                    int es = body.position();
                    Tlv entry = parse(es, body.skipTlv());
                    if (!Tag.SEQUENCE.equals(entry.tag)) {
                        throw fail(c, "map entry is " + entry.tag + ", not SEQUENCE");
                    }
                    DerReader er = new DerReader(buf, entry.contentStart, entry.end);
                    if (!er.hasMore()) {
                        throw fail(c, "map entry has no key");
                    }
                    int ks = er.position();
                    int ke = er.skipTlv();
                    if (!er.hasMore()) {
                        throw fail(c, "map entry has no value");
                    }
                    int vs = er.position();
                    int ve = er.skipTlv();
                    if (er.hasMore()) {
                        throw fail(c, "map entry has more than {key, value}");
                    }
                    Ctx kc = c.push(ordered).push(frame(Kind.MAP_ENTRY, c, entry));
                    Ctx vc = c.push(plain).push(frame(Kind.MAP_ENTRY, c, entry));
                    walkElement(ks, ke, kv[0], kc.child("[key " + i + "]"));
                    walkElement(vs, ve, kv[1], vc.child("[value " + i + "]"));
                    if (canonical) {
                        checkOrder(c, prevStart, prevEnd, ks, ke, false);
                        prevStart = ks;
                        prevEnd = ke;
                    }
                }
                return;
            }
            String elemWT = CollectionWireTypes.elementWireType(token);
            boolean multiset = CollectionWireTypes.isMultiset(token);
            Ctx ec = c.push(ordered);
            while (body.hasMore()) {
                if (++count > ObjectCodec.MAX_COLLECTION) {
                    throw fail(c, "element count exceeds MAX_COLLECTION ("
                            + ObjectCodec.MAX_COLLECTION + ")");
                }
                int es = body.position();
                int ee = body.skipTlv();
                walkElement(es, ee, elemWT, ec.child("[" + (count - 1) + "]"));
                if (canonical) {
                    checkOrder(c, prevStart, prevEnd, es, ee, multiset);
                    prevStart = es;
                    prevEnd = ee;
                }
            }
        }

        private void checkOrder(Ctx c, int ps, int pe, int s, int e, boolean multiset)
                throws DerException {
            if (ps < 0) {
                return;
            }
            int cmp = CollectionWireTypes.compareOctets(buf, ps, pe - ps, buf, s, e - s);
            if (CollectionWireTypes.orderViolated(cmp, multiset)) {
                throw fail(c, "canonical collection is not in X.690 sec.11.6 octet order");
            }
        }

        private void walkElement(int s, int e, String wireType, Ctx c) throws DerException {
            try {
                if (NESTED.equals(wireType)) {
                    walkSlot(s, e, c, false);
                } else if (CollectionWireTypes.isCollection(wireType)) {
                    walkCollection(s, e, wireType, c.depth(c.depth + 1));
                } else if (ANY.equals(wireType)) {
                    walkAny(s, e, c);
                } else {
                    validateValue(s, e, wireType, c);
                }
            } catch (DerException ex) {
                throw at(c, ex);
            }
        }

        /** A self-describing {@code Any} element: NULL, [0]..[9] scalar, [20] object, [30]/[31] collection. */
        private void walkAny(int s, int e, Ctx c) throws DerException {
            Tlv t = exact(s, e, c);
            if (NULL.equals(t.tag)) {
                if (t.contentLength() != 0) {
                    throw fail(c, "malformed NULL Any element");
                }
                return;
            }
            if (t.tag.tagClass() != Tag.CLASS_CONTEXT) {
                throw fail(c, "Any element has non-context tag " + t.tag);
            }
            int n = t.tag.tagNumber();
            boolean constructed = t.tag.isConstructed();
            if (n <= 9) {
                if (constructed) {
                    throw fail(c, "scalar Any tag [" + n + "] must be primitive");
                }
                ObjectCodec.validateAnyScalarArm(n, slice(t.contentStart, t.end));
                return;
            }
            if (n != 20 && n != 30 && n != 31) {
                throw fail(c, "unregistered Any tag [" + n + "]");
            }
            if (!constructed) {
                throw fail(c, "Any tag [" + n + "] must be constructed");
            }
            Tlv inner = exact(t.contentStart, t.end, c);
            int next = c.depth + 1;
            checkDepth(c, next);
            Ctx ic = c.push(frame(Kind.ANY, c, t)).depth(next);
            if (n == 20) {
                walkSlot(inner.start, inner.end, ic, false);
                return;
            }
            boolean canonical = n == 30;
            if (!(canonical ? Tag.SET : Tag.SEQUENCE).equals(inner.tag)) {
                throw fail(c, "Any collection [" + n + "] holds " + inner.tag);
            }
            boolean map = false;
            if (inner.contentLength() > 0) {
                map = Tag.SEQUENCE.equals(parse(inner.contentStart, inner.end).tag);
            }
            String token = canonical
                    ? (map ? CollectionWireTypes.mapToken(ANY, ANY) : CollectionWireTypes.setToken(ANY))
                    : (map ? CollectionWireTypes.orderedMapToken(ANY, ANY) : CollectionWireTypes.listToken(ANY));
            walkCollection(inner.start, inner.end, token, ic);
        }

        // ---------------------------------------------------------------------
        // MarshalledInstance
        // ---------------------------------------------------------------------

        private void walkMarshalledInstance(DerFieldStore store, Map<String, int[]> ranges, Ctx cc)
                throws DerException {
            try {
                for (String name : new String[] {"payloadBytes", "schemaBytes", "payloadFormat", "hash"}) {
                    if (!ranges.containsKey(name)) {
                        throw fail(cc, "MarshalledInstance record has no '" + name + "' field");
                    }
                }
                Object payload = store.get("payloadBytes", null);
                Object schema = store.get("schemaBytes", null);
                Object format = store.get("payloadFormat", null);
                Object hash = store.get("hash", null);
                if ((payload != null && !(payload instanceof byte[]))
                        || (schema != null && !(schema instanceof byte[]))
                        || (format != null && !(format instanceof String))
                        || !(hash instanceof Integer)) {
                    throw fail(cc, "MarshalledInstance record fields have unexpected wire types");
                }
                int hashValue = (Integer) hash;
                if (payload == null) {
                    if (hashValue != 13) {
                        throw fail(cc, "MarshalledInstance with null payload has hash " + hashValue);
                    }
                    return;
                }
                String fmt = format == null ? JOSS : (String) format;
                if (!MarshalledInstanceRecord.PAYLOAD_FORMAT.equals(fmt)) {
                    throw fail(cc, "MarshalledInstance carries a payload in format '" + fmt
                            + "'; only " + MarshalledInstanceRecord.PAYLOAD_FORMAT
                            + " payloads can be inspected");
                }
                int[] pr = ranges.get("payloadBytes");
                Tlv p = parse(pr[0], pr[1]);
                if (!Tag.OCTET_STRING.equals(p.tag)) {
                    throw fail(cc, "MarshalledInstance payloadBytes is " + p.tag);
                }
                int h = 0;
                for (int i = p.contentStart; i < p.end; i++) {
                    h = 31 * h + buf[i];
                }
                if (h != hashValue) {
                    throw fail(cc, "MarshalledInstance hash " + hashValue
                            + " does not match its payload (" + h + ")");
                }
                int limit = DerInputLimits.DEFAULT.maxMarshalledInstanceNesting();
                if (cc.miLevel >= limit) {
                    throw fail(cc, "MarshalledInstance nesting exceeds " + limit);
                }
                int[] hr = ranges.get("hash");
                MarshalledInstanceHash mih = new MarshalledInstanceHash(hr[0], hr[1] - hr[0], hashValue);
                Ctx pc = cc.push(frame(Kind.MARSHALLED_INSTANCE_PAYLOAD, cc.child(".payloadBytes"), p,
                                false, mih))
                        .child(".<payload>").depth(0).miLevel(cc.miLevel + 1);
                byte[] schemaBytes = (byte[]) schema;
                if (schemaBytes == null || schemaBytes.length == 0) {
                    walkStreamItem(p.contentStart, p.end, pc);
                } else {
                    walkHierarchy(p.contentStart, p.end, chain(pc, schemaBytes), pc,
                            new Target(Form.PAYLOAD, p.contentStart, p.contentLength(), pc.frames));
                }
            } catch (DerException e) {
                throw at(cc, e);
            }
        }

        // ---------------------------------------------------------------------
        // Record-level object-stream items
        // ---------------------------------------------------------------------

        void walkStreamItem(int s, int e, Ctx c) throws DerException {
            try {
                Tlv t = exact(s, e, c);
                if (t.tag.tagClass() != Tag.CLASS_CONTEXT) {
                    throw fail(c, "object-stream item has non-context tag " + t.tag);
                }
                int n = t.tag.tagNumber();
                boolean constructed = t.tag.isConstructed();
                if (!constructed) {
                    switch (n) {
                        case 0 -> {
                            if (t.contentLength() != 0) {
                                throw fail(c, "[0] null item has content");
                            }
                        }
                        case 2, 3, 4, 5, 6, 10, 11, 12, 13, 14 -> {
                            // A primitive scalar item has no nested structure.
                        }
                        default -> throw fail(c, "unexpected object-stream item " + t.tag);
                    }
                    return;
                }
                switch (n) {
                    case 1 -> walkStreamAtomic(t, c);
                    case 7 -> enumLeaf(t);
                    case 8 -> walkStreamProxy(t, c);
                    case 9, 16 -> throw fail(c, "object-stream item " + t.tag
                            + " (array or collection) is not walked by EndpointLocator");
                    default -> throw fail(c, "unexpected object-stream item " + t.tag);
                }
            } catch (DerException ex) {
                throw at(c, ex);
            }
        }

        /** {@code [1]}: a {@code MarshalledInstanceRecord SEQUENCE}. */
        private void walkStreamAtomic(Tlv t, Ctx c) throws DerException {
            Tlv rec = exact(t.contentStart, t.end, c);
            if (!Tag.SEQUENCE.equals(rec.tag)) {
                throw fail(c, "[1] content is " + rec.tag + ", not SEQUENCE");
            }
            DerReader r = new DerReader(buf, rec.contentStart, rec.end);
            int ps = r.position();
            Tlv p = parse(ps, r.skipTlv());
            if (!Tag.OCTET_STRING.equals(p.tag)) {
                throw fail(c, "[1] record payload is " + p.tag);
            }
            byte[] schema = r.readOctetString();
            byte[] digest = r.readOctetString();
            if (!r.hasMore()) {
                throw fail(c, "[1] record has no payloadFormat");
            }
            String format = r.readUtf8String();
            if (r.hasMore()) {
                throw fail(c, "[1] record has trailing content");
            }
            if (!MarshalledInstanceRecord.PAYLOAD_FORMAT.equals(format)) {
                throw fail(c, "[1] record payloadFormat '" + format + "' is not "
                        + MarshalledInstanceRecord.PAYLOAD_FORMAT);
            }
            List<AtomicSerialSchemaRecord> records = chain(c, schema);
            if (!Arrays.equals(records.get(0).schemaDigest(), digest)) {
                throw fail(c, "[1] record schemaDigest does not match its leaf schema record");
            }
            Ctx sc = c.push(frame(Kind.STREAM_ATOMIC, c, t))
                    .push(frame(Kind.STREAM_RECORD, c, rec))
                    .push(frame(Kind.STREAM_RECORD_PAYLOAD, c, p))
                    .depth(0);
            walkHierarchy(p.contentStart, p.end, records, sc,
                    new Target(Form.STREAM_ITEM, t.start, t.end - t.start, c.frames));
        }

        /** Object-stream {@code [8]}: INTEGER count, names, then the handler as a {@code [1]} item. */
        private void walkStreamProxy(Tlv t, Ctx c) throws DerException {
            DerReader pr = new DerReader(buf, t.contentStart, t.end);
            proxyNames(pr, c);
            if (!pr.hasMore()) {
                throw fail(c, "[8] proxy has no handler");
            }
            int hs = pr.position();
            int he = pr.skipTlv();
            if (pr.hasMore()) {
                throw fail(c, "trailing bytes after the handler in [8] proxy");
            }
            Tlv h = parse(hs, he);
            if (!CTX_ATOMIC.equals(h.tag)) {
                throw fail(c, "[8] proxy handler is " + h.tag + ", not a [1] record");
            }
            walkStreamAtomic(h, c.push(frame(Kind.PROXY, c, t)).child(".<handler>"));
        }

        // ---------------------------------------------------------------------
        // Findings
        // ---------------------------------------------------------------------

        private EndpointFinding finding(EndpointLocatorConfig.EndpointClass spec,
                                        List<AtomicSerialSchemaRecord> leafFirst,
                                        List<ClassPart> rootFirst, Ctx c, Target target)
                throws DerException {
            Map<String, Integer> nameCount = new HashMap<>();
            for (ClassPart part : rootFirst) {
                for (AtomicSerialFieldDef def : part.schema.fields()) {
                    nameCount.merge(def.wireName(), 1, Integer::sum);
                }
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            for (ClassPart part : rootFirst) {
                DerFieldStore store = part.store;
                for (String name : store.presentFieldNames()) {
                    if (store.isNested(name) || store.isCollection(name) || store.isNestedArray(name)) {
                        continue;
                    }
                    String key = nameCount.get(name) > 1 ? part.schema.className() + "#" + name : name;
                    fields.put(key, store.get(name, null));
                }
            }
            String host = null;
            if (spec.hostField() != null) {
                Object v = valueOf(spec.hostField(), rootFirst, c);
                if (v != null && !(v instanceof String)) {
                    throw fail(c, "host field '" + spec.hostField() + "' is not a String");
                }
                host = (String) v;
            }
            Integer port = null;
            if (spec.portField() != null) {
                Object v = valueOf(spec.portField(), rootFirst, c);
                if (!(v instanceof Integer)) {
                    throw fail(c, "port field '" + spec.portField() + "' is not an int");
                }
                port = (Integer) v;
            }
            SocketFactory sf = null;
            if (spec.socketFactoryField() != null) {
                String name = spec.socketFactoryField();
                ClassPart part = partOf(name, rootFirst, c);
                String wireType = null;
                for (AtomicSerialFieldDef def : part.schema.fields()) {
                    if (def.wireName().equals(name)) {
                        wireType = def.wireType();
                    }
                }
                if (!NESTED.equals(wireType)) {
                    throw fail(c, "socket factory field '" + name + "' has wire type '" + wireType
                            + "', not '" + NESTED + "'");
                }
                int[] r = part.ranges.get(name);
                sf = describeSlot(r[0], r[1], c);
                if (sf != null && !config.socketFactoryPolicy().test(sf)) {
                    throw fail(c.child("." + name), "socket factory " + sf.kind() + " "
                            + sf.classNames() + " is not accepted by the socket factory policy");
                }
            }
            List<String> chainNames = new ArrayList<>(leafFirst.size());
            for (AtomicSerialSchemaRecord r : leafFirst) {
                chainNames.add(r.className());
            }
            Location loc = new Location(target.form, target.offset, target.length, target.frames);
            return new EndpointFinding(c.path, leafFirst.get(0).className(), chainNames, fields,
                    host, port, sf, loc);
        }

        private ClassPart partOf(String name, List<ClassPart> rootFirst, Ctx c) throws DerException {
            for (int i = rootFirst.size() - 1; i >= 0; i--) {
                if (rootFirst.get(i).ranges.containsKey(name)) {
                    return rootFirst.get(i);
                }
            }
            throw fail(c, "recognised endpoint record has no field '" + name + "'");
        }

        private Object valueOf(String name, List<ClassPart> rootFirst, Ctx c) throws DerException {
            return partOf(name, rootFirst, c).store.get(name, null);
        }

        private SocketFactory describeSlot(int s, int e, Ctx c) throws DerException {
            Tlv t = exact(s, e, c);
            if (NULL.equals(t.tag)) {
                return null;
            }
            if (CTX_ENUM.equals(t.tag)) {
                String[] parts = enumLeaf(t);
                return new SocketFactory(SocketFactory.Kind.ENUM, List.of(parts[0]), parts[1]);
            }
            DerReader r = new DerReader(buf, t.contentStart, t.end);
            if (CTX_PROXY.equals(t.tag)) {
                return new SocketFactory(SocketFactory.Kind.PROXY, proxyNames(r, c), null);
            }
            List<String> names = new ArrayList<>();
            for (AtomicSerialSchemaRecord rec : chain(c, r.readOctetString())) {
                names.add(rec.className());
            }
            return new SocketFactory(SocketFactory.Kind.RECORD, names, null);
        }
    }
}
