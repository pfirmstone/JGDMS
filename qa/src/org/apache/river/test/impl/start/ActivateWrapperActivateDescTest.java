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

import java.util.logging.Level;

import java.net.URL;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.activation.ActivationGroupDesc.*;
import java.util.Arrays;

import org.apache.river.start.*;
import org.apache.river.start.ActivateWrapper.*;
import org.apache.river.qa.harness.TestException;

/**
 * This test verifies that the ActivateDesc constructor sets the
 * appropriate fields with the appropriate values.
 * The test creates a set of constructor parameters and then verifies
 * that the corresponding field is set to same value provided.
 */

public class ActivateWrapperActivateDescTest extends AbstractStartBaseTest {

    public void run() throws Exception {
	String mailbox = "net.jini.event.EventMailbox";
	String registrar = "net.jini.core.lookup.ServiceRegistrar";
	logger.log(Level.INFO, "run()");

	String implClassName = 
	    getConfig().getStringConfigVal(mailbox + ".impl", null); 
	logger.log(Level.INFO, "\timplClassName = " + implClassName);

	String classpathStr =
	    getConfig().getStringConfigVal(mailbox + ".classpath", null)
            + java.io.File.pathSeparator    			      
            + getConfig().getStringConfigVal(registrar + ".classpath", null); 

	logger.log(Level.INFO, "\tclasspath = " + classpathStr);
	URL[] classpath = null;
	classpath = ClassLoaderUtil.getClasspathURLs(classpathStr);

	String codebase0 = 
	    getConfig().getStringConfigVal(registrar + ".codebase", null);
	String codebase1 = 
	    getConfig().getStringConfigVal(mailbox + ".codebase", null);
	String codebaseStr = codebase0 + " " + codebase1;
	logger.log(Level.INFO, "\tcodebase = " + codebaseStr);
        URL[] codebase = null;
	codebase =  ClassLoaderUtil.getCodebaseURLs(codebaseStr);
	
	String policy =
	    getConfig().getStringConfigVal(mailbox + ".policyfile", null);
	logger.log(Level.INFO, "\tpolicy = " + policy);

	String logDir = null;
	logDir = getConfig().createUniqueFileName("ActWrp", "log", 
	        System.getProperty("java.io.tmpdir"));

	logger.log(Level.INFO, "\tlogDir = " + logDir);

        if (implClassName == null ||
	    classpath == null     ||
	    codebase == null      ||
	    policy == null        ||
	    logDir == null         ) {
            throw new TestException("Cannot have null arguments.");
	}

	logger.log(Level.INFO, "Generating activation wrapper descriptor");
        MarshalledObject params = null;
	params = new MarshalledObject(logDir);

	// Create ActivateDesc
        ActivateWrapper.ActivateDesc adesc = null;
        adesc = new ActivateWrapper.ActivateDesc(
	            implClassName,
	            classpath,
	            codebase,
	            policy,
	            params);
	logger.log(Level.INFO, "ActivateDesc = " + adesc);

	// Verifying component fields
        if (!implClassName.equals(adesc.className)  ||
	    !Arrays.equals(classpath, adesc.importLocation) ||
	    !Arrays.equals(codebase, adesc.exportLocation)  ||
	    !policy.equals(adesc.policy)            ||
	    !params.equals(adesc.data)                ) {
            throw new TestException("ActivateWrapper descriptor is invalid.");
	}
    }
}
