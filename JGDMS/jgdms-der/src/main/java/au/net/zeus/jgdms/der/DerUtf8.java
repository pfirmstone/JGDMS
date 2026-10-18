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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Strict UTF-8 for DER {@code UTF8String} content (JGDMS-STD-006 sec.3 principles 2 and 6: one
 * encoding per value; fail-secure decode).
 *
 * <p>{@code new String(bytes, UTF_8)} never throws: it substitutes U+FFFD for every malformed
 * sequence, so many different byte strings decode to the same value (a one-encoding-per-value
 * break), and {@code String.getBytes(UTF_8)} substitutes {@code '?'} for an unpaired surrogate, so
 * two different Strings encode to identical bytes and the value silently changes in transit. This
 * helper makes both directions strict: malformed UTF-8 is rejected on decode, and a String that
 * is not well-formed UTF-16 is rejected on encode. There is deliberately no lenient mode: a flag
 * that admits a second encoding is exactly what canonical form forbids.
 */
public final class DerUtf8 {

    /**
     * Thrown (unchecked, because {@code DerWriter}'s encoders do not declare checked exceptions) when
     * a String that is not well-formed UTF-16 is passed to {@link DerWriter#writeUtf8String}. The
     * object-stream layer converts it to an {@link java.io.IOException} at its public boundary.
     */
    public static final class UnencodableStringException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public UnencodableStringException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private DerUtf8() {
        throw new AssertionError("no instances");
    }

    /**
     * Decodes {@code len} bytes of well-formed UTF-8 from {@code buf} at {@code off}.
     *
     * <p>Fast path: the JDK's decoder always replaces a malformed sequence with U+FFFD, so a result
     * containing no U+FFFD proves the input was well-formed. Only when one is present (a genuine
     * U+FFFD in valid input, or malformed input) is the input re-checked with a reporting decoder.
     *
     * @throws DerException if the bytes are not well-formed UTF-8 (overlong forms, encoded
     *                      surrogates, code points above U+10FFFF, truncated sequences, stray
     *                      continuation or invalid lead bytes)
     */
    public static String decode(byte[] buf, int off, int len) throws DerException {
        String s = new String(buf, off, len, StandardCharsets.UTF_8);
        if (s.indexOf('\uFFFD') < 0) {
            return s;
        }
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return dec.decode(ByteBuffer.wrap(buf, off, len)).toString();
        } catch (CharacterCodingException e) {
            throw new DerException("UTF8String: content is not well-formed UTF-8 (" + e + ")", e);
        }
    }

    /** As {@link #decode(byte[], int, int)} over a whole array. */
    public static String decode(byte[] buf) throws DerException {
        return decode(buf, 0, buf.length);
    }

    /**
     * Encodes {@code s} as UTF-8, rejecting a String that is not well-formed UTF-16.
     *
     * @throws DerException if {@code s} contains an unpaired (or reversed) surrogate, which has no
     *                      UTF-8 encoding
     */
    public static byte[] encode(String s) throws DerException {
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i + 1 < n
                        && Character.isLowSurrogate(s.charAt(i + 1))) {
                    i++;
                    continue;
                }
                throw new DerException("UTF8String: unpaired surrogate U+"
                        + Integer.toHexString(c).toUpperCase() + " at index " + i
                        + " has no UTF-8 encoding (it would be silently replaced by '?')");
            }
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
