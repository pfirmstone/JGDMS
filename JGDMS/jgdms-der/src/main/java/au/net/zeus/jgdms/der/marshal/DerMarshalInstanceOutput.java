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
import au.net.zeus.jgdms.der.object.ProxyWireSupport;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.export.ProxyAccessor;
import net.jini.io.MarshalInstanceOutput;
import org.apache.river.api.io.AtomicSerial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Objects;

/**
 * DER implementation of {@link MarshalInstanceOutput} (JGDMS-STD-008 sec.13.3).
 *
 * <p>Writes only the DER payload bytes to the wrapped {@link OutputStream}. The schema
 * chain and digest are captured as first-class state and reported via
 * {@link #getSchemaBytes()}, {@link #getSchemaDigest()}, and {@link #getPayloadFormat()}.
 * {@link net.jini.io.MarshalledInstance} uses these to populate its own first-class
 * {@code schemaBytes}, {@code schemaDigest}, and {@code payloadFormat} fields (STD-008
 * sec.13.1), so the schema travels separately from the payload.
 *
 * <h2>Split payload / schema (B+C hybrid)</h2>
 * <p>
 * {@link #writeObject} encodes the object, writes <em>only</em>
 * {@link MarshalledInstanceRecord#payloadBytes()} to {@code objOut} (NOT the full
 * {@code MarshalledInstanceRecord#encode()} output), and stashes the schema for
 * the getter methods. The full {@code MarshalledInstanceRecord} is reconstructed on
 * the decode side from the two separate first-class fields carried by
 * {@code MarshalledInstance}.
 *
 * <h2>Whole-object granularity</h2>
 * <p>
 * DER {@code MarshalledInstance} operates at object granularity: {@link #writeObject}
 * encodes a complete {@code @AtomicSerial} object hierarchy into a single
 * payload blob. The primitive write methods ({@code writeInt}, {@code writeUTF}, etc.)
 * are not used by this transport and throw {@link UnsupportedOperationException} if called.
 *
 * <h2>Null objects</h2>
 * <p>
 * {@code MarshalledInstance}'s protected 3-arg constructor only calls
 * {@link #writeObject} with a non-null object (null objects are handled by the parent
 * via null {@code payloadBytes}). This implementation therefore assumes obj is non-null.
 */
public final class DerMarshalInstanceOutput implements MarshalInstanceOutput {

    private final OutputStream objOut;
    private final Collection   context;
    /**
     * Whether a downloadable top-level proxy is substituted with a {@code DerProxySerializer}
     * carrier ({@code true}, normal MarshalledInstance) or stored bare ({@code false}, the
     * carrier's own {@code serviceProxy}). See {@link DerMarshalledInstance#DerMarshalledInstance(Object, Collection, boolean)}.
     */
    private final boolean substitute;
    /**
     * The marshalling stream's context loader, threaded from the
     * {@link DerMarshalledInstance} construction site as the marshalled object's own defining
     * class loader (see {@code DerMarshalledInstance.getLoader}, which mirrors the JOSS
     * {@code AtomicMarshalledInstance.getLoader(accessor)} provenance). It gates
     * {@code DerProxySerializer.create(...)}'s {@code ProxyCodebaseSpi} lookup and
     * {@code substitute()} check -- i.e. it is the {@code streamLoader} the sibling
     * {@code DerObjectStreamCodec} holds as {@code writeStreamLoader}.
     *
     * <p><strong>Never the thread-context class loader</strong> (the Warres ambient-resolution
     * failure): stream class resolution must be governed by the endpoint/object-assigned loader,
     * not the ambient TCCL. May be {@code null} only when the marshalled object is {@code null}
     * or bootstrap-defined; a {@code null} value is handled fail-symmetric by
     * {@link org.apache.river.resource.Service} (system loader, not bootstrap).
     */
    private final ClassLoader streamLoader;

    // Stashed after writeObject -- reported via the widened MarshalInstanceOutput methods.
    private byte[] schemaBytes;
    private byte[] schemaDigest;

    /**
     * Constructs a new substituting output wrapping {@code objOut}, with no stream loader.
     *
     * @param objOut  the stream to write the payload bytes to (must not be null)
     * @param context the serialization context collection (may be empty, must not be null)
     */
    public DerMarshalInstanceOutput(OutputStream objOut, Collection context) {
        this(objOut, context, true, null);
    }

    /**
     * Constructs a new output wrapping {@code objOut}, selecting whether a downloadable
     * top-level proxy is substituted with a {@code DerProxySerializer} carrier, with no
     * stream loader.
     *
     * @param objOut     the stream to write the payload bytes to (must not be null)
     * @param context    the serialization context collection (may be empty, must not be null)
     * @param substitute {@code true} to substitute a downloadable top-level proxy; {@code false}
     *                   to store it bare (the carrier inner case)
     */
    public DerMarshalInstanceOutput(OutputStream objOut, Collection context, boolean substitute) {
        this(objOut, context, substitute, null);
    }

    /**
     * Constructs a new output wrapping {@code objOut}, selecting whether a downloadable
     * top-level proxy is substituted with a {@code DerProxySerializer} carrier, and threading
     * the marshalling stream's context loader used for the {@code ProxyCodebaseSpi} lookup.
     *
     * @param objOut       the stream to write the payload bytes to (must not be null)
     * @param context      the serialization context collection (may be empty, must not be null)
     * @param substitute   {@code true} to substitute a downloadable top-level proxy; {@code false}
     *                     to store it bare (the carrier inner case)
     * @param streamLoader the marshalling stream's context loader (the marshalled object's own
     *                     defining loader), used to gate the {@code ProxyCodebaseSpi} lookup and
     *                     {@code substitute()} check; may be {@code null}. NEVER the thread-context
     *                     loader (the Warres ambient-resolution failure).
     */
    public DerMarshalInstanceOutput(OutputStream objOut, Collection context, boolean substitute,
                                    ClassLoader streamLoader) {
        this.objOut       = Objects.requireNonNull(objOut,  "objOut");
        this.context      = Objects.requireNonNull(context, "context");
        this.substitute   = substitute;
        this.streamLoader = streamLoader;
    }

    // -------------------------------------------------------------------------
    // MarshalInstanceOutput
    // -------------------------------------------------------------------------

    /**
     * Encodes {@code obj} as a {@link MarshalledInstanceRecord}, writes only the
     * payload bytes to the wrapped output stream, and stashes the schema for retrieval
     * via {@link #getSchemaBytes()} and {@link #getSchemaDigest()}.
     *
     * <p>Steps:
     * <ol>
     *   <li>Generate the schema chain for {@code obj.getClass()} via
     *       {@link SchemaGenerator#generateChain}.</li>
     *   <li>Encode the object hierarchy via {@link ObjectCodec#encodeHierarchy}.</li>
     *   <li>Build a {@link MarshalledInstanceRecord} from the chain and payload.</li>
     *   <li>Write <em>only</em> {@link MarshalledInstanceRecord#payloadBytes()} to
     *       {@code objOut} (NOT the full {@link MarshalledInstanceRecord#encode()} result).
     *       The schema travels separately via the widened first-class fields.</li>
     *   <li>Stash {@link MarshalledInstanceRecord#schemaBytes()} and
     *       {@link MarshalledInstanceRecord#schemaDigest()} for the getter methods.</li>
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

        // Write seam: substitute a downloadable top-level proxy with a DerProxySerializer
        // carrier (unless this is the carrier's own bare serviceProxy -- substitute == false).
        // With no registered ProxyCodebaseSpi, create() returns the proxy unchanged and it
        // travels bare below; a registered provider whose substitute() is true yields a
        // DerProxySerializer (an @AtomicSerial record encoded via the schema-separated path).
        if (substitute) {
            if (obj instanceof DynamicProxyCodebaseAccessor dpca) {
                obj = ProxyWireSupport.substituteDownloadableProxy(dpca, streamLoader, context);
            } else if (obj instanceof ProxyAccessor pa) {
                obj = ProxyWireSupport.substituteDownloadableProxy(pa, streamLoader, context);
            }
        }

        // A value with NO @AtomicSerial class in its hierarchy (a bare java.lang.reflect.Proxy,
        // a String, a byte[], an enum, ...) has no separable schema; encode it as a
        // self-describing object-stream item (context-tagged per STD-008 sec.15.2 -- e.g. [8]
        // for a bare proxy) via the DER object-stream codec, leaving the first-class schemaBytes
        // EMPTY. That empty-schema sentinel tells DerMarshalInstanceInput to decode the
        // object-stream form rather than reconstruct a MarshalledInstanceRecord.
        if (nearestAtomicSerial(obj.getClass()) == null) {
            objOut.write(encodeObjectStreamItem(obj));
            this.schemaBytes  = new byte[0];
            this.schemaDigest = new byte[0];
            return;
        }

        // @AtomicSerial object (including a substituted DerProxySerializer): schema-separated
        // form -- write ONLY the payload bytes; the schema travels as first-class MI fields.
        try {
            SchemaChain.Result chain   = SchemaGenerator.generateChain(obj.getClass());
            byte[]             payload = ObjectCodec.encodeHierarchy(obj, chain);
            MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(chain, payload);

            objOut.write(rec.payloadBytes());

            this.schemaBytes  = rec.schemaBytes();
            this.schemaDigest = rec.schemaDigest();
        } catch (au.net.zeus.jgdms.der.DerException e) {
            throw new IOException("DER encoding failed: " + e.getMessage(), e);
        }
    }

    /**
     * Encodes {@code obj} as a single self-describing DER object-stream item (context-tagged
     * per STD-008 sec.15.2) using {@link DerMarshalOutputStream}. Used for a bare
     * {@code java.lang.reflect.Proxy} inside a MarshalledInstance ([8] form), which has no
     * separable {@code @AtomicSerial} schema. No stream header/envelope is written -- the
     * codec accumulates the single TLV which {@code flush()} drains to the buffer.
     */
    private byte[] encodeObjectStreamItem(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DerMarshalOutputStream dmos = new DerMarshalOutputStream(bos);
        dmos.writeObject(obj);
        dmos.flush();
        return bos.toByteArray();
    }

    /** The nearest class in {@code c}'s hierarchy annotated {@code @AtomicSerial}, or null if none. */
    private static Class<?> nearestAtomicSerial(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            if (k.isAnnotationPresent(AtomicSerial.class)) return k;
        }
        return null;
    }

    /**
     * Returns {@code false}: DER carries the embedded schema as data-independence
     * mechanism; no codebase URL annotations are written (STD-006 S8).
     */
    @Override
    public boolean hadAnnotations() {
        return false;
    }

    /**
     * The embedded schema chain bytes stashed by {@link #writeObject}, as reported
     * to {@link net.jini.io.MarshalledInstance} for promotion to its first-class
     * {@code schemaBytes} field (STD-008 sec.13.1).
     *
     * @return the schema chain bytes; empty array if {@link #writeObject} has not
     *         been called yet
     */
    @Override
    public byte[] getSchemaBytes() {
        return schemaBytes != null ? schemaBytes : new byte[0];
    }

    /**
     * The 32-byte SHA-256 digest of the leaf schema record stashed by
     * {@link #writeObject}, for fast-path schema comparison (STD-006 sec.12.4).
     *
     * @return the schema digest; empty array if {@link #writeObject} has not
     *         been called yet
     */
    @Override
    public byte[] getSchemaDigest() {
        return schemaDigest != null ? schemaDigest : new byte[0];
    }

    /**
     * The self-describing payload-format identifier for this codec:
     * {@link MarshalledInstanceRecord#PAYLOAD_FORMAT} ({@code "JGDMS-STD-006/ATOMIC-DER"}).
     * This value is captured by {@link net.jini.io.MarshalledInstance} into its
     * {@code payloadFormat} field, enabling ServiceLoader dispatch on the decode side.
     *
     * @return {@link MarshalledInstanceRecord#PAYLOAD_FORMAT}
     */
    @Override
    public String getPayloadFormat() {
        return MarshalledInstanceRecord.PAYLOAD_FORMAT;
    }

    /** The threaded marshal-stream loader (package-private, for tests): asserts it is not null-by-default. */
    ClassLoader streamLoader() {
        return streamLoader;
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
