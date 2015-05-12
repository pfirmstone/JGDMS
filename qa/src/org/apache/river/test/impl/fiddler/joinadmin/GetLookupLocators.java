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

package org.apache.river.test.impl.fiddler.joinadmin;

import java.util.logging.Level;

import org.apache.river.test.spec.discoveryservice.AbstractBaseTest;

import org.apache.river.test.share.LocatorsUtil;
import org.apache.river.test.share.JoinAdminUtil;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;

import net.jini.admin.JoinAdmin;
import net.jini.discovery.LookupDiscoveryService;

import net.jini.core.discovery.LookupLocator;

import java.rmi.RemoteException;
import org.apache.river.qa.harness.AbstractServiceAdmin;
import org.apache.river.qa.harness.Test;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully return the locators with which it has been configured to join.
 *
 * This test attempts to retrieve the set of locators with which the service
 * initially configured.
 *
 *
 * @see org.apache.river.qa.harness.QAConfig
 * @see org.apache.river.qa.harness.TestEnvironment
 */
public class GetLookupLocators extends AbstractBaseTest {

    private LookupLocator[] expectedLocators = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then retrieves from the
     *  tests's configuration property file, the set of locators whose 
     *  member(s) correspond to the specific the lookup service(s) that
     *  the lookup discovery service is expected to attempt to join.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        String joinStr = 
	    config.getStringConfigVal(serviceName + ".tojoin", null);
        logger.log(Level.FINE, "joinStr from QAConfig = " + joinStr);
	AbstractServiceAdmin admin = 
	    (AbstractServiceAdmin) getManager().getAdmin(discoverySrvc);
        if (admin == null) {
            return this;
        }
        expectedLocators = admin.getLocators();
        if(expectedLocators != null){
            if(expectedLocators.length == 0) {
                logger.log(Level.FINE, 
			   "expectedLocators.length = {no locators}");
            } else {
                LocatorsUtil.displayLocatorSet(expectedLocators,
                                               "expectedLocators",
                                               Level.FINE);
            }
        } else {
            logger.log(Level.FINE, "expectedLocators = null");
        }
        return this;
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, retrieves the set of locators that the service
     *     is currently configured to join.
     *  3. Determines if the set of locators retrieved through the admin is
     *     equivalent to the set of locators with which the service was
     *     configured when it was started.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start service "
				  + serviceName);
        }
	JoinAdmin joinAdmin = JoinAdminUtil.getJoinAdmin(discoverySrvc);
	LookupLocator[] curLocators = joinAdmin.getLookupLocators();
	LocatorsUtil.displayLocatorSet(curLocators,"curLocators",
				       Level.FINE);
	if (!LocatorsUtil.compareLocatorSets(expectedLocators, 
					     curLocators, 
					     Level.FINE)) 
	{
	    throw new TestException("Locator sets are not equivalent");
	}
    }
}


