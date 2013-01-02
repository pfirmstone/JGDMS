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
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import java.rmi.RemoteException;
import net.jini.core.entry.Entry;
import net.jini.core.lease.UnknownLeaseException;
import java.util.HashMap;

/** This class is used to verify that when a lookup is performed
 *  using a template containing only an attribute, the Registrar will 
 *  return from the set of services maintained by the Registrar
 *  a service having the attribute in the lookup template.
 *
 *  An argument is passed into the test harness indicating how the Registrar
 *  selects a service to return: random, round-robin, always-the-same, etc.
 *  This argument is necessary because the Registrar can use different
 *  selection criteria depending on the contents of the template used
 *  to "filter" the services in the Registrar. For example, if the
 *  the template contains only a service type or both a service type and
 *  attribute information, the services that match the template will be
 *  selectected randomly; whereas if the template contains only attribute
 *  information, the Registrar will always return the same matching service
 *  no matter how many times lookup is called (currently, the service that
 *  is returned is the last service to be registered that has that attribute).
 *
 *  Depending on the value of the input argument, the appropriate test will
 *  be employed to verify that the expected service is returned by the
 *  Registrar. Because the selection process may change in the future, the
 *  input argument allows the test employed to be changed by simply
 *  changing the value of the input argument.
 *
 *  @see com.sun.jini.qa.harness.QATest
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 *  @see com.sun.jini.test.spec.lookupservice.QATestUtils
 */
public class ReturnOnLookupByAttr extends QATestRegistrar {

    private ServiceItem[] srvcItems;
    private ServiceRegistration[] srvcRegs;
    private ServiceTemplate[] lookupTmpl;
    private ServiceRegistrar proxy;
    private int nAttrs = 0;
    private int attrsLen;
    private int nClasses = 0;
    private int nInstancesPerClass = 0;

    private static int LOOKUP_FACTOR = 10;
    private static int N_EXPECTED_MATCHES = 1;
    private static int RN = 1; /* Random Test Factor Numerator */
    private static int RD = 2; /* Random Test Factor Denominator */

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Creates all the attribute class instances.
     *  Creates all the service items. Registers all service items -- 
     *  requesting ANY service ID. Creates a set of templates with which 
     *  to perform lookups. Each "lookup template" should map to one of 
     *  the TEST_SRVC_CLASSES; should be created with the service ID field
     *  null, and with an attribute template containing one of the attribute 
     *  instances created earlier. For each test class instance 
     *  corresponding to the lookup template just created, adds that 
     *  template's attribute to the test class.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        int i,j,k;
        int n;
        Entry[] attrs;
        Entry[][] attrArray;
        /* change to n if you want to skip the first n attributes in the set */
        int attrStartIndx = 0; 

	super.construct(sysConfig);

        attrs = super.createAttributes(ATTR_CLASSES);
        nAttrs = super.getNAttrInstances();
        attrArray = new Entry[nAttrs][1];
        for (i=attrStartIndx,k=0;i<nAttrs;i++) {
	    attrArray[k++][0] = attrs[i];
	}
        attrsLen = k;

	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
        nClasses = super.getNTestClasses();
        nInstancesPerClass = super.getNInstancesPerClass();

	srvcRegs = super.registerAll();
	proxy = super.getProxy();

	lookupTmpl = new ServiceTemplate[nClasses];
        k = 0;
	for(j=0; j<nClasses; j++) {
            n = j%attrsLen;
	    lookupTmpl[j] = new ServiceTemplate(null,null,attrArray[n]);
            for (i=0;i<nInstancesPerClass;i++) {
		srvcRegs[k].addAttributes(attrArray[n]);
                k++;
	    }
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  For each class type in the set of templates, performs N simple
     *  lookups where N = 10*the number of registered services that are 
     *  of the type being looked up.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	Object serviceObj = null;
	HashMap[] histogram = new HashMap[nClasses];
	int srvcIndx = 0;
	int selectMethod = super.getSelectMethod();
	for(int j=0; j<nClasses; j++) {
	    histogram[j] = new HashMap();
	    for (int k=0; k<LOOKUP_FACTOR*N_EXPECTED_MATCHES; k++) {
		serviceObj = proxy.lookup(lookupTmpl[j]);
		srvcIndx = QATestUtils.srvcIndxFrmSimpleLookup(serviceObj,
							       srvcItems);
		QATestUtils.srvcHistogram(srvcIndx,histogram[j]);
	    }
	    switch (selectMethod)
	    {
		case QATestRegistrar.SELECT_METHOD_SAME_ONE:
	            /* Verify that the service returned is always the
	             * same service each time lookup() is called.
		     */
	            if (histogram[j].size() != 1) {
	                throw new TestException
		                            ("histogram["+j+"].size() ("
	                                     +histogram[j].size()+") != 1" );
		    }
	            break;
		case QATestRegistrar.SELECT_METHOD_RANDOM:
	                /* Test for randomness. A formal test for randomness
	                 * (such as a test for a Gaussian Distribution) is not
	                 * used. For the purposes of this test, it is enough
	                 * that at least half of the number of possible
	                 * matches have been returned. This is true if the 
	                 * number of "hits" in the histogram is at least half
	                 * the value of expectedNMatchesExact.
		         */
	            if (histogram[j].size()<(RN*expectedNMatchesExact[j])/RD) {
	                throw new TestException
		                 (" histogram["+j+"].size ("
	                           +histogram[j].size()+") < ("
	                           +RN+"*"+expectedNMatchesExact[j]+")/"+RD+
	                          " (= "+(RN*expectedNMatchesExact[j])/RD+")");
		    }
	            break;
		default:
	            throw new TestException
		         ("INVALID Selection Method Value ("+selectMethod+")");
	    }
	}
    }
}
