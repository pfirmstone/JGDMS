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
package net.jini.core.lookup;

import java.io.ObjectInput;
import java.rmi.RemoteException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.io.MarshalledInstance;

/**
 * Defines the interface to the lookup service.  The interface is not a
 * remote interface; each implementation of the lookup service exports
 * proxy objects that implement the StreamServiceRegistrar interface local to
 * the client, using an implementation-specific protocol to communicate
 * with the actual remote server.  All of the proxy methods obey normal
 * RMI remote interface semantics except where explicitly noted.  Two
 * proxy objects are equal if they are proxies for the same lookup service.
 * Every method invocation (on both StreamServiceRegistrar and ServiceRegistration)
 * is atomic with respect to other invocations.
 * 
 * The StreamServiceRegistrar is intended to perform the same function
 * as the ServiceRegistrar, but with the ability to return results as a 
 * stream, so memory consumption can be minimised at the client.
 * 
 * All clients utilising ServiceRegistrar, should switch to the 
 * StreamServiceRegistrar.
 * 
 * @see ServiceRegistrar
 * @see PortableServiceRegistrar
 * @see ServiceRegistration
 * @author Peter Firmstone
 * @since 2.2.0
 */
public interface StreamServiceRegistrar extends PortableServiceRegistrar{
    
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
      * <p>
      * The method signature varies slightly from ServiceRegistar in case
      * a class implements both method signatures and a caller used a null
      * MarshalledInstance, the call would be ambiguious.
     *
     * @param tmpl template to match
     * @param transitions bitwise OR of any non-empty set of transition values
     * @param listener listener to send events to
     * @param handback object to include in every ServiceEvent generated
     * @param leaseDuration requested lease duration
     * @return an EventRegistration object to the entity that registered the
     *         specified remote listener
     * @throws java.rmi.RemoteException
     * @since 2.2.0
     */
    EventRegistration notify(MarshalledInstance handback,
                             ServiceTemplate tmpl,
			     int transitions,
			     RemoteEventListener listener,
			     long leaseDuration)
	throws RemoteException;

    /**
     * Returns the service object (i.e., just ServiceItem.service) from an
     * item matching the template.  It makes the service object available via
     * the returned ObjectInput.  
     * 
     * If the returned object cannot be deserialized, it can be returned in
     * marshalled form as a MarshalledInstance.
     * 
     * Implementations of this interface should return the Objects in order of
     * their package implementation version, so that the number of ClassLoaders
     * are minimised and common packages can share code.  This is intended to
     * be used with the new codebase services (TODO once implemented).
     * 
     * ObjectInput should be an InputStream, in order to minimise
     * memory consumption requirements at the client.
     *
     * @param tmpl template to match
     * @param marshalled if true return objects in marshalled form.
     * @return an object input that represents a service that matches the
     * specified template
     * @throws java.rmi.RemoteException
     * @see MarshalledInstance
     * @since 2.2.0
     */
    ObjectInput lookup(ServiceTemplate tmpl, boolean marshalled) throws RemoteException;
}
