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

package net.jini.lookup;
/**
 * The <code>ServiceDiscoveryListener</code> interface defines the
 * methods used by objects such as a {@link net.jini.lookup.LookupCache 
 * LookupCache} to notify an entity that events of interest related to
 * the elements of the cache have occurred. It is the responsibility of
 * the entity wishing to be notified of the occurrence of such events to
 * construct an object that implements the 
 * <code>ServiceDiscoveryListener</code> interface and then register
 * that object with the cache's event mechanism. Any implementation of
 * this interface must define the actions to take upon receipt of an
 * event notification. The action taken is dependent on both the
 * application and the particular event that has occurred.
 * <p>
 * When the cache receives from one of the managed lookup services, an event
 * signaling the <i>registration</i> of a service of interest for the 
 * <i>first time</i> (or for the first time since the service has been
 * discarded), the cache applies any requested filtering and, if the service of
 * interest passes the filter (or if no filtering was requested), the cache
 * invokes the {@link net.jini.lookup.ServiceDiscoveryListener#serviceAdded
 * serviceAdded} method on all instances of
 * <code>ServiceDiscoveryListener</code> that are registered with the cache.
 * Invoking the {@link net.jini.lookup.ServiceDiscoveryListener#serviceAdded
 * serviceAdded} method notifies the entity that a service of interest that
 * matches any additional selection criteria has been discovered, and is
 * <i>safe</i> to use (if {@link net.jini.security.ProxyPreparer
 * proxy preparation} was requested).
 * <p>
 * When the cache receives, from a managed lookup service, an event signaling
 * the <i>removal</i> of a service of interest from the <i>last</i> such lookup
 * service with which it was registered, the cache invokes the 
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved} method on all instances of
 * <code>ServiceDiscoveryListener</code> that are registered with the cache;
 * doing so notifies the entity that a service of interest has been discarded.
 * <p>
 * In addition to the scenarios just described, the cache may also receive,
 * from a managed lookup service, a notification indicating that one of the
 * following events has occurred:
 * <ul><li> The service has changed in some fundamental way (for example,
 *          the service is replaced with a new version), as determined by
 *          {@link net.jini.io.MarshalledInstance#fullyEquals
 *          MarshalledInstance.fullyEquals}
 *     <li> The attributes of interest (across the attribute sets of all
 *          references to the service) have been uniquely modified
 * </ul>
 * <p>
 * Note that that when determining whether the proxy referenced in the event
 * is fundamentally different from the corresponding proxy held by the cache
 * (the proxy that references the same service as the proxy from the event),
 * the cache applies {@link net.jini.io.MarshalledInstance#fullyEquals
 * MarshalledInstance.fullyEquals} to the <b><i>unprepared</i></b> forms
 * of both proxies.
 * <p>
 * When the cache receives, from a managed lookup service, a notification
 * indicating that one of the above events has occurred, the cache will first
 * apply any requested filtering to the service referenced by the event;
 * after which the cache will invoke either the
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceChanged
 * serviceChanged} method or
 * the {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved} method, possibly followed by the
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceAdded serviceAdded}
 * method. Which of those methods the cache ultimately invokes is dependent
 * on the nature of the notification from the lookup service as well as the
 * results of any filtering that is performed.
 * <p>
 * If the event from the lookup service indicates that attributes of the
 * service have been modified, and if either no filtering is requested or
 * the service referenced by the event passes the filter, then the cache
 * invokes the {@link net.jini.lookup.ServiceDiscoveryListener#serviceChanged
 * serviceChanged} method on all instances of 
 * <code>ServiceDiscoveryListener</code> that are registered with the cache.
 * Invoking the {@link net.jini.lookup.ServiceDiscoveryListener#serviceChanged
 * serviceChanged} method notifies the entity that the attributes of the
 * previously discovered service have been changed in some way that is still
 * of interest to the entity.
 * <p>
 * If the event from the lookup service indicates that the previously
 * discovered service itself has changed, then if either filtering is not
 * requested or the service passes the requested filter, the cache invokes
 * the {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved} method and then the
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceAdded serviceAdded}
 * method on all instances of <code>ServiceDiscoveryListener</code> that are
 * registered with the cache. Invoking the
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved} method followed by the
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceAdded serviceAdded}
 * method notifies the entity that the previously discovered service has been
 * replaced with a new reference.
 * <p>
 * If, on the other hand, filtering is requested but the service fails the
 * filter, then the cache invokes only the
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved} method on all instances of
 * <code>ServiceDiscoveryListener</code> that are registered with the
 * cache. In this case, the
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved} method is invoked because the cache has concluded that
 * the previously discovered service has been replaced with a new reference
 * that is either no longer of interest to the entity, or is not safe
 * to use.
 * <p>
 * Finally, if filtering is requested but the filtering process results in
 * an <i>indefinite</i> state, then the cache first invokes the 
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved} method (to indicate to the entity that the service
 * is currently unusable), and then periodically retries the filter for
 * an implementation-dependent amount of time that is likely to exceed
 * the typical service lease duration, until either a failure occurs
 * or a pass occurs. If a pass occurs within the retry time period, the cache
 * invokes the {@link net.jini.lookup.ServiceDiscoveryListener#serviceAdded
 * serviceAdded} method because the cache has concluded that the previously
 * discovered service has been replaced with a new reference that is still
 * of interest to the entity, and is now safe to use.
 * <p>
 * The methods just described -- 
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceAdded serviceAdded},
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved}, and
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceChanged
 * serviceChanged} -- each take a single parameter of type
 * {@link net.jini.lookup.ServiceDiscoveryEvent ServiceDiscoveryEvent}, which
 * contains references to the service item corresponding to the event,
 * including representations of the service's state both before and after
 * the event.
 * <p>
 * Except for possible modifications that result from filtering, each method
 * defined by this interface must not modify the contents of the
 * {@link net.jini.lookup.ServiceDiscoveryEvent ServiceDiscoveryEvent}
 * parameter; doing so can result in unpredictable and undesirable effects
 * on future processing by the {@link net.jini.lookup.ServiceDiscoveryManager
 * ServiceDiscoveryManager}. Therefore, the effects of such modifications
 * are undefined.
 * <p>
 * The <code>ServiceDiscoveryListener</code> interface makes the following
 * concurrency guarantee: for any given listener object that implements this
 * interface or any sub-interface, no two methods defined by the interface
 * or sub-interface will be invoked at the same time by the same cache.
 * This applies to different invocations of the same or different methods,
 * on the same or different listeners registered with a single cache. For
 * example, the {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved} method of one listener will not be invoked while the
 * invocation of another listener's
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceAdded serviceAdded},
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved}, or
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceChanged
 * serviceChanged} method is in progress. Similarly, the one listener's
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceRemoved
 * serviceRemoved} method will not be invoked while that same listener's
 * {@link net.jini.lookup.ServiceDiscoveryListener#serviceAdded serviceAdded},
 * or {@link net.jini.lookup.ServiceDiscoveryListener#serviceChanged
 * serviceChanged} method is in progress.
 * <p>
 * Note that the intent of the methods of this interface is to allow the
 * recipient of the {@link net.jini.lookup.ServiceDiscoveryEvent
 * ServiceDiscoveryEvent} to be informed that a service has been added to,
 * removed from, or modified in the cache. Calls to these methods are
 * synchronous to allow the entity that makes the call (for example, a
 * thread that interacts with the various lookup services of interest)
 * to determine whether or not the call succeeded. However, it is not
 * part of the semantics of the call that the notification return can be
 * delayed while the recipient of the call reacts to the occurrence of the
 * event. Thus, it is highly recommended that implementations of this
 * interface avoid time consuming operations, and return from the method
 * as quickly as possible. For example, one strategy might be to simply
 * note the occurrence of the {@link net.jini.lookup.ServiceDiscoveryEvent
 * ServiceDiscoveryEvent}, and perform any time consuming event handling
 * asynchronously. 
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see LookupCache
 * @see ServiceDiscoveryEvent
 */
public interface ServiceDiscoveryListener {
    /** 
     * When the cache receives from one of the managed lookup services,
     * an event signaling the <i>registration</i> of a service of
     * interest for the <i>first time</i> (or for the first time since
     * the service has been discarded), the cache invokes the
     * <code>serviceAdded</code> method on all instances of
     * <code>ServiceDiscoveryListener</code> that are registered with the
     * cache; doing so notifies the entity that a service of interest has
     * been discovered.
     *
     * @param event an instance of <code>ServiceDiscoveryEvent</code> 
     * 			containing references to the service item 
     *			corresponding to the event, including 
     * 			representations of the service's state both 
     *			before and after the event.
     */
    void serviceAdded(ServiceDiscoveryEvent event);

    /** 
     * When the cache receives, from a managed lookup service, an event
     * signaling the removal of a service of interest from the last such
     * lookup service with which it was registered, the cache invokes
     * the <code>serviceRemoved</code> method on all instances of
     * <code>ServiceDiscoveryListener</code> that are registered with
     * the cache; doing so notifies the entity that a service of interest 
     * has been discarded.
     * 
     * @param event a <code>ServiceDiscoveryEvent</code> object 
     * 			containing references to the service item 
     *			corresponding to the event, including 
     *			representations of the service's state both 
     *			before and after the event.
     */
    void serviceRemoved(ServiceDiscoveryEvent event);

    /** 
     * When the cache receives, from a managed lookup service, an event
     * signaling the unique modification of the attributes of a service
     * of interest (across the attribute sets of all references to the
     * service), the cache invokes the <code>serviceChanged</code>
     * method on all instances of <code>ServiceDiscoveryListener</code>
     * that are registered with the cache; doing so notifies the entity
     * that the state of a service of interest has changed.
     * 
     * @param event a <code>ServiceDiscoveryEvent</code> object
     * 			containing references to the service item 
     * 			corresponding to the event, including 
     * 			representations of the service's state both
     * 			before and after the event.
     */
    void serviceChanged(ServiceDiscoveryEvent event);
}
