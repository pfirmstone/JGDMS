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

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * A plain, immutable, array-backed {@link List} used by {@link
 * au.net.zeus.jgdms.der.object.ObjectCodec ObjectCodec} to hand a decoded {@code list:}/{@code
 * bag:}-typed {@code @AtomicSerial} field value to a {@code check(GetArg)} constructor
 * (STD-006 §3.8; {@code AtomicSerial.GetArg} contract: collection fields are "replaced ... by a
 * safe limited functionality immutable Collection instance").
 *
 * <h2>Passive container -- no validation, no invariants</h2>
 * <p>This class holds decoded elements and does nothing else. It performs <b>no</b> validation of
 * its own (duplicate checking, null checking, element-type checking, ...): that is entirely the
 * responsibility of whatever {@code check(GetArg)} method receives this collection via {@code
 * GetArg.get(name, val, List.class)} (or {@code Collection.class}).
 *
 * <h2>Construction is a pure array copy</h2>
 * <p>The constructor performs only {@link List#toArray()} (a {@code System.arraycopy}), never an
 * insertion into a hash-bucketed structure ({@code HashSet}/{@code HashMap}-style, which calls
 * {@code hashCode()} per element) and never a sort/compare ({@code TreeSet}/{@code TreeMap}-style,
 * which calls {@code compareTo()}/{@code Comparator.compare()} per element). Zero methods are
 * invoked on the contained elements during construction.
 *
 * <h2>Immutability</h2>
 * <p>No mutating method is overridden. {@link AbstractList}'s own {@code add}/{@code set}/{@code
 * remove} already throw {@code UnsupportedOperationException} by default, and this class's {@link
 * #iterator()} is {@link AbstractList}'s own array-index-driven iterator, whose {@code remove()}
 * likewise delegates to (and is rejected by) {@link AbstractList#remove(int)}.
 */
public final class ImmutableList<E> extends AbstractList<E> implements RandomAccess {

    private final Object[] elements;

    /**
     * Copies {@code source}'s elements into a new backing array via {@link List#toArray()} --
     * positional copying only, no {@code hashCode}/{@code equals}/{@code compareTo} call on any
     * element.
     *
     * @param source the elements, in the order this list will present them (never {@code null},
     *               but may be empty)
     */
    public ImmutableList(List<?> source) {
        this.elements = source.toArray();
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
        return (E) elements[index];
    }

    @Override
    public int size() {
        return elements.length;
    }
}
