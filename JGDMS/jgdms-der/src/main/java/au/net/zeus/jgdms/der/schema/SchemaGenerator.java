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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Collections;

/**
 * Generates an {@link AtomicSerialSchemaRecord} from a single Object-rooted
 * {@code @AtomicSerial} class (Phase 4.2 — no hierarchy, no parent hash) or a
 * complete linked chain from a leaf class through all {@code @AtomicSerial}
 * ancestors (Phase 4.3 — see {@link #generateChain(Class)}).
 *
 * <h2>Usage — single class</h2>
 * <pre>{@code
 * AtomicSerialSchemaRecord record = SchemaGenerator.generate(MyClass.class);
 * }</pre>
 *
 * <h2>Usage — hierarchy chain (Phase 4.3)</h2>
 * <pre>{@code
 * SchemaChain.Result result = SchemaGenerator.generateChain(LeafClass.class);
 * // result.chain() is leaf-first, root-last; each record's parentSchemaHash
 * // points to its parent's digest.
 * }</pre>
 *
 * <h2>Type mapping (Java type → wireType string)</h2>
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
 * {@code char}, {@code float}, and {@code double} are explicitly deferred per
 * STD-006 §7.6 and are identified as such in the error message.
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

        AtomicSerial.SerialForm[] serialForm = invokeSerialForm(atomicSerialClass);

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
                // Generate this class's schema record (no parentSchemaHash yet —
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
     * @param javaType  the type reported by {@code SerialForm.getType()}
     * @param declaring the declaring class (for error messages)
     * @return the wireType string
     * @throws DerException if the type is unsupported
     */
    public static String toWireType(Class<?> javaType, Class<?> declaring)
            throws DerException {
        if (javaType == boolean.class || javaType == Boolean.class) return "boolean";
        if (javaType == byte.class    || javaType == Byte.class)    return "byte";
        if (javaType == short.class   || javaType == Short.class)   return "short";
        if (javaType == int.class     || javaType == Integer.class) return "int";
        if (javaType == long.class    || javaType == Long.class)    return "long";
        if (javaType == String.class)                               return "java.lang.String";
        if (javaType == byte[].class)                               return "byte[]";

        // Explicitly deferred types — give a specific message
        if (javaType == char.class    || javaType == Character.class
                || javaType == float.class  || javaType == Float.class
                || javaType == double.class || javaType == Double.class) {
            throw new DerException(
                    "SchemaGenerator: type " + javaType.getName()
                    + " is deferred per STD-006 §7.6 (char/float/double not yet supported)"
                    + " in class " + declaring.getName());
        }

        // Any other type — not guessed, clear error
        throw new DerException(
                "SchemaGenerator: unsupported serial field type " + javaType.getName()
                + " in class " + declaring.getName()
                + ". Supported types: boolean, byte, short, int, long, "
                + "java.lang.String, byte[]");
    }
}
