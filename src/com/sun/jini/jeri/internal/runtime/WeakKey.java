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

package com.sun.jini.jeri.internal.runtime;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * WeakKey objects are used by the object table to hold weak references
 * to DGC-enabled remote objects in the exported object table, so that
 * such a remote object with no known client references may be (locally)
 * garbage collected.
 *
 * In order for WeakKey objects to be used as keys in the object table,
 * this class extends java.lang.ref.WeakReference to override the
 * hashCode and equals methods such that WeakKey objects hash and compare
 * to each other according to the identity of their referents.  The
 * identity-based comparison is necessary to insulate the runtime from
 * arbitrary remote implementation classes' notions of object equality.
 *
 * @author	Sun Microsystems, Inc.
 **/
public final class WeakKey extends WeakReference {

    /**
     * saved value of the referent's identity hash code, to maintain
     * a consistent hash code after the referent has been cleared
     */
    private final int hash;

    /**
     * Create a new WeakKey to the given object.
     */
    public WeakKey(Object obj) {
	super(obj);
	hash = System.identityHashCode(obj);
    }

    /**
     * Create a new WeakKey to the given object, registered with a queue.
     */
    public WeakKey(Object obj, ReferenceQueue refQueue) {
	super(obj, refQueue);
	hash = System.identityHashCode(obj);
    }

    /**
     * Returns the identity hash code of the original referent.
     */
    public int hashCode() {
	return hash;
    }

    /**
     * Returns true if the given object is this identical WeakKey instance,
     * or, if this object's referent has not been cleared, if the given
     * object is another WeakKey instance with the identical non-null
     * referent as this one.
     */
    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	}

	if (obj instanceof WeakKey) {
	    Object referent = get();
	    return (referent != null) && (referent == ((WeakKey) obj).get());
	} else {
	    return false;
	}
    }
}
