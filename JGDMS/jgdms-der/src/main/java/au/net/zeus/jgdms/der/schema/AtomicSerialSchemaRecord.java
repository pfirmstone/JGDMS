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

package au.net.zeus.jgdms.der.schema;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of a schema record for one {@code @AtomicSerial} class,
 * per JGDMS-STD-006 S7.8.
 *
 * <pre>
 * AtomicSerialSchemaRecord ::= SEQUENCE {
 *     className        UTF8String (SIZE(1..1024)),
 *     parentSchemaHash OCTET STRING (SIZE(32)) OPTIONAL,   -- absent when parent is Object
 *     fields           SEQUENCE (SIZE(0..MAX)) OF AtomicSerialFieldDef   -- ORDER-SIGNIFICANT
 * }
 * -- Schema version = SHA-256(DER(AtomicSerialSchemaRecord))
 * </pre>
 *
 * <h3>OPTIONAL parentSchemaHash -- tag-based resolution</h3>
 * The {@code parentSchemaHash} field is resolved <em>by tag inspection</em>, not by
 * position or a default value:
 * <ul>
 *   <li>After decoding {@code className} (tag {@code 0x0C} UTF8String), call
 *       {@link DerReader#peekTag()}.</li>
 *   <li>If the next tag is {@code 0x04} (OCTET STRING): {@code parentSchemaHash} is
 *       <b>present</b> -- read it and then read the {@code fields} SEQUENCE
 *       ({@code 0x30}).</li>
 *   <li>If the next tag is {@code 0x30} (SEQUENCE): {@code parentSchemaHash} is
 *       <b>absent</b> (parent is {@code Object}) -- read {@code fields} directly.</li>
 *   <li>Any other tag is a decode error ({@link DerException}).</li>
 * </ul>
 *
 * <h3>Determinism</h3>
 * {@link #encode()} iterates {@link #fields()} (an ordered {@link List}) in
 * declaration order. There is no {@code HashMap} or {@code HashSet} in the encode
 * path. Re-encoding the same logical record -- or two independently constructed equal
 * records -- always produces byte-identical DER. This is required for digest stability
 * across JVM runs (JGDMS-STD-006 S7.8, Merkle chain guarantee).
 *
 * <h3>Merkle chain</h3>
 * {@code SHA-256(DER(AtomicSerialSchemaRecord))} is the "schema version" of a class.
 * When a class has a parent that is also an {@code @AtomicSerial} class, the parent's
 * schema version is embedded as {@code parentSchemaHash} in the child record. Changing
 * any field in any ancestor record changes the ancestor's digest, which then changes
 * every descendant's {@code parentSchemaHash} and therefore every descendant's own
 * digest. Use {@link SchemaChain#linkAndGetLeafDigest} to build and link a chain.
 *
 * <h3>SIZE bounds</h3>
 * Bounds are enforced on the UTF-8 byte length before constructing Java Strings, and
 * on DER-declared content lengths before allocating arrays:
 * <ul>
 *   <li>{@code className}: 1-1024 UTF-8 bytes</li>
 *   <li>{@code parentSchemaHash}: exactly 32 bytes when present</li>
 * </ul>
 */
public final class AtomicSerialSchemaRecord {

    /** Maximum UTF-8 byte length of className (SIZE(1..1024)). */
    static final int CLASS_NAME_MAX_BYTES = 1024;

    /** Required byte length of parentSchemaHash (SIZE(32)). */
    static final int PARENT_HASH_LENGTH = 32;

    private final String className;
    private final byte[] parentSchemaHash; // null = absent; always 32 bytes when non-null
    private final List<AtomicSerialFieldDef> fields;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * Creates a root record (no parent -- parent is {@code Object}).
     *
     * @param className the fully-qualified class name (1..1024 UTF-8 bytes)
     * @param fields    ordered list of field definitions (may be empty; ORDER-SIGNIFICANT)
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if className SIZE bound is violated
     */
    public AtomicSerialSchemaRecord(String className, List<AtomicSerialFieldDef> fields) {
        this(className, (byte[]) null, fields);
    }

    /**
     * Creates a record with an explicit parent schema hash.
     *
     * @param className        the fully-qualified class name (1..1024 UTF-8 bytes)
     * @param parentSchemaHash the 32-byte SHA-256 digest of the parent record's DER
     *                         encoding, or {@code null} if the parent is {@code Object}
     * @param fields           ordered list of field definitions (ORDER-SIGNIFICANT)
     * @throws NullPointerException     if {@code className} or {@code fields} is {@code null}
     * @throws IllegalArgumentException if className SIZE bound is violated or
     *                                  parentSchemaHash is non-null but not 32 bytes
     */
    public AtomicSerialSchemaRecord(String className, byte[] parentSchemaHash,
                                    List<AtomicSerialFieldDef> fields) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(fields, "fields");
        AtomicSerialFieldDef.checkStringBounds("className", className, 1, CLASS_NAME_MAX_BYTES);
        if (parentSchemaHash != null && parentSchemaHash.length != PARENT_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "parentSchemaHash must be exactly " + PARENT_HASH_LENGTH
                    + " bytes, got " + parentSchemaHash.length);
        }
        this.className = className;
        this.parentSchemaHash = (parentSchemaHash == null) ? null
                : Arrays.copyOf(parentSchemaHash, parentSchemaHash.length);
        // Defensive copy: preserve caller's order, prevent external mutation
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
    }

    /**
     * Creates a record with a parent hash wrapped in an {@link Optional}.
     *
     * @param className        the fully-qualified class name
     * @param parentSchemaHash optional 32-byte parent hash
     * @param fields           ordered list of field definitions
     */
    public AtomicSerialSchemaRecord(String className, Optional<byte[]> parentSchemaHash,
                                    List<AtomicSerialFieldDef> fields) {
        this(className, parentSchemaHash.orElse(null), fields);
    }

    // -----------------------------------------------------------------------
    // Accessors (all return defensive copies of mutable state)
    // -----------------------------------------------------------------------

    /** Returns the fully-qualified class name. */
    public String className() { return className; }

    /**
     * Returns the parent schema hash, or {@link Optional#empty()} if the parent
     * is {@code Object}.
     *
     * @return an {@link Optional} wrapping a <em>defensive copy</em> of the 32-byte hash
     */
    public Optional<byte[]> parentSchemaHash() {
        return (parentSchemaHash == null) ? Optional.empty()
                : Optional.of(Arrays.copyOf(parentSchemaHash, parentSchemaHash.length));
    }

    /**
     * Returns the raw parent schema hash bytes, or {@code null} if absent.
     * The returned array is a defensive copy.
     */
    public byte[] parentSchemaHashOrNull() {
        return (parentSchemaHash == null) ? null
                : Arrays.copyOf(parentSchemaHash, parentSchemaHash.length);
    }

    /**
     * Returns an immutable, order-significant list of field definitions.
     * The order is the wire order and corresponds directly to payload positions (S3.9).
     */
    public List<AtomicSerialFieldDef> fields() { return fields; }

    // -----------------------------------------------------------------------
    // Encoding
    // -----------------------------------------------------------------------

    /**
     * DER-encodes this record as an {@code AtomicSerialSchemaRecord} SEQUENCE.
     *
     * <p>Encoding order (deterministic, no map iteration):
     * <ol>
     *   <li>className (UTF8String)</li>
     *   <li>parentSchemaHash (OCTET STRING, only if present)</li>
     *   <li>fields (SEQUENCE OF AtomicSerialFieldDef, in List order)</li>
     * </ol>
     *
     * @return the complete DER TLV bytes
     */
    public byte[] encode() {
        // 1. className
        byte[] classNameTlv = DerWriter.writeUtf8String(className);

        // 2. parentSchemaHash (conditional)
        byte[] hashTlv = (parentSchemaHash == null) ? null
                : DerWriter.writeOctetString(parentSchemaHash);

        // 3. fields SEQUENCE: encode each child in List order (ORDER-SIGNIFICANT)
        int fieldCount = fields.size();
        List<byte[]> fieldTlvs = new ArrayList<>(fieldCount);
        for (AtomicSerialFieldDef field : fields) {
            fieldTlvs.add(field.encode());
        }
        byte[] fieldsTlv = DerWriter.writeSequence(fieldTlvs);

        // Assemble outer SEQUENCE content
        List<byte[]> outerChildren = new ArrayList<>(3);
        outerChildren.add(classNameTlv);
        if (hashTlv != null) {
            outerChildren.add(hashTlv);
        }
        outerChildren.add(fieldsTlv);

        return DerWriter.writeSequence(outerChildren);
    }

    // -----------------------------------------------------------------------
    // Decoding
    // -----------------------------------------------------------------------

    /**
     * Decodes an {@code AtomicSerialSchemaRecord} from a raw DER byte array.
     *
     * @param der the DER encoding (must be a single complete SEQUENCE TLV)
     * @return the decoded record
     * @throws DerException if the encoding is malformed or any constraint is violated
     */
    public static AtomicSerialSchemaRecord decode(byte[] der) throws DerException {
        Objects.requireNonNull(der, "der");
        DerReader reader = new DerReader(der);
        AtomicSerialSchemaRecord result = decode(reader);
        if (reader.hasMore()) {
            throw new DerException("AtomicSerialSchemaRecord: trailing bytes after SEQUENCE");
        }
        return result;
    }

    /**
     * Decodes one {@code AtomicSerialSchemaRecord} SEQUENCE from a {@link DerReader}.
     * The reader must be positioned at the start of the SEQUENCE TLV.
     *
     * <h3>OPTIONAL parentSchemaHash -- tag-based resolution</h3>
     * After reading {@code className}, {@link DerReader#peekTag()} determines whether
     * {@code parentSchemaHash} is present:
     * <ul>
     *   <li>Tag {@code 0x04} (OCTET STRING) -> present; read it, then read fields SEQUENCE.</li>
     *   <li>Tag {@code 0x30} (SEQUENCE) -> absent; read fields SEQUENCE directly.</li>
     *   <li>Any other tag -> {@link DerException}.</li>
     * </ul>
     *
     * @param reader a DER reader positioned at the SEQUENCE TLV
     * @return the decoded record
     * @throws DerException if the encoding is malformed or any constraint is violated
     */
    public static AtomicSerialSchemaRecord decode(DerReader reader) throws DerException {
        DerReader seq = reader.readSequence();

        // 1. className -- read header manually to check byte-length before String allocation
        DerReader.TlvHeader classHdr = seq.readTlvHeader();
        if (!Tag.UTF8STRING.equals(classHdr.tag())) {
            throw new DerException("AtomicSerialSchemaRecord: expected UTF8String for className, got "
                    + classHdr.tag());
        }
        AtomicSerialFieldDef.checkLengthBounds("className", classHdr.contentLength(),
                1, CLASS_NAME_MAX_BYTES);
        byte[] classBytes = seq.readRawContent(classHdr.contentLength());
        String className = new String(classBytes, StandardCharsets.UTF_8);

        // 2. OPTIONAL parentSchemaHash -- resolved by tag, not by default
        byte[] parentSchemaHash = null;
        Tag nextTag = seq.peekTag();
        if (Tag.OCTET_STRING.equals(nextTag)) {
            // parentSchemaHash is PRESENT
            DerReader.TlvHeader hashHdr = seq.readTlvHeader();
            if (hashHdr.contentLength() != PARENT_HASH_LENGTH) {
                throw new DerException(
                        "AtomicSerialSchemaRecord: parentSchemaHash must be exactly "
                        + PARENT_HASH_LENGTH + " bytes, got " + hashHdr.contentLength());
            }
            parentSchemaHash = seq.readRawContent(hashHdr.contentLength());
        } else if (!Tag.SEQUENCE.equals(nextTag)) {
            throw new DerException(
                    "AtomicSerialSchemaRecord: expected OCTET STRING (parentSchemaHash) or SEQUENCE (fields), got "
                    + nextTag);
        }
        // If nextTag == SEQUENCE, parentSchemaHash remains null (absent)

        // 3. fields SEQUENCE OF AtomicSerialFieldDef -- ORDER-SIGNIFICANT
        DerReader fieldsSeq = seq.readSequence();
        List<AtomicSerialFieldDef> fields = new ArrayList<>();
        while (fieldsSeq.hasMore()) {
            fields.add(AtomicSerialFieldDef.decode(fieldsSeq));
        }

        if (seq.hasMore()) {
            throw new DerException("AtomicSerialSchemaRecord: unexpected trailing bytes in outer SEQUENCE");
        }

        return new AtomicSerialSchemaRecord(className, parentSchemaHash, fields);
    }

    // -----------------------------------------------------------------------
    // Schema digest (Merkle node value)
    // -----------------------------------------------------------------------

    /**
     * Computes the schema version digest: {@code SHA-256(DER(this))}.
     *
     * <p>This is the canonical schema version identifier (JGDMS-STD-006 S7.8).
     * Two independently constructed equal records produce identical digests because
     * {@link #encode()} is deterministic.
     *
     * @return a fresh 32-byte SHA-256 digest
     */
    public byte[] schemaDigest() {
        return sha256(encode());
    }

    // -----------------------------------------------------------------------
    // Withers (return new record with one field changed)
    // -----------------------------------------------------------------------

    /**
     * Returns a new record identical to this one but with {@code parentSchemaHash}
     * replaced by the given value. Used by {@link SchemaChain} to stitch the chain.
     *
     * @param newParentHash the 32-byte parent digest, or {@code null} to clear it
     * @return a new {@code AtomicSerialSchemaRecord}
     */
    public AtomicSerialSchemaRecord withParentSchemaHash(byte[] newParentHash) {
        return new AtomicSerialSchemaRecord(className, newParentHash, new ArrayList<>(fields));
    }

    // -----------------------------------------------------------------------
    // equals / hashCode / toString
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomicSerialSchemaRecord that)) return false;
        return className.equals(that.className)
                && Arrays.equals(parentSchemaHash, that.parentSchemaHash)
                && fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, Arrays.hashCode(parentSchemaHash), fields);
    }

    @Override
    public String toString() {
        return "AtomicSerialSchemaRecord{className='" + className + "', "
                + "parentSchemaHash=" + (parentSchemaHash == null ? "absent"
                        : ("[32 bytes: " + bytesToHex(parentSchemaHash, 8) + "...]"))
                + ", fields=" + fields + '}';
    }

    // -----------------------------------------------------------------------
    // Static helpers
    // -----------------------------------------------------------------------

    /**
     * Computes SHA-256 of the given bytes using the JDK {@code MessageDigest}.
     *
     * @param data the bytes to hash
     * @return a fresh 32-byte digest
     * @throws IllegalStateException if SHA-256 is not available (should never happen on JDK 21)
     */
    static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(bytes.length, maxBytes);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
        }
        return sb.toString();
    }
}
