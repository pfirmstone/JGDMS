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

/**
 *
 * @author peter
 */
class TempIdentityReferrer<T> implements UntouchableReferrer<T> {
    
    private final T t;
    
    TempIdentityReferrer(T t){
        if ( t == null ) throw new NullPointerException("Null prohibited");
        this.t = t;
    }

    @Override
    public T get() {
        return t;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public boolean isEnqueued() {
        return false;
    }

    @Override
    public boolean enqueue() {
        return false;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Referrer)) {
            return false;
        }
        Object t2 = ((Referrer) o).get();
        return( t == t2 );
    }
    
    public int hashCode(){
        int hash = 7;
        hash = 29 * hash + System.identityHashCode(t);
        hash = 29 * hash + t.getClass().hashCode();
        return hash;
    }

    public T lookDontTouch() {
        return t;
    }
    
}
