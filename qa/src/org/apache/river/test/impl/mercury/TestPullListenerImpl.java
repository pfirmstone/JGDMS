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
package org.apache.river.test.impl.mercury;
import org.apache.river.proxy.BasicProxyTrustVerifier;
import org.apache.river.start.lifecycle.LifeCycle;
import org.apache.river.api.util.Startable;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
import net.jini.core.event.RemoteEventListener;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import net.jini.event.MailboxPullRegistration;
import net.jini.event.InvalidIteratorException;

public class TestPullListenerImpl 
    implements TestPullListener, ProxyAccessor, ServerProxyTrust, Startable
{
    private final Map events = new HashMap();

    private Exporter exporter;
    
    private TestPullListener serverStub;

    private static final String LISTENER = 
        "org.apache.river.test.impl.mercury.listener";
    
    private AccessControlContext context;
    private boolean started;

    public synchronized Object getProxy() { return serverStub; }
    
    public synchronized TrustVerifier getProxyVerifier() {
	return new BasicProxyTrustVerifier(serverStub);
    }    

    public TestPullListenerImpl(String[] configArgs, LifeCycle lc) throws Exception {
        final Configuration config =
            ConfigurationProvider.getInstance(configArgs);
        LoginContext loginContext = (LoginContext) config.getEntry(
            LISTENER, "loginContext", LoginContext.class, null);
        if (loginContext != null) {
            doInitWithLogin(config, loginContext);
        } else {
            doInit(config);
        }
    }
    /**
     * Method that attempts to login before deledating the
     * rest of the initialization process to <code>doInit</code>
     */
    private void doInitWithLogin(final Configuration config,
        LoginContext loginContext) throws Exception
    {
        loginContext.login();
        try {
            Subject.doAsPrivileged(
                loginContext.getSubject(),
                new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        doInit(config);
                        return null;
                    }
                },
                null);
        } catch (PrivilegedActionException e) {
           try {
                loginContext.logout();
            } catch (LoginException le) {
                System.out.println("Trouble logging out" + le);
            }
            throw e.getException();
        }
    }

    /** Initialization common to both activatable and transient instances. */
    private void doInit(Configuration config) throws Exception {
        exporter = (Exporter) getNonNullEntry(
            config, "exporter", Exporter.class,
            new BasicJeriExporter(TcpServerEndpoint.getInstance(0), 
				  new BasicILFactory(), 
				  false, 
				  true));
        context = AccessController.getContext();
    }

    protected Object getNonNullEntry(Configuration config,
                                     String name,
                                     Class type,
                                     Object defaultValue)
        throws ConfigurationException
    {
        Object result = config.getEntry(LISTENER, name, type, defaultValue);
        if (result == null) {
            throw new ConfigurationException(
                "Configuration entry for component " + LISTENER +
                ", name " + name + " should not be null");
        }
        return result;
    }


    public boolean verifyEvents(RemoteEvent[] theEvents) 
    {
        for (int i = 0; i < theEvents.length; i++) {
            if (verifyEvent(theEvents[i]) == false)
                return false;
        }
        return true;
    }

    public boolean verifyEvent(RemoteEvent theEvent) {
	System.out.println("::verifyEvent() verifying");

	// Wrap event into a handle
	RemoteEventHandle key = new RemoteEventHandle(theEvent);

        // See if we already have the handle
        RemoteEvent incoming;
        String eventString;
        synchronized (events) {
            incoming = (RemoteEvent) events.get(key);
            eventString = events.toString();
        }
        String s = (incoming ==null) ? "not found" : "found";
        System.out.println("Desired event was " + s + ": " + key);
        System.out.println("Events are:" + eventString);
	if (incoming == null)
	    return false; // don't have it
	else
	    return true; // have it
    }

    public Collection getRemoteEvents(MailboxPullRegistration mr) 
        throws RemoteException, InvalidIteratorException 
    {
        ArrayList al = new ArrayList();
	net.jini.event.RemoteEventIterator ri = 
	    mr.getRemoteEvents();
        synchronized (events){
            for ( RemoteEvent event = ri.next(5000L); 
                  event != null; 
                  event = ri.next(5000L)) {
                events.put(new RemoteEventHandle(event), event); 
                al.add(event);
            }
        }
	return al;
    }
    
    public Collection getCollectedRemoteEvents() {
        synchronized (events){
            return new ArrayList(events.values());
        }
    }

    public int getCollectedRemoteEventsSize() {
        synchronized (events){
            return events.size();
        }
    }

    @Override
    public final synchronized void start() throws Exception {
        if (started) return;
        started = true;
        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){

            @Override
            public Object run() throws Exception {
                // Export server instance and get its reference
                serverStub = (TestPullListener) exporter.export(TestPullListenerImpl.this);
                return null;
            }
            
        }, context);
    }

}


