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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DER object encoder and decoder for single {@code @AtomicSerial} classes
 * (JGDMS-STD-006 Phase 4.1 — no hierarchy; one private SEQUENCE per class).
 *
 * <h2>Encoding (object → DER)</h2>
 * <p>
 * {@link #encode(Object, Class, AtomicSerialSchemaRecord)} encodes an object's
 * state to the DER bytes of the class's private SEQUENCE. The field values are
 * read from the object's declared Java fields via reflection
 * ({@link Field#setAccessible(boolean)}). The wire name comes from the schema's
 * field definitions; the assumption (safe for standard {@code @AtomicSerial}
 * classes) is that the wire name equals the Java field name.
 *
 * <h2>Decoding (DER → object)</h2>
 * <p>
 * {@link #decode(Class, AtomicSerialSchemaRecord, byte[])} decodes the DER bytes
 * of one class's private SEQUENCE, builds a {@link DerFieldStore}, assembles a
 * single-entry {@link DerGetArg}, and drives construction by reflectively invoking
 * the {@code public C(AtomicSerial.GetArg)} constructor.
 *
 * <h2>check-before-construction contract</h2>
 * <p>
 * The {@code (GetArg)} constructor is responsible for calling its static
 * {@code check(GetArg)} method before assigning any field. If {@code check}
 * throws {@link IOException} or {@link InvalidObjectException}, the
 * {@link InvocationTargetException} wrapper is unwrapped and the exception
 * propagated. No partially-constructed object is returned.
 *
 * <h2>Type mapping for encoding (Class → wireType)</h2>
 * <ul>
 *   <li>{@code boolean} / {@link Boolean} → DER BOOLEAN</li>
 *   <li>{@code byte} / {@link Byte} → DER INTEGER (1-byte range)</li>
 *   <li>{@code short} / {@link Short} → DER INTEGER (2-byte range)</li>
 *   <li>{@code int} / {@link Integer} → DER INTEGER (4-byte range)</li>
 *   <li>{@code long} / {@link Long} → DER INTEGER (8-byte range)</li>
 *   <li>{@link String} → DER UTF8String</li>
 *   <li>{@code byte[]} → DER OCTET STRING</li>
 *   <li>Any other type → {@link DerException} naming the unsupported type</li>
 * </ul>
 */
public final class ObjectCodec {

    private ObjectCodec() {
        throw new AssertionError("no instances");
    }

    // =========================================================================
    // Decode (DER bytes → object)
    // =========================================================================

    /**
     * Decodes the DER bytes of one {@code @AtomicSerial} class's private SEQUENCE
     * and drives construction of an instance via the {@code (GetArg)} constructor.
     *
     * @param <T>             the type to construct
     * @param clazz           the leaf (and only, for Phase 4.1) {@code @AtomicSerial}
     *                        class; must have a {@code public T(AtomicSerial.GetArg)}
     *                        constructor
     * @param schema          the at-marshal-time schema for {@code clazz}
     * @param payloadSequence the complete DER encoding of the class's private SEQUENCE
     *                        TLV (outer SEQUENCE tag + length + content)
     * @return the constructed instance
     * @throws DerException           if the DER encoding is malformed or a wire type
     *                                is unsupported
     * @throws InvalidObjectException if the class's {@code check(GetArg)} invariant
     *                                enforcement fails
     * @throws IOException            if construction fails with an {@link IOException}
     * @throws NullPointerException   if any argument is {@code null}
     */
    public static <T> T decode(Class<T> clazz,
                                AtomicSerialSchemaRecord schema,
                                byte[] payloadSequence)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(payloadSequence, "payloadSequence");

        // 1. Build the field store for this class's SEQUENCE
        DerFieldStore store = new DerFieldStore(schema, payloadSequence);

        // 2. Assemble the single-entry DerGetArg
        Map<Class<?>, DerFieldStore> map = new LinkedHashMap<>();
        map.put(clazz, store);
        DerGetArg arg = new DerGetArg(map);

        // 3. Find and invoke the (GetArg) constructor
        Constructor<T> ctor = findGetArgConstructor(clazz);
        try {
            return ctor.newInstance(arg);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof InvalidObjectException ioe) {
                throw ioe;
            }
            if (cause instanceof IOException ioe) {
                throw ioe;
            }
            if (cause instanceof ClassNotFoundException cnfe) {
                throw cnfe;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            DerException de = new DerException(
                    "Construction of " + clazz.getName() + " failed: " + cause);
            de.initCause(cause);
            throw de;
        } catch (IllegalAccessException | InstantiationException ex) {
            // setAccessible(true) should prevent IllegalAccessException;
            // abstract classes should not reach here
            throw new AssertionError("Unexpected reflective access failure", ex);
        }
    }

    // =========================================================================
    // Encode (object → DER bytes)
    // =========================================================================

    /**
     * Encodes one {@code @AtomicSerial} class's serialisable state to the DER
     * bytes of its private SEQUENCE.
     *
     * <p>The method invokes the class's {@code serialForm()} to discover the
     * ordered list of fields, then reads each field's value by reflecting the
     * declared field with the same name in {@code declaringClass}. The assumption
     * that the wire name equals the Java field name holds for standard
     * {@code @AtomicSerial} implementations; it is documented here explicitly
     * because it is NOT a DER constraint but a coding convention.
     *
     * <p>The encoded bytes are a complete SEQUENCE TLV: tag {@code 0x30} + length
     * + ordered field TLVs. This is exactly what {@link DerFieldStore} expects as
     * {@code payloadSequence} in its byte-array constructor.
     *
     * @param instance       the object whose state is to be encoded; must be an
     *                       instance of {@code declaringClass}
     * @param declaringClass the {@code @AtomicSerial} class whose private SEQUENCE
     *                       is being encoded; must have a {@code public static
     *                       SerialForm[] serialForm()} method
     * @param schema         the schema for {@code declaringClass} (used to obtain
     *                       the ordered field definitions and wire types)
     * @return the complete DER encoding of the private SEQUENCE TLV
     * @throws DerException         if a field type is unsupported or reflection fails
     * @throws NullPointerException if any argument is {@code null}
     */
    public static byte[] encode(Object instance,
                                 Class<?> declaringClass,
                                 AtomicSerialSchemaRecord schema)
            throws DerException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(schema, "schema");

        List<byte[]> fieldTlvs = new ArrayList<>();
        for (AtomicSerialFieldDef fieldDef : schema.fields()) {
            String wireName = fieldDef.wireName();
            String wireType = fieldDef.wireType();
            Object value = readFieldValue(instance, declaringClass, wireName);
            byte[] tlv = encodeValue(value, wireType, wireName);
            fieldTlvs.add(tlv);
        }
        return DerWriter.writeSequence(fieldTlvs);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Finds and returns the {@code public C(AtomicSerial.GetArg)} constructor,
     * making it accessible.
     */
    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> findGetArgConstructor(Class<T> clazz)
            throws DerException {
        try {
            Constructor<T> ctor = clazz.getConstructor(AtomicSerial.GetArg.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException ex) {
            throw new DerException(
                    "Class " + clazz.getName()
                    + " has no public (AtomicSerial.GetArg) constructor");
        }
    }

    /**
     * Reads the value of the named field from the object instance, searching
     * {@code declaringClass}'s declared fields. Uses {@code setAccessible(true)}.
     */
    private static Object readFieldValue(Object instance, Class<?> declaringClass,
                                          String fieldName) throws DerException {
        try {
            Field f = declaringClass.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(instance);
        } catch (NoSuchFieldException ex) {
            throw new DerException(
                    "Class " + declaringClass.getName()
                    + " has no declared field named '" + fieldName + "'");
        } catch (IllegalAccessException ex) {
            throw new DerException(
                    "Cannot access field '" + fieldName + "' on "
                    + declaringClass.getName(), ex);
        }
    }

    /**
     * Encodes a Java value to its DER TLV according to the declared wire type.
     *
     * <p>Type mapping (as per STD-006 and {@code SchemaGenerator.toWireType}):
     * <ul>
     *   <li>{@code "boolean"} / {@code "java.lang.Boolean"} → BOOLEAN</li>
     *   <li>{@code "byte"} / {@code "java.lang.Byte"} → INTEGER</li>
     *   <li>{@code "short"} / {@code "java.lang.Short"} → INTEGER</li>
     *   <li>{@code "int"} / {@code "java.lang.Integer"} → INTEGER</li>
     *   <li>{@code "long"} / {@code "java.lang.Long"} → INTEGER</li>
     *   <li>{@code "java.lang.String"} → UTF8String</li>
     *   <li>{@code "byte[]"} / {@code "[B"} → OCTET STRING</li>
     * </ul>
     */
    private static byte[] encodeValue(Object value, String wireType,
                                       String fieldName) throws DerException {
        return switch (wireType) {
            case "boolean", "java.lang.Boolean" -> {
                if (!(value instanceof Boolean b)) {
                    throw new DerException("Expected Boolean for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeBoolean(b);
            }
            case "byte", "java.lang.Byte" -> {
                if (!(value instanceof Byte b)) {
                    throw new DerException("Expected Byte for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeInteger(BigInteger.valueOf(b));
            }
            case "short", "java.lang.Short" -> {
                if (!(value instanceof Short s)) {
                    throw new DerException("Expected Short for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeInteger(BigInteger.valueOf(s));
            }
            case "int", "java.lang.Integer" -> {
                if (!(value instanceof Integer iv)) {
                    throw new DerException("Expected Integer for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeInteger(BigInteger.valueOf(iv));
            }
            case "long", "java.lang.Long" -> {
                if (!(value instanceof Long l)) {
                    throw new DerException("Expected Long for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeInteger(BigInteger.valueOf(l));
            }
            case "java.lang.String" -> {
                if (!(value instanceof String s)) {
                    throw new DerException("Expected String for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeUtf8String(s);
            }
            case "byte[]", "[B" -> {
                if (!(value instanceof byte[] b)) {
                    throw new DerException("Expected byte[] for field '" + fieldName
                            + "' (wireType " + wireType + ") but got "
                            + (value == null ? "null" : value.getClass().getName()));
                }
                yield DerWriter.writeOctetString(b);
            }
            default -> throw new DerException(
                    "ObjectCodec: unsupported wire type '" + wireType
                    + "' for field '" + fieldName + "'"
                    + " (char/float/double deferred per §7.6)");
        };
    }
}
