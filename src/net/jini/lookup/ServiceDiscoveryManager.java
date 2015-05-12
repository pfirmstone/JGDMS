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

import au.net.zeus.collection.RC;
import au.net.zeus.collection.Ref;
import au.net.zeus.collection.Referrer;
import org.apache.river.logging.Levels;
import org.apache.river.lookup.entry.LookupAttributes;
import org.apache.river.proxy.BasicProxyTrustVerifier;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;
import net.jini.config.NoSuchEntryException;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.core.lease.Lease;
import net.jini.core.lookup.ServiceEvent;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.export.Exporter;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lease.LeaseListener;
import net.jini.lease.LeaseRenewalEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import org.apache.river.api.util.FutureObserver;
import org.apache.river.api.util.FutureObserver.ObservableFuture;
import org.apache.river.impl.thread.DependencyLinker;
import org.apache.river.impl.thread.ExtensibleExecutorService;
import org.apache.river.impl.thread.ExtensibleExecutorService.RunnableFutureFactory;
import org.apache.river.impl.thread.NamedThreadFactory;
import org.apache.river.impl.thread.ObservableFutureTask;

/**
 * The <code>ServiceDiscoveryManager</code> class is a helper utility class that
 * any client-like entity can use to "discover" services registered with any
 * number of lookup services of interest. On behalf of such entities, this class
 * maintains - as much as possible - up-to-date state information about both the
 * lookup services the entity wishes to query, and the services the entity
 * wishes to acquire and use. By maintaining current service state information,
 * the entity can implement efficient mechanisms for service access and usage.
 * <p>
 * There are three basic usage patterns for this class. In order of importance
 * and typical usage, those patterns are:
 * <p>
 * <ul>
 * <li> The entity requests that the <code>ServiceDiscoveryManager</code> create
 * a cache (an instance of {@link net.jini.lookup.LookupCache LookupCache})
 * which will asynchronously "discover", and locally store, references to
 * services that match criteria defined by the entity; services which are
 * registered with one or more lookup services managed by the
 * <code>ServiceDiscoveryManager</code> on behalf of the entity. The cache can
 * be viewed as a set of service references that the entity can access locally
 * as needed through one of the public, non-remote methods provided in the
 * cache's interface. Thus, rather than making costly remote queries of multiple
 * lookup services at the point in time when the entity needs the service, the
 * entity can simply make local queries on the cache for the services that the
 * cache acquired and stored at a prior time. An entity should employ this
 * pattern when the entity must make <i>frequent</i>
 * queries for multiple services. By populating the cache with multiple
 * instances of the desired services, redundancy in the availability of those
 * services can be provided. Thus, if an instance of a service is found to be
 * unavailable when needed, the entity can execute a local query on the cache
 * rather than one or more remote queries on the lookup services to acquire an
 * instance that is available. To employ this pattern, the entity invokes the
 * method  {@link net.jini.lookup.ServiceDiscoveryManager#createLookupCache
 *        createLookupCache}.
 * <li> The entity can register with the event mechanism provided by the
 * <code>ServiceDiscoveryManager</code>. This event mechanism allows the entity
 * to request that it be notified when a service of interest is discovered for
 * the first time, or has encountered a state change such as removal from all
 * lookup services, or attribute set changes. Although interacting with a local
 * cache of services in the way described in the first pattern can be very
 * useful to entities that need frequent access to multiple services, some
 * client-like entities may wish to interact with the cache in a reactive
 * manner. For example, an entity such as a service browser typically wishes to
 * be notified of the arrival of new services of interest as well as any changes
 * in the state of the current services in the cache. In these situations,
 * polling for such changes is usually viewed as undesirable. If the cache were
 * to also provide an event mechanism with notification semantics, the needs of
 * entities that employ either pattern can be satisfied. To employ this pattern,
 * the entity must create a cache and supply it with an instance of the  {@link net.jini.lookup.ServiceDiscoveryListener
 *        ServiceDiscoveryListener} interface that will receive instances of
 * {@link net.jini.lookup.ServiceDiscoveryEvent ServiceDiscoveryEvent} when
 * events of interest, related to the services in the cache, occur.
 * <li> The entity, through the public API of the
 * <code>ServiceDiscoveryManager</code>, can directly query the lookup services
 * managed by the <code>ServiceDiscoveryManager</code> for services of interest;
 * employing semantics similar to the semantics employed in a typical lookup
 * service query made through the
 * {@link net.jini.core.lookup.ServiceRegistrar ServiceRegistrar} interface.
 * Such queries will result in a remote call being made at the same time the
 * service is needed (unlike the first pattern, in which remote calls typically
 * occur prior to the time the service is needed). This pattern may be useful to
 * entities needing to find services on an infrequent basis, or when the cost of
 * making a remote call is outweighed by the overhead of maintaining a local
 * cache (for example, due to limited resources). Although an entity that needs
 * to query lookup service(s) can certainly make such queries through the
 * {@link net.jini.core.lookup.ServiceRegistrar ServiceRegistrar} interface, the
 * <code>ServiceDiscoveryManager</code> provides a broad API with semantics that
 * are richer than the semantics of the
 * {@link net.jini.core.lookup.ServiceRegistrar#lookup lookup} methods provided
 * by the {@link net.jini.core.lookup.ServiceRegistrar
 *        ServiceRegistrar}. This API encapsulates functionality that many client-like
 * entities may find more useful when managing both the set of desired lookup
 * services, and the service queries made on those lookup services. To employ
 * this pattern, the entity simply instantiates this class with the desired
 * parameters, and then invokes the appropriate version of the
 * {@link net.jini.lookup.ServiceDiscoveryManager#lookup lookup} method when the
 * entity wishes to acquire a service that matches desired criteria.
 * </ul>
 * <p>
 * All three mechanisms just described - local queries on the cache, service
 * discovery notification, and remote lookups - employ the same
 * template-matching scheme as that employed in the
 * {@link net.jini.core.lookup.ServiceRegistrar ServiceRegistrar} interface.
 * Additionally, each mechanism allows the entity to supply an object referred
 * to as a <i>filter</i>; an instance of
 * {@link net.jini.lookup.ServiceItemFilter ServiceItemFilter}. A filter is a
 * non-remote object that defines additional matching criteria that the
 * <code>ServiceDiscoveryManager</code> applies when searching for the entity's
 * services of interest. Employing a filter is particularly useful to entities
 * that wish to extend the capabilities of the standard template-matching
 * scheme.
 * <p>
 * In addition to (or instead of) employing a filter to apply additional
 * matching criteria to candidate service proxies initially found through
 * template matching, filters can also be used to extend the selection process
 * so that only proxies that are <i>safe</i> to use are returned to the entity.
 * To do this, the entity would use the
 * {@link net.jini.lookup.ServiceItemFilter ServiceItemFilter} interface to
 * supply the <code>ServiceDiscoveryManager</code> or
 * {@link net.jini.lookup.LookupCache LookupCache} with a filter that, when
 * applied to a candidate proxy, performs a set of operations that is referred
 * to as <i>proxy preparation</i>. As described in the documentation for
 * {@link net.jini.security.ProxyPreparer}, proxy preparation typically includes
 * operations such as, verifying trust in the proxy, specifying client
 * constraints, and dynamically granting necessary permissions to the proxy.
 * <p>
 * Note that this utility class is not remote. Clients and services that wish to
 * use this class will create an instance of this class in their own address
 * space to manage the state of discovered services and their associated lookup
 * services locally.
 *
 * @org.apache.river.impl <!-- Implementation Specifics -->
 *
 * The following implementation-specific items are discussed below:
 * <ul><li> <a href="#sdmConfigEntries">Configuring ServiceDiscoveryManager</a>
 * <li> <a href="#sdmLogging">Logging</a>
 * </ul>
 *
 * <a name="sdmConfigEntries">
 * <p>
 * <b><font size="+1">Configuring ServiceDiscoveryManager</font></b>
 * <p>
 * </a>
 *
 * This implementation of <code>ServiceDiscoveryManager</code> supports the
 * following configuration entries; where each configuration entry name is
 * associated with the component name
 * <code>net.jini.lookup.ServiceDiscoveryManager</code>. Note that the
 * configuration entries specified here are specific to this implementation of
 * <code>ServiceDiscoveryManager</code>. Unless otherwise stated, each entry is
 * retrieved from the configuration only once per instance of this utility,
 * where each such retrieval is performed in the constructor.
 * <p>
 * It is important to note that in addition to allowing a client of this utility
 * to request - through the public API - the creation of a cache that is used
 * externally by the client, this utility also creates instances of the cache
 * that are used internally by the utility itself. As such, in addition to the
 * configuration entries that are used only in this utility (and not in any
 * cache), and the configuration entries that are retrieved during the
 * construction of each new cache (and used by only that cache), there are
 * configuration entries specified below that are retrieved once during the
 * construction of this utility, but which are shared with, and used by, the
 * caches that are created.
 *
 *
 * <a name="cacheExecutorService">
 * <table summary="Describes the cacheExecutorService configuration entry"
 * border="0" cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>cacheExecutorService</code></font>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Type: <td> {@link java.util.concurrent/ExecutorService ExecutorService}
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Default: <td> <code>new
 *             {@link java.util.concurrent/ThreadPoolExecutor
 *                     ThreadPoolExecutor}( 10, 10, 15, TimeUnit.SECONDS, new LinkedBlockingQueue(),
 * new NamedThreadFactory( "SDM lookup cache", false ))</code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Description:
 * <td> The object that pools and manages the various threads executed by each
 * of the lookup caches created by this utility. There is one such
 * ExecutorService created for each cache. For each cache that is created in
 * this utility, a single, separate instance of this ExecutorService will be
 * retrieved and employed by that cache. This object should not be shared with
 * other components in the application that employs this utility.
 * </table>
 * </a>
 * <a name="discardExecutorService">
 * <table summary="Describes the discardExecutorService configuration entry"
 * border="0" cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>discardExecutorService</code></font>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Type: <td> {@link java.util.concurrent/ExecutorService ExecutorService}
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Default: <td> <code>new
 *             {@link java.util.concurrent/ThreadPoolExecutor
 *                     ThreadPoolExecutor}( 10, 10, 15, TimeUnit.SECONDS, new LinkedBlockingQueue(),
 * new NamedThreadFactory( "SDM discard timer", false ))</code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Description:
 * <td> The object that pools and manages the threads, executed by a cache, that
 * wait on verification events after a previousy discovered service has been
 * discarded. For each cache that is created in this utility, a single, separate
 * instance of this ExecutorService will be retrieved and employed by that
 * cache. This object should not be shared with other components in the
 * application that employs this utility.
 * </table>
 * </a>
 * <a name="discardWait">
 * <table summary="Describes the discardWait configuration entry" border="0"
 * cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>discardWait</code></font>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Type: <td> <code>long</code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Default: <td> <code>2*(5*60*1000)</code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Description:
 * <td> The value used to affect the behavior of the mechanism that handles the
 * <i>service discard problem</i> described in this utility's specification.
 * This item allows each entity that uses this utility to define how long (in
 * milliseconds) to wait for verification from the lookup service(s) that a
 * discarded service is actually down before committing or un-committing a
 * requested service discard. The current implementation of this utility
 * defaults to waiting 10 minutes (twice the maximum lease duration granted by
 * the Reggie implementation of the lookup service). Note that this item is used
 * only by the caches (both internal and external) that are created by this
 * utility, and not by the utility itself.
 * </table>
 * </a>
 * <a name="discoveryManager">
 * <table summary="Describes the discoveryManager configuration entry"
 * border="0" cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>discoveryManager</code></font>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Type: <td> {@link net.jini.discovery.DiscoveryManagement}
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Default: <td> <code> new
 *    {@link net.jini.discovery.LookupDiscoveryManager#LookupDiscoveryManager(
 *      java.lang.String[],
 *      net.jini.core.discovery.LookupLocator[],
 *      net.jini.discovery.DiscoveryListener,
 *      net.jini.config.Configuration) LookupDiscoveryManager}( new
 * java.lang.String[] {""}, new
 * {@link net.jini.core.discovery.LookupLocator}[0], null, config)</code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Description:
 * <td> The object used to manage the discovery processing performed by this
 * utility. This entry will be retrieved from the configuration only if no
 * discovery manager is specified in the constructor. Note that this object
 * should not be shared with other components in the application that employs
 * this utility. This item is used only by the service discovery manager, and
 * not by any cache that is created.
 * </table>
 * </a>
 * <a name="eventLeasePreparer">
 * <table summary="Describes the eventLeasePreparer configuration entry"
 * border="0" cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>eventLeasePreparer</code></font>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Type: <td> {@link net.jini.security.ProxyPreparer}
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Default: <td> <code>new {@link net.jini.security.BasicProxyPreparer}()
 * </code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Description:
 * <td> Preparer for the leases returned when a cache registers with the event
 * mechanism of any of the discovered lookup services. This item is used only by
 * the caches (both internal and external) that are created by this utility, and
 * not by the utility itself.
 * <p>
 * Currently, no methods of the returned proxy are invoked by this utility.
 * </table>
 * </a>
 * <a name="eventListenerExporter">
 * <table summary="Describes the eventListenerExporter configuration entry"
 * border="0" cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>eventListenerExporter</code></font>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Type: <td> {@link net.jini.export.Exporter}
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Default: <td> <code> new
 *                {@link net.jini.jeri.BasicJeriExporter#BasicJeriExporter(
 *                                        net.jini.jeri.ServerEndpoint,
 *                                        net.jini.jeri.InvocationLayerFactory,
 *                                        boolean,
 *                                        boolean) BasicJeriExporter}(
 *              {@link net.jini.jeri.tcp.TcpServerEndpoint#getInstance
 *                                      TcpServerEndpoint.getInstance}(0),<br>
 * &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp new
 * {@link net.jini.jeri.BasicILFactory}(),<br>
 * &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp &nbsp
 * false, false)</code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Description:
 * <td> Exporter for the remote event listener that each cache supplies to the
 * lookup services whose event mechanisms those caches register with. Note that
 * for each cache that is created in this utility, a single, separate instance
 * of this exporter will be retrieved and employed by that cache. Note also that
 * the default exporter defined here will disable distributed garbage collection
 * (DGC) for the server endpoint associated with the exported listener, and the
 * listener backend (the "impl") will be strongly referenced. This means that
 * the listener will not "go away" unintentionally. Additionally, that exporter
 * also sets the <code>keepAlive</code> flag to <code>false</code> to allow the
 * VM in which this utility runs to "go away" when desired; and not be kept
 * alive simply because the listener is still exported.
 * </table>
 * </a>
 * <a name="leaseManager">
 * <table summary="Describes the leaseManager configuration entry" border="0"
 * cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>leaseManager</code></font>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Type: <td> {@link net.jini.lease.LeaseRenewalManager}
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Default: <td> <code> new
 *       {@link net.jini.lease.LeaseRenewalManager#LeaseRenewalManager(
 *        net.jini.config.Configuration) LeaseRenewalManager}(config)</code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Description:
 * <td> The object used to manage any event leases returned to a cache that has
 * registered with the event mechanism of the various discovered lookup
 * services. This entry will be retrieved from the configuration only if no
 * lease renewal manager is specified in the constructor. This item is used only
 * by the caches (both internal and external) that are created by this utility,
 * and not by the utility itself.
 * </table>
 * </a>
 * <a name="registrarPreparer">
 * <table summary="Describes the registrarPreparer configuration entry"
 * border="0" cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>registrarPreparer</code></font>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Type: <td> {@link net.jini.security.ProxyPreparer}
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Default: <td> <code>new {@link net.jini.security.BasicProxyPreparer}()
 * </code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Description:
 * <td> Preparer for the proxies to the lookup services that are discovered and
 * used by this utility. This item is used only by the service discovery
 * manager, and not by any cache that is created.
 * <p>
 * The following methods of the proxy returned by this preparer are invoked by
 * this utility:
 * <ul>
 * <li>{@link net.jini.core.lookup.ServiceRegistrar#lookup lookup}
 * <li>{@link net.jini.core.lookup.ServiceRegistrar#notify notify}
 * </ul>
 *
 * </table>
 * </a>
 * <a name="sdmLogging">
 * <p>
 * <b><font size="+1">Logging</font></b>
 * <p>
 * </a>
 *
 * This implementation of <code>ServiceDiscoveryManager</code> uses the
 * {@link Logger} named <code>net.jini.lookup.ServiceDiscoveryManager</code> to
 * log information at the following logging levels:
 * <p>
 *
 * <table border="1" cellpadding="5" summary="Describes the information logged
 * by ServiceDiscoveryManager, and the levels at which that information is
 * logged">
 *
 *
 * <caption halign="center" valign="top">
 * <b><code>net.jini.lookup.ServiceDiscoveryManager</code></b>
 * </caption>
 *
 * <tr> <th scope="col"> Level</th>
 * <th scope="col"> Description</th>
 * </tr>
 *
 * <tr>
 * <td>{@link java.util.logging.Level#INFO INFO}</td>
 * <td>
 * when any exception occurs while querying a lookup service, or upon applying a
 * filter to the results of such a query
 * </td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#INFO INFO}</td>
 * <td>
 * when any exception occurs while attempting to register with the event
 * mechanism of a lookup service, or while attempting to prepare the lease on
 * the registration with that event mechanism
 * </td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#INFO INFO}</td>
 * <td>when any exception occurs while attempting to prepare a proxy</td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#INFO INFO}</td>
 * <td>
 * when an <code>IllegalStateException</code> occurs while discarding a lookup
 * service proxy after logging a failure that has occurred in one of the tasks
 * executed by this utility
 * </td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#INFO INFO}</td>
 * <td>upon failure of the lease renewal process</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.river.logging.Levels#HANDLED HANDLED}</td>
 * <td>
 * when an exception occurs because a remote call to a lookup service has been
 * interrupted as a result of the termination of a cache
 * </td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.river.logging.Levels#HANDLED HANDLED}</td>
 * <td>
 * when a "gap" is encountered in an event sequence from a lookup service
 * </td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#FINER FINER}</td>
 * <td>upon failure of the lease cancellation process</td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 * <td>whenever any task is started</td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 * <td>whenever any task completes successfully</td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 * <td>whenever a lookup cache is created</td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 * <td>whenever a lookup cache is terminated</td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 * <td>whenever a proxy is prepared</td>
 * </tr>
 * <tr>
 * <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 * <td>
 * when an exception (that is, <code>IllegalStateException</code>) occurs while
 * unexporting a cache's remote event listener while the cache is being
 * terminated
 * </td>
 * </tr>
 * </table>
 * <p>
 * See the {@link org.apache.river.logging.LogManager} class for one way to use the
 * logging level {@link org.apache.river.logging.Levels#HANDLED HANDLED} in standard
 * logging configuration files.
 * <p>
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.discovery.DiscoveryManagement
 * @see net.jini.lookup.LookupCache
 * @see net.jini.lookup.ServiceDiscoveryListener
 * @see net.jini.lookup.ServiceDiscoveryEvent
 * @see net.jini.core.lookup.ServiceRegistrar
 */
public class ServiceDiscoveryManager {

    /**
     * Class for implementing register/lookup/notify/dropProxy/discard tasks
     */
    private static abstract class CacheTask implements Runnable {
        protected final ProxyReg reg;
        protected volatile long thisTaskSeqN;

        protected CacheTask(ProxyReg reg, long seqN) {
            this.reg = reg;
            this.thisTaskSeqN = seqN;
            logger.log(Level.FINEST,
                    "ServiceDiscoveryManager {0} constructed",
                    this.toString());
        }//end constructor
        /* check if the task is on a specific ProxyReg, return true if it is */

        public boolean isFromProxy(ProxyReg reg) {
            if (this.reg == null) {
                return false;
            }
            return (this.reg).equals(reg);
        }//end isFromProxy

        /**
         * Returns the ProxyReg associated with this task (if any).
         */
        public ProxyReg getProxyReg() {
            return reg;
        }//end ProxyReg

        /**
         * Returns the unique sequence number of this task.
         */
        public long getSeqN() {
            return thisTaskSeqN;
        }//end getSeqN

        public abstract boolean hasDeps();

        public boolean dependsOn(CacheTask task) {
            return false;
        }
    }//end class ServiceDiscoveryManager.CacheTask

    /**
     * Class that defines the listener that will receive local events from the
     * internal LookupCache used in the blocking versions of lookup().
     */
    private final static class ServiceDiscoveryListenerImpl
            implements ServiceDiscoveryListener {

        private final List<ServiceItem> items = new LinkedList<ServiceItem>();

        @Override
        public synchronized void serviceAdded(ServiceDiscoveryEvent event) {
            items.add(event.getPostEventServiceItem());
            this.notifyAll();
        }

        @Override
        public void serviceRemoved(ServiceDiscoveryEvent event) {
        }

        @Override
        public void serviceChanged(ServiceDiscoveryEvent event) {
        }

        public synchronized ServiceItem[] getServiceItem() {
            ServiceItem[] r = new ServiceItem[items.size()];
            items.toArray(r);
            items.clear();
            return r;
        }
    }//end class ServiceDiscoveryManager.ServiceDiscoveryListenerImpl

    /**
     * Data structure used to group together the lease and event sequence
     * number. For each LookupCache, there is a HashMap that maps a ProxyReg to
     * an EventReg.
     * 
     * EventReg's object lock is also used to maintain atomicity of 
     * lookup service state.
     */
    private final static class EventReg {
        /* The Event source from the event registration */
        final Object source;
        /* The Event ID */
        public final long eventID;
        /* The current event sequence number for the Service template */
        private long seqNo;
        /* The Event notification lease */
        public final Lease lease;

        public EventReg(Object source, long eventID, long seqNo, Lease lease) {
            this.source = source;
            this.eventID = eventID;
            this.seqNo = seqNo;
            this.lease = lease;
        }

        /**
         * @param seqNo the seqNo to set, return a positive delta if successful,
         * otherwise a negative value indicates failure.
         */
        public long updateSeqNo(long seqNo) {
            assert Thread.holdsLock(this);
            long difference;
                difference = seqNo - this.seqNo;
                if (difference > 0) {
                    this.seqNo = seqNo;
                    return difference;
                } else {
                    return difference; // Return a negative or zero value
                }
        }
    }//end class ServiceDiscoveryManager.EventReg

    /**
     * Used in the LookupCache. For each LookupCache, there is a HashMap that
     * maps ServiceId to a ServiceItemReg. The ServiceItemReg class helps track
     * where the ServiceItem comes from.
     */
    private final static class ServiceItemReg {
        /* Maps ServiceRegistrars to their latest registered item */

        private final Map<ServiceRegistrar, ServiceItem> items;
        /* The ServiceRegistrar currently being used to track changes */
        private ServiceRegistrar proxy;
        /* Flag that indicates that the ServiceItem has been discarded. */
        private boolean bDiscarded;
        /* The discovered service, prior to filtering. */
        private ServiceItem item;
        /* The discovered service, after filtering. */
        private ServiceItem filteredItem;
        /* Creates an instance of this class, and associates it with the given
         * lookup service proxy.
         */

        public ServiceItemReg(ServiceRegistrar proxy, ServiceItem item) {
            this.bDiscarded = false;
            items = new HashMap<ServiceRegistrar, ServiceItem>();
            this.proxy = proxy;
            items.put(proxy, item);
            this.item = item;
            filteredItem = null;
        }

        /* Adds the given proxy to the 'proxy-to-item' map. This method is
         * called by the newOldService method.  Returns false if the proxy is being used
         * to track changes, true otherwise.
         */
        public boolean proxyNotUsedToTrackChange(ServiceRegistrar proxy, ServiceItem item) {
            synchronized (this) {
                items.put(proxy, item);
                return !proxy.equals(this.proxy);
            }
        }
        
        /**
         * Replaces the proxy used to track change if the proxy passed in is non null,
         * also replaces the ServiceItem.
         * @param proxy replacement proxy
         * @param item replacement item.
         */
        public void replaceProxyUsedToTrackChange(ServiceRegistrar proxy, ServiceItem item){
            synchronized (this) {
                if (proxy != null) this.proxy = proxy;
                this.item = item;
            }
        }
        /* Removes the given proxy from the 'proxy-to-item' map. This method
         * is called from the lookup, handleMatchNoMatch methods and 
         * ProxyRegDropTask.  If this proxy was being used to track changes, 
         * then pick a new one and return its current item, else return null.
         */
        public ServiceItem removeProxy(ServiceRegistrar proxy) {
            synchronized (this) {
                items.remove(proxy);
                if (proxy.equals(this.proxy)) {
                    if (items.isEmpty()) {
                        this.proxy = null;
                    } else {
                        Map.Entry ent = (Map.Entry) items.entrySet().iterator().next();
                        this.proxy = (ServiceRegistrar) ent.getKey();
                        return (ServiceItem) ent.getValue();
                    }//endif
                }//endif
            }
            return null;
        }
        /* Determines if the 'proxy-to-item' map contains any mappings.
         */

        public boolean hasNoProxys() {
            synchronized (this) {
                return items.isEmpty();
            }
        }
        /* Returns the flag indicating whether the ServiceItem is discarded. */

        public boolean isDiscarded() {
            synchronized (this){
                return bDiscarded;
            }
        }
        
        /* Discards if not discarded and returns true if successful */
        public boolean discard() {
            synchronized (this){
                if (!bDiscarded) {
                    bDiscarded = true;
                    return true;
                }
                return false;
            }
        }
        
        /* Undiscards if discarded and returns true if successful */
        public boolean unDiscard(){
            synchronized (this){
                if (bDiscarded){
                    bDiscarded = false;
                    return true;
                }
                return false;
            }
        }

        /**
         * @return the proxy
         */
        public ServiceRegistrar getProxy() {
            synchronized (this) {
                return proxy;
            }
        }

        /**
         * @return the filteredItem
         */
        public ServiceItem getFilteredItem() {
            synchronized (this){
                return filteredItem;
            }
        }

        /**
         * @param filteredItem the filteredItem to set
         */
        public void setFilteredItem(ServiceItem filteredItem) {
            synchronized (this){
                this.filteredItem = filteredItem;
            }
        }

        /**
         * @return the item
         */
        public ServiceItem getItem() {
            synchronized (this){
                return item;
            }
        }
    }

    /**
     * A wrapper class for a ServiceRegistrar.
     */
    private final static class ProxyReg {

        private final ServiceRegistrar proxy;
        private final int hash;

        public ProxyReg(ServiceRegistrar proxy) {
            if (proxy == null) {
                throw new IllegalArgumentException("proxy cannot be null");
            }
            this.proxy = proxy;
            hash = proxy.hashCode();
        }//end constructor

        @Override

        public boolean equals(Object obj) {
            if (obj instanceof ProxyReg) {
                return getProxy().equals(((ProxyReg) obj).getProxy());
            } else {
                return false;
            }
        }//end equals

        @Override
        public int hashCode() {
            return hash;
        }//end hashCode

        /**
         * @return the proxy
         */
        public ServiceRegistrar getProxy() {
            return proxy;
        }

    }//end class ServiceDiscoveryManager.ProxyReg

    /**
     * The Listener class for the LeaseRenewalManager.
     */
    private final class LeaseListenerImpl implements LeaseListener {

        private final ServiceRegistrar proxy;

        public LeaseListenerImpl(ServiceRegistrar proxy) {
            this.proxy = proxy;
        }
        /* When lease renewal fails, we discard the proxy  */

        @Override
        public void notify(LeaseRenewalEvent e) {
            fail(e.getException(), proxy, this.getClass().getName(), "notify",
                    "failure occurred while renewing an event lease", false);
        }
    }//end class ServiceDiscoveryManager.LeaseListenerImpl

    /**
     * Allows termination of LookupCacheImpl so blocking lookup can return
     * quickly
     */
    private static final class LookupCacheTerminator implements Runnable {

        private final BlockingQueue<LookupCacheImpl> cacheList = new LinkedBlockingQueue<LookupCacheImpl>(20);

        @Override
        public void run() {
            while (!cacheList.isEmpty() || !Thread.currentThread().isInterrupted()) {
                try {
                    LookupCacheImpl cache = cacheList.take();
                    synchronized (cache) {
                        cache.terminate();
                    }
                } catch (InterruptedException ex) {
                    logger.log(Level.FINEST, "SDM lookup cache terminator interrupted", ex);
                    Thread.currentThread().interrupt();
                }
            }
        }

        void terminate(LookupCacheImpl cache) {
            boolean added = cacheList.offer(cache);
            if (!added) { // happens if cacheList is full.
                // Do it yourself you lazy caller thread!  Can't you see I'm busy?
                synchronized (cache) {
                    cache.terminate();
                }
            }
        }

    }

    /**
     * ObservableFuture wrapper class for CacheTask's.
     * @param <T>
     */
    static final class CacheTaskWrapper<T> extends ObservableFutureTask<T> {

        private final Object task;

        private CacheTaskWrapper(Runnable r, T result) {
            super(r, result);
            task = r;
        }

        private CacheTaskWrapper(Callable<T> c) {
            super(c);
            task = c;
        }

        private Object getTask() {
            return task;
        }
    }

    /** */
    private static final class CacheTaskDependencyManager implements FutureObserver {

        // CacheTasks pending completion.

        private final ConcurrentLinkedQueue<CacheTaskWrapper> pending;
        private final ExecutorService executor;

        private CacheTaskDependencyManager(ExecutorService e) {
            this.pending = new ConcurrentLinkedQueue<CacheTaskWrapper>();
            executor = e;
        }

        private CacheTaskWrapper submit(Runnable t) {
            CacheTaskWrapper future = new CacheTaskWrapper(t, null);
            pending.offer(future);
            future.addObserver(this);
            if (t instanceof CacheTask && ((CacheTask) t).hasDeps()) {
                List<ObservableFuture> deps = new LinkedList<ObservableFuture>();
                Iterator<CacheTaskWrapper> it = pending.iterator();
                while (it.hasNext()) {
                    CacheTaskWrapper w = it.next();
                    Object c = w.getTask();
                    if (c instanceof CacheTask && ((CacheTask) t).dependsOn((CacheTask) c)) {
                        deps.add(w);
                    }
                }
                if (deps.isEmpty()) {
                    executor.submit(future);
                    logger.log(Level.FINEST,
                            "ServiceDiscoveryManager {0} submitted to executor task queue",
                            t.toString());
                } else {
                    DependencyLinker linker = new DependencyLinker(executor, deps, future);
                    linker.register();
                    logger.log(Level.FINEST,
                            "ServiceDiscoveryManager {0} registered dependencies",
                            t.toString());
                }
            } else {
                executor.submit(future);
                logger.log(Level.FINEST,
                        "ServiceDiscoveryManager {0} submitted to executor task queue",
                        t.toString());
            }
            return future;
        }

        @Override
        public void futureCompleted(Future e) {
            pending.remove(e);
            Object t;
            if (e instanceof CacheTaskWrapper) {
                t = ((CacheTaskWrapper) e).getTask();
            } else {
                t = e;
            }
            logger.log(Level.FINEST,
                    "ServiceDiscoveryManager {0} completed execution",
                    t.toString());
        }

        /**
         * Removes from the cache's task queue, all pending tasks associated
         * with the given ProxyReg. This method is called when the given
         * ProxyReg has been discarded.
         */
        private void removeUselessTask(ProxyReg reg) {
            Iterator<CacheTaskWrapper> it = pending.iterator();
            while (it.hasNext()) {
                CacheTaskWrapper w = it.next();
                Object t = w.getTask();
                if (t instanceof CacheTask && ((CacheTask) t).isFromProxy(reg)) {
                    w.cancel(true); // Also causes task to be removed
                    logger.log(Level.FINEST,
                            "ServiceDiscoveryManager {0} cancelled",
                            t.toString());
                }
            }//end loop
        }//end LookupCacheImpl.removeUselessTask
    }

    /**
     * Internal implementation of the LookupCache interface. Instances of this
     * class are used in the blocking versions of lookup() and are returned by
     * createLookupCache.
     */
    private static final class LookupCacheImpl implements LookupCache {
        private ExecutorService eventNotificationExecutor;

        /* RemoteEventListener class that is registered with the proxy to
         * receive notifications from lookup services when any ServiceItem
         * changes (NOMATCH_MATCH, MATCH_NOMATCH, MATCH_MATCH)
         */
        private final class LookupListener implements RemoteEventListener,
                ServerProxyTrust {

            RemoteEventListener export() throws ExportException {
                return (RemoteEventListener) lookupListenerExporter.export(this);
            }

            @Override
            public void notify(RemoteEvent evt) throws UnknownEventException,
                    java.rmi.RemoteException {
                if (!(evt instanceof ServiceEvent)) {
                    throw new UnknownEventException("ServiceEvent required,not: "
                            + evt.toString());
                }
                ServiceEvent theEvent = (ServiceEvent) evt;
		notifyServiceMap( theEvent.getSource(),
				  theEvent.getID(),
				  theEvent.getSequenceNumber(),
				  theEvent.getServiceID(),
				  theEvent.getServiceItem(),
				  theEvent.getTransition() );
            }

            /**
             * Returns a <code>TrustVerifier</code> which can be used to verify
             * that a given proxy to this listener can be trusted.
             */
            @Override
            public TrustVerifier getProxyVerifier() {
                return new BasicProxyTrustVerifier(lookupListenerProxy);
            }//end getProxyVerifier
        }//end class LookupCacheImpl.LookupListener

        /**
         * This task class, when executed, first registers to receive
         * ServiceEvents from the given ServiceRegistrar. If the registration
         * process succeeds (no RemoteExceptions), it then performs a lookup
         * to query the given ServiceRegistrar for a "snapshot" of
         * its current state with respect to services that match the given
         * template.
         *
         * Note that the order of execution is important. That
         * is, the lookup must be executed only after registration for
         * events has completed. This is because when an entity registers with
         * the event mechanism of a ServiceRegistrar, the entity will only
         * receive notification of events that occur "in the future", after the
         * registration is made. The entity will not receive events about
         * changes to the state of the ServiceRegistrar that may have occurred
         * before or during the registration process.
         *
         * Thus, if the order of these tasks were reversed and lookup
         * were to be performed prior to the RegisterListenerTask, then the
         * possibility exists for the occurrence of a change in the
         * ServiceRegistrar's state between the time lookup retrieves a
         * snapshot of that state, and the time the event registration process
         * has completed, resulting in an incorrect view of the current state of
         * the ServiceRegistrar.
         */
        private static final class RegisterListenerTask extends CacheTask {

            private final LookupCacheImpl cache;

            public RegisterListenerTask(
                    ProxyReg reg,
                    long seqN,
                    LookupCacheImpl cache) {
                super(reg, seqN);
                this.cache = cache;
            }

            @Override
            public boolean hasDeps() {
                return true;
            }

            @Override
            public boolean dependsOn(CacheTask t) {
                if (t instanceof ProxyRegDropTask) {
                    ProxyReg r = getProxyReg();
                    if (r != null && r.equals(t.getProxyReg())) {
                        if (t.getSeqN() < getSeqN()) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public void run() {
                logger.finest("ServiceDiscoveryManager - RegisterListenerTask "
                        + "started");
                long duration = cache.getLeaseDuration();
                if (duration < 0) {
                    return;
                }
                try {
                    EventReg eventReg = cache.sdm.registerListener(reg.getProxy(),
                            cache.tmpl,
                            cache.lookupListenerProxy,
                            duration);
                    // eventReg is a new object not visible to other threads yet.
                    // It will be safely published using a ConcurrentMap, so
                    // we don't need to synchronize here.
                    /* Cancel the lease if the cache has been terminated */
                    if (cache.bCacheTerminated || Thread.currentThread().isInterrupted()) {
                        // eventReg.lease is final and is already safely published
                        cache.sdm.cancelLease(eventReg.lease);
                        return;
                    } else {
                        EventReg existed = cache.eventRegMap.putIfAbsent(reg, eventReg);
                        while (existed != null) {
                            // Cancel existing lease.
                            cache.sdm.cancelLease(existed.lease);
                            boolean replaced = cache.eventRegMap.replace(reg, existed, eventReg);
                            if (replaced) {
                                break;
                            } else {
                                existed = cache.eventRegMap.putIfAbsent(reg, eventReg);
                            }
                        }
                        /* Execute the lookup only if there were no problems */
                        synchronized (eventReg){
                            cache.lookup(reg, eventReg);
                        }
                    }//endif
                } catch (Exception e) {
                    cache.sdm.fail(e, reg.getProxy(), this.getClass().getName(), "run",
                            "Exception occurred while attempting to register "
                            + "with the lookup service event mechanism",
                            cache.bCacheTerminated);
                }
                logger.finest("ServiceDiscoveryManager - RegisterListenerTask "
                        + "completed");
            }//end run

        }//end class LookupCacheImpl.RegisterListenerTask

        /**
         * When the given registrar is discarded, this Task class is used to
         * remove the registrar from the various maps maintained by this cache.
         */
        private static final class ProxyRegDropTask extends CacheTask {

            private final LookupCacheImpl cache;
            private final EventReg eReg;

            public ProxyRegDropTask(
                    ProxyReg reg, EventReg eReg, long seqN, LookupCacheImpl cache) {
                super(reg, seqN);
                this.cache = cache;
                this.eReg = eReg;
            }

            @Override
            public void run() {
                logger.finest("ServiceDiscoveryManager - ProxyRegDropTask "
                        + "started");
                //lease has already been cancelled by removeProxyReg
                cache.eventRegMap.remove(reg, eReg);
                /* For each itemReg in the serviceIdMap, disassociate the
                 * lookup service referenced here from the itemReg; and
                 * if the itemReg then has no more lookup services associated
                 * with it, remove the itemReg from the map and send a
                 * service removed event.
                 */
                Iterator<Map.Entry<ServiceID, ServiceItemReg>> iter
                        = cache.serviceIdMap.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<ServiceID, ServiceItemReg> e = iter.next();
                    ServiceID srvcID = e.getKey();
                    ServiceItemReg itemReg = e.getValue();
                    /* mutating the map would not have been allowed while
                     * iterating when using a standard map.
                     */
                    ServiceRegistrar proxy = null;
                    ServiceItem item;
                    synchronized (itemReg){
                        item = itemReg.removeProxy(reg.getProxy());//disassociate the LUS
                        if (item != null) {// new LUS chosen to track changes
                            proxy = itemReg.getProxy();
                            cache.itemMatchMatchChange(srvcID, itemReg, proxy, item, false);
                        } else if (itemReg.hasNoProxys()) {//no more LUSs, remove from map
                            item = itemReg.getFilteredItem();
                            iter.remove();
                            if (!itemReg.isDiscarded()) cache.removeServiceNotify(item);
                        }//endif
                    }
                    
                }//end loop
                logger.finest("ServiceDiscoveryManager - ProxyRegDropTask "
                        + "completed");
            }//end run

            @Override
            public boolean hasDeps() {
                return true;
            }

            @Override
            public boolean dependsOn(CacheTask t) {
                if (t instanceof RegisterListenerTask 
                        || t instanceof ProxyRegDropTask) {
                    ProxyReg r = getProxyReg();
                    if (r != null && r.equals(t.getProxyReg())) {
                        if (t.getSeqN() < getSeqN()) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }//end class LookupCacheImpl.ProxyRegDropTask

        /**
         * Task class used to determine whether or not to "commit" a service
         * discard request, increasing the chances that the service will
         * eventually be re-discovered. This task is also used to attempt a
         * filter retry on an item in which the cache's filter initially
         * returned indefinite.
         */
        private static final class ServiceDiscardTimerTask implements Runnable {

            private final ServiceID serviceID;
            private final long endTime;
            private final LookupCacheImpl cache;

            public ServiceDiscardTimerTask(
                    LookupCacheImpl cache, ServiceID serviceID) {
                this.serviceID = serviceID;
                this.cache = cache;
                this.endTime = cache.sdm.discardWait + System.currentTimeMillis();
            }//end constructor

            @Override
            public void run() {
                logger.finest("ServiceDiscoveryManager - "
                        + "ServiceDiscardTimerTask started");
                /* Exit if this cache has already been terminated. */
                if (cache.bCacheTerminated) {
                    return;
                }
                /* Simply return if a MATCH_NOMATCH event arrived for this
                 * item prior to this task running and as a result, the item
                 * was removed from the map.
                 */
                if (!cache.serviceIdMap.containsKey(serviceID)) {
                    return;
                }
                long curDur = endTime - System.currentTimeMillis();
                synchronized (this) {
                    /* Wait until interrupted or time expires */
                    while (curDur > 0) {
                        try {
                            this.wait(curDur);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return; // Task cancelled
                        }
                        /* Exit if this cache was terminated while waiting. */
                        if (cache.bCacheTerminated) {
                            return;
                        }
                        /* Either the wait period has completed or has been
                         * interrupted. If the service ID is no longer in
                         * in the serviceIdMap, then it's assumed that a
                         * MATCH_NOMATCH event must have arrived which could be
                         * viewed as an indication that the service's lease
                         * expired, which then could be interpreted as meaning
                         * the service is actually down, and will be
                         * re-discovered when it comes back on line. In that
                         * case, exit the thread.
                         */
                        if (!cache.serviceIdMap.containsKey(serviceID)) {
                            return;
                        }
                        curDur = endTime - System.currentTimeMillis();
                    }//end loop
                }//end sync
                /* The thread was not interrupted, time expired.
                 *
                 * If the service ID is still contained in the serviceIdMap,
                 * then a MATCH_NOMATCH event did not arrive, which is
                 * interpreted here to mean that the service is still up.
                 * The service ID will still be in the map if one (or both)
                 * of the following is true:
                 *  - the client discarded an unreachable service that never
                 *    actually went down (so it's lease never expired, and
                 *    a MATCH_NOMATCH event was never received)
                 *  - upon applying the cache's filter to the service, the
                 *    filter returned indefinite, and this task was queued
                 *    to request that filtering be retried at a later time
                 *
                 * For the first case above, the service is "un-discarded" so
                 * the service will be available to the client again. For the
                 * second case, the filter is retried. If the service passes
                 * the filter, the service is "un-discarded"; otherwise, it is
                 * 'quietly' removed from the map (because a service removed
                 * event was already sent when the service was originally
                 * discarded.
                 */
                ServiceItemReg itemReg = cache.serviceIdMap.get(serviceID);
                if (itemReg != null) { //Discarded status hasn't changed
                    ServiceItem itemToSend;
                    synchronized (itemReg){
                        if (!itemReg.unDiscard()) return; // "un-discards"
                        ServiceItem item;
                        ServiceItem filteredItem = null;
                        itemToSend = itemReg.getFilteredItem();
                        if (itemToSend == null) {
                            item = itemReg.getItem().clone();
                            filteredItem = item.clone();
                            //retry the filter
                            if (cache.sdm.filterPassed(filteredItem, cache.filter)) {
                                itemToSend = cache.addFilteredItemToMap(item, filteredItem, itemReg); 
                                if (itemToSend == null) {
                                    return;
                                }
                            } else {//'quietly' remove the item
                                cache.removeServiceIdMapSendNoEvent(serviceID, itemReg);
                                return;
                            }//endif
                        }//endif
                        /* Either the filter was retried and passed, in which case,
                         * the filtered itemCopy was placed in the map and 
                         * "un-discarded"; or the
                         * filter wasn't applied above (a non-null filteredItem
                         * field in the itemReg in the map means that the filter
                         * was applied at some previous time). In the latter case, the
                         * service can now be "un-discarded", and a notification
                         * that the service is now available can be sent for either case.
                         */
                        cache.addServiceNotify(itemToSend);
                    }
                }
                logger.finest("ServiceDiscoveryManager - "
                        + "ServiceDiscardTimerTask completed");
            }//end run
        }

        private final static int ITEM_ADDED = 0;
        private final static int ITEM_REMOVED = 2;
        private final static int ITEM_CHANGED = 3;

        /* The listener that receives remote events from the lookup services */
        private final LookupListener lookupListener;
        /* Exporter for the remote event listener (lookupListener) */
        private volatile Exporter lookupListenerExporter;
        /* Proxy to the listener that receives remote events from lookups */
        private volatile RemoteEventListener lookupListenerProxy;
        /**
         * Task manager for the various tasks executed by this LookupCache
         */
        private volatile ExecutorService cacheTaskMgr;
        private volatile CacheTaskDependencyManager cacheTaskDepMgr;
        /* Flag that indicates if the LookupCache has been terminated. */
        private volatile boolean bCacheTerminated = false;
        /* Contains the ServiceDiscoveryListener's that receive local events */
        private final Collection<ServiceDiscoveryListener> sItemListeners;
        /* Map from ServiceID to ServiceItemReg */
        private final ConcurrentMap<ServiceID, ServiceItemReg> serviceIdMap;
        /* Map from ProxyReg to EventReg: (proxyReg, {source,id,seqNo,lease})*/
        private final ConcurrentMap<ProxyReg, EventReg> eventRegMap;
        /* Template current cache instance should use for primary matching */
        private final ServiceTemplate tmpl;
        /* Filter current cache instance should use for secondary matching */
        private final ServiceItemFilter filter;
        /* Desired lease duration to request from lookups' event mechanisms */
        private final long leaseDuration;
        /* Log the time when the cache gets created. This value is used to
         * calculate the time when the cache should expire.
         */
        private final long startTime;
        /**
         * For tasks waiting on verification events after service discard
         */
        private volatile ExecutorService serviceDiscardTimerTaskMgr;
        private final ConcurrentMap<ServiceID, Future> serviceDiscardFutures;
        /**
         * Whenever a ServiceIdTask is created in this cache, it is assigned a
         * unique sequence number to allow such tasks associated with the same
         * ServiceID to be executed in the order in which they were queued in
         * the TaskManager. This field contains the value of the sequence number
         * assigned to the most recently created ServiceIdTask.
         */
        private final AtomicLong taskSeqN;

        private final ServiceDiscoveryManager sdm;

        public LookupCacheImpl(
                ServiceTemplate tmpl,
                ServiceItemFilter filter,
                ServiceDiscoveryListener sListener,
                long leaseDuration,
                ServiceDiscoveryManager sdm) throws RemoteException {
            this.taskSeqN = new AtomicLong();
            this.startTime = System.currentTimeMillis();
            this.eventRegMap = new ConcurrentHashMap<ProxyReg, EventReg>();
            this.serviceIdMap = new ConcurrentHashMap<ServiceID, ServiceItemReg>();
            this.sItemListeners = new HashSet<ServiceDiscoveryListener>();
            this.serviceDiscardFutures = RC.concurrentMap(new ConcurrentHashMap<Referrer<ServiceID>, Referrer<Future>>(), Ref.WEAK_IDENTITY, Ref.STRONG, 60000, 60000);
            this.tmpl = copyServiceTemplate(tmpl);
            this.leaseDuration = leaseDuration;
            this.filter = filter;
            lookupListener = new LookupListener();
            if (sListener != null) {
                sItemListeners.add(sListener);
            }
            this.sdm = sdm;
        }//end constructor

        // This method's javadoc is inherited from an interface of this class
        @Override
        public void terminate() {
            synchronized (this) {
                if (bCacheTerminated) {
                    return;//allow for multiple terminations
                }
                bCacheTerminated = true;
            }//end sync
            synchronized (sdm.caches) {
                sdm.caches.remove(this);
            }
            /* Terminate all tasks: first, terminate this cache's Executors*/
            cacheTaskMgr.shutdownNow();
            /* Terminate ServiceDiscardTimerTasks running for this cache */
            serviceDiscardTimerTaskMgr.shutdownNow();
            eventNotificationExecutor.shutdownNow();
            /* Cancel all event registration leases held by this cache. */
            Set set = eventRegMap.entrySet();
            Iterator iter = set.iterator();
            while (iter.hasNext()) {
                Map.Entry e = (Map.Entry) iter.next();
                EventReg eReg = (EventReg) e.getValue();
                sdm.cancelLease(eReg.lease);
            }//end loop
            /* Un-export the remote listener for events from lookups. */
            try {
                lookupListenerExporter.unexport(true);
            } catch (IllegalStateException e) {
                logger.log(Level.FINEST,
                        "IllegalStateException occurred while unexporting "
                        + "the cache's remote event listener",
                        e);
            }
            logger.finest("ServiceDiscoveryManager - LookupCache terminated");
        }//end LookupCacheImpl.terminate

        // This method's javadoc is inherited from an interface of this class
        @Override
        public ServiceItem lookup(ServiceItemFilter myFilter) {
            checkCacheTerminated();
            ServiceItem[] ret = getServiceItems(myFilter);
            if (ret.length == 0) {
                return null;
            }
            // Maths.abs(Integer.MIN_VALUE) = -ve, so to avoid random 
            // hard to debug bugs, this has been changed.
            int rand = sdm.random.nextInt(ret.length);
            return ret[rand];
        }//end LookupCacheImpl.lookup

        // This method's javadoc is inherited from an interface of this class
        @Override
        public ServiceItem[] lookup(ServiceItemFilter myFilter, int maxMatches) {
            checkCacheTerminated();
            if (maxMatches < 1) {
                throw new IllegalArgumentException("maxMatches must be > 0");
            }
            List<ServiceItem> items = new LinkedList<ServiceItem>();
            ServiceItem[] sa = getServiceItems(myFilter);
            int len = sa.length;
            if (len == 0) {
                return new ServiceItem[0];
            }
            int rand = sdm.random.nextInt(Integer.MAX_VALUE) % len;
            for (int i = 0; i < len; i++) {
                items.add(sa[(i + rand) % len]);
                if (items.size() == maxMatches) {
                    break;
                }
            }//end loop
            ServiceItem[] ret = new ServiceItem[items.size()];
            items.toArray(ret);
            return ret;
        }//end LookupCacheImpl.lookup

        // This method's javadoc is inherited from an interface of this class
        @Override
        public void discard(Object serviceReference) {
            checkCacheTerminated();
            /* Loop through the serviceIdMap, looking for the itemReg that
             * corresponds to given serviceReference. If such an itemReg
             * exists, and it's not already discarded, then queue a task
             * to discard the given serviceReference.
             */
            Iterator<Map.Entry<ServiceID, ServiceItemReg>> iter = getServiceIdMapEntrySetIterator();
            ServiceItem filteredItem;
            ServiceID sid;
            while (iter.hasNext()) {
                Map.Entry<ServiceID, ServiceItemReg> e = iter.next();
                ServiceItemReg itmReg = e.getValue();
                synchronized (itmReg){
                    filteredItem = itmReg.getFilteredItem();
                    if ( filteredItem != null && (filteredItem.service).equals(serviceReference)) {
                        sid = e.getKey();
                        if (itmReg.discard()) {
                            Future f = serviceDiscardTimerTaskMgr.submit(
                                    new ServiceDiscardTimerTask(this, sid));
                            serviceDiscardFutures.put(sid, f);
                            removeServiceNotify(filteredItem);
                        }
                        return;
                    }//endifeg)
                }//end sync
            }//end loop
        }//end LookupCacheImpl.discard

        /* Returns the iterator of entry set in the copy of the ServiceIdMap */
        private Iterator<Map.Entry<ServiceID, ServiceItemReg>> getServiceIdMapEntrySetIterator() {
            return serviceIdMap.entrySet().iterator();
        }//end LookupCacheImpl.getServiceIdMapEntrySetIterator

        /**
         * This method returns a <code>ServiceItem</code> array containing
         * elements that satisfy the following conditions: - is referenced by
         * one of the <code>itemReg</code> elements contained in the
         * <code>serviceIdMap</code> - is not currently discarded - satisfies
         * the given <code>ServiceItemFilter</code>
         *
         * Note that the <code>filter</code> parameter is a "2nd stage" filter.
         * That is, for each <code>itemReg</code> element in the
         * <code>serviceIdMap</code>, the "1st stage" filter corresponding to
         * the current instance of <code>LookupCache</code> has already been
         * applied to the <code>ServiceItem</code> referenced in that
         * <code>itemReg</code>. The <code>ServiceItemFilter</code> applied here
         * is supplied by the entity interacting with the cache, and provides a
         * second filtering process. Thus, this method applies the given
         * <code>filter</code> parameter to the <code>filteredItem</code> field
         * (not the <code>item</code> field) of each non-discarded
         * <code>itemReg</code> element in the <code>serviceIdMap</code>.
         *
         * This method returns all the instances of <code>ServiceItem</code>
         * that pass the given <code>filter</code>; and it discards all the
         * items that produce an indefinite result when that <code>filter</code>
         * is applied.
         */
        private ServiceItem[] getServiceItems(ServiceItemFilter filter2) {
            List<ServiceItem> items = new LinkedList<ServiceItem>();
            Iterator<Map.Entry<ServiceID, ServiceItemReg>> iter
                    = getServiceIdMapEntrySetIterator();
            while (iter.hasNext()) {
                Map.Entry<ServiceID, ServiceItemReg> e = iter.next();
                ServiceItemReg itemReg = e.getValue();
                Object serviceToDiscard;
                ServiceItem itemToFilter;
                synchronized (itemReg){
                    if ((itemReg.isDiscarded())
                            || (itemReg.getFilteredItem() == null)) {
                        continue;
                    }
                    /* Make a copy because the filter may change it to null */
                    itemToFilter = itemReg.getFilteredItem().clone();
                    serviceToDiscard = itemReg.getItem().service;
                }
                /* Apply the filter */
                boolean pass = ((filter2 == null) || (filter2.check(itemToFilter)));
                /* Handle filter fail - skip to next item */
                if (!pass) {
                    continue;
                }
                /* Handle filter pass - add item to return set */
                if (itemToFilter.service != null) {
                    items.add(itemToFilter);
                    continue;
                }//endif(pass)
                /* Handle filter indefinite - discard the item */
                discard(serviceToDiscard);
            }//end loop
            ServiceItem[] ret = new ServiceItem[items.size()];
            items.toArray(ret);
            return ret;
        }//end LookupCacheImpl.getServiceItems

        // This method's javadoc is inherited from an interface of this class
        @Override
        public void addListener(ServiceDiscoveryListener listener) {
            checkCacheTerminated();
            if (listener == null) {
                throw new NullPointerException("can't add null listener");
            }
            //No action is taken if not added according to LookupCache
            ServiceItem[] items = getServiceItems(null);
            synchronized (sItemListeners) {
                if (sItemListeners.add(listener)) {
                    for (int i = 0, l = items.length; i < l; i++) {
                        addServiceNotify(items[i], listener);
                    }//end loop
                }
            }

        }//end LookupCacheImpl.addListener

        // This method's javadoc is inherited from an interface of this class
        @Override
        public void removeListener(ServiceDiscoveryListener listener) {
            checkCacheTerminated();
            if (listener == null) {
                return;
            }
            synchronized (sItemListeners) {
                sItemListeners.remove(listener);
            }
        }//end LookupCacheImpl.removeListener

        /**
         * Add a new ProxyReg to the lookupCache. Called by the constructor and
         * the DiscMgrListener's discovered() method.
         *
         * @param reg a ProxyReg to add.
         */
        public void addProxyReg(ProxyReg reg) {
            RegisterListenerTask treg
                    = new RegisterListenerTask(
                            reg,
                            taskSeqN.getAndIncrement(),
                            this);
            cacheTaskDepMgr.submit(treg);
        }//end LookupCacheImpl.addProxyReg

        /**
         * Remove a ProxyReg from the lookupCache. Called by DiscMgrListener's
         * discarded() method.
         *
         * @param reg a ProxyReg to remove.
         */
        public void removeProxyReg(ProxyReg reg) {
            ProxyRegDropTask t;
            //let the ProxyRegDropTask do the eventRegMap.remove
            EventReg eReg = eventRegMap.get(reg);
            if (eReg != null) {
                try {
                    sdm.leaseRenewalMgr.remove(eReg.lease);
                } catch (Exception e) {
                    logger.log(Level.FINER,
                            "exception occurred while removing an "
                            + "event registration lease", e);
                }
            }//endif
            t = new ProxyRegDropTask(reg, eReg, taskSeqN.getAndIncrement(), this);
            cacheTaskDepMgr.removeUselessTask(reg);
            cacheTaskDepMgr.submit(t);
        }//end LookupCacheImpl.removeProxyReg

        /* Throws IllegalStateException if this lookup cache has been
         * terminated
         */
        private void checkCacheTerminated() {
            sdm.checkTerminated();
            if (bCacheTerminated) {
                throw new IllegalStateException("this lookup cache was terminated");
            }//endif
        }//end LookupCacheImpl.checkCacheTerminated

        /**
         * Called by the lookupListener's notify() method. Checks the event
         * sequence number and, based on whether or not a "gap" is found in in
         * the event sequence, performs lookup (if a gap was found) or
         * processes the event if no gap was found.
         *
         * Recall that the Event specification states that if the sequence
         * numbers of two successive events differ by only 1, then one can be
         * assured that no events were missed. On the other hand, if the
         * difference is greater than 1 (the sequence contains a "gap"), then
         * one or more events may -- or may not -- have been missed. Thus, if a
         * gap is found in the events, although it's possible that no events
         * were missed, this method takes the conservative approach by assuming
         * events were missed. When this method determines that an event may
         * have been missed, it requests a current "snapshot" of the given
         * ServiceRegistrar's state by performing lookup.
         * Since this method can safely assume that no events have been missed
         * if it finds no gaps in the event sequence, it can safely process 
         * the event maintaining equivalent state to the registrar, that is
         * finding and filtering new services and filtering updated or changed
         * existing services.
         *
         * Note that when a lookup service is discovered, this utility registers
         * with that lookup service's event mechanism for service events related
         * to the services of interest. Upon registering with the event
         * mechanism, a data structure (of type EventReg) containing information
         * about that registration is placed in a Map for later processing when
         * events do arrive. If the timing is right, it is possible that a
         * service event may arrive between the time the registration is made
         * and the time the EventReg is stored in the map. Thus, this method may
         * find that the eventRegMap does not contain an element corresponding
         * to the event this method is currently processing. In that case, this
         * method will do nothing. It will simply return so that the service
         * referenced in the event can be discovered using the snapshot returned
         * by the lookup method that is ultimately c by the
         * RegisterListenerTask (whose listener registration caused this method
         * to be invoked in the first place).
         */
        private void notifyServiceMap(
                Object eventSource,
                long eventID,
                long seqNo,
                ServiceID sid,
                ServiceItem item,
                int transition) {
            if (eventSource == null) {
                return;
            }
            /* Search eventRegMap for ProxyReg corresponding to event. */
            ProxyReg reg = null;
            EventReg eReg = null;
            Set<Map.Entry<ProxyReg, EventReg>> set = eventRegMap.entrySet();
            Iterator<Map.Entry<ProxyReg, EventReg>> iter = set.iterator();
            while (iter.hasNext()) {
                Map.Entry<ProxyReg, EventReg> e = iter.next();
                eReg = e.getValue();
                if ((eventID == eReg.eventID && eventSource.equals(eReg.source))) {
                    reg = e.getKey();
                    break;
                }//endif
            }//end loop
            if(reg == null || eReg == null) return;//event arrived before eventReg in map

            /* Next, look for any gaps in the event sequence. */
            synchronized (eReg) {
                long delta = eReg.updateSeqNo(seqNo);
                CacheTask t;
                if (delta == 1) {//no gap, handle current event
                    /* Fix for Bug ID 4378751. The conditions described by that
                     * bug involve a ServiceItem (corresponding to a previously
                     * discovered service ID) having a null service field. A
                     * null service field is due to an UnmarshalException caused
                     * by a SecurityException that results from the lack of a
                     * connection permission for the lookup service codebase
                     * to the service's remote codebase. Skip this ServiceItem,
                     * otherwise an un-expected serviceRemoved event will result
                     * because the primary if-block will be unintentionally
                     * entered due to the null service field in the ServiceItem.
                     */
                    if (((item != null) && (item.service == null))) {
                        return;
                    }
                    /* Handle the event by the transition type, and by whether
                     * the associated ServiceItem is an old, previously discovered
                     * item, or a newly discovered item.
                     */
                    if(transition == ServiceRegistrar.TRANSITION_MATCH_NOMATCH) {
                            handleMatchNoMatch(reg.getProxy(), sid);
                    } else if (transition == ServiceRegistrar.TRANSITION_NOMATCH_MATCH ||
                            transition == ServiceRegistrar.TRANSITION_MATCH_MATCH) {
                        newOldService(reg,item,
                            transition == ServiceRegistrar.TRANSITION_MATCH_MATCH);
                    }//endif(transition)
                } else if (delta == 0) { // Ignore
                } else if (delta < 0) { // Ignore
                } else {//gap in event sequence, request snapshot
                    lookup(reg, eReg);
                }//endif
            } //end sync(eReg)
        }//end LookupCacheImpl.notifyServiceMap
        
        /**
         * Requests a "snapshot" of the given registrar's state.
         */
        private void lookup(ProxyReg reg, EventReg eReg){
            assert Thread.holdsLock(eReg);
            ServiceRegistrar proxy = reg.getProxy();
            ServiceMatches matches;
            /* For the given lookup, get all services matching the tmpl */
            try {
                // Don't like the fact that we're calling foreign code while
                // holding an object lock, however holding this lock doesn't
                // provide an opportunity for DOS as the lock only relates to a specific 
                // ServiceRegistrar and doesn't interact with client code.
                matches = proxy.lookup(tmpl, Integer.MAX_VALUE);
            } catch (Exception e) {
                // ReRegisterGoodEquals test failure becomes more predictable
                // when fail is only called if decrement is successful.
                sdm.fail(e, proxy, this.getClass().getName(), "run",
                        "Exception occurred during call to lookup",
                        bCacheTerminated);
                return;
            }
            if (matches.items == null) {
                throw new AssertionError("spec violation in queried "
                        + "lookup service: ServicesMatches instance "
                        + "returned by call to lookup() method contains "
                        + "null 'items' field");
            }
            /* 1. Cleanup "orphaned" itemReg's. */
            Iterator<Map.Entry<ServiceID, ServiceItemReg>> iter
                    = serviceIdMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<ServiceID, ServiceItemReg> e = iter.next();
                ServiceID srvcID = e.getKey();
                ServiceItem itemInSnapshot = findItem(srvcID,
                        matches.items);
                if (itemInSnapshot != null) {
                    continue;//not an orphan
                }
                if (Thread.currentThread().isInterrupted()) {
                    continue; // skip
                }
                /* removal is performed whilst holding a lock on itemReg.*/
                ServiceItemReg itemReg = e.getValue();
                ServiceRegistrar prxy;
                ServiceItem item;
                synchronized (itemReg){
                    item = itemReg.removeProxy(reg.getProxy());//disassociate the LUS
                    if (item != null) {// new LUS chosen to track changes
                        prxy = itemReg.getProxy();
                        itemMatchMatchChange(srvcID, itemReg, prxy, item, false);
                    } else if (itemReg.hasNoProxys()) {//no more LUSs, remove from map
                        item = itemReg.getFilteredItem();
                        iter.remove();
                        if (!itemReg.isDiscarded()) removeServiceNotify(item);
                    }//endif
                }
            }//end loop
            /* 2. Handle "new" and "old" items from the given lookup */
            for (int i = 0, l = (matches.items).length; i < l; i++) {
                /* Skip items with null service field (Bug 4378751) */
                if ((matches.items[i]).service == null) {
                    continue;
                }
                newOldService(reg,matches.items[i],false);
            }//end loop
        }

        /**
         * Method used to  process the service state
         * ("snapshot"), matching this cache's template, retrieved from
         * the given lookup service.
         *
         * After retrieving the snapshot S, the lookup method calls this method
         * for each service referenced in S. This method determines if
         * the given service is an already-discovered service (is currently in
         * this cache's serviceIdMap), or is a new service. This method handles
         * the service differently, depending on whether the service is a new or
         * old.
         *
         * a. if the item is old, then this method will: - compare the given item
         * from the snapshot to the UN-filtered item in given itemReg if(same
         * version but attributes have changed) send changed event else if(
         * version has changed ) send removed event followed by added event else
         * do nothing - apply the filter to the given item if(filter fails) send
         * removed event else if(filter passes) set the filtered item in the
         * itemReg in the map else if (filter is indefinite) discard item send
         * removed event queue another filter attempt for later b. if the given
         * item is newly discovered, then this task will: - create a new
         * ServiceItemReg containing the given item - place the new itemReg in
         * the serviceIdMap - apply the filter to the given item if(filter
         * fails) remove the item from the map but send NO removed event else
         * if(filter passes) send added event for the FILTERED item else if
         * (filter is indefinite) discard item queue another filter attempt for
         * later but send NO removed event
         */
        private void newOldService(ProxyReg reg, ServiceItem item, boolean matchMatchEvent)
        {
            boolean previouslyDiscovered = false;
            ServiceItemReg itemReg;
            ServiceID thisTaskSid = item.serviceID;
            itemReg = serviceIdMap.get(thisTaskSid);
            if (itemReg == null) {
                if( !eventRegMap.containsKey(reg) ) {
                    /* reg must have been discarded, simply return */
                    return;
                }//endif
                // else
                itemReg = new ServiceItemReg(reg.getProxy(), item);
                ServiceItemReg existed = serviceIdMap.putIfAbsent(thisTaskSid, itemReg);
                if (existed != null) {
                    itemReg = existed;
                    previouslyDiscovered = true;
                }
            } else if (itemReg.isDiscarded()) {
                return;
            } else {
                previouslyDiscovered = true;
            }

            if (previouslyDiscovered) {//a. old, previously discovered item
                    itemMatchMatchChange(thisTaskSid, itemReg,
                            reg.getProxy(), item, matchMatchEvent);
            } else {//b. newly discovered item
                    ServiceItem newFilteredItem;
                    synchronized (itemReg){
                        newFilteredItem = filterMaybeDiscard(thisTaskSid, itemReg, item, false);
                    }
                    if (newFilteredItem != null) {
                        addServiceNotify(newFilteredItem);
                    }//endif
            }//endif
        }
        /**
         * Removes an entry in the serviceIdMap, but sends no notification.
         */
        private boolean removeServiceIdMapSendNoEvent(ServiceID sid, ServiceItemReg itemReg) {
            return serviceIdMap.remove(sid, itemReg);
        }//end LookupCacheImpl.removeServiceIdMapSendNoEvent

        /**
         * Returns the element in the given items array having the given
         * ServiceID.
         */
        private ServiceItem findItem(ServiceID sid, ServiceItem[] items) {
            if (items != null) {
                for (int i = 0, length = items.length; i < length; i++) {
                    if (items[i].serviceID.equals(sid)) {
                        return items[i];
                    }
                }//end loop
            }//endif
            return null;
        }//end LookupCacheImpl.findItem

        /**
         * With respect to a given service (referenced by the parameter
         * newItem), if either an event has been received from the given lookup
         * service (referenced by the proxy parameter), or a snapshot of the
         * given lookup service's state has been retrieved, this method
         * determines whether the service's attributes have changed, or whether
         * a new version of the service has been registered. After the
         * appropriate determination has been made, this method applies the
         * filter associated with the current cache and sends the appropriate
         * local ServiceDiscoveryEvent(s).
         *
         * This method is called under the following conditions: - when a new
         * lookup service is discovered, this method will be called for each
         * previously discovered service - when a gap in the events from a
         * previously discovered lookup service is discovered, this method will
         * be called for each previously discovered service - when a MATCH_MATCH
         * event is received, this method will be called for each previously
         * discovered service - when a NOMATCH_MATCH event is received, this
         * method will be called for each previously discovered service Note
         * that this method is never called when a MATCH_NOMATCH event is
         * received; such an event is always handled by the handleMatchNoMatch
         * method.
         *
         * When this method is called, it may send one of the following events
         * or combination of events: - a service changed event - a service
         * removed event followed by a service added event - a service removed
         * event
         *
         * A service removed event is sent when the service either fails the
         * filter, or the filter produces an indefinite result; in which case,
         * the service is also discarded.
         *
         * A service changed event is sent when the service passes the filter,
         * and it is determined that the service's attributes have changed. In
         * this case, the old and new service proxies are treated as the same if
         * one of the following conditions is met: - this method was called
         * because of the receipt of a MATCH_MATCH event - the old and new
         * service proxies are byte-wise fully equal (Note that the lookup
         * service specification guarantees that the proxies are the same when a
         * MATCH_MATCH event is received.)
         *
         * A service removed event followed by a service added event is sent
         * when the service passes the filter, and the conditions for which a
         * service changed event would be considered are not met; that is, this
         * method was not called because of the receipt of a MATCH_MATCH event;
         * or the old and new service proxies are not byte-wise fully equal.
         *
         * The if-else-block contained in this method implements the logic just
         * described. The parameter matchMatchEvent reflects the pertinent event
         * state that causes this method to be called. That is, either a
         * MATCH_MATCH event was received, or it wasn't, (and if it wasn't, then
         * a full byte-wise comparison is performed to determine whether the
         * proxies are still the same).
         *
         * To understand when the 'else' part of the if-else-block is executed,
         * consider the following conditions: - there is more than one lookup
         * service with which the service registers (ex. LUS-0 and LUS-1) -
         * after the service registers with LUS-0, a NOMATCH_MATCH event is
         * received and handled (so the service is now known to the cache) -
         * before the service registers with LUS-1, the service is replaced with
         * a new version - the NOMATCH_MATCH event resulting from the service's
         * registration with LUS-1 is received BEFORE receiving the
         * MATCH_NOMATCH/NOMATCH_MATCH event sequence that will ultimately
         * result from the re-registration of that new version with LUS-0 When
         * the above conditions occur, the NOMATCH_MATCH event that resulted
         * from the service's registration with LUS-1 will cause this method to
         * be invoked and the proxies to be fully compared (because the event
         * was not a MATCH_MATCH event); and since the old service proxy and the
         * new service proxy will not be fully equal, the else part of the
         * if-else-block will be executed.
         *
         * This method applies the filter only after the above comparisons and
         * determinations have been completed.
         */
        private void itemMatchMatchChange(
                ServiceID srvcID,
                ServiceItemReg itemReg,
                ServiceRegistrar proxy,
                ServiceItem newItem,
                boolean matchMatchEvent)
        {
            /* Save the pre-event state. Update the post-event state after
             * applying the filter.
             */
            ServiceItem oldItem;
            ServiceItem oldFilteredItem;
            boolean notifyServiceRemoved;
            boolean attrsChanged = false;
            boolean versionChanged = false;
            ServiceRegistrar proxyChanged = null;
            ServiceItem newFilteredItem;
            synchronized (itemReg){
                if (itemReg != serviceIdMap.get(srvcID)) return;
                notifyServiceRemoved = !itemReg.isDiscarded();
                oldItem = itemReg.getItem();
                oldFilteredItem = itemReg.getFilteredItem();
                if (itemReg.proxyNotUsedToTrackChange(proxy, newItem)) { // not tracking
                    if (matchMatchEvent) return;
                    if (notifyServiceRemoved) return;
                    proxyChanged = proxy; // start tracking instead
                }//endif
                if (!notifyServiceRemoved) {
                    itemReg.replaceProxyUsedToTrackChange(proxyChanged, newItem);
                    itemReg.setFilteredItem(null);
                    itemReg.discard();
                    if (matchMatchEvent) return;
                }//endif
                /* For an explanation of the logic of the following if-else-block,
                 * refer to the method description above.
                 */
                if( matchMatchEvent || sameVersion(newItem,oldItem) ) {
                    if(!notifyServiceRemoved) return;
                    /* Same version, determine if the attributes have changed.
                     * But first, replace the new service proxy with the old
                     * service proxy so the client always uses the old proxy
                     * (at least, until the version is changed).
                     */
    //                newItem.service = oldItem.service; //Data race
                    /* Now compare attributes */
                    attrsChanged = !LookupAttributes.equal(newItem.attributeSets,
                            oldItem.attributeSets);

                    if(!attrsChanged) return;//no change, no need to filter
                } else {//(!matchMatchEvent && !same version) ==> re-registration
                    versionChanged = true;
                }//endif
                /* Now apply the filter, and send events if appropriate */
                newFilteredItem =
                    filterMaybeDiscard(srvcID, itemReg, newItem, notifyServiceRemoved);
            }
            if(newFilteredItem != null) {
                /* Passed the filter, okay to send event(s). */
                if(attrsChanged) changeServiceNotify(newFilteredItem,
                            oldFilteredItem);
                if(versionChanged) {
                    if (notifyServiceRemoved) {
                        removeServiceNotify(oldFilteredItem);
                    }//endif
                    addServiceNotify(newFilteredItem);
                }//endif
            }//endif
        }

        /**
         * Convenience method that performs a byte-wise comparison, including
         * codebases, of the services referenced by the given service items, and
         * returns the result. If the services cannot be compared, it is assumed
         * that the versions are not the same, and <code>false</code> is
         * returned.
         */
        private boolean sameVersion(ServiceItem item0, ServiceItem item1) {
            boolean fullyEqual = false;
            try {
                MarshalledInstance mi0 = new MarshalledInstance(item0.service);
                MarshalledInstance mi1 = new MarshalledInstance(item1.service);
                fullyEqual = mi0.fullyEquals(mi1);
            } catch (IOException e) {
                logger.log(Level.INFO, "failure marshalling old and new "
                        + "services for equality check", e);
            }
            return fullyEqual;
        }//end LookupCacheImpl.sameVersion

        /**
         * Gets the remaining time left on the current cache's "lifespan".
         */
        public long getLeaseDuration() {
            if (leaseDuration == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            return leaseDuration + startTime - System.currentTimeMillis();
        }//end LookupCacheImpl.getLeaseDuration

        /**
         * Sends a notification to all listeners when a ServiceItem has been
         * added.
         */
        private void addServiceNotify(ServiceItem item) {
            serviceNotifyDo(null, item, ITEM_ADDED);
        } //end LookupCacheImpl.addServiceNotify

        /**
         * Sends a notification to the given listener when a ServiceItem has
         * been added.
         */
        private void addServiceNotify(ServiceItem item,
                ServiceDiscoveryListener srvcListener) {
            eventNotificationExecutor.execute(new ServiceNotifyDo(null, item, ITEM_ADDED, srvcListener, this));
            if (logger.isLoggable(Level.FINEST)) {
                try {
                    throw new Exception("Back Trace");
                } catch (Exception ex) {
                    ex.fillInStackTrace();
                    logger.log(Level.FINEST, "Log back trace", ex);
                }
            }
        } //end LookupCacheImpl.addServiceNotify

        /**
         * Sends a notification when a ServiceItem has been removed.
         */
        private void removeServiceNotify(ServiceItem item) {
            serviceNotifyDo(item, null, ITEM_REMOVED);
        }//end LookupCacheImpl.removeServiceNotify

        /**
         * Sends a notification when a ServiceItem has been changed, but still
         * matches.
         */
        private void changeServiceNotify(ServiceItem newItem,
                ServiceItem oldItem) {
            serviceNotifyDo(oldItem, newItem, ITEM_CHANGED);
        }//end LookupCacheImpl.changeServiceNotify

        /**
         * Common code for performing service notification to all listeners.
         */
        private void serviceNotifyDo(ServiceItem oldItem,
                ServiceItem item,
                int action) {
            synchronized (sItemListeners) {
                if (sItemListeners.isEmpty()) {
                    return;
                }
                Iterator<ServiceDiscoveryListener> iter = sItemListeners.iterator();
                while (iter.hasNext()) {
                    ServiceDiscoveryListener sl = iter.next();
                    eventNotificationExecutor.execute(new ServiceNotifyDo(oldItem, item, action, sl, this));
                    if (logger.isLoggable(Level.FINEST)) {
                        try {
                            throw new Exception("Back Trace");
                        } catch (Exception ex) {
                            ex.fillInStackTrace();
                            logger.log(Level.FINEST, "Log back trace", ex);
                        }
                    }
                }//end loop
            }
        }//end LookupCacheImpl.serviceNotifyDo

        /**
         * Common code for performing service notification to one listener in 
         * an executor task thread.
         */
        private static class ServiceNotifyDo implements Runnable {

            final ServiceItem oldItem;
            final ServiceItem item;
            final int action;
            final ServiceDiscoveryListener sl;
            final Object lookupCache;

            ServiceNotifyDo(ServiceItem oldItem,
                    ServiceItem item,
                    int action,
                    ServiceDiscoveryListener sl,
                    LookupCache lookupCache) {
                this.oldItem = oldItem;
                this.item = item;
                this.action = action;
                this.sl = sl;
                this.lookupCache = lookupCache;
            }

            @Override
            public void run() {
                ServiceDiscoveryEvent event;
                try {
                    event = new ServiceDiscoveryEvent(lookupCache, oldItem, item);
                } catch (NullPointerException e){
                    boolean lookupCacheNull = lookupCache == null;
                    boolean oldItemNull = oldItem == null;
                    boolean itemNull = item == null;
                    logger.log(Level.INFO,
                            "ServiceDiscoveryEvent constructor threw NullPointerException, lookupCache null? {0} oldItem null? {1} item null? {2}",
                            new Object[]{lookupCacheNull, oldItemNull, itemNull});
                    return;
                }
                switch (action) {
                    case ITEM_ADDED:
                        sl.serviceAdded(event);
                        break;
                    case ITEM_REMOVED:
                        sl.serviceRemoved(event);
                        break;
                    case ITEM_CHANGED:
                        sl.serviceChanged(event);
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "case must be one of the following: ITEM_ADDED, ITEM_REMOVED or ITEM_CHANGED");
                }//end switch(action)
            }
        }

        private void initCache() throws RemoteException {
            /* Get the exporter for the remote event listener from the
             * configuration.
             */
            try {
                Exporter defaultExporter
                        = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
                                new BasicILFactory(),
                                false, false);
                lookupListenerExporter
                        = sdm.thisConfig.getEntry(COMPONENT_NAME,
                                "eventListenerExporter",
                                Exporter.class,
                                defaultExporter);
            } catch (ConfigurationException e) {// exception, use default
                ExportException e1 = new ExportException("Configuration exception while "
                        + "retrieving exporter for "
                        + "cache's remote event listener",
                        e);
                throw e1;
            }
            /* 
             * Executor dedicated to event notification.
             */
            
            try {
                eventNotificationExecutor = sdm.thisConfig.getEntry(COMPONENT_NAME,
                        "eventNotificationExecutor",
                        ExecutorService.class);
            } catch (ConfigurationException e) { /* use default */
                 eventNotificationExecutor
                         = new ThreadPoolExecutor(
                                2, /* Min Threads */
                                30, /* Max Threads */
                                15,
                                TimeUnit.SECONDS,
                                new ArrayBlockingQueue<Runnable>(20), /* Fixed Length */
                                new NamedThreadFactory(
                                        "SDM event notifier",
                                        false
                                ),
                                new ThreadPoolExecutor.CallerRunsPolicy()
                        );
            }
            
            /* Get a general-purpose task manager for this cache from the
             * configuration. This task manager will be used to manage the
             * various tasks executed by this instance of the lookup cache.
             */
            try {
                cacheTaskMgr = sdm.thisConfig.getEntry(COMPONENT_NAME,
                        "cacheExecutorService",
                        ExecutorService.class);
            } catch (ConfigurationException e) { /* use default */

                cacheTaskMgr
                        = new ThreadPoolExecutor(
                                10, /* Min Threads */
                                10, /* Ignored */
                                15,
                                TimeUnit.SECONDS,
                                new LinkedBlockingQueue<Runnable>(), /* Unbounded */
                                new NamedThreadFactory(
                                        "SDM lookup cache",
                                        false
                                )
                        );

            }
            cacheTaskMgr = new ExtensibleExecutorService(
                    cacheTaskMgr,
                    new RunnableFutureFactory() {

                        @Override
                        public <T> RunnableFuture<T> newTaskFor(Runnable r, T value) {
                            if (r instanceof ObservableFutureTask) {
                                return (RunnableFuture<T>) r;
                            }
                            return new CacheTaskWrapper<T>(r, value);
                        }

                        @Override
                        public <T> RunnableFuture<T> newTaskFor(Callable<T> c) {
                            if (c instanceof ObservableFutureTask) {
                                return (RunnableFuture<T>) c;
                            }
                            return new CacheTaskWrapper<T>(c);
                        }
                    }
            );

            cacheTaskDepMgr = new CacheTaskDependencyManager(cacheTaskMgr);
            /* Get a special-purpose task manager for this cache from the
             * configuration. That task manager will be used to manage the
             * various instances of the special-purpose task, executed by
             * this instance of the lookup cache, that waits on verification
             * events after a previousy discovered service has been discarded.
             */
            try {
                serviceDiscardTimerTaskMgr
                        = sdm.thisConfig.getEntry(COMPONENT_NAME,
                                "discardExecutorService",
                                ExecutorService.class);
            } catch (ConfigurationException e) { /* use default */
                serviceDiscardTimerTaskMgr
                        = //                        new TaskManager(10,(15*1000),1.0f);
                        new ThreadPoolExecutor(
                                10, /* Min Threads */
                                10, /* Ignored */
                                15,
                                TimeUnit.SECONDS,
                                new LinkedBlockingQueue<Runnable>(), /* Unbounded Queue */
                                new NamedThreadFactory(
                                        "SDM discard timer",
                                        false
                                )
                        );
            }
            // Moved here from constructor to avoid publishing this reference
            lookupListenerProxy = lookupListener.export();
            Iterator<ProxyReg> it = sdm.proxyRegSet.iterator();
            while (it.hasNext()) {
                addProxyReg(it.next());
            }
        }//end LookupCacheImpl.initCache

        /**
         * Applies the first-stage <code>filter</code> associated with the
         * current instance of <code>LookupCache</code> to the given
         * <code>item</code> and returns the resulting filtered item if the
         * <code>filter</code> is passed (or is <code>null</code>); otherwise,
         * returns <code>null</code> and sends a service removed event if the
         * <code>sendEvent</code> parameter is <code>true</code>.
         * <p>
         * This method is called only when the <code>item</code> to be filtered
         * corresponds to an element that currently exists in the
         * <code>serviceIdMap</code>.
         * <p>
         * As described in the <code>ServiceItemFilter</code> specification,
         * when the <code>item</code> passes the <code>filter</code>, the
         * <code>service</code> field of the <code>item</code> is replaced with
         * the filtered form of the object previously contained in that field.
         * In this case, the <code>filteredItem</code> field of the
         * corresponding <code>ServiceItemReg</code> element of the
         * <code>serviceIdMap</code> is set to this new filtered item.
         * <p>
         * If the <code>filter</code> returns <code>indefinite</code>, then that
         * specification states that the <code>service</code> field is replaced
         * with <code>null</code>. In this case, the <code>filteredItem</code>
         * field of the corresponding <code>ServiceItemReg</code> element of the
         * <code>serviceIdMap</code> is left unchanged.
         */
        private ServiceItem filterMaybeDiscard(ServiceID srvcID,
                ServiceItemReg itemReg, ServiceItem item, boolean sendEvent) {
            if ((item == null) || (item.service == null)) {
                return null;
            }
            if (filter == null) {
                return addFilteredItemToMap(item, item, itemReg);
            }//endif
            /* Make a copy to filter because the filter may modify it. */
            ServiceItem filteredItem = item.clone();
            boolean pass = filter.check(filteredItem);
            /* Handle filter fail */
            if (!pass) {
                boolean notify = false;
                ServiceItem oldFilteredItem = null;
                if (itemReg != null) {
                    if (sendEvent) {
                        oldFilteredItem = itemReg.getFilteredItem();
                        notify = removeServiceIdMapSendNoEvent(srvcID, itemReg);
                    } else {
                        removeServiceIdMapSendNoEvent(srvcID, itemReg);
                    }//endif
                    if (notify) {
                        removeServiceNotify(oldFilteredItem);
                    }
                }//endif
                return null;
            }//endif(fail)
            /* Handle filter pass */
            if (filteredItem.service != null) {
                return addFilteredItemToMap(item, filteredItem, itemReg);
            }//endif(pass)
            /* Handle filter indefinite */
            discardRetryLater(item, sendEvent, itemReg);
            return null;
        }//end LookupCacheImpl.filterMaybeDiscard

        /**
         * Convenience method called by <code>filterMaybeDiscard</code> that
         * finds the <code>ServiceItemReg</code> element in the
         * <code>serviceIdMap</code> that corresponds to the given
         * <code>ServiceItem</code> and, if such an element is found, replaces
         * the <code>item</code> field of that element with the given
         * <code>item</code> parameter; and sets the <code>filteredItem</code>
         * field of that element to the value contained in the
         * <code>filteredItem</code> parameter.
         */
        private ServiceItem addFilteredItemToMap(ServiceItem item, ServiceItem filteredItem, ServiceItemReg itemReg) {
            ServiceID id = item.serviceID;
            assert Thread.holdsLock(itemReg);
            if (itemReg == serviceIdMap.get(id)){
                cancelDiscardTask(id);
                itemReg.replaceProxyUsedToTrackChange(null, item);
                itemReg.setFilteredItem(filteredItem);
                return filteredItem;
            }
            return null;
        }//end LookupCacheImpl.addFilteredItemToMap

        /**
         * Convenience method called by <code>filterMaybeDiscard</code> that
         * finds in the <code>serviceIdMap</code>, the
         * <code>ServiceItemReg</code> element corresponding to the given
         * <code>ServiceItem</code>, sets a service removed event, and queues a
         * <code>ServiceDiscardTimerTask</code> to retry the filter at a later
         * time. If the <code>serviceIdMap</code> does not contain a
         * <code>ServiceItemReg</code> corresponding to the given
         * <code>ServiceItem</code>, then this method simply returns.
         */
        private void discardRetryLater(ServiceItem item, boolean sendEvent, ServiceItemReg itemReg) {
            ServiceItem oldFilteredItem;
            oldFilteredItem = itemReg.getFilteredItem();
            /* If there's been any change in what is being discarded for
             * filter retry, then update the item field in the map to
             * capture that change; and set the filteredItem field to
             * to null to guarantee that the filter is re-applied to
             * that changed item.
             */
            
            if (itemReg.discard()){
                itemReg.replaceProxyUsedToTrackChange(null, item);
                itemReg.setFilteredItem(null);
                Future f = serviceDiscardTimerTaskMgr.submit(
                        new ServiceDiscardTimerTask(this, item.serviceID));
                serviceDiscardFutures.put(item.serviceID, f);
                if (sendEvent) removeServiceNotify(oldFilteredItem); // Maybe send anyway?
            }
        }//end LookupCacheImpl.discardRetryLater

        /**
         * Convenience method called (only
         * when a TRANSITION_MATCH_NOMATCH event is received) that removes the
         * given <code>item</code> from the <code>serviceIdMap</code> and wakes
         * up the <code>ServiceDiscardTimerTask</code> if the given
         * <code>item</code> is discarded; otherwise, sends a removed event.
         */
  	private void handleMatchNoMatch(ServiceRegistrar proxy, ServiceID srvcID)
        {
            ServiceItemReg itemReg = serviceIdMap.get(srvcID);
            
                if(itemReg != null) {
                    ServiceItem newItem;
                    ServiceItem filteredItem;
                    boolean notify = false;
                    ServiceRegistrar itemRegProxy = null;
                    synchronized (itemReg){
                        newItem = itemReg.removeProxy(proxy);
                        filteredItem = itemReg.getFilteredItem();
                        if (newItem != null) {
                            itemRegProxy = itemReg.getProxy();
                        } else if(itemReg.hasNoProxys()) {
                            if(itemReg.isDiscarded()) {
                                /* Remove item from map and wake up the discard task */
                                removeServiceIdMapSendNoEvent(srvcID, itemReg);
                                cancelDiscardTask(srvcID);
                            } else {//remove item from map and send removed event
                                notify = removeServiceIdMapSendNoEvent(srvcID, itemReg);
                            }//endif
                        }//endif
                    }
                    if (itemRegProxy != null) {
                        itemMatchMatchChange(srvcID, itemReg, itemRegProxy, newItem, false);
                    } else if (notify){
                        removeServiceNotify(filteredItem);
                    }
                }//endif
            
        }//end LookupCacheImpl.handleMatchNoMatch

        /**
         * Wake up service discard task if running, else remove from mgr.
         */
        private void cancelDiscardTask(ServiceID sid) {
            // Might need to record future's and cancel from there.
            Future task = serviceDiscardFutures.get(sid);
            if (task != null) {
                task.cancel(true);
            }
        }//end LookupCacheImpl.cancelDiscardTask

    }
    /* Name of this component; used in config entry retrieval and the logger.*/
    private static final String COMPONENT_NAME
            = "net.jini.lookup.ServiceDiscoveryManager";
    /* Logger used by this utility. */
    private static final Logger logger = Logger.getLogger(COMPONENT_NAME);
    /* The discovery manager to use (passed in, or create one). */
    private final DiscoveryManagement discMgr;
    /* Indicates whether the discovery manager was created internally or not */
    private final boolean discMgrInternal;
    /* The listener added to discMgr that receives DiscoveryEvents */
    private final DiscMgrListener discMgrListener;
    /* The LeaseRenewalManager to use (passed in, or create one). */
    private final LeaseRenewalManager leaseRenewalMgr;
    /* Contains all of the discovered lookup services (ServiceRegistrar). */
    private final Set<ProxyReg> proxyRegSet;
    /* Random number generator for use in lookup. */
    private final Random random = new Random();
    /* Contains all of the instances of LookupCache that are requested. */
    private final List<LookupCache> caches;

    /* Flag to indicate if the ServiceDiscoveryManager has been terminated. */
    private boolean bTerminated = false; //sync on this

    private final Thread terminatorThread;
    private final LookupCacheTerminator terminator;
    /* Flag to indicate LookupCacheTerminator has been started */
    private boolean started = false; // sync on terminatorThread
    /* Object used to obtain the configuration items for this utility. */
    private final Configuration thisConfig;
    /* Preparer for the proxies to the lookup services that are discovered
     * and used by this utility.
     */
    private final ProxyPreparer registrarPreparer;
    /* Preparer for the proxies to the leases returned to this utility when
     * it registers with the event mechanism of any of the discovered lookup
     * services.
     */
    private final ProxyPreparer eventLeasePreparer;
    /* Wait value used when handling the "service discard problem". */
    private final long discardWait;

    /* Listener class for lookup service discovery notification. */
    private class DiscMgrListener implements DiscoveryListener {
        /* New or previously discarded proxy has been discovered. */

        @Override
        public void discovered(DiscoveryEvent e) {
            ServiceRegistrar[] proxys = e.getRegistrars();
            ArrayList<ProxyReg> newProxys = new ArrayList<ProxyReg>(1);
            for (int i = 0; i < proxys.length; i++) {
                /* Prepare each lookup service proxy before using it. */
                try {
                    proxys[i]
                            = (ServiceRegistrar) registrarPreparer.prepareProxy(proxys[i]);
                    logger.log(Level.FINEST, "ServiceDiscoveryManager - "
                            + "discovered lookup service proxy prepared: {0}",
                            proxys[i]);
                } catch (Exception e1) {
                    logger.log(Level.INFO,
                            "failure preparing discovered ServiceRegistrar "
                            + "proxy, discarding the proxy",
                            e1);
                    discard(proxys[i]);
                    continue;
                }
                ProxyReg reg = new ProxyReg(proxys[i]);
                // Changed to only add to newProxys if actually new 7th Jan 2014
                if (proxyRegSet.add(reg)) {
                    newProxys.add(reg);
                }
            }//end loop
            Iterator<ProxyReg> iter = newProxys.iterator();
            while (iter.hasNext()) {
                ProxyReg reg = iter.next();
                cacheAddProxy(reg);
            }//end loop
        }//end DiscMgrListener.discovered

        /* Previously discovered proxy has been discarded. */
        @Override
        public void discarded(DiscoveryEvent e) {
            ServiceRegistrar[] proxys = e.getRegistrars();
            List<ProxyReg> drops = new LinkedList<ProxyReg>();
            for (int i = 0, l = proxys.length; i < l; i++) {
                ProxyReg reg = removeReg(proxys[i]);
                if (reg != null) { // this check can be removed.
                    drops.add(reg);
                } else {
                    //River-337
                    logger.severe("discard error, proxy was null");
                    //throw new RuntimeException("discard error");
                }//endif
            }//end loop
            Iterator<ProxyReg> iter = drops.iterator();
            while (iter.hasNext()) {
                dropProxy(iter.next());
            }//end loop
        }//end DiscMgrListener.discarded

        /**
         * Discards a ServiceRegistrar through the discovery manager.
         */
        private void discard(ServiceRegistrar proxy) {
            discMgr.discard(proxy);
        }//end discard

    }//end class ServiceDiscoveryManager.DiscMgrListener

    /**
     * Adds the given proxy to all the caches maintained by the SDM.
     */
    private void cacheAddProxy(ProxyReg reg) {
        synchronized (caches) {
            Iterator iter = caches.iterator();
            while (iter.hasNext()) {
                LookupCacheImpl cache = (LookupCacheImpl) iter.next();
                cache.addProxyReg(reg);
            }//end loop
        }
    }//end cacheAddProxy

    /**
     * Removes the given proxy from all the caches maintained by the SDM.
     */
    private void dropProxy(ProxyReg reg) {
        synchronized (caches) {
            Iterator iter = caches.iterator();
            while (iter.hasNext()) {
                LookupCacheImpl cache = (LookupCacheImpl) iter.next();
                cache.removeProxyReg(reg);
            }//end loop
        }
    }//end dropProxy

    /**
     * Constructs an instance of <code>ServiceDiscoveryManager</code> which
     * will, on behalf of the entity that constructs this class, discover and
     * manage a set of lookup services, as well as discover and manage sets of
     * services registered with those lookup services. The entity indicates
     * which lookup services to discover and manage through the parameters input
     * to this constructor.
     * <p>
     * As stated in the class description, this class has three usage patterns:
     * <p>
     * <ul>
     * <li> the entity uses a {@link net.jini.lookup.LookupCache
     *        LookupCache} to locally store and manage discovered services so that
     * those services can be accessed quickly
     * <li> the entity registers with the event mechanism provided by a
     * {@link net.jini.lookup.LookupCache LookupCache} to be notified when
     * services of interest are discovered
     * <li> the entity uses the <code>ServiceDiscoveryManager</code> to perform
     * remote queries of the lookup services, employing richer semantics than
     * that provided through the standard
     * {@link net.jini.core.lookup.ServiceRegistrar ServiceRegistrar} interface
     * </ul>
     * <p>
     * Although the first two usage patterns emphasize the use of a cache
     * object, that cache is acquired only through an instance of the
     * <code>ServiceDiscoveryManager</code> class.
     * <p>
     * It is important to note that some of the methods of this class      ({@link net.jini.lookup.ServiceDiscoveryManager#createLookupCache
     * createLookupCache} and the <i>blocking</i> versions of
     * {@link net.jini.lookup.ServiceDiscoveryManager#lookup lookup} to be
     * exact) can throw a {@link java.rmi.RemoteException} when invoked. This is
     * because each of these methods may attempt to register with the event
     * mechanism of at least one lookup service, a process that requires a
     * remote object (a listener) to be exported to the lookup service(s). Both
     * the process of registering with a lookup service's event mechanism and
     * the process of exporting a remote object are processes that can result in
     * a {@link java.rmi.RemoteException}.
     * <p>
     * In order to facilitate the exportation of the remote listener just
     * described, the <code>ServiceDiscoveryManager</code> class instantiates an
     * inner class that implements the
     * {@link net.jini.core.event.RemoteEventListener RemoteEventListener}
     * interface. Although this class defines, instantiates, and exports this
     * remote listener, <i>it is the entity's responsibility</i> to provide a
     * mechanism for any lookup service to acquire the proxy to the exported
     * listener. One way to do this is to configure this utility to export the
     * listener using the Jini(TM) Extensible Remote Invocation (Jini ERI)
     * communication framework. When the listener is exported to use Jini ERI,
     * and no proxy customizations (such as a custom invocation handler or
     * transport endpoint) are used, no other action is necessary to make the
     * proxy to the listener available to the lookup service(s) with which that
     * listener is registered.
     * <p>
     * The <a href="#eventListenerExporter">default exporter</a> for this
     * utility will export the remote event listener under Jini ERI, specifying
     * that the port and object ID with which the listener is to be exported
     * should be chosen by the Jini ERI framework, not the deployer.
     * <p>
     * If it is required that the remote event listener be exported under JRMP
     * instead of Jini ERI, then the entity that employs this utility must
     * specify this in its configuration. For example, the entity's
     * configuration would need to contain something like the following:
     * <p>
     * <blockquote>
     * <pre>
     * import net.jini.jrmp.JrmpExporter;
     *
     * application.configuration.component.name {
     *    .......
     *    .......
     *    // configuration items specific to the application
     *    .......
     *    .......
     * }//end application.configuration.component.name
     *
     * net.jini.lookup.ServiceDiscoveryManager {
     *
     *    serverExporter = new JrmpExporter();
     *
     * }//end net.jini.lookup.ServiceDiscoveryManager
     * </pre>
     * </blockquote>
     * <p>
     * It is important to note that when the remote event listener is exported
     * under JRMP, unlike Jini ERI, the JRMP remote communication framework does
     * <b><i>not</i></b> provide a mechanism that automatically makes the
     * listener proxy available to the lookup service(s) with which the listener
     * is registered; the deployer of the entity, or the entity itself, must
     * provide such a mechanism.
     * <p>
     * When exported under JRMP, one of the more common mechanisms for making
     * the listener proxy available to the lookup service(s) with which the
     * listener is registered consists of the following:
     * <p>
     * <ul><li> store the necessary class files in a JAR file
     * <li> make the class files in the JAR file <i>preferred</i>
     * (see {@link net.jini.loader.pref} for details)
     * <li> run an HTTP server to serve up the JAR file to any requesting lookup
     * service
     * <li> advertise the location of that JAR file by setting the
     * <code>java.rmi.server.codebase</code> property of the entity to "point"
     * at the JAR file
     * </ul>
     * <p>
     * For example, suppose an application consists of an entity that intends to
     * use the <code>ServiceDiscoveryManager</code> will run on a host named
     * <b><i>myHost</i></b>. And suppose that the <i>down-loadable</i> JAR file
     * named <b><i>sdm-dl.jar</i></b> that is provided in the distribution is
     * located in the directory <b><i>/files/jini/lib</i></b>, and will be
     * served by an HTTP server listening on port
     * <b><i>8082</i></b>. If the application is run with its codebase property
     * set to
     * <code>-Djava.rmi.server.codebase="http://myHost:8082/sdm-dl.jar"</code>,
     * the lookup service(s) should then be able to access the remote listener
     * exported under JRMP by the <code>ServiceDiscoveryManager</code> on behalf
     * of the entity.
     * <p>
     * If a mechanism for lookup services to access the remote listener exported
     * by the <code>ServiceDiscoveryManager</code> is not provided (either by
     * the remote communication framework itself, or by some other means), the
     * remote methods of the <code>ServiceDiscoveryManager</code> - the methods
     * involved in the two most important usage patterns of that utility - will
     * be of no use.
     * <p>
     * This constructor takes two arguments: an object that implements the
     * <code>DiscoveryManagement</code> interface and a reference to a
     * <code>LeaseRenewalManager</code> object. The constructor throws an
     * <code>IOException</code> because construction of a
     * <code>ServiceDiscoveryManager</code> may initiate the multicast discovery
     * process, a process that can throw an <code>IOException</code>.
     *
     * @param discoveryMgr the <code>DiscoveryManagement</code> implementation
     * through which notifications that indicate a lookup service has been
     * discovered or discarded will be received. If the value of the argument is
     * <code>null</code>, then an instance of the
     * <code>LookupDiscoveryManager</code> utility class will be constructed to
     * listen for events announcing the discovery of only those lookup services
     * that are members of the public group.
     *
     * @param leaseMgr the <code>LeaseRenewalManager</code> to use. A value of
     * <code>null</code> may be passed as the <code>LeaseRenewalManager</code>
     * argument. If the value of the argument is <code>null</code>, an instance
     * of the <code>LeaseRenewalManager</code> class will be created, initially
     * managing no <code>Lease</code> objects.
     *
     * @throws IOException because construction of a
     * <code>ServiceDiscoveryManager</code> may initiate the multicast discovery
     * process which can throw an <code>IOException</code>.
     *
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.core.event.RemoteEventListener
     * @see net.jini.core.lookup.ServiceRegistrar
     */
    public ServiceDiscoveryManager(DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr)
            throws IOException {

        this(initial(discoveryMgr, leaseMgr, EmptyConfiguration.INSTANCE));

    }//end constructor

    /**
     * Constructs an instance of this class, which is configured using the items
     * retrieved through the given <code>Configuration</code>, that will, on
     * behalf of the entity that constructs this class, discover and manage a
     * set of lookup services, as well as discover and manage sets of services
     * registered with those lookup services. Through the parameters input to
     * this constructor, the client of this utility indicates which lookup
     * services to discover and manage, and how it wants the utility
     * additionally configured.
     * <p>
     * For a more details, refer to the description of the alternate constructor
     * of this class.
     * <p>
     * This constructor takes three arguments: an object that implements the
     * <code>DiscoveryManagement</code> interface, a reference to an instance of
     * the <code>LeaseRenewalManager</code> class, and a
     * <code>Configuration</code> object. The constructor throws an
     * <code>IOException</code> because construction of a
     * <code>ServiceDiscoveryManager</code> may initiate the multicast discovery
     * process, a process that can throw an <code>IOException</code>. The
     * constructor also throws a <code>ConfigurationException</code> when an
     * exception occurs while retrieving an item from the given
     * <code>Configuration</code>
     *
     * @param discoveryMgr the <code>DiscoveryManagement</code> implementation
     * through which notifications that indicate a lookup service has been
     * discovered or discarded will be received. If the value of the argument is
     * <code>null</code>, then an instance of the
     * <code>LookupDiscoveryManager</code> utility class will be constructed to
     * listen for events announcing the discovery of only those lookup services
     * that are members of the public group.
     *
     * @param leaseMgr the <code>LeaseRenewalManager</code> to use. A value of
     * <code>null</code> may be passed as the <code>LeaseRenewalManager</code>
     * argument. If the value of the argument is <code>null</code>, an instance
     * of the <code>LeaseRenewalManager</code> class will be created, initially
     * managing no <code>Lease</code> objects.
     * @param config the <code>Configuration</code>
     *
     * @throws IOException because construction of a
     * <code>ServiceDiscoveryManager</code> may initiate the multicast discovery
     * process which can throw an <code>IOException</code>.
     *
     * @throws net.jini.config.ConfigurationException indicates an exception
     * occurred while retrieving an item from the given
     * <code>Configuration</code>
     *
     * @throws java.lang.NullPointerException if <code>null</code> is input for
     * the configuration
     *
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.core.event.RemoteEventListener
     * @see net.jini.core.lookup.ServiceRegistrar
     * @see net.jini.config.Configuration
     * @see net.jini.config.ConfigurationException
     */
    public ServiceDiscoveryManager(DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException,
            ConfigurationException {
        this(init(discoveryMgr, leaseMgr, config));
    }//end constructor

    private ServiceDiscoveryManager(Initializer init) {
        this.proxyRegSet = Collections.newSetFromMap(new ConcurrentHashMap<ProxyReg, Boolean>());
        this.caches = new ArrayList<LookupCache>(32);
        thisConfig = init.thisConfig;
        registrarPreparer = init.registrarPreparer;
        eventLeasePreparer = init.eventLeasePreparer;
        leaseRenewalMgr = init.leaseRenewalMgr;
        discardWait = init.discardWait.longValue();
        discMgr = init.discMgr;
        discMgrInternal = init.discMgrInternal;
        discMgrListener = new DiscMgrListener();
        discMgr.addDiscoveryListener(discMgrListener);
        terminator = new LookupCacheTerminator();
        terminatorThread = new Thread(terminator, "SDM lookup cache terminator");
        terminatorThread.setDaemon(false);
    }

    /**
     * Returns array of ServiceRegistrar created from the proxyRegSet
     */
    private ServiceRegistrar[] buildServiceRegistrar() {
        List<ServiceRegistrar> proxys = new LinkedList<ServiceRegistrar>();
        Iterator<ProxyReg> iter = proxyRegSet.iterator();
        while (iter.hasNext()) {
            ProxyReg reg = iter.next();
            proxys.add(reg.getProxy());
        }//end loop
        return proxys.toArray(new ServiceRegistrar[proxys.size()]);
    }//end buildServiceRegistrar

    /**
     * Queries each available lookup service in the set of lookup services
     * managed by the <code>ServiceDiscoveryManager</code> (the <i>managed
     * set</i>) for a service reference that matches criteria defined by the
     * entity that invokes this method. The semantics of this method are similar
     * to the semantics of the <code>lookup</code> method provided by the
     * <code>ServiceRegistrar</code> interface; employing the same
     * template-matching scheme. Additionally, this method allows any entity to
     * supply an object referred to as a <i>filter</i>. Such an object is a
     * non-remote object that defines additional matching criteria that the
     * <code>ServiceDiscoveryManager</code> applies when searching for the
     * entity's services of interest. This filtering facility is particularly
     * useful to entities that wish to extend the capabilities of standard
     * template-matching.
     * <p>
     * Entities typically employ this method when they need infrequent access to
     * services, and when the cost of making remote queries is outweighed by the
     * overhead of maintaining a local cache (for example, because of resource
     * limitations).
     * <p>
     * This version of <code>lookup</code> returns a <i>single</i> instance of
     * <code>ServiceItem</code> corresponding to one of possibly many service
     * references that satisfy the matching criteria. If multiple services
     * matching the input criteria happen to exist, it is arbitrary as to which
     * reference is actually returned. It is for this reason that entities that
     * invoke this method typically care only that <i>a</i>
     * service is returned, not <i>which</i> service.
     * <p>
     * Note that, unlike other versions of <code>lookup</code> provided by the
     * <code>ServiceDiscoveryManager</code>, this version does not
     * <i>block</i>. That is, this version will return immediately upon failure
     * (or success) to find a service matching the input criteria.
     *
     * It is important to understand this characteristic because there is a
     * common usage scenario that can cause confusion when this version of
     * <code>lookup</code> is used but fails to discover the expected service of
     * interest. Suppose an entity creates a service discovery manager and then
     * immediately calls this version of <code>lookup</code>, which simply
     * queries the currently discovered lookup services for the service of
     * interest. If the discovery manager employed by the service discovery
     * manager has not yet disovered any lookup services (thus, there are no
     * lookup services to query) the method will immediately return a value of
     * <code>null</code>. This can be confusing when one verifies that such a
     * service of interest has indeed been started and registered with the
     * existing lookup service(s). To address this issue, one of the blocking
     * versions of <code>lookup</code> could be used instead of this version, or
     * the entity could simply wait until the discovery manager has been given
     * enough time to complete its own (lookup) discovery processing.
     *
     * @param tmpl an instance of <code>ServiceTemplate</code> corresponding to
     * the object to use for template-matching when searching for desired
     * services. If <code>null</code> is input to this parameter, this method
     * will use a <i>wildcarded</i>
     * template (will match all services) when performing template-matching.
     * Note that the effects of modifying contents of this parameter before this
     * method returns are unpredictable and undefined.
     * @param filter an instance of <code>ServiceItemFilter</code> containing
     * matching criteria that should be applied in addition to the
     * template-matching employed when searching for desired services. If
     * <code>null</code> is input to this parameter, then only template-matching
     * will be employed to find the desired services.
     *
     * @return a single instance of <code>ServiceItem</code> corresponding to a
     * reference to a service that matches the criteria represented in the input
     * parameters; or <code>null</code> if no matching service can be found.
     * Note that if multiple services matching the input criteria exist, it is
     * arbitrary as to which reference is returned.
     *
     * @see net.jini.core.lookup.ServiceRegistrar#lookup
     * @see net.jini.core.lookup.ServiceTemplate
     * @see net.jini.lookup.ServiceItemFilter
     */
    public ServiceItem lookup(ServiceTemplate tmpl, ServiceItemFilter filter) {
        checkTerminated();
        ServiceRegistrar[] proxys = buildServiceRegistrar();
        int len = proxys.length;
        if (len == 0) {
            return null;
        }
        int rand = random.nextInt(Integer.MAX_VALUE) % len;
        for (int i = 0; i < len; i++) {
            ServiceRegistrar proxy = proxys[(i + rand) % len];
            ServiceItem sItem = null;
            try {
                int maxMatches = ((filter != null) ? Integer.MAX_VALUE : 1);
                ServiceMatches sm = proxy.lookup(tmpl, maxMatches);
                sItem = getMatchedServiceItem(sm, filter);
            } catch (Exception e) {
                logger.log(Level.INFO,
                        "Exception occurred during query, discarding proxy",
                        e);
                discard(proxy);
            }
            if (sItem != null) {
                return sItem; //Don't need to clone
            }
        }//end loop
        return null;
    }//end lookup

    /**
     * Queries each available lookup service in the managed set for a service
     * that matches the input criteria. The semantics of this method are similar
     * to the semantics of the <code>lookup</code> method provided by the
     * <code>ServiceRegistrar</code> interface; employing the same
     * template-matching scheme. Additionally, this method allows any entity to
     * supply an object referred to as a <i>filter</i>. Such an object is a
     * non-remote object that defines additional matching criteria that the
     * <code>ServiceDiscoveryManager</code> applies when searching for the
     * entity's services of interest. This filtering facility is particularly
     * useful to entities that wish to extend the capabilities of standard
     * template-matching.
     * <p>
     * This version of <code>lookup</code> returns a <i>single</i> instance of
     * <code>ServiceItem</code> corresponding to one of possibly many service
     * references that satisfy the matching criteria. If multiple services
     * matching the input criteria happen to exist, it is arbitrary as to which
     * reference is actually returned. It is for this reason that entities that
     * invoke this method typically care only that <i>a</i>
     * service is returned, not <i>which</i> service.
     * <p>
     * Note that this version of <code>lookup</code> provides a
     * <i>blocking</i> feature that is controlled through the
     * <code>waitDur</code> parameter. That is, this version will not return
     * until either a service that matches the input criteria has been found, or
     * the amount of time contained in the <code>waitDur</code> parameter has
     * passed. If, while waiting for the service of interest to be found, the
     * entity decides that it no longer wishes to wait the entire period for
     * this method to return, the entity may interrupt this method by invoking
     * the interrupt method from the <code>Thread</code> class. The intent of
     * this mechanism is to allow the entity to interrupt this method in the
     * same way it would a sleeping thread.
     * <p>
     * Entities typically employ this method when they need infrequent access to
     * services, are willing (or forced) to wait for those services to be found,
     * and consider the cost of making remote queries for those services is
     * outweighed by the overhead of maintaining a local cache (for example,
     * because of resource limitations).
     *
     * @param tmpl an instance of <code>ServiceTemplate</code> corresponding to
     * the object to use for template-matching when searching for desired
     * services. If <code>null</code> is input to this parameter, this method
     * will use a <i>wildcarded</i>
     * template (will match all services) when performing template-matching.
     * Note that the effects of modifying contents of this parameter before this
     * method returns are unpredictable and undefined.
     * @param filter an instance of <code>ServiceItemFilter</code> containing
     * matching criteria that should be applied in addition to the
     * template-matching employed when searching for desired services. If
     * <code>null</code> is input to this parameter, then only template-matching
     * will be employed to find the desired services.
     * @param waitDur the amount of time (in milliseconds) to wait before ending
     * the "search" and returning <code>null</code>. If a non-positive value is
     * input to this parameter, then this method will not wait; it will simply
     * query the available lookup services and return a matching service
     * reference or <code>null</code>.
     *
     * @return a single instance of <code>ServiceItem</code> corresponding to a
     * reference to a service that matches the criteria represented in the input
     * parameters; or <code>null</code> if no matching service can be found.
     * Note that if multiple services matching the input criteria exist, it is
     * arbitrary as to which reference is returned.
     *
     * @throws java.lang.InterruptedException this exception occurs when the
     * entity interrupts this method by invoking the interrupt method from the
     * <code>Thread</code> class.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when a
     * RemoteException occurs either as a result of an attempt to export a
     * remote listener, or an attempt to register with the event mechanism of a
     * lookup service.
     *
     * @see net.jini.core.lookup.ServiceRegistrar#lookup
     * @see net.jini.core.lookup.ServiceTemplate
     * @see net.jini.lookup.ServiceItemFilter
     * @see java.lang.Thread
     */
    public ServiceItem lookup(ServiceTemplate tmpl,
            ServiceItemFilter filter,
            long waitDur) throws InterruptedException,
            RemoteException {
        /* First query each lookup for the desired service */
        ServiceItem sm = lookup(tmpl, filter);//checkTerminated() is done here
        if (sm != null) {
            return sm;
        }
        /* If the desired service is not in any of the lookups, wait for it. */
        ServiceDiscoveryListener cacheListener
                = new ServiceDiscoveryListenerImpl();
        LookupCacheImpl cache = null;
        try {
            /* The cache must be created inside the listener sync block,
             * otherwise a race condition can occur. This is because the
             * creation of a cache results in event registration which
             * will ultimately result in the invocation of the serviceAdded()
             * method in the cache's listener, and the interruption of any
             * objects waiting on the cache's listener. If the notifications
             * happen to occur before commencing the wait on the listener
             * object (see below), then the wait will never be interrupted
             * because the interrupts were sent before the wait() method
             * was invoked. Synchronizing on the listener and the listener's
             * serviceAdded() method, and creating the cache only after the
             * lock has been acquired, together will prevent this situation
             * since event registration cannot occur until the cache is
             * created, and the lock that allows entry into the serviceAdded()
             * method (which is invoked once the events do arrive) is not
             * released until the wait() method is invoked .
             */
            synchronized (cacheListener) {
                cache = createLookupCache(tmpl, filter, cacheListener, waitDur);
                long duration = cache.getLeaseDuration();
                while (duration > 0) {
                    cacheListener.wait(duration);
                    sm = cache.lookup(null);
                    if (sm != null) {
                        return sm;
                    }
                    duration = cache.getLeaseDuration();
                }//end loop
            }//end sync(cacheListener)
            return null; // Make it clear we're returning null.
        } finally {
            if (cache != null) {
                terminator.terminate(cache);
            }
        }
    }//end lookup

    /**
     * The <code>createLookupCache</code> method allows the client-like entity
     * to request that the <code>ServiceDiscoveryManager</code> create a new
     * managed set (or cache) and populate it with services, which match
     * criteria defined by the entity, and whose references are registered with
     * one or more of the lookup services the entity has targeted for discovery.
     * <p>
     * This method returns an object of type <code>LookupCache</code>. Through
     * this return value, the entity can query the cache for services of
     * interest, manage the cache's event mechanism for service discoveries, or
     * terminate the cache.
     * <p>
     * An entity typically uses the object returned by this method to provide
     * local storage of, and access to, references to services that it is
     * interested in using. Entities needing frequent access to numerous
     * services will find the object returned by this method quite useful
     * because acquisition of those service references is provided through local
     * method invocations. Additionally, because the object returned by this
     * method provides an event mechanism, it is also useful to entities wishing
     * to simply monitor, in an event-driven manner, the state changes that
     * occur in the services of interest.
     * <p>
     * Although not required, a common usage pattern for entities that wish to
     * use the <code>LookupCache</code> class to store and manage "discovered"
     * services is to create a separate cache for each service type of interest.
     *
     * @param tmpl template to match. It uses template-matching semantics to
     * identify the service(s) to acquire from lookup services in the managed
     * set. If this value is <code>null</code>, it is the equivalent of passing
     * a <code>ServiceTemplate</code> constructed with all <code>null</code>
     * arguments (all wildcards).
     * @param filter used to apply additional matching criteria to any
     * <code>ServiceItem</code> found through template-matching. If this value
     * is <code>null</code>, no additional filtering will be applied beyond the
     * template-matching.
     * @param listener object that will receive notifications when services
     * matching the input criteria are discovered for the first time, or have
     * encountered a state change such as removal from all lookup services or
     * attribute set changes. If this value is <code>null</code>, the cache
     * resulting from that invocation will send no such notifications.
     *
     * @return LookupCache used to query the cache for services of interest,
     * manage the cache's event mechanism for service discoveries, or terminate
     * the cache.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when a
     * RemoteException occurs as a result of an attempt to export the remote
     * listener that receives service events from the lookup services in the
     * managed set.
     *
     * @see net.jini.lookup.ServiceItemFilter
     */
    public LookupCache createLookupCache(ServiceTemplate tmpl,
            ServiceItemFilter filter,
            ServiceDiscoveryListener listener)
            throws RemoteException {
        checkTerminated();
        return createLookupCache(tmpl, filter, listener, Long.MAX_VALUE);
    }//end createLookupCache

    /**
     * The <code>getDiscoveryManager</code> method will return an object that
     * implements the <code>DiscoveryManagement</code> interface. The object
     * returned by this method provides the <code>ServiceDiscoveryManager</code>
     * with the ability to set discovery listeners and to discard previously
     * discovered lookup services when they are found to be unavailable.
     *
     * @return DiscoveryManagement implementation
     * @see net.jini.discovery.DiscoveryManagement
     */
    public DiscoveryManagement getDiscoveryManager() {
        checkTerminated();
        return discMgr;
    }//end getDiscoveryManager

    /**
     * The <code>getLeaseRenewalManager</code> method will return an instance of
     * the <code>LeaseRenewalManager</code> class. The object returned by this
     * method manages the leases requested and held by the
     * <code>ServiceDiscoveryManager</code>. In general, these leases correspond
     * to the registrations made by the <code>ServiceDiscoveryManager</code>
     * with the event mechanism of each lookup service in the managed set.
     *
     * @return LeaseRenewalManager for this instance of the
     * <code>ServiceDiscoveryManager</code>.
     * @see net.jini.lease.LeaseRenewalManager
     */
    public LeaseRenewalManager getLeaseRenewalManager() {
        checkTerminated();
        return leaseRenewalMgr;
    }//end getLeaseRenewalManager

    /**
     * The <code>terminate</code> method performs cleanup duties related to the
     * termination of the event mechanism for lookup service discovery, the
     * event mechanism for service discovery, and the cache management duties of
     * the <code>ServiceDiscoveryManager</code>.
     * <p>
     * For each instance of <code>LookupCache</code> created and managed by the
     * <code>ServiceDiscoveryManager</code>, the <code>terminate</code> method
     * will do the following:
     * <ul>
     * <li>Either remove all listener objects registered for receipt of
     * <code>DiscoveryEvent</code> objects or, if the discovery manager employed
     * by the <code>ServiceDiscoveryManager</code> was created by the
     * <code>ServiceDiscoveryManager</code> itself, terminate all discovery
     * processing being performed by that manager object on behalf of the
     * entity.
     * <p>
     * <li>Cancel all event leases granted by each lookup service in the managed
     * set of lookup services.
     * <p>
     * <li>Un-export all remote listener objects registered with each lookup
     * service in the managed set.
     * <p>
     * <li>Terminate all threads involved in the process of retrieving and
     * storing references to discovered services of interest.
     * </ul>
     * Calling any method after the termination will result in an
     * <code>IllegalStateException</code>.
     *
     * @see net.jini.lookup.LookupCache
     * @see net.jini.discovery.DiscoveryEvent
     */
    public void terminate() {
        synchronized (this) {
            if (bTerminated) {
                return;//allow for multiple terminations
            }
            bTerminated = true;
            /* Terminate lookup service discovery processing */
            discMgr.removeDiscoveryListener(discMgrListener);
            if (discMgrInternal) {
                discMgr.terminate();
            }
        }//end sync
        terminatorThread.interrupt();
        /* Terminate all caches: cancel event leases, un-export listeners */
        List<LookupCache> terminate;
        synchronized (caches) {
            terminate = new ArrayList<LookupCache>(caches);
        }
        Iterator iter = terminate.iterator();
        while (iter.hasNext()) {
            LookupCacheImpl cache = (LookupCacheImpl) iter.next();
            cache.terminate();
        }//end loop
        leaseRenewalMgr.close();
    }//end terminate

    /**
     * Queries each available lookup service in the managed set for service(s)
     * that match the input criteria. The semantics of this method are similar
     * to the semantics of the <code>lookup</code> method provided by the
     * <code>ServiceRegistrar</code> interface; employing the same
     * template-matching scheme. Additionally, this method allows any entity to
     * supply an object referred to as a <i>filter</i>. Such an object is a
     * non-remote object that defines additional matching criteria that the
     * <code>ServiceDiscoveryManager</code> applies when searching for the
     * entity's services of interest. This filtering facility is particularly
     * useful to entities that wish to extend the capabilities of standard
     * template-matching.
     * <p>
     * Entities typically employ this method when they need infrequent access to
     * multiple instances of services, and when the cost of making remote
     * queries is outweighed by the overhead of maintaining a local cache (for
     * example, because of resource limitations).
     * <p>
     * This version of <code>lookup</code> returns an <i>array</i> of instances
     * of <code>ServiceItem</code> in which each element corresponds to a
     * service reference that satisfies the matching criteria. The number of
     * elements in the returned set will be no greater than the value of the
     * <code>maxMatches</code> parameter, but may be less.
     * <p>
     * Note that this version of <code>lookup</code> does not provide a
     * <i>blocking</i> feature. That is, this version will return immediately
     * with whatever number of service references it can find, up to the number
     * indicated in the <code>maxMatches</code> parameter. If no services
     * matching the input criteria can be found on the first attempt, an empty
     * array is returned.
     *
     * It is important to understand this characteristic because there is a
     * common usage scenario that can cause confusion when this version of
     * <code>lookup</code> is used but fails to discover any instances of the
     * expected service of interest. Suppose an entity creates a service
     * discovery manager and then immediately calls this version of
     * <code>lookup</code>, which simply queries the currently discovered lookup
     * services for the service of interest. If the discovery manager employed
     * by the service discovery manager has not yet discovered any lookup
     * services (thus, there are no lookup services to query) the method will
     * immediately return an empty array. This can be confusing when one
     * verifies that instance(s) of such a service of interest have indeed been
     * started and registered with the existing lookup service(s). To address
     * this issue, one of the blocking versions of <code>lookup</code> could be
     * used instead of this version, or the entity could simply wait until the
     * discovery manager has been given enough time to complete its own (lookup)
     * discovery processing.
     *
     * @param tmpl an instance of <code>ServiceTemplate</code> corresponding to
     * the object to use for template-matching when searching for desired
     * services. If <code>null</code> is input to this parameter, this method
     * will use a <i>wildcarded</i> template (will match all services) when
     * performing template-matching. Note that the effects of modifying contents
     * of this parameter before this method returns are unpredictable and
     * undefined.
     * @param maxMatches this method will return no more than this number of
     * service references
     * @param filter an instance of <code>ServiceItemFilter</code> containing
     * matching criteria that should be applied in addition to the
     * template-matching employed when searching for desired services. If
     * <code>null</code> is input to this parameter, then only template-matching
     * will be employed to find the desired services.
     *
     * @return an array of instances of <code>ServiceItem</code> where each
     * element corresponds to a reference to a service that matches the criteria
     * represented in the input parameters; or an empty array if no matching
     * service can be found.
     *
     * @see net.jini.core.lookup.ServiceRegistrar#lookup
     * @see net.jini.core.lookup.ServiceTemplate
     * @see net.jini.lookup.ServiceItemFilter
     */
    public ServiceItem[] lookup(ServiceTemplate tmpl,
            int maxMatches,
            ServiceItemFilter filter) {
        checkTerminated();
        if (maxMatches < 1) {
            throw new IllegalArgumentException("maxMatches must be > 0");
        }
        /* retrieve the lookup service(s) to query for matching service(s) */
        ServiceRegistrar[] proxys = buildServiceRegistrar();

        int len = proxys.length;
        List<ServiceItem> sItemSet = new ArrayList<ServiceItem>(len);
        if (len > 0) {
            /* loop thru the set of lookups, randomly selecting each lookup */
            int rand = (random.nextInt(Integer.MAX_VALUE)) % len;
            for (int i = 0; i < len; i++) {
                int max = maxMatches;
                ServiceRegistrar proxy = proxys[(i + rand) % len];
                try {
                    /* If a filter is to be applied (filter != null), then
                     * the value of the maxMatches parameter will not
                     * suffice when querying the current lookup service.
                     * This is because although services returned from a
                     * query of the lookup service will match the template,
                     * some of those services may get filtered out. Thus,
                     * asking for exactly maxMatches may result in fewer
                     * matching services than actually are contained in
                     * the lookup. Thus, all matching services are
                     * requested by passing in "infinity" for the maximum
                     * number of matches (Integer.MAX_VALUE).
                     */
                    if (filter != null) {
                        max = Integer.MAX_VALUE;
                    }
                    /* Query the current lookup for matching service(s). */
                    ServiceMatches sm = proxy.lookup(tmpl, max);
                    int nItems = sm.items.length;
                    if (nItems == 0) {
                        continue;//no matches, query next lookup
                    }                    /* Loop thru the matching services, randomly selecting
                     * each service, applying the filter if appropriate,
                     * and making sure the service has not already been
                     * selected (it may have been returned from a previously
                     * queried lookup).
                     */

                    int r = (random.nextInt(Integer.MAX_VALUE)) % nItems;
                    for (int j = 0; j < nItems; j++) {
                        ServiceItem sItem = sm.items[(j + r) % nItems];
                        if (sItem == null) {
                            continue;
                        }
                        if (!filterPassed(sItem, filter)) {
                            continue;
                        }
                        if (!isArrayContainsServiceItem(sItemSet, sItem)) {
                            sItemSet.add(sItem);
                        }
                        if (sItemSet.size() >= maxMatches) {
                            return sItemSet.toArray(new ServiceItem[sItemSet.size()]);
                        }
                    }//end loop(j)
                } catch (Exception e) {
                    logger.log(Level.INFO,
                            "Exception occurred during query, "
                            + "discarding proxy",
                            e);
                    discard(proxy);
                }
            }//end loop(i)
        }//endif(len>0)
        /* Will reach this return statement only when less than the number
         * of services requested have been found in the loop above.
         */
        return (ServiceItem[]) (sItemSet.toArray(new ServiceItem[sItemSet.size()]));
    }//end lookup

    /**
     * Queries each available lookup service in the managed set for service(s)
     * that match the input criteria. The semantics of this method are similar
     * to the semantics of the <code>lookup</code> method provided by the
     * <code>ServiceRegistrar</code> interface; employing the same
     * template-matching scheme. Additionally, this method allows any entity to
     * supply an object referred to as a <i>filter</i>. Such an object is a
     * non-remote object that defines additional matching criteria that the
     * <code>ServiceDiscoveryManager</code> applies when searching for the
     * entity's services of interest. This filtering facility is particularly
     * useful to entities that wish to extend the capabilities of standard
     * template-matching.
     * <p>
     * This version of <code>lookup</code> returns an <i>array</i> of instances
     * of <code>ServiceItem</code> in which each element corresponds to a
     * service reference that satisfies the matching criteria. The number of
     * elements in the returned set will be no greater than the value of the
     * <code>maxMatches</code> parameter, but may be less.
     * <p>
     * Note that this version of <code>lookup</code> provides a
     * <i>blocking</i> feature that is controlled through the
     * <code>waitDur</code> parameter in conjunction with the
     * <code>minMatches</code> and the <code>maxMatches</code> parameters. This
     * method will not return until one of the following occurs:
     * <p>
     * <ul>
     * <li> the number of matching services found on the first attempt is
     * greater than or equal to the value of the <code>minMatches</code>
     * parameter, in which case this method returns each of the services found
     * up to the value of the <code>maxMatches</code> parameter
     * <li> the number of matching services found <i>after</i> the first attempt
     * (that is, after the method enters the "wait state") is at least as great
     * as the value of the <code>minMatches</code> parameter in which case this
     * method returns each of the services found up to the value of the
     * <code>maxMatches</code> parameter
     * <li> the amount of time that has passed since this method entered the
     * wait state exceeds the value of the <code>waitDur</code> parameter, in
     * which case this method returns all of the currently discovered services
     * </ul>
     * <p>
     * The purpose of the <code>minMatches</code> parameter is to allow the
     * entity to balance its need for multiple matching service references with
     * its need to minimize the time spent in the wait state; time that most
     * would consider wasted if an acceptable number of matching service
     * references were found, but this method continued to wait until the end of
     * the designated time period.
     * <p>
     * If, while waiting for the minimum number of desired services to be
     * discovered, the entity decides that it no longer wishes to wait the
     * entire period for this method to return, the entity may interrupt this
     * method by invoking the interrupt method from the <code>Thread</code>
     * class. The intent of this mechanism is to allow the entity to interrupt
     * this method in the same way it would a sleeping thread.
     * <p>
     * Entities typically employ this method when they need infrequent access to
     * multiple instances of services, are willing (or forced) to wait for those
     * services to be found, and consider the cost of making remote queries for
     * those services is outweighed by the overhead of maintaining a local cache
     * (for example, because of resource limitations).
     *
     * @param tmpl an instance of <code>ServiceTemplate</code> corresponding to
     * the object to use for template-matching when searching for desired
     * services. If <code>null</code> is input to this parameter, this method
     * will use a
     * <i>wildcarded</i> template (will match all services) when performing
     * template-matching. Note that the effects of modifying contents of this
     * parameter before this method returns are unpredictable and undefined.
     * @param minMatches this method will immediately exit the wait state and
     * return once this number of service references is found
     * @param maxMatches this method will return no more than this number of
     * service references
     * @param filter an instance of <code>ServiceItemFilter</code> containing
     * matching criteria that should be applied in addition to the
     * template-matching employed when searching for desired services. If
     * <code>null</code> is input to this parameter, then only template-matching
     * will be employed to find the desired services.
     * @param waitDur the amount of time (in milliseconds) to wait before ending
     * the "search" and returning an empty array. If a non-positive value is
     * input to this parameter, then this method will not wait; it will simply
     * query the available lookup services and return whatever matching service
     * reference(s) it could find, up to <code>maxMatches</code>.
     *
     * @return an array of instances of <code>ServiceItem</code> where each
     * element corresponds to a reference to a service that matches the criteria
     * represented in the input parameters; or an empty array if no matching
     * service can be found within the time allowed.
     *
     * @throws java.lang.InterruptedException this exception occurs when the
     * entity interrupts this method by invoking the interrupt method from the
     * <code>Thread</code> class.
     *
     * @throws java.lang.IllegalArgumentException this exception occurs when one
     * of the following conditions is satisfied:
     * <p>
     * <ul> <li>the <code>minMatches</code> parameter is non-positive
     * <li>the <code>maxMatches</code> parameter is non-positive
     * <li>the value of <code>maxMatches</code> is <i>less than</i>
     * the value of <code>minMatches</code>
     * </ul>
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when a
     * RemoteException occurs either as a result of an attempt to export a
     * remote listener, or an attempt to register with the event mechanism of a
     * lookup service.
     *
     * @see net.jini.core.lookup.ServiceRegistrar#lookup
     * @see net.jini.core.lookup.ServiceTemplate
     * @see net.jini.lookup.ServiceItemFilter
     * @see java.lang.Thread
     */
    public ServiceItem[] lookup(ServiceTemplate tmpl,
            int minMatches,
            int maxMatches,
            ServiceItemFilter filter,
                                long waitDur )  throws InterruptedException,
                                                       RemoteException
    {
        checkTerminated();
        if (minMatches < 1) {
            throw new IllegalArgumentException("minMatches must be > 0");
        }
        if (maxMatches < minMatches) {
            throw new IllegalArgumentException("maxMatches must be > minMatches");
        }

        long delay = System.currentTimeMillis();
        ServiceItem[] sItems = lookup(tmpl, maxMatches, filter);
        if (sItems.length >= minMatches) {
            return sItems;
        }
        List<ServiceItem> sItemSet = new LinkedList<ServiceItem>();
        for (int i = 0, l = sItems.length; i < l; i++) {
	    //if(!sItemSet.contains(sItems[i])
            //sItemSet.add(sItems[i]);
            if (!isArrayContainsServiceItem(sItemSet, sItems[i])) {
                sItemSet.add(sItems[i]);
            }//endif
        }//end loop       
        ServiceDiscoveryListenerImpl cacheListener
                = new ServiceDiscoveryListenerImpl();
        /* The cache must be created inside the listener sync block,
         * otherwise a race condition can occur. This is because the
         * creation of a cache results in event registration which
         * will ultimately result in the invocation of the serviceAdded()
         * method in the cache's listener, and the interruption of any
         * objects waiting on the cache's listener. If the notifications
         * happen to occur before commencing the wait on the listener
         * object (see below), then the wait will never be interrupted
         * because the interrupts were sent before the wait() method
         * was invoked. Synchronizing on the listener and the listener's
         * serviceAdded() method, and creating the cache only after the
         * lock has been acquired, together will prevent this situation
         * since event registration cannot occur until the cache is
         * created, and the lock that allows entry into the serviceAdded()
         * method (which is invoked once the events do arrive) is not
         * released until the wait() method is invoked.
         */
        LookupCacheImpl cache;
        synchronized (cacheListener) { // uncontended lock.
            delay = (System.currentTimeMillis() - delay) + 1; // Calculate initial time delay in ms.
            cache = createLookupCache(tmpl, filter, cacheListener, waitDur);
            long duration = cache.getLeaseDuration();
            while (duration > delay) { // Some milli's to spare to ensure we return in reasonable time.
                cacheListener.wait(duration - delay);
                ServiceItem items[] = cacheListener.getServiceItem();
                for (int i = 0, l = items.length; i < l; i++) {
                    if (!isArrayContainsServiceItem(sItemSet, items[i])) {
                        sItemSet.add(items[i]);
                    }//endif
                }//end loop
                if (sItemSet.size() == minMatches) {
                    break;
                }
                duration = cache.getLeaseDuration();
            }//end loop
        }//end sync(cacheListener)
        // Termination is now performed by a dedicated thread to ensure
        // Remote method call doesn't take too long.
        terminator.terminate(cache);
        if (sItemSet.size() > maxMatches) {
            // Discard some matches
            ServiceItem[] r = new ServiceItem[maxMatches];
            // Iterator is faster for LinkedList.
            Iterator<ServiceItem> it = sItemSet.iterator();
            for (int i = 0; it.hasNext() && i < maxMatches; i++) {
                r[i] = it.next();
            }
            return r;
        }
        ServiceItem[] r = new ServiceItem[sItemSet.size()];
        sItemSet.toArray(r);
        return r;
    }//end lookup

    /**
     * From the given set of ServiceMatches, randomly selects and returns a
     * ServiceItem that matches the given filter (if applicable).
     */
    private ServiceItem getMatchedServiceItem(ServiceMatches sm,
            ServiceItemFilter filter) {
        int len = sm.items.length;
        if (len > 0) {
            int rand = random.nextInt(Integer.MAX_VALUE) % len;
            for (int i = 0; i < len; i++) {
                ServiceItem sItem = sm.items[(i + rand) % len];
                if (sItem == null) continue;
                if (!filterPassed(sItem, filter))  continue;
                return sItem;
            }//end loop
        }//endif
        return null;
    }//end getMatchedServiceItem

    /**
     * Creates a LookupCache with specific lease duration.
     */
    private LookupCacheImpl createLookupCache(ServiceTemplate tmpl,
            ServiceItemFilter filter,
            ServiceDiscoveryListener listener,
            long leaseDuration)
            throws RemoteException {
        /* Atomic start of terminator */
        synchronized (terminatorThread) {
            if (!started) {
                terminatorThread.start();
            }
            started = true;
        }
        if (tmpl == null) {
            tmpl = new ServiceTemplate(null, null, null);
        }
        LookupCacheImpl cache = new LookupCacheImpl(tmpl, filter, listener, leaseDuration, this);
        cache.initCache();
        synchronized (caches) {
            caches.add(cache);
        }
        logger.finest("ServiceDiscoveryManager - LookupCache created");
        return cache;
    }//end createLookupCache

    /**
     * Removes and returns element from proxyRegSet that corresponds to the
     * given proxy.
     */
    private ProxyReg removeReg(ServiceRegistrar proxy) {
        Iterator<ProxyReg> iter = proxyRegSet.iterator();
        while (iter.hasNext()) {
            ProxyReg reg = iter.next();
            // ProxyReg hashcode is same as proxy - optimisation
            if (reg.hashCode() == proxy.hashCode()) {
                if (reg.getProxy().equals(proxy)) {
                    iter.remove();
                    return reg;
                }
            }
        }//end loop
        return null;
    }//end removeReg

    /**
     * Convenience method invoked when failure occurs in the cache tasks
     * executed in this utility. If the appropriate logging level is enabled,
     * this method will log the stack trace of the given <code>Throwable</code>;
     * noting the given source class and method, and displaying the given
     * message. Additionally, this method will discard the given lookup service
     * proxy. Note that if the utility itself has already been terminated, or if
     * the cache in which the failure occurred has been terminated, then the
     * failure is logged at the HANDLED level, and the lookup service proxy is
     * not discarded.
     *
     * Also, note that if the discovery manager employed by this utility has
     * already been terminated, then the attempt to discard the given lookup
     * service proxy will result in an <code>IllegalStateException</code>. Since
     * this method is called from within the tasks run by this utility, and
     * since propagating an <code>IllegalStateException</code> out into the
     * ThreadGroup of those tasks is undesirable, this method does not propagate
     * <code>IllegalStateException</code>s that occur as a result of an attempt
     * to discard a lookup service proxy from the discovery manager.
     *
     * For more information, refer to Bug 4490358 and 4858211.
     */
    private void fail(Throwable e,
            ServiceRegistrar proxy,
            String sourceClass,
            String sourceMethod,
            String msg,
            boolean cacheTerminated) {
        Level logLevel = Level.INFO;
        boolean discardProxy = true;
        synchronized (this) {
            if (bTerminated || cacheTerminated) {
                logLevel = Levels.HANDLED;
                discardProxy = false;
            }//endif
        }//end sync(this)
        if ((e != null) && (logger.isLoggable(logLevel))) {
            logger.logp(logLevel, sourceClass, sourceMethod, msg, e);
        }//endif
        try {
            if (discardProxy) {
                discard(proxy);
            }
        } catch (IllegalStateException e1) {
            if (logger.isLoggable(logLevel)) {
                logger.logp(logLevel,
                        sourceClass,
                        sourceMethod,
                        "failure discarding lookup service proxy, "
                        + "discovery manager already terminated",
                        e1);
            }//endif
        }
    }//end fail

    /**
     * Discards a ServiceRegistrar through the discovery manager.
     */
    private void discard(ServiceRegistrar proxy) {
        discMgr.discard(proxy);
    }//end discard

    /**
     * Cancels the given event lease.
     */
    private void cancelLease(Lease lease) {
        try {
            leaseRenewalMgr.cancel(lease);
        } catch (Exception e) {
            logger.log(Level.FINER,
                    "exception occurred while cancelling an event "
                    + "registration lease",
                    e);
        }
    }//end cancelLease

    /**
     * Registers for events from the lookup service associated with the given
     * proxy, and returns both the lease and the event sequence number from the
     * event registration wrapped in the locally-defined class,
     * <code>EventReg</code>.
     *
     * This method is called from the <code>RegisterListenerTask</code>. If a
     * <code>RemoteException</code> occurs during the event registration
     * attempt, this method discards the lookup service and returns
     * <code>null</code>.
     */
    private EventReg registerListener(ServiceRegistrar proxy,
            ServiceTemplate tmpl,
            RemoteEventListener listenerProxy,
            long duration) throws RemoteException {
        /* Register with the event mechanism of the given lookup service */
        EventRegistration e;
        int transition = (ServiceRegistrar.TRANSITION_NOMATCH_MATCH
                | ServiceRegistrar.TRANSITION_MATCH_NOMATCH
                | ServiceRegistrar.TRANSITION_MATCH_MATCH);
        e = proxy.notify(tmpl, transition, listenerProxy, null, duration);
        /* Proxy preparation -
         *
         * Prepare the proxy to the lease on the event registration just
         * returned. Because lease management (renewal and cancellation)
         * involves remote calls, lease proxies should be prepared before
         * management of the associated leases begins. This allows one to
         * verify trust in the lease, and ensures that the appropriate
         * constraints are attached to the lease.
         */
        Lease eventLease = e.getLease();
        eventLease = (Lease) eventLeasePreparer.prepareProxy(eventLease);
        logger.log(Level.FINEST, "ServiceDiscoveryManager - proxy to event "
                + "registration lease prepared: {0}", eventLease);
        /* Management the lease on the event registration */
        leaseRenewalMgr.renewFor(eventLease,
                duration,
                new LeaseListenerImpl(proxy));
        /* Wrap source, id, event sequence & lease in EventReg, and return. */
        return (new EventReg(e.getSource(),
                e.getID(),
                e.getSequenceNumber(),
                eventLease));
    }//end registerListener

    /**
     * Throws an IllegalStateException if the current instance of the
     * ServiceDiscoveryManager has been terminated.
     */
    private synchronized void checkTerminated() {
        if (bTerminated) {
            throw new IllegalStateException("service discovery manager was terminated");
        }//endif
    }//end checkTerminated

    /**
     * Returns a "non-shallow" (not just a clone) copy of the given template.
     */
    static private ServiceTemplate copyServiceTemplate(ServiceTemplate tmpl) {
        Class[] serviceTypes = null;
        Entry[] attributeSetTemplates = null;
        if (tmpl.serviceTypes != null) {
            int len = tmpl.serviceTypes.length;
            serviceTypes = new Class[len];
            System.arraycopy(tmpl.serviceTypes, 0, serviceTypes, 0, len);
        }
        if (tmpl.attributeSetTemplates != null) {
            int len = tmpl.attributeSetTemplates.length;
            attributeSetTemplates = new Entry[len];
            System.arraycopy(tmpl.attributeSetTemplates, 0,
                    attributeSetTemplates, 0, len);
        }
        return new ServiceTemplate(tmpl.serviceID,
                serviceTypes,
                attributeSetTemplates);
    }//end copyServiceTemplate

    /**
     * Determines if the given ServiceItem is an element of the given array.
     */
    static private boolean isArrayContainsServiceItem(List<ServiceItem> a,
            ServiceItem s) {
        Iterator<ServiceItem> iter = a.iterator();
        while (iter.hasNext()) {
            Object o = iter.next();
            if (!(o instanceof ServiceItem)) {
                continue;
            }
            ServiceItem sa = (ServiceItem) o;
            if (sa.serviceID.equals(s.serviceID)
                    && LookupAttributes.equal(sa.attributeSets, s.attributeSets)
                    && (sa.service.equals(s.service))) {
                return true;
            }
        }//end loop
        return false;
    }//end isArrayContainsServiceItems

    private static class Initializer {

        Configuration thisConfig;
        ProxyPreparer registrarPreparer;
        ProxyPreparer eventLeasePreparer;
        LeaseRenewalManager leaseRenewalMgr;
        Long discardWait = Long.valueOf(2 * (5 * 60 * 1000));
        DiscoveryManagement discMgr;
        boolean discMgrInternal;
    }

    private static Initializer initial(
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException {
        try {
            return init(discoveryMgr, leaseMgr, config);
        } catch (ConfigurationException e) {
            /* This should never happen */
            throw new IOException(e);
        }
    }

    /* Convenience method that encapsulates the retrieval of the configurable
     * items from the given <code>Configuration</code> object.
     */
    private static Initializer init(DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException, ConfigurationException {
        /* Retrieve configuration items if applicable */
        if (config == null) {
            throw new NullPointerException("config is null");
        }
        Initializer init = new Initializer();
        init.thisConfig = config;
        /* Proxy preparers */
        init.registrarPreparer = init.thisConfig.getEntry(COMPONENT_NAME,
                "registrarPreparer",
                ProxyPreparer.class,
                new BasicProxyPreparer());
        init.eventLeasePreparer = init.thisConfig.getEntry(COMPONENT_NAME,
                "eventLeasePreparer",
                ProxyPreparer.class,
                new BasicProxyPreparer());
        /* Lease renewal manager */
        init.leaseRenewalMgr = leaseMgr;
        if (init.leaseRenewalMgr == null) {
            try {
                init.leaseRenewalMgr
                        = init.thisConfig.getEntry(COMPONENT_NAME,
                                "leaseManager",
                                LeaseRenewalManager.class);
            } catch (NoSuchEntryException e) { /* use default */

                init.leaseRenewalMgr = new LeaseRenewalManager(init.thisConfig);
            }
        }//endif
        /* Wait value for the "service discard problem". */
        init.discardWait = (init.thisConfig.getEntry(COMPONENT_NAME,
                "discardWait",
                long.class,
                init.discardWait));
        /* Discovery manager */
        init.discMgr = discoveryMgr;
        if (init.discMgr == null) {
            init.discMgrInternal = true;
            try {
                init.discMgr = init.thisConfig.getEntry(COMPONENT_NAME,
                        "discoveryManager",
                        DiscoveryManagement.class);
            } catch (NoSuchEntryException e) { /* use default */

                init.discMgr = new LookupDiscoveryManager(new String[]{""}, null, null, init.thisConfig);
            }
        }//endif
        return init;
    }//end init

    /**
     * Applies the given <code>filter</code> to the given <code>item</code>, and
     * returns <code>true</code> if the <code>filter</code> returns a
     * <code>pass</code> value; otherwise, returns <code>false</code>.
     * <p>
     * Note that as described in the specification of
     * <code>ServiceItemFilter</code>, when the <code>item</code> passes the
     * <code>filter</code>, the <code>service</code> field of the
     * <code>item</code> is replaced with the filtered form of the object
     * previously contained in that field. Additionally, if the
     * <code>filter</code> returns <code>indefinite</code>, then as specified,
     * the <code>service</code> field is replaced with <code>null</code> (in
     * which case, this method returns <code>false</code>).
     * <p>
     * This method is used by the non-blocking version(s) of the
     * <code>lookup</code> method of the <code>ServiceDiscoveryManager</code>,
     * as well as when second-stage filtering is performed in the
     * <code>LookupCache</code>.
     */
    private boolean filterPassed(ServiceItem item, ServiceItemFilter filter) {
        if ((item == null) || (item.service == null)) {
            return false;
        }
        if (filter == null) {
            return true;
        }
        return filter.check(item);
    }//end filterPassFail

}//end class ServiceDiscoveryManager
