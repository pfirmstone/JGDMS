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

package com.sun.jini.test.spec.servicediscovery.cache;

import java.util.logging.Level;

import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;

import net.jini.lookup.LookupCache;

import net.jini.core.lookup.ServiceItem;

import java.rmi.RemoteException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>LookupCache</code> interface, this class verifies that the 
 * version that returns a single instance of <code>ServiceItem</code>
 * operates as specified when invoked under the following condition:
 * <p><ul>
 *    <li> template matching performed by the service discovery manager is
 *         based on service type only
 *    <li> the lookup cache applies no first-stage filtering to the results
 *         of the template matching
 *    <li> the lookup method of the lookup cache applies no second-stage
 *         filtering to the results of the template matching; that is, null
 *         is input as the filter parameter of the lookup method
 * </ul><p>
 *
 * <pre>
 *    ServiceItem lookup(ServiceItemFilter filter);
 * </pre>
 */
public class CacheLookup extends AbstractBaseTest {

    protected long cacheDelay      = 0;
    protected LookupCache cache    = null;
    protected ServiceItem srvcItem = null;

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
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        testDesc = "single service cache lookup -- services pre-registered, "
                   +"no first-stage filter, no second-stage filter";
        logger.log(Level.FINE, "registering "+getnServices()
                              +" service(s) each with "+getnAttributes()
                              +" attribute(s) ...");
        registerServices(getnServices(), getnAttributes());
        cacheDelay = 20*1000;
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Requests the creation of a lookup cache that will perform template
     *     matching using the template created during construct, and which will
     *     apply NO first-stage filtering to the results of the template
     *     matching (<code>null</code> filter parameter)
     *  2. Invokes the desired version of the <code>lookup</code> method -
     *     applying NO second-stage filtering (<code>null</code> filter 
     *     parameter) - to query the cache for the desired expected service
     *  3. Verifies that the service returned is the service expected
     */
    protected void applyTestDef() throws Exception {
        /* Create a cache for the services that were registered. */
	logger.log(Level.FINE, "requesting a lookup cache");
	cache = srvcDiscoveryMgr.createLookupCache(template,
						   firstStageFilter,
						   null);//listener
        /* Query the cache for the desired registered service. */
	for (int i = 0; i < 3; i++) {	
	    logger.log(Level.FINE, "waiting "+(cacheDelay/1000)
			      +" second(s) to allow the cache to populate ...");
	    DiscoveryServiceUtil.delayMS(cacheDelay);
	    logger.log(Level.FINE, "querying the cache for one "
					+"service reference");
	    srvcItem = cache.lookup(secondStageFilter);
	    if (srvcItem != null) {
		break;
	    }
	}
        if(srvcItem == null) {
            throw new TestException
		(" -- no service in cache -- null service item "
                              +"returned");
        } else if(srvcItem.service == null) {
            throw new TestException(" -- service component of "
                              +"returned service is null");
        } else {
            for(int i=0;i<getExpectedServiceList().size();i++) {
                if((srvcItem.service).equals(getExpectedServiceList().get(i))) {
                    return;// passed
                }//endif
            }//end loop (i)
            throw new TestException(" -- returned service item "
                              +" is not equivalent to any of the "
                              +"service(s) registered with lookup");
        }//endif
    }//end applyTestDef

}//end class CacheLookup


