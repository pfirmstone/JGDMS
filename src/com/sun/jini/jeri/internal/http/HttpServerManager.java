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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import net.jini.io.context.AcknowledgmentSource;

/**
 * Class for managing server-side functions shared among multiple connections,
 * such as acknowledgment notification.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public class HttpServerManager {

    private static final Executor userThreadPool = (Executor)
	java.security.AccessController.doPrivileged(
	    new GetThreadPoolAction(true));

    private final AckListenerMap ackListeners;
    private final Object cookieLock = new Object();
    private long nextCookie = new Random().nextLong();

    /**
     * Creates new HttpServerManager which invalidates transport
     * acknowledgments after the given timeout.
     */
    public HttpServerManager(long ackTimeout) {
	ackListeners = new AckListenerMap(ackTimeout);
    }

    /**
     * Returns unique cookie string.
     */
    String newCookie() {
	synchronized (cookieLock) {
	    return Long.toString(Math.abs(nextCookie++), 16);
	}
    }

    /**
     * Registers listener waiting for given cookie.
     */
    void addAckListener(String cookie,
			AcknowledgmentSource.Listener listener)
    {
	if (cookie == null || listener == null) {
	    throw new NullPointerException();
	}
	synchronized (ackListeners) {
	    LinkedList list = (LinkedList) ackListeners.get(cookie);
	    if (list == null) {
		list = new LinkedList();
		ackListeners.put(cookie, list);
	    }
	    list.add(listener);
	}
    }

    /**
     * Notifies all listeners waiting for given cookie with received == true.
     * Notifications are performed in a separate thread.
     */
    void notifyAckListeners(String cookie) {
	if (cookie == null) {
	    throw new NullPointerException();
	}
	final LinkedList list = (LinkedList) ackListeners.remove(cookie);
	if (list != null) {
	    userThreadPool.execute(new Runnable() {
		public void run() { doAckNotifications(list, true); }
	    }, "Ack notifier");
	}
    }

    /**
     * Notifies list of AcknowledgmentSource.Listeners.
     */
    private static void doAckNotifications(LinkedList list, boolean recvd) {
	for (Iterator i = list.iterator(); i.hasNext(); ) {
	    AcknowledgmentSource.Listener al =
		(AcknowledgmentSource.Listener) i.next();
	    try { al.acknowledgmentReceived(recvd); } catch (Throwable th) {}
	}
    }
    
    /**
     * Map for tracking registered AcknowledgmentSource.Listeners.
     */
    private static class AckListenerMap extends TimedMap {
	
	AckListenerMap(long timeout) {
	    super(userThreadPool, timeout);
	}
	
	void evicted(Object key, Object value) {
	    doAckNotifications((LinkedList) value, false);
	}
    }
}
