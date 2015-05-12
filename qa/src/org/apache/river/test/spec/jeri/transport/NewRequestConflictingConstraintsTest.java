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
 * org.apache.river.test.spec.jeri.transport.CheckConstraintsTest
 *
 * Purpose: The purpose of this test is to verify that an
 * <code>UnsupportedConstraints</code> exception is thrown if conflicting
 * constrains are specified or if constraints not supported by the transport
 * are specified.  This test can be run against
 * <code>HttpEndpoint</code>, <code>HttpsEndpoint</code>,
 * <code>KerberosEndpoint</code>, <code>SslEndpoint</code>,
 * and <code>TcpEndpoint</code>.
 *
 * Use Case:  Specifying constraints on the transport.
 *
 * Test Design:
 * 1. Obtain an instance of an endpoint implementation.
 * 2. Call <code>newRequest</code> passing in a set of conflicting constraints.
 * 3. Verify that <code>UnsupportedConstraintsException</code> is thrown.
 * 4. Pass in a set of constraints including constraints that are not supported.
 * 5. Verify that <code>UnsupportedConstraints</code> exception is thrown.
 *
 */
package org.apache.river.test.spec.jeri.transport;

import java.util.logging.Level;

//harness imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.transport.util.AbstractEndpointTest;
import org.apache.river.test.spec.jeri.transport.util.SETContext;

//JERI imports
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.Integrity;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.ServerCapabilities;
import net.jini.jeri.ServerEndpoint;

//java.util
import java.util.Iterator;

public class NewRequestConflictingConstraintsTest extends AbstractEndpointTest{

    public void run() throws Exception {
        Endpoint endpoint = null;
        //Obtain endpoint
        ServerEndpoint serverEndpoint = getServerEndpoint();
        endpoint = serverEndpoint
            .enumerateListenEndpoints(new SETContext());
        //Obtain constraints
        InvocationConstraints conflictingConstraints =
            (InvocationConstraints) getConfigObject(
                InvocationConstraints.class, "conflictingConstraints");
        InvocationConstraints unsupportedConstraints =
            (InvocationConstraints) getConfigObject(
                InvocationConstraints.class, "unsupportedConstraints");
        boolean integritySupported = ((Boolean)getConfigObject(
            Boolean.class, "integritySupported")).booleanValue();
        //Verify conflicting constraints
        boolean exceptionThrown = false;
        try {
            OutboundRequestIterator it = endpoint
                .newRequest(conflictingConstraints);
            while (it.hasNext()) {
                OutboundRequest or = it.next();
                log.finest("Obtained " + or + " from " + endpoint);
            }
        } catch (UnsupportedConstraintException e){
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("Conflicting constraints"
                + " did not generate an UnsupportedConstraintsException"
                + " for " + endpoint + ".newRequest()");
        }
        //Verify unsupported constraints
        exceptionThrown = false;
        try {
            OutboundRequestIterator it = endpoint
                .newRequest(conflictingConstraints);
            while (it.hasNext()) {
                OutboundRequest or = it.next();
                log.finest("Obtained " + or + " from " + endpoint);
            }
        } catch (UnsupportedConstraintException e){
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("Unsupported constraints"
                + " did not generate an UnsupportedConstraintsException"
                + " for " + endpoint + ".newRequest()");
        }
    }

}
