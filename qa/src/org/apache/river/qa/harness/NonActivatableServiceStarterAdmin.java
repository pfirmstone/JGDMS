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

import java.io.IOException;
import java.rmi.ConnectException;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.io.MarshalledInstance;

/**
 * An admin for a nonactivatable service which passes a request to
 * start a service to a NonActivatableGroup. If a global group does
 * not exist, or if the group is intended to be private to the service,
 * then a <code>NonActivatableGroup</code> will be created.
 * <p>
 *
 * The logger named <code>org.apache.river.qa.harness.service</code> is used
 * to log debug information
 *
 * <table border=1 cellpadding=5>
 *
 *<tr> <th> Level <th> Description
 *
 *<tr> <td> FINE <td> parameter values used to start the service
 *</table> <p>
 */
public class NonActivatableServiceStarterAdmin extends AbstractServiceAdmin
                                               implements Admin
{
    /** the service proxy */
    private Object serviceRef;

    /** the groupAdmin for the group in which the service is to run */
    private NonActivatableGroupAdmin groupAdmin;

    /** flag indicating whether the group is private to this service */
    private boolean privateGroup = false;

    /** the admin manager for the test */
    private final AdminManager manager;

    /**
     * Construct a <code>NonActivatableServiceStarterAdmin</code>.
     *
     * @param config         the configuration object for the test run 
     * @param serviceName    the prefix used to build the property
     *                       names needed to acquire service parameters
     * @param index	     the instance number for this service
     * @param manager        the admin manager
     */
    public NonActivatableServiceStarterAdmin(QAConfig config, 
					     String serviceName, 
					     int index,
					     AdminManager manager)
    {
	super(config, serviceName, index);
	this.groupAdmin = manager.getNonActivatableGroupAdmin();
	this.manager = manager;
    }

    /* inherit javadoc */
    public synchronized Object getProxy() {
	return serviceRef;
    }

    /**
     * Configures and starts an instance of the service described by the
     * <code>serviceName</code> provided in the constructor. 
     * <p>
     * The option arguments for the service are generated, containing the name
     * of the service configuration file and overrides for the groups, locators,
     * and membergroups entries to be used by the service. A
     * <code>NonActivatableServiceDescriptor</code> is created using these
     * arguments and other required parameters (i.e. classpath, codebase, etc).
     * <p>
     * After the service is started, the service
     * proxy is prepared by calling the <code>doProxyPreparation</code> 
     * method. 
     * <p>
     * This admin administratively sets the lookup port, lookup group
     * and locators, and any member groups that were specified and
     * also passed as overrides. This behavior is considered <b>temporary</b>
     * until all tests have been modified to work with override
     * entry names.
     *
     * @throws TestException    if any of the mandatory service 
     *                          properties cannot be found. The mandatory
     *                          properties are: <code>impl,
     *                          codebase, classpath, policyfile</code>.
     *                          It is also thrown if the activation system is
     *                          not up, if a class server is not up, if the
     *                          class server cannot access the JAR files
     *                          specified by the codebase parameter, if any 
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
  	    addServiceConfigurationFileName(optionsList); // must be first
	    addServiceGroupsAndLocators(optionsList);
	    addServiceMemberGroups(optionsList);
	    addServiceUnicastDiscoveryPort(optionsList);
	    addServicePersistenceLog(optionsList);
	    addServiceExporter(optionsList);
	    addRegisteredOverrides(optionsList);
	    String[] serviceConfigArgs = 
		(String[]) optionsList.toArray(new String[0]);
	    NonActivatableGroup group = (NonActivatableGroup) getGroup();
	    // get all loggable parameters and log them before trying
	    // to call group.startService, so the info is available in case
	    // something goes wrong
	    getServiceCodebase();
	    getServicePolicyFile();
	    getServiceClasspath();
	    getServiceImpl();
	    getStarterConfiguration();
	    getServicePreparerName();
	    logServiceParameters(); // log debug output
	    logOverrides(serviceConfigArgs);
	    serviceRef = group.startService(getCodebase(),
					    getPolicyFile(),
					    getClasspath(),
					    getImpl(),
					    serviceConfigArgs,
					    getStarterConfigurationFileName(), getTransformer());
						       
	} catch (Exception e) {
	    throw new TestException("Problem creating service for "
				   + serviceName, e);
	}
        //XXX temporary work-around for jrmp dgc problem
//	try {
//	    serviceRef = new MarshalledInstance(serviceRef).get(false);
//        } catch (IOException e) {
//	    throw new TestException("Problem unmarshalling proxy", e);
//        } catch (ClassNotFoundException e) {
//            throw new TestException("Problem unmarshalling proxy", e);
//        }
	serviceRef = doProxyPreparation(serviceRef);
    }

    /**
     * Administratively destroys the service managed by this admin.  Regardless
     * of the success or failure of the attempt to destroy the service, if the
     * group in which this service is running is private to the service, then
     * the group is also destroyed by calling the <code>stop</code> method for
     * the groups admin. Failure to destroy the private group will generate a
     * stack trace but will not affect the return semantics of the call.
     *
     * @throws RemoteException 
     *         when a communication failure occurs between the front-end
     *         and the back-end of the service while attempting to destroy
     *         the service.
     */
    public synchronized void stop() throws RemoteException {
	try {
	    ServiceDestroyer.destroy(serviceRef);
	} catch (ConnectException e) {
	    logger.log(Level.INFO,
		       "Service Object is gone, presumed killed by test");
	} finally {
	    if (privateGroup) {
		logger.log(Level.FINE, "Destroying service-private group");
		try {
		    groupAdmin.stop(); //best effort
		} catch (Exception e) {
		    logger.log(Level.INFO,
			       "Attempt to stop private group failed",
			       e);
		}
	    }
	}
    }

    /**
     * Obtain the group in which to run the service. If a non-null
     * groupAdmin was passed to the constructor, then use that group.
     * Otherwise, create a shared <code>NonActivatableGroup</code> if the test
     * property "org.apache.river.qa.harness.shared" is undefined or has the
     * value <code>true.</code> If that property is defined and has the value
     * <code>false,</code> create a group which is private to the service.
     *
     * @return the NonActivatableGroup proxy
     * @throws TestException if the group could not be created
     */
    private NonActivatableGroup getGroup() throws TestException {
	if (groupAdmin == null) {
	    if (config.getBooleanConfigVal("org.apache.river.qa.harness.shared",
					   true))
	    {
		logger.log(Level.FINER, "Creating shared group");
		Object group = null;
		try {
		    group = manager.startService("nonActivatableGroup");
		} catch (Exception e) {
		    throw new TestException("Failed to start the shared "
					    + "nonactivatable group",
					    e);
		}
		groupAdmin = (NonActivatableGroupAdmin) manager.getAdmin(group);
	    } else {
		logger.log(Level.FINER, "Creating private group");
		groupAdmin = new NonActivatableGroupAdmin(config,
						      "nonActivatableGroup",
						      index,
						      getServiceOptions(),
						      getServiceProperties());
		try {
		    groupAdmin.start();
		} catch (Exception e) {
		    throw new TestException("Failed to start "
					  + "nonactivatable group",
					    e);
		}
		privateGroup = true;
	    }
	}
	return (NonActivatableGroup) groupAdmin.getProxy();
    }
}
