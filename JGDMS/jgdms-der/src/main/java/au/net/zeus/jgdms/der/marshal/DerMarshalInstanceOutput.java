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

import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import net.jini.io.MarshalInstanceOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Objects;

/**
 * DER implementation of {@link MarshalInstanceOutput}.
 *
 * <p>Writes an {@code @AtomicSerial} object as a {@link MarshalledInstanceRecord}
 * (JGDMS-STD-006 S7.8) to the wrapped {@link OutputStream}. The DER format carries
 * the full schema chain embedded in the record; no codebase annotations are emitted
 * (S8 -- the schema itself is the data-independence mechanism).
 *
 * <h2>Whole-object granularity</h2>
 * <p>
 * DER {@code MarshalledInstance} operates at object granularity: {@link #writeObject}
 * encodes a complete {@code @AtomicSerial} object hierarchy into a single
 * {@link MarshalledInstanceRecord} blob. The primitive write methods
 * ({@code writeInt}, {@code writeUTF}, etc.) are not used by this transport and throw
 * {@link UnsupportedOperationException} if called.
 *
 * <h2>Null objects</h2>
 * <p>
 * {@code MarshalledInstance}'s protected 3-arg constructor only calls
 * {@link #writeObject} with a non-null object (null objects are handled by the parent
 * via null {@code objBytes}). This implementation therefore assumes obj is non-null.
 *
 * <h2>STD-008 S13.6 "Option A" -- non-invasive spike</h2>
 * <p>
 * No platform files are modified. This class plugs into the existing
 * {@link net.jini.io.MarshalledInstance} factory seam via
 * {@link DerMarshalledInstance} subclassing the 3-arg protected constructor.
 */
public final class DerMarshalInstanceOutput implements MarshalInstanceOutput {

    private final OutputStream objOut;
    private final Collection   context;

    /**
     * Constructs a new output wrapping {@code objOut}.
     *
     * @param objOut  the stream to write the encoded record to (must not be null)
     * @param context the serialization context collection (may be empty, must not be null)
     */
    public DerMarshalInstanceOutput(OutputStream objOut, Collection context) {
        this.objOut  = Objects.requireNonNull(objOut,  "objOut");
        this.context = Objects.requireNonNull(context, "context");
    }

    // -------------------------------------------------------------------------
    // MarshalInstanceOutput
    // -------------------------------------------------------------------------

    /**
     * Encodes {@code obj} as a {@link MarshalledInstanceRecord} and writes its DER
     * bytes to the wrapped output stream.
     *
     * <p>Steps:
     * <ol>
     *   <li>Generate the schema chain for {@code obj.getClass()} via
     *       {@link SchemaGenerator#generateChain}.</li>
     *   <li>Encode the object hierarchy via {@link ObjectCodec#encodeHierarchy}.</li>
     *   <li>Build a {@link MarshalledInstanceRecord} from the chain and payload.</li>
     *   <li>Write {@link MarshalledInstanceRecord#encode()} bytes to {@code objOut}.</li>
     * </ol>
     *
     * @param obj the object to encode; must not be null (null is handled by the parent)
     * @throws IOException if encoding or writing fails
     */
    @Override
    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            // MarshalledInstance's protected ctor skips writeObject for null --
            // this case should not arise in normal use.
            return;
        }
        try {
            SchemaChain.Result chain   = SchemaGenerator.generateChain(obj.getClass());
            byte[]             payload = ObjectCodec.encodeHierarchy(obj, chain);
            MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);
            byte[] encoded = rec.encode();
            objOut.write(encoded);
        } catch (au.net.zeus.jgdms.der.DerException e) {
            throw new IOException("DER encoding failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns {@code false}: DER carries the embedded schema as data-independence
     * mechanism; no codebase URL annotations are written (STD-006 S8).
     */
    @Override
    public boolean hadAnnotations() {
        return false;
    }

    @Override
    public void flush() throws IOException {
        objOut.flush();
    }

    @Override
    public void close() throws IOException {
        objOut.close();
    }

    // -------------------------------------------------------------------------
    // ObjectStreamContext
    // -------------------------------------------------------------------------

    @Override
    public Collection getObjectStreamContext() {
        return context;
    }

    // -------------------------------------------------------------------------
    // Unsupported ObjectOutput primitives -- DER is object-granularity only
    // -------------------------------------------------------------------------

    @Override public void write(int b) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void write(byte[] b) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void write(byte[] b, int off, int len) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeBoolean(boolean v) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeByte(int v) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeShort(int v) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeChar(int v) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeInt(int v) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeLong(long v) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeFloat(float v) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeDouble(double v) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeBytes(String s) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeChars(String s) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }

    @Override public void writeUTF(String s) throws IOException {
        throw new UnsupportedOperationException("DER MarshalledInstance is object-granularity only");
    }
}
