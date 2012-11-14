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

import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;

import net.jini.core.lookup.ServiceItem;

import java.util.ArrayList;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import java.util.List;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>ServiceDiscoveryManager</code> utility class, this class verifies
 * that the non-blocking version that returns a single instance of
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
 *    ServiceItem lookup(ServiceTemplate tmpl, ServiceItemFilter filter);
 * </pre>
 */
public class Lookup extends AbstractBaseTest {

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
        testDesc = "single service lookup employing -- template";
        logger.log(Level.FINE, "registering "+nServices
                              +" service(s) each with "+nAttributes
                              +" attribute(s) ...");
        registerServices(nServices,nAttributes);
    }//end setup

    /** Defines the actual steps of this particular test.
     *  
     *  1. Invokes the desired version of the <code>lookup</code> method
     *     on the service discovery manager - applying NO filtering
     *     (<code>null</code> filter parameter) - to query the discovered
     *     lookup services for the desired service. 
     *  2. Verifies that the service returned is the service expected
     */
    protected void applyTestDef() throws Exception {
        /* Through the service discovery manager, query the discovered lookup
         * service(s) for the desired services
         */
        ServiceItem srvcItem = srvcDiscoveryMgr.lookup(template,
                                                       firstStageFilter);
        if(srvcItem == null) {
            throw new TestException(" -- service returned is null");
        } else if(srvcItem.service == null) {
            throw new TestException(" -- service component of "
                              +"returned service is null");
        } else {
            for(int i=0;i<expectedServiceList.size();i++) {
                if((srvcItem.service).equals(expectedServiceList.get(i))) {
	            return;//passed
                }//endif
            }//end loop (i)
            displaySrvcInfoOnFailure(srvcItem,expectedServiceList);
            throw new TestException(" -- returned service item "
				    +" is not equivalent to any of the "
				    +"service(s) registered with lookup");
        }//endif
    }//end applyTestDef

    /** Convenience method that should be called when failure occurs. This
     *  method displays useful debug information about the given 
     *  <code>ServiceItem</code> in relation to the <code>ArrayList</code>
     *  containing the registered services.
     */
    protected void displaySrvcInfoOnFailure(ServiceItem srvcItem,
                                            List srvcList)
    {
        logger.log(Level.FINE, "returned service item "
                              +" is not equivalent to any of the "
                              +"service(s) registered with lookup");
        logger.log(Level.FINE, "  discovered service      = "
                              +srvcItem.service);
        if(srvcItem != null) {
            logger.log(Level.FINE, "  discovered service.i    = "
                              +((TestService)(srvcItem.service)).i);
        }//endif
        if( (srvcList == null) || (srvcList.size() == 0) ) {
            logger.log(Level.FINE, "  no registered services");
            return;
        }//endif
        for(int i=0;i<srvcList.size();i++) {
            Object item = srvcList.get(i);
            if(item instanceof TestService) {
                TestService srvc = (TestService)item;
                logger.log(Level.FINE, "  registered service["+i+"]   = "+srvc);
                logger.log(Level.FINE, "  registered service["+i+"].i = "+srvc.i);
            } else {
                logger.log(Level.FINE, "  registered service["+i+"] not an"
                                  +"instance of TestService, an "
                                  +"instance of -- "+item);
            }//end if
        }//end loop
    }//end displayServices

}//end class Lookup


