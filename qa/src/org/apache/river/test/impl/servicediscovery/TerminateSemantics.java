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

package org.apache.river.test.impl.servicediscovery;

import java.util.logging.Level;

import org.apache.river.test.spec.servicediscovery.AbstractBaseTest;

import net.jini.discovery.DiscoveryManagement;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.LookupCache;

import net.jini.core.lookup.ServiceItem;

import java.rmi.RemoteException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

/**
 * With respect to the current implementation of the
 * <code>ServiceDiscoveryManager</code> utility, this class verifies
 * that - except for the <code>terminate</code> method itself - if any
 * of the public methods of that utility are invoked after the service
 * discovery manager has been terminated, an <code>IllegalStateException</code>
 * results. Any invocation of the <code>terminate</code> method made
 * after the initial invocation of that method will result in no
 * action being taken, and no exception being thrown.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> one instance of ServiceDiscoveryManager
 *   <li> after invoking the terminate method on the service discovery
 *        manager, each version of each public method is invoked
 * </ul><p>
 * 
 * If the <code>ServiceDiscoveryManager</code> utility functions as
 * intended, then upon invoking any of the public methods on an instance
 * of that utility (except the <code>terminate</code> method) after
 * that instance has been terminated, an <code>IllegalStateException</code>
 * will occur. Upon invoking the <code>terminate</code> method after the
 * initial invocation of that method, no action will be taken.
 * 
 * Regression test for Bug ID 4394139, 4480307
 */
public class TerminateSemantics extends AbstractBaseTest {

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Creates a service discovery manager using the default input
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        testDesc = "verify invocation semantics of public methods after "
                   +"termination";
        waitForLookupDiscovery = false;
        terminateDelay = 0;
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Terminates the service discovery manager created in construct and
     *     verifies that an invocation of any of that service discovery
     *     manager's public methods (except the terminate method itself)
     *     will result in an <code>IllegalStateException</code>. 
     *  2. Verifies that any additional invocation of that service discovery
     *     manager's terminate method will result in no further action being
     *     taken
     */
    protected void applyTestDef() throws Exception {
        /* Terminate the service discovery manager */
        logger.log(Level.FINE, "terminating the service discovery manager ...");
        srvcDiscoveryMgr.terminate();
        sdmList.clear();
        String methodStr = "";
        String successStr = "IllegalStateException occurred as expected -- ";
        String errStr = "  no IllegalStateException -- ";
        boolean failed = false;
        /* Invoke methods on terminated sdm -- getDiscoveryManager */
        methodStr = "getDiscoveryManager()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            DiscoveryManagement dm = srvcDiscoveryMgr.getDiscoveryManager();
            logger.log(Level.FINE, ""+errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, ""+successStr+methodStr);
        }
        /* getLeaseRenewalManager */
        methodStr = "getLeaseRenewalManager()";
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            LeaseRenewalManager lrm
                                  = srvcDiscoveryMgr.getLeaseRenewalManager();
            logger.log(Level.FINE, ""+errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, ""+successStr+methodStr);
        }
        /* createLookupCache */
        methodStr = new String("createLookupCache()");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            LookupCache cache = srvcDiscoveryMgr.createLookupCache
                                                            (template,
                                                             firstStageFilter,
                                                             null);//listener
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        } catch(RemoteException e) {
            e.printStackTrace();
            logger.log(Level.FINE, "RemoteException occurred on "
                              +"call to "+methodStr);
            failed = true;
        }
        /* lookup(tmpl,filter) */
        methodStr = new String("lookup(tmpl,filter)");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            ServiceItem srvcItem = srvcDiscoveryMgr.lookup(template,
                                                           firstStageFilter);
            logger.log(Level.FINE, ""+errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* lookup(tmpl,filter,waitDur) */
        methodStr = new String("lookup(tmpl,filter,waitDur)");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            ServiceItem srvcItem = srvcDiscoveryMgr.lookup(template,
                                                           firstStageFilter,
                                                           1000);
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
            logger.log(Level.FINE, "InterruptedException occurred "
                              +"on call to "+methodStr);
            failed = true;
        } catch(RemoteException e) {
            e.printStackTrace();
            logger.log(Level.FINE, "RemoteException occurred on "
                              +"call to "+methodStr);
            failed = true;
        }
        /* lookup(tmpl,maxMatches,filter) */
        methodStr = new String("lookup(tmpl,maxMatches,filter)");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            ServiceItem[] srvcItems = srvcDiscoveryMgr.lookup
                                                           (template,
                                                            1,
                                                            firstStageFilter);
            logger.log(Level.FINE, ""+errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, ""+successStr+methodStr);
        }
        /* lookup(tmpl,maxMatches,minMatches,filter,waitDur) */
        methodStr = new String("lookup(tmpl,maxMatches,minMatches,"
                                     +"filter,waitDur)");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            ServiceItem[] srvcItems = srvcDiscoveryMgr.lookup
                                                           (template,
                                                            1,
                                                            1,
                                                            firstStageFilter,
                                                            1000);
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
            logger.log(Level.FINE, "InterruptedException occurred "
                              +"on call to "+methodStr);
            failed = true;
        } catch(RemoteException e) {
            e.printStackTrace();
            logger.log(Level.FINE, "RemoteException occurred on "
                              +"call to "+methodStr);
            failed = true;
        }
        /* terminate */
        methodStr = new String("terminate()");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            srvcDiscoveryMgr.terminate();
            logger.log(Level.FINE, "OK - on multiple invocations of "+methodStr);
        } catch(Exception e) {
            e.printStackTrace();
            logger.log(Level.FINE, "exception occurred on second "
                              +"invocation of "+methodStr);
            failed = true;
        }
        if(failed) {
	    throw new TestException(" -- no IllegalStateException from at "
				    + "least one public method");
	}
    }//end applyTestDef

}//end class TerminateSemantics


