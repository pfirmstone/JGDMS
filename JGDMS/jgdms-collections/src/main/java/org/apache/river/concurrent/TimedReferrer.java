/*
 * Copyright 2012 Zeus Project Services Pty Ltd.
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

import java.util.concurrent.Future;

/**
 * 
 * @author Peter Firmstone.
 */
class TimedReferrer<T> implements UntouchableReferrer<T>, TimeBomb {
    
    private volatile long clock;
    private volatile long read;
    private final TimedRefQueue queue;
    private final T referent;
    private volatile boolean enqued;
    private final Object lock;
    
    TimedReferrer(T k, TimedRefQueue q){
        long time = System.nanoTime();
        clock = time;
        read = time;
        referent = k;
        queue = q;
        enqued = false;
        lock = new Object();
    }

    public T get() {
        // Doesn't need to be atomic.
        if (read < clock) read = clock; //Avoid unnecessary volatile write.
        return referent;
    }

    public void clear() {
        // Does nothing.
//        referent = null;
    }

    public boolean isEnqueued() {
        return enqued;
    }

    public boolean enqueue() {
        if (enqued) return false;
//        if (referent == null) return false;
        if (queue == null) return false;
        synchronized (lock){ // Sync for atomic write of enqued.
            if (enqued) return false;
            enqued = queue.offer(this);
        }
        return enqued;
    }
    
    @Override
    public void updateClock(long time){
        if (read < clock) { // only write volatile if necessary.
            if (referent instanceof Future) ((Future)referent).cancel(false);
            enqueue();
            // Don't clear, it will be removed soon anyway, prevents 
            // non empty Queue.poll() returning null.
            //clear();
        } else {
            clock = time;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o)  return true; // Same reference.
        if (!(o instanceof Referrer))  return false;
        Object k1 = get(); //call get(), so equals updates clock for key's in a hash map.
        Object k2 =((Referrer) o).get();
        if ( k1 != null && k1.equals(k2)) return true;
        return ( k1 == null && k2 == null && hashCode() == o.hashCode()); // Both objects were collected.
    }

    @Override
    public int hashCode() {
        Object k = referent; //don't call get(), avoid read update.
        int hash = 7;
        hash = 29 * hash + k.hashCode();
        hash = 29 * hash + k.getClass().hashCode();
        return hash;
    }
    
    @Override
    public String toString(){
        Object s = get();
        if (s != null) return s.toString();
        return super.toString();
    }

    public T lookDontTouch() {
        return referent;
    }
    
}
