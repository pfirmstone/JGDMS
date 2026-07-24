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
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceCodec;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.ProxyWireSupport;
import au.net.zeus.jgdms.der.object.RawWireFormRetaining;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import java.util.Arrays;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.export.ProxyAccessor;
import net.jini.io.context.DeserializationCompletion;
import net.jini.security.Security;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.DeSerializationPermission;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Engine for DER object-stream encoding and decoding (JGDMS-STD-008 sec.15.5,
 * Increment 1; boxed-scalar top-level items sec.15.2.1).
 *
 * <h2>Context-tag scheme for self-describing object items</h2>
 * <p>
 * Each object written through {@link #writeObject} / read through
 * {@link #readObject} is a context-specific TLV whose tag number encodes the
 * item kind:
 * <pre>
 *   [0]  PRIMITIVE   -- NULL reference (length 0)
 *   [1]  CONSTRUCTED -- @AtomicSerial object; content = MarshalledInstanceRecord DER SEQUENCE
 *   [2]  PRIMITIVE   -- boxed java.lang.Boolean; content = canonical BOOLEAN octet (0x00/0xFF)
 *   [3]  PRIMITIVE   -- java.lang.String; content = UTF8String value bytes
 *   [4]  PRIMITIVE   -- boxed java.lang.Byte; content = canonical minimal INTEGER content
 *   [5]  PRIMITIVE   -- byte[]; content = OCTET STRING value bytes
 *   [6]  PRIMITIVE   -- boxed java.lang.Short; content = canonical minimal INTEGER content
 *   [7]  CONSTRUCTED -- enum constant; content = UTF8String(declaringClassName) ++ UTF8String(constantName)
 *   [8]  CONSTRUCTED -- bare java.lang.reflect.Proxy; content = INTEGER ifaceCount ++ ifaceName(UTF8String)* ++ [1] @AtomicSerial InvocationHandler
 *   [9]  CONSTRUCTED -- top-level value array; content = UTF8String(arrayWireType) ++ element SEQUENCE
 *   [10] PRIMITIVE   -- boxed java.lang.Integer; content = canonical minimal INTEGER content
 *   [11] PRIMITIVE   -- boxed java.lang.Long; content = canonical minimal INTEGER content
 *   [12] PRIMITIVE   -- boxed java.lang.Float; content = canonical 4-byte IEEE-754 OCTET STRING content
 *   [13] PRIMITIVE   -- boxed java.lang.Double; content = canonical 8-byte IEEE-754 OCTET STRING content
 *   [14] PRIMITIVE   -- boxed java.lang.Character; content = canonical Unicode codepoint INTEGER content
 *   [15] PRIMITIVE   -- stream-format version octet (8F 01 01) -- NOT an object item: the
 *                       mandatory FIRST TLV of every object stream (STD-006 Appendix C
 *                       sec.C.5.2); anywhere else it is rejected by the readObject catch-all
 * </pre>
 *
 * <h2>Stream format: version octet + mandatory schema-chain dedup (STD-006 Appendix C)</h2>
 * <p>
 * Every DER object stream begins with the {@code [15]} stream-format version octet
 * ({@code 8F 01 01}); a stream that does not (including the superseded pre-dedup trunk
 * format) or that names an unknown version is hard-rejected at the first TLV. At every
 * chain site ([1] items, nested {@code @AtomicSerial} records, [9] {@code @AtomicSerial}
 * array elements) the stream carries the Appendix C dedup productions: the first
 * occurrence of a schema-chain identity travels as {@code fullChain [0]}, every
 * subsequent occurrence as a 32-byte {@code chainRef [1]} — unconditionally (dedup is
 * the format, not a mode, sec.C.9). {@link StreamSchemaDedup} holds the per-stream
 * tables and the transform; every {@code [8]} proxy interior is excluded byte-region-wide
 * (sec.C.6.5), and the record-level capture context ({@code streamFormat = false},
 * sec.C.1.2 item 3) uses neither the version octet nor the dedup productions.
 * Context class = 0x80 (primitive) / 0xA0 (constructed); constructed bit set only for
 * [1], [7], [8], [9]. Single-byte tags: [0]-&gt;0x80, [1]-&gt;0xa1, [2]-&gt;0x82, [3]-&gt;0x83,
 * [4]-&gt;0x84, [5]-&gt;0x85, [6]-&gt;0x86, [7]-&gt;0xa7, [8]-&gt;0xa8, [9]-&gt;0xa9, [10]-&gt;0x8a,
 * [11]-&gt;0x8b, [12]-&gt;0x8c, [13]-&gt;0x8d, [14]-&gt;0x8e.
 * (A {@code java.lang.reflect.Proxy} whose interfaces + {@code @AtomicSerial} handler are
 * locally resolvable is transmitted bare as [8]; one that needs a codebase download is
 * instead substituted by a {@code ProxySerializer} and rides the [1] path, per STD-008 sec.15.2.
 * Interface names in a [8] item are resolved TOLERANTLY: a name that fails to resolve locally is
 * dropped rather than failing the whole item, provided at least one interface still resolves --
 * see {@link au.net.zeus.jgdms.der.object.ProxyWireSupport} and
 * {@link au.net.zeus.jgdms.der.object.RawWireFormRetaining}, which also preserve the full
 * original wire bytes for a byte-for-byte-faithful later re-forward of a narrowed proxy.)
 *
 * <h3>Boxed-scalar tag allocation (STD-008 sec.15.2.1)</h3>
 * <p>
 * [2]/[4]/[6] fill the gaps the original [0]/[1]/[3]/[5]/[7]/[8]/[9] allocation left
 * unused; [10]-[14] is a contiguous run for the remaining five boxed types once the gaps
 * ran out. Eight DISTINCT tags are used -- one per boxed type -- rather than a single
 * shared "boxed primitive" tag, because {@code byte}/{@code short}/{@code int}/
 * {@code long} all encode on the wire as the identical DER INTEGER content and the
 * context tag is the ONLY type carrier at top level (there is no schema to consult): a
 * boxed {@code Integer} MUST decode back as an {@code Integer}, never silently as a
 * {@code Long} or {@code Short} of the same numeric value (board guidance sec.2.1 H2 --
 * distinct-tag discipline). All eight are PRIMITIVE (non-constructed) tags: a boxed
 * scalar carries a single inert value, never a nested TLV structure.
 * <p>
 * <b>This is a DIFFERENT namespace from STD-006 sec.3.12's {@code AnyElement} scalar
 * tags.</b> {@code AnyElement CHOICE} (the {@code ObjectCodec}/field-level polymorphic-slot
 * scheme) also numbers its scalar arms [0]-[9] -- a completely separate grammar, decoded
 * only for a polymorphic <em>field</em> value nested inside an already-identified
 * {@code @AtomicSerial} record's own payload bytes, never for a top-level stream item.
 * The two numeric spaces happen to overlap (e.g. this class's [2] boxed-{@code Boolean}
 * vs. {@code AnyElement}'s {@code scalarShort [2]}) but no decoder ever reads one
 * grammar's tag against the other's meaning: {@link #readObject} only ever dispatches on
 * the outermost item of a {@code writeObject}/{@code readObject} call; {@code AnyElement}
 * bytes only ever appear inside a payload already routed there by {@code ObjectCodec}'s
 * own decoder.
 *
 * <h2>No handle table -- pure value-tree, deterministic (STD-008 sec.15.3)</h2>
 * <p>
 * There is NO handle table and NO back-reference: every object occurrence is encoded in
 * full, by VALUE. This keeps the stream a deterministic (canonical-DER) function of the
 * argument values rather than of object identity or write order, and it carries no
 * aliasing. It loses nothing real -- {@code @AtomicSerial} deserialization defensively
 * copies and re-checks invariants per object, so shared identity is never preserved across
 * the boundary anyway (a deliberate security property). Reference cycles are consequently
 * impossible to express. A back-reference-style context tag is not part of the grammar:
 * any tag number this class does not explicitly recognise above (including a would-be
 * back-reference marker) is rejected fail-secure by {@link #readObject}'s final catch-all.
 */
final class DerObjectStreamCodec {

    // =========================================================================
    // Context tags (single byte, pre-computed)
    // =========================================================================

    /** [0] primitive context tag: NULL reference. */
    private static final Tag CTX_NULL        = new Tag(Tag.CLASS_CONTEXT, false, 0);
    /** [1] constructed context tag: @AtomicSerial object (MarshalledInstanceRecord). */
    private static final Tag CTX_ATOMIC      = new Tag(Tag.CLASS_CONTEXT, true,  1);
    /** [3] primitive context tag: java.lang.String (UTF8String content). */
    private static final Tag CTX_STRING      = new Tag(Tag.CLASS_CONTEXT, false, 3);
    /** [5] primitive context tag: byte[] (OCTET STRING content). */
    private static final Tag CTX_BYTES       = new Tag(Tag.CLASS_CONTEXT, false, 5);
    /** [7] constructed context tag: enum; content = UTF8String(declaringClass) ++ UTF8String(name). */
    private static final Tag CTX_ENUM        = new Tag(Tag.CLASS_CONTEXT, true,  7);
    /** [8] constructed context tag: bare java.lang.reflect.Proxy (interface names + @AtomicSerial handler). */
    private static final Tag CTX_PROXY       = new Tag(Tag.CLASS_CONTEXT, true,  8);
    /** [9] constructed context tag: top-level value array (UTF8 componentWireType + element SEQUENCE). */
    private static final Tag CTX_ARRAY       = new Tag(Tag.CLASS_CONTEXT, true,  9);

    // -- Boxed scalar top-level items (STD-008 sec.15.2.1; see class javadoc "Boxed-scalar
    // tag allocation" for why eight DISTINCT tags, not one shared tag). All PRIMITIVE
    // (non-constructed): a boxed scalar is a single inert value, never a nested TLV. Each
    // reuses the SAME canonical content the corresponding typed-primitive channel already
    // produces (writeBoolean/writeInteger/writeFloat/writeDouble/writeChar below), so a
    // value has exactly one wire encoding whether it travels boxed (top-level) or as a
    // declared-type field (one encoding per value -- board guidance sec.2.1 H1).
    /** [2] primitive context tag: boxed java.lang.Boolean; content = canonical BOOLEAN octet (0x00/0xFF). */
    private static final Tag CTX_BOOLEAN     = new Tag(Tag.CLASS_CONTEXT, false, 2);
    /** [4] primitive context tag: boxed java.lang.Byte; content = canonical minimal INTEGER content. */
    private static final Tag CTX_BYTE        = new Tag(Tag.CLASS_CONTEXT, false, 4);
    /** [6] primitive context tag: boxed java.lang.Short; content = canonical minimal INTEGER content. */
    private static final Tag CTX_SHORT       = new Tag(Tag.CLASS_CONTEXT, false, 6);
    /** [10] primitive context tag: boxed java.lang.Integer; content = canonical minimal INTEGER content. */
    private static final Tag CTX_INTEGER     = new Tag(Tag.CLASS_CONTEXT, false, 10);
    /** [11] primitive context tag: boxed java.lang.Long; content = canonical minimal INTEGER content. */
    private static final Tag CTX_LONG        = new Tag(Tag.CLASS_CONTEXT, false, 11);
    /** [12] primitive context tag: boxed java.lang.Float; content = canonical 4-byte IEEE-754 OCTET STRING content. */
    private static final Tag CTX_FLOAT       = new Tag(Tag.CLASS_CONTEXT, false, 12);
    /** [13] primitive context tag: boxed java.lang.Double; content = canonical 8-byte IEEE-754 OCTET STRING content. */
    private static final Tag CTX_DOUBLE      = new Tag(Tag.CLASS_CONTEXT, false, 13);
    /** [14] primitive context tag: boxed java.lang.Character; content = canonical Unicode codepoint INTEGER content. */
    private static final Tag CTX_CHARACTER   = new Tag(Tag.CLASS_CONTEXT, false, 14);

    /** {@link DeSerializationPermission}("PROXY") required to reconstruct a [8] proxy (mirrors JOSS). */
    private static final Permission PROXY_PERM = new DeSerializationPermission("PROXY");

    // =========================================================================
    // Write side state
    // =========================================================================

    /** Output accumulator for the write side. */
    private final List<byte[]> writeBuffer = new ArrayList<>();

    /**
     * Write-side proxy-substitution config. When {@code substituteProxies} is set (via
     * {@link #initWriter}), a downloadable proxy ({@link DynamicProxyCodebaseAccessor} /
     * {@link ProxyAccessor}) written through {@link #writeObject} is substituted with a
     * {@code DerProxySerializer} carrier (the object-stream counterpart of
     * {@code AtomicMarshalOutputStream.defaultReplaceObject}, used on the JERI invocation
     * arg/return path). Default OFF -- a plain object stream (e.g. the bare serviceProxy inside
     * a carrier) must NOT re-substitute. {@code writeStreamLoader} gates {@code create()}'s
     * {@code ProxyCodebaseSpi.substitute()} check; it is held only here on the trusted write side.
     */
    private boolean substituteProxies = false;
    private ClassLoader writeStreamLoader = null;
    private Collection<?> writeContext = Collections.emptyList();

    // =========================================================================
    // Read side state
    // =========================================================================

    /** DER reader over the input bytes (read side). */
    private DerReader reader;

    /**
     * Completion sink for the current decode unit, threaded into the constructed
     * {@code DerGetArg}s so that a decoded DGC live reference can register its batched
     * {@code dirty} on the per-decode-unit token. May be {@code null} (e.g. a standalone
     * MarshalledInstance decode with no DGC context).
     */
    private DeserializationCompletion decodeUnit;

    /**
     * Endpoint-assigned resolution context for class names in the stream ([7] enum, [8] proxy,
     * [1] {@code @AtomicSerial}). Defaults to {@link ResolutionContext#NONE}; the
     * MarshalledInstance/JERI entry points set the real endpoint loaders via
     * {@link #initReader(byte[], DeserializationCompletion, ResolutionContext)}. Holds a
     * capability (the loader) and is therefore never exposed beyond this trusted codec.
     */
    private ResolutionContext resolution = ResolutionContext.NONE;

    // =========================================================================
    // Stream-format / dedup state (STD-006 Appendix C — mandatory in the released
    // DER object-stream format)
    // =========================================================================

    /**
     * Whether this codec speaks the DER <b>object-stream format</b> (STD-006
     * Appendix C): the stream begins with the mandatory {@code [15]} stream-format
     * version octet ({@code 8F 01 01}, sec.C.5.2) and every chain site uses the
     * mandatory per-stream schema-chain dedup productions (sec.C.5/C.6/C.9).
     * {@code false} ONLY for the record-level capture context (the standalone
     * {@code MarshalledInstance} capture path, sec.C.1.2 item 3) — which is not an
     * object stream and MUST NOT use stream productions: no version octet, no
     * {@code SchemaChainRef}, canonical record-level full forms only. This is not a
     * negotiated mode of the stream format (there is none, sec.C.9.1); it is the
     * wire-only scope boundary.
     */
    private final boolean streamFormat;

    /** Encode-direction per-stream dedup state; non-null iff {@link #streamFormat}. */
    private final StreamSchemaDedup encodeDedup;

    /**
     * Decode-direction per-stream dedup state; created by {@link #initReader} when
     * {@link #streamFormat} — one table per stream, discarded with this codec
     * (sec.C.7.1/C.7.2).
     */
    private StreamSchemaDedup decodeDedup;

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Creates a fresh object-stream codec (stream format: version octet + mandatory
     * dedup); initialise read side later via {@link #initReader}.
     */
    DerObjectStreamCodec() {
        this(true);
    }

    /**
     * Creates a fresh codec, selecting between the object-stream format
     * ({@code streamFormat = true}) and the record-level capture context
     * ({@code streamFormat = false} — see {@link #streamFormat}).
     */
    DerObjectStreamCodec(boolean streamFormat) {
        this.streamFormat = streamFormat;
        if (streamFormat) {
            // sec.C.5.2: the version TLV is the FIRST TLV of every stream — seeded
            // before any item or positional primitive can be buffered. (Harmless for
            // a read-only codec: its write buffer is never drained.)
            writeBuffer.add(StreamSchemaDedup.versionTlv());
            this.encodeDedup = new StreamSchemaDedup(true);
        } else {
            this.encodeDedup = null;
        }
    }

    /**
     * Enables write-side substitution of a downloadable top-level proxy ({@link
     * DynamicProxyCodebaseAccessor} / {@link ProxyAccessor}) with a {@code DerProxySerializer}
     * carrier (the JERI invocation arg/return path). Without this, a proxy travels bare ([8]) and
     * must be locally resolvable.
     *
     * @param context     the stream context collection (must not be {@code null})
     * @param streamLoader the stream loader gating the {@code ProxyCodebaseSpi.substitute()} check
     *                     (the client proxy loader, or the dispatcher's stream loader); may be {@code null}
     */
    void initWriter(Collection<?> context, ClassLoader streamLoader) {
        this.writeContext = Objects.requireNonNull(context, "context");
        this.writeStreamLoader = streamLoader;
        this.substituteProxies = true;
    }

    /** Initialises the read side over a complete DER byte array (no decode-unit token). */
    void initReader(byte[] buf) throws IOException {
        initReader(buf, null);
    }

    /**
     * Initialises the read side over a complete DER byte array, threading the given
     * decode-unit completion token into every {@code DerGetArg} constructed during decode.
     *
     * @param buf        the complete DER byte array (must not be {@code null})
     * @param decodeUnit the per-decode-unit completion sink, or {@code null}
     */
    void initReader(byte[] buf, DeserializationCompletion decodeUnit) throws IOException {
        initReader(buf, decodeUnit, ResolutionContext.NONE);
    }

    /**
     * Initialises the read side, additionally carrying the endpoint-assigned
     * {@link ResolutionContext} used to resolve class names against the endpoint's loader (NOT
     * the thread-context loader -- the Warres ambient-resolution failure).
     *
     * @param buf        the complete DER byte array (must not be {@code null})
     * @param decodeUnit the per-decode-unit completion sink, or {@code null}
     * @param resolution the endpoint-assigned resolution context (must not be {@code null})
     */
    void initReader(byte[] buf, DeserializationCompletion decodeUnit, ResolutionContext resolution)
            throws IOException {
        Objects.requireNonNull(buf, "buf");
        this.reader = new DerReader(buf);
        this.decodeUnit = decodeUnit;
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        if (streamFormat) {
            // sec.C.5.2: every stream MUST begin with the stream-format version octet
            // (8F 01 01). Absence — including the superseded trunk format, whose first
            // TLV is an item tag — or an unknown version is a hard reject; there is no
            // unversioned form and no fallback. Consuming it here also creates this
            // stream's decode-side dedup table (sec.C.7.2: table created at stream open).
            consumeVersionOctet();
            this.decodeDedup = new StreamSchemaDedup(false);
        }
    }

    /** Verifies and consumes the mandatory {@code [15]} stream-format version octet. */
    private void consumeVersionOctet() throws IOException {
        DerReader.TlvHeader hdr;
        try {
            hdr = reader.readTlvHeader();
        } catch (DerException e) {
            throw new IOException(
                    "DER object stream: cannot read the stream-format version octet"
                    + " (STD-006 Appendix C sec.C.5.2): " + e.getMessage(), e);
        }
        if (!StreamSchemaDedup.TAG_VERSION.equals(hdr.tag())) {
            throw new IOException(
                    "DER object stream: does not begin with the mandatory [15] stream-format"
                    + " version octet (8F 01 01) — first TLV is " + hdr.tag()
                    + "; unversioned (superseded-trunk-format) streams are rejected"
                    + " (STD-006 Appendix C sec.C.5.2)");
        }
        if (hdr.contentLength() != 1) {
            throw new IOException(
                    "DER object stream: stream-format version octet content length must be 1,"
                    + " got " + hdr.contentLength() + " (STD-006 Appendix C sec.C.5.2)");
        }
        byte[] content;
        try {
            content = reader.readRawContent(1);
        } catch (DerException e) {
            throw new IOException(
                    "DER object stream: truncated stream-format version octet", e);
        }
        int version = content[0] & 0xFF;
        if (version != StreamSchemaDedup.STREAM_FORMAT_VERSION) {
            throw new IOException(
                    "DER object stream: unknown stream-format version 0x"
                    + String.format("%02X", version) + " (this implementation speaks version 0x"
                    + String.format("%02X", StreamSchemaDedup.STREAM_FORMAT_VERSION)
                    + " only) — hard reject, no fallback (STD-006 Appendix C sec.C.5.2)");
        }
    }

    // =========================================================================
    // Write side: typed primitives
    // =========================================================================

    void writeBoolean(boolean v) {
        writeBuffer.add(DerWriter.writeBoolean(v));
    }

    void writeByte(int v) {
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf((byte) v)));
    }

    void writeShort(int v) {
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf((short) v)));
    }

    void writeInt(int v) {
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf(v)));
    }

    void writeLong(long v) {
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf(v)));
    }

    /**
     * STD-008 sec.17.3.1: IEEE-754 in 4-byte OCTET STRING with canonical NaN and
     * canonical {@code +0.0}. {@code -0.0} is mapped to {@code +0.0} on encode.
     */
    void writeFloat(float v) {
        writeBuffer.add(DerWriter.writeOctetString(encodeCanonicalFloatContent(v)));
    }

    /**
     * STD-008 sec.17.3.1: IEEE-754 in 8-byte OCTET STRING with canonical NaN and
     * canonical {@code +0.0}. {@code -0.0} is mapped to {@code +0.0} on encode.
     */
    void writeDouble(double v) {
        writeBuffer.add(DerWriter.writeOctetString(encodeCanonicalDoubleContent(v)));
    }

    /**
     * STD-008 sec.17.3.2: Unicode codepoint INTEGER. Surrogate code units rejected.
     * (Note: {@link java.io.ObjectOutput#writeChar(int)} takes an {@code int}; we treat
     * the low 16 bits as the char value, matching {@link DataOutput#writeChar}.)
     */
    void writeChar(int v) {
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf(validatedCodepoint(v))));
    }

    /**
     * STD-008 sec.17.3.1 canonical 4-byte IEEE-754 content bytes for {@code v}: canonical
     * NaN ({@code 0x7FC00000}), {@code -0.0} mapped to {@code +0.0}. Shared by the typed
     * {@link #writeFloat} channel and the boxed {@code java.lang.Float} top-level item
     * (sec.15.2.1) so the two never diverge on the same value (H1 -- one encoding per value).
     */
    private static byte[] encodeCanonicalFloatContent(float v) {
        int bits;
        if (Float.isNaN(v))                                bits = 0x7FC00000;
        else if (Float.floatToRawIntBits(v) == 0x80000000) bits = 0x00000000;
        else                                                bits = Float.floatToRawIntBits(v);
        return new byte[] {
                (byte)(bits >>> 24), (byte)(bits >>> 16),
                (byte)(bits >>>  8), (byte) bits
        };
    }

    /**
     * STD-008 sec.17.3.1 canonical 8-byte IEEE-754 content bytes for {@code v}: canonical
     * NaN ({@code 0x7FF8000000000000}), {@code -0.0} mapped to {@code +0.0}. Shared by the
     * typed {@link #writeDouble} channel and the boxed {@code java.lang.Double} top-level
     * item (sec.15.2.1).
     */
    private static byte[] encodeCanonicalDoubleContent(double v) {
        long bits;
        if (Double.isNaN(v))                                           bits = 0x7FF8000000000000L;
        else if (Double.doubleToRawLongBits(v) == 0x8000000000000000L) bits = 0x0000000000000000L;
        else                                                             bits = Double.doubleToRawLongBits(v);
        byte[] content = new byte[8];
        for (int i = 7; i >= 0; i--) { content[i] = (byte)(bits & 0xFF); bits >>>= 8; }
        return content;
    }

    /**
     * STD-008 sec.17.3.2 codepoint validation (BMP, non-surrogate) shared by
     * {@link #writeChar} and the boxed {@code java.lang.Character} top-level item
     * (sec.15.2.1). Treats the low 16 bits of {@code v} as the char value, matching
     * {@link DataOutput#writeChar}.
     *
     * @throws IllegalArgumentException if the codepoint is an unpaired surrogate
     */
    private static int validatedCodepoint(int v) {
        int cp = v & 0xFFFF;
        if (cp >= 0xD800 && cp <= 0xDFFF) {
            throw new IllegalArgumentException(
                    "DER stream: unpaired surrogate code unit 0x"
                    + Integer.toHexString(cp).toUpperCase()
                    + " is not a valid Unicode codepoint (STD-008 sec.17.3.2)");
        }
        return cp;
    }

    void writeUTF(String s) {
        Objects.requireNonNull(s, "s");
        writeBuffer.add(DerWriter.writeUtf8String(s));
    }

    void writeBytes(byte[] b, int off, int len) {
        byte[] content = new byte[len];
        System.arraycopy(b, off, content, 0, len);
        writeBuffer.add(DerWriter.writeOctetString(content));
    }

    void writeSingleByte(int b) {
        // write(int) from ObjectOutputStream -- writes a single byte as an INTEGER
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf((byte)(b & 0xFF))));
    }

    // =========================================================================
    // Write side: objects
    // =========================================================================

    /**
     * Encodes an object item and appends it to the write buffer.
     *
     * <ul>
     *   <li>null -> [0] NULL</li>
     *   <li>@AtomicSerial instance -> [1] MarshalledInstanceRecord (full, every occurrence)</li>
     *   <li>String -> [3]</li>
     *   <li>byte[] -> [5]</li>
     *   <li>non-byte[] value array -> [9]</li>
     *   <li>enum constant -> [7]</li>
     *   <li>bare java.lang.reflect.Proxy -> [8]</li>
     *   <li>boxed Boolean/Byte/Short/Integer/Long/Float/Double/Character -- inert scalar
     *       VALUES, no object graph, no gadget surface (sec.15.2.1) -> [2]/[4]/[6]/[10]/
     *       [11]/[12]/[13]/[14] respectively, one distinct tag per type (H2)</li>
     *   <li>anything else -> UnsupportedOperationException (fail-secure; this restriction
     *       is deliberately NOT relaxed for arbitrary Serializable/Object graphs -- only
     *       the inert value-kinds above are admitted)</li>
     * </ul>
     */
    void writeObject(Object obj) throws IOException {
        // Write seam (JERI invocation arg/return path): substitute a downloadable proxy
        // (DynamicProxyCodebaseAccessor / ProxyAccessor) with a DerProxySerializer carrier so it
        // carries its bootstrap + codebase for download, riding the [1] @AtomicSerial path -- the
        // object-stream counterpart of AtomicMarshalOutputStream.defaultReplaceObject. With no
        // registered ProxyCodebaseSpi, create() returns the proxy unchanged and it travels bare
        // ([8]) below. OFF by default (a bare serviceProxy inside a carrier must NOT re-substitute).
        if (substituteProxies && obj != null) {
            if (obj instanceof DynamicProxyCodebaseAccessor dpca) {
                obj = ProxyWireSupport.substituteDownloadableProxy(dpca, writeStreamLoader, writeContext);
            } else if (obj instanceof ProxyAccessor pa) {
                obj = ProxyWireSupport.substituteDownloadableProxy(pa, writeStreamLoader, writeContext);
            }
        }
        if (obj == null) {
            writeBuffer.add(DerWriter.writeTlv(CTX_NULL, new byte[0]));
            return;
        }

        // String: value semantics, no handle table
        if (obj instanceof String s) {
            byte[] strBytes = s.getBytes(StandardCharsets.UTF_8);
            writeBuffer.add(DerWriter.writeTlv(CTX_STRING, strBytes));
            return;
        }

        // byte[]: value semantics, no handle table
        if (obj instanceof byte[] bytes) {
            writeBuffer.add(DerWriter.writeTlv(CTX_BYTES, bytes));
            return;
        }

        // Non-byte[] value array (primitive/String/enum/@AtomicSerial component) -> [9] CTX_ARRAY.
        // Arrays are VALUES, not @AtomicSerial objects (byte[] handled above as OCTET STRING).
        if (obj.getClass().isArray()) {
            try {
                byte[] content = ObjectCodec.encodeTopLevelArray(obj);
                if (streamFormat) {
                    // Appendix C: @AtomicSerial elements of a [9] array are P2 chain
                    // sites and dedup like every other site (sec.C.4.2 P2c).
                    content = encodeDedup.dedupTopLevelArray(content);
                }
                writeBuffer.add(DerWriter.writeTlv(CTX_ARRAY, content));
            } catch (DerException e) {
                throw new IOException("DER stream [9] array encode failed for "
                        + obj.getClass().getName(), e);
            }
            return;
        }

        // enum: STD-008 sec.15.2 [7] / sec.17.1 -- declaring class + constant name, by value.
        // Use getDeclaringClass(), NOT getClass() (which is the constant-body subclass for
        // constants with bodies, e.g. Op.ADD -> Op$1).
        if (obj instanceof Enum<?> e) {
            byte[] clsName = DerWriter.writeUtf8String(e.getDeclaringClass().getName());
            byte[] name    = DerWriter.writeUtf8String(e.name());
            byte[] content = new byte[clsName.length + name.length];
            System.arraycopy(clsName, 0, content, 0, clsName.length);
            System.arraycopy(name, 0, content, clsName.length, name.length);
            writeBuffer.add(DerWriter.writeTlv(CTX_ENUM, content));
            return;
        }

        // bare java.lang.reflect.Proxy -> [8]: interface names + the @AtomicSerial InvocationHandler,
        // reconstructed via Proxy.newProxyInstance. No ProxySerializer/bootstrap/codebase -- the
        // interfaces + handler must be locally resolvable on the receiver (sec.15.2).
        //
        // If obj's live handler RETAINS a raw [8] wire form (this node itself decoded obj from a
        // [8] item that dropped >=1 interface it couldn't resolve locally -- see readObject
        // below), re-emit those RETAINED ORIGINAL wire bytes verbatim, byte-for-byte, rather than
        // re-deriving fresh bytes from obj.getClass().getInterfaces() (which only shows the
        // narrowed runtime set this node built, and would also forfeit the sender's
        // @AtomicSerial-validated integrity guarantee -- see RawWireFormRetaining). This raw-bytes
        // branch runs FIRST and returns before the normal handler re-serialization below (which
        // would drop the handler's transient rawForm). Otherwise -- the common case, nothing was
        // ever dropped -- behaviour is unchanged: encode fresh.
        //
        // SCOPE OF THE RETENTION GUARANTEE (fence-scope boundary): this bare-[8] proxy encode path
        // is where retained-bytes verbatim re-emission holds. It does NOT cover a proxy diverted
        // EARLIER by the smart-proxy substitution branch above (the substituteProxies block, ~30
        // lines up), which is OFF by default: if that (default-off) path re-homes a proxy as a
        // downloadable DerProxySerializer/[1] carrier before reaching here, any rawForm it carried
        // is not relayed and that hop re-derives non-verbatim. That is intentional -- such a proxy
        // is being deliberately re-homed as a codebase proxy, where byte-for-byte relay of the
        // original bare-[8] form is not the intended behaviour -- and near-unreachable (a
        // bare-[8]-decoded proxy that is ALSO a substitutable ProxyAccessor is a contradictory
        // shape in normal operation). Impact is fidelity loss only, never a self-check bypass or
        // authz change. See RawWireFormRetaining's javadoc and STD-009 sec.6.4.
        if (Proxy.isProxyClass(obj.getClass())) {
            byte[] retained = ProxyWireSupport.wireContentForBoomerang(obj);
            if (retained != null) {
                writeBuffer.add(DerWriter.writeTlv(CTX_PROXY, retained));
                return;
            }
            Class<?>[] ifaces = obj.getClass().getInterfaces();
            if (ifaces.length == 0 || ifaces.length > ProxyWireSupport.MAX_PROXY_INTERFACES) {
                throw new IOException("DER stream [8] proxy: interface count " + ifaces.length
                        + " out of range (1.." + ProxyWireSupport.MAX_PROXY_INTERFACES + ")");
            }
            InvocationHandler h = Proxy.getInvocationHandler(obj);
            if (!h.getClass().isAnnotationPresent(AtomicSerial.class)) {
                throw new UnsupportedOperationException(
                        "DER stream [8] proxy: InvocationHandler "
                        + h.getClass().getName() + " is not @AtomicSerial");
            }
            java.io.ByteArrayOutputStream content = new java.io.ByteArrayOutputStream();
            content.writeBytes(DerWriter.writeInteger(BigInteger.valueOf(ifaces.length)));
            for (Class<?> iface : ifaces) {
                content.writeBytes(DerWriter.writeUtf8String(iface.getName()));
            }
            try {
                content.writeBytes(DerWriter.writeTlv(CTX_ATOMIC, encodeAtomicRecord(h)));
            } catch (DerException e) {
                throw new IOException("DER stream [8] proxy: handler encode failed", e);
            }
            writeBuffer.add(DerWriter.writeTlv(CTX_PROXY, content.toByteArray()));
            return;
        }

        // Boxed scalar VALUES (STD-008 sec.15.2.1 -- this increment unblocks
        // SOW-Entry-ATOMIC-DER-Migration.md A1: Outrigger wraps each Entry field in its own
        // top-level MarshalledInstance, so a boxed-primitive-typed field reaches writeObject
        // as a top-level item; Reggie has the identical shipped gap). A boxed
        // Boolean/Byte/Short/Integer/Long/Float/Double/Character carries no object graph, no
        // constructor to run on decode beyond e.g. Integer.valueOf (no attacker-influenced
        // work), and no readObject/gadget surface -- the SAME admission category already
        // granted to String ([3]) and byte[] ([5]) above, just extended to the remaining
        // scalar VALUE types. This is a DELIBERATE, NARROW relaxation of the
        // @AtomicSerial-restricted fallthrough below to admit inert scalar values ONLY -- it
        // does NOT admit arbitrary Serializable or Object graphs; anything that is not one
        // of the named value-kinds still falls through to, and fails, that restriction.
        //
        // Each type gets its OWN distinct context tag (never a shared "boxed primitive" tag)
        // so a boxed Integer can never decode as a Long of the same numeric value (H2 --
        // distinct-tag discipline; see class javadoc "Boxed-scalar tag allocation"). The
        // encoding reuses the IDENTICAL typed-primitive canonical content
        // (writeBoolean/writeInteger/writeFloat/writeDouble/writeChar above), so a value has
        // exactly one wire encoding whether it travels boxed (top-level) or as a
        // declared-type field (H1) -- required for Outrigger's byte-compare entry matching.
        if (obj instanceof Boolean b) {
            writeBuffer.add(DerWriter.writeTlv(CTX_BOOLEAN,
                    new byte[] { b ? (byte) 0xFF : (byte) 0x00 }));
            return;
        }
        if (obj instanceof Byte by) {
            writeBuffer.add(DerWriter.writeTlv(CTX_BYTE, BigInteger.valueOf(by).toByteArray()));
            return;
        }
        if (obj instanceof Short sh) {
            writeBuffer.add(DerWriter.writeTlv(CTX_SHORT, BigInteger.valueOf(sh).toByteArray()));
            return;
        }
        if (obj instanceof Integer iv) {
            writeBuffer.add(DerWriter.writeTlv(CTX_INTEGER, BigInteger.valueOf(iv).toByteArray()));
            return;
        }
        if (obj instanceof Long lg) {
            writeBuffer.add(DerWriter.writeTlv(CTX_LONG, BigInteger.valueOf(lg).toByteArray()));
            return;
        }
        if (obj instanceof Float f) {
            writeBuffer.add(DerWriter.writeTlv(CTX_FLOAT, encodeCanonicalFloatContent(f)));
            return;
        }
        if (obj instanceof Double d) {
            writeBuffer.add(DerWriter.writeTlv(CTX_DOUBLE, encodeCanonicalDoubleContent(d)));
            return;
        }
        if (obj instanceof Character c) {
            int cp = validatedCodepoint(c);
            writeBuffer.add(DerWriter.writeTlv(CTX_CHARACTER, BigInteger.valueOf(cp).toByteArray()));
            return;
        }

        // @AtomicSerial object -> a full record, EVERY occurrence (no handle table; sec.15.3).
        // Encoded by VALUE so the stream is a deterministic (canonical-DER) function of values,
        // not object identity/order; @AtomicSerial deserialization copies + re-checks invariants
        // per object, so shared identity is not preserved across the boundary anyway.
        Class<?> cls = obj.getClass();
        if (!cls.isAnnotationPresent(AtomicSerial.class)) {
            throw new UnsupportedOperationException(
                    "DER stream inc-1: unsupported object type "
                    + cls.getName()
                    + "; @AtomicSerial-restricted");
        }

        try {
            byte[] rec = encodeAtomicRecord(obj);
            if (streamFormat) {
                // Appendix C: the canonical sec.7.8 record becomes the stream-form
                // DedupMarshalledInstanceRecord — this record's chain site and every
                // P2 site in its payload interior, deduplicated in pre-order under the
                // pinned first-full-then-reference rule (sec.C.5.3/C.6). Unconditional:
                // dedup is the stream format, not a mode (sec.C.9).
                rec = encodeDedup.dedupTopLevelAtomic(rec);
            }
            writeBuffer.add(DerWriter.writeTlv(CTX_ATOMIC, rec));
        } catch (DerException e) {
            throw new IOException("DER encode failed for " + cls.getName(), e);
        }
    }

    // =========================================================================
    // Read side: typed primitives
    // =========================================================================

    boolean readBoolean() throws IOException {
        try {
            return reader.readBoolean();
        } catch (DerException e) {
            throw new IOException("readBoolean: " + e.getMessage(), e);
        }
    }

    byte readByte() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            long lv = v.longValueExact();
            if (lv < Byte.MIN_VALUE || lv > Byte.MAX_VALUE) {
                throw new IOException("readByte: value " + lv + " out of byte range");
            }
            return (byte) lv;
        } catch (ArithmeticException e) {
            throw new IOException("readByte: INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readByte: " + e.getMessage(), e);
        }
    }

    short readShort() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            long lv = v.longValueExact();
            if (lv < Short.MIN_VALUE || lv > Short.MAX_VALUE) {
                throw new IOException("readShort: value " + lv + " out of short range");
            }
            return (short) lv;
        } catch (ArithmeticException e) {
            throw new IOException("readShort: INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readShort: " + e.getMessage(), e);
        }
    }

    int readInt() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            return v.intValueExact();
        } catch (ArithmeticException e) {
            throw new IOException("readInt: INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readInt: " + e.getMessage(), e);
        }
    }

    long readLong() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            return v.longValueExact();
        } catch (ArithmeticException e) {
            throw new IOException("readLong: INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readLong: " + e.getMessage(), e);
        }
    }

    /**
     * STD-008 sec.17.3.1: strict canonical IEEE-754 decode. 4-byte OCTET STRING required;
     * non-canonical NaN bit patterns and {@code -0.0} bits rejected fail-secure.
     */
    float readFloat() throws IOException {
        byte[] content;
        try {
            content = reader.readOctetString();
        } catch (DerException e) {
            throw new IOException("readFloat: " + e.getMessage(), e);
        }
        return Float.intBitsToFloat(decodeCanonicalFloatBits(content, "readFloat"));
    }

    /** STD-008 sec.17.3.1: strict canonical IEEE-754 decode (8 bytes; rejects non-canonical NaN / {@code -0.0}). */
    double readDouble() throws IOException {
        byte[] content;
        try {
            content = reader.readOctetString();
        } catch (DerException e) {
            throw new IOException("readDouble: " + e.getMessage(), e);
        }
        return Double.longBitsToDouble(decodeCanonicalDoubleBits(content, "readDouble"));
    }

    /**
     * STD-008 sec.17.3.1 canonical decode shared by the typed {@link #readFloat} channel and
     * the boxed {@code java.lang.Float} top-level item (sec.15.2.1): requires exactly 4
     * content bytes, rejects non-canonical {@code -0.0} bits and non-canonical NaN bit
     * patterns.
     *
     * @param content the OCTET STRING content bytes (header already consumed by the caller)
     * @param what     a short label identifying the caller, for the error message
     */
    private static int decodeCanonicalFloatBits(byte[] content, String what) throws IOException {
        if (content.length != 4) {
            throw new IOException(what + ": OCTET STRING must be 4 bytes, got " + content.length);
        }
        int bits =  ((content[0] & 0xFF) << 24)
                  | ((content[1] & 0xFF) << 16)
                  | ((content[2] & 0xFF) <<  8)
                  |  (content[3] & 0xFF);
        if (bits == 0x80000000) {
            throw new IOException(what + ": -0.0 bits are not canonical (STD-008 sec.17.3.1)");
        }
        boolean isNaN = (bits & 0x7F800000) == 0x7F800000 && (bits & 0x007FFFFF) != 0;
        if (isNaN && bits != 0x7FC00000) {
            throw new IOException(what + ": non-canonical NaN 0x"
                    + String.format("%08X", bits) + " (canonical is 0x7FC00000)");
        }
        return bits;
    }

    /**
     * STD-008 sec.17.3.1 canonical decode shared by the typed {@link #readDouble} channel and
     * the boxed {@code java.lang.Double} top-level item (sec.15.2.1): requires exactly 8
     * content bytes, rejects non-canonical {@code -0.0} bits and non-canonical NaN bit
     * patterns.
     *
     * @param content the OCTET STRING content bytes (header already consumed by the caller)
     * @param what     a short label identifying the caller, for the error message
     */
    private static long decodeCanonicalDoubleBits(byte[] content, String what) throws IOException {
        if (content.length != 8) {
            throw new IOException(what + ": OCTET STRING must be 8 bytes, got " + content.length);
        }
        long bits = 0L;
        for (int i = 0; i < 8; i++) bits = (bits << 8) | (content[i] & 0xFF);
        if (bits == 0x8000000000000000L) {
            throw new IOException(what + ": -0.0 bits are not canonical (STD-008 sec.17.3.1)");
        }
        boolean isNaN = (bits & 0x7FF0000000000000L) == 0x7FF0000000000000L
                     && (bits & 0x000FFFFFFFFFFFFFL) != 0L;
        if (isNaN && bits != 0x7FF8000000000000L) {
            throw new IOException(what + ": non-canonical NaN 0x"
                    + String.format("%016X", bits) + " (canonical is 0x7FF8000000000000)");
        }
        return bits;
    }

    /**
     * Parses INTEGER content bytes (TLV header already consumed by the caller) with the SAME
     * canonical-form checks {@link DerReader#readInteger()} applies internally (X.690 minimal
     * two's-complement: no non-minimal leading {@code 0x00}/{@code 0xFF}). Used by the boxed
     * Byte/Short/Integer/Long/Character top-level items (sec.15.2.1), whose tag is
     * context-specific -- not the universal INTEGER tag {@code readInteger()} itself requires.
     *
     * @param what a short label identifying the caller, for the error message
     */
    private static BigInteger parseCanonicalIntegerContent(byte[] content, String what) throws IOException {
        if (content.length == 0) {
            throw new IOException(what + ": INTEGER content must be at least 1 byte");
        }
        if (content.length >= 2) {
            int b0 = content[0] & 0xFF;
            int b1 = content[1] & 0xFF;
            if (b0 == 0x00 && (b1 & 0x80) == 0) {
                throw new IOException(what
                        + ": non-canonical INTEGER, leading 0x00 with non-negative following byte (0x"
                        + Integer.toHexString(b1) + ")");
            }
            if (b0 == 0xFF && (b1 & 0x80) != 0) {
                throw new IOException(what
                        + ": non-canonical INTEGER, leading 0xFF with negative-marked following byte (0x"
                        + Integer.toHexString(b1) + ")");
            }
        }
        return new BigInteger(content);
    }

    /**
     * Parses a codepoint from already-consumed INTEGER content, applying the same
     * canonical-form (via {@link #parseCanonicalIntegerContent}) plus range and surrogate
     * checks as {@link #readChar} (STD-008 sec.17.3.2). Used by the boxed
     * {@code java.lang.Character} top-level item (sec.15.2.1).
     */
    private static char parseCanonicalCharContent(byte[] content, String what) throws IOException {
        BigInteger v = parseCanonicalIntegerContent(content, what);
        int cp = exactIntOrThrow(v, what);
        if (cp < 0 || cp > 0xFFFF) {
            throw new IOException(what + ": codepoint " + cp
                    + " out of BMP range [0, 0xFFFF] (STD-008 sec.17.3.2)");
        }
        if (cp >= 0xD800 && cp <= 0xDFFF) {
            throw new IOException(what + ": surrogate codepoint 0x"
                    + Integer.toHexString(cp).toUpperCase() + " is not a valid Unicode codepoint");
        }
        return (char) cp;
    }

    /** Wraps {@link BigInteger#longValueExact()}, translating overflow into a fail-secure {@link IOException}. */
    private static long exactLongOrThrow(BigInteger v, String what) throws IOException {
        try {
            return v.longValueExact();
        } catch (ArithmeticException e) {
            throw new IOException(what + ": INTEGER overflow", e);
        }
    }

    /** Wraps {@link BigInteger#intValueExact()}, translating overflow into a fail-secure {@link IOException}. */
    private static int exactIntOrThrow(BigInteger v, String what) throws IOException {
        try {
            return v.intValueExact();
        } catch (ArithmeticException e) {
            throw new IOException(what + ": INTEGER overflow", e);
        }
    }

    /** STD-008 sec.17.3.2: Unicode codepoint INTEGER, BMP non-surrogate. */
    char readChar() throws IOException {
        try {
            BigInteger v = reader.readInteger();
            int cp = v.intValueExact();
            if (cp < 0 || cp > 0xFFFF) {
                throw new IOException("readChar: codepoint " + cp
                        + " out of BMP range [0, 0xFFFF] (STD-008 sec.17.3.2)");
            }
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                throw new IOException("readChar: surrogate codepoint 0x"
                        + Integer.toHexString(cp).toUpperCase()
                        + " is not a valid Unicode codepoint");
            }
            return (char) cp;
        } catch (ArithmeticException e) {
            throw new IOException("readChar: codepoint INTEGER overflow", e);
        } catch (DerException e) {
            throw new IOException("readChar: " + e.getMessage(), e);
        }
    }

    String readUTF() throws IOException {
        try {
            return reader.readUtf8String();
        } catch (DerException e) {
            throw new IOException("readUTF: " + e.getMessage(), e);
        }
    }

    byte[] readOctetString() throws IOException {
        try {
            return reader.readOctetString();
        } catch (DerException e) {
            throw new IOException("readOctetString: " + e.getMessage(), e);
        }
    }

    int readSingleByte() throws IOException {
        // inverse of writeSingleByte
        return readByte() & 0xFF;
    }

    int available() {
        // Simple: 1 if there are more bytes to read, 0 otherwise
        return reader != null && reader.hasMore() ? 1 : 0;
    }

    // =========================================================================
    // Read side: objects
    // =========================================================================

    /**
     * Decodes the next context-tagged item from the stream and returns the object.
     */
    Object readObject() throws IOException, ClassNotFoundException {
        Tag tag;
        DerReader.TlvHeader hdr;
        try {
            hdr = reader.readTlvHeader();
        } catch (DerException e) {
            throw new IOException("readObject: failed to read TLV header: " + e.getMessage(), e);
        }
        tag = hdr.tag();

        if (CTX_NULL.equals(tag)) {
            // [0] NULL -- no content
            if (hdr.contentLength() != 0) {
                throw new IOException("readObject: [0] NULL TLV must have length 0, got "
                        + hdr.contentLength());
            }
            return null;
        }

        if (CTX_STRING.equals(tag)) {
            // [3] String: content is UTF-8 bytes
            byte[] content;
            try {
                content = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read string content", e);
            }
            return new String(content, StandardCharsets.UTF_8);
        }

        if (CTX_BYTES.equals(tag)) {
            // [5] byte[]: content is raw bytes
            try {
                return reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read byte[] content", e);
            }
        }

        if (CTX_ARRAY.equals(tag)) {
            // [9] top-level value array: content = UTF8String(arrayWireType) ++ element SEQUENCE.
            byte[] content;
            try {
                content = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read [9] array content", e);
            }
            DerReader ar = new DerReader(content);
            String awt;
            try {
                awt = ar.readUtf8String();
            } catch (DerException e) {
                throw new IOException("readObject: malformed [9] array wireType", e);
            }
            if (!awt.startsWith("array:")) {
                throw new IOException("readObject: [9] array wireType must start with 'array:', got '"
                        + awt + "'");
            }
            if (streamFormat) {
                // Appendix C: reconstitute the [9] array's P2 element sites (references
                // resolved fail-closed against this stream's table) BEFORE the existing
                // record-level decode sees the bytes. No-op for non-@AtomicSerial
                // components (whose elements carry no chain sites).
                try {
                    content = decodeDedup.reconstituteTopLevelArray(content);
                } catch (DerException e) {
                    throw new IOException(
                            "readObject: [9] array stream schema-dedup reconstitution"
                            + " failed: " + e.getMessage(), e);
                }
                ar = new DerReader(content);
                try {
                    awt = ar.readUtf8String();
                } catch (DerException e) {
                    throw new IOException("readObject: malformed [9] array wireType", e);
                }
            }
            byte[] seqBytes = Arrays.copyOfRange(content, ar.position(), content.length);
            String componentWT = awt.substring("array:".length());
            try {
                if (componentWT.startsWith("@AtomicSerial:")) {
                    String cls = componentWT.substring("@AtomicSerial:".length());
                    return ObjectCodec.decodeNestedArray(seqBytes, cls, 0, decodeUnit, resolution);
                }
                return DerFieldStore.decodePrimitiveArray(seqBytes, awt, resolution);
            } catch (DerException | ClassNotFoundException e) {
                throw new IOException("readObject: [9] array decode failed (" + awt + ")", e);
            }
        }

        if (CTX_ATOMIC.equals(tag)) {
            // [1] @AtomicSerial object. Stream format: the content is a stream-form
            // DedupMarshalledInstanceRecord (STD-006 Appendix C sec.C.5.3), verified and
            // reconstituted to the byte-exact canonical sec.7.8 four-field record —
            // references resolved fail-closed against this stream's table, chain sites
            // processed in pre-order, ceilings metered during the walk — BEFORE the
            // unchanged record-level decode below sees the bytes. (Record-level capture
            // context: the content is already canonical and passes straight through.)
            byte[] recBytes;
            try {
                recBytes = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read @AtomicSerial record bytes", e);
            }
            if (streamFormat) {
                try {
                    recBytes = decodeDedup.reconstituteTopLevelAtomic(recBytes);
                } catch (DerException e) {
                    throw new IOException(
                            "readObject: [1] stream schema-dedup reconstitution failed: "
                            + e.getMessage(), e);
                }
            }
            return decodeAtomicRecord(recBytes);
        }

        if (CTX_ENUM.equals(tag)) {
            // [7] enum: content = UTF8String(declaringClassName) ++ UTF8String(constantName)
            byte[] content;
            try {
                content = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read [7] enum content", e);
            }
            DerReader er = new DerReader(content);
            String className;
            String constant;
            try {
                className = er.readUtf8String();
                constant  = er.readUtf8String();
            } catch (DerException e) {
                throw new IOException("readObject: malformed [7] enum item", e);
            }
            if (er.hasMore()) {
                throw new IOException("readObject: trailing bytes in [7] enum item");
            }
            // Endpoint-assigned resolution (NEVER the thread-context loader -- Warres).
            Class<?> enumClass = resolution.loadClass(className);
            if (!enumClass.isEnum()) {
                throw new IOException("readObject: [7] enum class '" + className
                        + "' is not an enum");
            }
            try {
                return enumValueOf(enumClass, constant);
            } catch (IllegalArgumentException e) {
                throw new IOException("readObject: unknown enum constant '" + constant
                        + "' in " + className, e);
            }
        }

        if (CTX_PROXY.equals(tag)) {
            // [8] bare java.lang.reflect.Proxy: INTEGER count, count x UTF8String interface name,
            // then a [1] @AtomicSerial InvocationHandler. Reconstruct via Proxy.newProxyInstance.
            byte[] content;
            try {
                content = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read [8] proxy content", e);
            }
            DerReader pr = new DerReader(content);
            int count;
            String[] names;
            byte[] handlerRec;
            try {
                count = pr.readInteger().intValueExact();
                if (count <= 0 || count > ProxyWireSupport.MAX_PROXY_INTERFACES) {
                    throw new IOException("readObject: [8] proxy interface count " + count
                            + " out of range (1.." + ProxyWireSupport.MAX_PROXY_INTERFACES + ")");
                }
                names = new String[count];
                for (int i = 0; i < count; i++) {
                    names[i] = pr.readUtf8String();
                }
                DerReader.TlvHeader hh = pr.readTlvHeader();
                if (!CTX_ATOMIC.equals(hh.tag())) {
                    throw new IOException("readObject: [8] proxy handler must be a [1] @AtomicSerial item");
                }
                // Capture the handler's [1] @AtomicSerial record bytes but DEFER its decode until
                // after tolerant interface resolution -- we must know whether narrowing occurred
                // before decoding the handler, so we can inject the original [8] bytes into its
                // GetArg (see below).
                handlerRec = pr.readRawContent(hh.contentLength());
                if (pr.hasMore()) {
                    throw new IOException("readObject: trailing bytes in [8] proxy item");
                }
            } catch (ArithmeticException e) {
                throw new IOException("readObject: [8] proxy interface count overflow", e);
            } catch (DerException e) {
                throw new IOException("readObject: malformed [8] proxy item", e);
            }
            // Endpoint-assigned TOLERANT resolution of the proxy class (NEVER the thread-context
            // loader -- Warres): resolves each interface name independently rather than failing
            // the whole item when a single name doesn't resolve locally. Names that don't resolve
            // are dropped (and logged); the proxy still builds over the resolvable subset.
            ProxyWireSupport.Resolved resolved = ProxyWireSupport.resolveTolerant(names, resolution);
            // DeSerializationPermission("PROXY") gate before reconstruction -- DER counterpart of
            // AtomicMarshalInputStream.instantiateProxy's deSerializationPermitted(PROXY). No-op w/o SM.
            // Runs against the RESOLVED (narrowed) interface set actually being instantiated, not
            // the full original names -- that's what's actually being granted a live proxy.
            checkProxyDeSerializationPermitted(resolved.interfaces);
            // If (and only if) narrowing occurred, INJECT the FULL original [8] content bytes into
            // the handler's top-level GetArg (DC-1/DC-2: a LOCAL, trusted-decoder value keyed on
            // the receiver's own decode intent, disjoint from the wire field store). A retaining
            // handler ((GetArg) ctor reads getInjected(RAW_FORM_KEY)) then re-emits those bytes
            // verbatim on a later re-forward rather than re-deriving them from the narrowed live
            // proxy -- and the retention is carried by the real handler itself, so
            // Proxy.getInvocationHandler(narrowedProxy) is that handler and JERI's self-check
            // passes with no wrapper. Nothing dropped -> no injection (common case re-encodes fresh).
            // Pass the raw bytes (typed byte[]); RAW_FORM_KEY is applied internally by ObjectCodec.
            byte[] injectedRawForm = resolved.droppedNames.length == 0 ? null : content;
            Object handler = decodeAtomicRecord(handlerRec, injectedRawForm);
            if (!(handler instanceof InvocationHandler)) {
                throw new IOException("readObject: [8] proxy handler is not an InvocationHandler ("
                        + (handler == null ? "null" : handler.getClass().getName()) + ")");
            }
            try {
                return Proxy.newProxyInstance(resolved.proxyClass.getClassLoader(),
                        resolved.interfaces, (InvocationHandler) handler);
            } catch (IllegalArgumentException e) {
                throw new IOException("readObject: [8] proxy reconstruction failed", e);
            }
        }

        // Boxed scalar top-level items (STD-008 sec.15.2.1). Each decodes back to the EXACT
        // box its tag names (never a widened/narrowed sibling -- H2) and applies the same
        // canonical-form + range checks the corresponding typed-primitive reader already
        // enforces (fail-secure reject, never tolerate -- H1/principle 6).
        if (CTX_BOOLEAN.equals(tag)) {
            byte[] content = readTagContent(hdr, "readObject: [2] boxed Boolean");
            if (content.length != 1) {
                throw new IOException("readObject: [2] boxed Boolean content length must be 1, got "
                        + content.length);
            }
            int b = content[0] & 0xFF;
            if (b == 0x00) return Boolean.FALSE;
            if (b == 0xFF) return Boolean.TRUE;
            throw new IOException("readObject: [2] boxed Boolean content must be 0x00 or 0xFF, got 0x"
                    + Integer.toHexString(b));
        }

        if (CTX_BYTE.equals(tag)) {
            byte[] content = readTagContent(hdr, "readObject: [4] boxed Byte");
            long lv = exactLongOrThrow(parseCanonicalIntegerContent(content, "readObject: [4] boxed Byte"),
                    "readObject: [4] boxed Byte");
            if (lv < Byte.MIN_VALUE || lv > Byte.MAX_VALUE) {
                throw new IOException("readObject: [4] boxed Byte value " + lv + " out of byte range");
            }
            return Byte.valueOf((byte) lv);
        }

        if (CTX_SHORT.equals(tag)) {
            byte[] content = readTagContent(hdr, "readObject: [6] boxed Short");
            long lv = exactLongOrThrow(parseCanonicalIntegerContent(content, "readObject: [6] boxed Short"),
                    "readObject: [6] boxed Short");
            if (lv < Short.MIN_VALUE || lv > Short.MAX_VALUE) {
                throw new IOException("readObject: [6] boxed Short value " + lv + " out of short range");
            }
            return Short.valueOf((short) lv);
        }

        if (CTX_INTEGER.equals(tag)) {
            byte[] content = readTagContent(hdr, "readObject: [10] boxed Integer");
            BigInteger v = parseCanonicalIntegerContent(content, "readObject: [10] boxed Integer");
            return Integer.valueOf(exactIntOrThrow(v, "readObject: [10] boxed Integer"));
        }

        if (CTX_LONG.equals(tag)) {
            byte[] content = readTagContent(hdr, "readObject: [11] boxed Long");
            BigInteger v = parseCanonicalIntegerContent(content, "readObject: [11] boxed Long");
            return Long.valueOf(exactLongOrThrow(v, "readObject: [11] boxed Long"));
        }

        if (CTX_FLOAT.equals(tag)) {
            byte[] content = readTagContent(hdr, "readObject: [12] boxed Float");
            int bits = decodeCanonicalFloatBits(content, "readObject: [12] boxed Float");
            return Float.valueOf(Float.intBitsToFloat(bits));
        }

        if (CTX_DOUBLE.equals(tag)) {
            byte[] content = readTagContent(hdr, "readObject: [13] boxed Double");
            long bits = decodeCanonicalDoubleBits(content, "readObject: [13] boxed Double");
            return Double.valueOf(Double.longBitsToDouble(bits));
        }

        if (CTX_CHARACTER.equals(tag)) {
            byte[] content = readTagContent(hdr, "readObject: [14] boxed Character");
            return Character.valueOf(parseCanonicalCharContent(content, "readObject: [14] boxed Character"));
        }

        throw new IOException("readObject: unexpected context tag " + tag
                + " (expected [0],[1],[2],[3],[4],[5],[6],[7],[8],[9],[10],[11],[12],[13],[14]);"
                + " back-references are not supported (sec.15.3), and [15] is the stream-format"
                + " version octet, valid ONLY as the first TLV of the stream — a second or"
                + " mid-stream [15] is rejected here (STD-006 Appendix C sec.C.5.2)");
    }

    /**
     * Reads {@code hdr}'s content bytes (TLV header already consumed via {@link
     * DerReader#readTlvHeader()}), wrapping a {@link DerException} as a fail-secure
     * {@link IOException}. Shared by every context-tagged item's content read in
     * {@link #readObject}.
     */
    private byte[] readTagContent(DerReader.TlvHeader hdr, String what) throws IOException {
        try {
            return reader.readRawContent(hdr.contentLength());
        } catch (DerException e) {
            throw new IOException(what + ": failed to read content", e);
        }
    }

    /** Resolves an enum constant by declaring-class + name (raw-type bridge for {@link Enum#valueOf}). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValueOf(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass, name);
    }

    /** Encodes an {@code @AtomicSerial} object to its MarshalledInstanceRecord DER bytes (the [1] content). */
    private static byte[] encodeAtomicRecord(Object obj) throws DerException {
        SchemaChain.Result chain = SchemaGenerator.generateChain(obj.getClass());
        byte[] payload = ObjectCodec.encodeHierarchy(obj, chain);
        return MarshalledInstanceRecord.fromChain(chain, payload).encode();
    }

    /** Decodes the bytes inside a [1] {@code CTX_ATOMIC} TLV (a MarshalledInstanceRecord) into its object. */
    private Object decodeAtomicRecord(byte[] recBytes) throws IOException, ClassNotFoundException {
        MarshalledInstanceRecord rec;
        try {
            rec = MarshalledInstanceRecord.decode(recBytes);
        } catch (DerException e) {
            throw new IOException("readObject: malformed MarshalledInstanceRecord", e);
        }
        String leafClassName;
        try {
            leafClassName = rec.decodeSchemaChain().get(0).className();
        } catch (DerException e) {
            throw new IOException("readObject: failed to decode embedded schema chain", e);
        }
        // Endpoint-assigned resolution (NEVER the thread-context loader -- Warres).
        Class<?> leafClass = resolution.loadClass(leafClassName);
        try {
            return MarshalledInstanceCodec.decodeMarshalledInstance(rec, leafClass, decodeUnit, resolution).object();
        } catch (DerException e) {
            throw new IOException("readObject: decode failed for " + leafClassName, e);
        }
    }

    /**
     * As {@link #decodeAtomicRecord(byte[])}, additionally injecting {@code injectedRawForm} into
     * the decoded object's top-level {@code GetArg} (read via
     * {@link org.apache.river.api.io.AtomicSerial.GetArg#getInjected(String)} under
     * {@link RawWireFormRetaining#RAW_FORM_KEY}, which the callee applies internally). Used ONLY for
     * the {@code [8]} proxy handler so a narrowed proxy's handler receives its original {@code [8]}
     * wire bytes. Decodes via the typed-parameter public {@link ObjectCodec#decodeHierarchy(Class,
     * au.net.zeus.jgdms.der.schema.SchemaChain.Result, byte[],
     * net.jini.io.context.DeserializationCompletion, au.net.zeus.jgdms.der.getarg.ResolutionContext,
     * byte[])} directly (the embedded schema always drives decode, S7.8), mirroring
     * {@link MarshalledInstanceCodec#decodeMarshalledInstance} minus the schema-digest classification
     * that this proxy path discards.
     *
     * @param injectedRawForm the enclosing {@code [8]} TLV content bytes to inject, or {@code null}
     *                        to inject nothing (no narrowing at this hop)
     */
    private Object decodeAtomicRecord(byte[] recBytes, byte[] injectedRawForm)
            throws IOException, ClassNotFoundException {
        if (injectedRawForm == null) {
            // No narrowing -> no injection: identical to the plain path.
            return decodeAtomicRecord(recBytes);
        }
        MarshalledInstanceRecord rec;
        SchemaChain.Result chain;
        try {
            rec = MarshalledInstanceRecord.decode(recBytes);
            chain = rec.decodeSchemaChainAsResult();
        } catch (DerException e) {
            throw new IOException("readObject: malformed MarshalledInstanceRecord", e);
        }
        String leafClassName = chain.chain().get(0).className();
        // Endpoint-assigned resolution (NEVER the thread-context loader -- Warres).
        Class<?> leafClass = resolution.loadClass(leafClassName);
        try {
            return ObjectCodec.decodeHierarchy(leafClass, chain, rec.payloadBytes(),
                    decodeUnit, resolution, injectedRawForm);
        } catch (DerException e) {
            throw new IOException("readObject: decode failed for " + leafClassName, e);
        }
    }

    /** {@code DeSerializationPermission("PROXY")} gate for [8] reconstruction; no-op without a {@link SecurityManager}. */
    private static void checkProxyDeSerializationPermitted(Class<?>[] interfaces) {
        checkProxyDeSerializationPermitted(interfaces, System.getSecurityManager());
    }

    /**
     * Testable seam for the [8] {@code DeSerializationPermission("PROXY")} gate (mirrors
     * {@code ObjectCodec.checkAtomicDeSerializationPermitted}). The permission is checked against an
     * {@link AccessControlContext} built from the proxy interfaces' protection domains -- the
     * DER-path counterpart of JOSS {@code deSerializationPermitted(PROXY)} -- so a deployment can
     * govern which interface codebases may be reconstructed as a proxy from an untrusted stream.
     *
     * @param interfaces the resolved proxy interfaces about to be reconstructed
     * @param sm         the active security manager, or {@code null}
     * @throws SecurityException if {@code sm} denies {@code DeSerializationPermission("PROXY")}
     */
    @SuppressWarnings("removal")
    static void checkProxyDeSerializationPermitted(Class<?>[] interfaces, SecurityManager sm) {
        if (sm == null) return;
        AccessControlContext ctx = AccessController.doPrivileged(
                (PrivilegedAction<AccessControlContext>) () -> {
                    Set<ProtectionDomain> domains = new LinkedHashSet<>();
                    for (Class<?> i : interfaces) {
                        if (i == null) continue;
                        ProtectionDomain pd = i.getProtectionDomain();
                        if (pd != null) domains.add(pd);
                    }
                    return Security.create(domains.toArray(new ProtectionDomain[0]));
                });
        sm.checkPermission(PROXY_PERM, ctx);
    }

    // =========================================================================
    // Write-side drain
    // =========================================================================

    /**
     * Concatenates all accumulated write-buffer chunks and writes them to the
     * given output stream. Clears the buffer.
     */
    void drainTo(OutputStream out) throws IOException {
        for (byte[] chunk : writeBuffer) {
            out.write(chunk);
        }
        writeBuffer.clear();
    }
}
