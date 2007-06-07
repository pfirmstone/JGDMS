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

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * This class implements the interface for interacting with input log
 * streams. It extends <tt>java.util.InputStream</tt> and overrides its
 * methods in order to provide buffered input as well as byte 
 * offset tracking, which is useful during recovery situations.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
class LogInputStream extends InputStream implements LogStream {

    /** Holds value of the internal buffer size to allocate */
    private static final int BUFSIZE = 1024;

    /** byte array buffer to hold read data */
    private byte[] buf = new byte[BUFSIZE];

    /** count of available bytes in the read buffer */
    private int count = 0;

    /** index/offset into the read buffer for the next set of bytes to read */
    private int pos = 0;

    /** cumulative index/offset into this stream */
    private long offset = 0;

    /** Underlying input stream from which bytes are read */
    private FileInputStream in;

    /** Associated key for this stream object */
    private /*final*/ StreamKey key;
    
    /**
     * Simple constructor that accepts a <tt>File</tt> and <tt>StreamKey</tt>
     * arguments. The <tt>File</tt> and <tt>StreamKey</tt>
     * arguments are assigned to their respective internal fields.  
     *
     * @param     file   the <tt>File</tt> to used
     * @param     key    the associated key for this object
     *
     * @exception  IllegalArgumentException  if either the <tt>file</tt> or 
     *                   <tt>key</tt> arguments are null.
     *
     * @exception  FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason.
     */
    public LogInputStream(File file, StreamKey key) 
        throws FileNotFoundException 
    {
        if (file == null || key == null)
            throw new IllegalArgumentException("Arguments cannot be null");
	in = new FileInputStream(file);
	this.key = key;
    }

    // javadoc inherited from supertype 
    public int read() throws IOException {
	if (pos >= count)
	    refill();
	if (count < 0)
	    return -1;

	offset++;
	return buf[pos++] & 0xFF;
    }

    // javadoc inherited from supertype 
    public int read(byte[] b) throws IOException {
	return read(b, 0, b.length);
    }

    // javadoc inherited from supertype 
    public int read(byte[] b, int off, int len) throws IOException {
	if (pos >= count)
	    refill();
	if (count < 0)
	    return -1;

	int rlen = Math.min(len, count - pos);
	System.arraycopy(buf, pos, b, off, rlen);
	pos += rlen;
	offset += rlen;
	return rlen;
    }
    
    // javadoc inherited from supertype 
    public long skip(long n) throws IOException {
	if (count < 0)
	    return 0;

	int slen = (int) Math.min(n, count - pos);
	pos += slen;
	n -= slen;
	if (n > 0)
	    slen += in.skip(n);

	offset += slen;
	return slen;
    }

    // javadoc inherited from supertype 
    public int available() throws IOException {
	return (count >= 0) ? (count - pos) + in.available() : 0;
    }

    // javadoc inherited from supertype 
    public void close() throws IOException {
	count = -1;
	in.close();
    }

    /**
     * Returns the current "read" offset into this stream object. 
     */
    public long getOffset() {
	return offset;
    }
    
    /**
     * Refills the internal buffer with any available bytes. This method
     * will block until either bytes have been read, or no more bytes can be
     * read.
     */
    private void refill() throws IOException {
	if (count < 0)
	    return;
	pos = 0;
	do {
	    count = in.read(buf);
	} while (count == 0);
    }

    // javadoc inherited from supertype 
    public Object getKey() {
        return key;
    }
}
