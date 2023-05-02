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

import org.apache.river.start.ServiceDescriptor;
import org.apache.river.start.ServiceStarter;
import org.apache.river.start.SharedActivatableServiceDescriptor;

import java.io.File;
import java.io.IOException;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.ActivationGroup;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.arg.ActivationID;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.io.MarshalledInstance;
import net.jini.lookup.DiscoveryAdmin;
import net.jini.security.ProxyPreparer;

/**
 * An admin for an activatable service which uses {@link
 * org.apache.river.start.SharedActivatableServiceDescriptor} to start the service,
 * and which uses {@link org.apache.river.qa.harness.ServiceDestroyer} to stop the
 * service. The parameters which define the service are obtained as described in
 * {@link org.apache.river.qa.harness.AbstractServiceAdmin} extended by this
 * implementation. Additional parameter defined by this admin include:
 * <p>
 * <table align=center>
 *    <tr><td>restart    <td>boolean value for the restart flag
 * </table>
 * <p>
 *
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
public class ActivatableServiceStarterAdmin
                             extends AbstractServiceAdmin
                             implements Admin
{

    /** the service proxy */
    private Object serviceRef;

    /** the admin for the group used by this service */
    private SharedGroupAdmin groupAdmin;

    /** the name of the shared group persistence directory */
    private String sharedLogDirName;

    /** the value of the activation restart parameter */
    private boolean restart;

    /** the created object returned by the service descriptor */
    private SharedActivatableServiceDescriptor.Created created;

    /** the admin manager */
    private final AdminManager manager;

    /** flag indicating whether this service is running shared */
    private boolean privateGroup = false;

    /**
     * Construct an <code>ActivatableServiceStarterAdmin</code>.
     *
     * @param config         the property set object for the test run 
     * @param serviceName    the prefix used to build the property
     *                       names needed to acquire service parameters
     * @param index	     the instance number for this service
     * @param manager        the admin manager for this test
     */
    public ActivatableServiceStarterAdmin(QAConfig config, 
                                          String serviceName, 
                                          int index,
					  AdminManager manager)
    {
	super(config, serviceName, index);
	this.groupAdmin = manager.getSharedGroupAdmin();
	this.manager = manager;
    }

    /**
     * Return the <code>ActivationGroupID</code> associated with the
     * service started by this admin.
     *
     * @return the group ID, or <code>null</code> if the service has
     *         not been started
     */
    public synchronized ActivationGroupID getGroupID() {
	return (created == null ? null : created.gid);
    }
    
    /**
     * Return the <code>ActivationID</code> associated with the
     * service started by this admin.
     *
     * @return the activation ID, or <code>null</code> if the service has
     *         not been started
     */
     public synchronized ActivationID getActivationID() {
	return (created == null ? null : created.aid);
    }

    /* inherit javadoc */
    public synchronized Object getProxy() {
	return serviceRef;
    }

    /**
     * Configures and starts an instance of the service described by the
     * <code>serviceName</code> provided in the constructor. 
     * This method retrieves several parameters by constructing parameter
     * names using the prefix and a set of well-known tokens.
     * <p>
     * The option arguments for the service are generated, containing the name
     * of the service configuration file and overrides for the groups, locators
     * membergroups, and persistence log entries to be used by the service.
     * A <code>SharedActivatableServiceDescriptor</code> is created using these
     * arguments and other required parameters (i.e. classpath, codebase, etc).
     * If a descriptor transformer was registered with this admin, the 
     * transformers <code>transform</code> method is called with the
     * descriptor before calling the descriptors <code>create</code> method.
     * <p>
     * If a <code>SharedGroupAdmin</code> was not provided in the constructor,
     * one is created and started when <code>getServiceSharedLogDir</code> is
     * called in the service descriptor constructor.  The
     * <code>serviceName</code> used to construct the admin for the shared group
     * is <code>"sharedGroup"</code>. If the shared group must be created and
     * the activation system is not running, the activation system will be
     * started.
     * <p>
     * After the service is started, the service proxy is prepared
     * using the prepared using the prepared name returned by the
     * <code>getServicePreparerName</code> method.
     * <p>
     * This admin administratively sets the lookup port, lookup group
     * and locators, and any member groups that were specified and
     * also passed as overrides. This behavior is considered <b>temporary</b>
     * until all services have been converted to honor the override
     * entry names.
     *
     * @throws TestException    if any of the mandatory service starter
     *                          parameters cannot be found. The mandatory
     *                          well-known tokens are: <code>impl,
     *                          codebase, classpath, policyfile</code>.
     *                          It is also thrown if any 
     *                          exception is thrown while attempting to start
     *                          the service, or if generation of the 
     *                          configuration file fails.
     * @throws RemoteException  never. Any <code>RemoteExceptions</code> which
     *                          occur while attempting to start the service
     *                          will be wrapped in a 
     *                          {@link org.apache.river.qa.harness.TestException}.  
     */
    public synchronized void start() throws RemoteException, TestException {
	try {
	    // generate the overrides string array
	    ArrayList optionsList = new ArrayList();
  	    addServiceConfigurationFileName(optionsList);
	    addServiceGroupsAndLocators(optionsList);
	    addServiceMemberGroups(optionsList);
	    addServiceUnicastDiscoveryPort(optionsList);
	    addServicePersistenceLog(optionsList);
	    addServiceExporter(optionsList);
	    addRegisteredOverrides(optionsList);
	    String[] serviceConfigArgs = 
		(String[]) optionsList.toArray(new String[optionsList.size()]);
	    // if a sharedGroup does not exist for this service,
	    // one will be created by getServiceSharedLogDir().
	    // The check for the activation system being up is done there
	    SharedActivatableServiceDescriptor desc = 
		new SharedActivatableServiceDescriptor(
						getServiceCodebase(),
						getServicePolicyFile(),
						getServiceClasspath(),
						getServiceImpl(),
						getServiceSharedLogDir(),
						serviceConfigArgs,
						getServiceRestart(),
						getServiceActivationHost(),
						getServiceActivationPort());
						       
	    // get starter config and preparer name now so it will 
	    // show up in the log
	    Configuration starterConfig = getStarterConfiguration();
	    getServicePreparerName();
	    logServiceParameters(); // log debug output
	    logOverrides(serviceConfigArgs);
	    if (getTransformer() != null) {
		desc = (SharedActivatableServiceDescriptor) 
		       getTransformer().transform(desc);
	    }
	    created = (SharedActivatableServiceDescriptor.Created) 
		      desc.create(starterConfig);
	} catch (ConfigurationException e) {
	    throw new TestException("Configuration problem for " 
                                   + serviceName, e);
	} catch (Exception e) {
	    throw new TestException("Problem creating service for "
				   + serviceName, e);
	}
        //XXX temporary work-around for jrmp dgc problem
	try {
	    serviceRef = new MarshalledInstance(created.proxy).get(false);
        } catch (IOException e) {
	    throw new TestException("Problem unmarshalling proxy", e);
        } catch (ClassNotFoundException e) {
            throw new TestException("Problem unmarshalling proxy", e);
        }
	serviceRef = doProxyPreparation(serviceRef);
    }

    /**
     * Kill the service VM by calling the killVM method on the group admin.
     * 
     * @throws RemoteException if a communications error occured while making
     *                         the remote call to the killVM backend server
     */
    public synchronized void killVM() throws RemoteException {
	groupAdmin.killVM();
    }

    /**
     * Administratively destroys the service managed by this admin, and waits
     * for up to <code>ServiceDestroyer.DEFAULT_N_SECS_WAIT</code> seconds for
     * the service to unregister its activation group from the activation
     * service. Failure to unregister is silently ignored. If the
     * activation group is private to this service, then the
     * activation group is also destroyed by calling the <code>stop</code>
     * method of it's admin.
     *
     * @throws RemoteException 
     *         when a communication failure occurs between the front-end
     *         and the back-end of the service while attempting to destroy
     *         the service. When this exception does occur, the service may
     *         or may not have been successfully destroyed. If an
     *         <code>ActivationException</code> occurs, the exception is
     *         wrapped in a thrown <code>RemoteException</code>
     *
     */
    public void stop() throws RemoteException {
        try {
            stopAndWait(ServiceDestroyer.DEFAULT_N_SECS_WAIT);
        } catch (ActivationException e) {
            throw new RemoteException("Activation exception occurred "
                                    + "while stopping a service",
                                       e);
        }
    }
 
    /**
     * Administratively destroys the service managed by this admin.
     * This method attempts to verify that the desired service is actually
     * destroyed. If a private shared group is associated with this
     * service, this method will always attempt to destroy that group.
     * Failure to destroy the group will generate a stack trace but
     * will not affect the return semantics of the call.
     *
     * @param nSecsWait the number of seconds to wait for the service's 
     *                  activation ID to be no longer registered with the
     *                  the activation system
     *
     * @return          <code>int</code> value that indicates either success or 
     *                  one of a number of possible reasons for failure to 
     *                  destroy the service. Possible values are:
     * <p><ul>
     *   <li> ServiceDestroyer.DESTROY_SUCCESS
     *   <li> ServiceDestroyer.SERVICE_NOT_ADMINISTRABLE - returned when
     *        the service to destroy is not an instance of 
     *        net.jini.admin.Administrable
     *   <li> ServiceDestroyer.SERVICE_NOT_DESTROY_ADMIN - returned when
     *        the service to destroy is not an instance of 
     *        org.apache.river.admin.DestroyAdmin
     *   <li> ServiceDestroyer.DEACTIVATION_TIMEOUT - returned when the
     *        service's activation group is still registered with the
     *        activation system after the number of seconds to wait have passed
     *   <li> ServiceDestroyer.PERSISTENT_STORE_EXISTS - returned when the
     *        directory in which the service stores its persistent state
     *        still exists after the service has been successfully destroyed
     * </ul>
     * 
     * @throws RemoteException 
     *              typically, this exception occurs when
     *              there is a communication failure between the client and the
     *              service's backend. When this exception does occur, the
     *              service may or may not have been successfully destroyed.
     *
     * @throws ActivationException 
     *              typically, this exception occurs when problems arise while
     *              attempting to interact with the activation system
     */
    public synchronized int stopAndWait(int nSecsWait) 
               throws RemoteException, ActivationException
    {
	int destroyCode = ServiceDestroyer.DESTROY_SUCCESS;       
	try {
	    if (created != null) {
		destroyCode = 
		    ServiceDestroyer.destroy(created, 
					     nSecsWait,
					     config.getConfiguration());
	    }
	    return destroyCode;
	} catch (NoSuchObjectException e) {
	    logger.log(Level.INFO,
		       "Service Object is gone, presumed killed by test");
	    return destroyCode;
	} finally {
	    if (privateGroup) {
		logger.log(Level.FINE, "Destroying service-private group");
		try {
		    groupAdmin.stop();
		} catch (Exception e) { // best effort
		    logger.log(Level.INFO, 
			       "Attempt to stop private group failed",
			       e);
		}
	    }
	}
    }

    /**
     * Return the shared VM log directory name associated with this activatable
     * service. If a <code>SharedGroupAdmin</code> was not passed in the
     * constructor for this class, then if a global shared group is needed, that
     * group is created through the admin manager; otherwise, a
     * <code>SharedGroupAdmin</code> private to this service is created and its
     * <code>start</code> method is called.
     *
     * @return the shared vm log directory name
     * @throws TestException if this method attempts to start a shared
     *                       group and the attempt fails
     */
    protected synchronized String getServiceSharedLogDir() throws TestException {
	if (groupAdmin == null) {
	    if (config.getBooleanConfigVal("org.apache.river.qa.harness.shared",
					   true))
	    {
		Object group = null;
		try {
		    group = manager.startService("sharedGroup");
		} catch (Exception e) {
		    throw new TestException("Failed to start "
					    + "global shared group", e);
		}
		groupAdmin = (SharedGroupAdmin) manager.getAdmin(group);
	    } else {
		groupAdmin = new SharedGroupAdmin(config, 
						  "sharedGroup",
						  index,
						  getServiceOptions(),
						  getServiceProperties(),
						  manager);
		try {
		    groupAdmin.start();
		} catch (Exception e) {
		    throw new TestException("Failed to start shared group", e);
		}
		privateGroup = true;
	    }
	}
	sharedLogDirName = groupAdmin.getSharedGroupLog().getAbsolutePath();
	return sharedLogDirName;
    }

    /**
     * Return the shared log directory name originally returned by the
     * <code>getServiceSharedLogDir</code> method. 
     *
     * @return the service shared log directory name
     */
    public synchronized String getSharedLogDir() {
	return sharedLogDirName;
    }

    /**
     * Return the value of the restart parameter to use when registering
     * an activatable service. The environment is searched for a value
     * for the key <code>serviceName</code> + ".restart". The value
     * is interpreted as a boolean, and has the default value of
     * <code>true</code>.
     *
     * @return the value of the restart parameter
     */
    protected synchronized boolean getServiceRestart() {
	restart = config.getServiceBooleanProperty(serviceName,
						   "restart",
						   index,
						   true);
	return restart;
    }

    /**
     * Return the restart value originally returned by the
     * <code>getServiceRestart</code> method.
     *
     * @return the service restart value
     */
    public synchronized boolean getRestart() {
	return restart;
    }

    /* inherit javadoc */
    protected void logServiceParameters() throws TestException {
	super.logServiceParameters();
        String val = getSharedLogDir();
	logger.logp(Level.FINE, null, null, "     shared log        : " + val);
        boolean bval = getRestart();
	logger.logp(Level.FINE, null, null, "     restart           : " + bval);
    }
}
