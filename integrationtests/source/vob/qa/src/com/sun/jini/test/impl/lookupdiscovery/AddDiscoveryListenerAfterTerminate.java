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

package com.sun.jini.test.impl.lookupdiscovery;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.lookupdiscovery.AbstractBaseTest;

/**
 * With respect to the current implementation of the
 * <code>LookupDiscovery</code> utility, this class verifies
 * that if the <code>addDiscoveryListener</code> method is invoked
 * after the lookup discovery utility has been terminated, an
 * <code>IllegalStateException</code> results.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> one instance of LookupDiscovery
 *   <li> after invoking the terminate method on the lookup discovery utility,
 *        the addDiscoveryListener method is invoked
 * </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as intended,
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
    protected final static int GET_GROUPS                = 4;
    protected final static int ADD_GROUPS                = 5;
    protected final static int SET_GROUPS                = 6;
    protected final static int REMOVE_GROUPS             = 7;

    protected String[] methodStr = { ": calling addDiscoveryListener ...",
                                     ": calling removeDiscoveryListener ...",
                                     ": calling getRegistrars ...",
                                     ": calling discard ...",
                                     ": calling getGroups ...",
                                     ": calling addGroups ...",
                                     ": calling setGroups ...",
                                     ": calling removeGroups ..." };

    protected int methodType = ADD_DISCOVERY_LISTENER;

    /** Executes the current test by doing the following:
     * <p><ul>
     *    <li> invokes the terminate method on the lookup discovery utility
     *    <li> invokes the addDiscoveryListener method on the lookup
     *         discovery utility
     *    <li> verifies that an IllegalStateException is thrown when the
     *         attempt to add the listener is made
     *   </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        logger.log(Level.FINE, "terminating lookup discovery ...");
        lookupDiscovery.terminate();
        logger.log(Level.FINE, methodStr[methodType]);
        try {
            switch(methodType) {
                case ADD_DISCOVERY_LISTENER:
                    lookupDiscovery.addDiscoveryListener(mainListener);
                    break;
                case REMOVE_DISCOVERY_LISTENER:
                    lookupDiscovery.removeDiscoveryListener(mainListener);
                    break;
                case GET_REGISTRARS:
                    lookupDiscovery.getRegistrars();
                    break;
                case DISCARD:
                    lookupDiscovery.discard(null);
                    break;
                case GET_GROUPS:
                    lookupDiscovery.getGroups();
                    break;
                case ADD_GROUPS:
                    lookupDiscovery.addGroups(new String[0]);
                    break;
                case SET_GROUPS:
                    lookupDiscovery.setGroups(new String[0]);
                    break;
                case REMOVE_GROUPS:
                    lookupDiscovery.removeGroups(new String[0]);
                    break;
            }//end switch(methodType)
            throw new TestException("IllegalStateException not thrown");
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, "IllegalStateException thrown as expected");
        }
    }
}

