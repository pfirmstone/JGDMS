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
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.DiscoveryManagement;
import net.jini.lease.LeaseRenewalManager;

/**
 * A goal of any well-behaved service is to advertise the facilities and
 * functions it provides by requesting residency within at least one lookup
 * service. Making such a request of a lookup service is known as registering
 * with, or <i>joining</i>, a lookup service.
 * <p>
 * This class performs all of the functions related to discovery, joining,
 * service lease renewal, and attribute management which is required of a
 * well-behaved service. Each of these activities is intimately involved with
 * the maintenance of a service's residency in one or more lookup services (the
 * service's join state), thus the name <code>JoinManager</code>.
 * <p>
 * For each lookup service with which this class registers the service, a
 * proxy is maintained through which the join processing is coordinated.
 * <p>
 * <b>Implementation.</b> Since release 3.1.1 this class is a thin, delegating
 * <i>facade</i>. Its public constructors and methods are unchanged; each
 * instance holds a {@link JoinManagerSpi} delegate obtained from a
 * {@link DiscoveryProviderFactory} that is resolved once, via
 * {@link org.apache.river.resource.Service}, from the classpath. The join
 * implementation lives in a separate, non-downloadable jar so that this
 * download jar (and hence the proxy code beside it) can be compiled for the
 * widest range of client JVMs. If no {@code DiscoveryProviderFactory} is found,
 * construction throws {@link IllegalStateException}.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.discovery.DiscoveryManagement
 * @see net.jini.lease.LeaseRenewalManager
 * @see net.jini.core.lookup.ServiceRegistrar
 */
public class JoinManager {

    /** The join implementation this facade delegates to. */
    private final JoinManagerSpi impl;

    /**
     * Constructs an instance of this class that will register the given service
     * reference with all discovered lookup services and, through an event sent
     * to the given <code>ServiceIDListener</code> object, communicate the
     * service ID assigned by the first lookup service with which the service is
     * registered.
     *
     * @param serviceProxy the service reference (proxy) to register with all of
     * the desired lookup services.
     * @param attrSets array of <code>Entry</code> consisting of the attribute
     * sets with which to register the service.
     * @param callback reference to the object that should receive the event
     * containing the service ID assigned to the service by the first lookup
     * service with which the service is registered.
     * @param discoveryMgr reference to the <code>DiscoveryManagement</code>
     * object this class should use to manage the service's "join state".
     * @param leaseMgr reference to the <code>LeaseRenewalManager</code> object
     * this class should use to manage the leases the service is granted.
     *
     * @throws java.io.IOException typically, this exception occurs when
     * discovery-related processing cannot be initiated.
     *
     * @see net.jini.lookup.ServiceIDListener
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.lease.LeaseRenewalManager
     */
    public JoinManager(Object serviceProxy,
            Entry[] attrSets,
            ServiceIDListener callback,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr) throws IOException {
        this.impl = ServiceDiscoveryManager.factory().newJoinManager(
                serviceProxy, attrSets, callback, discoveryMgr, leaseMgr);
    }//end constructor

    /**
     * Constructs an instance of this class, configured using the items
     * retrieved through the given <code>Configuration</code> object, that will
     * register the given service reference with all discovered lookup services
     * and, through an event sent to the given <code>ServiceIDListener</code>
     * object, communicate the service ID assigned by the first lookup service
     * with which the service is registered.
     *
     * @param serviceProxy the service reference (proxy) to register with all of
     * the desired lookup services.
     * @param attrSets array of <code>Entry</code> consisting of the attribute
     * sets with which to register the service.
     * @param callback reference to the object that should receive the event
     * containing the service ID assigned to the service by the first lookup
     * service with which the service is registered.
     * @param discoveryMgr reference to the <code>DiscoveryManagement</code>
     * object this class should use to manage the service's "join state".
     * @param leaseMgr reference to the <code>LeaseRenewalManager</code> object
     * this class should use to manage the leases the service is granted.
     * @param config the <code>Configuration</code> object used to configure
     * this instance.
     *
     * @throws java.io.IOException typically, this exception occurs when
     * discovery-related processing cannot be initiated.
     * @throws net.jini.config.ConfigurationException indicates an exception
     * occurred while retrieving an item from the given
     * <code>Configuration</code>.
     *
     * @see net.jini.lookup.ServiceIDListener
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.lease.LeaseRenewalManager
     * @see net.jini.config.Configuration
     */
    public JoinManager(Object serviceProxy,
            Entry[] attrSets,
            ServiceIDListener callback,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException, ConfigurationException {
        this.impl = ServiceDiscoveryManager.factory().newJoinManager(
                serviceProxy, attrSets, callback, discoveryMgr, leaseMgr, config);
    }//end constructor

    /**
     * Constructs an instance of this class that will register the given service
     * reference, having the given service ID, with all discovered lookup
     * services. This constructor is typically used by services which have
     * already been assigned a service ID.
     *
     * @param serviceProxy the service reference (proxy) to register with all of
     * the desired lookup services.
     * @param attrSets array of <code>Entry</code> consisting of the attribute
     * sets with which to register the service.
     * @param serviceID the service ID to associate with the service being
     * registered.
     * @param discoveryMgr reference to the <code>DiscoveryManagement</code>
     * object this class should use to manage the service's "join state".
     * @param leaseMgr reference to the <code>LeaseRenewalManager</code> object
     * this class should use to manage the leases the service is granted.
     *
     * @throws java.io.IOException typically, this exception occurs when
     * discovery-related processing cannot be initiated.
     *
     * @see net.jini.core.lookup.ServiceID
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.lease.LeaseRenewalManager
     */
    public JoinManager(Object serviceProxy,
            Entry[] attrSets,
            ServiceID serviceID,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr) throws IOException {
        this.impl = ServiceDiscoveryManager.factory().newJoinManager(
                serviceProxy, attrSets, serviceID, discoveryMgr, leaseMgr);
    }//end constructor

    /**
     * Constructs an instance of this class, configured using the items
     * retrieved through the given <code>Configuration</code>, that will
     * register the given service reference, having the given service ID, with
     * all discovered lookup services.
     *
     * @param serviceProxy the service reference (proxy) to register with all of
     * the desired lookup services.
     * @param attrSets array of <code>Entry</code> consisting of the attribute
     * sets with which to register the service.
     * @param serviceID the service ID to associate with the service being
     * registered.
     * @param discoveryMgr reference to the <code>DiscoveryManagement</code>
     * object this class should use to manage the service's "join state".
     * @param leaseMgr reference to the <code>LeaseRenewalManager</code> object
     * this class should use to manage the leases the service is granted.
     * @param config the <code>Configuration</code> object used to configure
     * this instance.
     *
     * @throws java.io.IOException typically, this exception occurs when
     * discovery-related processing cannot be initiated.
     * @throws net.jini.config.ConfigurationException indicates an exception
     * occurred while retrieving an item from the given
     * <code>Configuration</code>.
     *
     * @see net.jini.core.lookup.ServiceID
     * @see net.jini.discovery.DiscoveryManagement
     * @see net.jini.lease.LeaseRenewalManager
     * @see net.jini.config.Configuration
     */
    public JoinManager(Object serviceProxy,
            Entry[] attrSets,
            ServiceID serviceID,
            DiscoveryManagement discoveryMgr,
            LeaseRenewalManager leaseMgr,
            Configuration config)
            throws IOException, ConfigurationException {
        this.impl = ServiceDiscoveryManager.factory().newJoinManager(
                serviceProxy, attrSets, serviceID, discoveryMgr, leaseMgr, config);
    }//end constructor

    /**
     * Returns the instance of <code>DiscoveryManagement</code> that was either
     * passed into the constructor, or that was created as a result of
     * <code>null</code> being input to that parameter.
     *
     * @return the instance of <code>DiscoveryManagement</code> used by this
     * class.
     * @see net.jini.discovery.DiscoveryManagement
     */
    public DiscoveryManagement getDiscoveryManager() {
        return impl.getDiscoveryManager();
    }//end getDiscoveryManager

    /**
     * Returns the instance of the <code>LeaseRenewalManager</code> class that
     * was either passed into the constructor, or that was created as a result
     * of <code>null</code> being input to that parameter.
     *
     * @return the instance of the <code>LeaseRenewalManager</code> used by this
     * class.
     * @see net.jini.lease.LeaseRenewalManager
     */
    public LeaseRenewalManager getLeaseRenewalManager() {
        return impl.getLeaseRenewalManager();
    }//end getLeaseRenewalManager

    /**
     * Returns an array containing the references to the current set of lookup
     * services with which the service is registered (joined).
     *
     * @return array of instances of <code>ServiceRegistrar</code>, each
     * corresponding to a lookup service with which the service is currently
     * registered.
     * @see net.jini.core.lookup.ServiceRegistrar
     */
    public ServiceRegistrar[] getJoinSet() {
        return impl.getJoinSet();
    }//end getJoinSet

    /**
     * Returns an array containing the set of attributes currently associated
     * with the service.
     *
     * @return array of instances of <code>Entry</code> consisting of the set of
     * attributes with which the service is registered in each lookup service
     * that it has joined.
     * @see net.jini.core.entry.Entry
     * @see #setAttributes
     */
    public Entry[] getAttributes() {
        return impl.getAttributes();
    }//end getAttributes

    /**
     * Associates a new set of attributes with the service, in addition to the
     * service's current set of attributes.
     *
     * @param attrSets array of <code>Entry</code> consisting of the attribute
     * sets with which to augment the service's current set of attributes.
     * @see #getAttributes
     */
    public void addAttributes(Entry[] attrSets) {
        impl.addAttributes(attrSets);
    }//end addAttributes

    /**
     * Associates a new set of attributes with the service, in addition to the
     * service's current set of attributes.
     *
     * @param attrSets array of <code>Entry</code> consisting of the attribute
     * sets with which to augment the service's current set of attributes.
     * @param checkSC flag indicating whether the elements of the set of
     * attributes to add should be checked to determine if they are service
     * controlled attributes.
     * @see #getAttributes
     */
    public void addAttributes(Entry[] attrSets, boolean checkSC) {
        impl.addAttributes(attrSets, checkSC);
    }//end addAttributes

    /**
     * Replaces the service's current set of attributes with a new set of
     * attributes.
     *
     * @param attrSets array of <code>Entry</code> consisting of the attribute
     * sets with which to replace the service's current set of attributes.
     * @see #getAttributes
     */
    public void setAttributes(Entry[] attrSets) {
        impl.setAttributes(attrSets);
    }//end setAttributes

    /**
     * Changes the service's current set of attributes using the same semantics
     * as the <code>modifyAttributes</code> method of the
     * <code>ServiceRegistration</code> interface.
     *
     * @param attrSetTemplates array of <code>Entry</code> used to identify
     * which elements to modify from the service's current set of attributes.
     * @param attrSets array of <code>Entry</code> containing the actual
     * modifications to make in the matching sets found using the
     * <code>attrSetTemplates</code> parameter.
     * @see net.jini.core.lookup.ServiceRegistration#modifyAttributes
     */
    public void modifyAttributes(Entry[] attrSetTemplates, Entry[] attrSets) {
        impl.modifyAttributes(attrSetTemplates, attrSets);
    }//end modifyAttributes

    /**
     * Changes the service's current set of attributes using the same semantics
     * as the <code>modifyAttributes</code> method of the
     * <code>ServiceRegistration</code> interface.
     *
     * @param attrSetTemplates array of <code>Entry</code> used to identify
     * which elements to modify from the service's current set of attributes.
     * @param attrSets array of <code>Entry</code> containing the actual
     * modifications to make in the matching sets found using the
     * <code>attrSetTemplates</code> parameter.
     * @param checkSC flag indicating whether the elements of the set of
     * attributes to modify should be checked to determine if they are service
     * controlled attributes.
     * @see net.jini.core.lookup.ServiceRegistration#modifyAttributes
     */
    public void modifyAttributes(Entry[] attrSetTemplates,
            Entry[] attrSets,
            boolean checkSC) {
        impl.modifyAttributes(attrSetTemplates, attrSets, checkSC);
    }//end modifyAttributes

    /**
     * Performs cleanup duties related to the termination of the lookup service
     * discovery event mechanism, as well as the lease and thread management
     * performed by the <code>JoinManager</code>.
     * <p>
     * Calling any method after the termination will result in an
     * <code>IllegalStateException</code>.
     */
    public void terminate() {
        impl.terminate();
    }//end terminate

    /**
     * Replaces the service's current registered reference with the given
     * reference, retaining the service's current set of attributes.
     *
     * @param serviceProxy the new service reference (proxy) to register with
     * all current and future discovered lookup services.
     */
    public void replaceRegistration(Object serviceProxy) {
        impl.replaceRegistration(serviceProxy);
    }//end replaceRegistration

    /**
     * Replaces the service's current registered reference and attributes with
     * the given reference and attributes.
     *
     * @param serviceProxy the new service reference (proxy) to register with
     * all current and future discovered lookup services.
     * @param attrSets array of <code>Entry</code> consisting of the attribute
     * sets with which to register the new service reference.
     */
    public void replaceRegistration(Object serviceProxy, Entry[] attrSets) {
        impl.replaceRegistration(serviceProxy, attrSets);
    }//end replaceRegistration

}//end class JoinManager
