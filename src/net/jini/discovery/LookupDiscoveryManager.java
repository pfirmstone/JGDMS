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
import org.apache.river.logging.Levels;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;
import net.jini.core.discovery.LookupLocator;

/** 
 * This class is a helper utility class that organizes and manages all
 * discovery-related activities on behalf of a client or service. Rather
 * than providing its own facility for coordinating and maintaining
 * all of the necessary state information related to group names,
 * {@link LookupLocator} objects, and {@link DiscoveryListener}
 * objects, clients and services can employ this class to provide those
 * facilities on their behalf.
 * <p>
 *
 * @org.apache.river.impl <!-- Implementation Specifics -->
 *
 * The following implementation-specific items are discussed below:
 * <ul><li> <a href="#ldmConfigEntries">Configuring LookupDiscoveryManager</a>
 *     <li> <a href="#ldmLogging">Logging</a>
 * </ul>
 *
 * <a name="ldmConfigEntries"><b>Configuring LookupDiscoveryManager</b></a>
 * <p>
 * Currently, there are no configuration entries directly supported by this
 * implementation of <code>LookupDiscoveryManager</code>. All configuration
 * entries affecting the operation of this utility are retrieved by either
 * the {@link LookupDiscovery} utility, or the {@link LookupLocatorDiscovery}
 * utility. Please refer to the documentation provided with those utilities
 * when configuring the behavior of <code>LookupDiscoveryManager</code>.
 * </p>
 * <a name="ldmLogging"><b>Logging</b></a>
 * <p>
 * With one exception, all logging information produced
 * when using this utility is controlled by the loggers supported by the
 * following utilities:
 * </p>
 * <ul>
 *  <li> {@link LookupDiscovery} 
 *  <li> {@link LookupLocatorDiscovery} 
 * </ul>
 * <p>
 * This implementation of <code>LookupDiscoveryManager</code> uses the {@link Logger}
 * named <code>net.jini.discovery.LookupDiscoveryManager</code> to log information
 * at the following logging levels: </p>
 * 
 * <table border="1" cellpadding="5"
 *       summary="Describes the information logged by LookupDiscoveryManager, and
 *                 the levels at which that information is logged">
 * 
 * <caption>
 *   <b><code>net.jini.discovery.LookupDiscoveryManager</code></b>
 * </caption>
 *
 * <tr> <th scope="col"> Level</th>
 *      <th scope="col"> Description</th>
 * </tr>
 * 
 * <tr>
 *   <td>{@link Levels#HANDLED HANDLED}</td>
 *   <td>
 *     when this utility asynchronously invokes a {@link net.jini.discovery.DiscoveryListener}
 *     implementation and that listener throws and unchecked exception. If the listener throws
 *     in a synchronous path (namely, via {@link #addDiscoveryListener(DiscoveryListener)}) then
 *     the exception is not trapped and will instead throw back to the caller.
 *   </td>
 * </tr>
 * </table>
 * <p>
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.discovery.DiscoveryManagement
 * @see net.jini.discovery.DiscoveryGroupManagement
 * @see net.jini.discovery.DiscoveryLocatorManagement
 * 
 */
public final class LookupDiscoveryManager extends AbstractLookupDiscoveryManager 
                                    implements DiscoveryManagement,
                                               DiscoveryGroupManagement,
                                               DiscoveryLocatorManagement
{

    /** 
     * Constructs an instance of this class that will organize and manage 
     * all discovery-related activities on behalf of the client or service
     * that instantiates this class.
     * <p>
     * If <code>null</code> (<code>DiscoveryGroupManagement.ALL_GROUPS</code>)
     * is input to the <code>groups</code> parameter, then attempts will be
     * made via group discovery to discover all lookup services located within
     * range of the entity that constructs this class. If the empty array
     * (<code>DiscoveryGroupManagement.NO_GROUPS</code>) is input to that
     * parameter, no group discovery will be performed until the set of
     * groups to discover is populated.
     * <p>
     * If an empty array or a <code>null</code> reference is input to the
     * <code>locators</code> parameter, no locator discovery will be performed
     * until the set of locators to discover is populated.
     *
     * @param groups   <code>String</code> array, none of whose elements may
     *                 be <code>null</code>, consisting of the names of the
     *                 groups whose members are lookup services the client
     *                 or service wishes to discover.
     * @param locators array of instances of <code>LookupLocator</code>, none
     *                 of whose elements may be <code>null</code>, and in
     *                 which each element corresponds to a specific lookup
     *                 service the client or service wishes to discover via
     *                 locator discovery.
     * @param listener a reference to <code>DiscoveryListener</code> object
     *                 that will be notified when a targeted lookup service
     *                 is discovered or discarded.
     *
     * @throws java.io.IOException because construction of this class may
     *         initiate the discovery process, which can throw an
     *         <code>IOException</code> when socket allocation occurs.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either one or more of the elements of the <code>groups</code>
     *         parameter is <code>null</code>, or one or more elements of
     *         the <code>locators</code> parameter is null.
     *
     * @see net.jini.core.discovery.LookupLocator
     * @see net.jini.discovery.DiscoveryListener
     */
    public LookupDiscoveryManager(String[] groups,  
				  LookupLocator[] locators,
				  DiscoveryListener listener)
                                                           throws IOException
    {
        // Safe construction idiom, exception is thrown prior to Object default
        // constructor being called, so is safe from finalizer attack.
        super(listener, getLookupDiscovery(groups), getLookupLocatorDiscovery(locators));
        beginDiscovery();
    }//end constructor

    private static LookupDiscovery getLookupDiscovery(String [] groups) 
            throws IOException {
        try {
            return new LookupDiscovery(groups, EmptyConfiguration.INSTANCE);
        } catch (ConfigurationException ex) {
            throw new IOException("EmptyConfiguration caused an exception", ex);
        }
    }
    
    private static LookupLocatorDiscovery getLookupLocatorDiscovery(LookupLocator[] locators) throws IOException{
        try {
            return new LookupLocatorDiscovery(locators, EmptyConfiguration.INSTANCE);
        } catch (ConfigurationException ex) {
            throw new IOException("EmptyConfiguration caused an exception", ex);
        }
    }
    
    /** 
     * Constructs an instance of this class, using the given 
     * <code>Configuration</code>, that will organize and manage all
     * discovery-related activities on behalf of the client or service
     * that instantiates this class.
     * <p>
     * If <code>null</code> (<code>DiscoveryGroupManagement.ALL_GROUPS</code>)
     * is input to the <code>groups</code> parameter, then attempts will be
     * made via group discovery to discover all lookup services located within
     * range of the entity that constructs this class. If the empty array
     * (<code>DiscoveryGroupManagement.NO_GROUPS</code>) is input to that
     * parameter, no group discovery will be performed until the set of
     * groups to discover is populated.
     * <p>
     * If an empty array or a <code>null</code> reference is input to the
     * <code>locators</code> parameter, no locator discovery will be performed
     * until the set of locators to discover is populated.
     *
     * @param groups   <code>String</code> array, none of whose elements may
     *                 be <code>null</code>, consisting of the names of the
     *                 groups whose members are lookup services the client
     *                 or service wishes to discover.
     * @param locators array of instances of <code>LookupLocator</code>, none
     *                 of whose elements may be <code>null</code>, and in
     *                 which each element corresponds to a specific lookup
     *                 service the client or service wishes to discover via
     *                 locator discovery.
     * @param listener a reference to <code>DiscoveryListener</code> object
     *                 that will be notified when a targeted lookup service
     *                 is discovered or discarded.
     *
     * @param config   an instance of <code>Configuration</code>, used to
     *                 obtain the objects needed to configure the current
     *                 instance of this class
     *
     * @throws java.io.IOException because construction of this class may
     *         initiate the discovery process, which can throw an
     *         <code>IOException</code> when socket allocation occurs.
     *
     * @throws net.jini.config.ConfigurationException indicates an exception
     *         occurred while retrieving an item from the given
     *         <code>Configuration</code>
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either one or more of the elements of the <code>groups</code>
     *         parameter is <code>null</code>, or one or more elements of
     *         the <code>locators</code> parameter is <code>null</code>, or
     *         when <code>null</code> is input for the configuration.
     *
     * @see net.jini.core.discovery.LookupLocator
     * @see net.jini.discovery.DiscoveryListener
     * @see net.jini.config.Configuration
     */
    public LookupDiscoveryManager(String[] groups,  
				  LookupLocator[] locators,
				  DiscoveryListener listener,
                                  Configuration config)
                                    throws IOException, ConfigurationException
    {
        // Safe construction idiom, exception is thrown prior to Object default
        // constructor being called, so is safe from finalizer attack.
        super(listener, new LookupDiscovery(groups,config), new LookupLocatorDiscovery(locators,config));
        beginDiscovery();
    }//end constructor
    
}//end class LookupDiscoveryManager
