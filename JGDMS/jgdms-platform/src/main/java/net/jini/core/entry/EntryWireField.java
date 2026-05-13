/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.core.entry;

/**
 * Describes a single named wire field in the explicit serial schema of a
 * {@link SerialEntry @SerialEntry} class.
 *
 * <p>
 * An array of {@code EntryWireField} instances is returned by the static
 * {@code entryForm()} method that every {@code @SerialEntry} class must
 * declare.  This array is the single source of truth for:
 * </p>
 * <ul>
 * <li>The <em>wire field names</em> used in the SHA-256 type-identity hash.</li>
 * <li>The <em>field ordering</em> used when serialising and deserialising
 *     entry field values.</li>
 * <li>The <em>type information</em> stored alongside each wire name.</li>
 * </ul>
 *
 * <p>
 * The wire field name is completely independent of the Java field name
 * declared in the class.  A developer may rename a Java field (e.g.
 * {@code host → hostname}) without changing the corresponding
 * {@code EntryWireField} name {@code "host"}, and the type-identity hash
 * will remain stable across that rename.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public static EntryWireField[] entryForm() {
 *     return new EntryWireField[] {
 *         new EntryWireField("host",  String.class),
 *         new EntryWireField("floor", Integer.class),
 *         new EntryWireField("room",  String.class)
 *     };
 * }
 * }</pre>
 *
 * @see SerialEntry
 * @see GetEntryArg
 * @see PutEntryArg
 *
 * @since 3.1
 */
public final class EntryWireField {

    private final String name;
    private final Class<?> type;

    /**
     * Creates a new {@code EntryWireField} with the given wire name and type.
     *
     * @param name the wire field name; must not be {@code null} or empty
     * @param type the declared type of the field; must not be {@code null}
     * @throws NullPointerException if {@code name} or {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code name} is empty
     */
    public EntryWireField(String name, Class<?> type) {
        if (name == null) throw new NullPointerException("name must not be null");
        if (type == null) throw new NullPointerException("type must not be null");
        if (name.isEmpty()) throw new IllegalArgumentException("name must not be empty");
        this.name = name;
        this.type = type;
    }

    /**
     * Returns the wire field name.
     *
     * @return the wire field name; never {@code null} or empty
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the declared type of this wire field.
     *
     * @return the field type; never {@code null}
     */
    public Class<?> getType() {
        return type;
    }

    @Override
    public String toString() {
        return "EntryWireField[" + name + ":" + type.getName() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EntryWireField)) return false;
        EntryWireField other = (EntryWireField) obj;
        return name.equals(other.name) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + type.hashCode();
    }
}
