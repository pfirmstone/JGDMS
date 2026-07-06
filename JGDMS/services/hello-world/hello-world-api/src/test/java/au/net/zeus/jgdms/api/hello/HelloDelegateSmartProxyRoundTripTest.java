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
import java.rmi.Remote;
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
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.AtomicSerial.Stateless;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Live {@link org.apache.river.api.io.AtomicMarshalInputStream} round-trip for the
 * P3 {@code @SmartProxy} <em>delegate-forwarding</em> shell -- the P3.3 oracle that
 * closes the "a generated shell has never been round-tripped" gap.
 *
 * <p>The shells here are hand-written but are EXACTLY the shape
 * {@code ServiceProxyProcessor.writeProxy(model, delegate)} emits (the golden codegen
 * tests in {@code ServiceProxyProcessorTest} pin the emitted text to this shape).  A
 * proxy for a genuinely DISJOINT api/protocol pair -- public
 * {@code Thermo.currentCelsius(region)} over wire {@code RawThermo.rawCelsius(region)}
 * -- forwards each api call to a developer {@code @SmartProxy} delegate that translates
 * the method name into the wire protocol.  The direct-forwarding generator could not
 * express this (there is no {@code currentCelsius} on the wire type); the delegate can.
 *
 * <p>Two shapes are exercised:
 * <ul>
 *   <li><b>stateless</b> ({@link ConstrainableThermoProxy}): {@code @Stateless}, wire
 *       form {@code {server, proxyID}}; the delegate is rebuilt from the deserialized
 *       server and an api call reaches the (name-translated) wire method after the
 *       wire round-trip;</li>
 *   <li><b>stateful</b> ({@link ConstrainableLabelledProxy}, {@code @State label}):
 *       non-{@code @Stateless}, declares its OWN serial form for {@code label} over the
 *       {@code @Stateless} {@code ConstrainableSmartProxy} base, and the durable
 *       {@code label} survives the wire and reaches the rebuilt delegate's constructor.
 *       This proves the frame-scoped {@code GetArg} layering (the leaf reads its own
 *       {@code label} frame; {@code super(arg)} reads {@code {server, proxyID}} from
 *       {@code AbstractSmartProxy}'s frame).</li>
 * </ul>
 *
 * <p>Lives in {@code hello-world-api} (like {@link ConstrainableSmartProxyRoundTripTest})
 * because a real smart-proxy round-trip needs the JDK default
 * {@code RMIClassLoaderSpi=default} (this module's surefire {@code argLine}) and the
 * SecurityManager-capable JDK (the smart proxy is a {@code ProxyAccessor}, marshalled
 * through the guarded {@code ProxySerializer} codebase path).
 */
public class HelloDelegateSmartProxyRoundTripTest {

    /** The distinctive wire reading the mock backend returns, so a forwarded call is observable. */
    private static final double RAW = 21.5;

    /** Public api: the client-facing contract the shell exposes (disjoint method name). */
    public interface Thermo extends Remote {
        double currentCelsius(String region) throws RemoteException;
    }

    /** Public api carrying durable proxy state in its behaviour. */
    public interface LabelledThermo extends Remote {
        String describe(String region) throws RemoteException;
    }

    /** Internal wire protocol: the interface the exported server stub implements. */
    public interface RawThermo extends Remote {
        double rawCelsius(String region) throws RemoteException;
    }

    /**
     * The developer {@code @SmartProxy} delegate: implements the public api, holds the
     * wire {@code server}, and translates {@code currentCelsius} into the disjoint wire
     * method {@code rawCelsius}.
     */
    public static final class ThermoLogic implements Thermo {
        private final RawThermo server;
        public ThermoLogic(RawThermo server) { this.server = server; }
        @Override
        public double currentCelsius(String region) throws RemoteException {
            return server.rawCelsius(region);
        }
    }

    /** A stateful delegate: its behaviour depends on a durable {@code label} chosen at export. */
    public static final class LabelledLogic implements LabelledThermo {
        private final RawThermo server;
        private final String label;
        public LabelledLogic(RawThermo server, String label) {
            this.server = server;
            this.label = label;
        }
        @Override
        public String describe(String region) throws RemoteException {
            return label + "=" + server.rawCelsius(region);
        }
    }

    /**
     * The infrastructure interfaces {@link AbstractSmartProxy} requires the
     * deserialized {@code server} to implement, plus the wire {@link RawThermo} and
     * {@link RemoteMethodControl}.  A dynamic {@code Proxy} over these, backed by the
     * {@code @AtomicSerial} {@link WireServerHandler}, is a marshallable stand-in for a
     * real exported wire stub.
     */
    private static final Class<?>[] WIRE_IFACES = {
        RawThermo.class, Administrable.class, CodebaseAccessor.class,
        ServiceProxyAccessor.class, ServiceAttributesAccessor.class,
        ServiceIDAccessor.class, JoinAdmin.class, DestroyAdmin.class,
        RemoteMethodControl.class
    };

    private static RawThermo wireServer() {
        return (RawThermo) Proxy.newProxyInstance(
                HelloDelegateSmartProxyRoundTripTest.class.getClassLoader(),
                WIRE_IFACES, new WireServerHandler());
    }

    /**
     * Public, stateless {@code @AtomicSerial} handler for {@link #wireServer()} so the
     * atomic engine reconstructs it via the reflective path.  {@code rawCelsius}
     * returns {@link #RAW} (the observable forwarded call); {@code setConstraints}
     * returns a fresh equivalent stub; every other method is a marshalling no-op.
     */
    @AtomicSerial
    @Stateless
    public static final class WireServerHandler implements InvocationHandler {
        public WireServerHandler() { }
        public WireServerHandler(GetArg arg) throws IOException, ClassNotFoundException { }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "rawCelsius":     return RAW;
                case "setConstraints":
                    return Proxy.newProxyInstance(
                            proxy.getClass().getClassLoader(),
                            proxy.getClass().getInterfaces(),
                            new WireServerHandler());
                case "getConstraints": return null;
                case "hashCode":       return System.identityHashCode(proxy);
                case "equals":         return proxy == args[0];
                case "toString":       return "WireServer";
                default:               return null;
            }
        }
    }

    /**
     * The STATELESS delegate-forwarding shell, byte-for-byte the shape
     * {@code writeProxy(model, delegate)} emits for a stateless delegate: transient
     * {@code delegate} rebuilt in both ctors, {@code @Stateless} (wire form
     * {@code {server, proxyID}}), each api method forwarded to the delegate.
     */
    @AtomicSerial
    @Stateless
    public static final class ConstrainableThermoProxy
            extends AbstractSmartProxy.ConstrainableSmartProxy
            implements Thermo {

        private transient final ThermoLogic delegate;

        public ConstrainableThermoProxy(RawThermo server, Uuid proxyID,
                                        MethodConstraints constraints) {
            super(server, proxyID, constraints);
            this.delegate = new ThermoLogic((RawThermo) server);
        }

        public ConstrainableThermoProxy(GetArg arg)
                throws IOException, ClassNotFoundException {
            super(arg);
            this.delegate = new ThermoLogic((RawThermo) server);
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new ConstrainableThermoProxy(
                    (RawThermo) server, getReferentUuid(), constraints);
        }

        @Override
        public double currentCelsius(String region) throws RemoteException {
            return delegate.currentCelsius(region);
        }
    }

    /**
     * The STATEFUL delegate-forwarding shell, the shape {@code writeProxy} emits for a
     * delegate with a {@code @State(name="label", type=String.class)} declaration:
     * non-{@code @Stateless}, its own {@code serialForm()}/{@code serialize()} for
     * {@code label}, and a {@code (GetArg)} ctor that reads {@code label} from its own
     * frame and passes it to the delegate ctor after {@code server}.
     */
    @AtomicSerial
    public static final class ConstrainableLabelledProxy
            extends AbstractSmartProxy.ConstrainableSmartProxy
            implements LabelledThermo {

        private transient final LabelledLogic delegate;
        private final String label;

        public static SerialForm[] serialForm() {
            return new SerialForm[]{
                new SerialForm("label", String.class)
            };
        }

        public static void serialize(PutArg arg, ConstrainableLabelledProxy obj)
                throws IOException {
            arg.put("label", obj.label);
            arg.writeArgs();
        }

        public ConstrainableLabelledProxy(RawThermo server, Uuid proxyID,
                                          MethodConstraints constraints, String label) {
            super(server, proxyID, constraints);
            this.label = label;
            this.delegate = new LabelledLogic((RawThermo) server, label);
        }

        public ConstrainableLabelledProxy(GetArg arg)
                throws IOException, ClassNotFoundException {
            super(arg);
            this.label = arg.get("label", null, String.class);
            this.delegate = new LabelledLogic((RawThermo) server, label);
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new ConstrainableLabelledProxy(
                    (RawThermo) server, getReferentUuid(), constraints, label);
        }

        @Override
        public String describe(String region) throws RemoteException {
            return delegate.describe(region);
        }
    }

    // ------------------------------------------------------------------ tests

    /**
     * P3.3 oracle (stateless): the delegate-forwarding shell round-trips through the
     * atomic engine, the delegate is rebuilt from the deserialized server, and a
     * public-api call reaches the DISJOINT wire method after the wire round-trip.
     */
    @Test
    public void statelessDelegateShellRoundTripsAndTranslates() throws Exception {
        Uuid uuid = UuidFactory.generate();
        ConstrainableThermoProxy proxy =
                new ConstrainableThermoProxy(wireServer(), uuid, null);

        // Pre-wire sanity: currentCelsius (api) reaches rawCelsius (wire) via the delegate.
        assertEquals(RAW, proxy.currentCelsius("here"), 0.0);

        Object rt = new AtomicMarshalledInstance(proxy).get(false);

        assertTrue("round-trip must produce a proxy", rt != null);
        assertTrue("round-tripped shell must implement the public api",
                rt instanceof Thermo);
        assertTrue("round-tripped shell must be constrainable",
                rt instanceof RemoteMethodControl);
        assertFalse("round-tripped shell must NOT be Serializable",
                rt instanceof Serializable);
        assertEquals("UUID identity must survive the round-trip",
                uuid, ((ReferentUuid) rt).getReferentUuid());
        // The heart of P3: an api call on the RECONSTRUCTED shell reaches the
        // name-translated wire method through the rebuilt delegate.
        assertEquals("api call must translate to the wire method after the round-trip",
                RAW, ((Thermo) rt).currentCelsius("here"), 0.0);
    }

    /**
     * P3.3 oracle (stateful): the declared {@code @State label} survives the wire and
     * reaches the rebuilt delegate's constructor -- proving the stateful serial form
     * layers correctly over the {@code @Stateless} base's frame.
     */
    @Test
    public void statefulDelegateShellRoundTripsAndStateReachesDelegate() throws Exception {
        Uuid uuid = UuidFactory.generate();
        ConstrainableLabelledProxy proxy =
                new ConstrainableLabelledProxy(wireServer(), uuid, null, "degC");

        assertEquals("degC=" + RAW, proxy.describe("here"));

        Object rt = new AtomicMarshalledInstance(proxy).get(false);

        assertTrue("round-tripped stateful shell must implement the public api",
                rt instanceof LabelledThermo);
        assertTrue("round-tripped stateful shell must be constrainable",
                rt instanceof RemoteMethodControl);
        assertFalse("round-tripped stateful shell must NOT be Serializable",
                rt instanceof Serializable);
        assertEquals("UUID identity must survive the round-trip",
                uuid, ((ReferentUuid) rt).getReferentUuid());
        // The durable label survived the wire AND reached the rebuilt delegate ctor
        // (the "degC=" prefix), while the forwarded wire call still resolves (RAW).
        assertEquals("durable @State must survive the wire and reach the delegate",
                "degC=" + RAW, ((LabelledThermo) rt).describe("here"));
    }
}
