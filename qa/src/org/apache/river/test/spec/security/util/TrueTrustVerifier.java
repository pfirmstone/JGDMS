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
package org.apache.river.test.spec.security.util;

// java.rmi
import java.rmi.RemoteException;

// net.jini
import net.jini.security.TrustVerifier;


/**
 * Class implementing TrustVerifier interface whose 'isTrustedObject' method
 * always returns true.
 */
public class TrueTrustVerifier extends BaseTrustVerifier {

    /**
     * Always returns true.
     *
     * @return true
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
            throws RemoteException {
        classes.add(getClass());
        objs.add(obj);
        ctxs.add(ctx);
        return true;
    }
}
