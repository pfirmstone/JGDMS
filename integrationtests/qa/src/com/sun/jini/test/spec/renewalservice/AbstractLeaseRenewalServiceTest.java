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


package com.sun.jini.test.spec.renewalservice;

import java.util.logging.Level;

// java.util
import java.util.ArrayList;
import java.util.StringTokenizer;

// java.io
import java.io.PrintWriter;

// java.rmi
import java.rmi.RemoteException;

// net.jini
import net.jini.lease.LeaseRenewalSet;
import net.jini.core.event.EventRegistration;
import net.jini.lease.LeaseRenewalService;
import net.jini.core.lease.Lease;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATest;

// net.jini
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;

/**
 * This class performs common setup tasks for all
 * LeaseRenewalServiceTests.
 */
public abstract class AbstractLeaseRenewalServiceTest extends QATest {

    /**
     * The test configuration object
     */
    protected Configuration testConfiguration;

    /**
     * Holds instances to LRS proxy objects returned from StartService.
     */
    private ArrayList lrsServices = new ArrayList();

    /**
     * the name of service for which these test are written
     */
    protected final String serviceName = "net.jini.lease.LeaseRenewalService";

    /**
     * utility class that implements convenience methods 
     */
    protected LeaseRenewalServiceTestUtil rstUtil;

    /**
     * Sets up the testing environment.
     */
    public void setup(QAConfig config) throws Exception {

       // mandatory call to parent
       super.setup(config);

       testConfiguration = config.getConfiguration();
	
       // output the name of this test
       logger.log(Level.FINE, "Test Name = " + this.getClass().getName());
	
       // Announce where we are in the test
       logger.log(Level.FINE, "AbstractLeaseRenewalServiceTest: " +
			 "In setup() method.");

       // capture an instance of the Properties file.
       rstUtil = new LeaseRenewalServiceTestUtil(config);

       /*  capture the number of LRS services required for this test.
        *  default = 1
        */
       String prop = serviceName + ".instances";
       int numLRS = getConfig().getIntConfigVal(prop, 1);

       // Must have at least one service for a sane test
       if (numLRS < 1) {
	  String err = "Property " + serviceName + ".instances < 1";
	  throw new IllegalArgumentException(err);
       }

       // start each service as requested
       for (int i = 0; i < numLRS; i++) {
	   logger.log(Level.FINE, "Starting LRS service #" + i);
	   lrsServices.add(manager.startService(serviceName));
       }
    }

    /**
     * Returns an instance of the Nth LeaseRenewalService requested.
     * 
     * <P>Notes:</P><BR>The number of LeaseRenewalServices started is 
     * determined by the net.jini.lease.LeaseRenewalService.instances 
     * Property value.
     * 
     * @param index The index of the LeaseRenewalService to be returned.
     * 
     * @return the Nth instance of the requested LeaseRenewalServices
     * 
     * @exception IllegalArugumentException
     *          If index is less than 0.
     * 
     * @exception IndexOutOfBoundsException
     *          If index is greater than or equal to the number of LRS 
     *          proxy instances.
     * 
     * @see    AbstractLeaseRenewalServiceTest#setup() 
     */
    public LeaseRenewalService getLRS(int index) {

	// index must be non-negative
	if (index < 0) {
	    throw new IllegalArgumentException("Argument index is < 0.");
	}
	
	return (LeaseRenewalService) lrsServices.get(index);
    }

    /**
     * Convenience method always returns the first LRS proxy instance.
     * 
     * @return the 1st instance of the requested LeaseRenewalServices
     * 
     * @exception IndexOutOfBoundsException
     *          If index is greater than or equal to the number of LRS 
     *          proxy instances.
     * 
     * @see    AbstractLeaseRenewalServiceTest#setup() 
     */
    public LeaseRenewalService getLRS() {
	return getLRS(0);
    }

    protected Exporter getExporter() throws ConfigurationException {
        // check for none cofiguration
	if (!(testConfiguration instanceof com.sun.jini.qa.harness.QAConfiguration)) { 
	    return QAConfig.getDefaultExporter();
	}
        return (Exporter) testConfiguration.getEntry("test",
                                                     "normListenerExporter", 
                                                     Exporter.class);
    }

    protected LeaseRenewalSet prepareSet(LeaseRenewalSet set)
	throws TestException
    {
	Object preparedSet = getConfig().prepare("test.normRenewalSetPreparer",
						 set);
	return (LeaseRenewalSet) preparedSet;
    }

    protected EventRegistration prepareRegistration(EventRegistration reg)
	throws TestException
    {
	Object r = getConfig().prepare("test.normEventRegistrationPreparer", reg);
	return (EventRegistration) r;
    }

    protected Lease prepareLease(Lease lease) throws TestException {
	Object l = getConfig().prepare("test.normLeasePreparer", lease);
	return (Lease) l;
    }
} // AbstractLeaseRenewalServiceTest




