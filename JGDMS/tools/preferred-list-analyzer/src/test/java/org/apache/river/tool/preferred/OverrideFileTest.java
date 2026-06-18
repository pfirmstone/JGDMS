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
package org.apache.river.tool.preferred;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class OverrideFileTest {

    private static ClassDecision share(String name) {
        return new ClassDecision(name, Decision.SHARE,
                Collections.<Hazard>emptyList(), false,
                Collections.<String>emptyList(), false);
    }

    @Test
    public void parsesValuesReasonsAndDotOrSlash() throws Exception {
        OverrideFile o = OverrideFile.parse(
                "# comment\n"
                + "net.jini.Foo = true   # isolate me\n"
                + "net.jini.Bar.class = false\n"
                + "\n");
        assertEquals(2, o.entries().size());
        OverrideFile.Entry e = o.entries().get(0);
        assertEquals("net/jini/Foo", e.getInternalName());
        assertTrue(e.isPreferred());
        assertEquals("isolate me", e.getReason());
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsMissingEquals() throws Exception {
        OverrideFile.parse("net.jini.Foo true\n");
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsBadValue() throws Exception {
        OverrideFile.parse("net.jini.Foo = maybe\n");
    }

    @Test
    public void appliesOverrideAndReportsDivergence() throws Exception {
        List<ClassDecision> ds = new ArrayList<ClassDecision>();
        ds.add(share("net/jini/Foo"));   // analysis: share(false)
        OverrideFile o = OverrideFile.parse("net.jini.Foo = true  # deliberate\n");
        OverrideFile.Result r = o.apply(ds);

        assertEquals(1, r.getApplied().size());
        assertEquals(1, r.getDivergent().size());
        assertTrue(r.getUnmatched().isEmpty());
        ClassDecision d = ds.get(0);
        assertTrue(d.isOverridden());
        assertTrue(d.isPreferred());                 // final value flipped to true
        assertFalse(d.getAnalysisDecision().isPreferred()); // analysis still says false
        assertTrue(d.isOverrideDivergent());
    }

    @Test
    public void reportsUnmatchedStaleOverride() throws Exception {
        List<ClassDecision> ds = new ArrayList<ClassDecision>();
        ds.add(share("net/jini/Foo"));
        OverrideFile o = OverrideFile.parse("net.jini.Gone = true\n");
        OverrideFile.Result r = o.apply(ds);
        assertTrue(r.getApplied().isEmpty());
        assertEquals(1, r.getUnmatched().size());
        assertEquals("net.jini.Gone", r.getUnmatched().get(0));
    }

    @Test
    public void nonDivergentOverrideIsAppliedButNotDivergent() throws Exception {
        List<ClassDecision> ds = new ArrayList<ClassDecision>();
        ds.add(share("net/jini/Foo"));
        OverrideFile o = OverrideFile.parse("net.jini.Foo = false\n"); // agrees with analysis
        OverrideFile.Result r = o.apply(ds);
        assertEquals(1, r.getApplied().size());
        assertTrue(r.getDivergent().isEmpty());
    }
}
