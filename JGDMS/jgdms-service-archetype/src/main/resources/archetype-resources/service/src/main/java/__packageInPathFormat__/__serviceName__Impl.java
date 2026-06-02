package ${package};

import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core, standalone implementation of {@link ${serviceName}Service}.
 *
 * <p>This class is a plain Java object with no Jini infrastructure
 * dependencies.  It contains the business logic; all Jini infrastructure
 * (export, discovery, lookup registration) is handled by the surrounding
 * {@link ${serviceName}ServiceImpl} wrapper.
 *
 * <h2>Thread safety</h2>
 * This implementation is immutable and therefore inherently thread-safe.
 *
 * @see ${serviceName}ServiceImpl
 * @since 1.0
 */
public class ${serviceName}Impl implements ${serviceName}Service {

    private static final Logger logger =
            Logger.getLogger(${serviceName}Impl.class.getName());

    /**
     * Creates a new {@code ${serviceName}Impl}.
     */
    public ${serviceName}Impl() {
    }

    // TODO: Implement your business logic here.

    @Override
    public String process(String input) throws RemoteException {
        if (input == null) throw new NullPointerException("input");
        String result = "Processed: " + input;
        logger.log(Level.FINE, "Returning: {0}", result);
        return result;
    }
}
