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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Post-processor that runs after a policy-update QA test run.
 *
 * <p>When the QA harness is run with
 * {@code -Djava.security.manager=org.apache.river.tool.SecurityPolicyWriter},
 * every test JVM appends any permissions it needed to its
 * {@code java.security.policy} file.  This includes policy files that are
 * intentionally restrictive so that tests expecting a {@code SecurityException}
 * will pass.  If those files were updated they would grant the very permissions
 * whose absence the test relies on, breaking the test on the next normal run.
 *
 * <p>This tool:
 * <ol>
 *   <li>Reads the exclusion set – the basenames of policy files that must not
 *       be modified – from two sources:
 *       <ul>
 *         <li>{@code <qa-dir>/harness/policy/policy-update-exclusions.properties}
 *             (hard-coded list maintained by humans), and</li>
 *         <li>every {@code .td} file under {@code <qa-dir>/src} that contains
 *             the property {@code policy.no.update=true} (per-test annotation).
 *             The value of {@code testPolicyfile} in those files is added to
 *             the exclusion set.</li>
 *       </ul>
 *   </li>
 *   <li>Restores every excluded policy file that was found under the qa
 *       directory tree to its VCS-tracked state using {@code git checkout --}
 *       (so that SecurityPolicyWriter's writes to them are undone).</li>
 *   <li>Runs {@link PolicyCondenser} on every other {@code .policy} file,
 *       replacing the original with the condensed result in-place.</li>
 * </ol>
 *
 * <p><b>Usage</b> (Ant / command line):
 * <pre>
 *   java -cp policy-condenser.jar:jgdms-platform.jar \
 *        org.apache.river.tool.PolicyUpdatePostProcessor \
 *        --qa-dir /path/to/qa
 * </pre>
 *
 * <p>In {@code qa/build.xml} this is invoked by the {@code policy-update}
 * target, which is activated by {@code ant -Dpolicy.update=true policy-update}.
 *
 * @author Peter Firmstone
 * @since 3.1.1
 */
public class PolicyUpdatePostProcessor {

    private static final Logger LOG =
            Logger.getLogger(PolicyUpdatePostProcessor.class.getName());

    /**
     * Entry point.
     *
     * @param args {@code --qa-dir <path>}
     */
    public static void main(String[] args) throws Exception {
        String qaDir = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--qa-dir".equals(args[i])) {
                qaDir = args[i + 1];
            }
        }
        if (qaDir == null) {
            System.err.println("Usage: PolicyUpdatePostProcessor --qa-dir <path-to-qa-directory>");
            System.exit(1);
        }

        new PolicyUpdatePostProcessor(new File(qaDir)).run();
    }

    private final File qaDir;

    PolicyUpdatePostProcessor(File qaDir) {
        this.qaDir = qaDir;
    }

    void run() throws Exception {
        Set<String> excludedBasenames = collectExcludedBasenames();
        LOG.log(Level.INFO, "Excluded policy file basenames: {0}", excludedBasenames);

        List<File> allPolicyFiles = findFiles(qaDir, ".policy");
        LOG.log(Level.INFO, "Found {0} .policy files under {1}",
                new Object[]{allPolicyFiles.size(), qaDir});

        int restored = 0;
        int condensed = 0;
        int skipped = 0;

        for (File pf : allPolicyFiles) {
            String basename = pf.getName();
            if (excludedBasenames.contains(basename)) {
                if (gitRestore(pf)) {
                    restored++;
                } else {
                    LOG.log(Level.WARNING,
                            "Could not restore excluded policy file via git: {0}",
                            pf.getAbsolutePath());
                }
            } else {
                if (condenseInPlace(pf)) {
                    condensed++;
                } else {
                    skipped++;
                }
            }
        }

        LOG.log(Level.INFO,
                "Policy update complete: {0} restored, {1} condensed, {2} skipped/unchanged.",
                new Object[]{restored, condensed, skipped});
        System.out.println("Policy update complete: " + restored + " restored, "
                + condensed + " condensed, " + skipped + " skipped/unchanged.");
    }

    /**
     * Builds the set of excluded policy file basenames from:
     * <ul>
     *   <li>the hard-coded exclusions file
     *       {@code harness/policy/policy-update-exclusions.properties}, and</li>
     *   <li>the {@code testPolicyfile} property of every {@code .td} file
     *       that has {@code policy.no.update=true}.</li>
     * </ul>
     */
    private Set<String> collectExcludedBasenames() throws IOException {
        Set<String> basenames = new HashSet<String>();

        // 1. Hard-coded exclusions list.
        File exclusionsFile = new File(qaDir,
                "harness/policy/policy-update-exclusions.properties");
        if (exclusionsFile.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(exclusionsFile));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        basenames.add(line);
                    }
                }
            } finally {
                br.close();
            }
        } else {
            LOG.log(Level.WARNING,
                    "Exclusions file not found: {0}", exclusionsFile.getAbsolutePath());
        }

        // 2. Per-test .td annotations.
        File srcDir = new File(qaDir, "src");
        List<File> tdFiles = findFiles(srcDir, ".td");
        for (File td : tdFiles) {
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(td);
            try {
                props.load(fis);
            } finally {
                fis.close();
            }
            if ("true".equalsIgnoreCase(props.getProperty("policy.no.update"))) {
                String policyFile = props.getProperty("testPolicyfile");
                if (policyFile != null && !policyFile.isEmpty()) {
                    // Strip any <url:...> wrapper and take only the filename.
                    String cleaned = policyFile.trim();
                    if (cleaned.startsWith("<url:") && cleaned.endsWith(">")) {
                        cleaned = cleaned.substring(5, cleaned.length() - 1).trim();
                    }
                    String basename = new File(cleaned).getName();
                    if (!basename.isEmpty()) {
                        basenames.add(basename);
                    }
                }
            }
        }

        return basenames;
    }

    /**
     * Restores a single policy file to its VCS-tracked state using
     * {@code git checkout -- <file>}.
     *
     * @return {@code true} if git reported success, {@code false} otherwise
     */
    private boolean gitRestore(File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "checkout", "--", file.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                LOG.log(Level.WARNING,
                        "git checkout returned {0} for {1}: {2}",
                        new Object[]{exitCode, file.getAbsolutePath(), output});
                return false;
            }
            LOG.log(Level.FINE, "Restored (git checkout): {0}", file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to run git checkout for " + file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Condenses a policy file in-place by delegating to
     * {@link PolicyCondenser#main(String[])} which writes a {@code .con}
     * file, and then atomically replacing the original with the condensed
     * version.
     *
     * @return {@code true} if the file was condensed and replaced,
     *         {@code false} if the condensed file was not produced (error)
     */
    private boolean condenseInPlace(File policyFile) {
        File condensedFile = new File(policyFile.getAbsolutePath() + ".con");
        // Remove any leftover .con file from a previous run.
        if (condensedFile.exists()) {
            condensedFile.delete();
        }
        try {
            PolicyCondenser.main(new String[]{policyFile.getAbsolutePath()});
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "PolicyCondenser failed for " + policyFile.getAbsolutePath(), e);
            return false;
        }
        if (!condensedFile.exists()) {
            LOG.log(Level.WARNING,
                    "PolicyCondenser did not produce .con file for {0}",
                    policyFile.getAbsolutePath());
            return false;
        }
        try {
            Files.move(condensedFile.toPath(), policyFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            LOG.log(Level.FINE, "Condensed in-place: {0}", policyFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            // ATOMIC_MOVE may not be supported across filesystems; fall back.
            try {
                Files.copy(condensedFile.toPath(), policyFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                condensedFile.delete();
                LOG.log(Level.FINE, "Condensed in-place (copy): {0}",
                        policyFile.getAbsolutePath());
                return true;
            } catch (IOException e2) {
                LOG.log(Level.WARNING,
                        "Failed to replace " + policyFile.getAbsolutePath()
                        + " with condensed version", e2);
                return false;
            }
        }
    }

    /**
     * Recursively walks {@code root} and returns all files whose name ends
     * with {@code suffix}.
     */
    private static List<File> findFiles(File root, String suffix) {
        List<File> result = new ArrayList<File>();
        collectFiles(root, suffix, result);
        return result;
    }

    private static void collectFiles(File dir, String suffix, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectFiles(child, suffix, result);
            } else if (child.getName().endsWith(suffix)) {
                result.add(child);
            }
        }
    }
}
