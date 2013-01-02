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

import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;
import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.lookup.LookupCache;
import net.jini.lookup.ServiceDiscoveryEvent;
import net.jini.lookup.ServiceDiscoveryListener;
import net.jini.lookup.ServiceDiscoveryManager;

import java.rmi.RemoteException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that when services that are to be discovered are
 * implemented with a poorly defined or non-existent ("bad") equals() method,
 * the event mechanism of the cache will operate in a predictable fashion,
 * sending an expected number of serviceAdded and serviceRemoved events.
 */
public class ReRegisterBadEquals extends ReRegisterGoodEquals {

    /** Constructs an instance of this class. Initializes this classname,
     *  and sets the sub-categories to which this test and its children belong.
     */
    public ReRegisterBadEquals() {
    }//end constructor

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  3. Creates a template that will match the test services based on
     *     service type only
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        testDesc = ""+getnLookupServices()+" lookup service(s), "+getnServices()
                       +" service(s) with poorly-defined equals() method";
        testServiceType  = AbstractBaseTest.TEST_SERVICE_BAD_EQUALS;
        return this;
    }//end construct

}//end class ReRegisterBadEquals

