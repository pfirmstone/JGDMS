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
package au.net.zeus.jgdms.api.policy;

import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import java.security.AllPermission;
import java.security.Permission;
import java.security.UnresolvedPermission;
import net.jini.security.GrantPermission;
import org.apache.river.api.net.Uri;
import org.apache.river.api.security.PolicyPermission;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The one definition of the META, GATE, HOLD and FLAG sets, and that the verdict mapper
 * reads the same META set rather than a copy of it.
 */
public class PermissionSetsTest {

    private static final String HASH =
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";

    private static Permission[] ceilingOf(Permission... declared) throws Exception {
        RegistryVerdict v = new RegistryVerdict(
                new Uri[]{ new Uri("http://example.com/foo-dl.jar") },
                VerdictType.SAFE, 1L, new byte[]{ 1 });
        return VerdictPermissionMapper.computeSubProcessCeiling(v, HASH, declared,
                new GrantPermission(new AllPermission()), false);
    }

    /** The mapper's set is the shared one, not a copy that can drift. */
    @Test
    public void testTheMapperUsesTheSharedMetaSet() {
        assertSame(PermissionSets.META, VerdictPermissionMapper.META_PERMISSION_CLASSES);
    }

    /**
     * Every class in the shared META set is excluded by the mapper, resolved or not, even
     * under a caller that may grant anything; this is the behaviour, not just the field.
     */
    @Test
    public void testTheMapperExcludesEveryClassOfTheSharedMetaSet() throws Exception {
        assertFalse(PermissionSets.META.isEmpty());
        for (String type : PermissionSets.META) {
            Permission unresolved = new UnresolvedPermission(type, null, null, null);
            Permission[] ceiling = ceilingOf(unresolved);
            assertEquals(type + " reached the ceiling", 0, ceiling.length);
        }
    }

    /** A resolved PolicyPermission, which the mapper's old private copy let through. */
    @Test
    public void testThePolicyPermissionIsNowExcludedByTheMapper() throws Exception {
        assertTrue(PermissionSets.META.contains("org.apache.river.api.security.PolicyPermission"));
        assertEquals(0, ceilingOf(new PolicyPermission("*")).length);
    }

    @Test
    public void testTheSetsHoldTheNamesTheTreeDefines() {
        assertEquals(PolicyPermission.class.getName(), PermissionSets.POLICY_PERMISSION);
        assertEquals(GrantPermission.class.getName(), PermissionSets.GRANT_PERMISSION);
        assertTrue(PermissionSets.GATE.contains("net.jini.loader.pref.INCONCLUSIVEPermit"));
        assertTrue(PermissionSets.GATE.contains("net.jini.loader.pref.BootstrapPermission"));
        assertNull(PermissionSets.excludedReason("java.lang.RuntimePermission"));
        assertEquals(PermissionSets.META_REASON,
            PermissionSets.excludedReason("java.security.AllPermission"));
        assertEquals(PermissionSets.GATE_REASON,
            PermissionSets.excludedReason("java.net.URLPermission"));
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
