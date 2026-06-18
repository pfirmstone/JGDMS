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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test helpers for loading compiled fixture bytecode and running the analyzer. */
final class FixtureSupport {

    private FixtureSupport() { }

    static final String FIX = "org/apache/river/tool/preferred/fixtures/";

    /** Reads the {@code .class} bytes of a fixture (or any classpath class) by internal name. */
    static byte[] bytes(String internalName) {
        String res = "/" + internalName + ".class";
        InputStream in = FixtureSupport.class.getResourceAsStream(res);
        if (in == null) throw new IllegalStateException("resource not found: " + res);
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(4096);
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
            return buf.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    /** Analyzes the given fixtures (by simple name under the fixtures package). */
    static Map<String, ClassDecision> analyzeFixtures(AnalyzerConfig config,
                                                      String... simpleNames) {
        Map<String, byte[]> classes = new LinkedHashMap<String, byte[]>();
        for (String simple : simpleNames) {
            String internal = FIX + simple;
            classes.put(internal, bytes(internal));
        }
        List<ClassDecision> ds = new PreferredAnalyzer(config).analyze(classes);
        Map<String, ClassDecision> byName = new LinkedHashMap<String, ClassDecision>();
        for (ClassDecision d : ds) byName.put(d.getInternalName(), d);
        return byName;
    }

    static ClassDecision decision(Map<String, ClassDecision> m, String simpleName) {
        return m.get(FIX + simpleName);
    }

    static boolean hasKind(ClassDecision d, HazardKind kind) {
        for (Hazard h : d.getHazards()) if (h.getKind() == kind) return true;
        return false;
    }
}
