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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is an experimental serial form of ReferenceCollection and 
 * all it's subclasses.
 * 
 * While the serial form of this class will remain compatible with itself,
 * ReferenceCollection may replace this implementation with another
 * at some point in the future.
 * 
 * This class will still be able to de-serialise into a ReferenceCollection.
 * 
 * The issue with defensive copying a collection is, there may be a number
 * of collection views serialised together, such as a Set and a SubSet or
 * any number of sub set's.  If we perform a defensive copy, these instances
 * will no longer refer to the same underlying set.
 * 
 * @param <T> referent type.
 * @author peter
 */
public class ReferenceCollectionSerialDataDefensiveCopy<T> 
extends ReferenceCollectionRefreshAfterSerialization<T> implements Serializable{
    private static final long serialVersionUID = 1L;
    private static final String method = "comparator";

   /** @serialField  */
   private Ref type;
   /** @serialField */
   private Collection<Referrer<T>> collection;
   /** @serialField */
   private Class referenceCollectionClass;
   
    @SuppressWarnings("unchecked")
   ReferenceCollectionSerialDataDefensiveCopy( Class clazz,
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
        // Attempt Defensive copy of collection.
        Class cl = collection.getClass();
        Collection replacement = null;
        Comparator comparator = null;
        boolean comparatorAbsenceConfirmed = false;
        Method comparatorMethod = null;
        try {
            comparatorMethod = cl.getMethod(method, (Class[]) null);
        } catch (NoSuchMethodException ex) {
            comparatorAbsenceConfirmed = true;
        } catch (SecurityException ex) {
            Logger.getLogger(ReferenceCollectionSerialDataDefensiveCopy.class.getName())
                    .log(Level.WARNING, "Insufficient privileges for defensive copying of serial data", ex);
            return; // Insufficient privileges, giving up.
        }
        if ( comparatorMethod != null ){
            try {
                comparator = (Comparator) comparatorMethod.invoke(collection, (Object[]) null);
                if ( comparator == null ) comparatorAbsenceConfirmed = true;
            } catch (Exception e) {
                comparatorAbsenceConfirmed = true;
                // nothing to see here, move along.
            }
        }
        if (comparatorAbsenceConfirmed){
            // There is no comparator
            Constructor[] constructors = cl.getConstructors();
            // Check for an int only arg constructor, this is important
            // For Queue's that may be capacity limited, since, we're
            // transferring to another machine, an unlimited capacity 
            // Queue would be rude.
        }
        // Comparator is confirmed, here's where we construct a new
        // object with a comparator and size constructor ,or a comparator
        // constructor.
        
        // Now use an iterator to add all elements.
        
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }
    
}
