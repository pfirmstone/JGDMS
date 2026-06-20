package ${package}.proxy;

import java.io.IOException;
import java.rmi.RemoteException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.Stateless;
import ${package}.${serviceName}Service;
import au.net.zeus.jgdms.proxy.AbstractSmartProxy;

/**
 * Client-side smart proxy for {@link ${serviceName}Service}.
 *
 * <p>This class is downloaded to client JVMs; it forwards every
 * {@link ${serviceName}Service} method call to the remote server-side
 * implementation over a JERI transport channel.
 *
 * <p>Use the {@link #create(${serviceName}Service, Uuid)} factory method
 * rather than constructing directly.  The factory automatically returns a
 * {@link Constrainable${serviceName}ServiceProxy} when the server stub
 * implements {@link RemoteMethodControl} (i.e. when the service is exported
 * over an SSL/TLS endpoint with method constraints).
 *
 * <h2>Serialization</h2>
 * This class is annotated with {@link AtomicSerial} and provides a
 * {@code (GetArg)} constructor for validated atomic deserialization, as
 * required by the JGDMS wire-serialisation standard.
 *
 * @see ${serviceName}Service
 * @see AbstractSmartProxy
 * @since 1.0
 */
@AtomicSerial
@Stateless  // no own serialized state; server + proxyID live on AbstractSmartProxy
public class ${serviceName}ServiceProxy
        extends AbstractSmartProxy
        implements ${serviceName}Service {

    private static final long serialVersionUID = 1L;

    /**
     * Factory method — returns a {@link Constrainable${serviceName}ServiceProxy}
     * when {@code server} implements {@link RemoteMethodControl}, otherwise
     * a plain {@code ${serviceName}ServiceProxy}.
     *
     * @param server  the remote server stub; must not be {@code null}
     * @param proxyID the service's stable unique identifier; must not be
     *                {@code null}
     * @return the appropriate proxy instance
     */
    public static AbstractSmartProxy create(${serviceName}Service server,
                                            Uuid proxyID) {
        if (server instanceof RemoteMethodControl) {
            // Preserve the constraints already configured on the exported stub;
            // passing null would call setConstraints(null) and discard them.
            MethodConstraints serverConstraints =
                    ((RemoteMethodControl) server).getConstraints();
            return new Constrainable${serviceName}ServiceProxy(
                    server, proxyID, serverConstraints);
        }
        return new ${serviceName}ServiceProxy(server, proxyID);
    }

    /**
     * Creates a new proxy wrapping the given server stub.
     *
     * @param server  the remote server stub; must not be {@code null}
     * @param proxyID the service's stable unique identifier; must not be
     *                {@code null}
     */
    public ${serviceName}ServiceProxy(${serviceName}Service server,
                                      Uuid proxyID) {
        super(server, proxyID);
    }

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * @param arg the deserialization argument bag
     * @throws IOException            if deserialization validation fails
     * @throws ClassNotFoundException if a required class cannot be found
     */
    public ${serviceName}ServiceProxy(GetArg arg)
            throws IOException, ClassNotFoundException {
        super(arg);
    }

    // TODO: Add all methods from ${serviceName}Service, delegating to server.

    @Override
    public String process(String input) throws RemoteException {
        return ((${serviceName}Service) server).process(input);
    }

    // -------------------------------------------------------------------------
    // Nested class: Constrainable${serviceName}ServiceProxy
    // -------------------------------------------------------------------------

    /**
     * Constrainable subclass of {@link ${serviceName}ServiceProxy}.
     *
     * <p>Provides full {@link RemoteMethodControl} support so that the client
     * can impose per-method security constraints (e.g. requiring
     * {@link net.jini.core.constraint.Integrity#YES}) on each outbound call.
     * Instances are produced by the
     * {@link ${serviceName}ServiceProxy#create} factory when the server stub
     * implements {@link RemoteMethodControl}.
     *
     * @since 1.0
     */
    @AtomicSerial
    @Stateless  // no own serialized state
    public static final class Constrainable${serviceName}ServiceProxy
            extends AbstractSmartProxy.ConstrainableSmartProxy
            implements ${serviceName}Service {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a constrainable proxy.
         *
         * @param server      the remote server stub
         * @param proxyID     the service's stable unique identifier
         * @param constraints per-method constraints, or {@code null}
         */
        public Constrainable${serviceName}ServiceProxy(
                ${serviceName}Service server,
                Uuid proxyID,
                MethodConstraints constraints) {
            super(server, proxyID, constraints);
        }

        /**
         * {@link AtomicSerial} deserialization constructor.
         *
         * @param arg the deserialization argument bag
         * @throws IOException            if deserialization validation fails
         * @throws ClassNotFoundException if a required class cannot be found
         */
        public Constrainable${serviceName}ServiceProxy(GetArg arg)
                throws IOException, ClassNotFoundException {
            super(arg);
        }

        @Override
        public RemoteMethodControl setConstraints(
                MethodConstraints constraints) {
            return new Constrainable${serviceName}ServiceProxy(
                    (${serviceName}Service) server, getReferentUuid(),
                    constraints);
        }

        // TODO: Add all methods from ${serviceName}Service, delegating to server.

        @Override
        public String process(String input) throws RemoteException {
            return ((${serviceName}Service) server).process(input);
        }
    }
}
