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
package org.apache.river.test.spec.jeri.transport;

import java.util.logging.Level;

//jeri imports
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
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

public class RobustnessTest extends AbstractEndpointTest {

    public void run() throws Exception {
        ServerEndpoint se = getServerEndpoint();
        GetDeliveryDispatcher dispatcher = new GetDeliveryDispatcher();
        GetDeliveryContext lc = new GetDeliveryContext(dispatcher);
        Endpoint e =
            se.enumerateListenEndpoints(lc);
        dispatcher.reject();
        try {
            OutboundRequestIterator ori =
                e.newRequest(InvocationConstraints.EMPTY);
            while (ori.hasNext()){
                OutboundRequest or = ori.next();
                ObjectOutputStream oos = new ObjectOutputStream(
                    or.getRequestOutputStream());
                oos.writeInt(1);
                oos.close();
            }
        } catch (Exception e2) {
            //Expected exception
            log.finest("Expected exception " + e2.getMessage());
        }
        if (dispatcher.dispatchCalled()!=-1){
            throw new TestException("Exception from the "
                + "dispatcher was not propagated");
        }
        dispatcher.accept();
        try {
            OutboundRequestIterator ori =
                e.newRequest(InvocationConstraints.EMPTY);
            while (ori.hasNext()) {
               OutboundRequest or = ori.next();
               ObjectOutputStream oos = new ObjectOutputStream(
               or.getRequestOutputStream());
               oos.writeInt(2);
               oos.close();
            }
        } catch (IOException ioe) {
            throw new TestException("Endpoint unavailable after"
                + " exception in previous remote call");
        }
        if (dispatcher.dispatchCalled()!=2){
            throw new TestException("Disaptcher was not called"
                + " after failure on previous remote call");
        }
    }

}
