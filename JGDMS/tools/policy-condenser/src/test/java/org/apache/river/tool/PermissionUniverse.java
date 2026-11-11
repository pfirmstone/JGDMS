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

import au.net.zeus.jgdms.api.policy.PermissionSets;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The questions of the provider cross-check, taken from the permission classes that exist, not from
 * the catalogue the lint applies: every non-abstract {@code java.security.Permission} class in the
 * JDK image (all modules) and in jgdms-platform, each built with every name and action string of a
 * bounded list through its public {@code (String)}, {@code (String, String)} or no-argument
 * constructor.  A class the catalogue has never heard of is asked about like any other, so a
 * catalogue miss cannot hide from the check that is meant to find it.
 */
final class PermissionUniverse {

    /** The names tried: wildcards, the names of the dangerous families, and the two-word forms. */
    static final String[] NAMES = {"*", "-", "<<ALL FILES>>", "*:*", "*.*", "ALLOW", "hard", "symbolic",
        "control", "monitor", "attachVirtualMachine", "createAttachProvider", "accessFlightRecorder",
        "registerEvent", "virtualMachineManager", "setLog", "setSyncFactory", "setOption.TCP_KEEPIDLE",
        "Arena", "\"*\" \"*\"", "* \"*\"", "* peer \"*\"", "* * \"*\""};

    /** The actions tried (an empty string and a null are both included through the one-argument form). */
    static final String[] ACTIONS = {"*", "read", "write", "read,write,execute,delete,readlink",
        "connect,accept,listen,resolve", "", "control", "read,write", "listen",
        "accept,connect,listen,resolve", "initiate", "accept", "initiate,accept", "connect",
        "connect,accept,listen"};

    /** Every non-abstract Permission class found, constructible or not. */
    final List<Class<?>> classes = new ArrayList<Class<?>>();
    /** The classes that gave at least one instance. */
    final List<Class<?>> constructible = new ArrayList<Class<?>>();
    /** The questions: one permission per class, name and actions. */
    final List<Permission> permissions = new ArrayList<Permission>();

    private PermissionUniverse() {
    }

    boolean has(String className) {
        for (Class<?> c : constructible) if (c.getName().equals(className)) return true;
        return false;
    }

    static PermissionUniverse build() throws Exception {
        Set<String> names = new LinkedHashSet<String>();
        FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path modules = jrt.getPath("/modules");
        Stream<Path> walk = Files.walk(modules);
        try {
            for (Path p : (Iterable<Path>) walk::iterator) {
                String f = String.valueOf(p.getFileName());
                if (!f.endsWith(".class") || !f.contains("Permission")) continue;
                String rel = modules.relativize(p).toString(); // module/pkg/Name.class
                names.add(rel.substring(rel.indexOf('/') + 1, rel.length() - 6).replace('/', '.'));
            }
        } finally {
            walk.close();
        }
        File platform = new File(PermissionSets.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        if (platform.isDirectory()) {
            Path root = platform.toPath();
            Stream<Path> w2 = Files.walk(root);
            try {
                for (Path p : (Iterable<Path>) w2::iterator) {
                    String f = String.valueOf(p.getFileName());
                    if (!f.endsWith(".class") || !f.contains("Permission")) continue;
                    String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
                    names.add(rel.substring(0, rel.length() - 6).replace('/', '.'));
                }
            } finally {
                w2.close();
            }
        } else {
            ZipFile z = new ZipFile(platform);
            try {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements();) {
                    String n = e.nextElement().getName();
                    if (n.endsWith(".class") && n.substring(n.lastIndexOf('/') + 1).contains("Permission")) {
                        names.add(n.substring(0, n.length() - 6).replace('/', '.'));
                    }
                }
            } finally {
                z.close();
            }
        }
        PermissionUniverse u = new PermissionUniverse();
        for (String n : names) {
            try {
                Class<?> c = Class.forName(n, false, PermissionUniverse.class.getClassLoader());
                if (Permission.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers())) {
                    u.classes.add(c);
                }
            } catch (Throwable t) {
                // not loadable here (a module that is not resolved, a missing dependency)
            }
        }
        for (Class<?> c : u.classes) {
            List<Permission> l = new ArrayList<Permission>();
            try {
                Constructor<?> k = c.getConstructor(String.class);
                for (String nm : NAMES) add(l, k, nm);
            } catch (Throwable t) {
                // no such constructor
            }
            try {
                Constructor<?> k = c.getConstructor(String.class, String.class);
                for (String nm : NAMES) for (String ac : ACTIONS) add(l, k, nm, ac);
            } catch (Throwable t) {
                // no such constructor
            }
            try {
                add(l, c.getConstructor());
            } catch (Throwable t) {
                // no such constructor
            }
            if (!l.isEmpty()) {
                u.constructible.add(c);
                u.permissions.addAll(l);
            }
        }
        return u;
    }

    private static void add(List<Permission> l, Constructor<?> k, Object... args) {
        try {
            l.add((Permission) k.newInstance(args));
        } catch (Throwable t) {
            // the constructor refuses this name or these actions
        }
    }
}
