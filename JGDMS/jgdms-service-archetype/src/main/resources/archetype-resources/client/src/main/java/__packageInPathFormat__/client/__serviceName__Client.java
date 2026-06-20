package ${package}.client;

import ${package}.${serviceName}Service;
import au.net.zeus.jgdms.client.ServiceDiscoveryHelper;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import org.apache.river.config.Config;

/**
 * ${serviceName}Service client — discovers and calls
 * {@link ${serviceName}Service} using {@link ServiceDiscoveryHelper}.
 *
 * <p>Uses a single try-with-resources block for concise discovery:
 * <pre>
 * try (ServiceDiscoveryHelper discovery =
 *         ServiceDiscoveryHelper.fromConfig(config, COMPONENT)) {
 *     ${serviceName}Service svc =
 *         discovery.lookup(${serviceName}Service.class, timeoutMs);
 *     System.out.println(svc.process(input));
 * }
 * </pre>
 *
 * <h2>Configuration component</h2>
 * Entries are read from component {@value #COMPONENT}:
 * <ul>
 *   <li>{@code lookupGroups} ({@code String[]}, default {@code {""}}) —
 *       Jini groups to search</li>
 *   <li>{@code lookupLocators} ({@link net.jini.core.discovery.LookupLocator}[],
 *       default empty) — unicast locators</li>
 *   <li>{@code lookupTimeoutMs} ({@code long}, default {@code 15000}) —
 *       maximum time in ms to wait for a service</li>
 *   <li>{@code serviceInput} ({@code String}, default {@code "test"}) —
 *       input passed to {@link ${serviceName}Service#process}</li>
 * </ul>
 *
 * @see ${serviceName}Service
 * @see ServiceDiscoveryHelper
 * @since 1.0
 */
public class ${serviceName}Client {

    /** Configuration component name for this client. */
    static final String COMPONENT = "${component}.client";

    private static final Logger logger =
            Logger.getLogger(${serviceName}Client.class.getName());

    /** Default timeout (ms) waiting for the service to be discovered. */
    private static final long DEFAULT_LOOKUP_TIMEOUT_MS = 15_000L;

    /**
     * Entry point.
     *
     * @param args configuration file path(s) passed to
     *             {@link ConfigurationProvider#getInstance}
     */
    public static void main(String[] args) throws Exception {
        new ${serviceName}Client().run(args);
    }

    /**
     * Runs the client: discovers the service, invokes it, then terminates.
     *
     * @param configArgs configuration arguments
     */
    public void run(String[] configArgs) throws Exception {
        Configuration config = ConfigurationProvider.getInstance(
                configArgs, ${serviceName}Client.class.getClassLoader());

        long lookupTimeoutMs = Config.getLongEntry(
                config, COMPONENT, "lookupTimeoutMs",
                DEFAULT_LOOKUP_TIMEOUT_MS, 0L, Long.MAX_VALUE);

        String serviceInput = Config.getNonNullEntry(
                config, COMPONENT, "serviceInput", String.class, "test");

        try (ServiceDiscoveryHelper discovery =
                ServiceDiscoveryHelper.fromConfig(config, COMPONENT)) {

            logger.log(Level.INFO,
                    "Searching for ${serviceName}Service (timeout={0} ms)…",
                    lookupTimeoutMs);

            ${serviceName}Service svc =
                    discovery.lookup(${serviceName}Service.class, lookupTimeoutMs);
            logger.log(Level.INFO, "Found ${serviceName}Service: {0}", svc);

            // TODO: Replace with your actual service call(s).
            String result = svc.process(serviceInput);
            System.out.println("Service replied: " + result);

        } catch (RemoteException e) {
            logger.log(Level.SEVERE, "Remote call failed", e);
            throw e;
        }
    }
}
