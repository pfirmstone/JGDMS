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

package com.sun.jini.reliableLog;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.SyncFailedException;

/**
 * This class is a simple implementation of a reliable Log.  The
 * client of a ReliableLog must provide a set of callbacks (via a
 * LogHandler) that enables a ReliableLog to read and write snapshots
 * (checkpoints) and log records.  This implementation ensures that the
 * data stored (via a ReliableLog) is recoverable after a system crash.
 * The implementation is unsynchronized; the client must synchronize
 * externally. <p>
 *
 * The secondary storage strategy is to record values in files using a
 * representation of the caller's choosing.  Two sorts of files are
 * kept: snapshots and logs.  At any instant, one snapshot is current.
 * The log consists of a sequence of updates that have occurred since
 * the current snapshot was taken.  The current stable state is the
 * value of the snapshot, as modified by the sequence of updates in
 * the log.  From time to time, the client of a ReliableLog instructs
 * the package to make a new snapshot and clear the log.  A ReliableLog
 * arranges disk writes such that updates are stable (as long as the
 * changes are force-written to disk) and atomic: no update is lost,
 * and each update either is recorded completely in the log or not at
 * all.  Making a new snapshot is also atomic. <p>
 *
 * Normal use for maintaining the recoverable store is as follows: The
 * client maintains the relevant data structure in virtual memory.  As
 * updates happen to the structure, the client informs the ReliableLog
 * (call it "log") by calling log.update.  Periodically, the client
 * calls log.snapshot to provide the current complete contents of the
 * data.  On restart, the client calls log.recover to obtain the
 * latest snapshot and the following sequences of updates; the client
 * applies the updates to the snapshot to obtain the state that
 * existed before the crash. <p>
 *
 * @author Sun Microsystems, Inc.
 *
 * @see LogHandler
 *
 */

public class ReliableLog {

    private static final String snapshotPrefix = "Snapshot.";
    private static final String logfilePrefix = "Logfile.";
    private static final String versionFile = "Version_Number";
    private static final int MAGIC = 0xf2ecefe7;
    private static final int FORMAT_UNPADDED = 0;
    private static final int FORMAT_PADDED = 1;
    private static final long intBytes = 4;

    private final File dir;		// base directory
    private int version = 0;		// current snapshot and log version
    private int format = FORMAT_UNPADDED;
    private String logName = null;
    private RandomAccessFile log = null;
    private FileDescriptor logFD;
    private long snapshotBytes = 0;
    private long logBytes = 0;
    private final LogHandler handler;
    private final byte[] intBuf = new byte[4];
    private final byte[] zeroBuf = new byte[4];
 
    /**
     * Creates a ReliableLog to handle snapshots and logging in a
     * stable storage directory, and sets up to recover any existing data
     * from the stable storage directory. If there is no existing data,
     * snapshot must be called next, otherwise recover must be called next.
     *
     * @param dirPath path to the stable storage directory
     * @param handler the handler for log callbacks
     *
     * @throws LogException if the directory cannot be created or
     * the current version in the directory is corrupted
     * @throws IOException if any other I/O error occurs
     */
    public ReliableLog(String dirPath, LogHandler handler) throws IOException {
	dir = new File(dirPath);
	if (!(dir.exists() ? dir.isDirectory() : dir.mkdir())) {
	    throw new LogException("could not create directory for log: " +
				   dirPath);
	}
	this.handler = handler;
	try {
	    DataInputStream in =
		new DataInputStream(new FileInputStream(fName(versionFile)));
	    try {
		version = in.readInt();
	    } finally {
		in.close();
	    }
	} catch (IOException ex) {
	    writeVersionFile();
	}
	if (version < 0) {
	    throw new LogException("corrupted version file");
	}
    }

    /**
     * Retrieves the contents of the snapshot file by calling the client
     * supplied recover callback and then applies the incremental updates
     * by calling the readUpdate callback for each logged updated.
     *
     * @throws LogException if recovery fails due to serious log corruption,
     * or if an exception is thrown by the recover or readUpdate callbacks
     * @throws IOException if an other I/O error occurs
     */
    public void recover() throws IOException {
	if (version == 0) 
	    return;
	
	String fname = versionName(snapshotPrefix);
	File file = new File(fname);
	InputStream in = new BufferedInputStream(new FileInputStream(file));
	try {
	    handler.recover(in);
	} catch (Exception e) {
	    throw new LogException("recovery failed", e);
	} finally {
	    in.close();
	}
	snapshotBytes = file.length();
	
	fname = versionName(logfilePrefix);
	file = new File(fname);
	DataInputStream din =
	    new DataInputStream(new BufferedInputStream(
						 new FileInputStream(file)));
	long length = file.length();
	try {
	    int updateLen = din.readInt();
	    /* have to worry about no MAGIC in original format */
	    if (updateLen == MAGIC) {
		format = din.readInt();
		if (format != FORMAT_PADDED) {
		    throw new LogException("corrupted log: bad log format");
		}
		logBytes += (intBytes + intBytes);
		updateLen = din.readInt();
	    }
	    while (true) {
		if (updateLen == 0) { /* expected termination case */
		    break;
		}
		if (updateLen < 0) { /* serious corruption */
		    throw new LogException("corrupted log: bad update length");
		}
		if (length - logBytes - intBytes < updateLen) {
		    /* partial record at end of log; this should not happen
		     * if forceToDisk is always true, but might happen if
		     * buffered updates are used.
		     */
		    break;
		}
		try {
		    handler.readUpdate(new LogInputStream(din, updateLen));
		} catch (Exception e) {
		    throw new LogException("read update failed", e);
		}
		logBytes += (intBytes + updateLen);
		if (format == FORMAT_PADDED) {
		    int offset = (int)logBytes & 3;
		    if (offset > 0) {
			offset = 4 - offset;
			logBytes += offset;
			din.skipBytes(offset);
		    }
		}
		updateLen = din.readInt();
	    } /* while */
	} catch (EOFException e) {
	} finally {
	    din.close();
	}
	/* reopen log file at end */
	openLogFile();
    }
    
    /**
     * Records this update in the log file and forces the update to disk.
     * The update is recorded by calling the client's writeUpdate callback.
     * This method must not be called until this log's recover method has
     * been invoked (and completed).
     *
     * @param value the object representing the update
     *
     * @throws LogException if an exception is thrown by the writeUpdate
     * callback, or forcing the update to disk fails
     * @throws IOException if any other I/O error occurs
     */
    public void update(Object value) throws IOException {
	update(value, true);
    }
    
    /**
     * Records this update in the log file and optionally forces the update
     * to disk.  The update is recorded by calling the client's writeUpdate
     * callback.  This method must not be called until this log's recover
     * method has been invoked (and completed).
     *
     * @param value the object representing the update
     * @param forceToDisk true if the update should be forced to disk, false
     * if the updates should be buffered
     *
     * @throws LogException if an exception is thrown by the writeUpdate
     * callback, or forcing the update to disk fails
     * @throws IOException if any other I/O error occurs
     */
    public void update(Object value, boolean forceToDisk) throws IOException {
        /* avoid accessing a null log field */
        if (log == null) {
            throw new LogException("log file for persistent state is "
                                   +"inaccessible, it may have been "
                                   +"corrupted or closed");
        }
	/* note: zero length header for this update was written as part
	 * of the previous update, or at initial opening of the log file
	 */
	try {
	    handler.writeUpdate(new LogOutputStream(log), value);
	} catch (Exception e) {
	    throw new LogException("write update failed", e);
	}
	if (forceToDisk) {
	    /* must force contents to disk before writing real length header */
	    try {
		logFD.sync();
	    } catch (SyncFailedException sfe) {
		throw new LogException("sync log failed", sfe);
	    }
	}
	long entryEnd = log.getFilePointer();
	long updateLen = entryEnd - logBytes - intBytes;
	if (updateLen > Integer.MAX_VALUE) {
	    throw new LogException("maximum record length exceeded");
	}
	/* write real length header */
	log.seek(logBytes);
	writeInt(log, (int) updateLen);
	/* pad out update record so length header does not span disk blocks */
	if (format == FORMAT_PADDED) {
	    entryEnd = (entryEnd + 3) & ~3L;
	}
	/* write zero length header for next update */
	log.seek(entryEnd);
	log.write(zeroBuf);
	logBytes = entryEnd;
	/* force both length headers to disk */
	if (forceToDisk) {
	    try {
		logFD.sync();
	    } catch (SyncFailedException sfe) {
		throw new LogException("sync log failed", sfe);
	    }
	}
    }
    
    /**
     * Write an int value in single write operation.
     *
     * @param out output stream
     * @param val int value
     * @throws IOException if any other I/O error occurs
     */
    private void writeInt(DataOutput out, int val) throws IOException {
	intBuf[0] = (byte) (val >> 24);
	intBuf[1] = (byte) (val >> 16);
	intBuf[2] = (byte) (val >> 8);
	intBuf[3] = (byte) val;
	out.write(intBuf);
    }

    /**
     * Records the client-defined current snapshot by invoking the client
     * supplied snapshot callback, and then empties the log of incremental
     * updates.
     *
     * @throws LogException if the snapshot callback throws an exception
     * @throws IOException if any other I/O error occurs
     */
    public void snapshot() throws IOException {
	int oldVersion = version;
	version++;

	String fname = versionName(snapshotPrefix);
	File snapshotFile = new File(fname);
	FileOutputStream out = new FileOutputStream(snapshotFile);
	try {
	    try {
		handler.snapshot(out);
		/* force contents to disk */
		out.getFD().sync();
	    } catch (Exception e) {
		throw new LogException("snapshot failed", e);
	    }
	    snapshotBytes = snapshotFile.length();
	} finally {
	    out.close();
	}

	logBytes = 0;
	openLogFile();
	writeVersionFile();
	deleteSnapshot(oldVersion);
	deleteLogFile(oldVersion);
    }
    
    /**
     * Closes the stable storage directory in an orderly manner.
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException {
	if (log == null) return;
	try {
	    log.close();
	} finally {
	    log = null;
	}
    }
    
    /**
     * Closes the incremental update log file, removes all ReliableLog-related
     * files from the stable storage directory, and deletes the directory.
     */
    public void deletePersistentStore() {
        try {
	    close();
	} catch (IOException e) {
        }
	try {
            deleteLogFile(version);
	} catch (LogException e) {
	}
	try {
            deleteSnapshot(version);
	} catch (LogException e) {
	}
	try {
            deleteFile(fName(versionFile));
	} catch (LogException e) {
	}
	try {
	    /* Delete the directory. The following call to the delete method
             * will fail only if the directory is not empty or if the Security
             * Manager's checkDelete() method throws a SecurityException. 
             * (The Security Manager will throw such an exception if it
             * determines that the current application is not allowed to
             * delete the directory.) For either case, upon un-successful
             * deletion of the directory, take no further action.
             */
	    dir.delete();
	} catch (SecurityException e) {
	}
    }

    /**
     * Returns the size of the current snapshot file in bytes;
     */
    public long snapshotSize() { return snapshotBytes; }
    
    /**
     * Returns the current size of the incremental update log file in bytes;
     */
    public long logSize() { return logBytes; }

    /**
     * Generates a filename prepended with the stable storage directory path.
     *
     * @param name the name of the file (sans directory path)
     */
    private String fName(String name) {
	return dir.getPath() + File.separator + name;
    }

    /**
     * Generates a version filename prepended with the stable storage
     * directory path with the current version number as a suffix.
     *
     * @param name version filename prefix
     */
    private String versionName(String name) {
	return versionName(name, version);
    }
    
    /**
     * Generates a version filename prepended with the stable storage
     * directory path with the given version number as a suffix.
     *
     * @param prefix filename prefix
     * @param ver version number
     */
    private String versionName(String prefix, int ver) {
	return fName(prefix) + String.valueOf(ver);
    }

    /**
     * Deletes a file.
     *
     * @param name the name of the file (complete path)
     * @throws LogException if file cannot be deleted
     */
    private void deleteFile(String name) throws LogException {
	if (!new File(name).delete()) {
	    throw new LogException("couldn't delete file: " + name); 
	}
    }
    
    /**
     * Removes the snapshot file.
     *
     * @param ver the version to remove
     *
     * @throws LogException if file cannot be deleted
     */
    private void deleteSnapshot(int ver) throws LogException {
	if (ver != 0) {
	    deleteFile(versionName(snapshotPrefix, ver));
	}
    }

    /**
     * Removes the incremental update log file.
     *
     * @param ver the version to remove
     *
     * @throws LogException if file cannot be deleted
     */
    private void deleteLogFile(int ver) throws LogException {
	if (ver != 0) {
	    deleteFile(versionName(logfilePrefix, ver));
	}
    }

    /**
     * Opens the incremental update log file in read/write mode.  If the
     * file does not exist, it is created.
     *
     * @throws IOException if an I/O error occurs
     */
    private void openLogFile() throws IOException {
	try {
	    close();
	} catch (IOException e) { /* assume this is okay */
	} 
	
	logName = versionName(logfilePrefix);
	log = new RandomAccessFile(logName, "rw");
	logFD = log.getFD();

	if (logBytes == 0) {
	    format = FORMAT_PADDED;
	    writeInt(log, MAGIC);
	    writeInt(log, format);
	    logBytes = (intBytes + intBytes);
	} else {
	    log.seek(logBytes);
	}
	log.setLength(logBytes);
	/* always start out with zero length header for the next update */
	log.write(zeroBuf);
	/* force length header to disk */
	logFD.sync();
    }
    
    /**
     * Writes the current version number to the version file.
     *
     * @throws IOException if an I/O error occurs
     */
    private void writeVersionFile() throws IOException {
	RandomAccessFile out = new RandomAccessFile(fName(versionFile), "rw");
	try {
	    /* write should be atomic (four bytes on one disk block) */
	    writeInt(out, version);
	    /* force version to disk */
	    out.getFD().sync();
	} finally {
	    out.close();
	}
    }
}
