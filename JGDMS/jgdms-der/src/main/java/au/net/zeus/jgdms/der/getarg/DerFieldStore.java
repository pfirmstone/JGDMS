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

package au.net.zeus.jgdms.der.getarg;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Complete field store for one {@code @AtomicSerial} class's private SEQUENCE,
 * per JGDMS-STD-006 S3.9 and S11.8 (Phase 3).
 *
 * <h2>Design contract</h2>
 *
 * <p>A {@code DerFieldStore} is constructed from:
 * <ol>
 *   <li>An explicit {@link AtomicSerialSchemaRecord} describing one class's
 *       namespace (the at-marshal-time schema; always the schema passed in -- never
 *       any ambient or global schema).</li>
 *   <li>The DER bytes (or a {@link DerReader} positioned at) the outer SEQUENCE
 *       TLV for that class's private namespace.</li>
 * </ol>
 *
 * <p>On construction the store decodes <em>every</em> field into an ordered
 * {@code name -> value} map before any {@code get()} is called. {@code get()} is
 * therefore a pure <em>selection</em> operation over an already-populated map;
 * it never triggers decoding. This is the complete-field-store / get-is-selection
 * semantics required by S3.9.
 *
 * <h2>Three decoding cases (S3.9)</h2>
 *
 * <p>Field-to-TLV matching is positional: {@code schema.fields().get(i)} maps to
 * the i-th child TLV in the payload SEQUENCE. Three runtime outcomes are possible:
 *
 * <dl>
 *   <dt><b>(a) Exact match</b></dt>
 *   <dd>The payload SEQUENCE contains exactly as many TLVs as the schema has
 *   fields. Every schema field is decoded and stored. No bytes are discarded.
 *   {@link #trailingFieldsDiscarded()} returns 0; all fields are present.</dd>
 *
 *   <dt><b>(b) Payload shorter than schema (forward compatibility -- old data, new schema)</b></dt>
 *   <dd>The payload SEQUENCE boundary is reached before the schema field list is
 *   exhausted. Fields already decoded are stored; remaining schema fields are
 *   <em>absent</em> from the store. {@code get(name, default)} returns
 *   {@code default} for absent fields; {@link #defaulted(String)} returns
 *   {@code true}.</dd>
 *
 *   <dt><b>(c) Payload longer than schema (backward compatibility -- new data, old schema)</b></dt>
 *   <dd>The schema field list is exhausted before the payload SEQUENCE boundary.
 *   The known fields are decoded positionally. The trailing extra TLVs are
 *   read-and-discarded using {@link DerReader#readTlvHeader()} +
 *   {@link DerReader#readRawContent(int)}. {@link #trailingFieldsDiscarded()}
 *   returns the count of discarded trailing TLVs. No error occurs.</dd>
 * </dl>
 *
 * <p>Cases (b) and (c) are expected operational modes, not errors. The model is
 * <em>symmetric</em> (S11.8): absent -> default; extra -> read-and-discarded.
 *
 * <h2>Namespace invariant (S3.9, S3.10)</h2>
 *
 * <p>Each {@code @AtomicSerial} class owns one private SEQUENCE. A single
 * {@code DerFieldStore} instance covers exactly one class's namespace (one
 * schema record, one SEQUENCE). Field names are scoped to that namespace;
 * a field {@code "x"} in one class is entirely independent of a field {@code "x"}
 * in another class.
 *
 * <h2>Lifetime and GC semantics (S3.9, S11.8)</h2>
 *
 * <p>Every decoded value lives in the store's map until the store is
 * dereferenced. A field decoded but never requested via {@code get()} stays in
 * the map -- it is NOT removed on access -- and becomes GC-eligible only when the
 * entire store goes out of scope. This matches the GetArg lifetime boundary
 * described in S3.9: "the construction chain is the lifetime boundary for all
 * decoded field values."
 *
 * <h2>Delegation target for real GetArg</h2>
 *
 * <p>The {@code get(name, default)} surface is designed to be cleanly delegated
 * to from a real {@code AtomicSerial.GetArg} subclass (a later phase). All
 * typed {@code get} overloads and {@link #defaulted(String)} mirror the
 * {@code ObjectInputStream.GetField} contract.
 */
public final class DerFieldStore {

    /** Sentinel used internally to mark an absent field (not a decoded null). */
    private static final Object ABSENT = new Object();

    /**
     * Wrapper stored for a nested {@code @AtomicSerial} field (wireType
     * {@code "@AtomicSerial"}). Holds the raw TLV bytes of the nested record
     * (a SEQUENCE or NULL). The actual decoding is deferred to
     * {@code DerGetArg.get(name, default)} (which lives in {@code der.object}
     * and can call {@code ObjectCodec.decodeNested}) to keep {@code der.getarg}
     * free of any dependency on {@code der.object}.
     */
    record NestedRaw(byte[] rawBytes) {
        NestedRaw {
            rawBytes = rawBytes.clone(); // defensive copy
        }
    }

    /**
     * Wrapper stored for a nested {@code @AtomicSerial[]} array field (wireType
     * {@code "array:@AtomicSerial:<componentClass>"}). Holds the raw TLV bytes of
     * the outer DER NULL or SEQUENCE (each element is itself a nested record or NULL).
     *
     * <p>The actual decoding is deferred to {@code DerGetArg.get(name, default)}
     * (in {@code der.object}) which calls {@code ObjectCodec.decodeNestedArray},
     * threading the cumulative depth guard. This mirrors the {@link NestedRaw}
     * pattern for the single-element case and preserves the no-cycle invariant:
     * {@code der.getarg} imports nothing from {@code der.object}.
     *
     * @param rawBytes           raw TLV bytes of the array SEQUENCE or DER NULL
     * @param componentClassName fully-qualified name of the component class,
     *                           extracted from the wireType suffix after the last ':'
     */
    record NestedArrayRaw(byte[] rawBytes, String componentClassName) {
        NestedArrayRaw {
            rawBytes = rawBytes.clone(); // defensive copy
        }
    }

    /**
     * Wrapper stored for a {@code Collection}/{@code Map} field (wireType one of
     * {@code set:}/{@code orderedset:}/{@code list:}/{@code map:}/{@code orderedmap:};
     * STD-006 §3.8). Holds the raw TLV bytes of the outer DER NULL or SEQUENCE and the
     * full collection wire-type token (so the decoder knows the element/key/value
     * wire-types and the target collection kind).
     *
     * <p>Like {@link NestedArrayRaw}, decoding is deferred to
     * {@code DerGetArg.get(name, default)} (in {@code der.object}) which calls
     * {@code ObjectCodec.decodeCollection}, threading the cumulative depth guard and
     * keeping {@code der.getarg} free of any {@code der.object} dependency (no cycle).
     *
     * @param rawBytes raw TLV bytes of the collection SEQUENCE or DER NULL
     * @param wireType the full collection wire-type token
     */
    record CollectionRaw(byte[] rawBytes, String wireType) {
        CollectionRaw {
            rawBytes = rawBytes.clone(); // defensive copy
        }
    }

    /**
     * The schema record this store was built with. This is always the schema
     * passed in at construction time (the at-marshal-time schema), never any
     * ambient or global schema.
     */
    private final AtomicSerialSchemaRecord schema;

    /**
     * Ordered map from field name -> decoded value (or {@link #ABSENT}).
     * <p>
     * The insertion order matches the schema field list. All schema field names
     * are present as keys; the value is either the decoded Java object or
     * {@code ABSENT} when the payload did not provide a TLV for that position
     * (case (b)).
     * <p>
     * LinkedHashMap preserves insertion order (= schema order), which is required
     * by the deterministic field enumeration contract.
     */
    private final Map<String, Object> fields;

    /**
     * Number of extra trailing TLVs in the payload SEQUENCE that were read and
     * discarded because the schema had no corresponding field (case (c)).
     */
    private final int trailingDiscarded;

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Constructs a {@code DerFieldStore} by decoding the DER bytes of one
     * {@code @AtomicSerial} class's private SEQUENCE.
     *
     * <p>The {@code payloadSequence} must be the complete DER encoding of the
     * outer SEQUENCE TLV (tag + length + content). The store reads the outer
     * SEQUENCE, then decodes each field positionally according to
     * {@code schema.fields()}.
     *
     * <p>All decoding is performed here, before the constructor returns; no
     * decoding occurs during subsequent {@code get()} calls.
     *
     * @param schema          the at-marshal-time schema for the class whose
     *                        SEQUENCE is being decoded (must not be {@code null})
     * @param payloadSequence the complete DER encoding of the class's private
     *                        SEQUENCE TLV (must not be {@code null})
     * @throws DerException         if the DER encoding is malformed, a wire type
     *                              is unsupported, or an integer value overflows its
     *                              declared type
     * @throws NullPointerException if either argument is {@code null}
     */
    public DerFieldStore(AtomicSerialSchemaRecord schema, byte[] payloadSequence)
            throws DerException {
        this(schema, payloadSequence, ResolutionContext.NONE);
    }

    /**
     * As {@link #DerFieldStore(AtomicSerialSchemaRecord, byte[])}, resolving any enum/array field
     * component classes against the endpoint-assigned {@link ResolutionContext} rather than the
     * thread-context loader.
     *
     * @param res the endpoint-assigned resolution context (must not be {@code null}; use
     *            {@link ResolutionContext#NONE} for a standalone decode)
     */
    public DerFieldStore(AtomicSerialSchemaRecord schema, byte[] payloadSequence, ResolutionContext res)
            throws DerException {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(payloadSequence, "payloadSequence");
        Objects.requireNonNull(res, "res");
        this.schema = schema;
        DerReader outer = new DerReader(payloadSequence);
        DerReader seq = outer.readSequence();
        // outer should have no further content (one top-level SEQUENCE)
        if (outer.hasMore()) {
            throw new DerException("DerFieldStore: trailing bytes after payload SEQUENCE");
        }
        DecodeResult r = decodeAllFields(schema, seq, res);
        this.fields = r.fields;
        this.trailingDiscarded = r.trailingDiscarded;
    }

    /**
     * Constructs a {@code DerFieldStore} from a {@link DerReader} positioned at
     * the start of the outer SEQUENCE TLV.
     *
     * <p>After construction the reader's cursor is advanced past the entire
     * SEQUENCE (tag + length + content), exactly as {@link DerReader#readSequence()}
     * does. This constructor is useful when the payload SEQUENCE appears embedded
     * within a larger DER structure.
     *
     * @param schema the at-marshal-time schema for the class
     * @param reader a {@link DerReader} positioned at the outer SEQUENCE TLV
     * @throws DerException         if the DER encoding is malformed
     * @throws NullPointerException if either argument is {@code null}
     */
    public DerFieldStore(AtomicSerialSchemaRecord schema, DerReader reader)
            throws DerException {
        this(schema, reader, ResolutionContext.NONE);
    }

    /**
     * As {@link #DerFieldStore(AtomicSerialSchemaRecord, DerReader)}, resolving any enum/array
     * field component classes against the endpoint-assigned {@link ResolutionContext}.
     *
     * @param res the endpoint-assigned resolution context (must not be {@code null})
     */
    public DerFieldStore(AtomicSerialSchemaRecord schema, DerReader reader, ResolutionContext res)
            throws DerException {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(res, "res");
        this.schema = schema;
        DerReader seq = reader.readSequence();
        DecodeResult r = decodeAllFields(schema, seq, res);
        this.fields = r.fields;
        this.trailingDiscarded = r.trailingDiscarded;
    }

    // =========================================================================
    // Core decode logic (static, so it can be called from both constructors)
    // =========================================================================

    private record DecodeResult(Map<String, Object> fields, int trailingDiscarded) {}

    /**
     * Decodes all fields from the SEQUENCE content reader into an ordered map.
     *
     * <p>Implements the three-case logic from S3.9:
     * <ul>
     *   <li>(a) Exact match -- all schema fields present in payload.</li>
     *   <li>(b) Payload shorter -- schema fields exhausted before payload;
     *       remaining schema fields stored as {@link #ABSENT}.</li>
     *   <li>(c) Payload longer -- payload TLVs remaining after schema exhausted;
     *       read-and-discard each extra TLV.</li>
     * </ul>
     *
     * @param schema the schema to decode against
     * @param seq    a sub-reader bounded to the SEQUENCE content
     * @return decoded field map and trailing discard count
     */
    private static DecodeResult decodeAllFields(AtomicSerialSchemaRecord schema,
                                                 DerReader seq, ResolutionContext res) throws DerException {
        List<AtomicSerialFieldDef> fieldDefs = schema.fields();
        // LinkedHashMap preserves schema insertion order
        Map<String, Object> map = new LinkedHashMap<>(fieldDefs.size() * 2);

        int schemaIdx = 0;

        // Phase 1: decode known fields (stop when either schema or payload exhausted)
        while (schemaIdx < fieldDefs.size() && seq.hasMore()) {
            AtomicSerialFieldDef def = fieldDefs.get(schemaIdx);
            final Object value;
            if ("@AtomicSerial".equals(def.wireType())) {
                // Nested @AtomicSerial field (STD-008 sec.16): read the raw TLV bytes
                // (SEQUENCE{schemaBytes,payloadBytes} or NULL 05 00) without decoding.
                // Actual decoding is deferred to DerGetArg.get() in der.object, which
                // can call ObjectCodec.decodeNested -- keeping der.getarg free of
                // any der.object dependency (no package cycle).
                value = readNestedRawTlv(seq);
            } else if (def.wireType().startsWith("array:@AtomicSerial:")) {
                // @AtomicSerial[] array field (STD-008 sec.17.2): read the raw TLV bytes
                // (SEQUENCE of element nested records, or DER NULL) without decoding.
                // Decoding is deferred to DerGetArg.get() in der.object (via
                // ObjectCodec.decodeNestedArray) so the cumulative depth guard is
                // threaded through and der.getarg stays cycle-free.
                String componentClassName = def.wireType().substring("array:@AtomicSerial:".length());
                value = readNestedArrayRawTlv(seq, componentClassName);
            } else if (CollectionWireTypes.isCollection(def.wireType())) {
                // Collection/Map field (STD-006 §3.8): read the raw TLV bytes (SEQUENCE of
                // elements/entries, or DER NULL) without decoding. Decoding is deferred to
                // DerGetArg.get() in der.object (via ObjectCodec.decodeCollection) so nested
                // @AtomicSerial / nested-collection elements thread the cumulative depth guard
                // and der.getarg stays cycle-free.
                value = readCollectionRawTlv(seq, def.wireType());
            } else {
                value = WireTypes.decode(seq, def.wireType(), res);
            }
            map.put(def.wireName(), value);
            schemaIdx++;
        }

        // Phase 2: case (b) -- schema has more fields than payload
        // Mark remaining schema fields as absent (no TLV in payload).
        while (schemaIdx < fieldDefs.size()) {
            map.put(fieldDefs.get(schemaIdx).wireName(), ABSENT);
            schemaIdx++;
        }

        // Phase 3: case (c) -- payload has more TLVs than schema
        // Read-and-discard each extra TLV up to the SEQUENCE boundary.
        int discarded = 0;
        while (seq.hasMore()) {
            DerReader.TlvHeader hdr = seq.readTlvHeader();
            seq.readRawContent(hdr.contentLength());
            discarded++;
        }

        return new DecodeResult(Collections.unmodifiableMap(map), discarded);
    }

    // =========================================================================
    // Object get -- primary API (designed for delegation from real GetArg)
    // =========================================================================

    /**
     * Returns the decoded value for the named field if present in the store, or
     * {@code defaultValue} if the field is absent (case (b)).
     *
     * <p>This is a pure selection operation. No decoding occurs here; all decoding
     * happened at construction time.
     *
     * <p>Decoded but unrequested fields remain in the store until the store itself
     * is dereferenced (see class Javadoc for GC semantics).
     *
     * @param name         the field name (as declared in the schema's {@code wireName})
     * @param defaultValue the value to return when the field is absent
     * @return the decoded value, or {@code defaultValue} if absent
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public Object get(String name, Object defaultValue) {
        Objects.requireNonNull(name, "name");
        Object v = fields.get(name);
        if (v == null) {
            // Name not in schema at all -- treat as absent (defensive)
            return defaultValue;
        }
        return v == ABSENT ? defaultValue : v;
    }

    // =========================================================================
    // Typed get overloads (mirror ObjectInputStream.GetField)
    // =========================================================================

    /**
     * Returns the boolean value for the named field, or {@code defaultValue} if absent.
     *
     * @param name         the field name
     * @param defaultValue the default to return if the field is absent
     * @return the decoded value or the default
     * @throws ClassCastException   if the stored value is not a {@link Boolean}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public boolean get(String name, boolean defaultValue) {
        Object v = get(name, (Object) null);
        return v == null ? defaultValue : (Boolean) v;
    }

    /**
     * Returns the byte value for the named field, or {@code defaultValue} if absent.
     *
     * @param name         the field name
     * @param defaultValue the default to return if the field is absent
     * @return the decoded value or the default
     * @throws ClassCastException   if the stored value is not a {@link Byte}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public byte get(String name, byte defaultValue) {
        Object v = get(name, (Object) null);
        return v == null ? defaultValue : (Byte) v;
    }

    /**
     * Returns the short value for the named field, or {@code defaultValue} if absent.
     *
     * @param name         the field name
     * @param defaultValue the default to return if the field is absent
     * @return the decoded value or the default
     * @throws ClassCastException   if the stored value is not a {@link Short}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public short get(String name, short defaultValue) {
        Object v = get(name, (Object) null);
        return v == null ? defaultValue : (Short) v;
    }

    /**
     * Returns the int value for the named field, or {@code defaultValue} if absent.
     *
     * @param name         the field name
     * @param defaultValue the default to return if the field is absent
     * @return the decoded value or the default
     * @throws ClassCastException   if the stored value is not an {@link Integer}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public int get(String name, int defaultValue) {
        Object v = get(name, (Object) null);
        return v == null ? defaultValue : (Integer) v;
    }

    /**
     * Returns the long value for the named field, or {@code defaultValue} if absent.
     *
     * @param name         the field name
     * @param defaultValue the default to return if the field is absent
     * @return the decoded value or the default
     * @throws ClassCastException   if the stored value is not a {@link Long}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public long get(String name, long defaultValue) {
        Object v = get(name, (Object) null);
        return v == null ? defaultValue : (Long) v;
    }

    /**
     * Returns the char value for the named field, or {@code defaultValue} if absent.
     * STD-008 sec.17.3.2 (S7.6 lift): the wire is a Unicode codepoint INTEGER, range-checked
     * by {@code WireTypes.decodeChar}.
     */
    public char get(String name, char defaultValue) {
        Object v = get(name, (Object) null);
        return v == null ? defaultValue : (Character) v;
    }

    /**
     * Returns the float value for the named field, or {@code defaultValue} if absent.
     * STD-008 sec.17.3.1 (S7.6 lift): IEEE-754 with strict canonical NaN / {@code +0.0}
     * enforced by {@code WireTypes.decodeFloat}.
     */
    public float get(String name, float defaultValue) {
        Object v = get(name, (Object) null);
        return v == null ? defaultValue : (Float) v;
    }

    /**
     * Returns the double value for the named field, or {@code defaultValue} if absent.
     * STD-008 sec.17.3.1 (S7.6 lift): IEEE-754 with strict canonical NaN / {@code +0.0}
     * enforced by {@code WireTypes.decodeDouble}.
     */
    public double get(String name, double defaultValue) {
        Object v = get(name, (Object) null);
        return v == null ? defaultValue : (Double) v;
    }

    // =========================================================================
    // Presence / absence query
    // =========================================================================

    /**
     * Returns {@code true} if the named field is absent from the store -- i.e.
     * the payload did not supply a TLV for this field (case (b)) or the field is
     * not defined in the schema at all. Mirrors {@code ObjectInputStream.GetField.defaulted()}.
     *
     * <p>When {@code defaulted} returns {@code true}, a subsequent
     * {@code get(name, default)} call will return {@code default}.
     *
     * @param name the field name
     * @return {@code true} if the field is absent
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public boolean defaulted(String name) {
        Objects.requireNonNull(name, "name");
        Object v = fields.get(name);
        return v == null || v == ABSENT;
    }

    // =========================================================================
    // Introspection
    // =========================================================================

    /**
     * Returns an immutable, schema-ordered set of field names that are
     * <em>present</em> (decoded, not absent) in this store.
     *
     * @return present field names in schema order
     */
    public Set<String> presentFieldNames() {
        // Build a set of names where the stored value is not ABSENT
        // Use LinkedHashMap iteration order to preserve schema order
        LinkedHashMap<String, Object> presentMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getValue() != ABSENT) {
                presentMap.put(e.getKey(), Boolean.TRUE);
            }
        }
        return Collections.unmodifiableSet(presentMap.keySet());
    }

    /**
     * Returns an immutable view of the complete field map (present fields only;
     * absent fields are not included). Values are the decoded Java objects.
     * The map preserves schema field order.
     *
     * @return an ordered, immutable map of present field names to decoded values
     */
    public Map<String, Object> presentFields() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getValue() != ABSENT) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Returns the number of extra trailing TLVs in the payload SEQUENCE that
     * were read-and-discarded because the schema had no corresponding field
     * (case (c)). Zero for cases (a) and (b).
     *
     * @return count of discarded trailing TLVs (>= 0)
     */
    public int trailingFieldsDiscarded() {
        return trailingDiscarded;
    }

    /**
     * Returns the class name from the schema this store was built with.
     *
     * @return the class name string from {@code schema.className()}
     */
    public String className() {
        return schema.className();
    }

    /**
     * Returns the schema record this store was built with. The schema is always
     * the one passed in at construction time (the at-marshal-time schema embedded
     * in the {@code MarshalledInstance}); it is never replaced by any ambient or
     * global schema.
     *
     * @return the schema record
     */
    public AtomicSerialSchemaRecord schema() {
        return schema;
    }

    // =========================================================================
    // Nested @AtomicSerial field access (STD-008 sec.16)
    // =========================================================================

    /**
     * Returns {@code true} if the named field holds a nested {@code @AtomicSerial}
     * raw record (wireType {@code "@AtomicSerial"}), regardless of whether the value
     * is null (DER NULL) or a real object.
     *
     * <p>A field that is absent (case (b)) returns {@code false} here; the caller
     * checks {@link #defaulted(String)} and returns the default value.
     *
     * @param name the field name
     * @return {@code true} if the stored value is a {@link NestedRaw} wrapper
     */
    public boolean isNested(String name) {
        Object v = fields.get(name);
        return v instanceof NestedRaw;
    }

    /**
     * Returns the raw TLV bytes for a nested {@code @AtomicSerial} field (a
     * SEQUENCE or DER NULL). The caller (in {@code der.object}) is responsible
     * for decoding them via {@code ObjectCodec.decodeNested}.
     *
     * @param name the field name
     * @return a defensive copy of the raw TLV bytes
     * @throws IllegalStateException if the field is not a nested raw field
     *                               (check {@link #isNested(String)} first)
     */
    public byte[] rawNested(String name) {
        Object v = fields.get(name);
        if (!(v instanceof NestedRaw nr)) {
            throw new IllegalStateException(
                    "DerFieldStore: field '" + name + "' is not a nested raw field");
        }
        return nr.rawBytes(); // NestedRaw.rawBytes() already returns a defensive copy
    }

    /**
     * Reads the complete TLV bytes (tag + length + content) of the next item in
     * {@code seq} as a raw byte array, without interpreting the TLV. Used to
     * capture nested {@code @AtomicSerial} records (SEQUENCE or NULL) without
     * decoding them here.
     */
    private static NestedRaw readNestedRawTlv(DerReader seq) throws DerException {
        // Peek the tag to determine whether this is NULL (2 bytes) or a SEQUENCE
        DerReader.TlvHeader hdr = seq.readTlvHeader();
        byte[] content = seq.readRawContent(hdr.contentLength());

        // Reconstruct the full TLV bytes for later decoding
        byte[] tagBytes    = hdr.tag().encode();
        byte[] lengthBytes = DerWriter.encodeLength(hdr.contentLength());
        byte[] raw = new byte[tagBytes.length + lengthBytes.length + content.length];
        int pos = 0;
        System.arraycopy(tagBytes,    0, raw, pos, tagBytes.length);    pos += tagBytes.length;
        System.arraycopy(lengthBytes, 0, raw, pos, lengthBytes.length); pos += lengthBytes.length;
        System.arraycopy(content,     0, raw, pos, content.length);
        return new NestedRaw(raw);
    }

    /**
     * Reads the complete TLV bytes (tag + length + content) of the next item in
     * {@code seq} for an {@code @AtomicSerial[]} array field, without interpreting
     * the TLV. The raw bytes are wrapped in a {@link NestedArrayRaw} so that
     * {@code DerGetArg} (in {@code der.object}) can decode them depth-boundedly.
     *
     * @param seq                sub-reader positioned at the array TLV (SEQUENCE or NULL)
     * @param componentClassName fully-qualified name of the component class
     * @return the raw bytes wrapped in a {@link NestedArrayRaw}
     */
    private static NestedArrayRaw readNestedArrayRawTlv(DerReader seq,
                                                         String componentClassName)
            throws DerException {
        DerReader.TlvHeader hdr = seq.readTlvHeader();
        byte[] content = seq.readRawContent(hdr.contentLength());

        byte[] tagBytes    = hdr.tag().encode();
        byte[] lengthBytes = DerWriter.encodeLength(hdr.contentLength());
        byte[] raw = new byte[tagBytes.length + lengthBytes.length + content.length];
        int pos = 0;
        System.arraycopy(tagBytes,    0, raw, pos, tagBytes.length);    pos += tagBytes.length;
        System.arraycopy(lengthBytes, 0, raw, pos, lengthBytes.length); pos += lengthBytes.length;
        System.arraycopy(content,     0, raw, pos, content.length);
        return new NestedArrayRaw(raw, componentClassName);
    }

    /**
     * Reads the complete TLV bytes (tag + length + content) of the next item in
     * {@code seq} for a {@code Collection}/{@code Map} field (STD-006 §3.8), without
     * interpreting the TLV. The raw bytes and the full wire-type token are wrapped in a
     * {@link CollectionRaw} so that {@code DerGetArg} (in {@code der.object}) can decode
     * them depth-boundedly via {@code ObjectCodec.decodeCollection}.
     *
     * @param seq      sub-reader positioned at the collection TLV (SEQUENCE or NULL)
     * @param wireType the full collection wire-type token
     * @return the raw bytes wrapped in a {@link CollectionRaw}
     */
    private static CollectionRaw readCollectionRawTlv(DerReader seq, String wireType)
            throws DerException {
        DerReader.TlvHeader hdr = seq.readTlvHeader();
        byte[] content = seq.readRawContent(hdr.contentLength());

        byte[] tagBytes    = hdr.tag().encode();
        byte[] lengthBytes = DerWriter.encodeLength(hdr.contentLength());
        byte[] raw = new byte[tagBytes.length + lengthBytes.length + content.length];
        int pos = 0;
        System.arraycopy(tagBytes,    0, raw, pos, tagBytes.length);    pos += tagBytes.length;
        System.arraycopy(lengthBytes, 0, raw, pos, lengthBytes.length); pos += lengthBytes.length;
        System.arraycopy(content,     0, raw, pos, content.length);
        return new CollectionRaw(raw, wireType);
    }

    // =========================================================================
    // Collection / Map field access (STD-006 §3.8)
    // =========================================================================

    /**
     * Returns {@code true} if the named field holds a {@code Collection}/{@code Map}
     * raw record (wireType {@code set:}/{@code orderedset:}/{@code list:}/{@code map:}/
     * {@code orderedmap:}), regardless of whether the value is null (DER NULL) or a
     * real collection.
     *
     * <p>A field that is absent (case (b)) returns {@code false}; the caller checks
     * {@link #defaulted(String)} and returns the default.
     *
     * @param name the field name
     * @return {@code true} if the stored value is a {@link CollectionRaw} wrapper
     */
    public boolean isCollection(String name) {
        return fields.get(name) instanceof CollectionRaw;
    }

    /**
     * Returns the raw TLV bytes for a {@code Collection}/{@code Map} field (a SEQUENCE
     * or DER NULL). The caller (in {@code der.object}) decodes them via
     * {@code ObjectCodec.decodeCollection}.
     *
     * @param name the field name
     * @return a defensive copy of the raw TLV bytes
     * @throws IllegalStateException if the field is not a collection raw field
     *                               (check {@link #isCollection(String)} first)
     */
    public byte[] rawCollection(String name) {
        Object v = fields.get(name);
        if (!(v instanceof CollectionRaw cr)) {
            throw new IllegalStateException(
                    "DerFieldStore: field '" + name + "' is not a collection raw field");
        }
        return cr.rawBytes(); // CollectionRaw.rawBytes() already returns a defensive copy
    }

    /**
     * Returns the full collection wire-type token for a {@code Collection}/{@code Map}
     * field.
     *
     * @param name the field name
     * @return the collection wire-type token
     * @throws IllegalStateException if the field is not a collection raw field
     *                               (check {@link #isCollection(String)} first)
     */
    public String collectionWireType(String name) {
        Object v = fields.get(name);
        if (!(v instanceof CollectionRaw cr)) {
            throw new IllegalStateException(
                    "DerFieldStore: field '" + name + "' is not a collection raw field");
        }
        return cr.wireType();
    }

    // =========================================================================
    // Nested @AtomicSerial[] array access (STD-008 sec.17.2)
    // =========================================================================

    /**
     * Returns {@code true} if the named field holds a nested {@code @AtomicSerial[]}
     * raw array record (wireType {@code "array:@AtomicSerial:<class>"}).
     *
     * <p>A field that is absent (case (b)) returns {@code false}; the caller
     * checks {@link #defaulted(String)} and returns the default.
     *
     * @param name the field name
     * @return {@code true} if the stored value is a {@link NestedArrayRaw} wrapper
     */
    public boolean isNestedArray(String name) {
        Object v = fields.get(name);
        return v instanceof NestedArrayRaw;
    }

    /**
     * Returns the raw TLV bytes for a nested {@code @AtomicSerial[]} array field
     * (a SEQUENCE or DER NULL). The caller (in {@code der.object}) is responsible
     * for decoding via {@code ObjectCodec.decodeNestedArray}.
     *
     * @param name the field name
     * @return a defensive copy of the raw TLV bytes
     * @throws IllegalStateException if the field is not a nested array raw field
     *                               (check {@link #isNestedArray(String)} first)
     */
    public byte[] rawNestedArray(String name) {
        Object v = fields.get(name);
        if (!(v instanceof NestedArrayRaw nar)) {
            throw new IllegalStateException(
                    "DerFieldStore: field '" + name + "' is not a nested array raw field");
        }
        return nar.rawBytes(); // NestedArrayRaw.rawBytes() already returns a defensive copy
    }

    /**
     * Returns the fully-qualified component class name for a nested
     * {@code @AtomicSerial[]} array field (extracted from the wireType).
     *
     * @param name the field name
     * @return the component class name (e.g. {@code "com.example.Foo"})
     * @throws IllegalStateException if the field is not a nested array raw field
     *                               (check {@link #isNestedArray(String)} first)
     */
    public String nestedArrayComponentClassName(String name) {
        Object v = fields.get(name);
        if (!(v instanceof NestedArrayRaw nar)) {
            throw new IllegalStateException(
                    "DerFieldStore: field '" + name + "' is not a nested array raw field");
        }
        return nar.componentClassName();
    }

    // =========================================================================
    // toString
    // =========================================================================

    @Override
    public String toString() {
        return "DerFieldStore{className='" + schema.className()
                + "', presentFields=" + presentFieldNames()
                + ", trailingDiscarded=" + trailingDiscarded + '}';
    }

    // =========================================================================
    // Top-level value-array decode bridge (for the [9] CTX_ARRAY stream tag)
    // =========================================================================

    /**
     * Decodes a top-level primitive / {@code String} / enum array from its raw element
     * {@code SEQUENCE} TLV bytes -- the {@code [9] CTX_ARRAY} stream decoder's bridge to the
     * package-private {@link WireTypes} decoder (which lives in this package, so the stream
     * codec cannot call it directly). Used for the value-array parallel of {@code byte[]};
     * {@code @AtomicSerial}-component arrays are NOT handled here -- the stream decoder calls
     * {@code ObjectCodec.decodeNestedArray} for those.
     *
     * @param sequenceTlv   the raw element {@code SEQUENCE} TLV (from {@code encodeTopLevelArray})
     * @param arrayWireType the full array wireType, e.g. {@code "array:long"} (never
     *                      {@code "array:@AtomicSerial:..."})
     * @param res           the endpoint resolution context (for enum component classes)
     * @return the decoded typed array (e.g. {@code long[]}, {@code String[]})
     * @throws DerException if the encoding is malformed or the component type is unsupported
     */
    public static Object decodePrimitiveArray(byte[] sequenceTlv, String arrayWireType,
                                              ResolutionContext res) throws DerException {
        return WireTypes.decode(new DerReader(sequenceTlv), arrayWireType, res);
    }

    /**
     * Decodes a single scalar / {@code String} / {@code enum} element from a {@link DerReader}
     * positioned at the element TLV -- the collection-codec's bridge to the package-private
     * {@link WireTypes} decoder (which cannot be reached directly from {@code der.object}). The
     * reader cursor advances past the element TLV.
     *
     * <p>Used by {@code ObjectCodec.decodeCollection} for a collection whose element (or map
     * key/value) wire-type is a scalar, {@code String}, {@code byte[]}, {@code enum:}, or
     * {@code array:} (value array) type. {@code @AtomicSerial} and nested-collection element
     * wire-types are NOT routed here -- {@code ObjectCodec} decodes those itself so the nesting
     * depth guard is threaded.
     *
     * @param reader   a reader positioned at the element TLV
     * @param wireType the element / key / value wire-type (never a {@code @AtomicSerial} or
     *                 collection token)
     * @param res      the endpoint resolution context (for enum component classes)
     * @return the decoded, boxed value (may be {@code null} for a nullable type encoded as NULL)
     * @throws DerException if the wireType is unsupported here or the encoding is malformed
     */
    public static Object decodeScalarElement(DerReader reader, String wireType,
                                             ResolutionContext res) throws DerException {
        return WireTypes.decode(reader, wireType, res);
    }
}
