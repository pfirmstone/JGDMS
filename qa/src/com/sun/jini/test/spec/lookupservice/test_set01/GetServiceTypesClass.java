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
package com.sun.jini.test.spec.lookupservice.test_set01;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import java.rmi.RemoteException;

/** This class is used to verify that the method getServiceTypes() operates
 *  as expected when a template containing only one of the registered
 *  test service classes is input.
 *
 *  In particular, this test wishes to verify the following statement from
 *  the lookup specification:
 *
 *    "The getServiceTypes method looks at all service items that match the
 *     specified template, and for every service item finds the most specific
 *     type (class or interface) or types the service item is an instance of
 *     that are neither equal to, nor a superclass of, any of the service
 *     types in the template ..."
 *  
 *  The difference between this test class and the other classes that
 *  test getServiceTypes() is in the template that is used for matching
 *  the registered services. This class will input a template containing
 *  only one of the registered service classes; whereas the other test 
 *  classes will employ templates that match on other criteria.
 * 
 *  To perform this test, N instances of each of the test service classes
 *  are created and registered with the lookup service. An array of templates,
 *  each containing one of the registered service classes, is created. For
 *  each of the elements of that array, getServiceTypes() is called with 
 *  that template element as input argument. The array of class types that
 *  is returned is then compared against the expected set of class type
 *  descriptors. 
 *
 *  @see com.sun.jini.qa.harness.TestEnvironment
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class GetServiceTypesClass extends QATestRegistrar {

    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate[] classTmpls;
    private ServiceRegistrar proxy;
    private int nClasses = 0;

    private String[][] expectedTypeDescs  = { 
                                              {TEST_SRVC_CLASSES[1]},
                                              null,
                                              {TEST_SRVC_CLASSES[3]},
                                              null,
                                              {TEST_SRVC_CLASSES[5]},
                                              null,
                                              {TEST_SRVC_CLASSES[7]},
                                              null,
                                              {TEST_SRVC_CLASSES[9]},
                                              null
                                           };

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Creates and registers all the service 
     *  items. Creates an array of ServiceTemplates in which each element 
     *  of the array contains the class type of one of the registered test 
     *  services as well as "wild cards" in all other fields. 
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
        nClasses = super.getNTestClasses();
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll();
	proxy = super.getProxy();

	classTmpls = new ServiceTemplate[nClasses];
	for(int i=0; i<nClasses; i++) {
	    Class c = Class.forName(TEST_SRVC_CLASSES[i]);
	    Class[] classType = {c};
	    classTmpls[i] = new ServiceTemplate(null,classType,null);
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  For each class type of the test services created and registered
     *  during construct, uses the corresponding ServiceTemplate to invoke the
     *  method getServiceTypes(). Verifies that the set of class types returned
     *  matches the expected set of type descriptors.
     */
    public void run() throws Exception {
	Class[] classTypes = null;
	for (int i=0; i<nClasses; i++) {
	    classTypes = proxy.getServiceTypes(classTmpls[i],null);	    
	    if (!QATestUtils.classTypesEqualTypeDescriptors(classTypes,
							    expectedTypeDescs[i]))
	    {
		throw new TestException("For Attribute["+i+ "], "
						+ "not all service type "
						+ "descriptors were returned");
	    }
	}
    }
}
