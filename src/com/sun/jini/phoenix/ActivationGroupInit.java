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

import java.lang.reflect.Method;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.rmi.server.RMIClassLoader;
import java.util.Collections;
import net.jini.io.MarshalInputStream;

/**
 * This is the bootstrap code to start a virtual machine (VM) executing an
 * activation group.
 *
 * The activator spawns (as a child process) an activation group as needed
 * and directs activation requests to the appropriate activation
 * group. After spawning the VM, the activator passes some
 * information to the bootstrap code via its stdin: <p>
 * <ul>
 * <li> the activation group's id, 
 * <li> the activation group's descriptor (an instance of the class
 *    java.rmi.activation.ActivationGroupDesc) for the group, and
 * <li> the group's incarnation number.
 * </ul><p>
 *
 * When the bootstrap VM starts executing, it reads group id and
 * descriptor from its stdin so that it can create the activation
 * group for the VM.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
class ActivationGroupInit {
    private ActivationGroupInit() {}

    /**
     * Main program to start a VM for an activation group.
     */
    public static void main(String args[])
    {
	try {
	    if (System.getSecurityManager() == null) {
		System.setSecurityManager(new SecurityManager());
	    }
	    MarshalInputStream in =
		new MarshalInputStream(
				   System.in,
				   ActivationGroupInit.class.getClassLoader(),
				   false, null, Collections.EMPTY_LIST);
	    in.useCodebaseAnnotations();
	    ActivationGroupID id  = (ActivationGroupID)in.readObject();
	    ActivationGroupDesc desc = (ActivationGroupDesc)in.readObject();
	    long incarnation = in.readLong();
	    Class cl = RMIClassLoader.loadClass(desc.getLocation(),
						desc.getClassName());
	    try {
		Method create =
		    cl.getMethod("createGroup",
				 new Class[]{ActivationGroupID.class,
					     ActivationGroupDesc.class,
					     long.class});
		create.invoke(null, new Object[]{id, desc,
						 new Long(incarnation)});
	    } catch (NoSuchMethodException e) {
		ActivationGroup.createGroup(id, desc, incarnation);
	    }
	} catch (Exception e) {
	    System.err.println("Exception in starting ActivationGroupInit:");
	    e.printStackTrace();
	} finally {
	    try {
		System.in.close();
		// note: system out/err shouldn't be closed
		// since the parent may want to read them.
	    } catch (Exception ex) {
		// ignore exceptions
	    }
	}
    }
}
