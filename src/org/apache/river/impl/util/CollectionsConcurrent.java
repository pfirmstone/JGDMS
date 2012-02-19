/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.impl.util;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @author Peter Firmstone.
 */
public class CollectionsConcurrent {
    // Not instantiable
    private CollectionsConcurrent() {}

    public static <T> Set<T> multiReadSet(Set<T> s) {
	return new MultiReadSet<T>(s);
    }
    
    public static <T> Collection<T> multiReadCollection(Collection<T> c){
	return new MultiReadCollection<T>(c);
    }
    
    static class MultiReadCollection<E> implements Collection<E>, Serializable {
	private static final long serialVersionUID = 1L;

	final Collection<E> c;  // Backing Collection
	final Object mutex;     // Object on which to synchronize
	final ReadWriteLock rwlock;
	final Lock rl;
	final Lock wl;

	MultiReadCollection(Collection<E> c) {
            if (c==null) {
		throw new NullPointerException();
	    }
	    this.c = c;
            mutex = this;
	    rwlock = new ReentrantReadWriteLock();
	    rl = rwlock.readLock();
	    wl = rwlock.writeLock();
        }

	Lock getReadLock(){
	    return rl;
	}
	
	Lock getWriteLock(){
	    return rl;
	}
	
	public int size() {
	    rl.lock();
	    try {
		return c.size();
	    }
	    finally {
		rl.unlock();
	    }
        }
	public boolean isEmpty() {
	    rl.lock();
	    try {
		return c.isEmpty();
	    }
	    finally {
		rl.unlock();
	    }
        }
	public boolean contains(Object o) {
	    rl.lock();
	    try {
		return c.contains(o);
	    }
	    finally {
		rl.unlock();
	    }
        }
	public Object[] toArray() {
	    rl.lock();
	    try {
		return c.toArray();
	    }
	    finally {
		rl.unlock();
	    }
        }
	public <T> T[] toArray(T[] a) {
	    rl.lock();
	    try {
		return c.toArray(a);
	    }
	    finally {
		rl.unlock();
	    }
        }

	public Iterator<E> iterator() {
	    rl.lock();
	    try {
		return new CollectionIterator<E>(this);
	    }
	    finally {
		rl.unlock();
	    }
        }

	public boolean add(E e) {
	    wl.lock();
	    try{
		return c.add(e);
	    }finally{
		wl.unlock();
	    }
        }
	public boolean remove(Object o) {
	    wl.lock();
	    try{
		return c.remove(o);
	    }finally{
		wl.unlock();
	    }
        }

	public boolean containsAll(Collection<?> coll) {
	    rl.lock();
	    try{
		return c.containsAll(coll);
	    }finally{
		rl.unlock();
	    }
        }
	public boolean addAll(Collection<? extends E> coll) {
	    wl.lock();
	    try{
		return c.addAll(coll);
	    }finally{
		wl.unlock();
	    }
        }
	public boolean removeAll(Collection<?> coll) {
	    wl.lock();
	    try{
		return c.removeAll(coll);
	    }finally{
		wl.unlock();
	    }
        }
	public boolean retainAll(Collection<?> coll) {
	    wl.lock();
	    try{
		return c.retainAll(coll);
	    }finally{
		wl.unlock();
	    }
        }
	public void clear() {
	    wl.lock();
	    try{
		c.clear();
	    }finally{
		wl.unlock();
	    }
        }
	@Override
	public String toString() {
	    wl.lock();
	    try{
		return c.toString();
	    }finally{
		wl.unlock();
	    }
        }
        private void writeObject(ObjectOutputStream s) throws IOException {
	    rl.lock();
	    try{
		s.defaultWriteObject();
	    }finally{
		rl.unlock();
	    }
        }
    }
    
    static class CollectionIterator<E> implements Iterator<E> {
	final MultiReadCollection col;
	final Iterator<E> iter;
	volatile E element;
	CollectionIterator(MultiReadCollection<E> c){
	    col = c;
	    Collection<E> copy = new ArrayList<E>(c.size());
	    copy.addAll(c);
	    iter = copy.iterator();   
	}

	public boolean hasNext() {
		return iter.hasNext();
	}

	public E next() {
		element = iter.next();
		return element;
	}

	public void remove() {
	    col.remove(element);
	}
	
    }
    
       static class MultiReadSet<E>
	  extends MultiReadCollection<E>
	  implements Set<E> {
	private static final long serialVersionUID = 1L;

	MultiReadSet(Set<E> s) {
            super(s);
        }

	@Override
	public boolean equals(Object o) {
	    rl.lock();
	    try{
		return c.equals(o);
	    }finally{
		rl.unlock();
	    }
        }
	@Override
	public int hashCode() {
	    rl.lock();
	    try{
		return c.hashCode();
	    }finally{
		rl.unlock();
	    }
        }
    }
}
