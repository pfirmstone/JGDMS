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

package au.net.zeus.jgdms.der.stream;

import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.DeSerializationPermission;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.Permission;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare {@code java.lang.reflect.Proxy} transport over a DER object stream (STD-008
 * sec.15.2 context tag {@code [8]}) — interface names + the {@code @AtomicSerial}
 * {@link InvocationHandler}, reconstructed via {@link Proxy#newProxyInstance}, with no
 * {@code ProxySerializer}/bootstrap/codebase involved. Verifies the {@code [8]} framing,
 * that the handler round-trips, and fail-secure rejection of a non-{@code @AtomicSerial}
 * handler.
 *
 * <p>In package {@code au.net.zeus.jgdms.der.stream} to reach the package-private
 * {@link DerObjectStreamCodec}.
 */
class DerObjectStreamProxyTest {

    public interface Greeter { String greet(); }

    /** A minimal @AtomicSerial InvocationHandler whose answer must survive the round-trip. */
    @AtomicSerial
    public static final class FixedAnswerHandler implements InvocationHandler {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[]{ new AtomicSerial.SerialForm("answer", String.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, FixedAnswerHandler h) throws IOException {
            arg.put("answer", h.answer);
            arg.writeArgs();
        }
        private final String answer;
        public FixedAnswerHandler(String answer) { this.answer = answer; }
        public FixedAnswerHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.answer = (String) arg.get("answer", null);
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "greet":    return answer;
                case "toString": return "Proxy[" + answer + "]";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals":   return proxy == (args == null ? null : args[0]);
                default:         return null;
            }
        }
    }

    private static byte[] write(Object o) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject(o);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    private static Object read(byte[] b) throws IOException, ClassNotFoundException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.initReader(b);
        return c.readObject();
    }

    @Test
    void bareProxy_roundTripsViaTag8_handlerPreserved() throws Exception {
        Greeter g = (Greeter) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ Greeter.class },
                new FixedAnswerHandler("hello-DER"));

        byte[] bytes = write(g);
        assertEquals((byte) 0xA8, bytes[0], "bare proxy must use the constructed context tag [8] (0xA8)");

        Object back = read(bytes);
        assertTrue(Proxy.isProxyClass(back.getClass()), "reconstructed value must be a dynamic proxy");
        assertTrue(back instanceof Greeter, "reconstructed proxy must implement the original interface");
        assertEquals("hello-DER", ((Greeter) back).greet(),
                "the @AtomicSerial InvocationHandler must round-trip (greet() returns the answer)");
    }

    @Test
    void nonAtomicSerialHandler_rejectedFailSecure() {
        // An anonymous (lambda) handler is NOT @AtomicSerial -> the bare proxy cannot be DER-encoded.
        Greeter g = (Greeter) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ Greeter.class },
                (proxy, method, args) -> "greet".equals(method.getName()) ? "x" : null);

        assertThrows(UnsupportedOperationException.class, () -> write(g),
                "a non-@AtomicSerial InvocationHandler must be rejected fail-secure");
    }

    /** The [8] read gate denies reconstruction when DeSerializationPermission("PROXY") is not granted. */
    @Test
    @SuppressWarnings("removal")
    void proxyGate_deniesPROXYWithoutGrant() {
        SecurityManager denying = new SecurityManager() {
            @Override public void checkPermission(Permission perm, Object context) {
                if (perm instanceof DeSerializationPermission && "PROXY".equals(perm.getName())) {
                    throw new SecurityException("denied DeSerializationPermission PROXY");
                }
            }
            @Override public void checkPermission(Permission perm) { /* allow */ }
        };
        assertThrows(SecurityException.class,
                () -> DerObjectStreamCodec.checkProxyDeSerializationPermitted(
                        new Class<?>[]{ Greeter.class }, denying),
                "[8] proxy reconstruction must be denied without DeSerializationPermission(PROXY)");
    }

    /** The gate is a no-op when DeSerializationPermission("PROXY") is granted (and without a SM). */
    @Test
    @SuppressWarnings("removal")
    void proxyGate_permitsWithGrant() {
        SecurityManager permitting = new SecurityManager() {
            @Override public void checkPermission(Permission perm, Object context) { /* allow all */ }
            @Override public void checkPermission(Permission perm) { /* allow */ }
        };
        assertDoesNotThrow(
                () -> DerObjectStreamCodec.checkProxyDeSerializationPermitted(
                        new Class<?>[]{ Greeter.class }, permitting),
                "[8] proxy reconstruction must be permitted with DeSerializationPermission(PROXY)");
    }
}
