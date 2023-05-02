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

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;

/**
 * A base class for tests to be run by the harness.
 * It implements the <code>org.apache.river.qa.harness.TestEnvironment</code> interface.
 * Minimal implementations for <code>construct</code> and <code>teardown</code>
 * are provided. Subclasses of this class are responsible for implementing
 * the <code>org.apache.river.qa.harness.Test</code> interface <code>run</code> method, and may override
 * <code>construct</code> and <code>teardown</code> to add test specific
 * initialization and cleanup operations.
 * <p>
 * A protected <code>logger</code> is instantiated by this class for use
 * by subclasses, with the logger name <code>org.apache.river.qa.harness.test</code>.
 */
public abstract class QATestEnvironment implements TestEnvironment {

    /** the logger */
    protected static final Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness");

    /** Keeps track of leases for automatic cancellation when test ends. */
    private final Collection<Lease> leaseArray = new ArrayList<Lease>();//access must be synchronized
    /** The admin manager for managing services */
    private AdminManager manager;

    /** The config object for accessing the test environment */
    private QAConfig config;

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
    public synchronized QAConfig getConfig() {
	return config;
    }

    /**
     * This method is called by the <code>MasterTest</code> immediately before
     * the run method is called. Override this method to implement test specific
     * construct code.  This method:
     * <ul>
     *   <li>saves a reference to <code>config</code>
     *   <li>starts the class server identified by the 
     *       <code>serviceName</code> "qaClassServer" if the config value 
     *       named <code>org.apache.river.qa.harness.runkitserver</code> is 
     *       <code>true</code> (the default)
     *   <li>starts the class server identified by the
     *       <code>serviceName</code> "jiniClassServer" if the config value
     *       named <code>org.apache.river.qa.harness.runjiniserver</code> is 
     *       <code>true</code> (the default) 
     * </ul>
     * <P>
     * In the majority of cases this method will be overridden. The first action
     * taken by the method should be a call to
     * <code>super.construct(sysConfig)</code>.
     * 
     * @throws Exception if any failure occurs during construct  
     */
    public synchronized Test construct(QAConfig config) throws Exception {
	int delayTime = 
	    config.getIntConfigVal("org.apache.river.qa.harness.startDelay", 0);
	if (delayTime > 0) {
	    try {
		Thread.sleep(1000 * delayTime);
	    } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
	    }
	}
	this.config = config;
	manager = new AdminManager(config);
	if (config.getBooleanConfigVal("org.apache.river.qa.harness.runkitserver", 
				       true)) 
	{
	    getManager().startService("qaClassServer");
	    SlaveRequest request = new StartClassServerRequest("qaClassServer");
	    SlaveTest.broadcast(request);
	}
	if (config.getBooleanConfigVal("org.apache.river.qa.harness.runjiniserver", 
				       true)) 
	{
	    getManager().startService("jiniClassServer");
	    SlaveRequest request = 
		new StartClassServerRequest("jiniClassServer");
	    SlaveTest.broadcast(request);
	}
	String testClassServer = 
	    config.getStringConfigVal("org.apache.river.qa.harness.testClassServer",
				      "");
	if (testClassServer.trim().length() > 0) {
	    getManager().startService("testClassServer");
	    SlaveRequest request = 
		new StartClassServerRequest(testClassServer);
	    SlaveTest.broadcast(request);
	}
        return new Test(){

            public void run() throws Exception {
                // Do nothing
            }
            
        };
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
	if (getManager() != null) { // null if test didn't call super.construct
	    try {
		logger.log(Level.FINE, 
			   "Destroying remaining managed services");
		getManager().destroyAllServices();
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
        synchronized (leaseArray){
            leaseArray.add(lease);
        }
    }

    /**
     * Cancel all Leases that have been tracked by the caller. 
     * <code>UnknownLeaseExceptions</code> are silently ignored.
     * <code>RemoteExceptions</code> cause an error message to
     * be written to the log. In all cases, all of the tracked
     * leases are discarded.
     */
    public void cancelTrackedLeases() {
        // copy leaseArray to avoid calling remote method while synchronized.
        Collection<Lease> cancel;
        synchronized (leaseArray){
            cancel = new ArrayList<Lease>(leaseArray.size());
            cancel.addAll(leaseArray);
            leaseArray.clear();
        }
	Iterator<Lease> iter = cancel.iterator();
	while (iter.hasNext()) {
	   Lease lease = iter.next();
	   try {
	       lease.cancel();
	   } catch (UnknownLeaseException ignore) {
	   } catch (RemoteException ex) {
	       logger.log(Level.INFO, "Failed to cancel lease", ex);
	   }
	}
    }

    // delay is done here in case multiple groups are present
    public void forceThreadDump() {
	Iterator it = getManager().iterator();
	while (it.hasNext()) {
	    Object admin = it.next();
	    if (admin instanceof NonActivatableGroupAdmin) {
		if (((NonActivatableGroupAdmin) admin).forceThreadDump()) {
		    try {
			Thread.sleep(5000); // give it time to flush
		    } catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		    }
		}
	    }
	}
    }

    /**
     * @return the manager
     */
    protected synchronized AdminManager getManager() {
            return manager;
    }

}
