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

package com.sun.jini.test.spec.servicediscovery.lookup;

import java.util.logging.Level;

import net.jini.core.lookup.ServiceItem;

import java.rmi.RemoteException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>ServiceDiscoveryManager</code> utility class, this class verifies
 * that the blocking version that returns a single instance of
 * <code>ServiceItem</code> operates as specified when invoked under
 * the following condition:
 * <p><ul>
 *    <li> template matching performed by the service discovery manager is
 *         based on service type only
 *    <li> the service discovery manager applies no filtering to the results
 *         of the template matching
 *    <li> at least 1 matching service is already available for discovery
 *         when the lookup method is called
 * </ul><p>
 *
 * <pre>
 *    ServiceItem lookup(ServiceTemplate tmpl,
 *                       ServiceItemFilter filter,
 *                       long waitDur);
 * </pre>
 *
 * If at least 1 matching service is already available to be discovered when
 * the <code>lookup</code> method is called (that is, the <code>lookup</code>
 * method does not have to wait for the desired service to be registered),
 * then the <code>lookup</code> method will not block. In that case, the
 * <code>lookup</code> method should return immediately after discovering
 * the desired service.
 */
public class LookupWaitNoBlock extends Lookup {

    /** Constructs an instance of this class. Initializes this classname,
     *  and sets the sub-categories to which this test and its children belong.
     */
    public LookupWaitNoBlock() {
    }//end constructor

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Registers M test services with the lookup services started above
     *  3. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  4. Creates a template that will match the test services based on
     *     service type only
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        testDesc = "single service lookup employing -- template, blocking";
    }//end setup

    /** Defines the actual steps of this particular test.
     *  
     *  1. Invokes the desired version of the <code>lookup</code> method
     *     on the service discovery manager - applying NO filtering
     *     (<code>null</code> filter parameter) - to query the discovered
     *     lookup services for the desired service. 
     *  2. Verifies that the service returned is the service expected,
     *     and the <code>lookup</code> method returns immediately without
     *     blocking
     * 
     *  @return a <code>String</code> containing a failure message, or
     *           <code>null</code> if the test was successful.
     */
    protected void applyTestDef() throws Exception {
        long waitDur = 30*1000;
        long waitDurSecs = waitDur/1000; //for debug output
	//XXX change from 2 to 20 to tolerate latencies when running secure
        long maxActualBlock = 20*1000;//immediate return = no greater than 2 sec
	//to account for network delays
        logger.log(Level.FINE, ""+expectedServiceList.size()
		   +" service(s) "
		   +"registered, look up exactly 1 service "
		   +"-- blocking "+waitDurSecs+" second(s)");

        /* Through the service discovery manager, query the discovered lookup
         * service(s) for the desired registered service(s), and verify
         * that the desired service is returned immediately (does not block).
         */
	long startTime = System.currentTimeMillis();
	ServiceItem srvcItem = srvcDiscoveryMgr.lookup(template,
						       firstStageFilter,
						       waitDur);
	long endTime = System.currentTimeMillis();
	long actualBlockTime = endTime-startTime;
	long waitError = (actualBlockTime-waitDur)/1000;
	if(srvcItem == null) {
	    throw new TestException(" -- service returned is null");
	} else if(srvcItem.service == null) {
	    throw new TestException(" -- service component of "
				    +"returned service is null");
	} else {
	    boolean srvcOK = false;
	    for(int i=0;i<expectedServiceList.size();i++) {
		if((srvcItem.service).equals(expectedServiceList.get(i))) {
		    srvcOK = true;
		    break;
		}//endif
	    }//end loop (i)
	    if(!srvcOK) {
		displaySrvcInfoOnFailure(srvcItem,expectedServiceList);
		throw new TestException(" -- returned service item is not "
					+"equivalent to any expected service");
	    }//endif
	}//endif
	/* Test that the call did not block */
	if(actualBlockTime > maxActualBlock) {
	    throw new TestException
		(" -- blocked longer than expected (ideal = 0) "
		 + "-- requested block = " + waitDur/1000 + ", max block = "
		 + maxActualBlock/1000 +" second(s), actual "
		 + "block = "+ (actualBlockTime/1000)
		 + " second(s)");
	}//endif
	logger.log(Level.FINE, "expected service found -- "
		   +"requested block = "+(waitDur/1000)
		   +" second(s), actual block = "
		   +(actualBlockTime/1000)
		   +" second(s)");
    }//end applyTestDef

}//end class LookupWaitNoBlock


