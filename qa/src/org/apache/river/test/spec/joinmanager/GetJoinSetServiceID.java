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

package org.apache.river.test.spec.joinmanager;

import java.util.logging.Level;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

import net.jini.lookup.JoinManager;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when the constructor that takes a 
 * <code>ServiceID</code> in its argument list is used to construct a
 * join manager that registers a service with N lookup services,
 * the method <code>getJoinSet</code> returns an array containing the
 * same lookup services with which the service was registered by the
 * join manager.
 * 
 */
public class GetJoinSetServiceID extends GetJoinSetCallback {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> starts N lookup service(s) whose member groups are finite
     *          and unique relative to the member groups of all other lookup
     *          services running within the same multicast radius of the new
     *          lookup services
     *     <li> creates an instance of the JoinManager using the version of
     *          the constructor that takes ServiceID, inputting an instance
     *          of a test service and a non-null instance of a lookup
     *          discovery manager configured to discover the lookup services
     *          started in the previous step
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        callbackJM = false;//create a "serviceID join manager"
        super.construct(sysConfig);
        return this;
    }//end construct

} //end class GetJoinSetServiceID


