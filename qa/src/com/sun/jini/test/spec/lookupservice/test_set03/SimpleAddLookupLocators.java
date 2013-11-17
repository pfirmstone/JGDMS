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
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import net.jini.admin.JoinAdmin;
import net.jini.core.discovery.LookupLocator;
import java.rmi.RemoteException;

/** This class is used to perform a simple verification of the following
 *  methods from the JoinAdmin interface: getLookupLocators() and
 *  addLookupLocators(). This class simply adds a number of LookupLocator
 *  class instances to the Registrar and then retrieves all locators
 *  belonging to the Registrar and verifies that the set returned contains
 *  the expected locators.
 *
 *  @see net.jini.admin.JoinAdmin
 *  @see net.jini.core.discovery.LookupLocator
 *  @see com.sun.jini.qa.harness.TestEnvironment
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class SimpleAddLookupLocators extends QATestRegistrar {

    /* Name of the class to load and instantiate for various arguments */
    private static final String CLASS_NAME
                                = "net.jini.core.discovery.LookupLocator";
    /* Arguments to the constructors of the various LookupLocator classes */
    private static final String[] HOST_ARGS =
        { "raglan", "thoth",  "raistlin","jellybean","mufti",
          "cocolat","terrier","marvin",  "savoy",    "recycle","baji" };
    private static final int[] PORT_ARGS =
        { 9990,9991,9992,9993,9994,9995,9996,9997,9998,9999,10000 };

    private JoinAdmin adminProxy;
    private LookupLocator[] locators = new LookupLocator[HOST_ARGS.length];

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Retrieves the proxy to the remote
     *  methods of the JoinAdmin interface. Loads the LookupLocator class.
     *  For each host/port pair, creates an instance of the LookupLocator
     *  class.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	/* Create the lookup service */
	super.construct(sysConfig);
	/* retrieve the proxy to the JoinAdmin methods */
	adminProxy = (JoinAdmin) super.getAdminProxy();
	/* load the LookupLocator class and create an instance of this class
         * for each host/port pair
         */
	Class locObj = Class.forName(CLASS_NAME);
	for (int i=0;i<HOST_ARGS.length;i++) {
	    locators[i] = QAConfig.getConstrainedLocator(HOST_ARGS[i],PORT_ARGS[i]);
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
     *  and verifies that this set contains the expected elements.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	/* retrieve the initial set of LookupLocators; verify that this set
	 * is empty 
	 */
	LookupLocator[] locs = adminProxy.getLookupLocators();
	if (locs.length > 0) {
	    throw new TestException("Unexpected number of INITIAL Lookup Locators "
				    + "returned (" + locs.length + ")");
	}
	/* add the set of locators to the Registrar */
	adminProxy.addLookupLocators(locators);
	/* retrieve all locators belonging to the Registrar and verify
	 * that this set contains the expected elements
	 */
	locs = adminProxy.getLookupLocators();
	if (locs.length != locators.length) {
	    throw new TestException("locs.length ("+locs.length
				    + ") != locators.length ("
				    + locators.length+")");
	}
	for(int i=0;i<locs.length;i++) {
	    if (!QATestUtils.objsAreEqual(locs[i],locators[i])) {
		throw new TestException("locsClass ("+(locs[i]).getClass()
					+ ") NOT equal to locatorsClass ("
					+ (locators[i]).getClass()+")");
	    }
	}
    }  
}
