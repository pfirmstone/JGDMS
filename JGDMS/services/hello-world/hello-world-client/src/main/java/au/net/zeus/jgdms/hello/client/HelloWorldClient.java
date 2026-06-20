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
import au.net.zeus.jgdms.client.ServiceDiscoveryHelper;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import org.apache.river.config.Config;

/**
 * Hello World client — discovers and calls the {@link HelloService} using
 * {@link ServiceDiscoveryHelper}.
 *
 * <p>This class demonstrates the JGDMS client-side lookup pattern using the
 * {@link ServiceDiscoveryHelper} convenience class, which collapses the
 * three-object discovery setup into a single try-with-resources block:
 * <pre>
 * try (ServiceDiscoveryHelper discovery =
 *         ServiceDiscoveryHelper.fromConfig(config, COMPONENT)) {
 *     HelloService svc = discovery.lookup(HelloService.class, timeoutMs);
 *     System.out.println(svc.sayHello(greetingName));
 * }
 * </pre>
 *
 * <h2>Configuration component</h2>
 * Entries are read from component {@value #COMPONENT}.  Configurable entries:
 * <ul>
 *   <li>{@code lookupGroups} ({@code String[]}, default {@code {""}}) —
 *       Jini groups to search (passed to {@link ServiceDiscoveryHelper})</li>
 *   <li>{@code lookupLocators} ({@link net.jini.core.discovery.LookupLocator}[],
 *       default empty) — unicast locators (passed to
 *       {@link ServiceDiscoveryHelper})</li>
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
 * @see ServiceDiscoveryHelper
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
    public void run(String[] configArgs) throws Exception {
        Configuration config = ConfigurationProvider.getInstance(
                configArgs, HelloWorldClient.class.getClassLoader());

        long lookupTimeoutMs = Config.getLongEntry(
                config, COMPONENT, "lookupTimeoutMs",
                DEFAULT_LOOKUP_TIMEOUT_MS, 0L, Long.MAX_VALUE);

        String greetingName = Config.getNonNullEntry(
                config, COMPONENT, "greetingName", String.class, "World");

        try (ServiceDiscoveryHelper discovery =
                ServiceDiscoveryHelper.fromConfig(config, COMPONENT)) {

            logger.log(Level.INFO,
                    "Searching for HelloService (timeout={0} ms)…",
                    lookupTimeoutMs);

            HelloService svc = discovery.lookup(HelloService.class, lookupTimeoutMs);
            logger.log(Level.INFO, "Found HelloService: {0}", svc);

            String reply = svc.sayHello(greetingName);
            System.out.println("Service replied: " + reply);

        } catch (java.util.NoSuchElementException e) {
            // ServiceDiscoveryHelper.lookup throws this when no HelloService
            // is discovered within the timeout — report it cleanly rather
            // than letting it surface as an unhandled stack trace.
            logger.log(Level.WARNING,
                    "No HelloService found within {0} ms — is the service running"
                    + " and registered with a reachable lookup service?",
                    lookupTimeoutMs);
            System.out.println("No HelloService found within "
                    + lookupTimeoutMs + " ms.");
        } catch (RemoteException e) {
            logger.log(Level.SEVERE, "Remote call failed", e);
            throw e;
        }
    }
}
