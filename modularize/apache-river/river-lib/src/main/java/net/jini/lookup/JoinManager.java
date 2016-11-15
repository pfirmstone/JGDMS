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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;
import net.jini.config.NoSuchEntryException;
import net.jini.core.entry.CloneableEntry;
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseListener;
import net.jini.lease.LeaseRenewalEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import org.apache.river.constants.ThrowableConstants;
import org.apache.river.logging.LogUtil;
import org.apache.river.lookup.entry.LookupAttributes;
import org.apache.river.thread.DependencyLinker;
import org.apache.river.thread.ExtensibleExecutorService;
import org.apache.river.thread.ExtensibleExecutorService.RunnableFutureFactory;
import org.apache.river.thread.FutureObserver;
import org.apache.river.thread.NamedThreadFactory;
import org.apache.river.thread.RetryTask;
import org.apache.river.thread.WakeupManager;

/**
 * A goal of any well-behaved service is to advertise the facilities and
 * functions it provides by requesting residency within at least one lookup
 * service. Making such a request of a lookup service is known as registering
 * with, or <i>joining</i>, a lookup service. To demonstrate this good
 * behavior, a service must comply with both the multicast discovery protocol
 * and the unicast discovery protocol in order to discover the lookup services
 * it is interested in joining. The service must also comply with the join
 * protocol to register with the desired lookup services. 
 * <p>
 * In order for a service to maintain its residency in the lookup services
 * it has joined, the service must provide for the coordination, systematic
 * renewal, and overall management of all leases on that residency. In
 * addition to handling all discovery and join duties, as well as managing
 * all leases on lookup service residency, the service must also provide
 * for the coordination and management of any attribute sets with which
 * it may have registered with the lookup services in which it resides.
 * <p>
 * This class performs all of the functions related to discovery, joining,
 * service lease renewal, and attribute management which is required of a
 * well-behaved service. Each of these activities is intimately involved
 * with the maintenance of a service's residency in one or more lookup
 * services (the service's join state), thus the name <code>JoinManager</code>.
 * <p>
 * This class should be employed by services, not clients. The use of this
 * class in a wide variety of services can help minimize the work resulting
 * from having to repeatedly implement this required functionality in each
 * service. Note that this class is not remote. Services that wish to use
 * this class will create an instance of this class in the service's address
 * space to manage the entity's join state locally.
 *
 * @org.apache.river.impl <!-- Implementation Specifics -->
 *
 * The following implementation-specific items are discussed below:
 * <ul><li> <a href="#jmConfigEntries">Configuring JoinManager</a>
 *     <li> <a href="#jmLogging">Logging</a>
 * </ul>
 *
 * <a name="jmConfigEntries">
 * <p>
 * <b><font size="+1">Configuring JoinManager</font></b>
 * <p>
 * </a>
 *
 * This implementation of <code>JoinManager</code> supports the following
 * configuration entries; where each configuration entry name is associated
 * with the component name <code>net.jini.lookup.JoinManager</code>. Note
 * that the configuration entries specified here are specific to this
 * implementation of <code>JoinManager</code>. Unless otherwise stated, each
 * entry is retrieved from the configuration only once per instance of
 * this utility, where each such retrieval is performed in the constructor.
 * 
 * <a name="discoveryManager">
 * <table summary="Describes the discoveryManager configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1">
 *     <code>discoveryManager</code></font>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Type: <td> {@link net.jini.discovery.DiscoveryManagement}
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Default: <td> <code> new 
 *    {@link net.jini.discovery.LookupDiscoveryManager#LookupDiscoveryManager(
 *       java.lang.String[],
 *       net.jini.core.discovery.LookupLocator[],
 *       net.jini.discovery.DiscoveryListener,
 *       net.jini.config.Configuration) LookupDiscoveryManager}(
 *                       new java.lang.String[] {""},
 *                       new {@link net.jini.core.discovery.LookupLocator}[0],
 *                       null, config)</code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Description:
 *       <td> The object used to manage the discovery processing
 *            performed by this utility. This entry will be retrieved
 *            from the configuration only if no discovery manager is
 *            specified in the constructor. Note that this object should
 *            not be shared with other components in the application that
 *            employs this utility.
 * </table>
 * </a>
 * <a name="leaseManager">
 * <table summary="Describes the leaseManager configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1">
 *     <code>leaseManager</code></font>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Type: <td> {@link net.jini.lease.LeaseRenewalManager}
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Default: <td> <code> new 
 *       {@link net.jini.lease.LeaseRenewalManager#LeaseRenewalManager(
 *         net.jini.config.Configuration) LeaseRenewalManager}(config)</code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Description:
 *       <td> The object used to manage each service lease returned
 *            to this utility when the service is registered with the
 *            the various discovered lookup services. This entry will
 *            be retrieved from the configuration only if no lease 
 *            renewal manager is specified in the constructor.
 * </table>
 * </a>
 * <a name="maxLeaseDuration">
 * <table summary="Describes the maxLeaseDuration
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1">
 *     <code>maxLeaseDuration</code></font>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Type: <td> <code>long</code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Default: <td> <code>Lease.FOREVER</code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Description:
 *       <td> The maximum lease duration (in milliseconds) that is requested
 *            from each discovered lookup service on behalf of the service;
 *            both when the lease is initially requested, as well as when 
 *            renewal of that lease is requested. Note that as this value is
 *            made smaller, renewal requests will be made more frequently
 *            while the service is up, and lease expiration will occur sooner
 *            when the service goes down.
 * </table>
 * </a>
 * <a name="registrarPreparer">
 * <table summary="Describes the registrarPreparer configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1">
 *     <code>registrarPreparer</code></font>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Type: <td> {@link net.jini.security.ProxyPreparer}
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Default: <td> <code>new {@link net.jini.security.BasicProxyPreparer}()
 *                     </code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *   Description:
 *     <td> Preparer for the proxies to the lookup services that are
 *          discovered and used by this utility. 
 *          <p>
 *          The following methods of the proxy returned by this preparer are
 *          invoked by this utility:
 *       <ul>
 *         <li>{@link net.jini.core.lookup.ServiceRegistrar#register register}
 *       </ul>
 * </table>
 * </a>
 * <a name="registrationPreparer">
 * <table summary="Describes the registrationPreparer configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1">
 *     <code>registrationPreparer</code></font>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Type: <td> {@link net.jini.security.ProxyPreparer}
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Default: <td> <code>new {@link net.jini.security.BasicProxyPreparer}()
 *                     </code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *   Description:
 *     <td> Preparer for the proxies to the registrations returned to
 *          this utility upon registering the service with each discovered
 *          lookup service.
 *          <p>
 *          The following methods of the proxy returned by this preparer are
 *          invoked by this utility:
 *       <ul>
 *         <li>{@link net.jini.core.lookup.ServiceRegistration#getServiceID
 *                                                           getServiceID}
 *         <li>{@link net.jini.core.lookup.ServiceRegistration#getLease
 *                                                           getLease}
 *         <li>{@link net.jini.core.lookup.ServiceRegistration#addAttributes
 *                                                           addAttributes}
 *         <li>{@link net.jini.core.lookup.ServiceRegistration#modifyAttributes
 *                                                           modifyAttributes}
 *         <li>{@link net.jini.core.lookup.ServiceRegistration#setAttributes
 *                                                           setAttributes}
 *       </ul>
 * </table>
 * </a>
 * <a name="serviceLeasePreparer">
 * <table summary="Describes the serviceLeasePreparer configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1">
 *     <code>serviceLeasePreparer</code></font>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Type: <td> {@link net.jini.security.ProxyPreparer}
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Default: <td> <code>new {@link net.jini.security.BasicProxyPreparer}()
 *                     </code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *   Description:
 *     <td> Preparer for the leases returned to this utility through
 *          the registrations with each discovered lookup service with
 *          which this utility has registered the service.
 *          <p>
 *          Currently, none of the methods on the service lease returned
 *          by this preparer are invoked by this implementation of the utility.
 * </table>
 * </a>
 * <a name="executorService">
 * <table summary="Describes the executorService configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1">
 *     <code>executorService</code></font>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Type: <td> {@link java.util.concurrent/ExecutorService ExecutorService}
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Default: <td> <code>new 
 *             {@link java.util.concurrent/ThreadPoolExecutor ThreadPoolExecutor}(
 *                   15,
 *                   15,
 *                   15,
 *                   TimeUnit.SECONDS,
 *                   new {@link java.util.concurrent/LinkedBlockingQueue LinkedBlockingQueue}(),
 *                   new {@link org.apache.river.impl.thread.NamedThreadFactory NamedThreadFactory}("JoinManager executor thread", false))</code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Description:
 *       <td> The object that pools and manages the various threads
 *            executed by this utility. This object
 *            should not be shared with other components in the
 *            application that employs this utility.
 * </table>
 * </a>
 * <a name="wakeupManager">
 * <table summary="Describes the wakeupManager configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1">
 *     <code>wakeupManager</code></font>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Type: <td> {@link org.apache.river.thread.WakeupManager}
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Default: <td> <code>new 
 *     {@link org.apache.river.thread.WakeupManager#WakeupManager(
 *          org.apache.river.thread.WakeupManager.ThreadDesc)
 *     WakeupManager}(new 
 *     {@link org.apache.river.thread.WakeupManager.ThreadDesc}(null,true))</code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Description:
 *       <td> Object that pools and manages the various tasks that are
 *            initially executed by the object corresponding to the
 *            <a href="#executorService"><code>executorService</code></a> entry
 *            of this component, but which fail during that initial execution.
 *            This object schedules the re-execution of such a failed task -
 *            in the <a href="#executorService"><code>executorService</code></a>
 *            object - at various times in the future, until either the
 *            task succeeds or the task has been executed the maximum
 *            number of allowable times, corresponding to the 
 *            <a href="#wakeupRetries"><code>wakeupRetries</code></a>
 *            entry of this component. This object should not be shared
 *            with other components in the application that employs this
 *            utility.
 * </table>
 * </a>
 * <a name="wakeupRetries">
 * <table summary="Describes the wakeupRetries
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1">
 *     <code>wakeupRetries</code></font>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Type: <td> <code>int</code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Default: <td> <code>6</code>
 * 
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Description:
 *       <td> The maximum number of times a failed task is allowed to
 *            be executed by the object corresponding to the 
 *            <a href="#wakeupManager"><code>wakeupManager</code></a>
 *            entry of this component.
 * </table>
 * </a>
 * <a name="jmLogging">
 * <p>
 * <b><font size="+1">Logging</font></b>
 * <p>
 * </a>
 *
 * This implementation of <code>JoinManager</code> uses the
 * {@link Logger} named <code>net.jini.lookup.JoinManager</code>
 * to log information at the following logging levels: <p>
 *
 * <table border="1" cellpadding="5"
 *         summary="Describes the information logged by JoinManager,
 *                 and the levels at which that information is logged">
 *
 * <caption halign="center" valign="top">
 *   <b><code>net.jini.lookup.JoinManager</code></b>
 * </caption>
 *
 * <tr> <th scope="col"> Level</th>
 *      <th scope="col"> Description</th>
 * </tr>
 *
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>when a task is stopped because of a definite exception</td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>
 *     when a task is stopped because it has exceeded the maximum number of
 *     times the task is allowed to be tried/re-tried
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>when any exception occurs while attempting to prepare a proxy</td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#FINER FINER}</td>
 *   <td>
 *     when any exception (other than the more serious exceptions logged
 *     at higher levels) occurs in a task
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>
 *     when an <code>IllegalStateException</code> occurs upon attempting to
 *     discard a lookup service
 *   </td>
 * </tr>
 *  <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>whenever any task is started</td>
 * </tr>
 * 
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>whenever any task completes successfully</td>
 * </tr>
 *
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>whenever a proxy is prepared</td>
 * </tr>
 * </table>
 * <p>
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.discovery.DiscoveryManagement
 * @see net.jini.lease.LeaseRenewalManager
 * @see java.util.logging.Level
 * @see java.util.logging.Logger
 */
public class JoinManager {
    
    /** Implementation Note:
     *
     *  This class executes a number of tasks asynchronously. Each task is
     *  initially queued in a <code>org.apache.river.thread.TaskManager</code>
     *  instance, which executes each task in a separate thread. In this
     *  way, an upper bound is placed on the number of threads executing
     *  concurrently at any one time; that is, the number of concurrent
     *  threads is "throttled".
     *
     *  In addition to throttling the number of concurrent threads, the
     *  use of a task manager, in conjunction with the task configuration,
     *  provides a level of resiliency with respect to down/unresponsive
     *  lookup services.
     *
     *  Recall from the specification that the primary function of a join
     *  manager is to maintain and update the state (registrations,
     *  attribute augmentations/replacements/changes, etc.) of the join
     *  manager's single associated service with all of the desired lookup
     *  services. Because updating a service's state in a lookup service
     *  involves remote communication, such update operations are performed
     *  asynchronously, in separate tasks. If those operations are not
     *  performed in separate tasks, then a communication problem with
     *  one of the lookup services while performing one operation could
     *  prevent (or significantly slow) the execution of all future
     *  state update operations; causing all processing in the join manager
     *  to degrade or even hang indefinitely.
     *
     *  Although performing each update operation in a separate task thread
     *  prevents a down/unresponsive lookup service from blocking other
     *  processing in the join manager, it does not guarantee consistency
     *  of the service's state in each "up" lookup service, with the state
     *  expected by the client that requested the state changes.
     *
     *  For example, suppose the client first requests that the service's
     *  attributes be replaced with a new set, and then immediately after
     *  the attribute replacement request, the client requests that the
     *  service's attributes be augmented by yet another set of attributes.
     *  Unless control is imposed on the order of execution of the tasks
     *  that perform the attribute replacement and augmentation, there is
     *  a possibility that the service's state in one or more of the lookup
     *  service's will have experienced the attribute augmentation prior to
     *  the attribute replacement, rather than the replacement followed by
     *  the augmentation, as the client would have expected. Thus, not only
     *  can the service's state in one or more of the lookup services be
     *  inconsistent with what the client expects, but it can also be
     *  inconsistent with one or more of the other lookup services with
     *  which the service is registered.
     *  
     *  To prevent the possibility of inconsistencies such as those just
     *  described, the state update operations are grouped according to
     *  the particular lookup service with which they are associated. Each
     *  such grouping is implemented as a task, executed by the task
     *  manager employed by this implementation of <code>JoinManager</code>,
     *  containing a queue of sub-tasks (the actual state update operations)
     *  that are executed by the main task in the same order in which they
     *  were requested by the client.
     *  
     *  Each main task executed by this join manager's task manager is a
     *  sub-class of the abstract class <code>RetryTask</code>, defined in
     *  the package <code>org.apache.river.thread</code>, which implements
     *  the <code>org.apache.river.thread.TaskManager.Task</code> interface.
     *  The association of each such task with a particular lookup service,
     *  and with a unique sequence number is reflected in the fields of this
     *  class. The unique sequence number associated with each main task
     *  is exploited by the <code>runAfter</code> method defined in 
     *  <code>RetryTask</code> to guarantee that the service managed by
     *  this <code>JoinManager</code> is assigned only one service ID.
     *   
     *  Because of the relationship between the main tasks and 
     *  <code>RetryTask</code>, those concrete main tasks created in this
     *  implementation of <code>JoinManager</code> can be processed by either
     *  a task manager or a wakeup manager; and this fact is exploited to
     *  provide a failure recovery mechanism that can tolerate task "storms".
     *  Task storms can occur in systems that contain numerous services,
     *  where each service employs its own join manager to handle its join
     *  state with the desired lookup services. A "registration request storm"
     *  is an example of one type of task storm.
     *
     *  A registration request storm can occur when a system is initially
     *  started, or when it recovers from a system-wide failure, when each
     *  service's join manager attempts to register with each discovered
     *  lookup service; resulting in a flood - or "storm" - of registration
     *  requests at each lookup service. Because the accept queue under some
     *  operating systems is - by default - very small, it is possible that,
     *  in the face of such a large number of concurrent requests, one or
     *  more of the lookup services will not be able to process the requests
     *  as fast as they are arriving in the queue. When this happens, the
     *  associated task in the join manager typically encounters a failure
     *  such as a <code>java.rmi.ConnectException</code>.
     *
     *  To deal with situations such as that described above, this
     *  <code>JoinManager</code> implementation employs a wakeup manager
     *  in addition to a task manager. When a main task is created, it is
     *  initially executed by a task manager. If a failure is encountered,
     *  and if the nature of that failure indicates that retrying the task
     *  will <i>definitely</i> fail again, then the associated lookup service
     *  is discarded so that the task can be retried when the lookup service
     *  is rediscovered. But if it is not clear that retrying the task will
     *  again fail, (that is, the failure is <i>indefinite</i>), then the
     *  task is queued for re-execution at a later time in a wakeup manager.
     *  This process is repeated - employing a "backoff" strategy - until one
     *  of the following events occurs:
     *
     *  <p><ul>
     *     <li> the task succeeds 
     *     <li> a definite failure is encountered
     *     <li> the task has been executed the maximum number times allowed 
     *  </ul><p>
     *  
     *  The maximum number of times a task is allowed to be retried before
     *  giving up and discarding the associated lookup service can be changed
     *  from its default value by setting the <code>wakeupRetries</code>
     *  configuration entry for this component.
     *
     *  @see org.apache.river.thread.TaskManager
     *  @see org.apache.river.thread.WakeupManager
     *  @see org.apache.river.thread.TaskManager.Task
     *  @see org.apache.river.thread.RetryTask
     *  @see org.apache.river.constants.ThrowableConstants
     */

    /** Abstract base class from which all of the task classes are derived. */
    private class ProxyRegTask extends RetryTask implements Comparable<ProxyRegTask> {
        private final long[] sleepTime = { 5*1000, 10*1000, 15*1000,
                                          20*1000, 25*1000, 30*1000 };
        // volatile fields only mutated while synchronized on proxyReg.taskList
        private volatile int tryIndx  = 0;
        private volatile int nRetries = 0;
        private final ProxyReg proxyReg;
        private final int seqN;

        /** Basic constructor; simply stores the input parameters */
        ProxyRegTask(ProxyReg proxyReg, int seqN) {
            super(JoinManager.this.executor,JoinManager.this.wakeupMgr);
            this.proxyReg = proxyReg;
            this.seqN = seqN;
        }//end constructor

        /** Executes the current instance of this task once, queuing it
         *  for retry at a later time and returning <code>false</code>
         *  upon failure. This method attempts to execute all of the tasks
         *  associated with the lookup service referenced in this task's 
         *  <code>proxyReg</code> field. Order of execution is important,
         *  and this method executes the tasks in the <code>proxyReg</code>'s
         *  <code>taskList</code> in a FIFO order.
         *
         *  Note that tasks may be added to the <code>taskList</code> of
         *  the <code>proxyReg</code> during the execution of this method.
         *
         *  Upon successfully executing all of the tasks in the 
         *  <code>taskList</code>, this method returns <code>true</code>
         *  and the current instance of this task is not executed again.
         *  For each unsuccessful execution of a task in the 
         *  <code>taskList</code>, this method returns <code>false</code>,
         *  which causes the task to be scheduled by the
         *  <code>WakeupManager</code> to be executed again at a later
         *  time, as indicated by the value returned by <code>retryTime</code>.
         */
        @Override
        public boolean tryOnce() {
            while(true) {
                JoinTask t;
                synchronized(proxyReg.taskList) {
                    if(proxyReg.taskList.isEmpty()) {
                        proxyReg.proxyRegTask = null;
                        return true;
                    }//endif
                    t = proxyReg.taskList.get(0);
                }//end sync
                try {
                    t.run();
                    synchronized(proxyReg.taskList) {
                        if( !proxyReg.taskList.isEmpty() ) {
                            JoinTask task = proxyReg.taskList.get(0);
                            if (task == t) proxyReg.taskList.remove(0);
                        }//endif
                        /* reset the retry info for the next task in the list */
                        tryIndx  = 0;
                        nRetries = 0;
                    }//end sync
                    
                } catch (Exception e) {
                    return stopTrying(e);
                }
            }//end loop
	}//end tryOnce

        /** Returns the next absolute time (in milliseconds) at which another
         *  execution of this task should be made (after the previous
         *  attempt has failed).
         */
        @Override
        public long retryTime() {
	    long nextTryTime = System.currentTimeMillis() + sleepTime[tryIndx];
            synchronized (proxyReg.taskList){
                if(tryIndx < sleepTime.length-1)  tryIndx++;//don't go past end
                    nRetries++;
            }
            return nextTryTime;
        }//end retryTime

        /** Returns true if the current instance of this task must be run
         *  after any task already in the task manager queue.
         *  
         *  It is important that when the join manager is constructed with
         *  a <code>null</code> service ID (where it is desired that 
         *  a unique service ID be generated on the service's behalf),
         *  that only the first task in the task manager's queue be run; no
         *  other tasks in the queue should be run while that first task
         *  is running. This is because the first sub-task executed by
         *  the first main task in the task manager's queue will always be
         *  a <code>RegisterTask</code>. And during the execution of that
         *  first sub-task (if the service ID has not yet been set), the
         *  service ID generated by the associated lookup service is retrieved
         *  and stored for use in all future lookup service registration
         *  tasks, Once the service ID is set by that first registration
         *  sub-task, all future main tasks (and their associated registration
         *  sub-tasks) can be run in parallel; each using the same service ID.
         *  If this is not done, then the registration sub-tasks would be
         *  run in parallel, each assigning a different ID to the service.
         *
         *  This method guarantees that until the service's ID is set,
         *  only one registration sub-task is run; that is, one task
         *  doesn't start until the currently running task has completed,
         *  and a non-<code>null</code> service ID is assigned to the service.
         *  
         *  Executing the main tasks sequentially until the service ID is 
         *  retrieved and stored must also be guaranteed because the currently
         *  running registration task may fail to register the service
         *  (because of a <code>RemoteException</code>), and thus may fail
         *  to obtain an ID for the service. This method guarantees then
         *  that each main task (and thus, each registration sub-task) will
         *  run in sequence until one of those tasks completes successfully;
         *  and from that point on, this method guarantees that all other
         *  queued tasks will run in parallel.
         *  
         *  @param tasks the tasks with which to compare the current task
         *  @param size  elements with index less than size are considered
         */
        public boolean dependsOn(ProxyRegTask t) {
            return seqN > t.getSeqN();
        }
        
        /* If the service's ID has already been set, then it's okay
         * to run all ProxyRegTask's in parallel, otherwise, the
         * ProxyRegTask with the lowest sequence number should be run.
         */
        public boolean hasDeps(){
            return serviceItem.serviceID == null;
        }

        /** Accessor method that returns the instance of <code>ProxyReg</code>
         *  (the lookup service) associated with the task represented by
         *  the current instance of this class.
         */
        public ProxyReg getProxyReg() {
            return proxyReg;
        }//end getProxy

        /** Accessor method that returns the unique sequence number associated
         *  with the task represented by the current instance of this class.
         */
        public int getSeqN() {
            return seqN;
        }//end getSeqN

        /** Convenience method called by the child tasks when they encounter
         *  an exception. If the given exception indicates that retrying the
         *  task would definitely fail, or if the maximum allowable number
         *  of retries of the task has been exceeded, then this method will
         *  do the following:
         *    - remove all pending tasks that are to be run after this task
         *    - cancel this task
         *    - discard the look service associated with this task
         *    - return <code>true</code> (which stops the wakeup manager
         *      from retrying this task
         *  otherwise, this method returns <code>false</code>, which indicates
         *  that the wakeup manager should not stop trying to successfully
         *  execute the task.
         */
        protected boolean stopTrying(Exception e) {
            int exCat = ThrowableConstants.retryable(e);
            if(    (exCat != ThrowableConstants.INDEFINITE)
                || (nRetries >= maxNRetries) )
            {
                removeTasks(proxyReg);//cancel and clear all related tasks
                proxyReg.fail(e);
                return true;//don't try again
            }//endif
            logger.log(Level.FINER,
                       "JoinManager - failure, will retry later", e);
            return false;//try this task again later
        }//end stopTrying

        @Override
        public int compareTo(ProxyRegTask o) {
            if (seqN < o.seqN) return -1;
            if (seqN > o.seqN) return 1;
            return 0;
        }
    }//end class ProxyRegTask
    
        private static final class ProxyRegTaskQueue implements FutureObserver {
        // CacheTasks pending completion.
        private final ConcurrentLinkedQueue<ProxyRegTask> pending;
        private final ExecutorService executor;
        
        private ProxyRegTaskQueue(ExecutorService e){
            this.pending = new ConcurrentLinkedQueue<ProxyRegTask>();
            executor = e;
        }
        
        private Future submit(ProxyRegTask t){
            pending.offer(t);
            t.addObserver(this);
            if (t.hasDeps()) {
                List<ObservableFuture> deps = new LinkedList<ObservableFuture>();
                Iterator<ProxyRegTask> it = pending.iterator();
                while (it.hasNext()){
                    ProxyRegTask c = it.next();
                    if (t.dependsOn(c)) {
                        deps.add(c);
                    }
                }
                if (deps.isEmpty()){
                    executor.submit(t);
                } else {
                    DependencyLinker linker = new DependencyLinker(executor, deps, t);
                    linker.register();
                }
            } else {
                executor.submit(t);
            }
            return t;
        }

        @Override
        public void futureCompleted(Future e) {
            pending.remove(e);
        }
    }

    /** Abstract base class from which all the sub-task classes are derived. */
    private static abstract class JoinTask {

        /** Data structure referencing the task's associated lookup service */
        protected final ProxyReg proxyReg;

        /** Basic constructor; simply stores the input parameters */
        JoinTask(ProxyReg proxyReg) {
            this.proxyReg = proxyReg;
        }//end constructor

        /** Method executed in a separate thread created by the task manager */
	public abstract void run() throws Exception;

    }//end class JoinTask

    /** Task that asynchronously registers the service associated with this
     *  join manager with the lookup service referenced by the current
     *  instance of this class.
     */
    private static class RegisterTask extends JoinTask {
        /** Attributes with which to register the service. These attributes
         *  must not change during the registration process performed in
         *  this this task.
         */
        final Entry[] regAttrs;

        /** Constructor that associates this task with the lookup service
         *  referenced in the given <code>ProxyReg</code> parameter.
         *
         *  @param proxyReg  data structure that references the lookup service
         *                   with which the service is to be registered
         *  @param regAttrs  array of Entry objects whose elements are the
         *                   attributes with which to register the service.
         *                   The caller of this constructor should take steps
         *                   to guarantee that the contents of this parameter
         *                   do not change during the registration process
         *                   performed in this task.
         */
        RegisterTask(ProxyReg proxyReg, Entry[] regAttrs) {
            super(proxyReg);
            this.regAttrs = regAttrs;
	}//end constructor

        /** Attempts to register this join manager's service with the lookup
         *  service referenced in this task's proxyReg field.
         */
        @Override
        public void run() throws Exception {
            logger.finest("JoinManager - RegisterTask started");
            proxyReg.register(regAttrs);
            logger.finest("JoinManager - RegisterTask completed");
        }//end run

    }//end class RegisterTask

    /** Task that asynchronously re-registers the service associated with this
     *  join manager with the lookup service referenced by the current
     *  instance of this class.
     *  
     *  This task is typically executed when the service's lease with the
     *  referenced lookup service has expired.
     */
    private class LeaseExpireNotifyTask extends JoinTask {
        /** Attributes with which to re-register the service. These attributes
         *  must not change during the registration process performed in
         *  this this task.
         */
        Entry[] regAttrs;
        /** Constructor that associates this task with the lookup service
         *  referenced in the given <code>ProxyReg</code> parameter.
         *
         *  @param proxyReg  data structure that references the lookup service
         *                   with which the service is to be re-registered
         *  @param regAttrs  array of Entry objects whose elements are the
         *                   attributes with which to re-register the service.
         *                   The caller of this constructor should take steps
         *                   to guarantee that the contents of this parameter
         *                   do not change during the registration process
         *                   performed in this task.
         */
        LeaseExpireNotifyTask(ProxyReg proxyReg, Entry[] regAttrs) {
            super(proxyReg);
            this.regAttrs = regAttrs;
	}//end constructor

        /** Attempts to re-register this join manager's service with the
         *  lookup service referenced by the current instance of this class.
         */
        @Override
        public void run() throws Exception {
            logger.finest("JoinManager - LeaseExpireNotifyTask started");
            if(joinSet.contains(proxyReg)) proxyReg.register(regAttrs);
            logger.finest("JoinManager - LeaseExpireNotifyTask completed");
	}//end run

    }//end class LeaseExpireNotifyTask

    /** Task that asynchronously requests the cancellation of the lease
     *  on the <code>ServiceRegistration</code> referenced by the given
     *  <code>ProxyReg</code> object. The lease to be cancelled was granted
     *  by the lookup service whose proxy is also referenced by the given
     *  <code>ProxyReg</code> object. This task is executed whenever that
     *  lookup service is discarded.
     *
     *  Note that although this task is executed upon receipt of any type
     *  of lookup service discard event, there is one type of discard
     *  that this task is actually intended to address: the so-called
     *  "lost-interest" discard. A discard corresponding to a loss of
     *  interest is an indication that the discarded lookup service is
     *  still up and available, but the discovery manager employed by
     *  this join manager (not the join manager or the service itself)
     *  discards the lookup service because the service is no longer
     *  interested in being registered with that lookup service. This
     *  loss of interest is caused by a change in either the lookup
     *  service's member groups or the service's groups-to-discover.
     *  Such a change in groups would typically be made administratively,
     *  through either the lookup service's administrative interface or
     *  through the service's administrative interface (or both). 
     *
     *  When the lookup service is discarded because of a loss of
     *  interest, there is a time period in which the service is still
     *  registered with the lookup service, even though the service no
     *  longer wishes to be registered with that lookup service. To 
     *  address this, the lease is cancelled so that the service is
     *  removed from the lookup service in a much more timely fashion
     *  than simply allowing the lease to expire.
     *
     *  As stated above, this task is also executed upon receipt of
     *  the other types of discard event. But because those other
     *  discard types occur when the lookup service is no longer
     *  available, the lease cancellation that is attempted here has
     *  no significant effect.
     *
     *  @see DiscMgrListener#discarded
     */
    private class DiscardProxyTask extends JoinTask {
        /** Constructor that provides this task with access (through the given
         *  <code>ProxyReg</code> parameter) to the lease to be cancelled.
         *
         *  @param proxyReg  data structure that references the 
         *                   <code>ServiceRegistration</code> and associated
         *                   lease whose cancellation is to be requested
         */
        DiscardProxyTask(ProxyReg proxyReg) {
            super(proxyReg);
	}//end constructor

        /** Requests the cancellation of the lease on the 
         *  <code>ServiceRegistration</code> that is referenced in the
         *  <code>proxyReg</code> data structure.
         */
        @Override
        public void run() {
            logger.finest("JoinManager --> DiscardProxyTask started");
            Lease svcLease = proxyReg != null ? proxyReg.serviceLease : null;
            if( svcLease != null ) {
                try {
                    svcLease.cancel();
                } catch (Exception e) { /*ignore*/ }
            }//endif
            logger.finest("JoinManager - DiscardProxyTask completed");
	}//end run

    }//end class DiscardProxyTask

    /** Task that asynchronously augments the attributes associated with this
     *  join manager's service in the lookup service referenced by the
     *  current instance of this class.
     */
    private static class AddAttributesTask extends JoinTask {
        /** The new attribute values with which the service's current
         *  attributes will be augmented, replaced, or changed.
         */
	protected final Entry[] attrSets;

        /** Constructor that associates this task with the lookup service
         *  referenced in the given <code>ProxyReg</code> parameter.
         *
         *  @param proxyReg  data structure that references the lookup service
         *                   in which the service's attributes should be
         *                   augmented 
         *  @param newAttrs  the attributes with which to augment the 
         *                   service's current set of attributes 
         */
        AddAttributesTask(ProxyReg proxyReg, Entry[] newAttrs) {
            super(proxyReg);
	    this.attrSets = (Entry[])newAttrs.clone();
	}//end constructor

        /** Performs the actual attribute augmentation, replacement, or
         *  modification work. This method is typically overridden by
         *  sub-classes of this class.
         */
        protected void doAttributes(ProxyReg proxyReg) throws Exception {
            logger.finest("JoinManager - AddAttributesTask started");
	    proxyReg.addAttributes(attrSets);
            logger.finest("JoinManager - AddAttributesTask completed");
        }//end AddAttributesTask.doAttributes

        /** Attempts to either augment, replace, or modify the attributes
         *  of this join manager's service in the lookup service referenced
         *  by the current instance of this class. Which action is taken --
         *  augmentation, replacement, or modification -- is dependent on the
         *  definition of the <code>doAttributes/code> method.
         */
        @Override
        public void run() throws Exception {
            doAttributes(proxyReg);
	}//end run

    }//end class AddAttributesTask

    /** Task that asynchronously replaces the attributes associated with this
     *  join manager's service in the lookup service referenced by the
     *  current instance of this class.
     */
    private static final class SetAttributesTask extends AddAttributesTask {
        /** Constructor that associates this task with the lookup service
         *  referenced in the given <code>ProxyReg</code> parameter.
         *
         *  @param proxyReg         data structure that references the lookup
         *                          service in which the service's attributes
         *                          should be replaced 
         *  @param replacementAttrs the attributes with which to replace the 
         *                          service's current set of attributes 
         */
        SetAttributesTask(ProxyReg proxyReg, Entry[] replacementAttrs){
            super(proxyReg, replacementAttrs);
	}//end constructor

        /** Performs the actual attribute replacement work. */
        @Override
        protected void doAttributes(ProxyReg proxyReg) throws Exception {
            logger.finest("JoinManager - SetAttributesTask started");
	    proxyReg.setAttributes(attrSets);
            logger.finest("JoinManager - SetAttributesTask completed");
	}//end SetAttributesTask.doAttributes

    }//end class SetAttributesTask

    /** Task that asynchronously modifies the attributes associated with this
     *  join manager's service in the lookup service referenced by the
     *  current instance of this class.
     */
    private static final class ModifyAttributesTask extends AddAttributesTask {
	private final Entry[] attrSetTemplates;
        /** Constructor that associates this task with the lookup service
         *  referenced in the given <code>ProxyReg</code> parameter.
         *
         *  @param proxyReg         data structure that references the lookup
         *                          service in which the service's attributes
         *                          should be changed 
         *  @param attrSetTemplates the attribute templates that are used to
         *                          select (through attribute matching) the
         *                          attributes to change in the service's
         *                          current set of attributes 
         *  @param attrChanges      the attributes containing the changes to
         *                          make to the attributes in the service's
         *                          current set that are selected through
         *                          attribute matching with the
         *                          attrSetTemplates parameter
         */
        ModifyAttributesTask( ProxyReg proxyReg,
                              Entry[] attrSetTemplates,
                              Entry[] attrChanges )
        {
            super(proxyReg, attrChanges);
	    this.attrSetTemplates = (Entry[])attrSetTemplates.clone();
	}//end constructor

        /** Performs the actual attribute modification work. */
        @Override
        protected void doAttributes(ProxyReg proxyReg) throws Exception {
            logger.finest("JoinManager - ModifyAttributesTask started");
	    proxyReg.modifyAttributes(attrSetTemplates, attrSets);
            logger.finest("JoinManager - ModifyAttributesTask completed");
	}//end ModifyAttributesTask.doAttributes

    }//end class ModifyAttributesTask

    /** Wrapper class in which each instance corresponds to a lookup
     *  service to discover, and with which this join manager's service
     *  should be registered.
     */
    private class ProxyReg implements FutureObserver{

        /** Class that is registered as a listener with this join manager's
         *  lease renewal manager. That lease renewal manager manages the
         *  lease granted to this join manager's associated service by the
         *  lookup service referenced in the proxy object associated with
         *  this class (<code>ProxyReg</code>).
         *
         *  If the lease expires in the lookup service before the lease 
         *  renewal manager requests renewal of the lease, then upon sending
         *  that renewal request, the lease renewal manager will receive an
         *  <code>UnknownLeaseException</code> from the lookup service.
         *  As a result, the lease renewal manager removes the expired lease
         *  so that no further renewal attempts are made for that lease, 
         *  and then sends to this listener a <code>LeaseRenewalEvent</code>,
         *  containing an <code>UnknownLeaseException</code>.
         *
         *  Alternatively, suppose that at the time the lease renewal manager
         *  is about to request the renewal of the lease, the lease renewal
         *  manager determines that the expiration time of the lease has
         *  actually passed. In this case, since there is no reason to
         *  send the renewal request, the lease renewal manager instead
         *  removes the expired lease (again, so that no further renewal
         *  attempts are made for that lease), and then sends a
         *  <code>LeaseRenewalEvent</code> to this listener to indicate
         *  that the lease has expired. The difference between this case,
         *  and the case described previously is that in this case the
         *  <code>LeaseRenewalEvent</code> contains no exception (that is,
         *  <code>LeaseRenewalEvent.getException</code> returns 
         *  <code>null</code>).
         *
         *  Both situations described above indicate that the lease
         *  referenced by the event received by this listener has expired.
         *  Thus, the normal course of action should be to attempt to
         *  re-register the service with the lookup service that originally
         *  granted the lease that has expired.
         *
         *  Prior to re-registering the service, the joinSet is examined to
         *  determine if it contains a ProxyReg object that is equivalent to
         *  the ProxyReg object referencing the current instance of this
         *  listener class. That is, using <code>equals</code>, it is
         *  determined whether the joinSet contains either ProxyReg.this,
         *  or a new instance of ProxyReg that is equal to ProxyReg.this.
         *  If the joinSet does not contain such a ProxyReg, then the lookup
         *  service must have been discarded and not yet re-discovered; in
         *  which case, there is no need to attempt to re-register with that
         *  lookup service, since it is unavailable.
         *
         *  If it is determined that the joinSet does contain either
         *  ProxyReg.this or a new ProxyReg equivalent to ProxyReg.this,
         *  then re-registration should be attempted, but only if the lease
         *  associated with the ProxyReg in the joinSet is equal to the
         *  expired lease referenced in the event received by this listener.
         *  Equality of those leases is an indication that the lease on the
         *  service's current registration has expired; thus, an attempt to
         *  re-register the service should be made.
         *
         *  If the lease associated with the ProxyReg from the joinSet
         *  does not equal the expired lease from the event, then
         *  re-registration should not be attempted. This is because
         *  the inequality of the leases is an indication that the lease
         *  renewal event received by this listener was the result of an
         *  <code>UnknownLeaseException</code> that occurs when the
         *  ProxyReg in the joinSet is a new ProxyReg, different from
         *  ProxyReg.this, and the lease renewal manager requests the
         *  renewal of the (now invalid) lease associated with that old
         *  ProxyReg.this; not the valid lease associated with the new
         *  ProxyReg.
         *
         *  A scenario such as that just described can occur when the
         *  lookup service is discarded, rediscovered, and the service is
         *  re-registered, resulting in a new ProxyReg (with new lease)
         *  being placed in the joinSet, replacing the previous ProxyReg
         *  (ProxyReg.this). But before the old, expired lease is removed
         *  from the lease renewal manager, an attempt to renew the old
         *  lease is made. Such an attempt can occur because the lease
         *  renewal manager may be in the process of requesting the renewal
         *  of that lease (or may have queued such a request) just prior to,
         *  or at the same time as, when the lease removal request is made
         *  during discard processing. When the request is made to renew
         *  the expired lease, an <code>UnknownLeaseException</code> occurs
         *  and a lease renewal event is sent to this listener.
         *
         *  If, upon receiving an event such as that just described, the
         *  service were to be re-registered, the current valid service
         *  registration would be replaced, a new lease would be granted,
         *  and the corresponding ProxyReg currently contained in the joinSet
         *  would be replaced with a new ProxyReg. Additionally, the now
         *  invalid lease corresponding to the ProxyReg that was just
         *  replaced would remain in the lease renewal manager. This means
         *  that an attempt to renew that lease will eventually be made and
         *  will fail, and the cycle just described will repeat indefinitely.
         *
         *  Thus, for the reasons stated above, re-registration is attempted
         *  only if the lease associated with the ProxyReg contained in the
         *  joinSet is equal to the expired lease referenced in the lease
         *  renewal event received by this listener.
         */
	private class DiscLeaseListener implements LeaseListener {
              @Override
  	    public void notify(LeaseRenewalEvent e) {
                Throwable ex = e.getException();
		if ( (ex == null) || (ex instanceof UnknownLeaseException) ) {
                    removeTasks(ProxyReg.this);
                    Lease expiredLease = e.getLease();
                    // Maybe re-register
                    Iterator<ProxyReg> it = joinSet.iterator();
                    while (it.hasNext()){
                        ProxyReg next = it.next();
                        if (!ProxyReg.this.equals(next)) continue;
                        if(expiredLease.equals(next.serviceLease)) {
                            // Okay to re-register
                            addTask(
                                new LeaseExpireNotifyTask (ProxyReg.this,
                                             (Entry[])lookupAttr.clone()));
                        }//endif
                    }
		} else {
		    fail(ex);
                }//endif
	    }//end notify
	}//end class DiscLeaseListener

        /** The <code>ProxyRegTask</code> that instantiated this
         *  <code>ProxyReg</code>.
         */
        volatile ProxyRegTask proxyRegTask;// writes sync on taskList
        /** The <i>prepared</i> proxy to the lookup service referenced by
         *  this class, and with which this join manager's service will be
         *  registered.
         */
	final ServiceRegistrar proxy;
        /** The <i>prepared</i> registration proxy returned by this class'
         *  associated lookup service when this join manager registers its
         *  associated service.
         * 
         * Writes to reference synchronized on JoinManager.this, but not referent
         * as it has foreign remote methods.
         */
	volatile ServiceRegistration srvcRegistration = null;
        /* The <i>prepared</i> proxy to the lease on the registration of this
         * join manager's service with the this class' associated lookup
         * service.
         */
	volatile Lease serviceLease = null;
        /** The set of sub-tasks that are to be executed in order for the
         *  lookup service associated with the current instance of this class.
         */
        final List<JoinTask> taskList = new ArrayList<JoinTask>();
        /** The instance of <code>DiscLeaseListener</code> that is registered
         *  with the lease renewal manager that handles the lease of this join
         *  manger's service.
         */
        final List<Future> runningTasks = new ArrayList<Future>();
        
	private final DiscLeaseListener dListener = new DiscLeaseListener();

        /** Constructor that associates this class with the lookup service
         *  referenced in the given <code>ProxyReg</code> parameter.
         *
	 *  @param proxy data structure that references the lookup service on
	 *               which the sub-tasks referenced in this class will be
	 *               executed in order
         */
	public ProxyReg(ServiceRegistrar proxy) {
	    if(proxy == null)  throw new IllegalArgumentException
                                                      ("proxy can't be null");
	    this.proxy = proxy;
	}//end constructor	
        
        @Override
        public void futureCompleted(Future e) {
            synchronized (runningTasks){
                runningTasks.remove(e);
            }
        }
        
        public void terminate(){
            synchronized (runningTasks){
                Iterator<Future> it = runningTasks.iterator();
                while (it.hasNext()){
                    it.next().cancel(false);
                }
                runningTasks.clear();
            }
        }

        /** Convenience method that adds new sub-tasks to this class' 
         *  task queue.
         *
         *  @param task the task to add to the task queue
         */
        public void addTask(JoinTask task) {
            if(bTerminated) return;
            Future future = null;
            synchronized(taskList) {
                taskList.add(task);
                if(this.proxyRegTask == null) {
                    this.proxyRegTask = new ProxyRegTask(this,taskSeqN++);
                    this.proxyRegTask.addObserver(this);
                    future = proxyRegTaskQueue.submit(this.proxyRegTask);
                }//endif
            }//end sync(taskList)
            synchronized (runningTasks){
                runningTasks.add(future);
            }
        }//end addTask

        /** Registers the service associated with this join manager with the
         *  the lookup service corresponding to this class. Additionally,
         *  this method retrieves the lease granted by the lookup service
         *  on the service's registration, and passes that lease to the
         *  lease renewal manager. If a <code>ServiceIDListener</code> 
         *  has been registered with this join manager, this method will
         *  send to that listener a notification containing the service's ID.
         */
        public void register(Entry[] srvcAttrs) throws Exception {
            if(proxy == null) throw new RuntimeException("proxy is null");
            /* The lookup service proxy was already prepared at discovery */
            ServiceItem tmpSrvcItem;
            ServiceItem item;
            srvcRegistration = null;
            //accessing serviceItem.serviceID
            item = serviceItem;
            tmpSrvcItem = new ServiceItem(item.serviceID,
                                              item.service,
                                              srvcAttrs);
            /* Retrieve and prepare the proxy to the service registration */
            ServiceRegistration tmpSrvcRegistration 
                                = proxy.register(tmpSrvcItem, renewalDuration);
            try {
                tmpSrvcRegistration = 
                   (ServiceRegistration)registrationPreparer.prepareProxy
                                                       ( tmpSrvcRegistration );
                logger.finest
                          ("JoinManager - ServiceRegistration proxy prepared");
            } catch(Exception e) {
		LogUtil.logThrow(logger, Level.WARNING, ProxyReg.class,
		    	"register", "JoinManager - failure during " +
		    	"preparation of ServiceRegistration proxy: {0}",
		    	new Object[] { tmpSrvcRegistration }, e);
                throw e; //rethrow the exception since proxy may be unusable
            }
            /* Retrieve and prepare the proxy to the service lease */
            Lease svcLease = tmpSrvcRegistration.getLease();
            try {
                this.serviceLease = 
                       (Lease)serviceLeasePreparer.prepareProxy(svcLease);
                logger.finest("JoinManager - service lease proxy prepared");
            } catch(Exception e) {
		LogUtil.logThrow(logger, Level.WARNING, ProxyReg.class,
		    	"register", "JoinManager - failure during " +
		    	"preparation of service lease proxy: {0}",
		    	new Object[] { svcLease }, e);
                throw e; //rethrow the exception since proxy may be unusable
            }
            leaseRenewalMgr.renewUntil(svcLease, Lease.FOREVER,
                                       renewalDuration, dListener);
            ServiceID tmpID = null;
            srvcRegistration = tmpSrvcRegistration;
            ServiceID id = srvcRegistration.getServiceID();
            synchronized (JoinManager.this){
                item = serviceItem;
                if(item.serviceID == null) {
                    serviceItem = new ServiceItem(id, item.service, item.attributeSets);
                    tmpID = id;
                }//endif
            }
            if( (tmpID != null) && (callback != null) )  {
                callback.serviceIDNotify(tmpID);
            }//endif
        }//end ProxyReg.register

        /** With respect to the lookup service referenced in this class
         *  and with which this join manager's service is registered, this
         *  method associates with that service a new set of attributes -- in
         *  addition to that service's current set of attributes.
         */
        public void addAttributes(Entry[] attSet) throws Exception {
            ServiceRegistration sr = srvcRegistration;
            if (sr != null) sr.addAttributes(attSet);
	}//end ProxyReg.addAttributes

        /** With respect to the lookup service referenced in this class
         *  and with which this join manager's service is registered, this
         *  method changes that service's current attributes by selecting
         *  the attributes to change using the given first parameter;
         *  and identifying the desired changes to make through the
         *  contents of the second parameter.
         */
        public void modifyAttributes(Entry[] templ, Entry[] attSet)
                                                             throws Exception
        {
            ServiceRegistration sr = srvcRegistration;
            if (sr != null) sr.modifyAttributes(templ, attSet);
	}//end ProxyReg.modifyAttributes		    

        /** With respect to the lookup service referenced in this class
         *  and with which this join manager's service is registered, this
         *  method replaces that service's current attributes with a new
         *  set of attributes.
         */
        public void setAttributes(Entry[] attSet) throws Exception {
            ServiceRegistration sr = srvcRegistration;
            if (sr != null) sr.setAttributes(attSet);
	}//end ProxyReg.setAttributes

        /** Convenience method that encapsulates appropriate behavior when
         *  failure is encountered related to the current instance of this
         *  class. This method discards the lookup service proxy associated
         *  with this object, and logs the stack trace of the given
         *  <code>Throwable</code> according to the logging levels specified
         *  for this utility.
         *  
         *  Note that if the discovery manager employed by this join manager
         *  has been terminated, then the attempt to discard the lookup 
         *  service proxy will result in an <code>IllegalStateException</code>.
         *  Since this method is called only within the tasks run by
         *  this join manager, and since propagating an
         *  <code>IllegalStateException</code> out into the
         *  <code>ThreadGroup</code> of those tasks is undesirable, this
         *  method will not propagate the <code>IllegalStateException</code>
         *  that occurs as a result of an attempt to discard a lookup
         *  service proxy from the discovery manager employed by this
         *  join manager.
         * 
         * For more information, refer to Bug 4490355.
         */
	public void fail(Throwable e) {
		if(bTerminated) return;
                LogUtil.logThrow(logger, Level.INFO, ProxyReg.class, "fail",
                    "JoinManager - failure for lookup service proxy: {0}",
                    new Object[] { proxy }, e);
                try {
                    discMgr.discard(proxy);
                } catch(IllegalStateException e1) {
                   logger.log(Level.FINEST,
                              "JoinManager - cannot discard lookup, "
                              +"discovery manager already terminated",
                              e1);
                }
	}//end ProxyReg.fail

	/** Returns true if the both objects' associated proxies are equal. */
        @Override
	public boolean equals(Object obj) {
	    if (obj instanceof ProxyReg) {
		return proxy.equals( ((ProxyReg)obj).proxy );
	    } else {
                return false;
            }//endif
	}//end ProxyReg.equals

	/** Returns the hash code of the proxy referenced in this class. */
        @Override
	public int hashCode() {
	    return proxy.hashCode();
	}//end hashCode

    }//end class ProxyReg

    /* Listener class for discovery/discard notification of lookup services. */
    private class DiscMgrListener implements DiscoveryListener {
	/* Invoked when new or previously discarded lookup is discovered. */
        @Override
	public void discovered(DiscoveryEvent e) {
		ServiceRegistrar[] proxys
				       = (ServiceRegistrar[])e.getRegistrars();
                int l = proxys.length;
		for(int i=0;i<l;i++) {
		    /* Prepare the proxy to the discovered lookup service
					 * before interacting with it.
					 */
		    try {
			proxys[i]
			  = (ServiceRegistrar)registrarPreparer.prepareProxy
								   (proxys[i]);
			logger.log(Level.FINEST, "JoinManager - discovered "
				   +"lookup service proxy prepared: {0}",
				   proxys[i]);
		    } catch(Exception e1) {
			LogUtil.logThrow(logger, Level.INFO,
			    DiscMgrListener.class, "discovered", "failure "
			    + "preparing discovered ServiceRegistrar proxy: "
			    + "{0}", new Object[] { proxys[i] }, e1);
			discMgr.discard(proxys[i]);
			continue;
		    }
		    /* If the serviceItem is a lookup service, don't need to
                     * register it with itself since a well-defined lookup
                     * service will always register with itself.
                     */
		    if( !proxys[i].equals(serviceItem.service) ) {
			ProxyReg proxyReg = new ProxyReg(proxys[i]);
			if( !joinSet.contains(proxyReg) ) {
			    joinSet.add(proxyReg);
			    proxyReg.addTask(new RegisterTask
						(proxyReg,
						 (Entry[])lookupAttr.clone()));
			}//endif
		    }//endif
		}//end loop
	}//end discovered

	/* Invoked when previously discovered lookup is discarded. */
        @Override
	public void discarded(DiscoveryEvent e) {
            ServiceRegistrar[] proxys
                                  = (ServiceRegistrar[])e.getRegistrars();
            int l = proxys.length;
            for(int i=0;i<l;i++) {
                ProxyReg proxyReg = findReg(proxys[i]);
                if(proxyReg != null) {
                    removeTasks(proxyReg);
                    joinSet.remove(proxyReg);
                    try {
                        leaseRenewalMgr.remove( proxyReg.serviceLease );
                    } catch(UnknownLeaseException ex) { /*ignore*/ }
                    proxyReg.addTask(new DiscardProxyTask(proxyReg));
                }//endif
            }//end loop
	}//end discarded
    }//end class DiscMgrListener

    /* Name of this component; used in config entry retrieval and the logger.*/
    private static final String COMPONENT_NAME = "net.jini.lookup.JoinManager";
    /* Logger used by this utility. */
    private static final Logger logger = Logger.getLogger(COMPONENT_NAME);
    /** Maximum number of concurrent tasks that can be run in any default
     * ExecutorService created by this class.
     */
    private static final int MAX_N_TASKS = 15;
    /** Whenever a task is created in this join manager, it is assigned a
     *  unique sequence number so that the task is not run prior to the
     *  execution, and completion of, any other tasks with which that task
     *  is associated (tasks are grouped by the <code>ProxyReg</code> with
     *  which each task is associated). This field contains the value of
     *  the sequence number assigned to the most recently created task.
     */
    private int taskSeqN = 0; // access sync on taskList
    /** Task manager for the various tasks executed by this join manager.
     *  On the first attempt to execute any task is managed by this
     *  <code>ExecutorService</code> so that the number of concurrent threads
     *  can be bounded. If one or more of those attempts fails, a
     *  <code>WakeupManager</code> is used (through the use of a
     *  <code>RetryTask</code>) to schedule - at a later time (employing a
     *  "backoff strategy") - the re-execution of each failed task in this
     *  <code>ExecutorService</code>.
     */
    private final ExecutorService executor;
    private final ProxyRegTaskQueue proxyRegTaskQueue;
    /** Maximum number of times a failed task is allowed to be re-executed. */
    private final int maxNRetries;
    /** Wakeup manager for the various tasks executed by this join manager.
     *  After an initial failure of any task executed by this join manager,
     *  the failed task is managed by this <code>WakeupManager</code>; which
     *  schedules the re-execution of the failed task - in the task manager -
     *  at various times in the future until either the task succeeds or the
     *  task has been executed the maximum number of allowable times. This
     *  wakeup manager is supplied to the <code>RetryTask</code>) that
     *  performs the actual task execution so that when termination of this
     *  join manager is requested, all tasks scheduled for retry by this
     *  wakeup manager can be cancelled.
     */
    private final WakeupManager wakeupMgr;
    /** Contains the reference to the service that is to be registered with
     *  all of the desired lookup services referenced by <code>discMgr</code>.
     */
    private volatile ServiceItem serviceItem = null; // writes sync on JoinManager.this
    /** Contains the attributes with which to associate the service in each
     *  of the lookup services with which this join manager registers the
     *  service.
     */
    private volatile Entry[] lookupAttr = null; // writes sync on JoinManager.this
    /** Contains the listener -- instantiated by the entity that constructs
     *  this join manager -- that will receive an event containing the
     *  service ID assigned to this join manager's service by one of the
     *  lookup services with which that service is registered.
     */
    private final ServiceIDListener callback;
    /** Contains elements of type <code>ProxyReg</code> where each element
     *  references a proxy to one of the lookup services with which this
     *  join manager's service is registered.
     */
    private final List<ProxyReg> joinSet = new CopyOnWriteArrayList<ProxyReg>();
    /** Contains the discovery manager that discovers the lookup services
     *  with which this join manager will register its associated service.
     */
    private final DiscoveryManagement discMgr;
    /** Contains the discovery listener registered by this join manager with
     *  the discovery manager so that this join manager is notified whenever
     *  one of the desired lookup services is discovered or discarded.
     */
    private final DiscMgrListener discMgrListener ;
    /** Flag that indicate whether the discovery manager employed by this
     *  join manager was created by this join manager itself, or by the
     *  entity that constructed this join manager.
     */
    private final boolean bCreateDiscMgr;
    /** Contains the lease renewal manager that renews all of the leases
     *  this join manager's service holds with each lookup service with which
     *  it has been registered.
     */
    private final LeaseRenewalManager leaseRenewalMgr;
    /** The value to use as the <code>renewDuration</code> parameter
     *  when invoking the lease renewal manager's <code>renewUntil</code>
     *  method to add a service lease to manage. This value represents,
     *  effectively, the time interval (in milliseconds) over which each
     *  managed lease must be renewed. As this value is made smaller,
     *  renewal requests will be made more frequently while the service
     *  is up, and lease expirations will occur sooner when the service
     *  goes down.
     */
    private final long renewalDuration;
    /** Flag that indicates if this join manager has been terminated. */
    private volatile boolean bTerminated = false; // write access sync on this.
    /* Preparer for the proxies to the lookup services that are discovered
     * and used by this utility.
     */
    private final ProxyPreparer registrarPreparer;
    /* Preparer for the proxies to the registrations returned to this utility
     * upon registering the service with each discovered lookup service.
     */
    private final ProxyPreparer registrationPreparer;
    /* Preparer for the proxies to the leases returned to this utility through
     * the registrations with each discovered lookup service with which this
     * utility has registered the service.
     */
    private final ProxyPreparer serviceLeasePreparer;

    /** 
     * Constructs an instance of this class that will register the given
     * service reference with all discovered lookup services and, through
     * an event sent to the given <code>ServiceIDListener</code> object,
     * communicate the service ID assigned by the first lookup service
     * with which the service is registered. This constructor is typically
     * used by services which have not yet been assigned a service ID.
     * <p>
     * The value input to the <code>serviceProxy</code> parameter represents
     * the service reference (proxy) to register with each discovered lookup
     * service. If the <code>Object</code> input to that parameter is not
     * <code>Serializable</code>, an <code>IllegalArgumentException</code>
     * is thrown. If <code>null</code> is input to that parameter, a
     * <code>NullPointerException</code> is thrown.
     * <p>
     * The value input to the <code>attrSets</code> parameter is an array
     * of <code>Entry</code> objects, none of whose elements may be
     * <code>null</code>, that represents the new set of attributes to
     * associate with the new service reference to be registered. Passing
     * <code>null</code> as the value of the <code>attrSets</code> parameter
     * is equivalent to passing an empty array. If any of the elements
     * of the <code>attrSets</code> array are <code>null</code>, a
     * <code>NullPointerException</code> is thrown. The set of attributes
     * passed in this parameter will be associated with the service in all
     * future join processing until those attributes are changed through
     * an invocation of a method on this class such as,
     * <code>addAttributes</code>, <code>setAttributes</code>, 
     * <code>modifyAttributes</code>, or <code>replaceRegistration</code>.
     * <p>
     * When constructing this utility, the service supplies an object through
     * which notifications that indicate a lookup service has been discovered
     * or discarded will be received. At a minimum, the object supplied
     * (through the <code>discoveryMgr</code> parameter) must satisfy the
     * contract defined in the <code>DiscoveryManagement</code> interface.
     * That is, the object supplied must provide this utility with the ability
     * to set discovery listeners and to discard previously discovered
     * lookup services when they are found to be unavailable. A value of
     * <code>null</code> may be input to the <code>discoveryMgr</code>
     * parameter. When <code>null</code> is input to that parameter, an
     * instance of <code>LookupDiscoveryManager</code> is used to listen
     * for events announcing the discovery of only those lookup services
     * that are members of the public group.
     * <p>
     * The object input to the <code>leaseMgr</code> parameter provides for
     * the coordination, systematic renewal, and overall management of all
     * leases on the given service reference's residency in the lookup 
     * services that have been joined. As with the <code>discoveryMgr</code>
     * parameter, a value of <code>null</code> may be input to this
     * parameter. When <code>null</code> is input to this parameter,
     * an instance of <code>LeaseRenewalManager</code>, initially managing
     * no <code>Lease</code> objects will be used. This feature allows a
     * service to either use a single entity to manage all of its leases,
     * or to use separate entities: one to manage the leases unrelated to
     * the join process, and one to manage the leases that result from the
     * join process, that are accessible only within the current instance
     * of the <code>JoinManager</code>.
     * 
     * @param serviceProxy the service reference (proxy) to register with all
     *                     discovered lookup services
     * @param attrSets     array of <code>Entry</code> consisting of the
     *                     attribute sets with which to register the service
     * @param callback     reference to the object that should receive the
     *                     event containing the service ID, assigned to the
     *                     service by the first lookup service with which the
     *                     service reference is registered
     * @param discoveryMgr reference to the <code>DiscoveryManagement</code>
     *                     object this class should use to manage lookup
     *                     service discovery on behalf of the given service
     * @param leaseMgr     reference to the <code>LeaseRenewalManager</code>
     *                     object this class should use to manage the leases
     *                     on the given service's residency in the lookup 
     *                     services that have been joined
     *
     * @throws java.lang.IllegalArgumentException if the object input to the
     *         <code>serviceProxy</code> parameter is not serializable
     * @throws java.lang.NullPointerException if either <code>null</code> is
     *         input to the <code>serviceProxy</code> parameter, or at least
     *         one of the elements of the <code>attrSets</code> parameter is
     *         <code>null</code>
     * @throws java.io.IOException if initiation of discovery process results
     *         in <code>IOException</code> when socket allocation occurs
     *
     * @throws java.lang.IllegalStateException if this method is called on 
     *         a terminated <code>JoinManager</code> instance. Note that this 
     *         exception is implementation-specific.
     *
     * @see net.jini.lookup.ServiceIDListener
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.discovery.LookupDiscoveryManager
     * @see net.jini.lease.LeaseRenewalManager
     */
     public JoinManager(Object serviceProxy,
                        Entry[] attrSets,
			ServiceIDListener callback,
			DiscoveryManagement discoveryMgr,
			LeaseRenewalManager leaseMgr)    throws IOException
    {
           this(serviceProxy, attrSets, null, callback, 
                 getConf(EmptyConfiguration.INSTANCE, leaseMgr, discoveryMgr, serviceProxy));
    }//end constructor

    /** 
     * Constructs an instance of this class, configured using the items
     * retrieved through the given <code>Configuration</code> object,
     * that will register the given service reference with all discovered
     * lookup services and, through an event sent to the given
     * <code>ServiceIDListener</code> object, communicate the service ID
     * assigned by the first lookup service with which the service is
     * registered. This constructor is typically used by services which
     * have not yet been assigned a service ID, and which wish to allow
     * for deployment-time configuration of the service's join processing.
     * <p>
     * The items used to configure the current instance of this class
     * are obtained through the object input to the <code>config</code>
     * parameter. If <code>null</code> is input to that parameter, a
     * <code>NullPointerException</code> is thrown.
     * <p>
     * The object this utility will use to manage lookup service discovery on
     * behalf of the given service can be supplied through either the
     * <code>discoveryMgr</code> parameter or through an entry contained
     * in the given <code>Configuration</code>. If <code>null</code> is input
     * to the <code>discoveryMgr</code> parameter, an attempt will first be
     * made to retrieve from the given <code>Configuration</code>, an entry
     * named "discoveryManager" (described above). If such an object is
     * successfully retrieved from the given <code>Configuration</code>, that 
     * object will be used to perform the lookup service discovery management
     * required by this utility.
     * <p>
     * If <code>null</code> is input to the <code>discoveryMgr</code>
     * parameter, and no entry named "discoveryManager" is specified in the
     * given <code>Configuration</code>, then an instance of the utility class
     * <code>LookupDiscoveryManager</code> will be used to listen for events
     * announcing the discovery of only those lookup services that are
     * members of the public group.
     * <p>
     * As with the <code>discoveryMgr</code> parameter, the object this 
     * utility will use to perform lease management on behalf of the given
     * service can be supplied through either the <code>leaseMgr</code>
     * parameter or through an entry contained in the given
     * <code>Configuration</code>. If <code>null</code> is input to the
     * <code>leaseMgr</code> parameter, an attempt will first be made to
     * retrieve from the given <code>Configuration</code>, an entry named
     * "leaseManager" (described above). If such an object is successfully
     * retrieved from the given <code>Configuration</code>, that object
     * will be used to perform the lease management required by this utility.
     * <p>
     * If <code>null</code> is input to the <code>leaseMgr</code>
     * parameter, and no entry named "leaseManager" is specified in the
     * given <code>Configuration</code>, then an instance of the utility
     * class <code>LeaseRenewalManager</code> that takes the given
     * <code>Configuration</code> will be created (initially managing no
     * leases) and used to perform all required lease renewal management
     * on behalf of the given service.
     * <p>
     * Except for the <code>config</code> parameter and the additional 
     * semantics imposed by that parameter (as noted above), all other
     * parameters of this form of the constructor, along with their
     * associated semantics, are identical to that of the five-argument
     * constructor that takes a <code>ServiceIDListener</code>.
     * 
     * @param serviceProxy the service reference (proxy) to register with all
     *                     discovered lookup services
     * @param attrSets     array of <code>Entry</code> consisting of the
     *                     attribute sets with which to register the service
     * @param callback     reference to the <code>ServiceIDListener</code>
     *                     object that should receive the event containing the
     *                     service ID assigned to the service by the first
     *                     lookup service with which the service reference
     *                     is registered
     * @param discoveryMgr reference to the <code>DiscoveryManagement</code>
     *                     object this class should use to manage lookup
     *                     service discovery on behalf of the given service
     * @param leaseMgr     reference to the <code>LeaseRenewalManager</code>
     *                     object this class should use to manage the leases
     *                     on the given service's residency in the lookup 
     *                     services that have been joined
     * @param config       instance of <code>Configuration</code> through
     *                     which the items used to configure the current
     *                     instance of this class are obtained
     *
     * @throws java.lang.IllegalArgumentException if the object input to the
     *         <code>serviceProxy</code> parameter is not serializable
     * @throws java.lang.NullPointerException if <code>null</code> is input
     *         to the <code>serviceProxy</code> parameter or the
     *         <code>config</code> parameter, or if at least one of the
     *         elements of the <code>attrSets</code> parameter is
     *         <code>null</code>
     * @throws java.io.IOException if initiation of discovery process results
     *         in <code>IOException</code> when socket allocation occurs
     * @throws net.jini.config.ConfigurationException if an exception
     *         occurs while retrieving an item from the given
     *         <code>Configuration</code> object
     *
     * @throws java.lang.IllegalStateException if this method is called on 
     *         a terminated <code>JoinManager</code> instance. Note that this 
     *         exception is implementation-specific.
     *
     * @see net.jini.lookup.ServiceIDListener
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.discovery.LookupDiscoveryManager
     * @see net.jini.lease.LeaseRenewalManager
     * @see net.jini.config.Configuration
     * @see net.jini.config.ConfigurationException
     */
     public JoinManager(Object serviceProxy,
                        Entry[] attrSets,
			ServiceIDListener callback,
			DiscoveryManagement discoveryMgr,
			LeaseRenewalManager leaseMgr,
                        Configuration config)
                                    throws IOException, ConfigurationException
    {
        
        this(serviceProxy, attrSets, null, callback, 
            getConfig(config, leaseMgr, discoveryMgr, serviceProxy)
        );
    }//end constructor

    /** 
     * Constructs an instance of this class that will register the
     * service with all discovered lookup services, using the supplied 
     * <code>ServiceID</code>. This constructor is typically used by
     * services which have already been assigned a service ID (possibly
     * by the service provider itself or as a result of a prior registration
     * with some lookup service), and which do not wish to allow for
     * deployment-time configuration of the service's join processing.
     * <p>
     * Except that the desired <code>ServiceID</code> is supplied through the
     * <code>serviceID</code> parameter rather than through a notification
     * sent to a <code>ServiceIDListener</code>, all other parameters
     * of this form of the constructor, along with their associated semantics,
     * are identical to that of the five-argument constructor that takes
     * a <code>ServiceIDListener</code>.
     *
     * @param serviceProxy a reference to the service requesting the services
     *                     of this class
     * @param attrSets     array of <code>Entry</code> consisting of the
     *                     attribute sets with which to register the service
     * @param serviceID    an instance of <code>ServiceID</code> with which to
     *                     register the service with all desired lookup
     *                     services
     * @param discoveryMgr reference to the <code>DiscoveryManagement</code>
     *                     object this class should use to manage the given
     *                     service's lookup service discovery duties
     * @param leaseMgr     reference to the <code>LeaseRenewalManager</code>
     *                     object this class should use to manage the leases
     *                     on the given service's residency in the lookup 
     *                     services that have been joined
     *
     * @throws java.lang.IllegalArgumentException if the object input to the
     *         <code>serviceProxy</code> parameter is not serializable
     * @throws java.lang.NullPointerException if either <code>null</code> is
     *         input to the <code>serviceProxy</code> parameter, or at least
     *         one of the elements of the <code>attrSets</code> parameter is
     *         <code>null</code>
     * @throws java.io.IOException if initiation of discovery process results
     *         in <code>IOException</code> when socket allocation occurs
     *
     * @throws java.lang.IllegalStateException if this method is called on 
     *         a terminated <code>JoinManager</code> instance. Note that this 
     *         exception is implementation-specific.
     *
     * @see net.jini.core.lookup.ServiceID
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.discovery.LookupDiscoveryManager
     * @see net.jini.lease.LeaseRenewalManager
     */
     public JoinManager(Object serviceProxy,
                        Entry[] attrSets,
			ServiceID serviceID,
			DiscoveryManagement discoveryMgr,
			LeaseRenewalManager leaseMgr)    throws IOException
    {
       this(serviceProxy, attrSets, serviceID, null, 
             getConf(EmptyConfiguration.INSTANCE, leaseMgr, discoveryMgr, serviceProxy)
       );
    }//end constructor

    /** 
     * Constructs an instance of this class, configured using the items
     * retrieved through the given <code>Configuration</code>, that will
     * register the service with all discovered lookup services, using the
     * supplied <code>ServiceID</code>. This constructor is typically used by
     * services which have already been assigned a service ID (possibly
     * by the service provider itself or as a result of a prior registration
     * with some lookup service), and which wish to allow for deployment-time
     * configuration of the service's join processing.
     * <p>
     * The items used to configure the current instance of this class
     * are obtained through the object input to the <code>config</code>
     * parameter. If <code>null</code> is input to that parameter, a
     * <code>NullPointerException</code> is thrown.
     * <p>
     * Except that the desired <code>ServiceID</code> is supplied through the
     * <code>serviceID</code> parameter rather than through a notification
     * sent to a <code>ServiceIDListener</code>, all other parameters
     * of this form of the constructor, along with their associated semantics,
     * are identical to that of the six-argument constructor that takes
     * a <code>ServiceIDListener</code>.
     *
     * @param serviceProxy a reference to the service requesting the services
     *                     of this class
     * @param attrSets     array of <code>Entry</code> consisting of the
     *                     attribute sets with which to register the service.
     * @param serviceID    an instance of <code>ServiceID</code> with which to
     *                     register the service with all desired lookup
     *                     services
     * @param discoveryMgr reference to the <code>DiscoveryManagement</code>
     *                     object this class should use to manage lookup
     *                     service discovery on behalf of the given service
     * @param leaseMgr     reference to the <code>LeaseRenewalManager</code>
     *                     object this class should use to manage the leases
     *                     on the given service's residency in the lookup 
     *                     services that have been joined
     * @param config       instance of <code>Configuration</code> through
     *                     which the items used to configure the current
     *                     instance of this class are obtained
     *
     * @throws java.lang.IllegalArgumentException if the object input to the
     *         <code>serviceProxy</code> parameter is not serializable
     * @throws java.lang.NullPointerException if <code>null</code> is input
     *         to the <code>serviceProxy</code> parameter or the
     *         <code>config</code> parameter, or if at least one of the
     *         elements of the <code>attrSets</code> parameter is
     *         <code>null</code>
     * @throws java.io.IOException if initiation of discovery process results
     *         in <code>IOException</code> when socket allocation occurs
     * @throws net.jini.config.ConfigurationException if an exception
     *         occurs while retrieving an item from the given
     *         <code>Configuration</code> object
     *
     * @throws java.lang.IllegalStateException if this method is called on 
     *         a terminated <code>JoinManager</code> instance. Note that this 
     *         exception is implementation-specific.
     *
     * @see net.jini.core.lookup.ServiceID
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.discovery.LookupDiscoveryManager
     * @see net.jini.lease.LeaseRenewalManager
     * @see net.jini.config.Configuration
     * @see net.jini.config.ConfigurationException
     */
     public JoinManager(Object serviceProxy,
                        Entry[] attrSets,
			ServiceID serviceID,
			DiscoveryManagement discoveryMgr,
			LeaseRenewalManager leaseMgr,
                        Configuration config)
                                    throws IOException, ConfigurationException
    {
        this(serviceProxy, attrSets, serviceID, null, 
             getConfig(config, leaseMgr, discoveryMgr, serviceProxy)
       );
    }//end constructor

    /** 
     * Returns the instance of <code>DiscoveryManagement</code> that was
     * either passed into the constructor, or that was created as a result
     * of <code>null</code> being input to that parameter.
     * <p>
     * The object returned by this method encapsulates the mechanism by which
     * either the <code>JoinManager</code> or the entity itself can set
     * discovery listeners and discard previously discovered lookup services
     * when they are found to be unavailable.
     *
     * @return the instance of the <code>DiscoveryManagement</code> interface
     *         that was either passed into the constructor, or that was
     *         created as a result of <code>null</code> being input to that
     *         parameter.
     * 
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.discovery.LookupDiscoveryManager
     */
    public DiscoveryManagement getDiscoveryManager(){
        if(bTerminated) 
            throw new IllegalStateException("join manager was terminated");
	return discMgr; 
    }//end getDiscoveryManager

    /** 
     * Returns the instance of the <code>LeaseRenewalManager</code> class
     * that was either passed into the constructor, or that was created
     * as a result of <code>null</code> being input to that parameter.
     * <p>
     * The object returned by this method manages the leases requested and
     * held by the <code>JoinManager</code>. Although it may also manage
     * leases unrelated to the join process that are requested and held by
     * the service itself, the leases with which the <code>JoinManager</code>
     * is concerned are the leases that correspond to the service registration
     * requests made with each lookup service the service wishes to join.
     *
     * @return the instance of the <code>LeaseRenewalManager</code> class
     *         that was either passed into the constructor, or that was
     *         created as a result of <code>null</code> being input to that
     *         parameter.
     * 
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.lease.LeaseRenewalManager
     */
    public LeaseRenewalManager getLeaseRenewalManager(){
        if(bTerminated) 
            throw new IllegalStateException("join manager was terminated");
	return leaseRenewalMgr;
    }//end getLeaseRenewalManager

    /** 
     * Returns an array of <code>ServiceRegistrar</code> objects, each
     * corresponding to a lookup service with which the service is currently
     * registered (joined). If there are no lookup services with which the
     * service is currently registered, this method returns the empty array.
     * This method returns a new array upon each invocation.
     *
     * @return array of instances of <code>ServiceRegistrar</code>, each
     *         corresponding to a lookup service with which the service is
     *         currently registered 
     * 
     * @see net.jini.core.lookup.ServiceRegistrar
     */
    public ServiceRegistrar[] getJoinSet() {
        if(bTerminated) throw new IllegalStateException("join manager was terminated");
        List<ServiceRegistrar> retList = new LinkedList<ServiceRegistrar>();
        for (Iterator<ProxyReg> iter = joinSet.iterator(); iter.hasNext(); ) {
            ProxyReg proxyReg = iter.next();
            if(proxyReg.srvcRegistration != null) {//test registration flag
                retList.add(proxyReg.proxy);
            }//endif
        }//end loop
        return ( (retList.toArray(new ServiceRegistrar[retList.size()]) ) );
    }//end getJoinSet

    /** 
     * Returns an array containing the set of attributes currently associated
     * with the service. If the service is not currently associated with an
     * attribute set, this method returns the empty array. This method returns
     * a new array upon each invocation.
     *
     * @return array of instances of <code>Entry</code> consisting of the
     *         set of attributes with which the service is registered in
     *         each lookup service that it has joined
     * 
     * @see net.jini.core.entry.Entry
     * @see #setAttributes
     */
    public Entry[] getAttributes() {
        if(bTerminated) throw new IllegalStateException("join manager was terminated");
        Entry[] result = lookupAttr.clone();
	for (int i = 0, l = result.length; i < l; i++){
	    if (result[i] instanceof CloneableEntry){
		result[i] = ((CloneableEntry)result[i]).clone();
	    }
	}
	return result;
    }//end getAttributes

    /** 
     * Associates a new set of attributes with the service, in addition to
     * the service's current set of attributes. The association of this new
     * set of attributes with the service will be propagated to each lookup
     * service with which the service is registered. Note that this
     * propagation is performed asynchronously, thus there is no guarantee
     * that the propagation of the attributes to all lookup services with
     * which the service is registered will have completed upon return from
     * this method.
     * <p>
     * An invocation of this method with duplicate elements in the 
     * <code>attrSets</code> parameter (where duplication means attribute
     * equality as defined by calling the <code>MarshalledObject.equals</code>
     * method on field values) is equivalent to performing the invocation
     * with the duplicates removed from that parameter.
     * <p>
     * Note that because there is no guarantee that attribute propagation
     * will have completed upon return from this method, services that 
     * invoke this method must take care not to modify the contents of the 
     * <code>attrSets</code> parameter. Doing so could cause the service's
     * attribute state to be corrupted or inconsistent on a subset of the
     * lookup services with which the service is registered as compared with
     * the state reflected on the remaining lookup services. It is for this
     * reason that the effects of modifying the contents of the
     * <code>attrSets</code> parameter, after this method is invoked, are
     * undefined.
     *
     * @param attrSets array of <code>Entry</code> consisting of the
     *                 attribute sets with which to augment the service's
     *                 current set of attributes
     *
     * @throws java.lang.NullPointerException if either <code>null</code> is
     *         input to the <code>attrSets</code> parameter, or one or more
     *         of the elements of the <code>attrSets</code> parameter is
     *         <code>null</code>
     *
     * @see net.jini.core.entry.Entry
     */
    public void addAttributes(Entry[] attrSets) {
	addAttributes(attrSets, false);
    }//end addAttributes

    /** 
     * Associates a new set of attributes with the service, in addition to
     * the service's current set of attributes. The association of this new
     * set of attributes with the service will be propagated to each lookup
     * service with which the service is registered. Note that this
     * propagation is performed asynchronously, thus there is no guarantee
     * that the propagation of the attributes to all lookup services with
     * which the service is registered will have completed upon return from
     * this method.
     * <p>
     * An invocation of this method with duplicate elements in the 
     * <code>attrSets</code> parameter (where duplication means attribute
     * equality as defined by calling the <code>MarshalledObject.equals</code>
     * method on field values) is equivalent to performing the invocation
     * with the duplicates removed from that parameter.
     * <p>
     * Note that because there is no guarantee that attribute propagation
     * will have completed upon return from this method, services that 
     * invoke this method must take care not to modify the contents of the 
     * <code>attrSets</code> parameter. Doing so could cause the service's
     * attribute state to be corrupted or inconsistent on a subset of the
     * lookup services with which the service is registered as compared with
     * the state reflected on the remaining lookup services. It is for this
     * reason that the effects of modifying the contents of the
     * <code>attrSets</code> parameter, after this method is invoked, are
     * undefined.
     * <p>
     * A service typically employs this version of <code>addAttributes</code> 
     * to prevent clients or other services from attempting to add what are
     * referred to as "service controlled attributes" to the service's set.
     * A service controlled attribute is an attribute that implements the
     * <code>ServiceControlled</code> marker interface.
     * <p>
     * Consider a printer service. With printers, there are often times error
     * conditions, that only the printer can detect (for example, a paper
     * jam or a toner low condition). To report conditions such as these to
     * interested parties, the printer typically adds an attribute to its
     * attribute set, resulting in an event being sent that notifies clients
     * that have registered interest in such events. When the condition is
     * corrected, the printer would then remove the attribute from its set
     * by invoking the <code>modifyAttributes</code> method in the appropriate
     * manner.
     * <p>
     * Attributes representing conditions that only the service can know about
     * or control are good candidates for being defined as service controlled
     * attributes. That is, the service provider (the developer of the printer
     * service for example) would define the attributes that represent
     * conditions such as those just described to implement the
     * <code>ServiceControlled</code> marker interface. Thus, when other
     * entities attempt to add new attributes, services that wish to employ
     * such service controlled attributes should ultimately invoke only this
     * version of <code>addAttributes</code> (with the <code>checkSC</code>
     * parameter set to <code>true</code>), resulting in a
     * <code>SecurityException</code> if any of the attributes being added
     * happen to be service controlled attributes. In this way, only the
     * printer itself would be able to set a "paper jammed" or "toner low"
     * attribute, not some arbitrary client.
     *
     * @param attrSets array of <code>Entry</code> consisting of the
     *                 attribute sets with which to augment the service's
     *                 current set of attributes
     * @param checkSC  <code>boolean</code> flag indicating whether the
     *                 elements of the set of attributes to add should be
     *                 checked to determine if they are service controlled
     *                 attributes
     *
     * @throws java.lang.NullPointerException if either <code>null</code> is
     *         input to the <code>attrSets</code> parameter, or one or more
     *         of the elements of the <code>attrSets</code> parameter is
     *         <code>null</code>
     *
     * @throws java.lang.SecurityException if the <code>checkSC</code>
     *         parameter is <code>true</code>, and at least one of the
     *         attributes to be added is an instance of the
     *         <code>ServiceControlled</code> marker interface
     *
     * @see net.jini.core.entry.Entry
     * @see net.jini.lookup.entry.ServiceControlled
     */
    public void addAttributes(Entry[] attrSets, boolean checkSC) {
        if(bTerminated) throw new IllegalStateException("join manager was terminated");
	synchronized(this) {
	    lookupAttr = LookupAttributes.add(lookupAttr, attrSets, checkSC);
            serviceItem = new ServiceItem(serviceItem.serviceID, serviceItem.service, lookupAttr);
        }
        Iterator<ProxyReg> it = joinSet.iterator();
        while (it.hasNext()){
            ProxyReg proxyReg = it.next();
            proxyReg.addTask(new AddAttributesTask(proxyReg,attrSets));
        }//end loop
    }//end addAttributes

    /** 
     * Replaces the service's current set of attributes with a new set of
     * attributes. The association of this new set of attributes with the
     * service will be propagated to each lookup service with which the
     * service is registered. Note that this propagation is performed
     * asynchronously, thus there is no guarantee that the propagation of
     * the attributes to all lookup services with which the service is
     * registered will have completed upon return from this method.
     * <p>
     * An invocation of this method with duplicate elements in the 
     * <code>attrSets</code> parameter (where duplication means attribute
     * equality as defined by calling the <code>MarshalledObject.equals</code>
     * method on field values) is equivalent to performing the invocation
     * with the duplicates removed from that parameter.
     * <p>
     * Note that because there is no guarantee that attribute propagation
     * will have completed upon return from this method, services that 
     * invoke this method must take care not to modify the contents of the 
     * <code>attrSets</code> parameter. Doing so could cause the service's
     * attribute state to be corrupted or inconsistent on a subset of the
     * lookup services with which the service is registered as compared with
     * the state reflected on the remaining lookup services. It is for this
     * reason that the effects of modifying the contents of the
     * <code>attrSets</code> parameter, after this method is invoked, are
     * undefined.
     *
     * @param attrSets array of <code>Entry</code> consisting of the
     *                 attribute sets with which to replace the service's
     *                 current set of attributes
     *
     * @throws java.lang.NullPointerException if either <code>null</code> is
     *         input to the <code>attrSets</code> parameter, or one or more
     *         of the elements of the <code>attrSets</code> parameter is
     *         <code>null</code>.
     *
     * @see net.jini.core.entry.Entry
     * @see #getAttributes
     */
    public void setAttributes(Entry[] attrSets) {
        if(bTerminated) 
            throw new IllegalStateException("join manager was terminated");
        testForNullElement(attrSets);
	synchronized(this) {
            lookupAttr = (Entry[]) attrSets.clone();
            serviceItem = new ServiceItem(serviceItem.serviceID, 
                                          serviceItem.service, lookupAttr);
        }
        Iterator<ProxyReg> it = joinSet.iterator();
        while (it.hasNext()){
            ProxyReg proxyReg = it.next();
            proxyReg.addTask(new SetAttributesTask(proxyReg,attrSets));
        }
    }//end setAttributes

    /** 
     * Changes the service's current set of attributes using the same
     * semantics as the <code>modifyAttributes</code> method of the
     * <code>ServiceRegistration</code> class.
     * <p>
     * The association of the new set of attributes with the service will
     * be propagated to each lookup service with which the service is
     * registered. Note that this propagation is performed asynchronously,
     * thus there is no guarantee that the propagation of the attributes to
     * all lookup services with which the service is registered will have
     * completed upon return from this method.
     * <p>
     * Note that if the length of the array containing the templates does
     * not equal the length of the array containing the modifications, an
     * <code>IllegalArgumentException</code> will be thrown and propagated
     * through this method.
     * <p>
     * Note also that because there is no guarantee that attribute propagation
     * will have completed upon return from this method, services that 
     * invoke this method must take care not to modify the contents of the 
     * <code>attrSets</code> parameter. Doing so could cause the service's
     * attribute state to be corrupted or inconsistent on a subset of the
     * lookup services with which the service is registered as compared with
     * the state reflected on the remaining lookup services. It is for this
     * reason that the effects of modifying the contents of the
     * <code>attrSets</code> parameter, after this method is invoked, are
     * undefined.
     *
     * @param attrSetTemplates array of <code>Entry</code> used to identify
     *                         which elements to modify from the service's
     *                         current set of attributes
     * @param attrSets         array of <code>Entry</code> containing the
     *                         actual modifications to make in the matching
     *                         sets found using the 
     *                         <code>attrSetTemplates</code> parameter
     *
     * @throws java.lang.IllegalArgumentException if the array containing the
     *         templates does not equal the length of the array containing the
     *         modifications
     *
     * @see net.jini.core.entry.Entry
     * @see net.jini.core.lookup.ServiceRegistration#modifyAttributes
     */
    public void modifyAttributes(Entry[] attrSetTemplates, Entry[] attrSets) {
	modifyAttributes(attrSetTemplates, attrSets, false);
    }//end modifyAttributes

    /** 
     * Changes the service's current set of attributes using the same
     * semantics as the <code>modifyAttributes</code> method of the
     * <code>ServiceRegistration</code> class.
     * <p>
     * The association of the new set of attributes with the service will
     * be propagated to each lookup service with which the service is
     * registered. Note that this propagation is performed asynchronously,
     * thus there is no guarantee that the propagation of the attributes to
     * all lookup services with which the service is registered will have
     * completed upon return from this method.
     * <p>
     * Note that if the length of the array containing the templates does
     * not equal the length of the array containing the modifications, an
     * <code>IllegalArgumentException</code> will be thrown and propagated
     * through this method.
     * <p>
     * Note also that because there is no guarantee that attribute propagation
     * will have completed upon return from this method, services that 
     * invoke this method must take care not to modify the contents of the 
     * <code>attrSets</code> parameter. Doing so could cause the service's
     * attribute state to be corrupted or inconsistent on a subset of the
     * lookup services with which the service is registered as compared with
     * the state reflected on the remaining lookup services. It is for this
     * reason that the effects of modifying the contents of the
     * <code>attrSets</code> parameter, after this method is invoked, are
     * undefined.
     * <p>
     * A service typically employs this version of 
     * <code>modifyAttributes</code> to prevent clients or other services
     * from attempting to modify what are referred to as "service controlled
     * attributes" in the service's set. A service controlled attribute is an
     * attribute that implements the <code>ServiceControlled</code> marker
     * interface.
     * <p>
     * Attributes representing conditions that only the service can know about
     * or control are good candidates for being defined as service controlled
     * attributes. When other entities attempt to modify a service's
     * attributes, if the service wishes to employ such service controlled
     * attributes, the service should ultimately invoke only this version 
     * of <code>modifyAttributes</code> (with the <code>checkSC</code>
     * parameter set to <code>true</code>), resulting in a
     * <code>SecurityException</code> if any of the attributes being modified
     * happen to be service controlled attributes.
     *
     * @param attrSetTemplates array of <code>Entry</code> used to identify
     *                         which elements to modify from the service's
     *                         current set of attributes
     * @param attrSets         array of <code>Entry</code> containing the
     *                         actual modifications to make in the matching
     *                         sets found using the 
     *                         <code>attrSetTemplates</code> parameter
     * @param checkSC          <code>boolean</code> flag indicating whether the
     *                         elements of the set of attributes to modify
     *                         should be checked to determine if they are
     *                         service controlled attributes
     *
     * @throws java.lang.IllegalArgumentException if the array containing the
     *         templates does not equal the length of the array containing
     *         the modifications
     *
     * @throws java.lang.SecurityException if the <code>checkSC</code>
     *         parameter is <code>true</code>, and at least one of the
     *         attributes to be modified is an instance of the
     *         <code>ServiceControlled</code> marker interface
     *
     * @see net.jini.core.entry.Entry
     * @see net.jini.core.lookup.ServiceRegistration#modifyAttributes
     * @see net.jini.lookup.entry.ServiceControlled
     */
    public void modifyAttributes(Entry[] attrSetTemplates,
                                 Entry[] attrSets,
                                 boolean checkSC)
    {
        if(bTerminated) 
            throw new IllegalStateException("join manager was terminated");
	synchronized(this) {
	    lookupAttr = LookupAttributes.modify(lookupAttr, attrSetTemplates,
                                                 attrSets, checkSC);
            serviceItem = new ServiceItem(serviceItem.serviceID,
                                          serviceItem.service, lookupAttr);
        }//end sync
        Iterator<ProxyReg> it = joinSet.iterator();
        while (it.hasNext()){
            ProxyReg proxyReg = it.next();
            proxyReg.addTask(new ModifyAttributesTask(proxyReg,
                                                      attrSetTemplates,
                                                      attrSets));
        }//end loop
    }//end modifyAttributes

    /**
     * Performs cleanup duties related to the termination of the lookup
     * service discovery event mechanism, as well as the lease and 
     * thread management performed by the <code>JoinManager</code>. This
     * method will cancel all of the service's managed leases that were
     * granted by the lookup services with which the service is registered,
     * and will terminate all threads that have been created.
     * <p>
     * Note that if the discovery manager employed by the instance of this
     * class that is being terminated was created by the instance itself,
     * this method will terminate all discovery processing being performed by
     * that manager object on behalf of the service; otherwise, the discovery
     * manager supplied by the service is still valid.
     * <p>
     * Whether an instance of the <code>LeaseRenewalManager</code> class was
     * supplied by the service or created by the <code>JoinManager</code>
     * itself, any reference to that object obtained by the service prior to
     * termination will still be valid after termination.
     * Note also this class makes certain concurrency guarantees with respect
     * to an invocation of the terminate method while other method invocations
     * are in progress. The termination process will not begin until
     * completion of all invocations of the methods defined in the public
     * interface of this class. Furthermore, once the termination process has
     * begun, no further remote method invocations will be made by this class,
     * and all other method invocations made on this class will not return
     * until the termination process has completed.
     * <p>
     * Upon completion of the termination process, the semantics of all
     * current and future method invocations on the instance of this class
     * that was just terminated are undefined; although the reference to the
     * <code>LeaseRenewalManager</code> object employed by that instance
     * of <code>JoinManager</code> is still valid.
     */
    public void terminate() {
        synchronized(this) {
            if(bTerminated) return;//allow for multiple terminations
            bTerminated = true;
        }//end sync(this)
        /* Terminate discovery and task management */
        discMgr.removeDiscoveryListener(discMgrListener);
        if(bCreateDiscMgr)  discMgr.terminate();
        terminateTaskMgr();
        /* Clear the joinSet and cancel all leases held by the service */
        Iterator<ProxyReg> iter = joinSet.iterator();
        while (iter.hasNext()) {
            try {
                leaseRenewalMgr.cancel(iter.next().serviceLease );
            } catch (UnknownLeaseException e){
            } catch (RemoteException e) {}
        }//end loop
        leaseRenewalMgr.close();
        joinSet.clear();
    }//end terminate

    /** 
     * Registers a new reference to the service with all current and future
     * discovered lookup services. The new service reference will replace
     * the reference that was previously registered as a result of either
     * constructing this utility, or a prior invocation of one of the forms
     * of this method. The new service reference will be registered using
     * the same <code>ServiceID</code> with which previous registrations
     * were made through this utility.
     * <p>
     * The value input to the <code>serviceProxy</code> parameter represents
     * the new service reference (proxy) to register with each discovered
     * lookup service. If the <code>Object</code> input to that parameter is
     * not <code>Serializable</code>, an <code>IllegalArgumentException</code>
     * is thrown. If <code>null</code> is input to that parameter, a
     * <code>NullPointerException</code> is thrown.
     * <p>
     * The attribute sets that this method associates with the new service
     * reference are the same attribute sets as those associated with the
     * old registration.
     *
     * @param serviceProxy the new service reference (proxy) to register with
     *                     all current and future discovered lookup services
     *
     * @throws java.lang.IllegalArgumentException if the object input to the
     *         <code>serviceProxy</code> parameter is not serializable
     * @throws java.lang.NullPointerException if <code>null</code> is input
     *         to the <code>serviceProxy</code> parameter
     *
     * @throws java.lang.IllegalStateException if this method is called on 
     *         a terminated <code>JoinManager</code> instance. Note that this 
     *         exception is implementation-specific.
     */
    public void replaceRegistration(Object serviceProxy) {
        replaceRegistrationDo(serviceProxy, null, false);
    }//end replaceRegistration

    /** 
     * Registers a new reference to the service with all current and future
     * discovered lookup services, applying semantics identical to the 
     * one-argument form of this method, except with respect to the
     * registration of the given attribute sets.
     * <p>
     * This form of <code>replaceRegistration</code> takes as its
     * second parameter, an array of <code>Entry</code> objects
     * (<code>attrSets</code>), none of whose elements may be
     * <code>null</code>, that represents the new set of attributes to
     * associate with the new service reference to be registered. As with
     * the constructor to this utility, passing <code>null</code> as the
     * value of the <code>attrSets</code> parameter is equivalent to passing
     * an empty array. If any of the elements of <code>attrSets</code> are 
     * <code>null</code>, a <code>NullPointerException</code> is thrown.
     * This new set of attributes will be associated with the service in
     * all future join processing.
     *
     * @param serviceProxy the new service reference (proxy) to register with
     *                     all current and future discovered lookup services
     * @param attrSets     array of <code>Entry</code> consisting of the
     *                     attribute sets with which to register the new
     *                     service reference. Passing <code>null</code> as
     *                     the value of this parameter is equivalent to
     *                     passing an empty <code>Entry</code> array
     *
     * @throws java.lang.IllegalArgumentException if the object input to the
     *         <code>serviceProxy</code> parameter is not serializable
     * @throws java.lang.NullPointerException if either <code>null</code> is
     *         input to the <code>serviceProxy</code> parameter, or at least
     *         one of the elements of the <code>attrSets</code> parameter is
     *         <code>null</code>
     *
     * @throws java.lang.IllegalStateException if this method is called on 
     *         a terminated <code>JoinManager</code> instance. Note that this 
     *         exception is implementation-specific.
     */
    public void replaceRegistration(Object serviceProxy, Entry[] attrSets) {
        replaceRegistrationDo(serviceProxy, attrSets, true);
    }//end replaceRegistration

    private static class Conf{
        ProxyPreparer registrarPreparer;
        ProxyPreparer registrationPreparer;
        ProxyPreparer serviceLeasePreparer;
        ExecutorService executorService;
        WakeupManager wakeupManager;
        Integer maxNretrys;
        LeaseRenewalManager leaseRenewalManager;
        Long renewalDuration;
        DiscoveryManagement discoveryMgr;
        boolean bcreateDisco;
        
        Conf (  ProxyPreparer registrarPreparer,
                ProxyPreparer registrationPreparer,
                ProxyPreparer serviceLeasePreparer,
                ExecutorService taskManager,
                WakeupManager wakeupManager,
                Integer maxNretrys,
                LeaseRenewalManager leaseRenewalManager,
                Long renewalDuration,
                DiscoveryManagement discoveryMgr,
                boolean bcreateDisco)
        {
            this.registrarPreparer = registrarPreparer;
            this.registrationPreparer = registrationPreparer;
            this.serviceLeasePreparer = serviceLeasePreparer;
            this.executorService = taskManager;
            this.wakeupManager = wakeupManager;
            this.maxNretrys = maxNretrys;
            this.leaseRenewalManager = leaseRenewalManager;
            this.renewalDuration = renewalDuration;
            this.discoveryMgr = discoveryMgr;
            this.bcreateDisco = bcreateDisco;
        }
    }
    
    /**
     * This method is for constructors that use an empty configuration.
     * 
     * @param config
     * @param leaseMgr
     * @param discoveryMgr
     * @param serviceProxy
     * @return Conf
     * @throws IOException
     * @throws NullPointerException
     * @throws IllegalArgumentException 
     */
    private static Conf getConf(    Configuration config,
                                   LeaseRenewalManager leaseMgr,
                                    DiscoveryManagement discoveryMgr,
                                    Object serviceProxy) 
            throws IOException, NullPointerException, IllegalArgumentException {
        try {
            return getConfig(config, leaseMgr, discoveryMgr, serviceProxy);
        } catch (ConfigurationException e){
            throw new IOException("Configuration problem during construction", e);
        }
    }
    
    /**
     * Gets the configuration and throws any exceptions.
     * 
     * This static method guards against finalizer attacks and allows fields
     * to be final.
     * 
     * @param config
     * @param leaseMgr
     * @param discoveryMgr
     * @param serviceProxy
     * @return Conf
     * @throws IOException
     * @throws ConfigurationException
     * @throws NullPointerException
     * @throws IllegalArgumentException 
     */
    private static Conf getConfig(  Configuration config,
                                    LeaseRenewalManager leaseMgr, 
                                    DiscoveryManagement discoveryMgr,
                                    Object serviceProxy) 
            throws IOException, ConfigurationException, NullPointerException,
            IllegalArgumentException 
    {
	if(!(serviceProxy instanceof java.io.Serializable)) {
            throw new IllegalArgumentException
                                       ("serviceProxy must be Serializable");
	}//endif
        /* Retrieve configuration items if applicable */
        if(config == null)  throw new NullPointerException("config is null");
        /* Proxy preparers */
        ProxyPreparer registrarPreparer = config.getEntry
                                                   (COMPONENT_NAME,
                                                    "registrarPreparer",
                                                    ProxyPreparer.class,
                                                    new BasicProxyPreparer());
        ProxyPreparer registrationPreparer = config.getEntry
                                                   (COMPONENT_NAME,
                                                    "registrationPreparer",
                                                    ProxyPreparer.class,
                                                    new BasicProxyPreparer());
        ProxyPreparer serviceLeasePreparer = config.getEntry
                                                   (COMPONENT_NAME,
                                                    "serviceLeasePreparer",
                                                    ProxyPreparer.class,
                                                    new BasicProxyPreparer());
        /* Task manager */
        ExecutorService taskMgr;
        try {
            taskMgr = config.getEntry(COMPONENT_NAME,
                                       "executorService",
                                       ExecutorService.class);
        } catch(NoSuchEntryException e) { /* use default */
            taskMgr = new ThreadPoolExecutor(
                MAX_N_TASKS, 
                MAX_N_TASKS, /* Ignored */
                15,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue(), /* Unbounded Queue */
                new NamedThreadFactory("JoinManager executor thread", false)
            );
        }
        /* Wakeup manager */
        WakeupManager wakeupMgr;
        try {
            wakeupMgr = config.getEntry(COMPONENT_NAME,
                                                       "wakeupManager",
                                                       WakeupManager.class);
        } catch(NoSuchEntryException e) { /* use default */
            wakeupMgr = new WakeupManager
                                    (new WakeupManager.ThreadDesc(null,true));
        }
        /* Max number of times to re-schedule tasks in thru wakeup manager */
        int maxNRetries = (config.getEntry
                                        (COMPONENT_NAME,
                                         "wakeupRetries",
                                         int.class,
                                         Integer.valueOf(6))).intValue();
        /* Lease renewal manager */
	if(leaseMgr == null) {
            try {
                leaseMgr = config.getEntry
                                                  (COMPONENT_NAME,
                                                   "leaseManager",
                                                   LeaseRenewalManager.class);
            } catch(NoSuchEntryException e) { /* use default */
                leaseMgr = new LeaseRenewalManager(config);
            }
        }//endif
        long renewalDuration = (config.getEntry
                                      (COMPONENT_NAME,
                                       "maxLeaseDuration",
                                       long.class,
                                       Long.valueOf(Lease.FOREVER))).longValue();
        if( (renewalDuration == 0) || (renewalDuration < Lease.ANY) ) {
            throw new ConfigurationException("invalid configuration entry: "
                                             +"renewalDuration ("
                                             +renewalDuration+") must be "
                                             +"positive or Lease.ANY");
        }//endif
        /* Discovery manager */
        boolean bCreateDiscMgr = false;
	if(discoveryMgr == null) {
	    bCreateDiscMgr = true;
            try {
                discoveryMgr = config.getEntry
                                                 (COMPONENT_NAME,
                                                  "discoveryManager",
                                                  DiscoveryManagement.class);
            } catch(NoSuchEntryException e) { /* use default */
                discoveryMgr = new LookupDiscoveryManager
                                     (new String[] {""}, null, null, config);
            }
	}//endif
        return new Conf(registrarPreparer, registrationPreparer, serviceLeasePreparer,
                taskMgr, wakeupMgr, maxNRetries, leaseMgr, renewalDuration,
                discoveryMgr, bCreateDiscMgr);
    }
    
    /** Convenience method invoked by the constructors of this class that
     *  uses the given <code>Configuration</code> to initialize the current
     *  instance of this utility, and initiates all join processing for
     *  the given parameters. This method handles the various configurations
     *  allowed by the different constructors.
     */
    private JoinManager(Object serviceProxy,
                                   Entry[] attrSets, ServiceID serviceID, 
                                   ServiceIDListener callback, Conf conf)
    {
	registrarPreparer = conf.registrarPreparer;
        registrationPreparer = conf.registrationPreparer;
        serviceLeasePreparer = conf.serviceLeasePreparer;
        executor = new ExtensibleExecutorService(conf.executorService, 
                new RunnableFutureFactory(){

            @Override
            public <T> RunnableFuture<T> newTaskFor(Runnable r, T value) {
                if (r instanceof ProxyRegTask) return (RunnableFuture<T>) r;
                throw new IllegalStateException("Runnable not instance of ProxyRegTask");
            }

            @Override
            public <T> RunnableFuture<T> newTaskFor(Callable<T> c) {
                if (c instanceof ProxyRegTask) return (RunnableFuture<T>) c;
                throw new IllegalStateException("Callable not instance of ProxyRegTask");
            }
            
        });
        proxyRegTaskQueue = new ProxyRegTaskQueue(executor);
        wakeupMgr = conf.wakeupManager;
        maxNRetries = conf.maxNretrys;
        leaseRenewalMgr = conf.leaseRenewalManager;
        renewalDuration = conf.renewalDuration;
        bCreateDiscMgr = conf.bcreateDisco;
        DiscMgrListener discMgrListen = new DiscMgrListener();
        if(attrSets == null) {
            lookupAttr = new Entry[0];
        } else {
            attrSets = attrSets.clone();
            LookupAttributes.check(attrSets,false);//null elements NOT ok
            lookupAttr = attrSets;
        }//endif
	serviceItem = new ServiceItem(serviceID, serviceProxy, lookupAttr);
	this.callback = callback;
	conf.discoveryMgr.addDiscoveryListener(discMgrListen);
        discMgr = conf.discoveryMgr;
        discMgrListener = discMgrListen;
    }//end createJoinManager

    /** For the given lookup service proxy, searches the <code>joinSet</code>
     *  for the corresponding <code>ProxyReg</code> element, and upon finding
     *  such an element, returns that element; otherwise returns
     *  <code>null</code>.
     */
    private ProxyReg findReg(ServiceRegistrar proxy) {
	for (Iterator iter = joinSet.iterator(); iter.hasNext(); ) {
	    ProxyReg reg =(ProxyReg)iter.next();
	    if(reg.proxy.equals(proxy))  return reg;
	}//end loop
	return null;
    }//end findReg

    /** Removes (from the task manager) and cancels (in the wakeup manager)
     *  all tasks associated with the given instance of <code>ProxyReg</code>.
     */
    private void removeTasks(ProxyReg proxyReg) {
        if(proxyReg == null) return;
        if(executor == null) return;
        synchronized(proxyReg.taskList) {
            if(proxyReg.proxyRegTask != null) {
                proxyReg.proxyRegTask.cancel(false);                
                proxyReg.proxyRegTask = null;  //don't reuse because of seq#
            }//endif
            proxyReg.taskList.clear();
        }//end sync(proxyReg.taskList)
        proxyReg.terminate();
    }//end removeTasks

    /** Removes from the task manager, all pending tasks regardless of the
     *  the instance of <code>ProxyReg</code> with which the task is
     *  associated, and then terminates the task manager, and makes it
     *  a candidate for garbage collection.
     */
    private void terminateTaskMgr() {
        synchronized(wakeupMgr) {
            /* Cancel all tasks scheduled for future retry by the wakeup mgr */
            wakeupMgr.cancelAll();//cancel all tickets
            wakeupMgr.stop();//stop execution of the wakeup manager
        }
        /* Interrupt all active tasks, prepare taskMgr for GC. */
        executor.shutdownNow();
    }//end terminateTaskMgr

    /** Examines the elements of the input set and, upon finding at least one
     *  <code>null</code> element, throws a <code>NullPointerException</code>.
     */
    private void testForNullElement(Object[] a) {
        if(a == null) return;
        int l = a.length;
        for(int i=0;i<l;i++) {
            if(a[i] == null) {
                throw new NullPointerException
                          ("input array contains at least one null element");
            }//endif
        }//end loop
    }//end testForNullElement

    /** Convenience method invoked by either form of the method
     *  <code>replaceRegistration</code>. This method registers the
     *  given <code>serviceProxy</code> with all discovered lookup
     *  services, replacing all current registrations. If the value
     *  of the <code>doAttrs</code> parameter is <code>true</code>,
     *  this method will associate the given <code>attrSets</code>
     *  with the new service registration; otherwise, it will use
     *  the attribute sets currently associated with the old registration.
     */
    private void replaceRegistrationDo(Object serviceProxy,
                                       Entry[] attrSets,
                                       boolean doAttrs)
    {
        if(bTerminated) 
            throw new IllegalStateException("join manager was terminated");
	if(!(serviceProxy instanceof java.io.Serializable)) {
            throw new IllegalArgumentException
                                        ("serviceProxy must be Serializable");
	}//endif
	synchronized(this) {
            if(doAttrs) {
                if(attrSets == null) {
                    lookupAttr = new Entry[0];
                } else {
                    attrSets = (Entry[])attrSets.clone();
                    LookupAttributes.check(attrSets,false);//no null elements
                    lookupAttr = attrSets;
                }//endif
            }//endif
            serviceItem = new ServiceItem(serviceItem.serviceID, serviceProxy, lookupAttr);
        }
        Iterator<ProxyReg> it = joinSet.iterator();
        while (it.hasNext()){
            ProxyReg proxyReg = it.next();
            removeTasks(proxyReg);
            try {
                leaseRenewalMgr.remove( proxyReg.serviceLease );
            } catch (UnknownLeaseException e) { }
            proxyReg.addTask(new RegisterTask(proxyReg,
                                                 (Entry[])lookupAttr.clone()));
        }//end loop
    }//end replaceRegistrationDo

}//end class JoinManager

