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

import net.jini.lookup.ServiceItemFilter;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>ServiceDiscoveryManager</code> utility class, this class verifies
 * that the blocking version that returns an array of instances of
 * <code>ServiceItem</code> operates as specified when invoked under
 * the following condition:
 * <p><ul>
 *    <li> template matching performed by the service discovery manager is
 *         based on service type only
 *    <li> the service discovery manager applies filtering to the results
 *         of the template matching
 *    <li> the number of services available for discovery is greater than the
 *         acceptable minimum number of services input to the lookup method
 * </ul><p>
 *
 * <pre>
 *    ServiceItem[] lookup(ServiceTemplate tmpl,
 *                         int minMatches,
 *                         int maxMatches
 *                         ServiceItemFilter filter,
 *                         long waitDur);
 * </pre>
 *
 * If, after filtering, there are more services available to be discovered
 * than the acceptable minimum number of services (<code>minMatches</code>),
 * then no blocking should occur; rather, the <code>lookup</code> method
 * should return immediately after discovering the acceptable number of
 * services.
 */
public class LookupMinMaxNoBlockFilter extends LookupMinMaxNoBlock {

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
     *  2. Registers M test services with the lookup services started above
     *  3. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  4. Creates a template that will match the test services based on
     *     service type only
     *  5. Creates a filter that will reject only some of the services 
     *     registered above; and which will be applied to the results of the
     *     template matching performed by the service discovery manager
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        testDesc = "multiple service lookup employing -- template, filter, "
                   +"blocking, more than minMatches available, should "
                   +"return without blocking";
        int nEven = 0;
        for(int i=0;i<maxMatches;i++) {
            if( (i%2) == 0 ) nEven++;
        }
        maxMatches = nEven;
        return this;
    }//end construct

}//end class LookupMinMaxNoBlockFilter


