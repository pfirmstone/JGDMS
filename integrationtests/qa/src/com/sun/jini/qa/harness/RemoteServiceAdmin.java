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

import java.rmi.MarshalledObject;
import java.rmi.RemoteException;

import net.jini.core.discovery.LookupLocator;

/**
 * An admin for a service which is to be started on a slave host. Some
 * <code>AbstractServiceAdmin</code> methods are overridden with 
 * implementations which call the slave host to perform the action
 * remotely.
 */
class RemoteServiceAdmin extends AbstractServiceAdmin implements Admin {

    /** the service proxy */
    private Object serviceRef;

    /** the slave host name */
    private String hostname;

    /** the test properties */
    private QAConfig config;

    /**
     * Construct a <code>RemoteServiceAdmin</code>.
     *
     * @param hostname     the name of the slave to run the service on
     * @param config       the test properties
     * @param serviceName  the service name
     * @param index	   the service instance number
     */
    public RemoteServiceAdmin(String hostname,
			      QAConfig config, 
			      String serviceName, 
			      int index)
    {
	super(config, serviceName, index);
        this.config = config;
	this.hostname = hostname;
    }

    /* inherit javadoc */
    public Object getProxy() {
	return serviceRef;
    }

    /**
     * Return the slave this service is/will run on.
     *
     * @return the slave host name
     */
    public String getHost() {
	return hostname;
    }

    /**
     * Call the slave host with a request to start the service. If the returned
     * object is a <code>Throwable,</code> that object is wrapped in a
     * <code>TestException</code> thrown by this method. Otherwise, the returned
     * object is assumed to be a <code>MarshalledObject</code> containing the
     * service proxy. The proxy is unwrapped and prepared before returning.
     * 
     * @throws RemoteException never
     * @throws TestException if the call to the slave returns <code>null</code>
     *                       or an object of any type other than 
     *                       <code>MarshalledObject.</code> If the returned
     *                       object is <code>Throwable,</code> the
     *                       <code>TestException</code> wraps the 
     *                       <code>Throwable.</code> Any unexpected exception
     *                       thrown by this method is also wrapped in a thrown
     *                       <code>TestException</code>.
     */
    public void start() throws RemoteException, TestException {
	try {
	    SlaveRequest request = new StartServiceRequest(serviceName, index);
	    Object o = SlaveTest.call(hostname, request);
	    if (o == null) {
		throw new TestException("Slave call returned null");
	    }
	    if (o instanceof Throwable) {
		throw new TestException("Slave call returned exception", (Throwable) o);
	    }
	    if (! (o instanceof MarshalledObject)) {
		throw new TestException("expected MarshalledObject, got " 
					+ o.getClass());
	    }
	    serviceRef = ((MarshalledObject) o).get();
	    getServicePreparerName();
	    serviceRef = doProxyPreparation(serviceRef);
	} catch (Exception e) {
	    throw new TestException("Unexpected exception", e);
	}
    }

    /**
     * Stop the remote service by sending a <code>StopServiceRequest</code>
     * to the slave host.
     *
     * @throws RemoteException if the request returns a non <code>null</code>
     *                         object, or if any exception is thrown by the
     *                         call.
     */
    public void stop() throws RemoteException {
	try {
	    SlaveRequest request = new StopServiceRequest(serviceRef);
	    Object o = SlaveTest.call(hostname, request);
	    if (o == null) {
		return;
	    }
	    if (o instanceof Throwable) {
		throw new RemoteException("Slave call returned exception", (Throwable) o);
	    }
	    throw new RemoteException("Slave call returned unexpected object: " 
				    + o);
	} catch (Exception e) {
	    throw new RemoteException("Unexpected exception", e);
	}
    }

    /**
     * Send a <code>KillVMRequest</code> to the remote service host
     * for the service started by this admin.
     * 
     * @return true if the service was killed on the slave host.
     * @throws TestException of the call to the slave fails.
     */
    public boolean killVM() throws TestException {
	SlaveRequest request = new KillVMRequest(serviceRef);
	Boolean b = (Boolean) SlaveTest.call(hostname, request);
	return b.booleanValue();
    }

    /**
     * Call the <code>getGroups</code> method on the remote admin.
     *
     * @return the group list defined by the remote admin
     */
    public String[] getGroups() {
	return (String[]) callAccessor("getGroups");
    }

    /**
     * Call the <code>getLocators</code> method on the remote admin.
     *
     * @return the locators list defined by the remote admin
     */
    public LookupLocator[] getLocators() {
	return (LookupLocator[]) callAccessor("getLocators");
    }

    /**
     * Utility method to perform an <code>AdminAccessorRequest</code>
     * call on the remote admin.
     *
     * @param accessorName the name of the accessor method on the admin
     * @return the result of the accessor call
     */
    private Object callAccessor(String accessorName) {
	SlaveRequest request = new AdminAccessorRequest(accessorName, 
							 serviceRef);
	try {
	    return SlaveTest.call(hostname, request);
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception", e);
	}
    }
}
