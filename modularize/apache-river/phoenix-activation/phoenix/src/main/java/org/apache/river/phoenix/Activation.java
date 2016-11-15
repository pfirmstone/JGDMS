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

package org.apache.river.phoenix;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationInstantiator;
import java.rmi.activation.ActivationMonitor;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.UnknownGroupException;
import java.rmi.activation.UnknownObjectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UID;
import java.security.CodeSource;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;
import org.apache.river.api.security.CombinerSecurityManager;
import org.apache.river.proxy.BasicProxyTrustVerifier;
import org.apache.river.proxy.MarshalledWrapper;
import org.apache.river.reliableLog.LogHandler;
import org.apache.river.reliableLog.ReliableLog;

/**
 * Phoenix main class.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
@AtomicSerial
class Activation implements Serializable {
    private static final long serialVersionUID = -6825932652725866242L;
    private static final String PHOENIX = "org.apache.river.phoenix";
    private static final ResourceBundle resources;
    private static final Logger logger = Logger.getLogger(PHOENIX);
    private static final int PHOENIX_PORT = 1198;
    private transient Lock readLock;
    private transient Lock writeLock; 
    private transient Condition signal;
    private transient Condition exported;
    private transient Condition unexported;
    
    static {
        ResourceBundle res = null;
        try {
            res = ResourceBundle.getBundle(
                "org.apache.river.phoenix.resources.phoenix");
        } catch (MissingResourceException mre) {
            logger.log( Level.CONFIG, "[missing resource file]", mre);
            res = null;
        }
        resources = res;
    }

    /** maps activation id uid to its respective group id */
    private final Map<UID,ActivationGroupID> idTable;
    /** maps group id to its GroupEntry groups */
    private final Map<ActivationGroupID,GroupEntry> groupTable;
    
    /** login context */
    private transient LoginContext login;
    /** number of simultaneous group exec's */
    private transient int groupSemaphore;
    /** counter for numbering groups */
    private transient int groupCounter;
    /** persistent store */
    private transient ReliableLog log;
    /** number of log updates since last log snapshot */
    private transient int numUpdates;
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
    private volatile boolean shuttingDown;
    /** Runtime shutdown hook */
    private transient Thread shutdownHook;
    /** Non-null if phoenix was started by the service starter */
    private transient PhoenixStarter starter;

    /**
     * Create an uninitialized instance of Activation that can be
     * populated with log data.  This is only called when the initial
     * snapshot is taken during the first incarnation of phoenix.
     */
    private Activation() {
	this.groupTable = new HashMap<ActivationGroupID,GroupEntry>();
	this.idTable = new HashMap<UID,ActivationGroupID>();
	this.shuttingDown = false;
	this.numUpdates = 0;
	this.groupCounter = 0;
        ReadWriteLock rwl = new ReentrantReadWriteLock();
        readLock = rwl.readLock();
        writeLock = rwl.writeLock();
        signal = writeLock.newCondition();
        exported = writeLock.newCondition();
        unexported = writeLock.newCondition();
    }
    
    Activation(GetArg arg) throws IOException{
	this(
	    Valid.copyMap(
		arg.get("idTable", null, Map.class),
		new HashMap<UID,ActivationGroupID>(),
		UID.class,
		ActivationGroupID.class
	    ),
	    Valid.copyMap(
		arg.get("groupTable", null, Map.class),
		new HashMap<ActivationGroupID,GroupEntry>(),
		ActivationGroupID.class, 
		GroupEntry.class
	    )
	);
    }
    
    private Activation(Map<UID,ActivationGroupID> idTable,
			Map<ActivationGroupID,GroupEntry> groupTable)
    {
	this.idTable = idTable;
	this.groupTable = groupTable;
	this.shuttingDown = false;
	this.numUpdates = 0;
	this.groupCounter = 0;
        ReadWriteLock rwl = new ReentrantReadWriteLock();
        readLock = rwl.readLock();
        writeLock = rwl.writeLock();
        signal = writeLock.newCondition();
        exported = writeLock.newCondition();
        unexported = writeLock.newCondition();
    }

    private void init(ReliableLog log,
		      LoginContext login,
		      Configuration config,
		      String[] configOptions,
		      PhoenixStarter starter)
	throws Exception
    {
        GroupEntry [] entries;
        writeLock.lock();
        try {
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
                "org.apache.river.phoenix.ActivationGroupInit";
            shutdownHook = new ShutdownHook();
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            groupPreparer = getPreparer(config, "instantiatorPreparer");
            groupData = new MarshalledInstance(
                new ActivationGroupData((String[]) config.getEntry(
                        PHOENIX, 
                        "groupConfig",
                        String[].class,
                        configOptions
                ))).convertToMarshalledObject();
            outputHandler = (GroupOutputHandler) config.getEntry(
                    PHOENIX, "groupOutputHandler", GroupOutputHandler.class,
                    new GroupOutputHandler() {
                        @Override
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
            systemStub = (ActivationSystem) systemExporter.export(system);
            activatorStub = (Activator) activatorExporter.export(activator);
            registryStub = (Registry) registryExporter.export(registry);
            exported.signalAll();
            logger.info(getTextResource("phoenix.daemon.started"));
            entries = new GroupEntry[gids.length];
            for (int i = gids.length; --i >= 0; ) {
                try {
                    entries[i] = getGroupEntry(gids[i]);
                } catch (UnknownGroupException e) {}//Ignore
            }
        } finally {
            writeLock.unlock();
        } 
        for (int i = entries.length; --i >= 0; ) {
            entries[i].restartServices();
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
					  Integer.valueOf(defValue))).intValue();
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
    
        @Override
	public MarshalledWrapper activate(ActivationID id, boolean force)
    	    throws ActivationException
	{
	    UID uid = getUID(id);
            GroupEntry entry;
            readLock.lock();
            try {
                entry = getGroupEntry(uid);
            } finally {
                readLock.unlock();
            }
            return entry.activate(uid, force);
	}

        @Override
	public TrustVerifier getProxyVerifier() {
            readLock.lock();
            try {
                return new ConstrainableAID.Verifier(activatorStub);
            } finally {
                readLock.unlock();
            }
	}
    }

    class MonitorImpl extends AbstractMonitor implements ServerProxyTrust {
	MonitorImpl() {
	}
	
        @Override
	public void inactiveObject(ActivationID id)
	    throws UnknownObjectException
	{
	    UID uid = getUID(id);
            writeLock.lock();
            try {
                getGroupEntry(uid).inactiveObject(uid);
            } finally {
                writeLock.unlock();
            }
	}
    
        @Override
	public void activeObject(ActivationID id, MarshalledObject mobj)
    	    throws UnknownObjectException
	{
	    UID uid = getUID(id);
            writeLock.lock();
            try {
                getGroupEntry(uid).activeObject(uid, mobj);
            } finally {
                writeLock.unlock();
            }
	}
	
        @Override
	public void inactiveGroup(ActivationGroupID id,
				  long incarnation)
	    throws UnknownGroupException
	{
            writeLock.lock();
            try {
                getGroupEntry(id).inactiveGroup(incarnation, false);
            } finally {
                writeLock.unlock();
            }
	}
	
        @Override
	public TrustVerifier getProxyVerifier() {
            readLock.lock();
            try {
                return new BasicProxyTrustVerifier(monitorStub);
            } finally {
                readLock.unlock();
            }
	}
    }
    
    class SystemImpl extends AbstractSystem implements ServerProxyTrust {
	SystemImpl() {
	}
	
	/** returns a ConstrainableAID */
        @Override
	public ActivationID registerObject(ActivationDesc desc)
	    throws ActivationException
	{
	    UID uid = new UID();
            writeLock.lock();
            try {
                ActivationGroupID groupID = desc.getGroupID();
                getGroupEntry(groupID).registerObject(uid, desc, true);
                return getAID(uid);
            } finally {
                writeLock.unlock();
            }
	}

        @Override
	public void unregisterObject(ActivationID id)
	    throws ActivationException
	{
	    UID uid = getUID(id);
            writeLock.lock();
            try {
                getGroupEntry(getGroupID(uid)).unregisterObject(uid, true);
            } finally {
                writeLock.unlock();
            }
	}
	
        @Override
	public ActivationGroupID registerGroup(ActivationGroupDesc desc)
	    throws ActivationException
	{
	    ActivationGroupID id = new ActivationGroupID(systemStub);
            writeLock.lock();
            try {
		addLogRecord(new LogRegisterGroup(id, desc));
                groupTable.put(id, new GroupEntry(id, desc, Activation.this));
            } finally {
                writeLock.unlock();
            }
	    return id;
	}
	
        @Override
	public ActivationMonitor activeGroup(ActivationGroupID id,
					     ActivationInstantiator group,
					     long incarnation)
	    throws ActivationException, RemoteException
	{
            writeLock.lock();
            try {
                group = (ActivationInstantiator) groupPreparer.prepareProxy(group);
                getGroupEntry(id).activeGroup(group, incarnation);
                return monitorStub;
            } finally {
                writeLock.unlock();
            }
	}
	
        @Override
	public void unregisterGroup(ActivationGroupID id)
	    throws ActivationException
	{
            writeLock.lock();
            try {
                GroupEntry groupEntry;
		groupEntry = getGroupEntry(id);		
		groupTable.remove(id);
                groupEntry.unregisterGroup(true);
            } finally {
                writeLock.unlock();
            }
	}

        @Override
	public ActivationDesc setActivationDesc(ActivationID id,
						ActivationDesc desc)
	    throws ActivationException
	{
	    UID uid = getUID(id);
            writeLock.lock();
            try {
                if (!getGroupID(uid).equals(desc.getGroupID())) {
                    throw new ActivationException(
                                           "ActivationDesc contains wrong group");
                }
                return getGroupEntry(uid).setActivationDesc(uid, desc, true);
            } finally {
                writeLock.unlock();
            }
	}

        @Override
	public ActivationGroupDesc setActivationGroupDesc(
						     ActivationGroupID id,
						     ActivationGroupDesc desc)
	    throws ActivationException
	{
            writeLock.lock();
            try {
                return getGroupEntry(id).setActivationGroupDesc(id, desc, true);
            } finally {
                writeLock.unlock();
            }
	}

        @Override
	public ActivationDesc getActivationDesc(ActivationID id)
	    throws UnknownObjectException
	{
	    UID uid = getUID(id);
            readLock.lock();
            try {
                return getGroupEntry(uid).getActivationDesc(uid);
            } finally {
                readLock.unlock();
            }
	}
	      
        @Override
	public ActivationGroupDesc getActivationGroupDesc(ActivationGroupID id)
	    throws UnknownGroupException
	{
            readLock.lock();
            try {
                GroupEntry entry = getGroupEntry(id);
                return entry.desc;
            } finally {
                readLock.unlock();
            }
	}
	
        @Override
	public void shutdown() {
            writeLock.lock();
            try {
                if (!shuttingDown) {
                    shuttingDown = true;
                    new Shutdown().start();
                }
            } finally {
                writeLock.unlock();
            }
	}

        @Override
	public Map<ActivationGroupID,ActivationGroupDesc> getActivationGroups() {
            readLock.lock();
            try {
                Collection<GroupEntry> entries;
                    entries = new ArrayList<GroupEntry>(groupTable.values());
                // release lock on groupTable before obtaining lock on entry.
                Map<ActivationGroupID,ActivationGroupDesc> map 
                        = new HashMap<ActivationGroupID,ActivationGroupDesc>(entries.size());
                for (Iterator<GroupEntry> iter = entries.iterator();
                     iter.hasNext(); )
                {
                    GroupEntry entry = iter.next();
                    if (!entry.removed) map.put(entry.groupID, entry.desc);
                }
                return map;
            } finally {
                readLock.unlock();
            }
	    
	}

        @Override
	public Map<ActivationID,ActivationDesc> getActivatableObjects(ActivationGroupID id)
	    throws UnknownGroupException
	{
	    writeLock.lock();
            try {
                // to wait for it to be exported
                while (activatorStub == null){
                    try {
                        exported.await();
                    } catch (InterruptedException ex) {
                        // restore interrupt.
                        Thread.currentThread().interrupt();
                    }
                }
                return getGroupEntry(id).getActivatableObjects();
            } finally {
                writeLock.unlock();
            }
        }
        @Override
	public TrustVerifier getProxyVerifier() {
            readLock.lock();
            try {
                return new BasicProxyTrustVerifier(systemStub);
            } finally {
                readLock.unlock();
            }
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
        @Override
	public Remote lookup(String name) throws NotBoundException {
	    if (name.equals(NAME)) {
                readLock.lock();
                try {
                    return systemStub;
                } finally {
                    readLock.unlock();
                }
	    }
	    throw new NotBoundException(name);
	}

	/** Always throws SecurityException. */
        @Override
	public void bind(String name, Remote obj) {
	    throw new SecurityException("read-only registry");
	}

	/** Always throws SecurityException. */
        @Override
	public void unbind(String name) {
	    throw new SecurityException("read-only registry");
	}

	/** Always throws SecurityException. */
        @Override
	public void rebind(String name, Remote obj) {
	    throw new SecurityException("read-only registry");
	}

	/** Returns a list containing the single name. */
        @Override
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

        @Override
	public void run() {
            writeLock.lock();
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
			    unexported.await(Math.min(rem, unexportWait),
                                    TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
                            Thread.currentThread().interrupt(); // restore
			}
		    }
		}
                // destroy all child processes (groups)
                GroupEntry[] groupEntries = 
                        groupTable.values().toArray(new GroupEntry[groupTable.size()]);
                int l = groupEntries.length;
                for (int i = 0; i < l; i++) {
                    groupEntries[i].shutdown();
                }
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                try {
                    log.close();
                } catch (IOException e) {}
                
		try {
		    if (login != null) {
			login.logout();
		    }
		} catch (LoginException e) {
		}
	    } catch (Throwable t) {
		logger.log(Level.WARNING, "exception during shutdown", t);
	    } finally {
                try {
                    logger.info(getTextResource("phoenix.daemon.shutdown"));
                    if (starter == null) {
                        System.exit(0);
                    } else {
                        starter.unregister();
                    }
                } finally {
                    writeLock.unlock();
                }
	    }
	}
    }
    
    /** Thread to destroy children in the event of abnormal termination. */
    private class ShutdownHook extends Thread {
	ShutdownHook() {
	    super("Phoenix Shutdown Hook");
	}

        @Override
	public void run() {
            writeLock.lock();
            try {
		shuttingDown = true;
                // destroy all child processes (groups) quickly
		for (Iterator<GroupEntry> iter = groupTable.values().iterator();
		     iter.hasNext(); )
		{
		    iter.next().shutdownFast();
		}
            } finally {
                writeLock.unlock();
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
        ActivationGroupID groupID = idTable.get(uid);
        if (groupID != null) return groupID;
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
            GroupEntry entry = groupTable.get(id);
            if (entry != null && !entry.removed) return entry;
	}
	throw new UnknownGroupException("group unknown");
    }

    /**
     * Returns the group entry for the object's id. Throws
     * UnknownObjectException if the object is not registered.
     */
    private GroupEntry getGroupEntry(UID uid) throws UnknownObjectException {
	ActivationGroupID gid = getGroupID(uid);
        GroupEntry entry = groupTable.get(gid);
        if (entry != null) return entry;
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
    @AtomicSerial
    private static class GroupEntry implements Serializable {
	
	private static final long serialVersionUID = 7222464070032993304L;
	private static final int MAX_TRIES = 2;
	private static final int NORMAL = 0;
	private static final int CREATING = 1;
	private static final int TERMINATE = 2;
	private static final int TERMINATING = 3;
        
	Activation activation;
	ActivationGroupDesc desc;
	final ActivationGroupID groupID;
	long incarnation;
	Map<UID,ObjectEntry> objects;
	Set<UID> restartSet;
	
	transient ActivationInstantiator group;
	transient int status;
	transient long waitTime;
	transient String groupName;
	transient volatile Process child;
	transient volatile boolean removed;
	transient Watchdog watchdog;
	
	GroupEntry(ActivationGroupID groupID, ActivationGroupDesc desc, Activation activation) {
	    this.removed = false;
	    this.child = null;
	    this.groupName = null;
	    this.waitTime = 0;
	    this.status = NORMAL;
	    this.group = null;
	    this.watchdog = null;
	    this.incarnation = 0;
	    this.restartSet = new HashSet<UID>();
	    this.objects = new HashMap<UID,ObjectEntry>(11);
	    this.groupID = groupID;
	    this.desc = desc;
	    this.activation = activation;
	}
	
	GroupEntry(GetArg arg) throws IOException{
	    this(
		    arg.get("desc", null, ActivationGroupDesc.class),
		    arg.get("groupID", null, ActivationGroupID.class),
		    arg.get("incarnation", 0L),
		    arg.get("objects", null, Map.class),
		    arg.get("restartSet", null, Set.class),
		    Valid.notNull(
			arg.get("activation", null, Activation.class),
			"activation cannot be null"
		    )
	    );
	}
	
	private GroupEntry(ActivationGroupDesc desc,
		ActivationGroupID groupID,
		long incarnation,
		Map<UID,ObjectEntry> objects,
		Set<UID> restartSet,
		Activation activation)
	{
	    this.removed = false;
	    this.child = null;
	    this.groupName = null;
	    this.waitTime = 0;
	    this.status = NORMAL;
	    this.group = null;
	    this.watchdog = null;
	    this.desc = desc;
	    this.groupID = groupID;
	    this.incarnation = incarnation;
	    this.objects = objects;
	    this.restartSet = restartSet;
	    this.activation = activation;
	}

        
	void restartServices() {
	    Iterator<UID> iter = null;
            activation.readLock.lock();
            try {
                if (restartSet.isEmpty()) return;
                /*
                 * Clone the restartSet so the set does not have to be locked
                 * during iteration. Locking the restartSet could cause
                 * deadlock if an object we are restarting caused another
                 * object in this group to be activated.
                 */

                iter = new HashSet<UID>(restartSet).iterator();
            } finally {
                activation.readLock.unlock();
            }
	    while (iter.hasNext()) {
		UID uid = iter.next();
		try {
		    activate(uid, true);
		} catch (Exception e) {
		    if (activation.shuttingDown) {
			return;
		    }
		    logger.log(Level.WARNING, "unable to restart service", e);
		}
	    }
	}
	
	void activeGroup(ActivationInstantiator inst,
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
	    activation.signal.signalAll();
	}

	private void checkRemoved() throws UnknownGroupException {
	    if (removed) {
		throw new UnknownGroupException("group removed");
	    }
	}

	private ObjectEntry getObjectEntry(UID uid)
	    throws UnknownObjectException
	{
	    if (removed) throw new UnknownObjectException("object's group removed");
	    ObjectEntry objEntry = objects.get(uid);
	    if (objEntry == null) throw new UnknownObjectException("object unknown");
	    return objEntry;
	}

	void registerObject(UID uid, 
					 ActivationDesc desc,
					 boolean addRecord)
    	    throws ActivationException
	{
	    checkRemoved();
            if (addRecord) activation.addLogRecord(new LogRegisterObject(uid, desc));
            objects.put(uid, new ObjectEntry(desc, activation));
            if (desc.getRestartMode()) restartSet.add(uid);
            activation.idTable.put(uid, groupID);
	}

	void unregisterObject(UID uid, boolean addRecord)
    	    throws ActivationException
	{
            ObjectEntry objEntry = getObjectEntry(uid);
            if (addRecord) activation.addLogRecord(new LogUnregisterObject(uid));
            objEntry.removed();
            objects.remove(uid);
            if (objEntry.desc.getRestartMode()) restartSet.remove(uid);
            activation.idTable.remove(uid);
	}
	
	Map<ActivationID,ActivationDesc> getActivatableObjects() {
            Map<ActivationID,ActivationDesc> map = 
                    new HashMap<ActivationID,ActivationDesc>(objects.size());
            for (Iterator<Map.Entry<UID,ObjectEntry>> iter = objects.entrySet().iterator();
                 iter.hasNext(); )
            {
                Map.Entry<UID,ObjectEntry> ent = iter.next();
                map.put(activation.getAID(ent.getKey()),ent.getValue().desc);
            }
            return map;
	}

	void unregisterGroup(boolean addRecord)
    	   throws ActivationException
	{
	    checkRemoved();
            if (addRecord) activation.addLogRecord(new LogUnregisterGroup(groupID));
            removed = true;
            for (Iterator<Map.Entry<UID,ObjectEntry>> iter = objects.entrySet().iterator();
                 iter.hasNext(); )
            {
                Map.Entry<UID,ObjectEntry> ent = iter.next();
                UID uid = ent.getKey();
                activation.idTable.remove(uid);
                ObjectEntry objEntry = ent.getValue();
                objEntry.removed();
            }
            objects.clear();
            restartSet.clear();
            reset();
            childGone();
	}

	ActivationDesc setActivationDesc(UID uid,
						      ActivationDesc desc,
						      boolean addRecord)
	    throws ActivationException
	{
            ObjectEntry objEntry = getObjectEntry(uid);
            if (addRecord) activation.addLogRecord(new LogUpdateDesc(uid, desc));
            ActivationDesc oldDesc = objEntry.desc;
            objEntry.desc = desc;
            if (desc.getRestartMode()) restartSet.add(uid);
            else  restartSet.remove(uid);
            return oldDesc;
	}

	ActivationDesc getActivationDesc(UID uid)
	    throws UnknownObjectException
	{
                return getObjectEntry(uid).desc;
	}

	ActivationGroupDesc setActivationGroupDesc(
		ActivationGroupID id,
		ActivationGroupDesc desc,
		boolean addRecord)
    	    throws ActivationException
	{
	    checkRemoved();
            if (addRecord) activation.addLogRecord(new LogUpdateGroupDesc(id, desc));
            ActivationGroupDesc oldDesc = this.desc;
            this.desc = desc;
            return oldDesc;
	}

	void inactiveGroup(long incarnation, boolean failure)
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

	void activeObject(UID uid, MarshalledObject mobj)
	    throws UnknownObjectException
	{
            getObjectEntry(uid).stub =
                new MarshalledWrapper(new MarshalledInstance(mobj));
	}

	void inactiveObject(UID uid)
    	    throws UnknownObjectException
	{
            getObjectEntry(uid).reset();
	}

	private void reset() {
	    group = null;
	    for (Iterator<ObjectEntry> iter = objects.values().iterator(); iter.hasNext(); )
	    {
		iter.next().reset();
	    }
	}
	
	private void childGone() {
	    if (child != null) {
		child = null;
		watchdog.dispose();
		watchdog = null;
		status = NORMAL;
		activation.signal.signalAll();
	    }
	}

	private void terminate() {
	    if (child != null && status != TERMINATING) {
		child.destroy();
		status = TERMINATING;
		waitTime = System.currentTimeMillis() + activation.groupTimeout;
		activation.signal.signalAll();
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
				activation.signal.await(waitTime - now, TimeUnit.MILLISECONDS);
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
			activation.signal.await();
		    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
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

	void shutdown() {
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
                boolean groupInactive = false;
		boolean failure = false;
		// look up object to activate
		ObjectEntry objEntry;
                activation.writeLock.lock();
                try {
                    objEntry = getObjectEntry(uid);
                    // if not forcing activation, return cached stub
                    MarshalledWrapper stub = objEntry.stub;
                    if (!force && stub != null) return stub;
                    inst = getInstantiator(groupID);
                    curIncarnation = incarnation;
                } finally {
                    activation.writeLock.unlock();
                }
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
                        activation.writeLock.lock();
                        try {
                            activation.getGroupEntry(groupID)
                                    .inactiveGroup(curIncarnation,failure);
                        } finally {
                            activation.writeLock.unlock();
                        }
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
		groupName = activation.Pstartgroup();
		acquired = true;
		String[] argv = activation.activationArgs(desc);
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
                    activation.addLogRecord(new LogGroupIncarnation(id, incarnation));
		    activation.outputHandler.handleOutput(id, desc, incarnation,
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
			    data = activation.groupData;
			}
			String loc = gd.getLocation();
			if (loc == null) {
			    loc = activation.groupLocation;
			}
			gd = new ActivationGroupDesc(
				"org.apache.river.phoenix.ActivationGroupImpl",
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
		    long stop = now + activation.groupTimeout;
		    do {
			activation.signal.await(stop - now, TimeUnit.MILLISECONDS);
			if (group != null) {
			    return group;
			}
			now = System.currentTimeMillis();
			// protect against premature return from wait
		    } while (status == CREATING && now < stop);
		} catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
		}
		terminate();
		throw new ActivationException(
					    "timeout creating child process");
	    } finally {
		if (acquired) {
		    activation.Vstartgroup();
		}
	    }
	}

	/**
	 * Waits for process termination and then restarts services.
	 */
	private class Watchdog extends Thread {
	    private final Process groupProcess = child;
	    private final long groupIncarnation = incarnation;
	    private volatile boolean canInterrupt = true;
	    private volatile boolean shouldQuit = false;
	    private volatile boolean shouldRestart = true;

	    Watchdog() {
		super("Watchdog-" + groupName + "-" + incarnation);
		setDaemon(true);
	    }

            /**
             * Prevent interruption if necessary.
             */
            @Override
            public void interrupt(){
                if (canInterrupt) super.interrupt();
		}

	    public void run() {
		if (shouldQuit) return;
		/*
		 * Wait for the group to crash or exit.
		 */
		try {
		    groupProcess.waitFor();
		} catch (InterruptedException exit) {
                    Thread.currentThread().interrupt();
		    return;
		}
                boolean interrupted;
		boolean restart = false;
                activation.writeLock.lock();
                try {
		    if (shouldQuit) return;
		    canInterrupt = false;
		    interrupted = interrupted(); // clear interrupt bit
		    /*
		     * Since the group crashed, we should
		     * reset the entry before activating objects
		     */
		    if (groupIncarnation == incarnation) {
			restart = shouldRestart && !activation.shuttingDown;
			reset();
			childGone();
		    }
                } finally {
                    activation.writeLock.unlock();
                }
                /*
                 * Activate those objects that require restarting
                 * after a crash.
                 */
                if (restart) restartServices();
                if (interrupted) interrupt();
                
           }

	    /** 
	     * Marks this thread as one that is no longer needed.
	     * If the thread is in a state in which it can be interrupted,
	     * then the thread is interrupted.
	     */
	    void dispose() {
		shouldQuit = true;
		    interrupt();
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
	List<String> argv = new ArrayList<String>();

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

    @AtomicSerial
    private static class ObjectEntry implements Serializable {
	private static final long serialVersionUID = -808474359039620126L;
	
	private final Activation activation;
	/** descriptor for object */
	ActivationDesc desc;
	/** the stub (if active) */
	transient MarshalledWrapper stub = null;
	transient boolean removed = false;
	
	ObjectEntry(ActivationDesc desc, Activation activation) {
	    this.desc = desc;
	    this.activation = activation;
	}
	
	ObjectEntry(GetArg arg) throws IOException{
	    this(arg.get("desc", null, ActivationDesc.class),
		 arg.get("activation", null, Activation.class));
	}

	MarshalledWrapper activate( UID uid,
				    boolean force,
				    ActivationInstantiator inst)
    	    throws RemoteException, ActivationException
	{
	    /* stub could be set to null by a concurrent group reset */
            MarshalledWrapper nstub;
            ActivationID id;
            ActivationDesc descriptor;
            activation.readLock.lock();
            try {
                nstub = stub;
                if (removed) {
                    throw new UnknownObjectException("object removed");
                } else if (!force && nstub != null) {
                    return nstub;
                }
                id = activation.getAID(uid);
                descriptor = desc;
            } finally {
                activation.readLock.unlock();
            }
	    MarshalledInstance marshalledProxy =
		new MarshalledInstance(inst.newInstance(id, descriptor));
            nstub = new MarshalledWrapper(marshalledProxy);
            activation.writeLock.lock();
            try {
                stub = nstub;
                return nstub;
            } finally {
                activation.writeLock.unlock();
            }
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
	
        @Override
	public void snapshot(OutputStream out) throws Exception {
	    if (state == null) {
		state = new Activation();
	    }
	    MarshalOutputStream s =
		new MarshalOutputStream(out, Collections.EMPTY_LIST);
	    s.writeObject(state);
	    s.flush();
	}
	
        @Override
	public void recover(InputStream in) throws Exception {
	    MarshalInputStream s =
		new MarshalInputStream(in,
				       ActLogHandler.class.getClassLoader(),
				       false, null, Collections.EMPTY_LIST);
	    s.useCodebaseAnnotations();
	    state = (Activation) s.readObject();
	}

        @Override
	public void writeUpdate(OutputStream out, Object value)
	    throws Exception
	{
	    MarshalOutputStream s =
		new MarshalOutputStream(out, Collections.EMPTY_LIST);
	    s.writeObject(value);
	    s.flush();
	}

        @Override
	public void readUpdate(InputStream in) throws Exception {
	    MarshalInputStream  s =
		new MarshalInputStream(in,
				       ActLogHandler.class.getClassLoader(),
				       false, null, Collections.EMPTY_LIST);
	    s.useCodebaseAnnotations();
	    applyUpdate(s.readObject());
	}

        @Override
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
	private final UID uid;
	private final ActivationDesc desc;

	LogRegisterObject(UID uid, ActivationDesc desc) {
	    this.uid = uid;
	    this.desc = desc;
	}
	
        @Override
	Object apply(Object state) {
            Activation act = (Activation) state;
            act.writeLock.lock();
	    try {
		act.getGroupEntry(desc.getGroupID()).registerObject(uid, desc, false);
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    } finally {
                act.writeLock.unlock();
            }
	    return state;
	}
    }

    /**
     * Log record for unregistering an object.
     */
    private static class LogUnregisterObject extends LogRecord {

	private static final long serialVersionUID = 6269824097396935501L;
	private final UID uid;

	LogUnregisterObject(UID uid) {
	    this.uid = uid;
	}
	
        @Override
	Object apply(Object state) {
	    try {
                Activation act = (Activation) state;
                act.writeLock.lock();
                try {
                    act.getGroupEntry(uid).unregisterObject(uid, false);
                } finally {
                    act.writeLock.unlock();
                }
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
	private final ActivationGroupID id;
	private final ActivationGroupDesc desc;

	LogRegisterGroup(ActivationGroupID id, ActivationGroupDesc desc) {
	    this.id = id;
	    this.desc = desc;
	}

        @Override
	Object apply(Object state) {
            Activation act = (Activation) state;
            act.writeLock.lock();
            try {
                // modify state directly
                // can't ask a nonexistent GroupEntry to register itself
                act.groupTable.put(id, new GroupEntry(id, desc, act));
                return state;
            } finally {
                act.writeLock.unlock();
            }
	}
    }

    /**
     * Log record for updating an activation desc
     */
    private static class LogUpdateDesc extends LogRecord {

	private static final long serialVersionUID = 545511539051179885L;

	private final UID uid;
	private final ActivationDesc desc;

	LogUpdateDesc(UID uid, ActivationDesc desc) {
	    this.uid = uid;
	    this.desc = desc;
	}
	
        @Override
	Object apply(Object state) {
            Activation act = (Activation) state;
            act.writeLock.lock();
	    try {
		act.getGroupEntry(uid).setActivationDesc(uid, desc, false);
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    } finally {
                act.writeLock.unlock();
            }
	    return state;
	}
    }
    
    /**
     * Log record for unregistering a group.
     */
    private static class LogUpdateGroupDesc extends LogRecord {

	private static final long serialVersionUID = -1271300989218424337L;
	private final ActivationGroupID id;
	private final ActivationGroupDesc desc;

	LogUpdateGroupDesc(ActivationGroupID id, ActivationGroupDesc desc) {
	    this.id = id;
	    this.desc = desc;
	}
	
        @Override
	Object apply(Object state) {
            Activation act = (Activation) state;
            act.writeLock.lock();
	    try {
		act.getGroupEntry(id).setActivationGroupDesc(id, desc, false);
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    } finally {
                act.writeLock.unlock();
            }
	    return state;
	}
    }
    
    /**
     * Log record for unregistering a group.
     */
    private static class LogUnregisterGroup extends LogRecord {

	private static final long serialVersionUID = -3356306586522147344L;
	private final ActivationGroupID id;

	LogUnregisterGroup(ActivationGroupID id) {
	    this.id = id;
	}
	
        @Override
	Object apply(Object state) {
            Activation act = (Activation) state;
            act.writeLock.lock();
            try {
                GroupEntry entry = act.groupTable.remove(id);
	    	entry.unregisterGroup(false);
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    } finally {
                act.writeLock.unlock();
            }
	    return state;
	}
    }

    /**
     * Log record for an active group incarnation
     */
    private static class LogGroupIncarnation extends LogRecord {

	private static final long serialVersionUID = 4146872747377631897L;
	private final ActivationGroupID id;
	private final long inc;

	LogGroupIncarnation(ActivationGroupID id, long inc) {
	    this.id = id;
	    this.inc = inc;
	}

        @Override
	Object apply(Object state) {
            Activation act = (Activation) state;
            act.writeLock.lock();
	    try {
		GroupEntry entry = act.getGroupEntry(id);
                entry.incarnation = inc;
	    } catch (Exception e) {
		logger.log(Level.WARNING, "log recovery throws", e);
	    } finally {
                act.writeLock.unlock();
            }
	    return state;
	}
    }
    
    private static void usage() {
 	System.err.println(
		    MessageFormat.format(getTextResource("phoenix.usage"),
                            (Object[]) new String[] {Activation.class.getName()}));
	System.exit(1);
    }

    private static void bomb(String error) {
	System.err.println("phoenix: " + error); // $NON-NLS$
	usage();
    }

    private static void bomb(String res, String val) {
	bomb(MessageFormat.format(getTextResource(res),
                (Object[]) new String[] {val}));
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
	    System.setSecurityManager(new CombinerSecurityManager());
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
        readLock.lock();
        try {
            return systemStub;
        } finally {
            readLock.unlock();
        }
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
	PrivilegedExceptionAction<Activation> action = new PrivilegedExceptionAction<Activation>() {
            @Override
	    public Activation run() throws Exception {
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
		    while (!Thread.currentThread().isInterrupted()) {
			try {
			    Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new Exception("Activation daemon interrupted");
			}
		    }
		} 
                return state;
	    }
	};
	if (login != null) {
	    return Subject.doAsPrivileged(login.getSubject(), action, null);
	} else {
	    return action.run();
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
        if (resources == null) return "[missing resource file: " + key + "]";
       
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
    private String Pstartgroup() throws ActivationException {
        boolean interrupted = false;
        try {
            while (true) {
                checkShutdown();
                // Wait until positive, then decrement.
                if (groupSemaphore > 0) {
                    groupSemaphore--;
                    return "Group-" + groupCounter++;
                }
                try {
                    signal.await();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /**
     * Release the group semaphore.  Every P operation must be
     * followed by a V operation.  This may cause another thread to
     * wake up and return from its P operation.
     */
    private void Vstartgroup() {
        // Increment and notify a waiter (not necessarily FIFO).
        groupSemaphore++;
        signal.signalAll();
    }
}
