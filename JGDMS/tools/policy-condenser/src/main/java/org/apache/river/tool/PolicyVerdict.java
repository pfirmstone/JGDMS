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

    /**
     * One thing the provider granted to a subject of one zone: a catalogue probe, or a permission
     * it could not judge (see {@link #UNJUDGED}, {@link #THROWS}).
     */
    static final class Hit {
        final String zone;
        /** The first subject (codebase and principal classes) that was granted it. */
        final String subject;
        final String id;
        final int severity;
        final String reason;
        /** Class name of the permission asked about, or of the one that could not be judged. */
        final String permissionClass;
        /** The permission asked about; null for a hit that stands for a class that could not be judged. */
        final java.security.Permission permission;

        Hit(String zone, String subject, PermissionSets.Probe probe) {
            this(zone, subject, probe.id, probe.severity, probe.reason,
                    probe.permission.getClass().getName(), probe.permission);
        }

        Hit(String zone, String subject, String id, int severity, String reason,
                String permissionClass) {
            this(zone, subject, id, severity, reason, permissionClass, null);
        }

        private Hit(String zone, String subject, String id, int severity, String reason,
                String permissionClass, java.security.Permission permission) {
            this.permission = permission;
            this.zone = zone;
            this.subject = subject;
            this.id = id;
            this.severity = severity;
            this.reason = reason;
            this.permissionClass = permissionClass;
        }
    }

    /** Id prefix of a hit for a permission class that cannot be loaded or built here. */
    static final String UNJUDGED = "unjudgeable ";
    /** Id prefix of a hit for a permission whose {@code implies} threw. */
    static final String THROWS = "implies throws ";

    /** The answers for one policy file. */
    static final class Answer {
        /** Null if the real parser loaded the file, else why it did not. */
        String loadError;
        /**
         * True if {@link #loadError} is the provider's: the parser accepted the file and the
         * provider could not build a policy from it.
         */
        boolean providerOnly;
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
        /** Subjects built from a stand-in, and what was done where none could be built. */
        final List<String> notes = new ArrayList<String>();
        /** Principal classes named by the policy that no principal could be made of: fail closed. */
        final List<String> unbuildable = new ArrayList<String>();

        boolean hit(String zone, String probeId) {
            for (Hit h : hits) if (h.zone.equals(zone) && h.id.equals(probeId)) return true;
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
                a.providerOnly = true; // the parser above accepted this file
                return a;
            }
            List<PermissionSets.Probe> probes = PermissionSets.probes();
            for (PermissionSets.Probe n : named) {
                boolean known = false;
                for (PermissionSets.Probe k : probes) known |= k.id.equals(n.id);
                if (!known) probes.add(n);
            }
            Principal[] none = new Principal[0];
            List<Principal> principals = principals(principalClasses, a.notes, a.unbuildable);
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

    /** The provider could not answer: it cannot load the file, or a subject cannot be built. */
    static final class LoadFailure extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        LoadFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A question's subject: code from {@code url} run by {@code who}. */
    static final class Subject {
        final String url;
        final Principal[] who;

        Subject(String url, Principal[] who) {
            this.url = url;
            this.who = who;
        }
    }

    /**
     * Asks the provider, for subjects and permissions chosen by the caller, what a policy file
     * grants: which of {@code perms} are implied for code from {@code url} run by {@code who}.
     * This lets a test check the lint's verdict against questions the lint does not itself ask.
     *
     * @return the permissions granted
     * @throws LoadFailure if the provider cannot load the file: that is not "nothing granted"
     */
    synchronized List<Permission> grantedTo(File policy, Map<String, String> defines, String url,
            Principal[] who, List<Permission> perms) {
        return grantedToEach(policy, defines, Collections.singletonList(new Subject(url, who)),
                perms).get(0);
    }

    /**
     * As {@link #grantedTo}, for several subjects against one load of the file: element {@code i}
     * of the result is what the provider grants {@code subjects.get(i)}.
     *
     * @throws LoadFailure if the provider cannot load the file or a subject's URL has no handler
     */
    synchronized List<List<Permission>> grantedToEach(File policy, Map<String, String> defines,
            List<Subject> subjects, final List<Permission> perms) {
        return eachSubject(policy, defines, subjects, new Ask() {
            public List<Permission> of(Policy p, ProtectionDomain pd) {
                List<Permission> res = new ArrayList<Permission>();
                for (Permission perm : perms) {
                    try {
                        if (p.implies(pd, perm)) res.add(perm);
                    } catch (RuntimeException e) {
                        res.add(perm); // cannot tell: counted as granted
                    }
                }
                return res;
            }
        });
    }

    /**
     * What the provider lists as granted to each subject, element {@code i} for
     * {@code subjects.get(i)}: the elements of the {@code PermissionCollection} that
     * {@link Policy#getPermissions(ProtectionDomain)} returns, not the answers to questions.  A
     * permission whose class cannot be resolved here stays an {@link UnresolvedPermission}.  An
     * empty list means the provider lists nothing for that subject.
     *
     * @throws LoadFailure if the provider cannot load the file, throws from the listing or
     *         returns no collection: that is not "nothing granted"
     */
    synchronized List<List<Permission>> listedToEach(File policy, Map<String, String> defines,
            List<Subject> subjects) {
        return eachSubject(policy, defines, subjects, new Ask() {
            public List<Permission> of(Policy p, ProtectionDomain pd) {
                PermissionCollection pc;
                try {
                    pc = p.getPermissions(pd);
                } catch (RuntimeException e) {
                    throw new LoadFailure(providerClass + " cannot list what it grants: " + why(e), e);
                }
                if (pc == null) throw new LoadFailure(providerClass + " returned no permission list", null);
                List<Permission> res = new ArrayList<Permission>();
                for (Enumeration<Permission> en = pc.elements(); en.hasMoreElements();) {
                    Permission e = en.nextElement();
                    if (e instanceof UnresolvedPermission) {
                        Permission r = resolve((UnresolvedPermission) e);
                        if (r != null) e = r;
                    }
                    res.add(e);
                }
                return res;
            }
        });
    }

    /** One question asked of a loaded provider about one subject. */
    private interface Ask {
        List<Permission> of(Policy p, ProtectionDomain pd);
    }

    private List<List<Permission>> eachSubject(File policy, Map<String, String> defines,
            List<Subject> subjects, Ask ask) {
        List<List<Permission>> all = new ArrayList<List<Permission>>();
        Properties sys = System.getProperties();
        Map<String, String> saved = new HashMap<String, String>();
        Map<String, String> set = new HashMap<String, String>(defines);
        set.put("/", File.separator);
        for (Map.Entry<String, String> e : set.entrySet()) {
            saved.put(e.getKey(), sys.getProperty(e.getKey()));
            sys.setProperty(e.getKey(), e.getValue());
        }
        try {
            URL u = policy.toURI().toURL();
            Policy p;
            try {
                p = (Policy) constructor.newInstance(new Object[]{new URL[]{u}});
            } catch (InvocationTargetException e) {
                throw new LoadFailure(providerClass + " cannot load " + policy.getName() + ": " + why(e), e);
            }
            for (Subject s : subjects) {
                CodeSource cs = source(s.url);
                if (cs == null) throw new LoadFailure("no URL handler for " + s.url, null);
                all.add(ask.of(p, new ProtectionDomain(cs, null, null, s.who)));
            }
        } catch (ReflectiveOperationException | MalformedURLException | RuntimeException e) {
            if (e instanceof LoadFailure) throw (LoadFailure) e;
            throw new LoadFailure(providerClass + " cannot load " + policy.getName() + ": " + why(e), e);
        } finally {
            for (Map.Entry<String, String> e : saved.entrySet()) {
                if (e.getValue() == null) sys.remove(e.getKey());
                else sys.setProperty(e.getKey(), e.getValue());
            }
        }
        return all;
    }

    /** Asks every probe of one subject; records the first subject of a zone granted a probe. */
    private static void collect(Policy p, Answer a, String zone, String label, CodeSource cs,
            Principal[] who, List<PermissionSets.Probe> probes) {
        if (cs == null) return; // e.g. no such URL handler on this JDK; the question is not asked
        ProtectionDomain pd = new ProtectionDomain(cs, null, null, who);
        // the listing is read once per subject: a permission the lint cannot judge must show up
        // whether or not a probe happens to be implied
        List<Permission> granted = elements(p, pd, zone, label, a);
        for (PermissionSets.Probe probe : probes) {
            if (a.hit(zone, probe.id)) continue;
            boolean yes = false;
            try {
                yes = p.implies(pd, probe.permission);
            } catch (RuntimeException e) {
                noteThrows(a, zone, label, probe.permission.getClass().getName(), e);
            }
            if (!yes) {
                for (Permission g : granted) {
                    try {
                        if (g.implies(probe.permission)) {
                            yes = true;
                            break;
                        }
                    } catch (RuntimeException e) {
                        // cannot tell: recorded once as a finding of its own, never read as "no"
                        noteThrows(a, zone, label, g.getClass().getName(), e);
                    }
                }
            }
            if (yes) a.hits.add(new Hit(zone, label, probe));
        }
    }

    private static void noteThrows(Answer a, String zone, String label, String cls, RuntimeException e) {
        String id = THROWS + cls;
        if (a.hit(zone, id)) return;
        a.hits.add(new Hit(zone, label, id, PermissionSets.HIGH, "permission class " + cls
                + " throws " + e.getClass().getSimpleName() + " from implies: what it grants cannot"
                + " be judged, so it counts as granted", cls));
    }

    /**
     * The permissions the provider lists for a subject; empty if it does not list them.  A
     * permission whose class cannot be loaded or built here is recorded as a hit of its own.
     */
    private static List<Permission> elements(Policy p, ProtectionDomain pd, String zone, String label,
            Answer a) {
        List<Permission> res = new ArrayList<Permission>();
        try {
            PermissionCollection pc = p.getPermissions(pd);
            if (pc != null) {
                for (Enumeration<Permission> en = pc.elements(); en.hasMoreElements();) {
                    Permission e = en.nextElement();
                    if (e instanceof UnresolvedPermission) {
                        // the provider resolves against the class loader of the checked class; the
                        // probe domains have none, so resolve with the lint's own loader
                        UnresolvedPermission u = (UnresolvedPermission) e;
                        String why = unresolvable(u);
                        if (why != null) {
                            String id = UNJUDGED + u.getUnresolvedType();
                            if (!a.notes.contains(why)) a.notes.add(why);
                            if (!a.hit(zone, id)) {
                                a.hits.add(new Hit(zone, label, id, PermissionSets.HIGH, why,
                                        u.getUnresolvedType()));
                            }
                            continue;
                        }
                        e = resolve(u);
                    }
                    res.add(e);
                }
            }
        } catch (RuntimeException e) {
            // the provider does not list permissions for this subject; implies() was asked already
        }
        return res;
    }

    /**
     * Why an unresolved permission cannot be built here ("not loadable", or "rejected by its own
     * constructor"), or {@code null} if it can.
     */
    private static String unresolvable(UnresolvedPermission u) {
        String t = u.getUnresolvedType();
        if (!PermissionSets.loadable(t)) {
            return "permission class " + t + " is not loadable here: what it grants cannot be judged";
        }
        if (resolve(u) == null) {
            return "permission class " + t + " is loadable but rejects its name "
                    + quote(u.getUnresolvedName()) + " and actions "
                    + quote(u.getUnresolvedActions()) + ": what it grants cannot be judged";
        }
        return null;
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private static Permission resolve(UnresolvedPermission u) {
        return PermissionSets.instantiate(u.getUnresolvedType(), nullIfEmpty(u.getUnresolvedName()),
                nullIfEmpty(u.getUnresolvedActions()));
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.length() == 0 ? null : s;
    }

    private static List<Principal> principals(Collection<String> named, List<String> notes,
            List<String> unbuildable) {
        Map<String, Principal> res = new LinkedHashMap<String, Principal>();
        Principal x500 = new X500Principal("CN=lint-stranger");
        res.put(x500.getClass().getName(), x500);
        Principal own = new StrangerPrincipal();
        res.put(own.getClass().getName(), own);
        for (String cn : named == null ? Collections.<String>emptyList() : named) {
            if (cn == null || cn.equals("*") || res.containsKey(cn)) continue;
            Principal made = null;
            try {
                Class<?> c = Class.forName(cn, true, PolicyVerdict.class.getClassLoader());
                if (!Principal.class.isAssignableFrom(c)) continue;
                for (String arg : NAME_FORMS) {
                    try {
                        made = (Principal) c.getConstructor(String.class).newInstance(arg);
                        break;
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        // try the next form of the name
                    }
                }
            } catch (ClassNotFoundException | LinkageError e) {
                // not on this classpath: stood in for below
            }
            if (made == null) {
                made = StandInPrincipal.create(cn, NAME_FORMS[0]);
                if (made != null) {
                    notes.add("principal class " + cn + " cannot be made from a name here: a"
                            + " generated class of the same name stands in for it");
                }
            }
            if (made == null) {
                unbuildable.add(cn);
                notes.add("principal class " + cn + " can be neither made nor stood in for: grants"
                        + " to it are treated as reaching a stranger");
            } else {
                res.put(cn, made);
            }
        }
        return new ArrayList<Principal>(res.values());
    }

    /** Names tried in turn to make a principal of a class the policy names. */
    private static final String[] NAME_FORMS = {"lint-stranger", "CN=lint-stranger",
        "spiffe://lint.example/stranger", "lint-stranger@LINT.EXAMPLE"};

    private static CodeSource source(String url) {
        try {
            return new CodeSource(new URL(url), (Certificate[]) null);
        } catch (MalformedURLException e) {
            return null; // e.g. no jrt: handler on this JDK; the question is then not asked
        }
    }
}
