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
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>ServiceDiscoveryManager</code> utility class, this class verifies
 * that the blocking version that returns an array of instances of
 * <code>ServiceItem</code> operates as specified when invoked under
 * the following condition:
 * <p><ul>
 *    <li> template matching performed by the service discovery manager is
 *         based on service type only
 *    <li> the service discovery manager applies no filtering to the results
 *         of the template matching
 *    <li> the minimum number of desired services is less than the maximum
 *         number of desired services
 * </ul><p>
 *
 * <pre>
 *    ServiceItem[] lookup(ServiceTemplate tmpl,
 *                         int minMatches,
 *                         int maxMatches
 *                         ServiceItemFilter filter,
 *                         long waitDur);
 * </pre>
 */
public class LookupMinLessMax extends LookupMinEqualsMax {

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
        testDesc = "multiple service lookup employing -- template, "
                   +"blocking, minMatches < maxMatches";
        if(nAddServices <= 2) {
            logger.log(Level.FINE, 
		       "This test guarantees that the minimum "
		       +"number of services to");
            logger.log(Level.FINE, 
		       "return is less than the maximum number"
		       +"of services to return.");
            logger.log(Level.FINE, 
		       "To make such a guarantee, the number of "
		       +"services to add must be > 2.");
            logger.log(Level.FINE, 
		       "The currently configured number of services "
		       +"to add = "+nAddServices+".");
            logger.log(Level.FINE, "Reset that number to 3.");
            nAddServices = 3;
            logger.log(Level.FINE, 
		       "additional services to register -- "
		       +nAddServices);
        }//endif
        maxMatches = nServices+nAddServices-1;
        minMatches = nServices+1;
    }//end setup

}//end class LookupMinLessMax


