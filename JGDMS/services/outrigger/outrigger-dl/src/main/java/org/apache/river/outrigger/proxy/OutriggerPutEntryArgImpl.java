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
package org.apache.river.outrigger.proxy;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.PutEntryArg;

/**
 * Package-private implementation of {@link PutEntryArg} for the Outrigger
 * (JavaSpace) proxy.  Collects named field values during a
 * {@link net.jini.core.entry.SerialEntry @SerialEntry} class's static
 * {@code serialize()} method, then orders them into a positional
 * {@code Object[]} matching the wire schema declared in {@code entryForm()}.
 */
final class OutriggerPutEntryArgImpl extends PutEntryArg {

    private final EntryWireField[] wireFields;
    private final Map<String, Object> collected;
    private boolean committed = false;
    private Object[] result;

    OutriggerPutEntryArgImpl(EntryWireField[] wireFields) {
        this.wireFields = wireFields;
        this.collected = new LinkedHashMap<>(Math.max(wireFields.length * 4 / 3 + 1, 16));
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

    Object[] getResult() {
        if (!committed)
            throw new IllegalStateException("writeArgs() has not been called yet");
        return result;
    }
}
