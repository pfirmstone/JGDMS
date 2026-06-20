package ${package};

import java.rmi.RemoteException;
import net.jini.activation.arg.ActivationID;
import net.jini.id.Uuid;
import ${package}.proxy.${serviceName}ServiceBackend;
import ${package}.proxy.${serviceName}ServiceProxy;
import au.net.zeus.jgdms.service.support.AbstractJiniService;
import org.apache.river.start.lifecycle.LifeCycle;

/**
 * Activatable, Jini-aware wrapper for {@link ${serviceName}Service}.
 *
 * <p>This class wires the core business logic ({@link ${serviceName}Impl}) into
 * the full JGDMS service infrastructure:
 * <ul>
 *   <li>Reads all configuration from a Jini {@link net.jini.config.Configuration}</li>
 *   <li>Exports itself via a configurable {@link net.jini.export.Exporter}</li>
 *   <li>Builds a {@link ${serviceName}ServiceProxy} for clients</li>
 *   <li>Registers with Jini lookup services via a
 *       {@link net.jini.lookup.JoinManager}</li>
 * </ul>
 *
 * <p>All infrastructure boilerplate is inherited from
 * {@link AbstractJiniService}; this subclass only supplies service-specific
 * logic.
 *
 * <h2>Configuration component</h2>
 * All Jini configuration entries are read from component
 * {@value #COMPONENT}.  The common infrastructure entries are documented in
 * {@link au.net.zeus.jgdms.service.support.JiniServiceParameters}.  No
 * service-specific mandatory entries exist.
 *
 * @see ${serviceName}Impl
 * @see ${serviceName}Service
 * @see AbstractJiniService
 * @since 1.0
 */
public class ${serviceName}ServiceImpl
        extends AbstractJiniService
        implements ${serviceName}ServiceBackend {

    /** Configuration component name for this service. */
    static final String COMPONENT = "${component}";

    /** The core implementation to which all calls are delegated. */
    private final ${serviceName}Impl impl;

    // -------------------------------------------------------------------------
    // Public constructors — no boilerplate needed beyond these two lines
    // -------------------------------------------------------------------------

    /**
     * Activatable constructor.  Required by Phoenix.
     *
     * @param activationID the activation ID assigned by the activation system
     * @param data         configuration arguments
     * @throws Exception if construction fails
     */
    public ${serviceName}ServiceImpl(ActivationID activationID,
                                     String[] data)
            throws Exception {
        super(activationID, data, COMPONENT, ${serviceName}Service.class);
        this.impl = new ${serviceName}Impl();
    }

    /**
     * Non-activatable constructor for use with
     * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter}.
     *
     * @param configArgs configuration arguments
     * @param lifeCycle  lifecycle callback; may be {@code null}
     * @throws Exception if construction fails
     */
    public ${serviceName}ServiceImpl(String[] configArgs,
                                     LifeCycle lifeCycle)
            throws Exception {
        super(configArgs, lifeCycle, COMPONENT, ${serviceName}Service.class);
        this.impl = new ${serviceName}Impl();
    }

    // -------------------------------------------------------------------------
    // AbstractJiniService template methods
    // -------------------------------------------------------------------------

    @Override
    protected Object createProxy(Object stub, Uuid serviceUuid) {
        return ${serviceName}ServiceProxy.create(
                (${serviceName}Service) stub, serviceUuid);
    }

    @Override
    protected Class<?>[] getServiceInterfaces() {
        return new Class<?>[]{ ${serviceName}Service.class };
    }

    // -------------------------------------------------------------------------
    // ${serviceName}Service — delegate all calls to the core implementation
    // -------------------------------------------------------------------------

    // TODO: Add all methods from ${serviceName}Service here, delegating to impl.

    @Override
    public String process(String input) throws RemoteException {
        getReadyState().check();
        return impl.process(input);
    }
}
