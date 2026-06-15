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

import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.util.Objects;

/**
 * DER encoding implementation of {@link ObjectOutput} (JGDMS-STD-008 sec.15.1,
 * Increment 1).
 *
 * <h2>Direct interface implementation</h2>
 * <p>
 * This class implements {@link java.io.ObjectOutput} <em>directly</em> -- it does NOT
 * extend {@link java.io.ObjectOutputStream}. Per STD-008 sec.15.1, the DER streams
 * must not carry any Java Object Serialization machinery: no stream header, no
 * writeObjectOverride, no override-mode state. The JERI contract depends only on the
 * {@code ObjectOutput}/{@code ObjectInput} interfaces; implementing them directly is
 * cleaner and aligns with the 4.0.0 "no Java Serialization" thesis.
 *
 * <h2>Typed primitives (positional, sec.15.2)</h2>
 * <p>
 * Primitives are written as STD-006 DER TLVs in call order:
 * <ul>
 *   <li>{@code writeBoolean} -- BOOLEAN</li>
 *   <li>{@code writeByte}/{@code writeShort}/{@code writeInt}/{@code writeLong} -- INTEGER</li>
 *   <li>{@code writeUTF} -- UTF8String</li>
 *   <li>{@code write(byte[])}/{@code write(byte[],int,int)} -- OCTET STRING</li>
 *   <li>{@code write(int)} -- single-byte INTEGER (low 8 bits as signed value)</li>
 * </ul>
 * These are POSITIONAL: the reader must call the matching typed read in the same order.
 * They carry no item tag beyond the natural DER universal tag.
 *
 * <h2>Deferred types</h2>
 * <p>
 * {@link #writeFloat}, {@link #writeDouble}, and {@link #writeChar} throw
 * {@link UnsupportedOperationException} per STD-006 sec.7.6. {@link #writeBytes(String)}
 * and {@link #writeChars(String)} also throw (ambiguous legacy encoding, not used by
 * JERI marshalling).
 *
 * <h2>Buffering and flush</h2>
 * <p>
 * The codec accumulates encoded TLVs in memory. {@link #flush()} writes all accumulated
 * bytes to the wrapped {@link OutputStream} and flushes it. {@link #close()} flushes
 * then closes the underlying stream.
 *
 * @see DerMarshalInputStream
 * @see DerObjectStreamCodec
 */
public final class DerMarshalOutputStream implements ObjectOutput {

    private final OutputStream out;
    private final DerObjectStreamCodec codec;

    /**
     * Constructs a DER object-stream writer over {@code out}.
     * <p>
     * No Java Object Serialization header or preamble is written.
     *
     * @param out the underlying output stream (must not be null)
     */
    public DerMarshalOutputStream(OutputStream out) {
        this.out   = Objects.requireNonNull(out, "out");
        this.codec = new DerObjectStreamCodec();
    }

    // =========================================================================
    // Object writing (self-describing, sec.15.2)
    // =========================================================================

    /**
     * Writes an object item. The item is self-describing: the context tag indicates
     * the kind. See {@link DerObjectStreamCodec} for the tag-to-kind table.
     *
     * @param obj the object to write (null, {@code @AtomicSerial}, String, or byte[])
     * @throws IOException if encoding fails
     * @throws UnsupportedOperationException if the object type is not supported
     *         (inc-1: only null, @AtomicSerial, String, byte[] are supported)
     */
    @Override
    public void writeObject(Object obj) throws IOException {
        codec.writeObject(obj);
    }

    // =========================================================================
    // Typed primitive writes (positional, DER-encoded)
    // =========================================================================

    /** Writes a boolean as a DER BOOLEAN TLV. */
    @Override
    public void writeBoolean(boolean val) throws IOException {
        codec.writeBoolean(val);
    }

    /** Writes a byte as a DER INTEGER TLV (signed value). */
    @Override
    public void writeByte(int val) throws IOException {
        codec.writeByte(val);
    }

    /** Writes a short as a DER INTEGER TLV. */
    @Override
    public void writeShort(int val) throws IOException {
        codec.writeShort(val);
    }

    /** Writes an int as a DER INTEGER TLV. */
    @Override
    public void writeInt(int val) throws IOException {
        codec.writeInt(val);
    }

    /** Writes a long as a DER INTEGER TLV. */
    @Override
    public void writeLong(long val) throws IOException {
        codec.writeLong(val);
    }

    /** Writes a String as a DER UTF8String TLV (positional, not self-describing). */
    @Override
    public void writeUTF(String str) throws IOException {
        Objects.requireNonNull(str, "str");
        codec.writeUTF(str);
    }

    /**
     * Writes a byte array as a DER OCTET STRING TLV.
     *
     * @param buf the bytes to write (must not be null)
     */
    @Override
    public void write(byte[] buf) throws IOException {
        Objects.requireNonNull(buf, "buf");
        codec.writeBytes(buf, 0, buf.length);
    }

    /**
     * Writes a slice of a byte array as a DER OCTET STRING TLV.
     *
     * @param buf the source array
     * @param off offset into buf
     * @param len number of bytes to write
     */
    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        Objects.requireNonNull(buf, "buf");
        codec.writeBytes(buf, off, len);
    }

    /**
     * Writes a single byte (low 8 bits of {@code val}) as a DER INTEGER TLV.
     * <p>
     * The value is treated as a signed byte. The reader's {@link DerMarshalInputStream#read()}
     * returns it as an unsigned int (0-255), matching the {@link java.io.ObjectInput#read()} contract.
     *
     * @param val the byte value (only the low 8 bits are used)
     */
    @Override
    public void write(int val) throws IOException {
        codec.writeSingleByte(val);
    }

    // =========================================================================
    // Strict canonical float/double/char (STD-008 sec.17.3, S7.6 lifted)
    // =========================================================================

    @Override
    public void writeFloat(float val) {
        codec.writeFloat(val);
    }

    @Override
    public void writeDouble(double val) {
        codec.writeDouble(val);
    }

    @Override
    public void writeChar(int val) {
        codec.writeChar(val);
    }

    /**
     * Throws {@link UnsupportedOperationException}: ambiguous legacy encoding, not
     * used by JERI marshalling.
     */
    @Override
    public void writeChars(String str) {
        throw new UnsupportedOperationException(
                "DER stream: writeChars(String) not supported; use writeUTF or write(byte[])");
    }

    /**
     * Throws {@link UnsupportedOperationException}: ambiguous legacy encoding, not
     * used by JERI marshalling.
     */
    @Override
    public void writeBytes(String str) {
        throw new UnsupportedOperationException(
                "DER stream: writeBytes(String) not supported; use write(byte[]) or writeUTF");
    }

    // =========================================================================
    // Flush and close
    // =========================================================================

    /**
     * Flushes all accumulated DER-encoded bytes to the underlying output stream.
     *
     * @throws IOException if the underlying stream throws
     */
    @Override
    public void flush() throws IOException {
        codec.drainTo(out);
        out.flush();
    }

    /**
     * Flushes all accumulated bytes and closes the underlying output stream.
     *
     * @throws IOException if flush or close of the underlying stream throws
     */
    @Override
    public void close() throws IOException {
        flush();
        out.close();
    }
}
