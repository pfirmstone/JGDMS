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

package com.artima.lookup.util;

import java.util.Map;
import java.io.Serializable;

/**
 * An implementation of the <code>java.util.Map.Entry</code> interface that has
 * a serialized form consistent in all virtual machines. <code>ConsistentMapEntry</code>
 * instances are unmodifiable. The <code>setValue</code> mutator method
 * throws <code>UnsupportedOperationException</code>.  This class permits <code>null</code>
 * for values and keys.
 *
 * <p>
 * Although instances of this class are unmodifiable, they are not necessarily
 * immutable. If a client retrieves a mutable object (either a key or value) contained in a
 * <code>ConsistentMapEntry</code> and mutates that object, the client in effect
 * mutates the state of the <code>ConsistentMapEntry</code>. In this case, the
 * serialized form of the <code>ConsistentMapEntry</code> will most likely also
 * have been mutated. A <code>ConsistentMapEntry</code> that contains only immutable
 * objects will maintain a consistent serialized form indefinitely. But a 
 * <code>ConsistentMapEntry</code> that contains mutable objects will maintain a
 * consistent serialized form only so long as the mutable objects are not
 * mutated.
 *
 * @author Bill Venners
 */
final class ConsistentMapEntry implements Map.Entry, Serializable {

    private static final long serialVersionUID = -8633627011729114409L;

    /**
     * @serial An <code>Object</code> key, or <code>null</code>
     */
    private Object key;

    /**
     * @serial An <code>Object</code> value, or <code>null</code>
     */
    private Object value;

    /**
     * Constructs a new <code>ConsistentMapEntry</code> with passed
     * <code>key</code> and <code>value</code>. <code>null</code> is
     * allowed in either (or both) parameters.
     *
     * @param key the key (<code>null</code> key is OK)
     * @param value the value (<code>null</code> value is OK) associated with the key
     */
    public ConsistentMapEntry(Object key, Object value) {

        this.key = key;
        this.value = value;
    }

    /**
     * Returns the key.
     *
     * @return the key.
     */
    public Object getKey() {
        return key;
    }

    /**
     * Returns the value.
     *
     * @return the value.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Replaces the value corresponding to this entry with the specified value. Because
     * all instances of this class are unmodifiable, this method always throws
     * <code>UnsupportedOperationException</code>.
     *
     * @throws UnsupportedOperationException always
     */
    public Object setValue(Object value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Compares the specified object (the <CODE>Object</CODE> passed
     * in <CODE>o</CODE>) with this <CODE>ConsistentMapEntry</CODE>
     * object for equality. Returns true if the specified object
     * is not <code>null</code>, if the specified object's class is
     * <CODE>ConsistentMapEntry</CODE>, if the keys of this object and
     * the specified object are either both <code>null</code> or semantically
     * equal, and the values of this object and the specified object are either
     * both <code>null</code> or semantically equal.
     *
     * @param o the object to compare against
     * @return <code>true</code> if the objects are the semantically equal,
     *     <code>false</code> otherwise.
     */
    public boolean equals(Object o) {

        if (o == null) {
            return false;
        }

        if (o == this) {
            return true;
        }

        // TODO: ASK JOSH SHOULD EQUALS CHECK FOR INSTANCEOF OR EXACT CLASS?
        if (o.getClass() != ConsistentMapEntry.class) {
            return false;
        }

        ConsistentMapEntry unmod = (ConsistentMapEntry) o;

        boolean keysEqual = equalsOrNull(key, unmod.key);
        boolean valsEqual = equalsOrNull(value, unmod.value);

        return keysEqual && valsEqual;
    }

    private static boolean equalsOrNull(Object o1, Object o2) {
        return (o1 == null ? o2 == null : o1.equals(o2));
    }

    /**    
     * Returns the hash code value for this <CODE>ConsistentMapEntry</CODE> object.
     *
     * @return the hashcode for this object
     */ 
    public int hashCode() {

        int keyHash = (key == null ? 0 : key.hashCode());
        int valueHash = (value == null ? 0 : value.hashCode());

        return keyHash ^ valueHash;
    }
}

