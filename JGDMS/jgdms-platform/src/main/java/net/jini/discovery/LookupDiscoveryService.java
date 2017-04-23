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

import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.RemoteEventListener;

/**
 * Defines the interface to the lookup discovery service. This interface is 
 * not a remote interface; each implementation of this service exports
 * proxy objects that implement the LookupDiscoveryService interface local to
 * the client, using an implementation-specific protocol to communicate
 * with the actual remote server. All of the proxy methods obey normal
 * RMI remote interface semantics except where explicitly noted. Two
 * proxy objects are equal if they are proxies for the same lookup discovery
 * service.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.discovery.LookupDiscoveryRegistration
 */
public interface LookupDiscoveryService {

    /**
     * Register with the lookup discovery service. When a client invokes
     * this method, it requests that the lookup discovery service perform
     * discovery processing on its behalf.
     * <p>
     * An invocation of this method produces an object - referred to as a
     * registration object (or simply, a registration) - that is mutable.
     * Because the registration produced by this method is mutable, each
     * invocation of this method produces a new registration object. Thus,
     * this method is not idempotent.
     * <p>
     * To register with the lookup discovery service, the client must
     * indicate the lookup services it is interested in discovering. It
     * does this by submitting two sets of of objects. One set consists
     * of the names of the groups whose members are lookup services the
     * client wishes to be discovered. The other set consists of 
     * LookupLocator objects, each corresponding to a specific lookup
     * service the client wishes to be discovered. The state information
     * managed by the lookup discovery service contains no knowledge of
     * the clients that register. Thus, there is no requirement that the
     * client identify itself during the registration process.
     * <p>
     * Registration with the lookup discovery service includes registration
     * with the event mechanism of the lookup discovery service. That is,
     * for each registration created as a result of an invocation of this
     * method, an event identifier will be generated that uniquely maps
     * the registration to the listener object and to the set of groups
     * and locators input to this method. This event identifier is returned
     * as a part of the registration object, and is unique across all
     * other active registrations with the lookup discovery service.
     * <p>
     * While the registration is in effect, whenever the lookup discovery
     * service finds a lookup service matching the discovery criteria of
     * one or more of its registrations, an instance of 
     * net.jini.discovery.RemoteDiscoveryEvent will be sent to the
     * listener corresponding to each such registration. The event sent
     * to each listener will contain the appropriate event identifier.
     * <p>
     * Any registration granted as a result of an invocation of this method
     * is leased. The initial duration of the lease granted to a client
     * by the lookup discovery service will be less than or equal to the
     * requested duration input to this method. Any registration with the
     * lookup discovery service is persistent across restarts (crashes) 
     * of the lookup discovery service until the lease on the registration
     * expires or is cancelled.
     *
     * @param groups        String array, none of whose elements may be null,
     *                      consisting of zero or more names of groups to
     *                      which lookup services to discover belong.
     *                      A value of null
     *                      (net.jini.discovery.LookupDiscovery.ALL_GROUPS)
     *                      is acceptable. If null is passed to this argument,
     *                      the lookup discovery service will attempt to
     *                      discover all lookup services located within the
     *                      multicast radius of the host on which the lookup
     *                      discovery service is running. If an empty array
     *                      (net.jini.discovery.LookupDiscovery.NO_GROUPS)
     *                      is passed to this argument, then no group discovery
     *                      will be performed for the associated registration
     *                      until the client, through one of the registration's
     *                      methods, populates the managed set of groups.
     *                      
     * @param locators      array of zero or more non-null LookupLocator
     *                      objects, each corresponding to a specific lookup
     *                      service to discover. If either the empty array
     *                      or null is passed to this argument, then no
     *                      locator discovery will be performed for the
     *                      associated registration until the client, through
     *                      one of the registration's methods, populates the
     *                      managed set of locators.
     *                      
     * @param listener      a non-null instance of RemoteEventListener. This 
     *                      argument specifies the entity that will receive
     *                      events notifying the registration that a lookup
     *                      service of interest has been discovered. A 
     *                      non-null value must be passed to this argument,
     *                      otherwise a NullPointerException will be thrown
     *                      and the registration will not succeed.
     *                      
     * @param handback      null or an instance of MarshalledObject. This
     *                      argument specifies an object that will be 
     *                      included in the notification event that the
     *                      lookup discovery service sends to the registered
     *                      listener.
     *                      
     * @param leaseDuration long value representing the amount of time (in
     *                      milliseconds) for which the resources of the
     *                      lookup discovery service are being requested.
     *                      
     * @return an instance of the LookupDiscoveryRegistration interface.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration may or may not have completed successfully.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         null is input to the <code>listener</code> parameter, as well
     *         as when one or more of the elements of the <code>groups</code>
     *         parameter is null.
     *
     * @throws java.lang.IllegalArgumentException this exception occurs when
     *         the value input to the <code>leaseDuration</code> parameter
     *         is neither positive, Lease.FOREVER, nor Lease.ANY.
     * @deprecated use {@link LookupDiscoveryServiceSafely#register(java.lang.String[], net.jini.core.discovery.LookupLocator[], net.jini.core.event.RemoteEventListener, net.jini.io.MarshalledInstance, long) }
     */
    @Deprecated 
    public LookupDiscoveryRegistration register(String[] groups,
                                                LookupLocator[] locators,
                                                RemoteEventListener listener,
                                                MarshalledObject handback,
                                                long leaseDuration)
                                                        throws RemoteException;
}
