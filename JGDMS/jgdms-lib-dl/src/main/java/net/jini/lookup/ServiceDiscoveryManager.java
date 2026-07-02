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
import java.util.Iterator;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.DiscoveryManagement;
import net.jini.lease.LeaseRenewalManager;
import org.apache.river.resource.Service;

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
 * <code>ServiceDiscoveryManager</code> on behalf of the entity. To employ this
 * pattern, the entity invokes the method
 * {@link net.jini.lookup.ServiceDiscoveryManager#createLookupCache createLookupCache}.
 * <li> The entity can register with the event mechanism provided by the
 * <code>ServiceDiscoveryManager</code>. This event mechanism allows the entity
 * to request that it be notified when a service of interest is discovered for
 * the first time, or has encountered a state change such as removal from all
 * lookup services, or attribute set changes.
 * <li> The entity, through the public API of the
 * <code>ServiceDiscoveryManager</code>, can directly query the lookup services
 * managed by the <code>ServiceDiscoveryManager</code> for services of interest;
 * employing semantics similar to the semantics employed in a typical lookup
 * service query made through the
 * {@link net.jini.core.lookup.ServiceRegistrar ServiceRegistrar} interface.
 * </ul>
 * <p>
 * Note that this utility class is not remote. Clients and services that wish to
 * use this class will create an instance of this class in their own address
 * space to manage the state of discovered services and their associated lookup
 * services locally.
 * <p>
 * <b>Implementation.</b> Since release 3.1.1 this class is a thin, delegating
 * <i>facade</i>. Its public constructors and methods are unchanged; each
 * instance holds a {@link ServiceDiscoveryManagerSpi} delegate obtained from a
 * {@link DiscoveryProviderFactory} that is resolved once, via
 * {@link org.apache.river.resource.Service}, from the classpath. The discovery
 * implementation lives in a separate, non-downloadable jar so that this
 * download jar (and hence the proxy code beside it) can be compiled for the
 * widest range of client JVMs. If no {@code DiscoveryProviderFactory} is found,
 * construction throws {@link IllegalStateException}.
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

    /* Name of this component; used in config entry retrieval and the logger.
     * Retained as public API-adjacent constant for callers that referenced it
     * via the fully qualified component name string.
     */
    static final String COMPONENT_NAME
            = "net.jini.lookup.ServiceDiscoveryManager";

    /**
     * Holder that resolves the single {@link DiscoveryProviderFactory} once,
     * lazily and thread-safely (class-init happens under the JVM's
     * initialization lock).
     */
    private static final class FactoryHolder {

        static final DiscoveryProviderFactory FACTORY = resolve();

        private static DiscoveryProviderFactory resolve() {
            Iterator<DiscoveryProviderFactory> it = Service.providers(
                    DiscoveryProviderFactory.class,
                    ServiceDiscoveryManager.class.getClassLoader());
            if (it.hasNext()) {
                return it.next();
            }
            return null;
        }
    }

    /**
     * Returns the resolved factory, or throws a clear diagnostic if none is on
     * the classpath.
     */
    static DiscoveryProviderFactory factory() {
        DiscoveryProviderFactory f = FactoryHolder.FACTORY;
        if (f == null) {
            throw new IllegalStateException("no net.jini.lookup.DiscoveryProviderFactory on the classpath — add the jgdms-lib dependency");
        }
        return f;
    }

    /** The discovery implementation this facade delegates to. */
    private final ServiceDiscoveryManagerSpi impl;

    /**
     * Constructs an instance of <code>ServiceDiscoveryManager</code> which
     * will, on behalf of the entity that constructs this class, discover and
     * manage a set of lookup services, as well as discover and manage sets of
     * services registered with those lookup services. The entity indicates
     * which lookup services to discover and manage through the parameters input
     * to this constructor.
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
        this.impl = factory().newServiceDiscoveryManager(discoveryMgr, leaseMgr);
    }//end constructor

    /**
     * Constructs an instance of this class, which is configured using the items
     * retrieved through the given <code>Configuration</code>, that will, on
     * behalf of the entity that constructs this class, discover and manage a
     * set of lookup services, as well as discover and manage sets of services
     * registered with those lookup services.
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
        this.impl = factory().newServiceDiscoveryManager(discoveryMgr, leaseMgr, config);
    }//end constructor

    /**
     * Queries each available lookup service in the set of lookup services
     * managed by the <code>ServiceDiscoveryManager</code> for a service
     * reference that matches criteria defined by the entity that invokes this
     * method. This version of <code>lookup</code> returns a <i>single</i>
     * instance of <code>ServiceItem</code> corresponding to one of possibly
     * many service references that satisfy the matching criteria.
     *
     * @param tmpl an instance of <code>ServiceTemplate</code> corresponding to
     * the object to use for template-matching when searching for desired
     * services.
     * @param filter an instance of <code>ServiceItemFilter</code> containing
     * matching criteria that should be applied in addition to the
     * template-matching employed when searching for desired services.
     *
     * @return a single instance of <code>ServiceItem</code> that satisfies the
     * matching criteria, or <code>null</code> if no matching service can be
     * found.
     *
     * @see net.jini.core.lookup.ServiceRegistrar#lookup
     * @see net.jini.core.lookup.ServiceTemplate
     * @see net.jini.lookup.ServiceItemFilter
     */
    public ServiceItem lookup(ServiceTemplate tmpl, ServiceItemFilter filter) {
        return impl.lookup(tmpl, filter);
    }//end lookup

    /**
     * Queries each available lookup service in the managed set for a service
     * that matches the input criteria. This <i>blocking</i> version of
     * <code>lookup</code> waits, up to the given duration, for a matching
     * service to be discovered.
     *
     * @param tmpl an instance of <code>ServiceTemplate</code> corresponding to
     * the object to use for template-matching.
     * @param filter an instance of <code>ServiceItemFilter</code> containing
     * additional matching criteria.
     * @param waitDur the amount of time (in milliseconds) to wait before ending
     * the "wait" and returning <code>null</code>.
     *
     * @return a single instance of <code>ServiceItem</code> that satisfies the
     * matching criteria, or <code>null</code>.
     *
     * @throws java.lang.InterruptedException if the entity interrupts the
     * "wait".
     * @throws java.rmi.RemoteException typically, this exception occurs when a
     * RemoteException occurs during a remote query of a lookup service.
     *
     * @see net.jini.core.lookup.ServiceRegistrar#lookup
     * @see net.jini.core.lookup.ServiceTemplate
     * @see net.jini.lookup.ServiceItemFilter
     */
    public ServiceItem lookup(ServiceTemplate tmpl,
            ServiceItemFilter filter,
            long waitDur) throws InterruptedException,
            RemoteException {
        return impl.lookup(tmpl, filter, waitDur);
    }//end lookup

    /**
     * The <code>createLookupCache</code> method allows the client-like entity
     * to request that this utility create a new managed set (or cache) of
     * services and their corresponding attribute sets.
     *
     * @param tmpl template to match. It uses <code>template</code> matching
     * semantics to identify the service(s) to acquire from lookup services in
     * the managed set.
     * @param filter used to apply additional matching criteria to any
     * <code>ServiceItem</code> found through template-matching.
     * @param listener object that will receive notifications when services
     * matching the input criteria are discovered for the first time, or have
     * encountered a state change such as removal from all lookup services or
     * attribute set changes.
     *
     * @return LookupCache used to query the cache for services of interest,
     * manage the cache's event mechanism for service discoveries, or terminate
     * the cache.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when a
     * RemoteException occurs as a result of an attempt to export the listener
     * that receives service events from the lookup services in the managed set.
     *
     * @see net.jini.lookup.ServiceItemFilter
     */
    public LookupCache createLookupCache(ServiceTemplate tmpl,
            ServiceItemFilter filter,
            ServiceDiscoveryListener listener)
            throws RemoteException {
        return impl.createLookupCache(tmpl, filter, listener);
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
        return impl.getDiscoveryManager();
    }//end getDiscoveryManager

    /**
     * The <code>getLeaseRenewalManager</code> method will return an instance of
     * the <code>LeaseRenewalManager</code> class. The object returned by this
     * method manages the leases requested and held by the
     * <code>ServiceDiscoveryManager</code>.
     *
     * @return LeaseRenewalManager for this instance of the
     * <code>ServiceDiscoveryManager</code>.
     * @see net.jini.lease.LeaseRenewalManager
     */
    public LeaseRenewalManager getLeaseRenewalManager() {
        return impl.getLeaseRenewalManager();
    }//end getLeaseRenewalManager

    /**
     * The <code>terminate</code> method performs cleanup duties related to the
     * termination of the event mechanism for lookup service discovery, the
     * event mechanism for service discovery, and the cache management duties of
     * the <code>ServiceDiscoveryManager</code>.
     * <p>
     * Calling any method after the termination will result in an
     * <code>IllegalStateException</code>.
     *
     * @see net.jini.lookup.LookupCache
     * @see net.jini.discovery.DiscoveryEvent
     */
    public void terminate() {
        impl.terminate();
    }//end terminate

    /**
     * Queries each available lookup service in the managed set for service(s)
     * that match the input criteria. This version of <code>lookup</code>
     * returns an <i>array</i> of instances of <code>ServiceItem</code> in which
     * each element corresponds to a service reference that satisfies the
     * matching criteria. Note that this version of <code>lookup</code> does not
     * provide a <i>blocking</i> feature.
     *
     * @param tmpl an instance of <code>ServiceTemplate</code> corresponding to
     * the object to use for template-matching when searching for desired
     * services.
     * @param maxMatches this method will return no more than this number of
     * service references.
     * @param filter an instance of <code>ServiceItemFilter</code> containing
     * additional matching criteria.
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
        return impl.lookup(tmpl, maxMatches, filter);
    }//end lookup

    /**
     * Queries each available lookup service in the managed set for service(s)
     * that match the input criteria. This <i>blocking</i> version of
     * <code>lookup</code> waits, up to the given duration, for at least
     * <code>minMatches</code> services to be discovered.
     *
     * @param tmpl an instance of <code>ServiceTemplate</code> corresponding to
     * the object to use for template-matching when searching for desired
     * services.
     * @param minMatches this method will immediately exit the wait and return
     * as soon as it has found this number of matching services.
     * @param maxMatches this method will return no more than this number of
     * service references.
     * @param filter an instance of <code>ServiceItemFilter</code> containing
     * additional matching criteria.
     * @param waitDur the amount of time (in milliseconds) to wait before ending
     * the "wait" and returning an empty array.
     *
     * @return an array of instances of <code>ServiceItem</code> where each
     * element corresponds to a reference to a service that matches the criteria
     * represented in the input parameters; or an empty array if no matching
     * service can be found.
     *
     * @throws java.lang.InterruptedException if the entity interrupts the
     * "wait".
     * @throws java.rmi.RemoteException typically, this exception occurs when a
     * RemoteException occurs during a remote query of a lookup service.
     *
     * @see net.jini.core.lookup.ServiceRegistrar#lookup
     * @see net.jini.core.lookup.ServiceTemplate
     * @see net.jini.lookup.ServiceItemFilter
     */
    public ServiceItem[] lookup(ServiceTemplate tmpl,
            int minMatches,
            int maxMatches,
            ServiceItemFilter filter,
            long waitDur) throws InterruptedException,
            RemoteException {
        return impl.lookup(tmpl, minMatches, maxMatches, filter, waitDur);
    }//end lookup

}//end class ServiceDiscoveryManager
