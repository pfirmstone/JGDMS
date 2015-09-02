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

package org.apache.river.test.spec.lookupdiscovery;
import org.apache.river.qa.harness.QAConfig;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;

/**
 * This class verifies that the <code>LookupDiscovery</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that the <code>equals</code> method returns
 * <code>true</code> if and only if two instances of this class refer to
 * the same object. That is, x and y are equal instances of
 * <code>LookupDiscovery</code> if and only if x == y is true.
 */
public class Equality extends AbstractBaseTest {

    /** Executes the current test by doing the following:
     * <p>
     *   Compares the instance of <code>LookupDiscovery</code> created during
     *   construct to itself and to a new instance; and verifies that the
     *   <code>equals</code> method returns the appropriate result depending
     *   on the particular instances of <code>LookupDiscovery</code> being
     *   compared.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* Create another LookupDiscovery for comparison */
        LookupDiscovery newLD = new LookupDiscovery
                                     (DiscoveryGroupManagement.NO_GROUPS,
				      getConfig().getConfiguration());
        lookupDiscoveryList.add(newLD);
        if( !satisfiesEqualityTest(lookupDiscovery,newLD) ) { 
            throw new TestException(
                                 "failed equality test -- DIFFERENT "
                                 +"instances of LookupDiscovery");
        }//endif
        if( !satisfiesEqualityTest(lookupDiscovery,lookupDiscovery) ) { 
            throw new TestException(
                                 "failed equality test -- SAME initial "
                                 +"instance of LookupDiscovery");
        }//endif
        if( !satisfiesEqualityTest(newLD,newLD) ) { 
            throw new TestException(
                                 "failed equality test -- SAME new "
                                 +"instance of LookupDiscovery");
        }//endif
        logger.log(Level.FINE,
                          "LookupDiscovery passed equality test");
    }//end run

}//end class Equality

