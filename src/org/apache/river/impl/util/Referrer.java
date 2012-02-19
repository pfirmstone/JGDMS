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

import java.lang.ref.Reference;

/**
 * An interface for References used in collections, it defines the equals
 * and hashCode contracts as well as methods identical to Reference.
 * 
 * A client may wish to implement this interface to replace a standard Referrer
 * during serialisation with custom implementations to deconstruct and
 * reconstruct non serialisable objects, or to perform integrity checks.
 * 
 * This must be implemented in a Collection provided by the client.
 * 
 * After de-serialisation is complete, the client Referrer will be replaced
 * with a standard Referrer.
 * 
 * @see Reference
 * @see Ref
 * @param <T> 
 * @author Peter Firmstone
 */
public interface Referrer<T> {
    
    /**
     * @see Reference#get()
     */
    public T get() ;
    /**
     * @see Reference#clear()
     */
    public void clear();
    /**
     * @see Reference#isEnqueued() 
     * @return true if enqueued.
     */
    public boolean isEnqueued();
    /**
     * @see Reference#enqueue() 
     * @return 
     */
    public boolean enqueue();
    
    /**
     * Equals is calculated on IDENTITY or equality.
     * 
     * IDENTITY calculation:
     * 
     * if (this == o) return true;
     * if (!(o instanceof Referrer)) return false;
     * Object k1 = get();
     * Object k2 = ((Referrer) o).get();
     * if ( k1 != null && k1 == k2 ) return true;
     * return ( k1 == null && k2 == null && hashCode() == o.hashCode());
     * 
     * Equality calculation:
     * 
     * if (this == o)  return true; // Same reference.
     * if (!(o instanceof Referrer))  return false;
     * Object k1 = get();
     * Object k2 = ((Referrer) o).get();
     * if ( k1 != null && k1.equals(k2)) return true;
     * return ( k1 == null && k2 == null && hashCode() == o.hashCode());
     *
     * @see Ref
     * @param o
     * @return 
     */
    public boolean equals(Object o);
    
    /**
     * Standard hashCode calculation for IDENTITY based references, where k
     * is the referent.  This may be stored in a final field.
     * 
     * int hash = 7;
     * hash = 29 * hash + System.identityHashCode(k);
     * hash = 29 * hash + k.getClass().hashCode();
     * 
     * Standard hashCode calculation for EQUALITY based references, where k
     * is the referent.
     * 
     * int hash = 7;
     * hash = 29 * hash + k.hashCode();
     * hash = 29 * hash + k.getClass().hashCode();
     * 
     * The hash must be calculated during construction and if the reference is
     * cleared, the recorded hashCode returned.  While the referent remains
     * reachable the hashCode must be calculated each time.
     * 
     * @return 
     */
    public int hashCode();
}
