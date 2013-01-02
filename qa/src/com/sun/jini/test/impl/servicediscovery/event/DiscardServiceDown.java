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

package com.sun.jini.test.impl.servicediscovery.event;

import net.jini.core.lookup.ServiceItem;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that the <code>ServiceDiscoveryManager</code> handles
 * the "discard problem" in the manner described in the specification.
 *
 * The discard problem occurs when an entity discards a service from a
 * lookup cache (because the service is unavailable to the entity), but
 * the entity is not really down (the service can still communicate with
 * the lookup services with which it is registered). When this situation
 * occurs, unless the service discovery manager takes steps equivalent to
 * those described in the specification, the service may never be
 * re-discovered (because the service - since it is not actually down -
 * continues to renew its leases with the lookup services, so none of
 * those lookup services will ever re-discover the service).
 * 
 * This class simulates the situation where a service that actually goes
 * down is discarded by an entity. This class then verifies that the service 
 * discovery manager identifies the situation and "commits" the service
 * discard.
 * 
 * Related bug ids: 4355024
 */
public class DiscardServiceDown extends DiscardServiceUp {

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  3. Creates a template that will match the test services based on
     *     service type only
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        int nLookupServices = getLookupServices().getnLookupServices();
        int nServices = getLookupServices().getnServices();
        testDesc = ""+nLookupServices+" lookup service(s), "+nServices
                       +" service(s) -- discard down service and wait for "
                       +"re-discovery";
        nAddedExpected      = 1*nServices;
        nRemovedExpected    = 1*nServices;
        simulateDownService = true;
        return this;
    }//end construct

}//end class DiscardServiceDown


