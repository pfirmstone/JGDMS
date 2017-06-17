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
package org.apache.river.test.impl.outrigger.admin;

import java.util.logging.Level;

// java classes
import java.rmi.*;

// jini classes
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;
import net.jini.admin.Administrable;
import org.apache.river.admin.JavaSpaceAdmin;
import org.apache.river.admin.AdminIterator;

// Test harness specific classes
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

// Shared classes
import org.apache.river.test.share.TestBase;
import org.apache.river.test.share.UninterestingEntry;

/**
 * Write a large number of identical entries into the space and then
 * tryes to delete all of them using the AdminIterator.
 */
public class IdenticalAdminIteratorTest extends TestBase implements Test {

    /** Number of entries to write */
    private int numberToWrite = 1000;

    /** Blocking factor for the iterator */
    private int blockingFactor = 10;

    /** Are we going to shutdown the service in the middle of the test */
    protected boolean tryShutdown = false;

    /** True if we are going to retry remote exceptions on iterator opts */
    private boolean retry = false;

    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.parse();
        return this;
    }

    /**
     * Parse non-generic option(s).
     * <DL>
     * <DT>-number_to_write<DD> Set the number of entries to
     * write into the space and the delete.  Defaults to 1000.
     * <p>
     * <DT>-blocking_factor<DD> Blocking factor the iterator used to delete
     * the entries.  Defaults to 10.
     * <p>
     * <DT>-tryShutdown<DD> If used the test will attempt to shutdown
     * the service and restart it (if it is activatable) and check to
     * ensure the service's JoinAdmin state is persistent.
     * <p>
     * <DT>-retry_remote_exception<DD> If used the test will attempt
     * retry <code>AdminIterator</code> opertations that throw
     * <code>RemoteException</code>
     * </DL>
     */
    protected void parse() throws Exception {
        super.parse();
        numberToWrite = getConfig().getIntConfigVal("org.apache.river.qa.outrigger."
                + "admin.IdenticalAdminIteratorTest.numberToWrite", 1000);
        blockingFactor = getConfig().getIntConfigVal("org.apache.river.qa.outrigger."
                + "admin.IdenticalAdminIteratorTest.blocking_factor", 10);
        tryShutdown = getConfig().getBooleanConfigVal("org.apache.river.qa.outrigger."
                + "admin.IdenticalAdminIteratorTest.tryShutdown", false);
        retry = getConfig().getBooleanConfigVal("org.apache.river.qa.outrigger."
                + "admin.IdenticalAdminIteratorTest.retry_remote_exception",
                false);
        minPostKillWait = getConfig().getLongConfigVal("org.apache.river.qa.outrigger."
                + "admin.IdenticalAdminIteratorTest.restart_wait", 5000);

        // Log out test options.
        logger.log(Level.INFO, "numberToWrite = " + numberToWrite);
        logger.log(Level.INFO, "blocking_factor = " + blockingFactor);
        logger.log(Level.INFO, "tryShutdown = " + tryShutdown);
        logger.log(Level.INFO, "retry_remote_exception = " + retry);
        logger.log(Level.INFO, "restart_wait = " + minPostKillWait);
    }

    /**
     * Utility to call AdminIterator.next(), retries in the
     * face of RemoteException if retry is true
     */
    private Entry next(AdminIterator i)
            throws RemoteException, UnusableEntryException {
        while (true) {
            try {
                return i.next();
            } catch (RemoteException e) {
                if (!retry) {
                    throw e;
                }
                logger.log(Level.INFO, "Retrying next()");
            }
        }
    }

    /**
     * Utility to call AdminIterator.delete(), retries in the
     * face of RemoteException if retry is true
     */
    private void delete(AdminIterator i) throws RemoteException {
        while (true) {
            try {
                i.delete();
                return;
            } catch (RemoteException e) {
                if (!retry) {
                    throw e;
                }
                logger.log(Level.INFO, "Retrying delete()");
            }
        }
    }

    public void run() throws Exception {
        specifyServices(new Class[] { JavaSpace.class });

        /* how many writes should between dot prints */
        int dotInterval = numberToWrite / 80;

        if (dotInterval < 1) {
            dotInterval = 1;
        }
        final JavaSpace space = (JavaSpace) services[0];
        final Entry aEntry = new UninterestingEntry();
        logger.log(Level.INFO, "Beginning to write test entries");
        int l = 0;

	for (; l < numberToWrite; l++) {
	    addOutriggerLease(space.write(aEntry, null, Lease.ANY), true);
	    
	    if (l % dotInterval == dotInterval - 1) {
		logger.log(Level.INFO, ".");
	    }
	}
        logger.log(Level.INFO, 
		   "\nDone writing test entries, wrote " + numberToWrite
		 + " entires");

        // Shutdown if we want to
        if (tryShutdown) {
            logger.log(Level.INFO, "Trying shutdown...");
	    shutdown(0);
            logger.log(Level.INFO, "success, restarting");
        }
        logger.log(Level.INFO, "Starting to delete entries using iterator");
        AdminIterator i;
        JavaSpaceAdmin admin;

	admin = (JavaSpaceAdmin) ((Administrable) space).getAdmin();
	admin = 
	    (JavaSpaceAdmin) getConfig().prepare("test.outriggerAdminPreparer", 
						 admin);
	i = admin.contents(aEntry, null, blockingFactor);
        int j = 0;

	while (next(i) != null) {
	    delete(i);
	    j++;
	    
	    if (j % dotInterval == dotInterval - 1) {
		logger.log(Level.INFO, ".");
	    }
	}
	
	if (j < numberToWrite) {
	    String msg = "\nCould not delete the number of entries we "
		+ "wrote, only deleted " + j + " entries";
	    throw new TestException(msg);
	} else if (j > numberToWrite) {
	    String msg = "\nDeleted more entries than we wrote, deleted "
		+ j + " entries";
	    throw new TestException(msg);
	}
        logger.log(Level.INFO, "\nFinished deleting, closing iterator");
	i.close();
        logger.log(Level.INFO, 
		   "Opening new iterator to make sure space is empty");
	i = admin.contents(aEntry, null, blockingFactor);
        j = 0;

	while (next(i) != null) {
	    j++;
	    
	    if (j % dotInterval == dotInterval - 1) {
		logger.log(Level.INFO, ".");
	    }
	}
	
	if (j != 0) {
	    String msg = "\n" + j
		+ " entries were left after we deleted all of them";
	    throw new TestException(msg);
	}
        logger.log(Level.INFO, 
		   "\nFinished checking emptiness, closing iterator");

	i.close();
    }
}
