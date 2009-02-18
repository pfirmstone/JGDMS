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

package com.sun.jini.test.spec.lookupdiscovery;
import com.sun.jini.qa.harness.QAConfig;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.GroupsUtil;

/**
 * With respect to the <code>getGroups</code> method, this class verifies
 * that the <code>LookupDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that the <code>getGroups</code> method returns a a new array upon each
 * invocation.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> no lookup services
 *    <li> one instance of the lookup discovery utility
 *    <li> the lookup discovery utility is initially configured to discover
 *         a finite set of groups (not NO_GROUPS and not ALL_GROUPS)
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then on each
 * separate invocation of the <code>getGroups</code> method, a new array
 * will be returned that contains the member groups with which the lookup
 * discovery utility is currently configured to discover.
 */
public class GetGroupsNew extends GetGroups {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> retrieves the member groups the lookup discovery utility
     *         under test is initially configured to discover
     *    <li> verifies that the set of groups the lookup discovery utility
     *         was configured to discover initially is the same as the set
     *         of groups returned by getGroups
     *    <li> invokes getGroups two more times and verifies that each
     *         invocation returns different arrays having the same contents
     * </ul>
     */
    public void run() throws Exception {
        super.run();
        logger.log(Level.FINE, "1st call to getGroups ...");
        String[] groups0 = newLD.getGroups();
        logger.log(Level.FINE, "2nd call to getGroups ...");
        String[] groups1 = newLD.getGroups();

        logger.log(Level.FINE,
                         "groups returned by 1st call to getGroups --");
        GroupsUtil.displayGroupSet(groups0,"  group0",
                                   Level.FINE);

        logger.log(Level.FINE,
                         "groups returned by 2nd call to getGroups --");
        GroupsUtil.displayGroupSet(groups1,"  group1",
                                   Level.FINE);

        /* Verify the two group set references are unique references */
        if(groups0 == groups1) {
            throw new TestException(
                                 "same array returned on different calls");
        }//endif
        logger.log(Level.FINE, "comparing groups from 1st "
                          +"call with groups from 2nd call ...");
        if( GroupsUtil.compareGroupSets(groups0,groups1, Level.OFF) ) {
            logger.log(Level.FINE, "group sets are equal");
        } else {
            throw new TestException("group sets are NOT equal");
        }//endif
    }//end run

}//end class GetGroupsNew

