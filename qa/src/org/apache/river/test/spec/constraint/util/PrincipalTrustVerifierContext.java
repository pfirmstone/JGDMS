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
package org.apache.river.test.spec.constraint.util;

import java.util.logging.Level;

// java
import java.rmi.RemoteException;


/**
 * Class implementing TrustVerifier.Context whose 'isTrustedObject' method
 * returns true if parameter is an instance of TestPrincipal and it's
 * 'isTrusted' method returns true.
 */
public class PrincipalTrustVerifierContext extends BaseTrustVerifierContext {

    /**
     * Returns true if parameter is an instance of TestPrincipal and it's
     * 'isTrusted' method returns true and false otherwise.
     *
     * @return true if parameter is an instance of TestPrincipal and it's
     *         'isTrusted' method returns true and false otherwise
     */
    public boolean isTrustedObject(Object obj) throws RemoteException {
        return ((obj instanceof TestPrincipal)
                ? ((TestPrincipal) obj).isTrusted() : false);
    }
}
