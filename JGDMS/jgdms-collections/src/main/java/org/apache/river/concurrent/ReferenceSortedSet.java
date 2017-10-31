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

import java.util.Comparator;
import java.util.SortedSet;

/**
 * Referenced set supports sorting Object based on their natural ordering
 * or a Comparator, which must be wrapped in a ReferenceComparator.
 * 
 * @param <T> 
 * @see Comparable
 * @see Comparator
 * @see ReferenceComparator
 * @author Peter Firmstone.
 */
class ReferenceSortedSet<T> extends ReferenceSet<T> implements SortedSet<T> {
    private final SortedSet<Referrer<T>> set;

    ReferenceSortedSet( SortedSet<Referrer<T>> set, Ref type, boolean gcThreads, long gcCycle){
        super(set, type, gcThreads, gcCycle);
        this.set = set;
    }
    
    ReferenceSortedSet(SortedSet<Referrer<T>> set, ReferenceQueuingFactory<T, Referrer<T>> rqf, Ref type){
        super(set, rqf, type);
        this.set = set;
    }
    
    @SuppressWarnings("unchecked")
    public Comparator<? super T> comparator() {
        processQueue();
        Comparator<? super Referrer<T>> c = set.comparator();
        if ( c instanceof ReferenceComparator){
            return ((ReferenceComparator) c).get();
        }
        return null;
    }

    public SortedSet<T> subSet(T fromElement, T toElement) {
        processQueue();
        Referrer<T> from = wrapObj(fromElement, false, true);
        Referrer<T> to = wrapObj(toElement, false, true);
        return new ReferenceSortedSet<T>( set.subSet(from, to), getRQF(), getRef());
    }

    public SortedSet<T> headSet(T toElement) {
        processQueue();
        Referrer<T> to = wrapObj(toElement, false, true);
        return new ReferenceSortedSet<T>(set.headSet(to), getRQF(), getRef());
    }

    public SortedSet<T> tailSet(T fromElement) {
        processQueue();
        Referrer<T> from = wrapObj(fromElement, false, true);
        return new ReferenceSortedSet<T>(set.tailSet(from), getRQF(), getRef());
    }

    public T first() {
        processQueue();
        Referrer<T> t = set.first();
        if ( t != null ) return t.get();
        return null;
    }

    public T last() {
        processQueue();
        Referrer<T> t = set.last();
        if ( t != null ) return t.get();
        return null;
    }

    /**
     * {@inheritDoc}
     * The assumption here is sorted sets do implement the equals method, and hence the parent hashCode is used.
     */
    @SuppressWarnings("EmptyMethod")
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * {@inheritDoc}
     * The assumption here is sorted sets do implement the equals method, and we use the parent implementation here.
     */
    @SuppressWarnings("EmptyMethod")
    public boolean equals(final Object other) {
        return super.equals(other);
    }
}
