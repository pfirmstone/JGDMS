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
package com.sun.jini.norm;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.config.ConfigurationException;

import com.sun.jini.norm.lookup.SubStore;
import com.sun.jini.reliableLog.LogHandler;
import com.sun.jini.reliableLog.ReliableLog;
import com.sun.jini.system.FileSystem;
import com.sun.jini.thread.ReadersWriter;

/**
 * Class that actually stores a Norm server's state to disk.  Basically
 * a wrapper around ReliableLog with the addition of lock management.
 *
 * @author Sun Microsystems, Inc.
 */
class PersistentStore {
    /** Logger for logging messages for this class */
    private static final Logger logger = Logger.getLogger("com.sun.jini.norm");

    /**
     * Object we use to reliably and persistently log updates to our
     * state, or null if not persistent.
     */
    private ReliableLog log;

    /**
     * No mutation of persistent state can occur during a snapshot,
     * however, we can have multiple mutators, use
     * <code>ReadersWriter</code> object to manage this invariant.  Note
     * as far as the lock is concerned the mutators are the readers and
     * snapshot thread the writer since for us mutation is the
     * non-exclusive operation.
     */
    final private ReadersWriter mutatorLock = new ReadersWriter();

    /**
     * Thread local that tracks if (and how many times) the current thread
     * has acquired a non-exclusive mutator lock.
     */
    final static private ThreadLocal lockState = new ThreadLocal();

    /** Cache a <code>Long</code> object with a zero value */
    final static private Long zero = new Long(0);

    /** Location of the persistent store, or null if not persistent */
    final private File storeLocation;

    /** Object that handles the recovery of logs, or null if not persistent  */
    final private LogHandler logHandler;

    /** The NormServer we are part of */
    final private NormServerBaseImpl server;

    /** Number of updates since last snapshot */
    private int updateCount;

    /** A list of all of the sub-stores */
    private List subStores = new LinkedList();

    /**
     * Construct a store that will persist its data to the specified
     * directory.
     *
     * @param logDir directory where the store should persist its data,
     *        which must exist, unless it is <code>null</code>, in which case
     *	      there is no persistence
     * @param logHandler object that will process the log and last
     *        snapshot to recover the server's state
     * @param server the server is called back after an update so it can
     *        decide whether or not to do a snapshot
     * @throws StoreException if there is a problem setting up the store
     */
    PersistentStore(String logDir, LogHandler logHandler, 
		    NormServerBaseImpl server)
	throws StoreException
    {
	this.logHandler = logHandler;
	this.server = server;
	if (logDir == null) {
	    storeLocation = null;
	} else {
	    storeLocation = new File(logDir);
	    try {
		log = new ReliableLog(
		    storeLocation.getCanonicalPath(), logHandler);
	    } catch (IOException e) {
		throw new CorruptedStoreException(
		    "Failure creating reliable log", e);
	    }

	    try {
		log.recover();
	    } catch (IOException e) {
		throw new CorruptedStoreException(
		    "Failure recovering reliable log", e);	    
	    }
	}
    }

    /**
     * Destroy the store.
     *
     * @throws IOException if it has difficulty removing the log files 
     */
    void destroy() throws IOException {
	// Prep all the sub-stores to be destroyed
	for (Iterator i = subStores.iterator(); i.hasNext(); ) {
	    SubStore subStore = (SubStore) i.next();
	    subStore.prepareDestroy();
	}
	if (log != null) {
	    log.deletePersistentStore();
	    FileSystem.destroy(storeLocation, true);
	}
    }

    /**
     * Inform the store of a sub-store
     */
    void addSubStore(SubStore subStore) throws StoreException {
	try {
	    if (log == null) {
		subStore.setDirectory(null);
	    } else {
		final String subDir = subStore.subDirectory();

		if (subDir == null) {
		    subStore.setDirectory(storeLocation);
		} else {
		    subStore.setDirectory(new File(storeLocation, subDir));
		}
	    }
	    
	    subStores.add(subStore);
	} catch (IOException e) {
	    throw new StoreException("Failure adding substore " + subStore,
				     e);
	} catch (ConfigurationException e) {
	    throw new StoreException("Failure adding substore " + subStore,
				     e);
	}
    }


    /////////////////////////////////////////////////////////////////
    // Methods for obtaining and releasing the locks on the store

    /**
     * Block until we can acquire a non-exclusive mutator lock on the
     * server's persistent state.  This lock should be acquired in a
     * <code>try</code> block and a <code>releaseMutatorLock</code> call
     * should be placed in a <code>finally</code> block.
     */
    void acquireMutatorLock() {
	// Do we already hold a lock?

	Long lockStateVal = (Long) lockState.get();
	if (lockStateVal == null) 
	    lockStateVal = zero;

	final long longVal = lockStateVal.longValue();

	if (longVal == 0) {
	    // No, this thread currently does not hold a lock,
	    // grab non-exclusive lock (which for mutatorLock is a
	    // read lock) 
	    mutatorLock.readLock();
	} 

	// Either way, bump the lock count and update our thread state
	lockState.set(new Long(longVal + 1));
    }

    /**
     * Release one level of mutator locks if this thread holds at least one.
     */
    void releaseMutatorLock() {
	Long lockStateVal = (Long) lockState.get();
	if (lockStateVal == null) 
	    lockStateVal = zero;

	final long longVal = lockStateVal.longValue();

	if (longVal == 0) {
	    // No lock to release, return
	    return;
	}

	if (longVal == 1) {
	    // Last one on stack release lock
	    // Using read lock because we want a non-exclusive lock
	    mutatorLock.readUnlock();
	    lockStateVal = zero;
	} else {
	    lockStateVal = new Long(longVal - 1);
	}

	lockState.set(lockStateVal);
    }

    //////////////////////////////////////////////////////////////////
    // Methods for writing records to the log and taking and
    // coordinating snapshots

    /**
     * Log an update. Will flush to disk before returning.
     *
     * @throws IllegalStateException if the current thread does not hold
     *	       a non-exclusive mutator lock
     * @throws IOException
     * @see ReliableLog#update
     */
    void update(Object o) {
	if (log == null) {
	    return;
	}
	final Long lockStateVal = (Long) lockState.get();
	if (lockStateVal == null || lockStateVal.longValue() == 0) {
	    throw new IllegalStateException("PersistentStore.update:" +
	        "Must acquire mutator lock before calling update()");
	}

	synchronized (this) { 
	    try {
		log.update(o, true);
		updateCount++;
		server.updatePerformed(updateCount);
	    } catch (IOException e) {
		// $$$ should probably be propagating this exception
		logger.log(Level.WARNING, "IOException while updating log", e);
	    }
	}
    }

    /**
     * Generate a snapshot, will perform the necessary locking to ensure no
     * threads are mutating the state of the server before creating the 
     * snapshot.
     *
     * @throws IOException
     * @see ReliableLog#snapshot
     */
    void snapshot() throws IOException {
	if (log == null) {
	    return;
	}
	try {
	    // Using write lock because we want an exclusive lock
	    mutatorLock.writeLock();
	    updateCount = 0;

	    // Don't need to sync on this because
	    // mutatorLock.writeLock() gives us an exclusive lock
	    log.snapshot();
	} finally {
	    // Using write lock because we want an exclusive lock
	    mutatorLock.writeUnlock();
	}
    }
}
