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
package au.net.zeus.jgdms.hello.client;

import au.net.zeus.jgdms.api.hello.HelloService;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.LookupDiscovery;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.ServiceDiscoveryManager;

/**
 * Hello World client — discovers and calls the {@link HelloService} using
 * {@link ServiceDiscoveryManager}.
 *
 * <p>This class demonstrates the canonical JGDMS client-side lookup pattern:
 * <ol>
 *   <li>Read the Jini configuration from the command-line config file(s).</li>
 *   <li>Create a {@link LookupDiscoveryManager} that discovers lookup
 *       services via multicast (and/or unicast locators).</li>
 *   <li>Create a {@link ServiceDiscoveryManager} to manage proxies
 *       returned by those lookup services.</li>
 *   <li>Call {@link ServiceDiscoveryManager#lookup} with a
 *       {@link ServiceTemplate} that matches {@link HelloService}, blocking
 *       up to a configurable timeout until a suitable service is found.</li>
 *   <li>Cast the returned proxy to {@link HelloService} and invoke
 *       {@link HelloService#sayHello}.</li>
 *   <li>Terminate the discovery managers before exit.</li>
 * </ol>
 *
 * <h2>Configuration component</h2>
 * Entries are read from component {@value #COMPONENT}.  Configurable entries:
 * <ul>
 *   <li>{@code lookupGroups} ({@code String[]}, default {@code {""}}) —
 *       Jini groups to search</li>
 *   <li>{@code lookupLocators} ({@link net.jini.core.discovery.LookupLocator}[],
 *       default empty) — unicast locators</li>
 *   <li>{@code lookupTimeoutMs} ({@code long}, default {@code 15000}) —
 *       maximum time in milliseconds to wait for a service</li>
 *   <li>{@code greetingName} ({@code String}, default {@code "World"}) —
 *       the name passed to {@link HelloService#sayHello}</li>
 * </ul>
 *
 * <h2>Running the client</h2>
 * <pre>
 * java -Djava.security.policy=hello-client.policy \
 *      -cp &lt;classpath&gt; \
 *      au.net.zeus.jgdms.hello.client.HelloWorldClient \
 *      hello-world-client.config
 * </pre>
 *
 * @see HelloService
 * @see ServiceDiscoveryManager
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class HelloWorldClient {

    /** Configuration component name for this client. */
    static final String COMPONENT = "au.net.zeus.jgdms.hello.client";

    private static final Logger logger =
            Logger.getLogger(HelloWorldClient.class.getName());

    /** Default timeout (ms) waiting for the service to be discovered. */
    private static final long DEFAULT_LOOKUP_TIMEOUT_MS = 15_000L;

    /**
     * Entry point.
     *
     * @param args configuration file path(s) passed to
     *             {@link ConfigurationProvider#getInstance}
     */
    public static void main(String[] args) throws Exception {
        new HelloWorldClient().run(args);
    }

    /**
     * Runs the client: discovers the service, invokes it, then terminates.
     *
     * @param configArgs configuration arguments
     */
    public void run(String[] configArgs) throws ConfigurationException,
                                                IOException,
                                                InterruptedException {
        Configuration config = ConfigurationProvider.getInstance(
                configArgs, HelloWorldClient.class.getClassLoader());

        // Read configuration entries.
        String[] lookupGroups = (String[]) config.getEntry(
                COMPONENT, "lookupGroups",
                String[].class, new String[]{""});

        net.jini.core.discovery.LookupLocator[] lookupLocators =
                (net.jini.core.discovery.LookupLocator[]) config.getEntry(
                        COMPONENT, "lookupLocators",
                        net.jini.core.discovery.LookupLocator[].class,
                        new net.jini.core.discovery.LookupLocator[0]);

        long lookupTimeoutMs = (long) (Long) config.getEntry(
                COMPONENT, "lookupTimeoutMs",
                long.class, DEFAULT_LOOKUP_TIMEOUT_MS);

        String greetingName = (String) config.getEntry(
                COMPONENT, "greetingName",
                String.class, "World");

        // Set up discovery and the ServiceDiscoveryManager.
        LookupDiscoveryManager ldm = new LookupDiscoveryManager(
                lookupGroups, lookupLocators, null, config);
        LeaseRenewalManager lrm = new LeaseRenewalManager(config);
        ServiceDiscoveryManager sdm =
                new ServiceDiscoveryManager(ldm, lrm, config);

        try {
            // Build a service template matching HelloService.
            ServiceTemplate template = new ServiceTemplate(
                    null,
                    new Class<?>[]{ HelloService.class },
                    null);

            logger.log(Level.INFO,
                    "Searching for HelloService (timeout={0} ms)…",
                    lookupTimeoutMs);

            // Block until a HelloService is found or the timeout expires.
            ServiceItem[] items = sdm.lookup(template, 1, 1, null,
                    lookupTimeoutMs);

            if (items == null || items.length == 0) {
                System.err.println("No HelloService found within "
                        + lookupTimeoutMs + " ms.");
                System.exit(1);
            }

            HelloService svc = (HelloService) items[0].service;
            logger.log(Level.INFO, "Found HelloService: {0}", svc);

            // Invoke the service.
            String reply = svc.sayHello(greetingName);
            System.out.println("Service replied: " + reply);

        } catch (RemoteException e) {
            logger.log(Level.SEVERE, "Remote call failed", e);
            throw e;
        } finally {
            // Always clean up discovery resources.
            sdm.terminate();
        }
    }
}
