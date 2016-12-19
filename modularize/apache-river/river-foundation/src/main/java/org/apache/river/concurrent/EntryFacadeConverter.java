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
import java.util.AbstractMap.SimpleEntry;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

/**
 *
 * @author Peter Firmstone.
 */
class EntryFacadeConverter<K,V> implements ReferenceQueuingFactory<Entry<K,V>, Entry<Referrer<K>, Referrer<V>>> {
    private final ReferenceQueuingFactory<K, Referrer<K>> krqf;
    private final ReferenceQueuingFactory<V, Referrer<V>> vrqf;


    EntryFacadeConverter(ReferenceQueuingFactory<K, Referrer<K>> krqf, ReferenceQueuingFactory<V, Referrer<V>> vrqf) {
        this.krqf = krqf;
        this.vrqf = vrqf;
    }

    public Entry<K,V> pseudoReferent(Entry<Referrer<K>, Referrer<V>> u) {
        return new ReferenceEntryFacade<K, V>(u, vrqf);
    }

    public Entry<Referrer<K>, Referrer<V>> referenced(Entry<K,V> w, boolean enque, boolean temporary) {
        // The entry could alread by a Referrer based Entry obscured by a facade.
        return new SimpleEntry<Referrer<K>, Referrer<V>>(
            krqf.referenced(w.getKey(), enque, false), vrqf.referenced(w.getValue(), enque, false)
        );
        }

    public void processQueue() {
//        krqf.processQueue();
//        vrqf.processQueue();
    }

}
