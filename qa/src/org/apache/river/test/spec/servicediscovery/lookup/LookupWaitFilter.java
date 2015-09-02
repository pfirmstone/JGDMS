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

package org.apache.river.test.spec.servicediscovery.lookup;

import net.jini.lookup.ServiceItemFilter;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.Test;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>ServiceDiscoveryManager</code> utility class, this class verifies
 * that the blocking version that returns a single instance of
 * <code>ServiceItem</code> operates as specified when invoked under
 * the following condition:
 * <p><ul>
 *    <li> template matching performed by the service discovery manager is
 *         based on service type only
 *    <li> the service discovery manager applies filtering to the results
 *         of the template matching
 * </ul><p>
 *
 * <pre>
 *    ServiceItem lookup(ServiceTemplate tmpl,
 *                       ServiceItemFilter filter,
 *                       long waitDur);
 * </pre>
 */
public class LookupWaitFilter extends LookupWait {

    /** Constructs and returns the <code>ServiceItemFilter</code> to be
     *  applied by the service discovery manager to the results of the
     *  template matching.
     */
    protected ServiceItemFilter getFirstStageFilter() {
        return new TestFilter2(); //returns even-valued services
    }//end getFirstStageFilter

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        testDesc = ": single service lookup employing -- template, filter, "
                   +"blocking";
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Invokes the desired version of the <code>lookup</code> method
     *     on the service discovery manager - applying the filter created
     *     during construct - and verifies that when no services are registered
     *     with the lookup services started during construct, the blocking
     *     mechanism of the <code>lookup</code> method blocks for the full
     *     amount of time requested
     *  2. With each of the lookup services started in construct, registers 3
     *     services each with 1 associated attribute 
     *  3. Again invokes the desired version of the <code>lookup</code>
     *     method - applying the filter - and verifies that the service
     *     returned is the service expected, and the <code>lookup</code>
     *     method blocks until the registration of the desired services 
     *     have completed successfully
     *  4. With each of the lookup services started in construct, registers 4
     *     more services each with 1 associated attribute 
     *  5. Again invokes the desired version of the <code>lookup</code>
     *     method - applying the filter - and verifies that because the filter
     *     rejects the services that would normally be discovered if the
     *     filter were not applied, the call to the <code>lookup</code>
     *     method blocks for the full amount of time requested
     */
    protected void applyTestDef() throws Exception {
        /* Verify blocking mechanism in the absense of registered services */
        verifyBlocking(waitDur);
        /* Register 3 services with 1 attribute and verify that the call to
         * lookup actually blocks until the desired services are registered.
         */
        waitDur = 60*1000; //reset the amount of time to block
        verifyBlocking(3,1,waitDur);
        /* Register 4 services with 1 attribute and verify that the call to
         * lookup actually blocks the full amount since the filter will
         * prevent the second service from being returned (its value is odd).
         */
        verifyBlocking(4,1,waitDur);
    }//end applyTestDef

} //end class LookupWaitFilter


