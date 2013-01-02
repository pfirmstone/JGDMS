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

import com.sun.jini.test.share.AttributesUtil;
import com.sun.jini.test.share.JoinAdminUtil;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.admin.JoinAdmin;
import net.jini.discovery.LookupDiscoveryService;
import net.jini.lookup.entry.ServiceControlled;

import net.jini.core.entry.Entry;

import java.rmi.RemoteException;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully add a new set of attributes to the its current set of
 * attributes.
 *
 */
public class AddLookupAttributes extends AbstractBaseTest {
    Entry[] newAttributeSet = null;
    private Entry[] expectedAttributes = null;

    /** Constructs and returns the set of attributes to add (can be
     *  overridden by sub-classes)
     */
    Entry[] getTestAttributeSet() {
        return new Entry[] {new AttributesUtil.TestAttr00("AAA","AAA","AAA"),
                            new AttributesUtil.TestAttr00("BBB","BBB","BBB"),
                            new AttributesUtil.TestAttr00("CCC","CCC","CCC")
                           };
    }

    /** Constructs and returns the set of attributes with which the service
     *  was initially configured
     */
    Entry[] getConfigAttributeSet() {
        return new Entry[] 
	    {AttributesUtil.getServiceInfoEntryFromConfig(getConfig()),
	     AttributesUtil.getBasicServiceTypeFromConfig(getConfig())
	    };
    }

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then constructs the
     *  set of attributes that should be expected after adding a new set
     *  to the service.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        Entry[] configAttributes = getConfigAttributeSet();
        newAttributeSet = getTestAttributeSet();
        boolean serviceControlled = false;
        if( (newAttributeSet != null) && (newAttributeSet.length > 0) ) {
            for(int i=0;i<newAttributeSet.length;i++) {
                if(newAttributeSet[i] instanceof ServiceControlled) {
                    serviceControlled = true;
                    break;
                }
            }
        }
        /* Construct the expected attributes set */
        expectedAttributes = configAttributes;
        if( (!serviceControlled) && (newAttributeSet != null) ) {
            /* Concatenate the original attributes with the attributes
             * expected to be added 
             */
            expectedAttributes = 
                     new Entry[configAttributes.length+newAttributeSet.length];
            for(int i=0; i<configAttributes.length; i++) {
                expectedAttributes[i] = configAttributes[i];
            }
            for(int i=0;i<newAttributeSet.length;i++) {
                expectedAttributes[i + configAttributes.length]
                                                          = newAttributeSet[i];
            }
        }
        if(newAttributeSet == null) {
            logger.log(Level.FINE, "expectedAttributes = NullPointerException");
        } else if(serviceControlled) {
            logger.log(Level.FINE, 
		       "expectedAttributes = "
		     + "SecurityException (ServiceControlled)");
        } else {//newAttributeSet != null
            AttributesUtil.displayAttributeSet(expectedAttributes, 
					      "expectedAttrs",
					      Level.FINE);
        }
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, adds to the service's current set of attributes
     *     a new set of attributes
     *  3. Determines if the set of attributes retrieved through the admin is
     *     equivalent to the expected set of attributes .
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start service "
				    +serviceName);
        }
	JoinAdmin joinAdmin = JoinAdminUtil.getJoinAdmin(discoverySrvc);
	Entry[] oldAttributes = joinAdmin.getLookupAttributes();
	AttributesUtil.displayAttributeSet(oldAttributes, 
					   "oldAttributes", 
					   Level.FINE);
	AttributesUtil.displayAttributeSet(newAttributeSet,
					   "addAttributes", 
					   Level.FINE);
	joinAdmin.addLookupAttributes(newAttributeSet);
	Entry[] newAttributes = joinAdmin.getLookupAttributes();
	AttributesUtil.displayAttributeSet(newAttributes,
					   "newAttributes", 
					   Level.FINE);
	if (!AttributesUtil.compareAttributeSets(expectedAttributes, 
						 newAttributes, 
						 Level.FINE))
	{
	    throw new TestException("Expected and new attributes not equal");
	}
    }
}


