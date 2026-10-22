/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.api.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Detects drift between this module's {@code org.apache.river.api.security}
 * classes and the copies a JDK (DirtyChai) bundles in {@code java.base}.
 * <p>
 * On such a JDK the bundled copy is what runs, unless it is patched over, so
 * code compiled here against this module's copy meets the JDK's: a public
 * or protected member that only this module's copy has is a
 * {@code NoSuchMethodError} or {@code NoSuchFieldError} waiting for a call,
 * and a class that has changed its kind, its superclass or its interfaces
 * can break callers as well.  This test compares the two, by their class
 * files, and fails on what the JDK's copy lacks or has differently, unless
 * it is listed in {@code conformance/bundled-security-divergences.txt}, one
 * divergence and the reason it is accepted per line.  What only the JDK's
 * copy has is reported, not failed.
 * <p>
 * The JDK's copy is read from the runtime image ({@code jrt:/}), which shows
 * the classes of the image whatever has been patched into the module, or from
 * a directory of class files named by the system property
 * {@value #JDK_CLASSES}.  The test is skipped when the JDK has no copy of the
 * package.  This module's copy is read from the class files it was built into.
 * <p>
 * Only public and protected members are compared; the others cannot be
 * reached from another runtime package, which is the position of the classes
 * of this package that the JDK does not bundle.  Private nested classes are
 * not compared.
 */
public class BundledCopyConformanceTest {

    /** A directory holding {@code org/apache/river/api/security/*.class}, to be used instead of the runtime image. */
    static final String JDK_CLASSES = "jgdms.conformance.jdkClasses";

    private static final String PACKAGE = "org/apache/river/api/security/";

    private static final String ALLOWED = "conformance/bundled-security-divergences.txt";

    /**
     * The classes of the package that DirtyChai bundles: they are copies,
     * and a JDK without one has diverged.  Classes of the package that are
     * not listed are this module's own and are not compared, unless the
     * JDK has one of the same name.
     */
    private static final String[] BUNDLED = {
        "CertificateGrant", "ClassLoaderGrant", "DigestDomainGrant", "DigestGrant",
        "PermissionDelegate", "PermissionGrant", "PermissionGrantBuilder",
        "PermissionGrantBuilderImp", "PermissionGrantBuilderImp$NullPermissionGrant",
        "PrincipalGrant", "ProtectionDomainGrant", "ScalableNestedPolicy", "URIGrant",
        "UnresolvedPrincipal"
    };

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_PRIVATE = 0x0002;
    private static final int ACC_PROTECTED = 0x0004;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_FINAL = 0x0010;
    private static final int ACC_INTERFACE = 0x0200;
    private static final int ACC_ABSTRACT = 0x0400;
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final int ACC_ANNOTATION = 0x2000;
    private static final int ACC_ENUM = 0x4000;

    /** The access flags of a class that are part of its contract. */
    private static final int CLASS_MASK = ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_STATIC
            | ACC_FINAL | ACC_INTERFACE | ACC_ABSTRACT | ACC_ANNOTATION | ACC_ENUM;

    /** The access flags of a member that are part of its contract. */
    private static final int MEMBER_MASK = ACC_PUBLIC | ACC_PROTECTED | ACC_STATIC | ACC_FINAL | ACC_ABSTRACT;

    @Test
    public void jdkCopyConformsToThisModulesCopy() throws Exception {
        Map<String, byte[]> jdk = jdkClasses();
        Assume.assumeFalse("The runtime has no copy of org.apache.river.api.security", jdk.isEmpty());
        Map<String, byte[]> own = ownClasses();
        assertFalse("This module's classes of org.apache.river.api.security were not found", own.isEmpty());

        Set<String> bundled = new LinkedHashSet<String>();
        for (String b : BUNDLED) bundled.add(PACKAGE + b);
        Map<String, ClassShape> jdkShapes = new TreeMap<String, ClassShape>();
        for (Map.Entry<String, byte[]> e : jdk.entrySet()) {
            jdkShapes.put(e.getKey(), ClassShape.parse(e.getValue()));
        }
        List<Divergence> divergences = new ArrayList<Divergence>();
        List<String> report = new ArrayList<String>();
        Set<String> compared = new TreeSet<String>();
        for (String name : own.keySet()) {
            byte[] theirs = jdk.get(name);
            if (theirs == null) {
                if (bundled.contains(name)) {
                    divergences.add(new Divergence(name, "missing", "", "the JDK has no such class"));
                }
                continue;
            }
            compared.add(name);
            compare(name, ClassShape.parse(own.get(name)), jdkShapes, divergences, report);
        }
        Set<String> jdkOnly = new TreeSet<String>(jdk.keySet());
        jdkOnly.removeAll(own.keySet());
        for (String name : jdkOnly) report.add("only in the JDK: " + dotted(name));
        Set<String> ownOnly = new TreeSet<String>(own.keySet());
        ownOnly.removeAll(jdk.keySet());
        ownOnly.removeAll(bundled);
        report.add(ownOnly.size() + " classes only in this module, which the JDK is not expected to bundle,"
                + " not compared");

        Map<String, String> allowed = allowed();
        Set<String> used = new TreeSet<String>();
        Map<String, List<Divergence>> failures = new TreeMap<String, List<Divergence>>();
        for (Divergence d : divergences) {
            if (allowed.containsKey(d.id())) {
                used.add(d.id());
            } else {
                List<Divergence> l = failures.get(d.name);
                if (l == null) failures.put(d.name, l = new ArrayList<Divergence>());
                l.add(d);
            }
        }
        Set<String> unused = new TreeSet<String>(allowed.keySet());
        unused.removeAll(used);
        for (String id : unused) report.add("allowed, but no longer a divergence: " + id);
        System.out.println("BundledCopyConformanceTest: " + compared.size() + " classes compared, "
                + divergences.size() + " divergences, " + used.size() + " allowed");
        for (String line : report) System.out.println("  " + line);

        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("The JDK's copy of org.apache.river.api.security lacks or differs from this module's in:");
            int count = 0;
            for (Map.Entry<String, List<Divergence>> e : failures.entrySet()) {
                sb.append("\n ").append(dotted(e.getKey()));
                for (Divergence d : e.getValue()) {
                    sb.append("\n    ").append(d.kind).append(' ').append(d.member);
                    if (!d.detail.isEmpty()) sb.append("  (").append(d.detail).append(')');
                    count++;
                }
            }
            sb.append("\n").append(count).append(" divergences. Each is a call that can fail where the JDK's copy runs;")
              .append(" decide each, and if it is accepted list it in ").append(ALLOWED)
              .append(" as 'class kind member | reason'.");
            fail(sb.toString());
        }
    }

    /** The class file reader sees what reflection does, in a class that has all kinds of members. */
    @Test
    public void classFileReaderAgreesWithReflection() throws Exception {
        Class<?> c = java.security.CodeSource.class;
        byte[] bytes;
        try (InputStream in = ClassLoader.getSystemResourceAsStream("java/security/CodeSource.class")) {
            bytes = in.readAllBytes();
        }
        ClassShape shape = ClassShape.parse(bytes);
        Set<String> expected = new TreeSet<String>();
        for (java.lang.reflect.Constructor<?> k : c.getDeclaredConstructors()) {
            if (isContract(k.getModifiers())) {
                expected.add("constructor <init>" + java.lang.invoke.MethodType.methodType(void.class,
                        k.getParameterTypes()).toMethodDescriptorString());
            }
        }
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (isContract(m.getModifiers()) && !m.isSynthetic()) {
                expected.add("method " + m.getName() + java.lang.invoke.MethodType.methodType(m.getReturnType(),
                        m.getParameterTypes()).toMethodDescriptorString());
            }
        }
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (isContract(f.getModifiers()) && !f.isSynthetic()) {
                expected.add("field " + f.getName() + f.getType().descriptorString());
            }
        }
        assertFalse(expected.isEmpty());
        assertEquals(expected, new TreeSet<String>(shape.members.keySet()));
        assertEquals(c.getModifiers() & CLASS_MASK, shape.flags);
        assertEquals(c.getSuperclass().getName(), dotted(shape.superName));
        assertEquals(java.io.ObjectStreamClass.lookup(c).getSerialVersionUID(), shape.serialVersionUID.longValue());
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            Member parsed = shape.members.get("method " + m.getName() + java.lang.invoke.MethodType.methodType(
                    m.getReturnType(), m.getParameterTypes()).toMethodDescriptorString());
            if (parsed != null) {
                assertEquals(m.getName(), m.getModifiers() & MEMBER_MASK, parsed.flags);
            }
        }
    }

    private static boolean isContract(int modifiers) {
        return (modifiers & (ACC_PUBLIC | ACC_PROTECTED)) != 0;
    }

    // ---- the comparison ----------------------------------------------------

    private static void compare(String name, ClassShape own, Map<String, ClassShape> jdkShapes,
            List<Divergence> out, List<String> report) {
        ClassShape jdk = jdkShapes.get(name);
        // A private nested class is an implementation detail of its outer class.
        if ((own.flags & ACC_PRIVATE) != 0 && (jdk.flags & ACC_PRIVATE) != 0) return;
        if (own.flags != jdk.flags) {
            out.add(new Divergence(name, "flags", "", "this module: " + flags(own.flags, CLASS_MASK)
                    + ", JDK: " + flags(jdk.flags, CLASS_MASK)));
        }
        if (!eq(own.superName, jdk.superName)) {
            out.add(new Divergence(name, "superclass", "", "this module: " + dotted(own.superName)
                    + ", JDK: " + dotted(jdk.superName)));
        }
        for (String i : own.interfaces) {
            if (!jdk.interfaces.contains(i)) {
                out.add(new Divergence(name, "interface", dotted(i), "the JDK's copy does not implement it"));
            }
        }
        for (String i : jdk.interfaces) {
            if (!own.interfaces.contains(i)) report.add(dotted(name) + ": only the JDK's copy implements " + dotted(i));
        }
        if (!eq(own.serialVersionUID, jdk.serialVersionUID)) {
            out.add(new Divergence(name, "serialVersionUID", "", "this module: " + own.serialVersionUID
                    + ", JDK: " + jdk.serialVersionUID));
        }
        for (Map.Entry<String, Member> e : own.members.entrySet()) {
            Member m = e.getValue();
            Member other = jdk.members.get(e.getKey());
            if (other == null && !m.kind.equals("constructor") && inherited(jdk, e.getKey(), jdkShapes)) {
                // A caller links to it through a superclass.
                report.add(dotted(name) + ": " + m.kind + " " + m.signature() + " is inherited in the JDK's copy");
            } else if (other == null) {
                out.add(new Divergence(name, m.kind, m.signature(),
                        "the JDK's copy has no such " + m.kind + ", " + flags(m.flags, MEMBER_MASK)));
            } else if (m.flags != other.flags) {
                out.add(new Divergence(name, m.kind + "-modifiers", m.signature(),
                        "this module: " + flags(m.flags, MEMBER_MASK) + ", JDK: " + flags(other.flags, MEMBER_MASK)));
            }
        }
        for (Map.Entry<String, Member> e : jdk.members.entrySet()) {
            if (!own.members.containsKey(e.getKey())) {
                report.add(dotted(name) + ": only in the JDK's copy: " + e.getValue().kind + " "
                        + e.getValue().signature());
            }
        }
    }

    /** Whether a superclass of the JDK's class, that is in the package, has the member. */
    private static boolean inherited(ClassShape jdk, String key, Map<String, ClassShape> jdkShapes) {
        Set<String> seen = new LinkedHashSet<String>();
        ClassShape c = jdk;
        while (c != null && c.superName != null && seen.add(c.superName)) {
            c = jdkShapes.get(c.superName);
            if (c != null && c.members.containsKey(key)) return true;
        }
        return false;
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String dotted(String internalName) {
        return internalName == null ? "null" : internalName.replace('/', '.');
    }

    private static String flags(int flags, int mask) {
        StringBuilder sb = new StringBuilder("[");
        String[] names = {"public", "private", "protected", "static", "final", "interface",
            "abstract", "annotation", "enum"};
        int[] bits = {ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_STATIC, ACC_FINAL, ACC_INTERFACE,
            ACC_ABSTRACT, ACC_ANNOTATION, ACC_ENUM};
        for (int i = 0; i < bits.length; i++) {
            if ((flags & mask & bits[i]) != 0) {
                if (sb.length() > 1) sb.append(' ');
                sb.append(names[i]);
            }
        }
        return sb.append(']').toString();
    }

    /** A difference between the copies, as the accepted list names it. */
    private static final class Divergence {
        final String name;
        final String kind;
        final String member;
        final String detail;

        Divergence(String name, String kind, String member, String detail) {
            this.name = name;
            this.kind = kind;
            this.member = member;
            this.detail = detail;
        }

        /** What an entry of the accepted list is: the class, the kind and the member. */
        String id() {
            return (dotted(name) + " " + kind + " " + member).trim();
        }
    }

    private static Map<String, String> allowed() throws IOException {
        Map<String, String> allowed = new LinkedHashMap<String, String>();
        InputStream in = BundledCopyConformanceTest.class.getClassLoader().getResourceAsStream(ALLOWED);
        if (in == null) return allowed;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null;) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int bar = line.indexOf(" | ");
                assertTrue("An accepted divergence needs a reason, 'divergence | reason': " + line, bar > 0
                        && line.substring(bar + 3).trim().length() > 0);
                allowed.put(line.substring(0, bar).trim(), line.substring(bar + 3).trim());
            }
        }
        return allowed;
    }

    // ---- where the class files are -----------------------------------------

    /** The JDK's copy, keyed by internal name. */
    private static Map<String, byte[]> jdkClasses() throws IOException {
        String dir = System.getProperty(JDK_CLASSES);
        Path path;
        if (dir != null && !dir.trim().isEmpty()) {
            path = Paths.get(dir.trim()).resolve(PACKAGE);
        } else {
            FileSystem jrt;
            try {
                jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
            } catch (RuntimeException e) {
                return Collections.emptyMap();
            }
            path = jrt.getPath("/modules/java.base/" + PACKAGE);
        }
        Map<String, byte[]> classes = new TreeMap<String, byte[]>();
        if (!Files.isDirectory(path)) return classes;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(path, "*.class")) {
            for (Path p : ds) {
                String file = p.getFileName().toString();
                classes.put(PACKAGE + file.substring(0, file.length() - ".class".length()),
                        Files.readAllBytes(p));
            }
        }
        return classes;
    }

    /**
     * This module's copy, keyed by internal name, from where it was built,
     * found by a class of the package that is not patched into java.base.
     */
    private static Map<String, byte[]> ownClasses() throws Exception {
        URL location = DefaultPolicyScanner.class.getProtectionDomain().getCodeSource().getLocation();
        Map<String, byte[]> classes = new TreeMap<String, byte[]>();
        File where = new File(location.toURI());
        if (where.isDirectory()) {
            File[] files = new File(where, PACKAGE).listFiles();
            if (files != null) {
                for (File f : files) {
                    String file = f.getName();
                    if (file.endsWith(".class")) {
                        classes.put(PACKAGE + file.substring(0, file.length() - ".class".length()),
                                Files.readAllBytes(f.toPath()));
                    }
                }
            }
        } else {
            try (JarFile jar = new JarFile(where)) {
                for (Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements();) {
                    JarEntry entry = en.nextElement();
                    String n = entry.getName();
                    if (n.startsWith(PACKAGE) && n.endsWith(".class")
                            && n.indexOf('/', PACKAGE.length()) < 0) {
                        try (InputStream in = jar.getInputStream(entry)) {
                            classes.put(n.substring(0, n.length() - ".class".length()), in.readAllBytes());
                        }
                    }
                }
            }
        }
        return classes;
    }

    // ---- class files -------------------------------------------------------

    private static final class Member {
        final String kind;      // constructor, method or field
        final String name;
        final String descriptor;
        final int flags;

        Member(String kind, String name, String descriptor, int flags) {
            this.kind = kind;
            this.name = name;
            this.descriptor = descriptor;
            this.flags = flags;
        }

        String key() {
            return kind + " " + name + descriptor;
        }

        /** In the form of source: {@code name(parameter types) : result}, or {@code name : type}. */
        String signature() {
            if (kind.equals("field")) {
                return name + " : " + type(descriptor, new int[]{0});
            }
            int close = descriptor.indexOf(')');
            StringBuilder sb = new StringBuilder(kind.equals("constructor") ? "<init>" : name).append('(');
            int[] pos = {1};
            boolean first = true;
            while (pos[0] < close) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(type(descriptor, pos));
            }
            sb.append(')');
            if (!kind.equals("constructor")) {
                sb.append(" : ").append(type(descriptor, new int[]{close + 1}));
            }
            return sb.toString();
        }

        private static String type(String d, int[] pos) {
            int dims = 0;
            while (d.charAt(pos[0]) == '[') {
                dims++;
                pos[0]++;
            }
            char c = d.charAt(pos[0]++);
            String t;
            switch (c) {
                case 'B': t = "byte"; break;
                case 'C': t = "char"; break;
                case 'D': t = "double"; break;
                case 'F': t = "float"; break;
                case 'I': t = "int"; break;
                case 'J': t = "long"; break;
                case 'S': t = "short"; break;
                case 'Z': t = "boolean"; break;
                case 'V': t = "void"; break;
                default:
                    int end = d.indexOf(';', pos[0]);
                    t = d.substring(pos[0], end).replace('/', '.');
                    pos[0] = end + 1;
            }
            StringBuilder sb = new StringBuilder(t);
            for (int i = 0; i < dims; i++) sb.append("[]");
            return sb.toString();
        }
    }

    /** What the comparison needs of a class file. */
    private static final class ClassShape {
        int flags;
        String superName;
        final List<String> interfaces = new ArrayList<String>();
        Long serialVersionUID;
        /** The public and protected constructors, methods and fields. */
        final Map<String, Member> members = new LinkedHashMap<String, Member>();

        static ClassShape parse(byte[] bytes) {
            try {
                return parse(new DataInputStream(new ByteArrayInputStream(bytes)));
            } catch (IOException e) {
                throw new IllegalArgumentException("Not a class file", e);
            }
        }

        private static ClassShape parse(DataInputStream in) throws IOException {
            if (in.readInt() != 0xCAFEBABE) throw new IOException("bad magic");
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            Object[] pool = new Object[count];    // Utf8 as String, Class as Integer (name index), Long as Long
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1: pool[i] = in.readUTF(); break;
                    case 3: case 4: in.readInt(); break;
                    case 5: pool[i] = in.readLong(); i++; break;
                    case 6: in.readLong(); i++; break;
                    case 7: pool[i] = Integer.valueOf(in.readUnsignedShort()); break;
                    case 8: case 16: case 19: case 20: in.readUnsignedShort(); break;
                    case 9: case 10: case 11: case 12: case 17: case 18: in.readInt(); break;
                    case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                    default: throw new IOException("constant pool tag " + tag);
                }
            }
            ClassShape c = new ClassShape();
            int access = in.readUnsignedShort();
            String self = className(pool, in.readUnsignedShort());
            int sup = in.readUnsignedShort();
            c.superName = sup == 0 ? null : className(pool, sup);
            int n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) c.interfaces.add(className(pool, in.readUnsignedShort()));
            n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                int f = in.readUnsignedShort();
                String name = (String) pool[in.readUnsignedShort()];
                String desc = (String) pool[in.readUnsignedShort()];
                Object constant = attributes(in, pool, null);
                if (name.equals("serialVersionUID") && desc.equals("J") && constant instanceof Long) {
                    c.serialVersionUID = (Long) constant;
                }
                c.add(new Member("field", name, desc, f));
            }
            n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                int f = in.readUnsignedShort();
                String name = (String) pool[in.readUnsignedShort()];
                String desc = (String) pool[in.readUnsignedShort()];
                attributes(in, pool, null);
                if (name.equals("<clinit>")) continue;
                c.add(new Member(name.equals("<init>") ? "constructor" : "method", name, desc, f));
            }
            int[] inner = {-1};
            attributes(in, pool, new Object[]{self, inner});
            // A nested class has its own access flags.
            c.flags = (inner[0] >= 0 ? inner[0] : access) & CLASS_MASK;
            return c;
        }

        private void add(Member m) {
            if ((m.flags & ACC_SYNTHETIC) != 0) return;
            if ((m.flags & (ACC_PUBLIC | ACC_PROTECTED)) == 0) return;
            members.put(m.key(), new Member(m.kind, m.name, m.descriptor, m.flags & MEMBER_MASK));
        }

        private static String className(Object[] pool, int index) {
            return (String) pool[((Integer) pool[index]).intValue()];
        }

        /**
         * Reads the attributes, returning the ConstantValue of a field.  Of a class
         * the access flags of the class itself in its InnerClasses attribute are
         * stored, where {@code classAttributes} is {self, int[1]}.
         */
        private static Object attributes(DataInputStream in, Object[] pool, Object[] classAttributes)
                throws IOException {
            Object constant = null;
            int n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                String name = (String) pool[in.readUnsignedShort()];
                int length = in.readInt();
                if (name.equals("ConstantValue") && length == 2) {
                    constant = pool[in.readUnsignedShort()];
                } else if (name.equals("InnerClasses") && classAttributes != null) {
                    int m = in.readUnsignedShort();
                    for (int j = 0; j < m; j++) {
                        int inner = in.readUnsignedShort();
                        in.readUnsignedShort();
                        in.readUnsignedShort();
                        int f = in.readUnsignedShort();
                        if (className(pool, inner).equals(classAttributes[0])) {
                            ((int[]) classAttributes[1])[0] = f;
                        }
                    }
                } else {
                    in.skipNBytes(length);
                }
            }
            return constant;
        }
    }
}
