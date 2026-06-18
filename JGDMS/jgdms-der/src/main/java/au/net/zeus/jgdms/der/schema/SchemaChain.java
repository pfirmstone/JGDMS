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

package au.net.zeus.jgdms.der.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Helper for building and computing digests over a leaf->root Merkle chain of
 * {@link AtomicSerialSchemaRecord}s (JGDMS-STD-006 S7.8).
 *
 * <h3>Merkle chain semantics</h3>
 * Given a class hierarchy {@code Leaf extends Mid extends Root}:
 * <ul>
 *   <li>{@code Root}'s record has no {@code parentSchemaHash} (parent is {@code Object}).</li>
 *   <li>{@code Mid}'s record's {@code parentSchemaHash} = {@code SHA-256(DER(Root record))}.</li>
 *   <li>{@code Leaf}'s record's {@code parentSchemaHash} = {@code SHA-256(DER(Mid record))}.</li>
 * </ul>
 * The leaf digest therefore captures the entire hierarchy: changing any ancestor record
 * changes its own digest, which propagates upward through every descendant's
 * {@code parentSchemaHash} chain, changing each descendant's own digest in turn.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Build records without parentSchemaHash first:
 * AtomicSerialSchemaRecord root = new AtomicSerialSchemaRecord("com.example.Root", List.of(...));
 * AtomicSerialSchemaRecord mid  = new AtomicSerialSchemaRecord("com.example.Mid",  List.of(...));
 * AtomicSerialSchemaRecord leaf = new AtomicSerialSchemaRecord("com.example.Leaf", List.of(...));
 *
 * // Link and get the leaf digest in one call:
 * SchemaChain.Result result = SchemaChain.linkAndGetLeafDigest(List.of(leaf, mid, root));
 * byte[] leafDigest = result.leafDigest();
 * List<AtomicSerialSchemaRecord> linked = result.chain();
 * }</pre>
 *
 * The input list is <b>leaf-first, root-last</b> (matching S7.8 "leaf -> root" order).
 */
public final class SchemaChain {

    private SchemaChain() {
        throw new AssertionError("no instances");
    }

    /**
     * Result of a {@link #linkAndGetLeafDigest} call.
     *
     * @param chain      the fully-linked chain, leaf-first; each record's
     *                   {@code parentSchemaHash} points to the digest of the next
     *                   record in the list (except the last, which has none)
     * @param leafDigest the SHA-256 digest of the leaf (first) record's DER encoding
     */
    public record Result(List<AtomicSerialSchemaRecord> chain, byte[] leafDigest) {}

    /**
     * Links a chain of records (leaf-first, root-last) by setting each non-root
     * record's {@code parentSchemaHash} to the digest of the next record, then
     * returns the linked chain together with the leaf digest.
     *
     * <p>The input records may have any {@code parentSchemaHash} value (including none);
     * they are replaced in the returned chain. The original records are not modified.
     *
     * @param records a list of schema records, <b>leaf at index 0, root at the last index</b>
     *                (must contain at least one record)
     * @return the linked chain and the leaf digest
     * @throws NullPointerException     if {@code records} is {@code null} or contains
     *                                  {@code null} elements
     * @throws IllegalArgumentException if {@code records} is empty
     */
    public static Result linkAndGetLeafDigest(List<AtomicSerialSchemaRecord> records) {
        Objects.requireNonNull(records, "records");
        if (records.isEmpty()) {
            throw new IllegalArgumentException("records must not be empty");
        }
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i) == null) {
                throw new NullPointerException("records[" + i + "] is null");
            }
        }

        int n = records.size();
        AtomicSerialSchemaRecord[] linked = new AtomicSerialSchemaRecord[n];

        // Process root-to-leaf (right to left) so each record's parent hash is known
        // when we process its child.
        //
        // linked[n-1] = root: clear parentSchemaHash (parent is Object)
        linked[n - 1] = records.get(n - 1).withParentSchemaHash(null);

        for (int i = n - 2; i >= 0; i--) {
            // The parent of records[i] is records[i+1] (already linked).
            byte[] parentDigest = linked[i + 1].schemaDigest();
            linked[i] = records.get(i).withParentSchemaHash(parentDigest);
        }

        List<AtomicSerialSchemaRecord> resultList = List.of(linked);
        byte[] leafDigest = linked[0].schemaDigest();
        return new Result(resultList, leafDigest);
    }

    /**
     * Computes the SHA-256 digest of a single record's DER encoding.
     * Convenience alias for {@link AtomicSerialSchemaRecord#schemaDigest()}.
     *
     * @param record the record to digest
     * @return a fresh 32-byte SHA-256 digest
     * @throws NullPointerException if {@code record} is {@code null}
     */
    public static byte[] digest(AtomicSerialSchemaRecord record) {
        Objects.requireNonNull(record, "record");
        return record.schemaDigest();
    }
}
