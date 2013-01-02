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

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import net.jini.discovery.LookupLocatorDiscovery;

import net.jini.core.discovery.LookupLocator;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code> utility
 * class operates in a manner consistent with the specification. In
 * particular, this class verifies that when the parameter input to the
 * constructor contains at least 1 <code>null</code> element, a
 * <code>NullPointerException</code> is thrown.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> an instance of the lookup locator discovery utility constructed
 *        using a set of locators in which at least 1 element is null
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified, then 
 * upon attempting to construct an instance of the lookup locator discovery
 * utility using a set of locators containing at least one <code>null</code>
 * element, a <code>NullPointerException</code> will occur.
 * 
 */
public class ConstructorNullElement extends AbstractBaseTest {

    protected LookupLocator[] nullLocs = null;
    protected LookupLocatorDiscovery lld = null;

    /** Performs actions necessary to prepare for execution of the 
     *  current test (refer to the description of this method in the
     *  parent class).
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        /* Create an array of LookupLocator instances in which at least 1
         * element is null
         */
        int nLocs = 5;
        nullLocs = new LookupLocator[nLocs];
        if(nLocs > 0) {
            nullLocs[0] = getTestLocator(0);//provides host and an initial port
            String host = nullLocs[0].getHost();
            int port    = nullLocs[0].getPort();
            int indx    = nLocs/2;
            for(int i=1;i<nullLocs.length;i++) {//populate the rest of array
                port = port+1;
                nullLocs[i] 
                        = ((indx == i) ? null : QAConfig.getConstrainedLocator(host,port));
            }//end loop
        }//endif
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that a <code>NullPointerException</code> is thrown when
     *   an instance of <code>LookupLocatorDiscovery</code> is created
     *   using a set of locators in which at least one element is
     *   <code>null</code>.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Create an instance of LookupLocatorDiscovery using null element */
        try {
            lld = new LookupLocatorDiscovery(nullLocs, 
					     getConfig().getConfiguration());
            locatorDiscoveryList.add(lld);
            String errStr = new String("no NullPointerException");
            logger.log(Level.FINE, errStr);
            throw new TestException(errStr);
        } catch(NullPointerException e) {
            logger.log(Level.FINE, "NullPointerException on "
                              +"construction of LookupLocatorDiscovery as "
                              +"expected");
        }
    }//end run

}//end class ConstructorNullElement


