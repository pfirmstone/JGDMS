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

package com.sun.jini.jeri.internal.http;

import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;

/**
 * Utility class for timing out connections.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public class ConnectionTimer {
    
    private static final Executor systemThreadPool = (Executor)
	java.security.AccessController.doPrivileged(
	    new GetThreadPoolAction(false));

    private final TimeoutMap timeouts;

    /**
     * Creates new ConnectionTimer which shuts down overdue connections after
     * the given timeout.
     */
    public ConnectionTimer(long timeout) {
	timeouts = new TimeoutMap(timeout);
    }

    /**
     * Schedules timeout for given connection.  If timeout is already scheduled
     * for given connection, renews timeout.  When the timeout occurs, the
     * connection's shutdown method will be called with the given force value.
     */
    public void scheduleTimeout(TimedConnection conn, boolean force) {
	if (conn == null) {
	    throw new NullPointerException();
	}
	timeouts.put(conn, new Boolean(force));
    }

    /**
     * Attempts to cancel timeout for the given connection.  Returns true if a
     * timeout was successfully cancelled, false otherwise (e.g. if connection
     * was never scheduled for a timeout, has already been timed out, or is
     * already in the midst of being timed out).
     */
    public boolean cancelTimeout(TimedConnection conn) {
	if (conn == null) {
	    throw new NullPointerException();
	}
	return timeouts.remove(conn) != null;
    }

    /**
     * Map for tracking idle connection timeouts.
     */
    private static class TimeoutMap extends TimedMap {
	
	TimeoutMap(long timeout) {
	    super(systemThreadPool, timeout);
	}
	
	void evicted(Object key, Object value) {
	    boolean force = ((Boolean) value).booleanValue();
	    ((TimedConnection) key).shutdown(force);
	}
    }
}
