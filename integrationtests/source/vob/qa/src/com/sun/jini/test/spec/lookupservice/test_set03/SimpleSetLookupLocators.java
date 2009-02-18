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
package com.sun.jini.test.spec.lookupservice.test_set03;
import com.sun.jini.qa.harness.QAConfig;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import net.jini.admin.JoinAdmin;
import net.jini.core.discovery.LookupLocator;
import java.rmi.RemoteException;

/** This class is used to perform a simple verification of the following
 *  methods from the JoinAdmin interface: getLookupLocators(),
 *  addLookupLocators() and setLookupLocators(). This class first
 *  retrieves the initial set of locators belonging to the Registrar and
 *  verifies that the set contains the expected elements (currently, that
 *  set should be empty). A new set of LookupLocators is then added to
 *  the Registrar and the locators belonging to the Registrar are again
 *  retrieved and verified. This class then sets (replaces) the current
 *  set of locators belonging to the Registrar with a new set of locators.
 *  It then retrieves the set of all locators belonging to the Registrar 
 *  and verifies that the set contains the expected elements.
 *
 *  @see net.jini.admin.JoinAdmin
 *  @see net.jini.core.discovery.LookupLocator
 *  @see com.sun.jini.test.spec.lookupservice.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class SimpleSetLookupLocators extends QATestRegistrar {

    /* Name of the class to load and instantiate for the various arguments */
    private static final String CLASS_NAME
                                          = "net.jini.core.discovery.LookupLocator";
    /* Arguments to the constructor of the various LookupLocator classes */
    private static final String[] ADD_HOSTS =
    { "raglan", "thoth",  "raistlin","jellybean","mufti",
      "cocolat","terrier","marvin",  "savoy",    "recycle","baji" };
    private static final int[] ADD_PORTS =
    { 9990,9991,9992,9993,9994,9995,9996,9997,9998,9999,10000 };

    private static final String[] SET_HOSTS =
    { "raglan0", "thoth0",  "raistlin0","jellybean0","mufti0","cocolat0" };
    private static final int[] SET_PORTS =
    { 8880,8881,8882,8883,8884,8885 };

    private JoinAdmin adminProxy;
    private LookupLocator[] addLocators = new LookupLocator[ADD_HOSTS.length];
    private LookupLocator[] setLocators = new LookupLocator[SET_HOSTS.length];

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Retrieves the proxy to the remote
     *  methods of the JoinAdmin interface. Loads the LookupLocator class.
     *  For each host/port to be added, creates an instance of the
     *  LookupLocator class. For each host/port to be set, creates another
     *  instance of the LookupLocator class.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig sysConfig) throws Exception {
	/* Create the lookup service */
	super.setup(sysConfig);
	/* retrieve the proxy to the JoinAdmin methods */
	adminProxy = (JoinAdmin) super.getAdminProxy();
	/* load the LookupLocator class */
	Class locObj = Class.forName(CLASS_NAME);
	/* for each host/port pair that is to be added to the Registrar,
	 * create an instance of the class
	 */
	for (int i=0,j=0;i<ADD_HOSTS.length;i++) {
	    addLocators[i] = QAConfig.getConstrainedLocator(ADD_HOSTS[i],ADD_PORTS[i]);
	}
	/* for each host/port pair that is to be set in the Registrar,
             * create an instance of the class
             */
	for (int i=0,j=0;i<SET_HOSTS.length;i++) {
	    setLocators[i] = QAConfig.getConstrainedLocator(SET_HOSTS[i],SET_PORTS[i]);
	}
    }

    /** Executes the current QA test.
     *
     *  Retrieves the initial set of locators belonging to the Registrar
     *  and verifies that this set contains the expected set of classes
     *  (currently, should be empty). Invokes addLookupLocators() to add 
     *  to the Registrar the set of locator instances created in setup.
     *  Retrieves the set of LookupLocators belonging to the Registrar
     *  and verifies that this set contains the expected elements. Invokes
     *  setLookupLocators() to replace the current set of locators in
     *  Registrar with the the set of locators created in setup. Retrieves
     *  the set of LookupLocators belonging to the Registrar and verifies
     *  that this set contains the expected elements. 
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
	    throw new TestException("locs.length (" + locs.length 
				    + ") != addLocators.length (" 
				    + addLocators.length+")");
	}
	for(int i=0;i<locs.length;i++) {
	    if (!QATestUtils.objsAreEqual(locs[i],addLocators[i])) {
		throw new TestException("locsClass (" + (locs[i]).getClass()
					+ ") NOT equal to locatorsClass ("
					+ (addLocators[i]).getClass() + ")");
	    }
	}
	/* replace (set) the set of locators added above with the second set
	 * of locators created in setup
	 */
	adminProxy.setLookupLocators(setLocators);
	/* retrieve all locators belonging to the Registrar and verify
	 * that this set contains the expected elements
	 */
	locs = adminProxy.getLookupLocators();
	if (locs.length != setLocators.length) {
	    throw new TestException("locs.length (" + locs.length
				    + ") != setLocators.length ("
				    + setLocators.length + ")");
	}
	for(int i=0;i<locs.length;i++) {
	    if (!QATestUtils.objsAreEqual(locs[i],setLocators[i])) {
		throw new TestException("locsClass (" + (locs[i]).getClass()
					+ ") NOT equal to locatorsClass ("
					+ (setLocators[i]).getClass() + ")");
	    }
	}
    }
}
