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
package com.sun.jini.test.spec.lookupservice.test_set02;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import java.rmi.RemoteException;
import java.io.IOException;
import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lease.*;
import net.jini.core.event.*;

/** This class is used to test that LeaseMap cancelAll() works.
 *  It creates nInstance of Service Lease and nInstance of
 *  Event Lease. Put both of them into a LeaseMap sice they are
 *  come from the same lookup service. Call cancelAll on the LeaseMap.
 *  verify both Service Lease and Event Lease by calling
 *  renew() and check for the UnknownLeaseException.
 */
public class LeaseMapCancel extends QATestRegistrar {
    /** the expected number of matches when testing lookup by ID */
    private static int EXPECTED_N_MATCHES = 1;
    /* lease duration to 1 minute */
    private final static long DEFAULT_LEASEDURATION = 120000;
    private static long leaseDuration = DEFAULT_LEASEDURATION;
    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private Lease[] srvcLeases ;
    private ServiceTemplate[] srvcIDTmpls;
    private ServiceRegistrar proxy;
    private int nInstances = 0;
    private long leaseStartTime;
    private LeaseMap leaseMap;
    private Lease[] evntLeases ;
    private EventRegistration[] evntRegs;

    /** Class which handles all events sent by the lookup service */
    private class Listener extends BasicListener 
                           implements RemoteEventListener, java.io.Serializable
    {
	Listener() throws RemoteException {
	    super();
	}
        /** Method called remotely by lookup to handle the generated event. */
	public void notify(RemoteEvent theEvent)
	                throws UnknownEventException, java.rmi.RemoteException 
	{
	}
    }

    /** The event handler for the services registered by this class */
    private static RemoteEventListener listener;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Loads and instantiates all service 
     *  classes. Retrieves the proxy to the lookup Registrar. Establishes 
     *  an approximate service lease start time for each service item by 
     *  retrieving the current system time. Registers each service class 
     *  instance with a specified lease duration. Creates an array of 
     *  Leases in which each element contains the service lease of one 
     *  of the registered services. Creates an array of ServiceTemplates 
     *  in which each element contains the service ID of one of the 
     *  registered service items.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        int i;
	super.construct(sysConfig);
	listener = new Listener();
        nInstances = super.getNInstances();
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	proxy = super.getProxy();
	srvcIDTmpls = new ServiceTemplate[nInstances];
	leaseStartTime = QATestUtils.getCurTime();
 	srvcRegs = registerAll(leaseDuration);
        srvcLeases = new Lease[srvcRegs.length];

	for(i=0; i<srvcLeases.length; i++) {
	    srvcLeases[i] = getRegistrationLease(srvcRegs[i]);
	}
	for(i=0; i<srvcIDTmpls.length; i++) {
	    srvcIDTmpls[i] = new ServiceTemplate(srvcRegs[i].getServiceID(),
                                                 null,null);
	}
 	evntRegs = new EventRegistration[nInstances];
	evntLeases = new Lease[nInstances];
	registerAllEvents();
	createLeaseMap();
        return this;
   }

    /** Executes the current QA test. */
    public void run() throws Exception {
	QATestUtils.verifyLeases(srvcLeases,
				 leaseStartTime + leaseDuration);
	QATestUtils.verifyLeases(evntLeases,
				 leaseStartTime + leaseDuration);
	java.util.Map mapCopy = new java.util.HashMap(leaseMap);
	leaseMap.cancelAll(); 
	if (!mapCopy.equals(leaseMap))
	    throw new TestException("map contents changed");
	verifyCanceledLease(srvcLeases);
	verifyCanceledLease(evntLeases);
    }

    /** put all our leases into the leaseMap */
    private void createLeaseMap() {
	
	leaseMap = prepareRegistrationLeaseMap(srvcLeases[0].createLeaseMap(leaseDuration));
	
	for(int i=1; i<srvcLeases.length; i++) {
	    leaseMap.put(srvcLeases[i], new Long(leaseDuration));
 	}
	
	for(int i=0; i<evntLeases.length; i++) {
	    leaseMap.put(evntLeases[i], new Long(leaseDuration));
 	}

    }

    /* For each registered service, registers an event notification request,
     * with a specified lease duration, based on the contents of the 
     * corresponding template created during construct and corresponding to
     * the appropriate transition mask. Populates the array of Leases so
     * that each element contains one of the event leases returned by the 
     * event notification registration process.
     */
    private void registerAllEvents() throws Exception {
        for(int i=0; i<evntRegs.length; i++) {
	    EventRegistration er;
	    er = proxy.notify(srvcIDTmpls[i],
			      ServiceRegistrar.TRANSITION_NOMATCH_MATCH  |
			      ServiceRegistrar.TRANSITION_MATCH_NOMATCH |
			      ServiceRegistrar.TRANSITION_MATCH_MATCH,
			      listener,
			      null,
			      leaseDuration);
	    evntRegs[i] = prepareEventRegistration(er);
	    evntLeases[i] = getEventLease(evntRegs[i]);
	}
    }  
  
    private static void verifyCanceledLease(Lease[] leases)
	throws Exception
    {
	for(int i=0; i<leases.length; i++) {
	    try {
		leases[i].renew(Long.MAX_VALUE);
		throw new TestException("UnknownLeaseException should "
				      + "have been thrown");
	    } catch (UnknownLeaseException e) {
	    }
	}

    }
  
}
