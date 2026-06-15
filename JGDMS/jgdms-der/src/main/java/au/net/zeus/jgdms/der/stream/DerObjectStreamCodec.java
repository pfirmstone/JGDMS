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
import java.util.IdentityHashMap;
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
 *   [2] PRIMITIVE   -- back-reference; content = INTEGER (handle into handle table)
 *   [3] PRIMITIVE   -- java.lang.String; content = UTF8String value bytes
 *   [5] PRIMITIVE   -- byte[]; content = OCTET STRING value bytes
 * </pre>
 * Context class = 0x80 (bits 7-6 set). Constructed bit = 0x20 set for [1].
 * Single-byte tags (all tag numbers <= 30):
 * <pre>
 *   [0] primitive  -> 0x80
 *   [1] constructed -> 0xa1
 *   [2] primitive  -> 0x82
 *   [3] primitive  -> 0x83
 *   [5] primitive  -> 0x85
 * </pre>
 *
 * <h2>Handle table (acyclic shared references, inc-1)</h2>
 * <p>
 * Write side: {@link IdentityHashMap} mapping object -> integer handle (assigned in
 * write order, starting at 0). Only {@code @AtomicSerial} object instances are
 * tracked (String and byte[] use value semantics -- no dedup). When writing an
 * {@code @AtomicSerial} object seen before, a [2] back-reference TLV is written
 * instead of a new [1] record.
 * <p>
 * Read side: {@link ArrayList} indexed by handle. When a [1] item is decoded,
 * the reconstructed object is appended (assigned the next handle) BEFORE returning.
 * When a [2] item is decoded, the object at the given index is returned directly.
 * Forward references are not supported (inc-2/3).
 */
final class DerObjectStreamCodec {

    // =========================================================================
    // Context tags (single byte, pre-computed)
    // =========================================================================

    /** [0] primitive context tag: NULL reference. */
    private static final Tag CTX_NULL        = new Tag(Tag.CLASS_CONTEXT, false, 0);
    /** [1] constructed context tag: @AtomicSerial object (MarshalledInstanceRecord). */
    private static final Tag CTX_ATOMIC      = new Tag(Tag.CLASS_CONTEXT, true,  1);
    /** [2] primitive context tag: back-reference INTEGER. */
    private static final Tag CTX_BACKREF     = new Tag(Tag.CLASS_CONTEXT, false, 2);
    /** [3] primitive context tag: java.lang.String (UTF8String content). */
    private static final Tag CTX_STRING      = new Tag(Tag.CLASS_CONTEXT, false, 3);
    /** [5] primitive context tag: byte[] (OCTET STRING content). */
    private static final Tag CTX_BYTES       = new Tag(Tag.CLASS_CONTEXT, false, 5);

    // =========================================================================
    // Write side state
    // =========================================================================

    /** Output accumulator for the write side. */
    private final List<byte[]> writeBuffer = new ArrayList<>();

    /** Handle table: object identity -> handle integer (write side). */
    private final IdentityHashMap<Object, Integer> writeHandles = new IdentityHashMap<>();

    // =========================================================================
    // Read side state
    // =========================================================================

    /** DER reader over the input bytes (read side). */
    private DerReader reader;

    /** Handle table: handle index -> object (read side). */
    private final List<Object> readHandles = new ArrayList<>();

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
     *   <li>known @AtomicSerial handle -> [2] back-reference</li>
     *   <li>new @AtomicSerial instance -> [1] MarshalledInstanceRecord</li>
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

        // @AtomicSerial: check handle table first
        Class<?> cls = obj.getClass();
        if (!cls.isAnnotationPresent(AtomicSerial.class)) {
            throw new UnsupportedOperationException(
                    "DER stream inc-1: unsupported object type "
                    + cls.getName()
                    + "; @AtomicSerial-restricted");
        }

        // Back-reference if already in table
        Integer existingHandle = writeHandles.get(obj);
        if (existingHandle != null) {
            byte[] handleTlv = DerWriter.writeInteger(BigInteger.valueOf(existingHandle));
            writeBuffer.add(DerWriter.writeTlv(CTX_BACKREF, handleTlv));
            return;
        }

        // New @AtomicSerial object: assign handle, then encode
        int handle = writeHandles.size();
        writeHandles.put(obj, handle);

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

        if (CTX_BACKREF.equals(tag)) {
            // [2] back-reference: content is a DER INTEGER
            byte[] content;
            try {
                content = reader.readRawContent(hdr.contentLength());
            } catch (DerException e) {
                throw new IOException("readObject: failed to read back-reference content", e);
            }
            DerReader intReader = new DerReader(content);
            BigInteger handle;
            try {
                handle = intReader.readInteger();
            } catch (DerException e) {
                throw new IOException("readObject: malformed back-reference INTEGER", e);
            }
            int h = handle.intValueExact();
            if (h < 0 || h >= readHandles.size()) {
                throw new IOException("readObject: back-reference handle " + h
                        + " out of bounds (table size=" + readHandles.size() + ")");
            }
            return readHandles.get(h);
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

            // Register in handle table AFTER construction (acyclic inc-1)
            readHandles.add(obj);
            return obj;
        }

        throw new IOException("readObject: unexpected context tag " + tag
                + " (expected [0],[1],[2],[3],[5])");
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

    /**
     * Returns the total number of bytes currently accumulated in the write buffer,
     * without draining.
     */
    int writtenByteCount() {
        int total = 0;
        for (byte[] chunk : writeBuffer) {
            total += chunk.length;
        }
        return total;
    }
}
