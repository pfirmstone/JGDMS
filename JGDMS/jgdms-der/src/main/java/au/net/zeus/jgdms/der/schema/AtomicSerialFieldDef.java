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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable representation of one field entry in an {@code AtomicSerialSchemaRecord},
 * per JGDMS-STD-006 S7.8.
 *
 * <pre>
 * AtomicSerialFieldDef ::= SEQUENCE {
 *     wireName  UTF8String (SIZE(1..255)),
 *     wireType  UTF8String (SIZE(1..1024))
 * }
 * </pre>
 *
 * <h3>SIZE bounds</h3>
 * Bounds are enforced on the <em>UTF-8 byte length</em> of the content, before
 * constructing a Java {@code String}:
 * <ul>
 *   <li>{@code wireName}: 1-255 bytes (UTF-8)</li>
 *   <li>{@code wireType}: 1-1024 bytes (UTF-8)</li>
 * </ul>
 * Violations are rejected with {@link DerException} (fail-secure, S3 principle 5).
 *
 * <h3>Determinism</h3>
 * {@link #encode()} is purely a function of {@link #wireName()} and {@link #wireType()};
 * no mutable state is accessed. Re-encoding the same logical field always produces
 * byte-identical DER.
 */
public final class AtomicSerialFieldDef {

    /** Maximum UTF-8 byte length of wireName (SIZE(1..255)). */
    static final int WIRE_NAME_MAX_BYTES = 255;

    /** Maximum UTF-8 byte length of wireType (SIZE(1..1024)). */
    static final int WIRE_TYPE_MAX_BYTES = 1024;

    private final String wireName;
    private final String wireType;

    /**
     * Creates an {@code AtomicSerialFieldDef}.
     *
     * @param wireName the field name (1..255 UTF-8 bytes)
     * @param wireType the field type string (1..1024 UTF-8 bytes)
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if a SIZE bound is violated
     */
    public AtomicSerialFieldDef(String wireName, String wireType) {
        Objects.requireNonNull(wireName, "wireName");
        Objects.requireNonNull(wireType, "wireType");
        checkStringBounds("wireName", wireName, 1, WIRE_NAME_MAX_BYTES);
        checkStringBounds("wireType", wireType, 1, WIRE_TYPE_MAX_BYTES);
        this.wireName = wireName;
        this.wireType = wireType;
    }

    /** Returns the field wire name. */
    public String wireName() { return wireName; }

    /** Returns the field wire type. */
    public String wireType() { return wireType; }

    /**
     * DER-encodes this field definition as a complete {@code SEQUENCE { UTF8String, UTF8String }}.
     *
     * @return the DER TLV bytes
     */
    public byte[] encode() {
        byte[] nameBytes = DerWriter.writeUtf8String(wireName);
        byte[] typeBytes = DerWriter.writeUtf8String(wireType);
        return DerWriter.writeSequence(List.of(nameBytes, typeBytes));
    }

    /**
     * Decodes one {@code AtomicSerialFieldDef} SEQUENCE from {@code reader}.
     * The reader must be positioned at the start of the SEQUENCE TLV.
     *
     * <p>Validates SIZE bounds on the UTF-8 <em>byte length</em> of each field
     * before constructing the Java String, per S3 principle 5.
     *
     * @param reader a DER reader positioned at the start of the SEQUENCE
     * @return the decoded field definition
     * @throws DerException if the encoding is malformed or any SIZE bound is violated
     */
    public static AtomicSerialFieldDef decode(DerReader reader) throws DerException {
        DerReader seq = reader.readSequence();

        // Read wireName: peek the content length via TLV header before allocating
        DerReader.TlvHeader nameHdr = seq.readTlvHeader();
        if (!Tag.UTF8STRING.equals(nameHdr.tag())) {
            throw new DerException("AtomicSerialFieldDef: expected UTF8String for wireName, got " + nameHdr.tag());
        }
        checkLengthBounds("wireName", nameHdr.contentLength(), 1, WIRE_NAME_MAX_BYTES);
        byte[] nameBytes = seq.readRawContent(nameHdr.contentLength());
        String wireName = new String(nameBytes, StandardCharsets.UTF_8);

        // Read wireType
        DerReader.TlvHeader typeHdr = seq.readTlvHeader();
        if (!Tag.UTF8STRING.equals(typeHdr.tag())) {
            throw new DerException("AtomicSerialFieldDef: expected UTF8String for wireType, got " + typeHdr.tag());
        }
        checkLengthBounds("wireType", typeHdr.contentLength(), 1, WIRE_TYPE_MAX_BYTES);
        byte[] typeBytes = seq.readRawContent(typeHdr.contentLength());
        String wireType = new String(typeBytes, StandardCharsets.UTF_8);

        if (seq.hasMore()) {
            throw new DerException("AtomicSerialFieldDef: unexpected trailing bytes in SEQUENCE");
        }

        return new AtomicSerialFieldDef(wireName, wireType);
    }

    // -----------------------------------------------------------------------
    // equals / hashCode / toString
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtomicSerialFieldDef that)) return false;
        return wireName.equals(that.wireName) && wireType.equals(that.wireType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wireName, wireType);
    }

    @Override
    public String toString() {
        return "AtomicSerialFieldDef{wireName='" + wireName + "', wireType='" + wireType + "'}";
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Checks that the UTF-8 byte length of {@code value} is within [min, max].
     * Used at construction time (Java String -> UTF-8 bytes).
     *
     * @throws IllegalArgumentException if out of range
     */
    static void checkStringBounds(String fieldName, String value, int min, int max) {
        int byteLen = value.getBytes(StandardCharsets.UTF_8).length;
        if (byteLen < min || byteLen > max) {
            throw new IllegalArgumentException(
                    fieldName + " UTF-8 byte length " + byteLen
                    + " is outside [" + min + ", " + max + "]");
        }
    }

    /**
     * Checks that a DER-declared content length is within [min, max].
     * Used at decode time (before allocating the content byte array).
     *
     * @throws DerException if out of range
     */
    static void checkLengthBounds(String fieldName, int length, int min, int max)
            throws DerException {
        if (length < min || length > max) {
            throw new DerException(
                    fieldName + " UTF-8 byte length " + length
                    + " is outside [" + min + ", " + max + "]");
        }
    }
}
