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
/**
 * Alternate group implementation to exercise activation system *
 * functionality.
 *
 * Ensures that the following behavior is possible using rmi
 * Activation:
 * 
 * 1. ActivationGroupImpl objects may be downloaded and installed
 * 2. SecurityManagers may be downloaded and installed.
 */

import java.rmi.server.*;
import java.rmi.*;
import net.jini.activation.*;
import net.jini.activation.arg.*;
import java.net.*;
import java.lang.reflect.*;
import net.jini.config.ConfigurationException;

public class AlternateGroup {
    public static synchronized ActivationGroup createGroup(
					      ActivationGroupID id,
					      ActivationGroupDesc desc,
					      long incarnation)
        throws ActivationException
    {
	try {
	    String securityManagerCodebaseURL = 
		TestLibrary.getProperty("securityManagerCodebaseURL",
					"file:customSecurityManager/");

	    // load group's security manager
	    System.err.println("Loading security manager: " + 
			       securityManagerCodebaseURL);
	    Class cl = RMIClassLoader.loadClass(
					 new URL(securityManagerCodebaseURL),
					 "CustomRMISecurityManager");
	    SecurityManager newSecurityManager =
		(SecurityManager) cl.newInstance();
	    System.err.println("Setting the security manager.");
	    System.setSecurityManager(newSecurityManager);

	    desc = (ActivationGroupDesc) desc.getData().get();
	    cl = RMIClassLoader.loadClass(desc.getLocation(),
					  desc.getClassName());
	    Method create =
		cl.getMethod("createGroup",
			     new Class[]{ActivationGroupID.class,
					 ActivationGroupDesc.class,
					 long.class});
	    ActivationGroup group = (ActivationGroup)
		create.invoke(null,
			      new Object[]{id, desc, new Long(incarnation)});
	    System.err.println("Alternate group implementation created.");
	    return group;
	} catch (Exception e) {
	    throw new ActivationException("Exception while" +
					  " creating custom group impl", e);
	}
    }
}
