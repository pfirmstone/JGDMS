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
package com.sun.jini.test.share.reggie;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.MarshalledObject;
import java.rmi.UnmarshalException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupID;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.StringTokenizer;
import java.security.SecureRandom;
import java.lang.reflect.Array;
import com.sun.jini.constants.TimeConstants;
import com.sun.jini.constants.ThrowableConstants;
//import com.sun.jini.start.SharedActivation;
import com.sun.jini.thread.ReadersWriter;
import com.sun.jini.thread.ReadersWriter.ConcurrentLockException;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.lookup.entry.BasicServiceType;
import net.jini.core.entry.Entry;
import net.jini.core.lease.*;
import net.jini.core.event.*;
import net.jini.core.lookup.*;
import net.jini.lookup.JoinManager;
import net.jini.lookup.entry.ServiceInfo;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.*;
import com.sun.jini.reliableLog.ReliableLog;
import com.sun.jini.reliableLog.LogException;
import com.sun.jini.reliableLog.LogHandler;
import net.jini.config.ConfigurationException;

/**
 * The server side of an implementation of the lookup service.  Multiple
 * client-side proxy classes are used, for the ServiceRegistrar interface
 * as well as for leases and administration; their methods transform the
 * parameters and then make corresponding calls on the Registrar interface
 * implemented on the server side.
 */
public class RegistrarImpl implements Registrar {

    /* ServiceInfo values */
    private static final String PRODUCT = "Lookup";
    private static final String MANUFACTURER = "Sun Microsystems, Inc.";
    private static final String VENDOR = MANUFACTURER;
    private static final String VERSION = 
	com.sun.jini.constants.VersionConstants.SERVER_VERSION;

    /** Maximum minMax lease duration for both services and events */
    private static final long MAX_LEASE = 1000L * 60 * 60 * 24 * 365 * 1000;
    /** Maximum minimum renewal interval */
    private static final long MAX_RENEW = 1000L * 60 * 60 * 24 * 365;
    /** Log format version */
    private static final int LOG_VERSION = 1;

    /** Internet Protocol (IP) addresses of the network interfaces (NICs)
     *  through which multicast packets will be sent.
     */
    private InetAddress[] nicAddresses;

    /** Empty attribute set */
    private static final EntryRep[] emptyAttrs = {};

    /** Proxy for myself */
    private RegistrarProxy proxy;
    /** Our service ID */
    private ServiceID myServiceID;
    /** Our activation id, if we're activatable */
    private ActivationID activationID;
    /** Reference to shared activation object. Used to modify the service's 
     *  activation state under a shared activation group environment.
     */
    //    private SharedActivation sharedActivationRef = null;
    /** Our LookupLocator */
    private LookupLocator myLocator;

    /**
     * Map from ServiceID to SvcReg.  Every service is in this map under
     * its serviceID.
     */
    private final HashMap serviceByID = new HashMap();
    /**
     * Identity map from SvcReg to SvcReg, ordered by lease expiration.
     * Every service is in this map.
     */
    private final TreeMap serviceByTime = new TreeMap();
    /**
     * Map from ServiceType to ArrayList(SvcReg).  Every service is in
     * this map under its exact serviceType.
     */
    private final HashMap serviceByType = new HashMap();
    /**
     * Map from EntryClass to HashMap[] where each HashMap is a map from
     * Object (field value) to ArrayList(SvcReg).  The HashMap array has as
     * many elements as the EntryClass has fields (including fields defined
     * by superclasses).  Services are in this map multiple times, once
     * for each field of each entry it has.  The outer map is indexed by the
     * first (highest) superclass that defines the field.  This means that a
     * HashMap[] has null elements for fields defined by superclasses, but
     * this is a small memory hit and is simpler than subtracting off base
     * index values when accessing the arrays.
     */
    private final HashMap serviceByAttr = new HashMap(23);
    /**
     * Map from EntryClass to ArrayList(SvcReg).  Services are in this map
     * multiple times, once for each no-fields entry it has (no fields meaning
     * none of the superclasses have fields either).  The map is indexed by
     * the exact type of the entry.
     */
    private final HashMap serviceByEmptyAttr = new HashMap(11);
    /** All EntryClasses with non-zero numInstances */
    private final ArrayList entryClasses = new ArrayList();
    /**
     * Map from Long(eventID) to EventReg.  Every event registration is in
     * this map under its eventID.
     */
    private final HashMap eventByID = new HashMap(11);
    /**
     * Identity map from EventReg to EventReg, ordered by lease expiration.
     * Every event registration is in this map.
     */
    private final TreeMap eventByTime = new TreeMap();
    /**
     * Map from ServiceID to EventReg or EventReg[].  An event
     * registration is in this map if its template matches on (at least)
     * a specific serviceID.
     */
    private final HashMap subEventByService = new HashMap(11);
    /**
     * Map from Long(eventID) to EventReg.  An event registration is in
     * this map if its template matches on ANY_SERVICE_ID.
     */
    private final HashMap subEventByID = new HashMap(11);

    /** random number generator for UUID generation */
    private final SecureRandom secRand = new SecureRandom();
    /** 128-bit buffer for use with secRand */
    private final byte[] secRandBuf16 = new byte[16];
    /** 64-bit buffer for use with secRand */
    private final byte[] secRandBuf8 = new byte[8];
    /** Event id generator */
    private long eventID = 0;
    /** Random number generator for use in lookup */
    private final Random random = new Random();

    /** ArrayList of pending EventTasks */
    private final ArrayList newNotifies = new ArrayList();

    /** Current maximum service lease duration granted, in milliseconds. */
    private long maxServiceLease;
    /** Current maximum event lease duration granted, in milliseconds. */
    private long maxEventLease;
    /** Earliest expiration time of a SvcReg */
    private long minSvcExpiration = Long.MAX_VALUE;
    /** Earliest expiration time of an EventReg */
    private long minEventExpiration = Long.MAX_VALUE;

    /** Manager for discovering other lookup services */
    private LookupDiscoveryManager discoer;
    /** Manager for joining other lookup services */
    private JoinManager joiner;
    /** Task manager for sending events and discovery responses */
    private final TaskManager tasker = new TaskManager(10, 1000 * 15, 1.0f);
    /** Service lease expiration thread */
    private final Thread serviceExpirer = new ServiceExpireThread();
    /** Event lease expiration thread */
    private final Thread eventExpirer = new EventExpireThread();
    /** Unicast discovery request packet receiving thread */
    private UnicastThread unicaster;
    /** Multicast discovery request packet receiving thread */
    private Thread multicaster;
    /** Multicast discovery announcement sending thread */
    private Thread announcer;
    /** Snapshot-taking thread */
    private final Thread snapshotter = new SnapshotThread();

    /** Concurrent object to control read and write access */
    private final ReadersWriter concurrentObj = new ReadersWriter();
    /** Object for synchronizing with the service expire thread */
    private final Object serviceNotifier = new Object();
    /** Object for synchronizing with the event expire thread */
    private final Object eventNotifier = new Object();
    /** Object on which the snapshot-taking thread will synchronize */
    private final Object snapshotNotifier = new Object();

    /** Class resolver */
    private final ClassResolver resolver = new ClassResolver();
    /** Canonical ServiceType for java.lang.Object */
    private ServiceType objectServiceType;

    /** Reliable log object to hold Service and Event Records */
    private ReliableLog log;
    /** Flag indicating whether system is in a state of recovery */
    private boolean inRecovery;
    /** Current number of records in the Log File since the last snapshot */
    private int logFileSize = 0;

    /** Name of directory for persistence using ReliableLog */
    private String logDirname;
    /** Log File must contain this many records before snapshot allowed */
    private int logToSnapshotThresh = 200;
    /** Weight factor applied to snapshotSize when deciding to take snapshot */
    private float snapshotWt = 10;
    /** Minimum value for maxServiceLease. */
    private long minMaxServiceLease = 1000 * 60 * 5;
    /** Minimum value for maxEventLease. */
    private long minMaxEventLease = 1000 * 60 * 30;
    /** Minimum average time between lease renewals, in milliseconds. */
    private long minRenewalInterval = 100;
    /** Port for unicast discovery */
    private int unicastPort = 0;
    /** The groups we are a member of */
    private String[] memberGroups = {};
    /** The groups we should join */
    private String[] lookupGroups = {};
    /** The locators of other lookups we should join */
    private LookupLocator[] lookupLocators = {};
    /** The attributes to use when joining (including with myself) */
    private Entry[] lookupAttrs;

    /** Socket timeout for unicast discovery request processing */
    private int unicastTimeout =
	Integer.getInteger("com.sun.jini.reggie.unicastTimeout",
			   1000 * 60).intValue();

    /**
     * Called by the activation group.
     *
     * @param activationID our activation id
     * @param data log directory name as a MarshalledObject
     */
    public RegistrarImpl(ActivationID activationID, MarshalledObject data)
	throws IOException
    {
	this.activationID = activationID;
	Activatable.exportObject(this, activationID, 0);
	try {
	    logDirname = (String)data.get();
	} catch (Exception e) {
	    throw new IllegalArgumentException
					  ("bad directory name in descriptor");
	}
	init();
    }
	
    /**
     * Constructs an activatable, persistent instance of the Reggie
     * implementation of the lookup service that runs in an activation
     * group (VM) in which other activatable objects may also run.
     *
     * @param activationID our activation id
     * @param data log directory name as a MarshalledObject
     * @param sharedActivationRef reference to the shared activation object
     */
//      public RegistrarImpl(ActivationID activationID,
//                           MarshalledObject data,
//                           SharedActivation sharedActivationRef)
//                                                             throws IOException
//      {
//  	this.activationID = activationID;
//          this.sharedActivationRef = sharedActivationRef;
//  	Activatable.exportObject(this, activationID, 0);
//  	try {
//  	    logDirname = (String)data.get();
//  	} catch (Exception e) {
//  	    throw new IllegalArgumentException
//  					  ("bad directory name in descriptor");
//  	}
//  	init();
//      }

    /**
     * Called by ServiceStarter.createTransient for transient instances.
     *
     * @param logDirname log directory name
     */
    RegistrarImpl(String logDirname) throws IOException {
	UnicastRemoteObject.exportObject(this);
	this.logDirname = logDirname;
	init();
    }

    /** A service item registration record. */
    private final static class SvcReg implements Comparable, Serializable {

	private static final long serialVersionUID = -1626838158255069853L;

	/**
	 * The service item.
	 *
	 * @serial
	 */
	public final Item item;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	public final long leaseID;
	/**
	 * The lease expiration time.
	 *
	 * @serial
	 */
	public long leaseExpiration;

	/** Simple constructor */
	public SvcReg(Item item, long leaseID, long leaseExpiration) {
	    this.item = item;
	    this.leaseID = leaseID;
	    this.leaseExpiration = leaseExpiration;
	}

	/**
	 * Primary sort by leaseExpiration, secondary by leaseID.  The
	 * secondary sort is immaterial, except to ensure a total order
	 * (required by TreeMap).
	 */
	public int compareTo(Object obj) {
	    SvcReg reg = (SvcReg)obj;
	    if (this == reg)
		return 0;
	    if (leaseExpiration < reg.leaseExpiration ||
		(leaseExpiration == reg.leaseExpiration &&
		 leaseID < reg.leaseID))
		return -1;
	    return 1;
	}
    }

    /** An event registration record. */
    private final static class EventReg implements Comparable, Serializable {

	private static final long serialVersionUID = -1549670962624946202L;

	/**
	 * The event id.
	 * @serial
	 */
	public final long eventID;
	/**
	 * The lease id.
	 * @serial
	 */
	public final long leaseID;
	/**
	 * The template to match.
	 * @serial
	 */
	public final Template tmpl;
	/**
	 * The transitions.
	 *
	 * @serial
	 */
	public final int transitions;
	/**
	 * The current sequence number.
	 *
	 * @serial
	 */
	public long seqNo;
	/**
	 * The event listener.
	 */
	public transient RemoteEventListener listener;
	/**
	 * The handback object.
	 *
	 * @serial
	 */
	public final MarshalledObject handback;
	/**
	 * The lease expiration time.
	 *
	 * @serial
	 */
	public long leaseExpiration;

	/** Simple constructor */
	public EventReg(long eventID, long leaseID, Template tmpl,
			int transitions, RemoteEventListener listener,
			MarshalledObject handback, long leaseExpiration) {
	    this.eventID = eventID;
	    this.leaseID = leaseID;
	    this.tmpl = tmpl;
	    this.transitions = transitions;
	    this.seqNo = 0;
	    this.listener = listener;
	    this.handback = handback;
	    this.leaseExpiration = leaseExpiration;
	}

	/**
	 * Primary sort by leaseExpiration, secondary by eventID.  The
	 * secondary sort is immaterial, except to ensure a total order
	 * (required by TreeMap).
	 */
	public int compareTo(Object obj) {
	    EventReg reg = (EventReg)obj;
	    if (this == reg)
		return 0;
	    if (leaseExpiration < reg.leaseExpiration ||
		(leaseExpiration == reg.leaseExpiration &&
		 eventID < reg.eventID))
		return -1;
	    return 1;
	}

	/**
	 * @serialData RemoteEventListener as a MarshalledObject
	 */
	private void writeObject(ObjectOutputStream stream)
	    throws IOException
	{
	    stream.defaultWriteObject();
	    stream.writeObject(new MarshalledObject(listener));
	}

	/**
	 * Unmarshals the event listener.
	 */
	private void readObject(ObjectInputStream stream)
	    throws IOException, ClassNotFoundException
	{
	    stream.defaultReadObject();
	    MarshalledObject mo = (MarshalledObject)stream.readObject();
	    try {
		listener = (RemoteEventListener)mo.get();
	    } catch (Throwable e) {
		if (e instanceof Error &&
		    ThrowableConstants.retryable(e) ==
		    ThrowableConstants.BAD_OBJECT)
		    throw (Error)e;
	    }
	}
    }

    /**
     * Interface defining the method(s) that must be implemented by each of
     * the concrete LogObj classes. This allows for the definition of
     * object-dependent invocations of the appropriate implementation of
     * the method(s) declared in this interface.
     */
    private static interface LogRecord extends Serializable {
	void apply(RegistrarImpl regImpl);
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a new service is registered.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class SvcRegisteredLogObj implements LogRecord {

	private static final long serialVersionUID = 833681392248560066L;

	/**
	 * The service registration.
	 *
	 * @serial
	 */
	private SvcReg reg;

	/** Simple constructor */
	public SvcRegisteredLogObj(SvcReg reg) {
	    this.reg = reg;
	}
	
	/**
	 * Modifies the state of the Registrar by registering the service
         * stored in the reg object. Also needs to delete any existing
	 * service with the same serviceID; this can happen if a service
	 * re-registers while an existing registration is in effect, because
	 * we don't log a separate lease cancellation record for the existing
	 * registration in that case.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.resolver.resolve(reg.item);
	    SvcReg oldReg =
		(SvcReg)regImpl.serviceByID.get(reg.item.serviceID);
	    if (oldReg != null)
		regImpl.deleteService(oldReg, 0);
	    regImpl.addService(reg);
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * new attributes are added to an existing service in the Registrar.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class AttrsAddedLogObj implements LogRecord {

	private static final long serialVersionUID = -5347182410011723905L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private long leaseID;
	/**
	 * The attributes added.
	 *
	 * @serial
	 */
	private EntryRep[] attrSets;

	/** Simple constructor */
	public AttrsAddedLogObj(ServiceID serviceID,
				long leaseID,
				EntryRep[] attrSets)
	{
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	    this.attrSets = attrSets;
	}
	
	/**
	 * Modifies the state of the Registrar by adding to all of the
         * services matching the template, the attributes stored in
         * attributeSets.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    try {
		regImpl.addAttributesDo(serviceID, leaseID, attrSets);
	    } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering  */
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * existing attributes of an existing service in the Registrar are
     * modified.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class AttrsModifiedLogObj implements LogRecord {

	private static final long serialVersionUID = -2773350506956661576L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private long leaseID;
	/**
	 * The templates to match.
	 * @serial
	 */
	private EntryRep[] attrSetTmpls;
	/**
	 * The new attributes.
	 *
	 * @serial
	 */
	private EntryRep[] attrSets;

	/** Simple constructor */
	public AttrsModifiedLogObj(ServiceID serviceID,
				   long leaseID,
				   EntryRep[] attrSetTmpls,
				   EntryRep[] attrSets)
	{
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	    this.attrSetTmpls = attrSetTmpls;
	    this.attrSets = attrSets;
	}

	/**
	 * Modifies the state of the Registrar by modifying the attributes
         * of the services that match the template with the attributes 
         * stored in attributeSets.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    try {
		regImpl.modifyAttributesDo(serviceID, leaseID,
					   attrSetTmpls, attrSets);
	    } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering  */
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * new attributes are set on an existing service in the Registrar.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class AttrsSetLogObj implements LogRecord {

	private static final long serialVersionUID = -395979667535255420L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private long leaseID;
	/**
	 * The new attributes.
	 *
	 * @serial
	 */
	private EntryRep[] attrSets;

	/** Simple constructor */
	public AttrsSetLogObj(ServiceID serviceID,
			      long leaseID,
			      EntryRep[] attrSets)
	{
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	    this.attrSets = attrSets;
	}
	
	/**
	 * Modifies the state of the Registrar by replacing the attributes
         * of the services matching the template with the attributes stored
         * in attributeSets.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    try {
		regImpl.setAttributesDo(serviceID, leaseID, attrSets);
	    } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering  */
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a new event is registered.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class EventRegisteredLogObj implements LogRecord {

	private static final long serialVersionUID = -807655888250060611L;

	/**
	 * The event registration.
	 *
	 * @serial
	 */
	private EventReg eventReg;

	/** Simple constructor */
	public EventRegisteredLogObj(EventReg eventReg) {
	    this.eventReg = eventReg;
	}
	
	/**
	 * Modifies the state of the Registrar by registering the event
         * stored in the eventReg object; and by updating both the event
         * sequence number and the event ID.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.resolver.resolve(eventReg.tmpl);
	    eventReg.seqNo += Integer.MAX_VALUE;
	    regImpl.addEvent(eventReg);
	    regImpl.eventID++;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on an existing service in the Registrar is cancelled.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class ServiceLeaseCancelledLogObj implements LogRecord {

	private static final long serialVersionUID = 8363406735506378972L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private long leaseID;

	/** Simple constructor */
	public ServiceLeaseCancelledLogObj(ServiceID serviceID, long leaseID) {
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	}
	
	/**
	 * Modifies the state of the Registrar by cancelling the lease
         * having ID equal to the contents of the leaseID field; and
         * corresponding to the service with ID equal to the contents of
         * the serviceID field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    try {
		regImpl.cancelServiceLeaseDo(serviceID, leaseID);
	    } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering  */
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on an existing service in the Registrar is renewed.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class ServiceLeaseRenewedLogObj implements LogRecord {

	private static final long serialVersionUID = -6941618092365889931L;

	/**
	 * The service id.
	 *
	 * @serial
	 */
	private ServiceID serviceID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private long leaseID;
	/**
	 * The new lease expiration time.
	 *
	 * @serial
	 */
	private long leaseExpTime;

	/** Simple constructor */
	public ServiceLeaseRenewedLogObj(ServiceID serviceID,
					 long leaseID,
					 long leaseExpTime)
	{
	    this.serviceID = serviceID;
	    this.leaseID = leaseID;
	    this.leaseExpTime = leaseExpTime;
	}
	
	/**
	 * Modifies the state of the Registrar by renewing the lease
         * having ID equal to the contents of the leaseID field; and
         * corresponding to the service with ID equal to the contents
         * of the serviceID field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.renewServiceLeaseAbs(serviceID, leaseID, leaseExpTime);
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on a registered event is cancelled.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class EventLeaseCancelledLogObj implements LogRecord {

	private static final long serialVersionUID = 723479933309720973L;

	/**
	 * The event id.
	 *
	 * @serial
	 */
	private long eventID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private long leaseID;

	/** Simple constructor */
	public EventLeaseCancelledLogObj(long eventID, long leaseID) {
	    this.eventID = eventID;
	    this.leaseID = leaseID;
	}
	
	/**
	 * Modifies the state of the Registrar by cancelling the lease
         * corresponding to the event with ID equal to the contents of
         * the eventID field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    try {
		regImpl.cancelEventLeaseDo(eventID, leaseID);
	    } catch (UnknownLeaseException e) {
		/* this exception should never occur when recovering */
	    }
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a lease on a registered event is renewed.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class EventLeaseRenewedLogObj implements LogRecord {

	private static final long serialVersionUID = -2313983070714702699L;

	/**
	 * The event id.
	 *
	 * @serial
	 */
	private long eventID;
	/**
	 * The lease id.
	 *
	 * @serial
	 */
	private long leaseID;
	/**
	 * The new lease expiration time.
	 *
	 * @serial
	 */
	private long leaseExpTime;

	/** Simple constructor */
	public EventLeaseRenewedLogObj(long eventID,
				       long leaseID,
				       long leaseExpTime)
	{
	    this.eventID = eventID;
	    this.leaseID = leaseID;
	    this.leaseExpTime = leaseExpTime;
	}

	/**
	 * Modifies the state of the Registrar by renewing the lease
         * corresponding to the event with ID equal to the contents of
         * the eventID field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.renewEventLeaseAbs(eventID, leaseID, leaseExpTime);
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * a leases in the Registrar is renewed via a LeaseMap.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LeasesRenewedLogObj implements LogRecord {

	private static final long serialVersionUID = -2796802215410373050L;

	/**
	 * The service and event ids.
	 *
	 * @serial
	 */
	private Object[] regIDs;
	/**
	 * The lease ids.
	 *
	 * @serial
	 */
	private long[] leaseIDs;
	/**
	 * The new lease expiration times.
	 *
	 * @serial
	 */
	private long[] leaseExpTimes;

	/** Simple constructor */
	public LeasesRenewedLogObj(Object[] regIDs,
				   long[] leaseIDs,
				   long[] leaseExpTimes)
	{
	    this.regIDs = regIDs;
	    this.leaseIDs = leaseIDs;
	    this.leaseExpTimes = leaseExpTimes;
	}
	
	/**
	 * Modifies the state of the Registrar by renewing the specified
         * leases.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.renewLeasesAbs(regIDs, leaseIDs, leaseExpTimes);
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * lease are cancelled via a LeaseMap.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LeasesCancelledLogObj implements LogRecord {

	private static final long serialVersionUID = 7462419701214242565L;

	/**
	 * The service and event ids.
	 *
	 * @serial
	 */
	private Object[] regIDs;
	/**
	 * The lease ids.
	 *
	 * @serial
	 */
	private long[] leaseIDs;

	/** Simple constructor */
	public LeasesCancelledLogObj(Object[] regIDs, long[] leaseIDs) {
	    this.regIDs = regIDs;
	    this.leaseIDs = leaseIDs;
	}
	
	/**
	 * Modifies the state of the Registrar by cancelling the specified
         * leases.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    /* Exceptions can be returned, since we didn't weed out unknown
	     * leases before logging, but we can just ignore them anyway.
	     */
	    regImpl.cancelLeasesDo(regIDs, leaseIDs);
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the Unicast Port Number is set to a new value.
     * <p>
     * Note: the apply() method of this class merely sets the private field
     *       unicastPort. This means that during a recovery, the unicaster
     *       thread will be created with this new port number ONLY IF that
     *       thread is created AFTER recovery is complete. Thus, it is 
     *       important that at re-initialization during a re-activation
     *       of the Registrar, the recovery() method is invoked before
     *       the unicaster thread is created.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class UnicastPortSetLogObj implements LogRecord {

	private static final long serialVersionUID = -5923066193628958723L;

	/**
	 * The new port number.
	 *
	 * @serial
	 */
	private int newPort;

	/** Simple constructor */
	public UnicastPortSetLogObj(int newPort) {
	    this.newPort = newPort;
	}

	/**
	 * Modifies the state of the Registrar by setting the value of the
         * private unicastPort field to the value of the newPort field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.unicastPort = newPort;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the Minimum Maximum Service Lease Duration is set to a new value.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class MinMaxServiceLeaseSetLogObj implements LogRecord {

	private static final long serialVersionUID = 3205940543108355772L;

	/**
	 * The new minimum maximum service lease duration.
	 *
	 * @serial
	 */
	private long newLeaseDuration;

	/** Simple constructor */
	public MinMaxServiceLeaseSetLogObj(long newLeaseDuration) {
	    this.newLeaseDuration = newLeaseDuration;
	}

	/**
	 * Modifies the state of the Registrar by setting the value of
         * the private minMaxServiceLease field to the value of the
         * newLeaseDuration field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.minMaxServiceLease = newLeaseDuration;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the Minimum Maximum Event Lease Duration is set to a new value.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class MinMaxEventLeaseSetLogObj implements LogRecord {

	private static final long serialVersionUID = -6425690586422726211L;

	/**
	 * The new minimum maximum event lease duration.
	 *
	 * @serial
	 */
	private long newLeaseDuration;

	/** Simple constructor */
	public MinMaxEventLeaseSetLogObj(long newLeaseDuration) {
	    this.newLeaseDuration = newLeaseDuration;
	}

	/**
	 * Modifies the state of the Registrar by setting the value of 
         * the private minMaxEventLease field to the value of the
         * newLeaseDuration field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.minMaxEventLease = newLeaseDuration;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the Minimum Renewal Interval is set to a new value.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class MinRenewalIntervalSetLogObj implements LogRecord {

	private static final long serialVersionUID = 5812923613520666861L;

	/**
	 * The new minimum renewal interval.
	 *
	 * @serial
	 */
	private long newRenewalInterval;

	/** Simple constructor */
	public MinRenewalIntervalSetLogObj(long newRenewalInterval) {
	    this.newRenewalInterval = newRenewalInterval;
	}

	/**
	 * Modifies the state of the Registrar by setting the value of
         * the private minRenewalInterval field to the value of the
         * newRenewalInterval.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.minRenewalInterval = newRenewalInterval;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the Weight Factor applied to the size of the Snapshot File in the
     * "snapshot determination" expression is set to a new value.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class SnapshotWeightSetLogObj implements LogRecord {

	private static final long serialVersionUID = -7068462033891338457L;

	/**
	 * The new snapshot weight factor.
	 *
	 * @serial
	 */
	private float newWeight;

	/** Simple constructor */
	public SnapshotWeightSetLogObj(float newWeight) {
	    this.newWeight = newWeight;
	}

	/**
	 * Modifies the state of the Registrar by setting the value of the
         * private snapshotWt field to the value of the newWeight field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.snapshotWt = newWeight;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set of groups to join is changed.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LookupGroupsChangedLogObj implements LogRecord {

	private static final long serialVersionUID = -5164130449456011085L;

	/**
	 * The new groups to join.
	 *
	 * @serial
	 */
	private String[] groups;

	/** Simple constructor */
	public LookupGroupsChangedLogObj(String[] groups) {
	    this.groups = groups;
	}

	/**
	 * Modifies the state of the Registrar by setting the private
         * field lookupGroups to the reference to the groups field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.lookupGroups = groups;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the set of locators of lookup services to join is changed.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LookupLocatorsChangedLogObj implements LogRecord {

	private static final long serialVersionUID = 7707774807971026109L;

	/**
	 * The new locators to join.
	 *
	 * @serial
	 */
	private LookupLocator[] locators;

	/** Simple constructor */
	public LookupLocatorsChangedLogObj(LookupLocator[] locators) {
	    this.locators = locators;
	}

	/**
	 * Modifies the state of the Registrar by setting the private
	 * field lookupLocators to the reference to the locators field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.lookupLocators = locators;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the Threshold employed in the "snapshot determination" expression
     * is set to a new value.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class LogToSnapshotThresholdSetLogObj implements LogRecord {

	private static final long serialVersionUID = 7141538629320197819L;

	/**
	 * The new snapshot threshold.
	 *
	 * @serial
	 */
	private int newThreshold;

	/** Simple constructor */
	public LogToSnapshotThresholdSetLogObj(int newThreshold) {
	    this.newThreshold = newThreshold;
	}

	/**
	 * Modifies the state of the Registrar by setting the value
         * of the private logToSnapshotThresh field to the value of
         * the newThreshold field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.logToSnapshotThresh = newThreshold;
	}
    }

    /**
     * LogObj class whose instances are recorded to the log file whenever
     * the memberGroups array is set to reference a new array of strings.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private static class MemberGroupsChangedLogObj implements LogRecord {

	private static final long serialVersionUID = 4764341386996269738L;

	/**
	 * The new groups to be a member of.
	 *
	 * @serial
	 */
	private String[] groups;

	/** Simple constructor */
	public MemberGroupsChangedLogObj(String[] groups) {
	    this.groups = groups;
	}

	/**
	 * Modifies the state of the Registrar by setting the private
         * memberGroups field to the reference to the groups field.
         * 
         * @see RegistrarImpl.LocalLogHandler#applyUpdate
	 */
	public void apply(RegistrarImpl regImpl) {
	    regImpl.memberGroups = groups;
	}
    }

    /**
     * Handler class for the persistent storage facility.
     * <p>
     * At any point during processing in the Registrar, there will exist
     * both a 'snapshot' of the Registrar's state and a set of records
     * detailing each significant change that has occurred to the state
     * since the snapshot was taken. The snapshot information and the
     * incremental change information will be stored in separate files
     * called, respectively, the snapshot file and the log file. Together,
     * these files are used to recover the state of the Registrar after a
     * crash or a network outage (or if the Registrar or its ActivationGroup
     * is un-registered and then re-registered through the Activation Daemon).
     * <p>
     * This class contains the methods that are used to record and recover
     * the snapshot of the Registrar's state; as well as the method used to
     * apply the state changes that were recorded in the log file.
     * <p>
     * When the ReliableLog class is instantiated, a new instance of this
     * class is passed to its constructor so that the methods of this
     * class may be invoked by the methods defined in the ReliableLog.
     * Because this class extends the LogHandler class associated with
     * the ReliableLog class, this class must provide implementations of
     * the abstract methods declared in the LogHandler. Also, some of the
     * methods defined in this class override the methods of the LogHandler
     * in order to customize the handling of snapshot creation and
     * retrieval.
     * <p>
     * Each significant change to the Registrar's state is written to the
     * log file as an individual record (when addLogRecord() is invoked).
     * After the number of records logged exceeds a pre-defined threshold,
     * a snapshot of the state is recorded by invoking -- through the
     * ReliableLog and its LogHandler -- the snapshot() method defined in
     * this class. After the snapshot is taken, the log file is cleared
     * and the incremental log process starts over.
     * <p>
     * The contents of the snapshot file reflect the DATA contained in
     * the fields making up the current state of the Registrar. That data
     * represents many changes -- over time -- to the Registrar's state.
     * On the other hand, each record written to the log file is an object
     * that reflects both the data used and the ACTIONS taken to make one
     * change to the Registrar's state at a particular point in time.
     * <p>
     * The data written to the snapshot file is shown below:
     * <ul>
     * <li> our service ID
     * <li> current lease ID
     * <li> current event ID
     * <li> current configuration parameters
     * <li> contents of the container holding the current registered services
     * <li> null (termination 'marker' for the set of registered services)
     * <li> contents of the container holding the current registered events
     * <li> null (termination 'marker' for the set of registered events)
     * </ul>
     * The type of state changes that will generate a new record in the log
     * file are:
     * <ul>
     * <li> a new service was registered
     * <li> a new event was registered
     * <li> new attributes were added to an existing service
     * <li> existing attributes of a service were modified
     * <li> a service lease was cancelled
     * <li> a service lease was renewed
     * <li> an event lease was cancelled
     * <li> an event lease was renewed
     * <li> a configuration parameter was changed
     * </ul>
     * During recovery, the state of the Registrar at the time of a crash
     * or outage is re-constructed by first retrieving the 'base' state from
     * the snapshot file; and then modifying that base state according to
     * the records retrieved from the log file. The reconstruction of the
     * base state is achieved by invoking the recover() method defined in
     * this class. The modifications recorded in the log file are then
     * applied to the base state by invoking the applyUpdate() method
     * defined in this class. Both recover() and applyUpdate() are invoked
     * through the ReliableLog and its associated LogHandler.
     * <p>
     * NOTE: The following lines must be added to the Registrar's policy file
     * <pre>
     *     permission java.io.FilePermission "dirname",   "read,write,delete";
     *     permission java.io.FilePermission "dirname/-", "read,write,delete";
     * </pre>
     *     where 'dirname' is the name of the directory path (relative or
     *     absolute) where the snapshot and log file will be maintained.
     */
    private class LocalLogHandler extends LogHandler {
	
	/** Simple constructor */
	public LocalLogHandler() { }

	/* Overrides snapshot() defined in ReliableLog's LogHandler class. */
	public void snapshot(OutputStream out) throws IOException {
	    takeSnapshot(out);
	}

	/* Overrides recover() defined in ReliableLog's LogHandler class. */
	public void recover(InputStream in)
	    throws IOException, ClassNotFoundException
	{
	    recoverSnapshot(in);
	}

	/**
	 * Required method implementing the abstract applyUpdate()
	 * defined in ReliableLog's associated LogHandler class.
	 * <p>
	 * During state recovery, the recover() method defined in the
	 * ReliableLog class is invoked. That method invokes the method
	 * recoverUpdates() which invokes the method readUpdates(). Both
	 * of those methods are defined in ReliableLog. The method
	 * readUpdates() retrieves a record from the log file and then
	 * invokes this method.
	 * <p>
	 * This method invokes the version of the method apply() that
	 * corresponds to the particular type of 'log record' object
	 * that is input as the first argument. The log record object and its
	 * corresponding apply() method are defined in one of the so-called
	 * LogObj classes. Any instance of one the LogObj classes is an
	 * implementation of the LogRecord interface. The particular
	 * implementation that is input to this method is dependent on the
	 * type of record that was originally logged. The apply() method
	 * will then modify the state of the Registrar in a way dictated
	 * by the type of record that was retrieved.
	 */
	public void applyUpdate(Object logRecObj) {
	    ((LogRecord)logRecObj).apply(RegistrarImpl.this);
	}
    }

    /** Base class for iterating over all Items that match a Template. */
    private abstract class ItemIter {
	/** Current time */
	public final long now = System.currentTimeMillis();
	/** True means duplicate items are possible */
	public boolean dupsPossible = false;
	/** Template to match */
	protected final Template tmpl;
	/** Next item to return */
	protected SvcReg reg;

	/** Subclass constructors must initialize reg */
	protected ItemIter(Template tmpl) {
	    this.tmpl = tmpl;
	}

	/** Returns true if the iteration has more elements. */
	public boolean hasNext() {
	    return reg != null;
	}

	/** Returns the next element in the iteration as an Item. */
	public Item next() {
	    if (reg == null)
		throw new NoSuchElementException();
	    Item item = reg.item;
	    step();
	    return item;
	}

	/** Returns the next element in the iteration as a SvcReg. */
	public SvcReg nextReg() {
	    if (reg == null)
		throw new NoSuchElementException();
	    SvcReg cur = reg;
	    step();
	    return cur;
	}

	/** Set reg to the next matching element, or null if none */
	protected abstract void step();
    }

    /** Iterate over all Items. */
    private class AllItemIter extends ItemIter {
	/** Iterator over serviceByID */
	private final Iterator iter;

	/** Assumes the empty template */
	public AllItemIter() {
	    super(null);
	    iter = serviceByID.values().iterator();
	    step();
	}

	/** Set reg to the next matching element, or null if none */
	protected void step() {
	    while (iter.hasNext()) {
		reg = (SvcReg)iter.next();
		if (reg.leaseExpiration > now)
		    return;
	    }
	    reg = null;
	}
    }

    /** Iterate over all matching Items by ServiceType. */
    private class TypeItemIter extends ItemIter {
	/** Concrete classes matching tmpl.serviceTypes */
	private final ServiceType[] types;
	/** Current index into types */
	private int typeidx;
	/** SvcRegs obtained from serviceByType for current service type */
	private ArrayList svcs;
	/** Current index into svcs */
	private int svcidx = 0;

	/**
	 * tmp.serviceID == null and
	 * tmpl.serviceTypes is non-empty
	 */
	public TypeItemIter(Template tmpl) {
	    super(tmpl);
	    types = matchingConcreteClasses(tmpl.serviceTypes);
	    typeidx = types.length;
	    step();
	}

	/** Set reg to the next matching element, or null if none */
	protected void step() {
	    do {
		while (--svcidx >= 0) {
		    reg = (SvcReg)svcs.get(svcidx);
		    if (reg.leaseExpiration > now &&
			matchAttributes(tmpl, reg.item))
			return;
		}
	    } while (stepType());
	    reg = null;
	}

	/**
	 * Step to the next type in types, if any, reset svcs and svcidx,
	 * and return false if types exhausted.
	 */
	private boolean stepType() {
	    if (--typeidx < 0)
		return false;
	    svcs = (ArrayList)serviceByType.get(types[typeidx]);
	    svcidx = svcs.size();
	    return true;
	}
    }

    /** Iterate over all matching Items by attribute value. */
    private class AttrItemIter extends ItemIter {
	/** SvcRegs obtained from serviceByAttr for chosen attr */
	protected ArrayList svcs;
	/** Current index into svcs */
	protected int svcidx;

	/**
	 * tmpl.serviceID == null and
	 * tmpl.serviceTypes is empty and
	 * tmpl.attributeSetTemplates[setidx].fields[fldidx] != null
	 */
	public AttrItemIter(Template tmpl, int setidx, int fldidx) {
	    super(tmpl);
	    EntryRep set = tmpl.attributeSetTemplates[setidx];
	    HashMap[] attrMaps =
		(HashMap[])serviceByAttr.get(getDefiningClass(set.eclass,
							      fldidx));
	    if (attrMaps != null && attrMaps[fldidx] != null) {
		svcs = (ArrayList)attrMaps[fldidx].get(set.fields[fldidx]);
		if (svcs != null) {
		    svcidx = svcs.size();
		    step();
		}
	    }
	}

	/** Simple constructor */
	protected AttrItemIter(Template tmpl) {
	    super(tmpl);
	}

	/** Set reg to the next matching element, or null if none. */
	protected void step() {
	    while (--svcidx >= 0) {
		reg = (SvcReg)svcs.get(svcidx);
		if (reg.leaseExpiration > now &&
		    matchAttributes(tmpl, reg.item))
		    return;
	    }
	    reg = null;
	}
    }

    /** Iterate over all matching Items by no-fields entry class. */
    private class EmptyAttrItemIter extends AttrItemIter {

	/**
	 * tmpl.serviceID == null and
	 * tmpl.serviceTypes is empty and
	 * eclass has no fields
	 */
	public EmptyAttrItemIter(Template tmpl, EntryClass eclass) {
	    super(tmpl);
	    svcs = (ArrayList)serviceByEmptyAttr.get(eclass);
	    if (svcs != null) {
		svcidx = svcs.size();
		step();
	    }
	}
    }

    /** Iterate over all matching Items by entry class, dups possible. */
    private class ClassItemIter extends ItemIter {
	/** Entry class to match on */
	private final EntryClass eclass;
	/** Current index into entryClasses */
	private int classidx;
	/** Values iterator for current HashMap */
	private Iterator iter;
	/** SvcRegs obtained from iter or serviceByEmptyAttr */
	private ArrayList svcs;
	/** Current index into svcs */
	private int svcidx = 0;

	/**
	 * tmpl.serviceID == null and
	 * tmpl.serviceTypes is empty and
	 * tmpl.attributeSetTemplates is non-empty
	 */
	public ClassItemIter(Template tmpl) {
	    super(tmpl);
	    dupsPossible = true;
	    eclass = tmpl.attributeSetTemplates[0].eclass;
	    classidx = entryClasses.size();
	    step();
	}

	/** Set reg to the next matching element, or null if none */
	protected void step() {
	    do {
		while (--svcidx >= 0) {
		    reg = (SvcReg)svcs.get(svcidx);
		    if (reg.leaseExpiration > now &&
			matchAttributes(tmpl, reg.item))
			return;
		}
	    } while (stepValue());
	    reg = null;
	}

	/**
	 * Step to the next HashMap value, if any, reset svcs and svcidx,
	 * and return false if everything exhausted.
	 */
	private boolean stepValue() {
	    while (true) {
		if (iter != null && iter.hasNext()) {
		    svcs = (ArrayList)iter.next();
		    svcidx = svcs.size();
		    return true;
		}
		if (!stepClass())
		    return false;
		if (iter == null)
		    return true;
	    }
	}

	/**
	 * Step to the next matching entry class, if any, reset iter
	 * using the HashMap for the last field of the class (and reset
	 * (svcs and svcidx if the entry class has no fields), and
	 * return false if everything exhausted.
	 */
	private boolean stepClass() {
	    while (--classidx >= 0) {
		EntryClass cand = (EntryClass)entryClasses.get(classidx);
		if (!eclass.isAssignableFrom(cand))
		    continue;
		if (cand.getNumFields() > 0) {
		    cand = getDefiningClass(cand, cand.getNumFields() - 1);
		    HashMap[] attrMaps = (HashMap[])serviceByAttr.get(cand);
		    iter = attrMaps[attrMaps.length - 1].values().iterator();
		} else {
		    iter = null;
		    svcs = (ArrayList)serviceByEmptyAttr.get(cand);
		    svcidx = svcs.size();
		}
		return true;
	    }
	    return false;
	}
    }

    /** Iterate over a singleton matching Item by serviceID. */
    private class IDItemIter extends ItemIter {

	/** tmpl.serviceID != null */
	public IDItemIter(Template tmpl) {
	    super(tmpl);
	    reg = (SvcReg)serviceByID.get(tmpl.serviceID);
	    if (reg != null &&
		(reg.leaseExpiration <= now || !matchItem(tmpl, reg.item)))
		reg = null;
	}

	/** Set reg to null */
	protected void step() {
	    reg = null;
	}
    }

    /** An event to be sent, and the listener to send it to. */
    private final class EventTask implements TaskManager.Task {

	/** The event registration */
	public final EventReg reg;
	/** The sequence number of this event */
	public final long seqNo;
	/** The service id */
	public final ServiceID sid;
	/** The new state of the item, or null if deleted */
	public final Item item;
	/** The transition that fired */
	public final int transition;

	/** Simple constructor, except increments reg.seqNo. */
	public EventTask(EventReg reg,
			 ServiceID sid,
			 Item item,
			 int transition)
	{
	    this.reg = reg;
	    seqNo = ++reg.seqNo;
	    this.sid = sid;
	    this.item = item;
	    this.transition = transition;
	}

	/** Send the event */
	public void run() {
	    try {
		reg.listener.notify(new RegistrarEvent(proxy, reg.eventID,
						       seqNo, reg.handback,
						       sid, transition, item));
	    } catch (Throwable e) {
		switch (ThrowableConstants.retryable(e)) {
		case ThrowableConstants.BAD_OBJECT:
		    if (e instanceof Error)
			throw (Error)e;
		case ThrowableConstants.BAD_INVOCATION:
		case ThrowableConstants.UNCATEGORIZED:
		    /* If the listener throws UnknownEvent or some other
		     * definite exception, we can cancel the lease.
		     */
		    try {
			cancelEventLease(reg.eventID, reg.leaseID);
		    } catch (UnknownLeaseException ee) {
		    }
		}
	    }
	}

	/** Keep events going to the same listener ordered. */
	public boolean runAfter(List tasks, int size) {
	    for (int i = size; --i >= 0; ) {
		Object obj = tasks.get(i);
		if (obj instanceof EventTask &&
		    reg.listener.equals(((EventTask)obj).reg.listener))
		    return true;
	    }
	    return false;
	}
    }

    /** Address for unicast discovery response. */
    private final class AddressTask implements TaskManager.Task {

	/** The address */
	public final InetAddress addr;
	/** The port */
	public final int port;

	/** Simple constructor */
	public AddressTask(InetAddress addr, int port) {
	    this.addr = addr;
	    this.port = port;
	}

	public int hashCode() {
	    return addr.hashCode();
	}

	/** Two tasks are equal if they have the same address and port */
	public boolean equals(Object obj) {
	    if (!(obj instanceof AddressTask))
		return false;
	    AddressTask ua = (AddressTask)obj;
	    return addr.equals(ua.addr) && port == ua.port;
	}

	/** Connect and then process a unicast discovery request */
	public void run() {
	    try {
		respond(new Socket(addr, port));
	    } catch (IOException e) {
	    } catch (SecurityException e) {
	    }
	}

	/** No ordering */
	public boolean runAfter(List tasks, int size) {
	    return false;
	}
    }

    /** Socket for unicast discovery response. */
    private final class SocketTask implements TaskManager.Task {

	/** The socket */
	public final Socket socket;

	/** Simple constructor */
	public SocketTask(Socket socket) {
	    this.socket = socket;
	}

	/** Process a unicast discovery request */
	public void run() {
	    respond(socket);
	}

	/** No ordering */
	public boolean runAfter(List tasks, int size) {
	    return false;
	}
    }

    /** Service lease expiration thread code */
    private class ServiceExpireThread extends Thread {
	/** True if thread has been interrupted */
	private boolean interrupted = false;

	/** Create a daemon thread */
	public ServiceExpireThread() {
	    super("service expire");
	    setDaemon(true);
	}

	public synchronized void interrupt() {
	    interrupted = true;
	    super.interrupt();
	}

	public synchronized boolean isInterrupted() {
	    return interrupted;
	}

	public void run() {
	    try {
		concurrentObj.writeLock();
	    } catch (ConcurrentLockException e) {
		return;
	    }
	    try {
		while (!isInterrupted()) {
		    long now = System.currentTimeMillis();
		    while (true) {
			SvcReg reg = (SvcReg)serviceByTime.firstKey();
			minSvcExpiration = reg.leaseExpiration;
			if (minSvcExpiration > now)
			    break;
			deleteService(reg, now);
			addLogRecord(new ServiceLeaseCancelledLogObj(
					    reg.item.serviceID, reg.leaseID));
		    }
		    queueEvents();
		    try {
			concurrentObj.writerWait(serviceNotifier,
						 minSvcExpiration - now);
		    } catch (ConcurrentLockException e) {
			return;
		    }
		}
	     } finally {
		 concurrentObj.writeUnlock();
	     }
	}
    }

    /** Event lease expiration thread code */
    private class EventExpireThread extends Thread {

	/** Create a daemon thread */
	public EventExpireThread() {
	    super("event expire");
	    setDaemon(true);
	}

	public void run() {
	    try {
		concurrentObj.writeLock();
	    } catch (ConcurrentLockException e) {
		return;
	    }
	    try {
		while (!isInterrupted()) {
		    long now = System.currentTimeMillis();
		    minEventExpiration = Long.MAX_VALUE;
		    while (!eventByTime.isEmpty()) {
			EventReg reg = (EventReg)eventByTime.firstKey();
			if (reg.leaseExpiration > now) {
			    minEventExpiration = reg.leaseExpiration;
			    break;
			}
			deleteEvent(reg);
		    }
		    try {
			concurrentObj.writerWait(eventNotifier,
						 minEventExpiration - now);
		    } catch (ConcurrentLockException e) {
			return;
		    }
		}
	     } finally {
		 concurrentObj.writeUnlock();
	     }
	}
    }

    /**
     * Termination thread code.  We do this in a separate thread to
     * avoid deadlock, because Activatable.inactive will block until
     * in-progress RMI calls are finished.
     */
    private class DestroyThread extends Thread {
        /** Maximum delay for unexport attempts */
        private static final long MAX_UNEXPORT_DELAY = 2*TimeConstants.MINUTES;

	/** Create a non-daemon thread */
	public DestroyThread() {
	    super("destroy");
	    /* override inheritance from RMI daemon thread */
	    setDaemon(false);
	}

	public void run() {

	    /* must unregister before unexport */
	    if (activationID != null) {
		try {
		    Activatable.unregister(activationID);
		} catch (RemoteException e) {
		    /* give up until we can at least unregister */
		    return;
		} catch (ActivationException e) {
		}
	    }
            final long endTime = System.currentTimeMillis()+MAX_UNEXPORT_DELAY;
            boolean unexported = false;
            try {
                /* Unexport only if there are no pending or in progress calls*/
                while(!unexported && (System.currentTimeMillis() < endTime)) {
                    unexported = UnicastRemoteObject.unexportObject
                                                    (RegistrarImpl.this,false);
                    if(!unexported) Thread.yield();
                }//end loop
            } catch (NoSuchObjectException e) {
                unexported = true;
            }
            if(!unexported) {//Not yet unexported. Forcibly unexport
                try {
                    UnicastRemoteObject.unexportObject
                                                    (RegistrarImpl.this,true);
                } catch (NoSuchObjectException e) { }
            }//endif
	    /* all daemons must terminate before deleting persistent store */
	    serviceExpirer.interrupt();
	    eventExpirer.interrupt();
	    unicaster.interrupt();
	    multicaster.interrupt();
	    announcer.interrupt();
	    snapshotter.interrupt();
	    tasker.terminate();
	    joiner.terminate();
	    discoer.terminate();
	    try {
		serviceExpirer.join();
		eventExpirer.join();
		unicaster.join();
		multicaster.join();
		announcer.join();
		snapshotter.join();
	    } catch (InterruptedException e) {
	    }
	    closeRequestSockets(tasker.getPending());
	    log.deletePersistentStore();
	    if (activationID != null) {
		/* inactive will set current group ID to null */
		ActivationGroupID gid = ActivationGroup.currentGroupID();
		try {
		    Activatable.inactive(activationID);
		} catch (RemoteException e) {
		} catch (ActivationException e) {
		}
                /* If group is not 'shared', must destroy the group (VM) */
//                  if (sharedActivationRef == null) {//not in a 'shared' group
		    try {
		        ActivationGroup.getSystem().unregisterGroup(gid);
		    } catch (RemoteException e) {
		    } catch (ActivationException e) {
		    }
//                  }
	    }
	}
    }

    /** Multicast discovery request thread code. */
    private class MulticastThread extends Thread {
	/** Multicast socket to receive packets */
	private final MulticastSocket socket;

	/**
	 * Create a high priority daemon thread.  Set up the socket now
	 * rather than in run, so that we get any exception up front.
	 */
	public MulticastThread() throws IOException {
	    super("multicast request");
	    setDaemon(true);
	    setPriority(Thread.MAX_PRIORITY);
	    socket = new MulticastSocket(Constants.discoveryPort);
            if((nicAddresses != null) && (nicAddresses.length > 0)) {
                for(int i=0;i<nicAddresses.length;i++) {
                    socket.setInterface(nicAddresses[i]);
                    socket.joinGroup(Constants.getRequestAddress());
                }//end loop
            } else {//use default interface
                socket.joinGroup(Constants.getRequestAddress());
            }//endif
	}

	/** True if thread has been interrupted */
	private boolean interrupted = false;

	/* This is a workaround for Thread.interrupt not working on
	 * MulticastSocket.receive on all platforms.
	 */
	public synchronized void interrupt() {
	    interrupted = true;
	    socket.close();
	}

	public synchronized boolean isInterrupted() {
	    return interrupted;
	}

	public void run() {
	    byte[] buf = new byte[576];
	    DatagramPacket dgram = new DatagramPacket(buf, buf.length);
	    while (!isInterrupted()) {
		try {
		    dgram.setLength(buf.length);
		    try {
			socket.receive(dgram);
		    } catch (NullPointerException e) {
			break; // workaround for bug 4190513
		    }
		    IncomingMulticastRequest req =
			new IncomingMulticastRequest(dgram);
		    if ((req.getGroups().length != 0 &&
			 !overlap(memberGroups, req.getGroups())) ||
			indexOf(req.getServiceIDs(), myServiceID) >= 0)
			continue;
		    tasker.addIfNew(new AddressTask(req.getAddress(),
						    req.getPort()));
		} catch (InterruptedIOException e) {
		    break;
		} catch (IOException e) {
		} catch (SecurityException e) {
		}
		/* if we fail in any way, just forget about it */
	    }
	    socket.close();
	}
    }

    /** Unicast discovery request thread code. */
    private class UnicastThread extends Thread {
	/** Server socket to accepts connections on. */
	private ServerSocket listen;
	/** Listen port */
	public int port;

	/**
	 * Create a daemon thread.  Set up the socket now rather than in run,
	 * so that we get any exception up front.
	 */
	public UnicastThread(int port) throws IOException {
	    super("unicast request");
	    setDaemon(true);
	    if (port == 0) {
		try {
		    listen = new ServerSocket(Constants.discoveryPort);
		    this.port = Constants.discoveryPort;
		    return;
		} catch (IOException e) {
		}
	    }
	    listen = new ServerSocket(port);
	    this.port = listen.getLocalPort();
	}

	/** True if thread has been interrupted */
	private boolean interrupted = false;

	/* This is a workaround for Thread.interrupt not working on
	 * ServerSocket.accept on all platforms.  ServerSocket.close
	 * can't be used as a workaround, because it also doesn't work
	 * on all platforms.
	 */
	public synchronized void interrupt() {
	    interrupted = true;
	    try {
		(new Socket(InetAddress.getLocalHost(), port)).close();
	    } catch (IOException e) {
	    }
	}

	public synchronized boolean isInterrupted() {
	    return interrupted;
	}

	public void run() {
	    while (!isInterrupted()) {
		try {
		    Socket socket = listen.accept();
		    if (isInterrupted()) {
			try {
			    socket.close();
			} catch (IOException e) {
			}
			break;
		    }
		    tasker.add(new SocketTask(socket));
		} catch (InterruptedIOException e) {
		    break;
		} catch (IOException e) {
		} catch (SecurityException e) {
		}
		/* if we fail in any way, just forget about it */
	    }
	    try {
		listen.close();
	    } catch (IOException e) {
	    }
	}
    }

    /** Multicast discovery announcement thread code. */
    private class AnnounceThread extends Thread {
	/** Multicast socket to send packets on */
	private final MulticastSocket socket;

	/**
	 * Create a daemon thread.  Set up the socket now rather than in run,
	 * so that we get any exception up front.
	 */
	public AnnounceThread() throws IOException {
	    super("discovery announcement");
	    setDaemon(true);
	    socket = new MulticastSocket();
	    socket.setTimeToLive(Integer.getInteger("net.jini.discovery.ttl",
						    15).intValue());
	}

	public synchronized void run() {
	    int interval = Integer.getInteger("net.jini.discovery.announce",
					      1000 * 60 * 2).intValue();
	    try {
		while (!isInterrupted() && announce(memberGroups)) {
		    wait(interval);
		}
	    } catch (InterruptedException e) {
	    }
	    if (memberGroups.length > 0)
		announce(new String[0]);//send NO_GROUPS just before shutdown
	    socket.close();
	}

	/**
	 * Announce membership in the specified groups, and return false if
	 * interrupted, otherwise return true.
	 */
	private boolean announce(String[] groups) {
	    try {
		DatagramPacket[] dgrams =
		    OutgoingMulticastAnnouncement.marshal(myServiceID,
							  myLocator,
							  groups);
                try {
                    sendPacketByNIC(socket,dgrams,nicAddresses);
                } catch (InterruptedIOException e) {
                    return false;
                }
	    } catch (InterruptedIOException e) {
		return false;
	    } catch (IOException e) {
	    }
	    return true;
	}
    }

    /**
     * Snapshot-taking thread. 
     * <p>
     * A snapshot is taken when -- after writing a new record to the 
     * log file -- it is determined that the size of the log file has 
     * exceeded a certain threshold. The code which adds the new record 
     * to the log file and which, in turn, decides that a snapshot
     * must be taken is "wrapped" in a writer mutex. That is, synchronization
     * of processing is achieved in the Registrar through a "reader/writer"
     * mutex construct. This construct allows only one writer at any one
     * time; but allows an unlimited number of simultaneous readers as
     * long as no writer has locked the mutex. During steady-state, it is
     * anticipated that there will be far more readers (e.g. lookups) in use 
     * than writers (e.g. add/mod/del Attributes). Since the process of
     * taking a snapshot can be time-consuming, if the whole snapshot-taking
     * process occupies that single writer mutex, then a significant number
     * of read requests will be un-necessarily blocked; possibly resulting
     * in an unacceptable degradation in response time. 
     * <p>
     * It is for the above reason that the process of taking a snapshot is
     * performed in a separate thread. The thread waits on the monitor
     * belonging to the snapshotNotifier instance until it is notified
     * (or "signalled") that a snapshot must be taken. The notification
     * is sent by another thread, created by the Registrar, which
     * determines when the conditions are right for a snapshot. The
     * notification takes the form of an interrupt indicating that the
     * Snapshot monitor is available. Although the interrupt is sent 
     * while the writer mutex is locked, the act of sending the notification
     * is less time-consuming than the act of taking the snapshot itself.
     * When the thread receives a notification, it awakens and requests a
     * lock on the reader mutex (this is all done in the readerWait() method).
     * Because a reader -- not a writer -- mutex is locked, read-only
     * processes still have access to the system state, so lookups can be
     * performed; and the reader mutex prevents changes to the data while
     * the snapshot is in progress.  
     * <p>
     * Note that the current snapshot is guaranteed to complete before the
     * next snapshot request is received. This is because even though
     * the act of taking a snapshot can be viewed as a writer process, 
     * the fact that the next snapshot notification will be wrapped in a
     * writer mutex, combined with the fact that a writer mutex can not
     * be locked while a reader mutex is locked, allows the snapshot to
     * be treated as a reader process.
     */
    private class SnapshotThread extends Thread {
	/** True if thread has been interrupted */
	private boolean interrupted = false;

	/** Create a daemon thread */
	public SnapshotThread() {
	    super("snapshot thread");
	    setDaemon(true);
	}

	public synchronized void interrupt() {
	    interrupted = true;
	    super.interrupt();
	}

	public synchronized boolean isInterrupted() {
	    return interrupted;
	}

	public void run() {
	    try {
		concurrentObj.readLock();
	    } catch (ConcurrentLockException e) {
		return;
	    }
	    try {
		while (!isInterrupted()) {
		    try {
			concurrentObj.readerWait(snapshotNotifier,
						 Long.MAX_VALUE);
                        try {
                            log.snapshot();
		            logFileSize = 0;
	                } catch (Exception e) {
			    if (isInterrupted())
				return;
	                    /* XXX
	                     * if taking the snapshot fails for any reason,
                             * then one of the following must be done:
	                     *   -- output the problem to a file and exit
	                     *   -- output the problem to a file and continue
	                     *   -- set an "I have a problem" attribute and
                             *      then send a notification
	                     * this issue will be addressed at a later time
	                     */
			    e.printStackTrace();
		        }
		    } catch (ConcurrentLockException e) {
			return;
		    }
		}
	    } finally {
		concurrentObj.readUnlock();
	    }
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public ServiceRegistration register(Item nitem, long leaseDuration)
    {
        /* Delay before registering so test can call JoinManager.addAttributes
         * before registration completes. This will reveal the race condition
         * in bug #4671109.
         */
        try { Thread.sleep(10*1000); } catch (InterruptedException e) { }

	concurrentObj.writeLock();
	try {
            ServiceRegistration reg = registerDo(nitem, leaseDuration);
	    return reg;
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public MarshalledObject lookup(Template tmpl)
    {
	concurrentObj.readLock();
	try {
	    return lookupDo(tmpl);
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public Matches lookup(Template tmpl, int maxMatches)
    {
	concurrentObj.readLock();
	try {
	    return lookupDo(tmpl, maxMatches);
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public EventRegistration notify(Template tmpl,
				    int transitions,
				    RemoteEventListener listener,
				    MarshalledObject handback,
				    long leaseDuration)
    {
	concurrentObj.writeLock();
	try {
	    return notifyDo(tmpl, transitions, listener, handback,
			    leaseDuration);
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public EntryClassBase[] getEntryClasses(Template tmpl)
    {
	concurrentObj.readLock();
	try {
	    return getEntryClassesDo(tmpl);
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public Object[] getFieldValues(Template tmpl, int setIndex, int field)
    {
	concurrentObj.readLock();
	try {
	    return getFieldValuesDo(tmpl, setIndex, field);
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public ServiceTypeBase[] getServiceTypes(Template tmpl, String prefix)
    {
	concurrentObj.readLock();
	try {
	    return getServiceTypesDo(tmpl, prefix);
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public ServiceID getServiceID() {
	return myServiceID;
    }

    // This method's javadoc is inherited from an interface of this class
    public LookupLocator getLocator() {
	return myLocator;
    }

    // This method's javadoc is inherited from an interface of this class
    public Object getAdmin() throws RemoteException {
	return (new RegistrarAdminProxy(this, myServiceID));
    }

    // This method's javadoc is inherited from an interface of this class
    public void addAttributes(ServiceID serviceID,
			      long leaseID,
			      EntryRep[] attrSets)
	throws UnknownLeaseException
    {
	if (serviceID.equals(myServiceID))
	    throw new SecurityException("privileged service id");
	concurrentObj.writeLock();
	try {
	    addAttributesDo(serviceID, leaseID, attrSets);
	    addLogRecord(new AttrsAddedLogObj(serviceID, leaseID, attrSets));
	    queueEvents();
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void modifyAttributes(ServiceID serviceID,
				 long leaseID,
				 EntryRep[] attrSetTmpls,
				 EntryRep[] attrSets)
	throws UnknownLeaseException
    {
	if (serviceID.equals(myServiceID))
	    throw new SecurityException("privileged service id");
	concurrentObj.writeLock();
	try {
	    modifyAttributesDo(serviceID, leaseID, attrSetTmpls, attrSets);
	    addLogRecord(new AttrsModifiedLogObj(serviceID, leaseID,
						 attrSetTmpls, attrSets));
	    queueEvents();
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setAttributes(ServiceID serviceID,
			      long leaseID,
			      EntryRep[] attrSets)
	throws UnknownLeaseException
    {
	if (serviceID.equals(myServiceID))
	    throw new SecurityException("privileged service id");
	concurrentObj.writeLock();
	try {
	    setAttributesDo(serviceID, leaseID, attrSets);
	    addLogRecord(new AttrsSetLogObj(serviceID, leaseID, attrSets));
	    queueEvents();
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void cancelServiceLease(ServiceID serviceID, long leaseID)
	throws UnknownLeaseException
    {
	concurrentObj.writeLock();
	try {
	    cancelServiceLeaseDo(serviceID, leaseID);
	    addLogRecord(new ServiceLeaseCancelledLogObj(serviceID, leaseID));
	    queueEvents();
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public long renewServiceLease(ServiceID serviceID,
				  long leaseID,
				  long renewDuration)
	throws UnknownLeaseException
    {
	concurrentObj.priorityWriteLock();
	try {
	    return renewServiceLeaseDo(serviceID, leaseID, renewDuration);
	    /* addLogRecord is in renewServiceLeaseDo */
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void cancelEventLease(long eventID, long leaseID)
	throws UnknownLeaseException
    {
	concurrentObj.writeLock();
	try {
	    cancelEventLeaseDo(eventID, leaseID);
	    addLogRecord(new EventLeaseCancelledLogObj(eventID, leaseID));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public long renewEventLease(long eventID, long leaseID, long renewDuration)
	throws UnknownLeaseException
    {
	concurrentObj.priorityWriteLock();
	try {
	    return renewEventLeaseDo(eventID, leaseID, renewDuration);
	    /* addLogRecord is in renewEventLeaseDo */
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public RenewResults renewLeases(Object[] regIDs,
				    long[] leaseIDs,
				    long[] renewDurations)
    {
	concurrentObj.priorityWriteLock();
	try {
	    return renewLeasesDo(regIDs, leaseIDs, renewDurations);
	    /* addLogRecord is in renewLeasesDo */
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public Exception[] cancelLeases(Object[] regIDs, long[] leaseIDs)
    {
	concurrentObj.writeLock();
	try {
	    Exception[] exceptions = cancelLeasesDo(regIDs, leaseIDs);
	    addLogRecord(new LeasesCancelledLogObj(regIDs, leaseIDs));
	    queueEvents();
	    return exceptions;
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public Entry[] getLookupAttributes() throws RemoteException {
	concurrentObj.readLock();
	try {
	    /* no need to clone, never modified once created */
	    return lookupAttrs;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    joiner.addAttributes(attrSets, true);
	    lookupAttrs = joiner.getAttributes();
	    EntryRep[] attrs = EntryRep.toEntryRep(attrSets, true);
	    try {
		addAttributesDo(myServiceID, 0, attrs);
	    } catch (UnknownLeaseException e) {
	    }
	    addLogRecord(new AttrsAddedLogObj(myServiceID, 0, attrs));
	    queueEvents();
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets)
	throws RemoteException
    {
	concurrentObj.writeLock();
	try {
	    joiner.modifyAttributes(attrSetTemplates, attrSets, true);
	    lookupAttrs = joiner.getAttributes();
	    EntryRep[] tmpls = EntryRep.toEntryRep(attrSetTemplates, false);
	    EntryRep[] attrs = EntryRep.toEntryRep(attrSets, false);
	    try {
		modifyAttributesDo(myServiceID, 0, tmpls, attrs);
	    } catch (UnknownLeaseException e) {
	    }
	    addLogRecord(new AttrsModifiedLogObj(myServiceID, 0,
						 tmpls, attrs));
	    queueEvents();
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public String[] getLookupGroups() throws RemoteException {
	concurrentObj.readLock();
	try {
	    /* no need to clone, never modified once created */
	    return lookupGroups;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupGroups(String[] groups) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    try {
		discoer.addGroups(groups);
	    } catch (IOException e) {
		throw new RuntimeException(e.toString());
	    }
	    lookupGroups = discoer.getGroups();
	    addLogRecord(new LookupGroupsChangedLogObj(lookupGroups));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupGroups(String[] groups) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    discoer.removeGroups(groups);
	    lookupGroups = discoer.getGroups();
	    addLogRecord(new LookupGroupsChangedLogObj(lookupGroups));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupGroups(String[] groups) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    try {
		discoer.setGroups(groups);
	    } catch (IOException e) {
		throw new RuntimeException(e.toString());
	    }
	    lookupGroups = discoer.getGroups();
	    addLogRecord(new LookupGroupsChangedLogObj(lookupGroups));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public LookupLocator[] getLookupLocators() throws RemoteException {
	concurrentObj.readLock();
	try {
	    /* no need to clone, never modified once created */
	    return lookupLocators;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	concurrentObj.writeLock();
	try {
	    discoer.addLocators(locators);
	    lookupLocators = discoer.getLocators();
	    addLogRecord(new LookupLocatorsChangedLogObj(lookupLocators));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	concurrentObj.writeLock();
	try {
	    discoer.removeLocators(locators);
	    lookupLocators = discoer.getLocators();
	    addLogRecord(new LookupLocatorsChangedLogObj(lookupLocators));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	concurrentObj.writeLock();
	try {
	    discoer.setLocators(locators);
	    lookupLocators = discoer.getLocators();
	    addLogRecord(new LookupLocatorsChangedLogObj(lookupLocators));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void addMemberGroups(String[] groups) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    for (int i = 0; i < groups.length; i++) {
	        if (indexOf(memberGroups, groups[i]) < 0)
		    memberGroups = (String[])arrayAdd(memberGroups, groups[i]);
	    }
	    synchronized (announcer) {
		announcer.notify();
	    }
	    addLogRecord(new MemberGroupsChangedLogObj(memberGroups));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeMemberGroups(String[] groups) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    for (int i = 0; i < groups.length; i++) {
                int j = indexOf(memberGroups, groups[i]);
	        if (j >= 0)
		    memberGroups = (String[])arrayDel(memberGroups, j);
	    }
	    synchronized (announcer) {
		announcer.notify();
	    }
	    addLogRecord(new MemberGroupsChangedLogObj(memberGroups));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public String[] getMemberGroups() {
	concurrentObj.readLock();
	try {
	    /* no need to clone, never modified once created */
	    return memberGroups;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setMemberGroups(String[] groups) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    memberGroups = (String[])removeDups(groups);
	    addLogRecord(new MemberGroupsChangedLogObj(memberGroups));
	    synchronized (announcer) {
		announcer.notify();
	    }
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public int getUnicastPort() throws RemoteException {
	concurrentObj.readLock();
	try {
	    return unicastPort;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setUnicastPort(int port) throws IOException,RemoteException {
	if (port == unicastPort)
	    return;
	concurrentObj.writeLock();
	try {
	    if ((port == 0 && unicaster.port == Constants.discoveryPort) ||
		port == unicaster.port)
	    {
		unicastPort = port;
		addLogRecord(new UnicastPortSetLogObj(port));
		return;
	    }
	    /* create a UnicastThread that listens on the new port */
	    UnicastThread newUnicaster = new UnicastThread(port);
	    /* terminate the current UnicastThread listening on the old port */
	    unicaster.interrupt();
	    try {
		unicaster.join();
	    } catch (InterruptedException e) { }
	    /* start the UnicastThread listening on the new port */
	    unicaster = newUnicaster;
	    unicaster.start();
	    unicastPort = port;
	    myLocator = new LookupLocator(myLocator.getHost(), unicaster.port);
	    synchronized (announcer) {
		announcer.notify();
	    }
	    addLogRecord(new UnicastPortSetLogObj(port));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setMinMaxServiceLease(long leaseDuration)
	throws RemoteException
    {
	concurrentObj.writeLock();
	try {
	    if (leaseDuration > MAX_LEASE)
		throw new IllegalArgumentException("max duration exceeded");
	    minMaxServiceLease = leaseDuration;
	    computeMaxLeases();
	    addLogRecord(new MinMaxServiceLeaseSetLogObj(leaseDuration));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public long getMinMaxServiceLease() throws RemoteException {
	concurrentObj.readLock();
	try {
	    return minMaxServiceLease;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setMinMaxEventLease(long leaseDuration)
	throws RemoteException
    {
	concurrentObj.writeLock();
	try {
	    if (leaseDuration > MAX_LEASE)
		throw new IllegalArgumentException("max duration exceeded");
	    minMaxEventLease = leaseDuration;
	    computeMaxLeases();
	    addLogRecord(new MinMaxEventLeaseSetLogObj(leaseDuration));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public long getMinMaxEventLease() throws RemoteException {
	concurrentObj.readLock();
	try {
	    return minMaxEventLease;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setMinRenewalInterval(long interval) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    if (interval > MAX_RENEW)
		throw new IllegalArgumentException("max interval exceeded");
	    minRenewalInterval = interval;
	    computeMaxLeases();
	    addLogRecord(new MinRenewalIntervalSetLogObj(interval));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public long getMinRenewalInterval() throws RemoteException {
	concurrentObj.readLock();
	try {
	    return minRenewalInterval;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setSnapshotWeight(float weight) throws RemoteException {
	concurrentObj.writeLock();
	try {
	    snapshotWt = weight;
	    addLogRecord(new SnapshotWeightSetLogObj(weight));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public float getSnapshotWeight() throws RemoteException {
	concurrentObj.readLock();
	try {
	    return snapshotWt;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLogToSnapshotThreshold(int threshold) throws RemoteException
    {
	concurrentObj.writeLock();
	try {
	    logToSnapshotThresh = threshold;
	    addLogRecord(new LogToSnapshotThresholdSetLogObj(threshold));
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public int getLogToSnapshotThreshold() throws RemoteException {
	concurrentObj.readLock();
	try {
	    return logToSnapshotThresh;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public String getStorageLocation() throws RemoteException {
	concurrentObj.readLock();
	try {
	    return logDirname;
	} finally {
	    concurrentObj.readUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void setStorageLocation(String location)
	throws IOException, RemoteException
    {
	if (location.equals(logDirname)) {
	    return;
	}
	concurrentObj.writeLock();
	try {
 	    ReliableLog newLog = new ReliableLog(location,
						 new LocalLogHandler());
	    newLog.snapshot();
	    ReliableLog oldLog = this.log;
	    this.log = newLog;
	    logDirname = location;
            MarshalledObject data = new MarshalledObject(location);
	    if (activationID != null) {
		try {
//                      if (sharedActivationRef == null) {
		        ActivationSystem sys = ActivationGroup.getSystem();
		        ActivationDesc desc = sys.getActivationDesc
                                                               (activationID);
		        desc = new ActivationDesc(desc.getGroupID(),
					          desc.getClassName(),
					          desc.getLocation(),
					          data,
					          desc.getRestartMode());
		        sys.setActivationDesc(activationID, desc);
//                      } else {//in a shared activation group
//                          sharedActivationRef.setInitializationData(activationID,
//                                                                    data);
//                      }//endif
		} catch (ActivationException e) {
		    throw new LogException("activation system problem", e);
		}
	    }
	    oldLog.deletePersistentStore();
	} finally {
	    concurrentObj.writeUnlock();
	}
    }

    // This method's javadoc is inherited from an interface of this class
    public void destroy() {
	(new DestroyThread()).start();
    }

    /**
     * Return a new array containing the elements of the given array
     * plus the given element added to the end.
     */
    private static Object[] arrayAdd(Object[] array, Object elt) {
	int len = array.length;
	Object[] narray =
	    (Object[])Array.newInstance(array.getClass().getComponentType(),
					len + 1);
	System.arraycopy(array, 0, narray, 0, len);
	narray[len] = elt;
	return narray;
    }

    /**
     * Return a new array containing all the elements of the given array
     * except the one at the specified index.
     */
    private static Object[] arrayDel(Object[] array, int i) {
	int len = array.length - 1;
	Object[] narray =
	    (Object[])Array.newInstance(array.getClass().getComponentType(),
					len);
	System.arraycopy(array, 0, narray, 0, i);
	System.arraycopy(array, i + 1, narray, i, len - i);
	return narray;
    }

    /** Returns the first index of elt in the array, else -1. */
    private static int indexOf(Object[] array, Object elt) {
	return indexOf(array, array.length, elt);
    }

    /** Returns the first index of elt in the array if < len, else -1. */
    private static int indexOf(Object[] array, int len, Object elt) {
	for (int i = 0; i < len; i++) {
	    if (elt.equals(array[i]))
		return i;
	}
	return -1;
    }

    /** Return true if the array is null or zero length */
    private static boolean isEmpty(Object[] array) {
	return (array == null || array.length == 0);
    }

    /** Return true if some object is an element of both arrays */
    private static boolean overlap(Object[] arr1, Object[] arr2) {
	for (int i = arr1.length; --i >= 0; ) {
	    if (indexOf(arr2, arr1[i]) >= 0)
		return true;
	}
	return false;
    }

    /** Test if all elements of the array are null. */
    private static boolean allNull(Object[] array) {
	for (int i = array.length; --i >= 0; ) {
	    if (array[i] != null)
		return false;
	}
	return true;
    }

    /** Weed out duplicates. */
    private static Object[] removeDups(Object[] arr) {
	for (int i = arr.length; --i >= 0; ) {
	    if (indexOf(arr, i, arr[i]) >= 0)
		arr = arrayDel(arr, i);
	}
	return arr;
    }

    /** Delete item.attributeSets[i] and return the new array. */
    private static EntryRep[] deleteSet(Item item, int i) {
	item.attributeSets = (EntryRep[])arrayDel(item.attributeSets, i);
	return item.attributeSets;
    }

    /**
     * Do a deep copy of the item, and substitute replacements for all
     * embedded EntryClass instances and null for the ServiceType and
     * codebase (since they aren't needed on the client side).
     */
    private static Item copyItem(Item item) {
	item = (Item)item.clone();
	item.serviceType = null;
	item.codebase = null;
	EntryRep[] attrSets = item.attributeSets;
	for (int i = attrSets.length; --i >= 0; ) {
	    attrSets[i].eclass = attrSets[i].eclass.getReplacement();
	}
	return item;
    }

    /**
     * Return the first (highest) class that defines the given field.  This
     * would be a method on EntryClass, but we want to minimize code
     * downloaded into the client.
     */
    private static EntryClass getDefiningClass(EntryClass eclass, int fldidx) {
	while (true) {
	    EntryClass sup = eclass.getSuperclass();
	    if (sup.getNumFields() <= fldidx)
		return eclass;
	    eclass = sup;
	}
    }

    /**
     * Add conc as a concrete class of the given type and all supertypes,
     * if it isn't already there.  (It might already be there due to
     * diamond merges in the interface hierarchy.)  This would be a method on
     * ServiceType, but we want to minimize code downloaded into the client.
     */
    private static void addConcreteClass(ServiceType type, ServiceType conc) {
	ServiceType[] types = type.getConcreteClasses();
	if (indexOf(types, conc) >= 0)
	    return;
	type.setConcreteClasses((ServiceType[])arrayAdd(types, conc));
	ServiceType[] ifaces = type.getInterfaces();
	for (int i = ifaces.length; --i >= 0; ) {
	    addConcreteClass(ifaces[i], conc);
	}
	ServiceType sup = type.getSuperclass();
	if (sup != null)
	    addConcreteClass(sup, conc);
    }

    /**
     * Delete conc as a concrete class of the given type and all supertypes,
     * if it's there.  (It might already be delete due to diamond merges
     * in the interface hierarchy.)  This would be a method on ServiceType,
     * but we want to minimize code downloaded into the client.
     */
    private static void deleteConcreteClass(ServiceType type, ServiceType conc)
    {
	ServiceType[] types = type.getConcreteClasses();
	int i = indexOf(types, conc);
	if (i >= 0) {
	    type.setConcreteClasses((ServiceType[])arrayDel(types, i));
	    ServiceType[] ifaces = type.getInterfaces();
	    for (int j = ifaces.length; --j >= 0; ) {
		deleteConcreteClass(ifaces[j], conc);
	    }
	    ServiceType sup = type.getSuperclass();
	    if (sup != null)
		deleteConcreteClass(sup, conc);
	}
    }

    /**
     * Test if an item matches a template.  This would be a method on
     * Template, but we want to minimize code downloaded into the client.
     */
    private static boolean matchItem(Template tmpl, Item item) {
	return ((tmpl.serviceID == null ||
		 tmpl.serviceID.equals(item.serviceID)) &&
		matchType(tmpl.serviceTypes, item.serviceType) &&
		matchAttributes(tmpl, item));
    }

    /** Test if a type is equal to or a subtype of every type in an array. */
    private static boolean matchType(ServiceType[] types, ServiceType type) {
	if (types != null) {
	    for (int i = types.length; --i >= 0; ) {
		if (!types[i].isAssignableFrom(type))
		    return false;
	    }
	}
	return true;
    }

    /**
     * Test if an entry matches a template.  This would be a method on
     * EntryRep, but we want to minimize code downloaded into the client.
     */
    private static boolean matchEntry(EntryRep tmpl, EntryRep entry) {
	if (!tmpl.eclass.isAssignableFrom(entry.eclass) ||
	    tmpl.fields.length > entry.fields.length)
	    return false;
	for (int i = tmpl.fields.length; --i >= 0; ) {
	    if (tmpl.fields[i] != null &&
		!tmpl.fields[i].equals(entry.fields[i]))
		return false;
	}
	return true;
    }

    /**
     * Test if there is at least one matching entry in the Item for
     * each entry template in the Template.
     */
    private static boolean matchAttributes(Template tmpl, Item item) {
	EntryRep[] tmpls = tmpl.attributeSetTemplates;
	if (tmpls != null) {
	    EntryRep[] entries = item.attributeSets;
	outer:
	    for (int i = tmpls.length; --i >= 0; ) {
		EntryRep etmpl = tmpls[i];
		for (int j = entries.length; --j >= 0; ) {
		    if (matchEntry(etmpl, entries[j]))
			continue outer;
		}
		return false;
	    }
	}
	return true;
    }

    /**
     * Test if an entry either doesn't match any template in an array,
     * or matches a template but is a subclass of the template type.
     */
    private static boolean attrMatch(EntryRep[] tmpls, EntryRep attrSet) {
	boolean good = true;
	if (tmpls != null) {
	    for (int i = tmpls.length; --i >= 0; ) {
		EntryRep tmpl = tmpls[i];
		if (matchEntry(tmpl, attrSet)) {
		    if (tmpl.eclass.isAssignableFrom(attrSet.eclass) &&
			!tmpl.eclass.equals(attrSet.eclass))
			return true;
		    good = false;
		}
	    }
	}
	return good;
    }

    /**
     * Test if the service has an entry of the given class or subclass
     * with a field of the given value.
     */
    private static boolean hasAttr(SvcReg reg,
				   EntryClass eclass,
				   int fldidx,
				   Object value)
    {
	EntryRep[] sets = reg.item.attributeSets;
	for (int i = sets.length; --i >= 0; ) {
	    EntryRep set = sets[i];
	    if (eclass.isAssignableFrom(set.eclass) &&
		((value == null && set.fields[fldidx] == null) ||
		 (value != null && value.equals(set.fields[fldidx]))))
		return true;
	}
	return false;
    }

    /**
     * Test if the service has an entry of the exact given class (assumed
     * to have no fields).
     */
    private static boolean hasEmptyAttr(SvcReg reg, EntryClass eclass) {
	EntryRep[] sets = reg.item.attributeSets;
	for (int i = sets.length; --i >= 0; ) {
	    if (eclass.equals(sets[i].eclass))
		return true;
	}
	return false;
    }

    /**
     * Find the most specific types (of type) that don't match prefix and
     * aren't equal to or a supertype of any types in bases, and add them
     * to types.
     */
    private static void addTypes(ArrayList types,
				 ArrayList codebases,
				 ServiceType[] bases,
				 String prefix,
				 ServiceType type,
				 String codebase)
    {
	if (types.contains(type))
	    return;
	if (bases != null) {
	    for (int i = bases.length; --i >= 0; ) {
		if (type.isAssignableFrom(bases[i]))
		    return;
	    }
	}
	if (prefix == null || type.getName().startsWith(prefix)) {
	    types.add(type);
	    codebases.add(codebase);
	    return;
	}
	ServiceType[] ifs = type.getInterfaces();
	for (int i = ifs.length; --i >= 0; ) {
	    addTypes(types, codebases, bases, prefix, ifs[i], codebase);
	}
	ServiceType sup = type.getSuperclass();
	if (sup != null)
	    addTypes(types, codebases, bases, prefix, sup, codebase);
    }

    /** Limit leaseDuration by limit, and check for negative value. */
    private static long limitDuration(long leaseDuration, long limit) {
	if (leaseDuration == Lease.ANY || leaseDuration > limit)
	    leaseDuration = limit;
	else if (leaseDuration < 0)
	    throw new IllegalArgumentException("negative lease duration");
	return leaseDuration;
    }

    /**
     * Sends the given packet data on the given <code>MulticastSocket</code>
     * through each of the network interfaces corresponding to elements of
     * the given array of IP addresses. If the given array of IP addresses
     * is <code>null</code> or empty, then the data will be sent through only
     * the default network interface.
     *
     * @param mcSocket   the <code>MulticastSocket</code> on which the data
     *                   will be sent
     * @param packet     <code>DatagramPacket</code> array whose elements are
     *                   the data to send 
     * @param addresses  <code>InetAddress</code> array whose elements
     *                   represent the Internet Protocol (IP) addresses
     *                   corresponding to the network interfaces (NICs)
     *                   through which the multicast data should be sent
     *
     * @throws java.io.InterruptedIOException
     */
    private static void sendPacketByNIC(MulticastSocket mcSocket,
                                        DatagramPacket[] packet,
                                        InetAddress[] addresses)
                                                 throws InterruptedIOException
    {
        if( (addresses != null) && (addresses.length > 0) ) {
            for(int i=0;i<addresses.length;i++) {
                try {
                    mcSocket.setInterface(addresses[i]);
                } catch(SocketException e) {
                    continue;//skip to next interface address
                }
                sendPacket(mcSocket,packet);
            }//end loop
        } else {//use default interface
            sendPacket(mcSocket,packet);
        }//endif
    }//end sendPacketByNIC

    /**
     * Sends the given packet data on the given <code>MulticastSocket</code>
     * through the network interface that is currently set.
     *
     * @param mcSocket the <code>MulticastSocket</code> on which the data
     *                 will be sent
     * @param packet   <code>DatagramPacket</code> array whose elements are 
     *                 the data to send 
     *
     * @throws java.io.InterruptedIOException
     */
    private static void sendPacket(MulticastSocket mcSocket,
                                   DatagramPacket[] packet)
                                                throws InterruptedIOException
    {
        for(int i=0;i<packet.length;i++) {
            try {
                mcSocket.send(packet[i]);
            } catch(InterruptedIOException e) {
                throw e;
            } catch(IOException e) {
            }
        }//end loop
    }//end sendPacket

    /**
     * Retrieves and parses the <code>-Dnet.jini.discovery.interface</code>
     * system property, converting each parsed value to an instance of
     * <code>InetAddress</code>, and returning the results of each conversion
     * in an array.
     *
     * @return <code>InetAddress</code> array in which each element represents
     *         the Internet Protocol (IP) address corresponding to a network
     *         interface.
     *
     * @throws java.net.UnknownHostException
     */
    private static InetAddress[] getNICAddresses() throws UnknownHostException
    {
        String str = null;
	try {
	    str = System.getProperty("net.jini.discovery.interface");
	} catch (SecurityException e) { /* ignore */ }
        if (str == null) return null;
        InetAddress[] addrs = null;
        String delimiter = ",";
        StringTokenizer st = null;
        st = new StringTokenizer(str,delimiter);
        int n = st.countTokens();
        if (n > 0) {
            addrs = new InetAddress[n];
            for(int i=0;((st.hasMoreTokens())&&(i<n));i++) {
                addrs[i] = InetAddress.getByName(st.nextToken());
            }
            return addrs;
        } else {
            return addrs;
        }
    }//end getNICAddresses

    /**
     * Add a service to our state.  This includes putting it in the
     * serviceByID map under the serviceID, in the serviceByTime map,
     * in the serviceByType map under the service's most-specific
     * ServiceType, and in the serviceByAttr map under all of its
     * attribute values, incrementing the number of instances of each
     * EntryClass, and updating entryClasses as necessary.  If this is
     * the first instance of that ServiceType registered, then we need
     * to add concrete class information to the type and all supertypes.
     */
    private void addService(SvcReg reg) {
	serviceByID.put(reg.item.serviceID, reg);
	serviceByTime.put(reg, reg);
	ServiceType type = reg.item.serviceType;
	ArrayList regs = (ArrayList)serviceByType.get(type);
	if (regs == null) {
	    regs = new ArrayList(3);
	    serviceByType.put(type, regs);
	    addConcreteClass(type, type);
	}
	regs.add(reg);
	EntryRep[] entries = reg.item.attributeSets;
	for (int i = entries.length; --i >= 0; ) {
	    addAttrs(reg, entries[i]);
	}
	computeMaxLeases();
    }

    /**
     * Delete given service, generating events as necessary.  This includes
     * deleting from the serviceByID, serviceByTime, serviceByType, and
     * serviceByAttr maps, decrementing the number of instances of each
     * EntryClass, and updating entryClasses as necessary.  If this is the
     * last registered instance of the service type, then we delete the
     * concrete class information from the type and all supertypes.
     */
    private void deleteService(SvcReg reg, long now) {
	Item item = reg.item;
	generateEvents(item, null, now);
	serviceByID.remove(item.serviceID);
	serviceByTime.remove(reg);
	ServiceType type = item.serviceType;
	ArrayList regs = (ArrayList)serviceByType.get(type);
	regs.remove(regs.indexOf(reg));
	if (regs.isEmpty()) {
	    serviceByType.remove(type);
	    deleteConcreteClass(type, type);
	}
	EntryRep[] entries = item.attributeSets;
	for (int i = entries.length; --i >= 0; ) {
	    deleteAttrs(reg, entries[i], false);
	}
	computeMaxLeases();
    }

    /**
     * Add an event registration to our state.  This includes adding a
     * template of each EntryClass, putting the registration in the
     * eventByID map, in the eventByTime map, and in either
     * subEventByService (if the template is for a specific service id)
     * or subEventByID.  Since we expect in most cases there will only
     * ever be a single event registration for a given service id, we
     * avoid creating a singleton array in that case.
     */
    private void addEvent(EventReg reg) {
	if (reg.listener == null)
	    return; /* failed to recover from log */
	EntryRep[] tmpls = reg.tmpl.attributeSetTemplates;
	if (tmpls != null) {
	    for (int i = tmpls.length; --i >= 0; ) {
		EntryClass eclass = tmpls[i].eclass;
		eclass.setNumTemplates(eclass.getNumTemplates() + 1);
	    }
	}
	Long id = new Long(reg.eventID);
	eventByID.put(id, reg);
	eventByTime.put(reg, reg);
	if (reg.tmpl.serviceID != null) {
	    Object val = subEventByService.get(reg.tmpl.serviceID);
	    if (val == null)
		val = reg;
	    else if (val instanceof EventReg)
		val = new EventReg[]{(EventReg)val, reg};
	    else
		val = arrayAdd((EventReg[])val, reg);
	    subEventByService.put(reg.tmpl.serviceID, val);
	} else {
	    subEventByID.put(id, reg);
	}
	computeMaxLeases();
    }

    /**
     * Remove an event registration from our state.  This includes deleting
     * a template of each EntryClass, deleting the registration from the
     * eventByID map, the eventByTime map, and either the subEventByService
     * or subEventByID map.
     */
    private void deleteEvent(EventReg reg) {
	EntryRep[] tmpls = reg.tmpl.attributeSetTemplates;
	if (tmpls != null) {
	    for (int i = tmpls.length; --i >= 0; ) {
		EntryClass eclass = tmpls[i].eclass;
		eclass.setNumTemplates(eclass.getNumTemplates() - 1);
	    }
	}
	Long id = new Long(reg.eventID);
	eventByID.remove(id);
	eventByTime.remove(reg);
	if (reg.tmpl.serviceID != null) {
	    Object val = subEventByService.get(reg.tmpl.serviceID);
	    if (val == reg) {
		subEventByService.remove(reg.tmpl.serviceID);
	    } else {
		Object[] array = (EventReg[])val;
		array = arrayDel(array, indexOf(array, reg));
		if (array.length == 1)
		    val = array[0];
		else
		    val = array;
		subEventByService.put(reg.tmpl.serviceID, val);
	    }
	} else {
	    subEventByID.remove(id);
	}
	computeMaxLeases();
    }

    /**
     * Put the service in the serviceByAttr map under all attribute values
     * in the given entry, or in the serviceByEmptyAttr map if the entry
     * has no attributes, add a new instance of the EntryClass, and update
     * entryClasses as necessary.
     */
    private void addAttrs(SvcReg reg, EntryRep entry) {
	EntryClass eclass = entry.eclass;
	addInstance(eclass);
	Object[] fields = entry.fields;
	if (fields.length > 0) {
	    /* walk backwards to make getDefiningClass more efficient */
	    for (int i = fields.length; --i >= 0; ) {
		eclass = getDefiningClass(eclass, i);
		addAttr(reg, eclass, i, fields[i]);
	    }
	    return;
	}
	ArrayList regs = (ArrayList)serviceByEmptyAttr.get(eclass);
	if (regs == null) {
	    regs = new ArrayList(2);
	    regs.add(reg);
	    serviceByEmptyAttr.put(eclass, regs);
	} else if (!regs.contains(reg)) {
	    regs.add(reg);
	}
    }

    /**
     * If checkDups is false, delete the service (if present) from
     * serviceByAttr under all attribute values of the given entry or
     * from serviceByEmptyAttr if the entry has no attributes.
     * If checkDups is true, only delete for a given attribute value if the
     * service has no other entries of similar type that still have the
     * same value.  Either way, delete an instance of the EntryClass,
     * and update entryClasses as necessary.
     */
    private void deleteAttrs(SvcReg reg, EntryRep entry, boolean checkDups) {
	EntryClass eclass = entry.eclass;
	deleteInstance(eclass);
	Object[] fields = entry.fields;
	if (fields.length == 0) {
	    ArrayList regs = (ArrayList)serviceByEmptyAttr.get(eclass);
	    if (regs == null || (checkDups && hasEmptyAttr(reg, eclass)))
		return;
	    int idx = regs.indexOf(reg);
	    if (idx >= 0) {
		regs.remove(idx);
		if (regs.isEmpty())
		    serviceByEmptyAttr.remove(eclass);
	    }
	    return;
	}
	/* walk backwards to make getDefiningClass more efficient */
	for (int fldidx = fields.length; --fldidx >= 0; ) {
	    eclass = getDefiningClass(eclass, fldidx);
	    HashMap[] attrMaps = (HashMap[])serviceByAttr.get(eclass);
	    if (attrMaps == null ||
		attrMaps[fldidx] == null ||
		(checkDups && hasAttr(reg, eclass, fldidx, fields[fldidx])))
		continue;
	    HashMap map = attrMaps[fldidx];
	    Object value = fields[fldidx];
	    ArrayList regs = (ArrayList)map.get(value);
	    if (regs == null)
		continue;
	    int idx = regs.indexOf(reg);
	    if (idx < 0)
		continue;
	    regs.remove(idx);
	    if (!regs.isEmpty())
		continue;
	    map.remove(value);
	    if (!map.isEmpty())
		continue;
	    attrMaps[fldidx] = null;
	    if (allNull(attrMaps))
		serviceByAttr.remove(eclass);
	}
    }

    /**
     * Store all non-null elements of values into the given entry,
     * and update serviceByAttr to match.
     */
    private void updateAttrs(SvcReg reg, EntryRep entry, Object[] values)
    {
	EntryClass eclass = entry.eclass;
	/* walk backwards to make getDefiningClass more efficient */
	for (int fldidx = values.length; --fldidx >= 0; ) {
	    Object oval = entry.fields[fldidx];
	    Object nval = values[fldidx];
	    if (nval != null && !nval.equals(oval)) {
		eclass = getDefiningClass(eclass, fldidx);
		HashMap map = addAttr(reg, eclass, fldidx, nval);
		entry.fields[fldidx] = nval;
		if (hasAttr(reg, eclass, fldidx, oval))
		    continue;
		ArrayList regs = (ArrayList)map.get(oval);
		regs.remove(regs.indexOf(reg));
		if (regs.isEmpty())
		    map.remove(oval); /* map cannot become empty */
	    }
	}
    }

    /**
     * Put the service under the given attribute value for the given
     * defining class and field, if it isn't already there.  Return
     * the HashMap for the given class and field.
     */
    private HashMap addAttr(SvcReg reg,
			    EntryClass eclass,
			    int fldidx,
			    Object value)
    {
	HashMap[] attrMaps = (HashMap[])serviceByAttr.get(eclass);
	if (attrMaps == null) {
	    attrMaps = new HashMap[eclass.getNumFields()];
	    serviceByAttr.put(eclass, attrMaps);
	}
	HashMap map = attrMaps[fldidx];
	if (map == null) {
	    map = new HashMap(11);
	    attrMaps[fldidx] = map;
	}
	ArrayList regs = (ArrayList)map.get(value);
	if (regs == null) {
	    regs = new ArrayList(3);
	    map.put(value, regs);
	} else if (regs.contains(reg))
	    return map;
	regs.add(reg);
	return map;
    }

    /**
     * Add an instance of the EntryClass, and add the class to entryClasses
     * if this is the first such instance.
     */
    private void addInstance(EntryClass eclass) {
	int num = eclass.getNumInstances();
	if (num == 0)
	    entryClasses.add(eclass);
	eclass.setNumInstances(num + 1);
    }

    /**
     * Delete an instance of the EntryClass, and remove the class from
     * entryClasses if this is the last such instance.
     */
    private void deleteInstance(EntryClass eclass) {
	int num = eclass.getNumInstances() - 1;
	if (num == 0)
	    entryClasses.remove(entryClasses.indexOf(eclass));
	eclass.setNumInstances(num);
    }

    /** Return an appropriate iterator for Items matching the Template. */
    private ItemIter matchingItems(Template tmpl) {
	if (tmpl.serviceID != null)
	    return new IDItemIter(tmpl);
	if (!isEmpty(tmpl.serviceTypes))
	    return new TypeItemIter(tmpl);
	EntryRep[] sets = tmpl.attributeSetTemplates;
	if (isEmpty(sets))
	    return new AllItemIter();
	for (int i = sets.length; --i >= 0; ) {
	    Object[] fields = sets[i].fields;
	    if (fields.length == 0) {
		EntryClass eclass = getEmptyEntryClass(sets[i].eclass);
		if (eclass != null)
		    return new EmptyAttrItemIter(tmpl, eclass);
	    } else {
		/* try subclass fields before superclass fields */
		for (int j = fields.length; --j >= 0; ) {
		    if (fields[j] != null)
			return new AttrItemIter(tmpl, i, j);
		}
	    }
	}
	return new ClassItemIter(tmpl);
    }

    /**
     * Return member of entryClasses that is equal to or a subclass of
     * the specified class, provided there is exactly one such member
     * and it has no fields.
     */
    private EntryClass getEmptyEntryClass(EntryClass eclass) {
	EntryClass match = null;
	for (int i = entryClasses.size(); --i >= 0; ) {
	    EntryClass cand = (EntryClass)entryClasses.get(i);
	    if (eclass.isAssignableFrom(cand)) {
		if (cand.getNumFields() != 0 || match != null)
		    return null;
		match = cand;
	    }
	}
	return match;
    }

    /**
     * Returns all concrete classes that are equal to or subclasses of
     * all of the types in an array.
     */
    private ServiceType[] matchingConcreteClasses(ServiceType[] types) {
	if (isEmpty(types))
	    return objectServiceType.getConcreteClasses();
	ServiceType[] stypes = types[0].getConcreteClasses();
	if (types.length > 1) {
	    for (int i = stypes.length; --i >= 0; ) {
		if (!matchType(types, stypes[i]))
		    stypes = (ServiceType[])arrayDel(stypes, i);
	    }
	}
	return stypes;
    }

    /** Return any valid codebase for an entry class that has instances. */
    private String pickCodebase(EntryClass eclass, long now)
	throws ClassNotFoundException
    {
	if (eclass.getNumFields() == 0)
	    return pickCodebase(eclass,
				(ArrayList)serviceByEmptyAttr.get(eclass),
				now);
	int fldidx = eclass.getNumFields() - 1;
	HashMap[] attrMaps =
	    (HashMap[])serviceByAttr.get(getDefiningClass(eclass, fldidx));
	for (Iterator iter = attrMaps[fldidx].values().iterator();
	     iter.hasNext(); )
	{
	    try {
		return pickCodebase(eclass, (ArrayList)iter.next(), now);
	    } catch (ClassNotFoundException e) {
	    }
	}
	throw new ClassNotFoundException();
    }

    /** Return any valid codebase for an entry of the exact given class. */
    private String pickCodebase(EntryClass eclass, ArrayList svcs, long now)
	throws ClassNotFoundException
    {
	for (int i = svcs.size(); --i >= 0; ) {
	    SvcReg reg = (SvcReg)svcs.get(i);
	    if (reg.leaseExpiration <= now)
		continue;
	    EntryRep[] sets = reg.item.attributeSets;
	    for (int j = sets.length; --j >= 0; ) {
		if (eclass.equals(sets[j].eclass))
		    return sets[j].codebase;
	    }
	}
	throw new ClassNotFoundException();
    }

    /** Return any valid codebase for a concrete service type. */
    private String pickCodebase(ServiceType stype, long now)
	throws ClassNotFoundException
    {
	ArrayList svcs = (ArrayList)serviceByType.get(stype);
	for (int i = svcs.size(); --i >= 0; ) {
	    SvcReg reg = (SvcReg)svcs.get(i);
	    if (reg.leaseExpiration > now)
		return reg.item.codebase;
	}
	throw new ClassNotFoundException();
    }

    /**
     * Compute new maxServiceLease and maxEventLease values.  This needs to
     * be called whenever the number of services (#S) or number of events
     * (#E) changes, or whenever any of the configuration parameters change.
     * The two base equations driving the computation are:
     *     #S/maxServiceLease + #E/maxEventLease <= 1/minRenewalInterval
     *     maxServiceLease/maxEventLease = minMaxServiceLease/minMaxEventLease
     */
    private void computeMaxLeases() {
	if (inRecovery)
	    return;
	maxServiceLease =
	    Math.max(minMaxServiceLease,
		     minRenewalInterval *
		     (serviceByID.size() +
		      ((eventByID.size() * minMaxServiceLease) /
		       minMaxEventLease)));
	maxEventLease = Math.max(minMaxEventLease,
				 ((maxServiceLease * minMaxEventLease) /
				  minMaxServiceLease));
    }

    /** Process a unicast discovery request, and respond. */
    private void respond(Socket socket) {
	try {
	    try {
		socket.setSoTimeout(unicastTimeout);
		IncomingUnicastRequest req = 
		    new IncomingUnicastRequest(socket.getInputStream());
		OutgoingUnicastResponse.marshal(socket.getOutputStream(),
						proxy, memberGroups);
	    } finally {
		socket.close();
	    }
	} catch (IOException e) {
	}
    }

    /** Close any sockets that were sitting in the task queue. */
    private void closeRequestSockets(ArrayList tasks) {
	for (int i = tasks.size(); --i >= 0; ) {
	    Object obj = tasks.get(i);
	    if (obj instanceof SocketTask) {
		try {
		    ((SocketTask)obj).socket.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /** Initialization common to both activatable and transient instances. */
    private void init() throws IOException {
	/* have to create these here because of checked exceptions */
	objectServiceType = resolver.resolve(new ServiceType(Object.class,
							     null, null));
	String host = System.getProperty("java.rmi.server.hostname");
	if (host == null)
	    host = InetAddress.getLocalHost().getHostName();
	log = new ReliableLog(logDirname, new LocalLogHandler());
	inRecovery = true;
	resolver.setInRecovery(true);
	log.recover();
	resolver.setInRecovery(false);
	inRecovery = false;
	computeMaxLeases();
        nicAddresses = getNICAddresses();
	unicaster = new UnicastThread(unicastPort);
	myLocator = new LookupLocator(host, unicaster.port);
	multicaster = new MulticastThread();
	announcer = new AnnounceThread();
	if (myServiceID == null)
	    myServiceID = newServiceID();
	proxy = new RegistrarProxy((Registrar)RemoteObject.toStub(this),
				   myServiceID);
	if (serviceByID.isEmpty()) {
	    /* register myself */
	    lookupAttrs = new Entry[]{new ServiceInfo(PRODUCT, MANUFACTURER,
						      VENDOR, VERSION,
						      "", ""),
				      new BasicServiceType("Lookup")};
	    Item item = new Item(new ServiceItem(myServiceID,
						 proxy,
						 lookupAttrs));
	    resolver.resolve(item);
	    SvcReg reg = new SvcReg(item, 0, Long.MAX_VALUE);
	    addService(reg);
	} else {
	    SvcReg reg = (SvcReg)serviceByID.get(myServiceID);
	    lookupAttrs = EntryRep.toEntry(reg.item.attributeSets);
	    /* delete whatever we can't reconstitute */
	    for (int i = lookupAttrs.length; --i >= 0; ) {
		if (lookupAttrs[i] == null) {
		    lookupAttrs = (Entry[])arrayDel(lookupAttrs, i);
		    EntryRep set = reg.item.attributeSets[i];
		    deleteSet(reg.item, i);
		    deleteAttrs(reg, set, true);
		}
	    }
	}
	log.snapshot();
        discoer = null;
        try {
	discoer = new LookupDiscoveryManager(lookupGroups, lookupLocators,
					     null);
        } catch (ConfigurationException e) {
            throw new IOException("LookupDiscoveryManager constructor threw exeption", e);
        }
	joiner = new JoinManager(proxy, lookupAttrs, myServiceID,
				 (DiscoveryListenerManagement) discoer, null);
	/* start up all the daemon threads */
	serviceExpirer.start();
	eventExpirer.start();
	unicaster.start();
	multicaster.start();
	announcer.start();
        snapshotter.start();
    }

    /** The code that does the real work of register. */
    private ServiceRegistration registerDo(Item nitem, long leaseDuration)
    {
	resolver.resolve(nitem);
	if (nitem.service == null)
	    throw new NullPointerException("null service");
	if (myServiceID.equals(nitem.serviceID))
	    throw new IllegalArgumentException("reserved service id");
	if (nitem.attributeSets == null)
	    nitem.attributeSets = emptyAttrs;
	else
	    nitem.attributeSets = (EntryRep[])removeDups(nitem.attributeSets);
	leaseDuration = limitDuration(leaseDuration, maxServiceLease);
	long now = System.currentTimeMillis();
	if (nitem.serviceID == null) {
	    /* new service, match on service object */
	    ArrayList svcs = (ArrayList)serviceByType.get(nitem.serviceType);
	    if (svcs != null) {
		for (int i = svcs.size(); --i >= 0; ) {
		    SvcReg reg = (SvcReg)svcs.get(i);
		    if (nitem.service.equals(reg.item.service)) {
			nitem.serviceID = reg.item.serviceID;
			deleteService(reg, now);
			break;
		    }
		}
	    }
	    if (nitem.serviceID == null)
		nitem.serviceID = newServiceID();
	} else {
	    /* existing service, match on service id */
	    SvcReg reg = (SvcReg)serviceByID.get(nitem.serviceID);
	    if (reg != null)
		deleteService(reg, now);
	}
	SvcReg reg = new SvcReg(nitem, newLeaseID(), now + leaseDuration);
	addService(reg);
	generateEvents(null, nitem, now);
	addLogRecord(new SvcRegisteredLogObj(reg));
	queueEvents();
	/* see if the expire thread needs to wake up earlier */
	if (reg.leaseExpiration < minSvcExpiration) {
	    minSvcExpiration = reg.leaseExpiration;
	    concurrentObj.waiterNotify(serviceNotifier);
	}
	return new Registration(this, new ServiceLease(this,
						       nitem.serviceID,
						       reg.leaseID,
						       reg.leaseExpiration));
    }

   /**
    * The code that does the real work of lookup.  As a special case,
    * if the template specifies at least one service type to match,
    * and there are multiple items that match the template, then we
    * make a random pick among them, in an attempt to load balance
    * use of "equivalent" services and avoid starving any of them.
    */
    private MarshalledObject lookupDo(Template tmpl)
    {
	resolver.resolve(tmpl);
	if (isEmpty(tmpl.serviceTypes) || tmpl.serviceID != null)
	{
	    ItemIter iter = matchingItems(tmpl);
	    if (iter.hasNext())
		return iter.next().service;
	    return null;
	}
	ServiceType[] types = matchingConcreteClasses(tmpl.serviceTypes);
	long now = System.currentTimeMillis();
	int tlen = types.length;
	if (tlen == 0)
	    return null;
	int trand = Math.abs(random.nextInt()) % tlen;
	for (int i = 0; i < tlen; i++) {
	    ArrayList svcs = (ArrayList)serviceByType.get(
						  types[(i + trand) % tlen]);
	    int slen = svcs.size();
	    int srand = Math.abs(random.nextInt()) % slen;
	    for (int j = 0; j < slen; j++) {
		SvcReg reg = (SvcReg)svcs.get((j + srand) % slen);
		if (reg.leaseExpiration > now &&
		    matchAttributes(tmpl, reg.item))
		    return reg.item.service;
	    }
	}
	return null;
    }

    /**
     * The code that does the real work of lookup.  We do a deep copy of the
     * items being returned, both to avoid having them modified while being
     * marshalled (by a concurrent update method), and to substitute
     * replacements for embedded EntryClass and ServiceType instances, to
     * minimize data sent back to the client.  If duplicates are possible
     * from the iterator, we save all matches, weeding out duplicates as we
     * go, then trim to maxMatches and deep copy.
     */
    private Matches lookupDo(Template tmpl, int maxMatches)
    {
	if (maxMatches < 0)
	    throw new IllegalArgumentException("negative maxMatches");
	resolver.resolve(tmpl);
	int totalMatches = 0;
	ArrayList matches = null;
	ItemIter iter = matchingItems(tmpl);
	if (maxMatches > 0 || iter.dupsPossible)
	    matches = new ArrayList();
	if (iter.dupsPossible) {
	    while (iter.hasNext()) {
		Item item = iter.next();
		if (!matches.contains(item))
		    matches.add(item);
	    }
	    totalMatches = matches.size();
	    if (maxMatches > 0) {
		for (int i = matches.size(); --i >= maxMatches; )
		    matches.remove(i);
		for (int i = matches.size(); --i >= 0; ) {
		    matches.set(i, copyItem((Item)matches.get(i)));
		}
	    } else {
		matches = null;
	    }
	} else {
	    while (iter.hasNext()) {
		Item item = iter.next();
		totalMatches++;
		if (--maxMatches >= 0)
		    matches.add(copyItem(item));
	    }
	}
	return new Matches(matches, totalMatches);
    }

    /**
     * The code that does the real work of notify.
     * Every registration is given a unique event id.  The event id
     * can thus also serve as a lease id.
     *
     */
    private EventRegistration notifyDo(Template tmpl,
				       int transitions,
				       RemoteEventListener listener,
				       MarshalledObject handback,
				       long leaseDuration)
    {
	resolver.resolve(tmpl);
	if (transitions == 0 ||
	    transitions !=
	    (transitions & (ServiceRegistrar.TRANSITION_MATCH_NOMATCH |
			    ServiceRegistrar.TRANSITION_NOMATCH_MATCH |
			    ServiceRegistrar.TRANSITION_MATCH_MATCH)))
	    throw new IllegalArgumentException("invalid transitions");
	if (listener == null)
	    throw new NullPointerException("listener");
	leaseDuration = limitDuration(leaseDuration, maxEventLease);
	long now = System.currentTimeMillis();
	EventReg reg = new EventReg(eventID, newLeaseID(), tmpl, transitions,
				    listener, handback, now + leaseDuration);
	eventID++;
	addEvent(reg);
	addLogRecord(new EventRegisteredLogObj(reg));
	/* see if the expire thread needs to wake up earlier */
	if (reg.leaseExpiration < minEventExpiration) {
	    minEventExpiration = reg.leaseExpiration;
	    concurrentObj.waiterNotify(eventNotifier);
	}
	return new EventRegistration(reg.eventID, proxy,
				     new EventLease(this, reg.eventID,
						    reg.leaseID,
						    reg.leaseExpiration),
				     reg.seqNo);
    }

    /**
     * The code that does the real work of getEntryClasses. If the
     * template is empty, then we can just use entryClasses, without
     * having to iterate over items, but we have to work harder to
     * get codebases.
     */
    private EntryClassBase[] getEntryClassesDo(Template tmpl)
    {
	resolver.resolve(tmpl);
	ArrayList classes = new ArrayList();
	ArrayList codebases = new ArrayList();
	if (tmpl.serviceID == null &&
	    isEmpty(tmpl.serviceTypes) &&
	    isEmpty(tmpl.attributeSetTemplates)) {
	    long now = System.currentTimeMillis();
	    for (int i = entryClasses.size(); --i >= 0; ) {
		EntryClass eclass = (EntryClass)entryClasses.get(i);
		try {
		    codebases.add(pickCodebase(eclass, now));
		    classes.add(eclass);
		} catch (ClassNotFoundException e) {
		}
	    }
	} else {
	    for (ItemIter iter = matchingItems(tmpl); iter.hasNext(); ) {
		Item item = iter.next();
		for (int i = item.attributeSets.length; --i >= 0; ) {
		    EntryRep attrSet = item.attributeSets[i];
		    if (attrMatch(tmpl.attributeSetTemplates, attrSet) &&
			!classes.contains(attrSet.eclass)) {
			classes.add(attrSet.eclass);
			codebases.add(attrSet.codebase);
		    }
		}
	    }
	}
	if (classes.isEmpty())
	    return null; /* spec says null */
	EntryClassBase[] vals = new EntryClassBase[classes.size()];
	for (int i = vals.length; --i >= 0; ) {
	    vals[i] = new EntryClassBase(
				((EntryClass)classes.get(i)).getReplacement(),
				(String)codebases.get(i));
	}
	return vals;
    }

    /**
     * The code that does the real work of getFieldValues.  If the
     * template is just a singleton entry with all null fields, then
     * we can do a faster computation by iterating over keys in the
     * given attribute's serviceByAttr map, rather than iterating
     * over items.
     */
    private Object[] getFieldValuesDo(Template tmpl, int setidx, int fldidx)
    {
	resolver.resolve(tmpl);
	ArrayList values = new ArrayList();
	EntryRep etmpl = tmpl.attributeSetTemplates[setidx];
	if (tmpl.serviceID == null &&
	    isEmpty(tmpl.serviceTypes) &&
	    tmpl.attributeSetTemplates.length == 1 &&
	    allNull(etmpl.fields))
	{
	    long now = System.currentTimeMillis();
	    EntryClass eclass = getDefiningClass(etmpl.eclass, fldidx);
	    boolean checkAttr = !eclass.equals(etmpl.eclass);
	    HashMap[] attrMaps = (HashMap[])serviceByAttr.get(eclass);
	    if (attrMaps != null && attrMaps[fldidx] != null) {
		for (Iterator iter = attrMaps[fldidx].entrySet().iterator();
		     iter.hasNext(); )
		{
		    Map.Entry ent = (Map.Entry)iter.next();
		    ArrayList regs = (ArrayList)ent.getValue();
		    Object value = ent.getKey();
		    for (int i = regs.size(); --i >= 0; ) {
			SvcReg reg = (SvcReg)regs.get(i);
			if (reg.leaseExpiration > now &&
			    (!checkAttr ||
			     hasAttr(reg, etmpl.eclass, fldidx, value))) {
			    values.add(value);
			    break;
			}
		    }
		}
	    }
	} else {
	    for (ItemIter iter = matchingItems(tmpl); iter.hasNext(); ) {
		Item item = iter.next();
		for (int j = item.attributeSets.length; --j >= 0; ) {
		    if (matchEntry(etmpl, item.attributeSets[j])) {
			Object value = item.attributeSets[j].fields[fldidx];
			if (!values.contains(value))
			    values.add(value);
		    }
		}
	    }
	}
	if (values.isEmpty())
	    return null;
	return values.toArray();
    }

    /**
     * The code that does the real work of getServiceTypes.  If the
     * template has at most service types, then we can do a fast
     * computation based solely on concrete classes, without having
     * to iterate over items, but we have to work a bit harder to
     * get codebases.
     */
    private ServiceTypeBase[] getServiceTypesDo(Template tmpl, String prefix)
    {
	resolver.resolve(tmpl);
	ArrayList classes = new ArrayList();
	ArrayList codebases = new ArrayList();
	if (tmpl.serviceID == null && isEmpty(tmpl.attributeSetTemplates)) {
	    long now = System.currentTimeMillis();
	    ServiceType[] types = matchingConcreteClasses(tmpl.serviceTypes);
	    for (int i = types.length; --i >= 0; ) {
		try {
		    addTypes(classes, codebases, tmpl.serviceTypes, prefix,
			     types[i], pickCodebase(types[i], now));
		} catch (ClassNotFoundException e) {
		}
	    }
	} else {
	    for (ItemIter iter = matchingItems(tmpl); iter.hasNext(); ) {
		Item item = iter.next();
		addTypes(classes, codebases, tmpl.serviceTypes, prefix,
			 item.serviceType, item.codebase);
	    }
	}
	if (classes.isEmpty())
	    return null; /* spec says null */
	ServiceTypeBase[] vals = new ServiceTypeBase[classes.size()];
	for (int i = vals.length; --i >= 0; ) {
	    vals[i] = new ServiceTypeBase(
			       ((ServiceType)classes.get(i)).getReplacement(),
			       (String)codebases.get(i));
	}
	return vals;
    }

    /**
     * The code that does the real work of addAttributes.
     * Add every element of attrSets to item, updating serviceByAttr as
     * necessary, incrementing the number of EntryClass instances, and
     * updating entryClasses as necessary.
     */
    private void addAttributesDo(ServiceID serviceID,
				 long leaseID,
				 EntryRep[] attrSets)
	throws UnknownLeaseException
    {
	resolver.resolve(attrSets);
	long now = System.currentTimeMillis();
	SvcReg reg = (SvcReg)serviceByID.get(serviceID);
	if (reg == null ||
	    reg.leaseID != leaseID ||
	    reg.leaseExpiration <= now)
	    throw new UnknownLeaseException();
	Item pre = (Item)reg.item.clone();
	EntryRep[] sets = reg.item.attributeSets;
	int i = 0;
	/* don't add if it's a duplicate */
	for (int j = 0; j < attrSets.length; j++) {
	    EntryRep set = attrSets[j];
	    if (indexOf(sets, set) < 0 && indexOf(attrSets, j, set) < 0) {
		attrSets[i++] = set;
		addAttrs(reg, set);
	    }
	}
	if (i > 0) {
	    int len = sets.length;
	    EntryRep[] nsets = new EntryRep[len + i];
	    System.arraycopy(sets, 0, nsets, 0, len);
	    System.arraycopy(attrSets, 0, nsets, len, i);
	    reg.item.attributeSets = nsets;
	}
	generateEvents(pre, reg.item, now);
    }

    /**
     * The code that does the real work of modifyAttributes.
     * Modify the attribute sets that match attrSetTmpls, updating
     * or deleting based on attrSets, updating serviceByAttr as necessary,
     * decrementing the number of EntryClass instances, and updating
     * entryClasses as necessary.
     */
    private void modifyAttributesDo(ServiceID serviceID,
				    long leaseID,
				    EntryRep[] attrSetTmpls,
				    EntryRep[] attrSets)
	throws UnknownLeaseException
    {
	resolver.resolve(attrSetTmpls);
	resolver.resolveWithNulls(attrSets);
	if (attrSetTmpls.length != attrSets.length)
	    throw new IllegalArgumentException(
				       "attribute set length mismatch");
	for (int i = attrSets.length; --i >= 0; ) {
	    if (attrSets[i] != null &&
		!attrSets[i].eclass.isAssignableFrom(attrSetTmpls[i].eclass))
		throw new IllegalArgumentException(
					   "attribute set type mismatch");
	}
	long now = System.currentTimeMillis();
	SvcReg reg = (SvcReg)serviceByID.get(serviceID);
	if (reg == null ||
	    reg.leaseID != leaseID ||
	    reg.leaseExpiration <= now)
	    throw new UnknownLeaseException();
	Item pre = (Item)reg.item.clone();
	EntryRep[] preSets = pre.attributeSets;
	EntryRep[] sets = reg.item.attributeSets;
	for (int i = preSets.length; --i >= 0; ) {
	    EntryRep preSet = preSets[i];
	    EntryRep set = sets[i];
	    for (int j = attrSetTmpls.length; --j >= 0; ) {
		if (matchEntry(attrSetTmpls[j], preSet)) {
		    EntryRep attrs = attrSets[j];
		    if (attrs == null) {
			sets = deleteSet(reg.item, i);
			deleteAttrs(reg, set, true);
			break;
		    } else {
			updateAttrs(reg, set, attrs.fields);
		    }
		}
	    }
	}
	for (int i = sets.length; --i >= 0; ) {
	    EntryRep set = sets[i];
	    if (indexOf(sets, i, set) >= 0) {
		sets = deleteSet(reg.item, i);
		deleteInstance(set.eclass);
	    }
	}
	reg.item.attributeSets = sets;
	generateEvents(pre, reg.item, now);
    }

    /**
     * The code that does the real work of setAttributes.
     * Replace all attributes of item with attrSets, updating serviceByAttr
     * as necessary, incrementing the number of EntryClass instances, and
     * updating entryClasses as necessary.
     */
    private void setAttributesDo(ServiceID serviceID,
				 long leaseID,
				 EntryRep[] attrSets)
	throws UnknownLeaseException
    {
	resolver.resolve(attrSets);
	if (attrSets == null)
	    attrSets = emptyAttrs;
	else
	    attrSets = (EntryRep[])removeDups(attrSets);
	long now = System.currentTimeMillis();
	SvcReg reg = (SvcReg)serviceByID.get(serviceID);
	if (reg == null ||
	    reg.leaseID != leaseID ||
	    reg.leaseExpiration <= now)
	    throw new UnknownLeaseException();
	Item pre = (Item)reg.item.clone();
	EntryRep[] entries = reg.item.attributeSets;
	for (int i = entries.length; --i >= 0; ) {
	    deleteAttrs(reg, entries[i], false);
	}
	reg.item.attributeSets = attrSets;
	for (int i = attrSets.length; --i >= 0; ) {
	    addAttrs(reg, attrSets[i]);
	}
	generateEvents(pre, reg.item, now);
    }

    /** The code that does the real work of cancelServiceLease. */
    private void cancelServiceLeaseDo(ServiceID serviceID, long leaseID)
	throws UnknownLeaseException
    {
	if (serviceID.equals(myServiceID))
	    throw new SecurityException("privileged service id");
	long now = System.currentTimeMillis();
	SvcReg reg = (SvcReg)serviceByID.get(serviceID);
	if (reg == null ||
	    reg.leaseID != leaseID ||
	    reg.leaseExpiration <= now)
	    throw new UnknownLeaseException();
	deleteService(reg, now);
	/* wake up thread if this might be the (only) earliest time */
	if (reg.leaseExpiration == minSvcExpiration)
	    concurrentObj.waiterNotify(serviceNotifier);
    }

    /** The code that does the real work of renewServiceLease. */
    private long renewServiceLeaseDo(ServiceID serviceID,
				     long leaseID,
				     long renewDuration)
	throws UnknownLeaseException
    {
	long now = System.currentTimeMillis();
	long renewExpiration = renewServiceLeaseInt(serviceID, leaseID,
						    renewDuration, now);
	addLogRecord(new ServiceLeaseRenewedLogObj(serviceID, leaseID,
						   renewExpiration));
	return renewExpiration - now;
    }

    /** Renew a service lease for a relative duration from now. */
    private long renewServiceLeaseInt(ServiceID serviceID,
				      long leaseID,
				      long renewDuration,
				      long now)
	throws UnknownLeaseException
    {
	if (serviceID.equals(myServiceID))
	    throw new SecurityException("privileged service id");
	if (renewDuration == Lease.ANY)
	    renewDuration = maxServiceLease;
	else if (renewDuration < 0)
	    throw new IllegalArgumentException("negative lease duration");
	SvcReg reg = (SvcReg)serviceByID.get(serviceID);
	if (reg == null ||
	    reg.leaseID != leaseID ||
	    reg.leaseExpiration <= now)
	    throw new UnknownLeaseException();
	if (renewDuration > maxServiceLease &&
	    renewDuration > reg.leaseExpiration - now)
	    renewDuration = Math.max(reg.leaseExpiration - now,
				     maxServiceLease);
	long renewExpiration = now + renewDuration;
	/* force a re-sort: must remove before changing, then reinsert */
	serviceByTime.remove(reg);
	reg.leaseExpiration = renewExpiration;
	serviceByTime.put(reg, reg);
	/* see if the expire thread needs to wake up earlier */
	if (renewExpiration < minSvcExpiration) {
	    minSvcExpiration = renewExpiration;
	    concurrentObj.waiterNotify(serviceNotifier);
	}
	return renewExpiration;
    }

    /** Renew the service lease for an absolute expiration time. */
    private void renewServiceLeaseAbs(ServiceID serviceID,
				      long leaseID,
				      long renewExpiration)
    {
	SvcReg reg = (SvcReg)serviceByID.get(serviceID);
	if (reg == null || reg.leaseID != leaseID)
	    return;
	/* force a re-sort: must remove before changing, then reinsert */
	serviceByTime.remove(reg);
	reg.leaseExpiration = renewExpiration;
	serviceByTime.put(reg, reg);
    }

    /** The code that does the real work of cancelEventLease. */
    private void cancelEventLeaseDo(long eventID, long leaseID)
	throws UnknownLeaseException
    {
	long now = System.currentTimeMillis();
	EventReg reg = (EventReg)eventByID.get(new Long(eventID));
	if (reg == null || reg.leaseExpiration <= now)
	    throw new UnknownLeaseException();
	deleteEvent(reg);
	/* wake up thread if this might be the (only) earliest time */
	if (reg.leaseExpiration == minEventExpiration)
	    concurrentObj.waiterNotify(eventNotifier);
    }

    /** The code that does the real work of renewEventLease. */
    private long renewEventLeaseDo(long eventID,
				   long leaseID,
				   long renewDuration)
	throws UnknownLeaseException
    {
	long now = System.currentTimeMillis();
	long renewExpiration = renewEventLeaseInt(eventID, leaseID,
						  renewDuration, now);
	addLogRecord(new EventLeaseRenewedLogObj(eventID, leaseID,
						 renewExpiration));
	return renewExpiration - now;
    }

    private long renewEventLeaseInt(long eventID,
				    long leaseID,
				    long renewDuration,
				    long now)
	throws UnknownLeaseException
    {
	if (renewDuration == Lease.ANY)
	    renewDuration = maxEventLease;
	else if (renewDuration < 0)
	    throw new IllegalArgumentException("negative lease duration");
	EventReg reg = (EventReg)eventByID.get(new Long(eventID));
	if (reg == null ||
	    reg.leaseID != leaseID ||
	    reg.leaseExpiration <= now)
	    throw new UnknownLeaseException();
	if (renewDuration > maxEventLease &&
	    renewDuration > reg.leaseExpiration - now)
	    renewDuration = Math.max(reg.leaseExpiration - now, maxEventLease);
	long renewExpiration = now + renewDuration;
	/* force a re-sort: must remove before changing, then reinsert */
	eventByTime.remove(reg);
	reg.leaseExpiration = renewExpiration;
	eventByTime.put(reg, reg);
	/* see if the expire thread needs to wake up earlier */
	if (renewExpiration < minEventExpiration) {
	    minEventExpiration = renewExpiration;
	    concurrentObj.waiterNotify(eventNotifier);
	}
	return renewExpiration;
    }

    /** Renew the event lease for an absolute expiration time. */
    private void renewEventLeaseAbs(long eventID,
				    long leaseID,
				    long renewExpiration)
    {
	EventReg reg = (EventReg)eventByID.get(new Long(eventID));
	if (reg == null || reg.leaseID != leaseID)
	    return;
	/* force a re-sort: must remove before changing, then reinsert */
	eventByTime.remove(reg);
	reg.leaseExpiration = renewExpiration;
	eventByTime.put(reg, reg);
    }

    /**
     * The code that does the real work of renewLeases.  Each element of
     * regIDs must either be a ServiceID (for a service lease) or a Long
     * (for an event lease).  Renewals contains durations.  All three
     * arrays must be the same length.
     */
    private RenewResults renewLeasesDo(Object[] regIDs,
				       long[] leaseIDs,
				       long[] renewals)
    {
	long now = System.currentTimeMillis();
	Exception[] exceptions = null;
	for (int i = 0; i < regIDs.length; i++) {
	    Object id = regIDs[i];
	    try {
		if (id instanceof ServiceID)
		    renewals[i] = renewServiceLeaseInt((ServiceID)id,
						       leaseIDs[i],
						       renewals[i], now);
		else
		    renewals[i] = renewEventLeaseInt(((Long)id).longValue(),
						     leaseIDs[i], renewals[i],
						     now);
	    } catch (Exception e) {
		renewals[i] = -1;
		if (exceptions == null)
		    exceptions = new Exception[]{e};
		else
		    exceptions = (Exception[])arrayAdd(exceptions, e);
	    }
	}
	/* don't bother to weed out problem leases */
	addLogRecord(new LeasesRenewedLogObj(regIDs, leaseIDs, renewals));
	for (int i = regIDs.length; --i >= 0; ) {
	    if (renewals[i] >= 0)
		renewals[i] -= now;
	}
	return new RenewResults(renewals, exceptions);
    }

    /**
     * Renew the leases for absolute expiration times.  Skip any leases
     * with negative expiration times.
     */
    private void renewLeasesAbs(Object[] regIDs,
				long[] leaseIDs,
				long[] renewExpirations)
    {
	for (int i = regIDs.length; --i >= 0; ) {
	    long expiration = renewExpirations[i];
	    if (expiration < 0)
		continue;
	    Object id = regIDs[i];
	    if (id instanceof ServiceID)
		renewServiceLeaseAbs((ServiceID)id, leaseIDs[i], expiration);
	    else
		renewEventLeaseAbs(((Long)id).longValue(), leaseIDs[i],
				   expiration);
	}
    }

    /**
     * The code that does the real work of cancelLeases.  Each element of
     * regIDs must either be a ServiceID (for a service lease) or a Long
     * (for an event lease).  The two arrays must be the same length.
     * If there are no exceptions, the return value is null.  Otherwise,
     * the return value has the same length as regIDs, and has nulls for
     * leases that successfully renewed.
     */
    private Exception[] cancelLeasesDo(Object[] regIDs, long[] leaseIDs) {
	Exception[] exceptions = null;
	for (int i = regIDs.length; --i >= 0; ) {
	    Object id = regIDs[i];
	    try {
		if (id instanceof ServiceID)
		    cancelServiceLeaseDo((ServiceID)id, leaseIDs[i]);
		else
		    cancelEventLeaseDo(((Long)id).longValue(), leaseIDs[i]);
	    } catch (Exception e) {
		if (exceptions == null)
		    exceptions = new Exception[regIDs.length];
		exceptions[i] = e;
	    }
	}
	return exceptions;
    }

    /**
     * Generate events for all matching event registrations.  A null pre
     * represents creation of a new item, a null post represents deletion
     * of an item.
     */
    private void generateEvents(Item pre, Item post, long now) {
	if (inRecovery)
	    return;
	ServiceID sid = (pre != null) ? pre.serviceID : post.serviceID;
	Object val = subEventByService.get(sid);
	if (val instanceof EventReg) {
	    generateEvent((EventReg)val, pre, post, sid, now);
	} else if (val != null) {
	    EventReg[] regs = (EventReg[])val;
	    for (int i = regs.length; --i >= 0; ) {
		generateEvent(regs[i], pre, post, sid, now);
	    }
	}
	for (Iterator iter = subEventByID.values().iterator();
	     iter.hasNext(); )
	{
	    generateEvent((EventReg)iter.next(), pre, post, sid, now);
	}
    }

    /**
     * Generate an event if the event registration matches.  A null pre
     * represents creation of a new item, a null post represents deletion
     * of an item.  sid is the serviceID of the item.
     */
    private void generateEvent(EventReg reg,
			       Item pre,
			       Item post,
			       ServiceID sid,
			       long now)
    {
	if (reg.leaseExpiration <= now)
	    return;
	if ((reg.transitions &
		  ServiceRegistrar.TRANSITION_NOMATCH_MATCH) != 0 &&
		 (pre == null || !matchItem(reg.tmpl, pre)) &&
		 (post != null && matchItem(reg.tmpl, post)))
	    pendingEvent(reg, sid, post,
			 ServiceRegistrar.TRANSITION_NOMATCH_MATCH);
	else if ((reg.transitions &
		  ServiceRegistrar.TRANSITION_MATCH_NOMATCH) != 0 &&
		 (pre != null && matchItem(reg.tmpl, pre)) &&
		 (post == null || !matchItem(reg.tmpl, post)))
	    pendingEvent(reg, sid, post,
			 ServiceRegistrar.TRANSITION_MATCH_NOMATCH);
	else if ((reg.transitions &
		  ServiceRegistrar.TRANSITION_MATCH_MATCH) != 0 &&
		 (pre != null && matchItem(reg.tmpl, pre)) &&
		 (post != null && matchItem(reg.tmpl, post)))
	    pendingEvent(reg, sid, post,
			 ServiceRegistrar.TRANSITION_MATCH_MATCH);
    }

    /** Add a pending EventTask for this event registration. */
    private void pendingEvent(EventReg reg,
			      ServiceID sid,
			      Item item,
			      int transition)
    {
	if (item != null)
	    item = copyItem(item);
	newNotifies.add(new EventTask(reg, sid, item, transition));
    }

    /** Queue all pending EventTasks for processing by the task manager. */
    private void queueEvents() {
	if (!newNotifies.isEmpty()) {
	    tasker.addAll(newNotifies);
	    newNotifies.clear();
	}
    }

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
    }

    /** Generate a new lease id */
    private long newLeaseID() {
	secRand.nextBytes(secRandBuf8);
	long id = 0;
	for (int i = 8; --i >= 0; ) {
	    id = (id << 8) + (secRandBuf8[i] & 0xFF);
	}
	return id;
    }

    /**
     * Write the current state of the Registrar to persistent storage.
     * <p>
     * A 'snapshot' of the Registrar's current state is represented by
     * the data contained in certain fields of the Registrar. That data
     * represents many changes -- over time -- to the Registrar's state.
     * This method will record that data to a file referred to as the
     * snapshot file.
     * <p>
     * The data written by this method to the snapshot file -- as well as
     * the format of the file -- is shown below:
     * <ul>
     * <li> our class name
     * <li> log format version number
     * <li> our service ID
     * <li> current event ID
     * <li> current configuration parameters
     * <li> contents of the container holding the current registered services
     * <li> null (termination 'marker' for the set of registered services)
     * <li> contents of the container holding the current registered events
     * <li> null (termination 'marker' for the set of registered events)
     * </ul>
     * Each data item is written to the snapshot file in serialized form.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private void takeSnapshot(OutputStream  out) throws IOException {
	ObjectOutputStream stream = new ObjectOutputStream(out);

	stream.writeUTF(RegistrarImpl.class.getName());
	stream.writeInt(LOG_VERSION);
	stream.writeObject(myServiceID);
	stream.writeLong(eventID);
	stream.writeInt(logToSnapshotThresh);
	stream.writeFloat(snapshotWt);
	stream.writeLong(minMaxServiceLease);
	stream.writeLong(minMaxEventLease);
	stream.writeLong(minRenewalInterval);
	stream.writeInt(unicastPort);
	stream.writeObject(memberGroups);
	stream.writeObject(lookupGroups);
	stream.writeObject(lookupLocators);
	for (Iterator iter = serviceByID.values().iterator(); iter.hasNext(); )
	{
	    stream.writeObject(iter.next());
	}
	stream.writeObject(null);
	for (Iterator iter = eventByID.values().iterator(); iter.hasNext(); )
	{
	    stream.writeObject(iter.next());
	}
	stream.writeObject(null);
	stream.flush();
    }

    /**
     * Retrieve the contents of the snapshot file and reconstitute the 'base'
     * state of the Registrar from the retrieved data.
     * <p>
     * The data retrieved by this method from the snapshot file is shown
     * below:
     * <ul>
     * <li> our class name
     * <li> log format version number
     * <li> our service ID
     * <li> current event ID
     * <li> current configuration parameters
     * <li> contents of the container holding the current registered services
     * <li> contents of the container holding the current registered events
     * </ul>
     * During recovery, the state of the Registrar at the time of a crash
     * or outage is re-constructed by first reconstituting the 'base state'
     * from the snapshot file; and then modifying that base state according
     * to the records retrieved from the log file. This method is invoked to
     * perform the first step in that reconstruction. As each registered
     * service or event is retrieved, it is resolved and then inserted into
     * its appropriate container object.
     * <p>
     * Because events can be generated before the next snapshot is taken,
     * the event sequence numbers must be incremented. This is because the
     * event specification requires that set of event sequence numbers
     * be monotonically increasing. Since the number of events that might
     * have been sent is arbitrary, each sequence number will be incremented
     * by a 'large' number so as to guarantee adherence to the specification.
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private void recoverSnapshot(InputStream in)
	throws IOException, ClassNotFoundException
    {
	ObjectInputStream stream = new ObjectInputStream(in);
	if (!RegistrarImpl.class.getName().equals(stream.readUTF()))
	    throw new IOException("log from wrong implementation");
	if (stream.readInt() != LOG_VERSION)
	    throw new IOException("wrong log format version");
	myServiceID = (ServiceID)stream.readObject();
	eventID = stream.readLong();
	logToSnapshotThresh = stream.readInt();
	snapshotWt = stream.readFloat();
	minMaxServiceLease = stream.readLong();
	minMaxEventLease = stream.readLong();
	minRenewalInterval = stream.readLong();
	unicastPort = stream.readInt();
	memberGroups = (String[])stream.readObject();
	lookupGroups = (String[])stream.readObject();
	lookupLocators = (LookupLocator[])stream.readObject();
	SvcReg sReg;
	while ((sReg = (SvcReg)stream.readObject()) != null) {
	    resolver.resolve(sReg.item);
	    addService(sReg);
	}

	EventReg eReg;
	while ((eReg = (EventReg)stream.readObject()) != null) {
	    resolver.resolve(eReg.tmpl);
	    eReg.seqNo += Integer.MAX_VALUE;
	    addEvent(eReg);
	}
    }

    /**
     * Add a state-change record to persistent storage.
     * <p>
     * Whenever a significant change occurs to the Registrar's state, this
     * method is invoked to record that change in a file called a log file.
     * Each record written to the log file is an object reflecting both
     * the data used and the ACTIONS taken to make one change to the
     * Registrar's state at a particular point in time. If the number of
     * records contained in the log file exceeds the pre-defined threshold,
     * a snapshot of the current state of the Registrar will be recorded.
     * <p>
     * Whenever one of the following state changes occurs, this method
     * will be invoked with the appropriate implementation of the
     * LogRecord interface as the input argument.
     * <ul>
     * <li> a new service was registered
     * <li> a new event was registered
     * <li> new attributes were added to an existing service
     * <li> existing attributes of a service were modified
     * <li> new attributes were set in an existing service
     * <li> a single service lease was renewed
     * <li> a single service lease was cancelled
     * <li> a single event lease was renewed
     * <li> a single event lease was cancelled
     * <li> a set of service leases were renewed
     * <li> a set of service leases were cancelled
     * <li> a set of event leases were renewed
     * <li> a set of event leases were cancelled
     * <li> the unicast port number was set
     * <li> the value of the minMaxServiceLease was set
     * <li> the value of the minMaxEventLease was set
     * <li> the value of the minRenewalInterval was set
     * <li> the weight factor used to determine when to take a snapshot was set
     * <li> the threshold used to determine when to take a snapshot was set
     * <li> the set of Lookup Groups were changed
     * <li> the set of Lookup Locators were changed
     * <li> the set of Member Groups were changed
     * </ul>
     * 
     * @see RegistrarImpl.LocalLogHandler
     */
    private void addLogRecord(LogRecord rec) {
	try {
	    log.update(rec, true);
	    if (++logFileSize >= logToSnapshotThresh) {
		int snapshotSize = serviceByID.size() + eventByID.size();
		if ((float)logFileSize >= snapshotWt*((float)snapshotSize)) {
                    concurrentObj.waiterNotify(snapshotNotifier);
		}
	    }
	} catch (Exception e) {
	    /* if log updating fails, then one of the following must be done:
	     *   -- output the problem to a separate file and exit()
	     *   -- output the problem to a separate file and continue
	     *   -- set an "I have a problem" attribute & send notification
	     * XXX this issue will be addressed at a later time
	     */
	    if (!Thread.currentThread().isInterrupted()) {
		e.printStackTrace();
	    }
	}
    }
}
