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

import net.jini.lookup.ServiceItemFilter;

import net.jini.core.lookup.ServiceItem;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>LookupCache</code> interface, this class verifies that the 
 * version that returns a single instance of <code>ServiceItem</code>
 * operates as specified when invoked under the following condition:
 * <p><ul>
 *    <li> template matching performed by the service discovery manager is
 *         based on service type only
 *    <li> the lookup cache applies first-stage filtering to the results
 *         of the template matching
 *    <li> the lookup method of the lookup cache applies second-stage
 *         filtering to the results of the template matching and the
 *         first-stage filtering
 * </ul><p>
 *
 * <pre>
 *    ServiceItem lookup(ServiceItemFilter filter);
 * </pre>
 */
public class CacheLookupFilterFilter extends CacheLookup {

    /** Constructs and returns the <code>ServiceItemFilter</code> to be
     *  applied by the lookup cache to the results of the template matching.
     */
    protected ServiceItemFilter getFirstStageFilter() {
        return new TestFilter3(); //returns services with values divisible by 3
    }//end getFirstStageFilter

    /** Constructs and returns the <code>ServiceItemFilter</code> to be
     *  applied, in the cache's <code>lookup</code> method, to the results
     *  of the template matching and the first-stage filtering (if any).
     */
    protected ServiceItemFilter getSecondStageFilter() {
        return new TestFilter3Not2(); //returns divisible by 3 but not by 2
    }//end getSecondStageFilter

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Registers M test services with the lookup services started above
     *  3. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  4. Creates a template that will match the test services based on
     *     service type only
     *  5. Creates a filter that will reject only some of the services 
     *     registered above; and which will be applied to the results of the
     *     template matching performed by the lookup cache (first-stage
     *     filtering)
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        testDesc = "single service cache lookup -- services pre-registered, "
                   +"first-stage filter, second-stage filter";
    }//end setup

    /** Defines the actual steps of this particular test.
     *  
     *  1. Requests the creation of a lookup cache that will perform template
     *     matching using the template created during setup, and which will
     *     apply the first-stage filter to the results of the template
     *     matching
     *  2. Invokes the desired version of the <code>lookup</code> method -
     *     requesting that the second-stage filter be applied to the results
     *     of the template matching and the first-stage filtering - to query
     *     the cache for the desired expected service
     *  3. Verifies that the service returned is the service expected
     * 
     *  @return a <code>String</code> containing a failure message, or
     *           <code>null</code> if the test was successful.
     */
    protected void applyTestDef() throws Exception {
        /* Create the cache and verify it returns a registered service. */ 
        super.applyTestDef();
        /* Verify the first-stage filter by verifying that when the cache
         * is queried for multiple services using only the first-stage
         * filter (no second-stage filter), all services returned
         * satisfy that filter.
         */
        logger.log(Level.FINE, "verifying first-stage filter");
        ServiceItem[] firstStageSrvcs = cache.lookup(null,Integer.MAX_VALUE);
        if(firstStageSrvcs == null) {
            logger.log(Level.FINE, "no services satisfying "
                              +"first-stage filter -- null service item "
                              +"array returned");
            throw new TestException
		(" -- no services satisfying first-stage filter "
		 +"-- null service item array returned");
        }//endif
        for(int i=0;i<firstStageSrvcs.length;i++) {
            if( !srvcValDivisibleByN( (TestService)(srvcItem.service),3) ) {
                logger.log(Level.FINE, "during first-stage filter "
                                  +"verification -- service returned with "
                                  +"value not divisible by 3");
		throw new TestException
		    (" -- during first-stage filter verification "
		     +"-- service returned with value not "
		     +"divisible by 3");
            }//endif
        }//end loop
        /* Verify the service returned from the cache satifies the second-stage
         * filter.
         */
        logger.log(Level.FINE, "verifying second-stage filter");
        srvcItem = cache.lookup(secondStageFilter);
        if(srvcItem == null) {
	    throw new TestException(" -- no service in cache -- null service "
				    +"item returned");
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
                throw new TestException(" -- returned service item "
					+" is not equivalent to any of the "
					+"service(s) registered with lookup");
            }//endif
        }//endif
        /* Verify cache filter returns a service divisible by 3, but not 2 */
        if( !srvcValDivisibleByN( (TestService)(srvcItem.service),3) ) {
            logger.log(Level.FINE, "during second-stage filter "
                              +"verification -- service returned with "
                              +"value not divisible by 3");
            throw new TestException
		(" -- during second-stage filter verification "
		 +"-- service returned with value not "
		 +"divisible by 3");
        }//endif
        if( srvcValDivisibleByN( (TestService)(srvcItem.service),2) ) {
            logger.log(Level.FINE, "during second-stage filter "
                              +"verification -- service returned with "
                              +"value divisible by both 3 and 2");
            throw new TestException(" -- during second-stage filter verification "
                              +"-- service returned with value "
                              +"divisible by both 3 and 2");
        }//endif
    }//end applyTestDef

}//end class CacheLookupFilterFilter


