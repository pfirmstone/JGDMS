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
package com.sun.jini.collection;

import java.io.PrintStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * This class is designed to allow weakly held keys to weakly held
 * objects.  For example, it can be used for smart proxy objects that
 * maintain equality for references to the same remote server.  If a
 * single VM twice invokes a remote method that returns a proxy for the
 * same JavaSpaces server, the references returned by that method 
 * should be the same.  This allows <code>==</code> tests to work for 
 * proxies to remote servers the same as they would for direct references 
 * to remote servers, which also maintain this property.
 * <p>
 * Here is an example that uses this class to ensure that exactly one
 * copy of a <code>java.io.Resolvable</code> object exists in each
 * VM:
 * <pre>
 *  private WeakTable knownProxies;
 *
 *  public Object readResolve() {
 *      // deferred creation means this table is not allocated on the server
 *      if (knownProxies == null)
 *          knownProxies = new WeakTable();
 *      return knownProxies.getOrAdd(remoteServer, this);
 *  }
 * </pre>
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class WeakTable {
    /** The map of known objects.  */
    private HashMap		table = new HashMap();

    /** The queue of cleared SpaceProxy objects. */
    private ReferenceQueue	refQueue = new ReferenceQueue();

    /** Print debug messages to this stream if not <code>null</code>. */
    private static PrintStream		DEBUG = null;

    /** Object to call back when keys are collected */
    private KeyGCHandler handler = null;

    /**
     * Create a new WeakTable object to maintain the maps.
     */
    public WeakTable() {
	if (DEBUG != null)
	    DEBUG.println("Creating WeakTable");

	table = new HashMap();
	refQueue = new ReferenceQueue();
    }

    /**
     * Create a new WeakTable object to maintain the maps that calls
     * back the designated object when keys are collected.
     */
    public WeakTable(KeyGCHandler handler) {
	this();
	this.handler = handler;
    }

    /**
     * Return the object that this key maps to.  If it currently maps to
     * no object, set it to map to the given object.  Return either the
     * existing entry or the new one, whichever is used.
     */
    public synchronized Object getOrAdd(Object key, Object proxy) {
	Object existing = get(key);

	if (existing != null) {
	    if (DEBUG != null)
		DEBUG.println("WeakTable.getOrAdd: found " + existing);
	    return existing;
	} else {
	    if (DEBUG != null)
		DEBUG.println("WeakTable.getOrAdd: adding " + proxy);
	    table.put(new WeakKeyReference(key, refQueue),
			     new WeakReference(proxy, refQueue));
	    return proxy;
	}
    }

    /**
     * Return the value associated with given key, or <code>null</code>
     * if no value can be found.
     */
    public synchronized Object get(Object key) {
	removeBlanks();
	WeakKeyReference keyRef = new WeakKeyReference(key);
	WeakReference ref = (WeakReference) table.get(keyRef);
	Object existing = (ref == null ? null : ref.get());
	if (DEBUG != null) {
	    DEBUG.println("WeakTable.get:ref = " + ref
			       + ", existing = " + existing);
	}

	return existing;
    }

    /**
     * Remove the object that the given key maps to.  If found return
     * the object, otherwise return null.
     */
    public synchronized Object remove(Object key) {
	removeBlanks();
	WeakKeyReference keyRef = new WeakKeyReference(key);
	WeakReference ref = (WeakReference) table.remove(keyRef);
	if (ref == null) return null;
	return ref.get();
    }
    
    /**
     * Remove any blank entries from the table.  This can be invoked
     * by a reaping thread if you like.
     */
    /*
     * We only clear table entries when the key shows up in the queue,
     * since that is much more efficient.  Since the key is usually the
     * remote reference, and since the remote reference is usually
     * referenced only from the proxy, the key reference should be
     * cleared around the same time as the proxy reference.  If not,
     * then all the hangs around unnecessarily is this table entry,
     * which is small.  This tradeoff seems worth the significant
     * efficiency of using the HashMap in the intended way -- efficient
     * key access.
     */
    public synchronized void removeBlanks() {
	if (DEBUG != null)
	    DEBUG.println("WeakTable.removeBlanks: starting");
	Reference ref;
	while ((ref = refQueue.poll()) != null) {
	    if (ref instanceof WeakKeyReference) {
		final WeakReference valref = (WeakReference)table.remove(ref);
		if (valref != null && handler != null && valref.get() != null) 
		    handler.keyGC(valref.get());

		if (DEBUG != null) {
		    boolean removed = (valref != null);		    
		    DEBUG.print("WeakTable.removeBlanks: key=" + ref);
		    DEBUG.println(", " + (removed ? "" : "!") + "removed, "
				  + table.size() + " remain");
		}
	    } else {
		if (DEBUG != null)
		    DEBUG.println("WeakTable.removeBlanks: value=" + ref);
	    }
	}
	if (DEBUG != null)
	    DEBUG.println("WeakTable.removeBlanks: finished");
    }

    /**
     * Handler for clients that need to know when a key is removed
     * from the table because it has been collected.
     */
    public static interface KeyGCHandler {
	/** Called by WeakTable when it notices that a key has been
	 *   collected and the value still exists. 
	 *   @param value The value associated with the collected key
	 */
	public void keyGC(Object value);
    }
}
