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

package au.net.zeus.jgdms.der.getarg;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;

import java.math.BigInteger;

/**
 * Maps {@code wireType} strings from {@code AtomicSerialFieldDef} to DER decode
 * operations, per JGDMS-STD-006 Phase 3.
 *
 * <h3>Supported wire types</h3>
 * <ul>
 *   <li>{@code "boolean"}, {@code "java.lang.Boolean"} -- DER BOOLEAN -> {@link Boolean}</li>
 *   <li>{@code "byte"}, {@code "java.lang.Byte"} -- DER INTEGER -> {@link Byte}
 *       (overflow rejected)</li>
 *   <li>{@code "short"}, {@code "java.lang.Short"} -- DER INTEGER -> {@link Short}
 *       (overflow rejected)</li>
 *   <li>{@code "int"}, {@code "java.lang.Integer"} -- DER INTEGER -> {@link Integer}
 *       (overflow rejected)</li>
 *   <li>{@code "long"}, {@code "java.lang.Long"} -- DER INTEGER -> {@link Long}
 *       (overflow rejected)</li>
 *   <li>{@code "java.lang.String"} -- DER UTF8String -> {@link String}</li>
 *   <li>{@code "byte[]"}, {@code "[B"} -- DER OCTET STRING -> {@code byte[]}</li>
 * </ul>
 *
 * <p>Any other wire type throws {@link DerException} (fail-secure, S3 principle 6).
 * The types {@code float}, {@code double}, and {@code char} are DEFERRED per spec S7.6;
 * they throw {@code DerException} with a "unsupported wire type" message until implemented.
 *
 * <p>This class is stateless and thread-safe. All methods are package-accessible
 * so that {@link DerFieldStore} (and future DER codec components) can call them,
 * while keeping the decode logic in one place.
 */
final class WireTypes {

    private WireTypes() {
        throw new AssertionError("no instances");
    }

    /**
     * Decodes the next TLV from {@code reader} according to {@code wireType}.
     *
     * <p>The reader must be positioned at the start of the TLV. After a
     * successful call the reader cursor is positioned immediately after the
     * decoded TLV's last content byte.
     *
     * <p>The decoded value's Java type is:
     * <ul>
     *   <li>{@link Boolean} for boolean wire types</li>
     *   <li>{@link Byte}, {@link Short}, {@link Integer}, or {@link Long}
     *       for the corresponding integer wire types</li>
     *   <li>{@link String} for {@code java.lang.String}</li>
     *   <li>{@code byte[]} for octet-string wire types</li>
     * </ul>
     *
     * @param reader   a {@link DerReader} positioned at the next TLV to decode
     * @param wireType the wire-type string from the field's {@code AtomicSerialFieldDef}
     * @return the decoded, boxed Java value (never {@code null})
     * @throws DerException if the wireType is unsupported, if the DER encoding
     *                      is malformed, or if the decoded integer value overflows
     *                      the declared type
     */
    static Object decode(DerReader reader, String wireType) throws DerException {
        return switch (wireType) {
            case "boolean", "java.lang.Boolean" -> reader.readBoolean();

            case "byte", "java.lang.Byte" -> {
                BigInteger v = reader.readInteger();
                long lv = toLong(v, wireType);
                if (lv < Byte.MIN_VALUE || lv > Byte.MAX_VALUE) {
                    throw new DerException("INTEGER value " + lv
                            + " overflows wire type '" + wireType + "' range ["
                            + Byte.MIN_VALUE + ", " + Byte.MAX_VALUE + "]");
                }
                yield (byte) lv;
            }

            case "short", "java.lang.Short" -> {
                BigInteger v = reader.readInteger();
                long lv = toLong(v, wireType);
                if (lv < Short.MIN_VALUE || lv > Short.MAX_VALUE) {
                    throw new DerException("INTEGER value " + lv
                            + " overflows wire type '" + wireType + "' range ["
                            + Short.MIN_VALUE + ", " + Short.MAX_VALUE + "]");
                }
                yield (short) lv;
            }

            case "int", "java.lang.Integer" -> {
                BigInteger v = reader.readInteger();
                long lv = toLong(v, wireType);
                if (lv < Integer.MIN_VALUE || lv > Integer.MAX_VALUE) {
                    throw new DerException("INTEGER value " + lv
                            + " overflows wire type '" + wireType + "' range ["
                            + Integer.MIN_VALUE + ", " + Integer.MAX_VALUE + "]");
                }
                yield (int) lv;
            }

            case "long", "java.lang.Long" -> {
                BigInteger v = reader.readInteger();
                // BigInteger.longValueExact() throws ArithmeticException if > Long.MAX_VALUE
                try {
                    yield v.longValueExact();
                } catch (ArithmeticException e) {
                    throw new DerException("INTEGER value " + v
                            + " overflows wire type '" + wireType + "' (long range)", e);
                }
            }

            case "java.lang.String" -> reader.readUtf8String();

            case "byte[]", "[B" -> reader.readOctetString();

            // Explicitly deferred per S7.6 -- fail-secure, not silently ignored
            case "char", "java.lang.Character",
                    "float", "java.lang.Float",
                    "double", "java.lang.Double" ->
                    throw new DerException("unsupported wire type (deferred per S7.6): " + wireType);

            default ->
                    throw new DerException("unsupported wire type: " + wireType);
        };
    }

    /**
     * Returns the expected DER tag name string for the given wireType, for use in
     * mismatch messages. (Tag cross-checking is performed by the reader methods
     * themselves; this is just for human-readable error text.)
     */
    static String expectedTagName(String wireType) {
        return switch (wireType) {
            case "boolean", "java.lang.Boolean" -> "BOOLEAN (0x01)";
            case "byte", "java.lang.Byte",
                    "short", "java.lang.Short",
                    "int", "java.lang.Integer",
                    "long", "java.lang.Long" -> "INTEGER (0x02)";
            case "byte[]", "[B" -> "OCTET STRING (0x04)";
            case "java.lang.String" -> "UTF8String (0x0C)";
            default -> "unknown";
        };
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Safely converts a {@link BigInteger} to {@code long} for range checking.
     * The BigInteger may exceed long range for byte/short/int fields; we clip
     * to detect overflow with a simple comparison after using longValue().
     */
    private static long toLong(BigInteger v, String wireType) throws DerException {
        // If the BigInteger magnitude exceeds long range the narrowing check below
        // will always catch it; we just need to avoid the ArithmeticException path
        // ourselves and report a nicer DerException.
        if (v.bitLength() > 63) {
            throw new DerException("INTEGER value " + v
                    + " overflows wire type '" + wireType + "' (exceeds long range)");
        }
        return v.longValue();
    }
}
