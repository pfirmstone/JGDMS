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
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.AtomicSerial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DER object encoder and decoder for single {@code @AtomicSerial} classes
 * (Phase 4.1 / 4.2 -- single class, one private SEQUENCE), for
 * {@code @AtomicSerial} class hierarchies (Phase 4.3 -- one private SEQUENCE
 * per class, superclass-first on wire), and for hierarchies containing
 * non-{@code @AtomicSerial} classes (Phase 4.4 -- S3.10 wire-visibility rules).
 *
 * <h2>Single-class encoding (object -> DER)</h2>
 * <p>
 * {@link #encode(Object, Class, AtomicSerialSchemaRecord)} encodes an object's
 * state to the DER bytes of the class's private SEQUENCE. The field values are
 * read from the object's declared Java fields via reflection
 * ({@link Field#setAccessible(boolean)}). The wire name comes from the schema's
 * field definitions; the assumption (safe for standard {@code @AtomicSerial}
 * classes) is that the wire name equals the Java field name.
 *
 * <h2>Single-class decoding (DER -> object)</h2>
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
 * <h2>Type mapping for encoding (Class -> wireType)</h2>
 * <ul>
 *   <li>{@code boolean} / {@link Boolean} -> DER BOOLEAN</li>
 *   <li>{@code byte} / {@link Byte} -> DER INTEGER (1-byte range)</li>
 *   <li>{@code short} / {@link Short} -> DER INTEGER (2-byte range)</li>
 *   <li>{@code int} / {@link Integer} -> DER INTEGER (4-byte range)</li>
 *   <li>{@code long} / {@link Long} -> DER INTEGER (8-byte range)</li>
 *   <li>{@link String} -> DER UTF8String</li>
 *   <li>{@code byte[]} -> DER OCTET STRING</li>
 *   <li>Any other type -> {@link DerException} naming the unsupported type</li>
 * </ul>
 */
public final class ObjectCodec {

    /**
     * Maximum nesting depth for nested {@code @AtomicSerial} object fields
     * (STD-008 sec.16.2 depth-bound DoS guard). Encode and decode both
     * throw {@link DerException} when this limit is exceeded.
     */
    public static final int MAX_NESTING = 16;

    private ObjectCodec() {
        throw new AssertionError("no instances");
    }

    // =========================================================================
    // Decode (DER bytes -> object)
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
        return encodeHierarchy(instance, chain, 0);
    }

    /**
     * Depth-aware overload used internally for nested object encoding.
     * The {@code depth} parameter counts nested {@code @AtomicSerial} object
     * fields; it is incremented each time {@link #encodeNested} recurses.
     * Throws {@link DerException} when {@code depth} exceeds {@link #MAX_NESTING}.
     */
    static byte[] encodeHierarchy(Object instance,
                                   SchemaChain.Result chain,
                                   int depth)
            throws DerException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(chain, "chain");
        if (depth > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec: nesting depth " + depth
                    + " exceeds MAX_NESTING (" + MAX_NESTING + ")");
        }

        // chain.chain() is leaf-first; we need superclass-first (root-first) for wire order.
        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        List<AtomicSerialSchemaRecord> rootFirst = new ArrayList<>(leafFirst);
        Collections.reverse(rootFirst);

        // For each class in root-first order, load the class and encode its SEQUENCE.
        List<byte[]> perClassSequences = new ArrayList<>(rootFirst.size());
        for (AtomicSerialSchemaRecord schemaRecord : rootFirst) {
            Class<?> cls = loadClass(schemaRecord.className());
            byte[] classSeq = encode(instance, cls, schemaRecord, depth);
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
     * instance of the lowest {@code @AtomicSerial} class in the chain, which is
     * assignability-checked against {@code expectedSupertype}.
     *
     * <h2>Phase 4.4 -- non-{@code @AtomicSerial} class handling (S3.10)</h2>
     *
     * <p>The {@code chain} is produced by
     * {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain}, which
     * walks the hierarchy skipping any class not annotated {@code @AtomicSerial}.
     * Consequently the chain's first record ({@code chain.chain().get(0)}) names
     * the <em>lowest {@code @AtomicSerial} class</em> -- which may differ from the
     * {@code expectedSupertype} argument when a non-{@code @AtomicSerial} subclass
     * is passed in.
     *
     * <ul>
     *   <li><b>Non-{@code @AtomicSerial} subclass dropped to its superclass</b>
     *       (e.g. {@code Bar extends Foo}, only {@code Foo} is {@code @AtomicSerial}):
     *       {@code generateChain(Bar.class)} yields a chain whose leaf record is {@code Foo}.
     *       This method constructs a {@code Foo}, not a {@code Bar}. The caller passes
     *       {@code expectedSupertype = Bar.class} (or any supertype of {@code Foo}) -- it
     *       merely constrains what the caller may assign the result to. The decoded
     *       object's runtime class is exactly {@code Foo}.</li>
     *   <li><b>All-{@code @AtomicSerial} hierarchy (Phase 4.3)</b>:
     *       the chain leaf IS the passed class, so behaviour is unchanged.</li>
     * </ul>
     *
     * <p>The method reads each per-class SEQUENCE from the outer wrapper in
     * <b>superclass-first</b> order, builds a {@link DerFieldStore} per class, and
     * populates a {@link DerGetArg} with all stores (insertion order: superclass-first,
     * leaf-last -- matching {@code DerGetArg}'s documented contract and
     * {@code serialClasses()} order).
     *
     * <p>The <em>construct class</em>'s {@code (GetArg)} constructor is then invoked
     * with the populated {@link DerGetArg}. It chains up via {@code super(check(arg))},
     * and each level in the chain reads only its own private namespace through
     * StackWalker dispatch.
     *
     * @param <T>              the expected return supertype (may be broader than the
     *                         actual construct class; the construct class must be
     *                         assignable to this type)
     * @param expectedSupertype the expected supertype of the decoded result; used for
     *                         the assignability check only -- the actual class constructed
     *                         is the chain's leaf {@code @AtomicSerial} record
     * @param chain            the linked schema chain (leaf-first from
     *                         {@link au.net.zeus.jgdms.der.schema.SchemaGenerator#generateChain})
     * @param hierarchyPayload the DER bytes produced by {@link #encodeHierarchy}
     * @return the constructed instance; its runtime class equals the chain's leaf
     *         {@code @AtomicSerial} class, which is assignable to {@code expectedSupertype}
     * @throws DerException           if the DER encoding is malformed, or if the
     *                                chain's construct class is not assignable to
     *                                {@code expectedSupertype}
     * @throws InvalidObjectException if any class's {@code check(GetArg)} fails
     * @throws IOException            if construction fails with IOException
     * @throws NullPointerException   if any argument is {@code null}
     */
    public static <T> T decodeHierarchy(Class<T> expectedSupertype,
                                         SchemaChain.Result chain,
                                         byte[] hierarchyPayload)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(expectedSupertype, "expectedSupertype");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(hierarchyPayload, "hierarchyPayload");

        // chain.chain() is leaf-first; the first entry is the lowest @AtomicSerial class.
        // This may differ from expectedSupertype when a non-@AtomicSerial subclass was
        // passed to generateChain (S3.10, first rule: non-@AtomicSerial subclass is dropped).
        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        String constructClassName = leafFirst.get(0).className();
        Class<?> constructClass = loadClass(constructClassName);

        // Assignability check: the constructed type must be a subtype of expectedSupertype.
        // When Bar extends Foo (Bar plain, Foo @AtomicSerial), constructClass = Foo,
        // expectedSupertype = Bar.class -> Foo IS a supertype of Bar, but Bar is NOT a
        // supertype of Foo. The correct check is: constructClass is assignable TO
        // expectedSupertype, meaning expectedSupertype.isAssignableFrom(constructClass).
        if (!expectedSupertype.isAssignableFrom(constructClass)) {
            throw new DerException(
                    "ObjectCodec.decodeHierarchy: the chain's construct class '"
                    + constructClassName + "' is not assignable to the expected supertype '"
                    + expectedSupertype.getName() + "'. "
                    + "This chain was not generated for a class related to "
                    + expectedSupertype.getName() + ".");
        }

        // Reverse for superclass-first wire order
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

        // S3.9 / S11.6 namespace-isolation fix: every @AtomicSerial class in the
        // construct class's hierarchy whose (GetArg) constructor will run MUST have a
        // store entry, so DerGetArg.callerClass() resolves EACH level to its OWN
        // namespace. A class present in the receiver's hierarchy but ABSENT from the
        // embedded data (e.g. an @AtomicSerial class inserted AFTER the data was
        // written -- S11.6) gets an EMPTY store, so its arg.get(name, default) calls
        // return defaults. Without this, callerClass() would skip the absent class's
        // frame and resolve to the nearest neighbouring class's store -- leaking that
        // neighbour's namespace (proved by NamespaceLeakRegressionTest).
        for (Class<?> c = constructClass; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(AtomicSerial.class) && !storeMap.containsKey(c)) {
                storeMap.put(c, emptyFieldStore(c));
            }
        }

        // Assemble the multi-entry DerGetArg (superclass-first insertion order)
        DerGetArg arg = new DerGetArg(storeMap);

        // Invoke the CONSTRUCT CLASS's (GetArg) constructor -- it chains up via super(check(arg)).
        // For all-@AtomicSerial hierarchies (Phase 4.3) this is the same as the old leafClass.
        // For non-@AtomicSerial subclass dropped to its @AtomicSerial superclass, this is the
        // @AtomicSerial superclass (e.g. Foo, not Bar).
        @SuppressWarnings("unchecked")
        Constructor<? extends T> ctor = (Constructor<? extends T>)
                findGetArgConstructor(constructClass);
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
                    "Construction of " + constructClass.getName() + " failed: " + cause);
            de.initCause(cause);
            throw de;
        } catch (IllegalAccessException | InstantiationException ex) {
            throw new AssertionError("Unexpected reflective access failure", ex);
        }
    }

    // =========================================================================
    // Encode (object -> DER bytes)
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
        return encode(instance, declaringClass, schema, 0);
    }

    /** Depth-aware encode -- called from the depth-aware encodeHierarchy. */
    private static byte[] encode(Object instance,
                                  Class<?> declaringClass,
                                  AtomicSerialSchemaRecord schema,
                                  int depth)
            throws DerException {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(schema, "schema");

        List<byte[]> fieldTlvs = new ArrayList<>();
        for (AtomicSerialFieldDef fieldDef : schema.fields()) {
            String wireName = fieldDef.wireName();
            String wireType = fieldDef.wireType();
            Object value = readFieldValue(instance, declaringClass, wireName);
            byte[] tlv = encodeValue(value, wireType, wireName, depth);
            fieldTlvs.add(tlv);
        }
        return DerWriter.writeSequence(fieldTlvs);
    }

    // =========================================================================
    // S3.11 Data Independence: decode to named field map WITHOUT loading classes
    // =========================================================================

    /**
     * Decodes a DER hierarchy payload into a named field map given ONLY the DER
     * bytes and the {@link SchemaChain.Result} -- <b>without loading any class from
     * the originating codebase</b>. This satisfies the S3.11 normative requirement
     * for data independence.
     *
     * <p>The returned map is keyed by class name (as declared in each
     * {@link AtomicSerialSchemaRecord}). Each value is an ordered map from field
     * name to decoded Java value, corresponding exactly to the state that
     * {@link DerGetArg} would hold during construction: all fields present in the
     * schema, each bound to its decoded value. Fields absent from the payload (case
     * (b): schema newer than data) appear as absent in
     * {@link DerFieldStore#presentFields()} and are not included in the inner map.
     *
     * <p>No class is loaded; no constructor is invoked; no {@code check(GetArg)} is
     * run. The decode is purely structural: schema -> DER -> named values. This is
     * the S3.11 claim in executable form.
     *
     * <pre>
     * SEQUENCE {          -- outer hierarchy SEQUENCE (produced by encodeHierarchy)
     *   SEQUENCE { ... }  -- root-class private SEQUENCE (first on wire)
     *   ...
     *   SEQUENCE { ... }  -- leaf-class private SEQUENCE (last on wire)
     * }
     * </pre>
     *
     * @param chain            the schema chain for the hierarchy (leaf-first from
     *                         {@link SchemaGenerator#generateChain}); class names
     *                         must match the names used at encode time
     * @param hierarchyPayload the DER bytes produced by {@link #encodeHierarchy}
     * @return an ordered map: className -> (fieldName -> decoded value), in
     *         superclass-first hierarchy order; each inner map preserves schema
     *         field order
     * @throws DerException         if the DER encoding is malformed or a wire type
     *                              is unsupported
     * @throws NullPointerException if any argument is {@code null}
     */
    public static java.util.LinkedHashMap<String, java.util.Map<String, Object>>
            decodeToFieldMap(SchemaChain.Result chain,
                             byte[] hierarchyPayload) throws DerException {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(hierarchyPayload, "hierarchyPayload");

        // chain.chain() is leaf-first; the wire is superclass-first (root-first).
        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        List<AtomicSerialSchemaRecord> rootFirst = new ArrayList<>(leafFirst);
        Collections.reverse(rootFirst);

        // Read the outer SEQUENCE; it contains one child SEQUENCE per class (root-first).
        DerReader outer = new DerReader(hierarchyPayload);
        DerReader outerSeq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException("decodeToFieldMap: trailing bytes after outer SEQUENCE");
        }

        // Result map: class name -> field name -> value, in superclass-first order.
        java.util.LinkedHashMap<String, java.util.Map<String, Object>> result =
                new java.util.LinkedHashMap<>(rootFirst.size() * 2);

        for (AtomicSerialSchemaRecord schemaRecord : rootFirst) {
            // Build a DerFieldStore for this class's SEQUENCE from the outer reader.
            // This advances outerSeq past the per-class SEQUENCE TLV.
            DerFieldStore store = new DerFieldStore(schemaRecord, outerSeq);
            // Extract the present fields as an ordered map.
            result.put(schemaRecord.className(), store.presentFields());
        }

        if (outerSeq.hasMore()) {
            throw new DerException("decodeToFieldMap: trailing bytes in outer SEQUENCE "
                    + "(more SEQUENCEs than schema records)");
        }

        return result;
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
     * Reads the value of the named field from the object instance.
     *
     * <p>The search starts at {@code declaringClass} and walks up the superclass chain
     * to {@code Object} until the field is found. This is required for the Phase 4.4
     * non-{@code @AtomicSerial} superclass case (S3.10, second rule): when an
     * {@code @AtomicSerial} class's {@code serialForm()} includes a field that is
     * physically declared in a non-{@code @AtomicSerial} superclass, the field will
     * not be found in {@code declaringClass}'s own declared fields but IS accessible
     * on the instance (because the instance is a subtype of that superclass).
     *
     * <p>Example: {@code Sub extends PlainSuper}, {@code Sub.serialForm()} declares
     * {@code "legacyName"}, but {@code legacyName} is a field of {@code PlainSuper}.
     * The walk finds it in {@code PlainSuper}.
     */
    private static Object readFieldValue(Object instance, Class<?> declaringClass,
                                          String fieldName) throws DerException {
        // Walk from declaringClass up to (but not including) Object looking for the field.
        Class<?> cls = declaringClass;
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(instance);
            } catch (NoSuchFieldException ex) {
                // Not in this class -- continue to superclass
                cls = cls.getSuperclass();
            } catch (IllegalAccessException ex) {
                throw new DerException(
                        "Cannot access field '" + fieldName + "' on "
                        + cls.getName(), ex);
            }
        }
        throw new DerException(
                "Class " + declaringClass.getName()
                + " (or any of its superclasses) has no declared field named '"
                + fieldName + "'");
    }

    /**
     * Encodes a Java value to its DER TLV according to the declared wire type.
     *
     * <p>Type mapping (as per STD-006 and {@code SchemaGenerator.toWireType}):
     * <ul>
     *   <li>{@code "boolean"} / {@code "java.lang.Boolean"} -> BOOLEAN</li>
     *   <li>{@code "byte"} / {@code "java.lang.Byte"} -> INTEGER</li>
     *   <li>{@code "short"} / {@code "java.lang.Short"} -> INTEGER</li>
     *   <li>{@code "int"} / {@code "java.lang.Integer"} -> INTEGER</li>
     *   <li>{@code "long"} / {@code "java.lang.Long"} -> INTEGER</li>
     *   <li>{@code "java.lang.String"} -> UTF8String</li>
     *   <li>{@code "byte[]"} / {@code "[B"} -> OCTET STRING</li>
     *   <li>{@code "@AtomicSerial"} -> SEQUENCE{schemaBytes, payloadBytes} or NULL</li>
     * </ul>
     */
    private static byte[] encodeValue(Object value, String wireType,
                                       String fieldName) throws DerException {
        return encodeValue(value, wireType, fieldName, 0);
    }

    private static byte[] encodeValue(Object value, String wireType,
                                       String fieldName, int depth) throws DerException {
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
            // Nested @AtomicSerial object field (STD-008 sec.16)
            case "@AtomicSerial" -> encodeNested(value, fieldName, depth);
            default -> throw new DerException(
                    "ObjectCodec: unsupported wire type '" + wireType
                    + "' for field '" + fieldName + "'"
                    + " (char/float/double deferred per S7.6)");
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

    /**
     * Builds an EMPTY {@link DerFieldStore} for {@code cls} -- a store over a schema
     * with no fields and an empty SEQUENCE payload. All {@code get(name, default)}
     * calls against it return the default; {@code defaulted(name)} is always true.
     *
     * <p>Used by {@link #decodeHierarchy} to register an absent class's namespace
     * (e.g. an @AtomicSerial class inserted after the data was written, S11.6) so
     * that {@code DerGetArg} dispatch resolves that class to its own (empty) store
     * rather than leaking a neighbour's namespace (S3.9).
     */
    private static DerFieldStore emptyFieldStore(Class<?> cls) throws DerException {
        AtomicSerialSchemaRecord emptySchema =
                new AtomicSerialSchemaRecord(cls.getName(), (byte[]) null, List.of());
        return new DerFieldStore(emptySchema, DerWriter.writeSequence(List.<byte[]>of()));
    }

    // =========================================================================
    // Nested @AtomicSerial field encode/decode (STD-008 sec.16)
    // =========================================================================

    /**
     * Wire format for a nested {@code @AtomicSerial} field:
     * <pre>
     * SEQUENCE {
     *   OCTET STRING -- schemaChainBytes (leaf-first concatenated AtomicSerialSchemaRecord DERs)
     *   OCTET STRING -- payloadBytes (ObjectCodec.encodeHierarchy output)
     * }
     * </pre>
     * A null value encodes as DER NULL (0x05 0x00).
     *
     * <p>The schema chain is encoded WITHOUT using {@code der.marshal} (which would
     * create a package cycle), by directly concatenating each record's
     * {@link AtomicSerialSchemaRecord#encode()} in leaf-first order -- the same
     * logic as {@code MarshalledInstanceRecord.encodeChainBytes}.
     *
     * @param value     the nested object value (may be null)
     * @param fieldName used in error messages only
     * @param depth     current nesting depth; incremented before the recursive call
     * @return the TLV bytes for this nested field
     */
    private static byte[] encodeNested(Object value, String fieldName, int depth)
            throws DerException {
        // null -> DER NULL
        if (value == null) {
            return new byte[]{0x05, 0x00};
        }

        // Depth check BEFORE recursing (depth + 1 will be the child's depth)
        if (depth + 1 > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec: nesting depth " + (depth + 1)
                    + " exceeds MAX_NESTING (" + MAX_NESTING
                    + ") for nested field '" + fieldName + "'");
        }

        Class<?> cls = value.getClass();
        if (!cls.isAnnotationPresent(AtomicSerial.class)) {
            throw new DerException(
                    "ObjectCodec: nested field '" + fieldName
                    + "' has wireType @AtomicSerial but runtime type "
                    + cls.getName() + " is not annotated @AtomicSerial");
        }

        // Generate chain and encode payload
        SchemaChain.Result chain = SchemaGenerator.generateChain(cls);
        byte[] payloadBytes = encodeHierarchy(value, chain, depth + 1);

        // Encode the schema chain: leaf-first concatenated AtomicSerialSchemaRecord DERs
        byte[] schemaChainBytes = encodeSchemaChainBytes(chain.chain());

        // Wrap as SEQUENCE { OCTET STRING(schemaChainBytes), OCTET STRING(payloadBytes) }
        List<byte[]> children = new ArrayList<>(2);
        children.add(DerWriter.writeOctetString(schemaChainBytes));
        children.add(DerWriter.writeOctetString(payloadBytes));
        return DerWriter.writeSequence(children);
    }

    /**
     * Decodes a nested {@code @AtomicSerial} field record as produced by
     * {@link #encodeNested}.
     *
     * <p>Called from {@link DerGetArg#get(String, Object)} when the field store
     * reports the field as nested (wireType {@code "@AtomicSerial"}).
     *
     * @param nestedRecordBytes the raw bytes of the nested record TLV (SEQUENCE or NULL)
     * @param depth             current nesting depth; used for the DoS guard
     * @return the decoded object, or {@code null} for a DER NULL encoding
     * @throws DerException if the encoding is malformed or depth exceeded
     * @throws IOException  if construction fails
     */
    public static Object decodeNested(byte[] nestedRecordBytes, int depth)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(nestedRecordBytes, "nestedRecordBytes");
        if (depth > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec.decodeNested: nesting depth " + depth
                    + " exceeds MAX_NESTING (" + MAX_NESTING + ")");
        }
        if (nestedRecordBytes.length == 0) {
            throw new DerException("ObjectCodec.decodeNested: empty bytes");
        }
        // DER NULL (0x05 0x00) -> null
        if (nestedRecordBytes[0] == 0x05) {
            if (nestedRecordBytes.length != 2 || nestedRecordBytes[1] != 0x00) {
                throw new DerException(
                        "ObjectCodec.decodeNested: malformed NULL TLV (expected 05 00)");
            }
            return null;
        }
        // SEQUENCE { OCTET STRING(schemaChainBytes), OCTET STRING(payloadBytes) }
        DerReader outer = new DerReader(nestedRecordBytes);
        DerReader seq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException(
                    "ObjectCodec.decodeNested: trailing bytes after nested SEQUENCE");
        }
        byte[] schemaChainBytes = seq.readOctetString();
        byte[] payloadBytes = seq.readOctetString();
        if (seq.hasMore()) {
            throw new DerException(
                    "ObjectCodec.decodeNested: unexpected trailing bytes in nested SEQUENCE");
        }

        // Decode schema chain (same format as MarshalledInstanceRecord.decodeSchemaChain)
        List<AtomicSerialSchemaRecord> records = new ArrayList<>();
        DerReader schemaReader = new DerReader(schemaChainBytes);
        while (schemaReader.hasMore()) {
            records.add(AtomicSerialSchemaRecord.decode(schemaReader));
        }
        if (records.isEmpty()) {
            throw new DerException("ObjectCodec.decodeNested: empty schema chain");
        }
        byte[] leafDigest = records.get(0).schemaDigest();
        SchemaChain.Result chain = new SchemaChain.Result(records, leafDigest);

        // The declared field type is Object (checked by caller via cast); the chain drives
        // the actual runtime class. decodeHierarchy does assignability checking.
        return decodeHierarchy(Object.class, chain, payloadBytes, depth + 1);
    }

    /**
     * Depth-aware variant of {@link #decodeHierarchy(Class, SchemaChain.Result, byte[])}
     * used for nested decode. The {@code expectedSupertype} is {@code Object.class};
     * the outer constructor's field assignment will perform the actual cast.
     */
    private static <T> T decodeHierarchy(Class<T> expectedSupertype,
                                          SchemaChain.Result chain,
                                          byte[] hierarchyPayload,
                                          int depth)
            throws DerException, IOException, ClassNotFoundException {
        Objects.requireNonNull(expectedSupertype, "expectedSupertype");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(hierarchyPayload, "hierarchyPayload");
        if (depth > MAX_NESTING) {
            throw new DerException(
                    "ObjectCodec.decodeHierarchy: nesting depth " + depth
                    + " exceeds MAX_NESTING (" + MAX_NESTING + ")");
        }

        List<AtomicSerialSchemaRecord> leafFirst = chain.chain();
        String constructClassName = leafFirst.get(0).className();
        Class<?> constructClass = loadClass(constructClassName);

        if (!expectedSupertype.isAssignableFrom(constructClass)) {
            throw new DerException(
                    "ObjectCodec.decodeHierarchy: the chain's construct class '"
                    + constructClassName + "' is not assignable to the expected supertype '"
                    + expectedSupertype.getName() + "'. "
                    + "This chain was not generated for a class related to "
                    + expectedSupertype.getName() + ".");
        }

        List<AtomicSerialSchemaRecord> rootFirst = new ArrayList<>(leafFirst);
        Collections.reverse(rootFirst);

        DerReader outer = new DerReader(hierarchyPayload);
        DerReader outerSeq = outer.readSequence();
        if (outer.hasMore()) {
            throw new DerException("ObjectCodec.decodeHierarchy: trailing bytes after outer SEQUENCE");
        }

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

        for (Class<?> c = constructClass; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(AtomicSerial.class) && !storeMap.containsKey(c)) {
                storeMap.put(c, emptyFieldStore(c));
            }
        }

        DerGetArg arg = new DerGetArg(storeMap, depth);

        @SuppressWarnings("unchecked")
        Constructor<? extends T> ctor = (Constructor<? extends T>)
                findGetArgConstructor(constructClass);
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
                    "Construction of " + constructClass.getName() + " failed: " + cause);
            de.initCause(cause);
            throw de;
        } catch (IllegalAccessException | InstantiationException ex) {
            throw new AssertionError("Unexpected reflective access failure", ex);
        }
    }

    /**
     * Encodes a schema chain as a concatenation of each record's DER SEQUENCE bytes,
     * in leaf-first order (same as {@code MarshalledInstanceRecord.encodeChainBytes},
     * but kept in {@code der.object} to avoid a {@code der.marshal} cycle).
     */
    private static byte[] encodeSchemaChainBytes(List<AtomicSerialSchemaRecord> chain) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord record : chain) {
            byte[] encoded = record.encode();
            buf.write(encoded, 0, encoded.length);
        }
        return buf.toByteArray();
    }
}
