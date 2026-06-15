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
import java.io.InputStream;
import java.io.ObjectInput;
import java.util.Objects;

/**
 * DER decoding implementation of {@link ObjectInput} (JGDMS-STD-008 sec.15.1,
 * Increment 1).
 *
 * <h2>Direct interface implementation</h2>
 * <p>
 * This class implements {@link java.io.ObjectInput} <em>directly</em> -- it does NOT
 * extend {@link java.io.ObjectInputStream}. Per STD-008 sec.15.1, the DER streams must
 * not carry any Java Object Serialization machinery. The JERI contract depends only on
 * the {@code ObjectOutput}/{@code ObjectInput} interfaces; implementing them directly is
 * cleaner and aligns with the 4.0.0 "no Java Serialization" thesis.
 *
 * <h2>Initialisation</h2>
 * <p>
 * The constructor reads ALL bytes from the supplied {@link InputStream} eagerly into a
 * buffer and feeds them to {@link DerObjectStreamCodec}. This is safe because a DER stream
 * is a bounded call-buffer (the caller has already framed the data); a streaming approach
 * would require additional framing beyond the DER TLV layer.
 *
 * <h2>Typed primitives (positional, sec.15.2)</h2>
 * <p>
 * Each typed read decodes the next DER TLV from the buffer and range-checks the value
 * against the target type. INTEGER values that do not fit the target type are rejected
 * with {@link IOException} (overflow, per STD-006). The reader must be called in the
 * same positional order as the corresponding writes.
 *
 * <h2>Deferred types</h2>
 * <p>
 * {@link #readFloat}, {@link #readDouble}, and {@link #readChar} throw
 * {@link UnsupportedOperationException} per STD-006 sec.7.6. {@link #readLine} and
 * {@link #skipBytes}/{@link #skip} throw {@link UnsupportedOperationException} (not
 * used by JERI unmarshalling; skipBytes also documented as IOException variant).
 *
 * @see DerMarshalOutputStream
 * @see DerObjectStreamCodec
 */
public final class DerMarshalInputStream implements ObjectInput {

    private final DerObjectStreamCodec codec;
    private final InputStream underlying;

    /**
     * Constructs a DER object-stream reader over {@code in}.
     * <p>
     * All bytes are read eagerly from {@code in} and handed to the codec.
     * No Java Object Serialization header is expected.
     *
     * @param in the source of DER-encoded data (must not be null)
     * @throws IOException if reading from {@code in} fails
     */
    public DerMarshalInputStream(InputStream in) throws IOException {
        this.underlying = Objects.requireNonNull(in, "in");
        byte[] buf = in.readAllBytes();
        this.codec = new DerObjectStreamCodec();
        this.codec.initReader(buf);
    }

    // =========================================================================
    // Object reading (self-describing, sec.15.2)
    // =========================================================================

    /**
     * Reads the next self-describing object item from the DER stream.
     * <p>
     * Dispatches on the context tag. See {@link DerObjectStreamCodec} for the tag-to-kind
     * table. Returns {@code null} for a [0] NULL item.
     *
     * @return the decoded object (may be null)
     * @throws IOException if the stream is malformed
     * @throws ClassNotFoundException if an {@code @AtomicSerial} class named in the schema
     *                                cannot be loaded
     */
    @Override
    public Object readObject() throws ClassNotFoundException, IOException {
        return codec.readObject();
    }

    // =========================================================================
    // Typed primitive reads (positional, DER-decoded)
    // =========================================================================

    /** Reads a DER BOOLEAN TLV and returns the value. */
    @Override
    public boolean readBoolean() throws IOException {
        return codec.readBoolean();
    }

    /**
     * Reads a DER INTEGER TLV and returns it as a {@code byte}.
     * Throws {@link IOException} if the value is outside [-128, 127].
     */
    @Override
    public byte readByte() throws IOException {
        return codec.readByte();
    }

    /**
     * Reads a DER INTEGER TLV and returns it as an unsigned byte (0-255).
     * Derives from {@link #readByte()}.
     */
    @Override
    public int readUnsignedByte() throws IOException {
        return codec.readByte() & 0xFF;
    }

    /**
     * Reads a DER INTEGER TLV and returns it as a {@code short}.
     * Throws {@link IOException} if the value is outside [-32768, 32767].
     */
    @Override
    public short readShort() throws IOException {
        return codec.readShort();
    }

    /**
     * Reads a DER INTEGER TLV and returns it as an unsigned short (0-65535).
     * Derives from {@link #readShort()}.
     */
    @Override
    public int readUnsignedShort() throws IOException {
        return codec.readShort() & 0xFFFF;
    }

    /**
     * Reads a DER INTEGER TLV and returns it as an {@code int}.
     * Throws {@link IOException} if the value does not fit in an int.
     */
    @Override
    public int readInt() throws IOException {
        return codec.readInt();
    }

    /**
     * Reads a DER INTEGER TLV and returns it as a {@code long}.
     * Throws {@link IOException} if the value does not fit in a long.
     */
    @Override
    public long readLong() throws IOException {
        return codec.readLong();
    }

    /**
     * Throws {@link UnsupportedOperationException}: float is deferred in
     * STD-006 sec.7.6.
     */
    @Override
    public float readFloat() throws IOException {
        throw new UnsupportedOperationException(
                "DER stream: float/double/char deferred (STD-006 S7.6)");
    }

    /**
     * Throws {@link UnsupportedOperationException}: double is deferred in
     * STD-006 sec.7.6.
     */
    @Override
    public double readDouble() throws IOException {
        throw new UnsupportedOperationException(
                "DER stream: float/double/char deferred (STD-006 S7.6)");
    }

    /**
     * Throws {@link UnsupportedOperationException}: char is deferred in
     * STD-006 sec.7.6.
     */
    @Override
    public char readChar() throws IOException {
        throw new UnsupportedOperationException(
                "DER stream: float/double/char deferred (STD-006 S7.6)");
    }

    /**
     * Reads a DER UTF8String TLV and returns the decoded string.
     * <p>
     * This is the positional inverse of {@link DerMarshalOutputStream#writeUTF(String)}.
     * It is NOT self-describing -- the reader must know to call {@code readUTF} at this
     * position.
     */
    @Override
    public String readUTF() throws IOException {
        return codec.readUTF();
    }

    /**
     * Throws {@link UnsupportedOperationException}: deprecated and not used by JERI.
     */
    @Override
    public String readLine() throws IOException {
        throw new UnsupportedOperationException(
                "DER stream: readLine() is deprecated and not supported");
    }

    // =========================================================================
    // Byte-array reads
    // =========================================================================

    /**
     * Reads a DER OCTET STRING TLV and copies the content into {@code buf}.
     * <p>
     * Expects exactly {@code buf.length} bytes in the OCTET STRING.
     *
     * @param buf destination array (must not be null)
     * @throws IOException if the OCTET STRING length does not match {@code buf.length}
     */
    @Override
    public void readFully(byte[] buf) throws IOException {
        Objects.requireNonNull(buf, "buf");
        byte[] decoded = codec.readOctetString();
        if (decoded.length != buf.length) {
            throw new IOException("readFully: expected " + buf.length
                    + " bytes but decoded OCTET STRING has " + decoded.length);
        }
        System.arraycopy(decoded, 0, buf, 0, buf.length);
    }

    /**
     * Reads a DER OCTET STRING TLV and copies the content into {@code buf[off..off+len-1]}.
     * <p>
     * Expects exactly {@code len} bytes in the OCTET STRING.
     *
     * @param buf destination array
     * @param off offset into buf
     * @param len expected number of bytes
     * @throws IOException on length mismatch or decode error
     */
    @Override
    public void readFully(byte[] buf, int off, int len) throws IOException {
        Objects.requireNonNull(buf, "buf");
        byte[] decoded = codec.readOctetString();
        if (decoded.length != len) {
            throw new IOException("readFully(off,len): expected " + len
                    + " bytes but decoded OCTET STRING has " + decoded.length);
        }
        System.arraycopy(decoded, 0, buf, off, len);
    }

    /**
     * Reads a DER OCTET STRING TLV and copies its content into {@code buf}.
     * Returns the number of bytes copied (the full OCTET STRING length, or -1 at
     * end of stream).
     *
     * @param buf destination array
     * @return bytes copied, or -1 if no more data is available
     * @throws IOException on decode error
     */
    @Override
    public int read(byte[] buf) throws IOException {
        Objects.requireNonNull(buf, "buf");
        if (codec.available() == 0) return -1;
        byte[] decoded = codec.readOctetString();
        int n = Math.min(decoded.length, buf.length);
        System.arraycopy(decoded, 0, buf, 0, n);
        return n;
    }

    /**
     * Reads a DER OCTET STRING TLV and copies up to {@code len} bytes into
     * {@code buf[off..]}.
     *
     * @param buf destination array
     * @param off offset
     * @param len max bytes to copy
     * @return bytes copied, or -1 at end of stream
     * @throws IOException on decode error
     */
    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        Objects.requireNonNull(buf, "buf");
        if (codec.available() == 0) return -1;
        byte[] decoded = codec.readOctetString();
        int n = Math.min(decoded.length, len);
        System.arraycopy(decoded, 0, buf, off, n);
        return n;
    }

    /**
     * Reads a single byte (inverse of {@link DerMarshalOutputStream#write(int)}).
     * <p>
     * The value is returned as an unsigned int (0-255) or -1 at end of stream.
     *
     * @return the byte value as an unsigned int (0-255), or -1 at end of stream
     * @throws IOException on decode error
     */
    @Override
    public int read() throws IOException {
        if (codec.available() == 0) return -1;
        return codec.readSingleByte();
    }

    /**
     * Throws {@link UnsupportedOperationException}: skip is not used by JERI unmarshalling;
     * positional DER streams cannot meaningfully skip typed values.
     */
    @Override
    public long skip(long n) throws IOException {
        throw new UnsupportedOperationException(
                "DER stream: skip() not supported on positional DER stream");
    }

    /**
     * Throws {@link UnsupportedOperationException}: skipBytes is not used by JERI unmarshalling.
     */
    @Override
    public int skipBytes(int n) throws IOException {
        throw new UnsupportedOperationException(
                "DER stream: skipBytes() not supported on positional DER stream");
    }

    /**
     * Returns a non-zero value if there is more data available, zero otherwise.
     * Used by callers to check end-of-stream before attempting a read.
     */
    @Override
    public int available() throws IOException {
        return codec.available();
    }

    // =========================================================================
    // Close
    // =========================================================================

    /**
     * Closes the underlying input stream.
     *
     * @throws IOException if closing the underlying stream throws
     */
    @Override
    public void close() throws IOException {
        underlying.close();
    }
}
