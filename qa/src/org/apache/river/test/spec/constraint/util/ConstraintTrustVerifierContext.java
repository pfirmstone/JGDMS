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

// net.jini
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.ServerAuthentication;


/**
 * Class implementing TrustVerifier.Context interface whose 'isTrustedObject'
 * method returns true if parameter is an instance of ClientAuthentication,
 * Delegation, Integrity or ServerAuthentication and false otherwise.
 */
public class ConstraintTrustVerifierContext extends BaseTrustVerifierContext {

    /**
     * Returns true if parameter is an instance of ClientAuthentication,
     * Delegation, Integrity or ServerAuthentication and false otherwise.
     *
     * @return true if parameter is an instance of ClientAuthentication,
     *         Delegation, Integrity or ServerAuthentication and false otherwise
     */
    public boolean isTrustedObject(Object obj) throws RemoteException {
        return ((obj instanceof ClientAuthentication)
                || (obj instanceof Delegation)
                || (obj instanceof Integrity)
                || (obj instanceof ServerAuthentication));
    }
}
