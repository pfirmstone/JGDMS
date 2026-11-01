/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.tool;

import au.net.zeus.jgdms.api.policy.PermissionSets;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * That the tool reads the shared META, GATE, HOLD and FLAG sets of
 * {@link PermissionSets} (defined and tested in jgdms-platform) and does not keep its own.
 */
public class PermissionSetsTest {

    @Test
    public void testTheGeneratorReadsTheSharedSets() {
        for (String c : PermissionSets.META) {
            assertEquals(PermissionSets.META_REASON, ProxyPolicyGenerator.excludedReason(c));
        }
        for (String c : PermissionSets.GATE) {
            assertEquals(PermissionSets.GATE_REASON, ProxyPolicyGenerator.excludedReason(c));
        }
        assertTrue(PermissionSets.META.contains("org.apache.river.api.security.PolicyPermission"));
        assertEquals(PermissionSets.META_REASON,
            ProxyPolicyGenerator.excludedReason("org.apache.river.api.security.PolicyPermission"));
        assertNull(ProxyPolicyGenerator.excludedReason("java.lang.RuntimePermission"));
    }

    /** Operator ruling 2026-11-01: a proxy must not hold AssertPrincipalPermission. */
    @Test
    public void testAssertPrincipalPermissionIsHeld() {
        String ap = "net.jini.security.AssertPrincipalPermission";
        assertNotNull(PermissionSets.holdReason(ap, "javax.security.auth.x500.X500Principal", "CN=gateway"));
        assertNotNull(PermissionSets.holdReason(ap, "*", "*"));
        assertNotNull(PermissionSets.holdReason(
            "(" + ap + " \"javax.security.auth.x500.X500Principal\" \"CN=gateway\")"));
    }
}
