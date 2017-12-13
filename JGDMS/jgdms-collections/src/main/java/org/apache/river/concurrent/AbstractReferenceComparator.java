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

import java.util.Comparator;

/**
 * Implements equals and hashCode, subclass ReferenceComparator implements 
 *
 * @author Peter Firmstone.
 */
abstract class AbstractReferenceComparator<T> implements Comparator<Referrer<T>> {

    AbstractReferenceComparator() {
    }

    /**
     * This is implemented such that if either Referrer contains a null
     * referent, the comparison is only made using Referrer's, this may
     * have a different natural order, than the comparator provided, however
     * equals will always return 0, this is important to correctly remove
     * a Referrer once its referent has been collected.
     * 
     * The following tests give this a good workout:
     * 
     * com/sun/jini/test/impl/joinmanager/ZRegisterStorm.td
     * com/sun/jini/test/spec/renewalmanager/EventTest.td
     * 
     * @param o1
     * @param o2
     * @return 
     */
    public int compare(Referrer<T> o1, Referrer<T> o2) {
        if (o1 == o2) return 0;
        T t1 = null;
        if (o1 instanceof UntouchableReferrer){
            t1 = ((UntouchableReferrer<T>)o1).lookDontTouch();
        } else {
            t1 = o1.get();
        }
        T t2 = null;
        if (o2 instanceof UntouchableReferrer){
            t2 = ((UntouchableReferrer<T>)o2).lookDontTouch();
        } else {
            t2 = o2.get();
        }
        if ( t1 != null && t2 != null){
            int c = get().compare(t1, t2);
            if ( c == 0 ){// If untouchable this is a hit.
                o1.get();
                o2.get();
            }
            return c;
        }
        int hash1 = o1.hashCode();
        int hash2 = o2.hashCode();
        if (hash1 < hash2) return -1;
        if (hash1 > hash2) return 1;
        if (o1.equals(o2)) return 0;
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof AbstractReferenceComparator) {
            return get().equals(((AbstractReferenceComparator) o).get());
        }
        return false;
    }

    abstract Comparator<? super T> get();

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + (this.get() != null ? this.get().hashCode() : 0);
        return hash;
    }
    
}
