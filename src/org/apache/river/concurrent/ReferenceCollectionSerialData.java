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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;

/**
 *This class is the serial form of ReferenceCollection and all it's subclasses.
 * 
 * While the serial form of this class will remain compatible with itself,
 * ReferenceCollection may replace this implementation with another
 * at some point in the future.
 * 
 * This class will still be able to de-serialise into a ReferenceCollection.
 * 
 * @author peter
 */
 class ReferenceCollectionSerialData<T> 
    extends ReferenceCollectionRefreshAfterSerialization<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** @serialField  */
    private Ref type;
    /** @serialField */
    private Collection<Referrer<T>> collection;
    /** @serialField */
    private Class referenceCollectionClass;

    @SuppressWarnings("unchecked")
   ReferenceCollectionSerialData( Class clazz,
            Collection<Referrer<T>> underlyingCollection, Ref type) 
           throws InstantiationException, IllegalAccessException{
        // Create a new instance of the underlying collection and
        // add all objects.
        if ( clazz == null || underlyingCollection == null || type == null){
            throw new NullPointerException("null parameters prohibited");
        }
        this.collection = underlyingCollection;
        this.type = type;
        this.referenceCollectionClass = clazz;
   }
    
    @Override
    public Ref getType() {
        return type;
    }

    @Override
    public Collection<Referrer<T>> getCollection() {
        return collection;
    }

    @Override
    public Class getClazz() {
        return referenceCollectionClass;
    }

     private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if ( referenceCollectionClass == null || collection == null || type == null){
            throw new InvalidObjectException("null fields found after deserialization");
        }
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }
    
}
