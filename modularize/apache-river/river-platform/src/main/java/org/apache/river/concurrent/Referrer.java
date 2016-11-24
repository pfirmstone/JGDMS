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

/**
 * <p>
 * The public API of package private Reference implementations, it defines the equals
 * and hashCode contracts as well as methods identical to Reference.
 * </p>
 * @see Reference
 * @see Ref
 * @param <T> Referent
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
     * @return true if successfully enqueued.
     */
    public boolean enqueue();
    
    /**
     * <p>
     * Equals is calculated on IDENTITY or EQUALITY.
     * </p>
     * IDENTITY calculation:<br>
     * <p><CODE>
     * if (this == o) return true;<br>
     * if (!(o instanceof Referrer)) return false;<br>
     * Object k1 = get();<br>
     * Object k2 = ((Referrer) o).get();<br>
     * if ( k1 != null &amp;&amp; k1 == k2 ) return true;<br>
     * return ( k1 == null &amp;&amp; k2 == null &amp;&amp; hashCode() == o.hashCode());<br>
     * </CODE>
     * <p>
     * EQUALITY calculation:<br>
     * <p><CODE>
     * if (this == o)  return true; // Same reference.<br>
     * if (!(o instanceof Referrer))  return false;<br>
     * Object k1 = get();<br>
     * Object k2 = ((Referrer) o).get();<br>
     * if ( k1 != null &amp;&amp; k1.equals(k2)) return true;<br>
     * return ( k1 == null &amp;&amp; k2 == null &amp;&amp; hashCode() == o.hashCode());<br>
     * </CODE>
     *
     * @see Ref
     * @param o
     * @return true if equal
     */
    public boolean equals(Object o);
    
    /**
     * <p>
     * Standard hashCode calculation for IDENTITY based references, where k
     * is the referent.  This may be stored in a final field:
     * </p>
     * <BR><CODE>
     * int hash = 7;<BR>
     * hash = 29 * hash + System.identityHashCode(k);<BR>
     * hash = 29 * hash + k.getClass().hashCode();<BR>
     * </CODE>
     * <BR>
     * <p>
     * Standard hashCode calculation for EQUALITY based references, where k
     * is the referent:
     * </p>
     * <BR><CODE>
     * int hash = 7;<BR>
     * hash = 29 * hash + k.hashCode();<BR>
     * hash = 29 * hash + k.getClass().hashCode();<BR>
     * </CODE>
     * <BR>
     * <p>
     * The hash must be calculated during construction and if the reference is
     * cleared, the recorded hashCode returned.  While the referent remains
     * reachable the hashCode must be calculated each time.
     * </p>
     * @return hash
     */
    public int hashCode();
}
