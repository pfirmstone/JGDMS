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

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;
import net.jini.config.NoSuchEntryException;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.export.ServiceAttributesAccessor;
import net.jini.export.ServiceIDAccessor;
import net.jini.export.ServiceProxyAccessor;
import net.jini.lease.LeaseListener;
import net.jini.lease.LeaseRenewalEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import org.apache.river.logging.Levels;
import org.apache.river.lookup.entry.LookupAttributes;

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
 * 
 * <a name="bootstrapPreparer">
 * <table summary="Describes the bootstrapPreparer configuration entry"
 * border="0" cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>bootstrapPreparer</code></font>
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
 * <td> Preparer for bootstrap proxy results returned by lookup services 
 * that are discovered and used by this utility.
 * This item is used only by the service discovery
 * manager, and not by any cache that is created.
 * <p>
 * The following methods of the proxy returned by this preparer are invoked by
 * this utility:
 * <ul>
 * <li>{@link net.jini.core.lookup.ServiceRegistrar#lookUp(net.jini.core.lookup.ServiceTemplate, int)  lookup}
 * <li>{@link net.jini.core.lookup.ServiceRegistrar#notify notify}
 * </ul>
 *
 * </table>
 * </a>
 * 
 *
 * <a name="useInsecureLookup">
 * <table summary="Describes the useInsecureLookup configuration entry"
 * border="0" cellpadding="2">
 * <tr valign="top">
 * <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 * <th scope="col" align="left" colspan="2"> <font size="+1">
 * <code>useInsecureLookup</code></font>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Type: <td> {@link java.lang.Boolean}
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Default: <td> <code>new {@link java.lang.Boolean#FALSE}()
 * </code>
 *
 * <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 * Description:
 * <td> When true, ServiceDiscoveryManager and LookupCache use {@link net.jini.core.lookup.ServiceRegistrar#lookup(net.jini.core.lookup.ServiceTemplate, int) 
 * instead of {@link net.jini.core.lookup.ServiceRegistrar#lookUp(net.jini.core.lookup.ServiceTemplate, int) 
 * to perform service discovery.
 * </table>
 * </a>
 * 
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
    
    final ProxyPreparer bootstrapProxyPreparer;
    final boolean useInsecureLookup;


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



    /* Name of this component; used in config entry retrieval and the logger.*/
    static final String COMPONENT_NAME
            = "net.jini.lookup.ServiceDiscoveryManager";
    /* Logger used by this utility. */
    static final Logger logger = Logger.getLogger(COMPONENT_NAME);
    /* The discovery manager to use (passed in, or create one). */
    final DiscoveryManagement discMgr;
    /* Indicates whether the discovery manager was created internally or not */
    final boolean discMgrInternal;
    /* The listener added to discMgr that receives DiscoveryEvents */
    final DiscMgrListener discMgrListener;
    /* The LeaseRenewalManager to use (passed in, or create one). */
    final LeaseRenewalManager leaseRenewalMgr;
    /* Contains all of the discovered lookup services (ServiceRegistrar). */
    final Set<ProxyReg> proxyRegSet;
    /* Random number generator for use in lookup. */
    final Random random = new Random();
    /* Contains all of the instances of LookupCache that are requested. */
    private final List<LookupCache> caches;

    /* Flag to indicate if the ServiceDiscoveryManager has been terminated. */
    private boolean bTerminated = false; //sync on this

    private final Thread terminatorThread;
    private final LookupCacheTerminator terminator;
    /* Flag to indicate LookupCacheTerminator has been started */
    private boolean started = false; // sync on terminatorThread
    /* Object used to obtain the configuration items for this utility. */
    final Configuration thisConfig;
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
    final long discardWait;

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
	bootstrapProxyPreparer = init.bootstrapProxyPreparer;
	useInsecureLookup = init.useInsecureLookup;
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
		Object [] matches;
		if (useInsecureLookup){
		    ServiceMatches sm = proxy.lookup(tmpl, maxMatches);
		    matches = sm.items;
		} else {
		    matches = proxy.lookUp(tmpl, maxMatches);
		}
		if (matches == null) continue;
                sItem = getMatchedServiceItem(matches, filter);
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
            ServiceItemFilter filter) 
    {
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
		    Object [] result;
		    if (useInsecureLookup){
			ServiceMatches matches = proxy.lookup(tmpl, max);
			result = matches.items;
		    } else {
			result = proxy.lookUp(tmpl, max);
		    }
		    if (result == null) continue;
                    int nItems = result.length;
                    if (nItems == 0) {
                        continue;//no matches, query next lookup
                    }                    
		    /* Loop thru the matching services, randomly selecting
                     * each service, applying the filter if appropriate,
                     * and making sure the service has not already been
                     * selected (it may have been returned from a previously
                     * queried lookup).
                     */

                    int r = (random.nextInt(Integer.MAX_VALUE)) % nItems;
                    for (int j = 0; j < nItems; j++) {
                        Object obj = result[(j + r) % nItems];
                        if (obj == null) continue;
			ServiceItem sItem;
			if (useInsecureLookup){
			    sItem = (ServiceItem) obj;
			    if (!filterPassed(sItem, filter)) continue;
			} else {
			    sItem = check(obj, filter, bootstrapProxyPreparer);
			    if (sItem == null) continue;
			}                     
                        if (!isArrayContainsServiceItem(sItemSet, sItem)) {
                            sItemSet.add(sItem);
                        }
                        if (sItemSet.size() >= maxMatches) {
                            return sItemSet.toArray(new ServiceItem[sItemSet.size()]);
                        }
                    }                } catch (Exception e) {
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
    private ServiceItem getMatchedServiceItem(Object [] sm,
						ServiceItemFilter filter) 
    {
        int len = sm.length;
        if (len > 0) {
            int rand = random.nextInt(Integer.MAX_VALUE) % len;
            for (int i = 0; i < len; i++) {
                Object sItem = sm[(i + rand) % len];
                if (sItem == null) continue;
		ServiceItem item = null;
		if (useInsecureLookup){
		    item = (ServiceItem) sItem;
		    if (filterPassed(item, filter)) return item;
		} else {
		    item = check(sItem, filter, bootstrapProxyPreparer);
		    if (item == null) continue;
		    return item;
		}
            }//end loop
        }//endif
        return null;
    }//end getMatchedServiceItem
    
    /**
     * 
     * @param bootstrapProxy
     * @param filter
     * @param bootstrapPreparer
     * @param serviceProxyPreparer
     * @return a new ServiceItem if preparation and filter are successful, or
     * null.
     */
    private ServiceItem check(Object bootstrapProxy, ServiceItemFilter filter,
	    ProxyPreparer bootstrapPreparer) 
    {
	try {
	    Object preparedProxy = bootstrapPreparer.prepareProxy(bootstrapProxy);
	    if (!(preparedProxy instanceof ServiceAttributesAccessor) &&
		    !(preparedProxy instanceof ServiceIDAccessor) &&
		    !(preparedProxy instanceof ServiceProxyAccessor)) return null;
	    Entry[] serviceAttributes =
		    ((ServiceAttributesAccessor) preparedProxy).getServiceAttributes();
	    ServiceID serviceID = ((ServiceIDAccessor) preparedProxy).serviceID();
	    ServiceItem item = new ServiceItem(serviceID, bootstrapProxy, serviceAttributes);
	    if (filter.check(item)) return item;
	    return null;
	} catch (IOException ex) {
	    return null;
	}
    }

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
     * LookupCache removes itself once it has been terminated.
     * @param cache
     * @return true if removed.
     */
    boolean removeLookupCache(LookupCache cache){
	synchronized (caches) {
	    return caches.remove(cache);
	}
    }

    /**
     * Removes and returns element from proxyRegSet that corresponds to the
     * given proxy.
     */
    ProxyReg removeReg(ServiceRegistrar proxy) {
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
    void fail(Throwable e,
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
    void cancelLease(Lease lease) {
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
    EventReg registerListener(ServiceRegistrar proxy,
            ServiceTemplate tmpl,
            RemoteEventListener listenerProxy,
            long duration) throws RemoteException {
        /* Register with the event mechanism of the given lookup service */
        EventRegistration e;
        int transition = (ServiceRegistrar.TRANSITION_NOMATCH_MATCH
                | ServiceRegistrar.TRANSITION_MATCH_NOMATCH
                | ServiceRegistrar.TRANSITION_MATCH_MATCH);
	if (useInsecureLookup){
	    e = proxy.notify(tmpl, transition, listenerProxy, null, duration);
	} else {
	    e = proxy.notiFy(tmpl, transition, listenerProxy, null, duration);
	}
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
    synchronized void checkTerminated() {
        if (bTerminated) {
            throw new IllegalStateException("service discovery manager was terminated");
        }//endif
    }//end checkTerminated

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

    /**
     * Initializer for ServiceDiscoveryManager
     */
    private static class Initializer {

        Configuration thisConfig;
        ProxyPreparer registrarPreparer;
        ProxyPreparer eventLeasePreparer;
	ProxyPreparer bootstrapProxyPreparer;
        LeaseRenewalManager leaseRenewalMgr;
        Long discardWait = Long.valueOf(2 * (5 * 60 * 1000));
        DiscoveryManagement discMgr;
        boolean discMgrInternal;
	boolean useInsecureLookup;
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
	init.bootstrapProxyPreparer = init.thisConfig.getEntry(COMPONENT_NAME,
                "bootstrapProxyPreparer",
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
	init.useInsecureLookup = (init.thisConfig.getEntry(COMPONENT_NAME,
		"useInsecureLookup",
		Boolean.class,
		Boolean.TRUE));
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
    boolean filterPassed(ServiceItem item, ServiceItemFilter filter) {
        if ((item == null) || (item.service == null)) {
            return false;
        }
        if (filter == null) {
            return true;
        }
        return filter.check(item);
    }//end filterPassFail

}//end class ServiceDiscoveryManager
