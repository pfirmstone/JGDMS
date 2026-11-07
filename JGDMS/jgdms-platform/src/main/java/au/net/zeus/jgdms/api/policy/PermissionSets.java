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
        // Operator ruling 2026-11-02: what changes WHO the code acts as is never a proxy's need.  HOLD:
        // doAs, doAsPrivileged (run as any subject), modifyPrincipals (add an identity to a subject),
        // modifyPrivateCredentials (take or plant keys and tickets), setLoginConfiguration (choose the
        // login modules that decide who a caller is).  Not held, only flagged: modifyPublicCredentials
        // (public credentials carry no authority by themselves), createLoginContext.* (it starts a login;
        // acting as the result still needs doAs), getSubject, getLoginConfiguration, refreshLoginConfiguration.
        if (AUTH.equals(type) && basic(name, new String[]{"doAs", "doAsPrivileged", "setLoginConfiguration",
                "modifyPrincipals", "modifyPrivateCredentials"}, none())) {
            return "escalation: AuthPermission " + name + " changes who the code acts as: it runs as,"
                + " becomes or logs in as another subject";
        }
        if (SERIAL.equals(type) && basic(name, new String[]{"enableSubclassImplementation"}, none())) {
            return "escalation: SerializablePermission enableSubclassImplementation overrides"
                + " the serialization streams";
        }
        return null;
    }

    /**
     * A FilePermission name is read as {@link #danger} reads it (the normaliser, the OS aliases and the
     * undefined-{@code ${property}} ruling) and flagged when that reading is a whole tree, which is HIGH
     * whatever the action.  A name that is HIGH only to write (a relative {@code ..} chain, the JDK home, a
     * temp tree) is not flagged: FLAG does not look at the action.
     *
     * @return why an entry is worth a reviewer's second look although it is written live,
     *         or {@code null}; reported by the plan and the gate, never refused
     */
    public static String flagReason(String type, String name, String actions) {
        return flagReason(type, name, actions, null);
    }

    /**
     * As {@link #flagReason(String, String, String)}; {@code env} is the environment {@link #danger} judged the
     * name in.  Without it a property defined only there would read as undefined here and flag a name that
     * {@code danger} has just found clean.
     */
    private static String flagReason(String type, String name, String actions, Properties env) {
        if (type == null || name == null) return null;
        if (FILE.equals(type) && ALL_FILES.equals(name)) return "reads or writes every file";
        String tree = FILE.equals(type) ? wholeTree(name, env) : null;
        if (tree != null) return "FilePermission " + name + " " + tree;
        if (AUTH.equals(type)) return "AuthPermission " + name + " acts on the current subject";
        if (SERIAL.equals(type)) return "SerializablePermission " + name;
        if (REFLECT.equals(type)) return "ReflectPermission " + name;
        // RuntimePermission("exitVM") is exitVM.* (the JDK renames it), so the bare name is flagged too
        if (RUNTIME.equals(type) && (basic(name, new String[]{"accessDeclaredMembers",
                "setContextClassLoader", "setFactory", "getProtectionDomain", "exitVM"},
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

    /**
     * Why a FilePermission name covers a whole root, drive, UNC share or host, or user-home tree
     * ({@code /-}, {@code C:\-}, a UNC share or host tree, {@code /home/-}, {@code ${user.home}/-}), or
     * {@code null}: the names {@link #fileReach} marks high whatever the action, and a name built on an
     * undefined {@code ${property}}, which {@link #danger} rates as the root.
     */
    private static String wholeTree(String name, Properties env) {
        if (name == null) return null;
        // Operator ruling 2026-11-07 (Opus review 2026-11-06, F6): this returned false for an undefined ${property}
        // because "danger() rates it", but ListPromotion never calls danger() (it asks holdReason and flagReason).
        // The reading is now danger()'s own (nameReach), so a name danger() reads as a whole tree is flagged too.
        String why = nameReach(name, false, env);
        if (why == null || !why.startsWith("[H]")) return null;
        return UNDEFINED_PROPERTY.equals(why) ? "names a ${property} defined nowhere here, rated as the root"
            : "covers a whole root, drive, share or home tree";
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
     * write, delete, execute or read (HIGH; {@code readlink} alone is MEDIUM) on all files, and
     * those actions on a root, drive, UNC share or host tree, or on /home or /Users (read also
     * HIGH), and on a whole user-home tree; write/delete/execute (HIGH) or read (MEDIUM) on a
     * current-directory tree; write/delete/execute (HIGH) on a whole JDK-home or temp-directory tree, the
     * deserialisation wildcard, and the identity, credential, network, TLS, JMX and management
     * permissions listed in {@link #extraDanger}.  File names are expanded with the system
     * properties; a {@code Documents and Settings} first name is read as {@code Users} and a
     * {@code <letter>$} share as that drive, on any host (operator ruling 2026-11-07).
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
        String flag = flagReason(type, name, actions, env);
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
    private static final String DC_DEFINE_CLASS = "au.zeus.jdk.authorization.guards.DefineClassPermission";
    private static final String DC_NATIVE_MEMORY = "au.zeus.jdk.authorization.guards.NativeMemoryPermission";
    private static final String SUBJECT_DELEGATION = "javax.management.remote.SubjectDelegationPermission";
    private static final String JFR = "jdk.jfr.FlightRecorderPermission";
    private static final String JDI = "com.sun.jdi.JDIPermission";
    private static final String LINK = "java.nio.file.LinkPermission";
    private static final String LINK_LANG = "java.lang.LinkPermission";
    private static final String NETWORK = "jdk.net.NetworkPermission";
    private static final String NETWORK_NET = "java.net.NetworkPermission";
    private static final String SQL = "java.sql.SQLPermission";

    /**
     * The permission classes the catalogue has an opinion on (an entry of {@link #danger}, or a
     * class it judges by name or scope).  A class outside this set is one the lint cannot rate:
     * a deployer's own permission class looks like that, so the lint notes it and says nothing more.
     */
    private static final Set<String> CATALOGUED = set(
        ALL_PERMISSION, GRANT_PERMISSION, UMBRELLA_GRANT_PERMISSION, POLICY_PERMISSION, RUNTIME,
        REFLECT, SECURITY, FILE, AUTH, SERIAL, SOCKET, NATIVE, PROPERTY, AUTHENTICATION, URL,
        DESERIALIZATION, ASSERT_PRINCIPAL, PRIVATE_CREDENTIAL, NET, SSL, MBEAN, MBEAN_SERVER,
        MBEAN_TRUST, MANAGEMENT, ATTACH, LOGGING, KRB_DELEGATION, KRB_SERVICE, DOWNLOAD, LOAD_CLASS,
        DC_LOAD_CLASS, DC_SERIAL_OBJECT, DC_DEFINE_CLASS, DC_NATIVE_MEMORY, SUBJECT_DELEGATION, JFR,
        JDI, LINK, LINK_LANG, NETWORK, NETWORK_NET, SQL);

    /** Whether the catalogue has an entry for the class, whatever the name: see {@link #danger}. */
    public static boolean catalogued(String type) {
        return type != null && CATALOGUED.contains(type);
    }

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
        // classes whose every name is dangerous: judged before the name is looked at
        if (DC_DEFINE_CLASS.equals(type)) {
            return new Danger(HIGH, "DefineClassPermission lets code define classes (Lookup.defineClass)"
                + " and so run code the class loaders never vetted");
        }
        if (DC_NATIVE_MEMORY.equals(type)) {
            return new Danger(HIGH, "NativeMemoryPermission " + name + " gives access to off-heap memory:"
                + " raw reads and writes, or exhausting it, outside the heap limit");
        }
        if (SUBJECT_DELEGATION.equals(type)) {
            return new Danger(HIGH, "SubjectDelegationPermission " + name
                + " lets the holder act over JMX as another authenticated subject");
        }
        if (JFR.equals(type)) {
            return new Danger(HIGH, "FlightRecorderPermission " + name
                + " reads the JVM's event stream or registers events");
        }
        if (JDI.equals(type)) {
            return new Danger(HIGH, "JDIPermission " + name + " lets code attach a debugger to JVMs");
        }
        if (LINK.equals(type) || LINK_LANG.equals(type)) {
            return new Danger(MEDIUM, "LinkPermission " + name + " creates links that can point outside"
                + " the granted file tree");
        }
        if (NETWORK.equals(type) || NETWORK_NET.equals(type)) {
            return new Danger(MEDIUM, "NetworkPermission " + name + " changes or reads socket options"
                + " (flow control, keep-alive) of connections it did not open");
        }
        if (name == null) return null;
        if (SOCKET.equals(type) && name.matches("\\*\\.[^.:*]+(:.*)?")) {
            // operator ruling 2026-11-02: a whole top-level domain (*.com) is as broad as any host, so MEDIUM
            return new Danger(MEDIUM, "SocketPermission " + name + " reaches every host of a top-level domain");
        }
        if (SQL.equals(type)) {
            if (basic(name, new String[]{"setLog", "setSyncFactory", "setNetworkTimeout", "callAbort",
                    "deregisterDriver"}, none())) {
                return new Danger(MEDIUM, "SQLPermission " + name + " changes JDBC global state (log"
                    + " stream, RowSet factory, driver registry) or aborts connections");
            }
            return null;
        }
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
            // operator ruling 2026-11-03: shutdownHooks runs code at JVM exit
            if (basic(name, new String[]{"shutdownHooks"}, none())) {
                return new Danger(MEDIUM, "RuntimePermission " + name + " registers code that runs at JVM exit"
                    + " and can delay it");
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
            // operator ruling 2026-11-03: getDomainCombiner reaches protection-domain information
            if (basic(name, new String[]{"getDomainCombiner"}, none())) {
                return new Danger(MEDIUM, "SecurityPermission " + name
                    + " reaches the protection-domain information of an access-control context");
            }
            if (basic(name, new String[]{"getPolicy"}, new String[]{"createPolicy."})) {
                return new Danger(MEDIUM, "SecurityPermission " + name + " reads or creates a policy");
            }
            // operator ruling 2026-11-02: these two BasicPermission wildcards are rated here, not allow-listed
            if (name.equals("authProvider.*") || name.equals("getProperty.*")) {
                return new Danger(MEDIUM, "SecurityPermission " + name + (name.startsWith("authProvider")
                    ? " obtains any named authentication provider"
                    : " reads any Security property by name"));
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

    private static final String UNDEFINED_PROPERTY =
        "[H]a ${property} defined neither by --define nor in this JVM, treated as the root";

    /** The one reading of a FilePermission name that {@link #danger} and {@link #flagReason} share. */
    private static String nameReach(String name, boolean write, Properties env) {
        String exp = expand(name, env);
        return exp == null ? UNDEFINED_PROPERTY : fileReach(exp, write, env, name.contains("${user.dir}"));
    }

    private static Danger fileDanger(String name, String actions, Properties env) {
        boolean write = hasAction(actions, "write") || hasAction(actions, "delete")
            || hasAction(actions, "execute");
        String why = nameReach(name, write, env);
        if (why == null) return null;
        boolean medium = why.startsWith("[M]");
        boolean high = why.startsWith("[H]");
        if (medium || high) why = why.substring(3);
        // operator ruling 2026-11-03: read of <<ALL FILES>> is an unscoped whole-tree read, HIGH like
        // "/-" read; readlink alone (links, not contents) stays MEDIUM
        if ("all files".equals(why) && hasAction(actions, "read")) high = true;
        String shown = "FilePermission " + (actions == null ? "" : actions + " ") + "on " + name;
        return new Danger((write && !medium) || high ? HIGH : MEDIUM, shown + " reaches " + why);
    }

    /** A file name taken apart: optional drive, absolute or relative, segments, trailing wildcard. */
    public static final class FilePath {
        public String drive;
        public boolean absolute;
        /** A UNC name (two leading slashes or backslashes, then host and share): a network host or share. */
        public boolean unc;
        public final List<String> segments = new ArrayList<String>();
        /** {@code "-"}, {@code "*"} or null. */
        public String wildcard;
        /**
         * A Win32 device-namespace name ({@code //./} before the first name).  Only the prefix is taken off:
         * {@code segments} start at the device name, which is not a drive and not a UNC share.
         */
        public boolean device;
    }

    private static int leadingSlashes(String p) {
        int n = 0;
        while (n < p.length() && p.charAt(n) == '/') n++;
        return n;
    }

    /**
     * {@code p} (separators unified) without any long-path prefix.  Opus review 2026-11-06 (F2): three steps
     * of the JDK remove it, in this order: WinNTFileSystem.normalize strips a raw prefix that was written
     * with backslashes (stripLongOrUNCPrefix, DirtyChai WinNTFileSystem.java:69, called at :132), then
     * normalizePrefix collapses the run of leading separators (:225-252), then WindowsPathParser.parse strips
     * the prefix again (WindowsPathParser.java:66-67).  So "///?/C:/Users/-" and a doubled prefix are both
     * C:/Users/-.  It is stripped repeatedly, a superset of those strips, and takes one separator after the
     * "?": the rest of a run belongs to the next prefix, while "UNC" may follow the whole run because the
     * collapse has happened by then.  "UNC" is matched without regard to case although the JDK's test is
     * case-sensitive: the JDK refuses "//?/unc/server/share/-" and reads the backslash form as the relative
     * path unc/server/share (both measured 2026-11-06), so HIGH is a conservative rating of a name that
     * grants no share there, kept because testWholeTreeReadVariantsAreHigh has pinned it since 0eb9f0cc7.
     */
    private static String withoutLongPathPrefix(String p) {
        for (;;) {
            int lead = leadingSlashes(p);
            String rest = p.substring(lead);
            if (lead < 2 || !(rest.equals("?") || rest.startsWith("?/"))) return p;
            rest = rest.substring(1);
            if (rest.startsWith("/")) rest = rest.substring(1);
            String name = rest.substring(leadingSlashes(rest));
            if (name.regionMatches(true, 0, "UNC", 0, 3) && (name.length() == 3 || name.charAt(3) == '/')) {
                p = "//" + (name.length() > 4 ? name.substring(4) : "");
            } else {
                p = rest;
            }
        }
    }

    /**
     * Sets {@code f.device} and returns {@code p} without the "//./" root of a Win32 device-namespace name;
     * the name is deliberately not resolved.  Opus normaliser review 2026-11-07 (N1, N2): the JDK reads
     * "//./X/" as a UNC root (host ".", share X) and clamps ".." there, so "//./x/../Volume{guid}/Users/-" has
     * the npath "//./x/Volume{guid}/Users" (measured on the DirtyChai build JDK), while Win32 roots the name at
     * "//./" alone and applies "..", "." and one trailing dot to the device name itself: "//./x/../Volume{guid}/Users"
     * and "//./UNC./localhost/C$/Users" reach C:/Users (live on the review host).  Neither reading gives
     * the other's reach, and the 2026-11-06 model of the volume, UNC and GLOBALROOT names as drive and share
     * roots both lowered base ratings and missed spellings, so fileReach rates every wildcard name here HIGH.
     * One separator, as in "/Device/Mup/server/share/-", is no device name: Win32 reads it as a path below
     * the current drive (File.exists is false for "/Device/Mup/localhost/C$/Users").
     */
    private static String withoutDeviceRoot(String p, FilePath f) {
        int lead = leadingSlashes(p);
        String rest = p.substring(lead);
        if (lead < 2 || !(rest.equals(".") || rest.startsWith("./"))) return p;
        f.device = true;
        return rest.substring(1);
    }

    /**
     * Win32 removes one trailing dot from a path segment (Opus review 2026-11-06 (F5): Files.isSameFile
     * "C:\Users.\peter" == "C:\Users\peter" on the review host), which the JDK does not do lexically.
     * A name made only of dots is left alone, so that "..." is not read as ".." .
     */
    private static String withoutTrailingDot(String s) {
        return s.endsWith(".") && s.replace(".", "").length() > 0 ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Splits a file name the way {@code java.io.FilePermission} reads a Windows name, whatever host the lint
     * runs on, adding Win32's removal of one trailing dot per segment: separators unified, the long-path
     * prefix removed, {@code .} dropped, {@code ..} applied and only then the last name tested for {@code -}.
     * A {@code *} is a wildcard only as the raw last name, because the JDK tests {@code endsWith("*")} before
     * it normalises ({@code C:\Users\*\.} is invalid there).  Two limits: a device-namespace name
     * ({@code //./}) is flagged and not resolved, and
     * {@code ..} may pop a UNC host or share.  A name the JDK refuses is parsed by the same rules, so what
     * it is rated says nothing about what the JDK grants.
     */
    public static FilePath filePath(String name) {
        FilePath f = new FilePath();
        String p = withoutDeviceRoot(withoutLongPathPrefix(name.trim().replace('\\', '/')), f);
        if (!f.device) {
            // Opus review 2026-11-03 (F3): java.io.File on Windows drops the slashes before a drive letter, so
            // "/C:/-" is the whole C: drive; read it as a drive whatever the host the lint runs on.
            int lead = leadingSlashes(p);
            boolean driveAfterSlashes = lead > 0 && p.length() >= lead + 2 && p.charAt(lead + 1) == ':'
                    && Character.isLetter(p.charAt(lead));
            if (driveAfterSlashes) p = p.substring(lead);
            // Opus review 2026-11-03 (F2): java.io.File on Windows collapses three or more leading separators
            // into the same UNC prefix as two, so "///server/share/-" is a whole share; any run of two or more
            // slashes followed by a name is a UNC name (conservative where the host reads it as a plain path).
            f.unc = !driveAfterSlashes && lead >= 2 && p.length() > lead;
            if (p.length() >= 2 && p.charAt(1) == ':' && Character.isLetter(p.charAt(0))) {
                f.drive = p.substring(0, 2).toLowerCase(Locale.ROOT);
                p = p.substring(2);
            }
        }
        f.absolute = f.device || p.startsWith("/");
        String[] raw = p.split("/");
        int last = raw.length - 1;
        while (last >= 0 && raw[last].length() == 0) last--;
        // Opus review 2026-11-06 (F1): FilePermission replaces a trailing "*" by "-", normalises, and only then
        // tests the last name (DirtyChai FilePermission.java:373-389), so "C:/-/." and "C:/-/x/.." are the whole
        // drive while "C:/Users/*/." is a name the JDK refuses.  A "*" counts only as the raw last name; a
        // trailing separator is let through, the wider reading of a name the JDK refuses.
        boolean star = last >= 0 && raw[last].equals("*");
        if (star) raw[last] = "-";
        // Opus normaliser review 2026-11-07 (N3): ".." pops a UNC host and share here.  The Windows JDK clamps
        // at the share ("//server/share/../-/-" has the npath "//server/share/-", measured), but on a Unix JDK
        // "//" is "/", so "//home/alice/../bob/-" is one user's home; the catalogue is host-blind and takes
        // the reading that rates wider.  The 2026-11-06 floor at host and share, which narrowed these names
        // to unrated on every host, is withdrawn.
        List<String> names = new ArrayList<String>();
        for (int i = 0; i <= last; i++) {
            String s = raw[i];
            if (s.length() == 0 || s.equals(".")) continue;
            if (s.equals("..")) {
                int n = names.size();
                if (n > 0 && !names.get(n - 1).equals("..")) names.remove(n - 1);
                else if (!f.absolute) names.add("..");
            } else {
                names.add(s);
            }
        }
        int n = names.size();
        if (n > 0 && names.get(n - 1).equals("-")) {
            f.wildcard = star ? "*" : "-";
            names.remove(n - 1);
        }
        for (String s : names) f.segments.add(withoutTrailingDot(s));
        return f;
    }

    private static boolean isDriveShare(String share) {
        if (share.length() != 2 || share.charAt(1) != '$') return false;
        char c = share.charAt(0);
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    /**
     * {@link #filePath} with three OS aliases of a ruled tree read as the tree they reach (the third, a profile's
     * {@code Local Settings} as {@code AppData\Local}, is described at its fold below).  Operator ruling
     * 2026-11-07 (Opus review 2026-11-06, F7), host-blind and without asking the OS: the review measured
     * {@code Files.isSameFile} true for {@code C:\Documents and Settings\peter} (a junction) and
     * {@code C:\Users\peter}, and for {@code \\localhost\C$\Users\peter} (the admin share) and the same, so a
     * first name {@code Documents and Settings} below a root is {@code Users} and a share named a letter and
     * {@code $} is that drive, whatever the host.  Every other share keeps the ruling that a named directory
     * below it is unrated: {@code ADMIN$}, {@code IPC$}, {@code CC$} and {@code C$x} are no drive, and
     * {@code \\h\ADMIN$\-} is HIGH as a whole share.  The JDK does not model either alias lexically, so the
     * differential test cannot score this.  A relative or drive-relative name is left alone, as {@code C:Users\-} is.
     */
    public static FilePath aliased(String name) {
        FilePath f = filePath(name);
        List<String> s = f.segments;
        if (f.unc && s.size() >= 2 && isDriveShare(s.get(1))) {
            f.drive = s.get(1).substring(0, 1).toLowerCase(Locale.ROOT) + ":";
            f.unc = false;
            s.subList(0, 2).clear();
        }
        // operator ruling 2026-11-07 (Q1): DOCUME~1 is the 8.3 name of that same junction
        if (f.absolute && !f.unc && !f.device && !s.isEmpty()
                && (s.get(0).equalsIgnoreCase("Documents and Settings") || s.get(0).equalsIgnoreCase("DOCUME~1"))) {
            s.set(0, "Users");
        }
        // operator ruling 2026-11-07: "Local Settings" in a profile is the junction to AppData\Local, so the temp
        // directory is reached as Users\<name>\Local Settings\Temp as well as Users\<name>\AppData\Local\Temp.
        if (f.absolute && !f.unc && !f.device && s.size() >= 3 && s.get(0).equalsIgnoreCase("Users")
                && (s.get(2).equalsIgnoreCase("Local Settings") || s.get(2).equalsIgnoreCase("LOCALS~1"))) {
            s.set(2, "AppData");
            s.add(3, "Local");
        }
        return f;
    }

    private static final Pattern SHORT_NAME =
        Pattern.compile("[^~]{1,6}~[0-9]+(\\.[^.~]{0,3})?");

    /**
     * Whether a recursive name this close to the root has an 8.3 short name ({@code DOCUME~1}, {@code USERS~1},
     * {@code PETER~1}) among its first two names, so it may reach a user home or the directory holding every
     * home.  Operator ruling 2026-11-07, host-blind: the file system, not the name, says what a short name
     * stands for and 8.3 generation can be off on a volume, so the shape alone is rated.  The names this rates
     * HIGH include real directories such as {@code C:\PROGRA~1\-}; no PolicyLint option or corpus row clears one
     * (the corpus allow-list takes no HIGH row), which is operator question Q3 of the 2026-11-07 Opus review.
     * Deeper names stay unrated by the ruling that bounds the rule to the first two names.
     */
    public static boolean mayBeShortName(FilePath f) {
        if (!f.absolute || f.device || !"-".equals(f.wildcard) || f.segments.size() > 2) return false;
        for (String s : f.segments) {
            if (SHORT_NAME.matcher(s).matches()) return true;
        }
        return false;
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
     * {@code *} name in such a directory, a recursive name with an 8.3 short-name shape among its first two
     * names ({@link #mayBeShortName}), and, for writing, any exact name below the JDK home.
     */
    static String fileReach(String expanded, boolean write, Properties env, boolean refsWorkingDir) {
        String n = expanded.trim();
        if (ALL_FILES.equals(n)) return "all files";
        FilePath f = aliased(n);
        // Opus normaliser review 2026-11-07 (N1, N2, R2-1): the JDK and Win32 root a "//./" name differently, so no
        // reading of its device name says what a wildcard grant reaches; any depth is HIGH, whatever the action.
        // A "*" is not recursive but is not harmless: the JDK clamps ".." at "//./X/", so "//./pipe/*" implies
        // "//./pipe/../Volume{guid}" and "//./pipe/../PhysicalDrive0" (read and write, measured on the DirtyChai
        // build JDK), and Win32 opens the raw device (access denied, not "not found", on the review host).  An
        // exact name is not rated: it implies only itself, as "/dev/sda" does.  Whether this is the ruling is
        // operator question Q3.
        if (f.device && f.wildcard != null) {
            return "[H]a wildcard name in the Win32 device namespace, which the JDK and the OS root differently";
        }
        if (f.wildcard != null && f.segments.isEmpty()) {
            // operator ruling 2026-11-02: reading a whole root or drive tree is HIGH, not only writing it
            if ("-".equals(f.wildcard) && f.absolute) {
                return "[H]a whole root or drive tree";
            }
            return f.absolute ? "a whole root or drive tree"
                : f.drive != null ? "a whole drive-relative tree" : "the whole current directory tree";
        }
        if (!f.absolute) {
            // Opus review 2026-11-06 (F3): 0eb9f0cc7 made "/C:../../-" a drive-relative name, and this test
            // left it unrated while "../../-" is rated; "C:.." climbs out of that drive's current directory
            // just the same (Files.isSameFile "C:..\..\..\Windows" == "C:\Windows" on the review host).
            if (!f.segments.isEmpty() && f.segments.get(0).equals("..")) {
                return "a path above the current directory";
            }
            return null;
        }
        if ("-".equals(f.wildcard)) {
            // operator ruling 2026-11-03: a whole UNC share or host, and the parent of every user home,
            // are whole trees whatever the platform the lint runs on or the action
            if (f.unc && f.segments.size() <= 2) return "[H]a whole network share or host tree";
            if (f.segments.size() == 1 && (f.segments.get(0).equalsIgnoreCase("home")
                    || f.segments.get(0).equalsIgnoreCase("Users"))) {
                return "[H]the whole of the directory holding every user home";
            }
            // operator ruling 2026-11-03 (Opus review F4): one user's whole home, on every platform
            if (f.segments.size() == 2 && (f.segments.get(0).equalsIgnoreCase("home")
                    || f.segments.get(0).equalsIgnoreCase("Users"))
                    || f.segments.size() == 2 && f.segments.get(0).equalsIgnoreCase("var")
                    && f.segments.get(1).equalsIgnoreCase("root")) {
                return "[H]the whole of one user's home";
            }
            // operator ruling 2026-11-03: the root user's home is a whole tree of credentials and keys
            if (f.segments.size() == 1 && f.segments.get(0).equalsIgnoreCase("root")) {
                return "[H]the whole of the root user's home";
            }
            if (mayBeShortName(f)) {
                return "[H]a whole tree named by an 8.3 short name, which may stand for a user home";
            }
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
            FilePath dir = aliased(d[1]);
            if (!sameDrive(f, dir)) continue;
            if ("-".equals(f.wildcard) && isPrefix(f.segments, dir.segments)) {
                // operator ruling 2026-11-02: reading the whole user-home tree is HIGH as well
                return (d[0].equals("the user home") ? "[H]" : "") + "the whole of " + d[0];
            }
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
        add(res, AUTH, "doAs", null, null);
        add(res, AUTH, "setLoginConfiguration", null, null);
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
        if (home != null) add(res, FILE, home + sep + "lint-probe", "read", home + sep + "-");
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
        add(res, DC_DEFINE_CLASS, null, null, null);
        add(res, DC_NATIVE_MEMORY, "Arena", null, null);
        // SubjectDelegationPermission is catalogued but has no probe: the providers do not list it (measured)
        add(res, JFR, "accessFlightRecorder", null, null);
        add(res, JDI, "virtualMachineManager", null, null);
        add(res, LINK, "symbolic", null, null);
        add(res, NETWORK, "setOption.lint", null, null);
        add(res, SQL, "setLog", null, null);
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
