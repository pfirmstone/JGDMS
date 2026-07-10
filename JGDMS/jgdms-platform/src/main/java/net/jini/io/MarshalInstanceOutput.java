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

package net.jini.io;

import java.io.ObjectOutput;

/**
 * Output seam used by {@link MarshalledInstance} to encode a contained object.
 *
 * <p>Schema-aware widening (JGDMS-STD-008 sec.13): after {@link #writeObject} and
 * {@link #flush}, a {@code MarshalInstanceOutput} reports the encoded object's
 * embedded schema, the schema digest, and a self-describing {@code payloadFormat}
 * identifier. {@link MarshalledInstance} captures these into first-class serial-form
 * fields so a receiver can dispatch to the matching codec by format (via
 * {@link MarshalFactoryProvider}) without subclassing. The default implementations
 * describe the legacy Java-Object-Serialization (JOSS) path: no schema and
 * {@code payloadFormat == }{@link MarshalledInstance#FORMAT_JOSS}. A schema-bearing
 * codec (e.g. the JGDMS-STD-006/ATOMIC-DER codec) overrides them.
 *
 * @author peter
 */
public interface MarshalInstanceOutput extends ObjectOutput, ObjectStreamContext {
    public boolean hadAnnotations();

    /**
     * The embedded schema describing the just-written object's wire form, or an
     * empty array for formats that carry no schema (e.g. JOSS).
     *
     * @return the schema bytes; never {@code null}. Empty for the JOSS default.
     */
    default byte[] getSchemaBytes() {
        return new byte[0];
    }

    /**
     * The 32-byte SHA-256 digest of the leaf schema record (STD-006 sec.7.8), exposed
     * for the sec.12.4 fast-path comparison without parsing the payload, or an empty
     * array for formats that carry no schema.
     *
     * @return the schema digest; never {@code null}. Empty for the JOSS default.
     */
    default byte[] getSchemaDigest() {
        return new byte[0];
    }

    /**
     * The self-describing payload-format identifier (STD-008 sec.13.1), used by
     * {@link MarshalledInstance#get} to select the decoding {@link MarshalFactory}
     * via {@link MarshalFactoryProvider}.
     *
     * @return the format id; never {@code null}. Defaults to
     *         {@link MarshalledInstance#FORMAT_JOSS}.
     */
    default String getPayloadFormat() {
        return MarshalledInstance.FORMAT_JOSS;
    }
}
