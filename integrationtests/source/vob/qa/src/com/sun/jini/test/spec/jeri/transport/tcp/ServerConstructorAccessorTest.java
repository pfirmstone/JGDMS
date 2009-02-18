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
 * com.sun.jini.test.spec.jeri.tcp.serverendpoint.ServerConstructorAccessorTest
 *
 * Purpose: The purpose of this test is to verify the constructor and accessor
 * methods in Tcp and Http <code>ServerEndpoint</code> implementations.
 *
 * Use Case: Creating Tcp and Http <code>ServerEndpoint</code> instances.
 *
 * Test Design:
 * 1. Call <code>getInstance</code>  passing in 0 as the server port.
 * 2. Call <code>getPort</code> and verify that the return value is  0.
 * 3. Call <code>getHost</code> and verify that the return value matches the
 *    return value of <code>InetAddress.getLocalHost</code>.
 * 4. Call <code>getClientSocketFactory</code> and verify that the return value
 *    is null.
 * 5. Call <code>getSocketFactory</code> and verify that the return value is
 *    null.
 * 6. Call <code>getInstance</code> passing in -1 as the server port.
 * 7. Verify that <code>IllegalArgumentException</code> is thrown.
 * 8. Call <code>getInstance</code> passing in 65536 as the server port.
 * 9. Verify that <code>IllegalArgumentException</code> is thrown.
 * 10. Call <code>getInstance</code> passing in a port, host, socket factory,
 *     and server socket factory.
 * 11. Call the corresponding accessor methods and verify that the returned
 *     parameters match the parameters passed in in step 8.
 *
 * Additional Utility Classes:
 * 1. Socket factory implementation
 * 2. Server socket factory implementation
 */
package com.sun.jini.test.spec.jeri.transport.tcp;

import java.util.logging.Level;

//harness imports
import com.sun.jini.qa.harness.TestException;

//utility classes
import com.sun.jini.test.spec.jeri.transport.util.AbstractEndpointTest;
import com.sun.jini.test.spec.jeri.transport.util.TestSocketFactory;
import com.sun.jini.test.spec.jeri.transport.util.TestServerSocketFactory;

//java.lang.reflect
import java.lang.reflect.Method;

//java.util
import java.util.logging.Logger;

//java.net
import java.net.InetAddress;

//javax.net
import javax.net.SocketFactory;
import javax.net.ServerSocketFactory;


public class ServerConstructorAccessorTest extends AbstractEndpointTest {
    private static final int port = 7070;
    private static final String host = "testHost";
    private static final SocketFactory sf = new TestSocketFactory();
    private static final ServerSocketFactory ssf =
        new TestServerSocketFactory();

    public void run() throws Exception {
        Class c = null;
        //Obtain the endpoint factory
        c = (Class) getConfigObject(Class.class, "serverEndpointFactory");
        log.finest("Running against " + c.getName());
        //Setup the methods to call
        Method getHost = c.getMethod("getHost", new Class[] {});
        Method getPort = c.getMethod("getPort", new Class[]{});
        Method getSocketFactory =
            c.getMethod("getServerSocketFactory",new Class[]{});
        Method getClientSocketFactory =
            c.getMethod("getSocketFactory",new Class[]{});
        Method getInstance = c.getMethod("getInstance", new
            Class[] {int.class});
        Method getInstance4Arg = c.getMethod("getInstance", new Class[]{
            String.class, int.class, SocketFactory.class,
            ServerSocketFactory.class});
        //Call getInstance passing 0 as the server port
        Object endpoint = getInstance.invoke(
            c, new Object[]{new Integer(0)});
        log.finest("Obtained endpoint instance " + endpoint);
        //Verify that the port is 0
        Integer retPort = (Integer) getPort.invoke(endpoint,new Object[]{});
        if (retPort.intValue()!=0){
            throw new TestException("Incorrect port number in"
                + " " + endpoint);
        }
        //Verify the host
        String retHost = (String) getHost.invoke(endpoint, new Object[]{});
        if (retHost!=null){
            throw new TestException("Expected " + null
                + " but received " + retHost + " from " + endpoint);
        }
        //Verify socket factories
        if (getSocketFactory.invoke(endpoint,new Object[]{})!=null) {
            throw new TestException("ServerSocketFactory is not"
                + null + " in " + endpoint);
        }
        if (getClientSocketFactory.invoke(endpoint,new Object[]{})!=null) {
            throw new TestException("Client socket factory is not"
                + " null in " + endpoint);
        }
        //Call getInstance passing in host, port, client socket factory and
        //server socketfactory
        endpoint = getInstance4Arg.invoke(
            c, new Object[]{host, new Integer(port), sf, ssf});
        //Verify that the port is 0
        retPort = (Integer) getPort.invoke(endpoint,new Object[]{});
        if (retPort.intValue()!=port){
            throw new TestException("Incorrect port number in"
                + " " + endpoint);
        }
        //Verify the host
        retHost = (String) getHost.invoke(endpoint, new Object[]{});
        if (retHost.compareTo(host)!=0){
            throw new TestException("Expected null"
                + " but received " + retHost + " from " + endpoint);
        }
        //Verify socket factories
        if (getSocketFactory.invoke(endpoint,new Object[]{})!=ssf) {
            throw new TestException("ServerSocketFactory is not"
                + null + " in " + endpoint);
        }
        if (getClientSocketFactory.invoke(endpoint,new Object[]{})!=sf) {
            throw new TestException("Client socket factory is not"
                + " null in " + endpoint);
        }
        //verify IllegalArgumentException with port < 0
        boolean exceptionThrown = false;
        try {
            endpoint = getInstance
                .invoke(c,new Object[]{new Integer(-1)});
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                exceptionThrown = true;
            }else {
                e.printStackTrace();
            }
        }
        if (!exceptionThrown) {
            throw new TestException("IllegalArgumentException was"
               + " not thrown when a port < 0 was passed in");
        }
        //verify IllegalArgumentException with port > 65535
        exceptionThrown = false;
        try {
            endpoint = getInstance
                .invoke(c,new Object[]{new Integer(65536)});
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                exceptionThrown = true;
            }else {
                e.printStackTrace();
            }
        }
        if (!exceptionThrown) {
            throw new TestException("IllegalArgumentException was"
               + " not thrown when a port < 1 was passed in");
        }
    }

}
