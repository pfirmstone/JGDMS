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
 * org.apache.river.test.spec.jeri.transport.IllegalArgumentExceptionTest
 *
 * Purpose: The purpose of this test is test is to verify that a
 *     <code>ServerEndpoint</code> implementation throws
 *     <code>java.lang.IllegalArgumentException</code> if a
 *     <code>ListenContext</code> implementation returns a
 *     <code>ListenCookie</code> that does not correspond to the
 *     <code>ListenEndoint</code> passed in to
 *     <code>ListenContext.addListenEndpoint</code>.
 *
 * Use Case: Erroneously returning an invalid <code>ListenCookie</code>
 *     from <code>ListenContext.addListenEndpoint</code>
 *
 * Test Design:
 * 1. Obtain a <code>ServerEndpoint</code> implementation.
 * 2. Call <code>ServerEndpoint.enumerateListenEndpoint</code> passing
 *     in an insturmented <code>ListenContext</code> that
 *     stores <code>ListenCookie</code> instances.
 * 3. Obtain another instance of <code>ServerEndpoint</code>.
 * 4. Call <code>ServerEndpoint.enumerateListenEndpoint</code> passing
 *     in an instrumented <code>ListenContext</code> that returns the
 *     <code>ListenCookie</code> instances stored in step 2.
 * 5. Verify that the <code>ServerEndpoint</code> implementation throws
 *     <code>java.lang.IllegalArgumentException</code>.
 * 6. Verify that the <code>ServerEndpoint</code> implementation throws
 *    an <code>IllegalArgumentException</code> if the
 *    <code>ListenContext</code> returns a null cookie.
 *
 * Additional Utilities:
 * 1. Instrumented <code>ListenContext</code> class.
 *
 */
package org.apache.river.test.spec.jeri.transport;

import java.util.logging.Level;

//test harness related imports
import org.apache.river.qa.harness.TestException;

//jeri imports
import net.jini.jeri.ServerEndpoint;

//utility classes
import org.apache.river.test.spec.jeri.transport.util.AbstractEndpointTest;
import org.apache.river.test.spec.jeri.transport.util.EndpointHolder;
import org.apache.river.test.spec.jeri.transport.util.IllegalArgumentContext;
import org.apache.river.test.spec.jeri.transport.util.NullCookieContext;

//java.util
import java.util.ArrayList;
import java.util.Iterator;

public class IllegalArgumentExceptionTest extends AbstractEndpointTest {

    public void run() throws Exception {
        //Obtain a serverEndpoint instance
        ServerEndpoint se = getServerEndpoint();
        IllegalArgumentContext context = new IllegalArgumentContext(null);
        //Call enumerateListenEndpoints and extract cookies
        se.enumerateListenEndpoints(context);
        ArrayList cookies = context.getCookies();
        //Obtain a different server endpoint
        se = (ServerEndpoint) getConfigObject(ServerEndpoint.class,
            "diffEndpoint");
        boolean exceptionThrown = false;
        try {
            se.enumerateListenEndpoints(
                new IllegalArgumentContext(cookies));
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("The ServerEndpoint"
                + " implementation does not throw an"
                + " IllegalArgumentException when an invalid"
                + " ListenCookie is returned by the ListenContext");
        }
        //Close listen operation
        ArrayList endpoints = context.getEndpoints();
        Iterator it = endpoints.iterator();
        while (it.hasNext()) {
            ((EndpointHolder) it.next()).getListenHandle().close();
        }
        // wait to make sure resources are released before attempting
        // to reuse endpoint
        Thread.currentThread().sleep(1000 * 30); 
        exceptionThrown = false;
        se = getServerEndpoint();
        try {
            se.enumerateListenEndpoints(new NullCookieContext());
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        if (!exceptionThrown) {
            throw new TestException("The ServerEndpoint"
                + " implementation does not throw an"
                + " IllegalArgumentException when an invalid"
                + " ListenCookie is returned by the ListenContext");
        }

    }

}
