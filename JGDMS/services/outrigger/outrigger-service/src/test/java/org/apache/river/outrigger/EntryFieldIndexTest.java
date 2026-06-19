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

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.jini.core.entry.Entry;
import org.apache.river.outrigger.proxy.EntryRep;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link EntryFieldIndex}: most-selective-field candidate
 * selection, empty-bucket early-abort, wildcard fall-through, supertype
 * templates (fewer fields than stored entries), null stored fields, and
 * removal.  Exercised over real {@link EntryRep}/{@link EntryHandle}s so the
 * per-field hash is the same {@code MarshalledInstance.hashCode()} the live
 * matcher uses.
 */
public class EntryFieldIndexTest {

    /** Two-field base entry.  Canonical field order (alpha within class): a, b. */
    public static class Base implements Entry {
        public String a;
        public String b;
        public Base() {}
        public Base(String a, String b) { this.a = a; this.b = b; }
    }

    /** Subclass adds field c.  Canonical order (super before sub): a, b, c. */
    public static class Sub extends Base {
        public String c;
        public Sub() {}
        public Sub(String a, String b, String c) { super(a, b); this.c = c; }
    }

    private final ConcurrentLinkedQueue<EntryHandle> content =
        new ConcurrentLinkedQueue<EntryHandle>();

    private EntryHandle handle(Entry e) throws Exception {
        return new EntryHandle(new EntryRep(e), null, null, content);
    }

    private EntryRep tmpl(Entry e) throws Exception {
        return new EntryRep(e);
    }

    @Test
    public void picksMostSelectiveConstrainedField() throws Exception {
        EntryFieldIndex ix = new EntryFieldIndex();
        // field a="x" is shared by all three; field b is distinct -> b is more selective.
        EntryHandle h1 = handle(new Sub("x", "p", "1"));
        EntryHandle h2 = handle(new Sub("x", "q", "2"));
        EntryHandle h3 = handle(new Sub("x", "r", "3"));
        ix.insert(h1); ix.insert(h2); ix.insert(h3);

        // Template a="x" (3 candidates) AND b="q" (1 candidate) -> must choose field b.
        EntryFieldIndex.CandidateSet cs = ix.candidates(tmpl(new Sub("x", "q", null)));
        assertFalse(cs.earlyAbort);
        assertEquals(1, cs.chosenField);                 // offset of "b"
        assertEquals(1, cs.candidates.size());
        assertTrue(cs.candidates.contains(h2));
    }

    @Test
    public void earlyAbortsOnEmptyBucket() throws Exception {
        EntryFieldIndex ix = new EntryFieldIndex();
        ix.insert(handle(new Sub("x", "p", "1")));

        // No entry has a="zzz" -> constrained field hashes to an empty bucket.
        EntryFieldIndex.CandidateSet cs = ix.candidates(tmpl(new Sub("zzz", null, null)));
        assertTrue(cs.earlyAbort);
        assertTrue(cs.candidates.isEmpty());
    }

    @Test
    public void wildcardTemplateRequestsFullScan() throws Exception {
        EntryFieldIndex ix = new EntryFieldIndex();
        ix.insert(handle(new Sub("x", "p", "1")));

        // All fields null -> index can't narrow; signal a full scan with null candidates.
        EntryFieldIndex.CandidateSet cs = ix.candidates(tmpl(new Sub(null, null, null)));
        assertFalse(cs.earlyAbort);
        assertNull(cs.candidates);
    }

    @Test
    public void emptyIndexAborts() throws Exception {
        EntryFieldIndex ix = new EntryFieldIndex();
        // Nothing inserted: no entry can match anything, even a wildcard.
        assertTrue(ix.candidates(tmpl(new Sub(null, null, null))).earlyAbort);
    }

    @Test
    public void supertypeTemplateIndexesOnSharedOffsets() throws Exception {
        EntryFieldIndex ix = new EntryFieldIndex();
        EntryHandle h1 = handle(new Sub("x", "p", "1"));
        EntryHandle h2 = handle(new Sub("y", "p", "2"));
        ix.insert(h1); ix.insert(h2);

        // A *Base* template has only fields a,b (offsets 0,1) -- same offsets as in Sub.
        EntryFieldIndex.CandidateSet cs = ix.candidates(tmpl(new Base("y", null)));
        assertFalse(cs.earlyAbort);
        assertEquals(0, cs.chosenField);                 // offset of "a"
        assertEquals(1, cs.candidates.size());
        assertTrue(cs.candidates.contains(h2));
    }

    @Test
    public void nullStoredFieldOnlyMatchesNullHashConstraint() throws Exception {
        EntryFieldIndex ix = new EntryFieldIndex();
        EntryHandle withNull = handle(new Sub(null, "p", "1"));   // field a was null at write time
        EntryHandle withVal  = handle(new Sub("x",  "p", "2"));
        ix.insert(withNull); ix.insert(withVal);

        // Constraining a to a non-null value must exclude the null-field entry.
        EntryFieldIndex.CandidateSet cs = ix.candidates(tmpl(new Sub("x", null, null)));
        assertEquals(1, cs.candidates.size());
        assertTrue(cs.candidates.contains(withVal));
        assertFalse(cs.candidates.contains(withNull));
    }

    @Test
    public void removalPrunesCandidate() throws Exception {
        EntryFieldIndex ix = new EntryFieldIndex();
        EntryHandle h1 = handle(new Sub("x", "q", "1"));
        ix.insert(h1);
        ix.remove(h1);

        // After removal b="q" yields an empty bucket -> early-abort.
        assertTrue(ix.candidates(tmpl(new Sub(null, "q", null))).earlyAbort);
    }

    @Test
    public void doubleInsertIsIdempotent() throws Exception {
        EntryFieldIndex ix = new EntryFieldIndex();
        EntryHandle h1 = handle(new Sub("x", "q", "1"));
        ix.insert(h1);
        ix.insert(h1);                                   // second insert must not duplicate
        Set<EntryHandle> c = ix.candidates(tmpl(new Sub(null, "q", null))).candidates;
        assertEquals(1, c.size());
        assertSame(h1, c.iterator().next());
    }
}
