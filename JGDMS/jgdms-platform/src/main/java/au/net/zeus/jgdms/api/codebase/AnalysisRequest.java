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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
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
 * <p><strong>Normalised form.</strong> The {@link #getJarBytes()} value and the
 * {@link #getContentHash()} value both describe the <em>normalised</em> JAR.
 * Host 4 (the Codebase Downloader) runs the raw downloaded bytes through
 * {@code net.pack200.Normalize} (reproducible options) to a byte-reproducible
 * Pack200 fixed point {@code C} before constructing this request.  The
 * {@code contentHash} field is the lowercase-hex SHA-256 of {@code C}, and is
 * the authoritative key used by the {@link VerdictRegistry} to store and
 * retrieve analysis results.
 *
 * <p><strong>Wire format.</strong> For transport efficiency the normalised JAR
 * bytes {@code C} are carried in the serialized form compressed with lossless
 * <em>DEFLATE</em>.  DEFLATE is a byte-exact inverse — {@code inflate(deflate(C))
 * == C} — so the engine recovers {@code C} exactly.  (Pack200 is deliberately
 * <em>not</em> used as the wire codec: its unpacker rewrites the JAR manifest,
 * so a Pack200 round trip is not byte-reversible and would break the binding
 * check below.  Pack200 remains the <em>normalisation</em> mechanism that
 * produces {@code C} and the content hash on Host 4 and on clients.)
 *
 * <p><strong>Binding verification.</strong> During AtomicSerial deserialization
 * {@link #check(GetArg)} inflates the DEFLATE bytes to recover {@code C},
 * recomputes the SHA-256 of {@code C}, and rejects the request with an
 * {@link InvalidObjectException} if it does not equal {@link #getContentHash()}.
 * This binds the analysed bytes to the content hash that clients look up, so a
 * BAE can never analyse one artifact while reporting under another's hash.
 * Decompression is performed exactly once; {@code check} returns {@code C},
 * which the bridge constructor stores as the in-memory {@link #jarBytes}.
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
public final class AnalysisRequest {

    /** Default BFS depth limit for call-graph traversal from {@code <clinit>}. */
    public static final int DEFAULT_MAX_BFS_DEPTH = 10;

    /**
     * Upper bound on {@link #getMaxBfsDepth()}.  Enforced both by
     * {@link #check(GetArg)} (the deserialization path) and by the public
     * {@link #AnalysisRequest(byte[], String, Uri, int)} constructor (the
     * locally-constructed path) — a caller building a request directly
     * bypasses {@code check} entirely, so the ceiling must be checked at
     * both sites to be load-bearing.  Guards {@code ClinitBlockingVisitor}'s
     * BFS against an unreasonably large requested depth.
     */
    public static final int MAX_MAX_BFS_DEPTH = 50;

    private static final String PACKED_JAR_BYTES = "packedJarBytes";
    private static final String CONTENT_HASH   = "contentHash";
    private static final String ORIGINAL_URI   = "originalUri";
    private static final String MAX_BFS_DEPTH  = "maxBfsDepth";

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
     * DEFLATE decompression expands the data by roughly 2–3×; using 3× as the
     * initial buffer factor is a safe upper bound that avoids reallocations for
     * typical JAR sizes.
     */
    private static final int UNPACK_EXPANSION_FACTOR = 3;

    /**
     * Upper bound on the inflated JAR size, as a decompression-bomb guard.
     * Host 4 caps downloads well below this; 256 MiB is generous headroom.
     */
    private static final long MAX_UNPACKED_SIZE = 256L * 1024 * 1024;

    /**
     * Compresses the (normalised) JAR bytes {@code C} with lossless DEFLATE.
     *
     * @param rawBytes JAR content to compress; must be non-null
     * @return DEFLATE-compressed bytes (byte-exactly invertible by {@link #unpackJar})
     */
    private static byte[] packJar(byte[] rawBytes) {
        int initCapacity = (int) Math.min(
                (long) rawBytes.length / 2 + PACK_INIT_CAPACITY_MARGIN,
                Integer.MAX_VALUE - 8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, initCapacity));
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(rawBytes);
        deflater.finish();
        byte[] buf = new byte[8192];
        try {
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                baos.write(buf, 0, n);
            }
        } finally {
            deflater.end();
        }
        return baos.toByteArray();
    }

    /**
     * Inflates DEFLATE-compressed bytes back to the exact original JAR bytes.
     *
     * <p>DEFLATE is a byte-exact inverse of {@link #packJar}, so the result is
     * byte-for-byte identical to the normalised bytes {@code C} that were
     * compressed.  The total inflated size is bounded by
     * {@link #MAX_UNPACKED_SIZE} as a decompression-bomb guard.
     *
     * @param packedBytes DEFLATE-compressed content; must be non-null
     * @return the original (normalised) JAR bytes
     * @throws IOException if the stream is malformed, truncated, or exceeds the
     *         size bound
     */
    private static byte[] unpackJar(byte[] packedBytes) throws IOException {
        int initCapacity = (int) Math.min(
                (long) packedBytes.length * UNPACK_EXPANSION_FACTOR,
                Integer.MAX_VALUE - 8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, initCapacity));
        Inflater inflater = new Inflater();
        inflater.setInput(packedBytes);
        byte[] buf = new byte[8192];
        long total = 0;
        try {
            while (!inflater.finished()) {
                int n;
                try {
                    n = inflater.inflate(buf);
                } catch (DataFormatException e) {
                    throw new IOException("invalid DEFLATE stream", e);
                }
                if (n == 0) {
                    if (inflater.finished() || inflater.needsDictionary()) break;
                    if (inflater.needsInput()) throw new IOException("truncated DEFLATE stream");
                }
                total += n;
                if (total > MAX_UNPACKED_SIZE)
                    throw new IOException("inflated size exceeds " + MAX_UNPACKED_SIZE + " bytes");
                baos.write(buf, 0, n);
            }
        } finally {
            inflater.end();
        }
        return baos.toByteArray();
    }

    /**
     * Computes the lowercase-hex SHA-256 digest of {@code bytes}.
     *
     * <p>Used by {@link #check(GetArg)} to verify that the declared
     * {@link #contentHash} binds to the normalised JAR bytes carried by the
     * request.
     *
     * @param bytes the input data; must be non-null
     * @return lowercase hex string of the 32-byte SHA-256 digest
     */
    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java SE spec; this can never happen.
            throw new AssertionError("SHA-256 not available", e);
        }
    }

    public static void serialize(PutArg arg, AnalysisRequest r) throws IOException {
        arg.put(PACKED_JAR_BYTES, r.packedJarBytes.clone());
        arg.put(CONTENT_HASH,  r.contentHash);
        arg.put(ORIGINAL_URI,  r.originalUri);
        arg.put(MAX_BFS_DEPTH, r.maxBfsDepth);
        arg.writeArgs();
    }

    /**
     * Validates all deserialized fields and inflates the DEFLATE bytes to
     * recover the normalised form {@code C}.
     *
     * <p>By returning the recovered {@code byte[]} rather than a
     * {@code boolean}, the result is passed directly to the bridge constructor
     * {@link #AnalysisRequest(GetArg, byte[])}, so decompression is performed
     * exactly once.
     *
     * @return the normalised JAR bytes {@code C}
     * @throws InvalidObjectException if any field fails validation or
     *         decompression fails, or the content-hash binding does not hold
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
        // Binding verification: the declared contentHash must be the SHA-256 of
        // the (normalised) JAR bytes carried by the request.  DEFLATE is a
        // byte-exact inverse, so the inflated bytes are exactly the normalised
        // form C that Host 4 hashed.  This is the BAE-side half of the
        // JGDMS-STD-002 content-hash binding — it ensures the bytes the engine
        // analyses are exactly the bytes a client will look up by hash.
        String actualHash = sha256Hex(jarBytes);
        if (!actualHash.equalsIgnoreCase(contentHash))
            throw new InvalidObjectException(
                    "contentHash does not match SHA-256 of jarBytes");
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
        if (maxBfsDepth > MAX_MAX_BFS_DEPTH)
            throw new InvalidObjectException(
                    "maxBfsDepth must not exceed " + MAX_MAX_BFS_DEPTH);
        return jarBytes;
    }

    /**
     * Transport representation of the JAR: the normalised JAR bytes {@code C}
     * compressed with lossless DEFLATE.  This is the serialized field; the
     * uncompressed runtime form is held by the transient {@link #jarBytes}.
     *
     * @serial
     */
    private final byte[] packedJarBytes;

    /**
     * Canonical normalised JAR bytes (the analysed artifact {@code C}),
     * uncompressed.  Runtime-only; recovered from {@link #packedJarBytes} and
     * not part of the serial form.
     */
    private final transient byte[] jarBytes;

    /**
     * SHA-256 hex digest of {@link #jarBytes}, computed by the caller.
     *
     * @serial
     */
    private final String contentHash;

    /**
     * The original URI from which the JAR was downloaded, stored as an RFC3986
     * URI string for safe serialization; used for traceability in the
     * {@link JarAnalysisReport} only — the engine never fetches from this URI.
     * May be {@code null} if the origin is not tracked.
     *
     * @serial
     */
    private final String originalUri;

    /**
     * Runtime {@link Uri} representation of {@link #originalUri}, populated by
     * the constructors.  Not serialized.  May be {@code null}.
     */
    private final transient Uri originalUriCache;

    /**
     * Maximum BFS depth for call-graph traversal from {@code <clinit>}.
     * Defaults to {@link #DEFAULT_MAX_BFS_DEPTH}.
     *
     * @serial
     */
    private final int maxBfsDepth;

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * <p>Calls {@link #check(GetArg)}, which inflates the DEFLATE bytes and
     * validates all fields (including the content-hash binding), then passes the
     * recovered normalised JAR bytes to the bridge constructor so that
     * decompression is performed only once.
     *
     * @param arg the deserialization argument bag
     * @throws IOException            if validation, decompression, or
     *                                normalisation fails
     * @throws ClassNotFoundException if a required class is not found
     */
    public AnalysisRequest(GetArg arg) throws IOException, ClassNotFoundException {
        this(arg, check(arg));
    }

    /**
     * Bridge constructor used by the AtomicSerial pattern.
     *
     * <p>{@code canonicalJarBytes} is the canonical normalised form already
     * produced and validated by {@link #check(GetArg)} — no second
     * decompression or normalisation is required.
     */
    private AnalysisRequest(GetArg arg, byte[] canonicalJarBytes)
            throws IOException, ClassNotFoundException {
        packedJarBytes = ((byte[]) arg.get(PACKED_JAR_BYTES, null)).clone();
        jarBytes       = canonicalJarBytes;
        contentHash    = (String) arg.get(CONTENT_HASH, null);
        originalUri    = (String) arg.get(ORIGINAL_URI, null);
        if (originalUri != null) {
            try {
                originalUriCache = new Uri(originalUri);
            } catch (URISyntaxException e) {
                throw new InvalidObjectException(
                        "originalUri is not a valid RFC3986 URI: " + e.getMessage());
            }
        } else {
            originalUriCache = null;
        }
        maxBfsDepth    = arg.get(MAX_BFS_DEPTH, DEFAULT_MAX_BFS_DEPTH);
    }

    /**
     * Constructs an {@code AnalysisRequest} with all fields specified.
     *
     * @param jarBytes    normalised JAR file bytes; must be non-null and non-empty
     * @param contentHash SHA-256 hex digest of {@code jarBytes}; must be
     *                    non-null and non-empty
     * @param originalUri the origin URI for traceability; may be {@code null}
     * @param maxBfsDepth BFS depth limit; must be positive and must not
     *                    exceed {@link #MAX_MAX_BFS_DEPTH}
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
        // Enforced here too (not only in check()/deserialization): a
        // locally-constructed AnalysisRequest goes through this constructor
        // directly and never calls check(), so the ceiling must be checked
        // at both sites to actually bound the BFS.
        if (maxBfsDepth > MAX_MAX_BFS_DEPTH)
            throw new IllegalArgumentException(
                    "maxBfsDepth must not exceed " + MAX_MAX_BFS_DEPTH);

        this.jarBytes         = jarBytes.clone();
        this.packedJarBytes   = packJar(this.jarBytes);
        this.contentHash      = contentHash;
        this.originalUri      = originalUri == null ? null : originalUri.toString();
        this.originalUriCache = originalUri;
        this.maxBfsDepth      = maxBfsDepth;
    }

    /**
     * Constructs an {@code AnalysisRequest} with the default BFS depth limit.
     *
     * @param jarBytes    normalised JAR file bytes; must be non-null and non-empty
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
     * Returns a copy of the canonical normalised JAR file bytes ({@code C}).
     *
     * <p>The bytes returned are the normalised JAR content; the Pack200
     * compressed form is used only on the wire during serialization.  On the
     * receiving side these bytes are recovered by re-normalising the unpacked
     * Pack200 stream.
     *
     * @return non-null, non-empty byte array
     */
    public byte[] getJarBytes() {
        return jarBytes.clone();
    }

    /**
     * Returns the SHA-256 hex digest of the normalised JAR bytes, as computed
     * by the caller.  This value is the primary key in the
     * {@link VerdictRegistry}.
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
        return originalUriCache;
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
