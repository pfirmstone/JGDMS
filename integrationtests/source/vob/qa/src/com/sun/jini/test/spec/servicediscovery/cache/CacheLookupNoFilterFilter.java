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

import net.jini.lookup.ServiceItemFilter;
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
 *    <li> the lookup cache applies no first-stage filtering to the results
 *         of the template matching
 *    <li> the lookup method of the lookup cache applies second-stage
 *         filtering to the results of the template matching
 * </ul><p>
 *
 * <pre>
 *    ServiceItem lookup(ServiceItemFilter filter);
 * </pre>
 */
public class CacheLookupNoFilterFilter extends CacheLookup {

    /** Constructs and returns the <code>ServiceItemFilter</code> to be
     *  applied, in the cache's <code>lookup</code> method, to the results
     *  of the template matching and the first-stage filtering (if any).
     */
    protected ServiceItemFilter getSecondStageFilter() {
        return new TestFilter4(); //returns services with values divisible by 4
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
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        testDesc = "single service cache lookup -- services pre-registered, "
                   +"no first-stage filter, second-stage filter";
    }//end setup

    /** Defines the actual steps of this particular test.
     *  
     *  1. Requests the creation of a lookup cache that will perform template
     *     matching using the template created during setup, and which will
     *     apply NO first-stage filtering to the results of the template
     *     matching (<code>null</code> filter parameter)
     *  2. Invokes the desired version of the <code>lookup</code> method -
     *     requesting that the second-stage filter be applied to the results
     *     of the template matching - to query the cache for the desired
     *     expected service
     *  3. Verifies that the service returned is the service expected
     * 
     *  @return a <code>String</code> containing a failure message, or
     *           <code>null</code> if the test was successful.
     */
    protected void applyTestDef() throws Exception {
        /* Create the cache and verify it returns a registered service. */ 
        super.applyTestDef();
        /* Verify the service returned from the cache satifies the second-stage
         * filter.
         */
        if( !srvcValDivisibleByN( (TestService)(srvcItem.service),4) ) {
            if( srvcValOdd((TestService)(srvcItem.service)) ) {
                throw new TestException
		    (" -- expected service with value divisible "
		     +"by 4, but service with odd value returned");
            } else {
                throw new TestException
		    (" -- expected service with value divisible "
		     +"by 4, but service with value divisible by "
		     +"only 2 returned");
            }
        }//endif
    }//end applyTestDef

}//end class CacheLookupNoFilterFilter


