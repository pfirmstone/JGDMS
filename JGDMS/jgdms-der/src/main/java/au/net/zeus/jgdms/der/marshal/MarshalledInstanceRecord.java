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

package au.net.zeus.jgdms.der.marshal;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable container for a serialised {@code @AtomicSerial} object, per
 * JGDMS-STD-006 S7.8 (v0.13).
 *
 * <pre>
 * MarshalledInstanceRecord ::= SEQUENCE {
 *     payloadBytes       OCTET STRING,            -- DER object payload
 *     schemaBytes        OCTET STRING,            -- full schema chain, leaf-first, concatenated
 *     schemaDigest       OCTET STRING (SIZE(32)), -- SHA-256(DER(leaf AtomicSerialSchemaRecord))
 *     payloadFormat      UTF8String               -- "JGDMS-STD-006/DER"
 * }
 * </pre>
 *
 * <h2>No codebaseAnnotation (v0.13)</h2>
 * <p>
 * The former {@code codebaseAnnotation UTF8String OPTIONAL} field is REMOVED
 * (STD-006 v0.13). It contradicted S8.3 (the DER format carries no codebase
 * annotation at any level), reintroduced the S8.1 stale-URL hazards, and made the
 * module invalid ASN.1 (S4.5 distinct-tag rule: two adjacent UTF8Strings, one
 * OPTIONAL, forcing a positional count rule no schema-driven decoder can express).
 * The authenticated {@code CodebaseAccessor} channel (S8.2) is the ONLY codebase
 * mechanism. {@link #decode(DerReader)} rejects any record carrying trailing
 * content after {@code payloadFormat} -- which includes every legacy record that
 * carried the annotation.
 *
 * <h2>schemaBytes</h2>
 * <p>
 * {@code schemaBytes} is a raw concatenation of each {@link AtomicSerialSchemaRecord}
 * DER SEQUENCE in leaf-first order. To recover the records, use
 * {@link #decodeSchemaChain()} which reads them back by scanning for complete SEQUENCE
 * TLVs until the bytes are exhausted, and then cross-checks each record's
 * {@code parentSchemaHash} against the next record's {@code schemaDigest()}.
 *
 * <h2>schemaDigest is a routing hint, verified before use</h2>
 * <p>
 * {@code schemaDigest} is sender-supplied and authenticates nothing by itself.
 * {@link #decodeSchemaChainAsResult()} verifies it against the leaf record actually
 * present in {@code schemaBytes} and throws {@link DerException} on mismatch
 * (STD-006 v0.13 S7.8 "schemaDigest verification", S12.4 fail-secure resolution).
 */
public final class MarshalledInstanceRecord {

    /** Canonical payloadFormat value for JGDMS-STD-006 DER encoding. */
    public static final String PAYLOAD_FORMAT = "JGDMS-STD-006/DER";

    /** Required byte length of schemaDigest (SIZE(32)). */
    private static final int DIGEST_LENGTH = 32;

    private final byte[] payloadBytes;
    private final byte[] schemaBytes;
    private final byte[] schemaDigest;      // exactly 32 bytes
    private final String payloadFormat;

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Canonical constructor. All arrays are defensively copied.
     *
     * @param payloadBytes  the DER-encoded object payload (from {@code encodeHierarchy})
     * @param schemaBytes   the concatenated chain of {@code AtomicSerialSchemaRecord} DER
     *                      SEQUENCEs, in leaf-first order
     * @param schemaDigest  exactly 32 bytes: SHA-256(DER(leaf AtomicSerialSchemaRecord))
     * @param payloadFormat wire format identifier; should be {@value #PAYLOAD_FORMAT}
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if {@code schemaDigest.length != 32}
     */
    public MarshalledInstanceRecord(byte[] payloadBytes,
                                    byte[] schemaBytes,
                                    byte[] schemaDigest,
                                    String payloadFormat) {
        Objects.requireNonNull(payloadBytes,  "payloadBytes");
        Objects.requireNonNull(schemaBytes,   "schemaBytes");
        Objects.requireNonNull(schemaDigest,  "schemaDigest");
        Objects.requireNonNull(payloadFormat, "payloadFormat");
        if (schemaDigest.length != DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "schemaDigest must be exactly " + DIGEST_LENGTH
                    + " bytes, got " + schemaDigest.length);
        }
        this.payloadBytes  = Arrays.copyOf(payloadBytes,  payloadBytes.length);
        this.schemaBytes   = Arrays.copyOf(schemaBytes,   schemaBytes.length);
        this.schemaDigest  = Arrays.copyOf(schemaDigest,  schemaDigest.length);
        this.payloadFormat = payloadFormat;
    }

    // =========================================================================
    // Factory helpers
    // =========================================================================

    /**
     * Builds a {@code MarshalledInstanceRecord} from a {@link SchemaChain.Result} and
     * the pre-encoded payload bytes.
     *
     * <p>
     * {@code schemaBytes} is set to the concatenation of each chain record's
     * {@code encode()} in leaf-first order (matching S7.8 "Schema chain encoding").
     * {@code schemaDigest} is taken directly from {@link SchemaChain.Result#leafDigest()}.
     * {@code payloadFormat} is set to {@value #PAYLOAD_FORMAT}.
     *
     * @param chain        the linked schema chain (leaf-first), as returned by
     *                     {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain}
     * @param payloadBytes the DER-encoded payload produced by
     *                     {@link au.net.zeus.jgdms.der.object.ObjectCodec#encodeHierarchy}
     * @return a new {@code MarshalledInstanceRecord}
     */
    public static MarshalledInstanceRecord fromChain(SchemaChain.Result chain,
                                                     byte[] payloadBytes) {
        Objects.requireNonNull(chain,        "chain");
        Objects.requireNonNull(payloadBytes, "payloadBytes");

        // Build schemaBytes: concatenate each record's DER SEQUENCE in leaf-first order
        byte[] schemaBytes = encodeChainBytes(chain.chain());

        return new MarshalledInstanceRecord(
                payloadBytes,
                schemaBytes,
                chain.leafDigest(),
                PAYLOAD_FORMAT);
    }

    /**
     * Concatenates the DER-encoded bytes of each record in the chain (leaf-first).
     */
    private static byte[] encodeChainBytes(List<AtomicSerialSchemaRecord> chain) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord record : chain) {
            byte[] encoded = record.encode();
            buf.write(encoded, 0, encoded.length);
        }
        return buf.toByteArray();
    }

    // =========================================================================
    // Accessors -- all return defensive copies of mutable state
    // =========================================================================

    /** Returns a defensive copy of the DER-encoded object payload. */
    public byte[] payloadBytes() {
        return Arrays.copyOf(payloadBytes, payloadBytes.length);
    }

    /**
     * Returns a defensive copy of the concatenated schema chain bytes (leaf-first
     * DER SEQUENCEs). Use {@link #decodeSchemaChain()} to parse these back into
     * {@link AtomicSerialSchemaRecord} objects.
     */
    public byte[] schemaBytes() {
        return Arrays.copyOf(schemaBytes, schemaBytes.length);
    }

    /**
     * Returns a defensive copy of the 32-byte SHA-256 digest of the leaf
     * {@code AtomicSerialSchemaRecord}. Sender-supplied; see
     * {@link #decodeSchemaChainAsResult()} for the mandatory verification against
     * {@link #schemaBytes()}.
     */
    public byte[] schemaDigest() {
        return Arrays.copyOf(schemaDigest, schemaDigest.length);
    }

    /** Returns the payload format identifier (normally {@value #PAYLOAD_FORMAT}). */
    public String payloadFormat() {
        return payloadFormat;
    }

    // =========================================================================
    // Encoding
    // =========================================================================

    /**
     * DER-encodes this record as a {@code MarshalledInstanceRecord} SEQUENCE.
     *
     * <p>Encoding order:
     * <ol>
     *   <li>{@code payloadBytes} (OCTET STRING)</li>
     *   <li>{@code schemaBytes} (OCTET STRING)</li>
     *   <li>{@code schemaDigest} (OCTET STRING)</li>
     *   <li>{@code payloadFormat} (UTF8String)</li>
     * </ol>
     *
     * @return the complete DER TLV bytes of the outer SEQUENCE
     */
    public byte[] encode() {
        List<byte[]> children = new ArrayList<>(4);
        children.add(DerWriter.writeOctetString(payloadBytes));
        children.add(DerWriter.writeOctetString(schemaBytes));
        children.add(DerWriter.writeOctetString(schemaDigest));
        children.add(DerWriter.writeUtf8String(payloadFormat));
        return DerWriter.writeSequence(children);
    }

    // =========================================================================
    // Decoding
    // =========================================================================

    /**
     * Decodes a {@code MarshalledInstanceRecord} from a raw DER byte array.
     *
     * @param der the DER encoding (a complete SEQUENCE TLV)
     * @return the decoded record
     * @throws DerException if the encoding is malformed, any constraint is violated,
     *                      or the record carries trailing content after
     *                      {@code payloadFormat} (which includes the pre-v0.13
     *                      {@code codebaseAnnotation} field)
     */
    public static MarshalledInstanceRecord decode(byte[] der) throws DerException {
        Objects.requireNonNull(der, "der");
        DerReader reader = new DerReader(der);
        MarshalledInstanceRecord result = decode(reader);
        if (reader.hasMore()) {
            throw new DerException("MarshalledInstanceRecord: trailing bytes after outer SEQUENCE");
        }
        return result;
    }

    /**
     * Decodes a {@code MarshalledInstanceRecord} from a {@link DerReader} positioned
     * at the outer SEQUENCE TLV.
     *
     * <p>The record is exactly four fields; anything after {@code payloadFormat} is
     * rejected (fail-secure). A pre-v0.13 record that carried the withdrawn
     * {@code codebaseAnnotation} therefore fails to decode: its annotation is read
     * as {@code payloadFormat} and the real {@code payloadFormat} becomes trailing
     * content.
     *
     * @param reader the reader positioned at the SEQUENCE TLV
     * @return the decoded record
     * @throws DerException if the encoding is malformed or any constraint is violated
     */
    public static MarshalledInstanceRecord decode(DerReader reader) throws DerException {
        DerReader seq = reader.readSequence();

        // 1. payloadBytes -- OCTET STRING
        byte[] payloadBytes = seq.readOctetString();

        // 2. schemaBytes -- OCTET STRING
        byte[] schemaBytes = seq.readOctetString();

        // 3. schemaDigest -- OCTET STRING (SIZE(32))
        byte[] schemaDigest = seq.readOctetString();
        if (schemaDigest.length != DIGEST_LENGTH) {
            throw new DerException(
                    "MarshalledInstanceRecord: schemaDigest must be exactly "
                    + DIGEST_LENGTH + " bytes, got " + schemaDigest.length);
        }

        // 4. payloadFormat -- UTF8String, final field
        if (!seq.hasMore()) {
            throw new DerException(
                    "MarshalledInstanceRecord: missing payloadFormat UTF8String");
        }
        String payloadFormat = seq.readUtf8String();

        if (seq.hasMore()) {
            throw new DerException(
                    "MarshalledInstanceRecord: unexpected trailing content after payloadFormat"
                    + " (the pre-v0.13 codebaseAnnotation field was withdrawn; STD-006 S7.8/S8.3)");
        }

        return new MarshalledInstanceRecord(
                payloadBytes, schemaBytes, schemaDigest, payloadFormat);
    }

    // =========================================================================
    // Schema chain helpers
    // =========================================================================

    /**
     * Parses {@link #schemaBytes()} back into the leaf-first ordered list of
     * {@link AtomicSerialSchemaRecord}s.
     *
     * <p>The bytes are read sequentially; each complete SEQUENCE TLV is decoded as
     * an {@code AtomicSerialSchemaRecord}. Reading continues until the bytes are
     * exhausted. After decoding, each record's {@code parentSchemaHash} is
     * cross-checked: for records at index {@code i > 0}, the previous record's
     * {@code parentSchemaHash} must equal this record's {@code schemaDigest()}.
     *
     * @return ordered list of schema records, leaf-first
     * @throws DerException if the bytes are malformed or the parentSchemaHash
     *                      cross-check fails
     */
    public List<AtomicSerialSchemaRecord> decodeSchemaChain() throws DerException {
        List<AtomicSerialSchemaRecord> records = new ArrayList<>();
        DerReader reader = new DerReader(schemaBytes);

        while (reader.hasMore()) {
            AtomicSerialSchemaRecord rec = AtomicSerialSchemaRecord.decode(reader);
            records.add(rec);
        }

        if (records.isEmpty()) {
            throw new DerException("MarshalledInstanceRecord: schemaBytes is empty");
        }

        // Cross-check: records[i].parentSchemaHash must equal records[i+1].schemaDigest()
        // (records[i] is the child, records[i+1] is its parent in the chain)
        for (int i = 0; i < records.size() - 1; i++) {
            AtomicSerialSchemaRecord child  = records.get(i);
            AtomicSerialSchemaRecord parent = records.get(i + 1);

            byte[] childParentHash = child.parentSchemaHashOrNull();
            if (childParentHash == null) {
                throw new DerException(
                        "MarshalledInstanceRecord: schema chain broken at index " + i
                        + " -- record for '" + child.className()
                        + "' has no parentSchemaHash but is not the last record in the chain");
            }
            byte[] parentDigest = parent.schemaDigest();
            if (!Arrays.equals(childParentHash, parentDigest)) {
                throw new DerException(
                        "MarshalledInstanceRecord: schema chain cross-check failed at index " + i
                        + " -- record[" + i + "].parentSchemaHash does not match "
                        + "record[" + (i + 1) + "].schemaDigest() for class '"
                        + parent.className() + "'");
            }
        }

        return records;
    }

    /**
     * Reconstructs this record's embedded schema chain as a {@link SchemaChain.Result},
     * suitable for passing directly to
     * {@link au.net.zeus.jgdms.der.object.ObjectCodec#decodeHierarchy}.
     *
     * <p><b>schemaDigest verification (STD-006 v0.13 S7.8, NORMATIVE).</b> The
     * record's {@code schemaDigest} field is sender-supplied and is only a routing
     * hint until verified. This method computes the leaf record's digest from the
     * bytes actually embedded in {@code schemaBytes} and throws if the field does
     * not match -- a mismatched record is rejected outright (S12.4 fail-secure
     * resolution; there is no local-schema or registry fallback).
     *
     * <p>The returned result has:
     * <ul>
     *   <li>{@link SchemaChain.Result#chain()} -- the leaf-first list from
     *       {@link #decodeSchemaChain()}</li>
     *   <li>{@link SchemaChain.Result#leafDigest()} -- the first record's
     *       {@code schemaDigest()}, verified equal to this record's
     *       {@link #schemaDigest()} field</li>
     * </ul>
     *
     * @return a {@link SchemaChain.Result} wrapping the embedded chain
     * @throws DerException if the schema chain bytes are malformed, or the
     *                      {@code schemaDigest} field does not match the digest of
     *                      the embedded leaf record
     */
    public SchemaChain.Result decodeSchemaChainAsResult() throws DerException {
        List<AtomicSerialSchemaRecord> chain = decodeSchemaChain();
        byte[] leafDigest = chain.get(0).schemaDigest();
        if (!Arrays.equals(leafDigest, schemaDigest)) {
            throw new DerException(
                    "MarshalledInstanceRecord: schemaDigest field does not match the digest"
                    + " of the embedded leaf schema record for class '"
                    + chain.get(0).className()
                    + "' -- rejected (STD-006 S7.8 schemaDigest verification)");
        }
        return new SchemaChain.Result(chain, leafDigest);
    }

    // =========================================================================
    // equals / hashCode / toString
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MarshalledInstanceRecord that)) return false;
        return Arrays.equals(payloadBytes, that.payloadBytes)
                && Arrays.equals(schemaBytes, that.schemaBytes)
                && Arrays.equals(schemaDigest, that.schemaDigest)
                && payloadFormat.equals(that.payloadFormat);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(payloadBytes);
        result = 31 * result + Arrays.hashCode(schemaBytes);
        result = 31 * result + Arrays.hashCode(schemaDigest);
        result = 31 * result + payloadFormat.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MarshalledInstanceRecord{"
                + "payloadBytes.length=" + payloadBytes.length
                + ", schemaBytes.length=" + schemaBytes.length
                + ", schemaDigest=[32 bytes]"
                + ", payloadFormat='" + payloadFormat + "'"
                + '}';
    }
}
