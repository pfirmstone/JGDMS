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
import org.apache.river.test.share.LocatorsUtil;

import net.jini.discovery.LookupLocatorDiscovery;

import net.jini.core.discovery.LookupLocator;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code>
 * utility operates in a manner consistent with the specification.
 * In particular, this class verifies that when the parameter input to
 * the <code>addLocators</code> method contains at least one <code>null</code>
 * element, a <code>NullPointerException</code> is thrown.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> zero lookup services
 *   <li> an instance of the lookup locator discovery utility that is
 *        constructed using an array of locators consistent with the
 *        the test's configuration
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified,
 * then when the <code>addLocators</code> method is invoked with a
 * set of locators in which at least one element is <code>null</code>,
 * a <code>NullPointerException</code> will occur.
 *
 */
public class AddLocatorsNullElement extends ConstructorNullElement {

    protected LookupLocator[] configLocs = new LookupLocator[0];

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
     *     <li> first verifies that the lookup locator discovery utility is
     *          currently configured to discover a non-null, non-empty set
     *          of locators
     *     <li> invokes addLocators with a set of locators in which at least
     *          one element is null
     *     <li> verifies that a NullPointerException is thrown upon the
     *          invocation of addLocators
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* First verify lld is initially configured with the expected locs */
        LookupLocator[] lldLocs = lld.getLocators();
	if (!LocatorsUtil.compareLocatorSets(lldLocs, configLocs,Level.FINE)) {
	    throw new TestException("Locator set are not equivalent");
	}
        /* Try to add array of locators in which at least 1 element is null */
        try {
            lld.addLocators(nullLocs);
            String errStr = new String("no NullPointerException");
            logger.log(Level.FINE, errStr);
            throw new TestException(errStr);
        } catch(NullPointerException e) {
            logger.log(Level.FINE, 
		       "NullPointerException on locator addition as expected");
        }
    }//end run

}//end class AddLocatorsNullElement

