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
import au.net.zeus.jgdms.der.DerWriter;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps {@code wireType} strings from {@code AtomicSerialFieldDef} to DER decode
 * operations, per JGDMS-STD-006 Phase 3 and STD-008 B1 inc-3.
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
 *   <li>{@code "enum:<className>"} -- DER UTF8String (constant name) or DER NULL
 *       -> the {@link Enum} constant; unknown name rejected with {@link DerException}</li>
 *   <li>{@code "array:<componentWireType>"} -- DER NULL or DER SEQUENCE of element TLVs
 *       -> the appropriate Java array; multi-dim rejected; element-wise NULL supported
 *       for nullable element types (String, enum); {@code "array:@AtomicSerial:<class>"}
 *       is NOT decoded here (requires {@code der.object}) -- see {@link DerFieldStore}</li>
 * </ul>
 *
 * <p>Any other wire type throws {@link DerException} (fail-secure, S3 principle 6).
 * {@code float}, {@code double}, {@code char} are supported with STRICT canonical
 * encodings per STD-008 sec.17.3 (S7.6 deferral lifted): float/double as IEEE-754 in
 * OCTET STRING with canonical NaN and {@code +0.0} only ({@code -0.0} bits rejected),
 * char as Unicode codepoint INTEGER (BMP non-surrogate only). Non-canonical patterns are
 * rejected fail-secure to preserve Entry-matching determinism.
 *
 * <p>This class is stateless and thread-safe. All methods are package-accessible
 * so that {@link DerFieldStore} (and future DER codec components) can call them,
 * while keeping the decode logic in one place.
 */
final class WireTypes {

    private WireTypes() {
        throw new AssertionError("no instances");
    }

    /** DER NULL tag byte (0x05). */
    private static final int TAG_NULL = 0x05;

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
     *   <li>{@link String} for {@code java.lang.String} (or {@code null} for DER NULL)</li>
     *   <li>{@code byte[]} for octet-string wire types</li>
     *   <li>an {@link Enum} constant for {@code "enum:<className>"} (or {@code null})</li>
     *   <li>a Java array for {@code "array:<componentWireType>"} (or {@code null})</li>
     * </ul>
     *
     * <p><b>Note:</b> {@code "array:@AtomicSerial:<class>"} is NOT decoded here --
     * it requires {@code ObjectCodec} (in {@code der.object}) to decode nested records
     * with the cumulative depth guard. {@link DerFieldStore} stores those as
     * {@code NestedArrayRaw} and delegates to {@code DerGetArg} in {@code der.object}.
     *
     * @param reader   a {@link DerReader} positioned at the next TLV to decode
     * @param wireType the wire-type string from the field's {@code AtomicSerialFieldDef}
     * @return the decoded, boxed Java value (may be {@code null} for nullable types)
     * @throws DerException if the wireType is unsupported, if the DER encoding
     *                      is malformed, or if the decoded integer value overflows
     *                      the declared type
     */
    static Object decode(DerReader reader, String wireType, ResolutionContext res) throws DerException {
        // Enum fields: "enum:<className>" (STD-008 sec.17.1)
        if (wireType.startsWith("enum:")) {
            return decodeEnum(reader, wireType, res);
        }
        // Array fields: "array:<componentWireType>" (STD-008 sec.17.2)
        // Note: "array:@AtomicSerial:<class>" is NEVER decoded here; DerFieldStore
        // intercepts it and stores a NestedArrayRaw for DerGetArg to handle.
        if (wireType.startsWith("array:")) {
            return decodeArray(reader, wireType, res);
        }

        // A nullable scalar reference field (boxed primitive, String, byte[]) whose value was
        // null travels as DER NULL. A primitive field never encodes null (its captured value is
        // autoboxed non-null), so a NULL here only ever corresponds to a nullable boxed/reference
        // field -- return null. (Enum/array nulls are handled above by decodeEnum/decodeArray.)
        if (peekIsNull(reader)) {
            readNull(reader);
            return null;
        }

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

            // A Class field: its name was written UTF8; resolve through the
            // endpoint-assigned ResolutionContext loader (primitive-aware).
            case "java.lang.Class" -> resolveClass(reader.readUtf8String(), res);

            case "byte[]", "[B" -> reader.readOctetString();

            // STD-008 sec.17.3 -- float/double/char with STRICT rejection of non-canonical
            // patterns. The wire invariant ("one byte sequence per value") is enforced
            // symmetrically: encoder produces canonical form, decoder rejects everything else.
            // Lenient normalize-on-decode would defeat Entry-matching determinism (matching
            // is on transmitted bytes, not decode/re-encode).
            case "float", "java.lang.Float" -> decodeFloat(reader);
            case "double", "java.lang.Double" -> decodeDouble(reader);
            case "char", "java.lang.Character" -> decodeChar(reader);

            default ->
                    throw new DerException("unsupported wire type: " + wireType);
        };
    }

    /**
     * Decodes the next TLV as a <b>class-free scalar</b> gated on the field's DECLARED
     * {@code wireType}, resolving and loading <b>no class whatsoever</b> (STD-011 §B2
     * class-free candidate projection). Unlike {@link #decode}, this method:
     * <ul>
     *   <li>decodes ONLY the inert scalar kinds -- {@code boolean}, the int family
     *       ({@code byte}/{@code short}/{@code int}/{@code long}), {@code float}/{@code double},
     *       {@code java.lang.String}, {@code byte[]}, plus a wire-null ({@code NULL}) -- using the
     *       same primitive {@link DerReader} decoders and canonical checks {@link #decode} uses;</li>
     *   <li>fail-closes ({@link DerException}) for EVERY non-scalar declared type -- {@code enum:},
     *       {@code array:}, any collection token, {@code @AtomicSerial}, {@code char}/{@code
     *       java.lang.Character}, {@code java.lang.Class}, {@code any} -- <b>without</b> calling
     *       {@link #decodeEnum}, {@link #decodeArray}, {@code loadClass}, or any reconstruction.
     *       The reject is decided by {@link #isClassFreeScalar} <em>before</em> any wire byte is
     *       interpreted (before the NULL peek), so a null-valued enum/array/Class field is
     *       fail-closed too, never silently mapped to {@code null}.</li>
     * </ul>
     *
     * <p>There is no {@link ResolutionContext} parameter <em>by design</em>: this method must never
     * reach a class loader, so it cannot be handed one. It is the field-level (nested-record, bare
     * declared-type TLV) counterpart of the self-describing object-stream item scalar reader used by
     * the top-level slice.
     *
     * @param reader   a reader positioned at the field's TLV
     * @param wireType the field's declared wire type
     * @return the decoded scalar (boxed) value, or {@code null} for a wire-null
     * @throws DerException if the declared type is not a class-free scalar, or the encoding is
     *                      malformed / out of range (fail-closed)
     */
    static Object decodeScalarNoClassLoad(DerReader reader, String wireType) throws DerException {
        if (!isClassFreeScalar(wireType)) {
            throw new DerException("WireTypes.decodeScalarNoClassLoad: declared wire type '"
                    + wireType + "' is not a class-free scalar (enum/array/collection/@AtomicSerial/"
                    + "char/Class/any are fail-closed -- no class is ever loaded)");
        }
        if (peekIsNull(reader)) {
            readNull(reader);
            return null;
        }
        return switch (wireType) {
            case "boolean", "java.lang.Boolean" -> reader.readBoolean();
            case "byte", "java.lang.Byte" -> {
                long lv = toLong(reader.readInteger(), wireType);
                if (lv < Byte.MIN_VALUE || lv > Byte.MAX_VALUE) {
                    throw new DerException("INTEGER value " + lv + " overflows wire type '"
                            + wireType + "'");
                }
                yield (byte) lv;
            }
            case "short", "java.lang.Short" -> {
                long lv = toLong(reader.readInteger(), wireType);
                if (lv < Short.MIN_VALUE || lv > Short.MAX_VALUE) {
                    throw new DerException("INTEGER value " + lv + " overflows wire type '"
                            + wireType + "'");
                }
                yield (short) lv;
            }
            case "int", "java.lang.Integer" -> {
                long lv = toLong(reader.readInteger(), wireType);
                if (lv < Integer.MIN_VALUE || lv > Integer.MAX_VALUE) {
                    throw new DerException("INTEGER value " + lv + " overflows wire type '"
                            + wireType + "'");
                }
                yield (int) lv;
            }
            case "long", "java.lang.Long" -> {
                try {
                    yield reader.readInteger().longValueExact();
                } catch (ArithmeticException e) {
                    throw new DerException("INTEGER value overflows wire type '" + wireType + "'", e);
                }
            }
            case "java.lang.String" -> reader.readUtf8String();
            case "byte[]", "[B" -> reader.readOctetString();
            case "float", "java.lang.Float" -> decodeFloat(reader);
            case "double", "java.lang.Double" -> decodeDouble(reader);
            // isClassFreeScalar admits nothing else; unreachable, but fail-closed for defence.
            default -> throw new DerException("WireTypes.decodeScalarNoClassLoad: '"
                    + wireType + "' is not a class-free scalar");
        };
    }

    /**
     * Whether {@code wireType} names one of the inert, class-free scalar kinds
     * {@link #decodeScalarNoClassLoad} may decode. {@code char}/{@code java.lang.Character} and
     * {@code java.lang.Class} are deliberately EXCLUDED (deferred / not a CEL scalar), as are
     * {@code enum:} / {@code array:} / collection tokens / {@code @AtomicSerial} / {@code any}.
     */
    private static boolean isClassFreeScalar(String wireType) {
        return switch (wireType) {
            case "boolean", "java.lang.Boolean",
                 "byte", "java.lang.Byte",
                 "short", "java.lang.Short",
                 "int", "java.lang.Integer",
                 "long", "java.lang.Long",
                 "float", "java.lang.Float",
                 "double", "java.lang.Double",
                 "java.lang.String",
                 "byte[]", "[B" -> true;
            default -> false;
        };
    }

    /** Canonical IEEE-754 NaN bit patterns (STD-008 sec.17.3.1); MUST match
     *  {@code ObjectCodec.CANONICAL_*_NAN_BITS}. */
    private static final int  CANONICAL_FLOAT_NAN_BITS  = 0x7FC00000;
    private static final long CANONICAL_DOUBLE_NAN_BITS = 0x7FF8000000000000L;
    private static final int  NEGATIVE_ZERO_FLOAT_BITS  = 0x80000000;
    private static final long NEGATIVE_ZERO_DOUBLE_BITS = 0x8000000000000000L;
    private static final int  FLOAT_EXP_MASK            = 0x7F800000;
    private static final int  FLOAT_MANT_MASK           = 0x007FFFFF;
    private static final long DOUBLE_EXP_MASK           = 0x7FF0000000000000L;
    private static final long DOUBLE_MANT_MASK          = 0x000FFFFFFFFFFFFFL;

    static Float decodeFloat(DerReader reader) throws DerException {
        byte[] content = reader.readOctetString();
        if (content.length != 4) {
            throw new DerException("DER float OCTET STRING must be exactly 4 bytes, got "
                    + content.length + " (STD-008 sec.17.3.1)");
        }
        int bits =  ((content[0] & 0xFF) << 24)
                  | ((content[1] & 0xFF) << 16)
                  | ((content[2] & 0xFF) <<  8)
                  |  (content[3] & 0xFF);
        // Reject -0.0 bits (canonical form is +0.0; STD-008 sec.17.3.1).
        if (bits == NEGATIVE_ZERO_FLOAT_BITS) {
            throw new DerException("DER float: -0.0 bit pattern (0x80000000) is not canonical;"
                    + " encoders MUST canonicalize -0.0 to +0.0");
        }
        // Any NaN bit pattern MUST equal the canonical quiet-NaN bit pattern.
        // NaN := exp all-ones AND mantissa nonzero.
        boolean isNaN = (bits & FLOAT_EXP_MASK) == FLOAT_EXP_MASK
                     && (bits & FLOAT_MANT_MASK) != 0;
        if (isNaN && bits != CANONICAL_FLOAT_NAN_BITS) {
            throw new DerException(
                    "DER float: non-canonical NaN bit pattern 0x"
                    + String.format("%08X", bits)
                    + "; canonical NaN is 0x7FC00000 (STD-008 sec.17.3.1)");
        }
        return Float.intBitsToFloat(bits);
    }

    static Double decodeDouble(DerReader reader) throws DerException {
        byte[] content = reader.readOctetString();
        if (content.length != 8) {
            throw new DerException("DER double OCTET STRING must be exactly 8 bytes, got "
                    + content.length + " (STD-008 sec.17.3.1)");
        }
        long bits = 0L;
        for (int i = 0; i < 8; i++) {
            bits = (bits << 8) | (content[i] & 0xFF);
        }
        if (bits == NEGATIVE_ZERO_DOUBLE_BITS) {
            throw new DerException("DER double: -0.0 bit pattern is not canonical;"
                    + " encoders MUST canonicalize -0.0 to +0.0");
        }
        boolean isNaN = (bits & DOUBLE_EXP_MASK) == DOUBLE_EXP_MASK
                     && (bits & DOUBLE_MANT_MASK) != 0L;
        if (isNaN && bits != CANONICAL_DOUBLE_NAN_BITS) {
            throw new DerException(
                    "DER double: non-canonical NaN bit pattern 0x"
                    + String.format("%016X", bits)
                    + "; canonical NaN is 0x7FF8000000000000 (STD-008 sec.17.3.1)");
        }
        return Double.longBitsToDouble(bits);
    }

    static Character decodeChar(DerReader reader) throws DerException {
        java.math.BigInteger v = reader.readInteger();
        int cp;
        try {
            cp = v.intValueExact();
        } catch (ArithmeticException e) {
            throw new DerException("DER char codepoint out of range: " + v, e);
        }
        if (cp < 0 || cp > 0xFFFF) {
            throw new DerException("DER char codepoint " + cp
                    + " out of range [0, 0xFFFF] (BMP only; STD-008 sec.17.3.2 --"
                    + " supplementary-plane codepoints must use String)");
        }
        if (cp >= 0xD800 && cp <= 0xDFFF) {
            throw new DerException("DER char: surrogate codepoint 0x"
                    + Integer.toHexString(cp).toUpperCase()
                    + " is not a valid Unicode codepoint (STD-008 sec.17.3.2)");
        }
        return (char) cp;
    }

    /**
     * Decodes an enum field from the next TLV in {@code reader}.
     *
     * <p>Wire format: DER UTF8String holding the constant name, or DER NULL for a
     * null enum field. An unknown constant name is rejected with {@link DerException}
     * (fail-secure -- no silent default).
     *
     * @param reader   reader positioned at the enum TLV
     * @param wireType the full wireType string, e.g. {@code "enum:com.example.Color"}
     * @return the {@link Enum} constant, or {@code null} for DER NULL
     * @throws DerException if the name is unknown, the class cannot be loaded,
     *                      or the TLV is malformed
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object decodeEnum(DerReader reader, String wireType, ResolutionContext res) throws DerException {
        String className = wireType.substring(5); // strip "enum:"
        if (peekIsNull(reader)) {
            readNull(reader);
            return null;
        }
        String name = reader.readUtf8String();
        Class<?> enumClass = loadClass(className, res);
        if (!enumClass.isEnum()) {
            throw new DerException(
                    "WireTypes.decodeEnum: class '" + className + "' is not an enum");
        }
        try {
            return Enum.valueOf((Class<? extends Enum>) enumClass, name);
        } catch (IllegalArgumentException e) {
            DerException de = new DerException(
                    "WireTypes.decodeEnum: unknown enum constant '"
                    + name + "' for class '" + className + "' -- "
                    + "fail-secure: removed constants are rejected on decode");
            de.initCause(e);
            throw de;
        }
    }

    /**
     * Decodes an array field from the next TLV in {@code reader}.
     *
     * <p>Wire format: DER NULL for a null array, or a DER SEQUENCE of element TLVs.
     * Elements whose types are nullable (String, enum) may themselves be DER NULL.
     *
     * <p><b>NOT called for {@code "array:@AtomicSerial:<class>"}</b> -- those are
     * stored as {@link DerFieldStore.NestedArrayRaw} by {@link DerFieldStore} and decoded
     * lazily via {@code DerGetArg} / {@code ObjectCodec} in {@code der.object}
     * to preserve the no-cycle invariant.
     *
     * @param reader   reader positioned at the array TLV
     * @param wireType the full wireType string, e.g. {@code "array:int"},
     *                 {@code "array:java.lang.String"}, {@code "array:enum:com.example.Color"}
     * @return the Java array (may be {@code null} for DER NULL)
     * @throws DerException if the component type is unsupported, multi-dim, or the
     *                      encoding is malformed
     */
    static Object decodeArray(DerReader reader, String wireType, ResolutionContext res) throws DerException {
        if (peekIsNull(reader)) {
            readNull(reader);
            return null;
        }

        String componentWT = wireType.substring(6); // strip "array:"

        // Reject multi-dim: if the component wireType is itself an array
        if (componentWT.startsWith("array:")) {
            throw new DerException(
                    "WireTypes.decodeArray: multi-dimensional arrays are not supported"
                    + " (wireType: '" + wireType + "'); inc-3 supports one level only");
        }

        // This method must NOT be called for @AtomicSerial element arrays --
        // DerFieldStore routes those to NestedArrayRaw. Guard here defensively.
        if (componentWT.startsWith("@AtomicSerial")) {
            throw new DerException(
                    "WireTypes.decodeArray: array:@AtomicSerial must not be decoded "
                    + "in WireTypes (would require der.object -- cycle); "
                    + "DerFieldStore must store it as NestedArrayRaw");
        }

        // Read the outer SEQUENCE: one TLV per element
        DerReader seq = reader.readSequence();
        List<Object> elements = new ArrayList<>();
        while (seq.hasMore()) {
            elements.add(decodeArrayElement(seq, componentWT, res));
        }

        return buildArray(elements, componentWT, res);
    }

    /**
     * Decodes one element TLV from an array SEQUENCE.
     *
     * <p>For nullable element types (String, enum), the element may be DER NULL.
     * For primitive element types (boolean/byte/short/int/long), DER NULL is
     * not a valid element (primitives cannot be null in Java).
     *
     * @param seq        sub-reader positioned at the element TLV
     * @param componentWT the wireType of the component (e.g. {@code "int"},
     *                   {@code "java.lang.String"}, {@code "enum:com.example.Color"})
     * @return the decoded element value (may be {@code null} for nullable types)
     * @throws DerException if the element encoding is malformed or invalid
     */
    private static Object decodeArrayElement(DerReader seq, String componentWT, ResolutionContext res)
            throws DerException {
        // Nullable element types: String and enum support per-element DER NULL.
        if (componentWT.equals("java.lang.String")) {
            if (peekIsNull(seq)) {
                readNull(seq);
                return null;
            }
            return seq.readUtf8String();
        }
        if (componentWT.equals("java.lang.Class")) {
            if (peekIsNull(seq)) {
                readNull(seq);
                return null;
            }
            return resolveClass(seq.readUtf8String(), res);
        }
        if (componentWT.startsWith("enum:")) {
            return decodeEnum(seq, componentWT, res);
        }
        // Primitive types: decode normally (DER NULL is an error for primitives)
        return decode(seq, componentWT, res);
    }

    /**
     * Builds a Java array of the appropriate component type from the decoded elements.
     *
     * @param elements   the decoded element values (in array index order)
     * @param componentWT the wireType of the component
     * @return a typed Java array
     * @throws DerException if the component class cannot be loaded or elements have
     *                      unexpected null values for primitive component types
     */
    private static Object buildArray(List<Object> elements, String componentWT, ResolutionContext res)
            throws DerException {
        int n = elements.size();
        switch (componentWT) {
            case "boolean", "java.lang.Boolean" -> {
                boolean[] arr = new boolean[n];
                for (int i = 0; i < n; i++) arr[i] = (Boolean) elements.get(i);
                return arr;
            }
            case "byte", "java.lang.Byte" -> {
                byte[] arr = new byte[n];
                for (int i = 0; i < n; i++) arr[i] = (Byte) elements.get(i);
                return arr;
            }
            case "short", "java.lang.Short" -> {
                short[] arr = new short[n];
                for (int i = 0; i < n; i++) arr[i] = (Short) elements.get(i);
                return arr;
            }
            case "int", "java.lang.Integer" -> {
                int[] arr = new int[n];
                for (int i = 0; i < n; i++) arr[i] = (Integer) elements.get(i);
                return arr;
            }
            case "long", "java.lang.Long" -> {
                long[] arr = new long[n];
                for (int i = 0; i < n; i++) arr[i] = (Long) elements.get(i);
                return arr;
            }
            case "java.lang.String" -> {
                return elements.toArray(new String[0]);
            }
            case "java.lang.Class" -> {
                return elements.toArray(new Class<?>[0]);
            }
            default -> {
                // Enum array: "enum:<className>"
                if (componentWT.startsWith("enum:")) {
                    String enumClassName = componentWT.substring(5);
                    Class<?> enumClass = loadClass(enumClassName, res);
                    Object arr = Array.newInstance(enumClass, n);
                    for (int i = 0; i < n; i++) {
                        Array.set(arr, i, elements.get(i)); // element may be null
                    }
                    return arr;
                }
                throw new DerException(
                        "WireTypes.buildArray: unsupported component wireType '"
                        + componentWT + "'");
            }
        }
    }

    /**
     * Peeks at the next tag byte in {@code reader} to check whether it is DER NULL
     * ({@code 0x05}). Does NOT advance the reader cursor.
     *
     * @param reader the reader to peek at
     * @return {@code true} if the next TLV is a DER NULL tag
     * @throws DerException if the reader has no more data
     */
    static boolean peekIsNull(DerReader reader) throws DerException {
        return reader.peekTag().encode()[0] == (byte) TAG_NULL;
    }

    /**
     * Reads and discards a DER NULL TLV ({@code 05 00}) from {@code reader}.
     *
     * @param reader the reader positioned at a DER NULL TLV
     * @throws DerException if the TLV is not a well-formed DER NULL
     */
    static void readNull(DerReader reader) throws DerException {
        // Read the TLV header: tag must be NULL (0x05), length must be 0
        DerReader.TlvHeader hdr = reader.readTlvHeader();
        if (hdr.tag().encode()[0] != (byte) TAG_NULL) {
            throw new DerException(
                    "WireTypes.readNull: expected NULL tag (0x05), got tag "
                    + hdr.tag());
        }
        if (hdr.contentLength() != 0) {
            throw new DerException(
                    "WireTypes.readNull: NULL TLV must have zero-length content, got "
                    + hdr.contentLength());
        }
        // No content to read for NULL
    }

    /**
     * Loads a class by name against the endpoint-assigned {@link ResolutionContext} (NOT the
     * thread-context loader -- see {@link ResolutionContext}).
     *
     * @param className the fully-qualified class name
     * @param res       the endpoint-assigned resolution context
     * @return the loaded class
     * @throws DerException if the class cannot be found
     */
    /**
     * The nine primitive (incl. {@code void}) pseudo-types, keyed by {@link Class#getName()}.
     * These have no defining loader and are NOT resolvable by name via a ClassLoader, so a
     * Class field carrying one is mapped directly rather than routed through the endpoint.
     */
    private static final Map<String, Class<?>> PRIMITIVE_CLASSES = Map.of(
            "boolean", boolean.class, "byte", byte.class, "char", char.class,
            "short", short.class, "int", int.class, "long", long.class,
            "float", float.class, "double", double.class, "void", void.class);

    /**
     * Resolves a Class-field name. Primitive/void names map directly; every other name is
     * resolved through the endpoint-assigned {@link ResolutionContext} loader (never ambient/
     * thread-context) -- endpoints are assigned a ClassLoader and it governs stream class
     * resolution.
     */
    private static Class<?> resolveClass(String className, ResolutionContext res) throws DerException {
        Class<?> prim = PRIMITIVE_CLASSES.get(className);
        if (prim != null) {
            return prim;
        }
        return loadClass(className, res);
    }

    private static Class<?> loadClass(String className, ResolutionContext res) throws DerException {
        try {
            // Endpoint-assigned resolution via ClassLoading -- NEVER the thread-context loader
            // (the Warres ambient-resolution failure). DER carries no codebase, so the name
            // resolves against the endpoint's defaultLoader through the preferred/OSGi-aware SPI.
            return res.loadClass(className);
        } catch (ClassNotFoundException ex) {
            DerException de = new DerException(
                    "WireTypes: cannot load class '" + className + "'");
            de.initCause(ex);
            throw de;
        }
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
                    "long", "java.lang.Long",
                    "char", "java.lang.Character" -> "INTEGER (0x02)";
            case "byte[]", "[B",
                    "float", "java.lang.Float",
                    "double", "java.lang.Double" -> "OCTET STRING (0x04)";
            case "java.lang.String", "java.lang.Class" -> "UTF8String (0x0C)";
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
