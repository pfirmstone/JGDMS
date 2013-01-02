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

package com.sun.jini.test.spec.servicediscovery.lookup;

import java.util.logging.Level;

import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;
import com.sun.jini.test.share.DiscoveryServiceUtil;

import net.jini.lookup.ServiceItemFilter;

import net.jini.core.entry.Entry;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;

import java.rmi.RemoteException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;

/**
 * With respect to the <code>lookup</code> method defined by the 
 * <code>ServiceDiscoveryManager</code> utility class, this class verifies
 * that the blocking version that returns a single instance of
 * <code>ServiceItem</code> operates as specified when invoked under
 * the following condition:
 * <p><ul>
 *    <li> template matching performed by the service discovery manager is
 *         based on service type only
 *    <li> the service discovery manager applies no filtering to the results
 *         of the template matching
 * </ul><p>
 *
 * <pre>
 *    ServiceItem lookup(ServiceTemplate tmpl,
 *                       ServiceItemFilter filter,
 *                       long waitDur);
 * </pre>
 */
public class LookupWait extends AbstractBaseTest {

    protected long waitDur = 30*1000;

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     */
    public Test construct(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        testDesc = "single service lookup employing -- template, blocking";
        return this;
    }//end construct

    /** Cleans up all state. */
    public void tearDown() {
        /* Because service registration occurs in a separate thread, 
         * some tests can complete before all of the service(s) are 
         * registered with all of the lookup service(s). In that case,
         * a lookup service may be destroyed in the middle of one of
         * registration requests, causing a RemoteException. To avoid
         * this, an arbitrary delay is executed to allow all previous
         * registrations to complete.
         */
        logger.log(Level.FINE, "waiting "+(regCompletionDelay/1000)+" seconds before "
                     +"tear down to allow all registrations to complete ... ");
        DiscoveryServiceUtil.delayMS(regCompletionDelay);
        super.tearDown();
    }//end tearDown

    /** Defines the actual steps of this particular test.
     *  
     *  1. Invokes the desired version of the <code>lookup</code> method
     *     on the service discovery manager - applying NO filtering
     *     (<code>null</code> filter parameter) - and verifies that when
     *     no services are registered with the lookup services started during
     *     construct, the blocking mechanism of the <code>lookup</code>
     *     method blocks for the full amount of time requested
     *  2. Registers 1 service with each of the lookup services started
     *     in construct
     *  3. Again invokes the desired version of the <code>lookup</code>
     *     method - applying NO filtering - and verifies that the service
     *     returned is the service expected, and the <code>lookup</code>
     *     method blocks until the registration of the desired service 
     *     has completed successfully
     */
    protected void applyTestDef() throws Exception {
        /* Verify blocking mechanism in the absense of registered services */
        verifyBlocking(waitDur);
        /* Register 1 service with 0 attributes and verify that the call to
         * lookup actually blocks until the desired service is registered.
         */
        waitDur = 60*1000; //reset the amount of time to block
        verifyBlocking(1,0,waitDur);
    }//end applyTestDef

    /** Tests that the blocking mechanism of the lookup() method will block
     *  for the expected amount of time based on the given parameter
     *  values.
     *
     *  If no services are to be registered (nSrvcs == 0), or if
     *  the template and filter combination given to lookup() match
     *  none of the registered services, this method verifies that 
     *  lookup() not only blocks the full amount of time, but also 
     *  returns null.
     *
     *  If services are to be registered (nSrvcs > 0), this method 
     *  verifies that lookup() blocks until the expected matching
     *  service is registered with with at least one of the lookup
     *  services used in the test.
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     */
    protected void verifyBlocking(int nSrvcs,int nAttrs,long waitDur) 
	throws Exception
    {
        String testServiceClassname 
        = "com.sun.jini.test.spec.servicediscovery.AbstractBaseTest$TestService";

        /* Remove all services from lookups and clear lists for this call */
	logger.log(Level.FINE, "un-registering all services ... ");
	unregisterServices();
        long waitDurSecs = waitDur/1000; //for debug output
        int i = 0;
        TestService expectedService = null;
        if(nSrvcs > 0) {// Create the template for lookup with or without attrs
            /* Will register N services with N attribute after a delay */
            i = nSrvcs-1;
            expectedService = new TestService(SERVICE_BASE_VALUE+i);
            if(    (firstStageFilter != null)
                && (firstStageFilter instanceof ServiceItemFilter) )
            {
                if(srvcValOdd(expectedService)) expectedService = null;
            }
            /* Create template that matches on type and, if attributes are
             * to be used, the last service registered.
             */
	    Class c = Class.forName(testServiceClassname);
	    Entry[] attrs = null;
	    if(nAttrs > 0) {
		attrs = new Entry[1];
		attrs[0] = new TestServiceIntAttr(SERVICE_BASE_VALUE+i);
	    }//endif
	    template = new ServiceTemplate(null, new Class[]{c}, attrs);
            logger.log(Level.FINE, "looking up service that will "
                              +"be registered -- blocking "+waitDurSecs
                              +" second(s)");
            /* Register all services after waiting less than the block time */
            logger.log(Level.FINE, "registering "+nSrvcs
                              +" service(s) each with "+nAttrs
                              +" attribute(s) ...");
            (new RegisterThread(nSrvcs,nAttrs,waitDur)).start();
        } else {//(nSrvcs<=0)
            /* Will register no services */
            logger.log(Level.FINE, "looking up non-existant "
                             +"service -- blocking "+waitDurSecs+" second(s)");
        }//endif(nSrvcs>0)
        /* Try to lookup the service, block until the service appears */
            long startTime = System.currentTimeMillis();
            ServiceItem srvcItem = srvcDiscoveryMgr.lookup(template,
                                                           firstStageFilter,
                                                           waitDur);
            long endTime = System.currentTimeMillis();
            long actualBlockTime = endTime-startTime;
            long waitError = (actualBlockTime-waitDur)/1000;
            if(expectedService == null) {//block full amount and return null
                /* Blocking time should be within epsilon of full amount */
                if(waitError<-3) {
                   throw new TestException(" -- failed to block requested "
                                     +"time -- requested block = "
                                     +waitDurSecs+" second(s), actual "
                                     +"block = "+(actualBlockTime/1000)
                                     +" second(s)");
                } else if(waitError>30) {
                    throw new TestException(" -- exceeded requested block "
                                      +"time -- requested block = "
                                      +waitDurSecs+" second(s), actual "
                                      +"block = "+(actualBlockTime/1000)
                                      +" second(s)");
                }//endif
                /* ServiceItem should be null since rejected by filter */
                if(srvcItem != null) {
                    throw new TestException(" -- unexpected non-null service item"
                                     +"returned on lookup of test service "+i);
                } else {
                    logger.log(Level.FINE, "  OK -- both null as expected");
                }
            } else { //(expectedService != null) -- block less than full amount
                /* Blocking time should be less than the full amount */
                if(waitError >= 0) {
                    throw new TestException(" -- blocked longer than expected "
                                      +"-- requested block = "
                                      +waitDurSecs+" second(s), actual "
                                      +"block = "+(actualBlockTime/1000)
                                      +" second(s)");
                }
                /* Correct non-null ServiceItem should have been returned */
                if(srvcItem == null) {
                    throw new TestException(" -- unexpected null service item "
                                      +"returned on lookup of test service");
                } else if(srvcItem.service == null) {
                    throw new TestException(" -- null service component returned "
                                      +"on lookup of test service ");
                } else {
                    if(nAttrs > 0) {
                        /* less restrictive tmpl could return any service */
                        for(int j=0;j<getExpectedServiceList().size();j++) {
                            if((srvcItem.service).equals
                                                (getExpectedServiceList().get(j)))
                            {
                                logger.log(Level.FINE, "expected "
                                              +"service found -- requested "
                                              +"block = "+waitDurSecs
                                              +" second(s), actual block = "
                                              +(actualBlockTime/1000)
                                              +" second(s)");
	                        return;// passed
                            }//endif
                        }//end loop (i)
                    } else {//(nAttrs<=0 )
                        /* more restrictive tmpl returns particular service */
                        if( !(srvcItem.service).equals(expectedService) ) {
                            logger.log(Level.FINE, "  FAILURE -- "
                                +"expectedService.i = "+expectedService.i+", "
                                +"(srvcItem.service).i = "
                                +((TestService)(srvcItem.service)).i);
                            throw new TestException(" -- filter failed -- service "
                                          +"returned from lookup not "
                                          +"equivalent to expected test "
                                          +"service "+i);
                        } else {
                            logger.log(Level.FINE, "  OK -- "
                                             +"(srvcItem.service).equals"
                                             +"(expectedService) as expected");
                        }//endif
                    }//endif(nAttrs>0)
                }//endif(srvcItem==null)
            }//endif(expectedService==null)
    }//end verifyBlocking

    protected void verifyBlocking(long waitDur) throws Exception {
        verifyBlocking(0,0,waitDur);
    }//end verifyBlocking

    protected void verifyBlocking(int nSrvcs,long waitDur) throws Exception {
        verifyBlocking(nSrvcs,0,waitDur);
    }//end verifyBlocking

}//end class LookupWait


