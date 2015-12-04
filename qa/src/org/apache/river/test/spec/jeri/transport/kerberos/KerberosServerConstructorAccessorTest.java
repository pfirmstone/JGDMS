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
 * org.apache.river.test.spec.jeri.kerberosendpoint.ConstructorAccessorTest
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

package org.apache.river.test.spec.jeri.transport.kerberos;

import java.util.logging.Level;
//harness imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.transport.util.AbstractEndpointTest;
import org.apache.river.test.spec.jeri.transport.util.SubjectProvider;
import org.apache.river.test.spec.jeri.transport.util.TestSocketFactory;
import org.apache.river.test.spec.jeri.transport.util.TestServerSocketFactory;

//java.lang.reflect
import java.lang.reflect.Method;

//java.net
import java.net.InetAddress;

//java.security
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

//java.util
import java.util.Iterator;
import java.util.Set;

//javax.net
import javax.net.SocketFactory;
import javax.net.ServerSocketFactory;

//javax.security
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.kerberos.KerberosPrincipal;

public class KerberosServerConstructorAccessorTest extends AbstractEndpointTest{

    private static final int port = 7070;
    private static final String host = "testHost";
    private static final SocketFactory sf = new TestSocketFactory();
    private static final ServerSocketFactory ssf =
        new TestServerSocketFactory();

    public void run() throws Exception {
        Class factoryClass = null;
        //Obtain a subject
        final Subject subject = SubjectProvider
            .getSubject("transport.KerberosServer");
        log.finest("Obtained subject : " + subject);
        //Obtain the endpoint factory
        final Class c = (Class)
            getConfigObject(Class.class, "serverEndpointFactory");
        factoryClass = c;
        log.finest("Running against " + c.getName());
        //Setup the methods to call
        final Method getHost = c.getMethod("getHost", new Class[] {});
        final Method getPort = c.getMethod("getPort", new Class[]{});
        final Method getServerSocketFactory =
            c.getMethod("getServerSocketFactory",new Class[]{});
        final Method getServerPrincipal = c.getMethod("getPrincipal",
            new Class[]{});
        final Method getSocketFactory =
            c.getMethod("getSocketFactory",new Class[]{});
        final Method getInstance = c.getMethod("getInstance", new
            Class[] {int.class});
        final Method getInstance6Arg = c.getMethod("getInstance",
            new Class[]{
            Subject.class, KerberosPrincipal.class, String.class,
            int.class, SocketFactory.class,ServerSocketFactory.class});
        //Call getInstance(0) under the subject
        Object endpoint = Subject.doAsPrivileged(subject,
            new PrivilegedExceptionAction() {
            public Object run() throws Exception {
                System.out.println(subject.getPrivateCredentials());
                return getInstance.invoke(c,new Object[]{new Integer(0)});
            }
        },null);
        log.finest("Obtained : " + endpoint);
        //Verify the host
        String expectedHost = (String) Subject.doAsPrivileged(subject,
            new PrivilegedExceptionAction() {
            public Object run() throws Exception {
                return InetAddress.getLocalHost().getHostAddress();
            }
        },null);
        String retHost = (String) getHost.invoke(endpoint, new Object[]{});
        if (retHost != null){
            throw new TestException("Expected " + expectedHost
                + " but received " + retHost + " from " + endpoint);
        }
        //Verify that the port is 0
        Integer retPort = (Integer) getPort.invoke(endpoint,new Object[]{});
        if (retPort.intValue()!=0){
            throw new TestException("Incorrect port number in"
                + " " + endpoint);
        }
        //Verify socket factories
        if (getServerSocketFactory.invoke(endpoint,new Object[]{})!=null) {
            throw new TestException("ServerSocketFactory is not"
                + null + " in " + endpoint);
        }
        if (getSocketFactory.invoke(endpoint,new Object[]{})!=null) {
            throw new TestException("Client socket factory is not"
                + " null in " + endpoint);
        }
        //Verify principals
        Set expectedPrincipals = subject.getPrincipals();
        KerberosPrincipal retPrincipal = (KerberosPrincipal)
            getServerPrincipal.invoke(endpoint,new Object[]{});
        Iterator it = expectedPrincipals.iterator();
        if (it.hasNext()) {
            KerberosPrincipal principal = (KerberosPrincipal) it.next();
            if (!principal.equals(retPrincipal)){
                throw new TestException("Principal returned"
                    + " does not match principal in subject");
            }
        }
        //Verify Illegal Argument Exceptions
        boolean exceptionThrown = false;
        try {
            //Call getInstance(-1)
            endpoint = Subject.doAsPrivileged(subject,
                new PrivilegedExceptionAction() {
                public Object run() throws Exception {
                    return getInstance.invoke(
                        c,new Object[]{new Integer(-1)});
                }
            },null);
        } catch (Exception i) {
            //PrivilegedActionException
            Throwable cause = i.getCause();
            //InvocationTargetException
            cause = cause.getCause();
            //SecurityException
            if (cause instanceof IllegalArgumentException) {
                exceptionThrown = true;
            } else {
                i.printStackTrace();
            }
        }
        if (!exceptionThrown) {
            throw new TestException("IllegalArgumentException was"
                + " not thrown when creating an instance with an"
                + " invalid port number");
        }
        try {
            //Call getInstance(65536)
            endpoint = Subject.doAsPrivileged(subject,
                new PrivilegedExceptionAction() {
                public Object run() throws Exception {
                    return getInstance.invoke(
                        c,new Object[]{new Integer(65536)});
                }
            },null);
        } catch (Exception i) {
            //PrivilegedActionException
            Throwable cause = i.getCause();
            //InvocationTargetException
            cause = cause.getCause();
            //SecurityException
            if (cause instanceof IllegalArgumentException) {
                exceptionThrown = true;
            } else {
                i.printStackTrace();
            }
        }
        if (!exceptionThrown) {
            throw new TestException("IllegalArgumentException was"
                + " not thrown when creating an instance with an"
                + " invalid port number");
        }
    }
}
