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

import java.rmi.RemoteException;
import net.jini.io.CDCMarshalledObject;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;

/**
 *
 * @author Peter Firmstone
 */
public interface StreamingServiceRegistrar extends PortableServiceRegistrar{
    
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
     * @throws java.rmi.RemoteException
     */
    EventRegistration notify(ServiceTemplate tmpl,
			     int transitions,
			     RemoteEventListener listener,
			     CDCMarshalledObject handback,
			     long leaseDuration)
	throws RemoteException;
}
