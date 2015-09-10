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

import java.lang.ref.Reference;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 
 * @author Peter Firmstone.
 */
class ReferenceEntryFacade<K, V> implements Map.Entry<K, V> {
    private final Entry<Referrer<K>, Referrer<V>> entry;
    private final ReferenceQueuingFactory<V, Referrer<V>> rqf;

    ReferenceEntryFacade(Entry<Referrer<K>, Referrer<V>> entry, ReferenceQueuingFactory<V, Referrer<V>> rqf){
        this.entry = entry;
        this.rqf = rqf;
    }
    
    public K getKey() {
        Referrer<K> k = entry.getKey();
        if ( k != null ) return k.get();
        return null;
    }

    public V getValue() {
        Referrer<V> v = entry.getValue();
        if ( v != null ) return v.get();
        return null;
    }

    public V setValue(V value) {
        Referrer<V> v = entry.setValue(wrapVal(value, true));
        if ( v != null ) return v.get();
        return null;
    }

    /**
     * Implementation copied directly from Entry javadoc.
     * @see Entry#hashCode() 
     * @return 
     */
    @Override
    public int hashCode() {
        return (getKey()==null ? 0 : getKey().hashCode()) ^
             (getValue()==null ? 0 : getValue().hashCode());
    }

    /**
     * Implementation copied directly from Entry javadoc.
     * @see Entry#equals(java.lang.Object) 
     */
    public boolean equals(Object o){
        if ( o == this ) return true;
        if ( !(o instanceof Entry) ) return false;
        Entry e1 = this;
        Entry e2 = (Entry) o;   
        return ( (e1.getKey()==null ? e2.getKey()==null : 
                e1.getKey().equals(e2.getKey()))  &&
                (e1.getValue()==null ? e2.getValue()==null : 
                e1.getValue().equals(e2.getValue())));
    }
    
    private Referrer<V> wrapVal(V val, boolean enque) {
        return rqf.referenced(val, enque, false);
}

}
