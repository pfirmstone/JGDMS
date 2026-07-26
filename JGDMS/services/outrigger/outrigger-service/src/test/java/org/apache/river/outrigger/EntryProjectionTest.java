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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import net.jini.core.entry.Entry;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.outrigger.proxy.EntryRep;

import au.net.zeus.jgdms.cel.CelError;
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.eval.Evaluator;
import au.net.zeus.jgdms.der.entry.EntryRepV2Codec;

/**
 * Unit B2 — the class-free candidate projection ({@link EntryProjection}). Proves
 * the five contract points (design memo B1 §6): class-free field reads, fail-closed
 * decode, the absent-vs-undeclared distinction, entrySchemaDigest-keyed
 * applicability, and reading only the referenced fields. Field values are decoded
 * from a candidate's own on-wire v2 body against its own v2 schema, never by
 * loading the entry class.
 */
public class EntryProjectionTest {

    // ---- Real reflective entry (its class IS on the classpath) --------------

    /** Two String fields a, b (canonical order a, b). */
    public static class Doc implements Entry {
        public String a;
        public String b;
        public Doc() {}
        public Doc(String a, String b) { this.a = a; this.b = b; }
    }

    // ---- A nested @AtomicSerial value + an Entry that holds one -------------

    /** A minimal nested @AtomicSerial object (STD-008 write contract). */
    @AtomicSerial
    public static final class Point {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("x", int.class),
                new AtomicSerial.SerialForm("y", int.class),
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, Point o) throws IOException {
            arg.put("x", o.x);
            arg.put("y", o.y);
            arg.writeArgs();
        }
        private final int x;
        private final int y;
        public Point(int x, int y) { this.x = x; this.y = y; }
        public Point(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.x = arg.get("x", 0);
            this.y = arg.get("y", 0);
        }
    }

    /** An Entry whose single field is an @AtomicSerial nested object. */
    public static class Located implements Entry {
        public Point where;
        public Located() {}
        public Located(Point where) { this.where = where; }
    }

    // ---- helpers ------------------------------------------------------------

    /** A fabricated v2 body whose "entry class" name is NOT a real class — the
     *  class-absent candidate (mirrors demo2 "match without the class"). */
    private static byte[] ghostBody(String className, String[] names,
                                    Class<?>[] types, Object[] values) throws Exception {
        return EntryRepV2Codec.encodeSerialEntry(className, names, types, values).body();
    }

    private static ExprNode.FieldRef ref(String... steps) {
        return new ExprNode.FieldRef(
                Arrays.stream(steps)
                      .<ExprNode.SelectorStep>map(ExprNode.SelectorStep.Unqual::new)
                      .toList());
    }

    private static CelError errorOf(EvalOutcome o) {
        assertTrue("expected an error outcome, got " + o, o instanceof EvalOutcome.Error);
        return ((EvalOutcome.Error) o).code();
    }

    private static CelValue valueOf(EvalOutcome o) {
        assertTrue("expected a value outcome, got " + o, o instanceof EvalOutcome.Value);
        return ((EvalOutcome.Value) o).value();
    }

    // =====================================================================
    // 1. Referenced-field extraction from a REAL v2 candidate
    // =====================================================================

    @Test
    public void extractsReferencedScalarFromRealV2Candidate() throws Exception {
        byte[] body = new EntryRep(new Doc("hello", "world")).bodyBytes();
        EntryProjection p = EntryProjection.project(body, ref("a"));
        assertFalse(p.isUndecodable());
        // Resolve via the evaluator (no hard-coded class name).
        EvalOutcome o = new Evaluator().evaluate(ref("a"), p);
        assertEquals(new CelValue.StringV("hello"), valueOf(o));
        // And directly, by the candidate's own namespace.
        assertTrue(p.declaresField(Doc.class.getName(), "a"));
        assertEquals(new CelValue.StringV("hello"), p.fieldValue(Doc.class.getName(), "a"));
    }

    // =====================================================================
    // 2. Class-free proof — the candidate's class is ABSENT (never loadable)
    // =====================================================================

    @Test
    public void projectsWithoutLoadingTheEntryClass() throws Exception {
        // "com.absent.Ghost" is not a class on this (or any) classpath.
        final String ghost = "com.absent.Ghost";
        try {
            Class.forName(ghost);
            fail("test premise broken: " + ghost + " must NOT be loadable");
        } catch (ClassNotFoundException expected) {
            // good — the class truly is absent.
        }
        byte[] body = ghostBody(ghost,
                new String[] { "name", "count" },
                new Class<?>[] { String.class, Integer.class },
                new Object[] { "widget", 42 });

        EntryProjection p = EntryProjection.project(body,
                new ExprNode.And(ref("name"), ref("count")));
        // Decoded fine WITHOUT the class existing => class-free.
        assertFalse(p.isUndecodable());
        assertEquals(List.of(ghost), p.namespaceChain());
        assertEquals(new CelValue.StringV("widget"), p.fieldValue(ghost, "name"));
        assertEquals(new CelValue.IntV(42), p.fieldValue(ghost, "count"));
    }

    // =====================================================================
    // 3. Fail-closed on truncated / garbage / non-canonical / non-v2 bytes
    // =====================================================================

    @Test
    public void failsClosedOnGarbageBytes() {
        EntryProjection p = EntryProjection.projectFields(
                new byte[] { 1, 2, 3, 4, 5 }, Set.of("a"));
        assertTrue(p.isUndecodable());
        assertNull(p.entrySchemaDigest());
    }

    @Test
    public void failsClosedOnTruncatedBody() throws Exception {
        byte[] body = new EntryRep(new Doc("x", "y")).bodyBytes();
        byte[] truncated = Arrays.copyOf(body, body.length - 1);
        assertTrue(EntryProjection.projectFields(truncated, Set.of("a")).isUndecodable());
    }

    @Test
    public void failsClosedOnTrailingBytes() throws Exception {
        byte[] body = new EntryRep(new Doc("x", "y")).bodyBytes();
        byte[] trailing = Arrays.copyOf(body, body.length + 1); // extra 0x00 after the body SEQUENCE
        assertTrue(EntryProjection.projectFields(trailing, Set.of("a")).isUndecodable());
    }

    @Test
    public void failsClosedOnNullBody() {
        assertTrue(EntryProjection.projectFields(null, Set.of("a")).isUndecodable());
    }

    @Test
    public void undecodableCandidateIsCandidateUndecodableAtEval() {
        // The evaluator maps an undecodable projection to CANDIDATE_UNDECODABLE
        // (fail-closed exclusion), never a match and never an escaping exception.
        EntryProjection p = EntryProjection.projectFields(new byte[] { 9, 9, 9 }, Set.of("a"));
        EvalOutcome o = new Evaluator().evaluate(
                new ExprNode.Eq(ref("a"), new ExprNode.LitString("x")), p);
        assertEquals(CelError.CANDIDATE_UNDECODABLE, errorOf(o));
    }

    // =====================================================================
    // 4. absent (declared, wire-null) vs undeclared — the §6.1 OPTION (b) crux
    // =====================================================================

    @Test
    public void distinguishesDeclaredNullFromUndeclared() throws Exception {
        final String ghost = "com.absent.Ghost";
        // Field a present ("hi"); field b DECLARED but wire-null; field c NOT declared.
        byte[] body = ghostBody(ghost,
                new String[] { "a", "b" },
                new Class<?>[] { String.class, String.class },
                new Object[] { "hi", null });

        EntryProjection p = EntryProjection.projectFields(body, Set.of("a", "b", "c"));
        assertFalse(p.isUndecodable());

        // b is DECLARED (schema presence) even though its value is null...
        assertTrue("declared-null field must be declared", p.declaresField(ghost, "b"));
        // ...and its value is NullV ("present, null"), never Java null.
        assertEquals(CelValue.NullV.INSTANCE, p.fieldValue(ghost, "b"));

        // c is NOT declared at all in this candidate's schema.
        assertFalse("undeclared field must not be declared", p.declaresField(ghost, "c"));
    }

    @Test
    public void hasDistinguishesDeclaredNullFromUndeclaredAtEval() throws Exception {
        final String ghost = "com.absent.Ghost";
        byte[] body = ghostBody(ghost,
                new String[] { "a", "b" },
                new Class<?>[] { String.class, String.class },
                new Object[] { "hi", null });
        Evaluator ev = new Evaluator();

        // has(a): declared + non-null   -> true
        assertEquals(new CelValue.BoolV(true),
                valueOf(ev.evaluate(new ExprNode.Has(ref("a")),
                        EntryProjection.project(body, new ExprNode.Has(ref("a"))))));
        // has(b): declared but wire-null -> false (§6.8: present-null yields false)
        assertEquals(new CelValue.BoolV(false),
                valueOf(ev.evaluate(new ExprNode.Has(ref("b")),
                        EntryProjection.project(body, new ExprNode.Has(ref("b"))))));
        // has(c): undeclared -> false (resolveStep ABSENT)
        assertEquals(new CelValue.BoolV(false),
                valueOf(ev.evaluate(new ExprNode.Has(ref("c")),
                        EntryProjection.project(body, new ExprNode.Has(ref("c"))))));

        // The load-bearing difference for OPTION (b): a VALUE reference to the
        // undeclared c is ABSENT_FIELD (fail-closed at eval), whereas a value
        // reference to the declared-null b is the value NullV, not an error.
        assertEquals(CelError.ABSENT_FIELD,
                errorOf(ev.evaluate(ref("c"), EntryProjection.project(body, ref("c")))));
        assertEquals(CelValue.NullV.INSTANCE,
                valueOf(ev.evaluate(ref("b"), EntryProjection.project(body, ref("b")))));
    }

    // =====================================================================
    // 5. Wrong-type field — projection is faithful; the evaluator TYPE_MISMATCHes
    // =====================================================================

    @Test
    public void wrongTypeFieldYieldsTypeMismatchAtEval() throws Exception {
        final String ghost = "com.absent.Ghost";
        // a is an int-valued field; the predicate compares it to a string.
        byte[] body = ghostBody(ghost,
                new String[] { "a" },
                new Class<?>[] { Integer.class },
                new Object[] { 7 });
        ExprNode pred = new ExprNode.Eq(ref("a"), new ExprNode.LitString("x"));
        EntryProjection p = EntryProjection.project(body, pred);
        // The projection faithfully returns the actual scalar (IntV)...
        assertEquals(new CelValue.IntV(7), p.fieldValue(ghost, "a"));
        // ...and comparing int == string is TYPE_MISMATCH at eval (fail-closed).
        assertEquals(CelError.TYPE_MISMATCH, errorOf(new Evaluator().evaluate(pred, p)));
    }

    // =====================================================================
    // 6. entrySchemaDigest-keyed applicability
    // =====================================================================

    @Test
    public void entrySchemaDigestKeysApplicability() throws Exception {
        byte[] bodyA = ghostBody("com.absent.Alpha",
                new String[] { "a" }, new Class<?>[] { String.class }, new Object[] { "x" });
        byte[] bodyB = ghostBody("com.absent.Beta",
                new String[] { "z" }, new Class<?>[] { String.class }, new Object[] { "x" });

        EntryProjection pa = EntryProjection.projectFields(bodyA, Set.of("a"));
        EntryProjection pb = EntryProjection.projectFields(bodyB, Set.of("z"));

        assertEquals(32, pa.entrySchemaDigest().length);
        // Different schemas => different applicability keys.
        assertFalse(Arrays.equals(pa.entrySchemaDigest(), pb.entrySchemaDigest()));
        // The key equals the body's own entrySchemaDigest (cross-checked via the codec).
        assertArrayEquals(EntryRepV2Codec.decodeEntrySchemaChain(bodyA).entrySchemaDigest(),
                pa.entrySchemaDigest());
        // Defensive copy — a caller cannot mutate the stored key.
        byte[] d = pa.entrySchemaDigest();
        d[0] ^= 0xFF;
        assertFalse(Arrays.equals(d, pa.entrySchemaDigest()));
    }

    // =====================================================================
    // Contract point 5 — reads ONLY the referenced fields
    // =====================================================================

    @Test
    public void readsOnlyReferencedFields() throws Exception {
        byte[] body = new EntryRep(new Doc("x", "y")).bodyBytes();
        // Only "a" is referenced; "b" must not be decoded (though still declared).
        EntryProjection p = EntryProjection.projectFields(body, Set.of("a"));
        assertEquals(new CelValue.StringV("x"), p.fieldValue(Doc.class.getName(), "a"));
        assertTrue("b remains schema-declared", p.declaresField(Doc.class.getName(), "b"));
        try {
            p.fieldValue(Doc.class.getName(), "b");
            fail("b was not referenced and must not have been projected");
        } catch (IllegalStateException expected) {
            // good — only referenced fields are decoded.
        }
    }

    @Test
    public void referencedFieldNamesWalksTheWholeAst() {
        // has(a) && (b == 1 || c in [d]) references a, b, c, d.
        ExprNode pred = new ExprNode.And(
                new ExprNode.Has(ref("a")),
                new ExprNode.Or(
                        new ExprNode.Eq(ref("b"), new ExprNode.LitInt(1)),
                        new ExprNode.In(ref("c"),
                                new ExprNode.InListOperand.FieldList(ref("d")))));
        assertEquals(Set.of("a", "b", "c", "d"),
                EntryProjection.referencedFieldNames(pred));
    }

    // =====================================================================
    // Nested @AtomicSerial projection via ObjectCodec.decodeToFieldMap
    // =====================================================================

    @Test
    public void projectsNestedAtomicSerialObjectClassFree() throws Exception {
        byte[] body = new EntryRep(new Located(new Point(3, 4))).bodyBytes();
        Evaluator ev = new Evaluator();

        // has(where): the nested object is present.
        assertEquals(new CelValue.BoolV(true),
                valueOf(ev.evaluate(new ExprNode.Has(ref("where")),
                        EntryProjection.project(body, new ExprNode.Has(ref("where"))))));

        // where.x resolves through the nested projection to the scalar 3.
        ExprNode nested = ref("where", "x");
        assertEquals(new CelValue.IntV(3),
                valueOf(ev.evaluate(nested, EntryProjection.project(body, nested))));
    }

    @Test
    public void nullBodyPathMatchAnyIsUndecodableNotCrash() {
        // A match-any (empty body) has no schema/fields to project; treat as
        // fail-closed rather than crash (B3 applies null-key filters per §6.1,
        // but the projection of an empty body is simply undecodable here).
        byte[] emptyBody = new byte[0];
        assertTrue(EntryProjection.projectFields(emptyBody, Set.of("a")).isUndecodable());
    }

    // =====================================================================
    // ADVERSARIAL — the class-free guarantee (the seat-BLOCK scenario)
    //
    // These prove that a hostile candidate can NEVER cause a wire-named class
    // to be resolved or an @AtomicSerial (GetArg) constructor to run on the
    // server during projection. Before the B2 root fix, EntryProjection routed
    // a scalar slice through the general self-describing object-stream reader
    // (DerMarshalInputStream.readObject), so a [1]/[7]/[8] item in a referenced
    // field loaded classes / ran constructors BEFORE the projection rejected
    // them. The fix decodes every value positionally, gated on the field's
    // DECLARED wireType, via the class-free jgdms-der decoders.
    // =====================================================================

    /** An enum whose class IS on the classpath — used only to build a hostile [7] enum item. */
    public enum Color { RED, GREEN }

    /**
     * An @AtomicSerial whose (GetArg) deserialization constructor sets a static tripwire.
     * If projection ever reconstructs it, {@link #constructed} flips to {@code true}.
     */
    @AtomicSerial
    public static final class Tripwire {
        /** Set true iff the @AtomicSerial (GetArg) ctor runs — the observable "a class was reconstructed" proof. */
        static volatile boolean constructed = false;
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("v", int.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Tripwire o) throws IOException {
            arg.put("v", o.v);
            arg.writeArgs();
        }
        private final int v;
        public Tripwire(int v) { this.v = v; }
        public Tripwire(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            constructed = true; // TRIPWIRE
            this.v = arg.get("v", 0);
        }
    }

    /** A nested @AtomicSerial object with an enum-typed sub-field and a reconstruction tripwire. */
    @AtomicSerial
    public static final class HasColor {
        static volatile boolean constructed = false; // TRIPWIRE
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("color", Color.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, HasColor o) throws IOException {
            arg.put("color", o.color);
            arg.writeArgs();
        }
        private final Color color;
        public HasColor(Color color) { this.color = color; }
        public HasColor(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            constructed = true; // TRIPWIRE
            this.color = (Color) arg.get("color", null);
        }
    }

    /** An Entry whose single field is a nested @AtomicSerial that itself has an enum sub-field. */
    public static class HasColorEntry implements Entry {
        public HasColor hc;
        public HasColorEntry() {}
        public HasColorEntry(HasColor hc) { this.hc = hc; }
    }

    @Test
    public void atomicSerialObjectInScalarSlot_failsClosed_noConstructor() throws Exception {
        Tripwire.constructed = false;
        // Schema DECLARES field "p" as int, but the wire value is an @AtomicSerial object
        // (a non-empty valueSchemaDigest [1]-style slice) — a hostile object-in-a-scalar-slot.
        byte[] body = ghostBody("com.absent.Ghost",
                new String[] { "p" }, new Class<?>[] { int.class }, new Object[] { new Tripwire(7) });
        EntryProjection proj = EntryProjection.project(body, ref("p"));
        assertTrue("an @AtomicSerial object in an int slot MUST be fail-closed", proj.isUndecodable());
        assertFalse("the @AtomicSerial (GetArg) constructor MUST NOT have run on the server",
                Tripwire.constructed);
    }

    @Test
    public void enumItemInStringSlot_failsClosed_noClassResolution() throws Exception {
        // Schema DECLARES "s" as java.lang.String; the wire value is a self-describing [7] enum item.
        // readScalarClassFree cross-checks the actual [7] tag against the declared String tag [3]
        // and fail-closes WITHOUT ever resolving the enum's declaring class or calling Enum.valueOf.
        byte[] body = ghostBody("com.absent.Ghost",
                new String[] { "s" }, new Class<?>[] { String.class }, new Object[] { Color.RED });
        EntryProjection proj = EntryProjection.project(body, ref("s"));
        assertTrue("a [7] enum item in a String slot MUST be fail-closed", proj.isUndecodable());
    }

    @Test
    public void arrayItemInScalarSlot_failsClosed() throws Exception {
        // Declared int; the wire value is a self-describing [9] array item -> fail-closed (no decode).
        byte[] body = ghostBody("com.absent.Ghost",
                new String[] { "a" }, new Class<?>[] { int.class }, new Object[] { new int[] { 1, 2, 3 } });
        EntryProjection proj = EntryProjection.project(body, ref("a"));
        assertTrue("a [9] array item in an int slot MUST be fail-closed", proj.isUndecodable());
    }

    @Test
    public void declaredIntActualStringWire_failsClosed_noCoercion() throws Exception {
        // Declared int, but the wire carries a [3] String scalar — a declared/actual type confusion.
        byte[] body = ghostBody("com.absent.Ghost",
                new String[] { "a" }, new Class<?>[] { int.class }, new Object[] { "not-an-int" });
        EntryProjection proj = EntryProjection.project(body, ref("a"));
        assertTrue("declared int but wire [3] String MUST be fail-closed", proj.isUndecodable());
        // The evaluator maps it to CANDIDATE_UNDECODABLE — never a coerced value or a match.
        EvalOutcome o = new Evaluator().evaluate(
                new ExprNode.Eq(ref("a"), new ExprNode.LitInt(0)), proj);
        assertEquals(CelError.CANDIDATE_UNDECODABLE, errorOf(o));
    }

    @Test
    public void nestedEnumSubField_failsClosed_noConstructor() throws Exception {
        HasColor.constructed = false;
        // A real nested @AtomicSerial (HasColor) whose OWN schema declares an enum: sub-field.
        // The class-free nested field-map decode fail-closes on the enum: sub-field WITHOUT
        // resolving its class, and never runs HasColor's (GetArg) constructor.
        byte[] body = new EntryRep(new HasColorEntry(new HasColor(Color.GREEN))).bodyBytes();
        EntryProjection proj = EntryProjection.project(body, ref("hc"));
        assertTrue("a nested object with an enum: sub-field MUST be fail-closed", proj.isUndecodable());
        assertFalse("the nested @AtomicSerial (GetArg) constructor MUST NOT have run",
                HasColor.constructed);
    }

    // =====================================================================
    // Root-first order reconstruction across a two-class entry hierarchy
    // (the single-record-chain tests never exercise this line).
    // =====================================================================

    /** Root class of a two-level entry hierarchy; its own namespace field is "base". */
    public static class Base implements Entry {
        public String base;
        public Base() {}
        public Base(String base) { this.base = base; }
    }

    /** Leaf class; its own namespace field is "derived". */
    public static class Derived extends Base {
        public String derived;
        public Derived() {}
        public Derived(String base, String derived) { super(base); this.derived = derived; }
    }

    @Test
    public void baseDerivedHierarchyAssignsEachClassFieldsRootFirst() throws Exception {
        // Distinct per-class values: if the field-index -> (class,field) reconstruction were
        // leaf-first (or otherwise skewed), Base#base and Derived#derived would receive each
        // other's slice and these assertions would fail.
        byte[] body = new EntryRep(new Derived("BASE_VAL", "DERIVED_VAL")).bodyBytes();
        EntryProjection p = EntryProjection.project(body,
                new ExprNode.And(ref("base"), ref("derived")));
        assertFalse(p.isUndecodable());

        // The candidate's own namespace chain is leaf-first: [Derived, Base].
        assertEquals(List.of(Derived.class.getName(), Base.class.getName()), p.namespaceChain());

        // Each class declares exactly its OWN field, and each field resolves to its OWN value.
        assertTrue(p.declaresField(Base.class.getName(), "base"));
        assertTrue(p.declaresField(Derived.class.getName(), "derived"));
        assertFalse("base is Base's field, not Derived's", p.declaresField(Derived.class.getName(), "base"));
        assertFalse("derived is Derived's field, not Base's", p.declaresField(Base.class.getName(), "derived"));
        assertEquals(new CelValue.StringV("BASE_VAL"), p.fieldValue(Base.class.getName(), "base"));
        assertEquals(new CelValue.StringV("DERIVED_VAL"), p.fieldValue(Derived.class.getName(), "derived"));
    }

    @Test
    public void topLevelReferencedSetIsFirstStepOnly() {
        // A FieldRef "obj.base" contributes only the TOP-LEVEL name "obj" to the referenced set;
        // the nested step "base" belongs to the nested projection, NOT the top-level decode. This
        // is the fix for flat-selector over-exclusion (a top-level field sharing a name with a
        // nested step must not be dragged into the top-level decode).
        ExprNode pred = new ExprNode.And(
                ref("obj", "base"),           // two-step: only "obj" is top-level
                new ExprNode.Has(ref("top"))); // single-step: "top"
        assertEquals(Set.of("obj", "top"), EntryProjection.referencedFieldNames(pred));
    }
}
