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
import java.util.HashMap;
import java.util.Map;

/**
 * Simple hash map which evicts entries after a fixed timeout.  All operations
 * which modify a TimedMap synchronize on the TimedMap instance itself,
 * including the thread which evicts expired entries.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
class TimedMap {
    
    private final Executor executor;
    private final long timeout;
    private final Map map = new HashMap();
    private final Queue evictQueue = new Queue();
    private boolean evictorActive = false;

    /**
     * Creates empty TimedMap which uses threads from the given Executor to
     * evict entries after the specified timeout.
     */
    TimedMap(Executor executor, long timeout) {
	if (executor == null) {
	    throw new NullPointerException();
	}
	if (timeout < 0) {
	    throw new IllegalArgumentException();
	}
	this.executor = executor;
	this.timeout = timeout;
    }
    
    /**
     * Associates the given key with the given value, resetting the key's
     * timeout.  Returns value (if any) previously associated with key.
     */
    synchronized Object put(Object key, Object value) {
	if (!evictorActive) {
	    executor.execute(new Evictor(), "TimedMap evictor");
	    evictorActive = true;
	}
	long now = System.currentTimeMillis();
	Mapping mapping = new Mapping(key, value, now + timeout);
	evictQueue.append(mapping);

	if ((mapping = (Mapping) map.put(key, mapping)) == null) {
	    return null;
	}
	if (mapping.expiry <= now) {
	    mapping.remove = false;
	    return null;
	}
	evictQueue.remove(mapping);
	return mapping.value;
    }
    
    /**
     * Returns value associated with given key, or null if no mapping for key
     * is found.  Resets timeout for key if it is present in map.
     */
    synchronized Object get(Object key) {
	Mapping mapping = (Mapping) map.get(key);
	if (mapping == null) {
	    return null;
	}
	long now = System.currentTimeMillis();
	if (mapping.expiry <= now) {
	    return null;
	}
	evictQueue.remove(mapping);
	mapping.expiry = now + timeout;
	evictQueue.append(mapping);
	return mapping.value;
    }
    
    /**
     * Removes mapping for key from map, returning the value associated with
     * the key (or null if no mapping present).
     */
    synchronized Object remove(Object key) {
	Mapping mapping = (Mapping) map.remove(key);
	if (mapping == null) {
	    return null;
	}
	if (mapping.expiry <= System.currentTimeMillis()) {
	    mapping.remove = false;
	    return null;
	}
	evictQueue.remove(mapping);
	return mapping.value;
    }
    
    /**
     * Upcall invoked after key's timeout has expired and key has been removed
     * from the map.
     */
    void evicted(Object key, Object value) {
    }

    /**
     * Key/value mapping.
     */
    private static class Mapping extends Queue.Node {

	final Object key;
	final Object value;
	long expiry;
	boolean remove = true;
	
	Mapping(Object key, Object value, long expiry) {
	    this.key = key;
	    this.value = value;
	    this.expiry = expiry;
	}
    }
    
    /**
     * Expired mapping eviction thread.
     */
    private class Evictor implements Runnable {

	public void run() {
	    Mapping mapping;
	    while ((mapping = nextEvicted()) != null) {
		evicted(mapping.key, mapping.value);
	    }
	}
	
	private Mapping nextEvicted() {
	    synchronized (TimedMap.this) {
		Mapping mapping;
		while ((mapping = (Mapping) evictQueue.getHead()) != null) {
		    long now = System.currentTimeMillis();
		    if (mapping.expiry <= now) {
			evictQueue.remove(mapping);
			if (mapping.remove) {
			    map.remove(mapping.key);
			}
			return mapping;
		    } else {
			try {
			    TimedMap.this.wait(mapping.expiry - now);
			} catch (InterruptedException ex) {
			}
		    }
		}
		evictorActive = false;
		return null;
	    }
	}
    }

    /**
     * Lightweight doubly-linked queue supporting constant-time manipulation.
     */
    private static class Queue {
	
	static class Node {
	    private Queue owner;
	    private Node prev;
	    private Node next;
	}
	
	private Node head;
	private Node tail;

	Queue() {
	    head = new Node();
	    tail = new Node();
	    head.next = tail;
	    tail.prev = head;
	}
	
	Node getHead() {
	    return (head.next != tail) ? head.next : null;
	}
	
	void remove(Node node) {
	    if (node.owner != this) {
		throw new IllegalArgumentException();
	    }
	    node.prev.next = node.next;
	    node.next.prev = node.prev;
	    node.prev = node.next = null;
	    node.owner = null;
	}
	
	void append(Node node) {
	    if (node.owner != null) {
		throw new IllegalArgumentException();
	    }
	    node.owner = this;
	    node.prev = tail.prev;
	    node.next = tail;
	    tail.prev.next = node;
	    tail.prev = node;
	}
    }
}
