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
 * org.apache.river.test.spec.jeri.httpendpoint.checkTrustEquivalenceTest
 *
 * Purpose: The purpose of this test is to verify functionality of the
 * <code>checkTrustEquivalence</code> method.  This test can be run against:
 * <code>HttpEndpoint</code>, <code>HttpsEndpoint<code>,
 * <code>SslEndpoint</code>, and <code>TcpEndpoint</code>.
 *
 * Use Case: Comparing two <<Endpoint>> instances.
 *
 * Test Design:
 * 1. Create an endpoint instance passing in a host and port.
 * 2. Create a second endpoint instance passing in the same host and port.
 * 3. Call checkTrustEquivalence on both endpoints passing in the other
 *    endpoint as the argument.
 * 4. Verify that the calls to checkTrustEquivalence return true.
 * 5. Create an endpoint instance with the same host and a different port than
 *    the one used in step 1.
 * 6. Repeat step 3.
 * 7. Verify that the calls to checkTrustEquivalence return false.
 * 8. Create an endpoint instance with the same port and a different host than
 *    the one used in step 1.
 * 9. Repeat step 3.
 * 10. Verify that the calls to checkTrustEquivalence return false.
 * 11. Create an endpoint instance passing in a host, port, and socket factory
 *     that does not implement TrustEquivalence.
 * 12. Create a second endpoint using the same parameters used in step 11.
 * 13. Repeat step 3.
 * 14. Verify that the calls to checkTrustEquivalence return false.
 * 15. Repeat step 11 with a socket factory that implements TrustEquivalence.
 * 16. Create a second endpoint using the same parameters used in step 15.
 * 17. Repeat step 3.
 * 18. Verify that the calls to checkTrustEquivalence return true.
 *
 * Additional Utility Classes:
 * 1. Socket factory implementation
 * 2. Socket factory implementation that also implements trust equivalence.
 */
package org.apache.river.test.spec.jeri.transport;

import java.util.logging.Level;

//harness imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.transport.util.AbstractEndpointTest;
import org.apache.river.test.spec.jeri.transport.util.UnequalSocketFactory;
import org.apache.river.test.spec.jeri.transport.util.TrustEquivalenceSocketFactory;

//java.lang.reflect
import java.lang.reflect.Method;

//java.util
import java.util.logging.Logger;

//javax.net
import javax.net.SocketFactory;

//JERI imports
import net.jini.security.proxytrust.TrustEquivalence;

public class CheckTrustEquivalenceTest extends AbstractEndpointTest{

    private static final int port = 7070;
    private static final int port2 = 7071;
    private static final String host = "testHost";
    private static final String host2 = "testHost2";
    private static final SocketFactory sf = new UnequalSocketFactory();
    private static final SocketFactory trustSf =
        new TrustEquivalenceSocketFactory();

    public void run() throws Exception {
        Class c;
        //Obtain the endpoint factory
        c = (Class) getConfigObject(Class.class, "endpointFactory");
        log.finest("Running against " + c.getName());
        //Obtain the getInstance methods used by the test
        Method getInstance2Arg = c.getMethod("getInstance", new
            Class[] {String.class, int.class});
        Method getInstance3Arg = c.getMethod("getInstance", new Class[]{
            String.class, int.class, SocketFactory.class});
        //Create an endpoint instance passing in a host and port.
        Object obj = getInstance2Arg.invoke(
            c, new Object[]{host, new Integer(port)});
        if (!(obj instanceof TrustEquivalence)) {
            throw new TestException(
                obj + " does not implement TrustEquivalence");
        }
        TrustEquivalence endpoint1 = (TrustEquivalence) obj;
        //Create a second endpoint instance passing in the same host and
        //port.
        obj = getInstance2Arg.invoke(
            c, new Object[]{host, new Integer(port)});
        if (!(obj instanceof TrustEquivalence)) {
            throw new TestException(
                obj + " does not implement TrustEquivalence");
        }
        TrustEquivalence endpoint2 = (TrustEquivalence) obj;
        //Verify TrustEquivalence
        if (!endpoint1.checkTrustEquivalence(endpoint2) ||
            !endpoint2.checkTrustEquivalence(endpoint1)) {
            throw new TestException("TrustEquivalence not"
                + " established on equivalent endpoints.");
        }
        //Create an endpoint instance with the same host and a
        //different port
        obj = getInstance2Arg.invoke(
            c, new Object[]{host, new Integer(port2)});
        if (!(obj instanceof TrustEquivalence)) {
            throw new TestException(
                obj + " does not implement TrustEquivalence");
        }
        TrustEquivalence endpoint3 = (TrustEquivalence) obj;
        //Verify TrustEquivalence
        if (endpoint1.checkTrustEquivalence(endpoint3) ||
            endpoint3.checkTrustEquivalence(endpoint1)) {
            throw new TestException("TrustEquivalence"
                + " established on non-equivalent endpoints.");
        }
        //Create an endpoint instance with the same port and a
        //different host
        obj = getInstance2Arg.invoke(
            c, new Object[]{host2, new Integer(port)});
        if (!(obj instanceof TrustEquivalence)) {
            throw new TestException(
                obj + " does not implement TrustEquivalence");
        }
        TrustEquivalence endpoint4 = (TrustEquivalence) obj;
        //Verify TrustEquivalence
        if (endpoint1.checkTrustEquivalence(endpoint4) ||
            endpoint4.checkTrustEquivalence(endpoint1)) {
            throw new TestException("TrustEquivalence"
                + " established on non-equivalent endpoints.");
        }
        //Create endpoint instances passing in a host, port,
        //and socket factory that does not implement TrustEquivalence.
        obj = getInstance3Arg.invoke(
            c, new Object[]{host2, new Integer(port),sf});
        if (!(obj instanceof TrustEquivalence)) {
            throw new TestException(
                obj + " does not implement TrustEquivalence");
        }
        endpoint1 = (TrustEquivalence) obj;
        obj = getInstance3Arg.invoke(
            c, new Object[]{host2, new Integer(port),sf});
        if (!(obj instanceof TrustEquivalence)) {
            throw new TestException(
                obj + " does not implement TrustEquivalence");
        }
        endpoint2 = (TrustEquivalence) obj;
        //Verify TrustEquivalence
        if (endpoint1.checkTrustEquivalence(endpoint2) ||
            endpoint2.checkTrustEquivalence(endpoint1)) {
            throw new TestException("TrustEquivalence"
                + " established on endpoints with factories that"
                + " do not implement TrustEquivalence.");
        }
        //Create endpoint instances passing in a host, port,
        //and socket factory that implements TrustEquivalence.
        obj = getInstance3Arg.invoke(
            c, new Object[]{host2, new Integer(port),trustSf});
        if (!(obj instanceof TrustEquivalence)) {
            throw new TestException(
                obj + " does not implement TrustEquivalence");
        }
        endpoint1 = (TrustEquivalence) obj;
        obj = getInstance3Arg.invoke(
            c, new Object[]{host2, new Integer(port),trustSf});
        if (!(obj instanceof TrustEquivalence)) {
            throw new TestException(
                obj + " does not implement TrustEquivalence");
        }
        endpoint2 = (TrustEquivalence) obj;
        //Verify TrustEquivalence
        if (!endpoint1.checkTrustEquivalence(endpoint2) ||
            !endpoint2.checkTrustEquivalence(endpoint1)) {
            throw new TestException("TrustEquivalence"
                + " not established on equivalend endpoints with factories"
                + " that implement TrustEquivalence.");
        }
    }
}
