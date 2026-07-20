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

import au.net.zeus.jgdms.cel.CelValue;
import au.net.zeus.jgdms.cel.eval.CandidateProjection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, in-memory {@link CandidateProjection} for tests: a leaf-first
 * list of class namespaces, each a {@code fieldName -> CelValue} map. Not
 * production code -- exercises the same interface a real Outrigger/BAE
 * binding would implement.
 */
public final class FakeCandidateProjection implements CandidateProjection {

    private final List<String> chain;
    private final Map<String, Map<String, CelValue>> namespaces;
    private final boolean undecodable;

    private FakeCandidateProjection(List<String> chain, Map<String, Map<String, CelValue>> namespaces, boolean undecodable) {
        this.chain = chain;
        this.namespaces = namespaces;
        this.undecodable = undecodable;
    }

    public static FakeCandidateProjection undecodable() {
        return new FakeCandidateProjection(List.of(), Map.of(), true);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean isUndecodable() {
        return undecodable;
    }

    @Override
    public List<String> namespaceChain() {
        return chain;
    }

    @Override
    public boolean declaresField(String className, String fieldName) {
        Map<String, CelValue> ns = namespaces.get(className);
        return ns != null && ns.containsKey(fieldName);
    }

    @Override
    public CelValue fieldValue(String className, String fieldName) {
        Map<String, CelValue> ns = namespaces.get(className);
        if (ns == null || !ns.containsKey(fieldName)) {
            throw new IllegalStateException("fieldValue() called without declaresField() true: "
                    + className + "." + fieldName);
        }
        return ns.get(fieldName);
    }

    public static final class Builder {
        private final List<String> chain = new java.util.ArrayList<>();
        private final Map<String, Map<String, CelValue>> namespaces = new LinkedHashMap<>();

        /** Adds a namespace (leaf-first order: call for the most-derived class first). */
        public Builder namespace(String className) {
            chain.add(className);
            namespaces.computeIfAbsent(className, k -> new LinkedHashMap<>());
            return this;
        }

        public Builder field(String className, String fieldName, CelValue value) {
            namespaces.computeIfAbsent(className, k -> {
                chain.add(className);
                return new LinkedHashMap<>();
            }).put(fieldName, value);
            return this;
        }

        public FakeCandidateProjection build() {
            return new FakeCandidateProjection(List.copyOf(chain), namespaces, false);
        }
    }
}
