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

import java.util.logging.Level;

import java.net.URL;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.activation.ActivationGroupDesc.*;
import java.util.Arrays;

import com.sun.jini.start.*;
import com.sun.jini.start.ActivateWrapper.*;
import com.sun.jini.qa.harness.TestException;

/**
 * This test verifies that the ActivateDesc constructor sets the
 * appropriate fields with the appropriate values.
 * The test creates a set of constructor parameters and then verifies
 * that the corresponding field is set to same value provided.
 */

public class StartAllServicesTest extends AbstractStartBaseTest {

    public void run() throws Exception {
	String reggie = "net.jini.core.lookup.ServiceRegistrar";
	String fiddler = "net.jini.discovery.LookupDiscoveryService";
	String mailbox = "net.jini.event.EventMailbox";
	String norm = "net.jini.lease.LeaseRenewalService";
	String mahalo = "net.jini.core.transaction.server.TransactionManager";
	String outrigger = "net.jini.space.JavaSpace";

	String[] services = new String[] {
	    reggie,
	    fiddler,
	    mailbox,
	    norm,
	    mahalo,
	    outrigger,
	};
	    
	logger.log(Level.INFO, "run()");
	logger.log(Level.INFO, "Trying to start the following services: {0}",
            Arrays.asList(services));
        Object serviceRef = null;
        for (int i=0; i < services.length; i++) {
            serviceRef = getManager().startService(services[i]);
	    logger.log(Level.INFO, "{0} ref: {1}", 
                new Object[] { services[i], serviceRef });
	}

        return;
    }
}
