/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.object.immutable;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;

/**
 * A plain, immutable, array-backed {@link SortedMap} used by {@link
 * au.net.zeus.jgdms.der.object.ObjectCodec ObjectCodec} to hand a decoded {@code orderedmap:}
 * field's value to a {@code check(GetArg)} constructor, when the receiving field's <em>declared
 * Java type</em> is itself a {@link SortedMap}/{@link java.util.NavigableMap} (mirrors {@link
 * ImmutableSortedSet}; see that class's Javadoc for the full rationale -- the same "known
 * limitation: natural ordering only" and "trusts the wire order, never sorts" notes apply here,
 * keyed on the entry KEY rather than a bare element).
 *
 * <h2>Passive container -- no validation, no invariants</h2>
 * <p>Beyond presenting the {@link SortedMap} interface faithfully over the entries it was given,
 * this class validates nothing.
 *
 * <h2>Immutability</h2>
 * <p>No mutating method is overridden; see {@link ImmutableMap}'s Javadoc for the same reasoning
 * ({@code AbstractMap.put} throws by default; the entry iterator relies on {@link
 * Iterator#remove()}'s default throwing implementation).
 */
public final class ImmutableSortedMap<K, V> extends AbstractMap<K, V> implements SortedMap<K, V> {

    /** Already in ascending key order (trusted from the wire; never sorted by this class). */
    private final Map.Entry<?, ?>[] entries;

    /**
     * Copies {@code source}'s entries (already in ascending key order) into a new backing array
     * via {@link List#toArray(Object[])} -- positional copying only, no {@code compareTo}/{@code
     * equals}/{@code hashCode} call on any key or value.
     *
     * @param source the entries, already in ascending key order (never {@code null}, but may be
     *               empty)
     */
    public ImmutableSortedMap(List<? extends Map.Entry<?, ?>> source) {
        this.entries = source.toArray(new Map.Entry<?, ?>[0]);
    }

    /** Internal: wraps an already-owned, already-ordered array with no further copy (used by the
     *  {@code headMap}/{@code tailMap}/{@code subMap} views). */
    private ImmutableSortedMap(Map.Entry<?, ?>[] entries) {
        this.entries = entries;
    }

    @Override
    public Comparator<? super K> comparator() {
        return null; // natural ordering -- see ImmutableSortedSet Javadoc "known limitation"
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new Iterator<Map.Entry<K, V>>() {
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < entries.length;
                }

                @Override
                @SuppressWarnings("unchecked")
                public Map.Entry<K, V> next() {
                    if (idx >= entries.length) {
                        throw new NoSuchElementException();
                    }
                    return (Map.Entry<K, V>) entries[idx++];
                }
            };
        }

        @Override
        public int size() {
            return entries.length;
        }
    }

    /** Natural-order compare, called only at CALL time (never during construction). */
    @SuppressWarnings("unchecked")
    private static int compareKeys(Object a, Object b) {
        return ((Comparable<Object>) a).compareTo(b);
    }

    @Override
    @SuppressWarnings("unchecked")
    public K firstKey() {
        if (entries.length == 0) {
            throw new NoSuchElementException();
        }
        return (K) entries[0].getKey();
    }

    @Override
    @SuppressWarnings("unchecked")
    public K lastKey() {
        if (entries.length == 0) {
            throw new NoSuchElementException();
        }
        return (K) entries[entries.length - 1].getKey();
    }

    @Override
    public SortedMap<K, V> headMap(K toKey) {
        int end = 0;
        while (end < entries.length && compareKeys(entries[end].getKey(), toKey) < 0) {
            end++;
        }
        return new ImmutableSortedMap<>(Arrays.copyOfRange(entries, 0, end));
    }

    @Override
    public SortedMap<K, V> tailMap(K fromKey) {
        int start = 0;
        while (start < entries.length && compareKeys(entries[start].getKey(), fromKey) < 0) {
            start++;
        }
        return new ImmutableSortedMap<>(Arrays.copyOfRange(entries, start, entries.length));
    }

    @Override
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        if (compareKeys(fromKey, toKey) > 0) {
            throw new IllegalArgumentException("fromKey > toKey");
        }
        int start = 0;
        while (start < entries.length && compareKeys(entries[start].getKey(), fromKey) < 0) {
            start++;
        }
        int end = start;
        while (end < entries.length && compareKeys(entries[end].getKey(), toKey) < 0) {
            end++;
        }
        return new ImmutableSortedMap<>(Arrays.copyOfRange(entries, start, end));
    }
}
