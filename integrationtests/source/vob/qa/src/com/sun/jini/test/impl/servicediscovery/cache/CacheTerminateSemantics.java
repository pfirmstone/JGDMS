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

package com.sun.jini.test.impl.servicediscovery.cache;

import java.util.logging.Level;

import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;

import net.jini.lookup.LookupCache;
import net.jini.lookup.ServiceDiscoveryListener;

import net.jini.core.lookup.ServiceItem;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * With respect to the current implementation of the <code>LookupCache</code>
 * interface returned by the <code>createLookupCache</code> method of the
 * current implementation of the <code>ServiceDiscoveryManager</code> utility,
 * this class verifies that - except for the <code>terminate</code> method
 * itself - if any of the public methods of the lookup cache are invoked
 * after that cache has been terminated, an <code>IllegalStateException</code>
 * results. Any invocation of the <code>terminate</code> method made after the
 * initial invocation of that method will result in no action being taken,
 * and no exception being thrown.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> no lookup services
 *   <li> one instance of ServiceDiscoveryManager
 *   <li> one instance of the LookupCache interface returned by the
 *        createLookupCache method of the service discovery manager
 *   <li> after invoking the terminate method on the lookup cache,
 *        each version of each public method of the cache is invoked
 * </ul><p>
 * 
 * If the <code>LookupCache</code> instance returned by the
 * <code>createLookupCache</code> method of the 
 * <code>ServiceDiscoveryManager</code> utility functions as intended,
 * then upon invoking any of the public methods on that lookup cache
 * (except the <code>terminate</code> method) after the cache has been
 * terminated, an <code>IllegalStateException</code> will occur. Upon
 * invoking the <code>terminate</code> method after the initial invocation
 * of that method, no action will be taken.
 * 
 * Regression test for Bug ID 4394139
 */
public class CacheTerminateSemantics extends AbstractBaseTest {

    private LookupCache cache;

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Creates a service discovery manager using the default input
     *  2. Creates a lookup cache using the default input
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        testDesc = "verify invocation semantics of public methods after "
                   +"termination";
        waitForLookupDiscovery = false;
        terminateDelay = 0;
        /* createLookupCache */
        cache = srvcDiscoveryMgr.createLookupCache(template,
                                                   firstStageFilter,
                                                   null);//listener
    }//end setup

    /** Defines the actual steps of this particular test.
     *  
     *  1. Terminates the lookup cache created in setup and verifies that
     *     an invocation of any of that cache's public methods (except the
     *     terminate method itself) will result in an
     *     <code>IllegalStateException</code>. 
     *  2. Verifies that any additional invocation of that cache's
     *     terminate method will result in no further action being
     *     taken
     * 
     *  @return a <code>String</code> containing a failure message, or
     *           <code>null</code> if the test was successful.
     */
    protected void applyTestDef() throws Exception {
        /* Terminate the lookup cache */
        logger.log(Level.FINE, "terminating the lookup cache ...");
        cache.terminate();
        String methodStr = "";
        String successStr = "IllegalStateException occurred as expected -- ";
        String errStr = "  no IllegalStateException -- ";
        boolean failed = false;
        ServiceDiscoveryListener srvcListener
                     = new AbstractBaseTest.SrvcListener(getConfig(),"");

        /* Invoke methods on terminated lookup cache -- addListener */
        methodStr = new String("addListener()");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            cache.addListener(srvcListener);
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* removeListener */
        methodStr = new String("removeListener()");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            cache.removeListener(srvcListener);
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* lookup(filter) */
        methodStr = new String("lookup(filter)");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            ServiceItem srvcItem = cache.lookup(secondStageFilter);
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* lookup(filter,maxMatches) */
        methodStr = new String("lookup(filter,maxMatches)");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            ServiceItem[] srvcItems = cache.lookup(secondStageFilter,1);
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* discard(serviceReference) */
        methodStr = new String("discard(serviceReference)");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            cache.discard(new Object());
            logger.log(Level.FINE, errStr+methodStr);
            failed = true;
        } catch(IllegalStateException e) {
            logger.log(Level.FINE, successStr+methodStr);
        }
        /* terminate */
        methodStr = new String("terminate()");
        logger.log(Level.FINE, "invoking "+methodStr+" ...");
        try {
            cache.terminate();
            logger.log(Level.FINE,
		       "OK - on multiple invocations of "+methodStr);
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

}//end class CacheTerminateSemantics


