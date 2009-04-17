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

import net.jini.lookup.JoinManager;

import net.jini.core.entry.Entry;

/**
 * This class verifies that the <code>JoinManager</code> utility class
 * operates in a manner consistent with the specification. In particular,
 * this class verifies that when <code>modifyAttributes</code> is invoked
 * with <code>true</code> input to the <code>checkSC</code> parameter, and
 * at least one of the attributes to be modified implements the
 * <code>net.jini.lookup.entry.ServiceControlled</code> interface, a
 * <code>SecurityException</code> is thrown.
 * 
 */
public class ModifyAttributesServiceControlled extends AbstractBaseTest {

    private Entry[] expectedAttrs;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> constructs the set of attributes -- containing at least 1
     *          element that implements the ServiceControlled interface -- with
     *          which to initially configure the join manager
     *     <li> constructs a set of attribute templates in which each element
     *          equals the corresponding element of the set of attributes
     *          with which the join manager will be initially configured
     *     <li> constructs a set of attributes that will be used to modify
     *          the initial attributes
     *     <li> constructs the set of attributes with which the join manager
     *          will be expected to be configured after the intended 
     *          modifications are requested
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, the non-null set of attributes created
     *          earlier, and a non-null instance of a lookup discovery
     *          manager configured to discover desired lookup services (if any)
     *   </ul>
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        if(serviceAttrs == null) {
            serviceAttrs = new Entry[3];
            for(int i=0;i<nAttributes;i++) {
                serviceAttrs[i] = new TestServiceIntAttr
                                                     (SERVICE_BASE_VALUE + i);
            }//end loop
        }//endif
        /* Replace one of the attributes with a ServiceControlled attribute */
        if(serviceAttrs.length > 1) {
            serviceAttrs[1] = new ServiceControlledAttr
                                                     (SERVICE_BASE_VALUE + 1);
        } else {
            serviceAttrs[0] = new ServiceControlledAttr(SERVICE_BASE_VALUE);
        }//endif
        attrTmpls = new Entry[serviceAttrs.length];
        newServiceAttrs = new Entry[serviceAttrs.length];
        expectedAttrs = new Entry[serviceAttrs.length];
        for(int i=0;i<serviceAttrs.length;i++) {
            if(serviceAttrs[i] instanceof ServiceControlledAttr) {
                attrTmpls[i] = new ServiceControlledAttr
                  (( ((ServiceControlledAttr)serviceAttrs[i]).val).intValue());
                newServiceAttrs[i] = new ServiceControlledAttr
                                       (SERVICE_BASE_VALUE + nAttributes + i);
                expectedAttrs[i] = new ServiceControlledAttr
                ((((ServiceControlledAttr)newServiceAttrs[i]).val).intValue());
            } else {//default to TestServiceIntAttr
                attrTmpls[i] = new TestServiceIntAttr
                    (( ((TestServiceIntAttr)serviceAttrs[i]).val).intValue());
                newServiceAttrs[i] = new TestServiceIntAttr
                                       (SERVICE_BASE_VALUE + nAttributes + i);
                expectedAttrs[i] = new TestServiceIntAttr
                  (( ((TestServiceIntAttr)newServiceAttrs[i]).val).intValue());
            }//endif
        }//end loop
        logger.log(Level.FINE, "creating a service ID join manager ...");
        joinMgrSrvcID = new JoinManager(testService,serviceAttrs,serviceID,
                                        getLookupDiscoveryManager(),leaseMgr,
					sysConfig.getConfiguration());
        joinMgrList.add(joinMgrSrvcID);
    }//end setup

    /** Executes the current test by doing the following:
     * <p>
     *   Verifies that when <code>modifyAttributes</code> is invoked
     *   with <code>true</code> input to the <code>checkSC</code> parameter,
     *   and at least one of the attributes to be modified implements the
     *   <code>net.jini.lookup.entry.ServiceControlled</code> interface, a
     *   <code>SecurityException</code> is thrown.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        verifyAttrsInJoinMgr(joinMgrSrvcID,serviceAttrs);
        logger.log(Level.FINE, "changing current attributes - "
                          +"containing at least 1 ServiceContribute - to "
                          +"new set ...");
        AttributesUtil.displayAttributeSet(serviceAttrs,"serviceAttrs",
                                           Level.FINE);
        AttributesUtil.displayAttributeSet(attrTmpls,"attrTmpls",
                                           Level.FINE);
        AttributesUtil.displayAttributeSet(newServiceAttrs,
                                           "newServiceAttrs", Level.FINE);
        AttributesUtil.displayAttributeSet(expectedAttrs,"expectedAttrs",
                                           Level.FINE);
        try {
            /* Version of modifyAttributes with checkSC parameter */
            joinMgrSrvcID.modifyAttributes(attrTmpls,newServiceAttrs,true);
	    throw new TestException("no SecurityException thrown");
        } catch(SecurityException e) {
            logger.log(Level.FINE, "SecurityException occurred "
                                            +"as expected");
        }
    }//end run

} //end class ModifyAttributesServiceControlled


