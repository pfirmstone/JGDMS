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
import java.util.jar.Manifest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * The bundle manifest that the build generates for the module.
 */
public class BundleImportsTest {

    /**
     * {@code HandshakeLimits} asks the JVM for its descriptor limit through
     * {@code com.sun.management}, which not every JVM has, and does without
     * it: a framework that does not export the package must still resolve the
     * bundle.
     */
    @Test
    public void testTheManagementPackageIsAnOptionalImport() throws Exception {
        Path classes = Path.of(HandshakeLimits.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI());
        Path file = classes.resolve("META-INF/MANIFEST.MF");
        Assume.assumeTrue("the build did not generate a manifest here",
                          Files.isRegularFile(file));
        Manifest manifest;
        try (InputStream in = Files.newInputStream(file)) {
            manifest = new Manifest(in);
        }
        String imports = manifest.getMainAttributes().getValue("Import-Package");
        Assume.assumeTrue("not a bundle manifest", imports != null);
        Assert.assertTrue(imports, imports.contains("com.sun.management;resolution:=optional"));
        /* The others are still required. */
        Assert.assertTrue(imports, imports.contains("java.lang.management"));
        Assert.assertFalse(imports, imports.contains("java.lang.management;resolution:=optional"));
    }
}
