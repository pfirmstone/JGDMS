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
 * com.sun.jini.test.spec.jeri.transport.ssl.ServerConstructorAccessorTest
 *
 * Purpose: The purpose of this test is to verify the constructor and accessor
 * methods of the SSL and HTTPS <code>ServerEndpoint</code> classes.
 *
 * Use Case: Constructing SSL and HTTPS <code>ServerEndpoint</code> instances.
 *
 * Test Design:
 * 1. Use an SSL login module to login and obtain a subject with one principal
 *    that has been granted <code>AuthenticationPermission</code> to listen and
 *    <code>SocketPermission</code> to connect to the localHost.
 * 2. Under the subject obtained in step 1, call <code>getInstance</code>
 *    passing in 0 as the port.
 * 3. Call <code>getHost</code> and verify that the return value matches the
 *    return value of <code>InetAddress.getHostName</code>.
 * 4. Call <code>getPort</code> and verify that the return value is 0.
 * 5. Call <code>getPrincipals</code> and verify that the returned principal
 *    matches the principal in the subject obtained in step 1.
 * 6. Call <code>getSocketFactory</code> and verify that a null value is
 *    returned.
 * 7. Call <code>getServerSocketFactory</code> and verify that a null value is
 *    returned.
 * 6. Use an SSL login module to login and obtain a subject with a principal
 *    that has not been granted connect <code>SocketPermission</code> to
 *    connect to the localHost.
 * 7. Under the subject obtained in step 6, call <code>getInstance</code>
 *    passing 0 as the port.
 * 8. Verify that <code>SecurityException</code> is thrown.
 * 9. Call <code>getInstance</code> passing in -1 as the port.
 * 10. Verify that <code>IllegalArgumentException</code> is thrown.
 * 11. Call <code>getInstance</code> passing in 65536 as the port.
 * 12. Verify that <code>IllegalArgumentException</code> is thrown.
 * 13. Call <code>getInstance</code> passing in a subject with two principals
 *     (both of which have AuthenticationPermission listen), an X500Principal[]
 *     array containing one of the principals in the subject, a string
 *     specifying the server host, a port number > 0 and < 65536, a
 *     <code>SocketFactory</code> and a <code>ServerSocketFactory</code>.
 * 14. Call the accessor methods and verify that all parameters match the
 *     parameters passed in in step 11.  Verify that <code>getPrincipals</code>
 *     returns the principal passed in the X500Principal[].
 *
 * Additional Utilities:
 * 1. Policy granting listen AuthenticationPermission and connect
 *    SocketPermission to a set of principals.
 * 2. Socket factory and server socket factory implementations.
 */
package com.sun.jini.test.spec.jeri.transport.ssl;

import java.util.logging.Level;
//harness imports
import com.sun.jini.qa.harness.TestException;

//utility classes
import com.sun.jini.test.spec.jeri.transport.util.AbstractEndpointTest;
import com.sun.jini.test.spec.jeri.transport.util.SubjectProvider;
import com.sun.jini.test.spec.jeri.transport.util.SETContext;
import com.sun.jini.test.spec.jeri.transport.util.TestSocketFactory;
import com.sun.jini.test.spec.jeri.transport.util.TestServerSocketFactory;

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
import javax.security.auth.x500.X500Principal;

//jeri imports
import net.jini.jeri.ServerEndpoint;


public class SslServerConstructorAccessorTest extends AbstractEndpointTest {

    private static final int port = 7070;
    private static final String host = "testHost";
    private static final SocketFactory sf = new TestSocketFactory();
    private static final ServerSocketFactory ssf =
        new TestServerSocketFactory();

    public void run() throws Exception {
        Class factoryClass = null;
        //Obtain a subject
        Subject subject = SubjectProvider
            .getSubject("transport.SslServer");
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
        final Method getPrincipals = c.getMethod("getPrincipals",
            new Class[]{});
        final Method getSocketFactory =
            c.getMethod("getSocketFactory",new Class[]{});
        final Method getInstance = c.getMethod("getInstance", new
            Class[] {int.class});
        final Method getInstance6Arg = c.getMethod("getInstance",
            new Class[]{
            Subject.class, X500Principal[].class, String.class,
            int.class, SocketFactory.class,ServerSocketFactory.class});
        //Call getInstance(0) under the subject
        Object endpoint = Subject.doAsPrivileged(subject,
            new PrivilegedExceptionAction() {
            public Object run() throws Exception {
                return getInstance.invoke(c,new Object[]{new Integer(0)});
            }
        },null);
        log.finest("Obtained : " + endpoint);
        //Verify the host
        String retHost = (String) getHost.invoke(endpoint, new Object[]{});
        if (retHost!=null){
            throw new TestException("Expected null"
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
        Set retPrincipals = (Set)
            getPrincipals.invoke(endpoint,new Object[]{});
        if (expectedPrincipals.size()!=retPrincipals.size()){
            throw new TestException("The number of principals"
               + " returned does not match the number of principals in the"
               + " subject");
        } else {
            Object[] expected = expectedPrincipals.toArray();
            Object[] returned = retPrincipals.toArray();
            for (int i=0; i<returned.length; i++) {
                if (!expected[i].equals(returned[i])) {
                    throw new TestException("Returned principal"
                        + returned[i] + " does not match " + expected[i]
                        + " in the subject");
                }
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
        //Verify Security Exception
        exceptionThrown = false;
        Subject unauthorized = SubjectProvider.getSubject(
            "transport.Unauthorized");
        try {
            //Call getInstance(0) under the unauthorized subject
            endpoint = Subject.doAsPrivileged(unauthorized,
                new PrivilegedExceptionAction() {
                public Object run() throws Exception {
                    ServerEndpoint endpoint = (ServerEndpoint)
                        getInstance.invoke(c,new Object[]{new Integer(0)});
                    return endpoint.enumerateListenEndpoints(
                        new SETContext());
                }
            },null);
        } catch (SecurityException s) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("SecurityException was"
                + " not thrown when creating an instance with an"
                + " unauthorized host");
        }
        //Verification of 6 argument getInstance method pending changes
        //in language of the spec
    }
}
