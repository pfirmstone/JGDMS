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
import java.security.Principal;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;

public class KerberosSubjectProvider implements SubjectProvider {

    private static Subject clientSubject = new Subject();
    private static Subject serverSubject = new Subject();
    private static Logger log = Logger.getLogger(
        "e2eTest.KerberosSubjectProvider");

    public static void initialize() throws LoginException {
        LoginContext lc = new LoginContext("e2etest.KerberosClient");
        lc.login();
        synchronized(clientSubject){
            clientSubject = lc.getSubject();
            clientSubject.setReadOnly();
        }
        lc = new LoginContext("e2etest.KerberosServer");
        lc.login();
        synchronized(serverSubject) {
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
        synchronized(serverSubject) {
            return serverSubject;
        }
    }

    public Subject getSubject() {
        return Subject.getSubject(AccessController.getContext());
    }

    public ClientMinPrincipal getClientMinPrincipal() {
        return new ClientMinPrincipal(clientSubject.getPrincipals());
    }

    public ClientMinPrincipalType getClientMinPrincipalType() {
        return new ClientMinPrincipalType(KerberosPrincipal.class);
    }

    public ClientMaxPrincipal getClientMaxPrincipal() {
        Set principals = clientSubject.getPrincipals(KerberosPrincipal.class);
        return new ClientMaxPrincipal(principals);
    }

    public ConstraintAlternatives getConstraintAlternatives1() {
        return new ConstraintAlternatives(new InvocationConstraint[] {
            new ClientMinPrincipal(clientSubject.getPrincipals()),
            new ClientMinPrincipal(new KerberosPrincipal("dummy"))
        });
    }

    public ConstraintAlternatives getConstraintAlternatives2() {
        return new ConstraintAlternatives(new InvocationConstraint[] {
            new ClientMinPrincipal(clientSubject.getPrincipals()),
            new ClientMinPrincipal(new KerberosPrincipal("dummy"))
        });
    }

    public ConstraintAlternatives getServerMinPrincipal() {
        return new ConstraintAlternatives(new InvocationConstraint[] {
            new ServerMinPrincipal(serverSubject.getPrincipals()),
            new ServerMinPrincipal(new KerberosPrincipal("dummy"))
        });
    }

    public ServerMinPrincipal getServerMainPrincipal() {
        return new ServerMinPrincipal(serverSubject.getPrincipals());
    }


}
