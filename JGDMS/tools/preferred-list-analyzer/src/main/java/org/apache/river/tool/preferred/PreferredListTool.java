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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line front-end for the {@link PreferredAnalyzer}, with two subcommands
 * matching the SOW &sect;5 goals.
 *
 * <pre>
 *   generate &lt;classesDirOrJar&gt; [options]
 *       Analyze the module and write a META-INF/PREFERRED.LIST.
 *   check &lt;classesDirOrJar&gt; --list &lt;PREFERRED.LIST&gt; [options]
 *       Recompute and report drift against a checked-in list; with
 *       --fail-on-drift, exit non-zero when the list is out of date (a linter).
 *
 *   Options:
 *     --out &lt;file&gt;         where generate writes the list (default: stdout)
 *     --list &lt;file&gt;        the checked-in PREFERRED.LIST (check; required)
 *     --report &lt;file&gt;      write the human-readable analysis report here
 *     --overrides &lt;file&gt;   apply a preferred-overrides.txt over the analysis
 *     --default-prefer     emit a global Preferred: true default (rarely wanted)
 *     --prefer-on-a        let criterion (a) drive prefer (default: review only)
 *     --fail-on-drift      check exits 1 when drift is found (default: report only)
 * </pre>
 *
 * <p>If the classes path does not exist, both subcommands print a warning and
 * exit 0, so a build that wires {@code check} before the target module is
 * compiled degrades gracefully rather than failing.
 *
 * <p>Exit codes: {@code 0} success / no enforced drift; {@code 1} drift with
 * {@code --fail-on-drift}; {@code 2} usage or I/O error.
 */
public final class PreferredListTool {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private PreferredListTool() { }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (ToolException e) {
            System.err.println("preferred-list: " + e.getMessage());
            System.exit(e.code);
        } catch (IOException e) {
            System.err.println("preferred-list: I/O error: " + e.getMessage());
            System.exit(2);
        }
    }

    /** Runs the tool and returns the process exit code (testable entry point). */
    static int run(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
            return 2;
        }
        String cmd = args[0];
        Map<String, String> opts = new LinkedHashMap<String, String>();
        List<String> positional = new ArrayList<String>();
        parseArgs(args, opts, positional);

        if ("generate".equals(cmd)) {
            return generate(positional, opts);
        } else if ("check".equals(cmd)) {
            return check(positional, opts);
        } else {
            usage();
            return 2;
        }
    }

    // ------------------------------------------------------------------------

    private static int generate(List<String> positional, Map<String, String> opts)
            throws IOException {
        File classes = requireClasses(positional);
        if (classes == null) return 0; // graceful skip

        List<ClassDecision> decisions = analyze(classes, opts);
        OverrideFile.Result ov = applyOverrides(decisions, opts);

        boolean defaultPrefer = opts.containsKey("default-prefer");
        String listText = PreferredList.render(decisions, defaultPrefer, "1.0");

        String out = opts.get("out");
        if (out != null) {
            writeFile(new File(out), listText);
            System.out.println("preferred-list: wrote " + out
                    + " (" + countPreferred(decisions) + " preferred of "
                    + decisions.size() + ")");
        } else {
            System.out.print(listText);
        }
        writeReportIfRequested(decisions, ov, opts);
        return 0;
    }

    private static int check(List<String> positional, Map<String, String> opts)
            throws IOException {
        File classes = requireClasses(positional);
        if (classes == null) return 0; // graceful skip

        String listPath = opts.get("list");
        if (listPath == null) {
            throw new ToolException(2, "check requires --list <PREFERRED.LIST>");
        }
        File listFile = new File(listPath);
        if (!listFile.isFile()) {
            System.out.println("preferred-list: checked-in list not found, skipping: "
                    + listPath);
            return 0;
        }

        List<ClassDecision> decisions = analyze(classes, opts);
        OverrideFile.Result ov = applyOverrides(decisions, opts);
        writeReportIfRequested(decisions, ov, opts);

        PreferredList checkedIn = PreferredList.parse(
                new String(Files.readAllBytes(listFile.toPath()), UTF8));
        List<PreferredList.Drift> drift = checkedIn.diff(decisions);

        if (drift.isEmpty()) {
            System.out.println("preferred-list: OK -- " + listPath
                    + " is up to date (" + decisions.size() + " classes).");
            return 0;
        }

        boolean fail = opts.containsKey("fail-on-drift");
        System.out.println("preferred-list: " + (fail ? "DRIFT" : "drift")
                + " -- " + drift.size() + " class(es) differ from " + listPath + ":");
        int shown = 0;
        for (PreferredList.Drift d : drift) {
            System.out.println("    " + d.getResourceName()
                    + ": checked-in=" + d.getCheckedIn() + " computed=" + d.getComputed());
            if (++shown >= 50) {
                System.out.println("    ... and " + (drift.size() - shown) + " more");
                break;
            }
        }
        if (fail) {
            System.out.println("preferred-list: run 'generate' to refresh the list.");
            return 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------------

    private static List<ClassDecision> analyze(File classes, Map<String, String> opts)
            throws IOException {
        AnalyzerConfig cfg = opts.containsKey("prefer-on-a")
                ? AnalyzerConfig.builder().preferOnCriterionA(true).build()
                : AnalyzerConfig.defaults();
        PreferredAnalyzer an = new PreferredAnalyzer(cfg);
        if (classes.isDirectory()) {
            return an.analyzeDirectory(classes);
        }
        return an.analyzeJar(classes);
    }

    private static OverrideFile.Result applyOverrides(List<ClassDecision> decisions,
                                                      Map<String, String> opts)
            throws IOException {
        String ovPath = opts.get("overrides");
        if (ovPath == null) return null;
        File f = new File(ovPath);
        if (!f.isFile()) return null;
        OverrideFile of = OverrideFile.parse(
                new String(Files.readAllBytes(f.toPath()), UTF8));
        return of.apply(decisions);
    }

    private static void writeReportIfRequested(List<ClassDecision> decisions,
                                               OverrideFile.Result ov,
                                               Map<String, String> opts)
            throws IOException {
        String reportPath = opts.get("report");
        if (reportPath == null) return;
        writeFile(new File(reportPath), AnalysisReport.render(decisions, ov));
        System.out.println("preferred-list: wrote report " + reportPath);
    }

    /** Returns the classes file, or null (after a warning) if it doesn't exist. */
    private static File requireClasses(List<String> positional) {
        if (positional.isEmpty()) {
            throw new ToolException(2, "missing <classesDirOrJar> argument");
        }
        File f = new File(positional.get(0));
        if (!f.exists()) {
            System.out.println("preferred-list: classes path not found, skipping: " + f);
            return null;
        }
        return f;
    }

    private static int countPreferred(List<ClassDecision> ds) {
        int n = 0;
        for (ClassDecision d : ds) if (d.isPreferred()) n++;
        return n;
    }

    private static void writeFile(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        Files.write(f.toPath(), content.getBytes(UTF8));
    }

    /** Parses {@code --key value} and {@code --flag} options; the rest are positional. */
    private static void parseArgs(String[] args, Map<String, String> opts,
                                  List<String> positional) {
        // args[0] is the subcommand; start at 1
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                if (isFlag(key)) {
                    opts.put(key, "true");
                } else if (i + 1 < args.length) {
                    opts.put(key, args[++i]);
                } else {
                    opts.put(key, "");
                }
            } else {
                positional.add(a);
            }
        }
    }

    private static boolean isFlag(String key) {
        return "default-prefer".equals(key)
                || "prefer-on-a".equals(key)
                || "fail-on-drift".equals(key);
    }

    private static void usage() {
        System.err.println(
            "Usage:\n"
          + "  generate <classesDirOrJar> [--out <file>] [--report <file>]\n"
          + "           [--overrides <file>] [--default-prefer] [--prefer-on-a]\n"
          + "  check    <classesDirOrJar> --list <PREFERRED.LIST> [--report <file>]\n"
          + "           [--overrides <file>] [--prefer-on-a] [--fail-on-drift]\n");
    }

    /** Internal exception carrying a process exit code. */
    static final class ToolException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int code;
        ToolException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
