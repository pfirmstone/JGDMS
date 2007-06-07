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
package com.sun.jini.mercury;

import net.jini.id.Uuid;

import com.sun.jini.logging.Levels;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.NoSuchElementException;

import net.jini.core.event.RemoteEvent;

/**
 * Class that implements the interface for an <tt>EventLog</tt>. 
 * This class encapsulates the details of reading/writing events from/to
 * some underlying persistence mechanism.
 *
 * This class makes certain assumptions. First, the <tt>next</tt> and
 * <tt>remove</tt> methods are intended to be called in pairs. If 
 * <tt>remove</tt> is not called, then subsequent calls to <tt>next</tt> 
 * will attempt to return the same object. Calling <tt>remove</tt> 
 * essentially advances the read pointer to the next object, if any. 
 * Second, if any <tt>IOExceptions</tt> are encountered during the reading
 * or writing of an event the associated read/write pointer is advanced
 * past the offending event. This means that events can be lost if I/O 
 * errors are encountered.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 */

/*
Implementation details:

Event state information is maintained by a control log file 
which maintains the current read and write counts/offsets.  This file
is updated whenever next(), add() or remove() is called.  This file used to 
initialize the state of the EventLog, if it exists, during the 
initialization process.

Events are logged into log files that maintain a fixed # of objects
per file.  The writing process adds events to a log file until this limit
is reached. A new log file is then created to handle the next set of events. 
The reading process will delete a log file once all the contained events 
have been successfully processed.  This serves as the garbage 
collection mechanism. Note that the actual number of events per log is a 
user-configurable environmental parameter via the 
com.sun.jini.mercury.eventsPerLog property (default = 10).

This class also employs the services of a StreamPool, which limits the
number of concurrently open file descriptors. This limit is also a user
configurable value via the com.sun.jini.mercury.streamPoolSize property 
(default = 10).

Event state is kept separate from the service's registration state in 
order to keep the event logging implementation as flexible as possible.
*/

class PersistentEventLog implements EventLog {

    //
    // Class fields
    //

    /** <tt>Logger</tt> used for persistence-related debugging messages */
    private static final Logger persistenceLogger = 
	MailboxImpl.persistenceLogger;

    /** Size of control data file: 4 longs * 8 bytes per long */
    private static final int CTLBLOCK_LEN = 8 * 4;

    /** File suffix for the control file */
    private static final String CTLFILE_SUFFIX = "ctl";

    /** File suffix for the log files */
    private static final String LOGFILE_SUFFIX = "log";

    /** Default number of events per log file, if not overriden by the user */
    private static final long DEFAULT_EVENTS_PER_LOGFILE = 10L;

    /** 
     * Maximum number of event objects per log file. The
     * default is used unless overridden by <tt>eventsPerLogProperty</tt>.
     */
    // Should be final but the compiler complains.
//TODO - make eventsPerLogFile & maxPoolSize configurable
/*
 * Notes: this probably needs to remain constant over service lifetime. Therefore,
 * this really needs to be treated as an "initial value that persisted and recovered.
 * Would like to piggy back off MailboxImpl recovery logic, but 1) it needs to recover
 * first in order to determine "first time up" and 2) seems logically better to keep
 * these parameters local to EventLog. Will probably need to develop EventLog recovery
 * logic for this purpose.
 */
    private static final long eventsPerLogFile = DEFAULT_EVENTS_PER_LOGFILE;

    /** Default size for the stream pool, if not overriden by the user */
    static final int DEFAULT_STREAM_POOL_SIZE = 10;

    /** 
     * Maximum limit for the number of concurrent <tt>LogStream</tt>s
     * that this pool will concurrently manage. The
     * default is used unless overridden by <tt>streamPoolSizeProperty</tt>.
     */
    private static final int maxPoolSize = DEFAULT_STREAM_POOL_SIZE; 

    /** 
     * The <tt>StreamPool</tt> reference to be used for all 
     * instances of <tt>EventLog</tt>.
     */
    // Should be final but the compiler complains.
    private static final StreamPool streamPool = 
        new StreamPool(maxPoolSize);

    //
    // Object fields
    //

    /** The associated <tt>Uuid</tt> for this <tt>EventLog</tt>. */
    private Uuid uuid = null;

    /** The current number of written events. */
    private long wcount = 0;

    /** The current number of read events. */
    private long rcount = 0;

    /** The current write offset into the current "write" log. */
    private long wpos = 0;

    /** 
     * The read offset into the current "read" log of the 
     * "last read" object.
     */
    private long rpos = 0;

    /** 
     * The read offset of the next event. This gets updated to
     * <tt>rpos</tt> once <tt>remove</tt> is called (indicating
     * that the last event read was successful).
     */
    private long nextReadPos = 0;

    /** The <tt>EventReader</tt> used to retrieve events. */
    private EventReader eventReader;

    /** The <tt>EventWriter</tt> used to store events */
    private EventWriter eventWriter;

    /** The <tt>File</tt> object of the event persistence directory */
    private File logDir;

    /** 
     * The <tt>File</tt> object that will maintain the control data for
     * for this <tt>EventLog</tt>.
     */
    private File controlFile;

    /** The in memory buffer that holds the control data */
    private byte[] ctlbuf = new byte[CTLBLOCK_LEN];

    /** 
     * Flag that is used to determine whether or not this object 
     * has been initialized. 
     */
    private boolean initialized = false;

    /** 
     * Flag that is used to determine whether or not this object 
     * has been closed. 
     */
    private boolean closed = false;

    private static final boolean debugState = false;
    
    /**
     * Simple constructor that takes a <tt>Uuid</tt> argument
     * and a <tt>File</tt> argument.  These arguments are simply
     * assigned to the appropriate internal fields. 
     *
     * @exception IllegalArgumentException if any of the arguments are null
     */
    PersistentEventLog(Uuid uuid, File logDir) {
        if (logDir == null || uuid == null) 
            throw new IllegalArgumentException("Arguments cannot be null");
        this.uuid = uuid;
        this.logDir = logDir;

        if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST, 
	        "PersistentEventLog for: {0}", uuid);
        }
    }

    // Inherit documentation from supertype
    public void init() throws IOException {

        if (initialized)
            throw new InternalMailboxException(
		"Trying to re-initialize control data "
                + "for: " + uuid);

        try {
            if (!logDir.exists()) // Create log directory if it doesn't exist
                logDir.mkdirs();

            if (!logDir.isDirectory()) // Verify that logDir is a directory
                throw new FileNotFoundException(logDir.toString()
                                  + " is not a directory");

            controlFile = getControlFile();
            if (controlFile.isFile()) { // Recover state from existing file
                if (persistenceLogger.isLoggable(Level.FINEST)) {
                    persistenceLogger.log(Level.FINEST, 
	                "EventLog::init() recovering data for ", 
                        uuid);
		}
                readControlFile();
	    } else { 
                if (persistenceLogger.isLoggable(Level.FINEST)) {
                    persistenceLogger.log(Level.FINEST, 
	                "default initialization for ", uuid);
		}
	    }

	    eventReader = new EventReader();
	    eventWriter = new EventWriter();

	} finally {
	}

        printControlData(persistenceLogger, "After EventLog::init");
	
	initialized = true;

	if (debugState)
	    assertInvariants();
    }

    // Inherit documentation from supertype
    public void add(RemoteEvent event) throws IOException {

        stateCheck();

	if (debugState)
	    assertInvariants();

	long logNum = getLogNum(wcount);
	File log = getLogFile(logNum);
	LogOutputStream out = null;
        try {
            // Get output stream
	    out = streamPool.getLogOutputStream(log, wpos);

            // Write the event
	    eventWriter.write(event, out);

	    // Update the control data
	    wpos = out.getOffset();
	    ++wcount;
	} catch (IOException ioe) {
	    // We'll get interrupted when asked to shutdown.
	    // In this case, we can skip the call to nextWriteLog.
	    if (ioe instanceof InterruptedIOException) {
                if (persistenceLogger.isLoggable(Level.FINEST)) {
                    persistenceLogger.log(Level.FINEST, 
	                "EventLog::add() interrupted "); 
		}
	    } else {
                if (persistenceLogger.isLoggable(Level.FINEST)) {
                    persistenceLogger.log(Level.FINEST, 
	                "EventLog::add() received IOException " 
			+ "... skipping to next write log");
		} 
	        nextWriteLog(); 
	    }
            if (persistenceLogger.isLoggable(Level.FINEST)) {
                persistenceLogger.log(Level.FINEST, 
	            "Exception: ", ioe);
	    }
	    throw (IOException)ioe.fillInStackTrace();
	} finally {
	    // Always make sure to release any obtained resources
	    if (out != null) {
	        if (getLogNum(wcount) == logNum) {
	            streamPool.releaseLogStream(out);
		} else { 
		    // Done writing to this particular log file, so
		    // remove it.
	            streamPool.removeLogStream(out);
	            wpos = 0;
		}
	    }
	    // Write control data before returning
            // TODO (FCS) - handle potential IOException
            writeControlFile();
	}

        printControlData(persistenceLogger, "EventLog::add");

	if (debugState) {
	    assertInvariants();
	}
    }

    // Inherit documentation from supertype
    public RemoteEvent next() throws IOException, ClassNotFoundException {
        boolean IOExceptionCaught = false;

        stateCheck();

	if (debugState) {
	    assertInvariants();
	}

        // Check if empty
	if (isEmpty()) 
	    throw new NoSuchElementException();

        long logNum = getLogNum(rcount);
        File log = getLogFile(logNum);
        LogInputStream in = null;
        RemoteEvent evt = null;
        try {
            // get the input stream
            in = streamPool.getLogInputStream(log, rpos);
            // read the event
            evt = eventReader.read(in); 
            // update the control data
            nextReadPos = in.getOffset();
            // Don't increment "real" read count until event is delivered
            // indicated by a call to remove().
        } catch (IOException ie) {
	    // We'll get interrupted when asked to shutdown.
	    // In this case, we can skip the call to nextReadLog.
	    if (ie instanceof InterruptedIOException) {
                if (persistenceLogger.isLoggable(Level.FINEST)) {
                    persistenceLogger.log(Level.FINEST, 
	                "EventLog::next() interrupted ");
		} 
	    } else {
                if (persistenceLogger.isLoggable(Level.FINEST)) {
                    persistenceLogger.log(Level.FINEST, 
	                "EventLog::next() received IOException " 
			+ "... skipping to next read log"); 
		}
	        nextReadLog();
                IOExceptionCaught = true; 
	    }
            if (persistenceLogger.isLoggable(Level.FINEST)) {
                    persistenceLogger.log(Level.FINEST, 
	                "Exception: ", ie);
	    }
	    throw (IOException)ie.fillInStackTrace();
        } catch (ClassNotFoundException cnfe) {
            // Note that the read offset is still valid since the
            // MarshalledObject extraction should always succeed.
            // It's just that the RemoteEvent within it could not
            // be reconstituted. Therefore, just skip to the next
            // MarshalledObject in the stream.
            nextReadPos = in.getOffset();
	    throw (ClassNotFoundException)cnfe.fillInStackTrace();
	} finally {
	    // If an IOException occurs then the eventReader is in 
	    // a bad state. Therefore, we get rid of our existing
	    // reference and create a fresh one. The underlying
	    // stream is also removed from from the pool, forcing
	    // a fresh copy to be returned on the next "get" request.
	    if (IOExceptionCaught) {
	        //TODO (FCS) add close to interface
	        //eventReader.close();
	        eventReader = new EventReader();
	        if (in != null)
	            streamPool.removeLogStream(in);
		// Remove associated log file since we are skipping over it
		if (!log.delete())
		    if (persistenceLogger.isLoggable(Levels.HANDLED)) {
                        persistenceLogger.log(Levels.HANDLED,
	                    "Had trouble deleting {0}", log);
		    }
	    } else {
	        if (in != null)
	            streamPool.releaseLogStream(in);
	    }

            // TODO (FCS) - handle potential IOException
            writeControlFile();

	}

        printControlData(persistenceLogger, "After Event::next");

	if (debugState) {
	    assertInvariants();
	}

        return evt;
    }
    
    // Inherit documentation from supertype
    public RemoteEventData[] readAhead(int maxEvents) 
        throws IOException, ClassNotFoundException 
    {
        boolean IOExceptionCaught = false;

        stateCheck();

	if (debugState) {
	    assertInvariants();
	}

        // Check if empty
	if (isEmpty()) 
	    throw new NoSuchElementException();    

        long readCount = rcount;
        long readPosition = rpos;
        long logNum = getLogNum(readCount); 
        File log = getLogFile(logNum);
        RemoteEvent evt = null;
        ArrayList rData = new ArrayList();
        int i = 0;
        LogInputStream in = null;
        boolean done = false;
        
        printControlData(persistenceLogger, "Before read::readAhead");
        if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST, 
                "EventLog::readAhead() maxEvents = {0}", 
                new Object[] {new Integer(maxEvents)});
        } 
        
        while ((readCount < wcount) && 
               (rData.size() < maxEvents) &&
               !done) 
        {
            if (logNum != getLogNum(readCount)) {
                logNum = getLogNum(readCount);
                log = getLogFile(logNum);
                readPosition = 0;
            }
            try {
                // get the input stream
                in = streamPool.getLogInputStream(log, readPosition);
                // read the event
                evt = eventReader.read(in); 
                // update readCount and readPosition
                readCount++;
                readPosition = in.getOffset(); // offset to next unread event
                //Generate new entry
                rData.add(new RemoteEventData(
                   evt, 
                   new RemoteEventDataCursor(readCount, readPosition)));
            } catch (IOException ie) {
                // We'll get interrupted when asked to shutdown.
                // In this case, we can skip the call to nextReadAheadLog.
                if (ie instanceof InterruptedIOException) {
                    if (persistenceLogger.isLoggable(Level.FINEST)) {
                        persistenceLogger.log(Level.FINEST, 
                            "EventLog::readAhead() interrupted ");
                    } 
                    // Stop processing events
                    done = true;
                } else {
                    if (persistenceLogger.isLoggable(Level.FINEST)) {
                        persistenceLogger.log(Level.FINEST, 
                            "EventLog::readAhead() received IOException " 
                            + "... skipping to next read log"); 
                    }
                    readCount = nextReadAheadLog(readCount);
                    // TODO - rcount = readCount;
                    // TODO - if bump rcount you need to bump wcount too
                    // TODO - if bump counts then you need to persist them
                    readPosition = 0;
                    IOExceptionCaught = true; 
                    if (persistenceLogger.isLoggable(Level.FINEST)) {
                        persistenceLogger.log(Level.FINEST, 
                            "EventLog::readAhead() new readCount is {0}", 
                            new Long(readCount)); 
                    }                    
                }
                if (persistenceLogger.isLoggable(Levels.HANDLED)) {
                        persistenceLogger.log(Levels.HANDLED, 
                            "Exception: ", ie);
                }
            } catch (ClassNotFoundException cnfe) {
                // Note that the read offset is still valid since the
                // MarshalledObject extraction should always succeed.
                // It's just that the RemoteEvent within it could not
                // be reconstituted. Therefore, just skip to the next
                // MarshalledObject in the stream.
                readCount++;
                readPosition = in.getOffset();
                //Generate new entry
                rData.add(new RemoteEventData(
                   null, 
                   new RemoteEventDataCursor(readCount, readPosition)));                
                if (persistenceLogger.isLoggable(Levels.HANDLED)) {
                        persistenceLogger.log(Levels.HANDLED, 
                            "Exception: ", cnfe);
                }            
            } finally {
                // If an IOException occurs then the eventReader is in 
                // a bad state. Therefore, we get rid of our existing
                // reference and create a fresh one. The underlying
                // stream is also removed from from the pool, forcing
                // a fresh copy to be returned on the next "get" request.
                if (IOExceptionCaught) {
                    //TODO (FCS) add close to interface
                    //eventReader.close();
                    eventReader = new EventReader();
                    if (in != null) {
                        streamPool.removeLogStream(in);
                        in = null;
                    }
                } else { 
                    if (in != null)
                        streamPool.releaseLogStream(in);
                    /* Shouldn't have to release since we have exclusive
                     * access, but the next getLogInputStream assumes things
                     * are on the freelist.
                     */
                }

            }
        }

        printControlData(persistenceLogger, "After Event::readAhead");

	if (debugState) {
	    assertInvariants();
	}
        
        return (RemoteEventData[]) rData.toArray(new RemoteEventData[0]);
    }
    
    // Inherit documentation from supertype
    public boolean isEmpty() throws IOException {
        stateCheck();
        return !(rcount < wcount);
    }

    // Inherit documentation from supertype
    public void remove() throws IOException {
        stateCheck();

	if (debugState) {
	    assertInvariants();
	}

	// Get current log number
        long logNum = getLogNum(rcount);

	// Update read counts
	if (rcount < wcount) {
	    ++rcount;
	    rpos = nextReadPos;
	} else {
	    throw new NoSuchElementException();
	}

	// Remove old log if we've advanced to a new
	// log file (ie done reading the old log file).
	if (getLogNum(rcount) != logNum) {
	    File log = getLogFile(logNum);
	    LogInputStream in = null;
	    try {
	        in = streamPool.getLogInputStream(log, rpos);
	        streamPool.removeLogStream(in);
	    } finally {
		if (!log.delete())
		    if (persistenceLogger.isLoggable(Levels.HANDLED)) {
                        persistenceLogger.log(Levels.HANDLED, 
	                    "Had trouble deleting {0}", log);
	            }
	        rpos = nextReadPos = 0;
	    }
	}

	// Store control data
        writeControlFile();

        // Verify that state remains intact
	assertInvariants();


        printControlData(persistenceLogger, "After Event::remove");

    }

        // Inherit documentation from supertype
    public void moveAhead(Object cookie) throws IOException {
        stateCheck();

	if (debugState) {
	    assertInvariants();
	}
        
        printControlData(persistenceLogger, "Before Event::moveAhead");
        
        long readCount = 0;
        long readPosition = 0;        
        RemoteEventDataCursor cursor = (RemoteEventDataCursor)cookie;
        if (cursor != null) {
            readCount = cursor.getReadCount();
            readPosition = cursor.getReadPosition();
        } else {
            /* TODO - should throw NullPointerException, but we do 
             * get called with null if client initially gets an empty set.
             * Need to change getNextBatchDo() to skip this call 
             * if cookie is null. Also, could just return at this point since
             * nothing will be advanced.
             */
	    readCount = rcount;
	    readPosition = rpos;
	}

        if (readCount > wcount) {
            throw new NoSuchElementException();
        }

        long currentLogNum = getLogNum(rcount);
        long nextLogNum = getLogNum(readCount);
        if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST, 
                "EventLog::moveAhead() readCount = {0}, readPosition = {1}, " 
                + "currentLogNum = {2}, nextLogNum = {3}",
                new Object[] {new Long(readCount), new Long(readPosition),
                              new Long(currentLogNum), new Long(nextLogNum)}); 
        }        
        File logFile = null;
        LogInputStream in = null;
        while (currentLogNum < nextLogNum) {
            try {
                logFile = getLogFile(currentLogNum);
                in = streamPool.getLogInputStream(logFile, rpos);
                streamPool.removeLogStream(in);
                /*
                 * NOTE - if file doesn't exist (corrupted) then
                 * getLogInputStream throws an IOException and you
                 * end up skipping the removeLogStream() call.
                 * This "log" should eventually get cleared if once
                 * the stream pool hits its limit.
                 */

            } catch (IOException ioe) {
                if (persistenceLogger.isLoggable(Levels.HANDLED)) {
                    persistenceLogger.log(Levels.HANDLED, 
                        "Had trouble accessing old log file", ioe);
                }
            } finally {
		if (logFile != null && !logFile.delete()) {
		    if (persistenceLogger.isLoggable(Levels.HANDLED)) {
                        persistenceLogger.log(Levels.HANDLED, 
	                    "Had trouble deleting {0}", logFile);
	            }
                }
                if (persistenceLogger.isLoggable(Level.FINEST)) {
                    persistenceLogger.log(Level.FINEST, 
                        "Deleted {0}", logFile);
                }
                currentLogNum++;
            }
        }
        rcount = readCount;
        // If we started a new log file, then reset the read pointer.
        if ((rcount % eventsPerLogFile) == 0) {
            rpos = 0;
        } else {
            rpos = readPosition;
        }
        
	// Store control data
        writeControlFile();
        
        // Verify that state remains intact
	assertInvariants();

        printControlData(persistenceLogger, "After Event::moveAhead");

    }        
    
    // Inherit documentation from supertype
    public void close() throws IOException {
        stateCheck();
	if (debugState) {
	    assertInvariants();
	}

	// Close read log
	long logNum = getLogNum(rcount);
	File log = getLogFile(logNum);
	LogStream strm = null;
	try {
	    strm = streamPool.getLogInputStream(log, rpos);
	    streamPool.removeLogStream(strm);
	} catch (FileNotFoundException fnfe) {
	    // Can happen if close is called just after you've finished
	    // reading a log. That is, the readCount is advanced to a new
	    // log which might not exist if a write hasn't taken place.
	    // TODO (FCS) - put a check to verify this condition and throw an
	    // exception otherwise?
	} catch (IOException ioe) {
	    // catch, but ignore so as not to skip the following
	    // code.
	}

	// Close write log
	logNum = getLogNum(wcount);
	log = getLogFile(logNum);
	try {
	    strm = streamPool.getLogOutputStream(log, wpos);
	    streamPool.removeLogStream(strm);
	} catch (IOException ioe) {
	    // catch, but ignore so as not to skip the following
	    // code.
	}
	
	// Close control log
	try {
	    strm = streamPool.getControlLog(controlFile);
	    streamPool.removeLogStream(strm);
	} catch (IOException ioe) {
	    // catch, but ignore so as not to skip the following
	    // code.
	}

        //TODO (FCS) - flag that an IOException was caught and rethrow to
        // indicate that there was a problem.

	closed = true;

	if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST, 
	        "EventLog::close for {0}", uuid);
	}
    }

    // Inherit documentation from supertype
    public void delete() throws IOException {
        if (!closed)
            throw new IOException("Cannot delete log until it is closed");
        removeDir(logDir);
    }

    /**
     * Attempt to delete the associated event log persistence directory.
     */
    private static void removeDir(File logDir) {
        if (logDir == null || !logDir.isDirectory())
            return;

        // Get the contents of this directory
        String[] contents = logDir.list();
	if (contents == null)  // Can happen if there was an IO error
	    return;

        File entry = null;
	if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST, 
	        "Deleting contents of: {0}", logDir);
	}
        for (int i=0; i < contents.length; i++) {
            entry = new File(logDir, contents[i]);
            if (entry.isDirectory()) {
                removeDir(entry);
	    } else {
                if(!entry.delete()) {
                    if (persistenceLogger.isLoggable(Levels.HANDLED)) {
                        persistenceLogger.log(Levels.HANDLED, 
	                    "Had trouble deleting file: {0}",
                            entry);
	            }
		} else {
                    if (persistenceLogger.isLoggable(Level.FINEST)) {
                        persistenceLogger.log(Level.FINEST, 
	                    "Deleted file: {0}", entry);
	            }
		}
	    }
	}

        if (!logDir.delete()) {
            if (persistenceLogger.isLoggable(Levels.HANDLED)) {
                persistenceLogger.log(Levels.HANDLED, 
	            "Had trouble deleting directory: {0}",
                    logDir);
	    }
	} else {
            if (persistenceLogger.isLoggable(Level.FINEST)) {
                persistenceLogger.log(Level.FINEST, 
                    "Deleted directory: {0}", logDir);
	    }
	}
    }

    /**
     * Advance the "read" state to the next available log.
     */
    private void nextReadLog() {
	if (debugState)
	    assertInvariants();

        // Normalize event count in case we were called due to a problem with
        // one of the log files
        long remainder = rcount % eventsPerLogFile; 
        rcount += (eventsPerLogFile - remainder);
	rpos = 0;

        // Ensure that the writeLog data doesn't get behind
	if (verifyInvariants() == false) {
	    nextWriteLog();
	}

        // Assert that state is still valid
	if (debugState)
	    assertInvariants();

        printControlData(persistenceLogger, "EventLog::nextReadLog");
    }

    /**
     * Advance the temporary "read" state to the next available log.
     */
    private long nextReadAheadLog(long readCount) {
        // Normalize event count in case we were called due to a problem with
        // one of the log files
        long remainder = readCount % eventsPerLogFile; 
        readCount += (eventsPerLogFile - remainder);

        printControlData(persistenceLogger, "EventLog::nextReadAheadLog");
        
        return readCount;
    }
    
    /**
     * Advance the "write" state to the next available log.
     */
    private void nextWriteLog() {
        // Don't check invariants at the top since we can get called
        // from readNextLog() with invalid state.

        // Normalize event count in case we were called due to a problem with
        // one of the log files
        long remainder = wcount % eventsPerLogFile; 
        wcount += (eventsPerLogFile - remainder);
	wpos = 0;

        printControlData(persistenceLogger, "EventLog::nextWriteLog");

	if (debugState)
	    assertInvariants();
    }

    /**
     * Output state information to the given <tt>Logger</tt>.
     * This is intended for debugging purposes only.
     */
    private void printControlData(Logger logger, String msg) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "{0}", msg);
            logger.log(Level.FINEST, "ID: {0}", uuid);
            logger.log(Level.FINEST, "ReadCount: {0}", 
	        new Long(rcount));
            logger.log(Level.FINEST, "ReadPos: {0}",  
	        new Long(rpos));
            logger.log(Level.FINEST, "NextReadPos: {0}",  
	        new Long(nextReadPos));
            logger.log(Level.FINEST, "WriteCount: {0}",  
	        new Long(wcount));
            logger.log(Level.FINEST, "WritePos: {0}",  
	        new Long(wpos));
	}
    }

    /**
     * Write state information to the underlying store.
     */
    private void writeControlFile() throws IOException {
	packLong(wcount, ctlbuf, 0);
	packLong(rcount, ctlbuf, 8);
	packLong(wpos, ctlbuf, 16);
	packLong(rpos, ctlbuf, 24);
	
	ControlLog ctl = streamPool.getControlLog(controlFile);
	ctl.seek(0L);
	ctl.write(ctlbuf);
	ctl.getFD().sync();
	streamPool.releaseLogStream(ctl);
    }
    
    /**
     * Read state information from the underlying store.
     */
    private void readControlFile() throws IOException {
	ControlLog ctl = streamPool.getControlLog(controlFile);
	ctl.seek(0L);
	ctl.readFully(ctlbuf);
	streamPool.releaseLogStream(ctl);

	wcount = unpackLong(ctlbuf, 0);
	rcount = unpackLong(ctlbuf, 8);
	wpos = unpackLong(ctlbuf, 16);
	rpos = unpackLong(ctlbuf, 24);
    }
    
    /**
     * Utility method for packing a <tt>long</tt> into a <tt>byte</tt> array.
     */
    private static void packLong(long val, byte[] b, int off) {
	b[off++] = (byte) (val >>> 56);
	b[off++] = (byte) (val >>> 48);
	b[off++] = (byte) (val >>> 40);
	b[off++] = (byte) (val >>> 32);
	b[off++] = (byte) (val >>> 24);
	b[off++] = (byte) (val >>> 16);
	b[off++] = (byte) (val >>> 8);
	b[off++] = (byte) (val >>> 0);
    }
    
    /**
     * Utility method for unpacking a <tt>long</tt> from a <tt>byte</tt> array.
     */
    private static long unpackLong(byte[] b, int off) {
	return ((b[off + 0] & 0xFFL) << 56) +
	    ((b[off + 1] & 0xFFL) << 48) +
	    ((b[off + 2] & 0xFFL) << 40) +
	    ((b[off + 3] & 0xFFL) << 32) +
	    ((b[off + 4] & 0xFFL) << 24) +
	    ((b[off + 5] & 0xFFL) << 16) +
	    ((b[off + 6] & 0xFFL) << 8) +
	    ((b[off + 7] & 0xFFL) << 0);
    }
    
    /**
     * Utility method for returning the <tt>File</tt> associated with the
     * given <tt>lognum</tt>.
     */
    private File getLogFile(long lognum) {
	return new File(logDir, lognum + "." + LOGFILE_SUFFIX).getAbsoluteFile();
    }
    
    /**
     * Utility method for returning the <tt>File</tt> that contains the
     * state information for this log.
     */
    private File getControlFile() {
	return new File(logDir, "log." + CTLFILE_SUFFIX).getAbsoluteFile();
    }

    /**
     * Utility method for returning the log file number for the given
     * (event) <tt>count</tt>.
     */
    private static long getLogNum(long count) {
	return (count / eventsPerLogFile); 
    }


    /**
     * Asserts that the log is in a valid state.
     * 
     * @exception IOException if the log is in an invalid state
     */
    private void stateCheck() throws IOException {
        if (!initialized)
            throw new IOException("Trying to use an uninitialized "
                + "control data object for: " + uuid);
	if (closed)
	    throw new IOException("Attempt to access closed log file for : "
	        + uuid);
    }

    /**
     * Utility method for checking if the object invariants are valid.
     */
    private boolean verifyInvariants() {
        if ((wcount < rcount) ||
            ((getLogNum(wcount) == getLogNum(rcount)) && (wpos < rpos))) {
            return false;
	}
	return true;
    }

    /**
     * Utility method for asserting that the object invariants are valid.
     *
     * @exception InternalMailboxException if the invariants aren't valid
     */
    private void assertInvariants() {
        if (verifyInvariants() == false) {
            printControlData(persistenceLogger, "Dumping invalid state for " + uuid);
            throw new InternalMailboxException("Invalid state for " + uuid);
	}
    }
}
