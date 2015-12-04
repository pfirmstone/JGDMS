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
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when a join manager is instructed -- through the
 * invocation of the method <code>addAttributes</code> -- to augment the
 * current set of attributes with a new set of attributes, the join manager
 * is re-configured with the appropriate set of attributes.
 * 
 */
public class AddAttributes extends GetAttributes {

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
     *     <li> constructs the set of attributes to expect after adding
     *          a new set
     *   </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        expectedAttrs = addAttrsAndRemoveDups(serviceAttrs,newServiceAttrs);
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that the join manager is re-configured correctly after
     *   the <code>addAttributes</code> method is used to add a new set
     *   of attributes to the set of attributes with which the join manager
     *   is currently configured.
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "adding new attributes through "
                                        +"join manager ...");
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs",
                                           Level.FINE);
        joinMgrSrvcID.addAttributes(newServiceAttrs);
        Entry[] newJoinAttrs = joinMgrSrvcID.getAttributes();
        logger.log(Level.FINE, "comparing the augmented attribute "
                                        +"set from the join manager to the "
                                        +"expected set of attributes ...");
        if (!AttributesUtil.compareAttributeSets(expectedAttrs,
                                                 newJoinAttrs,
                                                 Level.FINE))
        {
            throw new TestException("new attributes from join manager not "
                                   +"equal to service attributes");
        }
        logger.log(Level.FINE, "attributes from join manager equal "
                          +"expected set of attributes");
    }//end run

} //end class AddAttributes


