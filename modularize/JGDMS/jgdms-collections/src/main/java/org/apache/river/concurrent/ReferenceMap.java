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
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * ReferenceMap is a wrapper object that encapsulates another Map implementation
 * which it uses to store references.
 * 
 * Synchronisation must be performed by the underlying map, using external
 * synchronisation will fail, since each read is also potentially a 
 * mutation to remove a garbage collected reference.
 * 
 * Implementation note:
 * 
 * ReferenceQueue's must be allowed to be used by any views when creating
 * new References using the wrapper, so the Reference is passed to the
 * correct ReferenceQueue when it becomes unreachable.
 * 
 * There is only one ReferenceQueue for each Collection, whether that is a
 * Collection of Key's or Values.  Defensive copies cannot be made by the
 * underlying encapsulated collections.
 * 
 * Abstract map is extended to take advantage of it's equals, hashCode and 
 * toString methods, which rely on calling entrySet() which this class
 * overrides.
 * 
 * @param <K> 
 * @param <V> 
 * @author Peter Firmstone.
 */
class ReferenceMap<K, V> extends AbstractMap<K, V> implements Map<K, V> {

    private final Map<Referrer<K>, Referrer<V>> map;
    // ReferenceQueuingFactory's handle ReferenceQueue locking policy.
    private final ReferenceQueuingFactory<K, Referrer<K>> krqf;
    private final ReferenceQueuingFactory<V, Referrer<V>> vrqf;
    private final Ref key;
    private final Ref val;
            
    // Views of values and keys, reduces object creation.
    private final Collection<V> values;
    private final Set<K> keys;
    private final Set<Entry<K,V>> entrys;
    
    @SuppressWarnings("unchecked")
    ReferenceMap(Map<Referrer<K>,Referrer<V>> map, Ref key, Ref val, boolean gcThreads, long gcKeyCycle, long gcValCycle){
        // Note the ReferenceProcessor must synchronize on map, not the keySet or values for iteration.
        // This no effect on ConcurrentMap implementations, it's just in case of
        // Synchronized maps.
        RefQueue<K> keyQue = null;
        if (key.equals(Ref.TIME)) keyQue = new TimedRefQueue();
        else if (!key.equals(Ref.STRONG)) keyQue = new RefReferenceQueue<K>();
        RefQueue<V> valQue = null;
        if (val.equals(Ref.TIME)) valQue = new TimedRefQueue();
        else if (!val.equals(Ref.STRONG)) valQue = new RefReferenceQueue<V>();
        ReferenceProcessor<K> krp = new ReferenceProcessor<K>(map.keySet(), key, keyQue, gcThreads, map);
        ReferenceProcessor<V> vrp = new ReferenceProcessor<V>(map.values(), val, valQue, gcThreads, map); 
        this.krqf = krp;
        this.vrqf = vrp; 
        this.map = map;
        this.key = key;
        this.val = val;
        values = new ReferenceCollection<V>(this.map.values(), vrqf, val);
        keys = new ReferenceSet<K>(this.map.keySet(), krqf, key);
        // We let this escape during construction, but it's package private only
        // and doesn't escape the package.
        entrys = new EntrySetFacade<Entry<K,V>, Entry<Referrer<K>,Referrer<V>>>(
                map.entrySet(), 
                new EntryFacadeConverter<K,V>(krqf, vrqf)
                );
        krp.start(gcKeyCycle);
        vrp.start(gcValCycle);
    }
    
    ReferenceMap(Map<Referrer<K>, Referrer<V>> map, ReferenceQueuingFactory<K, Referrer<K>> krqf, ReferenceQueuingFactory<V, Referrer<V>> vrqf, Ref key, Ref val){
        this.map = map;
        this.krqf = krqf;
        this.vrqf = vrqf;
        this.key = key;
        this.val = val;
        values = new ReferenceCollection<V>(this.map.values(), vrqf, val);
        keys = new ReferenceSet<K>(this.map.keySet(), krqf, key);
        // We let this escape during construction, but it's package private only
        // and doesn't escape the package.
        entrys = new EntrySetFacade<Entry<K,V>, Entry<Referrer<K>,Referrer<V>>>(
                map.entrySet(), 
                new EntryFacadeConverter<K,V>(krqf, vrqf)
                );
    }
    

     
    ReferenceQueuingFactory<K, Referrer<K>> getKeyRQF(){
        return krqf;
     }
     
    ReferenceQueuingFactory<V, Referrer<V>> getValRQF(){
        return vrqf;
    }
    
    Ref keyRef() {
        return key;
    }
    
    Ref valRef() {
        return val;
    }
    
    /**
     * Removes all associations from this map.
     */
    public void clear() {
        processQueue();
        map.clear();
    }

    @SuppressWarnings(value = "unchecked")
    public boolean containsKey(Object key) {
        processQueue();
        return map.containsKey(wrapKey((K)key, false, true));
        }

    @SuppressWarnings("unchecked")
    public boolean containsValue(Object value) {
        processQueue();
        return map.containsValue(wrapVal((V) value, false, true));
    }

    /**
     * Returns a Set view of the mappings contained in the underlying map. 
     * 
     * The behaviour of this Set depends on the underlying Map passed in
     * at construction time.
     * 
     * The set supports element removal, which removes the corresponding 
     * mapping from the map, via the Iterator.remove, Set.remove, removeAll, 
     * retainAll and clear operations. 
     * 
     * @return
     */
    public Set<Entry<K,V>> entrySet() {
        return entrys;
    }

    /**
     * Returns value associated with given key, or null if none.
     */
    public V get(Object key) {
        processQueue();
        @SuppressWarnings(value = "unchecked")
        Referrer<V> refVal = map.get(wrapKey((K) key, false, true));
        if (refVal != null) return refVal.get();
        return null;
    }

    public boolean isEmpty() {
        processQueue();
        return map.isEmpty();
    }

    /**
     * The key Set returned, is encapsulated by ReferenceSet, which encapsulates
     * it's objects using the same Ref type as ReferenceMap
     *
     * @see Ref
     * @see ReferenceSet
     * @return
     */
    public Set<K> keySet() {
        processQueue();
        return keys;
    }

    public void processQueue() {
        // If someone else is cleaning out the trash, don't bother waiting,
        // the underlying Map is responsible for it's own synchronization.
        // Null values or keys may be returned as a result.
        // Or a ConcurrentMap that contains a value may no longer contain
        // it after checking.
//        krqf.processQueue();
//        vrqf.processQueue();
        }

    /**
     * Associates value with given key, returning value previously associated
     * with key, or null if none.
     * @param key - Key
     * @param value - Value
     * @return previous value or null
     */
    public V put(K key, V value) {
        processQueue();
        Referrer<V> val = map.put(wrapKey(key, true, false),wrapVal(value, true, false));
        if (val != null) return val.get();
            return null;
        }

    /**
     * Removes association for given key, returning value previously associated
     * with key, or null if none.
     */
    public V remove(Object key) {
        processQueue();
        @SuppressWarnings(value = "unchecked")
        Referrer<V> val = map.remove(wrapKey((K) key, false, true));
        if (val != null) return val.get();
        return null;
    }

    public int size() {
        processQueue();
        return map.size();
    }

    /**
     * Returns collection containing all values currently held in this map.
     */
    public Collection<V> values() {
        processQueue();
        return values;
    }

    Referrer<V> wrapVal(V val, boolean enque, boolean temporary) {
        return vrqf.referenced(val, enque, temporary);
    }

    Referrer<K> wrapKey(K key, boolean enque, boolean temporary) {
        return krqf.referenced(key, enque, temporary);
    }
    
}
