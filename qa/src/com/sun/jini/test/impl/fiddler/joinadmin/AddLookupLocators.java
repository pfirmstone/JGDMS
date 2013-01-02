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

package com.sun.jini.test.impl.fiddler.joinadmin;

import java.util.logging.Level;

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.test.share.LocatorsUtil;
import com.sun.jini.test.share.JoinAdminUtil;

import com.sun.jini.qa.harness.TestException;

import net.jini.admin.JoinAdmin;
import net.jini.discovery.LookupDiscoveryService;

import net.jini.core.discovery.LookupLocator;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import com.sun.jini.qa.harness.AbstractServiceAdmin;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully add a new set of locators to the set of locators with 
 * which it has been configured to join.
 *
 * This test attempts to add a non-empty set of locators to the set of
 * locators with which the service is currently configured.
 *
 * In addition to verifying the capabilities of the service with respect to
 * locator addition, this test also verifies that the <code>addLocators</code>
 * method of the <code>net.jini.discovery.DiscoveryLocatorManagement</code>
 * interface functions as specified. That is, 
 * <p>
 * "The <code>addLocators</code> method adds a set of locators to the
 *  managed set (of locators)".
 * 
 */
public class AddLookupLocators extends AbstractBaseTest {

    LookupLocator[] newLocatorSet = null;
    private LookupLocator[] expectedLocators = null;

    /** Constructs and returns the set of locators to add (can be overridden
     *  by sub-classes)
     */
    LookupLocator[] getTestLocatorSet() throws MalformedURLException {
        return new LookupLocator[]
                 {QAConfig.getConstrainedLocator("jini://newHost0:5160"),
		  QAConfig.getConstrainedLocator("jini://newHost1:6161"),
		  QAConfig.getConstrainedLocator("jini://newHost2:7162"),
		  QAConfig.getConstrainedLocator("jini://newHost3:8163")
		 };
    }//end getTestLocatorSet

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then constructs the set
     *  of locators that should be expected after adding a new set of locators.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        newLocatorSet = getTestLocatorSet();
	AbstractServiceAdmin admin = 
	    (AbstractServiceAdmin) getManager().getAdmin(discoverySrvc);
        if (admin == null) {
            return this;
        }
        LookupLocator[] configLocators = admin.getLocators();

        /* Construct the expected locators set */
        if(configLocators == null) {
            logger.log(Level.FINE, 
		       "expectedLocators = UnsupportedOperationException");
        } else {//configLocators != null
            if(newLocatorSet == null) {
                logger.log(Level.FINE, 
			   "expectedLocators = NullPointerException");
            } else {//newLocatorSet & configLocators != null
                expectedLocators =
                 new LookupLocator[configLocators.length+newLocatorSet.length];
                for(int i=0;i<configLocators.length;i++) {
                    expectedLocators[i] = configLocators[i];
                }
                for(int i=0;i<newLocatorSet.length;i++) {
                    expectedLocators[i+configLocators.length]
                                                           = newLocatorSet[i];
                }
                LocatorsUtil.displayLocatorSet(expectedLocators,
                                               "expectedLocators",
                                               Level.FINE);
            }
        }
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, adds to the service's current set of locators
     *     a new set of locators to join 
     *  3. Through the admin, retrieves the set of locators that the service
     *     is now configured to join.
     *  4. Determines if the new set of locators with which the service is
     *     configured after the addition attempt is equivalent to the set
     *     that is expected.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start service "
				    +serviceName);
        }
	JoinAdmin joinAdmin = JoinAdminUtil.getJoinAdmin(discoverySrvc);
	LookupLocator[] oldLocators = joinAdmin.getLookupLocators();
	LocatorsUtil.displayLocatorSet(oldLocators,"oldLocators", Level.FINE);
	LocatorsUtil.displayLocatorSet(newLocatorSet,"addLocators", Level.FINE);
	joinAdmin.addLookupLocators(newLocatorSet);
	LookupLocator[] newLocators = joinAdmin.getLookupLocators();
	LocatorsUtil.displayLocatorSet(newLocators,"newLocators", Level.FINE);
	if (!LocatorsUtil.compareLocatorSets(expectedLocators,
					     newLocators,
					     Level.FINE))
	{
	    throw new TestException("Locator sets are not equivalent");
	}
    }
}


