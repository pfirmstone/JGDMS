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

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import net.jini.core.discovery.LookupLocator;

import java.util.ArrayList;

/**
 * With respect to the <code>setLocators</code> method, this class verifies
 * that the <code>LookupLocatorDiscovery</code> utility operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that upon invoking the <code>setLocators</code> method to re-configure
 * the <code>LookupLocatorDiscovery</code> utility to discover a new set of
 * locators which replaces the current set of locators to discover, and
 * which contains some (but not all) of the locators with which it was
 * previously configured, that utility will send discarded events referencing
 * the previously discovered lookup services whose locators do not belong to
 * the new set of locators to discover.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services
 *   <li> one instance of LookupLocatorDiscovery configured to discover the
 *        set of locators whose elements are the locators of each lookup
 *        service that was started
 *   <li> one DiscoveryListener registered with that LookupLocatorDiscovery
 *   <li> after discovery, the LookupLocatorDiscovery utility is re-configured
 *        to discover a new set of locators; a set that contains only some
 *        of the locators with which it was originally configured
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * listener will receive the expected discarded events, having the expected
 * contents.
 *
 */
public class SetLocatorsReplaceSome extends Discovered {

    protected LookupLocator[] newLocatorsToDiscover = new LookupLocator[0];
    protected ArrayList oldLookupsToDiscover = initLookupsToStart;
    protected ArrayList newLookupsToDiscover = new ArrayList(11);
    protected ArrayList newLocatorsList = new ArrayList(11);

    protected boolean changeAll = false;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        /* Change the locators for the lookup services at an even index.
         * Change locators at an odd index as well if changeAll is true.
         */
        for(int i=0;i<oldLookupsToDiscover.size();i++) {
            LocatorGroupsPair oldPair =
                                (LocatorGroupsPair)oldLookupsToDiscover.get(i);
            LookupLocator oldLoc = oldPair.locator;
            String[] oldGroups   = oldPair.groups;
            String oldHost       = oldLoc.getHost();
            int    oldPort       = oldLoc.getPort();
            String newHost       = new String(oldHost);
            int    newPort       = oldPort;
            if( ((i%2) == 0) || changeAll ) {//index is even or changeAll
                newHost = new String(oldHost+"-new");
                newPort = oldPort+11;
            }//endif
            LookupLocator newLoc = QAConfig.getConstrainedLocator(newHost,newPort);
            LocatorGroupsPair newPair = new LocatorGroupsPair
                                                           (newLoc,oldGroups);
            newLookupsToDiscover.add(i,newPair);
            newLocatorsList.add(i,newLoc);
        }//end loop
        newLocatorsToDiscover =(LookupLocator[])(newLocatorsList).toArray
                                   (new LookupLocator[newLocatorsList.size()]);
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> re-configures the lookup locator discovery utility to discover
     *          the set of locators whose elements are the locators of each
     *          lookup service that was started during setup
     *     <li> starts the unicast discovery process by adding a discovery
     *          listener to the lookup locator discovery utility
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> invokes the setLocators method on the lookup locator discovery
     *          utility to re-configure that utility with a new set of
     *          locators to discover
     *     <li> verifies that the lookup locator discovery utility under test
     *          sends the expected discarded events
     * </ul>
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "re-configure LookupLocatorDiscovery to discover -- ");
        for(int i=0;i<newLocatorsToDiscover.length;i++) {
            logger.log(Level.FINE, "   "+newLocatorsToDiscover[i]);
        }//end loop

        /* Reset the listener's expected discard state */
        mainListener.setLookupsToDiscover(newLookupsToDiscover);
        locatorDiscovery.setLocators(newLocatorsToDiscover);
        waitForDiscard(mainListener);
    }//end run

}//end class SetLocatorsReplaceSome

