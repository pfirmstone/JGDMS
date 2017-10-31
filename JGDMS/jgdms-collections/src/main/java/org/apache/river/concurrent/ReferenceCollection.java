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

import java.util.*;

/**
 * A Collection of Reference Objects, the developer may chose any Collection
 * implementation to store the References, which is passed in a runtime.
 * 
 * The underlying Collection implementation governs the specific behaviour of this
 * Collection.
 * 
 * Synchronisation must be implemented by the underlying Collection and cannot
 * be performed externally to this class.  The underlying Collection must
 * also be mutable.  Objects will be removed automatically from the underlying
 * Collection when they are eligible for garbage collection.
 * 
 * Weak, Weak Identity, Soft, Soft Identity or Strong references may be used.
 * This Collection may be used as an Object pool cache or any other purpose 
 * that requires unique memory handling.
 * 
 * For concurrent threads, it is recommended to encapsulate the underlying
 * collection in a multi read, single write collection for scalability.
 * 
 * @see Ref
 * @author Peter Firmstone.
 */
class ReferenceCollection<T> extends AbstractCollection<T> 
                                implements Collection<T> {
    private final Collection<Referrer<T>> col;
    private final ReferenceQueuingFactory<T, Referrer<T>> rqf;
    private final Ref type;
    
    @SuppressWarnings("unchecked")
    ReferenceCollection(Collection<Referrer<T>> col, Ref type, boolean gcThread, long gcCycle){
        RefQueue<T> que = null;
        if (type == Ref.TIME) que = new TimedRefQueue();
        else if (type != Ref.STRONG) que = new RefReferenceQueue<T>();
        this.col = col;
        ReferenceProcessor<T> rp = new ReferenceProcessor<T>(col, type, que, gcThread, col);
        this.type = type;
        rqf = rp;
        rp.start(gcCycle);
    }
    
    ReferenceCollection(Collection<Referrer<T>> col, 
            ReferenceQueuingFactory<T, Referrer<T>> rqf, Ref type){
        this.col = col;
        this.rqf = rqf;
        this.type = type;
    }
    
    void processQueue(){
        //rqf.processQueue();
        }
    
    ReferenceQueuingFactory<T, Referrer<T>> getRQF(){
        return rqf;
    }
    
    Ref getRef(){
        return type;
    }
    
    Referrer<T> wrapObj(T t, boolean enqueue, boolean temporary){
        return rqf.referenced(t, enqueue, temporary);
    }
    
    public int size() {
        processQueue();
        return col.size();
    }

    public boolean isEmpty() {
        processQueue();
        return col.isEmpty();
    }

    public boolean contains(Object o) {
        processQueue();
        return col.contains(wrapObj((T) o, false, true));
    }
    
    /**
     * This Iterator may return null values if garbage collection
     * runs during iteration.
     * 
     * Always check for null values.
     * 
     * @return T - possibly null.
     */
    public Iterator<T> iterator() {
        processQueue();
        return new ReferenceIterator<T>(col.iterator());
    }

    public boolean add(T e) {
        processQueue();
        return col.add(wrapObj(e, true, false));
    }

    public boolean remove(Object o) {
        processQueue();
        return col.remove(wrapObj((T) o, false, true));
    }

 
    @SuppressWarnings("unchecked")
    public boolean containsAll(Collection<?> c) {
        processQueue();
        return col.containsAll(new CollectionDecorator<T>((Collection<T>) c, getRQF(), false, true));
    }

    
    @SuppressWarnings("unchecked")
    public boolean addAll(Collection<? extends T> c) {
        processQueue();
        return col.addAll(new CollectionDecorator<T>((Collection<T>) c, getRQF(), true, false));
    }

    public void clear() {
        col.clear();
    }
    
    /*
     * The next three methods are suitable implementations for subclasses also.
     */
    public String toString(){
        return col.toString();
    }

    @Override
    public int hashCode() {
        if ( col instanceof List || col instanceof Set ){
            return col.hashCode();
        }
        return System.identityHashCode(this);
    }
    
    /**
     * Because equals and hashCode are not defined for collections, we 
     * cannot guarantee consistent behaviour by implementing equals and
     * hashCode.  A collection could be a list, set, queue or deque.
     * So a List != Queue and a Set != list. therefore equals for collections is
     * not defined.
     * 
     * However since two collections may both also be Lists, while abstracted
     * from the client two lists may still be equal.
     * @see Collection#equals(java.lang.Object) 
     */
    
    @Override
    public boolean equals(Object o){
        if ( o == this ) return true;
        if ( col instanceof List || col instanceof Set ){
            return col.equals(o);
        }
        return false;
    }
}
