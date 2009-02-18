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

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import net.jini.admin.JoinAdmin;
import net.jini.discovery.LookupDiscoveryService;
import net.jini.lookup.entry.ServiceControlled;

import net.jini.core.entry.Entry;

import java.rmi.RemoteException;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully modify its current set of attributes.
 *
 * This test adds one or more "well-known" attributes to the service's 
 * current set and then attempts to modify those attributes.
 *
 */
public class ModifyLookupAttributes extends AbstractBaseTest {

    Entry[] attrSetTmpls    = null;
    Entry[] newAttributeSet = null;
    private Entry[] expectedAttributes = null;

    /** Constructs and returns the set of attributes to initially add (can be
     *  overridden by sub-classes)
     */
    Entry[] getInitialAttributeSet() {
        return new Entry[] {new AttributesUtil.TestAttr00("AAA","AAA","AAA"),
                            new AttributesUtil.TestAttr00("BBB","BBB","BBB")
                           };
    }

    /** Constructs and returns the set of attributes that are used in the
     *  matching process for the attributes that will be modified (can be
     *  overridden by sub-classes)
     */
    Entry[] getAttributeSetTemplates() {
        return getInitialAttributeSet();
    }

    /** Constructs and returns the set of attributes that are used to modify
     *  the desired set of attributes (can be overridden by sub-classes)
     */
    Entry[] getTestAttributeSet() {
        return new Entry[] {new AttributesUtil.TestAttr00("ZZZ","ZZZ","ZZZ"),
                            new AttributesUtil.TestAttr00("YYY","YYY","YYY")
                           };
    }

    /** Constructs and returns the set of attributes that are expected to
     *  be remaining from the just-added attributes after those just-added
     *  attributes have been modified in the desired way (can be overridden
     *  by sub-classes)
     */
    Entry[] getAttributesAfterMod() {
        return getTestAttributeSet();
    }

    /** Constructs and returns the set of attributes with which the service
     *  was initially configured
     */
    Entry[] getConfigAttributeSet() {
        return new Entry[] {AttributesUtil.getServiceInfoEntryFromConfig(getConfig()),
                            AttributesUtil.getBasicServiceTypeFromConfig(getConfig())
                           };
    }

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then constructs the
     *  set of attributes that should be expected after adding and then
     *  modifying a new set of attributes.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        Entry[] configAttributes = getConfigAttributeSet();
        newAttributeSet = getTestAttributeSet();

        attrSetTmpls = getAttributeSetTemplates();
        boolean serviceControlled = false;
        if( (attrSetTmpls != null) && (attrSetTmpls.length > 0) ) {
            for(int i=0;i<attrSetTmpls.length;i++) {
                if(attrSetTmpls[i] instanceof ServiceControlled) {
                    serviceControlled = true;
                    break;
                }
            }
        }
        /* Construct the expected attributes set */
        expectedAttributes = configAttributes;
        if(!serviceControlled) {
            Entry[] attrsAfterMod = getAttributesAfterMod();
            if(attrsAfterMod != null) {
                /* Concatenate the original attributes with the attributes
                 * expected to be remaning after modifying the just-added
                 * attributes
                 */
                expectedAttributes = 
                     new Entry[configAttributes.length+attrsAfterMod.length];
                for(int i=0;i<configAttributes.length;i++) {
                    expectedAttributes[i] = configAttributes[i];
                }
                for(int i=0;i<attrsAfterMod.length;i++) {
                    expectedAttributes[i+configAttributes.length]
                                                          = attrsAfterMod[i];
                }
            }//endif(attrsAfterMod != null)
        }//endif(!serviceControlled)
        if( (attrSetTmpls == null) || (newAttributeSet == null) ) {
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
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, adds a set of attributes to the service's
     *     current set
     *  3. Constructs the set of attribute template(s) that are used to
     *     select (match) the desired sub-set of the just-added attributes
     *     that will be modified
     *  4. Through the admin, modifies the selected set of just-added
     *     attributes
     *  5. Determines if the set of attributes just modified are 
     *     equivalent to the expected set of attributes .
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start service "
				  + serviceName);
        }
	JoinAdmin joinAdmin = JoinAdminUtil.getJoinAdmin(discoverySrvc);

	Entry[] initialAttributes = getInitialAttributeSet();
	joinAdmin.addLookupAttributes(initialAttributes);

	Entry[] oldAttributes = joinAdmin.getLookupAttributes();
	AttributesUtil.displayAttributeSet(oldAttributes,
					   "oldAttributes",
					   Level.FINE);
	Entry[] attrSetTmpls = getAttributeSetTemplates();
	AttributesUtil.displayAttributeSet(attrSetTmpls,
					   "attrSetTmpls",
					   Level.FINE);
	AttributesUtil.displayAttributeSet(newAttributeSet,
					   "newAttributeSet",
					   Level.FINE);
	joinAdmin.modifyLookupAttributes(attrSetTmpls,newAttributeSet);
	Entry[] newAttributes = joinAdmin.getLookupAttributes();
	AttributesUtil.displayAttributeSet(newAttributes,
					   "newAttributes",
					   Level.FINE);
	if (!AttributesUtil.compareAttributeSets(expectedAttributes,
						 newAttributes,
						 Level.FINE))
	{
	    throw new TestException("Attribute sets not equal");
	}
    }
}


