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

package org.apache.river.test.share;

import org.apache.river.qa.harness.Test;
import java.util.logging.Level;

// Test harness specific classes
import java.rmi.*;
import org.apache.river.qa.harness.TestException;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;

import net.jini.space.JavaSpace;
import net.jini.lease.LeaseRenewalService;
import net.jini.core.transaction.server.TransactionManager;


/**
 * Base class for tests that thest JoinAdmin related functionality
 */
public abstract class LookupTestBase extends TestBase implements Test {
    protected Object    service;
    protected Object    admin;

    protected boolean activatable;
    protected boolean tryShutdown;
    protected boolean noCleanup;

    /** Return true if for every elemet in <code>a1</code> there is an
        element that is <code>equal()</code> in <code>a2</code> and vis-versa
    */
    protected boolean arraysEqual(Object[] a1, Object[] a2) {
	if (a1.length != a2.length)
	    return false;

	for (int i=0; i<a1.length; i++) {
	    int j;
	    for (j=0; j<a2.length; j++)
		if (a1[i].equals(a2[j]))
		    break;
	    if (j == a2.length)
		// element in a1 that was not in a2
		return false;
	}

	// Because they are of equal length if all elements in a1 are
	// in a2, all elements of a2 must be in a1
	return true;
    }

    /**
     * Dump all of the elements in <code>objects</code> to the log.
     */
    protected void dumpArray(Object[] objects, String separator) {
	if (objects == null) {
	    logger.log(Level.INFO, "<array==null>");
	    return;
	}

	if (objects.length == 0) {
	    logger.log(Level.INFO, "<array.length==0>");
	    return;
	}

	for (int i=0; i<objects.length; i++) {
	    logger.log(Level.INFO, "\"" + objects[i] + "\"");
	    if (i != (objects.length - 1))
		logger.log(Level.INFO, separator);
	}

	logger.log(Level.INFO, "");
    }

    /**
     * Dump all of the join admin state in the passed object
     */
    protected void dumpJoinState(JoinAdmin joinAdmin)
	throws RemoteException
    {
	logger.log(Level.INFO, "getLookupAttributes():\n\t");
	dumpArray(joinAdmin.getLookupAttributes(), "\n\t");

	logger.log(Level.INFO, "getLookupGroups():");
	dumpArray(joinAdmin.getLookupGroups(), ",");

	logger.log(Level.INFO, "getLookupLocators():");
	dumpArray(joinAdmin.getLookupLocators(), ",");
    }

    /**
     * sleep for <code>dur</code> milliseconds ignoring all
     * InterruptedExceptions
     */
    protected void sleep(long dur) {
	try {
	    Thread.sleep(dur);
	} catch (InterruptedException e) {
            Thread.currentThread().interrupt();
	}
    }

    /**
     * Attempt to shut down <code>service</code>
     */
    protected void shutdown() throws Exception {
	shutdown(0);
    }

    /**
     * Parse command line args
     * <DL>
     * <DT>-notActivatable <DD> Should be used if the service
     * undertest is not activatable
     *
     * <DT>-tryShutdown<DD> If used the test will attempt to shutdown
     * the service and restart it (if it is activatable) and check to
     * ensure the service's JoinAdmin state is persistent.
     *
     * <DT>-noCleanup<DD> The lookup tests change the various join
     * admin atributs and then set them back to their orignal values
     * after thay are done.  If <code>-noCleanup</code> is used the
     * test will not perform this restoration.
     * </DL>
     */
    protected void parse() throws Exception {
	super.parse();

	activatable = !getConfig().getBooleanConfigVal("org.apache.river.test.share.notActivatable", false);
	tryShutdown = getConfig().getBooleanConfigVal("org.apache.river.test.share.tryShutdown", false);
	noCleanup   = getConfig().getBooleanConfigVal("org.apache.river.test.share.noCleanup", false);
    }

    protected void init() throws TestException {
	specifyServices(new Class[]{Administrable.class});
	service = services[0];
	String preparerName = null;
	if (service instanceof JavaSpace) {
	    preparerName = "test.outriggerAdminPreparer";
	} else if (service instanceof LeaseRenewalService) {
	    preparerName = "test.normAdminPreparer";
        } else if (service instanceof TransactionManager) {
	    preparerName = "test.mahaloAdminPreparer";
	} else {
	    throw new TestException("Unexpected service: " + service);
	}
	try {
	    admin = ((Administrable)service).getAdmin();
	    admin = getConfig().prepare(preparerName, admin);
	} catch (Throwable t) {
	    setupFailure("Could not get admin from " + service, t);
	}
    }

}



