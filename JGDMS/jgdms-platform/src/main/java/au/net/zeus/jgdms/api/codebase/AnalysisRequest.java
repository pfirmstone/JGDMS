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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.URISyntaxException;
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

    private static final long serialVersionUID = 1L;

    /** Default BFS depth limit for call-graph traversal from {@code <clinit>}. */
    public static final int DEFAULT_MAX_BFS_DEPTH = 10;

    private static final String JAR_BYTES      = "jarBytes";
    private static final String CONTENT_HASH   = "contentHash";
    private static final String ORIGINAL_URI   = "originalUri";
    private static final String MAX_BFS_DEPTH  = "maxBfsDepth";

    @SuppressWarnings("unused")
    private static final ObjectStreamField[] serialPersistentFields = serialForm();

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm(JAR_BYTES,     byte[].class),
            new SerialForm(CONTENT_HASH,  String.class),
            new SerialForm(ORIGINAL_URI,  String.class),
            new SerialForm(MAX_BFS_DEPTH, Integer.TYPE)
        };
    }

    public static void serialize(PutArg arg, AnalysisRequest r) throws IOException {
        arg.put(JAR_BYTES,     r.jarBytes.clone());
        arg.put(CONTENT_HASH,  r.contentHash);
        arg.put(ORIGINAL_URI,  r.originalUri == null ? null : r.originalUri.toString());
        arg.put(MAX_BFS_DEPTH, r.maxBfsDepth);
        arg.writeArgs();
    }

    private static boolean check(GetArg arg) throws IOException, ClassNotFoundException {
        byte[] jarBytes = (byte[]) arg.get(JAR_BYTES, null);
        if (jarBytes == null || jarBytes.length == 0)
            throw new InvalidObjectException("jarBytes must not be null or empty");
        String contentHash = (String) arg.get(CONTENT_HASH, null);
        if (contentHash == null || contentHash.isEmpty())
            throw new InvalidObjectException("contentHash must not be null or empty");
        int maxBfsDepth = arg.get(MAX_BFS_DEPTH, DEFAULT_MAX_BFS_DEPTH);
        if (maxBfsDepth <= 0)
            throw new InvalidObjectException("maxBfsDepth must be positive");
        return true;
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
     * @param arg the deserialization argument bag
     * @throws IOException            if validation fails
     * @throws ClassNotFoundException if a required class is not found
     */
    public AnalysisRequest(GetArg arg) throws IOException, ClassNotFoundException {
        this(arg, check(arg));
    }

    private AnalysisRequest(GetArg arg, boolean checked) throws IOException, ClassNotFoundException {
        jarBytes     = ((byte[]) arg.get(JAR_BYTES, null)).clone();
        contentHash  = (String) arg.get(CONTENT_HASH, null);
        String uriStr = (String) arg.get(ORIGINAL_URI, null);
        if (uriStr != null) {
            try {
                originalUri = new Uri(uriStr);
            } catch (URISyntaxException e) {
                throw new InvalidObjectException("originalUri is not a valid RFC3986 URI: " + e.getMessage());
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
     * Returns a copy of the raw JAR file bytes.
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
