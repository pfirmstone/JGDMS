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

import java.io.IOException;
import java.io.InputStream;

/** 
 * This class extends the functionality of the java.io.InputStream class
 * in order to provide an input mechanism that can be used by processes
 * that perform logging operations; in particular, processes that store 
 * state in order to provide persistence.
 *
 * @author Sun Microsystems, Inc.
 *
 *  @see java.io.InputStream
 */
public class LogInputStream extends InputStream {
    private InputStream in;
    private int length;

    /**
     * Creates a log input file with the specified input stream.
     *
     * @param in the input stream
     * @param length the total number of bytes allowed to be read
     *
     * @exception IOException If an I/O error has occurred.
     */
    public LogInputStream(InputStream in, int length) throws IOException {
	this.in = in;
	this.length = length;
    }

    /**
     * Throw exception for reading past end of log record.
     *
     * @param numBytes number of bytes attempted to be read
     *
     * @exception LogException Attempt to read past end of log.
     */
    private void badRead(int numBytes) throws LogException {
	throw new LogException("length=" +
			       String.valueOf(length) +
			       ", numBytes=" +
			       String.valueOf(numBytes));
    }
    
    /**
     * Reads a byte of data. This method will block if no input is 
     * available.
     *
     * @return the byte read, or -1 if the end of the stream is reached
     *
     * @exception LogException Attempt to read past end of log.
     * @exception IOException If an I/O error has occurred.
     */
    public int read() throws IOException, LogException {
	if (length < 1)
	    badRead(1);
	length--;
	return in.read();
    }

    /**
     * Reads data into an array of bytes.
     * This method blocks until some input is available.
     *
     * @param b	the buffer into which the data is read
     *
     * @return  the actual number of bytes read, -1 is
     * 		returned when the end of the stream is reached
     *
     * @exception LogException Attempt to read past end of log.
     * @exception IOException If an I/O error has occurred.
     */
    public int read(byte b[]) throws IOException, LogException {
	if (length < b.length)
	    badRead(b.length);
	int len = in.read(b);
	length -= len;
	return len;
    }

    /**
     * Reads data into an array of bytes.
     * This method blocks until some input is available.
     *
     * @param b	the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes read
     *
     * @return  the actual number of bytes read, -1 is
     * 		returned when the end of the stream is reached
     *
     * @exception LogException Attempt to read past end of log.
     * @exception IOException If an I/O error has occurred.
     */
    public int read(byte b[], int off, int len)
	throws IOException, LogException
    {
	if (length < len)
	    badRead(len);
	len = in.read(b, off, len);
	length -= len;
	return len;
    }

    /**
     * Skips n bytes of input.
     *
     * @param n the number of bytes to be skipped
     *
     * @return the actual number of bytes skipped
     *
     * @exception LogException Attempt to read past end of log.
     * @exception IOException If an I/O error has occurred.
     */
    public long skip(long n) throws IOException, LogException {
	if (length < n)
	    badRead((int)n);
	n = in.skip(n);
	length -= (int)n;
	return n;
    }
    
    /**
     * Returns the number of bytes that can be read without blocking.
     *
     * @return the number of available bytes, which is initially
     *		equal to the file size
     */
    public int available() {
	return length;
    }

    /**
     * Closes the input stream.  No further input can be read.
     */
    public void close() {
	length = 0;
    }

    /**
     * Closes the stream when garbage is collected.
     */
    protected void finalize() throws IOException {
	close();
    }

}
