/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Same package as DerProxySerializer so the test can use its package-private constructor to
// build a carrier directly (production builds one via the substitution seam + a ProxyCodebaseSpi).
package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.marshal.DerMarshalledInstance;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: a {@code java.lang.reflect.Proxy} travels inside a (DER) {@link MarshalledInstance}
 * with NO Java Object Serialization, both bare ([8]) and via the {@link DerProxySerializer}
 * carrier (whose {@code readResolve} reconstructs the proxy through the default no-download
 * {@code ProxyCodebaseSpi}).
 */
public class DerProxyMarshalledInstanceTest {

    /** A trivial remote-ish service interface. */
    public interface Greeter {
        String greet();
    }

    /**
     * An {@code @AtomicSerial} {@link InvocationHandler} -- the bare-{@code Proxy} [8] form and
     * the carrier both require the handler to be {@code @AtomicSerial} (it carries the portable
     * essence; in production it is a {@code BasicInvocationHandler} holding endpoint+constraints).
     */
    @AtomicSerial
    public static class FixedHandler implements InvocationHandler {
        private final String reply;

        public FixedHandler(String reply) {
            this.reply = reply;
        }

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm("reply", String.class) };
        }

        public static void serialize(PutArg arg, FixedHandler h) throws IOException {
            arg.put("reply", h.reply);
            arg.writeArgs();
        }

        public FixedHandler(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("reply", null, String.class));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "greet":    return reply;
                // CodebaseAccessor emulation for the rebuilt bootstrap proxy: advertise an EMPTY
                // codebase (nothing to download -- classes resolve locally), so a registered
                // ProxyCodebaseSpi (e.g. PreferredProxyCodebaseProvider) takes the no-download
                // path and simply unmarshals the serviceProxy with the parent loader.
                case "getClassAnnotation":         return "";
                case "getCertFactoryType":         return "X.509";
                case "getCertPathEncoding":        return "PkiPath";
                case "getEncodedCerts":            return new byte[0];
                case "getCodebaseDigestAlgorithm": return "";
                case "getCodebaseDigest":          return new byte[0];
                case "getDigestOffsets":           return new int[0];
                // RemoteMethodControl: no constraints.
                case "getConstraints":             return null;
                case "setConstraints":             return proxy;
                case "equals":   return proxy == (args == null ? null : args[0]);
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "Greeter[" + reply + "]";
                default:         return null;
            }
        }
    }

    private Greeter newProxy(String reply) {
        ClassLoader cl = getClass().getClassLoader();
        return (Greeter) Proxy.newProxyInstance(cl, new Class<?>[]{Greeter.class}, new FixedHandler(reply));
    }

    /** A bare proxy stored with substitute=false round-trips through a DER MarshalledInstance ([8] form, no JOSS). */
    @Test
    public void bareProxyRoundTripsThroughMarshalledInstance() throws Exception {
        ClassLoader cl = getClass().getClassLoader();
        Greeter real = newProxy("hello");

        // substitute=false -> the proxy is stored BARE (interface names + @AtomicSerial handler),
        // schemaBytes left empty (object-stream form). No carrier, no Java Serialization.
        MarshalledInstance mi = new DerMarshalledInstance(real, Collections.emptyList(), false);

        Greeter back = mi.get(cl, false, cl, Collections.emptyList(), Greeter.class);
        assertNotNull(back);
        assertTrue(Proxy.isProxyClass(back.getClass()), "decoded value must be a java.lang.reflect.Proxy");
        assertEquals("hello", back.greet());
        assertTrue(back.getClass().getInterfaces().length == 1
                && back.getClass().getInterfaces()[0] == Greeter.class);
    }

    /**
     * A {@link DerProxySerializer} carrier (handler + bare serviceProxy) round-trips through a DER
     * MarshalledInstance and, on decode, the root readResolve reconstructs the proxy via the
     * default no-download {@code ProxyCodebaseSpi} (which simply unmarshals the serviceProxy).
     */
    @Test
    public void carrierRoundTripsAndReadResolves() throws Exception {
        ClassLoader cl = getClass().getClassLoader();
        Greeter real = newProxy("hello");

        // serviceProxy wraps the real proxy bare (substitute=false), as DerProxySerializer.create does.
        MarshalledInstance serviceProxy = new DerMarshalledInstance(real, Collections.emptyList(), false);
        DerProxySerializer carrier = new DerProxySerializer(
                new FixedHandler("bootstrap"), serviceProxy, Collections.emptyList(), null, null);

        // Encode the carrier as a normal MarshalledInstance's content; decode with the no-type
        // get() (Object.class). DerMarshalInstanceInput applies readResolve to the root ->
        // default provider resolve -> serviceProxy.get() (local unmarshal) -> the real proxy.
        MarshalledInstance outer = new DerMarshalledInstance(carrier, Collections.emptyList());
        Object resolved = outer.get(cl, false, cl, Collections.emptyList());

        assertTrue(resolved instanceof Greeter,
                "carrier must readResolve to the Greeter proxy, got "
                + (resolved == null ? "null" : resolved.getClass().getName()));
        assertEquals("hello", ((Greeter) resolved).greet());
    }

    /**
     * Class resolution uses the endpoint-assigned {@code defaultLoader}, NOT the thread-context
     * loader (the Warres discipline -- blog post 7). A recording endpoint loader must be consulted
     * to resolve both the proxy interface ([8]) and the {@code @AtomicSerial} handler ([1]); the
     * old thread-context-loader code (the endpoint loader's parent here) would never touch it.
     */
    @Test
    public void resolvesAgainstEndpointLoaderNotThreadContext() throws Exception {
        java.util.List<String> asked = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        ClassLoader endpoint = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(DerProxyMarshalledInstanceTest.class.getName())) {
                    asked.add(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        Greeter real = newProxy("endpoint");
        MarshalledInstance mi = new DerMarshalledInstance(real, Collections.emptyList(), false);

        // Pass the recording loader as the endpoint defaultLoader; resolution must go through it.
        Greeter back = mi.get(endpoint, false, endpoint, Collections.emptyList(), Greeter.class);
        assertEquals("endpoint", back.greet());

        assertTrue(asked.stream().anyMatch(n -> n.contains("Greeter")),
                "endpoint loader must resolve the proxy interface (not the TCCL); asked=" + asked);
        assertTrue(asked.stream().anyMatch(n -> n.contains("FixedHandler")),
                "endpoint loader must resolve the @AtomicSerial handler (not the TCCL); asked=" + asked);
    }

    /** The handler must be @AtomicSerial: a carrier built from a non-@AtomicSerial handler is rejected. */
    @Test
    public void nonAtomicHandlerRejected() throws Exception {
        InvocationHandler plain = (proxy, method, args) -> null; // lambda: not @AtomicSerial
        MarshalledInstance svc = new DerMarshalledInstance("x", Collections.emptyList());
        boolean threw = false;
        try {
            new DerProxySerializer(plain, svc, Collections.emptyList(), null, null);
        } catch (java.io.InvalidObjectException expected) {
            threw = true;
        }
        assertTrue(threw, "non-@AtomicSerial handler must be rejected by the carrier");
    }
}
