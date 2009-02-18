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
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.File;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;

/**
 * A base class for tests to be run by the harness.
 * It partially implements the <code>com.sun.jini.qa.harness.Test</code> interface.
 * Minimal implementations for <code>setup</code> and <code>teardown</code>
 * are provided. Subclasses of this class are responsible for implementing
 * the <code>run</code> method, and may override
 * <code>setup</code> and <code>teardown</code> to add test specific
 * initialization and cleanup operations.
 * <p>
 * A protected <code>logger</code> is instantiated by this class for use
 * by subclasses, with the logger name <code>com.sun.jini.qa.harness.test</code>.
 */
public abstract class QATest implements Test {

    /** the logger */
    protected static Logger logger = 
	Logger.getLogger("com.sun.jini.qa.harness");

    /** Keeps track of leases for automatic cancellation when test ends. */
    private ArrayList leaseArray = new ArrayList();

    /** The admin manager for managing services */
    protected AdminManager manager;

    /** The config object for accessing the test environment */
    protected QAConfig config;

    /** 
     * Mostly mimics the behavior of the assert keyword. 
     * If <code>condition</code> is <code>true</code>, the method 
     * returns silently.  If <code>condition</code> is <code>false</code>,
     * the method throws <code>TestException</code> with the detail message
     * of <code>failureMsg</code>.
     * 
     * @param condition the condition to evaluate
     * @param failureMsg the exception message to provide
     * @throws TestException if <code>condition</code> is <code>false</code>
     */
    public void assertion(boolean condition, String failureMsg) 
        throws TestException
    {
        if (!condition) throw new TestException(failureMsg);
    }

    /** 
     * Mostly mimics the behavior of the assert keyword. 
     * If <code>condition</code> is <code>true</code>, the method 
     * returns silently.  If <code>condition</code> is <code>false</code>,
     * the method throws <code>TestException</code>.
     *
     * @param condition the condition to evaluate
     * @throws TestException if <code>condition</code> is <code>false</code>
     */
    public void assertion(boolean condition) 
        throws TestException
    {
        if (!condition) throw new TestException();
    }

    /**
     * Return the <code>QAConfig</code> object for the test environment.
     *
     * @return the harness QAConfig <code>object</code>
     */
    public QAConfig getConfig() {
	return config;
    }

    /**
     * This method is called by the <code>MasterTest</code> immediately before
     * the run method is called. Override this method to implement test specific
     * setup code.  This method:
     * <ul>
     *   <li>saves a reference to <code>config</code>
     *   <li>starts the class server identified by the 
     *       <code>serviceName</code> "qaClassServer" if the config value 
     *       named <code>com.sun.jini.qa.harness.runkitserver</code> is 
     *       <code>true</code> (the default)
     *   <li>starts the class server identified by the
     *       <code>serviceName</code> "jiniClassServer" if the config value
     *       named <code>com.sun.jini.qa.harness.runjiniserver</code> is 
     *       <code>true</code> (the default) 
     * </ul>
     * <P>
     * In the majority of cases this method will be overridden. The first action
     * taken by the method should be a call to
     * <code>super.setup(sysConfig)</code>.
     * 
     * @throws Exception if any failure occurs during setup  
     */
    public void setup(QAConfig config) throws Exception {
	int delayTime = 
	    config.getIntConfigVal("com.sun.jini.qa.harness.startDelay", 0);
	if (delayTime > 0) {
	    try {
		Thread.sleep(1000 * delayTime);
	    } catch (InterruptedException ignore) {
	    }
	}
	this.config = config;
	manager = new AdminManager(config);
	if (config.getBooleanConfigVal("com.sun.jini.qa.harness.runkitserver", 
				       true)) 
	{
	    manager.startService("qaClassServer");
	    SlaveRequest request = new StartClassServerRequest("qaClassServer");
	    SlaveTest.broadcast(request);
	}
	if (config.getBooleanConfigVal("com.sun.jini.qa.harness.runjiniserver", 
				       true)) 
	{
	    manager.startService("jiniClassServer");
	    SlaveRequest request = 
		new StartClassServerRequest("jiniClassServer");
	    SlaveTest.broadcast(request);
	}
	String testClassServer = 
	    config.getStringConfigVal("com.sun.jini.qa.harness.testClassServer",
				      "");
	if (testClassServer.trim().length() > 0) {
	    manager.startService("testClassServer");
	    SlaveRequest request = 
		new StartClassServerRequest(testClassServer);
	    SlaveTest.broadcast(request);
	}
    }

    /**
     * This method is called by the <code>MasterTest</code> immediately after
     * the run method returns.  Override this method to implement test specific
     * cleanup code.  This method cancels all tracked leases, destroys all
     * services that were started by the <code>AdminManager</code>, and sends
     * a <code>TeardownRequest</code> to all participating 
     * <code>SlaveTest</code>s.
     * <p>
     * If this method is overridden, the overriding method should include
     * a call to <code>super.teardown()</code>. In most cases, this will
     * be done as the final action of the overriding method, since test specific
     * cleanup may depend on access to services destroyed by this method.
     */
    public void tearDown() {
	cancelTrackedLeases();
	if (manager != null) { // null if test didn't call super.setup
	    try {
		logger.log(Level.FINE, 
			   "Destroying remaining managed services");
		manager.destroyAllServices();
	    } catch (Exception ex) {
		logger.log(Level.INFO, 
			   "Unexpected exception while cleaning up services",
			   ex);
	    }
	}
	SlaveTest.broadcast(new TeardownRequest());
    }

    /**
     * Track a lease for automatic cancellation when the test ends.
     * 
     * @param lease the Lease to add to the tracking array
     */
    public void trackLease(Lease lease) {
	leaseArray.add(lease);
    }

    /**
     * Cancel all Leases that have been tracked by the caller. 
     * <code>UnknownLeaseExceptions</code> are silently ignored.
     * <code>RemoteExceptions</code> cause an error message to
     * be written to the log. In all cases, all of the tracked
     * leases are discarded.
     */
    public void cancelTrackedLeases() {
	Iterator iter = leaseArray.iterator();
	while (iter.hasNext()) {
	   Lease lease = (Lease) iter.next();
	   try {
	       lease.cancel();
	   } catch (UnknownLeaseException ignore) {
	   } catch (RemoteException ex) {
	       logger.log(Level.INFO, "Failed to cancel lease", ex);
	   }
	}
	leaseArray.clear();
    }

    // delay is done here in case multiple groups are present
    public void forceThreadDump() {
	Iterator it = manager.iterator();
	while (it.hasNext()) {
	    Object admin = it.next();
	    if (admin instanceof NonActivatableGroupAdmin) {
		if (((NonActivatableGroupAdmin) admin).forceThreadDump()) {
		    try {
			Thread.sleep(5000); // give it time to flush
		    } catch (InterruptedException e) {
		    }
		}
	    }
	}
    }
}
