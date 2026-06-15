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
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceCodec;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Engine for DER object-stream encoding and decoding (JGDMS-STD-008 sec.15.5,
 * Increment 1).
 *
 * <h2>Context-tag scheme for self-describing object items</h2>
 * <p>
 * Each object written through {@link #writeObject} / read through
 * {@link #readObject} is a context-specific TLV whose tag number encodes the
 * item kind:
 * <pre>
 *   [0] PRIMITIVE   -- NULL reference (length 0)
 *   [1] CONSTRUCTED -- @AtomicSerial object; content = MarshalledInstanceRecord DER SEQUENCE
 *   [3] PRIMITIVE   -- java.lang.String; content = UTF8String value bytes
 *   [5] PRIMITIVE   -- byte[]; content = OCTET STRING value bytes
 * </pre>
 * Context class = 0x80. Constructed bit = 0x20 set for [1]. Single-byte tags:
 * [0]->0x80, [1]->0xa1, [3]->0x83, [5]->0x85.
 *
 * <h2>No handle table -- pure value-tree, deterministic (STD-008 sec.15.3)</h2>
 * <p>
 * There is NO handle table and NO back-reference: every object occurrence is encoded in
 * full, by VALUE. This keeps the stream a deterministic (canonical-DER) function of the
 * argument values rather than of object identity or write order, and it carries no
 * aliasing. It loses nothing real -- {@code @AtomicSerial} deserialization defensively
 * copies and re-checks invariants per object, so shared identity is never preserved across
 * the boundary anyway (a deliberate security property). Reference cycles are consequently
 * impossible to express. A back-reference-style context tag (e.g. [2]) is not part of the
 * grammar and is rejected fail-secure.
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

    // =========================================================================
    // Write side state
    // =========================================================================

    /** Output accumulator for the write side. */
    private final List<byte[]> writeBuffer = new ArrayList<>();

    // =========================================================================
    // Read side state
    // =========================================================================

    /** DER reader over the input bytes (read side). */
    private DerReader reader;

    // =========================================================================
    // Construction
    // =========================================================================

    /** Creates a fresh codec ready for writing; initialise read side later via {@link #initReader}. */
    DerObjectStreamCodec() {}

    /** Initialises the read side over a complete DER byte array. */
    void initReader(byte[] buf) {
        Objects.requireNonNull(buf, "buf");
        this.reader = new DerReader(buf);
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
        int bits;
        if (Float.isNaN(v))                                          bits = 0x7FC00000;
        else if (Float.floatToRawIntBits(v) == 0x80000000)           bits = 0x00000000;
        else                                                         bits = Float.floatToRawIntBits(v);
        byte[] content = new byte[] {
                (byte)(bits >>> 24), (byte)(bits >>> 16),
                (byte)(bits >>>  8), (byte) bits
        };
        writeBuffer.add(DerWriter.writeOctetString(content));
    }

    /**
     * STD-008 sec.17.3.1: IEEE-754 in 8-byte OCTET STRING with canonical NaN and
     * canonical {@code +0.0}. {@code -0.0} is mapped to {@code +0.0} on encode.
     */
    void writeDouble(double v) {
        long bits;
        if (Double.isNaN(v))                                                  bits = 0x7FF8000000000000L;
        else if (Double.doubleToRawLongBits(v) == 0x8000000000000000L)        bits = 0x0000000000000000L;
        else                                                                  bits = Double.doubleToRawLongBits(v);
        byte[] content = new byte[8];
        for (int i = 7; i >= 0; i--) { content[i] = (byte)(bits & 0xFF); bits >>>= 8; }
        writeBuffer.add(DerWriter.writeOctetString(content));
    }

    /**
     * STD-008 sec.17.3.2: Unicode codepoint INTEGER. Surrogate code units rejected.
     * (Note: {@link java.io.ObjectOutput#writeChar(int)} takes an {@code int}; we treat
     * the low 16 bits as the char value, matching {@link DataOutput#writeChar}.)
     */
    void writeChar(int v) {
        int cp = v & 0xFFFF;
        if (cp >= 0xD800 && cp <= 0xDFFF) {
            throw new IllegalArgumentException(
                    "DER stream: unpaired surrogate code unit 0x"
                    + Integer.toHexString(cp).toUpperCase()
                    + " is not a valid Unicode codepoint (STD-008 sec.17.3.2)");
        }
        writeBuffer.add(DerWriter.writeInteger(BigInteger.valueOf(cp)));
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
     *   <li>anything else -> UnsupportedOperationException (fail-secure)</li>
     * </ul>
     */
    void writeObject(Object obj) throws IOException {
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
            SchemaChain.Result chain = SchemaGenerator.generateChain(cls);
            byte[] payload = ObjectCodec.encodeHierarchy(obj, chain);
            MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);
            byte[] recBytes = rec.encode();
            writeBuffer.add(DerWriter.writeTlv(CTX_ATOMIC, recBytes));
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
        try {
            byte[] content = reader.readOctetString();
            if (content.length != 4) {
                throw new IOException("readFloat: OCTET STRING must be 4 bytes, got " + content.length);
            }
            int bits =  ((content[0] & 0xFF) << 24)
                      | ((content[1] & 0xFF) << 16)
                      | ((content[2] & 0xFF) <<  8)
                      |  (content[3] & 0xFF);
            if (bits == 0x80000000) {
                throw new IOException("readFloat: -0.0 bits are not canonical (STD-008 sec.17.3.1)");
            }
            boolean isNaN = (bits & 0x7F800000) == 0x7F800000 && (bits & 0x007FFFFF) != 0;
            if (isNaN && bits != 0x7FC00000) {
                throw new IOException("readFloat: non-canonical NaN 0x"
                        + String.format("%08X", bits) + " (canonical is 0x7FC00000)");
            }
            return Float.intBitsToFloat(bits);
        } catch (DerException e) {
            throw new IOException("readFloat: " + e.getMessage(), e);
        }
    }

    /** STD-008 sec.17.3.1: strict canonical IEEE-754 decode (8 bytes; rejects non-canonical NaN / {@code -0.0}). */
    double readDouble() throws IOException {
        try {
            byte[] content = reader.readOctetString();
            if (content.length != 8) {
                throw new IOException("readDouble: OCTET STRING must be 8 bytes, got " + content.length);
            }
            long bits = 0L;
            for (int i = 0; i < 8; i++) bits = (bits << 8) | (content[i] & 0xFF);
            if (bits == 0x8000000000000000L) {
                throw new IOException("readDouble: -0.0 bits are not canonical (STD-008 sec.17.3.1)");
            }
            boolean isNaN = (bits & 0x7FF0000000000000L) == 0x7FF0000000000000L
                         && (bits & 0x000FFFFFFFFFFFFFL) != 0L;
            if (isNaN && bits != 0x7FF8000000000000L) {
                throw new IOException("readDouble: non-canonical NaN 0x"
                        + String.format("%016X", bits) + " (canonical is 0x7FF8000000000000)");
            }
            return Double.longBitsToDouble(bits);
        } catch (DerException e) {
            throw new IOException("readDouble: " + e.getMessage(), e);
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

        if (CTX_ATOMIC.equals(tag)) {
            // [1] @AtomicSerial object: content is a MarshalledInstanceRecord SEQUENCE
            byte[] recBytes;
            try {
                recBytes = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read @AtomicSerial record bytes", e);
            }

            MarshalledInstanceRecord rec;
            try {
                rec = MarshalledInstanceRecord.decode(recBytes);
            } catch (DerException e) {
                throw new IOException("readObject: malformed MarshalledInstanceRecord", e);
            }

            // Resolve the concrete class from the embedded schema's leaf record
            String leafClassName;
            try {
                leafClassName = rec.decodeSchemaChain().get(0).className();
            } catch (DerException e) {
                throw new IOException("readObject: failed to decode embedded schema chain", e);
            }
            Class<?> leafClass;
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) cl = ClassLoader.getSystemClassLoader();
                leafClass = Class.forName(leafClassName, false, cl);
            } catch (ClassNotFoundException e) {
                throw e;
            }

            Object obj;
            try {
                MarshalledInstanceCodec.Result<?> result =
                        MarshalledInstanceCodec.decodeMarshalledInstance(rec, leafClass);
                obj = result.object();
            } catch (DerException e) {
                throw new IOException("readObject: decode failed for " + leafClassName, e);
            }

            return obj;
        }

        throw new IOException("readObject: unexpected context tag " + tag
                + " (expected [0],[1],[3],[5]); back-references are not supported (sec.15.3)");
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
