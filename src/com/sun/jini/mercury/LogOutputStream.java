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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.SyncFailedException;

/**
 * This class implements the interface for interacting with output log
 * streams. It extends <tt>java.util.OutputStream</tt> and overrides its
 * methods in order to provide buffered output as well as byte offset 
 * tracking, which is useful during recovery situations.
 * The additional methods <tt>drain</tt> and <tt>sync</tt> allow for the
 * flushing of any buffered data and synchronizing data buffers with the
 * underlying device, respectively.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */

class LogOutputStream extends OutputStream implements LogStream {
    
    /** Holds value of the internal buffer size to allocate */
    private static final int BUFSIZE = 1024;

    /** byte array buffer for written data */
    private byte[] buf = new byte[BUFSIZE];

    /** index/offset into the internal write buffer */
    private int pos = 0;

    /** cumulative index/offset into this stream */
    private long offset = 0;

    /** Underlying output stream from which bytes are written */
    private FileOutputStream out;

    /** Associated key for this stream object */
    private /*final*/ StreamKey key;

    /**
     * Simple constructor that accepts a <tt>File</tt>, <tt>StreamKey</tt>
     * and <tt>boolean</tt> arguments. The <tt>File</tt> and <tt>StreamKey</tt>
     * arguments are assigned to their respective internal fields.  
     * The <tt>boolean</tt> argument is used to determine whether or not to 
     * append bytes to the underlying (file) output stream.
     *
     * @param     file   the <tt>File</tt> to used
     * @param     key    the associated key for this object
     * @param     append determines whether or not to append to the 
     *                   underlying stream
     *
     * @exception  IllegalArgumentException  if either the <tt>file</tt> or 
     *                   <tt>key</tt> argument is null
     *
     * @exception  FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason
     */
    public LogOutputStream(File file, StreamKey key, boolean append) 
	throws FileNotFoundException
    {
        if (file == null || key == null)
            throw new IllegalArgumentException("Arguments cannot be null");

        // Throws an exception if !file.isFile()
	out = new FileOutputStream(file.getPath(), append);

	// If appending, then point to the end of the file.
	if (append) 
	    offset = file.length();
	this.key = key;
    }

    // javadoc inherited from supertype 
    public void write(int b) throws IOException {
	if (pos >= BUFSIZE)
	    drain();
	buf[pos++] = (byte) b;
	offset++;
    }

    // javadoc inherited from supertype 
    public void write(byte[] b) throws IOException {
	write(b, 0, b.length);
    }

    // javadoc inherited from supertype 
    public void write(byte[] b, int off, int len) throws IOException {
	if (pos + len >= BUFSIZE)
	    drain();
	if (len >= BUFSIZE) {
	    out.write(b, off, len);
	} else {
	    System.arraycopy(b, off, buf, pos, len);
	    pos += len;
	}
	offset += len;
    }

    // javadoc inherited from supertype 
    public void flush() throws IOException {
	drain();
	out.flush();
    }

    // javadoc inherited from supertype 
    public void close() throws IOException {
	flush();
	out.close();
    }
    
    /**
     * Returns the current "write" offset into this stream object. 
     */
    long getOffset() {
	return offset;
    }

    /**
     * Synchronizes system buffers with underlying device. 
     *
     * @exception IOException if an I/O error occurs
     *
     * @exception SyncFailedException if the buffers cannot be guaranteed to
     *                                have synchronized with physical media
     */
    void sync() throws IOException, SyncFailedException {
        out.getFD().sync();
    }
    
    /**
     * Writes any unwritten bytes to the underlying output stream.
     *
     * @exception IOException if an I/O error occurs
     *
     */
    void drain() throws IOException {
	if (pos > 0)
	    out.write(buf, 0, pos);
	pos = 0;
    }

    // javadoc inherited from supertype 
    public Object getKey() {
        return key;
    }
}
