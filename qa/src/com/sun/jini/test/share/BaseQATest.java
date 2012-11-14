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

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATest;

import com.sun.jini.test.share.DiscoveryProtocolSimulator;
import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.share.GroupsUtil;
import com.sun.jini.test.share.LocatorsUtil;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import java.rmi.activation.ActivationException;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;

import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryChangeListener;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.LookupDiscoveryService;

import net.jini.lookup.DiscoveryAdmin;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceRegistrar;

import java.io.IOException;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

/**
 * This class is an abstract class that contains common functionality 
 * related to setup and tearDown that may be useful to many of tests.
 * acts as the base class which
 * 
 * This abstract class contains various static inner classes, any one
 * of which can be used as a listener to participate in the lookup
 * service discovery process on behalf of the tests that sub-class this
 * abstract class.
 * <p>
 * This class provides an implementation of both the <code>setup</code> method
 * and the <code>tearDown</code> method, which perform -- respectively --
 * standard functions related to the initialization and clean up of the
 * system state necessary to execute the test.
 * 
 * @see com.sun.jini.qa.harness.QAConfig
 * @see com.sun.jini.qa.harness.QATest
 */
abstract public class BaseQATest extends QATest {

    protected static final int AUTOMATIC_LOCAL_TEST = Integer.MAX_VALUE;
    protected static final int MANUAL_TEST_REMOTE_COMPONENT = 1;
    protected static final int MANUAL_TEST_LOCAL_COMPONENT  = 2;

    protected volatile boolean useFastTimeout = false;//for faster completion
    protected volatile int fastTimeout = 10;//default value
    protected volatile boolean displayOn = true;//verbose in waitForDiscovery/Discard
    protected volatile boolean debugsync = false;//turns on synchronization debugging

    /** Ordered pair containing a LookupLocator and the corresponding groups */
    public class LocatorGroupsPair {
        public final LookupLocator locator;
        public final String[]      groups;
        public LocatorGroupsPair(LookupLocator locator, String[] groups) {
            this.locator = locator;
            this.groups  = groups;
        }//end constructor
        public boolean equals(Object obj) {
            if(this == obj) return true;
            if( !(obj instanceof LocatorGroupsPair) ) return false;
            if(!((LocatorGroupsPair)obj).locator.equals(locator)) return false;
            return GroupsUtil.compareGroupSets(groups,
                                              ((LocatorGroupsPair)obj).groups, Level.OFF);
        }//end equals
    }//end class LocatorGroupsPair

    /** Data structure representing a set of LookupLocators & groups to join */
    public class ToJoinPair {
        public final LookupLocator[] locators;
        public final String[]        groups;
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
        public final HashMap discoveredMap = new HashMap(11);
        public final HashMap discardedMap  = new HashMap(11);

        public final HashMap expectedDiscoveredMap = new HashMap(11);
        public final HashMap expectedDiscardedMap  = new HashMap(11);

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
                List uLocsList = new ArrayList(eSet.size());
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
                    discoveredMap.put(pair.locator,pair.groups);
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
                    discoveredMap.put(pair.locator,pair.groups);
                    expectedDiscardedMap.put(pair.locator,pair.groups);
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
                                 toLocatorArray(allLookupsToStart),
                                 toGroupsArray(allLookupsToStart));
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
                    LookupLocator curLoc    = pair.locator;
                    String[]      curGroups = pair.groups;
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

        /** Returns all of the groups (duplicates removed), across all lookup
         *  services, that are currently expected to be discovered.
         */
        String[] getCurExpectedDiscoveredGroups() {
            HashSet groupSet = new HashSet(expectedDiscoveredMap.size());
            Set eSet = expectedDiscoveredMap.entrySet();
	    Iterator iter = eSet.iterator();
            while(iter.hasNext()) {
                Map.Entry pair = (Map.Entry)iter.next();
                String[] curGroups = (String[])pair.getValue();
                for(int i=0;i<curGroups.length;i++) {
                    groupSet.add(curGroups[i]);
                }//end loop
            }//end loop
            return ((String[])(groupSet).toArray(new String[groupSet.size()]));
        }//end getCurExpectedDiscoveredGroups

        public void discovered(DiscoveryEvent evnt) {
	    /* the LDM (actually, its ld) has already prepared these registrars */
            ServiceRegistrar[] regs = evnt.getRegistrars();
            if(regs != null) {
                logger.log(Level.FINE, " discovery event received "
                                  +"-- "+regs.length+" lookup(s)");
                Map groupsMap = evnt.getGroups();
                synchronized(this) {
                    boolean oneDiscovered = false;
                    List lusList = getLookupListSnapshot
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
                                           = toLocatorArray(allLookupsToStart);
                                if(isElementOf(loc,locsToDiscover)) {
                                lookupOK = true;//is lookup of interest
                                }//endif
                            }//endif
                        }//endif
                        /* care only about the lookups of interest */
                        if( !lookupOK ) continue;
                        discardedMap.remove(loc);
                        discoveredMap.put(loc,groups);
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
                            (LocatorGroupsPair)regsToLocGroupsMap.get(regs[i]);
                        if(pair == null) continue;//lookup started outside test
                        LookupLocator loc = pair.locator;
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

        public final HashMap changedMap = new HashMap(11);
        public final HashMap expectedChangedMap = new HashMap(11);

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
                                      toGroupsArray(allLookupsToStart));
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
                Set eSet = discoveredMap.entrySet();
	        Iterator iter = eSet.iterator();
                while(iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    LookupLocator loc = (LookupLocator)pair.getKey();
                    String[] oldGroups = (String[])pair.getValue();

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
                    List regList = getLookupListSnapshot
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
                            discoveredMap.put(loc,groups);//replace old loc
                            discardedMap.remove(loc);
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
                               +discoveredMap.size());
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
        private int startIndx = 0;
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
		LookupLocator l = pair.locator;
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

    protected volatile String implClassname;

    protected volatile int maxSecsEventWait  = 10 * 60;
    protected volatile int announceInterval  = 2 * 60 * 1000;
    protected volatile int originalAnnounceInterval = 0;
    protected volatile int minNAnnouncements = 2;
    protected volatile int nIntervalsToWait  = 3;

    protected volatile boolean delayLookupStart = false;

    protected volatile int nLookupServices          = 0;
    protected volatile int nAddLookupServices       = 0;
    protected volatile int nRemoteLookupServices    = 0;
    protected volatile int nAddRemoteLookupServices = 0;

    protected volatile int nServices    = 0;//local/serializable test services
    protected volatile int nAddServices = 0;//additional local/serializable services

    /* Specified jini services */
    protected volatile String ldsImplClassname               = "no ImplClassname";
    protected volatile int nLookupDiscoveryServices          = 0;
    protected volatile int nAddLookupDiscoveryServices       = 0;
    protected volatile int nRemoteLookupDiscoveryServices    = 0;
    protected volatile int nAddRemoteLookupDiscoveryServices = 0;

    protected volatile String lrsImplClassname               = "no ImplClassname";
    protected volatile int nLeaseRenewalServices             = 0;
    protected volatile int nAddLeaseRenewalServices          = 0;
    protected volatile int nRemoteLeaseRenewalServices       = 0;
    protected volatile int nAddRemoteLeaseRenewalServices    = 0;

    protected volatile String emsImplClassname               = "no ImplClassname";
    protected volatile int nEventMailboxServices             = 0;
    protected volatile int nAddEventMailboxServices          = 0;
    protected volatile int nRemoteEventMailboxServices       = 0;
    protected volatile int nAddRemoteEventMailboxServices    = 0;

    /* Attributes per service */
    protected volatile int nAttributes    = 0;
    protected volatile int nAddAttributes = 0;

    protected volatile int nSecsLookupDiscovery  = 30;
    protected volatile int nSecsServiceDiscovery = 30;
    protected volatile int nSecsJoin             = 30;

    protected volatile String remoteHost = "UNKNOWN_HOST";

    /* Data structures - lookup services */
    protected final List initLookupsToStart = Collections.synchronizedList(new ArrayList(11));
    protected final List addLookupsToStart  = Collections.synchronizedList(new ArrayList(11));
    protected final List allLookupsToStart  = Collections.synchronizedList(new ArrayList(11));
    protected final List lookupsStarted     = Collections.synchronizedList(new ArrayList(11));

    protected final List lookupList = new ArrayList(1); //Synchronized externally.
    protected final Map genMap = Collections.synchronizedMap(new HashMap(11));
    protected volatile int nStarted = 0;
    /* Data structures - lookup discovery services */
    protected final List initLDSToStart = Collections.synchronizedList(new ArrayList(11));
    protected final List addLDSToStart  = Collections.synchronizedList(new ArrayList(11));
    protected final List allLDSToStart  = Collections.synchronizedList(new ArrayList(11));
    protected final List ldsList = Collections.synchronizedList(new ArrayList(1));

    protected final List expectedServiceList = Collections.synchronizedList(new ArrayList(1));

    protected volatile QAConfig config = null;

    private volatile boolean announcementsStopped = false;

    /* Need to keep a local mapping of registrars to their corresponding 
     * locators and groups so that when a registrar is discarded (indicating
     * that a remote call to retrieve the discarded registrar's locator and/or
     * group information should not be made), the locator and/or groups can
     * be retrieved through a non-remote mechanism. Each time a lookup service
     * is started, the registrar and its locator/groups pair are added to this
     * map.
     */
    protected final Map regsToLocGroupsMap = Collections.synchronizedMap(new HashMap(11));

    /* Private instance variables */

    /* Need to keep track of member groups by the index of the corresponding
     * lookup service so those groups can be mapped to the correct member
     * groups configuration item. 
     */
    private final List memberGroupsList = Collections.synchronizedList(new ArrayList(11));

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
    public void setup(QAConfig config) throws Exception {
	super.setup(config);
	this.config = config;
	logger.log(Level.FINE, " setup()");
	debugsync = getConfig().getBooleanConfigVal("qautil.debug.sync",false);
	getSetupInfo();
	getLookupInfo();
	if(!delayLookupStart) {
	    /* start desired initial lookup services so that they are up
	     * and running before the test actually begins its execution
	     */
	    startInitLookups();
	}//endif
	getLDSInfo();
	startInitLDS();
    }//end setup

    /** Cleans up any remaining state not already cleaned up by the test
     *  itself. If simulated lookup services were used by the test, this 
     *  method stops the multicast generators that were created and
     *  registered with RMID. This method then performs any standard clean
     *  up duties performed in the super class.
     */
    public void tearDown() {
        try {
            if(genMap.size() > 0) {
                logger.log(Level.FINE, 
                                " tearDown - terminating lookup service(s)");
                /* Stop announcements if the generator is simulated, but allow
                 * the super class' tearDown method to actually destroy the
                 * lookup services (simulated or non-simulated).
                 */
                if( !announcementsStopped ) {
                    Iterator iter = genMap.keySet().iterator();
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
	    if(originalAnnounceInterval == 0) {
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
            locArray[i] = pair.locator;
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
        List groupsList = new ArrayList(11);
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            String[] curGroups = pair.groups;
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
            if(loc.equals(pair.locator)) return pair.groups;
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
            String[] curMemberGroups = pair.groups;
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
        List filteredList = new ArrayList(list.size());
        for(int i=0;i<list.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)list.get(i);
            String[] groups = pair.groups;
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
    protected List getLookupListSnapshot() {
        return getLookupListSnapshot(null);
    }//end getLookupListSnapshot

    protected List getLookupListSnapshot(String infoStr) {
        String str = ( (infoStr == null) ? 
                       "     sync on lookupList --> " :
                       "     "+infoStr+" - sync on lookupList --> ");
        if(debugsync) logger.log(Level.FINE, str+"requested");
        synchronized(lookupList) {
            if(debugsync) logger.log(Level.FINE, str+"granted");
            List listSnapshot = new ArrayList(lookupList.size());
            for(int i=0;i<lookupList.size();i++) {
                listSnapshot.add(i,lookupList.get(i));
            }//end loop
            if(debugsync) logger.log(Level.FINE, str+"released");
            return listSnapshot;
        }//end sync(lookupList)
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
        String str = ( (infoStr == null) ? 
                       "     sync on lookupList --> " :
                       "     "+infoStr+" - sync on lookupList --> ");
        if(debugsync) logger.log(Level.FINE, str+"requested");
        synchronized(lookupList) {
            if(debugsync) logger.log(Level.FINE, str+"granted");
            int size = lookupList.size();
            if(debugsync) logger.log(Level.FINE, str+"released");
            return size;
        }//end sync(lookupList)
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
    protected boolean portInUse(int port) {
        for(int i=0;i<lookupsStarted.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)lookupsStarted.get(i);
            int curPort = (pair.locator).getPort();
            if(port == curPort) return true;
        }//end loop
        return false;
    }//end portInUse

    /** Constructs a <code>LookupLocator</code> using configuration information
     *  corresponding to the value of the given parameter <code>indx</code>.
     *  Useful when lookup services need to be started, or simply when
     *  instances of <code>LookupLocator</code> need to be constructed with
     *  meaningful state.
     */
    protected LookupLocator getTestLocator(int indx) throws TestException {
        /* Locator for lookup service corresponding to indx */
        int port = getConfig().getServiceIntProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "port", indx);
        if (port == Integer.MIN_VALUE) {
	    port = 4160;
	}
	String hostname = 
	    config.getServiceHost("net.jini.core.lookup.ServiceRegistrar", indx, null);
	logger.log(Level.FINER, "getServiceHost returned " + hostname);
	if (hostname == null) {
	    hostname = "localhost";
	    try {
		hostname = InetAddress.getLocalHost().getHostName();
	    } catch(UnknownHostException e) {
		e.printStackTrace();
	    }
	}
        return QAConfig.getConstrainedLocator(hostname,port);
    }//end getTestLocator

    /** Constructs a <code>LookupLocator</code> using configuration information
     *  corresponding to the value of the given parameter <code>indx</code>.
     *  Useful when lookup services need to be started, or simply when
     *  instances of <code>LookupLocator</code> need to be constructed with
     *  meaningful state.
     */
    protected LookupLocator getRemoteTestLocator(int indx) {
        /* Locator for lookup service corresponding to indx */
        int port = getConfig().getServiceIntProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "port", indx);
        if (port == Integer.MIN_VALUE) {
	    port = 4160;
	}
	// used for book keeping only, so don't need a constrainable locator
        return QAConfig.getConstrainedLocator(remoteHost,port);
    }//end getRemoteTestLocator

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup services needed by
     *  that test run. Useful when all of the lookup services are to be
     *  started during setup processing.
     */
    protected void startAllLookups() throws Exception {
        startInitLookups();
        startAddLookups();
    }//end startAllLookups

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup services INITIALLY
     *  needed by that test run. Useful when an initial set of lookups are
     *  to be started during setup processing, and (possibly) an additional
     *  set of lookups are to be started at some later time, after the test
     *  has already begun execution.
     */
    protected void startInitLookups() throws Exception {
        if(nLookupServices > 0) {
            /* Skip over remote lookups to the indices of the local lookups */
            int n0 = nRemoteLookupServices + nAddRemoteLookupServices;
            int n1 = n0 + nLookupServices;
            for(int i=n0;i<n1;i++) {
                LocatorGroupsPair pair
                               = (LocatorGroupsPair)initLookupsToStart.get(i);
                int port = (pair.locator).getPort();
                if(portInUse(port)) port = 0;
                String hostname = startLookup(i,port, pair.locator.getHost());
		logger.log(Level.FINEST, 
			   "service host is '" + hostname 
			   + "', this host is '" + config.getLocalHostName() + "'");
                if(port == 0) {
                    Object locGroupsPair = lookupsStarted.get(i);
                    initLookupsToStart.set(i,locGroupsPair);
                    allLookupsToStart.set(i,locGroupsPair);
                }
		LocatorGroupsPair p = (LocatorGroupsPair) initLookupsToStart.get(i);
		LookupLocator l = p.locator;
		logger.log(Level.FINEST, "init locator " + i + " = " + l);
            }//end loop
            if(testType != MANUAL_TEST_LOCAL_COMPONENT) {
                if(!listsEqual(initLookupsToStart,lookupsStarted)) {
                    logger.log(Level.FINE,
                                      " initial lookups started != "
                                      +"initial lookups wanted");
                    logger.log(Level.FINE,
                                      " initial lookups started --");
                    displayLookupStartInfo(lookupsStarted);
                    logger.log(Level.FINE,
                                      " initial lookups wanted --");
                    displayLookupStartInfo(initLookupsToStart);
                    tearDown();
                    throw new TestException("initial lookups started != "
                                              +"initial lookups wanted");
                }//endif
            }//endif
        }//endif(nLookupServices > 0)
    }//end startInitLookups

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, any additional lookup services 
     *  needed by that test run. Useful when an initial set of lookups are
     *  to be started during setup processing, and an additional set of
     *  lookups are to be started at some later time, after the test
     *  has already begun execution.
     */
    protected void startAddLookups() throws Exception {
        if(nAddLookupServices > 0) {
            /* Skip over remote lookups and lookups already started to the
             * indices of the additional local lookups
             */
            int n0 = nRemoteLookupServices + nAddRemoteLookupServices
                                           + lookupsStarted.size();
            int n1 = n0 + nAddLookupServices;
            for(int i=n0;i<n1;i++) {
                int j = i-n0;
                LocatorGroupsPair pair
                               = (LocatorGroupsPair)addLookupsToStart.get(j);
                int port = (pair.locator).getPort();
                if(portInUse(port)) port = 0;
                String hostname = startLookup(i,port, pair.locator.getHost());
                if(port == 0) {
                    Object locGroupsPair = lookupsStarted.get(i);
                    addLookupsToStart.set(j,locGroupsPair);
                    allLookupsToStart.set(i,locGroupsPair);
                }
		LocatorGroupsPair p = (LocatorGroupsPair) addLookupsToStart.get(j);
		LookupLocator l = p.locator;
		logger.log(Level.FINEST, "add locator " + j + " = " + l);
            }//end loop
            if(testType != MANUAL_TEST_LOCAL_COMPONENT) {
                if(!listsEqual(allLookupsToStart,lookupsStarted)) {
                    logger.log(Level.FINE,
                                      " additional lookups started != "
                                      +"additional lookups wanted");
                    logger.log(Level.FINE,
                                      " additional lookups started --");
                    displayLookupStartInfo(lookupsStarted);
                    logger.log(Level.FINE,
                                      " additional lookups wanted --");
                    displayLookupStartInfo(allLookupsToStart);
                    tearDown();
                    throw new TestException("additional lookups started != "
                                              +"additional lookups wanted");
                }//endif
            }//endif
        }//endif(nAddLookupServices > 0)
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
        logger.log(Level.FINE, " starting lookup service "+indx);
        /* retrieve the member groups with which to configure the lookup */
        String[] memberGroups = (String[])memberGroupsList.get(indx);
        ServiceRegistrar lookupProxy = null;
	String simulatorName = 
	    "com.sun.jini.test.services.lookupsimulator.LookupSimulatorImpl";
        if(implClassname.equals(simulatorName)) {
            DiscoveryProtocolSimulator generator = null;
            if(debugsync) logger.log(Level.FINE,
                              "     BaseQATest.startLookup - "
                              +"sync on lookupList --> requested");
            synchronized(lookupList) {
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"sync on lookupList --> granted");
                try {
                    /* Use either a random or an explicit locator port */
                    generator = new DiscoveryProtocolSimulator
                                                   (config,memberGroups,manager, port);
                } catch (ActivationException ex) {
                    ex.fillInStackTrace();
                    logger.log(Level.FINE, null, ex);
                    throw ex;
                } catch (IOException ex) {
                    ex.fillInStackTrace();
                    logger.log(Level.FINE, null, ex);
                    throw ex;
                } catch (TestException ex) {
                    ex.fillInStackTrace();
                    logger.log(Level.FINE, null, ex);
                    throw ex;
                }
                genMap.put( generator, memberGroups );
                lookupProxy = generator.getLookupProxy();
                lookupList.add( lookupProxy );
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"  added new proxy to lookupList");
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"sync on lookupList --> released");
            }//end sync(lookupList)
            /* Force non-unique groups for manual tests */
            if(    (testType == MANUAL_TEST_REMOTE_COMPONENT)
                || (testType == MANUAL_TEST_LOCAL_COMPONENT) ) 
            {
                generator.setMemberGroups(memberGroups);
            }//endif
        } else {//start a non-simulated lookup service implementation
            if(debugsync) logger.log(Level.FINE,
                              "     BaseQATest.startLookup - "
                              +"sync on lookupList --> requested");
            synchronized(lookupList) {
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"sync on lookupList --> granted");
		/* returned proxy is already prepared */
                lookupProxy = manager.startLookupService(serviceHost);
                lookupList.add( lookupProxy );
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"  added new proxy to lookupList");
                if(debugsync) logger.log(Level.FINE,
                                  "     BaseQATest.startLookup - "
                                  +"sync on lookupList --> released");
            }//end sync(lookupList)
            genMap.put( lookupProxy, memberGroups );
            /* Force non-unique groups for manual tests */
            if(    (testType == MANUAL_TEST_REMOTE_COMPONENT)
                || (testType == MANUAL_TEST_LOCAL_COMPONENT) ) 
            {
                if(lookupProxy instanceof Administrable) {
                    Object admin = ((Administrable)lookupProxy).getAdmin();
		    admin = getConfig().prepare("test.reggieAdminPreparer", 
						admin);
                    if(admin instanceof DiscoveryAdmin) {
                        ((DiscoveryAdmin)admin).setMemberGroups(memberGroups);
                    }
                }
            }
        }

        LookupLocator lookupLocator = 
	    QAConfig.getConstrainedLocator(lookupProxy.getLocator());
        LocatorGroupsPair locGroupsPair = new LocatorGroupsPair(lookupLocator,
                                                                memberGroups);
        try {
            lookupsStarted.add(indx,locGroupsPair);
        } catch(IndexOutOfBoundsException e) {
            /* There must be remote lookups, simply add it without the index */
            lookupsStarted.add(locGroupsPair);
        }
        regsToLocGroupsMap.put(lookupProxy,locGroupsPair);

        LocatorsUtil.displayLocator(lookupLocator,
                                    "  locator",Level.FINE);
        logger.log(Level.FINE, "   memberGroup(s) = "
                          +GroupsUtil.toCommaSeparatedStr(memberGroups));
        nStarted = genMap.size();
	return serviceHost;
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
    protected boolean locGroupsMapsEqual( Map map0, Map map1 ) {
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
    protected boolean locGroupsMapsEqual( Map map0, Map map1,
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
    protected boolean locGroupsMapsEqualByLoc( Map map0, Map map1 ) {
        return locGroupsMapsEqualByLoc(map0,map1,false);
    }//end locGroupsMapsEqualByLoc

    /** Given two locator-to-groups mappings, this method compares the
     *  elements of the locator key sets, displaying the locator
     *  information in given map if the <code>displayOn</code> parameter
     *  is <code>true</code>. This method returns <code>true</code>
     *  if the locators in the key sets are found to be equal; returns
     *  <code>false</code> otherwise.
     */
    protected boolean locGroupsMapsEqualByLoc( Map map0, Map map1,
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
    protected boolean locGroupsMapsEqualByGroups( Map map0, Map map1 ) {
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
    protected boolean locGroupsMapsEqualByGroups( Map map0, Map map1,
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
        ServiceRegistrar[] proxies = new ServiceRegistrar[genMap.size()];
	Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            Object curObj = iter.next();
            if(curObj instanceof DiscoveryProtocolSimulator) {
                proxies[i]
                      = ((DiscoveryProtocolSimulator)curObj).getLookupProxy();
            } else {
                proxies[i] = (ServiceRegistrar)curObj;
            }//endif
        }//end loop
        return proxies;
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
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            Object curObj = iter.next();
            ServiceRegistrar regProxy = null;
            if(curObj instanceof DiscoveryProtocolSimulator) {
                DiscoveryProtocolSimulator curGen
                                         = (DiscoveryProtocolSimulator)curObj;
                regProxy = curGen.getLookupProxy();
                curGen.stopAnnouncements();
            } else {
                regProxy = (ServiceRegistrar)curObj;
            }//endif
            /* destroy lookup service i */
            manager.destroyService(regProxy);
        }//end loop
        announcementsStopped = true;
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
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            logger.log(Level.FINE, " stop multicast announcements "
                              +"from lookup service "+i+" ...");
            Object curObj = iter.next();
            if(curObj instanceof DiscoveryProtocolSimulator) {
                DiscoveryProtocolSimulator curGen
                                         = (DiscoveryProtocolSimulator)curObj;
                curGen.stopAnnouncements();
            } else {//cannot stop the announcements, must destroy the lookup
                /* It's not a simulated LUS, thus the only way to stop the
                 * announcements is to destroy each LUS individually.
                 */
                manager.destroyService((ServiceRegistrar)curObj);
            }//endif
        }//end loop
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
    protected List pingAndDiscard(ServiceRegistrar[] proxies,
                                       DiscoveryManagement dm,
                                       LookupListener listener)
    {
        List proxiesToDiscard      = new ArrayList(1);
        List locGroupsNotDiscarded = new ArrayList(1);
        /* Determine proxies to discard and proxies that cannot be discarded */
        for(int i=0;i<proxies.length;i++) {
            LocatorGroupsPair curPair
                      = (LocatorGroupsPair)regsToLocGroupsMap.get(proxies[i]);
            try {
                LookupLocator loc = QAConfig.getConstrainedLocator(proxies[i].getLocator());
                logger.log(Level.FINE, " ");
                if(curPair != null) {
                    logger.log(Level.FINE,
                                      " warning -- lookup service "
                                      +"is still reachable --> locator = "
                                      +curPair.locator+"\n");
                    locGroupsNotDiscarded.add(curPair);
                } else {
                    logger.log(Level.FINE,
                                      " warning -- lookup service "+i
                                      +" is still reachable\n");
                }//endif
            } catch(RemoteException e) {//lookup is un-reachable, discard it
                proxiesToDiscard.add(proxies[i]);
            }
        }//end loop
        /* Perform the actual discards to generate the discard events */
        for(int i=0;i<proxiesToDiscard.size();i++) {
            dm.discard((ServiceRegistrar)proxiesToDiscard.get(i));
        }//end loop
        return locGroupsNotDiscarded;//return proxies we couldn't discard
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
        logger.log(Level.FINE, 
                          " number of announcements to wait for    -- "
                          +minNAnnouncements);
        logger.log(Level.FINE, 
                          " number of intervals to wait through    -- "
                          +nIntervalsToWait);
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            DiscoveryProtocolSimulator curGen = 
                                  (DiscoveryProtocolSimulator)iter.next();
            logger.log(Level.FINE, 
                              " gen "+i
                              +" - waiting ... announcements so far -- "
                              +curGen.getNAnnouncementsSent());
            for(int j=0; ((j<nIntervalsToWait)
                &&(curGen.getNAnnouncementsSent()< minNAnnouncements));j++)
            {
                DiscoveryServiceUtil.delayMS(announceInterval);
                logger.log(Level.FINE, 
                                  " gen "+i
                                  +" - waiting ... announcements so far -- "
                                  +curGen.getNAnnouncementsSent());
            }//end loop
            logger.log(Level.FINE, 
                              " gen "+i
                              +" - wait complete ... announcements  -- "
                              +curGen.getNAnnouncementsSent());
        }//end loop
    }//end verifyAnnouncementsSent

    /** This method replaces, with the given set of groups, the current
     *  member groups of the given lookup service (<code>generator</code>).
     *  This method returns an instance of <code>LocatorGroupsPair</code> in
     *  which the locator of the given lookup service is paired with the given
     *  set of new groups.
     */
    protected LocatorGroupsPair replaceMemberGroups(Object generator,
                                                    String[] newGroups)
                                                        throws RemoteException
    {
        ServiceRegistrar regProxy = null;
        if(generator instanceof DiscoveryProtocolSimulator) {
            regProxy
                    = ((DiscoveryProtocolSimulator)generator).getLookupProxy();
            ((DiscoveryProtocolSimulator)generator).setMemberGroups(newGroups);
        } else {
            regProxy = (ServiceRegistrar)generator;
            DiscoveryAdmin admin
                    = (DiscoveryAdmin)( ((Administrable)regProxy).getAdmin() );
	    try {
                admin = (DiscoveryAdmin)
		        getConfig().prepare("test.reggieAdminPreparer", admin);
	    } catch (TestException e) {
		throw new RemoteException("Problem preparing admin", e);
	    }
            admin.setMemberGroups(newGroups);
        }//endif
        LookupLocator loc = QAConfig.getConstrainedLocator(regProxy.getLocator());
        return new LocatorGroupsPair(loc,newGroups);
    }//end replaceMemberGroups

    /** Depending on the value of the boolean parameter <code>alternate</code>,
     *  this method either replaces the current member groups of the
     *  given lookup service (<code>generator</code>) with a new set
     *  of groups containing NONE of the current groups of interest
     *  (<code>alternate</code> == <code>false</code>), or a new set
     *  of groups in which, alternately, half the elements are equal
     *  to their counterparts in the original set, and half are different
     *  from their counterparts.
     *  
     *  This method is intended to guarantee that the new set of groups 
     *  that replaces the member groups of the given lookup service contains
     *  some, if not all, new groups different from the original groups.
     *
     *  This method returns an instance of  <code>LocatorGroupsPair</code>
     *  in which the locator of the given lookup service is paired with the
     *  set of new groups generated by this method.
     */
    protected LocatorGroupsPair replaceMemberGroups(Object generator,
                                                    boolean alternate)
                                                        throws RemoteException
    {
        ServiceRegistrar regProxy = null;
	DiscoveryAdmin admin = null;
	// only prepare the real proxy (until simulators are secure)
        if(generator instanceof DiscoveryProtocolSimulator) {
            regProxy
                   = ((DiscoveryProtocolSimulator)generator).getLookupProxy();
	    admin = (DiscoveryAdmin)( ((Administrable)regProxy).getAdmin() );
        } else {
            regProxy = (ServiceRegistrar)generator;
	    admin = (DiscoveryAdmin)( ((Administrable)regProxy).getAdmin() );
	    try {
                admin = (DiscoveryAdmin)
		        getConfig().prepare("test.reggieAdminPreparer", admin);
	    } catch (TestException e) {
		throw new RemoteException("Problem preparing admin", e);
	    }
        }//endif
        String[] groups    = admin.getMemberGroups();
        String[] newGroups =  ( (groups.length > 0) ? 
                          (new String[groups.length]) :
                          (new String[] {"Group_"+regProxy.getServiceID()}) );
        if(newGroups.length == 0) {
            logger.log(Level.FINE, "   NO_GROUPS");
        } else {
            for(int i=0;i<newGroups.length;i++) {
                boolean oddIndx = !((i%2) == 0);
                newGroups[i] = ( (alternate && oddIndx) ? new String(groups[i])
                                             : new String(groups[i]+"_new") );
                logger.log(Level.FINE, "   newGroups["+i+"] = "
                                           +newGroups[i]);
            }//end loop
        }//endif
        return replaceMemberGroups(generator,newGroups);
    }//end replaceMemberGroups

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
   protected List replaceMemberGroups(boolean alternate) {
        List locGroupsList = new ArrayList(genMap.size());
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            /* Replace the member groups of the current lookup service */
            logger.log(Level.FINE, " lookup service "+i+" - "
                              +"replacing member groups with -- ");
            try {
                locGroupsList.add(replaceMemberGroups(iter.next(),alternate));
            } catch(RemoteException e) {
                logger.log(Level.FINE, 
                                  " failed to change member groups "
                                  +"for lookup service "+i);
                e.printStackTrace();
            }
        }//end loop
        return locGroupsList;
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
   protected List replaceMemberGroups() {
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
   protected List replaceMemberGroups(String[] newGroups) {
        return replaceMemberGroups(genMap.size(),newGroups);
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
   protected List replaceMemberGroups(int nReplacements,
                                           String[] newGroups)
   {
        List locGroupsList = new ArrayList(genMap.size());
        Iterator iter = genMap.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            Object generator = iter.next();
            if(i<nReplacements) {
                /* Replace the member groups of the current lookup service */
                logger.log(Level.FINE, " lookup service "+i+" - "
                                  +"replacing member groups with --");
                if(newGroups.length == 0) {
                    logger.log(Level.FINE, "   NO_GROUPS");
                } else {
                    for(int j=0;j<newGroups.length;j++) {
                        logger.log(Level.FINE, "   newGroups["+j+"] = "
                                                   +newGroups[j]);
                    }//end loop
                }//endif
                try {
                    locGroupsList.add
                                  ( replaceMemberGroups(generator,newGroups) );
                } catch(RemoteException e) {
                    logger.log(Level.FINE, 
                                      " failed to change member groups "
                                      +"for lookup service "+i);
                    e.printStackTrace();
                }
            } else {//(i >= nReplacements)
                /* Leave member groups of the current lookup service as is*/
                logger.log(Level.FINE, " lookup service "+i+" - "
                                  +"leaving member groups unchanged --");
                ServiceRegistrar regProxy = null;
                if(generator instanceof DiscoveryProtocolSimulator) {
                    regProxy
                    = ((DiscoveryProtocolSimulator)generator).getLookupProxy();
                } else {
                    regProxy = (ServiceRegistrar)generator;
                }//endif
                try {
                    LookupLocator loc = QAConfig.getConstrainedLocator(regProxy.getLocator());
                    String[] groups   = regProxy.getGroups();
                    if(groups.length == 0) {
                        logger.log(Level.FINE, "   NO_GROUPS");
                    } else {
                        for(int j=0;j<groups.length;j++) {
                            logger.log(Level.FINE, "   groups["+j+"] = "
                                                       +groups[j]);
                        }//end loop
                    }//endif
                    locGroupsList.add
                                  ( new LocatorGroupsPair(loc,groups) );
                } catch(RemoteException e) {
                    logger.log(Level.FINE, 
                                      " failed on locator/groups retrieval "
                                      +"for lookup service "+i);
                    e.printStackTrace();
                }
            }//endif
        }//end loop
        return locGroupsList;
    }//end replaceMemberGroups

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup discovery services
     *  needed by that test run. Useful when all of the lookup discovery
     *  services are to be started during setup processing.
     */
    protected void startAllLDS() throws Exception {
        startInitLDS();
        startAddLDS();
    }//end startAllLDS

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup discovery services
     *  INITIALLY needed by that test run. Useful when an initial set of
     *  lookup discovery services are to be started during setup processing,
     *  and (possibly) an additional set of lookup discovery services are to
     *  be started at some later time, after the test has already begun
     *  execution.
     */
    protected void startInitLDS() throws Exception {
        if(nLookupDiscoveryServices > 0) {
            /* Skip over remote LDSs to the indices of the local LDSs */
            int n0 = nRemoteLookupDiscoveryServices 
                              + nAddRemoteLookupDiscoveryServices;
            int n1 = n0 + nLookupDiscoveryServices;
            for(int i=n0;i<n1;i++) {
                startLDS(i,(ToJoinPair)initLDSToStart.get(i));
            }//end loop
        }//endif(nLookupDiscoveryServices > 0)
    }//end startInitLDS

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, any additional lookup discovery services 
     *  needed by that test run. Useful when an initial set of lookup discovery
     *  services are to be started during setup processing, and an additional
     *  set of lookup discovery services are to be started at some later time,
     *  after the test has already begun execution.
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
                startLDS(i,(ToJoinPair)addLDSToStart.get(j));
            }//end loop
        }//endif(nAddLookupDiscoveryServices > 0)
    }//end startAddLDS

    /** Convenience method that can be used to start, at any point during
     *  the current test run, a single lookup discovery service with
     *  configuration referenced by the given parameter values. Useful when
     *  individual lookup discovery services are to be started at different
     *  points in time during the test run, or when a set of lookup discovery
     *  services are to be started from within a loop.
     */
    protected void startLDS(int indx, ToJoinPair tojoinPair) throws Exception {
        logger.log(Level.FINE, " starting lookup discovery service "
                                        +indx);
	/* the returned proxy is already prepared using the preparer named
	 * by the service preparername property
	 */
        LookupDiscoveryService ldsProxy =
             (LookupDiscoveryService)(manager.startService
                                ("net.jini.discovery.LookupDiscoveryService"));
        /* Force non-unique groups for manual tests */
        if(    (testType == MANUAL_TEST_REMOTE_COMPONENT)
            || (testType == MANUAL_TEST_LOCAL_COMPONENT) ) 
        {
            if(ldsProxy instanceof Administrable) {
                Object admin = ((Administrable)ldsProxy).getAdmin();
		admin = getConfig().prepare("test.fiddlerAdminPreparer", admin);
                if(admin instanceof JoinAdmin) {
                    ((JoinAdmin)admin).setLookupGroups(tojoinPair.groups);
                }//endif
            }//endif
        }//endif
        ldsList.add( ldsProxy );
        expectedServiceList.add( ldsProxy );
        LocatorsUtil.displayLocatorSet(tojoinPair.locators,
                                    "  locators to join",Level.FINE);
        GroupsUtil.displayGroupSet(tojoinPair.groups,
                                    "  groups to join",Level.FINE);
    }//end startLDS

    /* Retrieves/stores/displays configuration values for the current test */
    private void getSetupInfo() {
        testType = getConfig().getIntConfigVal("com.sun.jini.testType",
                                       AUTOMATIC_LOCAL_TEST);
        /* begin harness info */
        logger.log(Level.FINE, " ----- Harness Info ----- ");
        String harnessCodebase = System.getProperty("java.rmi.server.codebase",
                                                    "no codebase");
        logger.log(Level.FINE,
                          " harness codebase         -- "
                          +harnessCodebase);

        String harnessClasspath = System.getProperty("java.class.path",
                                                    "no classpath");
        logger.log(Level.FINE,
                          " harness classpath        -- "
                          +harnessClasspath);

        String discDebug = System.getProperty("net.jini.discovery.debug",
                                              "false");
        logger.log(Level.FINE,
                          " net.jini.discovery.debug        -- "
                          +discDebug);
        String regDebug = System.getProperty("com.sun.jini.reggie.proxy.debug",
                                             "false");
        logger.log(Level.FINE,
                          " com.sun.jini.reggie.proxy.debug -- "
                          +regDebug);
        String joinDebug = System.getProperty("com.sun.jini.join.debug",
                                              "false");
        logger.log(Level.FINE,
                          " com.sun.jini.join.debug         -- "
                          +joinDebug);
        String sdmDebug = System.getProperty("com.sun.jini.sdm.debug","false");
        logger.log(Level.FINE,
                          " com.sun.jini.sdm.debug          -- "
                          +sdmDebug);

        maxSecsEventWait = getConfig().getIntConfigVal
                      ("net.jini.discovery.maxSecsEventWait",
                        maxSecsEventWait);
        logger.log(Level.FINE,
                          " max secs event wait             -- "
                          +maxSecsEventWait);
        /* end harness info */

        /* begin lookup info */
        logger.log(Level.FINE, " ----- Lookup Service Info ----- ");
        implClassname = getConfig().getStringConfigVal
                                 ("net.jini.core.lookup.ServiceRegistrar.impl",
                                  "no implClassname");
        nLookupServices = getConfig().getIntConfigVal
                           ("net.jini.lookup.nLookupServices",
                             nLookupServices);
        nRemoteLookupServices = getConfig().getIntConfigVal
                           ("net.jini.lookup.nRemoteLookupServices",
                             nRemoteLookupServices);
        nAddLookupServices = getConfig().getIntConfigVal
                           ("net.jini.lookup.nAddLookupServices",
                             nAddLookupServices);

        nAddRemoteLookupServices = getConfig().getIntConfigVal
                           ("net.jini.lookup.nAddRemoteLookupServices",
                             nAddRemoteLookupServices);
        if(testType == MANUAL_TEST_REMOTE_COMPONENT) {
            nLookupServices = nRemoteLookupServices;
            nAddLookupServices = nAddRemoteLookupServices;
            nRemoteLookupServices = 0;
            nAddRemoteLookupServices = 0;
        }//endif
        logger.log(Level.FINE,
                          " # of lookup services to start            -- "
                          +nLookupServices);
        logger.log(Level.FINE,
                          " # of additional lookup services to start -- "
                          +nAddLookupServices);

        nSecsLookupDiscovery = getConfig().getIntConfigVal
                      ("net.jini.lookup.nSecsLookupDiscovery",
                        nSecsLookupDiscovery);
        logger.log(Level.FINE,
                          " seconds to wait for discovery            -- "
                          +nSecsLookupDiscovery);

        /* Multicast announcement info - give priority to the command line */
        try {
            int sysInterval = Integer.getInteger
                                 ("net.jini.discovery.announce",0).intValue();
	    originalAnnounceInterval = sysInterval;
            if(sysInterval > 0) {
                announceInterval = sysInterval;
            } else {
                sysInterval = getConfig().getIntConfigVal
                                           ("net.jini.discovery.announce",0);
                if(sysInterval > 0) announceInterval = sysInterval;
            }
            Properties props = System.getProperties();
            props.put("net.jini.discovery.announce",
                       (new Integer(announceInterval)).toString());
            System.setProperties(props);
        } catch (SecurityException e) { }
        logger.log(Level.FINE,
                          " discard if no announcements in (nSecs =) -- "
                          +(announceInterval/1000));
        minNAnnouncements = getConfig().getIntConfigVal
                         ("net.jini.discovery.minNAnnouncements",
                           minNAnnouncements);
        nIntervalsToWait = getConfig().getIntConfigVal
                          ("net.jini.discovery.nIntervalsToWait",
                            nIntervalsToWait);
        /* end lookup info */

        fastTimeout = 
	    getConfig().getIntConfigVal("com.sun.jini.test.share.fastTimeout", 
				 fastTimeout);

        /* begin local/serializable service info */
        nServices = getConfig().getIntConfigVal("net.jini.lookup.nServices",nServices);
        nAddServices = getConfig().getIntConfigVal("net.jini.lookup.nAddServices",
                                           nAddServices);
        if( (nServices+nAddServices) > 0) {
            logger.log(Level.FINE,
                              " ----- General Service Info ----- ");
            logger.log(Level.FINE,
                          " # of initial basic services to register  -- "
                          +nServices);
            logger.log(Level.FINE,
                          " # of additional basic srvcs to register  -- "
                          +nAddServices);

            nAttributes = getConfig().getIntConfigVal("net.jini.lookup.nAttributes",
                                              nAttributes);
            logger.log(Level.FINE,
                          " # of attributes per service              -- "
                          +nAttributes);
            nAddAttributes = getConfig().getIntConfigVal
                                            ("net.jini.lookup.nAddAttributes",
                                             nAddAttributes);
            logger.log(Level.FINE,
                          " # of additional attributes per service   -- "
                          +nAddAttributes);
            nSecsJoin = getConfig().getIntConfigVal
                          ("net.jini.lookup.nSecsJoin",
                            nSecsJoin);
            logger.log(Level.FINE,
                          " # of seconds to wait for service join    -- "
                          +nSecsJoin);
            nSecsServiceDiscovery = getConfig().getIntConfigVal
                          ("net.jini.lookup.nSecsServiceDiscovery",
                            nSecsServiceDiscovery);
            logger.log(Level.FINE,
                          " # of secs to wait for service discovery  -- "
                          +nSecsServiceDiscovery);
        }//endif(nServices+nAddServices > 0)

        /* begin lookup discovery service info */
        nLookupDiscoveryServices = getConfig().getIntConfigVal
                           ("net.jini.discovery.nLookupDiscoveryServices",
                             nLookupDiscoveryServices);
        nRemoteLookupDiscoveryServices = getConfig().getIntConfigVal
                         ("net.jini.discovery.nRemoteLookupDiscoveryServices",
                           nRemoteLookupDiscoveryServices);
        nAddLookupDiscoveryServices = getConfig().getIntConfigVal
                           ("net.jini.discovery.nAddLookupDiscoveryServices",
                             nAddLookupDiscoveryServices);

        nAddRemoteLookupDiscoveryServices = getConfig().getIntConfigVal
                      ("net.jini.discovery.nAddRemoteLookupDiscoveryServices",
                       nAddRemoteLookupDiscoveryServices);
        if(testType == MANUAL_TEST_REMOTE_COMPONENT) {
            nLookupDiscoveryServices = nRemoteLookupDiscoveryServices;
            nAddLookupDiscoveryServices = nAddRemoteLookupDiscoveryServices;
            nRemoteLookupDiscoveryServices = 0;
            nAddRemoteLookupDiscoveryServices = 0;
        }//endif
        int tmpN =   nLookupDiscoveryServices
                   + nAddLookupDiscoveryServices
                   + nRemoteLookupDiscoveryServices
                   + nAddRemoteLookupDiscoveryServices;
        if(tmpN > 0) {
            logger.log(Level.FINE,
                          " ----- Lookup Discovery Service Info ----- ");
            logger.log(Level.FINE,
                          " # of lookup discovery services to start  -- "
                          +nLookupDiscoveryServices);
            logger.log(Level.FINE,
                          " # of additional lookup discovery srvcs   -- "
                          +nAddLookupDiscoveryServices);
        }//endif(tmpN > 0)

        /* begin lease renewal service info */
        nLeaseRenewalServices = getConfig().getIntConfigVal
                           ("net.jini.lease.nLeaseRenewalServices",
                             nLeaseRenewalServices);
        nRemoteLeaseRenewalServices = getConfig().getIntConfigVal
                         ("net.jini.lease.nRemoteLeaseRenewalServices",
                           nRemoteLeaseRenewalServices);
        nAddLeaseRenewalServices = getConfig().getIntConfigVal
                           ("net.jini.lease.nAddLeaseRenewalServices",
                             nAddLeaseRenewalServices);

        nAddRemoteLeaseRenewalServices = getConfig().getIntConfigVal
                      ("net.jini.lease.nAddRemoteLeaseRenewalServices",
                       nAddRemoteLeaseRenewalServices);
        if(testType == MANUAL_TEST_REMOTE_COMPONENT) {
            nLeaseRenewalServices = nRemoteLeaseRenewalServices;
            nAddLeaseRenewalServices = nAddRemoteLeaseRenewalServices;
            nRemoteLeaseRenewalServices = 0;
            nAddRemoteLeaseRenewalServices = 0;
        }//endif
        tmpN =   nLeaseRenewalServices+ nAddLeaseRenewalServices
               + nRemoteLeaseRenewalServices + nAddRemoteLeaseRenewalServices;
        if(tmpN > 0) {
            logger.log(Level.FINE,
                          " ----- Lease Renewal Service Info ----- ");
            logger.log(Level.FINE,
                          " # of lease renewal services to start     -- "
                          +nLeaseRenewalServices);
            logger.log(Level.FINE,
                          " # of additional lease renewal srvcs      -- "
                          +nAddLeaseRenewalServices);
        }//endif(tmpN > 0)

        /* begin event mailbox service info */
        nEventMailboxServices = getConfig().getIntConfigVal
                           ("net.jini.event.nEventMailboxServices",
                             nEventMailboxServices);
        nRemoteEventMailboxServices = getConfig().getIntConfigVal
                         ("net.jini.event.nRemoteEventMailboxServices",
                           nRemoteEventMailboxServices);
        nAddEventMailboxServices = getConfig().getIntConfigVal
                           ("net.jini.event.nAddEventMailboxServices",
                             nAddEventMailboxServices);

        nAddRemoteEventMailboxServices = getConfig().getIntConfigVal
                      ("net.jini.event.nAddRemoteEventMailboxServices",
                       nAddRemoteEventMailboxServices);
        if(testType == MANUAL_TEST_REMOTE_COMPONENT) {
            nEventMailboxServices = nRemoteEventMailboxServices;
            nAddEventMailboxServices = nAddRemoteEventMailboxServices;
            nRemoteEventMailboxServices = 0;
            nAddRemoteEventMailboxServices = 0;
        }//endif
        tmpN =   nEventMailboxServices+ nAddEventMailboxServices
               + nRemoteEventMailboxServices + nAddRemoteEventMailboxServices;
        if(tmpN > 0) {
            logger.log(Level.FINE,
                          " ----- Event Mailbox Service Info ----- ");
            logger.log(Level.FINE,
                          " # of event mailbox services to start     -- "
                          +nEventMailboxServices);
            logger.log(Level.FINE,
                          " # of additional event mailbox srvcs      -- "
                          +nAddEventMailboxServices);
        }//endif(tmpN > 0)

        /* Handle remote/local components of manual tests */
        remoteHost = getConfig().getStringConfigVal("net.jini.lookup.remotehost",
                                            remoteHost);
        switch(testType) {
            case MANUAL_TEST_REMOTE_COMPONENT:
                logger.log(Level.FINE,
                                  " ***** REMOTE COMPONENT OF A MANUAL TEST "
                                  +"(remote host = "+remoteHost+") ***** ");
                break;
            case MANUAL_TEST_LOCAL_COMPONENT:
                logger.log(Level.FINE,
                                  " ***** LOCAL COMPONENT OF A MANUAL TEST "
                                  +"(remote host = "+remoteHost+") ***** ");
                logger.log(Level.FINE,
                              " ----- Remote Lookup Service Info ----- ");
                logger.log(Level.FINE,
                              " # of remote lookup services              -- "
                              +nRemoteLookupServices);
                logger.log(Level.FINE,
                              " # of additional remote lookup services   -- "
                              +nAddRemoteLookupServices);
                logger.log(Level.FINE,
                              " # of remote basic services               -- "
                              +nServices);

                logger.log(Level.FINE,
                       " ----- Remote Lookup Discovery Service Info ----- ");
                logger.log(Level.FINE,
                              " # of remote lookup discovery services    -- "
                              +nRemoteLookupDiscoveryServices);
                logger.log(Level.FINE,
                              " additional remote lookup discovery srvcs -- "
                              +nAddRemoteLookupDiscoveryServices);

                logger.log(Level.FINE,
                       " ----- Remote Lease Renewal Service Info ----- ");
                logger.log(Level.FINE,
                              " # of remote lease renewal services    -- "
                              +nRemoteLeaseRenewalServices);
                logger.log(Level.FINE,
                              " additional remote lease renewal srvcs -- "
                              +nAddRemoteLeaseRenewalServices);

                logger.log(Level.FINE,
                       " ----- Remote Event Mailbox Service Info ----- ");
                logger.log(Level.FINE,
                              " # of remote event mailbox services    -- "
                              +nRemoteEventMailboxServices);
                logger.log(Level.FINE,
                              " additional remote event mailbox srvcs -- "
                              +nAddRemoteEventMailboxServices);
                break;
        }//end switch(testType)
    }//end getSetupInfo

    /** Retrieves and stores the information needed to configure any lookup
     *  services that will be started for the current test run.
     */
    private void getLookupInfo() throws TestException {
        /* Retrieve the member groups & locator of each lookup */
        /* For all cases except MANUAL_TEST_LOCAL_COMPONENT, the number of
         * remote lookups is zero (see getSetupInfo). For that case, we must
         * handle the remote lookup services BEFORE handling the local lookups.
         * This is because the local component may be both starting local
         * lookups, and discovering remote lookups; but the remote component
         * will only be starting the remote lookups, and will therefore have
         * no knowledge of the local lookups. Because the groups and ports are
         * ordered by the index numbers in the config file, if the local info
         * were to be handled first, then the local component of the test
         * would fall out of alignment with the remote component.
         */
        int n0 = 0;
        int n1 = n0 + nRemoteLookupServices;
        for(int i=0;i<n1;i++) {//initial remote lookups
            /* Member groups for remote lookup service i */
            String groupsArg = getConfig().getServiceStringProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "membergroups", i);
            /* Do NOT use unique groups names since clocks on the local
             * and remote sides are not synchronized, and host names are
             * different
             */
            String[] memberGroups = config.parseString(groupsArg,",");
            if(memberGroups == DiscoveryGroupManagement.ALL_GROUPS) {
		logger.log(Level.FINER, "memberGroups = ALL_GROUPS");
		continue;
	    }
            memberGroupsList.add(memberGroups);
            /* Locator for initial remote lookup service i */
            LookupLocator lookupLocator = getRemoteTestLocator(i);
            initLookupsToStart.add
                          (new LocatorGroupsPair(lookupLocator,memberGroups));
        }//end loop
        /* Remote lookups started after initial remote lookups */
        n0 = n1;
        n1 = n0 + nAddRemoteLookupServices;
        for(int i=n0;i<n1;i++) {//additional remote lookups
            /* Member groups for remote lookup service i */
            String groupsArg = getConfig().getServiceStringProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "membergroups", i);
            /* Use NON-unique groups for remote lookups */
            String[] memberGroups = config.parseString(groupsArg,",");
            if(memberGroups == DiscoveryGroupManagement.ALL_GROUPS) continue;
            memberGroupsList.add(memberGroups);
            /* Locator for additional remote lookup service i */
            LookupLocator lookupLocator = getRemoteTestLocator(i);
            addLookupsToStart.add
                          (new LocatorGroupsPair(lookupLocator,memberGroups));
        }//end loop
        /* Handle all lookups to be started locally */
        n0 = n1;
        n1 = n0 + nLookupServices;
        int portBias = n0;
        for(int i=n0;i<n1;i++) {//initial local lookups
            /* Member groups for lookup service i */
            String groupsArg = getConfig().getServiceStringProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "membergroups", i);
            if(testType == AUTOMATIC_LOCAL_TEST) {
                /* Use unique group names to avoid conflict with other tests */
                groupsArg = config.makeGroupsUnique(groupsArg);
            }//endif
            String[] memberGroups = config.parseString(groupsArg,",");
            if(memberGroups == DiscoveryGroupManagement.ALL_GROUPS) {
		logger.log(Level.FINER, "memberGroups = All_Groups");
		continue;
	    }
            memberGroupsList.add(memberGroups);
            /* Locator for initial lookup service i */
            LookupLocator lookupLocator = getTestLocator(i-portBias);
            initLookupsToStart.add
                          (new LocatorGroupsPair(lookupLocator,memberGroups));
        }//end loop
        /* The lookup services to start after the initial lookup services */
        n0 = n1;
        n1 = n0 + nAddLookupServices;
        for(int i=n0;i<n1;i++) {//additional local lookups
            /* Member groups for lookup service i */
            String groupsArg = getConfig().getServiceStringProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "membergroups", i);
            if(testType == AUTOMATIC_LOCAL_TEST) {
                /* Use unique group names to avoid conflict with other tests */
                groupsArg = config.makeGroupsUnique(groupsArg);
            }//endif
            String[] memberGroups = config.parseString(groupsArg,",");
            if(memberGroups == DiscoveryGroupManagement.ALL_GROUPS) continue;
            memberGroupsList.add(memberGroups);
            /* Locator for additional lookup service i */
            LookupLocator lookupLocator = getTestLocator(i-portBias);
            addLookupsToStart.add
                          (new LocatorGroupsPair(lookupLocator,memberGroups));
        }//end loop
        /* Populate the ArrayList allLookupsToStart */
        for(int i=0;i<initLookupsToStart.size();i++) {
            allLookupsToStart.add(initLookupsToStart.get(i));
        }//end loop
        for(int i=0;i<addLookupsToStart.size();i++) {
            allLookupsToStart.add(addLookupsToStart.get(i));
        }//end loop
    }//end getLookupInfo

    /** Method used for debugging. Displays the following information about
     *  the contents of the given list of <code>LocatorGroupsPair</code>
     *  instances: the number of elements, the locator of the associated
     *  lookup service, and the member groups of the associated lookup service.
     */
    private void displayLookupStartInfo(List lookupList) {
        logger.log(Level.FINE, "   # of lookups = "
                                        +lookupList.size());
        for(int i=0;i<lookupList.size();i++) {
            LocatorGroupsPair pair = (LocatorGroupsPair)lookupList.get(i);
            LookupLocator loc    = pair.locator;
            String[]      groups = pair.groups;
            logger.log(Level.FINE,
                              "     locator lookup["+i+"] = "+loc);
            
            GroupsUtil.displayGroupSet(groups,"       group",
                                       Level.FINE);
        }//end loop
    }//end displayLookupStartInfo

    /** Retrieves and stores the information needed to configure any lookup
     *  discovery services that will be started for the current test run.
     */
    private void getLDSInfo() {
        /* Retrieve groups/locators each lookup discovery service should join*/
        int n0 = 0;
        int n1 = n0 + nRemoteLookupDiscoveryServices;
        for(int i=0;i<n1;i++) {//initial remote lookup discovery services
            /* Locators and groups to join */
            String tojoinArg = getConfig().getServiceStringProperty
                                ("net.jini.discovery.LookupDiscoveryService",
                                 "tojoin", i);
            /* Do NOT use unique groups names since clocks on the local
             * and remote sides are not synchronized, and host names are
             * different
             */
            LookupLocator[] locsToJoin = getLocatorsFromToJoinArg(tojoinArg);
            String[] groupsToJoin = getGroupsFromToJoinArg(tojoinArg);
            initLDSToStart.add(new ToJoinPair(locsToJoin,groupsToJoin));
        }//end loop
        /*Remote lookup discovery servicess started after initial remote LDSs*/
        n0 = n1;
        n1 = n0 + nAddRemoteLookupDiscoveryServices;
        for(int i=n0;i<n1;i++) {//additional remote lookup discovery services
            /* Locators and groups to join */
            String tojoinArg = getConfig().getServiceStringProperty
                                ("net.jini.discovery.LookupDiscoveryService",
                                 "tojoin", i);
            /* Use NON-unique groups to join */
            LookupLocator[] locsToJoin = getLocatorsFromToJoinArg(tojoinArg);
            String[] groupsToJoin = getGroupsFromToJoinArg(tojoinArg);
            addLDSToStart.add(new ToJoinPair(locsToJoin,groupsToJoin));
        }//end loop
        /* Handle all lookup discovery services to be started locally */
        n0 = n1;
        n1 = n0 + nLookupDiscoveryServices;
        for(int i=n0;i<n1;i++) {//initial local lookup discovery services
            /* Locators and groups to join */
            String tojoinArg = getConfig().getServiceStringProperty
                                ("net.jini.discovery.LookupDiscoveryService",
                                 "tojoin", i);
            if(testType == AUTOMATIC_LOCAL_TEST) {//use unique group names
                tojoinArg = config.makeGroupsUnique(tojoinArg);
            }//endif
            LookupLocator[] locsToJoin = getLocatorsFromToJoinArg(tojoinArg);
            String[] groupsToJoin = getGroupsFromToJoinArg(tojoinArg);
            initLDSToStart.add(new ToJoinPair(locsToJoin,groupsToJoin));
        }//end loop
        /* The LDSs to start after the initial LDSs */
        n0 = n1;
        n1 = n0 + nAddRemoteLookupDiscoveryServices;
        for(int i=n0;i<n1;i++) {//additional local lookup discovery services
            /* Locators and groups to join */
            String tojoinArg = getConfig().getServiceStringProperty
                                ("net.jini.discovery.LookupDiscoveryService",
                                 "tojoin", i);
            if(testType == AUTOMATIC_LOCAL_TEST) {//use unique group names
                tojoinArg = config.makeGroupsUnique(tojoinArg);
            }//endif
            LookupLocator[] locsToJoin = getLocatorsFromToJoinArg(tojoinArg);
            String[] groupsToJoin = getGroupsFromToJoinArg(tojoinArg);
            addLDSToStart.add(new ToJoinPair(locsToJoin,groupsToJoin));
        }//end loop
        /* Populate the ArrayList allLDSToStart */
        for(int i=0;i<initLDSToStart.size();i++) {
            allLDSToStart.add(initLDSToStart.get(i));
        }//end loop
        for(int i=0;i<addLDSToStart.size();i++) {
            allLDSToStart.add(addLDSToStart.get(i));
        }//end loop
    }//end getLDSInfo

    /** Convenience method that creates an array of Class objects in which
     *  element corresponds to a different service class type that will be
     *  started remotely and/or locally. The array returned by this method
     *  can be used when building templates that will be used to match on
     *  class type.
     */
    protected Class[] getServiceClassArray() {
        List classnamesList = new ArrayList(5);
        List loadedClassList = new ArrayList(expectedServiceList.size());
        if( (nLookupDiscoveryServices+nRemoteLookupDiscoveryServices) > 0) {
            classnamesList.add
                   (new String("net.jini.discovery.LookupDiscoveryService") );
        }//endif
        if( (nLeaseRenewalServices+nRemoteLeaseRenewalServices) > 0) {
            classnamesList.add
                          (new String("net.jini.lease.LeaseRenewalService") );
        }//endif
        if( (nEventMailboxServices+nRemoteEventMailboxServices) > 0) {
            classnamesList.add(new String("net.jini.event.EventMailbox") );
        }//endif
        for(int i=0;i<classnamesList.size();i++) {
            String classname = (String)classnamesList.get(i);
            try {
                loadedClassList.add(Class.forName(classname));
            } catch(ClassNotFoundException e) {
                logger.log(Level.FINE,
                                     " ClassNotFoundException while loading "
                                     +"service class "+classname);
                e.printStackTrace();
            }//end try
        }//end loop
        return ( (Class[])loadedClassList.toArray
                                        (new Class[loadedClassList.size()]) );
    }//end getServiceClassArray

    /** Special-purpose method that should be called by the remote component
     *  of a manual test after that component of the test has started all of
     *  the services for which it was configured. This method is useful
     *  in such a manual test scenario because it is generally desirable for
     *  the remote component to enter into a "wait state" so that the local
     *  component of the test can run and interact with the remote component
     *  without having to worry about the remote component shutting down
     *  unexpectedly.
     */
    protected void waitForKeyboardInput() {
        System.out.println(" start test in separate VM ... ");
        System.out.println(" ***** WHEN DONE, PRESS ANY KEY TO EXIT *****");
        try {
            int c=System.in.read();
        } catch (IOException e) { }
    }//end waitForKeyboardInput

    /** Convenience method that examines the given <code>String</code>
     *  containing a comma-separated list of groups and locators to join,
     *  and returns a <code>String</code> array containing the items that
     *  correspond to the groups to join.
     */
    private String[] getGroupsFromToJoinArg(String tojoinArg) {
        String[] tojoin = config.parseString(tojoinArg,",");
        if(tojoin == null) return DiscoveryGroupManagement.ALL_GROUPS;
        if(tojoin.length == 0) return DiscoveryGroupManagement.NO_GROUPS;
        List tojoinList = new ArrayList(tojoin.length);
        for(int i=0;i<tojoin.length;i++) {
            if( !config.isLocator(tojoin[i]) ) tojoinList.add(tojoin[i]);
        }//end loop
        return ( (String[])tojoinList.toArray(new String[tojoinList.size()]) );
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
        List tojoinList = new ArrayList(tojoin.length);
        for(int i=0;i<tojoin.length;i++) {
            try {
                tojoinList.add(QAConfig.getConstrainedLocator(tojoin[i]));
            } catch(MalformedURLException e) {
                continue;//not a valid locator (must be group), try next one
            }
        }//end loop
        return ( (LookupLocator[])tojoinList.toArray
                                     (new LookupLocator[tojoinList.size()]) );
    }//end getLocatorsFromToJoinArg

}//end class BaseQATest
