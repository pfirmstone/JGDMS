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
import java.io.OutputStream;
import java.io.RandomAccessFile;

/** 
 * This class extends the functionality of the java.io.OutputStream class
 * in order to provide an output mechanism that can be used by processes
 * that perform logging operations; in particular, processes that store 
 * state in order to provide persistence.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see java.io.OutputStream
 */
public class LogOutputStream extends OutputStream {

    private RandomAccessFile raf;
    
    /**
     * Creates an output file with the specified <code>RandomAccessFile</code>
     *
     * @param raf the output file
     *
     * @exception IOException If an I/O error has occurred.
     */
    public LogOutputStream(RandomAccessFile raf) throws IOException {
	this.raf = raf;
    }
    
    /**
     * Writes a byte of data. This method will block until the byte is 
     * actually written.
     *
     * @param b the byte to be written
     *
     * @exception IOException If an I/O error has occurred.
     */
    public void write(int b) throws IOException {
	raf.write(b);
    }

    /**
     * Writes an array of bytes. Will block until the bytes
     * are actually written.
     *
     * @param b	the data to be written
     *
     * @exception IOException If an I/O error has occurred.
     */
    public void write(byte b[]) throws IOException {
	raf.write(b);
    }

    /**
     * Writes a sub-array of bytes. 
     *
     * @param b	the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes to write
     *
     * @exception IOException If an I/O error has occurred.
     */
    public void write(byte b[], int off, int len) throws IOException {
	raf.write(b, off, len);
    }

    /**
     * A LogOutputStream cannot be closed, so this does nothing.
     *
     * @exception IOException If an I/O error has occurred.
     */
    public final void close() throws IOException {
    }

}
