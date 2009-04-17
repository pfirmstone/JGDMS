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

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.AttributesUtil;

import net.jini.lookup.JoinManager;

import net.jini.core.entry.Entry;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is constructed with a
 * given set of attributes, the method <code>getAttributes</code> returns
 * a set of attributes whose contents are equal to the contents of the
 * set of attributes with which the join manager was constructed.
 * 
 */
public class GetAttributes extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> starts N lookup service(s) (where N may be 0) whose member
     *          groups are finite and unique relative to the member groups
     *          of all other lookup services running within the same multicast
     *          radius of the new lookup services
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, a non-null set of attributes to register with
     *          the service, and a non-null instance of a lookup discovery
     *          manager configured to discover the lookup services started in
     *          the previous step (if any)
     *   </ul>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        /* Discover & join lookups just started */
        logger.log(Level.FINE, "creating a service ID join manager ...");
        joinMgrSrvcID = new JoinManager(testService,serviceAttrs,serviceID,
                                        getLookupDiscoveryManager(),leaseMgr,
					sysConfig.getConfiguration());
        joinMgrList.add(joinMgrSrvcID);
    }//end setup

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the set of attributes returned by the method
     *   <code>getAttributes</code> is the same as the set of attributes
     *   with which the join manager was constructed during setup.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        verifyAttrsInJoinMgr(joinMgrSrvcID,serviceAttrs);
        logger.log(Level.FINE, "attributes from join manager "
                          +"equal to expected attributes");
    }//end run

} //end class GetAttributes


