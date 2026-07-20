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

import au.net.zeus.jgdms.cel.CelType;
import au.net.zeus.jgdms.cel.verifier.SchemaView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a {@link SchemaView} from the corpus's pure-data JSON shape
 * (CORPUS-FORMAT.md Sec 2.4). No corpus vector needs a statically-known
 * nested-object schema, so {@link #nestedSchema} always defers
 * ({@link Optional#empty()}) -- exactly the sound "can't confirm, defer"
 * answer {@link SchemaView}'s own contract requires when unknown.
 */
final class PureSchemaView implements SchemaView {

    private final List<String> namespaceChain;
    private final Map<String, Map<String, CelType>> fieldTypes;
    private final Map<String, Set<String>> unresolvedTypeFields;

    private PureSchemaView(List<String> namespaceChain, Map<String, Map<String, CelType>> fieldTypes,
            Map<String, Set<String>> unresolvedTypeFields) {
        this.namespaceChain = namespaceChain;
        this.fieldTypes = fieldTypes;
        this.unresolvedTypeFields = unresolvedTypeFields;
    }

    @SuppressWarnings("unchecked")
    static PureSchemaView fromJson(Map<String, Object> json) {
        List<String> chain = new ArrayList<>();
        for (Object o : (List<Object>) json.getOrDefault("namespaceChain", List.of())) {
            chain.add((String) o);
        }
        Map<String, Map<String, CelType>> fieldTypes = new LinkedHashMap<>();
        Map<String, Object> fieldsJson = (Map<String, Object>) json.getOrDefault("fields", Map.of());
        for (Map.Entry<String, Object> classEntry : fieldsJson.entrySet()) {
            Map<String, Object> perClass = (Map<String, Object>) classEntry.getValue();
            Map<String, CelType> types = new LinkedHashMap<>();
            for (Map.Entry<String, Object> fieldEntry : perClass.entrySet()) {
                types.put(fieldEntry.getKey(), CelType.valueOf((String) fieldEntry.getValue()));
            }
            fieldTypes.put(classEntry.getKey(), types);
        }
        Map<String, Set<String>> unresolved = new LinkedHashMap<>();
        Map<String, Object> unresolvedJson = (Map<String, Object>) json.getOrDefault("unresolvedTypeFields", Map.of());
        for (Map.Entry<String, Object> classEntry : unresolvedJson.entrySet()) {
            Set<String> names = new java.util.LinkedHashSet<>();
            for (Object o : (List<Object>) classEntry.getValue()) {
                names.add((String) o);
            }
            unresolved.put(classEntry.getKey(), names);
        }
        return new PureSchemaView(chain, fieldTypes, unresolved);
    }

    @Override
    public List<String> namespaceChain() {
        return namespaceChain;
    }

    @Override
    public boolean declaresField(String className, String fieldName) {
        Map<String, CelType> ns = fieldTypes.get(className);
        boolean typed = ns != null && ns.containsKey(fieldName);
        boolean untyped = unresolvedTypeFields.getOrDefault(className, Set.of()).contains(fieldName);
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
        return Optional.empty();
    }
}
