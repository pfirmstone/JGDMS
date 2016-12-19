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
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A referenced hash map, that encapsulates and utilises any ConcurrentMap
 * implementation passed in at construction.
 * 
 * Based on any ConcurrentMap implementation, it doesn't accept null keys or values.
 *
 * It is recommended although not mandatory to use identity based References for keys,
 * unexpected results occur when relying on equal keys, if one key is no longer 
 * strongly reachable and has been garbage collected and removed from the 
 * Map.
 * 
 * 
 * 
 * If either a key or value, is no longer strongly reachable, their mapping
 * will be queued for removal and garbage collection, in compliance with
 * the Reference implementation selected.
 *
 * @param <K> 
 * @param <V> 
 * @see Ref
 * @author Peter Firmstone.
 *
 * @since 2.3
 */
class ReferenceConcurrentMap<K, V> extends ReferenceMap<K, V> implements ConcurrentMap<K, V> {

    // ConcurrentMap must be protected from null values?  It changes it's behaviour, is that a problem?
    private final ConcurrentMap<Referrer<K>, Referrer<V>> map;
    
    ReferenceConcurrentMap(ConcurrentMap<Referrer<K>,Referrer<V>> map, Ref key, Ref val, boolean gcThreads, long gcKeyCycle, long gcValCycle){
        super (map, key, val, gcThreads, gcKeyCycle, gcValCycle);
        this.map = map;
    }
    
    ReferenceConcurrentMap(ConcurrentMap<Referrer<K>, Referrer<V>> map,
            ReferenceQueuingFactory<K, Referrer<K>> krqf, ReferenceQueuingFactory<V, Referrer<V>> vrqf, Ref key, Ref val){
        super(map, krqf, vrqf, key, val);
        this.map = map;
    }
    
    public V putIfAbsent(K key, V value) {
        processQueue();  //may be a slight delay before atomic putIfAbsent
        Referrer<K> k = wrapKey(key, true, false);
        Referrer<V> v = wrapVal(value, true, false);
        Referrer<V> val = map.putIfAbsent(k, v);
        while ( val != null ) {
            V existed = val.get();
            // We hold a strong reference to value, so 
            if ( existed == null ){
                // stale reference must be replaced, it has been garbage collect but hasn't 
                // been removed, we must treat it like the entry doesn't exist.
                if ( map.replace(k, val, v)){
                    // replace successful
                    return null; // Because officially there was no record.
                } else {
                    // Another thread may have replaced it.
                    val = map.putIfAbsent(k, v);
                }
            } else {
                return existed;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean remove(Object key, Object value) {
        processQueue();
        return map.remove(wrapKey((K) key, false, true), wrapVal((V) value, false, true));
    }

    public boolean replace(K key, V oldValue, V newValue) {
        processQueue();
        return map.replace(wrapKey(key, false, true), wrapVal(oldValue, false, true), wrapVal(newValue, true, false));
    }

    public V replace(K key, V value) {
        processQueue();
        Referrer<V> val = map.replace(wrapKey(key, false, true), wrapVal(value, true, false));
        if ( val != null ) return val.get();
        return null;
    }
}
