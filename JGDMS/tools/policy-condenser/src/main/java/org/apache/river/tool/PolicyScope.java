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
import java.util.Collection;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * The symbolic half of the dangerous-policy gate: decides from a grant's own text whether the
 * grant can reach code or a caller an attacker controls, without asking a provider.
 *
 * <p>The probe half ({@link PolicyVerdict}) asks about a few fixed subjects, and each fixed subject
 * leaves another wildcard form that is not asked about.  This class reads the codeBase pattern, the
 * signedBy and digest clauses and the principal list instead, so the verdict does not depend on a
 * sample.  The lint raises a finding when either half does.
 *
 * <p>Attacker-controllable scope:
 * <ul>
 * <li>no codeBase (or a codeBase with a property undefined here, which the production parser
 *     drops), when no signedBy, digest or named principal narrows it: {@link Reach#STRANGER};</li>
 * <li>an {@code http}, {@code https} or {@code ftp} codeBase whose host contains a wildcard or is
 *     empty, or whose port is a wildcard or a range: {@link Reach#STRANGER}.  An exact host is
 *     pinned and reaches nothing; the grant is reported at INFO if it holds anything dangerous;</li>
 * <li>a {@code file:} codeBase that is relative, a root or drive, in a temp directory or a
 *     Downloads directory, written with {@code ${user.home}}, {@code ${java.io.tmpdir}} or
 *     {@code ${user.dir}}, or that is a recursive pattern over a directory above one of those:
 *     {@link Reach#FILE}.  {@code jar:} forms are judged by what they wrap;</li>
 * <li>a {@code file:} codeBase with a host other than {@code localhost} ({@code file://host/share/-},
 *     {@code file:////host/share/-}, a UNC path): a network share, so {@link Reach#STRANGER};</li>
 * <li>a codeBase scheme other than file, jrt, jar, http, https and ftp: not recognised, so treated as
 *     attacker-controlled ({@link Reach#STRANGER}), never as pinned.  {@code jar:} is judged by the
 *     URL it wraps, however deeply nested;</li>
 * <li>{@code jrt:/-} and {@code jrt:/*}: {@link Reach#PLATFORM};</li>
 * <li>a principal list that every entry of which is a wildcard class, or a wildcard (or prefix)
 *     name of a class whose names an outside party issues (a SPIFFE, JWT, Kerberos, X.500 or user
 *     principal), of a class this tool cannot load, or of a class no principal could be made of:
 *     {@link Reach#PRINCIPAL}.</li>
 * </ul>
 * A digest, a signer or a named principal pins the grant.  A wildcard name of a resolvable class
 * this list does not name is left to the provider, which is given a stand-in principal of that
 * class.
 */
final class PolicyScope {

    /** Whom a grant reaches. */
    enum Reach { NONE, STRANGER, PRINCIPAL, FILE, PLATFORM }

    /** The verdict for one grant. */
    static final class Verdict {
        Reach reach = Reach.NONE;
        /** Why, for the report. */
        String why = "";
        /** The codeBase is one exact remote host: reported at INFO when the grant is dangerous. */
        boolean pinnedRemote;
    }

    /** What a codeBase string is. */
    enum Kind { ABSENT, REMOTE_WILD, REMOTE_PINNED, FILE_WRITABLE, FILE_PINNED, JRT_BROAD, JRT_PINNED, OTHER }

    /** A classified codeBase. */
    static final class CodeBase {
        final Kind kind;
        final String why;

        CodeBase(Kind kind, String why) {
            this.kind = kind;
            this.why = why;
        }
    }

    private static final Pattern WRITABLE_PROPERTY =
            Pattern.compile("\\$\\{(user\\.home|java\\.io\\.tmpdir|user\\.dir)\\}");
    private static final String[] OUTSIDE_ISSUED = {"SpiffePrincipal", "JwtPrincipal",
        "KerberosPrincipal", "X500Principal", "UserPrincipal", "NTUserPrincipal", "UnixPrincipal",
        "LdapPrincipal", "SolarisPrincipal", "NTDomainPrincipal"};

    private PolicyScope() {}

    static Verdict scope(ProxyPolicyGenerator.GrantBlock g, Properties env,
            Collection<String> unbuildable) {
        Verdict v = new Verdict();
        if (present(g.digest)) {
            v.why = "scoped by a digest";
            return v;
        }
        if (hasSigner(g.signedBy)) {
            v.why = "scoped by signedBy";
            return v;
        }
        boolean anyPrincipal = !g.principals.isEmpty();
        boolean wildPrincipal = anyPrincipal;
        for (String[] pr : g.principals) {
            wildPrincipal &= attackerPrincipal(pr, unbuildable);
        }
        if (anyPrincipal && !wildPrincipal) {
            v.why = "scoped by a named principal";
            return v;
        }
        CodeBase cb = classify(g.codebase, env);
        switch (cb.kind) {
            case ABSENT:
            case REMOTE_WILD:
                v.reach = wildPrincipal ? Reach.PRINCIPAL : Reach.STRANGER;
                v.why = (cb.kind == Kind.ABSENT ? "no codeBase" : cb.why)
                        + (wildPrincipal ? " and a principal list that any authenticated subject meets" : "");
                return v;
            case REMOTE_PINNED:
                v.pinnedRemote = true;
                v.why = cb.why;
                return v;
            case FILE_WRITABLE:
                v.reach = Reach.FILE;
                v.why = cb.why;
                return v;
            case JRT_BROAD:
                v.reach = Reach.PLATFORM;
                v.why = cb.why;
                return v;
            default:
                v.why = cb.why;
                return v;
        }
    }

    /** Whether the principal entry {@code {class, name}} can be met by a principal an attacker holds. */
    static boolean attackerPrincipal(String[] pr, Collection<String> unbuildable) {
        String cls = pr[0];
        String name = pr[1];
        if ("*".equals(cls)) return true;
        if (name != null && name.indexOf('*') >= 0 && namespacePrefix(name)) return false;
        boolean wildName = name == null || name.indexOf('*') >= 0;
        if (!wildName) return false;
        if (cls == null) return true;
        for (String o : OUTSIDE_ISSUED) {
            if (cls.equals(o) || cls.endsWith("." + o)) return true;
        }
        if (unbuildable != null && unbuildable.contains(cls)) return true;
        return !PermissionSets.loadable(cls);
    }

    /**
     * A prefix pattern that fixes a trust domain and a path below it ({@code spiffe://td/host/bae/*}):
     * the issuer of the identities vouches for that namespace, so it is not a wildcard in the sense
     * of "anyone".  A bare {@code *}, a suffix pattern, a star inside the name, or a prefix that
     * names no domain and path is.
     */
    private static boolean namespacePrefix(String name) {
        int star = name.indexOf('*');
        if (star != name.length() - 1) return false;
        return name.substring(0, star).matches("^[A-Za-z][A-Za-z0-9+.-]*://[^/*]+/[^*]+$");
    }

    /** Classifies a codeBase after expanding its properties as the production parser does. */
    static CodeBase classify(String codebase, Properties env) {
        String raw = codebase == null ? "" : codebase.trim();
        if (raw.length() == 0) return new CodeBase(Kind.ABSENT, "no codeBase");
        String exp = PermissionSets.expand(raw, env);
        if (exp == null) {
            return new CodeBase(Kind.ABSENT, "codeBase with a property undefined here, which the"
                    + " production parser drops");
        }
        String s = exp.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        // jar: wraps another URL (jar:jar:... too): the nested one is what is judged
        while (lower.startsWith("jar:")) {
            s = s.substring(4);
            int bang = s.indexOf("!/");
            if (bang >= 0) s = s.substring(0, bang);
            lower = s.toLowerCase(Locale.ROOT);
        }
        if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("ftp:")) {
            return remote(s);
        }
        if (lower.startsWith("jrt:")) return jrt(s.substring(4));
        boolean writableProperty = WRITABLE_PROPERTY.matcher(raw).find();
        if (lower.startsWith("file:")) return file(s.substring(5), writableProperty);
        if (s.equals("-") || s.equals("*")) {
            return new CodeBase(Kind.OTHER, "codeBase is a bare wildcard, which names no location");
        }
        if (lower.indexOf(':') < 0 || lower.matches("^[a-z]:[\\\\/].*")) {
            return file(s, writableProperty); // no scheme: a path
        }
        // a scheme the lint does not recognise cannot be shown to be pinned: fail closed
        return new CodeBase(Kind.REMOTE_WILD, "codeBase scheme " + lower.substring(0, lower.indexOf(':'))
                + " is not one the lint recognises (file, jrt, jar:, http, https, ftp): treated as"
                + " attacker-controlled");
    }

    private static CodeBase remote(String s) {
        int i = s.indexOf("//");
        if (i < 0) return new CodeBase(Kind.REMOTE_WILD, "remote codeBase without a host");
        String rest = s.substring(i + 2);
        int end = rest.length();
        for (int k = 0; k < rest.length(); k++) {
            char c = rest.charAt(k);
            if (c == '/' || c == '?' || c == '#') {
                end = k;
                break;
            }
        }
        String auth = rest.substring(0, end);
        int at = auth.lastIndexOf('@');
        if (at >= 0) auth = auth.substring(at + 1);
        String host = auth;
        String port = null;
        if (auth.startsWith("[")) {
            int rb = auth.indexOf(']');
            host = rb < 0 ? auth : auth.substring(0, rb + 1);
            String after = rb < 0 ? "" : auth.substring(rb + 1);
            if (after.startsWith(":")) port = after.substring(1);
        } else {
            int c = auth.lastIndexOf(':');
            if (c >= 0) {
                host = auth.substring(0, c);
                port = auth.substring(c + 1);
            }
        }
        if (host.length() == 0 || host.indexOf('*') >= 0) {
            return new CodeBase(Kind.REMOTE_WILD, "codeBase host " + (host.length() == 0 ? "(empty)" : host)
                    + " is a wildcard: anyone who can serve from a matching host reaches the grant");
        }
        if (port != null && (port.indexOf('*') >= 0 || port.indexOf('-') >= 0 || port.length() == 0)) {
            return new CodeBase(Kind.REMOTE_WILD, "codeBase port " + (port.length() == 0 ? "(empty)" : port)
                    + " is a wildcard or range: anything listening on those ports of " + host
                    + " reaches the grant");
        }
        return new CodeBase(Kind.REMOTE_PINNED, "codeBase pins the host " + host);
    }

    private static CodeBase jrt(String path) {
        String p = path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        if (p.length() == 0 || p.equals("-") || p.equals("*")) {
            return new CodeBase(Kind.JRT_BROAD, "jrt: codeBase matches every platform module");
        }
        return new CodeBase(Kind.JRT_PINNED, "jrt: codeBase names one module");
    }

    private static CodeBase file(String path, boolean writableProperty) {
        String p = path.replace('\\', '/');
        // //host/share, ////host/share and \\host\share name a share on another machine (a UNC
        // path on Windows); only an empty host or localhost is this machine
        while (p.startsWith("//")) {
            int i = p.indexOf('/', 2);
            String host = i < 0 ? p.substring(2) : p.substring(2, i);
            if (host.length() > 0 && !host.equalsIgnoreCase("localhost")) {
                return new CodeBase(Kind.REMOTE_WILD, "file: codeBase on the host " + host
                        + ", a network share another party can serve code from");
            }
            p = i < 0 ? "/" : p.substring(i);
        }
        if (p.matches("^/[A-Za-z]:([\\\\/].*)?$")) p = p.substring(1);
        PermissionSets.FilePath f = PermissionSets.filePath(p);
        if (!f.absolute && f.drive == null) {
            return new CodeBase(Kind.FILE_WRITABLE, "relative file: codeBase: it depends on the"
                    + " working directory of whoever runs the service");
        }
        if (f.segments.isEmpty()) {
            return new CodeBase(Kind.FILE_WRITABLE, "file: codeBase is a drive or filesystem root");
        }
        if (writableProperty) {
            return new CodeBase(Kind.FILE_WRITABLE, "file: codeBase in a user-writable location"
                    + " (user home, temp or working directory)");
        }
        String first = f.segments.get(0).toLowerCase(Locale.ROOT);
        boolean tmp = first.equals("tmp") || first.equals("temp")
                || (first.equals("var") && f.segments.size() > 1
                    && f.segments.get(1).equalsIgnoreCase("tmp"))
                || (first.equals("dev") && f.segments.size() > 1 && f.segments.get(1).equalsIgnoreCase("shm"));
        for (String s : f.segments) {
            String l = s.toLowerCase(Locale.ROOT);
            tmp |= l.equals("downloads") || l.equals("temp") || l.equals("tmp");
        }
        if (tmp) {
            return new CodeBase(Kind.FILE_WRITABLE, "file: codeBase in a temp or Downloads directory");
        }
        if ("-".equals(f.wildcard)) {
            for (String prop : new String[]{"user.home", "java.io.tmpdir", "user.dir"}) {
                String d = System.getProperty(prop);
                if (d == null) continue;
                PermissionSets.FilePath dir = PermissionSets.filePath(d);
                if ((f.drive == null || dir.drive == null || f.drive.equals(dir.drive))
                        && startsWith(dir.segments, f.segments)) {
                    return new CodeBase(Kind.FILE_WRITABLE, "file: codeBase over a directory above "
                            + prop);
                }
            }
        }
        return new CodeBase(Kind.FILE_PINNED, "file: codeBase in a fixed directory");
    }

    private static boolean startsWith(java.util.List<String> path, java.util.List<String> prefix) {
        if (prefix.size() > path.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equalsIgnoreCase(path.get(i))) return false;
        }
        return true;
    }

    private static boolean present(String s) {
        return s != null && s.trim().length() > 0;
    }

    private static boolean hasSigner(String s) {
        if (s == null) return false;
        for (String t : s.split(",")) if (t.trim().length() > 0) return true;
        return false;
    }
}
