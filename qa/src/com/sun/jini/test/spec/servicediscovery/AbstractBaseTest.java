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

package com.sun.jini.test.spec.servicediscovery;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.BaseQATest;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.share.GroupsUtil;
import com.sun.jini.test.share.LocatorsUtil;

import com.sun.jini.qa.harness.TestException;

import net.jini.admin.Administrable;

import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryManager;

import net.jini.lease.LeaseRenewalEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.DesiredExpirationListener;

import net.jini.lookup.ServiceDiscoveryEvent;
import net.jini.lookup.ServiceDiscoveryListener;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.lookup.ServiceItemFilter;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;

import java.io.IOException;
import java.io.Serializable;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import net.jini.config.ConfigurationException;
import net.jini.discovery.DiscoveryManagement2;

/**
 * This class is an abstract class that acts as the base class which
 * most, if not all, tests of the <code>ServiceDiscoveryManager</code>
 * utility class should extend.
 * <p>
 * This class contains a static inner class in which multiple instances
 * can be created and used as test services, registering each instance
 * with the various lookup services that are created and used in the
 * test classes that extend this class.
 * <p>
 * This class also contains static inner class that can be used as a filter
 * in the various test classes that test the methods of the service
 * discovery manager that interact with a filter object.
 * <p>
 * This class provides an implementation of the <code>setup</code> method
 * which performs standard functions related to the initialization of the
 * system state necessary to execute the test.
 *
 * Any test class that extends this class is required to implement the 
 * <code>applyTestDef</code> method which defines the actual functions
 * that must be executed in order to verify the assertions addressed by
 * that test, and which is called by the common <code>run</code> method
 * defined in this class.
 * 
 * @see com.sun.jini.qa.harness.TestException
 * @see com.sun.jini.qa.harness.QAConfig
 */
abstract public class AbstractBaseTest extends BaseQATest {

    /** Note that for convenience, a number of inner classes are defined below.
     *  Each such inner class that is defined as <code>Serializable</code>
     *  is also defined as a <i>static</i> nested class. Each such class
     *  is made static because if the class were not static, it would have
     *  an implicit reference to the outer class that creates it.
     *  
     *  Whenever a serializable nested class is actually serialized (for 
     *  example, when a test service is registered with a lookup service,
     *  or when an attribute is added to the set of attributes associated
     *  with a registered service), it must be static; otherwise the implicit
     *  reference to the outer class would result in the occurrence of a
     *  <code>NotSerializableException</code>. This is because when
     *  serializing the nested class, the implicit reference to the outer
     *  class would cause the serialization mechanism to also attempt to
     *  serialize the outer class, which is not serializable.
     */
    protected final static int TEST_SERVICE            = 0;
    protected final static int TEST_SERVICE_BAD_EQUALS = 1;

    public interface TestServiceInterface {
        public boolean equals(Object obj);
    }//end TestServiceInterface

    /** Class whose different instances will be registered with various
     *  lookup services; each of which is expected to be discovered by the
     *  ServiceDiscoveryManager. 
     */
    public static class TestService implements Serializable, Administrable,
                                               TestServiceInterface
    {
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
        /* From Administable: for filtering "bad" services from the browser */
        public Object getAdmin() throws RemoteException {
            return null;
        }//end getAdmin
    }//end class TestService

    /** Class with an equals method that emulates Object.equals(). This class
     *  is used by tests that wish to simulate the "service removed/service
     *  added problem" that occurs when a service does not define a
     *  "good" equals() method and multiple lookup services are used.
     */
    public static class TestServiceBadEquals extends TestService {
        public TestServiceBadEquals(int i) {
            super(i);
        }//end constructor
        public boolean equals(Object obj) {
            if ( this == obj ) return true;
            return false;
        }//end equals
    }//end class TestServiceBadEquals

    /** Class used as one type of attribute that can be associated with the
     *  various service(s) under test. Template matching is performed on
     *  this class through a single <code>Integer</code> value.
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
                if ( (obj.getClass()).equals(TestServiceIntAttr.class) ) { 
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

    /* Filter that returns a service only if its value is divisible by 2 */
    public static class TestFilter2 implements ServiceItemFilter {
        public boolean check(ServiceItem item) {
            if(item == null) return false;
            return srvcValDivisibleByN((TestService)item.service,2);
        }//end check
    }//end class TestFilter2

    /* Filter that returns a service only if its value is divisible by 3 */
    public static class TestFilter3 implements ServiceItemFilter {
        public boolean check(ServiceItem item) {
            if(item == null) return false;
            return srvcValDivisibleByN((TestService)item.service,3);
        }//end check
    }//end class TestFilter3

    /* Filter that returns a service only if its value is divisible by 3,
     * but not by 2.
     */
    public static class TestFilter3Not2 implements ServiceItemFilter {
        public boolean check(ServiceItem item) {
            if(item == null) return false;
            if(srvcValDivisibleByN((TestService)item.service,2)) return false;
            return srvcValDivisibleByN((TestService)item.service,3);
        }//end check
    }//end class TestFilter3Not2

    /* Filter that returns a service only if its value is divisible by 4 */
    public static class TestFilter4 implements ServiceItemFilter {
        public boolean check(ServiceItem item) {
            if(item == null) return false;
            return srvcValDivisibleByN((TestService)item.service,4);
        }//end check
    }//end class TestFilter4

    /* Listener which receives notifications when a service is added, removed
     * or changed.
     */
    public static class SrvcListener implements ServiceDiscoveryListener {
        private QAConfig util;
        private String classname;
        private int nAdded   = 0;
        private int nRemoved = 0;
        private int nChanged = 0;
        private Object lock = new Object();
        public SrvcListener(QAConfig util, String classname) {
            this.util = util;
            this.classname = classname;
        }//end constructor
	public void serviceAdded(ServiceDiscoveryEvent event) {
            ServiceItem srvcItem = event.getPostEventServiceItem();
            ServiceID srvcID = srvcItem.serviceID;
            logger.log(Level.FINE, ""+nAdded+" -- serviceAdded()-"
                              +srvcItem.service+"-"+srvcID);
            synchronized(lock) {
                nAdded++;
            }
	}//end serviceAdded
	public void serviceRemoved(ServiceDiscoveryEvent event) {
            ServiceItem srvcItem = event.getPreEventServiceItem();
            ServiceID srvcID = srvcItem.serviceID;
            logger.log(Level.FINE, ""+nRemoved+" -- serviceRemoved()-"
                              +srvcItem.service+"-"+srvcID);
            synchronized(lock) {
                nRemoved++;
            }
	}//end serviceRemoved
	public void serviceChanged(ServiceDiscoveryEvent event) {
            ServiceItem srvcItem = event.getPreEventServiceItem();
            ServiceID srvcID = srvcItem.serviceID;
            logger.log(Level.FINE, ""+nChanged+" -- serviceChanged()-"
                              +srvcItem.service+"-"+srvcID);
            synchronized(lock) {
                nChanged++;
            }
	}//end serviceChanged
        public int getNAdded() {
            synchronized(lock) {
                return nAdded;
            }
        }//end getNAdded
        public int getNRemoved() {
            synchronized(lock) {
                return nRemoved;
            }
        }//end getNRemoved
        public int getNChanged() {
            synchronized(lock) {
                return nChanged;
            }
        }//end getNChanged
    }//end class SrvcListener

    /* Data structure representing information about a services registration */
    public static class RegInfo {
        public ServiceID srvcID;
        public ServiceRegistration srvcReg;
        public Lease srvcLease;
        public Entry[] srvcAttrs;
        public ServiceRegistrar lookupProxy;//lookup that granted srvcReg
    }//end class RegInfo

    /* Listener which receives notifications when a service lease expires
     * or an exception occurs related to a service lease.
     */
    public class ExpirationListener implements DesiredExpirationListener {
        private QAConfig util;
        private String classname;
        private int nExpired = 0;
        private Object lock  = new Object();
        public ExpirationListener(QAConfig util, String classname) {
            this.util = util;
            this.classname = classname;
        }//end constructor
        public void notify(LeaseRenewalEvent ev) {
            Throwable leaseException = ev.getException();
            logger.log(Level.FINE, "LeaseRenewalException --");
            leaseException.printStackTrace();
        }//end notify
        public void expirationReached(LeaseRenewalEvent ev) {
            logger.log(Level.FINE, ""+nExpired
                              +" -- service lease expired");
            synchronized(lock) {
                nExpired++;
            }
        }//end expirationReached
    }//end class ExpirationListener

    /** Thread in which the service is registered with lookup. This thread
     *  is intended to be used by tests that need to register services 
     *  after a call to lookup() has been initiated; typically when testing
     *  the blocking mechanism of the lookup mechanism. Upon completion
     *  of the registration process, this thread will exit.
     */
    protected class RegisterThread extends Thread {
        private int  startVal;
        private int  nSrvcs;
        private int  nAttrs;
        private long delay;
        public RegisterThread(int nSrvcs, int nAttrs, long waitDur) {
            super("RegisterThread");
            setDaemon(true);
            this.startVal = 0;
            this.nSrvcs   = nSrvcs;
            this.nAttrs   = nAttrs;
            this.delay    = (waitDur/4);
        }//end constructor
        public RegisterThread(int startVal,int nSrvcs,int nAttrs,long waitDur){
            super("registerThread");
            setDaemon(true);
            this.startVal = startVal;
            this.nSrvcs   = nSrvcs;
            this.nAttrs   = nAttrs;
            this.delay    = (waitDur/4);
        }//end constructor
        public void run() {
            DiscoveryServiceUtil.delayMS(delay);// give lookup time to block
            try {
                logger.log(Level.FINE, "RegisterThread -- "
                                  +"registering service(s) with lookup(s)");
                registerServices(startVal,nSrvcs,nAttrs);
            } catch(UnknownLeaseException e) {
                logger.log(Level.FINE, "UnknownLeaseException "
                                  +"occurred in RegisterThread");
                e.printStackTrace();
            } catch(RemoteException e) {
                logger.log(Level.FINE, "RemoteException occurred "
                                  +"in RegisterThread");
                e.printStackTrace();
            }
        }//end run
    }//end class RegisterThread

    protected final static long SERVICE_ID_VERSION = (0x1L << 12);
    protected final static long SERVICE_ID_VARIANT = (0x2L << 62);
    protected final static int  SERVICE_BASE_VALUE = 978;

    protected String testDesc 
                   = "AbstractBaseTest for ServiceDiscoveryManager.lookup()"
                    +" -- number of services -- ";

    protected String[] subCategories;

    protected boolean createSDMInSetup = true;
    protected boolean waitForLookupDiscovery = true;

    protected ServiceDiscoveryManager srvcDiscoveryMgr = null;
    protected ArrayList sdmList = new ArrayList(1);
    protected ArrayList cacheList = new ArrayList(1);

    protected ServiceTemplate   template    = null;
    protected ServiceItemFilter firstStageFilter   = null;
    protected ServiceItemFilter secondStageFilter = null;

    protected LeaseRenewalManager leaseRenewalMgr = null;
    private DesiredExpirationListener leaseListener;
    protected ArrayList leaseList = new ArrayList(1);

    protected long discardWait = 0;

    protected LookupListener mainListener = null;

    protected HashMap regInfoMap = new HashMap(1);
    protected int regCompletionDelay = 5*1000; //milliseconds
    protected int terminateDelay     = 7*1000; //milliseconds

    protected int nSecsServiceEvent = 40;
    protected int nSecsRemoteCall   = 5;

    /**
     * Override default values specified by the base class. 
     */
    public AbstractBaseTest() {
        useFastTimeout = true;//for faster lookup discovery
        fastTimeout = 30;//waits N seconds, then declares failure
    }

    /** Constructs and returns the LookupDiscoveryManager to use when
     *  constructing a ServiceDiscoveryManager (can be overridden by
     *  sub-classes)
     */
    protected LookupDiscoveryManager getLookupDiscoveryManager()
                                                         throws IOException
    {
        /* Construct the member groups with which to configure the
         * LookupDiscoveryManager utility
         */
        String[] groupsToDiscover = toGroupsArray(initLookupsToStart);
        GroupsUtil.displayGroupSet(groupsToDiscover,"  groupsToDiscover",
                                  Level.FINE);
        LookupLocator[] locsToDiscover = toLocatorArray(initLookupsToStart);
        LocatorsUtil.displayLocatorSet(locsToDiscover,"  locsToDiscover",
                                       Level.FINE);
	try {
	    return new LookupDiscoveryManager(groupsToDiscover,
					      locsToDiscover,
					      mainListener, 
					      getConfig().getConfiguration());
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
        ("com.sun.jini.test.spec.servicediscovery.AbstractBaseTest$TestService");
	return new ServiceTemplate(null, new Class[]{c}, null);
    }//end getServiceTemplate

    /** Constructs and returns the <code>ServiceItemFilter</code> that is to
     *  be applied either by the service discovery manager through a call
     *  to one of the versions of the <code>lookup</code> method on the
     *  service discovery manager, or by a lookup cache through a call to
     *  the <code>createCache</code> method.
     */
    protected ServiceItemFilter getFirstStageFilter() {
        return null;
    }//end getFirstStageFilter

    /** Constructs and returns the <code>ServiceItemFilter</code> that is to
     *  be applied by a lookup cache through a call to one of the versions
     *  of the <code>lookup</code> method on that cache.
     */
    protected ServiceItemFilter getSecondStageFilter() {
        return null;
    }//end getSecondStageFilter

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *    <li> starts the desired number lookup services (if any) with
     *         the desired configuration
     *    <li> constructs a lease renewal manager to manage the lease(s) of
     *         the service(s) registered with each lookup service
     *    <li> constructs and registers the expected service(s) with each
     *         lookup
     *    <li> submits the lease(s) of the registered service(s) with the
     *         lease renewal manager so that the lease(s) will continue to
     *         be renewed until the test completes (and so that there is no
     *         dependancy on the default value of the maximum lease duration
     *         used by the particular implementation of the lookup service
     *         being employed in the test)
     *    <li> constructs an instance of ServiceDiscoveryManager
     *    <li> constructs the template and filter to use for service discovery
     * </ul>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
	/* LeaseRenewalManager for services to be registered with lookup */
	leaseRenewalMgr =
	    new LeaseRenewalManager(sysConfig.getConfiguration());
	leaseListener   = new ExpirationListener(getConfig(),"");
	setupDiscardWait();
        getSetupInfo();
	mainListener = new LookupListener();
	mainListener.setLookupsToDiscover(initLookupsToStart);
	if(createSDMInSetup) {
	    /* Construct the ServiceDiscoveryManager that will be tested */
	    logger.log(Level.FINE, "constructing a service discovery manager");
	    srvcDiscoveryMgr = new ServiceDiscoveryManager
		( (DiscoveryManagement2) getLookupDiscoveryManager(),
		 null,  //LeaseRenewalManager
		 sysConfig.getConfiguration());
	    sdmList.add(srvcDiscoveryMgr);
	}//endif

	/* Construct the template and filter that will be used by lookup */
	template          = getServiceTemplate();
	firstStageFilter  = getFirstStageFilter();
	secondStageFilter = getSecondStageFilter();
    }//end setup

    /** Executes the current test by doing the following:
     *  
     *  1. When appropriate, verifies the service discovery manager can
     *     discover of the necessary lookup service(s)
     *  2. Runs the test
     */
    public void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(waitForLookupDiscovery) {
            /* Verify the necessary lookup service(s) were actually started,
             * and wait for the ServiceDiscoveryManager to discover all lookups
             */
            waitForDiscovery(mainListener);
        }//endif
        /* Setup ok, run the test */
        logger.log(Level.FINE, ""+testDesc);
        applyTestDef();
    }//end run

    /** Cleans up all state. Cancels all leases created during the test,
     *  destroys all services created during the test, and then performs
     *  any standard clean up duties performed in the super class.
     */
    public void tearDown() {
        try {
            logger.log(Level.FINE, "tearDown - cancelling service leases");
            /* Cancel all service leases created by this test */
            unregisterServices();
        } catch(UnknownLeaseException e) { 
            e.printStackTrace();
            logger.log(Level.FINE, "UnknownLeaseException while "
                              +"cancelling service lease");
        } catch(RemoteException e) {
            e.printStackTrace();
            logger.log(Level.FINE, "RemoteException while "
                              +"cancelling service lease");
        }
        if(terminateDelay > 0) {
            /* Wait before actually terminating. If termination occurs too 
             * quickly, then when caches are terminated, the remote listeners
             * that are registered with the lookup services by the cache may
             * actually be unexported during the event registration process,
             * or while events are arriving, etc. This can cause
             * marshal/unmarshal exceptions when shutting down lookup services.
             */
            logger.log(Level.FINE, "tearDown - waiting "
                              +(terminateDelay/1000)
                              +" second(s) to allow for settling before "
                              +"termination");
            DiscoveryServiceUtil.delayMS(terminateDelay);
        }//endif
        try {
           /* Terminate each service discovery manager and each discovery
            * manager created during the test run.
            */
            for(int i=0;i<sdmList.size();i++) {
                ServiceDiscoveryManager sdmMgr
                                    = (ServiceDiscoveryManager)sdmList.get(i);
                DiscoveryManagement2 discMgr = sdmMgr.discoveryManager();
                try {
                    logger.log(Level.FINE,
			 "tearDown - terminating service discovery manager "+i);
                    sdmMgr.terminate();
                } catch(Exception e) {
                    e.printStackTrace();
                }
                try {
                    logger.log(Level.FINE,
                             "tearDown - terminating discovery manager "+i);
                    discMgr.terminate();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }//end loop
        } catch(Exception e) {
            e.printStackTrace();
        }
        /* Destroy all lookup services registered with activation */
        super.tearDown();
    }//end tearDown

    /** Executes the actual steps of the particular test being run.
     *  
     *  This method will return <code>null</code> if there are no problems.
     *  If the <code>String</code> returned by this method is 
     *  non-<code>null</code>, then the test should declare failure and
     *  display the value returned by this method.
     *
     *  @return a <code>String</code> containing a failure message, or
     *           <code>null</code> if the test was successful.
     */
    abstract protected void applyTestDef() throws Exception;

    /* Returns true if the service's value is even */
    protected static boolean srvcValEven(TestService srvc) {
        return !srvcValOdd(srvc);
    }//end srvcValEven

    /* Returns true if the service's value is odd */
    protected static boolean srvcValOdd(TestService srvc) {
        if(srvc == null) return false;
        if( srvcValDivisibleByN(srvc,2) ) return false; //even
        return true; //odd
    }//end srvcValOdd

    /* Returns true if the service's value is divisible by the given value */
    protected static boolean srvcValDivisibleByN(TestService srvc, int n) {
        if(srvc == null) return false;
        if(n == 0) return false;
        if( (srvc.i % n) == 0 ) return true;//evenly divisible
        return false; //not evenly divisible
    }//end srvcValDivisibleByN

    /* Given an integer value, counts how many integers there are between
     * 0 and the given value that satisfy the current SDM filter. For example,
     * if the current SDM filter is TestFilter3, and the given value is
     * is 17, then this method will return a count of 6 since that filter
     * returns only those services having a value divisible by 3, and there
     * are 6 numbers between 0 (inclusive) and 17 (not inclusive) that are 
     * divisible by 3.
     */
    protected int countSrvcsByVal(int upperVal) {
        int modN = 1;
        if(firstStageFilter != null) {
            if(firstStageFilter instanceof TestFilter3Not2) {
                int count = 0;
                modN = 3;
                int notModN = 2;
                for(int i=0;i<upperVal;i++) {
                    if( ((i%modN) == 0) && ((i%notModN) != 0) ) {
                        count++;
                    }
                }//end loop
                return count;
            } else {
                if(firstStageFilter instanceof TestFilter2) modN = 2;
                if(firstStageFilter instanceof TestFilter3) modN = 3;
                if(firstStageFilter instanceof TestFilter4) modN = 4;
            }//endif
        }//endif
        int count = 0;
        for(int i=0;i<upperVal;i++) {
            if( (i%modN) == 0 ) count++;
        }//end loop
        return count;
    }//end countSrvcsByVal

    /** Constructs and registers the appropriate service(s) and associated
     *  attribute(s) based on the value of the given number of services
     *  and number of attributes.
     *
     *  This method is not called in the setup() method of this class because
     *  there are tests (for example, tests for the blocking versions of
     *  lookup()) that do not wish to have services pre-registered. Such
     *  tests typically wish to have more control over when the services
     *  being looked up are registered with the various lookup services
     *  started in setup().
     */
    protected void registerServices(int nSrvcs, int nAttrs) 
                               throws UnknownLeaseException, RemoteException
                                                              
    {
        registerServices(0,nSrvcs,nAttrs);
    }//end registerServices

    protected void registerServices(int startVal, int nSrvcs, int nAttrs) 
                               throws UnknownLeaseException, RemoteException
                                                              
    {
        registerServices(startVal,nSrvcs,nAttrs,TEST_SERVICE,false);
    }//end registerServices

    protected void registerServices(int startVal,
                                    int nSrvcs,
                                    int nAttrs,
                                    int serviceType) 
                               throws UnknownLeaseException, RemoteException
    {
        registerServices(startVal,nSrvcs,nAttrs,serviceType,false);
    }//end registerServices

    protected void reRegisterServices(int startVal,
                                      int nSrvcs,
                                      int nAttrs,
                                      int serviceType) 
                               throws UnknownLeaseException, RemoteException
    {
        registerServices(startVal,nSrvcs,nAttrs,serviceType,true);
    }//end registerServices

    protected void registerServices(int startVal,
                                    int nSrvcs,
                                    int nAttrs,
                                    int serviceType,
                                    boolean reRegister) 
                               throws UnknownLeaseException, RemoteException
                                                              
    {
        ArrayList lusList = getLookupListSnapshot
                                        ("AbstractBaseTest.registerServices");
        /* Construct and register the expected service(s) */
        int begIndx = ( (startVal >= 0) ? startVal : 0 );
        int endIndx = ( (nSrvcs > 0) ? (begIndx+nSrvcs) : 0 );
        for(int i=begIndx;i<endIndx;i++) {
            /* Whenever a service is registered in multiple lookups, it is
             * registered with a specific service ID. This is because if a
             * service ID is not specified, each lookup service will assign
             * a different service ID (since each test service is copied as
	     * a separate serializable object). This means that the 
             * ServiceDiscoveryManager will view one test service that is
             * registered in different lookup services as different services
             * (because ServiceDiscoveryManager looks at IDs when determining
             * if two services are equivalent). Thus, since these tests
             * generally want to work in an environment in which the same
             * service is registered in multiple lookups, registering with
             * a fixed, pre-set ID will simulate such an environment.
             */
            int idSeed  = SERVICE_BASE_VALUE + i;
            int srvcVal = idSeed;
            int attrVal = srvcVal;
            if(reRegister) srvcVal = srvcVal+9;//makes proxies not equal

            long lowBits = (1000+idSeed) >> 32;
            long leastSignificantBits = SERVICE_ID_VARIANT | lowBits;
            ServiceID srvcID =
                    new ServiceID( SERVICE_ID_VERSION, leastSignificantBits );
            /* Create each new instance of the service with a different
             * initial value. That value may be useful to some tests.
             */
            TestServiceInterface testService = null;
            switch(serviceType) {
                case TEST_SERVICE:
                    testService = new TestService(srvcVal);
                    break;
                case TEST_SERVICE_BAD_EQUALS:
                    testService = new TestServiceBadEquals(srvcVal);
                    break;
                default:
                    testService = new TestService(srvcVal);
                    break;
            }//end switch(serviceType)
            expectedServiceList.add( testService );
            /* Each test service is registered with at least one lookup. The
             * intent is to register some with all lookups, other services
             * with only some of the lookups. This is done so that when
             * a query is made through the ServiceDiscoveryManager, the
             * services are not all retrieved from the first lookup the
             * ServiceDiscoveryManager queries. A cyclic, round-robin 
             * mechanism is used to determine how many lookups the current
             * test service should register with.
             */
            int nLookups = ( (lusList.size() <= 0) ? 0 
                                  : (lusList.size() - (i%(lusList.size())) ) );
            ArrayList regInfoList = new ArrayList(nLookups);
            for(int j=0;j<nLookups;j++) {
                RegInfo regInfo = new RegInfo();
                int jndx = (lusList.size()-1) - j;
                ServiceRegistrar lookupProxy = 
                                        (ServiceRegistrar)lusList.get(jndx);
                LookupLocator loc = null;
                try {
                    loc = QAConfig.getConstrainedLocator(lookupProxy.getLocator());
                } catch(Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e.toString());
                }
                logger.log(Level.FINE, "registering test service "
                                              +i+" with lookup service "+loc);
                ServiceRegistration srvcReg =
                 lookupProxy.register(new ServiceItem(srvcID,testService,null),
                                      Long.MAX_VALUE);
		Lease srvcLease = null;
		try {
		    srvcReg = (ServiceRegistration) getConfig().prepare(
				   "test.reggieServiceRegistrationPreparer",
				   srvcReg);
		    srvcLease = (Lease) getConfig().prepare(
				   "test.reggieServiceRegistrationPreparer",
				   srvcReg.getLease());
		} catch (TestException e) {
		    throw new RemoteException("Configuration error", e);
		}
                regInfo.srvcID      = srvcID;
                regInfo.srvcReg     = srvcReg;
                regInfo.srvcLease   = srvcLease;
                regInfo.lookupProxy = lookupProxy;
                /* Renew the service's lease until the test is complete */
                leaseRenewalMgr.renewUntil(srvcLease,
                                           Lease.FOREVER, //expiration
                                           leaseListener);//listener
                if(reRegister) {
                    /* Remove old lease from local leaseList and from LRM */
                    leaseRenewalMgr.remove( (Lease)(leaseList.remove(0)) );
                }//endif
                /* Store the new lease for clean up at the end of the test */
                leaseList.add(srvcLease); //add the new lease
                /* Create and set attribute(s) for service just registered */
                if(nAttrs > 0) {
                    regInfo.srvcAttrs = new Entry[nAttrs];
                    Entry[] attrs = new Entry[nAttrs];
                    for(int k=0;k<nAttrs;k++) {
                        logger.log(Level.FINE, "registering attribute "+k
                                          +" with test service "+i);
                        if(k==0) {
                            attrs[k] = new TestServiceIntAttr(attrVal);
                        } else { // placeholder for future attributes
                            attrs[k] = new TestServiceIntAttr(attrVal);
                        }//endif
                        regInfo.srvcAttrs[k] = attrs[k];
                    }//end loop
                    srvcReg.setAttributes(attrs);
                }//endif
                regInfoList.add(regInfo);
            }//end loop (j) - service registration loop
            synchronized(regInfoMap) {
                if(reRegister) {
                    regInfoMap.remove(testService);
                }
                regInfoMap.put(testService,regInfoList);
            }//end sync
        }//end loop (i) - service creation loop
    }//end registerServices

    /** Cancels the leases of each service with each lookup service, and
     *  clears the contents of the expectedServiceList and the leaseList.
     */
    protected void unregisterServices() throws UnknownLeaseException,
                                               RemoteException
    {
        for(int i=0;i<leaseList.size();i++) {
            leaseRenewalMgr.cancel( (Lease)(leaseList.get(i)) );
        }//end loop
        leaseList.clear();
        expectedServiceList.clear();
    }//end unregisterServices

    /** Convenience method called by setup that configures the current test
     *  run with the appropriate timeout value for the service discard thread.
     *  These tests must also be configured to register a
     *  DiscardWaitOverrideProvider which will define a test override for
     *  net.jini.lookup.ServiceDiscoveryManager.discardWait 
     */
    private void setupDiscardWait() throws TestException {
	discardWait = 
	    getConfig().getLongConfigVal("com.sun.jini.sdm.discardWait",0);
    }//end setupDiscardWait

    /* Retrieves/stores/displays configuration values for the current test */
    private void getSetupInfo() {
        /* category/test-specific info */
        if( (nServices+nAddServices) > 0) {
            logger.log(Level.FINE, " ----- Category/Test Info ----- ");

            logger.log(Level.FINE,
                       " # of secs to wait for cache to populate  -- "
                       +nSecsServiceDiscovery);

            nSecsServiceEvent = getConfig().getIntConfigVal
                                         ("net.jini.lookup.nSecsServiceEvent",
                                          nSecsServiceEvent);
            logger.log(Level.FINE,
                       " # of secs to wait for events to arrive   -- "
                       +nSecsServiceEvent);

            nSecsRemoteCall = getConfig().getIntConfigVal
                                         ("net.jini.lookup.nSecsRemoteCall",
                                          nSecsRemoteCall);
            logger.log(Level.FINE,
                       " # of secs to wait for remote call        -- "
                       +nSecsRemoteCall);
        }//endif(nServices+nAddServices > 0)
    }//end getSetupInfo

}//end class AbstractBaseTest


