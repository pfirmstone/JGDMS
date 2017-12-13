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

import java.util.NoSuchElementException;
import java.util.Queue;

/**
 *
 * @param <T> 
 * @author Peter Firmstone.
 */
class ReferencedQueue<T> extends ReferenceCollection<T> implements Queue<T> {
    private final Queue<Referrer<T>> queue;
    
    public ReferencedQueue( Queue<Referrer<T>> queue, Ref type, boolean gcThreads, long gcCycle){
        super(queue, type, gcThreads, gcCycle);
        this.queue = queue;
    }
    
    public boolean offer(T e) {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        return queue.offer(r);
    }

    public T remove() {
        processQueue();
        do {
            Referrer<T> r = queue.remove();
            T t = r == null? null: r.get();
            if (t != null) return t;
        } while (!queue.isEmpty());
        throw new NoSuchElementException("Queue is empty");
    }

    public T poll() {
        processQueue();
        do {
            Referrer<T> r = queue.poll();
            T t = r == null? null: r.get();
            if (t != null) return t;
        } while (!queue.isEmpty());
        return null;
    }

    public T element() {
        processQueue();
        Referrer<T> t = queue.element();
        if ( t != null ) return t.get();
        return null;
    }

    public T peek() {
        processQueue();
        Referrer<T> t = queue.peek();
        if ( t != null ) return t.get();
        return null;
    }

    /**
     * {@inheritDoc}
     * The assumption here is queues do not implement the equals method, and hence do not implemenent hashCode.
     */
    @SuppressWarnings("EmptyMethod")
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * {@inheritDoc}
     * The assumption here is queues do not implement the equals method.
     */
    @SuppressWarnings("EmptyMethod")
    public boolean equals(final Object other) {
        return super.equals(other);
    }
}
