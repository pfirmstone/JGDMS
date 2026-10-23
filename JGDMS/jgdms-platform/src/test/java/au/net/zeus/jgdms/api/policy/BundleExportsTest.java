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
 * The bundle manifest that the build generates for this module (bnd, in the
 * process-classes phase, which runs before the tests).  Other bundles import
 * this package (jgdms-jeri for {@link UserSubjectSupport}, jgdms-pref-class-loader
 * for the leased grants), so a framework resolves them only if this one
 * exports it.
 */
public class BundleExportsTest {

    private static Manifest manifest() throws Exception {
        Path classes = Path.of(UserSubjectSupport.class.getProtectionDomain()
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

    @Test
    public void testThePolicyPackageIsExported() throws Exception {
        String exports = manifest().getMainAttributes().getValue("Export-Package");
        Assume.assumeTrue("not a bundle manifest", exports != null);
        String name = UserSubjectSupport.class.getPackage().getName();
        List<String> names = new ArrayList<>();
        for (String clause : clauses(exports)) {
            names.add(clause.split(";")[0].trim());
        }
        Assert.assertTrue(name + " is not in Export-Package: " + names,
                          names.contains(name));
    }
}
