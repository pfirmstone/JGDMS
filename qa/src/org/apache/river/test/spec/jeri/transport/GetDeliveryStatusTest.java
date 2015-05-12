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
 * org.apache.river.test.spec.jeri.transport.GetDeliveryStatus
 *
 * Purpose: The purpose of this test is to verify the operation of
 * <code>OutboundReques.getDeliveryStatus</code>.
 *
 * Use Case: Obtaining information on the status of a remote request.
 *
 * Test Design:
 * 1. Obtain a <code>ServerEndpoint</code> instance.
 * 2. Pass in instrumented <code>ListenContext</code> and
 *    <code>RequestDispatcher</code> instances to the
 *    <code>ServerEndpoint</code>.
 * 3. Obtain an <code>OutboundRequestIterator</code> from the
 *    <code>Endpoint</code> and extract its <code>OutboundRequest</code>
 *    instances, if any.
 * 4. Initiate a new request.
 * 5. In the instrumented <code>RequestDispatcher</code>, process the request.
 * 6. Call <code>getDeliveryStatus</code> on the <code>OutboundRequest</code>
 *    and verify that it returns true.
 * 7. Call <code>close</code> on the <code>ListenHandle</code> instances.
 * 8. Initiate a second request.
 * 9. Call <code>getDeliveryStatus</code> on the <code>OutboundRequest</code>
 *    and verify that it returns false.
 */
package org.apache.river.test.spec.jeri.transport;

import java.util.logging.Level;

//jeri imports
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.ServerEndpoint;

//harness imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.transport.util.AbstractEndpointTest;
import org.apache.river.test.spec.jeri.transport.util.EndpointHolder;
import org.apache.river.test.spec.jeri.transport.util.GetDeliveryContext;
import org.apache.river.test.spec.jeri.transport.util.GetDeliveryDispatcher;

//java.io
import java.io.IOException;
import java.io.ObjectOutputStream;

//java.util
import java.util.ArrayList;
import java.util.Iterator;

public class GetDeliveryStatusTest extends AbstractEndpointTest {

    public void run() throws Exception {
        ServerEndpoint se = getServerEndpoint();
        GetDeliveryDispatcher dispatcher = new GetDeliveryDispatcher();
        GetDeliveryContext lc = new GetDeliveryContext(dispatcher);
        Endpoint e =
            se.enumerateListenEndpoints(lc);
        dispatcher.accept();
        OutboundRequestIterator ori = 
            e.newRequest(InvocationConstraints.EMPTY);
        OutboundRequest or = null;
        while (ori.hasNext()) {
            or = ori.next();
            ObjectOutputStream oos = new ObjectOutputStream(
                or.getRequestOutputStream());
            oos.writeInt(1);
            oos.close();
        }
        if (dispatcher.dispatchCalled()!=1){
            throw new TestException("Dispatcher did not receive"
                + " the value sent");
        }
        if (!or.getDeliveryStatus()) {
            throw new TestException("Call on OutboundRequest"
                + ".getDeliveryStatus() returned false for an accepted"
                + " request");
        }
        ArrayList endpoints = lc.getEndpoints();
        Iterator it = endpoints.iterator();
        while (it.hasNext()){
            ((EndpointHolder)it.next()).getListenHandle().close();
        }
        dispatcher.reject();
        try {
            ori = e.newRequest(InvocationConstraints.EMPTY);
            while (ori.hasNext()) {
                or = ori.next();
                ObjectOutputStream oos = new ObjectOutputStream(
                    or.getRequestOutputStream());
                oos.writeInt(2);
                oos.close();
            }
        } catch (IOException ioe) {
            log.finest("Expected Exception: " + ioe.getMessage());
           //Expected IOException
        } catch (RuntimeException re) {
            throw new TestException("Dispatcher called after" +
                " its associated ListenHandle was closed");
        }
        if (or.getDeliveryStatus()) {
            throw new TestException("Call on OutboundRequest"
                + ".getDeliveryStatus() returned true for a failed"
                + " call");
        }
    }

}
