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

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.NavigableSet;

/**
 *
 * @param <T> 
 * @author Peter Firmstone.
 */
class ReferenceNavigableSet<T> 
    extends ReferenceSortedSet<T> implements NavigableSet<T> {
    private final NavigableSet<Referrer<T>> set;
    
    public ReferenceNavigableSet(NavigableSet<Referrer<T>> set, Ref type, boolean gcThreads, long gcCycle){
        super(set, type, gcThreads, gcCycle);
        this.set = set;
    }
    
    ReferenceNavigableSet(NavigableSet<Referrer<T>> set, ReferenceQueuingFactory<T, Referrer<T>> rqf, Ref type){
        super(set, rqf, type);
        this.set = set;
    }
    
    public T lower(T e) {
        processQueue();
        Referrer<T> t = set.lower(wrapObj(e, false, true));
        if ( t != null ) return t.get();
        return null;
    }

    public T floor(T e) {
        processQueue();
        Referrer<T> t = set.floor(wrapObj(e, false, true));
        if ( t != null ) return t.get();
        return null;
    }

    public T ceiling(T e) {
        processQueue();
        Referrer<T> t = set.ceiling(wrapObj(e, false, true));
        if ( t != null ) return t.get();
        return null;
    }

    public T higher(T e) {
        processQueue();
        Referrer<T> t = set.higher(wrapObj(e, false, true));
        if ( t != null ) return t.get();
        return null;
    }

    public T pollFirst() {
        processQueue();
        Referrer<T> t = set.pollFirst();
        if ( t != null ) return t.get();
        return null;
    }

    public T pollLast() {
        processQueue();
        Referrer<T> t = set.pollLast();
        if ( t != null ) return t.get();
        return null;
    }

    public NavigableSet<T> descendingSet() {
        processQueue();
        return new ReferenceNavigableSet<T>(set.descendingSet(), getRQF(), null);
    }

    public Iterator<T> descendingIterator() {
        processQueue();
        return new ReferenceIterator<T>(set.descendingIterator());
    }

    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        processQueue();
        return new ReferenceNavigableSet<T>(
            set.subSet(
                wrapObj(fromElement, false, true), 
                fromInclusive, 
                wrapObj(toElement, false, true), 
                toInclusive
            ), getRQF(), getRef());
    }

    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        processQueue();
        return new ReferenceNavigableSet<T>(
                set.headSet(
                    wrapObj(toElement, false, true), inclusive),
                    getRQF(), 
                    getRef()
                );
    }

    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        processQueue();
        return new ReferenceNavigableSet<T>(
                set.tailSet(
                    wrapObj(fromElement, false, true),
                    inclusive), 
                    getRQF(), 
                    getRef()
                );
    }

    /**
     * {@inheritDoc}
     * The assumption here is navigable sets do implement the equals method, and hence the parent hashCode is used.
     */
    @SuppressWarnings("EmptyMethod")
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * {@inheritDoc}
     * The assumption here is navigable sets do implement the equals method, and we use the parent implementation here.
     */
    @SuppressWarnings("EmptyMethod")
    public boolean equals(final Object other) {
        return super.equals(other);
    }
}
