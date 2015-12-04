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
package org.apache.river.test.spec.lookupservice.test_set03;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;
import org.apache.river.qa.harness.TestException;

import org.apache.river.test.spec.lookupservice.QATestRegistrar;
import org.apache.river.test.spec.lookupservice.QATestUtils;
import net.jini.admin.JoinAdmin;
import net.jini.core.discovery.LookupLocator;
import java.rmi.RemoteException;

/** This class is used to perform a simple verification of the following
 *  methods from the JoinAdmin interface: getLookupLocators(),
 *  addLookupLocators() and removeLookupLocators(). This class first
 *  retrieves the initial set of locators belonging to the Registrar and
 *  verifies that the set contains the expected elements (currently, that
 *  set should be empty). A new set of LookupLocators is then added to
 *  the Registrar and the locators belonging to the Registrar are again
 *  retrieved and verified. This class then removes from the Registrar
 *  a sub-set of the locators that were previously added; and retrieves 
 *  the set of all locators belonging to the Registrar and verifies
 *  that the set contains the expected elements.
 *
 *  @see net.jini.admin.JoinAdmin
 *  @see net.jini.core.discovery.LookupLocator
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class SimpleRemoveLookupLocators extends QATestRegistrar {

    /* Name of the class to load and instantiate for the various arguments */
    private static final String CLASS_NAME
                                          = "net.jini.core.discovery.LookupLocator";
    /* Arguments to the constructor of the various LookupLocator classes */
    private static final String[] HOST_ARGS =
    { "raglan", "thoth",  "raistlin","jellybean","mufti",
      "cocolat","terrier","marvin",  "savoy",    "recycle","baji" };
    private static final int[] PORT_ARGS =
    { 9990,9991,9992,9993,9994,9995,9996,9997,9998,9999,10000 };

    /* Indices of the locators to remove */
    private static final int[] INDXS = {1,3,4,8,10};

    private JoinAdmin adminProxy;
    private LookupLocator[] addLocators = new LookupLocator[HOST_ARGS.length];
    private LookupLocator[] removeLocators = new LookupLocator[INDXS.length];
    private LookupLocator[] expectedLocators
                 = new LookupLocator[addLocators.length-removeLocators.length];

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Retrieves the proxy to the remote
     *  methods of the JoinAdmin interface. Loads the LookupLocator class.
     *  For each host/port pair, creates an instance of the LookupLocator
     *  class. Stores the locators designated for removal in one array; and
     *  the remaining locators in another array.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	/* Create the lookup service */
	super.construct(sysConfig);
	/* retrieve the proxy to the JoinAdmin methods */
	adminProxy = (JoinAdmin) super.getAdminProxy();
	/* load the LookupLocator class and create an instance of this class
         * for each host/port pair; store the locators designated for
         * removal in the removeLocators array and the remaining locators
         * in the expectedLocators array
         */
	Class locObj = Class.forName(CLASS_NAME);
    iLoop:
	for (int i=0,j=0,k=0;i<HOST_ARGS.length;i++) {
	    addLocators[i] = QAConfig.getConstrainedLocator(HOST_ARGS[i],PORT_ARGS[i]);
	    for(int n=0;n<INDXS.length;n++) {
		if (i == INDXS[n]) {
		    removeLocators[j] = addLocators[i];
		    j++;
		    continue iLoop;
		}
	    }
	    expectedLocators[k] = addLocators[i];
	    k++;
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  Retrieves the initial set of locators belonging to the Registrar
     *  and verifies that this set contains the expected set of classes
     *  (currently, should be empty). Invokes addLookupLocators() to add 
     *  to the Registrar the set of locator instances created in construct.
     *  Retrieves the set of LookupLocators belonging to the Registrar
     *  and verifies that this set contains the expected elements. Invokes
     *  removeLookupLocators() to remove the sub-set of locators designated
     *  for removal. Retrieves the set of LookupLocators belonging to the
     *  Registrar and verifies that this set contains the expected elements. 
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	/* retrieve the initial set of LookupLocators; verify that this set
	 * is empty 
	 */
	LookupLocator[] locs = adminProxy.getLookupLocators();
	if (locs.length > 0) {
	    throw new TestException("Unexpected number of INITIAL Lookup "
				  + "Locators returned (" + locs.length + ")");
	}
	/* add the set of locators to the Registrar */
	adminProxy.addLookupLocators(addLocators);
	/* retrieve all locators belonging to the Registrar and verify
	 * that this set contains the expected elements
	 */
	locs = adminProxy.getLookupLocators();
	if (locs.length != addLocators.length) {
	    throw new TestException("locs.length ("+locs.length+
				    ") != addLocators.length ("
				    +addLocators.length+")");
	}
	for(int i=0;i<locs.length;i++) {
	    if (!QATestUtils.objsAreEqual(locs[i],addLocators[i])) {
		throw new TestException("locsClass ("+(locs[i]).getClass()+
					") NOT equal to locatorsClass ("
					+(addLocators[i]).getClass()+")");
	    }
	}
	/* remove the set of locators created in construct from the Registrar */
	adminProxy.removeLookupLocators(removeLocators);
	/* retrieve all locators belonging to the Registrar and verify
	 * that this set contains the expected elements
	 */
	locs = adminProxy.getLookupLocators();
	if (locs.length != expectedLocators.length) {
	    throw new TestException("locs.length ("+locs.length+
				    ") != expectedLocators.length ("
				    +expectedLocators.length+")");
	}
	for(int i=0;i<locs.length;i++) {
	    if (!QATestUtils.objsAreEqual(locs[i],expectedLocators[i])) {
		throw new TestException ("locsClass ("+(locs[i]).getClass()+
					 ") NOT equal to locatorsClass ("
					 +(expectedLocators[i]).getClass()+")");
	    }
	}
    }
}
