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
 * successfully modify its current set of attributes.
 *
 * This test adds one or more "well-known" attributes to the service's 
 * current set and then attempts to modify that set of just-added attributes
 * by inputing a null reference for the parameter corresponding to the
 * new set of attribute values; which should result in a
 * <code>NullPointerException</code>.
 */
public class ModifyLookupAttributesNullInput extends ModifyLookupAttributes {

    /** Constructs and returns the set of attributes that are used in the
     *  matching process for the attributes that will be modified (overrides
     *  the parent class' version of this method)
     */
    Entry[] getAttributeSetTemplates() {
        return new Entry[] {new AttributesUtil.TestAttr00(null,null,null)};
    }

    /** Constructs and returns the set of attributes that are used to modify
     *  the desired set of attributes (overrides the parent class' version
     *  of this method)
     */
    Entry[] getTestAttributeSet() {
        return null;
    }

    /** Constructs and returns the set of attributes that are expected to
     *  be remaining from the just-added attributes after those just-added
     *  attributes have been modified in the desired way (overrides the
     *  parent class' version of this method)
     */
    Entry[] getAttributesAfterMod() {
        return getInitialAttributeSet();
    }

    /** Executes the current test by doing the following:
     *  
     *  1. Retrieves the admin instance of the service under test.
     *  2. Through the admin, adds a set of attributes to the service's
     *     current set
     *  3. Constructs the set of attribute template(s) that are used to
     *     select (match) the desired sub-set of the just-added attributes
     *     that will be modified
     *  4. Through the admin, modifies the selected set of just-added
     *     attributes
     *  5. Because <code>null</code> is input to the parameter for the new
     *     set of attribute values, a <code>NullPointerException</code> is
     *     expected. Verify that the expected exception is thrown.
     */
    public void run() throws Exception {
        try {
	    super.run();
	    throw new TestException("Expected NullPointerException not thrown");
	} catch (NullPointerException e) {
	    logger.log(Level.FINE, "NullPointerException thrown as expected");
        }
    }
}


