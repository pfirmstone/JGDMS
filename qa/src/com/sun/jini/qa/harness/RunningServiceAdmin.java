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

import java.io.IOException;

// java.util
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.NoSuchElementException;

// net.jini
import java.util.logging.Logger;
import net.jini.config.ConfigurationException;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.DiscoveryEvent;

// java.rmi
import java.rmi.RemoteException;

/**
 * A specialization of <code>AbstractServiceAdmin</code> which obtains a proxy
 * for a running service rather than starting a service directly. The parameters
 * which define the service are obtained as described in {@link
 * com.sun.jini.qa.harness.AbstractServiceAdmin}. Only two service properties
 * supported by <code>AbstractServiceAdmin</code> are used:
 * <table>
 * <tr><td> tojoin       
 * <td>                 the set of groups and locators to be used to lookup
 *                      the service. This parameter is optional, and defaults
 *                      to the public group.
 * <tr><td> impl      
 * <td>                 the name of a service class which is to be found
 *                      in a <code>ServiceRegistrar</code>. Typically
 *                      this would be the same as the value passed in
 *                      the <code>serviceName</code> parameter in the
 *                      constructor. This parameter is mandatory.
 * </table>
 * Group names specified in the <code>tojoin</code> parameter are never
 * modified to make them unique.
 */
//XXX NOTE: this admin has never been used or tested
public class RunningServiceAdmin extends AbstractServiceAdmin implements Admin {

    /** the service proxy */
    private Object serviceRef;

    /** the map for managing discovery events */
    private LinkedList eventList;

    /** the test properties */
    private QAConfig config;

    /** the service name */
    private String serviceName;

    /** the service instance count */
    private int index;

    /**
     * Construct a <code>RunningServiceAdmin</code>.
     *
     * @param config         the configuration object for this test run
     * @param serviceName    the prefix used to build the property
     *                       names needed to acquire service parameters
     * @param index	     the instance number for this service
     */
    public RunningServiceAdmin(QAConfig config, 
			       String serviceName, 
			       int index)
    {
	super(config, serviceName, index);
    }

    /**
     * 'Starts' a service by looking it up in a lookup service and saving the
     * service proxy. The contents of the <code>tojoin</code> property is used
     * to define the groups/locators to be used to locate the lookup
     * service. The value of the <code>running</code> property is the name of
     * the class to use for performing the lookup. A class of this name must be
     * accessible by this class. The groups and locators contained in
     * <code>tojoin</code> are used to create a
     * <code>LookupDiscoveryManager</code>, and discovery events are processed
     * in the order received. As soon as a LUS is found containing a match to
     * the class named in <code>running</code>, discovery is terminated and the
     * service proxy is saved.
     * <p>
     * The join state (group/locators) of the service is not altered.
     *
     * @throws TestException    if the <code>running</code> parameter is not
     *                          found or is not the name of a class accessible
     *                          to this class.
     * @throws RemoteException  never. Any <code>RemoteException</code> which
     *                          occurs while attempting to find the service
     *                          will be wrapped in a <code>TestException</code>.
     */
    public void start() throws RemoteException, TestException {
	if (serviceRef != null) {
	    throw new TestException("RunningServiceAdmin: a service has "
				  + "already been started by this admin");
	}
	Class[] types = null;
	try {
	    types = new Class[]{Class.forName(getServiceImpl())};
	} catch (ClassNotFoundException e) {
	    throw new TestException("could not load class"
				  + getImpl() + " identified by "
				  + serviceName + ".impl",
				    e);
	}
	eventList = new LinkedList(); // every start call gets a new list
	DiscoveryListener listener = new RunningDiscoveryListener();
	LookupDiscoveryManager manager = null;
	// populate the groups/locators arrays. Discard the overrides list.
	// This admin implement doRandom to return false to inhibit
	// randomization of the group names
	addServiceGroupsAndLocators(new ArrayList());
	try {
	    manager = new LookupDiscoveryManager(getGroups(),
						 getLocators(),
						 listener);
        } catch (ConfigurationException e) {
            throw new TestException("failed to create a LookupDiscoveryManager",
				    e);
        } catch (IOException e) {
	    throw new TestException("failed to create a LookupDiscoveryManager",
				    e);
	}
	logServiceParameters();
	ServiceTemplate template = new ServiceTemplate(null, types, null);
	while (true) {
	    DiscoveryEvent event = null;
	    synchronized (eventList) {
		try {
		    event = (DiscoveryEvent) eventList.removeFirst();
		} catch (NoSuchElementException e) {
		    try {
			eventList.wait(); // XXX timeout?
		    } catch (InterruptedException ie) {
		    }
		}
	    }
	    if (event != null) {
		ServiceRegistrar[] registrars = event.getRegistrars();
		for (int i = registrars.length; --i >= 0; ) {
		    ServiceRegistrar registrar = registrars[i];
		    serviceRef = registrar.lookup(template);
		    if (serviceRef != null) {
			manager.terminate(); 
			return;              
		    }
		}
	    }
	}		    
    }

    /**
     * Stop the service. Since the service was started externally, it is
     * presumed that the service must also be stopped externally. Therefore,
     * this method simply clears the internal reference to the service
     * proxy and does not attempt to actually stop the service.
     *
     * @throws RemoteException never
     */
    public void stop() throws RemoteException {
	serviceRef = null;
    }

    // inherit javadoc
    public Object getProxy() {
	return serviceRef;
    }

    /**
     * An implementation of <code>DiscoveryListener</code> whose 
     * <code>discovered</code> method simply places <code>DiscoveryEvent</code>s
     * on a list for processing by the <code>start</code> method.
     */
    private class RunningDiscoveryListener implements DiscoveryListener {

	public void discarded(DiscoveryEvent e) {
	}

	public void discovered(DiscoveryEvent e) {
	    synchronized (eventList) {
		eventList.addLast(e);
		eventList.notify();
	    }
	}
    }

    /* inherit javadoc */
    protected boolean doRandom() {
	return false;
    }
}
