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
package org.apache.river.test.spec.jeri.basicjeriexporter.util;

//overture classes
import net.jini.jeri.Endpoint;
import net.jini.jeri.ServerEndpoint;
import net.jini.io.UnsupportedConstraintException;
import net.jini.core.constraint.InvocationConstraints;

//java.io
import java.io.IOException;

//java.lang.reflect
import java.lang.reflect.Method;

/**
 * This class implements an instrumented ServerEnpoint that
 * delegates to an underlying ServerEnpoint
 */
public class BJETestServerEndpoint implements ServerEndpoint {

    private ServerEndpoint endpoint;

    public BJETestServerEndpoint(ServerEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public Endpoint
        enumerateListenEndpoints(ServerEndpoint.ListenContext listenContext)
            throws java.io.IOException {
        try {
            Method m = this.getClass().getMethod("enumerateListenEndpoints",
                new Class[] {ServerEndpoint.ListenContext.class});
            BJETransportListener.getListener()
                .called(m,this,new Object[] {listenContext});
        } catch (NoSuchMethodException e){
            BJETransportListener.getUtilLog().warning("Something is"
                +" really wrong - a method from this class is not"
                + "found in this class: " + e.getMessage());
            e.printStackTrace();
        }
        BJETestListenContext context = new BJETestListenContext(listenContext);
        return new BJETestEndpoint(endpoint.enumerateListenEndpoints(context));
    }

    public InvocationConstraints checkConstraints(InvocationConstraints ic)
        throws UnsupportedConstraintException {
        return endpoint.checkConstraints(ic);
    }
}
