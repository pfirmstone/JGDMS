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
import java.util.Comparator;
import java.util.SortedMap;

/**
 *
 * @param <K> 
 * @param <V> 
 * @author peter
 */
class ReferenceSortedMap<K,V> extends ReferenceMap<K,V> implements SortedMap<K,V>{
    private SortedMap<Referrer<K>, Referrer<V>> map;
    
    ReferenceSortedMap(SortedMap<Referrer<K>, Referrer<V>> map, Ref keyRef, Ref valRef, boolean gcThreads, long gcKeyCycle, long gcValCycle){
        super(map, keyRef, valRef, gcThreads, gcKeyCycle, gcValCycle);
        this.map = map;
    }
    
    ReferenceSortedMap(SortedMap<Referrer<K>, Referrer<V>> map, 
            ReferenceQueuingFactory<K, Referrer<K>> krqf,
            ReferenceQueuingFactory<V, Referrer<V>> vrqf, Ref key, Ref val){
        super(map, krqf, vrqf, key, val);
        this.map = map;
    }

    @SuppressWarnings("unchecked")
    public Comparator<? super K> comparator() {
        processQueue();
        Comparator<? super Referrer<K>> c = map.comparator();
        if ( c instanceof ReferenceComparator){
            return ((ReferenceComparator) c).get();
        }
        return null;
    }

    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        processQueue();
        return new ReferenceSortedMap<K,V>(
                map.subMap(wrapKey(fromKey, false, true), wrapKey(toKey, false, true)),
                getKeyRQF(),
                getValRQF(), keyRef(), valRef()
                );
    }


    public SortedMap<K, V> headMap(K toKey) {
        processQueue();
        return new ReferenceSortedMap<K,V>(
                map.headMap(wrapKey(toKey, false, true)),
                getKeyRQF(),
                getValRQF(), keyRef(), valRef()
                );
    }


    public SortedMap<K, V> tailMap(K fromKey) {
        processQueue();
        return new ReferenceSortedMap<K,V>(
                map.tailMap(wrapKey(fromKey, false, true)),
                getKeyRQF(),
                getValRQF(), keyRef(), valRef()
                );
    }


    public K firstKey() {
        processQueue();
        Referrer<K> k = map.firstKey();
        if (k != null) return k.get();
        return null;
    }


    public K lastKey() {
        processQueue();
        Referrer<K> k = map.lastKey();
        if (k != null) return k.get();
        return null;
    }
    
}
