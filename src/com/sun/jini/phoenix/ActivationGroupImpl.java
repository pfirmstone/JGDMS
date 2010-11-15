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

import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;
import com.sun.jini.proxy.BasicProxyTrustVerifier;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationInstantiator;
import java.rmi.activation.ActivationMonitor;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.UnknownObjectException;
import java.rmi.server.ExportException;
import java.rmi.server.RMIClassLoader;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import net.jini.activation.ActivationGroup;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.Security;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

/**
 * The default activation group implementation for phoenix.  Instances of
 * this class are configurable through a {@link Configuration}, as detailed
 * further below, and provide the necessary support to allow exporter-based
 * remote objects to go inactive.  Instances of this class support the
 * creation of remote objects through the normal activatable constructor;
 * an activatable remote object must either implement the {@link
 * ProxyAccessor} interface to return a suitable proxy for the remote
 * object, or the remote object must itself be serializable and marshalling
 * the object must produce a suitable proxy for the remote object.
 * 
 * <p>An instance of this class can be configured by specifying an
 * {@link ActivationGroupData} instance containing configuration options
 * as the initialization data for the activation group. Typically
 * this is accomplished indirectly, by setting the
 * <code>groupConfig</code> configuration entry for
 * phoenix itself. The following entries are obtained from the configuration,
 * all for the component named <code>com.sun.jini.phoenix</code>:
 *
 *  <table summary="Describes the loginContext configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *      <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *      loginContext</code></font>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Type: <td> <code>{@link javax.security.auth.login.LoginContext}</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Default: <td> <code>null</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Description: <td> JAAS login context
 *  </table>
 *
 *  <table summary="Describes the inheritGroupSubject configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *      <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *      inheritGroupSubject</code></font>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Type: <td> <code>boolean</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Default: <td> <code>false</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Description: <td> if <code>true</code>, group subject is inherited
 *		when an activatable object is created 
 *  </table>
 *
 *  <table summary="Describes the instantiatorExporter configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *      <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *      instantiatorExporter</code></font>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Type: <td> <code>{@link net.jini.export.Exporter}</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Default: <td> retains existing JRMP export of instantiator
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Description: <td> {@link java.rmi.activation.ActivationInstantiator}
 *		exporter
 *  </table>
 *
 *  <table summary="Describes the monitorPreparer configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *      <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *      monitorPreparer</code></font>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Type: <td> <code>{@link net.jini.security.ProxyPreparer}</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Default: <td> <code>new {@link
 *		net.jini.security.BasicProxyPreparer}()</code> 
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Description: <td> {@link java.rmi.activation.ActivationMonitor}
 *		proxy preparer 
 *  </table>
 *
 *  <table summary="Describes the systemPreparer configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *      <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *      systemPreparer</code></font>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Type: <td> <code>{@link net.jini.security.ProxyPreparer}</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Default: <td> <code>new {@link
 *		net.jini.security.BasicProxyPreparer}()</code> 
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Description: <td> {@link java.rmi.activation.ActivationSystem}
 *		proxy preparer 
 *  </table>
 *
 *  <table summary="Describes the unexportTimeout configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *      <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *      unexportTimeout</code></font>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Type: <td> <code>int</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Default: <td> <code>60000</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Description: <td> maximum time in milliseconds to wait for
 *		in-progress calls to finish before forcibly unexporting the
 *		group when going inactive 
 *  </table>
 *
 *  <table summary="Describes the unexportWait configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *      <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *      unexportWait</code></font>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Type: <td> <code>int</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Default: <td> <code>10</code>
 *    <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *      Description: <td> milliseconds to wait between unexport attempts
 *		when going inactive 
 *  </table>
 * 
 * <p>This class depends on its {@link #createGroup createGroup} method being
 * called to initialize the activation group. As such, this class cannot be
 * used in conjunction with the standard <code>rmid</code>.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 **/
public class ActivationGroupImpl extends ActivationGroup
    implements ServerProxyTrust
{
    private static final long serialVersionUID = 5758693559430427303L;

    private static final String PHOENIX = "com.sun.jini.phoenix";
    /** instance has not been created */
    private static final int UNUSED = 0;
    /** in the middle of createGroup */
    private static final int CREATING = 1;
    /** constructor (including activeGroup) has succeeded */
    private static final int CREATED = 2;
    /** createGroup has succeeded */
    private static final int ACTIVE = 3;
    /** group is inactive */
    private static final int INACTIVE = 4;
    /** parameter types for activatable constructor */
    private static Class[] paramTypes = {
	ActivationID.class, MarshalledObject.class
    };

    /* avoid serial field clutter */
    private static final ObjectStreamField[] serialPersistentFields =
						ObjectStreamClass.NO_FIELDS;

    private static int state = UNUSED;
    private static long incarnation;
    /** original unprepared group id */
    private static ActivationGroupID groupID;

    /** server LoginContext or null */
    private static LoginContext login;
    private static Exporter exporter;
    /** true if calls should be refused, false otherwise */
    private static boolean refuseCalls = false;
    /** monitor proxy preparer */
    private static ProxyPreparer monPreparer;
    private static ActivationMonitor monitor;
    /** timeout on wait for unexport to succeed */
    private static long unexportTimeout;
    /** timeout on wait between unexport attempts */
    private static long unexportWait;
    /** maps ActivationID to ActiveEntry */
    private static Map active = new HashMap();
    /** ActivationIDs with operations in progress */
    private static List lockedIDs = new ArrayList();
    /** true if group subject is inherited when creating activatable objects */
    private static boolean inheritGroupSubject;

    /** permission to check for monitor's activeObject call */
    private final static Permission activeObjectPermission =
        new MonitorPermission(
	    "java.rmi.activation.ActivationMonitor.activeObject");
    
    /** permission to check for monitor's activeObject call */
    private static Permission inactiveObjectPermission =
        new MonitorPermission(
	    "java.rmi.activation.ActivationMonitor.inactiveObject");
    
    /** proxy for this activation group */
    private ActivationInstantiator proxy;

    /**
     * Creates an {@link java.rmi.activation.ActivationGroup} instance and
     * returns it. An {@link ActivationGroupData} instance is extracted from
     * the initialization data, and a {@link Configuration} is obtained by
     * calling
     * {@link net.jini.config.ConfigurationProvider#getInstance
     * Configuration.Provider.getInstance} with the configuration options from
     * that instance. A {@link LoginContext} is obtained from the
     * <code>loginContext</code> configuration entry, if one exists; if the
     * value is not <code>null</code>, a login is performed on that context,
     * and the resulting {@link Subject} (set to be read-only) is used as the
     * subject when executing the rest of this method. The subject is also
     * used for all subsequent remote calls by this class to the
     * {@link ActivationMonitor}. The {@link ActivationSystem} proxy
     * (obtained from the <code>ActivationGroupID</code>) is passed to the
     * {@link ProxyPreparer} given by the <code>systemPreparer</code>
     * configuration entry, if one exists; a new
     * <code>ActivationGroupID</code> is constructed with the resulting proxy.
     * An {@link Exporter} instance is obtained from the
     * <code>instantiatorExporter</code> configuration entry, if one exists;
     * this exporter will be used (in the constructor of this class) to export
     * the group. A <code>ProxyPreparer</code> instance is obtained from the
     * <code>monitorPreparer</code> configuration entry, if one exists; this
     * preparer will be used (in the constructor of this class) to prepare the
     * <code>ActivationMonitor</code>. A call is then made to
     * {@link ActivationGroup#createGroup ActivationGroup.createGroup} with
     * the new group identifier, the activation group descriptor, and the
     * group incarnation number, and the result of that call is returned.
     *
     * @param id the activation group identifier
     * @param desc the activation group descriptor
     * @param incarnation the group's incarnation number (zero on initial
     * creation)
     * @return the created activation group
     * @throws ActivationException if a group already exists or if an
     * exception occurs during group creation
     */
    public static synchronized
	java.rmi.activation.ActivationGroup createGroup(
					      final ActivationGroupID id,
					      final ActivationGroupDesc desc,
					      final long incarnation)
        throws ActivationException
    {
	if (state != UNUSED) {
	    throw new ActivationException("group previously created");
	}
	try {
	    final Configuration config = getConfiguration(desc.getData());
	    login = (LoginContext) config.getEntry(
			PHOENIX, "loginContext", LoginContext.class, null);
	    if (login != null) {
		login.login();
	    }
		
	    inheritGroupSubject =
		((Boolean) config.getEntry(
		    PHOENIX, "inheritGroupSubject", boolean.class,
		    Boolean.FALSE)).booleanValue();
	    
	    return (java.rmi.activation.ActivationGroup) doAction(
		new PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			ProxyPreparer sysPreparer =
			    getPreparer(config, "systemPreparer");
			monPreparer = getPreparer(config,
						  "monitorPreparer");
			TcpServerEndpoint se =
			    TcpServerEndpoint.getInstance(0);
			Exporter defaultExporter =
			    new BasicJeriExporter(se, new AccessILFactory());
			exporter = (Exporter) config.getEntry(
				PHOENIX, "instantiatorExporter",
				Exporter.class, defaultExporter);
			if (exporter == null) {
			    exporter = new AlreadyExportedExporter();
			}
			refuseCalls =
			    !(exporter instanceof AlreadyExportedExporter);
			unexportTimeout = getInt(config, "unexportTimeout",
						 60000);
			unexportWait = getInt(config, "unexportWait", 10);
			ActivationSystem sys = (ActivationSystem)
			    sysPreparer.prepareProxy(id.getSystem());
			ActivationGroupImpl.incarnation = incarnation;
			groupID = id;
			state = CREATING;
			ActivationGroupID gid = (sys == id.getSystem() ?
						 id : new WrappedGID(id, sys));
			Object group = ActivationGroup.createGroup(
						    gid, desc, incarnation);
			state = ACTIVE;
			return group;
		    }
		});
	} catch (ActivationException e) {
	    throw e;
	} catch (Exception e) {
	    throw new ActivationException("creation failed", e);
	} finally {
	    if (state != ACTIVE) {
		checkInactiveGroup();
	    }
	}
    }

    /**
     * Returns the configuration obtained from the specified marshalled
     * object. 
     */
    private static Configuration getConfiguration(MarshalledObject mobj)
	throws ConfigurationException, IOException, ClassNotFoundException
    {
	ActivationGroupData data = (ActivationGroupData) mobj.get();
	ClassLoader cl = ActivationGroupImpl.class.getClassLoader();
	ClassLoader ccl = Thread.currentThread().getContextClassLoader();
	if (!covers(cl, ccl)) {
	    cl = ccl;
	}
	return ConfigurationProvider.getInstance(data.getConfig(), cl);
    }
    
    /**
     * Returns true if the first argument is either equal to, or is a
     * descendant of, the second argument.  Null is treated as the root of
     * the tree.
     */
    private static boolean covers(ClassLoader sub, ClassLoader sup) {
	if (sup == null) {
	    return true;
	} else if (sub == null) {
	    return false;
	}
	do {
	    if (sub == sup) {
		return true;
	    }
	    sub = sub.getParent();
	} while (sub != null);
	return false;
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
     * ActivationGroupID containing a prepared ActivationSystem proxy and
     * the original ActivationGroupID (with unprepared ActivationSystem
     * proxy), that writeReplaces itself to the original.
     */
    private static class WrappedGID extends ActivationGroupID {
	/** Original gid */
	private final ActivationGroupID id;
	/** Prepared system proxy */
	private final ActivationSystem sys;

	WrappedGID(ActivationGroupID id, ActivationSystem sys) {
	    super(sys);
	    this.id = id;
	    this.sys = sys;
	}

	/* override */
	public ActivationSystem getSystem() {
	    return sys;
	}

	private Object writeReplace() {
	    return id;
	}
    }

    /**
     * Creates an instance with the specified group identifier and
     * initialization data. This constructor must be called indirectly,
     * via {@link #createGroup createGroup}. By default, this instance
     * automatically exports itself as a {@link UnicastRemoteObject}. (This
     * is a limitation of the existing activation system design.) If an
     * {@link Exporter} was obtained by {@link #createGroup createGroup},
     * then this instance is unexported from the JRMP runtime and re-exported
     * using that exporter. (Any incoming remote calls received on the
     * original JRMP export before this instance can be unexported will be
     * refused with a security exception thrown.) The
     * {@link ActivationSystem#activeGroup activeGroup} method of the
     * activation system proxy (in the group identifier) is called to
     * make the group active. The returned {@link ActivationMonitor} proxy
     * is passed to the corresponding {@link ProxyPreparer} obtained by
     * <code>createGroup</code>. Note that after this constructor returns,
     * {@link ActivationGroup#createGroup ActivationGroup.createGroup} will
     * also call <code>activeGroup</code> (so the activation system must
     * accept idempotent calls to that method), but the
     * <code>ActivationMonitor</code> proxy returned by that call will not be
     * used.
     * 
     * @param id the activation group identifier
     * @param data group initialization data (ignored)
     * @throws RemoteException if the group could not be exported or
     * made active, or proxy preparation fails
     * @throws ActivationException if the constructor was not called
     * indirectly from <code>createGroup</code>
     */
    public ActivationGroupImpl(ActivationGroupID id, MarshalledObject data)
	throws ActivationException, RemoteException
    {
	super(id);
	synchronized (ActivationGroupImpl.class) {
	    if (state != CREATING) {
		throw new ActivationException("not called from createGroup");
	    }
	}
	if (refuseCalls) {
	    unexportObject(this, true);
	    refuseCalls = false;
	}
	proxy = (ActivationInstantiator) exporter.export(this);
	try {
	    monitor = (ActivationMonitor) monPreparer.prepareProxy(
			  id.getSystem().activeGroup(id, proxy, incarnation));
	    state = CREATED;
	} finally {
	    if (state != CREATED) {
		exporter.unexport(true);
	    }
	}
	monPreparer = null;
    }

    public TrustVerifier getProxyVerifier() {
	return new BasicProxyTrustVerifier(proxy);
    }
    
    /**
     * Exporter for an object that is already exported to JRMP.
     */
    private static class AlreadyExportedExporter implements Exporter {
	/**
	 * A strong reference to the impl is OK because ActivationGroup
	 * also holds a strong reference.
	 */
	private Remote impl;

	AlreadyExportedExporter() {
	}

	public synchronized Remote export(Remote impl) throws ExportException {
	    this.impl = impl;
	    try {
		return RemoteObject.toStub(impl);
	    } catch (NoSuchObjectException e) {
		throw new ExportException("no stub found", e);
	    }
	}

	public synchronized boolean unexport(boolean force) {
	    try {
		if (impl != null &&
		    !UnicastRemoteObject.unexportObject(impl, force))
		{
		    return false;
		}
	    } catch (NoSuchObjectException e) {
	    }
	    impl = null;
	    return true;
	}
    }

    /**
     * Returns the proxy for this remote object. Group creation was designed
     * to rely on automatic stub replacement (as provided by the JRMP runtime),
     * which is not supported by all exporters.
     *
     * @return the proxy for this remote object
     */
    protected Object writeReplace() {
	return proxy;
    }

    /*
     * Obtains a lock on the ActivationID id before returning. Allows only one
     * thread at a time to hold a lock on a particular id.  If the lock for id
     * is in use, all requests for an equivalent (in the .equals sense)
     * id will wait for the id to be notified and use the supplied id as the
     * next lock. The caller of "acquireLock" must execute the "releaseLock"
     * method" to release the lock and "notifyAll" waiters for the id lock
     * obtained from this method.  The typical usage pattern is as follows:
     *
     * acquireLock(id);
     * try {
     *    // do stuff pertaining to id...
     * } finally {
     *    releaseLock(id);
     * }
     */
    private void acquireLock(ActivationID id) {
	while (true) {
	    synchronized (id) {
		synchronized (lockedIDs) {
		    int index = lockedIDs.indexOf(id);
		    if (index < 0) {
			lockedIDs.add(id);
			return;
		    }
		    ActivationID lockedID =
			(ActivationID) lockedIDs.get(index);
		    if (lockedID != id) {
			// don't wait on an id that won't be notified
			id = lockedID;
			continue;
		    }
		}
		try {
		    id.wait();
		} catch (InterruptedException ignore) {
		}
	    }
	}
    }

    /*
     * Releases the id lock obtained via the "acquireLock" method and then
     * notifies all threads waiting on the lock.
     */
    private void releaseLock(ActivationID id) {
	synchronized (lockedIDs) {
	    id = (ActivationID) lockedIDs.remove(lockedIDs.indexOf(id));
	}
	synchronized (id) {
	    id.notifyAll();
	}
    }

    /**
     * Creates a new instance of an activatable remote object and returns
     * a marshalled object containing the activated object's proxy.
     *
     * <p>If an active object already exists for the specified identifier,
     * the existing marshalled object for it is returned.
     *
     * <p>Otherwise:
     *
     * <p>The class for the object is loaded by invoking {@link
     * RMIClassLoader#loadClass(String,String) RMIClassLoader.loadClass}
     * passing the class location (obtained by invoking {@link
     * ActivationDesc#getLocation getLocation} on the activation
     * descriptor) and the class name (obtained by invoking {@link
     * ActivationDesc#getClassName getClassName} on the activation
     * descriptor).
     *
     * <p>The new instance is constructed as follows. If the class defines
     * a constructor with two parameters of type {@link ActivationID} and
     * {@link MarshalledObject}, that constructor is called with the
     * specified activation identifier and the initialization data from the
     * specified activation descriptor. Otherwise, an
     * <code>ActivationException</code> is thrown.
     *
     * <p>If the class loader of the object's class is a descendant of the
     * current context class loader, then that class loader is set as the
     * context class loader when the constructor is called.
     *
     * <p>If the <code>inheritGroupSubject</code> configuration entry is
     * <code>true</code> then the constructor is invoked in an action
     * passed to the {@link Security#doPrivileged Security.doPrivileged}
     * method; otherwise the constructor is invoked in an action passed to
     * the {@link AccessController#doPrivileged
     * AccessController.doPrivileged} method.
     *
     * <p>A proxy for the newly created instance is returned as follows:
     *
     * <ul><li>If the newly created instance implements {@link
     * ProxyAccessor}, a proxy is obtained by invoking the {@link
     * ProxyAccessor#getProxy getProxy} method on that instance. If the
     * obtained proxy is not <code>null</code>, that proxy is returned in a
     * <code>MarshalledObject</code>; otherwise, an
     * <code>ActivationException</code> is thrown.
     *
     * <li>If the newly created instance does not implement
     * <code>ProxyAccessor</code>, the instance is returned in a
     * <code>MarshalledObject</code>.  In this case, the instance must be
     * serializable, and marshalling the instance must produce a suitable
     * proxy for the remote object (for example, the object implements
     * {@link java.io.Serializable} and defines a <code>writeReplace</code>
     * method that returns the object's proxy).
     * </ul>
     *
     * <p>If both the remote object and the activation group are exported
     * using JRMP, then automatic stub replacement will produce the desired
     * result, but otherwise the remote object implementation must provide
     * a means for this group to obtain its proxy as indicated above.
     *
     * @throws ActivationException if the object's class could not be
     * loaded, if the loaded class does not define the appropriate
     * constructor, or any exception occurs activating the object
     **/
    public MarshalledObject newInstance(final ActivationID id,
					final ActivationDesc desc)
	throws ActivationException
    {
	synchronized (ActivationGroupImpl.class) {
	    if (refuseCalls) {
		throw new SecurityException("call refused");
	    }
	}

	acquireLock(id);
	try {
	    ActiveEntry entry;
	    synchronized (ActivationGroupImpl.class) {
		if (state != ACTIVE) {
		    throw new InactiveGroupException("group not active");
		}
		entry = (ActiveEntry) active.get(id);
	    }
	    if (entry != null) {
		return entry.mobj;
	    }

	    String className = desc.getClassName();
	    final Class cl = RMIClassLoader.loadClass(desc.getLocation(),
						      className);
	    final Thread t = Thread.currentThread();
	    final ClassLoader savedCcl = t.getContextClassLoader();
	    final ClassLoader ccl =
		covers(cl.getClassLoader(), savedCcl) ?
		cl.getClassLoader() : savedCcl;

	    Remote impl = null;

	    /*
	     * Fix for 4164971: allow non-public activatable class
	     * and/or constructor, create the activatable object in a
	     * privileged block
	     */
	    try {
		PrivilegedExceptionAction action =
		   new PrivilegedExceptionAction() {
		      public Object run() throws InstantiationException,
			  NoSuchMethodException, IllegalAccessException,
			  InvocationTargetException, ActivationException
		      {
			  Object[] params = new Object[] {id, desc.getData()};
			  Constructor constructor =
			      cl.getDeclaredConstructor(paramTypes);
			  constructor.setAccessible(true);
			  try {
			      /*
			       * Fix for 4289544: make sure to set the
			       * context class loader to be the class
			       * loader of the impl class before
			       * constructing that class.
			       */
			      t.setContextClassLoader(ccl);
			      return constructor.newInstance(params);
			  } finally {
			      t.setContextClassLoader(savedCcl);
			  }
		      }
		   };

		/*
		 * The activatable object is created is in a doPrivileged
		 * block to protect against user code which might have set
		 * a global socket factory (in which case application code
		 * would be on the stack).
		 */
		impl = (Remote) (inheritGroupSubject ?
				 Security.doPrivileged(action) :
				 AccessController.doPrivileged(action));
			
	    } catch (PrivilegedActionException pae) {
		throw pae.getException();
	    }

	    entry = new ActiveEntry(impl);
	    synchronized (ActivationGroupImpl.class) {
		active.put(id, entry);
	    }
	    return entry.mobj;
	} catch (NoSuchMethodException e) {
	    /* user forgot to provide activatable constructor? */
	    throw new ActivationException(
		"activation constructor not defined", e);
	} catch (NoSuchMethodError e) {
	    /* code recompiled and user forgot to provide
	     *  activatable constructor?
	     */
	    throw new ActivationException(
		"activation constructor not defined", e);
	} catch (InvocationTargetException e) {
	    throw new ActivationException("exception constructing object",
					  e.getTargetException());
	} catch (ActivationException e) {
	    throw e;
	} catch (Exception e) {
	    throw new ActivationException("unable to activate object", e);
	} finally {
	    releaseLock(id);
	    checkInactiveGroup();
	}
    }

    /**
     * Attempts to make the remote object that is associated with the
     * specified activation identifier, and that was exported as a JRMP
     * {@link java.rmi.activation.Activatable} object, inactive. This method
     * calls <code>Activatable.unexportObject</code> with the active remote
     * object and <code>false</code>, to unexport the object. If that call
     * returns <code>false</code>, this method returns <code>false</code>.
     * If that call returns <code>true</code>, the object is marked inactive
     * in this virtual machine, the superclass <code>inactiveObject</code>
     * method is called with the same activation identifier, with the
     * <code>ActivationMonitor</code> constraints (if any) set as
     * contextual client constraints, and with the group's subject (if any)
     * set as the executing subject, and this method returns <code>true</code>.
     *
     * @param id the activation identifier
     * @return <code>true</code> if the object was successfully made
     * inactive; <code>false</code> otherwise
     * @throws UnknownObjectException if the object is not known to be
     * active (it may already be inactive)
     * @throws ActivationException if an activation error occurs
     * @throws InactiveGroupException if the group is inactive
     * @throws RemoteException if the remote call to the activation
     * monitor fails
     *
     * @throws SecurityException if a security manager exists and invoking
     * its {@link SecurityManager#checkPermission checkPermission} method
     * with the permission <code>{@link MonitorPermission}("java.rmi.activation.ActivationMonitor.inactiveObject")</code>
     * throws a <code>SecurityException</code> 
     **/
    public boolean inactiveObject(final ActivationID id)
	throws ActivationException, RemoteException
    {
	SecurityManager security = System.getSecurityManager();
	if (security != null) {
	    security.checkPermission(inactiveObjectPermission);
	}
	acquireLock(id);
	try {
	    ActiveEntry entry;
	    synchronized (ActivationGroupImpl.class) {
		if (state != ACTIVE) {
		    throw new InactiveGroupException("group not active");
		}
		entry = (ActiveEntry) active.get(id);
	    }
	    if (entry == null) {
		// REMIND: should this be silent?
		throw new UnknownObjectException("object not active");
	    }

	    try {
		if (!Activatable.unexportObject(entry.impl, false)) {
		    return false;
		}
	    } catch (NoSuchObjectException allowUnexportedObjects) {
	    }

	    try {
		doAction(new PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			monitor.inactiveObject(id);
			return null;
		    }
		});
	    } catch (UnknownObjectException allowUnregisteredObjects) {
	    }

	    synchronized (ActivationGroupImpl.class) {
		active.remove(id);
	    }
	} finally {
	    releaseLock(id);
	    checkInactiveGroup();
	}

	return true;
    }

    /**
     * Attempts to make the remote object that is associated with the
     * specified activation identifier, and that was exported through the
     * specified exporter, inactive. The {@link Exporter#unexport unexport}
     * method of the specified exporter is called with <code>false</code>
     * as an argument. If that call returns <code>false</code>, this method
     * returns <code>false</code>. If that call returns <code>true</code>,
     * the object is marked inactive in this virtual machine, the
     * superclass <code>inactiveObject</code> method is called with the
     * activation identifier, with the <code>ActivationMonitor</code>
     * constraints (if any) set as contextual client constraints, and with
     * the group's subject (if any) set as the executing subject, and this
     * method returns <code>true</code>.
     *
     * @param id the activation identifier
     * @param exporter the exporter to use to unexport the object
     * @return <code>true</code> if the object was successfully made
     * inactive; <code>false</code> otherwise
     * @throws UnknownObjectException if the object is not known to be
     * active (it may already be inactive)
     * @throws ActivationException if an activation error occurs
     * @throws InactiveGroupException if the group is inactive
     * @throws RemoteException if the remote call to the activation monitor
     * fails
     * @throws SecurityException if a security manager exists and invoking
     * its {@link SecurityManager#checkPermission checkPermission} method
     * with the permission <code>{@link MonitorPermission}("java.rmi.activation.ActivationMonitor.inactiveObject")</code>
     * throws a <code>SecurityException</code> 
     **/
    public boolean inactiveObject(final ActivationID id, Exporter exporter)
	throws ActivationException, RemoteException
    {
	SecurityManager security = System.getSecurityManager();
	if (security != null) {
	    security.checkPermission(inactiveObjectPermission);
	}
	acquireLock(id);
	try {
	    ActiveEntry entry;
	    synchronized (ActivationGroupImpl.class) {
		if (state != ACTIVE) {
		    throw new InactiveGroupException("group not active");
		}
		entry = (ActiveEntry) active.get(id);
	    }
	    if (entry == null) {
		// REMIND: should this be silent?
		throw new UnknownObjectException("object not active");
	    }

	    if (!exporter.unexport(false)) {
		return false;
	    }

	    try {
		doAction(new PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			monitor.inactiveObject(id);
			return null;
		    }
		});
	    } catch (UnknownObjectException allowUnregisteredObjects) {
	    }

	    synchronized (ActivationGroupImpl.class) {
		active.remove(id);
	    }
	} finally {
	    releaseLock(id);
	    checkInactiveGroup();
	}

	return true;
    }

    /*
     * Determines if the group has become inactive and if so,
     * marks it as such, notifies the daemon, and unexports the group.
     */
    private static void checkInactiveGroup() {
	synchronized (ActivationGroupImpl.class) {
	    if (state == ACTIVE) {
		if (!active.isEmpty() || !lockedIDs.isEmpty()) {
		    return;
		}
		state = INACTIVE;
	    } else if (state == INACTIVE) {
		return;
	    } else if (state == CREATED) {
		state = UNUSED;
	    } else {
		if (login != null) {
		    try {
			login.logout();
		    } catch (LoginException e) {
		    }
		    login = null;
		}
		state = UNUSED;
		return;
	    }
	}
	try {
	    doAction(new PrivilegedExceptionAction() {
		public Object run() throws Exception {
		    monitor.inactiveGroup(groupID, incarnation);
		    return null;
		}
	    });
	} catch (Exception ignoreDeactivateFailure) {
	}
	Runnable action = new Runnable() {
	    public void run() {
		long stop = System.currentTimeMillis() + unexportTimeout;
		// allow a failing newInstance call to finish first
		boolean force = false;
		while (!exporter.unexport(force)) {
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
		if (login != null) {
		    try {
			login.logout();
		    } catch (LoginException e) {
		    }
		}
	    }
	};
	if (state == UNUSED) {
	    action.run();
	} else {
	    Executor systemThreadPool =
		(Executor) AccessController.doPrivileged(
		    (login == null) ?
		    (PrivilegedAction) new GetThreadPoolAction(false) :
		    new PrivilegedAction() {
			public Object run() {
			    return Subject.doAsPrivileged(
					      login.getSubject(),
					      new GetThreadPoolAction(false),
					      null);
			}
		});
	    systemThreadPool.execute(action, "UnexportGroup");
	}
    }

    /**
     * Marks the object as active in this virtual machine, and calls the
     * superclass <code>activeObject</code> method with the same arguments,
     * with the <code>ActivationMonitor</code> constraints (if any) set as
     * contextual client constraints, and with the group's subject (if any)
     * set as the executing subject. Any <code>RemoteException</code>
     * thrown by this call is caught and ignored. If the object is already
     * marked as active in this virtual machine, this method simply
     * returns.
     *
     * @param id the activation identifier
     * @param impl the active remote object
     * @throws UnknownObjectException if no object is registered under
     * the specified activation identifier
     * @throws ActivationException if an activation error occurs
     * @throws InactiveGroupException if the group is inactive
     * @throws SecurityException if a security manager exists and invoking
     * its {@link SecurityManager#checkPermission checkPermission} method
     * with the permission <code>{@link MonitorPermission}("java.rmi.activation.ActivationMonitor.activeObject")</code>
     * throws a <code>SecurityException</code> 
     **/
    public void activeObject(final ActivationID id, Remote impl)
	throws ActivationException
    {
	SecurityManager security = System.getSecurityManager();
	if (security != null) {
	    security.checkPermission(activeObjectPermission);
	}
	final ActiveEntry entry = new ActiveEntry(impl);
	acquireLock(id);
	try {
	    synchronized (ActivationGroupImpl.class) {
		if (state != ACTIVE) {
		    throw new InactiveGroupException("group not active");
		}
		if (active.containsKey(id)) {
		    return;
		}
		active.put(id, entry);
	    }
	    // created new entry, so inform monitor of active object
	    try {
		doAction(new PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			monitor.activeObject(id, entry.mobj);
			return null;
		    }
		});
	    } catch (RemoteException ignore) {
		// daemon can still find it by calling newInstance
	    }
	} finally {
	    releaseLock(id);
	    checkInactiveGroup();
	}
    }

    /**
     * Execute the specified action on behalf of the server subject without
     * requiring the caller to have doAsPrivileged permission.
     */
    private static Object doAction(final PrivilegedExceptionAction action)
	throws ActivationException, RemoteException
    {
	try {
	    if (login == null) {
		return AccessController.doPrivileged(action);
	    } else {
		return AccessController.doPrivileged(
		    new PrivilegedExceptionAction() {
		        public Object run() throws Exception {
			    try {
				return Subject.doAsPrivileged(
					   login.getSubject(), action, null);
			    } catch (PrivilegedActionException e) {
				throw e.getException();
			    }
			}
		});
	    }
	} catch (PrivilegedActionException e) {
	    Exception ex = e.getException();
	    if (ex instanceof RemoteException) {
		throw (RemoteException) ex;
	    } else if (ex instanceof ActivationException) {
		throw (ActivationException) ex;
	    } else {
		throw new ActivationException("unexpected failure", ex);
	    }
	}
    }

    /**
     * Entry in table for active object.
     */
    private static class ActiveEntry {
	Remote impl;
	MarshalledObject mobj;

	ActiveEntry(Remote impl) throws ActivationException {
	    this.impl = impl;
	    try {
		Object proxy ;
		if (impl instanceof ProxyAccessor) {
		    proxy = ((ProxyAccessor) impl).getProxy();
		    if (proxy == null) {
			throw new ActivationException(
			    "ProxyAccessor.getProxy returned null");
		    }
		} else {
		    proxy = impl;
		}
		this.mobj = new MarshalledObject(proxy);
	    } catch (IOException e) {
		throw new ActivationException(
		    "failed to marshal remote object", e);
	    }
	}
    }
}
