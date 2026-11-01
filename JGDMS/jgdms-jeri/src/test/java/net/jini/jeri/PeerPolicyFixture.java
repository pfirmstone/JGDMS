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
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

/**
 * Installs a policy under which the authenticated peer {@link #PEER} holds
 * exactly the permissions given to the constructor, any other principal holds
 * nothing, and code with no principal (the test infrastructure itself) holds
 * everything.  {@link #close()} restores the previous policy.
 *
 * <p>It does not install a SecurityManager (DirtyChai cannot uninstall one).
 * With none installed the dispatcher asks the policy directly; when the tests
 * are run with a SecurityManager (the {@code dirtychai} profile) it asks the
 * SecurityManager, which consults this same policy.
 */
final class PeerPolicyFixture implements AutoCloseable {

    /** The authenticated worker, e.g. the proxy process. */
    static final X500Principal PEER = new X500Principal("CN=gateway,O=Example");

    private final Policy previousPolicy;

    PeerPolicyFixture(Permission... grantedToPeer) {
        final List<Permission> grants = new ArrayList<>();
        Collections.addAll(grants, grantedToPeer);
        previousPolicy = Policy.getPolicy();
        Policy.setPolicy(new Policy() {
            @Override
            public boolean implies(ProtectionDomain pd, Permission p) {
                Principal[] ps = pd.getPrincipals();
                if (ps == null || ps.length == 0) return true;
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
    }

    /** The authenticated peer as the dispatcher sees it. */
    static Subject peer() {
        return new Subject(true, Collections.<Principal>singleton(PEER),
                Collections.emptySet(), Collections.emptySet());
    }

    @Override
    public void close() {
        Policy.setPolicy(previousPolicy);
    }
}
