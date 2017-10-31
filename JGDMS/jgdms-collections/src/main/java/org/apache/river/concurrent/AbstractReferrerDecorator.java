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
 * This class exists purely for allowing a client with their own Collection
 * implementation or Collection wrapper to perform custom serialisation of the 
 * References by replacing the standard Referrer's with their own implementation.
 * 
 * During de-serialisation the client's Referrer can 
 * perform any required de-serialisation defensive copying or integrity checks.
 * 
 * The client Referrer will be replaced after de-serialisation is complete.
 * 
 * The client doesn't need to implement this class, just Referrer.
 * 
 * @param <T> - the referent.
 * @author peter
 */
abstract class AbstractReferrerDecorator<T> implements UntouchableReferrer<T> {

     AbstractReferrerDecorator() {
    }

    public String toString() {
        return get().toString();
    }

    public void clear() {
        getReference().clear();
    }

    public boolean enqueue() {
        return getReference().enqueue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)  return true; // Same reference.
        if (!(o instanceof Referrer))  return false;
        return getReference().equals(o);
    }

    public final T get() {
        return getReference().get();
    }
    
    public T lookDontTouch(){
        Referrer<T> r = getReference();
        if (r instanceof UntouchableReferrer){
            return ((UntouchableReferrer<T>)r).lookDontTouch();
        }
        return r.get(); // Oh well, at least we tried.
    }

    /**
     * @return the Referrer.
     */
    abstract Referrer<T> getReference();

    @Override
    public int hashCode() {
        return getReference().hashCode();
    }

    public boolean isEnqueued() {
        return getReference().isEnqueued();
    }
    
}
