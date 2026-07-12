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

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;

/**
 * A plain, immutable, array-backed {@link SortedSet} used by {@link
 * au.net.zeus.jgdms.der.object.ObjectCodec ObjectCodec} to hand a decoded {@code orderedset:}
 * field's value to a {@code check(GetArg)} constructor, when the receiving field's <em>declared
 * Java type</em> is itself a {@link SortedSet}/{@link java.util.NavigableSet} (STD-006 §3.8's
 * {@code PRESERVE_ORDERED} discipline bundles {@code SortedSet}/{@code NavigableSet} together
 * with {@code LinkedHashSet}/{@code EnumSet} into the same {@code orderedset:} wire token; the
 * wire token alone cannot distinguish them -- see {@code CollectionWireTypes#disciplineFor}).
 *
 * <h2>Trusts the wire order -- never sorts</h2>
 * <p>An {@code orderedset:} field's elements arrive in the sender's iteration order, which for a
 * declared {@code SortedSet} field <em>is</em> the sender's comparator/natural order (that is the
 * whole reason the discipline is PRESERVE, not CANONICALISE: the order is already part of the
 * value, so the encoder just walks the sender's iterator). This class therefore never builds a
 * {@code TreeSet} and never calls {@code compareTo()}/{@code Comparator.compare()} to establish
 * its element order -- it simply trusts that the array it was constructed from is already in
 * ascending order and presents it as-is. This extends, rather than merely relies on, an existing
 * verified security property of this decoder: elements are validated via their own {@code
 * check(GetArg)} constructor before any hash/compare operation might reference them, and this
 * class additionally never performs the compare at all.
 *
 * <h2>Known limitation: natural ordering only</h2>
 * <p>{@link #comparator()} always returns {@code null} (natural ordering). The original sender's
 * {@link Comparator}, if any, is not part of the wire representation and cannot be recovered here;
 * {@link #headSet}/{@link #tailSet}/{@link #subSet} therefore compare elements via {@link
 * Comparable#compareTo} at CALL time (ordinary post-construction usage, not construction -- see
 * class-level note on the no-compare-at-construction guarantee above). A field whose original
 * {@code SortedSet} used a custom {@code Comparator} still round-trips its element order
 * correctly (the order was never re-derived, only preserved), but the metadata methods on the
 * decoded object behave as if the elements were naturally ordered.
 *
 * <h2>Passive container -- no validation, no invariants</h2>
 * <p>Beyond presenting the {@link SortedSet} interface faithfully over the array it was given,
 * this class validates nothing. Duplicate/null/element-type checks are the receiving {@code
 * check(GetArg)}'s responsibility.
 *
 * <h2>Immutability</h2>
 * <p>No mutating method is overridden; {@code AbstractCollection.add(E)} throws {@code
 * UnsupportedOperationException} by default and {@link #iterator()} relies on {@link
 * Iterator#remove()}'s default (throwing) implementation.
 */
public final class ImmutableSortedSet<E> extends AbstractSet<E> implements SortedSet<E> {

    /** Already in ascending order (trusted from the wire; never sorted by this class). */
    private final Object[] elements;

    /**
     * Copies {@code source}'s elements (already in ascending order) into a new backing array via
     * {@link List#toArray()} -- positional copying only, no {@code compareTo}/{@code equals}/
     * {@code hashCode} call on any element.
     *
     * @param source the elements, already in ascending order (never {@code null}, but may be
     *               empty)
     */
    public ImmutableSortedSet(List<?> source) {
        this.elements = source.toArray();
    }

    /** Internal: wraps an already-owned, already-ordered array with no further copy (used by the
     *  {@code headSet}/{@code tailSet}/{@code subSet} views). */
    private ImmutableSortedSet(Object[] elements) {
        this.elements = elements;
    }

    @Override
    public Comparator<? super E> comparator() {
        return null; // natural ordering -- see class Javadoc "known limitation"
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private int idx = 0;

            @Override
            public boolean hasNext() {
                return idx < elements.length;
            }

            @Override
            @SuppressWarnings("unchecked")
            public E next() {
                if (idx >= elements.length) {
                    throw new NoSuchElementException();
                }
                return (E) elements[idx++];
            }
        };
    }

    @Override
    public int size() {
        return elements.length;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E first() {
        if (elements.length == 0) {
            throw new NoSuchElementException();
        }
        return (E) elements[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    public E last() {
        if (elements.length == 0) {
            throw new NoSuchElementException();
        }
        return (E) elements[elements.length - 1];
    }

    /** Natural-order compare, called only at CALL time (never during construction). */
    @SuppressWarnings("unchecked")
    private static int compare(Object a, Object b) {
        return ((Comparable<Object>) a).compareTo(b);
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        int end = 0;
        while (end < elements.length && compare(elements[end], toElement) < 0) {
            end++;
        }
        return new ImmutableSortedSet<>(Arrays.copyOfRange(elements, 0, end));
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        int start = 0;
        while (start < elements.length && compare(elements[start], fromElement) < 0) {
            start++;
        }
        return new ImmutableSortedSet<>(Arrays.copyOfRange(elements, start, elements.length));
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        if (compare(fromElement, toElement) > 0) {
            throw new IllegalArgumentException("fromElement > toElement");
        }
        int start = 0;
        while (start < elements.length && compare(elements[start], fromElement) < 0) {
            start++;
        }
        int end = start;
        while (end < elements.length && compare(elements[end], toElement) < 0) {
            end++;
        }
        return new ImmutableSortedSet<>(Arrays.copyOfRange(elements, start, end));
    }
}
