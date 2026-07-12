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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A plain, immutable, array-backed {@link java.util.Set} used by {@link
 * au.net.zeus.jgdms.der.object.ObjectCodec ObjectCodec} to hand a decoded {@code set:}-typed (or
 * {@code orderedset:}-typed, when the receiving field's declared type is not itself a {@link
 * java.util.SortedSet}/{@link java.util.NavigableSet}) {@code @AtomicSerial} field value to a
 * {@code check(GetArg)} constructor (STD-006 §3.8).
 *
 * <p>Presents its elements in the order they were decoded: for a {@code set:} (canonicalise)
 * field that is the X.690 §11.6 octet-ascending order of the element encodings; for an {@code
 * orderedset:} (preserve) field that is the transmitted (encounter) order -- which, for the
 * original {@code LinkedHashSet}/{@code EnumSet} field this token can represent, equals the
 * insertion / ordinal order that is part of the value. This class does not sort or otherwise
 * reorder its elements.
 *
 * <h2>Passive container -- no validation, no invariants</h2>
 * <p>This class holds decoded elements and does nothing else: no duplicate rejection, no null
 * check, no element-type check. (A {@code set:} field's DER encoding is already order-checked
 * strictly-ascending at decode -- see {@code ObjectCodec.decodeSetOrList} -- which rules out
 * duplicate element *encodings*, but two distinct encodings whose values are nonetheless {@code
 * .equals()} are not reconciled here; that is out of scope for a passive container and is the
 * receiving {@code check(GetArg)}'s responsibility if it matters.)
 *
 * <h2>Construction is a pure array copy</h2>
 * <p>The constructor performs only {@link List#toArray()} (a {@code System.arraycopy}) -- never
 * an insertion into a hash-bucketed structure (which would call {@code hashCode()} per element)
 * and never a sort/compare. Zero methods are invoked on the contained elements during
 * construction. {@link #contains(Object)}, {@link #equals(Object)}, {@link #hashCode()} etc. --
 * inherited from {@link AbstractSet}/{@link java.util.AbstractCollection} -- do call {@code
 * equals()}/{@code hashCode()} on elements, but only when a caller invokes them on the
 * already-constructed set; that is ordinary post-construction usage, not construction.
 *
 * <h2>Immutability</h2>
 * <p>No mutating method is overridden. {@code AbstractCollection.add(E)} already throws {@code
 * UnsupportedOperationException} by default, and this class's {@link #iterator()} does not
 * override {@link Iterator#remove()}, so the {@link Iterator} interface's own default {@code
 * remove()} (which throws {@code UnsupportedOperationException}) applies.
 */
public final class ImmutableSet<E> extends AbstractSet<E> {

    private final Object[] elements;

    /**
     * Copies {@code source}'s elements into a new backing array via {@link List#toArray()} --
     * positional copying only, no {@code hashCode}/{@code equals}/{@code compareTo} call on any
     * element.
     *
     * @param source the elements, in the order this set will present them (never {@code null},
     *               but may be empty)
     */
    public ImmutableSet(List<?> source) {
        this.elements = source.toArray();
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
}
