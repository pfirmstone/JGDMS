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

package com.sun.jini.test.services.lookupsimulator;

//import com.sun.jini.start.SharedActivation;

import net.jini.discovery.DiscoveryGroupManagement;

import net.jini.discovery.ConstrainableLookupLocator;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;

import java.lang.reflect.Array;

import java.io.IOException;

import java.net.InetAddress;
import java.net.MalformedURLException;

import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationSystem;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.rmi.Remote;

import java.security.SecureRandom;

import net.jini.config.ConfigurationException;
import com.sun.jini.start.ServiceProxyAccessor;
import com.sun.jini.start.LifeCycle;
import net.jini.security.proxytrust.ServerProxyTrust;

import com.sun.jini.config.Config;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import net.jini.activation.ActivationExporter;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.tcp.TcpServerEndpoint;

/**

 * This class simulates the backend server implementation for an
 * activatable lookup service that can be used in tests that need to
 * discover, but not use a lookup service. This class can be particularly
 * useful to tests that exercise and verify utilities related to the
 * discovery protocols. Thus, most of the methods implemented by this
 * class throw an UnsupportedOperationException so as not to interfer
 * with other "real" applications that may be running on the network
 * while the test is in progress.
 */
public class LookupSimulatorImpl implements LookupSimulator, 
					    ServerProxyTrust,
					    ProxyAccessor
{
    private static Logger logger = Logger.getLogger("com.sun.jini.harness.test");
    private MethodConstraints locatorConstraints;
    private LookupSimulator myRef;
    private LookupSimulatorProxy proxy;
    private ServiceID serviceID = null;
    private ActivationID activationID;
    private ActivationSystem activationSystem;
    private LookupLocator locator;
    private int unicastPort = 0;
    private Exporter serverExporter;
    private String[] memberGroups = {""};
    /** random number generator for UUID generation */
    private final SecureRandom secRand = new SecureRandom();
    /** 128-bit buffer for use with secRand */
    private final byte[] secRandBuf16 = new byte[16];
    /** 64-bit buffer for use with secRand */
    private final byte[] secRandBuf8 = new byte[8];
    /* For synchronization instead of ReadersWriter locks used by reggie */
    private Object lockObject = new Object();
    private String unsupportedOperation = 
        "lookup service is a simulation; does not support requested operation";
    private LifeCycle lifeCycle;
    private LoginContext loginContext;
    boolean noneConfiguration;

    public LookupSimulatorImpl(ActivationID activationID, 
			       MarshalledObject data)
	throws Exception
    {
	this((String[]) data.get(), activationID, null);
    }

    public LookupSimulatorImpl(String[] configArgs, LifeCycle lifeCycle)
	throws Exception
    {
	this(configArgs, null, lifeCycle);
    }

    /** Called by the activation group to create this class in a separate VM.
     * @param activationID required argument containing the activation ID
     *                     assigned by the activation system for this class
     * @param data         required argument containing data in 
     *                     MarshalledObject form that will be passed by the
     *                     activation system to this class (should be null
     *                     for this class)
     */
    public LookupSimulatorImpl(String[] configArgs,
			       ActivationID activationID, 
			       LifeCycle lifeCycle)
	throws Exception
    {
	noneConfiguration = configArgs[0].equals("-");
	this.activationID = activationID;
	try {
	    final Configuration config = 
		ConfigurationProvider.getInstance(configArgs);
	    try {
		memberGroups = 
		    (String[]) removeDups(
			       (Object[]) config.getEntry("com.sun.jini.reggie", 
							  "initialMemberGroups",
							  String[].class,
							  memberGroups));
		locatorConstraints = 
		    (MethodConstraints) config.getEntry("lookupSimulator", 
							"locatorConstraints",
							MethodConstraints.class);
	    } catch (ConfigurationException e) {
		// ignore exception if running the  none configuration
		if (!noneConfiguration) {
		    throw new RemoteException(
				   "locatorConstraints configuration error", 
				   e);
		}
	    }
	    try {
		loginContext = (LoginContext) Config.getNonNullEntry(
		    config, "lookupSimulator", "loginContext", LoginContext.class);
	    } catch (NoSuchEntryException e) {
		// leave null
	    }
 	    if (loginContext != null) {
		loginContext.login();
		try {
		    Subject.doAsPrivileged(
			loginContext.getSubject(),
			new PrivilegedExceptionAction() {
			    public Object run() throws Exception {
				init(config);
				return null;
			    }
			},
			null);
		} catch (PrivilegedActionException e) {
		    throw e.getCause();
		}
	    } else {
		init(config);
	    }
	} catch (Throwable t) {
	    logger.log(Level.SEVERE, "Lookup simulator initialization failed", t);
	    if (t instanceof Exception) {
		throw (Exception) t;
	    } else {
		throw (Error) t;
	    }
	}
    }//end constructor

    // This method's javadoc is inherited from an interface of this class
    public TrustVerifier getProxyVerifier() {
	return new LookupSimulatorProxyVerifier(myRef);
    }

    public LookupLocator getConstrainedLocator(String host, int port) 
    {
	return new ConstrainableLookupLocator(host, port, locatorConstraints);
    }

    public LookupLocator getConstrainedLocator(LookupLocator loc) 
    {
	if (loc == null) {
	    return null;
	}
	return getConstrainedLocator(loc.getHost(), loc.getPort());
    }

    public LookupLocator getConstrainedLocator(String url) 
	throws MalformedURLException
    {
	return new ConstrainableLookupLocator(url, locatorConstraints);
    }

    /**
     * Return the smart proxy
     *
     * @return the proxy
     */
    public Object getServiceProxy() throws RemoteException {
	return proxy;
    }

    public Object getProxy() {
	return myRef;
    }

    /* LookupSimulator methods */
    public ServiceRegistration register(ServiceItem item, long duration)
                                                      throws RemoteException
    {
        throw new UnsupportedOperationException(unsupportedOperation);
    }//end register
    public Object lookup(ServiceTemplate tmpl) throws RemoteException {
        throw new UnsupportedOperationException(unsupportedOperation);
    }//end lookup
    public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches)
                                                      throws RemoteException
    {
        throw new UnsupportedOperationException(unsupportedOperation);
    }//end lookup
    public EventRegistration notify(ServiceTemplate tmpl,
                                    int transitions,
                                    RemoteEventListener listener,
                                    MarshalledObject handback,
                                    long leaseDuration) throws RemoteException
    {
        throw new UnsupportedOperationException(unsupportedOperation);
    }//end notify
    public Class[] getEntryClasses(ServiceTemplate tmpl) throws RemoteException
    {
        throw new UnsupportedOperationException(unsupportedOperation);
    }//end getEntryClasses
    public Object[] getFieldValues(ServiceTemplate tmpl,
                                   int setIndex,
                                   String field) throws RemoteException
    {
        throw new UnsupportedOperationException(unsupportedOperation);
    }//end getFieldValues
    public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix)
                                                      throws RemoteException
    {
        throw new UnsupportedOperationException(unsupportedOperation);
    }//end getServiceTypes

    public ServiceID getServiceID()  throws RemoteException {
        return serviceID;
    }//end getServiceID

    public LookupLocator getLocator() throws RemoteException {
        synchronized(lockObject) {
            return locator;
        }//end sync
    }//end getLocator
    public void setLocator(LookupLocator newLocator) throws RemoteException {
        synchronized(lockObject) {
            locator = getConstrainedLocator(newLocator.getHost(),
					    newLocator.getPort());
            unicastPort = locator.getPort();
        }//end sync
    }//end getLocator

    /* DiscoveryAdmin methods */
    public String[] getMemberGroups() throws RemoteException {
        synchronized(lockObject) {
            return memberGroups;
        }//end sync
    }//end getGroups
    public void addMemberGroups(String[] groups) throws RemoteException {
        throw new UnsupportedOperationException(unsupportedOperation);
    }//end addMemberGroups
    public void removeMemberGroups(String[] groups) throws RemoteException {
        throw new UnsupportedOperationException(unsupportedOperation);
    }//end removeMemberGroups
    public void setMemberGroups(String[] groups) throws RemoteException {
        synchronized(lockObject) {
            memberGroups = (String[])removeDups(groups);
        }//end sync
    }//end setMemberGroups
    public int getUnicastPort() throws RemoteException {
        synchronized(lockObject) {
            return unicastPort;
        }//end sync
    }//end getUnicastPort
    public void setUnicastPort(int port) throws RemoteException {
        synchronized(lockObject) {
            unicastPort = port;
            if(locator != null) {
                locator = getConstrainedLocator(locator.getHost(),unicastPort);
            } else {
                try {
                    String host = System.getProperty
                                                 ("java.rmi.server.hostname");
                    if (host == null) {
                        host = InetAddress.getLocalHost().getHostName();
                    }
                    locator = getConstrainedLocator(host,unicastPort);
                } catch(Exception e) { }
            }
        }//end sync
    }//end setUnicastPort

    /* DestroyAdmin methods */
    public void destroy() throws RemoteException {
        (new DestroyThread()).start();
    }//end destroy

    private void init(Configuration config) throws IOException, 
                                                   ConfigurationException,
						   ActivationException
    {
	if (activationID != null) {
	    activationSystem = ActivationGroup.getSystem();
	    // if not the none configuration, prepare proxies
	    if (!noneConfiguration) {
		ProxyPreparer activationIdPreparer =
		    (ProxyPreparer) Config.getNonNullEntry(config,
						       "lookupSimulator",
						       "activationIdPreparer",
						       ProxyPreparer.class);
	
		ProxyPreparer activationSystemPreparer = (ProxyPreparer)
		    Config.getNonNullEntry(config, 
				       "lookupSimulator",
				       "activationSystemPreparer",
				       ProxyPreparer.class);

		activationID = (ActivationID)
		    activationIdPreparer.prepareProxy(activationID);
		activationSystem = (ActivationSystem)
		    activationSystemPreparer.prepareProxy(activationSystem);
	    }
	}
	if (noneConfiguration) {
	    serverExporter =
		new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				      new BasicILFactory());
	    if (activationID != null) {
		serverExporter = new ActivationExporter(activationID,
							serverExporter);
	    }
	} else {
	    serverExporter = 
		(Exporter) Config.getNonNullEntry(config,
						  "lookupSimulator", 
						  "serverExporter",
						  Exporter.class,
						  Configuration.NO_DEFAULT,
						  activationID);
	}
	myRef = (LookupSimulator) serverExporter.export(this);
        if (serviceID == null) serviceID = newServiceID();
	proxy = LookupSimulatorProxy.getInstance(myRef, serviceID);
    }//end init

    /** Generate a new UUID */
    private ServiceID newServiceID() {
        secRand.nextBytes(secRandBuf16);
        secRandBuf16[6] &= 0x0f;
        secRandBuf16[6] |= 0x40; /* version 4 */
        secRandBuf16[8] &= 0x3f;
        secRandBuf16[8] |= 0x80; /* IETF variant */
        secRandBuf16[10] |= 0x80; /* multicast bit */
        long mostSig = 0;
        for (int i = 0; i < 8; i++) {
            mostSig = (mostSig << 8) | (secRandBuf16[i] & 0xff);
        }
        long leastSig = 0;
        for (int i = 8; i < 16; i++) {
            leastSig = (leastSig << 8) | (secRandBuf16[i] & 0xff);
        }
        return new ServiceID(mostSig, leastSig);
    }//end newServiceID

    /** Return a new array containing all the elements of the given array
     *  except the one at the specified index.
     */
    private static Object[] arrayDel(Object[] array, int i) {
        int len = array.length - 1;
        Object[] narray =
               (Object[])Array.newInstance(array.getClass().getComponentType(),
                                           len);
        System.arraycopy(array, 0, narray, 0, i);
        System.arraycopy(array, i + 1, narray, i, len - i);
        return narray;
    }//end ArrayDel

    /** Returns the first index of elt in the array, else -1. */
    private static int indexOf(Object[] array, Object elt) {
        return indexOf(array, array.length, elt);
    }//end indexOf

    /** Returns the first index of elt in the array if < len, else -1. */
    private static int indexOf(Object[] array, int len, Object elt) {
        for (int i = 0; i < len; i++) {
            if (elt.equals(array[i]))  return i;
        }
        return -1;
    }//end indexOf

    /** Weed out duplicates. */
    private static Object[] removeDups(Object[] arr) {
        for (int i = arr.length; --i >= 0; ) {
            if (indexOf(arr, i, arr[i]) >= 0)
                arr = arrayDel(arr, i);
        }
        return arr;
    }//end removeDups

    private class DestroyThread extends Thread {
        /** Create a non-daemon thread */
        public DestroyThread() {
            super("destroy");
            setDaemon(false); // override inheritance from RMI daemon thread
        }//end constructor
        public void run() {
            /* must unregister before unexport */
            if (activationID != null) {
                try {
                    activationSystem.unregisterObject(activationID);
                } catch (RemoteException e) {
                    return;// give up until we can at least unregister
                } catch (ActivationException e) { }
            }//endif
	    while (!serverExporter.unexport(false)) {
		Thread.yield();
	    }
            if (activationID != null) {
                try {
                    while (!Activatable.inactive(activationID)) {
                        Thread.yield();
                    }//end loop
                } catch (RemoteException e) {
                } catch (ActivationException e) { }
            }
	    if (lifeCycle != null) {
		lifeCycle.unregister(LookupSimulatorImpl.this);
	    }
            if (loginContext != null) {
                try {
                    loginContext.logout();
                } catch (LoginException e) {
                    logger.log(Level.FINE, "logout failed", e);
                }
            }
        }
    }//end class DestroyThread

} //end class LookupSimulatorImpl


