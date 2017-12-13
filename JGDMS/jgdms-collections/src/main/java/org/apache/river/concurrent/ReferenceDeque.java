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
import java.util.Deque;
import java.util.Iterator;

/**
 *
 * @author Peter Firmstone
 */
class ReferenceDeque<T> extends ReferencedQueue<T> implements Deque<T>{
    private final Deque<Referrer<T>> deque;
    ReferenceDeque(Deque<Referrer<T>> deque, Ref type, boolean gcThreads, long gcCycle){
        super(deque, type, gcThreads, gcCycle);
        this.deque = deque;
    }
    
    public void addFirst(T e) {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        deque.addFirst(r);
    }

    public void addLast(T e) {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        deque.addLast(r);
    }

    public boolean offerFirst(T e) {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        return deque.offerFirst(r);
    }

    public boolean offerLast(T e) {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        return deque.offerLast(r);
    }

    public T removeFirst() {
        processQueue();
        Referrer<T> t = deque.removeFirst();
        if ( t != null ) return t.get();
        return null;
    }

    public T removeLast() {
        processQueue();
        Referrer<T> t = deque.removeLast();
        if ( t != null ) return t.get();
        return null;
    }

    public T pollFirst() {
        processQueue();
        Referrer<T> t = deque.pollFirst();
        if ( t != null ) return t.get();
        return null;
    }

    public T pollLast() {
        processQueue();
        Referrer<T> t = deque.pollLast();
        if ( t != null ) return t.get();
        return null;
    }

    public T getFirst() {
        processQueue();
        Referrer<T> t = deque.getFirst();
        if ( t != null ) return t.get();
        return null;
    }

    public T getLast() {
        processQueue();
        Referrer<T> t = deque.getLast();
        if ( t != null ) return t.get();
        return null;
    }

    public T peekFirst() {
        processQueue();
        Referrer<T> t = deque.peekFirst();
        if ( t != null ) return t.get();
        return null;
    }

    public T peekLast() {
        processQueue();
        Referrer<T> t = deque.peekLast();
        if ( t != null ) return t.get();
        return null;
    }

    public boolean removeFirstOccurrence(Object o) {
        processQueue();
        @SuppressWarnings("unchecked")
        Referrer<T> r = wrapObj((T) o, false, true);
        return deque.removeFirstOccurrence(r);
    }

    public boolean removeLastOccurrence(Object o) {
        processQueue();
        @SuppressWarnings("unchecked")
        Referrer<T> r = wrapObj((T) o, false, true);
        return deque.removeLastOccurrence(r);
    }

    public void push(T e) {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        deque.push(r);
    }

    public T pop() {
        processQueue();
        Referrer<T> t = deque.pop();
        if ( t != null ) return t.get();
        return null;
    }

    public Iterator<T> descendingIterator() {
        return new ReferenceIterator<T>(deque.descendingIterator());
    }

    /**
     * {@inheritDoc}
     * The assumption here is deques do not implement the equals method, and hence do not implemenent hashCode.
     */
    @SuppressWarnings("EmptyMethod")
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * {@inheritDoc}
     * The assumption here is deques do not implement the equals method.
     */
    @SuppressWarnings("EmptyMethod")
    public boolean equals(final Object other) {
        return super.equals(other);
    }
}
