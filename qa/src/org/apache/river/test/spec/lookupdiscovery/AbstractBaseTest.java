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

package org.apache.river.test.spec.lookupdiscovery;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;

import org.apache.river.test.share.BaseQATest;

import org.apache.river.qa.harness.TestException;

import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class is an abstract class that acts as the base class which
 * most, if not all, tests of the <code>LookupDiscovery</code> utility
 * class should extend.
 * 
 * This abstract class contains a static inner class that can be
 * used as a listener to participate in the multicast announcement,
 * multicast request, and unicast request protocols on behalf of the
 * tests that sub-class this abstract class.
 * <p>
 * This class provides an implementation of the <code>construct</code> method
 * which performs standard functions related to the initialization of the
 * system state necessary to execute the test.
 *
 * Any test class that extends this class is required to implement the 
 * <code>run</code> method which defines the actual functions that must
 * be executed in order to verify the assertions addressed by that test.
 */
abstract public class AbstractBaseTest extends BaseQATest implements Test {

    protected volatile LookupDiscovery lookupDiscovery = null;
    protected final List<LookupDiscovery> lookupDiscoveryList = new CopyOnWriteArrayList<LookupDiscovery>();
    protected volatile LookupListener mainListener = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *    <li> starts the desired number lookup services (if any) with
     *         the desired configuration
     *    <li> creates an instance of lookup discovery to start the multicast
     *         discovery process
     *    <li> creates a default listener for use with the lookup discovery
     *         utility
     * </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
	/* Start group discovery by creating a lookup discovery  utility */
	logger.log(Level.FINE,
		   "creating a lookup discovery initially "
		   +"configured to discover NO_GROUPS");
	/* discover no groups at first, wait for test to call setGroups */
	lookupDiscovery = new LookupDiscovery
	    (DiscoveryGroupManagement.NO_GROUPS,
	     sysConfig.getConfiguration());
	lookupDiscoveryList.add(lookupDiscovery);
	mainListener = new LookupListener();
        return this;
    }

    /** Executes the current test
     */
    abstract public void run() throws Exception;

    /** Cleans up all state. Terminates the lookup discovery utilities that
     *  may have been created, shutdowns any lookup service(s) that may
     *  have been started, and performs any standard clean up duties performed
     *  in the super class.
     */
    public void tearDown() {
        try {
            /* Terminate each lookup discovery utility that was created */
            for(int i=0;i<lookupDiscoveryList.size();i++) {
                DiscoveryManagement ld 
                             = (DiscoveryManagement)lookupDiscoveryList.get(i);
                logger.log(Level.FINE, "tearDown - terminating "
                                  +"LookupDiscovery instance "+i);
                ld.terminate();
            }//end loop
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
	    super.tearDown();
	}
    }//end tearDown

    /** Convenience method that encapsulates basic discovery processing.
     *  Use this method when it is necessary to specify both the lookup
     *  discovery utility used for discovery, and the set of groups to
     *  discover.
     *  
     *  This method does the following:
     *  <p><ul>
     *     <li> uses the contents of the given ArrayList that references the
     *          locator and group information of the lookup services that
     *          have been started, together with the groups to discover,
     *          to set the lookps that should be expected to be discovered
     *          for the given listener
     *     <li> with respect to the given listener, starts the discovery
     *          process by adding that listener to the given lookup discovery
     *          utility
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovered events
     *  </ul>
     *  @throws org.apache.river.qa.harness.TestException
     */
    protected void doDiscovery(List locGroupsListStartedLookups,
                               LookupDiscovery ld,
                               LookupListener listener,
                               String[] groupsToDiscover)
                                                        throws TestException,
                                                               IOException
    {
        logger.log(Level.FINE,
                          "set groups to discover -- ");
        if(groupsToDiscover == DiscoveryGroupManagement.ALL_GROUPS) {
            logger.log(Level.FINE, "   ALL_GROUPS");
        } else {
            if(groupsToDiscover.length == 0) {
                logger.log(Level.FINE, "   NO_GROUPS");
            } else {
                for(int i=0;i<groupsToDiscover.length;i++) {
                    logger.log(Level.FINE,
                                      "   "+groupsToDiscover[i]);
                }//end loop
            }//endif
        }//end loop
        /* Set the expected groups to discover */
        listener.setLookupsToDiscover(locGroupsListStartedLookups,
                                      groupsToDiscover);
        /* Re-configure LookupDiscovery to discover given groups */
        ld.setGroups(groupsToDiscover);
        /* Add the given listener to the LookupDiscovery utility */
        ld.addDiscoveryListener(listener);
        /* Wait for the discovery of the expected lookup service(s) */
        waitForDiscovery(listener);
    }//end doDiscovery

    /** Convenience method that encapsulates basic discovery processing.
     *  Use this method when the standard lookup discovery utility
     *  created during construct is to be used for discovery, but the set of
     *  groups to discover is different than the member groups of the
     *  lookup services referenced in the locGroupsListStartedLookups
     *  parameter.
     *  @throws org.apache.river.qa.harness.TestException
     */
    protected void doDiscovery(List locGroupsListStartedLookups,
                               LookupListener listener,
                               String[] groupsToDiscover)
                                                       throws TestException,
                                                              IOException
    {
        doDiscovery(locGroupsListStartedLookups,lookupDiscovery,listener,
                    groupsToDiscover);
    }//end doDiscovery

    /** Convenience method that encapsulates basic discovery processing.
     *  Use this method when a lookup discovery utility different from
     *  the standard one created during construct is to be used for discovery,
     *  and the set of groups to discover is the same as the member groups
     *  of the lookup services referenced in the locGroupsListStartedLookups
     *  parameter.
     *  @throws org.apache.river.qa.harness.TestException
     */
    protected void doDiscovery(List locGroupsListStartedLookups,
                               LookupDiscovery ld,
                               LookupListener listener) throws TestException,
                                                               IOException
    {
        /* Build groups to Discover from member groups of started lookups */
        doDiscovery(locGroupsListStartedLookups,ld,listener,
                    toGroupsArray(locGroupsListStartedLookups));
    }//end doDiscovery

    /** Convenience method that encapsulates basic discovery processing.
     *  Use this method when the standard lookup discovery utility
     *  created during construct is to be used for discovery, and the set
     *  of groups to discover is the same as the member groups of the
     *  lookup services referenced in the locGroupsListStartedLookups
     *  parameter.
     *  @throws org.apache.river.qa.harness.TestException
     */
    protected void doDiscovery(List locGroupsListStartedLookups,
                               LookupListener listener) throws TestException,
                                                               IOException
    {
        doDiscovery(locGroupsListStartedLookups,lookupDiscovery,listener);
    }//end doDiscovery

}//end class AbstractBaseTest


