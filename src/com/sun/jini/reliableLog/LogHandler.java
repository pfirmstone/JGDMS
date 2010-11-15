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

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * A LogHandler represents snapshots and update records as serializable
 * objects.
 * <p>
 * This implementation does not know how to create an initial snaphot or
 * apply an update to a snapshot.  The client must specify these methods
 * via a subclass.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see ReliableLog
 */
public abstract class LogHandler {

    /**
     * Creates a LogHandler for a ReliableLog.
     */
    public LogHandler() {}
    
    /**
     * Writes the snapshot to a stream.  This callback is invoked when
     * the client calls the snaphot method of ReliableLog.
     *
     * @param out the output stream
     *
     * @exception Exception can raise any exception
     */
    public abstract void snapshot(OutputStream out) throws Exception;
    
    /**
     * Read the snapshot from a stream.  This callback is invoked when
     * the client calls the recover method of ReliableLog.  
     *
     * @param in the input stream
     *
     * @exception Exception can raise any exception
     */
    
    public abstract void recover(InputStream in) throws Exception;
    
    /**
     * Writes the representation (a serializable object) of an update 
     * to a stream.  This callback is invoked when the client calls the 
     * update method of ReliableLog.
     *
     * @param out the output stream
     * @param value the update object
     *
     * @exception Exception can raise any exception
     */
    public void writeUpdate(OutputStream out, Object value) throws Exception {
	ObjectOutputStream s = new ObjectOutputStream(out);
 	s.writeObject(value);
	s.flush();
    }
    
    /**
     * Reads a stably logged update (a serializable object) from a
     * stream.  This callback is invoked during recovery, once for
     * every record in the log.  After reading the update, this method
     * invokes the applyUpdate (abstract) method in order to execute
     * the update.
     *
     * @param in the input stream
     *
     * @exception Exception can raise any exception
     */
    public void readUpdate(InputStream in) throws Exception {
	ObjectInputStream  s = new ObjectInputStream(in);
	applyUpdate(s.readObject());
    }

    /**
     * Reads a stably logged update (a serializable object) from a stream.  
     * This callback is invoked during recovery, once for every record in the 
     * log.  After reading the update, this method is invoked in order to
     * execute the update.
     *
     * @param update the update object
     *
     * @exception Exception can raise any exception
     */
    public abstract void applyUpdate(Object update) throws Exception;
    
}
