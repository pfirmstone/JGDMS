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
import net.jini.core.entry.Entry;

import java.rmi.RemoteException;

import java.util.HashSet;
import java.util.Iterator;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully return the attributes with which it is currently configured.
 *
 */
public class GetLookupAttributes extends AbstractBaseTest {

    private Entry[] expectedAttributes = null;

    /** Constructs and returns the set of attributes with which the service
     *  is expected to be configured (can be overridden by sub-classes)
     */
    Entry[] getTestAttributeSet() {
        return new Entry[] {AttributesUtil.getServiceInfoEntryFromConfig(getConfig()),
                            AttributesUtil.getBasicServiceTypeFromConfig(getConfig())
                           };
    }

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service, and then constructs the
     *  set of attributes with which the service is expected to be 
     *  configured.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        expectedAttributes = getTestAttributeSet();
        AttributesUtil.displayAttributeSet(expectedAttributes,
                                           "expectedAttributes",
                                           Level.FINE);
        return this;
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, retrieves the set of attributes with which
     *     the service is currently configured.
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
	Entry[] curAttributes = joinAdmin.getLookupAttributes();
	AttributesUtil.displayAttributeSet(curAttributes, 
					   "curAttributes", 
					   Level.FINE);
	if (!AttributesUtil.compareAttributeSets(expectedAttributes,
						 curAttributes,
						 Level.FINE))
	{
	    throw new TestException("attribute sets not equal");
	}
    }
}


