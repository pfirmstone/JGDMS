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
 * Implements the S12.4 schema resolution decision tree.
 *
 * <p>When a receiver needs to decode a {@link MarshalledInstanceRecord} payload,
 * the resolution steps (in priority order) are:
 *
 * <ol>
 *   <li><b>Local digest match (fast path).</b>
 *       Extract {@code schemaDigest} from the record and compare it to
 *       {@code SHA-256(DER(localSerialForm()))} for the receiver's class. If they
 *       match, use the local schema directly. The registry is NOT invoked.</li>
 *   <li><b>Embedded schema present (primary fallback).</b>
 *       If the digests differ, decode the embedded {@code schemaBytes} from the
 *       record and use that chain.</li>
 *   <li><b>Registry lookup (synthetic edge case).</b>
 *       If the embedded schema is absent or corrupt (should not occur for conforming
 *       data per S7.8), query {@code registry.getSchema(digest)}. If a schema is
 *       found, use it (as a single-record chain).</li>
 *   <li><b>Default fallback.</b>
 *       If the registry also misses, use the local schema chain. Absent fields will
 *       receive {@code GetArg} defaults. No exception is thrown.</li>
 * </ol>
 *
 * <h2>Registry call invariant</h2>
 * <p>
 * The registry is NEVER called in step 1 (local digest match). It is only called in
 * step 3 when the embedded schema is absent or corrupt. Tests verify the registry spy
 * records zero calls on a local-digest match (Task 7.3 acceptance criterion).
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
     * The four S12.4 resolution branches, in priority order.
     */
    public enum Branch {
        /** Step 1: local serialForm() digest matches the record's digest. */
        LOCAL_MATCH,
        /** Step 2: embedded schema bytes used (primary fallback, S12.4 step 4a). */
        EMBEDDED,
        /** Step 3: registry lookup returned a schema (S12.4 step 4b, first sub-case). */
        REGISTRY,
        /** Step 4: registry miss; fell back to local schema + GetArg defaults. */
        DEFAULT_LOCAL
    }

    /**
     * Resolves the schema chain for decoding the given {@link MarshalledInstanceRecord}.
     *
     * <p>Follows the S12.4 decision tree (see class-level Javadoc). The registry is
     * only consulted if both the local digest and the embedded schema are unavailable
     * (step 3); on a local digest match (step 1) the registry is NOT invoked.
     *
     * @param rec            the {@link MarshalledInstanceRecord} to decode
     * @param receiverClass  the receiver's {@code @AtomicSerial} class; provides both
     *                       the local schema (via {@code serialForm()}) and the local
     *                       digest for step-1 comparison
     * @param registry       the {@link SchemaRegistry} to consult at step 3 (may be a
     *                       spy or no-op in tests; must not be null)
     * @return a {@link Result} holding the resolved chain and the branch taken
     * @throws NullPointerException if any argument is null
     */
    public static Result resolve(MarshalledInstanceRecord rec,
                                  Class<?> receiverClass,
                                  SchemaRegistry registry) {
        Objects.requireNonNull(rec,           "rec");
        Objects.requireNonNull(receiverClass, "receiverClass");
        Objects.requireNonNull(registry,      "registry");

        byte[] recordDigest = rec.schemaDigest();

        // ----------------------------------------------------------------
        // Step 1: local digest match -- fast path, no registry call
        // ----------------------------------------------------------------
        SchemaChain.Result localChain = tryGenerateLocalChain(receiverClass);
        if (localChain != null) {
            if (Arrays.equals(localChain.leafDigest(), recordDigest)) {
                return new Result(localChain, Branch.LOCAL_MATCH);
            }
        }

        // ----------------------------------------------------------------
        // Step 2: embedded schema present -- primary fallback
        // ----------------------------------------------------------------
        SchemaChain.Result embeddedChain = tryDecodeEmbeddedChain(rec);
        if (embeddedChain != null) {
            return new Result(embeddedChain, Branch.EMBEDDED);
        }

        // ----------------------------------------------------------------
        // Step 3: embedded absent/corrupt -- query registry (synthetic path)
        // ----------------------------------------------------------------
        byte[] registryBytes = registry.getSchema(recordDigest);
        if (registryBytes != null) {
            SchemaChain.Result registryChain = tryBuildSingleRecordChain(registryBytes);
            if (registryChain != null) {
                return new Result(registryChain, Branch.REGISTRY);
            }
        }

        // ----------------------------------------------------------------
        // Step 4: registry miss -- fall back to local schema + GetArg defaults
        // (no exception; absent fields receive defaults via DerGetArg)
        // ----------------------------------------------------------------
        if (localChain != null) {
            return new Result(localChain, Branch.DEFAULT_LOCAL);
        }
        // Absolute last resort: cannot even build a local chain (class has no
        // serialForm). Return an empty single-record chain so callers at least
        // get all-defaults rather than a null-chain crash.
        AtomicSerialEmptyChain emptyFallback = buildEmptyFallbackChain(receiverClass);
        return new Result(emptyFallback.chain(), Branch.DEFAULT_LOCAL);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Attempts to generate the receiver's local schema chain.
     * Returns {@code null} if the class lacks a valid {@code serialForm()}.
     */
    private static SchemaChain.Result tryGenerateLocalChain(Class<?> receiverClass) {
        try {
            return SchemaGenerator.generateChain(receiverClass);
        } catch (DerException e) {
            return null;
        }
    }

    /**
     * Attempts to decode the embedded schema chain from the record's
     * {@code schemaBytes}. Returns {@code null} if absent or malformed.
     */
    private static SchemaChain.Result tryDecodeEmbeddedChain(MarshalledInstanceRecord rec) {
        // A zero-length schemaBytes is treated as absent.
        byte[] sb = rec.schemaBytes();
        if (sb == null || sb.length == 0) {
            return null;
        }
        try {
            return rec.decodeSchemaChainAsResult();
        } catch (DerException e) {
            return null;
        }
    }

    /**
     * Builds a single-record {@link SchemaChain.Result} from a DER-encoded
     * {@link au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord}. Returns
     * {@code null} if the bytes are malformed.
     */
    private static SchemaChain.Result tryBuildSingleRecordChain(byte[] schemaBytes) {
        try {
            au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord rec =
                    au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord.decode(schemaBytes);
            return au.net.zeus.jgdms.der.schema.SchemaChain.linkAndGetLeafDigest(
                    java.util.List.of(rec));
        } catch (DerException e) {
            return null;
        }
    }

    /**
     * Builds a trivial single-record chain with no fields as an absolute fallback
     * (when the class has no recognisable {@code serialForm()}). All {@code arg.get()}
     * calls will return defaults.
     */
    private static AtomicSerialEmptyChain buildEmptyFallbackChain(Class<?> cls) {
        au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord emptyRec =
                new au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord(
                        cls.getName(), (byte[]) null, java.util.List.of());
        SchemaChain.Result chain = au.net.zeus.jgdms.der.schema.SchemaChain.linkAndGetLeafDigest(
                java.util.List.of(emptyRec));
        return () -> chain;
    }

    /** Functional alias used only in {@link #buildEmptyFallbackChain}. */
    @FunctionalInterface
    private interface AtomicSerialEmptyChain {
        SchemaChain.Result chain();
    }
}
