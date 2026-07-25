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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.eval.CandidateProjection;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.entry.EntryRepV2Codec;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;

/**
 * The class-free candidate projection (SOW Part&nbsp;B, unit&nbsp;B2). Given a
 * candidate entry's on-wire v2 body ({@code EntryRepV2Body}) and nothing else, it
 * exposes exactly the predicate-referenced field values a CEL evaluation observes
 * ({@link CandidateProjection}), resolving field <em>names</em> against the
 * candidate's <b>own</b> v2 schema chain. It is the candidate-side mirror of the
 * template-side {@link FilterAdmission} seam: where admission type-checks a filter
 * against a <em>template's</em> own schema, projection reads a <em>candidate's</em>
 * own fields — both class-free, both fail-closed, both driven by the one
 * {@code EntryRepV2Codec} / {@code jgdms-der} decode machinery
 * ({@code DerSchemaChainView} / {@code ObjectCodec.decodeToFieldMap}), never a
 * second lenient scanner (design memo B1 &sect;6).
 *
 * <h3>The five contract points (B1 &sect;6)</h3>
 * <ol>
 *   <li><b>Class-free.</b> No {@code Class.forName}, no constructor, no {@code
 *       check(GetArg)}, no deserialize-to-object, and no {@code ClassLoader}
 *       anywhere near this. The entry class is never named to the JVM. Scalar
 *       field values are decoded from their already-canonical v2 slice bytes as
 *       self-describing DER object-stream items with {@link ResolutionContext#NONE}
 *       (a {@code null} loader); a nested {@code @AtomicSerial} object field is
 *       decoded via {@link ObjectCodec#decodeToFieldMap} over that value's own
 *       embedded schema chain — the exact class-free field-map decode the SOW
 *       points to.</li>
 *   <li><b>Fail-closed.</b> Any canonical-decode failure, undecodable/trailing
 *       bytes, non-v2 / non-{@code ATOMIC_DER} body, or unexpected error while
 *       building the projection yields a projection whose {@link #isUndecodable()}
 *       is {@code true} — the evaluator maps that to {@code CANDIDATE_UNDECODABLE}
 *       and fail-closed-excludes the candidate (design memo B1 &sect;6, counted by
 *       B3 in {@code filter.failClosedExclusions}). Never a partial or guessed
 *       value; never an exception escaping into the (total) evaluator.</li>
 *   <li><b>absent-vs-undeclared.</b> A field <em>declared</em> in the candidate's
 *       schema but <em>absent</em> on the wire (the {@code [0]} absent {@code
 *       FieldSlice} — an entry-null field) is distinguished from a field
 *       <em>not declared at all</em> in this candidate's schema:
 *       {@link #declaresField} answers <em>schema presence</em> (from the schema
 *       chain, independent of the value), while a declared-absent field's
 *       {@link #fieldValue} is {@link CelValue.NullV} ("present, null", STD-011
 *       &sect;4.6). Thus B3's OPTION&nbsp;(b) ruling holds: an undeclared
 *       referenced field ({@code declaresField == false}) is a fail-closed
 *       <em>exclusion</em>, whereas a declared-null field is the value
 *       {@code null}.</li>
 *   <li><b>entrySchemaDigest-keyed applicability.</b> {@link #entrySchemaDigest()}
 *       surfaces the candidate's own {@code entrySchemaDigest} so B3 can apply the
 *       &sect;6.1 rule (a non-null-key filter applies only to a candidate whose
 *       digest equals the key).</li>
 *   <li><b>Read only the referenced fields.</b> Only the fields the predicate
 *       references (by name) are decoded; an unrelated field is never touched, so
 *       a candidate is never excluded for a fault in a field the query does not
 *       read.</li>
 * </ol>
 *
 * <h3>Scope (B2 judgment call, documented)</h3>
 * <p>Scalar fields ({@code bool}/{@code int}/{@code double}/{@code string}/{@code
 * bytes}, plus wire-null&nbsp;&rarr;&nbsp;{@code null}) are projected fully. A
 * referenced field that is a nested {@code @AtomicSerial} object is projected one
 * level deep via {@link ObjectCodec#decodeToFieldMap} (its own scalar sub-fields,
 * exposed as a nested {@link CandidateProjection} inside a {@link
 * CelValue.ObjectV}), which supports {@code has(obj)} and a single {@code obj.sub}
 * step. A referenced field of any other kind (enum / collection / array / {@code
 * char} / {@code any}), or a deeper object step this bounded descent cannot
 * resolve class-free, is treated fail-closed — consistent with {@code
 * DerSchemaChainView.nestedSchema()} deferring deep nesting to dynamic evaluation.
 * The verifier only ever admits scalar-typed field comparisons against a concrete
 * schema (a wrong type is rejected loudly at admission); a schema-less filter
 * defers unknown types, and this projection fail-closed-excludes what it cannot
 * soundly and class-freely produce.
 *
 * <p>Immutable and side-effect-free once built (STD-011 &sect;9.6): the referenced
 * values are decoded eagerly at construction, so {@link #fieldValue} is a pure
 * selection that never decodes and never throws — the fail-closed gate is
 * {@link #isUndecodable()}, which the evaluator checks before every field access.
 *
 * @since JGDMS 4.0.0
 */
public final class EntryProjection implements CandidateProjection {

    /** {@code FieldSlice} CHOICE tags (EntryRepV2Codec grammar): {@code [0]} absent NULL, {@code [1]} value SEQUENCE. */
    private static final Tag SLICE_ABSENT = new Tag(Tag.CLASS_CONTEXT, false, 0);
    private static final Tag SLICE_VALUE  = new Tag(Tag.CLASS_CONTEXT, true, 1);

    /** The single fail-closed sentinel: an undecodable candidate. */
    private static final EntryProjection UNDECODABLE = new EntryProjection();

    private final boolean undecodable;
    private final byte[] entrySchemaDigest;                     // 32 bytes; null iff undecodable
    private final List<String> namespaceChain;                  // leaf-first; empty iff undecodable
    private final Map<String, Set<String>> declaredByClass;     // className -> declared field names (SCHEMA presence)
    private final Map<String, Map<String, CelValue>> decoded;   // className -> fieldName -> decoded value (referenced only)

    /** The fail-closed sentinel constructor. */
    private EntryProjection() {
        this.undecodable = true;
        this.entrySchemaDigest = null;
        this.namespaceChain = List.of();
        this.declaredByClass = Map.of();
        this.decoded = Map.of();
    }

    private EntryProjection(byte[] entrySchemaDigest,
                            List<String> namespaceChain,
                            Map<String, Set<String>> declaredByClass,
                            Map<String, Map<String, CelValue>> decoded) {
        this.undecodable = false;
        this.entrySchemaDigest = entrySchemaDigest;
        this.namespaceChain = namespaceChain;
        this.declaredByClass = declaredByClass;
        this.decoded = decoded;
    }

    // =====================================================================
    // Factories
    // =====================================================================

    /**
     * Projects a candidate v2 body for the fields a given predicate references.
     * The referenced-field set is derived from {@code predicate} via {@link
     * #referencedFieldNames(ExprNode)}, so only those fields are decoded (contract
     * point&nbsp;5). This is the factory B3 uses: {@code
     * EntryProjection.project(candidate.bodyBytes(), compiledFilter.expr())}.
     *
     * @param candidateBody the candidate entry's on-wire {@code EntryRepV2Body}
     *                      bytes (e.g. from {@code EntryRep.bodyBytes()})
     * @param predicate     the verified predicate AST whose field references drive
     *                      the projection
     * @return a fail-closed projection (never {@code null}); {@link
     *         #isUndecodable()} is {@code true} on any decode failure
     */
    public static EntryProjection project(byte[] candidateBody, ExprNode predicate) {
        return projectFields(candidateBody, referencedFieldNames(predicate));
    }

    /**
     * Projects a candidate v2 body for an explicit set of referenced field names.
     * Fail-closed: any structural decode failure, or a decode failure of any one
     * referenced field, yields {@link #isUndecodable()} {@code == true}.
     *
     * @param candidateBody        the candidate's on-wire {@code EntryRepV2Body} bytes
     * @param referencedFieldNames the field names the predicate references (only
     *                             these are decoded); may be empty
     * @return a fail-closed projection (never {@code null})
     */
    public static EntryProjection projectFields(byte[] candidateBody, Set<String> referencedFieldNames) {
        if (candidateBody == null || referencedFieldNames == null) {
            return UNDECODABLE;
        }
        try {
            // 1. Fail-closed structural decode + full amendment-§A.9 validation.
            EntryRepV2Codec.DecodedBody db = EntryRepV2Codec.decode(candidateBody);
            // 2. The candidate's OWN leaf-first schema chain + entrySchemaDigest.
            EntryRepV2Codec.EntrySchemaChain esc =
                    EntryRepV2Codec.decodeEntrySchemaChain(candidateBody);
            List<AtomicSerialSchemaRecord> chain = esc.chain(); // leaf-first

            List<String> nsChain = new ArrayList<>(chain.size());
            Map<String, Set<String>> declaredByClass = new LinkedHashMap<>();
            for (AtomicSerialSchemaRecord rec : chain) {
                nsChain.add(rec.className());
                Set<String> names = new LinkedHashSet<>();
                for (AtomicSerialFieldDef f : rec.fields()) {
                    names.add(f.wireName());
                }
                declaredByClass.put(rec.className(), Collections.unmodifiableSet(names));
            }

            // 3. (className, fieldName) -> global slice index, in FieldComparator
            //    order: superclass-first (root-first = reverse of the leaf-first
            //    chain), then declared order within each class. This reproduces the
            //    positional slice order EntryRepV2Codec.encode laid down.
            byte[][] slices = db.sliceBytes();
            boolean[] absent = db.absent();
            Map<String, byte[]> schemaTable = db.schemaTable();
            Map<String, Map<String, Integer>> indexByClass = new LinkedHashMap<>();
            int idx = 0;
            for (int c = chain.size() - 1; c >= 0; c--) { // root-first
                AtomicSerialSchemaRecord rec = chain.get(c);
                Map<String, Integer> perClass = new LinkedHashMap<>();
                for (AtomicSerialFieldDef f : rec.fields()) {
                    perClass.put(f.wireName(), idx++);
                }
                indexByClass.put(rec.className(), perClass);
            }
            if (idx != slices.length) {
                // decode()'s A.8 field-count guard already proves this; defensive.
                return UNDECODABLE;
            }

            // 4. Eagerly decode ONLY the referenced fields. A decode failure of any
            //    one referenced field fails the whole candidate closed (a candidate
            //    whose referenced bytes will not decode canonically is a no-match).
            Map<String, Map<String, CelValue>> decoded = new LinkedHashMap<>();
            for (AtomicSerialSchemaRecord rec : chain) {
                Map<String, Integer> perClass = indexByClass.get(rec.className());
                Map<String, CelValue> vals = null;
                for (AtomicSerialFieldDef f : rec.fields()) {
                    if (!referencedFieldNames.contains(f.wireName())) continue;
                    int i = perClass.get(f.wireName());
                    CelValue v = absent[i]
                            ? CelValue.NullV.INSTANCE
                            : decodeSliceValue(slices[i], schemaTable);
                    if (vals == null) {
                        vals = new LinkedHashMap<>();
                        decoded.put(rec.className(), vals);
                    }
                    vals.put(f.wireName(), v);
                }
            }

            return new EntryProjection(
                    esc.entrySchemaDigest(),
                    Collections.unmodifiableList(nsChain),
                    Collections.unmodifiableMap(declaredByClass),
                    decoded);
        } catch (Throwable t) {
            // Any canonical-decode failure / undecodable-or-unmappable value /
            // unexpected error => fail-closed no-match (never partial, never thrown).
            return UNDECODABLE;
        }
    }

    // =====================================================================
    // CandidateProjection
    // =====================================================================

    @Override
    public boolean isUndecodable() {
        return undecodable;
    }

    @Override
    public List<String> namespaceChain() {
        return namespaceChain;
    }

    @Override
    public boolean declaresField(String className, String fieldName) {
        Set<String> names = declaredByClass.get(className);
        return names != null && names.contains(fieldName);
    }

    @Override
    public CelValue fieldValue(String className, String fieldName) {
        Map<String, CelValue> vals = decoded.get(className);
        CelValue v = (vals == null) ? null : vals.get(fieldName);
        if (v == null) {
            // The evaluator only calls this for a resolved, declared, REFERENCED
            // field (all of which are eagerly decoded above). Reaching here means
            // the caller resolved a field it did not declare referenced -- a wiring
            // bug (an incomplete referenced-field set), never attacker input. Fail
            // loudly rather than fabricate a value.
            throw new IllegalStateException(
                    "EntryProjection: field '" + className + "#" + fieldName
                    + "' was not projected (not in the referenced-field set)");
        }
        return v;
    }

    // =====================================================================
    // Applicability key (contract point 4; not part of CandidateProjection)
    // =====================================================================

    /**
     * The candidate's own {@code entrySchemaDigest} — B3's fail-closed
     * applicability key (design memo B1 &sect;6.1). Returns {@code null} when
     * {@link #isUndecodable()} is {@code true} (an undecodable candidate is
     * excluded before applicability is ever consulted).
     *
     * @return a copy of the 32-byte digest, or {@code null} if undecodable
     */
    public byte[] entrySchemaDigest() {
        return (entrySchemaDigest == null) ? null : entrySchemaDigest.clone();
    }

    // =====================================================================
    // Referenced-field extraction (drives contract point 5)
    // =====================================================================

    /**
     * The set of field names a predicate references anywhere in its AST — every
     * {@link ExprNode.SelectorStep} name across every {@link ExprNode.FieldRef}
     * (including {@code has(...)} targets and {@code in} field lists). This is the
     * set {@link #project(byte[], ExprNode)} decodes; an over-approximation by name
     * is safe (a name a candidate's schema does not declare is simply never
     * decoded).
     *
     * @param predicate the predicate AST (must not be {@code null})
     * @return an immutable set of referenced field names
     */
    public static Set<String> referencedFieldNames(ExprNode predicate) {
        Objects.requireNonNull(predicate, "predicate");
        Set<String> out = new HashSet<>();
        collect(predicate, out);
        return Collections.unmodifiableSet(out);
    }

    private static void collect(ExprNode node, Set<String> out) {
        switch (node) {
            case ExprNode.FieldRef f -> collectSteps(f, out);
            case ExprNode.Has h -> collectSteps(h.target(), out);
            case ExprNode.ListLit l -> { for (ExprNode e : l.elements()) collect(e, out); }
            case ExprNode.Not n -> collect(n.operand(), out);
            case ExprNode.Neg n -> collect(n.operand(), out);
            case ExprNode.And a -> { collect(a.left(), out); collect(a.right(), out); }
            case ExprNode.Or o -> { collect(o.left(), out); collect(o.right(), out); }
            case ExprNode.Eq e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Ne e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Lt e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Le e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Gt e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Ge e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Add e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Sub e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Mul e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Div e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.Mod e -> { collect(e.left(), out); collect(e.right(), out); }
            case ExprNode.In in -> {
                collect(in.needle(), out);
                switch (in.listOperand()) {
                    case ExprNode.InListOperand.LiteralList ll -> collect(ll.list(), out);
                    case ExprNode.InListOperand.FieldList fl -> collectSteps(fl.field(), out);
                }
            }
            case ExprNode.Cond c -> {
                collect(c.condition(), out);
                collect(c.thenBranch(), out);
                collect(c.elseBranch(), out);
            }
            case ExprNode.Call c -> { for (ExprNode a : c.arguments()) collect(a, out); }
            // Literals reference no field.
            case ExprNode.LitBool ignored -> { }
            case ExprNode.LitInt ignored -> { }
            case ExprNode.LitDouble ignored -> { }
            case ExprNode.LitString ignored -> { }
            case ExprNode.LitBytes ignored -> { }
            case ExprNode.LitNull ignored -> { }
        }
    }

    private static void collectSteps(ExprNode.FieldRef ref, Set<String> out) {
        for (ExprNode.SelectorStep step : ref.steps()) {
            switch (step) {
                case ExprNode.SelectorStep.Unqual u -> out.add(u.name());
                case ExprNode.SelectorStep.Qual q -> out.add(q.fieldName());
            }
        }
    }

    // =====================================================================
    // Class-free per-slice value decode
    // =====================================================================

    /**
     * Decodes one {@code FieldSlice}'s value class-free. An absent {@code [0]}
     * slice never reaches here (handled by the {@code absent[]} flag). A value
     * {@code [1]} slice is either a self-describing scalar object-stream item
     * (empty {@code valueSchemaDigest}) or a nested {@code @AtomicSerial} object
     * (32-byte digest keying a chain in the body's {@code schemaTable}), the latter
     * projected via {@link ObjectCodec#decodeToFieldMap}. Throws on anything it
     * cannot soundly and class-freely map — the caller turns that into a
     * fail-closed no-match.
     */
    private static CelValue decodeSliceValue(byte[] sliceBytes, Map<String, byte[]> schemaTable)
            throws Exception {
        DerReader r = new DerReader(sliceBytes);
        Tag tag = r.peekTag();
        if (SLICE_ABSENT.equals(tag)) {
            // Defensive: absent is normally routed via absent[]; a bare absent here is null.
            return CelValue.NullV.INSTANCE;
        }
        if (!SLICE_VALUE.equals(tag)) {
            throw new DerException("EntryProjection: FieldSlice tag must be [0]/[1], got " + tag);
        }
        r.readTlvHeader(); // step into the [1] IMPLICIT SEQUENCE content
        byte[] valueSchemaDigest = r.readOctetString();
        byte[] payload = r.readOctetString();
        if (valueSchemaDigest.length == 0) {
            // Self-describing scalar object-stream item: class-free (JDK scalar types
            // only; ResolutionContext.NONE => null loader, no codebase class load).
            Object o = readObjectStreamItem(payload);
            return toScalarCelValue(o);
        }
        // Nested @AtomicSerial value: decode class-free via decodeToFieldMap over the
        // value's own embedded schema chain (from the body's schemaTable).
        byte[] chainBytes = schemaTable.get(hex(valueSchemaDigest));
        if (chainBytes == null) {
            throw new DerException("EntryProjection: valueSchemaDigest not in schemaTable");
        }
        List<AtomicSerialSchemaRecord> nestedChain =
                SchemaChain.decodeChain(chainBytes, "EntryProjection.nested");
        SchemaChain.Result nestedResult = SchemaChain.linkAndGetLeafDigest(nestedChain);
        LinkedHashMap<String, Map<String, Object>> fieldMap =
                ObjectCodec.decodeToFieldMap(nestedResult, payload); // never loads a class
        return new CelValue.ObjectV(FieldMapProjection.of(nestedChain, fieldMap));
    }

    /** Reads one self-describing DER object-stream item class-free (no codebase loader). */
    private static Object readObjectStreamItem(byte[] payload)
            throws java.io.IOException, ClassNotFoundException {
        DerMarshalInputStream in = DerMarshalInputStream.recordLevelCapture(
                new ByteArrayInputStream(payload), ResolutionContext.NONE);
        return in.readObject(Object.class);
    }

    /** Maps a class-free decoded scalar Java value to its {@link CelValue}; throws for any non-scalar. */
    static CelValue toScalarCelValue(Object v) {
        if (v == null) return CelValue.NullV.INSTANCE;
        if (v instanceof Boolean b) return new CelValue.BoolV(b);
        if (v instanceof Byte x) return new CelValue.IntV(x.longValue());
        if (v instanceof Short x) return new CelValue.IntV(x.longValue());
        if (v instanceof Integer x) return new CelValue.IntV(x.longValue());
        if (v instanceof Long x) return new CelValue.IntV(x);
        if (v instanceof Float f) return new CelValue.DoubleV(f.doubleValue());
        if (v instanceof Double d) return new CelValue.DoubleV(d);
        if (v instanceof Character ch) return new CelValue.IntV(ch); // codepoint; char has no scalar CelType
        if (v instanceof String s) return new CelValue.StringV(s);
        if (v instanceof byte[] by) return new CelValue.BytesV(by.clone());
        // enum / collection / array / nested object / anything else: not a scalar
        // this projection can produce class-free => fail-closed at the call site.
        throw new IllegalStateException(
                "EntryProjection: field value type " + v.getClass().getName()
                + " is not a projectable scalar");
    }

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

    // =====================================================================
    // Nested @AtomicSerial object projection (one level, class-free)
    // =====================================================================

    /**
     * A class-free projection over a nested {@code @AtomicSerial} object's own
     * {@code className -> (fieldName -> value)} field-map (from {@link
     * ObjectCodec#decodeToFieldMap}). Supports {@code has(obj)} and a single
     * {@code obj.scalar} selector step. Its own scalar sub-fields are mapped
     * eagerly; a sub-field of a non-scalar kind (a deeper {@code @AtomicSerial}
     * object, collection, enum, ...) that {@code decodeToFieldMap} produced but
     * this bounded descent cannot map to a scalar renders the nested projection
     * {@link #isUndecodable()} — the evaluator then fail-closes at that selector
     * step (sound; deep nesting is deferred, matching {@code
     * DerSchemaChainView.nestedSchema()}).
     */
    private static final class FieldMapProjection implements CandidateProjection {

        private final boolean undecodable;
        private final List<String> namespaceChain;              // leaf-first
        private final Map<String, Set<String>> declaredByClass; // schema presence (all decoded fields)
        private final Map<String, Map<String, CelValue>> values;

        private FieldMapProjection(boolean undecodable,
                                   List<String> namespaceChain,
                                   Map<String, Set<String>> declaredByClass,
                                   Map<String, Map<String, CelValue>> values) {
            this.undecodable = undecodable;
            this.namespaceChain = namespaceChain;
            this.declaredByClass = declaredByClass;
            this.values = values;
        }

        static FieldMapProjection of(List<AtomicSerialSchemaRecord> leafFirstChain,
                                     LinkedHashMap<String, Map<String, Object>> fieldMap) {
            try {
                // decodeToFieldMap returns superclass-first; the projection wants leaf-first.
                List<String> nsRootFirst = new ArrayList<>(fieldMap.keySet());
                List<String> nsChain = new ArrayList<>(nsRootFirst);
                Collections.reverse(nsChain);

                Map<String, Set<String>> declaredByClass = new LinkedHashMap<>();
                Map<String, Map<String, CelValue>> values = new LinkedHashMap<>();
                for (Map.Entry<String, Map<String, Object>> e : fieldMap.entrySet()) {
                    // decode is strict (STD-006 §3.9): every schema field of the class is
                    // present in the map, so its key set is the class's declared-field set.
                    Set<String> names = new LinkedHashSet<>(e.getValue().keySet());
                    declaredByClass.put(e.getKey(), Collections.unmodifiableSet(names));
                    Map<String, CelValue> perClass = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> fe : e.getValue().entrySet()) {
                        perClass.put(fe.getKey(), toScalarCelValue(fe.getValue()));
                    }
                    values.put(e.getKey(), perClass);
                }
                return new FieldMapProjection(false,
                        Collections.unmodifiableList(nsChain),
                        Collections.unmodifiableMap(declaredByClass),
                        values);
            } catch (RuntimeException notScalar) {
                // A sub-field this bounded one-level descent cannot map class-free =>
                // the nested projection is undecodable (fail-closed at the next step).
                return new FieldMapProjection(true, List.of(), Map.of(), Map.of());
            }
        }

        @Override public boolean isUndecodable() { return undecodable; }
        @Override public List<String> namespaceChain() { return namespaceChain; }
        @Override public boolean declaresField(String className, String fieldName) {
            Set<String> names = declaredByClass.get(className);
            return names != null && names.contains(fieldName);
        }
        @Override public CelValue fieldValue(String className, String fieldName) {
            Map<String, CelValue> perClass = values.get(className);
            CelValue v = (perClass == null) ? null : perClass.get(fieldName);
            if (v == null) {
                throw new IllegalStateException(
                        "EntryProjection: nested field '" + className + "#" + fieldName
                        + "' is not present");
            }
            return v;
        }
    }
}
