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

package au.net.zeus.jgdms.cel.testsupport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A tiny, hand-rolled, test-only DER TLV builder for constructing
 * Appendix-B-shaped wire vectors (nested structures, deliberately malformed
 * probes) without needing a full ASN.1 toolchain in the test classpath. Not
 * production code -- this is a builder, not a validator; it happily
 * constructs canonical encodings by construction, and lets tests construct
 * non-canonical ones deliberately when that's the point of the vector.
 */
public final class TestDer {

    private TestDer() {}

    /** Builds a minimal-length-form DER TLV: tag byte(s) + minimal length + content. */
    public static byte[] tlv(byte[] tagBytes, byte[] content) {
        byte[] lenBytes = minimalLength(content.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(out, tagBytes, lenBytes, content);
        return out.toByteArray();
    }

    /** A single-byte low-tag-number-form context tag: class=CONTEXT(2), constructed flag, tag number 0-30. */
    public static byte[] contextTag(int number, boolean constructed) {
        if (number < 0 || number > 30) throw new IllegalArgumentException("use only for low-tag-number form: " + number);
        int b = 0x80 | (constructed ? 0x20 : 0x00) | number;
        return new byte[] { (byte) b };
    }

    public static byte[] ctxPrim(int number, byte[] content) {
        return tlv(contextTag(number, false), content);
    }

    public static byte[] ctxCons(int number, byte[]... children) {
        return tlv(contextTag(number, true), concat(children));
    }

    public static byte[] universal(int tagNumber, boolean constructed, byte[] content) {
        int b = (constructed ? 0x20 : 0x00) | tagNumber;
        return tlv(new byte[] { (byte) b }, content);
    }

    public static byte[] universalSeq(byte[]... children) {
        return universal(0x10, true, concat(children));
    }

    public static byte[] universalInt(byte[] content) {
        return universal(0x02, false, content);
    }

    public static byte[] universalUtf8(String s) {
        return universal(0x0C, false, s.getBytes(StandardCharsets.UTF_8));
    }

    /** Minimal two's-complement content octets for a signed long value. */
    public static byte[] minimalIntContent(long value) {
        if (value == 0) return new byte[] { 0x00 };
        java.math.BigInteger bi = java.math.BigInteger.valueOf(value);
        return bi.toByteArray(); // BigInteger.toByteArray() already yields minimal two's-complement form
    }

    /** Minimal two's-complement content octets for an arbitrary-precision signed value (for out-of-int64-range probes). */
    public static byte[] minimalIntContent(java.math.BigInteger value) {
        return value.toByteArray();
    }

    public static byte[] litBool(boolean v) {
        return ctxPrim(0, new byte[] { (byte) (v ? 0xFF : 0x00) });
    }

    public static byte[] litInt(long v) {
        return ctxPrim(1, minimalIntContent(v));
    }

    public static byte[] litDouble(double v) {
        long bits = Double.doubleToRawLongBits(v);
        byte[] content = new byte[8];
        for (int i = 7; i >= 0; i--) {
            content[i] = (byte) (bits & 0xFF);
            bits >>>= 8;
        }
        return ctxPrim(2, content);
    }

    public static byte[] litDoubleBits(long bits) {
        byte[] content = new byte[8];
        long b = bits;
        for (int i = 7; i >= 0; i--) {
            content[i] = (byte) (b & 0xFF);
            b >>>= 8;
        }
        return ctxPrim(2, content);
    }

    public static byte[] litString(String s) {
        return ctxPrim(3, s.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] litStringRaw(byte[] rawUtf8Bytes) {
        return ctxPrim(3, rawUtf8Bytes);
    }

    public static byte[] litBytes(byte[] b) {
        return ctxPrim(4, b);
    }

    public static byte[] litNull() {
        return ctxPrim(5, new byte[0]);
    }

    public static byte[] listLit(byte[]... elements) {
        return ctxCons(6, elements);
    }

    private static byte[] unqualStep(String name) {
        return ctxPrim(0, name.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] qualStep(String className, String fieldName) {
        return ctxCons(1, universalUtf8(className), universalUtf8(fieldName));
    }

    /** {@code fieldRef("a","b",...)} -- unqualified steps only, the common case. */
    public static byte[] fieldRef(String... unqualNames) {
        byte[][] steps = new byte[unqualNames.length][];
        for (int i = 0; i < unqualNames.length; i++) steps[i] = unqualStep(unqualNames[i]);
        return ctxCons(7, universalSeq(steps));
    }

    public static byte[] fieldRefQualified(String className, String fieldName) {
        return ctxCons(7, universalSeq(qualStep(className, fieldName)));
    }

    /** A FIELD_REF with an arbitrary, possibly-invalid number of unqual steps (fencepost/regression probes). */
    public static byte[] fieldRefNSteps(int n, String prefix) {
        byte[][] steps = new byte[n][];
        for (int i = 0; i < n; i++) steps[i] = unqualStep(prefix + i);
        return ctxCons(7, universalSeq(steps));
    }

    public static byte[] fieldRefZeroSteps() {
        return ctxCons(7, universalSeq());
    }

    public static byte[] has(String... unqualNames) {
        byte[][] steps = new byte[unqualNames.length][];
        for (int i = 0; i < unqualNames.length; i++) steps[i] = unqualStep(unqualNames[i]);
        return ctxCons(8, universalSeq(universalSeq(steps)));
    }

    public static byte[] notOp(byte[] operand) { return ctxCons(9, operand); }
    public static byte[] negOp(byte[] operand) { return ctxCons(10, operand); }

    public static byte[] and(byte[] l, byte[] r) { return ctxCons(11, l, r); }
    public static byte[] or(byte[] l, byte[] r) { return ctxCons(12, l, r); }
    public static byte[] eq(byte[] l, byte[] r) { return ctxCons(13, l, r); }
    public static byte[] ne(byte[] l, byte[] r) { return ctxCons(14, l, r); }
    public static byte[] lt(byte[] l, byte[] r) { return ctxCons(15, l, r); }
    public static byte[] le(byte[] l, byte[] r) { return ctxCons(16, l, r); }
    public static byte[] gt(byte[] l, byte[] r) { return ctxCons(17, l, r); }
    public static byte[] ge(byte[] l, byte[] r) { return ctxCons(18, l, r); }
    public static byte[] add(byte[] l, byte[] r) { return ctxCons(19, l, r); }
    public static byte[] sub(byte[] l, byte[] r) { return ctxCons(20, l, r); }
    public static byte[] mul(byte[] l, byte[] r) { return ctxCons(21, l, r); }
    public static byte[] div(byte[] l, byte[] r) { return ctxCons(22, l, r); }
    public static byte[] mod(byte[] l, byte[] r) { return ctxCons(23, l, r); }

    public static byte[] inLiteralList(byte[] needle, byte[]... listElements) {
        return ctxCons(24, needle, ctxCons(0, listElements));
    }

    public static byte[] inFieldList(byte[] needle, String... unqualNames) {
        byte[][] steps = new byte[unqualNames.length][];
        for (int i = 0; i < unqualNames.length; i++) steps[i] = unqualStep(unqualNames[i]);
        return ctxCons(24, needle, ctxCons(1, universalSeq(steps)));
    }

    public static byte[] cond(byte[] c, byte[] t, byte[] e) { return ctxCons(25, c, t, e); }

    public static byte[] call(int functionId, byte[]... args) {
        return ctxCons(26, universalInt(minimalIntContent(functionId)), universalSeq(args));
    }

    // ---- envelope helpers ---------------------------------------------------

    public static byte[] predicateContext() {
        return ctxPrim(0, new byte[0]);
    }

    public static byte[] transformContextListDouble() {
        // context [1] EXPLICIT DeclaredResultType.listType[2] IMPLICIT ScalarType(doubleT=2)
        byte[] inner = ctxPrim(2, minimalIntContent(2));
        return tlv(contextTag(1, true), inner);
    }

    public static byte[] filterRecord(int formatVersion, byte[] context, byte[] expression) {
        return universalSeq(universalInt(minimalIntContent(formatVersion)), context, expression);
    }

    /** Wraps a bare {@code ExprNode} fragment as a complete, decodable {@code CelFilterRecord} (formatVersion 1, predicate context) -- the common case for exercising the expression-level decoder against a single fragment. */
    public static byte[] wrapAsPredicateRecord(byte[] exprBytes) {
        return filterRecord(1, predicateContext(), exprBytes);
    }

    // ---- generic helpers ---------------------------------------------------

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(out, parts);
        return out.toByteArray();
    }

    private static void writeAll(ByteArrayOutputStream out, byte[]... parts) {
        for (byte[] p : parts) {
            out.write(p, 0, p.length);
        }
    }

    private static byte[] minimalLength(int len) {
        if (len < 0) throw new IllegalArgumentException("negative length");
        if (len <= 0x7F) {
            return new byte[] { (byte) len };
        }
        List<Byte> bytes = new ArrayList<>();
        int n = len;
        while (n > 0) {
            bytes.add(0, (byte) (n & 0xFF));
            n >>>= 8;
        }
        byte[] out = new byte[bytes.size() + 1];
        out[0] = (byte) (0x80 | bytes.size());
        for (int i = 0; i < bytes.size(); i++) out[i + 1] = bytes.get(i);
        return out;
    }

    /** Builds a non-canonical long-form length TLV (deliberately non-minimal) for canonical-form reject probes. */
    public static byte[] tlvLongFormLength(byte[] tagBytes, byte[] content, int lengthOctetsToUse) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(out, tagBytes);
        byte[] lenOctets = new byte[lengthOctetsToUse];
        int len = content.length;
        for (int i = lengthOctetsToUse - 1; i >= 0; i--) {
            lenOctets[i] = (byte) (len & 0xFF);
            len >>>= 8;
        }
        out.write(0x80 | lengthOctetsToUse);
        writeAll(out, lenOctets, content);
        return out.toByteArray();
    }

    public static byte[] hexToBytes(String hex) {
        String cleaned = hex.replaceAll("\\s", "");
        int len = cleaned.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(cleaned.charAt(i), 16) << 4)
                    + Character.digit(cleaned.charAt(i + 1), 16));
        }
        return data;
    }
}
