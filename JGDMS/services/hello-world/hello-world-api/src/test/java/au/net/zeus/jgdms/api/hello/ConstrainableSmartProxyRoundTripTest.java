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
package au.net.zeus.jgdms.api.hello;

import au.net.zeus.jgdms.proxy.AbstractSmartProxy;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.Stateless;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Live {@link org.apache.river.api.io.AtomicMarshalInputStream} round-trip for the
 * {@code @AtomicSerial} {@link AbstractSmartProxy.ConstrainableSmartProxy} base.
 *
 * <p>This guards the frame-scoped {@link GetArg} namespace bug: the
 * {@code server}/{@code proxyID} state is declared by {@link AbstractSmartProxy}, so it
 * lives in the {@code AbstractSmartProxy} {@code @AtomicSerial} namespace — a
 * {@code @Stateless} {@code ConstrainableSmartProxy} subclass frame cannot read it (each
 * class in an {@code @AtomicSerial} hierarchy has its own {@code GetArg} namespace). The
 * pre-{@code super} {@code arg.get("server")} the subclass used to do returned
 * {@code null} and threw {@code InvalidObjectException: server does not implement
 * RemoteMethodControl}; the fix reads/validates {@code server} in the superclass frame
 * via {@code super(arg)} then refines the {@code RemoteMethodControl} check on the
 * inherited field.
 *
 * <p>Lives in {@code hello-world-api} (not the {@code jgdms-lib-dl} unit test) because a
 * real round-trip of a smart proxy needs (a) {@code -Djava.rmi.server.RMIClassLoaderSpi
 * =default} (this module's surefire {@code argLine}) and (b) the SecurityManager-capable
 * JDK — {@link AbstractSmartProxy} legitimately implements
 * {@link net.jini.export.ProxyAccessor} (it is a downloadable smart proxy), so it is
 * marshalled through the {@code ProxySerializer} codebase path, which performs a guarded
 * permission check.
 */
public class ConstrainableSmartProxyRoundTripTest {

    /** Minimal service interface the concrete proxy exposes. */
    public interface DummyService {
        void doSomething() throws RemoteException;
    }

    /**
     * The infrastructure interfaces {@link AbstractSmartProxy#AbstractSmartProxy(GetArg)}
     * requires the deserialized {@code server} to implement, plus
     * {@link RemoteMethodControl} and the test's {@link DummyService}.  A dynamic
     * {@code Proxy} over these, backed by the {@code @AtomicSerial}
     * {@link InfraServerHandler}, is a marshallable stand-in for a real exported stub.
     */
    private static final Class<?>[] INFRA_IFACES = {
        DummyService.class, Administrable.class, CodebaseAccessor.class,
        ServiceProxyAccessor.class, ServiceAttributesAccessor.class,
        ServiceIDAccessor.class, JoinAdmin.class, DestroyAdmin.class,
        RemoteMethodControl.class
    };

    private static Object infraServer() {
        return Proxy.newProxyInstance(
                ConstrainableSmartProxyRoundTripTest.class.getClassLoader(),
                INFRA_IFACES, new InfraServerHandler());
    }

    /**
     * Public, stateless {@code @AtomicSerial} handler for {@link #infraServer()} so the
     * atomic engine reconstructs it via the reflective (non-delegate) path.
     * {@code setConstraints} returns a fresh equivalent stub (exercised during the
     * constrainable proxy's construction); every other method is a marshalling no-op.
     */
    @AtomicSerial
    @Stateless
    public static final class InfraServerHandler implements InvocationHandler {
        public InfraServerHandler() { }
        public InfraServerHandler(GetArg arg) throws IOException, ClassNotFoundException { }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "setConstraints":
                    return Proxy.newProxyInstance(
                            proxy.getClass().getClassLoader(),
                            proxy.getClass().getInterfaces(),
                            new InfraServerHandler());
                case "getConstraints": return null;
                case "hashCode":       return System.identityHashCode(proxy);
                case "equals":         return proxy == args[0];
                case "toString":       return "InfraServer";
                default:               return null;
            }
        }
    }

    /**
     * Public, marshallable concrete {@link AbstractSmartProxy.ConstrainableSmartProxy}.
     * {@code @Stateless} because it adds no serial state of its own (the
     * {@code server}/{@code proxyID} live in {@link AbstractSmartProxy}); public with a
     * public {@code (GetArg)} constructor so the reflective atomic-read path can
     * instantiate it.
     */
    @AtomicSerial
    @Stateless
    public static final class PublicConstrainableProxy
            extends AbstractSmartProxy.ConstrainableSmartProxy
            implements DummyService {

        public PublicConstrainableProxy(Object server, Uuid proxyID,
                                        MethodConstraints constraints) {
            super(server, proxyID, constraints);
        }

        public PublicConstrainableProxy(GetArg arg)
                throws IOException, ClassNotFoundException {
            super(arg);
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new PublicConstrainableProxy(server, getReferentUuid(), constraints);
        }

        @Override
        public void doSomething() throws RemoteException {
            ((DummyService) server).doSomething();
        }
    }

    @Test
    public void constrainableSmartProxyRoundTripsUnderAtomicEngine() throws Exception {
        Uuid uuid = UuidFactory.generate();
        PublicConstrainableProxy proxy =
                new PublicConstrainableProxy(infraServer(), uuid, null);

        Object rt = new AtomicMarshalledInstance(proxy).get(false);

        assertNotNull("round-trip must produce a proxy", rt);
        assertTrue("round-tripped proxy must be a ConstrainableSmartProxy",
                rt instanceof AbstractSmartProxy.ConstrainableSmartProxy);
        assertTrue("round-tripped proxy must be constrainable",
                rt instanceof RemoteMethodControl);
        assertTrue("round-tripped proxy must be the concrete DummyService proxy",
                rt instanceof DummyService);
        assertFalse("round-tripped proxy must NOT implement java.io.Serializable",
                rt instanceof Serializable);
        assertEquals("UUID identity must survive the round-trip",
                uuid, ((ReferentUuid) rt).getReferentUuid());
    }
}
