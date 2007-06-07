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
package com.sun.jini.outrigger.logstore;

import com.odi.Database;
import com.odi.DatabaseNotFoundException;
import com.odi.DatabaseRootNotFoundException;
import com.odi.ObjectStore;
import com.odi.Session;
import com.odi.Transaction;
import com.odi.util.OSHashtable;
import com.odi.util.OSVector;

import com.sun.jini.outrigger.OutriggerServerImpl;
import com.sun.jini.system.FileSystem;

import java.io.File;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class manages the ODI database.
 * Note: Only one thread can access the database at a time.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see OutriggerServerImpl
 */
class ODIStoreDB {

    private String		name;		// the space's name
    private String		path;		// path for persistent store
    private String		dbName;		// name of the DB itself
    private Database		db;		// the database
    private Transaction		odiTxn;		// current ODI txn
    private Thread		dbThread;	// db lock owner
    private int			txnNesting;	// nesting depth
    private int			commitMode;	// parameter to commit()
    private Session 		session;	// our session

    /** The properties that govern the ODI garbage collector. */
    private final static Properties	GC_PROPS;

    static {
	GC_PROPS = new Properties(System.getProperties());
	GC_PROPS.put("com.odi.ProGCKey", "StartGCMarkStart");
    }

    /** Logger for logging persistent store related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.storeLoggerName);

    /**
     * Create an <code>ODIStoreDB</code> object for the given
     * <code>name</code> and <code>path</code>.
     */
    ODIStoreDB(String path, String name) {
	this.name = name;
	this.path = path;
	this.commitMode = ObjectStore.RETAIN_HOLLOW;
    }

    /**
     * Set up the ODI persistent store.
     */
    boolean setupStore() {

        dbName = new File(path, name + ".odb").toString();
	logger.log(Level.CONFIG, "dbName = {0}", dbName);

	return openDB();
    }

    /**
     * Open the database.  If the database is being re-opened
     * after a persistent garbage collection, the database
     * can be non-null, but still closed.  This situation
     * is also taken into account.
     */
    private boolean openDB() {

	boolean created = false;

	// Create the session object and set the session field.
	//
	if (session == null) {

	    // We want to disable ODI's cross VM database locking feature
	    // because we do our own locking (activation won't allow
	    // multiple concurrent VM's for the same activatable object,
	    // and we won't create multiple spaces pointing at the same
	    // log) and ODI's locking keeps us from automatically recovering
	    // from crashes
	    //
	    Properties props = new Properties(System.getProperties());
	    props.setProperty("com.odi.useDatabaseLocking", "false");
	    session = Session.createGlobal(null, props);
	}

	// If you re-open the database, you must
	// refresh it
	//
	if ((db != null) && (!db.isOpen())) {
	    logger.log(Level.FINE, "re-opening DB: {0}", dbName);
	    db.open(ObjectStore.UPDATE);
	}

	if (db == null) {         //skip if already open
	    logger.log(Level.FINE, "opening DB: {0}", dbName); 

	    try {
		db = Database.open(dbName, ObjectStore.UPDATE);
	    } catch (DatabaseNotFoundException e) {
		db = Database.create(dbName, 0666);   // make it if necessary
		created = true;
	    }
	}
	return created;
    }

    Database getDb() {
	return db;
    }

    /**
     * Start an ODI txn.
     */
    void startTxn() {
	getDBLock();			// wait for the DB to be ours
    }

    /**
     * End an ODI txn.
     */
    void endTxn(boolean commit) {
	/*
	 * If endTxn is part of a finally and the startTxn never
	 * happened, someone else might have the lock, or nobody
	 * might.
	 */
	if (odiTxn != null && Thread.currentThread() == dbThread)
	    releaseDBLock(commit);
    }

    /**
     * Close the database without removing anything from disk
     */
    void close() {
	if (db != null)
	    db.close();
    }

    /**
     * Remove the database.
     */
    void destroy() {
	try {
	    if (db != null)
		db.destroy();
        } catch (Exception e) {
	    if (logger.isLoggable(Level.INFO)) {
		logger.log(Level.INFO, "Exception encounter while " +
		    "destroying database " + dbName + ", destroy continuing",
		    e);

	    }
        }
    }

    /**
     * Get the DB lock.  All access to the database is single-threaded
     * to avoid complications.  This is inefficient in some cases, but
     * always safe.
     *
     * @see releaseDBLock
     */
    private void getDBLock() {
	try {
	    Thread t = Thread.currentThread();
	    synchronized (db) {
		logger.log(Level.FINE, "Getting db lock");

		if (dbThread == t)	// already have it in this thread
		    txnNesting++;
		else {
		    while (dbThread != null) {
			logger.log(Level.FINE, "dbThread = {0}",
				   dbThread.getName());
			db.wait();
		    }
		    dbThread = t;
		    txnNesting = 0;
		    odiTxn = Transaction.begin(ObjectStore.UPDATE);
		}

		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE, 
			"dbThread set to {0}, nesting = {1}",
			new Object[]{t.getName(), new Integer(txnNesting)});
		}
	    }
	} catch (InterruptedException e) {
	    final String msg = "Unexpected Interruption";
	    final IllegalMonitorStateException ims = 
		new IllegalMonitorStateException(msg);
		
	    logger.log(Level.WARNING, msg, ims);
	    throw ims;
	}
    }

    /**
     * Release the DB lock (previously acquired by <code>getDBLock</code>).
     *
     * @see getDBLock
     */
    private void releaseDBLock(boolean commit) {
	Thread t = Thread.currentThread();
	synchronized (db) {
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, 
			   "releasing db lock held by {0}, nesting = {1}",
			   new Object[]{dbThread.getName(),
                                        new Integer(txnNesting)});
	    }

	    if (dbThread != t) {
		throw new IllegalArgumentException(
		    "releasing DB lock when not held by this thread");
	    }

	    if (txnNesting > 0) {
		--txnNesting;
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE, "nesting now {0}",
			       new Integer(txnNesting));
		}
		return;
	    }

	    if (commit) {
		if (logger.isLoggable(Level.FINE)) {
		    String msg = null;
		    switch(commitMode) {
		        case ObjectStore.RETAIN_STALE:
			  msg = "ObjectStore.RETAIN_STALE";
			  break;
		        case ObjectStore.RETAIN_READONLY:
			  msg = "ObjectStore.RETAIN_READONLY";
			  break;
		        case ObjectStore.RETAIN_UPDATE:
			  msg = "ObjectStore.RETAIN_UPDATE";
			  break;
		        case ObjectStore.RETAIN_HOLLOW:
			  msg = "ObjectStore.RETAIN_HOLLOW";
			  break;
			default:
			  msg = "UNKNOWN";
		    }

		    logger.log(Level.FINE, "odiTxn.commit({0}({1}))",
			       new Object[]{msg, new Integer(commitMode)});
		}

		odiTxn.commit(commitMode);
	    } else {
		logger.log(Level.FINE, "odiTxn.abort()");
		odiTxn.abort();
	    }
	    odiTxn = null;
	    dbThread = null;
	    db.notifyAll();
	}
    }

    /**
     * Return the root for the given <code>name</code>.  If the root is
     * unknown, returns <code>null</code>.
     */
    Object getRoot(String name) {
	Object rootval = null;
	try {
	    rootval = db.getRoot(name);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "ODIStoreDB:getRoot:root {0} found{1}",
			   new Object[]{name, ((rootval==null?", null":""))});
	    }
	    return rootval;
	} catch (DatabaseRootNotFoundException e) {
	    logger.log(Level.FINE, "ODIStoreDB:getRoot:root {0} not found",
		       name);

	    return null;
	}
    }

    /**
     * Set the root for the given <code>name</code> to given
     * <code>value</code>.  If the root does not exist, create it.
     */
    void setRoot(String name, Object value) {
	try {
	    startTxn();
	    db.setRoot(name, value);
	} catch (DatabaseRootNotFoundException e) {
	    db.createRoot(name, value);
	    logger.log(Level.FINE, "creating root {0}", name);
	} finally {
	    endTxn(true);
	}
    }

    /**
     * Run one pass of the database garbage collector, passing
     * <code>GC_PROPS</code> as the properties parameter.  This
     * requires closing an reopening the database, since the GC runs
     * only on closed database.
     */
    void gc() {
	db.close();
	Properties results = db.GC(GC_PROPS);
	if (results == null)
	    logger.log(Level.WARNING, "couldn't GC database, ignoring");
	else if (logger.isLoggable(Level.FINE)) {
	    Object unref = results.getProperty("Unreferenced Objects");
	    if (unref != null) {
		logger.log(Level.FINE, "Database GC''ed {0} object(s)", unref);
	    }
	}
	openDB();
    }
}
