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
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>ServiceDiscoveryManager</code> utility class, this class verifies
 * that the non-blocking version that returns an array of instances of
 * <code>ServiceItem</code> operates as specified when invoked under
 * the following condition:
 * <p><ul>
 *    <li> template matching performed by the service discovery manager is
 *         based on service type only
 *    <li> the service discovery manager applies no filtering to the results
 *         of the template matching
 * </ul><p>
 *
 * <pre>
 *    ServiceItem[] lookup(ServiceTemplate   tmpl,
 *                         int               maxMatches,
 *                         ServiceItemFilter filter);
 * </pre>
 */
public class LookupMax extends Lookup {

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
        testDesc = "multiple service lookup employing -- template";
    }//end setup

    /** Defines the actual steps of this particular test.
     *  
     *  1. Invokes the desired version of the <code>lookup</code> method
     *     on the service discovery manager - applying NO filtering
     *     (<code>null</code> filter parameter) - to query the discovered
     *     lookup services for the desired services. 
     *  2. Verifies that the services returned are the services expected
     * 
     *  @return a <code>String</code> containing a failure message, or
     *           <code>null</code> if the test was successful.
     */
    protected void applyTestDef() throws Exception {
        /* Through the service discovery manager, query the discovered lookup
         * service(s) for the desired services
         */
        ServiceItem[] srvcItems = srvcDiscoveryMgr.lookup(template,
                                                          nServices,
                                                          firstStageFilter);
        if(srvcItems == null) {
            throw new TestException
		(" -- array of service items returned is null");
        } else if( srvcItems.length != expectedServiceList.size() ) {
            logger.log(Level.FINE, "number of service items "
                              +"returned ("+srvcItems.length+") != "
                              +"expected number of service items ("
                              +expectedServiceList.size()+")");
            for(int i=0;i<srvcItems.length;i++) {
                logger.log(Level.FINE, "  service["+i+"] = "
                                  +srvcItems[i].service);
            }//end loop
            throw new TestException(" -- number of service items returned ("
				    +srvcItems.length+") != expected number "
				    +"of service items ("
				    +expectedServiceList.size()+")");
        } else {/* Compare the returned array to set of expected services */
	    label_i:
            for(int i=0;i<srvcItems.length;i++) {
                logger.log(Level.FINE, "comparing sevice item "+i);
                if( srvcItems[i] == null ) {
                    throw new TestException(" -- returned service item "+i
					    +" is null");
                } else if(srvcItems[i].service == null) {
                    throw new TestException(" -- service component of "
					    +"returned service item "+i
					    +" is null");
                } else {
                    for(int j=0;j<expectedServiceList.size();j++) {
                        if( (srvcItems[i].service).equals
                                               (expectedServiceList.get(j)) )
                        {
                            continue label_i; // next srvcItems[i]
                        }//endif
                    }//end loop (j)
                    throw new TestException(" -- returned service item "+i
					    +" is not contained in the "
					    +" expected set of services");
                }//endif
            }//end loop (i)
        }//endif
    }//end applyTestDef

}//end class LookupMax


