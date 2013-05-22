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

package com.sun.jini.test.share;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryService;
import net.jini.discovery.LookupDiscoveryRegistration;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import com.sun.jini.proxy.BasicProxyTrustVerifier;

/**
 * This class contains a set of static methods that provide general-purpose
 * functions related to the use of the lookup discovery service. This
 * utility class is intended to be useful to all categories of tests that
 * wish to use instances of that service.
 *
 * @see net.jini.discovery.LookupDiscoveryService
 * @see net.jini.discovery.LookupDiscoveryRegistration
 */
public class DiscoveryServiceUtil {
    /** Convenience class for handling the events sent by the service
     *  with which the client (the test) has registered
     */
    public static class BasicEventListener 
	implements RemoteEventListener, ServerProxyTrust, Serializable 
    {

	private Object proxy;

        public BasicEventListener() throws RemoteException {
            super();
	    Configuration c = QAConfig.getConfig().getConfiguration();
	    Exporter exporter = QAConfig.getDefaultExporter();
	    if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
		try {
		    exporter = (Exporter) c.getEntry("test",
						     "fiddlerListenerExporter", 
						     Exporter.class);
		} catch (ConfigurationException e) {
		    throw new RemoteException("Configuration problem", e);
		}
	    }
            synchronized (this){
                proxy = exporter.export(this);
            }
        }

	private synchronized Object writeReplace() throws ObjectStreamException {
	    return proxy;
	}

        public void notify(RemoteEvent ev) {
        }

	public synchronized TrustVerifier getProxyVerifier() {
	    return new BasicProxyTrustVerifier(proxy);
	}
    }//end class BasicEventListener

    public  static final long defaultDuration = 1*30*1000;

    private static final String[] defaultGroups
                                         = DiscoveryGroupManagement.NO_GROUPS;
    private static final LookupLocator[] defaultLocators
                                         = new LookupLocator[0];
    private static final Integer defaultHbVal    = new Integer(93);

   /** Converts a given absolute expiration time to a time of duration
     *  relative to the value of the <code>curTime</code> parameter.
     *
     *  Because inaccurracies in the value of the current system time are
     *  expected, this method rounds the result to the smallest whole
     *  number greater than or equal to the decimal representatin of the
     *  computed duration.
     * 
     *  @param expiration the absolute expiration time in milliseconds of
     *                    the lease whose duration is to be computed
     *
     *  @return the relative duration of the lease whose absolute expiration
     *          is equal to the value in the <code>expiration</code>
     *          parameter; rounded to the next whole number
     */
    public static long expirationToDuration(long expiration, long curTime) {
        double dExpiration = (double)expiration;
        double dCurTime    = (double)curTime;
        double dDur        = (dExpiration-dCurTime)/1000;
        double wDur        = Math.floor(dDur);
        double remainder   = dDur - wDur;
        double dDuration   = ( (remainder>0) ? (wDur+1) : wDur );
        return (long)(1000*dDuration);
    }//end expirationToDuration

    /** Sleeps for the given number of milliseconds */
    public static void delayMS(long nMS) {
        try {
            Thread.sleep(nMS);
        } catch (InterruptedException e) { }
    }//end delayMS

    /** Converts a given absolute expiration time to a time of duration
     *  relative to the current system time.
     *
     *  Because inaccurracies in the value of the current system time are
     *  expected, this method rounds the result to the smallest whole
     *  number greater than or equal to the decimal representatin of the
     *  computed duration.
     * 
     *  @param expiration the absolute expiration time in milliseconds of
     *                    the lease whose duration is to be computed
     *
     *  @return the relative duration of the lease whose absolute expiration
     *          is equal to the value in the <code>expiration</code>
     *          parameter; rounded to the next whole number
     */
    public static long expirationToDuration(long expiration) {
        return expirationToDuration(expiration,System.currentTimeMillis());
    }//end expirationToDuration

    /** Given a proxy to a lookup discovery service, this method is one of
     *  a number of overloaded versions that request a registration with
     *  the service to which the <code>proxy</code> parameter corresponds.
     *  This version of the method contains a fully defined argument
     *  list; that is, all other versions of this method ultimately
     *  invoke this method.
     * 
     *  @param proxy    the proxy object through which the request for
     *                  registration with the service is made
     *  @param listener the remote event listener with which to register
     *                   with the service's event mechanism
     *  @param groups   <code>String</code> containing the names of the
     *                  groups containing the lookup services to discover
     *  @param locators array of instances of <code>LookupLocator</code>,
     *                  where each element of the array corresponds to a
     *                  specific lookup service to discover
     *  @param duration the number of milliseconds to request for the duration
     *                  of the lease granted on the requested registration
     *  @param handback the object that will be returned in any events
     *                  sent by the registration to the listener 
     *
     *  @return a <code>net.jini.discovery.LookupDiscoveryRegistration</code>
     *          instance through which the registered client can manipulate
     *          the state of its registration with the service
     */
    public static LookupDiscoveryRegistration
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 String[] groups,
                                                 LookupLocator[] locators,
                                                 long duration,
                                                 MarshalledObject handback)
                                                        throws RemoteException
    {
	    return proxy.register(groups,locators,listener,handback,duration);
    }//end getRegistration

    /* 1. No handback parameter */
    public static LookupDiscoveryRegistration
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 String[] groups,
                                                 LookupLocator[] locators,
                                                 long duration)
                                                        throws RemoteException
    {
        try {
            return getRegistration(proxy,listener,groups,locators,duration,
                                   new MarshalledObject(defaultHbVal) );
        } catch (IOException e) {
            return getRegistration(proxy,listener,groups,locators,duration,
                                   null);
        }
    }//end getRegistration

    /* 2. No duration parameter */
    public static LookupDiscoveryRegistration
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 String[] groups,
                                                 LookupLocator[] locators,
                                                 MarshalledObject handback)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,groups,locators,
                               defaultDuration,handback);
    }//end getRegistration

    /* 3. No duration or handback parameter */
    public static LookupDiscoveryRegistration
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 String[] groups,
                                                 LookupLocator[] locators)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,groups,locators,defaultDuration);
    }//end getRegistration

    /* 4. No locators parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 String[] groups,
                                                 long duration,
                                                 MarshalledObject handback)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,groups,defaultLocators,
                               duration,handback);
    }//end getRegistration

    /* 5. No locators or handback parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 String[] groups,
                                                 long duration)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,groups,defaultLocators,duration);
    }//end getRegistration

    /* 6. No groups parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 LookupLocator[] locators,
                                                 long duration,
                                                 MarshalledObject handback)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,defaultGroups,locators,
                               duration,handback);
    }//end getRegistration

    /* 7. No groups or handback parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 LookupLocator[] locators,
                                                 long duration)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,defaultGroups,locators,duration);
    }//end getRegistration

    /* 8. No locators or duration parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 String[] groups,
                                                 MarshalledObject handback)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,groups,defaultLocators,
                               defaultDuration,handback);
    }//end getRegistration

    /* 9. No locators, duration, or handback parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 String[] groups)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener, groups,
                               defaultLocators,defaultDuration);
    }//end getRegistration

    /* 10. No groups or duration parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 LookupLocator[] locators,
                                                 MarshalledObject handback)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,defaultGroups,locators,
                               defaultDuration,handback);
    }//end getRegistration

    /* 11. No groups, duration, or handback parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 LookupLocator[] locators)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,defaultGroups,locators,
                               defaultDuration);
    }//end getRegistration

    /* 12. No groups or locators parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 long duration,
                                                 MarshalledObject handback)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,defaultGroups,defaultLocators,
                               duration,handback);
    }//end getRegistration

    /* 13. No groups, locators, or handback parameter */
    public static LookupDiscoveryRegistration 
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 long duration)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,defaultGroups,defaultLocators,
                               duration);
    }//end getRegistration

    /* 14. No groups, locators, or duration parameter */
    public static LookupDiscoveryRegistration
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener,
                                                 MarshalledObject handback)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,defaultGroups,defaultLocators,
                               defaultDuration,handback);
    }//end getRegistration

    /* 15. No groups, locators, duration, or handback parameter */
    public static LookupDiscoveryRegistration
                                 getRegistration(LookupDiscoveryService proxy,
                                                 RemoteEventListener listener)
                                                        throws RemoteException
    {
        return getRegistration(proxy,listener,defaultGroups, defaultLocators,
                               defaultDuration);
    }//end getRegistration

} //end class DiscoveryServiceUtil


