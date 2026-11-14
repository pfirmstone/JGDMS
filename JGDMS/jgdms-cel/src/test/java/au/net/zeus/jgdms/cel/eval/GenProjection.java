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

package au.net.zeus.jgdms.cel.eval;

import au.net.zeus.jgdms.cel.CelValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link CandidateProjection} over plain maps that logs every call to a {@link TraceLog.Sink}, for the
 * differential test. Deliberately NOT {@code FakeCandidateProjection}: that class answers {@code false} for a
 * class outside the chain, so it cannot tell an evaluator that dropped the
 * {@code namespaceChain().contains(className)} guard of qualified resolution. Here a "hidden" class may
 * declare a field without being in the chain, and a field named {@link #poison} throws, so a record that
 * fails mid-evaluation is part of the corpus.
 * <p>
 * Mutable between evaluations only ({@link CelCorpusGenerator} reseeds one object for the next record, which
 * is how an evaluator that caches resolution by projection identity is caught); within one evaluation every
 * query answers the identical instance, as the {@link CandidateProjection} contract requires.
 */
final class GenProjection implements CandidateProjection {

    private static final long INDEX_MASK = 0xFFFFFL;

    final TraceLog.Sink sink;
    final int id;
    boolean undecodable;
    List<String> chain = List.of();
    final Map<String, Map<String, CelValue>> data = new HashMap<>();
    /** A field name whose {@code fieldValue} throws {@code IllegalStateException("poison")}, or null. */
    String poison;

    GenProjection(TraceLog.Sink sink, int id) {
        this.sink = sink;
        this.id = id;
    }

    private long code(long kind, String className, String fieldName) {
        return (kind << 56) | ((long) id << 40) | ((CelCorpusGenerator.classIndex(className) & INDEX_MASK) << 20)
                | (CelCorpusGenerator.fieldIndex(fieldName) & INDEX_MASK);
    }

    @Override
    public boolean isUndecodable() {
        sink.log(code(TraceLog.PROJ_UNDECODABLE, null, null), 0L);
        return undecodable;
    }

    @Override
    public List<String> namespaceChain() {
        sink.log(code(TraceLog.PROJ_CHAIN, null, null), 0L);
        return chain;
    }

    @Override
    public boolean declaresField(String className, String fieldName) {
        sink.log(code(TraceLog.PROJ_DECLARES, className, fieldName), 0L);
        Map<String, CelValue> namespace = data.get(className);
        return namespace != null && namespace.containsKey(fieldName);
    }

    @Override
    public CelValue fieldValue(String className, String fieldName) {
        sink.log(code(TraceLog.PROJ_VALUE, className, fieldName), 0L);
        if (fieldName.equals(poison)) throw new IllegalStateException("poison");
        return data.get(className).get(fieldName);
    }
}
