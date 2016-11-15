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
package org.apache.river.reggie;

import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.export.ServiceProxyAccessor;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import net.jini.lookup.DiscoveryAdmin;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.proxy.MarshalledWrapper;

/**
 * Registrar defines the private protocol between the various client-side
 * proxies and the registrar server.
 * <p>
 * The declared methods are pretty straightforward transformations of the
 * ServiceRegistrar and ServiceRegistration interfaces, with external classes
 * (ServiceItem, ServiceTemplate, ServiceMatches, Entry) converted to internal
 * classes (Item, Template, Matches, EntryRep).  In addition, there are
 * methods for transformed Lease and LeaseMap interfaces, for service and
 * event leases.
 *
 * @author Sun Microsystems, Inc.
 *
 */
interface Registrar 
    extends Remote, ServiceProxyAccessor, Administrable,
	    DiscoveryAdmin, JoinAdmin, DestroyAdmin
{
    /**
     * Register a new service or re-register an existing service.
     * @see net.jini.core.lookup.ServiceRegistrar#register
     */
    ServiceRegistration register(Item item, long leaseDuration)
	throws RemoteException;

    /**
     * Returns the service object (i.e., just ServiceItem.service) from an
     * item matching the template, or null if there is no match.
     * @see net.jini.core.lookup.ServiceRegistrar#lookup
     * @param tmpl Template used for matching service items.
     * @return MarshalledWrapper containing service object
     * @throws RemoteException if connection related exception occurs.
     */
    MarshalledWrapper lookup(Template tmpl) throws RemoteException;

    /**
     * Returns at most maxMatches items matching the template, plus the total
     * number of items that match the template.
     * @see net.jini.core.lookup.ServiceRegistrar#lookup
     */
    Matches lookup(Template tmpl, int maxMatches) throws RemoteException;
    
    Object [] lookUp(Template tmpl, int maxProxys) throws RemoteException;

    /**
     * Registers for event notification.
     * @see net.jini.core.lookup.ServiceRegistrar#notify
     */
    EventRegistration notify(Template tmpl,
			     int transitions,
			     RemoteEventListener listener,
			     MarshalledObject handback,
			     long leaseDuration)
	throws RemoteException;
    
    /**
     * Registers for event notification.
     * @see net.jini.core.lookup.ServiceRegistrar#notify
     */
    EventRegistration notiFy(Template tmpl,
			     int transitions,
			     RemoteEventListener listener,
			     MarshalledInstance handback,
			     long leaseDuration)
	throws RemoteException;

    /**
     * Looks at all service items that match the specified template, finds
     * every entry (among those service items) that either doesn't match any
     * entry templates or is a subclass of at least one matching entry
     * template, and returns the set of the (most specific) classes of those
     * entries.
     * @see net.jini.core.lookup.ServiceRegistrar#getEntryClasses
     */
    EntryClassBase[] getEntryClasses(Template tmpl) throws RemoteException;

    /**
     * Looks at all service items that match the specified template, finds
     * every entry (among those service items) that matches
     * tmpl.attributeSetTemplates[setIndex], and returns the set of values
     * of the specified field of those entries.
     * The field name has been converted to an index (fields numbered
     * from super to subclass).
     *
     * @see net.jini.core.lookup.ServiceRegistrar#getFieldValues
     * @param tmpl template to match service items
     * @param setIndex index in the template of attributes
     * @param field field index.
     * @return Object array
     * @throws RemoteException if a connection related exception occurs.
     */
    Object[] getFieldValues(Template tmpl, int setIndex, int field)
	throws RemoteException;

    /**
     * Looks at all service items that match the specified template, and for
     * every service item finds the most specific type (class or interface)
     * or types the service item is an instance of that are neither equal to,
     * nor a superclass of, any of the service types in the template and that
     * have names that start with the specified prefix, and returns the set
     * of all such types.
     * @see net.jini.core.lookup.ServiceRegistrar#getServiceTypes
     * @param tmpl template to match service items.
     * @param prefix to match start of names
     * @return ServiceTypeBase array
     * @throws RemoteException if connection related exception occurs.
     */
    ServiceTypeBase[] getServiceTypes(Template tmpl, String prefix)
	throws RemoteException;

    /**
     * Returns a LookupLocator that can be used if necessary for unicast
     * discovery of the lookup service.
     * @see net.jini.core.lookup.ServiceRegistrar#getLocator
     * @return LookupLocator for lookup service
     * @throws RemoteException if connection related exception occurs.
     */
    LookupLocator getLocator() throws RemoteException;

    /**
     * Adds the specified attribute sets (those that aren't duplicates of
     * existing attribute sets) to the registered service item.
     * @see net.jini.core.lookup.ServiceRegistration#addAttributes
     * @param serviceID the serviceID used to identify the service
     * @param leaseID the Uuid of the lease.
     * @param attrSets the array of EntryRep's.
     * @throws UnknownLeaseException if Lease expired.
     * @throws RemoteException if a connection related problem occurs.
     */
    void addAttributes(ServiceID serviceID, Uuid leaseID, EntryRep[] attrSets)
	throws UnknownLeaseException, RemoteException;

    /**
     * Modifies existing attribute sets of a registered service item.
     * @see net.jini.core.lookup.ServiceRegistration#modifyAttributes
     */
    void modifyAttributes(ServiceID serviceID,
			  Uuid leaseID,
			  EntryRep[] attrSetTmpls,
			  EntryRep[] attrSets)
	throws UnknownLeaseException, RemoteException;

    /**
     * Deletes all of the service item's existing attributes, and replaces
     * them with the specified attribute sets.
     * @see net.jini.core.lookup.ServiceRegistration#setAttributes
     */
    void setAttributes(ServiceID serviceID, Uuid leaseID, EntryRep[] attrSets)
	throws UnknownLeaseException, RemoteException;

    /**
     * Cancels a service lease.
     * @see net.jini.core.lease.Lease#cancel
     * @param serviceID ServiceID
     * @param leaseID Uuid of lease.
     * @throws UnknownLeaseException if leaseID is unknown.
     * @throws RemoteException of a connection related exception occurs.
     */
    void cancelServiceLease(ServiceID serviceID, Uuid leaseID)
	throws UnknownLeaseException, RemoteException;

    /**
     * Renews a service lease.
     * @see net.jini.core.lease.Lease#renew
     */
    long renewServiceLease(ServiceID serviceID, Uuid leaseID, long duration)
	throws UnknownLeaseException, RemoteException;

    /**
     * Cancels an event lease.
     * @see net.jini.core.lease.Lease#cancel
     * @param eventID the event id.
     * @param leaseID Uuid of the lease.
     * @throws UnknownLeaseException if leaseID unknown.
     * @throws RemoteException if connection related exception occurs.
     */
    void cancelEventLease(long eventID, Uuid leaseID)
	throws UnknownLeaseException, RemoteException;

    /**
     * Renews an event lease.
     * @see net.jini.core.lease.Lease#renew
     */
    long renewEventLease(long eventID, Uuid leaseID, long duration)
	throws UnknownLeaseException, RemoteException;

    /**
     * Renews service and event leases from a LeaseMap.
     * @see net.jini.core.lease.LeaseMap#renewAll
     */
    RenewResults renewLeases(Object[] regIDs,
			     Uuid[] leaseIDs,
			     long[] durations)
	throws RemoteException;

    /**
     * Cancels service and event leases from a LeaseMap.
     * @see net.jini.core.lease.LeaseMap#cancelAll
     * @param regIDs array of service registration id's.
     * @param leaseIDs array of lease Uuid's
     * @return array containing Exceptions
     * @throws RemoteException if a connection related exception occurs.
     */
    Exception[] cancelLeases(Object[] regIDs, Uuid[] leaseIDs)
	throws RemoteException;
}
