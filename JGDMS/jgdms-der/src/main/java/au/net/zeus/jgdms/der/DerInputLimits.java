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
 * <p>Both are overridable via system property (read defensively -- a {@link SecurityException} or
 * malformed value falls back to the default):
 * <ul>
 *   <li>{@code au.net.zeus.jgdms.der.maxInputBytes} -- max bytes buffered from one DER input
 *       stream (default 16 MiB);</li>
 *   <li>{@code au.net.zeus.jgdms.der.maxMarshalledInstanceNesting} -- max
 *       {@code MarshalledInstance}-in-{@code MarshalledInstance} decode recursion (default 16).</li>
 * </ul>
 * (These bound a single object graph / invocation; legitimate proxies and nested carriers are far
 * below them -- a real downloadable proxy nests exactly one {@code MarshalledInstance} deep.)
 */
public final class DerInputLimits {

    /** Maximum bytes buffered from a single DER input stream. */
    public static final int MAX_INPUT_BYTES =
            readIntProp("au.net.zeus.jgdms.der.maxInputBytes", 16 * 1024 * 1024);

    /** Maximum {@code MarshalledInstance}-in-{@code MarshalledInstance} decode recursion depth. */
    public static final int MAX_MARSHALLED_INSTANCE_NESTING =
            readIntProp("au.net.zeus.jgdms.der.maxMarshalledInstanceNesting", 16);

    private DerInputLimits() {}

    /**
     * Reads the stream fully but refuses to buffer more than {@link #MAX_INPUT_BYTES} -- the bounded
     * counterpart of {@link InputStream#readAllBytes()} (which is unbounded and a memory-exhaustion
     * DoS on an attacker-controlled stream).
     *
     * @param in the stream (must not be null)
     * @return the buffered bytes (at most {@link #MAX_INPUT_BYTES})
     * @throws IOException if reading fails, or if the stream holds more than the limit
     */
    public static byte[] readAllBytesBounded(InputStream in) throws IOException {
        return readAllBytesBounded(in, MAX_INPUT_BYTES);
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
                    + " bytes (au.net.zeus.jgdms.der.maxInputBytes); refusing to buffer a larger stream");
        }
        return data;
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
