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

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;

/**
 *
 * @author Peter Firmstone.
 */
class ReferenceNavigableMap<K,V> extends ReferenceSortedMap<K,V> implements NavigableMap<K,V> {
    private final NavigableMap<Referrer<K>, Referrer<V>> map;
    
    ReferenceNavigableMap(NavigableMap<Referrer<K>, Referrer<V>> map, Ref keyRef, Ref valRef, boolean gcThreads, long gcKeyCycle, long gcValCycle){
        super(map, keyRef, valRef, gcThreads, gcKeyCycle, gcValCycle);
        this.map = map;
    }
    
    ReferenceNavigableMap(NavigableMap<Referrer<K>, Referrer<V>> map,
            ReferenceQueuingFactory<K, Referrer<K>> krqf,
            ReferenceQueuingFactory<V, Referrer<V>> vrqf, Ref key, Ref val){
        super(map, krqf, vrqf, key, val);
        this.map = map;
    }

    public Entry<K, V> lowerEntry(K key) {
        processQueue();
        return new ReferenceEntryFacade<K,V>(
            map.lowerEntry(wrapKey(key, false, true)),
            getValRQF()
        );
    }

    public K lowerKey(K key) {
        processQueue();
        Referrer<K> k = map.lowerKey(wrapKey(key, false, true));
        if (k != null) return k.get();
        return null;
    }

    public Entry<K, V> floorEntry(K key) {
        processQueue();
        return new ReferenceEntryFacade<K,V>(
            map.floorEntry(wrapKey(key, false, true)),
            getValRQF()
        );
    }

    public K floorKey(K key) {
        processQueue();
        Referrer<K> k = map.floorKey(wrapKey(key, false, true));
        if (k != null) return k.get();
        return null;
    }

    public Entry<K, V> ceilingEntry(K key) {
        processQueue();
        return new ReferenceEntryFacade<K,V>(
            map.ceilingEntry(wrapKey(key, false, true)),
            getValRQF()
        );
    }

    public K ceilingKey(K key) {
        processQueue();
        Referrer<K> k = map.ceilingKey(wrapKey(key, false, true));
        if (k != null) return k.get();
        return null;
    }

    public Entry<K, V> higherEntry(K key) {
        processQueue();
        return new ReferenceEntryFacade<K,V>(
            map.higherEntry(wrapKey(key, false, true)),
            getValRQF()
        );
    }

    public K higherKey(K key) {
        processQueue();
        Referrer<K> k = map.higherKey(wrapKey(key, false, true));
        if (k != null) return k.get();
        return null;
    }

    public Entry<K, V> firstEntry() {
        processQueue();
        return new ReferenceEntryFacade<K,V>(
            map.firstEntry(),
            getValRQF()
        );
    }

    public Entry<K, V> lastEntry() {
        processQueue();
        return new ReferenceEntryFacade<K,V>(
            map.lastEntry(),
            getValRQF()
        );
    }

    public Entry<K, V> pollFirstEntry() {
        processQueue();
        return new ReferenceEntryFacade<K,V>(
            map.pollFirstEntry(),
            getValRQF()
        );
    }

    public Entry<K, V> pollLastEntry() {
        processQueue();
        return new ReferenceEntryFacade<K,V>(
            map.pollLastEntry(),
            getValRQF()
        );
    }

    public NavigableMap<K, V> descendingMap() {
        processQueue();
        return new ReferenceNavigableMap<K,V>(
                map.descendingMap(),
                getKeyRQF(),
                getValRQF(), keyRef(), valRef()
                );
    }

    public NavigableSet<K> navigableKeySet() {
        processQueue();
        return new ReferenceNavigableSet<K>(map.navigableKeySet(), getKeyRQF(), keyRef());
    }

    public NavigableSet<K> descendingKeySet() {
        processQueue();
        return new ReferenceNavigableSet<K>(map.descendingKeySet(), getKeyRQF(), keyRef());
    }

    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        processQueue();
        return new ReferenceNavigableMap<K,V>(
            map.subMap(
                wrapKey(fromKey, false, true),
                fromInclusive, 
                wrapKey(toKey, false, true), 
                toInclusive
            ),
            getKeyRQF(),
            getValRQF(), keyRef(), valRef()
        );

    }

    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        processQueue();
        return new ReferenceNavigableMap<K,V>(
            map.headMap(wrapKey(toKey, false, true),inclusive),
            getKeyRQF(),
            getValRQF(), keyRef(), valRef()
        );
    }

    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        processQueue();
        return new ReferenceNavigableMap<K,V>(
            map.tailMap(wrapKey(fromKey, false, true),inclusive),
            getKeyRQF(),
            getValRQF(), keyRef(), valRef()
        );
    }
}
