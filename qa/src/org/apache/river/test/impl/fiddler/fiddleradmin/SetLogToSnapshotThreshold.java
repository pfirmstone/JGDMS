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

package org.apache.river.test.impl.fiddler.fiddleradmin;

import java.util.logging.Level;

import org.apache.river.test.spec.discoveryservice.AbstractBaseTest;
import org.apache.river.test.share.FiddlerAdminUtil;

import org.apache.river.qa.harness.TestException;

import org.apache.river.admin.FiddlerAdmin;

import net.jini.discovery.LookupDiscoveryService;

import java.rmi.RemoteException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully change the value of the threshold applied in the computation
 * that compares the current size of the log containing the running record
 * of the service's persistent state with the potential size of a snapshot
 * of the current state.
 *
 */
public class SetLogToSnapshotThreshold extends AbstractBaseTest {

    private int expectedValue = 0;

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, computes a new value with
     *  which to replace the value of the service's current log-to-snapshot
     *  threshold.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        expectedValue = 1+(2*config.getIntConfigVal(serviceName
                                                +".logtosnapshotthreshold",0));
        logger.log(Level.FINE, "expectedValue = "+expectedValue);
        return this;
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, attempts to replace with a new value the 
     *     current value of the threshold applied in the computation that
     *     compares the current size of the log containing the running
     *     record of the service's persistent state with the potential
     *     size of a snapshot of the current state.
     *  3. Through the admin, retrieves the value of the item just set
     *     and determines if the new value is equivalent to expected value
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start service "
				    +serviceName);
        }
	FiddlerAdmin serviceAdmin
	    = FiddlerAdminUtil.getFiddlerAdmin(discoverySrvc);
	
	int oldValue = serviceAdmin.getPersistenceSnapshotThreshold();
	logger.log(Level.FINE, "oldValue = " + oldValue);
	serviceAdmin.setPersistenceSnapshotThreshold(expectedValue);
	int newValue = serviceAdmin.getPersistenceSnapshotThreshold();
	logger.log(Level.FINE, "newValue = " + newValue);
	if(expectedValue != newValue) {
	    throw new TestException("new value (" + newValue + ") "
				    + "!= expected value ("+expectedValue+")");
	}
    }
}
