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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A List implementation that uses References.
 * 
 * This enables a Collection or List to contain, softly, weakly or strongly referenced
 * objects.  Objects that are no longer reachable will simply vanish from 
 * the Collection.
 * 
 * For example, this could be used as an Object cache containing softly reachable
 * objects which are only collected when the jvm is experiencing low memory
 * conditions.
 * 
 * Synchronisation must be performed by the underlying List, it cannot be
 * performed externally, since every read is also potentially a mutable
 * change, caused by removal of garbage collected Objects.
 * 
 * Removal of garbage collected objects is performed before iteration, but not
 * during, instead some Object's returned during iteration may be null.
 * 
 * @author Peter Firmstone.
 */
class ReferenceList<T> extends ReferenceCollection<T> implements List<T> {
    private final List<Referrer<T>> list;
    ReferenceList(List<Referrer<T>> list, Ref type, boolean gcThreads, long gcCycle){
        super(list, type, gcThreads, gcCycle);
        this.list = list;
    }
    
    ReferenceList(List<Referrer<T>> list, ReferenceQueuingFactory<T, Referrer<T>> rqf, Ref type){
        super(list, rqf, type);
        this.list = list;
    }
    
    /**
     * Implemented as per the List interface definition of equals.
     * @see List#equals(java.lang.Object) 
     * @param o
     * @return 
     */
    public boolean equals(Object o){
        if ( o == null ) return false;
        if (!( o instanceof List)) return false;
        List l = (List) o;
        if ( l.size() != size()) return false;
        Iterator<T> li = iterator();
        int i = 0;
        while(li.hasNext()){
            T t = li.next();
            if ( t != null ){
                if ( !(t.equals(l.get(i))) ) return false;
            } else {
                if ( l.get(i) != null ) return false; // both must be null
            }
            i++;
        }
        return true;
    }
    
    /**
     * Implemented as per List interface definition.
     * 
     * @see List#hashCode() 
     * @return 
     */
    public int hashCode() {
        // hash code calculation copied directly from List interface contract.
        int hashCode = 1;
        Iterator<T> i = iterator();
        while (i.hasNext()) {
            T obj = i.next();
            hashCode = 31*hashCode + (obj==null ? 0 : obj.hashCode());
        }
        return hashCode;
    }

    @SuppressWarnings("unchecked")
    public boolean addAll(int index, Collection<? extends T> c) {
        processQueue();
        return list.addAll(index, new CollectionDecorator<T>((Collection<T>) c, getRQF(), true, false));
    }

    public T get(int index) {
        processQueue();
        Referrer<T> r = list.get(index);
        if (r != null) return r.get();
        return null;
    }

    public T set(int index, T element) {
        processQueue();
        Referrer<T> r = list.set(index, wrapObj(element, true, false));
        if (r != null) return r.get();
        return null;
    }

    public void add(int index, T element) {
        processQueue();
        list.add(index, wrapObj(element, true, false));
    }

    public T remove(int index) {
        processQueue();
        Referrer<T> r = list.remove(index);
        if (r != null) return r.get();
        return null;
    }

    @SuppressWarnings("unchecked")
    public int indexOf(Object o) {
        processQueue();
        return list.indexOf(wrapObj((T) o, false, true));
    }

    @SuppressWarnings("unchecked")
    public int lastIndexOf(Object o) {
        processQueue();
        return list.lastIndexOf(wrapObj((T)o, false, true));
    }

    public ListIterator<T> listIterator() {
        processQueue();
        return new ReferenceListIterator<T>(list.listIterator(), getRQF());
    }

    public ListIterator<T> listIterator(int index) {
        processQueue();
        return new ReferenceListIterator<T>(list.listIterator(index), getRQF());
    }

    public List<T> subList(int fromIndex, int toIndex) {
        processQueue();
        List<T> sub = new ReferenceList<T>(list.subList(fromIndex, toIndex), getRQF(), null);
        return sub;
    }
    
    private  class ReferenceListIterator<T> implements ListIterator<T>{
        ListIterator<Referrer<T>> iterator;
        ReferenceQueuingFactory<T, Referrer<T>> rqf;
        private ReferenceListIterator(ListIterator<Referrer<T>> iterator, ReferenceQueuingFactory<T, Referrer<T>> rqf ){
            if ( iterator == null || rqf == null ) throw 
            new NullPointerException("Null iterator or reference queuing factory not allowed");
            this.iterator = iterator;
            this.rqf = rqf;
        }

        public boolean hasNext() {
            return iterator.hasNext();
        }

        public T next() {
            Referrer<T> t = iterator.next();
            if ( t != null ) return t.get();
            return null;
        }

        public boolean hasPrevious() {
            return iterator.hasPrevious();
        }

        public T previous() {
            Referrer<T> t = iterator.previous();
            if ( t != null ) return t.get();
            return null;
        }

        public int nextIndex() {
            return iterator.nextIndex();
        }

        public int previousIndex() {
            return iterator.previousIndex();
        }
        
        public void remove() {
            iterator.remove();
        }

        public void set(T e) {
            iterator.set(rqf.referenced(e, true, false));
        }

        public void add(T e) {
            iterator.add(rqf.referenced( e, true, false));
        }       
    }
}
