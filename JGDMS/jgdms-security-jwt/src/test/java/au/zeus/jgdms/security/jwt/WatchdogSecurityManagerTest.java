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
        String out = runChild(MODULE_PERMS, THREAD_PERMS, CALLER_BASE, new String[0], "read");
        assertTrue(out, out.contains("CHILD-READ-OK 5"));
        assertEquals(out, 2, out.split("CHILD-READ-OK", -1).length - 1);
    }

    /** The deadline passes: the closer thread is created too, with no thread permission for the caller. */
    @Test
    public void aDeadlineThatPassesWorksWhenTheCallerLacksCreatePlatformThread() throws Exception {
        String out = runChild(MODULE_PERMS, THREAD_PERMS, CALLER_BASE, new String[0], "expire");
        assertTrue(out, out.contains("CHILD-EXPIRE-OK"));
    }

    /** Without the grant the failure is loud and at login, not a silent missing deadline. */
    @Test
    public void withoutTheGrantLoginFailsWithAClearMessage() throws Exception {
        String out = runChild(DEFINE, "", CALLER_BASE, new String[0],
                "login", "https://localhost:1/jwks.json", "https://x", "a.b.c");
        assertTrue(out, out.contains("CHILD-LOGIN-REFUSED"));
        assertTrue(out, out.contains("watchdog could not be started"));
    }

    /**
     * Runs {@link WatchdogChild} under a SecurityManager with a policy that gives the
     * JWT module {@code moduleGrant}, the module's thread factory ({@code NewThreadAction},
     * on the stack of every thread creation) {@code collectionsGrant} and the child itself
     * {@code callerGrant}.
     *
     * @return the child's combined output; fails the test if the child exits non-zero
     */
    static String runChild(String moduleGrant, String collectionsGrant, String callerGrant,
            String[] systemProperties, String... args) throws Exception {
        Path policy = Files.createTempFile("watchdog-sm", ".policy");
        StringBuilder p = new StringBuilder();
        // The JDK's own (non-boot) modules, as in every JGDMS policy.
        p.append("grant codeBase \"jrt:/-\" {\npermission java.security.AllPermission;\n};\n");
        // jgdms-platform (JwtPrincipal and the rest of the base) is trusted JGDMS code, not what is tested.
        p.append(grant(net.jini.security.jwt.JwtPrincipal.class, "permission java.security.AllPermission;\n"));
        p.append(grant(BoundedBody.class, moduleGrant));
        p.append(grant(org.apache.river.thread.NewThreadAction.class, collectionsGrant));
        p.append(grant(WatchdogChild.class, callerGrant));
        Files.write(policy, p.toString().getBytes(StandardCharsets.UTF_8));
        // Developer aid: -Dwatchdog.audit.policy=FILE runs the child under polpAudit with an
        // empty policy instead, which appends to FILE the grants each domain needed, and
        // checks nothing (polpAudit never denies).
        String audit = System.getProperty("watchdog.audit.policy");
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
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue("child timed out: " + out, proc.waitFor(120, TimeUnit.SECONDS));
        assertEquals("policy:\n" + p + "\noutput:\n" + out, 0, proc.exitValue());
        return out;
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
