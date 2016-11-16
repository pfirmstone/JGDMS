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

import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;

/**
 *
 * @author peter
 */
abstract class ReadResolveFixForMapCircularReferences<K,V> 
    extends SerializationOfReferenceMap<K,V> 
    implements Map<K,V>, SortedMap<K,V>, NavigableMap<K,V>, ConcurrentMap<K,V>, 
                                                ConcurrentNavigableMap<K,V>{

    // Builder created Map on deserialization
    private volatile Map<K,V> map = null;
    private volatile boolean built = false;
    
    @Override
    public Comparator<? super K> comparator() {
        if (getMap() instanceof SortedMap) return ((SortedMap<K,V>) getMap()).comparator();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public ConcurrentNavigableMap<K, V> subMap(K fromKey, K toKey) {
        if (getMap() instanceof SortedMap) return ((ConcurrentNavigableMap<K,V>) getMap()).subMap(fromKey, toKey);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public ConcurrentNavigableMap<K, V> headMap(K toKey) {
        if (getMap() instanceof SortedMap) return ((ConcurrentNavigableMap<K,V>) getMap()).headMap(toKey);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    public ConcurrentNavigableMap<K, V> tailMap(K fromKey) {
        if (getMap() instanceof SortedMap) return ((ConcurrentNavigableMap<K,V>) getMap()).tailMap(fromKey);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public K firstKey() {
        if (getMap() instanceof SortedMap) return ((SortedMap<K,V>) getMap()).firstKey();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public K lastKey() {
        if (getMap() instanceof SortedMap) return ((SortedMap<K,V>) getMap()).lastKey();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    public NavigableSet<K> keySet() {
        if (getMap() instanceof SortedMap) return ((ConcurrentNavigableMap<K,V>) getMap()).keySet();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public Collection<V> values() {
        if (getMap() instanceof SortedMap) return ((SortedMap<K,V>) getMap()).values();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        if (getMap() instanceof SortedMap) return ((SortedMap<K,V>) getMap()).entrySet();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public int size() {
        return getMap().size();
    }

    @Override
    public boolean isEmpty() {
        return getMap().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return getMap().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return getMap().containsValue(value);
    }

    @Override
    public V get(Object key) {
        return getMap().get(key);
    }

    @Override
    public V put(K key, V value) {
        return getMap().put(key, value);
    }

    @Override
    public V remove(Object key) {
        return getMap().remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        getMap().putAll(m);
    }

    @Override
    public void clear() {
        getMap().clear();
    }

    @Override
    public Entry<K, V> lowerEntry(K key) {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).lowerEntry(key);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public K lowerKey(K key) {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).lowerKey(key);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public Entry<K, V> floorEntry(K key) {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).floorEntry(key);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public K floorKey(K key) {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).floorKey(key);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public Entry<K, V> ceilingEntry(K key) {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).ceilingEntry(key);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public K ceilingKey(K key) {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).ceilingKey(key);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public Entry<K, V> higherEntry(K key) {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).higherEntry(key);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public K higherKey(K key) {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).higherKey(key);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public Entry<K, V> firstEntry() {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).firstEntry();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public Entry<K, V> lastEntry() {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).lastEntry();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public Entry<K, V> pollFirstEntry() {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).pollFirstEntry();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public Entry<K, V> pollLastEntry() {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).pollLastEntry();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public ConcurrentNavigableMap<K, V> descendingMap() {
        if (getMap() instanceof NavigableMap) return ((ConcurrentNavigableMap<K,V>) getMap()).descendingMap();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public NavigableSet<K> navigableKeySet() {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).navigableKeySet();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public NavigableSet<K> descendingKeySet() {
        if (getMap() instanceof NavigableMap) return ((NavigableMap<K,V>) getMap()).descendingKeySet();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public ConcurrentNavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        if (getMap() instanceof NavigableMap) 
            return ((ConcurrentNavigableMap<K,V>) getMap()).subMap(fromKey, fromInclusive, toKey, toInclusive);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public ConcurrentNavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        if (getMap() instanceof NavigableMap) 
            return ((ConcurrentNavigableMap<K,V>) getMap()).headMap(toKey, inclusive);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public ConcurrentNavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        if (getMap() instanceof NavigableMap) 
            return ((ConcurrentNavigableMap<K,V>) getMap()).tailMap(fromKey, inclusive);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public V putIfAbsent(K key, V value) {
        if (getMap() instanceof ConcurrentMap) 
            return ((ConcurrentMap<K,V>) getMap()).putIfAbsent(key, value);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (getMap() instanceof ConcurrentMap) 
            return ((ConcurrentMap<K,V>) getMap()).remove(key, value);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        if (getMap() instanceof ConcurrentMap) 
            return ((ConcurrentMap<K,V>) getMap()).replace(key, oldValue, newValue);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    @Override
    public V replace(K key, V value) {
        if (getMap() instanceof ConcurrentMap) 
            return ((ConcurrentMap<K,V>) getMap()).replace(key, value);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    /**
     * @return the map
     */
    public Map<K,V> getMap() {
        return map;
    }

    /**
     * @return the built
     */
    public boolean isBuilt() {
        return built;
    }

    @Override
    Map<K, V> build() throws InstantiationException, IllegalAccessException, ObjectStreamException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    /**
     * @serialData 
     * @return the type
     */
    abstract Ref getType();

    /**
     * @serialData
     * @return the collection
     */
    abstract Map<Referrer<K>,Referrer<V>> getRefMap();

    /**
     * @serialData
     * @return the class
     */
    abstract Class getClazz();
    
}
