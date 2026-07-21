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

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;

import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * Maximum number of {@link AtomicSerialSchemaRecord} SEQUENCEs in one encoded
     * schema chain (STD-006 S4.5 {@code maxChainRecords}, <b>inclusive</b>: exactly
     * {@code maxChainRecords} records are accepted and {@code maxChainRecords + 1}
     * is rejected). Enforced by {@link #decodeChain} during the chain-decode loop
     * at every chain decode site -- the top-level {@code MarshalledInstanceRecord}
     * and every nested {@code @AtomicSerial} field record's embedded chain -- as a
     * structural resource ceiling: without it the loop is bounded only by
     * {@code maxInputBytes}. The deepest real {@code @AtomicSerial} hierarchy in
     * the repository is 4 records; 64 is deliberate headroom, not a target.
     */
    public static final int MAX_CHAIN_RECORDS = 64;

    /**
     * Maximum cumulative encoded byte length of one schema chain (STD-006 S4.5
     * {@code maxChainBytes}, <b>inclusive</b>: a chain of exactly
     * {@code maxChainBytes} bytes is accepted and one of {@code maxChainBytes + 1}
     * bytes is rejected). Enforced by {@link #decodeChain} during the chain-decode
     * loop (metered as records accumulate, not checked once at entry). This
     * deliberately tightens the base-admissible set: a single record at the
     * S4.5 {@code maxFields}/{@code className} ceilings could alone exceed this
     * bound, but no real class remotely approaches it (largest real
     * {@code serialForm()} in the repository is ~11 fields; real chains are under
     * 2 KiB).
     */
    public static final int MAX_CHAIN_BYTES = 65536;

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
     * Decodes an encoded schema chain -- the concatenation of complete
     * {@link AtomicSerialSchemaRecord} DER SEQUENCEs in leaf-first order (STD-006
     * S7.8 "Schema chain encoding") -- back into the ordered record list, enforcing
     * the STD-006 S4.5 chain ceilings and the S7.8 chain-integrity checks. This is
     * the <b>single</b> chain-decode path: both the top-level
     * {@code MarshalledInstanceRecord.decodeSchemaChain()} (P1) site and the nested
     * {@code @AtomicSerial} field record site in {@code ObjectCodec.decodeNested}
     * (P2) delegate here, so the two sites cannot diverge.
     *
     * <h4>Checks (all hard decode rejects -- fail-secure, never skip/default)</h4>
     * <ol>
     *   <li><b>{@code maxChainRecords} ceiling</b> ({@link #MAX_CHAIN_RECORDS},
     *       S4.5, inclusive): metered during the decode loop as records accumulate;
     *       the {@code (MAX_CHAIN_RECORDS + 1)}-th record throws before any further
     *       record is parsed.</li>
     *   <li><b>{@code maxChainBytes} ceiling</b> ({@link #MAX_CHAIN_BYTES}, S4.5,
     *       inclusive): the cumulative consumed byte count is metered during the
     *       loop; crossing the ceiling throws before any further record is
     *       parsed.</li>
     *   <li><b>Adjacent-pair cross-check</b> (S7.8): each record's
     *       {@code parentSchemaHash} must equal the next record's
     *       {@code schemaDigest()}; a non-terminal record with no
     *       {@code parentSchemaHash} is a broken chain.</li>
     *   <li><b>Chain completeness</b> (S7.8): the terminal record MUST NOT carry a
     *       {@code parentSchemaHash} -- a dangling parent hash is a truncated
     *       chain. Without this check a truncated chain {@code {leaf}} and the full
     *       chain {@code {leaf, parent, root}} would both yield the same leaf
     *       digest from different bytes, breaking digest-to-bytes injectivity.</li>
     *   <li><b>Non-empty</b>: an empty chain is rejected.</li>
     * </ol>
     *
     * @param chainBytes the concatenated chain bytes (leaf-first record SEQUENCEs)
     * @param context    caller name used as the error-message prefix (e.g.
     *                   {@code "MarshalledInstanceRecord"})
     * @return ordered list of schema records, leaf-first, at least one element
     * @throws DerException if the bytes are malformed, a ceiling is breached, the
     *                      adjacent-pair cross-check fails, the chain is truncated,
     *                      or the chain is empty
     */
    public static List<AtomicSerialSchemaRecord> decodeChain(byte[] chainBytes, String context)
            throws DerException {
        Objects.requireNonNull(chainBytes, "chainBytes");
        Objects.requireNonNull(context, "context");

        List<AtomicSerialSchemaRecord> records = new ArrayList<>();
        DerReader reader = new DerReader(chainBytes);

        while (reader.hasMore()) {
            AtomicSerialSchemaRecord rec = AtomicSerialSchemaRecord.decode(reader);
            records.add(rec);

            // S4.5 maxChainRecords -- metered at the accumulating frame (G10): the
            // ceiling-breaching record is the last one parsed; nothing beyond it is.
            if (records.size() > MAX_CHAIN_RECORDS) {
                throw new DerException(
                        context + ": schema chain record count exceeds maxChainRecords ("
                        + MAX_CHAIN_RECORDS + ", STD-006 S4.5) -- rejected");
            }
            // S4.5 maxChainBytes -- cumulative consumed bytes, metered during the loop.
            if (reader.position() > MAX_CHAIN_BYTES) {
                throw new DerException(
                        context + ": schema chain cumulative byte length " + reader.position()
                        + " exceeds maxChainBytes (" + MAX_CHAIN_BYTES
                        + ", STD-006 S4.5) -- rejected");
            }
            // S7.8 adjacent-pair cross-check, applied as each pair completes:
            // records[i].parentSchemaHash must equal records[i+1].schemaDigest()
            // (records[i] is the child, records[i+1] is its parent in the chain).
            int i = records.size() - 2;
            if (i >= 0) {
                AtomicSerialSchemaRecord child = records.get(i);
                byte[] childParentHash = child.parentSchemaHashOrNull();
                if (childParentHash == null) {
                    throw new DerException(
                            context + ": schema chain broken at index " + i
                            + " -- record for '" + child.className()
                            + "' has no parentSchemaHash but is not the last record in the chain");
                }
                if (!Arrays.equals(childParentHash, rec.schemaDigest())) {
                    throw new DerException(
                            context + ": schema chain cross-check failed at index " + i
                            + " -- record[" + i + "].parentSchemaHash does not match "
                            + "record[" + (i + 1) + "].schemaDigest() for class '"
                            + rec.className() + "'");
                }
            }
        }

        if (records.isEmpty()) {
            throw new DerException(context + ": schema chain is empty");
        }

        // S7.8 chain completeness: the terminal record must be a root (no
        // parentSchemaHash). A dangling parent hash means the chain was truncated.
        AtomicSerialSchemaRecord terminal = records.get(records.size() - 1);
        if (terminal.parentSchemaHashOrNull() != null) {
            throw new DerException(
                    context + ": schema chain is truncated -- terminal record for '"
                    + terminal.className()
                    + "' carries a parentSchemaHash but no parent record follows"
                    + " (STD-006 S7.8 chain completeness) -- rejected");
        }

        return records;
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
