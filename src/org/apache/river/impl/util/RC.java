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

/**
 * <p>
 * This class contains a number of static methods for using and abstracting
 * References in Collections.  Interfaces from the Java Collections Framework
 * are supported.
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
 * garbage collection.
 * </p><p>
 * Synchronisation must be implemented by your preferred {@link Collection}
 * and cannot be performed externally to the returned {@link Collection}.  
 * Your chosen underlying {@link Collection} must also be mutable.  
 * Objects will be removed automatically from underlying Collections when 
 * they are eligible for garbage collection, this breaks external synchronisation.
 * {@link CollectionsConcurrent#multiReadCollection(java.util.Collection)} may
 * be useful for synchronising your chosen underlying {@link Collection}, 
 * especially if Objects are not being garbage collected often and writes
 * are minimal. 
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
 * Serialisation is supported, provided it is also supported by underlying
 * collections.  Collections are not defensively copied during de-serialisation,
 * due in part to an inability of determining whether a Comparator is
 * used and in part, that if it is, it prevents Class.newInstance() construction.
 * </p><p>
 * Note that when a collection is first de-serialised, it's contents are
 * strongly referenced, then changed to the correct reference type.  This
 * will still occur, even if the Collection is immutable.
 * </p><p>
 * Map's don't currently support Serialization.
 * </p><p>
 * RC stands for Reference Collection and is abbreviated due to the length of
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
     * @param <T>
     * @param comparator
     * @return
     */
    public static <T> Comparator<Referrer<T>> comparator(Comparator<? super T> comparator){
        return new ReferenceComparator<T>(comparator);
    }
    
    /**
     * Wrap a Collection for holding references so it appears as a Collection
     * containing referents.
     * 
     * @param <T>
     * @param internal
     * @param type
     * @return
     */
    public static <T> Collection<T> collection(Collection<Referrer<T>> internal, Ref type){
        return new ReferenceCollection<T>(internal, type, true);
    }
    
//    /**
//     * The general idea here is, create a factory that produces a the underlying
//     * reference collection, then it can be used again later to defensively
//     * produce a new copy of the original collection after de-serialisation.
//     * 
//     * @param <T>
//     * @param factory
//     * @param type
//     * @return
//     */
//    public static <T> Collection<T> collection(CollectionFactory<Collection<Referrer<T>>> factory, Ref type){
//        return new ReferenceCollection<T>(factory.create(), type);
//    }
            
    /**
     * Wrap a List for holding references so it appears as a List
     * containing referents.
     * 
     * @param <T>
     * @param internal
     * @param type
     * @return
     */
    public static <T> List<T> list(List<Referrer<T>> internal, Ref type){
        return new ReferenceList<T>(internal, type, true);
    }   
    
    /** 
     * Wrap a Set for holding references so it appears as a Set
     * containing referents.
     * 
     * @param <T>
     * @param internal
     * @param type
     * @return
     */
    public static <T> Set<T> set(Set<Referrer<T>> internal, Ref type){
        return new ReferenceSet<T>(internal, type, true);
    }
    /**
     * Wrap a SortedSet for holding references so it appears as a SortedSet
     * containing referents.
     * 
     * @para        m <T>
     * @param internal
     * @param type
     * @return
     */
    public static <T> SortedSet<T> sortedSet(
            SortedSet<Referrer<T>> internal, Ref type){
        return new ReferenceSortedSet<T>(internal, type, true);
    }
    /**
     * Wrap a NavigableSet for holding references so it appears as a NavigableSet
     * containing referents.
     * 
     * @param <T>
     * @param internal
     * @param type
     * @return
     */
    public static <T> NavigableSet<T> navigableSet(
            NavigableSet<Referrer<T>> internal, Ref type){
        return new ReferenceNavigableSet<T>(internal, type, true);
    }
    /**
     * Wrap a Queue for holding references so it appears as a Queue
     * containing referents.
     * 
     * @param <T>
     * @param internal
     * @param type
     * @return
     */
    public static <T> Queue<T> queue(Queue<Referrer<T>> internal, Ref type){
        return new ReferencedQueue<T>(internal, type, true);
    }
    /**
     * Wrap a Deque for holding references so it appears as a Deque
     * containing referents.
     * 
     * @param <T>
     * @param internal
     * @param type
     * @return
     */
    public static <T> Deque<T> deque(Deque<Referrer<T>> internal, Ref type){
        return new ReferenceDeque<T>(internal, type, true);
    }
    /**
     * Wrap a BlockingQueue for holding references so it appears as a BlockingQueue
     * containing referents.
     * 
     * @param <T>
     * @param internal
     * @param type
     * @return
     */
    public static <T> BlockingQueue<T> blockingQueue(
            BlockingQueue<Referrer<T>> internal, Ref type){
        return new ReferenceBlockingQueue<T>(internal, type, true);
    }
    /**
     * Wrap a BlockingDeque for holding references so it appears as a BlockingDeque
     * containing referents.
     * 
     * @param <T>
     * @param internal
     * @param type
     * @return
     */
    public static <T> BlockingDeque<T> blockingDeque(
            BlockingDeque<Referrer<T>> internal, Ref type){
        return new ReferenceBlockingDeque<T>(internal, type, true);
    }
    /**
     * Wrap a Map for holding references so it appears as a Map
     * containing referents.
     * 
     * @param <K>
     * @param <V>
     * @param internal
     * @param key
     * @param value
     * @return
     */
    public static <K, V> Map<K, V> map(
            Map<Referrer<K>, Referrer<V>> internal, Ref key, Ref value){
        return new ReferenceMap<K, V>(internal, key, value, true);
    }
    /**
     * Wrap a SortedMap for holding references so it appears as a SortedMap
     * containing referents.
     * 
     * @param <K>
     * @param <V>
     * @param internal
     * @param key
     * @param value
     * @return
     */
    public static <K, V> SortedMap<K, V> sortedMap(
            SortedMap<Referrer<K>, Referrer<V>> internal, Ref key, Ref value){
        return new ReferenceSortedMap<K, V>(internal, key, value, true);
    }
    /**
     * Wrap a NavigableMap for holding Referrers so it appears as a NavigableMap
     * containing referents.
     * 
     * @param <K>
     * @param <V>
     * @param internal
     * @param key
     * @param value
     * @return
     */
    public static <K, V> NavigableMap<K, V> navigableMap(
            NavigableMap<Referrer<K>, Referrer<V>> internal, Ref key, Ref value){
        return new ReferenceNavigableMap<K, V>(internal, key, value, true);
    }
    /**
     * Wrap a ConcurrentMap for holding references so it appears as a ConcurrentMap
     * containing referents.
     * 
     * @param <K> - key type.
     * @param <V> - value type.
     * @param internal - for holding references.
     * @param key - key reference type.
     * @param value - value reference type.
     * @return
     */
    public static <K, V> ConcurrentMap<K, V> concurrentMap(
            ConcurrentMap<Referrer<K>, Referrer<V>> internal, Ref key, Ref value){
        return new ReferenceConcurrentMap<K, V>(internal, key, value, true);
    }
    
    /**
     * Wrap a ConcurrentNavigableMap for holding references so it appears as a 
     * ConcurrentNavigableMap containing referents.
     * 
     * @param <K>
     * @param <V>
     * @param internal
     * @param key
     * @param value
     * @return
     */
    public static <K, V> ConcurrentNavigableMap<K, V> concurrentNavigableMap(
            ConcurrentNavigableMap<Referrer<K>, Referrer<V>> internal, Ref key, Ref value){
        return new ReferenceConcurrentNavigableMap<K, V>(internal, key, value, true);
    }
}
