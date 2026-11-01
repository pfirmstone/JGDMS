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

    // The dangerous-policy catalogue extends HOLD and FLAG; each row below names its severity.
    @Test
    public void testDangerSeverities() {
        String f = "java.io.FilePermission";
        String r = "java.lang.RuntimePermission";
        String s = "java.security.SecurityPermission";
        assertEquals(PermissionSets.HIGH, PermissionSets.danger("java.security.AllPermission", null, null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "<<ALL FILES>>", "write").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "<<ALL FILES>>", "read,delete").severity);
        assertEquals(PermissionSets.MEDIUM, PermissionSets.danger(f, "<<ALL FILES>>", "read").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "/-", "write").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "C:\\-", "write").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "-", "execute").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "*", "write").severity);
        assertEquals(PermissionSets.MEDIUM, PermissionSets.danger(f, "/-", "read").severity);
        assertNull(PermissionSets.danger(f, "/opt/app/-", "write"));
        assertNull(PermissionSets.danger(f, "/", "write"));
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(s, "*", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(s, "insertProvider.BC", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(s, "removeProvider.SUN", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(s, "putProviderProperty.X", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(r, "setSecurityManager", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(r, "defineClassInPackage.p", null).severity);
        assertEquals(PermissionSets.MEDIUM, PermissionSets.danger(r, "getProtectionDomain", null).severity);
        assertEquals(PermissionSets.MEDIUM, PermissionSets.danger(r, "exitVM.1", null).severity);
        assertNull(PermissionSets.danger(r, "shutdownHooks", null));
        assertNull(PermissionSets.danger(r, "getenv.PATH", null));
        assertEquals(PermissionSets.MEDIUM,
            PermissionSets.danger("java.util.PropertyPermission", "*", "read,write").severity);
        assertNull(PermissionSets.danger("java.util.PropertyPermission", "*", "read"));
        assertNull(PermissionSets.danger("java.util.PropertyPermission", "user.dir", "write"));
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(
            "org.apache.river.api.io.DeSerializationPermission", "*", null).severity);
        assertNull(PermissionSets.danger(
            "org.apache.river.api.io.DeSerializationPermission", "ATOMIC", null));
        String g = "net.jini.security.GrantPermission";
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(g, "*", null).severity);
        assertEquals(PermissionSets.HIGH,
            PermissionSets.danger(g, "java.security.SecurityPermission \"setPolicy\"", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(g,
            "java.io.FilePermission \"/tmp/x\" \"read\"; java.lang.RuntimePermission \"createClassLoader\"",
            null).severity);
        assertNull(PermissionSets.danger(g, "java.io.FilePermission \"/tmp/x\" \"read\"", null));
        assertNull(PermissionSets.danger("com.example.Mine", "setSecurityManager", null));
    }

    @Test
    public void testProbesAreBuiltAndClassifiedByTheCatalogue() {
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (PermissionSets.Probe p : PermissionSets.probes()) {
            assertNotNull(p.id, p.permission);
            assertTrue(p.id, p.severity == PermissionSets.HIGH || p.severity == PermissionSets.MEDIUM);
            assertTrue("duplicate probe " + p.id, ids.add(p.id));
        }
        assertTrue(ids.toString(), ids.contains("AllPermission"));
        assertTrue(ids.toString(), ids.contains("RuntimePermission setSecurityManager"));
        assertTrue(ids.toString(), ids.contains("FilePermission <<ALL FILES>> write"));
        assertNotNull(PermissionSets.probe("java.security.SecurityPermission", "insertProvider.BC", null));
        assertNull(PermissionSets.probe("java.security.SecurityPermission", "getProperty.x", null));
        assertNull(PermissionSets.probe("java.security.AllPermission", null, null));
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
