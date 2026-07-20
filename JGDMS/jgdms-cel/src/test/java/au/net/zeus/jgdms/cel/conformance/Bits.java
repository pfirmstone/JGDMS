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

package au.net.zeus.jgdms.cel.conformance;

/**
 * The corpus's exact-string-form conventions (CORPUS-FORMAT.md Sec 2.1),
 * decoded on the Java runner side: {@code "0x"}-prefixed 16-hex-digit
 * binary64 bit patterns, and lowercase hex byte strings.
 */
final class Bits {

    private Bits() {}

    static double parseDoubleBits(String hex) {
        if (hex == null || !hex.startsWith("0x")) {
            throw new IllegalArgumentException("expected a \"0x\"-prefixed bit-pattern string, got: " + hex);
        }
        long bits = Long.parseUnsignedLong(hex.substring(2), 16);
        return Double.longBitsToDouble(bits);
    }

    static long parseUnsignedHexLong(String hex) {
        if (hex == null || !hex.startsWith("0x")) {
            throw new IllegalArgumentException("expected a \"0x\"-prefixed bit-pattern string, got: " + hex);
        }
        return Long.parseUnsignedLong(hex.substring(2), 16);
    }

    static byte[] hexToBytes(String hex) {
        if (hex == null) throw new IllegalArgumentException("null hex string");
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("odd-length hex string (malformed corpus data): " + hex);
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte value : b) {
            sb.append(Character.forDigit((value >> 4) & 0xF, 16));
            sb.append(Character.forDigit(value & 0xF, 16));
        }
        return sb.toString();
    }
}
