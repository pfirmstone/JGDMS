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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.UnresolvedPermission;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.security.auth.x500.X500Principal;

/**
 * Asks a production policy provider, not a text scan, what a policy file grants to code it should
 * not trust.
 *
 * <p>The subjects are code an attacker controls: a stranger's codebase on {@code http}, {@code https}
 * (with and without a port), {@code ftp}, a bare IP and {@code jar:}; the same code run by an
 * authenticated principal of an arbitrary class (an X500Principal, a class of this tool, and every
 * class the policy names); code from an arbitrary {@code file:} location; and code from an arbitrary
 * {@code jrt:} module.  The questions are the {@link PermissionSets#probes() dangerous-permission
 * probes} of the shared catalogue, AllPermission first.  A probe is granted if the provider's
 * {@code implies} says so, or if any element of the permission collection it returns for the
 * subject implies the probe (a permission class the catalogue does not know shows up that way).
 *
 * <p>Provider choice.  On the DirtyChai JDK the provider the security manager installs is
 * {@code au.zeus.jdk.authorization.policy.ConcurrentPolicyFile}; its package is not exported, so
 * it is reachable only with {@code --add-exports java.base/au.zeus.jdk.authorization.policy=
 * ALL-UNNAMED}.  Without it, or on a stock JDK, the tool falls back to
 * {@code org.apache.river.api.security.ConcurrentPolicyFile} of jgdms-platform, which is the same
 * source and was measured to give the same answers on the review fixtures
 * ({@code PolicyLintTest.providersAgreeOnFixtures}).  The stock
 * {@code sun.security.provider.PolicyFile} is never used: it disagrees with both on seven of the
 * review fixtures.  The provider class is reported in the output.
 *
 * <p>Both providers expand {@code ${property}} from {@link System#getProperties()}, so the caller's
 * definitions are set as system properties for the duration of one question and restored.
 */
final class PolicyVerdict {

    static final String DIRTYCHAI = "au.zeus.jdk.authorization.policy.ConcurrentPolicyFile";
    static final String PLATFORM = "org.apache.river.api.security.ConcurrentPolicyFile";

    static final String ZONE_STRANGER = "stranger";
    static final String ZONE_PRINCIPAL = "principal";
    static final String ZONE_FILE = "file";
    static final String ZONE_PLATFORM = "platform";

    /** Test seam: the class tried first for {@code auto} and {@code dirtychai}. */
    static volatile String dirtyChaiClass = DIRTYCHAI;

    /** Stranger codebases: every remote scheme and form an attacker can serve code from. */
    static final String[] STRANGER_URLS = {
        "http://evil.example.com/evil.jar",
        "https://evil.example.com/evil.jar",
        "https://evil.example.com:8443/a/b/evil.jar",
        "ftp://evil.example.com/evil.jar",
        "http://203.0.113.9:8080/evil.jar",
        "jar:http://evil.example.com/evil.jar!/"};
    private static final String[] PRINCIPAL_URLS = {
        "http://evil.example.com/evil.jar", "https://evil.example.com/evil.jar"};
    private static final String[] FILE_URLS = {
        "file:/lint-stranger/evil.jar", "jar:file:/lint-stranger/evil.jar!/"};
    private static final String PLATFORM_URL = "jrt:/lint.stranger.module";

    /** A principal of a class no policy names, for {@code principal * *}. */
    static final class StrangerPrincipal implements Principal {
        public String getName() {
            return "lint-stranger";
        }

        @Override
        public String toString() {
            return "StrangerPrincipal[lint-stranger]";
        }
    }

    /** One probe the provider granted to a subject of one zone. */
    static final class Hit {
        final String zone;
        /** The first subject (codebase and principal classes) that was granted the probe. */
        final String subject;
        final PermissionSets.Probe probe;

        Hit(String zone, String subject, PermissionSets.Probe probe) {
            this.zone = zone;
            this.subject = subject;
            this.probe = probe;
        }
    }

    /** The answers for one policy file. */
    static final class Answer {
        /** Null if the real parser loaded the file, else why it did not. */
        String loadError;
        /** AllPermission to code from any stranger codebase (every scheme), no principal. */
        boolean stranger;
        /** AllPermission to the same code run by an authenticated principal of any class. */
        boolean strangerPrincipal;
        /** AllPermission to code from an arbitrary file: location. */
        boolean fileCode;
        /** AllPermission to code from an arbitrary jrt: module. */
        boolean platformModule;
        /** Null CodeSource: not implied by design; reported, never a finding on its own. */
        boolean nullCodeSource;
        /** Every probe granted, by zone then catalogue order; AllPermission is a probe too. */
        final List<Hit> hits = new ArrayList<Hit>();
        /** Subjects that could not be built (a principal class that cannot be instantiated). */
        final List<String> notes = new ArrayList<String>();

        boolean hit(String zone, String probeId) {
            for (Hit h : hits) if (h.zone.equals(zone) && h.probe.id.equals(probeId)) return true;
            return false;
        }
    }

    final String providerClass;
    final String note;
    private final Constructor<?> constructor;
    private final Object parser;
    private final Method parse;

    private PolicyVerdict(String providerClass, String note, Constructor<?> constructor,
            Object parser, Method parse) {
        this.providerClass = providerClass;
        this.note = note;
        this.constructor = constructor;
        this.parser = parser;
        this.parse = parse;
    }

    /**
     * Opens a provider.  {@code mode} is {@code auto}, {@code dirtychai} or {@code platform}.
     *
     * @throws IllegalStateException if no provider of the requested kind can be used
     */
    static PolicyVerdict open(String mode) {
        String note = null;
        if (!"platform".equals(mode)) {
            try {
                return open(dirtyChaiClass, "production provider of the DirtyChai JDK");
            } catch (Throwable t) {
                if ("dirtychai".equals(mode)) {
                    throw new IllegalStateException(dirtyChaiClass + " cannot be used: " + why(t)
                            + " (needs --add-exports java.base/au.zeus.jdk.authorization.policy="
                            + "ALL-UNNAMED on the DirtyChai JDK)");
                }
                note = "fell back from " + dirtyChaiClass + ": " + why(t);
            }
        }
        try {
            return open(PLATFORM, note == null ? "provider of jgdms-platform" : note);
        } catch (Throwable t) {
            throw new IllegalStateException("no policy provider can be used: " + why(t));
        }
    }

    private static PolicyVerdict open(String className, String note) throws Exception {
        Class<?> c = Class.forName(className, true, PolicyVerdict.class.getClassLoader());
        if (!Policy.class.isAssignableFrom(c)) {
            throw new ClassCastException(className + " is not a java.security.Policy");
        }
        Constructor<?> k = c.getConstructor(URL[].class);
        String pkg = className.substring(0, className.lastIndexOf('.'));
        Class<?> pc = Class.forName(pkg + ".DefaultPolicyParser", true, c.getClassLoader());
        Object parser = pc.getConstructor().newInstance();
        Method parse = pc.getMethod("parse", URL.class, Properties.class);
        // Construct once on an empty list: reflection access errors surface here, not per file.
        k.newInstance(new Object[]{new URL[0]});
        return new PolicyVerdict(className, note, k, parser, parse);
    }

    private static String why(Throwable t) {
        Throwable c = t;
        while (c instanceof InvocationTargetException && c.getCause() != null) c = c.getCause();
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    /**
     * Loads {@code policy} with the provider and asks the questions.  {@code defines} are visible
     * to property expansion during the call and removed after it; {@code principalClasses} are
     * the principal classes the policy names, each tried as a subject if it can be instantiated;
     * {@code named} are probes for the dangerous permissions the file itself names, asked in
     * addition to the catalogue's.
     */
    synchronized Answer ask(File policy, Map<String, String> defines,
            Collection<String> principalClasses, List<PermissionSets.Probe> named) {
        Answer a = new Answer();
        Properties sys = System.getProperties();
        Map<String, String> saved = new HashMap<String, String>();
        Map<String, String> set = new HashMap<String, String>(defines);
        set.put("/", File.separator);
        for (Map.Entry<String, String> e : set.entrySet()) {
            saved.put(e.getKey(), sys.getProperty(e.getKey()));
            sys.setProperty(e.getKey(), e.getValue());
        }
        try {
            URL url = policy.toURI().toURL();
            try {
                parse.invoke(parser, url, sys);
            } catch (InvocationTargetException e) {
                a.loadError = why(e);
                return a;
            }
            Policy p;
            try {
                p = (Policy) constructor.newInstance(new Object[]{new URL[]{url}});
            } catch (InvocationTargetException e) {
                a.loadError = why(e);
                return a;
            }
            List<PermissionSets.Probe> probes = PermissionSets.probes();
            for (PermissionSets.Probe n : named) {
                boolean known = false;
                for (PermissionSets.Probe k : probes) known |= k.id.equals(n.id);
                if (!known) probes.add(n);
            }
            Principal[] none = new Principal[0];
            List<Principal> principals = principals(principalClasses, a.notes);
            for (String u : STRANGER_URLS) {
                collect(p, a, ZONE_STRANGER, u, source(u), none, probes);
            }
            for (String u : PRINCIPAL_URLS) {
                for (Principal pr : principals) {
                    collect(p, a, ZONE_PRINCIPAL, u + " as " + pr.getClass().getName(), source(u),
                            new Principal[]{pr}, probes);
                }
            }
            for (String u : FILE_URLS) {
                collect(p, a, ZONE_FILE, u, source(u), none, probes);
            }
            CodeSource jrt = source(PLATFORM_URL);
            if (jrt != null) collect(p, a, ZONE_PLATFORM, PLATFORM_URL, jrt, none, probes);
            a.stranger = a.hit(ZONE_STRANGER, "AllPermission");
            a.strangerPrincipal = a.hit(ZONE_PRINCIPAL, "AllPermission");
            a.fileCode = a.hit(ZONE_FILE, "AllPermission");
            a.platformModule = a.hit(ZONE_PLATFORM, "AllPermission");
            a.nullCodeSource = p.implies(new ProtectionDomain(null, null, null, none),
                    new AllPermission());
        } catch (Exception e) {
            a.loadError = why(e);
        } finally {
            for (Map.Entry<String, String> e : saved.entrySet()) {
                if (e.getValue() == null) sys.remove(e.getKey());
                else sys.setProperty(e.getKey(), e.getValue());
            }
        }
        return a;
    }

    /** Asks every probe of one subject; records the first subject of a zone granted a probe. */
    private static void collect(Policy p, Answer a, String zone, String label, CodeSource cs,
            Principal[] who, List<PermissionSets.Probe> probes) {
        if (cs == null) return; // e.g. no such URL handler on this JDK; the question is not asked
        ProtectionDomain pd = new ProtectionDomain(cs, null, null, who);
        List<Permission> granted = null;
        for (PermissionSets.Probe probe : probes) {
            if (a.hit(zone, probe.id)) continue;
            boolean yes = p.implies(pd, probe.permission);
            if (!yes) {
                if (granted == null) granted = elements(p, pd, a.notes);
                for (Permission g : granted) {
                    try {
                        if (g.implies(probe.permission)) {
                            yes = true;
                            break;
                        }
                    } catch (RuntimeException e) {
                        // a permission class that throws from implies is not trusted to say no
                        yes = true;
                        break;
                    }
                }
            }
            if (yes) a.hits.add(new Hit(zone, label, probe));
        }
    }

    /** The permissions the provider lists for a subject; empty if it does not list them. */
    private static List<Permission> elements(Policy p, ProtectionDomain pd, List<String> notes) {
        List<Permission> res = new ArrayList<Permission>();
        try {
            PermissionCollection pc = p.getPermissions(pd);
            if (pc != null) {
                for (Enumeration<Permission> en = pc.elements(); en.hasMoreElements();) {
                    Permission e = en.nextElement();
                    if (e instanceof UnresolvedPermission) {
                        // the provider resolves against the class loader of the checked class; the
                        // probe domains have none, so resolve with the lint's own loader
                        Permission r = resolve((UnresolvedPermission) e);
                        if (r == null) {
                            String why = "permission class " + ((UnresolvedPermission) e)
                                    .getUnresolvedType() + " is not loadable here: what it implies"
                                    + " cannot be judged";
                            if (!notes.contains(why)) notes.add(why);
                            continue;
                        }
                        e = r;
                    }
                    res.add(e);
                }
            }
        } catch (RuntimeException e) {
            // the provider does not list permissions for this subject; implies() was asked already
        }
        return res;
    }

    private static Permission resolve(UnresolvedPermission u) {
        try {
            Class<?> c = Class.forName(u.getUnresolvedType(), true, PolicyVerdict.class.getClassLoader());
            String n = u.getUnresolvedName();
            String act = u.getUnresolvedActions();
            boolean hasName = n != null && n.length() > 0;
            boolean hasActions = act != null && act.length() > 0;
            if (hasActions) {
                return (Permission) c.getConstructor(String.class, String.class).newInstance(n, act);
            }
            for (int i = 0; i < 3; i++) {
                try {
                    if (i == 0 && hasName) {
                        return (Permission) c.getConstructor(String.class).newInstance(n);
                    }
                    if (i == 1) return (Permission) c.getConstructor().newInstance();
                    if (i == 2) {
                        return (Permission) c.getConstructor(String.class, String.class)
                                .newInstance(n == null ? "" : n, act == null ? "" : act);
                    }
                } catch (NoSuchMethodException e) {
                    // try the next constructor shape
                }
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static List<Principal> principals(Collection<String> named, List<String> notes) {
        Map<Class<?>, Principal> res = new LinkedHashMap<Class<?>, Principal>();
        Principal x500 = new X500Principal("CN=lint-stranger");
        res.put(x500.getClass(), x500);
        Principal own = new StrangerPrincipal();
        res.put(own.getClass(), own);
        for (String cn : named == null ? Collections.<String>emptyList() : named) {
            if (cn == null || cn.equals("*")) continue;
            Principal made = null;
            try {
                Class<?> c = Class.forName(cn, true, PolicyVerdict.class.getClassLoader());
                if (!Principal.class.isAssignableFrom(c) || res.containsKey(c)) continue;
                for (String arg : new String[]{"lint-stranger", "CN=lint-stranger"}) {
                    try {
                        made = (Principal) c.getConstructor(String.class).newInstance(arg);
                        break;
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        // try the next form of the name
                    }
                }
                if (made == null) {
                    notes.add("principal class " + cn + " cannot be instantiated from a name");
                } else {
                    res.put(c, made);
                }
            } catch (ClassNotFoundException | LinkageError e) {
                notes.add("principal class " + cn + " is not loadable here");
            }
        }
        return new ArrayList<Principal>(res.values());
    }

    private static CodeSource source(String url) {
        try {
            return new CodeSource(new URL(url), (Certificate[]) null);
        } catch (MalformedURLException e) {
            return null; // e.g. no jrt: handler on this JDK; the question is then not asked
        }
    }
}
