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
import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.eval.CandidateProjection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link CandidateProjection} from the corpus's pure-data JSON
 * shape ({@code CORPUS-FORMAT.md} Sec 2.3) -- the runner-side half of "the
 * corpus is pure data": nothing here is corpus content, it is only the glue
 * that maps that content onto the real public {@code CandidateProjection}
 * interface {@link au.net.zeus.jgdms.cel.eval.Evaluator} actually consumes.
 */
final class PureCandidateProjection implements CandidateProjection {

    private final boolean undecodable;
    private final List<String> namespaceChain;
    private final Map<String, Map<String, CelValue>> fields;

    private PureCandidateProjection(boolean undecodable, List<String> namespaceChain,
            Map<String, Map<String, CelValue>> fields) {
        this.undecodable = undecodable;
        this.namespaceChain = namespaceChain;
        this.fields = fields;
    }

    @SuppressWarnings("unchecked")
    static PureCandidateProjection fromJson(Map<String, Object> json) {
        if (json == null) {
            throw new IllegalArgumentException("eval vector is missing its \"candidate\" object");
        }
        if (Boolean.TRUE.equals(json.get("undecodable"))) {
            return new PureCandidateProjection(true, List.of(), Map.of());
        }
        List<String> chain = stringList(json.get("namespaceChain"));
        Map<String, Object> fieldsJson = (Map<String, Object>) json.getOrDefault("fields", Map.of());
        Map<String, Map<String, CelValue>> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> classEntry : fieldsJson.entrySet()) {
            Map<String, Object> perClass = (Map<String, Object>) classEntry.getValue();
            Map<String, CelValue> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> fieldEntry : perClass.entrySet()) {
                values.put(fieldEntry.getKey(), toCelValue((Map<String, Object>) fieldEntry.getValue()));
            }
            fields.put(classEntry.getKey(), values);
        }
        return new PureCandidateProjection(false, chain, fields);
    }

    @SuppressWarnings("unchecked")
    static CelValue toCelValue(Map<String, Object> tv) {
        String type = (String) tv.get("type");
        if (type == null) {
            throw new IllegalArgumentException("typed value missing \"type\": " + tv);
        }
        return switch (type) {
            case "BOOL" -> new CelValue.BoolV((Boolean) tv.get("value"));
            case "INT" -> new CelValue.IntV(Long.parseLong((String) tv.get("value")));
            case "DOUBLE" -> new CelValue.DoubleV(Bits.parseDoubleBits((String) tv.get("value")));
            case "STRING" -> new CelValue.StringV((String) tv.get("value"));
            case "BYTES" -> new CelValue.BytesV(Bits.hexToBytes((String) tv.get("value")));
            case "NULL" -> CelValue.NullV.INSTANCE;
            case "LIST" -> {
                String elementType = (String) tv.get("elementType");
                CelType t = CelType.valueOf(elementType);
                List<Object> rawElements = (List<Object>) tv.getOrDefault("elements", List.of());
                List<CelValue> elements = new ArrayList<>(rawElements.size());
                for (Object raw : rawElements) {
                    elements.add(rawScalarToCelValue(elementType, raw));
                }
                yield new CelValue.ListV(t, elements);
            }
            case "OBJECT" -> new CelValue.ObjectV(fromJson((Map<String, Object>) tv.get("projection")));
            default -> throw new IllegalArgumentException("unknown typed-value \"type\": " + type);
        };
    }

    private static CelValue rawScalarToCelValue(String elementType, Object raw) {
        return switch (elementType) {
            case "BOOL" -> new CelValue.BoolV((Boolean) raw);
            case "INT" -> new CelValue.IntV(Long.parseLong((String) raw));
            case "DOUBLE" -> new CelValue.DoubleV(Bits.parseDoubleBits((String) raw));
            case "STRING" -> new CelValue.StringV((String) raw);
            case "BYTES" -> new CelValue.BytesV(Bits.hexToBytes((String) raw));
            default -> throw new IllegalArgumentException("list elementType must be a scalar type, got: " + elementType);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object o) {
        if (o == null) return List.of();
        List<String> result = new ArrayList<>();
        for (Object x : (List<Object>) o) {
            result.add((String) x);
        }
        return result;
    }

    @Override
    public boolean isUndecodable() {
        return undecodable;
    }

    @Override
    public List<String> namespaceChain() {
        return namespaceChain;
    }

    @Override
    public boolean declaresField(String className, String fieldName) {
        Map<String, CelValue> ns = fields.get(className);
        return ns != null && ns.containsKey(fieldName);
    }

    @Override
    public CelValue fieldValue(String className, String fieldName) {
        Map<String, CelValue> ns = fields.get(className);
        if (ns == null || !ns.containsKey(fieldName)) {
            throw new IllegalStateException("corpus vector bug: fieldValue() called without declaresField() true: "
                    + className + "." + fieldName);
        }
        return ns.get(fieldName);
    }
}
