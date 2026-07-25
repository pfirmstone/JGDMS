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
import java.util.SequencedSet;

/**
 * An immutable, array-backed {@link SequencedSet} used by {@link
 * au.net.zeus.jgdms.der.object.ObjectCodec ObjectCodec} to hand a decoded
 * {@code orderedset:}-typed (PRESERVE_ORDERED) collection value to a {@code check(GetArg)}
 * constructor when the receiving field's declared type is <b>not</b> itself a {@link
 * java.util.SortedSet}/{@link java.util.NavigableSet} (that case uses {@link ImmutableSortedSet},
 * which is already a {@code SequencedSet}).
 *
 * <p>The PRESERVE_ORDERED disciplines carry <em>encounter order as part of the value</em>
 * (STD-006 §3.8). Since COLL-2 assigns a decoded {@code orderedset:} value <b>directly</b> to an
 * interface-declared entry field with no coercion, the returned object must be assignable to a
 * field declared {@link SequencedSet} — so it must implement {@code SequencedSet}, not merely
 * {@link java.util.Set}. This class supplies that shape while the {@code set:} (CANONICALISE)
 * discipline keeps the plain {@link ImmutableSet} (a {@code Set}, not a {@code SequencedSet}).
 *
 * <p>Extends {@link ImmutableSet} so that code (and tests) asserting the general
 * {@code ImmutableSet} shape continue to hold; the only addition is the {@code SequencedSet}
 * contract. Construction, immutability, and the "zero methods invoked on the decoded elements
 * during construction" guarantee are all inherited unchanged from {@link ImmutableSet}: the
 * {@code SequencedSet} default methods ({@code addFirst}/{@code addLast}/{@code removeFirst}/
 * {@code removeLast}) throw {@link UnsupportedOperationException} against this immutable set, and
 * {@code getFirst}/{@code getLast} read through the inherited iterator.
 */
public final class ImmutableSequencedSet<E> extends ImmutableSet<E> implements SequencedSet<E> {

    /**
     * Copies {@code source}'s elements (in encounter order) into a new backing array via the
     * {@link ImmutableSet} constructor — positional copying only, no method invoked on any element.
     *
     * @param source the elements in encounter (insertion) order (never {@code null}, may be empty)
     */
    public ImmutableSequencedSet(List<?> source) {
        super(source);
    }

    /**
     * A reverse-ordered {@code SequencedSet} snapshot. This set is immutable, so a reversed copy is
     * behaviourally indistinguishable from a reverse view; it is built by positional copy +
     * {@link Collections#reverse} (no {@code hashCode}/{@code equals}/{@code compareTo} on any
     * element).
     */
    @Override
    public SequencedSet<E> reversed() {
        List<E> copy = orderedElements();
        Collections.reverse(copy);
        return new ImmutableSequencedSet<>(copy);
    }
}
