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

import java.lang.reflect.Field;
import java.util.Set;
import au.net.zeus.jgdms.api.policy.VerdictPermissionMapper;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The one definition of the META, GATE, HOLD and FLAG sets: what is in each, that the platform's
 * private copy of META cannot drift ahead of it, and that the three users read the one set.
 */
public class PermissionSetsTest {

    /**
     * The platform's {@code VerdictPermissionMapper} keeps a private META set (that module is not
     * this one to change); every name it holds must be in this tool's set, or the ceiling the
     * platform computes would exclude a class the promotion tooling lets through.
     */
    @Test
    public void testThePlatformsMetaSetIsContainedInTheToolsOne() throws Exception {
        Field f = VerdictPermissionMapper.class.getDeclaredField("META_PERMISSION_CLASSES");
        f.setAccessible(true);
        Set<?> platform = (Set<?>) f.get(null);
        assertFalse(platform.isEmpty());
        assertTrue(platform + " is not contained in " + PermissionSets.META,
            PermissionSets.META.containsAll(platform));
    }

    @Test
    public void testTheGeneratorAndPromotionReadTheSharedSets() {
        for (String c : PermissionSets.META) {
            assertEquals(PermissionSets.META_REASON, ProxyPolicyGenerator.excludedReason(c));
        }
        for (String c : PermissionSets.GATE) {
            assertEquals(PermissionSets.GATE_REASON, ProxyPolicyGenerator.excludedReason(c));
        }
        assertTrue(PermissionSets.META.contains("org.apache.river.api.security.PolicyPermission"));
        assertTrue(PermissionSets.GATE.contains("net.jini.loader.pref.INCONCLUSIVEPermit"));
        assertTrue(PermissionSets.GATE.contains("net.jini.loader.pref.BootstrapPermission"));
        assertNull(ProxyPolicyGenerator.excludedReason("java.lang.RuntimePermission"));
    }

    @Test
    public void testHoldIsByClassAndName() {
        String r = "java.lang.RuntimePermission";
        assertNotNull(PermissionSets.holdReason(r, "setSecurityManager", null));
        assertNotNull(PermissionSets.holdReason(r, "createClassLoader", null));
        assertNotNull(PermissionSets.holdReason(r, "*", null));
        assertNotNull(PermissionSets.holdReason(r, "loadLibrary.*", null));
        assertNotNull(PermissionSets.holdReason(r, "loadLibrary.zip", null));
        assertNull(PermissionSets.holdReason(r, "getClassLoader", null));
        assertNull(PermissionSets.holdReason(r, "setIO", null));
        // the same name under another class is not that permission
        assertNull(PermissionSets.holdReason("java.security.SecurityPermission",
            "setSecurityManager", null));
        assertNull(PermissionSets.holdReason("com.example.Mine", "suppressAccessChecks", null));
        String f = "java.io.FilePermission";
        assertNotNull(PermissionSets.holdReason(f, "<<ALL FILES>>", "read, Execute"));
        assertNull(PermissionSets.holdReason(f, "<<ALL FILES>>", "read,write"));
        assertNull(PermissionSets.holdReason(f, "/tmp/x", "execute"));
        assertNotNull(PermissionSets.holdReason(
            "au.zeus.jdk.authorization.guards.NativeInvocationPermission", null, null));
        assertNull(PermissionSets.holdReason(null, "x", "y"));
    }

    @Test
    public void testFlagIsReportedForTheBroadButCommonEntries() {
        assertNotNull(PermissionSets.flagReason("java.io.FilePermission", "<<ALL FILES>>", "read"));
        assertNotNull(PermissionSets.flagReason("javax.security.auth.AuthPermission", "getSubject", null));
        assertNotNull(PermissionSets.flagReason("javax.security.auth.AuthPermission", "doAs", null));
        assertNotNull(PermissionSets.flagReason("java.net.SocketPermission", "*", "connect"));
        assertNotNull(PermissionSets.flagReason("java.lang.RuntimePermission",
            "accessClassInPackage.sun.misc", null));
        assertNull(PermissionSets.flagReason("java.net.SocketPermission", "host:80", "connect"));
        assertNull(PermissionSets.flagReason("java.lang.RuntimePermission", "getClassLoader", null));
        assertNull(PermissionSets.flagReason("java.io.FilePermission", "data/x", "read"));
    }

    @Test
    public void testAnEntryLineIsReadAsTheLoaderReadsIt() {
        assertNotNull(PermissionSets.holdReason(
            "( java.lang.RuntimePermission \"setSecurityManager\" )"));
        assertNull(PermissionSets.holdReason("(java.lang.RuntimePermission \"setSecurityManager\""));
        assertNotNull(PermissionSets.flagReason(
            "(java.io.FilePermission \"<<ALL FILES>>\" \"read\")"));
    }
}
