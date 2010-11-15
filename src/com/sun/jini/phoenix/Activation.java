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

package com.sun.jini.phoenix;

import com.sun.jini.proxy.BasicProxyTrustVerifier;
import com.sun.jini.proxy.MarshalledWrapper;
import com.sun.jini.reliableLog.LogHandler;
import com.sun.jini.reliableLog.ReliableLog;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.Process;
import java.net.URL;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UID;
import java.security.CodeSource;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationNotFoundException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

/**
 * Phoenix main class.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
class Activation implements Serializable {
    private static final long serialVersionUID = -6825932652725866242L;
    private static final String PHOENIX = "com.sun.jini.phoenix";
    private static ResourceBundle resources = null;
    private static Logger logger = Logger.getLogger(PHOENIX);
    private static final int PHOENIX_PORT = 1198;
    private static final Object logLock = new Object();

    /** maps activation id uid to its respective group id */
    private Map idTable = new HashMap();
    /** maps group id to its GroupEntry groups */
    private Map groupTable = new HashMap();
    
    /** login context */
    private transient LoginContext login;
    /** number of simultaneous group exec's */
    private transient int groupSemaphore;
    /** counter for numbering groups */
    private transient int groupCounter = 0;
    /** persistent store */
    private transient ReliableLog log;
    /** number of log updates since last log snapshot */
    private transient int numUpdates = 0;
    /** take log snapshot after this many updates */
    private transient int snapshotInterval;
    /** the default java command for groups */
    private transient String[] command;
    /** timeout on wait for child process to be created or destroyed */
    private transient long groupTimeout;
    /** timeout on wait for unexport to succeed */
    private transient long unexportTimeout;
    /** timeout on wait between unexport attempts */
    private transient long unexportWait;
    /** ActivatorImpl instance */
    private transient Activator activator;
    /** exporter for activator */
    private transient Exporter activatorExporter;
    /** stub for activator */
    private transient Activator activatorStub;
    /** SystemImpl instance */
    private transient ActivationSystem system;
    /** exporter for system */
    private transient Exporter systemExporter;
    /** stub for system */
    private transient ActivationSystem systemStub;
    /** MonitorImpl instance */
    private transient ActivationMonitor monitor;
    /** exporter for monitor */
    private transient Exporter monitorExporter;
    /** stub for monitor */
    private transient ActivationMonitor monitorStub;
    /** RegistryImpl instance */
    private transient Registry registry;
    /** exporter for registry */
    private transient Exporter registryExporter;
    /** stub for registry */
    private transient Registry registryStub;
    /** MarshalledObject(ActivationGroupData) or null */
    private transient MarshalledObject groupData;
    /** Location of ActivationGroupImpl or null */
    private transient String groupLocation;
    /** preparer for ActivationInstantiators */
    private transient ProxyPreparer groupPreparer;
    private transient GroupOutputHandler outputHandler;
    /** true if shutdown has been called */
    private volatile boolean shuttingDown = false;
    /** Runtime shutdown hook */
    private transient Thread shutdownHook;
    /** Non-null if phoenix was started by the service starter */
    private transient PhoenixStarter starter;

    /**
     * Create an uninitialized instance of Activation that can be
     * populated with log data.  This is only called when the initial
     * snapshot is taken during the first incarnation of phoenix.
     */
    private Activation() {}

    private void init(ReliableLog log,
		      LoginContext login,
		      Configuration config,
		      String[] configOptions,
		      PhoenixStarter starter)
	throws Exception
    {
	this.log = log;
	this.login = login;
	this.starter = starter;
	groupSemaphore = getInt(config, "groupThrottle", 3);
	snapshotInterval = getInt(config, "persistenceSnapshotThreshold", 200);
	groupTimeout = getInt(config, "groupTimeout", 60000);
	unexportTimeout = getInt(config, "unexportTimeout", 60000);
	unexportWait = getInt(config, "unexportWait", 10);
	String[] opts = (String[]) config.getEntry(
		PHOENIX, "groupOptions", String[].class, new String[0]);
	command = new String[opts.length + 2];
	command[0] = (System.getProperty("java.home") +
		      File.separator + "bin" + File.separator + "java");
	System.arraycopy(opts, 0, command, 1, opts.length);
	command[command.length - 1] =
	    "com.sun.jini.phoenix.ActivationGroupInit";
	shutdownHook = new ShutdownHook();
	Runtime.getRuntime().addShutdownHook(shutdownHook);
	groupPreparer = getPreparer(config, "instantiatorPreparer");
	groupData = new MarshalledObject(new ActivationGroupData((String[])
	       config.getEntry(PHOENIX, "groupConfig",
			       String[].class, configOptions)));
	outputHandler = (GroupOutputHandler) config.getEntry(
		PHOENIX, "groupOutputHandler", GroupOutputHandler.class,
		new GroupOutputHandler() {
		    public void handleOutput(ActivationGroupID id,
					     ActivationGroupDesc desc,
					     long incarnation,
					     String groupName,
					     InputStream out,
					     InputStream err)
		    {
  			PipeWriter.plugTogetherPair(
				groupName, out, System.out, err, System.err);
		    }
		});
	
	groupLocation = (String)
	    config.getEntry(PHOENIX, "groupLocation",
			    String.class, getDefaultGroupLocation());
	
	ActivationGroupID[] gids = (ActivationGroupID[])
	    groupTable.keySet().toArray(
				    new ActivationGroupID[groupTable.size()]);
	activator = new ActivatorImpl();
	ServerEndpoint se = TcpServerEndpoint.getInstance(PHOENIX_PORT);
	activatorExporter =
	    getExporter(config, "activatorExporter",
			new BasicJeriExporter(se, new BasicILFactory(),
					      false, true,
					     PhoenixConstants.ACTIVATOR_UUID));
	system = new SystemImpl();
	systemExporter =
	    getExporter(config, "systemExporter",
			new BasicJeriExporter(se, new SystemAccessILFactory(),
					      false, true,
			             PhoenixConstants.ACTIVATION_SYSTEM_UUID));
	monitor = new MonitorImpl();
	monitorExporter =
	    getExporter(config, "monitorExporter",
			new BasicJeriExporter(se, new AccessILFactory()));
	registry = new RegistryImpl();
	registryExporter =
	    getExporter(config, "registryExporter", new RegistrySunExporter());
	monitorStub = (ActivationMonitor) monitorExporter.export(monitor);
	synchronized (activatorExporter) {
	    systemStub = (ActivationSystem) systemExporter.export(system);
	    activatorStub = (Activator) activatorExporter.export(activator);
	}
	registryStub = (Registry) registryExporter.export(registry);
	logger.info(getTextResource("phoenix.daemon.started"));
	for (int i = gids.length; --i >= 0; ) {
	    try {
		getGroupEntry(gids[i]).restartServices();
	    } catch (UnknownGroupException e) {
	    }
	}
    }

    private static String getDefaultGroupLocation() {
	ProtectionDomain pd = Activation.class.getProtectionDomain();
	CodeSource cs = pd.getCodeSource();
	URL location = null;
	if (cs != null) {
	    location = cs.getLocation();
	}
	if (location != null) {
	    String loc = location.toString();
	    if (loc.endsWith(".jar")) {
		return loc.substring(0, loc.length() - 4) + "-group.jar";
	    }
	}
	return null;
    }

    /**
     * Return a configuration for the specified options.
     */
    private static Configuration getConfig(String[] configOptions,
					   PhoenixStarter starter)
	throws ConfigurationException
    {
	try {
	    return ConfigurationProvider.getInstance(
		configOptions, Activation.class.getClassLoader());
	    
	} catch (ConfigurationNotFoundException e) {
	    if (starter == null) {
		bomb("phoenix.missing.config",
		     Arrays.asList(configOptions).toString());
	    }
	    throw e;
	}
    }

    /**
     * Return an int configuration entry.
     */
    private static int getInt(Configuration config, String name, int defValue)
	throws ConfigurationException
    {
	return ((Integer) config.getEntry(PHOENIX, name, int.class,
					  new Integer(defValue))).intValue();
    }

    /**
     * Return the exporter with the specified name from the specified
     * configuration.
     */
    private static Exporter getExporter(Configuration config,
					String name,
					Exporter defaultExporter)
	throws ConfigurationException
    {
	return (Exporter)
	    config.getEntry(PHOENIX, name, Exporter.class, defaultExporter);
    }

    /**
     * Return a ProxyPreparer configuration entry.
     */
    private static ProxyPreparer getPreparer(Configuration config, String name)
	throws ConfigurationException
    {
	return (ProxyPreparer) config.getEntry(PHOENIX, name,
					       ProxyPreparer.class,
					       new BasicProxyPreparer());
    }

    class ActivatorImpl extends AbstractActivator implements ServerProxyTrust {
	ActivatorImpl() {
	}
    
	public MarshalledWrapper activate(ActivationID id, boolean force)
    	    throws ActivationException
	{
	    UID uid = getUID(id);
	    return getGroupEntry(uid).activate(uid, force);
	}

	public TrustVerifier getProxyVerifier() {
	    return new ConstrainableAID.Verifier(activatorStub);
	}
    }

    class MonitorImpl extends AbstractMonitor implements ServerProxyTrust {
	MonitorImpl() {
	}
	
	public void inactiveObject(ActivationID id)
	    throws UnknownObjectException
	{
	    UID uid = getUID(id);
	    getGroupEntry(uid).inactiveObject(uid);
	}
    
	public void activeObject(ActivationID id, MarshalledObject mobj)
    	    throws UnknownObjectException
	{
	    UID uid = getUID(id);
	    getGroupEntry(uid).activeObject(uid, mobj);
	}
	
	public void inactiveGroup(ActivationGroupID id,
				  long incarnation)
	    throws UnknownGroupException
	{
	    getGroupEntry(id).inactiveGroup(incarnation, false);
	}
	
	public TrustVerifier getProxyVerifier() {
	    return new BasicProxyTrustVerifier(monitorStub);
	}
    }
    
    class SystemImpl extends AbstractSystem implements ServerProxyTrust {
	SystemImpl() {
	}
	
	/** returns a ConstrainableAID */
	public ActivationID registerObject(ActivationDesc desc)
	    throws ActivationException
	{
	    UID uid = new UID();
	    ActivationGroupID groupID = desc.getGroupID();
	    getGroupEntry(groupID).registerObject(uid, desc, true);
	    synchronized (activatorExporter) {
		return getAID(uid);
	    }
	}

	public void unregisterObject(ActivationID id)
	    throws ActivationException
	{
	    UID uid = getUID(id);
	    getGroupEntry(getGroupID(uid)).unregisterObject(uid, true);
	}
	
	public ActivationGroupID registerGroup(ActivationGroupDesc desc)
	    throws ActivationException
	{
	    ActivationGroupID id = new ActivationGroupID(systemStub);
	    synchronized (logLock) {
		addLogRecord(new LogRegisterGroup(id, desc));
		GroupEntry entry = new GroupEntry(id, desc);
		synchronized (groupTable) {
		    groupTable.put(id, entry);
		}
	    }
	    return id;
	}
	
	public ActivationMonitor activeGroup(ActivationGroupID id,
					     ActivationInstantiator group,
					     long incarnation)
	    throws ActivationException, RemoteException
	{
	    group = (ActivationInstantiator) groupPreparer.prepareProxy(group);
	    getGroupEntry(id).activeGroup(group, incarnation);
	    return monitorStub;
	}
	
	public void unregisterGroup(ActivationGroupID id)
	    throws ActivationException
	{
	    GroupEntry groupEntry;
	    synchronized (groupTable) {
		groupEntry = getGroupEntry(id);		
		groupTable.remove(id);
	    }
	    groupEntry.unregisterGroup(true);
	}

	public ActivationDesc setActivationDesc(ActivationID id,
						ActivationDesc desc)
	    throws ActivationException
	{
	    UID uid = getUID(id);
	    if (!getGroupID(uid).equals(desc.getGroupID())) {
		throw new ActivationException(
				       "ActivationDesc contains wrong group");
	    }
	    return getGroupEntry(uid).setActivationDesc(uid, desc, true);
	}

	public ActivationGroupDesc setActivationGroupDesc(
						     ActivationGroupID id,
						     ActivationGroupDesc desc)
	    throws ActivationException
	{
	    return getGroupEntry(id).setActivationGroupDesc(id, desc, true);
	}

	public ActivationDesc getActivationDesc(ActivationID id)
	    throws UnknownObjectException
	{
	    UID uid = getUID(id);
	    return getGroupEntry(uid).getActivationDesc(uid);
	}
	      
	public ActivationGroupDesc getActivationGroupDesc(ActivationGroupID id)
	    throws UnknownGroupException
	{
	    return getGroupEntry(id).desc;
	}
	
	public void shutdown() {
	    synchronized (Activation.this) {
		if (!shuttingDown) {
		    shuttingDown = true;
		    new Shutdown().start();
		}
	    }
	}

	public Map getActivationGroups() {
	    synchronized (groupTable) {
		Map map = new HashMap(groupTable.size());
		for (Iterator iter = groupTable.values().iterator();
		     iter.hasNext(); )
		{
		    GroupEntry entry = (GroupEntry) iter.next();
		    if (!entry.removed) {
			map.put(entry.groupID, entry.desc);
		    }
		}
		return map;
	    }
	}

	public Map getActivatableObjects(ActivationGroupID id)
	    throws UnknownGroupException
	{
	    synchronized (activatorExporter) {
		// to wait for it to be exported
	    }
	    return getGroupEntry(id).getActivatableObjects();
	}
	
	public TrustVerifier getProxyVerifier() {
	    return new BasicProxyTrustVerifier(systemStub);
	}
    }

    /**
     * A read-only registry containing a single entry for the system.
     */
    class RegistryImpl extends AbstractRegistry {
	/** The name of the single entry */
	private final String NAME = ActivationSystem.class.getName();

	RegistryImpl() {
	}

	/**
	 * Returns the single object if the specified name matches the single
	 * name, otherwise throws NotBoundException.
	 */
	public Remote lookup(String name) throws NotBoundException {
	    if (name.equals(NAME)) {
		return systemStub;
	    }
	    throw new NotBoundException(name);
	}

	/** Always throws SecurityException. */
	public void bind(String name, Remote obj) {
	    throw new SecurityException("read-only registry");
	}

	/** Always throws SecurityException. */
	public void unbind(String name) {
	    throw new SecurityException("read-only registry");
	}

	/** Always throws SecurityException. */
	public void rebind(String name, Remote obj) {
	    throw new SecurityException("read-only registry");
	}

	/** Returns a list containing the single name. */
	public String[] list() {
	    return new String[]{NAME};
	}
    }

    /**
     * If shutting down, throw an ActivationException.
     */
    private void checkShutdown() throws ActivationException {
	if (shuttingDown) {
	    throw new ActivationException(
				       "activation system is shutting down");
	}
    }

    /**
     * Thread to shutdown phoenix.
     */
    private class Shutdown extends Thread {
	Shutdown() {
	    super("Shutdown");
	}

	public void run() {
	    try {
		long stop = System.currentTimeMillis() + unexportTimeout;
		boolean force = false;
		while (!registryExporter.unexport(force) ||
		       !activatorExporter.unexport(force) ||
		       !systemExporter.unexport(force) ||
		       !monitorExporter.unexport(force))
		{
		    long rem = stop - System.currentTimeMillis();
		    if (rem <= 0) {
			force = true;
		    } else {
			try {
			    Thread.sleep(Math.min(rem, unexportWait));
			} catch (InterruptedException e) {
			}
		    }
		}
		// destroy all child processes (groups)
		GroupEntry[] groupEntries;
		synchronized (groupTable) {
		    groupEntries = (GroupEntry[]) groupTable.values().
			toArray(new GroupEntry[groupTable.size()]);
		}
		for (int i = 0; i < groupEntries.length; i++) {
		    groupEntries[i].shutdown();
		}
		
		Runtime.getRuntime().removeShutdownHook(shutdownHook);
		try {
		    synchronized (logLock) {
			log.close();
		    }
		} catch (IOException e) {
		}
		try {
		    if (login != null) {
			login.logout();
		    }
		} catch (LoginException e) {
		}
	    } catch (Throwable t) {
		logger.log(Level.WARNING, "exception during shutdown", t);
	    } finally {
		logger.info(getTextResource("phoenix.daemon.shutdown"));
		if (starter == null) {
		    System.exit(0);
		} else {
		    starter.unregister();
		}
	    }
	}
    }
    
    /** Thread to destroy children in the event of abnormal termination. */
    private class ShutdownHook extends Thread {
	ShutdownHook() {
	    super("Phoenix Shutdown Hook");
	}

	public void run() {
	    synchronized (Activation.this) {
		shuttingDown = true;
	    }
	    // destroy all child processes (groups) quickly
	    synchronized (groupTable) {
		for (Iterator iter = groupTable.values().iterator();
		     iter.hasNext(); )
		{
		    ((GroupEntry) iter.next()).shutdownFast();
		}
	    }
	}
    }

    private ActivationID getAID(UID uid) {
	if (activatorStub instanceof RemoteMethodControl) {
	    return new ConstrainableAID(activatorStub, uid);
	} else {
	    return new AID(activatorStub, uid);
	}
    }

    private static UID getUID(ActivationID id) throws UnknownObjectException {
	Class c = id.getClass();
	if (c == AID.class || c == ConstrainableAID.class) {
	    return ((AID) id).getUID();
	}
	throw new UnknownObjectException("object unknown");
    }

    /**
     * Returns the groupID for a given id of an object in the group.
     * Throws UnknownObjectException if the object is not registered.
     */
    private ActivationGroupID getGroupID(UID uid)
	throws UnknownObjectException
    {
	synchronized (idTable) {
	    ActivationGroupID groupID = (ActivationGroupID) idTable.get(uid);
	    if (groupID != null) {
		return groupID;
	    }
	}
	throw new UnknownObjectException("object unknown");
    }
    
    /**
     * Returns the group entry for the group id. Throws
     * UnknownGroupException if the group is not registered.
     */
    private GroupEntry getGroupEntry(ActivationGroupID id)
	throws UnknownGroupException
    {
	if (id.getClass() == ActivationGroupID.class) {
	    synchronized (groupTable) {
		GroupEntry entry = (GroupEntry) groupTable.get(id);
		if (entry != null && !entry.removed) {
		    return entry;
		}
	    }
	}
	throw new UnknownGroupException("group unknown");
    }

    /**
     * Returns the group entry for the object's id. Throws
     * UnknownObjectException if the object is not registered.
     */
    private GroupEntry getGroupEntry(UID uid) throws UnknownObjectException {
	ActivationGroupID gid = getGroupID(uid);
	synchronized (groupTable) {
	    GroupEntry entry = (GroupEntry) groupTable.get(gid);
	    if (entry != null) {
		return entry;
	    }
	}
	throw new UnknownObjectException("object's group removed");
    }

    /**
     * Container for group information: group's descriptor, group's
     * instantiator, flag to indicate pending group creation, and
     * table of the group's active objects.
     *
     * WARNING: GroupEntry objects should not be written into log file
     * updates.  GroupEntrys are inner classes of Activation and they
     * can not be serialized independent of this class.  If the
     * complete Activation system is written out as a log update, the
     * point of having updates is nullified.  
     */
    private class GroupEntry implements Serializable {
	
	private static final long serialVersionUID = 7222464070032993304L;
	private static final int MAX_TRIES = 2;
	private static final int NORMAL = 0;
	private static final int CREATING = 1;
	private static final int TERMINATE = 2;
	private static final int TERMINATING = 3;
	
	ActivationGroupDesc desc;
	ActivationGroupID groupID;
	long incarnation = 0;
	Map objects = new HashMap(11);
	HashSet restartSet = new HashSet();
	
	transient ActivationInstantiator group = null;
	transient int status = NORMAL;
	transient long waitTime = 0;
	transient String groupName = null;
	transient Process child = null;
	transient boolean removed = false;
	transient Watchdog watchdog = null;
	
	GroupEntry(ActivationGroupID groupID, ActivationGroupDesc desc) {
	    this.groupID = groupID;
	    this.desc = desc;
	}

	void restartServices() {
	    Iterator iter = null;
	    
	    synchronized (this) {
		if (restartSet.isEmpty()) {
		    return;
		}

		/*
		 * Clone the restartSet so the set does not have to be locked
		 * during iteration. Locking the restartSet could cause
		 * deadlock if an object we are restarting caused another
		 * object in this group to be activated.
		 */
		iter = ((Set) restartSet.clone()).iterator();
	    }
	    
	    while (iter.hasNext()) {
		UID uid = (UID) iter.next();
		try {
		    activate(uid, true);
		} catch (Exception e) {
		    if (shuttingDown) {
			return;
		    }
		    logger.log(Level.WARNING, "unable to restart service", e);
		}
	    }
	}
	
	synchronized void activeGroup(ActivationInstantiator inst,
				      long instIncarnation)
	    throws ActivationException
	{
	    if (group != null && group.equals(inst) &&
		incarnation == instIncarnation)
	    {
		return;
	    } else if (child != null && status != CREATING) {
		throw new ActivationException("group not being created");
	    } else if (incarnation != instIncarnation) {
		throw new ActivationException("invalid incarnation");
	    } else if (group != null) {
		throw new ActivationException("group already active");
	    }
	    
	    group = inst;
	    status = NORMAL;
	    notifyAll();
	}

	private void checkRemoved() throws UnknownGroupException {
	    if (removed) {
		throw new UnknownGroupException("group removed");
	    }
	}

	private ObjectEntry getObjectEntry(UID uid)
	    throws UnknownObjectException
	{
	    if (removed) {
		throw new UnknownObjectException("object's group removed");
	    }
	    ObjectEntry objEntry = (ObjectEntry) objects.get(uid);
	    if (objEntry == null) {
		throw new UnknownObjectException("object unknown");
	    }
	    return objEntry;
	}

	synchronized void registerObject(UID uid, 
					 ActivationDesc desc,
					 boolean addRecord)
    	    throws ActivationException
	{
	    checkRemoved();
	    synchronized (logLock) {
		if (addRecord) {
		    addLogRecord(new LogRegisterObject(uid, desc));
		}
		objects.put(uid, new ObjectEntry(desc));
		if (desc.getRestartMode()) {
		    restartSet.add(uid);
		}	    
		synchronized (idTable) {
		    idTable.put(uid, groupID);
		}
	    }
	}

	synchronized void unregisterObject(UID uid, boolean addRecord)
    	    throws ActivationException
	{
	    ObjectEntry objEntry = getObjectEntry(uid);
	    synchronized (logLock) {
		if (addRecord) {
		    addLogRecord(new LogUnregisterObject(uid));
		}
		objEntry.removed();
		objects.remove(uid);
		if (objEntry.desc.getRestartMode()) {
		    restartSet.remove(uid);
		}
		synchronized (idTable) {
		    idTable.remove(uid);
		}
	    }
	}
	
	synchronized Map getActivatableObjects() {
	    Map map = new HashMap(objects.size());
	    for (Iterator iter = objects.entrySet().iterator();
		 iter.hasNext(); )
	    {
		Map.Entry ent = (Map.Entry) iter.next();
		map.put(getAID((UID) ent.getKey()),
			((ObjectEntry) ent.getValue()).desc);
	    }
	    return map;
	}

	synchronized void unregisterGroup(boolean addRecord)
    	   throws ActivationException
	{
	    checkRemoved();
	    
	    synchronized (logLock) {
		if (addRecord) {
		    addLogRecord(new LogUnregisterGroup(groupID));
		}
		removed = true;
		for (Iterator iter = objects.entrySet().iterator();
		     iter.hasNext(); )
		{
		    Map.Entry ent = (Map.Entry) iter.next();
		    UID uid = (UID) ent.getKey();
		    synchronized (idTable) {
			idTable.remove(uid);
		    }
		    ObjectEntry objEntry = (ObjectEntry) ent.getValue();
		    objEntry.removed();
		}
		objects.clear();
		restartSet.clear();
		reset();
		childGone();
	    }
	}

	synchronized ActivationDesc setActivationDesc(UID uid,
						      ActivationDesc desc,
						      boolean addRecord)
	    throws ActivationException
	{
	    ObjectEntry objEntry = getObjectEntry(uid);
	    synchronized (logLock) {
		if (addRecord) {
		    addLogRecord(new LogUpdateDesc(uid, desc));
		}
		ActivationDesc oldDesc = objEntry.desc;
		objEntry.desc = desc;
		if (desc.getRestartMode()) {
		    restartSet.add(uid);
		} else {
		    restartSet.remove(uid);
		}
		return oldDesc;
	    }
	}

	synchronized ActivationDesc getActivationDesc(UID uid)
	    throws UnknownObjectException
	{
	    return getObjectEntry(uid).desc;
	}

	synchronized ActivationGroupDesc setActivationGroupDesc(
		ActivationGroupID id,
		ActivationGroupDesc desc,
		boolean addRecord)
    	    throws ActivationException
	{
	    checkRemoved();
	    synchronized (logLock) {
		if (addRecord) {
		    addLogRecord(new LogUpdateGroupDesc(id, desc));
		}
		ActivationGroupDesc oldDesc = this.desc;
		this.desc = desc;
		return oldDesc;
	    }
	}

	synchronized void inactiveGroup(long incarnation, boolean failure)
	    throws UnknownGroupException
	{
	    checkRemoved();
	    if (this.incarnation != incarnation) {
		throw new UnknownGroupException("invalid incarnation");
	    }
	    reset();
	    if (failure) {
		terminate();
	    } else if (child != null && status == NORMAL) {
		status = TERMINATE;
		watchdog.noRestart();
	    }
	}

	synchronized void activeObject(UID uid, MarshalledObject mobj)
	    throws UnknownObjectException
	{
	    getObjectEntry(uid).stub =
		new MarshalledWrapper(new MarshalledInstance(mobj));
	}

	synchronized void inactiveObject(UID uid)
    	    throws UnknownObjectException
	{
	    getObjectEntry(uid).reset();
	}

	private void reset() {
	    group = null;
	    for (Iterator iter = objects.values().iterator(); iter.hasNext(); )
	    {
		((ObjectEntry) iter.next()).reset();
	    }
	}
	
	private void childGone() {
	    if (child != null) {
		child = null;
		watchdog.dispose();
		watchdog = null;
		status = NORMAL;
		notifyAll();
	    }
	}

	private void terminate() {
	    if (child != null && status != TERMINATING) {
		child.destroy();
		status = TERMINATING;
		waitTime = System.currentTimeMillis() + groupTimeout;
		notifyAll();
	    }
	}

	private void await() {
	    while (true) {
		switch (status) {
		case NORMAL:
		    return;
		case TERMINATE:
		    terminate();
		case TERMINATING:
		    try {
			child.exitValue();
		    } catch (IllegalThreadStateException e) {
			long now = System.currentTimeMillis();
			if (waitTime > now) {
			    try {
				wait(waitTime - now);
			    } catch (InterruptedException ee) {
			    }
			    continue;
			}
			logger.log(Level.WARNING,
				   "group did not terminate: {0}", groupName);
		    }
		    childGone();
		    return;
		case CREATING:
		    try {
			wait();
		    } catch (InterruptedException e) {
		    }
		}
	    }
	}

	// no synchronization to avoid delay wrt getInstantiator
	void shutdownFast() {
	    Process p = child;
	    if (p != null) {
		p.destroy();
	    }
	}

	synchronized void shutdown() {
	    reset();
	    terminate();
	    await();
	}

	MarshalledWrapper activate(UID uid, boolean force)
	    throws ActivationException
	{
	    Exception detail = null;

	    /*
	     * Attempt to activate object and reattempt (several times)
	     * if activation fails due to communication problems.
	     */
	    for (int tries = MAX_TRIES; tries > 0; tries--) {
		ActivationInstantiator inst;
		long curIncarnation;

		// look up object to activate
		ObjectEntry objEntry;
		synchronized (this) {
		    objEntry = getObjectEntry(uid);
		    // if not forcing activation, return cached stub
		    if (!force && objEntry.stub != null) {
			return objEntry.stub;
		    }
		    inst = getInstantiator(groupID);
		    curIncarnation = incarnation;
		}

		boolean groupInactive = false;
		boolean failure = false;
		// activate object
		try {
		    return objEntry.activate(uid, force, inst);
		} catch (NoSuchObjectException e) {
		    groupInactive = true;
		    detail = e;
		} catch (ConnectException e) {
		    groupInactive = true;
		    failure = true;
		    detail = e;
		} catch (ConnectIOException e) {
		    groupInactive = true;
		    failure = true;
		    detail = e;
		} catch (InactiveGroupException e) {
		    groupInactive = true;
		    detail = e;
		} catch (RemoteException e) {
		    // REMIND: wait some here before continuing?
		    if (detail == null) {
			detail = e;
		    }
		}
		
		if (groupInactive) {
		    // group has failed; mark inactive
		    try {
			getGroupEntry(groupID).inactiveGroup(curIncarnation,
							     failure);
		    } catch (UnknownGroupException e) {
			// not a problem
		    }
		}
	    }

	    /** 
	     * signal that object activation failed, nested exception
	     * specifies what exception occurred when the object did not
	     * activate 
	     */
	    throw new ActivationException("object activation failed after " +
					  MAX_TRIES + " tries", detail);
	}

	/**
	 * Returns the instantiator for the group specified by id and
	 * entry. If the group is currently inactive, exec some
	 * bootstrap code to create the group.
	 */
	private ActivationInstantiator getInstantiator(ActivationGroupID id)
	    throws ActivationException
	{
	    await();
	    if (group != null) {
		return group;
	    }
	    checkRemoved();
	    boolean acquired = false;
	    try {
		groupName = Pstartgroup();
		acquired = true;
		String[] argv = activationArgs(desc);
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE, "{0} exec {1}",
			       new Object[]{groupName, Arrays.asList(argv)});
		}
		try {
		    child = Runtime.getRuntime().exec(argv);
		    status = CREATING;
		    ++incarnation;
		    watchdog = new Watchdog();
		    watchdog.start();

		    synchronized (logLock) {
			addLogRecord(
			    new LogGroupIncarnation(id, incarnation));
		    }
		    
		    outputHandler.handleOutput(id, desc, incarnation,
					       groupName,
					       child.getInputStream(),
					       child.getErrorStream());
		    MarshalOutputStream out =
			new MarshalOutputStream(child.getOutputStream(),
						Collections.EMPTY_LIST);
		    out.writeObject(id);
		    ActivationGroupDesc gd = desc;
		    if (gd.getClassName() == null) {
			MarshalledObject data = gd.getData();
			if (data == null) {
			    data = groupData;
			}
			String loc = gd.getLocation();
			if (loc == null) {
			    loc = groupLocation;
			}
			gd = new ActivationGroupDesc(
				"com.sun.jini.phoenix.ActivationGroupImpl",
				loc,
				data,
				gd.getPropertyOverrides(),
				gd.getCommandEnvironment());
		    }
		    out.writeObject(gd);
		    out.writeLong(incarnation);
		    out.flush();
		    out.close();
		    
		} catch (Exception e) {
		    terminate();
		    if (e instanceof ActivationException) {
			throw (ActivationException) e;
		    } else {
			throw new ActivationException(
				"unable to create activation group", e);
		    }
		}
		try {
		    long now = System.currentTimeMillis();
		    long stop = now + groupTimeout;
		    do {
			wait(stop - now);
			if (group != null) {
			    return group;
			}
			now = System.currentTimeMillis();
			// protect against premature return from wait
		    } while (status == CREATING && now < stop);
		} catch (InterruptedException e) {
		}
		terminate();
		throw new ActivationException(
					    "timeout creating child process");
	    } finally {
		if (acquired) {
		    Vstartgroup();
		}
	    }
	}

	/**
	 * Waits for process termination and then restarts services.
	 */
	private class Watchdog extends Thread {
	    private Process groupProcess = child;
	    private long groupIncarnation = incarnation;
	    private boolean canInterrupt = true;
	    private boolean shouldQuit = false;
	    private boolean shouldRestart = true;

	    Watchdog() {
		super("Watchdog-" + groupName + "-" + incarnation);
		setDaemon(true);
	    }

	    public void run() {
		if (shouldQuit) {
		    return;
		}
		/*
		 * Wait for the group to crash or exit.
		 */
		try {
		    groupProcess.waitFor();
		} catch (InterruptedException exit) {
		    return;
		}

		boolean restart = false;
		synchronized (GroupEntry.this) {
		    if (shouldQuit) {
			return;
		    }
		    canInterrupt = false;
		    interrupted(); // clear interrupt bit
		    /*
		     * Since the group crashed, we should
		     * reset the entry before activating objects
		     */
		    if (groupIncarnation == incarnation) {
			restart = shouldRestart && !shuttingDown;
			reset();
			childGone();
		    }
		}
		
		/*
		 * Activate those objects that require restarting
		 * after a crash.
		 */
		if (restart) {
		    restartServices();
		}
	    }

	    /** 
	     * Marks this thread as one that is no longer needed.
	     * If the thread is in a state in which it can be interrupted,
	     * then the thread is interrupted.
	     */
	    void dispose() {
		shouldQuit = true;
		if (canInterrupt) {
		    interrupt();
		}
	    }

	    /**
	     * Marks this thread as no longer needing to restart objects.
	     */
	    void noRestart() {
		shouldRestart = false;
	    }
	}
    }
	
    private String[] activationArgs(ActivationGroupDesc desc) {
	ActivationGroupDesc.CommandEnvironment cmdenv;
	cmdenv = desc.getCommandEnvironment();

	// argv is the literal command to exec
	List argv = new ArrayList();

	// Command name/path
	argv.add((cmdenv != null && cmdenv.getCommandPath() != null)
		    ? cmdenv.getCommandPath()
		    : command[0]);

	// Group-specific command options
	if (cmdenv != null && cmdenv.getCommandOptions() != null) {
	    argv.addAll(Arrays.asList(cmdenv.getCommandOptions()));
	}

	// Properties become -D parameters
	Properties props = desc.getPropertyOverrides();
	if (props != null) {
	    for (Enumeration p = props.propertyNames(); p.hasMoreElements(); )
	    {
		String name = (String) p.nextElement();
		/* Note on quoting: it would be wrong
		 * here, since argv will be passed to
		 * Runtime.exec, which should not parse
		 * arguments or split on whitespace.
		 */
		argv.add("-D" + name + "=" + props.getProperty(name));
	    }
	}

	// finally, phoenix-global command options and the classname 
	int i;
	for (i = 1; i < command.length; i++) {
	    argv.add(command[i]);
	}

	String[] realArgv = new String[argv.size()];
	System.arraycopy(argv.toArray(), 0, realArgv, 0,
			 realArgv.length);

	return (realArgv);
    }

    private class ObjectEntry implements Serializable {
	private static final long serialVersionUID = -808474359039620126L;
	/** descriptor for object */
	ActivationDesc desc;
	/** the stub (if active) */
	volatile transient MarshalledWrapper stub = null;
	volatile transient boolean removed = false;

	ObjectEntry(ActivationDesc desc) {
	    this.desc = desc;
	}

	synchronized MarshalledWrapper activate(UID uid,
						boolean force,
						ActivationInstantiator inst)
    	    throws RemoteException, ActivationException
	{
	    /* stub could be set to null by a concurrent group reset */
	    MarshalledWrapper nstub = stub;
	    if (removed) {
		throw new UnknownObjectException("object removed");
	    } else if (!force && nstub != null) {
		return nstub;
	    }
	    MarshalledInstance marshalledProxy =
		new MarshalledInstance(inst.newInstance(getAID(uid), desc));
	    nstub = new MarshalledWrapper(marshalledProxy);
	    stub = nstub;
	    return nstub;
	}
	
	void reset() {
	    stub = null;
	}

	void removed() {
	    removed = true;
	}
    }

    /**
     * Adds a record to the activation log. If the number of updates
     * passes a predetermined threshold, record a snapshot before
     * adding the record to the log.
     */
    private void addLogRecord(LogRecord rec) throws ActivationException {
	assert Thread.holdsLock(logLock);
	
	checkShutdown();
	if (numUpdates >= snapshotInterval) {
	    snapshot();
	}
	try {
	    log.update(rec, true);
	    numUpdates++;
	} catch (Exception e) {
	    logger.log(Level.WARNING, "log update throws", e);
	    snapshot();
	}
    }

    private void snapshot() throws ActivationException {
	assert Thread.holdsLock(logLock);
	try {
	    log.snapshot();
	    numUpdates = 0;
	} catch (Exception e) {
	    logger.log(Level.SEVERE, "log snapshot throws", e);
	    try {
		system.shutdown();
	    } catch (RemoteException ignore) {
		// can't happen
	    }
	    throw new ActivationException("log snapshot failed", e);
	}
    }
    
    /**
     * Handler for the log that knows how to take the initial snapshot
     * and apply an update (a LogRecord) to the current state.
     */
    private static class ActLogHandler extends LogHandler {

	private Activation state = null;
	
        ActLogHandler() {
	}

	public Activation getState() {
	    return state;
	}
	
	public void snapshot(OutputStream out) throws Exception {
	    if (state == null) {
		state = new Activation();
	    }
	    MarshalOutputStream s =
		new MarshalOutputStream(out, Collections.EMPTY_LIST);
	    s.writeObject(state);
	    s.flush();
	}
	
	public void recover(InputStream in) throws Exception {
	    MarshalInputStream s =
		new MarshalInputStream(in,
				       ActLogHandler.class.getClassLoader(),
				       false, null, Collections.EMPTY_LIST);
	    s.useCodebaseAnnotations();
	    state = (Activation) s.readObject();
	}

	public void writeUpdate(OutputStream out, Object value)
	    throws Exception
	{
	    MarshalOutputStream s =
		new MarshalOutputStream(out, Collections.EMPTY_LIST);
	    s.writeObject(value);
	    s.flush();
	}

	public void readUpdate(InputStream in) throws Exception {
	    MarshalInputStream  s =
		new MarshalInputStream(in,
				       ActLogHandler.class.getClassLoader(),
				       false, null, Collections.EMPTY_LIST);
	    s.useCodebaseAnnotations();
	    applyUpdate(s.readObject());
	}

	public void applyUpdate(Object update) throws Exception {
	    ((LogRecord) update).apply(state);
	}
    }

    /**
     * Abstract class for all log records. The subclass contains
     * specific update information and implements the apply method
     * that applies the update information contained in the record
     * to the current state.
     */
    private static abstract class LogRecord implements Serializable {

	private static final long serialVersionUID = 8395140512322687529L;
	abstract Object apply(Object state) throws Exception;
    }

    /**
     * Log record for registering an object.
     */
    private static class LogRegisterObject extends LogRecord {

	private static final long serialVersionUID = -6280336276146085143L;
	private UID uid;
	private ActivationDesc desc;

	LogRegisterObject(UID uid, ActivationDesc desc) {
	    this.uid = uid;
	    this.desc = desc;
	}
	
	Object apply(Object state) {
	    try {
		((Activation) state).getGroupEntry(desc.getGroupID()).
		    registerObject(uid, desc, false);
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    }
	    return state;
	}
    }

    /**
     * Log record for unregistering an object.
     */
    private static class LogUnregisterObject extends LogRecord {

	private static final long serialVersionUID = 6269824097396935501L;
	private UID uid;

	LogUnregisterObject(UID uid) {
	    this.uid = uid;
	}
	
	Object apply(Object state) {
	    try {
		((Activation) state).getGroupEntry(uid).unregisterObject(
								  uid, false);
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    }
	    return state;
	}
    }

    /**
     * Log record for registering a group.
     */
    private static class LogRegisterGroup extends LogRecord {

	private static final long serialVersionUID = -1966827458515403625L;
	private ActivationGroupID id;
	private ActivationGroupDesc desc;

	LogRegisterGroup(ActivationGroupID id, ActivationGroupDesc desc) {
	    this.id = id;
	    this.desc = desc;
	}

	Object apply(Object state) {
	    // modify state directly
	    // can't ask a nonexistent GroupEntry to register itself
	    ((Activation)state).groupTable.put(id, ((Activation) state).new
					       GroupEntry(id, desc));
	    return state;
	}
    }

    /**
     * Log record for updating an activation desc
     */
    private static class LogUpdateDesc extends LogRecord {

	private static final long serialVersionUID = 545511539051179885L;

	private UID uid;
	private ActivationDesc desc;

	LogUpdateDesc(UID uid, ActivationDesc desc) {
	    this.uid = uid;
	    this.desc = desc;
	}
	
	Object apply(Object state) {
	    try {
		((Activation) state).getGroupEntry(uid).
		    setActivationDesc(uid, desc, false);
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    }
	    return state;
	}
    }
    
    /**
     * Log record for unregistering a group.
     */
    private static class LogUpdateGroupDesc extends LogRecord {

	private static final long serialVersionUID = -1271300989218424337L;
	private ActivationGroupID id;
	private ActivationGroupDesc desc;

	LogUpdateGroupDesc(ActivationGroupID id, ActivationGroupDesc desc) {
	    this.id = id;
	    this.desc = desc;
	}
	
	Object apply(Object state) {
	    try {
		((Activation) state).getGroupEntry(id).
		    setActivationGroupDesc(id, desc, false);
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    }
	    return state;
	}
    }
    
    /**
     * Log record for unregistering a group.
     */
    private static class LogUnregisterGroup extends LogRecord {

	private static final long serialVersionUID = -3356306586522147344L;
	private ActivationGroupID id;

	LogUnregisterGroup(ActivationGroupID id) {
	    this.id = id;
	}
	
	Object apply(Object state) {
	    GroupEntry entry = (GroupEntry)
		((Activation) state).groupTable.remove(id);
	    try {
	    	entry.unregisterGroup(false);
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    }
	    return state;
	}
    }

    /**
     * Log record for an active group incarnation
     */
    private static class LogGroupIncarnation extends LogRecord {

	private static final long serialVersionUID = 4146872747377631897L;
	private ActivationGroupID id;
	private long inc;

	LogGroupIncarnation(ActivationGroupID id, long inc) {
	    this.id = id;
	    this.inc = inc;
	}

	Object apply(Object state) {
	    try {
		GroupEntry entry = ((Activation) state).getGroupEntry(id);
		entry.incarnation = inc;
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    }
	    return state;
	}
    }
    
    private static void usage() {
 	System.err.println(
		    MessageFormat.format(getTextResource("phoenix.usage"),
		    new String[] {Activation.class.getName()}));
	System.exit(1);
    }

    private static void bomb(String error) {
	System.err.println("phoenix: " + error); // $NON-NLS$
	usage();
    }

    private static void bomb(String res, String val) {
	bomb(MessageFormat.format(getTextResource(res), new String[] {val}));
    }

    /**
     * Starts phoenix. See the
     * <a href="package-summary.html#package_description">package
     * documentation</a> for details.
     *
     * @param args command line options
     */
    public static void main(String[] args) {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	boolean stop = false;
	if (args.length > 0 && args[0].equals("-stop")) {
	    stop = true;
	    String[] nargs = new String[args.length - 1];
	    System.arraycopy(args, 1, nargs, 0, nargs.length);
	    args = nargs;
	} else if (args.length == 1 && args[0].equals("-help")) {
	    usage();
	}
	try {
	    main(args, stop, null);
	} catch (Exception e) {
	    System.err.println(MessageFormat.format(
	                       getTextResource("phoenix.unexpected.exception"),
			       new Object[]{e.getMessage()}));
	    e.printStackTrace();
	    System.exit(1);
	}
    }

    /**
     * Returns the ActivationSystem proxy (used by the PhoenixStarter).
     */
    ActivationSystem getActivationSystemProxy() {
	return systemStub;
    }

    /**
     * Starts phoenix and returns a reference to the recovered
     * <code>Activation</code> instance.
     *
     * @param configOptions the configuration options for the configuration
     * @param stop if <code>true</code>, initiates shutdown of the
     * activation system on the "registryHost" and "registryPort" obtained
     * from the configuration
     * @param starter the <code>PhoenixStarter</code> instance, or
     * <code>null</code> 
     **/
    static Activation main(final String[] configOptions,
			   final boolean stop,
			   final PhoenixStarter starter)
	throws Exception
    {
	final Configuration config = getConfig(configOptions, starter);
	final LoginContext login =
	    (LoginContext) config.getEntry(PHOENIX, "loginContext",
					   LoginContext.class, null);
	if (login != null) {
	    login.login();
	}
	PrivilegedExceptionAction action = new PrivilegedExceptionAction() {
	    public Object run() throws Exception {
		if (stop) {
		    assert starter == null;
		    shutdown(config);
		    System.exit(0);
		}
		String logName = null;
		try {
		    logName = (String) config.getEntry(
			PHOENIX, "persistenceDirectory", String.class);
		} catch (NoSuchEntryException e) {
		    if (starter == null) {
			bomb("phoenix.missing.log", null);
		    } else {
			throw e;
		    }
		}
		ActLogHandler handler = new ActLogHandler();
		ReliableLog log = new ReliableLog(logName, handler);
		log.recover();
		Activation state = handler.getState();
		if (state == null) {
		    log.snapshot();
		    state = handler.getState();
		}
		state.init(log, login, config, configOptions, starter);
		if (starter == null) {
		    // prevent exit
		    while (true) {
			try {
			    Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {
			}
		    }
		} else {
		    return state;
		}
	    }
	};
	if (login != null) {
	    return (Activation)
		Subject.doAsPrivileged(login.getSubject(), action, null);
	} else {
	    return (Activation) action.run();
	}
    }

    /**
     * Shut down an activation system daemon, using the specified
     * configuration location to obtain the host and port of the daemon's
     * registry, the client constraints for the remote call, and the
     * permissions to grant to the system proxy.
     */
    private static void shutdown(Configuration config) throws Exception {
	String host = (String) config.getEntry(PHOENIX, "registryHost",
					       String.class, null);
	int port = getInt(config, "registryPort",
			  ActivationSystem.SYSTEM_PORT);
	Registry reg = LocateRegistry.getRegistry(host, port);
	ActivationSystem sys =
	    (ActivationSystem) reg.lookup(ActivationSystem.class.getName());
	ProxyPreparer sysPreparer = getPreparer(config, "systemPreparer");
	sys = (ActivationSystem) sysPreparer.prepareProxy(sys);
	sys.shutdown();
    }

    /**
     * Retrieves text resources from the locale-specific properties file.
     */
    static String getTextResource(String key) {
	if (resources == null) {
	    try {
		resources = ResourceBundle.getBundle(
		    "com.sun.jini.phoenix.resources.phoenix");
	    } catch (MissingResourceException mre) {
		return "[missing resource file: " + key + "]";
	    }
	}

	try {
	    return resources.getString(key);
	} catch (MissingResourceException mre) {
	    return "[missing resource: " + key + "]";
	}
    }

    /*
     * Dijkstra semaphore operations to limit the number of subprocesses
     * phoenix attempts to make at once.
     */

    /**
     * Acquire the group semaphore and return a group name.  Each
     * Pstartgroup must be followed by a Vstartgroup.  The calling thread
     * will wait until there are fewer than <code>N</code> other threads
     * holding the group semaphore.  The calling thread will then acquire
     * the semaphore and return.
     */
    private synchronized String Pstartgroup() throws ActivationException {
	while (true) {
	    checkShutdown();
	    // Wait until positive, then decrement.
	    if (groupSemaphore > 0) {
		groupSemaphore--;
		return "Group-" + groupCounter++;
	    }
	    try {
		wait();
	    } catch (InterruptedException e) {
	    }
	}
    }

    /**
     * Release the group semaphore.  Every P operation must be
     * followed by a V operation.  This may cause another thread to
     * wake up and return from its P operation.
     */
    private synchronized void Vstartgroup() {
	// Increment and notify a waiter (not necessarily FIFO).
	groupSemaphore++;
	notifyAll();
    }
}
