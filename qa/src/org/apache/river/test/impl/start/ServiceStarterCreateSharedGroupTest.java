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
package org.apache.river.test.impl.start;

import org.apache.river.qa.harness.Test;
import java.util.logging.Level;

import java.io.File;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.UnknownGroupException;
import net.jini.io.MarshalledInstance;
import java.util.Properties;
import java.util.Arrays;
import java.util.List;
import java.util.Enumeration;

import org.apache.river.qa.harness.TestException;
import org.apache.river.start.ServiceStarter;
import org.apache.river.start.SharedActivationGroupDescriptor;
import org.apache.river.system.FileSystem;
import net.jini.config.EmptyConfiguration;

public class ServiceStarterCreateSharedGroupTest extends StarterBase implements Test {
    public void run() throws Exception {
        String policy = getConfig().getStringConfigVal(
            "sharedGroup.policyfile", null);
        logger.log(Level.INFO, "policy = " + policy);
        String classpath = getConfig().getStringConfigVal(
            "sharedGroup.classpath", null);
        logger.log(Level.INFO, "classpath = " + classpath);
        if (policy == null || classpath == null) {
            throw new TestException(
                "Shared group classpath, "
                + "or policy was null");
        }

        SharedActivationGroupDescriptor sharedGroup =
            new SharedActivationGroupDescriptor(
                policy,
                classpath,
                ServiceDescriptorUtil.getLogDir(),
                null,
                null,
                null);
	create(sharedGroup);
        SharedActivationGroupDescriptor sharedGroupWithOpts =
            new SharedActivationGroupDescriptor(
                policy,
                classpath,
                ServiceDescriptorUtil.getLogDir(),
                "/bin/javax",
                new String[] { "-Xdebug" },
                new String[] { "bogusKey", "bogusValue" });
	create(sharedGroupWithOpts);

	return;
    }

    public static void create(
        SharedActivationGroupDescriptor sharedGroupDesc)
        throws Exception {
        try {
            ActivationGroupID gid = (ActivationGroupID)
		sharedGroupDesc.create(EmptyConfiguration.INSTANCE);
            File flog = new File(sharedGroupDesc.getLog());
            System.out.println("Log: " + flog);
            if (!flog.exists() || !flog.isDirectory()) {
	        throw new TestException(
                    "Failed -- Log dir wasn't created.");
            }
            File cookie = new File(flog, "cookie");
            if (!cookie.exists() || !cookie.isFile() || cookie.length() <= 0) {
	        throw new TestException(
                    "Failed -- Group cookie wasn't created.");
            }
            ActivationGroupID recovered = 
                restoreGroupID(sharedGroupDesc.getLog());
            if (!gid.equals(recovered)) {
	        throw new TestException(
                    "Failed -- Group ID improperly stored.");
            }

            ActivationGroupDesc adesc =  null;
	    try {
                adesc = 
		    ActivationGroup.getSystem().getActivationGroupDesc(gid);
		ActivationGroupDesc.CommandEnvironment ce = 
		    adesc.getCommandEnvironment(); 
		String[] opts = ce.getCommandOptions();
		List lopts = Arrays.asList(opts);
	        System.out.println("Options: " + lopts);
		if (!lopts.contains("-cp") || 
		    !lopts.contains(sharedGroupDesc.getClasspath())) {
	            throw new TestException(
                        "Failed -- bad opts.");
		}
		String[] userOpts = sharedGroupDesc.getServerOptions();
		for (int i=0; i < userOpts.length; i++) {
		    if (!lopts.contains(userOpts[i])) {
	                throw new TestException(
                            "Failed -- bad  user opts.");
		    }
		}
		Properties props = adesc.getPropertyOverrides();
	        System.out.println("Properties: " + props);
		String pol = props.getProperty("java.security.policy");
		if (pol == null || !pol.equals(sharedGroupDesc.getPolicy())) {
	            throw new TestException(
                        "Failed -- bad props.");
		}
		Properties userProps = sharedGroupDesc.getServerProperties();
	        System.out.println("User Properties: " + userProps);
		if (userProps != null) {
		    Enumeration e = userProps.keys();
		    Object key = null;
		    Object val = null;
		    while (e.hasMoreElements()) {
			key = e.nextElement();
			val = userProps.get(key);
			if (!props.containsKey(key) ||
			    !props.contains(val)) {
	                    throw new TestException(
                                "Failed -- bad user props.");
			}
		    }
		}

		String path = ce.getCommandPath(); 
	        System.out.println("getCommandPath: " + path);
		if (path != null &&
		    !path.equals(sharedGroupDesc.getServerCommand())) {
	            throw new TestException(
                        "Failed -- bad path.");
		}
		String cp = adesc.getLocation();
	        System.out.println("getLocation: " + cp);
		if (cp != null) {
	            throw new TestException(
                        "Failed -- bad location.");
		}
	    } catch (UnknownGroupException uge) {
	        throw new TestException(
                    "Failed -- Group ID not registered.");
	    }
        } finally {
            File xlog = new File(sharedGroupDesc.getLog());
            try {
	        FileSystem.destroy(xlog, true);
	    } catch (Exception e) {
	        System.out.println("Trouble deleting log: " + xlog);
	    }
        }
	return;
    }
    /**
     * Utility method that restores the object stored in a well known file
     * under the provided <code>dir</code> path.
     */
    private static ActivationGroupID restoreGroupID(String dir)
        throws IOException, ClassNotFoundException
    {
        File log = new File(dir);
        String absDir = log.getAbsolutePath();
        if (!log.exists() || !log.isDirectory()) {
            throw new IOException("Log directory [" 
	    + absDir + "] does not exist.");
        }

        File cookieFile = new File(log, "cookie");
        ObjectInputStream ois = null;
        ActivationGroupID obj = null;
        try {
//TODO - lock out strategy for concurrent r/w file access
            ois = new ObjectInputStream(
                      new BufferedInputStream(
                         new FileInputStream(cookieFile)));
            MarshalledInstance mo = (MarshalledInstance)ois.readObject();
	    obj = (ActivationGroupID)mo.get(false);
        } finally {
            if (ois != null) ois.close();
        }
        return obj;
    }
}

