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

package com.sun.jini.test.impl.locatordiscovery;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.DiscoveryServiceUtil;
import java.util.logging.Level;

import com.sun.jini.test.spec.locatordiscovery.AbstractBaseTest;
import net.jini.core.lookup.ServiceRegistrar;

/**
 * With respect to the current implementation of the
 * <code>LookupLocatorDiscovery</code> utility, this class verifies
 * that if the <code>discard</code> method is invoked, discovery for the
 * the discarded <code>ServiceRegistrar</code> is delayed.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one lookup service
 *   <li> one instance of LookupLocatorDiscovery configured to perform discovery
 *        using the locator for the previously started lookup service.
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery.
 *   <li> after discovery, the lookup service is discarded.
 *   <li> after a configured pause, specified by property
 *        com.sun.jini.test.impl.locatordiscovery.discardDelayFirstWait
 *        it makes sure that the LUS is still
 *        undiscovered.
 *   <li> it waits for the LUS to be discovered for a time specified by the
 *        property com.sun.jini.test.impl.locatordiscovery.discardDelay.
 * </ul><p>
 * 
 * If the <code>LookupLocatorDiscovery</code> utility functions as intended,
 * upon invoking the <code>discard</code> method, the rediscovery of the LUS
 * must be appropriately delayed.
 *
 */
public class DelayDiscoveryAfterDiscard extends AbstractBaseTest {
    long firstWait;
    long nextWait;
    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
	firstWait = sysConfig.getLongConfigVal(
	    "com.sun.jini.test.impl.locatordiscovery.discardDelayFirstWait",
	    5000);
	nextWait = sysConfig.getLongConfigVal(
	    "com.sun.jini.test.impl.locatordiscovery.discard",
	    30000);
        return this;
    }//end construct
    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> configures the lookup locator discovery utility to discover
     *         the set of locators whose elements are the locators of each
     *         lookup service that was started during construct
     *    <li> starts the unicast discovery process by adding a discovery
     *         listener to the lookup locator discovery utility
     *    <li> verifies that the lookup locator discovery utility under test
     *         sends the expected discovered events, having the expected 
     *         contents
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        doDiscovery(getLookupServices().getInitLookupsToStart(), mainListener);
	logger.log(Level.FINE, "calling getRegistrars ... ");
        ServiceRegistrar[] regs = locatorDiscovery.getRegistrars();
	// there should only be one here, but we'll discard all of them
	for (int i = 0; i < regs.length; i++) {
	    locatorDiscovery.discard(regs[i]);
	    logger.log(Level.FINE, "Discarding registrar: " + regs[i]);
	}
	DiscoveryServiceUtil.delayMS(firstWait);
	if (locatorDiscovery.getRegistrars().length != 0) {
	    // Discovery was not delayed enough
	    throw new TestException("Not enough discovery delay");
	}
	DiscoveryServiceUtil.delayMS(nextWait);
	if (locatorDiscovery.getRegistrars().length == 0) {
	    throw new TestException("Rediscovery not complete");
	}
    }//end run
}
