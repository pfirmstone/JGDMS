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

package au.net.zeus.jgdms.showcase;

import au.net.zeus.jgdms.cel.CelError;
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.eval.CandidateProjection;
import au.net.zeus.jgdms.cel.eval.Evaluator;
import au.net.zeus.jgdms.cel.math.MathProviders;
import au.net.zeus.jgdms.cel.verifier.CelVerifier;
import au.net.zeus.jgdms.cel.verifier.VerificationResult;
import au.net.zeus.jgdms.cel.wire.CelDecoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The automated confirmation for demonstration 5 ("filter records by a written rule,
 * without the class"). It exercises the rule-language engine directly — the same public
 * gate and evaluator the reader program uses — asserting each of the three claims:
 *
 * <ol>
 *   <li>a written rule selects the matching records and rejects the rest, evaluated
 *       through the class-free field view (here an in-memory field map standing in for
 *       one decoded from the wire);</li>
 *   <li>an over-expensive rule, an oversized rule, and a malformed rule are all refused
 *       at registration, and a rule reading a field that is not there fails closed with
 *       a definite error;</li>
 *   <li>the same rule gives the identical answer on every evaluation, including a
 *       transform that uses the correctly-rounded trigonometry.</li>
 * </ol>
 *
 * <p>The rule bytes are built here with a tiny local canonical-form builder ({@link W}),
 * so the test depends only on the rule-language library's public surface.
 */
class FilterByARuleTest {

    private static final String WEATHER = "demo.Weather";
    private static final String SURVEY  = "demo.Survey";

    // =====================================================================================
    // 1. Filter records by a written rule
    // =====================================================================================

    @Test
    void writtenRuleSelectsMatchingAndRejectsTheRest() throws Exception {
        // temperatureCelsius > 20.0 AND stationName starts-with "North"
        byte[] ruleBytes = W.predicateRecord(
                W.and(
                        W.gt(W.field("temperatureCelsius"), W.litDouble(20.0)),
                        W.startsWith(W.field("stationName"), "North")));

        VerificationResult reg = CelVerifier.verify(ruleBytes);
        assertTrue(reg.accepted(), "the gate must accept a well-formed, in-budget rule");

        ExprNode rule = reg.record().orElseThrow().expression();
        Evaluator evaluator = new Evaluator();

        assertTrue(selects(evaluator, rule, weather("North-Ridge", 25.4)), "warm + North must be selected");
        assertFalse(selects(evaluator, rule, weather("North-Vale", 14.2)), "cold + North must be rejected");
        assertFalse(selects(evaluator, rule, weather("South-Bay", 31.0)), "warm + South must be rejected");
        assertTrue(selects(evaluator, rule, weather("Northgate", 22.5)), "warm + North must be selected");
        assertFalse(selects(evaluator, rule, weather("East-Field", 19.9)), "not-above-20 must be rejected");
    }

    // =====================================================================================
    // 2. The rule cannot misbehave
    // =====================================================================================

    @Test
    void languageHasNoLoopsOrRecursionByConstruction() {
        // The closed AST inventory: exactly 27 node kinds, none of which is an iteration
        // or self-reference construct. This is structural, not a runtime guard.
        assertEquals(27, ExprNode.class.getPermittedSubclasses().length,
                "the language must have exactly its fixed, closed set of node kinds");
    }

    @Test
    void overExpensiveRuleRefusedAtRegistration() {
        // contains(stationName, <24-char literal>): cheap to write, expensive to run.
        byte[] rule = W.predicateRecord(W.contains(W.field("stationName"), "NORTHNORTHNORTHNORTHNORT"));
        VerificationResult r = CelVerifier.verify(rule);
        assertFalse(r.accepted(), "an over-expensive rule must be refused");
        assertEquals(VerificationResult.Reason.COST_EXCEEDED, r.reason());
    }

    @Test
    void oversizedRuleRefusedAtRegistration() {
        List<byte[]> many = new ArrayList<>();
        for (int i = 0; i < 1100; i++) many.add(W.litInt(i)); // over the 1024-node ceiling
        byte[] rule = W.predicateRecord(W.inLiteralList(W.litInt(0), many));
        VerificationResult r = CelVerifier.verify(rule);
        assertFalse(r.accepted(), "a rule over the node ceiling must be refused");
        assertEquals(VerificationResult.Reason.DECODE_REJECTED, r.reason());
    }

    @Test
    void malformedRuleRefusedCleanly() {
        VerificationResult r = CelVerifier.verify(new byte[]{0x2A, 0x03, 0x01, 0x02, 0x03});
        assertFalse(r.accepted(), "malformed bytes must be refused, not crashed");
        assertEquals(VerificationResult.Reason.DECODE_REJECTED, r.reason());
    }

    @Test
    void ruleReadingAnAbsentFieldFailsClosed() throws Exception {
        byte[] rule = W.predicateRecord(W.gt(W.field("pressureHectopascals"), W.litDouble(1000.0)));
        ExprNode expr = CelDecoder.decode(rule).expression();
        EvalOutcome outcome = new Evaluator().evaluate(expr, weather("North-Ridge", 25.4));
        assertTrue(outcome.isError(), "reading an absent field must not yield a value");
        assertEquals(CelError.ABSENT_FIELD, outcome.errorOrThrow());
    }

    // =====================================================================================
    // 3. The same rule, the same answer — exactly
    // =====================================================================================

    @Test
    void sameRuleSameAnswerEveryTime() throws Exception {
        ExprNode rule = CelDecoder.decode(W.predicateRecord(
                W.and(
                        W.gt(W.field("temperatureCelsius"), W.litDouble(20.0)),
                        W.startsWith(W.field("stationName"), "North")))).expression();
        Evaluator evaluator = new Evaluator();
        CandidateProjection reading = weather("North-Ridge", 25.4);
        EvalOutcome first = evaluator.evaluate(rule, reading);
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, evaluator.evaluate(rule, reading), "the yes/no answer must never vary");
        }
    }

    @Test
    void transformIsReproducibleToTheBit() throws Exception {
        // east/north/up local direction vector from bearing, vertical angle, slope distance.
        byte[] bear = W.field("bearingDegrees");
        byte[] elev = W.field("elevationDegrees");
        byte[] dist = W.field("slopeDistanceMetres");
        byte[] cosVert = W.cos(W.radians(elev));
        byte[] vector = W.listLit(
                W.mul(W.mul(dist, cosVert), W.sin(W.radians(bear))),
                W.mul(W.mul(dist, cosVert), W.cos(W.radians(bear))),
                W.mul(dist, W.sin(W.radians(elev))));
        byte[] transformBytes = W.transformListOfDoublesRecord(vector);

        VerificationResult reg = CelVerifier.verify(transformBytes);
        assertTrue(reg.accepted(), "the transform must pass the registration gate");

        ExprNode transform = reg.record().orElseThrow().expression();
        Evaluator evaluator = new Evaluator(MathProviders.correctlyRounded());
        CandidateProjection obs = survey(30.0, 10.0, 100.0);

        long[] firstBits = vectorBits(evaluator, transform, obs);
        for (int i = 0; i < 1000; i++) {
            assertArrayEquals(firstBits, vectorBits(evaluator, transform, obs));
        }

        // Sanity: the vector's length equals the slope distance (real geometry, not a stub).
        double magnitude = Math.sqrt(
                Math.pow(Double.longBitsToDouble(firstBits[0]), 2)
                        + Math.pow(Double.longBitsToDouble(firstBits[1]), 2)
                        + Math.pow(Double.longBitsToDouble(firstBits[2]), 2));
        assertTrue(Math.abs(magnitude - 100.0) < 1e-6, "direction vector length must equal slope distance");
    }

    // =====================================================================================
    // helpers
    // =====================================================================================

    private static boolean selects(Evaluator evaluator, ExprNode rule, CandidateProjection c) {
        EvalOutcome o = evaluator.evaluate(rule, c);
        return o.isValue() && o.valueOrThrow() instanceof CelValue.BoolV b && b.value();
    }

    private static long[] vectorBits(Evaluator evaluator, ExprNode transform, CandidateProjection c) {
        EvalOutcome o = evaluator.evaluate(transform, c);
        assertTrue(o.isValue() && o.valueOrThrow() instanceof CelValue.ListV, "transform must yield a list");
        CelValue.ListV list = (CelValue.ListV) o.valueOrThrow();
        assertEquals(3, list.elements().size(), "the direction vector must have three components");
        long[] bits = new long[3];
        for (int i = 0; i < 3; i++) {
            bits[i] = Double.doubleToRawLongBits(((CelValue.DoubleV) list.elements().get(i)).value());
        }
        return bits;
    }

    private static void assertArrayEquals(long[] expected, long[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "bit-for-bit component " + i + " must be identical");
        }
    }

    private static CandidateProjection weather(String stationName, double temperatureCelsius) {
        Map<String, CelValue> fields = new LinkedHashMap<>();
        fields.put("stationName", new CelValue.StringV(stationName));
        fields.put("temperatureCelsius", new CelValue.DoubleV(temperatureCelsius));
        return new Proj(WEATHER, fields);
    }

    private static CandidateProjection survey(double bearing, double elevation, double slopeDistance) {
        Map<String, CelValue> fields = new LinkedHashMap<>();
        fields.put("bearingDegrees", new CelValue.DoubleV(bearing));
        fields.put("elevationDegrees", new CelValue.DoubleV(elevation));
        fields.put("slopeDistanceMetres", new CelValue.DoubleV(slopeDistance));
        return new Proj(SURVEY, fields);
    }

    /** A one-class in-memory field view, standing in for one decoded class-free from the wire. */
    private record Proj(String className, Map<String, CelValue> fields) implements CandidateProjection {
        @Override public boolean isUndecodable() { return false; }
        @Override public List<String> namespaceChain() { return List.of(className); }
        @Override public boolean declaresField(String c, String f) { return className.equals(c) && fields.containsKey(f); }
        @Override public CelValue fieldValue(String c, String f) { return fields.get(f); }
    }

    /**
     * A tiny canonical-form builder for rule wire bytes — only what this test needs.
     * It lays out the bytes the way the canonical format defines; the library's own gate
     * then decodes and checks them.
     */
    private static final class W {
        static byte[] litInt(long v)    { return ctxPrim(1, minimalInt(v)); }
        static byte[] litString(String s) { return ctxPrim(3, s.getBytes(StandardCharsets.UTF_8)); }
        static byte[] litDouble(double v) {
            long bits = Double.doubleToRawLongBits(v);
            byte[] c = new byte[8];
            for (int i = 7; i >= 0; i--) { c[i] = (byte) (bits & 0xFF); bits >>>= 8; }
            return ctxPrim(2, c);
        }
        static byte[] field(String name) { return ctxCons(7, useq(ctxPrim(0, name.getBytes(StandardCharsets.UTF_8)))); }

        static byte[] and(byte[] l, byte[] r) { return ctxCons(11, l, r); }
        static byte[] gt(byte[] l, byte[] r)  { return ctxCons(17, l, r); }
        static byte[] mul(byte[] l, byte[] r) { return ctxCons(21, l, r); }

        static byte[] startsWith(byte[] recv, String needle) { return call(23, recv, litString(needle)); }
        static byte[] contains(byte[] recv, String needle)   { return call(22, recv, litString(needle)); }
        static byte[] radians(byte[] x) { return call(13, x); }
        static byte[] sin(byte[] x)     { return call(15, x); }
        static byte[] cos(byte[] x)     { return call(16, x); }

        static byte[] call(int id, byte[]... args) { return ctxCons(26, uint(minimalInt(id)), useq(args)); }
        static byte[] listLit(byte[]... elems)     { return ctxCons(6, elems); }
        static byte[] inLiteralList(byte[] needle, List<byte[]> elems) {
            return ctxCons(24, needle, ctxCons(0, elems.toArray(new byte[0][])));
        }

        static byte[] predicateRecord(byte[] root) {
            return useq(uint(minimalInt(1)), ctxPrim(0, new byte[0]), root);
        }
        static byte[] transformListOfDoublesRecord(byte[] root) {
            byte[] ctx = tlv(ctxTag(1, true), ctxPrim(2, minimalInt(2)));
            return useq(uint(minimalInt(1)), ctx, root);
        }

        // TLV layout
        private static byte[] tlv(byte[] tag, byte[] content) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(tag); out.writeBytes(len(content.length)); out.writeBytes(content);
            return out.toByteArray();
        }
        private static byte[] ctxTag(int n, boolean cons) { return new byte[]{(byte) (0x80 | (cons ? 0x20 : 0) | n)}; }
        private static byte[] ctxPrim(int n, byte[] c)    { return tlv(ctxTag(n, false), c); }
        private static byte[] ctxCons(int n, byte[]... ch){ return tlv(ctxTag(n, true), cat(ch)); }
        private static byte[] uni(int tag, boolean cons, byte[] c) { return tlv(new byte[]{(byte) ((cons ? 0x20 : 0) | tag)}, c); }
        private static byte[] useq(byte[]... ch) { return uni(0x10, true, cat(ch)); }
        private static byte[] uint(byte[] c)     { return uni(0x02, false, c); }
        private static byte[] minimalInt(long v) { return v == 0 ? new byte[]{0} : java.math.BigInteger.valueOf(v).toByteArray(); }
        private static byte[] cat(byte[]... parts) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] p : parts) out.writeBytes(p);
            return out.toByteArray();
        }
        private static byte[] len(int n) {
            if (n <= 0x7F) return new byte[]{(byte) n};
            List<Byte> b = new ArrayList<>();
            int x = n; while (x > 0) { b.add(0, (byte) (x & 0xFF)); x >>>= 8; }
            byte[] out = new byte[b.size() + 1];
            out[0] = (byte) (0x80 | b.size());
            for (int i = 0; i < b.size(); i++) out[i + 1] = b.get(i);
            return out;
        }
    }
}
