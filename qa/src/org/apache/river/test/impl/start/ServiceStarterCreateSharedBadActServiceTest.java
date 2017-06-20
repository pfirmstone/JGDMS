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


import java.io.File;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.net.URL;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.UnknownGroupException;
import java.rmi.MarshalledObject;
import java.rmi.UnmarshalException;
import java.util.Properties;
import java.util.Arrays;
import java.util.List;
import java.util.Enumeration;

import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.SharedGroupAdmin;
import org.apache.river.qa.harness.Test;
import org.apache.river.start.ActivateWrapper;
import org.apache.river.start.ActivateWrapper.ActivateDesc;
import org.apache.river.start.ClassLoaderUtil;
import org.apache.river.start.ServiceStarter;
import org.apache.river.start.SharedActivationGroupDescriptor;
import org.apache.river.start.SharedActivatableServiceDescriptor;
import org.apache.river.start.SharedActivatableServiceDescriptor.Created;
import org.apache.river.start.group.SharedGroup;
import net.jini.config.EmptyConfiguration;

public class ServiceStarterCreateSharedBadActServiceTest extends StarterBase implements Test {
    public void run() throws Exception {
        SharedGroup sg = null;
        sg = (SharedGroup)getManager().startService("sharedGroup");
	SharedGroupAdmin sga = null;
	sga = (SharedGroupAdmin)getManager().getAdmin(sg);

        String codebase = getConfig().getStringConfigVal(
            "net.jini.event.EventMailbox.codebase", null);
        logger.log(Level.INFO, "codebase = " + codebase);
        String policy = getConfig().getStringConfigVal(
            "net.jini.event.EventMailbox.policyfile", null);
        logger.log(Level.INFO, "policy = " + policy);
        String impl = getConfig().getStringConfigVal(
            "net.jini.event.EventMailbox.impl", null);
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

        String config = null;
	config = 
	        ServiceStarterCreateBadTransientServiceTest.getServiceConfigFile().toString();
	

	SharedActivatableServiceDescriptor badCodebase =
            new SharedActivatableServiceDescriptor(
		codebase.replaceAll("mercury", "mercury-bogus"),
                policy,
                classpath,
                impl,
                sga.getSharedGroupLog().toString(),
                new String[] { config },
		true);
        try {
            Created created = 
                (Created)badCodebase.create(EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a badcodebase descriptor: " + badCodebase);
        } catch (UnmarshalException e) {
	    logger.log(Level.INFO, "Expected Failure -- with a badcodebase descriptor: " 
	        + badCodebase);
            e.printStackTrace();
        } 

	SharedActivatableServiceDescriptor badPolicy =
            new SharedActivatableServiceDescriptor(
                codebase,
                policy.replaceAll("policy", "policy_bogus"),
                classpath,
                impl,
                sga.getSharedGroupLog().toString(),
                new String[] { config },
		true);
        try {
            Created created = 
                (Created)badPolicy.create(EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a bad policy descriptor: " + badPolicy);
        } catch (ActivationException e) {
	    logger.log(Level.INFO, "Expected Failure with a bad policy descriptor: " 
	        + badPolicy);
            e.printStackTrace();
        } 

	SharedActivatableServiceDescriptor badClasspath =
            new SharedActivatableServiceDescriptor(
                codebase,
                policy,
                classpath.replaceAll("mercury", "mercury_bogus"),
                impl,
                sga.getSharedGroupLog().toString(),
                new String[] { config },
		true);
        try {
            Created created = 
                (Created)badClasspath.create(EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a bad classpath descriptor: " + badClasspath);
        } catch (ActivationException ae) {
	    if (ae.getCause() instanceof ClassNotFoundException) {
	        logger.log(Level.INFO, 
		    "Expected Failure with a bad classpath descriptor: "
	            + badClasspath, ae);
	    } else {
	        logger.log(Level.INFO, 
		    "Unexpected Failure with a bad classpath descriptor: "
	            + badClasspath);
	        throw new TestException(
	            "Unexpected exception" 
		    + " with a bad classpath descriptor: " + badClasspath, ae);
	    }
        } 

	SharedActivatableServiceDescriptor badImpl =
            new SharedActivatableServiceDescriptor(
                codebase,
                policy,
                classpath,
                impl.replaceAll("Impl", "Impl_bogus"),
                sga.getSharedGroupLog().toString(),
                new String[] { config },
		true);
        try {
            Created created = 
                (Created)badImpl.create(EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a bad impl descriptor: " + badImpl);
        } catch (ActivationException ae) {
	    if (ae.getCause() instanceof ClassNotFoundException) {
	        logger.log(Level.INFO, 
		    "Expected Failure with a bad impl descriptor: "
	            + badImpl, ae);

	    } else {
	        logger.log(Level.INFO, 
		    "Unexpected Failure with a bad impl descriptor: "
	            + badImpl);
	        throw new TestException(
	            "Unexpected exception" 
		    + " with a bad impl descriptor: " + badImpl, ae);
	    }
        } 

	SharedActivatableServiceDescriptor badLog =
            new SharedActivatableServiceDescriptor(
                codebase,
                policy,
                classpath,
                impl,
                sga.getSharedGroupLog().toString() + "bogus",
                new String[] { config },
		true);
        try {
            Created created = 
                (Created)badLog.create(EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a bad log descriptor: " + badLog);
        } catch (IOException e) {
	    logger.log(Level.INFO, "Expected Failure with a bad log descriptor: "
	        + badLog, e);
        } 

	SharedActivatableServiceDescriptor badConfig =
            new SharedActivatableServiceDescriptor(
                codebase,
                policy,
                classpath,
                impl,
                sga.getSharedGroupLog().toString(),
                new String[] { sga.getSharedGroupLog().toString() },
		true);
        try {
            Created created = 
                (Created)badConfig.create(EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a bad config descriptor: " + badConfig);
        } catch (ActivationException e) {
	    logger.log(Level.INFO, "Expected Failure with a bad config descriptor: " 
	        + badConfig, e);
        } 
    }
}

