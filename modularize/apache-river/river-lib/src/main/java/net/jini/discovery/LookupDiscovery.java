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
package net.jini.discovery;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.constraint.MethodConstraints;

/**
 * This class is a helper utility class that encapsulates the functionality 
 * required of an entity that wishes to employ multicast discovery to 
 * find lookup services located within the entity's "multicast radius" 
 * (roughly, the number of hops beyond which neither the multicast requests
 * from the entity, nor the multicast announcements from the lookup service,
 * will propagate). This class helps make the process of acquiring references
 * to lookup services - based on no information other than lookup service
 * group membership - much simpler for both services and clients.
 *
 * @org.apache.river.impl <!-- Implementation Specifics -->
 *
 * The following implementation-specific items are discussed below:
 * <ul><li> <a href="#ldConfigEntries">Configuring LookupDiscovery</a>
 *     <li> <a href="#ldLogging">Logging</a>
 * </ul>
 *
 * <a name="ldConfigEntries"><b>Configuring LookupDiscovery</b></a>
 * 
 * This implementation of <code>LookupDiscovery</code> supports the
 * following configuration entries; where each configuration entry name
 * is associated with the component name
 * <code>net.jini.discovery.LookupDiscovery</code>. Note that the
 * configuration entries specified here are specific to this implementation
 * of <code>LookupDiscovery</code>. Unless otherwise stated, each entry
 * is retrieved from the configuration only once per instance of this
 * utility, where each such retrieval is performed in the constructor.
 *
 * <a name="discoveryConstraints"></a>
 * <table summary="Describes the discoveryConstraints
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>discoveryConstraints</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> {@link net.jini.core.constraint.MethodConstraints}
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>null</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> Constraints to apply to the multicast request, multicast
 *         	announcement and unicast discovery protocols.  Multicast
 *         	request constraints are derived by calling
 *         	{@link MethodConstraints#getConstraints getConstraints} on the
 *         	obtained <code>MethodConstraints</code> instance with a
 *         	<code>Method</code> object for the
 *    {@link org.apache.river.discovery.DiscoveryConstraints#multicastRequest
 *              multicastRequest} method; multicast announcement and unicast
 *              discovery constraints are similarly obtained by passing
 *              <code>Method</code> objects for the
 *    {@link org.apache.river.discovery.DiscoveryConstraints#multicastAnnouncement
 *              multicastAnnouncement} and
 *    {@link org.apache.river.discovery.DiscoveryConstraints#unicastDiscovery
 *              unicastDiscovery} methods, respectively.  A <code>null</code>
 *              value is interpreted as	mapping all methods to empty
 *              constraints.
 *         	<p>
 *         	This class supports the use of the following constraint types
 *              to control discovery behavior:
 *         	<ul>
 *         	  <li> {@link org.apache.river.discovery.DiscoveryProtocolVersion}:
 *         	       this constraint can be used to control which version(s)
 *         	       of the multicast request, multicast announcement and
 *         	       unicast discovery protocols are used.
 *         	  <li> {@link org.apache.river.discovery.MulticastMaxPacketSize}:
 *         	       this constraint can be used to control the maximum size
 *         	       of multicast request packets to send; it can also be
 *         	       used to specify the size of the buffer used to receive
 *         	       incoming multicast announcement packets.
 *         	  <li> {@link org.apache.river.discovery.MulticastTimeToLive}: this
 *         	       constraint can be used to control the time to live (TTL)
 *         	       value set on outgoing multicast request packets.
 *         	  <li> {@link org.apache.river.discovery.UnicastSocketTimeout}:
 *         	       this constraint can be used to control the read timeout
 *         	       set on sockets over which unicast discovery is
 *         	       performed.
 *         	  <li> {@link net.jini.core.constraint.ConnectionRelativeTime}:
 *         	       this constraint can be used to control the relative
 *                     connection timeout set on sockets over which unicast
 *                     discovery is performed.
 *          	  <li> {@link net.jini.core.constraint.ConnectionAbsoluteTime}:
 *         	       this constraint can be used to control the absolute
 *                     connection timeout set on sockets over which unicast
 *                     discovery is performed.
 *         	</ul>
 *         	Constraints other than those listed above are passed on to the
 *         	underlying implementations of versions 1 and 2 of the discovery
 *              protocols.
 * </table>
 * 
 * <a name="finalMulticastRequestInterval"></a>
 * <table summary="Describes the finalMulticastRequestInterval
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>finalMulticastRequestInterval</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>long</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>2*60*1000 (2 minutes)</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> With respect to when this utility is started, as well
 *            as when the set of groups to discover is changed, this
 *            entry represents the number of milliseconds to wait
 *            after sending the n-th multicast request where n is
 *            equal to the value of the <a href="#multicastRequestMax">
 *            <code>multicastRequestMax</code></a> entry of this component.
 * </table>
 * 
 * <a name="initialMulticastRequestDelayRange"></a>
 * <table summary="Describes the initialMulticastRequestDelayRange
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>initialMulticastRequestDelayRange</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>long</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>0 milliseconds</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> With respect to when this utility is started, this entry controls
 *       how long to wait before sending out the first multicast request.
 *       If the value is positive, the first request will be delayed by a random
 *       value between <code>0</code> and
 *       <code>initialMulticastRequestDelayRange</code>
 *       milliseconds. Subsequent request intervals are controlled by the
 *       <a href="#multicastRequestInterval">
 *       <code>multicastRequestInterval</code></a> entry. Note that this entry
 *       only has effect when this utility is initialized. The first multicast
 *       request is not delayed if the groups to discover are subsequently
 *       changed.
 * </table>
 * 
 * <a name="multicastAnnouncementInterval"></a>
 * <table summary="Describes the multicastAnnouncementInterval
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>multicastAnnouncementInterval</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>long</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>2*60*1000 (2 minutes)</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> A lookup service will send out multicast packets
 *            announcing its existence every N milliseconds; for some
 *            value of N. The value of this entry controls how often
 *            this utility examines the multicast announcements from
 *            previously discovered lookup services for <i>liveness</i>.
 * </table>
 * 
 * <a name="multicastInterfaceRetryInterval"></a>
 * <table summary="Describes the multicastInterfaceRetryInterval
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>multicastInterfaceRetryInterval</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>int</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>5*60*1000 (5 minutes)</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> With respect to any network interface this utility is configured
 *            to use to send and receive multicast packets (see entry  
 *            <a href="#multicastInterfaces">
 *            <code>multicastInterfaces</code></a>), if failure is encountered
 *            upon the initial attempt to set the interface or join the
 *            desired multicast group, this utility will retry the failed
 *            interface every <a href="#multicastInterfaceRetryInterval">
 *            <code>multicastInterfaceRetryInterval</code></a> milliseconds
 *            until success is encountered.
 * </table>
 * 
 * <a name="multicastInterfaces"></a>
 * <table summary="Describes the multicastInterfaces
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>multicastInterfaces</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> {@link java.net.NetworkInterface NetworkInterface[]}
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>new </code>
 *              {@link java.net.NetworkInterface NetworkInterface[]}
 *                     &nbsp; <code>{all currently supported interfaces}</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> Each network interface that is represented by an element
 *            in the array corresponding to this configuration item
 *            will be used to send and receive multicast packets when
 *            this utility is participating in the multicast discovery
 *            process. When not set, this utility will use all of the
 *            network interfaces in the system. When this entry is set
 *            to a zero length array, multicast discovery is effectively
 *            <b><i>disabled</i></b>. And when set to <code>null</code>,
 *            the interface to which the operating system defaults will be
 *            used.
 * </table>
 * 
 * <a name="multicastRequestHost"></a>
 * <table summary="Describes the multicastRequestHost
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>multicastRequestHost</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>String</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> 
 *    <code>{@link java.net.InetAddress}.getLocalHost().getHostAddress()</code>
 *
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> This entry specifies the host name to include in multicast
 *            requests if participating in <b><i>version 2</i></b> of the
 *            multicast request protocol. The name cannot be <code>null</code>.
 * </table>
 * 
 * <a name="multicastRequestInterval"></a>
 * <table summary="Describes the multicastRequestInterval
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>multicastRequestInterval</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>long</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>5000</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *     <td> With respect to when this utility is started, as well as
 *          when the set of groups to discover is changed, this entry
 *          represents the number of milliseconds to wait after
 *          sending the n-th multicast request, and before sending
 *          the (n+1)-st request, where n is less than the value of the
 *          <a href="#multicastRequestMax"><code>multicastRequestMax</code></a>
 *          entry of this component.
 * </table>
 * 
 * <a name="multicastRequestMax"></a>
 * <table summary="Describes the multicastRequestMax
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>multicastRequestMax</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>int</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>7</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> The maximum number multicast requests to send when this
 *            utility is started for the first time, and whenever the
 *            groups to discover is changed.
 * </table>
 * 
 * <a name="registrarPreparer"></a>
 * <table summary="Describes the registrarPreparer configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>registrarPreparer</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Type: <td> {@link net.jini.security.ProxyPreparer}
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Default: <td> <code>new {@link net.jini.security.BasicProxyPreparer}()
 *                     </code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *   Description:
 *     <td> Preparer for the proxies to the lookup services that are
 *          discovered and used by this utility. 
 *          <p>
 *          This preparer should perform all operations required to use a
 *          newly received proxy to a lookup service, which may including
 *          verifying trust in the proxy, granting permissions, and setting
 *          constraints.
 *          <p>
 *          The following methods of the 
 *          {@link net.jini.core.lookup.ServiceRegistrar ServiceRegistrar}
 *          returned by this preparer are invoked by this implementation of
 *          <code>LookupDiscovery</code>:
 *       <ul>
 *         <li>{@link net.jini.core.lookup.ServiceRegistrar#getServiceID
 *                                                           getServiceID}
 *         <li>{@link net.jini.core.lookup.ServiceRegistrar#getGroups
 *                                                           getGroups}
 *         <li>{@link net.jini.core.lookup.ServiceRegistrar#getLocator
 *                                                           getLocator}
 *       </ul>
 * </table>
 * 
 * <a name="executorService"></a>
 * <table summary="Describes the taskManager configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>executorService</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> {@link ExecutorService}
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>new 
 *             {@link ThreadPoolExecutor
 *              ThreadPoolExecutor}(
 *                      15, 
 *                      15, 
 *                      15, 
 *                      TimeUnit.SECONDS,
 *                      new LinkedBlockingQueue(),
 *                      new NamedThreadFactory("LookupDiscovery",false))</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> The object that pools and manages the various threads
 *            executed by this utility. This object
 *            should not be shared with other components in the
 *            application that employs this utility.
 * </table>
 * 
 * <a name="unicastDelayRange"></a>
 * <table summary="Describes the unicastDelayRange
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>unicastDelayRange</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>long</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>0 milliseconds</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> Controls how long this utility will wait before sending out
 *       unicast discovery requests. If the value is positive, any
 *       unicast discovery request that it initiates will be delayed by a
 *       random value between <code>0</code> and
 *       <code>unicastDelayRange</code> milliseconds. A typical use of this
 *       entry would be to achieve a more uniform distribution of unicast
 *       discovery requests to a lookup service, when a large number of
 *       <code>LookupDiscovery</code> instances simultaneously receive multicast
 *       announcements from the lookup service.
 * </table>
 * 
 * <a name="wakeupManager"></a>
 * <table summary="Describes the wakeupManager configuration entry" 
 *                border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>wakeupManager</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> {@link org.apache.river.thread.WakeupManager}
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>new 
 *     {@link org.apache.river.thread.WakeupManager#WakeupManager(
 *          org.apache.river.thread.WakeupManager.ThreadDesc)
 *     WakeupManager}(new 
 *     {@link org.apache.river.thread.WakeupManager.ThreadDesc}(null,true))</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> Object used to schedule unicast discovery requests that are
 *       delayed using the
 *       <a href="#unicastDelayRange"><code>unicastDelayRange</code></a>
 *       configuration entry of this utility. This entry is processed only
 *       if <code>unicastDelayRange</code> has a positive value.
 * </table>
 *
 * <a name="ldLogging"><b>Logging</b></a>
 *
 * This implementation of <code>LookupDiscovery</code> uses the {@link Logger}
 * named <code>net.jini.discovery.LookupDiscovery</code> to log information
 * at the following logging levels: <br>
 * 
 * <table border="1" cellpadding="5"
 *       summary="Describes the information logged by LookupDiscovery, and
 *                 the levels at which that information is logged">
 * 
 * <caption><b><code>net.jini.discovery.LookupDiscovery</code></b></caption>
 *
 * <tr> <th scope="col"> Level</th>
 *      <th scope="col"> Description</th>
 * </tr>
 * 
 * <tr>
 *   <td>{@link java.util.logging.Level#SEVERE SEVERE}</td>
 *   <td>
 *     when this utility is configured to use either the default network
 *     interface assigned by the system, or a specific list of network
 *     interfaces, if one of those interfaces is bad or not configured for
 *     multicast, or if a runtime exception occurs while either sending
 *     multicast requests, or while configuring one of the interfaces to
 *     receive multicast announcements, that fact will be logged at this
 *     level
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>
 *     when any exception other than an <code>InterruptedIOException</code>,
 *     <code>InterruptedException</code> or
 *     <code>UnsupportedConstraintException</code> occurs in a thread or task
 *     while attempting to marshal an outgoing multicast request
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>
 *     when any exception other than an <code>InterruptedIOException</code> or
 *     <code>SocketTimeoutException</code> occurs in a non-interrupted thread
 *     while attempting to receive an incoming multicast packet
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>
 *     when any exception other than an <code>InterruptedIOException</code>
 *     occurs while attempting unicast discovery
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>
 *     when this utility is configured to use either the default network
 *     interface assigned by the system, or a specific list of network
 *     interfaces, with respect to any such interface, if failure is
 *     encountered upon the initial attempt to set the interface or join
 *     the desired multicast group, the interface will be periodically
 *     retried, and successful recovery will be logged at this level
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>when any exception occurs while attempting to prepare a proxy</td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#CONFIG CONFIG}</td>
 *   <td>
 *     when the <code>multicastInterfaces</code> entry is configured to 
 *     be <code>null</code>, multicast packets will be sent and received
 *     through the default network interface assigned by the system, and
 *     that fact will be logged at this level
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#CONFIG CONFIG}</td>
 *   <td>
 *     when the <code>multicastInterfaces</code> entry is configured to 
 *     be a zero length array, multicast discovery will be disabled, and
 *     and that fact will be logged at this level
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#CONFIG CONFIG}</td>
 *   <td>
 *     when the <code>multicastInterfaces</code> entry contains a specific
 *     list of network interfaces, multicast packets will be sent and
 *     received through only the network interfaces contained in that list,
 *     and those interfaces will be logged at this level
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#CONFIG CONFIG}</td>
 *   <td>
 *     when the <code>multicastInterfaces</code> entry is excluded from
 *     the configuration, multicast packets will be sent and received
 *     through all interfaces in the system, and those interfaces will
 *     be logged at this level
 *   </td>
 * </tr>
 * <tr>
 *    <td>{@link org.apache.river.logging.Levels#FAILED FAILED}</td>
 *    <td>
 *      when an <code>UnknownHostException</code> occurs while determining
 *      the <code>multicastRequestHost</code>, but the caller does not have
 *      permissions to retrieve the local host name. The original
 *      <code>UnknownHostException</code> with the host name information
 *      is logged
 *    </td>
 * <tr>
 *   <td>{@link org.apache.river.logging.Levels#HANDLED HANDLED}</td>
 *   <td>
 *     when this utility is configured to use all network interfaces enabled
 *     in the system, if one of those interfaces is bad or not configured for
 *     multicast, or if a runtime exception occurs while either sending
 *     multicast requests, or while configuring one of the interfaces to
 *     receive multicast announcements, that fact will be logged at this
 *     level
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link org.apache.river.logging.Levels#HANDLED HANDLED}</td>
 *   <td>
 *     when any exception occurs while attempting to unmarshal an incoming
 *     multicast announcement
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link org.apache.river.logging.Levels#HANDLED HANDLED}</td>
 *   <td>
 *     when an <code>UnsupportedConstraintException</code> occurs while
 *     marshalling an outgoing multicast request, indicating that the provider
 *     that threw the exception will not be used for encoding that request
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link org.apache.river.logging.Levels#HANDLED HANDLED}</td>
 *   <td>
 *     when an <code>IOException</code> occurs upon attempting to close the
 *     socket after the thread that listens for multicast responses is asked
 *     to terminate
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link org.apache.river.logging.Levels#HANDLED HANDLED}</td>
 *   <td>
 *     when an exception is handled during unicast discovery
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#FINE FINE}</td>
 *   <td>
 *     when this utility is configured to use all network interfaces enabled
 *     in the system, with respect to any such interface, if failure is
 *     encountered upon the initial attempt to set the interface or join
 *     the desired multicast group, the interface will be periodically
 *     retried, and successful recovery will be logged at this level
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>whenever any thread or task is started</td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>
 *     whenever any thread (except the <code>Notifier</code> thread) or task
 *     completes successfully
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>whenever a discovered, discarded, or changed event is sent</td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>whenever a proxy is prepared</td>
 * </tr>
 * </table>
 * <p>
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.lookup.ServiceRegistrar
 * @see DiscoveryChangeListener
 * @see DiscoveryManagement
 * @see DiscoveryGroupManagement
 * @see DiscoveryListener
 * @see DiscoveryEvent
 * @see DiscoveryPermission
 */
public final class LookupDiscovery extends AbstractLookupDiscovery 
                             implements DiscoveryManagement,
                                        DiscoveryGroupManagement
{
    /**
     * Construct a new lookup discovery object, set to discover the
     * given set of groups.  The set is represented as an array of
     * strings.  This array may be empty, which is taken as the empty
     * set, and discovery is not performed.  The reference passed in
     * may be null, which is taken as no set, and in which case
     * discovery of all reachable lookup services is performed.
     * Otherwise, the array contains the names of groups to discover.
     * The caller must have DiscoveryPermission for each group (or
     * for all groups, if the array is null).
     *
     * @param groups the set of group names to discover (null for no
     *               set, empty for no discovery)
     *
     * @throws java.lang.NullPointerException input array contains at least
     *         one null element
     *
     * @throws java.io.IOException an exception occurred in starting discovery
     *
     * @see #NO_GROUPS
     * @see #ALL_GROUPS
     * @see #setGroups
     * @see DiscoveryPermission
     */
    public LookupDiscovery(String[] groups) throws IOException {
        super(groups);
        start();
//        try {
//            beginDiscovery(groups, EmptyConfiguration.INSTANCE);
//        } catch(ConfigurationException e) { /* swallow this exception */ }
    }//end constructor

    /**
     * Constructs a new lookup discovery object, set to discover the
     * given set of groups, and having the given <code>Configuration</code>.
     * <p>
     * The set of groups to discover is represented as an array of
     * strings.  This array may be empty, which is taken as the empty
     * set, and discovery is not performed.  The reference passed in
     * may be <code>null</code>, which is taken as no set, and in which
     * case discovery of all reachable lookup services is performed.
     * Otherwise, the array contains the names of groups to discover.
     * The caller must have <code>DiscoveryPermission</code> for each
     * group (or for all groups, if the array is <code>null</code>).
     *
     * @param groups the set of group names to discover (null for no
     *               set, empty for no discovery)
     *
     * @param config an instance of <code>Configuration</code>, used to
     *               obtain the objects needed to configure the current
     *               instance of this class
     *
     * @throws java.lang.NullPointerException input array contains at least
     *         one <code>null</code> element or <code>null</code> is input
     *         for the configuration
     *
     * @throws java.io.IOException an exception occurred in starting discovery
     *
     * @throws net.jini.config.ConfigurationException indicates an exception
     *         occurred while retrieving an item from the given
     *         <code>Configuration</code>
     *
     * @see #NO_GROUPS
     * @see #ALL_GROUPS
     * @see #setGroups
     * @see DiscoveryPermission
     * @see net.jini.config.Configuration
     * @see net.jini.config.ConfigurationException
     */
    public LookupDiscovery(String[] groups, Configuration config)
                                   throws IOException, ConfigurationException
    {
        super(groups, config);
        start();
    }//end constructor
     
}//end class LookupDiscovery
