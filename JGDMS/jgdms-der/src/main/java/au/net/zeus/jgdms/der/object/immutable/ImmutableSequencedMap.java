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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

/**
 * An immutable, array-backed {@link SequencedMap} used by {@link
 * au.net.zeus.jgdms.der.object.ObjectCodec ObjectCodec} to hand a decoded
 * {@code orderedmap:}-typed (PRESERVE_ORDERED) map value to a {@code check(GetArg)} constructor
 * when the receiving field's declared type is <b>not</b> itself a {@link java.util.SortedMap}/
 * {@link java.util.NavigableMap} (that case uses {@link ImmutableSortedMap}, which is already a
 * {@code SequencedMap}).
 *
 * <p>The PRESERVE_ORDERED disciplines carry <em>encounter order as part of the value</em>
 * (STD-006 §3.8). Since COLL-2 assigns a decoded {@code orderedmap:} value <b>directly</b> to an
 * interface-declared entry field with no coercion, the returned object must be assignable to a
 * field declared {@link SequencedMap} — so it must implement {@code SequencedMap}, not merely
 * {@link java.util.Map}. This class supplies that shape while the {@code map:} (CANONICALISE)
 * discipline keeps the plain {@link ImmutableMap} (a {@code Map}, not a {@code SequencedMap}).
 *
 * <p>Extends {@link ImmutableMap} so that code (and tests) asserting the general {@code ImmutableMap}
 * shape continue to hold; the only addition is the {@code SequencedMap} contract. Construction,
 * immutability, and the "zero methods invoked on the decoded keys/values during construction"
 * guarantee are all inherited unchanged from {@link ImmutableMap}: the {@code SequencedMap} default
 * mutators ({@code putFirst}/{@code putLast}/{@code pollFirstEntry}/{@code pollLastEntry}) throw
 * {@link UnsupportedOperationException} against this immutable map.
 */
public final class ImmutableSequencedMap<K, V> extends ImmutableMap<K, V> implements SequencedMap<K, V> {

    /**
     * Copies {@code source}'s entries (in encounter order) into a new backing array via the
     * {@link ImmutableMap} constructor — positional copying only, no method invoked on any
     * key or value.
     *
     * @param source the entries in encounter (insertion) order (never {@code null}, may be empty)
     */
    public ImmutableSequencedMap(List<? extends Map.Entry<?, ?>> source) {
        super(source);
    }

    /**
     * A reverse-ordered {@code SequencedMap} snapshot. This map is immutable, so a reversed copy is
     * behaviourally indistinguishable from a reverse view; it is built by positional copy +
     * {@link Collections#reverse} (no method invoked on any key or value).
     */
    @Override
    public SequencedMap<K, V> reversed() {
        List<Map.Entry<K, V>> copy = orderedEntries();
        Collections.reverse(copy);
        return new ImmutableSequencedMap<>(copy);
    }
}
