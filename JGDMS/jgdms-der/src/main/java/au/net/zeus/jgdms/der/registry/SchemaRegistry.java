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

package au.net.zeus.jgdms.der.registry;

/**
 * Schema Registry Service interface per JGDMS-STD-006 S12.2.
 *
 * <p>Stores and distributes {@link au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord}
 * instances indexed by their SHA-256 digest. All parameters and return values are
 * {@code byte[]}, {@link String}, or primitive types -- no service-specific objects
 * (S12.1 "Bytes and primitives only" constraint).
 *
 * <h2>Design constraints (S12.1)</h2>
 * <ul>
 *   <li><b>Append-only.</b> Schemas are never deleted. A schema registered with a given
 *       digest is immutable and permanent.</li>
 *   <li><b>Bytes and primitives only.</b> This interface is callable before the caller
 *       has loaded any service-specific classes.</li>
 *   <li><b>Idempotent registration.</b> Registering an already-known schema returns the
 *       existing digest without modifying any state.</li>
 * </ul>
 */
public interface SchemaRegistry {

    /**
     * Registers a schema. Returns the SHA-256 digest of the DER-encoded
     * {@code AtomicSerialSchemaRecord} (the schema version). Idempotent:
     * registering an already-known schema (same bytes) returns the existing digest
     * without creating a duplicate entry.
     *
     * <p>Append-only: registering bytes that are different from any previously
     * registered bytes creates a new, independent entry -- both the new digest and
     * any previous digests remain independently retrievable.
     *
     * @param schemaRecordBytes DER-encoded {@code AtomicSerialSchemaRecord}; must not be null
     * @return SHA-256(DER(AtomicSerialSchemaRecord)), exactly 32 bytes
     */
    byte[] register(byte[] schemaRecordBytes);

    /**
     * Retrieves a schema by its digest. Returns {@code null} if no schema with
     * the given digest has been registered.
     *
     * @param schemaDigest 32-byte SHA-256 digest
     * @return DER-encoded {@code AtomicSerialSchemaRecord}, or {@code null} if absent
     */
    byte[] getSchema(byte[] schemaDigest);

    /**
     * Retrieves the full schema chain for a leaf class schema digest.
     *
     * <p>Returns DER-encoded {@code AtomicSerialSchemaRecord} bytes for each class
     * in the hierarchy from leaf to root, in leaf-first order. Walking the chain:
     * starting from the leaf record, each record's {@code parentSchemaHash} is the
     * digest of the next record in the returned array.
     *
     * <p>Returns {@code null} if the leaf schema digest is not registered. Returns a
     * single-element array if the leaf has no {@code parentSchemaHash} (i.e. the parent
     * is {@code Object}). The walk terminates when a record has no {@code parentSchemaHash}
     * or when a parent digest is not found in the registry (partial chain is returned up
     * to that point).
     *
     * @param leafSchemaDigest 32-byte SHA-256 digest of the leaf class schema
     * @return array of DER-encoded {@code AtomicSerialSchemaRecord} bytes, leaf first;
     *         {@code null} if the leaf digest is not registered
     */
    byte[][] getSchemaChain(byte[] leafSchemaDigest);

    /**
     * Tests lossless forward compatibility over the FULL hierarchy chain
     * (STD-006 v0.13 S12.2): can data encoded under leaf schema A be decoded
     * under leaf schema B without dropping any field A declared and without any
     * A-declared field falling back to a {@code GetArg} default?
     *
     * <p>Note that mere decodability is not the question -- the {@code GetArg}
     * layer makes ANY two schemas "decodable" via defaults (STD-006 S11.8). This
     * method answers the stronger, useful question: is the migration lossless?
     *
     * <p>Chain-wise rule: for EVERY record in A's chain (leaf to root, followed
     * via {@code parentSchemaHash}), B's chain must contain a record with the same
     * {@code className} whose ordered field list starts with A's record's field
     * list (same {@code (wireName, wireType)} pairs, same order). Classes present
     * in B but not in A are permitted (their fields receive defaults). Classes
     * present in A but not in B mean A-data would be stored-but-unconsumed: not
     * lossless, returns {@code false}.
     *
     * <p>Returns {@code false} if either digest is unknown or either chain cannot
     * be completely retrieved from this registry -- fail-secure: an unjudgeable
     * chain is never reported compatible.
     *
     * <p>The pre-v0.13 contract compared only the two leaf records; that rule gave
     * wrong answers when the hierarchy itself evolved (STD-006 S11.4/S11.6).
     *
     * @param schemaDigestA 32-byte digest of the earlier/narrower leaf schema
     * @param schemaDigestB 32-byte digest of the later/wider leaf schema
     * @return {@code true} if B is losslessly forward-compatible with A
     */
    boolean isCompatible(byte[] schemaDigestA, byte[] schemaDigestB);
}
