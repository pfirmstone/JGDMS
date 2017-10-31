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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * Implemented as per Ref.SOFT
 * 
 * @see Ref#SOFT
 * @author Peter Firmstone.
 */
class SoftEqualityReference<T> extends SoftReference<T> implements Referrer<T>{
    private final int hash; // Once the object is garbage collected, hash is the only identifier.

    SoftEqualityReference(T k, ReferenceQueue<? super T> q) {
        super(k,q);
        int hash = 7;
        hash = 29 * hash + k.hashCode();
        hash = 29 * hash + k.getClass().hashCode();
        this.hash = hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)  return true; // Same reference.
        if (!(o instanceof Referrer))  return false;
        Object k1 = get();
        Object k2 = ((Referrer) o).get();
        if ( k1 != null && k1.equals(k2)) return true;
        return ( k1 == null && k2 == null && hashCode() == o.hashCode());
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
    
    @Override
    public String toString(){
        Object s = get();
        if (s != null) return s.toString();
        return super.toString();
    }
}
