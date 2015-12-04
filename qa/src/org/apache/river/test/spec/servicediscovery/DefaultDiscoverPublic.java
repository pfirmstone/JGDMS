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

package org.apache.river.test.spec.servicediscovery;

import java.util.logging.Level;

import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lookup.ServiceDiscoveryManager;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.Test;

/**
 * With respect to the lookup discovery processing performed by the
 * <code>ServiceDiscoveryManager</code> utility class, this class verifies
 * that that utility operates as specified. That is, this class verifies
 * the following statement from the specification:
 * <p><i>
 * "If the value of the <code>DiscoveryManagement/<code>
 *  argument is <code>null</code>, then an instance of the
 *  <code>net.jini.discovery.LookupDiscoveryManager<code/>
 *  utility class will be constructed to discover only those
 *  lookup services that are members of the public group."
 * </i><p>
 * 
 * Regression test for Bug ID 4364301
 */
public class DefaultDiscoverPublic extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Creates an instance of <code>ServiceDiscoveryManager</code>,
     *     inputting <code>null</code> to the <code>DiscoveryManagement</code>
     *     parameter.
     */
    public Test construct(QAConfig config) throws Exception {
        createSDMduringConstruction = false;
        waitForLookupDiscovery = false;
        terminateDelay = 0;
        super.construct(config);
        testDesc = "service discovery manager with default lookup "
                   +"discovery manager (should discover public lookups)";
        srvcDiscoveryMgr = 
	    new ServiceDiscoveryManager(null, 
					null,
					config.getConfiguration());
        sdmList.add(srvcDiscoveryMgr);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the instance of <code>DiscoveryManagement</code>
     *     being used by the service discovery manager.
     *  2. Retrieves the set of groups the lookup discovery manager is
     *     configured to discover. 
     *  3. Verifies the set of groups contains only the public group.
     */
    protected void applyTestDef() throws Exception {
        DiscoveryManagement dm = srvcDiscoveryMgr.getDiscoveryManager();
        if( !(dm instanceof LookupDiscoveryManager) ) {
        throw new TestException(" -- default lookup discovery manager is not "
				+"an instance of "
				+"net.jini.discovery.LookupDiscoveryManager");
        }//endif
        String[] groups = ((LookupDiscoveryManager)dm).getGroups();
        if(groups == DiscoveryGroupManagement.ALL_GROUPS) {
            throw new TestException(" -- default lookup discovery manager is "
				    +"configured to discover ALL_GROUPS");
        }//endif
        if(groups.length == 0) {
            throw new TestException(" -- default lookup discovery manager is "
				    +"configured to discover NO_GROUPS");
        }//endif
        String publicGroup = new String("");
        for(int i=0;i<groups.length;i++) {
            if( !(publicGroup.equals(groups[i])) ) {
                throw new TestException(" -- default lookup discovery manager "
					+"is configured to discover a "
					+"non-public group ("+groups[i]+")");
            }//endif
        }//end loop
    }//end applyTestDef

}//end class DefaultDiscoverPublic


