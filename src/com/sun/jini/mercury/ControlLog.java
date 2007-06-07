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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.SyncFailedException;

/**
 * Class that implements the interface for a <tt>ControlLog</tt>. 
 * This class extends <tt>java.io.RandomAccessFile</tt> and relies on 
 * <code>LogStream</code>
 * interface for the actual reading/writing of control data. The only added
 * (convenience) method is <tt>sync</tt> which allows the user to 
 * force synchronization with the underlying device.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */

class ControlLog extends RandomAccessFile implements LogStream {

    /** Key associated with this stream. */
    private final StreamKey key;

    /**
     * Constructor that takes a <tt>File</tt> and <tt>StreamKey</tt> argument. 
     * The <tt>file</tt> argument is passed through to the superclass 
     * and is always opened in read/write mode. The <tt>key</tt> argument 
     * is assigned to the appropriate internal field. 
     *
     * @exception IOException if an I/O error occurs
     */
    ControlLog(File file, StreamKey key) throws IOException {
        super(file, "rw");
        this.key = key;
    }

    /**
     * Forces system buffers to synchronize with the underlying device.
     *
     * @exception IOException if an I/O error occurs
     *
     * @exception SyncFailedException if the buffers cannot be guaranteed to
     *                                have synchronized with physical media.
     */
    void sync() throws IOException, SyncFailedException {
        getFD().sync();
    }

    //
    // LogStream Methods
    //

    // javadoc inherited from supertype
    public Object getKey() {
        return key;
    }

    // Note: close() method is already implemented by superclass.
}
