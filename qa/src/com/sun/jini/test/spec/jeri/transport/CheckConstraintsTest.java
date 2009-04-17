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
 * com.sun.jini.test.spec.jeri.transport.CheckConstraintsTest
 *
 * Purpose: The purpose of this test is to verify that an
 * <code>UnsupportedConstraints</code> exception is thrown if conflicting
 * constrains are specified or if constraints not supported by the transport
 * are specified.  In addition, the test verifies that Integrity.YES is
 * flagged as a constraint that needs to be implemented by the layers making
 * a call into <code>checkConstraints</code>.  This test can be run against
 * <code>HttpServerEndpoint</code>, <code>HttpsServerEndpoint</code>,
 * <code>KerberosServerEndpoint</code>, <code>SslServerEndpoint</code>,
 * and <code>TcpServerEndpoint</code>.
 *
 * Use Case:  Specifying constraints on the transport.
 *
 * Test Design:
 * 1. Obtain an instance of an endpoint implementation.
 * 2. Call checkConstraints passing in a set of conflicting constraints.
 * 3. Verify that <code>UnsupportedConstraintsException</code> is thrown.
 * 4. Pass in a set of constraints including constraints that are not supported.
 * 5. Verify that <code>UnsupportedConstraints</code> exception is thrown.
 * 6. Pass in a set of constraints including <code>Integrity.YES</code>.
 * 7. Verify that the returned set of constraints includes
 *    <code>Integrity.YES</code>
 *
 */
package com.sun.jini.test.spec.jeri.transport;

import java.util.logging.Level;

//harness imports
import com.sun.jini.qa.harness.TestException;

//utility classes
import com.sun.jini.test.spec.jeri.transport.util.AbstractEndpointTest;

//JERI imports
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.Integrity;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.ServerCapabilities;
import net.jini.jeri.ServerEndpoint;

//java.util
import java.util.Iterator;

public class CheckConstraintsTest extends AbstractEndpointTest {

    public void run() throws Exception {
        ServerEndpoint ep = null;
        //Obtain endpoint
        ep = getServerEndpoint();
        if (!(ep instanceof ServerCapabilities)){
            throw new TestException(ep + " does not implement"
                + " ServerCapabilities.");
        }
        ServerCapabilities serverEndpoint = (ServerCapabilities) ep;
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
            serverEndpoint.checkConstraints(conflictingConstraints);
        } catch (UnsupportedConstraintException e){
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("Conflicting constraints"
                + " did not generate an UnsupportedConstraintsException"
                + " for " + ep);
        }
        //Verify unsupported constraints
        exceptionThrown = false;
        try {
            serverEndpoint.checkConstraints(unsupportedConstraints);
        } catch (UnsupportedConstraintException e){
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("Unsupported constraints"
                + " did not generate an UnsupportedConstraintsException"
                + " for " + ep);
        }
        if (integritySupported) {
            //Verify integrity
            boolean integrityReturned = false;
            InvocationConstraints returned = serverEndpoint
                .checkConstraints(
                    new InvocationConstraints(Integrity.YES,null));
            Iterator it = returned.requirements().iterator();
            while (it.hasNext()) {
                InvocationConstraint ic = (InvocationConstraint) it.next();
                if (ic.equals(Integrity.YES)) {
                    integrityReturned = true;
                    break;
                }
            }
            if (!integrityReturned) {
                throw new TestException("Integrity.YES was not"
                    + " returned from checkConstraints");
            }
        }
    }

}
