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
package com.sun.jini.outrigger.snaplogstore;

import com.sun.jini.constants.TimeConstants;
import com.sun.jini.logging.Levels;
import com.sun.jini.outrigger.Recover;
import com.sun.jini.outrigger.StoredObject;
import com.sun.jini.outrigger.OutriggerServerImpl;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.space.InternalSpaceException;

/**
 * Back end of snapshot log store. This class consumes logs written by
 * LogOutputFile and stores the state as serilalzied objects. The class
 * processes the logs to optimize what is stored in the snapshot. For
 * example, a take log record will cause the removal of a write log
 * record with the same id (if the transaction is null).  <p>
 *
 * Likewise, cancels will cause the removal of write and register
 * records.  Also renew records update the expiration of the entry or
 * registration and are not stored directly in the database. 
 */
class BackEnd implements Observer {

    // The following data represent the persistent
    // state.
    private Long	  sessionId;
    private StoredObject  joinState;
    private Map   	  entries;
    private Map 	  registrations;
    private Map 	  pendingTxns;
    private byte          topUuid[];
    private LastLog	  lastLog;

    /** Number of times to attempt to restart the consumer thread. */
    private int		retry = 3;

    /** Snapshot object */
    private SnapshotFile	snapshotFile;

    /** Keep logs and snapshot tied, though not necessary */
    private final int SNAPSHOT_VERSION = LogFile.LOG_VERSION;

    /**
     * The base name for the log files.
     */
    private String	logFileBase;

    /**
     * The base name for the snapshot files
     */
    private String	snapshotFileBase;

    /**
     * Log file consumer thread.
     */
    private ConsumerThread	consumer;

    /** Max time to wait for the consumer thread to die on destroy */
    private final static long WAIT_FOR_THREAD = 1 * TimeConstants.MINUTES;

    /** Logger for logging persistent store related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.storeLoggerName);

    /**
     * Create a new <code>BackEnd</code> with the given <code>path</code>.
     */
    BackEnd(String path) {
	logFileBase = new File(path, LogFile.LOG_TYPE).getAbsolutePath();
	snapshotFileBase = new File(path, "Snapshot.").getAbsolutePath();
    }

    /**
     * Setup the database store and recover any existing state.
     */
    void setupStore(Recover space) {

	// Recover the snapshot (if any)
	//
	recoverSnapshot();

	// Consume any remaining log files.
	//
	consumeLogs(true);

	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "recoverSnapshot: number of entries:{0}, " +
			"number of pendingTxns:{1}, number of registrations:{2}",
			new Object[]{new Integer(entries.size()),
			new Integer(pendingTxns.size()),
			new Integer(registrations.size())});
	}

	// Recover the session id
	//
	if (sessionId != null)
	    space.recoverSessionId(sessionId.longValue());

	// Recover the top level Uuid
	//
	if (topUuid != null)
	    space.recoverUuid(ByteArrayWrapper.toUuid(topUuid));

	// Recover the join state
	//
	if (joinState != null) {
	    try {
		space.recoverJoinState(joinState);
	    } catch (Exception e) {
		throw logAndThrowRecoveryException(
		    "Error recovering join state", e);
	    }
	}

	// Recover the entries
	//
	try {
	    Iterator i = entries.values().iterator();

	    while (i.hasNext()) {
		space.recoverWrite((Resource)i.next(), null);
	    }
	} catch (Exception e) {
	    throw logAndThrowRecoveryException("Error recovering entries", e);
	}

	// Recover the prepared transactions and remove any
	// non-prepared ones.
	try {
	    Iterator i = pendingTxns.values().iterator();

	    while (i.hasNext()) {
		PendingTxn pt = (PendingTxn)i.next();

		// If the pending transaction was not recovered
		// (i.e. it was not prepared) then we can remove it.
		//
		if(!pt.recover(space))
		    i.remove();
	    }
	} catch (Exception e) {
	    throw logAndThrowRecoveryException("Error recovering transactions",
					       e);
	}

	// Recover the registrations
	//
	try {
	    Iterator i = registrations.values().iterator();

	    while (i.hasNext()) {
		Registration reg = (Registration)i.next();

		final BaseObject[] templates = reg.getTemplates();

		space.recoverRegister(reg, reg.getType(), templates);
	    }
	} catch (Exception e) {
	    throw logAndThrowRecoveryException(
                "Error recovering registrations", e);
	}
	startConsumer();
    }

    private void recoverSnapshot() {
	try {
	    File[] snapshot = new File[1];
	    snapshotFile = new SnapshotFile(snapshotFileBase, snapshot);

	    if (snapshot[0] == null) {

		// no snapshot, initialize fields and return
		sessionId	= null;
		entries		= new HashMap();
		registrations	= new HashMap();
		pendingTxns	= new HashMap();
		topUuid		= null;
		lastLog		= null;
		return;
	    } 

	    final ObjectInputStream in =
		new ObjectInputStream(new BufferedInputStream(
		    new FileInputStream(snapshot[0])));

	    final int version = in.readInt();
	    if (version != SNAPSHOT_VERSION) {
		logAndThrowRecoveryException(
		    "Wrong file version:" + version, null);
	    }

	    sessionId	= (Long)in.readObject();
	    joinState	= (StoredObject)in.readObject();
	    entries	= (Map)in.readObject();
	    registrations = (Map)in.readObject();
	    pendingTxns	= (Map)in.readObject();
	    topUuid	= (byte[])in.readObject();
	    lastLog	= (LastLog)in.readObject();
	    in.close();
	} catch (RuntimeException t) {
	    throw t;
	} catch (Throwable t) {
	    throw logAndThrowRecoveryException("Problem recovering snapshot",t);
	}
    }

    private void startConsumer() {

	// Create and start the log consumer thread
	//
	consumer = new ConsumerThread();
	consumer.start();
    }

    /**
     * Thread to consume log files. <code>LogOutputFile</code> calls
     * <code>update</code> (through the <code>Observer</code> interface
     * each time a log file is written.
     */
    private class ConsumerThread extends Thread {

	private boolean more = false;
	volatile private boolean interrupted = false;

	ConsumerThread() {}

	public void run() {
	    try {
		while (!interrupted) {

		    // This block is first because when start is
		    // called in setup there will not be any log files
		    // to process. LogOutputFile is created after
		    // setup returns.
		    //
		    synchronized(this) {
			while (!more)
			    wait();
			more = false;
		    }

		    // There is a small window between the wait and
		    // the consumeLogs where update can be called,
		    // setting more to true and yet consumeLogs
		    // actually consumes the log file that caused the
		    // update. This unlikely situation is ok since
		    // consumeLogs does the right thing if there are
		    // no logs to process We could sync around
		    // consumeLogs but we don't want LogOutputFile to
		    // wait.
		    //
		    consumeLogs(false);
		}
	    } catch (InterruptedException exit) {}
	}

	// Cause the thread to consume a log file.
	//
	synchronized private void update() {
	    more = true;	// For the case it is processing log files
	    notify();		// For the case is it waiting
	}

	// Set a local flag just in case someone clears the thread's own
	// interrupted status.
	//
	public void interrupt() {
	    interrupted = true;
	    super.interrupt();
	}
    }

    //---------------------
    // Required by Observer
    //---------------------

    public void update(Observable source, Object arg) {

	if (!consumer.isAlive()) {
	    if (retry > 0) {
		logger.log(Level.INFO, 
			   "Consumer thread died, attempting restart");
		retry--;
		startConsumer();
	    } else {
		logger.log(Level.SEVERE, 
			   "Consumer thread no longer running");
		return;
	    }
	}
	consumer.update();
    }

    /**
     * Destroy the consumer thread and database
     */
    void destroy() {
	try {
	    consumer.interrupt();

	    // wait for consumeLogs to finish in order to avoid errors
	    // once the database and log files are destroyed.
	    //
	    consumer.join(WAIT_FOR_THREAD);

	} catch (InterruptedException ignore) {
	} finally {
	    try {
		if (snapshotFile != null)
		    snapshotFile.destroy();
	    } catch (Throwable t) {
		logger.log(Level.INFO, 
		    "Exception encounter while destroying store", t);
	    }
	}
    }

    /**
     * Stop the consumer and close the database.
     */
    void close() {
	consumer.interrupt();
	// Wait forever, can't close database until 
	// consumer stops (which during startup should
	// not be long.
	try {
	    consumer.join();
	} catch (InterruptedException e) {
	    // never happens
	}
	if (snapshotFile != null) {
	    try {
		snapshotFile.close();
	    } catch (Throwable t) {
		logger.log(Level.INFO, 
		    "Exception encounter while closing store", t);
	    }
	}
    }

    /**
     * Return the pending transaction description for the given
     * transaction, creating the object and adding it to the table if
     * necessary.
     */
    private PendingTxn pendingTxn(Long txnId) {
	PendingTxn pt = (PendingTxn)pendingTxns.get(txnId);
	if (pt == null) {
	    pt = new PendingTxn(txnId);
	    pendingTxns.put(txnId, pt);
	}
	return pt;
    }

    /**
     * Remove a pending transaction from the table.  If it isn't there,
     * this call is harmless.
     */
    private void removePendingTxn(Long txnId) {
	pendingTxns.remove(txnId); // if it fails, it wasn't there to remove
    }

    // ------------------------------------------------------------
    //                  Log stuff
    // ------------------------------------------------------------

    // The following methods are called when a recovered log element
    // is read from the log file. Some methods, writeOp and takeOp
    // can also be called when a pending transaction is committed.
    //

    /**
     * This method sets the session id in the database. It's value is
     * only used during recovery after a restart.
     */
    void bootOp(long time, long session) {
	sessionId = new Long(session);
	if (logger.isLoggable(Level.FINE))
	    logger.log(Level.FINE, "bootOp({0})", new Date(time));
    }

    /**
     * Record the join state.
     */
    void joinStateOp(StoredObject state) {
	joinState = state;
	if (logger.isLoggable(Level.FINE))
	    logger.log(Level.FINE, "joinStateOp()");
    }

    /**
     * This method records a logged write operation. If under a
     * transaction the resource is held in a list for the pending
     * transaction. When committed this method will be called again
     * with the resource and a null transaction id.
     */
    void writeOp(Resource entry, Long txnId ) {
	if (logger.isLoggable(Level.FINE)) { 
	    logger.log(Level.FINE, "writeOp({0},{1})", 
		       new Object[]{entry,txnId});
	}

	if (txnId != null)
	    pendingTxn(txnId).addWrite(entry);
	else
	    entries.put(entry.getCookieAsWrapper(), entry);
    }

    /**
     * This method records a logged take operation. If under a
     * transaction the resource is held in a list for the pending
     * transaction. When committed this method will be called again
     * with the resource and a null transaction id.  
     */
    void takeOp(byte cookie[], Long txnId) {
	if (logger.isLoggable(Level.FINE)) { 
	    logger.log(Level.FINE, "takeOp({0},{1})", 
		       new Object[]{ByteArrayWrapper.toUuid(cookie),txnId});
	}

	if (txnId != null)
	    pendingTxn(txnId).addTake(cookie);
	else
	    entries.remove(new ByteArrayWrapper(cookie));
    }

    /*
     * This method records a logged event registration.
     */
    void registerOp(Registration registration) {
	logger.log(Level.FINE, "registerOp({0})", registration);

	registrations.put(registration.getCookieAsWrapper(), registration);
    }

    /**
     * This method processes a logged renew operation. Renew operations
     * apply to resources passed into writeOp and registerOp.
     */ 
    void renewOp(byte cookie[], long expiration) {
	if (logger.isLoggable(Level.FINE)) { 
	    logger.log(Level.FINE, "renewOp({0},{1})", 
		       new Object[]{ByteArrayWrapper.toUuid(cookie),
				    new Long(expiration)});
	}
	final ByteArrayWrapper baw = new ByteArrayWrapper(cookie);

	Resource resource;

	if ((resource = (Resource)entries.get(baw)) == null) {
	    // not an entry, try event registrations
	    if ((resource = (Resource)registrations.get(baw)) == null) {

		// No registration either, try transactional writes
		Iterator i = pendingTxns.values().iterator();
		while (i.hasNext()) {
		    if ((resource = ((PendingTxn)i.next()).get(baw)) != null)
			break;
		}
	    }
	}
	if (resource != null)
	    resource.setExpiration(expiration);
    }

    /**
     * This method processes a logged cancel operation. Cancel operations
     * apply to resources passed into writeOp and registerOp.
     */ 
    void cancelOp(byte cookie[]) {
	if (logger.isLoggable(Level.FINE)) { 
	    logger.log(Level.FINE, "cancelOp({0})", 
		       ByteArrayWrapper.toUuid(cookie));
	}
	final ByteArrayWrapper baw = new ByteArrayWrapper(cookie);

	if (entries.remove(baw) == null) {
	    if (registrations.remove(baw) == null) {

		Iterator i = pendingTxns.values().iterator();
		while (i.hasNext()) {
		    if (((PendingTxn)i.next()).remove(baw) != null)
			break;
		}
	    }
	}
    }

    /**
     * This method prepares a pending transaction.
     */
    void prepareOp(Long txnId, StoredObject transaction) {
	logger.log(Level.FINE, "prepareOp({0})", txnId);

	PendingTxn pt = pendingTxn(txnId);
	pt.prepare(transaction);
    }

    /**
     * This method commits a pending transaction.
     */
    void commitOp(Long txnId) {
	logger.log(Level.FINE, "commitOp({0})", txnId);

	PendingTxn pt = pendingTxn(txnId);
	pt.commit(this);
	removePendingTxn(txnId);
    }

    /**
     * This method aborts a pending transaction.
     */
    void abortOp(Long txnId) {
	logger.log(Level.FINE, "abortOp({0})", txnId);

	removePendingTxn(txnId);
    }

    /**
     * This method records the service's top level <code>Uuid</code>
     * @param uuid The service's <code>Uuid</code> represented as a
     *             <code>byte[16]</code>.
     */
    void uuidOp(byte[] uuid) {
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "uuidOp({0})", 
		       ByteArrayWrapper.toUuid(uuid));
	}

	topUuid = uuid;
    }

    /**
     * Consume the log files that exist.  If <code>all</code> is
     * <code>true</code>, all found log files will be processed.
     * If <code>log</code> is <code>false</code>, then all but the
     * most recent will be processed; this will prevent the back
     * end from reading the log file that is currently being
     * produced by the front end.
     */
    private void consumeLogs(boolean all) {
	Iterator it;
	try {
	    it = LogInputFile.logs(logFileBase, all);
	} catch (IOException e) {
	    final String msg = "couldn't open logs";
	    final InternalSpaceException ise = 
		new InternalSpaceException(msg, e);
	    logger.log(Level.SEVERE, msg , ise);
	    throw ise;
	}

	while (it.hasNext()) {
	    LogInputFile log = (LogInputFile)it.next();
	    logger.log(Level.FINE, "processing {0})", log);

	    if (log == null)		// file already consumed
		continue;
	
	    try {
		String logFile = log.toString();
		if (lastLog == null || !lastLog.sameAs(logFile))
		    log.consume(this);
		lastLog = new LastLog(logFile);

		ObjectOutputStream out = snapshotFile.next();

		out.writeInt(SNAPSHOT_VERSION);
		out.writeObject(sessionId);
		out.writeObject(joinState);
		out.writeObject(entries);
		out.writeObject(registrations);
		out.writeObject(pendingTxns);
		out.writeObject(topUuid);
		out.writeObject(lastLog);
		snapshotFile.commit();
	    } catch (IOException e) {
		final String msg = "error writing snapshot";
		final InternalSpaceException ise = 
		new InternalSpaceException(msg, e);
			logger.log(Level.SEVERE, msg , ise);
		throw ise;
	    }
	    log.finished();
	}
    }

    /**
     * This class remembers which log file was the last to be
     * successfully consumed.  If the recovery mechanism reopens this
     * file, then it will skip its contents -- this indicates a crash
     * happened after the contents were committed to the snapshot but
     * before the file was unlinked.  
     */
    private static class LastLog implements Serializable {
	private String	logFile;
	private long	timeStamp;

	LastLog(String path) {
	    logFile = path;
	    timeStamp = new File(logFile).lastModified();
	}

	boolean sameAs(String otherPath) {
	    if (!logFile.equals(otherPath))
		return false;
	    return (new File(otherPath).lastModified() == timeStamp);
	}
    }

    /**
     * Log and throw an InternalSpaceException to flag a store
     * recovery problem.
     */
    private InternalSpaceException logAndThrowRecoveryException(
	    String msg, Throwable nested)
    {
	final InternalSpaceException e = 
	    new InternalSpaceException(msg, nested);
	logger.log(Level.SEVERE, msg, e);
	throw e;
    }
}
