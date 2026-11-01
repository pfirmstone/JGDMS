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
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.jini.security.GrantPermission;
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
        // Operator ruling 2026-11-01: a proxy must not hold the right to pass itself off
        // as a user, whatever the class and name; only the deployer's own policy grants it.
        if (ASSERT_PRINCIPAL.equals(type)) return "escalation: AssertPrincipalPermission lets "
            + "the holder pass itself off as a user; a proxy must not hold it";
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
     * write/delete/execute (HIGH) or read (MEDIUM) on all files, on a root, drive or
     * current-directory tree, or on a whole user-home, JDK-home or temp-directory tree, the
     * deserialisation wildcard, and the identity, credential, network, TLS, JMX and management
     * permissions listed in {@link #extraDanger}.  File names are expanded with the system
     * properties.
     *
     * @return {@code null} if the entry is not in the catalogue
     */
    public static Danger danger(String type, String name, String actions) {
        return danger(type, name, actions, null);
    }

    /**
     * As {@link #danger(String, String, String)}; {@code ${property}} in a file name is looked up
     * in {@code env} first and then in the system properties (null {@code env}: system only).  A
     * file name with a property defined in neither is treated as the root.
     */
    public static Danger danger(String type, String name, String actions, Properties env) {
        if (type == null) return null;
        if (ALL_PERMISSION.equals(type)) return new Danger(HIGH, "AllPermission grants everything");
        if (UMBRELLA_GRANT_PERMISSION.equals(type) || POLICY_PERMISSION.equals(type)) {
            return new Danger(HIGH, META_REASON);
        }
        if (GRANT_PERMISSION.equals(type)) return grantDanger(name);
        String hold = holdReason(type, name, actions);
        // AssertPrincipalPermission is HOLD for proxy lists but graded by extraDanger for a
        // deployer's own policy: an exact grant to a named gateway is legitimate there.
        if (hold != null && !ASSERT_PRINCIPAL.equals(type)) return new Danger(HIGH, hold);
        if (FILE.equals(type) && name != null) {
            Danger f = fileDanger(name, actions, env);
            if (f != null) return f;
        }
        if (DESERIALIZATION.equals(type) && name != null
                && (name.equals("*") || name.equals("MARSHALLED"))) {
            return new Danger(HIGH, "DeSerializationPermission " + name
                + " lets any serialized class be read from an untrusted stream");
        }
        Danger x = extraDanger(type, name, actions);
        if (x != null) return x;
        String flag = flagReason(type, name, actions);
        return flag == null ? null : new Danger(MEDIUM, flag);
    }

    private static final String ASSERT_PRINCIPAL = "net.jini.security.AssertPrincipalPermission";
    private static final String PRIVATE_CREDENTIAL = "javax.security.auth.PrivateCredentialPermission";
    private static final String NET = "java.net.NetPermission";
    private static final String SSL = "javax.net.ssl.SSLPermission";
    private static final String MBEAN = "javax.management.MBeanPermission";
    private static final String MBEAN_SERVER = "javax.management.MBeanServerPermission";
    private static final String MBEAN_TRUST = "javax.management.MBeanTrustPermission";
    private static final String MANAGEMENT = "java.lang.management.ManagementPermission";
    private static final String ATTACH = "com.sun.tools.attach.AttachPermission";
    private static final String LOGGING = "java.util.logging.LoggingPermission";
    private static final String KRB_DELEGATION = "javax.security.auth.kerberos.DelegationPermission";
    private static final String KRB_SERVICE = "javax.security.auth.kerberos.ServicePermission";
    private static final String DOWNLOAD = "net.jini.loader.DownloadPermission";
    private static final String LOAD_CLASS = "net.jini.loader.LoadClassPermission";
    private static final String DC_LOAD_CLASS = "au.zeus.jdk.authorization.guards.LoadClassPermission";
    private static final String DC_SERIAL_OBJECT = "au.zeus.jdk.authorization.guards.SerialObjectPermission";

    /** Permission classes whose wildcard entries are HIGH: a package stem of one of these is too. */
    private static final String[] HIGH_CLASSES = {ALL_PERMISSION, RUNTIME, SECURITY, REFLECT, FILE,
        AUTH, SERIAL, NET, SSL, PRIVATE_CREDENTIAL, ASSERT_PRINCIPAL, UMBRELLA_GRANT_PERMISSION,
        POLICY_PERMISSION, GRANT_PERMISSION, NATIVE};

    private static final String[] AUTH_PROPERTIES = {"java.security.policy", "java.security.manager",
        "java.security.auth.login.config", "java.security.properties"};

    /**
     * The catalogue entries beyond HOLD and FLAG.  The table of the review notes
     * ({@code INVESTIGATION-policy-lint-redesign}) gives the reason for each severity.
     */
    private static Danger extraDanger(String type, String name, String actions) {
        if (name == null) return null;
        if (ASSERT_PRINCIPAL.equals(type)) {
            if (name.indexOf('*') >= 0 || (actions != null && actions.indexOf('*') >= 0)) {
                return new Danger(HIGH, "AssertPrincipalPermission " + name + " " + actions
                    + " lets the holder pass itself off as any matching user");
            }
            return null;
        }
        if (PRIVATE_CREDENTIAL.equals(type)) {
            if (name.indexOf('*') >= 0) {
                return new Danger(HIGH, "PrivateCredentialPermission " + name
                    + " reads the private credentials (keys, tickets) of any matching subject");
            }
            return null;
        }
        if (NET.equals(type)) {
            if (basic(name, new String[]{"specifyStreamHandler"}, none())) {
                return new Danger(HIGH, "NetPermission " + name + " replaces the URL stream handlers");
            }
            if (basic(name, new String[]{"setDefaultAuthenticator", "requestPasswordAuthentication",
                    "setProxySelector", "setCookieHandler", "setResponseCache", "getCookieHandler",
                    "getResponseCache"}, none())) {
                return new Danger(MEDIUM, "NetPermission " + name + " changes or reads global network state");
            }
            return null;
        }
        if (SSL.equals(type)) {
            if (basic(name, new String[]{"setDefaultSSLContext"}, none())) {
                return new Danger(HIGH, "SSLPermission " + name + " replaces the TLS context and trust");
            }
            if (basic(name, new String[]{"setHostnameVerifier", "getSSLSessionContext"}, none())) {
                return new Danger(MEDIUM, "SSLPermission " + name + " changes TLS verification or reads sessions");
            }
            return null;
        }
        if (RUNTIME.equals(type)) {
            if (basic(name, none(), new String[]{"accessClassInPackage.jdk.internal."})) {
                return new Danger(HIGH, "RuntimePermission " + name + " reaches the JDK's internal packages");
            }
            if (basic(name, new String[]{"setIO", "getClassLoader", "modifyThread", "modifyThreadGroup",
                    "writeFileDescriptor", "readFileDescriptor", "manageProcess",
                    "enableContextClassLoaderOverride", "charsetProvider", "accessSystemModules"},
                    none())) {
                return new Danger(MEDIUM, "RuntimePermission " + name
                    + " reaches global I/O, threads, processes or class loaders");
            }
            return null;
        }
        if (SECURITY.equals(type)) {
            if (basic(name, none(), new String[]{"clearProviderProperties."})) {
                return new Danger(HIGH, "SecurityPermission " + name + " clears a provider's properties");
            }
            if (basic(name, new String[]{"getPolicy"}, new String[]{"createPolicy."})) {
                return new Danger(MEDIUM, "SecurityPermission " + name + " reads or creates a policy");
            }
            return null;
        }
        if (PROPERTY.equals(type)) {
            if (hasAction(actions, "write")) {
                for (String p : AUTH_PROPERTIES) {
                    if (name.equals(p) || name.equals("*")
                            || (name.endsWith(".*") && p.startsWith(name.substring(0, name.length() - 1)))) {
                        return new Danger(HIGH, "PropertyPermission write on " + name
                            + " changes where the policy, security manager or login configuration come from");
                    }
                }
            }
            return null;
        }
        if (MBEAN.equals(type)) {
            if (name.indexOf('*') >= 0) {
                boolean mutate = actions == null || actions.indexOf('*') >= 0;
                for (String a : new String[]{"registerMBean", "invoke", "setAttribute", "unregisterMBean",
                        "instantiate", "createMBean"}) {
                    mutate |= hasAction(actions, a);
                }
                return new Danger(mutate ? HIGH : MEDIUM, "MBeanPermission " + name + " " + actions
                    + (mutate ? " registers, invokes or changes any MBean" : " reads any MBean"));
            }
            return null;
        }
        if (MBEAN_SERVER.equals(type)) {
            if (name.equals("*")) return new Danger(HIGH, "MBeanServerPermission * creates and replaces MBean servers");
            return new Danger(MEDIUM, "MBeanServerPermission " + name);
        }
        if (MBEAN_TRUST.equals(type)) {
            return new Danger(HIGH, "MBeanTrustPermission " + name + " registers trusted MBeans");
        }
        if (MANAGEMENT.equals(type)) {
            if (name.equals("control") || name.equals("*")) {
                return new Danger(HIGH, "ManagementPermission " + name + " changes the running JVM");
            }
            return new Danger(MEDIUM, "ManagementPermission " + name);
        }
        if (ATTACH.equals(type)) {
            if (name.equals("attachVirtualMachine") || name.equals("*")) {
                return new Danger(HIGH, "AttachPermission " + name + " attaches to any JVM of the same user");
            }
            return new Danger(MEDIUM, "AttachPermission " + name);
        }
        if (LOGGING.equals(type)) {
            return new Danger(MEDIUM, "LoggingPermission " + name + " reconfigures logging");
        }
        if ((KRB_DELEGATION.equals(type) || KRB_SERVICE.equals(type)) && name.indexOf('*') >= 0) {
            return new Danger(MEDIUM, simple(type) + " " + name + " uses Kerberos tickets for any service");
        }
        if (DOWNLOAD.equals(type)) {
            return new Danger(MEDIUM, "DownloadPermission lets code download classes");
        }
        if (LOAD_CLASS.equals(type) || DC_LOAD_CLASS.equals(type)) {
            if (name.indexOf('*') >= 0) {
                return new Danger(HIGH, simple(type) + " " + name + " opens the class-loading gate for every codebase");
            }
            return DC_LOAD_CLASS.equals(type)
                ? new Danger(MEDIUM, "LoadClassPermission " + name + " opens the class-loading gate") : null;
        }
        if (DC_SERIAL_OBJECT.equals(type)) {
            if (name.indexOf('*') >= 0) {
                return new Danger(HIGH, "SerialObjectPermission " + name + " lets any class be deserialised");
            }
            return null;
        }
        return null;
    }

    /**
     * GrantPermission is as dangerous as the most dangerous permission its target contains.  The
     * target is read by {@code GrantPermission} itself (so {@code delim=} forms are what the
     * runtime sees); an entry whose class is a wildcard, which that parser rejects, is judged from
     * the text.
     */
    private static Danger grantDanger(String target) {
        if (target == null) return null;
        Danger best = null;
        for (String entry : grantEntries(target)) {
            String[] w = entry.trim().split("\\s+", 2);
            String cls = w[0];
            if (cls.equals("*") || cls.equals("-") || wildcardCoversHigh(cls)) {
                return new Danger(HIGH, "GrantPermission to grant any permission (" + cls + ")");
            }
        }
        Permission[] contained = containedPermissions(target);
        if (contained == null) return grantTextDanger(target);
        for (Permission p : contained) {
            Danger d;
            if (p instanceof UnresolvedPermission) {
                UnresolvedPermission u = (UnresolvedPermission) p;
                d = danger(u.getUnresolvedType(), u.getUnresolvedName(), u.getUnresolvedActions());
            } else {
                d = danger(p.getClass().getName(), p.getName(), p.getActions());
            }
            if (d != null && (best == null || d.severity > best.severity)) {
                best = new Danger(d.severity, "GrantPermission may grant " + p.getName() + ": " + d.reason);
                if (best.severity >= HIGH) return best;
            }
        }
        return best;
    }

    /**
     * The fallback for a target GrantPermission's parser rejects (the runtime then cannot use it
     * either, but a reader still must not take it for harmless): entries split on {@code ;} with a
     * custom delimiter turned back into the double quote.
     */
    private static Danger grantTextDanger(String target) {
        String t = target.trim();
        if (t.startsWith("delim=") && t.length() > "delim=".length() + 1) {
            char d = t.charAt("delim=".length());
            t = t.substring("delim=".length() + 1);
            if (d != '"') t = t.replace(d, '"');
        }
        Danger best = null;
        for (String entry : t.split(";")) {
            Danger g = grantEntryDanger(entry);
            if (g != null && (best == null || g.severity > best.severity)) best = g;
        }
        return best;
    }

    private static Danger grantEntryDanger(String entry) {
        String e = entry.trim();
        if (e.length() == 0) return null;
        String[] w = e.split("\\s+", 2);
        String cls = w[0];
        String rest = w.length > 1 ? w[1].trim() : "";
        String name = null;
        String actions = null;
        Matcher m = Pattern
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

    private static boolean wildcardCoversHigh(String cls) {
        if (!cls.endsWith(".*")) return false;
        String stem = cls.substring(0, cls.length() - 1);
        for (String h : HIGH_CLASSES) if (h.startsWith(stem)) return true;
        return false;
    }

    /** The class-name words of a GrantPermission target, with a leading {@code delim=C} dropped. */
    private static List<String> grantEntries(String target) {
        String t = target.trim();
        if (t.startsWith("delim=") && t.length() > "delim=".length() + 1) {
            t = t.substring("delim=".length() + 1);
        }
        List<String> res = new ArrayList<String>();
        for (String e : t.split(";")) if (e.trim().length() > 0) res.add(e);
        return res;
    }

    /**
     * What GrantPermission's own parser made of a target; {@code null} if it rejects the target.
     * The contained permissions are not exposed, so the field is read.
     */
    private static Permission[] containedPermissions(String target) {
        try {
            GrantPermission g = new GrantPermission(target);
            java.lang.reflect.Field f = GrantPermission.class.getDeclaredField("grants");
            f.setAccessible(true);
            return (Permission[]) f.get(g);
        } catch (RuntimeException | ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    // ------------------------------------------------------------ file targets

    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{([^{}]*)\\}");

    /** The value of a property for a policy: {@code env} first, then the system. */
    private static String property(Properties env, String key) {
        if ("/".equals(key)) return File.separator;
        String v = env == null ? null : env.getProperty(key);
        return v != null ? v : System.getProperty(key);
    }

    /**
     * {@code s} with every {@code ${property}} replaced; {@code null} if one is defined neither in
     * {@code env} nor in the system properties.
     */
    public static String expand(String s, Properties env) {
        if (s == null) return null;
        Matcher m = PROPERTY_REF.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v = property(env, m.group(1));
            if (v == null) return null;
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Danger fileDanger(String name, String actions, Properties env) {
        boolean write = hasAction(actions, "write") || hasAction(actions, "delete")
            || hasAction(actions, "execute");
        String exp = expand(name, env);
        String why = exp == null
            ? "a ${property} defined neither by --define nor in this JVM, treated as the root"
            : fileReach(exp, write, env, name.contains("${user.dir}"));
        if (why == null) return null;
        boolean medium = why.startsWith("[M]");
        if (medium) why = why.substring(3);
        String shown = "FilePermission " + (actions == null ? "" : actions + " ") + "on " + name;
        return new Danger(write && !medium ? HIGH : MEDIUM, shown + " reaches " + why);
    }

    /** A file name taken apart: optional drive, absolute or relative, segments, trailing wildcard. */
    public static final class FilePath {
        public String drive;
        public boolean absolute;
        public final List<String> segments = new ArrayList<String>();
        /** {@code "-"}, {@code "*"} or null. */
        public String wildcard;
    }

    /** Splits a file name: separators unified, {@code .} dropped, {@code ..} applied. */
    public static FilePath filePath(String name) {
        FilePath f = new FilePath();
        String p = name.trim().replace('\\', '/');
        if (p.length() >= 2 && p.charAt(1) == ':' && Character.isLetter(p.charAt(0))) {
            f.drive = p.substring(0, 2).toLowerCase(Locale.ROOT);
            p = p.substring(2);
        }
        f.absolute = p.startsWith("/");
        String[] raw = p.split("/");
        int last = raw.length - 1;
        while (last >= 0 && raw[last].length() == 0) last--;
        if (last >= 0 && (raw[last].equals("-") || raw[last].equals("*"))) {
            f.wildcard = raw[last];
            last--;
        }
        for (int i = 0; i <= last; i++) {
            String s = raw[i];
            if (s.length() == 0 || s.equals(".")) continue;
            if (s.equals("..")) {
                int n = f.segments.size();
                if (n > 0 && !f.segments.get(n - 1).equals("..")) f.segments.remove(n - 1);
                else if (!f.absolute) f.segments.add("..");
            } else {
                f.segments.add(s);
            }
        }
        return f;
    }

    /** Whether {@code prefix} is the same as, or a leading part of, {@code path} (case ignored). */
    private static boolean isPrefix(List<String> prefix, List<String> path) {
        if (prefix.size() > path.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equalsIgnoreCase(path.get(i))) return false;
        }
        return true;
    }

    private static boolean sameDrive(FilePath a, FilePath b) {
        return a.drive == null || b.drive == null || a.drive.equals(b.drive);
    }

    /**
     * Why a file name, already expanded, reaches too much; {@code null} if it does not.  Wide are:
     * all files, the current directory tree or anything above it, a root or drive, a recursive
     * name over a whole home, JDK-home or temp directory (or over a directory above one), a
     * {@code *} name in such a directory, and, for writing, any exact name below the JDK home.
     */
    static String fileReach(String expanded, boolean write, Properties env, boolean refsWorkingDir) {
        String n = expanded.trim();
        if (ALL_FILES.equals(n)) return "all files";
        FilePath f = filePath(n);
        if (f.wildcard != null && f.segments.isEmpty()) {
            return f.absolute ? "a whole root or drive tree"
                : f.drive != null ? "a whole drive-relative tree" : "the whole current directory tree";
        }
        if (!f.absolute) {
            if (f.drive == null && !f.segments.isEmpty() && f.segments.get(0).equals("..")) {
                return "a path above the current directory";
            }
            return null;
        }
        List<String[]> dirs = new ArrayList<String[]>();
        dirs.add(new String[]{"the user home", property(env, "user.home")});
        if (refsWorkingDir) dirs.add(new String[]{"the working directory", property(env, "user.dir")});
        if (write) {
            dirs.add(new String[]{"the JDK home", property(env, "java.home")});
            dirs.add(new String[]{"the temp directory", property(env, "java.io.tmpdir")});
            dirs.add(new String[]{"/tmp", "/tmp"});
            dirs.add(new String[]{"/var/tmp", "/var/tmp"});
        }
        for (String[] d : dirs) {
            if (d[1] == null || d[1].length() == 0) continue;
            FilePath dir = filePath(d[1]);
            if (!sameDrive(f, dir)) continue;
            if ("-".equals(f.wildcard) && isPrefix(f.segments, dir.segments)) return "the whole of " + d[0];
            if ("*".equals(f.wildcard) && f.segments.size() == dir.segments.size()
                    && isPrefix(f.segments, dir.segments)) {
                // a temp directory's own files: services use /tmp/* for scratch space (the deploy
                // policies do), so the pattern is a medium finding, the whole tree stays high
                boolean temp = d[0].equals("the temp directory") || d[0].startsWith("/");
                return (temp ? "[M]" : "") + "every file directly in " + d[0];
            }
            if (write && d[0].equals("the JDK home") && isPrefix(dir.segments, f.segments)) {
                return "a file of the JDK home";
            }
        }
        return null;
    }

    /**
     * Whether a FilePermission name covers all files, or a whole root, drive or current-directory
     * tree: {@code <<ALL FILES>>}, {@code -}, {@code *}, {@code /-}, {@code C:\-} and the like.
     */
    static boolean broadFile(String name) {
        if (name == null) return false;
        String e = expand(name, null);
        return e == null || fileReach(e, true, null, name.contains("${user.dir}")) != null;
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
        String home = System.getProperty("user.home");
        String jhome = System.getProperty("java.home");
        if (home != null) add(res, FILE, home + sep + "lint-probe", "write", home + sep + "-");
        if (jhome != null) {
            add(res, FILE, jhome + sep + "conf" + sep + "security" + sep + "lint-probe", "write",
                jhome + sep + "-");
        }
        add(res, ASSERT_PRINCIPAL, "*", "*", null);
        add(res, PRIVATE_CREDENTIAL, "* * \"*\"", "read", null);
        add(res, NET, "specifyStreamHandler", null, null);
        add(res, NET, "setDefaultAuthenticator", null, null);
        add(res, SSL, "setDefaultSSLContext", null, null);
        add(res, SSL, "setHostnameVerifier", null, null);
        for (String n : new String[]{"accessClassInPackage.jdk.internal.lint", "setIO", "getClassLoader",
            "modifyThread", "modifyThreadGroup", "writeFileDescriptor", "readFileDescriptor",
            "manageProcess", "enableContextClassLoaderOverride", "charsetProvider"}) {
            add(res, RUNTIME, n, null, null);
        }
        for (String n : new String[]{"clearProviderProperties.lint", "getPolicy", "createPolicy.lint"}) {
            add(res, SECURITY, n, null, null);
        }
        add(res, PROPERTY, "java.security.policy", "write", null);
        add(res, PROPERTY, "java.security.manager", "write", null);
        add(res, MBEAN, "*", "registerMBean,invoke,setAttribute", null);
        add(res, MBEAN_SERVER, "*", null, null);
        add(res, MBEAN_TRUST, "register", null, null);
        add(res, MANAGEMENT, "control", null, null);
        add(res, DOWNLOAD, null, null, "x");
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
        return probe(type, name, actions, null);
    }

    /**
     * As {@link #probe(String, String, String)} with {@code ${property}} in the name expanded from
     * {@code env} and the system properties; {@code null} if one is undefined.
     */
    public static Probe probe(String type, String name, String actions, Properties env) {
        if (type == null || ALL_PERMISSION.equals(type)) return null;
        if (name != null && name.contains("${")) {
            name = expand(name, env);
            if (name == null) return null;
        }
        Danger d = danger(type, name, actions, env);
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

    /**
     * Whether the catalogue knows the class by name although it is not on this tool's classpath
     * (the DirtyChai guards): such a class is judged by its name, so it is not "unknown".
     */
    public static boolean knownByName(String type) {
        return NATIVE.equals(type) || DC_LOAD_CLASS.equals(type) || DC_SERIAL_OBJECT.equals(type);
    }

    /** Whether the class can be loaded by this tool, without initialising it. */
    public static boolean loadable(String type) {
        try {
            Class.forName(type, false, PermissionSets.class.getClassLoader());
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * The permission of a class, name and actions as the policy would build it; {@code null} if the
     * class cannot be loaded or its constructor refuses the arguments.
     */
    public static Permission instantiate(String type, String name, String actions) {
        return create(type, name, actions);
    }

    private static Permission create(String type, String name, String actions) {
        try {
            Class<?> c = Class.forName(type, true, PermissionSets.class.getClassLoader());
            if (actions != null) {
                return (Permission) c.getConstructor(String.class, String.class).newInstance(name, actions);
            }
            // the shapes a policy entry without actions can mean, as the provider's loader tries them
            for (int i = 0; i < 3; i++) {
                try {
                    if (i == 0 && name != null) {
                        return (Permission) c.getConstructor(String.class).newInstance(name);
                    }
                    if (i == 1 && name == null) return (Permission) c.getConstructor().newInstance();
                    if (i == 2) {
                        return (Permission) c.getConstructor(String.class, String.class)
                            .newInstance(name == null ? "" : name, "");
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
