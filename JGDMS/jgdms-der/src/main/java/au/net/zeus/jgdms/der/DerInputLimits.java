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

package au.net.zeus.jgdms.der;

import java.io.IOException;
import java.io.InputStream;

/**
 * Denial-of-service limits for the DER read path. The DER input streams read their input
 * <em>eagerly</em> into a {@code byte[]} (DER needs the whole TLV structure with definite lengths),
 * so the input size is the peak buffer cost; and a {@code MarshalledInstance} can contain another
 * {@code MarshalledInstance} (e.g. a {@code DerProxySerializer} carrier whose {@code readResolve}
 * unmarshals its {@code serviceProxy}), so decoding can recurse across streams. These limits cap
 * both.
 *
 * <h2>Per-deployment configuration</h2>
 * <p>This is an immutable value object so a deployment can set its own limits and hand them to the
 * JERI invocation layer through {@link net.jini.jeri.AtomicDerILFactory} (which a service's
 * {@code net.jini.config.Configuration} instantiates) -- e.g. in a configuration file:
 * <pre>
 *   exporter = new BasicJeriExporter(endpoint,
 *       new AtomicDerILFactory(null, MyService.class,
 *           au.net.zeus.jgdms.der.DerInputLimits.maxBytes(64 * 1024 * 1024)));
 * </pre>
 * The cap applies to that endpoint's invocation arg/return streams (the attacker-controlled input);
 * a {@code MarshalledInstance} nested in those args is transitively bounded by the same cap.
 *
 * <p>{@link #DEFAULT} is the JVM-wide fallback. Its two values come from system properties
 * (read defensively -- a {@link SecurityException} or malformed value falls back to the built-in
 * default):
 * <ul>
 *   <li>{@code au.net.zeus.jgdms.der.maxInputBytes} (default 16 MiB);</li>
 *   <li>{@code au.net.zeus.jgdms.der.maxMarshalledInstanceNesting} (default 16).</li>
 * </ul>
 * (These bound a single object graph / invocation; a real downloadable proxy nests exactly one
 * {@code MarshalledInstance} deep, far below the nesting bound.)
 */
public final class DerInputLimits {

    private static final int DEFAULT_MAX_INPUT_BYTES =
            readIntProp("au.net.zeus.jgdms.der.maxInputBytes", 16 * 1024 * 1024);
    private static final int DEFAULT_MAX_NESTING =
            readIntProp("au.net.zeus.jgdms.der.maxMarshalledInstanceNesting", 16);

    /** The JVM-wide default limits (system-property configurable); used when none are supplied. */
    public static final DerInputLimits DEFAULT =
            new DerInputLimits(DEFAULT_MAX_INPUT_BYTES, DEFAULT_MAX_NESTING);

    private final int maxInputBytes;
    private final int maxMarshalledInstanceNesting;

    /**
     * @param maxInputBytes               max bytes buffered from one DER input stream (must be &gt; 0)
     * @param maxMarshalledInstanceNesting max MarshalledInstance-in-MarshalledInstance decode
     *                                     recursion (must be &gt; 0)
     */
    public DerInputLimits(int maxInputBytes, int maxMarshalledInstanceNesting) {
        if (maxInputBytes <= 0) {
            throw new IllegalArgumentException("maxInputBytes must be > 0: " + maxInputBytes);
        }
        if (maxMarshalledInstanceNesting <= 0) {
            throw new IllegalArgumentException(
                    "maxMarshalledInstanceNesting must be > 0: " + maxMarshalledInstanceNesting);
        }
        this.maxInputBytes = maxInputBytes;
        this.maxMarshalledInstanceNesting = maxMarshalledInstanceNesting;
    }

    /** Limits with the given input-byte cap and the {@link #DEFAULT} nesting bound (config convenience). */
    public static DerInputLimits maxBytes(int maxInputBytes) {
        return new DerInputLimits(maxInputBytes, DEFAULT.maxMarshalledInstanceNesting);
    }

    /** Max bytes buffered from a single DER input stream. */
    public int maxInputBytes() {
        return maxInputBytes;
    }

    /** Max {@code MarshalledInstance}-in-{@code MarshalledInstance} decode recursion depth. */
    public int maxMarshalledInstanceNesting() {
        return maxMarshalledInstanceNesting;
    }

    /**
     * Reads the stream fully but refuses to buffer more than {@link #maxInputBytes()} -- the bounded
     * counterpart of {@link InputStream#readAllBytes()} (which is unbounded and a memory-exhaustion
     * DoS on an attacker-controlled stream).
     *
     * @param in the stream (must not be null)
     * @return the buffered bytes (at most {@link #maxInputBytes()})
     * @throws IOException if reading fails, or if the stream holds more than the limit
     */
    public byte[] readAllBytesBounded(InputStream in) throws IOException {
        return readAllBytesBounded(in, maxInputBytes);
    }

    /** As {@link #readAllBytesBounded(InputStream)} with an explicit limit (package-private; for tests). */
    static byte[] readAllBytesBounded(InputStream in, int max) throws IOException {
        if (max >= Integer.MAX_VALUE - 8) {
            // Cap effectively disabled (deliberate operator override); avoid max+1 overflow.
            return in.readAllBytes();
        }
        // readNBytes allocates incrementally up to (max + 1), so a huge stream costs at most ~max+1,
        // and reading one byte past the limit lets us detect (and reject) oversize input.
        byte[] data = in.readNBytes(max + 1);
        if (data.length > max) {
            throw new IOException("DER input exceeds the maximum permitted size of " + max
                    + " bytes; refusing to buffer a larger stream");
        }
        return data;
    }

    @Override
    public String toString() {
        return "DerInputLimits[maxInputBytes=" + maxInputBytes
                + ", maxMarshalledInstanceNesting=" + maxMarshalledInstanceNesting + "]";
    }

    private static int readIntProp(String name, int def) {
        try {
            String v = System.getProperty(name);
            if (v == null) return def;
            int parsed = Integer.parseInt(v.trim());
            return parsed > 0 ? parsed : def;
        } catch (SecurityException | NumberFormatException e) {
            return def;
        }
    }
}
