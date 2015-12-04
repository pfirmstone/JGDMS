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

package net.jini.security;

import java.rmi.RemoteException;
import net.jini.config.Configuration;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.Security;

/**
 * Performs operations on a newly unmarshalled remote proxy to prepare it for
 * use. Typical operations include verifying trust in the proxy by calling
 * {@link Security#verifyObjectTrust Security.verifyObjectTrust}, specifying
 * constraints by calling {@link RemoteMethodControl#setConstraints
 * RemoteMethodControl.setConstraints}, and granting the proxy permissions by
 * calling {@link Security#grant Security.grant}. <p>
 *
 * Applications are expected to use instances of this class retrieved from a
 * {@link Configuration} to prepare all remote proxies that they receive in
 * order to permit configuring how applications handle proxies.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface ProxyPreparer {

    /**
     * Performs operations on a newly unmarshalled proxy to prepare it for use,
     * returning the prepared proxy, which may or may not be the argument
     * itself. <p>
     *
     * Typical operations performed by this method include verifying trust in
     * the proxy by calling {@link Security#verifyObjectTrust
     * Security.verifyObjectTrust}, specifying constraints by calling {@link
     * RemoteMethodControl#setConstraints RemoteMethodControl.setConstraints},
     * and granting the proxy permissions by calling {@link Security#grant
     * Security.grant}.
     *
     * @param proxy the proxy to prepare
     * @return the prepared proxy
     * @throws NullPointerException if the proxy is <code>null</code>
     * @throws RemoteException if a communication-related exception occurs
     * @throws SecurityException if a security exception occurs
     */
    Object prepareProxy(Object proxy) throws RemoteException;
}
