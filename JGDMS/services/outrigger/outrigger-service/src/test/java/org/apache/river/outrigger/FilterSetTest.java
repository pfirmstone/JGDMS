/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.outrigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import au.net.zeus.jgdms.cel.ast.ExprNode;

/**
 * Unit B3 — the per-template plurality carrier ({@link FilterSet}) and its
 * candidate&rarr;filter selection rule (design memo B3 &sect;1.2/&sect;5, the
 * ratified subclass/schema-less overlay). Pure logic; no Outrigger runtime.
 */
public class FilterSetTest {

    private static byte[] digest(int seed) {
        byte[] d = new byte[32];
        for (int i = 0; i < 32; i++) d[i] = (byte) (seed + i);
        return d;
    }

    /** A distinct compiled filter with the given applicability key (null = schema-less). */
    private static CompiledFilter filter(byte[] key) {
        return new CompiledFilter(new ExprNode.LitBool(true), 0L, key);
    }

    @Test
    public void emptyIsEmptyAndYieldsNoConstraint() {
        assertTrue(FilterSet.EMPTY.isEmpty());
        assertTrue(FilterSet.EMPTY.applicableTo(digest(1)).isEmpty());
    }

    @Test
    public void singleConcreteExactMatch() {
        byte[] d1 = digest(1);
        CompiledFilter f1 = filter(d1);
        FilterSet fs = FilterSet.of(f1);
        assertFalse(fs.isEmpty());
        List<CompiledFilter> a = fs.applicableTo(d1);
        assertEquals(1, a.size());
        assertSame(f1, a.get(0));
    }

    @Test
    public void singleConcreteSubclassFallsBackToTheOneFilter() {
        // A subclass candidate carries a DIFFERENT digest but must still be
        // filtered by the one concrete filter it byte-matched (§3 amendment).
        CompiledFilter f1 = filter(digest(1));
        FilterSet fs = FilterSet.of(f1);
        List<CompiledFilter> a = fs.applicableTo(digest(99)); // unknown digest
        assertEquals(1, a.size());
        assertSame(f1, a.get(0));
    }

    @Test
    public void schemaLessAppliesToEveryCandidate() {
        CompiledFilter f0 = filter(null); // schema-less
        FilterSet fs = FilterSet.of(f0);
        assertEquals(List.of(f0), fs.applicableTo(digest(7)));
        assertEquals(List.of(f0), fs.applicableTo(digest(8)));
    }

    @Test
    public void schemaLessOverlaysOntoConcrete() {
        CompiledFilter f0 = filter(null);
        byte[] d1 = digest(1);
        CompiledFilter f1 = filter(d1);
        FilterSet fs = new FilterSet.Builder().add(f0).add(f1).build();
        List<CompiledFilter> a = fs.applicableTo(d1);
        assertEquals(2, a.size());
        assertTrue(a.contains(f0));
        assertTrue(a.contains(f1));
    }

    @Test
    public void multipleDistinctSchemasExactMatchSelectsOnlyThatFilter() {
        byte[] d1 = digest(1), d2 = digest(50);
        CompiledFilter f1 = filter(d1), f2 = filter(d2);
        FilterSet fs = new FilterSet.Builder().add(f1).add(f2).build();
        // A candidate of schema 1 is filtered by f1 ONLY, never f2 (§5).
        assertEquals(List.of(f1), fs.applicableTo(d1));
        assertEquals(List.of(f2), fs.applicableTo(d2));
    }

    @Test
    public void multipleDistinctSchemasSubclassWithoutHintFailsClosed() {
        CompiledFilter f1 = filter(digest(1)), f2 = filter(digest(50));
        FilterSet fs = new FilterSet.Builder().add(f1).add(f2).build();
        // Unknown digest, no matched-template hint => cannot recover the
        // originating filter => null (fail-closed exclusion), never a guess.
        assertNull(fs.applicableTo(digest(99)));
    }

    @Test
    public void multipleDistinctSchemasSubclassWithHintResolves() {
        byte[] d1 = digest(1), d2 = digest(50);
        CompiledFilter f1 = filter(d1), f2 = filter(d2);
        FilterSet fs = new FilterSet.Builder().add(f1).add(f2).build();
        // The match site knows the byte-matched template's digest (d1) => f1.
        assertEquals(List.of(f1), fs.applicableTo(digest(99), d1));
    }

    @Test
    public void builderDeDupesSameSchemaDigest() {
        // Two templates of the SAME schema share one predicate (one envelope per
        // op) => one byDigest entry, not a rejection (§5 collision note).
        byte[] d1 = digest(1);
        CompiledFilter f1 = filter(d1), f1b = filter(d1);
        FilterSet fs = new FilterSet.Builder().add(f1).add(f1b).build();
        List<CompiledFilter> a = fs.applicableTo(d1);
        assertEquals(1, a.size());
        assertSame(f1b, a.get(0)); // last write wins, same key
    }
}
