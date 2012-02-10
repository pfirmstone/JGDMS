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

import java.util.Comparator;

/**
 * Implements equals and hashCode, subclass ReferenceComparator implements 
 * Serializable and contains serial data.
 * 
 * @author peter
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
        T t1 = o1.get();
        T t2 = o2.get();
        if ( t1 != null && t2 != null) return get().compare(t1, t2);
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
