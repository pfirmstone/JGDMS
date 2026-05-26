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
package net.jini.export;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service-side utility for pre-computing per-JAR codebase digests.
 *
 * <p>Services call {@link #compute(String, String)} at startup — typically
 * from within the service constructor or initialisation method, right after
 * the codebase annotation string becomes available — to produce the values
 * that {@link CodebaseAccessor#getCodebaseDigest()} and
 * {@link CodebaseAccessor#getDigestOffsets()} must return.
 *
 * <h2>DoS guards</h2>
 * The same system-property-controlled limits that protect the client-side
 * {@code PreferredProxyCodebaseProvider} are applied here:
 * <ul>
 *   <li>{@code jgdms.proxy.maxCodebaseJars} (default 100) — maximum number of
 *       non-directory JAR URLs in the codebase annotation.</li>
 *   <li>{@code jgdms.proxy.maxJarBytes} (default 512 MiB) — maximum bytes read
 *       from any single JAR URL.</li>
 *   <li>{@code jgdms.proxy.jarReadTimeoutMs} (default 30 000 ms) — connect and
 *       read timeout applied to every URL connection opened during digest
 *       computation.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CodebaseDigestUtil.Result r = CodebaseDigestUtil.compute(codebase, "SHA-256");
 * if (r != null) {
 *     this.codebaseDigest  = r.getFlatDigest();
 *     this.digestOffsets   = r.getOffsets();
 *     this.digestAlgorithm = r.getAlgorithm();
 * }
 * }</pre>
 *
 * @see CodebaseAccessor#getCodebaseDigest()
 * @see CodebaseAccessor#getDigestOffsets()
 * @see CodebaseAccessor#getCodebaseDigestAlgorithm()
 * @since 3.1.1
 */
public final class CodebaseDigestUtil {

    private static final Logger logger =
            Logger.getLogger(CodebaseDigestUtil.class.getName());

    // -------------------------------------------------------------------------
    // DoS guard: system-property keys and defaults (shared with
    // PreferredProxyCodebaseProvider on the client side)
    // -------------------------------------------------------------------------

    /** Maximum non-directory JAR URLs permitted in one codebase annotation. */
    static final String MAX_CODEBASE_JARS_PROPERTY = "jgdms.proxy.maxCodebaseJars";
    static final int    DEFAULT_MAX_CODEBASE_JARS   = 100;

    /** Maximum bytes read from a single JAR URL when computing its digest. */
    static final String MAX_JAR_BYTES_PROPERTY  = "jgdms.proxy.maxJarBytes";
    static final long   DEFAULT_MAX_JAR_BYTES   = 512L * 1024L * 1024L;

    /**
     * Connect and read timeout in milliseconds applied to every URL connection
     * opened during digest computation.
     */
    static final String JAR_READ_TIMEOUT_MS_PROPERTY  = "jgdms.proxy.jarReadTimeoutMs";
    static final int    DEFAULT_JAR_READ_TIMEOUT_MS   = 30_000;

    private static final int  MAX_CODEBASE_JARS  = loadInt(MAX_CODEBASE_JARS_PROPERTY,
                                                            DEFAULT_MAX_CODEBASE_JARS);
    private static final long MAX_JAR_BYTES      = loadLong(MAX_JAR_BYTES_PROPERTY,
                                                            DEFAULT_MAX_JAR_BYTES);
    private static final int  JAR_READ_TIMEOUT_MS = loadInt(JAR_READ_TIMEOUT_MS_PROPERTY,
                                                            DEFAULT_JAR_READ_TIMEOUT_MS);

    // -------------------------------------------------------------------------

    private CodebaseDigestUtil() { /* utility class */ }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Immutable holder returned by {@link #compute(String, String)}.
     *
     * <p>All arrays are copied defensively; mutations to the returned arrays
     * do not affect the stored values.
     */
    public static final class Result {

        private final String algorithm;
        private final byte[] flatDigest;
        private final int[]  offsets;

        Result(String algorithm, byte[] flatDigest, int[] offsets) {
            this.algorithm   = algorithm;
            this.flatDigest  = flatDigest.clone();
            this.offsets     = offsets.clone();
        }

        /**
         * Returns the digest algorithm name (e.g. {@code "SHA-256"}).
         * @return algorithm name; never {@code null}
         */
        public String getAlgorithm() { return algorithm; }

        /**
         * Returns a copy of the flat per-JAR digest array.
         * @return flat digest bytes; never {@code null}
         * @see CodebaseAccessor#getCodebaseDigest()
         */
        public byte[] getFlatDigest() { return flatDigest.clone(); }

        /**
         * Returns a copy of the per-JAR start-byte-offset array.
         * @return offsets; never {@code null}
         * @see CodebaseAccessor#getDigestOffsets()
         */
        public int[] getOffsets() { return offsets.clone(); }
    }

    /**
     * Computes per-JAR content digests for all non-directory JAR URLs in the
     * given space-separated codebase annotation string.
     *
     * <p>Directory URLs (those whose path ends with {@code /}) are silently
     * skipped, matching the behaviour of the client-side codebase loader.
     *
     * @param classAnnotation the codebase annotation as returned by
     *        {@link CodebaseAccessor#getClassAnnotation()},
     *        or {@code null} / empty
     * @param algorithm       the digest algorithm to use (e.g. {@code "SHA-256"})
     * @return a {@link Result} containing the flat digest array and offsets,
     *         or {@code null} if {@code classAnnotation} is null, blank, or
     *         contains no JAR URLs
     * @throws IOException if a JAR URL cannot be opened / read, if the
     *         algorithm is unavailable, or if a DoS guard fires
     */
    public static Result compute(String classAnnotation, String algorithm)
            throws IOException {

        if (classAnnotation == null || classAnnotation.isBlank()) {
            return null;
        }

        // Split on whitespace; filter empty tokens
        String[] tokens = classAnnotation.trim().split("\\s+");
        List<URL> jarUrls = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            try {
                URL url = new URL(token);
                if (!isDirectory(url)) {
                    jarUrls.add(url);
                }
            } catch (MalformedURLException e) {
                logger.log(Level.WARNING,
                        "Ignoring malformed codebase URL: " + token, e);
            }
        }

        if (jarUrls.isEmpty()) {
            return null;
        }

        if (jarUrls.size() > MAX_CODEBASE_JARS) {
            throw new IOException(
                    "Codebase exceeds maximum JAR count ("
                    + MAX_CODEBASE_JARS + "): " + jarUrls.size() + " URLs");
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(algorithm + " MessageDigest not available", e);
        }

        List<byte[]> digests = new ArrayList<>(jarUrls.size());
        for (URL url : jarUrls) {
            md.reset();
            try (InputStream in = openWithTimeout(url)) {
                byte[] buf = new byte[8192];
                int n;
                long totalRead = 0L;
                while ((n = in.read(buf)) > 0) {
                    totalRead += n;
                    if (totalRead > MAX_JAR_BYTES) {
                        throw new IOException(
                                "JAR at " + url + " exceeds maximum allowed size of "
                                + MAX_JAR_BYTES + " bytes during digest computation");
                    }
                    md.update(buf, 0, n);
                }
            }
            digests.add(md.digest());
        }

        // Build flat array and offsets
        int totalLen = 0;
        for (byte[] d : digests) totalLen += d.length;

        byte[] flat    = new byte[totalLen];
        int[]  offsets = new int[digests.size()];
        int    pos     = 0;
        for (int i = 0; i < digests.size(); i++) {
            offsets[i] = pos;
            byte[] d = digests.get(i);
            System.arraycopy(d, 0, flat, pos, d.length);
            pos += d.length;
        }

        return new Result(algorithm, flat, offsets);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static boolean isDirectory(URL url) {
        String path = url.getPath();
        return path != null && path.endsWith("/");
    }

    private static InputStream openWithTimeout(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(JAR_READ_TIMEOUT_MS);
        conn.setReadTimeout(JAR_READ_TIMEOUT_MS);
        return conn.getInputStream();
    }

    private static int loadInt(String property, int defaultValue) {
        try {
            String s = System.getProperty(property);
            if (s != null) return Integer.parseInt(s.trim());
        } catch (Exception ignored) { }
        return defaultValue;
    }

    private static long loadLong(String property, long defaultValue) {
        try {
            String s = System.getProperty(property);
            if (s != null) return Long.parseLong(s.trim());
        } catch (Exception ignored) { }
        return defaultValue;
    }
}
