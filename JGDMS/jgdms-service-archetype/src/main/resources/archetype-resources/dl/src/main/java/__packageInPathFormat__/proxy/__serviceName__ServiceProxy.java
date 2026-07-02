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
 * rather than constructing directly.  The factory ALWAYS returns a
 * {@link Constrainable${serviceName}ServiceProxy}; it fails closed (throws)
 * when the server stub does not implement {@link RemoteMethodControl}.  This
 * class is {@code abstract} precisely so that no plain, non-constrainable proxy
 * can ever be constructed or appear as a deserialized wire type — the
 * constrainable form is the only concrete wire proxy, which prevents a silent
 * constraint downgrade.
 *
 * <h2>Serialization</h2>
 * This class is annotated with {@link AtomicSerial} and provides a
 * {@code (GetArg)} constructor for validated atomic deserialization, as
 * required by the JGDMS wire-serialisation standard.  Being abstract, it is
 * never itself a wire instance; the {@code (GetArg)} constructor exists only so
 * the concrete subclass may chain to it.
 *
 * @see ${serviceName}Service
 * @see AbstractSmartProxy
 * @since 1.0
 */
@AtomicSerial
@Stateless  // no own serialized state; server + proxyID live on AbstractSmartProxy
public abstract class ${serviceName}ServiceProxy
        extends AbstractSmartProxy
        implements ${serviceName}Service {

    private static final long serialVersionUID = 1L;

    /**
     * Factory method — ALWAYS returns a
     * {@link Constrainable${serviceName}ServiceProxy}.
     *
     * <p>Fails closed: if {@code server} does not implement
     * {@link RemoteMethodControl} the service was not exported with a
     * constrainable endpoint, so no secure proxy can be produced and this
     * method throws rather than silently returning a plain proxy that would
     * drop the client's security constraints.
     *
     * @param server  the remote server stub; must not be {@code null}
     * @param proxyID the service's stable unique identifier; must not be
     *                {@code null}
     * @return the constrainable proxy instance
     * @throws IllegalArgumentException if {@code server} is not a
     *         {@link RemoteMethodControl} (i.e. the service was not exported
     *         with a constrainable endpoint)
     */
    public static AbstractSmartProxy create(${serviceName}Service server,
                                            Uuid proxyID) {
        if (!(server instanceof RemoteMethodControl)) {
            throw new IllegalArgumentException(
                    "service must be exported with a constrainable endpoint: "
                    + "server does not implement RemoteMethodControl");
        }
        // Preserve the constraints already configured on the exported stub;
        // passing null would call setConstraints(null) and discard them.
        MethodConstraints serverConstraints =
                ((RemoteMethodControl) server).getConstraints();
        return new Constrainable${serviceName}ServiceProxy(
                server, proxyID, serverConstraints);
    }

    /**
     * Creates a new proxy wrapping the given server stub.  Only invoked by
     * subclass constructors — this class is abstract.
     *
     * @param server  the remote server stub; must not be {@code null}
     * @param proxyID the service's stable unique identifier; must not be
     *                {@code null}
     */
    protected ${serviceName}ServiceProxy(${serviceName}Service server,
                                         Uuid proxyID) {
        super(server, proxyID);
    }

    /**
     * {@link AtomicSerial} deserialization constructor.  Only chained to by the
     * concrete subclass — this class is abstract and is never itself a wire
     * instance.
     *
     * @param arg the deserialization argument bag
     * @throws IOException            if deserialization validation fails
     * @throws ClassNotFoundException if a required class cannot be found
     */
    protected ${serviceName}ServiceProxy(GetArg arg)
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
