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

import net.jini.discovery.LookupDiscovery;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * class operates in a manner consistent with the specification. In
 * particular, this class verifies that when the parameter input to the
 * constructor contains at least 1 <code>null</code> element, a
 * <code>NullPointerException</code> is thrown.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> an instance of the lookup discovery utility constructed
 *        using a set of groups in which at least 1 element is null
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then upon
 * attempting to construct an instance of the lookup discovery utility
 * using a set of groups containing at least one <code>null</code>
 * element, a <code>NullPointerException</code> will occur.
 */
public class ConstructorNullElement extends AbstractBaseTest {

    protected String[] nullGroups = new String[] {"group0","group1",
                                                   null,
                                                  "group3","group4"};
    protected LookupDiscovery newLD = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        delayLookupStart = true;//don't start lookups, just want config info
        super.setup(sysConfig);
    }//end setup

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that a <code>NullPointerException</code> is thrown when
     *   an instance of <code>LookupDiscovery</code> is created using a set
     *   of groups in which at least one element is <code>null</code>.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Create an instance of LookupDiscovery using null element */
        try {
            logger.log(Level.FINE, "creating a new "
                              +"LookupDiscovery configured to discover -- ");
            GroupsUtil.displayGroupSet(nullGroups,"  group",
                                       Level.FINE);
            newLD = new LookupDiscovery(nullGroups,
                                        getConfig().getConfiguration());
            lookupDiscoveryList.add(newLD);
        } catch(NullPointerException e) {
            logger.log(Level.FINE, "NullPointerException on "
                              +"construction of LookupDiscovery as expected");
            return;
        }
        String errStr = new String("no NullPointerException");
        logger.log(Level.FINE, errStr);
        throw new TestException(errStr);
    }//end run

}//end class ConstructorNullElement


