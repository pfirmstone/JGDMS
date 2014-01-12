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

package com.sun.jini.test.spec.discoveryservice.lease;

import java.util.logging.Level;

import com.sun.jini.test.spec.discoveryservice.AbstractBaseTest;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.test.share.DiscoveryAdminUtil;
import com.sun.jini.test.share.DiscoveryServiceUtil;
import com.sun.jini.test.share.GroupsUtil;
import com.sun.jini.test.share.JoinAdminUtil;
import com.sun.jini.test.share.LocatorsUtil;

import com.sun.jini.qa.harness.TestException;

import net.jini.admin.JoinAdmin;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryService;
import net.jini.discovery.LookupDiscoveryRegistration;
import net.jini.discovery.RemoteDiscoveryEvent;
import net.jini.lease.LeaseListener;
import net.jini.lease.LeaseRenewalEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.DiscoveryAdmin;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceRegistrar;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import java.rmi.MarshalledObject;
import java.rmi.RemoteException;

import java.util.ArrayList;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import com.sun.jini.proxy.BasicProxyTrustVerifier;
import com.sun.jini.qa.harness.Test;
import java.rmi.server.ExportException;

/**
 * This class determines if, when a client's lease on a registration with the
 * lookup discovery service expires, the resource provided by the lookup
 * discovery service (through the registration) are no longer available
 * to the client. In particular, since the resource provided by the lookup
 * discovery service is registration with that service's event mechanism,
 * this class verifies that after the lease on a client's registration
 * expires, the service sends no more discovery events to the listener
 * registered by the client.
 *
 * This class verifies the following behaviour specified by
 * <i>The LookupDiscoveryService</i> specification:
 * "... the resources granted by this service are leased, and implementations
 *  of this service must adherre to the distributed leasing model for 
 *  Jini technology as defined in the <i>Jini(tm) Distriburted Leasing
 *  Specification</i>."
 */
public class LeaseExpiration extends AbstractBaseTest {
    /** Convenience class for handling the events sent by the service
     *  with which the client (the test) has registered
     */
    
    

    public class ServiceEventListener implements RemoteEventListener, 
						 ServerProxyTrust,
						 Serializable 
    {
        private Exporter exporter;
        private Object proxy;
        
        public ServiceEventListener() throws RemoteException {
            super();
	    Configuration c = getConfig().getConfiguration();
	    Exporter exporter = getConfig().getDefaultExporter();
	    if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
		try {
		    exporter = (Exporter) c.getEntry("test",
						     "fiddlerListenerExporter",
						     Exporter.class);
		} catch (ConfigurationException e) {
		    throw new RemoteException("Could not find listener exporter", e);
		}
	    }
            this.exporter = exporter;
        }
        
        private synchronized void export() throws ExportException{
            proxy = exporter.export(this);
        }
        

	public synchronized Object writeReplace() throws ObjectStreamException {
	    return proxy;
	}

	public synchronized TrustVerifier getProxyVerifier() {
	    return new BasicProxyTrustVerifier(proxy);
	}

        public void notify(RemoteEvent ev) {
            logger.log(Level.FINE, 
                              "received an event from the service");
            if( !(ev instanceof RemoteDiscoveryEvent) ) {
                logger.log(Level.FINE, 
                                  "unexpected event type received ("
                                  +ev);
                return;
            }
            try {
                String handback = (String)((ev.getRegistrationObject().get()));
                logger.log(Level.FINE, 
                                  "lookup service discovered -- groups = "
                                  +handback);
            } catch (ClassNotFoundException e) {
                logger.log(Level.FINE, 
                                  "error unmarshalling event handback");
                e.printStackTrace();
            } catch (IOException e) {
                logger.log(Level.FINE, 
                                  "error unmarshalling event handback");
                e.printStackTrace();
            }
            synchronized(eventLock) {
                eventReceived = true;
                eventLock.notifyAll();
            }
        }
    }//end class ServiceEventListener

    /** Convenience class for monitoring the lease with the lookup discovery
     *  service.
     */
    public static class LRMListener implements LeaseListener {
        public LRMListener() {
            super();
        }
        public void notify(LeaseRenewalEvent ev) {
            if(ev != null) {
                Throwable leaseException = ev.getException();
                if(leaseException == null) {
                    logger.log(Level.FINE, 
                                      "LeaseRenewalEvent -- expiration "
                                      +"occurred before the renewal manager "
                                      +"could renew the lease");
                } else {
                    logger.log(Level.FINE, "WARNING -- exception "
                                      +"while renewing the lease");
                    (ev.getException()).printStackTrace();
                }//endif
            }//endif
        }//end notify
    }//end class LRMListener

    private ServiceRegistrar srvcReg = null;
    private final ArrayList lookupList = new ArrayList();
    private String[] memberGroups = DiscoveryGroupManagement.NO_GROUPS;
    private static final int N_CYCLES_WAIT_EXPIRATION = 10;
    private static final long N_SECS = 30;
    private final long duration = N_SECS*1000;
    private MarshalledObject handback = null;
    private boolean eventReceived = false;
    private final Object eventLock = new Object();

    /** Constructs and returns the duration values (in milliseconds) to 
     *  request on each renewal attempt (can be overridden by sub-classes)
     */
    long[] getRenewalDurations() {
        return new long[] { 45*1000, 15*1000 };
    }//end getRenewalDurations

    /** Performs actions necessary to prepare for execution of the 
     *  current test.
     *
     *  Starts one lookup discovery service.
     *  Start one lookup service belonging to a finite (non-empty, non-null),
     *  non-public set of group(s)
     *  Retrieve the set of group(s) with which the lookup service was started
     *  Create a handback object to help identify received discovery events
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        /* Start a lookup service */
        logger.log(Level.FINE, 
                          "starting a new lookup service");
        synchronized (this){
            synchronized(eventLock) {
                eventReceived = false;
                srvcReg = getManager().startLookupService(); // already prepared
                lookupList.add( srvcReg );
            }
            DiscoveryAdmin admin = DiscoveryAdminUtil.getDiscoveryAdmin(srvcReg);
            memberGroups = admin.getMemberGroups();
            LocatorsUtil.displayLocator(QAConfig.getConstrainedLocator(srvcReg.getLocator()),
                                        "  lookup locator",Level.FINE);
            logger.log(Level.FINE, 
                       "  lookup MemberGroup(s) = "
                       +GroupsUtil.toCommaSeparatedStr(memberGroups));
            handback = new MarshalledObject
                                  (GroupsUtil.toCommaSeparatedStr(memberGroups));
            return this;
        }
    }//end construct

    /** Executes the current test by doing the following:
     *  
     *  1. Request a registration with the lookup discovery service started
     *     during construct; requesting that the service discover lookup services
     *     belonging to the group(s) used during construct to start the lookup
     *     service 
     *  2. Pass the lease on the registration to a LeaseRenewalManager so
     *     the lease does not expire if the creation of the second lookup
     *     takes too long
     *  3. Verify that the lookup discovery service sends an event announcing
     *     the discovery of a lookup service belonging to those group(s)
     *     (this is done to demonstrate that the event mechanism of the
     *     lookup discovery service is functioning properly)
     *  4. Start a second lookup service belonging to the same group(s) as
     *     the lookup service started during construct
     *  5. Verify that the lookup discovery service sends another event
     *     announcing the discovery of a lookup service belonging to the
     *     group(s) used to start the lookup service just started (This is
     *     done to demonstrate that when another lookup service -- belonging
     *     to the group(s) identified in the client's registration request --
     *     is started, the lookup discovery service will send another event
     *     just like the first event. This establishes the expectation that
     *     should a third lookup service be started as a member of those 
     *     same group(s), another event should be sent.
     *  6. Remove the lease on the registration from the renewal manager
     *     so that the lease can expire
     *  6. Wait an appropriate amount of time in order to guarantee that
     *     client's lease on the registration has expired
     *  7. Verify that the lease has indeed expired by attempting to renew
     *     the lease
     *  8. Start a third lookup service belonging to the same group(s) as
     *     the lookup service started during construct
     *  9. Verify that the lookup discovery service does not send anymore
     *     discovery events to the registration's listener
     */
    public synchronized void run() throws Exception {
        logger.log(Level.FINE, "run()");
        if(discoverySrvc == null) {
            throw new TestException("could not successfully start the service "
				    +serviceName);
        }
        LeaseRenewalManager lrm =
	    new LeaseRenewalManager(getConfig().getConfiguration());
        Lease lease = null;
        long  actualDur = 0;
        /* Request a registration with the lookup discovery service */
        logger.log(Level.FINE, "registering with the lookup discovery service");
	ServiceEventListener eventListener = new ServiceEventListener();
        eventListener.export();
	LookupDiscoveryRegistration reg = 
	    DiscoveryServiceUtil.getRegistration
	                  (discoverySrvc,
			   eventListener,
			   memberGroups,
			   duration,
			   new MarshalledObject(GroupsUtil.toCommaSeparatedStr
						             (memberGroups)));
	lease = getPreparedLease(reg);
	lrm.renewUntil(lease,Lease.FOREVER,new LRMListener());
	actualDur = DiscoveryServiceUtil.expirationToDuration
	    (lease.getExpiration(),
	     System.currentTimeMillis());
	logger.log(Level.FINE, 
		   "lease duration granted = "
		   +(actualDur/1000)+" second(s)");
        long nSecsWait = ( (nSecsLookupDiscovery > (actualDur/1000)) ?
                            nSecsLookupDiscovery : (actualDur/1000) );
        /* Give the event time to arrive */
        long startTime = System.currentTimeMillis();
        long finishWait = nSecsWait * 1000;
        long waitDuration = 0L;
        synchronized (eventLock){
            if(!eventReceived) {
                while (waitDuration < finishWait) {
                    try {
                        eventLock.wait(1000);
                        waitDuration = System.currentTimeMillis() - startTime;
                        if(eventReceived) break;
                    } catch (InterruptedException e){
                        Thread.currentThread().interrupt();// restore
                    }
                }
            }//endif
            if(eventReceived) {
                logger.log(Level.FINE, 
                                  "first discovery event received after "
                                  +waitDuration/1000+" second(s)");
            } else {
                throw new TestException(
                                 " -- waited "+waitDuration/1000+" seconds, but no discovery "
                                 +"event received for the first lookup "
                                 +"service started");
            }//endif
        }
        /* Start another lookup belonging to same group(s) as first */
        logger.log(Level.FINE, 
                          "starting a new lookup service");
        synchronized(eventLock) {
            eventReceived = false;
	    srvcReg = getManager().startLookupService(); // prepared
            lookupList.add( srvcReg );
        }//end synchronized
        // prepared by DiscoveryAdminUtil
        DiscoveryAdmin admin1 = DiscoveryAdminUtil.getDiscoveryAdmin
                                                                (srvcReg);
        String[] memberGroups1 = admin1.getMemberGroups();
        LocatorsUtil.displayLocator(
                QAConfig.getConstrainedLocator(srvcReg.getLocator()),
                "  lookup locator",Level.FINE);
        logger.log(Level.FINE, 
                          "  lookup MemberGroup(s) = "
                          +GroupsUtil.toCommaSeparatedStr(memberGroups1));
        /* Give the event time to arrive */
        startTime = System.currentTimeMillis();
        synchronized (eventLock){
            if(!eventReceived) {
                while (waitDuration < finishWait) {
                    try {
                        eventLock.wait(1000);
                        waitDuration = System.currentTimeMillis() - startTime;
                        if(eventReceived) break;
                    } catch (InterruptedException e){
                        Thread.currentThread().interrupt();// restore
                    }
                }
            }//endif
            if(eventReceived) {
                logger.log(Level.FINE, 
                   "second discovery event received after "+waitDuration/1000+" second(s)");
            } else {
                throw new TestException(
                                 " -- waited "+waitDuration/1000+" seconds, but no discovery "
                                 +"event received for the second lookup "
                                 +"service started");
            }//endif
        }
        /* Remove the lease from the renewal manager so it can expire */
        try {
            lrm.remove(lease);
            logger.log(Level.FINE, 
                              "removed the lease from the renewal "
                              +"manager");
        } catch(UnknownLeaseException e) {
            logger.log(Level.FINE, 
                            "UnknownLeaseException -- failed to "
                            +"removed the lease from the renewal manager");
        }
        /* Wait long enough to allow the registration lease to expire */
        logger.log(Level.FINE, 
                              "waiting for lease expiration ...");
        boolean leaseExpired = false;
        for(int i=0;i<N_CYCLES_WAIT_EXPIRATION;i++) {
            DiscoveryServiceUtil.delayMS(2*actualDur);
            /* Verify the lease has expired by trying to renew the lease */
            try {
                lease.renew(duration);
            } catch (UnknownLeaseException e) { //expected exception
                leaseExpired = true;
                logger.log(Level.FINE, 
                              "lease on the registration has expired");
                break;
            }
        }//end loop
        if(!leaseExpired) {
            logger.log(Level.FINE, 
                              "failure -- lease still exists");
            throw new TestException(
                             " -- lease did not expire after "
                             +(N_CYCLES_WAIT_EXPIRATION*2*(actualDur/1000))
                             + "seconds");
        }//endif
        /* Start another lookup belonging to same group(s) as first */
        logger.log(Level.FINE, 
                          "starting a new lookup service");
        synchronized(eventLock) {
            eventReceived = false;
	    srvcReg = getManager().startLookupService(); // prepared
            lookupList.add( srvcReg );
        }//end synchronized

        // prepared by DiscoveryAdminUtil
        DiscoveryAdmin admin2 = DiscoveryAdminUtil.getDiscoveryAdmin
                                                                (srvcReg);
        String[] memberGroups2 = admin2.getMemberGroups();
        LocatorsUtil.displayLocator(
                QAConfig.getConstrainedLocator(srvcReg.getLocator()),
                "  lookup locator",Level.FINE);
        logger.log(Level.FINE, 
                          "  lookup MemberGroup(s) = "
                          +GroupsUtil.toCommaSeparatedStr(memberGroups2));
        /* Give the event time to arrive */
        startTime = System.currentTimeMillis();
        synchronized (eventLock){
            if(!eventReceived) {
                while (waitDuration < finishWait) {
                    try {
                        eventLock.wait(1000);
                        waitDuration = System.currentTimeMillis() - startTime;
                        if (eventReceived) break;
                    } catch (InterruptedException e){
                        Thread.currentThread().interrupt();// restore
                    }
                }
            }//endif
            if(eventReceived) {
                throw new TestException(
                                     " -- last discovery event received after "
                                     +waitDuration/1000+" second(s)");
            } else {
                logger.log(Level.FINE, 
                               "no events received after "+waitDuration/1000+" second(s)");

            }//endif
        }
    }//end run

} //end class LeaseExpiration


