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
import java.io.IOException;
import org.apache.river.start.lifecycle.LifeCycle;
import org.apache.river.constants.TimeConstants;
import org.apache.river.landlord.Landlord;
import org.apache.river.landlord.Landlord.RenewResults;
import org.apache.river.landlord.LeaseFactory;
import org.apache.river.landlord.LeasePeriodPolicy;
import org.apache.river.landlord.LeasedResource;
import org.apache.river.landlord.FixedLeasePeriodPolicy;
import org.apache.river.api.util.Startable;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.export.Exporter;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

import java.rmi.RemoteException;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent; 
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.export.ProxyAccessor;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.jeri.AtomicILFactory;
import org.apache.river.proxy.CodebaseProvider;

public class TestGeneratorImpl 
    implements TestGenerator, Landlord, TimeConstants, ProxyAccessor, Startable,
	DynamicProxyCodebaseAccessor
{

    private static Logger logger = Logger.getLogger("org.apache.river.qa.harness");

    private static final String GENERATOR = 
        "org.apache.river.test.impl.mercury.generator";

    private Map regs = new HashMap();

    private Exporter exporter;

    private LeasePeriodPolicy generatorLeasePolicy;
    
    private static final boolean DEBUG = true;
    
    TestGenerator serverStub = null;

    Uuid generatorUuid = null;

    LeaseFactory leaseFactory;
    
    AccessControlContext context;
    private boolean started;

    public synchronized Object getProxy() { return serverStub; }

    public TestGeneratorImpl(String[] configArgs, LifeCycle lc) 
	throws Exception 
    {
        final Configuration config =
            ConfigurationProvider.getInstance(configArgs);
        LoginContext loginContext = (LoginContext) config.getEntry(
            GENERATOR, "loginContext", LoginContext.class, null);
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
                logger.log(Level.INFO, "Trouble logging out" + le);
            }
            throw e.getException();
        }
    }

    /** Initialization common to both activatable and transient instances. */
    private void doInit(Configuration config) throws Exception {
        exporter = (Exporter) getNonNullEntry(
            config, "exporter", Exporter.class,
            new BasicJeriExporter(TcpServerEndpoint.getInstance(0), 
				  new AtomicILFactory(null, null, TestGenerator.class), 
				  false, 
				  true));
	generatorUuid = UuidFactory.generate();
        generatorLeasePolicy =
            new FixedLeasePeriodPolicy(
                20 * MINUTES,     // Maximum lease is 2 minutes
                1 * MINUTES      // Default lease is 1 hour
            );
	context = AccessController.getContext();
    }

    protected Object getNonNullEntry(Configuration config,
                                     String name,
                                     Class type,
                                     Object defaultValue)
        throws ConfigurationException
    {
        Object result = config.getEntry(GENERATOR, name, type, defaultValue);
        if (result == null) {
            throw new ConfigurationException(
                "Configuration entry for component " + GENERATOR +
                ", name " + name + " should not be null");
        }
        return result;
    }

    public synchronized EventRegistration register(long evID, MarshalledObject handback,
					RemoteEventListener toInform,
					long leaseLength)
	throws UnknownEventException, LeaseDeniedException
    {
	Uuid regUuid = leaseUuid(evID);
	TestRegistration reg = new TestRegistration(regUuid, handback,
						    toInform);

	Lease lease = leaseFactory.newLease(regUuid, leaseLength);
	regs.put(regUuid, reg);
	return new EventRegistration(evID, serverStub,
					lease, reg.getSequenceNumber());
    }

    private synchronized Uuid leaseUuid(long evID) {
	return UuidFactory.create(generatorUuid.getLeastSignificantBits(), evID);
    }

    public synchronized RemoteEvent generateEvent(long evID, int maxTries)
	throws RemoteException, UnknownEventException
    {
	TestRegistration reg = null;
	Uuid regUuid = leaseUuid(evID);
	
        reg = (TestRegistration) regs.get(regUuid);
        if (reg == null) {
	    logger.log(Level.INFO, "TestGeneratorImpl: generateEvent: " +
	        "Registration has been removed ... not sending event");
	    return null;
	}

	RemoteEventListener listener = reg.getListener();
	RemoteEvent event =
	    new RemoteEvent(serverStub, reg.getID(), reg.getSequenceNumber(),
			    reg.getHandback());
	boolean sent = false;
	while (!sent) {
	    try {
	        listener.notify(event);
		sent = true;
		logger.log(Level.FINER, "TestGeneratorImpl: generateEvent: " +
		    "sent event " + event);
		return event;
	    } catch (UnknownEventException ue) {
		logger.log(Level.INFO, "TestGeneratorImpl: generateEvent: " +
		    "Received unknown event exception");
		// Remove registration from our data structures?
		throw ue;
	    } catch (NoSuchObjectException nsoe) {
		// Target listener no longer exists
		logger.log(Level.INFO, "generateEvent: " +
		    "Received unknown object exception");
		// Remove registration from our data structures
		regs.remove(regUuid);
		throw nsoe;
	    } catch (RemoteException e) {
		logger.log(Level.INFO, "TestGeneratorImpl: generateEvent: " +
				    "unable to send event, retrying..."); 
		maxTries--;

		if (maxTries == 0) {
		    throw e;
		}
	    }
	}

	return null;
    }


    //-----------------------
    // LandLord methods
    //-----------------------

    public synchronized long renew(Uuid cookie, long extension)
        throws LeaseDeniedException, UnknownLeaseException, RemoteException {
	TestRegistration reg = (TestRegistration) regs.get(cookie);
	synchronized (reg) {
            if (reg.getExpiration() > System.currentTimeMillis())
		throw new UnknownLeaseException("unknown lease");

	    return generatorLeasePolicy.renew(reg, extension).duration;
	}
    }


    public RenewResults renewAll(Uuid[] cookies, long[] extensions)
	throws RemoteException
    {
        long[] granted = new long[cookies.length];
        List exceptions = new ArrayList();
        for (int i = 0; i < cookies.length; i++) {
            Exception failure = null;
            try {
                granted[i] = renew(cookies[i], extensions[i]);
            } catch (LeaseDeniedException e) {
                failure = e;
            } catch (UnknownLeaseException e) {
                failure = e;
            }
            if (failure != null) {
                exceptions.add(failure);
                granted[i] = -1;
            }
        }
        if (exceptions.size() == 0)
            return new Landlord.RenewResults(granted);
        else {
	    // Note: Can't cast exceptions.toArray() to Exception[]
	    Exception[] es = new Exception[exceptions.size()];
	    Iterator it = exceptions.iterator();
	    for (int i = 0; it.hasNext(); i++)
		es[i] = (Exception) it.next();
	    
	    return new RenewResults(granted, es);
	}
    }

    public synchronized void cancel(Uuid cookie)
        throws UnknownLeaseException 
    {
	TestRegistration reg = (TestRegistration) regs.remove(cookie);
	if (reg == null)
	    throw new UnknownLeaseException("Not managaing requested lease");
    }


    public Map cancelAll(Uuid[] cookies) {
        Map map = null;
        for (int i = 0; i < cookies.length; i++) {
            try {
                cancel(cookies[i]);
            } catch (UnknownLeaseException e) {
                if (map == null)
                    map = new HashMap();
                map.put(cookies[i], e);
            }
        }

	return map;
    }

    @Override
    public final synchronized void start() throws Exception {
        if (started) return;
        started = true;
        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){

            @Override
            public Object run() throws Exception {
                // Export server instance and get its reference
                serverStub = (TestGenerator)exporter.export(TestGeneratorImpl.this);
                leaseFactory = new LeaseFactory((Landlord) serverStub, generatorUuid);
                return null;
            }
            
        }, context);
    }


    private class TestRegistration implements LeasedResource {
	final private Uuid uuid;
	final private MarshalledObject handback;
	final private RemoteEventListener toInform;
	private long expiration;
	private long sequenceNumber;

	TestRegistration(Uuid uuid, MarshalledObject handback,
		    RemoteEventListener toInform) {
	    this.uuid = uuid;
	    this.handback = handback;
	    this.toInform = toInform;
	}

	synchronized long getSequenceNumber() { return sequenceNumber++; }

	long getID() { return uuid.getLeastSignificantBits(); }

	MarshalledObject getHandback() { return handback; }

	RemoteEventListener getListener() { return toInform; };

	synchronized long setExpiration() { return expiration; }

        public synchronized void setExpiration(long newExpiration) {
	    expiration = newExpiration;
	}

        public synchronized long getExpiration() {
	    return expiration;
	}

        public Uuid getCookie() {
	    return uuid;
	}
    }


    @Override
    public String getClassAnnotation() throws IOException {
	String result = CodebaseProvider.getClassAnnotation(serverStub == null ? 
		TestGenerator.class : serverStub.getClass());
	logger.log(Level.FINE, "Annotation: " + result);
	return result;
    }

    @Override
    public String getCertFactoryType() throws IOException {
	return null;
    }

    @Override
    public String getCertPathEncoding() throws IOException {
	return null;
    }

    @Override
    public byte[] getEncodedCerts() throws IOException {
	return null;
    }
}

