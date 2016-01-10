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
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 *
 * @author peter
 */
@AtomicSerial
class SerialMap<K,V> extends AbstractMap<K,V> implements SortedMap<K,V>, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    final Entry<K,V> [] entrySet;
    final Comparator<? super K> comparator;
    
    SerialMap(Map<K,V> map){
	entrySet = new Entry [map.size()];
	Set<Entry<K,V>> eSet = map.entrySet();
	Iterator<Entry<K,V>> it = eSet.iterator();
	for (int i = 0; it.hasNext(); i++){
	    entrySet[i] = new Ent(it.next());
	}
	if (map instanceof SortedMap){
	    comparator = ((SortedMap<K,V>)map).comparator();
	} else {
	    comparator = null;
	}
    }
    
    SerialMap(GetArg arg) throws IOException{
	entrySet = (Entry<K, V>[]) arg.get("entrySet", new Object[0]);
	comparator = (Comparator<? super K>) arg.get("comparator", null);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
	return new SerialSet<Entry<K,V>>(entrySet);
    }

    @Override
    public Comparator<? super K> comparator() {
	return comparator;
    }

    @Override
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
	throw new UnsupportedOperationException(
		"Not supported, SerialMap only intended for Serialization."); 
    }

    @Override
    public SortedMap<K, V> headMap(K toKey) {
	throw new UnsupportedOperationException(
		"Not supported, SerialMap only intended for Serialization."); 
    }

    @Override
    public SortedMap<K, V> tailMap(K fromKey) {
	throw new UnsupportedOperationException(
		"Not supported, SerialMap only intended for Serialization."); 
    }

    @Override
    public K firstKey() {
	throw new UnsupportedOperationException(
		"Not supported, SerialMap only intended for Serialization."); 
    }

    @Override
    public K lastKey() {
	throw new UnsupportedOperationException(
		"Not supported, SerialMap only intended for Serialization."); 
    }
    
    @AtomicSerial
    private static class Ent<K,V> implements Entry<K,V>, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	final K key;
	final V value;

	public Ent(Entry<? extends K, ? extends V> entry) {
	    key = entry.getKey();
	    value = entry.getValue();
	}

	@Override
	public K getKey() {
	   return key;
	}

	@Override
	public V getValue() {
	    return value;
	}

	@Override
	public V setValue(V value) {
	    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
	
    }
    
}
