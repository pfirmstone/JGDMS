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
package org.apache.river.test.impl.end2end.e2etest;

import java.security.AccessController;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.ServerMinPrincipal;
import au.net.zeus.jgdms.spiffe.SpiffePrincipal;

/**
 * Subject provider for the SPIFFE QA configuration set.
 *
 * <p>The login contexts are supplied by {@code qa/harness/trust/spiffelogins}
 * and load X.509-SVID credentials through {@code SpiffeLoginModule}.  The
 * end-to-end test uses the SPIFFE URI principals for client/server principal
 * constraint matching, while TLS authentication continues to use the same
 * Subject's X.509 credentials.
 */
public class SpiffeSubjectProvider implements SubjectProvider {

    private static Subject clientSubject = new Subject();
    private static Subject serverSubject = new Subject();

    public static void initialize() throws LoginException {
        LoginContext lc = new LoginContext("org.apache.river.Test");
        lc.login();
        synchronized (clientSubject) {
            clientSubject = lc.getSubject();
            clientSubject.setReadOnly();
        }

        lc = new LoginContext("org.apache.river.Reggie");
        lc.login();
        synchronized (serverSubject) {
            serverSubject = lc.getSubject();
            serverSubject.setReadOnly();
        }
    }

    public Subject getClientSubject() {
        synchronized (clientSubject) {
            return clientSubject;
        }
    }

    public Subject getServerSubject() {
        synchronized (serverSubject) {
            return serverSubject;
        }
    }

    public Subject getSubject() {
        return Subject.getSubject(AccessController.getContext());
    }

    public ClientMinPrincipal getClientMinPrincipal() {
        return new ClientMinPrincipal(getClientSubject().getPrincipals(SpiffePrincipal.class));
    }

    public ClientMinPrincipalType getClientMinPrincipalType() {
        return new ClientMinPrincipalType(SpiffePrincipal.class);
    }

    public ClientMaxPrincipal getClientMaxPrincipal() {
        return new ClientMaxPrincipal(getClientSubject().getPrincipals(SpiffePrincipal.class));
    }

    public ConstraintAlternatives getConstraintAlternatives1() {
        return new ConstraintAlternatives(new InvocationConstraint[] {
            new ClientMinPrincipal(getClientSubject().getPrincipals(SpiffePrincipal.class)),
            new ClientMinPrincipal(Set.of(new SpiffePrincipal("spiffe://test.jgdms.local/client/dummy")))
        });
    }

    public ConstraintAlternatives getConstraintAlternatives2() {
        return new ConstraintAlternatives(new InvocationConstraint[] {
            new ClientMinPrincipal(getClientSubject().getPrincipals(SpiffePrincipal.class)),
            new ClientMinPrincipal(Set.of(new SpiffePrincipal("spiffe://test.jgdms.local/client/other")))
        });
    }

    public ConstraintAlternatives getServerMinPrincipal() {
        return new ConstraintAlternatives(new InvocationConstraint[] {
            new ServerMinPrincipal(getServerSubject().getPrincipals(SpiffePrincipal.class)),
            new ServerMinPrincipal(Set.of(new SpiffePrincipal("spiffe://test.jgdms.local/svc/dummy")))
        });
    }

    public ServerMinPrincipal getServerMainPrincipal() {
        return new ServerMinPrincipal(getServerSubject().getPrincipals(SpiffePrincipal.class));
    }
}
