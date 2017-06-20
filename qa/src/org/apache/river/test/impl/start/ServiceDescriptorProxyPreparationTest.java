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
import java.rmi.RemoteException;
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
import org.apache.river.start.NonActivatableServiceDescriptor;
//import org.apache.river.start.NonActivatableServiceDescriptor.Created;
import org.apache.river.start.SharedActivatableServiceDescriptor;
//import org.apache.river.start.SharedActivatableServiceDescriptor.Created;
import org.apache.river.start.group.SharedGroup;
import net.jini.config.EmptyConfiguration;
import net.jini.event.EventMailbox;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

public class ServiceDescriptorProxyPreparationTest extends StarterBase implements Test {

    private final RemoteException antiMercuryException = 
	new RemoteException("Mercury service encountered");

    private final ProxyPreparer antiMercuryProxyPreparer = 
	new ProxyPreparer() {
	    public Object prepareProxy(Object proxy) throws RemoteException {
		if (proxy instanceof EventMailbox) {
		    throw antiMercuryException;
		}
                return null;
	    }
	};

    private final ProxyPreparer noOpProxyPreparer = 
	new BasicProxyPreparer();

    public void run() throws Exception {
        SharedGroup sg = null;
        sg = (SharedGroup)getManager().startService("sharedGroup");
	SharedGroupAdmin sga = null;
	sga = (SharedGroupAdmin)getManager().getAdmin(sg);

        String codebase = 
            getConfig().genIntegrityCodebase(
                getConfig().getStringConfigVal(
                    "net.jini.event.EventMailbox.codebase", null), null);
        logger.log(Level.INFO, "codebase = " + codebase);
        String policy = getConfig().getStringConfigVal(
            "net.jini.event.EventMailbox.policyfile", null);
        logger.log(Level.INFO, "policy = " + policy);
        String act_impl = getConfig().getStringConfigVal(
            "net.jini.event.EventMailbox.activatable.impl", null);
        logger.log(Level.INFO, "activatable impl = " + act_impl);
        String trans_impl = getConfig().getStringConfigVal(
            "net.jini.event.EventMailbox.transient.impl", null);
        logger.log(Level.INFO, "transient impl = " + trans_impl);
        String classpath = getConfig().getStringConfigVal(
            "net.jini.event.EventMailbox.classpath", null);
        logger.log(Level.INFO, "classpath = " + classpath);
        if (codebase == null || policy == null ||
            act_impl == null || trans_impl == null ||
            classpath == null) {
            throw new TestException(
                "Service codebase, classpath, "
                + "impl, or policy was null");
        }

        String config = null;
	config = 
	    ServiceStarterCreateBadTransientServiceTest.getServiceConfigFile().toString();
	

	SharedActivatableServiceDescriptor antiMercuryInnerProxy =
            new SharedActivatableServiceDescriptor(
		codebase,
                policy,
                classpath,
                act_impl,
                sga.getSharedGroupLog().toString(),
                new String[] { config },
		antiMercuryProxyPreparer,
		noOpProxyPreparer,
		true);
        try {
            SharedActivatableServiceDescriptor.Created created = 
                (SharedActivatableServiceDescriptor.Created)antiMercuryInnerProxy.create(
		    EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a bad inner proxy descriptor: " 
		+ antiMercuryInnerProxy);
        } catch (Exception e) {
	    logger.log(Level.INFO, 
		"Caught failure -- with a bad inner proxy descriptor: " 
	        + e);
            e.printStackTrace();
	    if (!antiMercuryException.equals(e)) {
	        throw new TestException("Caught unexpected exception");
	    } else {
	        logger.log(Level.INFO, 
		    "Expected failure caught.");
	    }
        } 

	config = 
	    ServiceStarterCreateBadTransientServiceTest.getServiceConfigFile().toString();
	SharedActivatableServiceDescriptor antiMercuryOuterProxy =
            new SharedActivatableServiceDescriptor(
		codebase,
                policy,
                classpath,
                act_impl,
                sga.getSharedGroupLog().toString(),
                new String[] { config },
		noOpProxyPreparer,
                antiMercuryProxyPreparer,
		true);
        try {
            SharedActivatableServiceDescriptor.Created created = 
                (SharedActivatableServiceDescriptor.Created)antiMercuryOuterProxy.create(
		    EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a bad outer proxy descriptor: " 
		+ antiMercuryOuterProxy);
        } catch (Exception e) {
	    logger.log(Level.INFO, 
		"Caught failure -- with a bad outer proxy descriptor: " 
	        + e);
            e.printStackTrace();
	    if (!antiMercuryException.equals(e)) {
	        throw new TestException("Caught unexpected exception");
	    } else {
	        logger.log(Level.INFO, 
		    "Expected failure caught.");
	    }
        } 

	config = 
	    ServiceStarterCreateBadTransientServiceTest.getServiceConfigFile().toString();
	SharedActivatableServiceDescriptor antiMercuryInnerOuterProxy =
            new SharedActivatableServiceDescriptor(
		codebase,
                policy,
                classpath,
                act_impl,
                sga.getSharedGroupLog().toString(),
                new String[] { config },
		antiMercuryProxyPreparer,
                antiMercuryProxyPreparer,
		true);
        try {
            SharedActivatableServiceDescriptor.Created created = 
                (SharedActivatableServiceDescriptor.Created)antiMercuryInnerOuterProxy.create(
		    EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a bad inner-outer proxy descriptor: " 
		+ antiMercuryInnerOuterProxy);
        } catch (Exception e) {
	    logger.log(Level.INFO, 
		"Caught failure -- with a bad inner-outer proxy descriptor: " 
	        + e);
            e.printStackTrace();
	    if (!antiMercuryException.equals(e)) {
	        throw new TestException("Caught unexpected exception");
	    } else {
	        logger.log(Level.INFO, 
		    "Expected failure caught.");
	    }
        } 

	config = 
	    ServiceStarterCreateBadTransientServiceTest.getServiceConfigFile().toString();
	NonActivatableServiceDescriptor antiNonActMercuryProxy =
            new NonActivatableServiceDescriptor(
		codebase,
                policy,
                classpath,
                trans_impl,
                new String[] { config },
		antiMercuryProxyPreparer);
        try {
            NonActivatableServiceDescriptor.Created created = 
                (NonActivatableServiceDescriptor.Created)antiNonActMercuryProxy.create(
		    EmptyConfiguration.INSTANCE);
	    throw new TestException(
	        "Created proxy: " + created.proxy 
		+ " with a bad trans proxy descriptor: " 
		+ antiNonActMercuryProxy);
        } catch (Exception e) {
	    logger.log(Level.INFO, 
		"Caught failure -- with a bad trans proxy descriptor: " 
	        + e);
            e.printStackTrace();
	    if (!antiMercuryException.equals(e)) {
	        throw new TestException("Caught unexpected exception");
	    } else {
	        logger.log(Level.INFO, 
		    "Expected failure caught.");
	    }
        } 
    }
}

