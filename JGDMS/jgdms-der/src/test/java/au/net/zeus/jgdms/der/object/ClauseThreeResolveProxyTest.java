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

package au.net.zeus.jgdms.der.object;

import java.io.File;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.security.auth.x500.X500Principal;

import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.object.fixtures.ProbeValue;
import au.net.zeus.jgdms.der.object.fixtures.ResolveOnlyProbe;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.Resolve;
import org.apache.river.api.io.Serializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clause 3 of the decode-admission gate (a leaf that is {@code @AtomicSerial} + java.io
 * {@link Resolve} but NOT {@code @Serializer}) is the documented residual: its resolved type is
 * unknowable before construction, so it is admitted and its ctor runs. This test:
 * <ul>
 *   <li><b>R2-F2 (bounded):</b> proves the clause-3 residual is nonetheless bounded — a
 *       clause-3 value admitted into a wrong concrete-typed slot resolves to a value that is NOT
 *       an instance of the declared slot type, so the caller's typed {@code get()} cast rejects
 *       it (it cannot masquerade as the declared type). This is exactly the {@code
 *       DerProxySerializer} situation, additionally bounded at runtime by the codebase-download
 *       grant + integrity check in its {@code readResolve()}.</li>
 *   <li><b>R2-F3 (tripwire):</b> enumerates every {@code @AtomicSerial}+{@code Resolve}-not-
 *       {@code @Serializer} class reachable on the production (non-test) jgdms classpath and pins
 *       the known-inert set, so a NEW clause-3 class cannot silently enter the admission surface
 *       unreviewed.</li>
 * </ul>
 */
class ClauseThreeResolveProxyTest {

    @BeforeEach
    void resetProbe() {
        ResolveOnlyProbe.DECODE_CONSTRUCTED.set(false);
    }

    // =========================================================================
    // R2-F2 — clause-3 residual is bounded by the post-resolve typed cast.
    // =========================================================================

    @Test
    void clauseThreeAdmitsButResolvedValueCannotMasqueradeAsDeclaredType() throws Exception {
        byte[] wire = ObjectCodec.encodeNested(new ResolveOnlyProbe(), "x", 0);

        // Declared X500Principal; wire leaf ResolveOnlyProbe (Resolve, no @Serializer): clause 3
        // admits (ctor runs = residual), readResolve yields a ProbeValue.
        Object decoded = ObjectCodec.decodeNested(wire, X500Principal.class, 0, null,
                ResolutionContext.NONE);
        assertTrue(ResolveOnlyProbe.DECODE_CONSTRUCTED.get(),
                "clause 3 admits a Resolve proxy -> its ctor runs (documented residual)");
        // BOUNDED: the resolved value is NOT an X500Principal, so the outer caller's
        // arg.get(name, X500Principal.class) cast rejects it -- it cannot populate the slot.
        assertFalse(decoded instanceof X500Principal,
                "a clause-3 value must not be able to masquerade as the declared slot type");
        assertInstanceOf(ProbeValue.class, decoded);
    }

    @Test
    void clauseThreeAdmitsInBroadSlot() throws Exception {
        byte[] wire = ObjectCodec.encodeNested(new ResolveOnlyProbe(), "x", 0);
        Object decoded = ObjectCodec.decodeNested(wire, Object.class, 0, null,
                ResolutionContext.NONE);
        assertTrue(ResolveOnlyProbe.DECODE_CONSTRUCTED.get());
        assertInstanceOf(ProbeValue.class, decoded);
    }

    // =========================================================================
    // R2-F3 — tripwire: pin the clause-3 set reachable on the production classpath.
    // =========================================================================

    /**
     * Every {@code @AtomicSerial}+{@code Resolve}-not-{@code @Serializer} class the gate would
     * admit via clause 3 that is reachable on the production (non-test) jgdms classpath. Each has
     * been reviewed as inert / bounded:
     * <ul>
     *   <li>{@code net.jini.core.constraint.Integrity},
     *       {@code net.jini.core.constraint.ServerAuthentication} — immutable constraint constant
     *       carriers; {@code readResolve()} returns a canonical singleton, no external effect;</li>
     *   <li>{@code net.jini.id.UuidFactory$Impl} — a trivial {@code @AtomicSerial} concrete
     *       {@code Uuid} subclass (the base {@code Uuid} is {@code @Serializer}, so it is clause 2;
     *       {@code Impl} is not, so it is clause 3). Inert: an immutable 128-bit value whose
     *       inherited {@code Uuid.readResolve()} returns {@code this} — no external effect;</li>
     *   <li>{@code au.net.zeus.jgdms.der.object.DerProxySerializer} — the one ACTIVE
     *       {@code readResolve()} (builds a bootstrap {@code CodebaseAccessor} proxy +
     *       {@code provider.resolve()}); bounded by the codebase-download grant + integrity check
     *       and NOT newly reachable (it is the DER analogue of the existing JOSS proxy carrier).</li>
     * </ul>
     * (Phoenix {@code AID$State}/{@code ConstrainableAID$State} are also clause-3 but live in
     * phoenix-activation, which is not on the jgdms-der classpath, so they are not discovered here.)
     */
    private static final Set<String> PINNED_CLAUSE_THREE = new TreeSet<>(Set.of(
            "net.jini.core.constraint.Integrity",
            "net.jini.core.constraint.ServerAuthentication",
            "net.jini.id.UuidFactory$Impl",
            "au.net.zeus.jgdms.der.object.DerProxySerializer"
    ));

    @Test
    void noNewClauseThreeClassEntersUnreviewed() throws Exception {
        Set<String> discovered = discoverClauseThree();
        assertEquals(PINNED_CLAUSE_THREE, discovered,
                "A new @AtomicSerial+Resolve-not-@Serializer class became gate clause-3 reachable "
                + "on the production classpath. Every such class is a decode-time construction "
                + "surface (its ctor runs in a wrong-typed slot). REVIEW it for inertness/bounding, "
                + "then add it to PINNED_CLAUSE_THREE. Discovered=" + discovered
                + " Pinned=" + PINNED_CLAUSE_THREE);
    }

    // ---- classpath scan (production jgdms entries only) ------------------------------------

    private static Set<String> discoverClauseThree() throws Exception {
        Set<String> found = new TreeSet<>();
        ClassLoader loader = ClauseThreeResolveProxyTest.class.getClassLoader();
        String cp = System.getProperty("java.class.path", "");
        for (String entry : cp.split(File.pathSeparator)) {
            if (entry.isEmpty() || entry.contains("test-classes")) {
                continue; // production classes only
            }
            File f = new File(entry);
            if (f.isDirectory()) {
                if (!entry.contains("jgdms")) continue;
                scanDir(f.toPath(), f.toPath(), loader, found);
            } else if (f.isFile() && f.getName().endsWith(".jar")
                    && f.getName().contains("jgdms")
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
                 String rel = root.relativize(p).toString()
                         .replace(File.separatorChar, '.');
                 String name = rel.substring(0, rel.length() - ".class".length());
                 consider(name, loader, out);
             });
        }
    }

    private static void scanJar(File jar, ClassLoader loader, Set<String> out) throws Exception {
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class") || n.equals("module-info.class")
                        || n.contains("META-INF")) {
                    continue;
                }
                String name = n.substring(0, n.length() - ".class".length()).replace('/', '.');
                consider(name, loader, out);
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
            // Unloadable in this environment (missing transitive dep, link error): it cannot be a
            // silent clause-3 gadget reachable HERE. Best-effort tripwire over these two modules.
        }
    }
}
