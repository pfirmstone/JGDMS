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

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 *
 * @param <T> 
 * @author Peter Firmstone.
 */
class ReferenceBlockingQueue<T> extends ReferencedQueue<T> implements BlockingQueue<T> {
    private final BlockingQueue<Referrer<T>> queue;
    
    ReferenceBlockingQueue(BlockingQueue<Referrer<T>> queue, Ref type, boolean gcThreads, long gcCycle){
        super(queue, type, gcThreads, gcCycle);
        this.queue = queue;
    }
    
    public void put(T e) throws InterruptedException {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        queue.put(r);
    }

    public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        return queue.offer(r, timeout, unit);
    }

    public T take() throws InterruptedException {
        processQueue();
        Referrer<T> t = queue.take();
        if ( t != null ) return t.get();
        return null;
    }

    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        processQueue();
        Referrer<T> t = queue.poll(timeout, unit);
        if ( t != null ) return t.get();
        return null;
    }

    public int remainingCapacity() {
        processQueue();
        return queue.remainingCapacity();
    }

    public int drainTo(Collection<? super T> c) {
        processQueue();
        if (c == null) throw new NullPointerException();
        if (c == this) throw new IllegalArgumentException();
        @SuppressWarnings("unchecked")
        Collection<Referrer<T>> dr = new CollectionDecorator<T>( (Collection<T>) c, getRQF(), false, true);
        return queue.drainTo(dr);   
        }

    public int drainTo(Collection<? super T> c, int maxElements) {
        processQueue();
        if (c == null) throw new NullPointerException();
        if (c == this) throw new IllegalArgumentException();
        @SuppressWarnings("unchecked")
        Collection<Referrer<T>> drain = new CollectionDecorator<T>( (Collection<T>) c, getRQF(), false, true);
        return queue.drainTo(drain, maxElements);
        }

    /**
     * {@inheritDoc}
     * The assumption here is blocking queues do not implement the equals method, and hence do not implemenent hashCode.
     */
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * {@inheritDoc}
     * The assumption here is blocking queues do not implement the equals method.
     */
    public boolean equals(final Object other) {
        return super.equals(other);
    }
}
