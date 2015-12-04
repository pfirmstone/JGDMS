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

package org.apache.river.test.spec.locatordiscovery;

import java.util.logging.Level;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.services.lookupsimulator.LookupSimulatorProxy;

import net.jini.core.lookup.ServiceID;

/**
 * With respect to the <code>discard</code> method, this class verifies that
 * the <code>LookupLocatorDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon invoking <code>discard</code> with an instance of 
 * <code>ServiceRegistrar</code> that does not exist in the utility's
 * managed set of lookup services, no action is taken by <code>discard</code>.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, each of the lookup service(s) that were started
 *        are destroyed and then discarded so that it can be verified that
 *        the discard mechanism is operating correctly
 *   <li> the discard method on the LookupLocatorDiscovery instance is then
 *        invoked with a ServiceRegistrar instance that is not an element of
 *        the lookup locator discovery utility's managed set of lookup services
 * </ul><p>
 * 
 * If the <code>LookupLocatorDiscovery</code> utility functions as specified,
 * then a <code>DiscoveryEvent</code> instance indicating a discovered event
 * (accurately reflecting the expected contents) will be sent to the initial
 * listener for each lookup service that was started. Upon the invocation
 * of the <code>discard</code> method with each discovered lookup service,
 * the expected discarded events are sent. And upon the invocation of the
 * <code>discard</code> method with a <code>ServiceRegistrar</code> that 
 * does not exist in the utility's managed set of lookup services, no further
 * events are sent or actions are taken.
 *
 */
public class DiscardDNE extends DiscardNull {

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        proxy = new LookupSimulatorProxy(null,new ServiceID(1,2));
        discardStr = new String("attempt to discard a registrar that "
                                +"DOES NOT EXIST in the managed set of "
                                +"registrars ...");
        return this;
    }//end construct

}//end class DiscardDNE

