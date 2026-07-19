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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;

import java.io.IOException;
import java.math.BigInteger;

import net.jini.io.context.DeserializationCompletion;

/**
 * The STD-006 self-describing {@code Any} element form: a context-tagged CHOICE over the
 * closed-subset categories (design memo {@code docs/der-type-model-and-element-rule.md} §4).
 *
 * <p>The {@code Any} form is the wire-type the element-derivation rule selects when — and only
 * when — a declared type is genuinely unresolvable: at collection-ELEMENT position, a collection
 * field's declared element type (a raw collection, {@code Set<?>}, {@code Set<Object>},
 * {@code Set<? super X>}, or a type-variable {@code Set<T>}; memo §3.4, edge cases E10–E14); and,
 * identically, at FIELD position, a serial field declared exactly {@code Object.class} (e.g.
 * {@code net.jini.core.event.RemoteEvent.source}, inherited from {@code java.util.EventObject} —
 * memo §2.1 exclusion 1, §3 "the rule is total"). It is NEVER the default and NEVER chosen for a
 * resolvable type.
 *
 * <h2>CHOICE — the context tag IS the category discriminator (memo §4)</h2>
 * <p>There is no {@code SEQUENCE{tag,body}} wrapper and no {@code ENUMERATED} discriminator: the
 * CHOICE's context tag is carried in-place on the element's own encoding and is the single
 * category discriminator. This eliminates the "lying encoding" hazard by construction (there is no
 * second field that could disagree) and adds no wrapper octets past the tag, so a value's payload
 * bytes are identical whether it appears in a declared-element collection or in an {@code Any}
 * collection.
 *
 * <pre>
 *   AnyElement ::= CHOICE {
 *       scalarBoolean        [0]  IMPLICIT BOOLEAN,
 *       scalarByte           [1]  IMPLICIT INTEGER,
 *       scalarShort          [2]  IMPLICIT INTEGER,
 *       scalarInt            [3]  IMPLICIT INTEGER,
 *       scalarLong           [4]  IMPLICIT INTEGER,
 *       scalarFloat          [5]  IMPLICIT OCTET STRING,   -- IEEE-754 32-bit, strict-canonical
 *       scalarDouble         [6]  IMPLICIT OCTET STRING,   -- IEEE-754 64-bit, strict-canonical
 *       scalarChar           [7]  IMPLICIT INTEGER,         -- Unicode codepoint (BMP non-surrogate)
 *       scalarString         [8]  IMPLICIT UTF8String,
 *       scalarBytes          [9]  IMPLICIT OCTET STRING,    -- a byte[] element
 *       -- gap [10..19] RESERVED
 *       atomicSerialObject   [20] EXPLICIT AtomicSerialRecord, -- schema-digest-identified record
 *       -- gap [21..29] RESERVED
 *       canonicalCollection  [30] EXPLICIT CanonicalCollection, -- SET OF 0x31: set:/bag:/map:
 *       orderedCollection    [31] EXPLICIT OrderedCollection    -- SEQUENCE OF 0x30: orderedset:/list:/orderedmap:
 *   }
 * </pre>
 *
 * <h3>IMPLICIT vs EXPLICIT tagging (memo §4.1)</h3>
 * <ul>
 *   <li><b>Scalars {@code [0]}–{@code [9]} are {@code IMPLICIT}</b>: the value's leading universal
 *       tag octet is <em>replaced</em> by the context tag (primitive). The value bytes past the tag
 *       are the scalar's ordinary canonical DER, so a cross-language reader knows the primitive
 *       category from the context tag alone.</li>
 *   <li><b>{@code atomicSerialObject [20]} and the collection arms {@code [30]}/{@code [31]} are
 *       {@code EXPLICIT}</b>: an {@code EXPLICIT} tag <em>wraps</em> the inner TLV, so the inner
 *       {@code SET OF}/{@code SEQUENCE OF} outer tag ({@code 0x31}/{@code 0x30}) — the Option-A
 *       preserve-vs-canonicalise discipline discriminator (memo §3.8) — survives intact. An
 *       {@code IMPLICIT} tag would overwrite that load-bearing outer tag (X.680 §31.2.7 forbids
 *       {@code IMPLICIT}-tagging a type whose outer tag carries meaning). {@code atomicSerialObject}
 *       is {@code EXPLICIT} for the same reason: the inner record may be a {@code SEQUENCE}, a
 *       {@code [8]} proxy record, or DER {@code NULL}, so its inner tag is load-bearing and must be
 *       preserved for {@link ObjectCodec#decodeNested} to dispatch.</li>
 * </ul>
 *
 * <h3>Null elements</h3>
 * <p>A {@code null} collection element is carried verbatim as DER {@code NULL} ({@code 05 00}) — the
 * same null sentinel the rest of the codec uses. {@code NULL} is not a registry category; it is the
 * absence of a value, permitted wherever a nullable element travels.
 *
 * <h2>The four SECURITY FENCES (memo §4.3 — NORMATIVE, each conformance-tested)</h2>
 * <ol type="a">
 *   <li><b>{@code MAX_NESTING} threaded through every recursion.</b> {@link #decode} carries the
 *       nesting depth and threads it through every {@code Any}→collection and
 *       {@code Any}→{@code atomicSerialObject} step; the underlying {@link ObjectCodec#decodeCollection}
 *       / {@link ObjectCodec#decodeNested} enforce {@link ObjectCodec#MAX_NESTING}. Under {@code Any}
 *       the nesting depth is attacker-controlled (the wire, not the schema, chooses each element's
 *       category), so an unbounded recursion is a StackOverflow DoS.</li>
 *   <li><b>Same {@code @AtomicSerial} reconstruction gate — {@code Any} is not a second door.</b> An
 *       {@code Any} {@code atomicSerialObject [20]} body is reconstructed through the identical
 *       {@link ObjectCodec#decodeNested} path as a typed {@code @AtomicSerial} element — the
 *       {@code DeSerializationPermission("ATOMIC")} gate, the endpoint {@link ResolutionContext}, and
 *       each class's {@code check(GetArg)} all run exactly as for a typed element. {@code Any}
 *       provides no ungated or differently-gated route to object reconstruction.</li>
 *   <li><b>Unknown / unregistered / reserved tag → HARD REJECT (fail-secure).</b> A context tag not
 *       in the pinned registry (a reserved-gap tag {@code [10..19]}/{@code [21..29]}, or any tag
 *       {@code > [31]}), or a context tag whose constructed/primitive form does not match its arm,
 *       is a decode error. The decoder never skips, never defaults, never continues.</li>
 *   <li><b>Canonical minimal tag encoding + deterministic value→category mapping.</b> The context
 *       tag MUST be in minimal DER tag encoding (all {@code Any} tags are {@code <= 31}, so all are
 *       single-octet low-tag-number form; a high-tag-number encoding of a low tag is rejected by
 *       {@link Tag#decode}). Every value maps to exactly one category tag on encode (a
 *       {@code String} is always {@code [8]}, an {@code int} always {@code [3]}, a canonicalise
 *       collection always {@code [30]}), so octet-sort and value-equality see one byte form per
 *       value.</li>
 * </ol>
 *
 * <h2>Value-equality / canonicity (memo §4.2)</h2>
 * <p>An {@code AnyElement} is a complete TLV whose leading octet is its context tag, so a
 * canonicalise collection of {@code Any} elements octet-sorts (X.690 §11.6,
 * {@link CollectionWireTypes#compareOctets}) by tag first, then by value within a category — a
 * total deterministic order, and category-grouping is a provable property of the leading tag. Two
 * {@code .equals} sets of {@code Any} elements therefore encode byte-identically.
 *
 * <p>This class is stateless and thread-safe.
 */
final class AnyCodec {

    private AnyCodec() {
        throw new AssertionError("no instances");
    }

    /** The element wire-type token that routes to the {@code Any} form (memo §3.4, §4). */
    static final String ANY = "any";

    // -------------------------------------------------------------------------
    // The Any tag REGISTRY (memo §4.5). The CHOICE context tags ARE the registry.
    // Scalar arms are IMPLICIT (primitive); object/collection arms are EXPLICIT (constructed).
    // -------------------------------------------------------------------------

    static final int TAG_BOOLEAN =  0; // [0]  IMPLICIT BOOLEAN
    static final int TAG_BYTE    =  1; // [1]  IMPLICIT INTEGER
    static final int TAG_SHORT   =  2; // [2]  IMPLICIT INTEGER
    static final int TAG_INT     =  3; // [3]  IMPLICIT INTEGER
    static final int TAG_LONG    =  4; // [4]  IMPLICIT INTEGER
    static final int TAG_FLOAT   =  5; // [5]  IMPLICIT OCTET STRING (IEEE-754 32-bit)
    static final int TAG_DOUBLE  =  6; // [6]  IMPLICIT OCTET STRING (IEEE-754 64-bit)
    static final int TAG_CHAR    =  7; // [7]  IMPLICIT INTEGER (codepoint)
    static final int TAG_STRING  =  8; // [8]  IMPLICIT UTF8String
    static final int TAG_BYTES   =  9; // [9]  IMPLICIT OCTET STRING (byte[])
    // [10..19] RESERVED
    static final int TAG_ATOMIC  = 20; // [20] EXPLICIT AtomicSerialRecord
    // [21..29] RESERVED
    static final int TAG_CANONICAL_COLL = 30; // [30] EXPLICIT SET OF (0x31)
    static final int TAG_ORDERED_COLL   = 31; // [31] EXPLICIT SEQUENCE OF (0x30)

    /** DER NULL tag byte. A null element is carried verbatim (not a registry category). */
    private static final int TAG_NULL = 0x05;

    // =========================================================================
    // Encode
    // =========================================================================

    /**
     * Encodes one collection element as an {@code AnyElement} (memo §4.1). The value's runtime
     * category selects the context tag deterministically (fence (d)); the category tag is the sole
     * discriminator on the wire.
     *
     * @param value     the element value (may be {@code null})
     * @param fieldName diagnostics
     * @param depth     current nesting depth (threaded into object/collection recursion)
     * @return the complete {@code AnyElement} TLV
     * @throws DerException if the value is not a closed-subset value (fence: the closed model
     *                      excludes arbitrary {@code Object} graphs and {@code Serializable})
     */
    static byte[] encode(Object value, String fieldName, int depth) throws DerException {
        // A null element travels as DER NULL (the absence sentinel; not a registry category).
        if (value == null) {
            return new byte[]{ (byte) TAG_NULL, 0x00 };
        }

        // --- Scalars: [0]-[9] IMPLICIT (the context tag replaces the value's universal tag) ---
        if (value instanceof Boolean b) {
            // BOOLEAN content is one canonical octet (0x00 / 0xFF).
            return implicitPrimitive(TAG_BOOLEAN, contentOf(DerWriter.writeBoolean(b)));
        }
        if (value instanceof Byte by) {
            return implicitPrimitive(TAG_BYTE, contentOf(DerWriter.writeInteger(BigInteger.valueOf(by))));
        }
        if (value instanceof Short s) {
            return implicitPrimitive(TAG_SHORT, contentOf(DerWriter.writeInteger(BigInteger.valueOf(s))));
        }
        if (value instanceof Integer iv) {
            return implicitPrimitive(TAG_INT, contentOf(DerWriter.writeInteger(BigInteger.valueOf(iv))));
        }
        if (value instanceof Long l) {
            return implicitPrimitive(TAG_LONG, contentOf(DerWriter.writeInteger(BigInteger.valueOf(l))));
        }
        if (value instanceof Float f) {
            // IEEE-754 32-bit strict-canonical OCTET STRING (STD-008 §17.3.1); reuse ObjectCodec.
            return implicitPrimitive(TAG_FLOAT, contentOf(ObjectCodec.encodeFloat(f, fieldName)));
        }
        if (value instanceof Double d) {
            return implicitPrimitive(TAG_DOUBLE, contentOf(ObjectCodec.encodeDouble(d, fieldName)));
        }
        if (value instanceof Character c) {
            return implicitPrimitive(TAG_CHAR, contentOf(ObjectCodec.encodeChar(c, fieldName)));
        }
        if (value instanceof String str) {
            return implicitPrimitive(TAG_STRING, contentOf(DerWriter.writeUtf8String(str)));
        }
        if (value instanceof byte[] bytes) {
            return implicitPrimitive(TAG_BYTES, contentOf(DerWriter.writeOctetString(bytes)));
        }

        // --- Collections: [30]/[31] EXPLICIT (inner SET OF/SEQUENCE OF outer tag preserved) ---
        if (value instanceof java.util.Collection<?> || value instanceof java.util.Map<?, ?>) {
            // Fence (a), encode side: each Any->collection level is bounded, symmetric with decode,
            // so a pathologically-nested Any value cannot StackOverflow the encoder either.
            int next = requireDepth(depth, "collection");
            // Derive the token by rule from the runtime declared collection class (discipline +
            // recursive Any elements): a canonicalise collection is [30], an ordered one is [31].
            // Elements of an Any collection are themselves Any (unresolvable stays unresolvable).
            String token = anyCollectionToken(value.getClass());
            byte[] innerColl = ObjectCodec.encodeCollection(value, token, fieldName, next);
            boolean canonicalise = CollectionWireTypes.isCanonicalise(token);
            int ctxTag = canonicalise ? TAG_CANONICAL_COLL : TAG_ORDERED_COLL;
            return explicitWrap(ctxTag, innerColl);
        }

        // --- @AtomicSerial object: [20] EXPLICIT (inner record tag preserved) ---
        // A value whose runtime class has an @AtomicSerial ancestor (or is a registered DER
        // serializer target, or a java.lang.reflect.Proxy over an @AtomicSerial handler) is a
        // closed record: encode it exactly as a typed nested @AtomicSerial element, then wrap.
        // encodeNested throws fail-fast if the value is not a closed-subset object, so an
        // arbitrary Object graph / Serializable can never travel as Any (memo §2.1 exclusion).
        byte[] innerRecord = ObjectCodec.encodeNested(value, fieldName, depth);
        return explicitWrap(TAG_ATOMIC, innerRecord);
    }

    /**
     * Derives the {@code Any} collection token for a runtime collection class: the ordering
     * discipline comes from the declared class (memo §3.8), and every element is itself {@code Any}
     * (an {@code Any} collection's elements are, by construction, unresolvable — memo §4.4). A map's
     * key and value are both {@code Any}.
     */
    private static String anyCollectionToken(Class<?> collClass) {
        if (java.util.Map.class.isAssignableFrom(collClass)) {
            return CollectionWireTypes.mapToken(collClass, ANY, ANY);
        }
        return CollectionWireTypes.setToken(collClass, ANY);
    }

    /**
     * Builds an {@code [n] IMPLICIT} primitive context TLV: the primitive context tag {@code n}
     * followed by the length and the supplied content (the value's own DER content, its universal
     * tag stripped). Minimal canonical length encoding (fence (d)).
     */
    private static byte[] implicitPrimitive(int ctxTag, byte[] content) {
        return DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, ctxTag), content);
    }

    /**
     * Builds an {@code [n] EXPLICIT} constructed context TLV: the constructed context tag {@code n}
     * wrapping the inner TLV verbatim (the inner outer tag survives — memo §4.1).
     */
    private static byte[] explicitWrap(int ctxTag, byte[] innerTlv) {
        return DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, ctxTag), innerTlv);
    }

    /**
     * Returns the content octets of a complete primitive DER TLV (strips its tag and length),
     * so the content can be re-tagged under an IMPLICIT context tag. The input is always a
     * definite-length primitive TLV produced by {@link DerWriter}.
     */
    private static byte[] contentOf(byte[] tlv) throws DerException {
        DerReader r = new DerReader(tlv);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        return r.readRawContent(hdr.contentLength());
    }

    // =========================================================================
    // Decode
    // =========================================================================

    /**
     * Decodes one {@code AnyElement} from {@code reader}, advancing the cursor past its complete
     * TLV. Dispatches solely on the context tag (fence (c)/(d)); threads {@code depth} into every
     * object/collection recursion (fence (a)); reconstructs {@code atomicSerialObject} through the
     * identical gated path as a typed element (fence (b)).
     *
     * @param reader     positioned at the start of the {@code AnyElement} TLV
     * @param depth      current nesting depth (for the {@link ObjectCodec#MAX_NESTING} guard)
     * @param decodeUnit the per-decode-unit completion sink, or {@code null}
     * @param resolution the endpoint resolution context
     * @return the decoded element value (may be {@code null} for the DER NULL sentinel)
     * @throws DerException if the context tag is unregistered/reserved/mismatched, the value is
     *                      not canonical for its tag, or the depth bound is exceeded
     */
    static Object decode(DerReader reader, int depth,
                         DeserializationCompletion decodeUnit,
                         ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        // No declared-type information at this call site (a collection/map element -- an Any
        // collection's elements are themselves Any by construction, memo §4.4 -- so there is no
        // narrower receiving slot type to thread). Admits at Object.class, preserving prior
        // behaviour; see the security-review R2 F1 hardening note on the typed overload below.
        return decode(reader, Object.class, depth, decodeUnit, resolution);
    }

    /**
     * Type-threading variant of {@link #decode(DerReader, int, DeserializationCompletion,
     * ResolutionContext)}: {@code expectedSupertype} is the receiving slot's <b>declared</b> Java
     * type (e.g. {@code callerClass.getDeclaredField(name).getType()} for an {@code Any}-resolved
     * field), threaded into the {@code atomicSerialObject [20]} arm's reconstruction gate exactly
     * as the typed nested-{@code @AtomicSerial} decode path already threads it (security review R2
     * F1 -- {@code ObjectCodec#decodeNested(byte[], Class, int, DeserializationCompletion,
     * ResolutionContext)}). Pass {@code Object.class} when no declared type is known.
     *
     * <p><b>Hardening, not a new gate.</b> For a genuinely {@code Object}-typed slot the admission
     * gate is vacuous either way (identical to the residual the withdrawn {@code MarshalledInstance}
     * wrap already had, and to every {@code Any} collection element on trunk today) -- this overload
     * exists so the F1 gate stays uniform across every {@code decodeNested} entry point, not because
     * an {@code Any} field is reachable through a different door than an {@code Any} element.
     *
     * @param reader             positioned at the start of the {@code AnyElement} TLV
     * @param expectedSupertype  the receiving slot's declared type (never {@code null})
     * @param depth              current nesting depth (for the {@link ObjectCodec#MAX_NESTING} guard)
     * @param decodeUnit         the per-decode-unit completion sink, or {@code null}
     * @param resolution         the endpoint resolution context
     * @return the decoded element value (may be {@code null} for the DER NULL sentinel)
     * @throws DerException if the context tag is unregistered/reserved/mismatched, the value is
     *                      not canonical for its tag, or the depth bound is exceeded
     */
    static Object decode(DerReader reader, Class<?> expectedSupertype, int depth,
                         DeserializationCompletion decodeUnit,
                         ResolutionContext resolution)
            throws DerException, IOException, ClassNotFoundException {
        Tag tag = reader.peekTag();

        // Null sentinel: DER NULL (universal 0x05) -> null element.
        if (tag.tagClass() == Tag.CLASS_UNIVERSAL && !tag.isConstructed() && tag.tagNumber() == TAG_NULL) {
            DerReader.TlvHeader hdr = reader.readTlvHeader();
            if (hdr.contentLength() != 0) {
                throw new DerException("AnyCodec.decode: malformed NULL element (non-zero length)");
            }
            return null;
        }

        // Fence (c): every AnyElement MUST carry a CONTEXT-class tag. A UNIVERSAL/APPLICATION/
        // PRIVATE tag here is not an AnyElement -- hard reject, no fallback.
        if (tag.tagClass() != Tag.CLASS_CONTEXT) {
            throw new DerException("AnyCodec.decode: expected a CONTEXT-class Any tag, got " + tag
                    + " (fail-secure: an AnyElement is always context-tagged)");
        }

        int n = tag.tagNumber();
        boolean constructed = tag.isConstructed();

        // --- Scalars [0]-[9] IMPLICIT (primitive): re-tag content under its universal type ---
        if (n <= TAG_BYTES) {
            // Fence (c): a scalar arm MUST be primitive. A constructed [0..9] is a mismatch.
            if (constructed) {
                throw new DerException("AnyCodec.decode: scalar Any tag [" + n
                        + "] must be primitive, got constructed (fail-secure)");
            }
            DerReader.TlvHeader hdr = reader.readTlvHeader();
            byte[] content = reader.readRawContent(hdr.contentLength());
            return decodeScalar(n, content);
        }

        // --- [20] EXPLICIT @AtomicSerial object ---
        if (n == TAG_ATOMIC) {
            if (!constructed) {
                throw new DerException("AnyCodec.decode: atomicSerialObject Any tag [20] must be "
                        + "constructed (EXPLICIT), got primitive (fail-secure)");
            }
            byte[] innerTlv = readExplicitInner(reader);
            // Fence (a): EVERY Any->atomicSerialObject step counts as one nesting level and is
            // bounded, BEFORE recursing -- under Any the wire (not the schema) chooses to nest an
            // object, so an unbounded chain is a StackOverflow DoS.
            int next = requireDepth(depth, "atomicSerialObject");
            // Fence (b): reconstruct through the SAME decodeNested path -- the ATOMIC gate, the
            // endpoint ResolutionContext, and each class's check(GetArg) all run identically.
            // Threads expectedSupertype (Hardening note, class javadoc): vacuous for a genuine
            // Object-typed slot, but keeps the F1 admission gate uniform across every
            // decodeNested entry point.
            return ObjectCodec.decodeNested(innerTlv, expectedSupertype, next, decodeUnit, resolution);
        }

        // --- [30]/[31] EXPLICIT collections ---
        if (n == TAG_CANONICAL_COLL || n == TAG_ORDERED_COLL) {
            if (!constructed) {
                throw new DerException("AnyCodec.decode: collection Any tag [" + n + "] must be "
                        + "constructed (EXPLICIT), got primitive (fail-secure)");
            }
            byte[] innerTlv = readExplicitInner(reader);
            // The inner SET OF (0x31) / SEQUENCE OF (0x30) outer tag survived the EXPLICIT wrap and
            // IS the discipline discriminator; cross-check it against the context tag so a [30] over
            // a SEQUENCE OF (or [31] over a SET OF) -- a mismatched/lying encoding -- is rejected
            // (fence (c)/(d)).
            Tag innerTag = new DerReader(innerTlv).peekTag();
            boolean innerCanonical = Tag.SET.equals(innerTag);
            boolean innerOrdered   = Tag.SEQUENCE.equals(innerTag);
            if (n == TAG_CANONICAL_COLL && !innerCanonical) {
                throw new DerException("AnyCodec.decode: canonicalCollection [30] inner tag must be "
                        + "SET OF (0x31), got " + innerTag + " (fail-secure)");
            }
            if (n == TAG_ORDERED_COLL && !innerOrdered) {
                throw new DerException("AnyCodec.decode: orderedCollection [31] inner tag must be "
                        + "SEQUENCE OF (0x30), got " + innerTag + " (fail-secure)");
            }
            // Fence (a): EVERY Any->collection step counts as one nesting level and is bounded
            // BEFORE recursing -- an Any-of-collection-of-Any-of-... chain is attacker-controlled
            // depth, so an unbounded recursion is a StackOverflow DoS.
            int next = requireDepth(depth, "collection");
            // The element type inside an Any collection is itself Any. Whether the inner collection
            // is a set/bag/map (canonicalise) or orderedset/list/orderedmap (preserve) is carried by
            // the inner 0x31/0x30 tag; decodeCollection reads that and applies the right order check.
            String token = innerCanonical ? probeCanonicalToken(innerTlv)
                                           : probeOrderedToken(innerTlv);
            return ObjectCodec.decodeCollection(innerTlv, token, next, decodeUnit, resolution);
        }

        // Fence (c): reserved gap ([10..19]/[21..29]) or any tag > [31] -> HARD REJECT.
        throw new DerException("AnyCodec.decode: unregistered/reserved Any context tag [" + n
                + "] (registry: [0..9] scalars, [20] object, [30]/[31] collections; "
                + "[10..19]/[21..29] reserved, > [31] unassigned) -- fail-secure hard reject");
    }

    /**
     * Fence (a): increments and bounds the nesting depth for one {@code Any}→collection /
     * {@code Any}→object recursion step. Returns {@code depth + 1}; rejects with a "nesting"
     * {@link DerException} <em>before</em> recursing when the bound {@link ObjectCodec#MAX_NESTING}
     * would be exceeded, so a hostile deeply-nested {@code Any} is stopped by the guard, never by
     * stack exhaustion.
     */
    private static int requireDepth(int depth, String what) throws DerException {
        int next = depth + 1;
        if (next > ObjectCodec.MAX_NESTING) {
            throw new DerException("AnyCodec.decode: Any " + what + " nesting depth " + next
                    + " exceeds MAX_NESTING (" + ObjectCodec.MAX_NESTING + ") -- fail-secure");
        }
        return next;
    }

    /**
     * Decodes a scalar {@code Any} value from the IMPLICIT content octets by re-tagging them under
     * the scalar's universal type and delegating to the ordinary strict-canonical scalar decoder
     * ({@code WireTypes} via {@link au.net.zeus.jgdms.der.getarg.DerFieldStore#decodeScalarElement}),
     * so {@code Any} scalars are validated <em>identically</em> to declared-type scalars (canonical
     * INTEGER minimality, IEEE-754 canonical float/double with {@code -0.0}/non-canonical-NaN
     * rejection, BMP-only char, integer overflow rejection).
     */
    private static Object decodeScalar(int ctxTag, byte[] content) throws DerException {
        Tag universalTag;
        String scalarWireType;
        switch (ctxTag) {
            case TAG_BOOLEAN -> { universalTag = Tag.BOOLEAN;      scalarWireType = "boolean"; }
            case TAG_BYTE    -> { universalTag = Tag.INTEGER;      scalarWireType = "byte"; }
            case TAG_SHORT   -> { universalTag = Tag.INTEGER;      scalarWireType = "short"; }
            case TAG_INT     -> { universalTag = Tag.INTEGER;      scalarWireType = "int"; }
            case TAG_LONG    -> { universalTag = Tag.INTEGER;      scalarWireType = "long"; }
            case TAG_FLOAT   -> { universalTag = Tag.OCTET_STRING; scalarWireType = "float"; }
            case TAG_DOUBLE  -> { universalTag = Tag.OCTET_STRING; scalarWireType = "double"; }
            case TAG_CHAR    -> { universalTag = Tag.INTEGER;      scalarWireType = "char"; }
            case TAG_STRING  -> { universalTag = Tag.UTF8STRING;   scalarWireType = "java.lang.String"; }
            case TAG_BYTES   -> { universalTag = Tag.OCTET_STRING; scalarWireType = "byte[]"; }
            default -> throw new DerException("AnyCodec: not a scalar Any tag: [" + ctxTag + "]");
        }
        // Rebuild the value's ordinary universal-tagged TLV and decode it with the SAME strict
        // scalar decoder used for declared-type scalar elements. ResolutionContext.NONE is fine:
        // scalars never load a class. This reuse guarantees Any scalars and declared scalars are
        // byte-for-byte value-equal (fence (d)) and validated by one code path.
        byte[] universalTlv = DerWriter.writeTlv(universalTag, content);
        DerReader r = new DerReader(universalTlv);
        Object v = au.net.zeus.jgdms.der.getarg.DerFieldStore
                .decodeScalarElement(r, scalarWireType, ResolutionContext.NONE);
        if (r.hasMore()) {
            throw new DerException("AnyCodec: trailing bytes in scalar Any [" + ctxTag + "] content");
        }
        return v;
    }

    /**
     * Reads a complete {@code [n] EXPLICIT} context TLV from {@code reader} and returns its single
     * inner TLV bytes (the wrapped value's own DER, its outer tag intact), advancing the cursor. The
     * EXPLICIT wrapper MUST contain exactly one inner TLV (fence (d): no trailing bytes).
     */
    private static byte[] readExplicitInner(DerReader reader) throws DerException {
        DerReader.TlvHeader hdr = reader.readTlvHeader();
        byte[] inner = reader.readRawContent(hdr.contentLength());
        // The inner content is exactly one TLV; verify no trailing bytes (a hidden second TLV would
        // be a non-canonical / ambiguous EXPLICIT wrapper).
        DerReader ir = new DerReader(inner);
        DerReader.TlvHeader innerHdr = ir.readTlvHeader();
        ir.readRawContent(innerHdr.contentLength());
        if (ir.hasMore()) {
            throw new DerException("AnyCodec.decode: EXPLICIT Any wrapper has trailing bytes after "
                    + "its single inner TLV (fail-secure)");
        }
        return inner;
    }

    /**
     * Reconstructs the concrete canonicalise collection token ({@code set:}/{@code bag:}/{@code map:})
     * for an inner SET-OF {@code Any} collection so {@link ObjectCodec#decodeCollection} applies the
     * correct order check. The inner encoding's shape (map entries vs plain elements) and the
     * canonicalise discipline are both intrinsic to the inner tag; the element type is {@code Any}.
     *
     * <p>Both {@code set:} and {@code bag:} decode identically here (strictly-ascending is stricter
     * than non-decreasing, but a canonically-encoded {@code Any} set never has duplicates, so
     * {@code set:} is the safe strict choice). A map (entries are inner {@code SEQUENCE{key,value}})
     * is distinguished structurally.
     */
    private static String probeCanonicalToken(byte[] innerTlv) throws DerException {
        return isMapShape(innerTlv, /*canonicalise*/ true)
                ? CollectionWireTypes.mapToken(ANY, ANY)
                : CollectionWireTypes.setToken(ANY);
    }

    /**
     * Reconstructs the concrete preserve collection token ({@code orderedset:}/{@code list:}/
     * {@code orderedmap:}) for an inner SEQUENCE-OF {@code Any} collection. Preserve disciplines
     * apply no order check, so {@code list:} (no set-duplicate rejection) is the safe permissive
     * choice for a non-map; a map is distinguished structurally.
     */
    private static String probeOrderedToken(byte[] innerTlv) throws DerException {
        return isMapShape(innerTlv, /*canonicalise*/ false)
                ? CollectionWireTypes.orderedMapToken(ANY, ANY)
                : CollectionWireTypes.listToken(ANY);
    }

    /**
     * Structurally distinguishes a map body (each element an inner {@code SEQUENCE{key,value}}) from
     * a set/list body (each element a bare value TLV).
     *
     * <p><b>Empty-collection boundary (documented + conformance-locked, F2).</b> An EMPTY collection
     * has no element to inspect, so it is <em>not</em> a map here and is resolved by the outer wire
     * tag alone: an empty canonicalise collection ({@code [30]}, an empty {@code SET OF}) decodes to
     * an empty {@code Set}; an empty ordered collection ({@code [31]}, an empty {@code SEQUENCE OF})
     * decodes to an empty {@code List}. An empty {@code Any} map, an empty {@code Any} set, and an
     * empty {@code Any} list are byte-identical on the wire for a given discipline (there is no
     * entry, key, or value to differ), so this resolution is a deterministic function of the tag,
     * not a decode quirk. The distinction is immaterial: an empty map / set / list are equivalently
     * empty, and the field's declared collection type coerces the container in Layer 2 (memo §8.2).
     * A NON-empty {@code Any} map is detected structurally (its entries are universal
     * {@code SEQUENCE}s) and round-trips as a {@code Map}.
     */
    private static boolean isMapShape(byte[] innerTlv, boolean canonicalise) throws DerException {
        DerReader outer = new DerReader(innerTlv);
        DerReader body = canonicalise ? outer.readSet() : outer.readSequence();
        if (!body.hasMore()) {
            return false; // empty -> canonicalise:[30]->Set, ordered:[31]->List (F2, deterministic)
        }
        Tag first = body.peekTag();
        // A map entry is an inner SEQUENCE{key,value} (0x30). A set/list element is an AnyElement,
        // whose leading tag is a CONTEXT tag (or DER NULL). So a universal SEQUENCE first element
        // means map; anything else (context tag, NULL) means set/list.
        return Tag.SEQUENCE.equals(first);
    }
}
