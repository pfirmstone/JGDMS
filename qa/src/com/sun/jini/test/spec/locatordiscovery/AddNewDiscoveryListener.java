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

package com.sun.jini.test.spec.locatordiscovery;

import java.util.logging.Level;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * With respect to the <code>addDiscoveryListener</code> method, this class
 * verifies that the <code>LookupLocatorDiscovery</code> utility operates
 * in a manner consistent with the specification. In particular, this class
 * verifies that upon adding a new instance of <code>DiscoveryListener</code>,
 * the <code>LookupLocatorDiscovery</code> utility will, for each lookup
 * service that was previously discovered, send a discovered event containing
 * the set of member groups with which the lookup service was configured.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each with a finite set of member groups
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, a new instance of DiscoveryListener is added to
 *        the LookupLocatorDiscovery utility through the invocation of the
 *        addDiscoveryListener method
 * </ul><p>
 * 
 * If the <code>LookupLocatorDiscovery</code> utility functions as specified,
 * then a <code>DiscoveryEvent</code> instance indicating a discovered event
 * will be sent to the new listener for each lookup service that was 
 * previously discovered; and that event will accurately reflect the set
 * of member groups with which the lookup service was configured.
 *
 */
public class AddNewDiscoveryListener extends Discovered {

    protected LookupListener newListener = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        newListener = new AbstractBaseTest.LookupListener();
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   <ul>
     *     <li> re-configures the lookup locator discovery utility to discover
     *          the set of locators whose elements are the locators of each
     *          lookup service that was started during construct
     *     <li> starts the unicast discovery process by adding a listener to
     *          the lookup locator discovery utility
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> invokes the addDiscoveryListener method on the 
     *          lookup locator discovery utility to add a new listener for
     *          the original set of lookups to discover
     *     <li> verifies that the lookup locator discovery utility under test
     *          sends the expected discovered events, with the expected
     *          contents
     *   </ul>
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "adding a new listener to "
		   +"LookupLocatorDiscovery ... ");
        newListener.setLookupsToDiscover(getInitLookupsToStart());
        locatorDiscovery.addDiscoveryListener(newListener);
        waitForDiscovery(newListener);
    }//end run

}//end class AddNewDiscoveryListener

