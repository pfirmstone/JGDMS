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
package org.apache.river.test.spec.jeri.connection.util;

//jeri imports
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.InboundRequest;

/**
 * Instrumented dispatcher
 */
public class TestDispatcher implements RequestDispatcher {

    private RequestDispatcher delegate;

    public TestDispatcher(RequestDispatcher dispatcher) {
        delegate = dispatcher;
    }

    public void dispatch(InboundRequest request) {
        ConnectionTransportListener.getListener().called("dispatch");
        delegate.dispatch(request);
    }

}
