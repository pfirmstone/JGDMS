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
package org.apache.river.test.spec.jeri.connection;

import java.util.logging.Level;

//harness related imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.connection.util.AbstractConnectionTest;
import org.apache.river.test.spec.jeri.connection.util.ConnectionTransportListener;
import org.apache.river.test.spec.jeri.connection.util.ListenOperation;
import org.apache.river.test.spec.jeri.connection.util.TestServerEndpoint;
import org.apache.river.test.spec.jeri.connection.util.TestService;
import org.apache.river.test.spec.jeri.connection.util.TestServiceImpl;
import org.apache.river.test.spec.jeri.connection.util.TransportListener;

//jeri imports
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;

//java.util
import java.util.HashMap;

/**
 * The purpose of this test is to verify the following behavior of
 * <code>net.jini.jeri.connection.ServerConnectionManager</code>:
 *     1. When a server connection is started, input and output streams
 *     for each received inbound request are passed into the managed
 *     server connection
 *     2. The <code>checkPermissions</code> method of the server connection
 *     is invoked
 *     3. If <code>checkPermissions</code> throws an exception, the request
 *     dispatcher associated with the managed connection is not called
 *     4. If <code>checkPermissions</code> retunrs normally, the request
 *     dispatcher associated with the managed connection is called
 *     5. Calling <code>checkPermissions</code>, <code>checkConstraints</code>,
 *     and <code>populateContext</code> on an inbound request results in the
 *     corresponding method being called on the managed server connection
 *     6. A <code>NullPointerException</code> is thrown if either argument to
 *     <code>ServerConnectionManager.handleConnection</code> is null
 *
 * Test Design:
 *     1. Initiate a listen operation on an instrumented server endpoint and
 *     pass in a server connection to
 *     <code>ServerConnectionManager.handleConnection</code>.
 *     2. Send a request from the client side of the connection.
 *     3. Verify that <code>checkPermissions</code> is called on the managed
 *     server connection.
 *     4. Verify that the request dispatcher associated with the server
 *     connection is called.
 *     5. Send another request from the client side of the connection.
 *     6. Throw an exception from the instrumented <code>checkPermissions</code>
 *     method.
 *     7. Verify that the request dispatcher associated with the server
 *     connection is not called.
 *     8. Inside the request dispatcher, call <code>checkPermissions</code>,
 *     <code>checkConstraints</code>, and <code>populateContext</code> and
 *     verify that the corresponding methods are called on the managed
 *     server connection.
 *     9. Call <code>ServerConnectionManager.handleConnection</code> with null
 *     values.
 *     10. Verify that <code>NullPointerException</code> is thrown when either
 *     parameter is null.
 *
 * Additional Utilities:
 *     1. Instrumented server connection implementation
 */
public class ServerConnectionManagerTest extends AbstractConnectionTest
    implements TransportListener {

    //Method calls checked by this test
    private HashMap methodCalls = new HashMap();
    private static final String connect1 = "connect1";
    private static final String connect2 = "connect2";
    private static final String dispatch = "dispatch";
    private static final String populateContext = "populateContext";
    private static final String checkPermissions = "checkPermissions";
    private static final String checkConstraints = "checkConstraints";
    private static final String nullServerConnection = "null ServerConnection";
    private static final String nullRequestDispatcher =
        "null RequestDispatcher";
    private static final String[] methodNames = new String[]{connect1,connect2,
        dispatch,populateContext, checkPermissions, checkConstraints,
        nullServerConnection, nullRequestDispatcher};

    public void run() throws Exception {
        //Register for instrumentation calls from the transport
        ConnectionTransportListener.registerListener(this);
        //initiate a listen operation
        BasicJeriExporter exporter = new BasicJeriExporter(
        new TestServerEndpoint(getListenPort()), new BasicILFactory());
        TestServiceImpl service = new TestServiceImpl();
        TestService stub = (TestService) exporter.export(service);
        //send a request and receive a response
        stub.doSomething();
        //Verify that checkPermissions is called
        if (methodCalls.get(checkPermissions)==null) {
            throw new TestException("The ServerConnectionManager"
                + " did not call checkPermissions on the ServerConnection");
        }
        //Verify that the RequestDispatcher is called
        if (methodCalls.get(dispatch)==null) {
            throw new TestException("The ServerConnectionManager"
                + " did not call dispatch on the RequestDispatcher");
        }
        //Verify that populateContext is called
        if (methodCalls.get(populateContext)==null) {
            throw new TestException("The ServerConnectionManager"
                + " did not call populateContext on the ServerConnection");
        }
        //Verify that checkConstraints is called
        if (methodCalls.get(checkConstraints)==null) {
            throw new TestException("The ServerConnectionManager"
               + " did not call checkConstraints on the ServerConnection");
        }
        //check exceptions
        if (methodCalls.get(nullServerConnection)==null) {
            throw new TestException("Null ServerConnection did"
                + " not results in a NullPointerException");
        }
        if (methodCalls.get(nullRequestDispatcher)==null) {
            throw new TestException("Null RequestDispatcher did"
                + " not results in a NullPointerException");
        }
        synchronized(this) {
            methodCalls = new HashMap();
        }
        //Throw a SecurityException in checkPermissions
        ListenOperation.throwException(checkPermissions);
        //make a call
        try {
            stub.doSomething();
        } catch (Exception e) {
            //expected exception
        }
        if (methodCalls.get(dispatch)!=null) {
            throw new TestException("RequestDispatcher was"
                + " called after a security exception was thrown in"
                + " ServerConnection.checkPermissions");
        }
    }

    public synchronized void called (String methodName) {
        for (int i=0;i<methodNames.length;i++) {
             if (methodNames[i].equals(methodName)) {
                 methodCalls.put(methodNames[i],methodName);
             }
        }
    }
}
