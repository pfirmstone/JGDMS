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

import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.reflect.*;
import java.nio.file.*; import java.lang.invoke.MethodType;
import java.util.*;

/**
 * Builds the directory that the dirtychai-emulation profile of jgdms-platform patches into java.base
 * (-Djgdms.dirtychai.patch=DIR): the org.apache.river.api.security classes of a compiled DirtyChai tree,
 * and the classes of that tree they need which the running JDK lacks or has without the member needed.
 * Usage: java tools/stubgen/EmulationPatchDir.java DC_CLASSES_DIR OUT_DIR
 *
 * Builds a patch-module directory from a DirtyChai class tree: the seeds, plus every class they need that the running JDK lacks or has without the needed member. */
public class EmulationPatchDir {
    public static void main(String[] a) throws Exception {
        Path dc = Path.of(a[0]), out = Path.of(a[1]);
        Deque<String> q = new ArrayDeque<>();
        try (var ds = Files.newDirectoryStream(dc.resolve("org/apache/river/api/security"), "*.class")) {
            for (Path p : ds) q.add("org/apache/river/api/security/" + p.getFileName().toString().replace(".class", ""));
        }
        Set<String> in = new TreeSet<>();
        Map<String,String> why = new TreeMap<>();
        while (!q.isEmpty()) {
            String n = q.poll();
            if (!in.add(n)) continue;
            byte[] b = Files.readAllBytes(dc.resolve(n + ".class"));
            { String base = n.substring(n.lastIndexOf('/') + 1);
              try (var ds2 = Files.newDirectoryStream(dc.resolve(n).getParent(), base + "$*.class")) {
                for (Path p : ds2) q.add(n.substring(0, n.lastIndexOf('/') + 1) + p.getFileName().toString().replace(".class", ""));
              } }
            ClassModel cm = ClassFile.of().parse(b);
            for (var e : cm.constantPool()) {
                if (e instanceof ClassEntry ce) {
                    String r = ce.asInternalName();
                    while (r.startsWith("[")) r = r.substring(1);
                    if (r.startsWith("L") && r.endsWith(";")) r = r.substring(1, r.length() - 1);
                    if (r.length() <= 1 || !Files.exists(dc.resolve(r + ".class"))) continue;
                    if (in.contains(r)) continue;
                    Class<?> k = boot(r);
                    if (k == null) { why.putIfAbsent(r, "absent from running JDK, needed by " + n); q.add(r); }
                } else if (e instanceof MemberRefEntry m) {
                    String o = m.owner().asInternalName();
                    if (o.startsWith("[") || in.contains(o) || !Files.exists(dc.resolve(o + ".class"))) continue;
                    Class<?> k = boot(o);
                    if (k == null) continue;
                    String nm = m.name().stringValue(), d = m.type().stringValue();
                    if (!has(k, nm, d, m instanceof FieldRefEntry)) {
                        why.putIfAbsent(o, "running JDK lacks " + nm + d + ", needed by " + n);
                        q.add(o);
                    }
                }
            }
        }
        for (String n : in) {
            Path t = out.resolve(n + ".class");
            Files.createDirectories(t.getParent());
            Files.copy(dc.resolve(n + ".class"), t, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println(in.size() + " classes");
        for (var e : why.entrySet()) System.out.println("  " + e.getKey() + "  <- " + e.getValue());
    }
    static Class<?> boot(String internal) {
        try { return Class.forName(internal.replace('/', '.'), false, null); } catch (Throwable t) { return null; }
    }
    static boolean has(Class<?> k, String nm, String d, boolean field) {
        if (k == null) return false;
        try {
            if (field) {
                for (Field f : k.getDeclaredFields()) if (f.getName().equals(nm)) return true;
            } else if (nm.equals("<init>")) {
                for (Constructor<?> c : k.getDeclaredConstructors()) if (desc(c.getParameterTypes(), void.class).equals(d)) return true;
                return false;
            } else {
                for (Method m : k.getDeclaredMethods()) if (m.getName().equals(nm) && desc(m.getParameterTypes(), m.getReturnType()).equals(d)) return true;
            }
        } catch (Throwable t) { return true; }
        if (k.isInterface() && !field && has(Object.class, nm, d, false)) return true;
        if (has(k.getSuperclass(), nm, d, field)) return true;
        for (Class<?> i : k.getInterfaces()) if (has(i, nm, d, field)) return true;
        return false;
    }
    static String desc(Class<?>[] p, Class<?> r) { return MethodType.methodType(r, p).toMethodDescriptorString(); }
}
