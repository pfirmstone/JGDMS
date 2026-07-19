/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.phoenix;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.Resolve;
import org.apache.river.api.io.Serializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Phoenix-side mirror of jgdms-der's {@code ClauseThreeResolveProxyTest} tripwire (security
 * re-review S1). The DER decode-admission gate's clause 3 admits any wire-named leaf that is
 * {@code @AtomicSerial} + java.io {@link Resolve} but NOT {@code @Serializer} (its resolved type
 * is unknowable before construction, so its {@code (GetArg)} ctor runs). The jgdms-der tripwire
 * cannot see phoenix classes (phoenix depends on jgdms-der, not vice-versa), so this test pins
 * the clause-3 set contributed by the <b>phoenix</b> production classpath, and FAILS if a new,
 * unreviewed {@code @AtomicSerial}+{@code Resolve}-not-{@code @Serializer} class appears.
 *
 * <p>The scan is restricted to classpath entries whose name contains {@code "phoenix"}, so the
 * jgdms-der / jgdms-platform clause-3 classes (already pinned by the der tripwire) are not
 * duplicated here.
 */
public class PhoenixClauseThreeTripwireTest {

    /**
     * The clause-3 classes contributed by phoenix, each reviewed inert in review round 2:
     * <ul>
     *   <li>{@code org.apache.river.phoenix.dl.AID$State} — the {@code @AtomicSerial}
     *       serialization proxy for an {@code AID} (the outer {@code AID} implements the java.io
     *       {@code Replace} interface, not {@code Resolve}). {@code readResolve()} rebuilds an
     *       {@code AID} from decoded {@code Uuid} bits — no reflective construction, no
     *       {@code Permission} reconstruction;</li>
     *   <li>{@code org.apache.river.phoenix.dl.ConstrainableAID$State} — the constrainable
     *       counterpart; {@code readResolve()} rebuilds a {@code ConstrainableAID} and verifies
     *       its method constraints — again no reflective/gadget surface.</li>
     * </ul>
     */
    private static final Set<String> PINNED_PHOENIX_CLAUSE_THREE = new TreeSet<>(Set.of(
            "org.apache.river.phoenix.dl.AID$State",
            "org.apache.river.phoenix.dl.ConstrainableAID$State"
    ));

    @Test
    public void noNewPhoenixClauseThreeClassEntersUnreviewed() throws Exception {
        Set<String> discovered = discoverClauseThree();
        assertEquals(
                "A new @AtomicSerial+Resolve-not-@Serializer class became DER decode-gate clause-3 "
                + "reachable on the phoenix production classpath. Each such class is a decode-time "
                + "construction surface (its ctor runs in a wrong-typed slot). REVIEW it for "
                + "inertness (no reflective construction, no Permission reconstruction) before "
                + "pinning it in PINNED_PHOENIX_CLAUSE_THREE; if it is NOT inert, that is a finding. "
                + "Discovered=" + discovered + " Pinned=" + PINNED_PHOENIX_CLAUSE_THREE,
                PINNED_PHOENIX_CLAUSE_THREE, discovered);
    }

    // ---- classpath scan (phoenix production entries only) ---------------------------------

    private static Set<String> discoverClauseThree() throws Exception {
        Set<String> found = new TreeSet<>();
        ClassLoader loader = PhoenixClauseThreeTripwireTest.class.getClassLoader();
        String cp = System.getProperty("java.class.path", "");
        for (String entry : cp.split(File.pathSeparator)) {
            if (entry.isEmpty() || entry.contains("test-classes") || !entry.contains("phoenix")) {
                continue; // phoenix production classes only
            }
            File f = new File(entry);
            if (f.isDirectory()) {
                scanDir(f.toPath(), f.toPath(), loader, found);
            } else if (f.isFile() && f.getName().endsWith(".jar")
                    && !f.getName().contains("-sources") && !f.getName().contains("-javadoc")) {
                scanJar(f, loader, found);
            }
        }
        return found;
    }

    private static void scanDir(Path root, Path dir, ClassLoader loader, Set<String> out)
            throws Exception {
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(p -> p.toString().endsWith(".class"))
             .forEach(p -> {
                 String rel = root.relativize(p).toString().replace(File.separatorChar, '.');
                 consider(rel.substring(0, rel.length() - ".class".length()), loader, out);
             });
        }
    }

    private static void scanJar(File jar, ClassLoader loader, Set<String> out) throws Exception {
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (!n.endsWith(".class") || n.equals("module-info.class") || n.contains("META-INF")) {
                    continue;
                }
                consider(n.substring(0, n.length() - ".class".length()).replace('/', '.'), loader, out);
            }
        }
    }

    private static void consider(String className, ClassLoader loader, Set<String> out) {
        if (className.endsWith("module-info") || className.endsWith("package-info")) {
            return;
        }
        try {
            Class<?> c = Class.forName(className, false, loader); // no <clinit>
            if (c.isInterface() || c.isAnnotation()) {
                return;
            }
            if (c.isAnnotationPresent(AtomicSerial.class)
                    && Resolve.class.isAssignableFrom(c)
                    && !c.isAnnotationPresent(Serializer.class)) {
                out.add(c.getName());
            }
        } catch (Throwable t) {
            // Unloadable in this environment: cannot be a silent clause-3 gadget reachable here.
        }
    }
}
