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

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInput;
import java.io.ObjectInputValidation;
import java.util.Objects;
import org.apache.river.api.io.AtomicObjectInput;

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
 * <h2>{@code float}/{@code double}/{@code char}</h2>
 * <p>
 * {@link #readFloat}, {@link #readDouble}, and {@link #readChar} decode with the strict
 * canonical rules of STD-008 sec.17.3 (S7.6 lifted): non-canonical NaN, {@code -0.0} bits,
 * wrong length, and surrogate/out-of-range codepoints are rejected fail-secure.
 * {@link #readLine} and {@link #skipBytes}/{@link #skip} throw
 * {@link UnsupportedOperationException} (not used by JERI unmarshalling).
 *
 * <h2>Atomic per-object validation ({@link AtomicObjectInput})</h2>
 * <p>
 * This stream implements {@link AtomicObjectInput} (STD-008 sec.18.2): the DER codec
 * validates every {@code @AtomicSerial} object atomically during construction (its
 * {@code check(GetArg)} runs before the object is returned, and the codec only constructs
 * {@code @AtomicSerial}-annotated classes -- no arbitrary gadget graphs), so no
 * partially-constructed object can escape. Implementing this marker makes a DER-exported
 * service satisfy a {@link net.jini.core.constraint.AtomicInputValidation#YES} requirement,
 * and lets the JERI in-band reader ({@code Util.unmarshalValue}) and
 * {@code MarshalledInstance.get} route DER object reads through the type-checked
 * {@link #readObject(Class)}.
 *
 * @see DerMarshalOutputStream
 * @see DerObjectStreamCodec
 */
public final class DerMarshalInputStream implements AtomicObjectInput {

    private final DerObjectStreamCodec codec;
    private final InputStream underlying;

    /**
     * Completion sink for this decode unit. {@code @AtomicSerial} objects decoded from
     * this stream register post-graph callbacks here (via {@link #registerValidation} or
     * the {@link net.jini.io.context.DeserializationCompletion} context element exposed by
     * the active {@code DerGetArg}); {@link #endDecodeUnit()} fires them before close.
     */
    private final DerDecodeUnit decodeUnit = new DerDecodeUnit();

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
        this(in, ResolutionContext.NONE);
    }

    /**
     * Constructs a DER object-stream reader over {@code in}, resolving class names against the
     * endpoint-assigned {@link ResolutionContext} (the receiving endpoint's loader) rather than
     * the thread-context loader -- the JGDMS class-resolution discipline (see
     * {@link ResolutionContext}).
     *
     * @param in         the source of DER-encoded data (must not be null)
     * @param resolution the endpoint-assigned resolution context (must not be null)
     * @throws IOException if reading from {@code in} fails
     */
    public DerMarshalInputStream(InputStream in, ResolutionContext resolution) throws IOException {
        this(in, resolution, DerInputLimits.DEFAULT);
    }

    /**
     * As {@link #DerMarshalInputStream(InputStream, ResolutionContext)} with explicit DoS limits --
     * the per-deployment input cap the JERI invocation layer obtained from its configuration.
     *
     * @param in         the source of DER-encoded data (must not be null)
     * @param resolution the endpoint-assigned resolution context (must not be null)
     * @param limits     the DoS limits (must not be null; {@link DerInputLimits#DEFAULT} for the JVM default)
     * @throws IOException if reading from {@code in} fails or it exceeds the byte cap
     */
    public DerMarshalInputStream(InputStream in, ResolutionContext resolution, DerInputLimits limits)
            throws IOException {
        this(in, resolution, limits, true);
    }

    /** Private: selects between the object-stream format and record-level capture. */
    private DerMarshalInputStream(InputStream in, ResolutionContext resolution,
                                  DerInputLimits limits, boolean streamFormat)
            throws IOException {
        this.underlying = Objects.requireNonNull(in, "in");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(limits, "limits");
        byte[] buf = limits.readAllBytesBounded(in); // bounded: refuse oversize input (DoS)
        this.codec = new DerObjectStreamCodec(streamFormat, limits.maxInputBytes());
        this.codec.initReader(buf, decodeUnit, resolution);
    }

    /**
     * Creates a <b>record-level capture</b> reader: the standalone
     * {@code MarshalledInstance} capture context (STD-006 Appendix C sec.C.1.2 item 3),
     * which is NOT a DER object stream — no {@code [15]} stream-format version octet is
     * expected or accepted, and no {@code SchemaChainRef} dedup production is valid;
     * canonical record-level full forms only.
     *
     * <p>Used ONLY by {@code DerMarshalInstanceInput}'s empty-schema-sentinel path (a
     * bare proxy / String / byte[] / enum captured inside a {@code MarshalledInstance}).
     * Every transport stream uses the public constructors, which enforce the mandatory
     * stream format (version octet + dedup, sec.C.9).
     *
     * @param in         the captured payload bytes (must not be null)
     * @param resolution the endpoint-assigned resolution context (must not be null)
     * @return a reader for record-level canonical bytes with no stream framing
     * @throws IOException if reading from {@code in} fails
     */
    public static DerMarshalInputStream recordLevelCapture(InputStream in,
                                                           ResolutionContext resolution)
            throws IOException {
        return new DerMarshalInputStream(in, resolution, DerInputLimits.DEFAULT, false);
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

    /**
     * Reads the next self-describing object and verifies its runtime type is assignable to
     * {@code type} (the {@link AtomicObjectInput} contract, STD-008 sec.18.2).
     *
     * <p>The DER codec only constructs {@code @AtomicSerial}-annotated classes, each
     * validated atomically by its {@code check(GetArg)} during construction, so no
     * partially-constructed object can escape; this method then rejects a fully-validated
     * result whose type is not the one the caller expected. A {@code null} item (DER NULL)
     * is returned as {@code null}.
     *
     * @param <T>  the expected type
     * @param type the expected class (must not be null)
     * @return the decoded object, cast to {@code T}, or {@code null}
     * @throws InvalidObjectException if the decoded object is not assignable to {@code type}
     * @throws IOException            if the stream is malformed
     * @throws ClassNotFoundException if a class named in the embedded schema cannot be loaded
     */
    /**
     * Reads the next self-describing item as a <b>class-free scalar</b> gated on the caller-supplied
     * declared {@code wireType}, reconstructing NO object and loading NO class (STD-011 §B2 class-free
     * candidate projection). Delegates to {@link DerObjectStreamCodec#readScalarClassFree}: only the
     * inert scalar kinds decode; a declared non-scalar, or an item whose actual context tag does not
     * match the declared scalar type (including a constructed {@code [1]/[7]/[8]/[9]/[16]} item),
     * is fail-closed with an {@link IOException} <b>before</b> any reconstruction path is reached. A
     * {@code [0]} NULL item returns {@code null}.
     *
     * @param declaredWireType the field's declared wire type (must not be {@code null})
     * @return the decoded scalar (boxed) value, or {@code null} for a NULL item
     * @throws IOException if the item is not a class-free scalar of the declared type, or is
     *                     malformed (fail-closed)
     */
    public Object readScalarClassFree(String declaredWireType) throws IOException {
        Objects.requireNonNull(declaredWireType, "declaredWireType");
        return codec.readScalarClassFree(declaredWireType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readObject(Class<T> type) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(type, "type");
        Object obj = codec.readObject();
        if (obj != null && !type.isInstance(obj)) {
            throw new InvalidObjectException(
                    "DER stream: decoded object of type " + obj.getClass().getName()
                    + " is not assignable to expected type " + type.getName());
        }
        return (T) obj;
    }

    /**
     * Registers a post-deserialization callback for this decode unit. Unlike
     * {@link java.io.ObjectInputStream}, the DER codec has no automatic "run validations
     * when the outermost {@code readObject} returns" trigger, so the callbacks are held
     * and fired explicitly by {@link #endDecodeUnit()} (called by the JERI framing layer
     * after the value-sequence is read and before this stream is closed). Each
     * {@code @AtomicSerial} object is itself validated atomically during construction (its
     * {@code check(GetArg)} runs before it is returned); this callback channel is for
     * cross-object, end-of-unit work such as the client DGC batched {@code dirty}.
     *
     * @param object   the callback (must not be {@code null})
     * @param priority run-order priority; higher priorities run first
     * @throws InvalidObjectException if {@code object} is {@code null}
     * @throws NotActiveException     if this decode unit has already completed
     */
    @Override
    public void registerValidation(ObjectInputValidation object, int priority)
            throws NotActiveException, InvalidObjectException {
        decodeUnit.registerCompletion(object, priority);
    }

    /**
     * Runs the callbacks registered for this decode unit (in decreasing-priority order),
     * exactly once. The JERI framing layer calls this after the whole argument/result
     * value-sequence has been read and BEFORE {@link #close()} (close drives the mux
     * acknowledgement), so the client DGC {@code dirty} is issued before the receiver
     * acknowledges receipt (SRC&nbsp;RR-116 transmit-race invariant). Idempotent.
     */
    @Override
    public void endDecodeUnit() throws IOException {
        decodeUnit.flush();
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

    /** Strict canonical IEEE-754 decode (STD-008 sec.17.3.1; S7.6 lifted). */
    @Override
    public float readFloat() throws IOException {
        return codec.readFloat();
    }

    /** Strict canonical IEEE-754 decode (STD-008 sec.17.3.1; S7.6 lifted). */
    @Override
    public double readDouble() throws IOException {
        return codec.readDouble();
    }

    /** Unicode codepoint INTEGER decode (STD-008 sec.17.3.2; S7.6 lifted). */
    @Override
    public char readChar() throws IOException {
        return codec.readChar();
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
     * <p>As a safety net, the decode unit is flushed first (so any registered completion
     * callbacks run before the close that drives the acknowledgement, even if the framing
     * layer omitted the explicit {@link #endDecodeUnit()} call). The flush is idempotent,
     * so on the normal path -- where {@code endDecodeUnit()} already ran -- this is a
     * no-op and does not invert the dirty-before-ack ordering. The underlying stream is
     * always closed, even if a completion callback throws.
     *
     * @throws IOException if a completion callback fails, or if closing the underlying
     *                     stream throws
     */
    @Override
    public void close() throws IOException {
        try {
            decodeUnit.flush();
        } finally {
            underlying.close();
        }
    }
}
