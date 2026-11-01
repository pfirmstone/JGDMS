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
package au.net.zeus.jgdms.api.policy;

import java.io.File;
import java.security.Permission;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.river.api.security.AdvisoryPermissionParser;

/**
 * The permission classes and entries the promotion tooling treats specially,
 * defined once for {@code ProxyPolicyGenerator}, {@code ListPromotion},
 * {@code PolicyLint} (tools/policy-condenser) and {@link VerdictPermissionMapper}.
 *
 * <p>A jar's {@code META-INF/PERMISSIONS.LIST} is a declared-needs cap and one
 * term of the effective grant, so promotion must not pass in one step an entry
 * that lets code leave the SecurityManager.  Three sets, strongest first:
 * <ul>
 *   <li><b>META</b> and <b>GATE</b>, by class: never written, and a list that
 *       holds one live was edited and is refused.</li>
 *   <li><b>HOLD</b>, by class and name: written commented out as {@code # HELD};
 *       a live one in a generated list was edited by hand; promotion needs
 *       {@code --allow-held}.</li>
 *   <li><b>FLAG</b>, by class and name: written live, but the plan and the gate
 *       report the entry; nothing is refused.</li>
 * </ul>
 * The JDK class names are the JDK's.  The JGDMS ones come from
 * {@code jgdms-platform} ({@code net.jini.security.GrantPermission},
 * {@code net.jini.security.policy.UmbrellaGrantPermission},
 * {@code net.jini.loader.DownloadPermission},
 * {@code org.apache.river.api.security.PolicyPermission}) and
 * {@code jgdms-pref-class-loader} ({@code net.jini.loader.pref.INCONCLUSIVEPermit},
 * {@code net.jini.loader.pref.BootstrapPermission}).  The DirtyChai ones
 * ({@code au.zeus.jdk.authorization.guards.LoadClassPermission} and
 * {@code NativeInvocationPermission}) are not in this tree: their names are those
 * the policy files and the DirtyChai guide record.
 */
public final class PermissionSets {

    public static final String ALL_PERMISSION = "java.security.AllPermission";
    public static final String GRANT_PERMISSION = "net.jini.security.GrantPermission";
    public static final String UMBRELLA_GRANT_PERMISSION = "net.jini.security.policy.UmbrellaGrantPermission";
    public static final String POLICY_PERMISSION = "org.apache.river.api.security.PolicyPermission";

    /**
     * Policy-grant meta-capabilities and blanket authority, never a proxy's
     * declared need.  {@link VerdictPermissionMapper} derives its exclusion set from this
     * one, so the offline tooling and the runtime ceiling cannot disagree on it.
     */
    public static final Set<String> META = set(
        GRANT_PERMISSION, UMBRELLA_GRANT_PERMISSION, ALL_PERMISSION, POLICY_PERMISSION);

    /**
     * Admission-gate classes: the proxy provider grants them itself once a codebase
     * is verified, so a jar must not request its own admission.  The two
     * {@code LoadClassPermission} names are the DirtyChai class as {@code polpAudit}
     * records it and the name the provider uses for its unresolved grant.
     * {@code INCONCLUSIVEPermit} (strict-mode tolerance of an INCONCLUSIVE jar) and
     * {@code BootstrapPermission} (a server acting as trusted codebase source before
     * the verdict registry exists) are gates of the same kind.
     */
    public static final Set<String> GATE = set(
        "au.zeus.jdk.authorization.guards.LoadClassPermission",
        "net.jini.loader.LoadClassPermission",
        "java.net.URLPermission",
        "net.jini.loader.DownloadPermission",
        "net.jini.loader.pref.INCONCLUSIVEPermit",
        "net.jini.loader.pref.BootstrapPermission");

    /** Why a meta class never reaches a list. */
    public static final String META_REASON = "policy-grant meta-capability: never a proxy's declared need";

    /** Why a gate class is dropped; also the reason promotion refuses a list that holds one. */
    public static final String GATE_REASON = "admission-gate permission: the proxy provider grants "
        + "it after verifying the codebase, a jar must not request its own admission";

    private static final String RUNTIME = "java.lang.RuntimePermission";
    private static final String REFLECT = "java.lang.reflect.ReflectPermission";
    private static final String SECURITY = "java.security.SecurityPermission";
    private static final String FILE = "java.io.FilePermission";
    private static final String AUTH = "javax.security.auth.AuthPermission";
    private static final String SERIAL = "java.io.SerializablePermission";
    private static final String SOCKET = "java.net.SocketPermission";
    private static final String NATIVE =
        "au.zeus.jdk.authorization.guards.NativeInvocationPermission";
    private static final String ALL_FILES = "<<ALL FILES>>";

    private PermissionSets() {
    }

    /**
     * @return why the generator never emits a permission of this class, so a list
     *         that holds one has been edited; {@code null} if it may appear
     */
    public static String excludedReason(String type) {
        if (META.contains(type)) return META_REASON;
        if (GATE.contains(type)) return GATE_REASON;
        return null;
    }

    /**
     * @return why an entry lets code leave the SecurityManager in one step, whatever else is
     *         granted, so it is held back; {@code null} if it is not in the HOLD set
     */
    public static String holdReason(String type, String name, String actions) {
        if (type == null) return null;
        if (NATIVE.equals(type)) return "escalation: native code invocation";
        if (name == null) return null;
        if (RUNTIME.equals(type) && basic(name, new String[]{"setSecurityManager",
                "createClassLoader", "createAccessControlContext"},
                new String[]{"loadLibrary.", "defineClassInPackage."})) {
            return "escalation: RuntimePermission " + name + " replaces or escapes the SecurityManager";
        }
        if (REFLECT.equals(type) && basic(name, new String[]{"suppressAccessChecks"}, none())) {
            return "escalation: ReflectPermission suppressAccessChecks defeats every access check";
        }
        if (SECURITY.equals(type) && basic(name, new String[]{"createAccessControlContext",
                "setPolicy", "insertProvider", "removeProvider"}, new String[]{"setProperty.",
                "insertProvider.", "removeProvider.", "putProviderProperty.",
                "removeProviderProperty."})) {
            return "escalation: SecurityPermission " + name + " changes the policy or its inputs";
        }
        if (FILE.equals(type) && ALL_FILES.equals(name) && hasAction(actions, "execute")) {
            return "escalation: executes any program";
        }
        if (AUTH.equals(type) && basic(name, new String[]{"doAsPrivileged", "modifyPrincipals",
                "modifyPrivateCredentials"}, none())) {
            return "escalation: AuthPermission " + name + " runs as, or becomes, another subject";
        }
        if (SERIAL.equals(type) && basic(name, new String[]{"enableSubclassImplementation"}, none())) {
            return "escalation: SerializablePermission enableSubclassImplementation overrides"
                + " the serialization streams";
        }
        return null;
    }

    /**
     * @return why an entry is worth a reviewer's second look although it is written live,
     *         or {@code null}; reported by the plan and the gate, never refused
     */
    public static String flagReason(String type, String name, String actions) {
        if (type == null || name == null) return null;
        if (FILE.equals(type) && ALL_FILES.equals(name)) return "reads or writes every file";
        if (AUTH.equals(type)) return "AuthPermission " + name + " acts on the current subject";
        if (SERIAL.equals(type)) return "SerializablePermission " + name;
        if (REFLECT.equals(type)) return "ReflectPermission " + name;
        if (RUNTIME.equals(type) && (basic(name, new String[]{"accessDeclaredMembers",
                "setContextClassLoader", "setFactory", "getProtectionDomain"},
                new String[]{"accessClassInPackage.sun.", "exitVM."}))) {
            return "RuntimePermission " + name + " reaches into JDK internals or global state";
        }
        if (PROPERTY.equals(type) && (name.equals("*") || name.endsWith(".*"))
                && hasAction(actions, "write")) {
            return "PropertyPermission write on " + name + " changes global state";
        }
        if (AUTHENTICATION.equals(type) && name.contains("*")) return "AuthenticationPermission " + name;
        if (URL.equals(type) && name.contains("*")) return "URLPermission " + name;
        if (SOCKET.equals(type) && (name.equals("*") || name.startsWith("*:"))) {
            return "SocketPermission to any host";
        }
        return null;
    }

    // ------------------------------------------------------------ dangerous-policy catalogue

    /** Severity of a {@link Danger}: lets code leave or rewrite the sandbox in one step. */
    public static final int HIGH = 2;
    /** Severity of a {@link Danger}: broad reach worth a reviewer's second look. */
    public static final int MEDIUM = 1;

    private static final String PROPERTY = "java.util.PropertyPermission";
    private static final String AUTHENTICATION = "net.jini.security.AuthenticationPermission";
    private static final String URL = "java.net.URLPermission";
    private static final String DESERIALIZATION = "org.apache.river.api.io.DeSerializationPermission";

    /** A permission found dangerous: its severity and why. */
    public static final class Danger {
        public final int severity;
        public final String reason;

        Danger(int severity, String reason) {
            this.severity = severity;
            this.reason = reason;
        }
    }

    /** A representative of a dangerous family, to be asked of a policy provider. */
    public static final class Probe {
        /** Short label for reports, e.g. {@code RuntimePermission setSecurityManager}. */
        public final String id;
        public final int severity;
        public final String reason;
        /** The permission to pass to {@code Policy.implies}. */
        public final Permission permission;

        Probe(String id, int severity, String reason, Permission permission) {
            this.id = id;
            this.severity = severity;
            this.reason = reason;
            this.permission = permission;
        }
    }

    /**
     * Whether a permission is dangerous when it reaches code the policy does not scope tightly.
     * It extends HOLD and FLAG (a held entry is HIGH, a flagged one MEDIUM) with what a policy
     * gate needs: AllPermission and the meta classes, GrantPermission of a dangerous target,
     * write/delete/execute (HIGH) or read (MEDIUM) on all files or on a root, drive or
     * current-directory tree, and the deserialisation wildcard.
     *
     * @return {@code null} if the entry is not in the catalogue
     */
    public static Danger danger(String type, String name, String actions) {
        if (type == null) return null;
        if (ALL_PERMISSION.equals(type)) return new Danger(HIGH, "AllPermission grants everything");
        if (UMBRELLA_GRANT_PERMISSION.equals(type) || POLICY_PERMISSION.equals(type)) {
            return new Danger(HIGH, META_REASON);
        }
        if (GRANT_PERMISSION.equals(type)) return grantDanger(name);
        String hold = holdReason(type, name, actions);
        if (hold != null) return new Danger(HIGH, hold);
        if (FILE.equals(type) && broadFile(name)) {
            for (String a : new String[]{"write", "delete", "execute"}) {
                if (hasAction(actions, a)) {
                    return new Danger(HIGH, "FilePermission " + a + " on " + name
                        + " reaches all files or a whole root, drive or directory tree");
                }
            }
            return new Danger(MEDIUM, "FilePermission read on " + name
                + " reaches all files or a whole root, drive or directory tree");
        }
        if (DESERIALIZATION.equals(type) && name != null
                && (name.equals("*") || name.equals("MARSHALLED"))) {
            return new Danger(HIGH, "DeSerializationPermission " + name
                + " lets any serialized class be read from an untrusted stream");
        }
        String flag = flagReason(type, name, actions);
        return flag == null ? null : new Danger(MEDIUM, flag);
    }

    /** GrantPermission is as dangerous as the most dangerous entry of its target. */
    private static Danger grantDanger(String target) {
        if (target == null) return null;
        String t = target.trim();
        if (t.startsWith("delim=") && t.length() > "delim=".length() + 1) {
            char d = t.charAt("delim=".length());
            t = t.substring("delim=".length() + 1);
            for (String entry : t.split(java.util.regex.Pattern.quote(String.valueOf(d)))) {
                Danger g = grantEntryDanger(entry);
                if (g != null) return g;
            }
            return null;
        }
        for (String entry : t.split(";")) {
            Danger g = grantEntryDanger(entry);
            if (g != null) return g;
        }
        return null;
    }

    private static Danger grantEntryDanger(String entry) {
        String e = entry.trim();
        if (e.length() == 0) return null;
        String[] w = e.split("\\s+", 2);
        String cls = w[0];
        if (cls.equals("*") || cls.equals("-") || (cls.endsWith(".*")
                && (ALL_PERMISSION.startsWith(cls.substring(0, cls.length() - 1))
                || RUNTIME.startsWith(cls.substring(0, cls.length() - 1))))) {
            return new Danger(HIGH, "GrantPermission to grant any permission (" + cls + ")");
        }
        String rest = w.length > 1 ? w[1].trim() : "";
        String name = null;
        String actions = null;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^\\s*\"((?:[^\"\\\\]|\\\\.)*)\"(?:\\s*,?\\s*\"((?:[^\"\\\\]|\\\\.)*)\")?").matcher(rest);
        if (m.find()) {
            name = m.group(1).replace("\\\"", "\"");
            actions = m.group(2);
        } else if (rest.length() > 0) {
            String[] r = rest.replace("\\", "").replace("\"", "").split("\\s+");
            name = r[0];
            actions = r.length > 1 ? r[1] : null;
        }
        Danger d = danger(cls, name, actions);
        return d == null ? null : new Danger(d.severity, "GrantPermission may grant " + cls + " "
            + name + ": " + d.reason);
    }

    /**
     * Whether a FilePermission name covers all files, or a whole root, drive or current-directory
     * tree: {@code <<ALL FILES>>}, {@code -}, {@code *}, {@code /-}, {@code C:\-} and the like.
     */
    static boolean broadFile(String name) {
        if (name == null) return false;
        String n = name.trim();
        if (ALL_FILES.equals(n) || n.equals("-") || n.equals("*")) return true;
        if (n.endsWith("-") || n.endsWith("*")) {
            String dir = n.substring(0, n.length() - 1);
            while (dir.endsWith("/") || dir.endsWith("\\")) dir = dir.substring(0, dir.length() - 1);
            return dir.length() == 0 || dir.matches("(?i)[a-z]:");
        }
        return false;
    }

    /**
     * The representatives of the dangerous families, built for the file system of this machine,
     * each with the severity {@link #danger} gives its family.  A policy provider is asked whether
     * code it should not trust is granted one: a wildcard, a broad path or a permission class the
     * catalogue does not know shows up as an {@code implies} that answers true.  Entries whose class
     * cannot be loaded here (the DirtyChai guards) are left out.
     */
    public static List<Probe> probes() {
        List<Probe> res = new ArrayList<Probe>();
        String root = File.listRoots() != null && File.listRoots().length > 0
            ? File.listRoots()[0].getPath() : File.separator;
        String sep = File.separator;
        String rootTree = root + "-";
        String under = root + "lint-probe" + sep + "x";
        // relative names: a policy "-" or "*" is relative to the current directory
        String cwd = "lint-probe" + sep + "x";
        add(res, ALL_PERMISSION, null, null, null);
        for (String a : new String[]{"write", "delete", "execute", "read"}) {
            add(res, FILE, ALL_FILES, a, null);
            add(res, FILE, under, a, rootTree);
        }
        add(res, FILE, cwd, "write", "-");
        add(res, FILE, cwd, "execute", "-");
        add(res, FILE, "lint-probe", "write", "*");
        for (String n : new String[]{"setPolicy", "setProperty.package.access", "insertProvider.lint",
            "removeProvider.lint", "putProviderProperty.lint", "createAccessControlContext"}) {
            add(res, SECURITY, n, null, null);
        }
        for (String n : new String[]{"setSecurityManager", "createClassLoader", "loadLibrary.lint",
            "defineClassInPackage.lint.probe", "createAccessControlContext", "getProtectionDomain",
            "accessClassInPackage.sun.lint", "setContextClassLoader", "setFactory", "exitVM.1"}) {
            add(res, RUNTIME, n, null, null);
        }
        add(res, REFLECT, "suppressAccessChecks", null, null);
        add(res, AUTH, "doAsPrivileged", null, null);
        add(res, AUTH, "modifyPrincipals", null, null);
        add(res, SERIAL, "enableSubclassImplementation", null, null);
        add(res, SOCKET, "evil.example.com:80", "connect,resolve", "*");
        add(res, SOCKET, "evil.example.com:80", "accept,resolve", "*");
        add(res, SOCKET, "localhost:80", "listen", "*");
        add(res, PROPERTY, "lint.probe.property", "write", "*");
        add(res, URL, "https://evil.example.com/lint", "GET", "*");
        add(res, DESERIALIZATION, "MARSHALLED", null, null);
        add(res, AUTHENTICATION, "javax.security.auth.x500.X500Principal \"CN=lint-probe\"",
            "connect", "* *");
        add(res, UMBRELLA_GRANT_PERMISSION, null, null, null);
        for (String[] g : new String[][]{{RUNTIME, "setSecurityManager"}, {SECURITY, "setPolicy"},
            {REFLECT, "suppressAccessChecks"}}) {
            Permission inner = create(g[0], g[1], null);
            if (inner != null) {
                Permission gp = create(GRANT_PERMISSION, null, null, inner);
                if (gp != null) {
                    String target = g[0] + " \"" + g[1] + "\"";
                    Danger d = danger(GRANT_PERMISSION, target, null);
                    res.add(new Probe("GrantPermission of " + simple(g[0]) + " " + g[1],
                        d == null ? HIGH : d.severity, d == null ? "GrantPermission" : d.reason, gp));
                }
            }
        }
        return res;
    }

    /**
     * The probe for one permission a policy file names, so a grant of a specific dangerous name
     * (say {@code insertProvider.BC}) is asked of the provider although the catalogue lists only a
     * representative of its family.
     *
     * @return {@code null} if the entry is not dangerous, is AllPermission (always asked) or its
     *         class cannot be built here
     */
    public static Probe probe(String type, String name, String actions) {
        if (type == null || ALL_PERMISSION.equals(type)) return null;
        Danger d = danger(type, name, actions);
        if (d == null) return null;
        Permission p = create(type, name, actions);
        if (p == null) return null;
        return new Probe(simple(type) + (name == null ? "" : " " + name)
            + (actions == null ? "" : " " + actions), d.severity, d.reason, p);
    }

    private static void add(List<Probe> res, String type, String name, String actions,
            String classifyName) {
        Permission p = create(type, name, actions);
        if (p == null) return;
        Danger d = danger(type, classifyName != null ? classifyName : name, actions);
        if (d == null) {
            throw new IllegalStateException("probe not in the catalogue: " + type + " " + name);
        }
        String id = simple(type) + (name == null ? "" : " " + (classifyName != null ? classifyName : name))
            + (actions == null ? "" : " " + actions);
        res.add(new Probe(id, d.severity, d.reason, p));
    }

    private static String simple(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }

    private static Permission create(String type, String name, String actions, Permission inner) {
        try {
            Class<?> c = Class.forName(type, true, PermissionSets.class.getClassLoader());
            return (Permission) c.getConstructor(Permission.class).newInstance(inner);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static Permission create(String type, String name, String actions) {
        try {
            Class<?> c = Class.forName(type, true, PermissionSets.class.getClassLoader());
            if (actions != null) {
                return (Permission) c.getConstructor(String.class, String.class).newInstance(name, actions);
            }
            if (name != null) return (Permission) c.getConstructor(String.class).newInstance(name);
            return (Permission) c.getConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * The type, name and actions of an entry line, parsed as the loader parses it;
     * {@code null} if it does not parse.
     */
    static String[] triple(String entry) {
        try {
            Permission p = AdvisoryPermissionParser.parse(entry,
                PermissionSets.class.getClassLoader());
            if (p instanceof UnresolvedPermission) {
                UnresolvedPermission u = (UnresolvedPermission) p;
                return new String[]{u.getUnresolvedType(), u.getUnresolvedName(),
                    u.getUnresolvedActions()};
            }
            return new String[]{p.getClass().getName(), p.getName(), p.getActions()};
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** {@link #holdReason} of an entry line; {@code null} if it does not parse. */
    public static String holdReason(String entry) {
        String[] t = triple(entry);
        return t == null ? null : holdReason(t[0], t[1], t[2]);
    }

    /** {@link #flagReason} of an entry line; {@code null} if it does not parse. */
    public static String flagReason(String entry) {
        String[] t = triple(entry);
        return t == null ? null : flagReason(t[0], t[1], t[2]);
    }

    /**
     * Whether a BasicPermission target name {@code name} (which may be {@code *} or end in
     * {@code .*}) names, or implies, one of {@code exact} or anything under a {@code prefixes} stem.
     */
    private static boolean basic(String name, String[] exact, String[] prefixes) {
        if (name.equals("*")) return true;
        if (name.endsWith(".*")) {
            String stem = name.substring(0, name.length() - 1);
            for (String h : exact) if (h.startsWith(stem)) return true;
            for (String p : prefixes) if (p.startsWith(stem) || stem.startsWith(p)) return true;
            return false;
        }
        for (String h : exact) if (h.equals(name)) return true;
        for (String p : prefixes) if (name.startsWith(p)) return true;
        return false;
    }

    private static boolean hasAction(String actions, String action) {
        if (actions == null) return false;
        for (String a : actions.split(",")) {
            if (a.trim().toLowerCase(Locale.ROOT).equals(action)) return true;
        }
        return false;
    }

    private static String[] none() {
        return new String[0];
    }

    private static Set<String> set(String... names) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(names)));
    }
}
