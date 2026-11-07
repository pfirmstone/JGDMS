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
 * <li>a {@code file:} codeBase is local only as one slash ({@code file:/path}), three slashes (an
 *     empty authority) or behind {@code //localhost}.  Two slashes and a host, and four or more
 *     slashes before a name ({@code file://host/share/-}, {@code file:////host/share/-},
 *     {@code file://///host/share/-}, a UNC path, a backslash form) are a network share, because
 *     Windows opens a run of four or more slashes as the UNC share {@code \\host\share}: so
 *     {@link Reach#STRANGER};</li>
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
        if (lower.startsWith("file:")) return file(s.substring(5), writableProperty, true);
        if (s.equals("-") || s.equals("*")) {
            return new CodeBase(Kind.OTHER, "codeBase is a bare wildcard, which names no location");
        }
        if (lower.indexOf(':') < 0 || lower.matches("^[a-z]:[\\\\/].*")) {
            return file(s, writableProperty, false); // no scheme: a path
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

    private static CodeBase share(String p, int slashes, String how) {
        int i = p.indexOf('/', slashes);
        String host = i < 0 ? p.substring(slashes) : p.substring(slashes, i);
        if (host.length() == 0) host = "(empty)";
        return new CodeBase(Kind.REMOTE_WILD, "file: codeBase on the host " + host + " (" + slashes
                + " leading slashes" + how + "), a network share another party can serve code from");
    }

    /** Percent-decodes as {@code URL} and {@code File(URI)} do (UTF-8); null if an escape is malformed. */
    static String percentDecode(String s) {
        if (s.indexOf('%') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) != '%') {
                out.append(s.charAt(i++));
                continue;
            }
            bytes.reset();
            while (i < s.length() && s.charAt(i) == '%') {
                if (i + 2 >= s.length()) return null;
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) return null;
                bytes.write((hi << 4) | lo);
                i += 3;
            }
            out.append(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private static CodeBase jrt(String path) {
        String p = path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        if (p.length() == 0 || p.equals("-") || p.equals("*")) {
            return new CodeBase(Kind.JRT_BROAD, "jrt: codeBase matches every platform module");
        }
        return new CodeBase(Kind.JRT_PINNED, "jrt: codeBase names one module");
    }

    /**
     * The text after {@code file:} (or a scheme-less path) is read as Windows reads it: a backslash
     * is a slash, and the path part (never the host) of a {@code file:} URL is percent-decoded as
     * {@code URL} and {@code File(URI)} do before a {@code File} is made, so {@code %2F} and
     * {@code %5C} are separators.  Two or more slashes before the rest of the decoded path are a
     * UNC share {@code \\host\share}.
     *
     * <pre>
     * raw leading slashes  authority       path judged                      result
     * 0 or 1               none            the text, decoded                local, unless it then starts with 2+ slashes
     * 2                    first segment   after the host, decoded          localhost: as the path rule; any other host: share
     * 3                    empty           from the third slash, decoded    as the path rule
     * 4 or more            a share         -                                share
     * slashes only         -               the root                         local
     * decoded path starts with 2+ slashes (even only slashes)                share
     * malformed escape ({@code %zz}, a lone {@code %}) in the path           share (fail closed)
     * </pre>
     * A host written percent-encoded ({@code %6Cocalhost}) is not decoded and stays a host.
     */
    private static CodeBase file(String path, boolean writableProperty, boolean url) {
        String p = path.replace('\\', '/');
        int n = 0;
        while (n < p.length() && p.charAt(n) == '/') n++;
        String q; // the path component: what File is made from
        if (n >= 2 && n == p.length()) { // slashes only: names nothing but the root
            q = "/";
        } else if (n >= 4) {
            return share(p, n, "");
        } else if (n == 2) {
            int i = p.indexOf('/', n);
            String host = i < 0 ? p.substring(n) : p.substring(n, i);
            if (!host.equalsIgnoreCase("localhost")) return share(p, n, "");
            q = i < 0 ? "/" : p.substring(i);
        } else if (n == 3) { // empty authority
            q = p.substring(2);
        } else {
            q = p;
        }
        if (url) {
            String d = percentDecode(q);
            if (d == null) {
                return new CodeBase(Kind.REMOTE_WILD, "file: codeBase with a malformed percent escape:"
                        + " it cannot be shown to name a local file, treated as a network share");
            }
            q = d.replace('\\', '/'); // a decoded %5C is a separator too
        }
        int m = 0;
        while (m < q.length() && q.charAt(m) == '/') m++;
        if (m >= 2) return share(q, m, url ? ", after decoding" : "");
        p = q;
        if (p.matches("^/[A-Za-z]:([\\\\/].*)?$")) p = p.substring(1);
        // operator ruling 2026-11-07: the same alias reading as the permission ratings, so Documents and Settings is
        // the Users tree here too
        PermissionSets.FilePath f = PermissionSets.aliased(p);
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
        // the shape is tested before the alias fold, which would turn DOCUME~1 into Users and hide it from this rule
        if (PermissionSets.mayBeShortName(PermissionSets.filePath(p))) {
            return new CodeBase(Kind.FILE_WRITABLE, "file: codeBase over a tree named by an 8.3 short name,"
                    + " which may stand for a user home");
        }
        if ("-".equals(f.wildcard)) {
            for (String prop : new String[]{"user.home", "java.io.tmpdir", "user.dir"}) {
                String d = System.getProperty(prop);
                if (d == null) continue;
                PermissionSets.FilePath dir = PermissionSets.aliased(d);
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
