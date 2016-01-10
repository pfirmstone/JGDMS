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

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The purpose of this class is to implement all the possible interfaces
 * that subclasses of ReferenceCollection may implement.  This is designed
 * to fix the readResolve issue that occurs in de-serialised object graphs 
 * containing circular references.
 * 
 * @author Peter Firmstone.
 */
abstract class ReadResolveFixCollectionCircularReferences<T> 
extends SerializationOfReferenceCollection<T>
implements List<T>, Set<T>, SortedSet<T>, NavigableSet<T> , 
Queue<T>, Deque<T>, BlockingQueue<T>, BlockingDeque<T>{
   
    // This abstract class must not hold any serial data.
    
    // Builder created List on deserialization
    private volatile Collection<T> serialBuilt = null;
    private volatile boolean built = false;
   
    ReadResolveFixCollectionCircularReferences(){}
    
    @Override
    Collection<T> build() throws InstantiationException, IllegalAccessException,
    ObjectStreamException {
        if (isBuilt()) return getSerialBuilt();
        setBuilt();
        /* Traverse Inheritance heirarchy in reverse order */
        if ( BlockingDeque.class.isAssignableFrom(getClazz()))
            return RC.blockingDeque((BlockingDeque<Referrer<T>>) getCollection(), getType(), 10000L);
        if ( BlockingQueue.class.isAssignableFrom(getClazz()))
            return RC.blockingQueue((BlockingQueue<Referrer<T>>) getCollection(), getType(), 10000L);
        if ( Deque.class.isAssignableFrom(getClazz()))
            return RC.deque((Deque<Referrer<T>>) getCollection(), getType(), 10000L);
        if ( Queue.class.isAssignableFrom(getClazz()))
            return RC.queue((Queue<Referrer<T>>) getCollection(), getType(), 10000L);
        if ( List.class.isAssignableFrom(getClazz()) )
            return RC.list((List<Referrer<T>> ) getCollection(), getType(), 10000L);
        if ( NavigableSet.class.isAssignableFrom(getClazz()) )
            return RC.navigableSet((NavigableSet<Referrer<T>>) getCollection(), getType(), 10000L);
        if ( SortedSet.class.isAssignableFrom(getClazz()) )
            return RC.sortedSet((SortedSet<Referrer<T>>) getCollection(), getType(), 10000L);
        if ( Set.class.isAssignableFrom(getClazz())) 
            return RC.set((Set<Referrer<T>>) getCollection(), getType(), 10000L);
        return RC.collection(getCollection(), getType(), 10000L);
    }
    
    /**
     * @serialData 
     * @return the type
     */
    abstract Ref getType();

    /**
     * @serialData
     * @return the collection
     */
    abstract Collection<Referrer<T>> getCollection();

    /**
     * @serialData
     * @return the class
     */
    abstract Class getClazz();

    /**
     * @return the serialBuilt
     */
    Collection<T> getSerialBuilt() {
        return serialBuilt;
    }

    /**
     * @param serialBuilt the serialBuilt to set
     */
    Collection<T> setSerialBuilt(Collection<T> serialBuilt) {
        this.serialBuilt = serialBuilt;
        return serialBuilt;
    }

    /**
     * @return the built
     */
    boolean isBuilt() {
        return built;
    }

    /**
     * 
     */
    void setBuilt() {
        built = true;
    }

    
        @Override
    public int hashCode() {
        if ( getSerialBuilt() instanceof List || getSerialBuilt() instanceof Set ){
            return getSerialBuilt().hashCode();
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
     * 
     * Unfortunately this object, when behaving as a delegate, is not always 
     * equal to the object it is trying to represent.
     * 
     * @see Collection#equals(java.lang.Object) 
     */
    
    @Override
    public boolean equals(Object o){
        if ( o == this ) return true;
        if ( getSerialBuilt() instanceof List || getSerialBuilt() instanceof Set ){
            return getSerialBuilt().equals(o);
        }
        return false;
    }

    @Override
    public Iterator<T> iterator(){
        if (getSerialBuilt() != null) return getSerialBuilt().iterator();
        return new NullIterator<T>();
    }
    
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(this, Spliterator.ORDERED);
    }
    
    @Override
    public int size() {
        if (getSerialBuilt() != null) return getSerialBuilt().size();
        return 0;
    }
    
    public boolean add(T t){
        if (getSerialBuilt() != null) return getSerialBuilt().add(t);
        return false;
    }
    
    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        in.defaultReadObject();
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    // If deserialized state may have changed since, if another type of
    // Map, apart from ImmutableMap, uses the same builder for example.
    final Object writeReplace() {
        if ( isBuilt()) return getSerialBuilt();
        return this;
    }

    final Object readResolve() throws ObjectStreamException{
        try {
            return setSerialBuilt(build());
        } catch (InstantiationException ex) {
            throw new InvalidClassException(this.getClass().toString(), ex.fillInStackTrace().toString());
        } catch (IllegalAccessException ex) {
            throw new InvalidClassException(this.getClass().toString(), ex.fillInStackTrace().toString());
        }
    }

    
    public boolean addAll(int index, Collection<? extends T> c) {
        if (getSerialBuilt() instanceof List) return ((List<T>) getSerialBuilt()).addAll(index, c);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T get(int index) {
        if (getSerialBuilt() instanceof List) return ((List<T>) getSerialBuilt()).get(index);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T set(int index, T element) {
        if (getSerialBuilt() instanceof List) return ((List<T>) getSerialBuilt()).set(index, element);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public void add(int index, T element) {
        if (getSerialBuilt() instanceof List)((List<T>) getSerialBuilt()).add(index, element);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T remove(int index) {
        if (getSerialBuilt() instanceof List) return ((List<T>) getSerialBuilt()).remove(index);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public int indexOf(Object o) {
        if (getSerialBuilt() instanceof List) return ((List<T>) getSerialBuilt()).indexOf(o);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public int lastIndexOf(Object o) {
        if (getSerialBuilt() instanceof List) return ((List<T>) getSerialBuilt()).lastIndexOf(o);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public ListIterator<T> listIterator() {
        if (getSerialBuilt() instanceof List) return ((List<T>) getSerialBuilt()).listIterator();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public ListIterator<T> listIterator(int index) {
        if (getSerialBuilt() instanceof List) return ((List<T>) getSerialBuilt()).listIterator(index);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public List<T> subList(int fromIndex, int toIndex) {
        if (getSerialBuilt() instanceof List) return ((List<T>) getSerialBuilt()).subList(fromIndex, toIndex);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public Comparator<? super T> comparator() {
        if (getSerialBuilt() instanceof SortedSet) return ((SortedSet<T>) getSerialBuilt()).comparator();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public SortedSet<T> subSet(T fromElement, T toElement) {
        if (getSerialBuilt() instanceof SortedSet) return ((SortedSet<T>) getSerialBuilt()).subSet(fromElement, toElement);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public SortedSet<T> headSet(T toElement) {
        if (getSerialBuilt() instanceof SortedSet) return ((SortedSet<T>) getSerialBuilt()).headSet(toElement);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public SortedSet<T> tailSet(T fromElement) {
        if (getSerialBuilt() instanceof SortedSet) return ((SortedSet<T>) getSerialBuilt()).tailSet(fromElement);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T first() {
        if (getSerialBuilt() instanceof SortedSet) return ((SortedSet<T>) getSerialBuilt()).first();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T last() {
        if (getSerialBuilt() instanceof SortedSet) 
            return ((SortedSet<T>) getSerialBuilt()).last();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T lower(T e) {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).lower(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T floor(T e) {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).floor(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T ceiling(T e) {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).ceiling(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T higher(T e) {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).higher(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T pollFirst() {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).pollFirst();
        if (getSerialBuilt() instanceof Deque) 
            return ((Deque<T>) getSerialBuilt()).pollFirst();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T pollLast() {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).pollLast();
        if (getSerialBuilt() instanceof Deque) 
            return ((Deque<T>) getSerialBuilt()).pollLast();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public NavigableSet<T> descendingSet() {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).descendingSet();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public Iterator<T> descendingIterator() {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).descendingIterator();
        if (getSerialBuilt() instanceof Deque) 
            return ((Deque<T>) getSerialBuilt()).descendingIterator();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).subSet(fromElement, fromInclusive, toElement, toInclusive);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).headSet(toElement, inclusive);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        if (getSerialBuilt() instanceof NavigableSet) 
            return ((NavigableSet<T>) getSerialBuilt()).tailSet(fromElement, inclusive);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public boolean offer(T e) {
        if (getSerialBuilt() instanceof Queue) return ((Queue<T>) getSerialBuilt()).offer(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T remove() {
        if (getSerialBuilt() instanceof Queue) return ((Queue<T>) getSerialBuilt()).remove();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T poll() {
        if (getSerialBuilt() instanceof Queue) return ((Queue<T>) getSerialBuilt()).poll();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T element() {
        if (getSerialBuilt() instanceof Queue) return ((Queue<T>) getSerialBuilt()).element();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T peek() {
        if (getSerialBuilt() instanceof Queue) return ((Queue<T>) getSerialBuilt()).peek();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public void addFirst(T e) {
        if (getSerialBuilt() instanceof Deque) ((Deque<T>) getSerialBuilt()).addFirst(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public void addLast(T e) {
        if (getSerialBuilt() instanceof Deque) ((Deque<T>) getSerialBuilt()).addLast(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public boolean offerFirst(T e) {
        if (getSerialBuilt() instanceof Deque) return ((Deque<T>) getSerialBuilt()).offerFirst(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public boolean offerLast(T e) {
        if (getSerialBuilt() instanceof Deque) return ((Deque<T>) getSerialBuilt()).offerLast(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T removeFirst() {
        if (getSerialBuilt() instanceof Deque) return ((Deque<T>) getSerialBuilt()).removeFirst();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T removeLast() {
        if (getSerialBuilt() instanceof Deque) return ((Deque<T>) getSerialBuilt()).removeLast();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T getFirst() {
        if (getSerialBuilt() instanceof Deque) return ((Deque<T>) getSerialBuilt()).getFirst();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T getLast() {
        if (getSerialBuilt() instanceof Deque) return ((Deque<T>) getSerialBuilt()).getLast();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T peekFirst() {
        if (getSerialBuilt() instanceof Deque) return ((Deque<T>) getSerialBuilt()).peekFirst();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T peekLast() {
        if (getSerialBuilt() instanceof Deque) return ((Deque<T>) getSerialBuilt()).peekLast();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public boolean removeFirstOccurrence(Object o) {
        if (getSerialBuilt() instanceof Deque) 
            return ((Deque<T>) getSerialBuilt()).removeFirstOccurrence(o);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public boolean removeLastOccurrence(Object o) {
        if (getSerialBuilt() instanceof Deque) 
            return ((Deque<T>) getSerialBuilt()).removeLastOccurrence(o);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public void push(T e) {
        if (getSerialBuilt() instanceof Deque)((Deque<T>) getSerialBuilt()).push(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T pop() {
        if (getSerialBuilt() instanceof Deque) return ((Deque<T>) getSerialBuilt()).pop();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public void put(T e) throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingQueue) ((BlockingQueue<T>) getSerialBuilt()).put(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingQueue) 
            return ((BlockingQueue<T>) getSerialBuilt()).offer(e, timeout, unit);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T take() throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingQueue) 
            return ((BlockingQueue<T>) getSerialBuilt()).take();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingQueue) 
            return ((BlockingQueue<T>) getSerialBuilt()).poll(timeout, unit);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public int remainingCapacity() {
        if (getSerialBuilt() instanceof BlockingQueue) 
            return ((BlockingQueue<T>) getSerialBuilt()).remainingCapacity();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public int drainTo(Collection<? super T> c) {
        if (getSerialBuilt() instanceof BlockingQueue) 
            return ((BlockingQueue<T>) getSerialBuilt()).drainTo(c);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public int drainTo(Collection<? super T> c, int maxElements) {
        if (getSerialBuilt() instanceof BlockingQueue) 
            return ((BlockingQueue<T>) getSerialBuilt()).drainTo(c, maxElements);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public void putFirst(T e) throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingDeque) 
            ((BlockingDeque<T>) getSerialBuilt()).putFirst(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public void putLast(T e) throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingDeque) 
            ((BlockingDeque<T>) getSerialBuilt()).putLast(e);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public boolean offerFirst(T e, long timeout, TimeUnit unit) throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingDeque) 
            return ((BlockingDeque<T>) getSerialBuilt()).offerFirst(e, timeout, unit);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public boolean offerLast(T e, long timeout, TimeUnit unit) throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingDeque) 
            return ((BlockingDeque<T>) getSerialBuilt()).offerLast(e, timeout, unit);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T takeFirst() throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingDeque) 
            return ((BlockingDeque<T>) getSerialBuilt()).takeFirst();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T takeLast() throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingDeque) 
            return ((BlockingDeque<T>) getSerialBuilt()).takeLast();
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T pollFirst(long timeout, TimeUnit unit) throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingDeque) 
            return ((BlockingDeque<T>) getSerialBuilt()).pollFirst(timeout, unit);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

    
    public T pollLast(long timeout, TimeUnit unit) throws InterruptedException {
        if (getSerialBuilt() instanceof BlockingDeque) 
            return ((BlockingDeque<T>) getSerialBuilt()).pollLast(timeout, unit);
        throw new UnsupportedOperationException("Unsupported Interface Method.");
    }

 
}
