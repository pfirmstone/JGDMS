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
 *  as expected when an "empty" template is input.
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
 *  "wild cards" in all fields; whereas the other test classes will employ
 *  templates that match on other criteria.
 * 
 *  To perform this test, N instances of each of the test service classes
 *  are created and registered with the lookup service and an empty template
 *  is created. For each of the service classes, getServiceTypes() is 
 *  called with the template as input argument. The array of class types that
 *  is returned is then compared against the expected set of class type
 *  descriptors. 
 *
 *  @see com.sun.jini.qa.harness.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class GetServiceTypesEmpty extends QATestRegistrar {

    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate emptyTmpl;
    private ServiceRegistrar proxy;

    private String[] expectedTypeDescs = { TEST_SRVC_CLASSES[0],
                                           TEST_SRVC_CLASSES[1],
                                           TEST_SRVC_CLASSES[2],
                                           TEST_SRVC_CLASSES[3],
                                           TEST_SRVC_CLASSES[4],
                                           TEST_SRVC_CLASSES[5],
                                           TEST_SRVC_CLASSES[6],
                                           TEST_SRVC_CLASSES[7],
                                           TEST_SRVC_CLASSES[8],
                                           TEST_SRVC_CLASSES[9],
                                          "com.sun.jini.reggie.RegistrarProxy"
                                         };

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Creates and registers all the service
     *  items; and creates an "empty" ServiceTemplate (all null fields).
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll();
	proxy = super.getProxy();
	emptyTmpl = new ServiceTemplate(null,null,null);
        return this;
    }

    /** Executes the current QA test.
     *
     *  Using the empty ServiceTemplate created during construct, invokes the
     *  method getServiceTypes(). Verifies that the set of class types returned
     *  matches the expected set of type descriptors.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	Class[] classTypes = null;
	classTypes = proxy.getServiceTypes(emptyTmpl,null);
	if (!QATestUtils.classTypesEqualTypeDescriptors(classTypes,
							expectedTypeDescs))
	{
	    throw new TestException("not ALL service type "
				    + "descriptors were returned");
	}
    }
}
