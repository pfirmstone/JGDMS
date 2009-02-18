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

package com.sun.jini.test.spec.discoveryservice;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.share.DiscoveryProtocolSimulator;
import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.share.FiddlerAdminUtil;
import com.sun.jini.test.share.GroupsUtil;
import com.sun.jini.test.share.LocatorsUtil;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

import com.sun.jini.qa.harness.QAConfig;

import net.jini.discovery.DiscoveryGroupManagement;

import net.jini.discovery.LookupDiscoveryService;
import net.jini.discovery.LookupDiscoveryRegistration;
import net.jini.discovery.RemoteDiscoveryEvent;
import net.jini.discovery.LookupUnmarshalException;

import net.jini.lookup.DiscoveryAdmin;
import net.jini.admin.Administrable;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistrar;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import java.rmi.MarshalledObject;
import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import java.util.logging.Level;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import com.sun.jini.proxy.BasicProxyTrustVerifier;

/**
 * This class is an abstract class that acts as the base class which
 * most, if not all, tests of the lookup discovery service
 * utility class should extend.
 * 
 * This abstract class contains a static inner class that can be
 * used as a listener to participate in one or both of group discovery
 * or locator discovery on behalf of the tests that sub-class this
 * abstract class.
 * <p>
 * This class provides an implementation of the <code>setup</code> method
 * which performs standard functions related to the initialization of the
 * system state necessary to execute the test.
 *
 * Any test class that extends this class is required to implement the 
 * <code>run</code> method which defines the actual functions that must
 * be executed in order to verify the assertions addressed by that test.
 * 
 * @see com.sun.jini.qa.harness.QAConfig
 * @see com.sun.jini.qa.harness.QATest
 */
abstract public class AbstractBaseTest extends QATest {

    protected boolean debugFlag = false;//resets timeouts for faster completion
    protected boolean displayOn = false;//verbose in waitForDiscovery/Discard

    protected static final int FAST_TIMEOUT = 5;

    protected static final int ACTIVE_DISCARDED      = 0;//discard
    protected static final int COMM_DISCARDED        = 1;//terminate
    protected static final int NO_INTEREST_DISCARDED = 2;//replace groups
    protected static final int PASSIVE_DISCARDED     = 3;//stop announcements

    /** Ordered pair containing a ServiceRegistrar and its member groups */
    protected class RegGroupsPair {
        ServiceRegistrar reg;
        String[]         groups;
        RegGroupsPair(ServiceRegistrar reg, String[] groups) {
            this.reg    = reg;
            this.groups = groups;
        }
    }//end class RegGroupsPair

    /** Data structure containing groups and/or locator to discover */
    protected class DiscoveryStruct {
        String[]      groups;  // ALL_GROUPS or NO_GROUPS ==> empty "slot"
        LookupLocator locator; // null ==> empty "slot"
        RegGroupsPair regGroups;
        DiscoveryStruct(String[]      groups,
                        LookupLocator locator,
                        RegGroupsPair regGroups) {
            this.groups    = groups;
            this.locator   = locator;
            this.regGroups = regGroups;
        }
    }//end class DiscoveryStruct

    /** Tuple type for collecting and returning registrar information from
     *  RemoteDiscoveryEvents. Contains: successfully un-marshalled registrars,
     *  still-marshalled registrars, exceptions corresponding to the 
     *  still-marshalled registrars, and the groups map corresponding to
     *  all registrars.
     */
    protected class RegTuple {
        public ServiceRegistrar[] regs;
        public MarshalledObject[] mRegs;
        public Throwable[]        exceptions;
        public Map                groupsMap;
        RegTuple(ServiceRegistrar[] regs,
                 MarshalledObject[] mRegs,
                 Throwable[]        exceptions,
                 Map                groupsMap)
        {
            this.regs       = regs;
            this.mRegs      = mRegs;
            this.exceptions = exceptions;
            this.groupsMap  = groupsMap;
        }
    }//end class RegTuple

    /** Data structure containing information about each registration */
    protected class RegistrationInfo {
        public String[]        groupsToDiscover;
        public LookupLocator[] locatorsToDiscover;
        public int handback = -1;//equivalent to null handback

        int expectedHandback;

        RegistrationInfo(String[]        groupsToDiscover,
                         LookupLocator[] locatorsToDiscover,
                         int             expectedHandback)
        {
            this.groupsToDiscover   = groupsToDiscover;
            this.locatorsToDiscover = locatorsToDiscover;
            this.expectedHandback   = expectedHandback;
            initState();
        }//end constructor

        /* Modified by the listener as events are received */
        public HashMap discoveredMap = new HashMap(11);
        public HashMap discardedMap  = new HashMap(11);

        /* Reflects current discovery/discard state for this regInfo.
         * Initialized to false. 
         * Set to true in waitForDiscovery() and waitForDiscard() respectively.
         * Should be re-set to false whenever the expected discovery or
         * expected discard events change (ex. because of a call to setGroups)
         */
        public boolean discoveryComplete = false;
        public boolean discardComplete   = false;

        /* holds current configuration for expected discoveries and discards */
        public HashMap expectedDiscoveredMap = new HashMap(11);

        public HashMap expectedActiveDiscardedMap     = new HashMap(11);
        public HashMap expectedCommDiscardedMap       = new HashMap(11);
        public HashMap expectedNoInterestDiscardedMap = new HashMap(11);
        public HashMap expectedPassiveDiscardedMap    = new HashMap(11);

        /** This method resets the state of each flag and set related to 
         *  discovery and discard events received for this regInfo. 
         */
        public void resetAllEventInfo() {
            synchronized(this) {
                discoveryComplete = false;
                discardComplete   = false;
                discoveredMap.clear();
                discardedMap.clear();

                expectedDiscoveredMap.clear();

                expectedActiveDiscardedMap.clear();
                expectedCommDiscardedMap.clear();
                expectedNoInterestDiscardedMap.clear();
                expectedPassiveDiscardedMap.clear();
            }//end sync(this)
        }//end resetAllEventInfo

        /** This method resets the state of each flag and set related to 
         *  only the discovery events received for this regInfo. 
         */
        public void resetDiscoveryEventInfo() {
            synchronized(this) {
                discoveryComplete = false;
                discoveredMap.clear();
                expectedDiscoveredMap.clear();
            }//end sync(this)
        }//end resetDiscoveryEventInfo

        /** This method resets the state of each flag and set related to 
         *  only the discard events received for this regInfo. 
         */
        public void resetDiscardEventInfo() {
            synchronized(this) {
                discardComplete = false;
                discardedMap.clear();

                expectedActiveDiscardedMap.clear();
                expectedCommDiscardedMap.clear();
                expectedNoInterestDiscardedMap.clear();
                expectedPassiveDiscardedMap.clear();
            }//end sync(this)
        }//end resetDiscardEventInfo

        /** This method should be called whenever the groups and/or locators
         *  this regInfo is supposed to discover are changed during a test.
         *  This method examines the new groups and locators to discover
         *  and then re-sets the values of the fields in this class that
         *  related to the expected discovery/discard state of this regInfo.
         */
        void setLookupsToDiscover(String[] groups, LookupLocator[] locators) {
            groupsToDiscover   = groups;
            locatorsToDiscover = locators;
            discoveryComplete = false;
            discardComplete = false;
            expectedNoInterestDiscardedMap.clear();
            expectedPassiveDiscardedMap.clear();
            synchronized(this) {
                /* If a registrar that's already been DISCOVERED belongs to
                 * NONE of the groups to discover, and has a corresponding
                 * locator that equals NONE of the elements of the set of
                 * locators to discover, MOVE that registrar's corresponding
                 * element from the expectedDiscoveredMap into the
                 * expectedNoInterestDiscardedMap and into the
                 * expectedPassiveDiscardedMap. 
                 */
                Set eSet = discoveredMap.entrySet();
	        Iterator iter = eSet.iterator();
                while(iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    ServiceRegistrar srvcReg = (ServiceRegistrar)pair.getKey();
                    String[] srvcRegGroups   = (String[])pair.getValue();
                    boolean inSet = inToDiscoverSet(srvcReg,srvcRegGroups);
                    if( inToDiscoverSet(srvcReg,srvcRegGroups) ) continue;
                    expectedDiscoveredMap.remove(srvcReg);
                    expectedNoInterestDiscardedMap.put(srvcReg,srvcRegGroups);
                    expectedPassiveDiscardedMap.put(srvcReg,srvcRegGroups);
                }//end loop
                /* If one of the registrars that's already been DISCARDED
                 * belongs to any of the groups to discover and/or has a
                 * locator that's an element of the set of locators to
                 * discover, MOVE that registrar's corresponding element from
                 * the discardedMap into the expectedDiscoveredMap. 
                 */
                eSet = discardedMap.entrySet();
	        iter = eSet.iterator();
                while(iter.hasNext()) {
                    Map.Entry pair = (Map.Entry)iter.next();
                    ServiceRegistrar srvcReg = (ServiceRegistrar)pair.getKey();
                    String[] srvcRegGroups   = (String[])pair.getValue();
                    if( !inToDiscoverSet(srvcReg,srvcRegGroups) ) continue;
                    discardedMap.remove(srvcReg);
                    expectedNoInterestDiscardedMap.remove(srvcReg);
                    expectedPassiveDiscardedMap.remove(srvcReg);
                    expectedDiscoveredMap.put(srvcReg,srvcRegGroups);
                }//end loop
            }//end sync(this)
            /* If one of the groups or locs to discover corresponds to one
             * or more of the lookups that were started but have not yet
             * been discovered (that is, the group or loc to discover does not
             * correspond to any element of the discoveredMap), then add
             * those un-discovered lookups to the expectedDiscoveredMap.
             */
            synchronized(lockObject) {
                Iterator iter = genMap.keySet().iterator();
                while(iter.hasNext()) {
                    Object curGen = iter.next();
                    ServiceRegistrar srvcReg = null;
                    LookupLocator srvcRegLoc = null;
                    /* retrieve the locator and member groups of cur lookup */
                    if( curGen instanceof DiscoveryProtocolSimulator ) {
                        DiscoveryProtocolSimulator generator 
                                         = (DiscoveryProtocolSimulator)curGen;
                        srvcReg = generator.getLookupProxy();
                        srvcRegLoc = generator.getLookupLocator();
                    } else {
                        srvcReg = (ServiceRegistrar)curGen;
                        try {
                            srvcRegLoc = QAConfig.getConstrainedLocator(srvcReg.getLocator());
                        } catch(RemoteException e) {
                            e.printStackTrace();
                            continue;
                        }
                    }//endif
                    String[] srvcRegGroups = (String[])genMap.get(curGen);
                    if(groupsToDiscover==DiscoveryGroupManagement.ALL_GROUPS){
                        expectedDiscoveredMap.put(srvcReg,srvcRegGroups);
                    } else {//(groupsToDiscover != ALL_GROUPS
                        boolean moveToExpected = false;
                        iLoop:
                        for(int i=0;i<srvcRegGroups.length;i++) {
                            for(int j=0;j<groupsToDiscover.length;j++) {
                                if( srvcRegGroups[i].equals
                                                      (groupsToDiscover[j]) )
                                {
                                    moveToExpected = true;
                                    break iLoop;
                                }//endif
                            }//end loop(j)
                        }//end loop(i)
                        if(!moveToExpected) {//no groups, try loc
                            for(int i=0;i<locatorsToDiscover.length;i++) {
                                if( locsEqual
                                          (srvcRegLoc,locatorsToDiscover[i]) )
                                {
                                    moveToExpected = true;
                                    break;
                                }//endif
                            }//end loop
                        }//endif(!moveToExpected)
                        if(!moveToExpected) continue;
                        expectedDiscoveredMap.put(srvcReg,srvcRegGroups);
                    }//endif(groupsToDiscover == ALL_GROUPS)
                }//end loop
            }//end sync(lockObject)
        }//end setLookupsToDiscover

        /** Convenience method that determines whether the given registrar
         *  belongs to at least one group this regInfo wants to discover,
         *  or has a locator this regInfo wants to discover.
         */
        private boolean inToDiscoverSet(ServiceRegistrar srvcReg,
                                        String[] srvcRegGroups)
        {
            if(groupsToDiscover == DiscoveryGroupManagement.ALL_GROUPS) {
                return true;
            }//endif
            boolean inSet = false;
            iLoop:
            for(int i=0;i<srvcRegGroups.length;i++) {
                for(int j=0;j<groupsToDiscover.length;j++) {
                    if( srvcRegGroups[i].equals(groupsToDiscover[j]) ){
                        inSet = true;
                        break iLoop;
                    }//endif
                }//end loop(j)
            }//end loop(i)
            if(!inSet) {//no groups, try loc
                try {
                    LookupLocator srvcRegLoc = QAConfig.getConstrainedLocator(srvcReg.getLocator());
                    for(int i=0;i<locatorsToDiscover.length;i++) {
                        if( locsEqual(srvcRegLoc,locatorsToDiscover[i]) ) {
                            inSet = true;
                            break;
                        }//endif
                    }//end loop
                } catch(RemoteException e) {
                    e.printStackTrace();
                    return false;
                }
            }//endif(!inSet)
            return inSet;
        }//end inToDiscoverSet

        /** Initializes the expected discovery/discard state of this regInfo.
         *  Whenever a new regInfo is created, this method is called and the
         *  fields of this class related to the discovery and discard events
         *  that would be expected based on the initial test configuration
         *  are populated.
         */
        private void initState() {
            synchronized(lockObject) {
                Iterator iter = genMap.keySet().iterator();
                for(int i=0;iter.hasNext();i++) {
                    Object curGen = iter.next();
                    ServiceRegistrar lookupProxy = null;
                    LookupLocator genLoc = null;
                    if( curGen instanceof DiscoveryProtocolSimulator ) {
                        DiscoveryProtocolSimulator generator 
                                         = (DiscoveryProtocolSimulator)curGen;
                        lookupProxy = generator.getLookupProxy();
                        genLoc = generator.getLookupLocator();
                    } else {
                        lookupProxy = (ServiceRegistrar)curGen;
                        try {
                            genLoc = QAConfig.getConstrainedLocator(lookupProxy.getLocator());
                        } catch(RemoteException e) {
                            e.printStackTrace();
                        }
                    }//endif
                    String[] genGroups = (String[])genMap.get(curGen);
                    /* is genLoc in the set of locators to discover? */
                    boolean discoverLoc = false;
                    if(locatorsToDiscover != null) {
                        for(int j=0;j<locatorsToDiscover.length;j++) {
                            if( locsEqual(genLoc,locatorsToDiscover[j]) ) {
                                discoverLoc = true;
                                break;
                            }//endif
                        }//end loop
                    }//endif
                    boolean discoverGroups = false;
                    /* are any of the generator's member groups in the
                     * set of groups to discover for this registration?
                     */
                    if(groupsToDiscover==DiscoveryGroupManagement.ALL_GROUPS) {
                        discoverGroups = true;//even if lookup is NO_GROUPS
                    } else { // (groupsToDiscover != ALL_GROUPS)
                        jLoop:
                        for(int j=0;j<groupsToDiscover.length;j++) {
                            for(int k=0;k<genGroups.length;k++) {
                                if((genGroups[k]).equals(groupsToDiscover[j])){
                                    discoverGroups = true;
                                    break jLoop;
                                }//endif
                            }//end loop(j)
                        }//end loop(i)
                    }//endif
                    /* populate the expected discoveries and discards */
                    if(discoverLoc || discoverGroups) {
                        expectedDiscoveredMap.put(lookupProxy,genGroups);
                    }//endif
                    /* active - for discard, group or loc change requests,  */
                    if(discoverLoc || discoverGroups) {
                        expectedActiveDiscardedMap.put(lookupProxy,genGroups);
                    }//endif
                    /* passive - for announcement termination */
                    if(discoverGroups) {
                        expectedCommDiscardedMap.put(lookupProxy,genGroups);
                    }//endif
                    /* passive - for announcement stop, groups change */
                    if(!discoverLoc && discoverGroups) {
                        expectedNoInterestDiscardedMap.put(lookupProxy,
                                                           genGroups);
                        expectedPassiveDiscardedMap.put(lookupProxy,
                                                        genGroups);
                    }//endif
                }//end loop(iter)
            }//end sync
        }//end initState
    }//end class RegistrationInfo

    /** Remote event listener class used to monitor the discovery events
     *  sent from the lookup discovery service that, on behalf of the test,
     *  participates in group and/or locator discovery. Note that in
     *  most cases, the test cannot proceed until at least one discovery
     *  event containing at least one reference to a ServiceRegistrar
     *  is received. This class provides information that allows the test
     *  to determine whether or not to proceed.
     */
    public class LDSEventListener
	implements RemoteEventListener, ServerProxyTrust, Serializable
    {

        private RegistrationInfo regInfo;
        private Object proxy;
	private ProxyPreparer registrarPreparer;
        public LDSEventListener(RegistrationInfo regInfo)
	    throws RemoteException
        {
            super();
            this.regInfo = regInfo;
	    Configuration c = getConfig().getConfiguration();
	    Exporter exporter = QAConfig.getDefaultExporter();
	    registrarPreparer = new BasicProxyPreparer();
	    if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
		try {
		    exporter = (Exporter) c.getEntry("test",
						     "fiddlerListenerExporter",
						     Exporter.class);
		    registrarPreparer = 
			(ProxyPreparer) c.getEntry("test",
						   "reggiePreparer",
						   ProxyPreparer.class);
		} catch (ConfigurationException e) {
		    throw new RemoteException("Configuration error", e);
		}
	    }
	    proxy = exporter.export(this);
        }//end constructor

	public Object writeReplace() throws ObjectStreamException {
	    return proxy;
	}

	public TrustVerifier getProxyVerifier() {
	    return new BasicProxyTrustVerifier(proxy);
	}

        /** Method called remotely by lookup to handle the generated event. */
        public void notify(RemoteEvent event) {
            RemoteDiscoveryEvent evnt = (RemoteDiscoveryEvent)event;
            setRegInfoHandback(evnt);
            if(evnt.isDiscarded()) {
                discarded(evnt);
            } else {
                discovered(evnt);
            }
        }//end notify

        public RegistrationInfo getRegInfo() {
            return regInfo;
        }//end getRegInfo

        private void discovered(RemoteDiscoveryEvent evnt) {
            RegTuple regTuple = getRegistrars(evnt);
            ServiceRegistrar[] regs = regTuple.regs;
            Map groupsMap = regTuple.groupsMap;
            if(regs != null) {
                synchronized(regInfo) {
                    boolean oneDiscovered = false;
                    for(int i=0;i<regs.length;i++) {
                        /* care only about the generators the test started */
                        if( !lookupList.contains(regs[i]) ) continue;
                        ServiceID srvcID = regs[i].getServiceID();
                        logger.log(Level.FINE, "registration_"+regInfo.handback
                                      +" -- discovered service ID = "+srvcID);
                        logDebugEventText(evnt);
                        try {
                            String[] groups = (String[])groupsMap.get(srvcID);
                            regInfo.discoveredMap.put(regs[i],groups);
                            LocatorsUtil.displayLocator(QAConfig.getConstrainedLocator(regs[i].getLocator()),
                                                        "  locator ",
                                                        Level.FINE);
                            String displayGroups 
                                      = GroupsUtil.toCommaSeparatedStr(groups);
                            if(displayGroups.equals("")) {
                                displayGroups = new String("NO_GROUPS");
                            }//endif
                            logger.log(Level.FINE, "  group(s) = "
                                              +displayGroups);
                        } catch(Exception e) { e.printStackTrace(); }
                        oneDiscovered = true;
                    }//end loop
                    if(oneDiscovered) {
                        logger.log(Level.FINE, "# of currently discovered "
                                          +"lookups for registration_"
                                          +regInfo.handback+" = "
                                          +regInfo.discoveredMap.size());
                    }
                }//end sync(regInfo)
            }//endif
        }//end discovered

        private void discarded(RemoteDiscoveryEvent evnt) {
            logger.log(Level.FINE, "got \"discarded\" event");
            RegTuple regTuple = getRegistrars(evnt);
            ServiceRegistrar[] regs = regTuple.regs;
            Map groupsMap = regTuple.groupsMap;
            if(regs != null) {
                synchronized(regInfo) {
                    for(int i=0;i<regs.length;i++) {
                        ServiceID srvcID = regs[i].getServiceID();
                        if(regInfo.discoveredMap.remove(regs[i]) != null) {
                            logger.log(Level.FINE, "registration_"+regInfo.handback
                                      +" -- discarded service ID = "+srvcID);
                            logDebugEventText(evnt);
                            String[] groups = (String[])groupsMap.get(srvcID);
                            regInfo.discardedMap.put(regs[i],groups);
                            String displayGroups 
                                      = GroupsUtil.toCommaSeparatedStr(groups);
                            if(displayGroups.equals("")) {
                                displayGroups = new String("NO_GROUPS");
                            }//endif
                            logger.log(Level.FINE, "discarded group(s) = "
                                              +displayGroups);
                        }//endif
                    }//end loop
                    logger.log(Level.FINE, "# of currently discovered "
                                          +"lookups for registration_"
                                          +regInfo.handback+" = "
                                          +regInfo.discoveredMap.size());
                }//end sync(regInfo)
            }//endif
        }//end discarded

        private RegTuple getRegistrars(RemoteDiscoveryEvent evnt) {
            if(evnt == null) {
                logger.log(Level.FINE, "WARNING -- event input to "
                                                +"getRegistrars - is null");
                return null;
            }
            ServiceRegistrar[] regs  = new ServiceRegistrar[0];
            MarshalledObject[] mRegs = new MarshalledObject[0];
            Throwable[] exceptions   = new Throwable[0];
            Map groupsMap = evnt.getGroups();
            try {
                regs = evnt.getRegistrars();
            } catch (LookupUnmarshalException e) {
                logger.log(Level.FINE, "LookupUnmarshalException --");
                regs       = e.getRegistrars();
                mRegs      = e.getMarshalledRegistrars();
                exceptions = e.getExceptions();
                logger.log(Level.FINE, "  registrars that were "
                                            +"successfully un-marshalled --");
                if(regs.length > 0) {
                    for(int i=0;i<regs.length;i++) {
                        try {
                            ServiceID srvcID = regs[i].getServiceID();
                            String[] groups = (String[])groupsMap.get(srvcID);
                            LocatorsUtil.displayLocator(QAConfig.getConstrainedLocator(regs[i].getLocator()),
                                                        "    locator ",
                                                        Level.FINE);
                            String displayGroups 
                                      = GroupsUtil.toCommaSeparatedStr(groups);
                            if(displayGroups.equals("")) {
                                displayGroups = new String("NO_GROUPS");
                            }//endif
                            logger.log(Level.FINE, "    group(s) = "
                                              +displayGroups);
                        } catch(Exception e1) { e1.printStackTrace(); }
                    }//end loop
                } else {
                    logger.log(Level.FINE, "  NO successfully "
                                      +"un-marshalled registrars");
                }//end if
                logger.log(Level.FINE, "  registrars that could "
                                            +"not be un-marshalled --");
                if(mRegs.length > 0) {
                    for(int i=0;i<mRegs.length;i++) {
                        logger.log(Level.FINE, "    mRegs["+i+"] = "+mRegs[i]);
                    }//end loop
                } else {
                    logger.log(Level.FINE, "  no still-marshalled registrars");
                }//end if
                logger.log(Level.FINE, "  un-marshalling exceptions --");
                if(exceptions.length > 0) {
                    for(int i=0;i<exceptions.length;i++) {
                        logger.log(Level.FINE, "    exceptions["
                                                      +i+"] = "+exceptions[i]);
                    }//end loop
                } else {
                    logger.log(Level.FINE, "  NO exceptions while "
                                                 +"un-marshalling registrars");
                }//endif
            }//end try (getRegistrars)
	    for (int i = 0; i < regs.length; i++) {
		logger.log(Level.FINEST, "preparing registrar " + regs[i]);
		try {
		    regs[i] = (ServiceRegistrar) 
			      registrarPreparer.prepareProxy(regs[i]);
		} catch (RemoteException e) {
		    logger.log(Level.SEVERE, "Exception preparing proxy", e);
		}
	    }
            return (new RegTuple(regs,mRegs,exceptions,groupsMap));
        }//end getRegistrars

        private void setRegInfoHandback(RemoteDiscoveryEvent evnt) {
            int iHandback = -1;//for null handbacks
            MarshalledObject mHandback = evnt.getRegistrationObject();
            if(mHandback != null) {
                try {
                    Integer handback = (Integer)mHandback.get();
                    iHandback = handback.intValue();
                } catch (ClassNotFoundException e) {
                    logger.log(Level.FINE, "WARNING - failure while "
                                +"un-marshalling event handback information "
                                +"(ClassNotFoundException)");
                    e.printStackTrace();
                } catch (IOException e) {
                    logger.log(Level.FINE, "WARNING - failure while "
                                +"un-marshalling event handback information "
                                +"(IOException)");
                    e.printStackTrace();
                } catch (Exception e) {
                    logger.log(Level.FINE, "WARNING - failure while "
                                +"un-marshalling event handback information");
                    e.printStackTrace();
                }
            }//endif(mHandback != null)
            synchronized(regInfo) {
                regInfo.handback = iHandback;
            }//end sync
        }//end setRegInfoHandback

        private void logDebugEventText(RemoteDiscoveryEvent evnt) {
            logger.log(Level.FINE, "  eventID  = "+evnt.getID());
            logger.log(Level.FINE, "  seqNum   = "
                                            +evnt.getSequenceNumber() );
            logger.log(Level.FINE, "  handback = "
                                            +regInfo.handback);
        }//end logDebugEventText

    }//end class LDSEventListener

    /** Thread in which a number of lookup services are started after various
     *  time delays. This thread is intended to be used by tests that need to
     *  simulate "late joiner" lookup services. After all of the requested
     *  lookup services have been started, this thread will exit.
     */
    protected class StaggeredStartThread extends Thread {
        private final long[] defaultWaitTimes = { 1*1000 };
        private long[] waitTimes;
        /** Use this constructor if it is desired that the lookup service(s)
         *  be started at the default wait times.
         */
        public StaggeredStartThread() {
            this(null);
        }//end constructor

        /** Use this constructor if it is desired that the lookup service(s)
         *  be started at the given wait times.
         */
         public StaggeredStartThread(long[] waitTimes) {
            super("StaggeredStartThread");
            setDaemon(true);
            this.waitTimes = ((waitTimes==null) ? defaultWaitTimes:waitTimes);
        }//end constructor

        public void run() {
            int n = waitTimes.length;
            int totalNLookups = nLookupServices+nAddLookupServices;
            for(int i=nLookupServices;i<totalNLookups;i++) {
                long waitMS = ( i < n ? waitTimes[i-1] : waitTimes[n-1] );
                logger.log(Level.FINE, "waiting "+(waitMS/1000)+" seconds before "
                              +"attempting to start the next lookup service");
                try {Thread.sleep(waitMS);} catch(InterruptedException e) { }
		LookupLocator l = (LookupLocator) lookupsToStart.get(i);
                int port = 
		    ((lookupsToStart.size() == totalNLookups ) ? l.getPort() 
		                                               : 0);
                try {
                    startLookup(i, port, l.getHost());
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }//end loop
        }//end run
    }//end class StaggeredStartThread

    protected String[] subCategories;

    protected String implClassname;    //implementation class of lookup

    protected static final String serviceName
                                = "net.jini.discovery.LookupDiscoveryService";
    protected LookupDiscoveryService discoverySrvc = null;

    protected int maxSecsEventWait  = 10 * 60;
    protected int announceInterval  = 2 * 60 * 1000;
    protected int minNAnnouncements = 2;
    protected int nIntervalsToWait  = 3;

    protected boolean startAllLookupsInSetup = true;

    protected int nLookupServices       = 0;
    protected int nAddLookupServices    = 0;
    protected int nRegistrations        = 0;
    protected int nAddRegistrations     = 0;
    protected int nSecsLookupDiscovery  = 30;

    private   ArrayList memberGroupsList = new ArrayList(11);
    protected ArrayList lookupsToStart   = new ArrayList(5);
    private   ArrayList locatorsStarted  = new ArrayList(5);

    protected ArrayList lookupList = new ArrayList(1);
    protected HashMap genMap = new HashMap(1);
    protected int nStarted = 0;

    protected int discardType = ACTIVE_DISCARDED;

    protected ArrayList useDiscoveryList = new ArrayList();

    protected ArrayList useGroupAndLocDiscovery0 = new ArrayList(11);
    protected ArrayList useGroupAndLocDiscovery1 = new ArrayList(11);
    protected ArrayList useOnlyGroupDiscovery    = new ArrayList(11);
    protected ArrayList useOnlyLocDiscovery      = new ArrayList(11);

    protected long leaseDuration = Integer.MAX_VALUE;
    protected HashMap registrationMap = new HashMap(11);

    protected boolean announcementsStopped = false;

    protected Object discoveryLock = new Object();

    private Object lockObject = new Object();

    /** Constructs an instance of this class. Initializes this classname */
    public AbstractBaseTest() {
    }//end constructor

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p>
     *   <ul>
     *     <li> retrieves configuration values needed by the current test
     *     <li> sets the net.jini.discovery.announce system property
     *     <li> retrieves the set of member groups with which to configure
     *          each announcement generator 
     *     <li> constructs the union of all member group sets for configuring
     *          the lookup discovery service utility
     *     <li> constructs the union of all locators-of-interest for
     *          configuring the LookupLocatorDiscovery utility
     *     <li> creates an instance of lookup discovery service to start
     *          both group and locator discovery
     *     <li> starts the configured number of lookup services
     *   </ul>
     */
    public void setup(QAConfig config) throws Exception {
	super.setup(config);
	logger.entering("","setup");
	getSetupInfo();
	getLookupInfo();
	if(startAllLookupsInSetup) {//Start lookup service(s)
	    startAllLookups();
	}//endif
	/* Start monitoring process by creating a LookupDiscoveryService */
	logger.log(Level.FINEST,"starting a lookup discovery "
		   +"service configured to join NO_GROUPS and NO_LOCATORS");
	// returned proxy is already prepared
	discoverySrvc = 
	    (LookupDiscoveryService)(manager.startService(serviceName));
    }//end setup

    /** Executes the current test
     */
    abstract public void run() throws Exception;

    /** Cleans up all state. Terminates the lookup discovery service utility,
     *  and each multicast announcement generator (along with corresponding
     *  lookup service simulation) that were created during the test; and then
     *  performs any standard clean up duties performed in the super class.
     */
    public void tearDown() {
        try {
            if(genMap.size() > 0) {
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
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            super.tearDown();
	}
    }//end tearDown

    /** Method that compares the given port to the ports of all the locators
     *  referenced in the set of elements that correspond to lookup services
     *  that have been started. Returns <code>true</code> if the given port
     *  equals any of the ports referenced in the set; <code>false</code>
     *  otherwise. Useful for guaranteeing unique port numbers when starting
     *  lookup serives.
     */
    protected boolean portInUse(int port) {
        for(int i=0;i<locatorsStarted.size();i++) {
            int curPort = ((LookupLocator)(locatorsStarted.get(i))).getPort();
            if(port == curPort) return true;
        }//end loop
        return false;
    }//end portInUse

    /** Convenience method that can be used to start, at a single point 
     *  during the current test run, all of the lookup services needed by
     *  that test run. Useful when all of the lookup services are to be
     *  started during setup processing.
     */
    protected void startAllLookups() throws Exception {
        int totalNLookups = nLookupServices+nAddLookupServices;
        for(int i=0;i<totalNLookups;i++) {
	    LookupLocator l = (LookupLocator) lookupsToStart.get(i);
            int port = l.getPort();
            if(portInUse(port) || (lookupsToStart.size() != totalNLookups)){
                port = 0;
            }
            startLookup(i, port, l.getHost());
        }//end loop
        /* If explicit ports were NOT set in config, then use what was started,
         * no questions asked. If explicit ports were set in config, then
         * verify that what was started equals what was wanted.
         */
        if(lookupsToStart.size() > 0) {//explicit ports were set in config
            if(!collectionsEqual(lookupsToStart,locatorsStarted)) {
                throw new TestException("locators started != locators "
                                          +"wanted");

            }//endif
        }//endif
        nStarted = genMap.size();
    }//end startAllLookups

    /** Convenience method that can be used to start, at any point during the
     *  current test run, a single lookup service with configuration 
     *  referenced by the given parameter values. Useful when individual
     *  lookup services are to be started at different points in time during
     *  the test run.
     */
    protected void startLookup(int indx, int port, String serviceHost) 
	throws Exception 
    {
        logger.log(Level.FINE, "starting lookup service "+indx);
        /* retrieve the member groups with which to configure the lookup */
        String[] memberGroups = (String[])memberGroupsList.get(indx);
        ServiceRegistrar lookupProxy = null;
        if(implClassname.equals("com.sun.jini.test.services.lookupsimulator.LookupSimulatorImpl"))
        {
            /* Use either a random or an explicit locator port */
            DiscoveryProtocolSimulator generator = 
		new DiscoveryProtocolSimulator(config, memberGroups, manager, port);
            genMap.put( generator, memberGroups );
            lookupProxy = generator.getLookupProxy();
        } else {//start a non-simulated lookup service implementation
	    logger.log(Level.FINER, "Starting lookup for host " + serviceHost);
            lookupProxy = manager.startLookupService(serviceHost); // already prepared 
            genMap.put( lookupProxy, memberGroups );
        }//endif
        lookupList.add( lookupProxy );
        LookupLocator lookupLocator = QAConfig.getConstrainedLocator(lookupProxy.getLocator());
        locatorsStarted.add(lookupLocator);
        LocatorsUtil.displayLocator(lookupLocator,
                                    "  locator ",Level.FINE);
        String displayGroups = GroupsUtil.toCommaSeparatedStr(memberGroups);
        if(displayGroups.equals("")) displayGroups = new String("NO_GROUPS");
        logger.log(Level.FINE, "  group(s) = "
                                        +displayGroups);
        /* Because the lookup discovery service can perform both group and
         * locator discovery, some tests will focus only on group 
         * discovery, some only on locator discovery, and some on both
         * group and locator discovery. To provide these tests with
         * a variety of options for the groups and/or locators to discover,
         * a number of data structures are populated below which the
         * tests can use to configure the lookup discovery service for
         * discovery. In some cases, only the member groups from above
         * are stored, in other cases only the locators are stored, and
         * in still other cases, a mix of groups and locators are stored.
         * The case where the groups and locators are mixed is intended
         * to provide tests that verify the lookup discovery service's 
         * ability to handle both group and locator discovery with a
         * set that can be used to configure the lookup discovery service
         * to discover some registrars through only group discovery,
         * some registrars through only locator discovery, and some
         * registrars through both group and locator discovery.
         */
        if( (indx%3) == 0 ) {// add both groups and locs
            useGroupAndLocDiscovery0.add
            (new DiscoveryStruct(memberGroups,lookupLocator,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
            useGroupAndLocDiscovery1.add
            (new DiscoveryStruct(memberGroups,null,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
        } else if( (indx%3) == 1 ) {// add only the groups
            useGroupAndLocDiscovery0.add
            (new DiscoveryStruct(memberGroups,null,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
            useGroupAndLocDiscovery1.add(new DiscoveryStruct
                                (memberGroups,null,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
        } else if( (indx%3) == 2 ) {// add only the locators
            useGroupAndLocDiscovery0.add
            (new DiscoveryStruct(null,lookupLocator,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
            useGroupAndLocDiscovery1.add(new DiscoveryStruct
                                (memberGroups,lookupLocator,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
        }//endif
        useOnlyGroupDiscovery.add
            (new DiscoveryStruct(memberGroups,null,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
        useOnlyLocDiscovery.add
            (new DiscoveryStruct(null,lookupLocator,
                                 new RegGroupsPair(lookupProxy,memberGroups)));
    }//end startLookup

    /** Returns <code>true</code> if the given collections are referentially
     *  equal, or if the given collections have the same contents;
     *  <code>false</code> otherwise.
     */
    protected boolean collectionsEqual(Collection c0, Collection c1) {
        if(c0 == c1) return true;//if both are null it returns true
        if( (c0 == null) || (c1 == null) ) return false;
        if(c0.size() != c1.size()) return false;
	Iterator iter = c0.iterator();
        while(iter.hasNext()) {
            if( !(c1.contains(iter.next())) ) return false;
        }//endif
        return true;
    }//end collectionsEqual

    /** Based on the ArrayList input, this method will extract and return a
     *  String[] array containing the groups that should be used to configure
     *  the lookup discovery service for group discovery.
     */
    protected String[] getGroupsToDiscover(ArrayList useList) {
        ArrayList groupsList = new ArrayList();
        for(int i=0;i<useList.size();i++) {
            String[] groupsArray = ((DiscoveryStruct)(useList.get(i))).groups;
            if( (groupsArray == null) || (groupsArray.length == 0) ) continue;
            for(int j=0;j<groupsArray.length;j++) {
                groupsList.add(groupsArray[j]);
            }
        }
        return (String[])(groupsList).toArray(new String[groupsList.size()]);
    }//end getGroupsToDiscover

    /** Based on the ArrayList input, this method will extract and return a
     *  LookupLocator[] array containing the locators that should be used
     *  to configure the lookup discovery service for locator discovery.
     */
    protected LookupLocator[] getLocatorsToDiscover(ArrayList useList) {
        ArrayList locsList = new ArrayList();
        for(int i=0;i<useList.size();i++) {
            LookupLocator loc = ((DiscoveryStruct)(useList.get(i))).locator;
            if(loc == null)  continue;
            locsList.add(loc);
        }
        return (LookupLocator[])(locsList).toArray
                                          (new LookupLocator[locsList.size()]);
    }//end getLocatorsToDiscover

    /** RegInfo index should always be the handback value for the regInfo */
    protected String[] getGroupsToDiscoverByIndex(int regIndex) {
        if( (regIndex%4) == 0 ) {
            return getGroupsToDiscover(useGroupAndLocDiscovery0);
        } else if( (regIndex%4) == 1 ) {
            return getGroupsToDiscover(useOnlyGroupDiscovery);
        } else if( (regIndex%4) == 2 ) {
            return getGroupsToDiscover(useGroupAndLocDiscovery1);
        } else if( (regIndex%4) == 3 ) {
            return getGroupsToDiscover(useOnlyLocDiscovery);
        } else {
            return getGroupsToDiscover(useGroupAndLocDiscovery0);
        }//endif
    }//end getGroupsToDiscoverByIndex

    /** RegInfo index should always be the handback value for the regInfo */
    protected ArrayList getGroupListToUseByIndex(int regIndex) {
        if( (regIndex%4) == 0 ) {
            return useGroupAndLocDiscovery0;
        } else if( (regIndex%4) == 1 ) {
            return useOnlyGroupDiscovery;
        } else if( (regIndex%4) == 2 ) {
            return useGroupAndLocDiscovery1;
        } else if( (regIndex%4) == 3 ) {
            return useOnlyLocDiscovery;
        } else {
            return useGroupAndLocDiscovery0;
        }//endif
    }//end getGroupListToUseByIndex

    /** RegInfo index should always be the handback value for the regInfo */
    protected LookupLocator[] getLocatorsToDiscoverByIndex(int regIndex) {
        if( (regIndex%4) == 0 ) {
            return getLocatorsToDiscover(useGroupAndLocDiscovery0);
        } else if( (regIndex%4) == 1 ) {
            return getLocatorsToDiscover(useOnlyGroupDiscovery);
        } else if( (regIndex%4) == 2 ) {
            return getLocatorsToDiscover(useGroupAndLocDiscovery1);
        } else if( (regIndex%4) == 3 ) {
            return getLocatorsToDiscover(useOnlyLocDiscovery);
        } else {
            return getLocatorsToDiscover(useGroupAndLocDiscovery0);
        }//endif
    }//end getLocatorsToDiscoverByIndex

    /** RegInfo index should always be the handback value for the regInfo */
    protected ArrayList getLocatorListToUseByIndex(int regIndex) {
        if( (regIndex%4) == 0 ) {
            return useGroupAndLocDiscovery0;
        } else if( (regIndex%4) == 1 ) {
            return useOnlyGroupDiscovery;
        } else if( (regIndex%4) == 2 ) {
            return useGroupAndLocDiscovery1;
        } else if( (regIndex%4) == 3 ) {
            return useOnlyLocDiscovery;
        } else {
            return useGroupAndLocDiscovery0;
        }//endif
    }//end getLocatorsToDiscoverByIndex

    /** Return any elements having at least a groups entry */
    protected Map getPassiveCommDiscardMap(ArrayList useList) {
        HashMap discardMap = new HashMap();
        for(int i=0;i<useList.size();i++) {
            String[] groupsArray = ((DiscoveryStruct)(useList.get(i))).groups;
            LookupLocator loc = ((DiscoveryStruct)(useList.get(i))).locator;
            if((groupsArray!=null) && (groupsArray.length>0)) {
                RegGroupsPair pair = 
                                ((DiscoveryStruct)(useList.get(i))).regGroups;
                discardMap.put(pair.reg,pair.groups);
            }
        }
        return discardMap;
    }//end getPassiveCommDiscardMap

    /** Return any elements having either a groups entry or a locators entry */
    protected Map getActiveCommDiscardMap(ArrayList useList) {
        HashMap discardMap = new HashMap();
        for(int i=0;i<useList.size();i++) {
            String[] groupsArray = ((DiscoveryStruct)(useList.get(i))).groups;
            LookupLocator loc = ((DiscoveryStruct)(useList.get(i))).locator;
            if(    ( (groupsArray != null) && (groupsArray.length > 0) )
                || (loc != null) )
            {
                RegGroupsPair pair = 
                                ((DiscoveryStruct)(useList.get(i))).regGroups;
                discardMap.put(pair.reg,pair.groups);
            }
        }
        return discardMap;
    }//end getActiveCommDiscardMap

    /** Return any elements having at least a locators entry */
    protected Map getModLocatorsDiscardMap(ArrayList useList) {
        HashMap discardMap = new HashMap();
        for(int i=0;i<useList.size();i++) {
            String[] groupsArray = ((DiscoveryStruct)(useList.get(i))).groups;
            LookupLocator loc = ((DiscoveryStruct)(useList.get(i))).locator;
            if( loc != null ) {
                RegGroupsPair pair = 
                                ((DiscoveryStruct)(useList.get(i))).regGroups;
                discardMap.put(pair.reg,pair.groups);
            }
        }
        return discardMap;
    }//end getModLocatorsDiscardMap

    /** Common code, shared by this class and its sub-classes, that is 
     *  invoked by the run() method. This method creates a registration --
     *  associated with the given handback parameter -- with the lookup
     *  discovery service, requesting a lease with the given lease duration,
     *  and requesting that the given groups and/or locators be discovered.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void doRegistration(String[] groupsToDiscover,
                                  LookupLocator[] locatorsToDiscover,
                                  int handback,
                                  long leaseDuration) throws RemoteException
    {
        /* Register with the lookup discovery service */
        if( groupsToDiscover == DiscoveryGroupManagement.ALL_GROUPS ) {
            logger.log(Level.FINE, "   ALL_GROUPS");
        } else if( groupsToDiscover.length <= 0 ) {
            logger.log(Level.FINE, "   NO_GROUPS");
        } else {
            GroupsUtil.displayGroupSet(groupsToDiscover,
                                       "   group",Level.FINE);
        }//endif
        if(locatorsToDiscover.length <= 0) {
            logger.log(Level.FINE, "   NO_LOCATORS");
        } else {
            for(int i=0;i<locatorsToDiscover.length;i++) {
                logger.log(Level.FINE, "   locator["+i+"] = "
                                                +locatorsToDiscover[i]);
            }
        }//endif
        MarshalledObject mHandback = null;
        int iHandback = -1;
        try {
            if(handback >= 0) {//negative handback param ==> null mHandback
                iHandback = handback;
                mHandback = new MarshalledObject(new Integer(handback));
            }//endif
        } catch (IOException e) {
            logger.log(Level.FINE, "WARNING -- failure on handback construction "
                              +"(handback = "+handback+")");
            e.printStackTrace();
        }
        RegistrationInfo regInfo = new RegistrationInfo(groupsToDiscover,
                                                        locatorsToDiscover,
                                                        iHandback);
        LDSEventListener listener =
                               new AbstractBaseTest.LDSEventListener(regInfo);
        LookupDiscoveryRegistration ldsReg = null;
	try {
	    ldsReg = discoverySrvc.register(groupsToDiscover,
					    locatorsToDiscover,
					    listener,
					    mHandback,
					    leaseDuration);	
	    logger.log(Level.FINEST, "Preparing fiddler registration");
	    Configuration c = config.getConfiguration();
	    ProxyPreparer p = new BasicProxyPreparer();
	    if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
		  p = (ProxyPreparer) c.getEntry("test", 
					       "fiddlerRegistrationPreparer", 
					       ProxyPreparer.class);
	    }
	    registrationMap.put(p.prepareProxy(ldsReg),listener);
	} catch (ConfigurationException e) {
	    throw new RemoteException("Configuration Error", e);
	}
    }//end doRegistration

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  resets the state of each flag and set related to discovery and discard
     *  events received for the registration.
     *
     *  This method is useful when the nature of a test requires that the
     *  expected event-related information for all of the current
     *  registrations be reset to its un-initialized state so that future
     *  calls to waitForDiscovery and/or waitForDiscard will not expect
     *  events for those registrations.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void resetAllEventInfoAllRegs() {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            resetAllEventInfoOneReg( (Map.Entry)iter.next() );
        }//end loop
    }//end resetAllEventInfoAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method resets the state of each flag and set related
     *  to discovery and discard events received for the registration
     *  referenced in the pair.
     *
     *  This method is useful when the nature of a test requires that the
     *  expected event-related information for a particular registrations be
     *  reset to its un-initialized state so that future calls to
     *  waitForDiscovery and/or waitForDiscard will not expect events for
     *  that registrations.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void resetAllEventInfoOneReg(Map.Entry regListenerPair) {
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        regInfo.resetAllEventInfo();
    }//end resetAllEventInfoOneReg

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  resets the state of each flag and set related to discovery events
     *  received for the registration.
     *
     *  This method is useful when the nature of a test requires that only the
     *  expected discovery event-related information for all of the current
     *  registrations be reset to its un-initialized state so that future
     *  calls to waitForDiscovery will not expect discovery events for those
     *  registrations.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void resetDiscoveryEventInfoAllRegs() {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            resetDiscoveryEventInfoOneReg( (Map.Entry)iter.next() );
        }//end loop
    }//end resetDiscoveryEventInfoAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method resets the state of each flag and set related
     *  to discovery events received for the registration referenced in the
     *  pair.
     *
     *  This method is useful when the nature of a test requires that only the
     *  expected discovery event-related information for a particular
     *  registrations be reset to its un-initialized state so that future
     *  calls to waitForDiscovery will not expect discovery events for that
     *  registration.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void resetDiscoveryEventInfoOneReg(Map.Entry regListenerPair) {
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        regInfo.resetDiscoveryEventInfo();
    }//end resetDiscoveryEventInfoOneReg

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  resets the state of each flag and set related to discard events
     *  received for the registration.
     *
     *  This method is useful when the nature of a test requires that only the
     *  expected discard event-related information for all of the current
     *  registrations be reset to its un-initialized state so that future
     *  calls to waitForDiscard will not expect discard events for those
     *  registrations.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void resetDiscardEventInfoAllRegs() {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            resetDiscardEventInfoOneReg( (Map.Entry)iter.next() );
        }//end loop
    }//end resetDiscardEventInfoAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method resets the state of each flag and set related
     *  to discard events received for the registration referenced in the
     *  pair.
     *
     *  This method is useful when the nature of a test requires that only the
     *  expected discard event-related information for a particular
     *  registrations be reset to its un-initialized state so that future
     *  calls to waitForDiscard will not expect discard events for that
     *  registration.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void resetDiscardEventInfoOneReg(Map.Entry regListenerPair) {
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        regInfo.resetDiscardEventInfo();
    }//end resetDiscardEventInfoOneReg

    /** Convenience method that allows sub-classes of this class to request
     *  that the state related to discard event information be cleared
     *  (that is, reset) for each registration referenced in the given regMap.
     */
    protected void resetDiscardEventInfoRegMap(HashMap regMap) {
        Iterator iter = (regMap.entrySet()).iterator();
        for(int i=0;iter.hasNext();i++) {
            resetDiscardEventInfoOneReg((Map.Entry)iter.next());
        }//end loop
    }//end resetDiscardEventInfoRegMap

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  augments that registration's desired groups with the given set of
     *  groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void addGroupsAllRegs(String[] groups) throws RemoteException {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            addGroupsOneReg( groups, (Map.Entry)iter.next() );
        }//end loop
        if( groups == DiscoveryGroupManagement.ALL_GROUPS ) {
            logger.log(Level.FINE, "   FAILURE -- tried to add ALL_GROUPS");
        } else if( groups.length <= 0 ) {
            logger.log(Level.FINE, "   NO-OP -- tried to add NO_GROUPS");
        } else {
            GroupsUtil.displayGroupSet(groups,
                                       "   all regs -- group",Level.FINE);
        }//endif
    }//end addGroupsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method augments that registration's desired groups
     *  with the given set of groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void addGroupsOneReg(String[]  groups,
                                   Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        String[] groupsToDiscover = groups;
        if(   (regInfo.groupsToDiscover != DiscoveryGroupManagement.ALL_GROUPS)
            &&(groups != DiscoveryGroupManagement.ALL_GROUPS) )
       {
            int curLen = (regInfo.groupsToDiscover).length;
            int newLen = groups.length;
            groupsToDiscover = new String[curLen + newLen];
            for(int i=0;i<curLen;i++) {
                groupsToDiscover[i] = new String(regInfo.groupsToDiscover[i]);
            }//end loop
            for(int i=curLen;i<(curLen+newLen);i++) {
                groupsToDiscover[i] = new String(groups[i-curLen]);
            }//end loop
            synchronized(regInfo) {
                /* update the expected state for this regInfo information */
                regInfo.setLookupsToDiscover(groupsToDiscover,
                                             new LookupLocator[0]);
            }//end sync(regInfo)
        }//endif
        /* Add to the groups the lds should discover for this registration */
        reg.addGroups(groups);
    }//end addGroupsOneReg

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  replaces that registration's desired groups with the given set of
     *  groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void setGroupsAllRegs(String[] groups) throws RemoteException {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            setGroupsOneReg( groups, (Map.Entry)iter.next() );
        }//end loop
        if( groups == DiscoveryGroupManagement.ALL_GROUPS ) {
            logger.log(Level.FINE, "   all regs -- groups set to ALL_GROUPS");
        } else if( groups.length <= 0 ) {
            logger.log(Level.FINE, "   all regs -- groups set to NO_GROUPS");
        } else {
            GroupsUtil.displayGroupSet(groups,
                                       "   all regs -- group",Level.FINE);
        }//endif
    }//end setGroupsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method replaces that registration's desired groups
     *  with the given set of groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void setGroupsOneReg(String[]  groups,
                                   Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        synchronized(regInfo) {
            /* update the expected state for this regInfo information */
            regInfo.setLookupsToDiscover(groups,new LookupLocator[0]);
        }//end sync(regInfo)
        /* Change the groups the lds should discover for this registration */
        reg.setGroups(groups);
    }//end setGroupsOneReg

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  removes the given set of groups from that registration's desired
     *  groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void removeGroupsAllRegs(String[] groups) throws RemoteException{
        Iterator iter = (registrationMap.entrySet()).iterator();
        for(int i=0;iter.hasNext();i++) {
            removeGroupsOneReg( groups, (Map.Entry)iter.next() );
        }//end loop
        if( groups == DiscoveryGroupManagement.ALL_GROUPS ) {
            logger.log(Level.FINE, "   FAILURE -- tried to remove ALL_GROUPS");
        } else if( groups.length <= 0 ) {
            logger.log(Level.FINE, "   NO-OP -- tried to remove NO_GROUPS");
        } else {
            GroupsUtil.displayGroupSet(groups,
                              "   all regs removing -- group",Level.FINE);
        }//endif
    }//end removeGroupsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method removes the given set of groups from that
     *  registration's desired groups.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void removeGroupsOneReg(String[]  groups,
                                      Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();

        String[] curGroupsToDiscover = regInfo.groupsToDiscover;
        String[] groupsToRemove = groups;
        String[] newGroupsToDiscover = DiscoveryGroupManagement.NO_GROUPS;

        if(    (curGroupsToDiscover != DiscoveryGroupManagement.ALL_GROUPS)
            && (groupsToRemove      != DiscoveryGroupManagement.ALL_GROUPS) )
        {
            int curLen = curGroupsToDiscover.length;
            int remLen = groupsToRemove.length;
            ArrayList newList = new ArrayList(curLen+remLen);
            iLoop:
            for(int i=0;i<curLen;i++) {
                for(int j=0;j<remLen;j++) {
                    if(curGroupsToDiscover[i].equals(groupsToRemove[j])) {
                        continue iLoop;
                    }//endif
                }//endloop(j)
                newList.add(curGroupsToDiscover[i]);
            }//end loop(i)
            newGroupsToDiscover
                   = (String[])(newList.toArray(new String[newList.size()]));
            synchronized(regInfo) {
                /* update the expected state for this regInfo information */
                regInfo.setLookupsToDiscover(newGroupsToDiscover,
                                             new LookupLocator[0]);
            }//end sync(regInfo)
        }//endif
        /* remove the groups the lds should discover from this registration */
        reg.removeGroups(groups);
    }//end removeGroupsOneReg

    /** Convenience method that allows sub-classes of this class to request,
     *  for the registrations referenced in the given regMap, the removal
     *  of the given set of groups from each registration.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void removeGroupsRegMap(HashMap regMap, String[] groups)
                                                       throws RemoteException
    {
        Set eSet = regMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            Map.Entry regListenerPair = (Map.Entry)iter.next();
            removeGroupsOneReg( groups, regListenerPair );
            if( groups.length <= 0 ) {
                logger.log(Level.FINE, "   NO-OP -- tried to remove NO_GROUPS");
            } else {
                RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
                GroupsUtil.displayGroupSet( groups,
                                               "   registration_"
                                               +regInfo.handback
                                               +" removing -- group",
                                               Level.FINE);
            }//endif
        }//end loop
    }//end removeGroupsRegMap

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  augments that registration's desired locators with the given set of
     *  locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void addLocatorsAllRegs(LookupLocator[] locators)
                                                      throws RemoteException
    {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            addLocatorsOneReg( locators, (Map.Entry)iter.next() );
        }//end loop
        if( locators.length <= 0 ) {
            logger.log(Level.FINE, "   NO-OP -- tried to add NO_LOCATORS");
        } else {
            LocatorsUtil.displayLocatorSet(locators,
                                      "   all regs -- locator",Level.FINE);
        }//endif
    }//end addLocatorsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method augments that registration's desired locators
     *  with the given set of locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void addLocatorsOneReg(LookupLocator[] locators,
                                     Map.Entry       regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        LookupLocator[] locatorsToDiscover = locators;

        if(locators != null) {
            int curLen = (regInfo.locatorsToDiscover).length;
            int newLen = locators.length;
            locatorsToDiscover = new LookupLocator[curLen + newLen];
            for(int i=0;i<curLen;i++) {
                locatorsToDiscover[i] = regInfo.locatorsToDiscover[i];
            }//end loop
            for(int i=curLen;i<(curLen+newLen);i++) {
                locatorsToDiscover[i] = locators[i-curLen];
            }//end loop
        }//endif
        synchronized(regInfo) {
            /* update the expected state for this regInfo information */
            regInfo.setLookupsToDiscover(DiscoveryGroupManagement.NO_GROUPS,
                                         locatorsToDiscover);
        }//end sync(regInfo)
        /* Add to the locators the lds should discover for this registration */
        reg.addLocators(locators);
    }//end addLocatorsOneReg

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  replaces that registration's desired locators with the given set of
     *  locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void setLocatorsAllRegs(LookupLocator[] locators)
                                                      throws RemoteException
    {
        Set eSet = registrationMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            setLocatorsOneReg( locators, (Map.Entry)iter.next() );
        }//end loop
        if( locators.length == 0 ) {
            logger.log(Level.FINE, "   all regs -- locators set to NO_LOCATORS");
        } else {
            LocatorsUtil.displayLocatorSet(locators,
                                      "   all regs -- locator",Level.FINE);
        }//endif
    }//end setLocatorsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method replaces that registration's desired locators
     *  with the given set of locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void setLocatorsOneReg(LookupLocator[]  locators,
                                     Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
        synchronized(regInfo) {
            /* update the expected state for this regInfo information */
            regInfo.setLookupsToDiscover(DiscoveryGroupManagement.NO_GROUPS,
                                         locators);
        }//end sync(regInfo)
        /* Change the locators the lds should discover for this registration */
        reg.setLocators(locators);
    }//end setLocatorsOneReg

    /** Common code, shared by this class and its sub-classes. For each
     *  current registration with the lookup discovery service, this method
     *  removes the given set of locators from that registration's desired
     *  locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void removeLocatorsAllRegs(LookupLocator[] locators)
                                                       throws RemoteException
    {
        Iterator iter = (registrationMap.entrySet()).iterator();
        for(int i=0;iter.hasNext();i++) {
            removeLocatorsOneReg( locators, (Map.Entry)iter.next() );
        }//end loop
        if( locators == null ) {
            logger.log(Level.FINE, "   FAILURE -- tried to remove null");
        } else if( locators.length == 0 ) {
            logger.log(Level.FINE, "   NO-OP -- tried to remove NO_LOCATORS");
        } else {
            LocatorsUtil.displayLocatorSet(locators,
                            "   all regs removing -- locator",Level.FINE);
        }//endif
    }//end removeLocatorsAllRegs

    /** Common code, shared by this class and its sub-classes. For the given
     *  <code>Map.Entry</code> ordered pair, in which a current registration
     *  with the lookup discovery service is the first element of the pair,
     *  and that registration's corresponding listener is the second element
     *  of the pair, this method removes the given set of locators from that
     *  registration's desired locators.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void removeLocatorsOneReg(LookupLocator[]  locators,
                                        Map.Entry regListenerPair)
                                                       throws RemoteException
    {
        LookupDiscoveryRegistration reg
                 = (LookupDiscoveryRegistration)regListenerPair.getKey();
        RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();

        LookupLocator[] curLocatorsToDiscover = regInfo.locatorsToDiscover;
        LookupLocator[] locatorsToRemove = locators;
        LookupLocator[] newLocatorsToDiscover = new LookupLocator[0];

        if( (curLocatorsToDiscover != null) && (locatorsToRemove != null) ) {
            int curLen = curLocatorsToDiscover.length;
            int remLen = locatorsToRemove.length;
            ArrayList newList = new ArrayList(curLen+remLen);
            iLoop:
            for(int i=0;i<curLen;i++) {
                for(int j=0;j<remLen;j++) {
                    if( locsEqual
                             (curLocatorsToDiscover[i],locatorsToRemove[j]) )
                    {
                        continue iLoop;
                    }//endif
                }//endloop(j)
                newList.add(curLocatorsToDiscover[i]);
            }//end loop(i)
            newLocatorsToDiscover = (LookupLocator[])(newList.toArray
                                         (new LookupLocator[newList.size()]));
            synchronized(regInfo) {
                /* update the expected state for this regInfo information */
                regInfo.setLookupsToDiscover
                                          (DiscoveryGroupManagement.NO_GROUPS,
                                           newLocatorsToDiscover);
            }//end sync(regInfo)
        }//endif
        /* remove the locators the lds should discover for this registration */
        reg.removeLocators(locators);
    }//end removeLocatorsOneReg

    /** Convenience method that allows sub-classes of this class to request,
     *  for the registrations referenced in the given regMap, the removal
     *  of the given set of locators from each registration.
     *  
     *  @throws jave.rmi.RemoteException
     */
    protected void removeLocatorsRegMap(HashMap regMap,
                                        LookupLocator[] locators)
                                                       throws RemoteException
    {
        Set eSet = regMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int i=0;iter.hasNext();i++) {
            Map.Entry regListenerPair = (Map.Entry)iter.next();
            removeLocatorsOneReg( locators, regListenerPair );
            if( locators.length == 0 ) {
                logger.log(Level.FINE, "   NO-OP -- tried to remove NO_LOCATORS");
            } else {
                RegistrationInfo regInfo  
                 = ((LDSEventListener)regListenerPair.getValue()).getRegInfo();
                LocatorsUtil.displayLocatorSet( locators,
                                               "   registration_"
                                               +regInfo.handback
                                               +" removing -- locator",
                                               Level.FINE);
            }//endif
        }//end loop
    }//end removeLocatorsRegMap

    /** Common code shared by each test that needs to wait for discovered
     *  events from the lookup discovery service, and verify that the
     *  expected discovered events have indeed arrived for each active
     *  registration.
     */
    protected void waitForDiscovery() throws TestException {
        /* Wait for the expected number of discovered events from each reg */
        int nSecsToWait0 = (nIntervalsToWait*(announceInterval/1000))/4;
        if(nSecsToWait0 == 0) nSecsToWait0 = 10;//small nominal value
        int nSecsToWait1 = 1; // guarantee at least 1 pass through timer loop
        if(nSecsToWait0 < maxSecsEventWait) {
            nSecsToWait1 = (maxSecsEventWait - nSecsToWait0);
        }//endif
        if(debugFlag) {//reset timeouts for faster completion
            nSecsToWait0 = FAST_TIMEOUT;
            nSecsToWait1 = FAST_TIMEOUT;
        }//endif
        logger.log(Level.FINE, "for DISCOVERY events -- waiting "
                          +"at least "+nSecsToWait0+" seconds, but no more "
                          +"than "+maxSecsEventWait+" seconds ...");
        /* no early breakout; verifies no extra discarded events are sent */
        for(int i=0;i<nSecsToWait0;i++) {
            DiscoveryServiceUtil.delayMS(1000);
        }//end loop
        /* Minimum wait period complete, now test for discovered events. Wait
         * no more than (max-min) seconds, exit immediately on success.
         */
        logger.log(Level.FINE, "initial wait period complete ... "
                         +"waiting at most "+nSecsToWait1+" more seconds ...");
        int[] displayFlag = new int[registrationMap.size()];
        int nGoodDiscoverySets = 0;
        int nCurRegistrations = registrationMap.size();
        timerLoop:
        for(int n=0;n<nSecsToWait1;n++) {
             /* step through each listener/regInfo */
            Iterator iter = registrationMap.values().iterator();
            for(int i=0;iter.hasNext();i++) {
                 LDSEventListener listener = (LDSEventListener)iter.next();
                RegistrationInfo regInfo  = listener.getRegInfo();
                synchronized(regInfo) {
                    if(regInfo.discoveryComplete) continue;
                    Map discoveredMap         = regInfo.discoveredMap;
                    Map expectedDiscoveredMap = regInfo.expectedDiscoveredMap;
                    if(displayOn && displayFlag[i] == 0) {//show info only once
                        displayFlag[i] = 1;
                        logger.log(Level.FINE, "  registration_"
                                          +regInfo.expectedHandback
                                          +" -- discoveredMap.size == "
                                          +discoveredMap.size());
                        Collection vals = discoveredMap.values();
                        for(Iterator itr = vals.iterator(); itr.hasNext(); ) {
                            String[] vGroups = (String[])itr.next();
                            if(vGroups == null) {
                                logger.log(Level.FINE, "  registration_"
                                                  +regInfo.expectedHandback
                                   +" -- discoveredMap.groups == ALL_GROUPS");
                            } else if( vGroups.length <= 0 ) {
                                logger.log(Level.FINE, "   registration_"
                                                  +regInfo.expectedHandback
                                   +" -- discoveredMap.groups == NO_GROUPS");
                            } else {
                                for(int m=0;m<vGroups.length;m++){
                                logger.log(Level.FINE, "  registration_"
                                                  +regInfo.expectedHandback
                                   +" -- discoveredMap.groups["+m+"] == "
                                                  +vGroups[m]);
                                }//end loop
                            }//endif
                        }//end loop
                        logger.log(Level.FINE, "  registration_"
                                         +regInfo.expectedHandback
                                         +" -- expectedDiscoveredMap.size == "
                                         +expectedDiscoveredMap.size());
                        vals = expectedDiscoveredMap.values();
                        for(Iterator itr = vals.iterator(); itr.hasNext(); ) {
                            String[] vGroups = (String[])itr.next();
                            if(vGroups == null) {
                                logger.log(Level.FINE, "  registration_"
                                                  +regInfo.expectedHandback
                                          +" -- expectedDiscoveredMap.groups "
                                                  +"== ALL_GROUPS");
                            } else if( vGroups.length <= 0 ) {
                                logger.log(Level.FINE, "   registration_"
                                                  +regInfo.expectedHandback
                                          +" -- expectedDiscoveredMap.groups "
                                                  +"== NO_GROUPS");
                            } else {
                                for(int m=0;m<vGroups.length;m++){
                                logger.log(Level.FINE, "  registration_"
                                                  +regInfo.expectedHandback
                                    +" -- expectedDiscoveredMap.groups["+m+"] "
                                                  +"== "+vGroups[m]);
                                }//end loop
                            }//endif
                        }//end loop
                    }//endif(displayOn && displayFlag[i] == 0)
                    /* nEventsReceived == nEventsExpected for this regInfo? */
                    if(discoveredMap.size() == expectedDiscoveredMap.size()) {
                        if(n == (nSecsToWait1-1)) {
                            logger.log(Level.FINE, "  registration_"
                               +regInfo.handback+" -- events expected ("
                               +expectedDiscoveredMap.size()+") == events "
                               +"received ("+discoveredMap.size()+")");
                        }//endif
                        /* groups in received event == expected groups? */
                        if( groupSetsEqual(discoveredMap,
                                           expectedDiscoveredMap) )
                        {
                            nGoodDiscoverySets++;
                            logger.log(Level.FINE, "  registration_"
                               +regInfo.handback+" -- events expected ("
                               +expectedDiscoveredMap.size()+") == events "
                               +"received ("+discoveredMap.size()+"),"
                               +" group sets equal (# of discoveries = "
                               +nGoodDiscoverySets+")");
                            regInfo.discoveryComplete = true;
                            break; // iter loop
                        } else {
                            if(n == (nSecsToWait1-1)) {
                                logger.log(Level.FINE, "   registration_"
                                                  +regInfo.expectedHandback
                                                  +" -- group sets not equal");
                                displayUnequalGroupSets(discoveredMap,
                                                        expectedDiscoveredMap);
                            }//endif
                        }//endif
                    } else {
                        if(n == (nSecsToWait1-1)) {
                            logger.log(Level.FINE, "   registration_"
                                              +regInfo.expectedHandback
                                              +" -- events expected ("
                                              +expectedDiscoveredMap.size()
                                              +") != events received ("
                                              +discoveredMap.size()+")");
                        }//endif
                    }//endif
                }//end sync
                if(nGoodDiscoverySets == nCurRegistrations)  break timerLoop;
            }//end loop(iter)
            if(nGoodDiscoverySets == nCurRegistrations) break timerLoop;
            DiscoveryServiceUtil.delayMS(1000);
        }//end loop(timerLoop)
        logger.log(Level.FINE, "DISCOVERY wait period complete");
        /* Verify discovery was successful */
        if(nGoodDiscoverySets != nCurRegistrations) {
            throw new TestException("discovery failed -- "
                                      +"waited "+nSecsToWait1
                                      +" seconds ("+(nSecsToWait1/60)
                                      +" minutes) -- "
                                      +nCurRegistrations
                                      +" registration(s) with lookup "
                                      +"discovery service, "+nGoodDiscoverySets
                                      +" registrations with successful "
                                      +"discoveries");
        }//endif
        logger.log(Level.FINE, ""+nCurRegistrations
                                      +" registration(s) with lookup "
                                      +"discovery service, "+nGoodDiscoverySets
                                      +" registrations with successful "
                                      +"discoveries");
    }//end waitForDiscovery

    protected Map getExpectedDiscardedMap(RegistrationInfo regInfo, 
                                          int              discardType)
    {
        switch(discardType) {
            case ACTIVE_DISCARDED:
                return regInfo.expectedActiveDiscardedMap;
            case COMM_DISCARDED:
                return regInfo.expectedCommDiscardedMap;
            case NO_INTEREST_DISCARDED:
                return regInfo.expectedNoInterestDiscardedMap;
            case PASSIVE_DISCARDED:
                return regInfo.expectedPassiveDiscardedMap;
        }//end switch
        return regInfo.expectedActiveDiscardedMap;
    }//end getExpectedDiscardedMap

    /** Common code shared by each test that needs to wait for discarded
     *  events from the lookup discovery service utility, and verify that the
     *  expected discard events have indeed arrived.
     */
    protected void waitForDiscard(int discardType) throws TestException {
        /* Wait for the expected number of discarded events from each reg */
        int nSecsToWait0 = (nIntervalsToWait*(announceInterval/1000))/2;
        if(nSecsToWait0 == 0) nSecsToWait0 = 10;//small nominal value
        int nSecsToWait1 = 1; // guarantee at least 1 pass through timer loop
        if(nSecsToWait0 < maxSecsEventWait) {
            nSecsToWait1 = (maxSecsEventWait - nSecsToWait0);
        }//endif
        if(debugFlag) {//reset timeouts for faster completion
            nSecsToWait0 = FAST_TIMEOUT;
            nSecsToWait1 = FAST_TIMEOUT;
        }//endif
        logger.log(Level.FINE, "for DISCARD events -- waiting at "
                          +"least "+nSecsToWait0+" seconds, but no more than "
                          +maxSecsEventWait+" seconds ...");
        /* no early breakout; verifies no extra discarded events are sent */
        for(int i=0;i<nSecsToWait0;i++) {
            DiscoveryServiceUtil.delayMS(1000);
        }//end loop
        /* Minimum wait period complete, now test for discard events. Wait no
         * more than (max-min) seconds, exit immediately on success.
         */
        logger.log(Level.FINE, "initial wait period complete ... "
                         +"waiting at most "+nSecsToWait1+" more seconds ...");
        int[] displayFlag = new int[registrationMap.size()];
        int nGoodSets = 0;
        int nCurRegistrations = registrationMap.size();
        int n=0;
        timerLoop:
        for(n=0;n<nSecsToWait1;n++) {
            /* step thru each listener/regInfo, testing for discarded events */
            Iterator iter = registrationMap.values().iterator();
            for(int i=0;iter.hasNext();i++) {
                LDSEventListener listener = (LDSEventListener)iter.next();
                RegistrationInfo regInfo = listener.getRegInfo();
                synchronized(regInfo) {
                    if(regInfo.discardComplete) continue;
                    Map discardedMap         = regInfo.discardedMap;
                    Map expectedDiscardedMap = getExpectedDiscardedMap
                                                       (regInfo,discardType);
                    if(displayOn && displayFlag[i] == 0) {//show info only once
                        logger.log(Level.FINE, "   registration_"
                                          +regInfo.expectedHandback
                                          +" -- discardedMap.size == "
                                          +discardedMap.size());
                        Collection vals = discardedMap.values();
                        for(Iterator itr = vals.iterator(); itr.hasNext(); ) {
                            String[] vGroups = (String[])itr.next();
                            if(vGroups == null) {
                                logger.log(Level.FINE, "   registration_"
                                                  +regInfo.expectedHandback
                                   +" -- discardedMap.groups == ALL_GROUPS");
                            } else if( vGroups.length <= 0 ) {
                                logger.log(Level.FINE, "   registration_"
                                                  +regInfo.expectedHandback
                                   +" -- discardedMap.groups == NO_GROUPS");
                            } else {
                                for(int m=0;m<vGroups.length;m++){
                                logger.log(Level.FINE, "   registration_"
                                                  +regInfo.expectedHandback
                                   +" -- discardedMap.groups["+m+"] == "
                                                  +vGroups[m]);
                                }//end loop
                            }//endif
                        }//end loop
                        logger.log(Level.FINE, "  registration_"
                                          +regInfo.expectedHandback
                                          +" -- expectedDiscardedMap.size == "
                                          +expectedDiscardedMap.size());
                        vals = expectedDiscardedMap.values();
                        for(Iterator itr = vals.iterator(); itr.hasNext(); ) {
                            String[] vGroups = (String[])itr.next();
                            if(vGroups == null) {
                                logger.log(Level.FINE, "  registration_"
                                                  +regInfo.expectedHandback
                                          +" -- expectedDiscardedMap.groups "
                                                  +"== ALL_GROUPS");
                            } else if( vGroups.length <= 0 ) {
                                logger.log(Level.FINE, "  registration_"
                                                  +regInfo.expectedHandback
                                          +" -- expectedDiscardedMap.groups "
                                                  +"== NO_GROUPS");
                            } else {
                                for(int m=0;m<vGroups.length;m++){
                                logger.log(Level.FINE, "   registration_"
                                                  +regInfo.expectedHandback
                                    +" -- expectedDiscardedMap.groups["+m+"] "
                                                  +"== "+vGroups[m]);
                                }//end loop
                            }//endif
                        }//end loop
                    }//endif(displayOn && displayFlag[i] == 0)
                    /* nEventsReceived == nEventsExpected for this regInfo? */
                    if(discardedMap.size() == expectedDiscardedMap.size()) {
                        if(n == (nSecsToWait1-1)) {
                            logger.log(Level.FINE, "  registration_"
                               +regInfo.handback+" -- events expected ("
                               +expectedDiscardedMap.size()+") == events "
                               +"received ("+discardedMap.size()+")");
                        }//endif
                        /* groups in received event == expected groups? */
                        if(groupSetsEqual(discardedMap,expectedDiscardedMap)) {
                            nGoodSets++;
                            logger.log(Level.FINE, "  registration_"
                               +regInfo.handback+" -- events expected ("
                               +expectedDiscardedMap.size()+") == events "
                               +"received ("+discardedMap.size()+"),"
                               +" group sets equal (# of discards = "
                               +nGoodSets+")");
                            regInfo.discardComplete = true;
                            break; // iter loop
                        } else {//group sets not equal
                            if(n == (nSecsToWait1-1)) {
                                logger.log(Level.FINE, "  registration_"
                                                  +regInfo.expectedHandback
                                                  +" -- group sets not equal");
                                displayUnequalGroupSets(discardedMap,
                                                        expectedDiscardedMap);
                            }//endif
                        }//endif
                    } else {
                        if(n == (nSecsToWait1-1)) {
                            logger.log(Level.FINE, "  registration_"
                                              +regInfo.expectedHandback
                                              +" -- events expected ("
                                              +expectedDiscardedMap.size()
                                              +") != events received ("
                                              +discardedMap.size()+")");
                        }//endif
                    }//endif
                }//end sync
                if(nGoodSets == nCurRegistrations)  break timerLoop;
            }//end loop(iter)
            if(nGoodSets == nCurRegistrations) break timerLoop;
            DiscoveryServiceUtil.delayMS(1000);
        }//end loop(timerLoop)
        logger.log(Level.FINE, "DISCARD wait period complete -- "
                          +"waited an extra "+n+" seconds");
        /* Verify discard was successful */
        if(nGoodSets != nCurRegistrations) {
            throw new TestException("discard failed -- "
                                      +"waited "
                                      +(nSecsToWait0+nSecsToWait1)
                                      +" seconds ("
                                      +((nSecsToWait0+nSecsToWait1)/60)
                                      +" minutes) -- "
                                      +nCurRegistrations
                                      +" registration(s) with lookup "
                                      +"discovery service, "+nGoodSets
                                      +" registrations with successful "
                                      +"discards");
        }//endif
        logger.log(Level.FINE, ""+nCurRegistrations
                                      +" registration(s) with lookup "
                                      +"discovery service, "+nGoodSets
                                      +" registrations with successful "
                                      +"discards");
    }//end waitForDiscard

    /** Common code, shared by this class and its sub-classes, that is 
     *  invoked by the run() method. This will method will replace the
     *  member groups of each lookup service started during setup. How
     *  those groups are replaced is dependent on the values of the input
     *  parameters. 
     * 
     *  Each group component of the replaceMap parameter will be replaced
     *  with the new groups whenever the lookpup service proxy whose
     *  groups are being replaced is also a key of the replaceMap.
     *
     *  If the replaceGroups parameter is non-null, that set of groups
     *  will be used to replace the groups of each lookup service; otherwise
     *  the set used to replace the goups will be constructed based on the
     *  the current group names.
     *
     *  If the alternate parameter is true, then when replacing the
     *  groups of any lookup service, only every other element of that
     *  set will be replaced with a new group name.
     *
     *  The discardType parameter is used to determine which map to populate
     *  with the expected discard data that is used to verify the assertion.
     */
    protected void replaceGroups(Object curGen,
                                 int genIndx,
                                 String[] replaceGroups,
                                 boolean alternate,
                                 int discardType)
    {
        try {
            ServiceRegistrar lookupProxy = null;
            if( curGen instanceof DiscoveryProtocolSimulator ) {
                DiscoveryProtocolSimulator generator 
                                         = (DiscoveryProtocolSimulator)curGen;
                lookupProxy = generator.getLookupProxy();
            } else {
                lookupProxy = (ServiceRegistrar)curGen;
            }//endif
            String[] curGroups = lookupProxy.getGroups();
            String[] newGroups =  new String[] {"Group_"
                                                +lookupProxy.getServiceID()};
            if(replaceGroups != null) {
                newGroups = replaceGroups;
            } else {
                if((curGroups != null) && (curGroups.length > 0)) {
                    if(alternate) {
                        int len = curGroups.length;
                        newGroups = new String[len];
                        for(int j=0;j<=(len/2);j++) {
                            int k = 2*j;
                            if(k >= len) break;
                            newGroups[k] = new String(curGroups[k]+"_new");
                            if( (k+1) >= len ) break;
                            newGroups[k+1]= new String(curGroups[k+1]);
                        }//end loop
                    } else { //don't alternate
                        newGroups = new String[] {curGroups[0]+"_new"};
                    }//endif (alternate)
                }//endif (curGroups!=null)
            }//endif
            if( (newGroups != null) && (newGroups.length <= 0) ) {
                logger.log(Level.FINE, "   newGroups = NO_GROUPS");
            } else {
                GroupsUtil.displayGroupSet(newGroups,
                                           "   newGroups",Level.FINE);
            }//endif
	    Iterator iter = registrationMap.values().iterator();
            for(int j=0;iter.hasNext();j++) {
                LDSEventListener listener = (LDSEventListener)iter.next();
                RegistrationInfo regInfo  = listener.getRegInfo();
                if(regInfo.expectedDiscoveredMap.containsKey(lookupProxy)) {
                    regInfo.expectedDiscoveredMap.put(lookupProxy,newGroups);
                }//endif
                Map expectedDiscardedMap = getExpectedDiscardedMap
                                                        (regInfo,discardType);
                if(expectedDiscardedMap.containsKey(lookupProxy)) {
                    if(discardType != PASSIVE_DISCARDED) {
                        expectedDiscardedMap.put(lookupProxy,newGroups);
                    } else {//(discardType == PASSIVE_DISCARDED)
                        /* If announcements were stopped, but the groups are
                         * replaced to generate an event, then the LDM will
                         * send discarded events for those lookups that are
                         * being discovered by only group discovery by ALL
                         * the registrations; and the LDS will then send
                         * discarded events that reflect the OLD groups, not
                         * the NEW groups being set here. On the other hand,
                         * for a given lookup service whose announcements have
                         * been stopped, if at least one registration is 
                         * interested in discovering that lookup by locator
                         * as well as by group, then the LDM will send a
                         * changed event, but the LDS will send a discarded
                         * event reflecting the NEW groups to each registration
                         * that is not also interested in the lookup by
                         * locator. The LDS does because the new groups are
                         * not of interest to those registrations. Thus, test
                         * for this situation and change the expected discarded
                         * groups for a registration only if that registration
                         * also wishes to discover the lookup by its locator.
                         */
                        try {
                            if( discoverByLocAnyReg(lookupProxy) ) {
                                expectedDiscardedMap.put(lookupProxy,
                                                         newGroups);
                            }//endif
                        } catch(Exception e1) { /*skip this lookupProxy*/ }
                    }//endif
                }//endif
            }//end loop(iter)
            setLookupMemberGroups(lookupProxy,newGroups);
        } catch(RemoteException e) {
            logger.log(Level.FINE, "failed to replace member groups "
                              +"for lookup service "+genIndx);
            e.printStackTrace();
        } catch(TestException e) {
            logger.log(Level.FINE, "failed to replace member groups "
                              +"for lookup service "+genIndx);
            e.printStackTrace();
        }
    }//end replaceGroups

    protected void replaceGroups(String[] replaceGroups,
                                 boolean alternate,
                                 int discardType)
    {
        if(nStarted > 0) {
            logger.log(Level.FINE, "replacing member groups for each lookup service ...");
	    Iterator iter = genMap.keySet().iterator();

            for(int i=0;iter.hasNext();i++) {
                replaceGroups(iter.next(),i,replaceGroups,
                              alternate,discardType);
            }//end loop
        }//endif
    }//end replaceGroups

    protected void replaceWithNoGroups(int discardType) {
        replaceGroups(DiscoveryGroupManagement.NO_GROUPS, false, discardType);
    }//end replaceWithNoGroups

    protected void changeGroups(int discardType) {
        replaceGroups(null,true, discardType); // alternates new groups
    }//end changeGroups

    protected void replaceGroups(int discardType) {
        replaceGroups(null,false, discardType); // basic replacement
    }//end replaceGroups

    /** Convenience method that determines whether the locator of the
     *  given registrar is a locator-of-interest to any of the current 
     *  client registrations. If at least one such registration wishes
     *  to discover the given registration by locator, then this method
     *  returns true; otherwise, it returns false.
     *
     *  @throws jave.rmi.RemoteException upon failing to retrieve the locator
     *          of the given registrar.
     */
    protected boolean discoverByLocAnyReg(ServiceRegistrar lookupProxy)
                                                         throws RemoteException
    {
        LookupLocator loc = QAConfig.getConstrainedLocator(lookupProxy.getLocator());
        /* If at least one of the registrations in the registrationMap is
         * configured to discover the given lookup service by locator, then
         * return true; otherwise return false.
         */
	Iterator iter = registrationMap.values().iterator();
        for(int i=0;iter.hasNext();i++) {
            LDSEventListener listener = (LDSEventListener)iter.next();
            RegistrationInfo regInfo  = listener.getRegInfo();
            for(int j=0;j<regInfo.locatorsToDiscover.length;j++) {
                if( locsEqual(loc,regInfo.locatorsToDiscover[j]) ) return true;
            }//end loop(j)
        }//end loop(iter)
        return false;
    }//end discoverByLocAnyReg

    /** Determines if the String[] contents of the given maps are equivalent */
    protected boolean groupSetsEqual(Map map0,Map map1) throws TestException{
        synchronized(lockObject) {
            Map newMap0 = getRegToGroupSetMapping(map0);
            Map newMap1 = getRegToGroupSetMapping(map1);
	    Iterator iter = newMap0.keySet().iterator();
            for(int i=0;iter.hasNext();i++) {
                Object key = iter.next();
                String[] groups0 = (String[])newMap0.get(key);
                String[] groups1 = (String[])newMap1.get(key);
                if(!GroupsUtil.compareGroupSets(groups0,groups1, Level.OFF)) return false;
            }//end loop
        }//end sync
        return true;
    }//end groupSetsEqual

    protected void displayUnequalGroupSets(Map map0,Map map1) {
        synchronized(lockObject) {
            Map newMap0 = getRegToGroupSetMapping(map0);
            Map newMap1 = getRegToGroupSetMapping(map1);
	    Iterator iter = newMap0.keySet().iterator();
            for(int i=0;iter.hasNext();i++) {
                Object key = iter.next();
                String[] groups0 = (String[])newMap0.get(key);
                String[] groups1 = (String[])newMap1.get(key);
                if(!GroupsUtil.compareGroupSets(groups0,groups1,Level.OFF)) {
                    GroupsUtil.displayGroupSet(groups0,"groups0",
                                               Level.FINE);
                    GroupsUtil.displayGroupSet(groups1,"groups1",
                                               Level.FINE);
                }//endif
            }//end loop
        }//end sync
    }//end displayUnequalGroupSets

    /** Common code shared by each test that needs to verify that the groups
     *  contained as values in the map0 parameter are also contained as 
     *  values in the map1 parameter. Note that the order of the parameters
     *  is important to determining containment.
     */
    protected void testGroupSetContainment(Map map0, Map map1)
                                                       throws TestException
    {
        synchronized(lockObject) {
            Map newMap0 = getRegToGroupSetMapping(map0);
            Map newMap1 = getRegToGroupSetMapping(map1);
	    Iterator iter = newMap0.keySet().iterator();
            for(int i=0;iter.hasNext();i++) {
                Object key = iter.next();
                String[] groups0 = (String[])newMap0.get(key);
                String[] groups1 = (String[])newMap1.get(key);
                if( (groups0 == null) || (groups1 == null) ) {
                    throw new TestException("failure -- "
                                         +" no mapping for lookup service "+i);
                }
                if( !GroupsUtil.compareGroupSets(groups0,groups1,Level.OFF) ) {
                    GroupsUtil.displayGroupSet(groups0,"groups0",
                                               Level.FINE);
                    GroupsUtil.displayGroupSet(groups1,"groups1",
                                               Level.FINE);
                    throw new TestException("failure -- "
                                   +" groups not equal for lookup service "+i);
                }
            }//end loop
        }//end sync
    }//end testGroupSetContainment

    /** Common code shared by each test that needs to verify that the groups
     *  contained as values in the map0 parameter are identical to the
     *  groups contained as values in the map1 parameter.
     */
    protected void testGroupSetEquality(Map map0, Map map1)
                                                       throws TestException
    {
        testGroupSetContainment(map0,map1);
        testGroupSetContainment(map1,map0);
    }//end testGroupSetEquality

    /** At least one of the maps maintained by this class maps multicast
     *  announcement generators to group sets; whereas other mappings
     *  maintained by this class map registrars to group sets. This method
     *  is used to "normalize" two different mappings so that the key sets
     *  of each are made up of registrars, not generators. In this way,
     *  the group sets contained in each mapping can be more easily
     *  extracted and compared.
     */
    private Map getRegToGroupSetMapping(Map map) {
        Map newMap = new HashMap();
	Iterator iter = map.keySet().iterator();
        for(int i=0;iter.hasNext();i++) {
            Object key = iter.next();
            if( !(key instanceof DiscoveryProtocolSimulator) ) return map;
            ServiceRegistrar reg = 
                           ((DiscoveryProtocolSimulator)key).getLookupProxy();
            String[] groups = (String[])map.get(key);
            newMap.put(reg,groups);
        }//end loop
        return newMap;
    }//end getRegToGroupSetMapping

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
	if (hostname == null) {
	    hostname = "localhost";
	    try {
		hostname = InetAddress.getLocalHost().getHostName();
	    } catch(UnknownHostException e) {
		e.printStackTrace();
	    }
	}
        return QAConfig.getConstrainedLocator(hostname, port);
    }

    /** Retrieves and stores the information needed to configure any lookup
     *  services that will be started for the current test run.
     */
    private void getLookupInfo() throws TestException {
        /* Retrieve the member groups & locator of each lookup to start */
        int totalNLookups = nLookupServices+nAddLookupServices;
        for(int i=0;i<totalNLookups;i++) {
            /* Member groups for lookup service i */
            String groupsArg = getConfig().getServiceStringProperty
                                    ("net.jini.core.lookup.ServiceRegistrar",
                                     "membergroups", i);
            String uniqueGroupsArg = config.makeGroupsUnique(groupsArg);
            String[] memberGroups = config.parseString(uniqueGroupsArg,",");
            if(memberGroups == DiscoveryGroupManagement.ALL_GROUPS) continue;
            memberGroupsList.add(memberGroups);
            /*Constrained Locator for lookup service i */
            lookupsToStart.add(getTestLocator(i));
        }//end loop
        /* If explicit ports were NOT set in config, then clear the set of
         * lookups to start so that arbitrary ports will be used.
         */
        if(lookupsToStart.size() != totalNLookups) lookupsToStart.clear();
    }//end getLookupInfo

    /* Retrieve (and display) configuration values for the current test */
    private void getSetupInfo() {
        /* begin harness info */
        logger.log(Level.FINE, "----- Harness Info ----- ");
        String harnessCodebase = System.getProperty("java.rmi.server.codebase",
                                                    "no codebase");
        logger.log(Level.FINE, "harness codebase      -- "
                                        +harnessCodebase);

        String harnessClasspath = System.getProperty("java.class.path",
                                                     "no classpath");
        logger.log(Level.FINE, "harness classpath     -- "
                                        +harnessClasspath);
        maxSecsEventWait = getConfig().getIntConfigVal
                                       ("net.jini.discovery.maxSecsEventWait",
                                         maxSecsEventWait);
        logger.log(Level.FINE, "max secs event wait   -- "
                          +maxSecsEventWait);
        /* end harness info */

        /* begin lookup info */
        logger.log(Level.FINE, "----- Lookup Service Info ----- ");
        implClassname = getConfig().getStringConfigVal
                                 ("net.jini.core.lookup.ServiceRegistrar.impl",
                                  "no implClassname");
        logger.log(Level.FINE, "lookup impl class     -- "
                          +implClassname);

        String genCodebase = System.getProperty("java.rmi.server.codebase",
                                                "No_Codebase");
        logger.log(Level.FINE, "generator codebase    -- "
                          +genCodebase);

        String codebase = getConfig().getStringConfigVal
                            ("net.jini.core.lookup.ServiceRegistrar.codebase",
                             null);
        logger.log(Level.FINE, "lookup codebase       -- "
                          +codebase);

        String classpath = getConfig().getStringConfigVal
                           ("net.jini.core.lookup.ServiceRegistrar.classpath",
                            null);
        logger.log(Level.FINE, "lookup classpath      -- "
                          +classpath);

        String policyFile = getConfig().getStringConfigVal
                          ("net.jini.core.lookup.ServiceRegistrar.policyfile",
                           "no policyFile");
        logger.log(Level.FINE, "lookup policy file    -- "
                          +policyFile);

        nLookupServices = getConfig().getIntConfigVal
                           ("net.jini.lookup.lookupdiscovery.nLookupServices",
                             nLookupServices);
        logger.log(Level.FINE, "# of lookup services to start            -- "
                          +nLookupServices);
        nAddLookupServices = getConfig().getIntConfigVal
                         ("net.jini.lookup.lookupdiscovery.nAddLookupServices",
                          nAddLookupServices);
        logger.log(Level.FINE, "# of additional lookup services to start -- "
                          +nAddLookupServices);


        nSecsLookupDiscovery = getConfig().getIntConfigVal
                      ("net.jini.lookup.lookupdiscovery.nSecsLookupDiscovery",
                        nSecsLookupDiscovery);
        logger.log(Level.FINE, "seconds to wait for discovery            -- "
                          +nSecsLookupDiscovery);
        /* Multicast announcement info - give priority to the command line */
        try {
            int sysInterval = Integer.getInteger
                                 ("net.jini.discovery.announce",0).intValue();
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
        logger.log(Level.FINE, "seconds between announcements            -- "
                          +(announceInterval/1000));
        minNAnnouncements = getConfig().getIntConfigVal
                         ("net.jini.lookup.lookupdiscovery.minNAnnouncements",
                           minNAnnouncements);
        nIntervalsToWait = getConfig().getIntConfigVal
                          ("net.jini.lookup.lookupdiscovery.nIntervalsToWait",
                            nIntervalsToWait);
        /* end lookup info */

        /* begin service info */
        logger.log(Level.FINE, "----- Lookup Discovery Service Info ----- ");
        String serviceImplClassname = getConfig().getStringConfigVal
                                                         (serviceName+".impl",
                                                          "no implClassname");
        logger.log(Level.FINE, "service impl class            -- "
                                        +serviceImplClassname);

        String serviceCodebase = getConfig().getStringConfigVal(serviceName+".codebase",
                                                      "no codebase");
        logger.log(Level.FINE, "service codebase              -- "
                                        +serviceCodebase);

        String serviceClasspath = getConfig().getStringConfigVal
                                                   (serviceName+".classpath",
                                                    "no classpath");
        logger.log(Level.FINE, "service classpath             -- "
                                        +serviceClasspath);

        String servicePolicyFile = getConfig().getStringConfigVal
                                                 (serviceName+".policyfile",
                                                  "no policyFile");
        logger.log(Level.FINE, "service policy file           -- "
                                        +servicePolicyFile);

        nRegistrations = getConfig().getIntConfigVal
                           ("com.sun.jini.test.spec.discoveryservice.nRegs",
                             nRegistrations);
        logger.log(Level.FINE, "# of registrations            -- "
                                        +nRegistrations);

        nAddRegistrations = getConfig().getIntConfigVal
                           ("com.sun.jini.test.spec.discoveryservice.nAddRegs",
                             nAddRegistrations);
        logger.log(Level.FINE, "# of additional registrations -- "
                                        +nAddRegistrations);
        /* end service info */

    }//end getSetupInfo

    /**
     * Administratively replaces the current set of groups in which the lookup
     * service (referenced by the <code>proxy</code> parameter) is a member
     * with a new set of member groups represented by the group names 
     * contained in the <code>groups</code> parameter.
     *
     * @param proxy   instance of the proxy to the lookup service whose
     *                current member groups are to be replaced
     * @param groups  String array whose elements are the names of the 
     *                member groups with which to replace the current
     *                member groups of the lookup service. If 
     *                <code>null</code> is input, no attempt will be
     *                made to replace the lookup service's current
     *                set of member groups. 
     *
     * @return <code>true</code> if the lookup service's set of member
     *         groups was successfully replaced; <code>false</code> otherwise.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the proxy and the
     *         service's backend. When this exception does occur, the
     *         set of groups to which the lookup service is a member may or
     *         may not have been successfully replaced.
     * 
     * @see net.jini.admin.Administrable
     * @see net.jini.admin.Administrable#getAdmin
     * @see net.jini.lookup.DiscoveryAdmin
     * @see net.jini.lookup.DiscoveryAdmin#setMemberGroups
     */
    protected boolean setLookupMemberGroups(ServiceRegistrar proxy,
                                                String[] groups)
                                                        throws RemoteException, TestException
    {
        if( (proxy == null) || (groups == null) ) return false;
        /* First, test that the lookup service implements both of the
         * appropriate administration interfaces
         */
        DiscoveryAdmin discoveryAdmin = null;
        if( !(proxy instanceof Administrable) ) return false;
        Object admin = ((Administrable)proxy).getAdmin();
//	try {
            admin = getConfig().prepare("test.reggieAdminPreparer", admin);
//	} catch (TestException e) {
//	    throw new RemoteException("Preparation error", e);
//	}
        if( !(admin instanceof DiscoveryAdmin) ) return false;
        discoveryAdmin = (DiscoveryAdmin)admin;
        /* Set the member groups for the lookup service */
        discoveryAdmin.setMemberGroups(groups);
        return true;
    }//end setLookupMemberGroups

    /**
     * Get a prepared lease from a registration. The fiddlerLeasePreparer
     * entry in the configuration is used for preparation.
     *
     * @param reg the <code>LookupDiscoveryRegistration</code> providing
     *            the lease
     * @return the prepared lease
     */
    protected Lease getPreparedLease(LookupDiscoveryRegistration reg) 
	throws RemoteException
    {
	Configuration c = config.getConfiguration();
	if (!(c instanceof com.sun.jini.qa.harness.QAConfiguration)) { // if none configuration
	    return reg.getLease();
	}
	try {
	    ProxyPreparer p = (ProxyPreparer) c.getEntry("test",
							 "fiddlerLeasePreparer",
							 ProxyPreparer.class);
	    Lease l = reg.getLease();
	    logger.log(Level.FINEST, "Returning prepared fiddler lease");
	    return (Lease) p.prepareProxy(l);
	} catch (ConfigurationException e) {
	    throw new RemoteException("Configuration Error", e);
	}
    }

    /** Determines if the given locators are equivalent.
     *
     *  This method is a convenience method that is called instead of calling
     *  only the <code>equals</code> method on <code>LookupLocator</code>.
     *  This is necessary because the <code>equals</code> method on
     *  <code>LookupLocator</code> performs a simple <code>String</code>
     *  compare of the host names referenced by the locators being compared.
     *  Such a comparison can result in a "false negative" when the hostname
     *  associated with one locator is a fully-qualified hostname
     *  (ex. "myhost.subdomain.mycompany.com"), but the hostname of the
     *  locator with which the first locator is being compared is only the
     *  unqualified hostname (ex. "myhost"). In this case, both host names
     *  are legal and functionally equivalent, but the <code>equals</code>
     *  method on <code>LookupLocator</code> will interpret them as unequal.
     *
     *  To address the problem described above, this method will do the 
     *  following when attempting to determine whether the given locators
     *  are equivalent:
     *
     *    1. Apply <code>LookupLocator</code>.<code>equals</code> to determine
     *       if the given locators are actually 'equal'.
     *    2. If <code>LookupLocator</code>.<code>equals</code> method returns
     *       <code>false</code>, then retrieve and compare the port and
     *       <code>InetAddress</code> of each element to the respective 
     *       ports and <code>InetAddress</code>s of the given locators.
     * 
     * @param locSet this method will determine whether or not the given
     *               locator is contained in this <code>Set</code> of
     *               <code>LookupLocator</code>s.
     * @param loc    this method will determine whether or not this
     *               <code>LookupLocator</code> is contained in the given set.
     * 
     * @return <code>true</code> if the given set of locators contains the
     *         given locator; <code>false</code> otherwise.
     */
    public static boolean locsEqual(LookupLocator loc0, LookupLocator loc1) {
        if( loc0.equals(loc1) ) return true;
        if( loc0.getPort() != loc1.getPort() ) return false;
        try {
            InetAddress addr0 = InetAddress.getByName(loc0.getHost());
            InetAddress addr1 = InetAddress.getByName(loc1.getHost());
            if( addr0.equals(addr1) ) return true;
        } catch(Exception e) {
            logger.log(Level.WARNING, "problem retrieving address by name", e);
            return false;
        }
        return false;
    }//end locsEqual

    /** Returns the registrars that were configured to be started and
     *  discovered.
     */
    protected ServiceRegistrar[] getRegsToDiscover(ArrayList useList) {
        ArrayList regsList = new ArrayList();
        for(int i=0;i<useList.size();i++) {
            ServiceRegistrar reg = 
                   ( ((DiscoveryStruct)(useList.get(i))).regGroups ).reg;
            if(reg == null)  continue;
            regsList.add(reg);
        }
        return (ServiceRegistrar[])(regsList).toArray
                                      (new ServiceRegistrar[regsList.size()]);
    }//end getRegsToDiscover

    protected ServiceRegistrar[] getRegsToDiscoverByIndex(int regIndex) {
        if( (regIndex%4) == 0 ) {
            return getRegsToDiscover(useGroupAndLocDiscovery0);
        } else if( (regIndex%4) == 1 ) {
            return getRegsToDiscover(useOnlyGroupDiscovery);
        } else if( (regIndex%4) == 2 ) {
            return getRegsToDiscover(useGroupAndLocDiscovery1);
        } else if( (regIndex%4) == 3 ) {
            return getRegsToDiscover(useOnlyLocDiscovery);
        } else {
            return getRegsToDiscover(useGroupAndLocDiscovery0);
        }//endif
    }//end getRegsToDiscoverByIndex

}//end class AbstractBaseTest
