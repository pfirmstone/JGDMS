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

package com.sun.jini.test.spec.locatordiscovery;

import java.util.logging.Level;

import com.sun.jini.test.share.BaseQATest;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupLocatorDiscovery;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is an abstract class that acts as the base class from which
 * most, if not all, tests of the <code>LookupLocatorDiscovery</code> utility
 * class should extend.
 * <p>
 * This abstract class contains at least one static inner class that can be
 * used as a listener to participate in the process of discovering lookup
 * services on behalf of the tests that are decendants of this abstract class.
 * <p>
 * This class provides an implementation of the <code>setup</code> method
 * which performs standard functions related to the initialization of the
 * system state necessary to execute the test.
 * <p>
 * Any test class that extends this class is required to implement the 
 * <code>run</code> method which defines the actual functions that must
 * be executed in order to verify the assertions addressed by that test.
 * <p>
 * Note that for tests that require lookup services to be started, if the
 * test configuration indicates that random ports are to be used when 
 * starting those lookup services, then the test must be implemented so
 * that those lookup services are started before discovery event processing
 * commences. This is so that the actual port numbers can be retrieved
 * and inserted into the expected event information. Thus, any test that
 * starts the necessary lookup services before discovery event processing
 * begins has the option of using random ports or explicit ports for the
 * lookup services that it starts. On the other hand, any test requiring
 * that its lookup services be started while discovery event processing is
 * in progress must be configured for explicit, pre-assigned, port numbers.
 *
 */
abstract public class AbstractBaseTest extends BaseQATest {

    protected volatile LookupLocatorDiscovery locatorDiscovery = null;
    protected final List locatorDiscoveryList = Collections.synchronizedList(new ArrayList(1));
    protected volatile LookupListener mainListener = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *    <li> retrieves configuration values needed by the current test
     *    <li> starts the desired number lookup services (if any) with
     *         the desired configuration
     *    <li> creates an instance of lookup locator discovery to start the
     *         unicast discovery process
     *    <li> creates a default listener for use with the lookup locator
     *         discovery utility
     * </ul>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        try {
            /* Start locator discovery by creating a lookup locator discovery*/
            logger.log(Level.FINE, "creating a lookup locator discovery "
                              +"initially configured to discover NO_LOCATORS");
            /* discover no locs at first, wait for test to call setLocators */
            locatorDiscovery 
                   = new LookupLocatorDiscovery(new LookupLocator[0],
						sysConfig.getConfiguration());
            locatorDiscoveryList.add(locatorDiscovery);
            mainListener = new LookupListener();
        } catch(Exception e) {
            e.printStackTrace();
            throw new Exception(e.toString());
        }
    }//end setup

    /** Executes the current test
     */
    abstract public void run() throws Exception;

    /** Cleans up all state. Terminates the lookup locator discovery utilities
     *  that may have been created, shutdowns any lookup service(s) that may
     *  have been started, and performs any standard clean up duties performed
     *  in the super class.
     */
    public void tearDown() {
        try {
            /* Terminate each lookup locator discovery utility */
            for(int i=0;i<locatorDiscoveryList.size();i++) {
                DiscoveryManagement lld
                          = (DiscoveryManagement)locatorDiscoveryList.get(i);
                logger.log(Level.FINE, "tearDown - terminating "
                                  +"lookup locator discovery "+i);
                lld.terminate();
            }//end loop
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
	    super.tearDown();
	}
    }//end tearDown

    /** Convenience method that encapsulates basic discovery processing.
     *  This method is useful when a lookup locator discovery utility different
     *  from the standard one created during setup is to be used for discovery.
     *  
     *  This method does the following:
     *  <p><ul>
     *   <li> uses the contents of the given ArrayList to set the lookps
     *        expected to be discovered for the given listener
     *   <li> with respect to the given listener, starts the discovery process
     *        by adding that listener to the given lookup locator discovery
     *        utility
     *   <li> verifies that the discovery process is working by waiting
     *        for the expected discovered events
     *  </ul>
     *  @throws com.sun.jini.qa.harness.TestException
     */
    protected void doDiscovery(List locGroupsListToDiscover,
                               LookupLocatorDiscovery lld,
                               LookupListener listener) throws TestException
    {
        logger.log(Level.FINE, "set locators to discover -- ");
        LookupLocator[] locsToDiscover = toLocatorArray
                                                    (locGroupsListToDiscover);
        for(int i=0;i<locsToDiscover.length;i++) {
            logger.log(Level.FINE, "   "+locsToDiscover[i]);
        }//end loop
        /* Set the expected locators to discover */
        listener.setLookupsToDiscover(locGroupsListToDiscover);
        /* Re-configure LookupLocatorDiscovery to discover given locators */
        lld.setLocators(locsToDiscover);
        /* Add the given listener to the LookupLocatorDiscovery utility */
        lld.addDiscoveryListener(listener);
        /* Wait for the discovery of the necessary lookup service(s) */
        waitForDiscovery(listener);
    }//end doDiscovery

    /** Convenience method that encapsulates basic discovery processing.
     *  Use this method when the standard lookup locator discovery utility
     *  created during setup is to be used for discovery.
     *  @throws com.sun.jini.qa.harness.TestException
     */
    protected void doDiscovery(List locGroupsListToDiscover,
                               LookupListener listener) throws TestException
    {
        doDiscovery(locGroupsListToDiscover,locatorDiscovery,listener);
    }//end doDiscovery

}//end class AbstractBaseTest


