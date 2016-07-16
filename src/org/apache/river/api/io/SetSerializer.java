/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.io;

import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 *
 * @author peter
 */
@AtomicSerial
class SetSerializer<T> extends AbstractSet<T> implements SortedSet<T>, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    T [] content;
    Comparator<? super T> comparator;
    
    SetSerializer(Set<T> set){
	content = set.toArray((T[]) new Object [set.size()]);
	if (set instanceof SortedSet) comparator = ((SortedSet<T>)set).comparator();
	else comparator = null;
    }
    
    SetSerializer(GetArg arg) throws IOException {
	// No invariant checks
	content = (T[]) arg.get("content", new Object[0], Object[].class);
	comparator = arg.get("comparator", null, Comparator.class);
    }
    
    SetSerializer(T [] set){
	content = set;
    }

    @Override
    public Iterator<T> iterator() {
	return new IT<T>(content);
    }

    @Override
    public int size() {
	return content.length;
    }

    @Override
    public Comparator<? super T> comparator() {
	return comparator;
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public T first() {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public T last() {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    private static class IT<T> implements Iterator<T> {
	
	T [] set;
	int index;
	
	IT(T [] set){
	    this.set = set;
	    index = 0;
	}

	@Override
	public boolean hasNext() {
	    if (index < set.length) return true;
	    return false;
	}

	@Override
	public T next() {
	    if (index < set.length) return set[index++];
	    throw new NoSuchElementException();
	}

	@Override
	public void remove() {
	    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
	
    }
    
}
