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
 * org.apache.river.test.spec.jeri.tcpendpoint.ConstructorAccessorTest
 *
 * Purpose: The purpose of this test is to verify the constructor
 * and accessor methods for the endpoint factory class.  This test can be run
 * against <code>TcpEndpoint</code>, <code>HttpEndpoint</code>,
 * <code>SslEndpoint</code>, and <code>HttpsEndpoint</code>.
 *
 * Use Case: Obtaining an instance <<EndpointFactory>>
 *
 * Test Design:
 * 1. Call <code><<EndpointFactory>>.getInstance</code> passing in a host and
 *    port.
 * 2. Call <code><<EndpointFactory>>.getPort</code>.
 * 3. Verify that the port passed in in step 1 is returned.
 * 4. Call <code><<EndpointFactory>>.getHost</code>.
 * 5. Verify that the host passed in in step 1 is returned.
 * 6. Call <code><<EndpointFactory>>.getSocketFactory</code>.
 * 7. Verify that the socket factory is null.
 * 8. Call <code><<EndpointFactory>>.getInstance</code> passing in a host, port
 *    and socket factory.
 * 9. Call <code><<EndpointFactory>>.getSocketFactory</code>.
 * 10. Verify that the socket factory passed in in step 8 is returned.
 * 11. Call <code><<EndpointFactory>>.getInstance</code> passing in a null host.
 * 12. Verify that <code>NullPointerException</code> is thrown.
 * 13. Call <code><<EndpointFactory>>.getInstance</code> passing in a port < 1.
 * 14. Verify that <code>IllegalArgumentException</code> is thrown.
 * 13. Call <code><<EndpointFactory>>.getInstance</code> passing in a port >
 *     65535.
 * 14. Verify that <code>IllegalArgumentException</code> is thrown.
 *
 * Additional Utility Classes:
 * 1. Socket factory implementation.
 */
package org.apache.river.test.spec.jeri.transport;

import java.util.logging.Level;

//harness imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.transport.util.AbstractEndpointTest;
import org.apache.river.test.spec.jeri.transport.util.TestSocketFactory;
//java.lang.reflect
import java.lang.reflect.Method;

//java.util
import java.util.logging.Logger;

//javax.net
import javax.net.SocketFactory;

public class ConstructorAccessorTest extends AbstractEndpointTest{

    private static final int port = 7070;
    private static final String host = "testHost";
    private static final SocketFactory sf = new TestSocketFactory();
    public void run() throws Exception {
        Class c = null;
        //Obtain the endpoint factory
        c = (Class) getConfigObject(Class.class, "endpointFactory");
        log.finest("Running against " + c.getName());
        //Setup the methods to call
        Method getHost = c.getMethod("getHost", new Class[] {});
        Method getPort = c.getMethod("getPort", new Class[]{});
        Method getSocketFactory =
            c.getMethod("getSocketFactory",new Class[]{});
        Method getInstance2Arg = c.getMethod("getInstance", new
            Class[] {String.class, int.class});
        Method getInstance3Arg = c.getMethod("getInstance", new Class[]{
            String.class, int.class, SocketFactory.class});
        Object endpoint = getInstance2Arg.invoke(
            c, new Object[]{host, new Integer(port)});
        log.finest("Obtained endpoint instance " + endpoint);
        //Verify the host and port parameters match values used at creation
        Integer portVal = (Integer)
            getPort.invoke(endpoint, new Object[]{});
        if (portVal.intValue()!=port) {
            throw new TestException("The state of the instance"
                + " does not match the state passed in at creation time."
                + " The port values are different : " + port + " " +
                portVal);
        }
        String hostVal = (String) getHost.invoke(endpoint, new Object[]{});
        if (hostVal.compareTo(host)!=0) {
            throw new TestException("The state of the instance"
                + " does not match the state passed in at creation time."
                + " The host values are different : " + host + " " +
                hostVal);
        }
        SocketFactory sfVal = (SocketFactory) getSocketFactory.invoke(
            endpoint, new Object[]{});
        if (sfVal!=null) {
            throw new TestException("The state of the instance"
                + " does not match the state passed in at creation time."
                + " The socket factory values are different : " + sf
                + " " + sfVal);
        }
        // Call getInstance passing in a host, port and socket factory.
        endpoint = getInstance3Arg.invoke(c, new Object[] {
            host,new Integer(port),sf});
        //verify that the socket factory passed in is maintained
        sfVal = (SocketFactory) getSocketFactory.invoke(
            endpoint, new Object[]{});
        if (sfVal!=sf) {
            throw new TestException("The state of the instance"
                + " does not match the state passed in at creation time."
                + " The socket factory values are different : " + sf
                + " " + sfVal);
        }
        //verify NullPointerException
        boolean exceptionThrown = false;
        try {
            endpoint = getInstance2Arg
                .invoke(c,new Object[]{null, new Integer(port)});
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof NullPointerException) {
                exceptionThrown = true;
            } else {
                e.printStackTrace();
            }
        }
        if (!exceptionThrown) {
            throw new TestException("NullPointerException was"
               + " not thrown when a null host was passed in");
        }
        //verify IllegalArgumentException with port < 1
        exceptionThrown = false;
        try {
            endpoint = getInstance2Arg
                .invoke(c,new Object[]{host, new Integer(0)});
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
        //verify IllegalArgumentException with port > 65535
        exceptionThrown = false;
        try {
            endpoint = getInstance2Arg
                .invoke(c,new Object[]{host, new Integer(65536)});
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                exceptionThrown = true;
            } else {
                e.printStackTrace();
            }
        }
        if (!exceptionThrown) {
            throw new TestException("IllegalArgumentException was"
               + " not thrown when a port < 65535 was passed in");
        }
    }


}
