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

import com.sun.jini.logging.Levels;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.SyncFailedException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 
 * This class provides a pool of <tt>LogStream</tt> objects.  Each
 * <tt>LogStream</tt> has an associated <tt>FileDescriptor</tt>, which
 * is the system resource we are trying to manage. This pool limits the
 * (user configurable) number of concurrent, open <tt>FileDescriptor</tt>s.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
class StreamPool {
    // Class fields
    /** Logger for lease related messages */
    private static final Logger persistenceLogger = 
        MailboxImpl.persistenceLogger;
    
    /** 
     * Maximum limit for the number of concurrent <tt>LogStream</tt>s
     * in the stream pool.
     */
    private final int maxPoolSize;

    /** Holds stream references by associated key */
    private final HashMap pool;

    /**
     * Holds stream references in least recently used (released) order.
     * It's used in order determine which stream to discard upon
     * reaching the maximum pool limit.
     */
    private final LinkedList freeList;

    /**
     * Simple constructor that creates a pool of given <code>size</code>.
     *
     * @exception IllegalArgumentException Thrown if the value of 
     *                <tt>maxPoolSize</tt> is less than 1.
     */
    StreamPool(int size) {
        if (size < 1) 
            throw new IllegalArgumentException(
		"Pool size must be greater than 0."); 
        maxPoolSize = size;
        pool = new HashMap(maxPoolSize);
        freeList = new LinkedList();

        if (persistenceLogger.isLoggable(Level.FINEST)) {
            persistenceLogger.log(Level.FINEST,
	        "Created StreamPool of size {0}", 
		new Integer(maxPoolSize));
	}
    }

    /**
     * Returns a <tt>ControlLog</tt> object for the specified <tt>file</tt>
     * from the pool if it already exists.
     * Otherwise, it creates a new instance and adds it to the pool.
     *
     * @exception IOException if an I/O error occurs
     */
    synchronized ControlLog getControlLog(File file) 
	throws IOException 
    {
	StreamKey key = new StreamKey(file, StreamType.CONTROL);
	ControlLog log = (ControlLog)pool.get(key);
	if (log != null) { // found it!
	    if (freeList.remove(key) == false)
	        throw new InternalMailboxException("Did not find re-used control log "
	            + "stream in freeList.");
	} else { // Log was not found, so attempt to add it

	    ensurePoolSpace();

            //
	    // Create new ControlLog and add it the pool.
            //
            log = new ControlLog(file, key);
            pool.put(key, log);

            if(freeList.remove(key))
                throw new InternalMailboxException("Found newly created ControlLog "
                    + "in freeList");
	}

        return log;
    }

    /**
     * Returns a <tt>LogInputStream</tt> object from the pool if it 
     * already exists. Otherwise, it creates a new instance and adds 
     * it to the pool.
     *
     * @exception IOException if an I/O error occurs
     */
    synchronized LogInputStream getLogInputStream(File file, long offset) 
	throws IOException
    {
	StreamKey key = new StreamKey(file, StreamType.INPUT);
	LogInputStream in = (LogInputStream)pool.get(key);
	if (in != null) { //found it!
	    if (freeList.remove(key) == false)
	        throw new InternalMailboxException("Did not find re-used input log "
	            + "stream in freelist.");
	} 

	if (in == null ||            // if log not found OR 
	    in.getOffset() > offset) // current read offset is past desired 
	{                            // then create a new log and add it
	    ensurePoolSpace();

	    in = new LogInputStream(file, key);
	    pool.put(key, in);

            if(freeList.remove(key))
                throw new InternalMailboxException("Found newly created ControlLog "
                    + "on freeList");
	}

	// Sanity check for offset value
	if (offset > file.length()) 
	    throw new EOFException("Attempting to read past end of file.");

	// Check if log offset needs adjusting. 
	// By this point in.offset <= offset.
	while (in.getOffset() < offset) {
	    in.skip(offset - in.getOffset());
	}

	return in;
    }

    /**
     * Returns a <tt>LogOutputStream</tt> object for the specified <tt>file</tt>
     * from the pool if it already exists. 
     * Otherwise, it creates a new instance and adds it to the pool.
     *
     * @exception IOException if an I/O error occurs
     */
    synchronized LogOutputStream getLogOutputStream(File file, long offset) 
	throws IOException 
    {
	StreamKey key = new StreamKey(file, StreamType.OUTPUT);
	LogOutputStream out = (LogOutputStream)pool.get(key);

	if (out != null) { // found it!
	    if (freeList.remove(key) == false)
	        throw new InternalMailboxException("Did not find re-used output log "
	            + "stream in freelist");

	     // Sanity check to see if we are still in sync. If not,
	     // then we need to close this stream and create another
	     // one (done in the next code block).
	    if (out.getOffset() != offset) {
	        removeLogStream(out);
	        out = null;
	    }
	}

        // Check to see if we need to create another log.
	if (out == null) { 

	    ensurePoolSpace();

	    if (offset == 0L) { // Create new log, without appending
	        out = new LogOutputStream(file, key, false);
	    } else { // Create new log, appending to existing file
	        long len = file.length();

	        if (offset > len) 
	            throw new EOFException("Attempting to write past end "
	                + "of file");

		if (offset < len) {
		    RandomAccessFile raf = new RandomAccessFile(file, "rw");
		    raf.setLength(offset);
		    raf.close();
		}

		out = new LogOutputStream(file, key, true);
	    }

            pool.put(key, out);

            if (freeList.remove(key))
                throw new InternalMailboxException("Found newly created output log "
                    + "in freeList");

	}

        return out;
    }

    /**
     * Ensures that room is available in the pool. If the pool is currently
     * full, then the least recently used <tt>LogStream</tt> will be removed 
     * and closed to make room. This method will block if the pool is full and
     * no <tt>LogStream</tt> objects can be closed. 
     *
     * @exception IOException if an I/O error occurs
     */
    private synchronized void ensurePoolSpace() throws IOException {
        if (pool.size() >= maxPoolSize) {
            while(freeList.size() < 1) {
                try {
		    wait();
		} catch (InterruptedException ie) { ; }
	    }
	    StreamKey key = (StreamKey)freeList.removeFirst();
	    LogStream els = (LogStream)pool.remove(key);
	    els.close();
	}
    }

    /**
     * Marks a stream as available for closing.
     * A log will only be closed if a new log is requested and the
     * pool has reached its maximum size.
     */
    synchronized void releaseLogStream(LogStream stream) {
        StreamKey key = (StreamKey)stream.getKey();
        if (pool.get(key) == null)
            throw new InternalMailboxException("Not managing stream: " 
                + stream + ":" + key + " -- release failed");
	freeList.add(key);
	notifyAll();
    }

    /**
     * Removes the given <tt>LogStream</tt> from the pool and closes it,
     * if possible. The intent is for this method to be called for 
     * unusable logs so that they will no longer be returned by a 
     * subsequent call to one of the "get" methods.
     */
    synchronized void removeLogStream(LogStream stream) {
        StreamKey key = (StreamKey)stream.getKey();
        if (pool.remove(key) == null)
            throw new InternalMailboxException("Not managing stream: " 
                + stream + ":" + key + " -- remove failed");

        // Remove it from freeList, if present
        freeList.remove(key);
        try {
            stream.close();
	} catch (IOException ioe) {
	    // Note the exception, but otherwise ignore
	    if (persistenceLogger.isLoggable(Levels.HANDLED)) {
                persistenceLogger.log(Levels.HANDLED,
		    "Exception closing Log", ioe);
	    }
	}
    }

    //
    // Debug use only!
    //
    synchronized int getPoolSize() { return pool.size(); }
    synchronized int getFreeSize() { return freeList.size(); }
    synchronized void dump() { 
	System.out.println("Pool:\n" + pool); 
	System.out.println("Free:\n" + freeList); 
    }
}
