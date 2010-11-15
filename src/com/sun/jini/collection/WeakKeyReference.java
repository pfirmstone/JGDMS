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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * A weak reference to a key in a table.  Its hash code is that of its
 * key at the time of the reference's creation.  Its
 * <code>equals</code> method will compare itself to another
 * <code>WeakKeyReference</code>, or to another object that is then
 * compared to the key held by this reference.
 * <p>
 * This class is public so that it can be used in other tables for
 * which <code>WeakTable</code> won't work.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class WeakKeyReference extends WeakReference {
    /** The key's hashcode, set at creation. */
    private final int hashCode;

    /** Print debug messages to this stream if not <code>null</code>. */
    private static final java.io.PrintStream DEBUG = null;

    /**
     * Create a new <code>WeakReference</code> to the given key.  The
     * reference is placed on no queue.
     */
    public WeakKeyReference(Object key) {
	super(key);
	hashCode = key.hashCode();
    }

    /**
     * Create a new <code>WeakReference</code> to the given key, placing
     * the cleared reference on the specified <code>ReferenceQueue</code>.
     */
    public WeakKeyReference(Object key, ReferenceQueue refQueue) {
	super(key, refQueue);
	hashCode = key.hashCode();
    }

    /**
     * Return the key's hashCode remembered from the time of reference
     * creation.  If you intend to insert the key in a table after changing
     * the key in a way that affects the hashCode, you will need to create
     * a new <code>WeakKeyReference</code> for that new table.
     */
    public int hashCode() {
	return hashCode;
    }

    /**
     * Equivalence for WeakKeyReference is defined as follows:
     * <ul>
     * <li>If the other reference is to this object, return <code>true</code>.
     * <li>If this object's key is cleared, return <code>false</code>.
     * <li>If the other reference is to a WeakKeyReference object, return
     *     <code>false</code> if the other's key is cleared; otherwise return
     *     the result of invoking <code>equals</code> on the two keys.
     * </ul>
     */
    public boolean equals(Object other) {
	if (other == null)
	    return false;
	if (this == other)
	    return true;
	if (!(other instanceof WeakKeyReference))
	    return false;
	Object thisRef = get();
	Object otherRef = ((WeakKeyReference) other).get();
	if (thisRef == null || otherRef == null) // if null it's not *anything*
	    return false;
	else
	    return thisRef.equals(otherRef);
    }
}
