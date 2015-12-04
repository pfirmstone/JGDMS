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

import net.jini.security.TrustVerifier;

import java.io.Serializable;
import java.rmi.RemoteException;

public class FakeTrustVerifier implements TrustVerifier, Serializable {

    /**
     * No-op implementation of interface method.
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
        throws RemoteException
    {
        return false;
    }

    /**
     * <code>object</code> is equal to this object if it is not null
     * and is an instance of <code>FakeTrustVerifier</code>.
     */
    public boolean equals(Object object) {
        if (object == null || !(object instanceof FakeTrustVerifier)) {
            return false;
        }
        return true;
    }

}
