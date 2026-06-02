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
package au.net.zeus.jgdms.hello.proxy;

import java.io.IOException;
import java.rmi.RemoteException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import au.net.zeus.jgdms.api.hello.HelloService;
import au.net.zeus.jgdms.proxy.AbstractSmartProxy;

/**
 * Client-side smart proxy for the {@link HelloService}.
 *
 * <p>This class is downloaded to client JVMs; it forwards every
 * {@link HelloService} method call to the remote server-side implementation
 * over a JERI transport channel.
 *
 * <p>Use the {@link #create(HelloService, Uuid)} factory method rather than
 * constructing directly.  The factory automatically returns a
 * {@link ConstrainableHelloServiceProxy} when the server stub implements
 * {@link RemoteMethodControl} (i.e. when the service is exported over an
 * SSL/TLS or Kerberos endpoint with method constraints).
 *
 * <h2>Serialization</h2>
 * This class is annotated with {@link AtomicSerial} and provides a
 * {@code (GetArg)} constructor for validated atomic deserialization, as
 * required by the JGDMS wire-serialisation standard.
 *
 * @see HelloService
 * @see AbstractSmartProxy
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
@AtomicSerial
public class HelloServiceProxy
        extends AbstractSmartProxy
        implements HelloService {

    private static final long serialVersionUID = 1L;

    /**
     * Factory method — returns a {@link ConstrainableHelloServiceProxy} when
     * {@code server} implements {@link RemoteMethodControl}, otherwise a plain
     * {@code HelloServiceProxy}.
     *
     * @param server  the remote server stub; must not be {@code null}
     * @param proxyID the service's stable unique identifier; must not be
     *                {@code null}
     * @return the appropriate proxy instance
     */
    public static AbstractSmartProxy create(HelloService server, Uuid proxyID) {
        if (server instanceof RemoteMethodControl) {
            return new ConstrainableHelloServiceProxy(server, proxyID, null);
        }
        return new HelloServiceProxy(server, proxyID);
    }

    /**
     * Creates a new proxy wrapping the given server stub.
     *
     * @param server  the remote server stub; must not be {@code null}
     * @param proxyID the service's stable unique identifier; must not be
     *                {@code null}
     */
    public HelloServiceProxy(HelloService server, Uuid proxyID) {
        super(server, proxyID);
    }

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * @param arg the deserialization argument bag
     * @throws IOException            if deserialization validation fails
     * @throws ClassNotFoundException if a required class cannot be found
     */
    public HelloServiceProxy(GetArg arg)
            throws IOException, ClassNotFoundException {
        super(arg);
    }

    @Override
    public String sayHello(String name) throws RemoteException {
        return ((HelloService) server).sayHello(name);
    }

    // -------------------------------------------------------------------------
    // Nested class: ConstrainableHelloServiceProxy
    // -------------------------------------------------------------------------

    /**
     * Constrainable subclass of {@link HelloServiceProxy}.
     *
     * <p>Provides full {@link RemoteMethodControl} support so that the client
     * can impose per-method security constraints (e.g. requiring
     * {@link net.jini.core.constraint.Integrity#YES}) on each outbound call.
     * Instances are produced by the
     * {@link HelloServiceProxy#create} factory when the server stub
     * implements {@link RemoteMethodControl}.
     *
     * @since 3.1.1
     */
    @AtomicSerial
    public static final class ConstrainableHelloServiceProxy
            extends AbstractSmartProxy.ConstrainableSmartProxy
            implements HelloService {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a constrainable proxy.
         *
         * @param server      the remote server stub
         * @param proxyID     the service's stable unique identifier
         * @param constraints per-method constraints, or {@code null}
         */
        public ConstrainableHelloServiceProxy(HelloService server,
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
        public ConstrainableHelloServiceProxy(GetArg arg)
                throws IOException, ClassNotFoundException {
            super(arg);
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new ConstrainableHelloServiceProxy(
                    (HelloService) server, getReferentUuid(), constraints);
        }

        @Override
        public String sayHello(String name) throws RemoteException {
            return ((HelloService) server).sayHello(name);
        }
    }
}
