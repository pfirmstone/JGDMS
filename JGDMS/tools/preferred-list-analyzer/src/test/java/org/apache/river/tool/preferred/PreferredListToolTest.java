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
package org.apache.river.tool.preferred;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Exercises the {@link PreferredListTool} CLI front-end and exit codes. */
public class PreferredListToolTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Writes a fixture class into a {@code classes} dir tree under temp. */
    private File classesDirWith(String... simpleNames) throws Exception {
        File dir = tmp.newFolder("classes");
        for (String simple : simpleNames) {
            String internal = FixtureSupport.FIX + simple;
            File f = new File(dir, internal + ".class");
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), FixtureSupport.bytes(internal));
        }
        return dir;
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));
    }

    @Test
    public void missingClassesSkipsGracefully() throws Exception {
        int code = PreferredListTool.run(new String[]{
                "check", new File(tmp.getRoot(), "nope").getPath(), "--list", "x"});
        assertEquals(0, code);
    }

    @Test
    public void generateWritesPreferredList() throws Exception {
        File classes = classesDirWith("SecureRandomField", "InstanceOnly");
        File out = new File(tmp.getRoot(), "PREFERRED.LIST");
        int code = PreferredListTool.run(new String[]{
                "generate", classes.getPath(), "--out", out.getPath()});
        assertEquals(0, code);
        String list = read(out);
        assertTrue(list.contains("Preferred: false")); // default
        assertTrue(list.contains(FixtureSupport.FIX + "SecureRandomField.class"));
        assertFalse(list.contains("InstanceOnly")); // shares -> not emitted
    }

    @Test
    public void checkPassesWhenListMatches() throws Exception {
        File classes = classesDirWith("SecureRandomField", "InstanceOnly");
        File list = new File(tmp.getRoot(), "PREFERRED.LIST");
        assertEquals(0, PreferredListTool.run(new String[]{
                "generate", classes.getPath(), "--out", list.getPath()}));
        // checking the freshly generated list against the same classes: no drift
        assertEquals(0, PreferredListTool.run(new String[]{
                "check", classes.getPath(), "--list", list.getPath(), "--fail-on-drift"}));
    }

    @Test
    public void checkFailsOnDriftOnlyWithFlag() throws Exception {
        File classes = classesDirWith("SecureRandomField");
        File staleList = new File(tmp.getRoot(), "stale.LIST");
        Files.write(staleList.toPath(),
                "Preferred: false\n".getBytes(Charset.forName("UTF-8"))); // omits the prefer

        // SecureRandomField should be preferred -> drift vs default-false list
        assertEquals("report-only by default", 0, PreferredListTool.run(new String[]{
                "check", classes.getPath(), "--list", staleList.getPath()}));
        assertEquals("fail-on-drift enforces", 1, PreferredListTool.run(new String[]{
                "check", classes.getPath(), "--list", staleList.getPath(), "--fail-on-drift"}));
    }

    @Test
    public void generateWritesReport() throws Exception {
        File classes = classesDirWith("SecureRandomField");
        File out = new File(tmp.getRoot(), "PREFERRED.LIST");
        File report = new File(tmp.getRoot(), "report.txt");
        assertEquals(0, PreferredListTool.run(new String[]{
                "generate", classes.getPath(),
                "--out", out.getPath(), "--report", report.getPath()}));
        String r = read(report);
        assertTrue(r.contains("Preferred-Class Analysis Report"));
        assertTrue(r.contains("PREFER"));
    }

    @Test
    public void unknownCommandIsUsageError() throws Exception {
        assertEquals(2, PreferredListTool.run(new String[]{"frobnicate"}));
        assertEquals(2, PreferredListTool.run(new String[]{}));
    }
}
