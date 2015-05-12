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
package org.apache.river.test.share;

import org.apache.river.qa.harness.AdminManager;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.test.share.BaseQATest.ToJoinPair;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryService;

/**
 *
 * @author peter
 */
public class LookupDiscoveryServices {
    /** the logger */
    private static final Logger logger = Logger.getLogger("org.apache.river.qa.harness");
    private final int testType;
    private final int nLookupDiscoveryServices;
    private final int nAddLookupDiscoveryServices;
    private final int nRemoteLookupDiscoveryServices;
    private final int nAddRemoteLookupDiscoveryServices;
    private final String remoteHost;
    /* Data structures - lookup discovery services */
    private final List<ToJoinPair> initLDSToStart;// No mutation after construction
    private final List<ToJoinPair> addLDSToStart;// No mutation after construction
    private final List<ToJoinPair> allLDSToStart;// No mutation after construction
    private final List<LookupDiscoveryService> ldsList;// Synchronized list
    private final List expectedServiceList;// Externally supplied list - concurrent.
    private final QAConfig config;
    private final AdminManager admin;
    
    public LookupDiscoveryServices(QAConfig config, AdminManager admin, List expectedServiceList){
        this.config = config;
        this.admin = admin;
        initLDSToStart = new ArrayList<ToJoinPair>(11);
        addLDSToStart  = new ArrayList<ToJoinPair>(11);
        allLDSToStart  = new ArrayList<ToJoinPair>(11);
        ldsList = Collections.synchronizedList(new ArrayList<LookupDiscoveryService>(3));
        this.expectedServiceList = expectedServiceList;
        testType = config.getIntConfigVal("org.apache.river.testType",
                                   BaseQATest.AUTOMATIC_LOCAL_TEST);
        /* begin lookup discovery service info */
        int nLookupDiscoveryServ = config.getIntConfigVal
                           ("net.jini.discovery.nLookupDiscoveryServices",
                             0);
        int nRemoteLookupDiscoveryServ = config.getIntConfigVal
                         ("net.jini.discovery.nRemoteLookupDiscoveryServices",
                           0);
        int nAddLookupDiscoveryServ = config.getIntConfigVal
                           ("net.jini.discovery.nAddLookupDiscoveryServices",
                             0);

        int nAddRemoteLookupDiscoveryServ = config.getIntConfigVal
                      ("net.jini.discovery.nAddRemoteLookupDiscoveryServices",
                       0);
        if(testType == BaseQATest.MANUAL_TEST_REMOTE_COMPONENT) {
            nLookupDiscoveryServ = nRemoteLookupDiscoveryServ;
            nAddLookupDiscoveryServ = nAddRemoteLookupDiscoveryServ;
            nRemoteLookupDiscoveryServ = 0;
            nAddRemoteLookupDiscoveryServ = 0;
        }//endif
        this.nLookupDiscoveryServices = nLookupDiscoveryServ;
        this.nRemoteLookupDiscoveryServices = nRemoteLookupDiscoveryServ;
        this.nAddLookupDiscoveryServices = nAddLookupDiscoveryServ;
        this.nAddRemoteLookupDiscoveryServices = nAddRemoteLookupDiscoveryServ;
        int tmpN =   nLookupDiscoveryServ
                   + nAddLookupDiscoveryServ
                   + nRemoteLookupDiscoveryServ
                   + nAddRemoteLookupDiscoveryServ;
        if(tmpN > 0) {
            logger.log(Level.FINE,
                          " ----- Lookup Discovery Service Info ----- ");
            logger.log(Level.FINE, " # of lookup discovery services to start  -- {0}", 
                    nLookupDiscoveryServ);
            logger.log(Level.FINE, " # of additional lookup discovery srvcs   -- {0}", 
                    nAddLookupDiscoveryServ);
        }//endif(tmpN > 0)
    /* Handle remote/local components of manual tests */
        remoteHost = config.getStringConfigVal("net.jini.lookup.remotehost",
                                            "UNKNOWN_HOST");
        switch(testType) {
            case BaseQATest.MANUAL_TEST_REMOTE_COMPONENT:
                logger.log(Level.FINE, " ***** REMOTE COMPONENT OF A MANUAL TEST "
                        +"(remote host = {0}) ***** ", remoteHost);
                break;
            case BaseQATest.MANUAL_TEST_LOCAL_COMPONENT:
                logger.log(Level.FINE, " ***** LOCAL COMPONENT OF A MANUAL TEST "
                        +"(remote host = {0}) ***** ", remoteHost);
                logger.log(Level.FINE,
                       " ----- Remote Lookup Discovery Service Info ----- ");
                logger.log(Level.FINE, " # of remote lookup discovery services    -- {0}", 
                        nRemoteLookupDiscoveryServ);
                logger.log(Level.FINE, " additional remote lookup discovery srvcs -- {0}", 
                        nAddRemoteLookupDiscoveryServ);
                break;
        }//end switch(testType)
        
        /** Retrieves and stores the information needed to configure any lookup
         *  discovery services that will be started for the current test run.
         */

        /* Retrieve groups/locators each lookup discovery service should join*/
        int n0 = 0;
        int n1 = n0 + nRemoteLookupDiscoveryServ;
        for(int i=0;i<n1;i++) {//initial remote lookup discovery services
            /* Locators and groups to join */
            String tojoinArg = config.getServiceStringProperty
                                ("net.jini.discovery.LookupDiscoveryService",
                                 "tojoin", i);

            initLDSToStart.add(getToJoinPair(tojoinArg));
        }//end loop
        /*Remote lookup discovery servicess started after initial remote LDSs*/
        n0 = n1;
        n1 = n0 + nAddRemoteLookupDiscoveryServ;
        for(int i=n0;i<n1;i++) {//additional remote lookup discovery services
            /* Locators and groups to join */
            String tojoinArg = config.getServiceStringProperty
                                ("net.jini.discovery.LookupDiscoveryService",
                                 "tojoin", i);
            /* Use NON-unique groups to join */
            addLDSToStart.add(getToJoinPair(tojoinArg));
        }//end loop
        /* Handle all lookup discovery services to be started locally */
        n0 = n1;
        n1 = n0 + nLookupDiscoveryServ;
        for(int i=n0;i<n1;i++) {//initial local lookup discovery services
            /* Locators and groups to join */
            String tojoinArg = config.getServiceStringProperty
                                ("net.jini.discovery.LookupDiscoveryService",
                                 "tojoin", i);
            if(testType == BaseQATest.AUTOMATIC_LOCAL_TEST) {//use unique group names
                tojoinArg = config.makeGroupsUnique(tojoinArg);
            }//endif
            initLDSToStart.add(getToJoinPair(tojoinArg));
        }//end loop
        /* The LDSs to start after the initial LDSs */
        n0 = n1;
        n1 = n0 + nAddRemoteLookupDiscoveryServ;
        for(int i=n0;i<n1;i++) {//additional local lookup discovery services
            /* Locators and groups to join */
            String tojoinArg = config.getServiceStringProperty
                                ("net.jini.discovery.LookupDiscoveryService",
                                 "tojoin", i);
            if(testType == BaseQATest.AUTOMATIC_LOCAL_TEST) {//use unique group names
                tojoinArg = config.makeGroupsUnique(tojoinArg);
            }//endif
            addLDSToStart.add(getToJoinPair(tojoinArg));
        }//end loop
        /* Populate the ArrayList allLDSToStart */
        for(int i=0;i<initLDSToStart.size();i++) {
            allLDSToStart.add(initLDSToStart.get(i));
        }//end loop
        for(int i=0;i<addLDSToStart.size();i++) {
            allLDSToStart.add(addLDSToStart.get(i));
        }//end loop

    }
    
    /** Convenience method that examines the given <code>String</code>
     *  containing a comma-separated list of groups and locators to join,
     *  and returns a <code>String</code> array containing the items that
     *  correspond to the groups to join.
     */
    private String[] getGroupsFromToJoinArg(String tojoinArg) {
        String[] tojoin = config.parseString(tojoinArg,",");
        if(tojoin == null) return DiscoveryGroupManagement.ALL_GROUPS;
        if(tojoin.length == 0) return DiscoveryGroupManagement.NO_GROUPS;
        ArrayList<String> tojoinList = new ArrayList<String>(tojoin.length);
        for(int i=0;i<tojoin.length;i++) {
            if( !config.isLocator(tojoin[i]) ) tojoinList.add(tojoin[i]);
        }//end loop
        return tojoinList.toArray(new String[tojoinList.size()]);
    }//end getGroupsFromToJoinArg

    
    /** Convenience method that examines the given <code>String</code>
     *  containing a comma-separated list of groups and locators to join,
     *  and returns a <code>LookupLocator</code> array containing the items
     *  that correspond to the locators to join.
     */
    //this method obtain constrainable locators because the locators are
    // administratively set after the service is started. It's not clear whether
    // this can be discarded in favor of the new initialLookupLocators configuration
    // entry
    private LookupLocator[] getLocatorsFromToJoinArg(String tojoinArg) {
        String[] tojoin = config.parseString(tojoinArg,",");
        if(tojoin == null) return new LookupLocator[0];
        if(tojoin.length == 0) return new LookupLocator[0];
        ArrayList<LookupLocator> tojoinList = new ArrayList<LookupLocator>(tojoin.length);
        for(int i=0;i<tojoin.length;i++) {
            try {
                tojoinList.add(QAConfig.getConstrainedLocator(tojoin[i]));
            } catch(MalformedURLException e) {
                continue;//not a valid locator (must be group), try next one
            }
        }//end loop
        return tojoinList.toArray(new LookupLocator[tojoinList.size()]);
    }//end getLocatorsFromToJoinArg

    private ToJoinPair getToJoinPair(String tojoinArg){
        /* Do NOT use unique groups names since clocks on the local
         * and remote sides are not synchronized, and host names are
         * different
         */
        LookupLocator[] locsToJoin = getLocatorsFromToJoinArg(tojoinArg);
        String[] groupsToJoin = getGroupsFromToJoinArg(tojoinArg);
        return new ToJoinPair(locsToJoin, groupsToJoin);
    }
    
    
    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup discovery services
     *  needed by that test run. Useful when all of the lookup discovery
     *  services are to be started during construct processing.
     * @throws Exception 
     */
    protected void startAllLDS() throws Exception {
        startInitLDS();
        startAddLDS();
    }//end startAllLDS

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup discovery services
     *  INITIALLY needed by that test run. Useful when an initial set of
     *  lookup discovery services are to be started during construct processing,
     *  and (possibly) an additional set of lookup discovery services are to
     *  be started at some later time, after the test has already begun
     *  execution.
     * @throws Exception 
     */
    protected void startInitLDS() throws Exception {
        if(nLookupDiscoveryServices > 0) {
            /* Skip over remote LDSs to the indices of the local LDSs */
            int n0 = nRemoteLookupDiscoveryServices 
                              + nAddRemoteLookupDiscoveryServices;
            int n1 = n0 + nLookupDiscoveryServices;
            for(int i=n0;i<n1;i++) {
                startLDS(i, initLDSToStart.get(i));
            }//end loop
        }//endif(nLookupDiscoveryServices > 0)
    }//end startInitLDS

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, any additional lookup discovery services 
     *  needed by that test run. Useful when an initial set of lookup discovery
     *  services are to be started during construct processing, and an additional
     *  set of lookup discovery services are to be started at some later time,
     *  after the test has already begun execution.
     * @throws Exception 
     */
    protected void startAddLDS() throws Exception {
        if(nAddLookupDiscoveryServices > 0) {
            /* Skip over remote LDSs and LDSs already started to the
             * indices of the additional local LDSs
             */
            int n0 = nRemoteLookupDiscoveryServices
                                  + nAddRemoteLookupDiscoveryServices
                                           + ldsList.size();
            int n1 = n0 + nAddLookupDiscoveryServices;
            for(int i=n0;i<n1;i++) {
                int j = i-n0;
                startLDS(i, addLDSToStart.get(j));
            }//end loop
        }//endif(nAddLookupDiscoveryServices > 0)
    }//end startAddLDS

    /** Convenience method that can be used to start, at any point during
     *  the current test run, a single lookup discovery service with
     *  configuration referenced by the given parameter values. Useful when
     *  individual lookup discovery services are to be started at different
     *  points in time during the test run, or when a set of lookup discovery
     *  services are to be started from within a loop.
     * @param indx
     * @param tojoinPair 
     * @throws Exception  
     */
    private void startLDS(int indx, ToJoinPair tojoinPair) throws Exception {
        logger.log(Level.FINE, " starting lookup discovery service {0}", indx);
	/* the returned proxy is already prepared using the preparer named
	 * by the service preparername property
	 */
        LookupDiscoveryService ldsProxy =
             (LookupDiscoveryService)(admin.startService
                                ("net.jini.discovery.LookupDiscoveryService"));
        /* Force non-unique groups for manual tests */
        if(    (testType == BaseQATest.MANUAL_TEST_REMOTE_COMPONENT)
            || (testType == BaseQATest.MANUAL_TEST_LOCAL_COMPONENT) ) 
        {
            if(ldsProxy instanceof Administrable) {
                Object adminis = ((Administrable)ldsProxy).getAdmin();
		adminis = config.prepare("test.fiddlerAdminPreparer", adminis);
                if(adminis instanceof JoinAdmin) {
                    ((JoinAdmin)adminis).setLookupGroups(tojoinPair.getGroups());
                }//endif
            }//endif
        }//endif
        ldsList.add( ldsProxy );
        expectedServiceList.add( ldsProxy );
        LocatorsUtil.displayLocatorSet(tojoinPair.getLocators(),
                                    "  locators to join",Level.FINE);
        GroupsUtil.displayGroupSet(tojoinPair.getGroups(),
                                    "  groups to join",Level.FINE);
    }//end startLDS

}
