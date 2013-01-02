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
package com.sun.jini.test.impl.norm;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

import java.rmi.NoSuchObjectException;

import net.jini.core.lease.Lease;

import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

import com.sun.jini.test.share.LeaseUsesTestBase;

/** 
 * Tests that an Lease Renweal Service properly grants, renews,
 * cancels, and/or expires leases.  Tests for availably by adding
 * leases to the set.
 */
public class UseRenewalServiceLeaseTest extends LeaseUsesTestBase {
    /** Renewal Service under test */
    private LeaseRenewalService lrs;
    
    /** 
     * Set we have gotten from the renewal service to test lease availability
     */
    private LeaseRenewalSet resource;

    /**
     * Counter we use to generate lease ids
     */
    private long idCounter = 0;

    /**
     * Counter we use to generate lease batches
     */
    private long bundle = 0;

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	this.parse();
        return this;
    }

    protected void parse() throws Exception {
	super.parse();
	renewals = getConfig().getIntConfigVal("com.sun.jini.test.impl.norm.renew", 0);
	cancel = getConfig().getBooleanConfigVal("com.sun.jini.test.impl.norm.cancel", false);
	shutdownTime = getConfig().getLongConfigVal("com.sun.jini.test.impl.norm.shutdownTime", -1);
	restartSleep = getConfig().getLongConfigVal("com.sun.jini.test.impl.norm.restartSleep", 10000);
    }

    protected Lease acquireResource() throws TestException {
	specifyServices(new Class[]{LeaseRenewalService.class});
	lrs = (LeaseRenewalService)services[0];

	// Ensure the service is running
	prep(0);	

	try {
	    resource = lrs.createLeaseRenewalSet(durationRequest);
	    resource = prepareSet(resource);
	    resourceRequested();
	    return prepareNormLease(resource.getRenewalSetLease());
	} catch (Exception e) {
	    throw new TestException ("creating lease renewal set: " + e.getMessage());
	}
	// Will never get here
	//return null;
    }
    
    protected boolean isAvailable() throws TestException {
	try {
	    Lease l = LocalLease.getLocalLease(System.currentTimeMillis() + 60000,
				     60000, bundle++, idCounter++);
	    resource.renewFor(l, 600000);
	    if (bundle % 27 == 0) {
		bundle = 0;
	    }

	    if (idCounter % 50 == 0) {
		logger.log(Level.INFO, idCounter + " ");
	    }

	    return true;
	} catch (NoSuchObjectException e) {
	    logger.log(Level.INFO, "\nGot no NoSuchObjectException, lease has expired");
	    e.printStackTrace();
	    logger.log(Level.INFO, (idCounter - 2) + " leases registered");
	    return false;
	} catch (Exception e) {
	    logger.log(Level.INFO, (idCounter - 2) + " leases registered");
	    throw new TestException ("Testing for availability: " + e.getMessage());
	}
	// Should never get here
	//return false;
    }
}
