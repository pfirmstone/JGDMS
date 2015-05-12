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
 * of SSLTrustVerifier.
 *
 * Use Case: Verifying trust on HttpsEndpoint, SslEndpoint,
 * ConfidentialityStrength, and X500Principal instances.
 *
 * Test Design:
 * 1. Obtain an instance of SslTrustVerifier.
 * 2. Verify that instances of HttpsEndpoint and SslEndpoint with no socket
 *    factories are trusted.
 * 3. Verify that instances of HttpsEndpoint and SslEndpoint with trusted
 *    socket factories are trusted.
 * 4. Verify that instances of HttpsEndpoint and SslEndpoint with untrusted
 *    socket factories are not trusted.
 * 5. Verify that instances of ConfidentialityStrength are trusted.
 * 6. Verify that instances of other constraints are not trusted.
 * 7. Verify that instances of X500Principal are trusted.
 * 8. Verify that instances of other principals are not trusted.
 * 9. Verify that Remote and Security exceptions from the TrustVerifier.Context
 *    are propagated.
 * 10. Verify that NullPointerException is thrown if either argument of
 *     isTrustedObject is null.
 *
 * Additional Utility classes
 * 1.Test TrustVerifier.Context implementation
 * 2.Implementations of trusted and untrusted socket factories
 *
 */
package org.apache.river.test.spec.jeri.ssltrustverifier;

import java.util.logging.Level;

//utility classes
import org.apache.river.test.spec.jeri.ssltrustverifier
    .util.AbstractTrustVerifierTestSSL;
import org.apache.river.test.spec.jeri.ssltrustverifier.util.TestTrustVerifierCtxSSL;
import org.apache.river.test.spec.jeri.ssltrustverifier.util.TestSocketFactory;

//harness imports
import org.apache.river.qa.harness.TestException;

//jeri imports
import net.jini.core.constraint.Confidentiality;
import net.jini.jeri.ssl.ConfidentialityStrength;
import net.jini.jeri.ssl.HttpsEndpoint;
import net.jini.jeri.ssl.SslEndpoint;
import net.jini.jeri.ssl.SslTrustVerifier;

//java.net
import java.net.InetAddress;

//java.rmi
import java.rmi.RemoteException;

//javax.security
import javax.security.auth.x500.X500Principal;
import javax.security.auth.kerberos.KerberosPrincipal;

public class IsTrustedObjectTestSSL extends AbstractTrustVerifierTestSSL {

    //inherit javadoc
    public void run() throws Exception {
        //Obtain an instance of SslTrustVerifier
        SslTrustVerifier verifier = new SslTrustVerifier();
        //Verify that instances of HttpsEndpoint and SslEndpoint
        //with no socket factories are trusted.
        int port = Integer.parseInt(getStringValue("listenPort"));
        HttpsEndpoint httpsEndpoint = HttpsEndpoint.getInstance(
            InetAddress.getLocalHost().getHostAddress(), port);
        TestTrustVerifierCtxSSL ctx = new TestTrustVerifierCtxSSL();
        if (!verifier.isTrustedObject(httpsEndpoint,ctx)){
            throw new TestException("HttpsEndpoint instance"
                + " with no socket factory is considered untrusted");
        }
        SslEndpoint sslEndpoint = SslEndpoint.getInstance(
            InetAddress.getLocalHost().getHostAddress(), port);
        if (!verifier.isTrustedObject(sslEndpoint,ctx)){
            throw new TestException("SslEndpoint instance"
                + " with no socket factory is considered untrusted");
        }
        //Verify that instances of HttpsEndpoint and SslEndpoint
        //with trusted socket factories are trusted.
        httpsEndpoint = HttpsEndpoint.getInstance(
            InetAddress.getLocalHost().getHostAddress(), port,
            new TestSocketFactory(true));
        sslEndpoint = SslEndpoint.getInstance(
            InetAddress.getLocalHost().getHostAddress(), port,
            new TestSocketFactory(true));
        if (!verifier.isTrustedObject(httpsEndpoint,ctx)){
            throw new TestException("HttpsEndpoint instance"
                + " with trusted socket factory is considered untrusted");
        }
        if (!verifier.isTrustedObject(sslEndpoint,ctx)){
            throw new TestException("SslEndpoint instance"
                + " with trusted factory is considered untrusted");
        }
        //Verify that instances of HttpsEndpoint and SslEndpoint with
        //untrusted socket factories are not trusted.
        httpsEndpoint = HttpsEndpoint.getInstance(
            InetAddress.getLocalHost().getHostAddress(), port,
            new TestSocketFactory(false));
        sslEndpoint = SslEndpoint.getInstance(
            InetAddress.getLocalHost().getHostAddress(), port,
            new TestSocketFactory(false));
        if (verifier.isTrustedObject(httpsEndpoint,ctx)){
            throw new TestException("HttpsEndpoint instance"
                + " with untrusted socket factory is considered trusted");
        }
        if (verifier.isTrustedObject(sslEndpoint,ctx)){
            throw new TestException("SslEndpoint instance"
                + " with untrusted factory is considered trusted");
        }
        //Verify that instances of ConfidentialityStrength are trusted.
        if (!verifier.isTrustedObject(ConfidentialityStrength.STRONG,ctx)){
            throw new TestException(
            "ConfidentialityStrength.STRONG is considered untrusted");
        }
        if (!verifier.isTrustedObject(ConfidentialityStrength.WEAK,ctx)){
            throw new TestException(
            "ConfidentialityStrength.WEAK is considered untrusted");
        }
        //Verify that instances of other constraints are not trusted.
        if (verifier.isTrustedObject(Confidentiality.YES,ctx)){
            throw new TestException(
            "Constraint other than ConfidentialityStrength"
                + " considered trusted");
        }
        //Verify that instances of X500Principal are trusted.
        X500Principal x5Principal = new X500Principal("CN=\"bogus\"");
        if (!verifier.isTrustedObject(x5Principal,ctx)){
            throw new TestException(
            "X500Principal is considered untrusted");
        }
        KerberosPrincipal kPrincipal = new KerberosPrincipal("bogus@bogus");
        if (verifier.isTrustedObject(kPrincipal,ctx)){
            throw new TestException(
            "Principal other than X500Principal is considered trusted");
        }
        //Verify that Remote and Security exceptions from the
        //TrustVerifier.Context are propagated.
        boolean exceptionThrown = false;
        ctx = new TestTrustVerifierCtxSSL(new RemoteException());
        try {
            verifier.isTrustedObject(httpsEndpoint,ctx);
        } catch (RemoteException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("RemoteException in the"
                + " context was not propagated");
        }
        exceptionThrown = false;
        ctx = new TestTrustVerifierCtxSSL(new SecurityException());
        try {
            verifier.isTrustedObject(httpsEndpoint,ctx);
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
            verifier.isTrustedObject(httpsEndpoint, null);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("NullPointerException was"
                + " not thrown for a null context");
        }
    }

}
