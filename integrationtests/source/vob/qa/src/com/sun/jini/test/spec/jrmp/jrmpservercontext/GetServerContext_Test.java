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
package com.sun.jini.test.spec.jrmp.jrmpservercontext;

import java.util.logging.Level;

// java.util
import java.util.logging.Level;

// net.jini
import net.jini.jrmp.JrmpExporter;
import net.jini.jrmp.JrmpServerContext;
import net.jini.iiop.IiopExporter;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.jrmp.util.AbstractTestBase;
import com.sun.jini.test.spec.jrmp.util.TestRemoteObject;
import com.sun.jini.test.spec.jrmp.util.TestRemoteInterface;

// org.omg
import org.omg.CORBA.ORB;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     getServerContext method of JrmpServerContext returns a server context
 *     collection containing an element that implements the ClientHost
 *     interface whose getClientHost method returns the client host if the
 *     current thread is handling a JRMP remote call, or null otherwise.
 *     The client host string is determined by calling
 *     RemoteServer.getClientHost; if getClientHost throws a
 *     ServerNotActiveException, then no JRMP call is in progress and
 *     null is returned.
 *
 * Test Cases
 *   This test uses different constructors to construct JrmpExporter.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestRemoteObject - object that implements java.rmi.Remote interface.
 *     It contains getServerContext method which construct JrmpServerContext
 *     and then if result is equal to null or it is not an instance of
 *     Collection it returns false otherwise it tries to invoke
 *     getClientHost method of element of returned Collection: if it returns
 *     null then false will be returned, otherwise method returns true.
 *
 * Action
 *   First, test situation when no JRMP call is in progress:
 *     1) construct a JrmpServerContext
 *     2) invoke getServerContext method of JrmpServerContext
 *     3) assert that null will be returned
 *   Test situation when IIOP call is in progress but there are no JRMP calls:
 *     1) construct a IiopExporter
 *     2) construct a TestRemoteObject
 *     3) invoke export of constructed IiopExporter with constructed
 *        TestRemoteObject as a parameter
 *     4) invoke getServerContext method of TestRemoteObject
 *     5) assert that false will be returned
 *   Test situation when current thread is handling a JRMP remote call
 *     1) construct a JrmpExporter
 *     2) construct a TestRemoteObject
 *     3) invoke export of constructed JrmpExporter with constructed
 *        TestRemoteObject as a parameter
 *     4) invoke getServerContext method of TestRemoteObject
 *     5) assert that true will be returned
 * </pre>
 */
public class GetServerContext_Test extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        // step 1
        JrmpServerContext jsc = new JrmpServerContext();
        Object obj = jsc.getServerContext();

        if (obj != null) {
            // FAIL
            throw new TestException(
                    "Result of getServerContext invocation when there are "
                    + "no remote calls in progress is: " + obj
                    + ", while null is expected.");
        } else {
            // PASS
            logger.log(Level.FINE,
                    "Result of getServerContext invocation when there "
                    + "are no remote calls is null as expected.");
        }

        // step 2
        ORB orb = ORB.init(new String[0], null);
        IiopExporter ie = new IiopExporter(orb);
        TestRemoteObject tro = new TestRemoteObject();
        TestRemoteInterface stub = (TestRemoteInterface) ie.export(tro);

        if (stub.checkGetServerContext().booleanValue()) {
            // FAIL
            throw new TestException(
                    "Result of getServerContext invocation when there are "
                    + "no JRMP remote calls in progress but there is a "
                    + "IIOP remote call in progress does not satisfy "
                    + "specification.");
        } else {
            // PASS
            logger.log(Level.FINE,
                    "Result of getServerContext invocation when there "
                    + "are no JRMP remote calls in progress but there is a "
                    + "IIOP remote call in progress satisfies "
                    + "specification.");
        }

        // unexport object
        ie.unexport(true);

        // step 3
        JrmpExporter je = createJrmpExporter();
        tro = new TestRemoteObject();
        stub = (TestRemoteInterface) je.export(tro);

        if (!stub.checkGetServerContext().booleanValue()) {
            // FAIL
            throw new TestException(
                    "Result of getServerContext invocation when there is a "
                    + "remote call in progress does not satisfy "
                    + "specification.");
        } else {
            // PASS
            logger.log(Level.FINE,
                    "Result of GetServerContext invocation when there "
                    + "is a remote call in progress satisfies "
                    + "specification.");
        }

        // unexport object
        je.unexport(true);
    }
}
