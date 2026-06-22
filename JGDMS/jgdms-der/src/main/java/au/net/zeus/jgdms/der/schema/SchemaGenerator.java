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

package au.net.zeus.jgdms.der.schema;

import au.net.zeus.jgdms.der.DerException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.MarshalDelegate;
import org.apache.river.api.io.MarshalDelegates;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Collections;

/**
 * Generates an {@link AtomicSerialSchemaRecord} from a single Object-rooted
 * {@code @AtomicSerial} class (Phase 4.2 -- no hierarchy, no parent hash) or a
 * complete linked chain from a leaf class through all {@code @AtomicSerial}
 * ancestors (Phase 4.3 -- see {@link #generateChain(Class)}).
 *
 * <h2>Usage -- single class</h2>
 * <pre>{@code
 * AtomicSerialSchemaRecord record = SchemaGenerator.generate(MyClass.class);
 * }</pre>
 *
 * <h2>Usage -- hierarchy chain (Phase 4.3)</h2>
 * <pre>{@code
 * SchemaChain.Result result = SchemaGenerator.generateChain(LeafClass.class);
 * // result.chain() is leaf-first, root-last; each record's parentSchemaHash
 * // points to its parent's digest.
 * }</pre>
 *
 * <h2>Type mapping (Java type -> wireType string)</h2>
 * <table border="1">
 *   <caption>Supported type mappings</caption>
 *   <tr><th>Java type</th><th>wireType string</th><th>DER encoding</th></tr>
 *   <tr><td>{@code boolean} / {@link Boolean}</td><td>{@code "boolean"}</td><td>BOOLEAN</td></tr>
 *   <tr><td>{@code byte} / {@link Byte}</td><td>{@code "byte"}</td><td>INTEGER</td></tr>
 *   <tr><td>{@code short} / {@link Short}</td><td>{@code "short"}</td><td>INTEGER</td></tr>
 *   <tr><td>{@code int} / {@link Integer}</td><td>{@code "int"}</td><td>INTEGER</td></tr>
 *   <tr><td>{@code long} / {@link Long}</td><td>{@code "long"}</td><td>INTEGER</td></tr>
 *   <tr><td>{@link String}</td><td>{@code "java.lang.String"}</td><td>UTF8String</td></tr>
 *   <tr><td>{@code byte[]}</td><td>{@code "byte[]"}</td><td>OCTET STRING</td></tr>
 * </table>
 *
 * <h2>Unsupported types</h2>
 * <p>Any type not in the table above (including {@code char}, {@code float},
 * {@code double}, and any Object type other than {@link String} and {@code byte[]})
 * causes {@link DerException} to be thrown naming the unsupported type.
 * {@code float}, {@code double}, {@code char} are supported as of STD-008 sec.17.3
 * (S7.6 deferral lifted) with strict canonical encodings -- see
 * {@code ObjectCodec.encodeValue} and {@code WireTypes.decode}.
 *
 * <h2>Determinism</h2>
 * <p>
 * Re-generating from the same class always produces a byte-identical schema record
 * (and therefore an identical {@link AtomicSerialSchemaRecord#schemaDigest()}).
 * Field order comes from {@code serialForm()} order; no maps or sets are used
 * in the field-definition path.
 *
 * <h2>Root class requirement ({@link #generate})</h2>
 * <p>
 * {@link #generate(Class)} is for classes whose direct parent is {@code Object} (no
 * {@code parentSchemaHash} field). Use {@link #generateChain(Class)} for hierarchy support.
 */
public final class SchemaGenerator {

    private SchemaGenerator() {
        throw new AssertionError("no instances");
    }

    /**
     * Generates an {@link AtomicSerialSchemaRecord} for a single Object-rooted
     * {@code @AtomicSerial} class.
     *
     * <p>Invokes {@code declaringClass.serialForm()} reflectively to obtain the
     * ordered field list. Each {@link AtomicSerial.SerialForm} entry is mapped to
     * an {@link AtomicSerialFieldDef} using the type mapping table above.
     *
     * @param atomicSerialClass the {@code @AtomicSerial} class to generate for;
     *                          must have a {@code public static SerialForm[] serialForm()}
     *                          method; must extend {@code Object} directly (for Phase 4.1/4.2)
     * @return an {@link AtomicSerialSchemaRecord} for the class, with no parent hash
     * @throws DerException         if {@code serialForm()} cannot be invoked, returns
     *                              {@code null}, or contains a field with an unsupported type
     * @throws NullPointerException if {@code atomicSerialClass} is {@code null}
     */
    public static AtomicSerialSchemaRecord generate(Class<?> atomicSerialClass)
            throws DerException {
        Objects.requireNonNull(atomicSerialClass, "atomicSerialClass");

        // A @Stateless @AtomicSerial class (e.g. a trivial preferred-class subclass such as
        // net.jini.id.UuidFactory$Impl, or a no-extra-state Throwable like LeaseException)
        // "has no arguments, Objects or data to write to the stream" and, per the annotation
        // contract, implements neither serialize(PutArg) nor serialForm(). It contributes an
        // empty namespace (no fields); the codec must not require serialForm() from it.
        AtomicSerial.SerialForm[] serialForm =
                atomicSerialClass.isAnnotationPresent(AtomicSerial.Stateless.class)
                        ? new AtomicSerial.SerialForm[0]
                        : invokeSerialForm(atomicSerialClass);

        List<AtomicSerialFieldDef> fields = new ArrayList<>(serialForm.length);
        for (AtomicSerial.SerialForm sf : serialForm) {
            String wireName = sf.getName();   // ObjectStreamField.getName()
            String wireType = toWireType(sf.getType(), atomicSerialClass);
            fields.add(new AtomicSerialFieldDef(wireName, wireType));
        }

        // Root record: no parentSchemaHash (parent is Object)
        return new AtomicSerialSchemaRecord(
                atomicSerialClass.getName(),
                (byte[]) null,
                fields);
    }

    /**
     * Generates a fully-linked {@link SchemaChain.Result} for a leaf class and
     * all of its {@code @AtomicSerial} ancestors up to (but not including)
     * {@code Object}.
     *
     * <h2>Walk order</h2>
     * <p>The method walks the superclass chain from {@code leafClass} up to
     * {@code Object}, collecting each class that is annotated with
     * {@code @AtomicSerial}. In Phase 4.3 every class in the hierarchy is
     * {@code @AtomicSerial}, so the collected list is: leaf, ..., root (the
     * first {@code @AtomicSerial} class below {@code Object}).
     *
     * <p>The collected list (leaf-first) is passed to
     * {@link SchemaChain#linkAndGetLeafDigest(List)} which processes it
     * root-to-leaf, setting each record's {@code parentSchemaHash} to the digest
     * of the next record up the chain. The result is:
     * <ul>
     *   <li>The root record has no {@code parentSchemaHash} (parent is {@code Object}).</li>
     *   <li>Each child record's {@code parentSchemaHash} = SHA-256 of its parent record.</li>
     *   <li>The leaf digest therefore commits the entire ancestor chain.</li>
     * </ul>
     *
     * <h2>Returned chain order</h2>
     * <p>The {@link SchemaChain.Result#chain()} list is <b>leaf-first, root-last</b>,
     * matching the input convention of {@link SchemaChain#linkAndGetLeafDigest}.
     * {@link au.net.zeus.jgdms.der.object.ObjectCodec#encodeHierarchy} and
     * {@code decodeHierarchy} both work with <b>superclass-first (root-first)</b> order
     * for the on-wire SEQUENCE arrangement; callers must reverse the chain if needed.
     *
     * @param leafClass the leaf {@code @AtomicSerial} class to generate for
     * @return a fully-linked chain, leaf-first
     * @throws DerException         if any class in the hierarchy lacks a
     *                              {@code serialForm()} or contains an unsupported type
     * @throws NullPointerException if {@code leafClass} is {@code null}
     */
    public static SchemaChain.Result generateChain(Class<?> leafClass) throws DerException {
        Objects.requireNonNull(leafClass, "leafClass");

        // Walk from leaf up to Object, collecting @AtomicSerial classes (leaf-first)
        List<AtomicSerialSchemaRecord> rawRecords = new ArrayList<>();
        Class<?> current = leafClass;
        while (current != null && current != Object.class) {
            if (current.isAnnotationPresent(AtomicSerial.class)) {
                // Generate this class's schema record (no parentSchemaHash yet --
                // SchemaChain.linkAndGetLeafDigest will set them)
                rawRecords.add(generate(current));
            }
            current = current.getSuperclass();
        }

        if (rawRecords.isEmpty()) {
            throw new DerException(
                    "SchemaGenerator.generateChain: no @AtomicSerial classes found "
                    + "in hierarchy rooted at " + leafClass.getName());
        }

        // rawRecords is leaf-first; SchemaChain.linkAndGetLeafDigest expects leaf-first
        return SchemaChain.linkAndGetLeafDigest(rawRecords);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Invokes {@code serialForm()} reflectively on {@code clazz}.
     *
     * @throws DerException if the method cannot be found, invoked, or returns null
     */
    private static AtomicSerial.SerialForm[] invokeSerialForm(Class<?> clazz)
            throws DerException {
        MarshalDelegate delegate = MarshalDelegates.delegateFor(clazz);
        if (delegate != null) {
            // In-package dispatch: the class's own serialForm() with no reflection
            // into its package-private members; reflection below is the fallback.
            AtomicSerial.SerialForm[] forms = delegate.serialForm(clazz);
            if (forms == null) {
                throw new DerException(
                        "SchemaGenerator: serialForm() on " + clazz.getName()
                        + " returned null");
            }
            return forms;
        }
        Method method;
        try {
            method = clazz.getMethod("serialForm");
        } catch (NoSuchMethodException ex) {
            throw new DerException(
                    "SchemaGenerator: class " + clazz.getName()
                    + " has no public static serialForm() method");
        }
        method.setAccessible(true);
        try {
            Object result = method.invoke(null);
            if (result == null) {
                throw new DerException(
                        "SchemaGenerator: serialForm() on " + clazz.getName()
                        + " returned null");
            }
            return (AtomicSerial.SerialForm[]) result;
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            DerException de = new DerException(
                    "SchemaGenerator: serialForm() on " + clazz.getName()
                    + " threw " + cause);
            de.initCause(cause);
            throw de;
        } catch (IllegalAccessException ex) {
            throw new DerException(
                    "SchemaGenerator: cannot invoke serialForm() on "
                    + clazz.getName(), ex);
        }
    }

    /**
     * Maps a Java {@link Class} to its canonical wireType string.
     *
     * <h3>Type mapping (extended for B1 inc-3)</h3>
     * <table border="1">
     *   <caption>Supported type mappings</caption>
     *   <tr><th>Java type</th><th>wireType string</th></tr>
     *   <tr><td>{@code boolean}/{@link Boolean}</td><td>{@code "boolean"}</td></tr>
     *   <tr><td>{@code byte}/{@link Byte}</td><td>{@code "byte"}</td></tr>
     *   <tr><td>{@code short}/{@link Short}</td><td>{@code "short"}</td></tr>
     *   <tr><td>{@code int}/{@link Integer}</td><td>{@code "int"}</td></tr>
     *   <tr><td>{@code long}/{@link Long}</td><td>{@code "long"}</td></tr>
     *   <tr><td>{@link String}</td><td>{@code "java.lang.String"}</td></tr>
     *   <tr><td>{@code byte[]}</td><td>{@code "byte[]"} (UNCHANGED -- OCTET STRING)</td></tr>
     *   <tr><td>{@code @AtomicSerial} type</td><td>{@code "@AtomicSerial"}</td></tr>
     *   <tr><td>any enum type</td><td>{@code "enum:<className>"}</td></tr>
     *   <tr><td>{@code T[]} (not {@code byte[]})</td>
     *       <td>{@code "array:<componentWireType>"} for single-dim arrays;
     *           multi-dim and array-of-byte rejected with {@link DerException}</td></tr>
     * </table>
     *
     * @param javaType  the type reported by {@code SerialForm.getType()}
     * @param declaring the declaring class (for error messages)
     * @return the wireType string
     * @throws DerException if the type is unsupported or multi-dimensional
     */
    public static String toWireType(Class<?> javaType, Class<?> declaring)
            throws DerException {
        if (javaType == boolean.class || javaType == Boolean.class) return "boolean";
        if (javaType == byte.class    || javaType == Byte.class)    return "byte";
        if (javaType == short.class   || javaType == Short.class)   return "short";
        if (javaType == int.class     || javaType == Integer.class) return "int";
        if (javaType == long.class    || javaType == Long.class)    return "long";
        if (javaType == String.class)                               return "java.lang.String";

        // byte[] is UNCHANGED: OCTET STRING, NOT promoted to "array:byte".
        // This preserves the existing compact encoding and back-compat (STD-008 sec.17.2).
        if (javaType == byte[].class)                               return "byte[]";

        // Nested @AtomicSerial object field (STD-008 sec.16): the runtime class
        // travels in the embedded schema so the marker need not name the class.
        if (javaType.isAnnotationPresent(AtomicSerial.class))       return "@AtomicSerial";

        // Enum fields (STD-008 sec.17.1): encode by NAME for version-stability.
        // Must be checked BEFORE the array check since enum types are not arrays.
        if (javaType.isEnum()) return "enum:" + javaType.getName();

        // Array fields (STD-008 sec.17.2): one level of array only.
        // byte[] is handled above (OCTET STRING); reaching here means component != byte.
        if (javaType.isArray()) {
            Class<?> componentType = javaType.getComponentType();
            String componentWireType = toWireType(componentType, declaring);
            // Reject multi-dimensional arrays (array-of-array)
            if (componentWireType.startsWith("array:")) {
                throw new DerException(
                        "SchemaGenerator: multi-dimensional arrays are not yet supported"
                        + " (type " + javaType.getName() + " in class "
                        + declaring.getName() + "); inc-3 supports one level only");
            }
            // Reject array:byte (ambiguous with the byte[] -> OCTET STRING mapping above).
            // This path is actually unreachable because byte.class produces "byte" and
            // byte[].class is handled before the isArray() check -- but guard it explicitly
            // for fail-secure clarity.
            if (componentWireType.equals("byte")) {
                throw new DerException(
                        "SchemaGenerator: array:byte is not supported (ambiguous with the"
                        + " byte[] -> OCTET STRING mapping) in class "
                        + declaring.getName()
                        + "; use byte[] directly for octet strings");
            }
            // For @AtomicSerial component arrays, embed the component class name so
            // the decode side can instantiate the correct array type without the field's
            // declared Java type (which is not available from the schema alone).
            if (componentWireType.equals("@AtomicSerial")) {
                return "array:@AtomicSerial:" + componentType.getName();
            }
            return "array:" + componentWireType;
        }

        // STD-008 sec.17.3 lifted the S7.6 deferral with strict canonicalization
        // (Entry-matching determinism: the wire MUST be byte-identical for the same value
        // across all senders -- encoder canonicalizes; decoder rejects non-canonical).
        if (javaType == float.class  || javaType == Float.class)     return "float";
        if (javaType == double.class || javaType == Double.class)    return "double";
        if (javaType == char.class   || javaType == Character.class) return "char";

        // A non-@AtomicSerial type with a registered DER replacement serializer
        // (e.g. java.security.AccessControlContext) is admitted as a nested
        // @AtomicSerial: the serializer (which IS @AtomicSerial) is substituted at
        // encode time and its schema travels in the embedded record.
        if (au.net.zeus.jgdms.der.serial.DerReplacer.isRegistered(javaType)) {
            return "@AtomicSerial";
        }

        // A field declared as an interface or abstract class is a polymorphic slot: it
        // cannot itself be the runtime type, and its runtime value's concrete
        // @AtomicSerial class travels in the embedded schema -- exactly as for a nested
        // @AtomicSerial field (the marker need not name a class). It is encoded as
        // "@AtomicSerial": encodeNested uses value.getClass(), and at decode the embedded
        // chain supplies the concrete class while the declared interface/abstract type is
        // used only for the constructor's assignability check. The runtime value MUST be
        // @AtomicSerial (or carry a registered serializer), else encodeNested fails fast.
        // (A CONCRETE non-@AtomicSerial type is a single fixed type, not a polymorphic
        // slot, so it is still rejected below.) This is what lets a live remote reference
        // -- e.g. net.jini.jeri.BasicObjectEndpoint, whose 'ep' field is declared as the
        // Endpoint interface -- travel on the DER wire (JGDMS-STD-008 sec.16).
        if (javaType.isInterface()
                || java.lang.reflect.Modifier.isAbstract(javaType.getModifiers())) {
            return "@AtomicSerial";
        }

        // Any other type -- not guessed, clear error
        throw new DerException(
                "SchemaGenerator: unsupported serial field type " + javaType.getName()
                + " in class " + declaring.getName()
                + ". Supported types: boolean, byte, short, int, long, "
                + "java.lang.String, byte[], @AtomicSerial, enum types, single-dim arrays");
    }
}
