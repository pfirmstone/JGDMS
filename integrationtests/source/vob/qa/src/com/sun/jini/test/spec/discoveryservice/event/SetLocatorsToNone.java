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

import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.test.share.DiscoveryProtocolSimulator;
import com.sun.jini.test.share.GroupsUtil;
import com.sun.jini.test.share.LocatorsUtil;

import net.jini.discovery.LookupDiscoveryRegistration;

import net.jini.core.discovery.LookupLocator;
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
 * This class verifies that the lookup discovery service operates in a manner
 * consistent with the specification. In particular, this class verifies
 * that when a registered client requests that the lookup discovery service
 * replace all locators-of-interest with 'NO_LOCATORS', that the lookup
 * discovery service effectively turns off discovery by locator.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each associated with a locator
 *   <li> one instance of the lookup discovery service
 *   <li> one or more registrations with the lookup discovery service
 *   <li> each registration with the lookup discovery service requests that
 *        the lookup services be discovered through only locator discovery
 *   <li> each registration with the lookup discovery service will receive
 *        remote discovery events through an instance of RemoteEventListener
 *   <li> each registration with the lookup discovery service invokes the
 *        setLocators() method -- requesting that the set of locators-of-
 *        interest be replaced with the emtpy set
 *   <li> each registration with the lookup discovery service will receive
 *        remote discard events through an instance of RemoteEventListener
 * </ul><p>
 * 
 * If the lookup discovery service utility functions as specified, then
 * for each discovered lookup service, a <code>RemoteDiscoveryEvent</code>
 * instance indicating a discovered event will be sent to the listener of
 * each registration that requested discovery of the lookup service. And
 * upon invoking the setLocators() method, for each discovered lookup service,
 * a <code>RemoteDiscoveryEvent</code> instance indicating a discarded event
 * will be sent to the listener of each registration that originally requested
 * discovery of the lookup service.
 *
 * Related bug ids: 5042473
 * 
 */
public class SetLocatorsToNone extends AbstractBaseTest {

    protected static Logger logger = 
                            Logger.getLogger("com.sun.jini.qa.harness.test");
    protected LookupLocator[] newLocatorsToDiscover = new LookupLocator[0];
    protected HashMap regInfoMap = registrationMap;

    /** Retrieves additional configuration values. */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
//      debugFlag = true;
//      displayOn = true;
        useDiscoveryList = useOnlyLocDiscovery;
        discardType      = ACTIVE_DISCARDED;
    }//end setup

    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        for(int i=0;i<nRegistrations;i++) {
            logger.log(Level.FINE, 
                      "lookup discovery service registration_"+i+" --");
            doRegistration
                      (net.jini.discovery.DiscoveryGroupManagement.NO_GROUPS,
                       getLocatorsToDiscover(useDiscoveryList),
                       i, leaseDuration);
        }//end loop
        logger.log(Level.FINE, "waiting for initial discovery");
        waitForDiscovery();
        logger.log(Level.FINE, 
                   "replacing the locators to discover with the EMPTY set");
        setLocatorsDo(newLocatorsToDiscover);
        logger.log(Level.FINE, "waiting for discard events");
        waitForDiscard(discardType);
    }//end run

    /** Invokes the setLocators() method on each registration. */
    void setLocatorsDo(LookupLocator[] newLocators) throws Exception {
        Set eSet = regInfoMap.entrySet();
        Iterator iter = eSet.iterator();
        for(int j=0;iter.hasNext();j++) {
            Map.Entry pair = (Map.Entry)iter.next();
            LookupDiscoveryRegistration ldsReg =
                                    (LookupDiscoveryRegistration)pair.getKey();

            LDSEventListener regListener = (LDSEventListener)pair.getValue();
            RegistrationInfo regInfo = regListener.getRegInfo();
            int rID = regInfo.handback;
	    logger.log(Level.FINE, 
		       "  registration_"+rID
		       +" -- request discovery of new locators");
	    
	    if((newLocators != null)&&(newLocators.length <= 0)) {
		logger.log(Level.FINE, "   NO_LOCATORS");
	    } else {
		LocatorsUtil.displayLocatorSet(newLocators,
                                               "   newLocator",Level.FINE);
	    }//endif
	    setExpectedDiscardedMap(regInfo);
	    ldsReg.setLocators(newLocators);
        }//end loop(j)
    }//end setLocatorsDo

    /** Populates the map containing the locators that are expected to be
     *  discarded, based on what was originally discovered and what is
     *  now supposed to be discovered after the call to setLocators().
     */
    void setExpectedDiscardedMap(RegistrationInfo regInfo) {
        Map locMap = getModLocatorsDiscardMap(useDiscoveryList);
        Map expectedMap = getExpectedDiscardedMap(regInfo,discardType);
        Set kSet = expectedMap.keySet();
        Iterator iter = kSet.iterator();
        for(int j=0;iter.hasNext();j++) {
            ServiceRegistrar lookupProxy = (ServiceRegistrar)iter.next();
            if(    !locMap.containsKey(lookupProxy)
                || ((regInfo.locatorsToDiscover).length == 0) )
            {
                iter.remove();
	    }//endif
	}//end loop
    }//end setExpectedDiscardedMap

}//end class SetLocatorsToNone

