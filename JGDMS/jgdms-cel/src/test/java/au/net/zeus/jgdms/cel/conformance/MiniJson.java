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

package au.net.zeus.jgdms.cel.conformance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free recursive-descent JSON reader, purpose-built to
 * parse the conformance corpus's own {@code vectors/*.json} files
 * ({@code CORPUS-FORMAT.md}) without pulling an external JSON library into
 * this module's test classpath -- consistent with the module's own house
 * style ({@code TestDer} is a hand-rolled DER builder for the same reason).
 * Not a general-purpose JSON library: just enough of RFC 8259 to round-trip
 * this corpus's own shapes (objects, arrays, strings with the standard
 * escapes, numbers, {@code true}/{@code false}/{@code null}).
 * <p>
 * Parses to plain JDK types: a JSON object becomes a {@code Map<String,Object>}
 * (insertion-ordered), an array becomes a {@code List<Object>}, a string
 * becomes a {@code String}, a number becomes a {@code Double} or {@code Long}
 * (integral literals with no {@code .}/exponent parse as {@code Long}),
 * {@code true}/{@code false} become {@code Boolean}, and {@code null} becomes
 * Java {@code null}.
 */
final class MiniJson {

    private MiniJson() {}

    static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object result = p.parseValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonParseException("trailing content after top-level JSON value at offset " + p.pos);
        }
        return result;
    }

    static final class JsonParseException extends RuntimeException {
        JsonParseException(String message) { super(message); }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; this.pos = 0; }

        boolean atEnd() { return pos >= s.length(); }

        char peek() {
            if (atEnd()) throw new JsonParseException("unexpected end of input at offset " + pos);
            return s.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            char actual = next();
            if (actual != c) {
                throw new JsonParseException("expected '" + c + "' but got '" + actual + "' at offset " + (pos - 1));
            }
        }

        void skipWhitespace() {
            while (!atEnd()) {
                char c = peek();
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') { pos++; return result; }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == ',') continue;
                if (c == '}') break;
                throw new JsonParseException("expected ',' or '}' in object at offset " + (pos - 1));
            }
            return result;
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') { pos++; return result; }
            while (true) {
                Object value = parseValue();
                result.add(value);
                skipWhitespace();
                char c = next();
                if (c == ',') continue;
                if (c == ']') break;
                throw new JsonParseException("expected ',' or ']' in array at offset " + (pos - 1));
            }
            return result;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw new JsonParseException("truncated \\u escape at offset " + pos);
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new JsonParseException("invalid escape '\\" + esc + "' at offset " + (pos - 1));
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new JsonParseException("invalid literal at offset " + pos);
        }

        Object parseNull() {
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            throw new JsonParseException("invalid literal at offset " + pos);
        }

        Object parseNumber() {
            int start = pos;
            if (!atEnd() && peek() == '-') pos++;
            while (!atEnd() && Character.isDigit(peek())) pos++;
            boolean isFloating = false;
            if (!atEnd() && peek() == '.') {
                isFloating = true;
                pos++;
                while (!atEnd() && Character.isDigit(peek())) pos++;
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                isFloating = true;
                pos++;
                if (!atEnd() && (peek() == '+' || peek() == '-')) pos++;
                while (!atEnd() && Character.isDigit(peek())) pos++;
            }
            if (pos == start) throw new JsonParseException("invalid number at offset " + pos);
            String literal = s.substring(start, pos);
            return isFloating ? (Object) Double.parseDouble(literal) : (Object) Long.parseLong(literal);
        }
    }
}
