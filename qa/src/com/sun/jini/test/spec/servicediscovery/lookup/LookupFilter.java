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

import net.jini.lookup.ServiceItemFilter;

import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>ServiceDiscoveryManager</code> utility class, this class verifies
 * that the non-blocking version that returns a single instance of
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
 *    ServiceItem lookup(ServiceTemplate tmpl, ServiceItemFilter filter);
 * </pre>
 */
public class LookupFilter extends Lookup {

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
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        testDesc = "single service lookup employing -- template, filter";
    }//end setup

    /** Defines the actual steps of this particular test.
     *  
     *  1. Invokes the desired version of the <code>lookup</code> method on
     *     the <code>ServiceDiscoveryManager</code> to query the discovered
     *     lookup service(s) for the desired registered service. 
     *  2. Verifies that the service returned is the service expected
     * 
     *  @return a <code>String</code> containing a failure message, or
     *           <code>null</code> if the test was successful.
     */
    /** Defines the actual steps of this particular test.
     *  
     *  1. Invokes the desired version of the <code>lookup</code> method
     *     on the service discovery manager - applying the filter created
     *     during setup - to query the discovered lookup services for the
     *     desired service. 
     *  2. Verifies that the service returned is the service expected
     */
    protected void applyTestDef() throws Exception {
        /* The version of lookup() being tested here returns 1 arbitrarily
         * chosen (by the lookup service) service from the set of possible
         * services. Because of this, and because the filter rejects
         * odd-valued services, the call to lookup should return either
         * 1 service (even-valued) or no service at all (because of the
         * filter).
         *
         * Thus, because expectedServiceList was populated in the parent
         * class with no knowledge of the filter or how the attributes will
         * be used, the contents of expectedServiceList must first be adjusted
         * to reflect how the lookup will be performed. Furthermore, the
         * contents must be adjusted on a service-by-service basis.
         */
        for(int i=0;i<expectedServiceList.size();i++) {
            logger.log(Level.FINE, "test service "+i);
            TestService expectedService 
                                   = (TestService)expectedServiceList.get(i);
            if( srvcValOdd(expectedService) ) {
                expectedService = null;
                logger.log(Level.FINE, "  expect null on lookup");
            } else {
                logger.log(Level.FINE, "  expect non-null service "
                                  +"with value = "+expectedService.i);
            }
            /* From all registered services of type TestService, find the one
             * service associated with a particular attribute.
             */
            logger.log(Level.FINE, "  creating template with "
                              +"attribute value = "+(SERVICE_BASE_VALUE+i));
	    Class c = Class.forName
	 ("com.sun.jini.test.spec.servicediscovery.AbstractBaseTest$TestService");
	    Entry[] attrs = new Entry[1];
	    attrs[0] = new TestServiceIntAttr(SERVICE_BASE_VALUE+i);
	    template = new ServiceTemplate(null, new Class[]{c}, attrs);
            /* Through the service discovery manager, query the discovered
             * lookup service(s) for the one registered service that both
             * matches the template and satisfies the filter
             */
            logger.log(Level.FINE, "  performing lookup -- "
                      +"srvcItem = srvcDiscoveryMgr.lookup(template,filter)");
            ServiceItem srvcItem = srvcDiscoveryMgr.lookup(template,
                                                           firstStageFilter);

            if(expectedService == null) {
                if(srvcItem != null) {
                    throw new TestException(" -- filter failed -- unexpected "
					    +"non-null service item returned "
					    +"on lookup of test service "+i);
                } else {
                    logger.log(Level.FINE, "  OK -- both null as expected");
                }
            } else { //expectedService != null
                if(srvcItem == null) {
                    throw new TestException(" -- filter failed -- unexpected "
					    +"null service item returned on "
					    +"lookup of test service "+i);
                } else if(srvcItem.service == null) {
                    throw new TestException
			(" -- null service component returned "
			 +"on lookup of test service "+i);
                } else {
                    if( !(srvcItem.service).equals(expectedService) ) {
                        logger.log(Level.FINE, "  FAILURE -- "
                               +"expectedService.i = "+expectedService.i+", "
                               +"(srvcItem.service).i = "
                               +((TestService)(srvcItem.service)).i);
                        throw new TestException(" -- filter failed -- service "
						+"returned from lookup not "
						+"equivalent to expected test "
						+"service "+i);
                    } else {
                        logger.log(Level.FINE, "  OK -- "
                    +"(srvcItem.service).equals(expectedService) as expected");
                    }
                }//endif(srvcItem==null)
            }//endif(expectedService==null)
        }//end loop
    }//end applyTestDef

}//end class LookupFilter


