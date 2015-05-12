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

package org.apache.river.test.spec.joinmanager;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.AttributesUtil;

import net.jini.core.entry.Entry;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is constructed with a
 * given set of attributes, each invocation of the <code>getAttributes</code>
 * method returns a new array whose contents are equal to the contents
 * of the set of attributes with which the join manager was constructed.
 * 
 */
public class GetAttributesNew extends GetAttributes {

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that each invocation of the <code>getAttributes</code>
     *   method returns a new array whose contents are equal to the contents
     *   of the set of attributes with which the join manager was constructed
     *   during construct.
     */
    public void run() throws Exception {
        super.run();

        Entry[] joinAttrs0 = joinMgrSrvcID.getAttributes();
        Entry[] joinAttrs1 = joinMgrSrvcID.getAttributes();
        if(joinAttrs0 == joinAttrs1) {
            String errStr = new String("same array returned on successive "
                                       +"calls to getAttributes");
            logger.log(Level.FINE, errStr);
            throw new TestException(errStr);
        }//endif
        /* Verify that even though succesive calls to getAttribute returns
         * different arrays, the contents of those arrays are always equal.
         */
        logger.log(Level.FINE, "arrays from successive calls to "
                          +"getAttributes are different, as expected");
        logger.log(Level.FINE, "comparing the contents of the "
                                        +"arrays returned from successive "
                                        +"calls to getAttributes ...");
        if (!AttributesUtil.compareAttributeSets(joinAttrs0,
                                                 joinAttrs1,
                                                 Level.FINE))
        {
            throw new TestException("contents of arrays from successive "
                                   +"calls to getAttributes are NOT equal "
                                   +"as expected");
        }
        logger.log(Level.FINE, "on successive calls to "
                          +"getAttributes, different arrays with equal "
                          +"content are always returned");
    }//end run

} //end class GetAttributesNew


