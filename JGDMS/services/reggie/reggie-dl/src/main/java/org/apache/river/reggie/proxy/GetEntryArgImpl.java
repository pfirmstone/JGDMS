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
import java.io.InvalidObjectException;
import java.util.HashMap;
import java.util.Map;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;

/**
 * Package-private implementation of {@link GetEntryArg} that wraps the
 * positional {@code Object[]} fields stored inside an {@link EntryRep} and
 * exposes them by the wire names declared in the corresponding
 * {@link net.jini.core.entry.SerialEntry @SerialEntry} class's
 * {@code entryForm()} method.
 */
final class GetEntryArgImpl extends GetEntryArg {

    /** Map from wire name to stored value (null values are permitted). */
    private final Map<String, Object> valueMap;
    /** Set of wire names that were actually present in the stored entry. */
    private final java.util.Set<String> presentNames;

    /**
     * Constructs a {@code GetEntryArgImpl} from the stored field array and
     * the wire schema declared by the entry class.
     *
     * @param wireFields the wire schema from {@code entryForm()}; its length
     *                   determines how many fields are consumed from
     *                   {@code storedFields}
     * @param storedFields the positional field values as stored in
     *                     {@link EntryRep}; values may be {@code null}
     *                     (representing a null field value) or instances of
     *                     {@link org.apache.river.proxy.MarshalledWrapper}
     *                     for non-primitive reference types
     */
    GetEntryArgImpl(EntryWireField[] wireFields, Object[] storedFields) {
        int count = Math.min(wireFields.length, storedFields.length);
        valueMap = new HashMap<>(Math.max((int) (count / 0.75) + 1, 16));
        presentNames = new java.util.HashSet<>(Math.max((int) (count / 0.75) + 1, 16));
        for (int i = 0; i < count; i++) {
            String name = wireFields[i].getName();
            valueMap.put(name, storedFields[i]);
            presentNames.add(name);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String name, T defaultValue, Class<T> type)
            throws IOException {
        if (name == null) throw new NullPointerException("name must not be null");
        if (type == null) throw new NullPointerException("type must not be null");
        if (!presentNames.contains(name)) {
            return defaultValue;
        }
        Object val = valueMap.get(name);
        if (val == null) {
            return null;
        }
        if (!type.isInstance(val)) {
            InvalidObjectException ioe = new InvalidObjectException(
                "Field \"" + name + "\": expected " + type.getName()
                + " but was " + val.getClass().getName());
            ioe.initCause(new ClassCastException(
                "Cannot cast " + val.getClass().getName()
                + " to " + type.getName()));
            throw ioe;
        }
        return type.cast(val);
    }

    @Override
    public boolean defaulted(String name) {
        if (name == null) throw new NullPointerException("name must not be null");
        return !presentNames.contains(name);
    }
}
