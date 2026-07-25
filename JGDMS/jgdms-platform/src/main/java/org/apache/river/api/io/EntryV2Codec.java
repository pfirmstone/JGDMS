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

package org.apache.river.api.io;

import java.io.IOException;

/**
 * Release-8 SPI seam for the Outrigger EntryRep-v2 whole-entry DER record
 * (JGDMS-STD-006 EntryRep-v2 amendment). The implementation lives in the JDK25-only
 * {@code jgdms-der} module ({@code au.net.zeus.jgdms.der.entry.DerEntryV2Codec}) and is
 * discovered via {@link java.util.ServiceLoader}, exactly as {@code MarshalFactoryProvider}
 * is for DER {@code MarshalledInstance}.
 *
 * <p>This interface lives in {@code jgdms-platform} because it is the one module both
 * {@code outrigger-dl} (which references it at compile, {@code release 8}) and
 * {@code jgdms-der} (which implements it, JDK 25) share. {@code EntryRep} cannot name any
 * {@code jgdms-der} type (that module is test-scope only in outrigger-dl), so all the DER
 * work is done behind this SPI at runtime on a DER-capable JVM.
 *
 * <p><b>Flag-day:</b> a v2-born space requires a DER-capable JVM. When no provider is on
 * the classpath, the caller (e.g. {@code EntryRep}) must fail LOUDLY, never silently.
 */
public interface EntryV2Codec {

    /** The encoded body plus the per-field slice bytes (cached for matching) and the entry digest. */
    final class Encoded {
        /** The canonical {@code EntryRepV2Body} DER bytes (wire + store form). */
        public final byte[] body;
        /** Per-field canonical slice bytes (the byte-equality match unit). */
        public final byte[][] sliceBytes;
        /** The 32-byte {@code entrySchemaDigest} (routing/identity; excluded from matching). */
        public final byte[] entrySchemaDigest;

        public Encoded(byte[] body, byte[][] sliceBytes, byte[] entrySchemaDigest) {
            this.body = body;
            this.sliceBytes = sliceBytes;
            this.entrySchemaDigest = entrySchemaDigest;
        }
    }

    /** The decoded, fully-validated body: the parts a receiver needs to match, index, and reconstruct. */
    final class Decoded {
        /** The 32-byte {@code entrySchemaDigest}. */
        public final byte[] entrySchemaDigest;
        /** Per-field raw slice bytes (the byte-equality match unit). */
        public final byte[][] sliceBytes;
        /** Per-field wildcard/null marker (true = {@code absent [0]} slice). */
        public final boolean[] absent;
        /** Opaque provider-internal schema table (passed back to {@link #decodeFieldValue}). */
        public final Object schemaTable;

        public Decoded(byte[] entrySchemaDigest, byte[][] sliceBytes, boolean[] absent,
                       Object schemaTable) {
            this.entrySchemaDigest = entrySchemaDigest;
            this.sliceBytes = sliceBytes;
            this.absent = absent;
            this.schemaTable = schemaTable;
        }
    }

    /**
     * Encodes an ordinary reflective Entry instance (usable public fields, in the single
     * {@code FieldComparator} order shared with the server/index).
     *
     * @param entryClass    the entry's runtime class
     * @param entryInstance the entry instance
     * @return the encoded body
     * @throws IOException if reflection or DER encoding fails (e.g. a non-encodable / proxy value)
     */
    Encoded encodeReflective(Class<?> entryClass, Object entryInstance) throws IOException;

    /**
     * Encodes a {@code @SerialEntry} instance from its explicit wire-field metadata
     * ({@code entryForm()}) and the ordered field values ({@code serialize()} output).
     *
     * @param className        the entry class name
     * @param superclassNames  superclass names (routing; may be empty)
     * @param wireNames        wire field names, in positional order
     * @param wireTypes        wire field declared types, in positional order
     * @param orderedValues    the field values, positionally aligned to {@code wireNames}
     * @return the encoded body
     * @throws IOException if DER encoding fails
     */
    Encoded encodeSerialEntry(String className, String[] superclassNames,
                              String[] wireNames, Class<?>[] wireTypes,
                              Object[] orderedValues) throws IOException;

    /**
     * Decodes and fully validates an {@code EntryRepV2Body} (fail-closed, amendment
     * &sect;A.9 including the &sect;A.8 field-count guard). Rejects a non-v2 body LOUDLY.
     *
     * @param body the DER body bytes
     * @return the validated parts
     * @throws IOException if the body is malformed, over a ceiling, not v2, or otherwise invalid
     */
    Decoded decode(byte[] body) throws IOException;

    /**
     * Reconstructs one field value from a decoded slice (for the client's {@code entry()}).
     *
     * @param slice        the raw slice bytes (from {@link Decoded#sliceBytes})
     * @param declaredType the expected field type (assignability check), or {@code null}
     * @param ctx          the {@link Decoded} whose schema table backs {@code @AtomicSerial} values
     * @return the decoded value, or {@code null} for an absent slice
     * @throws IOException            if decoding fails
     * @throws ClassNotFoundException if a class named in the schema cannot be loaded
     */
    Object decodeFieldValue(byte[] slice, Class<?> declaredType, Decoded ctx)
            throws IOException, ClassNotFoundException;
}
