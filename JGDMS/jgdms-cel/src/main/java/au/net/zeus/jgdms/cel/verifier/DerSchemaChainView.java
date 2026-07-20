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

package au.net.zeus.jgdms.cel.verifier;

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapts a {@code jgdms-der} {@code AtomicSerialSchemaRecord} chain (STD-006
 * §7.8; the same leaf-first Merkle chain {@code SchemaGenerator}/{@code
 * SchemaChain} build and {@code ObjectCodec.decodeToFieldMap} projects
 * against at evaluation time) to {@link SchemaView} for {@link CelVerifier}.
 * <p>
 * <b>wireType -&gt; {@link CelType} mapping.</b> {@code
 * AtomicSerialFieldDef.wireType()} is a free-form string token, not itself
 * one of the eight expression types ({@code SchemaGenerator}'s own javadoc
 * documents the token vocabulary: {@code "boolean"}, {@code "byte"}/{@code
 * "short"}/{@code "int"}/{@code "long"}, {@code "double"}/{@code "float"},
 * {@code "char"}, {@code "java.lang.String"}, {@code "byte[]"}, {@code
 * "@AtomicSerial"}, {@code "enum:*"}, {@code "array:*"}, the collection
 * tokens ({@code "set:"}/{@code "orderedset:"}/{@code "list:"}/{@code
 * "map:"}/{@code "orderedmap:"}), and {@code "any"}). This adapter maps the
 * tokens with an unambiguous, lossless {@link CelType} correspondence
 * ({@code boolean->BOOL}, the four integral tokens {@code ->INT}, {@code
 * double}/{@code float->DOUBLE}, {@code java.lang.String->STRING}, {@code
 * byte[]->BYTES}, {@code @AtomicSerial->OBJECT}) and returns {@link
 * Optional#empty()} for every other token ({@code char} has no expression-
 * language counterpart; {@code enum:*}/collection tokens/{@code any} carry
 * no closed-grammar scalar type) -- per {@link SchemaView}'s soundness
 * contract, {@code empty()} defers to dynamic evaluation, it never rejects.
 * <p>
 * <b>Nested-schema limitation (documented, not silently absent).</b> {@link
 * #nestedSchema(String, String)} always returns {@link Optional#empty()}:
 * a plain {@code @AtomicSerial}-typed field's wire-type token is the bare
 * marker {@code "@AtomicSerial"} ({@code SchemaGenerator}'s own javadoc:
 * "the runtime class travels in the embedded schema so the marker need not
 * name the class") -- the concrete nested class is a genuinely dynamic,
 * per-instance fact, not something a class-level schema chain can name
 * statically. A selector chain that steps through such a field therefore
 * always defers everything past that step to dynamic evaluation, which is
 * sound (never a false accept) even though it gives up some static coverage
 * for deeply nested field references.
 */
public final class DerSchemaChainView implements SchemaView {

    private final List<AtomicSerialSchemaRecord> chain; // leaf-first, per SchemaChain's convention

    /**
     * @param leafFirstChain the schema chain, leaf class first, root class last
     *                       (the same order {@code SchemaChain}/{@code SchemaGenerator} use)
     * @throws NullPointerException     if {@code leafFirstChain} or any element is {@code null}
     * @throws IllegalArgumentException if {@code leafFirstChain} is empty
     */
    public DerSchemaChainView(List<AtomicSerialSchemaRecord> leafFirstChain) {
        Objects.requireNonNull(leafFirstChain, "leafFirstChain");
        if (leafFirstChain.isEmpty()) {
            throw new IllegalArgumentException("leafFirstChain must not be empty");
        }
        for (AtomicSerialSchemaRecord r : leafFirstChain) {
            Objects.requireNonNull(r, "leafFirstChain element");
        }
        this.chain = List.copyOf(leafFirstChain);
    }

    private AtomicSerialSchemaRecord recordFor(String className) {
        for (AtomicSerialSchemaRecord r : chain) {
            if (r.className().equals(className)) return r;
        }
        return null;
    }

    private static AtomicSerialFieldDef fieldFor(AtomicSerialSchemaRecord record, String fieldName) {
        for (AtomicSerialFieldDef f : record.fields()) {
            if (f.wireName().equals(fieldName)) return f;
        }
        return null;
    }

    @Override
    public List<String> namespaceChain() {
        List<String> names = new ArrayList<>(chain.size());
        for (AtomicSerialSchemaRecord r : chain) names.add(r.className());
        return List.copyOf(names);
    }

    @Override
    public boolean declaresField(String className, String fieldName) {
        AtomicSerialSchemaRecord r = recordFor(className);
        return r != null && fieldFor(r, fieldName) != null;
    }

    @Override
    public Optional<CelType> fieldType(String className, String fieldName) {
        AtomicSerialSchemaRecord r = recordFor(className);
        if (r == null) return Optional.empty();
        AtomicSerialFieldDef f = fieldFor(r, fieldName);
        if (f == null) return Optional.empty();
        return celTypeOfWireType(f.wireType());
    }

    @Override
    public Optional<SchemaView> nestedSchema(String className, String fieldName) {
        return Optional.empty(); // see class javadoc: plain @AtomicSerial fields are polymorphic, not statically resolvable here
    }

    /** The lossless subset of {@code SchemaGenerator}'s wire-type token vocabulary this adapter can pin to a {@link CelType}; every other token defers (see class javadoc). */
    static Optional<CelType> celTypeOfWireType(String wireType) {
        return switch (wireType) {
            case "boolean" -> Optional.of(CelType.BOOL);
            case "byte", "short", "int", "long" -> Optional.of(CelType.INT);
            case "double", "float" -> Optional.of(CelType.DOUBLE);
            case "java.lang.String" -> Optional.of(CelType.STRING);
            case "byte[]" -> Optional.of(CelType.BYTES);
            case "@AtomicSerial" -> Optional.of(CelType.OBJECT);
            default -> Optional.empty(); // char, enum:*, array:*, set:/list:/map:/orderedset:/orderedmap:*, any, java.lang.Class
        };
    }
}
