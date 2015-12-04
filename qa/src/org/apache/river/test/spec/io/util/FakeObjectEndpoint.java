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
package org.apache.river.test.spec.io.util;

import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.core.constraint.InvocationConstraints;

import java.rmi.RemoteException;
import java.io.IOException;
import java.io.Serializable;

/**
 * A fake implementation of the <code>ObjectEndpoint</code>
 * interface.
 */
public class FakeObjectEndpoint implements ObjectEndpoint,Serializable {

    public FakeObjectEndpoint() {
    }

    /**
     * Implementation of interface method.
     */
    public RemoteException executeCall(OutboundRequest call)
        throws IOException
    {
        throw new AssertionError();
    }

    /**
     * Implementation of interface method.
     */
    public OutboundRequestIterator newCall(InvocationConstraints constraints) {
        throw new AssertionError();
    }

    /**
     * Overloads <code>Object.equals</code>.
     *
     * @return true if obj is instanceof FakeObjectEndpoint
     */
    public boolean equals(Object obj) {
        return obj instanceof FakeObjectEndpoint;
    }

    /**
     * Overloads <code>Object.hashCode</code>.
     *
     * @return the value 13
     */
    public int hashCode() {
        return 13;
    }
}
