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

package org.apache.river.test.impl.fiddler.joinadmin;

import java.util.logging.Level;

import org.apache.river.test.share.AttributesUtil;
import org.apache.river.qa.harness.TestException;
import net.jini.core.entry.Entry;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully modify its current set of attributes.
 *
 * This test attempts to add a null reference to the service's current set
 * of attributes; which should result in a <code>NullPointerException</code>.
 */
public class AddLookupAttributesNull extends AddLookupAttributes {

    /** Constructs and returns the set of attributes to add (overrides
     *  the parent class' version of this method)
     */
    Entry[] getTestAttributeSet() {
        return null;
    }//end getTestAttributeSet

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, adds a set of attributes to the service's
     *     current set
     *  3. Because <code>null</code> is input to the parameter for the new
     *     set of attribute values, a <code>NullPointerException</code> is
     *     expected. Verify that the expected exception is thrown.
     */
    public void run() throws Exception {
        try {
	    super.run();
	    throw new TestException("Did not receive NullPointerException");
	} catch (NullPointerException e) {
	    logger.log(Level.FINE, "received NullPointerException as expected");
        }
    }
}


