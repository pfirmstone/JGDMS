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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A plain, immutable, array-backed {@link Map} used by {@link
 * au.net.zeus.jgdms.der.object.ObjectCodec ObjectCodec} to hand a decoded {@code map:}-typed (or
 * {@code orderedmap:}-typed, when the receiving field's declared type is not itself a {@link
 * java.util.SortedMap}/{@link java.util.NavigableMap}) {@code @AtomicSerial} field value to a
 * {@code check(GetArg)} constructor (STD-006 §3.8).
 *
 * <p>Presents its entries in the order they were decoded: for a {@code map:} (canonicalise) field
 * that is the X.690 §11.6 octet-ascending order of the KEY encodings; for an {@code orderedmap:}
 * (preserve) field that is the transmitted (encounter) order.
 *
 * <h2>Passive container -- no validation, no invariants</h2>
 * <p>This class holds decoded entries and does nothing else: no duplicate-key rejection, no null
 * check. {@link #get}/{@link #containsKey} etc. -- inherited from {@link AbstractMap} via {@link
 * #entrySet()} -- perform an ordinary linear scan calling {@code equals()} on keys, but only when
 * a caller invokes them on the already-constructed map; that is ordinary post-construction usage,
 * not construction. (For an {@code orderedmap:} field -- unchecked on the wire -- two entries
 * with {@code .equals()}-equal keys are both retained rather than reconciled; that is out of
 * scope for a passive container and is the receiving {@code check(GetArg)}'s responsibility if it
 * matters. A {@code map:} field cannot have this, because its wire key encodings are already
 * checked strictly ascending at decode.)
 *
 * <h2>Construction is a pure array copy</h2>
 * <p>The constructor performs only {@link List#toArray(Object[])} (a {@code System.arraycopy})
 * over an already-built list of {@link Map.Entry} pairs -- never an insertion into a
 * hash-bucketed structure and never a sort/compare. Zero methods are invoked on the contained
 * keys/values during construction.
 *
 * <h2>Immutability</h2>
 * <p>No mutating method is overridden. {@code AbstractMap.put(K,V)} already throws {@code
 * UnsupportedOperationException} by default, and {@link #entrySet()}'s iterator does not override
 * {@link Iterator#remove()}, so the {@link Iterator} interface's own default {@code remove()}
 * (which throws {@code UnsupportedOperationException}) applies.
 */
public final class ImmutableMap<K, V> extends AbstractMap<K, V> {

    private final Map.Entry<?, ?>[] entries;

    /**
     * Copies {@code source}'s entries into a new backing array via {@link List#toArray(Object[])}
     * -- positional copying only, no {@code hashCode}/{@code equals}/{@code compareTo} call on
     * any key or value.
     *
     * @param source the entries, in the order this map will present them (never {@code null}, but
     *               may be empty); each element is typically an {@link
     *               AbstractMap.SimpleImmutableEntry}
     */
    public ImmutableMap(List<? extends Map.Entry<?, ?>> source) {
        this.entries = source.toArray(new Map.Entry<?, ?>[0]);
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
}
