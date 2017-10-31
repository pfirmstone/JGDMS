/* Copyright (c) 2010-2012 Zeus Project Services Pty Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.concurrent;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.lang.ref.ReferenceQueue;

/**
 * Implemented as per Ref.STRONG
 * 
 * @see Ref#STRONG
 * @author Peter Firmstone
 */
class StrongReference<T> implements Referrer<T>{
    private T referent;
    private final int hash;
    
    /**
     * Creates a new strong reference that refers to the given object.  The new
     * reference is not registered with any queue.
     *
     * @param referent object the new weak reference will refer to
     */
    StrongReference(T referent){
        this.referent = referent ;
        int hash = 7;
        hash = 29 * hash + referent.hashCode();
        hash = 29 * hash + referent.getClass().hashCode();
        this.hash = hash;
    }

    /**
     * Creates a new strong reference that refers to the given object.  The
     * reference queue is silently ignored.
     *
     * @param referent object the new weak reference will refer to
     * @param q queue is never used.
     */
    StrongReference(T referent, ReferenceQueue<? super T> q) {
	this(referent);
    }

    @Override
    public int hashCode() {
        Object k = get();
        int hash = 7;
        if (k != null) {
            hash = 29 * hash + k.hashCode();
            hash = 29 * hash + k.getClass().hashCode();
        } else {
            hash = this.hash;
        }
        return hash;
    }
    
    public boolean equals(Object o){
        if (this == o)  return true; // Same reference.
        if (!(o instanceof Referrer))  return false;
        Object k1 = get();
        Object k2 = ((Referrer) o).get();
        if ( k1 != null && k1.equals(k2)) return true;
        return ( k1 == null && k2 == null && hashCode() == o.hashCode());
    }
    
    @Override
    public String toString(){
        Object s = get();
        if (s != null) return s.toString();
        return super.toString();
    }

    @Override
    public void clear() {
	this.referent = null;
    }
    
    @Override
    public T get() {
        return referent;
    }

    @Override
    public boolean isEnqueued() {
        return false;
    }

    @Override
    public boolean enqueue() {
        return false;
    }
}
