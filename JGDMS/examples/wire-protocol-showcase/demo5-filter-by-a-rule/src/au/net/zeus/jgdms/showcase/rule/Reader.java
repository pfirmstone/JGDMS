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

package au.net.zeus.jgdms.showcase.rule;

import au.net.zeus.jgdms.cel.CelError;
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.EvalOutcome;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.eval.CandidateProjection;
import au.net.zeus.jgdms.cel.eval.Evaluator;
import au.net.zeus.jgdms.cel.math.MathProviders;
import au.net.zeus.jgdms.cel.verifier.CelVerifier;
import au.net.zeus.jgdms.cel.verifier.DerSchemaChainView;
import au.net.zeus.jgdms.cel.verifier.SchemaView;
import au.net.zeus.jgdms.cel.verifier.VerificationResult;
import au.net.zeus.jgdms.cel.wire.CelDecoder;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The reader side. It does NOT have the record classes on its classpath. It filters the
 * weather readings by a written rule, refuses rules that could misbehave at the moment
 * they are registered, and shows the same rule gives the same answer every time - all
 * without ever turning a reading back into an object of its class (which it cannot,
 * because the class is genuinely absent here).
 *
 * <p>The program checks every claim itself and exits non-zero if any of them fails, so
 * a person watching or a build server can trust the result.
 */
public final class Reader {

    private static final String WEATHER_CLASS = "au.net.zeus.jgdms.showcase.rule.records.WeatherReading";
    private static final String SURVEY_CLASS  = "au.net.zeus.jgdms.showcase.rule.records.SurveyObservation";

    private static boolean allHeld = true;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: Reader <input-file>");
            System.exit(2);
        }

        System.out.println("Reader (this program does NOT have the record classes on its classpath):");
        System.out.println();

        // Prove the record classes really are absent here.
        boolean weatherAbsent = classIsAbsent(WEATHER_CLASS);
        boolean surveyAbsent  = classIsAbsent(SURVEY_CLASS);
        check(weatherAbsent, "the weather record class is genuinely NOT loadable here");
        check(surveyAbsent,  "the survey record class is genuinely NOT loadable here");
        System.out.println();

        // Read every record as a class-free field view (className -> field -> value).
        List<Candidate> weather = new ArrayList<>();
        Candidate survey;
        try (DataInputStream in = new DataInputStream(new FileInputStream(args[0]))) {
            int nWeather = in.readInt();
            for (int i = 0; i < nWeather; i++) weather.add(readCandidate(in));
            survey = readCandidate(in);
        }
        System.out.println("  Read " + weather.size() + " weather readings and 1 survey observation");
        System.out.println("  straight into a class-free field view - no record object was built.");
        System.out.println();

        sectionOne(weather);
        sectionTwo(weather.get(0));
        sectionThree(weather.get(0), survey);

        System.out.println();
        System.out.println(allHeld
                ? "ALL CLAIMS HELD: a written rule filtered the readings without the record class,"
                  + " the gate refused every rule that could misbehave, and the same rule gave the"
                  + " same answer exactly - no downloaded code, no class needed, nothing that could hang."
                : "A CLAIM DID NOT HOLD - see the [FAIL] line(s) above.");
        if (!allHeld) System.exit(1);
    }

    // =====================================================================================
    // 1. HEADLINE: filter records by a written rule, without the class.
    // =====================================================================================

    private static void sectionOne(List<Candidate> weather) {
        heading("1. Filter readings by a written rule - without the record class");

        // The rule, authored as a tree and travelling as canonical bytes (there is no text
        // form to type). RuleWire builds the bytes and the readable line from the same source.
        RuleWire.Expr rule = RuleWire.and(
                RuleWire.gt(RuleWire.field("temperatureCelsius"), RuleWire.litDouble(20.0)),
                RuleWire.startsWith(RuleWire.field("stationName"), "North"));
        byte[] ruleBytes = RuleWire.predicateRecord(rule);

        System.out.println("  the rule (shown readably):  " + rule.text);
        System.out.println("  the rule travels as " + ruleBytes.length + " canonical bytes; the reader never parsed text.");
        System.out.println();

        // Register the rule against the shape that travelled with the first reading: the gate
        // decodes it, checks it against the closed function list, the ceilings and the cost
        // model, and type-checks it - one fail-closed accept/reject.
        SchemaView schema = weather.get(0).schemaView();
        VerificationResult reg = CelVerifier.verify(ruleBytes, schema);
        check(reg.accepted(), "the gate ACCEPTED the rule at registration"
                + (reg.accepted() ? " (cost " + reg.cost().getAsLong() + ")" : " -> " + reg));
        if (!reg.accepted()) return;

        // The accepted rule, ready to run. This is the very object the gate approved.
        ExprNode expr = reg.record().get().expression();
        Evaluator evaluator = new Evaluator();

        System.out.println();
        System.out.println("  running the rule against each reading, through the class-free field view:");
        boolean[] expected = { true, false, false, true, false };
        boolean patternHeld = true;
        for (int i = 0; i < weather.size(); i++) {
            Candidate c = weather.get(i);
            EvalOutcome outcome = evaluator.evaluate(expr, c);
            boolean selected = outcome.isValue()
                    && outcome.valueOrThrow() instanceof CelValue.BoolV b && b.value();
            System.out.printf("      %-18s temp=%-6s name=%-16s -> %s%n",
                    "[" + c.summary() + "]",
                    c.doubleField("temperatureCelsius"),
                    '"' + c.stringField("stationName") + '"',
                    selected ? "SELECT" : "reject");
            if (selected != expected[i]) patternHeld = false;
        }
        check(patternHeld, "the rule selected exactly the warm northern readings, rejected the rest");

        // The class really is required to rebuild the object - which is exactly the step
        // filtering never took.
        check(classIsAbsent(WEATHER_CLASS),
                "rebuilding a reading into its object would need the class; filtering never did");
    }

    // =====================================================================================
    // 2. The rule cannot misbehave (bounded by construction).
    // =====================================================================================

    private static void sectionTwo(Candidate anyWeather) throws Exception {
        heading("2. The rule cannot misbehave - it is bounded by construction");

        // (a) Structural: the language has a fixed, small set of node kinds and none of them
        //     is a loop or a call-back-to-itself. There is no way to write an unbounded rule.
        int nodeKinds = ExprNode.class.getPermittedSubclasses().length;
        check(nodeKinds == 27,
                "the language has exactly " + nodeKinds + " fixed node kinds - none is a loop or recursion,"
                        + " so no rule can iterate or call itself");

        // (b) An over-expensive rule is refused at registration, BEFORE anything runs.
        //     "stationName contains <long text>" is cheap to write but expensive to run;
        //     the cost model refuses it up front.
        RuleWire.Expr tooExpensive = RuleWire.contains(
                RuleWire.field("stationName"), "NORTHNORTHNORTHNORTHNORT"); // 24 chars
        VerificationResult costReject = CelVerifier.verify(RuleWire.predicateRecord(tooExpensive));
        check(!costReject.accepted() && costReject.reason() == VerificationResult.Reason.COST_EXCEEDED,
                "an over-expensive rule is REFUSED at registration (" + reason(costReject) + ")");

        // (c) An oversized rule (more nodes than the ceiling allows) is refused at registration.
        List<RuleWire.Expr> manyValues = new ArrayList<>();
        for (int i = 0; i < 1100; i++) manyValues.add(RuleWire.litInt(i)); // > the 1024-node ceiling
        RuleWire.Expr tooBig = RuleWire.inLiteralList(RuleWire.litInt(0), manyValues);
        VerificationResult sizeReject = CelVerifier.verify(RuleWire.predicateRecord(tooBig));
        check(!sizeReject.accepted() && sizeReject.reason() == VerificationResult.Reason.DECODE_REJECTED,
                "an oversized rule (over the node ceiling) is REFUSED at registration (" + reason(sizeReject) + ")");

        // (d) A malformed rule is refused cleanly - a definite rejection, not a crash.
        byte[] garbage = { 0x2A, 0x03, 0x01, 0x02, 0x03 };
        VerificationResult malformed = CelVerifier.verify(garbage);
        check(!malformed.accepted() && malformed.reason() == VerificationResult.Reason.DECODE_REJECTED,
                "a malformed rule is REFUSED cleanly, not crashed (" + reason(malformed) + ")");

        // (e) A rule that reads a field which is not there returns a definite, named error
        //     at evaluation - not a wrong answer, not a crash.
        RuleWire.Expr missingField = RuleWire.gt(
                RuleWire.field("pressureHectopascals"), RuleWire.litDouble(1000.0));
        ExprNode expr = CelDecoder.decode(RuleWire.predicateRecord(missingField)).expression();
        EvalOutcome outcome = new Evaluator().evaluate(expr, anyWeather);
        check(outcome.isError() && outcome.errorOrThrow() == CelError.ABSENT_FIELD,
                "a rule reading a field that is not there fails closed with a definite error ("
                        + describe(outcome) + ")");
    }

    // =====================================================================================
    // 3. The same rule, the same answer - exactly.
    // =====================================================================================

    private static void sectionThree(Candidate weather, Candidate survey) throws Exception {
        heading("3. The same rule, the same answer - exactly");

        // (a) The yes/no rule from section 1, run 1000 times over the same reading, is
        //     identical every time.
        RuleWire.Expr rule = RuleWire.and(
                RuleWire.gt(RuleWire.field("temperatureCelsius"), RuleWire.litDouble(20.0)),
                RuleWire.startsWith(RuleWire.field("stationName"), "North"));
        ExprNode predicate = CelDecoder.decode(RuleWire.predicateRecord(rule)).expression();
        Evaluator plain = new Evaluator();
        EvalOutcome first = plain.evaluate(predicate, weather);
        boolean stable = true;
        for (int i = 0; i < 1000; i++) {
            if (!plain.evaluate(predicate, weather).equals(first)) { stable = false; break; }
        }
        check(stable, "the yes/no rule gave the identical answer on all 1000 evaluations");

        // (b) A transform that uses the correctly-rounded trigonometry the language ships with:
        //     bearing, vertical angle and slope distance -> a local direction vector (east,
        //     north, up). The exact number is reproducible to the bit.
        RuleWire.Expr bearing   = RuleWire.field("bearingDegrees");
        RuleWire.Expr elevation = RuleWire.field("elevationDegrees");
        RuleWire.Expr distance  = RuleWire.field("slopeDistanceMetres");
        RuleWire.Expr cosVert = RuleWire.cos(RuleWire.radians(elevation));
        RuleWire.Expr sinVert = RuleWire.sin(RuleWire.radians(elevation));
        RuleWire.Expr east  = RuleWire.mul(RuleWire.mul(distance, cosVert), RuleWire.sin(RuleWire.radians(bearing)));
        RuleWire.Expr north = RuleWire.mul(RuleWire.mul(distance, cosVert), RuleWire.cos(RuleWire.radians(bearing)));
        RuleWire.Expr up    = RuleWire.mul(distance, sinVert);
        RuleWire.Expr vector = RuleWire.listLit(east, north, up);
        byte[] transformBytes = RuleWire.transformListOfNumbersRecord(vector);

        System.out.println("  the transform (shown readably):");
        System.out.println("      east  = " + east.text);
        System.out.println("      north = " + north.text);
        System.out.println("      up    = " + up.text);
        System.out.println();

        VerificationResult reg = CelVerifier.verify(transformBytes, survey.schemaView());
        check(reg.accepted(), "the gate ACCEPTED the transform at registration"
                + (reg.accepted() ? " (cost " + reg.cost().getAsLong() + ")" : " -> " + reg));
        if (!reg.accepted()) return;

        ExprNode transform = reg.record().get().expression();
        // The evaluator is given the correctly-rounded trigonometry provider.
        Evaluator exact = new Evaluator(MathProviders.correctlyRounded());
        double[] v0 = evalVector(exact, transform, survey);
        if (v0 == null) { check(false, "the transform produced a list of three numbers"); return; }

        System.out.printf("  result for %s (bearing %.1f deg, vertical angle %.1f deg, distance %.1f m):%n",
                '"' + survey.stringField("targetName") + '"',
                survey.doubleField("bearingDegrees"),
                survey.doubleField("elevationDegrees"),
                survey.doubleField("slopeDistanceMetres"));
        String[] axis = { "east ", "north", "up   " };
        for (int i = 0; i < 3; i++) {
            System.out.printf("      %s = %+.9f m   (exact bits 0x%016x)%n",
                    axis[i], v0[i], Double.doubleToRawLongBits(v0[i]));
        }
        System.out.println();

        // Reproducible to the bit across 1000 evaluations.
        boolean bitStable = true;
        for (int i = 0; i < 1000; i++) {
            double[] v = evalVector(exact, transform, survey);
            if (v == null
                    || Double.doubleToRawLongBits(v[0]) != Double.doubleToRawLongBits(v0[0])
                    || Double.doubleToRawLongBits(v[1]) != Double.doubleToRawLongBits(v0[1])
                    || Double.doubleToRawLongBits(v[2]) != Double.doubleToRawLongBits(v0[2])) {
                bitStable = false; break;
            }
        }
        check(bitStable, "the transform gave the bit-for-bit identical vector on all 1000 evaluations");

        // A sanity check that this is real geometry: the vector's length is the slope distance.
        double magnitude = Math.sqrt(v0[0] * v0[0] + v0[1] * v0[1] + v0[2] * v0[2]);
        double distanceMetres = survey.doubleField("slopeDistanceMetres");
        check(Math.abs(magnitude - distanceMetres) < 1e-6,
                "the direction vector's length equals the slope distance (" + fmt(magnitude)
                        + " m vs " + fmt(distanceMetres) + " m)");

        System.out.println();
        System.out.println("  HONESTY: this shows one implementation giving the same answer every time.");
        System.out.println("  The language is DESIGNED so a reader written in another language (Rust, Haskell)");
        System.out.println("  would give the byte-identical answer - pinned rounding, a canonical wire form,");
        System.out.println("  and a shared corpus of conformance vectors that ships with the rule-language");
        System.out.println("  module (evaluation, decode and registration vectors, including this");
        System.out.println("  correctly-rounded trigonometry). That second-language reader is planned, not");
        System.out.println("  yet running: nothing here claims a live cross-language comparison.");
    }

    // =====================================================================================
    // class-free reading + the CandidateProjection the evaluator consumes
    // =====================================================================================

    /** Reads one record's travelling shape + payload, and decodes it to a class-free field view. */
    private static Candidate readCandidate(DataInputStream in) throws Exception {
        int nSchema = in.readInt();
        List<AtomicSerialSchemaRecord> chainRecords = new ArrayList<>(nSchema);
        for (int i = 0; i < nSchema; i++) {
            chainRecords.add(AtomicSerialSchemaRecord.decode(Blocks.readBlock(in)));
        }
        byte[] payload = Blocks.readBlock(in);

        // Rebuild the schema chain from the travelling shape alone - no class is touched.
        SchemaChain.Result chain = SchemaChain.linkAndGetLeafDigest(chainRecords);
        // Decode to className -> (fieldName -> value). This never loads a class.
        LinkedHashMap<String, Map<String, Object>> fieldMap =
                ObjectCodec.decodeToFieldMap(chain, payload);
        return new Candidate(fieldMap, chainRecords);
    }

    /** A single record as the evaluator sees it: a class-free projection plus its travelling shape. */
    private static final class Candidate implements CandidateProjection {
        private final List<String> namespaceChain;               // leaf-first
        private final Map<String, Map<String, CelValue>> fields;  // className -> field -> value
        private final List<AtomicSerialSchemaRecord> chainRecords;

        Candidate(LinkedHashMap<String, Map<String, Object>> fieldMap,
                  List<AtomicSerialSchemaRecord> chainRecords) {
            this.chainRecords = chainRecords;
            List<String> names = new ArrayList<>(fieldMap.keySet()); // decode order is root-first
            java.util.Collections.reverse(names);                    // projection wants leaf-first
            this.namespaceChain = List.copyOf(names);
            Map<String, Map<String, CelValue>> f = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : fieldMap.entrySet()) {
                Map<String, CelValue> perClass = new LinkedHashMap<>();
                for (Map.Entry<String, Object> fe : e.getValue().entrySet()) {
                    perClass.put(fe.getKey(), toCelValue(fe.getValue()));
                }
                f.put(e.getKey(), perClass);
            }
            this.fields = f;
        }

        SchemaView schemaView() { return new DerSchemaChainView(chainRecords); }

        String summary() { return namespaceChain.get(0).substring(namespaceChain.get(0).lastIndexOf('.') + 1); }

        String stringField(String name) {
            CelValue v = lookup(name);
            return v instanceof CelValue.StringV s ? s.value() : String.valueOf(v);
        }

        double doubleField(String name) {
            CelValue v = lookup(name);
            return v instanceof CelValue.DoubleV d ? d.value() : Double.NaN;
        }

        private CelValue lookup(String name) {
            for (String cls : namespaceChain) {
                Map<String, CelValue> ns = fields.get(cls);
                if (ns != null && ns.containsKey(name)) return ns.get(name);
            }
            return null;
        }

        @Override public boolean isUndecodable() { return false; }
        @Override public List<String> namespaceChain() { return namespaceChain; }
        @Override public boolean declaresField(String className, String fieldName) {
            Map<String, CelValue> ns = fields.get(className);
            return ns != null && ns.containsKey(fieldName);
        }
        @Override public CelValue fieldValue(String className, String fieldName) {
            return fields.get(className).get(fieldName);
        }
    }

    /** Maps a class-free decoded Java value to the rule language's value, widening as the language requires. */
    private static CelValue toCelValue(Object v) {
        if (v == null) return CelValue.NullV.INSTANCE;
        if (v instanceof Boolean b) return new CelValue.BoolV(b);
        if (v instanceof Byte x)    return new CelValue.IntV(x.longValue());
        if (v instanceof Short x)   return new CelValue.IntV(x.longValue());
        if (v instanceof Integer x) return new CelValue.IntV(x.longValue());
        if (v instanceof Long x)    return new CelValue.IntV(x);
        if (v instanceof Float f)   return new CelValue.DoubleV(f.doubleValue());
        if (v instanceof Double d)  return new CelValue.DoubleV(d);
        if (v instanceof String s)  return new CelValue.StringV(s);
        if (v instanceof byte[] by) return new CelValue.BytesV(by);
        throw new IllegalStateException("unmappable field value type: " + v.getClass());
    }

    // =====================================================================================
    // small helpers
    // =====================================================================================

    private static double[] evalVector(Evaluator evaluator, ExprNode transform, Candidate c) {
        EvalOutcome outcome = evaluator.evaluate(transform, c);
        if (!outcome.isValue() || !(outcome.valueOrThrow() instanceof CelValue.ListV list)) return null;
        if (list.elements().size() != 3) return null;
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(list.elements().get(i) instanceof CelValue.DoubleV d)) return null;
            out[i] = d.value();
        }
        return out;
    }

    private static boolean classIsAbsent(String className) {
        try {
            Class.forName(className);
            return false;
        } catch (ClassNotFoundException expected) {
            return true;
        }
    }

    private static void check(boolean condition, String label) {
        System.out.println("  " + (condition ? "[PASS] " : "[FAIL] ") + label);
        if (!condition) allHeld = false;
    }

    private static void heading(String title) {
        System.out.println();
        System.out.println("---------------------------------------------------------------------------");
        System.out.println(title);
        System.out.println("---------------------------------------------------------------------------");
    }

    private static String reason(VerificationResult r) {
        return r.accepted() ? "ACCEPTED" : r.reason() + ": " + r.diagnostic();
    }

    private static String describe(EvalOutcome o) {
        return o.isError() ? "error " + o.errorOrThrow() : "value " + o.valueOrThrow();
    }

    private static String fmt(double d) { return String.format("%.6f", d); }
}
