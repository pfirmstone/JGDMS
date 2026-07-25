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

package au.net.zeus.jgdms.der.entry;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceCodec;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.export.ProxyAccessor;
import org.apache.river.api.io.AtomicSerial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Codec for the Outrigger {@code EntryRepV2Body} whole-entry DER record
 * (JGDMS-STD-006 EntryRep-v2 amendment).
 *
 * <pre>
 * EntryRepV2Body ::= SEQUENCE {
 *   version           INTEGER (2),
 *   entrySchemaDigest OCTET STRING (SIZE(32)),
 *   schemaTable       SEQUENCE (SIZE(0..maxSchemaTable)) OF SchemaEntry,  -- ASCENDING by digest, no dups
 *   fields            SEQUENCE (SIZE(0..maxFields)) OF FieldSlice }        -- FieldComparator order
 * SchemaEntry ::= SEQUENCE { digest OCTET STRING (SIZE(32)), chainBytes OCTET STRING }
 * FieldSlice  ::= CHOICE {
 *   absent [0] IMPLICIT NULL,
 *   value  [1] IMPLICIT SEQUENCE { valueSchemaDigest OCTET STRING (SIZE(0..32)),
 *                                   payload OCTET STRING } }
 * </pre>
 *
 * <h2>The match contract (amendment &sect;A.4)</h2>
 * <p>{@link #encodeFieldSlice(Object)} is a <b>pure function of the field value
 * alone</b>: the returned slice bytes depend on nothing about the enclosing entry, its
 * schema chain, the {@code schemaTable}, the other fields, or template-vs-stored role.
 * The same value therefore yields byte-identical slice bytes in every context, so
 * positional slice-byte comparison ({@code EntryRep.matches}) preserves v1 semantics.
 * The {@code schemaTable} and {@code entrySchemaDigest} live OUTSIDE the compared
 * region.
 *
 * <p>A slice's {@code (valueSchemaDigest, payload)} is byte-identical to a per-value
 * DER {@link MarshalledInstanceRecord}'s {@code (schemaDigest, payloadBytes)} &mdash; the
 * exact bytes v1's {@code matches()} compared &mdash; so matching parity holds by
 * construction (&sect;A.4.1).
 */
public final class EntryRepV2Codec {

    private EntryRepV2Codec() {
        throw new AssertionError("no instances");
    }

    // =========================================================================
    // Ceilings (amendment &sect;A.6; PROPOSED values pending ratification)
    // =========================================================================

    /** {@code EntryRepV2Body.version}: the only accepted value. */
    public static final int VERSION = 2;
    /** {@code maxSchemaTable}: max distinct schema chains per entry (inclusive). */
    public static final int MAX_SCHEMA_TABLE = 256;
    /** {@code maxFields}: STD-006 &sect;4.5, inclusive. */
    public static final int MAX_FIELDS = 65535;
    /** {@code maxSlicePayload}: max bytes of one {@code FieldSlice.value.payload} (1 MiB, inclusive). */
    public static final int MAX_SLICE_PAYLOAD = 1 << 20;
    /** {@code maxBodyBytes}: max total {@code EntryRepV2Body} DER length (8 MiB, inclusive). */
    public static final int MAX_BODY_BYTES = 8 << 20;

    private static final int DIGEST_LEN = 32;
    private static final Tag SLICE_ABSENT = new Tag(Tag.CLASS_CONTEXT, false, 0); // [0] IMPLICIT NULL
    private static final Tag SLICE_VALUE  = new Tag(Tag.CLASS_CONTEXT, true, 1);  // [1] IMPLICIT SEQUENCE
    private static final byte[] EMPTY = new byte[0];

    // =========================================================================
    // Encode
    // =========================================================================

    /**
     * The per-value slice product: the canonical slice bytes plus, when the value has an
     * {@code @AtomicSerial} class in its hierarchy, the value's own schema chain
     * (contributed to the {@code schemaTable}).
     *
     * @param sliceBytes        the DER {@code FieldSlice} bytes (context-free; the matched unit)
     * @param valueSchemaDigest 0-length for a self-describing value, else the 32-byte
     *                          runtime-class-chain leaf digest
     * @param chainBytes        {@code null} for a self-describing value, else the value's
     *                          leaf-first chain bytes ({@code SchemaEntry.chainBytes})
     */
    public record Slice(byte[] sliceBytes, byte[] valueSchemaDigest, byte[] chainBytes) {}

    /** The encoded body plus the per-field slice bytes (cached for matching) and the entry digest. */
    public record EncodedBody(byte[] body, byte[][] sliceBytes, byte[] entrySchemaDigest) {}

    /**
     * Encodes one field value as a canonical {@code FieldSlice} &mdash; the <b>crux</b>
     * pure function (&sect;A.4.1). {@code null} yields the single {@code absent} marker
     * (entry-null field or template wildcard); a non-null value yields
     * {@code value [1] { valueSchemaDigest, payload }} where both parts depend only on
     * the value.
     *
     * @param value the field value, or {@code null} for null/wildcard
     * @return the {@link Slice}
     * @throws IOException if DER encoding of the value fails (non-DER-encodable value)
     */
    public static Slice encodeFieldSlice(Object value) throws IOException {
        if (value == null) {
            return new Slice(DerWriter.writeTlv(SLICE_ABSENT, EMPTY), EMPTY, null);
        }
        // F1 (board ruling: REJECT, do not substitute). A live downloadable/smart proxy value
        // would encode SILENTLY as a bare [8] object-stream item (deterministic empty digest),
        // which DIVERGES from v1's DerProxySerializer substitution in deployment. Substitution
        // cannot be added here because substituted bytes depend on streamLoader/context, not on
        // the value alone -- it would breach the pure-function contract (amendment A.4.1). So a
        // proxy-typed field value is refused LOUDLY at encode.
        rejectProxyValue(value);
        byte[] digest;
        byte[] payload;
        byte[] chainBytes;
        try {
            if (nearestAtomicSerial(value.getClass()) != null) {
                // @AtomicSerial graph: schema-separated form (byte-identical to a DER
                // MarshalledInstance's schemaDigest/payloadBytes).
                SchemaChain.Result chain = SchemaGenerator.generateChain(value.getClass());
                payload = ObjectCodec.encodeHierarchy(value, chain);
                digest = chain.leafDigest();
                chainBytes = EntrySchemaGenerator.concatChain(chain.chain());
            } else {
                // No @AtomicSerial class (String/boxed/byte[]/enum/collection/bare proxy):
                // self-describing object-stream item; empty schema digest.
                payload = encodeObjectStreamItem(value);
                digest = EMPTY;
                chainBytes = null;
            }
        } catch (DerException e) {
            throw new IOException("EntryRepV2: cannot DER-encode field value of type "
                    + value.getClass().getName() + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // D6: the object-stream (inc-1) layer signals some non-encodable values with an
            // unchecked exception (e.g. UnsupportedOperationException for a bare top-level
            // collection value -- see amendment A.4.2 disclosure). Wrap it as the checked
            // IOException this method contracts, so a caller (EntryRep) can surface it as a
            // MarshalException rather than have it escape unchecked.
            throw new IOException("EntryRepV2: cannot DER-encode field value of type "
                    + value.getClass().getName() + ": " + e, e);
        }
        if (payload.length > MAX_SLICE_PAYLOAD) {
            throw new IOException("EntryRepV2: field payload " + payload.length
                    + " exceeds maxSlicePayload (" + MAX_SLICE_PAYLOAD + ")");
        }
        List<byte[]> inner = new ArrayList<>(2);
        inner.add(DerWriter.writeOctetString(digest));
        inner.add(DerWriter.writeOctetString(payload));
        byte[] sliceBytes = DerWriter.writeTlv(SLICE_VALUE, concat(inner));
        return new Slice(sliceBytes, digest, chainBytes);
    }

    /**
     * Encodes an {@code EntryRepV2Body} from the entry's schema (its chain +
     * {@code entrySchemaDigest}) and the ordered field values (in {@code FieldComparator}
     * order &mdash; the same order as {@code EntrySchemaGenerator.forClass(..).orderedFields()}).
     *
     * @param entrySchemaDigest the 32-byte {@code entrySchemaDigest}
     * @param entryChainBytes   the entry's leaf-first chain bytes
     * @param fieldValues       the ordered field values ({@code null} = null/wildcard)
     * @return the encoded body plus the per-field slice bytes
     * @throws IOException if a value cannot be encoded or a ceiling is exceeded
     */
    public static EncodedBody encode(byte[] entrySchemaDigest, byte[] entryChainBytes,
                                     Object[] fieldValues) throws IOException {
        Objects.requireNonNull(entrySchemaDigest, "entrySchemaDigest");
        Objects.requireNonNull(entryChainBytes, "entryChainBytes");
        Objects.requireNonNull(fieldValues, "fieldValues");
        if (entrySchemaDigest.length != DIGEST_LEN) {
            throw new IOException("EntryRepV2: entrySchemaDigest must be 32 bytes");
        }
        if (fieldValues.length > MAX_FIELDS) {
            throw new IOException("EntryRepV2: field count " + fieldValues.length
                    + " exceeds maxFields (" + MAX_FIELDS + ")");
        }

        // schemaTable, keyed by digest, ordered ascending, deduped. The entry chain is
        // always present. Each referenced value chain is added once.
        TreeMap<byte[], byte[]> table = new TreeMap<>(Arrays::compareUnsigned);
        table.put(entrySchemaDigest.clone(), entryChainBytes.clone());

        byte[][] slices = new byte[fieldValues.length][];
        List<byte[]> sliceTlvs = new ArrayList<>(fieldValues.length);
        for (int i = 0; i < fieldValues.length; i++) {
            Slice s = encodeFieldSlice(fieldValues[i]);
            slices[i] = s.sliceBytes();
            sliceTlvs.add(s.sliceBytes());
            if (s.chainBytes() != null) {
                // dedup by content digest; a duplicate class contributes no extra bytes.
                table.putIfAbsent(s.valueSchemaDigest().clone(), s.chainBytes());
            }
        }
        if (table.size() > MAX_SCHEMA_TABLE) {
            throw new IOException("EntryRepV2: schemaTable size " + table.size()
                    + " exceeds maxSchemaTable (" + MAX_SCHEMA_TABLE + ")");
        }

        // Encode schemaTable (already ascending via TreeMap ordering).
        List<byte[]> tableTlvs = new ArrayList<>(table.size());
        for (Map.Entry<byte[], byte[]> e : table.entrySet()) {
            List<byte[]> se = new ArrayList<>(2);
            se.add(DerWriter.writeOctetString(e.getKey()));
            se.add(DerWriter.writeOctetString(e.getValue()));
            tableTlvs.add(DerWriter.writeSequence(se));
        }

        List<byte[]> outer = new ArrayList<>(4);
        outer.add(DerWriter.writeInteger(VERSION));
        outer.add(DerWriter.writeOctetString(entrySchemaDigest));
        outer.add(DerWriter.writeSequence(tableTlvs));
        outer.add(DerWriter.writeSequence(sliceTlvs));
        byte[] body = DerWriter.writeSequence(outer);
        if (body.length > MAX_BODY_BYTES) {
            throw new IOException("EntryRepV2: body length " + body.length
                    + " exceeds maxBodyBytes (" + MAX_BODY_BYTES + ")");
        }
        return new EncodedBody(body, slices, entrySchemaDigest.clone());
    }

    /**
     * Convenience: encode an ordinary reflective Entry instance. Reads field values in
     * the single {@link EntrySchemaGenerator} field order.
     *
     * @param entryClass    the entry class
     * @param entryInstance the entry instance (its runtime class must be {@code entryClass})
     * @return the encoded body
     * @throws IOException if reflection or encoding fails
     */
    public static EncodedBody encodeReflective(Class<?> entryClass, Object entryInstance)
            throws IOException {
        try {
            EntrySchemaGenerator.EntrySchema schema = EntrySchemaGenerator.forClass(entryClass);
            List<java.lang.reflect.Field> fields = schema.orderedFields();
            Object[] values = new Object[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                values[i] = fields.get(i).get(entryInstance);
            }
            return encode(schema.entrySchemaDigest(), schema.chainBytes(), values);
        } catch (DerException | IllegalAccessException e) {
            throw new IOException("EntryRepV2: cannot encode entry " + entryClass.getName()
                    + ": " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Decode (fail-closed; amendment &sect;A.9)
    // =========================================================================

    /** The decoded, fully-validated body: the parts a receiver needs to match, index, and reconstruct. */
    public record DecodedBody(byte[] entrySchemaDigest,
                              byte[][] sliceBytes,      // per-field raw slice TLV bytes (the matched unit)
                              boolean[] absent,         // per-field: true for an [0] absent/wildcard slice
                              Map<String, byte[]> schemaTable) { // hex(digest) -> chainBytes
    }

    /**
     * Decodes and fully validates an {@code EntryRepV2Body}. Every check in amendment
     * &sect;A.9 is enforced fail-closed before any part is returned.
     *
     * @param body the DER body bytes
     * @return the validated {@link DecodedBody}
     * @throws DerException if the body is malformed, a ceiling is breached, the version
     *                      is not 2, the schemaTable is unsorted/duplicated/incomplete, a
     *                      digest/chain mismatch is found, or trailing bytes are present
     */
    public static DecodedBody decode(byte[] body) throws DerException {
        Objects.requireNonNull(body, "body");
        if (body.length > MAX_BODY_BYTES) {
            throw new DerException("EntryRepV2: body length " + body.length
                    + " exceeds maxBodyBytes (" + MAX_BODY_BYTES + ")");
        }
        DerReader outer = new DerReader(body);
        DerReader seq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException("EntryRepV2: trailing bytes after body SEQUENCE");
        }

        // 1. version INTEGER == 2 (loud flag-day guard)
        BigInteger version = seq.readInteger();
        if (!version.equals(BigInteger.valueOf(VERSION))) {
            throw new DerException("EntryRepV2: unsupported version " + version
                    + " (expected " + VERSION + ") -- refusing non-v2 record LOUDLY");
        }

        // 2. entrySchemaDigest OCTET STRING (32)
        byte[] entrySchemaDigest = seq.readOctetString();
        if (entrySchemaDigest.length != DIGEST_LEN) {
            throw new DerException("EntryRepV2: entrySchemaDigest must be 32 bytes, got "
                    + entrySchemaDigest.length);
        }

        // 3. schemaTable SEQUENCE OF SchemaEntry -- ASCENDING by digest, no dups, digest binds chain
        DerReader tableSeq = seq.readSequence();
        Map<String, byte[]> table = new LinkedHashMap<>();
        byte[] prevDigest = null;
        int tableCount = 0;
        while (tableSeq.hasMore()) {
            tableCount++;
            if (tableCount > MAX_SCHEMA_TABLE) {
                throw new DerException("EntryRepV2: schemaTable exceeds maxSchemaTable ("
                        + MAX_SCHEMA_TABLE + ")");
            }
            DerReader entry = tableSeq.readSequence();
            byte[] digest = entry.readOctetString();
            if (digest.length != DIGEST_LEN) {
                throw new DerException("EntryRepV2: SchemaEntry.digest must be 32 bytes, got "
                        + digest.length);
            }
            byte[] chainBytes = entry.readOctetString();
            if (entry.hasMore()) {
                throw new DerException("EntryRepV2: trailing bytes in SchemaEntry");
            }
            if (prevDigest != null && Arrays.compareUnsigned(prevDigest, digest) >= 0) {
                throw new DerException("EntryRepV2: schemaTable not strictly ascending by digest"
                        + " (unsorted or duplicate) -- rejected");
            }
            prevDigest = digest;
            // digest binds chainBytes: verify chain decodes (S7.8) and its leaf digest matches.
            List<au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord> chain =
                    SchemaChain.decodeChain(chainBytes, "EntryRepV2.SchemaEntry");
            byte[] leafDigest = chain.get(0).schemaDigest();
            if (!Arrays.equals(leafDigest, digest)) {
                throw new DerException("EntryRepV2: SchemaEntry.digest does not match the leaf"
                        + " of chainBytes for class '" + chain.get(0).className() + "' -- rejected");
            }
            table.put(hex(digest), chainBytes);
        }
        if (!table.containsKey(hex(entrySchemaDigest))) {
            throw new DerException("EntryRepV2: entrySchemaDigest absent from schemaTable"
                    + " (completeness) -- rejected");
        }

        // 4. fields SEQUENCE OF FieldSlice
        DerReader fieldsSeq = seq.readSequence();
        List<byte[]> sliceList = new ArrayList<>();
        List<Boolean> absentList = new ArrayList<>();
        java.util.Set<String> referenced = new java.util.HashSet<>();
        referenced.add(hex(entrySchemaDigest));
        while (fieldsSeq.hasMore()) {
            if (sliceList.size() >= MAX_FIELDS) {
                throw new DerException("EntryRepV2: field count exceeds maxFields (" + MAX_FIELDS + ")");
            }
            int start = fieldsSeq.position();
            Tag tag = fieldsSeq.peekTag();
            if (SLICE_ABSENT.equals(tag)) {
                DerReader.TlvHeader h = fieldsSeq.readTlvHeader();
                if (h.contentLength() != 0) {
                    throw new DerException("EntryRepV2: absent [0] slice must be empty");
                }
                sliceList.add(fieldsSeq.slice(start, fieldsSeq.position()));
                absentList.add(Boolean.TRUE);
            } else if (SLICE_VALUE.equals(tag)) {
                DerReader.TlvHeader h = fieldsSeq.readTlvHeader();
                int innerEnd = fieldsSeq.position() + h.contentLength();
                byte[] digest = fieldsSeq.readOctetString();
                if (digest.length != 0 && digest.length != DIGEST_LEN) {
                    throw new DerException("EntryRepV2: valueSchemaDigest must be 0 or 32 bytes, got "
                            + digest.length);
                }
                byte[] payload = fieldsSeq.readOctetString();
                if (payload.length > MAX_SLICE_PAYLOAD) {
                    throw new DerException("EntryRepV2: field payload " + payload.length
                            + " exceeds maxSlicePayload (" + MAX_SLICE_PAYLOAD + ")");
                }
                if (fieldsSeq.position() != innerEnd) {
                    throw new DerException("EntryRepV2: trailing/short content in value [1] slice");
                }
                if (digest.length == DIGEST_LEN) {
                    if (!table.containsKey(hex(digest))) {
                        throw new DerException("EntryRepV2: valueSchemaDigest absent from schemaTable"
                                + " (completeness) -- rejected");
                    }
                    referenced.add(hex(digest));
                }
                sliceList.add(fieldsSeq.slice(start, fieldsSeq.position()));
                absentList.add(Boolean.FALSE);
            } else {
                throw new DerException("EntryRepV2: FieldSlice tag must be [0] or [1], got " + tag);
            }
        }
        if (seq.hasMore()) {
            throw new DerException("EntryRepV2: trailing bytes after fields SEQUENCE");
        }

        // 5. no orphan schemaTable entries (every table digest is entry or slice referenced)
        for (String d : table.keySet()) {
            if (!referenced.contains(d)) {
                throw new DerException("EntryRepV2: orphan schemaTable entry (digest referenced by"
                        + " nothing) -- rejected");
            }
        }

        // 6. FIELD-COUNT GUARD (amendment A.8/A.9 D1). entrySchemaDigest is EXCLUDED from
        //    matching (A.4.6), so this structural check is the ONLY defence in the decoded body
        //    against a positional / class-confusion shift: the number of slices MUST equal the
        //    total usable-field count implied by the entry's own on-wire schema chain. A body
        //    with too few/many slices for its declared class is rejected LOUDLY.
        byte[] entryChainBytes = table.get(hex(entrySchemaDigest));
        // (entryChainBytes is non-null: completeness above proved entrySchemaDigest is in the table.)
        int declaredFieldCount = 0;
        for (AtomicSerialSchemaRecord rec :
                SchemaChain.decodeChain(entryChainBytes, "EntryRepV2.entryChain")) {
            declaredFieldCount += rec.fields().size();
        }
        if (declaredFieldCount != sliceList.size()) {
            throw new DerException("EntryRepV2: field-count skew -- entry schema implies "
                    + declaredFieldCount + " usable field(s) but the body carries "
                    + sliceList.size() + " slice(s) -- rejected (A.8 field-count guard)");
        }

        byte[][] slices = sliceList.toArray(new byte[0][]);
        boolean[] absent = new boolean[absentList.size()];
        for (int i = 0; i < absent.length; i++) absent[i] = absentList.get(i);
        return new DecodedBody(entrySchemaDigest, slices, absent, table);
    }

    // =========================================================================
    // Value reconstruction (for EntryRep.entry())
    // =========================================================================

    /**
     * Reconstructs one field value from a decoded slice.
     *
     * @param sliceBytes   the raw slice TLV bytes (from {@link DecodedBody#sliceBytes()})
     * @param receiverType the expected field type (assignability check)
     * @param schemaTable  the decoded body's schema table (hex(digest) -&gt; chainBytes)
     * @return the decoded value, or {@code null} for an absent slice
     * @throws IOException            if decoding fails
     * @throws ClassNotFoundException if a class named in the schema cannot be loaded
     */
    public static Object decodeFieldValue(byte[] sliceBytes, Class<?> receiverType,
                                          Map<String, byte[]> schemaTable)
            throws IOException, ClassNotFoundException {
        try {
            DerReader r = new DerReader(sliceBytes);
            Tag tag = r.peekTag();
            if (SLICE_ABSENT.equals(tag)) {
                return null;
            }
            if (!SLICE_VALUE.equals(tag)) {
                throw new DerException("EntryRepV2: slice tag must be [0]/[1]");
            }
            DerReader.TlvHeader h = r.readTlvHeader();
            byte[] digest = r.readOctetString();
            byte[] payload = r.readOctetString();
            if (digest.length == 0) {
                // self-describing object-stream item
                DerMarshalInputStream in = DerMarshalInputStream.recordLevelCapture(
                        new ByteArrayInputStream(payload), ResolutionContext.NONE);
                Object o = in.readObject(Object.class);
                if (o != null && receiverType != null && !receiverType.isInstance(o)) {
                    throw new DerException("EntryRepV2: decoded value type "
                            + o.getClass().getName() + " not assignable to " + receiverType.getName());
                }
                return o;
            }
            // @AtomicSerial value: reconstruct a MarshalledInstanceRecord and decode via the
            // embedded chain from the schemaTable.
            byte[] chainBytes = schemaTable.get(hex(digest));
            if (chainBytes == null) {
                throw new DerException("EntryRepV2: valueSchemaDigest not in schemaTable");
            }
            MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                    payload, chainBytes, digest, MarshalledInstanceRecord.PAYLOAD_FORMAT);
            Class<?> rt = receiverType == null ? Object.class : receiverType;
            return MarshalledInstanceCodec.decodeMarshalledInstance(rec, rt).object();
        } catch (DerException e) {
            throw new IOException("EntryRepV2: cannot decode field value: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * F1: refuses a live proxy/downloadable-service value held as an entry field. Detects the
     * {@code ProxyAccessor}/{@code DynamicProxyCodebaseAccessor} carriers, and a dynamic proxy
     * that is both {@code RemoteMethodControl} and {@code CodebaseAccessor} (a smart proxy). Such
     * a value would encode non-deterministically vs its deployment (streamLoader/context
     * dependent), so it cannot be a pure function of the value -- refuse LOUDLY.
     */
    private static void rejectProxyValue(Object value) throws IOException {
        boolean proxyish = (value instanceof ProxyAccessor)
                || (value instanceof DynamicProxyCodebaseAccessor)
                || (java.lang.reflect.Proxy.isProxyClass(value.getClass())
                        && value instanceof RemoteMethodControl
                        && value instanceof CodebaseAccessor);
        if (proxyish) {
            throw new IOException("EntryRepV2: a live proxy/downloadable-service value of type "
                    + value.getClass().getName() + " may not be stored as an entry field"
                    + " (its encoding is not a pure function of the value -- F1). Store a data"
                    + " value, not a proxy.");
        }
    }

    /** The nearest class in {@code c}'s hierarchy annotated {@code @AtomicSerial}, or null. */
    private static Class<?> nearestAtomicSerial(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            if (k.isAnnotationPresent(AtomicSerial.class)) return k;
        }
        return null;
    }

    /** Encodes {@code obj} as a self-describing DER object-stream item (record-level capture). */
    private static byte[] encodeObjectStreamItem(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DerMarshalOutputStream dmos = DerMarshalOutputStream.recordLevelCapture(bos);
        dmos.writeObject(obj);
        dmos.flush();
        return bos.toByteArray();
    }

    private static byte[] concat(List<byte[]> parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static String hex(byte[] b) {
        char[] c = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            c[i * 2] = HEX[(b[i] >> 4) & 0xF];
            c[i * 2 + 1] = HEX[b[i] & 0xF];
        }
        return new String(c);
    }
}
