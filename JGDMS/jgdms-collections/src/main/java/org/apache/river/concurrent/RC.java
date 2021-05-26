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

import java.lang.ref.Reference;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ListIterator;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.Iterator;

/**
 * <p>
 * This class contains static methods for decorating collections
 * with reference capability. Classes implementing Interfaces from the Java Collections Framework
 * are supported.  Freshly constructed empty collections are passed to these
 * static methods as parameters and returned decorated with the desired 
 * reference capability.  {@link Referrer} is an interface equivalent to 
 * {@link Reference}, as terms they are used interchangeably.
 * </p><p>
 * Referents in these collections may implement {@link Comparable} or
 * a {@link Comparator} may be used for sorting.  When Comparator's are utilised,
 * they must first be encapsulated {@link RC#comparator(java.util.Comparator) },
 * before passing to a constructor for your preferred underlying Collection
 * implementation.
 * </p><p>
 * {@link Comparable} is not supported for IDENTITY == referenced Collections, 
 * in this case a Comparator must be used.  
 * </p><p>
 * All other references support {@link Comparable}, if the referent Object
 * doesn't implement {@link Comparable}, then {@link Reference#hashCode()} is used
 * for sorting.  If two referent Objects have identical hashCodes,
 * but are unequal and do not implement {@link Comparable}, their references
 * will also have identical hashCodes, so only one of the referents can
 * be added to a {@link SortedSet} or {@link SortedMap}.  This can be fixed by using a
 * {@link Comparator}.
 * </p><p>
 * For all intents and purposes these utilities behave the same as your preferred
 * underlying {@link Collection} implementation, with the exception of 
 * {@link Reference} reachability.  An Object or Key,Value entry is removed 
 * from a {@link Collection} or {@link Map}, upon becoming eligible for 
 * garbage collection. The parameter gcCycle controls how often the underlying
 * collection is cleaned of enqueued references.  TIME references are collected
 * after one gcCycle of no access, shorter cycle times will cause increased 
 * collection and removal of TIME based references, but have no effect on 
 * collection of soft or weak references, only the rate of removal of enqueued
 * references.
 * </p><p>
 * Note that TIME based references with rapid gcCycle's will not scale well.
 * Longer gcCycle's will scale. 
 * </p><p>
 * Synchronisation must be implemented by the encapsulated {@link Collection},
 * removal of enqued references is performed by background Executor threads.
 * Your chosen encapsulated {@link Collection} must also be mutable.  
 * Objects will be removed automatically from encapsulated Collections when 
 * they are eligible for garbage collection, object's that implement AutoCloseable
 * will automatically have their resources freed after removal,
 * external synchronisation of decorated collections is not supported.  
 * </p><p>
 * If you're using Iterators, you must synchronise on the underlying Collection
 * or Map, if iterating through keys or values, this doesn't apply to 
 * concurrent collections that are guaranteed not to throw a 
 * ConcurrentModificationException.
 * </p><p>  
 * An Unmodifiable wrapper {@link Collections#unmodifiableCollection(java.util.Collection)}
 * may be used externally to prevent additions to the underlying Collections,
 * referents will still be removed as they become unreachable however.
 * </p><p>
 * Note that any Sub List, Sub Set or Sub Map obtained by any of the Java
 * Collections Framework interfaces, must be views of the underlying
 * Collection, if the Collection uses defensive copies instead of views, 
 * References could potentially remain in one copy after garbage collection, 
 * causing null returns.  If using standard Java Collections Framework 
 * implementations, these problems don't occur as all Sub Lists, 
 * Sub Sets or Sub Maps are views only.
 * </p><p>
 * {@link Map#entrySet() } view instances returned preserve your chosen reference
 * behaviour, they even support {@link Set#add(java.lang.Object)} or 
 * {@link Set#addAll(java.util.Collection)} methods, although you'll be hard
 * pressed to find a standard java implementation that does.  If you have a
 * Map with a Set of Entry's implementing add, the implementation will need a 
 * Comparator, that compares Entry's only by their keys, to avoid duplicating
 * keys, primarily because an Entry hashCode includes the both key and value in its 
 * calculation. {@link Entry#hashCode() }
 * </p><p>
 * All other {@link Map#entrySet() } methods are fully implemented and supported.
 * </p><p>
 * {@link Entry} view instances returned by these methods preserve reference
 * behaviour, all methods are fully implemented and supported.
 * </p><p>
 * {@link Set} and it's sub interfaces {@link SortedSet} and 
 * {@link NavigableSet}, return views that preserve reference behaviour, 
 * all methods are fully implemented and supported.
 * </p><p>
 * {@link Map} and it's sub interfaces {@link SortedMap}, {@link NavigableMap},
 * {@link ConcurrentMap} and {@link ConcurrentNavigableMap} return
 * views that preserve reference behaviour, all methods are fully implemented 
 * and supported.
 * </p><p>      
 * {@link List} returns views that preserve reference behaviour, all methods are 
 * fully implemented and supported.
 * </p><p>
 * {@link Queue} and it's sub interfaces {@link Deque}, {@link BlockingQueue} and
 * {@link BlockingDeque} return views that preserve reference behaviour, 
 * all methods are fully implemented and supported.
 * </p><p>
 * {@link Iterator} and {@link ListIterator} views preserve reference behaviour, all methods
 * are fully implemented and supported.
 * </p><p>
 * RC stands for Referrer Collection and is abbreviated due to the length of
 * generic parameter arguments typically required.
 * </p>
 * @see Ref
 * @see Referrer
 * @see Reference
 * @author Peter Firmstone.
 */
public class RC {
    private RC(){} // Non instantiable
    
    /**
     * When using a Comparator in SortedSet's and SortedMap's, the Comparator
     * must be encapsulated using this method, to order the Set or Map 
     * by referents and not References.
     * 
     * @param <T> referent type.
     * @param comparator for referents.
     * @return Decorated Comparator for Referrers
     */
    public static <T> Comparator<Referrer<T>> comparator(Comparator<? super T> comparator){
        return new ReferenceComparator<T>(comparator);
    }
    
    /**
     * Decorate a Collection for holding references so it appears as a Collection
     * containing referents.
     * 
     * @param <T> referent type.
     * @param internal Collection for holding Referrer objects.
     * @param type Referrer implementation required.
     * @param gcCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated Collection.
     */
    public static <T> Collection<T> collection(Collection<Referrer<T>> internal, Ref type, long gcCycle){
        return new ReferenceCollection<T>(internal, type, true, gcCycle);
    }
            
    /**
     * Decorate a List for holding references so it appears as a List
     * containing referents.
     * 
     * @param <T> referent type.
     * @param internal List for holding Referrer objects.
     * @param type Referrer implementation required.
     * @param gcCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated List.
     */
    public static <T> List<T> list(List<Referrer<T>> internal, Ref type, long gcCycle){
        return new ReferenceList<T>(internal, type, true, gcCycle);
    }   
    
    /** 
     * Decorate a Set for holding references so it appears as a Set
     * containing referents.
     * 
     * @param <T> referent type.
     * @param internal Set for holding Referrer objects.
     * @param type Referrer implementation required.
     * @param gcCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated Set.
     */
    public static <T> Set<T> set(Set<Referrer<T>> internal, Ref type, long gcCycle){
        return new ReferenceSet<T>(internal, type, true, gcCycle);
    }
    /**
     * Decorate a SortedSet for holding references so it appears as a SortedSet
     * containing referents.
     * 
     * @param <T> referent type.
     * @param internal SortedSet for holding Referrer objects.
     * @param type Referrer implementation required.
     * @param gcCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated SortedSet
     */
    public static <T> SortedSet<T> sortedSet(
            SortedSet<Referrer<T>> internal, Ref type, long gcCycle){
        return new ReferenceSortedSet<T>(internal, type, true, gcCycle);
    }
    /**
     * Decorate a NavigableSet for holding references so it appears as a NavigableSet
     * containing referents.
     * 
     * @param <T> referent type.
     * @param internal NavigableSet for holding Referrer objects.
     * @param type Referrer implementation required.
     * @param gcCycle scheduled cleaning task interval in milliseconds.
     * @return NavigableSet&lt;Referrer&lt;T&gt;&gt; decorated as NavigableSet&lt;T&gt;
     */
    public static <T> NavigableSet<T> navigableSet(
            NavigableSet<Referrer<T>> internal, Ref type, long gcCycle){
        return new ReferenceNavigableSet<T>(internal, type, true, gcCycle);
    }
    /**
     * Decorate a Queue for holding references so it appears as a Queue
     * containing referents.
     * 
     * @param <T> referent type.
     * @param internal Queue for holding Referrer objects.
     * @param type Referrer implementation required.
     * @param gcCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated Queue.
     */
    public static <T> Queue<T> queue(Queue<Referrer<T>> internal, Ref type, long gcCycle){
        return new ReferencedQueue<T>(internal, type, true, gcCycle);
    }
    /**
     * Decorate a Deque for holding references so it appears as a Deque
     * containing referents.
     * 
     * @param <T> referent type.
     * @param internal Deque for holding Referrer objects.
     * @param type Referrer implementation required.
     * @param gcCycle scheduled cleaning task interval in milliseconds.
     * @return Deque&lt;Referrer&lt;T&gt;&gt; decorated as Deque&lt;T&gt;
     */
    public static <T> Deque<T> deque(Deque<Referrer<T>> internal, Ref type, long gcCycle){
        return new ReferenceDeque<T>(internal, type, true, gcCycle);
    }
    /**
     * Decorate a BlockingQueue for holding references so it appears as a BlockingQueue
     * containing referents.
     * 
     * @param <T> referent type.
     * @param internal BlockingQueue for holding Referrer objects.
     * @param type Referrer implementation required.
     * @param gcCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated BlockingQueue
     */
    public static <T> BlockingQueue<T> blockingQueue(
            BlockingQueue<Referrer<T>> internal, Ref type, long gcCycle){
        return new ReferenceBlockingQueue<T>(internal, type, true, gcCycle);
    }
    /**
     * Decorate a BlockingDeque for holding references so it appears as a BlockingDeque
     * containing referents.
     * 
     * @param <T> referent type.
     * @param internal BlockingDeque for holding Referrer objects.
     * @param type Referrer implementation required.
     * @param gcCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated BlockingDeque
     */
    public static <T> BlockingDeque<T> blockingDeque(
            BlockingDeque<Referrer<T>> internal, Ref type, long gcCycle){
        return new ReferenceBlockingDeque<T>(internal, type, true, gcCycle);
    }
    /**
     * Decorate a Map for holding references so it appears as a Map
     * containing referents.
     * 
     * @param <K> key referent type
     * @param <V> value referent type
     * @param internal Map for holding Referrer objects
     * @param key Referrer implementation required, as defined by Ref
     * @param value Referrer implementation required, as defined by Ref
     * @param gcKeyCycle scheduled cleaning task interval in milliseconds.
     * @param gcValCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated Map
     */
    public static <K, V> Map<K, V> map(
            Map<Referrer<K>, Referrer<V>> internal, Ref key, Ref value, long gcKeyCycle, long gcValCycle){
        return new ReferenceMap<K, V>(internal, key, value, true, gcKeyCycle, gcValCycle);
    }
    /**
     * Decorate a SortedMap for holding references so it appears as a SortedMap
     * containing referents.
     * 
     * @param <K> key referent type
     * @param <V> value referent type
     * @param internal SortedMap for holding Referrer objects
     * @param key Referrer implementation required, as defined by Ref
     * @param value Referrer implementation required, as defined by Ref
     * @param gcKeyCycle scheduled cleaning task interval in milliseconds.
     * @param gcValCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated SortedMap
     */
    public static <K, V> SortedMap<K, V> sortedMap(
            SortedMap<Referrer<K>, Referrer<V>> internal, Ref key, Ref value, long gcKeyCycle, long gcValCycle){
        return new ReferenceSortedMap<K, V>(internal, key, value, true, gcKeyCycle, gcValCycle);
    }
    /**
     * Decorate a NavigableMap for holding Referrers so it appears as a NavigableMap
     * containing referents.
     * 
     * @param <K> key referent type
     * @param <V> value referent type
     * @param internal NavigableMap for holding Referrer objects
     * @param key Referrer implementation required, as defined by Ref
     * @param value Referrer implementation required, as defined by Ref
     * @param gcKeyCycle scheduled cleaning task interval in milliseconds.
     * @param gcValCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated NavigableMap
     */
    public static <K, V> NavigableMap<K, V> navigableMap(
            NavigableMap<Referrer<K>, Referrer<V>> internal, Ref key, Ref value, long gcKeyCycle, long gcValCycle){
        return new ReferenceNavigableMap<K, V>(internal, key, value, true, gcKeyCycle, gcValCycle);
    }
    /**
     * Decorate a ConcurrentMap for holding references so it appears as a ConcurrentMap
     * containing referents.
     * 
     * @param <K> - key type.
     * @param <V> - value type.
     * @param internal - for holding references.
     * @param key - key reference type.
     * @param value - value reference type.
     * @param gcKeyCycle scheduled cleaning task interval in milliseconds.
     * @param gcValCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated ConcurrentMap
     */
    public static <K, V> ConcurrentMap<K, V> concurrentMap(
            ConcurrentMap<Referrer<K>, Referrer<V>> internal, Ref key, Ref value, long gcKeyCycle, long gcValCycle){
        return new ReferenceConcurrentMap<K, V>(internal, key, value, true, gcKeyCycle, gcValCycle);
    }
    
    /**
     * Decorate a ConcurrentNavigableMap for holding references so it appears as a 
     * ConcurrentNavigableMap containing referents.
     * 
     * @param <K> key referent type
     * @param <V> value referent type
     * @param internal NavigableMap for holding Referrer objects
     * @param key Referrer implementation required, as defined by Ref
     * @param value Referrer implementation required, as defined by Ref
     * @param gcKeyCycle scheduled cleaning task interval in milliseconds.
     * @param gcValCycle scheduled cleaning task interval in milliseconds.
     * @return Decorated ConcurrentNavigableMap
     */
    public static <K, V> ConcurrentNavigableMap<K, V> concurrentNavigableMap(
            ConcurrentNavigableMap<Referrer<K>, Referrer<V>> internal, Ref key, Ref value, long gcKeyCycle, long gcValCycle){
        return new ReferenceConcurrentNavigableMap<K, V>(internal, key, value, true, gcKeyCycle, gcValCycle);
    }
}
