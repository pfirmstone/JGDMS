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
package org.apache.river.tool.serial;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Tracks serialization-schema evolution across releases.
 *
 * <p>Two axes (see {@code docs/JGDMS-API-Compatibility-Accepted-Breaks.md}):
 * <ul>
 *   <li><b>Entry mode &mdash; GATE.</b> For {@code @SerialEntry} classes the
 *       64-bit registrar type hash is obtained from the <i>runtime</i>
 *       {@code org.apache.river.reggie.proxy.ClassMapper} (so it is
 *       bit-identical to the registrar, including the superclass-hash chain).
 *       A hash change between the golden file and the current build is a
 *       breaking change (stored entries become invisible &mdash; STD-005
 *       RULE-7) and fails the build under {@code --fail-on-change}.</li>
 *   <li><b>{@code @AtomicSerial} mode &mdash; INFORMATIVE.</b> ASM extracts the
 *       {@code serialForm()} / {@code entryForm()} (name,type) schema; field
 *       add/remove/retype is reported but never fails the build (the
 *       {@code @AtomicSerial} schema is designed to evolve via defaulted
 *       {@code GetArg.get}).</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   generate --golden &lt;file&gt; --scan &lt;classesDir&gt; [--scan &lt;dir&gt;]...
 *   check    --golden &lt;file&gt; --scan &lt;classesDir&gt; [--scan &lt;dir&gt;]... [--fail-on-change]
 * </pre>
 * Run with the scanned classes <i>and</i> {@code reggie-dl} (plus their
 * dependencies) on the classpath; the Entry hash is computed reflectively.
 */
public final class SerialSchemaTracker {

    private static final String SERIAL_ENTRY  = "Lnet/jini/core/entry/SerialEntry;";
    private static final String ATOMIC_SERIAL = "Lorg/apache/river/api/io/AtomicSerial;";

    enum Kind { ENTRY, ATOMIC }

    static final class Schema {
        final String fqcn;
        Kind kind;
        final List<String> fields = new ArrayList<String>();
        long hash;
        boolean hasHash;
        String note = "";
        Schema(String fqcn) { this.fqcn = fqcn; }
    }

    // ---------------------------------------------------------------- ASM scan

    static final class Scan extends ClassVisitor {
        boolean isEntry, isAtomic;
        final List<String> fields = new ArrayList<String>();
        Scan() { super(Opcodes.ASM9); }
        @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (SERIAL_ENTRY.equals(desc))       isEntry = true;
            else if (ATOMIC_SERIAL.equals(desc)) isAtomic = true;
            return null;
        }
        @Override public MethodVisitor visitMethod(int a, String name, String desc,
                                                   String sig, String[] ex) {
            boolean ef = "entryForm".equals(name)  && desc.endsWith("Lnet/jini/core/entry/EntryWireField;");
            boolean sf = "serialForm".equals(name) && desc.contains("SerialForm;");
            return (ef || sf) ? new FormReader(fields) : null;
        }
    }

    /** Captures (name,type) pairs from {@code new EntryWireField(..)/new SerialForm(..)}. */
    static final class FormReader extends MethodVisitor {
        final List<String> out;
        String pendName, pendType;
        FormReader(List<String> out) { super(Opcodes.ASM9); this.out = out; }
        @Override public void visitLdcInsn(Object cst) {
            if (cst instanceof String)      pendName = (String) cst;
            else if (cst instanceof Type)   pendType = typeName((Type) cst);
        }
        @Override public void visitFieldInsn(int op, String owner, String name, String desc) {
            if (op == Opcodes.GETSTATIC && "TYPE".equals(name)) pendType = primName(owner);
        }
        @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            if (op == Opcodes.INVOKESPECIAL && "<init>".equals(name)
                    && (owner.endsWith("EntryWireField") || owner.endsWith("SerialForm"))) {
                if (pendName != null) out.add(pendName + ":" + (pendType == null ? "?" : pendType));
                pendName = null; pendType = null;
            }
        }
    }

    static String typeName(Type t) {
        if (t.getSort() == Type.ARRAY) return t.getDescriptor().replace('/', '.'); // Class.getName() form
        return t.getClassName();
    }
    static String primName(String wrapperOwner) {
        switch (wrapperOwner) {
            case "java/lang/Integer":   return "int";
            case "java/lang/Long":      return "long";
            case "java/lang/Boolean":   return "boolean";
            case "java/lang/Byte":      return "byte";
            case "java/lang/Character": return "char";
            case "java/lang/Short":     return "short";
            case "java/lang/Float":     return "float";
            case "java/lang/Double":    return "double";
            case "java/lang/Void":      return "void";
            default:                    return "?";
        }
    }

    // ----------------------------------------------- registrar hash (runtime)

    private static Method toEntryClassBase;
    private static Field eclassField, hashField;

    /** The registrar's 64-bit @SerialEntry type hash, via runtime ClassMapper. */
    static long registrarHash(Class<?> c) throws Exception {
        if (toEntryClassBase == null) {
            Class<?> cm = Class.forName("org.apache.river.reggie.proxy.ClassMapper");
            toEntryClassBase = cm.getDeclaredMethod("toEntryClassBase", Class.class);
            toEntryClassBase.setAccessible(true);
        }
        Object ecb = toEntryClassBase.invoke(null, c);
        if (eclassField == null) {
            eclassField = ecb.getClass().getDeclaredField("eclass");
            eclassField.setAccessible(true);
        }
        Object ec = eclassField.get(ecb);
        if (hashField == null) {
            hashField = ec.getClass().getDeclaredField("hash");
            hashField.setAccessible(true);
        }
        return hashField.getLong(ec);
    }

    // ----------------------------------------------------------------- driver

    /**
     * Caps the class-file major version so an older ASM can parse newer JDK
     * bytecode. Safe here: we read only annotations and constant operands,
     * which are version-independent. (JDK 25 = major 69; ASM 9.7.1 reads <= 67.)
     */
    static void capClassVersion(byte[] b) {
        if (b.length < 8) return;
        int major = ((b[6] & 0xFF) << 8) | (b[7] & 0xFF);
        if (major > 67) { b[6] = 0; b[7] = 52; }   // present as Java 8 for parsing
    }

    static Map<String, Schema> scan(List<Path> dirs) throws IOException {
        Map<String, Schema> out = new TreeMap<String, Schema>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            final List<Path> classFiles = new ArrayList<Path>();
            Files.walk(dir).filter(p -> p.toString().endsWith(".class")).forEach(classFiles::add);
            for (Path p : classFiles) {
                byte[] b = Files.readAllBytes(p);
                capClassVersion(b);   // tolerate JDK-newer-than-ASM bytecode (we read only structure)
                ClassReader cr;
                try {
                    cr = new ClassReader(b);
                } catch (RuntimeException e) {
                    System.err.println("  (skip unreadable " + p + ": " + e.getMessage() + ")");
                    continue;
                }
                Scan s = new Scan();
                // Keep method code (no SKIP_CODE): FormReader needs the instruction stream.
                cr.accept(s, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                if (!s.isEntry && !s.isAtomic) continue;
                Schema sch = new Schema(cr.getClassName().replace('/', '.'));
                sch.kind = s.isEntry ? Kind.ENTRY : Kind.ATOMIC;
                sch.fields.addAll(s.fields);
                out.put(sch.fqcn, sch);
            }
        }
        return out;
    }

    static void resolveHashes(Map<String, Schema> m) {
        for (Schema s : m.values()) {
            if (s.kind != Kind.ENTRY) continue;
            try {
                s.hash = registrarHash(Class.forName(s.fqcn));
                s.hasHash = true;
            } catch (Throwable t) {
                s.note = "no-registrar-hash (" + rootMsg(t) + ")";
            }
        }
    }

    static String rootMsg(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) r = r.getCause();
        return r.getClass().getSimpleName() + (r.getMessage() != null ? ": " + r.getMessage() : "");
    }

    static String line(Schema s) {
        String fields = String.join(", ", s.fields);
        if (s.kind == Kind.ENTRY) {
            return "ENTRY  " + s.fqcn + "  " + (s.hasHash ? Long.toString(s.hash) : "?") + "  | " + fields;
        }
        return "ATOMIC " + s.fqcn + "  -  | " + fields;
    }

    static void writeGolden(Path f, Map<String, Schema> m) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# JGDMS serial-schema golden file. ENTRY hash is the registrar type hash (STD-005 RULE-7).\n");
        for (Schema s : m.values()) sb.append(line(s)).append('\n');
        Files.write(f, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, String> readGolden(Path f) throws IOException {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (!Files.exists(f)) return out;
        for (String ln : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (ln.startsWith("#") || ln.trim().isEmpty()) continue;
            String[] t = ln.trim().split("\\s+", 4);   // KIND fqcn hash | fields...
            if (t.length >= 3) out.put(t[1], ln.trim());
        }
        return out;
    }

    static String hashOf(String goldenLine) {
        String[] t = goldenLine.split("\\s+", 4);
        return t.length >= 3 ? t[2] : "?";
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "";
        Path golden = null;
        List<Path> scan = new ArrayList<Path>();
        boolean failOnChange = false;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--golden":        golden = Paths.get(args[++i]); break;
                case "--scan":          scan.add(Paths.get(args[++i])); break;
                case "--fail-on-change": failOnChange = true; break;
                default: System.err.println("unknown arg: " + args[i]); System.exit(2);
            }
        }
        if (golden == null || scan.isEmpty() || (!"generate".equals(mode) && !"check".equals(mode))) {
            System.err.println("usage: (generate|check) --golden <file> --scan <dir> [--scan <dir>]... [--fail-on-change]");
            System.exit(2);
        }

        Map<String, Schema> cur = scan(scan);
        resolveHashes(cur);

        if ("generate".equals(mode)) {
            writeGolden(golden, cur);
            int entries = 0, atomic = 0;
            for (Schema s : cur.values()) { if (s.kind == Kind.ENTRY) entries++; else atomic++; }
            System.out.println("Wrote " + golden + ": " + entries + " @SerialEntry, " + atomic + " @AtomicSerial.");
            System.exit(0);
        }

        // check
        Map<String, String> old = readGolden(golden);
        TreeSet<String> all = new TreeSet<String>();
        all.addAll(old.keySet());
        for (String k : cur.keySet()) all.add(k);

        int gateFailures = 0, infoChanges = 0;
        for (String fqcn : all) {
            String o = old.get(fqcn);
            Schema c = cur.get(fqcn);
            boolean isEntry = (c != null && c.kind == Kind.ENTRY) || (o != null && o.startsWith("ENTRY"));
            if (o == null) {
                System.out.println("  + ADDED   " + fqcn + (isEntry ? " (new @SerialEntry — ok)" : " (new @AtomicSerial)"));
            } else if (c == null) {
                System.out.println("  - REMOVED " + fqcn + (isEntry ? "  *** ENTRY removed" : ""));
                if (isEntry) gateFailures++;
            } else {
                String oldHash = hashOf(o);
                String newHash = c.hasHash ? Long.toString(c.hash) : "?";
                String oldFields = o.contains("|") ? o.substring(o.indexOf('|') + 1).trim() : "";
                String newFields = String.join(", ", c.fields);
                if (c.kind == Kind.ENTRY && !oldHash.equals(newHash)) {
                    System.out.println("  ! HASH    " + fqcn + "  " + oldHash + " -> " + newHash
                            + "  *** BREAKING (stored entries invisible)  [" + oldFields + " -> " + newFields + "]");
                    gateFailures++;
                } else if (!oldFields.equals(newFields)) {
                    System.out.println("  ~ SCHEMA  " + fqcn + "  [" + oldFields + " -> " + newFields + "]"
                            + (c.kind == Kind.ENTRY ? "" : "  (informative)"));
                    infoChanges++;
                }
            }
        }
        for (Schema s : cur.values()) if (s.kind == Kind.ENTRY && !s.hasHash)
            System.out.println("  ? NOHASH  " + s.fqcn + "  " + s.note);

        System.out.println();
        System.out.println("Summary: " + gateFailures + " breaking (gate), " + infoChanges + " informative schema change(s).");
        if (gateFailures > 0 && failOnChange) {
            System.out.println("GATE: FAIL");
            System.exit(1);
        }
        System.out.println("GATE: PASS");
        System.exit(0);
    }

    private SerialSchemaTracker() {}
}
