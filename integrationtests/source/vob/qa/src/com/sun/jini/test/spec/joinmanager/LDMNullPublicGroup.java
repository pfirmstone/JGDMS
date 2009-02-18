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

package com.sun.jini.test.spec.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;

import net.jini.lookup.JoinManager;

import net.jini.core.discovery.LookupLocator;

import java.util.ArrayList;

/**
 * This class verifies that when <code>null</code> is input to the
 * <code>discoveryMgr</code> parameter of either version of the constructor
 * of the <code>JoinManager</code> utility class, those constructors
 * operate as specified. That is,
 * <p>
 * "... an instance of the  <code>LookupDiscoveryManager</code> utility class
 * will be constructed to listen for events announcing the discovery of only
 * those lookup services that are members of the public group."
 *
 * Related bug ids: 4306089
 * 
 */
public class LDMNullPublicGroup extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> creates an instance of the JoinManager using the version of
     *          the constructor that takes ServiceIDListener (callback),
     *          inputting an instance of a test service and null to all
     *          other arguments
     *     <li> creates an instance of the JoinManager using the version of
     *          the constructor that takes a service ID, inputting 
     *          an instance of a test service and null to all other arguments
     *   </ul>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        logger.log(Level.FINE, "creating a callback join manager ...");
        joinMgrCallback = new JoinManager(testService,serviceAttrs,callback,
                                          discoveryMgr,leaseMgr,
					  sysConfig.getConfiguration());
        joinMgrList.add(joinMgrCallback);
        logger.log(Level.FINE, "creating a service ID join manager ...");
        joinMgrSrvcID = new JoinManager(testService,serviceAttrs,serviceID,
                                        discoveryMgr,leaseMgr,
					sysConfig.getConfiguration());
        joinMgrList.add(joinMgrSrvcID);
    }//end setup

    /** Executes the current test by doing the following:
     * <p>
     *   For each instance of the <code>JoinManager</code> utility class
     *   created during the setup process,
     *   <ul>
     *     <li> retrieves the instance of DiscoveryManagement being
     *          employed by the JoinManager
     *     <li> verifies that the instance of DiscoveryManagement that is
     *          retrieved is an instance of the LookupDiscoveryManager
     *          utility class
     *     <li> verifies that the instance of DiscoveryManagement that is
     *          retrieved is configured to discover only lookup services
     *          that are members of the public group
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Callback join manager */
        logger.log(Level.FINE, 
                          "retrieving the discovery manager from the "
                          +"callback join manager ...");
        DiscoveryManagement dmCallback = joinMgrCallback.getDiscoveryManager();
        if(dmCallback == null) {
            throw new TestException("could not successfully retrieve "
                                 +"the discovery manager");
        } else {
            logger.log(Level.FINE, 
                              "discovery manager successfully retrieved");
        }//endif
        if( !(dmCallback instanceof LookupDiscoveryManager) ) {
            throw new TestException(
                                 "callback join manager -- "
                                 +"discovery manager is NOT an "
                                 +"instance of LookupDiscoveryManager");
        }//endif
        LookupLocator[] locsCallback =
                           ((LookupDiscoveryManager)dmCallback).getLocators();
        if( (locsCallback != null) && (locsCallback.length > 0) ) {
            throw new TestException(
                                 "callback join manager -- "
                                 +"discovery manager has a NON-empty "
                                 +"managed set of locators");
        }//endif
        logger.log(Level.FINE, "retrieving the managed set of groups ...");
        String[] groupsCallback = 
                             ((LookupDiscoveryManager)dmCallback).getGroups();

        if(groupsCallback == DiscoveryGroupManagement.ALL_GROUPS) {
            throw new TestException(
                                 "callback join manager -- discovery "
                                 +"manager configured for ALL_GROUPS");
        } else {//(groupsCallback != DiscoveryGroupManagement.ALL_GROUPS)
            logger.log(Level.FINE, 
                              "  discovery manager configured to discover "
                              +groupsCallback.length+" group(s)");
            for(int i=0;i<groupsCallback.length;i++){
                if( (new String("")).equals(groupsCallback[0]) ) {
                    logger.log(Level.FINE, 
                            "  groups["+i+"] = the public group");
                } else {
                    logger.log(Level.FINE, 
                          "  groups["+i+"] = "+groupsCallback[i]);
                }
            }//end loop

            if( (groupsCallback != null) && (groupsCallback.length == 0) ) {
                throw new TestException(
                                 "callback join manager -- discovery "
                                 +"manager configured for NO_GROUPS");
            }
            if( (groupsCallback != null) && (groupsCallback.length > 1) ) {
                throw new TestException(
                               "callback join manager -- discovery "
                               +"manager configured for more than one group");
            }
            if( !( (new String("")).equals(groupsCallback[0]) ) ) {
                throw new TestException(
                               "callback join manager -- discovery "
                               +"manager configured for one NON-public group "
                               +"("+groupsCallback[0]+")");
            }
        }//endif(groupsCallback == DiscoveryGroupManagement.ALL_GROUPS)

        /* Service ID join manager */
        logger.log(Level.FINE, 
                          "retrieving the discovery manager from the "
                          +"service ID join manager ...");
        DiscoveryManagement dmSrvcID = joinMgrSrvcID.getDiscoveryManager();
        if(dmSrvcID == null) {
            throw new TestException("could not successfully retrieve "
                                 +"the discovery manager");
        } else {
            logger.log(Level.FINE, 
                              "discovery manager successfully retrieved");
        }//endif
        if( !(dmSrvcID instanceof LookupDiscoveryManager) ) {
            throw new TestException(
                                 "service ID join manager -- "
                                 +"discovery manager is NOT an "
                                 +"instance of LookupDiscoveryManager");
        }//endif
        LookupLocator[] locsSrvcID = 
                            ((LookupDiscoveryManager)dmSrvcID).getLocators();
        if( (locsSrvcID != null) && (locsSrvcID.length > 0) ) {
            throw new TestException(
                                 "service ID join manager -- "
                                 +"discovery manager has a NON-empty "
                                 +"managed set of locators");
        }//endif
        logger.log(Level.FINE, 
                          "retrieving the managed set of groups ...");
        String[] groupsSrvcID = ((LookupDiscoveryManager)dmSrvcID).getGroups();

        if(groupsSrvcID == DiscoveryGroupManagement.ALL_GROUPS) {
            throw new TestException(
                                 "service ID join manager -- discovery "
                                 +"manager configured for ALL_GROUPS");
        } else {//(groupsSrvcID != DiscoveryGroupManagement.ALL_GROUPS)
            logger.log(Level.FINE, 
                              "  discovery manager configured to discover "
                              +groupsSrvcID.length+" group(s)");
            for(int i=0;i<groupsSrvcID.length;i++){
                if( (new String("")).equals(groupsSrvcID[0]) ) {
                    logger.log(Level.FINE, 
                                  "  groups["+i+"] = the public group");
                } else {
                    logger.log(Level.FINE, 
                                  "  groups["+i+"] = "+groupsSrvcID[i]);
                }//endif
            }//end loop

            if( (groupsSrvcID != null) && (groupsSrvcID.length == 0) ) {
                throw new TestException(
                                 "service ID join manager -- discovery "
                                 +"manager configured for NO_GROUPS");
            }//endif
            if( (groupsSrvcID != null) && (groupsSrvcID.length > 1) ) {
                throw new TestException(
                                 "service ID join manager -- discovery manager"
                                 +"configured for more than one group");
            }//endif
            if( !( (new String("")).equals(groupsSrvcID[0]) ) ) {
                throw new TestException(
                                 "service ID join manager -- discovery manager"
                                 +"configured for one NON-public group "
                                 +"("+groupsSrvcID[0]+")");
            }//endif
        }//endif(groupsSrvcID == DiscoveryGroupManagement.ALL_GROUPS)
    }//end run
} //end class LDMNullPublicGroup


