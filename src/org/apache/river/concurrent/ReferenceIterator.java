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
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 * @author Peter Firmstone.
 */
class ReferenceIterator<T> implements Iterator<T> {
    private final Iterator<Referrer<T>> iterator;
    private T next;

    ReferenceIterator(Iterator<Referrer<T>> iterator) {
        if ( iterator == null ) throw new IllegalArgumentException("iterator cannot be null");
        this.iterator = iterator;
        next = null;
    }

    public boolean hasNext() {
        while ( iterator.hasNext()){
            Referrer<T> t = iterator.next();
            if ( t != null ) {
                next = t.get();
                if (next != null) return true;
                else iterator.remove(); // garbage collected.
            }
        }
        next = null;
        return false;
    }

    public T next() {
        if (next == null) throw new NoSuchElementException();
        return next;
    }

    public void remove() {
        iterator.remove();
    }
    
}
