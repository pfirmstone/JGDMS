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

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that the method <code>getRegistrars</code> returns
 * an array of <code>ServiceRegistrar</code> instances in which each element
 * of the array is a proxy to one of the lookup services the lookup discovery
 * utility has discovered.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *    <li> one or more lookup services, each belonging to a finite set of
 *         member groups
 *    <li> one instance of LookupDiscovery configured to discover the union
 *         of the member groups to which each lookup belongs 
 *    <li> one instance of DiscoveryListener registered with that
 *         LookupDiscovery utility
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the
 * <code>getRegistrars</code> method will return an array containing the
 * same registrars as those that were started (and ultimately discovered
 * by the lookup discovery utility).
 */
public class GetRegistrars extends Discovered {

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> re-configures the lookup discovery utility to use group
     *         discovery to discover the set of lookup services started during
     *         construct
     *    <li> starts the multicast discovery process by adding a listener to
     *         the lookup discovery utility
     *    <li> verifies that the lookup discovery utility under test
     *         sends the expected discovered events, with the expected
     *         contents
     *    <li> invokes getRegistrars to retrieve the registrars the lookup
     *         discovery utility has currently discovered
     *    <li> compares the registrars returned by getRegistrars with the 
     *         registrars that were started during construct (and discovered
     *         by the lookup discovery utility), and verifies that those sets
     *         are the same
     * </ul>
     */
    public void run() throws Exception {
        /* Verify the lookups discovered are the lookups that were started */
        super.run();
        logger.log(Level.FINE, "calling getRegistrars ... ");
        ServiceRegistrar[] regs = ldToUse.getRegistrars(); // prepared by lds
        List lusList = getLookupListSnapshot("GetRegistrars.run");
        logger.log(Level.FINE,
                          "# of lookups started = "+lusList.size()
                          +", # of registrars from LookupDiscovery = "
                          +regs.length);
        if(regs.length != lusList.size()) {
            throw new TestException(
                                 "# of registrars from LookupDiscovery ("
                                 +regs.length+") != # of lookups started ("
                                 +lusList.size()+")");
        }//endif
        /* Compare lookups returned by lookup discovery with those started */
        for(int i=0;i<regs.length;i++) {
            LookupLocator curLoc = QAConfig.getConstrainedLocator(regs[i].getLocator());
            if( !lusList.contains(regs[i]) ) {
                throw new TestException(
                                     "registrar from "
                                     +"LookupLocatorDiscover NOT in set "
                                     +"of started lookups -- "
                                     +curLoc);
            } else {
                logger.log(Level.FINE, "  OK -- "+curLoc);
            }//endif
        }//end loop
    }//end run

}//end class GetRegistrars

