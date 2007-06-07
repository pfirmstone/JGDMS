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
 * policy for the {@link java.rmi.activation.ActivationInstantiator} remote
 * object exported with
 * {@link net.jini.jeri.BasicJeriExporter}. This
 * class can be passed to
 * {@link net.jini.jeri.BasicInvocationDispatcher},
 * and then used in security policy permission grants.
 * <p>
 * An instance contains a name (also referred to as a "target name") but no
 * actions list; you either have the named permission or you don't. The
 * convention is that the target name is the fully qualified name of the
 * remote method being invoked. Wildcard matches are supported using the
 * syntax specified by {@link AccessPermission}.
 * <p>
 * The possible target names for the activation instantiator are:
 * <table border=1 cellpadding=5>
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationInstantiator.newInstance</td>
 * <td>invoking {@link java.rmi.activation.ActivationInstantiator#newInstance
 * ActivationInstantiator.newInstance}</td>
 * <td>The caller can inject a new object into the activation group if it
 * knows the activation group identifier.</td>
 * </tr>
 * </table>
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public final class InstantiatorPermission extends AccessPermission {
    private static final long serialVersionUID = 6532912902541268520L;

    /**
     * Creates an instance with the specified name.
     *
     * @param name the target name
     */
    public InstantiatorPermission(String name) {
	super(name);
    }
}
