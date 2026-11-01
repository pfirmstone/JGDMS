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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Runs {@link PolicyLint} in process and reads its output; shared by the lint tests. */
final class LintHarness {

    private LintHarness() {}

    /** One run: exit status and output. */
    static final class Result {
        int exit;
        String out;
        String err;

        /** The finding lines. */
        List<String> findings() {
            List<String> r = new ArrayList<String>();
            for (String l : out.split("\n")) {
                if (l.length() > 0 && !l.startsWith("SUMMARY") && !l.startsWith("TOTAL")
                        && !l.startsWith("#") && !l.startsWith("---")
                        && l.matches(".*:\\d+ [A-Z]\\d+ .*")) {
                    r.add(l);
                }
            }
            return r;
        }

        /** The kinds in output order (file, then line), e.g. "P1 U1". */
        String kinds() {
            StringBuilder sb = new StringBuilder();
            for (String l : findings()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(l.replaceAll("^.*:\\d+ ([A-Z]\\d+) .*$", "$1"));
            }
            return sb.toString();
        }

        int count(String kind) {
            int n = 0;
            for (String l : findings()) if (l.matches(".*:\\d+ " + kind + " .*")) n++;
            return n;
        }
    }

    static Result run(String... args) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        Result r = new Result();
        r.exit = PolicyLint.run(args, new PrintStream(o, true, "UTF-8"), new PrintStream(e, true, "UTF-8"));
        r.out = o.toString("UTF-8");
        r.err = e.toString("UTF-8");
        return r;
    }

    /** Copies a test resource to {@code dir/name} (parents created) and returns the file. */
    static File copyResource(String resource, File dir, String name) throws Exception {
        InputStream in = LintHarness.class.getResourceAsStream(resource);
        if (in == null) throw new IllegalStateException("missing test resource " + resource);
        try {
            File f = new File(dir, name);
            f.getParentFile().mkdirs();
            Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return f;
        } finally {
            in.close();
        }
    }
}
