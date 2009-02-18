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
 * com.sun.jini.test.spec.jeri.transport.CheckTrustEquivalenceTest
 *
 * Purpose: The purpose of this test is to verify functionality of the
 * <code>checkTrustEquivalence</code> method.
 *
 * Use Case: Comparing two <code>KerberosEndpoint</code> instances.
 *
 * Test Design:
 * 1. Create an endpoint instance passing in a host, port, and
 *    kerberos principal.
 * 2. Create a second endpoint instance passing in the same host, port, and
 *    kerberos principal.
 * 3. Call checkTrustEquivalence on both endpoints passing in the other
 *    endpoint as the argument.
 * 4. Verify that the calls to checkTrustEquivalence return true.
 * 5. Create an endpoint instance with the same host and port and a different
 *    than kerberos principal than the one used in step 1.
 * 6. Repeat step 3.
 * 7. Verify that the calls to checkTrustEquivalence return false.
 * 8. Create an endpoint instance with the same port and kerberos principal
 *    and a different host than the one used in step 1.
 * 9. Repeat step 3.
 * 10. Verify that the calls to checkTrustEquivalence return false.
 * 11. Create an endpoint instance with the same host and kerberos principal
 *     and a different port than the one used in step 1.
 * 12. Repeat step 3.
 * 13. Verify that the calls to checkTrustEquivalence return false.
 * 14. Create an endpoint instance passing in a host, port, kerberos principal,
 *     and socket factory that does not implement TrustEquivalence.
 * 12. Create a second endpoint using the same parameters used in step 11.
 * 13. Repeat step 3.
 * 14. Verify that the calls to checkTrustEquivalence return false.
 * 15. Repeat step 11 with a socket factory that implements TrustEquivalence.
 * 16. Create a second endpoint using the same parameters used in step 15.
 * 17. Repeat step 3.
 * 18. Verify that the calls to checkTrustEquivalence return true.
 *
 * Additional Utility Classes:
 * 1. Socket factory implementation
 * 2. Socket factory implementation that also implements trust equivalence.
 */
package com.sun.jini.test.spec.jeri.transport.kerberos;

import java.util.logging.Level;

//harness imports
import com.sun.jini.qa.harness.TestException;

//utility classes
import com.sun.jini.test.spec.jeri.transport.util.AbstractEndpointTest;
import com.sun.jini.test.spec.jeri.transport.util.SubjectProvider;
import com.sun.jini.test.spec.jeri.transport.util.UnequalSocketFactory;
import com.sun.jini.test.spec.jeri.transport.util.TrustEquivalenceSocketFactory;

//java.util
import java.util.logging.Logger;

//javax.net
import javax.net.SocketFactory;

//java.security
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

//javax.security
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;

//JERI imports
import net.jini.jeri.kerberos.KerberosEndpoint;
import net.jini.security.proxytrust.TrustEquivalence;

public class KerberosCheckTrustEquivalenceTest extends AbstractEndpointTest{

    private static final int port = 7070;
    private static final int port2 = 7071;
    private static final String host = "testHost";
    private static final String host2 = "testHost2";
    private static final SocketFactory sf = new UnequalSocketFactory();
    private static final SocketFactory trustSf =
        new TrustEquivalenceSocketFactory();

    public void run() throws Exception {
        final Subject subject = SubjectProvider
            .getSubject("transport.KerberosClient");
        Subject.doAs(subject,
            new PrivilegedExceptionAction(){
                public Object run() throws Exception {
                    runTest(subject);
                    return null;
                }
        });
    }

    private void runTest(Subject subject) throws Exception {
        KerberosPrincipal principal = (KerberosPrincipal)subject
            .getPrincipals().iterator().next();
        //Create an endpoint instance passing in a host and port.
        KerberosEndpoint kEndpoint = KerberosEndpoint
            .getInstance(host,port,principal);
        if (!(kEndpoint instanceof TrustEquivalence)) {
            throw new TestException(
                kEndpoint + " does not implement TrustEquivalence");
        }
        TrustEquivalence endpoint1 = (TrustEquivalence) kEndpoint;
        //Create a second endpoint instance passing in the same host and
        //port.
        KerberosEndpoint kEndpoint2 = KerberosEndpoint
            .getInstance(host,port,principal);
        if (!(kEndpoint2 instanceof TrustEquivalence)) {
            throw new TestException(
                kEndpoint2 + " does not implement TrustEquivalence");
        }
        TrustEquivalence endpoint2 = (TrustEquivalence) kEndpoint2;
        //Verify TrustEquivalence
        if (!endpoint1.checkTrustEquivalence(endpoint2) ||
            !endpoint2.checkTrustEquivalence(endpoint1)) {
            throw new TestException("TrustEquivalence not"
                + " established on equivalent endpoints.");
        }
        //Create an endpoint instance with the same host and a
        //different port
        KerberosEndpoint kEndpoint3 = KerberosEndpoint
            .getInstance(host,port2,principal);
        if (!(kEndpoint3 instanceof TrustEquivalence)) {
            throw new TestException(
                kEndpoint3 + " does not implement TrustEquivalence");
        }
        TrustEquivalence endpoint3 = (TrustEquivalence) kEndpoint3;
        //Verify TrustEquivalence
        if (endpoint1.checkTrustEquivalence(endpoint3) ||
            endpoint3.checkTrustEquivalence(endpoint1)) {
            throw new TestException("TrustEquivalence"
                + " established on non-equivalent endpoints.");
        }
        //Create an endpoint instance with the same port and a
        //different host
        KerberosEndpoint kEndpoint4 = KerberosEndpoint
            .getInstance(host2,port,principal);
        if (!(kEndpoint4 instanceof TrustEquivalence)) {
            throw new TestException(
                kEndpoint4 + " does not implement TrustEquivalence");
        }
        TrustEquivalence endpoint4 = (TrustEquivalence) kEndpoint4;
        //Verify TrustEquivalence
        if (endpoint1.checkTrustEquivalence(endpoint4) ||
            endpoint4.checkTrustEquivalence(endpoint1)) {
                throw new TestException("TrustEquivalence"
                    + " established on non-equivalent endpoints.");
        }
        //Create endpoint instances passing in a host, port,
        //and socket factory that does not implement TrustEquivalence.
        kEndpoint = KerberosEndpoint
            .getInstance(host,port,principal,sf);
        if (!(kEndpoint instanceof TrustEquivalence)) {
            throw new TestException(
                kEndpoint + " does not implement TrustEquivalence");
        }
        endpoint1 = (TrustEquivalence) kEndpoint;
        kEndpoint2 = KerberosEndpoint
            .getInstance(host,port,principal,sf);
        if (!(kEndpoint2 instanceof TrustEquivalence)) {
            throw new TestException(
                kEndpoint2 + " does not implement TrustEquivalence");
        }
        endpoint2 = (TrustEquivalence) kEndpoint2;
        //Verify TrustEquivalence
        if (endpoint1.checkTrustEquivalence(endpoint2) ||
            endpoint2.checkTrustEquivalence(endpoint1)) {
            throw new TestException("TrustEquivalence"
            + " established on endpoints with factories that"
            + " do not implement TrustEquivalence.");
        }
        //Create endpoint instances passing in a host, port,
        //and socket factory that implements TrustEquivalence.
        kEndpoint = KerberosEndpoint
            .getInstance(host,port,principal,trustSf);
        if (!(kEndpoint instanceof TrustEquivalence)) {
            throw new TestException(
                kEndpoint + " does not implement TrustEquivalence");
        }
        endpoint1 = (TrustEquivalence) kEndpoint;
        kEndpoint2 = KerberosEndpoint
            .getInstance(host,port,principal,trustSf);
        if (!(kEndpoint2 instanceof TrustEquivalence)) {
            throw new TestException(
                kEndpoint2 + " does not implement TrustEquivalence");
        }
        endpoint2 = (TrustEquivalence) kEndpoint2;
        //Verify TrustEquivalence
        if (!endpoint1.checkTrustEquivalence(endpoint2) ||
            !endpoint2.checkTrustEquivalence(endpoint1)) {
            throw new TestException("TrustEquivalence"
            + " not established on equivalend endpoints with factories"
            + " that implement TrustEquivalence.");
        }
    }
}
