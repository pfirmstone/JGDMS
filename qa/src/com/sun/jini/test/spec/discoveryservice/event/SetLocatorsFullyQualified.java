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
import com.sun.jini.qa.harness.Test;

import com.sun.jini.test.share.GroupsUtil;
import com.sun.jini.test.share.LocatorsUtil;

import net.jini.discovery.LookupDiscoveryRegistration;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.ConstrainableLookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

import java.io.IOException;
import java.net.InetAddress;
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
 * that when a registered client calls setLocators() with locators having
 * fully-qualified host names, the lookup discovery service will successfully
 * discover the associated lookup services.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one or more lookup services, each associated with a locator
 *   <li> one instance of the lookup discovery service
 *   <li> one or more registrations with the lookup discovery service, each
 *        initially configured to discover no lookup services
 *   <li> each registration with the lookup discovery service invokes the
 *        setLocators() method -- requesting that the set of locators-of-
 *        interest be replaced with a set in which each element is a locator
 *        having a fully-qualified host name
 *   <li> each registration with the lookup discovery service will receive
 *        remote discovery events through an instance of RemoteEventListener
 * </ul><p>
 * 
 * If the lookup discovery service functions as specified, then for each
 * discovered lookup service, a <code>RemoteDiscoveryEvent</code> instance
 * indicating a discovered event will be sent to the listener of each
 * registration that requested discovery of the lookup service.
 *
 * Related bug ids: 4979612
 * 
 */
public class SetLocatorsFullyQualified extends AbstractBaseTest {

    protected static Logger logger = 
                            Logger.getLogger("com.sun.jini.qa.harness.test");
    protected LookupLocator[] locsStarted;
    protected LookupLocator[] locsToSet;
    protected LookupLocator[] expectedLocs;
    protected HashMap regInfoMap = registrationMap;

    /** Retrieves additional configuration values. */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
//      debugFlag = true;
//      displayOn = true;
        useDiscoveryList = useOnlyLocDiscovery;
        locsStarted = getLocatorsToDiscover(useDiscoveryList);
        locsToSet   = new LookupLocator[locsStarted.length];
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
        for(int i=0; i<locsStarted.length; i++) {
            String host = (locsStarted[i].getHost()).toLowerCase();
            if( host.indexOf(domain) == -1 ) {//host does not contain domain
                host = new String(host+"."+domain);//add domain
            }//endif
            if (locsStarted[i] instanceof ConstrainableLookupLocator) {
                locsToSet[i] = new ConstrainableLookupLocator(
                    host, locsStarted[i].getPort(),
                    ((ConstrainableLookupLocator)locsStarted[i]).getConstraints());
            } else {
                locsToSet[i] = new LookupLocator(host,locsStarted[i].getPort());
            }
            logger.log(Level.FINE, "locsToSet["+i+"] = "+locsToSet[i]);
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
        logger.log(Level.FINE, "setting the new locators to discover");
        /* Invoke the setLocators() method on each registration and set the
         * locators that are expected to be discovered, based on the locators
         * that are set.
         */
        setLocatorsAllRegs(locsToSet);
        logger.log(Level.FINE, "waiting for discovery events");
        waitForDiscovery();
    }//end run

}//end class SetLocatorsFullyQualified

