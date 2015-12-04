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
 * org.apache.river.test.spec.jeri.serverEndpoint.EqualityTest
 *
 * Purpose: The purpose of this test is to verify that two functionally
 *     equivalent endoints identify each other as equal.
 *
 * Use Case: Comparing <code>ListenEdpoints</code> instances.
 *
 * Test Design:
 * 1. Obtain two functionally equivalent <code>ServerEndpoint</code> instances.
 * 2. Call the <code>equals</code> method on the first instance passing in the
 *    second instance as an argument.
 * 3. Verify that the <code>equals</code> method returns true and that the
 *    <code>ListenEndpoints</code> are also equal.
 * 4. Call the <code>equals</code> method on the second instance passing in the
 *    first instance as an argument.
 * 5. Verify that the <code>equals</code> method returns true and that the
 *    <code>ListenEndpoints</code> are also equal.
 * 6. Verify that the hashcodes on equal endoints are also equal.
 * 7. Obtain two functionally different <code>ServerEndpoint</code> instances.
 * 8. Call the <code>equals</code> method on the first instance passing in the
 *    second instance as an argument.
 * 9. Verify that the <code>equals</code> method returns false and that the
 *    <code>ListenEndpoints</code> are not equal.
 * 10. Call the <code>equals</code> method on the second instance passing in the
 *    first instance as an argument.
 * 11. Veriy that the <code>equals</code> method returns false and that the
 *    <code>ListenEndpoints</code> are not equal.
 */
package org.apache.river.test.spec.jeri.transport;

import java.util.logging.Level;


//jeri imports
import net.jini.jeri.ServerEndpoint;

//harness imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.transport.util.AbstractEndpointTest;
import org.apache.river.test.spec.jeri.transport.util.EndpointHolder;
import org.apache.river.test.spec.jeri.transport.util.EqualityContext;

//java.util
import java.util.ArrayList;
import java.util.Iterator;

public class EqualityTest extends AbstractEndpointTest {

    public void run() throws Exception {
        //Verify that functionally equivalent ServerEndpoint instances
        //are considered equal.
        ServerEndpoint se = getServerEndpoint();
        ServerEndpoint se2 = (ServerEndpoint)
            getConfigObject(ServerEndpoint.class,"equalEndpoint");
        if (!se.equals(se2)) {
            throw new TestException("Functionally equivalent"
                + " endpoints are not considered equal");
        }
        if (!se2.equals(se)){
             throw new TestException("Functionally equivalent"
                + " endpoints are not considered equal");
        }

        //Verify hashcodes
        if (se.hashCode()!=se2.hashCode()) {
            throw new TestException("Equal endpoints do not"
               + " return equal hashcodes");
        }

        //Verify that ListenEndpoint on functionally equivalent
        //ServerEndpoint instances are considered equal.
        EqualityContext lc = new EqualityContext();
        EqualityContext lc2 = new EqualityContext();
        se.enumerateListenEndpoints(lc);
        ArrayList endpoints = lc.getEndpoints();
        //close the listen operations to avoid problems when obtaining
        //the listen endpoints for the functionally equivalent endpoint
        Iterator it = endpoints.iterator();
        while (it.hasNext()){
            ((EndpointHolder)it.next()).getListenHandle().close();
        }
        // wait to make sure resources released before attempting
        // to reuse socket
        Thread.currentThread().sleep(1000 * 30); 
        se2.enumerateListenEndpoints(lc2);
        ArrayList endpoints2 = lc2.getEndpoints();
        for (int i=0; i<endpoints.size(); i++){
            EndpointHolder eph = (EndpointHolder) endpoints.get(i);
            EndpointHolder eph2 = (EndpointHolder) endpoints2.get(i);
            ServerEndpoint.ListenEndpoint le = eph.getListenEndpoint();
            ServerEndpoint.ListenEndpoint le2 = eph2.getListenEndpoint();
            if (!le.equals(le2)){
                throw new TestException("ListenEndpoint " + le
                    + " on ServerEndpoint " + se + " is not considered"
                    + " equal to ListenEndpoint " + le2 + " on functionally"
                    + " equivalent ServerEndpoint " + se2);
            }
            if (!le2.equals(le)){
                throw new TestException("ListenEndpoint " + le
                    + " on ServerEndpoint " + se + " is not considered"
                    + " equal to ListenEndpoint " + le2 + " on functionally"
                    + " equivalent ServerEndpoint " + se2);
            }
        }
        //Verify that endpoints that are not functionally equivalent
        //are not considered equal
        se2 = (ServerEndpoint) getConfigObject(
            ServerEndpoint.class,"diffEndpoint");
        if (se.equals(se2)) {
            throw new TestException("Functionally different"
                + " endpoints are considered equal");
        }
        if (se2.equals(se)){
             throw new TestException("Functionally different"
                + " endpoints are considered equal");
        }
        //Verify that ListenEndpoint on functionally equivalent
        //ServerEndpoint instances are considered equal.
        lc2 = new EqualityContext();
        se2.enumerateListenEndpoints(lc2);
        endpoints2 = lc2.getEndpoints();
        for (int i=0; i<endpoints.size(); i++){
            EndpointHolder eph = (EndpointHolder) endpoints.get(i);
            EndpointHolder eph2 = (EndpointHolder) endpoints2.get(i);
            ServerEndpoint.ListenEndpoint le = eph.getListenEndpoint();
            ServerEndpoint.ListenEndpoint le2 = eph2.getListenEndpoint();
            if (le.equals(le2)){
                throw new TestException("ListenEndpoint " + le
                    + " on ServerEndpoint " + se + " not considered"
                    + " equal to ListenEndpoint " + le2 + " on functionally"
                    + " different ServerEndpoint " + se2);
            }
            if (le2.equals(le)){
                throw new TestException("ListenEndpoint " + le
                    + " on ServerEndpoint " + se + " is considered"
                    + " equal to ListenEndpoint " + le2 + " on functionally"
                    + " different ServerEndpoint " + se2);
            }
        }

    }

}
