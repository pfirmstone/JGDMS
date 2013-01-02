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

package com.sun.jini.test.spec.discoverymanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.BaseQATest;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

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
abstract public class AbstractBaseTest extends BaseQATest {

    protected static final int BY_GROUP = 1;
    protected static final int BY_LOC   = 2;
    protected static final int BY_BOTH  = 3;

    protected static final int[][] discoverBy = 
               { 
                 {BY_BOTH, BY_GROUP, BY_LOC, BY_BOTH, BY_GROUP, BY_LOC, 
                  BY_BOTH, BY_GROUP, BY_LOC, BY_BOTH, BY_GROUP, BY_LOC},

                 {BY_GROUP, BY_GROUP, BY_GROUP, BY_GROUP, BY_GROUP, BY_GROUP, 
                  BY_GROUP, BY_GROUP, BY_GROUP, BY_GROUP, BY_GROUP, BY_GROUP},

                 {BY_GROUP, BY_GROUP, BY_BOTH, BY_GROUP, BY_GROUP, BY_BOTH, 
                  BY_GROUP, BY_GROUP, BY_BOTH, BY_GROUP, BY_GROUP, BY_BOTH},

                 {BY_LOC, BY_LOC, BY_LOC, BY_LOC, BY_LOC, BY_LOC, 
                  BY_LOC, BY_LOC, BY_LOC, BY_LOC, BY_LOC, BY_LOC},

                 {BY_LOC, BY_BOTH, BY_LOC, BY_BOTH, BY_LOC, BY_BOTH, 
                  BY_LOC, BY_BOTH, BY_LOC, BY_BOTH, BY_LOC, BY_BOTH}
               };
    /* Indices into the rows of the above matrix */
    protected static final int MIX               = 0;
    protected static final int ALL_BY_GROUP      = 1;
    protected static final int BY_GROUP_AND_BOTH = 2;
    protected static final int ALL_BY_LOC        = 3;
    protected static final int BY_LOC_AND_BOTH   = 4;

    protected LookupDiscoveryManager discoveryMgr = null;
    protected ArrayList ldmList = new ArrayList(1);
    protected LookupListener mainListener = null;

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
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
	/* Start group and locator discovery by creating a lookup 
	 * discovery  manager.
	 */
	logger.log(Level.FINE,
		   "creating a lookup discovery manager "
		   +"initially configured to discover NO_GROUPS "
		   +"and NO_LOCATORS");
	/* discover no groups at first, wait for test to call setGroups */
	discoveryMgr = 
	    new LookupDiscoveryManager(DiscoveryGroupManagement.NO_GROUPS,
				       new LookupLocator[0],
				       null,
				       config.getConfiguration());
	ldmList.add(discoveryMgr);
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
            for(int i=0;i<ldmList.size();i++) {
                DiscoveryManagement ldm
                             = (DiscoveryManagement)ldmList.get(i);
                logger.log(Level.FINE, 
			   "terminating LookupDiscoveryManager instance "+i);
                ldm.terminate();
            }//end loop
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
	    super.tearDown();
	}
    }//end tearDown

    /** Convenience method that encapsulates basic discovery processing.
     *  Use this method when it is necessary to specify the lookup discovery
     *  manager used for discovery, as well as the set of locators, and the
     *  set of groups to discover.
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
     *          manager
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovered events
     *  </ul>
     *  @throws com.sun.jini.qa.harness.TestException
     */
    protected void doDiscovery(List locGroupsListStartedLookups,
                               LookupDiscoveryManager ldm,
                               LookupListener listener,
                               LookupLocator[] locsToDiscover,
                               String[] groupsToDiscover)
                                                        throws TestException,
                                                               IOException
    {
        logger.log(Level.FINE, "locators to discover -- ");
        for(int i=0;i<locsToDiscover.length;i++) {
            logger.log(Level.FINE, "   "+locsToDiscover[i]);
        }//end loop
        logger.log(Level.FINE, "groups to discover -- ");
        if(groupsToDiscover == DiscoveryGroupManagement.ALL_GROUPS) {
            logger.log(Level.FINE, "   ALL_GROUPS");
        } else {
            if(groupsToDiscover.length == 0) {
                logger.log(Level.FINE, "   NO_GROUPS");
            } else {
                for(int i=0;i<groupsToDiscover.length;i++) {
                    logger.log(Level.FINE, "    "+groupsToDiscover[i]);
                }//end loop
            }//endif
        }//end loop
        /* Set the expected locs and groups to discover */
        listener.setLookupsToDiscover(locGroupsListStartedLookups,
                                      locsToDiscover,
                                      groupsToDiscover);
        /* Re-configure LookupDiscoveryManager to discover given locators */
        ldm.setLocators(locsToDiscover);
        /* Re-configure LookupDiscoveryManager to discover given groups */
        ldm.setGroups(groupsToDiscover);
        /* Add the given listener to the LookupDiscoveryManager utility */
        ldm.addDiscoveryListener(listener);
        /* Wait for the discovery of the expected lookup service(s) */
        waitForDiscovery(listener);
    }//end doDiscovery

    /** Convenience method that encapsulates basic discovery processing.
     *  Use this method when a lookup discovery manager different from
     *  the standard one created during construct is to be used for discovery,
     *  and you know the row from the static discoverBy matrix to use when
     *  determining the lookups to discover by group, by locator, and by both.
     *  @throws com.sun.jini.qa.harness.TestException
     */
    protected void doDiscovery(List locGroupsListStartedLookups,
                               LookupDiscoveryManager ldm,
                               LookupListener listener,
                               int discoverByRow) throws TestException,
                                                               IOException
    {
        /* Build groups to Discover from member groups of started lookups */
        doDiscovery
          (locGroupsListStartedLookups,
           ldm,
           listener,
           toLocatorsToDiscover(locGroupsListStartedLookups,discoverByRow),
           toGroupsToDiscover(locGroupsListStartedLookups,discoverByRow) );
    }//end doDiscovery

    /** Convenience method that encapsulates basic discovery processing.
     *  Use this method when the standard lookup discovery manager
     *  created during construct is to be used for discovery, and you know
     *  the row from the static discoverBy matrix to use when determining
     *  the lookups to discover by group, by locator, and by both.
     *  @throws com.sun.jini.qa.harness.TestException
     */
    protected void doDiscovery(ArrayList locGroupsListStartedLookups,
                               LookupListener listener,
                               int discoverByRow) throws TestException,
                                                               IOException
    {
        doDiscovery
          (locGroupsListStartedLookups,
           discoveryMgr,
           listener,
           toLocatorsToDiscover(locGroupsListStartedLookups,discoverByRow),
           toGroupsToDiscover(locGroupsListStartedLookups,discoverByRow) );
    }//end doDiscovery

    /** This method returns an array of LookupLocator instances, where
     *  each element corresponds to an element of the given list of 
     *  LocatorGroupsPair instances. The 'key' used to search for and select
     *  the desired elements from the given list is the value of the
     *  discoverByRow parameter, which corresponds to one of the rows of
     *  the discoverBy matrix whose elements indicate which discovery
     *  mechanism(s) (group, locator or both) are to be used to discover
     *  the corresponding lookup service.
     */
    public static LookupLocator[] toLocatorsToDiscover(List list,
                                                       int discoverByRow)
    {
        List locList = new ArrayList(list.size());
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            if(    (discoverBy[discoverByRow][i] == BY_BOTH)
                || (discoverBy[discoverByRow][i] == BY_LOC) )
            {
                locList.add(pair.getLocator());
            }//endif
        }//end loop
        return 
         (LookupLocator[])(locList.toArray(new LookupLocator[locList.size()]));
    }//end toLocatorsToDiscover

    /** This method returns a String array, where each element corresponds to
     *  one of the member groups of the lookup services contained in the given
     *  list of LocatorGroupsPair instances. The 'key' used to search for and
     *  select the desired elements from the given list is the value of the
     *  discoverByRow parameter, which corresponds to one of the rows of
     *  the discoverBy matrix whose elements indicate which discovery
     *  mechanism(s) (group, locator or both) are to be used to discover
     *  the corresponding lookup service.
     */
    public static String[] toGroupsToDiscover(List list,
                                              int discoverByRow)
    {
        List groupsList = new ArrayList(11);
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            String[] curGroups = pair.getGroups();
            if(curGroups.length == 0) continue;//skip NO_GROUPS
            if(    (discoverBy[discoverByRow][i] == BY_BOTH)
                || (discoverBy[discoverByRow][i] == BY_GROUP) )
            {
                for(int j=0;j<curGroups.length;j++) {
                    groupsList.add(new String(curGroups[j]));
                }//end loop(j)
            }//endif
        }//end loop(i)
        return ((String[])(groupsList).toArray(new String[groupsList.size()]));
    }//end toGroupsToDiscover

}//end class AbstractBaseTest


