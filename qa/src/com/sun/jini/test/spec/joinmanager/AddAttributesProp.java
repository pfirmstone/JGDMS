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

package com.sun.jini.test.spec.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.AttributesUtil;

import net.jini.core.entry.Entry;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is instructed -- through the
 * invocation of the method <code>addAttributes</code> -- to augment the
 * current set of attributes with a new set of attributes, the join manager
 * propagates the appropriate set of new attributes (no duplicates) to each
 * lookup service with which the associated test service is registered.
 * 
 */
public class AddAttributesProp extends RegisterAttributes {

    protected Entry[] expectedAttrs;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> starts N lookup service(s) (where N may be 0) whose member
     *          groups are finite and unique relative to the member groups
     *          of all other lookup services running within the same multicast
     *          radius of the new lookup services
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, a non-null set of attributes to register with
     *          the service, and a non-null instance of a lookup discovery
     *          manager configured to discover the lookup services started in
     *          the previous step (if any)
     *     <li> constructs the set of attributes with which to augment the
     *          join manager's current set of attributes
     *     <li> constructs the set of attributes to expect after adding
     *          the new set
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        newServiceAttrs = addAttrsWithDups(serviceAttrs,newServiceAttrs);
        expectedAttrs   = addAttrsAndRemoveDups(serviceAttrs,newServiceAttrs);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Augments the current set of attributes with a new set, and verifies
     *   that the appropriate attributes (no duplicates) are propagated to
     *   each lookup service with which the associated test service is
     *   registered.
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "adding new attributes through "
                                        +"join manager ...");
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs",
                                           Level.FINE);
        joinMgrSrvcID.addAttributes(newServiceAttrs);

        logger.log(Level.FINE, "verifying added attributes were "
                                  +"propagated to each lookup service ...");
        verifyPropagation(expectedAttrs, getnSecsJoin());
        logger.log(Level.FINE, "added attributes successfully propagated to "
                          +"all "+curLookupListSize("AddAttributesProp.run")
                          +" lookup service(s)");
        return;
    }//end run

}//end class AddAttributesProp


