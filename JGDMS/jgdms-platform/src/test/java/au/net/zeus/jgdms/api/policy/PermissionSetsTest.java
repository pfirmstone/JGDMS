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
        // the JDK strips the long-path prefix on Windows (measured 2026-11-06 on the DirtyChai build JDK: the
        // npath of each of these is the plain share or drive, recursive)
        for (String n : new String[]{"\\\\?\\UNC\\server\\share\\-", "//?/UNC/server/share/-", "\\\\?\\C:\\-"}) {
            for (String a : new String[]{"read", "write"}) {
                assertEquals(n + " " + a, h, sev(f, n, a));
            }
        }
        // Not a claim about the JDK (Opus review 2026-11-06, F8; Opus normaliser review 2026-11-07, C6): its
        // "UNC" test is case-sensitive, so new FilePermission("//?/unc/server/share/-") is invalid and grants
        // nothing, and the backslash form is the relative path unc/server/share, which grants no share either.
        // HIGH is a conservative rating of a name that reaches no share there, pinned so that it is not
        // narrowed by accident.
        for (String a : new String[]{"read", "write"}) {
            assertEquals("//?/unc/server/share/-" + " " + a, h, sev(f, "//?/unc/server/share/-", a));
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

    private void assertReadAndWrite(int read, int write, String... names) {
        String f = "java.io.FilePermission";
        for (String n : names) {
            assertEquals(n + " read", read, sev(f, n, "read"));
            assertEquals(n + " write", write, sev(f, n, "write"));
        }
    }

    // Opus review 2026-11-06 (F1): FilePermission applies "." and ".." first and tests the last name for "-"
    // afterwards, so each of these is the same whole tree as its plain form (npath and recursive measured on the
    // DirtyChai build JDK).  Read is HIGH as well as write: the operator rulings of 2026-11-02 and 2026-11-03.
    @Test
    public void testTheWildcardIsTheLastNormalisedName() {
        int h = PermissionSets.HIGH;
        assertReadAndWrite(h, h, "C:\\-\\.", "C:/-/./", "C:\\-\\x\\..", "C:\\Users\\-\\.", "C:\\Users\\alice\\-\\.",
            "${user.home}${/}-${/}.", "/-/.", "/home/-/.", "/root/-/x/..", "\\\\server\\share\\-\\.",
            "//server/share/-/x/..");
        // a "-" that ".." pops is a directory, and so is one the name continues past
        assertReadAndWrite(0, 0, "C:\\Users\\alice\\Documents\\-\\..", "/home/alice/work/-/x");
        // a "*" is a wildcard only as the raw last name; "C:\Users\*\." is invalid in the JDK, and "-." is a file
        assertReadAndWrite(0, 0, "C:\\Users\\*\\.", "C:\\-.");
    }

    // Opus review 2026-11-06 (F2); order corrected by the Opus normaliser review 2026-11-07 (C-2): the JDK strips
    // a raw prefix written with backslashes first (WinNTFileSystem.java:132), then collapses the run of leading
    // separators (normalizePrefix), then WindowsPathParser strips the prefix again.  So the doubled form written
    // with backslashes is valid there and the same form written with slashes is not (measured).
    @Test
    public void testLongPathPrefixAfterAnyRunOfSeparators() {
        int h = PermissionSets.HIGH;
        assertReadAndWrite(h, h, "\\\\\\?\\C:\\Users\\-", "///?/C:/Users/alice/-", "\\\\\\?\\UNC\\server\\share\\-",
            "///?/UNC/server/share/-", "\\\\?\\\\\\?\\C:\\Users\\-", "\\\\\\?\\C:\\-");
        // File.normalize collapses a doubled separator as well, so "UNC" can follow a run of them
        assertReadAndWrite(h, h, "\\\\\\\\?\\\\UNC\\\\server\\\\share\\\\-", "//?//UNC//server//share//-");
        // the narrow names stay narrow
        assertReadAndWrite(0, 0, "\\\\\\?\\C:\\data\\app\\-", "///?/UNC/server/share/dir/-");
    }

    // Opus review 2026-11-06 (F3), a regression of 0eb9f0cc7: a drive-relative ".." chain is at least a path
    // above the current directory, as the same chain without a drive is.
    @Test
    public void testDriveRelativeParentChainsAreRated() {
        int m = PermissionSets.MEDIUM;
        int h = PermissionSets.HIGH;
        assertReadAndWrite(m, h, "..\\..\\-", "C:..\\..\\-", "/C:../../../../../../-", "C:../-", "c:..\\-");
        // C:- is the drive's current directory tree, not a chain
        assertReadAndWrite(m, h, "/C:-", "C:-");
    }

    private static String dev(String rest) {
        return "\\\\.\\" + rest;
    }

    // Opus normaliser review 2026-11-07 (N1, N2, R2-1), rule chosen by the dispatching session: a name under
    // "\\.\" that ends in "-" or "*" is HIGH at any depth, because the JDK clamps ".." at "\\.\X\" while Win32
    // roots the name at "\\.\" and applies "..", "." and a trailing dot to the device name itself.  Of the 41
    // names below that are rated HIGH, 2 are invalid in the JDK (measured 2026-11-07): "\\.\C:\Users\-" and
    // "//?///./pipe/x/-" grant nothing there and are rated by their prefix.  The N1 and N2 blocks are the
    // review's spellings; the others are this test's own: the canonical names reach their targets, the "any
    // depth" names reach no ruled target as spelled, and the "*" names reach a device through ".." in the
    // access.  All are HIGH by the rule.
    @Test
    public void testAWildcardNameInTheDeviceNamespaceIsHigh() {
        int h = PermissionSets.HIGH;
        String g = "Volume{00000000-0000-0000-0000-000000000000}";
        // the canonical forms
        assertReadAndWrite(h, h, dev("UNC\\server\\share\\-"), "//./UNC/server/share/-", dev("UNC\\-"),
            dev(g + "\\Users\\alice\\-"), dev(g + "\\-"), dev("C:\\Users\\-"),
            dev("GLOBALROOT\\Device\\HarddiskVolume3\\Users\\alice\\-"));
        // N1: a device name before the target, and ".." between them
        assertReadAndWrite(h, h, dev("pipe\\UNC\\-"), dev("pipe\\GLOBALROOT\\-"), dev("a\\" + g + "\\-"),
            dev("x\\..\\" + g + "\\-"), dev("UNC\\..\\" + g + "\\-"), dev("pipe\\..\\UNC\\localhost\\C$\\-"),
            dev("pipe\\HarddiskVolume8\\Users\\-"), dev("UNC\\server\\share\\..\\-\\."));
        // N2: a "." inside the root, a trailing dot, a case change, a second kind of root
        assertReadAndWrite(h, h, dev("GLOBALROOT\\Device\\HarddiskVolume8\\..\\HarddiskVolume8\\Users\\-"),
            dev("GLOBALROOT\\.\\Device\\HarddiskVolume8\\Users\\-"), dev("GLOBALROOT\\Device\\.\\HarddiskVolume8\\-"),
            dev("GLOBALROOT\\Device\\x\\..\\HarddiskVolume8\\Users\\alice\\-"),
            dev("GLOBALROOT.\\Device\\HarddiskVolume8\\Users\\-"), dev("GLOBALROOT\\device\\HarddiskVolume8\\Users\\-"),
            dev("GLOBALROOT\\DEVICE\\HarddiskVolume8\\-"), dev("GLOBALROOT\\Device\\Mup\\server\\share\\-"),
            dev("GLOBALROOT\\Device\\Mup\\localhost\\C$\\-"), dev("UNC.\\server\\share\\-"),
            dev(".\\UNC\\server\\share\\-"), dev("UNC\\.\\server\\share\\-"));
        // any depth, any separator flavour, behind a long-path prefix
        assertReadAndWrite(h, h, dev("UNC\\server\\share\\dir\\-"), dev(g + "\\Users\\alice\\Documents\\-"),
            dev(g + "\\data\\-"), "///./pipe/x/-", "\\\\\\.\\pipe\\x\\-", "\\\\?\\" + dev("pipe\\x\\-"),
            "//?///./pipe/x/-");
        // R2-1: "*" is not recursive, but the JDK lets "\\.\pipe\*" imply "\\.\pipe\..\Volume{guid}" and
        // "\\.\pipe\..\PhysicalDrive0" (read and write, measured) and Win32 opens the raw device, and
        // "\\.\Volume{guid}\*" is the device spelling of "C:\*"
        assertReadAndWrite(h, h, dev("pipe\\*"), dev(g + "\\*"), dev("pipe\\..\\" + g + "\\*"),
            dev("GLOBALROOT\\Device\\HarddiskVolume3\\*"), dev(g + "\\Users\\peter\\*"), dev("UNC\\server\\share\\*"),
            "//./*");
        // an exact name implies only itself (measured: "\\.\x\..\Volume{guid}" implies neither a name below it nor
        // "\\.\Volume{guid}"), as "/dev/sda" is rated 0; "foo*" is a name, not a wildcard
        assertReadAndWrite(0, 0, dev("x\\..\\" + g), dev("UNC\\server\\share"), dev("pipe\\foo"), dev("pipe\\foo*"));
        // not a device name: one separator is a path below the current drive, as the JDK reads it (npath
        // \Device\Mup\server\share) and as Win32 does (File.exists is false for \Device\Mup\localhost\C$\Users);
        // "UNC." with two separators is a host name (\\UNC.\localhost\C$\Users does not exist)
        assertReadAndWrite(0, 0, "\\Device\\Mup\\server\\share\\-", "/Device/Mup/server/share/-",
            "\\\\UNC.\\server\\share\\-");
        assertTrue(PermissionSets.filePath(dev("pipe\\UNC\\-")).device);
        assertFalse(PermissionSets.filePath("\\Device\\Mup\\-").device);
        assertFalse(PermissionSets.filePath("\\\\UNC.\\server\\share\\-").device);
        assertNull(PermissionSets.filePath(dev(g + "\\Users\\-")).drive);
        assertEquals("c:", PermissionSets.filePath("C:\\Users").drive);
    }

    // Opus normaliser review 2026-11-07 (N3): on a Unix JDK "//" is "/", so each of these names one user's whole
    // home there, and the 2026-11-06 floor at host and share had narrowed them to unrated on every host.  The
    // Windows JDK clamps ".." at the share and reads them narrower; the catalogue is host-blind and takes the
    // wider reading.  Base 0eb9f0cc7 rated all five HIGH.
    @Test
    public void testNamesThatAreAHomeOnAUnixJdkStayHigh() {
        int h = PermissionSets.HIGH;
        assertReadAndWrite(h, h, "//./home/alice/-", "//./Users/alice/-", "//home/alice/../bob/-",
            "//home/alice/x/../../bob/-", "//var/root/../root/-");
    }

    // Opus review 2026-11-06 (F5): Win32 removes one trailing dot from a segment, so "Users." is "Users".
    @Test
    public void testATrailingDotOnASegmentIsIgnored() {
        int h = PermissionSets.HIGH;
        assertReadAndWrite(h, h, "C:\\Users.\\-", "C:\\Users.\\alice\\-", "C:\\Users\\alice.\\-", "/home./-",
            "/Root./-");
        assertReadAndWrite(0, 0, "C:\\Users\\alice\\Documents.\\-");
        // only one dot is removed, and a name of dots only is no ".."
        assertEquals(java.util.Arrays.asList("Users", "a."), PermissionSets.filePath("Users./a..").segments);
        assertEquals(java.util.Arrays.asList("..."), PermissionSets.filePath("C:\\...\\-").segments);
    }

    // Opus normaliser review 2026-11-07 (R2-2): the home and root trees are named without regard to case, as the
    // JDK compares Windows paths (measured: "c:\USERS\alice\-" implies C:\Users\alice\zq81\f.txt, and so do the
    // others below).  A case-sensitive comparison left every earlier test green, because none spelt the first
    // name of a two-name home in any case but the canonical one.
    @Test
    public void testTheNamesOfTheHomeAndRootTreesAreMatchedWithoutRegardToCase() {
        int h = PermissionSets.HIGH;
        assertReadAndWrite(h, h, "c:\\USERS\\alice\\-", "C:\\USERS\\-", "/HOME/-", "/HOME/alice/-", "/Home/alice/-",
            "/VAR/ROOT/-", "/Var/Root/-", "/ROOT/-");
    }

    // Opus normaliser review 2026-11-07 (R2-2): delete and execute take the write branch of fileDanger, so a tree
    // that is only a risk to write (the temp directory, the JDK home) is HIGH to delete and to execute as well,
    // and reading it is not rated.  Dropping "delete" from that branch left every earlier test green, because
    // each earlier delete row names a tree that is HIGH whatever the action.
    @Test
    public void testDeleteAndExecuteAreRatedLikeWrite() {
        String f = "java.io.FilePermission";
        int h = PermissionSets.HIGH;
        for (String n : new String[]{"${java.io.tmpdir}${/}-", "${java.home}${/}-",
                "${java.home}${/}lib${/}security${/}cacerts", "/tmp/-", "/var/tmp/-"}) {
            assertEquals(n + " write", h, sev(f, n, "write"));
            assertEquals(n + " delete", h, sev(f, n, "delete"));
            assertEquals(n + " execute", h, sev(f, n, "execute"));
            assertEquals(n + " delete,read", h, sev(f, n, "delete,read"));
        }
        assertEquals(0, sev(f, "/tmp/-", "read"));
        assertEquals(0, sev(f, "/var/tmp/-", "read"));
        // the temp directory's own files are MEDIUM to delete, as to write; the whole current tree is HIGH
        assertEquals(PermissionSets.MEDIUM, sev(f, "${java.io.tmpdir}${/}*", "delete"));
        assertEquals(h, sev(f, "-", "delete"));
        assertEquals(PermissionSets.MEDIUM, sev(f, "-", "read"));
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

    // Operator ruling 2026-11-07 (Opus review 2026-11-06, F7), host-blind: Windows keeps "Documents and Settings" as a
    // junction to "Users" (the review measured Files.isSameFile of the two homes true), so as the first name below a
    // drive or the root it is "Users" for every Users rule.  The JDK does not model junctions lexically, so the
    // differential test cannot score these; the ratings are the ruling's.
    @Test
    public void testDocumentsAndSettingsIsUsers() {
        int h = PermissionSets.HIGH;
        // the whole Users tree and one user's whole home, in any case, separator, prefix or dotted spelling
        assertReadAndWrite(h, h, "C:\\Documents and Settings\\-", "c:/documents and settings/-",
            "C:\\DOCUMENTS AND SETTINGS\\-", "\\Documents and Settings\\-", "/Documents and Settings/-",
            "\\\\?\\C:\\Documents and Settings\\-", "///?/C:/Documents and Settings/-",
            "C:\\Documents and Settings.\\-", "C:\\Documents and Settings\\-\\.",
            "C:\\x\\..\\Documents and Settings\\-", "C:\\Documents and Settings\\alice\\-",
            "C:/Documents and Settings/Alice/-", "\\Documents and Settings\\alice\\-",
            "/documents and settings/alice/-", "C:\\Documents and Settings\\alice.\\-");
        // below a home, as under Users; "*" over a home or Users has no ruling (Q2) and is unrated, as C:\Users\*
        assertReadAndWrite(0, 0, "C:\\Documents and Settings\\alice\\Documents\\-",
            "C:/Documents and Settings/alice/work/-", "C:\\Documents and Settings\\alice\\ntuser.dat",
            "C:\\Documents and Settings\\*", "C:\\Documents and Settings\\alice\\*");
        // no root name: relative, drive-relative, below another name or below an ordinary share
        assertReadAndWrite(0, 0, "Documents and Settings\\-", "C:Documents and Settings\\-",
            "C:\\data\\Documents and Settings\\-", "\\\\server\\share\\Documents and Settings\\-",
            "\\\\server\\share\\Documents and Settings\\alice\\-", "C:\\Documents and Settings\\alice\\Documents.\\-");
        // the lexical parse is not aliased: the fold belongs to the rating
        assertEquals(java.util.Arrays.asList("Documents and Settings"),
            PermissionSets.filePath("C:\\Documents and Settings\\-").segments);
        // the working home is compared in the same form: a home spelt through the junction is the same home
        java.util.Properties env = new java.util.Properties();
        env.setProperty("user.home", "C:\\Documents and Settings\\bob\\h");
        assertEquals(h, PermissionSets.danger("java.io.FilePermission", "C:\\Users\\bob\\h\\-", "read", env).severity);
        assertEquals(h,
            PermissionSets.danger("java.io.FilePermission", "C:\\Documents and Settings\\bob\\h\\-", "read", env).severity);
        assertNull(PermissionSets.danger("java.io.FilePermission", "C:\\Users\\bob\\h\\sub\\-", "read", env));
    }

    // Operator ruling 2026-11-07 (F7), host-blind: a UNC share named one letter and "$" is that drive on ANY host
    // (the review measured Files.isSameFile of \\localhost\C$\Users\peter and C:\Users\peter true), so every rule of
    // the drive holds below it.  Any other share keeps the ruling of 2026-11-03: a named directory below it is unrated.
    @Test
    public void testALetterAndDollarShareIsThatDriveOnAnyHost() {
        int h = PermissionSets.HIGH;
        int m = PermissionSets.MEDIUM;
        // the whole drive, the whole Users tree, one user's whole home, the junction through the share
        assertReadAndWrite(h, h, "\\\\localhost\\C$\\-", "\\\\h\\c$\\-", "\\\\h\\D$\\-", "//h/C$/-", "\\\\h\\C$\\-\\.",
            "\\\\localhost\\C$\\Users\\-", "\\\\h\\c$\\users\\-", "//h/C$/Users/-", "\\\\10.0.0.7\\C$\\Users\\-",
            "\\\\?\\UNC\\h\\C$\\Users\\-", "///h/C$/Users/-", "\\\\h\\C$\\x\\..\\Users\\-",
            "\\\\localhost\\C$\\Users\\alice\\-", "\\\\h\\E$\\Users\\Alice\\-", "//h/C$/Users/alice/-/.",
            "\\\\localhost\\C$\\Documents and Settings\\-", "\\\\h\\C$\\Documents and Settings\\alice\\-",
            "//h/c$/documents and settings/-");
        // the drive's own files, as C:\*; a "*" over Users or a home has no ruling (Q2), as under C:
        assertReadAndWrite(m, h, "\\\\h\\C$\\*");
        assertReadAndWrite(0, 0, "\\\\h\\C$\\Users\\*", "\\\\h\\C$\\Users\\alice\\*");
        // a named directory below the drive, Users or a home stays unrated, as under C:
        assertReadAndWrite(0, 0, "\\\\h\\C$\\Users\\alice\\Documents\\-", "\\\\h\\C$\\data\\-",
            "\\\\h\\C$\\Users\\alice\\ntuser.dat", "\\\\h\\C$\\Documents and Settings\\alice\\Documents\\-");
        // an ordinary share is no drive, whatever its directory is called
        assertReadAndWrite(0, 0, "\\\\h\\share\\Users\\-", "\\\\h\\share\\Users\\alice\\-",
            "\\\\h\\share\\Documents and Settings\\-", "//h/share/data/-", "\\\\h\\share\\C$\\Users\\-");
        // ADMIN$ and IPC$ are shares: a whole one is HIGH as any share is, a directory below it is unrated
        assertReadAndWrite(h, h, "\\\\h\\ADMIN$\\-", "\\\\h\\IPC$\\-");
        assertReadAndWrite(0, 0, "\\\\h\\ADMIN$\\Users\\x", "\\\\h\\ADMIN$\\Users\\-", "\\\\h\\ADMIN$\\Users\\alice\\-",
            "\\\\h\\IPC$\\Users\\-");
        // not "one letter and $": two letters, trailing text, a digit, a non-ASCII letter, no letter
        for (String share : new String[]{"CC$", "C$x", "1$", "$", "C$$", "\u00e9$", "C"}) {
            assertReadAndWrite(0, 0, "\\\\h\\" + share + "\\Users\\-", "\\\\h\\" + share + "\\Users\\alice\\-");
        }
        // only the second name is the share: a host called C$ is no drive
        assertReadAndWrite(0, 0, "\\\\C$\\share\\Users\\-");
        // the drive is the share's letter: a home on D: is reached through D$, and not through E$ or from C:
        java.util.Properties env = new java.util.Properties();
        env.setProperty("user.home", "D:\\data\\home");
        String f = "java.io.FilePermission";
        assertEquals(h, PermissionSets.danger(f, "\\\\h\\D$\\data\\home\\-", "read", env).severity);
        assertNull(PermissionSets.danger(f, "\\\\h\\E$\\data\\home\\-", "read", env));
        assertNull(PermissionSets.danger(f, "C:\\data\\home\\-", "read", env));
    }

    // Opus alias review 2026-11-07 (F6): "Documents and Settings" is the junction only as the first name below a
    // drive or the root.  Each input is unrated because a guard of aliased() leaves the name plain, and is rated when
    // that guard is removed; the home and JDK-home values come from the environment, so the result does not depend on
    // who runs the test.  The relative property value is contrived: for a name the f.absolute guard changes no
    // rating, because fileReach returns a relative name unrated before any Users rule.
    @Test
    public void testTheDocumentsAndSettingsFoldKeepsToADriveOrRootName() {
        String f = "java.io.FilePermission";
        java.util.Properties env = new java.util.Properties();
        // a UNC host called "Documents and Settings" is a host, and its share "bob" is no home of user bob
        env.setProperty("user.home", "C:\\Users\\bob");
        assertNull(PermissionSets.danger(f, "\\\\Documents and Settings\\bob\\*", "read", env));
        assertNull(PermissionSets.danger(f, "\\\\Documents and Settings\\bob\\*", "write", env));
        // a device name is not a path below a drive: this exact name is no file of the JDK home
        env.setProperty("java.home", "C:\\Users\\bob\\jdk");
        assertNull(PermissionSets.danger(f, "//./Documents and Settings/bob/jdk/bin/java.exe", "write", env));
        // a relative name is not below the root: the junction is not "Documents and Settings" below the working directory
        env.setProperty("java.home", "Documents and Settings\\bob\\jdk");
        assertNull(PermissionSets.danger(f, "C:\\Users\\bob\\jdk\\conf\\x", "write", env));
        // control: the same JDK home, below a drive, is the Users tree, so the name above is a file of it
        env.setProperty("java.home", "C:\\Documents and Settings\\bob\\jdk");
        assertEquals(PermissionSets.HIGH,
            PermissionSets.danger(f, "C:\\Users\\bob\\jdk\\conf\\x", "write", env).severity);
    }

    // Operator ruling 2026-11-07: a recursive name with an 8.3 short name among its first two names may stand for a
    // user home, so it is HIGH on every host; the shape alone is rated, since the file system says what a short name
    // is and 8.3 generation can be off.  Deeper names, "*" names, exact names and any non-short name are unrated.
    @Test
    public void testAnEightDotThreeNameNearTheRootIsHigh() {
        int h = PermissionSets.HIGH;
        assertReadAndWrite(h, h, "C:\\DOCUME~1\\-", "c:/docume~1/-", "C:\\USERS~1\\-", "C:\\USERS~1\\PETER~1\\-",
            "C:\\Users\\PETER~1\\-", "C:\\DOCUME~1\\alice\\-", "C:\\PROGRA~1\\-", "\\USERS~1\\-", "/HOME~1/-",
            "\\\\?\\C:\\DOCUME~1\\-", "C:\\DOCUME~12\\-", "C:\\ABCDEF~1.TXT\\-", "C:\\DOCUME~1\\-\\.");
        assertReadAndWrite(0, 0, "C:\\PROGRA~1\\app\\lib\\-", "C:\\DOCUME~1\\alice\\Documents\\-", "C:\\DOCUME~1\\*",
            "C:\\USERS~1\\alice\\ntuser.dat", "C:\\data~\\-", "C:\\ABCDEFG~1\\-", "C:\\~1\\-", "C:\\Users\\alice\\~x\\-",
            "DOCUME~1\\-", "C:DOCUME~1\\-");
    }

    // Operator ruling 2026-11-07: "Local Settings" in a profile is the junction to AppData\Local, so it reaches the
    // temp directory as the long spelling does; the home value comes from the environment.
    @Test
    public void testLocalSettingsIsAppDataLocal() {
        String f = "java.io.FilePermission";
        java.util.Properties env = new java.util.Properties();
        env.setProperty("user.home", "C:\\Users\\bob");
        env.setProperty("java.io.tmpdir", "C:\\Users\\bob\\AppData\\Local\\Temp");
        for (String n : new String[]{"C:\\Users\\bob\\AppData\\Local\\Temp\\-", "C:\\Users\\bob\\Local Settings\\Temp\\-",
            "C:\\Documents and Settings\\bob\\Local Settings\\Temp\\-", "c:/users/bob/local settings/temp/-",
            // operator ruling 2026-11-07 (Q1): the 8.3 names of the two junctions fold too
            "C:\\Users\\bob\\LOCALS~1\\Temp\\-", "C:\\DOCUME~1\\bob\\AppData\\Local\\Temp\\-",
            "C:\\DOCUME~1\\bob\\LOCALS~1\\Temp\\-", "c:/docume~1/bob/locals~1/temp/-"}) {
            PermissionSets.Danger d = PermissionSets.danger(f, n, "write", env);
            assertNotNull(n, d);
            assertTrue(n + " reason " + d.reason, d.reason.contains("temp directory"));
        }
        // a UNC host called Users is a host, not the Users directory.  The other two guards of the fold (first name
        // Users, an absolute name) change no rating with this environment: a relocated profile is left open
        // (Q1 of the 2026-11-07 Opus review folded the 8.3 names only), so they are not pinned here.
        assertNull(PermissionSets.danger(f, "\\\\Users\\bob\\Local Settings\\Temp\\-", "write", env));
    }

    // Opus review 2026-11-07 (MP1): the rule looks at the second name too; this name has no Users or home rule behind it.
    @Test
    public void testAShortNameInTheSecondPlaceIsHigh() {
        assertReadAndWrite(PermissionSets.HIGH, PermissionSets.HIGH, "C:\\data\\PETERF~1\\-", "/srv/PETERF~1/-");
    }

    // Opus alias review 2026-11-07 (F4), accepted by the dispatching session as a conservative over-rating and pinned:
    // narrowing it is the operator's decision.  filePath pops the share on "..", which is the reading of a Unix JDK
    // ("//" is "/"; N3) and the wider one, and the X$ fold exists only on Windows, where the JDK and the OS clamp ".."
    // at the share and see a named directory below a share, which the ruling leaves unrated.  So the combination is
    // a reading no host produces.  Both names were unrated before the fold.
    @Test
    public void testDotDotAcrossAShareBeforeADriveShareIsHigh() {
        int h = PermissionSets.HIGH;
        assertReadAndWrite(h, h, "\\\\h\\x\\..\\C$\\Users\\-", "\\\\h\\C$\\..\\C$\\Users\\-");
    }

    private static boolean flagged(String name) {
        return PermissionSets.flagReason("java.io.FilePermission", name, "read") != null;
    }

    // Operator ruling 2026-11-07 (Opus review 2026-11-06, F6): ListPromotion never calls danger() (it asks holdReason
    // and flagReason), so a spelling that only danger() read as a whole tree promoted unflagged.  flagReason reads a
    // FilePermission name as danger does: the F1 to F5 spellings of the normaliser, the OS aliases and the
    // undefined-property ruling.
    // (At 685e1e541 every F1 to F5 spelling below was already flagged, through fileReach; this pins it, and the
    // undefined property was the one that was not.)
    @Test
    public void testFlagReasonReadsAFileNameAsDangerDoes() {
        String f = "java.io.FilePermission";
        String[] wholeTrees = {
            // F1: the wildcard is the last normalised name
            "C:\\-\\.", "C:/-/./", "C:\\-\\x\\..", "C:\\Users\\-\\.", "C:\\Users\\alice\\-\\.", "${user.home}${/}-${/}.",
            "/-/.", "/home/-/.", "/root/-/x/..", "\\\\server\\share\\-\\.", "//server/share/-/x/..",
            // F2: a long-path prefix behind a run of separators
            "\\\\\\?\\C:\\Users\\-", "///?/C:/Users/alice/-", "\\\\\\?\\UNC\\server\\share\\-", "\\\\?\\\\\\?\\C:\\Users\\-",
            // F4: the device namespace
            "\\\\.\\UNC\\server\\share\\-", "//./UNC/server/share/-", "\\\\.\\pipe\\x\\-", "//./*",
            "\\\\.\\Volume{00000000-0000-0000-0000-000000000000}\\Users\\alice\\-",
            // F5: a trailing dot on a segment
            "C:\\Users.\\-", "C:\\Users.\\alice\\-", "C:\\Users\\alice.\\-", "/home./-", "/Root./-",
            // the OS aliases
            "C:\\Documents and Settings\\-", "C:\\Documents and Settings\\alice\\-", "\\\\localhost\\C$\\-",
            "\\\\localhost\\C$\\Users\\-", "\\\\h\\c$\\Users\\alice\\-", "//h/C$/Documents and Settings/-",
            // the undefined-property ruling
            "${undefined.prop}/-", "${undefined.prop}${/}-", "${undefined.prop}/x/-"};
        for (String n : wholeTrees) {
            assertNotNull("flagReason " + n, PermissionSets.flagReason(f, n, "read"));
            assertEquals("danger " + n, PermissionSets.HIGH, sev(f, n, "read"));
            assertNull("flagReason is not held " + n, PermissionSets.holdReason(f, n, "read"));
        }
        // as ListPromotion calls it, from an entry line
        assertNotNull(PermissionSets.flagReason("(java.io.FilePermission \"C:\\\\-\\\\.\" \"read\")"));
        assertNotNull(PermissionSets.flagReason("(java.io.FilePermission \"${undefined.prop}/-\" \"read\")"));
        assertNotNull(PermissionSets.flagReason("(java.io.FilePermission \"\\\\\\\\localhost\\\\C$\\\\Users\\\\-\" \"read\")"));
        // danger() rates a name on an undefined property as the root whatever its tail, so the flag does too;
        // the reason says so
        assertTrue(PermissionSets.flagReason(f, "${undefined.prop}/foo.jar", "read").contains("${property}"));
        // F3: a relative chain is MEDIUM to read and HIGH only to write, so it is no whole tree, with or without a
        // drive, and FLAG does not look at the action
        assertEquals(flagged("..\\..\\-"), flagged("C:..\\..\\-"));
        assertFalse(flagged("C:..\\..\\-"));
        assertEquals(PermissionSets.MEDIUM, sev(f, "C:..\\..\\-", "read"));
        // narrow names are not flagged
        for (String n : new String[]{"C:\\Users\\alice\\Documents\\-\\..", "/home/alice/work/-/x", "C:\\Users\\*\\.",
                "C:\\Documents and Settings\\alice\\Documents\\-", "\\\\h\\ADMIN$\\Users\\x", "\\\\h\\CC$\\Users\\-",
                "\\\\h\\C$\\Users\\alice\\work\\-", "//h/share/data/-", "\\\\h\\share\\Users\\-", "C:\\Users\\*",
                "\\\\.\\pipe\\foo"}) {
            assertNull("flagReason " + n, PermissionSets.flagReason(f, n, "read"));
        }
        // danger() judges a property in the environment it is given; ListPromotion passes none, so flagReason reads
        // the same property as undefined, and danger()'s own FLAG step must not (it passes its environment on)
        java.util.Properties env = new java.util.Properties();
        env.setProperty("app.home", "/opt/app");
        assertNull(PermissionSets.danger(f, "${app.home}/work/-", "read", env));
        assertNotNull(PermissionSets.flagReason(f, "${app.home}/work/-", "read"));
    }
}
