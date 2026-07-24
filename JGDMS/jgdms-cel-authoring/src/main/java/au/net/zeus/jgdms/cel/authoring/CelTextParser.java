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
import au.net.zeus.jgdms.cel.wire.FunctionRegistry;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The T-CEL-A recursive-descent text parser: STD-011 §5's textual grammar
 * ({@link String}) to a jgdms-cel {@link ExprNode} AST.
 * <p>
 * Follows §5.3's grammar and precedence exactly (postfix &gt; unary {@code !}/{@code -}
 * &gt; {@code * / %} &gt; {@code + -} &gt; relations/{@code in} &gt; {@code &&} &gt;
 * {@code ||} &gt; {@code ?:}; {@code &&}/{@code ||} left-associative, {@code ?:}
 * right-associative, relations non-chaining). Enforces §5.2 lexical rules, the
 * §5.3.1 well-formedness constraints (call allowlist, {@code has}/{@code field}
 * argument shapes, {@code contains} literal needle, {@code in} operand shape),
 * §5.3.2 list homogeneity/non-emptiness, the §5.4 exclusions (rejected wholesale
 * with clear errors), and the {@code CelCeilings} bounds (via
 * {@link CelAstValidator}). Text that does not parse is rejected wholesale --
 * the parser never returns a partial AST (§5.1).
 * <p>
 * STD-011 §5's grammar defines only {@code Expr}; there is no textual syntax for
 * the {@code EvaluationContext}/{@code DeclaredResultType} envelope. This parser
 * therefore returns an {@link ExprNode}; assemble a full {@code CelFilterRecord}
 * with {@link CelRecordBuilder} by supplying the context programmatically.
 * <p>
 * <b>Overload note (schema-free limitation, reported as an OPEN QUESTION).</b>
 * The type-dispatched functions {@code size}/{@code abs}/{@code min}/{@code max}
 * map one text name to several Appendix B §B.8.3 wire ids by operand type. This
 * standalone parser resolves the overload by {@link CelStaticType static
 * inference}; over a bare field-reference argument the type is not statically
 * knowable without a schema, and the parser rejects it rather than guess a wire
 * id. Such expressions must be built through the AST directly (or await a
 * schema-aware parser).
 */
public final class CelTextParser {

    private CelTextParser() {}

    /** Global platform functions callable as {@code name(...)} (§7.2), plus the special forms {@code has}/{@code field}/{@code size}. */
    private static final Set<String> GLOBAL_FUNCTIONS = Set.of(
            "int", "double", "abs", "min", "max", "sqrt", "radians", "degrees",
            "sin", "cos", "tan", "asin", "acos", "atan", "atan2");

    /** Words usable only in their grammar positions, never as a bare field IDENT (§5.2). {@code true}/{@code false}/{@code null}/{@code in} are separate tokens. */
    private static final Set<String> RESERVED = Set.of(
            "has", "field",
            "as", "break", "const", "continue", "else", "for", "function", "if",
            "import", "let", "loop", "package", "namespace", "return", "var", "void", "while");

    private static final BigInteger TWO_63 = BigInteger.ONE.shiftLeft(63);              // 9223372036854775808
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);       // 2^63 - 1

    /**
     * Parses a DETERMINISTIC CEL textual expression into its AST, enforcing
     * every §5 rule and the {@code CelCeilings} bounds.
     *
     * @param text the expression source (§5's {@code Expr})
     * @return the AST; {@code null} is never returned and no partial AST escapes
     * @throws CelParseException on any lexical, syntactic, well-formedness,
     *         exclusion, or ceiling violation
     */
    public static ExprNode parse(String text) throws CelParseException {
        if (text == null) throw new NullPointerException("text");
        List<Token> tokens = new Lexer(text).lex();
        Parser p = new Parser(tokens);
        ExprNode node = p.parseExpr();
        p.expectEof();
        String authoring = CelAstValidator.findAuthoringViolation(node);
        if (authoring != null) {
            throw new CelParseException("well-formedness violation: " + authoring);
        }
        String wire = CelAstValidator.findWireViolation(node);
        if (wire != null) {
            throw new CelParseException("ceiling/wire violation: " + wire);
        }
        return node;
    }

    // =====================================================================
    // Tokens
    // =====================================================================

    private enum T {
        INT_LIT, DOUBLE_LIT, STRING_LIT, BYTES_LIT, TRUE, FALSE, NULL, IDENT, IN,
        LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, DOT,
        QUESTION, COLON, OROR, ANDAND, NOT,
        LT, LE, GT, GE, EQ, NE,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        EOF
    }

    private record Token(T type, String text, Object value, int pos) {}

    // =====================================================================
    // Lexer
    // =====================================================================

    private static final class Lexer {
        private final String s;
        private int i = 0;

        Lexer(String s) { this.s = s; }

        List<Token> lex() throws CelParseException {
            List<Token> out = new ArrayList<>();
            while (true) {
                skipWhitespaceAndComments();
                if (i >= s.length()) { out.add(new Token(T.EOF, "", null, i)); return out; }
                out.add(nextToken());
            }
        }

        private void skipWhitespaceAndComments() throws CelParseException {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') { i++; continue; }
                if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                    i += 2;
                    while (i < s.length() && s.charAt(i) != '\r' && s.charAt(i) != '\n') i++;
                    continue;
                }
                break;
            }
        }

        private Token nextToken() throws CelParseException {
            int start = i;
            char c = s.charAt(i);

            // bytes literal b"..."
            if (c == 'b' && i + 1 < s.length() && s.charAt(i + 1) == '"') {
                return bytesLiteral();
            }
            if (isIdentStart(c)) return identifier();
            if (c >= '0' && c <= '9') return number();
            if (c == '"' || c == '\'') return stringLiteral(c);

            i++; // consume the punctuation/operator lead char
            switch (c) {
                case '(': return new Token(T.LPAREN, "(", null, start);
                case ')': return new Token(T.RPAREN, ")", null, start);
                case '[': return new Token(T.LBRACKET, "[", null, start);
                case ']': return new Token(T.RBRACKET, "]", null, start);
                case ',': return new Token(T.COMMA, ",", null, start);
                case '.': return new Token(T.DOT, ".", null, start);
                case '?': return new Token(T.QUESTION, "?", null, start);
                case ':': return new Token(T.COLON, ":", null, start);
                case '+': return new Token(T.PLUS, "+", null, start);
                case '-': return new Token(T.MINUS, "-", null, start);
                case '*': return new Token(T.STAR, "*", null, start);
                case '/': return new Token(T.SLASH, "/", null, start);
                case '%': return new Token(T.PERCENT, "%", null, start);
                case '<': return two('=') ? new Token(T.LE, "<=", null, start) : new Token(T.LT, "<", null, start);
                case '>': return two('=') ? new Token(T.GE, ">=", null, start) : new Token(T.GT, ">", null, start);
                case '=':
                    if (two('=')) return new Token(T.EQ, "==", null, start);
                    throw err(start, "stray '='; assignment is not part of the language (§5.4)");
                case '!':
                    if (two('=')) return new Token(T.NE, "!=", null, start);
                    return new Token(T.NOT, "!", null, start);
                case '|':
                    if (two('|')) return new Token(T.OROR, "||", null, start);
                    throw err(start, "single '|' is not an operator; did you mean '||'?");
                case '&':
                    if (two('&')) return new Token(T.ANDAND, "&&", null, start);
                    throw err(start, "single '&' is not an operator; did you mean '&&'?");
                default:
                    throw err(start, "unexpected character '" + c + "'");
            }
        }

        /** If the current char equals {@code expect}, consume it and return true. */
        private boolean two(char expect) {
            if (i < s.length() && s.charAt(i) == expect) { i++; return true; }
            return false;
        }

        private Token identifier() {
            int start = i;
            i++;
            while (i < s.length() && isIdentPart(s.charAt(i))) i++;
            String name = s.substring(start, i);
            return switch (name) {
                case "true" -> new Token(T.TRUE, name, Boolean.TRUE, start);
                case "false" -> new Token(T.FALSE, name, Boolean.FALSE, start);
                case "null" -> new Token(T.NULL, name, null, start);
                case "in" -> new Token(T.IN, name, null, start);
                default -> new Token(T.IDENT, name, null, start);
            };
        }

        private Token number() throws CelParseException {
            int start = i;
            // hex
            if (s.charAt(i) == '0' && i + 1 < s.length() && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
                i += 2;
                int hs = i;
                while (i < s.length() && isHex(s.charAt(i))) i++;
                if (i == hs) throw err(start, "malformed hex literal '0x' with no digits");
                BigInteger v = new BigInteger(s.substring(hs, i), 16);
                requireIntRange(v, start);
                return new Token(T.INT_LIT, s.substring(start, i), v, start);
            }
            while (i < s.length() && isDigit(s.charAt(i))) i++;
            boolean isDouble = false;
            // fractional part: DIGIT+ "." DIGIT+
            if (i < s.length() && s.charAt(i) == '.' && i + 1 < s.length() && isDigit(s.charAt(i + 1))) {
                isDouble = true;
                i++; // '.'
                while (i < s.length() && isDigit(s.charAt(i))) i++;
            }
            // exponent
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                int save = i;
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                int es = i;
                while (i < s.length() && isDigit(s.charAt(i))) i++;
                if (i == es) { i = save; } // 'e' not part of an exponent; leave for identifier? no -> malformed
                else isDouble = true;
            }
            String txt = s.substring(start, i);
            if (isDouble) {
                double d = Double.parseDouble(txt);   // correctly rounded, ties-to-even
                return new Token(T.DOUBLE_LIT, txt, d, start);
            }
            BigInteger v = new BigInteger(txt);
            requireIntRange(v, start);
            return new Token(T.INT_LIT, txt, v, start);
        }

        /** Lexical range check: an INT_LIT value must lie in [0, 2^63] (the 2^63 case's legality is decided at parse, §5.2). */
        private void requireIntRange(BigInteger v, int start) throws CelParseException {
            if (v.signum() < 0 || v.compareTo(TWO_63) > 0) {
                throw err(start, "integer literal out of range [0, 2^63]: " + v);
            }
        }

        private Token stringLiteral(char quote) throws CelParseException {
            int start = i;
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw err(start, "unterminated string literal");
                char c = s.charAt(i);
                if (c == quote) { i++; break; }
                if (c == '\\') {
                    i++;
                    if (i >= s.length()) throw err(start, "unterminated escape in string literal");
                    char e = s.charAt(i);
                    switch (e) {
                        case '\\' -> { sb.append('\\'); i++; }
                        case '"' -> { sb.append('"'); i++; }
                        case '\'' -> { sb.append('\''); i++; }
                        case 'n' -> { sb.append('\n'); i++; }
                        case 'r' -> { sb.append('\r'); i++; }
                        case 't' -> { sb.append('\t'); i++; }
                        case 'u' -> { i++; sb.appendCodePoint(readUnicodeEscape(4, start)); }
                        case 'U' -> { i++; sb.appendCodePoint(readUnicodeEscape(8, start)); }
                        default -> throw err(start, "invalid string escape '\\" + e + "'");
                    }
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return new Token(T.STRING_LIT, s.substring(start, i), sb.toString(), start);
        }

        private int readUnicodeEscape(int nHex, int start) throws CelParseException {
            if (i + nHex > s.length()) throw err(start, "truncated \\" + (nHex == 4 ? "u" : "U") + " escape");
            String hex = s.substring(i, i + nHex);
            for (int k = 0; k < nHex; k++) {
                if (!isHex(hex.charAt(k))) throw err(start, "non-hex digit in \\" + (nHex == 4 ? "u" : "U") + " escape");
            }
            i += nHex;
            int cp = Integer.parseInt(hex, 16);
            if (cp > 0x10FFFF) throw err(start, "\\U escape beyond U+10FFFF: " + hex);
            if (cp >= 0xD800 && cp <= 0xDFFF) throw err(start, "escape denotes a surrogate code point U+" + hex + " (§5.2)");
            return cp;
        }

        private Token bytesLiteral() throws CelParseException {
            int start = i;
            i += 2; // 'b' and opening '"'
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (true) {
                if (i >= s.length()) throw err(start, "unterminated bytes literal");
                char c = s.charAt(i);
                if (c == '"') { i++; break; }
                if (c == '\\') {
                    i++;
                    if (i >= s.length()) throw err(start, "unterminated escape in bytes literal");
                    char e = s.charAt(i);
                    switch (e) {
                        case '\\' -> { out.write('\\'); i++; }
                        case '"' -> { out.write('"'); i++; }
                        case 'x' -> {
                            i++;
                            if (i + 2 > s.length() || !isHex(s.charAt(i)) || !isHex(s.charAt(i + 1))) {
                                throw err(start, "\\x escape requires exactly two hex digits");
                            }
                            out.write(Integer.parseInt(s.substring(i, i + 2), 16));
                            i += 2;
                        }
                        default -> throw err(start, "invalid bytes escape '\\" + e + "' (only \\\\, \\\", \\xHH)");
                    }
                } else {
                    if (c < 0x20 || c > 0x7E) throw err(start, "non-printable-ASCII byte in bytes literal (use \\xHH)");
                    out.write(c);
                    i++;
                }
            }
            return new Token(T.BYTES_LIT, s.substring(start, i), out.toByteArray(), start);
        }

        private CelParseException err(int pos, String msg) {
            return new CelParseException("lexical error at offset " + pos + ": " + msg);
        }

        private static boolean isIdentStart(char c) { return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'); }
        private static boolean isIdentPart(char c) { return isIdentStart(c) || isDigit(c); }
        private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
        private static boolean isHex(char c) { return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
    }

    // =====================================================================
    // Parser
    // =====================================================================

    private static final class Parser {
        private final List<Token> toks;
        private int p = 0;

        Parser(List<Token> toks) { this.toks = toks; }

        private Token peek() { return toks.get(p); }
        private Token peek2() { return p + 1 < toks.size() ? toks.get(p + 1) : toks.get(toks.size() - 1); }
        private Token advance() { return toks.get(p++); }
        private boolean check(T t) { return peek().type() == t; }
        private boolean match(T t) { if (check(t)) { p++; return true; } return false; }

        private Token expect(T t, String what) throws CelParseException {
            if (!check(t)) throw err("expected " + what + " but found '" + peek().text() + "'");
            return advance();
        }

        void expectEof() throws CelParseException {
            if (!check(T.EOF)) throw err("unexpected trailing input '" + peek().text() + "'");
        }

        // ---- precedence climbing (§5.3) ----

        ExprNode parseExpr() throws CelParseException {                 // Expr ::= COr [ "?" COr ":" Expr ]
            ExprNode cond = parseOr();
            if (match(T.QUESTION)) {
                ExprNode thenB = parseOr();
                expect(T.COLON, "':' in conditional");
                ExprNode elseB = parseExpr();                           // right-associative
                return new ExprNode.Cond(cond, thenB, elseB);
            }
            return cond;
        }

        private ExprNode parseOr() throws CelParseException {           // COr ::= CAnd { "||" CAnd }
            ExprNode left = parseAnd();
            while (match(T.OROR)) left = new ExprNode.Or(left, parseAnd());
            return left;
        }

        private ExprNode parseAnd() throws CelParseException {          // CAnd ::= Rel { "&&" Rel }
            ExprNode left = parseRelation();
            while (match(T.ANDAND)) left = new ExprNode.And(left, parseRelation());
            return left;
        }

        private ExprNode parseRelation() throws CelParseException {     // Rel ::= Add [ RelOp Add ]  (no chaining)
            ExprNode left = parseAddition();
            T t = peek().type();
            switch (t) {
                case LT: advance(); return new ExprNode.Lt(left, parseAddition());
                case LE: advance(); return new ExprNode.Le(left, parseAddition());
                case GT: advance(); return new ExprNode.Gt(left, parseAddition());
                case GE: advance(); return new ExprNode.Ge(left, parseAddition());
                case EQ: advance(); return new ExprNode.Eq(left, parseAddition());
                case NE: advance(); return new ExprNode.Ne(left, parseAddition());
                case IN: advance(); return parseInOperand(left);
                default: return left;
            }
        }

        private ExprNode parseInOperand(ExprNode needle) throws CelParseException {
            ExprNode right = parseAddition();
            // §5.3.1 item 6 / §6.5: right must be a list literal or a field designator.
            if (right instanceof ExprNode.ListLit list) {
                return new ExprNode.In(needle, new InListOperand.LiteralList(list));
            }
            if (right instanceof ExprNode.FieldRef fr) {
                return new ExprNode.In(needle, new InListOperand.FieldList(fr));
            }
            throw err("'in' right operand must be a list literal or a field designator (§5.3.1 item 6)");
        }

        private ExprNode parseAddition() throws CelParseException {     // Add ::= Mul { ("+"|"-") Mul }
            ExprNode left = parseMultiplication();
            while (true) {
                if (match(T.PLUS)) left = new ExprNode.Add(left, parseMultiplication());
                else if (match(T.MINUS)) left = new ExprNode.Sub(left, parseMultiplication());
                else return left;
            }
        }

        private ExprNode parseMultiplication() throws CelParseException { // Mul ::= Unary { ("*"|"/"|"%") Unary }
            ExprNode left = parseUnary();
            while (true) {
                if (match(T.STAR)) left = new ExprNode.Mul(left, parseUnary());
                else if (match(T.SLASH)) left = new ExprNode.Div(left, parseUnary());
                else if (match(T.PERCENT)) left = new ExprNode.Mod(left, parseUnary());
                else return left;
            }
        }

        private ExprNode parseUnary() throws CelParseException {        // Unary ::= "!" Unary | "-" Unary | Postfix
            if (match(T.NOT)) return new ExprNode.Not(parseUnary());
            if (check(T.MINUS)) {
                advance();
                // §5.2 fold: unary '-' immediately over the literal 2^63 yields LIT_INT(-2^63).
                if (check(T.INT_LIT) && TWO_63.equals((BigInteger) peek().value())) {
                    advance();
                    return applyPostfix(new ExprNode.LitInt(Long.MIN_VALUE));
                }
                return new ExprNode.Neg(parseUnary());
            }
            return parsePostfix();
        }

        private ExprNode parsePostfix() throws CelParseException {      // Postfix ::= Primary { "." IDENT [ "(" [ExprList] ")" ] }
            return applyPostfix(parsePrimary());
        }

        private ExprNode applyPostfix(ExprNode node) throws CelParseException {
            while (match(T.DOT)) {
                Token name = expect(T.IDENT, "field or method name after '.'");
                if (check(T.LPAREN)) {
                    node = parseMethodCall(node, name.text());
                } else {
                    // plain selector: extend a field designator with an unqualified step.
                    if (RESERVED.contains(name.text())) {
                        throw err("reserved word '" + name.text() + "' cannot be a bare field name; use field(...) (§5.2)");
                    }
                    node = appendUnqual(node, name.text());
                }
            }
            return node;
        }

        private ExprNode parseMethodCall(ExprNode receiver, String method) throws CelParseException {
            expect(T.LPAREN, "'(' ");
            switch (method) {
                case "field" -> {
                    String cn = stringLitArg("field(className, ...)");
                    expect(T.COMMA, "',' in field(...)");
                    String fn = stringLitArg("field(..., fieldName)");
                    expect(T.RPAREN, "')' ");
                    return appendQual(receiver, cn, fn);
                }
                case "contains" -> {
                    ExprNode arg = parseExpr();
                    expect(T.RPAREN, "')' ");
                    if (!(arg instanceof ExprNode.LitString)) {
                        throw err("contains(...) needle must be a string literal (§5.3.1 item 4)");
                    }
                    return new ExprNode.Call(FunctionRegistry.CONTAINS_ID, List.of(receiver, arg));
                }
                case "startsWith" -> {
                    ExprNode arg = parseExpr();
                    expect(T.RPAREN, "')' ");
                    return new ExprNode.Call(FunctionRegistry.STARTS_WITH_ID, List.of(receiver, arg));
                }
                case "endsWith" -> {
                    ExprNode arg = parseExpr();
                    expect(T.RPAREN, "')' ");
                    return new ExprNode.Call(FunctionRegistry.ENDS_WITH_ID, List.of(receiver, arg));
                }
                default -> throw err("unknown method '" + method
                        + "' (only contains/startsWith/endsWith/field are method calls; no user-defined methods, §5.3.1 item 1)");
            }
        }

        /** Appends an unqualified selector step to a field designator; the subject must already be a FieldRef. */
        private ExprNode appendUnqual(ExprNode subject, String name) throws CelParseException {
            if (!(subject instanceof ExprNode.FieldRef fr)) {
                throw err("field selection '." + name + "' applies only to a field designator, not to this expression (§5.3.3)");
            }
            List<SelectorStep> steps = new ArrayList<>(fr.steps());
            steps.add(new SelectorStep.Unqual(name));
            return new ExprNode.FieldRef(steps);
        }

        private ExprNode appendQual(ExprNode subject, String className, String fieldName) throws CelParseException {
            if (!(subject instanceof ExprNode.FieldRef fr)) {
                throw err("qualified selector '.field(...)' applies only to a field designator (§5.3.3)");
            }
            List<SelectorStep> steps = new ArrayList<>(fr.steps());
            steps.add(new SelectorStep.Qual(className, fieldName));
            return new ExprNode.FieldRef(steps);
        }

        private ExprNode parsePrimary() throws CelParseException {
            Token t = peek();
            switch (t.type()) {
                case INT_LIT: advance(); return litInt(t);
                case DOUBLE_LIT: advance(); return new ExprNode.LitDouble((Double) t.value());
                case STRING_LIT: advance(); return new ExprNode.LitString((String) t.value());
                case BYTES_LIT: advance(); return new ExprNode.LitBytes((byte[]) t.value());
                case TRUE: advance(); return new ExprNode.LitBool(true);
                case FALSE: advance(); return new ExprNode.LitBool(false);
                case NULL: advance(); return new ExprNode.LitNull();
                case LBRACKET: return parseListLiteral();
                case LPAREN: {
                    advance();
                    ExprNode e = parseExpr();
                    expect(T.RPAREN, "')' ");
                    return e;
                }
                case IDENT: {
                    if (peek2().type() == T.LPAREN) return parseGlobalCall(t.text());
                    // bare IDENT: a field reference (§5.3.1 item 5)
                    advance();
                    if (RESERVED.contains(t.text())) {
                        throw err("reserved word '" + t.text() + "' cannot be a bare field name; use field(...) (§5.2)");
                    }
                    return new ExprNode.FieldRef(List.of(new SelectorStep.Unqual(t.text())));
                }
                default:
                    throw err("unexpected token '" + t.text() + "' where an expression was expected");
            }
        }

        private ExprNode litInt(Token t) throws CelParseException {
            BigInteger v = (BigInteger) t.value();
            if (v.compareTo(LONG_MAX) > 0) {   // exactly 2^63: legal only under immediate unary '-' (handled in parseUnary)
                throw err("integer literal 2^63 is well-formed only as the immediate operand of unary '-' (§5.2)");
            }
            return new ExprNode.LitInt(v.longValueExact());
        }

        private ExprNode parseListLiteral() throws CelParseException {   // ListLiteral ::= "[" ExprList "]"  (non-empty)
            expect(T.LBRACKET, "'['");
            if (check(T.RBRACKET)) throw err("empty list literal is not allowed (§5.3.2)");
            List<ExprNode> elements = new ArrayList<>();
            elements.add(parseExpr());
            while (match(T.COMMA)) elements.add(parseExpr());
            expect(T.RBRACKET, "']' to close list literal");
            return new ExprNode.ListLit(elements);
        }

        // ---- calls (§5.3.1 item 1 allowlist) ----

        private ExprNode parseGlobalCall(String name) throws CelParseException {
            advance();                       // IDENT
            expect(T.LPAREN, "'('");
            switch (name) {
                case "has" -> {
                    ExprNode arg = parseExpr();
                    expect(T.RPAREN, "')' ");
                    if (!(arg instanceof ExprNode.FieldRef fr)) {
                        throw err("has(...) argument must be a field designator (§5.3.1 item 2)");
                    }
                    return new ExprNode.Has(fr);
                }
                case "field" -> {
                    String cn = stringLitArg("field(className, ...)");
                    expect(T.COMMA, "',' in field(...)");
                    String fn = stringLitArg("field(..., fieldName)");
                    expect(T.RPAREN, "')' ");
                    return new ExprNode.FieldRef(List.of(new SelectorStep.Qual(cn, fn)));
                }
                case "size" -> {
                    ExprNode a = oneArg();
                    return new ExprNode.Call(resolveSize(a), List.of(a));
                }
                case "int" -> { ExprNode a = oneArg(); return new ExprNode.Call(4, List.of(a)); }
                case "double" -> { ExprNode a = oneArg(); return new ExprNode.Call(5, List.of(a)); }
                case "abs" -> { ExprNode a = oneArg(); return new ExprNode.Call(resolveAbs(a), List.of(a)); }
                case "min" -> { ExprNode[] ab = twoArgs(); return new ExprNode.Call(resolveMinMax(true, ab[0], ab[1]), List.of(ab[0], ab[1])); }
                case "max" -> { ExprNode[] ab = twoArgs(); return new ExprNode.Call(resolveMinMax(false, ab[0], ab[1]), List.of(ab[0], ab[1])); }
                case "sqrt" -> { ExprNode a = oneArg(); return new ExprNode.Call(12, List.of(a)); }
                case "radians" -> { ExprNode a = oneArg(); return new ExprNode.Call(13, List.of(a)); }
                case "degrees" -> { ExprNode a = oneArg(); return new ExprNode.Call(14, List.of(a)); }
                case "sin" -> { ExprNode a = oneArg(); return new ExprNode.Call(15, List.of(a)); }
                case "cos" -> { ExprNode a = oneArg(); return new ExprNode.Call(16, List.of(a)); }
                case "tan" -> { ExprNode a = oneArg(); return new ExprNode.Call(17, List.of(a)); }
                case "asin" -> { ExprNode a = oneArg(); return new ExprNode.Call(18, List.of(a)); }
                case "acos" -> { ExprNode a = oneArg(); return new ExprNode.Call(19, List.of(a)); }
                case "atan" -> { ExprNode a = oneArg(); return new ExprNode.Call(20, List.of(a)); }
                case "atan2" -> { ExprNode[] ab = twoArgs(); return new ExprNode.Call(21, List.of(ab[0], ab[1])); }
                case "contains", "startsWith", "endsWith" ->
                        throw err("'" + name + "' is a method (receiver." + name + "(...)), not a global function (§6.6)");
                default -> throw err("unknown function '" + name + "' -- there are no user-defined functions (§5.4 item 3)");
            }
        }

        private ExprNode oneArg() throws CelParseException {
            ExprNode a = parseExpr();
            expect(T.RPAREN, "')' ");
            return a;
        }

        private ExprNode[] twoArgs() throws CelParseException {
            ExprNode a = parseExpr();
            expect(T.COMMA, "',' between the two arguments");
            ExprNode b = parseExpr();
            expect(T.RPAREN, "')' ");
            return new ExprNode[]{ a, b };
        }

        private String stringLitArg(String what) throws CelParseException {
            if (!check(T.STRING_LIT)) throw err(what + " argument must be a string literal (§5.3.1 item 3)");
            return (String) advance().value();
        }

        // ---- overload resolution (schema-free static inference) ----

        private int resolveSize(ExprNode arg) throws CelParseException {
            return switch (CelStaticType.of(arg)) {
                case STRING -> 1;
                case BYTES -> 2;
                case LIST -> 3;
                default -> throw overloadErr("size", arg);
            };
        }

        private int resolveAbs(ExprNode arg) throws CelParseException {
            return switch (CelStaticType.of(arg)) {
                case INT -> 6;
                case DOUBLE -> 7;
                default -> throw overloadErr("abs", arg);
            };
        }

        private int resolveMinMax(boolean isMin, ExprNode a, ExprNode b) throws CelParseException {
            CelStaticType ta = CelStaticType.of(a), tb = CelStaticType.of(b);
            if (ta == CelStaticType.DOUBLE || tb == CelStaticType.DOUBLE) return isMin ? 9 : 11;
            if (ta == CelStaticType.INT || tb == CelStaticType.INT) return isMin ? 8 : 10;
            throw overloadErr(isMin ? "min" : "max", a);
        }

        private CelParseException overloadErr(String fn, ExprNode arg) {
            return new CelParseException("parse error: cannot resolve the '" + fn
                    + "' overload -- its argument's scalar type is not statically known without a schema"
                    + " (this is a documented limitation of the standalone text parser; build such calls through the AST)");
        }

        private CelParseException err(String msg) {
            return new CelParseException("parse error at offset " + peek().pos() + ": " + msg);
        }
    }
}
