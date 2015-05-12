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
 * org.apache.river.test.spec.jeri.transport.ServerEndpointTest
 *
 * Purpose: The purpose of this test is to verify the following:
 *     1.  The <code>ServerEndpoint</code> implementation calls the
 *         <code>addListenEndpoint</code> method on a supplied
 *         <code>ListenContext</code>
 *     2.  The expected number of <code>ListenEndpoint</code> instances are
 *         passed in to the instrumented </code>ListenContext</code>
 *     5.  The <code>ListenEndpoint.listen</code> method returns a
 *         <code>ListenCookie</code> that
 *         corresponds to the listen operation that was started.
 *     3.  The <code>Endpoint</code> returned by
 *         <code>enumarateListenEndpoints</code> implements Serializable.
 *     4.  An <code>OutboundRequestIterator</code> can be obtained from the
 *         <code>Endpoint</code>.
 *     5.  Calling <code>next</code> on an <code>OutboundRequestIterator</code>
 *         that has returned false from <code>hasNext</code> throws
 *         <code>NoSuchElementException</code>.
 *     6.  <code>OutboundRequest</code> instances that can be used to
 *         communicate with the server side of the connection can be obtained
 *         from the <code>OutboundRequestIterator</code>.
 *     7.  Passing an unmodifiable <code>Collection</code> to
 *         <code>OutboundRequest.populateContext</code> throws
 *         <code>UnsupportedOperationException</code>.
 *     8.  Calling input/output stream accessor methods multiple times
 *         on <code>OutboundRequest</code> return the same objects.
 *     9.  While the listen operation is active, all incoming requests are
 *         dispatched to the <code>RequestDispatcher</code> specified as part
 *         of the listen operation.
 *     10. <code>InboundRequest</code> instances dispatched to the
 *         <code>RequestDispatcher</code> can be used to communicate with
 *         the client side of the connection.
 *     11. The <code>ListenHandle</code> returned by the
 *         <code>ListenEndpoint.listen</code> method can be used to stop
 *         the <code>listen</code> operation.
 *     12. Once close is called on the <code>ListenHandle</code> incoming
 *         requests are no longer dispatched to the
 *         <code>RequestDispatcher</code> associated with the listen operation.
 *
 * Use Cases:
 *     1. Initiating a listen operation.
 *     2. Supplying a listen context to a server endpoint.
 *     3. Supplying a request dispatcher for a listen operation.
 *     4. Sending data between client and server sides of an endpoint.
 *     5. Stopping a listen operation.
 *
 * Test Outline:
 * 1.  Obtain a <code>ServerEndpoint</code> instance.
 * 2.  Call <code>ServerEndpoint.enumerateListenEndpoints</code> passing in an
 *     instrumented <code>ListenContext</code>.
 * 3.  Verify that <code>addListenEndpoint</code> is called on the
 *     <code>ListenContext</code> specified.
 * 4.  Verify that the expected number of <code>ListenEndpoints</code> are
 *     passed in to the context.
 * 5.  For each <code>ListenEndpoint</code> passed in to the instrumented
 *     <code>ServerContext.addListenEndpoint</code> method, call
 *     <code>listen</code> with an instrumented <code>RequestDispatcher</code>,
 *     store the <code>ListenHandle</code> returned, and return
 *     a <code>ListenCookie</code> obtained from the handle.
 * 6.  Verify that the <code>Endpoint</code> returned by
 *     <code>ServerEndpoint.enumerateListenEndpoints</code> is serializable.
 * 7.  Verify that an <code>OutboundRequestIterator</code> can be obtained from
 *     the <code>Endpoint</code>.
 * 8.  Verify that calling <code>next</code> after <code>hasNext</code> returns
 *     false results in <code>NoSuchElementException</code>.
 * 9.  Verify that <code>OutboundRequest</code> instances can be obtained from
 *     the <code>OutboundRequestIterator</code>
 * 10. Verify that the <code>OutboundRequests</code> can be used to communicate
 *     with the server side of the connection.
 * 11. Verify that passing an unmodifiable <code>Collection</code> to
 *     <code>OutboundRequest.populateContext</code> throws
 *     <code>UnsupportedOperationException</code>.
 * 12. Verify that calling input/output streams accessor methods multiple times
 *     on <code>OutboundRequest</code>.
 * 13. Verify that the correct request is received by the
 *     <code>RequestDispatcher</code> instances passed in in step 5.
 * 14. Verify that the correct response is received from the
 *     <code>RequestDispatcherInstances</code> specified in step 5.
 * 15. Call <code>ListenHandle.close</code> on the <code>ListenHandle</code>
 *     instances obtained in step 5.
 * 16. Verify that new requests are not received by the instrumented
 *     <code>RequestDispatcher</code> instances.
 *
 * Additional Utilities:
 * 1. Instrumented <code>RequestDispatcher</code> implementation.
 * 2. Instrumented <code>ListenContext</code> implementation.
 *
 */
package org.apache.river.test.spec.jeri.transport;

import java.util.logging.Level;

//test harness related imports
import org.apache.river.qa.harness.TestException;

//jeri imports
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.ServerEndpoint;

//utility classes
import org.apache.river.test.spec.jeri.transport.util.AbstractEndpointTest;
import org.apache.river.test.spec.jeri.transport.util.EndpointHolder;
import org.apache.river.test.spec.jeri.transport.util.SETContext;
import org.apache.river.test.spec.jeri.transport.util.SETRequestHandler;

//java.io
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

//java.util
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ServerEndpointTest extends AbstractEndpointTest{

     public void run() throws Exception {
        //obtain a ServerEndpoint and verify that the addListenEndpoint
        //is called on the ListenContext specified and the expected number
        //of ListenEndpoints are passed in to the context.
        SETContext lc = new SETContext();
        ServerEndpoint se = getServerEndpoint();
        Endpoint e = se.enumerateListenEndpoints(lc);
        boolean exceptionThrown = false;
        log.finest("Call to enumerateListeEndpoints returns " + e);
        Integer expected = (Integer) getConfigObject(Integer.class,
            "expectedListenEndpoints");
        ArrayList endpoints = lc.getEndpoints();
        if (expected.intValue() != endpoints.size()) {
            throw new TestException(
                "ServerEndpoint.enumerateListenEndpoints did not return"
                + "the expected number of ListenEndpoints.  Expected "
                + expected.intValue() + " but received "
                + endpoints.size());
        }
        //Verify that the returned endpoint is serializable
        if (!(e instanceof Serializable)){
            throw new TestException("The Endpoint returned by"
                + " ServerEndpoint.enumerateListenEndpoints is not"
                + " serializable");
        }

        //Verify that an outbound request can be obtained from the Endpoint
        OutboundRequestIterator ori =
            e.newRequest(InvocationConstraints.EMPTY);
        int response = 0;
        while (ori.hasNext()) {
            //Verify that the outbound request can be used to communicate
            //with the server side of the connection
            OutboundRequest or = ori.next();
            ObjectOutputStream oos =
                new ObjectOutputStream(or.getRequestOutputStream());
            oos.writeInt(this.hashCode());
            oos.close();
            ObjectInputStream ois = new ObjectInputStream(
                or.getResponseInputStream());
            response = ois.readInt();
            ois.close();
            //Verify UnsupportedOperationException
            ArrayList al = new ArrayList();
            al.add(new Object());
            Collection uc = Collections.unmodifiableCollection(al);
            exceptionThrown = false;
            try {
                uc.add(new Object());
                //This is an implementation specific check
                //comment code line above this comment if implementation
                //is expected to populate the passed in context
                or.populateContext(uc);
            } catch (UnsupportedOperationException uoe) {
                //This exception would only occur if one of the
                //JERI layers attempts to write context information
                //to the collection
                exceptionThrown = true;
            }
            if (!exceptionThrown){
                throw new TestException("Passing an unmodifiable"
                    + " collection to populateContext does not result"
                    + " in UnsupportedOperationException");
            }
            //Verify idempotency of input/output streams
            InputStream i1 = or.getResponseInputStream();
            InputStream i2 = or.getResponseInputStream();
            OutputStream o1 = or.getRequestOutputStream();
            OutputStream o2 = or.getRequestOutputStream();
            if (o1!=o2||i1!=i2) {
                throw new TestException("Idempotency of streams"
                    + " is not maintained");
            }
        }

        //Verify NoSuchElementException
        exceptionThrown = false;
        try {
            ori.next();
        } catch (NoSuchElementException nse) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("Calling next on an iterator"
                + " that has returned false from hasNext does not throw"
                + " NoSuchElementException");
        }

        Iterator it = endpoints.iterator();
        while(it.hasNext()) {
            EndpointHolder eph = (EndpointHolder) it.next();
            SETRequestHandler rh = (SETRequestHandler)
                eph.getRequestHandler();
            if (response != rh.hashCode()){
                throw new TestException("The response received"
                    + "does not match the response sent by the server"
                    + "side of the connection.  Server sent "
                    + rh.hashCode() + ", but the client got " + response);
            }
            ArrayList requests = rh.getRequests();
            if (requests.size()!=1){
                throw new TestException("The RequestDisptcher"
                    + " received the incorrect number of requests.  "
                    + "Expected 1 and received " + requests.size());
            }
            Iterator requestIterator = requests.iterator();
            while (requestIterator.hasNext()){
                Integer value = (Integer) requestIterator.next();
                if (value.intValue()!=this.hashCode()){
                    throw new TestException("The data read from"
                        + " the InboundRequest does not match the data"
                        + " written to the OutboundRequest.  Wrote "
                        + this.hashCode() + " read " + value);
                }
            }
            ServerEndpoint.ListenHandle lh = eph.getListenHandle();
            //Close the listen operation
            lh.close();
        }
        //Verify that new requests are not received after close has
        //been called on the ListenHandle
        try {
            ori = e.newRequest(InvocationConstraints.EMPTY);
            while (ori.hasNext()) {
                OutboundRequest or = ori.next();
                ObjectOutputStream oos =
                    new ObjectOutputStream(or.getRequestOutputStream());
                oos.writeInt(this.hashCode());
                oos.close();
            }
            it = endpoints.iterator();
            while(it.hasNext()) {
                EndpointHolder eph = (EndpointHolder) it.next();
                SETRequestHandler rh = (SETRequestHandler)
                    eph.getRequestHandler();
                ArrayList requests = rh.getRequests();
                if (requests.size()!=1){
                    throw new TestException("Requests were"
                        + " delivered to the server after a close operation"
                        + " on the ListenHandle");
                }
            }
        } catch (IOException ioe){
            //An IOException is valid afted close has been called on the
            //ListenHandle
        }
     }
}
