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
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, append-only in-memory implementation of {@link SchemaRegistry}
 * (JGDMS-STD-006 S12.1).
 *
 * <h2>Append-only guarantee</h2>
 * <p>
 * A schema is stored permanently once registered. Re-registering an identical
 * byte sequence (same bytes -> same digest) is idempotent: the existing entry is
 * returned and no second copy is created. Re-registering different bytes yields a
 * different digest and creates an independent entry; neither digest overwrites the
 * other.
 *
 * <h2>Thread safety</h2>
 * <p>
 * All state is held in a {@link ConcurrentHashMap} keyed by hex-encoded digest.
 * {@link ConcurrentHashMap#putIfAbsent} provides the compare-and-store atomicity
 * needed for idempotent registration without external locking.
 *
 * <h2>isCompatible: chain-wise (STD-006 v0.13 S12.2)</h2>
 * <p>
 * {@link #isCompatible(byte[], byte[])} judges lossless forward compatibility over
 * the FULL hierarchy chain, not just the leaf record: for every record in A's
 * chain, B's chain must contain a record with the same {@code className} whose
 * ordered field list starts with A's record's field list (same {@code wireName}
 * and {@code wireType}, same order). Classes present only in B receive
 * {@code GetArg} defaults and are permitted; classes present only in A mean data
 * loss and fail the test. An unknown digest or an incompletely retrievable chain
 * returns {@code false} (fail-secure: an unjudgeable chain is not reported
 * compatible). The pre-v0.13 leaf-only prefix rule gave wrong answers across the
 * S11.4/S11.6 hierarchy evolutions.
 */
public final class InMemorySchemaRegistry implements SchemaRegistry {

    /**
     * Key = hex-encoded digest string (stable, non-null, HashMap-friendly).
     * Value = defensive copy of the raw DER bytes.
     */
    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    // =========================================================================
    // SchemaRegistry implementation
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Computes {@code SHA-256(schemaRecordBytes)}, then calls
     * {@link ConcurrentHashMap#putIfAbsent}: if a previous entry exists under
     * the same key (same digest), it is kept and the new bytes are discarded
     * (idempotent). If no entry exists, the new bytes are stored.
     *
     * @throws NullPointerException if {@code schemaRecordBytes} is null
     */
    @Override
    public byte[] register(byte[] schemaRecordBytes) {
        Objects.requireNonNull(schemaRecordBytes, "schemaRecordBytes");
        byte[] digest = sha256(schemaRecordBytes);
        String key = hexKey(digest);
        // Append-only, idempotent: only store if not already present
        store.putIfAbsent(key, schemaRecordBytes.clone());
        return digest.clone();
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if {@code schemaDigest} is null
     */
    @Override
    public byte[] getSchema(byte[] schemaDigest) {
        Objects.requireNonNull(schemaDigest, "schemaDigest");
        String key = hexKey(schemaDigest);
        byte[] bytes = store.get(key);
        return (bytes == null) ? null : bytes.clone();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Walks the chain starting from the leaf: for each record, decodes it to
     * read its {@code parentSchemaHash}. If present, that hash is used as the next
     * lookup key. Walking terminates when either (a) the parent hash is absent
     * (the record's parent is {@code Object}), or (b) the parent hash points to a
     * digest not in the registry.
     *
     * @throws NullPointerException if {@code leafSchemaDigest} is null
     */
    @Override
    public byte[][] getSchemaChain(byte[] leafSchemaDigest) {
        Objects.requireNonNull(leafSchemaDigest, "leafSchemaDigest");
        byte[] currentDigest = leafSchemaDigest;
        List<byte[]> chain = new ArrayList<>();
        while (true) {
            byte[] schemaBytes = getSchema(currentDigest);
            if (schemaBytes == null) {
                // Not found: if this is the first lookup, return null (leaf not registered);
                // otherwise return the partial chain collected so far (parent not in registry).
                if (chain.isEmpty()) {
                    return null;
                }
                break;
            }
            chain.add(schemaBytes);

            // Decode the record to find the parent hash
            byte[] parentHash = decodeParentHashOrNull(schemaBytes);
            if (parentHash == null) {
                // Root record (no parent) -- chain is complete
                break;
            }
            currentDigest = parentHash;
        }
        return chain.toArray(new byte[0][]);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Chain-wise (v0.13): retrieves the complete chain for both leaf digests,
     * indexes B's chain by {@code className}, and requires every record in A's
     * chain to have a same-named counterpart in B whose ordered field list starts
     * with A's (same {@code wireName} and {@code wireType} for every pair).
     *
     * <p>Returns {@code false} if either digest is unknown, either chain cannot be
     * completely retrieved (a {@code parentSchemaHash} points outside the
     * registry), or any record fails to decode — fail-secure.
     *
     * @throws NullPointerException if either argument is null
     */
    @Override
    public boolean isCompatible(byte[] schemaDigestA, byte[] schemaDigestB) {
        Objects.requireNonNull(schemaDigestA, "schemaDigestA");
        Objects.requireNonNull(schemaDigestB, "schemaDigestB");

        List<AtomicSerialSchemaRecord> chainA = retrieveCompleteChain(schemaDigestA);
        List<AtomicSerialSchemaRecord> chainB = retrieveCompleteChain(schemaDigestB);
        if (chainA == null || chainB == null) {
            return false;
        }

        Map<String, AtomicSerialSchemaRecord> byNameB = new HashMap<>();
        for (AtomicSerialSchemaRecord recB : chainB) {
            byNameB.put(recB.className(), recB);
        }

        for (AtomicSerialSchemaRecord recA : chainA) {
            AtomicSerialSchemaRecord recB = byNameB.get(recA.className());
            if (recB == null) {
                // Class present in A but absent in B: A-data for this namespace
                // would be stored-but-unconsumed under B -- not lossless.
                return false;
            }
            if (!isFieldPrefix(recA.fields(), recB.fields())) {
                return false;
            }
        }
        return true;
    }

    /**
     * True iff {@code fieldsA} is a prefix of {@code fieldsB} (same order, every
     * {@code (wireName, wireType)} pair equal).
     */
    private static boolean isFieldPrefix(List<AtomicSerialFieldDef> fieldsA,
                                         List<AtomicSerialFieldDef> fieldsB) {
        if (fieldsA.size() > fieldsB.size()) {
            return false;
        }
        for (int i = 0; i < fieldsA.size(); i++) {
            if (!fieldsA.get(i).equals(fieldsB.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Walks the chain from {@code leafDigest} to the root, decoding each record.
     * Returns {@code null} if the leaf is unknown, any parent digest is missing
     * from the registry (incomplete chain), or any record fails to decode.
     */
    private List<AtomicSerialSchemaRecord> retrieveCompleteChain(byte[] leafDigest) {
        byte[] currentDigest = leafDigest;
        List<AtomicSerialSchemaRecord> chain = new ArrayList<>();
        while (true) {
            byte[] schemaBytes = getSchema(currentDigest);
            if (schemaBytes == null) {
                return null;
            }
            AtomicSerialSchemaRecord rec;
            try {
                rec = AtomicSerialSchemaRecord.decode(schemaBytes);
            } catch (DerException e) {
                return null;
            }
            chain.add(rec);
            byte[] parentHash = rec.parentSchemaHashOrNull();
            if (parentHash == null) {
                return chain;
            }
            currentDigest = parentHash;
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Computes SHA-256 of the given bytes.
     *
     * @throws IllegalStateException if SHA-256 is unavailable (should never happen on JDK 21+)
     */
    private static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Encodes a digest as a lowercase hex string for use as a {@code Map} key.
     * All 32 bytes -> 64 hex characters.
     */
    private static String hexKey(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Decodes the {@code parentSchemaHash} field from a raw DER-encoded
     * {@code AtomicSerialSchemaRecord} without propagating exceptions. Returns
     * {@code null} if the record has no parent hash or if decoding fails.
     */
    private static byte[] decodeParentHashOrNull(byte[] schemaBytes) {
        try {
            AtomicSerialSchemaRecord rec = AtomicSerialSchemaRecord.decode(schemaBytes);
            return rec.parentSchemaHashOrNull();
        } catch (DerException e) {
            return null;
        }
    }
}
