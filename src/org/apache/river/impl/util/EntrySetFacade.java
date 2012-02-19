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

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/**
 * EntrySetFacade disguises the contained set's reference from the
 * caller with a Facade, or exposes the underlying references to the set, when
 * returned by the caller.
 *
 * @param <O>
 * @param <R>
 */
/**
 *
 * @author peter
 */
class EntrySetFacade<O, R> extends AbstractSet<O> implements Set<O> {
    private Set<R> set;
    private ReferenceQueuingFactory<O, R> factory;

    EntrySetFacade(Set<R> set, ReferenceQueuingFactory<O, R> wf) {
        this.set = set;
        this.factory = wf;
    }

    public int size() {
        factory.processQueue();
        return set.size();
    }

    public boolean isEmpty() {
        factory.processQueue();
        return set.isEmpty();
    }

    public Iterator<O> iterator() {
        factory.processQueue();
        return new EntryIteratorFacade<O, R>(set.iterator(), factory);
    }

    public void clear() {
        set.clear();
    }

    public boolean add(O e) {
        factory.processQueue();
        if ( e == null ) return false;
        return set.add(factory.referenced(e, true, false));
    }
    }
