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
package net.jini.jeri;

import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

/**
 * Installs a policy under which the authenticated peer {@link #PEER} holds
 * exactly the permissions given to the constructor, any other principal holds
 * nothing, and code with no principal (the test infrastructure itself) holds
 * everything.  {@link #close()} restores the previous policy.
 *
 * <p>The dispatcher consults the policy only through the SecurityManager, so
 * the normal constructor installs a plain {@link SecurityManager} if none is
 * installed (DirtyChai cannot uninstall one; once this fixture has installed
 * it, {@link #close()} restores a permissive policy instead of the previous one,
 * so other test classes of the same JVM are not restricted).  The
 * {@link #withoutSecurityManager} variant installs only the policy, for tests
 * that run in a child JVM and must see no SecurityManager.
 *
 * <p>It counts the calls to {@code Policy.implies} for a domain that has
 * principals, and how many of them came straight from the dispatcher.
 */
final class PeerPolicyFixture implements AutoCloseable {

    /** The authenticated worker, e.g. the proxy process. */
    static final X500Principal PEER = new X500Principal("CN=gateway,O=Example");

    private static volatile boolean installedSecurityManager;

    private static final Policy PERMISSIVE = new Policy() {
        @Override
        public boolean implies(ProtectionDomain pd, Permission p) {
            return true;
        }

        @Override
        public PermissionCollection getPermissions(ProtectionDomain pd) {
            return all();
        }

        @Override
        public PermissionCollection getPermissions(java.security.CodeSource cs) {
            return all();
        }

        private PermissionCollection all() {
            PermissionCollection c = new Permissions();
            c.add(new java.security.AllPermission());
            return c;
        }
    };

    private final Policy previousPolicy;
    private final AtomicInteger principalCalls = new AtomicInteger();
    private final AtomicInteger dispatcherCalls = new AtomicInteger();

    PeerPolicyFixture(Permission... grantedToPeer) {
        this(true, grantedToPeer);
    }

    /** The policy only: no SecurityManager is installed. */
    static PeerPolicyFixture withoutSecurityManager(Permission... grantedToPeer) {
        return new PeerPolicyFixture(false, grantedToPeer);
    }

    private PeerPolicyFixture(boolean securityManager, Permission... grantedToPeer) {
        final List<Permission> grants = new ArrayList<>();
        Collections.addAll(grants, grantedToPeer);
        previousPolicy = Policy.getPolicy();
        Policy.setPolicy(new Policy() {
            @Override
            public boolean implies(ProtectionDomain pd, Permission p) {
                Principal[] ps = pd.getPrincipals();
                if (ps == null || ps.length == 0) return true;
                principalCalls.incrementAndGet();
                StackTraceElement[] st = new Throwable().getStackTrace();
                if (st.length > 1 && st[1].getClassName()
                        .startsWith("net.jini.jeri.BasicInvocationDispatcher")) {
                    dispatcherCalls.incrementAndGet();
                }
                boolean peer = false;
                for (Principal x : ps) peer |= PEER.equals(x);
                if (!peer) return false;
                for (Permission g : grants) if (g.implies(p)) return true;
                return false;
            }

            @Override
            public PermissionCollection getPermissions(ProtectionDomain pd) {
                // Not consulted for the peer: implies() above is authoritative.
                return new Permissions();
            }
        });
        if (securityManager && System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
            installedSecurityManager = true;
        }
    }

    /** Calls to {@code Policy.implies} for a domain with principals, by anyone. */
    int principalCalls() {
        return principalCalls.get();
    }

    /** Those of them whose immediate caller is {@code BasicInvocationDispatcher}. */
    int dispatcherCalls() {
        return dispatcherCalls.get();
    }

    /** The authenticated peer as the dispatcher sees it. */
    static Subject peer() {
        return new Subject(true, Collections.<Principal>singleton(PEER),
                Collections.emptySet(), Collections.emptySet());
    }

    @Override
    public void close() {
        Policy.setPolicy(installedSecurityManager ? PERMISSIVE : previousPolicy);
    }
}
