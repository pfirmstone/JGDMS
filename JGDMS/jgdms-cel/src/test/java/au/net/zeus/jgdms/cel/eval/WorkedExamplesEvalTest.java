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

package au.net.zeus.jgdms.cel.eval;

import au.net.zeus.jgdms.cel.CelError;
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.math.MathProvider;
import au.net.zeus.jgdms.cel.testsupport.FakeCandidateProjection;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JGDMS-STD-011 §14's worked examples, evaluated end-to-end. Transcendental
 * examples (§14.3) are run only under the explicit
 * {@link MathProvider#nonConformantForTestingOnly()} opt-in, and this test
 * asserts <em>only</em> that the placeholder-gating machinery works (the
 * call succeeds once opted in, throws otherwise) -- never that the
 * placeholder's numeric output is spec-conformant, per the task's explicit
 * instruction. The conformant correctly-rounded implementation is T3 phase 2.
 */
class WorkedExamplesEvalTest {

    private static ExprNode field(String name) {
        return new ExprNode.FieldRef(List.of(new ExprNode.SelectorStep.Unqual(name)));
    }

    private static boolean matches(Evaluator eval, ExprNode predicate, CandidateProjection candidate) {
        EvalOutcome outcome = eval.evaluate(predicate, candidate);
        // §9.4's verdict mapping: true -> match, false or any error -> no-match.
        return outcome instanceof EvalOutcome.Value v && v.value() instanceof CelValue.BoolV b && b.value();
    }

    // ======================================================================
    // §14.1: range predicate over a numeric field
    // ======================================================================

    @Test
    void s14_1_rangePredicate_match() {
        Evaluator eval = new Evaluator();
        ExprNode predicate = new ExprNode.And(
                new ExprNode.Ge(field("pressureHpa"), new ExprNode.LitDouble(950.0)),
                new ExprNode.Lt(field("pressureHpa"), new ExprNode.LitDouble(1050.0)));
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.SensorReading", "pressureHpa", new CelValue.DoubleV(1013.25))
                .build();
        assertTrue(matches(eval, predicate, candidate));
    }

    @Test
    void s14_1_rangePredicate_nanIsNoMatch_notAnError() {
        Evaluator eval = new Evaluator();
        ExprNode predicate = new ExprNode.And(
                new ExprNode.Ge(field("pressureHpa"), new ExprNode.LitDouble(950.0)),
                new ExprNode.Lt(field("pressureHpa"), new ExprNode.LitDouble(1050.0)));
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.SensorReading", "pressureHpa", new CelValue.DoubleV(Double.NaN))
                .build();
        EvalOutcome outcome = eval.evaluate(predicate, candidate);
        assertInstanceOf(EvalOutcome.Value.class, outcome, "NaN comparisons are total (false), not an error");
        assertFalse(matches(eval, predicate, candidate));
    }

    @Test
    void s14_1_rangePredicate_absentField_leftErrorReported_noMatch() {
        Evaluator eval = new Evaluator();
        ExprNode predicate = new ExprNode.And(
                new ExprNode.Ge(field("pressureHpa"), new ExprNode.LitDouble(950.0)),
                new ExprNode.Lt(field("pressureHpa"), new ExprNode.LitDouble(1050.0)));
        CandidateProjection candidate = FakeCandidateProjection.builder().namespace("com.example.SensorReading").build();
        EvalOutcome outcome = eval.evaluate(predicate, candidate);
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class, outcome);
        assertEquals(CelError.ABSENT_FIELD, e.code());
        assertFalse(matches(eval, predicate, candidate));
    }

    // ======================================================================
    // §14.2: compound predicate with string operations
    // ======================================================================

    private static ExprNode s14_2_predicate() {
        ExprNode statusEq = new ExprNode.Eq(field("status"), new ExprNode.LitString("ACTIVE"));
        ExprNode startsWith = new ExprNode.Call(23, List.of(field("station"), new ExprNode.LitString("GLS-")));
        ExprNode contains = new ExprNode.Call(22, List.of(field("note"), new ExprNode.LitString("deprecated")));
        ExprNode notContains = new ExprNode.Not(contains);
        ExprNode sampleCountGe = new ExprNode.Ge(field("sampleCount"), new ExprNode.LitInt(30));
        return new ExprNode.And(new ExprNode.And(new ExprNode.And(statusEq, startsWith), notContains), sampleCountGe);
    }

    @Test
    void s14_2_fullMatch() {
        Evaluator eval = new Evaluator();
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.SensorReading", "status", new CelValue.StringV("ACTIVE"))
                .field("com.example.SensorReading", "station", new CelValue.StringV("GLS-1"))
                .field("com.example.SensorReading", "note", new CelValue.StringV("clean"))
                .field("com.example.SensorReading", "sampleCount", new CelValue.IntV(50))
                .build();
        assertTrue(matches(eval, s14_2_predicate(), candidate));
    }

    @Test
    void s14_2_nullNote_unguardedIdiom_errorsToNoMatch() {
        Evaluator eval = new Evaluator();
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.SensorReading", "status", new CelValue.StringV("ACTIVE"))
                .field("com.example.SensorReading", "station", new CelValue.StringV("GLS-1"))
                .field("com.example.SensorReading", "note", CelValue.NullV.INSTANCE)
                .field("com.example.SensorReading", "sampleCount", new CelValue.IntV(50))
                .build();
        EvalOutcome outcome = eval.evaluate(s14_2_predicate(), candidate);
        assertInstanceOf(EvalOutcome.Error.class, outcome, "contains() on a null receiver is TYPE_MISMATCH");
        assertFalse(matches(eval, s14_2_predicate(), candidate));
    }

    @Test
    void s14_2_nullNote_guardedIdiom_matches() {
        // status == "ACTIVE" && station.startsWith("GLS-") && (!has(note) || !note.contains("deprecated")) && sampleCount >= 30
        Evaluator eval = new Evaluator();
        ExprNode statusEq = new ExprNode.Eq(field("status"), new ExprNode.LitString("ACTIVE"));
        ExprNode startsWith = new ExprNode.Call(23, List.of(field("station"), new ExprNode.LitString("GLS-")));
        ExprNode hasNote = new ExprNode.Has((ExprNode.FieldRef) field("note"));
        ExprNode notHasNote = new ExprNode.Not(hasNote);
        ExprNode notContains = new ExprNode.Not(new ExprNode.Call(22, List.of(field("note"), new ExprNode.LitString("deprecated"))));
        ExprNode guard = new ExprNode.Or(notHasNote, notContains);
        ExprNode sampleCountGe = new ExprNode.Ge(field("sampleCount"), new ExprNode.LitInt(30));
        ExprNode predicate = new ExprNode.And(new ExprNode.And(new ExprNode.And(statusEq, startsWith), guard), sampleCountGe);

        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.SensorReading", "status", new CelValue.StringV("ACTIVE"))
                .field("com.example.SensorReading", "station", new CelValue.StringV("GLS-1"))
                .field("com.example.SensorReading", "note", CelValue.NullV.INSTANCE)
                .field("com.example.SensorReading", "sampleCount", new CelValue.IntV(50))
                .build();
        assertTrue(matches(eval, predicate, candidate));
    }

    // ======================================================================
    // §14.3: the Survey-zoot transform (transcendental CALLs; placeholder-only)
    // ======================================================================

    private static ExprNode s14_3_transform() {
        ExprNode slopeDistM1 = field("slopeDistM");
        ExprNode slopeDistM2 = field("slopeDistM");
        ExprNode slopeDistM3 = field("slopeDistM");
        ExprNode elevationDeg1 = field("elevationDeg");
        ExprNode elevationDeg2 = field("elevationDeg");
        ExprNode elevationDeg3 = field("elevationDeg");
        ExprNode bearingDeg1 = field("bearingDeg");
        ExprNode bearingDeg2 = field("bearingDeg");

        ExprNode cosElev1 = call(FunctionRegistry.Name.COS, call(FunctionRegistry.Name.RADIANS, elevationDeg1));
        ExprNode cosElev2 = call(FunctionRegistry.Name.COS, call(FunctionRegistry.Name.RADIANS, elevationDeg2));
        ExprNode sinBearing = call(FunctionRegistry.Name.SIN, call(FunctionRegistry.Name.RADIANS, bearingDeg1));
        ExprNode cosBearing = call(FunctionRegistry.Name.COS, call(FunctionRegistry.Name.RADIANS, bearingDeg2));
        ExprNode sinElev = call(FunctionRegistry.Name.SIN, call(FunctionRegistry.Name.RADIANS, elevationDeg3));

        ExprNode element1 = new ExprNode.Mul(new ExprNode.Mul(slopeDistM1, cosElev1), sinBearing);
        ExprNode element2 = new ExprNode.Mul(new ExprNode.Mul(slopeDistM2, cosElev2), cosBearing);
        ExprNode element3 = new ExprNode.Mul(slopeDistM3, sinElev);
        return new ExprNode.ListLit(List.of(element1, element2, element3));
    }

    private static ExprNode call(FunctionRegistry.Name name, ExprNode... args) {
        int id = -1;
        for (int i = 1; i <= 24; i++) {
            FunctionRegistry.Entry e = FunctionRegistry.byId(i);
            if (e != null && e.name() == name) { id = i; break; }
        }
        return new ExprNode.Call(id, List.of(args));
    }

    private static CandidateProjection surveyCandidate(double bearing, double elevation, double distance) {
        return FakeCandidateProjection.builder()
                .field("au.geo.survey.RawObservation", "bearingDeg", new CelValue.DoubleV(bearing))
                .field("au.geo.survey.RawObservation", "elevationDeg", new CelValue.DoubleV(elevation))
                .field("au.geo.survey.RawObservation", "slopeDistM", new CelValue.DoubleV(distance))
                .build();
    }

    /**
     * With no {@link MathProvider} installed (the fail-closed default), a
     * transcendental CALL MUST throw rather than silently return any value.
     */
    @Test
    void s14_3_noProviderInstalled_throws() {
        Evaluator eval = new Evaluator(); // MathProvider.none() by default
        CandidateProjection candidate = surveyCandidate(45.0, 10.0, 100.0);
        assertThrows(IllegalStateException.class, () -> eval.evaluate(s14_3_transform(), candidate));
    }

    /**
     * Under the explicit {@code nonConformantForTestingOnly()} opt-in, the
     * transform machinery (CALL dispatch, list construction, cost-irrelevant
     * evaluation) runs to completion. This asserts only that a well-shaped
     * result (a 3-element list of finite doubles) comes back -- it does
     * <em>not</em> assert specific numeric values, because
     * {@code StrictMathPlaceholder} is explicitly non-conformant (§7.5); T3
     * phase 2's correctly-rounded provider is what conformant assertions
     * would run against.
     */
    @Test
    void s14_3_placeholderOptIn_runsAndProducesWellShapedResult() {
        Evaluator eval = new Evaluator(MathProvider.nonConformantForTestingOnly());
        CandidateProjection candidate = surveyCandidate(45.0, 10.0, 100.0);
        EvalOutcome outcome = eval.evaluate(s14_3_transform(), candidate);
        EvalOutcome.Value v = assertInstanceOf(EvalOutcome.Value.class, outcome);
        CelValue.ListV list = assertInstanceOf(CelValue.ListV.class, v.value());
        assertEquals(3, list.elements().size());
        for (CelValue element : list.elements()) {
            double d = assertInstanceOf(CelValue.DoubleV.class, element).value();
            assertTrue(Double.isFinite(d));
        }
    }

    /**
     * A garbage observation ({@code elevationDeg = NaN}): {@code
     * radians(NaN)} is DOMAIN per §7.1 (NaN argument to any platform
     * function), and the transform delivers the explicit error result
     * (§9.5) -- never a poisoned vector containing NaN components.
     */
    @Test
    void s14_3_nanElevation_deliversExplicitError_neverPoisonedVector() {
        Evaluator eval = new Evaluator(MathProvider.nonConformantForTestingOnly());
        CandidateProjection candidate = surveyCandidate(45.0, Double.NaN, 100.0);
        EvalOutcome outcome = eval.evaluate(s14_3_transform(), candidate);
        EvalOutcome.Error e = assertInstanceOf(EvalOutcome.Error.class, outcome);
        assertEquals(CelError.DOMAIN, e.code());
    }

    // ======================================================================
    // §14.4: ambiguous-field rejection (see also FieldResolutionTest)
    // ======================================================================

    @Test
    void s14_4_ambiguousFieldRejection() {
        Evaluator eval = new Evaluator();
        ExprNode predicate = new ExprNode.Gt(field("timestamp"), new ExprNode.LitInt(1700000000000L));
        CandidateProjection betaCandidate = FakeCandidateProjection.builder()
                .field("com.example.Beta", "timestamp", new CelValue.IntV(1750000000000L))
                .field("com.example.Alpha", "timestamp", new CelValue.IntV(1750000000000L))
                .build();
        EvalOutcome outcome = eval.evaluate(predicate, betaCandidate);
        assertEquals(CelError.AMBIGUOUS_FIELD, ((EvalOutcome.Error) outcome).code());
        assertFalse(matches(eval, predicate, betaCandidate));
    }

    // ======================================================================
    // §14.5: absent field => no-match; has() as the guard
    // ======================================================================

    @Test
    void s14_5_absentFieldUnguarded_isError_noMatch() {
        Evaluator eval = new Evaluator();
        ExprNode predicate = new ExprNode.Lt(field("batteryMv"), new ExprNode.LitInt(3300));
        CandidateProjection oldSchema = FakeCandidateProjection.builder().namespace("com.example.SensorReading").build();
        assertFalse(matches(eval, predicate, oldSchema));
        assertInstanceOf(EvalOutcome.Error.class, eval.evaluate(predicate, oldSchema));
    }

    @Test
    void s14_5_hasGuard_oldSchema_falseNoError() {
        Evaluator eval = new Evaluator();
        ExprNode predicate = new ExprNode.And(
                new ExprNode.Has((ExprNode.FieldRef) field("batteryMv")),
                new ExprNode.Lt(field("batteryMv"), new ExprNode.LitInt(3300)));
        CandidateProjection oldSchema = FakeCandidateProjection.builder().namespace("com.example.SensorReading").build();
        EvalOutcome outcome = eval.evaluate(predicate, oldSchema);
        assertInstanceOf(EvalOutcome.Value.class, outcome, "has()-guarded absence must be a clean false, not an error");
        assertFalse(matches(eval, predicate, oldSchema));
    }

    @Test
    void s14_5_hasGuard_newSchema_lowBattery_matches() {
        Evaluator eval = new Evaluator();
        ExprNode predicate = new ExprNode.And(
                new ExprNode.Has((ExprNode.FieldRef) field("batteryMv")),
                new ExprNode.Lt(field("batteryMv"), new ExprNode.LitInt(3300)));
        CandidateProjection newSchema = FakeCandidateProjection.builder()
                .field("com.example.SensorReading", "batteryMv", new CelValue.IntV(3100))
                .build();
        assertTrue(matches(eval, predicate, newSchema));
    }

    @Test
    void s14_5_missingTelemetryTreatedAsSuspect_matchesOnAbsence() {
        // !has(batteryMv) || batteryMv < 3300
        Evaluator eval = new Evaluator();
        ExprNode predicate = new ExprNode.Or(
                new ExprNode.Not(new ExprNode.Has((ExprNode.FieldRef) field("batteryMv"))),
                new ExprNode.Lt(field("batteryMv"), new ExprNode.LitInt(3300)));
        CandidateProjection oldSchema = FakeCandidateProjection.builder().namespace("com.example.SensorReading").build();
        assertTrue(matches(eval, predicate, oldSchema));
    }

    // ======================================================================
    // §14.6: overflow error case
    // ======================================================================

    @Test
    void s14_6_overflowSurfaces() {
        Evaluator eval = new Evaluator();
        ExprNode predicate = new ExprNode.Gt(
                new ExprNode.Mul(field("sampleCount"), new ExprNode.LitInt(86400000000000L)),
                new ExprNode.LitInt(0));
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.SensorReading", "sampleCount", new CelValue.IntV(200000))
                .build();
        EvalOutcome outcome = eval.evaluate(predicate, candidate);
        assertEquals(CelError.OVERFLOW, ((EvalOutcome.Error) outcome).code());
        assertFalse(matches(eval, predicate, candidate));
    }

    @Test
    void s14_6_fixedWithExplicitDoubleConversion_noOverflow() {
        Evaluator eval = new Evaluator();
        ExprNode doubleOfSampleCount = new ExprNode.Call(5, List.of(field("sampleCount"))); // double(x: int), id 5
        ExprNode predicate = new ExprNode.Gt(
                new ExprNode.Mul(doubleOfSampleCount, new ExprNode.LitDouble(86400000000000.0)),
                new ExprNode.LitDouble(0.0));
        CandidateProjection candidate = FakeCandidateProjection.builder()
                .field("com.example.SensorReading", "sampleCount", new CelValue.IntV(200000))
                .build();
        assertTrue(matches(eval, predicate, candidate));
    }
}
