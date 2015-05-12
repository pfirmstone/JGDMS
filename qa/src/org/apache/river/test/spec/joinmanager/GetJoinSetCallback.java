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

package org.apache.river.test.spec.joinmanager;

import java.util.logging.Level;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

import net.jini.lookup.JoinManager;
import net.jini.core.lookup.ServiceRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when the constructor that takes a 
 * <code>ServiceIDListener</code> in its argument list is used to
 * construct a join manager that registers a service with N lookup services,
 * the method <code>getJoinSet</code> returns an array containing the
 * same lookup services with which the service was registered by the
 * join manager.
 * 
 */
public class GetJoinSetCallback extends AbstractBaseTest {

    protected JoinManager jm;
    protected ServiceRegistrar[] joinRegs;
    protected ServiceRegistrar[] startedRegs; 
    protected boolean callbackJM = true;; 
    protected int nServiceIDEventsExpected = 1; 

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> starts N lookup service(s) whose member groups are finite
     *          and unique relative to the member groups of all other lookup
     *          services running within the same multicast radius of the new
     *          lookup services
     *     <li> creates an instance of the JoinManager using the version of
     *          the constructor that takes ServiceIDListener (callback),
     *          inputting an instance of a test service and a non-null
     *          instance of a lookup discovery manager configured to discover
     *          the lookup services started in the previous step
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Discover & join lookups just started */
        String jmType = ( (callbackJM) ? "callback" : "service ID" );
        logger.log(Level.FINE, "creating a "+jmType+" join manager ...");
        if(callbackJM) {
            jm = new JoinManager(testService,serviceAttrs,
                                 new SrvcIDListener(testService),
                                 getLookupDiscoveryManager(),leaseMgr,
				 sysConfig.getConfiguration());
            nServiceIDEventsExpected = 1;
        } else {//create a join manager that sends the generated ID in an event
            jm = new JoinManager(testService,serviceAttrs,serviceID,
                                 getLookupDiscoveryManager(),leaseMgr,
				 sysConfig.getConfiguration());
            nServiceIDEventsExpected = 0;
        }//endif
        joinMgrList.add(jm);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the set of lookup services returned by the method
     *   <code>getJoinSet</code> is the same as the set of lookup services
     *   with which the test service was registered by the join manager
     *   created during construct.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
	/* Verify that the lookups were discovered */
	logger.log(Level.FINE, "verifying the lookup "
		   +"service(s) are discovered ...");
	mainListener.setLookupsToDiscover(getLookupsStarted(),
					  toGroupsArray(getLookupsStarted()));
	waitForDiscovery(mainListener);
	verifyJoin();
	joinRegs = jm.getJoinSet();
	List<ServiceRegistrar> lusList = getLookupListSnapshot("GetJoinSetCallback.run");
	startedRegs = (lusList).toArray(new ServiceRegistrar[lusList.size()]);
	logger.log(Level.FINE, "comparing the join set to the "
		   +"set of lookup service(s) started ...");
	if( !arraysEqual(joinRegs,startedRegs) ) {
	    logger.log(Level.FINE, 
		       "set of lookup service(s) joined not "
		       +"equal to set of lookup service(s) started");
	    throw new TestException("set of lookup service(s) joined not equal"
				    +" to set of lookup service(s) started");
	}//endif
	logger.log(Level.FINE, 
		   "set of lookup service(s) joined equals "
		   +"set of lookup service(s) started");
    }//end run

    /** Verifies that a new invocation of the method <code>getJoinSet</code>,
     *  occurring after an initial invocation in which the output is
     *  stored in the <code>joinRegs</code> array, will return a new
     *  array that contains the the same lookup services as those
     *  with which the service was registered by the join manager
     *  created during construct.
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     */
    protected void verifyNewArray() throws Exception {
        ServiceRegistrar[] newJoinRegs = jm.getJoinSet();
        if(newJoinRegs == joinRegs) {
            String errStr = new String("new join set == original join set, "
                                       +"a new array is not returned");
            logger.log(Level.FINE, errStr);
            throw new TestException(errStr);
        }//endif
        logger.log(Level.FINE, "comparing the new join set to the "
		   +"set of lookup service(s) started ...");
        if( !arraysEqual(newJoinRegs,startedRegs) ) {
            String errStr ="the new join set is not equal to "
		          +"the set of lookup service(s) started";
            logger.log(Level.FINE, errStr);
            throw new TestException(errStr);
        }//endif
        logger.log(Level.FINE, 
		   "new array returned that equals the set of "
		   +"lookup service(s) started");
    }//end verifyNewArray

}//end class GetJoinSetCallback


