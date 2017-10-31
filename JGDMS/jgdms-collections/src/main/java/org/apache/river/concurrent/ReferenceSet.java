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

import java.util.Iterator;
import java.util.Set;

/**
 * A Referenced Set.
 * 
 * 
 * @see ReferenceCollection
 * @author Peter Firmstone.
 */
class ReferenceSet<T> extends ReferenceCollection<T> implements Set<T>{

    ReferenceSet(Set<Referrer<T>> col, Ref type, boolean gcThreads, long gcCycle){
        super(col, type, gcThreads, gcCycle);
    }
    
    ReferenceSet(Set<Referrer<T>> col, ReferenceQueuingFactory<T, Referrer<T>> rqf, Ref type){
        super(col, rqf, type);
    }
    
    public boolean equals(Object o) {
	if (o == this) return true;
	if (!(o instanceof Set)) return false;
        @SuppressWarnings("unchecked")
	Set<T> s = (Set<T>) o;
	if (s.size() != size())
	    return false;
        try {
            return containsAll(s);
        } catch (ClassCastException e)   {
            return false;
        } catch (NullPointerException e) {
            return false;
}
    }

    @Override
    public int hashCode() {
        int hash = 0;
        Iterator<T> i = iterator();
        while (i.hasNext()){
            T next = i.next();
            if ( next != null) {
                hash = hash + next.hashCode();
            }
        }
        return hash;
    }
}
