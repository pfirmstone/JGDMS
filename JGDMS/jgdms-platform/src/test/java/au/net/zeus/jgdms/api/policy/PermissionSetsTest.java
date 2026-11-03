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
        // Operator ruling 2026-11-01: a proxy must not hold AssertPrincipalPermission, any class/name
        String ap = "net.jini.security.AssertPrincipalPermission";
        assertNotNull(PermissionSets.holdReason(ap, "javax.security.auth.x500.X500Principal", "CN=gateway"));
        assertNotNull(PermissionSets.holdReason(ap, "*", "*"));
        assertNotNull(PermissionSets.holdReason(ap, null, null));
        assertNotNull(PermissionSets.holdReason(
            "(" + ap + " \"javax.security.auth.x500.X500Principal\" \"CN=gateway\")"));
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
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "<<ALL FILES>>", "read").severity);
        assertEquals(PermissionSets.MEDIUM, PermissionSets.danger(f, "<<ALL FILES>>", "readlink").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "/-", "write").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "C:\\-", "write").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "-", "execute").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "*", "write").severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "/-", "read").severity);
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
        assertEquals(PermissionSets.MEDIUM, PermissionSets.danger(r, "exitVM", null).severity);
        assertEquals(PermissionSets.MEDIUM, PermissionSets.danger(r, "shutdownHooks", null).severity);
        assertNull(PermissionSets.danger(r, "getenv.PATH", null));
        assertEquals(PermissionSets.HIGH,
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

    private static int sev(String type, String name, String actions) {
        PermissionSets.Danger d = PermissionSets.danger(type, name, actions);
        return d == null ? 0 : d.severity;
    }

    // Operator decision 2026-11-02: what bears on identity, and reading a whole root or the whole
    // home tree, is HIGH; the rest of the broad-but-common set stays MEDIUM.
    @Test
    public void testIdentityAndBroadReadRatings() {
        int h = PermissionSets.HIGH;
        int m = PermissionSets.MEDIUM;
        String au = "javax.security.auth.AuthPermission";
        assertEquals(h, sev(au, "doAs", null));
        assertEquals(h, sev(au, "setLoginConfiguration", null));
        assertEquals(m, sev(au, "modifyPublicCredentials", null));
        assertEquals(m, sev(au, "getSubject", null));
        assertEquals(m, sev(au, "getLoginConfiguration", null));
        String f = "java.io.FilePermission";
        assertEquals(h, sev(f, "C:\\-", "read"));
        assertEquals(h, sev(f, "d:/-", "read"));
        assertEquals(h, sev(f, "/-", "read"));
        assertEquals(h, sev(f, "${user.home}${/}-", "read"));
        // kept MEDIUM: readlink alone on all files, a root's own files, the temp directory's own files
        // (operator ruling 2026-11-03: <<ALL FILES>> with read is an unscoped whole-tree read, HIGH)
        assertEquals(m, sev(f, "<<ALL FILES>>", "readlink"));
        assertEquals(h, sev(f, "<<ALL FILES>>", "read"));
        assertEquals(m, sev(f, "C:\\*", "read"));
        // clean: a pinned exact read, a directory below the home
        assertEquals(0, sev(f, "/opt/app/conf/-", "read"));
        assertEquals(0, sev(f, "/etc/hosts", "read"));
        assertEquals(0, sev(f, "${user.home}${/}.myapp${/}-", "read"));
        String r = "java.lang.RuntimePermission";
        for (String n : new String[]{"exitVM.*", "exitVM.0", "setIO"}) assertEquals(n, m, sev(r, n, null));
        String s = "java.security.SecurityPermission";
        assertEquals(m, sev(s, "authProvider.*", null));
        assertEquals(m, sev(s, "getProperty.*", null));
        assertEquals(0, sev(s, "getProperty.java.version", null));
        assertTrue(PermissionSets.danger(au, "doAs", null).reason.contains("doAs"));
        String so = "java.net.SocketPermission";
        assertEquals(m, sev(so, "*.com", "connect,resolve"));
        assertEquals(m, sev(so, "*.com:443", "connect"));
        assertEquals(m, sev(so, "*", "resolve"));
        assertEquals(0, sev(so, "*.example.com", "connect"));
        assertEquals(0, sev(so, "host.example.com:443", "connect"));
    }

    // The catalogue gaps of the Opus review of 66974f70f: each entry that read as clean is now
    // HIGH or MEDIUM.  A catalogue that dropped one fails its row.
    @Test
    public void testCatalogueGaps() {
        int h = PermissionSets.HIGH;
        int m = PermissionSets.MEDIUM;
        String a = "net.jini.security.AssertPrincipalPermission";
        assertEquals(h, sev(a, "*", "*"));
        assertEquals(h, sev(a, "javax.security.auth.x500.X500Principal", "CN=*"));
        assertEquals(0, sev(a, "javax.security.auth.x500.X500Principal", "CN=gateway,O=Example"));
        String pc = "javax.security.auth.PrivateCredentialPermission";
        assertEquals(h, sev(pc, "* * \"*\"", "read"));
        assertEquals(h, sev(pc, "javax.security.auth.kerberos.KerberosTicket * \"*\"", "read"));
        assertEquals(0, sev(pc, "a.B c.D \"x\"", "read"));
        assertEquals(h, sev("java.net.NetPermission", "*", null));
        assertEquals(h, sev("java.net.NetPermission", "specifyStreamHandler", null));
        assertEquals(m, sev("java.net.NetPermission", "setDefaultAuthenticator", null));
        assertEquals(h, sev("javax.net.ssl.SSLPermission", "*", null));
        assertEquals(h, sev("javax.net.ssl.SSLPermission", "setDefaultSSLContext", null));
        String r = "java.lang.RuntimePermission";
        assertEquals(h, sev(r, "accessClassInPackage.jdk.internal.misc", null));
        assertEquals(h, sev(r, "accessClassInPackage.jdk.internal.*", null));
        assertEquals(m, sev(r, "accessClassInPackage.sun.misc", null));
        for (String n : new String[]{"setIO", "getClassLoader", "modifyThread", "modifyThreadGroup",
            "writeFileDescriptor", "readFileDescriptor", "manageProcess",
            "enableContextClassLoaderOverride", "charsetProvider"}) {
            assertEquals(n, m, sev(r, n, null));
        }
        assertEquals(h, sev("java.security.SecurityPermission", "clearProviderProperties.SUN", null));
        assertEquals(m, sev("java.security.SecurityPermission", "getPolicy", null));
        assertEquals(m, sev("java.security.SecurityPermission", "createPolicy.JavaPolicy", null));
        String pp = "java.util.PropertyPermission";
        assertEquals(h, sev(pp, "java.security.policy", "write"));
        assertEquals(h, sev(pp, "java.security.manager", "write"));
        assertEquals(h, sev(pp, "java.security.*", "write"));
        assertEquals(h, sev(pp, "*", "read,write"));
        assertEquals(0, sev(pp, "java.security.policy", "read"));
        assertEquals(h, sev("javax.management.MBeanPermission", "*", "invoke"));
        assertEquals(m, sev("javax.management.MBeanPermission", "*", "getAttribute"));
        assertEquals(0, sev("javax.management.MBeanPermission", "app.Bean#x[app:type=Bean]", "invoke"));
        assertEquals(h, sev("javax.management.MBeanServerPermission", "*", null));
        assertEquals(h, sev("javax.management.MBeanTrustPermission", "register", null));
        assertEquals(h, sev("java.lang.management.ManagementPermission", "control", null));
        assertEquals(m, sev("java.lang.management.ManagementPermission", "monitor", null));
        assertEquals(h, sev("com.sun.tools.attach.AttachPermission", "attachVirtualMachine", null));
        assertEquals(m, sev("java.util.logging.LoggingPermission", "control", null));
        assertEquals(m, sev("net.jini.loader.DownloadPermission", "x", null));
        // DirtyChai's guards, by name: they are not on this classpath
        String lc = "au.zeus.jdk.authorization.guards.LoadClassPermission";
        assertEquals(h, sev(lc, "*", null));
        assertEquals(m, sev(lc, "ALLOW", null));
        assertEquals(h, sev("net.jini.loader.LoadClassPermission", "*", null));
        assertEquals(0, sev("net.jini.loader.LoadClassPermission", "x.y.Z", null));
        String so = "au.zeus.jdk.authorization.guards.SerialObjectPermission";
        assertEquals(h, sev(so, "*", null));
        assertEquals(0, sev(so, "net.jini.io.MarshalledInstance", null));
        assertEquals(h, sev("au.zeus.jdk.authorization.guards.NativeInvocationPermission", "x", null));
        assertTrue(PermissionSets.knownByName(so));
        assertFalse(PermissionSets.knownByName("com.example.Mine"));
    }

    // Item 3: file names are expanded and normalised before they are judged.
    @Test
    public void testFileTargetsAreExpandedAndNormalised() {
        String f = "java.io.FilePermission";
        java.util.Properties env = new java.util.Properties();
        env.setProperty("app.home", "/opt/app");
        env.setProperty("user.home", "/home/alice");
        env.setProperty("java.home", "/usr/lib/jvm/jdk");
        env.setProperty("java.io.tmpdir", "/var/tmp-x");
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "${user.home}/-", "write", env).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "${user.home}${/}*", "write", env).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "/home/-", "delete", env).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "${java.home}/-", "write", env).severity);
        assertEquals(PermissionSets.HIGH,
            PermissionSets.danger(f, "${java.home}/conf/security/java.security", "write", env).severity);
        assertNull(PermissionSets.danger(f, "${java.home}/conf/security/java.security", "read", env));
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "/opt/../-", "write", env).severity);
        assertEquals(PermissionSets.HIGH,
            PermissionSets.danger(f, "/home/alice/work/../../-", "write", env).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "${io.not.defined}/x/-", "write", env).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "${user.home}/-", "read", env).severity);
        // a temp directory's own files are medium, its whole tree is high
        assertEquals(PermissionSets.MEDIUM, PermissionSets.danger(f, "/tmp/*", "write", env).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "/tmp/-", "write", env).severity);
        // clean: an application's own directory, a subdirectory of the home, a read of the JDK home
        assertNull(PermissionSets.danger(f, "${app.home}/work/-", "write", env));
        assertNull(PermissionSets.danger(f, "${user.home}/.myapp/-", "write", env));
        assertNull(PermissionSets.danger(f, "${java.home}/lib/-", "read", env));
        assertNull(PermissionSets.danger(f, "/opt/app/-", "read,write", env));
        // the working directory only when the name says ${user.dir}
        env.setProperty("user.dir", "/srv/svc");
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(f, "${user.dir}/-", "execute", env).severity);
        assertNull(PermissionSets.danger(f, "/srv/svc/-", "execute", env));
    }

    // Item 4: a GrantPermission target is read by GrantPermission's own parser, so a custom
    // delimiter does not hide an entry.
    @Test
    public void testGrantPermissionDelimiterForms() {
        String g = "net.jini.security.GrantPermission";
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(g,
            "delim=| java.lang.RuntimePermission |createClassLoader|", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(g,
            "net.jini.security.AssertPrincipalPermission \"*\", \"*\"", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(g,
            "delim=' java.net.SocketPermission 'localhost:1024-', 'accept'; java.io.FilePermission '/-', 'write'", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(g, "java.io.*", null).severity);
        assertEquals(PermissionSets.HIGH, PermissionSets.danger(g, "javax.security.auth.*", null).severity);
        assertNull(PermissionSets.danger(g,
            "delim=| java.net.SocketPermission |localhost:1024-|, |accept,listen|", null));
    }

    // Opus review round 10, finding 1: classes the catalogue missed.  A class that lets code define
    // classes, use off-heap memory, act as another subject, record the JVM or debug it is HIGH
    // whatever its name; links, socket options and the JDBC global settings are MEDIUM.
    @Test
    public void testClassesTheCatalogueMissed() {
        String dc = "au.zeus.jdk.authorization.guards.";
        for (String[] c : new String[][]{{dc + "DefineClassPermission", null}, {dc + "DefineClassPermission", "ALLOW"},
            {dc + "DefineClassPermission", "*"}, {dc + "NativeMemoryPermission", "Arena"},
            {"javax.management.remote.SubjectDelegationPermission", "*"},
            {"jdk.jfr.FlightRecorderPermission", "accessFlightRecorder"},
            {"com.sun.jdi.JDIPermission", "virtualMachineManager"}}) {
            PermissionSets.Danger d = PermissionSets.danger(c[0], c[1], null);
            assertNotNull(c[0] + " " + c[1], d);
            assertEquals(c[0] + " " + c[1], PermissionSets.HIGH, d.severity);
            assertTrue(c[0], PermissionSets.catalogued(c[0]));
        }
        for (String[] c : new String[][]{{"java.nio.file.LinkPermission", "symbolic"},
            {"java.lang.LinkPermission", "hard"}, {"jdk.net.NetworkPermission", "setOption.TCP_KEEPIDLE"},
            {"java.net.NetworkPermission", "setOption.x"}, {"java.sql.SQLPermission", "setLog"},
            {"java.sql.SQLPermission", "setSyncFactory"}, {"java.sql.SQLPermission", "setNetworkTimeout"},
            {"java.sql.SQLPermission", "*"}}) {
            PermissionSets.Danger d = PermissionSets.danger(c[0], c[1], null);
            assertNotNull(c[0] + " " + c[1], d);
            assertEquals(c[0] + " " + c[1], PermissionSets.MEDIUM, d.severity);
            assertTrue(c[0], PermissionSets.catalogued(c[0]));
        }
        // a SQLPermission name the catalogue has no reason to rate stays out
        assertNull(PermissionSets.danger("java.sql.SQLPermission", "somethingElse", null));
        // an application permission is not catalogued, and is not dangerous by name
        assertFalse(PermissionSets.catalogued("net.jini.export.ExportPermission"));
        assertNull(PermissionSets.danger("net.jini.export.ExportPermission", "*", null));
        assertFalse(PermissionSets.catalogued(null));
    }

    // Operator ruling 2026-11-03 (Opus review finding 4): a whole tree is HIGH to read, whatever the
    // spelling of the root, the order of the actions, the share or the platform the lint runs on.
    @Test
    public void testWholeTreeReadVariantsAreHigh() {
        int h = PermissionSets.HIGH;
        String f = "java.io.FilePermission";
        for (String n : new String[]{"c:\\-", "C:/-", "C:\\-", "d:/-", "/-", "/tmp/../-"}) {
            for (String a : new String[]{"read", "read,readlink", "readlink,read", "read, write", "readlink",
                "write", "delete", "execute"}) {
                assertEquals(n + " " + a, h, sev(f, n, a));
            }
        }
        for (String n : new String[]{"${user.home}${/}-", "${user.home}/-"}) {
            for (String a : new String[]{"read", "read,readlink", "readlink,read", "readlink", "read, write"}) {
                assertEquals(n + " " + a, h, sev(f, n, a));
            }
        }
        // a whole UNC share or host, written either way, for every action
        for (String n : new String[]{"\\\\server\\share\\-", "\\\\server\\-", "//server/share/-", "//server/-"}) {
            for (String a : new String[]{"read", "write", "delete", "execute", "read,readlink", "readlink,read"}) {
                assertEquals(n + " " + a, h, sev(f, n, a));
            }
        }
        // three or more leading separators are the same UNC prefix on Windows (Opus review 2026-11-03, F2)
        for (String n : new String[]{"///server/share/-", "\\\\\\server\\share\\-", "////server/-"}) {
            for (String a : new String[]{"read", "write"}) {
                assertEquals(n + " " + a, h, sev(f, n, a));
            }
        }
        // a slash before the drive letter is the same drive (F3)
        for (String n : new String[]{"/C:/-", "//c:/-", "\\C:\\-"}) {
            for (String a : new String[]{"read", "write"}) {
                assertEquals(n + " " + a, h, sev(f, n, a));
            }
        }
        assertEquals(h, sev(f, "/c:/Users/-", "read"));
        // the JDK strips the long-path prefix on Windows; a slash before "C:" needs no separator after it
        for (String n : new String[]{"\\\\?\\UNC\\server\\share\\-", "//?/unc/server/share/-", "\\\\?\\C:\\-"}) {
            for (String a : new String[]{"read", "write"}) {
                assertEquals(n + " " + a, h, sev(f, n, a));
            }
        }
        assertEquals(h, sev(f, "/C:-", "write"));
        assertEquals(h, sev(f, "/C:*", "write"));
        assertEquals(0, sev(f, "\\\\?\\UNC\\server\\share\\dir\\-", "read"));
        assertEquals(0, sev(f, "///server/share/dir/-", "read"));
        assertEquals(0, sev(f, "/C:/data/app/-", "read"));
        // the parent of every user home, on every platform
        for (String n : new String[]{"/home/-", "/Users/-", "C:\\Users\\-"}) {
            assertEquals(n, h, sev(f, n, "read"));
        }
        // one user's whole home (Opus review F4), and a name built on an undefined property (F5)
        for (String n : new String[]{"/home/alice/-", "/Users/Alice/-", "C:\\Users\\alice\\-", "/var/root/-",
                "${review.no.such.property}${/}-"}) {
            assertEquals(n, h, sev(f, n, "read"));
        }
        assertEquals(0, sev(f, "/home/alice/work/-", "read"));
        assertEquals(0, sev(f, "/var/log/-", "read"));
        // the root user's home, on every platform
        for (String n : new String[]{"/root/-", "/Root/-"}) {
            assertEquals(n, h, sev(f, n, "read"));
        }
        // a named directory below it stays clean
        assertEquals(0, sev(f, "/root/work/-", "read"));
        assertEquals(h, sev(f, "<<ALL FILES>>", "read"));
        assertEquals(h, sev(f, "<<ALL FILES>>", "read,readlink"));
        assertEquals(h, sev(f, "<<ALL FILES>>", "readlink,read"));
        assertEquals(h, sev(f, "<<ALL FILES>>", "READ"));
        assertEquals(h, sev(f, "<<ALL FILES>>", "write"));
        // readlink alone on all files stays MEDIUM
        assertEquals(PermissionSets.MEDIUM, sev(f, "<<ALL FILES>>", "readlink"));
        // clean: an exact directory below a share or a home, a specific file, a pinned directory
        assertEquals(0, sev(f, "\\\\server\\share\\dir\\-", "read"));
        assertEquals(0, sev(f, "\\\\server\\share\\file.txt", "read"));
        assertEquals(0, sev(f, "/home/alice/work/-", "read"));
        assertEquals(0, sev(f, "/opt/app/conf/-", "read"));
        assertEquals(0, sev(f, "/etc/hosts", "read"));
        assertEquals(0, sev(f, "C:\\data\\app\\-", "read"));
    }

    // Operator ruling 2026-11-03 (finding 8): what changes WHO the code acts as is HOLD for a proxy.
    @Test
    public void testIdentityChangingAuthPermissionsAreHeld() {
        String au = "javax.security.auth.AuthPermission";
        for (String n : new String[]{"doAs", "doAsPrivileged", "setLoginConfiguration", "modifyPrincipals",
            "modifyPrivateCredentials", "*"}) {
            assertNotNull(n, PermissionSets.holdReason(au, n, null));
            assertEquals(n, PermissionSets.HIGH, sev(au, n, null));
        }
        assertNotNull(PermissionSets.holdReason("(" + au + " \"doAs\")"));
        assertNotNull(PermissionSets.holdReason("(" + au + " \"setLoginConfiguration\")"));
        // flagged, not held: they do not themselves change who the code acts as
        for (String n : new String[]{"modifyPublicCredentials", "createLoginContext.*", "createLoginContext.x",
            "getSubject", "getLoginConfiguration", "refreshLoginConfiguration"}) {
            assertNull(n, PermissionSets.holdReason(au, n, null));
            assertNotNull(n, PermissionSets.flagReason(au, n, null));
            assertEquals(n, PermissionSets.MEDIUM, sev(au, n, null));
        }
        // the same names under another class are not that permission
        assertNull(PermissionSets.holdReason("com.example.Mine", "doAs", null));
    }

    // Operator ruling 2026-11-03 (finding 8): a whole-tree read is FLAG (written, but reported).
    @Test
    public void testWholeTreeReadsAreFlagged() {
        String f = "java.io.FilePermission";
        for (String n : new String[]{"<<ALL FILES>>", "/-", "C:\\-", "c:/-", "\\\\server\\share\\-",
            "//server/-", "/home/-", "${user.home}${/}-"}) {
            assertNotNull(n, PermissionSets.flagReason(f, n, "read"));
            assertNull(n + " is not held", PermissionSets.holdReason(f, n, "read"));
        }
        assertNull(PermissionSets.flagReason(f, "/opt/app/conf/-", "read"));
        assertNull(PermissionSets.flagReason(f, "\\\\server\\share\\dir\\-", "read"));
        assertNull(PermissionSets.flagReason(f, "data/x", "read"));
        assertNotNull(PermissionSets.flagReason("(java.io.FilePermission \"/-\" \"read\")"));
    }

    // Operator ruling 2026-11-03: two names the catalogue had left clean are MEDIUM.
    @Test
    public void testGetDomainCombinerAndShutdownHooksAreMedium() {
        assertEquals(PermissionSets.MEDIUM, sev("java.security.SecurityPermission", "getDomainCombiner", null));
        assertEquals(PermissionSets.MEDIUM, sev("java.lang.RuntimePermission", "shutdownHooks", null));
        assertTrue(PermissionSets.danger("java.security.SecurityPermission", "getDomainCombiner", null)
            .reason.contains("protection-domain"));
        assertTrue(PermissionSets.danger("java.lang.RuntimePermission", "shutdownHooks", null)
            .reason.contains("JVM exit"));
        assertEquals(0, sev("java.security.SecurityPermission", "getProperty.java.version", null));
    }
}
