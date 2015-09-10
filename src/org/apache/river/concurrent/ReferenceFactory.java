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
import java.lang.ref.ReferenceQueue;

/**
 * ReferenceFactory creates References, representing the various subclasses
 * of Reference, such as WEAK, SOFT and STRONG for
 * use in collections.
 * 
 * These subtypes override equals and hashcode, so that References may be used
 * in Collections, Maps, Sets and Lists to represent their referent.
 * 
 * @see Reference
 * @see Ref
 * @author Peter Firmstone.
 */
class ReferenceFactory<T> {
    
    // Non instantiable.
    private ReferenceFactory(){}
    
    static <T> Referrer<T> create(T t, RefQueue<? super T> queue, Ref type ){
        if (t == null) throw new NullPointerException("Reference collections cannot contain null");
        RefReferenceQueue<? super T> refQueue = null;
        TimedRefQueue timedRefQue = null;
        if (queue instanceof RefReferenceQueue) refQueue = (RefReferenceQueue<? super T>) queue;
        if (queue instanceof TimedRefQueue) timedRefQue = (TimedRefQueue) queue;
        switch (type){
            case WEAK_IDENTITY: 
                return new ReferrerDecorator<T>(new WeakIdentityReference<T>(t, refQueue));
            case SOFT_IDENTITY: 
                return new ReferrerDecorator<T>(new SoftIdentityReference<T>(t, refQueue));
            case WEAK: 
                if (t instanceof Comparable) 
                    return new ComparableReferrerDecorator<T>(new WeakEqualityReference<T>(t, refQueue));
                return new ReferrerDecorator<T>(new WeakEqualityReference<T>(t, refQueue));
            case SOFT: 
                if (t instanceof Comparable) 
                    return new ComparableReferrerDecorator<T>(new SoftEqualityReference<T>(t, refQueue));
                return new ReferrerDecorator<T>(new SoftEqualityReference<T>(t, refQueue));
            case TIME:
                if (t instanceof Comparable)
                    return new TimedComparableReferrerDecorator<T>(new TimedReferrer<T>(t, timedRefQue));
                return new TimedReferrerDecorator<T>(new TimedReferrer<T>(t, timedRefQue));
            default: 
                if (t instanceof Comparable) 
                    return new ComparableReferrerDecorator<T>(new StrongReference<T>(t, refQueue));
                return new ReferrerDecorator<T>(new StrongReference<T>(t, refQueue));
        }
    }
    
    /**
     * This doesn't create a genuine reference, only a simple wrapper object
     * that will be used once then discarded.  The Referrer will be allocated
     * to the stack by the jvm and not the heap.  There is no queue, no
     * reference, this is simply a wrapper class that provides the correct
     * equals, hashcode and comparator semantics.
     * 
     * This object must not be added to any collection classes, it is not
     * serialisable, it's sole intended purpose is to improve read performance
     * by being allocated to the stack, not the heap space and avoiding a
     * cache miss.  To achieve this goal, it must not be shared with other
     * Threads either.
     * 
     * @param <T>
     * @param t
     * @param type
     * @return 
     */
    static <T> Referrer<T> singleUseForLookup(T t, Ref type){
        if (t == null) throw new NullPointerException("Reference collections cannot contain null");
        switch (type){
            case WEAK_IDENTITY: 
                return new TempIdentityReferrer<T>(t);
            case SOFT_IDENTITY: 
                return new TempIdentityReferrer<T>(t);
            case WEAK: 
                if (t instanceof Comparable) return new TempComparableReferrer<T>(t);
                return new TempEqualReferrer<T>(t);
            case SOFT: 
                if (t instanceof Comparable) return new TempComparableReferrer<T>(t);
                return new TempEqualReferrer<T>(t);
            default: 
                if (t instanceof Comparable) return new TempComparableReferrer<T>(t);
                return new TempEqualReferrer<T>(t);
        }
    }
    
}
