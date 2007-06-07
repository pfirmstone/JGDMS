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

import java.rmi.activation.ActivationMonitor;
import net.jini.security.AccessPermission;

/**
 * Represents permissions that can be used to express the access control
 * policy for the {@link ActivationMonitor} remote object exported with
 * {@link net.jini.jeri.BasicJeriExporter}. This class can be passed to
 * {@link net.jini.jeri.BasicInvocationDispatcher}, and then used in
 * security policy permission grants.
 *
 * <p>This permission class can also be used to grant permission to invoke
 * the method {@link java.rmi.activation.ActivationGroup#activeObject
 * ActivationGroup.activeObject}, {@link
 * java.rmi.activation.ActivationGroup#inactiveObject
 * ActivationGroup.inactiveObject }, or {@link
 * net.jini.activation.ActivationGroup#inactive ActivationGroup.inactive}.
 * 
 * <p>An instance contains a name (also referred to as a "target name") but
 * no actions list; you either have the named permission or you don't. The
 * convention is that the target name is the fully qualified name of the
 * remote method being invoked. Wildcard matches are supported using the
 * syntax specified by {@link AccessPermission}.
 * 
 * <p>The possible target names for the activation monitor are:
 * <table border=1 cellpadding=5>
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 * <tr>
 * <td>ActivationMonitor.activeObject</td>
 * <td>invoking {@link ActivationMonitor#activeObject
 * ActivationMonitor.activeObject}</td>
 * <td>The caller can cause an object to be treated as active by the
 * activation system and can inject the proxy for that object that will be
 * returned by the activator, if it knows the activation identifier.</td>
 * </tr>
 * <tr>
 * <td>ActivationMonitor.inactiveGroup</td>
 * <td>invoking {@link ActivationMonitor#inactiveGroup
 * ActivationMonitor.inactiveGroup}</td>
 * <td>The caller can cause an activation group to be treated as inactive by
 * the activation system if it knows the activation group identifier and
 * the incarnation.</td>
 * </tr>
 * <tr>
 * <td>ActivationMonitor.inactiveObject</td>
 * <td>invoking {@link ActivationMonitor#inactiveObject
 * ActivationMonitor.inactiveObject}</td>
 * <td>The caller can cause an active object to be treated as inactive by
 * the activation system if it knows the activation identifier.</td>
 * </tr>
 * </table>
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0 */
public final class MonitorPermission extends AccessPermission {
    private static final long serialVersionUID = 2475659022830374738L;

    /**
     * Creates an instance with the specified name.
     *
     * @param name the target name
     */
    public MonitorPermission(String name) {
	super(name);
    }
}
