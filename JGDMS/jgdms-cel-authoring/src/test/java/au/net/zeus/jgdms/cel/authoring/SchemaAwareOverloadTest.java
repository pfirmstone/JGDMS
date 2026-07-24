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

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.ast.ExprNode.SelectorStep;
import au.net.zeus.jgdms.cel.verifier.SchemaView;
import au.net.zeus.jgdms.cel.wire.CelDecoder;
import au.net.zeus.jgdms.cel.wire.CelFilterRecord;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OQ-1 closure: schema-aware type-dispatched overload resolution
 * ({@code size}/{@code abs}/{@code min}/{@code max}) via
 * {@link CelTextParser#parse(String, SchemaView)}, resolving the Appendix B
 * §B.8.3 wire id from a field's DECLARED type. Uses the same inference
 * {@code CelVerifier} runs ({@code StaticTypeChecker} via
 * {@code au.net.zeus.jgdms.cel.verifier.CelTypeInference}). Canonicality (G1)
 * is proven: a schema-resolved expression encodes byte-identically to the
 * equivalent hand-built AST with the same id.
 */
class SchemaAwareOverloadTest {

    /** One namespace "T" with a field of each shape needed; plus a nested object and an indeterminate ("Any") field. */
    private static SchemaView schema() {
        TestSchema inner = new TestSchema()
                .field("innerList", CelType.LIST)
                .field("innerInt", CelType.INT);
        return new TestSchema()
                .field("strField", CelType.STRING)
                .field("bytesField", CelType.BYTES)
                .field("listField", CelType.LIST)
                .field("intA", CelType.INT)
                .field("intB", CelType.INT)
                .field("dblA", CelType.DOUBLE)
                .field("dblB", CelType.DOUBLE)
                .unresolved("anyField")               // declared, but type not statically pinnable
                .objectField("obj", inner);
    }

    private static int id(String text, SchemaView s) throws Exception {
        ExprNode node = CelTextParser.parse(text, s);
        return ((ExprNode.Call) node).functionId();
    }

    // ---- resolution from declared field types ----
    @Test void sizeResolvesFromDeclaredType() throws Exception {
        assertEquals(1, id("size(strField)", schema()));
        assertEquals(2, id("size(bytesField)", schema()));
        assertEquals(3, id("size(listField)", schema()));
        assertEquals(3, id("size(obj.innerList)", schema()));   // nested selector chain, via the reused traversal
    }

    @Test void absResolvesFromDeclaredType() throws Exception {
        assertEquals(6, id("abs(intA)", schema()));
        assertEquals(7, id("abs(dblA)", schema()));
    }

    @Test void minMaxResolveFromDeclaredType() throws Exception {
        assertEquals(8, id("min(intA, intB)", schema()));
        assertEquals(9, id("min(dblA, dblB)", schema()));
        assertEquals(10, id("max(intA, intB)", schema()));
        assertEquals(11, id("max(dblA, dblB)", schema()));
    }

    // ---- fail-closed ----
    @Test void failClosedCases() {
        assertThrows(CelParseException.class, () -> CelTextParser.parse("min(intA, dblA)", schema())); // differing types
        assertThrows(CelParseException.class, () -> CelTextParser.parse("max(intA, dblB)", schema()));
        assertThrows(CelParseException.class, () -> CelTextParser.parse("size(absentField)", schema())); // absent from schema
        assertThrows(CelParseException.class, () -> CelTextParser.parse("abs(absentField)", schema()));
        assertThrows(CelParseException.class, () -> CelTextParser.parse("size(anyField)", schema()));   // declared, indeterminate type
        assertThrows(CelParseException.class, () -> CelTextParser.parse("abs(strField)", schema()));    // known but wrong shape (string)
    }

    // ---- regression: the SAME text schema-free still rejects (fix-#4 preserved) ----
    @Test void schemaFreeStillRejectsFieldOverloads() {
        assertThrows(CelParseException.class, () -> CelTextParser.parse("size(strField)"));
        assertThrows(CelParseException.class, () -> CelTextParser.parse("abs(intA)"));
        assertThrows(CelParseException.class, () -> CelTextParser.parse("min(intA, intB)"));
        // and the schema-free literal cases still resolve unchanged
        assertDoesNotThrowParse("size(\"abc\")");
        assertDoesNotThrowParse("abs(-3)");
        assertDoesNotThrowParse("min(1, 2)");
    }

    private static void assertDoesNotThrowParse(String text) {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> CelTextParser.parse(text));
    }

    // ---- canonicality (G1): schema affects the id, not the bytes ----
    @Test void schemaResolvedEncodesByteIdenticalToHandBuiltAst() throws Exception {
        ExprNode parsed = CelTextParser.parse("min(intA, intB)", schema());
        ExprNode handBuilt = new ExprNode.Call(8, List.of(fr("intA"), fr("intB")));
        assertEquals(handBuilt, parsed);

        byte[] fromParse = CelEncoder.encodeExpression(parsed);
        byte[] fromAst = CelEncoder.encodeExpression(handBuilt);
        assertArrayEquals(fromAst, fromParse, "schema resolution must not change encoding (G1)");

        // full record round-trip through the decoder oracle
        CelFilterRecord rec = CelRecordBuilder.predicate(parsed);
        assertEquals(rec, CelDecoder.decode(CelEncoder.encode(rec)));
    }

    @Test void sizeListFieldEncodesByteIdenticalToHandBuiltId3() throws Exception {
        ExprNode parsed = CelTextParser.parse("size(listField)", schema());
        ExprNode handBuilt = new ExprNode.Call(3, List.of(fr("listField")));
        assertArrayEquals(CelEncoder.encodeExpression(handBuilt), CelEncoder.encodeExpression(parsed));
    }

    private static ExprNode.FieldRef fr(String name) {
        return new ExprNode.FieldRef(List.of(new SelectorStep.Unqual(name)));
    }

    // ---- minimal in-memory SchemaView (jgdms-cel's FakeSchemaView is test-scoped there) ----
    private static final class TestSchema implements SchemaView {
        private static final String CLASS = "com.example.T";
        private final Map<String, CelType> fields = new LinkedHashMap<>();
        private final Set<String> unresolved = new java.util.LinkedHashSet<>();
        private final Map<String, SchemaView> nested = new LinkedHashMap<>();

        TestSchema field(String name, CelType type) { fields.put(name, type); return this; }
        TestSchema unresolved(String name) { unresolved.add(name); return this; }
        TestSchema objectField(String name, SchemaView view) { fields.put(name, CelType.OBJECT); nested.put(name, view); return this; }

        @Override public List<String> namespaceChain() { return List.of(CLASS); }

        @Override public boolean declaresField(String className, String fieldName) {
            return CLASS.equals(className) && (fields.containsKey(fieldName) || unresolved.contains(fieldName));
        }

        @Override public Optional<CelType> fieldType(String className, String fieldName) {
            if (!CLASS.equals(className)) return Optional.empty();
            return Optional.ofNullable(fields.get(fieldName));   // empty for unresolved/absent
        }

        @Override public Optional<SchemaView> nestedSchema(String className, String fieldName) {
            if (!CLASS.equals(className)) return Optional.empty();
            return Optional.ofNullable(nested.get(fieldName));
        }
    }
}
