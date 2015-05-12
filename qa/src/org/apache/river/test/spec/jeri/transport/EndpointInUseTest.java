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
 * org.apache.river.test.spec.jeri.serverendpoint.EndpointInUseTest
 *
 * Purpose:  Verify that <code>java.io.IOException</code> is thrown in
 *     <code>ServerEndpoint.enumerateListenEndpoints</code> if a listen
 *     operation is attempted on a communication endpoint that is already under
 *     exclusive use.  Verify that
 *     <code>net.jini.jeri.ServerEndpoint.ListenEndpoint.listen</code> throws
 *     <code>java.io.IOException</code> if a listen operation is attempted on
 *     a communication endpoint that is already under exclusive use.
 *
 * Use Case: Attempting to listen on a communication endpoint that is being used
 *     exclusively.
 *
 * Test Design:
 *
 * 1. Obtain an instance of <code>net.jini.jeri.ServerEndpoint</code> that
 *    listens on a specific port (does not use an anonymous port).
 * 2. Call <code>enumerateListenEndpoints</code> on the communiction endpoint
 *     obtained in step 1 above passing in a test
 *     <code>net.jini.jeri.ServerEndpoint.ListenContext</code> implementation.
 * 3. Verify that <code>ServerEndpoint.enumerateListenEndpoints</code> returns
 *     successfully.
 * 4. Call <code>enumerateListenEndpoints</code> on the
 *     <code>ServerEndpoint</code> obtained in step 1 passing in a test
 *     <code>ServerEndpoint.ListenContext</code>
 * 5. Verify that calling <code>listen</code> on the
 *     <code>ServerEndpoint.ListenEndpoint</code> passed in to
 *     <code>ListenContext.addListenEndoint</code> throws
 *     <code>java.io.IOException</code>
 * 6. Verify that <code>enumerateListenEndpoints</code> throws
 *     <code>java.io.IOException</code>
 *
 *
 * Additional Utility Classes:
 *
 * 1. Test net.jini.jeri.ServerEndpoint.ListenContext implementation.
 */
package org.apache.river.test.spec.jeri.transport;

import java.util.logging.Level;

//jeri imports
import net.jini.jeri.ServerEndpoint;

//harness imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.transport.util.AbstractEndpointTest;
import org.apache.river.test.spec.jeri.transport.util.EIUTContext;
import org.apache.river.test.spec.jeri.transport.util.SETContext;

//java.io
import java.io.IOException;

//java.util
import java.util.Collection;
import java.util.Iterator;

public class EndpointInUseTest extends AbstractEndpointTest {

    public void run() throws Exception {
        //Obtain a server endpoint
        ServerEndpoint se = getServerEndpoint();
        //Call enumerateListenEndpoints to start a listen operation
        //on the endpoint
        se.enumerateListenEndpoints(new SETContext());
        //Call enumerateListenEndpoints while the endpoint is in use
        //and verify that an IOException is thrown
        EIUTContext context = new EIUTContext();
        try {
            se.enumerateListenEndpoints(context);
        } catch (IOException e) {
            //Verify that IOException was thrown in listen calls to
            //ListenEndpoints
            Iterator it = context.getResults().iterator();
            while (it.hasNext()) {
                Boolean b = (Boolean) it.next();
                if (!b.booleanValue()){
                    throw new TestException("Call to Listen"
                        + " Endpoint.listen did not throw an IOException"
                        + " when the endpoint was in use");
                }
            }
            return;
        }
        throw new TestException("Attempting to use an endpoint"
            + " already in use did not throw an IOException");
    }
}
