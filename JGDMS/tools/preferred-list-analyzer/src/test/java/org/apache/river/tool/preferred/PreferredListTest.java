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

public class PreferredListTest {

    private static ClassDecision dec(String name, Decision d) {
        return new ClassDecision(name, d, Collections.<Hazard>emptyList(),
                d == Decision.CONFLICT, Collections.<String>emptyList(), false);
    }

    @Test
    public void rendersDefaultFalseWithOnlyPreferredEntries() {
        List<ClassDecision> ds = new ArrayList<ClassDecision>();
        ds.add(dec("net/jini/id/UuidFactory", Decision.PREFER));
        ds.add(dec("net/jini/core/Foo", Decision.SHARE));
        ds.add(dec("net/jini/core/constraint/DelegationAbsoluteTime", Decision.CONFLICT));
        String out = PreferredList.render(ds);
        assertTrue(out.contains("Preferred: false"));
        assertTrue(out.contains("Name: net/jini/id/UuidFactory.class"));
        // SHARE and CONFLICT (both false == default) are omitted
        assertFalse(out.contains("net/jini/core/Foo.class"));
        assertFalse(out.contains("DelegationAbsoluteTime.class"));
    }

    @Test
    public void roundTripRenderThenParseResolves() throws Exception {
        List<ClassDecision> ds = new ArrayList<ClassDecision>();
        ds.add(dec("net/jini/id/UuidFactory", Decision.PREFER));
        ds.add(dec("net/jini/core/Foo", Decision.SHARE));
        PreferredList pl = PreferredList.parse(PreferredList.render(ds));
        assertFalse(pl.getDefaultPreferred());
        assertTrue(pl.resolve("net/jini/id/UuidFactory.class"));
        assertFalse(pl.resolve("net/jini/core/Foo.class"));
    }

    @Test
    public void wildcardResolutionMostSpecificWins() throws Exception {
        String list =
                "PreferredResources-Version: 1.0\n\n"
              + "Preferred: false\n\n"
              + "Name: net/jini/jeri/ssl/-\nPreferred: true\n\n"   // recursive
              + "Name: net/jini/jeri/ssl/Special.class\nPreferred: false\n"; // exact overrides
        PreferredList pl = PreferredList.parse(list);
        assertTrue(pl.resolve("net/jini/jeri/ssl/HttpsEndpoint.class"));      // via -
        assertTrue(pl.resolve("net/jini/jeri/ssl/sub/Deep.class"));           // via - recursive
        assertFalse(pl.resolve("net/jini/jeri/ssl/Special.class"));           // exact wins
        assertFalse(pl.resolve("net/jini/other/X.class"));                    // default
    }

    @Test
    public void oneLevelWildcardDoesNotMatchSubpackages() throws Exception {
        String list =
                "Preferred: false\n\n"
              + "Name: net/jini/foo/*\nPreferred: true\n";
        PreferredList pl = PreferredList.parse(list);
        assertTrue(pl.resolve("net/jini/foo/A.class"));
        assertFalse(pl.resolve("net/jini/foo/bar/B.class"));   // subpackage not matched by *
    }

    @Test
    public void diffDetectsDriftAgainstStaleList() throws Exception {
        // Stale checked-in list: everything preferred true by default.
        String stale = "Preferred: true\n";
        PreferredList pl = PreferredList.parse(stale);

        List<ClassDecision> computed = new ArrayList<ClassDecision>();
        computed.add(dec("net/jini/id/UuidFactory", Decision.PREFER));  // computed true == stale true: ok
        computed.add(dec("net/jini/core/Foo", Decision.SHARE));         // computed false != stale true: drift

        List<PreferredList.Drift> drift = pl.diff(computed);
        assertEquals(1, drift.size());
        assertEquals("net/jini/core/Foo.class", drift.get(0).getResourceName());
        assertTrue(drift.get(0).getCheckedIn());
        assertFalse(drift.get(0).getComputed());
    }

    @Test
    public void noDriftWhenListMatches() throws Exception {
        List<ClassDecision> computed = new ArrayList<ClassDecision>();
        computed.add(dec("net/jini/id/UuidFactory", Decision.PREFER));
        computed.add(dec("net/jini/core/Foo", Decision.SHARE));
        PreferredList pl = PreferredList.parse(PreferredList.render(computed));
        assertTrue(pl.diff(computed).isEmpty());
    }
}
