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
package au.zeus.jgdms.security.jwt;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link BoundedBody} under a real SecurityManager, in child JVMs (a SecurityManager
 * cannot be removed from this one).
 *
 * <p>Invariant: the watchdog is the module's own business.  The permissions it needs
 * ({@code createPlatformThread}, {@code setContextClassLoader}) are checked against the
 * module's code source and never against the code that happens to call the module, so
 * a policy that grants them to the module's codebase is enough, and an auditing
 * SecurityManager (polpAudit) records them against that codebase only.
 */
public class WatchdogSecurityManagerTest {

    /** DirtyChai guards lambda spinning (any lambda of the module) with this. */
    static final String DEFINE =
            "permission au.zeus.jdk.authorization.guards.DefineClassPermission \"ALLOW\";\n";

    /** The two thread permissions the watchdog's threads need. */
    static final String THREAD_PERMS =
            "permission java.lang.RuntimePermission \"createPlatformThread\";\n"
            + "permission java.lang.RuntimePermission \"setContextClassLoader\";\n";

    /** What the module is granted for the watchdog: the thread permissions and lambdas. */
    static final String MODULE_PERMS = THREAD_PERMS + DEFINE;

    /** What the application code (WatchdogChild) is granted: never a thread permission. */
    static final String CALLER_BASE =
            "permission java.util.PropertyPermission \"*\", \"read\";\n"
            + "permission java.io.FilePermission \"<<ALL FILES>>\", \"read\";\n"
            + "permission au.zeus.jdk.authorization.guards.LoadClassPermission \"ALLOW\";\n";

    @Test
    public void theFirstReadSucceedsWhenTheCallerLacksCreatePlatformThread() throws Exception {
        String out = runChild(MODULE_PERMS, CALLER_BASE, new String[0], "read");
        assertTrue(out, out.contains("CHILD-READ-OK 5"));
        assertEquals(out, 2, out.split("CHILD-READ-OK", -1).length - 1);
    }

    /** The deadline passes: the closer thread is created too, with no thread permission for the caller. */
    @Test
    public void aDeadlineThatPassesWorksWhenTheCallerLacksCreatePlatformThread() throws Exception {
        String out = runChild(MODULE_PERMS, CALLER_BASE, new String[0], "expire");
        assertTrue(out, out.contains("CHILD-EXPIRE-OK"));
    }

    /**
     * Invariant: the module's threads inherit the module's protection domain alone, so
     * polpAudit (empty starting policy) records the thread permissions against the module
     * and the jgdms-collections jar needs none of them.
     */
    @Test
    public void polpAuditRecordsTheThreadPermissionsForTheModuleAndNotForJgdmsCollections() throws Exception {
        Path recorded = Files.createTempFile("watchdog-audit", ".policy");
        String out = runChild(recorded, "", "", new String[0], "read");
        assertTrue(out, out.contains("CHILD-READ-OK 5"));
        out = runChild(recorded, "", "", new String[0], "expire");
        assertTrue(out, out.contains("CHILD-EXPIRE-OK"));
        assertOnlyTheModuleIsRecorded(recorded, "createPlatformThread", "setContextClassLoader");
    }

    /** Without the grant the failure is loud and at login, not a silent missing deadline. */
    @Test
    public void withoutTheGrantLoginFailsWithAClearMessage() throws Exception {
        String out = runChild(DEFINE, CALLER_BASE, new String[0],
                "login", "https://localhost:1/jwks.json", "https://x", "a.b.c");
        assertTrue(out, out.contains("CHILD-LOGIN-REFUSED"));
        assertTrue(out, out.contains("watchdog could not be started"));
    }

    /**
     * Runs {@link WatchdogChild} under a SecurityManager with a policy that gives the
     * JWT module {@code moduleGrant} and the child itself {@code callerGrant}.  The
     * jgdms-collections jar is granted nothing but what loading its classes needs: the
     * module's threads must not put it on their context.
     *
     * @return the child's combined output; fails the test if the child exits non-zero
     */
    static String runChild(String moduleGrant, String callerGrant,
            String[] systemProperties, String... args) throws Exception {
        return runChild(null, moduleGrant, callerGrant, systemProperties, args);
    }

    /**
     * As {@link #runChild(String, String, String[], String...)}; with {@code auditFile} not
     * null the child runs under polpAudit with an empty starting policy instead, and the
     * grants each domain needed are appended to {@code auditFile}.  polpAudit never denies,
     * so only the recorded file says anything.
     */
    static String runChild(Path auditFile, String moduleGrant, String callerGrant,
            String[] systemProperties, String... args) throws Exception {
        Path policy = Files.createTempFile("watchdog-sm", ".policy");
        StringBuilder p = new StringBuilder();
        // The JDK's own (non-boot) modules, as in every JGDMS policy.
        p.append("grant codeBase \"jrt:/-\" {\npermission java.security.AllPermission;\n};\n");
        // jgdms-platform (JwtPrincipal and the rest of the base) is trusted JGDMS code, not what is tested.
        p.append(grant(net.jini.security.jwt.JwtPrincipal.class, "permission java.security.AllPermission;\n"));
        p.append(grant(BoundedBody.class, moduleGrant));
        // jgdms-collections: loadable and readable, nothing else.
        p.append(grant(COLLECTIONS_CLASS, ""));
        p.append(grant(WatchdogChild.class, callerGrant));
        Files.write(policy, p.toString().getBytes(StandardCharsets.UTF_8));
        // Developer aid: -Dwatchdog.audit.policy=FILE runs the child under polpAudit with an
        // empty policy instead, which appends to FILE the grants each domain needed, and
        // checks nothing (polpAudit never denies).
        String audit = auditFile != null ? auditFile.toString() : System.getProperty("watchdog.audit.policy");
        if (audit != null) {
            policy = Path.of(audit);
            Files.write(policy, new byte[0]);
        }
        String javaExe = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = new ArrayList<>(List.of(javaExe,
                "-Djava.security.manager=" + (audit != null ? "polpAudit" : "default"),
                "-Djava.security.policy=" + policy));
        for (String sp : systemProperties) cmd.add(sp);
        cmd.addAll(List.of("-cp", classPath(), WatchdogChild.class.getName()));
        cmd.addAll(List.of(args));
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        // Read on another thread: a child that does not exit (a non-daemon thread) must time out, not hang the test.
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try {
                proc.getInputStream().transferTo(buf);
            } catch (java.io.IOException ignore) {
                // The child was destroyed.
            }
        });
        reader.setDaemon(true);
        reader.start();
        boolean exited = proc.waitFor(120, TimeUnit.SECONDS);
        if (!exited) proc.destroyForcibly().waitFor();
        reader.join(10_000);
        String out = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        assertTrue("child timed out: " + out, exited);
        assertEquals("policy:\n" + p + "\noutput:\n" + out, 0, proc.exitValue());
        return out;
    }

    /** A class of the jgdms-collections jar, which locates it for the policy. */
    static final Class<?> COLLECTIONS_CLASS = org.apache.river.thread.NewThreadAction.class;

    /** Permission names a module thread needs, which the jgdms-collections jar must never be recorded with. */
    static final String[] VIRAL = {"createPlatformThread", "setContextClassLoader", "URLPermission", "AuthPermission"};

    /**
     * Parses a policy recorded by polpAudit into codebase to the concatenated text of its
     * grant blocks.  A grant without a codebase is keyed "".
     */
    static java.util.Map<String, String> parseGrants(String policyText) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("grant\\s+(?:codebase\\s+\"([^\"]*)\"\\s*)?[^{]*\\{(.*?)\\n\\s*\\}\\s*;",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                .matcher(policyText);
        while (m.find()) out.merge(m.group(1) == null ? "" : m.group(1), m.group(2), (x, y) -> x + "\n" + y);
        return out;
    }

    /** The recorded text of every domain of the module's own classes (not the test classes, which play the caller). */
    static String moduleDomains(java.util.Map<String, String> grants) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : grants.entrySet()) {
            String cb = e.getKey().replace('\\', '/');
            if (cb.contains("jgdms-security-jwt") && !cb.contains("test-classes")) sb.append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * Fails unless the recorded policy gives the jgdms-collections jar none of {@link #VIRAL}
     * (LoadClassPermission and reading its own jar are fine) and the module's domain every
     * name in {@code moduleNeeds}.
     */
    static void assertOnlyTheModuleIsRecorded(Path recorded, String... moduleNeeds) throws Exception {
        String text = new String(Files.readAllBytes(recorded), StandardCharsets.UTF_8);
        // Developer aid: -Dwatchdog.audit.copy=FILE keeps the recorded policy for reading.
        if (System.getProperty("watchdog.audit.copy") != null) {
            Files.write(Path.of(System.getProperty("watchdog.audit.copy")), text.getBytes(StandardCharsets.UTF_8));
        }
        java.util.Map<String, String> grants = parseGrants(text);
        String module = moduleDomains(grants);
        assertTrue("no jwt module domain recorded in:\n" + text, module.length() > 0);
        for (String need : moduleNeeds) {
            assertTrue(need + " not recorded for the jwt module in:\n" + text, module.contains(need));
        }
        for (java.util.Map.Entry<String, String> e : grants.entrySet()) {
            if (!e.getKey().contains("jgdms-collections")) continue;
            for (String v : VIRAL) {
                assertTrue(v + " recorded for " + e.getKey() + " in:\n" + text, !e.getValue().contains(v));
            }
        }
    }

    /**
     * The class path of this JVM, with a surefire manifest-only booter jar expanded to
     * the entries of its Class-Path, so that no class of the child is loaded through a
     * code source (the booter jar) that the policy does not name.
     */
    private static String classPath() throws Exception {
        List<String> out = new ArrayList<>();
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File f = new File(e);
            if (f.isFile()) {
                try (java.util.jar.JarFile jf = new java.util.jar.JarFile(f)) {
                    java.util.jar.Manifest mf = jf.getManifest();
                    String cp = mf == null ? null : mf.getMainAttributes().getValue("Class-Path");
                    if (cp != null) {
                        for (String u : cp.trim().split("\\s+")) {
                            out.add(new File(java.net.URI.create(u)).getPath());
                        }
                        continue;
                    }
                }
            }
            out.add(e);
        }
        return String.join(File.pathSeparator, out);
    }

    /** DirtyChai asks for this against the domain of every code source whose classes are defined. */
    private static final String LOAD =
            "permission au.zeus.jdk.authorization.guards.LoadClassPermission \"ALLOW\";\n";

    private static String grant(Class<?> c, String permissions) throws Exception {
        URL loc = c.getProtectionDomain().getCodeSource().getLocation();
        String u = loc.toString();
        String path = loc.getPath().replaceFirst("^/(?=[A-Za-z]:)", "");
        String own = LOAD + "permission java.io.FilePermission \"" + path + "\", \"read\";\n";
        if (u.endsWith("/")) {
            u = u + "-";
            // The directory itself, which "dir/-" does not imply.
            own = LOAD + "permission java.io.FilePermission \"" + path.substring(0, path.length() - 1) + "\", \"read\";\n"
                    + "permission java.io.FilePermission \"" + path + "-\", \"read\";\n";
        }
        // Every codebase may read its own classes: the class loader asks for it when the code is defined.
        return "grant codeBase \"" + u + "\" {\n" + permissions + own + "};\n";
    }

    /** Writes a trust store holding only {@code cert}, for a child JVM's HttpClient. */
    static File trustStore(java.security.cert.Certificate cert, char[] password) throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("server", cert);
        File f = File.createTempFile("watchdog-trust", ".p12");
        try (java.io.OutputStream os = Files.newOutputStream(f.toPath())) {
            ks.store(os, password);
        }
        f.deleteOnExit();
        return f;
    }
}
