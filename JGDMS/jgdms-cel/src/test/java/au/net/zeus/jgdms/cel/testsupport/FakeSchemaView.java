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

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.verifier.SchemaView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A minimal, in-memory {@link SchemaView} for tests: a leaf-first list of
 * class namespaces, each a {@code fieldName -> CelType} map, with optional
 * nested-object schema links -- deliberately more capable than {@code
 * DerSchemaChainView} (which can never resolve a nested schema, a documented
 * real limitation) so the general nested-selector-chain traversal logic in
 * {@code StaticTypeChecker} can be exercised end-to-end. Not production
 * code -- exercises the same interface a real schema-registry binding would
 * implement. Mirrors {@link FakeCandidateProjection}'s builder shape
 * deliberately, since {@link SchemaView} is that interface's static,
 * per-class counterpart.
 */
public final class FakeSchemaView implements SchemaView {

    private final List<String> chain;
    private final Map<String, Map<String, CelType>> fieldTypes;
    private final Map<String, Map<String, SchemaView>> nested;
    /** Fields present with a schema-unresolvable ("Any") type: declared, but {@link #fieldType} defers. */
    private final Map<String, java.util.Set<String>> unresolvedTypeFields;

    private FakeSchemaView(List<String> chain, Map<String, Map<String, CelType>> fieldTypes,
            Map<String, Map<String, SchemaView>> nested, Map<String, java.util.Set<String>> unresolvedTypeFields) {
        this.chain = chain;
        this.fieldTypes = fieldTypes;
        this.nested = nested;
        this.unresolvedTypeFields = unresolvedTypeFields;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<String> namespaceChain() {
        return chain;
    }

    @Override
    public boolean declaresField(String className, String fieldName) {
        Map<String, CelType> ns = fieldTypes.get(className);
        boolean typed = ns != null && ns.containsKey(fieldName);
        boolean untyped = unresolvedTypeFields.getOrDefault(className, java.util.Set.of()).contains(fieldName);
        return typed || untyped;
    }

    @Override
    public Optional<CelType> fieldType(String className, String fieldName) {
        Map<String, CelType> ns = fieldTypes.get(className);
        if (ns == null || !ns.containsKey(fieldName)) return Optional.empty();
        return Optional.of(ns.get(fieldName));
    }

    @Override
    public Optional<SchemaView> nestedSchema(String className, String fieldName) {
        Map<String, SchemaView> ns = nested.get(className);
        if (ns == null) return Optional.empty();
        return Optional.ofNullable(ns.get(fieldName));
    }

    public static final class Builder {
        private final List<String> chain = new java.util.ArrayList<>();
        private final Map<String, Map<String, CelType>> fieldTypes = new LinkedHashMap<>();
        private final Map<String, Map<String, SchemaView>> nested = new LinkedHashMap<>();
        private final Map<String, java.util.Set<String>> unresolvedTypeFields = new LinkedHashMap<>();

        /** Adds a namespace (leaf-first order: call for the most-derived class first). */
        public Builder namespace(String className) {
            if (!chain.contains(className)) chain.add(className);
            fieldTypes.computeIfAbsent(className, k -> new LinkedHashMap<>());
            return this;
        }

        public Builder field(String className, String fieldName, CelType type) {
            namespace(className);
            fieldTypes.get(className).put(fieldName, type);
            return this;
        }

        /** Declares a field present in the schema whose type this view cannot statically resolve (an "Any"-shaped field) -- {@link #fieldType} defers for it. */
        public Builder unresolvedTypeField(String className, String fieldName) {
            namespace(className);
            unresolvedTypeFields.computeIfAbsent(className, k -> new java.util.LinkedHashSet<>()).add(fieldName);
            return this;
        }

        public Builder objectField(String className, String fieldName, SchemaView nestedView) {
            field(className, fieldName, CelType.OBJECT);
            nested.computeIfAbsent(className, k -> new LinkedHashMap<>()).put(fieldName, nestedView);
            return this;
        }

        public FakeSchemaView build() {
            return new FakeSchemaView(List.copyOf(chain),
                    Map.copyOf(fieldTypes),
                    Map.copyOf(nested),
                    Map.copyOf(unresolvedTypeFields));
        }
    }
}
