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

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import net.jini.io.MarshalInstanceInput;
import org.apache.river.api.io.AtomicObjectInput;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputValidation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

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

    /**
     * Per-thread decode-recursion depth for the cross-stream MarshalledInstance-in-MarshalledInstance
     * guard (see {@link #readObject(Class)}). A {@link ScopedValue} (not a {@code ThreadLocal} --
     * virtual threads) bound around each decode and read by any nested decode reached via
     * {@code serviceProxy.get()} on the same call stack.
     */
    private static final ScopedValue<Integer> DECODE_DEPTH = ScopedValue.newInstance();

    private final byte[]     payloadBytes;
    private final byte[]     schemaBytes;
    private final Collection context;
    private final InputStream objIn;  // kept for close()
    /**
     * The endpoint-assigned {@link ResolutionContext} (the unmarshalling stream's
     * {default, verifier} loaders + integrity setting). Class names resolve against the
     * endpoint loader, not the thread-context loader (the Warres failure). A {@code ClassLoader}
     * is a capability: this is threaded into the decode via trusted channels only (a narrow
     * package-private {@code DerGetArg} accessor for {@code DerProxySerializer}, and the
     * object-stream codec), and is NEVER broadcast through {@code getObjectStreamContext()}.
     */
    private final ResolutionContext resolution;

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
     * @param defaultLoader           the endpoint's default class loader, or {@code null}
     * @param verifyCodebaseIntegrity whether codebase integrity is verified (moot for DER's
     *                                no-annotation class resolution, carried for fidelity)
     * @param verifierLoader          the endpoint's verifier class loader, or {@code null}
     * @throws IOException if reading from {@code objIn} fails
     */
    public DerMarshalInstanceInput(InputStream objIn, byte[] schemaBytes, Collection context,
                                   ClassLoader defaultLoader, boolean verifyCodebaseIntegrity,
                                   ClassLoader verifierLoader)
            throws IOException {
        this.objIn       = Objects.requireNonNull(objIn,       "objIn");
        this.schemaBytes = Objects.requireNonNull(schemaBytes, "schemaBytes");
        this.context     = Objects.requireNonNull(context,     "context");
        this.resolution  = new ResolutionContext(defaultLoader, verifyCodebaseIntegrity, verifierLoader);
        this.payloadBytes = DerInputLimits.DEFAULT.readAllBytesBounded(objIn); // bounded: refuse oversize input (DoS)
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
        // Cross-stream recursion guard: a MarshalledInstance can contain another (a DerProxySerializer
        // carrier's readResolve unmarshals its serviceProxy via get() -> a fresh DerMarshalInstanceInput),
        // and that recursion is NOT bounded by ObjectCodec.MAX_NESTING (each get() is a fresh depth-0
        // decode). Bound it with a ScopedValue depth counter -- ScopedValue, not ThreadLocal (virtual
        // threads); it propagates down the synchronous get() call stack -- so a deeply nested chain of
        // carriers fails with a clean exception rather than a StackOverflowError.
        int depth = DECODE_DEPTH.orElse(0);
        int maxNesting = DerInputLimits.DEFAULT.maxMarshalledInstanceNesting();
        if (depth >= maxNesting) {
            throw new InvalidObjectException(
                    "DER MarshalledInstance decode recursion reached the limit of " + maxNesting
                    + " (au.net.zeus.jgdms.der.maxMarshalledInstanceNesting); possible nested-serializer DoS");
        }
        try {
            return ScopedValue.where(DECODE_DEPTH, depth + 1).call(() -> readObjectImpl(type));
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // CallableOp's checked LUB is Exception; readObjectImpl only throws IOException/CNFE,
            // so this is unreachable -- wrap defensively rather than swallow.
            throw new IOException("DER MarshalledInstance decode failed", e);
        }
    }

    private <T> T readObjectImpl(Class<T> type) throws IOException, ClassNotFoundException {
        // Empty schemaBytes is the sentinel for the OBJECT-STREAM form (e.g. a bare
        // java.lang.reflect.Proxy [8] item) written by DerMarshalInstanceOutput when the
        // marshalled object had no separable @AtomicSerial schema. Decode it via the DER
        // object-stream codec rather than reconstructing a MarshalledInstanceRecord. The
        // endpoint resolution context is threaded through so [8] interface/handler classes
        // resolve against the endpoint loader (NOT the thread-context loader).
        if (schemaBytes == null || schemaBytes.length == 0) {
            DerMarshalInputStream in = new DerMarshalInputStream(
                    new ByteArrayInputStream(payloadBytes), resolution);
            return in.readObject(type);
        }
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
                    MarshalledInstanceRecord.PAYLOAD_FORMAT);

            // Decode at Object.class, NOT `type`: a @AtomicSerial SERIALIZER whose own class is not
            // assignable to `type` -- a DerProxySerializer carrier that resolves to a proxy of
            // `type` -- must not be rejected by the pre-construction assignability check before
            // readResolve runs. JOSS parity: apply readResolve to the ROOT object so the carrier
            // resolves to the real (downloaded/unmarshalled) proxy; plain @AtomicSerial values (not
            // Resolve) pass through unchanged (nested fields are already resolved by decodeNested).
            Object obj = MarshalledInstanceCodec.decodeMarshalledInstance(
                    rec, Object.class, null, resolution).object();
            Object resolved = au.net.zeus.jgdms.der.serial.DerReplacer.resolve(obj);
            // Enforce the requested type against the RESOLVED value (the same type guarantee as the
            // pre-construction check, applied to what the caller actually receives).
            if (resolved != null && type != null && !type.isAssignableFrom(resolved.getClass())) {
                throw new InvalidObjectException(
                        "DER MarshalledInstance: resolved object of type " + resolved.getClass().getName()
                        + " is not assignable to the requested type " + type.getName());
            }
            @SuppressWarnings("unchecked")
            T result = (T) resolved;
            return result;
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
