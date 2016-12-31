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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * A temporary but functional replacement for ReferenceKey's.  No attempt is
 * made to use readResolve to replace the original, that responsibility is left
 * up to the collection implementation during de-serialisation.
 * 
 * @author peter
 */
class ReferenceSerializedForm<T> implements Referrer<T>, Serializable{
    private static final long serialVersionUID = 1L;
    
    private T obj;
    private transient int hash;
    
    ReferenceSerializedForm(){
        obj = null;
        hash = 0;
    }
    
    ReferenceSerializedForm(T t){
        obj = t;
        int hash = 7;
        hash = 29 * hash + System.identityHashCode(t);
        hash = 29 * hash + (t!= null ? t.getClass().hashCode() :0);
        this.hash = hash;
    }

    public T get() {
        T t = obj;
        obj = null;
        return t;
    }

    public void clear() {
        obj = null;
    }

    public boolean isEnqueued() {
        return false;
    }

    public boolean enqueue() {
        return false;
    }
    
    /* In this case we're using Identity because it has a wider scope.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)  return true; // Same reference.
        if (!(o instanceof Referrer))  return false;
        Object k1 = get();
        Object k2 = ((Referrer) o).get();
        if ( k1 != null && k1 == k2) return true;
        return ( k1 == null && k2 == null && hashCode() == o.hashCode()); // Both objects were collected.
    }

    @Override
    public int hashCode() {
        return hash;
    }
    
    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int hash = 7;
        hash = 29 * hash + System.identityHashCode(obj);
        hash = 29 * hash + (obj != null ? obj.getClass().hashCode() :0);
        this.hash = hash;
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    
}
