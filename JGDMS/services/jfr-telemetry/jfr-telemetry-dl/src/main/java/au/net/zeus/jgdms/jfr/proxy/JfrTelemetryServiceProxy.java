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
package au.net.zeus.jgdms.jfr.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;
import java.util.Set;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.api.telemetry.JfrTelemetryService;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
import au.net.zeus.jgdms.proxy.AbstractSmartProxy;

/**
 * Client-side smart proxy for the {@link JfrTelemetryService}.
 *
 * <p>Downloaded to client JVMs; forwards all {@link JfrTelemetryService}
 * method calls to the remote server-side implementation over a JERI transport
 * channel.
 *
 * <p>Use the {@link #create(JfrTelemetryService, Uuid)} factory method rather
 * than constructing directly; the factory automatically returns a
 * {@link ConstrainableJfrTelemetryServiceProxy} when the server stub implements
 * {@link RemoteMethodControl}.
 *
 * @see JfrTelemetryService
 * @see AbstractSmartProxy
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
@AtomicSerial
public class JfrTelemetryServiceProxy
        extends AbstractSmartProxy
        implements JfrTelemetryService {

    private static final long serialVersionUID = 1L;

    /**
     * Factory method — returns a {@link ConstrainableJfrTelemetryServiceProxy}
     * when {@code server} implements {@link RemoteMethodControl}, otherwise a
     * plain {@code JfrTelemetryServiceProxy}.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     * @return the appropriate proxy instance
     */
    public static AbstractSmartProxy create(JfrTelemetryService server,
                                            Uuid proxyID) {
        if (server instanceof RemoteMethodControl) {
            return new ConstrainableJfrTelemetryServiceProxy(server, proxyID, null);
        }
        return new JfrTelemetryServiceProxy(server, proxyID);
    }

    /**
     * Creates a new proxy wrapping the given server stub.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     */
    public JfrTelemetryServiceProxy(JfrTelemetryService server, Uuid proxyID) {
        super(server, proxyID);
    }

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * <p>{@link AbstractSmartProxy} validates that the deserialized
     * {@code server} stub implements every interface declared on this concrete
     * proxy class; no additional check is needed here.
     *
     * @param arg the deserialization argument bag
     * @throws IOException            if deserialization validation fails
     * @throws ClassNotFoundException if a required class cannot be found
     */
    public JfrTelemetryServiceProxy(GetArg arg)
            throws IOException, ClassNotFoundException {
        super(arg);
    }

    @Override
    public void reportPinning(PinningReport report) throws RemoteException {
        ((JfrTelemetryService) server).reportPinning(report);
    }

    @Override
    public long getPinnedNanos(Set<Uri> codebaseUrls) throws RemoteException {
        return ((JfrTelemetryService) server).getPinnedNanos(codebaseUrls);
    }

    @Override
    public long getPinCount(Set<Uri> codebaseUrls) throws RemoteException {
        return ((JfrTelemetryService) server).getPinCount(codebaseUrls);
    }

    // -------------------------------------------------------------------------
    // Nested class: ConstrainableJfrTelemetryServiceProxy
    // -------------------------------------------------------------------------

    /**
     * Constrainable subclass of {@link JfrTelemetryServiceProxy}.
     *
     * <p>This class additionally implements {@link RemoteMethodControl} so
     * that the client can apply per-method JERI constraints (e.g.
     * {@link net.jini.core.constraint.Integrity},
     * {@link net.jini.core.constraint.ServerAuthentication}) to calls
     * forwarded through this proxy.
     *
     * @since 3.1.1
     */
    @AtomicSerial
    public static final class ConstrainableJfrTelemetryServiceProxy
            extends JfrTelemetryServiceProxy
            implements RemoteMethodControl {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a constrainable proxy.
         *
         * @param server      the remote server stub; must implement
         *                    {@link RemoteMethodControl}
         * @param proxyID     the service's stable unique identifier
         * @param constraints per-method constraints, or {@code null}
         */
        public ConstrainableJfrTelemetryServiceProxy(JfrTelemetryService server,
                                                     Uuid proxyID,
                                                     MethodConstraints constraints) {
            super(constrainServer(server, constraints), proxyID);
        }

        /**
         * {@link AtomicSerial} deserialization constructor.
         *
         * @param arg the deserialization argument bag
         * @throws IOException            if deserialization validation fails
         * @throws ClassNotFoundException if a required class cannot be found
         */
        public ConstrainableJfrTelemetryServiceProxy(GetArg arg)
                throws IOException, ClassNotFoundException {
            super(arg);
            if (!(server instanceof RemoteMethodControl))
                throw new InvalidObjectException(
                        "server does not implement RemoteMethodControl");
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new ConstrainableJfrTelemetryServiceProxy(
                    (JfrTelemetryService) server, proxyID, constraints);
        }

        @Override
        public MethodConstraints getConstraints() {
            return ((RemoteMethodControl) server).getConstraints();
        }

        private static JfrTelemetryService constrainServer(
                JfrTelemetryService server, MethodConstraints constraints) {
            if (constraints == null) return server;
            return (JfrTelemetryService)
                    ((RemoteMethodControl) server).setConstraints(constraints);
        }

        @Override
        public void reportPinning(PinningReport report) throws RemoteException {
            ((JfrTelemetryService) server).reportPinning(report);
        }

        @Override
        public long getPinnedNanos(Set<Uri> codebaseUrls) throws RemoteException {
            return ((JfrTelemetryService) server).getPinnedNanos(codebaseUrls);
        }

        @Override
        public long getPinCount(Set<Uri> codebaseUrls) throws RemoteException {
            return ((JfrTelemetryService) server).getPinCount(codebaseUrls);
        }
    }
}
