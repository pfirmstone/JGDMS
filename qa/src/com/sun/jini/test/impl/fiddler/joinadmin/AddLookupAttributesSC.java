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

package com.sun.jini.test.impl.fiddler.joinadmin;

import java.util.logging.Level;

import com.sun.jini.test.share.AttributesUtil;
import com.sun.jini.qa.harness.TestException;
import net.jini.core.entry.Entry;

/**
 * This class determines whether or not the lookup discovery service can
 * successfully add a new set of attributes to the its current set of
 * attributes.
 *
 * This test attempts to add to the service's current set of attributes,
 * a set of attributes in which at least one element implements the
 * <code>net.jini.lookup.entry.ServiceControlled</code> interface.
 *
 *
 * @see com.sun.jini.qa.harness.QAConfig
 * @see com.sun.jini.qa.harness.QATest
 * @see com.sun.jini.qa.harness.QATestUtil
 */
public class AddLookupAttributesSC extends AddLookupAttributes {
    /** Constructs and returns the set of attributes to add (overrides the
     *  parent class' version of this method)
     */
    Entry[] getTestAttributeSet() {
        return new Entry[]
                      {new AttributesUtil.TestAttr00("AAA","AAA","AAA"),
                       new AttributesUtil.TestAttrSC00("Service","Controlled"),
                       new AttributesUtil.TestAttr00("CCC","CCC","CCC")
                      };
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, adds to the service's current set of attributes
     *     a new set of attributes in which at least one element implements
     *     the <code>net.jini.lookup.entry.ServiceControlled</code> interface
     *  3. Because a client should not be allowed to add a ServiceControlled
     *     attribute to the service, a <code>SecurityException</code> is
     *     expected. Verify that the expected exception is thrown.
     */
    public void run() throws Exception {
        try {
	    super.run();
	    throw new TestException("SecurityException was not thrown");
	} catch (SecurityException e) {
                logger.log(Level.FINE, 
			   "received SecurityException as expected");
        }
    }
}


