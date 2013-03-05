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

package com.sun.jini.test.spec.discoveryservice.event;

import java.util.logging.Level;

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import com.sun.jini.test.share.DiscoveryProtocolSimulator;
import com.sun.jini.test.share.GroupsUtil;

import net.jini.discovery.LookupDiscoveryRegistration;

import net.jini.core.lookup.ServiceRegistrar;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * With respect to the <code>removeGroups</code> method, this class verifies
 * that the lookup discovery service operates in a manner consistent with the
 * specification. In particular, this class verifies that upon re-configuring
 * the lookup discovery service to discover a new set of member groups for
 * each of its registrations, containing only some of the groups with which
 * it was previously configured, that service will send to each registration's
 * listener a discarded event for each previously discovered lookup service
 * that does not belong to any of the groups from the new set.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each belonging to a finite set of
 *        member groups
 *   <li> one instance of the lookup discovery service
 *   <li> one or more registration with the lookup discovery service
 *   <li> each registration with the lookup discovery service requests that
 *        some of the lookup services be discovered through only group
 *        discovery, some through only locator discovery, and some through
 *        both group and locator discovery
 *   <li> after discovery, using the removeGroups method of each registration,
 *        the lookup discovery service is re-configured with a new set of
 *        groups to discover; a set that contains only some of the groups
 *        with which it was originally configured
 * </ul><p>
 * 
 * If the lookup discovery service utility functions as specified, then
 * for each discarded lookup service a <code>RemoteDiscoveryEvent</code>
 * indicating a discarded event will be sent to each registration's listener.
 */
public class RemoveGroupsSome extends AbstractBaseTest {

    protected volatile String[] groupsToRemove = new String[] {"LDSGroup0_A"};
    protected volatile Map groupsMap = new HashMap(1);
    protected final HashSet proxiesRemoved = new HashSet(11);

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     *
     *  Retrieves additional configuration values. 
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        useDiscoveryList = getUseGroupAndLocDiscovery0();
        groupsMap        = getPassiveCommDiscardMap(useDiscoveryList);
        discardType      = ACTIVE_DISCARDED;
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   <ul>
     *     <li> registers with the lookup discovery service, requesting
     *          the discovery of the the desired lookup services using the
     *          desired discovery protocol
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovery events
     *     <li> verifies that the lookup discovery service utility under test
     *          sends the expected number of events - containing the expected
     *          set of member groups
     *     <li> invokes the removeGroups method on each registration with the
     *          lookup discovery service to re-configure that utility with a
     *          new set of groups to discover
     *     <li> verifies that the lookup discovery service sends the expected
     *          events to each registration's listener
     *   </ul>
     */
    public void run() throws Exception {
        setGroupsToRemove(groupsMap);
        logger.log(Level.FINE, "run()");
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, 
                      "lookup discovery service registration_"+i+" --");
            doRegistration(getGroupsToDiscoverByIndex(i),
                           getLocatorsToDiscoverByIndex(i),
                           i, leaseDuration);
        }//end loop
        waitForDiscovery();
        removeGroupsDo(groupsToRemove);
        waitForDiscard(discardType);
    }//end run

    /** Common code, shared by this class and its sub-classes, that is 
     *  invoked by the run() method. This method constructs the set of
     *  groups to remove from each registration's managed set of groups.
     */
    void setGroupsToRemove(Map groupsMap) {
        ArrayList groupsList = new ArrayList();
        Iterator iter = getGenMap().keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            DiscoveryProtocolSimulator curGen = 
                                       (DiscoveryProtocolSimulator)iter.next();
            String[]         curGroups   = curGen.getMemberGroups();
            ServiceRegistrar lookupProxy = curGen.getLookupProxy();
            if( ((i%2) == 0) && (groupsMap.containsKey(lookupProxy)) ) {
                for(int j=0;j<curGroups.length;j++) {
                    groupsList.add(curGroups[j]);
                }//end loop
                proxiesRemoved.add(lookupProxy);
	    }//endif
        }//end loop
        groupsToRemove = (String[])(groupsList).toArray
                                              (new String[groupsList.size()]);
    }//end setGroupsToRemove

    /** Common code, shared by this class and its sub-classes, that is 
     *  invoked by the run() method. This method invokes the removeGroups()
     *  method on each registration.
     */
    void removeGroupsDo(String[] groupsToRemove) throws Exception {
        Set eSet = getRegistrationMap().entrySet();
        Iterator iter = eSet.iterator();
        for(int j=0;iter.hasNext();j++) {
            Map.Entry pair = (Map.Entry)iter.next();
            LookupDiscoveryRegistration ldsReg =
                                    (LookupDiscoveryRegistration)pair.getKey();

            LDSEventListener regListener = (LDSEventListener)pair.getValue();
            RegistrationInfo regInfo = regListener.getRegInfo();
            int rID = regInfo.getHandback();
	    logger.log(Level.FINE, 
		       "  registration_"+rID
		       +" -- request removal of groups");
	    if((groupsToRemove!=null)&&(groupsToRemove.length<=0)) {
		logger.log(Level.FINE, "   NO_GROUPS");
	    } else {
		GroupsUtil.displayGroupSet(groupsToRemove,
					   "   removeGroup",Level.FINE);
	    }//endif
	    setExpectedDiscardedMap(regInfo);
	    ldsReg.removeGroups(groupsToRemove);
        }//end loop(j)
    }//end removeGroupsDo

    void setExpectedDiscardedMap(RegistrationInfo regInfo) {
        Map gMap = getPassiveCommDiscardMap
                              ( getGroupListToUseByIndex(regInfo.getHandback()) );
        Map expectedMap = getExpectedDiscardedMap(regInfo,discardType);
        Set kSet = expectedMap.keySet();
        Iterator iter = kSet.iterator();
        for(int j=0;iter.hasNext();j++) {
            ServiceRegistrar lookupProxy = (ServiceRegistrar)iter.next();
            if(    !gMap.containsKey(lookupProxy)
                || !proxiesRemoved.contains(lookupProxy) )
            {
                iter.remove();
	    }//endif
	}//end loop
    }//end setExpectedDiscardedMap

} //end class RemoveGroupsSome

