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
package org.apache.river.outrigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The immutable per-template plurality carrier for a filtered operation (design
 * memo B3 &sect;1.2/&sect;5). Built once, at admission, from an operation's
 * admitted {@link CompiledFilter}(s), and threaded to every match chokepoint the
 * operation reaches (the confirm window on the capture path; the watcher on the
 * fan-out path). Threading one object — not a bare {@code CompiledFilter} — is
 * what makes B1 &sect;6.2 per-template plurality correct <em>by construction</em>
 * rather than by vigilance.
 *
 * <h3>Structure</h3>
 * <ul>
 *   <li><b>{@code byDigest}</b> — the concrete-schema filters, keyed by the hex
 *       of their applicability {@code entrySchemaDigest}. A single-template
 *       concrete op has exactly one entry; a multi-template op has one entry per
 *       distinct template schema. Because there is exactly one filter envelope
 *       per operation (B1), two templates of the same schema map the same key to
 *       the same predicate — the {@link Builder} de-dupes by digest rather than
 *       rejecting a duplicate (&sect;5 collision note).</li>
 *   <li><b>{@code schemaLess}</b> — the null-applicability-key filters (admitted
 *       against a null / match-any template). Each applies to <em>every</em>
 *       candidate (B1 &sect;6.1 OPTION-(b)), resolved per candidate against that
 *       candidate's own v2 schema.</li>
 * </ul>
 *
 * <h3>Candidate &rarr; filter selection ({@link #applicableTo})</h3>
 * Per the ratified rule ({@code [RATIFIED Peter 2026-07-26]}, memo &sect;3/&sect;5):
 * <ol>
 *   <li>Every {@code schemaLess} filter applies (schema-less resolution).</li>
 *   <li>If a {@code byDigest} key equals the candidate's own
 *       {@code entrySchemaDigest}, that concrete filter applies with its field
 *       identity trusted by construction (digest == key).</li>
 *   <li>Otherwise (a digest mismatch — dominantly a <b>subclass entry</b> whose
 *       own chain is longer than the template it byte-matched): the candidate is
 *       resolved schema-lessly against the <em>originating</em> concrete filter —
 *       the one whose template it byte-matched. When the operation carries
 *       exactly one concrete filter that association is immediate (it IS the
 *       originating filter). When the operation carries several concrete filters
 *       of distinct schemas, the digest alone cannot recover which template
 *       produced the byte-match, so this carrier fails <b>closed</b> (excludes
 *       the candidate) rather than guess — over-exclusion is a correctness
 *       annoyance, never a security failure (memo &sect;7); the caller may pass
 *       the byte-matched template's own digest to
 *       {@link #applicableTo(byte[], byte[])} to resolve it precisely.</li>
 * </ol>
 * An empty combined list is <b>never</b> treated as "no constraint" (memo
 * &sect;5): a non-empty {@code FilterSet} that resolves to no applicable filter
 * for a candidate is the ambiguous-subclass fail-closed case above.
 *
 * @since JGDMS 4.0.0
 */
final class FilterSet {

    /** The shared no-op instance for unfiltered callers (byte-for-byte unchanged behaviour). */
    static final FilterSet EMPTY = new FilterSet(Map.of(), List.of());

    private final Map<String, CompiledFilter> byDigest;   // hex(entrySchemaDigest) -> concrete filter
    private final List<CompiledFilter> schemaLess;        // null-key filters (apply to all candidates)

    private FilterSet(Map<String, CompiledFilter> byDigest, List<CompiledFilter> schemaLess) {
        this.byDigest = byDigest;
        this.schemaLess = schemaLess;
    }

    /** @return {@code true} if this set carries no filters (an unfiltered operation). */
    boolean isEmpty() {
        return byDigest.isEmpty() && schemaLess.isEmpty();
    }

    /**
     * The filters a candidate of the given own-schema digest must satisfy, with
     * no knowledge of which template the candidate byte-matched (memo &sect;5
     * selection rule).
     *
     * @param candidateDigest the candidate's own {@code entrySchemaDigest}
     *        (the stored, pre-decode digest); {@code null}/empty is treated as
     *        "no concrete key to match" (schema-less filters still apply)
     * @return the combined all-must-pass filter list, or {@code null} to signal a
     *         fail-closed exclusion (the ambiguous-subclass case). Never an empty
     *         list for a non-empty {@code FilterSet}.
     */
    List<CompiledFilter> applicableTo(byte[] candidateDigest) {
        return applicableTo(candidateDigest, null);
    }

    /**
     * The filters a candidate must satisfy, given the applicability digest of the
     * template it byte-matched (memo &sect;3/&sect;5). {@code matchedTemplateDigest}
     * lets the multi-distinct-schema subclass case resolve precisely to the
     * originating filter instead of failing closed.
     *
     * @param candidateDigest      the candidate's own stored {@code entrySchemaDigest}
     * @param matchedTemplateDigest the applicability digest of the template the
     *        candidate byte-matched, or {@code null} if that template was
     *        schema-less / unknown to the caller
     * @return the combined all-must-pass filter list, or {@code null} to signal a
     *         fail-closed exclusion. Never empty for a non-empty {@code FilterSet}.
     */
    List<CompiledFilter> applicableTo(byte[] candidateDigest, byte[] matchedTemplateDigest) {
        if (isEmpty()) {
            return Collections.emptyList();
        }
        final List<CompiledFilter> out = new ArrayList<>(schemaLess.size() + 1);
        out.addAll(schemaLess);

        if (!byDigest.isEmpty()) {
            CompiledFilter concrete = null;
            if (candidateDigest != null && candidateDigest.length > 0) {
                concrete = byDigest.get(hex(candidateDigest));
            }
            if (concrete == null) {
                // Digest mismatch: subclass / cross-schema candidate. Resolve to the
                // ORIGINATING filter (the template the candidate byte-matched),
                // schema-lessly, per the ratified §3 amendment.
                if (matchedTemplateDigest != null && matchedTemplateDigest.length > 0) {
                    concrete = byDigest.get(hex(matchedTemplateDigest));
                }
                if (concrete == null && byDigest.size() == 1) {
                    // Exactly one concrete filter for the whole op => it IS the
                    // originating filter (single-template ops, and multi-template
                    // ops whose templates share one schema).
                    concrete = byDigest.values().iterator().next();
                }
                if (concrete == null) {
                    // Several distinct concrete schemas and no matched-template hint:
                    // cannot recover which template's predicate to apply. Fail closed
                    // rather than guess (over-exclusion is safe; §5/§7).
                    return null;
                }
            }
            out.add(concrete);
        }

        // A non-empty FilterSet always yields at least one applicable filter here
        // (schemaLess non-empty, or a concrete resolved above); an empty result
        // would be the "no constraint" fail-open §5 forbids, so guard it.
        if (out.isEmpty()) {
            return null;
        }
        return out;
    }

    // =====================================================================
    // Builder
    // =====================================================================

    /**
     * Accumulates a {@link FilterSet} from an admission loop. Each template's
     * admitted {@link CompiledFilter} is added via {@link #add}; a concrete
     * (non-null-key) filter is keyed by its digest (de-duped), a schema-less
     * (null-key) filter is appended to the schema-less overlay.
     */
    static final class Builder {
        private final Map<String, CompiledFilter> byDigest = new LinkedHashMap<>();
        private final List<CompiledFilter> schemaLess = new ArrayList<>();

        /**
         * Add one admitted filter. A duplicate digest key (a second template of
         * the same schema in one op) maps the same predicate to the same key and
         * is a harmless overwrite (memo &sect;5: one envelope per op ⇒ one
         * predicate per distinct schema), never a rejection.
         *
         * @param filter the admitted filter (must not be {@code null})
         * @return {@code this}
         */
        Builder add(CompiledFilter filter) {
            if (filter == null) throw new NullPointerException("filter");
            if (filter.isSchemaLess()) {
                schemaLess.add(filter);
            } else {
                byDigest.put(hex(filter.applicabilitySchemaDigest()), filter);
            }
            return this;
        }

        /** @return the immutable {@link FilterSet}; {@link FilterSet#EMPTY} if nothing was added. */
        FilterSet build() {
            if (byDigest.isEmpty() && schemaLess.isEmpty()) {
                return EMPTY;
            }
            return new FilterSet(
                    Collections.unmodifiableMap(new LinkedHashMap<>(byDigest)),
                    Collections.unmodifiableList(new ArrayList<>(schemaLess)));
        }
    }

    /** Convenience: a single-filter {@link FilterSet} for the single-template ops. */
    static FilterSet of(CompiledFilter filter) {
        return new Builder().add(filter).build();
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** Lowercase hex, matching EntryRepV2Codec.decode's schemaTable keying. */
    private static String hex(byte[] b) {
        char[] c = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            c[i * 2] = HEX[(b[i] >> 4) & 0xF];
            c[i * 2 + 1] = HEX[b[i] & 0xF];
        }
        return new String(c);
    }
}
