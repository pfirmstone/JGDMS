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

package org.apache.river.qa.harness;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import net.jini.activation.arg.ActivationException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import org.apache.river.start.ServiceDescriptor;
import org.apache.river.start.SharedActivationGroupDescriptor;
import org.apache.river.start.group.SharedGroup;

/**
 * An admin for a shared activation group. 
 * <p>
 * This admin supports the special service property <code>"implPrefix"</code>
 * which is interpreted as the <code>serviceName</code> associated
 * with the groups administrative service. After the shared group is
 * created, the activatable service identified by this <code>serviceName</code>
 * is started; it is this proxy which is returned by calls to this admins
 * <code>getProxy</code> method.
 * <p>
 * The logger named <code>org.apache.river.qa.harness.service</code> is used
 * to log debug information
 *
 * <table border=1 cellpadding=5>
  *
 *<tr> <th> Level <th> Description 
 *
 *<tr> <td> FINE <td> parameter values used to start the service, and a
 *                    message indicating whether shared VM mode is being used
 *</table> <p>
 */
public class SharedGroupAdmin extends AbstractServiceAdmin implements Admin {

    /** the logger */
    private static Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness");

    /** the service proxy */
    private Object serviceRef;

    /** the shared group log directory */
    private File sharedGroupLog;

    /** the proxy for the vm killer service */
    private VMKiller killerProxy;

    /** options passed through the constructor */
    private String[] options;

    /** properties passed through the constructor */
    private String[] properties;

    /** merged properties from constructor and getServiceProperties method */
    private String[] combinedProperties;

    /** merged options from constructor and getServiceProperties method */
    private String[] combinedOptions;

    /** the admin manager */
    private final AdminManager manager;

    /**
     * Construct an <code>SharedGroupAdmin</code>.
     *
     * @param config       the configuration object for the test run 
     * @param serviceName  the service name
     * @param index	   the instance number for this service
     * @param manager      the admin manager for the test
     */
    public SharedGroupAdmin(QAConfig config, 
			    String serviceName, 
			    int index,
			    AdminManager manager)
    {
	super(config, serviceName, index);
	this.manager = manager;
    }

    /**
     * Construct an <code>SharedGroupAdmin</code>. This constructor
     * allows options and properties to be provided. It is intended
     * for use when a service creates a private shared group and applies
     * its own vm arguments to the group VM.
     *
     * @param config       the configuration object for the test run 
     * @param serviceName  the service name
     * @param index	   the instance number for this service
     * @param options      VM options to be added to the set provided
     *                     by the superclass
     * @param properties   VM properties to be added to the set provided
     *                     by the superclass
     */
    public SharedGroupAdmin(QAConfig config, 
			    String serviceName, 
			    int index,
			    String[] options,
			    String[] properties,
			    AdminManager manager)
    {
	super(config, serviceName, index);
	this.options = options;
	this.properties = properties;
	this.manager = manager;
    }

    /* inherit javadoc */
    public synchronized Object getProxy() {
	return serviceRef;
    }

    /**
     * Configures and starts an instance of the shared group named 
     * <code>serviceName</code> provided in the constructor. 
     * <p>
     * A check is made that the activation system is running. If the check
     * fails, a <code>TestException</code> is thrown.
     * <p>
     * If the shared group log file passed in the constructor is 
     * <code>null</code>, a unique temporary name is generated and
     * registered for automatic deletion.
     * <p>
     * A <code>SharedActivationGroupDescriptor</code> is contructed and
     * it's <code>create</code> method is called, passing the starter
     * configuration object. The <code>serviceName</code> for the group's
     * administrative service is identified by the service parameter
     * <code>"implPrefix"</code>. An 
     * <code>ActivatableServiceStarterAdmin</code> is constructed using
     * the prefix, and the start method called.
     * The proxy for the groups administrative service is retrieved from this
     * admin and is saved as the <code>serviceRef</code>.
     *
     * @throws TestException    if any of the mandatory service starter
     *                          parameters cannot be found. The mandatory
     *                          well-known tokens are: <code>impl,
     *                          codebase, classpath, policyfile, 
     *                          implPrefix</code>.
     *                          It is also thrown if the activation system is
     *                          not up, if a shared group log directory was
     *                          specified which already exists,  or if any 
     *                          exception is thrown while attempting to start
     *                          the service.
     * @throws RemoteException  never. Any <code>RemoteExceptions</code> which
     *                          occur while attempting to start the service
     *                          will be wrapped in a 
     *                          {@link org.apache.river.qa.harness.TestException}.
     */
    public synchronized void start() throws RemoteException, TestException {
	if (!ActivationSystemAdmin.wasStarted()) {
	    manager.startService("activationSystem");
	}
        /* Bomb if the Activation daemon is not up (probably redundant) */
        if (!config.activationUp(10)) {
	    throw new TestException("Activation System is not up");
        }
	try {
	    sharedGroupLog = config.createUniqueDirectory("sgrp",
							  "dir",
							  null);
	} catch (IOException e) {
	    throw new TestException("Failed to create directory", e);
	}
	sharedGroupLog.delete();
	if (sharedGroupLog.exists()) {
	    throw new TestException("Could not delete shared group directory: " 
				    + sharedGroupLog);
	}
	config.registerDeletion(sharedGroupLog);
	try {
	    ServiceDescriptor desc =
		new SharedActivationGroupDescriptor(
					     getServicePolicyFile(),
					     getServiceClasspath(),
					     sharedGroupLog.getAbsolutePath(),
					     getServiceJVM(),
					     getServiceOptions(),
					     getServiceProperties(),
					     getActivationHost(),
					     getActivationPort());
	    Configuration starterConfig = getStarterConfiguration();
	    logServiceParameters(); // log debug output
	    desc.create(starterConfig);
	    String groupImpl = getMandatoryParameter("implPrefix");
	    ActivatableServiceStarterAdmin implAdmin = 
		new ActivatableServiceStarterAdmin(config,
						   groupImpl,
						   index, 
						   manager);
	    implAdmin.start();
	    serviceRef = implAdmin.getProxy();

	} catch (ConfigurationException cex) {
	    throw new TestException("Starter config problem", cex);
	} catch (Exception e) {
	    throw new TestException("Unexpected exception starting service", e);
	}
    }

    /**
     * Override the base class method to merge properties which may have
     * been supplied through the constructor.
     *
     * @return the merged property array
     */
    public synchronized String[] getServiceProperties() throws TestException {
	combinedProperties = 
	    config.mergeProperties(super.getServiceProperties(), properties);
	return combinedProperties.clone();
    }

    /** 
     * Override the base class method to return the merged properties.
     * The <code>getServiceProperties</code> method must be called
     * prior to calling this method.
     *
     * @return the merged property array
     */
    public synchronized String[] getProperties() {
	return combinedProperties.clone();
    }

    /**
     * Override the base class method to merge options which may have
     * been supplied through the constructor.
     *
     * @return the merged property array
     */
    public synchronized String[] getServiceOptions() {
	combinedOptions = 
	    config.mergeOptions(super.getServiceOptions(), options);
	return combinedOptions.clone();
    }

    /** 
     * Override the base class method to return the merged options.
     * The <code>getServiceOptions</code> method must be called
     * prior to calling this method.
     *
     * @return the merged options array
     */
    public synchronized String[] getOptions() {
	return combinedOptions.clone();
    }

    /**
     * Kill the group VM. The killer service is started on demand the
     * first time this method is called.  The proxy is cached and
     * reused on subsequent calls.
     *
     * @throws RemoteException if an error occurs while 
     *                         attempting to start or call the back end service 
     */
    public synchronized void killVM() throws RemoteException {
	try {
	    if (killerProxy == null) {
		ActivatableServiceStarterAdmin killerAdmin =
		    new ActivatableServiceStarterAdmin(config,
						       "vmKiller",
						       index,
						       manager);
		killerAdmin.start();
		killerProxy = (VMKiller) killerAdmin.getProxy();
	    }
	    killerProxy.killVM();
	} catch (java.rmi.UnmarshalException ignore) {  //expected in this case
	} catch (Exception e) {
	    throw new RemoteException("failed to invoke killer service",e);
	}
    }
    
    /**
     * Destroys the shared group managed by this admin by calling the 
     * <code>destroyVM</code> method on the groups administrative service.
     *
     * @throws RemoteException if destroying the shared group failed
     */
    public synchronized void stop() throws RemoteException {
	try {
	    if (killerProxy != null) {
		logger.log(Level.FINE, "destroying VMKiller service");
		killerProxy.destroy();
	    }
	} catch (Exception e) {
	    logger.log(Level.INFO, "Attempt to kill VMKiller failed", e);
	}
	try {
	    logger.log(Level.FINEST, "destroying sharedVM");
	    ((SharedGroup) serviceRef).destroyVM();
	} catch (ActivationException e) {
	    throw new RemoteException("destroyVM failed", e);
	} catch (ClassCastException e){
	    throw new RemoteException("SharedGroup loaded by: " + SharedGroup.class.getClassLoader().toString() +
		    "\nserviceRef proxy loaded by: " +serviceRef.getClass().getClassLoader().toString(), e);
	}
    }
 
    /**
     * Return the shared VM log directory name associated with this
     * shared group. The <code>start</code> method must be called before
     * calling this accessor.
     *
     * @return the shared vm log dir (which may be <code>null</code>)
     */
    public synchronized File getSharedGroupLog() {
	return sharedGroupLog;
    }
}
