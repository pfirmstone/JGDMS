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

package org.apache.river.impl.util;

/**
 * Ref enum represents the types of references provided by the jvm, it also
 * defines equals contract behaviour for Reference implementations suitable
 * for use in Collections.
 * 
 * It is recommended to use STRONG or IDENTITY based references as keys
 * in Maps, due to the unpredictability that occurs, when an equal
 * Reference Key disappears due to garbage collection.
 * 
 * Map implementations must delete their key -> value mapping when either the 
 * key or value References become unreachable.
 * 
 * Object.toString() is overridden in the reference implementations to return
 * toString() of the referent, if it is still reachable, otherwise the reference
 * calls their superclass toString() method, where superclass is a java 
 * Reference subclass.
 * 
 * Phantom references are not used, they are designed to replace
 * {@link Object#finalize() } and remain unreachable, but not garbage collected until
 * the {@link PhantomReference} also becomes unreachable, get() always returns
 * null.
 *
 * @see Reference
 * @see WeakReference
 * @see SoftReference
 * @see PhantomReference
 * @see Map
 * @see ConcurrentMap
 * @see List
 * @see Set
 * @see Collection
 * @see Comparable
 * @author Peter Firmstone.
 */
public enum Ref {
    /**
     * SOFT References implement equals based on equality of the referent 
     * objects, while the referent is still reachable.  The hashCode
     * implementation is based on the referent implementation of hashCode,
     * while the referent is reachable.
     * 
     * After garbage collection, Reference equality is based
     * on the original identity of the referents using System.identityHashCode().
     * 
     * Because {@link System#identityHashCode(java.lang.Object)} is not unique, 
     * the referents Class.hashCode() is also used to calculate the hashCode, 
     * generated during Reference construction.
     * 
     * SOFT References implement Comparable allowing the referent Objects
     * to be compared if they implement Comparable.  If the referent Object
     * doesn't implement Comparable, the hashCode's of the Reference is 
     * compared instead.  If the referent Objects don't implement Comparable,
     * then they shouldn't really be used in sorted collections.
     * 
     * Garbage collection must be the same as SoftReference.
     * @see SoftReference
     * @see WeakReference
     * @see Comparable
     */
    SOFT,
    /**
     * SOFT_IDENTY References implement equals based on identity == of the
     * referent objects.
     * 
     * Garbage collection must be the same as SoftReference.
     * @see SoftReference
     */
    SOFT_IDENTITY,
    /**
     * WEAK References implement equals based on equality of the referent 
     * objects, while the referent is still reachable.  The hashCode
     * implementation is based on the referent implementation of hashCode,
     * while the referent is reachable.
     * 
     * After garbage collection, Reference equality is based
     * on the original identity of the referents using System.identityHashCode().
     * 
     * Because System.identityHashCode() is not unique, the referents
     * Class.hashCode() is also used to calculate the hashCode, generated during
     * Reference construction.
     * 
     * WEAK References implement comparable allowing the referent Objects
     * to be compared if they implement Comparable.  If the referent Object
     * doesn't implement Comparable, the hashCode's of the Reference is 
     * compared instead.  If the referent Object's don't implement Comparable,
     * then they shouldn't really be used in sorted collections.
     * 
     * Garbage collection must be the same as WeakReference.
     * @see WeakReference
     * @see Comparable
     */
    WEAK,
    /**
     * WEAK_IDENTY References implement equals based on identity == of the
     * referent objects.
     * 
     * Garbage collection must be the same as WeakReference.
     * 
     * @see WeakReference
     */
    WEAK_IDENTITY,
    /**
     * STRONG References implement equals and hashCode() based on the 
     * equality of the underlying Object.
     * 
     * STRONG References implement Comparable allowing the referent Objects
     * to be compared if they implement Comparable.  If the referent Object
     * doesn't implement Comparable, the hashCode's of the Reference is 
     * compared instead.  If the referent Object's don't implement Comparable,
     * then they shouldn't really be used in sorted collections.
     * 
     * Garbage collection doesn't occur until the Reference is cleared.
     * @see Comparable
     */
    STRONG
}
