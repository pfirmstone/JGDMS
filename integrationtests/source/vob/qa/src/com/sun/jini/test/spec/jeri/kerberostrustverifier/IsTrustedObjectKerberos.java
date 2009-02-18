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
/**
 * Purpose: The purpose of this test is to verify the functionality
 * of KerberosTrustVerifier.
 *
 * Use Case: Verifying trust on KerberosEndpoint and KerberosPrincipal
 * instances.
 *
 * Test Design:
 * 1. Obtain an instance of KerberosTrustVerifier.
 * 2. Verify that instances of KerberosEndpoint with no socket factory are
 *    trusted.
 * 3. Verify that instances of KerberosEndpoint with trusted socket factories
 *    are trusted.
 * 4. Verify that instances of KerberosEndpoint with untrusted socket
 *    factories are not trusted.
 * 5. Verify that instances of other constraints are not trusted.
 * 6. Verify that instances of KerberosPrincipal are trusted.
 * 7. Verify that instances of other principals are not trusted.
 * 8. Verify that Remote and Security exceptions from the TrustVerifier.Context
 *    are propagated.
 * 9. Verify that NullPointerException is thrown if either argument of
 *     isTrustedObject is null.
 *
 * Additional Utility classes
 * 1.Test TrustVerifier.Context implementation
 * 2.Implementations of trusted and untrusted socket factories
 *
 */
package com.sun.jini.test.spec.jeri.kerberostrustverifier;

import java.util.logging.Level;

//utility classes
import com.sun.jini.test.spec.jeri.kerberostrustverifier
    .util.AbstractTrustVerifierTestKerberos;
import com.sun.jini.test.spec.jeri.ssltrustverifier.util.TestTrustVerifierCtxSSL;
import com.sun.jini.test.spec.jeri.ssltrustverifier.util.TestSocketFactory;

//harness imports
import com.sun.jini.qa.harness.TestException;

//jeri imports
import net.jini.jeri.kerberos.KerberosEndpoint;
import net.jini.jeri.kerberos.KerberosTrustVerifier;

//java.net
import java.net.InetAddress;

//java.rmi
import java.rmi.RemoteException;

//javax.security
import javax.security.auth.x500.X500Principal;
import javax.security.auth.kerberos.KerberosPrincipal;

public class IsTrustedObjectKerberos extends AbstractTrustVerifierTestKerberos{

    //inherit javadoc
    public void run() throws Exception {
        //Obtain an instance of KerberosTrustVerifier
        KerberosTrustVerifier verifier = new KerberosTrustVerifier();
        //Verify that instances of KerberosEndpoint with no socket
        //factory are trusted.
        int port = Integer.parseInt(getStringValue("listenPort"));
        KerberosEndpoint endpoint = KerberosEndpoint.getInstance(
            InetAddress.getLocalHost().getHostAddress(), port,
            new KerberosPrincipal("test@test"));
        TestTrustVerifierCtxSSL ctx = new TestTrustVerifierCtxSSL();
        if (!verifier.isTrustedObject(endpoint,ctx)){
            throw new TestException("KerberosEndpoint instance"
                + " with no socket factory is considered untrusted");
        }
        //Verify that instances of KerberosEndpoint
        //with trusted socket factories are trusted.
        endpoint = KerberosEndpoint.getInstance(
            InetAddress.getLocalHost().getHostAddress(), port,
            new KerberosPrincipal("test@test"),
            new TestSocketFactory(true));
        if (!verifier.isTrustedObject(endpoint,ctx)){
            throw new TestException("KerberosEndpoint instance"
                + " with trusted factory is considered untrusted");
        }
        //Verify that instances of KerberosEndpoint with
        //untrusted socket factories are not trusted.
        endpoint = KerberosEndpoint.getInstance(
            InetAddress.getLocalHost().getHostAddress(), port,
            new KerberosPrincipal("test@test"),
            new TestSocketFactory(false));
        if (verifier.isTrustedObject(endpoint,ctx)){
            throw new TestException("KerberosEndpoint instance"
                + " with untrusted factory is considered trusted");
        }
        //Verify that instances of KerberosPrincipal are trusted.
        KerberosPrincipal kPrincipal = new KerberosPrincipal("bogus@bogus");
        if (!verifier.isTrustedObject(kPrincipal,ctx)){
            throw new TestException(
            "KerberosPrincipal is not considered trusted");
        }
        //Verify that other principals are not trusted
        X500Principal x5Principal = new X500Principal("CN=\"bogus\"");
        if (verifier.isTrustedObject(x5Principal,ctx)){
            throw new TestException("X500Principal is considered trusted");
        }
        //Verify that Remote and Security exceptions from the
        //TrustVerifier.Context are propagated.
        boolean exceptionThrown = false;
        /*ctx = new TestTrustVerifierCtxSSL(new RemoteException());
        try {
            verifier.isTrustedObject(endpoint,ctx);
        } catch (RemoteException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("RemoteException in the"
                + " context was not propagated");
        }*/
        exceptionThrown = false;
        ctx = new TestTrustVerifierCtxSSL(new SecurityException());
        try {
            verifier.isTrustedObject(endpoint,ctx);
        } catch (SecurityException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("SecurityException in the"
                + " context was not propagated");
        }
        //Verify that NullPointerException is thrown if either argument of
        //isTrustedObject is null.
        exceptionThrown = false;
        ctx = new TestTrustVerifierCtxSSL();
        try {
            verifier.isTrustedObject(null, ctx);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("NullPointerException was"
                + " not thrown for a null object");
        }
        exceptionThrown = false;
        try {
            verifier.isTrustedObject(endpoint, null);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("NullPointerException was"
                + " not thrown for a null context");
        }
    }

}
