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
package au.net.zeus.jgdms.bae.proxy;

import java.io.IOException;
import java.rmi.RemoteException;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import au.net.zeus.jgdms.api.codebase.AnalysisException;
import au.net.zeus.jgdms.api.codebase.AnalysisRequest;
import au.net.zeus.jgdms.api.codebase.BytecodeAnalysisEngine;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import au.net.zeus.jgdms.proxy.AbstractSmartProxy;
import java.io.InvalidObjectException;

/**
 * Client-side smart proxy for the {@link BytecodeAnalysisEngine} service.
 *
 * <p>This class is downloaded to client JVMs and forwards
 * {@link #analyzeJar} calls to the remote server-side implementation
 * over a JERI transport channel.
 *
 * <p>The proxy is {@link AtomicSerial} and extends {@link AbstractSmartProxy},
 * which provides safe deserialization, {@link net.jini.export.ProxyAccessor},
 * and {@link net.jini.id.ReferentUuid} identity based on the service UUID.
 *
 * <p>Use the {@link #create(BytecodeAnalysisEngine, Uuid)} factory method
 * rather than constructing directly; the factory automatically returns a
 * {@link ConstrainableBytecodeAnalysisEngineProxy} when the server stub
 * implements {@link RemoteMethodControl}.
 *
 * @see BytecodeAnalysisEngine
 * @see AbstractSmartProxy
 * @see ConstrainableBytecodeAnalysisEngineProxy
 * @since 3.1.1
 */
@AtomicSerial
public class BytecodeAnalysisEngineProxy
        extends AbstractSmartProxy
        implements BytecodeAnalysisEngine {

    private static final long serialVersionUID = 1L;

    /**
     * Factory method — returns a {@link ConstrainableBytecodeAnalysisEngineProxy}
     * when {@code server} implements {@link RemoteMethodControl}, otherwise a
     * plain {@code BytecodeAnalysisEngineProxy}.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     * @return the appropriate proxy instance
     */
    public static AbstractSmartProxy create(BytecodeAnalysisEngine server,
                                            Uuid proxyID) {
        if (server instanceof RemoteMethodControl) {
            return new ConstrainableBytecodeAnalysisEngineProxy(server, proxyID, null);
        }
        return new BytecodeAnalysisEngineProxy(server, proxyID);
    }

    /**
     * Creates a new proxy wrapping the given server stub.
     *
     * <p>Prefer the {@link #create(BytecodeAnalysisEngine, Uuid)} factory
     * method, which automatically returns a constrainable proxy when the
     * server stub implements {@link RemoteMethodControl}.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     */
    public BytecodeAnalysisEngineProxy(BytecodeAnalysisEngine server, Uuid proxyID) {
        super(server, proxyID);
    }

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * @param arg the deserialization argument bag
     * @throws IOException if deserialization validation fails
     */
    public BytecodeAnalysisEngineProxy(GetArg arg) throws IOException, ClassNotFoundException {
         this(arg, check(arg));
    }
    
    private BytecodeAnalysisEngineProxy(GetArg arg, boolean check) throws IOException, ClassNotFoundException {
        super(arg);
    }
    
    private static boolean check(GetArg arg) throws IOException, ClassNotFoundException {
        BytecodeAnalysisEngineProxy sup = new BytecodeAnalysisEngineProxy(arg, true);
        if (sup.server instanceof BytecodeAnalysisEngine && 
                BytecodeAnalysisEngine.class.equals(sup.server.getClass())) return true;
        throw new InvalidObjectException("Server not an instance of BytecodeAnalysisEngine");
    }

    @Override
    public JarAnalysisReport analyzeJar(AnalysisRequest request)
            throws AnalysisException, RemoteException {
        return ((BytecodeAnalysisEngine) server).analyzeJar(request);
    }

    // =========================================================================
    // Nested class: ConstrainableBytecodeAnalysisEngineProxy
    // =========================================================================

    /**
     * Constrainable subclass of {@link BytecodeAnalysisEngineProxy}.
     *
     * <p>Extends {@link AbstractSmartProxy.ConstrainableSmartProxy} to
     * provide full {@link RemoteMethodControl} support.  Instances are
     * produced by the {@link BytecodeAnalysisEngineProxy#create} factory
     * when the server stub implements {@link RemoteMethodControl}.
     *
     * @since 3.1.1
     */
    @AtomicSerial
    public static final class ConstrainableBytecodeAnalysisEngineProxy
            extends AbstractSmartProxy.ConstrainableSmartProxy
            implements BytecodeAnalysisEngine {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a new constrained proxy.
         *
         * @param server      the remote server stub
         * @param proxyID     the service's stable unique identifier
         * @param constraints the client method constraints; may be {@code null}
         */
        public ConstrainableBytecodeAnalysisEngineProxy(BytecodeAnalysisEngine server,
                                                  Uuid proxyID,
                                                  MethodConstraints constraints) {
            super(server, proxyID, constraints);
        }

        /**
         * {@link AtomicSerial} deserialization constructor.
         *
         * @param arg the deserialization argument bag
         * @throws IOException if deserialization validation fails
         */
        public ConstrainableBytecodeAnalysisEngineProxy(GetArg arg) throws IOException, ClassNotFoundException  {
            this(arg, check(arg));
        }
        
        private ConstrainableBytecodeAnalysisEngineProxy(GetArg arg, boolean check) throws IOException, ClassNotFoundException  {
            super(arg);
        }
        
        private static boolean check(GetArg arg) throws IOException, ClassNotFoundException {
            ConstrainableBytecodeAnalysisEngineProxy sup = new ConstrainableBytecodeAnalysisEngineProxy(arg, true);
            if (sup.server instanceof BytecodeAnalysisEngine && 
                    BytecodeAnalysisEngine.class.equals(sup.server.getClass())) return true;
            throw new InvalidObjectException("Server not an instance of BytecodeAnalysisEngine");
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new ConstrainableBytecodeAnalysisEngineProxy(
                    (BytecodeAnalysisEngine) server, getReferentUuid(), constraints);
        }

        @Override
        public JarAnalysisReport analyzeJar(AnalysisRequest request)
                throws AnalysisException, RemoteException {
            return ((BytecodeAnalysisEngine) server).analyzeJar(request);
        }
    }
}
