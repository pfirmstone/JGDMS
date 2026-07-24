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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a rule in the canonical wire format, and, at the same time, the readable
 * pseudo-syntax for that same rule, so the two can never drift apart.
 *
 * <p>The rule language has no text form to type — a rule is authored as a tree and
 * travels as canonical bytes. So a rule is built here by composing small pieces
 * ({@link Expr}), each of which carries BOTH its wire bytes and a short readable line.
 * The reader prints the readable line and hands the bytes to the real gate and the
 * real evaluator; nothing here interprets the rule.
 *
 * <p>This is a small, self-contained byte builder — it has no dependency on the record
 * classes, and no dependency on the rule-language library. It only lays out the bytes
 * the way the canonical format defines; the library's own gate then checks them.
 */
public final class RuleWire {

    private RuleWire() {}

    /** A rule fragment: its canonical wire bytes, and the readable line for those same bytes. */
    public static final class Expr {
        public final byte[] wire;
        public final String text;
        Expr(byte[] wire, String text) { this.wire = wire; this.text = text; }
    }

    // ---- leaf values --------------------------------------------------------

    public static Expr litBool(boolean v) {
        return new Expr(ctxPrim(0, new byte[]{(byte) (v ? 0xFF : 0x00)}), Boolean.toString(v));
    }

    public static Expr litInt(long v) {
        return new Expr(ctxPrim(1, minimalIntContent(v)), Long.toString(v));
    }

    public static Expr litDouble(double v) {
        long bits = Double.doubleToRawLongBits(v);
        byte[] content = new byte[8];
        for (int i = 7; i >= 0; i--) { content[i] = (byte) (bits & 0xFF); bits >>>= 8; }
        return new Expr(ctxPrim(2, content), trimDouble(v));
    }

    public static Expr litString(String s) {
        return new Expr(ctxPrim(3, s.getBytes(StandardCharsets.UTF_8)), '"' + s + '"');
    }

    /** A field reference by (unqualified) name, e.g. {@code field("temperatureCelsius")}. */
    public static Expr field(String name) {
        byte[] step = ctxPrim(0, name.getBytes(StandardCharsets.UTF_8)); // unqualified selector step
        return new Expr(ctxCons(7, universalSeq(step)), name);
    }

    // ---- operators ----------------------------------------------------------

    public static Expr and(Expr l, Expr r) { return new Expr(ctxCons(11, l.wire, r.wire), "(" + l.text + " AND " + r.text + ")"); }
    public static Expr or(Expr l, Expr r)  { return new Expr(ctxCons(12, l.wire, r.wire), "(" + l.text + " OR " + r.text + ")"); }
    public static Expr gt(Expr l, Expr r)  { return new Expr(ctxCons(17, l.wire, r.wire), l.text + " > " + r.text); }
    public static Expr ge(Expr l, Expr r)  { return new Expr(ctxCons(18, l.wire, r.wire), l.text + " >= " + r.text); }
    public static Expr mul(Expr l, Expr r) { return new Expr(ctxCons(21, l.wire, r.wire), l.text + " * " + r.text); }

    // ---- platform functions (ids from the pinned function table) ------------

    public static Expr startsWith(Expr receiver, String needle) {
        return call(23, receiver.text + " starts-with \"" + needle + "\"", receiver, litString(needle));
    }

    public static Expr contains(Expr receiver, String needle) {
        return call(22, receiver.text + " contains \"" + needle + "\"", receiver, litString(needle));
    }

    public static Expr radians(Expr x) { return call(13, "radians(" + x.text + ")", x); }
    public static Expr sin(Expr x)     { return call(15, "sin(" + x.text + ")", x); }
    public static Expr cos(Expr x)     { return call(16, "cos(" + x.text + ")", x); }

    private static Expr call(int functionId, String text, Expr... args) {
        byte[][] argWire = new byte[args.length][];
        for (int i = 0; i < args.length; i++) argWire[i] = args[i].wire;
        byte[] wire = ctxCons(26, universalInt(minimalIntContent(functionId)), universalSeq(argWire));
        return new Expr(wire, text);
    }

    // ---- collections --------------------------------------------------------

    /** {@code [ e0, e1, ... ]} — a list literal (used as a transform's result). */
    public static Expr listLit(Expr... elements) {
        byte[][] elWire = new byte[elements.length][];
        StringBuilder text = new StringBuilder("[ ");
        for (int i = 0; i < elements.length; i++) {
            elWire[i] = elements[i].wire;
            if (i > 0) text.append(", ");
            text.append(elements[i].text);
        }
        text.append(" ]");
        return new Expr(ctxCons(6, elWire), text.toString());
    }

    /** {@code needle in [ e0, e1, ... ]} over a literal list. */
    public static Expr inLiteralList(Expr needle, List<Expr> elements) {
        byte[][] elWire = new byte[elements.size()][];
        for (int i = 0; i < elements.size(); i++) elWire[i] = elements.get(i).wire;
        byte[] wire = ctxCons(24, needle.wire, ctxCons(0, elWire));
        return new Expr(wire, needle.text + " in [ ..." + elements.size() + " values... ]");
    }

    // ---- record envelopes ---------------------------------------------------

    /** Wraps a rule whose result is a yes/no answer (a predicate) as a complete rule record. */
    public static byte[] predicateRecord(Expr root) {
        byte[] context = ctxPrim(0, new byte[0]); // predicate context
        return universalSeq(universalInt(minimalIntContent(1)), context, root.wire);
    }

    /** Wraps a rule whose result is a list of numbers (a transform) as a complete rule record. */
    public static byte[] transformListOfNumbersRecord(Expr root) {
        // context [1] EXPLICIT { declared result type = list-of ScalarType(double) }
        byte[] declaredResultType = ctxPrim(2, minimalIntContent(2)); // listType[2] IMPLICIT ScalarType doubleT(=2)
        byte[] context = tlv(contextTag(1, true), declaredResultType);
        return universalSeq(universalInt(minimalIntContent(1)), context, root.wire);
    }

    // =========================================================================
    // canonical TLV layout helpers (no interpretation — only byte layout)
    // =========================================================================

    private static byte[] tlv(byte[] tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(tag);
        out.writeBytes(minimalLength(content.length));
        out.writeBytes(content);
        return out.toByteArray();
    }

    private static byte[] contextTag(int number, boolean constructed) {
        int b = 0x80 | (constructed ? 0x20 : 0x00) | number;
        return new byte[]{(byte) b};
    }

    private static byte[] ctxPrim(int number, byte[] content) { return tlv(contextTag(number, false), content); }

    private static byte[] ctxCons(int number, byte[]... children) { return tlv(contextTag(number, true), concat(children)); }

    private static byte[] universal(int tagNumber, boolean constructed, byte[] content) {
        int b = (constructed ? 0x20 : 0x00) | tagNumber;
        return tlv(new byte[]{(byte) b}, content);
    }

    private static byte[] universalSeq(byte[]... children) { return universal(0x10, true, concat(children)); }
    private static byte[] universalInt(byte[] content)     { return universal(0x02, false, content); }

    private static byte[] minimalIntContent(long value) {
        if (value == 0) return new byte[]{0x00};
        return java.math.BigInteger.valueOf(value).toByteArray(); // already minimal two's-complement
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    private static byte[] minimalLength(int len) {
        if (len <= 0x7F) return new byte[]{(byte) len};
        List<Byte> bytes = new ArrayList<>();
        int n = len;
        while (n > 0) { bytes.add(0, (byte) (n & 0xFF)); n >>>= 8; }
        byte[] out = new byte[bytes.size() + 1];
        out[0] = (byte) (0x80 | bytes.size());
        for (int i = 0; i < bytes.size(); i++) out[i + 1] = bytes.get(i);
        return out;
    }

    private static String trimDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            // keep one decimal so a reader sees it is a real-number literal, e.g. 20.0
            return (long) v + ".0";
        }
        return Double.toString(v);
    }
}
