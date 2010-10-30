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

import com.sun.jini.collection.SoftCache;
import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;
import java.util.HashSet;
import java.util.Set;

/**
 * Class for managing client-side functions shared among multiple connections
 * (e.g., tracking of unsent response acknowledgments, caching of information
 * about contacted HTTP servers).
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public class HttpClientManager {
    
    private static final Executor systemThreadPool = (Executor)
	java.security.AccessController.doPrivileged(
	    new GetThreadPoolAction(false));

    private final SoftCache rolodex = new SoftCache();
    private final TimedMap unsentAcks;

    /**
     * Creates new HttpClientManager which expires unsent acknowledgments after
     * the specified timeout.
     */
    public HttpClientManager(long ackTimeout) {
	unsentAcks = new TimedMap(systemThreadPool, ackTimeout);
    }
    
    /**
     * Forgets all cached information about contacted HTTP servers.
     */
    public void clearServerInfo() {
	rolodex.clear();
    }

    /**
     * Adds to list of unsent acknowledgments for server at given host/port.
     */
    void addUnsentAcks(String host, int port, String[] cookies) {
	if (cookies == null) {
	    throw new NullPointerException();
	}
	synchronized (unsentAcks) {
	    ServerKey key = new ServerKey(host, port);
	    Set set = (Set) unsentAcks.get(key);
	    if (set == null) {
		set = new HashSet();
		unsentAcks.put(key, set);
	    }
	    for (int i = 0; i < cookies.length; i++) {
		set.add(cookies[i]);
	    }
	}
    }

    /**
     * Removes cookies from list of unsent acknowledgments for server at given
     * host/port.
     */
    void clearUnsentAcks(String host, int port, String[] cookies) {
	if (cookies == null) {
	    throw new NullPointerException();
	}
	synchronized (unsentAcks) {
	    ServerKey key = new ServerKey(host, port);
	    Set set = (Set) unsentAcks.get(key);
	    if (set == null) {
		return;
	    }
	    for (int i = 0; i < cookies.length; i++) {
		set.remove(cookies[i]);
	    }
	    if (set.isEmpty()) {
		unsentAcks.remove(key);
	    }
	}
    }

    /**
     * Returns list of unsent acknowledgments for server at given host/port.
     */
    String[] getUnsentAcks(String host, int port) {
	synchronized (unsentAcks) {
	    Set set = (Set) unsentAcks.get(new ServerKey(host, port));
	    return (set != null) ? 
		(String[]) set.toArray(new String[set.size()]) : new String[0];
	}
    }

    /**
     * Returns cached information about specified HTTP server, or ServerInfo
     * struct with default values if no entry found.
     */
    ServerInfo getServerInfo(String host, int port) {
	ServerKey key = new ServerKey(host, port);
	synchronized (rolodex) {
	    ServerInfo info = (ServerInfo) rolodex.get(key);
	    return (info != null) ? 
		(ServerInfo) info.clone() : new ServerInfo(host, port);
	}
    }

    /**
     * Caches HTTP server information, overwriting any previously registered
     * information for server if timestamp is more recent.
     */
    void cacheServerInfo(ServerInfo info) {
	if (info.timestamp == ServerInfo.NO_TIMESTAMP) {
	    return;
	}
	ServerKey key = new ServerKey(info.host, info.port);
	synchronized (rolodex) {
	    ServerInfo oldInfo = (ServerInfo) rolodex.get(key);
	    if (oldInfo == null || info.timestamp > oldInfo.timestamp) {
		rolodex.put(key, info.clone());
	    }
	}
    }
    
    /**
     * Server lookup key.
     */
    private static class ServerKey {
	
	private final String host;
	private final int port;
	private final int hash;
	
	ServerKey(String host, int port) {
	    this.host = host;
	    this.port = port;
	    hash = (host.hashCode() << 10) | (port & 0x3FF);
	}
	
	public int hashCode() {
	    return hash;
	}
	
	public boolean equals(Object obj) {
	    if (obj instanceof ServerKey) {
		ServerKey key = (ServerKey) obj;
		return host.equals(key.host) && port == key.port;
	    }
	    return false;
	}
    }
}
