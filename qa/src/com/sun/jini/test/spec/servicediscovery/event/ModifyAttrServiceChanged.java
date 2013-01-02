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

package com.sun.jini.test.spec.servicediscovery.event;

import java.util.logging.Level;

import com.sun.jini.test.spec.servicediscovery.AbstractBaseTest;
import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.share.AttributesUtil;

import com.sun.jini.lookup.entry.LookupAttributes;

import net.jini.lookup.LookupCache;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lookup.ServiceRegistration;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * This class verifies that the event mechanism defined by the
 * <code>LookupCache</code> interface operates as specified with respect
 * to <code>serviceChanged</code> events when the set of attributes
 * associated with a set of registered services is modified.
 */
public class ModifyAttrServiceChanged extends AbstractBaseTest {

    protected String proto = "jeri";

    protected long waitDur    = 30*1000;
    protected long cacheDelay = 20*1000;
    protected long eventDelay = 10*1000;

    protected LookupCache cache;
    protected int testServiceType;

    protected int nAddedExpected   = 0;
    protected int nRemovedExpected = 0;
    protected int nChangedExpected = 0;

    protected AbstractBaseTest.SrvcListener srvcListener;

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  1. Starts N lookup services 
     *  2. Creates a service discovery manager that discovers the lookup
     *     services started above
     *  3. Creates a template that will match the test services based on
     *     service type only
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        testDesc = "change set of service attribute(s) -- "
                   +"expect service changed event(s)";
        nAddedExpected   = getnServices();
        nChangedExpected = nAddedExpected;
        testServiceType  = AbstractBaseTest.TEST_SERVICE;

        proto = config.getStringConfigVal
                                  ("com.sun.jini.qa.harness.configs", "jeri");
        /* Use longer delays when proto is secure. */
        if(    !(proto.compareToIgnoreCase("jeri") == 0)
            && !(proto.compareToIgnoreCase("jrmp") == 0)
            && !(proto.compareToIgnoreCase("http") == 0)
            && !(proto.compareToIgnoreCase("none") == 0) )
        {
            waitDur    = 60*1000;
            cacheDelay = 60*1000;
            eventDelay = 60*1000;
        }//endif
        return this;
    }//end construct

    /** Defines the actual steps of this particular test.
     *  
     *  1. Registers the test service(s) along with an attribute, and
     *     verifies the registration was successful.
     *  2. Creates a cache, registers with the event mechanism of the cache,
     *     and verifies that the cache and its event mechanism works as
     *     expected.
     *  3. Changes the attributes through either modification, replacement
     *     or augmentation, and verifies that the expected number of 
     *     serviceChanged/serviceRemoved/serviceAdded events are sent by
     *     the cache's event mechanism.
     */
    protected void applyTestDef() throws Exception {
        registerAndVerify(waitDur);
        createCacheAndVerify();
        changeAttrAndVerify();
    }//end applyTestDef

    /** Verifies the number of added, removed, and changed events expected
     *  to have been received by the listener are each equal to the expected
     *  value at the time this method is called.
     */
    protected void verifyCurrentEvents() throws Exception {
        verifyCurrentEvents(nAddedExpected,
			    nRemovedExpected,
			    nChangedExpected);
    }//end verifyCurrentEvents
    protected void verifyCurrentEvents(int nExpectedAdded,
				       int nExpectedRemoved,
				       int nExpectedChanged)
	throws Exception
    {
        int nAdded   = srvcListener.getNAdded();
        int nRemoved = srvcListener.getNRemoved();
        int nChanged = srvcListener.getNChanged();
        logger.log(Level.FINE, "serviceAdded events   -- "
                          +"# received = "+nAdded+", # expected = "
                          +nExpectedAdded);
        logger.log(Level.FINE, "serviceRemoved events -- "
                          +"# received = "+nRemoved+", # expected = "
                          +nExpectedRemoved);
        logger.log(Level.FINE, "serviceChanged events -- "
                          +"# received = "+nChanged+", # expected = "
                          +nExpectedChanged);
        if(    (nExpectedAdded   != nAdded)
            || (nExpectedRemoved != nRemoved)
            || (nExpectedChanged != nChanged) )
        {
            throw new TestException
		("unexpected events -- added: received "+nAdded
		 +" - expected "+nExpectedAdded+", removed: received "
		 +nRemoved+" - expected "+nExpectedRemoved
		 +", changed: received "+nChanged+" - expected "
		 +nExpectedChanged);
        }
    }//end verifyCurrentEvents

    /** Based on the how the current test is to change the service's set of
     *  attribute(s) (modification, replacement, or augmentation), this method
     *  constructs and returns the set of attributes that will result after 
     *  the change is requested.
     *
     *  This method should be over-ridden by tests that extend this class.
     * 
     *  @return the set of attributes that will result after the particular
     *          change is made to the test service's set of attribute(s).
     */
    protected Entry[] getNewAttrs(Entry[] oldAttrs, Entry[] chngAttr) {
        return LookupAttributes.modify(oldAttrs,oldAttrs,chngAttr);
    }//end getNewAttrs

    /** Based on the how the current test is to change the service's set of
     *  attribute(s) (modification, replacement, or augmentation), this method
     *  makes the request for the actual change.
     *
     *  This method should be over-ridden by tests that extend this class.
     */
    protected void changeAttributes(ServiceRegistration srvcReg, 
                                    Entry[] oldAttrs,
                                    Entry[] chngAttr)
                                throws UnknownLeaseException, RemoteException
    {
        srvcReg.modifyAttributes(oldAttrs,chngAttr);
    }//end changeAttributes

    /** For each registered test service, and for each lookup service with
     *  which each test service is registered, this method "changes" the
     *  associated attribute. Depending on the definition of the changeAttr()
     *  method, the change made to the attribute will take one of the 
     *  following forms:
     *    -- modification
     *    -- replacement (set new attribute)
     *    -- augmentation (add a new attribute)
     *
     *  After the attribute is changed, the success or failure of the change
     *  is verified by comparing the number of serviceChanged events 
     *  received to the number expected. 
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     */
    protected void changeAttrAndVerify() throws Exception {
        synchronized(regInfoMap) {
            Set eSet = regInfoMap.entrySet();
            /* For each registered service, from the set of service-to-
             * registration information mappings, retrieve each
             * ServiceRegistration and use that registration object to
             * change the service's attribute on the lookup service to
             * which the ServiceRegistration corresponds. After the change
             * is made, the set of mappings is updated with the new mapping.
             */
            for(Iterator itr = eSet.iterator(); itr.hasNext(); ) {
                /* Retrieve the (key,value) pair from the set of mappings 
                 * that was populated during construct. The key is the test
                 * service that was registered with the lookup service(s).
                 * The value is an ArrayList contain a set of data structures
                 * of type AbstractBaseTest.RegInfo, containing information
                 * about the given test service's registration with a 
                 * particular lookup service; information such as:
                 * the service ID, the service registration, the service
                 * lease with the lookup service, and the set of attributes
                 * with which the service was registered with the lookup
                 * service.
                 */
                Map.Entry pair = (Map.Entry)itr.next(); //get the mapping
                TestService srvc = (TestService)(pair.getKey());//get the key
                logger.log(Level.FINE, "For testService."+srvc.i+" --");
                ArrayList regInfoList = (ArrayList)(pair.getValue());//the val

                /* Retrieve the first attribute from the first set of 
                 * registration information. This "old" attribute will be used
                 * to create the new attribute that is used to "change" the
                 * old attribute. Since the same attribute is registered with
                 * every lookup service with which the service is registered, 
                 * we can retrieve any attribute from any regInfo element.
                 */
                RegInfo regInfo0 = (RegInfo)(regInfoList.get(0));
                Entry[] oldAttrs = regInfo0.srvcAttrs;
                AttributesUtil.displayAttributeSet(oldAttrs,"oldAttrs",
                                                   Level.FINE);
                /* Create the attribute that will be used to "change" the
                 * current set of attributes.
                 */
                Entry[] chngAttr = new Entry[1];
                int newVal = 1+
                           (((TestServiceIntAttr)oldAttrs[0]).val).intValue();
                chngAttr[0] = new TestServiceIntAttr(newVal);
                AttributesUtil.displayAttributeSet(chngAttr,"changeAttr",
                                                   Level.FINE);

                /* Determine the new set of attribute(s) that will result
                 * after the change to the current set is made.
                 */
                Entry[] newAttrs = getNewAttrs(oldAttrs,chngAttr);
                AttributesUtil.displayAttributeSet(newAttrs,"newAttrs",
                                                   Level.FINE);
                /* For each lookup service with which the current service is
                 * is registered, change the set of attribute(s) associated
                 * with that service.
                 */
                for(int i=0;i<regInfoList.size();i++) {
                    RegInfo regInfo = (RegInfo)(regInfoList.get(i));
                    ServiceRegistration srvcReg = regInfo.srvcReg;
                    Entry[] srvcAttrs = regInfo.srvcAttrs;
                    logger.log(Level.FINE, " service ID    = "+regInfo.srvcID);
                    logger.log(Level.FINE, " service Reg   = "+srvcReg);
                    logger.log(Level.FINE,
			       " service Lease = " +regInfo.srvcLease);
		    LookupLocator loc = regInfo.lookupProxy.getLocator();
		    loc = QAConfig.getConstrainedLocator(loc);
		    logger.log(Level.FINE, 
			       "  modifying attributes in lookup service -- "
			       +loc);
		    changeAttributes(srvcReg,oldAttrs,chngAttr);
		    regInfo.srvcAttrs = chngAttr;
                    regInfoList.set(i,regInfo);
                }//end loop(i)
                pair.setValue( regInfoList );
            }//end loop(itr)
        }//end sync(regInfoMap)
        logger.log(Level.FINE, "waiting "+(eventDelay/1000)+" seconds for "
                               +"serviceChanged events ...");
        DiscoveryServiceUtil.delayMS(eventDelay);
        verifyCurrentEvents();
    }//end changeAttrAndVerify

    /** This method creates a cache, registers with the local event mechanism
     *  of the cache, and verifies that the expected number of serviceAdded
     *  events are received. The event mechanism is configured to use 
     *  matching criteria that includes no filter and a template consisting
     *  of only the class type of the various test service(s) that were
     *  registered.
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     */
    protected void createCacheAndVerify() throws Exception {
        createCacheAndVerify(template);
    }//end createCacheAndVerify

    protected void createCacheAndVerify(ServiceTemplate tmpl) throws Exception {
        /* Create the cache. */
	logger.log(Level.FINE, "requesting a lookup cache");
	srvcListener = new AbstractBaseTest.SrvcListener
	    (getConfig(),"");
	cache = srvcDiscoveryMgr.createLookupCache(tmpl,
						   null,//filter
						   srvcListener);
	cacheList.add(cache);
        delayOnCache();
        verifyCurrentEvents(nAddedExpected,0,0);
    }//end createCacheAndVerify

    /* Enters a finite (or infinite) wait state to allow a cache to populate */
    protected void delayOnCache() {
        logger.log(Level.FINE, "waiting "+(cacheDelay/1000)+" seconds to "
                          +"allow the cache to be populated ... ");
        DiscoveryServiceUtil.delayMS(cacheDelay);
    }//end delayOnCache

    /** This method registers the configured number of test service(s) with 
     *  the configured number of lookup service(s) that were started during
     *  construct. Additionally, for each lookup service with which a given
     *  service is registered, this method associates the configured number
     *  of test attributes. After registration is complete, this method
     *  verifies that each registration was successful by performing a
     *  blocking lookup on the service discovery manager created during
     *  construct. 
     *
     *  To verify the success of each registration, a template reflecting
     *  both the class type of the registered service(s) and the exact,
     *  single attribute of the service (based on knowledge of how the
     *  method registerServices() registers attributes) is used in the call
     *  to lookup.
     * 
     *  This method will declare failure if either the number of registered
     *  services is 0, or the number of attributes is not 1.
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     */
    protected void registerAndVerify(long waitDur) throws Exception {
        if(getnServices() <= 0) throw new TestException
	    ("no service(s) registered -- check the configuration file");
        if(getnAttributes() != 1) throw new TestException
	    ("# of attributes registered with "
	     +"each service ("+getnAttributes()+") != 1 "
	     +"-- check the configuration file");
        /* Register each test service and its corresponding attribute. */
	registerServices(0, getnServices(), getnAttributes(),testServiceType);
        String testServiceClassname 
        = "com.sun.jini.test.spec.servicediscovery.AbstractBaseTest$TestService";
        TestService[] expectedService = new TestService[getnServices()];
        ServiceTemplate[] lookupTmpl  = new ServiceTemplate[getnServices()];
        for(int i=0;i<expectedService.length;i++) {
            int val = SERVICE_BASE_VALUE+i;
            expectedService[i] = new TestService(val);
            lookupTmpl[i] = null; 
            /* Create template that matches on type and attribute */
	    Class c = Class.forName(testServiceClassname);
	    Entry[] attrs = new Entry[1];
	    attrs[0] = new TestServiceIntAttr(val);
	    lookupTmpl[i] = new ServiceTemplate(null,new Class[]{c},attrs);
            logger.log(Level.FINE, "looking up TestService."+val
                              +" -- blocking "+waitDur/1000+" second(s)");
        }//end loop(i)
        verifyServiceRegistration(expectedService,lookupTmpl,waitDur);
    }//end registerAndVerify

    protected void verifyServiceRegistration(Object[] expectedService,
					     ServiceTemplate[] lookupTmpl,
					     long waitDur)
	throws Exception
    {
	if(expectedService.length != lookupTmpl.length) {
	    throw new TestException
		("number of services registered ("
		 +expectedService.length
		 +") NOT equal to number of templates ("
		 +lookupTmpl.length+")");
	}
	long waitDurSecs = waitDur/1000; //for debug output
	/* Loop through the expected service(s):
	 *   -- using the service's corresponding template, perform a blocking
	 *      lookup on the service discovery manager
	 *   -- verify the expected service was returned by the lookup
	 */
	for(int i=0;i<expectedService.length;i++) {
	    /* Try to lookup the service, block until the service appears */
	    long startTime = System.currentTimeMillis();
	    ServiceItem srvcItem = srvcDiscoveryMgr.lookup(lookupTmpl[i],
							   null,//filter
							   waitDur);
	    long endTime = System.currentTimeMillis();
	    long actualBlockTime = endTime-startTime;
	    long waitError = (actualBlockTime-waitDur)/1000;
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
		/* template does exact match on attribute value, using a
		 * less restrictive template could return any service
		 */
		for(int j=0;j<getExpectedServiceList().size();j++) {
		    if( (srvcItem.service).equals(expectedService[i]) ) {
			logger.log(Level.FINE, "expected "
				   +"service found -- requested "
				   +"block = "+waitDurSecs
				   +" second(s), actual block = "
				   +(actualBlockTime/1000)
				   +" second(s)");
		    } else {
			if(expectedService[i] instanceof
			   AbstractBaseTest.TestService)
			    {
				logger.log(Level.FINE, "unexpected "
					   +"service found -- expected "
					   +"value = "
					   +((AbstractBaseTest.TestService)expectedService[i]).i
					   +", value returned = "
					   +((AbstractBaseTest.TestService)srvcItem.service).i);
				throw new TestException
				    (" -- unexpected service found -- "
				     +"expected value = "
				     +((AbstractBaseTest.TestService)expectedService[i]).i
				     +", value returned = "
				     +((AbstractBaseTest.TestService)srvcItem.service).i);
			    } else {
				logger.log(Level.FINE, "unexpected "
					   +"service found -- expected "
					   +"value = "
					   +expectedService[i]
					   +", value returned = "
					   +(srvcItem.service));
				throw new TestException
				    (" -- unexpected service found -- "
				     +"expected value = "+expectedService[i]
				     +", value returned = "
				     +(srvcItem.service));
			    }//endif(instance of AbstractBaseTest.TestService)
		    }//endif
		}//end loop (j)
	    }//endif(srvcItem==null)
	}//end loop(i)
	return;//no problems
    }//end verifyServiceRegistration

}//end class ModifyAttrServiceChanged


