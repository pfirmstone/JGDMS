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

package com.sun.jini.test.spec.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is constructed, the
 * service and its corresponding set of attributes input to the constructor
 * are registered with all lookup services the join manager is configured to
 * discover (through its <code>DiscoveryManagement</code> instance).
 * 
 */
public class RegisterAttributes extends Register {

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the test service input to the join manager constructor
     *   is registered with all lookup services the join manager is configured
     *   to discover (through its <code>DiscoveryManagement</code> instance);
     *   additionally, verifies that the test service's corresponding set of 
     *   attributes are associated with the test service in each lookup service
     *   in which that test service is registered.
     */
    public void run() throws Exception {
        super.run();

        verifyPropagation(serviceAttrs);
        logger.log(Level.FINE, ""
                          +": join manager successfully registered "
                          +"initial attributes with all "
                          +curLookupListSize("RegisterAttributes.run")
                          +" lookup service(s)");
    }//end run

} //end class RegisterAttributes


