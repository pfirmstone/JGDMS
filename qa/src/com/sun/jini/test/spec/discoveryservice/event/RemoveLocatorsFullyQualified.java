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

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.share.LocatorsUtil;

import net.jini.discovery.LookupDiscoveryRegistration;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.ConstrainableLookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This class verifies that the lookup discovery service operates in a 
 * manner consistent with the specification. In particular, on behalf of
 * a registered client, this class registers with the discovery service,
 * a set of locators-of-interest in which pairs of locators in the set are 
 * equivalent; that is, one is created using a fully-qualified host name,
 * and the other is created with an un-qualified host name. Thus, for
 * each lookup service to be discovered, the set of locators to discover
 * contains two, equivalent locators that correspond to the lookup service.
 * This class verifies that the discovery service behaves correctly with
 * respect to discovery. That is, although there are two locators for each
 * lookup to discover, only one discovery event is sent for each locator pair.
 *
 * After discovery processing has been verified, this class verifies that
 * the discovery service behaves correctly with respect to the discard
 * process in the face of the removal of the locators. That is, when only
 * one of each locator pair in the set of locators to discover is removed
 * from that set for a given registration, no discard events are sent by the
 * discovery service. And then, upon removal of the remaining, equivalent
 * locators, the appropriate discarded events are sent by the discovery
 * service.
 *
 * NOTE:
 *
 * Any particular lookup service returns a single locator; which may return
 * a host name that is fully-qualified, un-qualified, or even an IP address.
 * The form the locator's host name takes is typically dependent on the
 * configuration of the system to which the lookup service belongs (for
 * example, the operationg system, the naming service (DNS, NIS, etc.), the
 * configuration of /etc/hosts, c:\winnt\system32\drivers\etc\hosts, etc.).
 *
 * Because of this, the discovery service must allow clients to register
 * for discovery-by-locator using locators having different forms of the
 * host name. That is, when a client registers with the discovery service
 * for locator discovery, the discovery service allows the client to
 * register 'many-locators-for-one-lookup'. Thus, even though a particular
 * host on which a lookup service is running, is configured with (is known by)
 * multiple names and/or IP addresses, the client does not have to 
 * 'guess' -- or know apriori -- the single name/address the lookup's 
 * locator will actually return in order to discover that lookkup service
 * through locator discovery.
 *
 * For example, suppose a lookup service runs on a host with two network
 * interface cards (NICs), one with IP address 168.1.2.3, and the other
 * with address 192.1.2.3. Suppose that the host belongs to the domain
 * 'mycompany.com', and that additionally, the host is known by the name
 * 'host1' as well as the name 'host2'. Finally, assume that the host
 * is configured in such a way that when the getHost() method on the 
 * lookup service's associated locater is invoked, the value that is 
 * returned is, 'host2.mycompany.com'. That is,
 *
 *     Host/Address                       Lookup Service's Locator
 *     ------------                       ------------------------
 *    
 *    host1 -----------------\
 *    host1.mycompany.com --- \
 *    168.1.2.3 -------------  \
 *                              | -----> jini://host2.mycompany.com:4160
 *    host2 -----------------  /
 *    host2.mycompany.com --- /
 *    192.1.2.3 -------------/
 *
 * The discovery service should then allow clients to register for
 * discovery, a set of locators created from any combination of the
 * possible hosts/addresses in the left column, and the result will be
 * a single discovery event for the lookup service with the locator
 * in the right column.
 *
 * Upon removal of the locators from the discovery service for a given
 * registration, no discarded event will be sent until all locators
 * associated with the hosts/addresses in the left column are removed
 * from the discovery service for that registration.
 *
 * This test verifies that the discovery service behaves as described above.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each associated with a locator
 *   <li> one instance of the lookup discovery service
 *   <li> one or more registrations with the lookup discovery service, each
 *        initially configured to none of the lookup services
 *   <li> each registration with the lookup discovery service invokes the
 *        addLocators() method a first time, inputting a set of locators
 *        created from host names that are equivalent to the host names
 *        of the locators of each lookup service that was started
 *   <li> receipt of the expected discovery events is then verified
 *   <li> each registration with the lookup discovery service invokes the
 *        addLocators() method a second time, inputting a set of locators
 *        created from host names that are equivalent (but not equal by
 *        String comparison) to the host names of the locators input to the
 *        first call to addLocators()
 *   <li> verification that no discovery events are received is then performed
 *   <li> each registration with the lookup discovery service invokes the
 *        removeLocators() method a first time, inputting a set of locators
 *        created from host names that are equivalent to the host names of
 *        the set of locators input to one of the calls to addLocators()
 *   <li> verification that no discarded events are received is then performed
 *   <li> each registration with the lookup discovery service invokes the
 *        removeLocators() method a second time, inputting a set of locators
 *        created from host names that are equivalent to the host names of
 *        the set of locators input to the other call to addLocators()
 *   <li> receipt of the expected discarded events is then verified
 *
 * Related bug ids: 4979612
 * 
 */
public class RemoveLocatorsFullyQualified extends AbstractBaseTest {

    protected static Logger logger = 
                            Logger.getLogger("com.sun.jini.qa.harness.test");
    protected volatile LookupLocator[] unqualifiedLocs;
    protected volatile LookupLocator[] qualifiedLocs;
    protected volatile ServiceRegistrar[] lookupsStarted = new ServiceRegistrar[0];

    /** Retrieves additional configuration values. */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
//      debugFlag = true;
//      displayOn = true;
        useDiscoveryList = getUseOnlyLocDiscovery();
        discardType      = ACTIVE_DISCARDED;

        lookupsStarted = getRegsToDiscover(useDiscoveryList);

        unqualifiedLocs   = getLocatorsToDiscover(useDiscoveryList);
        qualifiedLocs  = new LookupLocator[unqualifiedLocs.length];
        String domain = ( config.getStringConfigVal("com.sun.jini.jsk.domain",
                                                    null) ).toLowerCase();
        /* This test wishes to discover-by-locator, each of the lookup services
         * that were configured to be started for this test. Thus, the set of
         * locators to discover that is supplied to the lookup discovery
         * service is created using the host and port of the locators of
         * the lookups that are started. One difference is that the host
         * names used to create the locators supplied to the lookup discovery
         * service should each be fully-qualified names; that is, containing
         * the domain of the host. 
         *
         * The loop below constructs that set of locators; extracting the
         * host and port of the locator of each lookup service that was
         * started, concatenating the host and domain when appropriate, and
         * using the fully-qualified host and port to create the locator to
         * discover.
         */
        for(int i=0; i<unqualifiedLocs.length; i++) {
            String host = (unqualifiedLocs[i].getHost()).toLowerCase();
            if( host.indexOf(domain) == -1 ) {//host does not contain domain
                host = new String(host+"."+domain);//add domain
            }//endif
            if (unqualifiedLocs[i] instanceof ConstrainableLookupLocator) {
                qualifiedLocs[i] = new ConstrainableLookupLocator(
                    host,unqualifiedLocs[i].getPort(),
                    ((ConstrainableLookupLocator)unqualifiedLocs[i]).getConstraints());
            } else {
                qualifiedLocs[i] = new LookupLocator
                                           (host,unqualifiedLocs[i].getPort());
            }
            logger.log(Level.FINE, "qualifiedLocs["+i+"] = "+qualifiedLocs[i]);
        }//end loop
        return this;
    }//end construct

    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Register with the discovery service, initially requesting the 
         * discovery of NO_GROUPS and NO_LOCATORS.
         */
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, 
                      "lookup discovery service registration_"+i+" --");
            doRegistration(DiscoveryGroupManagement.NO_GROUPS,
                           new LookupLocator[0],
                           i, leaseDuration);
        }//end loop

        /* 1. Do initial discovery by setting the locators to discover using
         *    un-qualified host names in the locators.
         */
        logger.log(Level.FINE, "Discovery 1: add locators with "
                               +"un-qualified host names");
        addLocatorsDo(unqualifiedLocs);
        logger.log(Level.FINE, "locators set - waiting for discovered events");
        waitForDiscovery();

        /* 2. Add the same locators as above, but use fully-qualified host
         *    names. For each registration with the discovery service, the
         *    discovery service should then have two equivalent locators
         *    corresponding to each lookup that was started. In this case,
         *    there should be no discovery events receive, since those lookups
         *    were already discovered above.
         */
        logger.log(Level.FINE, "Discovery 2: add locators with "
                               +"fully-qualified host names");
        addLocatorsDo(qualifiedLocs);
        logger.log(Level.FINE,
                   "locators added - waiting for discovered events");
        waitForDiscovery();

        /* 3. Remove the locators with un-qualified host names. Since there
         *    are still locators registered with the discovery service that
         *    correspond to the lookups discovered above, there should be 
         *    no discard events upon removal.
         */
        logger.log(Level.FINE, "Discard 1: remove locators with "
                               +"un-qualified host names");
        removeLocatorsDo(unqualifiedLocs);
        logger.log(Level.FINE,
                   "locators removed - waiting for discarded events");
        waitForDiscard(discardType);

        /* 4. Remove the locators with fully-qualified host names. After
         *    performing the locator removal above, for each discovered
         *    lookup, there should be only 1 corresponding locator left.
         *    Thus, discarded events should be sent by the discovery service
         *    removal.
         */
        logger.log(Level.FINE, "Discard 2: remove locators with "
                               +"fully-qualified host names");
        removeLocatorsDo(qualifiedLocs);
        logger.log(Level.FINE,
                   "locators removed - waiting for discarded events");
        waitForDiscard(discardType);
    }//end run

    /** For each registration with the discovery service, this method adds
     *  the given locators to the set of locators that should be discovered.
     */
    void addLocatorsDo(LookupLocator[] locators)   throws Exception
    {
        Set eSet = getRegistrationMap().entrySet();
        Iterator iter = eSet.iterator();
        for(int j=0;iter.hasNext();j++) {
            Map.Entry pair = (Map.Entry)iter.next();
            LookupDiscoveryRegistration ldsReg =
                                    (LookupDiscoveryRegistration)pair.getKey();
            LDSEventListener regListener = (LDSEventListener)pair.getValue();
            RegistrationInfo regInfo = regListener.getRegInfo();
	    setExpectedDiscoveredMap(regInfo,ldsReg);
            ldsReg.addLocators(locators);
        }//end loop(j)
    }//end addLocatorsDo

    /** For each registration with the discovery service, this method removes
     *  the given locators from the set of locators that should be discovered.
     */
    void removeLocatorsDo(LookupLocator[] locators) throws Exception {
        Set eSet = getRegistrationMap().entrySet();
        Iterator iter = eSet.iterator();
        for(int j=0;iter.hasNext();j++) {
            Map.Entry pair = (Map.Entry)iter.next();
            LookupDiscoveryRegistration ldsReg =
                                    (LookupDiscoveryRegistration)pair.getKey();
            LDSEventListener regListener = (LDSEventListener)pair.getValue();
            RegistrationInfo regInfo = regListener.getRegInfo();
	    setExpectedDiscardedMap(regInfo,ldsReg);
	    ldsReg.removeLocators(locators);
        }//end loop(j)
    }//end removeLocatorsDo

    /* For the given registration, this method populates the corresponding
     * maps that contain the currently discovered lookups and the lookups
     * that are expected to be discovered. If a lookup that was started for
     * this test has already been discovered, then it is placed in the
     * registration's discoveredMap; otherwise, it is expected to be
     * discovered.
     *
     * Note that if all of the lookups that were started have already been
     * discovered, then this method clears the discoveredMap -- indicating
     * that no discovered events are expected -- so that the method
     * AbstractBaseTest.waitForDiscovery() can work correctly.
     */
    void setExpectedDiscoveredMap(RegistrationInfo regInfo, 
                                  LookupDiscoveryRegistration ldsReg)
                                                              throws Exception
    {
        regInfo.resetDiscoveryEventInfo();//clear discovered & expected maps
        HashSet lookupsDiscovered = 
                         new HashSet( Arrays.asList(ldsReg.getRegistrars()) );
        /* if a started lookup has not been discovered, then expect an event */
        for(int i=0;i<lookupsStarted.length;i++) {
            String[] groups = lookupsStarted[i].getGroups();
            if( !lookupsDiscovered.contains(lookupsStarted[i]) ) {
                synchronized(regInfo) {
                    regInfo.getExpectedDiscoveredMap().put
                                               ( lookupsStarted[i],groups );
                }//end sync
            } else {
                synchronized(regInfo) {
                    regInfo.getDiscoveredMap().put( lookupsStarted[i], groups );
                }//end sync               
            }//endif
        }//end loop
        synchronized(regInfo) {
             if(regInfo.getExpectedDiscoveredMap().size() == 0) {
                 regInfo.getDiscoveredMap().clear();//expect no more discovery
             }//endif
        }//end sync               
    }//end setExpectedDiscoveredMap

    /* For the given registration, this method populates the registration's
     * discoveredMap with the lookups that are currently discovered
     * by the discovery service. This is done so that the method
     * AbstractBaseTest.waitForDiscard() can work correctly.
     */
    void setDiscoveredMapForDiscard(RegistrationInfo regInfo, 
                                    LookupDiscoveryRegistration ldsReg,
                                    ServiceRegistrar[] discoveredLookups)
                                                              throws Exception
    {
        synchronized(regInfo) {
             if(regInfo.getExpectedDiscoveredMap().size() == 0) {
                 regInfo.getDiscoveredMap().clear();
             }//endif
        }//end sync               
        HashSet lookupsDiscovered = 
                             new HashSet( Arrays.asList(discoveredLookups) );
        for(int i=0;i<lookupsStarted.length;i++) {
            String[] groups = lookupsStarted[i].getGroups();
            if( lookupsDiscovered.contains(lookupsStarted[i]) ) {
                synchronized(regInfo) {
                    regInfo.getDiscoveredMap().put( lookupsStarted[i], groups );
                }//end sync               
            }//endif
        }//end loop
    }//end setDiscoveredMapForDiscard

    /* For the given registration, this method populates the registration's
     * map that contains the lookups that are expected to be discarded. 
     * This method decides whether or not to place a lookup in the 
     * map by determining how many equivalent locators registered for 
     * discovery by the discovery service correspond to each discovered
     * lookup. For each discovered lookup, if the registration's set of
     * locators-of-interest contains only 1 locator corresponding to that
     * lookup, then the lookup is placed in the map.
     */
    void setExpectedDiscardedMap(RegistrationInfo regInfo, 
                                 LookupDiscoveryRegistration ldsReg)
                                                              throws Exception
    {
        /* Get the locators that the given registration currently wishes the
         * discovery service to discover for it. Note that there may be
         * multiple, equivalent locators corresponding to the lookups that
         * have been started/discovered; that is, there can be a many-to-one
         * relationship between the locators-of-interest and the actual
         * lookup service.
         */
        LookupLocator[] locsToDiscover = ldsReg.getLocators();
        LocatorsUtil.displayLocatorSet(locsToDiscover,
				       "  locsToDiscover",
                                       Level.FINE);

        /* Get the lookups that are currently discovered by the discovery
         * service and place those lookups in the registration's discoveredMap.
         * This must be done because AbstractBaseTest.waitForDiscard()
         * uses the contents of the discoveredMap to determine whether
         * or not a discarded event from the discovery service should be
         * processed.
         */
        ServiceRegistrar[] lookupsDiscovered = ldsReg.getRegistrars();
        setDiscoveredMapForDiscard(regInfo,ldsReg,lookupsDiscovered);

       /* If there's only 1 loc of interest (corresponding to one of the
        * already-discovered lookups) registered with the lds for the given
        * registration, then add to the expected discard map; because only
        * then will a discard event be sent upon removal of the locator.
        */
        regInfo.resetDiscardEventInfo();
        for(int i=0;i<lookupsDiscovered.length;i++) {
            LookupLocator discoveredLoc = lookupsDiscovered[i].getLocator();
            int nLocMatches = 0;
            for(int j=0;j<locsToDiscover.length;j++) {
                if( locsEqual(discoveredLoc, locsToDiscover[j]) ) {
                    nLocMatches = nLocMatches+1;
                }//endif
            }//end loop
	    logger.log(Level.FINE,"  For locator "+discoveredLoc
                                  +" - # of matching locs = "+nLocMatches);
            if(nLocMatches == 1) {//then removal should result in discard event
                synchronized(regInfo) {
                    regInfo.getExpectedActiveDiscardedMap().put
                                         ( lookupsDiscovered[i],
                                           lookupsDiscovered[i].getGroups() );
                }//end sync
            }//endif
        }//end loop
    }//end setExpectedDiscardedMap

}//end class RemoveLocatorsFullyQualified

