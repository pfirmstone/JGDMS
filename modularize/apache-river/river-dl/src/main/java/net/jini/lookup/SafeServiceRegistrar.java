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

import java.rmi.RemoteException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.io.MarshalledInstance;

/**
 * ServiceRegistrar that provides safe lookup and notify methods to allow
 * authentication prior to download and delayed un-marshaling.
 * 
 * @since 4.0
 */
public interface SafeServiceRegistrar extends ServiceRegistrar{
    /**
     * Returns an array with a maximum length of maxProxies, containing bootstrap
     * proxies matching the template with service proxies that are likely to 
     * be compatible with the clients constraints. 
     * Bootstrap proxies returned are instances of RemoteMethodControl,
     * ServiceProxyAccessor and ServiceAttributesAccessor.  The bootstrap proxy
     * is used as a token to authenticate the service, prior to dynamically 
     * granting permissions, local attribute filtering and ultimately
     * download of the service proxy codebase and unmarshalling of a service 
     * smart proxy.  Only in the case of a smart proxy, will the Bootstrap proxy
     * implement ServiceCodebaseAccessor, which is an optional interface.
     * 
     * For this method to be secure, the client must use {@link AtomicInputValidation},
     * {@link ConfidentialityStrength, ConfidentialityStrength#STRONG} and
     * {@link Integrity}
     * 
     * @param tmpl
     * @param maxProxies
     * @return an array of bootstrap proxies, that implement 
     * RemoteMethodControl, ServiceProxyAccessor and ServiceAttributesAccessor
     * @throws RemoteException 
     * @see net.jini.core.constraint.RemoteMethodControl
     * @see net.jini.export.ServiceProxyAccessor
     * @see net.jini.export.ServiceAttributesAccessor
     * @see net.jini.export.ServiceCodebaseAccessor
     */
    Object [] lookUp(ServiceTemplate tmpl, int maxProxies) throws RemoteException;
    
    /**
     * Registers for event notification.  The registration is leased; the
     * lease expiration request is not exact.  The registration is persistent
     * across restarts (crashes) of the lookup service until the lease expires
     * or is cancelled.  The event ID in the returned EventRegistration is
     * unique at least with respect to all other active event registrations
     * in this lookup service with different service templates or transitions.
     * <p>
     * While the event registration is in effect, a ServiceEvent is sent to
     * the specified listener whenever a register, lease cancellation or
     * expiration, or attribute change operation results in an item changing
     * state in a way that satisfies the template and transition combination.
     *
     * @param tmpl template to match
     * @param transitions bitwise OR of any non-empty set of transition values
     * @param listener listener to send events to
     * @param handback object to include in every ServiceEvent generated
     * @param leaseDuration requested lease duration
     * @return an EventRegistration object to the entity that registered the
     *         specified remote listener
     * @throws java.rmi.RemoteException if a connection problem occurs.
     */
    EventRegistration notiFy(ServiceTemplate tmpl,
			    int transitions,
			    RemoteEventListener listener,
			    MarshalledInstance handback,
			    long leaseDuration) throws RemoteException ;
}
