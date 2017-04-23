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
 * Immutable map backed by an array.
 * 
 * @author peter
 */
@AtomicSerial
class MapSerializer<K,V> extends AbstractMap<K,V> implements SortedMap<K,V>, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    final Entry<K,V> [] entrySet;
    final Comparator<? super K> comparator;
    
    private static Ent[] convert(Map map){
        Ent [] entrySet = new Ent[map.size()];
        Set<Entry> eSet = map.entrySet();
	Iterator<Entry> it = eSet.iterator();
	for (int i = 0; it.hasNext(); i++){
	    entrySet[i] = new Ent(it.next());
	}
        return entrySet;
    }
    
    MapSerializer(Map<K,V> map){
        this(
                convert(map),
                map instanceof SortedMap ?
                        ((SortedMap<K,V>)map).comparator()
                        : null
        );
    }
    
    MapSerializer(GetArg arg) throws IOException{
        this(
            arg.get("entrySet", new Ent[0], Ent[].class),
            arg.get("comparator", null, Comparator.class)
        );
    }
    
    private MapSerializer(Ent[] set, Comparator<? super K> comp){
        entrySet = set;
        comparator = comp;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
	return new SetSerializer<Entry<K,V>>(entrySet);
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
        /* Classes are only here for compiler, atomic deserialization only
         * checks these against keys and value, an attacker can supply 
         * any class they want so, this isn't for security.  The client
         * is responsible for type checking map contents before use 
         */
	final Class<? extends K> keyClass;
	final Class<? extends V> valueClass;

	public Ent(Entry<? extends K, ? extends V> entry) {
	    this(entry.getKey(), entry.getValue());
	}
	
	public Ent(GetArg arg) throws IOException{
	    this((K) key(arg), (V) value(arg));
	}
	
	private Ent(K key, V value){
	    this.key = key;
	    this.value = value;
	    if(key != null){
		keyClass = (Class<K>) key.getClass();
	    } else {
		keyClass = null;
	    }
	    if (value != null){
		valueClass = (Class<V>) value.getClass();
	    } else {
		valueClass = null;
	    }
	}
	
	private static <K> K key(GetArg arg) throws IOException {
	    Class<K> keyClass = arg.get("keyClass", null, Class.class);
	    K key;
	    if (keyClass != null){
		key = arg.get("key", null, keyClass) ;
	    } else {
		key = null;
	    }
	    return key;
	}
	
	private static <V> V value(GetArg arg) throws IOException {
	    Class<V> valueClass = arg.get("valueClass", null, Class.class);
	    V value;
	    if (valueClass != null){
		value = arg.get("value", null, valueClass);
	    } else {
		value = null;
	    }
	    return value;
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
	    throw new UnsupportedOperationException("Not supported, SerialMap only intended for Serialization"); //To change body of generated methods, choose Tools | Templates.
	}
	
    }
    
}
