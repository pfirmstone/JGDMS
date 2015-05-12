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

package org.apache.river.test.impl.joinmanager;

import org.apache.river.qa.harness.Test;
import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import org.apache.river.test.spec.joinmanager.AbstractBaseTest;

import net.jini.discovery.DiscoveryManagement;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.JoinManager;

import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceRegistrar;

/**
 * With respect to the current implementation of the <code>JoinManager</code>
 * utility, this class verifies that if any of the public methods of that
 * utility are invoked after the join manager has been terminated, an
 * <code>IllegalStateException</code> results.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> one instance of JoinManager
 *   <li> after invoking the terminate method on the join manager, each 
 *        version of each public is invoked
 * </ul><p>
 * 
 * If the <code>JoinManager</code> utility functions as intended, then upon
 * invoking any of the public methods on and inatacne of the utility after
 * that instance has been terminated, an <code>IllegalStateException</code>
 * will occur.
 *
 */
public class TerminateSemantics extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, a set of attributes (either null or non-null)
     *          with which to register the service, and both a null instance
     *          of a lookup discovery manager, and a null instance of a
     *          lease renewal manager
     *   </ul>
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        logger.log(Level.FINE, "creating a service ID join manager ...");
        joinMgrSrvcID = new JoinManager(testService,serviceAttrs,serviceID,
                                        null,null,
					sysConfig.getConfiguration());
        return this;
    }//end construct

    /** Executes the current test by doing the following:
     * <p>
     *  Terminates the join manager created in construct and verifies that
     *  an invocation of any of that join manager's public methods will
     *  result in an <code>IllegalStateException</code>.
     */
    public void run() throws Exception {
        logger.log(Level.FINE, ""+": run()");
        /* Terminate the join manager */
        logger.log(Level.FINE, "terminating the join manager ...");
        joinMgrSrvcID.terminate();
        String methodStr = "";
        String successStr = "IllegalStateException occurred as expected -- ";
        String errStr = "no IllegalStateException -- ";
        boolean failed = false;
        /* Invoke methods on terminated join manager -- getDiscoveryManager */
        methodStr = "getDiscoveryManager()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            DiscoveryManagement dm = joinMgrSrvcID.getDiscoveryManager();
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* getLeaseRenewalManager */
        methodStr = "getLeaseRenewalManager()";
        logger.log(Level.FINE, ""+": invoking "+methodStr+" ...");
        try {
            LeaseRenewalManager lrm = joinMgrSrvcID.getLeaseRenewalManager();
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* getJoinSet */
        methodStr = "getJoinSet()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            ServiceRegistrar[] regs = joinMgrSrvcID.getJoinSet();
            logger.log(Level.FINE, ""+errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, ""+successStr+methodStr);
        }
        /* getAttributes */
        methodStr = "getAttributes()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            Entry[] attrs = joinMgrSrvcID.getAttributes();
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* addAttributes */
        Entry[] newEntries = {new TestServiceIntAttr(99999)};
        methodStr = "first form of addAttributes()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            joinMgrSrvcID.addAttributes( newEntries );
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        methodStr = "second form of addAttributes()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            joinMgrSrvcID.addAttributes( newEntries, true );
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* setAttributes */
        methodStr = "setAttributes()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            joinMgrSrvcID.setAttributes( newEntries );
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* modifyAttributes */
        Entry[] tmpls = {new TestServiceIntAttr(88888)};
        methodStr = "first form of modifyAttributes()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            joinMgrSrvcID.modifyAttributes( tmpls,newEntries );
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        methodStr = "second form of modifyAttributes()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            joinMgrSrvcID.modifyAttributes( tmpls,newEntries,true );
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }

        if(failed) {
	    throw new TestException("no IllegalStateException "
				    +"from least one method");
	}
    }//end run

}//end class TerminateSemantics


