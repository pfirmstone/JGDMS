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
package net.jini.jeri.ssl;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * The bundle manifest that the build generates for the module (bnd, in the
 * process-classes phase, which runs before the tests).
 */
public class BundleImportsTest {

    /**
     * The packages that may be imported without being optional: the standard
     * Java SE ones, which every framework exports, and the project's own,
     * which come in the bundles of the project.  Anything else, such as
     * com.sun.management or sun.nio.ch, is specific to some JVMs and must be
     * optional (the code has to do without it), or a framework that does not
     * export it will not resolve the bundle.
     */
    private static final String[] ALLOWED_PREFIXES = {
        "java.", "javax.", "org.ietf.jgss",
        "au.net.zeus.", "net.jini.", "org.apache.river.",
    };

    private static Manifest manifest() throws Exception {
        Path classes = Path.of(HandshakeLimits.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI());
        Path file = classes.resolve("META-INF/MANIFEST.MF");
        Assume.assumeTrue("the build has not generated " + file
            + " (run through maven, which generates it before the tests)",
            Files.isRegularFile(file));
        try (InputStream in = Files.newInputStream(file)) {
            return new Manifest(in);
        }
    }

    /** Splits a list at the commas that are not inside quotes. */
    private static List<String> clauses(String list) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (char c : list.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            }
            if (c == ',' && !quoted) {
                out.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString().trim());
        return out;
    }

    private static List<String> imports() throws Exception {
        String imports = manifest().getMainAttributes().getValue("Import-Package");
        Assume.assumeTrue("not a bundle manifest", imports != null);
        return clauses(imports);
    }

    /** Whether the import is optional. */
    private static boolean optional(String clause) {
        return clause.contains(";resolution:=optional");
    }

    /**
     * {@code HandshakeLimits} asks the JVM for its descriptor limit through
     * {@code com.sun.management}, and the mux loads {@code sun.nio.ch}'s
     * IOVecWrapper by name; neither is in Java SE, and the code does without
     * them: a framework that does not export the packages must still resolve
     * the bundle.
     */
    @Test
    public void testTheJvmSpecificPackagesAreOptionalImports() throws Exception {
        List<String> imports = imports();
        for (String name : new String[] { "com.sun.management", "sun.nio.ch" }) {
            boolean found = false;
            for (String clause : imports) {
                if (clause.startsWith(name + ";") || clause.equals(name)) {
                    found = true;
                    Assert.assertTrue(name + " must be optional: " + clause,
                                      optional(clause));
                }
            }
            Assert.assertTrue(name + " is imported, as the code uses it: " + imports,
                              found);
        }
    }

    /** Every import that is not optional is a Java SE package or the project's. */
    @Test
    public void testNoOtherImportIsMandatoryOutsideJavaSeAndTheProject() throws Exception {
        List<String> bad = new ArrayList<>();
        for (String clause : imports()) {
            String name = clause.split(";")[0].trim();
            boolean allowed = false;
            for (String prefix : ALLOWED_PREFIXES) {
                allowed |= name.startsWith(prefix);
            }
            if (!allowed && !optional(clause)) {
                bad.add(clause);
            }
        }
        Assert.assertTrue("mandatory imports that are neither Java SE nor the "
            + "project's, nor optional: " + bad, bad.isEmpty());
    }
}
