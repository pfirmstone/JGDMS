/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.api.codebase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import net.pack200.Pack200;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.net.Uri;

/**
 * An immutable, serializable request to analyse a single JAR file, passed
 * from the Codebase Downloader (Host 4) to a {@link BytecodeAnalysisEngine}
 * (Host 2) over a JERI transport channel.
 *
 * <p>This is the <em>push model</em>: the caller, not the engine, is
 * responsible for fetching the JAR bytes and computing the SHA-256 content
 * hash.  The engine therefore requires no outbound network access — it
 * operates purely on the bytes supplied in this request.
 *
 * <p>The {@link #getContentHash()} value is the authoritative key used by the
 * {@link VerdictRegistry} to store and retrieve analysis results.  It must be
 * computed by the caller from {@link #getJarBytes()} before constructing this
 * object.
 *
 * <p><strong>Compression.</strong> The JAR bytes are stored in the serialized
 * form compressed using Pack200 (via the pfirmstone/pack200 library, whose
 * unpacker is hardened against untrusted input).  The in-memory
 * {@link #jarBytes} field always holds the raw, uncompressed bytes; compression
 * and decompression happen transparently during serialization and
 * deserialization.
 *
 * <p><strong>Serialization safety.</strong> All fields are validated atomically
 * before the object is constructed, using the {@link AtomicSerial} protocol.
 *
 * @see BytecodeAnalysisEngine#analyzeJar
 * @see JarAnalysisReport
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
@AtomicSerial
public final class AnalysisRequest implements Serializable {

    private static final long serialVersionUID = 2L;

    /** Default BFS depth limit for call-graph traversal from {@code <clinit>}. */
    public static final int DEFAULT_MAX_BFS_DEPTH = 10;

    /**
     * Serialized-form field name for the Pack200-compressed JAR bytes.
     * The in-memory {@link #jarBytes} field holds the raw, decompressed bytes;
     * this field is the transport representation.
     */
    private static final String PACKED_JAR_BYTES = "packedJarBytes";
    private static final String CONTENT_HASH   = "contentHash";
    private static final String ORIGINAL_URI   = "originalUri";
    private static final String MAX_BFS_DEPTH  = "maxBfsDepth";

    @SuppressWarnings("unused")
    private static final ObjectStreamField[] serialPersistentFields = serialForm();

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm(PACKED_JAR_BYTES, byte[].class),
            new SerialForm(CONTENT_HASH,     String.class),
            new SerialForm(ORIGINAL_URI,     String.class),
            new SerialForm(MAX_BFS_DEPTH,    Integer.TYPE)
        };
    }

    /**
     * Pack200 compresses Java class files by roughly 40–60 %; initialising the
     * output buffer to half the input size plus a small header margin avoids
     * most reallocations without wasting significant memory.
     */
    private static final int PACK_INIT_CAPACITY_MARGIN = 256;

    /**
     * Pack200 decompression expands the data by roughly 2–3×; using 3× as the
     * initial buffer factor is a safe upper bound that avoids reallocations for
     * typical JAR sizes.
     */
    private static final int UNPACK_EXPANSION_FACTOR = 3;

    /**
     * Compresses raw JAR bytes using Pack200.
     *
     * @param rawBytes raw JAR file content; must be non-null
     * @return Pack200-compressed bytes
     * @throws IOException if packing fails
     */
    private static byte[] packJar(byte[] rawBytes) throws IOException {
        // Compute initial capacity safely to avoid int overflow for very large inputs.
        // Pack200 compresses by ~40–60 %, so half the input size plus a header margin
        // is a good initial estimate; cap at the maximum safe array allocation size.
        int initCapacity = (int) Math.min(
                (long) rawBytes.length / 2 + PACK_INIT_CAPACITY_MARGIN,
                Integer.MAX_VALUE - 8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(initCapacity);
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(rawBytes))) {
            Pack200.newPacker().pack(jis, baos);
        }
        return baos.toByteArray();
    }

    /**
     * Decompresses Pack200-compressed bytes back to raw JAR bytes.
     *
     * @param packedBytes Pack200-compressed content; must be non-null
     * @return raw JAR file bytes
     * @throws IOException if unpacking fails
     */
    private static byte[] unpackJar(byte[] packedBytes) throws IOException {
        // Compute initial capacity safely to avoid int overflow for very large inputs.
        // Pack200 expands by roughly 2–3×; 3× is a conservative upper bound.
        int initCapacity = (int) Math.min(
                (long) packedBytes.length * UNPACK_EXPANSION_FACTOR,
                Integer.MAX_VALUE - 8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(initCapacity);
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            Pack200.newUnpacker().unpack(new ByteArrayInputStream(packedBytes), jos);
        }
        return baos.toByteArray();
    }

    public static void serialize(PutArg arg, AnalysisRequest r) throws IOException {
        arg.put(PACKED_JAR_BYTES, packJar(r.jarBytes));
        arg.put(CONTENT_HASH,  r.contentHash);
        arg.put(ORIGINAL_URI,  r.originalUri == null ? null : r.originalUri.toString());
        arg.put(MAX_BFS_DEPTH, r.maxBfsDepth);
        arg.writeArgs();
    }

    /**
     * Validates all deserialized fields and decompresses the JAR bytes.
     *
     * <p>By returning the unpacked {@code byte[]} rather than a {@code boolean},
     * the result is passed directly to the bridge constructor
     * {@link #AnalysisRequest(GetArg, byte[])}, eliminating the need to
     * decompress the Pack200 stream a second time.
     *
     * @return the decompressed raw JAR bytes
     * @throws InvalidObjectException if any field fails validation or
     *         decompression fails
     */
    private static byte[] check(GetArg arg) throws IOException, ClassNotFoundException {
        byte[] packedJarBytes = (byte[]) arg.get(PACKED_JAR_BYTES, null);
        if (packedJarBytes == null || packedJarBytes.length == 0)
            throw new InvalidObjectException("packedJarBytes must not be null or empty");
        byte[] jarBytes;
        try {
            jarBytes = unpackJar(packedJarBytes);
        } catch (IOException e) {
            throw new InvalidObjectException(
                    "packedJarBytes could not be decompressed: " + e.getMessage());
        }
        if (jarBytes.length == 0)
            throw new InvalidObjectException("jarBytes must not be empty after decompression");
        String contentHash = (String) arg.get(CONTENT_HASH, null);
        if (contentHash == null || contentHash.isEmpty())
            throw new InvalidObjectException("contentHash must not be null or empty");
        String uriStr = (String) arg.get(ORIGINAL_URI, null);
        if (uriStr != null) {
            try {
                new Uri(uriStr);
            } catch (URISyntaxException e) {
                throw new InvalidObjectException(
                        "originalUri is not a valid RFC3986 URI: " + e.getMessage());
            }
        }
        int maxBfsDepth = arg.get(MAX_BFS_DEPTH, DEFAULT_MAX_BFS_DEPTH);
        if (maxBfsDepth <= 0)
            throw new InvalidObjectException("maxBfsDepth must be positive");
        return jarBytes;
    }

    /** Raw JAR bytes pushed by the caller. */
    private final byte[] jarBytes;

    /** SHA-256 hex digest of {@link #jarBytes}, computed by the caller. */
    private final String contentHash;

    /**
     * The original URI from which the JAR was downloaded; used for
     * traceability in the {@link JarAnalysisReport} only — the engine
     * never fetches from this URI.  May be {@code null} if the origin is
     * not tracked.
     */
    private final Uri originalUri;

    /**
     * Maximum BFS depth for call-graph traversal from {@code <clinit>}.
     * Defaults to {@link #DEFAULT_MAX_BFS_DEPTH}.
     */
    private final int maxBfsDepth;

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * <p>Calls {@link #check(GetArg)}, which decompresses the Pack200 bytes
     * and validates all fields, then passes the decompressed JAR bytes to the
     * bridge constructor so that decompression is performed only once.
     *
     * @param arg the deserialization argument bag
     * @throws IOException            if validation or decompression fails
     * @throws ClassNotFoundException if a required class is not found
     */
    public AnalysisRequest(GetArg arg) throws IOException, ClassNotFoundException {
        this(arg, check(arg));
    }

    /**
     * Bridge constructor used by the AtomicSerial pattern.
     *
     * <p>{@code unpackedJarBytes} is the result already produced and validated
     * by {@link #check(GetArg)} — no second decompression is required.
     */
    private AnalysisRequest(GetArg arg, byte[] unpackedJarBytes)
            throws IOException, ClassNotFoundException {
        jarBytes     = unpackedJarBytes;
        contentHash  = (String) arg.get(CONTENT_HASH, null);
        String uriStr = (String) arg.get(ORIGINAL_URI, null);
        if (uriStr != null) {
            try {
                originalUri = new Uri(uriStr);
            } catch (URISyntaxException e) {
                throw new InvalidObjectException(
                        "originalUri is not a valid RFC3986 URI: " + e.getMessage());
            }
        } else {
            originalUri = null;
        }
        maxBfsDepth  = arg.get(MAX_BFS_DEPTH, DEFAULT_MAX_BFS_DEPTH);
    }

    /**
     * Constructs an {@code AnalysisRequest} with all fields specified.
     *
     * @param jarBytes    raw JAR file bytes; must be non-null and non-empty
     * @param contentHash SHA-256 hex digest of {@code jarBytes}; must be
     *                    non-null and non-empty
     * @param originalUri the origin URI for traceability; may be {@code null}
     * @param maxBfsDepth BFS depth limit; must be positive
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if {@code jarBytes} or
     *                                  {@code contentHash} is {@code null}
     */
    public AnalysisRequest(byte[] jarBytes,
                           String contentHash,
                           Uri originalUri,
                           int maxBfsDepth) {
        if (jarBytes == null)     throw new NullPointerException("jarBytes");
        if (jarBytes.length == 0) throw new IllegalArgumentException("jarBytes must not be empty");
        if (contentHash == null)  throw new NullPointerException("contentHash");
        if (contentHash.isEmpty()) throw new IllegalArgumentException("contentHash must not be empty");
        if (maxBfsDepth <= 0)     throw new IllegalArgumentException("maxBfsDepth must be positive");

        this.jarBytes    = jarBytes.clone();
        this.contentHash = contentHash;
        this.originalUri = originalUri;
        this.maxBfsDepth = maxBfsDepth;
    }

    /**
     * Constructs an {@code AnalysisRequest} with the default BFS depth limit.
     *
     * @param jarBytes    raw JAR file bytes; must be non-null and non-empty
     * @param contentHash SHA-256 hex digest of {@code jarBytes}; must be
     *                    non-null and non-empty
     * @param originalUri the origin URI for traceability; may be {@code null}
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if {@code jarBytes} or
     *                                  {@code contentHash} is {@code null}
     */
    public AnalysisRequest(byte[] jarBytes, String contentHash, Uri originalUri) {
        this(jarBytes, contentHash, originalUri, DEFAULT_MAX_BFS_DEPTH);
    }

    /**
     * Returns a copy of the raw (decompressed) JAR file bytes.
     *
     * <p>The bytes returned are the original, uncompressed JAR content as
     * supplied to the constructor.  The Pack200-compressed form is used only
     * during serialization for transport efficiency.
     *
     * @return non-null, non-empty byte array
     */
    public byte[] getJarBytes() {
        return jarBytes.clone();
    }

    /**
     * Returns the SHA-256 hex digest of the JAR bytes, as computed by the
     * caller.  This value is the primary key in the {@link VerdictRegistry}.
     *
     * @return non-null, non-empty hex string
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * Returns the origin URI of the JAR, if available.  The engine uses this
     * for traceability reporting only — it never fetches from this URI.
     *
     * @return the origin URI, or {@code null} if not provided
     */
    public Uri getOriginalUri() {
        return originalUri;
    }

    /**
     * Returns the maximum BFS depth for the call-graph traversal from
     * {@code <clinit>}.
     *
     * @return a positive integer
     */
    public int getMaxBfsDepth() {
        return maxBfsDepth;
    }

    @Override
    public String toString() {
        return "AnalysisRequest{contentHash='" + contentHash
                + "', jarBytes.length=" + jarBytes.length
                + ", originalUri=" + originalUri
                + ", maxBfsDepth=" + maxBfsDepth + '}';
    }
}
