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
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.test.share.AttributesUtil;
import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.spec.joinmanager.AbstractBaseTest;

import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lookup.JoinManager;

import net.jini.core.entry.Entry;

/** Regression test for bug #4671109.
 *
 * The discovery of the lookup service and the creation of the join manager
 * causes a RegisterTask to be queued. In order to reveal the race condition
 * described in bug #4671109, the following sequence must be guaranteed by
 * this test:
 *
 *  - the RegisterTask must actually start (been pulled off the TaskManager
 *    queue and begun execution of the run() method) before the test calls
 *    JoinManager.addAttributes()
 *  - JoinManager.addAttributes() must be called only after the RegisterTask
 *    starts, but before it finishes.
 *
 * Guaranteeing the sequence above will cause the service to be registered
 * with only the initial attributes (because the local field 'lookupAttr'
 * has not yet been set by the call to addAttributes()). Then, because
 * addAttributes() is called before the RegisterTask completes, the joinSet
 * will not yet have been populated with the registered service, so an
 * AddAttributesTask will never be queued. Note that although the call to
 * addAttributes() will not create an AddAttributeTask to propagate the
 * attributes to the lookup service with which the service is eventually
 * registered, that call will actually add the new attributes to the local
 * lookupAttr field. Thus, the state of the service's attributes in the lookup
 * service will differ from its local attribute state in the join manager.
 *
 * To guarantee the sequence of events described above, a number of somewhat
 * arbitrary time delays must be injected into the process. 
 *
 * After the RegisterTask is queued, there is a small delay before that task
 * actually begins. Thus, a delay of N seconds (where N can probably be small)
 * should be inserted between the time the join manager is created (and the
 * lookup service has already been discovered) and the time the call to
 * addAttributes() is made. This should guarantee that the local lookupAttr
 * field is not set before the RegisterTask begins; thus, the service will
 * be registered with only the initial attributes. Through a bit of
 * trial-and-error it has been determined that a value of N = 5 seconds
 * should be sufficient for this delay (see the run() method below).
 *
 * A delay must be inserted into the register() method on the backend impl
 * of the lookup service itself. This will delay the completion of the
 * RegisterTask on the join manager; and thus will give this test enough time
 * to call addAttributes() before the RegisterTask completes and adds the
 * registered service to the joinSet (which would then cause addAttributes()
 * to queue an AddAttributesTask). Again, through trial-and-error, a delay of
 * about 2*N seconds seems reasonable here (N seconds to cover the initial
 * N second delay from the point of queuing the RegisterTask to the start
 * of that task, and another N seconds to allow this test to call
 * addAttributes()).
 * 
 * Finally, to verify that the race condition either exists, or has been
 * fixed, a delay must be inserted to allow the RegisterTask to complete
 * before the test attempts to retrieve the service and its associated
 * attributes from the lookup service. The retrieved attributes are compared
 * against the expected attributes (the combination of the initial and the
 * added attributes). If those retrieved attributes are equal to the initial
 * attributes, then the race condition has occurred. If the retrieved 
 * attributes equal the combination of the initial attributes and the
 * added attributes, then the race has probably been eliminated. Because 
 * the delay described here is intended to guarantee that the RegisterTask
 * has completed before attribute retrieval and comparison begins, it can be
 * as long as desired. But setting it equal to the delay in the register()
 * method in the backend of the lookup service (2*N) is probably sufficient.
 */
public class AddAttributesRace extends AbstractBaseTest {

    protected Entry[] expectedAttrs;
    protected LookupDiscoveryManager ldm;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *    <li> starts N lookup service(s) whose member groups are finite
     *         and unique relative to the member groups of all other lookup
     *         services running within the same multicast radius of the new
     *         lookup services
     *    <li> creates a discovery manager that discovers the lookup service(s)
     *         started during setup
     *    <li> verifies the discovery of the lookup service(s) started during
     *         setup
     *    <li> initializes the initial set of attributes with which to
     *         register the service, the new set of attributes to add
     *         to the service through join manager, and the set of attributes
     *         expected to be associated with the service in the lookup
     *         service(s) started above
     *   </ul>
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        /* Make sure lookups are discovered before creating join manager */
        logger.log(Level.FINE, "do lookup service discovery ...");
        ldm = getLookupDiscoveryManager();
        mainListener.setLookupsToDiscover(lookupsStarted,
                                          toGroupsArray(lookupsStarted));
        waitForDiscovery(mainListener);
        logger.log(Level.FINE, "discovery complete");
        /* Initialize the attribute sets */
        expectedAttrs   = addAttrsAndRemoveDups(serviceAttrs,newServiceAttrs);
        AttributesUtil.displayAttributeSet(serviceAttrs,
                                           "initial service attrs",
					   Level.FINE);
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "service attrs to add",
					   Level.FINE);
        AttributesUtil.displayAttributeSet(expectedAttrs,
                                           "expected service attrs",
					   Level.FINE);
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> creates a join manager configured to both use the discovery
     *         manager created above (to avoid discovery-related delays), and 
     *         to register a service with an initial set of attributes
     *    <li> waits for the RegisterTask in the join manager to begin
     *         execution
     *    <li> after the RegisterTask has begun execution, and before it
     *         completes execution, invokes addAttributes() on the join
     *         manager to associate a new set of attributes with the
     *         service to be registered by the join manager
     *    <li> after the RegisterTask completes, retrieves from the lookup
     *         service, the registered service and its currently associated
     *         attributes
     *    <li> if the set of retrieved attributes equals the combination of
     *         the initial attributes and the added attributes, declares
     *         success; otherwise, declares failure
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        int regWait  = 5; //seconds to wait for RegisterTask to start
        int propWait = 10;//seconds to wait for RegisterTask to complete
	/* Create the join manager to queue and start a RegisterTask */
	logger.log(Level.FINE, "create join manager");
	joinMgrSrvcID = new JoinManager(testService,
					serviceAttrs,
					serviceID,
					ldm,
					leaseMgr,
					getConfig().getConfiguration());
	joinMgrList.add(joinMgrSrvcID);
	/* Delay to allow RegisterTask to start before adding attributes */
	logger.log(Level.FINE, "wait "+regWait
		   +" seconds for RegisterTask to start");
	DiscoveryServiceUtil.delayMS(regWait*1000);
	/* Add attributes. If race, no AddAttributesTask will be queued */
	logger.log(Level.FINE, "add attributes");
	joinMgrSrvcID.addAttributes(newServiceAttrs);
	/* Delay to allow RegisterTask to complete comparing attributes */
	logger.log(Level.FINE, "wait "+propWait
		   +" seconds for attribute propagation");
	verifyPropagation(expectedAttrs,propWait);
    }
}//end class AddAttributesRace
