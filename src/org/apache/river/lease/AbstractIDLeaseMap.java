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

package org.apache.river.lease;

import java.util.AbstractMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.concurrent.RC;
import org.apache.river.concurrent.Ref;
import org.apache.river.concurrent.Referrer;
import org.apache.river.lease.ID;

/**
 * AbstractIDLeaseMap is intended to work around some minor design warts in the 
 * {@link Lease} interface:
 * 
 * In the real world, when a Lease is renewed, a new Lease contract document
 * is issued, however when an electronic Lease is renewed the Lease expiry
 * date is changed and the record of the previous Lease is lost.  Ideally the
 * renew method would return a new Lease.
 * 
 * Current Lease implementations rely on a {@link Uuid} to represents the lease,
 * the expiry date is not included the equals or hashCode calculations.  For this
 * reason, two Lease objects, one expired and one valid, may be equal, this
 * is undesirable.
 * 
 * The Lease interface doesn't specify a contract for equals or hashCode,
 * all Lease implementations are also mutable, previous implementations
 * of {@link LeaseMap} used Leases as keys.
 * 
 * AbstractIDLeaseMap uses only the {@link ID}, usually a {@link Uuid}
 * provided by a Lease for internal map keys, if {@link ID} is not implemented
 * then the Lease itself is used as the key.
 * 
 * Both Lease keys and Long values are actually stored internally as values
 * referred to by ID keys, allowing Lease implementations to either not override
 * hashCode and equals object methods or allow implementations that more
 * accurately model reality.
 * 
 * This implementation is thread safe, concurrent and doesn't require external 
 * synchronization.
 * 
 * @param <K> 
 * @author peter
 */
public abstract class AbstractIDLeaseMap<K extends Lease> extends AbstractMap<K,Long> 
                              implements LeaseMap<K,Long> {
    
    private final ConcurrentMap<Object,K> leaseMap;
    private final ConcurrentMap<Object,Long> durationMap;
    private final Set<Entry<K,Long>> set;
    
    /**
     * Constructor for subclasses.
     */
    protected AbstractIDLeaseMap(){
        leaseMap = RC.concurrentMap(
                new ConcurrentHashMap<Referrer<Object>,Referrer<K>>(),
                Ref.WEAK, Ref.STRONG, 10000, 10000);
        durationMap = RC.concurrentMap(
                new ConcurrentHashMap<Referrer<Object>,Referrer<Long>>(),
                Ref.WEAK, Ref.STRONG, 10000, 10000);
        set = new ConcurrentSkipListSet<Entry<K,Long>>();
    }

    @Override
    public Set<Entry<K,Long>> entrySet() {
        return set;
    }
    
    void checkKey(Object key) throws IllegalArgumentException {
        if (!canContainKey(key)) throw new IllegalArgumentException("Key not valid for this LeaseMap");
    }
    
    void checkValue(Object value) throws IllegalArgumentException {
        if (!(value instanceof Long)) throw new IllegalArgumentException("Value not valid for this LeaseMap, must be Long");
    }
    
    public boolean containsValue(Object value){
        checkValue(value);
        return durationMap.containsValue(value);
    }
    
    /**
     * {@inheritDoc}
     * 
     * Determines whether the ID of the key matches the ID of another key
     * in the map.
     * 
     * @param key
     * @return 
     */
    public boolean containsKey(Object key){
        checkKey(key);
        return set.contains(new LeaseEntry<K>(getIdentity(key), null, null, null));
    }
    
    public Long get(Object key){
        checkKey(key);
        return durationMap.get(getIdentity(key));
    }
    
    /**
     * {@inheritDoc}
     * 
     * This implementation will place a new key value pair association in the map,
     * or it will replace both the key and the value if an equivalent association
     * currently exists in the map.
     * 
     * @param key
     * @param value
     * @return 
     */
    public Long put(K key, Long value) {
        checkKey(key);
        Object identity = getIdentity(key);
        LeaseEntry<K> entry = new LeaseEntry<K>(identity, leaseMap, durationMap, set);
        if (entry.isNew(key, value)){
            return null;
        } else { // existing identity, replace Lease and duration.
            leaseMap.replace(identity, key);
            return durationMap.replace(identity, value);
        }
    }
    
    public Long remove(Object key){
        checkKey(key);
        LeaseEntry<K> entry = new LeaseEntry<K>(getIdentity(key), null, durationMap, null);
        if (set.remove(entry)) return entry.getValue();
        return null;
    }
    
    private Object getIdentity(Object key){
        if (key instanceof ID) {
                return((ID) key).identity();
            } else {
                return key;
            } // Allows for support of legacy lease implementations where equals is based on Uuid.
    }

    /**
     * The logic behind this Entry is that the identity which maintains strong
     * references to the key and value will not be added to the set or leaseMap
     * if it's already present.
     */
    private static class LeaseEntry<K extends Lease> implements Entry<K,Long>, Comparable<LeaseEntry<K>> {
        
        private final ConcurrentMap<Object,K> leaseMap;
        private final ConcurrentMap<Object,Long> durationMap;
        private final Set<Entry<K,Long>> set;
        private final Object identity;
        private volatile K key;
        private volatile Long value;
        
                
        
        LeaseEntry(Object identity, ConcurrentMap<Object,K> leaseMap, ConcurrentMap<Object,Long> durationMap, Set<Entry<K,Long>> set){
            if (identity == null) throw new NullPointerException("Identity cannot be null");
            this.set = set;
            this.leaseMap = leaseMap;
            this.durationMap = durationMap;
            this.identity = identity;
        }
        
        boolean isNew(K key, Long value){
            if (set.add(this)){
                Object exists = leaseMap.putIfAbsent(identity, key);
                Object valExists = durationMap.putIfAbsent(identity, value);
                // If exists, there's a problem if identity is not the same object
                // we are forced to remove it, because key and value may not be 
                // strongly referenced by our identity and risk being garbage collected.
                if (exists != null || valExists != null){
                    this.key = key;
                    this.value = value;
                    try {
                        leaseMap.remove(identity);
                        durationMap.remove(identity);
                        leaseMap.put(identity, key);
                        durationMap.put(identity, value);
                    } finally {
                        this.key = null;
                        this.value = null;
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public K getKey() {
            K key = this.key;
            if (key != null) return key;
            return leaseMap.get(identity);
        }

        @Override
        public Long getValue() {
            Long value = this.value;
            if (value != null) return value;
            return durationMap.get(identity);
        }

        @Override
        public Long setValue(Long value) {
            return durationMap.replace(identity, value);
        }

        @Override
        public int hashCode() {
            return identity.hashCode();
        }
        
        public boolean equals(Object o){
            if (!(o instanceof LeaseEntry)) return false;
            LeaseEntry that = (LeaseEntry) o;
            return this.identity.equals(that.identity);
        }

        public int compareTo(LeaseEntry<K> o) {
            if (identity instanceof Uuid && o.identity instanceof Uuid){
                long mine = ((Uuid) identity).getLeastSignificantBits();
                long his = ((Uuid) o.identity).getLeastSignificantBits();
                if ( mine < his) return -1;
                if ( mine > his) return 1;
                if ( mine == his){
                    mine = ((Uuid) identity).getMostSignificantBits();
                    his = ((Uuid) o.identity).getMostSignificantBits();
                    if ( mine < his) return -1;
                    if ( mine > his) return 1;
                    return 0;
                }
            }
            int myHash = hashCode();
            int hisHash = o.hashCode();
            if (myHash < hisHash) return -1;
            if (myHash > hisHash) return 1;
            return 0;
        }
    
    }
 
}
