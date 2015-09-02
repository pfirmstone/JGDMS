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
package org.apache.river.test.spec.lookupservice.test_set01;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;
import org.apache.river.qa.harness.TestException;

import org.apache.river.test.spec.lookupservice.QATestRegistrar;
import org.apache.river.test.spec.lookupservice.QATestUtils;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.entry.Entry;
import net.jini.core.lease.UnknownLeaseException;
import java.rmi.RemoteException;

/** This class is used to verify that the method getEntryClasses() operates
 *  as expected when an "empty" template is input.
 *
 *  In particular, this test wishes to verify the following statement from
 *  the lookup specification:
 *
 *    "The getEntryClasses method looks at all service items that match the
 *     specified template, finds every entry (among those service items)
 *     that either doesn't match any entry templates or is a subclass of
 *     at least one matching entry template, and returns the set of the 
 *     (most specific) classes of those entries ..."
 *  
 *  The difference between this test class and the other classes that
 *  test getEntryClasses() is in the template that is used for matching
 *  the registered services. This class will input a template containing
 *  "wild cards" in all fields; whereas the other test classes will employ
 *  templates that match on other criteria.
 * 
 *  To perform this test, N instances of each of the test service classes
 *  are created and registered with the lookup service. Each of the test
 *  attribute classes are instantiated and added to each of the services.
 *  That is, each of the N instances of the M services has assigned to it
 *  L different attributes. An empty template is created. For each of the
 *  service classes, getEntryClasses() is called with the empty template 
 *  as input argument. The array of class types that is returned is then
 *  compared against the expected set of class type descriptors. 
 *
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class GetEntryClassesEmpty extends QATestRegistrar {

    private ServiceItem[] srvcItems ;
    private ServiceRegistration[] srvcRegs ;
    private ServiceTemplate emptyTmpl;
    private ServiceRegistrar proxy;

    private Entry[] attrEntries;
    private Entry[][] attrs;
    private int attrsLen;

    /* Change to n if you want to skip the first n attributes in the set */
    private int attrStartIndx = 0; 

    private String[] expectedAttrTypeDescs = { ATTR_CLASSES[0],
                                               ATTR_CLASSES[1],
                                               ATTR_CLASSES[2],
                                               ATTR_CLASSES[3],
                                               ATTR_CLASSES[4],
                                               ATTR_CLASSES[5],
                                               ATTR_CLASSES[6],
                                               ATTR_CLASSES[7],
                                               ATTR_CLASSES[8],
                                               ATTR_CLASSES[9],
                                               ATTR_CLASSES[10],
                                               ATTR_CLASSES[11],
                                               ATTR_CLASSES[12]
                                             };

    private String[] expectedTypeDescs;

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Instantiates all attribute classes; and
     *  populates an array with those attribute objects. Creates and registers
     *  all the service items; adds each attribute object to each registered
     *  service. Creates an "empty" ServiceTemplate containing "wild cards" 
     *  in all of its fields. Retrieves all pre-existing class types; builds
     *  the set of expected class types from the pre-existing set and the
     *  set expected from the addition of the attribute classes. Adds each
     *  attribute to each service item.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        int i,j,k;

	super.construct(sysConfig);

        /* create the array of Entry arrays holding the attributes */
        attrEntries = super.createAttributes(ATTR_CLASSES);
        attrs = new Entry[attrEntries.length][1];
        for (i=attrStartIndx,k=0;i<attrEntries.length;i++) {
	    attrs[k++][0] = attrEntries[i];
        }
        attrsLen = k;

        /* create and register all of the service items */
	srvcItems = super.createServiceItems(TEST_SRVC_CLASSES);
	srvcRegs = super.registerAll();
	proxy = super.getProxy();
        /* create an "empty" template */
	emptyTmpl = new ServiceTemplate(null,null,null);

        /* retrieve all pre-existing class types; add them to expected set */
        Class[] curClassTypes = null;
	curClassTypes = proxy.getEntryClasses(emptyTmpl);
	expectedTypeDescs
	    = new String[(curClassTypes.length+expectedAttrTypeDescs.length)];
	for(i=0;i<curClassTypes.length;i++){
	    expectedTypeDescs[i] = (curClassTypes[i]).getName();
	}
	for(i=curClassTypes.length,j=0;i<expectedTypeDescs.length;i++,j++){
	    expectedTypeDescs[i] = expectedAttrTypeDescs[j];
	}

        /* add each attribute to each service item */
        for(i=0; i<srvcRegs.length; i++) {
	    for(j=0; j<attrsLen; j++) {
                    srvcRegs[i].addAttributes(attrs[j]);
	    }
	}
        return this;
    }

    /** Executes the current QA test.
     *
     *  Using the empty ServiceTemplate created during construct, invokes the
     *  method getServiceTypes(). Verifies that the set of class types returned
     *  matches the expected set of type descriptors.
     */
    public void run() throws Exception {
	Class[] classTypes = null;
	classTypes = proxy.getEntryClasses(emptyTmpl);
	if (!QATestUtils.classTypesEqualTypeDescriptors(classTypes,
							expectedTypeDescs))
	{
	    throw new TestException("not ALL service type descriptors "
				    + "were returned");
	}
    }
}
