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

import com.sun.jini.outrigger.OutriggerServerImpl;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.space.InternalSpaceException;

/**
 * A class to help you read log files created by <code>LogOutputFile</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see LogOutputFile
 */
class LogInputFile extends LogFile {
    private File		file;	// the current log file

    private static final long	intBytes = 4;

    /** Logger for logging persistent store related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.storeLoggerName);

    /**
     * Return an <code>Iterator</code> that will loop through all
     * the logs that match the given <code>basePath</code> pattern,
     * interpreted as described in the matching <code>LogStream</code>
     * constructor.  If <code>returnAll</code> is <code>false</code>,
     * the most recent file will be left off the list.  This would be
     * the proper value for an ongoing poll looking for completed log
     * files.  You would specify <code>true</code> during recovery,
     * when all existing logs should be committed because no new ones
     * are currently being created
     *
     * @see java.util.Iterator
     */
    // @see LogStream#LogStream(String)
    static Iterator logs(String basePath, boolean returnAll)
	throws IOException
    {
	LogFile lf = new LogFile(basePath);// an object to represent the path
	ArrayList inDir = new ArrayList();
	lf.existingLogs(inDir);

	// strip off most recent if we're not trying to read them all
	if (!returnAll && inDir.size() > 0)
	    inDir.remove(inDir.size() - 1);

	return new LogInputFileIterator(inDir, lf);
    }

    /**
     * The implementation of <code>Iterator</code> returned by
     * <code>LogInputStream.logs</code>.  The <code>next</code> method
     * occasionally returns <code>null</code>.
     *
     * @see LogInputFileIterator#next
     */
    private static class LogInputFileIterator implements Iterator {
	private LogFile		baseLogFile;
	private Iterator	fileList;

	/**
	 * Create a new <code>LogInputFileIterator</code> object
	 * for the given list.
	 */
	LogInputFileIterator(Collection files, LogFile baseLogFile) {
	    this.baseLogFile = baseLogFile;
	    fileList = files.iterator();
	}

	public boolean hasNext() {
	    return fileList.hasNext();
	}

	/**
	 * Return the next <code>File</code> object, or
	 * <code>null</code>.  You will get <code>null</code> when the
	 * file existed at the time of listing, but no longer exists
	 * when the iterator gets to it.  For example, if a process is
	 * consuming all completed logs, the listing might find a log,
	 * but that process may have consumed and removed it by the
	 * time you invoke <code>next</code>, so you will get a
	 * <code>null</code>.
	 */
	public Object next() {
	    File file = (File) fileList.next();
	    try {
		return new LogInputFile(baseLogFile, file);
	    } catch (IOException e) {
		file.delete();	// file is malformed -- remove it
		return null;	// can't throw any reasonable exception,
				// so signal the problem with a null
	    }
	}

	/**
	 * Remove the <code>File</code> object returned by the iterator
	 * from the list.  This does <em>not</em> remove the file
	 * itself.
	 */
	public void remove() {
	    fileList.remove();
	}
    }

    /**
     * Create a new <code>LogInputFile</code>.
     * <p>
     * <b>Note:</b> Don't invoke this.  This is needed by the
     * enumeration returned by <code>logs</code>, which is how you
     * should be getting <code>LogInputFile</code> objects.  When
     * nested classes arrive, this constructor can be properly
     * protected.
     */
    // @see logs
    private LogInputFile(LogFile desc, File path) throws IOException {
	super(desc.baseDir, desc.baseFile);
	file = path;
    }

    /**
     * Consume the input file, invoking the appropriate operations on
     * the given object.
     */
    synchronized void consume(BackEnd opOn) {
	try {
	    DataInputStream din = 
		new DataInputStream(new BufferedInputStream(
					new FileInputStream(file)));
	    ObjectInputStream in = new ObjectInputStream(din);

	    long length = file.length();
	    int fileVer = din.readInt();

	    if (fileVer != LOG_VERSION)
		failure("unsupported log version: " + fileVer);

	    long logBytes = intBytes;
	    int updateLen = din.readInt();

	    Long txnId;
	    int  count;
	    Resource rep;
	    byte[] cookie;

	    while (updateLen != 0) {	/* 0 is expected termination case */

		if (updateLen < 0)	/* serious corruption */
		    failure("file corrupted, negative record length at " +
			    logBytes);

		if (length - logBytes - intBytes < updateLen)

		    /* partial record at end of log; this should not happen
                     * if forceToDisk is always true, but might happen if
                     * buffered updates are used.
                     */
                    failure("file corrupted, partial record at " + logBytes);

		int op = in.readByte();

		switch (op) {
		  case BOOT_OP:
		    long time = in.readLong();
		    long sessionId = in.readLong();
		    opOn.bootOp(time, sessionId);
		    break;

		  case JOINSTATE_OP:
		    BaseObject state = (BaseObject)in.readObject();
		    opOn.joinStateOp(state);
		    break;

		  case WRITE_OP:
		    rep = (Resource)in.readObject();
		    txnId = (Long)in.readObject();
		    opOn.writeOp(rep, txnId);
		    break;

		  case BATCH_WRITE_OP:
		    txnId = (Long)in.readObject();
		    count = in.readInt();
		    for (int i=0; i<count; i++) {
			rep = (Resource)in.readObject();
			opOn.writeOp(rep, txnId);
		    }
		    break;

		  case TAKE_OP:
		    cookie = new byte[16]; 
		    in.readFully(cookie);
		    txnId = (Long)in.readObject();
		    opOn.takeOp(cookie, txnId);
		    break;

		  case BATCH_TAKE_OP:
		    txnId = (Long)in.readObject();
		    count = in.readInt();
		    for (int i=0; i<count; i++) {
			cookie = new byte[16];
			in.readFully(cookie);
			opOn.takeOp(cookie, txnId);
		    }
		    break;

		  case REGISTER_OP:
		    Registration registration = 
			(Registration)in.readObject();
		    opOn.registerOp(registration);
		    break;

		  case RENEW_OP:
		    cookie = new byte[16];
		    in.readFully(cookie);
		    long expires = in.readLong();
		    opOn.renewOp(cookie, expires);
		    break;

		  case CANCEL_OP:
		    cookie = new byte[16];
		    in.readFully(cookie);
		    opOn.cancelOp(cookie);
		    break;

		  case PREPARE_OP:
		    txnId = (Long)in.readObject();
		    BaseObject transaction = (BaseObject)in.readObject();
		    opOn.prepareOp(txnId, transaction);
		    break;

		  case COMMIT_OP:
		    txnId = (Long)in.readObject();
		    opOn.commitOp(txnId);
		    break;

		  case ABORT_OP:
		    txnId = (Long)in.readObject();
		    opOn.abortOp(txnId);
		    break;

		  case UUID_OP:
		    final byte uuid[] = new byte[16];
		    in.readFully(uuid);
		    opOn.uuidOp(uuid);
		    break;

		  default:
                    failure("log record corrupted, unknown opcode");

		}  // case

		logBytes += (intBytes + updateLen);

		// deal with padding
		int offset = (int)logBytes & 3;
		if (offset > 0) {
		    offset = 4 - offset;
		    logBytes += offset;
		    din.skipBytes(offset);
		}
		updateLen = din.readInt();

	    }  // while
	} catch (EOFException e) {
	    failure("unexpected end-of-file", e);

	} catch (IOException e) {
	    failure("I/O error while consuming logs", e);

	} catch (ClassNotFoundException e) {
	    failure("unexpected class?", e);
	}
    }

    /**
     * Report a failure consuming the log file and throw an
     * <code>InternalSpaceException</code> containing <code>message</code>.
     */
    private void failure(String message) {
	failure(message, null);
    }

    /**
     * Report a exception while consuming the log file and throw an
     * <code>InternalSpaceException</code> containing <code>message</code>.
     */
    private void failure(String message, Exception e) {
	String errorMsg = "Error consuming log file: " + file + ", " + 
	    message + "Log file consumption stopped";

	final InternalSpaceException ise =
	    new InternalSpaceException(errorMsg, e);
	logger.log(Level.SEVERE, errorMsg, ise);
	throw ise;
    }

    /**
     * This log has been successfully drained, and committed -- it can be
     * removed.
     */
    void finished() {
	file.delete();
    }

    public String toString() {
	return file.toString();
    }
}
