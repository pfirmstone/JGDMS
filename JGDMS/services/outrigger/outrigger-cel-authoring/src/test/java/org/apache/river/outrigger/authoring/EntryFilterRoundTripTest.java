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
package org.apache.river.outrigger.authoring;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import net.jini.core.entry.Entry;

import org.apache.river.outrigger.CompiledFilter;
import org.apache.river.outrigger.FilterAdmission;
import org.apache.river.outrigger.proxy.EntryRep;
import net.jini.space.FilterRejectedException;

import au.net.zeus.jgdms.cel.authoring.CelParseException;

/**
 * End-to-end authoring round-trip (SOW Part B, unit B1): a CEL text rule
 * compiled by {@link EntryFilter} against an Entry class is accepted by the
 * server-side {@link FilterAdmission} seam when well-typed, and rejected loudly
 * when mistyped — proving the authoring schema and the server's independently
 * re-derived schema agree, while the server still verifies from scratch (the
 * envelope carries no client name table).
 */
public class EntryFilterRoundTripTest {

    public static class Doc implements Entry {
        public String a;
        public String b;
        public Doc() {}
        public Doc(String a, String b) { this.a = a; this.b = b; }
    }

    @Test
    public void wellTypedRuleCompilesAndIsAdmitted() throws Exception {
        byte[] env = EntryFilter.compile("a == \"x\"", Doc.class);
        assertNotNull(env);
        CompiledFilter cf = FilterAdmission.admit(env, new EntryRep(new Doc("x", "y")));
        assertNotNull(cf);
        assertNotNull(cf.expr());
        assertTrue(!cf.isSchemaLess());
    }

    @Test
    public void mistypedRuleIsRejectedLoudly() throws Exception {
        // a > 5 : STRING compared to INT. Rejected either at authoring
        // (CelParseException) or, definitively, at server admission
        // (FILTER_TYPE_MISMATCH). Never silently accepted.
        byte[] env;
        try {
            env = EntryFilter.compile("a > 5", Doc.class);
        } catch (CelParseException authorSideRejection) {
            return; // rejected at authoring — a valid loud outcome
        }
        try {
            FilterAdmission.admit(env, new EntryRep(new Doc("x", "y")));
            fail("mistyped rule was neither rejected at authoring nor at admission");
        } catch (FilterRejectedException e) {
            assertTrue(e.reason() == FilterRejectedException.Reason.FILTER_TYPE_MISMATCH
                    || e.reason() == FilterRejectedException.Reason.FILTER_RESULT_TYPE_MISMATCH,
                    "expected a type/result-type rejection, got " + e.reason());
        }
    }

    @Test
    public void compiledEnvelopeIsAnOpaqueCanonicalFilterEnvelope() throws Exception {
        byte[] env = EntryFilter.compile("a == \"x\" && b == \"y\"", Doc.class);
        // The envelope decodes as a canonical FilterEnvelope carrying opaque CEL.
        org.apache.river.outrigger.proxy.FilterEnvelope decoded =
                org.apache.river.outrigger.proxy.FilterEnvelope.decode(env);
        assertTrue(decoded.version() == org.apache.river.outrigger.proxy.FilterEnvelope.VERSION);
        assertTrue(decoded.celWire().length > 0);
    }
}
