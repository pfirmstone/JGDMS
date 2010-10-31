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

package com.artima.lookup.util;

import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.HashSet;
import java.util.AbstractMap;
import java.io.Serializable;

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
public class ConsistentMap extends AbstractMap implements Serializable {

    private static final long serialVersionUID = -5223157327307155247L;

    /**
     * @serial A <code>Set</code> of <code>java.util.Map.Entry</code> objects,
     *     the key-value pairs contained in this <code>ConsistentMap</code>.
     */
    private Set entrySet;

    /**
     * Constructs a new, empty <code>ConsistentMap</code>. All instances
     * of <code>ConsistentMap</code> are unmodifiable.
     */
    public ConsistentMap() {
        entrySet = new ConsistentSet(new HashSet());
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
    public ConsistentMap(Map init) {

        if (init == null) {
            throw new NullPointerException();
        }

        // Must put the key-value pairs into ConsistentMapEntry objects,
        // so they'll behave correctly when setValue() is invoked on them.
        HashSet unmodEntries = new HashSet();
        Set entries = init.entrySet();
        Iterator it = entries.iterator();
        while (it.hasNext()) {

            Map.Entry entry = (Map.Entry) it.next();
            Map.Entry unmodEntry = new ConsistentMapEntry(entry.getKey(), entry.getValue());
            unmodEntries.add(unmodEntry);
        }
        entrySet = new ConsistentSet(unmodEntries);
    }

    /**
     * Returns a set view of the mappings contained in this
     * <code>ConsistentMap</code>. Each element in the returned
     * set is a <code>Map.Entry</code>
     *
     * @return a set view of the mappings contained in this
     *     <code>ConsistentMap</code>.
     */
    public Set entrySet() {

        return entrySet;
    }
}

