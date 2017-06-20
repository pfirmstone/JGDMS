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
import org.apache.river.qa.harness.TestException;
import org.apache.river.start.lifecycle.LifeCycle;
import org.apache.river.start.NonActivatableServiceDescriptor;
import org.apache.river.start.ServiceDescriptor;
import org.apache.river.start.SharedActivatableServiceDescriptor;
import org.apache.river.start.SharedActivationGroupDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.logging.Level;

import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

import net.jini.config.EmptyConfiguration;

public class SerializedServiceDescriptors extends StarterBase implements Test {
    private static LifeCycle lc =
	new LifeCycle() { // default, no-op object
            public boolean unregister(Object impl) { return false; }
	};
    private static ProxyPreparer pp = new BasicProxyPreparer();

    public void run() throws Exception {

        NonActivatableServiceDescriptor nasdWithExtras = 
            new NonActivatableServiceDescriptor(
                "http://resendes:8080/mercury-dl.jar",
                "http://resendes:8086/policy.mercury",
                "http://resendes:8080/mercury.jar",
                "org.apache.river.mercury.TransientMercuryBogusImpl",
                new String[] { 
		    "http://resendes:8089/mercury_service_trans.config" 
		},
                lc,
                pp);
                
        NonActivatableServiceDescriptor nasdWithoutExtras = 
            new NonActivatableServiceDescriptor(
                "http://resendes:8080/mercury-dl.jar",
                "http://resendes:8086/policy.mercury",
                "http://resendes:8080/mercury.jar",
                "org.apache.river.mercury.TransientMercuryBogusImpl",
                new String[] { 
		    "http://resendes:8089/mercury_service_trans.config" 
		});
        LifeCycle DefaultLifeCycle = nasdWithoutExtras.getLifeCycle();
        ProxyPreparer DefaultPreparer = nasdWithoutExtras.getServicePreparer();
        
	String sharedVmLog = "/tmp/sharedvm.log";
        String[] sharedvm_props = new String[] {// server properties
		    "org.apache.river.start.activatewrapper.debug", "init",
	        };
        SharedActivationGroupDescriptor sharedvm =
            new SharedActivationGroupDescriptor(
	        "/view/resendes/vob/jive/policy/policy.sharedvm",
	        "/view/resendes/vob/jive/lib/bogus_sharedvm.jar",
	        sharedVmLog,
    	        "/bin/java", //server command
	        new String[] {// server options
		    "-ea", 
		},
	        sharedvm_props);

        SharedActivatableServiceDescriptor sasd = 
            new SharedActivatableServiceDescriptor(
	        "http://resendes:8080/mercury-dl.jar",
	        "http://resendes:8086/policy.mercury",
	        "http://resendes:8080/mercury.jar",
	        "org.apache.river.mercury.MailboxBogusImpl",
	        sharedVmLog,
	        new String[] { 
		    "http://resendes:8089/mercury_service_act.config" 
		},
	        true);

        SharedActivatableServiceDescriptor sasdWithExtras = 
            new SharedActivatableServiceDescriptor(
	        "http://resendes:8080/mercury-dl.jar",
	        "http://resendes:8086/policy.mercury",
	        "http://resendes:8080/mercury.jar",
	        "org.apache.river.mercury.MailboxBogusImpl",
	        sharedVmLog,
	        new String[] { 
		    "http://resendes:8089/mercury_service_act.config" 
		},
                pp,
                pp,
	        true,
                "localhost",
                1234);
                
        ServiceDescriptor[] serviceDescriptors = 
	    new ServiceDescriptor[] {
		// sasdWithExtras & nasdWithExtras not written because 
                // default/recovered preparer and lifecycle objects won't match.
                nasdWithoutExtras, 
		sharedvm, 
		sasd
	    };

        // Serialize service descriptors to a file
	FileOutputStream f = new FileOutputStream("tmp");    
	ObjectOutput oos = new ObjectOutputStream(f);    
	oos.writeObject(nasdWithExtras);    
       	oos.writeObject(nasdWithoutExtras);    
	oos.writeObject(sharedvm);    
	oos.writeObject(sasd);    
	oos.writeObject(sasdWithExtras);    
        oos.writeObject(serviceDescriptors);    
	oos.flush(); 
	oos.close();
        logger.log(Level.INFO, "Wrote: " + nasdWithExtras);
        logger.log(Level.INFO, "Wrote: " + nasdWithoutExtras);        
        logger.log(Level.INFO, "Wrote: " + sharedvm);
        logger.log(Level.INFO, "Wrote: " + sasd);
        logger.log(Level.INFO, "Wrote: " + sasdWithExtras);
        logger.log(Level.INFO, "Wrote: " + Arrays.asList(serviceDescriptors));

        // Deserialize objects from a file.    
	FileInputStream in = new FileInputStream("tmp");    
	ObjectInputStream ois = new ObjectInputStream(in);    
	NonActivatableServiceDescriptor nasdWithExtras_r = 
	    (NonActivatableServiceDescriptor)ois.readObject();
	nasdWithExtras_r.setLifeCycle(lc);
        nasdWithExtras_r.setServicePreparer(pp);
	NonActivatableServiceDescriptor nasdWithoutExtras_r = 
	    (NonActivatableServiceDescriptor)ois.readObject();        
	SharedActivationGroupDescriptor sharedvm_r = 
	    (SharedActivationGroupDescriptor)ois.readObject();
	SharedActivatableServiceDescriptor sasd_r =
	    (SharedActivatableServiceDescriptor)ois.readObject();
	SharedActivatableServiceDescriptor sasdWithExtras_r =
	    (SharedActivatableServiceDescriptor)ois.readObject();
	//sasdWithExtras_r.setLifeCycle(lc);
        sasdWithExtras_r.setServicePreparer(pp);
        sasdWithExtras_r.setInnerProxyPreparer(pp);
	ServiceDescriptor[] recovered = 
            (ServiceDescriptor[])ois.readObject(); 
	ois.close();
	
        logger.log(Level.INFO, "Read: " + nasdWithExtras_r);
        logger.log(Level.INFO, "Read: " + nasdWithoutExtras_r);
        logger.log(Level.INFO, "Read: " + sharedvm_r);
        logger.log(Level.INFO, "Read: " + sasd_r);
        logger.log(Level.INFO, "Read: " + sasdWithExtras_r);
        logger.log(Level.INFO, "Read: " + Arrays.asList(recovered));

	if (!verifyNonActivatableServiceDescriptors(nasdWithExtras, nasdWithExtras_r)) {
	    throw new TestException(
	        "Written and recovered NonActivatableServiceDescriptors don't match");
	}
	if (!verifyNonActivatableServiceDescriptors(nasdWithoutExtras, nasdWithoutExtras_r)) {
	    throw new TestException(
	        "Written and recovered NonActivatableServiceDescriptors don't match");
	}
        if (!verifySharedActivationGroupDescriptors(sharedvm, sharedvm_r)) {
	    throw new TestException(
	        "Written and recovered SharedActivationGroupDescriptors don't match");
	}
	if (!verifySharedActivatableServiceDescriptors(sasd, sasd_r)) {
	    throw new TestException(
	        "Written and recovered SharedActivatableServiceDescriptors don't match");
	}
	if (!verifySharedActivatableServiceDescriptors(sasdWithExtras, sasdWithExtras_r)) {
	    throw new TestException(
	        "Written and recovered SharedActivatableServiceDescriptors don't match");
	}
	if (!verifyServiceDescriptors(serviceDescriptors, recovered)) {
	    throw new TestException(
	        "Written and recovered ServiceDescriptor[] don't match");
	}
	
	//Do some negative tests - Ensure bad descriptor doesn't match
        NonActivatableServiceDescriptor bogus_nasd = 
            new NonActivatableServiceDescriptor(
                nasdWithoutExtras.getExportCodebase(),
                nasdWithoutExtras.getPolicy(),
                nasdWithoutExtras.getImportCodebase(),
                nasdWithoutExtras.getImplClassName() + "_bogus",
                nasdWithoutExtras.getServerConfigArgs(),
	        nasdWithoutExtras.getLifeCycle());
        if (verifyNonActivatableServiceDescriptors(bogus_nasd, nasdWithoutExtras)) {
	    throw new TestException("Bogus NASD passed verification");
	}
	//Do some negative tests - Ensure setLifeCycle can't be called after creation
	try {
	    nasdWithoutExtras.create(EmptyConfiguration.INSTANCE); //Original descriptor
	} catch (Exception e) {
            logger.log(Level.INFO, "exception creating NASD ... ignoring", e);
	}
	try {
	    nasdWithoutExtras.setLifeCycle(lc); 
	} catch (IllegalStateException ie) {
            logger.log(Level.INFO, "Expected exception setting NASD LifeCycle ... ignoring", ie);
	} catch (Exception e) {
	    e.printStackTrace();
	    throw new TestException("Unexpected exception: " + e);
	}
	
	try {
	    nasdWithoutExtras_r.create(EmptyConfiguration.INSTANCE); //Recovered descriptor
	} catch (Exception e) {
            logger.log(Level.INFO, "exception creating recovered NASD ... ignoring", e);
	}
	try {
	    nasdWithoutExtras_r.setLifeCycle(lc); 
	} catch (IllegalStateException ie) {
            logger.log(Level.INFO, "Expected exception setting recovered NASD LifeCycle ... ignoring", ie);
	} catch (Exception e) {
	    e.printStackTrace();
	    throw new TestException("Unexpected exception: " + e);
	}

	//Do some negative tests - Ensure bad descriptor doesn't match
        SharedActivatableServiceDescriptor bogus_sasd = 
            new SharedActivatableServiceDescriptor(
	        sasd.getExportCodebase(),
	        sasd.getPolicy(),
	        sasd.getImportCodebase(),
	        sasd.getImplClassName(),
	        sasd.getSharedGroupLog() + "_bogus",
	        sasd.getServerConfigArgs(),
	        sasd.getRestart());
	if (verifySharedActivatableServiceDescriptors(bogus_sasd, sasd)) {
	    throw new TestException("Bogus SASD passed verification");
	}
	
	//Do some negative tests - Ensure setLifeCycle can't be called after creation
	try {
	    sasd.create(EmptyConfiguration.INSTANCE); //Original descriptor
	} catch (Exception e) {
            logger.log(Level.INFO, "exception creating SASD ... ignoring", e);
	}
	try {
	    sasd.setLifeCycle(lc); 
	} catch (IllegalStateException ie) {
            logger.log(Level.INFO, "Expected exception setting SASD LifeCycle ... ignoring", ie);
	} catch (Exception e) {
	    e.printStackTrace();
	    throw new TestException("Unexpected exception: " + e);
	}
	try {
	    sasd_r.create(EmptyConfiguration.INSTANCE); //Recovered descriptor
	} catch (Exception e) {
            logger.log(Level.INFO, "exception creating recovered SASD ... ignoring", e);
	}
	try {
	    sasd_r.setLifeCycle(lc); 
	} catch (IllegalStateException ie) {
            logger.log(Level.INFO, "Expected exception setting recovered SASD LifeCycle ... ignoring", ie);
	} catch (Exception e) {
	    e.printStackTrace();
	    throw new TestException("Unexpected exception: " + e);
	}
		
        SharedActivationGroupDescriptor bogus_sharedvm =
            new SharedActivationGroupDescriptor(
	        sharedvm.getPolicy(),
	        sharedvm.getClasspath(),
	        sharedvm.getLog() + "_bogus",
	        sharedvm.getServerCommand(),
	        sharedvm.getServerOptions(),
	        sharedvm_props);
	if (verifySharedActivationGroupDescriptors(bogus_sharedvm, sharedvm)) {
	    throw new TestException("Bogus SAGD passed verification");
	}
		
    }
    
    private static boolean verifyServiceDescriptors(
        ServiceDescriptor[] wrote, ServiceDescriptor[] read) 
    {
        if (wrote.length != read.length) {
            logger.log(Level.INFO, "read length [" + read.length 
	        + "] doesn't match written length [" + wrote.length + "]");
            return false;
	}
	for (int i=0; i < wrote.length; i++) {
	    if (wrote[i] instanceof SharedActivatableServiceDescriptor) {
	        SharedActivatableServiceDescriptor sasdw = 
		    (SharedActivatableServiceDescriptor)wrote[i];
	        SharedActivatableServiceDescriptor sasdr = 
		    (SharedActivatableServiceDescriptor)read[i];
		if (!verifySharedActivatableServiceDescriptors(sasdw, sasdr)) {
		    return false;
		}
	    } else if (wrote[i] instanceof NonActivatableServiceDescriptor) {
	        NonActivatableServiceDescriptor nasdw = 
		    (NonActivatableServiceDescriptor)wrote[i];
	        NonActivatableServiceDescriptor nasdr = 
		    (NonActivatableServiceDescriptor)read[i];
		if (!verifyNonActivatableServiceDescriptors(nasdw, nasdr)) {
		    return false;
		}
	    } else if (wrote[i] instanceof SharedActivationGroupDescriptor) {
	        SharedActivationGroupDescriptor sagdw = 
		    (SharedActivationGroupDescriptor)wrote[i];
	        SharedActivationGroupDescriptor sagdr = 
		    (SharedActivationGroupDescriptor)read[i];
		if (!verifySharedActivationGroupDescriptors(sagdw, sagdr)) {
		    return false;
		}
	    } else {
                logger.log(Level.INFO, "ServiceDescriptor " + wrote[i]
		    + " not handled");
	        return false;
	    }
	}
	return true;
    }
    
    private static boolean verifySharedActivatableServiceDescriptors(
        SharedActivatableServiceDescriptor wrote, SharedActivatableServiceDescriptor read) 
    {
        if (verifyNonActivatableServiceDescriptors(wrote, read) &&
	    wrote.getSharedGroupLog().equals(read.getSharedGroupLog()) &&
	    (wrote.getRestart() == read.getRestart()) &&
	    wrote.getActivationSystemHost().equals(read.getActivationSystemHost()) &&
	    (wrote.getActivationSystemPort() == read.getActivationSystemPort()))
	{
            logger.log(Level.FINE, "Written SASD [" + wrote + "] does match "
	        + "recovered SASD [" + read + "]");	    
	    return true;
	}
        logger.log(Level.INFO, "Written SASD [" + wrote + "] DOESN'T match "
	    + "recovered SASD [" + read + "]");
	return false;
    }
    
    private static boolean verifyNonActivatableServiceDescriptors(
        NonActivatableServiceDescriptor wrote, NonActivatableServiceDescriptor read) 
    {
        if (wrote.getExportCodebase().equals(read.getExportCodebase()) &&
	    wrote.getPolicy().equals(read.getPolicy()) &&
	    wrote.getImportCodebase().equals(read.getImportCodebase()) &&
	    wrote.getImplClassName().equals(read.getImplClassName()) &&
	    Arrays.equals(wrote.getServerConfigArgs(), read.getServerConfigArgs()) &&
	    wrote.getLifeCycle().equals(read.getLifeCycle()) &&
            (wrote.getServicePreparer()==null?
                read.getServicePreparer()==null:
                wrote.getServicePreparer().equals(read.getServicePreparer())))
	{
            logger.log(Level.FINE, "Written NASD [" + wrote + "] does match "
	        + "recovered NASD [" + read + "]");	    
            return true;
	}
        logger.log(Level.INFO, "Written NASD [" + wrote + "] DOESN'T match "
	    + "recovered NASD [" + read + "]");
	return false;
    }
    
    private static boolean verifySharedActivationGroupDescriptors(
        SharedActivationGroupDescriptor wrote, SharedActivationGroupDescriptor read) 
    {
        if (wrote.getPolicy().equals(read.getPolicy()) &&
	    wrote.getClasspath().equals(read.getClasspath()) &&
	    wrote.getLog().equals(read.getLog()) &&
	    wrote.getServerCommand().equals(read.getServerCommand()) &&
	    Arrays.equals(wrote.getServerOptions(), read.getServerOptions()) &&
	    wrote.getServerProperties().equals(read.getServerProperties()) &&
	    wrote.getActivationSystemHost().equals(read.getActivationSystemHost()) &&
	    (wrote.getActivationSystemPort() == read.getActivationSystemPort()))
	{
            logger.log(Level.FINE, "Written SAGD [" + wrote + "] does match "
	        + "recovered SAGD [" + read + "]");	    
	    return true;
	}
        logger.log(Level.INFO, "Written SAGD [" + wrote + "] DOESN'T match "
	    + "recovered SAGD [" + read + "]");
	return false;
    }
}
