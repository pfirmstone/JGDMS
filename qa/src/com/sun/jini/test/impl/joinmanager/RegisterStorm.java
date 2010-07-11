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

package com.sun.jini.test.impl.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.spec.joinmanager.AbstractBaseTest;

import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceIDListener;

import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceMatches;

import java.rmi.RemoteException;
import java.util.ArrayList;
import net.jini.discovery.DiscoveryListenerManagement;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is constructed, the
 * service input to the constructor is registered with all lookup
 * services the join manager is configured to discover (through its
 * <code>DiscoveryManagement</code> instance).
 * 
 */
public class RegisterStorm extends AbstractBaseTest {

    private static class SIDListener implements ServiceIDListener {
        private Object srvc;
        public SIDListener(Object srvc) {
            this.srvc = srvc;
        }//end constructor
        public void serviceIDNotify(ServiceID serviceID) {
        }//end serviceIDNotify
    }//end class SIDListener

    public RegisterStorm() {
        /* Initialize timeout values here (rather than in the run() method)
         * so they can be overridden in the configuration of this test.
         */
	useFastTimeout = true;
        fastTimeout = 20;
    }//end constructor

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the test service input to the join manager constructor
     *   is registered with all lookup services the join manager is configured
     *   to discover (through its <code>DiscoveryManagement</code> instance).
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");

	/* Verify that the lookups were discovered */
	logger.log(Level.FINE, 
		   "verifying the lookup service(s) are discovered ...");
	String[] groupsToDiscover =  toGroupsArray(lookupsStarted);
	LookupDiscoveryManager ldm = getLookupDiscoveryManager();
	mainListener.setLookupsToDiscover(lookupsStarted,
					  toGroupsArray(lookupsStarted));
	waitForDiscovery(mainListener);

	logger.log(Level.FINE, 
		   "registering " + nServices + " services ...");
	int mod = 1;
	if (nServices > 20) {
	    if (nServices >= 10000) {
		mod = 1000;
	    } else if ( (nServices >= 1000) && (nServices < 10000) ) {
		mod = 100;
	    } else if ( (nServices >= 100) && (nServices < 1000) ) {
		mod = 50;
	    } else {
		mod = 10;
	    }//endif
	}//endif
	for(int i=0;i<nServices;i++) {
	    TestService ts = new TestService(SERVICE_BASE_VALUE+i);
	    ServiceIDListener sidListener = new SIDListener(ts);
	    if( (i%mod == 0) || (i == nServices-1) ) {
		/* If N services (N large), show only some debug info*/
		logger.log(Level.FINE, "registering service # "+i);
	    }//endif
	    JoinManager jm = 
		new JoinManager(ts,serviceAttrs,
				sidListener,
				(DiscoveryListenerManagement) ldm,leaseMgr,
				getConfig().getConfiguration());
	    joinMgrList.add(jm);
	}//end loop
	int nSecs = 90;
	logger.log(Level.FINE, 
		   "waiting for the registration 'storm' to pass ...");
	DiscoveryServiceUtil.delayMS(nSecs*1000);
	logger.log(Level.FINE, 
		   "querying the lookup service "
		   +"to verify all service registrations ...");
	ArrayList lusList = getLookupListSnapshot
	    ("impl.joinmanager.RegisterStorm");
	ServiceRegistrar reg = (ServiceRegistrar)lusList.get(0);
	/* Verify nServices registered with lookup service 0 */
	ServiceMatches matches = reg.lookup(template,
					    Integer.MAX_VALUE);
	int nRegServices = matches.totalMatches;
	logger.log(Level.FINE, "# of services expected   = " + nServices);
	logger.log(Level.FINE, "# of services registered = " + nRegServices);
	if(nServices != nRegServices) {
	    throw new TestException("# of services expected ("+
				    nServices+") != # of services "
				    +"registered ("+nRegServices+")");
	}//endif
    }//end run

}//end class RegisterStorm
