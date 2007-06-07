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

package com.sun.jini.jeri.internal.mux;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * ConnectionIO is an abstraction over a bi-directional byte stream
 * connection that provides the following features:
 *
 * - methods for sending sequences of bytes over the connection atomically
 *   (with respect to other threads) and asynchronously, with the option of
 *   receiving a notification when a given sequence has been written (i.e.
 *   when the buffer used to pass the sequence may again be used)
 *
 * - callbacks invoked on the host Mux object to process data that has been
 *   received over the connection, whenever new data arrives
 *
 * The ConnectionIO API uses java.nio.ByteBuffer objects to represent
 * sequences of bytes for writing or reading.
 *
 * The abstraction is intended to be implementable for both blocking
 * streams (requiring separate read and write threads per connection) and
 * non-blocking selectable channels (using a select-based I/O event handler
 * for completing I/O operations that would otherwise block).
 *
 * @author	Sun Microsystems, Inc.
 * 
 */
abstract class ConnectionIO {

    /** the Mux object associated with this instance */
    final Mux mux;

    /**
     * Constructs a new instance.  The supplied Mux object is used in
     * the following ways:
     *
     * - its processIncomingData method is invoked whenever new data is
     *   received over the connection.
     *
     * - its muxLock field is used for mutual exclusion of instance state,
     *   as well as for notification of state changes.  This lock is shared
     *   so that the muxDown field, and notifications of changes to it, are
     *   integrated with other state change notifications.
     */
    ConnectionIO(Mux mux) {
	this.mux = mux;
    }

    /**
     * Start whatever asynchronous activities are required for implementing
     * this instance.  This method must be invoked before invoking any of the
     * "send" methods, and data read from the connection will only be
     * dispatched to the Mux object after this method has been invoked.
     */
    abstract void start() throws IOException;

    /**
     * Sends the sequence of bytes contained in the supplied buffer to the
     * underlying connection.  The sequence of bytes is the contents of the
     * buffer between its current position and its limit.  This sequence is
     * guaranteed to be written atomically with respect to other threads
     * invoking this instance's "send" methods.
     *
     * The actual writing to the underlying connection, including access to
     * the buffer's contents and other state, is asynchronous with the
     * invocation of this method; therefore, the supplied buffer must not
     * be mutated even after this method has returned.
     */
    abstract void asyncSend(ByteBuffer buffer);

    /**
     * Sends the sequence of bytes contained in the supplied buffers to the
     * underlying connection.  The sequence of bytes is the contents of the
     * first buffer between its current position and its limit, followed by
     * the contents of the second buffer between its current position and
     * its limit.  This sequence is guaranteed to be written atomically with
     * respect to other threads invoking this instance's "send" methods.
     *
     * The actual writing to the underlying connection, including access to
     * the buffers' contents and other state, is asynchronous with the
     * invocation of this method; therefore, the supplied buffers must not
     * be mutated even after this method has returned.
     */
    abstract void asyncSend(ByteBuffer first, ByteBuffer second);

    /**
     * Sends the sequence of bytes contained in the supplied buffers to the
     * underlying connection.  The sequence of bytes is the contents of the
     * first buffer between its current position and its limit, followed by
     * the contents of the second buffer between its current position and
     * its limit.  This sequence is guaranteed to be written atomically with
     * respect to other threads invoking this instance's "send" methods.
     *
     * The actual writing to the underlying connection, including access to
     * the buffers' contents and other state, is asynchronous with the
     * invocation of this method; therefore, the supplied buffers must not
     * be mutated even after this method has returned, until it is guaranteed
     * that use of the buffers has completed.
     *
     * The returned IOFuture object can be used to wait until the write has
     * definitely completed (or will definitely not complete due to some
     * failure).  After the write has completed, each buffers' position will
     * have been incremented to its limit (which will not have changed).
     */
    abstract IOFuture futureSend(ByteBuffer first, ByteBuffer second);
}
