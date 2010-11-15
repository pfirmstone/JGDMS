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

import com.sun.jini.proxy.ConstrainableProxyUtil;
import com.sun.jini.proxy.ThrowThis;

import net.jini.discovery.LookupDiscoveryRegistration;
import net.jini.discovery.LookupUnmarshalException;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.lookup.ServiceRegistrar;

import java.lang.reflect.Method;
import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * This class is an implementation of the LookupDiscoveryRegistration
 * interface.
 * <p>
 * When a client requests a registration with a lookup discovery service,
 * an instance of this class is returned. This class is used by the client
 * as a proxy to the registration object created by the lookup discovery
 * service for the client. The remote methods of this class each have a
 * counterpart on the back-end server of the Fiddler implementation of the
 * lookup discovery service. The client can use the methods implemented in
 * this class to manage the parameters of its registration with the lookup
 * discovery service.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.discovery.LookupDiscoveryRegistration
 */
class FiddlerRegistration implements LookupDiscoveryRegistration, 
                                     ReferentUuid, Serializable
{

    private static final long serialVersionUID = 2L;

    /**
     * The reference through which communication occurs between the
     * client-side and the server-side of the lookup discovery service
     *
     * @serial
     */
    final Fiddler server;
    /** 
     * The unique identifier assigned to the current instance of this 
     * registration proxy class by the lookup discovery service. This 
     * ID is used to determine equality between registrations (proxies),
     * as well as to index into the various managed sets maintained by
     * the back-end server.
     *
     * @serial
     */
    final Uuid registrationID;
    /**
     * The object which encapsulates the information used by the client
     * to identify a notification sent by the lookup discovery service
     * to the listener registered with the lookup discovery service by
     * the client's registration, for which the instance of this class
     * serves as proxy.
     * <p>
     * Note that it is this object that contains the lease object through
     * which the client requests the renewal or cancellation of its
     * registration with this service.
     *
     * @serial
     */
    final EventRegistration eventReg;

    /**
     * Public static factory method that creates and returns an instance of 
     * <code>FiddlerRegistration</code>. If the server associated with
     * this registration implements <code>RemoteMethodControl</code>, then the
     * object returned by this method will also implement
     * <code>RemoteMethodControl</code>.
     * 
     * @param server         reference to the server object through which 
     *                       communication occurs between the client-side and
     *                       server-side of the lookup discovery service.
     * @param registrationID the unique identifier assigned by the lookup
     *                       discovery service to the current instance of 
     *                       this proxy
     * @param eventReg       object which encapsulates the information used
     *                       by the client to identify a notification sent by
     *                       the lookup discovery service to the listener
     *                       registered with the lookup discovery service by
     *                       the client's registration, for which the instance
     *                       of this class serves as proxy.
     * <p>
     *                       It is through this object that the client requests
     *                       the renewal or cancellation of the registration 
     *                       being constructed.
     * 
     * @return an instance of <code>FiddlerRegistration</code> that implements
     *         <code>RemoteMethodControl</code> if the given <code>server</code>
     *         does.
     */
    public static FiddlerRegistration createRegistration
                                                  (Fiddler server,
                                                   Uuid registrationID,
                                                   EventRegistration eventReg)
    {
        if(server instanceof RemoteMethodControl) {
            return new ConstrainableFiddlerRegistration
                                    (server, registrationID, eventReg, null);
        } else {
            return new FiddlerRegistration(server, registrationID, eventReg);
        }//endif
    }//end createRegistration

    /**
     * Constructs a new instance of FiddlerRegistration.
     *
     * @param server         reference to the server object through which 
     *                       communication occurs between the client-side and
     *                       server-side of the lookup discovery service.
     * @param registrationID the unique identifier assigned by the lookup
     *                       discovery service to the current instance of 
     *                       this proxy
     * @param eventReg       object which encapsulates the information used
     *                       by the client to identify a notification sent by
     *                       the lookup discovery service to the listener
     *                       registered with the lookup discovery service by
     *                       the client's registration, for which the instance
     *                       of this class serves as proxy.
     * <p>
     *                       It is through this object that the client requests
     *                       the renewal or cancellation of the registration 
     *                       being constructed.
     */
    private FiddlerRegistration(Fiddler server,
                                Uuid registrationID,
                                EventRegistration eventReg)
    {
	this.server         = server;
	this.registrationID = registrationID;
	this.eventReg       = eventReg;
    }//end constructor

    /* *** Methods of net.jini.discovery.LookupDiscoveryRegistration *** */

    /**
     * Returns an EventRegistration object that encapsulates the information
     * needed by the client to identify a notification sent by the lookup
     * discovery service to the registration's listener. This method is
     * not remote and takes no arguments.
     *
     * @return the EventRegistration for this registration.
     *
     * @see 
     * net.jini.discovery.LookupDiscoveryRegistration#getEventRegistration
     */
    public EventRegistration getEventRegistration() {
        return eventReg;
    }

    /**
     * Returns the Lease object that controls a client's registration with 
     * the lookup discovery service. It is through the object returned by
     * this method that the client can request the renewal or cancellation
     * of the registration with the lookup discovery service. This method is
     * not remote and takes no arguments.
     * 
     * @return the Lease on this registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#getLease
     */
    public Lease getLease() {
        return eventReg.getLease();
    }

    /**
     * Returns an array consisting of instances of the ServiceRegistrar
     * interface. Each element in the returned set is a proxy to one of
     * lookup service(s) that have already been discovered for this
     * registration. The contents of the returned set make up the
     * 'remote state' of this registration's currently discovered lookup
     * service(s). This method returns a new array on each invocation.
     * <p>
     * To obtain the desired lookup service proxies, this method sends a
     * request to the the lookup discovery service. Upon receiving the
     * request, the lookup discovery service sends the requested set of
     * proxies as a set of marshalled instances of the ServiceRegistrar
     * interface. Thus, in order to construct the return set, this method
     * attempts to unmarshal each element of the set received from the
     * lookup discovery service. Should a failure occur while attempting
     * to unmarshal any of the elements of the received set of marshalled
     * proxy objects, this method will throw an exception of type
     * LookupUnmarshalException. 
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
     * 
     * @throws java.rmi.NoSuchObjectException whenever the referenced
     *         registration is invalid or non-existent.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#getRegistrars
     */
    public ServiceRegistrar[] getRegistrars() throws LookupUnmarshalException,
                                                     RemoteException
    {
	MarshalledObject[] mRegs = null; 
        try {
            mRegs = server.getRegistrars(registrationID);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
        if (mRegs == null) return null;

        ServiceRegistrar[] regs = new ServiceRegistrar[mRegs.length];
        if(regs.length > 0) {
            ArrayList marshalledRegs = new ArrayList();
            for(int i=0;i<mRegs.length;i++) marshalledRegs.add(mRegs[i]);
            ArrayList unmarshalledRegs = new ArrayList();
            ArrayList exceptions = unmarshalRegistrars(marshalledRegs,
                                                       unmarshalledRegs);
            /* Add the un-marshalled elements to the end of regs */
            insertRegistrars(regs,unmarshalledRegs);
            if( exceptions.size() > 0 ) {
                throw(new LookupUnmarshalException
                      ( (ServiceRegistrar[])(unmarshalledRegs.toArray
                              (new ServiceRegistrar[unmarshalledRegs.size()])),
                        (MarshalledObject[])(marshalledRegs.toArray
                               (new MarshalledObject[marshalledRegs.size()])),
                        (Throwable[])(exceptions.toArray
                               (new Throwable[exceptions.size()])),
                        "failed to unmarshal at least one ServiceRegistrar") );
            }//endif
        } else {
            return regs;
        }//endif

        /* Remove duplicates */
        HashSet regsCopy = new HashSet(); // no duplicates
        for(int i=0;i<regs.length;i++) {
            if(regs[i] == null) continue;
            regsCopy.add(regs[i]);
        }//endloop

        return ( (ServiceRegistrar[])(regsCopy).toArray
                                     (new ServiceRegistrar[regsCopy.size()]) );
    }

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
     * @return a String array containing the elements of the managed set of
     *         groups for the registration.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     * 
     * @throws java.rmi.NoSuchObjectException whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#getGroups
     */
    public String[] getGroups()  throws RemoteException {
        try {
            return server.getGroups(registrationID);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
        return new String[0];
    }

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
     * @return array consisting of net.jini.core.discovery.LookupLocator
     *         objects corresponding to the elements of the managed set of
     *         locators for the registration.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     * 
     * @throws java.rmi.NoSuchObjectException whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#getLocators
     */
    public LookupLocator[] getLocators()  throws RemoteException {
        try {
            return server.getLocators(registrationID);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
        return null;
    }

    /**
     * Adds a set of group names to the managed set of groups associated
     * with the registration corresponding to the current instance of
     * this class.
     * 
     * @param groups a String array, none of whose elements may be null,
     *               consisting of the group names with which to augment
     *               the registration's managed set of groups.
     * <p>
     *               If any element of this parameter duplicates any other
     *               element of this parameter, the duplicate will be ignored.
     *               If any element of this parameter duplicates any element
     *               of the registration's current managed set of groups, the
     *               duplicate will be ignored.
     * <p>
     *               If the empty set is input, then the registration's
     *               managed set of groups will not change. If null is
     *               input, this method will throw a NullPointerException.
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
     * @throws java.rmi.NoSuchObjectException whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#addGroups
     */
    public void addGroups(String[] groups)  throws RemoteException {
        try {
            server.addGroups(registrationID,groups);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
    }

    /**
     * Replaces all of the group names in the managed set of groups
     * associated with the registration corresponding to the current
     * instance of this class.
     * 
     * @param groups a String array, none of whose elements may be null,
     *               consisting of the group names with which to replace the
     *               names in this registration's managed set of groups.
     * <p>
     *               If any element of this parameter duplicates any other
     *               element of this parameter, the duplicate will be ignored.
     * <p>
     *               If the empty set is input, then group discovery for
     *               the registration will cease. If null is input, the
     *               lookup discovery service will attempt to discover all
     *               as yet undiscovered lookup services located within its
     *               multicast radius and, upon discovery of any such lookup
     *               service, will send to the registration's listener an
     *               event signaling that discovery.
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
     * @throws java.rmi.NoSuchObjectException whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#setGroups
     */
    public void setGroups(String[] groups)  throws RemoteException {
        try {
            server.setGroups(registrationID,groups);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
    }

    /**
     * Deletes a set of group names from the managed set of groups
     * associated with the registration corresponding to the current
     * instance of this class.
     * 
     * @param groups a String array, none of whose elements may be null,
     *               consisting of the group names to delete from the
     *               registration's managed set of groups.
     * <p>
     *               If any element of this parameter duplicates any other
     *               element of this parameter, the duplicate will be ignored.
     * <p>
     *               If the empty set is input, the registration's managed
     *               set of groups will not change. If null is input, this
     *               method will throw a NullPointerException.
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
     * @throws java.rmi.NoSuchObjectException whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeGroups
     */
    public void removeGroups(String[] groups)  throws RemoteException {
        try {
            server.removeGroups(registrationID,groups);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
    }

    /**
     * Adds a set of LookupLocator objects to the managed set of locators
     * associated with the registration corresponding to the current
     * instance of this class.
     * 
     * @param locators an array, none of whose elements may be null, consisting
     *                 of the LookupLocator objects with which to augment
     *                 the registration's managed set of locators.
     * <p>
     *                 If any element of this parameter duplicates any other
     *                 element of this parameter, the duplicate will be
     *                 ignored. If any element of this parameter duplicates
     *                 any element of the registration's managed set of
     *                 locators, the duplicate will be ignored.
     * <p>
     *                 If the empty array is input, then the registration's
     *                 managed set of locators will not change. If null is
     *                 input, this method will throw a NullPointerException.
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
     * @throws java.rmi.NoSuchObjectException whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#addLocators
     */
    public void addLocators(LookupLocator[] locators) throws RemoteException {
        try {
            server.addLocators(registrationID,locators);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
    }

    /**
     * Replaces with a new set of LookupLocator objects, all of the
     * elements in the managed set of locators associated with the
     * registration corresponding to the current instance of this class.
     * 
     * @param locators an array, none of whose elements may be null, consisting
     *                 of the LookupLocator objects with which to replace the
     *                 locators in the registration's managed set of locators.
     * <p>
     *                 If any element of this parameter duplicates any other
     *                 element of this parameter, the duplicate will be
     *                 ignored.
     * <p>
     *                 If the empty array is input, then locator discovery for
     *                 the registration will cease. If null is input, this
     *                 method will throw a NullPointerException.
     * 
     * @throws java.lang.NullPointerException this exception occurs when
     *         either null is input to the locators parameter, or one or
     *         more of the elements of the locators parameter is null.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         registration's managed set of locators may or may not have
     *         been successfully replaced.
     * 
     * @throws java.rmi.NoSuchObjectException whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#setLocators
     */
    public void setLocators(LookupLocator[] locators) throws RemoteException {
        try {
            server.setLocators(registrationID,locators);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
    }

    /**
     * Deletes a set of LookupLocator objects from the managed set of
     * locators associated with the registration corresponding to the
     * current instance of this class.
     * 
     * @param locators an array, none of whose elements may be null, consisting
     *                 of the LookupLocator objects to remove from the
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
     * @throws java.rmi.NoSuchObjectException whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#removeLocators
     */
    public void removeLocators(LookupLocator[] locators) throws RemoteException
    {
        try {
            server.removeLocators(registrationID,locators);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
    }

    /**
     * Informs the lookup discovery service of the existence of an 
     * unavailable lookup service and requests that the lookup discovery
     * service discard the unavailable lookup service.
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
     * 
     * @throws java.rmi.NoSuchObjectException whenever the
     *         <code>registrationID</code> parameter references an invalid
     *         or non-existent registration.
     *
     * @see net.jini.discovery.LookupDiscoveryRegistration#discard
     */
    public void discard(ServiceRegistrar registrar) throws RemoteException {
        try {
            server.discard(registrationID,registrar);
        } catch (ThrowThis e) {
            e.throwRemoteException();
        }
    }

    /* From net.jini.id.ReferentUuid */

    /** 
     * Returns the universally unique identifier that has been assigned to the
     * resource this proxy represents.
     *
     * @return the instance of <code>Uuid</code> that is associated with the
     *         resource this proxy represents. This method will not return
     *         <code>null</code>.
     *
     * @see net.jini.id.ReferentUuid
     */
    public Uuid getReferentUuid() {
        return registrationID;
    }

    /* *** hashCode and Equals for this class *** */

    /** 
     * For any instance of this class, returns the hashcode value generated
     * by the hashCode method of the registration ID associated with the
     * current instance of this proxy.
     *
     * @return <code>int</code> value representing the hashcode for an
     *         instance of this class.
     */
    public int hashCode() {
	return registrationID.hashCode();
    }

    /** 
     * For any instance of this class, indicates whether the object input
     * to this method is equal to the current instance of this class; where
     * equality of proxies to a registration with a lookup discovery service
     * is defined by reference equality. That is, two proxies are equal if
     * they reference (are proxies to) the same backend server.
     *
     * @param obj reference to the object that is to be compared to the
     *            object on which this method is invoked.
     *
     * @return <code>true</code> if the object input is referentially
     *         equal to the object on which this method is invoked;
     *         <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
	return ReferentUuids.compare(this,obj);
    }

    /**
     * Attempts to unmarshal each element of the first input argument. When
     * an element of that argument is successfully unmarshalled, that element
     * is removed from the first set and the resulting unmarshalled proxy
     * is placed in the set referenced by the second input argument. 
     * Whenever failure occurs as a result of an attempt to unmarshal one
     * of the elements of the first set, the exception that is thrown as
     * as a result of that failure is placed in the returned set of
     * exceptions. 
     * <p>
     * Note that there is a one-to-one correspondence between the exceptions
     * contained in the return set and the remaining elements in the first
     * set after all unmarshalling attempts have completed.
     * 
     * @param marshalledRegs   an ArrayList object consisting of marshalled
     *                         instances of ServiceRegistrar, each 
     *                         corresponding to a proxy to a lookup service.
     *
     * @param unmarshalledRegs an ArrayList object consisting of all
     *                         successfully unmarshalled proxies from
     *                         the first argument.
     *
     * @return an ArrayList consisting of the exceptions that occur as a
     *         result of attempts to unmarshal each element of the first
     *         argument to this method.
     */
    private static ArrayList unmarshalRegistrars(ArrayList marshalledRegs,
                                                 ArrayList unmarshalledRegs)
    {
        ArrayList exceptions = new ArrayList();
       /* Try to un-marshal the current element in the set of marshalled regs.
        * 
        * If current element is successfully un-marshalled: 
        *    -- record the un-marshalled element
        *    -- delete the corresponding marshalled element from its set
        * 
        * If current element cannot be un-marshalled:
        *    -- record the exception
        *    -- leave the corresponding marshalled element in its set
        *    -- increment the index to the next marshalled element
        */
        int i = 0;
        int nMarshalledRegs = marshalledRegs.size();
        for(int n=0;n<nMarshalledRegs;n++) {
            try {
                /* Try to un-marshal the current element in marshalledRegs */

                /* Note that index 'n' is only a counter. That is, it is 
                 * intentional that the element at index 'i' is the element
                 * that is unmarshalled, not the element at index 'n'.
                 * This is because whenever the element is successfully
                 * unmarshalled, the element is removed from the set of
                 * marshalled registrars, decreasing that set by 1 element.
                 * Thus, the 'next' element to unmarshal is actually at
                 * the same index as the last element that was unmarshalled.
                 */
                MarshalledObject marshalledObj
                                 = (MarshalledObject)(marshalledRegs.get(i));
                ServiceRegistrar reg = (ServiceRegistrar)(marshalledObj.get());
                /* Success: record the un-marshalled element
                 *          delete the corresponding un-marshalled element
                 */
                unmarshalledRegs.add( reg );
                marshalledRegs.remove(i);
            } catch(IOException e) {
                exceptions.add(e);
                i=i+1;
            } catch(ClassNotFoundException e) {
                exceptions.add(e);
                i=i+1;
            }
        }
        return exceptions;
    }

    /**
     * Places the the lookup service reference(s), contained in the input
     * ArrayList, into the 'empty' slots occurring at the end (indicated
     * by the first null element) of the input array.
     * 
     * @param regsArray array that will receive the new references.
     * 
     * @param regsList ArrayList containing the ServiceRegistrar references
     *        to place in regsArray input argument.
     */
    private static void insertRegistrars(ServiceRegistrar[] regsArray,
                                         ArrayList regsList)
    {
        if((regsArray != null) && (regsList != null)) {
            int lenA = regsArray.length;
            int lenB = regsList.size();
            if((lenA == 0) || (lenB == 0)) return;
            int beg = indexFirstNull(regsArray);
            int end = ( (beg+lenB) <= lenA ? (beg+lenB) : (lenA) );
            for(int i=beg, j=0; i<end; i++,j++) {
                regsArray[i] = (ServiceRegistrar)(regsList.get(j));
            }
        }
    }

    /**
     * Finds the index of the first element in the input array that contains
     * null.
     * <p>
     * If the array is null (or has zero length), -1 will be returned. If
     * every element of the array is non-null, this method will return
     * the length of the array. Thus, after invoking this method, it is
     * important to test for these conditions to avoid the occurrence of an 
     * IndexOutOfBoundsException when using the value returned by this
     * method.
     * 
     * @param arr Object array to examine for the first occurrence of null
     *
     * @return the index of the first element in the input array that contains
     *         null. A value of -1 is returned if the input array is null;
     *         the length of the array is returned if no element in the
     *         array is null.
     */
    private static int indexFirstNull(Object[] arr) {
        int i = -1;
        if( (arr == null) || (arr.length == 0) ) return i;
        for(i=0;i<arr.length;i++) {
            if(arr[i] == null) return i;
        }
        return i;
    }

    /** When an instance of this class is deserialized, this method is
     *  automatically invoked. This implementation of this method validates
     *  the state of the deserialized instance.
     *
     * @throws <code>InvalidObjectException</code> if the state of the
     *         deserialized instance of this class is found to be invalid.
     */
    private void readObject(ObjectInputStream s)  
                               throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();

        /* Verify server */
        if(server == null) {
            throw new InvalidObjectException
                                          ("FiddlerRegistration.readObject "
                                           +"failure - server field is null");
        }//endif

        /* Verify registrationID */
        if(registrationID == null) {
            throw new InvalidObjectException
                                  ("FiddlerRegistration.readObject "
                                   +"failure - registrationID field is null");
        }//endif

        /* Verify eventReg and its contents */
        if(eventReg == null) {
            throw new InvalidObjectException
                                        ("FiddlerRegistration.readObject "
                                         +"failure - eventReg field is null");
        }//endif
        /* Verify eventReg is not a subclass EventRegistration */
        if( !((EventRegistration.class).equals(eventReg.getClass())) ) {
            throw new InvalidObjectException
                              ("ConstrainableFiddlerRegistration.readObject "
                               +"failure - eventReg class is not "
                               +"EventRegistration");
        }//endif
        /* Verify eventReg.source */
        Object source = eventReg.getSource();
        if(source == null) {
            throw new InvalidObjectException
                                        ("FiddlerRegistration.readObject "
                                         +"failure - eventReg source is null");
        }//endif
        if( !(source instanceof FiddlerProxy) ) {
            throw new InvalidObjectException
                                ("FiddlerRegistration.readObject failure - "
                                 +"eventReg source is not an instance of "
                                 +"FiddlerProxy");
        }//endif
        /* source.server != null was verified in FiddlerProxy.readObject() */

        /* Verify eventReg.lease */
        Object lease = eventReg.getLease();
        if( !(lease instanceof FiddlerLease) ) {
            throw new InvalidObjectException
                                ("FiddlerRegistration.readObject failure - "
                                 +"eventReg lease is not an instance of "
                                 +"FiddlerLease");
        }//endif
        /* lease.server != null was verified in FiddlerLease.readObject() */

    }//end readObject

    /** During deserialization of an instance of this class, if it is found
     *  that the stream contains no data, this method is automatically
     *  invoked. Because it is expected that the stream should always 
     *  contain data, this implementation of this method simply declares
     *  that something must be wrong.
     *
     * @throws <code>InvalidObjectException</code> to indicate that there
     *         was no data in the stream during deserialization of an
     *         instance of this class; declaring that something is wrong.
     */
    private void readObjectNoData() throws InvalidObjectException {
        throw new InvalidObjectException("no data found when attempting to "
                                         +"deserialize FiddlerRegistration "
                                         +"instance");
    }//end readObjectNoData

    /** The constrainable version of <code>FiddlerRegistration</code>. 
     *  <p>
     *  When a client obtains an instance of this proxy class, the client
     *  should not attempt to use the proxy until the client is assured
     *  that the proxy can be trusted. In addition to implementing the
     *  methods and mechanisms required by <code>RemoteMethodControl</code>, 
     *  this class - in conjunction with the service's
     *  <code>ProxyVerifier</code> class, helps provide a mechanism
     *  for verifying trust in the proxy on behalf of a client.
     *  <p>
     *  In order to verify that an instance of this class is trusted, 
     *  trust must be verified in all subsidiary objects (contained in that
     *  instance) through which the client ultimately makes calls (local or
     *  remote).  With respect to this class, the <code>server</code> field
     *  (which will be referred to as 'server1' for this description) is
     *  a proxy object through which the client makes remote calls to the 
     *  service's backend. Therefore, trust in that object must be
     *  verified.
     *  <p>
     *  In addition to server1, this class also contains a field of
     *  type <code>Uuid</code> (<code>registrationID</code>), and a
     *  field of type <code>EventRegistration</code> (the field 
     *  <code>eventReg</code>). Therefore, as with server1, trust must
     *  also be verified in each of these objects.
     *  <p>
     *  As indicated by the pattern described above, in order to verify
     *  trust in the subsidiary objects of this class, trust must also be
     *  verified in any subsidiary objects those objects themselves
     *  contain; and so on, until all subsidiary objects have been 
     *  exhausted. The <code>eventReg</code> field contains such subsidiary
     *  objects that also contain subsidiary objects, each requiring 
     *  verification. Those subsidiary objects are: a field of type
     *  <code>ConstrainableFiddlerProxy</code> (named <code>source</code>),
     *  and a field of type <code>ConstrainableFiddlerLease</code>
     *  (the field named <code>lease</code>, referred to below as 'lease1').
     *  <p>
     *  As with this class, the <code>source</code> field of
     *  <code>eventReg</code> is also an "outer proxy" to the service's
     *  backend, and thus also contains an (inner) proxy object (referred
     *  to below as 'server2') through which remote calls are made to the
     *  service's backend; thus, server2 must be verified. And since
     *  the <code>lease</code> field of <code>eventReg</code> also contains
     *  an (inner) proxy object ('server3'), that subsidiary object must
     *  be verified as well.
     *  <p>
     *  The description above is summarized in the following diagram:
     *  <p>
     *  <pre>
     *    FiddlerRegistration {
     *        Fiddler server1
     *        Uuid registrationID
     *        EventRegistration eventReg {
     *            ConstrainableFiddlerProxy source {
     *                Fiddler server2
     *            }//end source
     *            ConstrainableFiddlerLease lease {
     *                Fiddler server3
     *            }//end lease
     *        }//end eventReg
     *    }//end FiddlerRegistration
     *  </pre>
     *  <p>
     *  Thus, in order to verify that an instance of this class is trusted,
     *  trust must be verified in the following objects from the diagram
     *  above:
     *  <ul><li> server1
     *      <li> registrationID
     *      <li> eventReg
     *        <ul><li> source
     *              <ul><li> server2</ul>
     *            <li> lease
     *              <ul><li> server3</ul>
     *        </ul>
     *  </ul>
     *
     *  When a client obtains an instance of this proxy class, the
     *  deserialization process which delivers the proxy to the client
     *  invokes the <code>readObject</code> method of this class, as well
     *  as the <code>readObject</code> method for each subsidiary object,
     *  as the mechanism "walks" through the serialization graph. For
     *  each object that must be verified, part of that trust verification
     *  process is performed in the various <code>readObject</code> methods,
     *  and the remaining part is performed when the client prepares
     *  the proxy. This class' participation in the trust verification
     *  process can be summarized as follows:
     *  <p>
     *  <ul>
     *    <li> server1
     *      <ul>
     *        <li> readObject
     *          <ul>
     *            <li> verify server1 != null
     *            <li> verify registrationID != null
     *            <li> verify eventReg != null
     *            <li> verify eventReg is an instance of EventRegistration, but
     *                 NOT a subclass of EventRegistration (if it's a subclass,
     *                 then it's possible that the subclass contains methods
     *                 that override the methods of EventRegistration with
     *                 untrusted, un-constrained implementations)
     *            <li> verify eventReg.source != null
     *            <li> verify eventReg.source is an instance of FiddlerProxy
     *            <li> verify server2 != null (this is done in the readObject()
     *                 of FiddlerProxy)
     *            <li> verify eventReg.lease is an instance of FiddlerLease
     *            <li> verify server3 != null (this is done in the readObject()
     *                 of FiddlerLease)
     *
     *            <li> verify server1 implements RemoteMethodControl
     *            <li> verify server1's method constraints are the same
     *                 as those placed on the corresponding public Remote
     *                 methods of its outer proxy class
     *
     *            <li> verify eventReg.source is an instance of
     *                 ConstrainableFiddlerProxy
     *
     *            <li> verify lease is instance of ConstrainableFiddlerLease
     *          </ul>
     *        <li> proxy preparation
     *          <ul>
     *            <li> Security.verifyObjectTrust() which calls
     *            <li> ProxyVerifier.isTrustedObject(this) which calls
     *            <ul>
     *              <li> ProxyVerifier.isTrustedObject(source) which calls
     *                   canonicalServerObject.checkTrustEquivalence(server2)
     *              <li> ProxyVerifier.isTrustedObject(lease) which calls
     *                   canonicalServerObject.checkTrustEquivalence(server3)
     *              <li> canonicalServerObject.checkTrustEquivalence(server1)
     *                   (whose implementation is supplied by the particular 
     *                   RMI implementation that was used to export the server)
     *            </ul>
     *          </ul>
     *      </ul>
     *  </ul>
     *
     * @since 2.0
     */
    static final class ConstrainableFiddlerRegistration
                                                 extends FiddlerRegistration
                                                 implements RemoteMethodControl
    {
        static final long serialVersionUID = 2L;

        /* Array containing element pairs in which each pair of elements
         * represents a correspondence 'mapping' between two methods having
         * the following characteristics:
         *  - the first element in the pair is one of the public, remote
         *    method(s) that may be invoked by the client through the proxy
         *    class that this class extends
         *  - the second element in the pair is the method, implemented
         *    in the backend server class, that is ultimately executed in
         *    the server's backend when the client invokes the corresponding
         *    method in this proxy
         */
        private static final Method[] methodMapArray = 
        {
            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "getRegistrars", new Class[] {} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "getRegistrars",
                                new Class[] {Uuid.class} ),

            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "getGroups", new Class[] {} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "getGroups", 
                                new Class[] {Uuid.class} ),

            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "getLocators", new Class[] {} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "getLocators",
                                new Class[] {Uuid.class} ),

            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "addGroups", 
                                new Class[] {String[].class} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "addGroups", 
                                new Class[] {Uuid.class,
                                             String[].class} ),

            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "setGroups", 
                                new Class[] {String[].class} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "setGroups", 
                                new Class[] {Uuid.class,
                                             String[].class} ),

            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "removeGroups",
                                new Class[] {String[].class} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "removeGroups",
                                new Class[] {Uuid.class,
                                             String[].class} ),

            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "addLocators",
                                new Class[] {LookupLocator[].class} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "addLocators",
                                new Class[] {Uuid.class,
                                             LookupLocator[].class} ),

            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "setLocators",
                                new Class[] {LookupLocator[].class} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "setLocators",
                                new Class[] {Uuid.class,
                                             LookupLocator[].class} ),

            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "removeLocators",
                                new Class[] {LookupLocator[].class} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "removeLocators",
                                new Class[] {Uuid.class,
                                             LookupLocator[].class} ),

            ProxyUtil.getMethod(LookupDiscoveryRegistration.class,
                                "discard",
                                new Class[] {ServiceRegistrar.class} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "discard",
                                new Class[] {Uuid.class,
                                             ServiceRegistrar.class} )
        };//end methodMapArray

        /** Client constraints placed on this proxy (may be <code>null</code>).
         *
         * @serial
         */
        private MethodConstraints methodConstraints;

        /** Constructs a new <code>ConstrainableFiddlerRegistration</code>
         *  instance.
         *  <p>
         *  For a description of all but the <code>methodConstraints</code>
         *  argument (provided below), refer to the description for the
         *  constructor of this class' super class.
         *
         *  @param methodConstraints the client method constraints to place on
         *                           this proxy (may be <code>null</code>).
         */
        private ConstrainableFiddlerRegistration
                                         (Fiddler server,
                                          Uuid registrationID,
                                          EventRegistration eventReg,
                                          MethodConstraints methodConstraints)
        {
            super( constrainServer(server, methodConstraints),
                   registrationID,
                   eventReg);
	    this.methodConstraints = methodConstraints;
        }//end constructor

        /** Returns a copy of the given server proxy having the client method
         *  constraints that result after the specified method mapping is
         *  applied to the given client method constraints.
         */
        private static Fiddler constrainServer( Fiddler server,
                                                MethodConstraints constraints )
        {
            MethodConstraints newConstraints 
               = ConstrainableProxyUtil.translateConstraints(constraints,
                                                             methodMapArray);
            RemoteMethodControl constrainedServer = 
                ((RemoteMethodControl)server).setConstraints(newConstraints);

            return ((Fiddler)constrainedServer);
        }//end constrainServer

        /** Returns a new copy of this proxy class 
         *  (<code>ConstrainableFiddlerRegistration</code>) with its client
         *  constraints set to the specified constraints. A <code>null</code>
         *  value is interpreted as mapping all methods to empty constraints.
         */
        public RemoteMethodControl setConstraints
                                              (MethodConstraints constraints)
        {
            return (new ConstrainableFiddlerRegistration(server,
                                                         registrationID,
                                                         eventReg,
                                                         constraints) );
        }//end setConstraints

        /** Returns the client constraints placed on the current instance of
         *  this proxy class (<code>ConstrainableFiddlerRegistration</code>).
         *  The value returned by this method can be <code>null</code>,
         *  which is interpreted as mapping all methods to empty constraints.
         */
        public MethodConstraints getConstraints() {
            return methodConstraints;
        }//end getConstraints

        /**
	 * Returns a proxy trust iterator that is used in 
         * <code>ProxyTrustVerifier</code> to retrieve this object's
         * trust verifier.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
	    return new SingletonProxyTrustIterator(server);
        }//end getProxyTrustIterator

        /** Performs various functions related to the trust verification
         *  process for the current instance of this proxy class, as
         *  detailed in the description for this class.
         *
         * @throws <code>InvalidObjectException</code> if any of the
         *         requirements for trust verification (as detailed in the 
         *         class description) are not satisfied.
         */
        private void readObject(ObjectInputStream s)  
                                   throws IOException, ClassNotFoundException
        {
            /* Note that basic validation of the fields of this class was
             * already performed in the readObject() method of this class'
             * super class.
             */
            s.defaultReadObject();
            /* Verify server1 constraints */
            ConstrainableProxyUtil.verifyConsistentConstraints
                                                       (methodConstraints,
                                                        server,
                                                        methodMapArray);

            /* Verify server3 constraints */
            Object source = eventReg.getSource();
            if( !(source instanceof FiddlerProxy.ConstrainableFiddlerProxy) ) {
                throw new InvalidObjectException
                              ("ConstrainableFiddlerRegistration.readObject "
                               +"failure - eventReg source is not an instance "
                               +" of ConstrainableFiddlerProxy");
            }//endif
            /* Verify server4 constraints */
            Object lease = eventReg.getLease();
            if( !(lease instanceof FiddlerLease.ConstrainableFiddlerLease) ) {
                throw new InvalidObjectException
                              ("ConstrainableFiddlerRegistration.readObject "
                               +"failure - eventReg lease is not an instance "
                               +" of ConstrainableFiddlerLease");
            }//endif
        }//end readObject

    }//end class ConstrainableFiddlerRegistration

}//end class FiddlerRegistration
