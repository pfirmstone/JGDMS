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

package au.net.zeus.jgdms.cel.wire;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.ast.ExprNode.InListOperand;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.Tag;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the JGDMS-STD-011 Appendix B DER wire encoding into a {@link
 * CelFilterRecord}. This is trusted-computing-base code: every canonical-form
 * MUST-reject of Appendix B §B.5 and every decode-time enforcement of §B.6 is
 * implemented here, fail-closed -- a decode problem is always a hard
 * rejection ({@link CelDecodeException}), never a partial AST.
 * <p>
 * Reuses {@code jgdms-der}'s {@link DerReader}/{@link Tag} as the low-level
 * TLV primitive (tag decode, canonical-length decode, bounds-checked reads):
 * {@link DerReader#readTlvHeader()} already enforces X.690 minimal-length
 * encoding and tag-number minimality (via {@link Tag#decode}) for every TLV
 * this decoder reads, context-tagged or not. What this class adds on top,
 * because {@code DerReader}'s typed readers (e.g. {@code readBoolean()},
 * {@code readInteger()}) only match <em>universal</em> tags and this wire
 * form is built almost entirely from <em>context</em>-tagged {@code
 * IMPLICIT} values: per-context-tag dispatch, the {@code ExprNode} closed
 * node set, the running node-count/depth/selector-step ceilings (§B.6.2),
 * the {@code maxScalarBytes}/name-length ceilings checked before allocation
 * (§B.6.3), strict (non-lenient) UTF-8 validation (§B.5 item 10 --
 * {@code DerReader.readUtf8String()} decodes leniently, which is correct for
 * candidate field data elsewhere but not conformant for this wire form's own
 * literals), non-canonical BOOLEAN/ENUMERATED content rejection (§B.5 items
 * 8-9), non-finite {@code LIT_DOUBLE} rejection (§B.5 item 5), and the
 * function-id/arity/needle-literal checks of §B.6.1/§B.6.5.
 */
public final class CelDecoder {

    private CelDecoder() {}

    /**
     * Decodes a complete {@code CelFilterRecord} from its canonical DER
     * encoding. Rejects (never partially accepts) any non-canonical
     * encoding, unknown tag, arity/shape violation, or ceiling violation.
     *
     * @param wire the complete DER-encoded {@code CelFilterRecord}
     * @return the decoded record (formatVersion, context, AST)
     * @throws CelDecodeException on any decode failure
     */
    public static CelFilterRecord decode(byte[] wire) throws CelDecodeException {
        if (wire == null) throw new NullPointerException("wire");
        try {
            DerReader root = new DerReader(wire);
            DerReader content = root.readSequence();
            if (root.hasMore()) {
                reject("trailing bytes after CelFilterRecord");
            }

            long formatVersion = decodeIntegerContent(content, Tag.INTEGER, "CelFilterRecord.formatVersion");
            if (formatVersion != CelFilterRecord.FORMAT_VERSION) {
                reject("unsupported CelFilterRecord.formatVersion " + formatVersion
                        + " (only " + CelFilterRecord.FORMAT_VERSION + " is defined)");
            }

            EvaluationContext ctx = decodeEvaluationContext(content);

            // The maxExprNodes/maxExprDepth ceilings apply to the AST (STD-011
            // §10.3: "AST node count"), not to the envelope framing decoded
            // above -- the session starts fresh for `expression`.
            Session session = new Session();
            ExprNode expression = decodeExprNode(content, session);

            if (content.hasMore()) {
                reject("trailing bytes after CelFilterRecord.expression");
            }
            return new CelFilterRecord((int) formatVersion, ctx, expression);
        } catch (DerException e) {
            throw new CelDecodeException(e.getMessage(), e);
        }
    }

    // ======================================================================
    // Decode session: the running ceilings threaded through recursive decode
    // ======================================================================

    /** Mutable, single-decode-call state: never shared across concurrent decode() invocations. */
    private static final class Session {
        int nodeCount = 0;
        int depth = 0;
    }

    private static void consumeNode(Session s) throws CelDecodeException {
        s.nodeCount++;
        if (s.nodeCount > CelCeilings.MAX_EXPR_NODES) {
            reject("maxExprNodes (" + CelCeilings.MAX_EXPR_NODES + ") exceeded at node " + s.nodeCount);
        }
    }

    private static void enterDepth(Session s) throws CelDecodeException {
        s.depth++;
        if (s.depth > CelCeilings.MAX_EXPR_DEPTH) {
            reject("maxExprDepth (" + CelCeilings.MAX_EXPR_DEPTH + ") exceeded at depth " + s.depth);
        }
    }

    private static void exitDepth(Session s) {
        s.depth--;
    }

    // ======================================================================
    // ExprNode dispatch (the 27-arm CHOICE, STD-011 §11.1 / Appendix B §B.4.1)
    // ======================================================================

    private static Tag ctx(int number, boolean constructed) {
        return new Tag(Tag.CLASS_CONTEXT, constructed, number);
    }

    /** {tagNumber -> expected constructed flag} for the 27 defined ExprNode arms, per §B.4.1's leading-octet table. */
    private static boolean expectedConstructed(int tagNumber) {
        // literals [0]-[5]: primitive; structural/unary/binary/in/cond/call [6]-[26]: constructed
        return tagNumber >= 6 && tagNumber <= 26;
    }

    private static ExprNode decodeExprNode(DerReader r, Session s) throws DerException, CelDecodeException {
        enterDepth(s);
        try {
            if (!r.hasMore()) {
                reject("unexpected end of input while expecting an ExprNode");
            }
            Tag peeked = r.peekTag();
            if (peeked.tagClass() != Tag.CLASS_CONTEXT
                    || peeked.tagNumber() < 0 || peeked.tagNumber() > 26
                    || peeked.isConstructed() != expectedConstructed(peeked.tagNumber())) {
                reject("unknown/reserved ExprNode tag: " + peeked);
            }
            int tagNumber = peeked.tagNumber();
            DerReader.TlvHeader hdr = r.readTlvHeader();
            consumeNode(s);
            int len = hdr.contentLength();

            switch (tagNumber) {
                case 0: return decodeLitBool(r, len);
                case 1: return new ExprNode.LitInt(decodeInt64Content(r, len));
                case 2: return decodeLitDouble(r, len);
                case 3: return new ExprNode.LitString(decodeUtf8Raw(r, len, 0, CelCeilings.MAX_SCALAR_BYTES, "LIT_STRING"));
                case 4: return new ExprNode.LitBytes(decodeBytesRaw(r, len, 0, CelCeilings.MAX_SCALAR_BYTES, "LIT_BYTES"));
                case 5:
                    if (len != 0) reject("LIT_NULL: NULL content must be zero-length");
                    return new ExprNode.LitNull();
                case 6: return decodeListLitBody(childReader(r, len), s);
                case 7: return decodeFieldRefFields(childReader(r, len), s);
                case 8: return decodeHasBody(childReader(r, len), s);
                case 9: return new ExprNode.Not(decodeUnaryOperand(childReader(r, len), s));
                case 10: return new ExprNode.Neg(decodeUnaryOperand(childReader(r, len), s));
                case 11: case 12: case 13: case 14: case 15: case 16: case 17:
                case 18: case 19: case 20: case 21: case 22: case 23:
                    return decodeBinary(tagNumber, childReader(r, len), s);
                case 24: return decodeInBody(childReader(r, len), s);
                case 25: return decodeCondBody(childReader(r, len), s);
                case 26: return decodeCallBody(childReader(r, len), s);
                default:
                    // Unreachable: tagNumber was validated above against [0,26].
                    throw new IllegalStateException("unreachable tag " + tagNumber);
            }
        } finally {
            exitDepth(s);
        }
    }

    // ---- literals ---------------------------------------------------------

    private static ExprNode.LitBool decodeLitBool(DerReader r, int len) throws DerException, CelDecodeException {
        if (len != 1) reject("LIT_BOOL: BOOLEAN content length must be 1, got " + len);
        byte[] content = r.readRawContent(1);
        int b = content[0] & 0xFF;
        if (b == 0x00) return new ExprNode.LitBool(false);
        if (b == 0xFF) return new ExprNode.LitBool(true);
        reject("LIT_BOOL: non-canonical BOOLEAN content 0x" + Integer.toHexString(b)
                + " (DER requires exactly 0x00 or 0xFF)");
        throw new IllegalStateException("unreachable");
    }

    private static long decodeInt64Content(DerReader r, int len) throws DerException, CelDecodeException {
        BigInteger v = readMinimalTwosComplement(r, len, "LIT_INT");
        if (v.bitLength() > 63) {
            reject("LIT_INT value out of int64 range: " + v);
        }
        return v.longValueExact();
    }

    private static ExprNode.LitDouble decodeLitDouble(DerReader r, int len) throws DerException, CelDecodeException {
        if (len != 8) reject("LIT_DOUBLE: OCTET STRING content length must be 8, got " + len);
        byte[] content = r.readRawContent(8);
        long bits = 0L;
        for (int i = 0; i < 8; i++) {
            bits = (bits << 8) | (content[i] & 0xFFL);
        }
        double d = Double.longBitsToDouble(bits);
        if (!Double.isFinite(d)) {
            reject("LIT_DOUBLE: non-finite bit pattern 0x" + Long.toHexString(bits)
                    + " is not a well-formed literal (Appendix B §B.5 item 5)");
        }
        return new ExprNode.LitDouble(d);
    }

    // ---- list literal -------------------------------------------------------

    /**
     * Decodes a {@code ListLitNode}'s content (the sequence of element
     * {@code ExprNode}s). The wrapper LIST_LIT node itself must already have
     * been counted by the caller -- either implicitly, via {@code
     * decodeExprNode}'s generic {@code consumeNode()} for the {@code
     * listLit[6]} ExprNode arm, or explicitly, via {@link
     * #decodeLiteralListOperand} for the {@code InListOperand.literalList}
     * embedding. Per Appendix B §B.6.2 item 5, depth/node-count accounting
     * for <em>elements</em> happens once per element (inside {@code
     * decodeExprNode} itself, called below), never once for the list as a
     * whole -- this method contributes no depth increment of its own.
     */
    private static ExprNode.ListLit decodeListLitBody(DerReader content, Session s) throws DerException, CelDecodeException {
        List<ExprNode> elements = new ArrayList<>();
        int count = 0;
        while (content.hasMore()) {
            count++;
            if (count > CelCeilings.MAX_COLLECTION) {
                reject("ListLitNode exceeds maxCollection (" + CelCeilings.MAX_COLLECTION + ")");
            }
            elements.add(decodeExprNode(content, s));
        }
        if (elements.isEmpty()) {
            reject("ListLitNode must be non-empty (STD-011 §5.3.2)");
        }
        return new ExprNode.ListLit(elements);
    }

    // ---- field references --------------------------------------------------

    /** Decodes the FIELD_REF ExprNode arm's content (the `steps` field) -- the wrapper node was already counted by the caller (decodeExprNode's generic consumeNode()). */
    private static ExprNode.FieldRef decodeFieldRefFields(DerReader content, Session s) throws DerException, CelDecodeException {
        return decodeStepsSequence(content, s);
    }

    private static ExprNode.FieldRef decodeStepsSequence(DerReader fieldRefContent, Session s) throws DerException, CelDecodeException {
        DerReader stepsSeq = fieldRefContent.readSequence();
        List<SelectorStep> steps = new ArrayList<>();
        int local = 0;
        while (stepsSeq.hasMore()) {
            local++;
            if (local > CelCeilings.MAX_SELECTOR_STEPS) {
                reject("maxSelectorSteps (" + CelCeilings.MAX_SELECTOR_STEPS + ") exceeded at step " + local);
            }
            consumeNode(s); // each selector step is its own AST node (Appendix B §B.6.2)
            steps.add(decodeSelectorStep(stepsSeq));
        }
        if (steps.isEmpty()) {
            reject("FieldRefNode.steps must be non-empty");
        }
        requireExhausted(fieldRefContent, "FieldRefNode");
        return new ExprNode.FieldRef(steps);
    }

    private static SelectorStep decodeSelectorStep(DerReader r) throws DerException, CelDecodeException {
        if (!r.hasMore()) reject("unexpected end of input while expecting a SelectorStep");
        Tag peeked = r.peekTag();
        if (peeked.equals(ctx(0, false))) {
            String name = readUtf8Content(r, ctx(0, false), 1, CelCeilings.MAX_UNQUAL_NAME_BYTES, "SelectorStep.unqual");
            return new SelectorStep.Unqual(name);
        } else if (peeked.equals(ctx(1, true))) {
            DerReader content = enterConstructed(r, ctx(1, true), "SelectorStep.qual");
            String className = readUtf8Content(content, Tag.UTF8STRING, 1, CelCeilings.MAX_CLASS_NAME_BYTES, "QualifiedSelector.className");
            String fieldName = readUtf8Content(content, Tag.UTF8STRING, 1, CelCeilings.MAX_UNQUAL_NAME_BYTES, "QualifiedSelector.fieldName");
            requireExhausted(content, "QualifiedSelector");
            return new SelectorStep.Qual(className, fieldName);
        } else {
            reject("unknown SelectorStep tag: " + peeked);
            throw new IllegalStateException("unreachable");
        }
    }

    /** Decodes a FieldRefNode reached via an <em>untagged</em> component (HasNode.target): its own tag is FieldRefNode's default universal SEQUENCE tag. */
    private static ExprNode.FieldRef decodeUntaggedFieldRefNode(DerReader parent, Session s) throws DerException, CelDecodeException {
        consumeNode(s); // the FIELD_REF node itself
        DerReader content = parent.readSequence();
        return decodeStepsSequence(content, s);
    }

    /** Decodes a FieldRefNode reached via InListOperand's {@code fieldList [1] IMPLICIT FieldRefNode} arm: the context tag replaces FieldRefNode's own tag directly. */
    private static ExprNode.FieldRef decodeFieldListOperand(DerReader r, Session s) throws DerException, CelDecodeException {
        consumeNode(s); // the FIELD_REF node itself
        DerReader content = enterConstructed(r, ctx(1, true), "InListOperand.fieldList");
        return decodeStepsSequence(content, s);
    }

    /** Decodes a ListLitNode reached via InListOperand's {@code literalList [0] IMPLICIT ListLitNode} arm: the context tag replaces ListLitNode's own tag directly. */
    private static ExprNode.ListLit decodeLiteralListOperand(DerReader r, Session s) throws DerException, CelDecodeException {
        consumeNode(s); // the LIST_LIT node itself
        DerReader content = enterConstructed(r, ctx(0, true), "InListOperand.literalList");
        return decodeListLitBody(content, s);
    }

    // ---- has ----------------------------------------------------------------

    private static ExprNode.Has decodeHasBody(DerReader content, Session s) throws DerException, CelDecodeException {
        ExprNode.FieldRef target = decodeUntaggedFieldRefNode(content, s);
        requireExhausted(content, "HasNode");
        return new ExprNode.Has(target);
    }

    // ---- unary / binary -------------------------------------------------------

    private static ExprNode decodeUnaryOperand(DerReader content, Session s) throws DerException, CelDecodeException {
        ExprNode operand = decodeExprNode(content, s);
        requireExhausted(content, "UnaryNode");
        return operand;
    }

    private static ExprNode decodeBinary(int tagNumber, DerReader content, Session s) throws DerException, CelDecodeException {
        ExprNode left = decodeExprNode(content, s);
        ExprNode right = decodeExprNode(content, s);
        requireExhausted(content, "BinaryNode");
        return switch (tagNumber) {
            case 11 -> new ExprNode.And(left, right);
            case 12 -> new ExprNode.Or(left, right);
            case 13 -> new ExprNode.Eq(left, right);
            case 14 -> new ExprNode.Ne(left, right);
            case 15 -> new ExprNode.Lt(left, right);
            case 16 -> new ExprNode.Le(left, right);
            case 17 -> new ExprNode.Gt(left, right);
            case 18 -> new ExprNode.Ge(left, right);
            case 19 -> new ExprNode.Add(left, right);
            case 20 -> new ExprNode.Sub(left, right);
            case 21 -> new ExprNode.Mul(left, right);
            case 22 -> new ExprNode.Div(left, right);
            case 23 -> new ExprNode.Mod(left, right);
            default -> throw new IllegalStateException("unreachable binary tag " + tagNumber);
        };
    }

    // ---- in -------------------------------------------------------------------

    private static ExprNode.In decodeInBody(DerReader content, Session s) throws DerException, CelDecodeException {
        ExprNode needle = decodeExprNode(content, s);
        if (!content.hasMore()) reject("InNode.listOperand is missing");
        Tag t = content.peekTag();
        InListOperand operand;
        if (t.equals(ctx(0, true))) {
            operand = new InListOperand.LiteralList(decodeLiteralListOperand(content, s));
        } else if (t.equals(ctx(1, true))) {
            operand = new InListOperand.FieldList(decodeFieldListOperand(content, s));
        } else {
            reject("unknown InListOperand tag: " + t);
            throw new IllegalStateException("unreachable");
        }
        requireExhausted(content, "InNode");
        return new ExprNode.In(needle, operand);
    }

    // ---- cond -------------------------------------------------------------------

    private static ExprNode.Cond decodeCondBody(DerReader content, Session s) throws DerException, CelDecodeException {
        ExprNode c = decodeExprNode(content, s);
        ExprNode t = decodeExprNode(content, s);
        ExprNode e = decodeExprNode(content, s);
        requireExhausted(content, "CondNode");
        return new ExprNode.Cond(c, t, e);
    }

    // ---- call -------------------------------------------------------------------

    private static ExprNode.Call decodeCallBody(DerReader content, Session s) throws DerException, CelDecodeException {
        long functionId = decodeIntegerContent(content, Tag.INTEGER, "CallNode.functionId");
        if (functionId < 1 || functionId > 24) {
            reject("CallNode.functionId " + functionId + " outside the pinned registry range [1,24]");
        }
        int fid = (int) functionId;

        DerReader argsSeq = content.readSequence();
        List<ExprNode> args = new ArrayList<>();
        int count = 0;
        while (argsSeq.hasMore()) {
            count++;
            if (count > 2) {
                reject("CallNode.arguments exceeds the grammar's bound of 2");
            }
            args.add(decodeExprNode(argsSeq, s));
        }
        if (args.isEmpty()) {
            reject("CallNode.arguments must be non-empty");
        }
        requireExhausted(content, "CallNode");

        FunctionRegistry.Entry entry = FunctionRegistry.byId(fid);
        if (entry == null) {
            reject("unregistered CallNode.functionId " + fid);
        }
        if (args.size() != entry.arity()) {
            reject("arity mismatch for function id " + fid + " (" + entry.name()
                    + "): expected " + entry.arity() + " argument(s), got " + args.size());
        }
        if (fid == FunctionRegistry.CONTAINS_ID && !(args.get(1) instanceof ExprNode.LitString)) {
            reject("contains(): needle argument (position 2) must be a string literal (STD-011 §5.3.1 item 4)");
        }
        return new ExprNode.Call(fid, args);
    }

    // ======================================================================
    // Envelope: EvaluationContext / DeclaredResultType
    // ======================================================================

    private static EvaluationContext decodeEvaluationContext(DerReader r) throws DerException, CelDecodeException {
        if (!r.hasMore()) reject("unexpected end of input while expecting EvaluationContext");
        Tag t = r.peekTag();
        if (t.equals(ctx(0, false))) {
            DerReader.TlvHeader hdr = r.readTlvHeader();
            if (hdr.contentLength() != 0) reject("EvaluationContext.predicate: NULL content must be zero-length");
            return new EvaluationContext.Predicate();
        } else if (t.equals(ctx(1, true))) {
            DerReader wrapped = enterConstructed(r, ctx(1, true), "EvaluationContext.transform");
            DeclaredResultType drt = decodeDeclaredResultType(wrapped);
            requireExhausted(wrapped, "EvaluationContext.transform (EXPLICIT wrapper)");
            return new EvaluationContext.Transform(drt);
        } else {
            reject("unknown EvaluationContext tag: " + t);
            throw new IllegalStateException("unreachable");
        }
    }

    private static DeclaredResultType decodeDeclaredResultType(DerReader r) throws DerException, CelDecodeException {
        if (!r.hasMore()) reject("unexpected end of input while expecting DeclaredResultType");
        Tag t = r.peekTag();
        if (t.equals(ctx(0, false))) {
            return new DeclaredResultType.Scalar(decodeScalarTypeContent(r, ctx(0, false), "DeclaredResultType.scalar"));
        } else if (t.equals(ctx(1, false))) {
            DerReader.TlvHeader hdr = r.readTlvHeader();
            if (hdr.contentLength() != 0) reject("DeclaredResultType.nullType: NULL content must be zero-length");
            return new DeclaredResultType.NullType();
        } else if (t.equals(ctx(2, false))) {
            return new DeclaredResultType.ListType(decodeScalarTypeContent(r, ctx(2, false), "DeclaredResultType.listType"));
        } else if (t.equals(ctx(3, false))) {
            DerReader.TlvHeader hdr = r.readTlvHeader();
            if (hdr.contentLength() != 0) reject("DeclaredResultType.objectType: NULL content must be zero-length");
            return new DeclaredResultType.ObjectType();
        } else {
            reject("unknown DeclaredResultType tag: " + t);
            throw new IllegalStateException("unreachable");
        }
    }

    private static ScalarType decodeScalarTypeContent(DerReader r, Tag expectedTag, String what) throws DerException, CelDecodeException {
        BigInteger v = readMinimalTwosComplementTagged(r, expectedTag, what);
        int iv;
        try {
            iv = v.intValueExact();
        } catch (ArithmeticException e) {
            reject(what + ": ENUMERATED value out of range: " + v);
            throw new IllegalStateException("unreachable");
        }
        try {
            return ScalarType.fromWireValue(iv);
        } catch (IllegalArgumentException e) {
            reject(what + ": unknown ScalarType enumerated value " + iv);
            throw new IllegalStateException("unreachable");
        }
    }

    // ======================================================================
    // Shared low-level helpers
    // ======================================================================

    private static DerReader childReader(DerReader r, int len) throws DerException {
        return new DerReader(r.readRawContent(len));
    }

    /** Reads a constructed TLV expected to carry {@code expectedTag}, returning a reader bounded to its content. */
    private static DerReader enterConstructed(DerReader r, Tag expectedTag, String what) throws DerException, CelDecodeException {
        DerReader.TlvHeader hdr = r.readTlvHeader();
        checkTag(expectedTag, hdr.tag(), what);
        return childReader(r, hdr.contentLength());
    }

    private static void checkTag(Tag expected, Tag actual, String what) throws CelDecodeException {
        if (!expected.equals(actual)) {
            reject(what + ": expected tag " + expected + ", got " + actual);
        }
    }

    private static void requireExhausted(DerReader r, String what) throws CelDecodeException {
        if (r.hasMore()) {
            reject(what + ": trailing bytes beyond the declared structure (non-canonical encoding)");
        }
    }

    private static long decodeIntegerContent(DerReader r, Tag expectedTag, String what) throws DerException, CelDecodeException {
        BigInteger v = readMinimalTwosComplementTagged(r, expectedTag, what);
        if (v.bitLength() > 62) {
            // formatVersion/functionId are small, bounded fields; anything
            // needing this many bits is certainly out of their valid ranges,
            // and this guards longValueExact() below against pathological input.
            reject(what + ": integer value implausibly large: " + v);
        }
        return v.longValueExact();
    }

    private static BigInteger readMinimalTwosComplementTagged(DerReader r, Tag expectedTag, String what) throws DerException, CelDecodeException {
        DerReader.TlvHeader hdr = r.readTlvHeader();
        checkTag(expectedTag, hdr.tag(), what);
        return readMinimalTwosComplement(r, hdr.contentLength(), what);
    }

    /** Reads {@code len} content bytes already positioned at the TLV content (header consumed by the caller) and validates DER INTEGER/ENUMERATED minimal two's-complement form. */
    private static BigInteger readMinimalTwosComplement(DerReader r, int len, String what) throws DerException, CelDecodeException {
        if (len == 0) {
            reject(what + ": zero-length INTEGER/ENUMERATED content");
        }
        byte[] content = r.readRawContent(len);
        if (len >= 2) {
            int b0 = content[0] & 0xFF;
            int b1 = content[1] & 0xFF;
            if (b0 == 0x00 && (b1 & 0x80) == 0) {
                reject(what + ": non-canonical INTEGER (redundant leading 0x00 byte)");
            }
            if (b0 == 0xFF && (b1 & 0x80) != 0) {
                reject(what + ": non-canonical INTEGER (redundant leading 0xFF byte)");
            }
        }
        return new BigInteger(content);
    }

    private static String readUtf8Content(DerReader r, Tag expectedTag, int minLen, int maxLen, String what) throws DerException, CelDecodeException {
        DerReader.TlvHeader hdr = r.readTlvHeader();
        checkTag(expectedTag, hdr.tag(), what);
        return decodeUtf8Raw(r, hdr.contentLength(), minLen, maxLen, what);
    }

    private static String decodeUtf8Raw(DerReader r, int len, int minLen, int maxLen, String what) throws DerException, CelDecodeException {
        if (len < minLen || len > maxLen) {
            reject(what + ": length " + len + " outside [" + minLen + ", " + maxLen + "]");
        }
        byte[] raw = r.readRawContent(len);
        return strictUtf8Decode(raw, what);
    }

    private static byte[] decodeBytesRaw(DerReader r, int len, int minLen, int maxLen, String what) throws DerException, CelDecodeException {
        if (len < minLen || len > maxLen) {
            reject(what + ": length " + len + " outside [" + minLen + ", " + maxLen + "]");
        }
        return r.readRawContent(len);
    }

    /**
     * Strict UTF-8 decode: rejects ill-formed sequences, overlong encodings,
     * and directly-encoded surrogate code points (Appendix B §B.5 item 10).
     * {@code DerReader.readUtf8String()} decodes leniently (substitution on
     * malformed input), which is correct nowhere in this wire form's own
     * canonical-form obligations, so this decoder never calls it for
     * expression-literal or selector-name content.
     */
    private static String strictUtf8Decode(byte[] raw, String what) throws CelDecodeException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException e) {
            reject(what + ": ill-formed or non-canonical UTF-8 content");
            throw new IllegalStateException("unreachable");
        }
    }

    private static void reject(String message) throws CelDecodeException {
        throw new CelDecodeException(message);
    }
}
