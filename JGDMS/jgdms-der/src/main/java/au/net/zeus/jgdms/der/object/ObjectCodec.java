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
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DER object encoder and decoder for single {@code @AtomicSerial} classes
 * (Phase 4.1 / 4.2 — single class, one private SEQUENCE) and for
 * {@code @AtomicSerial} class hierarchies (Phase 4.3 — one private SEQUENCE
 * per class, superclass-first on wire).
 *
 * <h2>Single-class encoding (object → DER)</h2>
 * <p>
 * {@link #encode(Object, Class, AtomicSerialSchemaRecord)} encodes an object's
 * state to the DER bytes of the class's private SEQUENCE. The field values are
 * read from the object's declared Java fields via reflection
 * ({@link Field#setAccessible(boolean)}). The wire name comes from the schema's
 * field definitions; the assumption (safe for standard {@code @AtomicSerial}
 * classes) is that the wire name equals the Java field name.
 *
 * <h2>Single-class decoding (DER → object)</h2>
 * <p>
 * {@link #decode(Class, AtomicSerialSchemaRecord, byte[])} decodes the DER bytes
 * of one class's private SEQUENCE, builds a {@link DerFieldStore}, assembles a
 * single-entry {@link DerGetArg}, and drives construction by reflectively invoking
 * the {@code public C(AtomicSerial.GetArg)} constructor.
 *
 * <h2>Hierarchy encoding (Phase 4.3)</h2>
 * <p>
 * {@link #encodeHierarchy(Object, SchemaChain.Result)} emits one private SEQUENCE
 * per {@code @AtomicSerial} class, assembled in a top-level wrapper SEQUENCE in
 * <b>superclass-first (root-first, leaf-last)</b> order. Each class's SEQUENCE
 * contains only the fields declared by that class (via its own {@code serialForm()});
 * no class can write another class's fields. Wire structure:
 * <pre>
 * SEQUENCE {          -- outer hierarchy SEQUENCE
 *   SEQUENCE { ... }  -- root-class private SEQUENCE (first on wire)
 *   SEQUENCE { ... }  -- mid-class private SEQUENCE
 *   SEQUENCE { ... }  -- leaf-class private SEQUENCE (last on wire)
 * }
 * </pre>
 *
 * <h2>Hierarchy decoding (Phase 4.3)</h2>
 * <p>
 * {@link #decodeHierarchy(Class, SchemaChain.Result, byte[])} reads each per-class
 * SEQUENCE from the outer wrapper in superclass-first order, builds one
 * {@link DerFieldStore} per class, populates a multi-entry {@link DerGetArg}
 * (insertion order: superclass-first), then reflectively invokes the LEAF class's
 * {@code (GetArg)} constructor. The leaf constructor chains up via
 * {@code super(check(arg))}, and StackWalker dispatch in {@link DerGetArg} ensures
 * each level reads exclusively from its own {@link DerFieldStore}.
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
    // Hierarchy encode (Phase 4.3)
    // =========================================================================

    /**
     * Encodes an object from a {@code @AtomicSerial} hierarchy to DER bytes.
     *
     * <p>The returned bytes are a complete SEQUENCE TLV wrapping one private SEQUENCE
     * per {@code @AtomicSerial} class in the hierarchy, in <b>superclass-first
     * (root-first, leaf-last)</b> order. Each per-class SEQUENCE contains only the
     * fields declared by that class via its own {@code serialForm()}.
     *
     * <p>The {@code chain} must have been produced by
     * {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain(Class)} for the
     * leaf class. The chain order from {@link SchemaChain.Result#chain()} is
     * <em>leaf-first</em>; this method reverses it to <em>superclass-first</em> before
     * encoding so that the wire order matches the DerGetArg insertion order expected by
     * {@link #decodeHierarchy}.
     *
     * @param instance the object to encode; must be an instance of the leaf class
     * @param chain    the linked schema chain for the hierarchy (leaf-first from
     *                 {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain})
     * @return the complete DER encoding: outer SEQUENCE { per-class SEQUENCE ... }
     * @throws DerException         if a field type is unsupported or reflection fails
     * @throws NullPointerException if any argument is {@code null}
     */
    public static byte[] encodeHierarchy(Object instance,
                                          SchemaChain.Result chain)
            throws DerException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(chain, "chain");

        // chain.chain() is leaf-first; we need superclass-first (root-first) for wire order.
        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        List<AtomicSerialSchemaRecord> rootFirst = new ArrayList<>(leafFirst);
        Collections.reverse(rootFirst);

        // For each class in root-first order, load the class and encode its SEQUENCE.
        List<byte[]> perClassSequences = new ArrayList<>(rootFirst.size());
        for (AtomicSerialSchemaRecord schemaRecord : rootFirst) {
            Class<?> cls = loadClass(schemaRecord.className());
            byte[] classSeq = encode(instance, cls, schemaRecord);
            perClassSequences.add(classSeq);
        }

        // Wrap all per-class SEQUENCEs in an outer SEQUENCE
        return DerWriter.writeSequence(perClassSequences);
    }

    // =========================================================================
    // Hierarchy decode (Phase 4.3)
    // =========================================================================

    /**
     * Decodes DER bytes produced by {@link #encodeHierarchy} and constructs an
     * instance of {@code leafClass} by invoking its {@code (AtomicSerial.GetArg)}
     * constructor.
     *
     * <p>The method reads each per-class SEQUENCE from the outer wrapper in
     * <b>superclass-first</b> order, builds a {@link DerFieldStore} per class, and
     * populates a {@link DerGetArg} with all stores (insertion order: superclass-first,
     * leaf-last — matching {@code DerGetArg}'s documented contract and
     * {@code serialClasses()} order).
     *
     * <p>The LEAF class's {@code (GetArg)} constructor is then invoked with the
     * populated {@link DerGetArg}. It chains up via {@code super(check(arg))}, and
     * each level in the chain reads only its own private namespace through
     * StackWalker dispatch.
     *
     * <p>The {@code chain} must have been produced by
     * {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain} for
     * {@code leafClass}.
     *
     * @param <T>       the leaf class type
     * @param leafClass the leaf {@code @AtomicSerial} class to construct
     * @param chain     the linked schema chain (leaf-first from
     *                  {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain})
     * @param hierarchyPayload the DER bytes produced by {@link #encodeHierarchy}
     * @return the constructed leaf instance
     * @throws DerException           if the DER encoding is malformed
     * @throws InvalidObjectException if any class's {@code check(GetArg)} fails
     * @throws IOException            if construction fails with IOException
     * @throws NullPointerException   if any argument is {@code null}
     */
    public static <T> T decodeHierarchy(Class<T> leafClass,
                                         SchemaChain.Result chain,
                                         byte[] hierarchyPayload)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(leafClass, "leafClass");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(hierarchyPayload, "hierarchyPayload");

        // chain.chain() is leaf-first; reverse to get superclass-first for wire order
        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        List<AtomicSerialSchemaRecord> rootFirst = new ArrayList<>(leafFirst);
        Collections.reverse(rootFirst);

        // Read the outer SEQUENCE; it contains one child SEQUENCE per class (root-first)
        DerReader outer = new DerReader(hierarchyPayload);
        DerReader outerSeq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException("ObjectCodec.decodeHierarchy: trailing bytes after outer SEQUENCE");
        }

        // Build a DerFieldStore for each class, inserting superclass-first into the map
        Map<Class<?>, DerFieldStore> storeMap = new LinkedHashMap<>();
        for (AtomicSerialSchemaRecord schemaRecord : rootFirst) {
            Class<?> cls = loadClass(schemaRecord.className());
            DerFieldStore store = new DerFieldStore(schemaRecord, outerSeq);
            storeMap.put(cls, store);
        }
        if (outerSeq.hasMore()) {
            throw new DerException("ObjectCodec.decodeHierarchy: trailing bytes in outer SEQUENCE "
                    + "(more SEQUENCEs than schema records)");
        }

        // Assemble the multi-entry DerGetArg (superclass-first insertion order)
        DerGetArg arg = new DerGetArg(storeMap);

        // Invoke the LEAF class's (GetArg) constructor — it chains up via super(check(arg))
        Constructor<T> ctor = findGetArgConstructor(leafClass);
        try {
            return ctor.newInstance(arg);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof InvalidObjectException ioe) throw ioe;
            if (cause instanceof IOException ioe) throw ioe;
            if (cause instanceof ClassNotFoundException cnfe) throw cnfe;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            DerException de = new DerException(
                    "Construction of " + leafClass.getName() + " failed: " + cause);
            de.initCause(cause);
            throw de;
        } catch (IllegalAccessException | InstantiationException ex) {
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

    /**
     * Loads a class by name using the thread context class loader (falling back to
     * the system class loader). Used by hierarchy encode/decode to resolve class
     * names from schema records.
     *
     * @param className the fully-qualified class name
     * @return the loaded class
     * @throws DerException if the class cannot be found
     */
    private static Class<?> loadClass(String className) throws DerException {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = ClassLoader.getSystemClassLoader();
            return Class.forName(className, false, cl);
        } catch (ClassNotFoundException ex) {
            throw new DerException(
                    "ObjectCodec: cannot load class '" + className + "'", ex);
        }
    }
}
