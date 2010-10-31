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

import com.sun.jini.admin.DestroyAdmin;
import com.sun.jini.proxy.ConstrainableProxyUtil;

import net.jini.admin.JoinAdmin;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;

import java.lang.reflect.Method;
import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.RemoteException;

/**
 * This class is a proxy providing access to the methods of an implementation
 * of the lookup discovery service which allow the administration of the
 * service. Clients only see instances of this class via the FiddlerAdmin
 * interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class FiddlerAdminProxy implements FiddlerAdmin, ReferentUuid, Serializable {

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
     * proxy class by the lookup discovery service. This ID is used to
     * determine equality between proxies.
     *
     * @serial
     */
    final Uuid proxyID;

    /**
     * Public static factory method that creates and returns an instance of 
     * <code>FiddlerAdminProxy</code>. If the server associated with this proxy
     * implements <code>RemoteMethodControl</code>, then the object returned by
     * this method will also implement <code>RemoteMethodControl</code>.
     * 
     * @param server  reference to the server object through which 
     *                communication occurs between the client-side and
     *                server-side of the associated service.
     * @param proxyID the unique identifier assigned by the service to each
     *                instance of this proxy
     * 
     * @return an instance of <code>FiddlerAdminProxy</code> that implements
     *         <code>RemoteMethodControl</code> if the given <code>server</code>
     *         does.
     */
    public static FiddlerAdminProxy createAdminProxy(Fiddler server,
                                                     Uuid proxyID)
    {
        if(server instanceof RemoteMethodControl) {
            return new ConstrainableFiddlerAdminProxy(server, proxyID, null);
        } else {
            return new FiddlerAdminProxy(server, proxyID);
        }//endif
    }//end createAdminProxy

    /**
     * Constructs a new instance of FiddlerAdminProxy.
     *
     * @param server  reference to the server object through which 
     *                communication occurs between the client-side and
     *                server-side of the associated service
     * @param proxyID the unique identifier assigned by the service to each
     *                instance of this proxy
     */
    private FiddlerAdminProxy(Fiddler server, Uuid proxyID) {
	this.server  = server;
	this.proxyID = proxyID;
    }//end constructor

    /* *** Methods of com.sun.jini.fiddler.FiddlerAdmin *** */

    /**
     * Changes the least upper bound applied to all lease durations granted
     * by the lookup discovery service.
     * <p>
     * This method is a mechanism for an entity with the appropriate
     * privileges to administratively change the value of the least upper
     * bound that will be applied by the Fiddler implementation of the lookup
     * discovery service when determining the duration to assign to the lease
     * on a requested registration.
     *
     * @param newBound <code>long</code> value representing the new least
     *        upper bound (in milliseconds) on the set of all possible
     *        lease durations that may be granted
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         bound value may or may not have been changed successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdmin#setLeaseBound
     */
    public void setLeaseBound(long newBound) throws RemoteException {
        server.setLeaseBound(newBound);
    }
    /**
     * Retrieves the least upper bound applied to all lease durations granted
     * by the lookup discovery service.
     *
     * @return <code>long</code> value representing the current least
     *         upper bound (in milliseconds) on the set of all possible
     *         lease durations that may be granted
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see com.sun.jini.fiddler.FiddlerAdmin#setLeaseBound
     */
    public long getLeaseBound() throws RemoteException {
        return server.getLeaseBound();
    }

    /**
     * Change the weight factor applied by the lookup discovery service
     * to the snapshot size during the test to determine whether or not
     * to take a "snapshot" of the system state.
     *
     * @param weight weight factor for snapshot size
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         weight factor may or may not have been changed successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdmin#setPersistenceSnapshotWeight
     */
    public void setPersistenceSnapshotWeight(float weight)
                                                     throws RemoteException
    {
        server.setPersistenceSnapshotWeight(weight);
    }

    /**
     * Retrieve the weight factor applied by the lookup discovery service
     * to the snapshot size during the test to determine whether or not to
     * take a "snapshot" of the system state.
     * 
     * @return float value corresponding to the weight factor for snapshot
     *         size
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see com.sun.jini.fiddler.FiddlerAdmin#getPersistenceSnapshotWeight
     */
    public float getPersistenceSnapshotWeight() throws RemoteException {
        return server.getPersistenceSnapshotWeight();
    }

    /**
     * Change the value of the size threshold of the snapshot; which is
     * employed by the lookup discovery service in the test to determine
     * whether or not to take a "snapshot" of the system state.
     *
     * @param threshold size threshold for taking a snapshot
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         threshold may or may not have been changed successfully.
     *
     * @see com.sun.jini.fiddler.FiddlerAdmin#setPersistenceSnapshotThreshold
     */
    public void setPersistenceSnapshotThreshold(int threshold)
                                                        throws RemoteException
    {
        server.setPersistenceSnapshotThreshold(threshold);
    }

    /**
     * Retrieve the value of the size threshold of the snapshot; which is
     * employed by the lookup discovery service in the test to determine
     * whether or not to take a "snapshot" of the system state.
     * 
     * @return int value corresponding to the size threshold of the snapshot
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see com.sun.jini.fiddler.FiddlerAdmin#getPersistenceSnapshotThreshold
     */
    public int getPersistenceSnapshotThreshold() throws RemoteException {
        return server.getPersistenceSnapshotThreshold();
    }

    /* *** Methods of net.jini.admin.JoinAdmin *** */

    /** 
     * Get the current attribute sets for the lookup discovery service. 
     * 
     * @return array of net.jini.core.entry.Entry containing the current
     *         attribute sets for the lookup discovery service
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see net.jini.admin.JoinAdmin#getLookupAttributes
     */
    public Entry[] getLookupAttributes() throws RemoteException {
	return server.getLookupAttributes();
    }

    /** 
     * Add attribute sets to the current set of attributes associated
     * with the lookup discovery service. The resulting set will be used
     * for all future registrations with lookup services. The new attribute
     * sets are also added to the lookup discovery service's attributes
     * on each lookup service with which the lookup discovery service
     * is currently registered.
     *
     * @param  attrSets array of net.jini.core.entry.Entry containing the
     *         attribute sets to add
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         attributes may or may not have been added successfully.
     *
     * @see net.jini.admin.JoinAdmin#addLookupAttributes
     */
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
	server.addLookupAttributes(attrSets);
    }

    /** 
     * Modify the current set of attributes associated with the lookup
     * discovery service. The resulting set will be used for all future
     * registrations with lookup services. The same modifications are 
     * also made to the lookup discovery service's attributes on each
     * lookup service with which the lookup discovery service is currently
     * registered.
     *
     * @param  attrSetTemplates  array of net.jini.core.entry.Entry containing
     *         the templates for matching attribute sets
     * @param  attrSets array of net.jini.core.entry.Entry containing the
     *         modifications to make to matching sets
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         attributes may or may not have been modified successfully.
     *
     * @see net.jini.admin.JoinAdmin#modifyLookupAttributes
     */
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets)
	throws RemoteException
    {
	server.modifyLookupAttributes(attrSetTemplates, attrSets);
    }

    /** 
     * Get the names of the groups whose members are lookup services the
     * lookup discovery services wishes to register with (join).
     * 
     * @return String array containing the names of the groups whose members
     *         are lookup services the lookup discovery service wishes to
     *         join.
     * <p>
     *         If the array returned is empty, the lookup discovery service
     *         is configured to join no groups. If null is returned, the
     *         lookup discovery service is configured to join all groups.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see net.jini.admin.JoinAdmin#getLookupGroups
     */
    public String[] getLookupGroups() throws RemoteException {
	return server.getLookupGroups();
    }

    /** 
     * Add new names to the set consisting of the names of groups whose
     * members are lookup services the lookup discovery service wishes
     * to register with (join). Any lookup services belonging to the
     * new groups that the lookup discovery service has not yet registered
     * with, will be discovered and joined.
     *
     * @param  groups String array containing the names of the groups to add
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         group names may or may not have been added successfully.
     *
     * @see net.jini.admin.JoinAdmin#addLookupGroups
     */
    public void addLookupGroups(String[] groups) throws RemoteException {
	server.addLookupGroups(groups);
    }

    /** 
     * Remove a set of group names from lookup discovery service's managed
     * set of groups (the set consisting of the names of groups whose
     * members are lookup services the lookup discovery service wishes
     * to join). Any leases granted to the lookup discovery service by
     * lookup services that are not members of the groups whose names 
     * remain in the managed set will be cancelled at those lookup services.
     *
     * @param  groups String array containing the names of the groups to remove
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         group names may or may not have been removed successfully.
     *
     * @see net.jini.admin.JoinAdmin#removeLookupGroups
     */
    public void removeLookupGroups(String[] groups) throws RemoteException {
	server.removeLookupGroups(groups);
    }

    /** 
     * Replace the lookup discovery service's managed set of groups with a
     * new set of group names. Any leases granted to the lookup discovery
     * service by lookup services that are not members of the groups whose
     * names are in the new managed set will be cancelled at those lookup
     * services. Lookup services that are members of groups reflected in
     * the new managed set will be discovered and joined.
     *
     * @param  groups String array containing the names of the new groups
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         group names may or may not have been replaced successfully.
     *
     * @see net.jini.admin.JoinAdmin#setLookupGroups
     */
    public void setLookupGroups(String[] groups) throws RemoteException {
	server.setLookupGroups(groups);
    }

    /** 
     * Get the lookup discovery service's managed set of locators. The
     * managed set of locators is the set of LookupLocator objects
     * corresponding to the specific lookup services with which the lookup
     * discovery service wishes to register (join).
     * 
     * @return array of objects of type net.jini.core.discovery.LookupLocator,
     *         each of which corresponds to a specific lookup service the
     *         lookup discovery service wishes to join.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     *
     * @see net.jini.admin.JoinAdmin#getLookupLocators
     */
    public LookupLocator[] getLookupLocators() throws RemoteException {
	return server.getLookupLocators();
    }

    /** 
     * Add a set of LookupLocator objects to the lookup discovery service's
     * managed set of locators. The managed set of locators is the set of
     * LookupLocator objects corresponding to the specific lookup services
     * with which the lookup discovery service wishes to register (join).
     * <p>
     * Any lookup services corresponding to the new locators that the lookup
     * discovery service has not yet joined, will be discovered and joined.
     *
     * @param  locators array of net.jini.core.discovery.LookupLocator objects to add
     *         to the managed set of locators
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         new locators may or may not have been added successfully.
     *
     * @see net.jini.admin.JoinAdmin#addLookupLocators
     */
    public void addLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.addLookupLocators(locators);
    }

    /** 
     * Remove a set of LookupLocator objects from the lookup discovery
     * service's managed set of locators. The managed set of locators is the
     * set of LookupLocator objects corresponding to the specific lookup
     * services with which the lookup discovery service wishes to register
     * (join).
     * <p>
     * Note that any leases granted to the lookup discovery service by
     * lookup services that do not correspond to any of the locators
     * remaining in the managed set will be cancelled at those lookup
     * services.
     *
     * @param  locators array of net.jini.core.discovery.LookupLocator objects to
     *         remove from the managed set of locators
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         new locators may or may not have been removed successfully.
     *
     * @see net.jini.admin.JoinAdmin#removeLookupLocators
     */
    public void removeLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.removeLookupLocators(locators);
    }

    /** 
     * Replace the lookup discovery service's managed set of locators with
     * a new set of locators. The managed set of locators is the set of
     * LookupLocator objects corresponding to the specific lookup services
     * with which the lookup discovery service wishes to register (join).
     * <p>
     * Note that any leases granted to the lookup discovery service by
     * lookup services whose corresponding locator is removed from the
     * managed set will be cancelled at those lookup services. The lookup
     * services corresponding to the new locators in the managed set
     * will be discovered and joined.
     *
     * @param  locators array of net.jini.core.discovery.LookupLocator objects with
     *         which to replace the current managed set of locators
     *         remove from the managed set of locators
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         locators in the managed set may or may not have been replaced
     *         successfully.
     *
     * @see net.jini.admin.JoinAdmin#setLookupLocators
     */
    public void setLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.setLookupLocators(locators);
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
        return proxyID;
    }

    /* *** Methods of com.sun.jini.admin.DestroyAdmin *** */

    /**
     * Destroy the lookup discovery service, if possible, including its
     * persistent storage. This method will typically spawn a separate
     * thread to do the actual work asynchronously, so a successful
     * return from this method usually does not mean that the service
     * has been destroyed.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         lookup discovery service may or may not have been successfully
     *         destroyed.
     *
     * @see com.sun.jini.admin.DestroyAdmin#destroy
     */
    public void destroy() throws RemoteException {
	server.destroy();
    }

    /* *** HashCode and Equals for this class *** */

    /** 
     * For any instance of this class, returns the hashcode value generated
     * by the hashCode method of the proxy ID associated with the current
     * instance of this proxy.
     *
     * @return <code>int</code> value representing the hashcode for an
     *         instance of this class.
     */
    public int hashCode() {
	return proxyID.hashCode();
    }

    /** 
     * For any instance of this class, indicates whether the object input
     * to this method is equal to the current instance of this class; where
     * equality of administrative proxies to a lookup discovery service is
     * defined by reference equality. That is, two proxies are equal if they
     * reference (are proxies to) the same backend server.
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
            throw new InvalidObjectException("FiddlerAdminProxy.readObject "
                                             +"failure - server "
                                             +"field is null");
        }//endif
        /* Verify proxyID */
        if(proxyID == null) {
            throw new InvalidObjectException("FiddlerAdminProxy.readObject "
                                             +"failure - proxyID "
                                             +"field is null");
        }//endif
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
                                         +"deserialize FiddlerAdminProxy "
                                         +"instance");
    }//end readObjectNoData

    /** The constrainable version of the class <code>FiddlerAdminProxy</code>. 
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
     *  is a proxy object through which the client makes remote calls to the 
     *  service's backend. Therefore, trust in that object must be
     *  verified. Additionally, this class also contains a field of type
     *  <code>Uuid</code> (<code>proxyID</code> which should be
     *  tested for trust. Consider the following diagram:
     *  <p>
     *  <pre>
     *    FiddlerAdminProxy {
     *        Fiddler server
     *        Uuid proxyID
     *    }//end FiddlerAdminProxy
     *  </pre>
     *  <p>
     *  Thus, in order to verify that an instance of this class is trusted,
     *  trust must be verified in the following objects from the diagram
     *  above:
     *  <ul><li> server
     *      <li> proxyID
     *  </ul>
     *
     *  When a client obtains an instance of this proxy class, the
     *  deserialization process which delivers the proxy to the client
     *  invokes the <code>readObject</code> method of this class. Part of
     *  trust verification is performed in the <code>readObject</code> method,
     *  and part is performed when the client prepares the proxy. Thus, this
     *  class' participation in the trust verification process can be
     *  summarized as follows:
     *  <p>
     *  <ul>
     *    <li> server
     *      <ul>
     *        <li> readObject
     *          <ul>
     *            <li> verify server != null
     *            <li> verify server implements RemoteMethodControl
     *            <li> verify server's method constraints are the same
     *                 as those placed on the corresponding public Remote
     *                 methods of its outer proxy class
     *          </ul>
     *        <li> proxy preparation
     *          <ul>
     *            <li> Security.verifyObjectTrust() which calls
     *            <li> ProxyVerifier.isTrustedObject() which calls
     *            <li> canonicalServerObject.checkTrustEquivalence(server)
     *                 (whose implementation is supplied by the particular 
     *                 RMI implementation that was used to export the server)
     *          </ul>
     *      </ul>
     *    <li> proxyID
     *      <ul><li> readObject
     *          <ul><li> verify proxyID != null</ul>
     *      </ul>
     *  </ul>
     *
     * @since 2.0
     */
    static final class ConstrainableFiddlerAdminProxy 
                                                extends FiddlerAdminProxy
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
            ProxyUtil.getMethod(JoinAdmin.class,
                                "getLookupAttributes", new Class[] {} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "getLookupAttributes", new Class[] {} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "addLookupAttributes",
                                new Class[] {Entry[].class} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "addLookupAttributes",
                                new Class[] {Entry[].class} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "modifyLookupAttributes",
                                new Class[] {Entry[].class,
                                             Entry[].class} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "modifyLookupAttributes",
                                new Class[] {Entry[].class,
                                             Entry[].class} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "getLookupGroups", new Class[] {} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "getLookupGroups", new Class[] {} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "addLookupGroups",
                                new Class[] {String[].class} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "addLookupGroups",
                                new Class[] {String[].class} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "removeLookupGroups",
                                new Class[] {String[].class} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "removeLookupGroups",
                                new Class[] {String[].class} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "setLookupGroups",
                                new Class[] {String[].class} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "setLookupGroups",
                                new Class[] {String[].class} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "getLookupLocators", new Class[] {} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "getLookupLocators", new Class[] {} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "addLookupLocators",
                                new Class[] {LookupLocator[].class} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "addLookupLocators",
                                new Class[] {LookupLocator[].class} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "removeLookupLocators",
                                new Class[] {LookupLocator[].class} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "removeLookupLocators",
                                new Class[] {LookupLocator[].class} ),

            ProxyUtil.getMethod(JoinAdmin.class,
                                "setLookupLocators",
                                new Class[] {LookupLocator[].class} ),
            ProxyUtil.getMethod(JoinAdmin.class,
                                "setLookupLocators",
                                new Class[] {LookupLocator[].class} ),


            ProxyUtil.getMethod(DestroyAdmin.class,
                                "destroy", new Class[] {} ),
            ProxyUtil.getMethod(DestroyAdmin.class,
                                "destroy", new Class[] {} ),

            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "setLeaseBound",
                                new Class[] {long.class} ),
            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "setLeaseBound",
                                new Class[] {long.class} ),

            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "getLeaseBound",
                                new Class[] {} ),
            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "getLeaseBound",
                                new Class[] {} ),

            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "setPersistenceSnapshotWeight",
                                new Class[] {float.class} ),
            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "setPersistenceSnapshotWeight",
                                new Class[] {float.class} ),

            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "getPersistenceSnapshotWeight",
                                new Class[] {} ),
            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "getPersistenceSnapshotWeight",
                                new Class[] {} ),

            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "setPersistenceSnapshotThreshold",
                                new Class[] {int.class} ),
            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "setPersistenceSnapshotThreshold",
                                new Class[] {int.class} ),

            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "getPersistenceSnapshotThreshold",
                                new Class[] {} ),
            ProxyUtil.getMethod(FiddlerAdmin.class,
                                "getPersistenceSnapshotThreshold",
                                new Class[] {} )
        };//end methodMapArray

        /** Client constraints placed on this proxy (may be <code>null</code>).
         *
         * @serial
         */
        private MethodConstraints methodConstraints;

        /** Constructs a new <code>ConstrainableFiddlerAdminProxy</code>
         *  instance.
         *  <p>
         *  For a description of all but the <code>methodConstraints</code>
         *  argument (provided below), refer to the description for the
         *  constructor of this class' super class.
         *
         *  @param methodConstraints the client method constraints to place on
         *                           this proxy (may be <code>null</code>).
         */
        private ConstrainableFiddlerAdminProxy
                                       (Fiddler server, 
                                        Uuid proxyID,
                                        MethodConstraints methodConstraints)
        {
            super( constrainServer(server, methodConstraints), proxyID);
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
         *  (<code>ConstrainableFiddlerAdminProxy</code>) with its client
         *  constraints set to the specified constraints. A <code>null</code>
         *  value is interpreted as mapping all methods to empty constraints.
         */
        public RemoteMethodControl setConstraints
                                              (MethodConstraints constraints)
        {
            return (new ConstrainableFiddlerAdminProxy
                                              (server, proxyID, constraints));
        }//end setConstraints

        /** Returns the client constraints placed on the current instance
         *  of this proxy class (<code>ConstrainableFiddlerAdminProxy</code>).
         *  The value returned by this method can be <code>null</code>,
         *  which is interpreted as mapping all methods to empty constraints.
         */
        public MethodConstraints getConstraints() {
            return methodConstraints;
        }//end getConstraints

        /** Returns a proxy trust iterator that is used in 
         *  <code>ProxyTrustVerifier</code> to retrieve this object's
         *  trust verifier.
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
            /* Verify the server and its constraints */
            ConstrainableProxyUtil.verifyConsistentConstraints
                                                       (methodConstraints,
                                                        server,
                                                        methodMapArray);
        }//end readObject

    }//end class ConstrainableFiddlerAdminProxy

}//end class FiddlerAdminProxy
