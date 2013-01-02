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

import com.sun.jini.test.share.AttributesUtil;
import com.sun.jini.test.share.BaseQATest;
import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.share.GroupsUtil;
import com.sun.jini.test.share.LocatorsUtil;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;

import net.jini.lease.LeaseRenewalManager;

import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceIDListener;

import net.jini.lookup.entry.ServiceControlled;

import net.jini.core.discovery.LookupLocator;

import net.jini.core.entry.Entry;

import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.jini.config.ConfigurationException;

/**
 * This class is an abstract class that acts as the base class which
 * most, if not all, tests of the <code>JoinManager</code> utility class
 * should extend.
 * 
 * This class contains a static inner class in which multiple instances
 * can be created and used as test services, registering each instance
 * with the various lookup services that are created and used in the
 * various test classes that sub-class this class.
 * 
 * This class also contains static inner class that can be used as a filter
 * in the various test classes that test the methods of the service
 * discovery manager that interact with a filter object.
 * <p>
 * This class provides an implementation of the <code>construct</code> method
 * which performs standard functions related to the initialization of the
 * system state necessary to execute the test.
 *
 * Any test class that extends this class is required to implement the 
 * <code>run</code> method which defines the actual functions that must
 * be executed in order to verify the assertions addressed by that test.
 */
abstract public class AbstractBaseTest extends BaseQATest {

    /** Class whose different instances will be registered with various
     *  lookup services; each of which is expected to be discovered by the
     *  ServiceDiscoveryManager. 
     *  
     *  Note that this class is a <i>static</i> nested class. If this class
     *  weren't declared static, it would have an implicit reference to the
     *  outer class that creates it. Thus, when this class is registered
     *  with any lookup service, since it must be serialized in order to
     *  be registered, without the static declaration an attempt to
     *  serialize the outer class would also be made; resutling in a 
     *  <code>NotSerializableException</code>.
     */
    public static class TestService implements Serializable {
        public int i;
        public TestService(int i) {
            this.i = i;
        }//end constructor
        public boolean equals(Object obj) {
            try {
                if ( this == obj ) {
                    return true;
                } else if (    ((obj.getClass()).equals(TestService.class))
                            && (((TestService)obj).i == i) ) {
                    return true;
                } else {
                    return false;
                }
            } catch (NullPointerException e) {
                return false;
	    }
        }//end equals
        public String toString() {
            return ( (this.getClass()).getName()+" (i="+i+")");
        }//end toString
    }//end class TestService

    /** Serializable class that implements its own <code>writeObject</code>
     *  method containing a time delay prior to serialization.
     *
     *  When this class is included as a field in any attribute class whose
     *  instances are elements of attribute sets used to augment, replace,
     *  or change a service's current attributes, the processing of those
     *  attributes is delayed. Using this class to impose such an artificial
     *  delay aids the testing of the synchonization of multi-threaded 
     *  implementations.
     *
     *  @see SlowTestServiceIntAttr
     */
    public static class SlowAttrField implements Serializable {
        private void writeObject(ObjectOutputStream stream) throws IOException{
            try{Thread.sleep(1*1000);}catch(InterruptedException e){}
            stream.defaultWriteObject();
        }//end writeObjectStream
    }//end class SlowAttrField

    /** Class used as one type of attribute that can be associated with the
     *  service under test. Template matching is performed on this class
     *  through a single <code>Integer</code> value.
     */
    public static class TestServiceIntAttr implements Entry {
        public Integer val = null;
        public TestServiceIntAttr() { } //required no-arg constructor
        public TestServiceIntAttr(int i) {
            this.val = new Integer(i);
        }//end constructor
        public boolean equals(Object obj) {
            if ( this == obj ) return true;
            try {
                if (obj instanceof TestServiceIntAttr) { 
                    return fieldsMatch(obj);
                } else {
                    return false;
                }
            } catch (Exception e) {
                return false;
	    }
        }//end equals
        private boolean fieldsMatch(Object obj) {
            try {
                if( ((TestServiceIntAttr)obj).val == val )       return true;
                if( ((TestServiceIntAttr)obj).val.equals(val) )  return true;
                return false;
            } catch(Exception e) {
                return false;
	    }
        }//end fieldsMatch
    }//end class TestServiceIntAttr

    /** Class used to inject an artificial delay in the serialization process
     *  of the <code>TestServiceIntAttr</code> attribute class when augmenting,
     *  replacing, or changing the service's attributes. This attribute class
     *  should be used in any tests that attempt to verify the synchronization
     *  of the various attribute modification tasks employed by the current
     *  implementation of the join manager.
     */
    public static class SlowTestServiceIntAttr extends TestServiceIntAttr {
        public SlowAttrField slowAttr = new SlowAttrField();
        public SlowTestServiceIntAttr() {//required no-arg constructor
            super();
        }//end constructor
        public SlowTestServiceIntAttr(int i) {
            super(i);
        }//end constructor
    }//end class SlowTestServiceIntAttr

    /** Class used as one type of attribute that can be associated with the
     *  service under test. Template matching is performed on this class
     *  through a single <code>String</code> value.
     */
    public static class TestServiceStringAttr implements Entry {
        public String val = null;
        public TestServiceStringAttr() { } //required no-arg constructor
        public TestServiceStringAttr(String s) {
            this.val = new String(s);
        }//end constructor
        public boolean equals(Object obj) {
            if ( this == obj ) return true;
            try {
                if ( (obj.getClass()).equals(TestServiceStringAttr.class) ) { 
                    return fieldsMatch(obj);
                } else {
                    return false;
                }
            } catch (Exception e) {
                return false;
	    }
        }//end equals
        private boolean fieldsMatch(Object obj) {
            try {
                if( ((TestServiceStringAttr)obj).val == val )      return true;
                if( ((TestServiceStringAttr)obj).val.equals(val) ) return true;
                return false;
            } catch(Exception e) {
                return false;
	    }
        }//end fieldsMatch
    }//end class TestServiceStringAttr

    /** Class used to test methods that take a <code>ServiceControlled</code>
     *  parameter.
     */
    public static class ServiceControlledAttr extends    TestServiceIntAttr
                                              implements ServiceControlled
    {
        public ServiceControlledAttr() { } //required no-arg constructor
        public ServiceControlledAttr(int i) {
            super(i);
        }//end constructor
    }//end class ServiceControlledAttr

    /** Listener class used to receive -- upon service registration -- LOCAL
     *  notification from the join manager when a serviceID is assigned by
     *  a lookup service with which the service registers. Each different
     *  service instance should be associated with a new instance of this
     *  class.
     */
    protected class SrvcIDListener implements ServiceIDListener {
        private Object srvc;
        private int nEvents;
        public SrvcIDListener(Object srvc) {
            this.srvc = srvc;
            this.nEvents = 0;
        }//end constructor
        public void serviceIDNotify(ServiceID serviceID) {
            logger.log(Level.FINE, 
                              "Service ID Event -- "+srvc+" -- "
                              +serviceID);
            synchronized(srvcToNEvents) {
                nEvents++;
                srvcToNEvents.put(srvc,new Integer(nEvents));
                logger.log(Level.FINE, "Current # of service ID "
                                                +"Events -- "+nEvents);
            }//end sync
        }//end serviceIDNotify
    }//end class SrvcIDListener

    protected final static int SERVICE_BASE_VALUE = 667;

    protected volatile LookupDiscoveryManager discoveryMgr = null;
    protected volatile LeaseRenewalManager leaseMgr        = null;

    protected volatile JoinManager joinMgrCallback         = null;
    protected volatile JoinManager joinMgrSrvcID           = null;
    protected final List<JoinManager> joinMgrList          = new CopyOnWriteArrayList<JoinManager>();

    protected volatile LookupListener mainListener         = null;

    protected final TestService testService    = new TestService(SERVICE_BASE_VALUE);
    protected final ServiceID serviceID        = new ServiceID(0,SERVICE_BASE_VALUE);
    protected volatile ServiceIDListener callback = null;
    protected volatile Entry[] serviceAttrs       = null;
    protected volatile Entry[] newServiceAttrs    = null;
    protected volatile Entry[] attrTmpls          = null;
    protected volatile ServiceTemplate template   = null;
    protected final HashMap<Object,Integer> srvcToNEvents = new HashMap<Object,Integer>(5); //use synchronized to access.

    /** Constructs and returns the LookupDiscoveryManager to use when
     *  constructing a JoinManager (can be overridden by sub-classes)
     */
    protected LookupDiscoveryManager getLookupDiscoveryManager()
                                                         throws IOException
    {
        return getLookupDiscoveryManager(toGroupsArray(getAllLookupsToStart()),
                                         null);
    }//end getLookupDiscoveryManager

    protected LookupDiscoveryManager getLookupDiscoveryManager(QAConfig config)
                                                         throws IOException
    {
        return getLookupDiscoveryManager(toGroupsArray(getAllLookupsToStart()),
                                         null, config);
    }//end getLookupDiscoveryManager

    protected LookupDiscoveryManager getLookupDiscoveryManager
                                                       (String[] groupsToJoin)
                                                            throws IOException
    {
        return getLookupDiscoveryManager(groupsToJoin,null);
    }//end getLookupDiscoveryManager

    protected LookupDiscoveryManager getLookupDiscoveryManager
                                                 (LookupLocator[] locsToJoin)
                                                            throws IOException
    {
        return getLookupDiscoveryManager(DiscoveryGroupManagement.NO_GROUPS,
                                         locsToJoin);

    }//end getLookupDiscoveryManager

    protected LookupDiscoveryManager getLookupDiscoveryManager
                                                 (String[] groupsToJoin,
                                                  LookupLocator[] locsToJoin)
                                                            throws IOException
    {
        GroupsUtil.displayGroupSet(groupsToJoin,
                                   "groupsToDiscoverAndJoin",Level.FINE);
        LocatorsUtil.displayLocatorSet(locsToJoin,
                                      "locsToDiscoverAndJoin",Level.FINE);
	try {
	    return new LookupDiscoveryManager(groupsToJoin, 
					      locsToJoin,
					      mainListener,
					      getConfig().getConfiguration());
	} catch (ConfigurationException e) {
	    throw new RuntimeException("Configuration Error", e);
	}
    }//end getLookupDiscoveryManager

    protected LookupDiscoveryManager getLookupDiscoveryManager
                                                 (String[] groupsToJoin,
                                                  LookupLocator[] locsToJoin,
						  QAConfig config)
                                                            throws IOException
    {
        GroupsUtil.displayGroupSet(groupsToJoin,
                                   "groupsToDiscoverAndJoin",Level.FINE);
        LocatorsUtil.displayLocatorSet(locsToJoin,
                                      "locsToDiscoverAndJoin",Level.FINE);
	try {
	    return new LookupDiscoveryManager(groupsToJoin, 
					      locsToJoin,
					      mainListener,
					      config.getConfiguration());
	} catch (ConfigurationException e) {
	    throw new RuntimeException("Configuration Error", e);
	}
    }//end getLookupDiscoveryManager

    /** Constructs and returns the ServiceTemplate to use for matching
     *  (can be overridden by sub-classes)
     */
    protected ServiceTemplate getServiceTemplate()
                                               throws ClassNotFoundException
    {
        Class c = Class.forName
            ("com.sun.jini.test.spec.joinmanager.AbstractBaseTest$TestService");
	return new ServiceTemplate(null, new Class[]{c}, null);
    }//end getServiceTemplate

    /* Returns true if the service's value is odd */
    protected static boolean testServiceValOdd(TestService srvc) {
        if(srvc == null) return false;
        if( (srvc.i % 2) == 0 ) return false; // even
        return true; // odd
    }//end testServiceValEven

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *    <li> retrieves configuration values needed by the current test
     *    <li> starts the desired number lookup services (if any) with
     *         the desired configuration
     *    <li> creates attribute instances that are associated with the
     *         test service(s) registered with the instances of JoinManager
     *         being tested
     *    <li> creates a default ServiceTemplate for use with instances of
     (         JoinManager
     *    <li> creates a default listener for use with instances of JoinManager
     * </ul>
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        super.construct(sysConfig);
        if(getnAttributes() > 0) {
            serviceAttrs = new Entry[getnAttributes()];
            attrTmpls    = new Entry[getnAttributes()];
            for(int i=0;i<getnAttributes();i++) {
               serviceAttrs[i] = new TestServiceIntAttr
                                                     (SERVICE_BASE_VALUE + i);
                attrTmpls[i] = new TestServiceIntAttr(SERVICE_BASE_VALUE + i);
            }//end loop
        }//endif
        if(getnAddAttributes() > 0) {
            if(getnAttributes() <= 0) serviceAttrs = new Entry[0];
            newServiceAttrs = new Entry[getnAddAttributes()];
            for(int i=0;i<getnAddAttributes();i++) {
                newServiceAttrs[i] = new TestServiceIntAttr
                                       (SERVICE_BASE_VALUE + getnAttributes() + i);
            }//end loop
        }//endif
        template = getServiceTemplate();
        mainListener = new LookupListener();
        return this;
    }//end construct

    /** Executes the current test
     */
    abstract public void run() throws Exception;

    /** Cleans up all state. Terminates any discovery manager(s) and any
     *  join manager(s) that may have been created, shutdowns any lookup
     *  service(s) that may have been started, and performs any standard
     *  clean up duties performed in the super class.
     */
    public void tearDown() {
        try {
            int mod = 1;
            int n = joinMgrList.size();
            if(n > 20) {
                if(n >= 10000) {
                    mod = 1000;
                } else if( (n >= 1000) && (n < 10000) ) {
                    mod = 100;
                } else if( (n >= 100) && (n < 1000) ) {
                    mod = 50;
                } else {
                    mod = 10;
                }//endif
            }//endif
            /* Terminate each joinmanager and each discovery manager created */
            for(int i=0;i<joinMgrList.size();i++) {
                JoinManager joinMgr = (JoinManager)joinMgrList.get(i);
                DiscoveryManagement discMgr = joinMgr.getDiscoveryManager();
                /* If N join mgrs (N large), show only some debug info */
                boolean show = ( ((i%mod == 0)||(i == n-1)) ? true : false);
                try {
                    if(show) logger.log(Level.FINE, 
                                  "tearDown - terminating join manager "+i);
                    joinMgr.terminate();
                } catch(Exception e) {
                    e.printStackTrace();
                }
                try {
                    if(show) logger.log(Level.FINE, 
                             "tearDown - terminating discovery manager "+i);
                    discMgr.terminate();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }//end loop
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
	    super.tearDown();
	}
     }//end tearDown

    /** Waits for the join manager to register the test service with all
     *  lookup services that have been started. Waits at least nSecsJoin
     *  seconds, but no more than 2*nSecsJoin seconds to allow for the
     *  possibility that more events than expected may arrive.
     */
    protected void verifyJoin() throws Exception {
        logger.log(Level.FINE, "waiting at least "+getnSecsJoin()
                                       +" seconds but no more than "
                                       +(2*getnSecsJoin())+" seconds for join ...");
        /* Wait at least nSecsJoin */
        for(int i=0;i<getnSecsJoin();i++) {
            DiscoveryServiceUtil.delayMS(1000);
        }//end loop
        /* Wait no more than another nSecsJoin */
        List lusList = getLookupListSnapshot
                                              ("AbstractBaseTest.verifyJoin");
        List regList = new ArrayList(lusList.size());
        long T0 = System.currentTimeMillis();
        long deltaT = 0;
        long maxT   = getnSecsJoin() * 1000; //work in milliseconds
        while(deltaT < maxT) {
            for(int i=0;i<lusList.size();i++) {
                ServiceRegistrar reg = (ServiceRegistrar)lusList.get(i);
                if(regList.contains(reg)) continue;
                try {
                    Object testService = reg.lookup(template);
                    if(testService instanceof TestService) {
                        regList.add(reg);
                    }//endif
                } catch(RemoteException e) { /* try again*/ }
            }//end loop
            if(regList.size() == lusList.size()) break;
            DiscoveryServiceUtil.delayMS(1000);
            deltaT = System.currentTimeMillis() - T0;
        }//end loop
        logger.log(Level.FINE, "JOIN wait period complete");
        if(regList.size() != lusList.size()) {
            throw new TestException("join failed -- instance of TestService found "
                              +"registered in "+regList.size()+" lookup "
                              +"service(s) out of a total of "
                              +lusList.size()+" lookup service(s)");
        }//endif
    }//end verifyJoin

    /** Waits for the join manager to register the test service with all
     *  lookup services that have been started, and then waits for the
     *  expected number of ServiceID events. Waits at least nSecsJoin
     *  seconds, but no more than 2*nSecsJoin seconds to allow for the
     *  possibility that more events than expected may arrive.
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     */
    protected void verifyJoin(int nEventsExpected) throws Exception {
        verifyJoin();
        /* Wait no more than another nSecsJoin for the service ID events */
        int nEventsRcvd = 0;
        for(int i=0;i<getnSecsJoin();i++) {
            synchronized(srvcToNEvents) {
                if(srvcToNEvents.size() > 0) {
                    Integer objN = (Integer)srvcToNEvents.get(testService);
                    if(objN != null) {
                        nEventsRcvd = objN.intValue();
                        if(    (nEventsExpected > 0)
                            && (nEventsRcvd >= nEventsExpected) )
                        {
                            break;
                        }//endif
                    }//endif(objN != null)
                }//endif(srvcToNEvents.size() > 0)
            }//end sync
            DiscoveryServiceUtil.delayMS(1000);
        }//end loop
        if(nEventsRcvd != nEventsExpected) {
            throw new TestException("join failed -- waited "+getnSecsJoin()
                              +" seconds -- "+nEventsExpected
                              +" ServiceID event(s) expected, "+nEventsRcvd
                              +" ServiceID event(s) received");
        }//endif
        logger.log(Level.FINE, ""+nEventsExpected
                                        +" ServiceID event(s) expected, "
                                        +nEventsRcvd
                                        +" ServiceID event(s) received");
        /* Retrieve and store the service's ID as stored in each lookup */
        List lusList = getLookupListSnapshot
                                              ("AbstractBaseTest.verifyJoin");
        List srvcIDs = new ArrayList(lusList.size());
        for(int i=0;i<lusList.size();i++) {
            ServiceRegistrar reg = (ServiceRegistrar)lusList.get(i);
	    /* Verify 1 service registered with lookup service i */
	    ServiceMatches matches = reg.lookup(template,
						Integer.MAX_VALUE);
	    if(matches.totalMatches == 0) {
		throw new TestException("lookup service "+i
                                        +" -- no matching service found");
	    } else if(matches.totalMatches != 1) {
		throw new TestException("lookup service "+i+" -- totalMatches ("
					+matches.totalMatches+") != 1");
	    }//endif
	    /* Verify that the given attributes were propagated */
	    ServiceID srvcID = matches.items[0].serviceID;
	    logger.log(Level.FINE, "lookup service "+i
		       +" -- test service ID = "+srvcID);
	    srvcIDs.add(srvcID);
        }//end loop
        /* Verify the service's ID is consistent across all lookups */
        ServiceID sid0 = (ServiceID)srvcIDs.get(0);
        for(int i=1;i<srvcIDs.size();i++) {
            ServiceID sid1 = (ServiceID)srvcIDs.get(i);
            if( !sid0.equals(sid1) ) {
                throw new TestException ("service ID is not the same in "
                                    +"all lookups");
            }//endif
        }//end loop
        logger.log(Level.FINE, "service ID is the same in all lookups");
    }//end verifyJoin

    /** Verifies that the contents of the set of attributes returned by the
     *  <code>getAttributes</code> method of the given <code>JoinManager</code>
     *  equals the contents of the given set of attributes. Prior to
     *  verifying the contents of the attribute sets, verifies that the
     *  test service has been registered with the intended lookup services
     *  (if any).
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     */
    protected void verifyAttrsInJoinMgr(JoinManager joinMgr, Entry[] attrs) 
	throws Exception
    {
        List lusList = getLookupListSnapshot
                                     ("AbstractBaseTest.verifyAttrsInJoinMgr");
        if(lusList.size() > 0) {
            verifyJoin(1);
        }//endif
        Entry[] joinMgrAttrs = joinMgr.getAttributes();
        logger.log(Level.FINE, "comparing attributes from join "
                          +"manager with expected attributes ...");
        if (!AttributesUtil.compareAttributeSets(attrs,
						 joinMgrAttrs, 
						 Level.FINE)) 
	{
            throw new TestException("attributes from join manager "
				    + "not equal to expected attributes");
	}
    }//end verifyAttrsInJoinMgr

    /** Verifies that the test service is registered with all lookup services
     *  the join manager is configured to discover. And verifies that the
     *  given set of attributes are equal to the set of attributes associated
     *  with the test service in each lookup service in which that test
     *  service is registered. That is, this method verifies that the given
     *  set of attributes were propagated to each lookup service with which
     *  the test service is registered.
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     */
    protected void verifyPropagation(Entry[] attrs) throws Exception {
        verifyPropagation(attrs, 0);
    }//end verifyPropagation

    /** Verifies that the test service is registered with all lookup services
     *  the join manager is configured to discover. And verifies that the
     *  given set of attributes are equal to the set of attributes associated
     *  with the test service in each lookup service in which that test
     *  service is registered. That is, this method verifies that the given
     *  set of attributes were propagated to each lookup service with which
     *  the test service is registered. This method waits the given number
     *  of seconds (<code>nSecsWait</code>) for the propagation to complete
     *  successfully.
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     */
    protected void verifyPropagation(Entry[] attrs, int nSecsWait) 
	throws Exception
    {
        List lusList = getLookupListSnapshot
                                       ("AbstractBaseTest.verifyPropagation");
        if(lusList.size() > 0) {
            if(nSecsWait > 0) {
                logger.log(Level.FINE, "waiting at least "
                                  +nSecsWait+" seconds but no more than "
                               +(2*nSecsWait)+" seconds for propagation ...");
            }//endif
            /* Wait at least nSecsWait */
            for(int i=0;i<nSecsWait;i++) {
                DiscoveryServiceUtil.delayMS(1000);
            }//end loop
            /* Wait no more than another nSecsWait */
            List regList = new ArrayList(lusList.size());
            for(int i=0;i<lusList.size();i++) {
                regList.add(lusList.get(i));
            }//end loop
            long T0 = System.currentTimeMillis();
            long deltaT = 0;
            long maxT   = nSecsWait * 1000; //work in milliseconds
            while(deltaT <= maxT) {
                int nRegs = regList.size();
                for(int i=0;i<nRegs;i++) {
                    /* Pop the stack of lookups */
                    ServiceRegistrar reg = (ServiceRegistrar)regList.remove(0);
		    /* Verify 1 service registered with lookup service i */
		    ServiceMatches matches = reg.lookup(template,
							Integer.MAX_VALUE);
		    if(matches.totalMatches == 0) {
			throw new TestException("lookup service "+i
					     +" -- no matching service found");
		    } else if(matches.totalMatches != 1) {
			throw new TestException("lookup service "+i
						+" -- totalMatches ("
						+matches.totalMatches
						+") != 1");
		    }//endif
		    /* Verify that the given attributes were propagated */
		    Entry[] lookupAttrs = matches.items[0].attributeSets;
		    logger.log(Level.FINE, "lookup service "+i
			       +" -- comparing attributes");
		    if(!AttributesUtil.compareAttributeSets(attrs,
							    lookupAttrs,
							    Level.OFF))
                        {
                            logger.log(Level.FINE, 
				       "                 -- NO MATCH");
                            regList.add(reg);// push it back on the stack
                        } else {
                            logger.log(Level.FINE, 
				       "                 -- attributes MATCH");
                        }//endif
                 }//end loop
                if(regList.size() == 0) break;
                DiscoveryServiceUtil.delayMS(1000);
                deltaT = System.currentTimeMillis() - T0;
            }//end loop
            /* If propagation failed, display expected & actual attributes */
            if(regList.size() > 0) {
                String retStr = "attributes not propagated successfully "
                                   +"on "+regList.size()+" lookup service(s)";
                logger.log(Level.FINE, retStr);
                AttributesUtil.displayAttributeSet(attrs,
                                                   "expectedAttrs",
                                                   Level.FINE);
                for(int i=0;i<regList.size();i++) {
                    ServiceRegistrar reg = (ServiceRegistrar)regList.get(i);
		    ServiceMatches matches = reg.lookup(template,
							Integer.MAX_VALUE);
		    if(matches.totalMatches != 1) {
			logger.log(Level.FINE, 
				   "lookup service "+i+" -- totalMatches "
				   +"invalid ("+matches.totalMatches+") ... "
				   +"no attributes to compare");
		    }//endif
		    Entry[] lookupAttrs = matches.items[0].attributeSets;
		    AttributesUtil.displayAttributeSet(lookupAttrs,
						       ("lookup service "+i),
						       Level.FINE);
                }//end loop
		throw new TestException(retStr);
            }//endif
        } else {//(lusList.size() <= 0)
            throw new TestException("no lookup services started");
        }//endif(lusList.size() > 0)
    }//end verifyPropagation

} //end class AbstractBaseTest


