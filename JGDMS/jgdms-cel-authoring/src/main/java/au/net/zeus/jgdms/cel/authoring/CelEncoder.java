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

package au.net.zeus.jgdms.cel.authoring;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.ast.ExprNode.InListOperand;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;
import au.net.zeus.jgdms.cel.wire.DeclaredResultType;
import au.net.zeus.jgdms.cel.wire.EvaluationContext;
import au.net.zeus.jgdms.cel.wire.ScalarType;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * The T-CEL-B wire encoder: {@code CelFilterRecord} / {@code ExprNode} to the
 * canonical Appendix B DER wire form -- the exact tags the jgdms-cel wire
 * decoder ({@code CelDecoder}) consumes.
 * <p>
 * <b>Canonicality (G1) is the core contract: one AST encodes to exactly one
 * byte string, and that string is what the decoder round-trips back to the same
 * AST.</b> Every construct here emits the minimal DER form -- minimal
 * tag/length octets (via {@link DerWriter}), minimal two's-complement INTEGER
 * (LIT_INT / functionId / formatVersion) and ENUMERATED (ScalarType), BOOLEAN
 * TRUE as exactly {@code 0xFF}, order-preserving {@code SEQUENCE OF} for list
 * elements / selector steps / call arguments (semantic order, never sorted).
 * The encoder cannot emit a non-canonical form: it has no code path that would
 * (no long-form length where short suffices -- {@code DerWriter.encodeLength}
 * is minimal; no redundant INTEGER bytes -- {@code BigInteger.toByteArray} is
 * minimal; no non-{@code 0xFF} TRUE).
 * <p>
 * The one value the AST types admit but the canonical form forbids -- a
 * non-finite {@code LIT_DOUBLE} (Appendix B §B.5 item 5) -- is rejected with
 * {@link CelEncodeException}, mirroring the decoder. The encoder also runs the
 * shared {@link CelAstValidator} first, so a ceiling-violating hand-built AST is
 * refused rather than encoded into decoder-rejected bytes.
 */
public final class CelEncoder {

    private CelEncoder() {}

    // ---- public API -------------------------------------------------------

    /**
     * Encodes a complete {@code CelFilterRecord} to its canonical Appendix B
     * §B.9 DER wire form.
     *
     * @param record the record (formatVersion, context, expression)
     * @return the canonical DER encoding; {@code CelDecoder.decode(encode(r))}
     *         reconstructs an AST equal to {@code r}
     * @throws CelEncodeException if the expression carries a non-finite
     *         {@code LIT_DOUBLE} or violates a {@code CelCeilings} bound
     */
    public static byte[] encode(CelFilterRecord record) throws CelEncodeException {
        if (record == null) throw new NullPointerException("record");
        if (record.formatVersion() != CelFilterRecord.FORMAT_VERSION) {
            throw new CelEncodeException("formatVersion " + record.formatVersion()
                    + " is not the pinned value " + CelFilterRecord.FORMAT_VERSION
                    + " (Appendix B §B.9); the decoder would reject any other value");
        }
        String v = CelAstValidator.findWireViolation(record.expression());
        if (v != null) throw new CelEncodeException(v);

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeAll(content, DerWriter.writeInteger(record.formatVersion()));   // 02 .. formatVersion
        writeAll(content, encodeContext(record.context()));
        writeAll(content, encodeExpr(record.expression()));
        return DerWriter.writeSequence(content.toByteArray());
    }

    /**
     * Encodes a bare {@code ExprNode} to its canonical Appendix B
     * context-tagged TLV (the {@code expression} field's own encoding, without
     * the {@code CelFilterRecord} envelope).
     *
     * @param node the AST node
     * @return the canonical DER encoding of the node
     * @throws CelEncodeException on a non-finite {@code LIT_DOUBLE} or ceiling violation
     */
    public static byte[] encodeExpression(ExprNode node) throws CelEncodeException {
        if (node == null) throw new NullPointerException("node");
        String v = CelAstValidator.findWireViolation(node);
        if (v != null) throw new CelEncodeException(v);
        return encodeExpr(node);
    }

    // ---- context / envelope ----------------------------------------------

    private static byte[] encodeContext(EvaluationContext ctx) throws CelEncodeException {
        return switch (ctx) {
            case EvaluationContext.Predicate p -> ctxTlv(0, false, new byte[0]);   // [0] IMPLICIT NULL
            case EvaluationContext.Transform t ->
                    // [1] EXPLICIT DeclaredResultType: the inner TLV survives intact under the wrapper.
                    ctxTlv(1, true, encodeDeclaredResultType(t.resultType()));
        };
    }

    private static byte[] encodeDeclaredResultType(DeclaredResultType drt) {
        return switch (drt) {
            case DeclaredResultType.Scalar s -> ctxTlv(0, false, enumeratedContent(s.type()));
            case DeclaredResultType.NullType n -> ctxTlv(1, false, new byte[0]);
            case DeclaredResultType.ListType l -> ctxTlv(2, false, enumeratedContent(l.elementType()));
            case DeclaredResultType.ObjectType o -> ctxTlv(3, false, new byte[0]);
        };
    }

    private static byte[] enumeratedContent(ScalarType type) {
        // ENUMERATED shares INTEGER's minimal two's-complement content encoding (X.690 §8.4).
        return BigInteger.valueOf(type.wireValue()).toByteArray();
    }

    // ---- ExprNode dispatch (mirrors CelDecoder.decodeExprNode's arms) ------

    private static byte[] encodeExpr(ExprNode node) throws CelEncodeException {
        return switch (node) {
            case ExprNode.LitBool b -> ctxTlv(0, false, new byte[]{ (byte) (b.value() ? 0xFF : 0x00) });
            case ExprNode.LitInt i -> ctxTlv(1, false, BigInteger.valueOf(i.value()).toByteArray());
            case ExprNode.LitDouble d -> ctxTlv(2, false, doubleContent(d.value()));
            case ExprNode.LitString s -> ctxTlv(3, false, strictUtf8(s.value(), "LIT_STRING"));
            case ExprNode.LitBytes b -> ctxTlv(4, false, b.value().clone());
            case ExprNode.LitNull n -> ctxTlv(5, false, new byte[0]);

            case ExprNode.ListLit l -> ctxTlv(6, true, encodeExprSeq(l.elements()));
            case ExprNode.FieldRef f -> ctxTlv(7, true, encodeStepsSequence(f));
            case ExprNode.Has h -> ctxTlv(8, true, encodeFieldRefUntagged(h.target()));

            case ExprNode.Not u -> ctxTlv(9, true, encodeExpr(u.operand()));
            case ExprNode.Neg u -> ctxTlv(10, true, encodeExpr(u.operand()));

            case ExprNode.And x -> binary(11, x.left(), x.right());
            case ExprNode.Or x -> binary(12, x.left(), x.right());
            case ExprNode.Eq x -> binary(13, x.left(), x.right());
            case ExprNode.Ne x -> binary(14, x.left(), x.right());
            case ExprNode.Lt x -> binary(15, x.left(), x.right());
            case ExprNode.Le x -> binary(16, x.left(), x.right());
            case ExprNode.Gt x -> binary(17, x.left(), x.right());
            case ExprNode.Ge x -> binary(18, x.left(), x.right());
            case ExprNode.Add x -> binary(19, x.left(), x.right());
            case ExprNode.Sub x -> binary(20, x.left(), x.right());
            case ExprNode.Mul x -> binary(21, x.left(), x.right());
            case ExprNode.Div x -> binary(22, x.left(), x.right());
            case ExprNode.Mod x -> binary(23, x.left(), x.right());

            case ExprNode.In in -> encodeIn(in);
            case ExprNode.Cond c -> {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                writeAll(b, encodeExpr(c.condition()));
                writeAll(b, encodeExpr(c.thenBranch()));
                writeAll(b, encodeExpr(c.elseBranch()));
                yield ctxTlv(25, true, b.toByteArray());
            }
            case ExprNode.Call call -> encodeCall(call);
        };
    }

    private static byte[] binary(int tagNumber, ExprNode left, ExprNode right) throws CelEncodeException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeAll(b, encodeExpr(left));
        writeAll(b, encodeExpr(right));
        return ctxTlv(tagNumber, true, b.toByteArray());
    }

    private static byte[] encodeIn(ExprNode.In in) throws CelEncodeException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeAll(b, encodeExpr(in.needle()));
        switch (in.listOperand()) {
            case InListOperand.LiteralList ll ->
                    // literalList [0] IMPLICIT ListLitNode: same content as a listLit node, tag [0].
                    writeAll(b, ctxTlv(0, true, encodeExprSeq(ll.list().elements())));
            case InListOperand.FieldList fl ->
                    // fieldList [1] IMPLICIT FieldRefNode: same content as a fieldRef node, tag [1].
                    writeAll(b, ctxTlv(1, true, encodeStepsSequence(fl.field())));
        }
        return ctxTlv(24, true, b.toByteArray());
    }

    private static byte[] encodeCall(ExprNode.Call call) throws CelEncodeException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeAll(b, DerWriter.writeInteger(call.functionId()));          // UNIVERSAL INTEGER (0x02)
        writeAll(b, DerWriter.writeSequence(encodeExprSeq(call.arguments()))); // arguments SEQUENCE OF ExprNode
        return ctxTlv(26, true, b.toByteArray());
    }

    // ---- FieldRef helpers -------------------------------------------------

    /** The content of a {@code fieldRef[7]} / {@code fieldList[1]} node: the inner {@code steps SEQUENCE OF SelectorStep} (universal 0x30). */
    private static byte[] encodeStepsSequence(ExprNode.FieldRef fr) throws CelEncodeException {
        ByteArrayOutputStream steps = new ByteArrayOutputStream();
        for (SelectorStep s : fr.steps()) writeAll(steps, encodeSelectorStep(s));
        return DerWriter.writeSequence(steps.toByteArray());
    }

    /** A {@code FieldRefNode} carrying its own universal SEQUENCE tag (Has.target is untagged): {@code SEQUENCE { steps SEQUENCE OF }}. */
    private static byte[] encodeFieldRefUntagged(ExprNode.FieldRef fr) throws CelEncodeException {
        return DerWriter.writeSequence(encodeStepsSequence(fr));
    }

    private static byte[] encodeSelectorStep(SelectorStep step) throws CelEncodeException {
        return switch (step) {
            case SelectorStep.Unqual u -> ctxTlv(0, false, strictUtf8(u.name(), "SelectorStep.unqual"));
            case SelectorStep.Qual q -> {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                writeAll(b, DerWriter.writeTlv(Tag.UTF8STRING, strictUtf8(q.className(), "QualifiedSelector.className")));
                writeAll(b, DerWriter.writeTlv(Tag.UTF8STRING, strictUtf8(q.fieldName(), "QualifiedSelector.fieldName")));
                yield ctxTlv(1, true, b.toByteArray());
            }
        };
    }

    private static byte[] encodeExprSeq(java.util.List<ExprNode> nodes) throws CelEncodeException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (ExprNode n : nodes) writeAll(b, encodeExpr(n));
        return b.toByteArray();
    }

    // ---- low-level --------------------------------------------------------

    private static byte[] doubleContent(double value) throws CelEncodeException {
        if (!Double.isFinite(value)) {
            throw new CelEncodeException("non-finite LIT_DOUBLE cannot be canonically encoded (Appendix B §B.5 item 5)");
        }
        long bits = Double.doubleToRawLongBits(value);   // finite: no NaN, exact bit pattern (incl. -0.0)
        byte[] content = new byte[8];
        for (int i = 7; i >= 0; i--) {
            content[i] = (byte) (bits & 0xFF);
            bits >>>= 8;
        }
        return content;
    }

    /**
     * Strict UTF-8 encode: rejects an unpaired surrogate (which
     * {@code String.getBytes(UTF_8)} would silently replace with {@code 0x3F},
     * collapsing distinct strings onto one wire form -- a G1 canonicality
     * break). Mirrors the decoder's strict-UTF-8 decode (Appendix B §B.5 item
     * 10). The shared {@link CelAstValidator} already rejects such strings
     * before encode, so this is defence in depth: reaching it signals a
     * validator-invariant violation.
     */
    private static byte[] strictUtf8(String s, String what) throws CelEncodeException {
        CharsetEncoder enc = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer bb = enc.encode(CharBuffer.wrap(s));
            byte[] out = new byte[bb.remaining()];
            bb.get(out);
            return out;
        } catch (CharacterCodingException e) {
            throw new CelEncodeException(what + ": contains an unpaired surrogate; not encodable as canonical UTF-8 (Appendix B §B.5 item 10)");
        }
    }

    /** Emits a context-class TLV with the given tag number, constructed flag, and content, using minimal tag/length octets. */
    private static byte[] ctxTlv(int number, boolean constructed, byte[] content) {
        return DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, constructed, number), content);
    }

    private static void writeAll(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }
}
