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
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.AttributesUtil;

import net.jini.core.entry.Entry;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when the <code>addAttributes</code> method
 * is invoked with <code>true</code> input to the <code>checkSC</code>
 * parameter, and with an attribute set containing at least 1 element
 * that implements the <code>net.jini.lookup.entry.ServiceControlled</code>
 * interface, a <code>SecurityException</code> is thrown.
 * 
 */
public class AddAttributesServiceControlled extends GetAttributes {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, a non-null set of attributes to register with
     *          the service, and a non-null instance of a lookup discovery
     *          manager configured to discover the lookup services started in
     *          the previous step (if any)
     *     <li> constructs the set of attributes -- containing at least 1
     *          element that implements the ServiceControlled interface -- to
     *          input to the addAttributes method
     *   </ul>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        if(newServiceAttrs == null) {
            newServiceAttrs = new Entry[3];
            for(int i=0;i<nAttributes;i++) {
                newServiceAttrs[i] = new TestServiceIntAttr
                                       (SERVICE_BASE_VALUE + nAttributes + i);
            }//end loop
        }//endif
        /* Replace one of the attributes with a ServiceControlled attribute */
        if(newServiceAttrs.length > 1) {
            newServiceAttrs[1] = new ServiceControlledAttr
                                       (SERVICE_BASE_VALUE + nAttributes + 1);
        } else {
            newServiceAttrs[0] = new ServiceControlledAttr
                                       (SERVICE_BASE_VALUE + nAttributes);
        }//endif
    }//end setup

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that a <code>SecurityException</code> is thrown when the
     *   <code>addAttributes</code> method is invoked with <code>true</code>
     *   input to the <code>checkSC</code> parameter, and with an attribute
     *   set containing at least 1 element that imnplements the
     *   <code>net.jini.lookup.entry.ServiceControlled</code> interface.
     */
    public void run() throws Exception {
        super.run();

        logger.log(Level.FINE, "adding attribute set that "
                      +"contains at least 1 ServiceControlled attribute ...");
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs",
                                           Level.FINE);
        try {
            /* Version of addAttributes with checkSC parameter */
            joinMgrSrvcID.addAttributes(newServiceAttrs, true);
	    throw new TestException("no SecurityException");
        } catch(SecurityException e) {
            logger.log(Level.FINE, "SecurityException occurred "
                                            +"as expected");
        }
    }

} //end class AddAttributesServiceControlled


