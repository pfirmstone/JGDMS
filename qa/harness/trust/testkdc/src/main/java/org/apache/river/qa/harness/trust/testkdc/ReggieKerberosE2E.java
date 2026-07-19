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
package org.apache.river.qa.harness.trust.testkdc;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.core.discovery.LookupLocator;
import net.jini.export.Exporter;

import org.apache.river.reggie.proxy.Registrar;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;

/**
 * Direct, wire-level validation of the reggie kerberos JERI transport
 * described by qa/harness/configs/kerberos/reggie/reggie.config: loads that
 * *exact*, unmodified config file, retrieves its real "exporter" component
 * (KerberosServerEndpoint.getInstance(0) + AtomicILFactory, exactly as
 * configured), exports a Registrar-shaped remote object under the "reggie"
 * server Subject (JAAS Krb5LoginModule, keytab-based, same
 * qa/harness/trust/kerberos.login used by the real QA harness), then makes
 * an authenticated remote call as the "test" tester Subject with
 * ClientAuthentication.YES + ServerAuthentication.YES +
 * ServerMinPrincipal(reggie) -- the exact constraint shape
 * test.config's reggiePreparer applies to real reggie proxies.
 *
 * This is a standalone driver, not the legacy ant/QARunner harness: see
 * ../run-reggie-kerberos-e2e.sh and the task's final report for why (no
 * root/no container runtime in this sandbox to stand up the full harness's
 * ClassServer/activation/multicast-discovery machinery; this driver instead
 * exercises the actual production classes -- KerberosServerEndpoint,
 * KerberosEndpoint, AtomicILFactory, BasicJeriExporter, the real
 * org.apache.river.reggie.proxy.Registrar interface, and reggie.config
 * itself -- against a real KRB5 KDC).
 *
 * Run with -Dsun.security.krb5.debug=true -Dsun.security.jgss.debug=true to
 * observe the real AS-REQ/AS-REP, TGS-REQ/TGS-REP and AP-REQ/AP-REP wire
 * exchanges against the Apache Kerby test KDC (see TestKdc).
 */
public final class ReggieKerberosE2E {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println(
                "usage: ReggieKerberosE2E <reggie.config path> <kerberos.login path> <reggiePrincipal>");
            System.exit(2);
        }
        String reggieConfigPath = args[0];
        String loginConfigPath = args[1];
        String reggiePrincipalName = args[2];

        // javax.security.auth.login.Configuration is resolved lazily on
        // first use; setting this here (before any LoginContext is
        // constructed) is sufficient and keeps the run script simple.
        System.setProperty("java.security.auth.login.config", "file:" + loginConfigPath);

        System.out.println("[driver] logging in server Subject (org.apache.river.Reggie / keytab) ...");
        LoginContext serverLc = new LoginContext("org.apache.river.Reggie");
        serverLc.login();
        Subject serverSubject = serverLc.getSubject();
        System.out.println("[driver] server Subject principals: " + serverSubject.getPrincipals());
        for (Object cred : serverSubject.getPrivateCredentials()) {
            System.out.println("[driver] server Subject private credential: " + cred.getClass()
                + " -> " + cred);
        }

        // --- KNOWN GAP WORKAROUND (see final report) -----------------------
        // Modern JDK (since JDK-6894072, i.e. every JDK 8u40+/9+ release)
        // Krb5LoginModule, when configured exactly as
        // qa/harness/trust/kerberos.login does (useKeyTab=true, storeKey=true,
        // a *bound* principal -- not "*"), stores a lazy javax.security.auth
        // .kerberos.KeyTab private credential instead of eagerly materialized
        // KerberosKey objects. net.jini.jeri.kerberos.KerberosServerEndpoint
        // .getKey() (JGDMS/jgdms-jeri .../KerberosServerEndpoint.java) only
        // ever checks "cred instanceof KerberosKey" -- it never recognizes a
        // KeyTab credential -- so KerberosServerEndpoint.getInstance(...) is
        // *unable* to find a server key via this login pattern on any modern
        // JDK, and throws UnsupportedConstraintException("Cannot find any
        // Kerberos key in the serverSubject..."). This reproduces
        // unconditionally, is not specific to this driver or to Apache Kerby,
        // and blocks the reggie kerberos config from ever starting a listen
        // endpoint via qa/harness/trust/kerberos.login as written.
        //
        // Workaround (driver-side only; does not touch JGDMS production
        // code): materialize the KerberosKey(s) ourselves from the KeyTab
        // credential Krb5LoginModule already deposited, and add them to the
        // Subject, exactly what a fixed KerberosServerEndpoint.getKey() would
        // need to do internally.
        for (Object cred : new java.util.HashSet<>(serverSubject.getPrivateCredentials())) {
            if (cred instanceof javax.security.auth.kerberos.KeyTab) {
                javax.security.auth.kerberos.KeyTab kt = (javax.security.auth.kerberos.KeyTab) cred;
                KerberosPrincipal forPrincipal = new KerberosPrincipal(reggiePrincipalName);
                javax.security.auth.kerberos.KerberosKey[] keys = kt.getKeys(forPrincipal);
                System.out.println("[driver] KNOWN-GAP WORKAROUND: materializing "
                    + keys.length + " KerberosKey(s) from KeyTab credential for "
                    + forPrincipal + " (KerberosServerEndpoint.getKey() cannot see"
                    + " KeyTab credentials -- see final report)");
                for (javax.security.auth.kerberos.KerberosKey k : keys) {
                    serverSubject.getPrivateCredentials().add(k);
                }
            }
        }

        System.out.println("[driver] logging in tester Subject (org.apache.river.Test / keytab) ...");
        LoginContext testerLc = new LoginContext("org.apache.river.Test");
        testerLc.login();
        Subject testerSubject = testerLc.getSubject();
        System.out.println("[driver] tester Subject principals: " + testerSubject.getPrincipals());

        System.out.println("[driver] loading reggie.config (unmodified): " + reggieConfigPath);
        Configuration config = ConfigurationProvider.getInstance(new String[] { reggieConfigPath });

        // The Registrar test double: a JDK dynamic proxy implementing the
        // *real* org.apache.river.reggie.proxy.Registrar interface (the
        // private wire protocol between reggie's client-side proxies and
        // the registrar server). Standing up a full ServerImpl is out of
        // scope here -- what's under test is the Kerberos JERI transport
        // (task #26's ProxyTrustILFactory->AtomicILFactory migration),
        // not reggie's lookup-matching business logic.
        Registrar registrarImpl = (Registrar) Proxy.newProxyInstance(
            ReggieKerberosE2E.class.getClassLoader(),
            new Class<?>[] { Registrar.class },
            new DefaultAnswerHandler());

        System.out.println("[driver] retrieving exporter.transientExporter from reggie.config"
            + " and exporting the Registrar test double, as the server Subject ...");
        final Remote[] stubHolder = new Remote[1];
        Subject.doAs(serverSubject, (PrivilegedExceptionAction<Void>) () -> {
            Exporter exporter = (Exporter) config.getEntry(
                "exporter", "transientExporter", Exporter.class);
            stubHolder[0] = exporter.export(registrarImpl);
            return null;
        });
        Registrar registrarStub = (Registrar) stubHolder[0];
        System.out.println("[driver] exported. stub class=" + registrarStub.getClass()
            + " endpoint=" + registrarStub);

        KerberosPrincipal reggiePrincipal = new KerberosPrincipal(reggiePrincipalName);
        InvocationConstraints authConstraints = new InvocationConstraints(
            new InvocationConstraint[] {
                ClientAuthentication.YES,
                ServerAuthentication.YES,
                Integrity.YES,
                new ServerMinPrincipal(reggiePrincipal)
            },
            null);

        System.out.println("[driver] === POSITIVE CASE: authenticated call as tester Subject ===");
        boolean positiveOk = Subject.doAs(testerSubject, (PrivilegedExceptionAction<Boolean>) () -> {
            RemoteMethodControl controlled = (RemoteMethodControl) registrarStub;
            Registrar prepared = (Registrar) controlled.setConstraints(
                new BasicMethodConstraints(authConstraints));
            LookupLocator loc = prepared.getLocator();
            System.out.println("[driver] remote call getLocator() succeeded, returned: " + loc);
            return true;
        });

        System.out.println("[driver] === NEGATIVE CASE: ServerMinPrincipal set to a principal"
            + " the KDC never issued the server a key for -> must fail ===");
        boolean negativeRejected;
        try {
            KerberosPrincipal bogus = new KerberosPrincipal("not-reggie@" + reggiePrincipal.getRealm());
            InvocationConstraints wrongConstraints = new InvocationConstraints(
                new InvocationConstraint[] {
                    ClientAuthentication.YES,
                    ServerAuthentication.YES,
                    Integrity.YES,
                    new ServerMinPrincipal(bogus)
                },
                null);
            Subject.doAs(testerSubject, (PrivilegedExceptionAction<Void>) () -> {
                RemoteMethodControl controlled = (RemoteMethodControl) registrarStub;
                Registrar prepared = (Registrar) controlled.setConstraints(
                    new BasicMethodConstraints(wrongConstraints));
                prepared.getLocator(); // expected to throw
                return null;
            });
            negativeRejected = false;
            System.out.println("[driver] UNEXPECTED: call with bogus ServerMinPrincipal succeeded");
        } catch (Exception e) {
            negativeRejected = true;
            System.out.println("[driver] expected rejection: " + e);
        }

        System.out.println();
        System.out.println("RESULT positiveCase=" + (positiveOk ? "PASS" : "FAIL")
            + " negativeCase=" + (negativeRejected ? "PASS" : "FAIL"));
        if (positiveOk && negativeRejected) {
            System.out.println("REGGIE-KERBEROS-E2E: PASS");
            System.exit(0); // BasicJeriExporter's listen thread is non-daemon; exit explicitly.
        } else {
            System.out.println("REGGIE-KERBEROS-E2E: FAIL");
            System.exit(1);
        }
    }

    /** Returns a harmless default value for any Registrar method invoked on the test double. */
    private static final class DefaultAnswerHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] methodArgs) throws Throwable {
            Class<?> rt = method.getReturnType();
            if ("getLocator".equals(method.getName())) {
                return null; // valid: ServiceRegistrar#getLocator permits null in this test double
            }
            if (rt == void.class) {
                return null;
            }
            if (rt == boolean.class) {
                return Boolean.FALSE;
            }
            if (rt.isPrimitive()) {
                return 0;
            }
            if (rt.isArray()) {
                return java.lang.reflect.Array.newInstance(rt.getComponentType(), 0);
            }
            if (rt == java.util.Map.class) {
                return Collections.emptyMap();
            }
            return null;
        }
    }
}
