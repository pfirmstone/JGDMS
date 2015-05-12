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
 * org.apache.river.test.spec.jeri.transport.ListenContextExceptionTest
 *
 * Purpose: Verify that an arbirtrary exception thrown by
 *     a <code>ServerEndpoint.ListenContext</code> implementation is
 *     propagated by <code>enumerateListenEndpoints</code>.
 *
 * Use Case: Encountering an arbitrary exception thrown in a
 * <code>ServerEndpoint.ListenContext</code> implementation.
 *
 * Test Design:
 *
 * 1. Obtain an instance of <code>net.jini.jeri.ServerEndpoint</code>.
 * 2. Call <code>enumerateListenEndpoints</code> on the communiction endpoint
 *     obtained in step 1 above passing in a test
 *     <code>net.jini.jeri.ServerEndpoint.ListenContext</code> implementation.
 * 3. Inside the <code>ListenContext</code> implementation throw an arbitrary
 *     instance of <code>java.lang.RuntimeException</code>
 * 4. Verify that <code>enumerateListenEndpoints</code> throws the
 *     same exception thrown by the <code>ListenContext</code> in step 3 above.
 * 5. Obtain an instance of <code>net.jini.jeri.ServerEndpoint</code>.
 * 6. Call <code>enumerateListenEndpoints</code> on the communiction endpoint
 *     obtained in step 1 above passing in a test
 *     <code>net.jini.jeri.ServerEndpoint.ListenContext</code> implementation.
 * 7. Inside the <code>ListenContext</code> implementation throw an arbitrary
 *     instance of <code>java.io.IOException</code>.
 * 8. Verify that <code>enumerateListenEndpoints</code> throws the
 *     same exception thrown by the <code>ListenContext</code> in step 7 above.
 *
 * Additional Utility Classes:
 *
 * 1. Test net.jini.jeri.ServerEndpoint.ListenContext implementation
 * 2. Test exception class
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
import org.apache.river.test.spec.jeri.transport.util.ArbitraryException;
import org.apache.river.test.spec.jeri.transport.util.ExceptionListenContext;

//java.io
import java.io.IOException;

public class ListenContextExceptionTest extends AbstractEndpointTest {
    public void run() throws Exception {
        //Obtain a server endpoint instance
        ServerEndpoint endpoint = getServerEndpoint();
        try {
            //Verify that an arbitrary exception is propagated
            endpoint.enumerateListenEndpoints(
                new ExceptionListenContext(new ArbitraryException()));
        } catch (ArbitraryException a) {
            try {
                //Verify that an IOException is propagated
                endpoint.enumerateListenEndpoints(
                    new ExceptionListenContext(new IOException()));
            } catch (IOException e) {
                return;
            }
        }
        throw new TestException("An arbitrary exception in the"
            + " ListenContext is not propagated by the ServerEndpoint"
            + ".enumerateListenEndpoints method");
    }

}
