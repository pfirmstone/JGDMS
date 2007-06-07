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

package com.sun.jini.phoenix;

import net.jini.security.AccessPermission;

/**
 * Represents permissions that can be used to express the access control
 * policy for the activator, the remote object handling object activation,
 * if that remote object is exported with {@link
 * net.jini.jeri.BasicJeriExporter}. This class can be passed to {@link
 * net.jini.jeri.BasicInvocationDispatcher}, and then used in security
 * policy permission grants.
 *
 * <p>This permission class can be used for server-side access control of
 * remote object activation initiated by the client-side {@link
 * java.rmi.activation.ActivationID#activate ActivationID.activate} method.
 * The server-side method name for this operation is <code>activate</code>.
 * 
 * <p>An instance contains a name (also referred to as a "target name") but no
 * actions list; you either have the named permission or you don't. The
 * convention is that the target name is the fully qualified name of the
 * remote method being invoked. Wildcard matches are supported using the
 * syntax specified by {@link AccessPermission}.
 * 
 * <p>The possible target names for the activator are:
 * <table border=1 cellpadding=5>
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 * <tr>
 * <td>activate</td>
 * <td>invoking the activator's <code>activate</code> method</td>
 * <td>The caller can activate an object and obtain the proxy for it if it
 * knows the activation identifier.</td>
 * </tr>
 * <tr>
 * <td>net.jini.security.proxytrust.ProxyTrust.getProxyVerifier</td>
 * <td>invoking
 * {@link net.jini.security.proxytrust.ProxyTrust#getProxyVerifier
 * ProxyTrust.getProxyVerifier}</td>
 * <td>The caller can verify trust in activation identifiers.</td>
 * </tr>
 * </table>
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public final class ActivatorPermission extends AccessPermission {
    private static final long serialVersionUID = 1133348041678833146L;

    /**
     * Creates an instance with the specified name.
     *
     * @param name the target name
     */
    public ActivatorPermission(String name) {
	super(name);
    }
}
