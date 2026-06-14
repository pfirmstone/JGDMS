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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, append-only in-memory implementation of {@link SchemaRegistry}
 * (JGDMS-STD-006 §12.1).
 *
 * <h2>Append-only guarantee</h2>
 * <p>
 * A schema is stored permanently once registered. Re-registering an identical
 * byte sequence (same bytes → same digest) is idempotent: the existing entry is
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
 * <h2>isCompatible field comparison</h2>
 * <p>
 * {@link #isCompatible(byte[], byte[])} decodes both {@code AtomicSerialSchemaRecord}
 * bytes and compares their ordered field lists: B is compatible with A iff
 * {@code fields(A)} is a prefix of {@code fields(B)} (same order, same
 * {@code wireName} and {@code wireType} for every pair). B may have additional
 * trailing fields.
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
                // Root record (no parent) — chain is complete
                break;
            }
            currentDigest = parentHash;
        }
        return chain.toArray(new byte[0][]);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Decodes both schemas from the registry, then checks whether
     * {@code fields(A)} is a prefix of {@code fields(B)} (same count and order,
     * matching {@code wireName} and {@code wireType} for every pair).
     *
     * <p>Returns {@code false} if either digest is unknown.
     *
     * @throws NullPointerException if either argument is null
     */
    @Override
    public boolean isCompatible(byte[] schemaDigestA, byte[] schemaDigestB) {
        Objects.requireNonNull(schemaDigestA, "schemaDigestA");
        Objects.requireNonNull(schemaDigestB, "schemaDigestB");

        byte[] bytesA = getSchema(schemaDigestA);
        byte[] bytesB = getSchema(schemaDigestB);
        if (bytesA == null || bytesB == null) {
            return false;
        }

        List<AtomicSerialFieldDef> fieldsA = decodeFields(bytesA);
        List<AtomicSerialFieldDef> fieldsB = decodeFields(bytesB);
        if (fieldsA == null || fieldsB == null) {
            return false;
        }

        // B is forward-compatible with A iff fields(A) is a prefix of fields(B)
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
     * All 32 bytes → 64 hex characters.
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

    /**
     * Decodes the ordered field list from a raw DER-encoded
     * {@code AtomicSerialSchemaRecord}. Returns {@code null} if decoding fails.
     */
    private static List<AtomicSerialFieldDef> decodeFields(byte[] schemaBytes) {
        try {
            AtomicSerialSchemaRecord rec = AtomicSerialSchemaRecord.decode(schemaBytes);
            return rec.fields();
        } catch (DerException e) {
            return null;
        }
    }
}
