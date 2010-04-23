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

import net.jini.core.lookup.PortableServiceRegistrar;
import net.jini.discovery.DiscoveryListener;

/**
 * This interface defines methods related to the discovery event mechanism
 * and discovery process termination. Through this interface an entity can
 * register or un-register for discovery events, discard a lookup service,
 * or terminate the discovery process.
 *
 * @author Peter Firmstone.
 * @since 2.2.0
 */
public interface DiscoveryManagement2 extends DiscoveryListenerManagement {

    /**
     * Returns an array of instances of <code>PortableServiceRegistrar</code>, each
     * corresponding to a proxy to one of the currently discovered lookup
     * services. For each invocation of this method, a new array is returned.
     *
     * @return array of instances of <code>ServiceRegistrar</code>, each
     *         corresponding to a proxy to one of the currently discovered
     *         lookup services
     *
     * @see net.jini.core.lookup.ServiceRegistrar
     */
    public PortableServiceRegistrar[] getPRegistrars();

    /**
     * Removes an instance of <code>ServiceRegistrar</code> from the
     * managed set of lookup services, making the corresponding lookup
     * service eligible for re-discovery. This method takes no action if
     * the parameter input to this method is <code>null</code>, or if it
     * does not match (using <code>equals</code>) any of the elements in
     * the managed set.
     * 
     * Changing the Parameter to PortableServiceRegistrar doesn't break client
     * code, but it may break implementation code if that code utilises
     * the nofify method from ServiceRegistrar
     * 
     * Implementer beware check for Facade and unwrap before using equals
     * comparison to remove.
     *
     * @param proxy the instance of <code>PortableServiceRegistrar</code> to remove
     *              from the managed set of lookup services
     *
     * @see net.jini.core.lookup.PortableServiceRegistrar
     * @see net.jini.core.lookup.ServiceRegistrar
     * @see net.jini.core.lookup.StreamServiceRegistrar
     * @see net.jini.core.lookup.Facade;
     */
   public void discard(PortableServiceRegistrar proxy);
}
