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

import net.jini.io.MarshalInstanceInput;
import org.apache.river.api.io.AtomicObjectInput;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputValidation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * DER implementation of {@link MarshalInstanceInput} and
 * {@link AtomicObjectInput} (JGDMS-STD-008 sec.13.3, B+C hybrid).
 *
 * <p>On construction all bytes from {@code objIn} are read eagerly into a
 * {@code byte[]} ({@code payloadBytes}). The {@code schemaBytes} parameter carries
 * the embedded schema that {@link net.jini.io.MarshalledInstance} holds as a first-class
 * field (promoted there by {@link DerMarshalInstanceOutput#getSchemaBytes()} at marshal
 * time). These two byte arrays together reconstruct the full
 * {@link MarshalledInstanceRecord} for decoding -- no full-record DER wrapper is needed.
 *
 * <h2>Decode path (STD-008 sec.13.5)</h2>
 * <p>
 * {@link #readObject(Class)} is the primary method called by
 * {@link net.jini.io.MarshalledInstance#get(ClassLoader, boolean, ClassLoader, Collection, Class)}
 * when it detects that this input stream implements {@link AtomicObjectInput}. It:
 * <ol>
 *   <li>Reconstructs a {@link MarshalledInstanceRecord} from
 *       ({@code payloadBytes}, {@code schemaBytes}). The digest is taken from the leaf
 *       record in the parsed schema chain (the {@code schemaDigest} field of
 *       {@code MarshalledInstance} is passed for construction but the decode logic
 *       re-derives it from parsing the chain in
 *       {@link MarshalledInstanceCodec#decodeMarshalledInstance}).</li>
 *   <li>Delegates to {@link MarshalledInstanceCodec#decodeMarshalledInstance(MarshalledInstanceRecord, Class)},
 *       which uses the {@code schemaBytes}-derived chain (embedded schema) to drive
 *       decoding -- per STD-006 sec.7.8 / STD-008 sec.13.5 normative rule.</li>
 * </ol>
 *
 * <h2>Unsupported primitives</h2>
 * <p>
 * Like the output side, DER operates at object granularity. All primitive
 * {@code ObjectInput} methods ({@code readInt}, {@code readUTF}, etc.) throw
 * {@link UnsupportedOperationException}.
 */
public final class DerMarshalInstanceInput implements MarshalInstanceInput, AtomicObjectInput {

    private final byte[]     payloadBytes;
    private final byte[]     schemaBytes;
    private final Collection context;
    private final InputStream objIn;  // kept for close()

    /**
     * Constructs a new input from an already-separated payload stream and schema bytes.
     *
     * <p>All bytes from {@code objIn} are read eagerly -- this is safe because the
     * stream wraps a {@code ByteArrayInputStream} (the parent's {@code payloadBytes} field)
     * and is bounded; no blocking I/O occurs.
     *
     * @param objIn       the stream carrying the payload bytes (must not be null)
     * @param schemaBytes the embedded schema bytes from the first-class
     *                    {@code MarshalledInstance.schemaBytes} field (must not be null;
     *                    must be the output of {@link DerMarshalInstanceOutput#getSchemaBytes()})
     * @param context     the serialization context collection; may be empty, must not be null
     * @throws IOException if reading from {@code objIn} fails
     */
    public DerMarshalInstanceInput(InputStream objIn, byte[] schemaBytes, Collection context)
            throws IOException {
        this.objIn       = Objects.requireNonNull(objIn,       "objIn");
        this.schemaBytes = Objects.requireNonNull(schemaBytes, "schemaBytes");
        this.context     = Objects.requireNonNull(context,     "context");
        this.payloadBytes = objIn.readAllBytes();
    }

    // -------------------------------------------------------------------------
    // AtomicObjectInput -- primary decode path
    // -------------------------------------------------------------------------

    /**
     * Decodes the DER-encoded object from {@code payloadBytes} using the embedded
     * {@code schemaBytes} (STD-006 sec.7.8 / STD-008 sec.13.5 normative rule: the
     * embedded schema is authoritative).
     *
     * <p>Reconstruction: a {@link MarshalledInstanceRecord} is built from
     * {@code payloadBytes} and {@code schemaBytes} by parsing the schema chain to
     * recover the leaf digest, then assembling the record via the canonical constructor.
     * {@link MarshalledInstanceCodec#decodeMarshalledInstance} then uses the
     * embedded chain from that record to drive {@link au.net.zeus.jgdms.der.object.ObjectCodec}.
     *
     * @param <T>  the expected type
     * @param type the expected class
     * @return the deserialized object
     * @throws IOException            if the record is malformed or construction fails
     * @throws ClassNotFoundException if a class named in the schema cannot be loaded
     */
    @Override
    public <T> T readObject(Class<T> type) throws IOException, ClassNotFoundException {
        try {
            // Reconstruct MarshalledInstanceRecord from the two separate first-class fields.
            // Parse the schema chain to derive the leaf digest (needed by the canonical
            // constructor), then assemble the record.
            //
            // Use a temporary record to decode the schema chain and get the leaf digest.
            // We need a 32-byte digest for the constructor -- parse schemaBytes to get it.
            au.net.zeus.jgdms.der.DerReader chainReader =
                    new au.net.zeus.jgdms.der.DerReader(schemaBytes);
            au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord leafRecord =
                    au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord.decode(chainReader);
            byte[] leafDigest = leafRecord.schemaDigest();

            MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                    payloadBytes,
                    schemaBytes,
                    leafDigest,
                    Optional.empty(),
                    MarshalledInstanceRecord.PAYLOAD_FORMAT);

            return MarshalledInstanceCodec.decodeMarshalledInstance(rec, type).object();
        } catch (au.net.zeus.jgdms.der.DerException e) {
            throw new IOException("DER decoding failed: " + e.getMessage(), e);
        }
    }

    /**
     * No-op: DER does not use validation callbacks. {@link net.jini.io.MarshalledInstance}'s
     * get() path does not call this; provided for interface completeness.
     *
     * @throws NotActiveException never thrown
     * @throws InvalidObjectException never thrown
     */
    @Override
    public void registerValidation(ObjectInputValidation object, int priority)
            throws NotActiveException, InvalidObjectException {
        // DER MarshalledInstance does not support post-deserialization validation callbacks.
    }

    // -------------------------------------------------------------------------
    // ObjectInput -- readObject() delegates to readObject(Object.class)
    // -------------------------------------------------------------------------

    /**
     * Delegates to {@link #readObject(Class) readObject(Object.class)}.
     * {@link net.jini.io.MarshalledInstance#get} uses the {@link AtomicObjectInput} cast
     * path ({@code readObject(type)}) in preference to this method when
     * {@code in instanceof AtomicObjectInput}.
     */
    @Override
    public Object readObject() throws ClassNotFoundException, IOException {
        return readObject(Object.class);
    }

    // -------------------------------------------------------------------------
    // MarshalInstanceInput
    // -------------------------------------------------------------------------

    /**
     * No-op: DER carries the schema, not a codebase URL annotation.
     * The parent's get() calls this unconditionally before reading.
     */
    @Override
    public void useCodebaseAnnotations() {
        // DER has no codebase annotations -- the embedded schema provides
        // data-independence (STD-006 S8).
    }

    // -------------------------------------------------------------------------
    // ObjectStreamContext
    // -------------------------------------------------------------------------

    @Override
    public Collection getObjectStreamContext() {
        return context;
    }

    // -------------------------------------------------------------------------
    // Closeable
    // -------------------------------------------------------------------------

    @Override
    public void close() throws IOException {
        objIn.close();
    }

    // -------------------------------------------------------------------------
    // Unsupported ObjectInput primitives -- DER is object-granularity only
    // -------------------------------------------------------------------------

    @Override public int read() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public int read(byte[] b) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public int read(byte[] b, int off, int len) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public long skip(long n) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public int available() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void readFully(byte[] b) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void readFully(byte[] b, int off, int len) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public int skipBytes(int n) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public boolean readBoolean() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public byte readByte() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public int readUnsignedByte() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public short readShort() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public int readUnsignedShort() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public char readChar() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public int readInt() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public long readLong() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public float readFloat() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public double readDouble() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public String readLine() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public String readUTF() throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }
}
