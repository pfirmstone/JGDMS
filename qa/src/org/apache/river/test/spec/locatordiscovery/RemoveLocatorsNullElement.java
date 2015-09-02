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

package org.apache.river.test.spec.locatordiscovery;

import java.util.logging.Level;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.LocatorsUtil;

import net.jini.core.discovery.LookupLocator;

/**
 * This class verifies that the <code>LookupLocatorDiscovery</code>
 * utility operates in a manner consistent with the specification.
 * In particular, this class verifies that when the parameter input to
 * the <code>removeLocators</code> method contains at least one
 * <code>null</code> element, a <code>NullPointerException</code> is thrown.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> zero lookup services
 *   <li> an instance of the lookup locator discovery utility that is
 *        constructed using an array of locators consistent with the
 *        the test's configuration
 * </ul><p>
 * 
 * If the lookup locator discovery utility functions as specified,
 * then when the <code>removeLocators</code> method is invoked with a
 * set of locators in which at least one element is <code>null</code>,
 * a <code>NullPointerException</code> will occur.
 *
 */
public class RemoveLocatorsNullElement extends AddLocatorsNullElement {

    /** Executes the current test by doing the following:
     * <p><ul>
     *     <li> first verifies that the lookup locator discovery utility is
     *          currently configured to discover a non-null, non-empty set
     *          of locators
     *     <li> invokes removeLocators with a set of locators in which at least
     *          one element is null
     *     <li> verifies that a NullPointerException is thrown upon the
     *          invocation of removeLocators
     * </ul>
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        /* First verify lld is initially configured with the expected locs */
        LookupLocator[] lldLocs = lld.getLocators();
	if (!LocatorsUtil.compareLocatorSets(lldLocs, configLocs,Level.FINE)) {
	    throw new TestException("Locator set are not equivalent");
	}

        /* Remove locators using a set containing at least 1 null element */
        try {
            lld.removeLocators(nullLocs);
            String errStr = new String("no NullPointerException");
            logger.log(Level.FINE, errStr);
            throw new TestException(errStr);
        } catch(NullPointerException e) {
            logger.log(Level.FINE, 
		       "NullPointerException on locator removal as expected");
        }
        return;
    }//end run

}//end class RemoveLocatorsNullElement

