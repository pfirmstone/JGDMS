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
package au.net.zeus.jgdms.client;

import java.io.IOException;
import java.rmi.RemoteException;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.ServiceDiscoveryManager;
import org.apache.river.config.Config;

/**
 * Convenience wrapper that collapses the standard three-object client
 * service-discovery setup into a single {@link AutoCloseable} object.
 *
 * <h2>Background</h2>
 * Every JGDMS client that locates a service via multicast or unicast
 * discovery requires the same boilerplate sequence:
 * <ol>
 *   <li>Create a {@link LookupDiscoveryManager} with the desired groups and
 *       locators.</li>
 *   <li>Create a {@link LeaseRenewalManager}.</li>
 *   <li>Create a {@link ServiceDiscoveryManager} (SDM) wrapping both.</li>
 *   <li>Build a {@link ServiceTemplate} matching the desired service
 *       interface.</li>
 *   <li>Call {@link ServiceDiscoveryManager#lookup} with a timeout.</li>
 *   <li>Remember to call {@link ServiceDiscoveryManager#terminate()} when
 *       done.</li>
 * </ol>
 *
 * <p>{@code ServiceDiscoveryHelper} wraps all of the above and exposes a
 * single {@link #lookup(Class, long)} method.  Because it implements
 * {@link AutoCloseable} it integrates cleanly with try-with-resources,
 * guaranteeing that discovery resources are released even if an exception
 * is thrown.
 *
 * <h2>Configuration entries</h2>
 * The following entries are read from the given {@code component} (all
 * optional):
 * <ul>
 *   <li>{@code lookupGroups} ({@code String[]}, default {@code {""}}) —
 *       Jini groups to search</li>
 *   <li>{@code lookupLocators} ({@link LookupLocator}[], default empty) —
 *       unicast locators</li>
 * </ul>
 * The {@link ServiceDiscoveryManager} and {@link LeaseRenewalManager} are
 * also constructed from {@code config} so that any additional SDM entries
 * (such as {@code proxyPreparer}) in the configuration file are honoured
 * automatically.
 *
 * <h2>Usage</h2>
 * <pre>
 * Configuration config = ConfigurationProvider.getInstance(configArgs,
 *         MyClient.class.getClassLoader());
 *
 * try (ServiceDiscoveryHelper discovery =
 *         ServiceDiscoveryHelper.fromConfig(config, COMPONENT)) {
 *
 *     HelloService svc = discovery.lookup(HelloService.class, 15_000L);
 *     System.out.println(svc.sayHello("World"));
 * }
 * </pre>
 *
 * @see ServiceDiscoveryManager
 * @see LookupDiscoveryManager
 * @see LeaseRenewalManager
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public final class ServiceDiscoveryHelper implements AutoCloseable {

    /** Default lookup-groups: public multicast group. */
    private static final String[] DEFAULT_GROUPS = {""};

    /** Default lookup-locators: none. */
    private static final LookupLocator[] NO_LOCATORS = new LookupLocator[0];

    /** The managed ServiceDiscoveryManager instance. */
    private final ServiceDiscoveryManager sdm;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code ServiceDiscoveryHelper} by reading discovery
     * parameters from the given {@link Configuration}.
     *
     * <p>The helper reads {@code lookupGroups} and {@code lookupLocators}
     * from {@code component}.  Both parameters are optional; if absent the
     * helper uses the public multicast group with no unicast locators.
     *
     * @param config    the Jini configuration; must be non-null
     * @param component the configuration component name used to look up
     *                  {@code lookupGroups} and {@code lookupLocators};
     *                  must be non-null
     * @return a new {@code ServiceDiscoveryHelper} ready for use
     * @throws ConfigurationException if a configuration entry is of the
     *                                wrong type or otherwise invalid
     * @throws IOException            if discovery setup fails (e.g. multicast
     *                                socket cannot be opened)
     */
    public static ServiceDiscoveryHelper fromConfig(Configuration config,
                                                    String component)
            throws ConfigurationException, IOException {
        String[] groups = Config.getNonNullEntry(
                config, component, "lookupGroups",
                String[].class, DEFAULT_GROUPS).clone();
        LookupLocator[] locators = Config.getNonNullEntry(
                config, component, "lookupLocators",
                LookupLocator[].class, NO_LOCATORS).clone();
        LookupDiscoveryManager ldm =
                new LookupDiscoveryManager(groups, locators, null, config);
        LeaseRenewalManager lrm = new LeaseRenewalManager(config);
        ServiceDiscoveryManager sdm =
                new ServiceDiscoveryManager(ldm, lrm, config);
        return new ServiceDiscoveryHelper(sdm);
    }

    /**
     * Creates a new {@code ServiceDiscoveryHelper} wrapping the given
     * {@link ServiceDiscoveryManager}.
     *
     * <p>Use this constructor when you need fine-grained control over the
     * underlying {@link LookupDiscoveryManager} or {@link LeaseRenewalManager},
     * or when you want to reuse an existing manager.  In most cases prefer
     * {@link #fromConfig(Configuration, String)}.
     *
     * @param sdm the service-discovery manager to wrap; must be non-null
     * @throws IllegalArgumentException if {@code sdm} is {@code null}
     */
    public ServiceDiscoveryHelper(ServiceDiscoveryManager sdm) {
        if (sdm == null) throw new IllegalArgumentException("sdm must not be null");
        this.sdm = sdm;
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Discovers a service of type {@code serviceType} and returns it,
     * blocking up to {@code timeoutMs} milliseconds.
     *
     * <p>Builds a {@link ServiceTemplate} that matches any service
     * implementing {@code serviceType}, then delegates to
     * {@link ServiceDiscoveryManager#lookup(ServiceTemplate,
     * net.jini.lookup.ServiceItemFilter, long)}.
     *
     * @param <S>         the service type
     * @param serviceType the service interface to search for; must be
     *                    non-null
     * @param timeoutMs   maximum time to wait in milliseconds; must be
     *                    &gt;= 0
     * @return the discovered service proxy, cast to {@code S}
     * @throws java.util.NoSuchElementException if no service is found within
     *         the timeout
     * @throws InterruptedException if the calling thread is interrupted
     *         while waiting
     * @throws RemoteException if a remote communication failure occurs
     *         during lookup
     * @throws IllegalArgumentException if {@code serviceType} is
     *         {@code null} or {@code timeoutMs} is negative
     */
    @SuppressWarnings("unchecked")
    public <S> S lookup(Class<S> serviceType, long timeoutMs)
            throws InterruptedException, RemoteException {
        if (serviceType == null)
            throw new IllegalArgumentException("serviceType must not be null");
        if (timeoutMs < 0)
            throw new IllegalArgumentException("timeoutMs must be >= 0");
        ServiceTemplate template = new ServiceTemplate(
                null, new Class<?>[]{ serviceType }, null);
        ServiceItem item = sdm.lookup(template, null, timeoutMs);
        if (item == null) {
            throw new java.util.NoSuchElementException(
                    "No " + serviceType.getSimpleName()
                    + " found within " + timeoutMs + " ms");
        }
        return (S) item.service;
    }

    /**
     * Discovers all available services of type {@code serviceType}, up to
     * {@code maxServices}, blocking up to {@code timeoutMs} milliseconds.
     *
     * <p>Useful when the client needs to contact multiple instances of the
     * same service (e.g. for load distribution or failover).
     *
     * @param <S>          the service type
     * @param serviceType  the service interface to search for; must be
     *                     non-null
     * @param maxServices  the maximum number of service instances to return;
     *                     must be &gt;= 1
     * @param timeoutMs    maximum time to wait in milliseconds; must be
     *                     &gt;= 0
     * @return an array of up to {@code maxServices} discovered service
     *         proxies; never {@code null} but may be empty if none are
     *         found
     * @throws InterruptedException if the calling thread is interrupted
     *         while waiting
     * @throws RemoteException if a remote communication failure occurs
     *         during lookup
     * @throws IllegalArgumentException if {@code serviceType} is
     *         {@code null}, {@code maxServices} &lt; 1, or {@code timeoutMs}
     *         is negative
     */
    @SuppressWarnings("unchecked")
    public <S> S[] lookupAll(Class<S> serviceType,
                             int maxServices,
                             long timeoutMs)
            throws InterruptedException, RemoteException {
        if (serviceType == null)
            throw new IllegalArgumentException("serviceType must not be null");
        if (maxServices < 1)
            throw new IllegalArgumentException("maxServices must be >= 1");
        if (timeoutMs < 0)
            throw new IllegalArgumentException("timeoutMs must be >= 0");
        ServiceTemplate template = new ServiceTemplate(
                null, new Class<?>[]{ serviceType }, null);
        ServiceItem[] items = sdm.lookup(template, 1, maxServices, null, timeoutMs);
        S[] result = (S[]) java.lang.reflect.Array.newInstance(serviceType,
                items == null ? 0 : items.length);
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                result[i] = (S) items[i].service;
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // AutoCloseable
    // -------------------------------------------------------------------------

    /**
     * Terminates the underlying {@link ServiceDiscoveryManager} and releases
     * all discovery resources.
     *
     * <p>After this method returns, the {@link #lookup} methods must not be
     * called.  Calling {@code close()} more than once is safe (subsequent
     * calls have no effect).
     */
    @Override
    public void close() {
        sdm.terminate();
    }
}
