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
 * name pattern: {@code *} (any name), an exact name, {@code prefix*} or
 * {@code *suffix}.  The name tested is {@code Principal.getName()}; for an
 * {@code X500Principal} that is the RFC 2253 form, which a policy author must
 * use.
 *
 * <pre>
 * grant principal javax.security.auth.x500.X500Principal "CN=gateway,O=Example" {
 *     permission net.jini.security.AssertPrincipalPermission
 *         "javax.security.auth.x500.X500Principal", "CN=*,OU=staff,O=Example";
 * };
 * </pre>
 *
 * <h2>Security impact</h2>
 * Granting this permission lets the named peer make the server treat its
 * callers as any matching user, so every principal-scoped grant for those
 * users becomes available to that peer.  Grant it only to a gateway the
 * deployer trusts to have authenticated the users, and keep both fields as
 * narrow as possible; {@code "*", "*"} gives the peer every identity except
 * the two that can never be asserted this way (see below).
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
     * @param namePattern    {@code *}, an exact name, {@code prefix*} or
     *                       {@code *suffix}
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if an argument is empty
     */
    public AssertPrincipalPermission(String principalClass, String namePattern) {
        super(check(principalClass, "principalClass"));
        if (namePattern == null) throw new NullPointerException("namePattern");
        if (namePattern.isEmpty())
            throw new IllegalArgumentException("namePattern cannot be empty");
        this.principalClass = "*".equals(principalClass) ? null : principalClass;
        this.namePattern = namePattern;
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
            String prefix = mine.substring(0, mine.length() - 1);
            return !other.startsWith("*") && other.startsWith(prefix);
        }
        if (mine.startsWith("*")) {
            String suffix = mine.substring(1);
            return !other.endsWith("*") && other.endsWith(suffix);
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
