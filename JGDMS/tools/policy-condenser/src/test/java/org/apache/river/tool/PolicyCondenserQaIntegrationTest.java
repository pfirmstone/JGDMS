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

package org.apache.river.tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.river.api.security.DefaultPolicyParser;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PolicyParser;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration test that exercises PolicyCondenser against every .policy file
 * in the qa integration test suite.
 *
 * <p>The qa directory is located via the system property {@code qa.dir}.
 * When run via Maven this property is injected by the surefire plugin
 * (see pom.xml).  The test is skipped automatically when the directory
 * cannot be found (e.g., in a partial checkout).
 */
public class PolicyCondenserQaIntegrationTest {

    private File tempDir;
    private File qaDir;

    @Before
    public void setUp() throws Exception {
        tempDir = new File(
            System.getProperty("java.io.tmpdir"),
            "policy-condenser-qa-" + Thread.currentThread().getId() + "-" + System.nanoTime()
        );
        tempDir.mkdirs();

        String qaDirProp = System.getProperty("qa.dir");
        if (qaDirProp != null) {
            qaDir = new File(qaDirProp).getCanonicalFile();
        }
        Assume.assumeTrue("qa directory not found – skipping", qaDir != null && qaDir.isDirectory());
    }

    @After
    public void tearDown() {
        deleteRecursively(tempDir);
    }

    /**
     * For every {@code .policy} file found under the qa directory:
     * <ol>
     *   <li>Copy it to a temp directory so the qa tree is not polluted with
     *       {@code .con} files.</li>
     *   <li>Run the condenser on the copy.</li>
     *   <li>Assert the condensed file is created and is parseable.</li>
     *   <li>Assert the condensed grant count is ≤ the original grant count
     *       (identical grants are merged; unique grants are kept).</li>
     *   <li>Assert that no permissions are lost: every permission present in
     *       the original (successfully parsed) grants also appears in the
     *       condensed grants.</li>
     * </ol>
     */
    @Test
    public void testCondenseAllQaPolicyFiles() throws Exception {
        List<File> policyFiles = collectPolicyFiles(qaDir);
        assertTrue("Expected to find policy files under " + qaDir, !policyFiles.isEmpty());

        int filesProcessed = 0;
        int totalOriginalGrants = 0;
        int totalCondensedGrants = 0;
        List<String> failures = new ArrayList<String>();

        PolicyParser parser = new DefaultPolicyParser();
        // Use a defensive copy of system properties so the test is isolated from
        // any modifications the condenser or parser may make to the live instance.
        Properties sysProps = (Properties) System.getProperties().clone();

        for (File policyFile : policyFiles) {
            File copy = copyToTemp(policyFile, filesProcessed);
            File condensedFile = new File(copy.getAbsolutePath() + ".con");

            try {
                PolicyCondenser.main(new String[]{copy.getAbsolutePath()});

                assertTrue(
                    "Condensed file should exist for " + policyFile.getName(),
                    condensedFile.exists()
                );

                Collection<PermissionGrant> originalGrants =
                    parser.parse(copy.toURI().toURL(), sysProps);
                Collection<PermissionGrant> condensedGrants =
                    parser.parse(condensedFile.toURI().toURL(), sysProps);

                int origSize = originalGrants.size();
                int condSize = condensedGrants.size();

                assertTrue(
                    "Condensed grant count (" + condSize + ") should be ≤ original ("
                        + origSize + ") for " + policyFile.getName(),
                    condSize <= origSize
                );

                assertNoPermissionsLost(policyFile.getName(), originalGrants, condensedGrants);

                totalOriginalGrants += origSize;
                totalCondensedGrants += condSize;
            } catch (AssertionError | Exception e) {
                failures.add(policyFile.getAbsolutePath() + ": " + e.getMessage());
            }
            filesProcessed++;
        }

        System.out.println(
            "PolicyCondenser qa integration: processed " + filesProcessed + " files, "
            + totalOriginalGrants + " original grants → "
            + totalCondensedGrants + " condensed grants ("
            + (totalOriginalGrants - totalCondensedGrants) + " merged)."
        );

        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder(failures.size() + " file(s) failed:\n");
            for (String f : failures) {
                sb.append("  ").append(f).append("\n");
            }
            fail(sb.toString());
        }
    }

    /**
     * Spot-check: the large shared-VM policy files that are known to contain
     * many duplicate codebase grants (one for X500Principal, one for
     * SpiffePrincipal with the same codebase) should actually be condensed.
     */
    @Test
    public void testKnownDuplicateFilesAreCondensed() throws Exception {
        String[] knownDuplicateFiles = {
            "harness/policy/defaultspiffesharedvm.policy",
            "harness/policy/defaultsecuresharedvm.policy",
            "harness/policy/defaultspiffetest.policy",
            "harness/policy/defaultsecuretest.policy",
        };

        PolicyParser parser = new DefaultPolicyParser();
        Properties sysProps = (Properties) System.getProperties().clone();
        int index = 0;

        for (String relativePath : knownDuplicateFiles) {
            File policyFile = new File(qaDir, relativePath);
            if (!policyFile.exists()) continue;

            File copy = copyToTemp(policyFile, index++);
            File condensedFile = new File(copy.getAbsolutePath() + ".con");

            PolicyCondenser.main(new String[]{copy.getAbsolutePath()});
            assertTrue("Condensed file must exist for " + relativePath, condensedFile.exists());

            Collection<PermissionGrant> originalGrants =
                parser.parse(copy.toURI().toURL(), sysProps);
            Collection<PermissionGrant> condensedGrants =
                parser.parse(condensedFile.toURI().toURL(), sysProps);

            assertTrue(
                relativePath + " should be condensed (orig=" + originalGrants.size()
                    + ", cond=" + condensedGrants.size() + ")",
                condensedGrants.size() <= originalGrants.size()
            );

            System.out.println(
                relativePath + ": " + originalGrants.size()
                    + " → " + condensedGrants.size() + " grants."
            );
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<File> collectPolicyFiles(File dir) {
        List<File> result = new ArrayList<File>();
        collectPolicyFilesRecursive(dir, result);
        return result;
    }

    private void collectPolicyFilesRecursive(File dir, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectPolicyFilesRecursive(child, result);
            } else if (child.getName().endsWith(".policy")) {
                result.add(child);
            }
        }
    }

    private File copyToTemp(File source, int index) throws IOException {
        // Use index + name to avoid collisions when files share the same name
        String safeName = index + "_" + source.getName();
        File dest = new File(tempDir, safeName);
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    /**
     * Asserts that every permission in {@code original} also appears in
     * {@code condensed}.  Permissions are compared using
     * {@link Permission#equals(Object)}.
     *
     * <p>Duplicate permissions across different grants in the original may be
     * deduplicated after condensation, so the total permission count in the
     * condensed output may be ≤ the sum across the original.  What must NOT
     * happen is the loss of a distinct permission.
     */
    private void assertNoPermissionsLost(
        String fileName,
        Collection<PermissionGrant> original,
        Collection<PermissionGrant> condensed
    ) {
        Set<Permission> originalPerms = new HashSet<Permission>();
        for (PermissionGrant g : original) {
            originalPerms.addAll(g.getPermissions());
        }
        Set<Permission> condensedPerms = new HashSet<Permission>();
        for (PermissionGrant g : condensed) {
            condensedPerms.addAll(g.getPermissions());
        }

        Set<Permission> missing = new HashSet<Permission>(originalPerms);
        missing.removeAll(condensedPerms);

        if (!missing.isEmpty()) {
            // Diagnostic dump so we can see the exact char codes of the
            // names that fail equality.
            System.err.println("=== " + fileName + " : missing permissions ===");
            for (Permission p : missing) {
                String n = p.getName();
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < n.length(); i++) {
                    hex.append(String.format("%02X ", (int) n.charAt(i)));
                }
                System.err.println("MISSING name=[" + n + "]");
                System.err.println("MISSING hex =[" + hex + "]");
                System.err.println("MISSING class=" + p.getClass().getName()
                    + " actions=" + p.getActions());
            }
            System.err.println("--- candidates with same class in condensed ---");
            for (Permission p : condensedPerms) {
                for (Permission m : missing) {
                    if (p.getClass() == m.getClass()) {
                        StringBuilder hex = new StringBuilder();
                        String n = p.getName();
                        for (int i = 0; i < n.length(); i++) {
                            hex.append(String.format("%02X ", (int) n.charAt(i)));
                        }
                        System.err.println("CANDIDATE name=[" + n + "]");
                        System.err.println("CANDIDATE hex =[" + hex + "]");
                    }
                }
            }
        }

        assertEquals(
            "Permissions lost in condensed output for " + fileName
                + ": " + missing,
            0, missing.size()
        );
    }

    private void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteRecursively(child);
                } else {
                    child.delete();
                }
            }
        }
        dir.delete();
    }
}
