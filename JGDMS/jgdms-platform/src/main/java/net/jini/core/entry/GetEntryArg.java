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

import java.io.IOException;
import java.io.InvalidObjectException;

/**
 * Read-only view of the named field values for a {@link SerialEntry @SerialEntry}
 * class during deserialization.
 *
 * <p>
 * An instance of this class is passed as the sole argument to the
 * deserialization constructor of every {@code @SerialEntry} class:
 * </p>
 * <pre>{@code
 * public Location(GetEntryArg arg) throws IOException {
 *     this(arg, check(arg));   // static check BEFORE bridge ctor
 * }
 *
 * private Location(GetEntryArg arg, boolean checked) throws IOException {
 *     host  = arg.get("host",  null, String.class);
 *     floor = arg.get("floor", null, Integer.class);
 *     room  = arg.get("room",  null, String.class);
 * }
 * }</pre>
 *
 * <h2>Default-value fallback</h2>
 * <p>
 * When the stored entry was serialized by an older version of the class that
 * did not yet include a particular field, {@link #get(String, Object, Class)}
 * returns the supplied {@code defaultValue} rather than throwing an exception.
 * This makes it possible to add new fields to {@code entryForm()} while
 * remaining backward-compatible with previously stored entries.
 * </p>
 *
 * <h2>Null vs absent</h2>
 * <p>
 * {@link #defaulted(String)} distinguishes between a field that was explicitly
 * stored as {@code null} and a field that was absent from the stored entry
 * (e.g. because it did not exist in the version that wrote the entry).
 * </p>
 *
 * @see SerialEntry
 * @see PutEntryArg
 * @see EntryWireField
 *
 * @since 3.1
 */
public abstract class GetEntryArg {

    /**
     * Retrieve the value of the named wire field, returning {@code defaultValue}
     * if the field is absent from the stored entry.
     *
     * <p>
     * Implementations must check that the stored value is assignment-compatible
     * with {@code type} before returning it, and throw
     * {@link InvalidObjectException} (with a {@link ClassCastException} as its
     * cause) if the check fails.
     * </p>
     *
     * @param <T>          the expected type of the field value
     * @param name         the wire field name as declared in {@code entryForm()}
     * @param defaultValue the value to return when the field is absent from
     *                     the stored entry; may be {@code null}
     * @param type         the expected type; used for a runtime type check
     *                     before the value is returned; must not be {@code null}
     * @return the stored value cast to {@code T}, or {@code defaultValue} if
     *         the field is absent
     * @throws IOException           if an I/O error occurs
     * @throws NullPointerException  if {@code name} or {@code type} is {@code null}
     * @throws InvalidObjectException if the stored value is present but not
     *                               assignment-compatible with {@code type}
     */
    public abstract <T> T get(String name, T defaultValue, Class<T> type)
            throws IOException;

    /**
     * Returns {@code true} if the named field was absent from the stored entry
     * (i.e. was not written by the serializing version of the class).
     *
     * <p>
     * A return value of {@code false} means the field was present in the stored
     * entry, even if its stored value was {@code null}.
     * </p>
     *
     * @param name the wire field name as declared in {@code entryForm()}
     * @return {@code true} if the field was not present in the stored entry
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public abstract boolean defaulted(String name);
}
