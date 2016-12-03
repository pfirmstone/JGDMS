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

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;

/**
 * <p>
 * Ref enum represents types of references available for use in java
 * collection framework implementations.
 * </p><p>
 * Only use STRONG, WEAK_IDENTITY and TIME based references as keys
 * in Maps, use of other reference types should be discouraged
 * due to unpredictable results, when for example, an equal WEAK
 * Reference Key disappears due to garbage collection, or SOFT reference keys
 * aren't garbage collected as expected.
 * </p><p>
 * Map implementations delete their key -&gt; value mapping when either the 
 * key or value References become unreachable. ConcurrentMap's will retry until
 * putIfAbsent is successful when an existing Referrer key is cleared.
 * </p><p>
 * Only use STRONG and TIME based references in Queue's, other types break
 * Queue's contract, null has special meaning, a cleared reference returns
 * null.
 * </p><p>
 * Object.toString() is overridden in reference implementations to call
 * toString() on the referent, if still reachable, otherwise the reference
 * calls the superclass toString() method, where the superclass is a java 
 * Reference subclass.  Consideration is being given to returning a null string
 * "" instead, if you feel strongly about this, please contact the author.
 * </p><p>
 * Phantom references are not used, they are designed to replace
 * {@link Object#finalize() } and remain unreachable, but not garbage collected until
 * the {@link PhantomReference} also becomes unreachable, get() always returns
 * null.
 * </p><p>
 * TIME and SOFT and SOFT_IDENTITY references update their access timestamp, 
 * when Referrer.get(), Referrer.equals() or Referrer.toString() is called.  
 * SOFT and SOFT_IDENTITY do so lazily and are not guaranteed to
 * succeed in updating the access time.  SOFT references also update their
 * access timestamp when either Referrer.hashCode() or Comparable.compareTo() 
 * is called.
 * </p><p>
 * For sets and map keys that require traversal using a Comparator or
 * Comparable, access times will be updated during each traversal. In
 * these circumstances, SOFT and SOFT_IDENTITY references are typically not enqueued and
 * do not behave as expected. 
 * </p><p>
 * SOFT references are only suited for use in lists or as values in maps and not
 * suitable for keys in hash or tree maps.
 * </p><p>
 * TIME references only update their access timestamp during traversal when
 * a Comparator or Comparable returns zero (a successful match), or when equals is true.
 * TIME references are suitable for use as keys in tree and hash based maps 
 * as well as sets and tasks in Queues.  In fact TIME is the only referrer
 * suitable for use in Queue's and their subtypes.  Tasks in a Queue, if
 * timed out, are first cancelled, then removed, they are not cleared as
 * doing so could cause a null poll() return when a queue is not empty,
 * which would violate the contract for Queue.
 * </p><p>
 * SOFT_IDENTITY references are suitable for use as
 * keys in hash tables, hash maps and hash sets, since hashCode() does not update
 * the access timestamp, while equals() does.  SOFT_IDENTITY references are not
 * recommended for use in tree maps, tree sets or queues.
 * </p>
 * @see Reference
 * @see WeakReference
 * @see SoftReference
 * @see PhantomReference
 * @see Comparable
 * @author Peter Firmstone.
 */
public enum Ref {
    /**
     * <P>
     * TIME References implement equals based on equality of the referent
     * objects.  Time references are STRONG references that are removed
     * after a period of no access, even if they are strongly
     * referenced outside the cache, they are removed.  TIME references don't
     * rely on Garbage Collection algorithms.
     * </P><P>
     * TIME References support cancellation of tasks implementing Future, when
     * the reference times out, if it contains a Future, it is cancelled.
     * </P><P>
     * A call to equals(), get() or toString(), will cause the timestamp on
     * the reference to be updated, whereas hashCode() will not, in addition,
     * Comparators and referents that implement Comparable, only update the
     * timestamp if they return 0.  This allows referents to be inspected
     * without update when they don't match.
     * </P><P>
     * TIME References require synchronisation for iteration, so during
     * cleaning periods, a synchronised Collection or Map will be locked.
     * A lock is still obtained for iterating over Concurrent Maps and 
     * Collections, however this does not normally synchronise access between threads,
     * only other cleaning task threads.
     * </P>
     * @see Future
     * 
     */
    TIME,
    /**
     * <P>
     * SOFT References implement equals based on equality of the referent 
     * objects, while the referent is still reachable.  The hashCode
     * implementation is based on the referent implementation of hashCode,
     * while the referent is reachable.
     * </P><P>
     * SOFT References implement Comparable allowing the referent Objects
     * to be compared if they implement Comparable.  If the referent Object
     * doesn't implement Comparable, the hashCode's of the Reference is 
     * compared instead.  If the referent Objects don't implement Comparable,
     * then they shouldn't really be used in sorted collections.
     * </P><P>
     * Garbage collection must be the same as SoftReference.
     * </P>
     * @see SoftReference
     * @see WeakReference
     * @see Comparable
     */
    SOFT,
    /**
     * <P>
     * SOFT_IDENTY References implement equals based on identity == of the
     * referent objects.
     * </P><P>
     * Garbage collection must be the same as SoftReference.
     * </P>
     * @see SoftReference
     */
    SOFT_IDENTITY,
    /**
     * <P>
     * WEAK References implement equals based on equality of the referent 
     * objects, while the referent is still reachable.  The hashCode
     * implementation is based on the referent implementation of hashCode,
     * while the referent is reachable.
     * </P><P>
     * WEAK References implement comparable allowing the referent Objects
     * to be compared if they implement Comparable.  If the referent Object
     * doesn't implement Comparable, the hashCode's of the Reference is 
     * compared instead.  If the referent Object's don't implement Comparable,
     * then they shouldn't really be used in sorted collections.
     * </P><P>
     * Garbage collection must be the same as WeakReference.
     * </P>
     * @see WeakReference
     * @see Comparable
     */
    WEAK,
    /**
     * <P>
     * WEAK_IDENTY References implement equals based on identity == of the
     * referent objects.
     * </P><P>
     * Garbage collection must be the same as WeakReference.
     * </P>
     * @see WeakReference
     */
    WEAK_IDENTITY,
    /**
     * <P>
     * STRONG References implement equals and hashCode() based on the 
     * equality of the underlying Object.
     * </P><P>
     * STRONG References implement Comparable allowing the referent Objects
     * to be compared if they implement Comparable.  If the referent Object
     * doesn't implement Comparable, the hashCode's of the Reference is 
     * compared instead.  If the referent Object's don't implement Comparable,
     * then they shouldn't really be used in sorted collections.
     * </P><P>
     * Garbage collection doesn't occur unless the Reference is cleared.
     * </P>
     * @see Comparable
     */
    STRONG
}
