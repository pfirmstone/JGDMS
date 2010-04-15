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
import net.jini.core.discovery.LookupLocator;

/**
 *
 * @author peter
 */
public interface PortableServiceRegistrar {
    /**
     * An event is sent when the changed item matches the template both
     * before and after the operation.
     */
    int TRANSITION_MATCH_MATCH = 1 << 2;
    /**
     * An event is sent when the changed item matches the template before
     * the operation, but doesn't match the template after the operation
     * (this includes deletion of the item).
     */
    int TRANSITION_MATCH_NOMATCH = 1 << 0;
    /**
     * An event is sent when the changed item doesn't match the template
     * before the operation (this includes not existing), but does match
     * the template after the operation.
     */
    int TRANSITION_NOMATCH_MATCH = 1 << 1;

    /**
     * Looks at all service items that match the specified template, finds
     * every entry (among those service items) that either doesn't match any
     * entry templates or is a subclass of at least one matching entry
     * template, and returns the set of the (most specific) classes of those
     * entries.  Duplicate classes are eliminated, and the order of classes
     * within the returned array is arbitrary.  Null (not an empty array) is
     * returned if there are no such entries or no matching items.  If a
     * returned class cannot be deserialized, that element of the returned
     * array is set to null and no exception is thrown.
     *
     * @param tmpl template to match
     * @return an array of entry Classes (attribute sets) for every service
     * that matches the specified template
     * @throws java.rmi.RemoteException
     */
    Class[] getEntryClasses(ServiceTemplate tmpl) throws RemoteException;

    /**
     * Looks at all service items that match the specified template, finds
     * every entry (among those service items) that matches
     * tmpl.attributeSetTemplates[setIndex], and returns the set of values
     * of the specified field of those entries.  Duplicate values are
     * eliminated, and the order of values within the returned array is
     * arbitrary.  Null (not an empty array) is returned if there are no
     * matching items.  If a returned value cannot be deserialized, that
     * element of the returned array is set to null and no exception is
     * thrown.
     *
     * @param tmpl template to match
     * @param setIndex index into tmpl.attributeSetTemplates
     * @param field name of field of tmpl.attributeSetTemplates[setIndex]
     *
     * @return an array of objects that represents field values of entries
     * associated with services that meet the specified matching
     * criteria
     *
     * @throws NoSuchFieldException field does not name a field of the
     * entry template
     * @throws java.rmi.RemoteException
     */
    Object[] getFieldValues(ServiceTemplate tmpl, int setIndex, String field) throws NoSuchFieldException, RemoteException;

    /**
     * Returns the set of groups that this lookup service is currently a
     * member of.
     *
     * @return a String array of groups that this lookup service is currently
     * a member of.
     * @throws java.rmi.RemoteException
     */
    String[] getGroups() throws RemoteException;

    /**
     * Returns a LookupLocator that can be used if necessary for unicast
     * discovery of the lookup service.
     *
     * @return a LookupLocator that can be used for unicast discovery of
     * the lookup service, if necessary.
     * @throws java.rmi.RemoteException
     */
    LookupLocator getLocator() throws RemoteException;

    /**
     * Returns the service ID of the lookup service.  Note that this does not
     * make a remote call.  A lookup service is always registered with itself
     * under this service ID, and if a lookup service is configured to
     * register itself with other lookup services, it will register with all
     * of them using this same service ID.
     *
     * @return the service ID of the lookup service.
     */
    ServiceID getServiceID();

    /**
     * Looks at all service items that match the specified template, and for
     * every service item finds the most specific type (class or interface)
     * or types the service item is an instance of that are neither equal to,
     * nor a superclass of, any of the service types in the template and that
     * have names that start with the specified prefix, and returns the set
     * of all such types.  Duplicate types are eliminated, and the order of
     * types within the returned array is arbitrary.  Null (not an empty
     * array) is returned if there are no such types.  If a returned type
     * cannot be deserialized, that element of the returned array is set to
     * null and no exception is thrown.
     *
     * @param tmpl template to match
     * @param prefix class name prefix
     *
     * @return an array of Classes of all services that either match the
     * specified template or match the specified prefix
     * @throws java.rmi.RemoteException
     */
    Class[] getServiceTypes(ServiceTemplate tmpl, String prefix) throws RemoteException;

    /**
     * Returns the service object (i.e., just ServiceItem.service) from an
     * item matching the template, or null if there is no match.  If multiple
     * items match the template, it is arbitrary as to which service object
     * is returned.  If the returned object cannot be deserialized, an
     * UnmarshalException is thrown with the standard RMI semantics.
     *
     * @param tmpl template to match
     * @return an object that represents a service that matches the
     * specified template
     * @throws java.rmi.RemoteException
     */
    Object lookup(ServiceTemplate tmpl) throws RemoteException;

    /**
     * Returns at most maxMatches items matching the template, plus the total
     * number of items that match the template.  The return value is never
     * null, and the returned items array is only null if maxMatches is zero.
     * For each returned item, if the service object cannot be deserialized,
     * the service field of the item is set to null and no exception is
     * thrown. Similarly, if an attribute set cannot be deserialized, that
     * element of the attributeSets array is set to null and no exception
     * is thrown.
     *
     * @param tmpl template to match
     * @param maxMatches maximum number of matches to return
     * @return a ServiceMatches instance that contains at most maxMatches
     * items matching the template, plus the total number of items
     * that match the template.  The return value is never null, and
     * the returned items array is only null if maxMatches is zero.
     * @throws java.rmi.RemoteException
     */
    ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches) throws RemoteException;

    /**
     * Register a new service or re-register an existing service. The method
     * is defined so that it can be used in an idempotent fashion.
     * Specifically, if a call to register results in a RemoteException (in
     * which case the item might or might not have been registered), the
     * caller can simply repeat the call to register with the same parameters,
     * until it succeeds.
     * <p>
     * To register a new service, item.serviceID should be null.  In that
     * case, if item.service does not equal (using MarshalledObject.equals)
     * any existing item's service object, then a new service ID will be
     * assigned and included in the returned ServiceRegistration.  The
     * service ID is unique over time and space with respect to all other
     * service IDs generated by all lookup services.  If item.service does
     * equal an existing item's service object, the existing item is first
     * deleted from the lookup service (even if it has different attributes)
     * and its lease is cancelled, but that item's service ID is reused for
     * the newly registered item.
     * <p>
     * To re-register an existing service, or to register the service in any
     * other lookup service, item.serviceID should be set to the same service
     * ID that was returned by the initial registration.  If an item is
     * already registered under the same service ID, the existing item is
     * first deleted (even if it has different attributes or a different
     * service instance) and its lease is cancelled.  Note that service
     * object equality is not checked in this case, to allow for reasonable
     * evolution of the service (e.g., the serialized form of the stub
     * changes, or the service implements a new interface).
     * <p>
     * Any duplicate attribute sets included in a service item are eliminated
     * in the stored representation of the item.  The lease duration request
     * is not exact; the returned lease is allowed to have a shorter (but not
     * longer) duration than what was requested.  The registration is
     * persistent across restarts (crashes) of the lookup service until the
     * lease expires or is cancelled.
     *
     * @param item service item to register
     * @param leaseDuration requested lease duration, in milliseconds
     * @return a ServiceRegistration in this lookup service for the specified
     * service item
     * @throws java.rmi.RemoteException
     */
    ServiceRegistration register(ServiceItem item, long leaseDuration) throws RemoteException;

}
