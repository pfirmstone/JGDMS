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
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.GroupsUtil;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;

import java.util.ArrayList;
import java.util.List;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when the parameter input to the constructor
 * contains at least 1 element that is a duplicate of another element in
 * the input set, the <code>LookupDiscovery</code> utility operates
 * as if the constructor was invoked with the duplicates removed from the
 * input set.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more initial lookup services started during construct
 *   <li> an instance of the lookup discovery utility constructed
 *        using a set of groups in which at least 1 element duplicates
 *        at least 1 other element in the set
 *   <li> one instance of DiscoveryListener registered with the lookup
 *        discovery utility
 * </ul><p>
 * 
 * If the lookup discovery utility functions as specified, then the client's
 * listener will receive the expected number of discovery events, with the
 * expected contents.
 */
public class ConstructorDups extends AbstractBaseTest {

    protected final ArrayList newLookups = new ArrayList(11);
    protected volatile String[] dupGroups = DiscoveryGroupManagement.NO_GROUPS;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	/* Create a set of groups to discover that contain duplicates */
        List<LocatorGroupsPair> lookupsToStart = getAllLookupsToStart();
	int len1 = lookupsToStart.size();
	int len2 = 2*len1;
	for(int i=0;i<len1;i++) {
	    LocatorGroupsPair pair = lookupsToStart.get(i);
	    newLookups.add(i,pair);
	}//end loop
	for(int i=len1;i<len2;i++) {
	    LocatorGroupsPair pair = lookupsToStart.get(i-len1);
	    newLookups.add(i,pair);
	}//end loop
	dupGroups = toGroupsArray(newLookups);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> constructs a lookup discovery utility using a set of groups in
     *         which at least 1 element of the set is a duplicate of at least
     *         1 other element of the set
     *    <li> starts the multicast discovery process for the lookup discovery
     *         utility just constructed by adding a discovery listener
     *    <li> verifies that the lookup discovery utility under test sends the
     *         expected discovered events, with the expected contents
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Create LookupDiscovery instance using the new groups */
        logger.log(Level.FINE, "creating a new "
                          +"LookupDiscovery configured to discover -- ");
        GroupsUtil.displayGroupSet(dupGroups,"  group",
                                   Level.FINE);
        LookupDiscovery newLD = 
	    new LookupDiscovery(dupGroups, getConfig().getConfiguration());
        lookupDiscoveryList.add(newLD);
        /* Verify discovery - set the expected groups to discover */
        mainListener.setLookupsToDiscover(newLookups,dupGroups);
        /* Add the listener to the LookupDiscovery utility */
        newLD.addDiscoveryListener(mainListener);
        /* Wait for the discovery of the expected lookup service(s) */
        waitForDiscovery(mainListener);
    }//end run

}//end class ConstructorDups

