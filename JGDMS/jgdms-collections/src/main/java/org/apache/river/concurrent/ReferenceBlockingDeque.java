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
import java.util.Collection;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Peter Firmstone.
 */
class ReferenceBlockingDeque<T> extends ReferenceDeque<T> implements BlockingDeque<T>{

    private final BlockingDeque<Referrer<T>> deque;
    
    ReferenceBlockingDeque(BlockingDeque<Referrer<T>> deque, Ref type, boolean gcThreads, long gcCycle){
        super(deque, type, gcThreads, gcCycle);
        this.deque = deque;
    }
    
    public void putFirst(T e) throws InterruptedException {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        deque.putFirst(r);
    }


    public void putLast(T e) throws InterruptedException {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        deque.putLast(r);
    }


    public boolean offerFirst(T e, long timeout, TimeUnit unit) throws InterruptedException {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        return deque.offerFirst(r, timeout, unit);
    }


    public boolean offerLast(T e, long timeout, TimeUnit unit) throws InterruptedException {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        return deque.offerLast(r, timeout, unit);
    }


    public T takeFirst() throws InterruptedException {
        processQueue();
        Referrer<T> t = deque.takeFirst();
        if ( t != null ) return t.get();
        return null;
    }


    public T takeLast() throws InterruptedException {
        processQueue();
        Referrer<T> t = deque.takeLast();
        if ( t != null ) return t.get();
        return null;
    }


    public T pollFirst(long timeout, TimeUnit unit) throws InterruptedException {
        processQueue();
        Referrer<T> t = deque.pollFirst(timeout, unit);
        if ( t != null ) return t.get();
        return null;
    }


    public T pollLast(long timeout, TimeUnit unit) throws InterruptedException {
        processQueue();
        Referrer<T> t = deque.pollLast(timeout, unit);
        if ( t != null ) return t.get();
        return null;
    }


    public void put(T e) throws InterruptedException {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        deque.put(r);
    }


    public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        return deque.offer(r,timeout, unit);
    }


    public T take() throws InterruptedException {
        processQueue();
        Referrer<T> t = deque.take();
        if ( t != null ) return t.get();
        return null;
    }


    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        processQueue();
        Referrer<T> t = deque.poll(timeout, unit);
        if ( t != null ) return t.get();
        return null;
    }


    public int remainingCapacity() {
        return deque.remainingCapacity();
    }


    public int drainTo(Collection<? super T> c) {
        processQueue();
        if (c == null) throw new NullPointerException();
        if (c == this) throw new IllegalArgumentException();
        @SuppressWarnings("unchecked")
        Collection<Referrer<T>> dr = new CollectionDecorator<T>( (Collection<T>) c, getRQF(), false, true);
        return deque.drainTo(dr);
        }


    public int drainTo(Collection<? super T> c, int maxElements) {
        processQueue();
        if (c == null) throw new NullPointerException();
        if (c == this) throw new IllegalArgumentException();
        @SuppressWarnings("unchecked")
        Collection<Referrer<T>> drain = new CollectionDecorator<T>( (Collection<T>) c, getRQF(), false, true);
        return deque.drainTo(drain, maxElements);
        }

    /**
     * {@inheritDoc}
     * The assumption here is blocking deques do not implement the equals method, and hence do not implemenent hashCode.
     */
    @SuppressWarnings("EmptyMethod")
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * {@inheritDoc}
     * The assumption here is blocking deques do not implement the equals method.
     */
    @SuppressWarnings("EmptyMethod")
    public boolean equals(final Object other) {
        return super.equals(other);
    }
}
