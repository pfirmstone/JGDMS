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
 * policy for the {@link java.rmi.activation.ActivationSystem} remote
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
 * The possible target names are:
 * <table border=1 cellpadding=5>
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.activeGroup</td>
 * <td>invoking {@link java.rmi.activation.ActivationSystem#activeGroup
 * ActivationSystem.activeGroup}</td>
 * <td>The caller can inject itself as the instantiation of a group if the
 * group is currently being activated.</td>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.getActivationDesc</td>
 * <td>invoking {@link java.rmi.activation.ActivationSystem#getActivationDesc
 * ActivationSystem.getActivationDesc}</td>
 * <td>The caller can obtain the descriptor for an existing activatable object
 * if it knows the activation identifier.</td>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.getActivationGroupDesc</td>
 * <td>invoking
 * {@link java.rmi.activation.ActivationSystem#getActivationGroupDesc
 * ActivationSystem.getActivationGroupDesc}</td>
 * <td>The caller can obtain the descriptor for an existing activation group
 * if it knows the activation group identifier.</td>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.registerGroup</td>
 * <td>invoking {@link java.rmi.activation.ActivationSystem#registerGroup
 * ActivationSystem.registerGroup}</td>
 * <td>The caller can register new activation groups.</td>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.registerObject</td>
 * <td>invoking {@link java.rmi.activation.ActivationSystem#registerObject
 * ActivationSystem.registerObject}</td>
 * <td>The caller can register new activatable objects.</td>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.setActivationDesc</td>
 * <td>invoking {@link java.rmi.activation.ActivationSystem#setActivationDesc
 * ActivationSystem.setActivationDesc}</td>
 * <td>The caller can replace the descriptor for an existing activatable
 * object if it knows the activation identifier.</td>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.setActivationGroupDesc</td>
 * <td>invoking
 * {@link java.rmi.activation.ActivationSystem#setActivationGroupDesc
 * ActivationSystem.setActivationGroupDesc}</td>
 * <td>The caller can replace the descriptor for an existing activation group
 * if it knows the activation group identifier.</td>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.shutdown</td>
 * <td>invoking {@link java.rmi.activation.ActivationSystem#shutdown
 * ActivationSystem.shutdown}</td>
 * <td>The caller can shut down the activation system.</td>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.unregisterObject</td>
 * <td>invoking {@link java.rmi.activation.ActivationSystem#unregisterObject
 * ActivationSystem.unregisterObject}</td>
 * <td>The caller can unregister existing activatable objects if it knows
 * their activation identifiers.</td>
 * </tr>
 * <tr>
 * <td>java.rmi.activation.ActivationSystem.unregisterGroup</td>
 * <td>invoking {@link java.rmi.activation.ActivationSystem#unregisterGroup
 * ActivationSystem.unregisterGroup}</td>
 * <td>The caller can unregister existing activation groups if it knows
 * their activation group identifiers.</td>
 * </tr>
 * <tr>
 * <td>com.sun.jini.phoenix.ActivationAdmin.getActivationGroups</td>
 * <td>invoking {@link com.sun.jini.phoenix.ActivationAdmin#getActivationGroups
 * ActivationAdmin.getActivationGroups}</td>
 * <td>The caller can obtain the activation group identifiers and
 * descriptors for all registered activation groups</td>
 * </tr>
 * <tr>
 * <td>com.sun.jini.phoenix.ActivationAdmin.getActivatableObjects</td>
 * <td>invoking
 * {@link com.sun.jini.phoenix.ActivationAdmin#getActivatableObjects
 * ActivationAdmin.getActivatableObjects}</td>
 * <td>The caller can obtain the activation identifiers and
 * descriptors for all registered activatable objects in an activation group
 * if it knows the activation group identifier</td>
 * </tr>
 * </table>
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public final class SystemPermission extends AccessPermission {
    private static final long serialVersionUID = -3058499612160420636L;

    /**
     * Creates an instance with the specified name.
     *
     * @param name the target name
     */
    public SystemPermission(String name) {
	super(name);
    }
}
