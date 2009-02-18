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
package com.sun.jini.test.spec.jeri.connection;

import java.util.logging.Level;

//harness related imports
import com.sun.jini.qa.harness.TestException;

//utility classes
import com.sun.jini.test.spec.jeri.connection.util.AbstractConnectionTest;
import com.sun.jini.test.spec.jeri.connection.util.ConnectionTransportListener;
import com.sun.jini.test.spec.jeri.connection.util.ListenOperation;
import com.sun.jini.test.spec.jeri.connection.util.TestEndpoint;
import com.sun.jini.test.spec.jeri.connection.util.TestServerEndpoint;
import com.sun.jini.test.spec.jeri.connection.util.TestService;
import com.sun.jini.test.spec.jeri.connection.util.TestServiceImpl;
import com.sun.jini.test.spec.jeri.connection.util.TransportListener;

//jeri imports
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.connection.ConnectionManager;

//java.util
import java.util.HashMap;

/**
 * The purpose of this test is to verify the following behavior of
 * <code>net.jini.jeri.connection.ConnectionManager</code>:
 *     1. The <code>connect</code> method on the <code>ConnectionEnpoint</code>
 *     passed in to the constructor is called when
 *     <code>ConnectionManager.newRequest</code> is called
 *     2. The <code>connect</code> method that takes active and idle connections
 *     is called first on <code>ConnectionEndpoint</code> and only if that
 *     method returns null is the single-argument <code>connect</code> method
 *     called.
 *     3. If a <code>Connection</code> is returned by the
 *     <code>ConnectionEndpoint</code>, the <code>writeRequestData</code>
 *     method is invoked on that connection passing in the
 *     <code>OutboundRequestHandle</code> used in the
 *     <code>ConnectionManager.newRequest</code> call
 *     4. An exception thrown by <code>ConnectionEndpoint.connect</code> or
 *     <code>Connection.writeRequestData</code> is returned to the caller of
 *     <code>ConnectionManager.newRequest</code>
 *     5. <code>Connection.readResponseData</code> is called before any
 *     data is read from the <code>OutboundRequest</code> response input
 *     stream
 *     6. Calls to <code>populateContext</code> and
 *     <code>getUnfulfilledConstraints</code> are delegated to the
 *     <code>Connection</code> associated with the
 *     <code>OutboundRequestHandle</code>
 *
 * Test Design:
 *     1. Construct a connection manager passing in an instrumented
 *     <code>ConnectionEndpoint</code> implementation.
 *     2. Call <code>newRequest</code> on the connection manager created in
 *     step 1.
 *     3. Verify that the <code>connect</code> method that takes active and
 *     idle connections is called.
 *     4. In the instrumented endpoint return a connection object.
 *     5. Verify that the single argument <code>connect</code> method is not
 *     called.
 *     6. Construct a second connection manager.
 *     7. In the instrumented endpoint return null from the first call to
 *     <code>connect</code>.
 *     8. Verify that the single-argument <code>connect</code> method is
 *     called on the instrumented connection endpoint.
 *     9. Call <code>newRequest</code> again and throw an exception in the
 *     instrumented connection endpoint.
 *     10. Verfiy that the exception is propagated to the called of
 *     <code>ConnectionManager.newRequest</code>.
 *     11. Extract an outbound request instance from the iterator returned by
 *     the connection manager constructed in step 1.
 *     12. Make a request over the outbound request.
 *     13. Verify that <code>Connection.writeRequestData</code> is called.
 *     14. Make a second call.
 *     15. This time, inside the instrumented connection object, throw an
 *     exception.
 *     16. Verify that the exception is propagated to the caller of
 *     <code>ConnectionManager.newRequest</code>.
 *     17. Make a third call that returns data from the server side of the
 *     connection.
 *     18. Verify that <code>Connection.readResponseData</code> is called and
 *     that the data in the stream is intact; i.e. no data has been read.
 *     19. Call <code>populateContext</code> and
 *     <code>getUnfulfilledConstraints>/code> on the outbound request.
 *     20. Verify that the corresponding methods are called on the instrumented
 *     connection.
 *
 * Additional Utilities:
 *     1. Instrumented connection endpoint implementation
 */
public class ConnectionManagerTest extends AbstractConnectionTest
    implements TransportListener {

    //Method calls checked by this test
    private HashMap methodCalls = new HashMap();
    private static final String connect1 = "connect1";
    private static final String connect2 = "connect2";
    private static final String populateContext = "populateContext";
    private static final String getUnfulfilledConstraints =
        "getUnfulfilledConstraints";
    private static final String readResponseData = "readResponseData";
    private static final String writeRequestData = "writeRequestData";
    private static final String[] methodNames = new String[]{connect1, connect2,
        populateContext, getUnfulfilledConstraints, readResponseData,
        writeRequestData};

    //inherit javadoc
    public void run() throws Exception {
        //Register for instrumentation calls from the transport
        ConnectionTransportListener.registerListener(this);
        //initiate a listen operation
        TestServerEndpoint tse = new TestServerEndpoint(getListenPort());
        BasicJeriExporter exporter =
            new BasicJeriExporter(tse, new BasicILFactory());
        TestServiceImpl service = new TestServiceImpl();
        TestService stub = (TestService) exporter.export(service);
        TestEndpoint te = tse.getTestEndpoint();
        //make a call
        stub.doSomething();
        //Verify that the 3-arg connect method is called
        if (methodCalls.get(connect2)==null) {
            throw new TestException("The ConnectionManager"
                + " did not call 3-arg connect on the ConnectionEndpoint");
        }
        //Verify that the 1-arg connect method is called
        if (methodCalls.get(connect1)==null) {
            throw new TestException("The ConnectionManager"
                + " did not call 1-arg connect on the ConnectionEndpoint");
        }
        //Verify that writeRequestData is called
        if (methodCalls.get(writeRequestData)==null) {
            throw new TestException("The ConnectionManager"
                + " did not call writeRequestData on the Connection");
        }
        //Verify that readResponseData is called
        if (methodCalls.get(readResponseData)==null) {
            throw new TestException("The ConnectionManager"
                + " did not call readResponseData on the Connection");
        }
        //Verify that populateContext is called
        if (methodCalls.get(populateContext)==null) {
            throw new TestException("The ConnectionManager"
                + " did not call populateContext on the Connection");
        }
        //Verify that getUnfulfilledConstraints is called
        if (methodCalls.get(getUnfulfilledConstraints)==null) {
            throw new TestException("The ConnectionManager"
                + " did not call getUnfulfilledConstraints on the"
                + " Connection");
        }
        //Verify that the single-arg connect is not called if the 3-arg
        //connect is called
        clearMethodCalls();
        stub.doSomething();
        //Verify that the 3-arg connect method is called
        if (methodCalls.get(connect2)==null) {
            throw new TestException("The ConnectionManager"
                + " did not call 3-arg connect on the ConnectionEndpoint");
        }
        //Verify that the single-arg connect method is not called
        if (methodCalls.get(connect1)!=null) {
            throw new TestException("The ConnectionManager called"
                + " 1-arg connect even though a connection was returned on"
                + " call to 3-arg connect");
        }
        //Check exceptions
        clearMethodCalls();
        te.setException("connect");
        boolean exceptionThrown = false;
        try {
           stub.doSomething();
        } catch (Exception e) {
            if (e.getMessage().equals("Bogus Exception")) {
                exceptionThrown = true;
            } else {
                e.printStackTrace();
            }
        }
        if (!exceptionThrown) {
            throw new TestException("The ConnectionManager"
                + " does not propagate an exception thrown in"
                + " ConnectionEndpoint.connect");
        }
        clearMethodCalls();
        te.setException("writeRequestData");
        exceptionThrown = false;
        try {
           stub.doSomething();
        } catch (Exception e) {
            if (e.getMessage().equals("Bogus Exception")) {
                exceptionThrown = true;
            } else {
                e.printStackTrace();
            }
        }
        if (!exceptionThrown) {
            throw new TestException("The ConnectionManager"
                + " does not propagate an exception thrown in"
                + " Connection.writeRequestData");
        }
    }

    //inherit javadoc
    public synchronized void called (String methodName) {
        for (int i=0;i<methodNames.length;i++) {
             if (methodNames[i].equals(methodName)) {
                 methodCalls.put(methodNames[i],methodName);
             }
        }
    }

    /**
     * Clears the methodNames hashmap.
     */
    private synchronized void clearMethodCalls() {
        methodCalls = new HashMap();
    }
}
