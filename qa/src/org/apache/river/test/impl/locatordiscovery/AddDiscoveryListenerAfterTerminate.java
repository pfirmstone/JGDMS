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

package org.apache.river.test.impl.locatordiscovery;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.locatordiscovery.AbstractBaseTest;

import net.jini.core.discovery.LookupLocator;

/**
 * With respect to the current implementation of the
 * <code>LookupLocatorDiscovery</code> utility, this class verifies
 * that if the <code>addDiscoveryListener</code> method is invoked
 * after the lookup locator discovery utility has been terminated, an
 * <code>IllegalStateException</code> results.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> one instance of LookupLocatorDiscovery
 *   <li> after invoking the terminate method on the lookup locator
 *        discovery utility, the addDiscoveryListener method is invoked
 * </ul><p>
 * 
 * If the <code>LookupLocatorDiscovery</code> utility functions as intended,
 * upon invoking the <code>addDiscoveryListener</code> method after the
 * that utility has been terminated, an <code>IllegalStateException</code>
 * will occur.
 *
 */
public class AddDiscoveryListenerAfterTerminate extends AbstractBaseTest {

    protected final static int ADD_DISCOVERY_LISTENER    = 0;
    protected final static int REMOVE_DISCOVERY_LISTENER = 1;
    protected final static int GET_REGISTRARS            = 2;
    protected final static int DISCARD                   = 3;
    protected final static int GET_LOCATORS              = 4;
    protected final static int ADD_LOCATORS              = 5;
    protected final static int SET_LOCATORS              = 6;
    protected final static int REMOVE_LOCATORS           = 7;
    protected final static int GET_DISCOVERED_LOCATORS   = 8;
    protected final static int GET_UNDISCOVERED_LOCATORS = 9;

    protected String[] methodStr = { ": calling addDiscoveryListener ...",
                                     ": calling removeDiscoveryListener ...",
                                     ": calling getRegistrars ...",
                                     ": calling discard ...",
                                     ": calling getLocators ...",
                                     ": calling addLocators ...",
                                     ": calling setLocators ...",
                                     ": calling removeLocators ...",
                                     ": calling getDiscoveredLocators ...",
                                     ": calling getUndiscoveredLocators ..."};

    protected int methodType = ADD_DISCOVERY_LISTENER;

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> invokes the terminate method on the lookup locator discovery
     *         utility
     *    <li> invokes the addDiscoveryListener method on the lookup locator
     *         discovery utility
     *    <li> verifies that an IllegalStateException is thrown when the
     *         attempt to add the listener is made
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        logger.log(Level.FINE, "terminating lookup locator discovery ...");
        locatorDiscovery.terminate();
        logger.log(Level.FINE, methodStr[methodType]);
        try {
            switch(methodType) {
                case ADD_DISCOVERY_LISTENER:
                    locatorDiscovery.addDiscoveryListener(mainListener);
                    break;
                case REMOVE_DISCOVERY_LISTENER:
                    locatorDiscovery.removeDiscoveryListener(mainListener);
                    break;
                case GET_REGISTRARS:
                    locatorDiscovery.getRegistrars();
                    break;
                case DISCARD:
                    locatorDiscovery.discard(null);
                    break;
                case GET_LOCATORS:
                    locatorDiscovery.getLocators();
                    break;
                case ADD_LOCATORS:
                    locatorDiscovery.addLocators(new LookupLocator[0]);
                    break;
                case SET_LOCATORS:
                    locatorDiscovery.setLocators(new LookupLocator[0]);
                    break;
                case REMOVE_LOCATORS:
                    locatorDiscovery.removeLocators(new LookupLocator[0]);
                    break;
                case GET_DISCOVERED_LOCATORS:
                    locatorDiscovery.getDiscoveredLocators();
                    break;
                case GET_UNDISCOVERED_LOCATORS:
                    locatorDiscovery.getUndiscoveredLocators();
                    break;
            }//end switch(methodType)
            throw new TestException("no IllegalStateException");
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, "IllegalStateException thrown as expected");
        }
    }
}

