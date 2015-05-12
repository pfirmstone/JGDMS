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
package org.apache.river.test.spec.jeri.util;

import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.core.constraint.InvocationConstraints;

import java.util.logging.Logger;

/**
 * A fake implementation of the <code>Endpoint</code>
 * interface.
 */
public class FakeEndpoint implements Endpoint {

    Logger logger;
    private OutboundRequestIterator iterator;

    /**
     * Constructs a FakeEndpoint.
     *
     * @param ori the return value for the <code>newRequest</code> method
     *        or null if <code>newRequest</code> should throw AssertionError
     */
    public FakeEndpoint(OutboundRequestIterator ori) {
        logger = Logger.getLogger("org.apache.river.qa.harness.test");
        logger.entering(getClass().getName(),
            "constructor(outboundRequestIterator:" + ori + ")");
        iterator = ori;
    }

    /**
     * Implementation of interface method.
     * 
     * @return ori if not null, otherwise throw an AssertionError
     */
    public OutboundRequestIterator newRequest(InvocationConstraints c) {
        logger.entering(getClass().getName(),"newRequest");
        if (c == null) {
            throw new NullPointerException();
        } else if (iterator == null) {
            throw new AssertionError();
        } else {
            return iterator;
        }
    }
}

