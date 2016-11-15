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

import java.util.logging.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.discovery.LookupLocator;
import org.apache.river.thread.NamedThreadFactory;

/**
 * This class encapsulates the functionality required of an entity that
 * wishes to employ the unicast discovery protocol to discover a lookup
 * service. This utility provides an implementation that makes the process
 * of finding specific lookup services much simpler for both services and
 * clients.
 * <p>
 * Because this class participates in only the unicast discovery protocol,
 * and because the unicast discovery protocol imposes no restriction on the
 * physical location of the entity relative to a lookup service, this utility
 * can be used to discover lookup services running on hosts that are located
 * far from, or near to, the host on which the entity is running. This lack
 * of a restriction on location brings with it a requirement that the
 * discovering entity supply this class with specific information about the
 * desired lookup services; namely, the location of the device(s) hosting
 * each lookup service. This information is supplied through an instance
 * of the {@link net.jini.core.discovery.LookupLocator LookupLocator} class,
 * or its subclass {@link net.jini.discovery.ConstrainableLookupLocator
 * ConstrainableLookupLocator}.
 *
 * @org.apache.river.impl <!-- Implementation Specifics -->
 *
 * The following implementation-specific items are discussed below:
 * <ul><li> <a href="#lldConfigEntries">Configuring LookupLocatorDiscovery</a>
 *     <li> <a href="#lldLogging">Logging</a>
 * </ul>
 *
 * <a name="lldConfigEntries"><b>Configuring LookupLocatorDiscovery</b></a>
 *
 * This implementation of <code>LookupLocatorDiscovery</code> supports the
 * following configuration entries; where each configuration entry name
 * is associated with the component name
 * <code>net.jini.discovery.LookupLocatorDiscovery</code>. Note that the
 * configuration entries specified here are specific to this implementation
 * of <code>LookupLocatorDiscovery</code>. Unless otherwise stated, each
 * entry is retrieved from the configuration only once per instance of
 * this utility, where each such retrieval is performed in the constructor.
 *
 * <a name="initialUnicastDelayRange"></a>
 * <table summary="Describes the initialUnicastDelayRange
 *                configuration entry" border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2">
 *     <code>initialUnicastDelayRange</code>
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
 *       how long to wait before attempting unicast discovery.
 *       If the value is positive, initial unicast discovery requests
 *       will be delayed by a random value between <code>0</code> and
 *       <code>initialUnicastDelayRange</code> milliseconds. Once the wait
 *       period is up, the <code>LookupLocator</code>s specified at construction
 *       time are used for initiating unicast discovery requests, unless the
 *       managed <code>LookupLocator</code>s have been changed in the interim;
 *       in which case, no delayed unicast discovery requests are performed.
 *       Note that this entry only has effect when this utility is initialized.
 *       It does not delay discovery requests that are initiated if the managed
 *       <code>LookupLocator</code>s are subsequently changed.
 * </table>
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
 *          Currently, none of the methods on the
 *          {@link net.jini.core.lookup.ServiceRegistrar ServiceRegistrar}
 *          returned by this preparer are invoked by this implementation of
 *          <code>LookupLocatorDiscovery</code>.
 * </table>
 * 
 * <a name="executorService"></a>
 * <table summary="Describes the executorService configuration entry" 
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
 *             {@link ThreadPoolExecutor}(
 *                       15,
 *                       15,
 *                       15,
 *                       TimeUnit.SECONDS,
 *                       new {@link LinkedBlockingQueue}(),
 *                       new {@link NamedThreadFactory}("LookupLocatorDiscovery", false)
 *                   )</code>
 * 
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description:
 *       <td> The object that pools and manages the various threads
 *            executed by this utility. This object
 *            should not be shared with other components in the
 *            application that employs this utility.
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
 *       <td> Object that pools and manages the various tasks that are
 *            initially executed by the object corresponding to the
 *            <a href="#executorService"><code>executorService</code></a> entry
 *            of this component, but which fail during that initial execution.
 *            This object schedules the re-execution of such a failed task -
 *            in the <a href="#executorService"><code>executorService</code></a>
 *            object - at various times in the future, (employing a
 *            "backoff strategy"). The re-execution of the failed task will
 *            continue to be scheduled by this object until the task finally
 *            succeeds. This object should not be shared with other components
 *            in the application that employs this utility.
 * </table>
 *
 * <a name="lldLogging"><b>Logging</b></a>
 *<p>
 * This implementation of <code>LookupLocatorDiscovery</code> uses the
 * {@link Logger} named <code>net.jini.discovery.LookupLocatorDiscovery</code>
 * to log information at the following logging levels: </p>
 *
 * <table border="1" cellpadding="5"
 *         summary="Describes the information logged by LookupLocatorDiscovery,
 *                 and the levels at which that information is logged">
 *
 * <caption>
 *   <b><code>net.jini.discovery.LookupLocatorDiscovery</code></b>
 * </caption>
 *
 * <tr> <th scope="col"> Level</th>
 *      <th scope="col"> Description</th>
 * </tr>
 *
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>
 *     when any exception occurs in a task or thread, while attempting unicast
 *     discovery of a given locator
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#INFO INFO}</td>
 *   <td>when any exception occurs while attempting to prepare a proxy</td>
 * </tr>
 * <tr>
 *   <td>{@link org.apache.river.logging.Levels#HANDLED HANDLED}</td>
 *   <td>
 *     when an exception is handled during unicast discovery.
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>whenever any thread or task is started</td>
 * </tr>
 *
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>
 *     whenever any thread (except the <code>Notifier</code> thread) or task
 *     completes successfully
 *   </td>
 * </tr>
 *
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>whenever a discovered or discarded event is sent</td>
 * </tr>
 *
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>whenever a proxy is prepared</td>
 * </tr>
 *
 * <tr>
 *   <td>{@link java.util.logging.Level#FINEST FINEST}</td>
 *   <td>
 *     when an <code>IOException</code> occurs upon attempting to close the
 *     socket after a unicast discovery attempt has either completed
 *     successfully or failed
 *   </td>
 * </tr>
 * </table>
 * <p>
 *
 * This implementation of <code>LookupLocatorDiscovery</code> determines
 * the constraints (if any) to apply to unicast discovery for a given
 * {@link net.jini.core.discovery.LookupLocator LookupLocator} instance
 * by calling the 
 * {@link net.jini.core.constraint.RemoteMethodControl#getConstraints
 * getConstraints} method of that instance, if it implements the
 * {@link net.jini.core.constraint.RemoteMethodControl RemoteMethodControl}
 * interface. If the {@link net.jini.core.discovery.LookupLocator
 * LookupLocator} instance does not implement
 * {@link net.jini.core.constraint.RemoteMethodControl RemoteMethodControl},
 * then no constraints are applied to unicast discovery for that instance.
 * <p>
 * For more information on constraining unicast discovery, refer to the
 * documentation for the {@link net.jini.discovery.ConstrainableLookupLocator
 * ConstrainableLookupLocator} class.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.discovery.LookupLocator
 */
public final class LookupLocatorDiscovery extends AbstractLookupLocatorDiscovery 
                                    implements DiscoveryManagement,
                                               DiscoveryLocatorManagement
{
    /**
     * Creates an instance of this class (<code>LookupLocatorDiscovery</code>),
     * with an initial array of <code>LookupLocator</code>s to be managed.
     * For each managed <code>LookupLocator</code>, unicast discovery is
     * performed to obtain a <code>ServiceRegistrar</code> proxy for that
     * lookup service.
     * 
     * @param locators the locators to discover
     * 
     * @throws java.lang.NullPointerException input array contains at least
     *         one <code>null</code> element
     */
    public LookupLocatorDiscovery(LookupLocator[] locators) {
        super();
        super.beginDiscovery(locators);
    }//end constructor

    /**
     * Constructs a new lookup locator discovery object, set to discover the
     * given set of locators, and having the given <code>Configuration</code>.
     * <p>
     * For each managed <code>LookupLocator</code>, unicast discovery is
     * performed to obtain a <code>ServiceRegistrar</code> proxy for that
     * lookup service.
     * 
     * @param locators the locators to discover
     *
     * @param config   an instance of <code>Configuration</code>, used to
     *                 obtain the objects needed to configure the current
     *                 instance of this class
     *
     * @throws net.jini.config.ConfigurationException indicates an exception
     *         occurred while retrieving an item from the given
     *         <code>Configuration</code>
     * 
     * @throws java.lang.NullPointerException input array contains at least
     *         one <code>null</code> element or <code>null</code> is input
     *         for the configuration
     */
    public LookupLocatorDiscovery(LookupLocator[] locators,
                                  Configuration config)
                                                throws ConfigurationException
    {
        super(config);
        super.beginDiscovery(locators);
    }//end constructor

}//end class LookupLocatorDiscovery
