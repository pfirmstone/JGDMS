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
import net.jini.core.entry.Entry;
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
     * Returns a ResultStream that provides access to MarshalledServiceItem 
     * instances.  The ResultStream terminates with a null value.  The result
     * stream may be infinite.
     * 
     * MarshalledServiceItem extends ServiceItem and can be used anywhere a 
     * ServiceItem can.  A MarshalledServiceItem implementation instance 
     * contains the marshalled form of a Service and it's Entry's,
     * the corresponding superclass ServiceItem however contains null values
     * for the service and excludes any Entry's that are not specifically requested
     * unmarshalled by this method. The ServiceID will be unmarshalled always.
     * 
     * This method is designed to allow the caller to control exactly what
     * is unmarshalled and when, it allows unmarshalling of specific entries
     * that the caller may wish to utilise for filtering. It is
     * designed to allow both the caller and the implementer to deal with very
     * large result sets in an incremental fashion.
     * 
     * It is absolutely essential that the caller deletes any references to
     * the returned result stream as soon as it is no longer requried.
     *
     * @param tmpl template to match
     * specified template
     * @param unmarshalledEntries only Entry's with these classes will be in
     * unmarshalled form.
     * @param maxBatchSize Allows the caller to limit the number of results
     * held locally, larger batch sizes reduce network traffic, but may delay
     * processing locally depending on implementation.
     * @return ResultStream containing ServiceItem's
     * @throws java.rmi.RemoteException
     * @see MarshalledServiceItem
     * @see ServiceItem
     * @see ResultStream
     * @see ServiceResultStreamFilter
     * @since 2.2.0
     */
    ResultStream<MarshalledServiceItem> lookup(ServiceTemplate tmpl, 
        Class<? extends Entry>[] unmarshalledEntries, int maxBatchSize) throws RemoteException;
    
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
     * @param maxBatchSize 
     * @return a ResultStream of entry Classes (attribute sets) for every service
     * that matches the specified template
     * @throws java.rmi.RemoteException
     */
    ResultStream<Class> getEntryClasses(ServiceTemplate tmpl, int maxBatchSize) 
            throws RemoteException;

    /**
     * Looks at all service items that match the specified template, finds
     * every entry (among those service items) that matches
     * tmpl.attributeSetTemplates[setIndex], and returns the set of values
     * of the specified field of those entries.  Duplicate values are
     * eliminated, and the order of values isarbitrary.  
     * If a returned value cannot be deserialized, that
     * element is excluded and no exception is thrown.
     *
     * @param tmpl template to match
     * @param setIndex index into tmpl.attributeSetTemplates
     * @param field name of field of tmpl.attributeSetTemplates[setIndex]
     *
     * @param maxBatchSize 
     * @return a ResultStream of objects that represents field values of entries
     * associated with services that meet the specified matching
     * criteria
     *
     * @throws NoSuchFieldException field does not name a field of the
     * entry template
     * @throws java.rmi.RemoteException
     */
    ResultStream getFieldValues(ServiceTemplate tmpl, int setIndex, String field,
            int maxBatchSize) throws NoSuchFieldException, RemoteException;
    
    /**
     * Looks at all service items that match the specified template, and for
     * every service item finds the most specific type (class or interface)
     * or types the service item is an instance of that are neither equal to,
     * nor a superclass of, any of the service types in the template and that
     * have names that start with the specified prefix, and returns the set
     * of all such types.  Duplicate types are eliminated, and the order of
     * types within the returned array is arbitrary.  
     * Null is returned if there are no such types.  If a returned type
     * cannot be deserialized, that element is excluded and no exception is thrown.
     *
     * @param tmpl template to match
     * @param prefix class name prefix
     *
     * @param maxBatchSize 
     * @return an array of Classes of all services that either match the
     * specified template or match the specified prefix
     * @throws java.rmi.RemoteException
     */
    ResultStream<Class> getServiceTypes(ServiceTemplate tmpl, String prefix, 
            int maxBatchSize) throws RemoteException;

}
