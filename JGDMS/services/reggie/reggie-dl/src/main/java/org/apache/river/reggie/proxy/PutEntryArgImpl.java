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
package org.apache.river.reggie.proxy;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.PutEntryArg;

/**
 * Package-private implementation of {@link PutEntryArg} that collects named
 * field values during a {@link net.jini.core.entry.SerialEntry @SerialEntry}
 * class's static {@code serialize()} method and then orders them into a
 * positional {@code Object[]} matching the wire schema declared in
 * {@code entryForm()}.
 */
final class PutEntryArgImpl extends PutEntryArg {

    private final EntryWireField[] wireFields;
    /** Insertion-ordered map of wire name → value as supplied by serialize(). */
    private final Map<String, Object> collected;
    private boolean committed = false;
    /** The ordered result array, populated after writeArgs() is called. */
    private Object[] result;

    PutEntryArgImpl(EntryWireField[] wireFields) {
        this.wireFields = wireFields;
        this.collected = new LinkedHashMap<>(wireFields.length * 2);
    }

    @Override
    public void put(String name, Object value) throws IOException {
        if (name == null) throw new NullPointerException("name must not be null");
        if (committed)
            throw new IllegalStateException("writeArgs() has already been called");
        collected.put(name, value);
    }

    @Override
    public void writeArgs() throws IOException {
        if (committed)
            throw new IllegalStateException("writeArgs() has already been called");
        committed = true;
        result = new Object[wireFields.length];
        for (int i = 0; i < wireFields.length; i++) {
            result[i] = collected.get(wireFields[i].getName());
        }
    }

    /**
     * Returns the ordered field values in {@code entryForm()} order.
     * Must only be called after {@link #writeArgs()}.
     *
     * @return positional {@code Object[]} ready for storage in {@link EntryRep}
     * @throws IllegalStateException if {@link #writeArgs()} has not been called
     */
    Object[] getResult() {
        if (!committed)
            throw new IllegalStateException("writeArgs() has not been called yet");
        return result;
    }
}
