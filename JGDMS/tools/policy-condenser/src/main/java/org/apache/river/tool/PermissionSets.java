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
package org.apache.river.tool;

import java.security.Permission;
import java.security.UnresolvedPermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.river.api.security.AdvisoryPermissionParser;

/**
 * The permission classes and entries the promotion tooling treats specially,
 * defined once for {@link ProxyPolicyGenerator}, {@link ListPromotion} and
 * {@link PolicyLint}.
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
final class PermissionSets {

    static final String ALL_PERMISSION = "java.security.AllPermission";
    static final String GRANT_PERMISSION = "net.jini.security.GrantPermission";
    static final String UMBRELLA_GRANT_PERMISSION = "net.jini.security.policy.UmbrellaGrantPermission";
    static final String POLICY_PERMISSION = "org.apache.river.api.security.PolicyPermission";

    /**
     * Policy-grant meta-capabilities and blanket authority, never a proxy's
     * declared need.  The platform's {@code VerdictPermissionMapper} keeps its own
     * private copy of the first three (it lives in a module this one may not
     * change); {@code PermissionSetsTest} fails if that copy gains a name this set lacks.
     */
    static final Set<String> META = set(
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
    static final Set<String> GATE = set(
        "au.zeus.jdk.authorization.guards.LoadClassPermission",
        "net.jini.loader.LoadClassPermission",
        "java.net.URLPermission",
        "net.jini.loader.DownloadPermission",
        "net.jini.loader.pref.INCONCLUSIVEPermit",
        "net.jini.loader.pref.BootstrapPermission");

    /** Why a meta class never reaches a list. */
    static final String META_REASON = "policy-grant meta-capability: never a proxy's declared need";

    /** Why a gate class is dropped; also the reason promotion refuses a list that holds one. */
    static final String GATE_REASON = "admission-gate permission: the proxy provider grants "
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
    static String excludedReason(String type) {
        if (META.contains(type)) return META_REASON;
        if (GATE.contains(type)) return GATE_REASON;
        return null;
    }

    /**
     * @return why an entry lets code leave the SecurityManager in one step, whatever else is
     *         granted, so it is held back; {@code null} if it is not in the HOLD set
     */
    static String holdReason(String type, String name, String actions) {
        if (type == null) return null;
        if (NATIVE.equals(type)) return "escalation: native code invocation";
        if (name == null) return null;
        if (RUNTIME.equals(type) && basic(name, new String[]{"setSecurityManager",
                "createClassLoader", "createAccessControlContext"}, new String[]{"loadLibrary."})) {
            return "escalation: RuntimePermission " + name + " replaces or escapes the SecurityManager";
        }
        if (REFLECT.equals(type) && basic(name, new String[]{"suppressAccessChecks"}, none())) {
            return "escalation: ReflectPermission suppressAccessChecks defeats every access check";
        }
        if (SECURITY.equals(type) && basic(name, new String[]{"createAccessControlContext",
                "setPolicy"}, new String[]{"setProperty."})) {
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
    static String flagReason(String type, String name, String actions) {
        if (type == null || name == null) return null;
        if (FILE.equals(type) && ALL_FILES.equals(name)) return "reads or writes every file";
        if (AUTH.equals(type)) return "AuthPermission " + name + " acts on the current subject";
        if (SERIAL.equals(type)) return "SerializablePermission " + name;
        if (REFLECT.equals(type)) return "ReflectPermission " + name;
        if (RUNTIME.equals(type) && (basic(name, new String[]{"accessDeclaredMembers",
                "setContextClassLoader", "setFactory"}, new String[]{"accessClassInPackage.sun."}))) {
            return "RuntimePermission " + name + " reaches into JDK internals or global state";
        }
        if (SOCKET.equals(type) && (name.equals("*") || name.startsWith("*:"))) {
            return "SocketPermission to any host";
        }
        return null;
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
    static String holdReason(String entry) {
        String[] t = triple(entry);
        return t == null ? null : holdReason(t[0], t[1], t[2]);
    }

    /** {@link #flagReason} of an entry line; {@code null} if it does not parse. */
    static String flagReason(String entry) {
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
