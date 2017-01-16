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

import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.lookup.ServiceRegistrar;

import java.rmi.RemoteException;

/**
 * When a client requests a registration with a lookup discovery service,
 * an instance of this interface is returned. It is through this interface
 * that the client manages the parameters reflected in its registration
 * with the lookup discovery service.
 * <p>
 * This interface is not a remote interface; each implementation of the
 * lookup discovery service exports proxy objects that implement this
 * interface local to the client, using an implementation-specific protocol
 * to communicate with the actual remote server. The proxy methods obey
 * normal RMI remote interface semantics except where explicitly noted. Two
 * proxy objects are equal if they are proxies for the same registration
 * created by the same lookup discovery service.
 * <p>
 * If a client's registration with the lookup discovery service has expired
 * or been cancelled, then any invocation of a remote method defined in this
 * interface will result in a java.rmi.NoSuchObjectException.
 * <p>
 * Each remote method of this interface may throw a RemoteException.
 * Typically, this exception occurs when there is a communication failure
 * between the client's registration object and the lookup discovery service.
 * Whenever this exception occurs as a result of the invocation of one of
 * these methods, the method may or may not have completed its processing
 * successfully.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.lookup.ServiceRegistrar
 * @see net.jini.discovery.LookupDiscoveryService
 */
public interface LookupDiscoveryRegistration {

    /**
     * Returns an EventRegistration object that encapsulates the information
     * needed by the client to identify a notification sent by the lookup
     * discovery service to the registration's listener. This method is
     * not remote and takes no arguments.
     *
     * @return the EventRegistration for this registration.
     */
    public EventRegistration getEventRegistration();

    /**
     * Returns the Lease object that controls a client's registration with 
     * the lookup discovery service. It is through the object returned by
     * this method that the client can request the renewal or cancellation
     * of the registration with the lookup discovery service. This method is
     * not remote and takes no arguments.
     * <p>
     * Note that the object returned by the getEventRegistration method also
     * provides a getLease method. That method and this method both return
     * the same Lease object. This method is provided as a convenience to
     * avoid the indirection associated with the getLease method on the
     * EventRegistration object, as well as to avoid the overhead of making
     * two method calls when retrieving the lease.
     * 
     * @return the Lease on this registration.
     */
    public Lease getLease();

    /**
     * Returns an array consisting of instances of the ServiceRegistrar
     * interface. Each element in the returned set is a proxy to one of
     * lookup service(s) that have already been discovered for this
     * registration. The contents of the returned set make up the 'remote
     * state' of this registration's currently discovered lookup service(s).
     * This method returns a new array on each invocation.
     * <p>
     * This method can be used to maintain synchronization between the set
     * of discovered lookup services making up a registration's local state
     * on the client and the registration's corresponding remote state
     * maintained by the lookup discovery service. The local state can
     * become un-synchronized with the remote state when a gap occurs in
     * the events received by the registration's listener.
     * <p>
     * According to the event semantics, if there is no gap between two
     * sequence numbers, no events have been missed and the states remain
     * synchronized; if there is a gap, events may or may not have been
     * missed. Thus, upon finding gaps in the sequence of events, the
     * client can invoke this method and use the returned information to
     * synchronize the local state with the remote state.
     * <p>
     * This method requests that the lookup discovery service return the set
     * of proxies to the lookup services currently discovered for the
     * the particular registration object on which this method is invoked.
     * When the lookup discovery service receives such a request, it sends
     * the requested set of proxies as a set of marshalled instances of the
     * ServiceRegistrar interface. Thus, in order to construct the return
     * set, this method attempts to unmarshal each element of the set
     * received from the lookup discovery service. Should a failure occur
     * while attempting to unmarshal any of the elements of the received
     * set of marshalled proxy objects, this method will throw an exception
     * of type LookupUnmarshalException. 
     * <p>
     * When a LookupUnmarshalException is thrown by this method, the
     * contents of the exception provides the client with the following
     * useful information: (1) the knowledge that a problem has occurred
     * while unmarshalling at least one of the elements making up the
     * remote state of this registration's discovered lookup service(s),
     * (2) the set consisting of the proxy objects that were successfully
     * unmarshalled by this method, (3) the set consisting of the marshalled
     * proxy objects that could not be unmarshalled by this method, and
     * (4) the set of exceptions corresponding to each failed attempt at
     * unmarshalling.
     * <p>
     * Typically, the type of exception that occurs when attempting to
     * unmarshal an element of the set of marshalled proxies is either an
     * IOException or a ClassNotFoundException. A ClassNotFoundException 
     * occurs whenever a remote field of the marshalled proxy cannot be
     * retrieved (usually because the codebase of one of the field's classes
     * or interfaces is currently 'down'). To address this situation, the
     * client may wish to proceed with its processing using the successfully
     * unmarshalled proxies; and attempt to unmarshal the unavailable proxies
     * (or re-invoke this method) at some later time.
     * <p>
     * Note that if this method returns successfully without throwing a
     * LookupUnmarshalException, the client is guaranteed that all
     * marshalled proxies returned to this method by the lookup discovery
     * service have been successfully unmarshalled; and the client then
     * has a snapshot - relative to the point in time when this method
     * is invoked - of the remote state of the lookup service(s) discovered
     * for this registration.
     * 
     * @return an array of ServiceRegistrar objects.
     * 
     * @throws net.jini.discovery.LookupUnmarshalException this exception
     *         is thrown when failure occurs while attempting to unmarshal
     *         one or more of the marshalled instances of ServiceRegistrar
     *         received from the lookup discovery service.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     */
    public ServiceRegistrar[] getRegistrars() throws LookupUnmarshalException,
                                                     RemoteException;

    /**
     * Returns an array consisting of the names of the groups whose members
     * are lookup services the lookup discovery service will attempt to
     * discover for this registration. This set of group names is referred
     * to as the registration's 'managed set of groups'.
     * <p>
     * If the registration's managed set of groups is currently empty, this
     * method will return the empty array. If the lookup discovery service
     * has no managed set of groups associated with this registration, this
     * method will return null.
     * 
     * @return a String array containing the elements of the managed set of
     *         groups for the registration.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     * @see #setGroups
     */
    public String[] getGroups()  throws RemoteException;

    /**
     * Returns an array consisting of the LookupLocator objects corresponding
     * to specific lookup services the lookup discovery service will attempt
     * to discover for this registration. This set of locators is referred
     * to as the registration's 'managed set of locators'.
     * <p>
     * If the registration's managed set of locators is currently empty, this
     * method will return the empty array. If the lookup discovery service
     * has no managed set of locators associated with this registration,
     * this method will return null.
     * 
     * @return array consisting of net.jini.core.discovery.LookupLocator
     *         objects corresponding to the elements of the managed set of
     *         locators for the registration.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     * @see #setLocators
     */
    public LookupLocator[] getLocators()  throws RemoteException;

    /**
     * Adds a set of group names to the managed set of groups associated
     * with this registration.
     * 
     * @param groups a String array, none of whose elements may be null,
     *               consisting of the group names with which to augment this
     *               registration's managed set of groups.
     * <p>
     *               If any element of this parameter duplicates any other
     *               element of this parameter, the duplicate will be ignored.
     *               If any element of this parameter duplicates any element
     *               of the registration's current managed set of groups,
     *               the duplicate will be ignored.
     * <p>
     *               If the empty set (equivalent to
     *               net.jini.discovery.LookupDiscovery.NO_GROUPS) is input,
     *               then this registration's managed set of groups will not
     *               change.
     * <p>
     *               If null (equivalent to
     *               net.jini.discovery.LookupDiscovery.ALL_GROUPS) is
     *               input, this method will throw a NullPointerException.
     * 
     * @throws java.lang.UnsupportedOperationException this exception occurs
     *         when the lookup discovery service has no managed set of groups
     *         associated with this registration.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null (net.jini.discovery.LookupDiscovery.ALL_GROUPS)
     *         is input to the groups parameter, or one or more of the
     *         elements of the groups parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, this
     *         registration's managed set of groups may or may not have been
     *         successfully augmented.
     * @see #removeGroups
     */
    public void addGroups(String[] groups)  throws RemoteException;

    /**
     * Replaces all of the group names in this registration's managed set of
     * groups with new set of names.
     * <p>
     * Once a new group name has been placed in the managed set, if there are
     * lookup services belonging to that group that have already been
     * discovered, no event will be sent to this registration's listener for
     * those particular lookup services. Attempts to discover all as yet
     * undiscovered lookup services belonging to that group will continue
     * to be made for this registration.
     * <p>
     * After an invocation of this method results in the removal (due to
     * replacement) of one or more group names from the registration's
     * managed set of groups, attempts to discover any lookup service that
     * meets all of the following criteria will cease to be made for this
     * registration: the lookup service is a member of one or more of the
     * removed group(s), but that lookup service is neither a member of
     * any group in the resulting managed set of groups, nor does that
     * lookup service correspond to any element in the registration's
     * managed set of locators.
     * 
     * @param groups a String array, none of whose elements may be null,
     *               consisting of the group names with which to replace the
     *               names in this registration's managed set of groups.
     * <p>
     *               If any element of this parameter duplicates any other
     *               element of this parameter, the duplicate will be ignored.
     * <p>
     *               If the empty set (equivalent to
     *               net.jini.discovery.LookupDiscovery.NO_GROUPS) is input,
     *               then group discovery for this registration will cease.
     * <p>
     *               If null (equivalent to
     *               net.jini.discovery.LookupDiscovery.ALL_GROUPS) is
     *               input, the lookup discovery service will attempt to
     *               discover all as yet undiscovered lookup services located
     *               within its multicast radius and, upon discovery of any
     *               such lookup service, will send to this registration's
     *               listener an event signaling that discovery.
     * 
     * @throws java.lang.NullPointerException this exception occurs when one
     *         or more of the elements of the groups parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, this
     *         registration's managed set of groups may or may not have been
     *         successfully replaced.
     * @see #getGroups
     */
    public void setGroups(String[] groups)  throws RemoteException;

    /**
     * Deletes a set of group names from this registration's managed set of
     * groups.
     * <p>
     * After an invocation of this method results in the removal of one
     * or more group names from the registration's managed set of groups,
     * attempts to discover any lookup service that satisfies the following
     * condition will cease to be made for this registration: the lookup 
     * service is a member of one or more of the removed group(s), but that
     * lookup service is neither a member of any group in the resulting
     * managed set of groups, nor does that lookup service correspond to
     * any element in the registration's managed set of locators.
     * 
     * @param groups a String array, none of whose elements may be null,
     *               consisting of the group names to delete from this
     *               registration's managed set of groups.
     * <p>
     *               If any element of this parameter duplicates any other
     *               element of this parameter, the duplicate will be ignored.
     * <p>
     *               If the empty set (equivalent to
     *               net.jini.discovery.LookupDiscovery.NO_GROUPS) is input,
     *               the registration's managed set of groups will not change.
     * <p>
     *               If null (equivalent to
     *               net.jini.discovery.LookupDiscovery.ALL_GROUPS) is
     *               input, this method will throw a NullPointerException.
     * 
     * @throws java.lang.UnsupportedOperationException this exception occurs
     *         when the lookup discovery service has no managed set of groups
     *         associated with this registration.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null (net.jini.discovery.LookupDiscovery.ALL_GROUPS)
     *         is input to the groups parameter, or one or more of the
     *         elements of the groups parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, this
     *         registration's managed set of groups may or may not have been
     *         successfully modified.
     * @see #addGroups
     */
    public void removeGroups(String[] groups)  throws RemoteException;

    /**
     * Adds a set of LookupLocator objects to the managed set of locators
     * associated with this registration.
     * <p>
     * For any new locator placed in the registration's managed set of
     * locators as a result of an invocation of this method, if that
     * locator equals no other locator corresponding to a previously
     * discovered lookup service (across all registrations), the lookup
     * discovery service will attempt unicast discovery of the lookup
     * service associated with the new locator.
     * 
     * @param locators an array, none of whose elements may be null, consisting
     *                 of the LookupLocator objects with which to augment
     *                 this registration's managed set of locators.
     * <p>
     *                 If any element of this parameter duplicates any other
     *                 element of this parameter, the duplicate will be
     *                 ignored. If any element of this parameter duplicates
     *                 any element of this registration's managed set of
     *                 locators, the duplicate will be ignored.
     * <p>
     *                 If the empty array is input, then this registration's
     *                 managed set of locators will not change. If null is
     *                 input, this method will throw a NullPointerException.
     * 
     * @throws java.lang.UnsupportedOperationException this exception occurs
     *         when the lookup discovery service has no managed set of
     *         locators associated with this registration.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the locators parameter, or one or
     *         more of the elements of the locators parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, this
     *         registration's managed set of locators may or may not have
     *         been successfully augmented.
     * @see #removeLocators
     */
    public void addLocators(LookupLocator[] locators)  throws RemoteException;

    /**
     * Replaces all of the locators in this registration's managed set of
     * locators with a new set of LookupLocator objects.
     * <p>
     * For any new locator placed in the managed set of locators as a result
     * of an invocation of this method, if that locator equals no other
     * locator corresponding to a previously discovered lookup service
     * (across all registrations), the lookup discovery service will attempt
     * unicast discovery of the lookup service associated with the new locator.
     * <p>
     * After an invocation of this method results in the removal (due to
     * replacement) of a locator from the registration's managed set of
     * locators, the action taken by the lookup discovery service can be
     * described as follows: if the lookup service corresponding to the
     * removed locator has yet to be discovered for this registration, 
     * attempts to perform locator discovery of that lookup service will
     * cease; if that lookup service has already been discovered for this
     * registration through locator discovery, but not through group 
     * discovery, the lookup service will be discarded.
     * 
     * @param locators an array, none of whose elements may be null, consisting
     *                 of the LookupLocator objects with which to replace the
     *                 locators in this registration's managed set of locators.
     * <p>
     *                 If any element of this parameter duplicates any other
     *                 element of this parameter, the duplicate will be
     *                 ignored.
     * <p>
     *                 If the empty array is input, then locator discovery for
     *                 this registration will cease. If null is input, this
     *                 method will throw a NullPointerException.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the locators parameter, or one or
     *         more of the elements of the locators parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, this
     *         registration's managed set of locators may or may not have
     *         been successfully replaced.
     * @see #getLocators
     */
    public void setLocators(LookupLocator[] locators)  throws RemoteException;

    /**
     * Deletes a set of of LookupLocator objects from this registration's
     * managed set of locators.
     * <p>
     * After an invocation of this method results in the removal of a
     * locator from the registration's managed set of locators, the action
     * taken by the lookup discovery service can be described as follows:
     * if the lookup service corresponding to the removed locator has yet
     * to be discovered for this registration, attempts to perform locator
     * discovery of that lookup service will cease; if that lookup service
     * has already been discovered for this registration through locator
     * discovery, but not through group discovery, the lookup service will
     * be discarded.
     * 
     * @param locators an array, none of whose elements may be null, consisting
     *                 of the LookupLocator objects to remove from this
     *                 registration's managed set of locators.
     * <p>
     *                 If any element of this parameter duplicates any other
     *                 element of this parameter, the duplicate will be
     *                 ignored.
     * <p>
     *                 If the empty set is input, the managed set of locators
     *                 will not change. If null is input, this method will
     *                 throw a NullPointerException.
     * 
     * @throws java.lang.UnsupportedOperationException this exception occurs
     *         when the lookup discovery service has no managed set of
     *         locators associated with this registration.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the locators parameter, or one or
     *         more of the elements of the locators parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, this
     *         registration's managed set of locators may or may not have
     *         been successfully modified.
     * @see #addLocators
     */
    public void removeLocators(LookupLocator[] locators)
                                                       throws RemoteException;

    /**
     * Informs the lookup discovery service of the existence of an 
     * unavailable lookup service and requests that the lookup discovery
     * service discard the unavailable lookup service.
     * <p>
     * When the lookup discovery service removes an already-discovered lookup
     * service from a registration's managed set of lookup services and
     * makes the lookup service eligible for re-discovery, the lookup service
     * is considered to be "discarded". There are a number of situations where
     * the lookup discovery service will discard a lookup service.
     * <p>
     * The lookup discovery service will discard a lookup service in response
     * to a discard request resulting from an invocation of this method.
     * <p>
     * The lookup discovery service will discard a lookup service when the
     * lookup service - previously discovered through locator discovery - is
     * removed from a registration's managed set of locators in response to
     * an invocation of either the setLocators method or the removeLocators
     * method.
     * <p>
     * The lookup discovery service will discard a lookup service when the
     * multicast announcements from an already-discovered lookup service
     * are no longer being received.
     * <p>
     * Whenever the lookup discovery service discards a lookup service
     * previously discovered for this registration, it will send an event
     * to this registration's listener indicating that the lookup service
     * has been discarded.
     * <p>
     * Note that if a lookup service crashes or is unavailable for some
     * reason, there will be no automatic notification of the occurrence
     * of such an event. This means that for each of a registration's
     * targeted lookup services, after a lookup service is initially
     * discovered, the lookup discovery service will not attempt to discover
     * that lookup service again (for that registration) until that lookup
     * service is discarded.
     * <p>
     * Thus, when a client determines that lookup service discovered for a
     * registration is no longer available, it is the responsibility of the
     * client to inform the lookup discovery service - through the invocation
     * of this method - that the previously discovered lookup service is no
     * longer available, and that attempts should be made to re-discover that
     * lookup service for the registration. Typically, a client determines
     * that a lookup service is unavailable when the client attempts to use
     * the lookup service but receives a non-fatal exception or error 
     * (for example, a RemoteException) as a result of the attempt.
     * 
     * @param registrar a reference to the lookup service that the lookup
     *                  discovery service is being asked to discard.
     * <p>
     *                  If this parameter equals none of the lookup services
     *                  contained in the managed set of lookup services for
     *                  this registration, no action will be taken.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         null is input to the registrar parameter.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, 
     *         the lookup service may or may not have been successfully
     *         discarded.
     */
    public void discard(ServiceRegistrar registrar)  throws RemoteException;
}
