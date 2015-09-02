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

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.LocatorsUtil;
import org.apache.river.qa.harness.Test;
import net.jini.discovery.LookupLocatorDiscovery;

import net.jini.core.discovery.LookupLocator;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular, this
 * class verifies that the method <code>getLocators</code> returns
 * an array of <code>LookupLocator</code> instances in which each element
 * of the array corresponds to a specific lookup service the lookup locator
 * discovery utility is currently configured to discover.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> zero lookup services
 *    <li> an instance of the lookup locator discovery utility that is
 *         constructed using an array of locators consistent with the
 *         the test's configuration
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * <code>getLocators</code> method will return an array containing the
 * same locators as those used to configure this test.
 *
 */
public class GetLocators extends AbstractBaseTest {

    protected LookupLocator[] configLocs = new LookupLocator[0];
    protected LookupLocatorDiscovery lld = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        delayLookupStart = true;//don't start lookups, just want config info
        super.construct(sysConfig);
        configLocs = toLocatorArray(getInitLookupsToStart());
        lld = new LookupLocatorDiscovery(configLocs, sysConfig.getConfiguration());
        locatorDiscoveryList.add(lld);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> invokes getLocators to retrieve the locators the lookup
     *         locator discovery utility is currently configured to discover
     *    <li> compares the locators returned by getLocators with the 
     *         locators used to initially configure this test, and verifies
     *         that those sets are the same
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        LookupLocator[] lldLocs = lld.getLocators();
	if (!LocatorsUtil.compareLocatorSets(lldLocs, configLocs,Level.FINE)) {
	    throw new TestException("Locator set are not equivalent");
	}
    }//end run

}//end class GetLocators

