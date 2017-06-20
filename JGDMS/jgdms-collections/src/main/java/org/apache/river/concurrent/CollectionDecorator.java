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

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

/**
 *
 * @author peter
 */
class CollectionDecorator<T> extends AbstractCollection<Referrer<T>> implements Collection<Referrer<T>> {
    private final Collection<T> col;
    private final ReferenceQueuingFactory<T, Referrer<T>> rqf;
    private final boolean enque;
    private final boolean temporary;
    
    CollectionDecorator(Collection<T> col, ReferenceQueuingFactory<T, Referrer<T>> rqf, boolean enque, boolean temporary){
        this.col = col;
        this.rqf = rqf;
        this.enque = enque;
        this.temporary = temporary;
    }

    @Override
    public Iterator<Referrer<T>> iterator() {
        return new Iter<T>(col.iterator(), rqf);
    }

    @Override
    public int size() {
        return col.size();
    }
    
    public boolean add(Referrer<T> t) {
	return col.add( t != null ? t.get() : null );
    }
    
    private class Iter<T> implements Iterator<Referrer<T>> {
        Iterator<T> iterator;
        private final ReferenceQueuingFactory<T, Referrer<T>> rqf;
        Iter(Iterator<T> it, ReferenceQueuingFactory<T, Referrer<T>> rqf){
            iterator = it;
            this.rqf = rqf;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Referrer<T> next() {
            return rqf.referenced( iterator.next(), enque, temporary);
        }

        @Override
        public void remove() {
            iterator.remove();
        }
        
    }
    
}
