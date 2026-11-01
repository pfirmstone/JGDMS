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

package net.jini.security;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Permission for an authenticated peer to assert, on the wire, a user
 * principal that the server cannot verify itself.
 *
 * <p>A JERI request may carry user principals next to the authenticated
 * connection's own identity (the worker, e.g. a proxy process).  Principals
 * that come from a verified JWT are derived from the token and need no
 * permission.  Every other principal (an {@code X500Principal}, a
 * {@code KerberosPrincipal}, ...) is only the sender's claim, and
 * {@code BasicInvocationDispatcher} accepts it only if the policy grants this
 * permission to the <em>authenticated peer</em> (the principals of the
 * connection's {@code ClientSubject}) for that exact principal.  With no grant
 * the call is refused.
 *
 * <h2>Names</h2>
 * The <em>target name</em> is the fully qualified class name of the principal,
 * or {@code *} for any class.  The <em>actions</em> string is the principal
 * name pattern.  Exactly three forms are supported:
 * <ul>
 *   <li>{@code *} &mdash; any name;</li>
 *   <li>an exact name, with no {@code *} in it;</li>
 *   <li>a prefix pattern: a name followed by one trailing {@code *}
 *       ({@code CN=staff-*}).</li>
 * </ul>
 * A pattern that has a {@code *} anywhere else (in the middle or at the
 * start, such as {@code CN=*,O=Example} or {@code *,O=Example}) is not
 * supported and the constructor throws {@code IllegalArgumentException}; it
 * is never compared literally.  The name tested is
 * {@code Principal.getName()}; for an {@code X500Principal} that is the
 * RFC 2253 form, which a policy author must use.
 *
 * <p><b>Prefix patterns on distinguished names are weak.</b>  The prefix is a
 * plain string prefix, so {@code "CN=alice*"} also matches
 * {@code "CN=alice,O=Attacker"} and {@code "CN=alice2"}.  For X500 and
 * Kerberos principals use exact names; use a prefix pattern only for a name
 * space whose names you control end to end.
 *
 * <pre>
 * grant principal javax.security.auth.x500.X500Principal "CN=gateway,O=Example" {
 *     permission net.jini.security.AssertPrincipalPermission
 *         "javax.security.auth.x500.X500Principal", "CN=alice,OU=staff,O=Example";
 * };
 * </pre>
 *
 * <h2>Security impact</h2>
 * Granting this permission lets the named peer make the server treat its
 * callers as any matching user, so every principal-scoped grant for those
 * users becomes available to that peer.  Grant it only to a gateway the
 * deployer trusts to have authenticated the users, and keep both fields as
 * narrow as possible.
 *
 * <p>This permission does not distinguish a user identity from a workload
 * identity of the same class.  A grant of {@code "*", "*"} or of
 * {@code "javax.security.auth.x500.X500Principal", "*"} lets that peer assert
 * <em>any</em> X500 identity, including the X500 identities of other
 * workloads, so every {@code principal X500Principal "..."} workload grant
 * becomes reachable from a user Subject.  Name the exact user identities.
 *
 * <p>The permission is only as narrow as the policy that grants it.  An
 * unscoped {@code AllPermission} grant, or a policy entry written
 * {@code principal * "..."} (a wildcard principal class matches every
 * peer in some policy implementations), hands this permission to every
 * authenticated peer.
 *
 * <p>No grant of this permission, however wide, lets a peer assert a
 * {@code SpiffePrincipal} (a workload identity) or a {@code JwtPrincipal}
 * (derived from a verified token only) in a user Subject: the dispatcher
 * refuses those without consulting the policy.
 *
 * @since 3.1.1
 * @see net.jini.jeri.BasicInvocationDispatcher
 */
public final class AssertPrincipalPermission extends Permission {

    private static final long serialVersionUID = 6417429086528132187L;

    private final String principalClass; // null if wildcarded
    private final String namePattern;

    /**
     * Creates an instance.
     *
     * @param principalClass the principal class name, or {@code *}
     * @param namePattern    {@code *}, an exact name, or a name followed by
     *                       one trailing {@code *}
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if an argument is empty, or the
     *         pattern has a {@code *} anywhere but as its last character
     */
    public AssertPrincipalPermission(String principalClass, String namePattern) {
        super(check(principalClass, "principalClass"));
        this.namePattern = checkPattern(namePattern);
        this.principalClass = "*".equals(principalClass) ? null : principalClass;
    }

    private static String checkPattern(String namePattern) {
        if (namePattern == null) throw new NullPointerException("namePattern");
        if (namePattern.isEmpty())
            throw new IllegalArgumentException("namePattern cannot be empty");
        int star = namePattern.indexOf('*');
        if (star >= 0 && star != namePattern.length() - 1)
            throw new IllegalArgumentException(
                "namePattern may contain '*' only as its last character: " + namePattern);
        return namePattern;
    }


    private static String check(String s, String what) {
        if (s == null) throw new NullPointerException(what);
        if (s.isEmpty()) throw new IllegalArgumentException(what + " cannot be empty");
        return s;
    }

    /**
     * Returns {@code true} if {@code p} is an {@code AssertPrincipalPermission}
     * whose class is matched by this one's class and whose name pattern is
     * matched by this one's pattern.
     */
    @Override
    public boolean implies(Permission p) {
        if (!(p instanceof AssertPrincipalPermission)) return false;
        AssertPrincipalPermission o = (AssertPrincipalPermission) p;
        if (principalClass != null && !principalClass.equals(o.principalClass)) return false;
        return patternImplies(namePattern, o.namePattern);
    }

    private static boolean patternImplies(String mine, String other) {
        if ("*".equals(mine)) return true;
        if (mine.endsWith("*")) {
            return other.startsWith(mine.substring(0, mine.length() - 1));
        }
        return mine.equals(other);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof AssertPrincipalPermission)) return false;
        AssertPrincipalPermission o = (AssertPrincipalPermission) obj;
        return getName().equals(o.getName()) && namePattern.equals(o.namePattern);
    }

    @Override
    public int hashCode() {
        return getName().hashCode() * 31 + namePattern.hashCode();
    }

    /** Returns the principal name pattern. */
    @Override
    public String getActions() {
        return namePattern;
    }

    @Override
    public PermissionCollection newPermissionCollection() {
        return new Collection();
    }

    private static final class Collection extends PermissionCollection {
        private static final long serialVersionUID = 1L;
        private final List<AssertPrincipalPermission> perms = new ArrayList<>();

        @Override
        public synchronized void add(Permission permission) {
            if (!(permission instanceof AssertPrincipalPermission))
                throw new IllegalArgumentException("wrong permission type: " + permission);
            if (isReadOnly())
                throw new SecurityException("attempt to add a Permission to a readonly PermissionCollection");
            perms.add((AssertPrincipalPermission) permission);
        }

        @Override
        public synchronized boolean implies(Permission permission) {
            if (!(permission instanceof AssertPrincipalPermission)) return false;
            for (AssertPrincipalPermission p : perms) {
                if (p.implies(permission)) return true;
            }
            return false;
        }

        @Override
        public synchronized Enumeration<Permission> elements() {
            return Collections.<Permission>enumeration(new ArrayList<Permission>(perms));
        }
    }
}
