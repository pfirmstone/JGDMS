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
package com.sun.jini.test.impl.start;

import com.sun.jini.qa.harness.Test;
import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.start.ServiceStarter;
import com.sun.jini.start.NonActivatableServiceDescriptor;
import net.jini.config.ConfigurationException; 
import net.jini.config.EmptyConfiguration;
import java.lang.reflect.InvocationTargetException;


import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.security.AccessControlException;

public class ServiceStarterCreateBadTransientServiceTest extends StarterBase implements Test {
    public static File getServiceConfigFile() throws Exception {
        File config = File.createTempFile("Mercury", ".config");
	config.deleteOnExit();
	String logDir = config.toString() + "_log";
        // believe it or not, the next line coverts '\' to '\\'
	logDir = logDir.replaceAll("\\\\", "\\\\\\\\"); //config file needs doubled '\'
        String entries = 
	    "com.sun.jini.mercury{ \n static persistenceDirectory = \"" 
	    + logDir + "\";\n}\n";
        FileWriter fw = new FileWriter(config);
	fw.write(entries);
	fw.flush();
	fw.close();
        return config; 
    }

    private boolean failed = false;
    private void failed() {
        failed = true;
    }

    public void run() throws Exception {
        File service_config = getServiceConfigFile();

	String codebase = getConfig().getStringConfigVal(
	        "net.jini.event.EventMailbox.codebase", null);
	logger.log(Level.INFO, "codebase = " + codebase);
	String policy = getConfig().getStringConfigVal(
	        "net.jini.event.EventMailbox.policyfile", null);
	logger.log(Level.INFO, "policy = " + policy);
	String impl = getConfig().getStringConfigVal(
	        "net.jini.event.EventMailbox.transient.impl", null);
	logger.log(Level.INFO, "impl = " + impl);
	String classpath = getConfig().getStringConfigVal(
	        "net.jini.event.EventMailbox.classpath", null);
	logger.log(Level.INFO, "classpath = " + classpath);
	if (codebase == null || policy == null ||
	    impl == null || classpath == null) {
	    throw new TestException(
		    "Service codebase, classpath, "
		    + "impl, or policy was null");
	}

	NonActivatableServiceDescriptor badCodebase =
                new NonActivatableServiceDescriptor(
                    codebase.replaceAll("mercury-dl.jar", "mercury-dl_bogus.jar"), 
		    policy, classpath, impl, 
		    new String[] { service_config.toString() });
	try {
	    Object proxy = badCodebase.create(EmptyConfiguration.INSTANCE);
            throw new TestException(" Created proxy [" + proxy 
                    + "] with bad codebase descriptor: " + badCodebase);
        } catch (ClassNotFoundException e) {
            logger.log(Level.INFO, "Expected Failure with bad codebase descriptor: " 
		    + badCodebase);
            e.printStackTrace();
	}

	NonActivatableServiceDescriptor badPolicy =
                new NonActivatableServiceDescriptor(
                    codebase, policy.replaceAll("policy", "bogus_policy"),
                    classpath, impl, new String[] { service_config.toString() });
            
	try {
	    Object proxy = badPolicy.create(EmptyConfiguration.INSTANCE);
	    throw new TestException( 
		   "Failed - Created proxy [" + proxy 
                   + "] with bad policy descriptor: " + badPolicy);
        } catch (InvocationTargetException ce) {
            if (verifyInvocationTargetException(ce)) {
                logger.log(Level.INFO, 
			   "Expected Failure with bad policy descriptor: " 
		       + badPolicy);
                ce.printStackTrace();
	    } else {
                logger.log(Level.INFO, 
			   "Unexpected InvocationTargetException with "
			   + "bad policy descriptor");
	        throw new TestException( 
		    "Unexpected InvocationTargetException with bad policy", ce);
	    }
        }

	NonActivatableServiceDescriptor badClasspath =
                new NonActivatableServiceDescriptor(
                    codebase,
                    policy,
                    classpath.replaceAll("mercury", "mercury_bogus"),
                    impl, new String[] { service_config.toString() });
	try {
	    Object proxy = badClasspath.create(EmptyConfiguration.INSTANCE);
            throw new TestException( "Failed - Created proxy [" 
				     + proxy
				     + "] with bad classpath descriptor: "
				     + badClasspath);
        } catch (ClassNotFoundException e) {
            logger.log(Level.INFO, 
		       "Expected Failure with bad classpath descriptor: " 
		       + badClasspath);
            e.printStackTrace();
	}

	NonActivatableServiceDescriptor badImpl =
                new NonActivatableServiceDescriptor(
                    codebase,
                    policy,
                    classpath,
                    impl.replaceAll("Impl", "Impl_bogus"),
                    new String[] { service_config.toString() });
	try {
	    Object proxy = badImpl.create(EmptyConfiguration.INSTANCE);
            throw new TestException(
	            "Failed - Created proxy [" + proxy 
                    + "] with bad implementation descriptor: " + badImpl);
        } catch (ClassNotFoundException e) {
            logger.log(Level.INFO, "Expected Failure with bad implementation descriptor: "
		    + badImpl);
            e.printStackTrace();
	}

	NonActivatableServiceDescriptor badConfig =
            new NonActivatableServiceDescriptor(
                    codebase,
                    policy,
                    classpath,
                    impl,
                    new String[] { service_config.getParent() });
	try {
	    Object proxy = badConfig.create(EmptyConfiguration.INSTANCE);
            throw new TestException(
	            "Failed - Created proxy [" + proxy 
                    + "] with bad configuration descriptor: " + badConfig);
        } catch (java.lang.reflect.InvocationTargetException e) {
            logger.log(Level.INFO, "Expected Failure  with bad configuration descriptor: " 
		    + badConfig);
            e.printStackTrace();
	}
	
        return;
    }
    
    private static boolean verifyInvocationTargetException(
        InvocationTargetException e) 
    {
        Throwable cause = e.getCause();
        if (cause != null && cause instanceof ConfigurationException) {
	    cause = cause.getCause();
            if (cause != null && cause instanceof AccessControlException) {
	        return true;
	    }
	}
	return false;
    }
}

