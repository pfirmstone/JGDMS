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
import java.util.Arrays;

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
        // Read to EOF into a buffer that starts small (the stream's own available() hint, floor 256)
        // and doubles up to max + 1. Reading one byte past the limit lets us detect and reject
        // oversize input, and a huge stream still costs at most ~(max + 1) bytes. The initial size
        // is only a hint: it is capped at max + 1 and the buffer never exceeds that bound.
        final long limit = (long) max + 1;
        int available = 0;
        try {
            available = Math.max(0, in.available());
        } catch (IOException ignore) {
            // a hint only; fall back to the floor
        }
        byte[] buf = new byte[(int) Math.min(limit, Math.max(256, available))];
        int n = 0;
        while (true) {
            if (n == buf.length) {
                if (n == limit) break;                       // read max + 1 bytes: oversize
                buf = Arrays.copyOf(buf, (int) Math.min(limit, 2L * n));
            }
            int r = in.read(buf, n, buf.length - n);
            if (r < 0) break;                                // EOF
            n += r;
        }
        if (n > max) {
            throw new IOException("DER input exceeds the maximum permitted size of " + max
                    + " bytes; refusing to buffer a larger stream");
        }
        return n == buf.length ? buf : Arrays.copyOf(buf, n);
    }

    /**
     * Reads a DER OBJECT STREAM (a sequence of top-level TLVs) fully, framed item by item under {@link #maxInputBytes()}:
     * each item's header is read first, and an item whose declared length cannot fit in the remaining budget is rejected
     * BEFORE any of its content is buffered. A hostile stream that declares a huge item therefore costs a header, not
     * {@code maxInputBytes} of buffering and its growth copies.
     *
     * <p>What it returns is exactly what {@link #readAllBytesBounded(InputStream)} returns for the same stream, with ONE
     * deliberate difference: a stream whose item declares more content than the budget has left is refused even if the
     * stream is actually shorter (a truncated final item that the plain read would have buffered and left to fail when,
     * and if, that item is read). Streams that cannot be framed at all (a header that is not a canonical definite
     * length) are buffered under the same cap as before, so framing errors keep being reported where they were: when the
     * item is read. The version octet is the first item like any other.
     *
     * <p>Only for object streams. A payload that is not item-framed (a {@code MarshalledInstance}'s captured payload) must use
     * {@link #readAllBytesBounded(InputStream)}.
     *
     * @throws IOException if reading fails, the stream holds more than the limit, or an item declares more than fits
     */
    public byte[] readFramedBounded(InputStream in) throws IOException {
        return readFramedBounded(in, maxInputBytes);
    }

    /** As {@link #readFramedBounded(InputStream)} with an explicit limit (package-private; for tests). */
    static byte[] readFramedBounded(InputStream in, int max) throws IOException {
        if (max >= Integer.MAX_VALUE - 8) {
            return in.readAllBytes();                    // cap deliberately disabled
        }
        int available = 0;
        try {
            available = Math.max(0, in.available());
        } catch (IOException ignore) {
            // a hint only
        }
        // The first item's header tells us what to allocate, so the hint only seeds a modest buffer: a stream that
        // claims a huge available() must not make us allocate it before a single header has been read.
        Buffer b = new Buffer(Math.min(max, Math.max(256, Math.min(available, 64 * 1024))), max);
        while (true) {
            // ---- one item header: identifier octet(s), then length octet(s) ----
            int first = in.read();
            if (first < 0) return b.result();            // EOF at an item boundary
            b.put(first);
            if ((first & 0x1F) == 0x1F) {                // high-tag-number form: base-128 octets
                int count = 0, c;
                do {
                    c = in.read();
                    if (c < 0) return b.result();        // EOF inside a header: the reader rejects it when it gets there
                    b.put(c);
                    if (++count > 5) { b.readRest(in); return b.result(); }   // not a tag we can frame
                } while ((c & 0x80) != 0);
            }
            int l = in.read();
            if (l < 0) return b.result();
            b.put(l);
            long len;
            if (l < 0x80) {
                len = l;
            } else if (l == 0x80 || l == 0xFF || (l & 0x7F) > 4) {
                b.readRest(in);                          // indefinite / reserved / absurd length: not framable
                return b.result();
            } else {
                len = 0;
                for (int i = 0, octets = l & 0x7F; i < octets; i++) {
                    int c = in.read();
                    if (c < 0) return b.result();
                    b.put(c);
                    len = (len << 8) | c;
                }
            }
            // ---- the item's content: refuse BEFORE buffering any of it if it cannot fit ----
            if (b.n + len > max) {
                throw new IOException("DER input exceeds the maximum permitted size of " + max
                        + " bytes: an item declares " + len + " content bytes with " + (max - b.n)
                        + " left; refusing to buffer it");
            }
            if (b.readFully(in, (int) len) < len) return b.result();   // truncated: keep what arrived, as the plain read does
        }
    }

    /** A growing buffer that never holds more than {@code max} bytes and says so the moment a byte beyond that arrives. */
    private static final class Buffer {
        private byte[] buf;
        private int n;
        private final int max;

        Buffer(int initial, int max) {
            this.buf = new byte[initial];
            this.max = max;
        }

        void put(int b) throws IOException {
            if (n == max) throw oversize(max);
            grow(n + 1);
            buf[n++] = (byte) b;
        }

        private void grow(long need) {
            if (need > buf.length) {
                buf = Arrays.copyOf(buf, (int) Math.min(max, Math.max(need, 2L * buf.length)));
            }
        }

        /** Reads up to {@code len} bytes (the caller has checked they fit); returns how many arrived. */
        int readFully(InputStream in, int len) throws IOException {
            grow((long) n + len);
            int got = 0;
            while (got < len) {
                int r = in.read(buf, n + got, len - got);
                if (r < 0) break;
                got += r;
            }
            n += got;
            return got;
        }

        /** The plain bounded read-to-EOF, from the current position. */
        void readRest(InputStream in) throws IOException {
            while (true) {
                if (n == buf.length) {
                    if (n == max) {
                        if (in.read() >= 0) throw oversize(max);   // one byte past the cap
                        return;
                    }
                    grow(n + 1L);
                }
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) return;
                n += r;
            }
        }

        byte[] result() {
            return n == buf.length ? buf : Arrays.copyOf(buf, n);
        }
    }

    private static IOException oversize(int max) {
        return new IOException("DER input exceeds the maximum permitted size of " + max
                + " bytes; refusing to buffer a larger stream");
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
