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

import au.net.zeus.jgdms.cel.ast.ExprNode;

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
 *       resolved schema-lessly against the shared predicate expression. By the
 *       <b>one-envelope-per-op invariant</b> (B1, enforced at construction by
 *       {@link Builder#build()}'s shared-expression guard) every filter in this
 *       set carries the SAME predicate expression, so any concrete filter is a
 *       correct representative: if the caller supplies the byte-matched template's
 *       digest that filter is used precisely, otherwise the first concrete filter
 *       is used (identical expression). The referenced field names are then
 *       resolved against the candidate's <em>own</em> v2 schema at evaluation time
 *       — present-and-unambiguous ⇒ evaluated against the candidate's own value;
 *       absent-or-ambiguous ⇒ fail-closed exclusion downstream (memo &sect;3/&sect;5
 *       amendment, {@code [RATIFIED Peter 2026-07-26]}). This carrier no longer
 *       fails closed on a bare digest mismatch — the prior "&ge;2 distinct schemas
 *       ⇒ exclude" rule was a fail-<em>open</em> that silently over-excluded every
 *       legitimate subclass result at a multi-template site.</li>
 * </ol>
 * An empty combined list is <b>never</b> treated as "no constraint" (memo
 * &sect;5); a non-empty {@code FilterSet} always resolves to at least one
 * applicable filter (a {@code schemaLess} filter, or a concrete representative).
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
                // Digest mismatch: dominantly a SUBCLASS candidate — it byte-matched a
                // (superclass) template on that template's own declared fields, but its
                // own schema chain is longer, so its digest matches no byDigest key.
                // Prefer the byte-matched template's own filter when the caller knows it.
                if (matchedTemplateDigest != null && matchedTemplateDigest.length > 0) {
                    concrete = byDigest.get(hex(matchedTemplateDigest));
                }
                if (concrete == null) {
                    // No exact hint (every multi-template capture/fan-out site passes
                    // null). By the one-envelope-per-op invariant (B1) — enforced at
                    // construction by Builder.build()'s shared-expression guard — every
                    // filter in this set carries the SAME predicate expression, so ANY
                    // concrete filter is a correct representative to resolve SCHEMA-LESS
                    // against the candidate's own schema chain (§3/§5 amendment). Pick
                    // the first deterministically. This REPLACES the former ">=2 distinct
                    // schemas => return null" behaviour, which was a fail-open: it
                    // silently over-excluded every legitimate subclass result at a
                    // multi-template site. A genuinely unresolvable referenced field is
                    // still caught fail-closed DOWNSTREAM at projection/eval
                    // (ABSENT_FIELD / AMBIGUOUS_FIELD), not by dropping the candidate here.
                    concrete = byDigest.values().iterator().next();
                }
            }
            out.add(concrete);
        }

        // A non-empty FilterSet always yields at least one applicable filter here
        // (schemaLess non-empty, or a concrete representative resolved above); an empty
        // result would be the "no constraint" fail-open §5 forbids, so guard it
        // defensively (unreachable for a non-empty set after the §5 amendment).
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
            assertOneEnvelope();
            return new FilterSet(
                    Collections.unmodifiableMap(new LinkedHashMap<>(byDigest)),
                    Collections.unmodifiableList(new ArrayList<>(schemaLess)));
        }

        /**
         * Guards the <b>one-envelope-per-op invariant</b> (B1): every
         * {@link CompiledFilter} admitted for a single filtered operation is the
         * SAME filter envelope admitted against each template, so all carry the
         * SAME predicate expression. &sect;5's subclass resolution
         * ({@link FilterSet#applicableTo(byte[], byte[])}) relies on this — on a
         * digest mismatch it applies <em>any</em> filter's expression to the
         * candidate. A future multi-envelope change that broke this would silently
         * misapply one template's predicate to another template's candidates (the
         * &sect;6.2 / N-6 hazard). We fail LOUDLY at construction rather than let
         * that be a silent evaluation bug (G7: construction beats a check). Cheap:
         * a structural {@link ExprNode#equals} over a bounded AST, once per op.
         */
        private void assertOneEnvelope() {
            ExprNode ref = null;
            for (CompiledFilter f : byDigest.values()) { ref = f.expr(); break; }
            if (ref == null) {
                for (CompiledFilter f : schemaLess) { ref = f.expr(); break; }
            }
            if (ref == null) return;
            for (CompiledFilter f : byDigest.values()) {
                if (!ref.equals(f.expr())) throw multiEnvelope();
            }
            for (CompiledFilter f : schemaLess) {
                if (!ref.equals(f.expr())) throw multiEnvelope();
            }
        }

        private static IllegalStateException multiEnvelope() {
            return new IllegalStateException(
                    "FilterSet invariant violated: a filtered operation must carry"
                    + " exactly ONE filter envelope, so every CompiledFilter must share"
                    + " the same predicate expression (B1 one-envelope-per-op; §6.2/N-6)."
                    + " Multiple distinct expressions in one FilterSet would silently"
                    + " misapply one template's predicate to another template's candidates.");
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
