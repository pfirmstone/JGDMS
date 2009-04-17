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

import net.jini.discovery.LookupLocatorDiscovery;

import net.jini.core.discovery.LookupLocator;

import java.util.ArrayList;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when the parameter input to the constructor
 * contains at least one element that is a duplicate of another element in
 * the input set, the <code>LookupLocatorDiscovery</code> utility operates
 * as if the constructor was invoked with the duplicates removed from the
 * input set.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more initial lookup services started during setup
 *   <li> an instance of the lookup locator discovery utility constructed
 *        using a set of locators in which at least 1 element duplicates
 *        at least 1 other element in the set
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        locator discovery utility
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then the
 * client's listener will receive the expected number of discovery events,
 * with the expected contents.
 *
 */
public class ConstructorDups extends AbstractBaseTest {

    protected LookupLocator[] dupLocs = null;
    protected ArrayList newLookups = new ArrayList(11);

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        /* Create a set of locators to discover that contain duplicates */
        int len1 = allLookupsToStart.size();
        int len2 = 2*len1;
        for(int i=0;i<len1;i++) {
            LocatorGroupsPair pair
                                = (LocatorGroupsPair)allLookupsToStart.get(i);
            newLookups.add(i,pair);
        }//end loop
        for(int i=len1;i<len2;i++) {
            LocatorGroupsPair pair
                          = (LocatorGroupsPair)allLookupsToStart.get(i-len1);
            newLookups.add(i,pair);
        }//end loop
        dupLocs = toLocatorArray(newLookups);
    }//end setup

    /** Executes the current test by doing the following:
     * <p><ul>
     *   <li> constructs a lookup locator discovery utility using a set of
     *        locators in which at least 1 element of the set is a duplicate
     *        of at least 1 other element of the set
     *   <li> starts the unicast discovery process for the lookup locator
     *        discovery utility just constructed by adding a discovery 
     *        listener
     *   <li> verifies that the lookup locator discovery utility under
     *        test sends the expected discovered events, with the expected
     *        contents
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Create LookupLocatorDiscovery instance using the new locs */
        logger.log(Level.FINE, "constructing LookupLocatorDiscovery to discover -- ");
        for(int i=0;i<dupLocs.length;i++) {
            logger.log(Level.FINE, "   "+dupLocs[i]);
        }//end loop
        LookupLocatorDiscovery newLLD
                   = new LookupLocatorDiscovery(dupLocs,
		            getConfig().getConfiguration());
        locatorDiscoveryList.add(newLLD);
        /* Verify discovery */
        doDiscovery(newLookups,newLLD,mainListener);
    }//end run

}//end class ConstructorDups

