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

package org.apache.river.lookup.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * An implementation of the <code>java.util.Map</code> interface that has
 * a serialized form consistent in all virtual machines. <code>ConsistentMap</code>
 * instances are unmodifiable. All mutator methods, such as <code>add</code> and
 * <code>remove</code>, throw <code>UnsupportedOperationException</code>.
 * This class permits <code>null</code> values and the <code>null</code> key.
 *
 * <p>
 * Although instances of this class are unmodifiable, they are not necessarily
 * immutable. If a client retrieves a mutable object (either a key or value) contained in a
 * <code>ConsistentMap</code> and mutates that object, the client in effect
 * mutates the state of the <code>ConsistentMap</code>. In this case, the
 * serialized form of the <code>ConsistentMap</code> will most likely also
 * have been mutated. A <code>ConsistentMap</code> that contains only immutable
 * objects will maintain a consistent serialized form indefinitely. But a 
 * <code>ConsistentMap</code> that contains mutable objects will maintain a
 * consistent serialized form only so long as the mutable objects are not
 * mutated.
 *
 * @author Bill Venners
 */
@AtomicSerial
public class ConsistentMap<K,V> extends AbstractMap<K,V> implements Serializable {

    private static final long serialVersionUID = -5223157327307155247L;

    /**
     * @serial A <code>Set</code> of <code>java.util.Map.Entry</code> objects,
     *     the key-value pairs contained in this <code>ConsistentMap</code>.
     */
    private final Set<Map.Entry<K,V>> entrySet;

    /**
     * Constructs a new, empty <code>ConsistentMap</code>. All instances
     * of <code>ConsistentMap</code> are unmodifiable.
     */
    public ConsistentMap() {
        entrySet = new ConsistentSet<Map.Entry<K,V>>(new HashSet<Map.Entry<K,V>>());
    }

    /**
     * Constructs a new <code>ConsistentMap</code> containing the elements
     * in the passed collection. All instances of <code>ConsistentMap</code>
     * are unmodifiable.
     *
     * @param init the map whose elements are to be placed into this map.
     * @throws  NullPointerException if the passed <code>init</code> reference
     *     is <code>null</code>
     */
    public ConsistentMap(Map<K,V> init) {
	this(entrySet(init));
    }

    public ConsistentMap(GetArg arg) throws IOException {
	this(entrySet((Map<K, V>) arg.get("entrySet", null)));
    }
    
    private static <K,V> Set<Map.Entry<K,V>> entrySet(Map<K,V> init){
        if (init == null) {
            throw new NullPointerException();
        }

        // Must put the key-value pairs into ConsistentMapEntry objects,
        // so they'll behave correctly when setValue() is invoked on them.
        Set<Map.Entry<K,V>> unmodEntries = new HashSet<Map.Entry<K,V>>(init.size());
        Set<Map.Entry<K,V>> entries = init.entrySet();
        Iterator<Map.Entry<K,V>> it = entries.iterator();
        while (it.hasNext()) {

            Map.Entry<K,V> entry = it.next();
            Map.Entry<K,V> unmodEntry = new ConsistentMapEntry<K,V>(entry.getKey(), entry.getValue());
            unmodEntries.add(unmodEntry);
        }
        return new ConsistentSet<Map.Entry<K,V>>(unmodEntries);
    }

    private ConsistentMap(Set<Map.Entry<K,V>> set){
	entrySet = set;
    }

    /**
     * Returns a set view of the mappings contained in this
     * <code>ConsistentMap</code>. Each element in the returned
     * set is a <code>Map.Entry</code>
     *
     * @return a set view of the mappings contained in this
     *     <code>ConsistentMap</code>.
     */
    @Override
    public Set<Map.Entry<K,V>> entrySet() {
        return Collections.unmodifiableSet(entrySet);
    }
}

