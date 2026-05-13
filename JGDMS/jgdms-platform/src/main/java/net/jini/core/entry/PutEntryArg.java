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

/**
 * Write-only view used by a {@link SerialEntry @SerialEntry} class to record
 * its named field values during serialization.
 *
 * <p>
 * An instance of this class is passed to the static {@code serialize()} method
 * that every {@code @SerialEntry} class must declare:
 * </p>
 * <pre>{@code
 * public static void serialize(PutEntryArg arg, Location loc)
 *         throws IOException {
 *     arg.put("host",  loc.host);
 *     arg.put("floor", loc.floor);
 *     arg.put("room",  loc.room);
 *     arg.writeArgs();
 * }
 * }</pre>
 *
 * <p>
 * Each call to {@link #put(String, Object)} records one wire field value
 * using the wire name declared in {@code entryForm()}.  The names do not need
 * to match the Java field names; the developer may freely rename Java fields
 * without changing the wire names.
 * </p>
 *
 * <p>
 * {@link #writeArgs()} must be called exactly once, at the end of the
 * {@code serialize()} method, to commit the buffered values to the underlying
 * representation.
 * </p>
 *
 * @see SerialEntry
 * @see GetEntryArg
 * @see EntryWireField
 *
 * @since 3.1
 */
public abstract class PutEntryArg {

    /**
     * Records the value of the named wire field.
     *
     * <p>
     * The {@code name} must match a wire field name declared in the class's
     * {@code entryForm()} array.  Passing an unrecognised name may result in
     * an {@link IllegalArgumentException} at the implementation's discretion.
     * </p>
     *
     * @param name  the wire field name as declared in {@code entryForm()};
     *              must not be {@code null}
     * @param value the value to record; may be {@code null}
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public abstract void put(String name, Object value) throws IOException;

    /**
     * Commits all buffered field values to the underlying representation.
     *
     * <p>
     * This method must be called exactly once at the end of the
     * {@code serialize()} method, after all {@link #put(String, Object)} calls
     * have been made.
     * </p>
     *
     * @throws IOException if an I/O error occurs
     */
    public abstract void writeArgs() throws IOException;
}
