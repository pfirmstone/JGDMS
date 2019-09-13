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
import net.jini.core.entry.Entry;
import java.rmi.RemoteException;
import java.io.IOException;

/** This class is used to perform a simple verification of the following
 *  methods from the JoinAdmin interface: getLookupAttributes() and
 *  addLookupAttributes(). This class simply adds a number of attribute
 *  class instances to the Registrar and then retrieves all attributes
 *  belonging to the Registrar and verifies that the original set of 
 *  attributes belonging to the Registrar was indeed modified.
 *
 *  @see net.jini.admin.JoinAdmin
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class SimpleAddLookupAttributes extends QATestRegistrar {

    private static final String[] INITIAL_ATTRS
     = { "net.jini.lookup.entry.ServiceInfo",
         "org.apache.river.lookup.entry.BasicServiceType"};

    private JoinAdmin adminProxy;
    private String[] expectedAttrs;
    private Entry[] attrInstances = new Entry[ATTR_CLASSES.length];

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Retrieves the proxy to the remote
     *  methods of the JoinAdmin interface. Populates an array with the
     *  names of both the attribute classes expected to initially belong to
     *  the Registrar and all of the test attribute classes. Loads each 
     *  test attribute class and creates a non-initialized (null fields) 
     *  instance of each loaded class.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	/* Create the lookup service */
	super.construct(sysConfig);
	/* retrieve the proxy to the JoinAdmin methods */
	adminProxy = (JoinAdmin) super.getAdminProxy();
	/* populate the expectedAttrs array with the names of both the
         * attribute classes expected to initially belong to the Registrar
         * and all of the test attribute classes
         */
        expectedAttrs = new String[INITIAL_ATTRS.length+ATTR_CLASSES.length];
        for(int i=0;i<INITIAL_ATTRS.length;i++) {
            expectedAttrs[i] = INITIAL_ATTRS[i];
	}
        for(int i=INITIAL_ATTRS.length,j=0;i<expectedAttrs.length;i++,j++){
            expectedAttrs[i] = ATTR_CLASSES[j];
	}
	/* load each attribute class and create a non-initialized (null 
         * fields) instance of each loaded class
         */
        for (int i=0;i<ATTR_CLASSES.length;i++) {
	    Class loadedAttrObj = Class.forName(ATTR_CLASSES[i]);
	    attrInstances[i] = (Entry)loadedAttrObj.getDeclaredConstructor().newInstance();
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  Retrieves the initial set of attribute classes belonging to the
     *  Registrar and then verifies that this set contains the expected
     *  set of classes. Invokes addLookupAttributes() to add to the 
     *  Registrar the set of attribute instances created in construct.
     *  Retrieves the set of attribute classes belonging to the Registrar
     *  and verifies that this set contains the expected classes.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	/* retrieve the current set of attribute classes belonging to the
	 * Registrar; verify that this set contains the expected classes 
	 */
	Entry[] attrs = adminProxy.getLookupAttributes();
	Class[] classTypes = new Class[attrs.length];
	for(int i=0;i<attrs.length;i++) {
	    classTypes[i] = (attrs[i]).getClass();
	}
	if (!QATestUtils.classTypesEqualTypeDescriptors(classTypes,
							INITIAL_ATTRS))
	{
	    throw new TestException("The set of Lookup Attributes returned "
				  + "is NOT equal to the set expected");
	}
	/* invoke addLookupAttributes() to add to the Registrar the set of
	 * attribute instances created in construct
	 */
	adminProxy.addLookupAttributes(attrInstances);
	/* retrieve the set of attribute classes belonging to the Registrar
	 * and verify that this set contains the expected classes
	 */

	attrs = adminProxy.getLookupAttributes();
	classTypes = new Class[attrs.length];
	for(int i=0;i<attrs.length;i++) {
	    classTypes[i] = (attrs[i]).getClass();
	}
	if (!QATestUtils.classTypesEqualTypeDescriptors(classTypes,
							expectedAttrs)) 
	{
	    throw new TestException("The set of Lookup Attributes returned "
				  + "is NOT equal to the set expected");
	}
    }
}
