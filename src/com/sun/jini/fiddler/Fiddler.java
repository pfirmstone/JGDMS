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
package com.sun.jini.fiddler;

import net.jini.discovery.LookupDiscoveryRegistration;

import com.sun.jini.proxy.ThrowThis;
import com.sun.jini.start.ServiceProxyAccessor;
import net.jini.id.Uuid;

import net.jini.admin.Administrable;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceRegistrar;

import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This interface defines the private protocol between the client-side
 * proxy and the server of a lookup discovery service.
 * <p>
 * The declared methods mirror the methods of the LookupDiscoveryService 
 * interface. Classes that act as a "smart proxy" to the lookup discovery
 * service typically contain a field which implements this interface. 
 * When the lookup discovery service registers with a lookup service,
 * the proxy - containing a "server field" that is an instance of this
 * interface - is registered. It is through the server field that the
 * proxy interacts with the back-end server of the lookup discovery service,
 * on behalf of a client, using the private protocol defined by a class
 * that implements this interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
interface Fiddler extends Remote, Administrable, FiddlerAdmin,
                          ServiceProxyAccessor
{
    /**
     * Returns the unique identifier generated (or recovered) by the back-end
     * implementation of the lookup discovery service when an instance of
     * that service is constructed. This ID is typically used to determine
     * equality between the proxies of any two instances of the lookup
     * discovery service.
     * 
     * @return the unique ID that was generated (or recovered) by the
     *         back-end implementation of the lookup discovery service
     *         at creation time
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     */
    Uuid getProxyID() throws RemoteException;

    /**
     * On behalf of a requesting client, creates a registration with the
     * lookup discovery service, which then performs discovery processing
     * on behalf of the client.
     *
     * @param groups        String array, none of whose elements may be null,
     *                      consisting of zero or more names of groups to
     *                      which lookup services to discover belong.
     *                      
     * @param locators      array of zero or more non-null LookupLocator
     *                      objects, each corresponding to a specific lookup
     *                      service to discover.
     *                      
     * @param listener      a non-null instance of RemoteEventListener. This 
     *                      argument specifies the entity that will receive
     *                      events notifying the registration that a lookup
     *                      service of interest has been discovered. 
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
     * @see net.jini.discovery.LookupDiscoveryService#register
     */
    LookupDiscoveryRegistration register(String[] groups,
                                         LookupLocator[] locators,
                                         RemoteEventListener listener,
                                         MarshalledObject handback,
                                         long leaseDuration)
                                                       throws RemoteException;

    /**
     * Returns an array consisting of instances of the ServiceRegistrar
     * interface. Each element in the returned set is a proxy to one of
     * the lookup service(s) that have already been discovered for the
     * registration corresponding to the <code>registrationID</code> 
     * parameter. Each element of the return set is a marshalled instance
     * of the <code>ServiceRegistrar</code> interface. The contents of the
     * returned set make up the 'remote state' of the registration's
     * currently discovered lookup service(s).
     *
     * @param registrationID unique identifier assigned to the registration
     *                       from which the set of registrars is being 
     *                       retrieved
     * 
     * @return an array of MarshalledObject objects where each element is
     *         is a marshalled instance of ServiceRegistrar.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @throws com.sun.jini.proxy.ThrowThis which is a non-remote "wrapper"
     *         class used to wrap various remote exceptions (for example,
     *         NoSuchObjectException) that this method wishes to throw.
     *         When a service is implemented as a smart proxy with a
     *         backend server, and a method on the backend which was invoked
     *         through the proxy wishes to explicitly throw a particular
     *         remote exception, it cannot simply throw that exception if
     *         it wishes that exception to be visible to the proxy running
     *         on the "client side". This is because when the backend throws
     *         any remote exception, the RMI sub-system automatically wraps
     *         that exception in a java.rmi.ServerException. Thus, the proxy
     *         will only be able to "see" the ServerException (the actual
     *         exception that the backend tried to throw is "buried" in the
     *         detail field of the ServerException). Thus, in order to allow
     *         the proxy access to the actual remote exception this method
     *         throws, that exception wraps the desired remote exception in
     *         the non-remote exception ThrowThis; which will not be wrapped
     *         in a ServerException.
     *
     *         This method throws a NoSuchObjectException wrapped in a
     *         ThrowThis exception whenever the <code>registrationID</code>
     *         parameter references an invalid or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#getRegistrars
     */
    MarshalledObject[] getRegistrars(Uuid registrationID)
                                             throws RemoteException, ThrowThis;
    /**
     * Returns an array consisting of the names of the groups whose members
     * are lookup services the lookup discovery service will attempt to
     * discover for the registration corresponding to the current instance
     * of this class. This set of group names is referred to as the
     * registration's 'managed set of groups'.
     * <p>
     * If the registration's managed set of groups is currently empty, then
     * the empty array is returned. If the lookup discovery service currently
     * has no managed set of groups for the registration through which the
     * request is being made, then null will be returned.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       from which the set of groups is being retrieved
     * 
     * @return a String array containing the elements of the managed set of
     *         groups for the registration.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#getGroups
     */
    String[] getGroups(Uuid registrationID)  throws RemoteException, ThrowThis;

    /**
     * Returns an array consisting of the the LookupLocator objects
     * corresponding to specific lookup services the lookup discovery
     * service will attempt to discover for for the registration
     * corresponding to the current instance of this class. This set of
     * locators is referred to as the registration's 'managed set of locators'.
     * <p>
     * If the registration's managed set of locators is currently empty, then
     * the empty array is returned. If the lookup discovery service currently
     * has no managed set of locators for the registration through which the
     * request is being made, then null will be returned.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       from which the set of locators is being retrieved
     * 
     * @return array consisting of net.jini.core.discovery.LookupLocator
     *         objects corresponding to the elements of the managed set of
     *         locators for the registration.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#getLocators
     */
    LookupLocator[] getLocators(Uuid registrationID)
                                             throws RemoteException, ThrowThis;
    /**
     * Adds a set of group names to the managed set of groups associated
     * with the registration corresponding to the current instance of
     * this class.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of groups being augmented
     *                       corresponds
     * @param groups         a String array, none of whose elements may be
     *                       null, consisting of the group names with which to
     *                       augment the registration's managed set of groups.
     * <p>
     *                       If any element of this parameter duplicates any
     *                       other element of this parameter, the duplicate
     *                       will be ignored. If any element of this parameter
     *                       duplicates any element of the registration's
     *                       current managed set of groups, the duplicate will
     *                       be ignored.
     * <p>
     *                       If the empty set is input, then the registration's
     *                       managed set of groups will not change. If null is
     *                       input, this method will throw a
     *                       <code>NullPointerException</code>.
     * 
     * @throws java.lang.IllegalStateException this exception occurs when
     *         the <code>addGroups</code> method of the discovery
     *         manager is invoked after the <code>terminate</code> method 
     *         of that manager is called.
     * 
     * @throws java.lang.UnsupportedOperationException this exception occurs
     *         when the lookup discovery service has no managed set of groups
     *         associated with the registration.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the groups parameter, or one or more
     *         of the elements of the groups parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of groups may or may not have been
     *         successfully augmented.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#addGroups
     */
    void addGroups(Uuid     registrationID, 
                   String[] groups)  throws RemoteException,ThrowThis;
    /**
     * Replaces all of the group names in the managed set of groups
     * associated with the registration corresponding to the current
     * instance of this class.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of groups being replaced
     *                       corresponds
     * @param groups         a String array, none of whose elements may be 
     *                       null, consisting of the group names with which to 
     *                       replace the names in this registration's managed 
     *                       set of groups.
     * <p>
     *                       If any element of this parameter duplicates any 
     *                       other element of this parameter, the duplicate 
     *                       will be ignored.
     * <p>
     *                       If the empty set is input, then group discovery 
     *                       for the registration will cease. If null is input,
     *                       the lookup discovery service will attempt to 
     *                       discover all as yet undiscovered lookup services 
     *                       located within its multicast radius and, upon 
     *                       discovery of any such lookup service, will send 
     *                       to the registration's listener an event signaling
     *                       that discovery.
     * 
     * @throws java.lang.IllegalStateException this exception occurs when
     *         the <code>addGroups</code> method of the discovery
     *         manager is invoked after the <code>terminate</code> method 
     *         of that manager is called.
     * 
     * @throws java.lang.NullPointerException this exception occurs when one
     *         or more of the elements of the groups parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of groups may or may not have been
     *         successfully replaced.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#setGroups
     */
    void setGroups(Uuid     registrationID,
                   String[] groups)  throws RemoteException,ThrowThis;
    /**
     * Deletes a set of group names from the managed set of groups
     * associated with the registration corresponding to the current
     * instance of this class.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of groups being removed
     *                       corresponds
     * @param groups         a String array, none of whose elements may be
     *                       null, consisting of the group names to delete 
     *                       from the registration's managed set of groups.
     * <p>
     *                       If any element of this parameter duplicates any 
     *                       other element of this parameter, the duplicate 
     *                       will be ignored.
     * <p>
     *                       If the empty set is input, the registration's 
     *                       managed set of groups will not change. If null is 
     *                       input, this method will throw a 
     *                       <code>NullPointerException</code>.
     * 
     * @throws java.lang.IllegalStateException this exception occurs when
     *         the <code>addGroups</code> method of the discovery
     *         manager is invoked after the <code>terminate</code> method 
     *         of that manager is called.
     * 
     * @throws java.lang.UnsupportedOperationException this exception occurs
     *         when the lookup discovery service has no managed set of groups
     *         associated with the registration.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the groups parameter, or one or more
     *         of the elements of the groups parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of groups may or may not have been
     *         successfully modified.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeGroups
     */
    void removeGroups(Uuid     registrationID,
                      String[] groups)  throws RemoteException,ThrowThis;
    /**
     * Adds a set of LookupLocator objects to the managed set of locators
     * associated with the registration corresponding to the current
     * instance of this class.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of locators being augmented
     *                       corresponds
     * @param locators       an array, none of whose elements may be null, 
     *                       consisting of the LookupLocator objects with 
     *                       which to augment the registration's managed set 
     *                       of locators.
     * <p>
     *                       If any element of this parameter duplicates any 
     *                       other element of this parameter, the duplicate 
     *                       will be ignored. If any element of this parameter 
     *                       duplicates any element of the registration's 
     *                       managed set of locators, the duplicate will be 
     *                       ignored.
     * <p>
     *                       If the empty array is input, then the 
     *                       registration's managed set of locators will not 
     *                       change. If null is input, this method will throw 
     *                       a <code>NullPointerException</code>.
     * 
     * @throws java.lang.UnsupportedOperationException this exception occurs
     *         when the lookup discovery service has no managed set of
     *         locators associated with the registration.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the locators parameter, or one or
     *         more of the elements of the locators parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of locators may or may not have
     *         been successfully augmented.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#addLocators
     */
    void addLocators(Uuid            registrationID,
                     LookupLocator[] locators) throws RemoteException,
                                                      ThrowThis;
    /**
     * Replaces with a new set of LookupLocator objects, all of the
     * elements in the managed set of locators associated with the
     * registration corresponding to the current instance of this class.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of locators being replaced
     *                       corresponds
     * @param locators       an array, none of whose elements may be null,
     *                       consisting of the LookupLocator objects with 
     *                       which to replace the locators in the 
     *                       registration's managed set of locators.
     * <p>
     *                       If any element of this parameter duplicates any 
     *                       other element of this parameter, the duplicate 
     *                       will be ignored.
     * <p>
     *                       If the empty array is input, then locator 
     *                       discovery for the registration will cease. If 
     *                       null is input, this method will throw a 
     *                       <code>NullPointerException</code>.
     * 
     * @throws java.lang.NullPointerException this exception occurs when one
     *         or more of the elements of the locators parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of locators may or may not have
     *         been successfully replaced.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#setLocators
     */
    void setLocators(Uuid            registrationID,
                     LookupLocator[] locators) throws RemoteException,
                                                      ThrowThis;
    /**
     * Deletes a set of LookupLocator objects from the managed set of
     * locators associated with the registration corresponding to the
     * current instance of this class.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the set of locators being removed
     *                       corresponds
     * @param locators       an array, none of whose elements may be null,
     *                       consisting of the LookupLocator objects to remove
     *                       from the registration's managed set of locators.
     * <p>
     *                       If any element of this parameter duplicates any
     *                       other element of this parameter, the duplicate
     *                       will be ignored.
     * <p>
     *                       If the empty set is input, the managed set of
     *                       locators will not change. If null is input,
     *                       this method will throw a 
     *                       <code>NullPointerException</code>.
     * 
     * @throws java.lang.UnsupportedOperationException this exception occurs
     *         when the lookup discovery service has no managed set of
     *         locators associated with the registration.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the locators parameter, or one or
     *         more of the elements of the locators parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of locators may or may not have
     *         been successfully modified.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeLocators
     */
    void removeLocators(Uuid            registrationID,
                        LookupLocator[] locators)  throws RemoteException,
                                                          ThrowThis;
    /**
     * Informs the lookup discovery service of the existence of an 
     * unavailable lookup service and requests that the lookup discovery
     * service discard the unavailable lookup service.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       making the current discard request
     * @param registrar      a reference to the lookup service that the lookup
     *                       discovery service is being asked to discard.
     * <p>
     *                       If this parameter equals none of the lookup
     *                       services contained in the managed set of lookup
     *                       services for this registration, no action will
     *                       be taken.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         null is input to the registrar parameter.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, 
     *         the lookup service may or may not have been successfully
     *         discarded.
     *
     * @throws java.rmi.NoSuchObjectException wrapped in an instance of
     *         com.sun.jini.proxy.ThrowThis exception whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration. Refer to the description of the
     *         <code>getRegistrars</code> method for more information on
     *         this exception.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#discard
     */
    void discard(Uuid             registrationID,
                 ServiceRegistrar registrar) throws RemoteException, ThrowThis;

    /**
     * This method renews the lease corresponding to the given 
     * <code>registrationID</code> and <code>leaseID</code> parameters,
     * granting a new duration that is less than or equal to the requested
     * duration value contained in the <code>duration</code> parameter.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the lease being renewed corresponds
     * @param leaseID        identifier assigned by the lease grantor to the
     *                       lease being renewed
     * @param duration       the requested duration for the lease being renewed
     *
     * @return <code>long</code> value representing the actual duration that
     *         was granted for the renewed lease. Note that the actual
     *         duration granted and returned by this method may be less than
     *         the duration requested.
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being renewed does not exist, or is unknown
     *         to the lease grantor; typically because the lease has expired.
     *         
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the lease may or may
     *         not have been renewed successfully.
     *
     * @see net.jini.core.lease.Lease#renew
     */
    long renewLease(Uuid registrationID,
                    Uuid leaseID,
                    long duration) throws UnknownLeaseException,
                                          RemoteException;
    /**
     * Renews all leases from a <code>LeaseMap</code>.
     * <p>
     * For each element in the <code>registrationIDs</code> parameter,
     * this method will renew the corresponding element in the
     * <code>leaseIDs</code> parameter with the corresponding element
     * in the <code>registrationID</code> parameter.
     *
     * @param registrationIDs array containing the unique identifiers assigned
     *                        to the each registration to which each lease 
     *                        to be renewed corresponds
     * @param leaseIDs        array containing the identifiers assigned by the
     *                        lease grantor to each lease being renewed
     * @param durations       array containing the requested durations for 
     *                        each lease being renewed
     * 
     * @return an instance of FiddlerRenewResults containing data corresponding
     *         to the results (granted durations or exceptions) of each
     *         renewal attempt
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, this method may or
     *         may not have complete its processing successfully.
     *
     * @see net.jini.core.lease.LeaseMap#renewAll
     */
    FiddlerRenewResults renewLeases(Uuid[] registrationIDs,
			            Uuid[] leaseIDs,
			            long[] durations)  throws RemoteException;
    /**
     * This method cancels the lease corresponding to the given 
     * <code>registrationID</code> and <code>leaseID</code> parameters.
     *
     * @param registrationID unique identifier assigned to the registration
     *                       to which the lease being cancelled corresponds
     * @param leaseID        identifier assigned by the lease grantor to the
     *                       lease being cancelled
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being cancelled is unknown to the lease grantor.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the lease may or may
     *         not have been cancelled successfully.
     *
     * @see net.jini.core.lease.Lease#cancel
     */
    void cancelLease(Uuid registrationID,
                     Uuid leaseID)  throws UnknownLeaseException,
                                           RemoteException;
    /**
     * Cancels all leases from a <code>LeaseMap</code>.
     * <p>
     * For each element in the <code>registrationIDs</code> parameter,
     * this method will cancel the corresponding element in the
     * <code>leaseIDs</code> parameter.
     *
     * @param registrationIDs array containing the unique identifiers assigned
     *                        to the each registration to which each lease 
     *                        to be cancelled corresponds
     * @param leaseIDs        array containing the identifiers assigned by the
     *                        lease grantor to each lease being cancelled
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, this method may or
     *         may not have complete its processing successfully.
     * 
     * @return array consisting of any exceptions that may have occurred 
     *         while attempting to cancel one of the leases in the map.
     *
     * @see net.jini.core.lease.LeaseMap#cancelAll
     */
    Exception[] cancelLeases(Uuid[] registrationIDs,
                             Uuid[] leaseIDs)  throws RemoteException;
}
