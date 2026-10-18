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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The bounded eager-read DoS guard (finding A): refuse to buffer more than the limit. */
public class DerInputLimitsTest {

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) i;
        return b;
    }

    @Test
    public void underLimitReturnsAllBytes() throws Exception {
        byte[] data = bytes(100);
        byte[] out = DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(data), 1000);
        assertArrayEquals(data, out);
    }

    @Test
    public void exactlyAtLimitIsAccepted() throws Exception {
        byte[] data = bytes(256);
        byte[] out = DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(data), 256);
        assertEquals(256, out.length);
    }

    @Test
    public void oneOverLimitIsRejected() {
        byte[] data = bytes(257);
        IOException ex = assertThrows(IOException.class,
                () -> DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(data), 256));
        // never buffers the whole oversize stream -- just enough (limit + 1) to detect it
    }

    @Test
    public void hugeLimitDisablesTheCap() throws Exception {
        // Operator-disabled cap (>= MAX_VALUE-8): falls back to readAllBytes, no overflow.
        byte[] data = bytes(50);
        byte[] out = DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(data), Integer.MAX_VALUE);
        assertArrayEquals(data, out);
    }

    // ---- SOW-DER-Codec-Performance T5: proportional read, same verdicts ----

    /** Delivers at most {@code chunk} bytes per read and reports a chosen available(). */
    private static final class Trickle extends java.io.InputStream {
        private final byte[] data; private final int chunk; private final int avail; private int pos;
        Trickle(byte[] data, int chunk, int avail) { this.data = data; this.chunk = chunk; this.avail = avail; }
        @Override public int read() { return pos < data.length ? data[pos++] & 0xFF : -1; }
        @Override public int read(byte[] b, int off, int len) {
            if (pos >= data.length) return -1;
            int n = Math.min(Math.min(len, chunk), data.length - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
        @Override public int available() { return avail; }
    }

    @Test
    public void chunkedStreamIsReadToEof() throws Exception {
        for (int len : new int[] {0, 1, 255, 256, 257, 511, 512, 513, 5000, 65536}) {
            for (int chunk : new int[] {1, 7, 100, 4096}) {
                byte[] data = bytes(len);
                byte[] out = DerInputLimits.readAllBytesBounded(new Trickle(data, chunk, 0), 1 << 20);
                assertArrayEquals(data, out, "len=" + len + " chunk=" + chunk);
            }
        }
    }

    @Test
    public void limitBoundaryPairsHoldAtBufferGrowthPoints() throws Exception {
        for (int max : new int[] {1, 255, 256, 257, 511, 512, 513, 1000, 4096, 65536}) {
            byte[] atLimit = bytes(max);
            assertArrayEquals(atLimit,
                    DerInputLimits.readAllBytesBounded(new Trickle(atLimit, 33, 0), max), "at max=" + max);
            byte[] over = bytes(max + 1);
            assertThrows(IOException.class,
                    () -> DerInputLimits.readAllBytesBounded(new Trickle(over, 33, 0), max), "over max=" + max);
        }
    }

    @Test
    public void availableHintCannotBypassTheLimit() throws Exception {
        // A hostile stream claiming a huge (or negative) available() must neither allocate
        // beyond max + 1 nor change the verdict.
        byte[] atLimit = bytes(300);
        assertArrayEquals(atLimit,
                DerInputLimits.readAllBytesBounded(new Trickle(atLimit, 64, Integer.MAX_VALUE), 300));
        assertThrows(IOException.class,
                () -> DerInputLimits.readAllBytesBounded(new Trickle(bytes(301), 64, Integer.MAX_VALUE), 300));
        assertArrayEquals(atLimit,
                DerInputLimits.readAllBytesBounded(new Trickle(atLimit, 64, -5), 300));
    }

    @Test
    public void emptyStreamYieldsEmptyArray() throws Exception {
        assertEquals(0, DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(new byte[0]), 10).length);
    }
}
