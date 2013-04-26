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
package com.sun.jini.lease;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;

/**
 * A base class for implementing LeaseMaps.  This class implements all
 * of the Map methods, and ensures keys and values are of the correct type.
 * The subclass is responsible for implementing the actual LeaseMap methods:
 * canContainKey, renewAll, and cancelAll, and serialization of any subclass
 * state.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public abstract class AbstractLeaseMap implements LeaseMap<Lease, Long>, ConcurrentMap<Lease, Long> {
    /**
     * Map from Lease to Long(duration), where all leases have the same
     * destination.
     *
     * @serial
     */
    protected final Map<Lease, Long> map;
    
    protected final Object mapLock; 

    /**
     * Default to using a small HashMap.  It is assumed that
     * canContainKey(lease) is true.
     * @param lease
     * @param duration  
     */
    protected AbstractLeaseMap(Lease lease, long duration) {
	this(new ConcurrentHashMap<Lease, Long>(13), lease, duration);
    }

    /**
     * Provide a map of your choice.  It is assumed that
     * canContainKey(lease) is true.
     * @deprecated to get peoples attention that map may require synchronized access.
     */
    @Deprecated
    protected AbstractLeaseMap(Map<Lease, Long> map, Lease lease, long duration) {
        if (map instanceof ConcurrentMap) {
            this.map = map;
            mapLock = null;
        } else {
            this.map = Collections.synchronizedMap(map);
            mapLock = map;
        }
	map.put(lease, Long.valueOf(duration));
    }

    /** Check that the key is valid for this map
     * @param key 
     */
    protected void checkKey(Object key) {
	if (!canContainKey(key)) {
            throw new IllegalArgumentException(
                                       "key is not valid for this LeaseMap");
        }
    }

    /** Check that the value is a Long
     * @param value 
     */
    protected static void checkValue(Object value) {
	if (!(value instanceof Long)) {
            throw new IllegalArgumentException("value is not a Long");
        }
    }

    // inherit javadoc
    public int size() {
	return map.size();
    }

    // inherit javadoc
    public boolean isEmpty() {
	return map.isEmpty();
    }

    // inherit javadoc
    public boolean containsKey(Object key) {
	checkKey(key);
	return map.containsKey(key);
    }

    // inherit javadoc
    public boolean containsValue(Object value) {
	checkValue(value);
	return map.containsValue(value);
    }

    // inherit javadoc
    public Long get(Object key) {
	checkKey(key);
	return map.get(key);
    }

    // inherit javadoc
    public Long put(Lease key, Long value) {
	checkKey(key);
	checkValue(value);
	return map.put(key, value);
    }

    // inherit javadoc
    public Long remove(Object key) {
	checkKey(key);
	return map.remove(key);
    }

    /* Can't use map.putAll here, because we need to ensure checking
     * of the keys and values.
     */
    // inherit javadoc
    public void putAll(Map<? extends Lease, ? extends Long> m) {
	Iterator iter = m.entrySet().iterator();
	while (iter.hasNext()) {
            @SuppressWarnings("unchecked")
	    Map.Entry<? extends Lease, ? extends Long> e = (Map.Entry<? extends Lease, ? extends Long>) iter.next();
	    put(e.getKey(), e.getValue());
	}
    }

    // inherit javadoc
    public void clear() {
	map.clear();
    }

    // inherit javadoc
    public Set<Lease> keySet() {
	return map.keySet();
    }

    // inherit javadoc
    public Collection<Long> values() {
	return map.values();
    }

    /* We have to wrap the set so that we can do type checking on
     * Map.Entry.setValue.
     */
    // inherit javadoc
    public Set<Map.Entry<Lease, Long>> entrySet() {
	return new EntrySet(map.entrySet());
    }

    // inherit javadoc
    public boolean equals(Object o) {
	return map.equals(o); // XXX should sameDestination matter?
    }

    // inherit javadoc
    public int hashCode() {
	return map.hashCode();
    }

    @Override
    public Long putIfAbsent(final Lease key, final Long value) {
        if (map instanceof ConcurrentMap){
            ConcurrentMap<Lease, Long> m = (ConcurrentMap<Lease, Long>) map;
            return m.putIfAbsent(key, value);
        } else {
            synchronized (mapLock){
                Set<Map.Entry<Lease, Long>> entries;
                entries = map.entrySet();
                Map.Entry<Lease, Long> entry = new Map.Entry<Lease, Long>(){

                    @Override
                    public Lease getKey() {
                        return key;
                    }

                    @Override
                    public Long getValue() {
                        return value;
                    }

                    @Override
                    public Long setValue(Long value) {
                        throw new UnsupportedOperationException("Not supported.");
                    }
                    
                };
                if (entries.contains(entry)){
                    // Not absent return long.
                    return value;
                } else if ( map.containsKey(key)) {
                    // Might contain null value;
                    Long result = map.get(key);
                    if (result == null) map.put(key, value);
                    // If result is not null it is returned and no put was made
                    // or if it is null, the key value pair was added and null value 
                    // is retruned.
                    return result;
                } else {
                    map.put(key, value);
                    return null;
                }
                
            }
        }
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        if (map instanceof ConcurrentMap){
            return ((ConcurrentMap) map).remove(key, value);
        } else {
            Set<Map.Entry<Lease,Long>> entries;
                entries = map.entrySet();
                Map.Entry entry = new Map.Entry(){

                    @Override
                    public Object getKey() {
                        return key;
                    }

                    @Override
                    public Object getValue() {
                        return value;
                    }

                    @Override
                    public Object setValue(Object value) {
                        throw new UnsupportedOperationException("Not supported.");
                    }
                    
                };
                return entries.remove(entry);
        }
    }

    @Override
    public boolean replace(final Lease key, final Long oldValue, Long newValue) {
        if (map instanceof ConcurrentMap){
            return ((ConcurrentMap)map).replace(key, oldValue, newValue);
        } else {
            synchronized (mapLock){
            Set<Map.Entry<Lease,Long>> entries;
                entries = map.entrySet();
                Map.Entry<Lease, Long> entry = new Map.Entry<Lease, Long>(){

                    @Override
                    public Lease getKey() {
                        return key;
                    }

                    @Override
                    public Long getValue() {
                        return oldValue;
                    }

                    @Override
                    public Long setValue(Long value) {
                        throw new UnsupportedOperationException("Not supported.");
                    }
                    
                };
                if (entries.contains(entry)){
                    Long result = map.put(key, newValue);
                    assert result.equals(oldValue);
                    return true;
                } else  {
                    return false;
                }
            }
        }
    }

    @Override
    public Long replace(final Lease key, final Long value) {
        if (map instanceof ConcurrentMap){
            return ((ConcurrentMap<Lease, Long>)map).replace(key, value);
        } else {
            synchronized (mapLock){
            Set<Map.Entry<Lease,Long>> entries;
                entries = map.entrySet();
                Map.Entry<Lease, Long> entry = new Map.Entry<Lease, Long>(){

                    @Override
                    public Lease getKey() {
                        return key;
                    }

                    @Override
                    public Long getValue() {
                        return value;
                    }

                    @Override
                    public Long setValue(Long value) {
                        throw new UnsupportedOperationException("Not supported.");
                    }
                    
                };
                if (entries.contains(entry)){
                    Long result = map.put(key, value);
                    return result;
                } else  {
                    return null;
                }
            }
        }
    }

    /**
     * We use an AbstractSet to minimize the number of places where
     * we have to wrap objects inside new classes.  This could be
     * expensive, but the standard underlying maps (HashMap and TreeMap)
     * also use an AbstractSet for this set, so we're really not
     * making things that much worse.
     */
    private final static class EntrySet extends AbstractSet<Map.Entry<Lease,Long>> {
	private final Set<Map.Entry<Lease, Long>> set;

	public EntrySet(Set<Map.Entry<Lease, Long>> set) {
	    this.set = set;
	}

	/** Wrap so we can do type checking on Map.Entry.setValue. */
	public Iterator<Map.Entry<Lease, Long>> iterator() {
	    return new EntryIterator(set.iterator());
	}

	public boolean contains(Object o) {
	    return set.contains(o);
	}

	public boolean remove(Object o) {
	    return set.remove(o);
	}

	public int size() {
	    return set.size();
	}

	public void clear() {
	    set.clear();
	}
    }

    /** A wrapper so that we can wrap each Entry returned. */
    private static final class EntryIterator implements Iterator<Map.Entry<Lease,Long>> {
	private final Iterator<Map.Entry<Lease,Long>> iter;

	public EntryIterator(Iterator<Map.Entry<Lease,Long>> iter) {
	    this.iter = iter;
	}

	public boolean hasNext() {
	    return iter.hasNext();
	}

	public Map.Entry<Lease,Long> next() {
	    return new Entry(iter.next());
	}

	public void remove() {
	    iter.remove();
	}
    }

    /** Pass through, except for type checking on setValue */
    private static final class Entry implements Map.Entry<Lease, Long> {
	private final Map.Entry<Lease, Long> e;

	public Entry(Map.Entry<Lease, Long> e) {
	    this.e = e;
	}

	public Lease getKey() {
	    return e.getKey();
	}

	public Long getValue() {
	    return e.getValue();
	}

	public Long setValue(Long value) {
	    checkValue(value);
	    return e.setValue(value);
	}

	public boolean equals(Object o) {
	    return e.equals(o);
	}

	public int hashCode() {
	    return e.hashCode();
	}
    }
}
