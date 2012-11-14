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

package com.sun.jini.test.impl.lookupdiscovery;

import com.sun.jini.test.share.BaseQATest;
import com.sun.jini.test.spec.lookupdiscovery.AbstractBaseTest;
import com.sun.jini.thread.TaskManager;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import java.io.IOException;
import net.jini.config.Configuration;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscovery;
import net.jini.loader.pref.PreferredClassLoader;

/**
 * With respect to the current implementation of the
 * <code>LookupDiscovery</code> utility, this class verifies
 * that any tasks that it adds/schedules in its <code>TaskManager</code> or
 * <code>WakupManager</code> is done in using the unprivileged context of
 * the original caller.
 *
 * The environment in which this class expects to operate is as follows:
 * <p><ul>
 *   <li> one lookup service
 *   <li> one instance of LookupDiscovery configured to perform discovery
 *        for the groups that the previously started lookup service belongs
 *        to. The instance is created in a context which has not been granted
 *        <code>java.security.AllPermission</code>.
 *   <li> After discovery, the <code>TaskManager</code> and
 *        <code>WakeupManager</code> that the instance has been configured
 *        with are used to schedule new tasks. They should be run in the
 *        context with no <code>java.security.AllPermission</code>.
 * </ul><p>
 * 
 * If the <code>LookupDiscovery</code> utility functions as intended, the
 * scheduled tasks must not be granted <code>java.security.AllPermission</code>,
 * and must encounter <code>java.lang.SecurityException</code>.
 *
 */
public class RestoreContextForTasks extends BaseQATest {
    private static final String CLASSNAME =
	"com.sun.jini.test.impl.lookupdiscovery.util.TestTaskProducerImpl";
    private static final String QAHOMEPROP = "com.sun.jini.test.home";
    private static final String JARLOCATION = "/lib/ld.jar";

    private TestTaskProducer tp;
    private LookupDiscovery uld;
    public interface TestTaskProducer {
	public LookupDiscovery setup(Configuration config) throws Exception;
	public void run() throws Exception;
    }
    public static class DangerousRunnable implements Runnable {
	public boolean securityException = false;
	public boolean done = false;

	public void run() {
	    try {
		System.getSecurityManager().checkPermission(
			new java.security.AllPermission());
	    } catch (SecurityException se) {
		logger.log(Level.FINE, "Expected exception caught", se);
		securityException = true;
	    }
	    synchronized (this) {
		done = true;
		this.notify();
	    }
	}
    }
    public static class DangerousTask extends DangerousRunnable
	implements TaskManager.Task {

	public void run() {
	    try {
		System.getSecurityManager().checkPermission(
			new java.security.AllPermission());
	    } catch (SecurityException se) {
		logger.log(Level.FINE, "Expected exception caught", se);
		securityException = true;
	    }
	    synchronized (this) {
		done = true;
		this.notify();
	    }
	}
	public boolean runAfter(List l, int i) {
	    return false;
	}
    }
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	ClassLoader cl = PreferredClassLoader.newInstance(new URL[]
	    {new URL("file://" + System.getProperty(QAHOMEPROP) + JARLOCATION)},
	    this.getClass().getClassLoader(), null, false);
	tp = (TestTaskProducer)
	    Class.forName(CLASSNAME, true, cl).newInstance();
	uld = tp.setup(sysConfig.getConfiguration());
    }
    /** (Copied from AbstractBaseTest). We cannot use that class for this test,
     *  because we only want a LookupDiscovery instance created from an
     *  unprivileged context.
     *  Convenience method that encapsulates basic discovery processing.
     *  Use this method when it is necessary to specify both the lookup
     *  discovery utility used for discovery, and the set of groups to
     *  discover.
     *  
     *  This method does the following:
     *  <p><ul>
     *     <li> uses the contents of the given ArrayList that references the
     *          locator and group information of the lookup services that
     *          have been started, together with the groups to discover,
     *          to set the lookps that should be expected to be discovered
     *          for the given listener
     *     <li> with respect to the given listener, starts the discovery
     *          process by adding that listener to the given lookup discovery
     *          utility
     *     <li> verifies that the discovery process is working by waiting
     *          for the expected discovered events
     *  </ul>
     *  @throws com.sun.jini.qa.harness.TestException
     */
    private void doDiscovery(List locGroupsListStartedLookups,
                               LookupDiscovery ld,
                               LookupListener listener,
                               String[] groupsToDiscover)
                                                        throws TestException,
                                                               IOException
    {
        logger.log(Level.FINE,
                          "set groups to discover -- ");
        if(groupsToDiscover == DiscoveryGroupManagement.ALL_GROUPS) {
            logger.log(Level.FINE, "   ALL_GROUPS");
        } else {
            if(groupsToDiscover.length == 0) {
                logger.log(Level.FINE, "   NO_GROUPS");
            } else {
                for(int i=0;i<groupsToDiscover.length;i++) {
                    logger.log(Level.FINE,
                                      "   "+groupsToDiscover[i]);
                }//end loop
            }//endif
        }//end loop
        /* Set the expected groups to discover */
        listener.setLookupsToDiscover(locGroupsListStartedLookups,
                                      groupsToDiscover);
        /* Re-configure LookupDiscovery to discover given groups */
        ld.setGroups(groupsToDiscover);
        /* Add the given listener to the LookupDiscovery utility */
        ld.addDiscoveryListener(listener);
        /* Wait for the discovery of the expected lookup service(s) */
        waitForDiscovery(listener);
    }//end doDiscovery
    
    public void run() throws Exception {
	doDiscovery(initLookupsToStart, uld, 
		new LookupListener(), toGroupsArray(initLookupsToStart));
	tp.run();
    }
    
    /** Cleans up all state. Terminates the lookup discovery utilities that
     *  may have been created, shutdowns any lookup service(s) that may
     *  have been started, and performs any standard clean up duties performed
     *  in the super class.
     */
    public void tearDown() {
        try {
            /* Terminate each lookup discovery utility that was created */
	    uld.terminate();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
	    super.tearDown();
	}
    }//end tearDown
}

