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


/**
 * Class implementing TrustVerifier.Context interface having constructor with 1
 * parameter:  Object[]. Collection of those objects will be returned by
 * getCallerContext method. 'isTrustedObject' method of this class returns
 * false when it is invoked for the first N times (default 2) but returns
 * true always after that.
 */
public class TrustVerifierContext extends BaseTrustVerifierContext {

    /** Number of 'isTrustedObject' method calls. */
    private int num = 0;
    int limit;

    /**
     * Constructor saving array of context objects, false limit of 2.
     */
    public TrustVerifierContext(Object[] objs) {
        this(objs, 2);
    }

    /**
     * Constructor saving array of context objects.
     */
    public TrustVerifierContext(Object[] objs, int limit) {
        super(objs);
	this.limit = limit;
    }

    /**
     * Returns false when it is invoked for the first limit times
     * but returns true always after that.
     *
     * @return false when it is invoked for the first limit times
     *         and returns true always after that
     */
    public boolean isTrustedObject(Object obj) throws RemoteException {
        objList.add(obj);
        srcArray.add(this);

        if (num < limit) {
            ++num;
            return false;
        }
        return true;
    }

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object
     */
    public String toString() {
        return "TrustVerifierContext[" + super.toString() + "]";
    }
}
