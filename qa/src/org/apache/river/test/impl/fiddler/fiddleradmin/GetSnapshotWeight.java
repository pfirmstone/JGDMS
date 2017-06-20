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
import java.util.Properties;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully return the value of the weight factor applied in the
 * computation that determines when the service should take a snapshot
 * of its persistent state.
 *
 */
public class GetSnapshotWeight extends AbstractBaseTest {

    private float expectedValue = (float)(0.0);

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then retrieves from the
     *  tests's configuration property file, the value of the snapshot
     *  weight factor with which the service is expected to be initially
     *  configured.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        expectedValue = 
	    config.getFloatConfigVal(serviceName + ".snapshotweight", 0);
        logger.log(Level.FINE, "expectedValue = " + expectedValue);
        return this;
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, retrieves the snapshot weight factor 
     *     currently applied in the computation that determines when the
     *     service should take a snapshot of its persistent state.
     *  3. Determines if the value retrieved through the admin is
     *     equivalent to the expected value.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start service "
				  + serviceName);
        }
	FiddlerAdmin serviceAdmin
	    = FiddlerAdminUtil.getFiddlerAdmin(discoverySrvc);
	
	float curValue = serviceAdmin.getPersistenceSnapshotWeight();
	logger.log(Level.FINE, "curValue = " + curValue);
	if(expectedValue != curValue) {
	    throw new TestException("current value (" + curValue + ") "
				  + "!= expected value ("+expectedValue+")");
	}
    }
}


