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

package au.net.zeus.jgdms.der.entry;

import au.net.zeus.jgdms.der.DerException;
import org.apache.river.api.io.EntryV2Codec;

import java.io.IOException;
import java.util.Map;

/**
 * {@link EntryV2Codec} provider backed by {@link EntryRepV2Codec} / {@link EntrySchemaGenerator}.
 * Discovered by {@link java.util.ServiceLoader} from {@code outrigger-dl}'s {@code EntryRep} on a
 * DER-capable JVM (the flag-day requirement).
 */
public final class DerEntryV2Codec implements EntryV2Codec {

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public DerEntryV2Codec() {}

    @Override
    public Encoded encodeReflective(Class<?> entryClass, Object entryInstance) throws IOException {
        EntryRepV2Codec.EncodedBody b = EntryRepV2Codec.encodeReflective(entryClass, entryInstance);
        return new Encoded(b.body(), b.sliceBytes(), b.entrySchemaDigest());
    }

    @Override
    public Encoded encodeSerialEntry(String className, String[] superclassNames,
                                     String[] wireNames, Class<?>[] wireTypes,
                                     Object[] orderedValues) throws IOException {
        EntryRepV2Codec.EncodedBody b =
                EntryRepV2Codec.encodeSerialEntry(className, wireNames, wireTypes, orderedValues);
        return new Encoded(b.body(), b.sliceBytes(), b.entrySchemaDigest());
    }

    @Override
    public Decoded decode(byte[] body) throws IOException {
        try {
            EntryRepV2Codec.DecodedBody d = EntryRepV2Codec.decode(body);
            return new Decoded(d.entrySchemaDigest(), d.sliceBytes(), d.absent(), d.schemaTable());
        } catch (DerException e) {
            throw new IOException("EntryRepV2: cannot decode body: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object decodeFieldValue(byte[] slice, Class<?> declaredType, Decoded ctx)
            throws IOException, ClassNotFoundException {
        Map<String, byte[]> table = (Map<String, byte[]>) ctx.schemaTable;
        return EntryRepV2Codec.decodeFieldValue(slice, declaredType, table);
    }
}
