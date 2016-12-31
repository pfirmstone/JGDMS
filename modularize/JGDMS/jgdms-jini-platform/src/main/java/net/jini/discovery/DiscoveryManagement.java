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

package net.jini.discovery;

import net.jini.discovery.DiscoveryListener;
import net.jini.core.lookup.ServiceRegistrar;

/**
 * This interface defines methods related to the discovery event mechanism
 * and discovery process termination. Through this interface an entity can
 * register or un-register for discovery events, discard a lookup service,
 * or terminate the discovery process.
 *
 * @author Sun Microsystems, Inc.
 */
public interface DiscoveryManagement {

    /**
     * Adds an instance of <code>DiscoveryListener</code> to the set of
     * objects listening for discovery events. Once the listener is
     * registered, it will be notified of all lookup services discovered
     * to date, and will then be notified as new lookup services are
     * discovered or existing lookup services are discarded.
     * <p>
     * If <code>null</code> is input to this method, a
     * <code>NullPointerException</code> is thrown. If the listener
     * input to this method duplicates (using the <code>equals</code>
     * method) another element in the current set of listeners, no action
     * is taken.
     *
     * @param listener an instance of <code>DiscoveryListener</code> 
     *                 corresponding to the listener to add to the set of
     *                 listeners.
     *
     * @throws java.lang.NullPointerException if <code>null</code> is
     *         input to the <code>listener</code> parameter
     *
     * @see #removeDiscoveryListener
     * @see net.jini.discovery.DiscoveryListener
     */
    public void addDiscoveryListener(DiscoveryListener listener);

    /**
     * Removes a listener from the set of objects listening for discovery
     * events. If the listener object input to this method does not exist
     * in the set of listeners, then this method will take no action.
     *
     * @param listener an instance of <code>DiscoveryListener</code>
     *                 corresponding to the listener to remove from the set
     *                 of listeners.
     *
     * @see #addDiscoveryListener
     * @see net.jini.discovery.DiscoveryListener
     */
    public void removeDiscoveryListener(DiscoveryListener listener);

    /**
     * Returns an array of instances of <code>ServiceRegistrar</code>, each
     * corresponding to a proxy to one of the currently discovered lookup
     * services. For each invocation of this method, a new array is returned.
     *
     * @return array of instances of <code>ServiceRegistrar</code>, each
     *         corresponding to a proxy to one of the currently discovered
     *         lookup services
     *
     * @see net.jini.core.lookup.ServiceRegistrar
     */
    public ServiceRegistrar[] getRegistrars();

    /**
     * Removes an instance of <code>ServiceRegistrar</code> from the
     * managed set of lookup services, making the corresponding lookup
     * service eligible for re-discovery. This method takes no action if
     * the parameter input to this method is <code>null</code>, or if it
     * does not match (using <code>equals</code>) any of the elements in
     * the managed set.
     *
     * @param proxy the instance of <code>ServiceRegistrar</code> to remove
     *              from the managed set of lookup services
     *
     * @see net.jini.core.lookup.ServiceRegistrar
     */
   public void discard(ServiceRegistrar proxy);

    /**
     * Ends all discovery processing being performed by the current
     * implementation of this interface. After this method is invoked,
     * no new lookup services will be discovered, and the effect of any
     * new operations performed on the current implementation object are
     * undefined. Any additional termination semantics must be defined
     * by the implementation class itself.
     */
    public void terminate();
}
