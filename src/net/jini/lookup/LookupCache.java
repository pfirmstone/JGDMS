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

package net.jini.lookup;
  
import net.jini.core.lookup.ServiceItem;

/**
 * The <code>LookupCache</code> interface defines the methods provided
 * by the object created and returned by the 
 * {@link net.jini.lookup.ServiceDiscoveryManager ServiceDiscoveryManager}
 * when a client-like entity invokes the
 * {@link net.jini.lookup.ServiceDiscoveryManager#createLookupCache
 * createLookupCache} method. It is within the object returned by that
 * method that discovered service references, matching criteria defined
 * by the entity, are stored. Through this interface, the entity may
 * retrieve one or more of the stored service references, register and
 * un-register with the cache's event mechanism, discard previously
 * discovered service references to make them eligible for re-discovery,
 * and terminate all of the cache's processing.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see ServiceDiscoveryManager
 */
public interface LookupCache {
   /** 
     * Finds a <code>ServiceItem</code> object that satisfies the given
     * <code>filter</code> parameter. 
     * <p>
     * The service item returned must have been previously discovered to 
     * be both registered with one or more of the lookup services in the 
     * managed set, and to match criteria defined by the entity.
     * <p>
     * The semantics of the <code>filter</code> argument are identical
     * to those of the <code>filter</code> argument specified for a
     * number of the methods defined in the interface of the
     * <code>ServiceDiscoveryManager</code> utility class. This argument
     * is intended to allow an entity to separate its filtering into two
     * steps: an initial filter applied during the discovery phase, and
     * a finer resolution filter applied upon retrieval from the cache.
     * As with the methods of the <code>ServiceDiscoveryManager</code>, if
     * <code>null</code> is the value of this argument, then no additional
     * filtering will be performed.
     *
     * @param filter used for matching <code>ServiceItem</code>s. A null 
     * 		     value means no additional filtering should be applied.
     * 		    
     * @return ServiceItem that satisfies the filter, and that was 
     *                     previously discovered to be registered with one
     *                     or more lookup services in the managed set. A 
     *                     <code>null</code> value will be returned if no
     *                     <code>ServiceItem</code> is found that matches
     *                     the criteria or if the cache is empty.
     */
    public ServiceItem lookup(ServiceItemFilter filter);

   /** 
     * Finds an array of instances of <code>ServiceItem</code> that each
     * satisfy the given <code>filter</code> parameter.
     * <p>
     * Each service item contained in the returned array must have been
     * previously discovered to be both registered with one or more of the
     * lookup services in the managed set, and to match criteria defined
     * by the entity.
     * <p>
     * The semantics of the <code>filter</code> argument are
     * identical to those of the <code>filter</code> argument specified
     * for a number of the methods defined in the interface of the
     * <code>ServiceDiscoveryManager</code> utility class. This argument is
     * intended to allow an entity to separate its filtering into two
     * steps: an initial filter applied during the discovery phase, and
     * a finer resolution filter applied upon retrieval from the cache.
     * As with the methods of the <code>ServiceDiscoveryManager</code>, if
     * <code>null</code> is the value of this argument, then no
     * additional filtering will be performed.
     * 
     * @param filter         used for matching <code>ServiceItem</code>s.
     *                       A null value means no additional filtering should
     *                       be applied.
     * 		    
     * @param maxMatches     maximum number of matches to return. If this 
     *                       value is set to <code>Integer.MAX_VALUE</code>
     *                       then all elements in the cache that match the
     *                       criteria will be returned.
     *
     * @return ServiceItem[] array whose elements each satisfy the filter,
     *                       and that were previously discovered to be
     *                       registered with one or more lookup services in
     *                       the managed set. An empty array will be returned
     *                       if no <code>ServiceItem</code> is found that
     *                       matches the criteria or if the cache is empty.
     *
     * @throws java.lang.IllegalArgumentException if <code>maxMatches</code>
     *         is a negative number.
     */
    public ServiceItem[] lookup(ServiceItemFilter filter, int maxMatches);

   /** 
     * Registers a <code>ServiceDiscoveryListener</code> object with
     * the event mechanism of a <code>LookupCache</code>. The listener 
     * object will receive a <code>ServiceDiscoveryEvent</code> upon the 
     * discovery, removal, or modification of one of the cache's
     * services. Once a listener is registered, it will be notified of
     * all service references discovered to date, and will be notified as
     * new services are discovered and existing services are modified or
     * discarded. 
     *
     * If the parameter value duplicates (using <code>equals</code>) another
     * element in the set of listeners, no action is taken. If the parameter
     * value is <code>null</code>, a <code>NullPointerException</code> is
     * thrown.
     *
     * @param listener the <code>ServiceDiscoveryListener</code> object to
     * 		       register.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         <code>null</code> is input to the <code>listener</code>
     *         parameter.
     * @see #removeListener
     *
     */
    public void addListener(ServiceDiscoveryListener listener);

   /** 
     * Removes a <code>ServiceDiscoveryListener</code> object from the set  
     * of listeners currently registered with the <code>LookupCache</code>.
     * Once all listeners are removed from the cache's set of listeners, 
     * the cache will send no more <code>ServiceDiscoveryEvent</code> 
     * notifications. 
     *
     * If the parameter value is <code>null</code>, or if the parameter value
     * does not exist in the managed set of listeners, no action is taken.
     *
     * @param listener the <code>ServiceDiscoveryListener</code> object to
     *                 remove.
     * @see #addListener
     */
    public void removeListener(ServiceDiscoveryListener listener);

   /** 
     * Deletes a service reference from the cache and causes a notification 
     * to be sent to all registered listeners indicating that the service 
     * has been discarded.
     *
     * @param serviceReference the service reference to	discard.
     */
    public void discard(Object serviceReference);

   /** 
     * Performs cleanup duties related to the termination of
     * the processing being performed by a particular instance of
     * <code>LookupCache</code>. For that instance, this method cancels
     * all event leases granted by the lookup services that supplied the
     * contents of the cache, and un-exports all remote listener objects
     * registered with those lookup services. The <code>terminate</code>
     * method is typically called when the entity is no longer interested
     * in the contents of the <code>LookupCache</code>.
     */
    void terminate();
}
