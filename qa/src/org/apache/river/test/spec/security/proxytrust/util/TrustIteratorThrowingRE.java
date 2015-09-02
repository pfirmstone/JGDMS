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
package org.apache.river.test.spec.security.proxytrust.util;

import java.util.logging.Level;

// java
import java.rmi.RemoteException;
import java.util.NoSuchElementException;


/**
 * Class implementing ProxyTrustIterator interface whose 'next' method always
 * throws RemoteException.
 */
public class TrustIteratorThrowingRE extends TestTrustIterator
        implements ThrowingRE {

    /** RemoteException which will be thrown during 'next' call */
    protected RemoteException re;

    /**
     * Constructs iterator from given array.
     *
     * @param objs array of objects
     */
    public TrustIteratorThrowingRE(Object[] objs) {
        super(objs);
        re = new RemoteException();
    }

    /**
     * Always throws RemoteException
     *
     * @throws RemoteException always
     */
    public Object next() throws RemoteException {
        // increase index to avoid infinite loops
        ++curIdx;
        srcArray.add(this);
        throw re;
    }

    /**
     * Returns name of checked method.
     *
     * @return 'checked method' name
     */
    public String getMethodName() {
        return "next";
    }

    /**
     * Returns exception which was thrown during 'next' call.
     *
     * @return exception which was thrown during 'next' call
     */
    public RemoteException getException() {
        return re;
    }
}
