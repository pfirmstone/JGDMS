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
 * com.sun.jini.test.spec.jeri.transport.NullPointerExceptionTest
 *
 * Purpose: Verify that <code>java.lang.NullPointerException</code>
 *     is thrown if a null <code>ListenContext</code> is passed into
 *     <code>enumerateListenEndpoints</code>
 *
 * Use Case: Erroneously passing a null <code>ListenContext</code> into
 *     <code>ServerEndpoint</code>.
 *
 * Test Design:
 *
 * 1. Obtain an instance of <code>net.jini.jeri.ServerEndpoint</code>.
 * 2. Call <code>enumerateListenEndpoints</code> on the communiction endpoint
 *     obtained in step 1 above passing in a null value for the
 *     <code>ListenContext</code> parameter.
 * 3. Verify that <code>java.lang.NullPointerException</code> is thrown.
 * 4. Call <code>enumerateListenEndpoints</code> on the endpoint obtained in
 *    step 1 and pass a null <code>RequestDispatcher</code> value to
 *    <code>ListenEndpoint.listen</code>.
 * 5. Verify that <code>java.lang.NullPointerException</code> is thrown.
 * 6. Obtain an instance of <code>net.jini.jeri.Endpoint</code> from the
 *    server endpoint obtained in step 1 and pass in a null
 *    <code>InvocationConstraints</code> value to
 *    <code>Endpoint.newRequest</code>.
 * 7. Verify that <code>java.lang.NullPointerException</code> is thrown.
 * 8. Obtain an <code>OutboundRequest</code> from the endpoint obtained in
 *    step 6 and pass is a null <code>Collection</code> value to
 *    <code>OutboundRequest.populateContext</code>
 * 9. Verify that <code>java.lang.NullPointerException</code> is thrown.
 */
package com.sun.jini.test.spec.jeri.transport;

import java.util.logging.Level;

//test harness related imports
import com.sun.jini.qa.harness.TestException;

//jeri imports
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.ServerEndpoint;

//utility classes
import com.sun.jini.test.spec.jeri.transport.util.AbstractEndpointTest;
import com.sun.jini.test.spec.jeri.transport.util.EndpointHolder;
import com.sun.jini.test.spec.jeri.transport.util.SETContext;

//java.util
import java.util.ArrayList;
import java.util.Iterator;

public class NullPointerExceptionTest extends AbstractEndpointTest {
    public void run() throws Exception {
        boolean exceptionThrown = false;
        ServerEndpoint endpoint = getServerEndpoint();
        //Verify endpoint.enumerateListenEndpoint(null);
        try {
            log.finest("Testing enumerateListenEndpoint(null)");
            endpoint.enumerateListenEndpoints(null);
        } catch (NullPointerException e) {
            log.finest("enumerateListenEndpoint(null) throws exception");
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("Passing in a null"
                + " ListenContext to ServerEndpoint.enumerateListenEnpoints"
                + " does not result in a NulPointerException");
        }

        //Verify ListenEndpoint.listen(null);
        endpoint = getServerEndpoint();
        SETContext context = new SETContext();
        endpoint.enumerateListenEndpoints(context);
        ArrayList endpoints = context.getEndpoints();
        log.finest(endpoints.size() + " endpoints returned");
        Iterator i = endpoints.iterator();
        while (i.hasNext()) {
            EndpointHolder holder = (EndpointHolder) i.next();
            ServerEndpoint.ListenEndpoint le = holder.getListenEndpoint();
            exceptionThrown = false;
            try {
                log.finest("Testing ListenEndpoint.listen(null)");
                le.listen(null);
            } catch (NullPointerException e) {
                holder.getListenHandle().close();
                log.finest("ListenEndpoint.listen(null) throws exception");
                exceptionThrown = true;
            }
            if (!exceptionThrown) {
                throw new TestException("Passing in null to"
                    + " ListenEndpoint.listen does not result in a"
                    + " NullPointerException");
            }
        }
        // wait to make sure resources are released before attempting
        // to reuse endpoint
        Thread.currentThread().sleep(1000 * 30); 
        //Verify Endpoint.newRequest(null);
        endpoint = getServerEndpoint();
        context = new SETContext();
        Endpoint ep = endpoint.enumerateListenEndpoints(context);
        exceptionThrown = false;
        try {
            ep.newRequest(null);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("Passing in null to"
                + " Endpoint.newRequest does not result in a"
                + " NullPointerException");
        }

        //Close listen operations
        endpoints = context.getEndpoints();
        i = endpoints.iterator();
        while (i.hasNext()) {
            ((EndpointHolder)i.next()).getListenHandle().close();
        }
        // wait to make sure resources are released before attempting
        // to reuse endpoint
        Thread.currentThread().sleep(1000 * 30); 
        //Verify OutboundRequest.populateContext(null)
        endpoint = getServerEndpoint();
        context = new SETContext();
        ep = endpoint.enumerateListenEndpoints(context);
        OutboundRequestIterator it =
            ep.newRequest(InvocationConstraints.EMPTY);
        while (it.hasNext()) {
            exceptionThrown = false;
            try {
                it.next().populateContext(null);
            } catch (NullPointerException e) {
                exceptionThrown = true;
            }
            if (!exceptionThrown) {
                throw new TestException("Passing in null to"
                    + " OutboundRequest.populateContext does not result"
                    + " in a NullPointerException");
            }
        }
    }
}
