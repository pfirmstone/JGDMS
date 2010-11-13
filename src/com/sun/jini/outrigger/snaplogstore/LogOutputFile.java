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

import com.sun.jini.outrigger.LogOps;
import com.sun.jini.outrigger.OutriggerServerImpl;
import com.sun.jini.outrigger.StorableObject;
import com.sun.jini.outrigger.StorableResource;
import net.jini.id.Uuid;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.space.InternalSpaceException;

/**
 * A class to write a log file, to be read later by
 * <code>LogInputFile</code>.  Each operation on the file is forced to
 * disk, so when the operation logging function returns, the data is
 * committed to the log in a recoverable way.
 * <p>
 * <code>LogOutputFile</code> cannot extend <code>Observable</code>
 * because it must extend <code>LogFile</code> (clearly
 * <code>Observable</code> should have been an interface).  It acts as
 * an <code>Observable</code> by having a method that returns its
 * "observable part", which is an object that reports observable
 * events.  Right now the only observable event is the switching to a
 * new physical file when the current one becomes full.
 *
 * @author Sun Microsystems, Inc.
 * @see LogInputFile
 * @see java.util.Observable
 */
class LogOutputFile extends LogFile implements LogOps {
    private RandomAccessFile	logFile = null;// the current output log file
    private FileDescriptor	logFD;	   // the current log file descriptor
    private ObjectOutputStream	out;	   // objects written
    private int			suffix;	   // the current suffix number
    private int			opCnt;	   // number of ops on current file
    private int			maxOps;	   // max ops to allow in file
    private Observable		observable;// handle Observer/Observable

    private long logBytes = 0;
    private final byte[] intBuf = new byte[4];
    private final byte[] zeroBuf = new byte[4];

    private long deferedUpdateLength = 0;
    private long deferedPosition = 0;

    private static final long intBytes = 4;

    /** Logger for logging persistent store related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.storeLoggerName);

    /**
     * Create a <code>LogOutputFile</code> object that will stream
     * output to a series of files described by <code>basePath</code>,
     * as interpreted by the relevant <code>LogFile</code>
     * constructor.  When the file becomes full (the maximum number of
     * operations is reached), the file is closed and a new file with
     * the next highest suffix is created.  The
     * <code>Observable</code> notification for this event passes a
     * <code>File</code> argument for the filled file as the argument
     * to <code>Observer</code>.
     * 
     * @see #observable()
     */
    //@see com.sun.jini.mercury.LogStream#LogStream(String)
    LogOutputFile(String basePath, int maxOps) throws IOException {
	super(basePath);
	ArrayList inDir = new ArrayList();
	suffix = existingLogs(inDir);
	this.maxOps = maxOps;
	nextPath();
    }

    /**
     * Return an <code>Observable</code> object that represents this object
     * in the Observer/Observable pattern.
     *
     * @see java.util.Observer
     */
    Observable observable() {
	if (observable == null) {	     // defer allocation until needed
	    observable = new Observable() {  // we only use this if changed
		public void notifyObservers() {
		    setChanged();
		    super.notifyObservers();
		}
		public void notifyObservers(Object arg) {
		    setChanged();
		    super.notifyObservers(arg);
		}
	    };
	}
	return observable;
    }

    /**
     * Switch this over to the next path in the list
     */
    private void nextPath() throws IOException {
	boolean completed = false;

	if (logFile != null) {

	    // If there was a deferred header, write it out now
	    //
	    if (deferedUpdateLength != 0) {
		logFD.sync();		// force the bytes to disk
		logFile.seek(deferedPosition);
		writeInt((int)deferedUpdateLength);
	    }
	    try {
		close();   	        // close the stream and the file
	    } catch (IOException ignore) { } // assume this is okay
	    completed = true;
	}

	suffix++;			// go to next suffix
	logFile = new RandomAccessFile(baseDir.getPath() + File.separator +
				       baseFile + suffix, "rw");
	logFD = logFile.getFD();
	out = new ObjectOutputStream(new LogOutputStream(logFile));

	writeInt(LOG_VERSION);

	logBytes = logFile.getFilePointer();
	logFile.setLength(logBytes);

	// always start out with zero length header for the next update
	logFile.write(zeroBuf);

	// force length header to disk 
	logFD.sync();

	deferedUpdateLength = 0;
	opCnt = 0;

	/*
	 * Tell consumer about the completed log.  This is done after the
	 * new one is created so that the old path can be known not
	 * to be the newest (because something newer is there).
	 */
	if (observable != null && completed)
	    observable.notifyObservers();
    }

    /**
     * Close the log, but don't remove it.
     */
    synchronized void close() throws IOException {
	if (logFile != null) {
	    try {
		out.close();
		logFile.close();
	    } finally {
		logFile = null;
	    }
	}
    }

    /**
     * Override destroy so we can try to close logFile before calling
     * super tries to delete all the files.
     */
    void destroy() {
	try {
	    close();
	} catch (Throwable t) {
	    // Don't let failure keep us from deleting the files we can	    
	}
	super.destroy();
    }

    /**
     * Log a server boot.
     */
    public synchronized void bootOp(long time, long sessionId) {
	try {
	    out.writeByte(BOOT_OP);
	    out.writeLong(time);
	    out.writeLong(sessionId);
	    flush();
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Log a change in join state
     */
    public synchronized void joinStateOp(StorableObject state) {
	try {
	    out.writeByte(JOINSTATE_OP);
	    out.writeObject(new BaseObject(state));
	    flush();
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Log a <code>write</code> operation.
     */
    public synchronized void writeOp(StorableResource entry, Long txnId) {
	try {
	    out.writeByte(WRITE_OP);
	    out.writeObject(new Resource(entry));
	    out.writeObject(txnId);

	    // A write operation under a transaction does not need to be
	    // flushed until it is prepared.
	    //
	    flush(txnId == null);
	} catch (IOException e) {
	    failed(e);
	}
    }

    // Inherit java doc from supertype
    public synchronized void writeOp(StorableResource entries[], Long txnId) {
	try {
	    out.writeByte(BATCH_WRITE_OP);
	    out.writeObject(txnId);

	    // In the middle of records we need to use the stream's
	    // writeInt, not our private one	    
	    out.writeInt(entries.length);
	    for (int i=0; i<entries.length; i++) {
		out.writeObject(new Resource(entries[i]));
	    }

	    // A write operation under a transaction does not need to be
	    // flushed until it is prepared.
	    //
	    flush(txnId == null, entries.length);
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Log a <code>take</code> operation.
     */
    public synchronized void takeOp(Uuid cookie, Long txnId) {
	try {
	    out.writeByte(TAKE_OP);
	    cookie.write(out);
	    out.writeObject(txnId);

	    // A take operation under a transaction does not need to be
	    // flushed until it is prepared.
	    //
	    flush(txnId == null);
	} catch (IOException e) {
	    failed(e);
	}
    }

    // Inherit java doc from supertype
    public synchronized void takeOp(Uuid cookies[], Long txnId) {
	try {
	    out.writeByte(BATCH_TAKE_OP);
	    out.writeObject(txnId);

	    // In the middle of records we need to use the stream's
	    // writeInt, not our private one	    
	    out.writeInt(cookies.length);
	    for (int i=0; i<cookies.length; i++) {
		cookies[i].write(out);
	    }

	    // A take operation under a transaction does not need to be
	    // flushed until it is prepared.
	    //
	    flush(txnId == null, cookies.length);
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Log a <code>notify</code> operation.
     */
    public synchronized void registerOp(StorableResource registration,
					String type, StorableObject[] templates) 
    {
	try {
	    out.writeByte(REGISTER_OP);
	    out.writeObject(new Registration(registration, type, templates));
	    flush();
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Log a <code>renew</code> operation.
     */
    public synchronized void renewOp(Uuid cookie, long expiration) {
	try {
	    out.writeByte(RENEW_OP);
	    cookie.write(out);
	    out.writeLong(expiration);
	    flush();
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Log a <code>cancel</code> operation.
     */
    public synchronized void cancelOp(Uuid cookie, boolean expired) {
	try {
	    out.writeByte(CANCEL_OP);
	    cookie.write(out);

	    // cancels due to expiration don't need to be flushed
	    // right away
	    flush(!expired);
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Log a transaction <code>prepare</code> operation.
     */
    public synchronized void prepareOp(Long txnId,
				       StorableObject transaction) {
	try {
	    out.writeByte(PREPARE_OP);
	    out.writeObject(txnId);
	    out.writeObject(new BaseObject(transaction));
	    flush();
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Log a transaction <code>commit</code> operation.
     */
    public synchronized void commitOp(Long txnId) {
	try {
	    out.writeByte(COMMIT_OP);
	    out.writeObject(txnId);
	    flush();
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Log a transaction <code>abort</code> operation.
     */
    public synchronized void abortOp(Long txnId) {
	try {
	    out.writeByte(ABORT_OP);
	    out.writeObject(txnId);
	    flush();
	} catch (IOException e) {
	    failed(e);
	}
    }

    public synchronized void uuidOp(Uuid uuid) {
	try {
	    out.writeByte(UUID_OP);
	    uuid.write(out);
	    flush();
	} catch (IOException e) {
	    failed(e);
	}
    }

    /**
     * Flush the current output after an operation.  If the number of
     * operations is exceeded, shift over to the next path.  
     */
    private void flush() throws IOException {
	flush(true);
    }
    

    /**
     * Conditionally flush the current output. If the number of
     * operations is exceeded, shift over to the next path even if
     * <code>forceToDisk</code> is <code>false</code>.
     */
    private synchronized void flush(boolean forceToDisk) 
	throws IOException 
    {
	flush(forceToDisk, 1);
    }

    /**
     * Conditionally flush the current output. If the number of
     * operations is exceeded, shift over to the next path even if
     * <code>forceToDisk</code> is <code>false</code>.
     */
    private synchronized void flush(boolean forceToDisk,
				    int effectiveOpCount)
	throws IOException 
    {
	assert effectiveOpCount > 0;

	out.flush();

	if (forceToDisk) {

	    // must force contents to disk before writing real length header
	    logFD.sync();
	}

	long entryEnd = logFile.getFilePointer();
	long updateLen = entryEnd - logBytes - intBytes;

        // If we are not forcing to disk, we want to defer the write of the
        // first header. This will leave a zero just after the last sync'ed
        // record and will assure that LogInputFile will not read a partially
        // written record.
        //
        if (!forceToDisk) {

	    // If this is the first flush(false) we save the header information
	    // and location for later. Otherwise we write out the header
	    // normally.
	    //
	    if (deferedUpdateLength == 0) {
		deferedUpdateLength = updateLen;  // save the header length
		deferedPosition = logBytes;       // and position for later
	    } else {
		// write real length header
		logFile.seek(logBytes);
		writeInt((int)updateLen);
	    }
	} else {

	    // If there was a deferred header, write that out now and
	    // then write the current header.
	    //
	    if (deferedUpdateLength != 0) {
		logFile.seek(deferedPosition);
		writeInt((int)deferedUpdateLength);
		deferedUpdateLength = 0;
	    }
	    // write real length header
	    logFile.seek(logBytes);
	    writeInt((int)updateLen);
	}

	// pad out update record so length header does not span disk blocks
	entryEnd = (entryEnd + 3) & ~3L;

	// write zero length header for next update
	logFile.seek(entryEnd);
	logFile.write(zeroBuf);
	logBytes = entryEnd;

	if (forceToDisk)
	    logFD.sync();
	
	opCnt += effectiveOpCount;
	if (opCnt >= maxOps)
	    nextPath();
	else
	    out.reset();		// not critical to flush this
    }

    /**
     * Write an int value in single write operation. Note we only use
     * this method when writing log file and recored headers.  We
     * can't use it inside records because the data inside records is
     * written/read using <code>ObjectIn/OutputStream</code> and this
     * method writes directly to the <code>RandomAccessFile</code>.
     *   
     * @param val int value
     * @throws IOException if any other I/O error occurs 
     */
    private void writeInt(int val) throws IOException {
	intBuf[0] = (byte) (val >> 24);
	intBuf[1] = (byte) (val >> 16);
	intBuf[2] = (byte) (val >> 8);
	intBuf[3] = (byte) val;
	logFile.write(intBuf);
    }

    private void failed(Exception e) throws InternalSpaceException {
	logger.log(Level.SEVERE, 
		   "Unexpected I/O error while persisting Space data",
		   e);
	System.exit(-5);
    }
}
