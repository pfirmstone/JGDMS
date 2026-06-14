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
 * Schema Registry Service interface per JGDMS-STD-006 §12.2.
 *
 * <p>Stores and distributes {@link au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord}
 * instances indexed by their SHA-256 digest. All parameters and return values are
 * {@code byte[]}, {@link String}, or primitive types — no service-specific objects
 * (§12.1 "Bytes and primitives only" constraint).
 *
 * <h2>Design constraints (§12.1)</h2>
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
     * registered bytes creates a new, independent entry — both the new digest and
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
     * Tests forward compatibility: is schema B a superset of schema A?
     *
     * <p>Returns {@code true} if B's fields START WITH exactly A's fields in the same
     * order and types — i.e., data encoded with schema A can be decoded with schema B
     * (B may add trailing fields which, if requested by the receiver, receive
     * {@code GetArg} defaults).
     *
     * <p>Formally: let {@code fields(X)} denote the ordered field list of the
     * {@code AtomicSerialSchemaRecord} identified by digest {@code X}. Then:
     * <pre>
     *   isCompatible(A, B) == true
     *     iff fields(A) is a prefix of fields(B)
     *     (same size and order, every (wireName, wireType) pair matches)
     * </pre>
     *
     * <p>If either digest is unknown to the registry the method returns {@code false}.
     *
     * @param schemaDigestA 32-byte digest of the earlier/narrower schema
     * @param schemaDigestB 32-byte digest of the later/wider schema
     * @return {@code true} if B is forward-compatible with A (B's fields are a superset
     *         starting with A's fields in the same order)
     */
    boolean isCompatible(byte[] schemaDigestA, byte[] schemaDigestB);
}
