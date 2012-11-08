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
import com.sun.jini.constants.VersionConstants;
import net.jini.admin.JoinAdmin;
import net.jini.core.entry.Entry;
import java.rmi.RemoteException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** This class is used to perform a simple verification of the following
 *  methods from the JoinAdmin interface: getLookupAttributes(),
 *  addLookupAttributes() and modifyLookupAttributes(). This class first
 *  retrieves the initial set of attributes belonging to the Registrar
 *  and verifies that that set contains the expected attributes. A new set
 *  of attributes is then added to the Registrar and all attributes are
 *  again retrieved and verified against the expected set of attributes.
 *  Finally, the set of attributes that were added are then modified and
 *  all attributes belonging to the Registrar are once again retrieved and
 *  verified against the expected set of attributes.
 *
 *  @see net.jini.admin.JoinAdmin
 *  @see com.sun.jini.qa.harness.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class SimpleModifyLookupAttributes extends QATestRegistrar {

    /* Names of the attribute classes initially belonging to the Registrar */
    private static final String[] INITIAL_ATTRS
                                    = { "net.jini.lookup.entry.ServiceInfo",
                                        "com.sun.jini.lookup.entry.BasicServiceType"};
    /* Arguments to the constructors of the initial attribute classes */
    private static final Object[][] INITIAL_ATTRS_ARGS =
    { 
      {"Lookup", "Apache River", "Apache foundation",
	    VersionConstants.SERVER_VERSION, "", ""},
      {"Lookup"},
    };
    /* Argument classes to the constructors of the initial attribute classes */
    private static final Class[][] INITIAL_ATTRS_CLASSES =
    { 
      {String.class, String.class, String.class,
       String.class, String.class, String.class},
      {String.class},
    };

    private JoinAdmin adminProxy;
    private Entry[] initialAttrs = new Entry[INITIAL_ATTRS.length];
    private Entry[] addAttrs;
    private Entry[] modAttrs;
    private Entry[] expectedAddAttrs;
    private Entry[] expectedModAttrs;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Retrieves the proxy to the remote
     *  methods of the JoinAdmin interface. Loads each of the initial
     *  attribute classes and creates an initialized instance of each 
     *  loaded class; setting the field values to the values of the fields 
     *  of the initial attributes belonging to the Registrar. Loads each of 
     *  the test attribute classes and creates an initialized (non-null
     *  fields) instance of each loaded class. Creates another set of
     *  instances of each attribute class (initialized, but different from
     *  the first set). Populates an array with the references of both the
     *  initial attribute instances and the attribute instances that will 
     *  be added to the Registrar. Populates another array with the 
     *  references of both the initial attribute instances and the attribute 
     *  instances that will be used to modify the added attributes.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig sysConfig) throws Exception {
	/* create the lookup service */
	super.setup(sysConfig);
	/* retrieve the proxy to the JoinAdmin methods */
	adminProxy = (JoinAdmin) super.getAdminProxy();

	/* load each of the initial attribute classes and create an 
         * initialized (non-null fields) instance of each loaded class
         */
        for (int i=0;i<INITIAL_ATTRS.length;i++) {
	    Class loadedAttrObj = Class.forName(INITIAL_ATTRS[i]);
	    Class[] argTypes = INITIAL_ATTRS_CLASSES[i];
	    Constructor con = loadedAttrObj.getConstructor(argTypes);
	    initialAttrs[i] = (Entry) con.newInstance(INITIAL_ATTRS_ARGS[i]);
	}
	/* load each attribute class and create an initialized (non-null 
         * fields) instance of each loaded class; then create another set
         * of instances (initialized but different from the first set) of 
         * each attribute class
         */
        addAttrs = super.createAttributes(ATTR_CLASSES);
        modAttrs = super.createModifiedAttributes(ATTR_CLASSES);
	/* populate the expectedAddAttrs array with the references of both
         * the initial attribute instances and the attribute instances
         * that will be added to the Registrar
         */
        expectedAddAttrs = new Entry[initialAttrs.length+addAttrs.length];
        for(int i=0;i<initialAttrs.length;i++) {
            expectedAddAttrs[i] = initialAttrs[i];
	}
        for(int i=initialAttrs.length,j=0;i<expectedAddAttrs.length;i++,j++){
            expectedAddAttrs[i] = addAttrs[j];
	}
	/* populate the expectedModAttrs array with the references of both
         * the initial attribute instances and the attribute instances
         * that will be used to modify the added attributes
         */
        expectedModAttrs = new Entry[initialAttrs.length+modAttrs.length];
        for(int i=0;i<initialAttrs.length;i++) {
            expectedModAttrs[i] = initialAttrs[i];
	}
        for(int i=initialAttrs.length,j=0;i<expectedModAttrs.length;i++,j++){
            expectedModAttrs[i] = modAttrs[j];
	}
    }

    /** Executes the current QA test.
     *
     *  Retrieves the initial set of attribute classes belonging to the
     *  Registrar and then verifies that this set contains the expected
     *  set of classes. Invokes addLookupAttributes() to add to the 
     *  Registrar the set of attribute instances created in setup; and
     *  then retrieves all attributes belonging to the Registrar and
     *  verifies that the retrieved set contains the expected classes.
     *  Invokes modifyLookupAttributes() to modify the first set of
     *  attributes added to the Registrar with the second set of attributes
     *  created in setup; and then retrieves all attributes belonging to 
     *  the Registrar and verifies that the retrieved set contains the
     *  expected classes.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	/* retrieve the initial set of attribute classes belonging to the
	 * Registrar; verify that this set contains the expected classes 
	 */
	Entry[] attrs = adminProxy.getLookupAttributes();
	if (attrs.length != initialAttrs.length) {
	    throw new TestException("attrs.length ("+attrs.length+
				    ") != initialAttrs.length ("
				    +initialAttrs.length+")");
	}
	for(int i=0;i<attrs.length;i++) {
	    if (!QATestUtils.objsAreEqual(attrs[i],initialAttrs[i])) {
		throw new TestException("attrsClass ("+(attrs[i]).getClass()+
					") NOT equal to initialAttrsClass ("
					+(initialAttrs[i]).getClass()+")");
	    }
	}
	/* invoke addLookupAttributes() to add to the Registrar the first 
	 * set of attribute instances created in setup
	 */
	adminProxy.addLookupAttributes(addAttrs);
	/* retrieve the current set of attribute classes belonging to the
	 * Registrar; verify that this set contains the expected classes 
	 */
	attrs = adminProxy.getLookupAttributes();
	if (attrs.length != expectedAddAttrs.length) {
	    throw new TestException("attrs.length ("+attrs.length+
				    ") != expectedAddAttrs.length ("
				    +expectedAddAttrs.length+")");
	}
	for(int i=0;i<attrs.length;i++) {
	    if (!QATestUtils.objsAreEqual(attrs[i],expectedAddAttrs[i])) {
		throw new TestException("attrsClass ("+(attrs[i]).getClass()+
					") NOT equal to expectedAddAttrsClass ("
	                                +(expectedAddAttrs[i]).getClass()+")");
	    }
	}
	/* invoke modifyLookupAttributes() to modify the first set of
	 * attributes added to the Registrar with the second set of attributes
	 * created in setup
	 */
	adminProxy.modifyLookupAttributes(addAttrs,modAttrs);
	/* retrieve the current set of attribute classes belonging to the
	 * Registrar; verify that this set contains the expected classes 
	 */
	attrs = adminProxy.getLookupAttributes();
	if (attrs.length != expectedModAttrs.length) {
	    throw new TestException("attrs.length ("+attrs.length+
				    ") != expectedModAttrs.length ("
				    +expectedModAttrs.length+")");
	}
	for(int i=0;i<attrs.length;i++) {
	    if (!QATestUtils.objsAreEqual(attrs[i],expectedModAttrs[i])) {
		throw new TestException("attrsClass ("+(attrs[i]).getClass()+
					") NOT equal to expectedModAttrsClass ("
	                                +(expectedModAttrs[i]).getClass()+")");
	    }
	}
    }
}
