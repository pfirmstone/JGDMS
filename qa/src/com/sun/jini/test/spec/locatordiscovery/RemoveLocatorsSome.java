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
import net.jini.core.discovery.LookupLocator;

import java.util.ArrayList;
import java.util.List;

/**
 * With respect to the <code>removeLocators</code> method, this class
 * verifies that the <code>LookupLocatorDiscovery</code> utility operates
 * in a manner consistent with the specification. In particular, this class
 * verifies that upon invoking the <code>removeLocators</code> method to
 * remove some (but not all) of the locators with which the lookup locator
 * discovery utility was previously configured to discover, that utility will
 * send discarded events referencing the previously discovered lookup services
 * whose locators correspond to the locators that were removed.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, removeLocators is invoked to remove some (but not
 *        all) of the locators with which the lookup locator discovery utility
 *        was originally configured to discover
 * </ul><p>
  *
 */
public class RemoveLocatorsSome extends Discovered {

    protected volatile List curLookupsToDiscover = new ArrayList(0);
    protected final List newLookupsToDiscover = new ArrayList(11);
    protected final List lookupsToRemoveList = new ArrayList(11);
    protected volatile LookupLocator[] locsToRemove = new LookupLocator[0];

    protected volatile boolean changeAll = false;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        curLookupsToDiscover = getInitLookupsToStart();
        /* Remove the locators for the lookup services at an even index.
         * Remove locators at an odd index as well if changeAll is true.
         */
        for(int i=0;i<curLookupsToDiscover.size();i++) {
            LocatorGroupsPair curPair =
                                (LocatorGroupsPair)curLookupsToDiscover.get(i);
            if( ((i%2) == 0) || changeAll ) {//index is even or changeAll
                lookupsToRemoveList.add(curPair.getLocator());
            } else {
                newLookupsToDiscover.add(curPair);
            }//endif
        }//end loop
        locsToRemove =(LookupLocator[])(lookupsToRemoveList).toArray
                               (new LookupLocator[lookupsToRemoveList.size()]);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> re-configures the lookup locator discovery utility to discover
     *          the set of locators whose elements are the locators of each
     *          lookup service that was started during construct
     *     <li> starts the unicast discovery process by adding a discovery
     *          listener to the lookup locator discovery utility
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> invokes the removeLocators method on the lookup locator
     *          discovery utility to remove the indicated locators
     *     <li> verifies that the lookup locator discovery utility under test
     *          sends the expected discarded events
     * </ul>
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "remove locators from LookupLocatorDiscovery -- ");
        for(int i=0;i<locsToRemove.length;i++) {
            logger.log(Level.FINE, "   "+locsToRemove[i]);
        }//end loop
        /* Reset the listener's expected discard state */
        mainListener.setLookupsToDiscover(newLookupsToDiscover);
        locatorDiscovery.removeLocators(locsToRemove);
        waitForDiscard(mainListener);
    }//end run

}//end class RemoveLocatorsSome

