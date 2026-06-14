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

/**
 * Generates an {@link AtomicSerialSchemaRecord} from a single Object-rooted
 * {@code @AtomicSerial} class (Phase 4.2 — no hierarchy, no parent hash).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AtomicSerialSchemaRecord record = SchemaGenerator.generate(MyClass.class);
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
 * <h2>Root class requirement</h2>
 * <p>
 * This generator is for classes whose direct parent is {@code Object} (no
 * {@code parentSchemaHash} field). Phase 4.3 (hierarchy support) will extend
 * this to produce chained records.
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
