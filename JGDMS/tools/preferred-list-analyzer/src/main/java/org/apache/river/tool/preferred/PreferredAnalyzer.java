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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Orchestrates preferred-class analysis of a compiled module: scans every
 * {@code .class} entry (from a directory tree or a JAR), gathers
 * {@link ClassSignals}, resolves the transitive {@code Serializable} set, and
 * classifies each class with the {@link DecisionEngine}.
 *
 * <p>This is the pure analysis core (no Maven, no I/O side effects beyond
 * reading the input).  Override application, {@code PREFERRED.LIST} emission and
 * reporting are layered on top by {@link OverrideFile}, {@link PreferredList}
 * and {@link AnalysisReport}.
 */
public final class PreferredAnalyzer {

    private final AnalyzerConfig config;
    private final ClassSignalScanner scanner;
    private final DecisionEngine engine;

    public PreferredAnalyzer(AnalyzerConfig config) {
        if (config == null) throw new NullPointerException("config");
        this.config  = config;
        this.scanner = new ClassSignalScanner(config);
        this.engine  = new DecisionEngine(config);
    }

    public PreferredAnalyzer() {
        this(AnalyzerConfig.defaults());
    }

    /**
     * Analyzes a compiled-classes directory tree (e.g.
     * {@code target/classes}).
     *
     * @param classesDir the root directory; must exist and be a directory
     * @return the per-class decisions, sorted by internal name; never null
     * @throws IOException if the directory cannot be read
     */
    public List<ClassDecision> analyzeDirectory(File classesDir) throws IOException {
        if (classesDir == null || !classesDir.isDirectory()) {
            throw new IOException("Not a directory: " + classesDir);
        }
        Map<String, byte[]> classBytes = new LinkedHashMap<String, byte[]>();
        collectClassFiles(classesDir, classesDir, classBytes);
        return analyze(classBytes);
    }

    /**
     * Analyzes a JAR's {@code .class} entries.
     *
     * @param jar the JAR file
     * @return the per-class decisions, sorted by internal name; never null
     * @throws IOException if the JAR cannot be read
     */
    public List<ClassDecision> analyzeJar(File jar) throws IOException {
        Map<String, byte[]> classBytes = new LinkedHashMap<String, byte[]>();
        FileInputStream fis = new FileInputStream(jar);
        try {
            JarInputStream jis = new JarInputStream(fis);
            try {
                JarEntry e;
                while ((e = jis.getNextJarEntry()) != null) {
                    String n = e.getName();
                    if (n.endsWith(".class")) {
                        classBytes.put(n.substring(0, n.length() - ".class".length()),
                                readAll(jis));
                    }
                }
            } finally {
                jis.close();
            }
        } finally {
            fis.close();
        }
        return analyze(classBytes);
    }

    /**
     * Analyzes an explicit set of class bytes (key = internal name).  Useful for
     * tests with synthetic fixtures.
     *
     * @param classBytes map of internal class name to {@code .class} bytes
     * @return the per-class decisions, sorted by internal name; never null
     */
    public List<ClassDecision> analyze(Map<String, byte[]> classBytes) {
        // Phase 1: scan all classes.
        Map<String, ClassSignals> signalsByName =
                new LinkedHashMap<String, ClassSignals>(classBytes.size());
        for (Map.Entry<String, byte[]> e : classBytes.entrySet()) {
            ClassSignals s = scanner.scan(e.getValue());
            // prefer the ASM-derived name; fall back to the map key
            String key = s.getInternalName();
            if (key == null || key.startsWith("<")) key = e.getKey();
            signalsByName.put(key, s);
        }

        // Phase 2: resolve the transitive Serializable set across the scanned types.
        Set<String> serializable = computeSerializable(signalsByName);

        // Phase 3: classify.
        List<ClassDecision> out = new ArrayList<ClassDecision>(signalsByName.size());
        for (ClassSignals s : signalsByName.values()) {
            boolean unresolvedSuper = hasUnresolvedSupertype(s, signalsByName);
            out.add(engine.classify(s, serializable, unresolvedSuper));
        }
        Collections.sort(out, new Comparator<ClassDecision>() {
            public int compare(ClassDecision a, ClassDecision b) {
                return a.getInternalName().compareTo(b.getInternalName());
            }
        });
        return out;
    }

    /**
     * Computes the set of internal names that are {@code Serializable}, either
     * directly or transitively through superclasses / superinterfaces that are
     * present in the analyzed set.  Types outside the analyzed set are treated as
     * non-serializable unless they declare it directly (we cannot see their
     * supertypes).
     */
    static Set<String> computeSerializable(Map<String, ClassSignals> signalsByName) {
        Set<String> serializable = new HashSet<String>();
        // seed with directly-declared
        for (ClassSignals s : signalsByName.values()) {
            if (s.implementsSerializableDirectly()) {
                serializable.add(s.getInternalName());
            }
        }
        // fixpoint over super/interface edges within the analyzed set
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ClassSignals s : signalsByName.values()) {
                String name = s.getInternalName();
                if (serializable.contains(name)) continue;
                if (isSerializableVia(s, serializable)) {
                    serializable.add(name);
                    changed = true;
                }
            }
        }
        return serializable;
    }

    private static boolean isSerializableVia(ClassSignals s, Set<String> serializable) {
        if ("java/io/Serializable".equals(s.getSuperName())) return true;
        if (s.getSuperName() != null && serializable.contains(s.getSuperName())) return true;
        for (String iface : s.getInterfaces()) {
            if ("java/io/Serializable".equals(iface)) return true;
            if (serializable.contains(iface)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if walking the class's superclass chain reaches a
     * class that is <em>not</em> in the analyzed set before reaching
     * {@code java.lang.Object} &mdash; i.e. an ancestor we could not inspect, so
     * its {@code Serializable} status (and hence the class's cross-boundary
     * status) cannot be confirmed.  Interfaces are not followed here: wire value
     * types reach {@code Serializable} through their superclass chain (or declare
     * it directly, which {@link #computeSerializable} already catches).
     *
     * <p>This is a conservative safety net for the cross-boundary heuristic; the
     * fuller fix (resolving supertypes on the classpath) is warranted before the
     * tool is pointed at the {@code -dl}/proxy modules.
     */
    static boolean hasUnresolvedSupertype(ClassSignals s,
                                          Map<String, ClassSignals> signalsByName) {
        String sup = s.getSuperName();
        int guard = 0;
        while (sup != null && !"java/lang/Object".equals(sup)) {
            if (++guard > 4096) break;            // pathological-cycle safety
            ClassSignals parent = signalsByName.get(sup);
            if (parent == null) return true;      // ancestor outside the analyzed set
            sup = parent.getSuperName();
        }
        return false;
    }

    private static void collectClassFiles(File root, File dir, Map<String, byte[]> out)
            throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectClassFiles(root, f, out);
            } else if (f.getName().endsWith(".class")
                    && !f.getName().equals("module-info.class")
                    && !f.getName().equals("package-info.class")) {
                String rel = root.toURI().relativize(f.toURI()).getPath();
                String internal = rel.substring(0, rel.length() - ".class".length());
                FileInputStream fis = new FileInputStream(f);
                try {
                    out.put(internal, readAll(fis));
                } finally {
                    fis.close();
                }
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(8192);
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }
}
