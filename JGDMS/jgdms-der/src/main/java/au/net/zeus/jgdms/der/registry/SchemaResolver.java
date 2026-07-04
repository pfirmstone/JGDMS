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

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;

import java.util.Arrays;
import java.util.Objects;

/**
 * Implements the S12.4 schema resolution decision tree (STD-006 S12.4,
 * fail-secure).
 *
 * <p>When a receiver needs to decode a {@link MarshalledInstanceRecord} payload:
 *
 * <ol>
 *   <li><b>Decode and verify the embedded schema (mandatory).</b>
 *       {@code schemaBytes} is decoded, the {@code parentSchemaHash} chain is
 *       cross-checked, and the record's {@code schemaDigest} field is verified
 *       against the embedded leaf record
 *       ({@link MarshalledInstanceRecord#decodeSchemaChainAsResult()}). Absent,
 *       empty, undecodable, chain-inconsistent, or digest-mismatched schema bytes
 *       are a hard <b>reject</b> ({@link DerException}) -- there is no local-schema
 *       or registry fallback (design principle 6: no permissive fallback).</li>
 *   <li><b>Local comparison.</b> The verified digest is compared with
 *       {@code SHA-256(DER(localSerialForm()))}. A match means the local and
 *       embedded schemas are byte-identical ({@link Branch#LOCAL_MATCH}, the
 *       allocation-free fast path); a mismatch means the embedded schema is used
 *       and the {@code GetArg} layer absorbs divergence
 *       ({@link Branch#EMBEDDED}).</li>
 * </ol>
 *
 * <h2>No registry at decode time</h2>
 * <p>
 * The embedded schema is unconditionally required by S7.8: data without a usable
 * embedded schema is non-conforming and rejected. A {@link SchemaRegistry} is
 * never consulted at decode time to recover a missing or corrupt schema; its
 * purposes are caching, sharing, archival, and offline compatibility queries --
 * never decode-time recovery for malformed instances.
 */
public final class SchemaResolver {

    private SchemaResolver() {
        throw new AssertionError("no instances");
    }

    /**
     * Holds the resolution result: the chosen schema chain and the branch taken.
     *
     * @param chain  the resolved schema chain (leaf-first), suitable for passing to
     *               {@link au.net.zeus.jgdms.der.object.ObjectCodec#decodeHierarchy}
     * @param branch the S12.4 branch that was selected
     */
    public record Result(SchemaChain.Result chain, Branch branch) {}

    /**
     * The two S12.4 resolution branches. An absent, corrupt, or digest-mismatched
     * embedded schema is not a branch -- it is a rejection ({@link DerException}).
     */
    public enum Branch {
        /**
         * The verified embedded digest matches the local {@code serialForm()}
         * digest: local and embedded schema are byte-identical (S3.9 case (a)).
         */
        LOCAL_MATCH,
        /**
         * Digests differ (S3.9 case (b)/(c)): the embedded schema is used and
         * {@code GetArg} absorbs the divergence.
         */
        EMBEDDED
    }

    /**
     * Resolves the schema chain for decoding the given {@link MarshalledInstanceRecord}.
     *
     * <p>Follows the v0.13 S12.4 decision tree (see class-level Javadoc). Both
     * branches return the same <em>logical</em> schema; {@link Branch#LOCAL_MATCH}
     * additionally tells the caller that its local {@code serialForm()} is
     * byte-identical to the wire schema (S3.9 case (a) fast path).
     *
     * @param rec            the {@link MarshalledInstanceRecord} to decode
     * @param receiverClass  the receiver's {@code @AtomicSerial} class; provides the
     *                       local digest for the fast-path comparison (a class
     *                       without a valid {@code serialForm()} simply never takes
     *                       the fast path)
     * @return a {@link Result} holding the resolved chain and the branch taken
     * @throws DerException         if the embedded schema is absent, undecodable,
     *                              chain-inconsistent, or does not match the
     *                              record's {@code schemaDigest} (fail-secure
     *                              reject; S7.8 / S12.4)
     * @throws NullPointerException if any argument is null
     */
    public static Result resolve(MarshalledInstanceRecord rec,
                                  Class<?> receiverClass)
            throws DerException {
        Objects.requireNonNull(rec,           "rec");
        Objects.requireNonNull(receiverClass, "receiverClass");

        // ----------------------------------------------------------------
        // Step 1: decode + verify the embedded schema. Any failure here is a
        // rejection -- decodeSchemaChainAsResult() throws on empty/undecodable
        // bytes, a broken parentSchemaHash chain, or a schemaDigest mismatch.
        // ----------------------------------------------------------------
        SchemaChain.Result embeddedChain = rec.decodeSchemaChainAsResult();

        // ----------------------------------------------------------------
        // Step 2: compare the verified digest with the local serialForm() digest.
        // ----------------------------------------------------------------
        SchemaChain.Result localChain = tryGenerateLocalChain(receiverClass);
        if (localChain != null
                && Arrays.equals(localChain.leafDigest(), embeddedChain.leafDigest())) {
            // Byte-identical schemas; using the locally generated chain avoids
            // holding the embedded copy.
            return new Result(localChain, Branch.LOCAL_MATCH);
        }
        return new Result(embeddedChain, Branch.EMBEDDED);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Attempts to generate the receiver's local schema chain.
     * Returns {@code null} if the class lacks a valid {@code serialForm()} --
     * which only forgoes the fast path; the embedded schema still decodes the data.
     */
    private static SchemaChain.Result tryGenerateLocalChain(Class<?> receiverClass) {
        try {
            return SchemaGenerator.generateChain(receiverClass);
        } catch (DerException e) {
            return null;
        }
    }
}
