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

package au.net.zeus.jgdms.bench.cel;

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.cel.authoring.CelEncodeException;
import au.net.zeus.jgdms.cel.authoring.CelEncoder;
import au.net.zeus.jgdms.cel.authoring.CelParseException;
import au.net.zeus.jgdms.cel.authoring.CelRecordBuilder;
import au.net.zeus.jgdms.cel.authoring.CelTextParser;
import au.net.zeus.jgdms.cel.cost.CostExceededException;
import au.net.zeus.jgdms.cel.cost.CostModel;
import au.net.zeus.jgdms.cel.verifier.CelVerifier;
import au.net.zeus.jgdms.cel.verifier.SchemaView;
import au.net.zeus.jgdms.cel.verifier.VerificationResult;
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;
import au.net.zeus.jgdms.cel.wire.ScalarType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The two STD-011 v0.1 expressions the stage-1 benchmarks evaluate, generated as TEXT and parsed with the production
 * {@link CelTextParser}:
 * <ul>
 *   <li><b>E1 vector</b>: azimuth/elevation/range to {@code [x, y, z]} with the rawscan convention
 *       {@code x = r cos(el) cos(az), y = r cos(el) sin(az), z = r sin(el)}. The structure and operation order are
 *       STD-011 §14.3's (range times cos(elevation) times the horizontal trigonometric term); the angle convention is
 *       NOT §14.3's (that example takes DEGREES, bearing clockwise from north, via {@code radians()}; the rawscan stores
 *       RADIANS, azimuth counter-clockwise from +X), so no {@code radians()} appears.</li>
 *   <li><b>E2 projection</b>: the FORWARD branch of the camera projection {@code PerImageCamera.projectWith}
 *       (Observations_to_Salsa, READ 2026-11-11), returning {@code [colCrop, rowCrop]}, with the camera rotation, offset
 *       and axes folded into one affine map by {@link Camera#synthetic()}.</li>
 * </ul>
 *
 * <h2>Why E2 is built by substitution and not parsed whole</h2>
 * STD-011 v0.1 has no variables (§5.4 item 4), so every reuse must be written out. Written out in full, E2 is about a
 * thousand times over {@code MAX_EXPR_NODES}, and its text is over the parser's own input cap, so
 * {@link CelTextParser#parse} refuses it before any tree exists (MEASURED: see {@link #inlinedLength}). To measure what
 * the evaluator would do with exactly that tree, each named intermediate is written as a small text template over the
 * data fields and earlier intermediates, parsed with the production parser (each is inside the ceilings), and the
 * placeholders are replaced by the already-built subtree INSTANCES. The result is the full tree the §5.4 author
 * would have to write, held as a DAG so that it fits in memory. The evaluator is a tree walker with no memoisation, so
 * evaluating the DAG does exactly the same OPERATIONS and allocations the tree does (review F6, Opus 2026-11-11: not the same
 * memory locality: the DAG is a few hundred cache-hot node objects where a materialised tree of this size would be tens of
 * megabytes, so tree-walking cost measured on the DAG is a lower bound for a tree that large; HYPOTHESIS, unmeasured).
 * Never call {@code equals}, {@code hashCode} or
 * {@code toString} on an E2 node: the records recurse over every path.
 * <p>
 * The substitution is checked, not trusted: for E1 and for the intermediates small enough to parse whole, the
 * substituted tree must be {@code equals} to the parse of the fully inlined text (see the correctness test).
 */
public final class Stage1Expressions {

    /** Names of the data fields the projection declares. */
    private static final String[] TRIG = {"SIN", "COS", "TAN", "ASIN", "ACOS", "ATAN", "ATAN2"};

    /**
     * SYNTHETIC camera constants: plausible in magnitude for a 4096 x 3072 sensor with a centred principal point and a
     * small radial and decentering distortion, but NOT any real camera's calibration. The twelve affine terms are the
     * result of folding a synthetic telescope pointing, a synthetic camera axis tilt and a synthetic offset, the fold
     * being done here in double arithmetic, once, client-side (the legitimate constant folding of the brief). No claim
     * is made that these resemble a real SX12 image; the expressions' SIZE and OPERATION MIX are what is measured.
     */
    public static final class Camera {
        public final double cx0, cx1, cx2, dx;
        public final double cy0, cy1, cy2, dy;
        public final double cz0, cz1, cz2, dz;
        public final double a3, a5, a7, a9;
        public final double pC, pR, twoPC, twoPR;
        public final double focalPx, ppCol, ppRow;

        private Camera(double[] cx, double dx, double[] cy, double dy, double[] cz, double dz) {
            this.cx0 = cx[0]; this.cx1 = cx[1]; this.cx2 = cx[2]; this.dx = dx;
            this.cy0 = cy[0]; this.cy1 = cy[1]; this.cy2 = cy[2]; this.dy = dy;
            this.cz0 = cz[0]; this.cz1 = cz[1]; this.cz2 = cz[2]; this.dz = dz;
            this.a3 = 0.0113;
            this.a5 = -0.0042;
            this.a7 = 0.0009;
            this.a9 = -0.00011;
            this.pC = 1.2e-4;
            this.pR = -8.0e-5;
            this.twoPC = 2.0 * pC;
            this.twoPR = 2.0 * pR;
            this.focalPx = 3100.0;
            this.ppCol = 2048.0;
            this.ppRow = 1536.0;
        }

        /** The synthetic camera used by every stage-1 run. Deterministic. */
        public static Camera synthetic() {
            double az0 = -0.8;
            double el0 = 0.1;
            double[] yT = {StrictMath.cos(el0) * StrictMath.cos(az0), StrictMath.cos(el0) * StrictMath.sin(az0),
                StrictMath.sin(el0)};
            double[] xT = unit(new double[] {yT[1], -yT[0], 0.0});
            double[] zT = cross(xT, yT);
            double[] tilt = {0.0021, -0.0013, 0.0007};
            double[] col = rotate(tilt, new double[] {1.0, 0.0, 0.0});
            double[] row = rotate(tilt, new double[] {0.0, 0.0, 1.0});
            double[] opt = rotate(tilt, new double[] {0.0, -1.0, 0.0});
            double[] offset = {0.0112, 0.0, 0.0735};
            return new Camera(toInstrument(xT, yT, zT, col), -dot(offset, col),
                    toInstrument(xT, yT, zT, row), -dot(offset, row),
                    toInstrument(xT, yT, zT, opt), -dot(offset, opt));
        }

        /** One line per constant, the form printed in the READY file. */
        public String describe() {
            StringBuilder sb = new StringBuilder(512);
            sb.append("SYNTHETIC camera (not any real calibration):\n");
            sb.append("  xc = ").append(cx0).append(" X + ").append(cx1).append(" Y + ").append(cx2).append(" Z + ")
                    .append(dx).append('\n');
            sb.append("  yc = ").append(cy0).append(" X + ").append(cy1).append(" Y + ").append(cy2).append(" Z + ")
                    .append(dy).append('\n');
            sb.append("  zc = ").append(cz0).append(" X + ").append(cz1).append(" Y + ").append(cz2).append(" Z + ")
                    .append(dz).append('\n');
            sb.append("  order3=").append(a3).append(" order5=").append(a5).append(" order7=").append(a7)
                    .append(" order9=").append(a9).append('\n');
            sb.append("  decentering pC=").append(pC).append(" pR=").append(pR).append(" (2pC=").append(twoPC)
                    .append(", 2pR=").append(twoPR).append(" folded)\n");
            sb.append("  focalPx=").append(focalPx).append(" principal point (col,row)=(").append(ppCol).append(", ")
                    .append(ppRow).append(")\n");
            return sb.toString();
        }

        private static double[] toInstrument(double[] xT, double[] yT, double[] zT, double[] axisInTelescope) {
            return new double[] {
                axisInTelescope[0] * xT[0] + axisInTelescope[1] * yT[0] + axisInTelescope[2] * zT[0],
                axisInTelescope[0] * xT[1] + axisInTelescope[1] * yT[1] + axisInTelescope[2] * zT[1],
                axisInTelescope[0] * xT[2] + axisInTelescope[1] * yT[2] + axisInTelescope[2] * zT[2]};
        }

        private static double dot(double[] a, double[] b) {
            return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        }

        private static double[] cross(double[] a, double[] b) {
            return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
        }

        private static double[] unit(double[] a) {
            double n = Math.sqrt(dot(a, a));
            return new double[] {a[0] / n, a[1] / n, a[2] / n};
        }

        /** Rodrigues rotation of {@code v} about the axis-angle vector {@code w}. */
        private static double[] rotate(double[] w, double[] v) {
            double theta = Math.sqrt(dot(w, w));
            double[] k = {w[0] / theta, w[1] / theta, w[2] / theta};
            double c = StrictMath.cos(theta);
            double s = StrictMath.sin(theta);
            double[] kxv = cross(k, v);
            double kv = dot(k, v);
            return new double[] {v[0] * c + kxv[0] * s + k[0] * kv * (1.0 - c), v[1] * c + kxv[1] * s + k[1] * kv * (1.0 - c),
                v[2] * c + kxv[2] * s + k[2] * kv * (1.0 - c)};
        }
    }

    /** The static facts reported for an expression: its size under the decoder's own counting rule, and its cost. */
    public record Measure(long nodes, int depth, long trigCallsSyntactic) {
    }

    private final Camera camera;
    private final LinkedHashMap<String, String> pieces;
    private final Map<String, Long> lengthMemo = new LinkedHashMap<>();

    private Stage1Expressions(Camera camera) {
        this.camera = camera;
        this.pieces = pieces(camera);
    }

    public static Stage1Expressions synthetic() {
        return new Stage1Expressions(Camera.synthetic());
    }

    /**
     * A copy with one template replaced. For the correctness test's NEGATIVE CONTROL only: a deliberately wrong tree must be
     * told apart from the native baseline, or the bit-for-bit comparison proves nothing.
     */
    Stage1Expressions withPiece(String name, String template) {
        Stage1Expressions copy = new Stage1Expressions(camera);
        if (!copy.pieces.containsKey(name)) {
            throw new IllegalArgumentException(new StringBuilder("no piece named ").append(name).toString());
        }
        copy.pieces.put(name, template);
        return copy;
    }

    public Camera camera() {
        return camera;
    }

    /** The named intermediates in dependency order: name to template text (placeholders are other names). */
    public Map<String, String> pieces() {
        return java.util.Collections.unmodifiableMap(pieces);
    }

    /** The fully inlined TEXT of E1, the text a §5.4 author would write: parsed whole by the production parser. */
    public String e1Text() {
        return inlinedText("e1", 100_000);
    }

    /** E1, parsed by {@link CelTextParser#parse} from {@link #e1Text()}. */
    public ExprNode e1() throws CelParseException {
        return CelTextParser.parse(e1Text());
    }

    /** E2 built by substitution (see the class comment); the full tree held as a DAG. */
    public ExprNode e2() throws CelParseException {
        return expand("e2");
    }

    /** The substituted tree for the named piece, every placeholder replaced by the shared subtree instance. */
    public ExprNode expand(String name) throws CelParseException {
        Map<String, ExprNode> done = new LinkedHashMap<>();
        for (Map.Entry<String, String> p : pieces.entrySet()) {
            done.put(p.getKey(), substitute(CelTextParser.parse(p.getValue()), done));
            if (p.getKey().equals(name)) {
                return done.get(name);
            }
        }
        throw new IllegalArgumentException(new StringBuilder("no piece named ").append(name).toString());
    }

    // ------------------------------------------------------------------------------------------- templates

    private static LinkedHashMap<String, String> pieces(Camera k) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("px", "range * cos(elevation) * cos(azimuth)");
        m.put("py", "range * cos(elevation) * sin(azimuth)");
        m.put("pz", "range * sin(elevation)");
        m.put("e1", "[px, py, pz]");
        m.put("xc", cat(lit(k.cx0), " * px + ", lit(k.cx1), " * py + ", lit(k.cx2), " * pz + ", lit(k.dx)));
        m.put("yc", cat(lit(k.cy0), " * px + ", lit(k.cy1), " * py + ", lit(k.cy2), " * pz + ", lit(k.dy)));
        m.put("zc", cat(lit(k.cz0), " * px + ", lit(k.cz1), " * py + ", lit(k.cz2), " * pz + ", lit(k.dz)));
        m.put("rho", "sqrt(xc * xc + yc * yc)");
        m.put("theta", "atan2(rho, -zc)");
        String tiny = lit(1e-15);
        m.put("ar", cat("(rho < ", tiny, ") ? 0.0 : theta * (xc / rho)"));
        m.put("br", cat("(rho < ", tiny, ") ? 0.0 : theta * (yc / rho)"));
        m.put("rs", cat("1.0 + ", lit(k.a3), " * (theta * theta) + ", lit(k.a5), " * (theta * theta) * (theta * theta) + ",
                lit(k.a7), " * (theta * theta) * (theta * theta) * (theta * theta) + ", lit(k.a9),
                " * (theta * theta) * (theta * theta) * (theta * theta) * (theta * theta)"));
        m.put("s", cat("(theta < ", tiny, ") ? 1.0 : (theta * rs) / theta"));
        m.put("a1", "ar * s");
        m.put("b1", "br * s");
        m.put("a2", cat("a1 + (", lit(k.pC), " * ((a1 * a1 + b1 * b1) + 2.0 * a1 * a1) + ", lit(k.twoPR), " * a1 * b1)"));
        m.put("b2", cat("b1 + (", lit(k.pR), " * ((a1 * a1 + b1 * b1) + 2.0 * b1 * b1) + ", lit(k.twoPC), " * a1 * b1)"));
        m.put("th2", "sqrt(a2 * a2 + b2 * b2)");
        m.put("rpx", cat(lit(k.focalPx), " * tan(th2)"));
        m.put("ux", cat("(th2 < ", tiny, ") ? 0.0 : a2 / th2"));
        m.put("uy", cat("(th2 < ", tiny, ") ? 0.0 : b2 / th2"));
        m.put("col", cat(lit(k.ppCol), " + rpx * ux"));
        m.put("row", cat(lit(k.ppRow), " + rpx * uy"));
        m.put("e2", "(zc >= 0.0) ? [-1.0, -1.0] : [col, row]");
        return m;
    }

    private static String cat(String... parts) {
        StringBuilder sb = new StringBuilder(160);
        for (String p : parts) {
            sb.append(p);
        }
        return sb.toString();
    }

    /** A double as CEL source: {@code Double.toString} round-trips exactly; a negative value is parenthesised. */
    static String lit(double v) {
        if (!Double.isFinite(v) || v == 0.0) {
            throw new IllegalArgumentException("a zero or non-finite constant has no place in these expressions");
        }
        StringBuilder sb = new StringBuilder(32);
        if (v < 0.0) {
            sb.append("(-").append(Double.toString(-v)).append(')');
        } else {
            sb.append(Double.toString(v));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------- inlining (text)

    /** Template text with every placeholder replaced by the parenthesised inlined text of that piece. */
    public String inlinedText(String name, int maxChars) {
        if (inlinedLength(name) > maxChars) {
            throw new IllegalStateException(new StringBuilder(name).append(" inlines to ").append(inlinedLength(name))
                    .append(" characters, over the requested ").append(maxChars).toString());
        }
        return inline(name);
    }

    private String inline(String name) {
        String template = pieces.get(name);
        StringBuilder sb = new StringBuilder(template.length() * 4);
        scan(template, (token, isPlaceholder) -> {
            if (isPlaceholder) {
                sb.append('(').append(inline(token)).append(')');
            } else {
                sb.append(token);
            }
        });
        return sb.toString();
    }

    /**
     * Length in characters of the fully inlined text of {@code name}, computed from the templates (memoised) without
     * building the text. For E2 this is the evidence that the text cannot even be handed to the production parser, whose
     * input cap is 1,000,000 characters ({@code CelTextParser.MAX_INPUT_CHARS}).
     */
    public long inlinedLength(String name) {
        Long hit = lengthMemo.get(name);
        if (hit != null) {
            return hit;
        }
        long[] total = {0L};
        scan(pieces.get(name), (token, isPlaceholder) -> {
            total[0] += isPlaceholder ? inlinedLength(token) + 2L : token.length();
        });
        lengthMemo.put(name, total[0]);
        return total[0];
    }

    private interface TokenSink {
        void accept(String token, boolean isPlaceholder);
    }

    /** Splits a template into literal runs and placeholder names; an identifier glued to a number (the E of 1.0E-15) is not one. */
    private void scan(String template, TokenSink sink) {
        int i = 0;
        int runStart = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            boolean startsIdent = (Character.isLetter(c) || c == '_')
                    && (i == 0 || !(Character.isLetterOrDigit(template.charAt(i - 1)) || template.charAt(i - 1) == '_'
                    || template.charAt(i - 1) == '.'));
            if (!startsIdent) {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < n && (Character.isLetterOrDigit(template.charAt(j)) || template.charAt(j) == '_')) {
                j++;
            }
            String ident = template.substring(i, j);
            if (pieces.containsKey(ident)) {
                if (runStart < i) {
                    sink.accept(template.substring(runStart, i), false);
                }
                sink.accept(ident, true);
                runStart = j;
            }
            i = j;
        }
        if (runStart < n) {
            sink.accept(template.substring(runStart), false);
        }
    }

    // ------------------------------------------------------------------------------------------- substitution (trees)

    private static ExprNode substitute(ExprNode node, Map<String, ExprNode> defs) {
        return switch (node) {
            case ExprNode.LitDouble d -> d;
            case ExprNode.LitInt i -> i;
            case ExprNode.LitBool b -> b;
            case ExprNode.FieldRef f -> {
                if (f.steps().size() == 1 && f.steps().get(0) instanceof SelectorStep.Unqual u && defs.containsKey(u.name())) {
                    yield defs.get(u.name());
                }
                yield f;
            }
            case ExprNode.Neg x -> new ExprNode.Neg(substitute(x.operand(), defs));
            case ExprNode.Add x -> new ExprNode.Add(substitute(x.left(), defs), substitute(x.right(), defs));
            case ExprNode.Sub x -> new ExprNode.Sub(substitute(x.left(), defs), substitute(x.right(), defs));
            case ExprNode.Mul x -> new ExprNode.Mul(substitute(x.left(), defs), substitute(x.right(), defs));
            case ExprNode.Div x -> new ExprNode.Div(substitute(x.left(), defs), substitute(x.right(), defs));
            case ExprNode.Lt x -> new ExprNode.Lt(substitute(x.left(), defs), substitute(x.right(), defs));
            case ExprNode.Ge x -> new ExprNode.Ge(substitute(x.left(), defs), substitute(x.right(), defs));
            case ExprNode.Cond c -> new ExprNode.Cond(substitute(c.condition(), defs), substitute(c.thenBranch(), defs),
                    substitute(c.elseBranch(), defs));
            case ExprNode.Call c -> {
                List<ExprNode> args = new ArrayList<>(c.arguments().size());
                for (ExprNode a : c.arguments()) {
                    args.add(substitute(a, defs));
                }
                yield new ExprNode.Call(c.functionId(), args);
            }
            case ExprNode.ListLit l -> {
                List<ExprNode> els = new ArrayList<>(l.elements().size());
                for (ExprNode e : l.elements()) {
                    els.add(substitute(e, defs));
                }
                yield new ExprNode.ListLit(els);
            }
            default -> throw new IllegalStateException(
                    new StringBuilder("substitution does not handle ").append(node.getClass().getSimpleName()).toString());
        };
    }

    // ------------------------------------------------------------------------------------------- measurement

    /**
     * Node count by the DECODER's rule (CelAstValidator.visitExpr, READ 2026-11-11: every expression node counts one, a
     * field reference also counts one per selector step, list elements are expression nodes), maximum depth (root = 1; a
     * field reference adds none), and the number of transcendental calls counted per path (both branches of every
     * {@code ?:}, so SYNTACTIC, not the number one evaluation makes). Counted by identity memoisation over the DAG, with
     * multiplicity: the same number a tree walk would give.
     */
    public static Measure measure(ExprNode root) {
        return toMeasure(count(root, new IdentityHashMap<>()));
    }

    private record Counted(long nodes, int depth, long trig) {
    }

    /**
     * WHY this is a third copy of the decoder's counting rule (after {@code CelDecoder} and
     * {@code CelAstValidator.visitExpr}; review F9, Opus 2026-11-11): the production counter is package-private
     * ({@code CelAstValidator} is a package-private final class in {@code jgdms-cel-authoring}), so no code in this module
     * can call it, and stage 1 may not edit that module. The copy is kept honest by the correctness test
     * {@code ourNodeAndDepthCountsAgreeWithTheProductionCeilingsAtTheirBoundaries}, which checks it against the production
     * parser at 1,023 and 1,024 nodes and at depth 32 and 33. When the production side exposes its counter, delete this one.
     * It is also a counter over a DAG: it memoises by instance identity, which the production counter (a tree walker) never needs.
     */
    private static Counted count(ExprNode n, IdentityHashMap<ExprNode, Counted> memo) {
        Counted hit = memo.get(n);
        if (hit != null) {
            return hit;
        }
        long nodes = 1;
        int depth = 0;
        long trig = 0;
        List<ExprNode> kids = children(n);
        for (ExprNode c : kids) {
            Counted cc = count(c, memo);
            nodes += cc.nodes();
            depth = Math.max(depth, cc.depth());
            trig += cc.trig();
        }
        if (n instanceof ExprNode.FieldRef f) {
            nodes += f.steps().size();
        }
        if (n instanceof ExprNode.Call c) {
            String name = FunctionRegistry.byId(c.functionId()).name().name();
            for (String t : TRIG) {
                if (t.equals(name)) {
                    trig++;
                }
            }
        }
        Counted out = new Counted(nodes, depth + 1, trig);
        memo.put(n, out);
        return out;
    }

    private static List<ExprNode> children(ExprNode n) {
        return switch (n) {
            case ExprNode.LitDouble d -> List.of();
            case ExprNode.LitInt i -> List.of();
            case ExprNode.LitBool b -> List.of();
            case ExprNode.FieldRef f -> List.of();
            case ExprNode.Neg x -> List.of(x.operand());
            case ExprNode.Add x -> List.of(x.left(), x.right());
            case ExprNode.Sub x -> List.of(x.left(), x.right());
            case ExprNode.Mul x -> List.of(x.left(), x.right());
            case ExprNode.Div x -> List.of(x.left(), x.right());
            case ExprNode.Lt x -> List.of(x.left(), x.right());
            case ExprNode.Ge x -> List.of(x.left(), x.right());
            case ExprNode.Cond c -> List.of(c.condition(), c.thenBranch(), c.elseBranch());
            case ExprNode.Call c -> c.arguments();
            case ExprNode.ListLit l -> l.elements();
            default -> throw new IllegalStateException(
                    new StringBuilder("measure does not handle ").append(n.getClass().getSimpleName()).toString());
        };
    }

    /** Helper kept on the record so {@link #measure} reads as one expression. */
    private static Measure toMeasure(Counted c) {
        return new Measure(c.nodes(), c.depth(), c.trig());
    }

    // ------------------------------------------------------------------------------------------- verdicts

    /** The schema a registering consumer would hold for the projection: three double fields in one namespace. */
    public static final class Schema implements SchemaView {
        @Override
        public List<String> namespaceChain() {
            return List.of(RecordProjection.CLASS_NAME);
        }

        @Override
        public boolean declaresField(String className, String fieldName) {
            return RecordProjection.CLASS_NAME.equals(className)
                    && ("range".equals(fieldName) || "azimuth".equals(fieldName) || "elevation".equals(fieldName));
        }

        @Override
        public Optional<CelType> fieldType(String className, String fieldName) {
            return declaresField(className, fieldName) ? Optional.of(CelType.DOUBLE) : Optional.empty();
        }

        @Override
        public Optional<SchemaView> nestedSchema(String className, String fieldName) {
            return Optional.empty();
        }
    }

    /**
     * The production admission verdict for a transform returning {@code list<double>}: encode (which enforces the decoder's
     * ceilings itself), then {@link CelVerifier#verify(byte[], SchemaView)}. {@code CelVerifier} takes wire bytes only, so an
     * expression the encoder refuses never reaches it: the refusal text is then the verdict, and it is the ENCODER's.
     */
    public static String admissionVerdict(ExprNode root, SchemaView schema) {
        byte[] wire;
        try {
            wire = CelEncoder.encode(CelRecordBuilder.transformList(root, ScalarType.DOUBLE_T));
        } catch (CelEncodeException e) {
            return new StringBuilder("REFUSED by CelEncoder (the decoder's ceilings): ").append(e.getMessage())
                    .append(" [CelVerifier.verify not callable: it takes wire bytes only]").toString();
        }
        VerificationResult r = CelVerifier.verify(wire, schema);
        StringBuilder sb = new StringBuilder(160);
        sb.append("CelVerifier.verify(wire[").append(wire.length).append(" bytes], ")
                .append(schema == null ? "no schema" : "schema").append("): ");
        if (r.accepted()) {
            sb.append("ACCEPTED, static cost ").append(r.cost().getAsLong());
        } else {
            sb.append("REJECTED ").append(r.reason()).append(": ").append(r.diagnostic());
        }
        return sb.toString();
    }

    /** C(E) from the production cost model, or its refusal text (which carries the computed total). */
    public static String staticCost(ExprNode root) {
        try {
            return new StringBuilder("C(E) = ").append(CostModel.computeCost(root)).append(" (maxExprCost 1000000)").toString();
        } catch (CostExceededException e) {
            return new StringBuilder("CostModel refused: ").append(e.getMessage()).toString();
        }
    }
}
