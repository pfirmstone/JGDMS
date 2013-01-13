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

package com.sun.jini.test.share;

import com.sun.jini.qa.harness.QATestEnvironment;


import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;


import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryChangeListener;
import net.jini.discovery.DiscoveryListener;


import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceRegistrar;




import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * This class is an abstract class that contains common functionality 
 * related to construct and tearDown that may be useful to many of tests.
 * acts as the base class which
 * 
 * This abstract class contains various static inner classes, any one
 * of which can be used as a listener to participate in the lookup
 * service discovery process on behalf of the tests that sub-class this
 * abstract class.
 * <p>
 * This class provides an implementation of both the <code>construct</code> method
 * and the <code>tearDown</code> method, which perform -- respectively --
 * standard functions related to the initialization and clean up of the
 * system state necessary to execute the test.
 * 
 * @see com.sun.jini.qa.harness.QAConfig
 * @see com.sun.jini.qa.harness.QATestEnvironment
 */
abstract public class BaseQATest extends QATestEnvironment {

    public static final int AUTOMATIC_LOCAL_TEST = Integer.MAX_VALUE;
    public static final int MANUAL_TEST_REMOTE_COMPONENT = 1;
    public static final int MANUAL_TEST_LOCAL_COMPONENT  = 2;

    protected volatile boolean useFastTimeout = false;//for faster completion
    protected volatile int fastTimeout = 10;//default value
    protected volatile boolean displayOn = true;//verbose in waitForDiscovery/Discard
    protected volatile boolean debugsync = false;//turns on synchronization debugging

    /**
     * @return the lookupServices
     */
    protected LookupServices getLookupServices() {
        return lookupServices;
    }

    /**
     * @return the lookupDiscoveryServices
     */
    protected LookupDiscoveryServices getLookupDiscoveryServices() {
        return lookupDiscoveryServices;
    }

    /**
     * @return the leaseRenewalServices
     */
    protected LeaseRenewalServices getLeaseRenewalServices() {
        return leaseRenewalServices;
    }

    /**
     * @return the eventMailBoxServices
     */
    protected EventMailBoxServices getEventMailBoxServices() {
        return eventMailBoxServices;
    }

    /**
     * @return the expectedServiceList
     */
    public List getExpectedServiceList() {
        return expectedServiceList;
    }

    /**
     * @return the nLookupServices
     */
    protected int getnLookupServices() {
        return lookupServices.getnLookupServices();
    }

    /**
     * @return the nAddLookupServices
     */
    protected int getnAddLookupServices() {
        return lookupServices.getnAddLookupServices();
    }

    /**
     * @return the nServices
     */
    protected int getnServices() {
        return lookupServices.getnServices();
    }

    /**
     * @return the nAddServices
     */
    protected int getnAddServices() {
        return lookupServices.getnAddServices();
    }

    /**
     * @return the nAttributes
     */
    protected int getnAttributes() {
        return lookupServices.getnAttributes();
    }

    /**
     * @return the nAddAttributes
     */
    protected int getnAddAttributes() {
        return lookupServices.getnAddAttributes();
    }

    /**
     * @return the nSecsServiceDiscovery
     */
    protected int getnSecsServiceDiscovery() {
        return lookupServices.getnSecsServiceDiscovery();
    }

    /**
     * @return the nSecsJoin
     */
    protected int getnSecsJoin() {
        return lookupServices.getnSecsJoin();
    }

    /**
     * @return the initLookupsToStart
     */
    protected List<LocatorGroupsPair> getInitLookupsToStart() {
        return lookupServices.getInitLookupsToStart();
    }

    /**
     * @return the addLookupsToStart
     */
    protected List<LocatorGroupsPair> getAddLookupsToStart() {
        return lookupServices.getAddLookupsToStart();
    }

    /**
     * @return the allLookupsToStart
     */
    protected List<LocatorGroupsPair> getAllLookupsToStart() {
        return lookupServices.getAllLookupsToStart();
    }

    /**
     * @return the lookupsStarted
     */
    protected List<LocatorGroupsPair> getLookupsStarted() {
        return lookupServices.getLookupsStarted();
    }

    /**
     * @return the genMap
     */
    protected Map<Object, String[]> getGenMap() {
        return lookupServices.getGenMap();
    }

    /** Ordered pair containing a LookupLocator and the corresponding groups */
    public static class LocatorGroupsPair {
        private final LookupLocator locator;
        private final String[]      groups;
        public LocatorGroupsPair(LookupLocator locator, String[] groups) {
            this.locator = locator;
            this.groups  = groups;
        }//end constructor
        public boolean equals(Object obj) {
            if(this == obj) return true;
            if( !(obj instanceof LocatorGroupsPair) ) return false;
            if(!((LocatorGroupsPair)obj).locator.equals(locator)) return false;
            return GroupsUtil.compareGroupSets(getGroups(), ((LocatorGroupsPair)obj).getGroups(), Level.OFF);
        }//end equals

        /**
         * @return the locator
         */
        public LookupLocator getLocator() {
            return locator;
        }

        /**
         * @return the groups
         */
        public String[] getGroups() {
            return groups.clone();
        }
    }//end class LocatorGroupsPair

    /** Data structure representing a set of LookupLocators & groups to join */
    public static class ToJoinPair {
        private final LookupLocator[] locators;
        private final String[]        groups;
        public ToJoinPair(LookupLocator[] locators, String[] groups) {
            this.locators = locators;
            this.groups   = groups;
        }//end constructor
        public ToJoinPair(LookupLocator[] locators) {
            this.locators = locators;
            this.groups   = DiscoveryGroupManagement.NO_GROUPS;
        }//end constructor
        public ToJoinPair(String[] groups) {
            this.locators = new LookupLocator[0];
            this.groups   = groups;
        }//end constructor

        /**
         * @return the locators
         */
        protected LookupLocator[] getLocators() {
            return locators.clone();
        }

        /**
         * @return the groups
         */
        protected String[] getGroups() {
            return groups.clone();
        }
    }//end class ToJoinPair

    /** Listener class used to monitor the discovery events sent from the
     *  helper utility that, on behalf of the test, participates in the
     *  multicast discovery protocol. Note that in most cases, the
     *  test cannot proceed until at least one discovery event containing
     *  at least one reference to a ServiceRegistrar is received. This class
     *  provides information that allows the test to determine whether or
     *  not to proceed.
     */
    protected class LookupListener implements DiscoveryListener {
        public LookupListener(){ }
        private final Map<LookupLocator,String[]> discoveredMap = new HashMap<LookupLocator,String[]>(11);
        private final Map<LookupLocator,String[]> discardedMap  = new HashMap<LookupLocator,String[]>(11);

        private final Map expectedDiscoveredMap = new HashMap(11);
        private final Map expectedDiscardedMap  = new HashMap(11);
        
        Set<Map.Entry<LookupLocator,String[]>> getDiscovered(){
            synchronized(this){
                Set<Map.Entry<LookupLocator,String[]>> disc = new HashSet<Map.Entry<LookupLocator,String[]>>(discoveredMap.size());
                disc.addAll(discoveredMap.entrySet());
                return disc;
            }
        }
        
        /**
         * Replaces and entry in the discovered map and removes LookupLocator
         * from the discarded map
         * @param l
         * @param groups
         * @return 
         */
        void updateDiscovered(LookupLocator l, String[] groups){
            synchronized(this){
                discardedMap.remove(l);
                discoveredMap.put(l, groups);
            }
        }
        
        int discoveredCount(){
            synchronized(this){
                return discoveredMap.size();
            }
        }

        /** Returns the locators of the lookups from discoveredMap, which
         *  contains both the lookups that are expected to be discovered,
         *  as well as the lookups that have already been discovered.
         */
        public LookupLocator[] getDiscoveredLocators() {
            synchronized(this) {
                Set kSet = discoveredMap.keySet();
                return ((LookupLocator[])(kSet).toArray
                                            (new LookupLocator[kSet.size()]));
            }//end sync(this)
        }//end getDiscoveredLocators

        /** Returns the locators that are expected to be discovered, but which
         *  have not yet been discovered.
         */
        /** Returns the locators of the lookups from expectedDiscoveredMap that
         *  are not also in discoveredMap; this method returns the locators
         *  of the lookups that are expected to be discovered, but which
         *  have not yet been discovered.
         */
        public LookupLocator[] getUndiscoveredLocators() {
            synchronized(this) {
                Set dSet = discoveredMap.keySet();
                Set eSet = expectedDiscoveredMap.keySet();
                ArrayList uLocsList = new ArrayList(eSet.size());
                Iterator iter = eSet.iterator();
                while(iter.hasNext()) {
                    LookupLocator loc = (LookupLocator)iter.next();
                    if( !dSet.contains(loc) ) uLocsList.add(loc);
                }//end loop
                return ((LookupLocator[])(uLocsList).toArray
                                       (new LookupLocator[uLocsList.size()]));
            }//end sync(this)
        }//end getUndiscoveredLocators

        public void clearAllEventInfo() {
            synchronized(this) {
                discoveredMap.clear();
                discardedMap.clear();
                expectedDiscoveredMap.clear();
                expectedDiscardedMap.clear();
            }//end sync(this)
        }//end clearAllEventInfo

        public void clearDiscoveryEventInfo() {
            synchronized(this) {
                discoveredMap.clear();
                expectedDiscoveredMap.clear();
            }//end sync(this)
        }//end clearDiscoveryEventInfo

        public void clearDiscardEventInfo() {
            synchronized(this) {
                discardedMap.clear();
                expectedDiscardedMap.clear();
            }//end sync(this)
        }//end clearDiscardEventInfo

        /** Use this method to set the contents of the discoveredMap to a
         *  specific set of values.
         */
        public void setDiscoveredMap(List locGroupsList) {
            synchronized(this) {
                discoveredMap.clear();
                discardedMap.clear();
                for(int i=0;i<locGroupsList.size();i++) {
                    LocatorGroupsPair pair =
                                      (LocatorGroupsPair)locGroupsList.get(i);
                    discoveredMap.put(pair.getLocator(), pair.getGroups());
                }//end loop
            }//end sync(this)
        }//end setDiscoveredMap

        /** Use this method to set the contents of both the discoveredMap
         *  and the expectedDiscardedMap to a specific set of values.
         */
        public void setDiscardEventInfo(List locGroupsList) {
            synchronized(this) {
                discoveredMap.clear();
                discardedMap.clear();
                for(int i=0;i<locGroupsList.size();i++) {
                    LocatorGroupsPair pair =
                                      (LocatorGroupsPair)locGroupsList.get(i);
                    /* Have to set discoveredMap so that discarded() method
                     * will place discarded lookup in the discardedMap when
                     * the discarded event arrives.
                     */
                    discoveredMap.put(pair.getLocator(), pair.getGroups());
                    expectedDiscardedMap.put(pair.getLocator(), pair.getGroups());
                }//end loop
            }//end sync(this)
        }//end clearDiscardEventInfo

        /** This method should be called whenever the lookups this listener
         *  is supposed to discover are changed during a test. This method
         *  examines the new lookups to discover and then re-sets the 
         *  current and/or expected state of the fields of this class related
         *  to the discovered/discarded event state of this listener.
         */
        public void setLookupsToDiscover(List locGroupsList) {
            setLookupsToDiscover(locGroupsList, 
                                 toLocatorArray(getLookupServices().getAllLookupsToStart()),
                                 toGroupsArray(getLookupServices().getAllLookupsToStart()));
        }//end setLookupsToDiscover

        public void setLookupsToDiscover(List locGroupsList,
                                         LookupLocator[] locatorsToDiscover)
        {
            setLookupsToDiscover(locGroupsList, 
                                 locatorsToDiscover,
                                 DiscoveryGroupManagement.NO_GROUPS);
        }//end setLookupsToDiscover

        public void setLookupsToDiscover(List locGroupsList,
                                         String[] groupsToDiscover)
        {
            setLookupsToDiscover(locGroupsList, 
                                 new LookupLocator[0],
                                 groupsToDiscover);
        }//end setLookupsToDiscover

        public void setLookupsToDiscover(List locGroupsList,
                                         LookupLocator[] locatorsToDiscover,
                                         String[] groupsToDiscover)
        {
            synchronized(this) {
                LookupLocator[] locators = toLocatorArray(locGroupsList);
                boolean discoverAll = discoverAllLookups(locGroupsList,
                                                   groupsToDiscover);
                /* The input ArrayList contains (locator,groups) pairs that
                 * represent the locator and current member groups of lookup
                 * services that have been started. Those lookup services 
                 * that satisfy one or both of the following conditions
                 * either are expected to be discovered, or have already been
                 * discovered:
                 *   - the lookup's corresponding locator is referenced in the
                 *     locatorsToDiscover parameter
                 *   - the lookup's current member groups consist of one or
                 *     more of the groups referenced in the groupsToDiscover
                 *     parameter
                 * The (locator,groups) pairs from the ArrayList that
                 * correspond to such lookup services are placed in the set
                 * that contains information related to the lookup services
                 * that are expected to be discovered.
                 */
                for(int i=0;i<locGroupsList.size();i++) {
                    LocatorGroupsPair pair =
                                      (LocatorGroupsPair)locGroupsList.get(i);
                    LookupLocator curLoc    = pair.getLocator();
                    String[]      curGroups = pair.getGroups();
                    if(    discoverByLocators(curLoc,locatorsToDiscover)
                        || discoverAll
                        || discoverByGroups(curGroups,groupsToDiscover) )
                    {
                        expectedDiscoveredMap.put(curLoc,curGroups);
                    }//endif
                }//end loop
                /* The input ArrayList contains (locator,groups) pairs that
                 * represent the locator and current member groups of lookup
                 * services that have been started. The referenced lookup
                 * services may have been previously discovered, and the
                 * current member groups may reflect some change from when
                 * the lookup service was previously discovered. The
                 * discoveredMap for this listener contains (locator,groups)
                 * pairs that correspond to lookup services that actually have
                 * been previously DISCOVERED through either locator discovery
                 * or through group discovery of the original member groups of
                 * the lookup service (or both).
                 * 
                 * For any (locator,groups) pair from the discoveredMap,
                 * the corresponding lookup service can become no longer of
                 * interest. This occurs when both of the following conditions
                 * occur:
                 *   - the lookup's corresponding locator is NOT referenced in
                 *     the locatorsToDiscover parameter (so there is no
                 *     interest in discovering that lookup service using
                 *     locator discovery)
                 *   - the lookup's current member groups consist of NONE of
                 *     the groups referenced in the groupsToDiscover parameter
                 *     (so there is no interest in discovering that lookup
                 *     service using group discovery)
                 * 
                 * Note that loss of interest in using group discovery to
                 * discover the lookup service can occur when one or both of
                 * the following conditions occurs:
                 *   - the lookup's current member groups has changed
                 *   - the contents of the groupsToDiscover parameter has
                 *     changed
                 * 
                 * When a combination of conditions - as described above - 
                 * indicate that a previously discovered lookup service
                 * (corresponding to an element of the discoveredMap) is 
                 * no longer of interest through either locator discovery
                 * or group discovery (or both), the lookup service will
                 * eventually be discarded. Thus, the corresponding
                 * (locator,groups) pair should be REMOVED from the
                 * expectedDiscoveredMap, and a pair having that lookup's
                 * corresponding locator and current member groups should
                 * be placed in the expectedDiscardedMap.
                 *
                 * Thus, for our purposes here, there are three conditions
                 * in which the lookup service will no longer be of interest:
                 *   -- the element of discoveredMap, corresponding to the
                 *      lookup service in question, corresponds to NONE of the
                 *      elements of the input ArrayList
                 *   -- the locator of the lookup service in question 
                 *      equals NONE of the elements of the locatorsToDiscover
                 *      parameter
                 *   -- NONE of the current member groups of the lookup
                 *      service in question equal any of the elements of the
                 *      groupsToDiscover parameter
                 */
                Set eSet = discoveredMap.entrySet();
	        Iterator iter = eSet.iterator();
                while(iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    LookupLocator loc = (LookupLocator)pair.getKey();
                    /* We care what the current member groups are now, as
                     * indicated by the contents of the input ArrayList;
                     * not the member groups that were originally discovered.
                     * If the groups of a previously discovered lookup, and/or
                     * the groupsToDiscover have changed in such a way that
                     * interest in the previously discovered lookup service
                     * has ceased (and we are not currently interested in 
                     * discovering that lookup service using locator 
                     * discovery), then we should expect that lookup service
                     * to be discarded.
                     *
                     * Below, the current locator from the set of previously
                     * discovered lookups is used to determine the current
                     * member groups of that lookup service. If that locator
                     * is contained in the input ArrayList, then the groups
                     * paired with the locator in that ArrayList are considered
                     * the most current; and they are used to determine if
                     * we are still interested in the lookup service. If that
                     * locator is not referenced in the input ArrayList, then
                     * we assume the lookup's current member groups are the
                     * groups associated with the locator when the lookup
                     * service was previously discovered; and those original
                     * groups are used to determine if we are still interested
                     * in the lookup service.
                     */
                    String[] memberGroups = getGroups(loc,locGroupsList);
                    if(memberGroups == null) {
                        memberGroups = (String[])pair.getValue();
                    }//endif
                    if(isElementOf(loc,locators) &&
                       (    discoverByLocators(loc,locatorsToDiscover)
                         || discoverAll
                         || discoverByGroups(memberGroups,groupsToDiscover)) )
                    {
                        continue;//still interested, go to next
                    }//endif
                    /* not interested in this lookup anymore, expect discard */
                    expectedDiscoveredMap.remove(loc);
                    expectedDiscardedMap.put(loc,memberGroups);
                }//end loop

                /* The input ArrayList contains (locator,groups) pairs that
                 * represent the locator and current member groups of lookup
                 * services that have been started. The referenced lookup
                 * services may have been previously discardred, and the
                 * current member groups may reflect some change from when
                 * the lookup service was previously discardred. The
                 * discardedMap for this listener contains (locator,groups)
                 * pairs that correspond to lookup services that actually have
                 * been previously DISCARDED for various reasons (no longer
                 * interested in that lookup's locator, groups have changed,
                 * announcements have stopped, etc.).
                 * 
                 * For any (locator,groups) pair from the discardedMap,
                 * the corresponding lookup service can change from not
                 * being of interest to now being of interest. This occurs
                 * when one of both of the following conditions occur:
                 *   - the lookup's corresponding locator is NOW referenced in
                 *     the locatorsToDiscover parameter (so there is new
                 *     interest in discovering that lookup service using
                 *     locator discovery)
                 *   - the lookup's current member groups consist of AT LEAST
                 *     ONE of the groups referenced in the groupsToDiscover
                 *     parameter (so there is new interest in discovering
                 *     that lookup service using group discovery)
                 * 
                 * Note that renewed interest in using group discovery to
                 * discover the lookup service can occur when one or both of
                 * the following conditions occurs:
                 *   - the lookup's current member groups has changed
                 *   - the contents of the groupsToDiscover parameter has
                 *     changed
                 * 
                 * When a combination of conditions - as described above - 
                 * indicate that a previously discarded lookup service
                 * (corresponding to an element of the discardedMap) is 
                 * now of interest through either locator discovery or group
                 * discovery (or both), the lookup service will eventually be
                 * re-discovered. Thus, the corresponding (locator,groups)
                 * pair should be REMOVED from the expectedDiscardedMap, and
                 * a pair having that lookup's corresponding locator and
                 * current member groups should be placed in the
                 * expectedDiscoveredMap.
                 *
                 * Thus, for our purposes here, there are three conditions
                 * that must be met for the status of the lookup service to 
                 * change from 'not of interest' to 'now of interest':
                 *   -- the element of discardedMap, corresponding to the
                 *      lookup service in question, must correspond to an
                 *      element of the input ArrayList
                 *   -- the locator of the lookup service in question must
                 *      equal one of the elements of the locatorsToDiscover
                 *      parameter
                 *   -- at least ONE of the current member groups of the lookup
                 *      service in question must equal one of the elements
                 *      of the groupsToDiscover parameter
                 */
                eSet = discardedMap.entrySet();
	        iter = eSet.iterator();
                while(iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    LookupLocator loc = (LookupLocator)pair.getKey();
                    /* We care what the current member groups are now, as
                     * indicated by the contents of the input ArrayList;
                     * not the member groups that were originally discarded.
                     * If the groups of a previously discarded lookup, and/or
                     * the groupsToDiscover have changed in such a way that
                     * we are now again interested in re-discovering the
                     * previously discarded lookup service (or we are now
                     * currently interested in discovering that lookup service
                     * using locator discovery), then we should expect that
                     * lookup service to be re-discovered.
                     *
                     * Below, the current locator from the set of previously
                     * discarded lookups is used to determine the current
                     * member groups of that lookup service. If that locator
                     * is contained in the input ArrayList, then the groups
                     * paired with the locator in that ArrayList are considered
                     * the most current; and they are used to determine if
                     * we are still interested in the lookup service. If that
                     * locator is not referenced in the input ArrayList, then
                     * we assume the lookup's current member groups are the
                     * groups associated with the locator when the lookup
                     * service was previously discarded; and those original
                     * groups are used to determine if we are still interested
                     * in the lookup service.
                     */
                    String[] memberGroups = getGroups(loc,locGroupsList);
                    if(memberGroups == null) {
                        memberGroups = (String[])pair.getValue();
                    }//endif
                    if( !isElementOf(loc,locators) ||
                        (    !discoverByLocators(loc,locatorsToDiscover)
                          && !discoverAll 
                          && !discoverByGroups(memberGroups,groupsToDiscover)))
                    {
                        continue;//still not interested, go to next
                    }//endif
                    /* now interested in this lookup, expect re-discovery */
                    expectedDiscardedMap.remove(loc);
                    expectedDiscoveredMap.put(loc,memberGroups);
                }//end loop
            }//end sync(this)
        }//end setLookupsToDiscover

//        /** Returns all of the groups (duplicates removed), across all lookup
//         *  services, that are currently expected to be discovered.
//         */
//        String[] getCurExpectedDiscoveredGroups() {
//            synchronized (this){
//                HashSet groupSet = new HashSet(expectedDiscoveredMap.size());
//                Set eSet = expectedDiscoveredMap.entrySet();
//                Iterator iter = eSet.iterator();
//                while(iter.hasNext()) {
//                    Map.Entry pair = (Map.Entry)iter.next();
//                    String[] curGroups = (String[])pair.getValue();
//                    for(int i=0;i<curGroups.length;i++) {
//                        groupSet.add(curGroups[i]);
//                    }//end loop
//                }//end loop
//                return ((String[])(groupSet).toArray(new String[groupSet.size()]));
//            }
//        }//end getCurExpectedDiscoveredGroups

        public void discovered(DiscoveryEvent evnt) {
	    /* the LDM (actually, its ld) has already prepared these registrars */
            ServiceRegistrar[] regs = evnt.getRegistrars();
            if(regs != null) {
                logger.log(Level.FINE, " discovery event received "
                                  +"-- "+regs.length+" lookup(s)");
                Map groupsMap = evnt.getGroups();
                synchronized(this) {
                    boolean oneDiscovered = false;
                    List<ServiceRegistrar> lusList = getLookupListSnapshot
                                     ("BaseQATest$LookupListenter.discovered");
                    for(int i=0;i<regs.length;i++) {
                        LookupLocator loc = null;
                        String[] groups = null;
                        boolean lookupOK = false;
                        if( lusList.contains(regs[i]) ) {
                            logger.log(Level.FINE,
                                        "     regs["+i+"] is in lookupList");
                            lookupOK = true;
                        } else {
                            logger.log(Level.FINE,
                                   "     regs["+i+"] is NOT in lookupList");
                        }//endif
                        if(    lookupOK
                            || (testType == MANUAL_TEST_LOCAL_COMPONENT) )
                        {
                            /* it's either a lookup started by the test, or
                             * it may be a remote lookup of interest
                             */
                            try {
                                loc = QAConfig.getConstrainedLocator(regs[i].getLocator());
                                groups = (String[])groupsMap.get(regs[i]);
                                /* Recall that when lookups are started, they
                                 * are first started using a default port and
                                 * belonging to NO_GROUPS, and then the port
                                 * and member groups are set -- IN THAT ORDER.
                                 * Because of this, when using an LLD to
                                 * discover lookups by locator, it's
                                 * theoretically possible that a discovery
                                 * event may arrive with the configured port,
                                 * but belonging to NO_GROUPS. This can happen
                                 * when the unicast exchange between the LLD
                                 * and the lookup occurs between the time the
                                 * lookup is started and the port is set, and
                                 * the time the member groups are set to the
                                 * configured member groups. Because of this,
                                 * the groups arriving in the discovery event
                                 * are tested for NO_GROUPS (which may be what
                                 * was actually intended, or may indicate that
                                 * the groups have not been set yet). If the
                                 * groups are NO_GROUPS, wait N seconds to
                                 * allow the groups to be set, then query
                                 * the lookup service for its member groups so
                                 * as to guarantee that the discoveredMap has
                                 * the actual member groups that were intended.
                                 */
                                if(groups.length == 0) {
                                    logger.log(Level.FINE,
                                     "   NO_GROUPS in event ... wait 5 "
                                     +"seconds for member groups to be set");
                                    DiscoveryServiceUtil.delayMS(5000);
                                    logger.log(Level.FINE,
                                    "   wait period complete ... retrieving "
                                    +"member groups from regs["+i+"]");
                                    groups = regs[i].getGroups();
                                }//endif
                            } catch(Exception e) { e.printStackTrace(); }
                            if( !lookupOK &&
                                (testType == MANUAL_TEST_LOCAL_COMPONENT))
                            {
                                /* determine if it's a LUS of interest */
                                LookupLocator[] locsToDiscover
                                           = toLocatorArray(getLookupServices().getAllLookupsToStart());
                                if(isElementOf(loc,locsToDiscover)) {
                                lookupOK = true;//is lookup of interest
                                }//endif
                            }//endif
                        }//endif
                        /* care only about the lookups of interest */
                        if( !lookupOK ) continue;
                        updateDiscovered(loc,groups);
                        LocatorsUtil.displayLocator(loc,
                                                    "  discovered locator",
                                                    Level.FINE);
                        logger.log(Level.FINE,
                                          "   discovered member group(s) = "
                                      +GroupsUtil.toCommaSeparatedStr(groups));
                        oneDiscovered = true;
                    }//end loop
                    if(oneDiscovered) {
                        logger.log(Level.FINE,
                              " number of currently discovered lookup(s) = "
                              +discoveredMap.size());
                    }
                }//end sync(this)
            } else {//(regs == null)
                logger.log(Level.FINE, " discovery event received "
                                  +"-- event.getRegistrars() returned NULL");
            }//endif(regs != null)
        }//end discovered

        public void discarded(DiscoveryEvent evnt) {
	    /* the LDM (actually, its ld) has already prepared these registrars */
            ServiceRegistrar[] regs = evnt.getRegistrars();
            if(regs != null) {
                logger.log(Level.FINE, " discard event received "
                                  +"-- "+regs.length+" lookup(s)");
                Map groupsMap = evnt.getGroups();
                synchronized(this) {
                    for(int i=0;i<regs.length;i++) {
                        /* Can't make a remote call to retrieve the locator
                         * of the discarded registrar like what was done when
                         * the registrar was discovered (since it may be down) 
                         * so get the locator from the local map of registrars
                         * to locators that was populated when each registrar
                         * was started.
                         */
                        LocatorGroupsPair pair = 
                            (LocatorGroupsPair)getLookupServices().getRegsToLocGroupsMap().get(regs[i]);
                        if(pair == null) continue;//lookup started outside test
                        LookupLocator loc = pair.getLocator();
                        /* Add to discardedMap only if previously discovered */
                        if((loc != null)&&(discoveredMap.remove(loc) != null)){
                            logger.log(Level.FINE,
                                              "   discarded locator = "+loc);
                            String[] groups = (String[])groupsMap.get(regs[i]);
                            logger.log(Level.FINE,
                                      "   discarded member group(s) = "
                                      +GroupsUtil.toCommaSeparatedStr(groups));
                            discardedMap.put(loc,groups);
                        }//endif
                    }//end loop
                    logger.log(Level.FINE,
                              " number of currently discovered lookup(s) = "
                              +discoveredMap.size());
                }//end sync(this)
            } else {//(regs == null)
                logger.log(Level.FINE, " discard event received "
                                  +"-- event.getRegistrars() returned NULL");
            }//endif(regs != null)
        }//end discarded
    }//end class LookupListener

    /** Listener class used to monitor the following events sent from the
     *  helper utility that, on behalf of the test, participates in the
     *  multicast discovery protocols:
     *  <p><ul>
     *    <li> the discovery of new lookup services
     *    <li> the re-discovery of discarded lookup services
     *    <li> the discarding of already-discovered lookup services
     *    <li> member group changes in already-discovered lookup services
     *  </ul><p>
     */
    protected class GroupChangeListener extends LookupListener
                                        implements DiscoveryChangeListener
    {
        public GroupChangeListener(){ }

        private final Map<LookupLocator,String[]> changedMap = new HashMap<LookupLocator,String[]>(11);
        private final Map<LookupLocator,String[]> expectedChangedMap = new HashMap<LookupLocator,String[]>(11);

        public void clearAllEventInfo() {
            synchronized(this) {
                super.clearAllEventInfo();
                changedMap.clear();
                expectedChangedMap.clear();
            }//end sync(this)
        }//end clearAllEventInfo

        public void clearChangeEventInfo() {
            synchronized(this) {
                changedMap.clear();
                expectedChangedMap.clear();
            }//end sync(this)
        }//end clearChangeEventInfo

        /** This method should be called whenever the lookups this listener
         *  is supposed to discover are changed during a test. This method
         *  examines the new lookups to discover and then re-sets the 
         *  current and/or expected state of the fields of this class related
         *  to the changed event state of this listener.
         */
        public void setLookupsToDiscover(List locGroupsList) {
            this.setLookupsToDiscover(locGroupsList, 
                                      toGroupsArray(getLookupServices().getAllLookupsToStart()));
        }//end setLookupsToDiscover

        public void setLookupsToDiscover(List locGroupsList,
                                         String[] groupsToDiscover)
        {
            this.setLookupsToDiscover(locGroupsList, 
                                      new LookupLocator[0],
                                      groupsToDiscover);
        }//end setLookupsToDiscover

        public void setLookupsToDiscover(List locGroupsList,
                                         LookupLocator[] locatorsToDiscover,
                                         String[] groupsToDiscover)
        {
            synchronized(this) {
                super.setLookupsToDiscover(locGroupsList,
                                           locatorsToDiscover,
                                           groupsToDiscover);
                LookupLocator[] locators = toLocatorArray(locGroupsList);
                boolean discoverAll = discoverAllLookups(locGroupsList,
                                                         groupsToDiscover);
                /* The input ArrayList contains (locator,groups) pairs that
                 * represent the locator and current member groups of lookup
                 * services that have been started. The referenced lookup
                 * services may have been previously discovered, and the
                 * current member groups may reflect some change from when
                 * the lookup service was previously discovered. The
                 * discoveredMap for this listener contains (locator,groups)
                 * pairs that correspond to lookup services that actually have
                 * been previously DISCOVERED through group discovery of the
                 * original member groups of the lookup service.
                 * 
                 * For any (locator,groups) pair from the discoveredMap,
                 * the corresponding lookup service can experience a "change"
                 * in its set of member groups. This occurs when the contents
                 * of the lookup service's set of member groups is changed
                 * in such a way that its contents differs from the contents
                 * of the member groups associated with that lookup service, in
                 * the discoveredMap, but at least one of the groups in the new
                 * set of member groups is an element of the groupsToDiscover
                 * (otherwise, a discard - rather than a change - situation
                 * has occurred). When this does occur, the lookup discovery
                 * utility will notice (through the contents of the multicast
                 * announcements), and will send to this listener, a changed
                 * event that reflects the new member groups. Thus, a new
                 * (locator,groups) pair, corresponding to the current pair
                 * in the discoveredMap - but which reflects the new member
                 * groups, should be placed in the expectedChangedMap.
                 */
                Set<Map.Entry<LookupLocator,String[]>> eSet = getDiscovered();
	        Iterator<Map.Entry<LookupLocator,String[]>> iter = eSet.iterator();
                while(iter.hasNext()) {
                    Map.Entry<LookupLocator,String[]> pair = iter.next();
                    LookupLocator loc = pair.getKey();
                    String[] oldGroups = pair.getValue();

                    if( !isElementOf(loc,locators) ) continue;
                    String[] newGroups = getGroups(loc,locGroupsList);
                    if(newGroups == null) {
                        /* The lookup with the given loc does not appear to
                         * be in the given list, and we must not be interested
                         * in discovering it by group, skip it since we don't
                         * expect changed events from such a lookup, even if
                         * its member groups are changed.
                         */
                        continue;
                    }//endif
                    boolean discoveredByGroup = false;
                    boolean discoveredByLoc   = false;
                    if(    discoverAll 
                        || discoverByGroups(oldGroups,groupsToDiscover)  )
                    {
                        discoveredByGroup = true;
                    }//endif
                    if( discoverByLocators(loc,locatorsToDiscover) ) {
                        discoveredByLoc = true;
                    }//endif
                    if(discoveredByGroup) {
                        if(GroupsUtil.compareGroupSets(oldGroups,newGroups, Level.OFF)) {
                            continue;//no change in groups, go to next
                        }//endif
                        /* If we're interested in ALL_GROUPS or if the old and
                         * new groups intersect, then expect a changed event
                         */
                        if(   (groupsToDiscover 
                                       == DiscoveryGroupManagement.ALL_GROUPS)
                            || (discoverByGroups(newGroups,oldGroups)) )
                        {
                            expectedChangedMap.put(loc,newGroups);
                        } else {//ALL old groups replaced with new groups
                            if(discoveredByLoc) {
                                expectedChangedMap.put(loc,newGroups);
                            }//endif
                        }//endif
                    } else {//discovered by only locator ==> no changed event
                        continue;
                    }//endif(discoveredByGroup)
                }//end loop
            }//end sync(this)
        }//end setLookupsToDiscover

        public void changed(DiscoveryEvent evnt) {
	    /* the LDM (actually, its ld) has already prepared these registrars */
            ServiceRegistrar[] regs = evnt.getRegistrars();
            if(regs != null) {
                logger.log(Level.FINE, " change event received "
                                  +"-- "+regs.length+" lookup(s)");
                Map groupsMap = evnt.getGroups();
                synchronized(this) {
                    boolean oneChanged = false;
                    List<ServiceRegistrar> regList = getLookupListSnapshot
                                        ("BaseQATest$LookupListenter.changed");
                    for(int i=0;i<regs.length;i++) {
                        if( regList.contains(regs[i]) ) {
                            logger.log(Level.FINE,
                                       "     regs["+i+"] is in lookupList");
                        } else {
                            logger.log(Level.FINE,
                                  "     regs["+i+"] is NOT in lookupList");
                            continue;//care only about lookups the test started
                        }//endif
                        try {
                            LookupLocator loc = QAConfig.getConstrainedLocator(regs[i].getLocator());
                            String[] groups = (String[])groupsMap.get(regs[i]);
                            changedMap.put(loc,groups);
                            updateDiscovered(loc,groups);//replace old loc
                            LocatorsUtil.displayLocator(loc,
                                                        "  locator on groups changed",
                                                        Level.FINE);
                            logger.log(Level.FINE,
                                          "   changed member group(s) = "
                                      +GroupsUtil.toCommaSeparatedStr(groups));
                        } catch(Exception e) { e.printStackTrace(); }
                        oneChanged = true;
                    }//end loop
                    if(oneChanged) {
                        logger.log(Level.FINE,
                               " number of currently changed lookup(s)    = "
                               +changedMap.size());
                        logger.log(Level.FINE,
                               " number of currently discovered lookup(s) = "
                               +discoveredCount());
                    }
                }//end sync(this)
            } else {//(regs == null)
                logger.log(Level.FINE, " change event received "
                                  +"-- event.getRegistrars() returned NULL");
            }//endif(regs != null)
        }//end changed
    }//end class GroupChangeListener

    /** Thread in which a number of lookup services are started after various
     *  time delays. This thread is intended to be used by tests that need to
     *  simulate "late joiner" lookup services. After all of the requested
     *  lookup services have been started, this thread will exit.
     */
    protected class StaggeredStartThread extends Thread {
        private final long[] waitTimes
                           = {    5*1000, 10*1000, 20*1000, 30*1000, 60*1000, 
                               2*60*1000,
                                 60*1000, 30*1000, 20*1000, 10*1000, 5*1000 };
        private final int startIndx;
        private final List locGroupsList;
        /** Use this constructor if it is desired that all lookup services
         *  be started in this thread. The locGroupsList parameter is an
         *  ArrayList that should contain LocatorGroupsPair instances that
         *  reference the locator and corresponding member groups of each
         *  lookup service to start.
         */
        public StaggeredStartThread(List locGroupsList) {
            this(0,locGroupsList);
        }//end constructor

        /** Use this constructor if a number of lookup services (equal to the
         *  value of the given startIndx) have already been started; and this
         *  thread will start the remaining lookup services. The locGroupsList
         *  parameter is an ArrayList that should contain LocatorGroupsPair
         *  instances that reference the locator and corresponding member
         *  groups of each lookup service to start.
         */
         public StaggeredStartThread(int startIndx,List locGroupsList) {
            super("StaggeredStartThread");
            setDaemon(true);
            this.startIndx     = startIndx;
            this.locGroupsList = locGroupsList;
        }//end constructor

        public void run() {
            int n = waitTimes.length;
            for(int i=startIndx;((!isInterrupted())&&(i<locGroupsList.size()));
                                                                          i++)
            {
                long waitMS = ( i < n ? waitTimes[i] : waitTimes[n-1] );
                logger.log(Level.FINE,
                              " waiting "+(waitMS/1000)+" seconds before "
                              +"attempting to start the next lookup service");
                try { 
                    Thread.sleep(waitMS);
                } catch(InterruptedException e) { 
                    /* Need to re-interrupt this thread because catching
                     * an InterruptedException clears the interrupted status
                     * of this thread. 
                     * 
                     * If the sleep() call was not interrupted but was timed
                     * out, this means that this thread should continue
                     * processing; and the fact that the interrupted status
                     * has been cleared is consistent with that fact. On the
                     * other hand, if the sleep() was actually interrupted,
                     * this means that some entity external to this thread
                     * is signalling that this thread should exit. But the
                     * code below that determines whether to exit or continue
                     * processing bases its decision on the state of the
                     * interrupted status. And since the interrupted status
                     * was cleared when the InterruptedException was caught,
                     * the interrupted status of this thread needs to be reset
                     * to an interrupted state so that an exit will occur.
                     */
                    Thread.currentThread().interrupt();
                }
                LocatorGroupsPair pair
                                    = (LocatorGroupsPair)locGroupsList.get(i);
		LookupLocator l = pair.getLocator();
                int port = l.getPort();
                if(portInUse(port)) port = 0;
                if( isInterrupted() )  break;//exit this thread
                try {
                    startLookup(i, port, l.getHost());
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }//end loop
        }//end run
    }//end class StaggeredStartThread

    /* Protected instance variables */
    protected volatile int testType = AUTOMATIC_LOCAL_TEST;

//    protected String implClassname;

    protected volatile int maxSecsEventWait  = 10 * 60;
//    protected int announceInterval  = 2 * 60 * 1000;
//    protected int originalAnnounceInterval = 0;
//    protected int minNAnnouncements = 2;
    protected volatile int nIntervalsToWait  = 3;

    protected volatile boolean delayLookupStart = false;
    /* refactored fields */
    
//    private int nLookupServices          = 0;
//    private int nAddLookupServices       = 0;
//    
//    private int nServices    = 0;//local/serializable test services
//    private int nAddServices = 0;//additional local/serializable services
//
//
//    /* Attributes per service */
//    private int nAttributes    = 0;
//    private int nAddAttributes = 0;
//
////    protected int nSecsLookupDiscovery  = 30;
//    private int nSecsServiceDiscovery = 30;
//    private int nSecsJoin             = 30;
//
////    protected String remoteHost = "UNKNOWN_HOST";
//
//    /* Data structures - lookup services */
//    private ArrayList initLookupsToStart = new ArrayList(11);
//    private ArrayList addLookupsToStart  = new ArrayList(11);
//    private ArrayList allLookupsToStart  = new ArrayList(11);
//    private ArrayList lookupsStarted     = new ArrayList(11);
//
////    protected ArrayList lookupList = new ArrayList(1);
//    private HashMap genMap = new HashMap(11);
//    protected int nStarted = 0;
    /* Data structures - lookup discovery services */
//    protected ArrayList initLDSToStart = new ArrayList(11);
//    protected ArrayList addLDSToStart  = new ArrayList(11);
//    protected ArrayList allLDSToStart  = new ArrayList(11);
//    protected ArrayList ldsList = new ArrayList(1);
  
    
    /* end refactored fields */
//    protected Class[] serviceClasses = null;
    private final List expectedServiceList = new CopyOnWriteArrayList();

    protected volatile QAConfig config = null;
    
    private volatile boolean announcementsStopped = false;
    
    private volatile LookupServices lookupServices;
    private volatile LookupDiscoveryServices lookupDiscoveryServices;
    private volatile LeaseRenewalServices leaseRenewalServices;
    private volatile EventMailBoxServices eventMailBoxServices;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *    <li> retrieves configuration values needed by the current test
     *    <li> retrieves configuration information related to any lookup
     *         services that may need to be started
     *    <li> if appropriate, starts the configured number of initial 
     *         lookup services
     * </ul>
     */
    public Test construct(QAConfig config) throws Exception {
	super.construct(config);
	this.config = config;
	logger.log(Level.FINE, " construct()");
	debugsync = getConfig().getBooleanConfigVal("qautil.debug.sync",false);
        testType = config.getIntConfigVal("com.sun.jini.testType",
                                       BaseQATest.AUTOMATIC_LOCAL_TEST);
        lookupServices = new LookupServices(config, getManager(), fastTimeout);
        lookupDiscoveryServices = new LookupDiscoveryServices(config, getManager(), expectedServiceList);
        leaseRenewalServices = new LeaseRenewalServices(config);
        eventMailBoxServices = new EventMailBoxServices(config);
	if(!delayLookupStart) {
	    /* start desired initial lookup services so that they are up
	     * and running before the test actually begins its execution
	     */
	    startInitLookups();
	}//endif
	startInitLDS();
        return new Test(){

            public void run() throws Exception {
                //Nothing to do.
            }
            
        };
    }//end construct

    /** Cleans up any remaining state not already cleaned up by the test
     *  itself. If simulated lookup services were used by the test, this 
     *  method stops the multicast generators that were created and
     *  registered with RMID. This method then performs any standard clean
     *  up duties performed in the super class.
     */
    public void tearDown() {
        try {
            if(getLookupServices().getGenMap().size() > 0) {
                logger.log(Level.FINE, 
                                " tearDown - terminating lookup service(s)");
                /* Stop announcements if the generator is simulated, but allow
                 * the super class' tearDown method to actually destroy the
                 * lookup services (simulated or non-simulated).
                 */
                if( !announcementsStopped ) {
                    Iterator iter = getLookupServices().getGenMap().keySet().iterator();
                    for(int i=0;iter.hasNext();i++) {
                        Object generator = iter.next();
                        if(generator instanceof DiscoveryProtocolSimulator) {
                      ((DiscoveryProtocolSimulator)generator).stopAnnouncements();
                        }//endif
                    }//end loop
                    announcementsStopped = true;
                }//endif(!announcementsStopped)
            }//endif
	    /* Reset original net.jini.discovery.announce property */
	    if(lookupServices.getOriginalAnnounceInterval() == 0) {
                Properties props = System.getProperties();
                props.remove("net.jini.discovery.announce");
                System.setProperties(props);
	    }
            /* Destroy all lookup services registered with activation */
        } catch(Exception e) {
            e.printStackTrace();
        }
        super.tearDown();
    }//end tearDown

    /** With respect to the given objects, determines whether or not the
     *  following is true:  [x.equals(y)] if and only [x == y]
     */
    public static boolean satisfiesEqualityTest(Object x, Object y) {
        if( (x == null) || (y == null) ) return false;
        if(  (x == y) &&  (x.equals(y)) ) return true;
        if( !(x == y) && !(x.equals(y)) ) return true;
        return false;
    }//end satisfiesEqualityTest

    /** Returns <code>true</code> if the given arrays are referentially
     *  equal, or if the given arrays have the same contents;
     *  <code>false</code> otherwise.
     */
    public static boolean arraysEqual(Object[] a0, Object[] a1) {
        if(a0 == a1) return true;
        if( (a0 == null) || (a1 == null) ) return false;
        if(a0.length != a1.length) return false;
        iLabel:
        for(int i=0;i<a0.length;i++) {
            for(int j=0;i<a1.length;j++) {
                if( a0[i].equals(a1[j]) )  continue iLabel;//next a0
            }//end loop(j)
            return false; //a0[i] not contained in a1
        }//end loop(i)
        return true;
    }//end arraysEqual

    /** Returns <code>true</code> if the given lists are referentially
     *  equal, or if the given lists have the same contents;
     *  <code>false</code> otherwise.
     */
    public static boolean listsEqual(List l0, List l1) {
        if(l0 == l1) return true;//if both are null it returns true
        if( (l0 == null) || (l1 == null) ) return false;
        if(l0.size() != l1.size()) return false;
	Iterator iter = l0.iterator();
        while(iter.hasNext()) {
            if( !(l1.contains(iter.next())) ) return false;
        }//endif
        return true;
    }//end listsEqual

    /** Returns an array of locators in which each element of the array is
     *  the locator component of an element of the given <code>ArrayList</code>
     *  containing instances of <code>LocatorGroupsPair</code>.
     */
    public static LookupLocator[] toLocatorArray(List list) {
        LookupLocator[] locArray = new LookupLocator[list.size()];
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            locArray[i] = pair.getLocator();
        }//end loop
        return locArray;
    }//end toLocatorArray

    /** Returns an array of group names in which each element of the
     *  array is an element of one of the group array component values
     *  of an element of the given <code>ArrayList</code> containing
     *  instances of <code>LocatorGroupsPair</code>. That is, the array
     *  returned contains the union (minus duplicates) of all the group
     *  names referenced in the given <code>ArrayList</code>.
     *
     *  Note that since the elements of the given <code>ArrayList</code>
     *  represent the (locator,member groups) pair associated with a lookup
     *  service, and since a lookup service cannot belong to ALL_GROUPS
     *  (although it can belong to NO_GROUPS), this method does not deal
     *  with the possibility of ALL_GROUPS in the given <code>ArrayList</code>.
     */
    public static String[] toGroupsArray(List list) {
        ArrayList groupsList = new ArrayList(11);
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            String[] curGroups = pair.getGroups();
            if(curGroups.length == 0) continue;//skip NO_GROUPS
            for(int j=0;j<curGroups.length;j++) {
                groupsList.add(new String(curGroups[j]));
            }//end loop(j)
        }//end loop(i)
        return ((String[])(groupsList).toArray(new String[groupsList.size()]));
    }//end toGroupsArray

    /** Convenience method that determines whether the given array
     *  contains the given object.
     */
    public static boolean isElementOf(Object o, Object[] a) {
        if((o == null) || (a == null)) return false;
        for(int i=0;i<a.length;i++) {
            if( o.equals(a[i]) ) return true;
        }//end loop
        return false;
    }//end isElementOf

    /** For the given locator of a particular lookup service, if that
     *  locator is contained in the given set of locators to discover,
     *  this method returns true; otherwise, it returns false (which
     *  means that that the corresponding lookup service is not expected
     *  to be discovered).
     */
    public static boolean discoverByLocators
                                         (LookupLocator locator,
                                          LookupLocator[] locatorsToDiscover)
    {
        return isElementOf(locator,locatorsToDiscover);
    }//end discoverByLocators

    /** For the given member groups of a particular lookup service, 
     *  if at least one of those member groups is in the given set of
     *  groups to discover, this method returns true; otherwise, it
     *  returns false (which means that that the associated lookup
     *  service is not expected to be discovered).
     */
    public static boolean discoverByGroups(String[] memberGroups,
                                           String[] groupsToDiscover)
    {
        if(groupsToDiscover == DiscoveryGroupManagement.ALL_GROUPS) {
            return true;
        }//endif
        for(int i=0;i<memberGroups.length;i++) {
            if(isElementOf(memberGroups[i],groupsToDiscover)) return true;
        }//endif
        return false;
    }//end discoverByGroups

    /** Searches the given ArrayList containing instances of
     *  LocatorGroupsPair for an element that references the given
     *  locator, and returns the corresponding member groups of the
     *  lookup service associated with that locator. If no element
     *  of the given ArrayList references the given locator, then
     *  null is returned. When null is returned, this should NOT be
     *  interpretted as ALL_GROUPS since a lookup service cannot be
     *  a member of ALL_GROUPS; rather, it should be interpretted to
     *  mean simply that the given locator with corresponding groups
     *  was not found.
     */
    public static String[] getGroups(LookupLocator loc, List list) {
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            if(loc.equals(pair.getLocator())) return pair.getGroups();
        }//end loop
        return null; 
    }//end getGroups

    /** For each lookup service corresponding to a (locator,groups) pair
     *  in the given ArrayList, this method determines if at least one
     *  of that lookup's member groups is in the given set of groups
     *  to discover; which means that that lookup service is expected
     *  to be discovered. If all such lookup services are expected to
     *  be discovered, this method returns true. If at least one lookup
     *  service belongs to groups which are not desired to be discovered,
     *  this method returns false.
     */
    public static boolean discoverAllLookups(List list,
                                             String[] groupsToDiscover)
    {
        /* If ALL_GROUPS, then we must want to discover all the lookups */
        if(groupsToDiscover == DiscoveryGroupManagement.ALL_GROUPS) {
            return true;
        }//endif
        /* If at least 1 lookup has member groups that are NOT in the set
         * groups to discover, then we do not want to discover all of the
         * lookups
         */
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            String[] curMemberGroups = pair.getGroups();
            if( !discoverByGroups(curMemberGroups,groupsToDiscover) ) {
                return false;
            }//endif
        }//end loop
        return true;
    }//end discoverAllLookups

    /** This method returns an array of LocatorGroupsPair instances constructed
     *  from the elements of the given list of LocatorGroupsPair instances.
     *  The elements of the return list are selected from the given list
     *  if one or more of the groups associated with a particular element of
     *  the given list are contained in the given set of groupsToDiscover.
     */
    public static List filterListByGroups(List list,
                                               String[] groupsToDiscover)
    {
        ArrayList filteredList = new ArrayList(list.size());
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            String[] groups = pair.getGroups();
            if(discoverByGroups(groups,groupsToDiscover)) {
                filteredList.add(pair);
            }//endif
        }//end loop
        return filteredList;
    }//end filterListByGroups

    /* Convenience method that returns a set of attributes containing 
     * one element from the first set, and two copies of each element 
     * from the second set of attributes. This method is useful for
     * constructing attribute sets that can be used in tests that verify
     * the behavior of various attribute modification methods.
     */
    public static Entry[] addAttrsDup1DupAll(Entry[] attrs0, Entry[] attrs1) {
        /* Create an array that contains the first element from attrs0, all
         * the elements from attrs1, and then the elements from attrs1 again.
         */
        Entry[] tmpAttrs = null;
        if(attrs0 != null) {
            tmpAttrs = new Entry[1];
            tmpAttrs[0] = attrs0[0];
        }//endif
        return addAttrsWithDups(tmpAttrs,attrs1);
    }//end addAttrsDup1DupAll

    /* Convenience method that returns a set of attributes containing 
     * all the elements of the given sets of attributes, with the second
     * set of elements duplicated. This method is useful for constructing
     * attribute sets that can be used in tests that verify the behavior of
     * various attribute modification methods.
     */
    public static Entry[] addAttrsWithDups(Entry[] attrs0, Entry[] attrs1) {
        /* Create an array that contains elements from attrs0, elements from
         * attrs1, and then elements from attrs1 again.
         */
        int len0 = ( (attrs0 == null) ? 0 : attrs0.length );
        int len1 = ( (attrs1 == null) ? 0 : attrs1.length );
        int len  = len0+(2*len1);
        Entry[] retArray = new Entry[len];
        /* Include elements from attrs0 */
        int start = 0;
        int blockLen = len0;
        for(int i=start;i<blockLen;i++) {
            retArray[i] = attrs0[i-start];
        }//end loop
        /* Include elements from attrs1 */
        start = blockLen;
        blockLen = blockLen+len1;
        for(int i=start;i<blockLen;i++) {
            retArray[i] = attrs1[i-start];
        }//end loop
        /* Include elements from attrs1 again */
        start = blockLen;
        blockLen = blockLen+len1;
        for(int i=start;i<blockLen;i++) {
            retArray[i] = attrs1[i-start];
        }//end loop
        return retArray;
    }//end addAttrsWithDups

    /* Convenience method that returns a set of attributes that contains
     * the union of the given sets with duplicates removed.
     */
    public static Entry[] addAttrsAndRemoveDups(Entry[] attrs0,Entry[] attrs1){
        int len0 = ( (attrs0 == null) ? 0 : attrs0.length );
        int len1 = ( (attrs1 == null) ? 0 : attrs1.length );
        /* HashSet removes dupliates based on the equals() method */
        HashSet sumSet = new HashSet(len0+len1);
        for(int i=0;i<len0;i++) {
            sumSet.add(attrs0[i]);
        }//end loop
        for(int i=0;i<len1;i++) {
            sumSet.add(attrs1[i]);
        }//end loop
        return  ((Entry[])(sumSet).toArray(new Entry[sumSet.size()]) );
    }//end addAttrsAndRemoveDups

    /** Convenience method that returns a shallow copy of the
     *  <code>lookupList</code> <code>ArrayList</code> that contains the
     *  proxies to the lookup services that have been started so far.
     *  The size of that list is retrieved while the list is locked, 
     *  so that the list is not modified while the copy is being made.
     */
    protected List<ServiceRegistrar> getLookupListSnapshot() {
        return getLookupListSnapshot(null);
    }//end getLookupListSnapshot

    protected List<ServiceRegistrar> getLookupListSnapshot(String infoStr) {
        return getLookupServices().getLookupListSnapshot(infoStr);
    }//end getLookupListSnapshot

    /** Convenience method that returns the current size of the
     *  <code>lookupList</code> <code>ArrayList</code> that contains the
     *  proxies to the lookup services that have been started so far.
     *  The size of that list is retrieved while the list is locked, 
     *  so that the list is not modified while the retrieval is being made.
     */
    protected int curLookupListSize() {
        return curLookupListSize(null);
    }//end curLookupListSize

    protected int curLookupListSize(String infoStr) {
        return getLookupServices().curLookupListSize(infoStr);
    }//end curLookupListSize

    /* Convenience method that removes the duplicate elements from the 
     * given set or attributes and returns the result.
     */
    public static Entry[] removeDups(Entry[] attrs) {
        int len = ( (attrs == null) ? 0 : attrs.length );
        /* HashSet removes dupliates based on the equals() method */
        HashSet attrsSet = new HashSet(len);
        for(int i=0;i<len;i++) {
            attrsSet.add(attrs[i]);
        }//end loop
        return  ((Entry[])(attrsSet).toArray(new Entry[attrsSet.size()]) );
    }//end addAttrsAndRemoveDups

    /** Method that compares the given port to the ports of all the lookup
     *  services that have been currently started. Returns <code>true</code>
     *  if the given port equals any of the ports referenced in the set
     *  lookup services that have been started; <code>false</code>
     *  otherwise. This method is useful for guaranteeing unique port
     *  numbers when starting lookup services.
     */
    protected final boolean portInUse(int port) {
        return getLookupServices().portInUse(port);
    }//end portInUse

    /** Constructs a <code>LookupLocator</code> using configuration information
     *  corresponding to the value of the given parameter <code>indx</code>.
     *  Useful when lookup services need to be started, or simply when
     *  instances of <code>LookupLocator</code> need to be constructed with
     *  meaningful state.
     * @param indx 
     */
    protected LookupLocator getTestLocator(int indx) throws TestException {
       return getLookupServices().getTestLocator(indx);
    }//end getTestLocator

    /** Constructs a <code>LookupLocator</code> using configuration information
     *  corresponding to the value of the given parameter <code>indx</code>.
     *  Useful when lookup services need to be started, or simply when
     *  instances of <code>LookupLocator</code> need to be constructed with
     *  meaningful state.
     */
    protected LookupLocator getRemoteTestLocator(int indx) {
        return getLookupServices().getRemoteTestLocator(indx);
    }//end getRemoteTestLocator

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup services needed by
     *  that test run. Useful when all of the lookup services are to be
     *  started during construct processing.
     */
    protected void startAllLookups() throws Exception {
        startInitLookups();
        startAddLookups();
    }//end startAllLookups

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup services INITIALLY
     *  needed by that test run. Useful when an initial set of lookups are
     *  to be started during construct processing, and (possibly) an additional
     *  set of lookups are to be started at some later time, after the test
     *  has already begun execution.
     */
    protected void startInitLookups() throws Exception {
        try {   
            getLookupServices().startInitLookups();
        } catch (TestException e){
            tearDown();
            throw e;
        }
    }//end startInitLookups

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, any additional lookup services 
     *  needed by that test run. Useful when an initial set of lookups are
     *  to be started during construct processing, and an additional set of
     *  lookups are to be started at some later time, after the test
     *  has already begun execution.
     */
    protected void startAddLookups() throws Exception {
        try {  
            getLookupServices().startAddLookups();
        } catch (TestException e){
            tearDown();
            throw e;
        }
    }//end startAddLookups

    /** 
     * Start a lookup service with configuration referenced by the
     * given parameter values.
     *
     * @param indx the index of lookup services within the set of
     *             lookups to start for this test
     * @param port the port the lookup service is to use
     * @return the name of the system the lookup service was started on
     * @throws Exception if something goes wrong
     */
    protected String startLookup(int indx, int port) throws Exception {
	return startLookup(indx, port, config.getLocalHostName());
    }

    protected String startLookup(int indx, int port, String serviceHost) throws Exception {
        return getLookupServices().startLookup(indx, port, serviceHost);
    }

    /** Common code shared by each test that needs to wait for discovered
     *  events from the discovery helper utility, and verify that the
     *  expected discovered events have indeed arrived.
     */
    protected void waitForDiscovery(LookupListener listener)
                                                      throws TestException
    {
        /* Wait for the expected # of discovered events from the listener */
        int nSecsToWait0 = nIntervalsToWait*fastTimeout;
        if(nSecsToWait0 == 0) nSecsToWait0 = fastTimeout;//default value
        int nSecsToWait1 = 1; // guarantee at least 1 pass through timer loop
        if(nSecsToWait0 < maxSecsEventWait) {
            nSecsToWait1 = (maxSecsEventWait - nSecsToWait0);
        }//endif
        if(useFastTimeout) {//reset timeouts for faster completion
            nSecsToWait0     = fastTimeout;
            nSecsToWait1     = fastTimeout;
            maxSecsEventWait = fastTimeout;
        }//endif
        logger.log(Level.FINE, " for DISCOVERY events -- waiting "
                          +"at least "+nSecsToWait0+" seconds, but no more "
                          +"than "+(nSecsToWait0+nSecsToWait1)+" seconds ...");
        /* no early breakout; verifies no extra discovered events are sent */
        for(int i=0;i<nSecsToWait0;i++) {
            DiscoveryServiceUtil.delayMS(1000);
        }//end loop
        /* Minimum wait period complete, now test for discovered events. Wait
         * no more than (max-min) seconds, exit immediately on success.
         */
        logger.log(Level.FINE, " initial wait period complete ... "
                         +"waiting at most "+nSecsToWait1+" more seconds ...");
        boolean discoveryComplete = false;
        boolean showCompInfo = true;//turn this off after 1st pass thru loop
	Map discoveredClone = null;
        iLoop:
        for(int i=0;i<nSecsToWait1;i++) {
            synchronized(listener) {
                Map discoveredMap = listener.discoveredMap;
                Map expectedDiscoveredMap = listener.expectedDiscoveredMap;

                if(displayOn && 
		       (discoveredClone == null 
			|| !locGroupsMapsEqual(discoveredMap, discoveredClone)))
		{
		    discoveredClone = (Map) ((HashMap)discoveredMap).clone();

                    logger.log(Level.FINE,
                                      "   discoveredMap.size == "
                                      +discoveredMap.size());
                    Set eSet = discoveredMap.entrySet();
	            Iterator iter = eSet.iterator();
                    while(iter.hasNext()) {
                        Map.Entry pair = (Map.Entry)iter.next();
                        LookupLocator loc = (LookupLocator)pair.getKey();
                        String[] groups = (String[])pair.getValue();
                        logger.log(Level.FINE,
                                         "   discoveredMap.locator = "+loc);
                        if( groups.length <= 0 ) {
                            logger.log(Level.FINE,
                                   "     discoveredMap.groups == NO_GROUPS");
                        } else {
                            for(int m=0;m<groups.length;m++){
                                logger.log(Level.FINE,
                                                "     discoveredMap.groups["
                                                  +m+"] == "+groups[m]);
                            }//end loop
                        }//endif
                    }//end loop
                    logger.log(Level.FINE,
                                      "   expectedDiscoveredMap.size == "
                                      +expectedDiscoveredMap.size());
                    eSet = expectedDiscoveredMap.entrySet();
                    iter = eSet.iterator();
                    while(iter.hasNext()) {
                        Map.Entry pair = (Map.Entry)iter.next();
                        LookupLocator loc = (LookupLocator)pair.getKey();
                        String[] groups = (String[])pair.getValue();
                        logger.log(Level.FINE,
                                  "   expectedDiscoveredMap.locator = "+loc);
                        if( groups.length <= 0 ) {
                            logger.log(Level.FINE,
                                     "     expectedDiscoveredMap.groups == "
                                     +"NO_GROUPS");
                        } else {
                            for(int m=0;m<groups.length;m++){
                                logger.log(Level.FINE,
                                        "     expectedDiscoveredMap.groups["
                                                  +m+"] == "+groups[m]);
                            }//end loop
                        }//endif
                    }//end loop
                }//endif(displayOn)
                /* nEventsReceived == nEventsExpected for this listener? */
                if(discoveredMap.size() == expectedDiscoveredMap.size()) {
                    if(i == (nSecsToWait1-1)) {
                        logger.log(Level.FINE,
                               "   events expected ("
                               +expectedDiscoveredMap.size()+") == events "
                               +"received ("+discoveredMap.size()+")");
                    }//endif
                    /* locators in received event == expected locators? */
                    if( locGroupsMapsEqual(expectedDiscoveredMap,discoveredMap,
                                           showCompInfo) )
                    {
                        logger.log(Level.FINE,
                                  "   events expected ("
                                  +expectedDiscoveredMap.size()+") == events "
                                  +"received ("+discoveredMap.size()+"),"
                                  +" all locators equal");
                        discoveryComplete = true;
                        break iLoop;
                    } else {
                        if(i == (nSecsToWait1-1)) {
                            logger.log(Level.FINE,
                                              "   not all lookups equal");
                        }//endif
                    }//endif
                } else {//(nEventsReceived != nEventsExpected)
                    if(i == (nSecsToWait1-1)) {
                        logger.log(Level.FINE,
                                              "   events expected ("
                                              +expectedDiscoveredMap.size()
                                              +") != events received ("
                                              +discoveredMap.size()+")");
                    }//endif
                }//endif(nEventsReceived == nEventsExpected)
            }//end sync(listener)
            DiscoveryServiceUtil.delayMS(1000);
            showCompInfo = false;//display comparison info only when i = 0
        }//end loop(iLoop)
        logger.log(Level.FINE, " DISCOVERY wait period complete");
        synchronized(listener) {
            if(!discoveryComplete) {
                throw new TestException("discovery failed -- "
                                         +"waited "+nSecsToWait1
                                         +" seconds ("+(nSecsToWait1/60)
                                         +" minutes) -- "
                                         +listener.expectedDiscoveredMap.size()
                                         +" discovery event(s) expected, "
                                         +listener.discoveredMap.size()
                                         +" discovery event(s) received");
            }//endif(!discoveryComplete)
            logger.log(Level.FINE, " "
                              +listener.expectedDiscoveredMap.size()
                              +" discovery event(s) expected, "
                              +listener.discoveredMap.size()
                              +" discovery event(s) received");
        }//end sync(listener)
    }//end waitForDiscovery

    /** Common code shared by each test that needs to wait for discarded
     *  events from the discovery helper utility, and verify that the
     *  expected discarded events have indeed arrived.
     */
    protected void waitForDiscard(LookupListener listener)
                                                      throws TestException
    {
        /* Wait for the expected # of discard events from the listener */
        int nSecsToWait0 = nIntervalsToWait*fastTimeout;
        if(nSecsToWait0 == 0) nSecsToWait0 = fastTimeout;//default value
        int nSecsToWait1 = 1; // guarantee at least 1 pass through timer loop
        if(nSecsToWait0 < maxSecsEventWait) {
            nSecsToWait1 = (maxSecsEventWait - nSecsToWait0);
        }//endif
        if(useFastTimeout) {//reset timeouts for faster completion
            nSecsToWait0     = fastTimeout;
            nSecsToWait1     = fastTimeout;
            maxSecsEventWait = fastTimeout;
        }//endif
        logger.log(Level.FINE, " for DISCARD events -- waiting "
                          +"at least "+nSecsToWait0+" seconds, but no more "
                          +"than "+(nSecsToWait0+nSecsToWait1)+" seconds ...");
        /* no early breakout; verifies no extra discard events are sent */
        for(int i=0;i<nSecsToWait0;i++) {
            DiscoveryServiceUtil.delayMS(1000);
        }//end loop
        /* Minimum wait period complete, now test for discard events. Wait
         * no more than (max-min) seconds, exit immediately on success.
         */
        logger.log(Level.FINE, " initial wait period complete ... "
                         +"waiting at most "+nSecsToWait1+" more seconds ...");
        boolean discardComplete = false;
        boolean showCompInfo = true;//turn this off after 1st pass thru loop
	Map discardedClone = null;
        iLoop:
	for(int i=0;i<nSecsToWait1;i++) {
            synchronized(listener) {
                Map discardedMap = listener.discardedMap;
                Map expectedDiscardedMap = listener.expectedDiscardedMap;

                if(displayOn && 
		       (discardedClone == null 
			|| !locGroupsMapsEqual(discardedMap, discardedClone)))
		{
		    discardedClone = (Map) ((HashMap)discardedMap).clone();

                    logger.log(Level.FINE,
                                      "   discardedMap.size == "
                                      +discardedMap.size());
                    Set eSet = discardedMap.entrySet();
	            Iterator iter = eSet.iterator();
                    while(iter.hasNext()) {
                        Map.Entry pair = (Map.Entry)iter.next();
                        LookupLocator loc = (LookupLocator)pair.getKey();
                        String[] groups = (String[])pair.getValue();
                        logger.log(Level.FINE,
                                         "   discardedMap.locator = "+loc);
                        if( groups.length <= 0 ) {
                            logger.log(Level.FINE,
                                   "     discardedMap.groups == NO_GROUPS");
                        } else {
                            for(int m=0;m<groups.length;m++){
                                logger.log(Level.FINE,
                                                "     discardedMap.groups["
                                                  +m+"] == "+groups[m]);
                            }//end loop
                        }//endif
                    }//end loop
                    logger.log(Level.FINE,
                                      "   expectedDiscardedMap.size == "
                                      +expectedDiscardedMap.size());
                    eSet = expectedDiscardedMap.entrySet();
                    iter = eSet.iterator();
                    while(iter.hasNext()) {
                        Map.Entry pair = (Map.Entry)iter.next();
                        LookupLocator loc = (LookupLocator)pair.getKey();
                        String[] groups = (String[])pair.getValue();
                        logger.log(Level.FINE,
                                  "   expectedDiscardedMap.locator = "+loc);
                        if( groups.length <= 0 ) {
                            logger.log(Level.FINE,
                                     "     expectedDiscardedMap.groups == "
                                     +"NO_GROUPS");
                        } else {
                            for(int m=0;m<groups.length;m++){
                                logger.log(Level.FINE,
                                        "     expectedDiscardedMap.groups["
                                                  +m+"] == "+groups[m]);
                            }//end loop
                        }//endif
                    }//end loop
                }//endif(displayOn)
                /* nEventsReceived == nEventsExpected for this listener? */
                if(discardedMap.size() == expectedDiscardedMap.size()) {
                    if(i == (nSecsToWait1-1)) {
                        logger.log(Level.FINE,
                               "   events expected ("
                               +expectedDiscardedMap.size()+") == events "
                               +"received ("+discardedMap.size()+")");
                    }//endif
                    /* locators in received event == expected locators? */
                    if( locGroupsMapsEqual(expectedDiscardedMap,discardedMap,
                                           showCompInfo) )
                    {
                        logger.log(Level.FINE,
                                  "   events expected ("
                                  +expectedDiscardedMap.size()+") == events "
                                  +"received ("+discardedMap.size()+"),"
                                  +" all locators equal");
                        discardComplete = true;
                        break iLoop;
                    } else {
                        if(i == (nSecsToWait1-1)) {
                            logger.log(Level.FINE,
                                              "   not all lookups equal");
                        }//endif
                    }//endif
                } else {//(nEventsReceived != nEventsExpected)
                    if(i == (nSecsToWait1-1)) {
                        logger.log(Level.FINE,
                                              "   events expected ("
                                              +expectedDiscardedMap.size()
                                              +") != events received ("
                                              +discardedMap.size()+")");
                    }//endif
                }//endif(nEventsReceived == nEventsExpected)
            }//end sync(listener)
            DiscoveryServiceUtil.delayMS(1000);
            showCompInfo = false;//display comparison info only when i = 0
        }//end loop(iLoop)
        logger.log(Level.FINE, " DISCARD wait period complete");
        synchronized(listener) {
            if(!discardComplete) {
                throw new TestException("discard failed -- "
                                         +"waited "+nSecsToWait1
                                         +" seconds ("+(nSecsToWait1/60)
                                         +" minutes) -- "
                                         +listener.expectedDiscardedMap.size()
                                         +" discard event(s) expected, "
                                         +listener.discardedMap.size()
                                         +" discard event(s) received");
            }//endif(!discardComplete)
            logger.log(Level.FINE, " "
                              +listener.expectedDiscardedMap.size()
                              +" discard event(s) expected, "
                              +listener.discardedMap.size()
                              +" discard event(s) received");
        }//end sync(listener)
    }//end waitForDiscard

    /** Common code shared by each test that needs to wait for changed
     *  events from the discovery helper utility, and verify that the
     *  expected changed events have indeed arrived.
     */
    protected void waitForChange(GroupChangeListener listener)
                                                      throws TestException
    {
        /* Wait for the expected # of changed events from the listener */
        int nSecsToWait0 = nIntervalsToWait*fastTimeout;
        if(nSecsToWait0 == 0) nSecsToWait0 = fastTimeout;//default value
        int nSecsToWait1 = 1; // guarantee at least 1 pass through timer loop
        if(nSecsToWait0 < maxSecsEventWait) {
            nSecsToWait1 = (maxSecsEventWait - nSecsToWait0);
        }//endif
        if(useFastTimeout) {//reset timeouts for faster completion
            nSecsToWait0     = fastTimeout;
            nSecsToWait1     = fastTimeout;
            maxSecsEventWait = fastTimeout;
        }//endif
        logger.log(Level.FINE, " for CHANGE events -- waiting "
                          +"at least "+nSecsToWait0+" seconds, but no more "
                          +"than "+(nSecsToWait0+nSecsToWait1)+" seconds ...");
        /* no early breakout; verifies no extra changed events are sent */
        for(int i=0;i<nSecsToWait0;i++) {
            DiscoveryServiceUtil.delayMS(1000);
        }//end loop
        /* Minimum wait period complete, now test for changed events. Wait
         * no more than (max-min) seconds, exit immediately on success.
         */
        logger.log(Level.FINE, " initial wait period complete ... "
                         +"waiting at most "+nSecsToWait1+" more seconds ...");
        boolean changeComplete = false;
        boolean showCompInfo = true;//turn this off after 1st pass thru loop
	Map changedClone = null;
    iLoop:
        for(int i=0;i<nSecsToWait1;i++) {
            synchronized(listener) {
                Map changedMap = listener.changedMap;
                Map expectedChangedMap = listener.expectedChangedMap;
                if(displayOn && 
		       (changedClone == null 
			|| !locGroupsMapsEqual(changedMap, changedClone)))
		{
		    changedClone = (Map) ((HashMap)changedMap).clone();

                    logger.log(Level.FINE,
                                      "   changedMap.size == "
                                      +changedMap.size());
                    Set eSet = changedMap.entrySet();
	            Iterator iter = eSet.iterator();
                    while(iter.hasNext()) {
                        Map.Entry pair = (Map.Entry)iter.next();
                        LookupLocator loc = (LookupLocator)pair.getKey();
                        String[] groups = (String[])pair.getValue();
                        logger.log(Level.FINE,
                                         "   changedMap.locator = "+loc);
                        if( groups.length <= 0 ) {
                            logger.log(Level.FINE,
                                   "     changedMap.groups == NO_GROUPS");
                        } else {
                            for(int m=0;m<groups.length;m++){
                                logger.log(Level.FINE,
                                                "     changedMap.groups["
                                                  +m+"] == "+groups[m]);
                            }//end loop
                        }//endif
                    }//end loop
                    logger.log(Level.FINE,
                                      "   expectedChangedMap.size == "
                                      +expectedChangedMap.size());
                    eSet = expectedChangedMap.entrySet();
                    iter = eSet.iterator();
                    while(iter.hasNext()) {
                        Map.Entry pair = (Map.Entry)iter.next();
                        LookupLocator loc = (LookupLocator)pair.getKey();
                        String[] groups = (String[])pair.getValue();
                        logger.log(Level.FINE,
                                  "   expectedChangedMap.locator = "+loc);
                        if( groups.length <= 0 ) {
                            logger.log(Level.FINE,
                                     "     expectedChangedMap.groups == "
                                     +"NO_GROUPS");
                        } else {
                            for(int m=0;m<groups.length;m++){
                                logger.log(Level.FINE,
                                        "     expectedChangedMap.groups["
                                                  +m+"] == "+groups[m]);
                            }//end loop
                        }//endif
                    }//end loop
                }//endif(displayOn)
                /* nEventsReceived == nEventsExpected for this listener? */
                if(changedMap.size() == expectedChangedMap.size()) {
                    if(i == (nSecsToWait1-1)) {
                        logger.log(Level.FINE,
                               "   events expected ("
                               +expectedChangedMap.size()+") == events "
                               +"received ("+changedMap.size()+")");
                    }//endif
                    /* locators in received event == expected locators? */
                    if( locGroupsMapsEqual(expectedChangedMap,changedMap,
                                           showCompInfo) )
                    {
                        logger.log(Level.FINE,
                                  "   events expected ("
                                  +expectedChangedMap.size()+") == events "
                                  +"received ("+changedMap.size()+"),"
                                  +" all locators equal");
                        changeComplete = true;
                        break iLoop;
                    } else {
                        if(i == (nSecsToWait1-1)) {
                            logger.log(Level.FINE,
                                              "   not all lookups equal");
                        }//endif
                    }//endif
                } else {//(nEventsReceived != nEventsExpected)
                    if(i == (nSecsToWait1-1)) {
                        logger.log(Level.FINE,
                                              "   events expected ("
                                              +expectedChangedMap.size()
                                              +") != events received ("
                                              +changedMap.size()+")");
                    }//endif
                }//endif(nEventsReceived == nEventsExpected)
            }//end sync(listener)
            DiscoveryServiceUtil.delayMS(1000);
            showCompInfo = false;//display comparison info only when i = 0
        }//end loop(iLoop)
        logger.log(Level.FINE, " CHANGE wait period complete");
        synchronized(listener) {
            if(!changeComplete) {
                throw new TestException("change failed -- "
                                         +"waited "+nSecsToWait1
                                         +" seconds ("+(nSecsToWait1/60)
                                         +" minutes) -- "
                                         +listener.expectedChangedMap.size()
                                         +" change event(s) expected, "
                                         +listener.changedMap.size()
                                         +" change event(s) received");
            }//endif(!changeComplete)
            logger.log(Level.FINE, " "
                              +listener.expectedChangedMap.size()
                              +" change event(s) expected, "
                              +listener.changedMap.size()
                              +" change event(s) received");
        }//end sync(listener)
    }//end waitForChange

    /** Given two locator-to-groups mappings, this method compares both
     *  the locator key sets and the groups value sets, and returns 
     *  <code>true</code> if the elements of the key sets and the value sets
     *  are found to be equal; returns <code>false</code> otherwise.
     */
    public static boolean locGroupsMapsEqual( Map map0, Map map1 ) {
        if(!locGroupsMapsEqualByLoc(map0,map1,false)) return false;
        return locGroupsMapsEqualByGroups(map0,map1,false);
    }//end locGroupsMapsEqual

    /** Given two locator-to-groups mappings, this method compares both
     *  the locator key sets and the groups value sets, displaying the
     *  locator and group information in each map if the <code>displayOn</code>
     *  parameter is <code>true</code>. This method returns <code>true</code>
     *  if the elements of the key sets and the value sets are found to be
     *  equal; returns <code>false</code> otherwise.
     */
    public static boolean locGroupsMapsEqual( Map map0, Map map1,
                                          boolean displayOn)
    {
        if(!locGroupsMapsEqualByLoc(map0,map1,displayOn)) return false;
        return locGroupsMapsEqualByGroups(map0,map1,displayOn);
    }//end locGroupsMapsEqual

    /** Given two locator-to-groups mappings, this method compares the
     *  elements of the locator key sets, and returns <code>true</code>
     *  if the locators in the key sets are found to be equal; returns
     *  <code>false</code> otherwise.
     */
    public static boolean locGroupsMapsEqualByLoc( Map map0, Map map1 ) {
        return locGroupsMapsEqualByLoc(map0,map1,false);
    }//end locGroupsMapsEqualByLoc

    /** Given two locator-to-groups mappings, this method compares the
     *  elements of the locator key sets, displaying the locator
     *  information in given map if the <code>displayOn</code> parameter
     *  is <code>true</code>. This method returns <code>true</code>
     *  if the locators in the key sets are found to be equal; returns
     *  <code>false</code> otherwise.
     */
    public static boolean locGroupsMapsEqualByLoc( Map map0, Map map1,
                                               boolean displayOn )
    {
        if( (map0 == null) || (map1 == null) ) return false;
        if(map0.size() != map1.size()) return false;
        Collection locKeys0 = map0.keySet();
        Collection locKeys1 = map1.keySet();
        if(displayOn) {
            logger.log(Level.FINE, " comparing locators ... ");
            logger.log(Level.FINE, " locators set 0 -- ");
        }//endif
        LookupLocator[] locs0 = new LookupLocator[locKeys0.size()];
        Iterator iter = locKeys0.iterator();
        for(int i=0;iter.hasNext();i++) {
            locs0[i] = (LookupLocator)iter.next();
            if(displayOn)  logger.log(Level.FINE, "    "+locs0[i]);
        }//end loop
        if(displayOn) logger.log(Level.FINE, " locators set 1 -- ");
        LookupLocator[] locs1 = new LookupLocator[locKeys1.size()];
        iter = locKeys1.iterator();
        for(int i=0;iter.hasNext();i++) {
            locs1[i] = (LookupLocator)iter.next();
            if(displayOn)  logger.log(Level.FINE, "    "+locs1[i]);
        }//end loop
        return LocatorsUtil.compareLocatorSets(locs0,locs1, Level.OFF);
    }//end locGroupsMapsEqualByLoc

    /** Given two locator-to-groups mappings, this method compares the
     *  elements of the value sets which contain arrays of member groups.
     *  This method returns <code>true</code> if the group sets in those
     *  value sets are found to be equal; returns <code>false</code>
     *  otherwise.
     */
    public static boolean locGroupsMapsEqualByGroups( Map map0, Map map1 ) {
        return locGroupsMapsEqualByGroups(map0,map1,false);
    }//end locGroupsMapsEqualByGroups

    /** Given two locator-to-groups mappings, this method compares the
     *  elements of the value sets which contain arrays of member groups,
     *  displaying the locator and group information in each map if
     *  the <code>displayOn</code> parameter is <code>true</code>.
     *  This method returns <code>true</code> if the group sets in those
     *  value sets are found to be equal; returns <code>false</code>
     *  otherwise.
     */
    public static boolean locGroupsMapsEqualByGroups( Map map0, Map map1,
                                                  boolean displayOn )
    {
        if( (map0 == null) || (map1 == null) ) return false;
        if(map0.size() != map1.size()) return false;
        Collection locKeys0 = map0.keySet();
        if(displayOn) {
            logger.log(Level.FINE,
                              " comparing group sets of each lookup ... ");
        }//endif
        Iterator iter = locKeys0.iterator();
        for(int i=0;iter.hasNext();i++) {
            LookupLocator loc = (LookupLocator)iter.next();
            String[] groups0   = (String[])map0.get(loc);
            String[] groups1   = (String[])map1.get(loc);
            if(displayOn) {
                logger.log(Level.FINE,
                                  "    set 0 -- locator = "+loc);
                if(groups0.length == 0) {
                    logger.log(Level.FINE,
                                      "      groups = NO_GROUPS");
                } else {
                    for(int j=0;j<groups0.length;j++) {
                        logger.log(Level.FINE,
                                        "      group["+j+"] = "+groups0[j]);
                    }//end loop
                }//endif
                logger.log(Level.FINE,
                                  "    set 1 -- locator = "+loc);
                if(groups1.length == 0) {
                    logger.log(Level.FINE,
                                      "      groups = NO_GROUPS");
                } else {
                    for(int j=0;j<groups1.length;j++) {
                        logger.log(Level.FINE,
                                         "      group["+j+"] = "+groups1[j]);
                    }//end loop
                }//endif
            }//endif(displayOn)
            if(!GroupsUtil.compareGroupSets(groups0,groups1, Level.OFF)) return false;
        }//end loop
        return true;
    }//end locGroupsMapsEqualByGroups

    /** Returns the proxy to each lookup service started (already prepared)*/
    protected ServiceRegistrar[] getLookupProxies() {
        return getLookupServices().getLookupProxies();
    }//end getLookupProxies

    /** For each lookup service corresponding to an element of the global
     *  HashMap 'genMap', this method stops the generation of multicast
     *  announcements by either destroying the lookup service, or 
     *  explicitly stopping the announcements and then destroying the
     *  lookup service.
     *  
     *  @throws com.sun.jini.qa.harness.TestException
     */
    protected void terminateAllLookups() throws TestException {
        announcementsStopped = getLookupServices().terminateAllLookups();
    }//end terminateAllLookups

    /** This method stops the generation of multicast announcements from each
     *  lookup service that has been started. The announcements are stopped
     *  directly if possible or, if stopping the announcements directly is not
     *  possible, the announcements are stopped indirectly by destroying each
     *  lookup service (ex. reggie does not allow one to stop its multicast
     *  announcements while allowing the service to remain running and
     *  reachable.)
     */
    protected void stopAnnouncements() {
        
        announcementsStopped = true;
    }//end stopAnnouncements

    /** For each lookup service proxy contained in the input array, this
     *  method first determines if that lookup service is reachable by
     *  attempting to retrieve the associated locator; that is, it
     *  attempts to 'ping' the lookup service. The lookup services found
     *  to be un-reachable are then used to update the given listener's
     *  expected discard state. After updating the listener's expected 
     *  state, each unreachable lookup service is discarded from the given
     *  instance of LookupDiscovery.
     * 
     *  This method returns an ArrayList containing LocatorGroupsPair
     *  instances corresponding to the lookups that were NOT discarded.
     *  This is so that the expected discard event state can be built
     *  correctly (a discarded event should not be expected for a lookup
     *  service that couldn't be discarded).
     */
    protected ArrayList pingAndDiscard(ServiceRegistrar[] proxies,
                                       DiscoveryManagement dm,
                                       LookupListener listener)
    {
        return getLookupServices().pingAndDiscard(proxies, dm, listener);
    }//end pingAndDiscard

    /** Since the lookup discovery utility typically sends a unicast
     *  announcement at startup, resulting in immediate discovery by
     *  unicast, the lookup service which generates the multicast 
     *  announcements won't send its first announcement until after
     *  net.jini.discovery.announce number of milliseconds. This means
     *  that until that first multicast announcement arrives, the lookup
     *  discovery utility will have no point of reference (no initial time
     *  stamp) with which to determine if the announcements have indeed
     *  stopped. Thus, multicast monitoring doesn't really begin until after
     *  the first announcement arrives. It is for this reason that before
     *  the multicast announcements are stopped, it may be of value to wait
     *  enough time so as to guarantee that at least one announcement has
     *  been sent. This method can be used by tests that wish to provide
     *  such a guarantee.
     *
     *  The time this method waits is computed from the configurable number
     *  of announcement time intervals over which to wait (nIntervalsToWait),
     *  and the configurable minimum number of announcements over which
     *  unreachability is determined (minNAnnouncements).
     * 
     *  Note that this method should only be used when the test uses
     *  simulated lookup services.
     */
    protected void verifyAnnouncementsSent() {
       
    }//end verifyAnnouncementsSent


    /** Depending on the value of the boolean parameter <code>alternate</code>,
     *  for each lookup service that has been started, this method either
     *  replaces the current member groups of the lookup service with a
     *  new set of groups containing NONE of the current groups of interest
     *  (<code>alternate</code> == <code>false</code>), or a new set of
     *  groups in which, alternately, half the elements are equal to their
     *  counterparts in the original set, and half are different from their
     *  counterparts.
     *
     *  This method is intended to guarantee that the new set of groups 
     *  that replaces the member groups of each lookup service that was
     *  started contains some, if not all, new groups different from the
     *  original groups.
     *  
     *  This method returns an <code>ArrayList</code> in which each element
     *  is an instance of <code>LocatorGroupsPair</code> corresponding to one
     *  of the lookup services that was started; and in which the locator of
     *  the associated lookup service is paired with the set of new groups
     *  generated by this method.
     *
     *  This method can be used to cause various discovered/discarded/changed
     *  events to be sent by the discovery helper utility.
     */
    protected List<LocatorGroupsPair> replaceMemberGroups(boolean alternate) {
        return getLookupServices().replaceMemberGroups(alternate);
    }//end replaceMemberGroups

   /**  For each lookup service that has been started, this method replaces
     *  the lookup service's current member groups with a new set of groups
     *  containing NONE of the current groups of interest.
     *
     *  This method is intended to guarantee that the member groups of each of
     *  the lookup services that has been started will be changed to a set
     *  of groups which contains NONE of the original member groups of the
     *  associated lookup service, and also contains NONE of the groups the
     *  discovery helper utility is currently interested in discovering.
     *
     *  This method returns an <code>ArrayList</code> in which each element
     *  is an instance of <code>LocatorGroupsPair</code> corresponding to one
     *  of the lookup services that was started; and in which the locator of
     *  the associated lookup service is paired with the set of new groups
     *  generated by this method.
     *
     *  This method can be used to cause various discovered/discarded/changed
     *  events to be sent by the discovery helper utility.
     */
   protected List<LocatorGroupsPair> replaceMemberGroups() {
       return replaceMemberGroups(false);
   }//end replaceMemberGroups

    /** For each lookup service that has been started, this method replaces
     *  the lookup service's current member groups with the given set of
     *  groups.
     *
     *  This method returns an <code>ArrayList</code> in which each element
     *  is an instance of <code>LocatorGroupsPair</code> corresponding to one
     *  of the lookup services that was started; and in which the locator of
     *  the associated lookup service is paired with given set of groups.
     */
   protected List<LocatorGroupsPair> replaceMemberGroups(String[] newGroups) {
       return getLookupServices().replaceMemberGroups(newGroups);
    }//end replaceMemberGroups

    /** For N of the lookup services started, this method replaces the lookup
     *  service's current member groups with the given set of groups; where
     *  N is determined by the value of the given <code>nReplacements</code>
     *  parameter.
     *
     *  This method returns an <code>ArrayList</code> in which each element
     *  is an instance of <code>LocatorGroupsPair</code> corresponding to one
     *  of the lookup services that was started; and in which the locator of
     *  the associated lookup service is paired with the given set of groups.
     */
   protected List<LocatorGroupsPair> replaceMemberGroups(int nReplacements,
                                           String[] newGroups)
   {
        return getLookupServices().replaceMemberGroups(nReplacements,newGroups);
    }//end replaceMemberGroups

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup discovery services
     *  needed by that test run. Useful when all of the lookup discovery
     *  services are to be started during construct processing.
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
     */
    protected void startInitLDS() throws Exception {
        getLookupDiscoveryServices().startInitLDS();
    }//end startInitLDS

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, any additional lookup discovery services 
     *  needed by that test run. Useful when an initial set of lookup discovery
     *  services are to be started during construct processing, and an additional
     *  set of lookup discovery services are to be started at some later time,
     *  after the test has already begun execution.
     */
    protected void startAddLDS() throws Exception {
        getLookupDiscoveryServices().startAddLDS();
    }//end startAddLDS

//    /** Convenience method that creates an array of Class objects in which
//     *  element corresponds to a different service class type that will be
//     *  started remotely and/or locally. The array returned by this method
//     *  can be used when building templates that will be used to match on
//     *  class type.
//     */
//    protected Class[] getServiceClassArray() {
//        ArrayList classnamesList = new ArrayList(5);
//        ArrayList loadedClassList = new ArrayList(expectedServiceList.size());
//        if( (nLookupDiscoveryServices+nRemoteLookupDiscoveryServices) > 0) {
//            classnamesList.add
//                   (new String("net.jini.discovery.LookupDiscoveryService") );
//        }//endif
//        if( (nLeaseRenewalServices+nRemoteLeaseRenewalServices) > 0) {
//            classnamesList.add
//                          (new String("net.jini.lease.LeaseRenewalService") );
//        }//endif
//        if( (nEventMailboxServices+nRemoteEventMailboxServices) > 0) {
//            classnamesList.add(new String("net.jini.event.EventMailbox") );
//        }//endif
//        for(int i=0;i<classnamesList.size();i++) {
//            String classname = (String)classnamesList.get(i);
//            try {
//                loadedClassList.add(Class.forName(classname));
//            } catch(ClassNotFoundException e) {
//                logger.log(Level.FINE,
//                                     " ClassNotFoundException while loading "
//                                     +"service class "+classname);
//                e.printStackTrace();
//            }//end try
//        }//end loop
//        return ( (Class[])loadedClassList.toArray
//                                        (new Class[loadedClassList.size()]) );
//    }//end getServiceClassArray

//    /** Special-purpose method that should be called by the remote component
//     *  of a manual test after that component of the test has started all of
//     *  the services for which it was configured. This method is useful
//     *  in such a manual test scenario because it is generally desirable for
//     *  the remote component to enter into a "wait state" so that the local
//     *  component of the test can run and interact with the remote component
//     *  without having to worry about the remote component shutting down
//     *  unexpectedly.
//     */
//    protected void waitForKeyboardInput() {
//        System.out.println(" start test in separate VM ... ");
//        System.out.println(" ***** WHEN DONE, PRESS ANY KEY TO EXIT *****");
//        try {
//            int c=System.in.read();
//        } catch (IOException e) { }
//    }//end waitForKeyboardInput

}//end class BaseQATest
