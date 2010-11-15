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

import java.rmi.activation.ActivationGroupDesc;
import java.security.AccessControlException;
import java.security.Permission;
import java.util.Enumeration;
import java.util.Properties;
import net.jini.jeri.BasicInvocationDispatcher;

/**
 * Group policy that requires the appropriate {@link ExecPermission} and
 * set of {@link ExecOptionPermission} have been granted to the client
 * subject or the empty protection domain (a domain with all
 * <code>null</code> elements) if there is no client subject.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 **/
public class DefaultGroupPolicy implements GroupPolicy {

    /**
     * Checks permissions for the specified group descriptor. If the group
     * class name in the descriptor is not <code>null</code> and is not
     * {@link ActivationGroupImpl}, or if the class location or the
     * initialization data in the descriptor is not <code>null</code>, an
     * <code>AccessControlException</code> is thrown. For each property in
     * the descriptor's property overrides, {@link #checkPermission
     * checkPermission} is called with an {@link ExecOptionPermission}
     * constructed with a target of the form "-D<i>name</i>=<i>value</i>",
     * where <i>name</i> is the name of the property and <i>value</i> is the
     * value of the property. If the command environment in the descriptor is
     * not <code>null</code>, then additional checks are made as follows. If
     * the command path is not <code>null</code>, <code>checkPermission</code>
     * is called with an {@link ExecPermission} constructed with the path as
     * a target. For each command option, <code>checkPermission</code> is
     * called with an <code>ExecOptionPermission</code> constructed with the
     * option as a target.
     *
     * @param desc the activation group descriptor
     * @throws AccessControlException if permission is not granted to create
     * the specified group
     */
    public void checkGroup(ActivationGroupDesc desc) {
	String groupClassName = desc.getClassName();
	if ((groupClassName != null &&
	     !groupClassName.equals(
			       "com.sun.jini.phoenix.ActivationGroupImpl")) ||
	    desc.getLocation() != null ||
	    desc.getData() != null)
	{
	    throw new AccessControlException(
		"access denied (custom group implementation not allowed)");
	}

	Properties props = desc.getPropertyOverrides();
	if (props != null) {
	    Enumeration p = props.propertyNames();
	    while (p.hasMoreElements()) {
		String name = (String) p.nextElement();
		String option = ("\"-D" + name + "=" +
				 props.getProperty(name) + "\"");
		checkPermission(new ExecOptionPermission(option));
	    }
	}

	ActivationGroupDesc.CommandEnvironment cmdenv;
	cmdenv = desc.getCommandEnvironment();
	if (cmdenv != null) {
	    String path = cmdenv.getCommandPath();
	    if (path != null) {
		checkPermission(new ExecPermission(path));
	    }

	    String[] options = cmdenv.getCommandOptions();
	    if (options != null) {
		for (int i = 0; i < options.length; i++) {
		    checkPermission(new ExecOptionPermission(
						   "\"" + options[i] + "\""));
		}
	    }
	}
    }

    /**
     * Calls {@link BasicInvocationDispatcher#checkClientPermission
     * BasicInvocationDispatcher.checkClientPermission} with the
     * specified permission.
     *
     * @param p the permission being checked
     * @throws AccessControlException if permission is not granted
     */
    protected void checkPermission(Permission p) {
	BasicInvocationDispatcher.checkClientPermission(p);
    }
}
