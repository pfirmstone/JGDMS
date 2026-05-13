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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional annotation for {@link Entry} classes that wish to declare an
 * explicit wire field schema independent of their Java field declarations.
 *
 * <h2>Purpose</h2>
 * <p>
 * The standard Jini Entry serialisation mechanism derives the type-identity
 * hash and field ordering from live reflection over {@code Class.getFields()}.
 * This means that any change to a field's Java name or declared type — even a
 * pure refactoring rename — silently breaks all stored entries in deployed
 * Lookup Services and JavaSpaces.
 * </p>
 * <p>
 * {@code @SerialEntry} allows an Entry implementor to separate the
 * <em>wire contract</em> (the set of named fields used for storage and
 * retrieval) from the <em>Java implementation</em> (the field names visible
 * in source code).  This mirrors the approach taken by
 * {@link org.apache.river.api.io.AtomicSerial @AtomicSerial} for ordinary
 * serialisable objects.
 * </p>
 *
 * <h2>Requirements</h2>
 * <p>
 * A class annotated with {@code @SerialEntry} <strong>must</strong> provide
 * all three of the following:
 * </p>
 * <ol>
 * <li>A public static {@code entryForm()} method that returns the wire
 *     schema:
 *     <pre>{@code
 *     public static EntryWireField[] entryForm() { ... }
 *     }</pre>
 * </li>
 * <li>A public static {@code serialize()} method that writes the entry's
 *     state through a {@link PutEntryArg}:
 *     <pre>{@code
 *     public static void serialize(PutEntryArg arg, T obj) throws java.io.IOException { ... }
 *     }</pre>
 *     where {@code T} is the annotated class.
 * </li>
 * <li>A public deserialization constructor whose sole parameter is
 *     {@link GetEntryArg}:
 *     <pre>{@code
 *     public T(GetEntryArg arg) throws java.io.IOException { ... }
 *     }</pre>
 * </li>
 * </ol>
 *
 * <h2>Hash computation</h2>
 * <p>
 * For {@code @SerialEntry} classes the type-identity hash is computed using
 * <strong>SHA-256</strong> (rather than the legacy SHA-1) over:
 * </p>
 * <ol>
 * <li>The 64-bit hash of the superclass (if any), written as a long.</li>
 * <li>The fully qualified class name (UTF-8).</li>
 * <li>For each field in {@link #entryForm() entryForm()} order: the wire
 *     field name (UTF-8) followed by the wire field type name (UTF-8).</li>
 * </ol>
 * <p>
 * Because the hash is derived from the explicit wire schema rather than from
 * live reflection, renaming a Java field without changing {@code entryForm()}
 * does <em>not</em> change the hash.
 * </p>
 *
 * <h2>Backward compatibility — public fields</h2>
 * <p>
 * Because all Jini Entry wire-schema fields are {@code public}, renaming a Java
 * field without changing the wire name in {@code entryForm()} does <em>not</em>
 * change the SHA-256 hash, but it <strong>does</strong> break binary
 * compatibility: any pre-compiled class that accesses the old field name
 * directly will fail to link.  {@code @SerialEntry} therefore protects the
 * <em>wire contract</em> but not the <em>Java API contract</em>.  The safest
 * evolution strategy for a deployed Entry class remains subclassing rather than
 * in-place modification.
 * </p>
 *
 * <h2>Java records</h2>
 * <p>
 * Java 16+ records are natural {@code @SerialEntry} implementations: their
 * components are implicitly {@code final}, the canonical constructor fires the
 * JMM freeze action, and they provide {@code equals}/{@code hashCode}/
 * {@code toString} over their components automatically.  A record-based entry
 * adds a {@code public RecordEntry(GetEntryArg arg)} constructor that delegates
 * to the canonical constructor:
 * </p>
 * <pre>{@code
 * @SerialEntry
 * public record LocationRecord(String host, Integer floor) implements Entry {
 *
 *     public static EntryWireField[] entryForm() {
 *         return new EntryWireField[] {
 *             new EntryWireField("host",  String.class),
 *             new EntryWireField("floor", Integer.class),
 *         };
 *     }
 *
 *     public LocationRecord(GetEntryArg arg) throws java.io.IOException {
 *         this(arg.get("host",  null, String.class),
 *              arg.get("floor", null, Integer.class));
 *         if (host == null)
 *             throw new java.io.InvalidObjectException("host required");
 *     }
 *
 *     public static void serialize(PutEntryArg arg, LocationRecord r)
 *             throws java.io.IOException {
 *         arg.put("host",  r.host());
 *         arg.put("floor", r.floor());
 *         arg.writeArgs();
 *     }
 * }
 * }</pre>
 *
 * <h2>Canonical example</h2>
 * <pre>{@code
 * @SerialEntry
 * public class Location implements Entry {
 *
 *     public static EntryWireField[] entryForm() {
 *         return new EntryWireField[] {
 *             new EntryWireField("host",  String.class),
 *             new EntryWireField("floor", Integer.class),
 *             new EntryWireField("room",  String.class)
 *         };
 *     }
 *
 *     public String  host;
 *     public Integer floor;
 *     public String  room;
 *
 *     public Location() {}   // still required for legacy interop
 *
 *     public Location(GetEntryArg arg) throws java.io.IOException {
 *         this(arg, check(arg));
 *     }
 *
 *     private Location(GetEntryArg arg, boolean checked)
 *             throws java.io.IOException {
 *         host  = arg.get("host",  null, String.class);
 *         floor = arg.get("floor", null, Integer.class);
 *         room  = arg.get("room",  null, String.class);
 *     }
 *
 *     private static boolean check(GetEntryArg arg)
 *             throws java.io.IOException {
 *         if (arg.get("host", null, String.class) == null)
 *             throw new java.io.InvalidObjectException("host must not be null");
 *         return true;
 *     }
 *
 *     public static void serialize(PutEntryArg arg, Location loc)
 *             throws java.io.IOException {
 *         arg.put("host",  loc.host);
 *         arg.put("floor", loc.floor);
 *         arg.put("room",  loc.room);
 *         arg.writeArgs();
 *     }
 * }
 * }</pre>
 *
 * @see EntryWireField
 * @see GetEntryArg
 * @see PutEntryArg
 * @see Entry
 *
 * @since 3.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SerialEntry {
}
