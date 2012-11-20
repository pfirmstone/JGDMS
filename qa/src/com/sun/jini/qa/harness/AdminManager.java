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

package com.sun.jini.qa.harness;

import java.io.File;
import java.lang.reflect.Constructor;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.admin.Administrable;
import net.jini.core.lookup.ServiceRegistrar;

/**
 * A factory for, and manager of, <code>Admin</code>s.
 * This class will create admins for services based on the value
 * of a <code>serviceName</code> string. References to
 * these admins are retained in a table; methods exist for accessing
 * these admins, destroying individual services, and destroying
 * all services.
 * <p>
 * This manager includes explicit support for managing a 'global'
 * shared activatable group and a 'global' shared non-activatable
 * group. If this manager is used to create a a
 * <code>SharedGroupAdmin</code> or a
 * <code>NonActivatableGroupAdmin</code>, the manager will retain a
 * reference to it. Once a shared group is created, all services
 * managed by this class will be registered to run in the appropriate
 * group.  If the manager was already maintaining a group, the new
 * group replaces the current group. If this manager is never called
 * to manage a shared group, then each service must create a group of
 * it's own.
 * <p>
 * If the test is distributed then the admin created for an activatable
 * or non-activatable service may be a RemoteServiceAdmin, and the
 * start method of this admin will cause the service to be started
 * on a host other than the test host.
 * <p>
 * The type of admin returned by the <code>getAdmin</code> methods is based
 * on the value of the service property named <code>type</code>. 
 * Specifically: 
 * <ul> 
 * <li>if <code>type</code> has the value <code>"group"</code>, then
 *     a <code>SharedGroupAdmin</code> is returned. A reference to the
 *     groups admin is maintained and passed to the constructor of any
 *     subsequently created <code>ActivatableServiceStarterAdmin</code>s.
 * <li>if <code>type</code> has the value <code>"nonactivatablegroup"</code>
 *     a <code>NonActivatableGroupAdmin</code> is returned. A reference to
 *     the groups admin is maintained and passed to the constructor of any
 *     subsequently created <code>NonActivatableServiceStarterAdmin</code>s.
 * <li>if <code>type</code> has the value <code>"rmid"</code> or
 *     <code>"phoenix"</code>, then  an <code>ActivationSystemAdmin</code>
 *     is returned
 * <li>if <code>type</code> has the value <code>"classServer"</code>
 *     then a <code>ClassServerAdmin</code> is returned.
 * <li>if <code>type</code> has the value <code>"running"</code>
 *     then a <code>RunningServiceAdmin</code> is returned.  
 * <li>if <code>type</code> has the value <code>"persistent"</code>
 *     or <code>"transient"</code> 
 *     then a <code>NonActivatableServiceStarterAdmin</code> is returned if
 *     the service is to be run on the local host, or a 
 *     <code>RemoteServiceAdmin</code>
 *     is returned if the service is to be run on a different host. See
 *     <code>QAConfig.getServiceHost</code> for details about the host
 *     selection policy. If a remote admin is returned, the service started
 *     on the remote system will be of the appropriate persistent/transient type.
 * <li>if <code>type</code> is undefined or has the value 
 *     <code>"activatable"</code>
 *     then an <code>ActivatableServiceStarterAdmin</code> is returned if
 *     the service is to be run on the local host, or a 
 *     <code>RemoteServiceAdmin</code>
 *     is returned if the service is to be run on a different host. See
 *     <code>QAConfig.getServiceHost</code> for details about the host
 *     selection policy. If a remote admin is returned, the service started
 *     on the remote system will be activatable.
 * <li>if none of these conditions are satisfied, a 
 *     <code>TestException</code> is thrown.
 * </ul>
 * The logger named <code>com.sun.jini.qa.harness.service</code> is used
 * to log debug information
 *
 * <table border=1 cellpadding=5>
 *
 *<tr> <th> Level <th> Description
 *
 *<tr> <td> FINE <td>to log starting a service, to log destroying a service,
 *                   result (or problem) calling destroy
 *</table> <p> 
 */
public class AdminManager {

    private static String SERVICEREGISTRAR = 
	"net.jini.core.lookup.ServiceRegistrar";

    /** the logger for this class */
    private static Logger logger =
	Logger.getLogger("com.sun.jini.qa.harness");

    /** A mapping of service name prefixes to their index counters. */    
    private final Map serviceCounters  = new HashMap();
    
    /** The set of admins to be managed by this manager. */
    private final Set createdAdminSet   = new HashSet();
    
    /** The <code>QAConfig</code> object */
    private final QAConfig config;

    /** the admin for the shared group managed by this class. */
    private volatile SharedGroupAdmin sharedGroupAdmin = null;

    /** The admin for the shared non-activatable group */
    private volatile NonActivatableGroupAdmin nonActivatableGroupAdmin = null;
    
    /**
     * Construct an <code>AdminManager</code>. 
     *
     * @param config the configuration object for this run
     */
    public AdminManager(QAConfig config) {
	this.config = config;
    }

    /**
     * Get the admin associated with the given service name. The host to run 
     * the service on is based on the host selection policy. This method is
     * called on the master host; the service instance number is incremented
     * automatically.
     *
     * @param serviceName a string identifying the service, which is used as 
     *        a base string for generating well-known service property
     *        names.
     *
     * @return an admin for the service identified by the prefix. This
     *         method never returns <code>null</code>.
     *
     * @throws TestException if the prefix cannot be resolved to identify one
     *                       of the admin types.
     * @throws  NullPointerException if <code>serviceName</code> is null
     */
    public Admin getAdmin(String serviceName) throws TestException {
	return getAdmin(serviceName, null);
    }

    /**
     * Get the admin associated with the given service name. This method is
     * called on the master host; the service instance number is incremented
     * automatically.
     *
     * @param serviceName a string identifying the service, which is used as 
     *        a base string for generating well-known service property
     *        names.
     * @param host the host to run the service on, or <code>null</code>
     *             to select a host based on the host selection policy.
     *
     * @return an admin for the service identified by the prefix. This
     *         method never returns <code>null</code>.
     *
     * @throws TestException if the prefix cannot be resolved to identify one
     *                       of the admin types.
     * @throws  NullPointerException if <code>serviceName</code> is null
     */
    public Admin getAdmin(String serviceName, String host) 
	                                       throws TestException
    {
	if (serviceName == null) {
	    throw new NullPointerException("null passed to getAdmin");
	}
	int counter = nextIndex(serviceName, true);
	return getAdmin(serviceName, counter, host);
    }

    /**
     * Get the admin associated with the given service name. This variation
     * is called by the slave test with a counter value supplied by
     * the slave request object.
     *
     * @param serviceName a string identifying the service, which is used as 
     *        a base string for generating well-known service property
     *        names.
     * @param counter the instance counter for this service
     *
     * @return an admin for the service identified by the prefix. This
     *         method never returns <code>null</code>.
     *
     * @throws TestException if the prefix cannot be resolved to identify one
     *                       of the admin types.
     * @throws  NullPointerException if <code>serviceName</code> is null
     */
    Admin getAdmin(String serviceName, int counter)
	                       throws TestException
    {
	return getAdmin(serviceName, counter, null);
    }

    /**
     * Get the admin associated with the given service name.
     *
     * @param serviceName a string identifying the service, which is used as 
     *        a base string for generating well-known service property
     *        names.
     * @param counter the instance counter for this service
     * @param host the host to run the service on, or <code>null</code>
     *             to select a host based on the host selection policy.
     *
     * @return an admin for the service identified by the prefix. This
     *         method never returns <code>null</code>.
     *
     * @throws TestException if the prefix cannot be resolved to identify one
     *                       of the admin types.
     * @throws  NullPointerException if <code>serviceName</code> is null
     */
    private Admin getAdmin(String serviceName, int counter, String host)
	                       throws TestException
    {
	logger.log(Level.FINEST, 
		   "getAdmin called with prefix " + serviceName);
	if (serviceName == null) {
	    throw new NullPointerException("null passed to getAdmin");
	}
	Admin admin = null;

	setServiceType(serviceName); //set type property in dynamic props

	if (isServiceSuppliedAdmin(serviceName)) {
	    String serviceHost = config.getServiceHost(serviceName,
						       counter, 
						       host);
	    if (serviceHost != null) {
		admin = new RemoteServiceAdmin(serviceHost, 
					       config,
					       serviceName,
					       counter);
	    } else {
		admin = getServiceSuppliedAdmin(serviceName, counter);
	    }
	} else if (isSharedGroup(serviceName)) {
	    /*
	     * a shared group is just a specific activatable service, so
	     * this case must be handled before the general activatable service case
	     */
	    if (sharedGroupAdmin != null) {
		logger.log(Level.FINE, 
			   "Warning: replacing default shared group");
	    }
	    sharedGroupAdmin = new SharedGroupAdmin(config, 
						    serviceName,
						    0,
						    this);
	    admin = sharedGroupAdmin;
	} else if (isNonActivatableGroup(serviceName)) {
	    if (nonActivatableGroupAdmin != null) {
		logger.log(Level.FINE, 
			   "Warning: replacing default nonactivatable group");
	    }
	    nonActivatableGroupAdmin = new NonActivatableGroupAdmin(config, 
						    serviceName,
						    0);
	    admin = nonActivatableGroupAdmin;
	} else if (isActivationSystem(serviceName)) {
	    admin = new ActivationSystemAdmin(config,
					      serviceName,
					      counter);
	} else if (isClassServer(serviceName)) {
	    admin = new ClassServerAdmin(config, serviceName, counter);
	} else if (isActivatable(serviceName)) {
	    String serviceHost = config.getServiceHost(serviceName,
						       counter, 
						       host);
	    if (serviceHost != null) {
		admin = new RemoteServiceAdmin(serviceHost, 
					       config,
					       serviceName,
					       counter);
	    } else {
		admin = new ActivatableServiceStarterAdmin(config, 
							   serviceName, 
							   counter,
							   this);
	    }
        } else if (isNonActivatable(serviceName)) { 
	    // always returns null on slave systems
	    String serviceHost = config.getServiceHost(serviceName, 
						       counter, 
						       host);
	    if (serviceHost != null) {
		admin = new RemoteServiceAdmin(serviceHost, 
					       config,
					       serviceName,
					       counter);
	    } else {
		admin = new NonActivatableServiceStarterAdmin(config, 
							      serviceName, 
							      counter,
							      this);
	    }
	} else if (isRunning(serviceName)) {
	    admin = new RunningServiceAdmin(config, 
					    serviceName, 
					    counter);
	} else {
	    throw new TestException("Could not create an admin for "
				  + "serviceName '" 
				  +  serviceName + "'");
	}
        synchronized (createdAdminSet){
            createdAdminSet.add(admin);
        }
	return admin;
    }

    /**
     * Return a flag indicating whether a service-supplied admin should be used
     * to start the service. This is assumed to be the case if a test property
     * named <code>serviceName</code> + <code>.adminName</code> is defined.
     *
     * @return true if a service-supplied admin should be used
     */
    private boolean isServiceSuppliedAdmin(String serviceName) {
	return config.getStringConfigVal(serviceName + ".adminName", null) != null;
    }

    /**
     * Get the service-supplied admin instance to use to start the service. The class name of
     * the admin is supplied via the <code>serviceName</code> + <code>.adminName</code> test
     * property. However, if <code>com.sun.jini.qa.harness.serviceMode<code> is defined and
     * its value (<code>mode</code>) has a corresponding entry named 
     * <code>serviceName</code> + . + <code>mode</code> + <code>.adminName</code>, then
     * use the value of that property as the admin class name. The service-supplied
     * class must extend one of <code>ActivatableServiceStarterAdmin</code> or
     * <code>NonActivatableServiceStarterAdmin</code>, and must supply a 4-arg constructor
     * accepting parameters of type QAConfig, String, int, and AdminManager.
     *
     * @param serviceName the service name
     * @param counter the instance counter of this service
     * @return the admin
     * @throws TestException if the admin cannot be instantiated or is not an allowed type
     */
    private Admin getServiceSuppliedAdmin(String serviceName, int counter) throws TestException {
	String adminName = config.getStringConfigVal(serviceName + ".adminName", null);
        logger.log(Level.FINEST, "adminName: {0}", adminName);
	String mode = 
	    config.getStringConfigVal("com.sun.jini.qa.harness.serviceMode", 
				      null);
	if (mode != null) {
	    String modeName = serviceName + "." + mode + ".adminName";
	    String adminByMode = config.getStringConfigVal(modeName, null);
	    if (adminByMode != null) {
		adminName = adminByMode;
	    }
	}
	try {
	    Class c = Class.forName(adminName, true, config.getTestLoader());
	    if (! (ActivatableServiceStarterAdmin.class.isAssignableFrom(c)
		         || NonActivatableServiceStarterAdmin.class.isAssignableFrom(c))) {
		throw new TestException("User supplied admin must extend (Non)ActivatableServiceAdmin");
	    }
	    Constructor constructor = c.getConstructor(new Class[]{QAConfig.class,
								   String.class,
								   int.class,
								   AdminManager.class});
	    Object instance = constructor.newInstance(new Object[]{config,
								   serviceName,
								   new Integer(counter),
								   this});
	    return (Admin) instance;
	} catch (Exception e) {
	    throw new TestException("Failed to instantiate service-supplied admin", e);
	}
    }

    /**
     * Obtain the admin associated with <code>proxy</code>. The
     * service must have been started by the <code>startService</code>
     * method of this class in order to obtain the admin.
     *
     * @param proxy the service proxy bound to the desired admin
     *
     * @return the <code>Admin</code> to which the
     *         proxy is bound. <code>null</code> is returned if the
     *         admin cannot be found.
     */
    public Admin getAdmin(Object proxy) {
        synchronized (createdAdminSet){
            Iterator it = createdAdminSet.iterator();
            while (it.hasNext()) {
                Admin ad = (Admin) it.next();
                Object p = ad.getProxy();
                if (p != null && p.equals(proxy)) {
                    return ad;
                }
            }
            return null;
        }
    }

    /**
     * Obtain the next index for the given <code>key</code>. Indices start
     * at zero and increment on each call for the same object. Key
     * comparisons are based on <code>equals</code>. The 
     * <code>postTestCleanup</code> method resets all counters to zero.
     *
     * @param key  the object for which a new index is needed
     * @param save if <code>true</code>, update counters table
     * @return     the next index value. The first index returned is the value 
     *             zero.
     */
    private int nextIndex(Object key, boolean save) {
        int newVal;
        synchronized (serviceCounters) {
            Object valObj = serviceCounters.get(key);
            int oldVal =
                 ((valObj == null) ? -1 
                                   : ((int)(((Integer)valObj).intValue())));
            newVal = 1 + oldVal;
            if (save) {
		serviceCounters.put(key,new Integer(newVal));
	    }
        }
        return newVal;
    }

    /**
     * Test whether the given <code>prefix</code> represents an
     * activatable service. A service is assumed to be activatable
     * if a value for <code>prefix.running</code> does not exist and
     * a value for <code>prefix.type</code> has the value 
     * "<code>activatable</code>" or does not exist.
     *
     * @param prefix the service prefix
     *
     * @return <code>true</code> if <code>prefix</code> represents an 
     *         activatable service
     */
    private boolean isActivatable(String prefix) {
        if (prefix == null) {
            return false;
        }
	int index = nextIndex(prefix, false);
	// if a running service is provided, not activatable
	if (config.getServiceStringProperty(prefix, "running", index) != null) {
	    return false;
	}
	// if type is undefined or "activatable", it's activatable
	String type =  config.getServiceStringProperty(prefix, 
						       "type",
						       index, 
						       "activatable");
	return type.equals("activatable");
    }

    /**
     * Test whether the given <code>prefix</code> represents a non-activatable
     * service. A service is assumed to be non-activatable if a configuration
     * value for <code>prefix.type</code> exists and has the value
     * "<code>persistent</code>" or <code>"transient"</code>, but a value for
     * <code>prefix.running</code> does not exist.
     *
     * @param prefix the service prefix
     *
     * @return <code>true</code> if <code>prefix</code> represents a
     *         non-activatable service
     */
    private boolean isNonActivatable(String prefix) {
        if (prefix == null) {
            return false;
        }
	int index = nextIndex(prefix, false);
	// if a running service is provided, not non-activatable
	if (config.getServiceStringProperty(prefix, "running", index) != null) {
	    return false;
	}
	// must have one of the non activatable types
	String type = config.getServiceStringProperty(prefix, "type", index);
	return type != null
	            && (type.equals("persistent") || type.equals("transient"));
    }

    /**
     * Test whether the given <code>prefix</code> represents a
     * running service. A service is assumed to be running
     * if a configuration value for <code>prefix.running</code> 
     * exists. This method does not verify the correctness of the value.
     *
     * @param prefix the service prefix
     *
     * @return <code>true</code> if <code>prefix</code> represents a
     *         running service
     */
    private boolean isRunning(String prefix) {
        if (prefix == null) {
            return false;
        }
	int index = nextIndex(prefix, false);
	// if a locator is provided, the service is running 
	return config.getServiceStringProperty(prefix, 
					       "running", 
					       index) != null;
    }

    /**
     * Determine whether <code>prefix</code> corresponds to a
     * shared group to be managed. The service is assumed to 
     * be a shared group if the value of <code>prefix.type</code> 
     * exists and is "<code>group</code>".
     *
     * @param prefix the service prefix
     * @return true if the prefix identifies a shared group
     */
    private boolean isSharedGroup(String prefix) {
        if (prefix == null) {
            return false;
        }
	int index = nextIndex(prefix, false);
	// if an impl is not provided, it's not a shared group
	String type = config.getServiceStringProperty(prefix, "type", index);
	return type != null && type.equals("group");
    }

    private boolean isNonActivatableGroup(String prefix) {
        if (prefix == null) {
            return false;
        }
	int index = nextIndex(prefix, false);
	// if an impl is not provided, it's not a shared group
	String type = config.getServiceStringProperty(prefix, "type", index);
	return type != null && type.equals("nonactivatablegroup");
    }

    /**
     * Determine whether <code>prefix</code> corresponds to an
     * activation system to be managed. The service is assumed
     * to be an activation system if a value for the <code>prefix.type</code>
     * exists and is "<code>rmid</code>" or "<code>phoenix</code>".
     *
     * @param prefix the service prefix
     * @return true if the prefix identifies an activation system
     */
    private boolean isActivationSystem(String prefix) {
        if (prefix == null) {
            return false;
        }
	int index = nextIndex(prefix, false);
	// if an activation command line is specified, its an act system 
	String type = config.getServiceStringProperty(prefix, "type", index);
	return type != null && 
	       (type.equals("rmid") || type.equals("phoenix"));
    }	

    /**
     * Determine whether <code>prefix</code> corresponds to an
     * class server to be managed. The service is assumed to be
     * a class server if a value for the <code>prefix.type</code>
     * configuration value exists and has the value 
     * "<code>classServer</code>".
     *
     * @param prefix the service prefix
     * @return true if the prefix identifies a class server
     */
    private boolean isClassServer(String prefix) {
        if (prefix == null) {
            return false;
        }
	int index = nextIndex(prefix, false);
	// if a class server class is identified, its a class server 
	String type = config.getServiceStringProperty(prefix, 
						      "type", 
						      index);
	return type != null && type.equals("classServer");
    }	

    /**
     * Start the given service and return a reference to the proxy for that
     * service. If <code>transformer</code> is non-null and the admin is an
     * instance of <code>AbstractServiceAdmin</code>, that transformer is
     * registered with the admin before it's start method is called.
     * 
     * @param serviceName The service starter prefix used to construct
     *                      the well-known service starter property names
     * @param transformer   A <code>ServiceDescriptorTransformer</code> to
     *                      register with the admin.
     * 
     * @return              proxy for the service described by
     *                      <code>serviceName</code>.
     * 
     * @throws RemoteException  if a communication error occurs while
     *                          attempting to start the service
     * @throws TestException    if an admin cannot be created for the service
     */
    public Object startTransformedService(String serviceName, 
			       ServiceDescriptorTransformer transformer) 
	throws RemoteException, TestException
    {
	logger.log(Level.FINE, "starting " + serviceName);
	Admin admin = getAdmin(serviceName); // never returns null
	if (transformer != null && admin instanceof AbstractServiceAdmin) {
	    AbstractServiceAdmin a = (AbstractServiceAdmin) admin;
	    a.registerDescriptorTransformer(transformer);
	}
	admin.start();
        return admin.getProxy();
    }

    /**
     * Start the given service and return a reference to the proxy for that
     * service.
     * 
     * @param serviceName The service starter prefix used to construct
     *                    the well-known service starter property names
     * @param host        The host to start the service on, or null to
     *                    select a host based on the host selection policy
     * @return            proxy for the service described by
     *                    <code>serviceName</code>.
     * 
     * @throws RemoteException  if a communication error occurs while
     *                          attempting to start the service
     * @throws TestException    if an admin cannot be created for the service
     */
    public Object startService(String serviceName, String host) 
	throws RemoteException, TestException
    {
	logger.log(Level.FINE, "starting {0}", serviceName);
	Admin admin = getAdmin(serviceName, host); // never returns null
	admin.start();
        return admin.getProxy();
    }

    /**
     * Start the given service and return a reference to the proxy for that
     * service. The host to run the service on is selected based on the
     * current host selection policy.
     * 
     * @param serviceName The service starter prefix used to construct
     *                    the well-known service starter property names
     * @return            proxy for the service described by
     *                    <code>serviceName</code>.
     * 
     * @throws RemoteException  if a communication error occurs while
     *                          attempting to start the service
     * @throws TestException    if an admin cannot be created for the service
     */
    public Object startService(String serviceName) throws RemoteException, 
                                                          TestException
    {
	return startService(serviceName, null);
    }

    /**
     * Start an instance of the service named
     * <code>net.jini.core.lookup.ServiceRegistrar</code> and return a reference
     * to the proxy for that service. The host to run the service on is selected
     * based on the current host selection policy.
     * 
     * @return            proxy for the service described by
     *                    <code>serviceName</code>.
     * 
     * @throws RemoteException  if a communication error occurs while
     *                          attempting to start the service
     * @throws TestException    if an admin cannot be created for the service
     */
    public ServiceRegistrar startLookupService() throws RemoteException,
                                                        TestException
    {
	return (ServiceRegistrar) startService(SERVICEREGISTRAR);
    }

    /**
     * Start an instance of the service named
     * <code>net.jini.core.lookup.ServiceRegistrar</code> and return a reference
     * to the proxy for that service.
     *
     * @param host        the host to run the service on, or null to select
     *                    the host based on the current host selection policy.
     * 
     * @return            proxy for the service described by
     *                    <code>serviceName</code>.
     * 
     * @throws RemoteException  if a communication error occurs while
     *                          attempting to start the service
     * @throws TestException    if an admin cannot be created for the service
     */
    public ServiceRegistrar startLookupService(String host) 
	throws RemoteException, TestException
    {
	return (ServiceRegistrar) startService(SERVICEREGISTRAR, host);
    }

    /**
     * Stops the given managed <code>service</code>. If <code>service</code>
     * is not in the managed set, this method does nothing. If the service
     * is activatable (and is bound to an instance of 
     * <code>ActivatableServiceAdmin</code>), this method will call the
     * admins <code>stopAndWait</code> method, specifying a default
     * number of seconds for the service's activation group to be unregistered
     * with the activation system. Otherwise, the admins <code>stop</code>
     * method will be called. The default number of seconds is obtained
     * from the configuration parameter named
     * <code>com.sun.jini.qa.harness.nSecsWaitDestroy</code>. If this does
     * not exist, the value used is 
     * <code>ServiceDestroyer.DEFAULT_N_SECS_WAIT</code>.
     * <p>
     * The admin bound to this service is removed from the managed set.
     * 
     * @param service   reference to the service to destroy
     *
     * @return          <code>true</code> if the admin's <code>stop</code>
     *                  method was called, or if the admin's 
     *                  <code>stopAndWait</code> method was called and returned
     *                  a success status.
     */
    public boolean destroyService(Object service) {
	String waitProp = "com.sun.jini.qa.harness.nSecsWaitDestroy";
        int nSecsWait = 
	        config.getIntConfigVal(waitProp,
                                       ServiceDestroyer.DEFAULT_N_SECS_WAIT);
        return destroyService(service, nSecsWait);
    }

    /**
     * Stops all managed services. Service's which are activatable and bound 
     * to an instance of <code>ActivatableServiceAdmin</code> will be stopped
     * by calling the admin's <code>stopAndWait</code> method, specifying a 
     * default number of seconds for the service's activation group to be
     * unregistered with the activation system. Otherwise, the admins
     * <code>stop</code> method will be called. The default number of seconds
     * is obtained from the configuration parameter named 
     * <code>com.sun.jini.qa.harness.nSecsWaitDestroy</code>. If this does
     * not exist, the value used is 
     * <code>ServiceDestroyer.DEFAULT_N_SECS_WAIT</code>.
     * <p>
     * This method sorts the managed set of services, stopping all 
     * non-lookup jini services, then stopping the lookup services, then
     * stopping any shared activation groups, then stopping any 
     * shared nonactivatable groups, then stopping any
     * activation systems, and finally stopping any class servers.
     * <p>
     * All services will be removed from the managed set.
     */
    public void destroyAllServices() {
	String waitProp = "com.sun.jini.qa.harness.nSecsWaitDestroy";
        int nSecsWait = 
	        config.getIntConfigVal(waitProp,
                                       ServiceDestroyer.DEFAULT_N_SECS_WAIT);
        destroyAllServices(nSecsWait);
    }

    /**
     * Convenience method that interprets a given return value produced by
     * <code>com.sun.jini.start.ServiceDestroyer.destroy</code> and 
     * displays the appropriate debug output based on that interpretation.
     *
     * @param destroyCode <code>int</code> value that indicates success or
     *                    the type of failure after an attempt has been made
     *                    to destroy a service
     */
    private void handleDestroyCode(int destroyCode) {
        switch(destroyCode) {
            case ServiceDestroyer.DESTROY_SUCCESS:
                logger.log(Level.FINE, "successfully destroyed");
                break;
            case ServiceDestroyer.SERVICE_NOT_ADMINISTRABLE:
                logger.log(Level.FINE, 
                             "destroy failure - not an instance "
                             + "of net.jini.admin.Administrable");
                break;
            case ServiceDestroyer.SERVICE_NOT_DESTROY_ADMIN:
                logger.log(Level.FINE,
                             "destroy failure - not an instance "
                             + "of com.sun.jini.admin.DestroyAdmin");
                break;
            case ServiceDestroyer.DEACTIVATION_TIMEOUT:
                logger.log(Level.FINE, 
                             "destroy failure - timeout occurred "
                             + "before completion of deactivation");
                break;
            case ServiceDestroyer.PERSISTENT_STORE_EXISTS:
                logger.log(Level.FINE,
                             "destroy warning - deactivated, "
                             + "but persistent log still exists");
                break;
        }
    }
    
    /**
     * Stops all managed services. Service's which are activatable and bound 
     * to an instance of <code>ActivatableServiceAdmin</code> will be stopped
     * by calling the admin's <code>stopAndWait</code> method, specifying
     * <code>nSecsWait</code> seconds for the service's activation group to be
     * unregistered with the activation system. Otherwise, the admins
     * <code>stop</code> method will be called.
     * <p>
     * This method sorts the managed set of services, stopping all 
     * non-lookup jini services, then stopping the lookup services, then
     * stopping any shared activation groups, then stopping any shared
     * nonactivatable groups, then stopping any
     * activation systems, and finally stopping any class servers. Service
     * running on slave are not destroyed; it is assumed that a request will
     * be sent to the slave to cause their local instance of this
     * method to be called.
     * <p>
     * All services will be removed from the managed set.
     *
     * @param nSecsWait the number of seconds to wait for each service's 
     *                  activation group to be no longer registered with the
     *                  the activation system
     */
    public void destroyAllServices(int nSecsWait) {
	/*
	 * ArrayLists are created containing the service proxies
	 * to delete. This is important because the destroyService
	 * method iterates over createdAdminSet, and removes entries
	 * from that set. If this method called destroyService while
	 * directly iterating over createdAdminSet, a
	 * ConcurrentModificationException would result
	 */
	ArrayList lusList = new ArrayList();
	ArrayList svcList = new ArrayList();
	ArrayList sharedList = new ArrayList();
	ArrayList nonActList = new ArrayList();
	ArrayList actSystemList = new ArrayList();
	ArrayList classServerList = new ArrayList();
        synchronized (createdAdminSet){
            Iterator it = createdAdminSet.iterator();
            while (it.hasNext()) {
                Admin admin = (Admin) it.next();
                if (admin.getProxy() == null) {  // never started
                    it.remove(); // must use iterator's remove method
                    continue;
                }
                if (admin.getProxy() instanceof ServiceRegistrar) {
                    lusList.add(admin);
                } else if (admin instanceof SharedGroupAdmin) {
                    sharedList.add(admin);
                } else if (admin instanceof NonActivatableGroupAdmin) {
                    nonActList.add(admin);
                } else if (admin instanceof ActivationSystemAdmin) {
                    actSystemList.add(admin);
                } else if (admin instanceof ClassServerAdmin) {
                    classServerList.add(admin);
                } else {
                    svcList.add(admin);
                }
            }
        }
	ArrayList[] lists = new ArrayList[] {svcList, 
					     lusList, 
					     sharedList,
					     nonActList,
					     actSystemList,
					     classServerList};
	for (int i = 0; i < lists.length; i++) {
	    List list = lists[i];
	    Iterator it = list.iterator();
	    /* Step through the iterator destroying each service */
	    while(it.hasNext()) {
		Admin admin = (Admin) it.next();
		destroyService(admin.getProxy(), nSecsWait);
	    }
	}
    }

    /**
     * Stops the given managed <code>service</code>. If <code>service</code>
     * is not in the managed set, this method does nothing. If the service
     * is activatable (and is bound to an instance of 
     * <code>ActivatableServiceAdmin</code>), this method will call the
     * admins <code>stopAndWait</code> method, specifying <code>nSecsWait</code>
     * seconds for the service's activation group to be unregistered
     * with the activation system. Otherwise, the admins <code>stop</code>
     * method will be called. If the admin is an instance of
     * <code>SharedGroupAdmin</code>, then <code>sharedGroupAdmin</code>
     * field is set to <code>null</code> after the group is stopped.
     * <p>
     * In all case, the admin bound to this service is removed from the managed
     * set.
     * 
     * @param service   reference to the service to destroy. If 
     *                  <code>service</code> is <code>null</code>, this
     *                  method does nothing, and return <code>true</code>.
     * @param nSecsWait the number of seconds to wait for the service's 
     *                  activation group to be no longer registered with the
     *                  the activation system. If this parameters is
     *                  less than or equal to zero, no waiting is performed
     *                  and success is assumed.
     *
     * @return          <code>true</code> if the admin's <code>stop</code>
     *                  method was called, or if the admin's 
     *                  <code>stopAndWait</code> method was called and returned
     *                  a success status.
     */
    public boolean destroyService(Object service, int nSecsWait) {
	if (service == null) {
	    return true;
	}
        synchronized (createdAdminSet){
            Iterator it = createdAdminSet.iterator();
            while(it.hasNext()) {
                Admin admin = (Admin) it.next();
                if (admin == null) {
                    continue;
                }
                Object proxy = admin.getProxy();
                // proxy will be null if the service  wasn't started
                if (proxy == null || (! proxy.equals(service))) {
                    continue;
                }
                try {
                    logger.log(Level.FINE, 
                               "destroying service: " + proxy.getClass());
                    if (admin instanceof ActivatableServiceStarterAdmin) {
                        ActivatableServiceStarterAdmin 
                                ssa = (ActivatableServiceStarterAdmin) admin;
                        int destroyCode = ssa.stopAndWait(nSecsWait);
                        if(nSecsWait <= 0) {//doesn't care if act group still there
                            destroyCode = ServiceDestroyer.DESTROY_SUCCESS;
                        }
                        handleDestroyCode(destroyCode);
                        return destroyCode == ServiceDestroyer.DESTROY_SUCCESS ;
                    } else {
                        admin.stop();
                    }
                    if (admin == sharedGroupAdmin) {
                        sharedGroupAdmin = null;
                    }
                    if (admin == nonActivatableGroupAdmin) {
                        nonActivatableGroupAdmin = null;
                    }
                    return true;
                } catch(RemoteException e) { 
                    logger.log(Level.FINE, "RemoteException stopping service", e);
                } catch(ActivationException e) {
                    logger.log(Level.FINE, "ActivationException stopping service:", e);
                }
                finally {
                    it.remove(); // must use iterator's remove
                }
            }
        }
        return false;
    }

    /** 
     * Obtain the log directory associated with the shared activation
     * group being managed by this manager. 
     * 
     * @return the shared log directory path represented as a string. This
     *         string will be null if there is no active shared group.
     */
    public String getSharedVMLog() {
	String sharedLogDir = null;
	if (sharedGroupAdmin != null) {
	    sharedLogDir = 
		sharedGroupAdmin.getSharedGroupLog().getAbsolutePath();
	}
	return sharedLogDir;
    }

    /**
     * Kill the activation group containing the service represented by the
     * given proxy. If the service is activatable, the group is killed and
     * this method returns true. If the service is not activatable, this
     * method returns false. If the service is running on a slave host,
     * the slave activation group is killed.
     * 
     * @param proxy the proxy of the service who's group is to be killed
     * @return true if the service is activatable and its group was killed
     */
    public boolean killVM(Object proxy) {
	Admin admin = getAdmin(proxy);
	if (admin == null) {
	    return false;
	}
	if (admin instanceof ActivatableServiceStarterAdmin) {
	    // assume it works, even if a RemoteException is thrown
	    try {
		((ActivatableServiceStarterAdmin) admin).killVM();
	    } catch (RemoteException e) {
		logger.log(Level.INFO, "Exception killing VM", e);
	    }
	    return true;
	}
	if (admin instanceof RemoteServiceAdmin) {
	    try {
		return ((RemoteServiceAdmin) admin).killVM();
	    } catch (TestException e) {
		logger.log(Level.INFO, "This should NEVER happen", e);
	    }
	}
	return false;
    }

    /**
     * Return the name of the host the service represented by the given proxy
     * is running on. If the service is running on the local system and it is
     * not possible to determine the name of the local system, "localhost" is
     * returned.
     *
     * @param proxy the proxy of the service to obtain host info for.
     * @return the name of the host
     */
    public String getHost(Object proxy) {
	String hostName = config.getLocalHostName();
	Admin admin = getAdmin(proxy);
	if (admin != null && (admin instanceof RemoteServiceAdmin)) {
	    hostName = ((RemoteServiceAdmin) admin).getHost();
	}
	return hostName;
    }

    /**
     * Return an <code>Iterator</code> for the managed admins.
     *
     * @return the <code>Iterator</code>
     */
    Iterator iterator() {
        Set set = new HashSet();
        synchronized (createdAdminSet){
            set.addAll(createdAdminSet);
        }
	return set.iterator();
    }

    /**
     * If <code>com.sun.jini.qa.harness.serviceMode</code> is defined
     * and matches a supported service mode, set the type property
     * to that mode.
     *
     * @param the service name
     */
    private void setServiceType(String serviceName) {
	String mode = 
	    config.getStringConfigVal("com.sun.jini.qa.harness.serviceMode", 
				      null);
	if (mode == null) {
	    return;
	}
	String impl = serviceName + "." + mode + "." + "impl";
	if (config.getStringConfigVal(impl, null) != null) {
	    config.setDynamicParameter(serviceName + ".type", mode);
	}
    }

    /**
     * Return the <code>SharedGroupAdmin</code> being managed.
     * 
     * @return the admin
     */
    public SharedGroupAdmin getSharedGroupAdmin() {
	return sharedGroupAdmin;
    }

    /**
     * Return the <code>NonActivatableGroupAdmin</code> being managed.
     * 
     * @return the admin
     */
    public NonActivatableGroupAdmin getNonActivatableGroupAdmin() {
	return nonActivatableGroupAdmin;
    }

    public AbstractServiceAdmin[] getAllAdmins() {
        synchronized (createdAdminSet){
            AbstractServiceAdmin[] admins = 
                new AbstractServiceAdmin[createdAdminSet.size()];
            return (AbstractServiceAdmin[]) createdAdminSet.toArray(admins);
        }
    }
}
