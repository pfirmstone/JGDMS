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
import org.apache.river.qa.harness.QAConfig;

import org.apache.river.test.share.LocatorsUtil;
import org.apache.river.test.share.JoinAdminUtil;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

import net.jini.admin.JoinAdmin;
import net.jini.discovery.LookupDiscoveryService;

import net.jini.core.discovery.LookupLocator;

import java.net.MalformedURLException;
import java.rmi.RemoteException;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully replace set of locators with which it has been configured
 * to join with a new set of locators.
 *
 * This test attempts to replace a non-empty set of locators with which the
 * service is currently configured with another non-emtpy set of locators.
 *
 * In addition to verifying the capabilities of the service with respect
 * to locator replacement, this test also verifies that the
 * <code>setLocators</code> method of the interface
 * <code>net.jini.discovery.DiscoveryLocatorManagement</code> functions
 * as specified. That is, 
 * <p>
 * "The <code>setLocators</code> method replaces all of the locators in the
 *  managed set with <code>LookupLocator</code> objects from a new set."
 * 
 */
public class SetLookupLocators extends AbstractBaseTest {

    LookupLocator[] newLocatorSet = null;
    private LookupLocator[] expectedLocators = null;

    /** Constructs and returns the set of locators with which to replace
     *  the service's current set of locators (can be overridden by 
     *  sub-classes)
     */
    LookupLocator[] getTestLocatorSet() throws MalformedURLException {
        return new LookupLocator[]
                                {QAConfig.getConstrainedLocator("jini://newHost0:5160"),
                                 QAConfig.getConstrainedLocator("jini://newHost1:6161"),
                                 QAConfig.getConstrainedLocator("jini://newHost2:7162"),
                                 QAConfig.getConstrainedLocator("jini://newHost3:8163")
                                };
    }

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then constructs the set
     *  of locators that should be expected expected after replacing the
     *  service's current set of locators  with the new set of locators.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        newLocatorSet = getTestLocatorSet();
        /* Construct the expected locators set */
        if(newLocatorSet == null) {
            logger.log(Level.FINE, "expectedLocators = NullPointerException");
        } else {//newLocatorSet != null
            expectedLocators = newLocatorSet;
            LocatorsUtil.displayLocatorSet(expectedLocators,
                                           "expectedLocators",
					   Level.FINE);
        }
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin,replaces the service's current set of locators
     *     with a new set of locators to join 
     *  3. Through the admin, retrieves the set of locators that the service
     *     is now configured to join.
     *  4. Determines if the new set of locators with which the service is
     *     configured after the replacement attempt is equivalent to the set
     *     that is expected.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start service "
				  + serviceName);
        }
	JoinAdmin joinAdmin = JoinAdminUtil.getJoinAdmin(discoverySrvc);
	LookupLocator[] oldLocators = joinAdmin.getLookupLocators();
	LocatorsUtil.displayLocatorSet(oldLocators, "oldLocators", Level.FINE);
	LocatorsUtil.displayLocatorSet(newLocatorSet,"setLocators", Level.FINE);
	joinAdmin.setLookupLocators(newLocatorSet);
	LookupLocator[] newLocators = joinAdmin.getLookupLocators();
	LocatorsUtil.displayLocatorSet(newLocators,"newLocators", Level.FINE);
	if (!LocatorsUtil.compareLocatorSets(expectedLocators, 
					     newLocators,
					     Level.FINE)) 
        {
	    throw new TestException("Locator set are not equivalent");
	}
    }
}


