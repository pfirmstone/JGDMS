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

package au.net.zeus.jgdms.der.util;

/**
 * Hex-dump test utility for the DER codec test suite.
 * <p>
 * Two complementary capabilities:
 * <ul>
 *   <li>{@link #toHex(byte[])} / {@link #fromHex(String)} &mdash; a canonical,
 *       lossless {@code byte[] <-> String} round-trip used to express
 *       known-answer DER vectors compactly and to compare encoder output
 *       against an external truth source.</li>
 *   <li>{@link #dump(byte[])} &mdash; a human-readable, multi-line offset /
 *       hex / ASCII listing for debugging failing assertions. {@code dump} is
 *       <em>not</em> a round-trip format; use {@code toHex}/{@code fromHex}
 *       when bytes must survive the trip back.</li>
 * </ul>
 * This class is deliberately dependency-free so it can be used by every test
 * in the module without pulling in production code.
 */
public final class Hex {

    private static final char[] LOWER = "0123456789abcdef".toCharArray();

    private Hex() {
        throw new AssertionError("no instances");
    }

    /**
     * Encodes bytes as a continuous lowercase hex string (two chars per byte,
     * no separators). The empty array maps to the empty string.
     *
     * @param bytes the bytes to encode (must not be {@code null})
     * @return the canonical lowercase hex encoding
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(LOWER[v >>> 4]);
            sb.append(LOWER[v & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * Decodes a hex string back to bytes. Tolerant of input formatting so that
     * hand-written vectors are convenient: ASCII whitespace (space, tab,
     * newline, carriage return), colons, and underscores are ignored, and both
     * upper- and lower-case digits are accepted. After stripping separators the
     * remaining digit count must be even.
     *
     * @param hex the hex text (must not be {@code null})
     * @return the decoded bytes; the empty/separator-only string yields an
     *         empty array
     * @throws IllegalArgumentException if a non-hex, non-separator character is
     *         present or the digit count is odd
     */
    public static byte[] fromHex(String hex) {
        if (hex == null) {
            throw new NullPointerException("hex");
        }
        // First pass: collect digit nibbles, skipping permitted separators.
        int n = hex.length();
        int[] nibbles = new int[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            char c = hex.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r'
                    || c == ':' || c == '_') {
                continue;
            }
            int d = Character.digit(c, 16);
            if (d < 0) {
                throw new IllegalArgumentException(
                        "Illegal hex character '" + c + "' at index " + i);
            }
            nibbles[count++] = d;
        }
        if ((count & 1) != 0) {
            throw new IllegalArgumentException(
                    "Odd number of hex digits: " + count);
        }
        byte[] out = new byte[count / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((nibbles[2 * i] << 4) | nibbles[2 * i + 1]);
        }
        return out;
    }

    /**
     * Produces a classic offset / hex / ASCII hex dump, 16 bytes per row, for
     * use in test failure messages. Non-printable bytes render as '{@code .}'.
     *
     * @param bytes the bytes to render (must not be {@code null})
     * @return a multi-line dump; the empty array yields the empty string
     */
    public static String dump(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < bytes.length; row += 16) {
            appendOffset(sb, row);
            sb.append("  ");
            int end = Math.min(row + 16, bytes.length);
            for (int i = row; i < row + 16; i++) {
                if (i < end) {
                    int v = bytes[i] & 0xFF;
                    sb.append(LOWER[v >>> 4]).append(LOWER[v & 0x0F]);
                } else {
                    sb.append("  ");
                }
                sb.append(i == row + 7 ? "  " : " ");
            }
            sb.append("|");
            for (int i = row; i < end; i++) {
                int v = bytes[i] & 0xFF;
                sb.append(v >= 0x20 && v < 0x7F ? (char) v : '.');
            }
            sb.append("|");
            if (end < bytes.length) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static void appendOffset(StringBuilder sb, int offset) {
        for (int shift = 28; shift >= 0; shift -= 4) {
            sb.append(LOWER[(offset >>> shift) & 0x0F]);
        }
    }
}
