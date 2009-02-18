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
 * com.sun.jini.test.spec.jeri.kerberosendpoint.ConstructorAccessorTest
 *
 * Purpose: The purpose of this test is to verify the constructor
 * and accessor methods for the <code>KerberosEndpoint</code> class.
 *
 * Use Case: Obtaining a <code>KerberosEndpoint</code> instance
 *
 * Test Design:
 * 1. Call <code>getInstance</code> passing in a server host, kerberos server
 *    principal, and -1 as the server port.
 * 2. Verify that <code>IllegalArgumentException</code> is thrown.
 * 3. Call <code>getInstance</code> passing in a kerberos server principal,
 *    a port > 0 and < 65536, and null for the server host value.
 * 4. Verify that <code>NullPointerException</code> is thrown.
 * 5. Call <code>getInstance</code> passing in a server host, kerberos server
 *    principal, and server port.
 * 6. Verify that the parameters passed in to the <code>getInstance</code>
 *    method match the values obtained from the respective accessor methods.
 * 7. Verify that <code>getClientSocketFactory</code> returns null.
 * 8. Call <code>getInstance</code> passing in a server host, kerberos server
 *    principal, server port, and socket factory.
 * 9. Verify that <code>getClientSocketFactory</code> returns the socket
 *    factory passed in in step 8.
 *
 * Additional Utility Classes:
 * 1. Socket factory implementation.
 */

package com.sun.jini.test.spec.jeri.transport.kerberos;

import java.util.logging.Level;

//harness imports
import com.sun.jini.qa.harness.TestException;

//utility classes
import com.sun.jini.test.spec.jeri.transport.util.AbstractEndpointTest;
import com.sun.jini.test.spec.jeri.transport.util.SubjectProvider;
import com.sun.jini.test.spec.jeri.transport.util.TestSocketFactory;

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

public class KerberosConstructorAccessorTest extends AbstractEndpointTest {

    private static final int port = 7070;
    private static final String host = "testHost";
    private static final SocketFactory sf = new TestSocketFactory();

    //inherit javadoc
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

    /**
     * Executes the test described in the class description
     *
     * @return The test result
     */
    private void runTest(Subject subject) throws Exception {
        KerberosPrincipal principal = (KerberosPrincipal)subject
            .getPrincipals().iterator().next();
        KerberosEndpoint endpoint = KerberosEndpoint.getInstance(host,port,
            principal);
        log.finest("Obtained endpoint instance " + endpoint);
        //Verify the host and port parameters match values used at creation
        int portVal = endpoint.getPort();
        if (portVal!=port) {
            throw new TestException("The state of the instance"
                + " does not match the state passed in at creation time."
                + " The port values are different : " + port + " " +
                portVal);
        }
        String hostVal = endpoint.getHost();
        if (hostVal.compareTo(host)!=0) {
            throw new TestException("The state of the instance"
                + " does not match the state passed in at creation time."
                + " The host values are different : " + host + " " +
                hostVal);
        }
        SocketFactory sfVal = endpoint.getSocketFactory();
        if (sfVal!=null) {
            throw new TestException("The state of the instance"
                + " does not match the state passed in at creation time."
                + " The socket factory values are different : " + sf
                + " " + sfVal);
        }
        // Call getInstance passing in a host, port and socket factory.
        endpoint = KerberosEndpoint.getInstance(host,port,principal,sf);
        //verify that the socket factory passed in is maintained
        sfVal = endpoint.getSocketFactory();
        if (sfVal!=sf) {
            throw new TestException("The state of the instance"
                + " does not match the state passed in at creation time."
                + " The socket factory values are different : " + sf
                + " " + sfVal);
        }
        //verify NullPointerException
        boolean exceptionThrown = false;
        try {
            endpoint = KerberosEndpoint.getInstance(null, port, principal);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("NullPointerException was"
                + " not thrown when a null host was passed in");
        }
        //verify IllegalArgumentException with port < 1
        exceptionThrown = false;
        try {
            endpoint = KerberosEndpoint.getInstance(host,0,principal);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("IllegalArgumentException was"
                + " not thrown when a port < 1 was passed in");
        }
        //verify IllegalArgumentException with port > 65535
        exceptionThrown = false;
        try {
            endpoint = KerberosEndpoint.getInstance(host,65536,principal);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("IllegalArgumentException was"
                + " not thrown when a port < 65535 was passed in");
        }
    }
}
